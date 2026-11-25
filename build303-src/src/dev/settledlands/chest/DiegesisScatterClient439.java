package dev.settledlands.chest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Build 439: optional client-side 3D scatter placement engine for {@code diegesis} /
 * {@code handson_furniture} storage stations.
 *
 * <p>Uses zero hard bytecode references to {@code com.pockyl.diegesis.*}; every class/method is
 * resolved lazily and cached via reflection so dedicated servers and clients without Diegesis
 * incur no classloading risk. Supports both {@code beta.12..16} (7-arg {@code ItemPose}, tick
 * watch sync) and {@code beta.17+} ({@code flatLies}, {@code leastHalf}, {@code DiegesisClient.addBehavior}).</p>
 */
public final class DiegesisScatterClient439 {

    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float SQRT_HALF = 0.70710677F;
    private static final float FALLBACK_FLAT_HALF = 0.15F;
    private static final float CEILING_CLEARANCE_PAD = 0.10F;
    private static final float NORMAL_GAP = 0.125F;
    private static final float COMFORT_GAP = 0.40F;
    private static final float WALL_PAD = 0.10F;
    private static final float WALL_PAD_NARROW = 0.04F;
    private static final float OPEN_PAD = 0.45F;
    private static final float OPEN_PAD_NARROW = 0.25F;
    private static final float NARROW_THRESHOLD = 2.6F;
    private static final long SEND_COOLDOWN_MS = 1500L;

    private static final Object LOCK = new Object();
    private static final Map<Object, Long> SCENE_SEEDS = new WeakHashMap<>();
    private static final Map<Object, Boolean> KNOWN_STATIONS = new WeakHashMap<>();
    private static final Map<BlockPos, SentEntry> SENT_BY_POS = new HashMap<>();
    private static final AtomicBoolean BEHAVIOR_REGISTERED = new AtomicBoolean(false);
    private static int watchTicks;

    private static final class SentEntry {
        final long seed;
        final long sentAtMs;

        SentEntry(long seed, long sentAtMs) {
            this.seed = seed;
            this.sentAtMs = sentAtMs;
        }
    }

    private static final class SupportCandidate {
        final int slot;
        final int surfaceIndex;
        final float x;
        final float z;
        final float top;
        final float halfX;
        final float halfZ;
        final float scale;

        SupportCandidate(int slot, int surfaceIndex, float x, float z, float top,
                float halfX, float halfZ, float scale) {
            this.slot = slot;
            this.surfaceIndex = surfaceIndex;
            this.x = x;
            this.z = z;
            this.top = top;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.scale = scale;
        }
    }

    private static final class SurfaceScore {
        final int index;
        final double weightKey;

        SurfaceScore(int index, double weightKey) {
            this.index = index;
            this.weightKey = weightKey;
        }
    }

    private static volatile boolean apiProbed;
    private static volatile boolean apiReady;

    // SceneInstance / ClientStation / NodeInstance / PlacedItem / PlacedShape
    private static Field sceneStationField;
    private static Field scenePlacedField;
    private static Field scenePlacingOptionsField;
    private static Method sceneUpdateMethod;

    private static Field clientStationSceneField;
    private static Method clientStationStateMethod;
    private static Method clientStationPosMethod;
    private static Method clientStationFocusedMethod;

    private static Method nodePlacedMethod;

    private static Field placedItemGuessedField;
    private static Method placedItemSlotMethod;
    private static Method placedItemStackMethod;
    private static Method placedItemShapeMethod;
    private static Method placedItemPoseMethod;

    private static Method placedShapeExtentsMethod;
    private static Method placedShapeIsFlatMethod;

    // PlacingOptions / PlaceSurface / Placing
    private static Method optionsItemSizeMethod;
    private static Method optionsScaleRangeMethod;
    private static Method optionsSurfacesMethod;

    private static Method surfaceFromMethod;
    private static Method surfaceToMethod;
    private static Method surfaceHeightMethod;
    private static Method surfaceClearanceMethod;
    private static Method surfaceOpenMethod;
    private static Method surfaceFlatLiesMethod; // optional (beta.17+)

    private static Method vec2XMethod;
    private static Method vec2YMethod;
    private static Method placingLeastHalfMethod; // optional (beta.17+)

    // StationState / SlotRef / ItemPose / Quaternionf
    private static Method stateDataMethod;
    private static Method stateGetPoseMethod;
    private static Method slotRefIndexMethod;

    private static Constructor<?> itemPoseCtor;
    private static Method poseXMethod;
    private static Method poseYMethod;
    private static Method poseZMethod;
    private static Method poseLiftMethod;
    private static Method poseRotationMethod;
    private static Method poseScaleMethod;
    private static Method poseOnMethod;
    private static Method poseTopMethod;

    private static Method quatXMethod;
    private static Method quatYMethod;
    private static Method quatZMethod;
    private static Method quatWMethod;
    private static Constructor<?> quat4fCtor;

    // DiegesisClient behavior registration (beta.17+)
    private static volatile boolean behaviorApiProbed;
    private static Method diegesisAddBehaviorMethod;
    private static Class<?> factoryIface;
    private static Class<?> behaviorIface;
    private static Method viewIsFocusedMethod;
    private static Method viewPosMethod;
    private static Method viewStateMethod;

    private DiegesisScatterClient439() {}

