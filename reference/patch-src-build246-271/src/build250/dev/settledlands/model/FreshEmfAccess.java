package dev.settledlands.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * Optional, version-checked EMF integration. Loaded only when the external EMF
 * and TnM mods are present. No external assets or implementation are bundled.
 *
 * EMF 3.3.9's public animateModelForState mounts and unmounts its input. ETF
 * 7.2.4 does not push if that exact state is already mounted, yet unMount still
 * pops it. Player Pre is inside that state. Running the isolated root in the
 * already-active state avoids corrupting ETF's caller-owned stack. The internal
 * entry point and counter are intentionally limited to the verified EMF version.
 */
public final class FreshEmfAccess extends EMFAnimationApi.EMFAnimationHook {
    private static final FreshEmfAccess OBSERVER = new FreshEmfAccess();
    private static final ThreadLocal<Deque<Run>> RUNS = new ThreadLocal<>();
    private static final String[] PARTS = {
        "body", "head", "right_arm", "left_arm", "right_leg", "left_leg"
    };
    private static final String[] CHANNELS = {"rx", "ry", "rz", "tx", "ty", "tz"};
    private static boolean installed;
    private static final AtomicLong PRIVATE_COUNTS = new AtomicLong(Long.MIN_VALUE);

    private FreshEmfAccess() {}

    public static synchronized boolean install() throws Exception {
        if (!installed) {
            installed = EMFAnimationApi.registerAnimationHook(OBSERVER);
        }
        return installed && hookCount("strm.touchnmotion.TouchNMotionHook") == 1;
    }

    private static int hookCount(String name) {
        int count = 0;
        for (EMFAnimationApi.EMFAnimationHook hook : EMFState.animationHooks) {
            if (hook != null && hook.getClass().getName().equals(name)) count++;
        }
        return count;
    }

    private static EMFModelPartRoot root(PlayerModel<?> model) {
        if (!(model instanceof IEMFModel emf) || !emf.emf$isEMFModel()) return null;
        return emf.emf$getEMFRootModel();
    }

    /** Select the current player's variant without touching the shared renderer. */
    public static boolean prepare(PlayerModel<?> model, PlayerModel<?> rendererModel, Object player) {
        EMFEntityRenderState state = EMFState.state();
        EMFModelPartRoot src = root(rendererModel), dst = root(model);
        if (state == null || (Object) state.emfEntity() != player || state.skipModelVariate()
                || EMFState.isEntityForcedToVanillaModel(state) || src == null || dst == null
                || src == dst || src.modelName == null
                || !src.modelName.equals(dst.modelName)) return false;
        // Mirror 3.3.9's variant logic using only the isolated root's own cache.
        // doVariantCheck also writes EMFManager.lastModelSuffixOfEntity, which
        // belongs to the caller and can evict or alter another renderer's data.
        if (dst.variantTester == null) {
            dst.setVariantStateTo(1);
        } else {
            UUID id = state.uuid();
            if (id == null) return false;
            Integer cached = dst.entitySuffixMap.get(id);
            int suffix = cached == null ? -1 : cached;
            if (suffix != -1) {
                dst.checkIfShouldExpireEntity(state, id);
            } else {
                suffix = Math.max(1, dst.variantTester.getSuffixForETFEntity(state));
                dst.entitySuffixMap.put(id, suffix);
            }
            dst.setVariantStateTo(suffix);
        }
        return dst.hasAnimation();
    }

    /** Run the pack and its hooks once, then require evidence of the expected rig. */
    public static boolean animate(PlayerModel<?> model, Object player) {
        EMFEntityRenderState state = EMFState.state();
        EMFModelPartRoot root = root(model);
        if (state == null || (Object) state.emfEntity() != player || root == null
                || !root.hasAnimation() || hookCount("strm.touchnmotion.TouchNMotionHook") != 1) return false;
        Deque<Run> runs = RUNS.get();
        if (runs == null) {
            runs = new ArrayDeque<>();
            RUNS.set(runs);
        }
        Run run = new Run(root, state, model);
        runs.push(run);
        float[] rootBefore = rootPose(root);
        float oldLimbAngle = state.limbAngle(), oldLimbDistance = state.limbDistance();
        float oldHeadYaw = state.headYaw(), oldHeadPitch = state.headPitch();
        EMFManager manager = EMFManager.getInstance();
        long oldRenderCount = manager.entityRenderCount;
        boolean oldMain = root.isMainModel, oldMainPhase = EMFState.isMainPhase, oldLayer = EMFState.isLayerPhase;
        try {
            root.isMainModel = true;
            EMFState.isMainPhase = true;
            EMFState.isLayerPhase = false;
            // Give this private root a distinct render token without changing
            // the caller's counter or suppressing repeated / nested renders.
            manager.entityRenderCount = PRIVATE_COUNTS.getAndIncrement();
            root.animate(); // respects EMF pause conditions and invokes TnM's hook
            return run.started && run.ended && run.valid && unchangedRoot(rootBefore, root);
        } finally {
            manager.entityRenderCount = oldRenderCount;
            state.setLimbAngle(oldLimbAngle);
            state.setLimbDistance(oldLimbDistance);
            state.setHeadYaw(oldHeadYaw);
            state.setHeadPitch(oldHeadPitch);
            root.isMainModel = oldMain;
            EMFState.isMainPhase = oldMainPhase;
            EMFState.isLayerPhase = oldLayer;
            if (runs.pop() != run) throw new IllegalStateException("EMF animation scope mismatch");
            if (runs.isEmpty()) RUNS.remove();
        }
    }

