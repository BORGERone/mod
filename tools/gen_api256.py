#!/usr/bin/env python3
"""Compile-only Minecraft/NeoForge/Mixin/PAL/EMF/ETF/TnM API silhouettes for build 256; never package in a mod jar."""
import pathlib
import sys
import gen_api252

SRC = dict(gen_api252.SRC)
SRC.update({
    'com/mojang/authlib/GameProfile.java': '''package com.mojang.authlib;
import java.util.UUID;
public class GameProfile {
    public GameProfile(UUID id, String name) {}
    public UUID getId() { return null; }
    public String getName() { return ""; }
}''',
    'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Mixin {
    Class<?>[] value() default {};
    String[] targets() default {};
    int priority() default 1000;
    boolean remap() default true;
}''',
    'org/spongepowered/asm/mixin/Shadow.java': '''package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Shadow {}''',
    'org/spongepowered/asm/mixin/injection/At.java': '''package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface At {
    String value();
    String target() default "";
}''',
    'org/spongepowered/asm/mixin/injection/Inject.java': '''package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {
    String[] method();
    At[] at();
    boolean cancellable() default false;
    int require() default -1;
}''',
    'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': '''package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfo {
    public boolean isCancelled() { return false; }
    public void cancel() {}
}''',
    'org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java': '''package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfoReturnable<R> extends CallbackInfo {
    public void setReturnValue(R value) {}
    public R getReturnValue() { return null; }
}''',
    'net/neoforged/api/distmarker/Dist.java': '''package net.neoforged.api.distmarker;
public enum Dist { CLIENT, DEDICATED_SERVER }''',
    'net/neoforged/bus/api/EventPriority.java': '''package net.neoforged.bus.api;
public enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }''',
    'net/neoforged/bus/api/SubscribeEvent.java': '''package net.neoforged.bus.api;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;
    boolean receiveCanceled() default false;
}''',
    'net/neoforged/fml/common/EventBusSubscriber.java': '''package net.neoforged.fml.common;
import java.lang.annotation.*;
import net.neoforged.api.distmarker.Dist;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "";
    Bus bus() default Bus.GAME;
    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};
    enum Bus { GAME, MOD }
}''',
    'net/minecraft/world/entity/HumanoidArm.java': '''package net.minecraft.world.entity;
public enum HumanoidArm { LEFT, RIGHT }''',
    'net/minecraft/world/entity/EquipmentSlot.java': '''package net.minecraft.world.entity;
public enum EquipmentSlot { MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY }''',
    'net/minecraft/world/entity/Pose.java': '''package net.minecraft.world.entity;
public enum Pose { STANDING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK, CROUCHING, LONG_JUMPING, DYING }''',
    'net/minecraft/world/InteractionHand.java': '''package net.minecraft.world;
public enum InteractionHand { MAIN_HAND, OFF_HAND }''',
    'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys;
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public final double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
}''',
    'net/minecraft/world/entity/WalkAnimationState.java': '''package net.minecraft.world.entity;
public class WalkAnimationState {
    public void setSpeed(float s) {}
    public void update(float target, float factor) {}
    public float speed() { return 0f; }
    public float speed(float pt) { return 0f; }
    public float position() { return 0f; }
    public float position(float pt) { return 0f; }
}''',
    'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