    /**
     * Registers a global experimental Diegesis {@code StationBehavior} that commits client-computed
     * scatter poses to the server on first focus/open. Safe no-op when Diegesis is absent or older.
     */
    public static void registerBehavior() {
        if (BEHAVIOR_REGISTERED.get()) return;
        ensureBehaviorApi();
        if (diegesisAddBehaviorMethod == null || factoryIface == null || behaviorIface == null) {
            return;
        }
        if (!BEHAVIOR_REGISTERED.compareAndSet(false, true)) return;
        try {
            ClassLoader cl = factoryIface.getClassLoader();
            Object behaviorProxy = Proxy.newProxyInstance(cl, new Class<?>[]{behaviorIface},
                    new BehaviorInvocationHandler());
            Object factoryProxy = Proxy.newProxyInstance(cl, new Class<?>[]{factoryIface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("create".equals(name)) return behaviorProxy;
                        return defaultValue(method.getReturnType(), proxy, args);
                    });
            diegesisAddBehaviorMethod.invoke(null, factoryProxy);
        } catch (Throwable ignored) {
            BEHAVIOR_REGISTERED.set(false);
        }
    }

    /**
     * Periodic client tick watch (from {@link ChestOpenWatch401}) backing up {@code StationBehavior}
     * on earlier Diegesis versions lacking {@code DiegesisClient#addBehavior}, and whenever a chunk
     * update arrives after the station first opened.
     */
    public static void tickWatch() {
        watchTicks++;
        if ((watchTicks & 3) != 0) return;
        List<Object> snapshot;
        synchronized (LOCK) {
            if (KNOWN_STATIONS.isEmpty()) return;
            snapshot = new ArrayList<>(KNOWN_STATIONS.keySet());
        }
        if (!apiReady || clientStationFocusedMethod == null) return;
        for (Object station : snapshot) {
            if (station == null) continue;
            try {
                Object focused = clientStationFocusedMethod.invoke(station);
                if (focused instanceof Boolean b && b.booleanValue()) {
                    trySyncStation(station);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class BehaviorInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (args != null && args.length >= 1 && args[0] != null) {
                if ("onOpen".equals(name)) {
                    clearSendCooldown(args[0]);
                    trySyncFocusedStation(args[0]);
                    return null;
                }
                if ("onSceneReady".equals(name)) {
                    if (isViewFocused(args[0])) {
                        trySyncFocusedStation(args[0]);
                    } else {
                        refreshIdleScatterIfNeeded(args[0]);
                    }
                    return null;
                }
                if ("onStateChanged".equals(name)) {
                    if (isViewFocused(args[0])) {
                        trySyncFocusedStation(args[0]);
                    } else {
                        refreshIdleScatterIfNeeded(args[0]);
                    }
                    return null;
                }
                if ("frame".equals(name)) {
                    if (isViewFocused(args[0])) {
                        trySyncFocusedStation(args[0]);
                    }
                    return null;
                }
            }
            return defaultValue(method.getReturnType(), proxy, args);
        }
    }

    private static Object defaultValue(Class<?> ret, Object proxy, Object[] args) {
        if (ret == void.class || ret == Void.class) return null;
        if (ret == boolean.class || ret == Boolean.class) {
            if (args != null && args.length == 1 && args[0] == proxy) return Boolean.TRUE;
            return Boolean.FALSE;
        }
        if (ret == int.class || ret == Integer.class) return Integer.valueOf(0);
        if (ret == long.class || ret == Long.class) return Long.valueOf(0L);
        if (ret == float.class || ret == Float.class) return Float.valueOf(0.0F);
        if (ret == double.class || ret == Double.class) return Double.valueOf(0.0);
        if (ret == Optional.class) return Optional.empty();
        if (ret == String.class) return "SettledLandsScatterBehavior";
        return null;
    }

    /**
     * Entry point called from {@code DiegesisScatterMixin439} at the head of
     * {@code SceneInstance#freeSpot(PlacedItem, PlacedShape)}. Returns an {@code ItemPose} for
     * scattered dungeon loot containers, or {@code null} to let Diegesis run its stock logic.
     */
    public static Object scatterFreeSpot(Object sceneObj, Object itemObj, Object shapeObj) {
        if (sceneObj == null || itemObj == null || shapeObj == null) return null;
        ensureClientApi();
        if (!apiReady) return null;
        if (!BEHAVIOR_REGISTERED.get()) {
            registerBehavior();
        }
        try {
            Object station = sceneStationField.get(sceneObj);
            if (station == null) return null;
            Object state = clientStationStateMethod.invoke(station);
            long seed = readScatterSeed(state);
            if (seed == 0L) return null;

            synchronized (LOCK) {
                SCENE_SEEDS.put(sceneObj, Long.valueOf(seed));
                KNOWN_STATIONS.put(station, Boolean.TRUE);
            }

            Object options = scenePlacingOptionsField.get(sceneObj);
            Object placedRaw = scenePlacedField.get(sceneObj);
            if (options == null || !(placedRaw instanceof List<?> placedList)) return null;

            Object surfacesRaw = optionsSurfacesMethod.invoke(options);
            if (!(surfacesRaw instanceof List<?> surfaces) || surfaces.isEmpty()) return null;

            return computeScatteredPose(itemObj, shapeObj, options, surfaces, placedList, seed);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float resolveLeastHalf(Object options, Object shapeObj, boolean isFlat) throws Throwable {
        if (placingLeastHalfMethod != null) {
            return ((Float) placingLeastHalfMethod.invoke(null, options, shapeObj)).floatValue();
        }
        return isFlat ? FALLBACK_FLAT_HALF : 0.0F;
    }

    private static Object computeScatteredPose(Object itemObj, Object shapeObj, Object options,
            List<?> surfaces, List<?> placedList, long seed) throws Throwable {
        Object slotRef = placedItemSlotMethod.invoke(itemObj);
        if (slotRef == null) return null;
        int slotIdx = ((Integer) slotRefIndexMethod.invoke(slotRef)).intValue();

        float itemSize = ((Float) optionsItemSizeMethod.invoke(options)).floatValue();
        float[] scaleRange = (float[]) optionsScaleRangeMethod.invoke(options);
        boolean isFlat = ((Boolean) placedShapeIsFlatMethod.invoke(shapeObj)).booleanValue();
        float ownLeast = isFlat ? resolveLeastHalf(options, shapeObj, true) : 0.0F;

        float rangeMin = Math.max(0.45F, scaleRange != null && scaleRange.length > 0 ? scaleRange[0] : 0.50F);
        float rangeMax = Math.min(1.30F, scaleRange != null && scaleRange.length > 1 ? scaleRange[1] : 1.00F);
        if (rangeMax < rangeMin) rangeMax = rangeMin;

        // Gather existing occupied 3D boxes and flat-top support candidates.
        List<float[]> taken = new ArrayList<>();
        Set<Integer> coveredSlots = new HashSet<>();
        List<RawPlacedInfo> existingInfos = new ArrayList<>();

        for (Object nodeObj : placedList) {
            if (nodeObj == null) continue;
            Object other = nodePlacedMethod.invoke(nodeObj);
            if (other == null || other == itemObj) continue;
            Object otherPose = placedItemPoseMethod.invoke(other);
            Object otherShape = placedItemShapeMethod.invoke(other);
            if (otherPose == null || otherShape == null) continue;

            Object oSlotObj = placedItemSlotMethod.invoke(other);
            int oSlot = oSlotObj != null ? ((Integer) slotRefIndexMethod.invoke(oSlotObj)).intValue() : -1;
            int oOn = ((Integer) poseOnMethod.invoke(otherPose)).intValue();
            if (oOn >= 0) {
                coveredSlots.add(Integer.valueOf(oOn));
            }

            float ox = ((Float) poseXMethod.invoke(otherPose)).floatValue();
            float oy = ((Float) poseYMethod.invoke(otherPose)).floatValue();
            float oz = ((Float) poseZMethod.invoke(otherPose)).floatValue();
            float olift = ((Float) poseLiftMethod.invoke(otherPose)).floatValue();
            float oscale = ((Float) poseScaleMethod.invoke(otherPose)).floatValue();
            float otop = ((Float) poseTopMethod.invoke(otherPose)).floatValue();
            Object oRot = poseRotationMethod.invoke(otherPose);

            Vector3f oExt = (Vector3f) placedShapeExtentsMethod.invoke(otherShape, oRot, itemSize * oscale);
            boolean oFlat = ((Boolean) placedShapeIsFlatMethod.invoke(otherShape)).booleanValue();
            float oLeast = oFlat ? resolveLeastHalf(options, otherShape, true) : 0.0F;
            float oHalfX = Math.max(oExt.x, oLeast);
            float oHalfY = Math.max(oExt.y, oLeast);
            float oHalfZ = Math.max(oExt.z, oLeast);
            float oMaxY = Math.max(otop, oy + oHalfY * 2.0F);

            taken.add(new float[]{
                    ox - oHalfX, ox + oHalfX,
                    oy, oMaxY,
                    oz - oHalfZ, oz + oHalfZ
            });
            existingInfos.add(new RawPlacedInfo(oSlot, oOn, ox, oy, oz, otop, oHalfX, oHalfZ, oExt.y, oscale, oFlat));
        }

        List<SupportCandidate> supports = new ArrayList<>();
        for (RawPlacedInfo info : existingInfos) {
            if (info.slot < 0 || info.on >= 0 || coveredSlots.contains(Integer.valueOf(info.slot))) {
                continue;
            }
            if (!info.flat && info.extY > itemSize * 0.25F) {
                continue;
            }
            int surfIdx = findSurfaceIndex(surfaces, info.x, info.y, info.z);
            if (surfIdx >= 0) {
                supports.add(new SupportCandidate(
                        info.slot, surfIdx, info.x, info.z, info.top, info.halfX, info.halfZ, info.scale));
            }
        }

        Random rnd = new Random(mixSeed(seed, slotIdx));

        // Shuffle surfaces weighted by area^(0.75) so main shelves hold most loot while pockets/drawers participate.
        List<SurfaceScore> order = new ArrayList<>(surfaces.size());
        for (int sIdx = 0; sIdx < surfaces.size(); sIdx++) {
            Object surf = surfaces.get(sIdx);
            Object from = surfaceFromMethod.invoke(surf);
            Object to = surfaceToMethod.invoke(surf);
            float fx = ((Float) vec2XMethod.invoke(from)).floatValue();
            float fz = ((Float) vec2YMethod.invoke(from)).floatValue();
            float tx = ((Float) vec2XMethod.invoke(to)).floatValue();
            float tz = ((Float) vec2YMethod.invoke(to)).floatValue();
            double area = Math.max(1.0, (double) Math.abs(tx - fx) * (double) Math.abs(tz - fz));
            double u = Math.max(1.0e-4, rnd.nextDouble());
            double key = -Math.log(u) / Math.pow(area, 0.75);
            order.add(new SurfaceScore(sIdx, key));
        }
        Collections.sort(order, (a, b) -> Double.compare(a.weightKey, b.weightKey));

        float preferredScale = clamp(0.76F + rnd.nextFloat() * 0.36F, rangeMin, rangeMax);
        float[] scaleTries = buildScaleTries(preferredScale, rangeMin);

        for (SurfaceScore ss : order) {
            int surfIdx = ss.index;
            Object surf = surfaces.get(surfIdx);
            Object from = surfaceFromMethod.invoke(surf);
            Object to = surfaceToMethod.invoke(surf);
            float fx = ((Float) vec2XMethod.invoke(from)).floatValue();
            float fz = ((Float) vec2YMethod.invoke(from)).floatValue();
            float tx = ((Float) vec2XMethod.invoke(to)).floatValue();
            float tz = ((Float) vec2YMethod.invoke(to)).floatValue();
            float minSurfX = Math.min(fx, tx);
            float maxSurfX = Math.max(fx, tx);
            float minSurfZ = Math.min(fz, tz);
            float maxSurfZ = Math.max(fz, tz);

            float surfHeight = ((Float) surfaceHeightMethod.invoke(surf)).floatValue();
            float clearance = ((Float) surfaceClearanceMethod.invoke(surf)).floatValue();
            boolean flatLies = surfaceFlatLiesMethod != null
                    && ((Boolean) surfaceFlatLiesMethod.invoke(surf)).booleanValue();
            Set<?> openSides = (Set<?>) surfaceOpenMethod.invoke(surf);

            boolean openRight = hasOpenSide(openSides, "RIGHT");
            boolean openLeft = hasOpenSide(openSides, "LEFT");
            boolean openFront = hasOpenSide(openSides, "FRONT");
            boolean openBack = hasOpenSide(openSides, "BACK");

            boolean narrow = (maxSurfX - minSurfX <= NARROW_THRESHOLD)
                    || (maxSurfZ - minSurfZ <= NARROW_THRESHOLD);
            float wallPad = narrow ? WALL_PAD_NARROW : WALL_PAD;
            float openPad = narrow ? OPEN_PAD_NARROW : OPEN_PAD;

            float padLeft = openLeft ? openPad : wallPad;
            float padRight = openRight ? openPad : wallPad;
            float padFront = openFront ? openPad : wallPad;
            float padBack = openBack ? openPad : wallPad;

            float[] poseAngles = choosePoseAngles(rnd, isFlat, flatLies);
            boolean primaryLying = poseAngles[0] > 0.5F;
            float yawRad = poseAngles[1];
            float altYawRad = poseAngles[2];
            boolean allowAltFlip = poseAngles[3] > 0.5F;

            for (int flipPass = 0; flipPass < (allowAltFlip ? 2 : 1); flipPass++) {
                boolean lying = (flipPass == 0) ? primaryLying : !primaryLying;
                float useYaw = (flipPass == 0) ? yawRad : altYawRad;
                Object rot = buildRotation(useYaw, lying);

                for (float scale : scaleTries) {
                    Vector3f ext = (Vector3f) placedShapeExtentsMethod.invoke(
                            shapeObj, rot, itemSize * scale);
                    float halfY = Math.max(ext.y, ownLeast);
                    if (halfY * 2.0F > clearance - CEILING_CLEARANCE_PAD) {
                        continue;
                    }
                    float baseHalfX = Math.max(ext.x, ownLeast);
                    float baseHalfZ = Math.max(ext.z, ownLeast);

                    // 25% chance to stack neatly on a flat/low support item already resting on this surface.
                    if (flipPass == 0 && (isFlat || halfY <= itemSize * 0.35F)
                            && !supports.isEmpty() && rnd.nextFloat() < 0.25F) {
                        Object stacked = tryPlaceOnSupport(
                                supports, surfIdx, surfHeight, clearance,
                                minSurfX + padRight, maxSurfX - padLeft,
                                minSurfZ + padFront, maxSurfZ - padBack,
                                taken, ext.x, ext.z, baseHalfX, halfY, baseHalfZ,
                                rot, scale, rnd);
                        if (stacked != null) {
                            return stacked;
                        }
                    }

                    // Surface placement bounds (respecting walls and open edges).
                    float minX = minSurfX + padRight + baseHalfX + NORMAL_GAP;
                    float maxX = maxSurfX - padLeft - baseHalfX - NORMAL_GAP;
                    float minZ = minSurfZ + padFront + baseHalfZ + NORMAL_GAP;
                    float maxZ = maxSurfZ - padBack - baseHalfZ - NORMAL_GAP;
                    if (minX > maxX || minZ > maxZ) {
                        continue;
                    }

                    float bottom = surfHeight;
                    float top = bottom + halfY * 2.0F;

                    // Phase 1: comfortable airy spacing (when surface has generous room).
                    if ((maxX - minX) >= 1.5F && (maxZ - minZ) >= 1.5F) {
                        float cx = baseHalfX + COMFORT_GAP;
                        float cz = baseHalfZ + COMFORT_GAP;
                        for (int t = 0; t < 16; t++) {
                            float rx = minX + rnd.nextFloat() * (maxX - minX);
                            float rz = minZ + rnd.nextFloat() * (maxZ - minZ);
                            if (isClear(taken, rx - cx, rx + cx, bottom, top, rz - cz, rz + cz)) {
                                return itemPoseCtor.newInstance(
                                        rx, bottom, rz, halfY, rot, scale, -1);
                            }
                        }
                    }

                    // Phase 2: tight random scatter across the shelf/drawer.
                    float nx = baseHalfX + NORMAL_GAP;
                    float nz = baseHalfZ + NORMAL_GAP;
                    for (int t = 0; t < 28; t++) {
                        float rx = minX + rnd.nextFloat() * (maxX - minX);
                        float rz = minZ + rnd.nextFloat() * (maxZ - minZ);
                        if (isClear(taken, rx - nx, rx + nx, bottom, top, rz - nz, rz + nz)) {
                            return itemPoseCtor.newInstance(
                                    rx, bottom, rz, halfY, rot, scale, -1);
                        }
                    }

                    // Phase 3: shuffled jittered-grid sweep so crowded shelves still find any free pocket naturally.
                    List<float[]> candidates = new ArrayList<>();
                    for (float gx = minX; gx <= maxX + 1.0e-3F; gx += 0.50F) {
                        float cx = Math.min(gx, maxX);
                        for (float gz = minZ; gz <= maxZ + 1.0e-3F; gz += 0.50F) {
                            float cz = Math.min(gz, maxZ);
                            candidates.add(new float[]{cx, cz});
                        }
                    }
                    Collections.shuffle(candidates, rnd);
                    for (float[] cand : candidates) {
                        float jx = clamp(cand[0] + (rnd.nextFloat() - 0.5F) * 0.25F, minX, maxX);
                        float jz = clamp(cand[1] + (rnd.nextFloat() - 0.5F) * 0.25F, minZ, maxZ);
                        if (isClear(taken, jx - nx, jx + nx, bottom, top, jz - nz, jz + nz)) {
                            return itemPoseCtor.newInstance(
                                    jx, bottom, jz, halfY, rot, scale, -1);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Object tryPlaceOnSupport(List<SupportCandidate> supports, int surfIdx,
            float surfHeight, float clearance, float safeMinX, float safeMaxX,
            float safeMinZ, float safeMaxZ, List<float[]> taken,
            float extX, float extZ, float baseHalfX, float halfY, float baseHalfZ,
            Object rot, float scale, Random rnd) throws Throwable {
        List<SupportCandidate> local = new ArrayList<>();
        for (SupportCandidate s : supports) {
            if (s.surfaceIndex != surfIdx) continue;
            if (scale > s.scale + 0.05F) continue;
            if (s.halfX < extX * 0.72F || s.halfZ < extZ * 0.72F) continue;
            if (s.top + halfY * 2.0F > surfHeight + clearance - CEILING_CLEARANCE_PAD) continue;
            local.add(s);
        }
        if (local.isEmpty()) return null;
        Collections.shuffle(local, rnd);
        float maxYLimit = surfHeight + clearance - CEILING_CLEARANCE_PAD;
        for (SupportCandidate sup : local) {
            float offX = (rnd.nextFloat() * 2.0F - 1.0F) * Math.min(0.28F, sup.halfX * 0.30F);
            float offZ = (rnd.nextFloat() * 2.0F - 1.0F) * Math.min(0.28F, sup.halfZ * 0.30F);
            float xLow = safeMinX + baseHalfX + NORMAL_GAP;
            float xHigh = safeMaxX - baseHalfX - NORMAL_GAP;
            float zLow = safeMinZ + baseHalfZ + NORMAL_GAP;
            float zHigh = safeMaxZ - baseHalfZ - NORMAL_GAP;
            if (xLow > xHigh || zLow > zHigh) continue;
            float px = clamp(sup.x + offX, xLow, xHigh);
            float pz = clamp(sup.z + offZ, zLow, zHigh);
            float py = sup.top;
            float pTop = py + halfY * 2.0F;
            if (pTop > maxYLimit) continue;
            float cx = baseHalfX + NORMAL_GAP;
            float cz = baseHalfZ + NORMAL_GAP;
            if (isClear(taken, px - cx, px + cx, py, pTop, pz - cz, pz + cz)) {
                return itemPoseCtor.newInstance(px, py, pz, halfY, rot, scale, sup.slot);
            }
        }
        return null;
    }

    /**
     * Synchronizes any client-computed scatter poses back to the server once the player opens or
     * focuses the container (accepts {@code StationView} / {@code ClientStation}).
     */
    public static void trySyncFocusedStation(Object viewObj) {
        if (viewObj == null) return;
        ensureClientApi();
        ensureBehaviorApi();
        if (!apiReady) return;
        try {
            Object posObj = viewPosMethod != null
                    ? viewPosMethod.invoke(viewObj)
                    : clientStationPosMethod.invoke(viewObj);
            if (!(posObj instanceof BlockPos pos)) return;
            Object state = viewStateMethod != null
                    ? viewStateMethod.invoke(viewObj)
                    : clientStationStateMethod.invoke(viewObj);
            syncStationWithPosAndState(viewObj, pos, state);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshIdleScatterIfNeeded(Object viewObj) {
        if (viewObj == null) return;
        ensureClientApi();
        ensureBehaviorApi();
        if (!apiReady) return;
        try {
            Object state = viewStateMethod != null
                    ? viewStateMethod.invoke(viewObj)
                    : clientStationStateMethod.invoke(viewObj);
            long seed = readScatterSeed(state);
            if (seed == 0L) return;
            Object sceneObj = clientStationSceneField.get(viewObj);
            if (sceneObj == null) return;
            Long appliedSeed;
            synchronized (LOCK) {
                appliedSeed = SCENE_SEEDS.get(sceneObj);
            }
            if (appliedSeed != null && appliedSeed.longValue() == seed) return;
            Object placedRaw = scenePlacedField.get(sceneObj);
            if (!(placedRaw instanceof List<?> placedList)) return;
            boolean resetAny = false;
            for (Object nodeObj : placedList) {
                if (nodeObj == null) continue;
                Object pItem = nodePlacedMethod.invoke(nodeObj);
                if (pItem == null) continue;
                ItemStack stack = (ItemStack) placedItemStackMethod.invoke(pItem);
                if (stack == null || stack.isEmpty()) continue;
                Object slot = placedItemSlotMethod.invoke(pItem);
                if (stateGetPoseMethod.invoke(state, slot) == null) {
                    placedItemGuessedField.set(pItem, null);
                    resetAny = true;
                }
            }
            synchronized (LOCK) {
                SCENE_SEEDS.put(sceneObj, Long.valueOf(seed));
                KNOWN_STATIONS.put(viewObj, Boolean.TRUE);
            }
            if (resetAny) {
                sceneUpdateMethod.invoke(sceneObj, state, 0.0F);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void trySyncStation(Object stationObj) {
        if (stationObj == null || !apiReady) return;
        try {
            Object posObj = clientStationPosMethod.invoke(stationObj);
            if (!(posObj instanceof BlockPos pos)) return;
            Object state = clientStationStateMethod.invoke(stationObj);
            syncStationWithPosAndState(stationObj, pos, state);
        } catch (Throwable ignored) {
        }
    }

    private static void syncStationWithPosAndState(Object stationObj, BlockPos pos, Object state)
            throws Throwable {
        long seed = readScatterSeed(state);
        if (seed == 0L) {
            synchronized (LOCK) {
                SENT_BY_POS.remove(pos);
            }
            return;
        }
        Object sceneObj = clientStationSceneField.get(stationObj);
        if (sceneObj == null) return;
        Object placedRaw = scenePlacedField.get(sceneObj);
        if (!(placedRaw instanceof List<?> placedList)) return;

        Long appliedSeed;
        synchronized (LOCK) {
            appliedSeed = SCENE_SEEDS.get(sceneObj);
        }
        if (appliedSeed == null || appliedSeed.longValue() != seed) {
            boolean resetAny = false;
            for (Object nodeObj : placedList) {
                if (nodeObj == null) continue;
                Object pItem = nodePlacedMethod.invoke(nodeObj);
                if (pItem == null) continue;
                ItemStack stack = (ItemStack) placedItemStackMethod.invoke(pItem);
                if (stack == null || stack.isEmpty()) continue;
                Object slot = placedItemSlotMethod.invoke(pItem);
                if (stateGetPoseMethod.invoke(state, slot) == null) {
                    placedItemGuessedField.set(pItem, null);
                    resetAny = true;
                }
            }
            synchronized (LOCK) {
                SCENE_SEEDS.put(sceneObj, Long.valueOf(seed));
            }
            if (resetAny) {
                sceneUpdateMethod.invoke(sceneObj, state, 0.0F);
            }
        }

        List<ChestDiegesisCompat436.PoseEntry> entries = new ArrayList<>();
        for (Object nodeObj : placedList) {
            if (nodeObj == null) continue;
            Object pItem = nodePlacedMethod.invoke(nodeObj);
            if (pItem == null) continue;
            ItemStack stack = (ItemStack) placedItemStackMethod.invoke(pItem);
            if (stack == null || stack.isEmpty()) continue;
            Object slot = placedItemSlotMethod.invoke(pItem);
            if (stateGetPoseMethod.invoke(state, slot) != null) continue;
            Object pose = placedItemPoseMethod.invoke(pItem);
            if (pose == null) continue;

            int slotIdx = ((Integer) slotRefIndexMethod.invoke(slot)).intValue();
            float x = ((Float) poseXMethod.invoke(pose)).floatValue();
            float y = ((Float) poseYMethod.invoke(pose)).floatValue();
            float z = ((Float) poseZMethod.invoke(pose)).floatValue();
            float lift = ((Float) poseLiftMethod.invoke(pose)).floatValue();
            float scale = ((Float) poseScaleMethod.invoke(pose)).floatValue();
            int on = ((Integer) poseOnMethod.invoke(pose)).intValue();
            Object rot = poseRotationMethod.invoke(pose);
            float qx = ((Float) quatXMethod.invoke(rot)).floatValue();
            float qy = ((Float) quatYMethod.invoke(rot)).floatValue();
            float qz = ((Float) quatZMethod.invoke(rot)).floatValue();
            float qw = ((Float) quatWMethod.invoke(rot)).floatValue();

            entries.add(new ChestDiegesisCompat436.PoseEntry(
                    slotIdx, x, y, z, lift, qx, qy, qz, qw, scale, on));
        }
        if (entries.isEmpty()) return;

        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            SentEntry prev = SENT_BY_POS.get(pos);
            if (prev != null && prev.seed == seed && (now - prev.sentAtMs) < SEND_COOLDOWN_MS) {
                return;
            }
            SENT_BY_POS.put(pos.immutable(), new SentEntry(seed, now));
        }

        String wire = ChestDiegesisCompat436.encodePoses(entries);
        if (!wire.isEmpty()) {
            ChestLootNet436.sendScatterPoses(pos, seed, wire);
        }
    }

    private static void clearSendCooldown(Object viewObj) {
        if (viewObj == null) return;
        try {
            Object posObj = viewPosMethod != null
                    ? viewPosMethod.invoke(viewObj)
                    : (clientStationPosMethod != null ? clientStationPosMethod.invoke(viewObj) : null);
            if (posObj instanceof BlockPos pos) {
                synchronized (LOCK) {
                    SENT_BY_POS.remove(pos);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isViewFocused(Object viewObj) {
        if (viewObj == null || viewIsFocusedMethod == null) return false;
        try {
            Object res = viewIsFocusedMethod.invoke(viewObj);
            return res instanceof Boolean b && b.booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float[] choosePoseAngles(Random rnd, boolean isFlat, boolean flatLies) {
        float fullYaw = rnd.nextFloat() * TWO_PI;
        float frontYaw = (rnd.nextFloat() * 2.0F - 1.0F) * (float) Math.toRadians(52.0);
        if (flatLies) {
            if (isFlat) {
                return new float[]{1.0F, fullYaw, fullYaw, 0.0F};
            }
            return new float[]{0.0F, fullYaw, fullYaw, 1.0F};
        }
        if (isFlat) {
            boolean standPrimary = rnd.nextFloat() < 0.65F;
            return standPrimary
                    ? new float[]{0.0F, frontYaw, fullYaw, 1.0F}
                    : new float[]{1.0F, fullYaw, frontYaw, 1.0F};
        }
        return new float[]{0.0F, fullYaw, fullYaw, 0.0F};
    }

    private static Object buildRotation(float yawRad, boolean lying) throws Throwable {
        float half = yawRad * 0.5F;
        float cy = (float) Math.cos(half);
        float sy = (float) Math.sin(half);
        if (!lying) {
            return quat4fCtor.newInstance(0.0F, sy, 0.0F, cy);
        }
        return quat4fCtor.newInstance(cy * SQRT_HALF, sy * SQRT_HALF, -sy * SQRT_HALF, cy * SQRT_HALF);
    }

    private static float[] buildScaleTries(float preferred, float minScale) {
        List<Float> vals = new ArrayList<>(5);
        float[] factors = {1.00F, 0.85F, 0.72F, 0.60F};
        for (float f : factors) {
            float s = Math.max(minScale, preferred * f);
            if (vals.isEmpty() || Math.abs(vals.get(vals.size() - 1).floatValue() - s) > 1.0e-3F) {
                vals.add(Float.valueOf(s));
            }
        }
        if (vals.isEmpty() || Math.abs(vals.get(vals.size() - 1).floatValue() - minScale) > 1.0e-3F) {
            vals.add(Float.valueOf(minScale));
        }
        float[] out = new float[vals.size()];
        for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i).floatValue();
        return out;
    }

    private static boolean hasOpenSide(Set<?> openSides, String name) {
        if (openSides == null || openSides.isEmpty()) return false;
        for (Object side : openSides) {
            if (side instanceof Enum<?> e && name.equals(e.name())) return true;
        }
        return false;
    }

    private static int findSurfaceIndex(List<?> surfaces, float x, float y, float z) throws Throwable {
        int best = -1;
        float bestHeight = -Float.MAX_VALUE;
        for (int i = 0; i < surfaces.size(); i++) {
            Object surf = surfaces.get(i);
            Object from = surfaceFromMethod.invoke(surf);
            Object to = surfaceToMethod.invoke(surf);
            float fx = ((Float) vec2XMethod.invoke(from)).floatValue();
            float fz = ((Float) vec2YMethod.invoke(from)).floatValue();
            float tx = ((Float) vec2XMethod.invoke(to)).floatValue();
            float tz = ((Float) vec2YMethod.invoke(to)).floatValue();
            float h = ((Float) surfaceHeightMethod.invoke(surf)).floatValue();
            float cl = ((Float) surfaceClearanceMethod.invoke(surf)).floatValue();
            float loX = Math.min(fx, tx);
            float hiX = Math.max(fx, tx);
            float loZ = Math.min(fz, tz);
            float hiZ = Math.max(fz, tz);
            if (x >= loX - 0.01F && x <= hiX + 0.01F && z >= loZ - 0.01F && z <= hiZ + 0.01F
                    && y >= h - 0.01F && y <= h + cl && h > bestHeight) {
                bestHeight = h;
                best = i;
            }
        }
        return best;
    }

    private static boolean isClear(List<float[]> taken, float minX, float maxX,
            float minY, float maxY, float minZ, float maxZ) {
        for (float[] box : taken) {
            if (box[1] > minX && box[0] < maxX
                    && box[3] > minY + 0.002F && box[2] < maxY - 0.002F
                    && box[5] > minZ && box[4] < maxZ) {
                return false;
            }
        }
        return true;
    }

    private static float clamp(float val, float min, float max) {
        return val < min ? min : (val > max ? max : val);
    }

    private static long mixSeed(long seed, int slotIdx) {
        long z = seed ^ ((long) (slotIdx + 1) * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long readScatterSeed(Object state) {
        if (state == null || stateDataMethod == null) return 0L;
        try {
            Object dataObj = stateDataMethod.invoke(state);
            if (!(dataObj instanceof CompoundTag tag)
                    || !tag.contains(ChestDiegesisCompat436.SCATTER_SEED_KEY)) {
                return 0L;
            }
            return tag.getLong(ChestDiegesisCompat436.SCATTER_SEED_KEY);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static final class RawPlacedInfo {
        final int slot;
        final int on;
        final float x;
        final float y;
        final float z;
        final float top;
        final float halfX;
        final float halfZ;
        final float extY;
        final float scale;
        final boolean flat;

        RawPlacedInfo(int slot, int on, float x, float y, float z, float top,
                float halfX, float halfZ, float extY, float scale, boolean flat) {
            this.slot = slot;
            this.on = on;
            this.x = x;
            this.y = y;
            this.z = z;
            this.top = top;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.extY = extY;
            this.scale = scale;
            this.flat = flat;
        }
    }

    private static void ensureClientApi() {
        if (apiProbed) return;
        synchronized (DiegesisScatterClient439.class) {
            if (apiProbed) return;
            try {
                Class<?> sceneCls = Class.forName("com.pockyl.diegesis.impl.client.scene.SceneInstance");
                Class<?> stationCls = Class.forName("com.pockyl.diegesis.impl.client.ClientStation");
                Class<?> nodeCls = Class.forName("com.pockyl.diegesis.impl.client.scene.NodeInstance");
                Class<?> pItemCls = Class.forName("com.pockyl.diegesis.impl.client.scene.PlacedItem");
                Class<?> pShapeCls = Class.forName("com.pockyl.diegesis.impl.client.scene.PlacedShape");
                Class<?> optionsCls = Class.forName("com.pockyl.diegesis.api.scene.PlacingOptions");
                Class<?> surfaceCls = Class.forName("com.pockyl.diegesis.api.scene.PlaceSurface");
                Class<?> stateCls = Class.forName("com.pockyl.diegesis.api.state.StationState");
                Class<?> slotRefCls = Class.forName("com.pockyl.diegesis.api.state.SlotRef");
                Class<?> poseCls = Class.forName("com.pockyl.diegesis.api.state.ItemPose");
                Class<?> vec2Cls = Class.forName("org.joml.Vector2fc");
                Class<?> quatFcCls = Class.forName("org.joml.Quaternionfc");

                sceneStationField = sceneCls.getDeclaredField("station");
                sceneStationField.setAccessible(true);
                scenePlacedField = sceneCls.getDeclaredField("placed");
                scenePlacedField.setAccessible(true);
                scenePlacingOptionsField = sceneCls.getDeclaredField("placingOptions");
                scenePlacingOptionsField.setAccessible(true);
                sceneUpdateMethod = sceneCls.getMethod("update", stateCls, float.class);

                clientStationSceneField = stationCls.getDeclaredField("scene");
                clientStationSceneField.setAccessible(true);
                clientStationStateMethod = stationCls.getMethod("state");
                clientStationPosMethod = stationCls.getMethod("pos");
                try {
                    clientStationFocusedMethod = stationCls.getMethod("isFocused");
                } catch (Throwable ignored) {
                    clientStationFocusedMethod = null;
                }

                nodePlacedMethod = nodeCls.getMethod("placed");

                placedItemGuessedField = pItemCls.getDeclaredField("guessed");
                placedItemGuessedField.setAccessible(true);
                placedItemSlotMethod = pItemCls.getMethod("slot");
                placedItemStackMethod = pItemCls.getMethod("stack");
                placedItemShapeMethod = pItemCls.getMethod("shape");
                placedItemPoseMethod = pItemCls.getMethod("pose");

                placedShapeExtentsMethod = pShapeCls.getMethod("extents", quatFcCls, float.class);
                placedShapeIsFlatMethod = pShapeCls.getMethod("isFlat");

                optionsItemSizeMethod = optionsCls.getMethod("itemSize");
                optionsScaleRangeMethod = optionsCls.getMethod("scaleRange");
                optionsSurfacesMethod = optionsCls.getMethod("surfaces");

                surfaceFromMethod = surfaceCls.getMethod("from");
                surfaceToMethod = surfaceCls.getMethod("to");
                surfaceHeightMethod = surfaceCls.getMethod("height");
                surfaceClearanceMethod = surfaceCls.getMethod("clearance");
                surfaceOpenMethod = surfaceCls.getMethod("open");
                try {
                    surfaceFlatLiesMethod = surfaceCls.getMethod("flatLies");
                } catch (Throwable ignored) {
                    surfaceFlatLiesMethod = null;
                }

                vec2XMethod = vec2Cls.getMethod("x");
                vec2YMethod = vec2Cls.getMethod("y");
                try {
                    Class<?> placingCls = Class.forName("com.pockyl.diegesis.impl.client.scene.Placing");
                    Class<?> shapeIfaceCls = Class.forName("com.pockyl.diegesis.impl.client.scene.Placing$Shape");
                    placingLeastHalfMethod = placingCls.getMethod("leastHalf", optionsCls, shapeIfaceCls);
                } catch (Throwable ignored) {
                    placingLeastHalfMethod = null;
                }

                stateDataMethod = stateCls.getMethod("data");
                stateGetPoseMethod = stateCls.getMethod("getPose", slotRefCls);
                slotRefIndexMethod = slotRefCls.getMethod("index");

                itemPoseCtor = poseCls.getConstructor(float.class, float.class, float.class, float.class,
                        quatFcCls, float.class, int.class);
                poseXMethod = poseCls.getMethod("x");
                poseYMethod = poseCls.getMethod("y");
                poseZMethod = poseCls.getMethod("z");
                poseLiftMethod = poseCls.getMethod("lift");
                poseRotationMethod = poseCls.getMethod("rotation");
                poseScaleMethod = poseCls.getMethod("scale");
                poseOnMethod = poseCls.getMethod("on");
                poseTopMethod = poseCls.getMethod("top");

                quatXMethod = quatFcCls.getMethod("x");
                quatYMethod = quatFcCls.getMethod("y");
                quatZMethod = quatFcCls.getMethod("z");
                quatWMethod = quatFcCls.getMethod("w");
                quat4fCtor = Quaternionf.class.getConstructor(float.class, float.class, float.class, float.class);
                apiReady = true;
            } catch (Throwable ignored) {
                apiReady = false;
            }
            apiProbed = true;
        }
    }

    private static void ensureBehaviorApi() {
        if (behaviorApiProbed) return;
        synchronized (DiegesisScatterClient439.class) {
            if (behaviorApiProbed) return;
            try {
                Class<?> clientCls = Class.forName("com.pockyl.diegesis.api.client.DiegesisClient");
                Class<?> fCls = Class.forName("com.pockyl.diegesis.api.client.StationBehavior$Factory");
                Class<?> bCls = Class.forName("com.pockyl.diegesis.api.client.StationBehavior");
                Class<?> vCls = Class.forName("com.pockyl.diegesis.api.client.StationView");

                diegesisAddBehaviorMethod = clientCls.getMethod("addBehavior", fCls);
                viewIsFocusedMethod = vCls.getMethod("isFocused");
                viewPosMethod = vCls.getMethod("pos");
                viewStateMethod = vCls.getMethod("state");
                factoryIface = fCls;
                behaviorIface = bCls;
            } catch (Throwable ignored) {
                diegesisAddBehaviorMethod = null;
                factoryIface = null;
                behaviorIface = null;
            }
            behaviorApiProbed = true;
        }
    }
}
