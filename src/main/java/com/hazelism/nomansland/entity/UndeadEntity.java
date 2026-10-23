package com.hazelism.nomansland.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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
 * <p>Stage scope: locomotion + melee + vanilla-style targeting.
 * Later stages plug in here, each behind cheap, throttled checks:
 * limb loss (SynchedEntityData + bone hiding in the renderer), climbing,
 * smell/investigation goals, and moon-phase spawn gating.
 *
 * <p>No per-tick allocations: animations are static constants, goals are
 * created once in {@link #registerGoals()}.
 */
public class UndeadEntity extends Monster implements GeoEntity {

    // The corrupted illager model ships a single walk cycle; idle is the bind pose.
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.corrupted_illager.walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public UndeadEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Zombies in the original climb (navigation.climb); the real climb goal arrives later.
        // Keep pathfinding vanilla for now - it is heavily optimised and shared across mobs.
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Values from the Bedrock original: health 40, attack 3, follow_range 20.
        // MOVEMENT_SPEED: Bedrock "movement: 0.36" is not 1:1 with the Java attribute;
        // 0.30 keeps the same in-game feel (a Java zombie is 0.23). Tune in playtests.
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
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
