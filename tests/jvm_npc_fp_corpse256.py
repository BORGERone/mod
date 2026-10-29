#!/usr/bin/env python3
"""End-to-end Java 21 (-Xverify:all) harness for Build 256 (plus Build 255 bug reproduction).

Exercises the real bytecode of:
  - BbModel, BbModelParser, BbModelStorage, BbModelManager + bundled ivy_oleander.bbmodel & corpse_coffin_v2.bbmodel
  - NpcMotionBridge256, NpcFreshEmfAccess256, NpcMotionPlayerProxy256, WalkAnimationSyncAccess256
  - ClientLevelProxyLookupMixin256, WalkAnimationStateMixin256 (woven directly from JAR bytecode & verified under -Xverify:all)
  - PlayerBodyRenderer, PlayerFirstPersonBodyGate256, FirstPersonPossession256, FirstPersonArmSubscriber256
  - PossessedFirstPersonHandMixin256, PossessedFirstPersonModelMixin256 (woven directly from JAR bytecode & verified under -Xverify:all)
  - GuideNpc, GuideCorpseNpc, GuideTriggerNpc, CorpseRecovery, PossessionClientSync, NpcClient
"""
import argparse
import json
import os
import pathlib
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import build_joml248
import classedit
import jvm_auto_ai255 as ai255
import jvm_npc_copy253 as nc253
import jvm_ui_ivy254 as ui254
import patch_corpse_click244 as v244
import patch_npc_stats246 as prev
import smt


