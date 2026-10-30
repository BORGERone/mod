package dev.settledlands.npc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side autonomous AI engine for GuideNpc:
 * 1) Free wandering within a configurable radius around a persisted home anchor,
 *    with automatic return when outside radius or after chasing a target.
 * 2) Automatic hostile-mob engagement in a configurable radius using Ivy Oleander's
 *    5 melee combos, orthodox stance and dodges (strictly NO fireballs/volleys),
 *    scaled by Damage and Speed from NpcStatsGraph ("Характеристики").
 *
 * NOTE: Must NEVER reference net.minecraft.client.* or dev.settledlands.npc.client.*.
 */
public final class NpcAutoAiEngine255 {
    public static final String TAG_HOME_SET = "AutoHomeSet";
    public static final String TAG_HOME_X = "AutoHomeX";
    public static final String TAG_HOME_Y = "AutoHomeY";
    public static final String TAG_HOME_Z = "AutoHomeZ";

    private static final ResourceLocation NPC_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath("settledlands", "npc_stats_health");
    private static final ResourceLocation NPC_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath("settledlands", "npc_stats_damage");
    private static final ResourceLocation NPC_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("settledlands", "npc_stats_speed");
    private static final ResourceLocation NPC_CALM_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("settledlands", "npc_calm_speed");

    public enum MeleeCombo {
        JAB("orthodox_left_jab", 10, new int[]{4}, 0.75F),
        HOOK("orthodox_right_hook", 13, new int[]{5}, 0.90F),
        JAB_RIGHT_CROSS("orthodox_left_jab_right_cross", 20, new int[]{5, 13}, 0.80F),
        JAB_JAB_HOOK("orthodox_jab_jab_hook", 25, new int[]{5, 11, 19}, 0.75F),
        DASH_RIGHT_CROSS("orthodox_dash_forward_right_cross", 20, new int[]{12}, 1.15F);

        public final String anim;
        public final int durationTicks;
        public final int[] impactTicks;
        public final float damageMult;

        MeleeCombo(String anim, int durationTicks, int[] impactTicks, float damageMult) {
            this.anim = anim;
            this.durationTicks = durationTicks;
            this.impactTicks = impactTicks;
            this.damageMult = damageMult;
        }
    }

    private static final Map<GuideNpc, NpcAutoAiEngine255> ENGINES =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Home anchor state
    private boolean hasHome;
    private int homeX;
    private int homeY;
    private int homeZ;

    // Cached node values prevent modifier churn in the per-tick server AI path
    private int appliedHealthStat = -1;
    private int appliedDamageStat = -1;
    private int appliedSpeedStat = -1;
    private NpcProfile syncedProfile;
    private NpcDialogueGraph syncedGraph;

    // Melee combat state
    private LivingEntity target;
    private LivingEntity recentMobAttacker;
    private int recentAttackerExpireTick;
    private MeleeCombo currentCombo;
    private boolean comboLunge;
    private boolean dodging;
    private int actionTicks;
    private int impactsLeft;
    private int comboCooldown;
    private int exitCombatTicks;
    private int scanCooldown;
    private int lastIncomingHitTick = -100;
    private int incomingHits;
    private boolean stanceActive;

    // Wander / return state
    private boolean returningHome;
    private BlockPos wanderTarget;
    private int wanderWaitTicks = 40;
    private int wanderActiveTicks;
    private int wanderProgressTicks;
    private double lastProgressX;
    private double lastProgressZ;

    private NpcAutoAiEngine255() {}

    public static NpcAutoAiEngine255 of(GuideNpc npc) {
        if (npc == null) return null;
        synchronized (ENGINES) {
            NpcAutoAiEngine255 e = ENGINES.get(npc);
            if (e == null) {
                e = new NpcAutoAiEngine255();
                ENGINES.put(npc, e);
            }
            return e;
        }
    }