import net.minecraft.world.entity.Entity;
public abstract class Level {
    public boolean isClientSide() { return false; }
    public abstract Entity getEntity(int id);
}''',
    'net/minecraft/client/multiplayer/ClientLevel.java': '''package net.minecraft.client.multiplayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class ClientLevel extends Level {
    @Override public Entity getEntity(int id) { return null; }
}''',
    'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
public class Entity {
    public double xo, yo, zo, xOld, yOld, zOld;
    public float yRotO, xRotO, fallDistance;
    public int tickCount;
    public boolean horizontalCollision, verticalCollision;
    public int getId() { return 0; }
    public void setId(int id) {}
    public UUID getUUID() { return null; }
    public Level level() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public void setPosRaw(double x, double y, double z) {}
    public Vec3 getDeltaMovement() { return Vec3.ZERO; }
    public void setDeltaMovement(Vec3 v) {}
    public boolean onGround() { return true; }
    public void setOnGround(boolean v) {}
    public float getYRot() { return 0f; }
    public void setYRot(float v) {}
    public float getXRot() { return 0f; }
    public void setXRot(float v) {}
    public float getViewXRot(float pt) { return 0f; }
    public Entity getVehicle() { return null; }
    public boolean isPassenger() { return false; }
    public boolean shouldRiderSit() { return true; }
    public boolean isShiftKeyDown() { return false; }
    public boolean isCrouching() { return false; }
    public boolean isSprinting() { return false; }
    public boolean isSwimming() { return false; }
    public boolean isInWater() { return false; }
    public boolean isSpectator() { return false; }
    public boolean isInvisible() { return false; }
    public boolean isRemoved() { return false; }
    public Pose getPose() { return Pose.STANDING; }
    public void setPose(Pose p) {}
    public String getScoreboardName() { return ""; }
}''',
    'net/minecraft/world/entity/LivingEntity.java': '''package net.minecraft.world.entity;
import java.util.function.BiPredicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidType;
public class LivingEntity extends Entity {
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public float yBodyRot, yBodyRotO, yHeadRot, yHeadRotO, oAttackAnim, attackAnim;
    public int hurtTime, hurtDuration, deathTime, swingTime;
    public boolean swinging;
    public InteractionHand swingingArm = InteractionHand.MAIN_HAND;
    public float getAttackAnim(float t) { return 0f; }
    public boolean isBaby() { return false; }
    public boolean isAlive() { return true; }
    public boolean isSleeping() { return false; }
    public float getSwimAmount(float t) { return 0f; }
    public boolean isFallFlying() { return false; }
    public boolean isVisuallySwimming() { return false; }
    public boolean isInFluidType(BiPredicate<FluidType, Double> p) { return false; }
    public boolean canSwimInFluidType(FluidType f) { return false; }
    public float getScale() { return 1f; }
    public float getHealth() { return 20f; }
    public ItemStack getMainHandItem() { return null; }
    public ItemStack getOffhandItem() { return null; }
    public ItemStack getItemBySlot(EquipmentSlot slot) { return null; }
    public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
    public ItemStack getUseItem() { return null; }
    public int getUseItemRemainingTicks() { return 0; }
    public boolean isUsingItem() { return false; }
    public InteractionHand getUsedItemHand() { return InteractionHand.MAIN_HAND; }
}''',
    'net/minecraft/world/entity/Mob.java': '''package net.minecraft.world.entity;
public class Mob extends LivingEntity {
    public LivingEntity getTarget() { return null; }
}''',
    'net/minecraft/world/entity/PathfinderMob.java': '''package net.minecraft.world.entity;
public class PathfinderMob extends Mob {}''',
    'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
import net.minecraft.world.entity.LivingEntity;
public class Player extends LivingEntity {}''',
    'net/minecraft/client/player/AbstractClientPlayer.java': '''package net.minecraft.client.player;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
public class AbstractClientPlayer extends Player {
    public AbstractClientPlayer() {}
    public AbstractClientPlayer(ClientLevel level, GameProfile profile) {}
    public PlayerSkin getSkin() { return null; }
}''',
    'net/minecraft/client/player/RemotePlayer.java': '''package net.minecraft.client.player;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
public class RemotePlayer extends AbstractClientPlayer {
    public RemotePlayer(ClientLevel level, GameProfile profile) { super(level, profile); }
}''',
    'net/minecraft/world/item/ItemStack.java': '''package net.minecraft.world.item;
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    public boolean isEmpty() { return true; }
}''',
    'com/mojang/blaze3d/vertex/VertexConsumer.java': '''package com.mojang.blaze3d.vertex;
