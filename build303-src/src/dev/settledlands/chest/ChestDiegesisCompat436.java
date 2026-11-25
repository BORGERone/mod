package dev.settledlands.chest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;

/**
 * Build 436 + Build 439: optional soft-compat bridge to {@code diegesis} and
 * {@code handson_furniture} via cached reflection. Safe with zero runtime classes from either mod
 * present.
 *
 * <p>Build 439 adds {@link #markLootScatter}, {@link #encodePoses}, {@link #decodePoses}, and
 * {@link #commitScatteredPoses} so dungeon loot containers tagged with {@code SettledLandsScatterSeed}
 * receive natural client-measured 3D poses (using exact {@code PlacedShape}/{@code PlaceSurface}
 * geometry) that are committed to the server {@code StationState} once on first open.</p>
 */
public final class ChestDiegesisCompat436 {

    public static final String SCATTER_SEED_KEY = "SettledLandsScatterSeed";
    public static final String ITEMS_GROUP = "items";
    private static final int MAX_POSE_ENTRIES = 512;
    private static final int MAX_ENCODED_LEN = 30_000;
    private static final double MAX_COMMIT_DIST_SQR = 64.0 * 64.0;

    public static final class PoseEntry {
        public final int slot;
        public final float x;
        public final float y;
        public final float z;
        public final float lift;
        public final float qx;
        public final float qy;
        public final float qz;
        public final float qw;
        public final float scale;
        public final int on;

        public PoseEntry(int slot, float x, float y, float z, float lift,
                float qx, float qy, float qz, float qw, float scale, int on) {
            this.slot = slot;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lift = lift;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
            this.scale = scale;
            this.on = on;
        }
    }

    private static volatile boolean diegesisProbed;
    private static Method activeStationMethod;
    private static Method stationPlayersMethod;

    private static volatile boolean roomyProbed;
    private static Class<?> roomyClass;
    private static Method ownSizeMethod;

    private static volatile boolean stateApiProbed;
    private static Class<?> stationHostClass;
    private static Method hostStateMethod;
    private static Method stateDataMethod;
    private static Method stateSetDataMethod;
    private static Method stateSchemaMethod;
    private static Method schemaSlotGroupsMethod;
    private static Method slotGroupSizeMethod;
    private static Method stateGetItemMethod;
    private static Method stateGetPoseMethod;
    private static Method stateSetPoseMethod;
    private static Method statePollStoragesMethod;
    private static Method slotRefOfMethod;
    private static Constructor<?> itemPoseCtor;
    private static Method itemPoseSanitizedMethod;
    private static Constructor<?> quat4fCtor;

    private ChestDiegesisCompat436() {}

