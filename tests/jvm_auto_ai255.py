#!/usr/bin/env python3
"""End-to-end Java 21 (-Xverify:all) harness for Build 255 (plus Build 254 baseline check).

Exercises real NpcAutoAi255, NpcAutoAiEngine255, NpcAutoAiConfigUi255, NpcAutoAiGraphUi255,
GuideNpc, GuideTriggerNpc, GuideCorpseNpc, NpcProfile, NpcDialogueGraph, NpcStatsGraph,
NpcEdit, NpcConfigScreen, NpcDialogueGraphScreen, NpcGraphCanvas, and NpcCopyPickPayload
on a multi-tick 3D server level with block hazards, navigation, hostile Enemy mobs,
neutral mobs, players, NBT persistence, network packets, and undo/redo history.
"""
import argparse, os, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import build_joml248
import jvm_npc_copy253 as nc253
import jvm_ui_ivy254 as ui254

AUTO_AI_OVERRIDES = {
    'org/slf4j/Logger.java': '''
package org.slf4j;
public interface Logger {
    default void info(String m) {}
    default void info(String m, Object... a) {}
    default void warn(String m) {}
    default void warn(String m, Object... a) {}
    default void error(String m) {}
    default void error(String m, Object... a) {}
    default void error(String m, Throwable t) {}
}
''',
    'org/slf4j/LoggerFactory.java': '''
package org.slf4j;
public final class LoggerFactory {
    private static final Logger NOP = new Logger() {};
    public static Logger getLogger(String name) { return NOP; }
    public static Logger getLogger(Class<?> cls) { return NOP; }
}
''',
    'net/minecraft/core/Vec3i.java': '''
package net.minecraft.core;
import java.util.Objects;
public class Vec3i {
    private final int x, y, z;
    public Vec3i(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public double distSqr(Vec3i o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
    @Override public boolean equals(Object o) {
        return o instanceof Vec3i p && x == p.x && y == p.y && z == p.z;
    }
    @Override public int hashCode() { return Objects.hash(x, y, z); }
    @Override public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}
''',
    'net/minecraft/core/BlockPos.java': '''
package net.minecraft.core;
public class BlockPos extends Vec3i {
    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(getX() + dx, getY() + dy, getZ() + dz); }
    public BlockPos above() { return new BlockPos(getX(), getY() + 1, getZ()); }
    public BlockPos below() { return new BlockPos(getX(), getY() - 1, getZ()); }
    public long asLong() {
        return (((long) getX() & 0x3FFFFFFL) << 38) | (((long) getZ() & 0x3FFFFFFL) << 12) | ((long) getY() & 0xFFFL);
    }
    public static BlockPos of(long v) {
        int x = (int) (v >> 38);
        int y = (int) (v << 52 >> 52);
        int z = (int) (v << 26 >> 38);
        return new BlockPos(x, y, z);
    }
    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
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
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }
    public static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
''',
    'net/minecraft/util/RandomSource.java': '''
package net.minecraft.util;
import java.util.Random;
public interface RandomSource {
    void setSeed(long seed);
    int nextInt(int bound);
    float nextFloat();
    double nextDouble();
    boolean nextBoolean();

    static RandomSource create(long seed) {
        return new RandomSource() {
            private final Random rng = new Random(seed);
            @Override public void setSeed(long s) { rng.setSeed(s); }
            @Override public int nextInt(int bound) { return rng.nextInt(bound); }
            @Override public float nextFloat() { return rng.nextFloat(); }
            @Override public double nextDouble() { return rng.nextDouble(); }
            @Override public boolean nextBoolean() { return rng.nextBoolean(); }
        };
    }
}
''',
    'net/minecraft/world/InteractionHand.java': '''
package net.minecraft.world;
public enum InteractionHand { MAIN_HAND, OFF_HAND }
''',
    'net/minecraft/sounds/SoundEvent.java': '''
package net.minecraft.sounds;
public class SoundEvent {
    public final String id;
    public SoundEvent(String id) { this.id = id; }
}
''',
    'net/minecraft/sounds/SoundEvents.java': '''
package net.minecraft.sounds;
public final class SoundEvents {
    public static final SoundEvent WARDEN_SONIC_CHARGE = new SoundEvent("warden_sonic_charge");
    public static final SoundEvent WOLF_GROWL = new SoundEvent("wolf_growl");
    public static final SoundEvent PLAYER_ATTACK_STRONG = new SoundEvent("player_attack_strong");
    public static final SoundEvent PLAYER_ATTACK_NODAMAGE = new SoundEvent("player_attack_nodamage");
}
''',
    'net/minecraft/world/phys/Vec3.java': '''
package net.minecraft.world.phys;
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public final double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
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
    'net/minecraft/world/phys/AABB.java': '''
package net.minecraft.world.phys;
public class AABB {
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }
    public AABB inflate(double dx, double dy, double dz) {
        return new AABB(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
    }
    public AABB inflate(double d) { return inflate(d, d, d); }
    public boolean intersects(AABB o) {
        return maxX >= o.minX && minX <= o.maxX
                && maxY >= o.minY && minY <= o.maxY
                && maxZ >= o.minZ && minZ <= o.maxZ;
    }
}
''',
    'net/minecraft/world/level/block/Block.java': '''
package net.minecraft.world.level.block;
public class Block {
    public final String name;
    public final boolean solid;
    public Block(String name, boolean solid) { this.name = name; this.solid = solid; }
}
''',
    'net/minecraft/world/level/block/Blocks.java': '''
package net.minecraft.world.level.block;
public final class Blocks {
    public static final Block AIR = new Block("air", false);
    public static final Block STONE = new Block("stone", true);
    public static final Block LAVA = new Block("lava", false);
    public static final Block MAGMA_BLOCK = new Block("magma_block", true);
    public static final Block CACTUS = new Block("cactus", true);
    public static final Block CAMPFIRE = new Block("campfire", true);
    public static final Block SOUL_CAMPFIRE = new Block("soul_campfire", true);
    public static final Block FIRE = new Block("fire", false);
    public static final Block SOUL_FIRE = new Block("soul_fire", false);
    public static final Block WATER = new Block("water", false);
    public static final Block SWEET_BERRY_BUSH = new Block("sweet_berry_bush", false);
    public static final Block WITHER_ROSE = new Block("wither_rose", false);
    public static final Block POWDER_SNOW = new Block("powder_snow", false);
}
''',
    'net/minecraft/world/level/block/state/BlockState.java': '''
package net.minecraft.world.level.block.state;
import net.minecraft.world.level.block.Block;
public class BlockState {
    private final Block block;
    public BlockState(Block block) { this.block = block; }
    public Block getBlock() { return block; }
    public boolean isSolid() { return block != null && block.solid; }
    public boolean is(Block other) { return block == other; }
}
''',
    'net/minecraft/world/damagesource/DamageSource.java': '''
package net.minecraft.world.damagesource;
import net.minecraft.world.entity.Entity;
public class DamageSource {
    private final Entity attacker;
    public DamageSource(Entity attacker) { this.attacker = attacker; }
    public Entity getEntity() { return attacker; }
}
''',
    'net/minecraft/world/damagesource/DamageSources.java': '''
package net.minecraft.world.damagesource;
import net.minecraft.world.entity.LivingEntity;
public class DamageSources {
    public DamageSource mobAttack(LivingEntity attacker) { return new DamageSource(attacker); }
}
''',
    'net/minecraft/world/entity/ai/attributes/Attributes.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.core.Holder;
public final class Attributes {
    public static final Holder<Attribute> SCALE = new Holder.Reference<>();
    public static final Holder<Attribute> MAX_HEALTH = new Holder.Reference<>();
    public static final Holder<Attribute> ATTACK_DAMAGE = new Holder.Reference<>();
    public static final Holder<Attribute> MOVEMENT_SPEED = new Holder.Reference<>();
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeInstance.java': '''
package net.minecraft.world.entity.ai.attributes;
public class AttributeInstance {
    public double baseValue = 1.0;
    public AttributeInstance() {}
    public AttributeInstance(double baseValue) { this.baseValue = baseValue; }
    public void setBaseValue(double v) { this.baseValue = v; }
    public double getBaseValue() { return baseValue; }
    public double getValue() { return baseValue; }
}
''',
    'net/minecraft/world/entity/ai/control/LookControl.java': '''
package net.minecraft.world.entity.ai.control;
import net.minecraft.world.entity.Entity;
public class LookControl {
    public Entity lastLookTarget;
    public void setLookAt(Entity target, float yMax, float xMax) { this.lastLookTarget = target; }
}
''',
    'net/minecraft/world/entity/ai/navigation/PathNavigation.java': '''
package net.minecraft.world.entity.ai.navigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
public class PathNavigation {
    private final Mob mob;
    public float multiplier = 1.0f;
    public boolean active = false;
    public double destX, destY, destZ;
    public double lastSpeed = 0.0;
    public int moveCalls = 0;

    public PathNavigation() { this.mob = null; }
    public PathNavigation(Mob mob) { this.mob = mob; }

    public void setMaxVisitedNodesMultiplier(float f) { this.multiplier = f; }
    public void resetMaxVisitedNodesMultiplier() { this.multiplier = 1.0f; }
    public boolean isStableDestination(BlockPos pos) {
        return mob == null || mob.level() == null || mob.level().getBlockState(pos.below()).isSolid();
    }
    public boolean isDone() { return !active; }
    public void stop() { this.active = false; }
    public boolean moveTo(Entity target, double speed) {
        if (target == null) return false;
        return moveTo(target.getX(), target.getY(), target.getZ(), speed);
    }
    public boolean moveTo(double x, double y, double z, double speed) {
        this.destX = x;
        this.destY = y;
        this.destZ = z;
        this.lastSpeed = speed;
        this.active = true;
        this.moveCalls++;
        return true;
    }
    public void stepMovement() {
        if (!active || mob == null) return;
        double dx = destX - mob.getX();
        double dz = destZ - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double step = Math.max(0.05, 0.22 * lastSpeed);
        if (dist <= step) {
            mob.setPos(destX, destY, destZ);
            active = false;
        } else {
            mob.setPos(mob.getX() + dx / dist * step, destY, mob.getZ() + dz / dist * step);
        }
    }
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
public class Entity implements IEntityExtension {
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
    public boolean noGravity = false;
    public boolean hurtMarked = false;
    private double x = 0.5, y = 64.0, z = 0.5;
    private float yRot = 0.0f;
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
    public boolean isPassenger() { return passenger; }
    public boolean isSpectator() { return spectator; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setPos(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3 position() { return new Vec3(x, y, z); }
    public BlockPos blockPosition() { return BlockPos.containing(x, y, z); }
    public AABB getBoundingBox() { return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3); }
    public float getYRot() { return yRot; }
    public void setYRot(float r) { this.yRot = r; }
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
    'net/minecraft/world/entity/LivingEntity.java': '''
package net.minecraft.world.entity;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
public class LivingEntity extends Entity {
    private final Map<Holder<Attribute>, AttributeInstance> attrs = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create(12345L);
    public float health = 20f;
    public float yBodyRot = 0f;
    public float yHeadRot = 0f;
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
    public RandomSource getRandom() { return random; }
    public boolean hasLineOfSight(Entity target) { return lineOfSight; }
    public void setHealth(float h) {
        this.health = h;
        if (h <= 0f) this.alive = false;
    }
    public float getHealth() { return health; }
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
    'net/minecraft/world/entity/Mob.java': '''
package net.minecraft.world.entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
public class Mob extends LivingEntity {
    protected int xpReward;
    private boolean persistenceRequired;
    public boolean leashed = false;
    private LivingEntity target;
    private final PathNavigation nav = new PathNavigation(this);
    private final LookControl lookControl = new LookControl();

    public Mob(EntityType<? extends Mob> type, Level level) { super(type, level); }
    public void setPersistenceRequired() { this.persistenceRequired = true; }
    public boolean isLeashed() { return leashed; }
    public LivingEntity getTarget() { return target; }
    public void setTarget(LivingEntity t) { this.target = t; }
    public PathNavigation getNavigation() { return nav; }
    public LookControl getLookControl() { return lookControl; }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
''',
    'net/minecraft/world/entity/PathfinderMob.java': '''
package net.minecraft.world.entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
public class PathfinderMob extends Mob {
    protected PathfinderMob(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }
    @Override public boolean hurt(DamageSource source, float amount) { return super.hurt(source, amount); }
}
''',
    'net/minecraft/world/entity/monster/Enemy.java': '''
package net.minecraft.world.entity.monster;
public interface Enemy {}
''',
    'net/minecraft/world/entity/monster/Zombie.java': '''
package net.minecraft.world.entity.monster;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
public class Zombie extends PathfinderMob implements Enemy {
    public Zombie(Level level) {
        super(null, level);
        this.health = 80.0f;
    }
}
''',
    'net/minecraft/world/entity/animal/Wolf.java': '''
package net.minecraft.world.entity.animal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
public class Wolf extends PathfinderMob {
    public Wolf(Level level) {
        super(null, level);
        this.health = 30.0f;
    }
}
''',
    'net/minecraft/world/level/Level.java': '''
package net.minecraft.world.level;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
public class Level {
    public boolean isClientSide = false;
    protected final Map<Integer, Entity> byId = new LinkedHashMap<>();
    protected final Map<UUID, Entity> byUuid = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();

    public boolean isClientSide() { return isClientSide; }
    public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
    public int getMinBuildHeight() { return -64; }
    public int getMaxBuildHeight() { return 320; }
    public Entity getEntity(int id) { return byId.get(id); }
    public void addEntity(Entity e) {
        byId.put(e.getId(), e);
        byUuid.put(e.getUUID(), e);
    }
    public void removeEntity(Entity e) {
        e.removed = true;
        byId.remove(e.getId());
        byUuid.remove(e.getUUID());
    }
    public void setBlock(BlockPos pos, Block b) {
        blocks.put(pos, new BlockState(b));
    }
    public BlockState getBlockState(BlockPos pos) {
        BlockState custom = blocks.get(pos);
        if (custom != null) return custom;
        // Default flat stone floor at y <= 63, air at y >= 64
        return new BlockState(pos.getY() <= 63 ? Blocks.STONE : Blocks.AIR);
    }
    public Player getNearestPlayer(Entity src, double maxDist) {
        Player best = null;
        double bestSq = maxDist * maxDist;
        for (Entity e : byId.values()) {
            if (e instanceof Player p && p.isAlive() && !p.isSpectator()) {
                double dSq = src.distanceToSqr(p);
                if (dSq <= bestSq) {
                    bestSq = dSq;
                    best = p;
                }
            }
        }
        return best;
    }
}
''',
    'net/minecraft/nbt/NbtIo.java': '''
package net.minecraft.nbt;
import java.io.DataOutput;
import java.io.IOException;
public final class NbtIo {
    public static void write(CompoundTag tag, DataOutput out) throws IOException {
        out.writeByte(10);
        out.writeInt(tag == null ? 0 : tag.getAllKeys().size() * 16);
    }
}
''',
    'net/minecraft/util/datafix/DataFixTypes.java': '''
package net.minecraft.util.datafix;
public enum DataFixTypes {}
''',
    'net/minecraft/world/level/saveddata/SavedData.java': '''
package net.minecraft.world.level.saveddata;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.util.datafix.DataFixTypes;
public abstract class SavedData {
    public void setDirty() {}
    public static class Factory<T extends SavedData> {
        public Factory(Supplier<T> constructor, BiFunction<?, ?, T> deserializer, DataFixTypes type) {}
    }
}
''',
    'net/minecraft/server/MinecraftServer.java': '''
package net.minecraft.server;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
public class MinecraftServer {
    public ServerLevel overworldLevel;
    public RegistryAccess.Frozen registryAccess() { return RegistryAccess.EMPTY; }
    public ServerLevel overworld() { return null; }
    public Iterable<ServerLevel> getAllLevels() {
        return overworldLevel == null ? java.util.List.of() : java.util.List.of(overworldLevel);
    }
}
''',
    'net/minecraft/server/level/ServerLevel.java': '''
package net.minecraft.server.level;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
public class ServerLevel extends Level {
    private final MinecraftServer server = new MinecraftServer();
    public final Set<BlockPos> unloadedPositions = new HashSet<>();

    public ServerLevel() {
        this.isClientSide = false;
        this.server.overworldLevel = this;
    }
    public MinecraftServer getServer() { return server; }
    public Entity getEntity(UUID uuid) { return byUuid.get(uuid); }
    public boolean isLoaded(BlockPos pos) { return !unloadedPositions.contains(pos); }
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box) {
        return getEntitiesOfClass(cls, box, e -> true);
    }
    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box, Predicate<? super T> pred) {
        if ((box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ) > 100000.0) {
            throw new IllegalArgumentException("Oversized AABB query forbidden (>1e5): " + box);
        }
        List<T> out = new ArrayList<>();
        for (Entity e : byId.values()) {
            if (cls.isInstance(e) && !e.isRemoved() && e.getBoundingBox().intersects(box)) {
                T cast = (T) e;
                if (pred == null || pred.test(cast)) {
                    out.add(cast);
                }
            }
        }
        return out;
    }
}
''',
    'net/minecraft/server/level/ServerPlayer.java': '''
package net.minecraft.server.level;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
public class ServerPlayer extends Player {
    public final MinecraftServer server;
    public int permLevel = 2;
    public final List<Component> messages = new ArrayList<>();
    public final List<ItemStack> dropped = new ArrayList<>();
    public ServerPlayer(ServerLevel level) {
        super(level);
        this.server = level.getServer();
    }
    public boolean hasPermissions(int p) { return permLevel >= p; }
    public void displayClientMessage(Component c, boolean actionBar) { messages.add(c); }
    public ItemEntity drop(ItemStack stack, boolean randomly) {
        dropped.add(stack);
        return new ItemEntity(stack);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.save(new ListTag()));
    }
}
''',
    'net/neoforged/neoforge/network/PacketDistributor.java': '''
package net.neoforged.neoforge.network;
import java.util.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
public final class PacketDistributor {
    public static final List<CustomPacketPayload> SENT_TO_SERVER = new ArrayList<>();
    public static final List<CustomPacketPayload> SENT_TO_CLIENT = new ArrayList<>();
    public static void sendToServer(CustomPacketPayload first, CustomPacketPayload... rest) {
        SENT_TO_SERVER.add(first);
        Collections.addAll(SENT_TO_SERVER, rest);
    }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload first, CustomPacketPayload... rest) {
        SENT_TO_CLIENT.add(first);
        Collections.addAll(SENT_TO_CLIENT, rest);
    }
}
''',
    'dev/settledlands/npc/NpcRelations.java': '''
package dev.settledlands.npc;
import java.util.*;
import net.minecraft.server.MinecraftServer;
public class NpcRelations {
    public record Rel(UUID player, String name, int value) {}
    private static final NpcRelations INSTANCE = new NpcRelations();
    public int migrated = 0;
    public static NpcRelations get(MinecraftServer s) { return INSTANCE; }
    public void migrateNpc(UUID a, UUID b) { migrated++; }
    public void migrateQuestSlotsToIds(UUID u, List<String> ids) {}
    public List<Rel> list(UUID npcId) { return List.of(); }
}
''',
    'dev/settledlands/npc/IvyCombatAi.java': '''
package dev.settledlands.npc;
import net.minecraft.world.damagesource.DamageSource;
public class IvyCombatAi {
    public static int fireballCalls = 0;
    public static int legacyTickCalls = 0;
    private final GuideNpc npc;
    public IvyCombatAi(GuideNpc npc) { this.npc = npc; }
    public boolean tick() { legacyTickCalls++; return false; }
    public void onHurt(DamageSource source) {}
    public void launchFireball() { fireballCalls++; }
}
''',
    'net/minecraft/client/gui/components/Checkbox.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class Checkbox extends AbstractWidget {
    public interface OnValueChange { void onValueChange(Checkbox cb, boolean selected); }
    private boolean selected;
    private final OnValueChange callback;
    private Checkbox(int x, int y, int w, Component msg, boolean sel, OnValueChange cb) {
        super(x, y, w, 18, msg);
        this.selected = sel;
        this.callback = cb;
    }
    public boolean selected() { return selected; }
    @Override public void onClick(double mx, double my) {
        this.selected = !this.selected;
        if (this.callback != null) this.callback.onValueChange(this, this.selected);
    }
    public static Builder builder(Component msg, Font font) { return new Builder(msg); }
    public static class Builder {
        private final Component msg;
        private int x, y, maxW = 120;
        private boolean sel;
        private OnValueChange cb;
        private Tooltip tip;
        public Builder(Component msg) { this.msg = msg; }
        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder selected(boolean s) { this.sel = s; return this; }
        public Builder maxWidth(int w) { this.maxW = w; return this; }
        public Builder onValueChange(OnValueChange cb) { this.cb = cb; return this; }
        public Builder tooltip(Tooltip t) { this.tip = t; return this; }
        public Checkbox build() {
            Checkbox c = new Checkbox(x, y, maxW, msg, sel, cb);
            c.setTooltip(tip);
            return c;
        }
    }
}
''',
    'net/minecraft/client/gui/components/AbstractSliderButton.java': '''
package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;
public abstract class AbstractSliderButton extends AbstractWidget {
    protected double value;
    public AbstractSliderButton(int x, int y, int w, int h, Component msg, double value) {
        super(x, y, w, h, msg);
        this.value = value;
    }
    public double normalizedValue() { return value; }
    public void setNormalizedValue(double v) {
        this.value = Math.max(0.0, Math.min(1.0, v));
        applyValue();
        updateMessage();
    }
    protected abstract void updateMessage();
    protected abstract void applyValue();
}
''',
    'net/minecraft/client/gui/screens/Screen.java': '''
package net.minecraft.client.gui.screens;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
public abstract class Screen implements ContainerEventHandler, Renderable {
    public static boolean shiftHeld = false;
    public static boolean hasShiftDown() { return shiftHeld; }

    protected final Component title;
    public int width = 960;
    public int height = 540;
    protected Minecraft minecraft;
    protected Font font;
    public final List<Renderable> renderables = new ArrayList<>();
    private final List<GuiEventListener> children = new ArrayList<>();
    private GuiEventListener focused;
    private boolean dragging;

    protected Screen(Component title) {
        this.title = title == null ? Component.empty() : title;
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }
    protected void init() {}
    public void init(Minecraft mc, int w, int h) {
        this.minecraft = mc;
        this.font = mc.font;
        this.width = w;
        this.height = h;
        rebuildWidgets();
    }
    protected void clearWidgets() {
        renderables.clear();
        children.clear();
    }
    protected void rebuildWidgets() {
        clearWidgets();
        init();
    }
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T w) {
        renderables.add(w);
        children.add(w);
        return w;
    }
    @Override public List<? extends GuiEventListener> children() { return children; }
    @Override public void setFocused(GuiEventListener l) { this.focused = l; }
    @Override public void setDragging(boolean d) { this.dragging = d; }
    public void tick() {}
    public void onClose() {}
    public boolean isPauseScreen() { return false; }
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x80000000);
    }
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        for (Renderable r : renderables) {
            r.render(g, mx, my, pt);
        }
    }
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (GuiEventListener c : children) {
            if (c.mouseClicked(mx, my, b)) return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { return false; }
    @Override public boolean mouseReleased(double mx, double my, int b) { return false; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return false; }
    @Override public boolean keyPressed(int k, int s, int m) { return false; }
    public boolean keyReleased(int k, int s, int m) { return false; }
    public void setTooltipForNextRenderPass(Component c) {}
    public void setTooltipForNextRenderPass(List<?> l) {}
    public void setTooltipForNextRenderPass(List<?> l, ClientTooltipPositioner p, boolean f) {}
    public void setTooltipForNextRenderPass(Tooltip t, ClientTooltipPositioner p, boolean f) {}
}
''',
    'testing/AutoAiBase254Check.java': '''
package testing;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcConfigScreen;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.fml.loading.FMLPaths;

public final class AutoAiBase254Check {
    private static void ok(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        FMLPaths.currentGameDir = Path.of(args[0]);
        ServerLevel sl = new ServerLevel();
        GuideNpc npc = new GuideNpc(NpcContent.guide(), sl);
        npc.setPos(50.5, 64.0, 50.5);
        sl.addEntity(npc);

        Zombie z = new Zombie(sl);
        z.setPos(54.5, 64.0, 50.5);
        sl.addEntity(z);

        Method step = GuideNpc.class.getDeclaredMethod("customServerAiStep");
        step.setAccessible(true);
        for (int t = 0; t < 60; t++) {
            npc.tickCount = t;
            step.invoke(npc);
            npc.getNavigation().stepMovement();
        }
        ok(npc.getNavigation().moveCalls == 0 && z.totalDamageTaken == 0f,
                "Build 254 peaceful GuideNpc had no autonomous wandering or hostile-mob melee");

        ClientLevel cl = new ClientLevel();
        Minecraft mc = Minecraft.getInstance();
        mc.level = cl;
        mc.player = new LocalPlayer(cl);
        GuideNpc cnpc = new GuideNpc(NpcContent.guide(), cl);
        cl.addEntity(cnpc);

        NpcConfigScreen cfg = new NpcConfigScreen(cnpc.getId(), cnpc.profile(), new ArrayList<>());
        cfg.init(mc, 960, 540);
        // Switch to tab 5 (Mobility — 6th top tab button)
        java.util.List<Button> tabBtns254 = new ArrayList<>();
        for (Renderable r : cfg.renderables) {
            if (r instanceof Button b) tabBtns254.add(b);
        }
        ok(tabBtns254.size() >= 6, "NpcConfigScreen must create top tab buttons");
        tabBtns254.get(5).onClick(tabBtns254.get(5).getX() + 2, tabBtns254.get(5).getY() + 2);
        boolean hasAutoWander254 = false;
        for (Renderable r : cfg.renderables) {
            if (r instanceof AbstractWidget w && w.getMessage().getString().contains("auto_wander")) {
                hasAutoWander254 = true;
            }
        }
        ok(!hasAutoWander254, "Build 254 NpcConfigScreen did not have auto_wander widget");

        NpcDialogueGraphScreen gs = new NpcDialogueGraphScreen(cnpc.getId(), cnpc.profile(), cfg, 0);
        gs.init(mc, 960, 540);
        GuiGraphics gfx = new GuiGraphics();
        gs.renderBackground(gfx, 100, 100, 0f);
        boolean hasAutoCard254 = gfx.ops.stream().anyMatch(op -> op.detail.contains("auto_ai.title"));
        ok(!hasAutoCard254, "Build 254 NpcDialogueGraphScreen did not have Autonomous AI card");

        System.out.println("CONFIRMED build 254 baseline: no autonomous home-radius wander, no hostile-mob Ivy melee, no Auto-AI controls");
    }
}
''',
    'testing/AutoAi255Scenario.java': '''
package testing;

import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.IvyCombatAi;
import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcAutoAiEngine255;
import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcCopyPickPayload;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcEdit;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcStatsGraph;
import dev.settledlands.npc.client.NpcAutoAiGraphUi255;
import dev.settledlands.npc.client.NpcConfigScreen;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcUi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AutoAi255Scenario {
    private static void ok(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void tickServerNpc(GuideNpc npc, Method step) throws Exception {
        npc.tickCount++;
        step.invoke(npc);
        npc.getNavigation().stepMovement();
    }

    public static void main(String[] args) throws Exception {
        FMLPaths.currentGameDir = Path.of(args[0]);
        Path jarPath = Path.of(args[1]);
        Path outDir = Path.of(args[2]);
        Path autostubDir = Path.of(args[3]);

        Method step = GuideNpc.class.getDeclaredMethod("customServerAiStep");
        step.setAccessible(true);

        // =========================================================================
        // 1. Dedicated-server ClassLoader check (ZERO client class refs on server)
        // =========================================================================
        ClassLoader srvLoader = new URLClassLoader(
                new URL[]{outDir.toUri().toURL(), jarPath.toUri().toURL(), autostubDir.toUri().toURL()},
                ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("dev.settledlands.npc.client.")) {
                    throw new ClassNotFoundException("Dedicated server forbids client class: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };
        for (String clsName : new String[]{
                "dev.settledlands.npc.NpcAutoAi255",
                "dev.settledlands.npc.NpcAutoAiEngine255",
                "dev.settledlands.npc.NpcProfile",
                "dev.settledlands.npc.NpcDialogueGraph",
                "dev.settledlands.npc.NpcEdit",
                "dev.settledlands.npc.GuideNpc"
        }) {
            Class.forName(clsName, true, srvLoader);
        }

        // =========================================================================
        // 2. Default-off safety, radius clamping [1..64], and NBT roundtrip
        // =========================================================================
        NpcProfile defProf = new NpcProfile();
        ok(!NpcAutoAi255.isAutoWander(defProf) && !NpcAutoAi255.isAutoAttackHostiles(defProf),
                "autonomous AI must default to false so quest/shop NPCs never move or fight unexpectedly");
        ok(NpcAutoAi255.wanderRadius(defProf) == 10 && NpcAutoAi255.aggroRadius(defProf) == 12,
                "default radii must be 10 and 12");

        NpcAutoAi255.setWanderRadius(defProf, -10);
        NpcAutoAi255.setAggroRadius(defProf, 999);
        ok(NpcAutoAi255.wanderRadius(defProf) == 1 && NpcAutoAi255.aggroRadius(defProf) == 64,
                "radii must clamp strictly to [1..64]");

        defProf = NpcEdit.apply(defProf, "field", 0, 0, 0, "wanderRadius", "18");
        defProf = NpcEdit.apply(defProf, "field", 0, 0, 0, "wanderRadius", "not-a-number");
        ok(NpcAutoAi255.wanderRadius(defProf) == 18, "malformed radius edit must keep previous clamped value");

        // =========================================================================
        // 3. Non-GuideNpc isolation (Corpse, Trigger, Leashed, Passenger, Default-off)
        // =========================================================================
        ServerLevel sl = new ServerLevel();
        GuideNpc peaceful = new GuideNpc(NpcContent.guide(), sl);
        peaceful.setPos(10.5, 64.0, 10.5);
        sl.addEntity(peaceful);
        Zombie nearPeaceful = new Zombie(sl);
        nearPeaceful.setPos(12.5, 64.0, 10.5);
        sl.addEntity(nearPeaceful);
        for (int i = 0; i < 40; i++) tickServerNpc(peaceful, step);
        ok(peaceful.getNavigation().moveCalls == 0 && nearPeaceful.totalDamageTaken == 0f,
                "default-off GuideNpc must stand completely still and never attack");
        sl.removeEntity(nearPeaceful);

        GuideCorpseNpc corpse = new GuideCorpseNpc(NpcContent.guideCorpse(), sl);
        corpse.setPos(20.5, 64.0, 20.5);
        NpcAutoAi255.setAutoWander(corpse.profile(), true);
        NpcAutoAi255.setAutoAttackHostiles(corpse.profile(), true);
        sl.addEntity(corpse);

        GuideTriggerNpc trigger = new GuideTriggerNpc(NpcContent.guideTrigger(), sl);
        trigger.setPos(22.5, 64.0, 20.5);
        NpcAutoAi255.setAutoWander(trigger.profile(), true);
        NpcAutoAi255.setAutoAttackHostiles(trigger.profile(), true);
        sl.addEntity(trigger);

        Zombie nearSpecial = new Zombie(sl);
        nearSpecial.setPos(21.5, 64.0, 20.5);
        sl.addEntity(nearSpecial);
        for (int i = 0; i < 40; i++) {
            tickServerNpc(corpse, step);
            tickServerNpc(trigger, step);
        }
        ok(corpse.getNavigation().moveCalls == 0 && trigger.getNavigation().moveCalls == 0
                && nearSpecial.totalDamageTaken == 0f,
                "GuideCorpseNpc and GuideTriggerNpc must NEVER wander or attack");
        CompoundTag corpseSaved = corpse.saveWithoutId(new CompoundTag());
        ok(!corpseSaved.getCompound("Guide").contains(NpcAutoAiEngine255.TAG_HOME_SET),
                "GuideCorpseNpc must never persist AutoHomeSet");
        sl.removeEntity(nearSpecial);

        // =========================================================================
        // 4. Home-radius free wandering, hazard avoidance, NBT home persistence & return
        // =========================================================================
        GuideNpc wanderer = new GuideNpc(NpcContent.guide(), sl);
        wanderer.setPos(100.5, 64.0, 100.5);
        NpcProfile wp = wanderer.profile().copy();
        NpcAutoAi255.setAutoWander(wp, true);
        NpcAutoAi255.setWanderRadius(wp, 6);
        wanderer.setProfile(wp);
        sl.addEntity(wanderer);

        // Place hazards inside the radius to verify walkable filter rejects them
        sl.setBlock(new BlockPos(102, 63, 100), Blocks.LAVA);
        sl.setBlock(new BlockPos(103, 63, 100), Blocks.MAGMA_BLOCK);
        sl.setBlock(new BlockPos(99, 64, 101), Blocks.WATER);
        sl.setBlock(new BlockPos(98, 64, 100), Blocks.FIRE);
        sl.setBlock(new BlockPos(100, 64, 102), Blocks.CACTUS);

        double maxDistFromHome = 0.0;
        Set<BlockPos> chosenTargets = new HashSet<>();
        for (int i = 0; i < 360; i++) {
            tickServerNpc(wanderer, step);
            double dx = wanderer.getX() - 100.5;
            double dz = wanderer.getZ() - 100.5;
            double d = Math.sqrt(dx * dx + dz * dz);
            maxDistFromHome = Math.max(maxDistFromHome, d);
            if (!wanderer.getNavigation().isDone()) {
                BlockPos dest = BlockPos.containing(
                        wanderer.getNavigation().destX,
                        wanderer.getNavigation().destY,
                        wanderer.getNavigation().destZ
                );
                chosenTargets.add(dest);
                ok(!dest.equals(new BlockPos(102, 64, 100))
                        && !dest.equals(new BlockPos(103, 64, 100))
                        && !dest.equals(new BlockPos(99, 64, 101))
                        && !dest.equals(new BlockPos(98, 64, 100))
                        && !dest.equals(new BlockPos(100, 64, 102)),
                        "wanderer must never choose a hazardous destination: " + dest);
            }
        }
        ok(chosenTargets.size() >= 2 && maxDistFromHome >= 1.8 && maxDistFromHome <= 7.5,
                "wanderer must stroll across multiple spots inside radius=6 (targets="
                        + chosenTargets.size() + ", maxDist=" + maxDistFromHome + ")");
        ok(new BlockPos(100, 64, 100).equals(NpcAutoAiEngine255.of(wanderer).homePos()),
                "homePos must stay anchored at initial spawn (100,64,100)");

        // Tight radius=2 hazard-ring test: surround (300,64,300) with solid MAGMA_BLOCK floors on the west
        // and FIRE/SWEET_BERRY_BUSH airspace on the east, leaving only (300,64,302) safe!
        GuideNpc hazardProbe = new GuideNpc(NpcContent.guide(), sl);
        hazardProbe.setPos(300.5, 64.0, 300.5);
        NpcProfile hp = hazardProbe.profile().copy();
        NpcAutoAi255.setAutoWander(hp, true);
        NpcAutoAi255.setWanderRadius(hp, 2);
        hazardProbe.setProfile(hp);
        sl.addEntity(hazardProbe);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (dx == 0 && dz == 2) continue; // only safe destination!
                if (dx < 0) {
                    sl.setBlock(new BlockPos(300 + dx, 63, 300 + dz), Blocks.MAGMA_BLOCK); // solid hazardous floor!
                } else {
                    sl.setBlock(new BlockPos(300 + dx, 64, 300 + dz), Blocks.SWEET_BERRY_BUSH); // hazardous airspace over stone!
                }
            }
        }
        for (int i = 0; i < 120; i++) {
            tickServerNpc(hazardProbe, step);
            if (!hazardProbe.getNavigation().isDone()) {
                BlockPos d = BlockPos.containing(
                        hazardProbe.getNavigation().destX,
                        hazardProbe.getNavigation().destY,
                        hazardProbe.getNavigation().destZ
                );
                ok(d.equals(new BlockPos(300, 64, 302)),
                        "tight hazard ring must reject MAGMA_BLOCK floors and SWEET_BERRY_BUSH airspace, got " + d);
            }
        }
        sl.removeEntity(hazardProbe);

        // Move away from home, save NBT, reload into a fresh GuideNpc -> home (100,64,100) persists!
        wanderer.setPos(105.2, 64.0, 102.8);
        CompoundTag savedWanderer = wanderer.saveWithoutId(new CompoundTag());
        ok(savedWanderer.getCompound("Guide").getBoolean(NpcAutoAiEngine255.TAG_HOME_SET),
                "Guide NBT must persist AutoHomeSet=true");

        GuideNpc reloadedWanderer = new GuideNpc(NpcContent.guide(), sl);
        reloadedWanderer.setPos(105.2, 64.0, 102.8);
        reloadedWanderer.load(savedWanderer);
        sl.addEntity(reloadedWanderer);
        ok(new BlockPos(100, 64, 100).equals(NpcAutoAiEngine255.of(reloadedWanderer).homePos()),
                "reloaded GuideNpc must restore home anchor (100,64,100), got " + NpcAutoAiEngine255.of(reloadedWanderer).homePos());

        // Knock 45 blocks outside home radius (>28-block chunked step-back) -> must walk all the way back inside radius!
        reloadedWanderer.setPos(145.5, 64.0, 100.5);
        for (int i = 0; i < 260; i++) {
            tickServerNpc(reloadedWanderer, step);
        }
        double distAfterReturn = Math.hypot(reloadedWanderer.getX() - 100.5, reloadedWanderer.getZ() - 100.5);
        ok(distAfterReturn <= 6.2,
                "wanderer knocked 45 blocks away must navigate back within home radius=6, got dist=" + distAfterReturn);

        //Leashed wanderer stops autonomous actions; customMoveTarget updates home anchor
        reloadedWanderer.leashed = true;
        int callsBeforeLeash = reloadedWanderer.getNavigation().moveCalls;
        for (int i = 0; i < 30; i++) tickServerNpc(reloadedWanderer, step);
        ok(reloadedWanderer.getNavigation().moveCalls == callsBeforeLeash, "leashed GuideNpc must not auto-wander");
        reloadedWanderer.leashed = false;

        Field customMoveField = GuideNpc.class.getDeclaredField("customMoveTarget");
        customMoveField.setAccessible(true);
        reloadedWanderer.setPos(130.5, 64.0, 130.5);
        customMoveField.set(reloadedWanderer, new Vec3(130.5, 64.0, 130.5));
        tickServerNpc(reloadedWanderer, step);
        customMoveField.set(reloadedWanderer, null);
        ok(new BlockPos(130, 64, 130).equals(NpcAutoAiEngine255.of(reloadedWanderer).homePos()),
                "scripted customMoveTarget must re-anchor homePos to new scripted destination");

        // Disabling both flags clears home so re-enabling at a new location anchors at the new spot
        NpcProfile offProf = reloadedWanderer.profile().copy();
        NpcAutoAi255.setAutoWander(offProf, false);
        NpcAutoAi255.setAutoAttackHostiles(offProf, false);
        reloadedWanderer.setProfile(offProf);
        tickServerNpc(reloadedWanderer, step);
        ok(!NpcAutoAiEngine255.of(reloadedWanderer).hasHome(), "disabling both toggles must clear home anchor");
        reloadedWanderer.setPos(200.5, 64.0, 200.5);
        NpcAutoAi255.setAutoWander(offProf, true);
        reloadedWanderer.setProfile(offProf);
        tickServerNpc(reloadedWanderer, step);
        ok(new BlockPos(200, 64, 200).equals(NpcAutoAiEngine255.of(reloadedWanderer).homePos()),
                "re-enabling autoWander must anchor home at current position (200,64,200)");

        // Respect explicit wand mobility route when idleWalking + mobilityPoints are present
        NpcProfile routeProf = reloadedWanderer.profile().copy();
        routeProf.idleWalking = true;
        routeProf.mobilityPoints.add(new NpcProfile.MobilityPoint("204, 64, 200", 100, "2"));
        routeProf.mobilityPoints.add(new NpcProfile.MobilityPoint("200, 64, 204", 100, "2"));
        reloadedWanderer.setProfile(routeProf);
        ok(reloadedWanderer.settledlands$hasActiveMobilityRoute(), "mobilityPoints must report active mobility route");

        // =========================================================================
        // 5. Ivy Melee Combat (NO fireballs!), non-hostile safety, dodge, & return
        // =========================================================================
        ServerLevel combatLevel = new ServerLevel();
        GuideNpc guard = new GuideNpc(NpcContent.guide(), combatLevel);
        guard.setPos(50.5, 64.0, 50.5);
        NpcProfile gp = guard.profile().copy();
        gp.dialogueGraph = new NpcDialogueGraph();
        NpcStatsGraph.ensure(gp.dialogueGraph);
        NpcAutoAi255.setAutoWander(gp, false);
        NpcAutoAi255.setAutoAttackHostiles(gp, true);
        NpcAutoAi255.setAggroRadius(gp, 12);
        guard.setProfile(gp);
        combatLevel.addEntity(guard);

        // Place Player, bystander GuideNpc, peaceful Wolf inside aggroRadius, and far Zombie outside aggroRadius
        ServerPlayer bystanderPlayer = new ServerPlayer(combatLevel);
        bystanderPlayer.setPos(52.5, 64.0, 50.5);
        combatLevel.addEntity(bystanderPlayer);

        GuideNpc bystanderNpc = new GuideNpc(NpcContent.guide(), combatLevel);
        bystanderNpc.setPos(51.5, 64.0, 52.5);
        combatLevel.addEntity(bystanderNpc);

        Wolf peacefulWolf = new Wolf(combatLevel);
        peacefulWolf.setPos(53.5, 64.0, 51.5);
        combatLevel.addEntity(peacefulWolf);

        Zombie farZombie = new Zombie(combatLevel);
        farZombie.setPos(70.5, 64.0, 50.5); // dist = 20 > aggroRadius 12
        combatLevel.addEntity(farZombie);

        for (int i = 0; i < 40; i++) tickServerNpc(guard, step);
        ok(bystanderPlayer.totalDamageTaken == 0f
                && bystanderNpc.totalDamageTaken == 0f
                && peacefulWolf.totalDamageTaken == 0f
                && farZombie.totalDamageTaken == 0f
                && NpcAutoAiEngine255.of(guard).currentTarget() == null,
                "guard must NEVER attack players, GuideNpcs, peaceful animals, or out-of-radius enemies");
        combatLevel.removeEntity(farZombie);

        // Spawn hostile Zombie inside aggroRadius at (59.5, 64.0, 50.5) (9 blocks from guard post)
        IvyCombatAi.fireballCalls = 0;
        Zombie enemy = new Zombie(combatLevel);
        enemy.health = 40.0f;
        enemy.setPos(59.5, 64.0, 50.5);
        combatLevel.addEntity(enemy);

        Set<String> seenAnims = new HashSet<>();
        EnumSet<NpcAutoAiEngine255.MeleeCombo> seenCombos = EnumSet.noneOf(NpcAutoAiEngine255.MeleeCombo.class);
        double maxChaseDist = 0.0;
        for (int i = 0; i < 320 && enemy.isAlive(); i++) {
            tickServerNpc(guard, step);
            maxChaseDist = Math.max(maxChaseDist, Math.hypot(guard.getX() - 50.5, guard.getZ() - 50.5));
            String anim = guard.getCustomAnimName();
            if (anim != null && !anim.isEmpty()) seenAnims.add(anim);
            NpcAutoAiEngine255.MeleeCombo combo = NpcAutoAiEngine255.of(guard).currentCombo();
            if (combo != null) seenCombos.add(combo);
        }
        ok(!enemy.isAlive() && enemy.totalDamageTaken >= 40.0f,
                "guard must chase and kill hostile Zombie using Ivy melee combos, dealt=" + enemy.totalDamageTaken);
        ok(IvyCombatAi.fireballCalls == 0 && seenAnims.stream().noneMatch(a -> a.contains("fire") || a.contains("cast")),
                "autonomous NPC combat must use strictly melee combos WITHOUT fireballs, saw anims=" + seenAnims);
        ok(seenAnims.contains("orthodox_idle") && !seenCombos.isEmpty() && guard.swingCount >= 3,
                "expected orthodox_idle + Ivy melee combos + main-hand swings, saw anims=" + seenAnims + " combos=" + seenCombos);
        ok(maxChaseDist >= 5.0, "guard must have chased Zombie away from post before returning, maxChaseDist=" + maxChaseDist);
        combatLevel.removeEntity(enemy);

        // Tick post-combat: exit_orthodox -> "" animation reset -> walk back to (50.5, 64.0, 50.5) guard post!
        boolean sawExitOrthodox = false;
        for (int i = 0; i < 120; i++) {
            tickServerNpc(guard, step);
            if ("exit_orthodox".equals(guard.getCustomAnimName())) {
                sawExitOrthodox = true;
            }
        }
        double guardPostDist = Math.hypot(guard.getX() - 50.5, guard.getZ() - 50.5);
        ok(sawExitOrthodox && "".equals(guard.getCustomAnimName()) && guardPostDist <= 1.75,
                "after combat, guard must play exit_orthodox, clear custom animation, and return to home post (dist=" + guardPostDist + ")");

        // Neutral Wolf attacks guard -> guard retaliates AND rapid hits trigger dodge_left / dodge_right!
        Wolf angryWolf = new Wolf(combatLevel);
        angryWolf.health = 50.0f;
        angryWolf.setPos(51.8, 64.0, 50.5);
        angryWolf.setTarget(guard);
        combatLevel.addEntity(angryWolf);

        boolean sawDodge = false;
        for (int attempt = 0; attempt < 16 && !sawDodge; attempt++) {
            guard.setHealth(60.0f);
            guard.hurt(angryWolf.damageSources().mobAttack(angryWolf), 1.0f);
            guard.hurt(angryWolf.damageSources().mobAttack(angryWolf), 1.0f);
            if ("dodge_left".equals(guard.getCustomAnimName()) || "dodge_right".equals(guard.getCustomAnimName())) {
                sawDodge = true;
            }
            tickServerNpc(guard, step);
        }
        ok(NpcAutoAiEngine255.of(guard).currentTarget() == angryWolf && sawDodge,
                "guard must retaliate against attacking neutral mob and perform dodge_left/dodge_right under rapid hits");

        // Starting dialogue (setTalking(true) -> isTalking() == true) immediately pauses combat & clears animation!
        guard.setTalking(true);
        ok(guard.isTalking(), "guard must report isTalking() == true");
        tickServerNpc(guard, step);
        ok(NpcAutoAiEngine255.of(guard).currentTarget() == null && "".equals(guard.getCustomAnimName()),
                "starting player dialogue must immediately pause autonomous combat and reset animation");
        guard.setTalking(false);
        combatLevel.removeEntity(angryWolf);

        // =========================================================================
        // 6. Characteristics (NpcStatsGraph) Damage & Speed Scaling Verification
        // =========================================================================
        double[] lowMetrics = measureStatsScaling(5, 5, step);
        double[] highMetrics = measureStatsScaling(20, 20, step);
        double dmgRatio = highMetrics[0] / lowMetrics[0];
        double spdRatio = highMetrics[1] / lowMetrics[1];
        ok(Math.abs(dmgRatio - 4.0) < 0.02,
                "Damage=20 vs Damage=5 in NpcStatsGraph must scale melee hit damage 4.0x, got "
                        + highMetrics[0] + " / " + lowMetrics[0] + " = " + dmgRatio);
        ok(spdRatio > 2.5,
                "Speed=20 vs Speed=5 in NpcStatsGraph must scale chase speed, got "
                        + highMetrics[1] + " / " + lowMetrics[1] + " = " + spdRatio);

        // =========================================================================
        // 7. Two-way UI, Network (CfgEdit + CfgSave GraphOnly), Undo/Redo & Copy Egg
        // =========================================================================
        ClientLevel cl = new ClientLevel();
        Minecraft mc = Minecraft.getInstance();
        mc.level = cl;
        mc.player = new LocalPlayer(cl);

        ServerLevel syncSrv = new ServerLevel();
        ServerPlayer opPlayer = new ServerPlayer(syncSrv);
        opPlayer.permLevel = 2;
        syncSrv.addEntity(opPlayer);

        GuideNpc srvNpc = new GuideNpc(NpcContent.guide(), syncSrv);
        srvNpc.setPos(0.5, 64.0, 0.5);
        syncSrv.addEntity(srvNpc);

        GuideNpc cliNpc = new GuideNpc(NpcContent.guide(), cl);
        cliNpc.setId(srvNpc.getId());
        cl.addEntity(cliNpc);

        // 7a. NpcConfigScreen tab 0 preserves 3D preview at Z>=95 (scrollMax==0), tab 5 exposes Autonomous AI controls
        PacketDistributor.SENT_TO_SERVER.clear();
        NpcConfigScreen cfg = new NpcConfigScreen(cliNpc.getId(), srvNpc.profile().copy(), new ArrayList<>());
        mc.screen = cfg;
        cfg.init(mc, 960, 540);

        GuiGraphics tab0Gfx = new GuiGraphics();
        cfg.render(tab0Gfx, 200, 200, 0f);
        double tab0PreviewZ = tab0Gfx.ops.stream()
                .filter(op -> "fill".equals(op.kind) && "0xff335577".equals(op.detail))
                .mapToDouble(op -> op.z).max().orElse(0.0);
        ok(tab0PreviewZ >= 95.0, "Tab 0 (Персонаж) must keep 3D NPC preview visible at 960x540 (got previewZ=" + tab0PreviewZ + ")");

        // Click tab 5 ("mobility" — 6th top tab button)
        List<Button> topTabBtns = new ArrayList<>();
        for (Renderable r : cfg.renderables) {
            if (r instanceof Button b) topTabBtns.add(b);
        }
        ok(topTabBtns.size() >= 6, "Mobility tab button must exist on NpcConfigScreen");
        Button mobTabBtn = topTabBtns.get(5);
        mobTabBtn.onClick(mobTabBtn.getX() + 2, mobTabBtn.getY() + 2);

        Checkbox wanderCb = null;
        Checkbox attackCb = null;
        List<AbstractSliderButton> autoSliders = new ArrayList<>();
        for (Renderable r : cfg.renderables) {
            if (r instanceof Checkbox cb) {
                String msg = cb.getMessage().getString();
                if (msg.contains("auto_wander")) wanderCb = cb;
                else if (msg.contains("auto_attack")) attackCb = cb;
            } else if (r instanceof AbstractSliderButton slBtn && slBtn.getMessage().getString().contains("hint.blocks")) {
                autoSliders.add(slBtn);
            }
        }
        ok(wanderCb != null && attackCb != null && autoSliders.size() == 2,
                "Mobility tab must display both Autonomous AI checkboxes and both 1..64 radius sliders");
        ok(!wanderCb.selected() && !attackCb.selected(), "Autonomous AI checkboxes must start unchecked");

        // Toggle both checkboxes & set sliders to 16 and 24 -> verify local profile AND CfgEdit packets -> NpcEdit.apply!
        wanderCb.onClick(wanderCb.getX() + 2, wanderCb.getY() + 2);
        attackCb.onClick(attackCb.getX() + 2, attackCb.getY() + 2);
        autoSliders.get(0).setNormalizedValue((16.0 - 1.0) / 63.0);
        autoSliders.get(1).setNormalizedValue((24.0 - 1.0) / 63.0);

        ok(NpcAutoAi255.isAutoWander(cfg.settledlands$profile())
                && NpcAutoAi255.isAutoAttackHostiles(cfg.settledlands$profile())
                && NpcAutoAi255.wanderRadius(cfg.settledlands$profile()) == 16
                && NpcAutoAi255.aggroRadius(cfg.settledlands$profile()) == 24,
                "NpcConfigScreen widgets must immediately update local profile");

        // Apply every emitted CfgEdit packet to server NPC via real GuideNpc.applyEdit
        int cfgEditCount = 0;
        for (CustomPacketPayload pkt : PacketDistributor.SENT_TO_SERVER) {
            if (pkt instanceof NpcNet.CfgEdit ce) {
                cfgEditCount++;
                GuideNpc.applyEdit(opPlayer, ce.entityId(), ce.op(), ce.a(), ce.b(), ce.c(), ce.key(), ce.value());
            }
        }
        ok(cfgEditCount >= 4
                && NpcAutoAi255.isAutoWander(srvNpc.profile())
                && NpcAutoAi255.isAutoAttackHostiles(srvNpc.profile())
                && NpcAutoAi255.wanderRadius(srvNpc.profile()) == 16
                && NpcAutoAi255.aggroRadius(srvNpc.profile()) == 24,
                "CfgEdit packets from NpcConfigScreen must update server NpcProfile via NpcEdit.apply");

        // 7b. Open NpcDialogueGraphScreen from NpcConfigScreen -> verify two-way sync, zero-shadow Z, capacity, undo/redo, drag, and GraphOnly save!
        NpcDialogueGraphScreen graphScr = new NpcDialogueGraphScreen(
                cliNpc.getId(), cfg.settledlands$profile(), cfg, 0
        );
        mc.screen = graphScr;
        graphScr.init(mc, 960, 540);

        NpcDialogueGraph mainGraph = graphScr.settledlands$graph();
        NpcAutoAi255.GraphState gs = NpcAutoAi255.graphState(mainGraph);
        ok(gs.synced && gs.autoWander && gs.autoAttackHostiles && gs.wanderRadius == 16 && gs.aggroRadius == 24,
                "NpcDialogueGraphScreen must inherit all 4 settings from NpcConfigScreen");

        NpcDialogueGraph.Node statsNode = NpcStatsGraph.find(mainGraph);
        ok(statsNode != null && gs.cardX == statsNode.x && gs.cardY == statsNode.y + 186,
                "Autonomous AI card must default directly below Characteristics node (-360, 186), got (" + gs.cardX + "," + gs.cardY + ")");

        // Zero-shadow Z check on NpcAutoAiGraphUi255.draw
        NpcGraphCanvas canvas = graphScr.settledlands$canvas();
        canvas.frameAll(mainGraph);
        ok(NpcAutoAiGraphUi255.visible(canvas, gs), "frameAll must frame the Autonomous AI card inside the viewport");

        GuiGraphics cardGfx = new GuiGraphics();
        NpcAutoAiGraphUi255.draw(graphScr, cardGfx);
        ok(!cardGfx.ops.isEmpty()
                && cardGfx.ops.stream().anyMatch(op -> op.detail.contains("auto_ai.title"))
                && cardGfx.ops.stream().anyMatch(op -> op.detail.contains("auto_ai.wander"))
                && cardGfx.ops.stream().anyMatch(op -> op.detail.contains("auto_ai.attack")),
                "Autonomous AI card draw must emit title, wander, and attack rows");
        ok(cardGfx.pose().depth == 0 && cardGfx.centeredStringCount == 0 && cardGfx.shadowStringCount == 0,
                "Autonomous AI card must emit ZERO shadowed/centered strings so overlapping panels cover it cleanly");
        ok(cardGfx.ops.stream().allMatch(op -> op.z == 0.0),
                "all Autonomous AI card draw ops must stay at Z=0.0");

        GuiGraphics fullBgGfx = new GuiGraphics();
        graphScr.renderBackground(fullBgGfx, 100, 100, 0f);
        ok(fullBgGfx.ops.stream().anyMatch(op -> op.detail.contains("auto_ai.title")),
                "NpcDialogueGraphScreen.renderBackground must render the Autonomous AI card");

        // Click wander toggle OFF, click wanderRadius '+' with Shift (+5 -> 21), click aggroRadius '-' (-1 -> 23)
        double wanderToggleMx = canvas.screenX(gs.cardX + NpcAutoAiGraphUi255.TOGGLE_X + 10);
        double wanderToggleMy = canvas.screenY(gs.cardY + NpcAutoAiGraphUi255.ROW_WANDER_Y + 6);
        ok(graphScr.mouseClicked(wanderToggleMx, wanderToggleMy, 0), "clicking wander toggle on graph card");
        ok(!gs.autoWander, "clicking wander toggle must flip autoWander to false");

        Screen.shiftHeld = true;
        double wanderPlusMx = canvas.screenX(gs.cardX + NpcAutoAiGraphUi255.PLUS_X + 6);
        double wanderPlusMy = canvas.screenY(gs.cardY + NpcAutoAiGraphUi255.ROW_WANDER_R_Y + 6);
        ok(graphScr.mouseClicked(wanderPlusMx, wanderPlusMy, 0), "Shift-clicking wanderRadius '+'");
        Screen.shiftHeld = false;
        ok(gs.wanderRadius == 21, "Shift-clicking '+' must increment wanderRadius by 5 (16 -> 21), got " + gs.wanderRadius);

        double aggroMinusMx = canvas.screenX(gs.cardX + NpcAutoAiGraphUi255.MINUS_X + 6);
        double aggroMinusMy = canvas.screenY(gs.cardY + NpcAutoAiGraphUi255.ROW_AGGRO_R_Y + 6);
        ok(graphScr.mouseClicked(aggroMinusMx, aggroMinusMy, 0), "clicking aggroRadius '-'");
        ok(gs.aggroRadius == 23, "clicking '-' must decrement aggroRadius (24 -> 23), got " + gs.aggroRadius);

        // Drag card header by (-40, +30) in world space
        int oldCardX = gs.cardX, oldCardY = gs.cardY;
        double dragStartMx = canvas.screenX(oldCardX + 20);
        double dragStartMy = canvas.screenY(oldCardY + 10);
        double dragEndMx = canvas.screenX(oldCardX - 20);
        double dragEndMy = canvas.screenY(oldCardY + 40);
        ok(graphScr.mouseClicked(dragStartMx, dragStartMy, 0), "clicking card header to start drag");
        ok(graphScr.mouseDragged(dragEndMx, dragEndMy, 0, dragEndMx - dragStartMx, dragEndMy - dragStartMy), "dragging card");
        ok(graphScr.mouseReleased(dragEndMx, dragEndMy, 0), "releasing dragged card");
        ok(gs.cardX == oldCardX - 40 && gs.cardY == oldCardY + 30,
                "card drag must move card to (" + (oldCardX - 40) + "," + (oldCardY + 30) + "), got (" + gs.cardX + "," + gs.cardY + ")");

        // Undo & Redo via real NpcDialogueGraphScreen.history(boolean)
        Method histMethod = NpcDialogueGraphScreen.class.getDeclaredMethod("history", boolean.class);
        histMethod.setAccessible(true);
        histMethod.invoke(graphScr, false); // undo drag
        NpcAutoAi255.GraphState afterUndoDrag = NpcAutoAi255.graphState(graphScr.settledlands$graph());
        ok(afterUndoDrag.cardX == oldCardX && afterUndoDrag.cardY == oldCardY && afterUndoDrag.aggroRadius == 23,
                "Ctrl+Z must undo card drag first");
        histMethod.invoke(graphScr, false); // undo aggroRadius 24 -> 23
        ok(NpcAutoAi255.graphState(graphScr.settledlands$graph()).aggroRadius == 24, "Ctrl+Z must undo aggroRadius edit");
        histMethod.invoke(graphScr, true);  // redo aggroRadius -> 23
        histMethod.invoke(graphScr, true);  // redo card drag
        NpcAutoAi255.GraphState afterRedo = NpcAutoAi255.graphState(graphScr.settledlands$graph());
        ok(afterRedo.aggroRadius == 23 && afterRedo.cardX == oldCardX - 40, "Ctrl+Y must redo edits and drag");

        // Save from NpcDialogueGraphScreen -> real CfgSave (GraphOnly=true) -> GuideNpc.applyConfig -> CfgView -> applySnapshot!
        PacketDistributor.SENT_TO_SERVER.clear();
        PacketDistributor.SENT_TO_CLIENT.clear();
        Method saveMethod = NpcDialogueGraphScreen.class.getDeclaredMethod("save");
        saveMethod.setAccessible(true);
        ok((Boolean) saveMethod.invoke(graphScr), "NpcDialogueGraphScreen.save() must succeed");

        NpcNet.CfgSave cfgSave = PacketDistributor.SENT_TO_SERVER.stream()
                .filter(p -> p instanceof NpcNet.CfgSave)
                .map(p -> (NpcNet.CfgSave) p)
                .findFirst().orElseThrow(() -> new AssertionError("CfgSave not sent"));
        ok(cfgSave.data().getBoolean("GraphOnly"), "Graph save must use GraphOnly=true");
        GuideNpc.applyConfig(opPlayer, srvNpc.getId(), cfgSave.data());

        ok(!NpcAutoAi255.isAutoWander(srvNpc.profile())
                && NpcAutoAi255.isAutoAttackHostiles(srvNpc.profile())
                && NpcAutoAi255.wanderRadius(srvNpc.profile()) == 21
                && NpcAutoAi255.aggroRadius(srvNpc.profile()) == 23,
                "GraphOnly save must sync Auto-AI settings onto server GuideNpc.profile()");

        NpcNet.CfgView cfgView = PacketDistributor.SENT_TO_CLIENT.stream()
                .filter(p -> p instanceof NpcNet.CfgView)
                .map(p -> (NpcNet.CfgView) p)
                .findFirst().orElseThrow(() -> new AssertionError("CfgView snapshot not sent back"));
        NpcProfile snapProfile = NpcProfile.load(cfgView.data().getCompound("Profile"), RegistryAccess.EMPTY);
        graphScr.applySnapshot(snapProfile);

        // Close NpcDialogueGraphScreen -> parent NpcConfigScreen.acceptGraphProfile updates checkboxes & sliders!
        graphScr.onClose();
        ok(!NpcAutoAi255.isAutoWander(cfg.settledlands$profile())
                && NpcAutoAi255.isAutoAttackHostiles(cfg.settledlands$profile())
                && NpcAutoAi255.wanderRadius(cfg.settledlands$profile()) == 21
                && NpcAutoAi255.aggroRadius(cfg.settledlands$profile()) == 23,
                "closing NpcDialogueGraphScreen must sync updated Auto-AI values back to NpcConfigScreen");

        // 7c. NPC Pick-Copy Egg preserves all Auto-AI settings & spawns an independent copy
        Method buildEgg = NpcCopyPickPayload.class.getDeclaredMethod("buildCopyEgg", GuideNpc.class);
        buildEgg.setAccessible(true);
        ItemStack copyEgg = (ItemStack) buildEgg.invoke(null, srvNpc);
        GuideNpc spawnedCopy = NpcContent.guide().spawnFromStack(syncSrv, copyEgg, opPlayer);
        ok(!NpcAutoAi255.isAutoWander(spawnedCopy.profile())
                && NpcAutoAi255.isAutoAttackHostiles(spawnedCopy.profile())
                && NpcAutoAi255.wanderRadius(spawnedCopy.profile()) == 21
                && NpcAutoAi255.aggroRadius(spawnedCopy.profile()) == 23,
                "NPC copy egg must preserve all 4 Autonomous AI settings on the spawned copy");
        NpcAutoAi255.setWanderRadius(spawnedCopy.profile(), 7);
        ok(NpcAutoAi255.wanderRadius(srvNpc.profile()) == 21, "copied NPC Auto-AI state must be independent from source NPC");

        System.out.println("PASS build 255 Auto-AI: home-radius wander + hazard filter + NBT anchor + return, "
                + "Ivy melee combos & dodges (0 fireballs) + Stats Damage/Speed 4.0x scaling, "
                + "two-way NpcConfigScreen <-> NpcDialogueGraphScreen sync + GraphOnly save + undo/redo + NPC copy");
    }

    private static double[] measureStatsScaling(int dmgStat, int spdStat, Method step) throws Exception {
        ServerLevel lvl = new ServerLevel();
        GuideNpc fighter = new GuideNpc(NpcContent.guide(), lvl);
        fighter.setPos(0.5, 64.0, 0.5);
        NpcProfile p = fighter.profile().copy();
        p.dialogueGraph = new NpcDialogueGraph();
        NpcStatsGraph.ensure(p.dialogueGraph);
        NpcDialogueGraph.Node sNode = NpcStatsGraph.find(p.dialogueGraph);
        sNode.checkRadius = dmgStat;    // Damage
        sNode.checkCooldown = spdStat;  // Speed
        NpcAutoAi255.setAutoAttackHostiles(p, true);
        NpcAutoAi255.setAggroRadius(p, 16);
        fighter.setProfile(p);
        lvl.addEntity(fighter);

        // 1. Measure chase navigation speed against a Zombie 8 blocks away
        Zombie farTarget = new Zombie(lvl);
        farTarget.health = 200.0f;
        farTarget.setPos(8.5, 64.0, 0.5);
        lvl.addEntity(farTarget);
        fighter.getRandom().setSeed(999L); // deterministic roll (no dash lunge on first tick)
        step.invoke(fighter);
        double chaseSpeed = fighter.getNavigation().lastSpeed;

        // 2. Measure deterministic JAB hit damage at melee distance (1.2 blocks -> roll=0 -> JAB)
        farTarget.setPos(1.7, 64.0, 0.5);
        farTarget.totalDamageTaken = 0f;
        fighter.getRandom().setSeed(42L);
        for (int i = 0; i < 12 && farTarget.totalDamageTaken == 0f; i++) {
            fighter.tickCount++;
            step.invoke(fighter);
        }
        ok(farTarget.totalDamageTaken > 0f, "fighter must land melee hit in scaling probe");
        return new double[]{farTarget.totalDamageTaken, chaseSpeed};
    }
}
''',
}