public interface VertexConsumer {}''',
    'net/minecraft/client/renderer/RenderType.java': '''package net.minecraft.client.renderer;
import net.minecraft.resources.ResourceLocation;
public abstract class RenderType {
    public static RenderType entityCutout(ResourceLocation loc) { return null; }
    public static RenderType entityCutoutNoCull(ResourceLocation loc) { return null; }
    public static RenderType entityTranslucent(ResourceLocation loc) { return null; }
    public static RenderType entitySolid(ResourceLocation loc) { return null; }
}''',
    'net/minecraft/client/renderer/MultiBufferSource.java': '''package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);
}''',
    'net/minecraft/client/renderer/texture/OverlayTexture.java': '''package net.minecraft.client.renderer.texture;
public class OverlayTexture {
    public static final int NO_OVERLAY = 655360;
}''',
    'com/mojang/math/Axis.java': '''package com.mojang.math;
import org.joml.Quaternionf;
public interface Axis {
    Axis XP = null, YP = null, ZP = null;
    Quaternionf rotationDegrees(float degrees);
}''',
    'net/minecraft/util/Mth.java': '''package net.minecraft.util;
public class Mth {
    public static float wrapDegrees(float v) { return v; }
    public static float rotLerp(float d, float a, float b) { return a + d * (b - a); }
    public static float lerp(float d, float a, float b) { return a + d * (b - a); }
}''',
    'net/minecraft/client/model/Model.java': '''package net.minecraft.client.model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
public abstract class Model {
    public abstract void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color);
}''',
    'net/minecraft/client/model/EntityModel.java': '''package net.minecraft.client.model;
import net.minecraft.world.entity.Entity;
public abstract class EntityModel<T extends Entity> extends Model {
    public boolean riding, young;
    public float attackTime;
    public void prepareMobModel(T e, float a, float b, float c) {}
    public abstract void setupAnim(T e, float a, float b, float c, float d, float f);
}''',
    'net/minecraft/client/model/AgeableListModel.java': '''package net.minecraft.client.model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
public abstract class AgeableListModel<E extends Entity> extends EntityModel<E> {
    @Override public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {}
}''',
    'net/minecraft/client/model/HumanoidModel.java': '''package net.minecraft.client.model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
public class HumanoidModel<T extends LivingEntity> extends AgeableListModel<T> {
    public ModelPart body, head, hat, rightArm, leftArm, rightLeg, leftLeg;
    public boolean crouching;
    public ArmPose rightArmPose = ArmPose.EMPTY, leftArmPose = ArmPose.EMPTY;
    public enum ArmPose { EMPTY, ITEM }
    @Override public void prepareMobModel(T e, float a, float b, float c) {}
    @Override public void setupAnim(T e, float a, float b, float c, float d, float f) {}
}''',
    'net/minecraft/client/model/geom/ModelPart.java': '''package net.minecraft.client.model.geom;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.stream.Stream;
public class ModelPart {
    public float x, y, z, xRot, yRot, zRot, xScale = 1f, yScale = 1f, zScale = 1f;
    public boolean visible = true, skipDraw;
    public ModelPart(List<?> cubes, Map<String, ModelPart> children) {}
    public Stream<ModelPart> getAllParts() { return Stream.of(this); }
    public void resetPose() {}
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {}
}''',
    'net/minecraft/client/renderer/entity/player/PlayerRenderer.java': '''package net.minecraft.client.renderer.entity.player;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public void render(AbstractClientPlayer player, float yaw, float pt, PoseStack poseStack, MultiBufferSource buffer, int light) {}
    public void renderRightHand(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player) {}
    public void renderLeftHand(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player) {}
}''',
    'net/neoforged/neoforge/client/event/RenderPlayerEvent.java': '''package net.neoforged.neoforge.client.event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