OVERRIDES_256 = {
    'com/mojang/authlib/GameProfile.java': '''
package com.mojang.authlib;
import java.util.UUID;
public class GameProfile {
    private final UUID id;
    private final String name;
    public GameProfile(UUID id, String name) { this.id = id; this.name = name; }
    public UUID getId() { return id; }
    public String getName() { return name; }
}
''',
    'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': '''
package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfo {
    private final String name;
    private final boolean cancellable;
    private boolean cancelled;
    public CallbackInfo(String name, boolean cancellable) {
        this.name = name;
        this.cancellable = cancellable;
    }
    public String getId() { return name; }
    public boolean isCancellable() { return cancellable; }
    public boolean isCancelled() { return cancelled; }
    public void cancel() {
        if (!cancellable) throw new IllegalStateException("Not cancellable");
        this.cancelled = true;
    }
}
''',
    'org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable.java': '''
package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfoReturnable<R> extends CallbackInfo {
    private R returnValue;
    public CallbackInfoReturnable(String name, boolean cancellable) {
        super(name, cancellable);
    }
    public void setReturnValue(R value) {
        cancel();
        this.returnValue = value;
    }
    public R getReturnValue() { return returnValue; }
}
''',
    'com/mojang/math/Axis.java': '''
package com.mojang.math;
import org.joml.Quaternionf;
public interface Axis {
    Axis XP = deg -> new Quaternionf().rotationX((float) Math.toRadians(deg));
    Axis YP = deg -> new Quaternionf().rotationY((float) Math.toRadians(deg));
    Axis ZP = deg -> new Quaternionf().rotationZ((float) Math.toRadians(deg));
    Quaternionf rotationDegrees(float degrees);
}
''',
    'com/mojang/blaze3d/vertex/VertexConsumer.java': '''
package com.mojang.blaze3d.vertex;
public interface VertexConsumer {
    default VertexConsumer setColor(int c) { return this; }
}
''',
    'com/mojang/blaze3d/vertex/PoseStack.java': '''
package com.mojang.blaze3d.vertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
public class PoseStack {
    public static final class Pose {
        private final Matrix4f mat = new Matrix4f();
        private final Matrix3f norm = new Matrix3f();
        public Pose() {}
        public Pose(Pose src) { mat.set(src.mat); norm.set(src.norm); }
        public Matrix4f pose() { return mat; }
        public Matrix3f normal() { return norm; }
    }
    private final Pose[] stack = new Pose[64];
    public int depth;
    private final double[] zStack = new double[64];
    public PoseStack() { stack[0] = new Pose(); }
    public double currentZ() { return zStack[depth]; }
    public void pushPose() {
        stack[depth + 1] = new Pose(stack[depth]);
        zStack[depth + 1] = zStack[depth];
        depth++;
    }
    public void popPose() {
        if (depth <= 0) throw new IllegalStateException("popPose underflow");
        depth--;
    }
    public void translate(float x, float y, float z) {
        stack[depth].mat.translate(x, y, z);
        zStack[depth] += z;
    }
    public void translate(double x, double y, double z) {
        translate((float) x, (float) y, (float) z);
    }
    public void scale(float x, float y, float z) {
        stack[depth].mat.scale(x, y, z);
    }
    public void mulPose(Quaternionf q) {
        if (q != null) stack[depth].mat.rotate(q);
    }
    public Pose last() { return stack[depth]; }
}
''',
    'net/minecraft/client/renderer/RenderType.java': '''
package net.minecraft.client.renderer;
import net.minecraft.resources.ResourceLocation;
public class RenderType {
    public final String kind;
    public final ResourceLocation texture;
    public RenderType(String kind, ResourceLocation texture) {
        this.kind = kind;
        this.texture = texture;
    }
    public static RenderType entityCutout(ResourceLocation loc) { return new RenderType("cutout", loc); }
    public static RenderType entityCutoutNoCull(ResourceLocation loc) { return new RenderType("cutout_nocull", loc); }
    public static RenderType entityTranslucent(ResourceLocation loc) { return new RenderType("translucent", loc); }
    public static RenderType entitySolid(ResourceLocation loc) { return new RenderType("solid", loc); }
}
''',
    'net/minecraft/client/renderer/MultiBufferSource.java': '''
package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);

    final class Recording implements MultiBufferSource {
        public final List<RenderType> requested = new ArrayList<>();
        public static final class TaggedConsumer implements VertexConsumer {
            public final RenderType renderType;
            public TaggedConsumer(RenderType rt) { this.renderType = rt; }
        }
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            requested.add(type);
            return new TaggedConsumer(type);
        }
    }
}
''',
    'net/minecraft/client/renderer/texture/OverlayTexture.java': '''
package net.minecraft.client.renderer.texture;
public class OverlayTexture {
    public static final int NO_OVERLAY = 655360;
}
''',
    'net/minecraft/world/phys/Vec3.java': '''
package net.minecraft.world.phys;
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public final double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public double lengthSqr() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSqr()); }
    public double horizontalDistanceSqr() { return x * x + z * z; }
    public double distanceToSqr(Vec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
    public Vec3 normalize() {
        double len = length();
        return len < 1.0E-6 ? ZERO : new Vec3(x / len, y / len, z / len);
    }
    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
    public Vec3 yRot(float rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vec3(x * c + z * s, y, z * c - x * s);
    }
}
''',
    'net/minecraft/world/item/UseAnim.java': '''
package net.minecraft.world.item;
public enum UseAnim {
    NONE, EAT, DRINK, BLOCK, BOW, SPEAR, CROSSBOW, SPYGLASS, TOOT_HORN, BRUSH
}
''',
    'net/minecraft/world/item/ItemDisplayContext.java': '''
package net.minecraft.world.item;
public enum ItemDisplayContext {
    NONE, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND
}
''',
    'net/minecraft/client/renderer/ItemInHandRenderer.java': '''
package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
public class ItemInHandRenderer {
    public static final List<String> CALLS = new ArrayList<>();
    public void renderItem(LivingEntity entity, ItemStack item, ItemDisplayContext ctx,
                           boolean left, PoseStack stack, MultiBufferSource buffers, int light) {
        if (item != null && !item.isEmpty()) {
            CALLS.add((left ? "L:" : "R:") + ctx.name());
        }
    }
}
''',
    'net/minecraft/world/entity/EquipmentSlot.java': '''
package net.minecraft.world.entity;
public enum EquipmentSlot {
    MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY
}
''',
    'net/minecraft/util/Mth.java': '''
package net.minecraft.util;
public final class Mth {
    public static double atan2(double y, double x) { return Math.atan2(y, x); }
    public static float wrapDegrees(float deg) {
        float d = deg % 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }
    public static float sin(float v) { return (float) Math.sin(v); }
    public static float cos(float v) { return (float) Math.cos(v); }
    public static float sqrt(float v) { return (float) Math.sqrt(v); }
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
''',
    'net/minecraft/world/entity/HumanoidArm.java': '''
package net.minecraft.world.entity;
public enum HumanoidArm { LEFT, RIGHT }
''',
    'net/minecraft/world/entity/Pose.java': '''
package net.minecraft.world.entity;
public enum Pose { STANDING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK, CROUCHING, LONG_JUMPING, DYING }
''',
    'net/minecraft/world/entity/WalkAnimationState.java': '''
package net.minecraft.world.entity;
import net.minecraft.util.Mth;
public class WalkAnimationState {
    private float speedOld;
    private float speed;
    private float position;
    public void setSpeed(float s) { this.speed = s; }
    public void update(float target, float factor) {
        this.speedOld = this.speed;
        this.speed += (target - this.speed) * factor;
        if (this.speed > 1.0f) this.speed = 1.0f;
        this.position += this.speed;
    }
    public void setTestWalk(float pos, float spd, float spdOld) {
        this.position = pos;
        this.speed = spd;
        this.speedOld = spdOld;
    }
    public void settledlands$copyWalkStateFrom(WalkAnimationState src) {}
    public float speed() { return this.speed; }
    public float speed(float pt) { return Mth.lerp(pt, this.speedOld, this.speed); }
    public float position() { return this.position; }
    public float position(float pt) { return this.position - this.speed * (1.0f - pt); }
}
''',
    'net/minecraft/world/entity/Entity.java': '''
package net.minecraft.world.entity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import traben.entity_model_features.utils.EMFEntity;
public class Entity implements IEntityExtension, EMFEntity {
    private static int NEXT_ID = 100;
    private final EntityType<?> type;
    protected Level level;
    private UUID uuid = UUID.randomUUID();
    private int id = NEXT_ID++;
    public int tickCount = 0;
    public boolean alive = true;
    public boolean removed = false;
    public boolean passenger = false;
    public boolean spectator = false;
    public boolean invisible = false;
    public boolean onGroundFlag = true;
    public boolean crouchingFlag = false;
    public boolean sprintingFlag = false;
    public boolean swimmingFlag = false;
    public boolean inWaterFlag = false;
    public boolean noGravity = false;
    public boolean hurtMarked = false;
    public boolean horizontalCollision = false;
    public boolean verticalCollision = false;
    public float fallDistance = 0f;
    public double xo = 0.5, yo = 64.0, zo = 0.5;
    public double xOld = 0.5, yOld = 64.0, zOld = 0.5;
    public float yRotO = 0f, xRotO = 0f;
    private double x = 0.5, y = 64.0, z = 0.5;
    private float yRot = 0.0f, xRot = 0.0f;
    private Pose pose = Pose.STANDING;
    private Entity vehicle;
    private Vec3 deltaMovement = Vec3.ZERO;
    private final DamageSources damageSources = new DamageSources();
    public final List<SoundEvent> playedSounds = new ArrayList<>();
    private Component customName;
    private boolean customNameVisible;
    protected final SynchedEntityData entityData = new SynchedEntityData();
    private final CompoundTag persistentData = new CompoundTag();

    public Entity(EntityType<?> type, Level level) {
        this.type = type;
        this.level = level;
        defineSynchedData(new SynchedEntityData.Builder(this.entityData));
    }
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    public EntityType<?> getType() { return type; }
    public Level level() { return level; }
    public UUID getUUID() { return uuid; }
    public void setUUID(UUID u) { this.uuid = u; }
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public boolean isAlive() { return alive && !removed; }
    public boolean isRemoved() { return removed; }
    public boolean isPassenger() { return passenger || vehicle != null; }
    public Entity getVehicle() { return vehicle; }
    public void setVehicle(Entity v) { this.vehicle = v; }
    public boolean shouldRiderSit() { return true; }
    public boolean isSpectator() { return spectator; }
    public boolean isInvisible() { return invisible; }
    public boolean onGround() { return onGroundFlag; }
    public void setOnGround(boolean v) { this.onGroundFlag = v; }
    public boolean isShiftKeyDown() { return crouchingFlag; }
    public boolean isCrouching() { return crouchingFlag; }
    public boolean isSprinting() { return sprintingFlag; }
    public boolean isSwimming() { return swimmingFlag; }
    public boolean isInWater() { return inWaterFlag; }
    public Pose getPose() { return pose; }
    public void setPose(Pose p) { this.pose = p == null ? Pose.STANDING : p; }
    public String getScoreboardName() { return "entity_" + id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setPosRaw(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public void setPos(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3 position() { return new Vec3(x, y, z); }
    public BlockPos blockPosition() { return BlockPos.containing(x, y, z); }
    public AABB getBoundingBox() { return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3); }
    public float getYRot() { return yRot; }
    public void setYRot(float r) { this.yRot = r; }
    public float getXRot() { return xRot; }
    public void setXRot(float r) { this.xRot = r; }
    public float getViewXRot(float pt) { return xRot; }
    public double distanceToSqr(Entity o) { return distanceToSqr(o.x, o.y, o.z); }
    public double distanceToSqr(double ox, double oy, double oz) {
        double dx = x - ox, dy = y - oy, dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
    public Vec3 getDeltaMovement() { return deltaMovement; }
    public void setDeltaMovement(double dx, double dy, double dz) { this.deltaMovement = new Vec3(dx, dy, dz); }
    public void setDeltaMovement(Vec3 v) { this.deltaMovement = v == null ? Vec3.ZERO : v; }
    public void push(double dx, double dy, double dz) {
        this.deltaMovement = this.deltaMovement.add(dx, dy, dz);
        this.x += dx;
        this.z += dz;
    }
    public void playSound(SoundEvent s, float vol, float pitch) { if (s != null) playedSounds.add(s); }
    public DamageSources damageSources() { return damageSources; }
    public void setNoGravity(boolean v) { this.noGravity = v; }
    public void setCustomName(Component c) { this.customName = c; }
    public Component getCustomName() { return customName; }
    public void setCustomNameVisible(boolean v) { this.customNameVisible = v; }
    public boolean isCustomNameVisible() { return customNameVisible; }
    public void refreshDimensions() {}
    @Override public CompoundTag getPersistentData() { return persistentData; }
    protected void readAdditionalSaveData(CompoundTag tag) {}
    protected void addAdditionalSaveData(CompoundTag tag) {}
    public CompoundTag saveWithoutId(CompoundTag tag) {
        try {
            addAdditionalSaveData(tag);
            return tag;
        } catch (Throwable t) {
            throw new net.minecraft.ReportedException("Saving entity NBT", t);
        }
    }
    public void load(CompoundTag tag) { readAdditionalSaveData(tag); }
}
''',
    'net/neoforged/neoforge/fluids/FluidType.java': '''
package net.neoforged.neoforge.fluids;
public class FluidType {}
''',
    'net/minecraft/world/entity/LivingEntity.java': '''
package net.minecraft.world.entity;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidType;
public class LivingEntity extends Entity {
    private final Map<Holder<Attribute>, AttributeInstance> attrs = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create(12345L);
    private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public float health = 20f;
    public float yBodyRot = 0f, yBodyRotO = 0f;
    public float yHeadRot = 0f, yHeadRotO = 0f;
    public float oAttackAnim = 0f, attackAnim = 0f;
    public int hurtTime = 0, hurtDuration = 10, deathTime = 0, swingTime = 0;
    public boolean swinging = false;
    public boolean sleeping = false;
    public boolean fallFlying = false;
    public boolean visuallySwimming = false;
    public float swimAmount = 0f;
    public InteractionHand swingingArm = InteractionHand.MAIN_HAND;
    public HumanoidArm mainArm = HumanoidArm.RIGHT;
    public float xxa = 0f, yya = 0f, zza = 0f;
    public boolean lineOfSight = true;
    public int swingCount = 0;
    public float totalDamageTaken = 0f;
    public DamageSource lastDamageSource;

    public LivingEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        attrs.put(Attributes.SCALE, new AttributeInstance(1.0));
        attrs.put(Attributes.MAX_HEALTH, new AttributeInstance(20.0));
        attrs.put(Attributes.ATTACK_DAMAGE, new AttributeInstance(4.0));
        attrs.put(Attributes.MOVEMENT_SPEED, new AttributeInstance(0.25));
    }
    public AttributeInstance getAttribute(Holder<Attribute> h) { return attrs.get(h); }
    public double getAttributeValue(Holder<Attribute> h) {
        AttributeInstance ai = attrs.get(h);
        return ai == null ? 0.0 : ai.getValue();
    }
    public float getScale() {
        AttributeInstance ai = attrs.get(Attributes.SCALE);
        return ai == null ? 1.0f : (float) ai.getValue();
    }
    public RandomSource getRandom() { return random; }
    public boolean hasLineOfSight(Entity target) { return lineOfSight; }
    public void setHealth(float h) {
        this.health = h;
        if (h <= 0f) this.alive = false;
    }
    public float getHealth() { return health; }
    public float getAttackAnim(float pt) { return attackAnim; }
    public boolean isBaby() { return false; }
    public boolean isSleeping() { return sleeping; }
    public boolean isFallFlying() { return fallFlying; }
    public boolean isVisuallySwimming() { return visuallySwimming; }
    public float getSwimAmount(float pt) { return swimAmount; }
    public boolean isInFluidType(BiPredicate<FluidType, Double> p) { return false; }
    public boolean canSwimInFluidType(FluidType f) { return true; }
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return equipment.getOrDefault(slot, ItemStack.EMPTY);
    }
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }
    public ItemStack getMainHandItem() { return getItemBySlot(EquipmentSlot.MAINHAND); }
    public ItemStack getOffhandItem() { return getItemBySlot(EquipmentSlot.OFFHAND); }
    public HumanoidArm getMainArm() { return mainArm; }
    public boolean isUsingItem() { return false; }
    public boolean isBlocking() { return false; }
    public boolean onClimbable() { return false; }
    public net.minecraft.world.phys.Vec3 getViewVector(float pt) {
        double rad = Math.toRadians(getYRot());
        return new net.minecraft.world.phys.Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }
    public ItemStack getUseItem() { return ItemStack.EMPTY; }
    public int getUseItemRemainingTicks() { return 0; }
    public InteractionHand getUsedItemHand() { return InteractionHand.MAIN_HAND; }
    public boolean hurt(DamageSource source, float amount) {
        if (!isAlive() || amount <= 0f) return false;
        this.lastDamageSource = source;
        this.totalDamageTaken += amount;
        setHealth(this.health - amount);
        return true;
    }
    public void swing(InteractionHand hand) { swingCount++; }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
''',
    'net/minecraft/client/resources/PlayerSkin.java': '''
package net.minecraft.client.resources;
public record PlayerSkin(Model model) {
    public enum Model { SLIM, WIDE }
}
''',
    'net/minecraft/client/player/AbstractClientPlayer.java': '''
package net.minecraft.client.player;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class AbstractClientPlayer extends Player {
    public PlayerSkin skin = new PlayerSkin(PlayerSkin.Model.SLIM);
    public AbstractClientPlayer(Level level) { super(level); }
    public AbstractClientPlayer(ClientLevel level, GameProfile profile) {
        super(level);
        if (profile != null && profile.getId() != null) {
            setUUID(profile.getId());
        }
    }
    public PlayerSkin getSkin() { return skin; }
}
''',
    'net/minecraft/client/player/LocalPlayer.java': '''
package net.minecraft.client.player;
import net.minecraft.world.level.Level;
public class LocalPlayer extends AbstractClientPlayer {
    public LocalPlayer(Level level) { super(level); }
}
''',
    'net/minecraft/client/player/RemotePlayer.java': '''
package net.minecraft.client.player;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
public class RemotePlayer extends AbstractClientPlayer {
    public RemotePlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }
}
''',
    'net/minecraft/client/CameraType.java': '''
package net.minecraft.client;
public enum CameraType {
    FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT;
    public boolean isFirstPerson() { return this == FIRST_PERSON; }
}
''',
    'net/minecraft/client/Options.java': '''
package net.minecraft.client;
public class Options {
    public CameraType cameraType = CameraType.FIRST_PERSON;
    public CameraType getCameraType() { return cameraType; }
}
''',
    'net/minecraft/client/model/geom/ModelLayerLocation.java': '''
package net.minecraft.client.model.geom;
public record ModelLayerLocation(String name) {}
''',
    'net/minecraft/client/model/geom/ModelLayers.java': '''
package net.minecraft.client.model.geom;
public class ModelLayers {
    public static final ModelLayerLocation PLAYER = new ModelLayerLocation("player");
    public static final ModelLayerLocation PLAYER_SLIM = new ModelLayerLocation("player_slim");
}
''',
    'net/minecraft/client/model/geom/ModelPart.java': '''
package net.minecraft.client.model.geom;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.stream.Stream;
public class ModelPart {
    public static class Cube {
        public Cube() {}
        public Cube(int u, int v, float x, float y, float z, float dx, float dy, float dz,
                    float gx, float gy, float gz, boolean mirror, float tw, float th, Set<?> faces) {}
        public void compile(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {}
    }
    public static final class RenderRecord {
        public final ModelPart part;
        public final float x, y, z, xRot, yRot, zRot;
        public final int light, overlay, color;
        public final VertexConsumer consumer;
        public RenderRecord(ModelPart part, VertexConsumer consumer, int light, int overlay, int color) {
            this.part = part;
            this.x = part.x; this.y = part.y; this.z = part.z;
            this.xRot = part.xRot; this.yRot = part.yRot; this.zRot = part.zRot;
            this.consumer = consumer;
            this.light = light; this.overlay = overlay; this.color = color;
        }
    }
    public static final List<RenderRecord> RENDERS = new ArrayList<>();
    private final List<?> cubes;
    public final Map<String, ModelPart> children;
    public float x, y, z, xRot, yRot, zRot;
    public float xScale = 1f, yScale = 1f, zScale = 1f;
    public boolean visible = true, skipDraw = false;
    public PartPose initialPose = new PartPose(0, 0, 0, 0, 0, 0);

    public ModelPart(List<?> cubes, Map<String, ModelPart> children) {
        this.cubes = cubes == null ? List.of() : cubes;
        this.children = children == null ? Map.of() : children;
    }
    public void setInitialPose(PartPose p) {
        this.initialPose = p;
        resetPose();
    }
    public PartPose getInitialPose() { return initialPose; }
    public void loadPose(PartPose p) {
        this.x = p.x; this.y = p.y; this.z = p.z;
        this.xRot = p.xRot; this.yRot = p.yRot; this.zRot = p.zRot;
        this.xScale = 1f; this.yScale = 1f; this.zScale = 1f;
    }
    public void resetPose() { loadPose(this.initialPose); }
    public void setPos(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public void setRotation(float xr, float yr, float zr) { this.xRot = xr; this.yRot = yr; this.zRot = zr; }
    public void translateAndRotate(PoseStack poseStack) {
        poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        if (this.xRot != 0.0F || this.yRot != 0.0F || this.zRot != 0.0F) {
            poseStack.mulPose(new org.joml.Quaternionf().rotationZYX(this.zRot, this.yRot, this.xRot));
        }
        if (this.xScale != 1.0F || this.yScale != 1.0F || this.zScale != 1.0F) {
            poseStack.scale(this.xScale, this.yScale, this.zScale);
        }
    }
    public ModelPart getChild(String name) {
        ModelPart c = children.get(name);
        if (c == null) throw new NoSuchElementException("Missing child " + name);
        return c;
    }
    public Stream<ModelPart> getAllParts() {
        return Stream.concat(Stream.of(this), children.values().stream().flatMap(ModelPart::getAllParts));
    }
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {
        if (!visible) return;
        RENDERS.add(new RenderRecord(this, consumer, packedLight, packedOverlay, color));
    }
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        render(poseStack, consumer, packedLight, packedOverlay, -1);
    }
}
''',
    'net/minecraft/client/model/Model.java': '''
package net.minecraft.client.model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
public abstract class Model {
    public abstract void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color);
}
''',
    'net/minecraft/client/model/EntityModel.java': '''
package net.minecraft.client.model;
import net.minecraft.world.entity.Entity;
public abstract class EntityModel<T extends Entity> extends Model {
    public boolean riding, young;
    public float attackTime;
    public void prepareMobModel(T e, float a, float b, float c) {}
    public abstract void setupAnim(T e, float a, float b, float c, float d, float f);
}
''',
    'net/minecraft/client/model/AgeableListModel.java': '''
package net.minecraft.client.model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
public abstract class AgeableListModel<E extends Entity> extends EntityModel<E> {
    public static int STEVE_RENDER_TO_BUFFER_COUNT = 0;

    protected void settledlands$mixinHookRenderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                                                        int packedLight, int packedOverlay, int color,
                                                        CallbackInfo ci) {}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        CallbackInfo ci = new CallbackInfo("renderToBuffer", true);
        settledlands$mixinHookRenderToBuffer(poseStack, buffer, packedLight, packedOverlay, color, ci);
        if (ci.isCancelled()) {
            return;
        }
        STEVE_RENDER_TO_BUFFER_COUNT++;
    }
}
''',
    'net/minecraft/client/model/HumanoidModel.java': '''
package net.minecraft.client.model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
public class HumanoidModel<T extends LivingEntity> extends AgeableListModel<T> {
    public ModelPart body, head, hat, rightArm, leftArm, rightLeg, leftLeg;
    public boolean crouching;
    public ArmPose rightArmPose = ArmPose.EMPTY, leftArmPose = ArmPose.EMPTY;
    public enum ArmPose { EMPTY, ITEM }
    @Override public void prepareMobModel(T e, float a, float b, float c) {}
    @Override public void setupAnim(T e, float a, float b, float c, float d, float f) {}
}
''',
    'net/minecraft/client/model/PlayerModel.java': '''
package net.minecraft.client.model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
public class PlayerModel<T extends LivingEntity> extends HumanoidModel<T> implements IEMFModel {
    private final EMFModelPartRoot emfRoot;
    public ModelPart leftSleeve, rightSleeve, leftPants, rightPants, jacket;

    public PlayerModel(ModelPart root, boolean slim) {
        this.emfRoot = root instanceof EMFModelPartRoot r ? r : null;
        this.head = root.getChild("head");
        this.hat = root.getChild("hat");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static ModelPart createPlayerRoot() {
        ModelPart head = part(0, 0, 0);
        ModelPart hat = part(0, 0, 0);
        ModelPart body = part(0, 0, 0);
        ModelPart rArm = part(-5, 2, 0);
        ModelPart lArm = part(5, 2, 0);
        ModelPart rLeg = part(-1.9f, 12, 0);
        ModelPart lLeg = part(1.9f, 12, 0);
        java.util.Map<String, ModelPart> children = new java.util.LinkedHashMap<>();
        children.put("head", head);
        children.put("hat", hat);
        children.put("body", body);
        children.put("right_arm", rArm);
        children.put("left_arm", lArm);
        children.put("right_leg", rLeg);
        children.put("left_leg", lLeg);
        return new EMFModelPartRoot(children);
    }

    private static ModelPart part(float x, float y, float z) {
        traben.entity_model_features.models.parts.EMFModelPart p =
            new traben.entity_model_features.models.parts.EMFModelPart(java.util.Map.of());
        p.setInitialPose(new PartPose(x, y, z, 0, 0, 0));
        return p;
    }

    @Override public boolean emf$isEMFModel() { return emfRoot != null; }
    @Override public EMFModelPartRoot emf$getEMFRootModel() { return emfRoot; }
}
''',
    'net/minecraft/client/model/geom/EntityModelSet.java': '''
package net.minecraft.client.model.geom;
import net.minecraft.client.model.PlayerModel;
public class EntityModelSet {
    public ModelPart bakeLayer(ModelLayerLocation loc) {
        return PlayerModel.createPlayerRoot();
    }
}
''',
    'net/minecraft/client/renderer/entity/EntityRenderer.java': '''
package net.minecraft.client.renderer.entity;
import net.minecraft.world.entity.Entity;
public class EntityRenderer<T extends Entity> {}
''',
    'net/minecraft/client/renderer/entity/LivingEntityRenderer.java': '''
package net.minecraft.client.renderer.entity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;
public class LivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {
    protected M model;
    public M getModel() { return model; }
}
''',
    'net/minecraft/client/renderer/entity/player/PlayerRenderer.java': '''
package net.minecraft.client.renderer.entity.player;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
public class PlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static int STEVE_RIGHT_HAND_COUNT = 0;
    public static int STEVE_LEFT_HAND_COUNT = 0;

    public PlayerRenderer() {
        this.model = new PlayerModel<>(PlayerModel.createPlayerRoot(), true);
    }

    protected void settledlands$mixinHookHeadRender(AbstractClientPlayer player, float yaw, float pt,
                                                    PoseStack poseStack, MultiBufferSource buffer,
                                                    int light, CallbackInfo ci) {}

    protected void settledlands$mixinHookReturnRender(AbstractClientPlayer player, float yaw, float pt,
                                                      PoseStack poseStack, MultiBufferSource buffer,
                                                      int light, CallbackInfo ci) {}

    protected void settledlands$mixinHookRightHand(PoseStack poseStack, MultiBufferSource buffer,
                                                   int light, AbstractClientPlayer player, CallbackInfo ci) {}

    protected void settledlands$mixinHookLeftHand(PoseStack poseStack, MultiBufferSource buffer,
                                                  int light, AbstractClientPlayer player, CallbackInfo ci) {}

    public void render(AbstractClientPlayer player, float yaw, float pt,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        CallbackInfo headCi = new CallbackInfo("render", false);
        settledlands$mixinHookHeadRender(player, yaw, pt, poseStack, buffer, light, headCi);
        try {
            VertexConsumer vc = buffer != null
                ? buffer.getBuffer(RenderType.entitySolid(ResourceLocation.fromNamespaceAndPath("minecraft", "steve")))
                : null;
            this.model.renderToBuffer(poseStack, vc, light, 655360, -1);
        } finally {
            CallbackInfo retCi = new CallbackInfo("render", false);
            settledlands$mixinHookReturnRender(player, yaw, pt, poseStack, buffer, light, retCi);
        }
    }

    public void renderRightHand(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player) {
        CallbackInfo ci = new CallbackInfo("renderRightHand", true);
        settledlands$mixinHookRightHand(poseStack, buffer, light, player, ci);
        if (ci.isCancelled()) return;
        STEVE_RIGHT_HAND_COUNT++;
    }

    public void renderLeftHand(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player) {
        CallbackInfo ci = new CallbackInfo("renderLeftHand", true);
        settledlands$mixinHookLeftHand(poseStack, buffer, light, player, ci);
        if (ci.isCancelled()) return;
        STEVE_LEFT_HAND_COUNT++;
    }
}
''',
    'net/minecraft/client/renderer/entity/EntityRenderDispatcher.java': '''
package net.minecraft.client.renderer.entity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
public class EntityRenderDispatcher {
    public final PlayerRenderer playerRenderer = new PlayerRenderer();
    public final ItemInHandRenderer itemInHandRenderer = new ItemInHandRenderer();
    public boolean renderShadow = true;
    public boolean shouldRenderShadow() { return renderShadow; }
    public ItemInHandRenderer getItemInHandRenderer() { return itemInHandRenderer; }
    @SuppressWarnings("unchecked")
    public <T extends Entity> EntityRenderer<? super T> getRenderer(T entity) {
        if (entity instanceof AbstractClientPlayer) {
            return (EntityRenderer<? super T>) playerRenderer;
        }
        return new EntityRenderer<>();
    }
}
''',
    'net/minecraft/world/entity/player/Inventory.java': '''
package net.minecraft.world.entity.player;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
public class Inventory {
    public int selected = 0;
    public boolean full = false;
    public final List<ItemStack> items = new ArrayList<>();
    public boolean add(ItemStack s) {
        if (full) return false;
        items.add(s);
        return true;
    }
    public ListTag save(ListTag list) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                CompoundTag slot = (CompoundTag) s.save(RegistryAccess.EMPTY);
                slot.putByte("Slot", (byte) i);
                list.add(slot);
            }
        }
        return list;
    }
}
''',
    'strm/touchnmotion/compat/ParCoolActivity.java': '''
package strm.touchnmotion.compat;
import net.minecraft.client.player.AbstractClientPlayer;
public class ParCoolActivity {
    public static boolean active(AbstractClientPlayer p) { return false; }
}
''',
    'strm/touchnmotion/compat/WholeBody.java': '''
package strm.touchnmotion.compat;
import net.minecraft.client.player.AbstractClientPlayer;
public class WholeBody {
    public static boolean held(AbstractClientPlayer p) { return false; }
}
''',
    'net/minecraft/world/entity/player/Player.java': '''
package net.minecraft.world.entity.player;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
public class Player extends LivingEntity {
    protected final Inventory inventory = new Inventory();
    public AbstractContainerMenu containerMenu = new AbstractContainerMenu();
    public Player(Level level) { super(null, level); }
    public Inventory getInventory() { return inventory; }
    public float getAttackStrengthScale(float adjust) { return 1.0f; }
}
''',
    'net/minecraft/client/Minecraft.java': '''
package net.minecraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.HitResult;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public LocalPlayer player;
    public ClientLevel level;
    public HitResult hitResult;
    public Screen screen;
    public Options options = new Options();
    public Font font = new Font();
    public final TextureManager textureManager = new TextureManager();
    public final EntityRenderDispatcher dispatcher = new EntityRenderDispatcher();
    public final EntityModelSet entityModels = new EntityModelSet();
    public final ItemInHandRenderer itemInHandRenderer = new ItemInHandRenderer();

    public static Minecraft getInstance() { return INSTANCE; }
    public void setScreen(Screen s) { this.screen = s; }
    public TextureManager getTextureManager() { return textureManager; }
    public EntityRenderDispatcher getEntityRenderDispatcher() { return dispatcher; }
    public EntityModelSet getEntityModels() { return entityModels; }
    public ItemInHandRenderer getItemInHandRenderer() { return itemInHandRenderer; }
    public com.mojang.blaze3d.platform.Window getWindow() { return null; }
}
''',
    'net/minecraft/client/multiplayer/ClientLevel.java': '''
package net.minecraft.client.multiplayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
public class ClientLevel extends Level {
    public ClientLevel() { this.isClientSide = true; }
    public Iterable<Entity> entitiesForRendering() { return byId.values(); }
    @Override public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }

    protected void settledlands$mixinHookGetEntity(int id, CallbackInfoReturnable<Entity> cir) {}

    @Override
    public Entity getEntity(int id) {
        CallbackInfoReturnable<Entity> cir = new CallbackInfoReturnable<>("getEntity", true);
        settledlands$mixinHookGetEntity(id, cir);
        if (cir.isCancelled()) {
            return cir.getReturnValue();
        }
        return super.getEntity(id);
    }

    public void addClientEntity(Entity e) {
        byId.put(e.getId(), e);
        byUuid.put(e.getUUID(), e);
    }
}
''',
    'net/neoforged/neoforge/client/event/RenderPlayerEvent.java': '''
package net.neoforged.neoforge.client.event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
public abstract class RenderPlayerEvent {
    private final Player entity;
    private final PlayerRenderer renderer;
    private final float partialTick;
    private final PoseStack poseStack;
    private final MultiBufferSource buffer;
    private final int packedLight;

    public RenderPlayerEvent(Player entity, PlayerRenderer renderer, float partialTick,
                             PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.entity = entity;
        this.renderer = renderer;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.packedLight = packedLight;
    }
    public Player getEntity() { return entity; }
    public PlayerRenderer getRenderer() { return renderer; }
    public float getPartialTick() { return partialTick; }
    public PoseStack getPoseStack() { return poseStack; }
    public MultiBufferSource getMultiBufferSource() { return buffer; }
    public int getPackedLight() { return packedLight; }

    public static class Pre extends RenderPlayerEvent {
        private boolean canceled;
        public Pre(Player entity, PlayerRenderer renderer, float partialTick,
                   PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            super(entity, renderer, partialTick, poseStack, buffer, packedLight);
        }
        @Override public Player getEntity() { return super.getEntity(); }
        @Override public PlayerRenderer getRenderer() { return super.getRenderer(); }
        @Override public float getPartialTick() { return super.getPartialTick(); }
        @Override public PoseStack getPoseStack() { return super.getPoseStack(); }
        @Override public MultiBufferSource getMultiBufferSource() { return super.getMultiBufferSource(); }
        @Override public int getPackedLight() { return super.getPackedLight(); }
        public void setCanceled(boolean c) { this.canceled = c; }
        public boolean isCanceled() { return canceled; }
    }
}
''',
    'net/neoforged/neoforge/client/event/RenderArmEvent.java': '''
package net.neoforged.neoforge.client.event;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
public class RenderArmEvent {
    private final PoseStack poseStack;
    private final MultiBufferSource buffer;
    private final int packedLight;
    private final AbstractClientPlayer player;
    private final HumanoidArm arm;
    private boolean canceled;

    public RenderArmEvent(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                          AbstractClientPlayer player, HumanoidArm arm) {
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.packedLight = packedLight;
        this.player = player;
        this.arm = arm;
    }
    public PoseStack getPoseStack() { return poseStack; }
    public MultiBufferSource getMultiBufferSource() { return buffer; }
    public int getPackedLight() { return packedLight; }
    public AbstractClientPlayer getPlayer() { return player; }
    public HumanoidArm getArm() { return arm; }
    public void setCanceled(boolean c) { this.canceled = c; }
    public boolean isCanceled() { return canceled; }
}
''',
    'org/apache/maven/artifact/versioning/ArtifactVersion.java': '''
package org.apache.maven.artifact.versioning;
public class ArtifactVersion {
    private final String v;
    public ArtifactVersion(String v) { this.v = v; }
    @Override public String toString() { return v; }
}
''',
    'net/neoforged/neoforgespi/language/IModInfo.java': '''
package net.neoforged.neoforgespi.language;
import org.apache.maven.artifact.versioning.ArtifactVersion;
public interface IModInfo {
    ArtifactVersion getVersion();
}
''',
    'net/neoforged/fml/ModContainer.java': '''
package net.neoforged.fml;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
public class ModContainer {
    private final IModInfo info;
    public ModContainer(String ver) {
        ArtifactVersion av = new ArtifactVersion(ver);
        this.info = () -> av;
    }
    public IModInfo getModInfo() { return info; }
}
''',
    'net/neoforged/fml/ModList.java': '''
package net.neoforged.fml;
import java.util.*;
public class ModList {
    public static final Map<String, String> MODS = new LinkedHashMap<>();
    private static final ModList INSTANCE = new ModList();
    public static boolean RETURN_NULL = false;
    public static ModList get() { return RETURN_NULL ? null : INSTANCE; }
    public boolean isLoaded(String id) { return MODS.containsKey(id); }
    public Optional<ModContainer> getModContainerById(String id) {
        String v = MODS.get(id);
        return v == null ? Optional.empty() : Optional.of(new ModContainer(v));
    }
}
''',
    'com/zigythebird/playeranimcore/api/firstPerson/FirstPersonMode.java': '''
package com.zigythebird.playeranimcore.api.firstPerson;
public enum FirstPersonMode {
    NONE, VANILLA, THIRD_PERSON_MODEL, DISABLED;
    private static boolean fpPass = false;
    public static boolean isFirstPersonPass() { return fpPass; }
    public static void setFirstPersonPass(boolean v) { fpPass = v; }
}
''',
    'traben/entity_texture_features/utils/ETFEntity.java': '''
package traben.entity_texture_features.utils;
public interface ETFEntity {}
''',
    'traben/entity_model_features/utils/EMFEntity.java': '''
package traben.entity_model_features.utils;
import traben.entity_texture_features.utils.ETFEntity;
public interface EMFEntity extends ETFEntity {}
''',
    'traben/entity_texture_features/features/state/ETFEntityRenderState.java': '''
package traben.entity_texture_features.features.state;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.utils.EMFEntity;
import traben.entity_texture_features.utils.ETFEntity;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
public interface ETFEntityRenderState {
    static ETFEntityRenderState forEntity(ETFEntity entity) {
        if (!(entity instanceof EMFEntity emf)) return null;
        UUID id = entity instanceof Entity e ? e.getUUID() : UUID.randomUUID();
        return new EMFEntityRenderState.Impl(emf, id);
    }
}
''',
    'traben/entity_texture_features/features/state/ETFState.java': '''
package traben.entity_texture_features.features.state;
import java.util.ArrayDeque;
import java.util.Deque;
public class ETFState {
    public static final Deque<ETFEntityRenderState> STACK = new ArrayDeque<>();
    public static void mount(ETFEntityRenderState state) {
        if (state != null && STACK.peek() == state) return;
        STACK.push(state);
    }
    public static void unMount() {
        if (!STACK.isEmpty()) STACK.pop();
    }
    public static ETFEntityRenderState state() {
        return STACK.peek();
    }
}
''',
    'traben/entity_texture_features/ETFApi.java': '''
package traben.entity_texture_features;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
public interface ETFApi {
    interface ETFVariantSuffixProvider {
        int getSuffixForETFEntity(ETFEntityRenderState state);
    }
}
''',
    'traben/entity_texture_features/utils/ETFLruCache.java': '''
package traben.entity_texture_features.utils;
import java.util.LinkedHashMap;
import java.util.UUID;
public class ETFLruCache<X, Y> extends LinkedHashMap<X, Y> {
    public static class UUIDInteger extends ETFLruCache<UUID, Integer> {}
}
''',
    'traben/entity_model_features/models/animation/state/EMFEntityRenderState.java': '''
package traben.entity_model_features.models.animation.state;
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

    final class Impl implements EMFEntityRenderState {
        private final EMFEntity entity;
        private final UUID uuid;
        private boolean fpHand;
        private float limbAngle, limbDistance, headYaw, headPitch;
        public Impl(EMFEntity entity, UUID uuid) { this.entity = entity; this.uuid = uuid; }
        @Override public EMFEntity emfEntity() { return entity; }
        @Override public boolean isFirstPersonHand() { return fpHand; }
        @Override public void setIsFirstPersonHand(boolean v) { this.fpHand = v; }
        @Override public boolean skipModelVariate() { return false; }
        @Override public UUID uuid() { return uuid; }
        @Override public float limbAngle() { return limbAngle; }
        @Override public void setLimbAngle(float v) { this.limbAngle = v; }
        @Override public float limbDistance() { return limbDistance; }
        @Override public void setLimbDistance(float v) { this.limbDistance = v; }
        @Override public float headYaw() { return headYaw; }
        @Override public void setHeadYaw(float v) { this.headYaw = v; }
        @Override public float headPitch() { return headPitch; }
        @Override public void setHeadPitch(float v) { this.headPitch = v; }
    }
}
''',
    'traben/entity_model_features/models/animation/state/EMFState.java': '''
package traben.entity_model_features.models.animation.state;
import java.util.*;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;
public abstract class EMFState {
    public static final List<EMFAnimationApi.EMFAnimationHook> animationHooks = new ArrayList<>();
    public static boolean isLayerPhase, isMainPhase, isInGui;
    public static EMFEntityRenderState state() {
        ETFEntityRenderState s = ETFState.state();
        return s instanceof EMFEntityRenderState emf ? emf : null;
    }
    public static boolean isEntityForcedToVanillaModel(EMFEntityRenderState state) { return false; }
}
''',
    'traben/entity_model_features/EMFManager.java': '''
package traben.entity_model_features;
public class EMFManager {
    private static final EMFManager INSTANCE = new EMFManager();
    public long entityRenderCount;
    public static EMFManager getInstance() { return INSTANCE; }
}
''',
    'traben/entity_model_features/models/EMFModel_ID.java': '''
package traben.entity_model_features.models;
import java.util.Objects;
public class EMFModel_ID {
    public final String name;
    public EMFModel_ID(String name) { this.name = name; }
    @Override public boolean equals(Object o) { return o instanceof EMFModel_ID m && Objects.equals(name, m.name); }
    @Override public int hashCode() { return Objects.hashCode(name); }
}
''',
    'traben/entity_model_features/models/IEMFModel.java': '''
package traben.entity_model_features.models;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
public interface IEMFModel {
    boolean emf$isEMFModel();
    EMFModelPartRoot emf$getEMFRootModel();
}
''',
    'traben/entity_model_features/models/animation/EMFAnimationHandler.java': '''
package traben.entity_model_features.models.animation;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.models.parts.EMFModelPart;
public class EMFAnimationHandler {
    public static boolean FA_ACTIVE = true;
    private final Map<String, ModelPart> parts;
    public EMFAnimationHandler(Map<String, ModelPart> parts) { this.parts = parts; }
    private EMFModelPart emfPart(String k) {
        ModelPart p = parts.get(k);
        return p instanceof EMFModelPart ep ? ep : null;
    }
    public AnimLineData getLine(String key) {
        if (!FA_ACTIVE) return null;
        if ("var.Created_by_FreshLX_for_Fresh_Animations".equals(key)) {
            return new AnimLineData(null, "1");
        }
        if ("var.mvmnt_rlegrx".equals(key) || "var.mvmnt_llegrx".equals(key)) {
            return new AnimLineData(null, "cos(limb_swing)*limb_speed*(var.walk+var.run)");
        }
        if ("right_leg.rx".equals(key)) {
            return new AnimLineData(emfPart("right_leg"), "var.mvmnt_rlegrx");
        }
        if ("left_leg.rx".equals(key)) {
            return new AnimLineData(emfPart("left_leg"), "var.mvmnt_llegrx");
        }
        int dot = key.indexOf('.');
        if (dot > 0) {
            EMFModelPart p = emfPart(key.substring(0, dot));
            if (p != null) return new AnimLineData(p, "0");
        }
        return null;
    }
    public static final class AnimLineData {
        public final EMFModelPart partToApplyTo;
        public final String expression;
        public AnimLineData(EMFModelPart partToApplyTo, String expression) {
            this.partToApplyTo = partToApplyTo;
            this.expression = expression;
        }
    }
}
''',
    'net/minecraft/world/item/ItemStack.java': '''
package net.minecraft.world.item;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack((Item) null, 0);
    private final Item item;
    private int count;
    private final Map<DataComponentType<?>, Object> components = new IdentityHashMap<>();
    public ItemStack(ItemLike like) { this(like == null ? null : like.asItem(), 1); }
    public ItemStack(ItemLike like, int count) { this.item = like == null ? null : like.asItem(); this.count = count; }
    public Item getItem() { return item; }
    public boolean is(Item other) { return item != null && item == other; }
    public boolean isEmpty() { return item == null || count <= 0; }
    public int getCount() { return count; }
    public String getDescriptionId() { return "item.minecraft.iron_sword"; }
    public UseAnim getUseAnimation() { return UseAnim.NONE; }
    @SuppressWarnings("unchecked")
    public <T> T set(DataComponentType<? super T> type, T val) {
        return (T) (val == null ? components.remove(type) : components.put(type, val));
    }
    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<? extends T> type) { return (T) components.get(type); }
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(DataComponentType<? extends T> type, T def) {
        Object v = components.get(type);
        return v == null ? def : (T) v;
    }
    public ItemStack copy() {
        if (isEmpty()) return EMPTY;
        ItemStack out = new ItemStack(item, count);
        out.components.putAll(components);
        return out;
    }
    public Tag save(HolderLookup.Provider provider) {
        if (isEmpty()) throw new IllegalStateException("Cannot encode empty ItemStack");
        CompoundTag out = new CompoundTag();
        out.putInt("count", count);
        CustomData entityData = get(DataComponents.ENTITY_DATA);
        if (entityData != null) {
            CompoundTag encoded = entityData.encodeWithIdOrThrow();
            CompoundTag comps = new CompoundTag();
            comps.put("minecraft:entity_data", encoded);
            out.put("components", comps);
        }
        return out;
    }
    public static Optional<ItemStack> parse(HolderLookup.Provider p, Tag t) { return Optional.of(EMPTY); }
    public Tag saveOptional(HolderLookup.Provider p) { return isEmpty() ? new CompoundTag() : save(p); }
}
''',
    'traben/entity_model_features/EMFAnimationApi.java': '''
package traben.entity_model_features;
import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
public interface EMFAnimationApi {
    static boolean registerAnimationHook(EMFAnimationHook hook) throws Exception {
        if (!EMFState.animationHooks.contains(hook)) {
            EMFState.animationHooks.add(hook);
        }
        return true;
    }
    abstract class EMFAnimationHook {
        public boolean onAnimationStart(AnimationContext c, boolean cancelled) { return true; }
        public void onAnimationEnd(AnimationContext c, boolean cancelled) {}
        public record AnimationContext(
            EMFEntityRenderState activeState,
            EMFModelPartRoot animatingModelRoot,
            Throwable error,
            ModelPart[] partsRequestedToPauseThisAnimation,
            EMFAnimationHandler animationHandler
        ) {}
    }
}
''',
    'traben/entity_model_features/models/parts/EMFModelPart.java': '''
package traben.entity_model_features.models.parts;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
public class EMFModelPart extends ModelPart {
    public boolean isSetByAnimation;
    public EMFModelPart(Map<String, ModelPart> children) { super(List.of(), children); }
}
''',
    'traben/entity_model_features/models/parts/EMFModelPartRoot.java': '''
package traben.entity_model_features.models.parts;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.EMFModel_ID;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.utils.ETFLruCache;
public class EMFModelPartRoot extends EMFModelPart {
    public static int ANIMATE_COUNT = 0;
    public static float LAST_LIMB_ANGLE = 0f;
    public static float LAST_LIMB_DISTANCE = 0f;
    public static float LAST_HEAD_YAW = 0f;
    public static boolean LAST_PROXY_SPRINTING = false;
    public static boolean LAST_PROXY_CROUCHING = false;

    public boolean isMainModel;
    public EMFModel_ID modelName = new EMFModel_ID("player_slim");
    public ETFApi.ETFVariantSuffixProvider variantTester;
    public ETFApi.ETFVariantSuffixProvider unused;
    public ETFLruCache.UUIDInteger entitySuffixMap = new ETFLruCache.UUIDInteger();
    private final EMFAnimationHandler handler;

    public EMFModelPartRoot(Map<String, ModelPart> children) {
        super(children);
        this.handler = new EMFAnimationHandler(children);
    }
    public boolean hasAnimation() { return EMFAnimationHandler.FA_ACTIVE; }
    public void setVariantStateTo(int v) {}
    public void checkIfShouldExpireEntity(EMFEntityRenderState state, UUID id) {}
    public Map<String, ModelPart> getAllVanillaPartsByNameEMF() { return this.children; }

    public void animate() {
        EMFEntityRenderState state = EMFState.state();
        if (state == null) return;
        EMFAnimationApi.EMFAnimationHook.AnimationContext ctx =
            new EMFAnimationApi.EMFAnimationHook.AnimationContext(state, this, null, new ModelPart[0], handler);
        boolean cancelled = false;
        for (EMFAnimationApi.EMFAnimationHook h : List.copyOf(EMFState.animationHooks)) {
            if (!h.onAnimationStart(ctx, cancelled)) cancelled = true;
        }
        if (!cancelled) {
            ANIMATE_COUNT++;
            LAST_LIMB_ANGLE = state.limbAngle();
            LAST_LIMB_DISTANCE = state.limbDistance();
            LAST_HEAD_YAW = state.headYaw();
            if (state.emfEntity() instanceof LivingEntity le) {
                LAST_PROXY_SPRINTING = le.isSprinting();
                LAST_PROXY_CROUCHING = le.isCrouching();
            }
            float gait = (float) Math.cos(state.limbAngle()) * state.limbDistance() * 0.65f;
            float runBias = LAST_PROXY_SPRINTING ? 0.25f : 0.0f;
            children.get("right_leg").xRot = gait + runBias;
            children.get("left_leg").xRot = -gait - runBias;
            children.get("right_arm").xRot = -gait * 0.8f;
            children.get("left_arm").xRot = gait * 0.8f;
            children.get("body").yRot = gait * 0.15f;
            children.get("head").yRot = (float) Math.toRadians(state.headYaw());
            children.get("head").xRot = (float) Math.toRadians(state.headPitch());
        }
        for (EMFAnimationApi.EMFAnimationHook h : List.copyOf(EMFState.animationHooks)) {
            h.onAnimationEnd(ctx, cancelled);
        }
    }
}
''',
    'strm/touchnmotion/interaction/DrawnEntities.java': '''
package strm.touchnmotion.interaction;
import net.minecraft.world.entity.Entity;
public class DrawnEntities {
    public static boolean inWorld(Entity e) {
        return e != null && e.level() != null && e.level().getEntity(e.getId()) == e;
    }
}
''',
    'strm/touchnmotion/ModelSpace.java': '''
package strm.touchnmotion;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import strm.touchnmotion.interaction.DrawnEntities;
public class ModelSpace {
    public static int BEFORE_CALLS = 0;
    public static boolean LAST_IN_WORLD = false;
    public static float LAST_WALK_SPEED = 0f;
    public static float LAST_WALK_POS = 0f;

    public static void before(LivingEntity entity, PoseStack stack, float pt, Consumer<Object> cb) {
        if (!(entity instanceof AbstractClientPlayer)) return;
        boolean iw = DrawnEntities.inWorld(entity);
        LAST_IN_WORLD = iw;
        if (!iw) return;
        BEFORE_CALLS++;
        LAST_WALK_SPEED = entity.walkAnimation.speed(pt);
        LAST_WALK_POS = entity.walkAnimation.position(pt);
        stack.translate(0.0f, 0.0f, 0.02f);
    }
}
''',
    'strm/touchnmotion/TouchNMotionHook.java': '''
package strm.touchnmotion;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import strm.touchnmotion.interaction.DrawnEntities;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
public class TouchNMotionHook extends EMFAnimationApi.EMFAnimationHook {
    public static int HOOK_APPLIED_COUNT = 0;
    public static final float TNM_BODY_ZROT = 0.075f;

    @Override
    public void onAnimationEnd(AnimationContext ctx, boolean wasCancelled) {
        if (wasCancelled || ctx == null) return;
        EMFEntityRenderState st = ctx.activeState();
        if (st == null || st.isFirstPersonHand()) return;
        if (!(st.emfEntity() instanceof Entity e) || !DrawnEntities.inWorld(e)) return;
        HOOK_APPLIED_COUNT++;
        ModelPart body = ctx.animatingModelRoot().getAllVanillaPartsByNameEMF().get("body");
        if (body != null) {
            body.zRot += TNM_BODY_ZROT;
        }
    }
}
''',
    'testing/NpcFpCorpse256Scenario.java': '''
package testing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import dev.settledlands.npc.CorpseRecovery;
import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.PossessionClientSync;
import dev.settledlands.npc.client.NpcClient;
import dev.settledlands.npc.client.PlayerBodyRenderer;
import java.lang.reflect.Method;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import strm.touchnmotion.ModelSpace;
import strm.touchnmotion.TouchNMotionHook;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.EMFAnimationHandler;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;

public final class NpcFpCorpse256Scenario {
    private static final class HostileZombie extends PathfinderEnemy {}
    private static class PathfinderEnemy extends net.minecraft.world.entity.PathfinderMob implements Enemy {
        PathfinderEnemy() { super(null, null); }
    }
    private static final class DummyScreen extends Screen {
        DummyScreen(String title) { super(Component.literal(title)); }
    }

    private static void ok(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static String pickAttackAnimName(BbModel ivy) {
        for (Object entryObj : ivy.getAnimations().entrySet()) {
            java.util.Map.Entry<?, ?> e = (java.util.Map.Entry<?, ?>) entryObj;
            String s = String.valueOf(e.getKey());
            String lo = s.toLowerCase(java.util.Locale.ROOT);
            dev.settledlands.model.BbAnimation anim = (dev.settledlands.model.BbAnimation) e.getValue();
            if (!lo.contains("idle") && !lo.contains("walk") && !lo.contains("die") && !lo.contains("death")
                && !"loop".equalsIgnoreCase(anim.getLoop())
                && !"hold_on_last_frame".equalsIgnoreCase(anim.getLoop())
                && anim.getAnimatedBones().contains("rightarm")) {
                return s;
            }
        }
        return String.valueOf(ivy.getAnimations().keySet().iterator().next());
    }

    private static void enableAnimStack() throws Exception {
        ModList.MODS.clear();
        ModList.MODS.put("entity_model_features", "3.3.9");
        ModList.MODS.put("entity_texture_features", "7.2.4");
        ModList.MODS.put("touch_n_motion", "1.0.4");
        ModList.MODS.put("emf_compat_core", "2.3.2");
        EMFAnimationHandler.FA_ACTIVE = true;
        EMFState.animationHooks.clear();
        EMFAnimationApi.registerAnimationHook(new TouchNMotionHook());
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "candidate256";
        java.nio.file.Path gameDir = java.nio.file.Files.createTempDirectory("sl256-gamedir-");
        try {
            net.neoforged.fml.loading.FMLPaths.currentGameDir = gameDir;
            BbModelManager.clearCache();
            Method setupLambda = NpcClient.class.getDeclaredMethod("lambda$setup$1");
            setupLambda.setAccessible(true);
            setupLambda.invoke(null);
            BbModel ivy = BbModelManager.getModel("ivy_oleander");
            ok(ivy != null, "ivy_oleander.bbmodel must be loaded by BbModelManager");

            if ("base255".equals(mode)) {
                runBase255Reproduction(ivy);
                return;
            }
            runCandidate256(ivy);
        } finally {
            try (var walk = java.nio.file.Files.walk(gameDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
    }

    private static void runBase255Reproduction(BbModel ivy) throws Exception {
        enableAnimStack();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel cl = new ClientLevel();
        mc.level = cl;
        LocalPlayer lp = new LocalPlayer(cl);
        cl.addClientEntity(lp);
        mc.player = lp;

        GuideNpc npc = new GuideNpc(null, cl);
        cl.addClientEntity(npc);
        npc.profile().texture = "bbmodel:ivy_oleander";
        npc.applyProfile();
        npc.walkAnimation.setTestWalk(1.4f, 0.85f, 0.80f);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(new Item(), 1));
        npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(new Item(), 1));

        EMFModelPartRoot.ANIMATE_COUNT = 0;
        TouchNMotionHook.HOOK_APPLIED_COUNT = 0;
        ItemInHandRenderer.CALLS.clear();
        MultiBufferSource.Recording buf = new MultiBufferSource.Recording();
        PoseStack ps = new PoseStack();
        ivy.render(npc, 0f, 0.5f, ps, buf, 0xF000F0, 0);

        ok(EMFModelPartRoot.ANIMATE_COUNT == 0, "base 255 must NOT run FA on GuideNpc");
        ok(TouchNMotionHook.HOOK_APPLIED_COUNT == 0, "base 255 must NOT run TnM on GuideNpc");
        ok(ItemInHandRenderer.CALLS.isEmpty(), "base 255 did not render held items on GuideNpc");

        // Bug 2: possessed local player in FIRST_PERSON with Inventory screen open -> Steve NOT canceled in 255
        PossessionClientSync.CLIENT_STATES.clear();
        PossessionClientSync.CLIENT_STATES.put(lp.getUUID(), new PossessionClientSync.State("ivy_oleander", "Ivy"));
        mc.options.cameraType = CameraType.FIRST_PERSON;
        mc.screen = new DummyScreen("Inventory");
        EMFState.isInGui = false;
        FirstPersonMode.setFirstPersonPass(false);
        RenderPlayerEvent.Pre invEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 1.0f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(invEv);
        ok(!invEv.isCanceled(), "base 255 bug: RenderPlayerEvent.Pre was NOT canceled in 1st-person Inventory");

        // Bug 3: server GuideCorpseNpc has 1.0/1.0 HP in 255
        ServerLevel sl = new ServerLevel();
        GuideCorpseNpc corpse = new GuideCorpseNpc(null, sl);
        CorpseRecovery.syncHealth(corpse);
        double maxHp255 = corpse.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        ok(maxHp255 == 1.0 && corpse.getHealth() == 1.0f, "base 255 bug: corpse HP was 1.0/1.0, got " + corpse.getHealth() + "/" + maxHp255);

        System.out.println("CONFIRMED build 255 bugs: npcFaCalls=0, npcHeldItems=0, fpInventoryCanceled=false, corpseMaxHp=1.0");
    }

    private static void runCandidate256(BbModel ivy) throws Exception {
        enableAnimStack();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel cl = new ClientLevel();
        mc.level = cl;
        LocalPlayer lp = new LocalPlayer(cl);
        cl.addClientEntity(lp);
        mc.player = lp;

        // ------------------------------------------------------------------
        // PART 1: NPC Animation System (FA: Player Extension + Touch'n Motion)
        // ------------------------------------------------------------------
        GuideNpc npc = new GuideNpc(null, cl);
        cl.addClientEntity(npc);
        npc.profile().texture = "bbmodel:ivy_oleander";
        npc.profile().lookAtPlayer = true;
        npc.applyProfile();
        npc.tickCount = 40;
        npc.yBodyRot = 30f;
        npc.yBodyRotO = 30f;
        npc.yHeadRot = 55f;
        npc.yHeadRotO = 55f;
        npc.setXRot(12f);
        npc.xRotO = 12f;
        npc.walkAnimation.setTestWalk(1.25f, 0.82f, 0.78f);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(new Item(), 1));
        npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(new Item(), 1));

        // Mount an outer ETF state to verify mount/unMount balance without leaks
        ETFEntityRenderState outerState = ETFEntityRenderState.forEntity(lp);
        ETFState.STACK.clear();
        ETFState.mount(outerState);

        EMFModelPartRoot.ANIMATE_COUNT = 0;
        ModelSpace.BEFORE_CALLS = 0;
        TouchNMotionHook.HOOK_APPLIED_COUNT = 0;
        ItemInHandRenderer.CALLS.clear();
        ModelPart.RENDERS.clear();
        MultiBufferSource.Recording buf = new MultiBufferSource.Recording();
        PoseStack ps = new PoseStack();

        ivy.render(npc, 30f, 0.5f, ps, buf, 0xF000F0, 0);

        ok(ps.depth == 0, "PoseStack depth must return to 0 after NPC render, got " + ps.depth);
        ok(ETFState.state() == outerState && ETFState.STACK.size() == 1, "ETFState stack must restore outer state");
        ok(EMFModelPartRoot.ANIMATE_COUNT == 1, "FA EMFModelPartRoot.animate() must run for GuideNpc, got " + EMFModelPartRoot.ANIMATE_COUNT);
        ok(ModelSpace.BEFORE_CALLS == 1 && ModelSpace.LAST_IN_WORLD, "TnM ModelSpace.before must see proxy inWorld during GuideNpc render");
        ok(TouchNMotionHook.HOOK_APPLIED_COUNT == 1, "TnM TouchNMotionHook must run on GuideNpc EMF root");
        ok(Math.abs(ModelSpace.LAST_WALK_SPEED - npc.walkAnimation.speed(0.5f)) < 1e-5f,
            "WalkAnimationStateMixin256 must sync exact walk speed to proxy");
        ok(Math.abs(ModelSpace.LAST_WALK_POS - npc.walkAnimation.position(0.5f)) < 1e-5f,
            "WalkAnimationStateMixin256 must sync exact walk position to proxy");
        ok(ItemInHandRenderer.CALLS.size() == 2
            && ItemInHandRenderer.CALLS.contains("R:THIRD_PERSON_RIGHT_HAND")
            && ItemInHandRenderer.CALLS.contains("L:THIRD_PERSON_LEFT_HAND"),
            "Both mainhand and offhand items must render on GuideNpc: " + ItemInHandRenderer.CALLS);

        // An open inventory draws the world behind the GUI; it must NOT suppress real TnM for world NPCs.
        int tnmBeforeOpenScreen = TouchNMotionHook.HOOK_APPLIED_COUNT;
        mc.screen = new DummyScreen("Inventory still renders the world behind itself");
        EMFState.isInGui = false;
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(TouchNMotionHook.HOOK_APPLIED_COUNT == tnmBeforeOpenScreen + 1,
            "Opening Inventory must not disable TnM on world-rendered GuideNpc");
        mc.screen = null;

        // Verify ClientLevelProxyLookupMixin256 does NOT leak proxy after render completes
        for (int probeId = -0x50000001; probeId >= -0x50000010; probeId--) {
            ok(cl.getEntity(probeId) == null, "Proxy ID " + probeId + " must NOT leak in ClientLevel.getEntity outside render");
        }

        // Verify FA + TnM retargeted non-zero rotations onto ivy_oleander's BbModel bones (including TnM body zRot!)
        ModelPart bbRightLeg = ivy.getPart("rightleg");
        ModelPart bbLeftLeg = ivy.getPart("leftleg");
        ModelPart bbBody = ivy.getPart("body");
        ModelPart bbHead = ivy.getPart("head");
        ok(Math.abs(bbRightLeg.xRot) > 0.05f && Math.abs(bbLeftLeg.xRot) > 0.05f && bbRightLeg.xRot * bbLeftLeg.xRot < 0f,
            "FA gait must drive opposing rightleg/leftleg xRot on ivy_oleander: " + bbRightLeg.xRot + " vs " + bbLeftLeg.xRot);
        ok(Math.abs(bbBody.zRot) > 0.03f, "TnM body zRot delta must retarget onto ivy_oleander body: " + bbBody.zRot);
        ok(Math.abs(bbHead.yRot) > 0.2f, "Head yaw must retarget onto ivy_oleander head when lookAtPlayer=true: " + bbHead.yRot);

        // 1b. lookAtPlayer=false when idle -> headYaw zeroed; combat target -> sprinting + head tracking
        NpcProfile noLook = new NpcProfile();
        noLook.texture = "bbmodel:ivy_oleander";
        noLook.lookAtPlayer = false;
        npc.setProfile(noLook);
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(Math.abs(EMFModelPartRoot.LAST_HEAD_YAW) < 1e-4f,
            "lookAtPlayer=false without combat target must clamp headYaw to 0, got " + EMFModelPartRoot.LAST_HEAD_YAW);

        HostileZombie target = new HostileZombie();
        npc.setTarget(target);
        npc.setPoseIndex(2); // NPC sneak poseIndex
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.LAST_PROXY_SPRINTING, "GuideNpc chasing combat target at speed 0.80 must set proxy sprinting=true");
        ok(EMFModelPartRoot.LAST_PROXY_CROUCHING, "GuideNpc poseIndex==2 must set proxy crouching=true");
        ok(Math.abs(EMFModelPartRoot.LAST_HEAD_YAW) > 5.0f, "Combat target must re-enable head tracking even when lookAtPlayer=false");
        npc.setTarget(null);
        npc.setPoseIndex(0);

        // 1c. Build 255 melee combo one-shot clip: active during swing, then seamlessly resumes FA + TnM!
        String attackClip = pickAttackAnimName(ivy);
        int faBeforeCombo = EMFModelPartRoot.ANIMATE_COUNT;
        ItemInHandRenderer.CALLS.clear();
        npc.tickCount = 100;
        npc.playCustomAnimation(attackClip, false, "");
        npc.tickCount = 101; // 0.075s into clip -> active!
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo,
            "Active full-body melee combo clip (" + attackClip + ") must NOT be overwritten by FA locomotion");
        ok(ItemInHandRenderer.CALLS.size() == 2,
            "Held sword & shield must still render during melee combo swing: " + ItemInHandRenderer.CALLS);

        // Once combo clip finishes (elapsed > length, customAnimName still synced until cleared), FA + TnM MUST resume!
        npc.tickCount = 600; // 25s elapsed -> finished one-shot!
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo + 1,
            "Finished one-shot customAnim must automatically resume FA + TnM locomotion!");

        // Partial bone mask (customAnimBone = "rightarm") runs FA + TnM on body/legs AND overlays masked bone!
        npc.playCustomAnimation("", false, "");
        npc.tickCount = 701;
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        float unmaskedRightArmX = ivy.getPart("rightarm").xRot;
        float unmaskedRightArmY = ivy.getPart("rightarm").yRot;
        float unmaskedRightArmZ = ivy.getPart("rightarm").zRot;
        float unmaskedRightLegX = ivy.getPart("rightleg").xRot;

        int faBeforeMask = EMFModelPartRoot.ANIMATE_COUNT;
        npc.tickCount = 700;
        npc.playCustomAnimation(attackClip, false, "rightarm");
        npc.tickCount = 701;
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeMask + 1,
            "Masked customAnimBone='rightarm' must keep FA + TnM locomotion active on the body/legs!");
        ok(Math.abs(ivy.getPart("rightleg").xRot - unmaskedRightLegX) < 1e-4f,
            "Non-masked rightleg must retain exact FA gait pose under customAnimBone='rightarm'");
        float armDelta = Math.abs(ivy.getPart("rightarm").xRot - unmaskedRightArmX)
            + Math.abs(ivy.getPart("rightarm").yRot - unmaskedRightArmY)
            + Math.abs(ivy.getPart("rightarm").zRot - unmaskedRightArmZ);
        ok(armDelta > 1e-3f,
            "Masked customAnimBone='rightarm' (" + attackClip + ") must modify rightarm pose relative to unmasked FA, delta=" + armDelta);
        npc.playCustomAnimation("", false, "");

        // Talking NPC uses BbModel talk animation + held items
        int faBeforeTalk = EMFModelPartRoot.ANIMATE_COUNT;
        ItemInHandRenderer.CALLS.clear();
        npc.setTalking(true);
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeTalk && ItemInHandRenderer.CALLS.size() == 2,
            "Talking GuideNpc must preserve talk clip + render held items");
        npc.setTalking(false);

        // 1d. GuideCorpseNpc and GuideTriggerNpc MUST NEVER run FA/TnM locomotion (even if customAnim is empty!)
        int faBeforeCorpse = EMFModelPartRoot.ANIMATE_COUNT;
        int tnmBeforeCorpse = TouchNMotionHook.HOOK_APPLIED_COUNT;
        GuideCorpseNpc clientCorpse = new GuideCorpseNpc(null, cl);
        clientCorpse.profile().texture = "bbmodel:ivy_oleander";
        clientCorpse.applyProfile();
        clientCorpse.playCustomAnimation("", false, "");
        ivy.render(clientCorpse, 0f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        clientCorpse.playCustomAnimation("die", true, "");
        ivy.render(clientCorpse, 0f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCorpse && TouchNMotionHook.HOOK_APPLIED_COUNT == tnmBeforeCorpse,
            "GuideCorpseNpc must bypass NpcMotionBridge256 completely");

        GuideTriggerNpc triggerNpc = new GuideTriggerNpc(null, cl);
        ivy.render(triggerNpc, 0f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCorpse && TouchNMotionHook.HOOK_APPLIED_COUNT == tnmBeforeCorpse,
            "GuideTriggerNpc must bypass NpcMotionBridge256 completely");

        // 1e. Fallback when FA / optional mods are absent: uses full player state-machine + held items without crashing
        ModList.MODS.clear();
        EMFAnimationHandler.FA_ACTIVE = false;
        ItemInHandRenderer.CALLS.clear();
        npc.onGroundFlag = false;
        npc.setDeltaMovement(0.0, 0.35, 0.0);
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(ItemInHandRenderer.CALLS.size() == 2,
            "Fallback without FA/EMF must cleanly run BbModel actor state machine + render held items");
        npc.onGroundFlag = true;
        npc.setDeltaMovement(0.0, 0.0, 0.0);
        enableAnimStack();

        // ------------------------------------------------------------------
        // PART 2: Possessed First-Person Inventory + First-Person Hands & Sword
        // ------------------------------------------------------------------
        PossessionClientSync.CLIENT_STATES.clear();
        PossessionClientSync.CLIENT_STATES.put(lp.getUUID(), new PossessionClientSync.State("ivy_oleander", "Ivy"));
        mc.options.cameraType = CameraType.FIRST_PERSON;
        FirstPersonMode.setFirstPersonPass(false);
        mc.dispatcher.renderShadow = true;

        // 2a. World camera pass in 1st person (screen==null, isInGui==false, sleeping==false, renderShadow==true) -> NOT canceled
        mc.screen = null;
        EMFState.isInGui = false;
        lp.sleeping = false;
        RenderPlayerEvent.Pre worldFp = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 0.5f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(worldFp);
        ok(!worldFp.isCanceled(), "World 1st-person pass must NOT render full possessed body in camera");

        // 2b. Inventory / GUI screen open in 1st person -> CANCELED (renders possessed NPC instead of Steve!)
        mc.screen = new DummyScreen("Inventory");
        ModelPart.RENDERS.clear();
        RenderPlayerEvent.Pre invFp = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 1.0f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(invFp);
        ok(invFp.isCanceled(), "1st-person Inventory screen MUST cancel Steve & render possessed NPC!");
        ok(!ModelPart.RENDERS.isEmpty(), "Possessed ivy_oleander BbModel parts must render in 1st-person Inventory!");

        // Null buffer safety (e.g. probe pass) -> never cancel even when screen != null
        RenderPlayerEvent.Pre nullBufEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 1.0f, new PoseStack(), null, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(nullBufEv);
        ok(!nullBufEv.isCanceled(), "Null MultiBufferSource must NEVER cancel RenderPlayerEvent.Pre");

        // PAL/HnS FirstPersonMode pass -> must NOT draw full body even if screen != null
        FirstPersonMode.setFirstPersonPass(true);
        RenderPlayerEvent.Pre hnsFpBodyEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 0.5f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(hnsFpBodyEv);
        ok(!hnsFpBodyEv.isCanceled(), "PAL FirstPersonMode pass must NOT render full NPC body");
        FirstPersonMode.setFirstPersonPass(false);

        // EMFState.isInGui == true (even if mc.screen == null) -> CANCELED
        mc.screen = null;
        EMFState.isInGui = true;
        RenderPlayerEvent.Pre emfGuiEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 1.0f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(emfGuiEv);
        ok(emfGuiEv.isCanceled(), "EMFState.isInGui==true in 1st person must render possessed NPC");
        EMFState.isInGui = false;

        // Detached HUD paperdoll (renderShadow==false, fullbright 0xF000F0, partialTick==1.0f) -> CANCELED
        mc.dispatcher.renderShadow = false;
        RenderPlayerEvent.Pre dollEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 1.0f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(dollEv);
        ok(dollEv.isCanceled(), "HUD paperdoll in 1st person must render possessed NPC");
        mc.dispatcher.renderShadow = true;

        // Sleeping in bed in 1st person -> CANCELED
        lp.sleeping = true;
        RenderPlayerEvent.Pre bedEv = new RenderPlayerEvent.Pre(lp, mc.dispatcher.playerRenderer, 0.5f, new PoseStack(), buf, 0xF000F0);
        PlayerBodyRenderer.onRenderPlayerPre(bedEv);
        ok(bedEv.isCanceled(), "Sleeping player in 1st person must render possessed NPC");
        lp.sleeping = false;

        // 2c. First-person empty/offhand arm via FirstPersonArmSubscriber256 AND PossessedFirstPersonHandMixin256
        Class<?> armSubCls = Class.forName("dev.settledlands.npc.client.FirstPersonArmSubscriber256");
        Method onRenderArm = armSubCls.getDeclaredMethod("onRenderArm", RenderArmEvent.class);

        ModelPart bbRightArm = ivy.getPart("rightarm");
        ModelPart bbLeftArm = ivy.getPart("leftarm");
        bbRightArm.setPos(-5.0f, 1.0f, 0.0f);
        bbRightArm.setRotation(0.33f, -0.22f, 0.11f);
        ModelPart.RENDERS.clear();
        buf.requested.clear();
        PoseStack armPs = new PoseStack();
        RenderArmEvent rightArmEv = new RenderArmEvent(armPs, buf, 0xF000F0, lp, HumanoidArm.RIGHT);
        onRenderArm.invoke(null, rightArmEv);
        ok(rightArmEv.isCanceled(), "RenderArmEvent(RIGHT) must be canceled for possessed player");
        ok(armPs.depth == 0, "PoseStack balanced in RenderArmEvent(RIGHT)");
        ok(ModelPart.RENDERS.size() == 1 && ModelPart.RENDERS.get(0).part == bbRightArm,
            "RenderArmEvent(RIGHT) must draw ivy_oleander rightarm");
        ok(Math.abs(ModelPart.RENDERS.get(0).x - (-5.0f)) < 1e-4f
            && Math.abs(ModelPart.RENDERS.get(0).y - 1.0f) < 1e-4f
            && Math.abs(ModelPart.RENDERS.get(0).xRot) < 1e-4f,
            "RenderArmEvent(RIGHT) must render clean first-person (-5, +1, 0) pose");
        ok(!buf.requested.isEmpty() && ivy.getTextureLocation().equals(buf.requested.get(0).texture),
            "First-person arm must use ivy_oleander texture, got " + buf.requested);
        ok(Math.abs(bbRightArm.xRot - 0.33f) < 1e-5f && Math.abs(bbRightArm.yRot - (-0.22f)) < 1e-5f,
            "First-person arm render must restore bbRightArm pose afterwards!");

        ModelPart.RENDERS.clear();
        RenderArmEvent leftArmEv = new RenderArmEvent(new PoseStack(), buf, 0xF000F0, lp, HumanoidArm.LEFT);
        onRenderArm.invoke(null, leftArmEv);
        ok(leftArmEv.isCanceled() && ModelPart.RENDERS.size() == 1 && ModelPart.RENDERS.get(0).part == bbLeftArm
            && Math.abs(ModelPart.RENDERS.get(0).x - 5.0f) < 1e-4f && Math.abs(ModelPart.RENDERS.get(0).y - 1.0f) < 1e-4f,
            "RenderArmEvent(LEFT) must draw ivy_oleander leftarm at (+5, +1, 0)");

        // Direct PlayerRenderer.renderRightHand / renderLeftHand fallback (PossessedFirstPersonHandMixin256)
        PlayerRenderer pr = mc.dispatcher.playerRenderer;
        PlayerRenderer.STEVE_RIGHT_HAND_COUNT = 0;
        PlayerRenderer.STEVE_LEFT_HAND_COUNT = 0;
        ModelPart.RENDERS.clear();
        pr.renderRightHand(new PoseStack(), buf, 0xF000F0, lp);
        pr.renderLeftHand(new PoseStack(), buf, 0xF000F0, lp);
        ok(PlayerRenderer.STEVE_RIGHT_HAND_COUNT == 0 && PlayerRenderer.STEVE_LEFT_HAND_COUNT == 0,
            "PossessedFirstPersonHandMixin256 must cancel vanilla Steve renderRightHand/renderLeftHand");
        ok(ModelPart.RENDERS.size() == 2 && ModelPart.RENDERS.get(0).part == bbRightArm && ModelPart.RENDERS.get(1).part == bbLeftArm,
            "PossessedFirstPersonHandMixin256 must render ivy_oleander rightarm and leftarm");

        // Spectator / unpossessed safety: Steve hands untouched
        lp.spectator = true;
        RenderArmEvent specArmEv = new RenderArmEvent(new PoseStack(), buf, 0xF000F0, lp, HumanoidArm.RIGHT);
        onRenderArm.invoke(null, specArmEv);
        pr.renderRightHand(new PoseStack(), buf, 0xF000F0, lp);
        ok(!specArmEv.isCanceled() && PlayerRenderer.STEVE_RIGHT_HAND_COUNT == 1,
            "Spectator player must NOT draw possessed first-person hands");
        lp.spectator = false;

        // 2d. First-person PAL / HnS sword swing pass (PossessedFirstPersonHandMixin256 + PossessedFirstPersonModelMixin256)
        FirstPersonMode.setFirstPersonPass(true);
        PlayerModel<net.minecraft.client.player.AbstractClientPlayer> pm = pr.getModel();
        pm.rightArm.visible = true;
        pm.leftArm.visible = false;
        pm.rightArm.setPos(-5.2f, 2.5f, -1.1f);
        pm.rightArm.setRotation(-1.15f, 0.42f, -0.18f);
        AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT = 0;
        ModelPart.RENDERS.clear();
        buf.requested.clear();

        pr.render(lp, 0f, 0.5f, new PoseStack(), buf, 0xF000F0);

        ok(AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT == 0,
            "PossessedFirstPersonModelMixin256 must cancel Steve AgeableListModel.renderToBuffer during PAL 1st-person pass!");
        ok(ModelPart.RENDERS.size() == 1 && ModelPart.RENDERS.get(0).part == bbRightArm,
            "PAL 1st-person pass must render visible ivy_oleander rightarm only, got " + ModelPart.RENDERS.size());
        ModelPart.RenderRecord rec = ModelPart.RENDERS.get(0);
        // ivy_oleander root+waist+body+rightarm initial y sum is +1.0, minus PlayerModel arm +2.0 = -1.0 offset -> 2.5 - 1.0 = 1.5
        ok(Math.abs(rec.x - (-5.2f)) < 1e-4f
            && Math.abs(rec.y - 1.5f) < 1e-4f
            && Math.abs(rec.z - (-1.1f)) < 1e-4f
            && Math.abs(rec.xRot - (-1.15f)) < 1e-4f
            && Math.abs(rec.yRot - 0.42f) < 1e-4f
            && Math.abs(rec.zRot - (-0.18f)) < 1e-4f,
            "PAL 1st-person pass must transfer exact HnS 6-DOF pose with pivot delta onto ivy_oleander rightarm: y=" + rec.y);
        ok(Math.abs(pm.rightArm.xRot - (-1.15f)) < 1e-5f && Math.abs(pm.rightArm.y - 2.5f) < 1e-5f,
            "PlayerModel.rightArm pose must stay intact for PlayerItemInHandLayer & HnS BladeTracker!");

        // Outside PAL first-person pass (e.g. 3rd person or armor layer), AgeableListModel.renderToBuffer is NOT canceled
        FirstPersonMode.setFirstPersonPass(false);
        AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT = 0;
        pr.render(lp, 0f, 0.5f, new PoseStack(), buf, 0xF000F0);
        ok(AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT == 1,
            "Outside FirstPersonMode pass, AgeableListModel.renderToBuffer must NOT be canceled by 1st-person arm mixin");

        // Unpossessed player in PAL first-person pass -> Steve arm renders normally
        PossessionClientSync.CLIENT_STATES.clear();
        FirstPersonMode.setFirstPersonPass(true);
        AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT = 0;
        pr.render(lp, 0f, 0.5f, new PoseStack(), buf, 0xF000F0);
        ok(AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT == 1,
            "Unpossessed player must render vanilla Steve arm in PAL 1st-person pass");
        FirstPersonMode.setFirstPersonPass(false);

        // ------------------------------------------------------------------
        // PART 3: Corpse 1/60 HP + Coffin 60/60 HP
        // ------------------------------------------------------------------
        ServerLevel sl = new ServerLevel();
        GuideCorpseNpc corpse = new GuideCorpseNpc(null, sl);
        CorpseRecovery.syncHealth(corpse);
        ok(corpse.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 60.0 && corpse.getHealth() == 1.0f,
            "Non-coffin GuideCorpseNpc must have 1.0/60.0 HP, got " + corpse.getHealth() + "/"
            + corpse.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
        corpse.setCoffin(true);
        ok(corpse.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 60.0 && corpse.getHealth() == 60.0f,
            "Coffin GuideCorpseNpc must have 60.0/60.0 HP");
        corpse.setCoffin(false);
        ok(corpse.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 60.0 && corpse.getHealth() == 1.0f,
            "Reverting coffin -> corpse must restore 1.0/60.0 HP");

        System.out.println("PASS build 256 end-to-end: NPC FA+TnM+items+combos+corpse-guard+fallback, FP inventory+arms+PAL sword, corpse 1/60 HP");
    }
}
''',
}


