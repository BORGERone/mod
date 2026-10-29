#!/usr/bin/env python3
"""Minimal compile-time API declarations for src/build255 (never packaged into JAR)."""
import pathlib

FILES = {
    'net/minecraft/core/Holder.java': (
        'package net.minecraft.core;\n'
        'public interface Holder<T> {}\n'
    ),
    'net/minecraft/core/BlockPos.java': (
        'package net.minecraft.core;\n'
        'public class BlockPos {\n'
        '    public BlockPos(int x, int y, int z) {}\n'
        '    public int getX() { return 0; }\n'
        '    public int getY() { return 0; }\n'
        '    public int getZ() { return 0; }\n'
        '    public BlockPos offset(int dx, int dy, int dz) { return this; }\n'
        '    public static BlockPos containing(double x, double y, double z) { return null; }\n'
        '}\n'
    ),
    'net/minecraft/nbt/CompoundTag.java': (
        'package net.minecraft.nbt;\n'
        'public class CompoundTag {\n'
        '    public boolean contains(String key, int type) { return false; }\n'
        '    public boolean getBoolean(String key) { return false; }\n'
        '    public int getInt(String key) { return 0; }\n'
        '    public CompoundTag getCompound(String key) { return null; }\n'
        '    public void putBoolean(String key, boolean val) {}\n'
        '    public void putInt(String key, int val) {}\n'
        '}\n'
    ),
    'net/minecraft/network/chat/Component.java': (
        'package net.minecraft.network.chat;\n'
        'public interface Component {\n'
        '    static MutableComponent translatable(String key) { return null; }\n'
        '    static MutableComponent translatable(String key, Object... args) { return null; }\n'
        '    String getString();\n'
        '}\n'
    ),
    'net/minecraft/network/chat/MutableComponent.java': (
        'package net.minecraft.network.chat;\n'
        'public class MutableComponent implements Component {\n'
        '    public String getString() { return ""; }\n'
        '}\n'
    ),
    'net/minecraft/sounds/SoundEvent.java': (
        'package net.minecraft.sounds;\n'
        'public class SoundEvent {}\n'
    ),
    'net/minecraft/sounds/SoundEvents.java': (
        'package net.minecraft.sounds;\n'
        'public class SoundEvents {\n'
        '    public static SoundEvent WARDEN_SONIC_CHARGE;\n'
        '    public static SoundEvent WOLF_GROWL;\n'
        '    public static SoundEvent PLAYER_ATTACK_STRONG;\n'
        '    public static SoundEvent PLAYER_ATTACK_NODAMAGE;\n'
        '}\n'
    ),
    'net/minecraft/util/Mth.java': (
        'package net.minecraft.util;\n'
        'public class Mth {\n'
        '    public static double atan2(double y, double x) { return 0; }\n'
        '    public static float rotLerp(float delta, float start, float end) { return 0; }\n'
        '}\n'
    ),
    'net/minecraft/util/RandomSource.java': (
        'package net.minecraft.util;\n'
        'public interface RandomSource {\n'
        '    int nextInt(int bound);\n'
        '    float nextFloat();\n'
        '    boolean nextBoolean();\n'
        '}\n'
    ),
    'net/minecraft/world/InteractionHand.java': (
        'package net.minecraft.world;\n'
        'public enum InteractionHand { MAIN_HAND, OFF_HAND }\n'
    ),
    'net/minecraft/world/phys/Vec3.java': (
        'package net.minecraft.world.phys;\n'
        'public class Vec3 {\n'
        '    public final double x, y, z;\n'
        '    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }\n'
        '    public Vec3 subtract(Vec3 other) { return this; }\n'
        '    public double lengthSqr() { return 0; }\n'
        '    public double horizontalDistanceSqr() { return 0; }\n'
        '    public Vec3 normalize() { return this; }\n'
        '    public Vec3 yRot(float rad) { return this; }\n'
        '    public Vec3 scale(double s) { return this; }\n'
        '}\n'
    ),
    'net/minecraft/world/phys/AABB.java': (
        'package net.minecraft.world.phys;\n'
        'public class AABB {\n'
        '    public AABB inflate(double x, double y, double z) { return this; }\n'
        '}\n'
    ),
    'net/minecraft/world/damagesource/DamageSource.java': (
        'package net.minecraft.world.damagesource;\n'
        'import net.minecraft.world.entity.Entity;\n'
        'public class DamageSource {\n'
        '    public Entity getEntity() { return null; }\n'
        '}\n'
    ),
    'net/minecraft/world/damagesource/DamageSources.java': (
        'package net.minecraft.world.damagesource;\n'
        'import net.minecraft.world.entity.LivingEntity;\n'
        'public class DamageSources {\n'
        '    public DamageSource mobAttack(LivingEntity attacker) { return null; }\n'
        '}\n'
    ),
    'net/minecraft/world/entity/ai/attributes/Attribute.java': (
        'package net.minecraft.world.entity.ai.attributes;\n'
        'public class Attribute {}\n'
    ),
    'net/minecraft/world/entity/ai/attributes/AttributeInstance.java': (
        'package net.minecraft.world.entity.ai.attributes;\n'
        'public class AttributeInstance {\n'
        '    public double getValue() { return 0; }\n'
        '}\n'
    ),
    'net/minecraft/world/entity/ai/attributes/Attributes.java': (
        'package net.minecraft.world.entity.ai.attributes;\n'
        'import net.minecraft.core.Holder;\n'
        'public class Attributes {\n'
        '    public static Holder<Attribute> ATTACK_DAMAGE;\n'
        '}\n'
    ),
    'net/minecraft/world/entity/ai/control/LookControl.java': (
        'package net.minecraft.world.entity.ai.control;\n'
        'import net.minecraft.world.entity.Entity;\n'
        'public class LookControl {\n'
        '    public void setLookAt(Entity target, float yMaxRotSpeed, float xMaxRotAngle) {}\n'
        '}\n'
    ),
    'net/minecraft/world/entity/ai/navigation/PathNavigation.java': (
        'package net.minecraft.world.entity.ai.navigation;\n'
        'import net.minecraft.world.entity.Entity;\n'
        'public abstract class PathNavigation {\n'
        '    public void stop() {}\n'
        '    public boolean isDone() { return true; }\n'
        '    public boolean moveTo(Entity target, double speed) { return false; }\n'
        '    public boolean moveTo(double x, double y, double z, double speed) { return false; }\n'
        '}\n'
    ),
    'net/minecraft/world/level/block/Block.java': (
        'package net.minecraft.world.level.block;\n'
        'public class Block {}\n'
    ),
    'net/minecraft/world/level/block/Blocks.java': (
        'package net.minecraft.world.level.block;\n'
        'public class Blocks {\n'
        '    public static Block LAVA;\n'
        '    public static Block MAGMA_BLOCK;\n'
        '    public static Block CACTUS;\n'
        '    public static Block CAMPFIRE;\n'
        '    public static Block SOUL_CAMPFIRE;\n'
        '    public static Block FIRE;\n'
        '    public static Block SOUL_FIRE;\n'
        '    public static Block WATER;\n'
        '    public static Block SWEET_BERRY_BUSH;\n'
        '    public static Block WITHER_ROSE;\n'
        '    public static Block POWDER_SNOW;\n'
        '}\n'
    ),
    'net/minecraft/world/level/block/state/BlockState.java': (
        'package net.minecraft.world.level.block.state;\n'
        'import net.minecraft.world.level.block.Block;\n'
        'public class BlockState {\n'
        '    public boolean isSolid() { return false; }\n'
        '    public boolean is(Block block) { return false; }\n'
        '}\n'
    ),
    'net/minecraft/world/level/Level.java': (
        'package net.minecraft.world.level;\n'
        'import net.minecraft.core.BlockPos;\n'
        'import net.minecraft.world.level.block.state.BlockState;\n'
        'public abstract class Level {\n'
        '    public boolean isClientSide;\n'
        '    public int getMinBuildHeight() { return -64; }\n'
        '    public int getMaxBuildHeight() { return 320; }\n'
        '    public BlockState getBlockState(BlockPos pos) { return null; }\n'
        '}\n'
    ),
    'net/minecraft/server/level/ServerLevel.java': (
        'package net.minecraft.server.level;\n'
        'import java.util.List;\n'
        'import net.minecraft.core.BlockPos;\n'
        'import net.minecraft.world.entity.Entity;\n'
        'import net.minecraft.world.level.Level;\n'
        'import net.minecraft.world.phys.AABB;\n'
        'public class ServerLevel extends Level {\n'
        '    public boolean isLoaded(BlockPos pos) { return true; }\n'
        '    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box) { return List.of(); }\n'
        '}\n'
    ),
    'net/minecraft/world/entity/Entity.java': (
        'package net.minecraft.world.entity;\n'
        'import net.minecraft.core.BlockPos;\n'
        'import net.minecraft.sounds.SoundEvent;\n'
        'import net.minecraft.world.damagesource.DamageSources;\n'
        'import net.minecraft.world.level.Level;\n'
        'import net.minecraft.world.phys.AABB;\n'
        'import net.minecraft.world.phys.Vec3;\n'
        'public abstract class Entity {\n'
        '    public int tickCount;\n'
        '    public boolean hurtMarked;\n'
        '    public Level level() { return null; }\n'
        '    public boolean isAlive() { return true; }\n'
        '    public boolean isRemoved() { return false; }\n'
        '    public boolean isPassenger() { return false; }\n'
        '    public boolean isSpectator() { return false; }\n'
        '    public BlockPos blockPosition() { return null; }\n'
        '    public Vec3 position() { return null; }\n'
        '    public double getX() { return 0; }\n'
        '    public double getY() { return 0; }\n'
        '    public double getZ() { return 0; }\n'
        '    public float getYRot() { return 0; }\n'
        '    public void setYRot(float yRot) {}\n'
        '    public double distanceToSqr(Entity e) { return 0; }\n'
        '    public double distanceToSqr(double x, double y, double z) { return 0; }\n'
        '    public AABB getBoundingBox() { return null; }\n'
        '    public void push(double x, double y, double z) {}\n'
        '    public void playSound(SoundEvent sound, float vol, float pitch) {}\n'
        '    public DamageSources damageSources() { return null; }\n'
        '    public Vec3 getDeltaMovement() { return null; }\n'
        '    public void setDeltaMovement(double x, double y, double z) {}\n'
        '}\n'
    ),
    'net/minecraft/world/entity/LivingEntity.java': (
        'package net.minecraft.world.entity;\n'
        'import net.minecraft.core.Holder;\n'
        'import net.minecraft.util.RandomSource;\n'
        'import net.minecraft.world.InteractionHand;\n'
        'import net.minecraft.world.damagesource.DamageSource;\n'
        'import net.minecraft.world.entity.ai.attributes.Attribute;\n'
        'import net.minecraft.world.entity.ai.attributes.AttributeInstance;\n'
        'public abstract class LivingEntity extends Entity {\n'
        '    public float yBodyRot;\n'
        '    public float yHeadRot;\n'
        '    public RandomSource getRandom() { return null; }\n'
        '    public boolean hasLineOfSight(Entity target) { return true; }\n'
        '    public AttributeInstance getAttribute(Holder<Attribute> attr) { return null; }\n'
        '    public boolean hurt(DamageSource source, float amount) { return false; }\n'
        '    public void swing(InteractionHand hand) {}\n'
        '}\n'
    ),
    'net/minecraft/world/entity/Mob.java': (
        'package net.minecraft.world.entity;\n'
        'import net.minecraft.world.entity.ai.control.LookControl;\n'
        'import net.minecraft.world.entity.ai.navigation.PathNavigation;\n'
        'public abstract class Mob extends LivingEntity {\n'
        '    public boolean isLeashed() { return false; }\n'
        '    public LivingEntity getTarget() { return null; }\n'
        '    public PathNavigation getNavigation() { return null; }\n'
        '    public LookControl getLookControl() { return null; }\n'
        '}\n'
    ),
    'net/minecraft/world/entity/PathfinderMob.java': (
        'package net.minecraft.world.entity;\n'
        'public abstract class PathfinderMob extends Mob {}\n'
    ),
    'net/minecraft/world/entity/monster/Enemy.java': (
        'package net.minecraft.world.entity.monster;\n'
        'public interface Enemy {}\n'
    ),
    'net/minecraft/world/entity/player/Player.java': (
        'package net.minecraft.world.entity.player;\n'
        'import net.minecraft.world.entity.LivingEntity;\n'
        'public abstract class Player extends LivingEntity {}\n'
    ),
    'com/mojang/blaze3d/vertex/PoseStack.java': (
        'package com.mojang.blaze3d.vertex;\n'
        'public class PoseStack {\n'
        '    public void pushPose() {}\n'
        '    public void popPose() {}\n'
        '    public void translate(double x, double y, double z) {}\n'
        '    public void scale(float x, float y, float z) {}\n'
        '}\n'
    ),
    'net/minecraft/client/gui/Font.java': (
        'package net.minecraft.client.gui;\n'
        'public class Font {\n'
        '    public int width(String text) { return 0; }\n'
        '}\n'
    ),
    'net/minecraft/client/gui/GuiGraphics.java': (
        'package net.minecraft.client.gui;\n'
        'import com.mojang.blaze3d.vertex.PoseStack;\n'
        'public class GuiGraphics {\n'
        '    public PoseStack pose() { return null; }\n'
        '    public void fill(int x1, int y1, int x2, int y2, int color) {}\n'
        '    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) { return 0; }\n'
        '}\n'
    ),
    'net/minecraft/client/gui/components/events/GuiEventListener.java': (
        'package net.minecraft.client.gui.components.events;\n'
        'public interface GuiEventListener {}\n'
    ),
    'net/minecraft/client/gui/components/AbstractWidget.java': (
        'package net.minecraft.client.gui.components;\n'
        'import net.minecraft.client.gui.components.events.GuiEventListener;\n'
        'public abstract class AbstractWidget implements GuiEventListener {}\n'
    ),
    'net/minecraft/client/gui/components/Checkbox.java': (
        'package net.minecraft.client.gui.components;\n'
        'public class Checkbox extends AbstractWidget {\n'
        '    public interface OnValueChange {\n'
        '        void onValueChange(Checkbox box, boolean selected);\n'
        '    }\n'
        '}\n'
    ),
    'dev/settledlands/npc/NpcProfile.java': (
        'package dev.settledlands.npc;\n'
        'public class NpcProfile {\n'
        '    public boolean idleWalking;\n'
        '    public NpcDialogueGraph dialogueGraph;\n'
        '    public NpcProfile() {}\n'
        '}\n'
    ),
    'dev/settledlands/npc/NpcDialogueGraph.java': (
        'package dev.settledlands.npc;\n'
        'import java.util.*;\n'
        'public final class NpcDialogueGraph {\n'
        '    public int entry;\n'
        '    public final LinkedHashMap<Integer, Node> nodes = new LinkedHashMap<>();\n'
        '    public static class Node {\n'
        '        public int id, type, x, y;\n'
        '    }\n'
        '}\n'
    ),
    'dev/settledlands/npc/NpcStatsGraph.java': (
        'package dev.settledlands.npc;\n'
        'public final class NpcStatsGraph {\n'
        '    public static final int TYPE = 16;\n'
        '    public static NpcDialogueGraph.Node find(NpcDialogueGraph g) { return null; }\n'
        '    public static int damage(NpcDialogueGraph g) { return 10; }\n'
        '    public static int speed(NpcDialogueGraph g) { return 10; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/GuideNpc.java': (
        'package dev.settledlands.npc;\n'
        'import net.minecraft.world.entity.PathfinderMob;\n'
        'public class GuideNpc extends PathfinderMob {\n'
        '    public int aiMode() { return 0; }\n'
        '    public boolean isTalking() { return false; }\n'
        '    public NpcProfile profile() { return null; }\n'
        '    public void playCustomAnimation(String anim, boolean loop) {}\n'
        '    public double walkingNavigationSpeed(boolean returning) { return 1.0D; }\n'
        '    public boolean settledlands$hasActiveMobilityRoute() { return false; }\n'
        '    public void settledlands$tickMobilityOriginal() {}\n'
        '}\n'
    ),
    'dev/settledlands/npc/GuideTriggerNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideTriggerNpc extends GuideNpc {}\n'
    ),
    'dev/settledlands/npc/GuideCorpseNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideCorpseNpc extends GuideNpc {}\n'
    ),
    'dev/settledlands/npc/client/NpcUi.java': (
        'package dev.settledlands.npc.client;\n'
        'import java.util.function.IntConsumer;\n'
        'import java.util.function.IntFunction;\n'
        'import net.minecraft.client.gui.components.AbstractWidget;\n'
        'import net.minecraft.client.gui.components.Checkbox;\n'
        'import net.minecraft.network.chat.Component;\n'
        'public class NpcUi {\n'
        '    public static class NpcSlider extends AbstractWidget {}\n'
        '    public static Checkbox checkbox(int x, int y, Component label, boolean checked,\n'
        '            Checkbox.OnValueChange onChange) { return null; }\n'
        '    public static NpcSlider slider(int x, int y, int w, int min, int max, int val,\n'
        '            IntFunction<Component> fmt, IntConsumer onChange) { return null; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcConfigScreen.java': (
        'package dev.settledlands.npc.client;\n'
        'import dev.settledlands.npc.NpcProfile;\n'
        'import net.minecraft.client.gui.Font;\n'
        'import net.minecraft.client.gui.components.events.GuiEventListener;\n'
        'public class NpcConfigScreen {\n'
        '    public NpcProfile settledlands$profile() { return null; }\n'
        '    public Font settledlands$font() { return null; }\n'
        '    public int settledlands$left() { return 0; }\n'
        '    public int settledlands$contentRight() { return 0; }\n'
        '    public int settledlands$fieldX() { return 0; }\n'
        '    public int settledlands$fieldW() { return 0; }\n'
        '    public int settledlands$rowH() { return 0; }\n'
        '    public int settledlands$rowLabel(String key, int y) { return y; }\n'
        '    public int settledlands$blockStart(String key, int y) { return 0; }\n'
        '    public int settledlands$contentTop(int block) { return 0; }\n'
        '    public int settledlands$blockEnd(int block, int y) { return y; }\n'
        '    public GuiEventListener settledlands$addWidget(GuiEventListener w) { return w; }\n'
        '    public void settledlands$field(String key, int val) {}\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcGraphCanvas.java': (
        'package dev.settledlands.npc.client;\n'
        'public class NpcGraphCanvas {\n'
        '    public double panX, panY, zoom;\n'
        '    public int left, right, top, bottom;\n'
        '    public double screenX(double d) { return 0; }\n'
        '    public double screenY(double d) { return 0; }\n'
        '    public double worldX(double d) { return 0; }\n'
        '    public double worldY(double d) { return 0; }\n'
        '    public boolean contains(double x, double y) { return false; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcStatsGraphUi.java': (
        'package dev.settledlands.npc.client;\n'
        'import dev.settledlands.npc.NpcDialogueGraph;\n'
        'public final class NpcStatsGraphUi {\n'
        '    public static boolean mouseClicked(NpcDialogueGraphScreen s, double x, double y, int b) { return false; }\n'
        '    public static void frameAll(NpcGraphCanvas c, NpcDialogueGraph g) {}\n'
        '}\n'
    ),
    'net/minecraft/client/gui/screens/Screen.java': (
        'package net.minecraft.client.gui.screens;\n'
        'public abstract class Screen {\n'
        '    public static boolean hasShiftDown() { return false; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcDialogueGraphScreen.java': (
        'package dev.settledlands.npc.client;\n'
        'import dev.settledlands.npc.NpcDialogueGraph;\n'
        'import net.minecraft.client.gui.Font;\n'
        'public class NpcDialogueGraphScreen {\n'
        '    public NpcDialogueGraph settledlands$graph() { return null; }\n'
        '    public NpcGraphCanvas settledlands$canvas() { return null; }\n'
        '    public Font settledlands$font() { return null; }\n'
        '    public void settledlands$changed() {}\n'
        '    public void settledlands$remember() {}\n'
        '    public boolean settledlands$contextOpen() { return false; }\n'
        '    public boolean settledlands$insideInspector(double x, double y) { return false; }\n'
        '    public void settledlands$hideStatsInspector() {}\n'
        '    public boolean settledlands$mouseDraggedOriginal(double x, double y, int b, double dx, double dy) { return false; }\n'
        '    public boolean settledlands$mouseReleasedOriginal(double x, double y, int b) { return false; }\n'
        '}\n'
    ),
}


def main(out: pathlib.Path):
    for rel, src in FILES.items():
        p = out / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(src, encoding='utf-8')


if __name__ == '__main__':
    import sys
    main(pathlib.Path(sys.argv[1]))