    public static boolean isEligibleNpc(GuideNpc npc) {
        if (npc == null) return false;
        if (npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return false;
        Level level = npc.level();
        if (level == null || level.isClientSide) return false;
        return npc.isAlive() && !npc.isRemoved() && !npc.isPassenger();
    }

    public boolean hasHome() {
        return hasHome;
    }

    public BlockPos homePos() {
        return hasHome ? new BlockPos(homeX, homeY, homeZ) : null;
    }

    public void setHome(BlockPos pos) {
        if (pos == null) {
            this.hasHome = false;
            return;
        }
        this.hasHome = true;
        this.homeX = pos.getX();
        this.homeY = pos.getY();
        this.homeZ = pos.getZ();
    }

    public LivingEntity currentTarget() {
        return target;
    }

    public MeleeCombo currentCombo() {
        return currentCombo;
    }

    // ---- NBT Persistence Hooks ----

    public static void saveNpcData(GuideNpc npc, CompoundTag guideTag) {
        if (npc == null || guideTag == null) return;
        if (npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return;
        NpcAutoAiEngine255 eng = of(npc);
        if (eng != null && eng.hasHome) {
            guideTag.putBoolean(TAG_HOME_SET, true);
            guideTag.putInt(TAG_HOME_X, eng.homeX);
            guideTag.putInt(TAG_HOME_Y, eng.homeY);
            guideTag.putInt(TAG_HOME_Z, eng.homeZ);
        }
    }

    public static void loadNpcData(GuideNpc npc, CompoundTag rootTag) {
        if (npc == null || rootTag == null) return;
        if (npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return;
        NpcAutoAiEngine255 eng = of(npc);
        if (eng == null) return;
        if (rootTag.contains("Guide", 10)) {
            CompoundTag guide = rootTag.getCompound("Guide");
            if (guide.getBoolean(TAG_HOME_SET)) {
                eng.hasHome = true;
                eng.homeX = guide.getInt(TAG_HOME_X);
                eng.homeY = guide.getInt(TAG_HOME_Y);
                eng.homeZ = guide.getInt(TAG_HOME_Z);
                return;
            }
        }
        eng.hasHome = false;
    }

    // ---- GuideNpc Runtime Hooks ----

    public static void onScriptedMoveActive(GuideNpc npc) {
        if (npc == null || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return;
        Level level = npc.level();
        if (level == null || level.isClientSide) return;
        NpcAutoAiEngine255 eng = of(npc);
        eng.abortCombatAndWander(npc, false);
        NpcProfile p = npc.profile();
        if (NpcAutoAi255.isAutoWander(p) || NpcAutoAi255.isAutoAttackHostiles(p)) {
            eng.setHome(npc.blockPosition());
        }
    }

    public static void onTalkingPaused(GuideNpc npc) {
        if (npc == null || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return;
        NpcAutoAiEngine255 eng = ENGINES.get(npc);
        if (eng != null && (eng.target != null || eng.exitCombatTicks > 0 || eng.wanderTarget != null)) {
            eng.abortCombatAndWander(npc, true);
        }
    }

    public static void onHurt(GuideNpc npc, DamageSource source) {
        if (!isEligibleNpc(npc) || source == null) return;
        if (npc.aiMode() != 0 && !isAutoAttackEnabled(npc)) return;
        NpcProfile profile = npc.profile();
        if (!NpcAutoAi255.isAutoAttackHostiles(profile)) return;

        NpcAutoAiEngine255 eng = of(npc);
        eng.ensureHome(npc);

        Entity directAttacker = source.getEntity();
        LivingEntity livingAttacker = directAttacker instanceof LivingEntity le ? le : null;
        if (livingAttacker != null && isValidHostileTarget(npc, livingAttacker, livingAttacker)) {
            eng.recentMobAttacker = livingAttacker;
            eng.recentAttackerExpireTick = npc.tickCount + 120;
            if (!npc.isTalking() && (eng.target == null || !eng.target.isAlive())) {
                eng.engage(npc, livingAttacker);
            }
        }

        if (!npc.isTalking() && eng.target != null) {
            if (npc.tickCount - eng.lastIncomingHitTick <= 20) {
                eng.incomingHits++;
            } else {
                eng.incomingHits = 1;
            }
            eng.lastIncomingHitTick = npc.tickCount;
            if (eng.incomingHits >= 2 && !eng.dodging && npc.getRandom().nextFloat() < 0.40F) {
                eng.incomingHits = 0;
                eng.startDodge(npc, livingAttacker != null ? livingAttacker : eng.target);
            }
        }
    }

    public static boolean isAutoAttackEnabled(GuideNpc npc) {
        return isEligibleNpc(npc) && NpcAutoAi255.isAutoAttackHostiles(npc.profile());
    }

    public static int legacyAiMode(GuideNpc npc) {
        return isAutoAttackEnabled(npc) ? 0 : npc.aiMode();
    }

    public static void syncStats(GuideNpc npc) {
        if (isEligibleNpc(npc)) {
            of(npc).syncStatsFromGraph(npc, npc.profile());
        }
    }

    public static void runMobilityAndAutoAi(GuideNpc npc) {
        boolean fighting = tickCombat(npc);
        if (fighting) {
            return;
        }
        if (npc != null) {
            npc.settledlands$tickMobilityOriginal();
        }
        tickWander(npc);
    }

    /**
     * Runs autonomous hostile-mob melee combat. Returns true when actively handling
     * combat or combat-exit stance so customServerAiStep skips passive look/wander.
     */
    public static boolean tickCombat(GuideNpc npc) {
        if (!isEligibleNpc(npc)) {
            NpcAutoAiEngine255 existing = ENGINES.get(npc);
            if (existing != null) existing.abortCombatAndWander(npc, false);
            return false;
        }
        if (npc.isTalking()) {
            return false;
        }
        NpcProfile profile = npc.profile();
        boolean wantWander = NpcAutoAi255.isAutoWander(profile);
        boolean wantAttack = NpcAutoAi255.isAutoAttackHostiles(profile);
        NpcAutoAiEngine255 eng = of(npc);
        if (!wantWander && !wantAttack) {
            if (eng.hasHome || eng.target != null || eng.stanceActive || eng.wanderTarget != null) {
                eng.abortCombatAndWander(npc, true);
                eng.hasHome = false;
            }
            return false;
        }

        eng.ensureHome(npc);
        if (!wantAttack) {
            if (eng.target != null || eng.exitCombatTicks > 0 || eng.stanceActive) {
                eng.abortCombatAndWander(npc, true);
            }
            return false;
        }

        return eng.stepCombat(npc, profile);
    }

    /**
     * Runs autonomous wandering inside wanderRadius around home and post-combat return.
     */
    public static void tickWander(GuideNpc npc) {
        if (!isEligibleNpc(npc) || npc.isTalking()) return;
        NpcAutoAiEngine255 eng = of(npc);
        if (npc.isLeashed()) {
            eng.clearWander();
            return;
        }
        NpcProfile profile = npc.profile();
        boolean wantWander = NpcAutoAi255.isAutoWander(profile);
        boolean wantAttack = NpcAutoAi255.isAutoAttackHostiles(profile);
        if (!wantWander && !wantAttack) {
            if (eng.hasHome || eng.wanderTarget != null) {
                eng.clearWander();
                eng.hasHome = false;
            }
            return;
        }

        eng.ensureHome(npc);

        // Respect explicit wand-assigned route walking when active
        if (profile != null && profile.idleWalking && npc.settledlands$hasActiveMobilityRoute()) {
            eng.clearWander();
            eng.setHome(npc.blockPosition());
            return;
        }

        eng.stepWanderAndReturn(npc, profile, wantWander, wantAttack);
    }

    private void syncStatsFromGraph(GuideNpc npc, NpcProfile profile) {
        NpcDialogueGraph graph = profile == null ? null : profile.dialogueGraph;
        if (profile == syncedProfile && graph == syncedGraph && appliedHealthStat >= 0
                && npc.tickCount % 20 != 0) {
            return;
        }
        int healthStat = NpcStatsGraph.health(graph);
        int damageStat = NpcStatsGraph.damage(graph);
        int speedStat = NpcStatsGraph.speed(graph);

        AttributeInstance health = npc.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && appliedHealthStat != healthStat) {
            setModifier(health, NPC_HEALTH_ID, 2.0D * (healthStat - NpcStatsGraph.DEFAULT),
                    AttributeModifier.Operation.ADD_VALUE);
            npc.setHealth((float) Math.min(npc.getHealth(), npc.getMaxHealth()));
            appliedHealthStat = healthStat;
        }
        AttributeInstance damage = npc.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && appliedDamageStat != damageStat) {
            setModifier(damage, NPC_DAMAGE_ID, damageStat / 10.0D - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            appliedDamageStat = damageStat;
        }
        AttributeInstance speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && appliedSpeedStat != speedStat) {
            setModifier(speed, NPC_SPEED_ID, speedStat / 10.0D - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            appliedSpeedStat = speedStat;
        }
        syncedProfile = profile;
        syncedGraph = graph;
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount,
                                    AttributeModifier.Operation operation) {
        if (!Double.isFinite(amount) || Math.abs(amount) < 1.0E-12D) {
            attribute.removeModifier(id);
            return;
        }
        AttributeModifier old = attribute.getModifier(id);
        if (old != null && old.operation() == operation && Math.abs(old.amount() - amount) < 1.0E-12D) {
            return;
        }
        attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    /**
     * `walkingNavigationSpeed` is an inverse attribute factor. Reapply the node's
     * speed once after that inverse, then halve only calm walking. Combat retains
     * build 255's full node-scaled pace.
     */
    public static double navigationSpeedFactor(GuideNpc npc) {
        if (npc == null || npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc) return 1.0D;
        NpcProfile profile = npc.profile();
        double stat = clampDouble(NpcAutoAi255.speedMultiplier(profile), 0.1D, 10.0D);
        NpcAutoAiEngine255 eng = ENGINES.get(npc);
        boolean fighting = eng != null && ((eng.target != null && eng.target.isAlive())
                || eng.exitCombatTicks > 0 || (eng.stanceActive && eng.actionTicks > 0));
        if (!fighting && npc.aiMode() != 0 && npc.ivyCombat() != null) {
            LivingEntity legacyTarget = npc.ivyCombat().combatTarget();
            fighting = legacyTarget != null && legacyTarget.isAlive();
        }
        // A small calm-pace bump keeps route walking responsive without changing
        // combat chase speed or applying the Stats multiplier a second time.
        return stat * (fighting ? 1.0D : 0.55D);
    }

    // ---- Internal Combat Implementation ----

    private boolean stepCombat(GuideNpc npc, NpcProfile profile) {
        int aggroR = NpcAutoAi255.aggroRadius(profile);
        int wanderR = NpcAutoAi255.isAutoWander(profile) ? NpcAutoAi255.wanderRadius(profile) : 0;
        double maxChaseFromHome = Math.max(aggroR + 8.0D, wanderR + aggroR + 4.0D);
        double maxTargetDist = Math.max(aggroR + 8.0D, 18.0D);

        LivingEntity remembered = (npc.tickCount <= recentAttackerExpireTick) ? recentMobAttacker : null;
        if (target != null) {
            double distSq = npc.distanceToSqr(target);
            double npcHomeDistSq = horizDistSqToHome(npc.getX(), npc.getZ());
            if (!isValidHostileTarget(npc, target, remembered)
                    || distSq > maxTargetDist * maxTargetDist
                    || npcHomeDistSq > maxChaseFromHome * maxChaseFromHome) {
                disengage(npc);
            }
        }

        if (target == null) {
            if (exitCombatTicks > 0) {
                exitCombatTicks--;
                if (exitCombatTicks == 0) {
                    stanceActive = false;
                    npc.playCustomAnimation("", false);
                }
                holdPosition(npc);
                return true;
            }

            LivingEntity found = findHostileTarget(npc, aggroR, maxChaseFromHome, remembered);
            if (found == null) {
                return false;
            }
            engage(npc, found);
        }

        if (comboCooldown > 0) {
            comboCooldown--;
        }

        double speedMul = clampDouble(NpcAutoAi255.speedMultiplier(profile), 0.1D, 10.0D);

        if (actionTicks > 0) {
            faceTarget(npc, target, 1.0F);
            npc.getNavigation().stop();
            // Let dodge or dash impulse glide briefly before planting feet
            boolean gliding = (dodging && actionTicks > 5)
                    || (comboLunge && currentCombo != null
                    && actionTicks > currentCombo.durationTicks - 7);
            if (!gliding) {
                holdPosition(npc);
            }
            tickComboImpact(npc, profile, speedMul);
            return true;
        }

        double distSq = npc.distanceToSqr(target);
        double dist = Math.sqrt(distSq);
        // While navigating, leave body/head orientation to locomotion. Explicitly
        // facing the target here makes the model walk sideways toward it.

        if (dist <= 2.45D && comboCooldown <= 0) {
            startMeleeCombo(npc, pickMeleeCombo(npc.getRandom()));
            return true;
        }

        if (dist >= 2.35D && dist <= 5.5D && comboCooldown <= 0 && npc.getRandom().nextFloat() < 0.22F) {
            lungeForward(npc, target, 0.55D * clampDouble(speedMul, 0.5D, 1.8D));
            startMeleeCombo(npc, pickMeleeCombo(npc.getRandom()));
            comboLunge = true;
            return true;
        }

        if (dist > 2.05D) {
            // The attribute now carries the node multiplier; compensate here to retain
            // build 255's exact clamped combat chase speed rather than applying it twice.
            double chaseSpeed = clampDouble(1.28D * speedMul, 0.25D, 3.2D) / speedMul;
            npc.getNavigation().moveTo(target, chaseSpeed);
        } else {
            npc.getNavigation().stop();
        }
        return true;
    }

    private LivingEntity findHostileTarget(GuideNpc npc, int aggroR, double maxChaseFromHome, LivingEntity remembered) {
        if (remembered != null && isValidHostileTarget(npc, remembered, remembered)) {
            double dSq = npc.distanceToSqr(remembered);
            double maxR = aggroR + 4.0D;
            if (dSq <= maxR * maxR && horizDistSqToHome(remembered.getX(), remembered.getZ()) <= maxChaseFromHome * maxChaseFromHome) {
                return remembered;
            }
        }

        if (scanCooldown > 0) {
            scanCooldown--;
            return null;
        }
        scanCooldown = 10;

        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        double r = aggroR;
        double rSq = r * r;
        double leashSq = maxChaseFromHome * maxChaseFromHome;
        AABB box = npc.getBoundingBox().inflate(r, Math.min(r, 8.0D), r);
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, box);

        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            LivingEntity cand = candidates.get(i);
            if (!isValidHostileTarget(npc, cand, remembered)) continue;
            double dSq = npc.distanceToSqr(cand);
            if (dSq > rSq || dSq >= bestDistSq) continue;
            if (horizDistSqToHome(cand.getX(), cand.getZ()) > leashSq) continue;
            if (dSq > 16.0D && !npc.hasLineOfSight(cand)) continue;
            best = cand;
            bestDistSq = dSq;
        }
        return best;
    }

    public static boolean isValidHostileTarget(GuideNpc npc, LivingEntity candidate, LivingEntity rememberedAttacker) {
        if (candidate == null || candidate == npc) return false;
        if (!candidate.isAlive() || candidate.isRemoved() || candidate.isSpectator()) return false;
        if (candidate instanceof GuideNpc || candidate instanceof Player) return false;
        if (candidate instanceof Enemy) return true;
        if (candidate instanceof Mob mob) {
            return mob.getTarget() == npc || candidate == rememberedAttacker;
        }
        return false;
    }

    private void engage(GuideNpc npc, LivingEntity newTarget) {
        clearWander();
        this.target = newTarget;
        this.exitCombatTicks = 0;
        this.currentCombo = null;
        this.comboLunge = false;
        this.dodging = false;
        this.actionTicks = 0;
        this.impactsLeft = 0;
        this.comboCooldown = 0;
        this.stanceActive = true;
        npc.getNavigation().stop();
        npc.playCustomAnimation("orthodox_idle", true);
    }

    private void disengage(GuideNpc npc) {
        this.target = null;
        this.currentCombo = null;
        this.comboLunge = false;
        this.dodging = false;
        this.actionTicks = 0;
        this.impactsLeft = 0;
        this.comboCooldown = 0;
        this.exitCombatTicks = 12;
        this.returningHome = true;
        npc.getNavigation().stop();
        npc.playCustomAnimation("exit_orthodox", false);
    }

    private void abortCombatAndWander(GuideNpc npc, boolean clearAnim) {
        boolean wasAnimated = this.stanceActive || this.target != null || this.exitCombatTicks > 0 || this.actionTicks > 0;
        this.target = null;
        this.currentCombo = null;
        this.comboLunge = false;
        this.dodging = false;
        this.actionTicks = 0;
        this.impactsLeft = 0;
        this.comboCooldown = 0;
        this.exitCombatTicks = 0;
        this.stanceActive = false;
        clearWander();
        if (clearAnim && wasAnimated && npc != null) {
            npc.playCustomAnimation("", false);
        }
    }

    private static MeleeCombo pickMeleeCombo(RandomSource rng) {
        // Always use a real multi-strike Ivy animation; single jab/hook choices
        // made most encounters look as if the NPC only ever used its first attack.
        return rng.nextBoolean() ? MeleeCombo.JAB_RIGHT_CROSS : MeleeCombo.JAB_JAB_HOOK;
    }

    private void startMeleeCombo(GuideNpc npc, MeleeCombo combo) {
        this.comboLunge = false;
        this.dodging = false;
        this.currentCombo = combo;
        this.actionTicks = combo.durationTicks;
        this.impactsLeft = combo.impactTicks.length;
        this.stanceActive = true;
        npc.playCustomAnimation(combo.anim, false);
        npc.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.30F, 1.80F);
    }

    private void startDodge(GuideNpc npc, LivingEntity threat) {
        boolean left = npc.getRandom().nextBoolean();
        this.currentCombo = null;
        this.comboLunge = false;
        this.dodging = true;
        this.actionTicks = 9;
        this.impactsLeft = 0;
        this.stanceActive = true;
        npc.playCustomAnimation(left ? "dodge_left" : "dodge_right", false);
        if (threat != null) {
            Vec3 diff = threat.position().subtract(npc.position());
            Vec3 flat = new Vec3(diff.x, 0.0D, diff.z);
            if (flat.lengthSqr() > 1.0E-4D) {
                Vec3 side = flat.normalize().yRot(1.5707964F).scale(left ? -0.5D : 0.5D);
                npc.push(side.x, 0.18D, side.z);
            }
        }
        npc.playSound(SoundEvents.WOLF_GROWL, 0.5F, 1.4F);
    }

    private void tickComboImpact(GuideNpc npc, NpcProfile profile, double speedMul) {
        if (currentCombo == null) {
            actionTicks--;
            if (actionTicks <= 0) {
                dodging = false;
                comboCooldown = computeCooldown(npc.getRandom(), speedMul);
                npc.playCustomAnimation("orthodox_idle", true);
            }
            return;
        }

        int elapsed = currentCombo.durationTicks - actionTicks;
        int[] impacts = currentCombo.impactTicks;
        int nextIdx = impacts.length - impactsLeft;
        if (impactsLeft > 0 && elapsed >= impacts[nextIdx]) {
            impactsLeft--;
            applyMeleeHit(npc, currentCombo);
        }

        actionTicks--;
        if (actionTicks <= 0) {
            currentCombo = null;
            comboLunge = false;
            dodging = false;
            comboCooldown = computeCooldown(npc.getRandom(), speedMul);
            npc.playCustomAnimation("orthodox_idle", true);
        }
    }

    private void applyMeleeHit(GuideNpc npc, MeleeCombo combo) {
        if (target == null || !target.isAlive()) return;
        double bonusReach = (combo == MeleeCombo.DASH_RIGHT_CROSS) ? 0.55D : 0.0D;
        double maxReach = 2.45D + bonusReach + 0.6D;
        if (npc.distanceToSqr(target) > maxReach * maxReach) {
            return;
        }

        AttributeInstance attr = npc.getAttribute(Attributes.ATTACK_DAMAGE);
        float baseDamage = attr != null ? (float) attr.getValue() : 4.0F;
        // ATTACK_DAMAGE already carries the characteristics-node multiplier.
        float finalDamage = Math.max(0.25F, baseDamage * combo.damageMult);

        boolean hit = target.hurt(npc.damageSources().mobAttack(npc), finalDamage);
        npc.swing(InteractionHand.MAIN_HAND);
        npc.playSound(hit ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.0F);
        if (hit) {
            Vec3 diff = target.position().subtract(npc.position());
            if (diff.horizontalDistanceSqr() > 1.0E-4D) {
                Vec3 push = new Vec3(diff.x, 0.0D, diff.z).normalize().scale(0.45D);
                target.push(push.x, 0.10D, push.z);
                target.hurtMarked = true;
            }
        }
    }

    private static int computeCooldown(RandomSource rng, double speedMul) {
        int raw = 10 + rng.nextInt(12);
        double scaled = raw / clampDouble(speedMul, 0.4D, 2.5D);
        return Math.max(4, (int) Math.round(scaled));
    }

    private static void lungeForward(GuideNpc npc, LivingEntity target, double strength) {
        Vec3 diff = target.position().subtract(npc.position());
        Vec3 flat = new Vec3(diff.x, 0.0D, diff.z);
        if (flat.lengthSqr() > 1.0E-4D) {
            Vec3 impulse = flat.normalize().scale(strength);
            npc.push(impulse.x, 0.12D, impulse.z);
        }
    }

    private static void faceTarget(GuideNpc npc, LivingEntity target, float lerp) {
        double dx = target.getX() - npc.getX();
        double dz = target.getZ() - npc.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232D) - 90.0F;
        float nextYaw = Mth.rotLerp(lerp, npc.getYRot(), yaw);
        npc.setYRot(nextYaw);
        npc.yBodyRot = nextYaw;
        npc.yHeadRot = nextYaw;
    }

    private static void holdPosition(GuideNpc npc) {
        npc.getNavigation().stop();
        Vec3 dm = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, dm.y, 0.0D);
    }