# Opcode length table for standard non-wide, non-switch JVM bytecodes used in Mixin methods
_SIMPLE_OP_LEN = {
    **{op: 1 for op in (
        0x00, *range(0x01, 0x10), *range(0x1a, 0x2e), *range(0x2e, 0x36), *range(0x3b, 0x4f),
        *range(0x4f, 0x57), *range(0x57, 0x60), *range(0x60, 0x84), *range(0x85, 0x99),
        *range(0xac, 0xb2), 0xbe, 0xbf, 0xc2, 0xc3,
    )},
    0x10: 2, 0x15: 2, 0x16: 2, 0x17: 2, 0x18: 2, 0x19: 2,
    0x36: 2, 0x37: 2, 0x38: 2, 0x39: 2, 0x3a: 2, 0xbc: 2,
    0x11: 3, 0x84: 3,
    **{op: 3 for op in (*range(0x99, 0xa9), 0xc6, 0xc7)},
    0xc8: 5,
}


def _cls_name(k: classedit.Klass, idx: int) -> str:
    return k.utf8(k.cp[idx]['a'])


def _copy_cp_entry(src_k: classedit.Klass, idx: int, dst_k: classedit.Klass, cls_remap: dict[str, str]) -> int:
    e = src_k.cp[idx]
    tag = e['tag']
    if tag == 1:
        return dst_k.add_utf8(e['s'])
    if tag in (3, 4):
        for i, de in enumerate(dst_k.cp):
            if de and de['tag'] == tag and de['a'] == e['a'] and de['b'] == e['b']:
                return i
        return dst_k._add(tag, e['a'], e['b'])
    if tag == 7:
        nm = _cls_name(src_k, idx)
        return dst_k.add_class(cls_remap.get(nm, nm))
    if tag == 8:
        return dst_k.add_string(src_k.utf8(e['a']))
    if tag == 12:
        return dst_k.add_nat(src_k.utf8(e['a']), src_k.utf8(e['b']))
    if tag in (9, 10, 11):
        owner = cls_remap.get(_cls_name(src_k, e['a']), _cls_name(src_k, e['a']))
        nat = src_k.cp[e['b']]
        name, desc = src_k.utf8(nat['a']), src_k.utf8(nat['b'])
        if tag == 9:
            return dst_k.add_fieldref(owner, name, desc)
        if tag == 10:
            return dst_k.add_methodref(owner, name, desc)
        return dst_k.add_interfaceref(owner, name, desc)
    raise ValueError(f'Unsupported CP tag {tag} in Mixin transplant')


