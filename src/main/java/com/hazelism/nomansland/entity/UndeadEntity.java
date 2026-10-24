package com.hazelism.nomansland.entity;

import com.hazelism.nomansland.entity.ai.EncirclementGoal;
import com.hazelism.nomansland.entity.ai.HordeClimbGoal;
import com.hazelism.nomansland.entity.ai.InvestigateGoal;
import com.hazelism.nomansland.entity.ai.SenseGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Port of Bedrock {@code bp:undead} (HP 40, damage 3, follow range 20), running
 * the corrupted illager model, plus the Horde Mind behaviour layer:
 *
 * <ul>
 *   <li><b>Senses</b> - {@link SenseGoal}: line-of-sight acquisition of moving prey,
 *       smell points are only investigated (stealth belongs to external mods).</li>
 *   <li><b>Encirclement</b> - {@link EncirclementGoal}: undead sharing a target take
 *       slots around it instead of queueing up single-file.</li>
 *   <li><b>Alert wave</b> - a hunting undead periodically points nearby pack members
 *       at its prey's position; they investigate, then acquire on sight.</li>
 *   <li><b>Separation</b> - gentle push-apart inside the crowd so the horde reads as
 *       a mass of bodies, not one stacked blob (suspended while piling up to climb).</li>
 *   <li><b>Pack rage</b> - when an undead dies, nearby pack members speed up and
 *       turn on the killer.</li>
 *   <li><b>Horde Climbing</b> - {@link HordeClimbGoal}: pile up under elevated prey.</li>
 * </ul>
 *
 * Death is vanilla: the corpse system from earlier versions was removed by
 * request - loot comes from the standard entity loot table.
 *
 * <p>Performance: every horde query is throttled (5-20 ticks) and staggered by
 * entity id; nothing here scans per tick or allocates on hot paths.
 */
public class UndeadEntity extends Monster implements GeoEntity {

    public static final double FOLLOW_RANGE = 20.0D;
    private static final float LIMB_BREAK_CHANCE = 0.25F;
    private static final int INVESTIGATE_MEMORY_TICKS = 10 * 20;

    /** 0 = intact, 1 = shortened (short_left_arm), 2 = stump (left_arm_stomp). */
    private static final EntityDataAccessor<Integer> DATA_LEFT_ARM =
            SynchedEntityData.defineId(UndeadEntity.class, EntityDataSerializers.INT);

    // The corrupted illager model ships a single walk cycle; idle is the bind pose.
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.corrupted_illager.walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Server-side only investigation memory (never synced - the client does not need it).
    @Nullable
    private BlockPos investigatePos;
    private long investigateExpiryTick;