public abstract class RenderPlayerEvent {
    public Player getEntity() { return null; }
    public PlayerRenderer getRenderer() { return null; }
    public float getPartialTick() { return 0f; }
    public PoseStack getPoseStack() { return null; }
    public MultiBufferSource getMultiBufferSource() { return null; }
    public int getPackedLight() { return 0; }
    public static class Pre extends RenderPlayerEvent {
        @Override public Player getEntity() { return null; }
        @Override public PlayerRenderer getRenderer() { return null; }
        @Override public float getPartialTick() { return 0f; }
        @Override public PoseStack getPoseStack() { return null; }
        @Override public MultiBufferSource getMultiBufferSource() { return null; }
        @Override public int getPackedLight() { return 0; }
        public void setCanceled(boolean c) {}
        public boolean isCanceled() { return false; }
    }
}''',
    'net/neoforged/neoforge/client/event/RenderArmEvent.java': '''package net.neoforged.neoforge.client.event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
public class RenderArmEvent {
    public PoseStack getPoseStack() { return null; }
    public MultiBufferSource getMultiBufferSource() { return null; }
    public int getPackedLight() { return 0; }
    public AbstractClientPlayer getPlayer() { return null; }
    public HumanoidArm getArm() { return HumanoidArm.RIGHT; }
    public void setCanceled(boolean c) {}
    public boolean isCanceled() { return false; }
}''',
    'dev/settledlands/npc/NpcProfile.java': '''package dev.settledlands.npc;
public class NpcProfile {
    public boolean lookAtPlayer = true;
}''',
    'dev/settledlands/npc/GuideNpc.java': '''package dev.settledlands.npc;
import net.minecraft.world.entity.PathfinderMob;
public class GuideNpc extends PathfinderMob {
    public NpcProfile profile() { return null; }
    public int visualScale() { return 100; }
    public int poseIndex() { return 0; }
    public boolean isTalking() { return false; }
    public String getCustomAnimName() { return ""; }
    public String getCustomAnimBone() { return ""; }
    public boolean isCustomAnimLoop() { return false; }
    public int getCustomAnimStartTick() { return 0; }
}''',
    'dev/settledlands/npc/GuideCorpseNpc.java': '''package dev.settledlands.npc;
public class GuideCorpseNpc extends GuideNpc {
    public boolean isCoffin() { return false; }
}''',
    'dev/settledlands/npc/GuideTriggerNpc.java': '''package dev.settledlands.npc;
public class GuideTriggerNpc extends GuideNpc {}''',
    'dev/settledlands/npc/PossessionClientSync.java': '''package dev.settledlands.npc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public class PossessionClientSync implements CustomPacketPayload {
    public static final Map<UUID, State> CLIENT_STATES = new HashMap<>();
    public static final class State {
        public final String model;
        public final String name;
        public State(String model, String name) { this.model = model; this.name = name; }
    }
    public PossessionClientSync(UUID id, String model, String name) {}
}''',
    'dev/settledlands/model/BbAnimation.java': '''package dev.settledlands.model;
import java.util.Set;
public class BbAnimation {
    public String getName() { return ""; }
    public float getLength() { return 0f; }
    public String getLoop() { return ""; }
    public Set<String> getAnimatedBones() { return Set.of(); }
    public float[] sample(float time, String boneName, String channel) { return null; }
}''',
    'dev/settledlands/model/BbModelManager.java': '''package dev.settledlands.model;
public class BbModelManager {
    public static BbModel getModel(String name) { return null; }
}''',
    'dev/settledlands/model/BbModel.java': '''package dev.settledlands.model;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.GuideNpc;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