def _remap_mixin_code(
    mixin_k: classedit.Klass,
    mixin_method_name: str,
    mixin_method_desc: str,
) -> tuple[dict, str]:
    src_m = mixin_k.find_method(mixin_method_name, mixin_method_desc)
    assert src_m is not None, f'Missing {mixin_method_name}{mixin_method_desc}'
    parts = classedit.code_parts(mixin_k, src_m)
    assert parts['exc'] == [], parts['exc']
    return parts, _cls_name(mixin_k, mixin_k.this)


def _translate_bytes(raw_code: bytes, mixin_k: classedit.Klass, dst_k: classedit.Klass, mixin_cls_name: str) -> bytes:
    target_cls_name = _cls_name(dst_k, dst_k.this)
    cls_remap = {mixin_cls_name: target_cls_name}
    out = bytearray()
    pc = 0
    n = len(raw_code)
    while pc < n:
        op = raw_code[pc]
        if op == 0x12:
            old_idx = raw_code[pc + 1]
            new_idx = _copy_cp_entry(mixin_k, old_idx, dst_k, cls_remap)
            assert new_idx <= 255, f'ldc index {new_idx} > 255 in {mixin_cls_name}'
            out.extend((0x12, new_idx))
            pc += 2
        elif op in (0x13, 0x14, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1):
            old_idx = struct.unpack_from('>H', raw_code, pc + 1)[0]
            new_idx = _copy_cp_entry(mixin_k, old_idx, dst_k, cls_remap)
            out.append(op)
            out.extend(struct.pack('>H', new_idx))
            pc += 3
        elif op == 0xb9:
            old_idx = struct.unpack_from('>H', raw_code, pc + 1)[0]
            new_idx = _copy_cp_entry(mixin_k, old_idx, dst_k, cls_remap)
            out.append(0xb9)
            out.extend(struct.pack('>H', new_idx))
            out.extend(raw_code[pc + 3 : pc + 5])
            pc += 5
        elif op in _SIMPLE_OP_LEN:
            ln = _SIMPLE_OP_LEN[op]
            out.extend(raw_code[pc : pc + ln])
            pc += ln
        else:
            raise ValueError(f'Unsupported opcode 0x{op:02x} at pc={pc} in {mixin_cls_name}')
    return bytes(out)


