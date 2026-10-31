package dev.settledlands.model;

import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Isolated PAL + EMF 3.3.9 adapter for HnS's FirstPersonMode.THIRD_PERSON_MODEL pass.
 *
 * Loaded only after FirstPersonSwordBridge252 verifies exact mod versions and
 * confirms that emf_compat_hackers_and_slashers is not installed.
 *
 * During PAL's first-person pass on the local camera player, PAL computes the
 * HnS weapon pose and FirstPersonConfiguration visibility in PlayerModel.setupAnim,
 * before EMFModelPartRoot.animate() runs Fresh Animations (player.jem),
 * EMFCompatAnimationHook, and TouchNMotionHook. PRE_HOOK captures the exact
 * post-PAL model state at the head of EMFState.animationHooks (before crouch
 * repair or JEM expressions), and POST_HOOK restores it at the tail of
 * EMFState.animationHooks (after FA and TouchNMotionHook), so both rendered
 * first-person arms and PlayerItemInHandLayer / BladeTracker see PAL's pose.
 */
public final class FirstPersonPalEmfAccess252 {
    private static final ResourceLocation[] HNS_FIRST_PERSON_LAYERS = {
        ResourceLocation.fromNamespaceAndPath("hackersandslashers", "attack_layer"),
        ResourceLocation.fromNamespaceAndPath("hackersandslashers", "action_layer"),
        ResourceLocation.fromNamespaceAndPath("hackersandslashers", "defense_layer"),
        ResourceLocation.fromNamespaceAndPath("hackersandslashers", "pose_action_layer"),
        ResourceLocation.fromNamespaceAndPath("hackersandslashers", "first_person_pose_layer"),
    };

    private static final ThreadLocal<Deque<Snapshot>> SNAPSHOTS = new ThreadLocal<>();
    private static boolean disabled, warned, reported;

    private static final EMFAnimationApi.EMFAnimationHook PRE_HOOK = new EMFAnimationApi.EMFAnimationHook() {
        @Override
        public boolean onAnimationStart(AnimationContext context, boolean cancelled) {
            if (disabled) return true;
            try {
                Target target = resolveFirstPersonTarget(context);
                if (target != null) {
                    Snapshot snapshot = Snapshot.capture(target.root, target.model);
                    if (snapshot != null) {
                        Deque<Snapshot> stack = SNAPSHOTS.get();
                        if (stack == null) {
                            stack = new ArrayDeque<>();
                            SNAPSHOTS.set(stack);
                        } else {
                            stack.removeIf(existing -> existing.root == target.root);
                        }
                        stack.push(snapshot);
                    }
                }
            } catch (Throwable error) {
                SNAPSHOTS.remove();
                disable(error);
            }
            return true;
        }
    };

    private static final EMFAnimationApi.EMFAnimationHook POST_HOOK = new EMFAnimationApi.EMFAnimationHook() {
        @Override
        public void onAnimationEnd(AnimationContext context, boolean cancelled) {
            Deque<Snapshot> stack = SNAPSHOTS.get();
            if (stack == null || stack.isEmpty()) return;
            EMFModelPartRoot root = context == null ? null : context.animatingModelRoot();
            Snapshot top = stack.peek();
            if (top == null || top.root != root) return;
            stack.pop();
            if (stack.isEmpty()) SNAPSHOTS.remove();
            if (disabled) return;
            try {
                top.restore();
                if (!reported) {
                    reported = true;
                    System.out.println("[SettledLands/Motion252] First-person HnS/PAL pose preserved after EMF/FA/TnM.");
                }
            } catch (Throwable error) {
                disable(error);
            }
        }
    };

    private FirstPersonPalEmfAccess252() {}

    public static boolean ensureInstalled() {
        if (disabled) return false;
        List<EMFAnimationApi.EMFAnimationHook> hooks = EMFState.animationHooks;
        if (hooks == null) return false;
        if (hooks.isEmpty() || hooks.get(0) != PRE_HOOK) {
            hooks.remove(PRE_HOOK);
            hooks.add(0, PRE_HOOK);
        }
        if (hooks.get(hooks.size() - 1) != POST_HOOK) {
            hooks.remove(POST_HOOK);
            hooks.add(POST_HOOK);
        }
        return hooks.size() >= 2
                && hooks.get(0) == PRE_HOOK
                && hooks.get(hooks.size() - 1) == POST_HOOK;
    }