public class BbModel {
    public static final ThreadLocal<Integer> CURRENT_LOD_LEVEL = ThreadLocal.withInitial(() -> 0);
    public static final ThreadLocal<Boolean> CURRENT_LOD = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public static final class PartRestPose {
        public final ModelPart part = null;
        public final float rx = 0, ry = 0, rz = 0, px = 0, py = 0, pz = 0;
    }
    public String getName() { return null; }
    public ResourceLocation getTextureLocation() { return null; }
    public int getTextureWidth() { return 0; }
    public int getTextureHeight() { return 0; }
    public ModelPart getPart(String name) { return null; }
    public ModelPart getRootPart() { return null; }
    public Vector3f getRestRotation(String name) { return null; }
    public Vector3f getRestPosition(String name) { return null; }
    public BbAnimation findAnimation(String name) { return null; }
    public void renderActor(LivingEntity e, float s, float partial, PoseStack p, MultiBufferSource b, int light) {}
    public void settledlands$renderActorOriginal(LivingEntity e, float s, float partial, PoseStack p, MultiBufferSource b, int light) {}
    public void settledlands$renderNpcOriginal(GuideNpc npc, float yaw, float partial, PoseStack p, MultiBufferSource b, int light, int lod) {}
    public IdentityHashMap<ModelPart, ModelPart> settledlands$partParents() { return null; }
    public Map<ModelPart, PartRestPose> settledlands$restPoseByPart() { return null; }
    public void settledlands$renderHeldItem(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack, boolean leftHand) {}
    public void setupAnimRaw(boolean a, boolean b, String c, String d, boolean e, int f, int g, float h, float i, float j, float k, float l, float m) {}
}''',
    'traben/entity_texture_features/utils/ETFEntity.java': '''package traben.entity_texture_features.utils;
public interface ETFEntity {}''',
    'traben/entity_model_features/utils/EMFEntity.java': '''package traben.entity_model_features.utils;
import traben.entity_texture_features.utils.ETFEntity;
public interface EMFEntity extends ETFEntity {}''',
    'traben/entity_texture_features/features/state/ETFEntityRenderState.java': '''package traben.entity_texture_features.features.state;
import traben.entity_texture_features.utils.ETFEntity;
public interface ETFEntityRenderState {
    static ETFEntityRenderState forEntity(ETFEntity entity) { return null; }
}''',
    'traben/entity_texture_features/features/state/ETFState.java': '''package traben.entity_texture_features.features.state;
public class ETFState {
    public static void mount(ETFEntityRenderState state) {}
    public static void unMount() {}
    public static ETFEntityRenderState state() { return null; }
}''',
    'traben/entity_model_features/models/animation/state/EMFEntityRenderState.java': '''package traben.entity_model_features.models.animation.state;
import java.util.UUID;
import traben.entity_model_features.utils.EMFEntity;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
public interface EMFEntityRenderState extends ETFEntityRenderState {
    EMFEntity emfEntity();
    boolean isFirstPersonHand();
    void setIsFirstPersonHand(boolean v);
    boolean skipModelVariate();
    UUID uuid();
    float limbAngle();
    void setLimbAngle(float v);
    float limbDistance();
    void setLimbDistance(float v);
    float headYaw();
    void setHeadYaw(float v);
    float headPitch();
    void setHeadPitch(float v);
}''',
    'traben/entity_model_features/models/animation/state/EMFState.java': '''package traben.entity_model_features.models.animation.state;
import java.util.*;
import traben.entity_model_features.EMFAnimationApi;
public abstract class EMFState {
    public static final List<EMFAnimationApi.EMFAnimationHook> animationHooks = new ArrayList<>();
    public static boolean isLayerPhase, isMainPhase, isInGui;
    public static EMFEntityRenderState state() { return null; }
    public static boolean isEntityForcedToVanillaModel(EMFEntityRenderState state) { return false; }
}''',
})


def main(dest: str) -> None:
    root = pathlib.Path(dest)
    for name, content in SRC.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content + '\n', encoding='utf8')
    print('wrote', len(SRC), '256 compile-only silhouettes')


if __name__ == '__main__':
    main(sys.argv[1])