def _remap_vtypes(buf: bytes, pos: int, count: int, mixin_k: classedit.Klass, dst_k: classedit.Klass, cls_remap: dict[str, str], out: bytearray) -> int:
    for _ in range(count):
        tag = buf[pos]
        if tag == 7:
            old_idx = struct.unpack_from('>H', buf, pos + 1)[0]
            new_idx = _copy_cp_entry(mixin_k, old_idx, dst_k, cls_remap)
            out.append(7)
            out.extend(struct.pack('>H', new_idx))
            pos += 3
        elif tag == 8:
            out.extend(buf[pos : pos + 3])
            pos += 3
        else:
            out.append(tag)
            pos += 1
    return pos


def _remap_smt(body: bytes, mixin_k: classedit.Klass, dst_k: classedit.Klass, mixin_cls_name: str) -> bytes:
    cls_remap = {mixin_cls_name: _cls_name(dst_k, dst_k.this)}
    frames = smt.parse(body)
    remapped = []
    for f in frames:
        nf = dict(f)
        kind = f['kind']
        payload = f.get('payload', b'')
        if payload:
            out = bytearray()
            if 64 <= kind <= 127 or kind == 247:
                _remap_vtypes(payload, 0, 1, mixin_k, dst_k, cls_remap, out)
            elif 252 <= kind <= 254:
                _remap_vtypes(payload, 0, kind - 251, mixin_k, dst_k, cls_remap, out)
            elif kind == 255:
                nl = struct.unpack_from('>H', payload, 0)[0]
                out.extend(payload[0:2])
                p = _remap_vtypes(payload, 2, nl, mixin_k, dst_k, cls_remap, out)
                ns = struct.unpack_from('>H', payload, p)[0]
                out.extend(payload[p : p + 2])
                _remap_vtypes(payload, p + 2, ns, mixin_k, dst_k, cls_remap, out)
            nf['payload'] = bytes(out)
        remapped.append(nf)
    return smt.build(remapped)


