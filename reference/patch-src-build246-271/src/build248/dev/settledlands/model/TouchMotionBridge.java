package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Experimental, optional bridge to the independently installed Touch'n Motion 1.0.4.
 * No code or classes from that mod are bundled here. Its private pose entry point is
 * version-checked because it is not a supported public integration API.
 */
public final class TouchMotionBridge {
    private static final String[] BB = {"body", "head", "rightarm", "leftarm", "rightleg", "leftleg"};
    private static final String[] HUMANOID = {"body", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final Consumer<Object> NO_MODEL_CALLBACK = ignored -> {};
    private static final ThreadLocal<Deque<Scope>> SCOPES = new ThreadLocal<>();
    // Quaternionf 1.10.5 quantizes near π; <0.007 model pixels is negligible,
    // while genuine shear still causes a fail-closed fallback.
    private static final float MATRIX_EPS = 0.0004f;
    private static boolean checked, disabled, warned;
    private static Method modelBefore, recordAnimated, applyAll, inWorld, activeState, stateEntity, firstPersonHand, parcool, held;

    private TouchMotionBridge() {}

    private static void warn(String message, Throwable error) {
        if (warned) return;
        warned = true;
        System.err.println("[SettledLands/TouchMotion] " + message);
        if (error != null) error.printStackTrace(System.err);
    }

    private static synchronized boolean available() {
        if (disabled) return false;
        if (checked) return modelBefore != null;
        checked = true;
        try {
            ModList mods = ModList.get();
            if (mods == null || !mods.isLoaded("touch_n_motion")) return false;
            var container = mods.getModContainerById("touch_n_motion").orElse(null);
            String version = container == null ? "" : String.valueOf(container.getModInfo().getVersion());
            if (!"1.0.4".equals(version)) {
                warn("Unsupported Touch'n Motion version " + version + "; Ivy compatibility is disabled.", null);
                return false;
            }
            ClassLoader loader = TouchMotionBridge.class.getClassLoader();
            Class<?> space = Class.forName("strm.touchnmotion.ModelSpace", false, loader);
            Class<?> hook = Class.forName("strm.touchnmotion.TouchNMotionHook", false, loader);
            Class<?> feet = Class.forName("strm.touchnmotion.footgrounding.compat.FootGrounding", false, loader);
            Class<?> drawn = Class.forName("strm.touchnmotion.interaction.DrawnEntities", false, loader);
            Class<?> state = Class.forName("traben.entity_model_features.models.animation.state.EMFState", false, loader);
            Class<?> stateType = Class.forName("traben.entity_model_features.models.animation.state.EMFEntityRenderState", false, loader);
            Class<?> pc = Class.forName("strm.touchnmotion.compat.ParCoolActivity", false, loader);
            Class<?> whole = Class.forName("strm.touchnmotion.compat.WholeBody", false, loader);
            Method before = space.getMethod("before", LivingEntity.class, PoseStack.class, float.class, Consumer.class);
            Method record = feet.getMethod("recordAnimated", UUID.class, Function.class);
            Method apply = hook.getDeclaredMethod("applyAll", UUID.class, Function.class, boolean.class, boolean.class);
            Method world = drawn.getMethod("inWorld", Entity.class);
            Method current = state.getMethod("state");
            Method entity = stateType.getMethod("emfEntity");
            Method hand = stateType.getMethod("isFirstPersonHand");
            Method activity = pc.getMethod("active", AbstractClientPlayer.class);
            Method heldBody = whole.getMethod("held", AbstractClientPlayer.class);
            if (!Modifier.isStatic(apply.getModifiers()) || !apply.trySetAccessible())
                throw new IllegalAccessException("Touch'n Motion pose entry point is not accessible");
            modelBefore = before;
            recordAnimated = record;
            applyAll = apply;
            inWorld = world;
            activeState = current;
            stateEntity = entity;
            firstPersonHand = hand;
            parcool = activity;
            held = heldBody;
            return true;
        } catch (Throwable error) {
            disabled = true;
            warn("Could not initialize optional 1.0.4 bridge; original rendering is retained.", unwrap(error));
            return false;
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
    }

    private static boolean isIvy(BbModel model) {
        if (model == null || model.getName() == null) return false;
        String name = model.getName().toLowerCase(Locale.ROOT);
        return name.equals("ivy") || name.equals("ivy_oleander");
    }

    /** Wrapper for the unmodified actor render; the added scope is always unwound. */
    public static void render(BbModel model, LivingEntity entity, float scale, float partial,
                              PoseStack stack, MultiBufferSource buffers, int light) {
        if (!(entity instanceof AbstractClientPlayer) || !isIvy(model) || !available()) {
            model.settledlands$renderActorOriginal(entity, scale, partial, stack, buffers, light);
            return;
        }
        Deque<Scope> scopes = SCOPES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            SCOPES.set(scopes);
        }
        Scope scope = new Scope(model, entity, stack);
        scopes.push(scope);
        try {
            model.settledlands$renderActorOriginal(entity, scale, partial, stack, buffers, light);
        } finally {
            // The original renderer pops once. When a TnM pose was pushed, that removes
            // our pose instead of its own; pop the remaining pose here. On an exception
            // before its pop this removes ours, preserving the original error behaviour.
            if (scope.pushed) stack.popPose();
            Scope removed = scopes.pop();
            if (removed != scope) throw new IllegalStateException("Nested render scope mismatch");
            if (scopes.isEmpty()) SCOPES.remove();
        }
    }

    /** Called immediately before the original Blockbench setupAnimRaw. */
    public static void before(BbModel model, LivingEntity entity, PoseStack stack, float partial,
                              IdentityHashMap<ModelPart, ModelPart> parents) {
        Scope scope = current(model, entity, stack);
        if (scope == null || disabled || !validRig(model, parents)) return;
        try {
            Object state = activeState.invoke(null);
            if (state == null || stateEntity.invoke(state) != entity) return;
            if (Boolean.TRUE.equals(firstPersonHand.invoke(state))) return;
            if (!Boolean.TRUE.equals(inWorld.invoke(null, entity))) return;
            if (Boolean.TRUE.equals(parcool.invoke(null, entity)) || Boolean.TRUE.equals(held.invoke(null, entity))) return;
            scope.parents = parents;
            stack.pushPose();
            scope.pushed = true;
            modelBefore.invoke(null, entity, stack, partial, NO_MODEL_CALLBACK);
            scope.prepared = true;
        } catch (Throwable error) {
            cancel(scope);
            disabled = true;
            warn("TnM pose preparation failed; original rendering is retained.", unwrap(error));
        }
    }

    /** Called immediately after the Blockbench pose, before the model and held items draw. */
    public static void after(BbModel model, LivingEntity entity, PoseStack stack,
                             IdentityHashMap<ModelPart, ModelPart> parents) {
        Scope scope = current(model, entity, stack);
        if (scope == null || !scope.prepared || scope.parents != parents) return;
        scope.prepared = false;
        try {
            Rig rig = Rig.capture(model, parents);
            if (rig == null) { cancel(scope); return; }
            Function<String, ModelPart> parts = rig.virtual::get;
            UUID uuid = entity.getUUID();
            recordAnimated.invoke(null, uuid, parts);
            applyAll.invoke(null, uuid, parts, true, true);
            if (!rig.commit(parents)) cancel(scope);
        } catch (Throwable error) {
            cancel(scope);
            disabled = true;
            warn("TnM additive pose failed; original rendering is retained.", unwrap(error));
        }
    }

    private static Scope current(BbModel model, LivingEntity entity, PoseStack stack) {
        Deque<Scope> scopes = SCOPES.get();
        Scope scope = scopes == null ? null : scopes.peek();
        return scope != null && scope.model == model && scope.entity == entity && scope.stack == stack ? scope : null;
    }

    private static void cancel(Scope scope) {
        if (scope.pushed) {
            scope.stack.popPose();
            scope.pushed = false;
        }
        scope.prepared = false;
    }

    private static boolean validRig(BbModel model, IdentityHashMap<ModelPart, ModelPart> parents) {
        if (parents == null || model.getRootPart() == null) return false;
        ModelPart body = model.getPart("body"), waist = model.getPart("waist");
        if (body == null || waist == null || !beneath(waist, body, parents)) return false;
        IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();
        for (String name : BB) {
            ModelPart part = model.getPart(name);
            if (part == null || seen.put(part, Boolean.TRUE) != null || !beneath(part, model.getRootPart(), parents)) return false;
            if (part != body && !beneath(part, waist, parents)) return false;
        }
        return true;
    }

    private static boolean beneath(ModelPart part, ModelPart ancestor, IdentityHashMap<ModelPart, ModelPart> parents) {
        for (int i = 0; part != null && i < 128; i++, part = parents.get(part)) if (part == ancestor) return true;
        return false;
    }

    private static final class Scope {
        final BbModel model;
        final LivingEntity entity;
        final PoseStack stack;
        IdentityHashMap<ModelPart, ModelPart> parents;
        boolean pushed, prepared;
        Scope(BbModel model, LivingEntity entity, PoseStack stack) {
            this.model = model; this.entity = entity; this.stack = stack;
        }
    }

    /** Flatten the real nested bones to model space; project the result back parent-first. */
    private static final class Rig {
        final Map<String, ModelPart> virtual = new LinkedHashMap<>();
        final IdentityHashMap<ModelPart, ModelPart> realToVirtual = new IdentityHashMap<>();
        final IdentityHashMap<ModelPart, Pose> originals = new IdentityHashMap<>();
        final List<ModelPart> order = new ArrayList<>();

        static Rig capture(BbModel model, IdentityHashMap<ModelPart, ModelPart> parents) {
            Rig rig = new Rig();
            for (int i = 0; i < BB.length; i++) {
                ModelPart real = model.getPart(BB[i]);
                Matrix4f matrix = world(real, parents, null);
                Pose pose = matrix == null ? null : Pose.from(matrix);
                if (pose == null) return null;
                ModelPart part = new ModelPart(List.of(), Map.of());
                pose.set(part);
                rig.virtual.put(HUMANOID[i], part);
                rig.realToVirtual.put(real, part);
                rig.originals.put(part, new Pose(part));
                rig.order.add(real);
            }
            rig.order.sort(Comparator.comparingInt(part -> depth(part, parents)));
            return rig;
        }

        boolean commit(IdentityHashMap<ModelPart, ModelPart> parents) {
            boolean changed = false;
            for (ModelPart part : virtual.values()) if (!originals.get(part).same(part)) { changed = true; break; }
            if (!changed) return true;
            IdentityHashMap<ModelPart, Pose> updates = new IdentityHashMap<>();
            for (ModelPart real : order) {
                ModelPart part = realToVirtual.get(real);
                Matrix4f desired = new Pose(part).matrix();
                Matrix4f parent = world(parents.get(real), parents, updates);
                if (parent == null || !parent.isFinite() || Math.abs(parent.determinant()) < 1e-8f) return false;
                Matrix4f local = new Matrix4f(parent).invert().mul(desired);
                Pose result = Pose.from(local);
                if (result == null) return false;
                updates.put(real, result);
            }
            // Commit only after every bone has a finite representable local transform.
            for (ModelPart real : order) updates.get(real).set(real);
            return true;
        }
    }

    private static int depth(ModelPart part, IdentityHashMap<ModelPart, ModelPart> parents) {
        int count = 0;
        while (part != null && count < 128) { count++; part = parents.get(part); }
        return count;
    }

    private static Matrix4f world(ModelPart part, IdentityHashMap<ModelPart, ModelPart> parents,
                                  IdentityHashMap<ModelPart, Pose> overrides) {
        ArrayDeque<ModelPart> path = new ArrayDeque<>();
        while (part != null && path.size() < 128) { path.addFirst(part); part = parents.get(part); }
        if (part != null) return null;
        Matrix4f out = new Matrix4f();
        for (ModelPart node : path) {
            Pose pose = overrides == null ? null : overrides.get(node);
            out.mul((pose == null ? new Pose(node) : pose).matrix());
        }
        return out;
    }

    private static final class Pose {
        final float x, y, z, rx, ry, rz, sx, sy, sz;
        Pose(ModelPart p) { this(p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale); }
        Pose(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
            this.x=x; this.y=y; this.z=z; this.rx=rx; this.ry=ry; this.rz=rz; this.sx=sx; this.sy=sy; this.sz=sz;
        }
        Matrix4f matrix() {
            // Match Minecraft 1.21.1 ModelPart.translateAndRotate exactly:
            // PoseStack.mulPose(new Quaternionf().rotationZYX(...)), not the
            // numerically different Matrix4f.rotateZYX near right angles.
            return new Matrix4f().translation(x / 16f, y / 16f, z / 16f)
                .rotate(new Quaternionf().rotationZYX(rz, ry, rx)).scale(sx, sy, sz);
        }
        static Pose from(Matrix4f matrix) {
            if (matrix == null || !matrix.isFinite() || Math.abs(matrix.determinant()) < 1e-8f) return null;
            Vector3f t = matrix.getTranslation(new Vector3f());
            Vector3f scale = matrix.getScale(new Vector3f());
            Quaternionf q = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
            // Recover ZYX from a normalized matrix, including the gimbal-lock
            // branch. asin/sqrt(1-y²) lose precision for poses near ±90°.
            Matrix4f rotation = new Matrix4f().rotation(q);
            float cosine = (float)Math.hypot(rotation.m00(), rotation.m01());
            float angleY = (float)Math.atan2(-rotation.m02(), cosine);
            float angleX, angleZ;
            if (cosine > 1e-6f) {
                angleX = (float)Math.atan2(rotation.m12(), rotation.m22());
                angleZ = (float)Math.atan2(rotation.m01(), rotation.m00());
            } else {
                angleX = (float)Math.atan2(-rotation.m21(), rotation.m11());
                angleZ = 0f;
            }
            if (!t.isFinite() || !scale.isFinite() || !q.isFinite()
                    || !Float.isFinite(angleX) || !Float.isFinite(angleY) || !Float.isFinite(angleZ)) return null;
            Pose pose = new Pose(t.x*16f, t.y*16f, t.z*16f, angleX, angleY, angleZ, scale.x, scale.y, scale.z);
            return matrix.equals(pose.matrix(), MATRIX_EPS) ? pose : null;
        }
        void set(ModelPart p) {
            p.x=x; p.y=y; p.z=z; p.xRot=rx; p.yRot=ry; p.zRot=rz; p.xScale=sx; p.yScale=sy; p.zScale=sz;
        }
        boolean same(ModelPart p) {
            return Float.floatToIntBits(x)==Float.floatToIntBits(p.x)
                && Float.floatToIntBits(y)==Float.floatToIntBits(p.y)
                && Float.floatToIntBits(z)==Float.floatToIntBits(p.z)
                && Float.floatToIntBits(rx)==Float.floatToIntBits(p.xRot)
                && Float.floatToIntBits(ry)==Float.floatToIntBits(p.yRot)
                && Float.floatToIntBits(rz)==Float.floatToIntBits(p.zRot)
                && Float.floatToIntBits(sx)==Float.floatToIntBits(p.xScale)
                && Float.floatToIntBits(sy)==Float.floatToIntBits(p.yScale)
                && Float.floatToIntBits(sz)==Float.floatToIntBits(p.zScale);
        }
    }
}