    /**
     * Returns true when {@code player} currently has an open 3D {@code diegesis} station at {@code pos}.
     */
    public static boolean isStationOpen(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) return false;
        ensureDiegesis();
        if (activeStationMethod == null || stationPlayersMethod == null) return false;
        try {
            Object optObj = activeStationMethod.invoke(null, level, pos);
            if (!(optObj instanceof Optional<?> opt) || opt.isEmpty()) return false;
            Object playersObj = stationPlayersMethod.invoke(opt.get());
            return playersObj instanceof Collection<?> col && col.contains(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * When {@code handson_furniture} is loaded, {@code BasicLootBlockEntityMixin} wraps furniture
     * containers in {@code RoomyContainer} and expands {@link Container#getContainerSize()} to 160+
     * 3D scene slots. Returning {@code handson$ownSize()} ensures dungeon loot lands inside the
     * furniture's native inventory slots (visible both in 3D stations and standard Refurbished GUIs).
     */
    public static int usableSlotCount(Object blockEntity, Container container) {
        if (container == null) return 0;
        int total = container.getContainerSize();
        if (total <= 0) return 0;
        if (blockEntity == null) return total;
        ensureRoomy();
        if (roomyClass != null && ownSizeMethod != null && roomyClass.isInstance(blockEntity)) {
            try {
                Object val = ownSizeMethod.invoke(blockEntity);
                if (val instanceof Integer own && own > 0 && own <= total) {
                    return own;
                }
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    /**
     * Marks a {@code StationHost} container (Hands-On Furniture / Diegesis) with a deterministic
     * scatter seed after dungeon loot generation, clearing any stale poses on populated or empty
     * slots while keeping untouched pre-existing placed items intact.
     */
    public static boolean markLootScatter(Object blockEntity, Collection<Integer> populatedSlots, long seed) {
        if (blockEntity == null) return false;
        ensureStateApi();
        if (stationHostClass == null || !stationHostClass.isInstance(blockEntity)) return false;
        long nonZeroSeed = seed != 0L ? seed : 0x5E771EDL;
        try {
            Object state = hostStateMethod.invoke(blockEntity);
            if (state == null) return false;

            int itemsSize = itemsGroupSize(state);
            if (itemsSize > 0 && slotRefOfMethod != null && stateSetPoseMethod != null) {
                if (populatedSlots != null) {
                    for (Integer slotObj : populatedSlots) {
                        if (slotObj == null) continue;
                        int slot = slotObj.intValue();
                        if (slot >= 0 && slot < itemsSize) {
                            Object ref = slotRefOfMethod.invoke(null, ITEMS_GROUP, slot);
                            if (stateGetPoseMethod.invoke(state, ref) != null) {
                                stateSetPoseMethod.invoke(state, ref, null);
                            }
                        }
                    }
                }
                if (stateGetItemMethod != null) {
                    for (int i = 0; i < itemsSize; i++) {
                        Object ref = slotRefOfMethod.invoke(null, ITEMS_GROUP, i);
                        if (stateGetPoseMethod.invoke(state, ref) != null) {
                            Object stackObj = stateGetItemMethod.invoke(state, ref);
                            if (stackObj instanceof ItemStack stack && stack.isEmpty()) {
                                stateSetPoseMethod.invoke(state, ref, null);
                            }
                        }
                    }
                }
            }

            Object dataObj = stateDataMethod.invoke(state);
            CompoundTag data = dataObj instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
            data.putLong(SCATTER_SEED_KEY, nonZeroSeed);
            stateSetDataMethod.invoke(state, data);

            if (statePollStoragesMethod != null) {
                statePollStoragesMethod.invoke(state);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Encodes a list of client-computed item poses into a compact Base64 wire payload. */
    public static String encodePoses(List<PoseEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        int count = Math.min(entries.size(), MAX_POSE_ENTRIES);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4 + count * 40);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(count);
            for (int i = 0; i < count; i++) {
                PoseEntry e = entries.get(i);
                dos.writeShort(e.slot);
                dos.writeFloat(e.x);
                dos.writeFloat(e.y);
                dos.writeFloat(e.z);
                dos.writeFloat(e.lift);
                dos.writeFloat(e.qx);
                dos.writeFloat(e.qy);
                dos.writeFloat(e.qz);
                dos.writeFloat(e.qw);
                dos.writeFloat(e.scale);
                dos.writeShort(e.on);
            }
            dos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Decodes and finite-validates a Base64 wire payload of {@link PoseEntry} records. */
    public static List<PoseEntry> decodePoses(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_ENCODED_LEN) {
            return Collections.emptyList();
        }
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length < 4) return Collections.emptyList();
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
            int count = dis.readInt();
            if (count <= 0 || count > MAX_POSE_ENTRIES) return Collections.emptyList();
            if (raw.length != 4 + count * 40) return Collections.emptyList();
            List<PoseEntry> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int slot = dis.readShort();
                float x = dis.readFloat();
                float y = dis.readFloat();
                float z = dis.readFloat();
                float lift = dis.readFloat();
                float qx = dis.readFloat();
                float qy = dis.readFloat();
                float qz = dis.readFloat();
                float qw = dis.readFloat();
                float scale = dis.readFloat();
                int on = dis.readShort();
                if (slot < 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                        || !Float.isFinite(lift) || !Float.isFinite(qx) || !Float.isFinite(qy)
                        || !Float.isFinite(qz) || !Float.isFinite(qw) || !Float.isFinite(scale)) {
                    continue;
                }
                out.add(new PoseEntry(slot, x, y, z, lift, qx, qy, qz, qw, scale, on));
            }
            return out;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Server-side handler for committed client scatter poses: verifies proximity, chunk load, and
     * matching {@code SettledLandsScatterSeed}, sanitizes each pose, writes poses into
     * {@code StationState}, clears the seed so no second client can overwrite them, and marks the
     * {@code BlockEntity} dirty for saving/sync.
     */
    public static boolean commitScatteredPoses(ServerPlayer player, BlockPos pos, long expectedSeed,
            String encodedPoses) {
        if (player == null || !player.isAlive() || pos == null || expectedSeed == 0L) return false;
        ServerLevel level = player.serverLevel();
        if (level == null || !level.isLoaded(pos)) return false;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_COMMIT_DIST_SQR) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container)) return false;

        ensureStateApi();
        if (stationHostClass == null || !stationHostClass.isInstance(be)
                || itemPoseCtor == null || itemPoseSanitizedMethod == null || quat4fCtor == null) {
            return false;
        }
        try {
            Object state = hostStateMethod.invoke(be);
            if (state == null) return false;
            Object dataObj = stateDataMethod.invoke(state);
            if (!(dataObj instanceof CompoundTag data) || !data.contains(SCATTER_SEED_KEY)) {
                return false;
            }
            if (data.getLong(SCATTER_SEED_KEY) != expectedSeed) {
                return false;
            }

            int itemsSize = itemsGroupSize(state);
            if (itemsSize <= 0) return false;

            List<PoseEntry> entries = decodePoses(encodedPoses);
            if (entries.isEmpty()) return false;

            Map<Integer, Integer> validSupport = new HashMap<>();
            for (PoseEntry e : entries) {
                if (e.slot >= 0 && e.slot < itemsSize) {
                    Object ref = slotRefOfMethod.invoke(null, ITEMS_GROUP, e.slot);
                    Object stackObj = stateGetItemMethod.invoke(state, ref);
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                        validSupport.put(e.slot, e.on);
                    }
                }
            }

            for (PoseEntry e : entries) {
                if (e.slot < 0 || e.slot >= itemsSize) continue;
                Object ref = slotRefOfMethod.invoke(null, ITEMS_GROUP, e.slot);
                Object stackObj = stateGetItemMethod.invoke(state, ref);
                if (!(stackObj instanceof ItemStack stack) || stack.isEmpty()) continue;
                if (stateGetPoseMethod.invoke(state, ref) != null) continue;

                int on = resolveValidSupport(e.slot, e.on, itemsSize, state, validSupport);
                Object quat = quat4fCtor.newInstance(e.qx, e.qy, e.qz, e.qw);
                Object rawPose = itemPoseCtor.newInstance(
                        e.x, e.y, e.z, e.lift, quat, e.scale, on);
                Object sanitized = itemPoseSanitizedMethod.invoke(rawPose);
                if (sanitized != null) {
                    stateSetPoseMethod.invoke(state, ref, sanitized);
                }
            }

            CompoundTag nextData = data.copy();
            nextData.remove(SCATTER_SEED_KEY);
            stateSetDataMethod.invoke(state, nextData);
            if (statePollStoragesMethod != null) {
                statePollStoragesMethod.invoke(state);
            }
            be.setChanged();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int resolveValidSupport(int slot, int candidateOn, int itemsSize, Object state,
            Map<Integer, Integer> entrySupport) throws Throwable {
        if (candidateOn < 0 || candidateOn == slot || candidateOn >= itemsSize) return -1;
        Object onRef = slotRefOfMethod.invoke(null, ITEMS_GROUP, candidateOn);
        Object onStack = stateGetItemMethod.invoke(state, onRef);
        if (!(onStack instanceof ItemStack s) || s.isEmpty()) return -1;

        int cur = candidateOn;
        for (int depth = 0; depth < 8 && cur >= 0; depth++) {
            if (cur == slot) return -1;
            Integer next = entrySupport.get(cur);
            if (next == null) break;
            cur = next.intValue();
        }
        return candidateOn;
    }

    private static int itemsGroupSize(Object state) {
        if (stateSchemaMethod == null || schemaSlotGroupsMethod == null || slotGroupSizeMethod == null) {
            return 0;
        }
        try {
            Object schema = stateSchemaMethod.invoke(state);
            if (schema == null) return 0;
            Object groupsObj = schemaSlotGroupsMethod.invoke(schema);
            if (!(groupsObj instanceof Map<?, ?> groups)) return 0;
            Object group = groups.get(ITEMS_GROUP);
            if (group == null) return 0;
            Object sizeObj = slotGroupSizeMethod.invoke(group);
            return sizeObj instanceof Integer sz ? sz.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void ensureDiegesis() {
        if (diegesisProbed) return;
        synchronized (ChestDiegesisCompat436.class) {
            if (diegesisProbed) return;
            try {
                Class<?> api = Class.forName("com.pockyl.diegesis.api.Diegesis");
                Class<?> ctx = Class.forName("com.pockyl.diegesis.api.StationContext");
                activeStationMethod = api.getMethod("activeStation", ServerLevel.class, BlockPos.class);
                stationPlayersMethod = ctx.getMethod("players");
            } catch (Throwable ignored) {
                activeStationMethod = null;
                stationPlayersMethod = null;
            }
            diegesisProbed = true;
        }
    }

    private static void ensureRoomy() {
        if (roomyProbed) return;
        synchronized (ChestDiegesisCompat436.class) {
            if (roomyProbed) return;
            try {
                Class<?> iface = Class.forName("com.pockyl.handson_furniture.station.RoomyContainer");
                Method own = iface.getMethod("handson$ownSize");
                roomyClass = iface;
                ownSizeMethod = own;
            } catch (Throwable ignored) {
                roomyClass = null;
                ownSizeMethod = null;
            }
            roomyProbed = true;
        }
    }

    private static void ensureStateApi() {
        if (stateApiProbed) return;
        synchronized (ChestDiegesisCompat436.class) {
            if (stateApiProbed) return;
            try {
                Class<?> hostCls = Class.forName("com.pockyl.diegesis.api.state.StationHost");
                Class<?> stateCls = Class.forName("com.pockyl.diegesis.api.state.StationState");
                Class<?> schemaCls = Class.forName("com.pockyl.diegesis.api.state.StationSchema");
                Class<?> groupCls = Class.forName("com.pockyl.diegesis.api.state.StationSchema$SlotGroup");
                Class<?> slotRefCls = Class.forName("com.pockyl.diegesis.api.state.SlotRef");
                Class<?> poseCls = Class.forName("com.pockyl.diegesis.api.state.ItemPose");
                Class<?> quatFcCls = Class.forName("org.joml.Quaternionfc");

                hostStateMethod = hostCls.getMethod("stationState");
                stateDataMethod = stateCls.getMethod("data");
                stateSetDataMethod = stateCls.getMethod("setData", CompoundTag.class);
                stateSchemaMethod = stateCls.getMethod("schema");
                schemaSlotGroupsMethod = schemaCls.getMethod("slotGroups");
                slotGroupSizeMethod = groupCls.getMethod("size");
                stateGetItemMethod = stateCls.getMethod("getItem", slotRefCls);
                stateGetPoseMethod = stateCls.getMethod("getPose", slotRefCls);
                stateSetPoseMethod = stateCls.getMethod("setPose", slotRefCls, poseCls);
                try {
                    statePollStoragesMethod = stateCls.getMethod("pollStorages");
                } catch (Throwable ignored) {
                    statePollStoragesMethod = null;
                }
                slotRefOfMethod = slotRefCls.getMethod("of", String.class, int.class);
                itemPoseCtor = poseCls.getConstructor(float.class, float.class, float.class, float.class,
                        quatFcCls, float.class, int.class);
                itemPoseSanitizedMethod = poseCls.getMethod("sanitized");
                quat4fCtor = Quaternionf.class.getConstructor(float.class, float.class, float.class, float.class);
                stationHostClass = hostCls;
            } catch (Throwable ignored) {
                stationHostClass = null;
                hostStateMethod = null;
                stateDataMethod = null;
                stateSetDataMethod = null;
                stateSchemaMethod = null;
                schemaSlotGroupsMethod = null;
                slotGroupSizeMethod = null;
                stateGetItemMethod = null;
                stateGetPoseMethod = null;
                stateSetPoseMethod = null;
                statePollStoragesMethod = null;
                slotRefOfMethod = null;
                itemPoseCtor = null;
                itemPoseSanitizedMethod = null;
                quat4fCtor = null;
            }
            stateApiProbed = true;
        }
    }
}