    @Override
    public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
        Run run = current(context);
        if (run != null) {
            run.started = true;
            run.valid = !isCancelledByHook && hasFreshRig(context.animationHandler(), run.model);
        }
        // Observation must never cancel another mod's animations.
        return true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        Run run = current(context);
        if (run != null) {
            run.ended = true;
            run.valid &= !wasCancelledByHook && context.error() == null;
        }
    }

    private static Run current(AnimationContext context) {
        Deque<Run> runs = RUNS.get();
        Run run = runs == null ? null : runs.peek();
        return run != null && context.animatingModelRoot() == run.root
            && context.activeState() == run.state ? run : null;
    }

    // The retargeter currently transfers six biped parts, not the root. A pack
    // which animates the root is rejected rather than silently losing motion.
    private static float[] rootPose(ModelPart root) {
        return new float[]{root.x, root.y, root.z, root.xRot, root.yRot, root.zRot,
                           root.xScale, root.yScale, root.zScale};
    }

    private static boolean unchangedRoot(float[] before, ModelPart root) {
        float[] after = rootPose(root);
        for (int i = 0; i < before.length; i++) {
            if (!Float.isFinite(before[i]) || !Float.isFinite(after[i])
                    || Math.abs(before[i] - after[i]) > 1e-5f) return false;
        }
        return true;
    }

    // A marker alone can come from a compatibility layer. Require gait-shaped
    // expressions and that the resolved leg channels actually consume them. This
    // is a conservative compatibility signature, not proof of pack provenance.
    private static boolean movementExpression(EMFAnimationHandler.AnimLineData line) {
        String expression = line == null ? null : line.expression;
        return expression != null && expression.contains("limb_speed") && expression.contains("var.run")
                && expression.contains("var.walk") && expression.contains("cos(");
    }

    private static boolean hasFreshRig(EMFAnimationHandler handler, PlayerModel<?> model) {
        if (handler == null) return false;
        EMFAnimationHandler.AnimLineData marker = handler.getLine("var.Created_by_FreshLX_for_Fresh_Animations");
        EMFAnimationHandler.AnimLineData rightMovement = handler.getLine("var.mvmnt_rlegrx");
        EMFAnimationHandler.AnimLineData leftMovement = handler.getLine("var.mvmnt_llegrx");
        if (marker == null || marker.expression == null || !"1".equals(marker.expression.trim())
                || !movementExpression(rightMovement) || !movementExpression(leftMovement)) return false;
        EMFAnimationHandler.AnimLineData rightLeg = handler.getLine("right_leg.rx");
        EMFAnimationHandler.AnimLineData leftLeg = handler.getLine("left_leg.rx");
        if (rightLeg == null || rightLeg.expression == null || !rightLeg.expression.contains("var.mvmnt_rlegrx")
                || leftLeg == null || leftLeg.expression == null || !leftLeg.expression.contains("var.mvmnt_llegrx")) return false;
        ModelPart[] parts = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < PARTS.length; i++) {
            if (parts[i] == null) return false;
            for (String channel : CHANNELS) {
                EMFAnimationHandler.AnimLineData line = handler.getLine(PARTS[i] + "." + channel);
                // A pack can animate a separate custom child instead of the flat
                // vanilla limb. TnM and this retargeter would not see that pose.
                if (line == null || line.partToApplyTo != parts[i]) return false;
            }
        }
        return true;
    }

    private static final class Run {
        final EMFModelPartRoot root;
        final EMFEntityRenderState state;
        final PlayerModel<?> model;
        boolean started, ended, valid;
        Run(EMFModelPartRoot root, EMFEntityRenderState state, PlayerModel<?> model) {
            this.root = root;
            this.state = state;
            this.model = model;
        }
    }
}