    private static Target resolveFirstPersonTarget(EMFAnimationApi.EMFAnimationHook.AnimationContext context) {
        if (context == null || !FirstPersonMode.isFirstPersonPass()) return null;
        EMFEntityRenderState state = context.activeState();
        EMFModelPartRoot root = context.animatingModelRoot();
        if (state == null || root == null || !root.isMainModel || state.isFirstPersonHand()) return null;
        Object raw = state.emfEntity();
        if (!(raw instanceof AbstractClientPlayer player)) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player != player || mc.options == null) return null;
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity != null && cameraEntity != player) return null;
        CameraType cameraType = mc.options.getCameraType();
        if (cameraType == null || !cameraType.isFirstPerson()) return null;
        var dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null) return null;
        var renderer = dispatcher.getRenderer(player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) return null;
        PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
        if (!(model instanceof IEMFModel emf) || !emf.emf$isEMFModel() || emf.emf$getEMFRootModel() != root) {
            return null;
        }
        if (!hasActiveHnsFirstPersonLayer(player)) return null;
        return new Target(root, model);
    }

    private static boolean hasActiveHnsFirstPersonLayer(AbstractClientPlayer player) {
        if (!(player instanceof IAnimatedPlayer animated)) return false;
        PlayerAnimManager manager = animated.playerAnimLib$getAnimManager();
        if (manager == null || !manager.isActive()) return false;
        if (manager.getFirstPersonMode() != FirstPersonMode.THIRD_PERSON_MODEL) return false;
        for (ResourceLocation layerId : HNS_FIRST_PERSON_LAYERS) {
            IAnimation layer = animated.playerAnimLib$getAnimation(layerId);
            if (layer != null && layer.isActive()) return true;
        }
        return false;
    }

    private static void disable(Throwable error) {
        disabled = true;
        SNAPSHOTS.remove();
        if (!warned) {
            warned = true;
            System.err.println("[SettledLands/Motion252] First-person PAL/EMF hook failed and is disabled.");
            if (error != null) error.printStackTrace(System.err);
        }
    }

    private record Target(EMFModelPartRoot root, PlayerModel<AbstractClientPlayer> model) {}

    private static final class PartState {
        final ModelPart part;
        final float x, y, z, xRot, yRot, zRot, xScale, yScale, zScale;
        final boolean visible, skipDraw, emfAnimated;

        private PartState(ModelPart part, float x, float y, float z,
                          float xRot, float yRot, float zRot,
                          float xScale, float yScale, float zScale,
                          boolean visible, boolean skipDraw, boolean emfAnimated) {
            this.part = part;
            this.x = x; this.y = y; this.z = z;
            this.xRot = xRot; this.yRot = yRot; this.zRot = zRot;
            this.xScale = xScale; this.yScale = yScale; this.zScale = zScale;
            this.visible = visible;
            this.skipDraw = skipDraw;
            this.emfAnimated = emfAnimated;
        }

        static PartState of(ModelPart part) {
            if (!Float.isFinite(part.x) || !Float.isFinite(part.y) || !Float.isFinite(part.z)
                    || !Float.isFinite(part.xRot) || !Float.isFinite(part.yRot) || !Float.isFinite(part.zRot)
                    || !Float.isFinite(part.xScale) || !Float.isFinite(part.yScale) || !Float.isFinite(part.zScale)) {
                return null;
            }
            boolean animated = part instanceof EMFModelPart emf && emf.isSetByAnimation;
            return new PartState(part, part.x, part.y, part.z,
                    part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale,
                    part.visible, part.skipDraw, animated);
        }

        void apply() {
            part.x = x; part.y = y; part.z = z;
            part.xRot = xRot; part.yRot = yRot; part.zRot = zRot;
            part.xScale = xScale; part.yScale = yScale; part.zScale = zScale;
            part.visible = visible;
            part.skipDraw = skipDraw;
            if (part instanceof EMFModelPart emf) {
                emf.isSetByAnimation = emfAnimated;
            }
        }
    }

    private static final class Snapshot {
        final EMFModelPartRoot root;
        final List<PartState> states;
        final List<ModelPart> customParts;

        private Snapshot(EMFModelPartRoot root, List<PartState> states, List<ModelPart> customParts) {
            this.root = root;
            this.states = states;
            this.customParts = customParts;
        }

        static Snapshot capture(EMFModelPartRoot root, PlayerModel<?> model) {
            IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();
            List<PartState> captured = new ArrayList<>(16);
            List<ModelPart> custom = new ArrayList<>(16);
            ModelPart[] explicit = {
                root,
                model.head, model.hat,
                model.body, model.jacket,
                model.rightArm, model.rightSleeve,
                model.leftArm, model.leftSleeve,
                model.rightLeg, model.rightPants,
                model.leftLeg, model.leftPants
            };
            for (ModelPart part : explicit) {
                if (part != null && seen.put(part, Boolean.TRUE) == null) {
                    PartState state = PartState.of(part);
                    if (state == null) return null;
                    captured.add(state);
                }
            }
            var stream = root.getAllParts();
            if (stream != null) {
                var it = stream.iterator();
                while (it.hasNext()) {
                    ModelPart part = it.next();
                    if (part != null && seen.put(part, Boolean.TRUE) == null) {
                        custom.add(part);
                    }
                }
            }
            return new Snapshot(root, captured, custom);
        }

        void restore() {
            for (PartState state : states) {
                state.apply();
            }
            for (ModelPart custom : customParts) {
                custom.resetPose();
                if (custom instanceof EMFModelPart emf) {
                    emf.isSetByAnimation = false;
                }
            }
        }
    }
}