    // ---- Internal Home Wander & Return Implementation ----

    private void ensureHome(GuideNpc npc) {
        if (!hasHome) {
            setHome(npc.blockPosition());
        }
    }

    private double horizDistSqToHome(double x, double z) {
        if (!hasHome) return 0.0D;
        double dx = x - (homeX + 0.5D);
        double dz = z - (homeZ + 0.5D);
        return dx * dx + dz * dz;
    }

    private void clearWander() {
        this.wanderTarget = null;
        this.wanderActiveTicks = 0;
        this.wanderProgressTicks = 0;
    }

    private void stepWanderAndReturn(GuideNpc npc, NpcProfile profile, boolean wantWander, boolean wantAttack) {
        double distHomeSq = horizDistSqToHome(npc.getX(), npc.getZ());

        // 1. Stationary guard mode (autoWander = false, autoAttackHostiles = true): return to guard post after combat
        if (!wantWander) {
            if (returningHome || distHomeSq > 2.25D) {
                returningHome = true;
                navigateTowardHome(npc, 1.5D);
            }
            return;
        }

        // 2. Free home-radius wander mode (autoWander = true)
        int radius = NpcAutoAi255.wanderRadius(profile);
        double outerLimit = radius + 1.25D;
        double innerResume = Math.max(1.5D, radius * 0.65D);
        if (distHomeSq > outerLimit * outerLimit || (returningHome && distHomeSq > innerResume * innerResume)) {
            returningHome = true;
            clearWander();
            navigateTowardHome(npc, innerResume);
            return;
        }
        returningHome = false;

        // Currently strolling to a chosen point inside radius
        if (wanderTarget != null) {
            wanderActiveTicks++;
            double targetDistSq = npc.distanceToSqr(
                    wanderTarget.getX() + 0.5D,
                    wanderTarget.getY(),
                    wanderTarget.getZ() + 0.5D
            );
            if (targetDistSq <= 1.75D || npc.getNavigation().isDone() || wanderActiveTicks > 200) {
                npc.getNavigation().stop();
                clearWander();
                // Stay at each destination for 20–45 seconds before choosing another.
                wanderWaitTicks = 400 + npc.getRandom().nextInt(501);
                return;
            }
            wanderProgressTicks++;
            if (wanderProgressTicks >= 30) {
                double movedSq = (npc.getX() - lastProgressX) * (npc.getX() - lastProgressX)
                        + (npc.getZ() - lastProgressZ) * (npc.getZ() - lastProgressZ);
                lastProgressX = npc.getX();
                lastProgressZ = npc.getZ();
                wanderProgressTicks = 0;
                if (movedSq < 0.06D) {
                    npc.getNavigation().stop();
                    clearWander();
                    wanderWaitTicks = 160 + npc.getRandom().nextInt(161);
                }
            }
            return;
        }

        if (wanderWaitTicks > 0) {
            wanderWaitTicks--;
            return;
        }

        BlockPos next = pickWalkableHomePos(npc, radius);
        if (next == null) {
            wanderWaitTicks = 30 + npc.getRandom().nextInt(40);
            return;
        }

        double walkSpeed = clampDouble(npc.walkingNavigationSpeed(false), 0.2D, 2.8D);
        if (npc.getNavigation().moveTo(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D, walkSpeed)) {
            this.wanderTarget = next;
            this.wanderActiveTicks = 0;
            this.wanderProgressTicks = 0;
            this.lastProgressX = npc.getX();
            this.lastProgressZ = npc.getZ();
        } else {
            this.wanderWaitTicks = 25 + npc.getRandom().nextInt(35);
        }
    }

