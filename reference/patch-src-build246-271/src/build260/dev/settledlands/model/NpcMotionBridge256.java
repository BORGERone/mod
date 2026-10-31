package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcProfile;
import java.lang.reflect.Field;
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
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Build 256 NPC player-equivalent motion bridge:
 *  - Skips GuideCorpseNpc and GuideTriggerNpc completely (preserves frozen die pose).
 *  - Preserves active GuideNpc custom clips (story scenes, loops, holds, Build 255 melee combos)
 *    and talking animations while adding held-item rendering in both hands.
 *  - On Ivy rigs with Fresh Animations: Player Extension (+ optional Touch'n Motion 1.0.4),
 *    runs FA + TnM via a detached client RemotePlayer proxy and retargets the 6 biped bones
 *    onto BbModel.
 *  - Falls back cleanly to BbModel.settledlands$renderActorOriginal (full player state machine:
 *    jump/fall/land/run/swim/climb/sneak + crossfades + held items) when FA/EMF are absent.
 */
public final class NpcMotionBridge256 {
    public static final int PROXY_ID_THRESHOLD = -0x50000000;
    public static final int PROXY_ID_BASE = -0x50256000;

    private static final String[] BB = {"body", "head", "rightarm", "leftarm", "rightleg", "leftleg"};
    private static final String[] HUMANOID = {"body", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final Consumer<Object> NO_MODEL_CALLBACK = ignored -> {};
    private static final float MATRIX_EPS = 0.0004f;

    private static final WeakHashMap<GuideNpc, NpcMotionPlayerProxy256> PROXIES = new WeakHashMap<>();
    private static final ThreadLocal<Deque<NpcMotionPlayerProxy256>> ACTIVE_PROXIES = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Biped>> FREE_BIPEDS = ThreadLocal.withInitial(ArrayDeque::new);

    private static boolean checked;
    private static boolean emfReady;
    private static boolean tnmReady;
    private static boolean warned;
    private static boolean reported;

    private static Method mountProxyState;
    private static Method unmountProxyState;
    private static Method prepareAndAnimate;
    private static Method modelBefore;
    private static Method drawnInWorld;
    private static Field emfIsInGui;
    private static boolean guiFieldsChecked;

    private NpcMotionBridge256() {}

    public static Entity lookupActiveProxy(ClientLevel level, int id) {
        if (level == null || id >= PROXY_ID_THRESHOLD) return null;
        Deque<NpcMotionPlayerProxy256> stack = ACTIVE_PROXIES.get();
        if (stack == null || stack.isEmpty()) return null;
        for (NpcMotionPlayerProxy256 proxy : stack) {
            if (proxy != null && proxy.getId() == id && proxy.level() == level) {
                return proxy;
            }
        }
        return null;
    }

    public static void renderNpc(BbModel model, GuideNpc npc, float entityYaw, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, int lod) {
        if (model == null) return;
        if (npc == null || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc
                || poseStack == null || buffer == null) {
            model.settledlands$renderNpcOriginal(npc, entityYaw, partialTick, poseStack, buffer, packedLight, lod);
            return;
        }

        // Render a walking body along its actual travel vector, independently of
        // lookAtPlayer. Restore the synchronized entity state after the render pass.
        float savedBodyYawO = npc.yBodyRotO;
        float savedBodyYaw = npc.yBodyRot;
        float travelYaw = movementBodyYaw(npc, partialTick);
        boolean overrideBodyYaw = Math.abs(Mth.wrapDegrees(travelYaw - savedBodyYaw)) > 0.01F;
        try {
            if (overrideBodyYaw) {
                npc.yBodyRotO = travelYaw;
                npc.yBodyRot = travelYaw;
                entityYaw = travelYaw;
            }

            // Preserve active full-body custom/story/combat animations and talking.
            CustomClipState clipState = evaluateCustomClip(model, npc, partialTick);
            if (clipState == CustomClipState.FULL_BODY || npc.isTalking()) {
                model.settledlands$renderNpcOriginal(npc, entityYaw, partialTick, poseStack, buffer, packedLight, lod);
                if (lod < 2) {
                    renderNpcHeldItems(model, npc, partialTick, poseStack, buffer, packedLight);
                }
                return;
            }

            // Distant LOD (>= 64 blocks): keep ultra-fast distant NPC path.
            if (lod >= 2) {
                model.settledlands$renderNpcOriginal(npc, entityYaw, partialTick, poseStack, buffer, packedLight, lod);
                return;
            }

            if (isIvyRig(model) && ensureEmfBridge() && renderFreshNpc(model, npc, partialTick, poseStack, buffer, packedLight, lod, clipState == CustomClipState.PARTIAL_MASK)) {
                return;
            }

            // Player-equivalent fallback state machine + held items.
            renderActorFallback(model, npc, partialTick, poseStack, buffer, packedLight, lod, clipState == CustomClipState.PARTIAL_MASK);
        } finally {
            npc.yBodyRotO = savedBodyYawO;
            npc.yBodyRot = savedBodyYaw;
        }
    }

    private static float movementBodyYaw(GuideNpc npc, float partialTick) {
        Vec3 movement = npc.getDeltaMovement();
        if (!npc.onGround() || npc.isPassenger() || movement == null
                || movement.x * movement.x + movement.z * movement.z < 1.0E-4D) {
            return Mth.rotLerp(partialTick, npc.yBodyRotO, npc.yBodyRot);
        }
        float yaw = (float) (Math.atan2(movement.z, movement.x) * 57.29577951308232D) - 90.0F;
        return Mth.wrapDegrees(yaw);
    }

    private enum CustomClipState {
        NONE,
        FULL_BODY,
        PARTIAL_MASK
    }

    private static CustomClipState evaluateCustomClip(BbModel model, GuideNpc npc, float partialTick) {
        String customName = npc.getCustomAnimName();
        if (customName == null || customName.isBlank()) return CustomClipState.NONE;
        BbAnimation anim = model.findAnimation(customName);
        if (anim == null) return CustomClipState.NONE;
        String loopMode = anim.getLoop();
        boolean active;
        if (npc.isCustomAnimLoop()
            || "loop".equalsIgnoreCase(loopMode)
            || "hold_on_last_frame".equalsIgnoreCase(loopMode)
            || "hold".equalsIgnoreCase(loopMode)) {
            active = true;
        } else {
            float elapsed = Math.max(0.0f, ((float) (npc.tickCount - npc.getCustomAnimStartTick()) + partialTick) / 20.0f);
            float duration = anim.getLength() <= 0.05f ? 1.0f : anim.getLength();
            active = elapsed <= duration;
        }
        if (!active) return CustomClipState.NONE;
        String boneMask = npc.getCustomAnimBone();
        if (boneMask == null || boneMask.isBlank() || "all".equalsIgnoreCase(boneMask.trim())) {
            return CustomClipState.FULL_BODY;
        }
        return CustomClipState.PARTIAL_MASK;
    }

    public static float effectiveScale(GuideNpc npc) {
        if (npc == null) return 1.0f;
        float scale = npc.getScale();
        if (scale <= 0.01f) {
            scale = npc.visualScale() / 100.0f;
        }
        if (scale <= 0.01f) {
            scale = 1.0f;
        }
        return Math.max(0.1f, Math.min(3.0f, scale));
    }

    private static void renderNpcHeldItems(BbModel model, GuideNpc npc, float partialTick,
                                           PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack main = npc.getMainHandItem();
        ItemStack off = npc.getOffhandItem();
        boolean hasMain = main != null && !main.isEmpty();
        boolean hasOff = off != null && !off.isEmpty();
        if (!hasMain && !hasOff) return;
        boolean leftHanded = npc.getMainArm() == HumanoidArm.LEFT;
        ItemStack rightStack = leftHanded ? off : main;
        ItemStack leftStack = leftHanded ? main : off;
        float scale = effectiveScale(npc);
        float bodyYaw = Mth.rotLerp(partialTick, npc.yBodyRotO, npc.yBodyRot);
        poseStack.pushPose();
        try {
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
            poseStack.scale(-1.0f, -1.0f, 1.0f);
            poseStack.translate(0.0f, -1.501f, 0.0f);
            if (rightStack != null && !rightStack.isEmpty()) {
                model.settledlands$renderHeldItem(npc, poseStack, buffer, packedLight, rightStack, false);
            }
            if (leftStack != null && !leftStack.isEmpty()) {
                model.settledlands$renderHeldItem(npc, poseStack, buffer, packedLight, leftStack, true);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderActorFallback(BbModel model, GuideNpc npc, float partialTick,
                                            PoseStack poseStack, MultiBufferSource buffer,
                                            int packedLight, int lod, boolean partialBoneMask) {
        if (partialBoneMask) {
            float yaw = Mth.rotLerp(partialTick, npc.yBodyRotO, npc.yBodyRot);
            model.settledlands$renderNpcOriginal(npc, yaw, partialTick, poseStack, buffer, packedLight, lod);
            if (lod < 2) {
                renderNpcHeldItems(model, npc, partialTick, poseStack, buffer, packedLight);
            }
            return;
        }
        float scale = effectiveScale(npc);
        boolean restoreRot = false;
        float savedHeadO = npc.yHeadRotO, savedHead = npc.yHeadRot;
        float savedXRotO = npc.xRotO, savedXRot = npc.getXRot();
        NpcProfile profile = npc.profile();
        if (profile != null && !profile.lookAtPlayer && !npc.isTalking() && npc.getTarget() == null) {
            restoreRot = true;
            npc.yHeadRotO = npc.yBodyRotO;
            npc.yHeadRot = npc.yBodyRot;
            npc.xRotO = 0.0f;
            npc.setXRot(0.0f);
        }
        try {
            model.settledlands$renderActorOriginal(npc, scale, partialTick, poseStack, buffer, packedLight);
        } finally {
            if (restoreRot) {
                npc.yHeadRotO = savedHeadO;
                npc.yHeadRot = savedHead;
                npc.xRotO = savedXRotO;
                npc.setXRot(savedXRot);
            }
            BbModel.CURRENT_LOD_LEVEL.set(0);
            BbModel.CURRENT_LOD.set(Boolean.FALSE);
        }
    }

    private static synchronized boolean ensureEmfBridge() {
        if (checked) return emfReady;
        checked = true;
        try {
            ModList mods = ModList.get();
            if (mods == null || !mods.isLoaded("entity_model_features") || !mods.isLoaded("entity_texture_features")) {
                return false;
            }
            String emfVer = modVersion(mods, "entity_model_features");
            String etfVer = modVersion(mods, "entity_texture_features");
            if (!emfVer.startsWith("3.3.") || !etfVer.startsWith("7.2.")) {
                warnOnce("Unsupported EMF/ETF version (" + emfVer + " / " + etfVer + "); NPC uses embedded actor animation.", null);
                return false;
            }
            ClassLoader loader = NpcMotionBridge256.class.getClassLoader();
            boolean wantTnm = false;
            Method beforeMethod = null;
            Method inWorldMethod = null;
            if (mods.isLoaded("touch_n_motion")) {
                String tnmVer = modVersion(mods, "touch_n_motion");
                String coreVer = modVersion(mods, "emf_compat_core");
                if ("1.0.4".equals(tnmVer) && "2.3.2".equals(coreVer)) {
                    Class<?> space = Class.forName("strm.touchnmotion.ModelSpace", false, loader);
                    Class<?> drawn = Class.forName("strm.touchnmotion.interaction.DrawnEntities", false, loader);
                    beforeMethod = space.getMethod("before", LivingEntity.class, PoseStack.class, float.class, Consumer.class);
                    inWorldMethod = drawn.getMethod("inWorld", Entity.class);
                    wantTnm = true;
                } else {
                    warnOnce("Unsupported Touch'n Motion / Core version (" + tnmVer + " / " + coreVer + "); running FA without TnM on NPCs.", null);
                }
            }
            Class<?> access = Class.forName("dev.settledlands.model.NpcFreshEmfAccess256", true, loader);
            Method install = access.getMethod("install", boolean.class);
            if (!Boolean.TRUE.equals(install.invoke(null, Boolean.FALSE))) {
                return false;
            }
            boolean hasHook = wantTnm && Boolean.TRUE.equals(access.getMethod("hasTnmHook").invoke(null));
            mountProxyState = access.getMethod("mountProxyState", Object.class, float.class, float.class, float.class, float.class);
            unmountProxyState = access.getMethod("unmountProxyState", Object.class);
            prepareAndAnimate = access.getMethod(
                "prepareAndAnimate",
                PlayerModel.class, PlayerModel.class, Object.class, boolean.class,
                float.class, float.class, float.class, float.class
            );
            modelBefore = hasHook ? beforeMethod : null;
            drawnInWorld = hasHook ? inWorldMethod : null;
            tnmReady = hasHook;
            emfReady = true;
            return true;
        } catch (Throwable t) {
            emfReady = false;
            tnmReady = false;
            warnOnce("Could not initialize NPC Fresh Animations bridge; falling back to actor state machine.", unwrap(t));
            return false;
        }
    }

    private static String modVersion(ModList mods, String id) {
        return mods.getModContainerById(id).map(c -> String.valueOf(c.getModInfo().getVersion())).orElse("");
    }

    private static void warnOnce(String msg, Throwable error) {
        if (warned) return;
        warned = true;
        System.err.println("[SettledLands/NpcMotion256] " + msg);
        if (error != null) error.printStackTrace(System.err);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
    }

    private static boolean isIvyRig(BbModel model) {
        if (model == null || model.getName() == null) return false;
        String name = model.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("ivy") && !name.equals("ivy_oleander")) return false;
        return validRig(model, model.settledlands$partParents());
    }

    private static boolean isGuiPass() {
        if (!guiFieldsChecked) {
            guiFieldsChecked = true;
            ClassLoader loader = NpcMotionBridge256.class.getClassLoader();
            try {
                Class<?> emfState = Class.forName("traben.entity_model_features.models.animation.state.EMFState", false, loader);
                emfIsInGui = emfState.getField("isInGui");
            } catch (Throwable ignored) {
            }
        }
        if (emfIsInGui == null) return false;
        try {
            return emfIsInGui.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static NpcMotionPlayerProxy256 getOrCreateProxy(GuideNpc npc, ClientLevel level) {
        synchronized (PROXIES) {
            NpcMotionPlayerProxy256 proxy = PROXIES.get(npc);
            if (proxy == null || !proxy.matches(npc, level)) {
                proxy = new NpcMotionPlayerProxy256(level, npc);
                PROXIES.put(npc, proxy);
            }
            return proxy;
        }
    }

    private static boolean renderFreshNpc(BbModel model, GuideNpc npc, float partialTick,
                                          PoseStack poseStack, MultiBufferSource buffer,
                                          int packedLight, int lod, boolean partialBoneMask) {
        Level rawLevel = npc.level();
        if (!(rawLevel instanceof ClientLevel clientLevel)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getEntityRenderDispatcher() == null || mc.getEntityModels() == null) return false;
        IdentityHashMap<ModelPart, ModelPart> parents = model.settledlands$partParents();
        if (!validRig(model, parents)) return false;

        NpcMotionPlayerProxy256 proxy = getOrCreateProxy(npc, clientLevel);
        proxy.syncFrom(npc);

        var entityRenderer = mc.getEntityRenderDispatcher().getRenderer(proxy);
        if (!(entityRenderer instanceof PlayerRenderer playerRenderer)) return false;
        PlayerModel<AbstractClientPlayer> anchorModel = playerRenderer.getModel();
        if (anchorModel == null) return false;
        boolean slim = proxy.getSkin() != null && proxy.getSkin().model() == PlayerSkin.Model.SLIM;

        float scale = effectiveScale(npc);
        float pt = lod >= 1 ? 0.0f : partialTick;
        float bodyYaw = Mth.rotLerp(partialTick, npc.yBodyRotO, npc.yBodyRot);
        float headAbsYaw = Mth.rotLerp(partialTick, npc.yHeadRotO, npc.yHeadRot);
        float headYaw = Mth.wrapDegrees(headAbsYaw - bodyYaw);
        float headPitch = Mth.rotLerp(partialTick, npc.xRotO, npc.getXRot());
        NpcProfile profile = npc.profile();
        if (profile != null && !profile.lookAtPlayer && !npc.isTalking() && npc.getTarget() == null) {
            headYaw = 0.0f;
            headPitch = 0.0f;
        }
        float rawSwing = npc.walkAnimation.position(partialTick);
        float rawAmount = npc.walkAnimation.speed(partialTick);
        float age = (float) npc.tickCount + pt;

        boolean useTnmThisFrame = false;
        if (tnmReady && lod == 0 && !isGuiPass() && modelBefore != null && drawnInWorld != null) {
            try {
                useTnmThisFrame = Boolean.TRUE.equals(drawnInWorld.invoke(null, npc));
            } catch (Throwable ignored) {
                useTnmThisFrame = false;
            }
        }

        Biped biped = acquireBiped(anchorModel, slim);
        Object mountedToken = null;
        boolean outerPushed = false;
        boolean tnmPushed = false;
        boolean proxyRegistered = false;
        BbModel.CURRENT_LOD_LEVEL.set(lod);
        BbModel.CURRENT_LOD.set(lod > 0);
        try {
            Entity vehicle = npc.getVehicle();
            boolean shouldSit = npc.isPassenger() && vehicle != null && vehicle.shouldRiderSit();
            float swing = 0.0f;
            float amount = 0.0f;
            if (!shouldSit && npc.isAlive()) {
                swing = rawSwing * (npc.isBaby() ? 3.0f : 1.0f);
                amount = Math.min(rawAmount, 1.0f);
            }

            poseStack.pushPose();
            outerPushed = true;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));

            float swimAmount = npc.getSwimAmount(partialTick);
            if (!npc.isFallFlying() && swimAmount > 0.0f && Float.isFinite(swimAmount)) {
                boolean fluid = npc.isInWater() || npc.isInFluidType((type, height) -> npc.canSwimInFluidType(type));
                float targetPitch = fluid ? -90.0f - npc.getViewXRot(partialTick) : -90.0f;
                if (Float.isFinite(targetPitch)) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(swimAmount * targetPitch));
                    if (npc.isVisuallySwimming()) {
                        poseStack.translate(0.0f, -1.0f, 0.3f);
                    }
                }
            }

            poseStack.scale(-1.0f, -1.0f, 1.0f);
            poseStack.translate(0.0f, -1.501f, 0.0f);

            if (useTnmThisFrame) {
                pushActiveProxy(proxy);
                proxyRegistered = true;
                poseStack.pushPose();
                tnmPushed = true;
                try {
                    modelBefore.invoke(null, proxy, poseStack, partialTick, NO_MODEL_CALLBACK);
                } catch (Throwable tnmErr) {
                    poseStack.popPose();
                    tnmPushed = false;
                    popActiveProxy(proxy);
                    proxyRegistered = false;
                    useTnmThisFrame = false;
                }
            }

            mountedToken = mountProxyState.invoke(null, proxy, swing, amount, headYaw, headPitch);
            if (mountedToken == null) {
                return false;
            }

            PlayerModel<AbstractClientPlayer> vanilla = biped.model;
            biped.root.getAllParts().forEach(ModelPart::resetPose);
            vanilla.crouching = proxy.isCrouching();
            vanilla.rightArmPose = armPoseFor(proxy, true);
            vanilla.leftArmPose = armPoseFor(proxy, false);
            vanilla.attackTime = npc.getAttackAnim(partialTick);
            vanilla.riding = shouldSit;
            vanilla.young = npc.isBaby();
            vanilla.prepareMobModel(proxy, swing, amount, partialTick);
            vanilla.setupAnim(proxy, swing, amount, age, headYaw, headPitch);

            boolean animated = Boolean.TRUE.equals(
                prepareAndAnimate.invoke(null, vanilla, anchorModel, proxy, useTnmThisFrame, swing, amount, headYaw, headPitch)
            );
            if (!animated) {
                return false;
            }

            model.getRootPart().getAllParts().forEach(ModelPart::resetPose);
            Rig rig = Rig.capture(model, parents, biped);
            if (rig == null || !rig.commit(parents)) {
                return false;
            }

            if (partialBoneMask) {
                applyPartialCustomMask(model, npc, rawSwing, rawAmount, age, headYaw, headPitch, pt);
            }

            ResourceLocation tex = model.getTextureLocation();
            if (tex == null) {
                return false;
            }
            RenderType renderType = "ivy".equalsIgnoreCase(model.getName())
                ? RenderType.entityCutout(tex)
                : RenderType.entityCutoutNoCull(tex);
            VertexConsumer vc = buffer.getBuffer(renderType);
            model.getRootPart().render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, -1);

            boolean leftHanded = npc.getMainArm() == HumanoidArm.LEFT;
            ItemStack main = npc.getMainHandItem();
            ItemStack off = npc.getOffhandItem();
            ItemStack rightStack = leftHanded ? off : main;
            ItemStack leftStack = leftHanded ? main : off;
            if (rightStack != null && !rightStack.isEmpty()) {
                model.settledlands$renderHeldItem(npc, poseStack, buffer, packedLight, rightStack, false);
            }
            if (leftStack != null && !leftStack.isEmpty()) {
                model.settledlands$renderHeldItem(npc, poseStack, buffer, packedLight, leftStack, true);
            }

            if (!reported) {
                reported = true;
                System.out.println("[SettledLands/NpcMotion256] Fresh Animations"
                    + (useTnmThisFrame ? " + Touch'n Motion" : "")
                    + " active for GuideNpc model=" + model.getName());
            }
            return true;
        } catch (Throwable t) {
            warnOnce("NPC Fresh Animations render failed; falling back to actor state machine.", unwrap(t));
            return false;
        } finally {
            if (mountedToken != null) {
                try {
                    unmountProxyState.invoke(null, mountedToken);
                } catch (Throwable ignored) {
                }
            }
            if (tnmPushed) {
                poseStack.popPose();
            }
            if (proxyRegistered) {
                popActiveProxy(proxy);
            }
            if (outerPushed) {
                poseStack.popPose();
            }
            releaseBiped(biped);
            BbModel.CURRENT_LOD_LEVEL.set(0);
            BbModel.CURRENT_LOD.set(Boolean.FALSE);
        }
    }

    private static void applyPartialCustomMask(BbModel model, GuideNpc npc,
                                               float swing, float amount, float age,
                                               float headYaw, float headPitch, float pt) {
        String mask = npc.getCustomAnimBone();
        if (mask == null || mask.isEmpty()) return;
        ModelPart maskRoot = model.getPart(mask);
        if (maskRoot == null) return;
        // Snapshot non-masked bones, evaluate masked custom clip via setupAnimRaw, then restore non-masked bones.
        List<ModelPart> allParts = model.getRootPart().getAllParts().toList();
        IdentityHashMap<ModelPart, Boolean> inMask = new IdentityHashMap<>();
        maskRoot.getAllParts().forEach(p -> inMask.put(p, Boolean.TRUE));
        Pose[] saved = new Pose[allParts.size()];
        for (int i = 0; i < allParts.size(); i++) {
            saved[i] = new Pose(allParts.get(i));
        }
        model.setupAnimRaw(
            false, true, npc.getCustomAnimName(), mask, npc.isCustomAnimLoop(),
            npc.getCustomAnimStartTick(), npc.tickCount,
            swing, amount, age, headYaw, headPitch, pt
        );
        for (int i = 0; i < allParts.size(); i++) {
            ModelPart p = allParts.get(i);
            if (!inMask.containsKey(p)) {
                saved[i].set(p);
            }
        }
    }

    private static HumanoidModel.ArmPose armPoseFor(NpcMotionPlayerProxy256 proxy, boolean rightArm) {
        boolean mainIsRight = proxy.getMainArm() == HumanoidArm.RIGHT;
        ItemStack stack = (rightArm == mainIsRight) ? proxy.getMainHandItem() : proxy.getOffhandItem();
        return stack != null && !stack.isEmpty() ? HumanoidModel.ArmPose.ITEM : HumanoidModel.ArmPose.EMPTY;
    }

    private static void pushActiveProxy(NpcMotionPlayerProxy256 proxy) {
        Deque<NpcMotionPlayerProxy256> stack = ACTIVE_PROXIES.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ACTIVE_PROXIES.set(stack);
        }
        stack.push(proxy);
    }

    private static void popActiveProxy(NpcMotionPlayerProxy256 proxy) {
        Deque<NpcMotionPlayerProxy256> stack = ACTIVE_PROXIES.get();
        if (stack == null) return;
        stack.removeFirstOccurrence(proxy);
        if (stack.isEmpty()) ACTIVE_PROXIES.remove();
    }

    private static Biped acquireBiped(PlayerModel<AbstractClientPlayer> anchor, boolean slim) {
        Deque<Biped> free = FREE_BIPEDS.get();
        for (Iterator<Biped> it = free.iterator(); it.hasNext();) {
            Biped candidate = it.next();
            if (candidate.anchor == anchor && candidate.slim == slim) {
                it.remove();
                return candidate;
            }
        }
        return new Biped(anchor, slim);
    }

    private static void releaseBiped(Biped biped) {
        if (biped == null) return;
        Deque<Biped> free = FREE_BIPEDS.get();
        free.addFirst(biped);
        while (free.size() > 4) free.removeLast();
    }

    private static boolean validRig(BbModel model, IdentityHashMap<ModelPart, ModelPart> parents) {
        if (parents == null || model.getRootPart() == null) return false;
        ModelPart body = model.getPart("body"), waist = model.getPart("waist");
        if (body == null || waist == null || !beneath(waist, body, parents)) return false;
        IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();
        for (String name : BB) {
            ModelPart part = model.getPart(name);
            if (part == null || seen.put(part, Boolean.TRUE) != null || !beneath(part, model.getRootPart(), parents)) {
                return false;
            }
            if (part != body && !beneath(part, waist, parents)) {
                return false;
            }
        }
        return true;
    }

    private static boolean beneath(ModelPart part, ModelPart ancestor, IdentityHashMap<ModelPart, ModelPart> parents) {
        for (int i = 0; part != null && i < 128; i++, part = parents.get(part)) {
            if (part == ancestor) return true;
        }
        return false;
    }

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
            parts.put("body", model.body);
            parts.put("head", model.head);
            parts.put("right_arm", model.rightArm);
            parts.put("left_arm", model.leftArm);
            parts.put("right_leg", model.rightLeg);
            parts.put("left_leg", model.leftLeg);
            parts.forEach((name, part) -> rest.put(name, new Pose(part).matrix()));
        }
    }

    private static final class Rig {
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
                        || !restMatrix.isFinite() || Math.abs(restMatrix.determinant()) < 1e-8f) {
                    return null;
                }
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
        while (part != null && count < 128) {
            count++;
            part = parents.get(part);
        }
        return count;
    }

    private static Matrix4f world(ModelPart part, IdentityHashMap<ModelPart, ModelPart> parents,
                                  IdentityHashMap<ModelPart, Pose> overrides) {
        ArrayDeque<ModelPart> path = new ArrayDeque<>();
        while (part != null && path.size() < 128) {
            path.addFirst(part);
            part = parents.get(part);
        }
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

        Pose(ModelPart p) {
            this(p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale);
        }

        Pose(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        Matrix4f matrix() {
            return new Matrix4f().translation(x / 16.0f, y / 16.0f, z / 16.0f)
                .rotate(new Quaternionf().rotationZYX(rz, ry, rx))
                .scale(sx, sy, sz);
        }

        static Pose from(Matrix4f matrix) {
            if (matrix == null || !matrix.isFinite() || Math.abs(matrix.determinant()) < 1e-8f) return null;
            Vector3f t = matrix.getTranslation(new Vector3f());
            Vector3f scale = matrix.getScale(new Vector3f());
            Quaternionf q = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
            Matrix4f rotation = new Matrix4f().rotation(q);
            float cosine = (float) Math.hypot(rotation.m00(), rotation.m01());
            float angleY = (float) Math.atan2(-rotation.m02(), cosine);
            float angleX, angleZ;
            if (cosine > 1e-6f) {
                angleX = (float) Math.atan2(rotation.m12(), rotation.m22());
                angleZ = (float) Math.atan2(rotation.m01(), rotation.m00());
            } else {
                angleX = (float) Math.atan2(-rotation.m21(), rotation.m11());
                angleZ = 0.0f;
            }
            if (!t.isFinite() || !scale.isFinite() || !q.isFinite()
                    || !Float.isFinite(angleX) || !Float.isFinite(angleY) || !Float.isFinite(angleZ)) {
                return null;
            }
            Pose pose = new Pose(t.x * 16.0f, t.y * 16.0f, t.z * 16.0f, angleX, angleY, angleZ, scale.x, scale.y, scale.z);
            return matrix.equals(pose.matrix(), MATRIX_EPS) ? pose : null;
        }

        void set(ModelPart p) {
            p.x = x;
            p.y = y;
            p.z = z;
            p.xRot = rx;
            p.yRot = ry;
            p.zRot = rz;
            p.xScale = sx;
            p.yScale = sy;
            p.zScale = sz;
        }
    }
}