def _transplant_into_stub(
    raw_target: bytes,
    mixin_k: classedit.Klass,
    mixin_method_name: str,
    mixin_method_desc: str,
    target_method_name: str,
) -> bytes:
    parts, mixin_cls_name = _remap_mixin_code(mixin_k, mixin_method_name, mixin_method_desc)
    dst_k = classedit.Klass(raw_target)
    dst_m = dst_k.find_method(target_method_name, mixin_method_desc)
    assert dst_m is not None, (target_method_name, mixin_method_desc)
    new_code = _translate_bytes(parts['code'], mixin_k, dst_k, mixin_cls_name)
    nested = []
    for _, name, body in parts['nested']:
        if name == 'StackMapTable':
            smt_ni = dst_k.add_utf8('StackMapTable')
            nested.append([smt_ni, 'StackMapTable', _remap_smt(body, mixin_k, dst_k, mixin_cls_name)])
    new_parts = {
        'max_stack': max(parts['max_stack'], 6),
        'max_locals': parts['max_locals'],
        'code': new_code,
        'exc': [],
        'nested': nested,
    }
    return dst_k.dump(dst_m, classedit.build_code_body(dst_k, new_parts))


def weave_mixins_256(jar: pathlib.Path, out_dir: pathlib.Path) -> bool:
    with zipfile.ZipFile(jar) as zf:
        cfg = json.loads(zf.read('settledlands.mixins.json').decode('utf-8'))
        all_mixins = set(cfg.get('mixins', [])) | set(cfg.get('client', []))
        if 'WalkAnimationStateMixin256' not in all_mixins and 'ClientLevelProxyLookupMixin256' not in all_mixins:
            return False
        required = {
            'WalkAnimationStateMixin256',
            'ClientLevelProxyLookupMixin256',
            'PossessedFirstPersonHandMixin256',
            'PossessedFirstPersonModelMixin256',
        }
        missing = required - all_mixins
        if missing:
            raise AssertionError(f'settledlands.mixins.json missing Build 256 mixins: {sorted(missing)}')

        walk_mx = classedit.Klass(zf.read('dev/settledlands/mixin/WalkAnimationStateMixin256.class'))
        cl_mx = classedit.Klass(zf.read('dev/settledlands/mixin/ClientLevelProxyLookupMixin256.class'))
        hand_mx = classedit.Klass(zf.read('dev/settledlands/mixin/PossessedFirstPersonHandMixin256.class'))
        model_mx = classedit.Klass(zf.read('dev/settledlands/mixin/PossessedFirstPersonModelMixin256.class'))

    # 1. Weave WalkAnimationStateMixin256 -> WalkAnimationState (+ implements WalkAnimationSyncAccess256)
    walk_path = out_dir / 'net/minecraft/world/entity/WalkAnimationState.class'
    walk_k = classedit.Klass(walk_path.read_bytes())
    itf_idx = walk_k.add_class('dev/settledlands/model/WalkAnimationSyncAccess256')
    if itf_idx not in walk_k.ifaces:
        walk_k.ifaces.append(itf_idx)
        walk_k._dirty = True
    walk_raw = v244.rebuild(walk_k, walk_k.methods)
    walk_raw = _transplant_into_stub(
        walk_raw,
        walk_mx,
        'settledlands$copyWalkStateFrom',
        '(Lnet/minecraft/world/entity/WalkAnimationState;)V',
        'settledlands$copyWalkStateFrom',
    )
    walk_path.write_bytes(walk_raw)

    # 2. Weave ClientLevelProxyLookupMixin256 -> ClientLevel.settledlands$mixinHookGetEntity
    cl_path = out_dir / 'net/minecraft/client/multiplayer/ClientLevel.class'
    cl_raw = _transplant_into_stub(
        cl_path.read_bytes(),
        cl_mx,
        'settledlands$lookupNpcMotionProxy',
        '(ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V',
        'settledlands$mixinHookGetEntity',
    )
    cl_path.write_bytes(cl_raw)

    # 3. Weave PossessedFirstPersonHandMixin256 -> PlayerRenderer
    pr_path = out_dir / 'net/minecraft/client/renderer/entity/player/PlayerRenderer.class'
    render_desc = (
        '(Lnet/minecraft/client/player/AbstractClientPlayer;FF'
        'Lcom/mojang/blaze3d/vertex/PoseStack;'
        'Lnet/minecraft/client/renderer/MultiBufferSource;'
        'ILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
    )
    hand_desc = (
        '(Lcom/mojang/blaze3d/vertex/PoseStack;'
        'Lnet/minecraft/client/renderer/MultiBufferSource;'
        'ILnet/minecraft/client/player/AbstractClientPlayer;'
        'Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
    )
    pr_raw = pr_path.read_bytes()
    pr_raw = _transplant_into_stub(pr_raw, hand_mx, 'settledlands$pushPossessedFpContext', render_desc, 'settledlands$mixinHookHeadRender')
    pr_raw = _transplant_into_stub(pr_raw, hand_mx, 'settledlands$popPossessedFpContext', render_desc, 'settledlands$mixinHookReturnRender')
    pr_raw = _transplant_into_stub(pr_raw, hand_mx, 'settledlands$renderPossessedRightHand', hand_desc, 'settledlands$mixinHookRightHand')
    pr_raw = _transplant_into_stub(pr_raw, hand_mx, 'settledlands$renderPossessedLeftHand', hand_desc, 'settledlands$mixinHookLeftHand')
    pr_path.write_bytes(pr_raw)

    # 4. Weave PossessedFirstPersonModelMixin256 -> AgeableListModel
    am_path = out_dir / 'net/minecraft/client/model/AgeableListModel.class'
    model_desc = (
        '(Lcom/mojang/blaze3d/vertex/PoseStack;'
        'Lcom/mojang/blaze3d/vertex/VertexConsumer;'
        'IIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V'
    )
    am_raw = _transplant_into_stub(
        am_path.read_bytes(),
        model_mx,
        'settledlands$renderPossessedFpModelArms',
        model_desc,
        'settledlands$mixinHookRenderToBuffer',
    )
    am_path.write_bytes(am_raw)
    return True


