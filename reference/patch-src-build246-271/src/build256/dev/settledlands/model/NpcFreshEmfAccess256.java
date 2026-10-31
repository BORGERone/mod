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
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.utils.ETFEntity;

/**
 * Optional EMF/ETF helper for GuideNpc Fresh Animations: Player Extension +
 * Touch'n Motion retargeting. Loaded reflectively only when EMF and ETF are
 * installed; never replaces or mutates FreshEmfAccess (build 250).
 */
public final class NpcFreshEmfAccess256 extends EMFAnimationApi.EMFAnimationHook {
    private static final NpcFreshEmfAccess256 OBSERVER = new NpcFreshEmfAccess256();
    private static final ThreadLocal<Deque<Run>> RUNS = new ThreadLocal<>();
    private static final String[] PARTS = {
        "body", "head", "right_arm", "left_arm", "right_leg", "left_leg"
    };
    private static final String[] CHANNELS = {"rx", "ry", "rz", "tx", "ty", "tz"};
    private static final AtomicLong PRIVATE_COUNTS = new AtomicLong(Long.MIN_VALUE / 2L);
    private static boolean installed;

    private NpcFreshEmfAccess256() {}

    public static synchronized boolean install(boolean requireTnmHook) throws Exception {
        if (!installed) {
            installed = EMFAnimationApi.registerAnimationHook(OBSERVER);
        }
        if (!installed) return false;
        return !requireTnmHook || hookCount("strm.touchnmotion.TouchNMotionHook") == 1;
    }

    public static boolean hasTnmHook() {
        return hookCount("strm.touchnmotion.TouchNMotionHook") == 1;
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

    public static Object mountProxyState(Object proxy, float swing, float amount, float headYaw, float headPitch) {
        if (!(proxy instanceof ETFEntity etfEntity)) return null;
        ETFEntityRenderState created = ETFEntityRenderState.forEntity(etfEntity);
        if (!(created instanceof EMFEntityRenderState emfState)) return null;
        if (ETFState.state() == created) return null;
        emfState.setIsFirstPersonHand(false);
        emfState.setLimbAngle(swing);
        emfState.setLimbDistance(amount);
        emfState.setHeadYaw(headYaw);
        emfState.setHeadPitch(headPitch);
        ETFState.mount(emfState);
        if (EMFState.state() != emfState) {
            if (ETFState.state() == created) ETFState.unMount();
            return null;
        }
        return emfState;
    }

    public static void unmountProxyState(Object token) {
        if (token instanceof ETFEntityRenderState state && ETFState.state() == state) {
            ETFState.unMount();
        }
    }

    public static boolean prepareAndAnimate(PlayerModel<?> model, PlayerModel<?> rendererModel,
                                            Object proxy, boolean requireTnmHook,
                                            float swing, float amount, float headYaw, float headPitch) {
        EMFEntityRenderState state = EMFState.state();
        EMFModelPartRoot src = root(rendererModel), dst = root(model);
        if (state == null || (Object) state.emfEntity() != proxy || state.skipModelVariate()
                || EMFState.isEntityForcedToVanillaModel(state) || src == null || dst == null
                || src == dst || src.modelName == null || !src.modelName.equals(dst.modelName)) {
            return false;
        }
        if (requireTnmHook && hookCount("strm.touchnmotion.TouchNMotionHook") != 1) {
            return false;
        }
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
        if (!dst.hasAnimation()) return false;

        Deque<Run> runs = RUNS.get();
        if (runs == null) {
            runs = new ArrayDeque<>();
            RUNS.set(runs);
        }
        Run run = new Run(dst, state, model);
        runs.push(run);
        float[] rootBefore = rootPose(dst);
        float oldLimbAngle = state.limbAngle(), oldLimbDistance = state.limbDistance();
        float oldHeadYaw = state.headYaw(), oldHeadPitch = state.headPitch();
        EMFManager manager = EMFManager.getInstance();
        long oldRenderCount = manager.entityRenderCount;
        boolean oldMain = dst.isMainModel;
        boolean oldMainPhase = EMFState.isMainPhase;
        boolean oldLayerPhase = EMFState.isLayerPhase;
        try {
            state.setLimbAngle(swing);
            state.setLimbDistance(amount);
            state.setHeadYaw(headYaw);
            state.setHeadPitch(headPitch);
            dst.isMainModel = true;
            EMFState.isMainPhase = true;
            EMFState.isLayerPhase = false;
            manager.entityRenderCount = PRIVATE_COUNTS.getAndIncrement();
            dst.animate();
            return run.started && run.ended && run.valid && unchangedRoot(rootBefore, dst);
        } finally {
            manager.entityRenderCount = oldRenderCount;
            state.setLimbAngle(oldLimbAngle);
            state.setLimbDistance(oldLimbDistance);
            state.setHeadYaw(oldHeadYaw);
            state.setHeadPitch(oldHeadPitch);
            dst.isMainModel = oldMain;
            EMFState.isMainPhase = oldMainPhase;
            EMFState.isLayerPhase = oldLayerPhase;
            if (runs.pop() != run) throw new IllegalStateException("NPC EMF animation scope mismatch");
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

    private static float[] rootPose(ModelPart root) {
        return new float[]{
            root.x, root.y, root.z,
            root.xRot, root.yRot, root.zRot,
            root.xScale, root.yScale, root.zScale
        };
    }

    private static boolean unchangedRoot(float[] before, ModelPart root) {
        float[] after = rootPose(root);
        for (int i = 0; i < before.length; i++) {
            if (!Float.isFinite(before[i]) || !Float.isFinite(after[i])
                    || Math.abs(before[i] - after[i]) > 1e-5f) {
                return false;
            }
        }
        return true;
    }

    private static boolean movementExpression(EMFAnimationHandler.AnimLineData line) {
        String expression = line == null ? null : line.expression;
        return expression != null && expression.contains("limb_speed")
            && expression.contains("var.run") && expression.contains("var.walk")
            && expression.contains("cos(");
    }

    private static boolean hasFreshRig(EMFAnimationHandler handler, PlayerModel<?> model) {
        if (handler == null) return false;
        EMFAnimationHandler.AnimLineData marker = handler.getLine("var.Created_by_FreshLX_for_Fresh_Animations");
        EMFAnimationHandler.AnimLineData rightMovement = handler.getLine("var.mvmnt_rlegrx");
        EMFAnimationHandler.AnimLineData leftMovement = handler.getLine("var.mvmnt_llegrx");
        if (marker == null || marker.expression == null || !"1".equals(marker.expression.trim())
                || !movementExpression(rightMovement) || !movementExpression(leftMovement)) {
            return false;
        }
        EMFAnimationHandler.AnimLineData rightLeg = handler.getLine("right_leg.rx");
        EMFAnimationHandler.AnimLineData leftLeg = handler.getLine("left_leg.rx");
        if (rightLeg == null || rightLeg.expression == null || !rightLeg.expression.contains("var.mvmnt_rlegrx")
                || leftLeg == null || leftLeg.expression == null || !leftLeg.expression.contains("var.mvmnt_llegrx")) {
            return false;
        }
        ModelPart[] parts = {
            model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg
        };
        for (int i = 0; i < PARTS.length; i++) {
            if (parts[i] == null) return false;
            for (String channel : CHANNELS) {
                EMFAnimationHandler.AnimLineData line = handler.getLine(PARTS[i] + "." + channel);
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