    private void navigateTowardHome(GuideNpc npc, double stopDist) {
        double distSq = horizDistSqToHome(npc.getX(), npc.getZ());
        if (distSq <= stopDist * stopDist) {
            returningHome = false;
            npc.getNavigation().stop();
            wanderWaitTicks = 30 + npc.getRandom().nextInt(50);
            return;
        }
        if (!npc.getNavigation().isDone() && (npc.tickCount & 15) != 0) {
            return;
        }
        double hx = homeX + 0.5D;
        double hy = homeY;
        double hz = homeZ + 0.5D;
        double dist = Math.sqrt(distSq);
        if (dist > 28.0D) {
            double step = 22.0D / dist;
            hx = npc.getX() + (hx - npc.getX()) * step;
            hz = npc.getZ() + (hz - npc.getZ()) * step;
            BlockPos mid = findSurfaceNear(npc.level(), BlockPos.containing(hx, npc.getY(), hz));
            if (mid != null) {
                hx = mid.getX() + 0.5D;
                hy = mid.getY();
                hz = mid.getZ() + 0.5D;
            } else {
                hy = npc.getY();
            }
        }
        double retSpeed = clampDouble(npc.walkingNavigationSpeed(true), 0.25D, 3.0D);
        npc.getNavigation().moveTo(hx, hy, hz, retSpeed);
    }