def make_fixture(java: pathlib.Path, jar: pathlib.Path, gson_source: pathlib.Path, tmp: pathlib.Path) -> str:
    joml_out = tmp / 'joml'
    build_joml248.build(java, joml_out)
    gson_out = ui254.build_gson(java, gson_source, tmp)

    fake = tmp / 'auto'
    fake.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, TMPDIR=str(fake))
    targets = [
        'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/GuideTriggerNpc#isInvisible',
        'dev/settledlands/npc/GuideCorpseNpc#isCoffin',
        'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
        'dev/settledlands/npc/client/NpcClient#setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent)',
        'dev/settledlands/npc/client/PlayerBodyRenderer#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
        'dev/settledlands/model/BbModel#getName',
    ]
    r = subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(jar), *targets, '--java', str(java)],
        env=env, capture_output=True, text=True,
    )
    assert r.returncode == 0 and 'ИТОГ: OK' in r.stdout, (r.stdout, r.stderr)
    autos = list(fake.glob('jvmverify-*'))
    assert len(autos) == 1
    autostub = autos[0]
    for n in ('java', 'javax', 'jdk', 'sun', 'dev', 'org/joml', 'com/google/gson'):
        shutil.rmtree(autostub / n, ignore_errors=True)

    merged = dict(nc253.OVERRIDES)
    merged.pop('testing/NpcCopy253Scenario.java', None)
    merged.update(ui254.EXTRA_OVERRIDES)
    merged.pop('testing/UiIvy254Scenario.java', None)
    merged.update(ai255.AUTO_AI_OVERRIDES)
    merged.pop('testing/AutoAi255Scenario.java', None)
    merged.update(OVERRIDES_256)

    src_dir = tmp / 'src'
    out = tmp / 'out'
    out.mkdir()
    sources = []
    for rel, code in merged.items():
        p = src_dir / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(code.strip() + '\n', encoding='utf-8')
        sources.append(p)
        cls_stub = autostub / rel.replace('.java', '.class')
        if cls_stub.exists():
            cls_stub.unlink()

    cp = ':'.join(map(str, [jar, joml_out, gson_out, autostub]))
    subprocess.run(
        [str(java), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
         '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-cp', cp, '-d', str(out), *map(str, sorted(sources))],
        check=True,
    )
    weave_mixins_256(jar, out)
    for extra in (joml_out, gson_out):
        for cf in extra.rglob('*.class'):
            rel = cf.relative_to(extra)
            dst = out / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cf, dst)
    for cf in autostub.rglob('*.class'):
        rel = cf.relative_to(autostub)
        dst = out / rel
        if not dst.exists():
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cf, dst)
    return f'{out}:{jar}'