def make_fixture(java: pathlib.Path, jar: pathlib.Path, gson_source: pathlib.Path, tmp: pathlib.Path):
    joml_out = tmp / 'joml'
    build_joml248.build(java, joml_out)
    gson_out = ui254.build_gson(java, gson_source, tmp)

    fake = tmp / 'auto'
    fake.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, TMPDIR=str(fake))
    targets = [
        'dev/settledlands/npc/NpcAutoAi255#isAutoWander(dev.settledlands.npc.NpcProfile)',
        'dev/settledlands/npc/NpcAutoAiEngine255#runMobilityAndAutoAi(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
        'dev/settledlands/npc/NpcContent#spawnEgg',
        'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
        'dev/settledlands/npc/IvyCombatAi#launchFireball',
        'dev/settledlands/npc/GuideTriggerNpc#isInvisible',
        'dev/settledlands/npc/GuideCorpseNpc#isCoffin',
        'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
        'dev/settledlands/npc/client/NpcClient#setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent)',
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

    merged_overrides = dict(nc253.OVERRIDES)
    merged_overrides.pop('testing/NpcCopy253Scenario.java', None)
    merged_overrides.update(ui254.EXTRA_OVERRIDES)
    merged_overrides.pop('testing/UiIvy254Scenario.java', None)
    merged_overrides.update(AUTO_AI_OVERRIDES)

    src_dir = tmp / 'src'
    out = tmp / 'out'
    out.mkdir()
    sources = []
    for rel, code in merged_overrides.items():
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
    for extra in (joml_out, gson_out):
        for cf in extra.rglob('*.class'):
            dest = out / cf.relative_to(extra)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cf, dest)
    return out, autostub


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--gson-source', required=True, type=pathlib.Path)
    q.add_argument('--base-jar', type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    gson = a.gson_source.resolve()

    with tempfile.TemporaryDirectory(prefix='autoai255-') as d:
        tmp = pathlib.Path(d)
        out, autostub = make_fixture(java, jar, gson, tmp)

        if a.base_jar is not None:
            base_jar = a.base_jar.resolve()
            game254 = tmp / 'game254'
            game254.mkdir()
            cp_base = ':'.join(map(str, [out, base_jar, autostub]))
            r = subprocess.run(
                [str(java), '-Xverify:all', '-cp', cp_base, 'testing.AutoAiBase254Check', str(game254)],
                capture_output=True, text=True,
            )
            assert r.returncode == 0 and 'CONFIRMED build 254 baseline:' in r.stdout, (r.stdout, r.stderr)
            print(r.stdout.strip(), flush=True)

        game255 = tmp / 'game255'
        game255.mkdir()
        cp = ':'.join(map(str, [out, jar, autostub]))
        r = subprocess.run(
            [str(java), '-Xverify:all', '-cp', cp, 'testing.AutoAi255Scenario',
             str(game255), str(jar), str(out), str(autostub)],
            capture_output=True, text=True,
        )
        assert r.returncode == 0 and 'PASS build 255 Auto-AI:' in r.stdout, (r.stdout, r.stderr)
        print(r.stdout.strip(), flush=True)


if __name__ == '__main__':
    main()