    private BlockPos pickWalkableHomePos(GuideNpc npc, int radius) {
        Level level = npc.level();
        RandomSource rng = npc.getRandom();
        int rSq = radius * radius;
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = rng.nextInt(radius * 2 + 1) - radius;
            int dz = rng.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > rSq) continue;
            if (radius >= 3 && dx == 0 && dz == 0) continue;

            int tx = homeX + dx;
            int tz = homeZ + dz;
            if (radius >= 3) {
                double moveSq = (tx + 0.5D - npc.getX()) * (tx + 0.5D - npc.getX())
                        + (tz + 0.5D - npc.getZ()) * (tz + 0.5D - npc.getZ());
                if (moveSq < 2.25D) continue;
            }

            BlockPos candidate = findSurfaceNear(level, new BlockPos(tx, homeY, tz));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockPos findSurfaceNear(Level level, BlockPos center) {
        if (level instanceof ServerLevel sl && !sl.isLoaded(center)) {
            return null;
        }
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        int[] offsets = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4};
        for (int dy : offsets) {
            int y = center.getY() + dy;
            if (y < minY || y > maxY) continue;
            BlockPos feet = new BlockPos(center.getX(), y, center.getZ());
            if (isWalkableSpot(level, feet)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isWalkableSpot(Level level, BlockPos feet) {
        BlockPos below = feet.offset(0, -1, 0);
        BlockPos head = feet.offset(0, 1, 0);
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isSolid() || isHazardousFloor(belowState)) {
            return false;
        }
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        return !feetState.isSolid() && !headState.isSolid()
                && !isHazardousAirspace(feetState) && !isHazardousAirspace(headState);
    }

    private static boolean isHazardousFloor(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE);
    }

    private static boolean isHazardousAirspace(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.CACTUS);
    }

    private static double clampDouble(double v, double lo, double hi) {
        if (!Double.isFinite(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }
}
