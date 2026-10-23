package com.hazelism.nomansland.entity;

import com.hazelism.nomansland.entity.ai.HordeClimbGoal;
import com.hazelism.nomansland.entity.ai.InvestigateGoal;
import com.hazelism.nomansland.entity.ai.SenseGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Port of Bedrock {@code bp:undead} (HP 40, damage 3, follow range 20).
 *
 * <p>Behaviour ported so far: smell/sight targeting of moving, non-sneaking prey
 * ({@link SenseGoal}), investigating smelled spots ({@link InvestigateGoal}),
 * wall climbing while chasing, pack anger broadcast on being attacked,
 * a breakable left arm (the only limb the corrupted illager model has variants
 * for) and the corpse system: at HP &lt;= 20 the mob collapses for 90s and can
 * be looted by a sneaking player, exactly like the original's undead:dead state.
 *
 * <p>No per-tick allocations: animations are static constants, scans are
 * throttled to once per 10 ticks and only run while targetless.
 */
public class UndeadEntity extends Monster implements GeoEntity {

    public static final double FOLLOW_RANGE = 20.0D;
    /** Below this the mob does not die - it becomes a lootable corpse (original: health<=20). */
    private static final float CORPSE_THRESHOLD = 20.0F;
    private static final int CORPSE_LIFETIME_TICKS = 90 * 20;
    private static final int INVESTIGATE_MEMORY_TICKS = 10 * 20;
    private static final float LIMB_BREAK_CHANCE = 0.25F;

    private static final EntityDataAccessor<Boolean> DATA_CORPSE =
            SynchedEntityData.defineId(UndeadEntity.class, EntityDataSerializers.BOOLEAN);
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
    private int corpseTicks;

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
        builder.define(DATA_CORPSE, false);
        builder.define(DATA_LEFT_ARM, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HordeClimbGoal(this)); // preempts melee while prey is out of reach above
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false)); // original melee speed_multiplier 1.1
        this.goalSelector.addGoal(5, new InvestigateGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SenseGoal(this));
    }

    // ------------------------------------------------------------------ corpse system

    public boolean isCorpse() {
        return this.entityData.get(DATA_CORPSE);
    }

    private void enterCorpse() {
        this.entityData.set(DATA_CORPSE, true);
        this.corpseTicks = 0;
        this.setTarget(null);
        this.clearInvestigatePos();
        this.getNavigation().stop();
        // Hard immobilisation. Note: overriding customServerAiStep is NOT enough -
        // NeoForge calls it after the goal/target selectors have already ticked,
        // so a corpse kept turning towards players and even attacking. NoAi stops
        // goal, look and move control entirely; the corpse timer runs in tick().
        this.setNoAi(true);
        this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, 0.0D), 0.0D);
        this.setYHeadRot(this.getYRot());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isCorpse()) {
            // Corpses take no damage (original damage_sensor deals_damage=false);
            // admin/kill damage types still work.
            return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
        }
        if (!this.level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.getHealth() > CORPSE_THRESHOLD && this.getHealth() - amount <= CORPSE_THRESHOLD) {
            // Cap the blow so the mob collapses instead of dying, like the original.
            boolean hit = super.hurt(source, this.getHealth() - CORPSE_THRESHOLD);
            if (hit) {
                this.setHealth(CORPSE_THRESHOLD);
                onHitEffects(source);
                enterCorpse();
            }
            return hit;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            onHitEffects(source);
        }
        return hit;
    }

    /** Limb breaking + pack anger, shared by the normal and corpse-threshold hit paths. */
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
            broadcastAnger(living);
        }
    }

    /** Original minecraft:angry with broadcast_anger: nearby undead join the fight. */
    private void broadcastAnger(LivingEntity attacker) {
        for (UndeadEntity ally : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(10.0D), u -> u != this && !u.isCorpse() && u.getTarget() == null)) {
            ally.setTarget(attacker);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (this.isCorpse()) {
            return; // unreachable in practice (NoAi skips AI), kept as a safety net
        }
        super.customServerAiStep();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return !this.isCorpse() && super.doHurtTarget(target); // corpses never attack
    }

    @Override
    public boolean isPushable() {
        return !this.isCorpse() && super.isPushable(); // original undead:dead is not pushable
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.isCorpse()) {
            // Corpses saved by older versions may not have NoAi set yet.
            if (!this.isNoAi()) {
                this.setNoAi(true);
            }
            if (++this.corpseTicks >= CORPSE_LIFETIME_TICKS) {
                this.discard(); // original: 90s timer -> despawn
            }
            return;
        }
        supportOnHorde();
    }

    /**
     * Pile support for Horde Climbing: undead are not solid blocks in Java, so a
     * climber would sink through the mob below. If another undead's head is right
     * under our feet while we are falling, cancel the fall - the pile behaves like
     * a soft, writhing staircase. Throttled to every 3 ticks (staggered per entity)
     * and only relevant in a horde situation, so the spatial query stays cheap.
     */
    private void supportOnHorde() {
        if (this.onGround() || this.getTarget() == null || (this.tickCount + this.getId()) % 3 != 0) {
            return;
        }
        var movement = this.getDeltaMovement();
        if (movement.y >= 0.0D) {
            return;
        }
        for (UndeadEntity mate : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(0.3D, 0.6D, 0.3D),
                u -> u != this && !u.isCorpse() && u.isAlive())) {
            double mateHead = mate.getY() + mate.getBbHeight();
            double feet = this.getY();
            if (feet > mateHead - 0.75D && feet < mateHead + 0.25D) {
                this.setDeltaMovement(movement.x, 0.0D, movement.z);
                this.fallDistance = 0.0F;
                return;
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

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isCorpse() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                spawnCorpseLoot();
                this.discard(); // original: drop_loot -> 0.1s despawn timer
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** undead_surprise loot table from the original: junk + always rotten flesh. */
    private void spawnCorpseLoot() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        net.minecraft.world.item.Item junk = switch (this.getRandom().nextInt(4)) {
            case 0 -> Items.STICK;
            case 1 -> Items.STRING;
            case 2 -> Items.LEATHER;
            default -> Items.BONE;
        };
        dropItem(serverLevel, new ItemStack(junk));
        dropItem(serverLevel, new ItemStack(Items.ROTTEN_FLESH));
    }

    private void dropItem(ServerLevel level, ItemStack stack) {
        ItemEntity drop = new ItemEntity(level, this.getX(), this.getY() + 0.5D, this.getZ(), stack);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
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
        tag.putBoolean("Corpse", this.isCorpse());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_LEFT_ARM, Math.min(2, Math.max(0, tag.getInt("LeftArmStage"))));
        if (tag.getBoolean("Corpse")) {
            this.entityData.set(DATA_CORPSE, true);
            this.setNoAi(true);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animationPredicate));
    }

    /** Runs on the client only, every frame - keep it allocation-free. */
    protected PlayState animationPredicate(AnimationState<UndeadEntity> state) {
        if (this.isCorpse()) {
            return PlayState.STOP; // lying flat is done by the renderer's corpse rotation
        }
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