    public UndeadEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Values from the Bedrock original: health 40, attack 3, follow_range 20.
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEFT_ARM, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HordeClimbGoal(this)); // preempts others while prey is out of reach above
        this.goalSelector.addGoal(2, new EncirclementGoal(this)); // take a slot around the prey...
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1D, false)); // ...then strike from it
        this.goalSelector.addGoal(5, new InvestigateGoal(this));
        this.goalSelector.addGoal(6, new BreakDoorGoal(this, difficulty -> difficulty == Difficulty.HARD));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SenseGoal(this));
    }

    // ------------------------------------------------------------------ Horde Mind

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        HordeClimbGoal.applyPileSupport(this, this.getTarget());
        sharePreyWithPack();
        separateFromPack();
    }

    /** Alert wave: pack members without a target are pointed at our prey's position. */
    private void sharePreyWithPack() {
        LivingEntity target = this.getTarget();
        if (target == null || (this.tickCount + this.getId()) % 20 != 0) {
            return;
        }
        for (UndeadEntity mate : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(10.0D, 4.0D, 10.0D),
                u -> u != this && u.isAlive() && u.getTarget() == null)) {
            mate.setInvestigatePos(target.blockPosition());
        }
    }

    /** Gentle push-apart so the horde does not collapse into a single stacked blob. */
    private void separateFromPack() {
        LivingEntity target = this.getTarget();
        if ((this.tickCount + this.getId()) % 5 != 0
                || (target != null && target.getY() > this.getY() + 1.2D)) {
            return; // suspended while piling up to climb
        }
        double pushX = 0.0D;
        double pushZ = 0.0D;
        for (UndeadEntity mate : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(0.9D, 0.5D, 0.9D),
                u -> u != this && u.isAlive())) {
            double dx = this.getX() - mate.getX();
            double dz = this.getZ() - mate.getZ();
            double distSqr = dx * dx + dz * dz;
            if (distSqr > 0.0001D && distSqr < 0.81D) {
                double scale = 0.03D / distSqr;
                pushX += dx * scale;
                pushZ += dz * scale;
            }
        }
        if (pushX != 0.0D || pushZ != 0.0D) {
            var movement = this.getDeltaMovement();
            this.setDeltaMovement(movement.x + pushX, movement.y, movement.z + pushZ);
        }
    }

    /** Pack rage: our death enrages nearby pack members and turns them on the killer. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level().isClientSide || !(source.getEntity() instanceof LivingEntity killer) || killer == this) {
            return;
        }
        for (UndeadEntity mate : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(12.0D, 5.0D, 12.0D),
                u -> u != this && u.isAlive())) {
            mate.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10 * 20, 0, false, true));
            if (mate.getTarget() == null) {
                mate.setTarget(killer);
            }
        }
    }

    // ------------------------------------------------------------------ combat feedback

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            onHitEffects(source);
        }
        return hit;
    }

    /** Limb breaking + pack anger, from the Bedrock original. */
    private void onHitEffects(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player || attacker instanceof IronGolem) {
            // Original: every such hit rolls let_it_broke over all limbs. Only the left
            // arm has model variants, so it advances one break stage with a tuned chance.
            if (this.getRandom().nextFloat() < LIMB_BREAK_CHANCE) {
                int stage = this.getLeftArmStage();
                if (stage < 2) {
                    this.entityData.set(DATA_LEFT_ARM, stage + 1);
                }
            }
        }
        if (attacker instanceof LivingEntity living && living != this) {
            // minecraft:angry broadcast_anger: nearby undead join the fight.
            for (UndeadEntity ally : this.level().getEntitiesOfClass(UndeadEntity.class,
                    this.getBoundingBox().inflate(10.0D), u -> u != this && u.isAlive() && u.getTarget() == null)) {
                ally.setTarget(living);
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return this.horizontalCollision && this.getTarget() != null;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false; // original damage_sensor negates fall damage
    }

    // ------------------------------------------------------------------ investigation memory

    public void setInvestigatePos(BlockPos pos) {
        this.investigatePos = pos.immutable();
        this.investigateExpiryTick = this.level().getGameTime() + INVESTIGATE_MEMORY_TICKS;
    }

    @Nullable
    public BlockPos getInvestigatePos() {
        return this.investigatePos;
    }

    public boolean hasFreshInvestigatePos() {
        return this.investigatePos != null && this.level().getGameTime() <= this.investigateExpiryTick;
    }

    public void clearInvestigatePos() {
        this.investigatePos = null;
    }

    // ------------------------------------------------------------------ limbs

    public int getLeftArmStage() {
        return this.entityData.get(DATA_LEFT_ARM);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        // Original entity_spawned rolls one random broken limb; only the left arm is
        // modelled, so a 1-in-6 roll starts the mob with a shortened left arm.
        if (this.getRandom().nextInt(6) == 0) {
            this.entityData.set(DATA_LEFT_ARM, 1);
        }
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LeftArmStage", this.getLeftArmStage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_LEFT_ARM, Math.min(2, Math.max(0, tag.getInt("LeftArmStage"))));
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animationPredicate));
    }

    /** Runs on the client only, every frame - keep it allocation-free. */
    protected PlayState animationPredicate(AnimationState<UndeadEntity> state) {
        // No idle clip in the model: STOP leaves the bind pose untouched.
        return state.isMoving() ? state.setAndContinue(WALK) : PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------ Spawning

    /** Vanilla monster light/ground rules; moon-phase gating will narrow this later. */
    public static boolean checkUndeadSpawnRules(EntityType<UndeadEntity> type, ServerLevelAccessor level,
                                                MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }
}
