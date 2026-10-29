package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Experimental, optional Fresh Animations: Player Extension + Touch'n Motion
 * bridge. Both are independently installed; neither mod nor pack is bundled.
 * EMF's observed 3.3.9 internals are version-checked and loaded separately.
 */
public final class PlayerMotionBridge {
    private static final String[] BB = {"body", "head", "rightarm", "leftarm", "rightleg", "leftleg"};
    private static final String[] HUMANOID = {"body", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final Consumer<Object> NO_MODEL_CALLBACK = ignored -> {};
    private static final ThreadLocal<Deque<Scope>> SCOPES = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Biped>> FREE = ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean reportedActive;
    // Quaternionf 1.10.5 quantizes near π; <0.007 model pixels is negligible,
    // while genuine shear still causes a fail-closed fallback.
    private static final float MATRIX_EPS = 0.0004f;
    private static boolean checked, disabled, warned;
    private static Method modelBefore, freshPrepare, freshAnimate, inWorld, activeState, stateEntity, firstPersonHand, parcool, held;
    private static boolean combatChecked, combatDisabled, combatWarned, combatReported;
    private static Method combatPrepare, combatRoot, combatParts, combatItemPosition, combatItemPose;

    private PlayerMotionBridge() {}

    private static void warn(String message, Throwable error) {
        if (warned) return;
        warned = true;
        System.err.println("[SettledLands/FreshMotion251] " + message);
        if (error != null) error.printStackTrace(System.err);
    }

    private static synchronized boolean available() {
        if (disabled) return false;
        if (checked) return modelBefore != null;
        checked = true;
        try {
            ModList mods = ModList.get();
            if (mods == null || !mods.isLoaded("touch_n_motion")) return false;
            for (String[] required : new String[][] {
                    {"touch_n_motion", "1.0.4"}, {"entity_model_features", "3.3.9"},
                    {"entity_texture_features", "7.2.4"}, {"emf_compat_core", "2.3.2"}}) {
                var container = mods.getModContainerById(required[0]).orElse(null);
                String version = container == null ? "" : String.valueOf(container.getModInfo().getVersion());
                if (!required[1].equals(version)) {
                    warn("Unsupported " + required[0] + " version " + version + "; Ivy integration is disabled.", null);
                    return false;
                }
            }
            ClassLoader loader = PlayerMotionBridge.class.getClassLoader();
            Class<?> space = Class.forName("strm.touchnmotion.ModelSpace", false, loader);
            Class<?> drawn = Class.forName("strm.touchnmotion.interaction.DrawnEntities", false, loader);
            Class<?> state = Class.forName("traben.entity_model_features.models.animation.state.EMFState", false, loader);
            Class<?> stateType = Class.forName("traben.entity_model_features.models.animation.state.EMFEntityRenderState", false, loader);
            Class<?> pc = Class.forName("strm.touchnmotion.compat.ParCoolActivity", false, loader);
            Class<?> whole = Class.forName("strm.touchnmotion.compat.WholeBody", false, loader);
            Class<?> fresh = Class.forName("dev.settledlands.model.FreshEmfAccess", true, loader);
            Method before = space.getMethod("before", LivingEntity.class, PoseStack.class, float.class, Consumer.class);
            Method world = drawn.getMethod("inWorld", Entity.class);
            Method current = state.getMethod("state");
            Method entity = stateType.getMethod("emfEntity");
            Method hand = stateType.getMethod("isFirstPersonHand");
            Method activity = pc.getMethod("active", AbstractClientPlayer.class);
            Method heldBody = whole.getMethod("held", AbstractClientPlayer.class);
            Method prepare = fresh.getMethod("prepare", PlayerModel.class, PlayerModel.class, Object.class);
            Method animate = fresh.getMethod("animate", PlayerModel.class, Object.class);
            if (!Boolean.TRUE.equals(fresh.getMethod("install").invoke(null))) {
                warn("Touch'n Motion animation hook unavailable; original rendering is retained.", null);
                return false;
            }
            modelBefore = before;
            freshPrepare = prepare;
            freshAnimate = animate;
            inWorld = world;
            activeState = current;
            stateEntity = entity;
            firstPersonHand = hand;
            parcool = activity;
            held = heldBody;
            return true;
        } catch (Throwable error) {
            disabled = true;
            warn("Could not initialize optional Fresh Animations/TnM bridge; original rendering is retained.", unwrap(error));
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
            if (scope.combatRootPushed) stack.popPose();
            if (scope.swimRootPushed) stack.popPose();
            Scope removed = scopes.pop();
            if (removed != scope) throw new IllegalStateException("Nested render scope mismatch");
            if (scopes.isEmpty()) SCOPES.remove();
        }
    }

    /** Prepare the optional integration immediately before the actor setup call. */
    public static void before(BbModel model, LivingEntity entity, PoseStack stack, float partial,
                              IdentityHashMap<ModelPart, ModelPart> parents) {
        Scope scope = current(model, entity, stack);
        if (scope == null) return;
        try {
            if (!eligible(scope, parents)) { rollbackRoot(scope); return; }
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

    /** Replaces ONLY renderActor's setup call, leaving NPC setup untouched.
     * On an eligible player, do not execute any embedded Ivy clip at all. */
    public static void setup(BbModel model, boolean talking, boolean alive, String state1, String state2,
                             boolean flag, int animation, int tick, float limbSwing, float limbAmount,
                             float age, float headYaw, float headPitch, float partial) {
        Deque<Scope> scopes = SCOPES.get();
        Scope scope = scopes == null ? null : scopes.peek();
        boolean applied = false;
        boolean freshPath = scope != null && scope.model == model && scope.prepared;
        if (freshPath) {
            scope.prepared = false;
            Biped biped = null;
            try {
                AbstractClientPlayer player = (AbstractClientPlayer)scope.entity;
                var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
                if (!(renderer instanceof PlayerRenderer playerRenderer)) throw new IllegalStateException("Player renderer unavailable");
                PlayerModel<AbstractClientPlayer> current = playerRenderer.getModel();
                boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
                Deque<Biped> free = FREE.get();
                for (Iterator<Biped> it = free.iterator(); it.hasNext();) {
                    Biped candidate = it.next();
                    if (candidate.anchor == current && candidate.slim == slim) {
                        biped = candidate;
                        it.remove();
                        break;
                    }
                }
                if (biped == null) biped = new Biped(current, slim);
                PlayerModel<AbstractClientPlayer> vanilla = biped.model;
                if (Boolean.TRUE.equals(freshPrepare.invoke(null, vanilla, current, player))) {
                    biped.root.getAllParts().forEach(ModelPart::resetPose);
                    vanilla.crouching = current.crouching;
                    vanilla.rightArmPose = current.rightArmPose;
                    vanilla.leftArmPose = current.leftArmPose;
                    vanilla.attackTime = player.getAttackAnim(partial);
                    Entity vehicle = player.getVehicle();
                    boolean shouldSit = player.isPassenger() && vehicle != null && vehicle.shouldRiderSit();
                    vanilla.riding = shouldSit;
                    vanilla.young = player.isBaby();
                    float swing = 0f, amount = 0f;
                    if (!shouldSit && player.isAlive()) {
                        swing = limbSwing * (vanilla.young ? 3f : 1f);
                        amount = Math.min(limbAmount, 1f);
                    }
                    // Vanilla inputs are preparation only. EMF applies the active
                    // Fresh Animations pack and TnM adds its pose through its own hook.
                    vanilla.prepareMobModel(player, swing, amount, partial);
                    vanilla.setupAnim(player, swing, amount, age, Mth.wrapDegrees(headYaw), headPitch);
                    if (Boolean.TRUE.equals(freshAnimate.invoke(null, vanilla, player))) {
                        if (scope.combat != null) {
                            try {
                                combatParts.invoke(null, scope.combat, vanilla);
                            } catch (Throwable combatError) {
                                disableCombat(combatError);
                                cancel(scope); // this frame returns to the unmodified 250 fallback
                                model.setupAnimRaw(talking, alive, state1, state2, flag, animation, tick,
                                                   limbSwing, limbAmount, age, headYaw, headPitch, partial);
                                return;
                            }
                        }
                        // The model is reset before capturing its rest rig. NPC never
                        // enters this player-only path; its clips remain unchanged.
                        model.getRootPart().getAllParts().forEach(ModelPart::resetPose);
                        Rig rig = Rig.capture(model, scope.parents, biped);
                        if (rig != null) applied = rig.commit(scope.parents);
                        scope.combatReady = applied && scope.combat != null;
                        if (scope.combatReady && !combatReported) {
                            combatReported = true;
                            System.out.println("[SettledLands/Motion251] Active HnS attack layer transferred after FA/TnM; visual outcome is not verified.");
                        }
                        if (!applied) warn("Fresh Animations pose could not be safely retargeted; player is shown in a neutral pose to avoid conflicting clips.", null);
                    } else {
                        warn("Fresh Animations did not run, was paused/cancelled, or has an unsupported rig; player is shown in a neutral pose to avoid conflicting clips.", null);
                    }
                } else {
                    warn("Fresh Animations Player Extension is unavailable or disabled for this player; player is shown in a neutral pose to avoid conflicting clips.", null);
                }
                if (applied && !reportedActive) {
                    reportedActive = true;
                    System.out.println("[SettledLands/FreshMotion251] Fresh Animations pose + Touch'n Motion hook observed; embedded actor clips skipped. model="
                        + model.getName() + " texture=" + model.getTextureLocation()
                        + " uv=" + model.getTextureWidth() + "x" + model.getTextureHeight());
                }
            } catch (Throwable error) {
                disabled = true;
                warn("Fresh Animations/TnM retarget failed; original player rendering is retained.", unwrap(error));
            } finally {
                if (biped != null) {
                    Deque<Biped> free = FREE.get();
                    free.addFirst(biped);
                    while (free.size() > 4) free.removeLast();
                }
            }
        }
        if (!applied) {
            if (scope != null && scope.model == model) cancel(scope);
            if (freshPath && !disabled) {
                // Never bring conflicting embedded clips back when the requested
                // FA/TnM path is installed but unavailable or rejected.  A neutral
                // rest pose is explicit and cannot masquerade as FA movement.
                model.getRootPart().getAllParts().forEach(ModelPart::resetPose);
            } else {
                model.setupAnimRaw(talking, alive, state1, state2, flag, animation, tick,
                                   limbSwing, limbAmount, age, headYaw, headPitch, partial);
            }
        }
    }

    /** Exactly the same eligibility restrictions as the FA/TnM path. */
    private static boolean eligible(Scope scope, IdentityHashMap<ModelPart, ModelPart> parents) throws Exception {
        if (disabled || !validRig(scope.model, parents)) return false;
        Object state = activeState.invoke(null);
        if (state == null || stateEntity.invoke(state) != scope.entity) return false;
        if (Boolean.TRUE.equals(firstPersonHand.invoke(state))) return false;
        if (!Boolean.TRUE.equals(inWorld.invoke(null, scope.entity))) return false;
        return !Boolean.TRUE.equals(parcool.invoke(null, scope.entity)) && !Boolean.TRUE.equals(held.invoke(null, scope.entity));
    }

    /**
     * Called immediately after the original actor yaw, before its mirror and
     * translation. Pre cancels PlayerRenderer, so reproduce its swim root here;
     * for an active HnS attack invoke PAL's own body stage at the same point.
     * Tentative changes are rolled back if the later FA/TnM retarget fails.
     */
    public static void root(BbModel model, LivingEntity entity, PoseStack stack, float partial,
                            IdentityHashMap<ModelPart, ModelPart> parents) {
        Scope scope = current(model, entity, stack);
        if (scope == null) return;
        try {
            if (!eligible(scope, parents)) return;
            AbstractClientPlayer player = (AbstractClientPlayer) scope.entity;
            float amount = player.getSwimAmount(partial);
            if (!player.isFallFlying() && amount > 0.0f && Float.isFinite(amount)) {
                stack.pushPose();
                scope.swimRootPushed = true;
                boolean fluid = player.isInWater() || player.isInFluidType((type, height) -> player.canSwimInFluidType(type));
                float target = fluid ? -90.0f - player.getViewXRot(partial) : -90.0f;
                if (!Float.isFinite(target)) throw new IllegalStateException("Invalid swim pitch");
                stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(amount * target));
                if (player.isVisuallySwimming()) stack.translate(0.0f, -1.0f, 0.3f);
            }
            if (combatAvailable()) {
                try {
                    Object combat = combatPrepare.invoke(null, player);
                    if (combat != null) {
                        // The guard preserves PoseStack's private trustedNormals as well
                        // as both matrices if a PAL or later FA/TnM stage fails.
                        stack.pushPose();
                        scope.combatRootPushed = true;
                        combatRoot.invoke(null, combat, stack);
                        scope.combat = combat;
                    }
                } catch (Throwable error) {
                    if (scope.combatRootPushed) { stack.popPose(); scope.combatRootPushed = false; }
                    scope.combat = null;
                    disableCombat(error);
                }
            }
        } catch (Throwable error) {
            discardRoot(scope);
            scope.combat = null;
            disabled = true;
            warn("Player root stage failed; original player rendering is retained.", unwrap(error));
        }
    }

    /** Drop our temporary root frames without losing Minecraft's normal flags. */
    private static void discardRoot(Scope scope) {
        if (scope.combatRootPushed) { scope.stack.popPose(); scope.combatRootPushed = false; }
        if (scope.swimRootPushed) { scope.stack.popPose(); scope.swimRootPushed = false; }
    }

    /** Restore the old actor root after the original mirror and translate. */
    private static void rollbackRoot(Scope scope) {
        if (!scope.combatRootPushed && !scope.swimRootPushed) return;
        discardRoot(scope);
        scope.stack.scale(-1.0f, -1.0f, 1.0f);
        scope.stack.translate(0.0f, -1.501f, 0.0f);
    }

    /** Lazy, optional PAL linkage: no third-party classes are resolved without both mods. */
    private static synchronized boolean combatAvailable() {
        if (combatDisabled) return false;
        if (combatChecked) return combatPrepare != null;
        combatChecked = true;
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("hackersandslashers") || !mods.isLoaded("player_animation_library")) return false;
        String hns = mods.getModContainerById("hackersandslashers").map(c -> c.getModInfo().getVersion().toString()).orElse("");
        String pal = mods.getModContainerById("player_animation_library").map(c -> c.getModInfo().getVersion().toString()).orElse("");
        if (!"2.0-beta2.5".equals(hns) || !"1.1.6+mc.1.21.1".equals(pal)
                || mods.isLoaded("emf_compat_hackers_and_slashers")) {
            combatWarn("Unsupported HnS/PAL version or separate HnS EMF addon; attack bridge is off.", null);
            return false;
        }
        try {
            Class<?> api = Class.forName("dev.settledlands.model.HackSlashAccess", true, PlayerMotionBridge.class.getClassLoader());
            combatPrepare = api.getMethod("prepare", AbstractClientPlayer.class);
            combatRoot = api.getMethod("root", Object.class, PoseStack.class);
            combatParts = api.getMethod("parts", Object.class, PlayerModel.class);
            combatItemPosition = api.getMethod("itemPosition", Object.class, PoseStack.class, boolean.class);
            combatItemPose = api.getMethod("itemPose", Object.class, PoseStack.class, boolean.class);
            return true;
        } catch (Throwable error) {
            disableCombat(error);
            return false;
        }
    }

    private static void combatWarn(String message, Throwable error) {
        if (combatWarned) return;
        combatWarned = true;
        System.err.println("[SettledLands/Motion251] " + message);
        if (error != null) error.printStackTrace(System.err);
    }

    private static void disableCombat(Throwable error) {
        combatDisabled = true;
        combatWarn("HnS/PAL bridge failed; it is disabled while FA/TnM remains available.", unwrap(error));
    }

    /** Translation only: unlike scaling it cannot change the private normal flag. */
    public static void itemPosition(BbModel model, LivingEntity entity, PoseStack stack, boolean left) {
        Scope scope = current(model, entity, stack);
        if (scope == null || !scope.combatReady || combatDisabled) return;
        Matrix4f oldPose = new Matrix4f(stack.last().pose());
        try {
            combatItemPosition.invoke(null, scope.combat, stack, left);
        } catch (Throwable error) {
            stack.last().pose().set(oldPose);
            disableCombat(error);
        }
    }

    /** Keep the item's nonuniform scale inside its own guard, even on an error. */
    public static void renderHeldItem(ItemInHandRenderer renderer, LivingEntity entity, ItemStack item,
                                      ItemDisplayContext context, boolean left, PoseStack stack,
                                      MultiBufferSource buffers, int light, BbModel model) {
        Scope scope = current(model, entity, stack);
        if (scope == null || !scope.combatReady || combatDisabled) {
            renderer.renderItem(entity, item, context, left, stack, buffers, light);
            return;
        }
        stack.pushPose();
        boolean guarded = true;
        try {
            try {
                combatItemPose.invoke(null, scope.combat, stack, left);
            } catch (Throwable error) {
                stack.popPose();
                guarded = false;
                disableCombat(error);
            }
            renderer.renderItem(entity, item, context, left, stack, buffers, light);
        } finally {
            if (guarded) stack.popPose();
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
        rollbackRoot(scope);
        scope.combat = null;
        scope.combatReady = false;
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
        boolean pushed, prepared, swimRootPushed, combatRootPushed, combatReady;
        Object combat;
        Scope(BbModel model, LivingEntity entity, PoseStack stack) {
            this.model = model; this.entity = entity; this.stack = stack;
        }
    }

    /** An isolated EMF-baked player rig; never rendered or installed on the shared renderer. */
    private static final class Biped {
        final ModelPart root;
        final PlayerModel<AbstractClientPlayer> model;
        final PlayerModel<AbstractClientPlayer> anchor;
        final boolean slim;
        final Map<String, ModelPart> parts = new LinkedHashMap<>();
        final Map<String, Matrix4f> rest = new LinkedHashMap<>();
        Biped(PlayerModel<AbstractClientPlayer> anchor, boolean slim) {
            this.anchor = anchor;
            this.slim = slim;
            root = Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER);
            model = new PlayerModel<>(root, slim);
            parts.put("body", model.body); parts.put("head", model.head);
            parts.put("right_arm", model.rightArm); parts.put("left_arm", model.leftArm);
            parts.put("right_leg", model.rightLeg); parts.put("left_leg", model.leftLeg);
            parts.forEach((name, part) -> rest.put(name, new Pose(part).matrix()));
        }
    }

    /** Retarget deltas from the genuine flat vanilla pivots to Ivy's real rest rig.
     * Fresh and TnM animate in flat biped space. Apply V * inverse(Vrest) * IvyRest,
     * then solve nested Ivy locals parent-first. Never write any part until all pass. */
    private static final class Rig {
        final Map<String, ModelPart> virtual = new LinkedHashMap<>();
        final IdentityHashMap<ModelPart, ModelPart> realToVirtual = new IdentityHashMap<>();
        final IdentityHashMap<ModelPart, Matrix4f> realRest = new IdentityHashMap<>();
        final IdentityHashMap<ModelPart, Matrix4f> virtualRest = new IdentityHashMap<>();
        final List<ModelPart> order = new ArrayList<>();

        static Rig capture(BbModel model, IdentityHashMap<ModelPart, ModelPart> parents, Biped biped) {
            Rig rig = new Rig();
            for (int i = 0; i < BB.length; i++) {
                ModelPart real = model.getPart(BB[i]);
                Matrix4f realMatrix = world(real, parents, null);
                ModelPart part = biped.parts.get(HUMANOID[i]);
                Matrix4f restMatrix = biped.rest.get(HUMANOID[i]);
                if (part == null || restMatrix == null || Pose.from(realMatrix) == null
                    || !restMatrix.isFinite() || Math.abs(restMatrix.determinant()) < 1e-8f) return null;
                rig.virtual.put(HUMANOID[i], part);
                rig.realToVirtual.put(real, part);
                rig.realRest.put(real, realMatrix);
                rig.virtualRest.put(part, restMatrix);
                rig.order.add(real);
            }
            rig.order.sort(Comparator.comparingInt(part -> depth(part, parents)));
            return rig;
        }

        boolean commit(IdentityHashMap<ModelPart, ModelPart> parents) {
            IdentityHashMap<ModelPart, Pose> updates = new IdentityHashMap<>();
            for (ModelPart real : order) {
                ModelPart part = realToVirtual.get(real);
                Matrix4f desired = new Pose(part).matrix()
                    .mul(new Matrix4f(virtualRest.get(part)).invert())
                    .mul(realRest.get(real));
                Matrix4f parent = world(parents.get(real), parents, updates);
                if (parent == null || !parent.isFinite() || Math.abs(parent.determinant()) < 1e-8f) return false;
                Matrix4f local = new Matrix4f(parent).invert().mul(desired);
                Pose result = Pose.from(local);
                if (result == null) return false;
                updates.put(real, result);
            }
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
