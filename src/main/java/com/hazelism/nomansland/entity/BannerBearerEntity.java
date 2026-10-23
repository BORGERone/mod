package com.hazelism.nomansland.entity;

import com.hazelism.nomansland.entity.ai.BearerEscortGoal;
import com.hazelism.nomansland.entity.ai.RallyCastGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The banner bearer ("flag zombie"): a cowardly support undead. It never attacks.
 * It trails the player at mid-range, flees when approached ({@link BearerEscortGoal}),
 * and periodically plants the banner and channels a rally ({@link RallyCastGoal}):
 *
 * <ul>
 *   <li>during the 2s cast it is outlined via {@code GLOWING} - visible through walls,
 *       so players get a clear "interrupt me" signal;</li>
 *   <li>rising ring particles telegraph the area;</li>
 *   <li>on completion, every {@link UndeadEntity} within {@link #RALLY_RADIUS} heals
 *       {@link #RALLY_HEAL} HP and gains Speed II for 10 seconds;</li>
 *   <li>taking a solid hit during the cast interrupts it (counterplay).</li>
 * </ul>
 *
 * Cast state is synced with a single boolean in SynchedEntityData that drives the
 * client-side cast animation; no custom packets. Uses the undead herald model.
 */
public class BannerBearerEntity extends Monster implements GeoEntity {

    public static final int CAST_DURATION_TICKS = 40;
    public static final int CAST_COOLDOWN_TICKS = 280;
    public static final double RALLY_RADIUS = 10.0D;
    public static final float RALLY_HEAL = 6.0F;

    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(BannerBearerEntity.class, EntityDataSerializers.BOOLEAN);

    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.banner_bearer.misc.idle");
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.banner_bearer.move.walk");
    protected static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.banner_bearer.misc.interact");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Server-side cast bookkeeping (ticks remaining); the synced boolean mirrors "castTicks > 0".
    private int castTicks;
    private int castCooldown;

    public BannerBearerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D) // a frail, hunched skeleton - two solid hits and he folds
                .add(Attributes.ATTACK_DAMAGE, 0.0D) // never attacks; kept so modifiers stay sane
                .add(Attributes.MOVEMENT_SPEED, 0.23D) // shuffling gait, slower than the horde he buffs
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CASTING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BearerEscortGoal(this)); // keep near the player, never close
        this.goalSelector.addGoal(2, new RallyCastGoal(this));    // buff the horde
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        // Deliberately no target selector and no melee goal: this mob is prey, not a hunter.
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    // ------------------------------------------------------------------ rally cast

    /** Attempts to begin the rally cast. Called by RallyCastGoal on its throttle. */
    public boolean tryStartRallyCast() {
        if (this.castTicks > 0 || this.castCooldown > 0 || this.level().isClientSide) {
            return false;
        }
        if (this.level().getNearestPlayer(this, 22.0D) == null) {
            return false; // no audience, no rally
        }
        if (countRallyTargets() == 0) {
            return false; // nobody to buff
        }
        this.castTicks = CAST_DURATION_TICKS;
        this.castCooldown = CAST_DURATION_TICKS + CAST_COOLDOWN_TICKS + this.getRandom().nextInt(80);
        this.entityData.set(DATA_CASTING, true);
        // Outlined through walls for the whole channel: the "interrupt me" signal.
        this.addEffect(new MobEffectInstance(MobEffects.GLOWING, CAST_DURATION_TICKS + 10, 0, false, false));
        this.level().playSound(null, this.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.8F);
        return true;
    }

    private void finishRallyCast() {
        this.entityData.set(DATA_CASTING, false);
        this.removeEffect(MobEffects.GLOWING);
        for (UndeadEntity ally : this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(RALLY_RADIUS), LivingEntity::isAlive)) {
            ally.heal(RALLY_HEAL);
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10 * 20, 1, false, true));
        }
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    this.getX(), this.getY() + 1.2D, this.getZ(), 40, 2.5D, 1.0D, 2.5D, 0.08D);
            server.sendParticles(ParticleTypes.ENCHANT,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 30, RALLY_RADIUS * 0.5D, 1.5D, RALLY_RADIUS * 0.5D, 0.4D);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.9F);
    }

    private void interruptCast() {
        this.castTicks = 0;
        this.entityData.set(DATA_CASTING, false);
        this.removeEffect(MobEffects.GLOWING);
    }

    private int countRallyTargets() {
        return this.level().getEntitiesOfClass(UndeadEntity.class,
                this.getBoundingBox().inflate(RALLY_RADIUS), LivingEntity::isAlive).size();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.castCooldown > 0) {
            this.castCooldown--;
        }
        if (this.castTicks > 0) {
            // Rising particle ring while channeling (4 pulses/sec, ~12 particles each).
            if (this.castTicks % 5 == 0 && this.level() instanceof ServerLevel server) {
                double baseAngle = (CAST_DURATION_TICKS - this.castTicks) * 0.35D;
                double y = this.getY() + 0.3D + (CAST_DURATION_TICKS - this.castTicks) * 0.03D;
                for (int i = 0; i < 12; i++) {
                    double angle = baseAngle + i * (Math.PI / 6.0D);
                    server.sendParticles(ParticleTypes.ENCHANT,
                            this.getX() + Math.sin(angle) * 2.2D, y, this.getZ() + Math.cos(angle) * 2.2D,
                            1, 0.0D, 0.06D, 0.0D, 0.02D);
                }
                server.sendParticles(ParticleTypes.WITCH,
                        this.getX(), this.getY() + 2.2D, this.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.02D);
            }
            if (--this.castTicks == 0) {
                finishRallyCast();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        // Counterplay: a real hit (2+ damage) knocks the banner down mid-channel.
        if (hit && !this.level().isClientSide && this.castTicks > 0 && amount >= 2.0F) {
            interruptCast();
        }
        return hit;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false; // same undead physiology as the rest of the horde
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animationPredicate));
    }

    /** Client-side, per frame - allocation-free. */
    protected PlayState animationPredicate(AnimationState<BannerBearerEntity> state) {
        if (this.isCasting()) {
            return state.setAndContinue(CAST);
        }
        return state.isMoving() ? state.setAndContinue(WALK) : state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** Natural spawns use the same ground/light rules as the rest of the horde. */
    public static boolean checkBannerBearerSpawnRules(EntityType<BannerBearerEntity> type,
                                                      net.minecraft.world.level.ServerLevelAccessor level,
                                                      net.minecraft.world.entity.MobSpawnType spawnType,
                                                      net.minecraft.core.BlockPos pos,
                                                      net.minecraft.util.RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }
}