def run_scenario(java: pathlib.Path, cp: str, mode: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [str(java), '-Xshare:off', '-Xverify:all', '-cp', cp, 'testing.NpcFpCorpse256Scenario', mode],
        capture_output=True, text=True,
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', type=pathlib.Path, required=True)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    ap.add_argument('--base255-jar', type=pathlib.Path, default=None)
    ap.add_argument('--gson-source', type=pathlib.Path, required=True)
    args = ap.parse_args()

    java = args.java.resolve()
    jar = args.jar.resolve()
    gson_src = args.gson_source.resolve()

    if args.base255_jar is not None:
        base_jar = args.base255_jar.resolve()
        with tempfile.TemporaryDirectory(prefix='npcfp256-base255-') as td:
            cp255 = make_fixture(java, base_jar, gson_src, pathlib.Path(td))
            r255 = run_scenario(java, cp255, 'base255')
            if r255.returncode != 0:
                sys.stderr.write(r255.stdout + '\n' + r255.stderr)
                raise SystemExit(r255.returncode)
            print(r255.stdout.strip(), flush=True)

    with tempfile.TemporaryDirectory(prefix='npcfp256-cand-') as td:
        cp256 = make_fixture(java, jar, gson_src, pathlib.Path(td))
        r256 = run_scenario(java, cp256, 'candidate256')
        if r256.returncode != 0:
            sys.stderr.write(r256.stdout + '\n' + r256.stderr)
            raise SystemExit(r256.returncode)
        print(r256.stdout.strip(), flush=True)


if __name__ == '__main__':
    main()
