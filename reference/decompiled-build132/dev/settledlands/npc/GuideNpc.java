package dev.settledlands.npc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public class GuideNpc extends PathfinderMob {
   private static final String TAG_ROOT = "Guide";
   private static final String TAG_PHRASES = "Phrases";
   private static final String TAG_PROFILE = "Profile";
   private static final String TAG_NUMBER = "N";
   private static final String TAG_TEXT = "T";
   public static final int MAX_PHRASES = 512;
   public static final double ACTION_RANGE_SQ = 64.0;
   private static final EntityDataAccessor<Boolean> DATA_TALKING = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.BYTE);
   private static final EntityDataAccessor<String> DATA_TEXTURE = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Integer> DATA_SCALE = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> DATA_ANIM_NAME = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> DATA_ANIM_LOOP = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> DATA_ANIM_START_TICK = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> DATA_ANIM_BONE = SynchedEntityData.defineId(GuideNpc.class, EntityDataSerializers.STRING);
   private int clientAnimStartTick = -1;
   private final List<GuidePhrase> phrases = new ArrayList<>();
   private NpcProfile profile = new NpcProfile();
   private int lookTimer;
   private NpcEscort escort;
   private long mobilityRouteCheckSecond = Long.MIN_VALUE;
   private int mobilityWaitTicks;
   private int activeMobilityPoint = -1;
   private int lastMobilityPoint = -1;
   private BlockPos activeMobilityTarget;
   private boolean mobilityPaused;
   private String activeMobilityRouteId = "";
   private List<NpcProfile.MobilityPoint> activeMobilityPoints = List.of();
   private Vec3 customMoveTarget;
   private double customMoveSpeed = 1.0;
   private int customMoveRepathTicks;
   private int customMoveFailures;
   private int customMoveProgressTicks;
   private double customMoveProgressX;
   private double customMoveProgressY;
   private double customMoveProgressZ;

   public GuideNpc(EntityType<? extends GuideNpc> var1, Level var2) {
      super(var1, var2);
      this.setPersistenceRequired();
      this.xpReward = 0;
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 60.0)
         .add(Attributes.MOVEMENT_SPEED, 0.24)
         .add(Attributes.FOLLOW_RANGE, 48.0)
         .add(Attributes.STEP_HEIGHT, 1.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder var1) {
      super.defineSynchedData(var1);
      var1.define(DATA_TALKING, Boolean.FALSE);
      var1.define(DATA_POSE, (byte)0);
      var1.define(DATA_TEXTURE, "");
      var1.define(DATA_SCALE, 100);
      var1.define(DATA_ANIM_NAME, "");
      var1.define(DATA_ANIM_LOOP, Boolean.FALSE);
      var1.define(DATA_ANIM_START_TICK, 0);
      var1.define(DATA_ANIM_BONE, "");
   }

   public void playCustomAnimation(String animName, boolean loop, String bone) {
      if (animName == null) animName = "";
      if (bone == null) bone = "";
      this.entityData.set(DATA_ANIM_NAME, animName);
      this.entityData.set(DATA_ANIM_LOOP, loop);
      this.entityData.set(DATA_ANIM_BONE, bone);
      this.entityData.set(DATA_ANIM_START_TICK, this.tickCount);
      if (this.level().isClientSide) {
         this.clientAnimStartTick = this.tickCount;
      }
   }

   public void playCustomAnimation(String animName, boolean loop) {
      playCustomAnimation(animName, loop, "");
   }

   public String getCustomAnimBone() {
      try {
         return (String)this.entityData.get(DATA_ANIM_BONE);
      } catch (Throwable ignored) {
         return "";
      }
   }

   public String getCustomAnimName() {
      try {
         return (String)this.entityData.get(DATA_ANIM_NAME);
      } catch (Throwable ignored) {
         return "";
      }
   }

   public boolean isCustomAnimLoop() {
      try {
         return (Boolean)this.entityData.get(DATA_ANIM_LOOP);
      } catch (Throwable ignored) {
         return false;
      }
   }

   public int getCustomAnimStartTick() {
      if (this.level().isClientSide) {
         if (this.clientAnimStartTick < 0) {
            this.clientAnimStartTick = this.tickCount;
         }
         return this.clientAnimStartTick;
      }
      try {
         return (Integer)this.entityData.get(DATA_ANIM_START_TICK);
      } catch (Throwable ignored) {
         return 0;
      }
   }

   public int visualScale() {
      try {
         return (Integer)this.entityData.get(DATA_SCALE);
      } catch (Throwable ignored) {
         return 100;
      }
   }

   public void setClientScale(int scale) {
      try {
         this.entityData.set(DATA_SCALE, NpcProfile.clamp(scale, 50, 150));
         this.refreshDimensions();
      } catch (Throwable ignored) {}
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_SCALE.equals(key)) {
         this.refreshDimensions();
      }
      if (DATA_ANIM_NAME.equals(key) || DATA_ANIM_START_TICK.equals(key)) {
         this.clientAnimStartTick = this.tickCount;
      }
   }

   @Override
   public float getScale() {
      try {
         int s = this.visualScale();
         if (s > 0) {
            return (float) s / 100.0f;
         }
      } catch (Throwable ignored) {}
      return super.getScale();
   }

   public String texturePath() {
      return (String)this.entityData.get(DATA_TEXTURE);
   }

   public UUID relationKey() {
      String var1 = this.profile.texture == null ? "" : this.profile.texture;
      return var1.isEmpty() ? this.getUUID() : UUID.nameUUIDFromBytes(("settledlands:npcmodel:" + var1).getBytes(StandardCharsets.UTF_8));
   }

   public int poseIndex() {
      return (Byte)this.entityData.get(DATA_POSE);
   }

   public void setPoseIndex(int var1) {
      this.entityData.set(DATA_POSE, (byte)NpcProfile.clamp(var1, 0, 2));
   }

   public boolean isTalking() {
      return (Boolean)this.entityData.get(DATA_TALKING);
   }

   public void setTalking(boolean var1) {
      this.entityData.set(DATA_TALKING, var1);
   }

   protected void registerGoals() {
   }

   public boolean removeWhenFarAway(double var1) {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   private Player trackedLookPlayer = null;
   private int lookScanTimer = 0;

   @Override
   protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
      return new net.minecraft.world.entity.ai.control.BodyRotationControl(this) {
         @Override
         public void clientTick() {
            if (GuideNpc.this.isPassenger() || GuideNpc.this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
               GuideNpc.this.yBodyRot = GuideNpc.this.getYRot();
               GuideNpc.this.yHeadRot = net.minecraft.util.Mth.rotateIfNecessary(GuideNpc.this.yHeadRot, GuideNpc.this.yBodyRot, (float)GuideNpc.this.getMaxHeadYRot());
            } else {
               float diff = net.minecraft.util.Mth.wrapDegrees(GuideNpc.this.yHeadRot - GuideNpc.this.yBodyRot);
               if (Math.abs(diff) > 45.0F) {
                  GuideNpc.this.yBodyRot += (diff - (diff > 0 ? 45.0F : -45.0F)) * 0.08F;
               }
            }
         }
      };
   }

   protected void customServerAiStep() {
      if (this.escort != null) {
         this.escort.tick(this);
         return;
      }

      if (this.customMoveTarget != null) {
         this.tickCustomMovement();
         this.tickCheckNodes();
         return;
      }

      // Smooth player tracking every tick if lookAtPlayer is enabled
      if (this.profile.lookAtPlayer) {
         if (--this.lookScanTimer <= 0) {
            this.lookScanTimer = 10;
            this.trackedLookPlayer = this.level().getNearestPlayer(this, 12.0);
         }
         if (this.trackedLookPlayer != null && this.trackedLookPlayer.isAlive() && !this.trackedLookPlayer.isSpectator() && this.distanceToSqr(this.trackedLookPlayer) <= 144.0) {
            this.getLookControl().setLookAt(this.trackedLookPlayer, 10.0F, 20.0F);
         } else {
            this.trackedLookPlayer = null;
         }
      }

      if (this.isTalking()) {
         this.mobilityPaused = true;
         this.getNavigation().stop();
         this.xxa = 0.0F;
         this.yya = 0.0F;
         this.zza = 0.0F;
         Vec3 movement = this.getDeltaMovement();
         this.setDeltaMovement(0.0, movement.y, 0.0);
         return;
      }

      this.tickMobility();
      this.tickCheckNodes();
   }

   private int checkScanTimer = 0;
   private final Map<Integer, Map<UUID, Long>> checkCooldowns = new HashMap<>();
   private final Map<Integer, Long> damageCooldowns = new HashMap<>();
   private final Map<Integer, Long> timeLastTriggerDays = new HashMap<>();
   private final Map<Integer, Long> timerLastTriggerTicks = new HashMap<>();
   private final Map<Integer, Map<UUID, Long>> walkCheckCooldowns = new HashMap<>();
   private final Map<Integer, Map<UUID, Long>> animCheckCooldowns = new HashMap<>();
   private final Map<Integer, Long> walkDamageCooldowns = new HashMap<>();
   private final Map<Integer, Long> animDamageCooldowns = new HashMap<>();
   private final Map<Integer, Long> walkTimeLastTriggerDays = new HashMap<>();
   private final Map<Integer, Long> animTimeLastTriggerDays = new HashMap<>();
   private final Map<Integer, Long> walkTimerLastTriggerTicks = new HashMap<>();
   private final Map<Integer, Long> animTimerLastTriggerTicks = new HashMap<>();

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (!this.level().isClientSide) {
         GuideDialogue.handleNpcDamaged(this, source, amount, this.damageCooldowns);
         GuideDialogue.handleNpcDamaged(this, source, amount, this.walkDamageCooldowns, this.profile.walkGraph);
         GuideDialogue.handleNpcDamaged(this, source, amount, this.animDamageCooldowns, this.profile.animGraph);
      }
      return super.hurt(source, amount);
   }

   public void tickCheckNodes() {
      tickCheckNodes(false);
   }

   public void tickCheckNodes(boolean force) {
      if (this.level().isClientSide() || this.isTalking() || !this.isAlive()) return;
      if (!force && ++this.checkScanTimer < 10) return;
      this.checkScanTimer = 0;

      if (GuideDialogue.graphActive(this.profile)) {
         NpcDialogueGraph graph = this.profile.dialogueGraph;
         if (graph != null) {
            GuideDialogue.tickGraphCheckNodes(this, graph, this.checkCooldowns);
            GuideDialogue.tickGraphTimeNodes(this, graph, this.timeLastTriggerDays);
            GuideDialogue.tickGraphTimerNodes(this, graph, this.timerLastTriggerTicks);
         }
      }
      this.tickSubGraph(this.profile.walkGraph, this.walkCheckCooldowns, this.walkTimeLastTriggerDays, this.walkTimerLastTriggerTicks);
      this.tickSubGraph(this.profile.animGraph, this.animCheckCooldowns, this.animTimeLastTriggerDays, this.animTimerLastTriggerTicks);
   }

   private void tickSubGraph(NpcDialogueGraph graph, Map<Integer, Map<UUID, Long>> checkCd, Map<Integer, Long> timeCd, Map<Integer, Long> timerCd) {
      if (graph == null) return;
      GuideDialogue.tickGraphCheckNodes(this, graph, checkCd);
      GuideDialogue.tickGraphTimeNodes(this, graph, timeCd);
      GuideDialogue.tickGraphTimerNodes(this, graph, timerCd);
   }

   private void tickMobility() {
      if (this.customMoveTarget != null) {
         return;
      }
      if (!this.profile.idleWalking) {
         if (this.activeMobilityTarget != null || this.mobilityWaitTicks > 0 || this.mobilityPaused) {
            this.resetMobility();
         }
         return;
      }
      // Existing schedule/group snapshots need refreshing once per game second, not 20 times/sec.
      long routeSecond = this.level() instanceof ServerLevel serverLevel
         ? Math.floorDiv(serverLevel.getDayTime(), 20L) : 0L;
      if (routeSecond != this.mobilityRouteCheckSecond) {
         this.mobilityRouteCheckSecond = routeSecond;
         this.updateActiveMobilityRoute(this.currentMobilityRoute());
      }
      List<NpcProfile.MobilityPoint> points = this.activeMobilityPoints;
      if (points.isEmpty()) {
         if (this.activeMobilityTarget != null || this.mobilityWaitTicks > 0 || this.mobilityPaused) {
            this.resetMobility();
         }
         return;
      }

      if (this.mobilityPaused) {
         this.mobilityPaused = false;
         if (this.activeMobilityTarget != null && !this.navigateTo(this.activeMobilityTarget)) {
            this.activeMobilityTarget = null;
            this.activeMobilityPoint = -1;
            this.mobilityWaitTicks = 20;
         }
      }

      if (this.activeMobilityTarget != null) {
         if (!this.getNavigation().isDone()) {
            return;
         }

         BlockPos target = this.activeMobilityTarget;
         int pointIndex = this.activeMobilityPoint;
         this.activeMobilityTarget = null;
         this.activeMobilityPoint = -1;
         if (this.nearMobilityTarget(target) && pointIndex >= 0 && pointIndex < points.size()) {
            this.lastMobilityPoint = pointIndex;
            this.mobilityWaitTicks = this.mobilityDurationTicks(points.get(pointIndex).duration);
            if (this.mobilityWatchFirstPoint && this.mobilityWatchNodeId >= 0) {
               this.mobilityWatchFirstPoint = false;
               int firedId = this.mobilityWatchNodeId;
               this.mobilityWatchNodeId = -1;
               this.triggerMobilityArrival(firedId);
            }
         } else {
            this.mobilityWaitTicks = 20;
         }
         return;
      }

      if (this.mobilityWaitTicks > 0) {
         this.mobilityWaitTicks--;
         return;
      }

      // If last visited point had an outgoing graph connection, follow the graph!
      if (this.lastMobilityPoint >= 0 && this.lastMobilityPoint < points.size()) {
         NpcProfile.MobilityPoint lastNode = points.get(this.lastMobilityPoint);
         if (lastNode != null && lastNode.next >= 0) {
            int nextIdx = this.resolveNextMobilityPoint(points, lastNode.next, 0);
            if (nextIdx >= 0 && nextIdx < points.size()) {
               NpcProfile.MobilityPoint nextPt = points.get(nextIdx);
               BlockPos target = this.mobilityPosition(nextPt.coordinates);
               if (target != null && this.navigateTo(target)) {
                  this.activeMobilityPoint = nextIdx;
                  this.activeMobilityTarget = target;
                  this.lastMobilityPoint = nextIdx;
                  return;
               }
            }
         }
      }

      ArrayList<Integer> eligible = new ArrayList<>();
      for (int i = 0; i < points.size(); i++) {
         NpcProfile.MobilityPoint point = points.get(i);
         if (point == null || point.chance <= 0) {
            continue;
         }
         if (this.mobilityPosition(point.coordinates) != null && this.getRandom().nextInt(100) < NpcProfile.clamp(point.chance, 0, 100)) {
            eligible.add(i);
         }
      }

      if (eligible.isEmpty()) {
         this.mobilityWaitTicks = 20;
         return;
      }

      int pointIndex = eligible.get(this.getRandom().nextInt(eligible.size()));
      NpcProfile.MobilityPoint point = points.get(pointIndex);
      BlockPos target = this.mobilityPosition(point.coordinates);
      if (target == null || !this.navigateTo(target)) {
         this.mobilityWaitTicks = 20;
         return;
      }
      this.activeMobilityPoint = pointIndex;
      this.activeMobilityTarget = target;
      this.lastMobilityPoint = pointIndex;
   }

   private int resolveNextMobilityPoint(List<NpcProfile.MobilityPoint> points, int startIdx, int depth) {
      if (depth > 20 || startIdx < 0 || startIdx >= points.size()) {
         return -1;
      }
      NpcProfile.MobilityPoint node = points.get(startIdx);
      if (node == null) return -1;
      if (node.type == NpcProfile.MobilityPoint.TYPE_POINT) {
         return startIdx;
      }
      if (node.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
         int min = Math.max(1, node.randomMin);
         int max = Math.max(min, node.randomMax);
         int picked = min + this.getRandom().nextInt(max - min + 1);
         int port = picked - 1;
         if (port >= 0 && port < node.randomBranches.size()) {
            int branchTarget = node.randomBranches.get(port);
            if (branchTarget >= 0) {
               return this.resolveNextMobilityPoint(points, branchTarget, depth + 1);
            }
         }
         return -1;
      }
      if (node.type == NpcProfile.MobilityPoint.TYPE_IF) {
         boolean success = this.getRandom().nextInt(100) < NpcProfile.clamp(node.chance, 0, 100);
         int branchTarget = success ? node.next : node.falseNext;
         if (branchTarget >= 0) {
            return this.resolveNextMobilityPoint(points, branchTarget, depth + 1);
         }
         return -1;
      }
      return -1;
   }

   private MobilityRoute currentMobilityRoute() {
      if (!this.profile.mobilitySchedule.isEmpty() && this.level() instanceof ServerLevel serverLevel) {
         long dayTicks = Math.floorMod(serverLevel.getDayTime(), NpcProfile.MOBILITY_DAY_SECONDS * 20L);
         int second = (int)(dayTicks / 20L);
         NpcProfile.MobilityScheduleEntry entry = this.profile.mobilityScheduleAt(second);
         if (entry != null) {
            NpcMobilityGroups.Group group = NpcMobilityGroups.get(serverLevel.getServer()).getGroup(entry.groupId);
            return new MobilityRoute("schedule:" + entry.groupId,
               group == null ? List.of() : copyMobilityPoints(group.points()));
         }
      }
      return new MobilityRoute("fallback:" + this.profile.mobilityGroupId, copyMobilityPoints(this.profile.mobilityPoints));
   }

   private void updateActiveMobilityRoute(MobilityRoute route) {
      if (java.util.Objects.equals(this.activeMobilityRouteId, route.id())
         && sameMobilityPoints(this.activeMobilityPoints, route.points())) {
         return;
      }
      this.getNavigation().stop();
      this.activeMobilityPoint = -1;
      this.activeMobilityTarget = null;
      this.mobilityWaitTicks = 0;
      this.mobilityPaused = false;
      this.activeMobilityRouteId = route.id();
      this.activeMobilityPoints = copyMobilityPoints(route.points());
   }

   private static List<NpcProfile.MobilityPoint> copyMobilityPoints(List<NpcProfile.MobilityPoint> source) {
      ArrayList<NpcProfile.MobilityPoint> copy = new ArrayList<>();
      if (source != null) {
         for (NpcProfile.MobilityPoint point : source) {
            if (point != null && copy.size() < NpcProfile.MAX_MOBILITY_POINTS) {
               copy.add(new NpcProfile.MobilityPoint(point.coordinates, point.chance, point.duration));
            }
         }
      }
      return List.copyOf(copy);
   }

   private static boolean sameMobilityPoints(List<NpcProfile.MobilityPoint> first, List<NpcProfile.MobilityPoint> second) {
      if (first.size() != second.size()) {
         return false;
      }
      for (int i = 0; i < first.size(); i++) {
         NpcProfile.MobilityPoint a = first.get(i);
         NpcProfile.MobilityPoint b = second.get(i);
         if (a == null ? b != null : !a.sameAs(b)) {
            return false;
         }
      }
      return true;
   }

   private BlockPos mobilityPosition(String coordinates) {
      if (coordinates == null || coordinates.isBlank()) {
         return null;
      }
      String[] values = coordinates.trim().split("[,\\s]+");
      if (values.length != 3) {
         return null;
      }
      try {
         int x = Integer.parseInt(values[0]);
         int y = Integer.parseInt(values[1]);
         int z = Integer.parseInt(values[2]);
         if (x < -29999984 || x > 29999984 || z < -29999984 || z > 29999984
            || y < this.level().getMinBuildHeight() || y >= this.level().getMaxBuildHeight()) {
            return null;
         }
         return new BlockPos(x, y, z);
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private boolean navigateTo(BlockPos target) {
      return this.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, this.walkingNavigationSpeed(false));
   }

   private boolean nearMobilityTarget(BlockPos target) {
      double dx = this.getX() - (target.getX() + 0.5);
      double dz = this.getZ() - (target.getZ() + 0.5);
      return dx * dx + dz * dz <= 4.0 && Math.abs(this.getY() - target.getY()) <= 2.0;
   }

   private int mobilityDurationTicks(String duration) {
      int minimum = 5;
      int maximum = 10;
      if (duration != null && !duration.isBlank()) {
         String[] values = duration.trim().split("\\s*[-–]\\s*", -1);
         try {
            if (values.length == 1) {
               minimum = maximum = Integer.parseInt(values[0]);
            } else if (values.length == 2) {
               minimum = Integer.parseInt(values[0]);
               maximum = Integer.parseInt(values[1]);
            } else {
               minimum = -1;
            }
         } catch (NumberFormatException ignored) {
            minimum = -1;
         }
      }
      if (minimum < 0 || maximum < 0) {
         minimum = 5;
         maximum = 10;
      }
      minimum = NpcProfile.clamp(minimum, 1, 86400);
      maximum = NpcProfile.clamp(maximum, 1, 86400);
      if (minimum > maximum) {
         int swap = minimum;
         minimum = maximum;
         maximum = swap;
      }
      int seconds = minimum + this.getRandom().nextInt(maximum - minimum + 1);
      return seconds * 20;
   }

   public SpawnGroupData finalizeSpawn(ServerLevelAccessor var1, DifficultyInstance var2, MobSpawnType var3, SpawnGroupData var4) {
      this.setPersistenceRequired();
      return super.finalizeSpawn(var1, var2, var3, var4);
   }

   public boolean teach(GuidePhrase var1) {
      if (var1 != null && !var1.text().isEmpty()) {
         for (GuidePhrase var3 : this.phrases) {
            if (var3.sameAs(var1)) {
               return false;
            }
         }

         if (this.phrases.size() >= 512) {
            return false;
         } else {
            this.phrases.add(var1);
            return true;
         }
      } else {
         return false;
      }
   }

   public int phraseCount() {
      return this.phrases.size();
   }

   public List<GuidePhrase> sortedPhrases() {
      ArrayList var1 = new ArrayList<>(this.phrases);
      Collections.sort(var1);
      return var1;
   }

   public boolean isEscorting() { return this.escort != null; }

   public NpcEscort escort() { return this.escort; }

   public void beginEscort(ServerPlayer player, String code, BlockPos destination) {
      this.resetMobility();
      this.escort = NpcEscort.begin(this, player, code, destination);
      this.getNavigation().setMaxVisitedNodesMultiplier(0.5F);
      GuideDialogue.closeNpcDialogues(this);
      this.applyProfile();
   }

   /** Normalise vanilla Mob forward input to default player walking; no permanent attribute edits. */
   public double walkingNavigationSpeed(boolean catchingUp) {
      return EscortRules.navigationSpeed(this.getAttributeValue(Attributes.MOVEMENT_SPEED), catchingUp);
   }

   public boolean walkTo(double x, double y, double z, double speedMultiplier) {
      if (this.level().isClientSide() || !this.isAlive()) return false;
      this.resetMobility();
      this.customMoveTarget = new Vec3(x, y, z);
      this.customMoveSpeed = Math.max(0.1, Math.min(5.0, speedMultiplier));
      this.customMoveRepathTicks = 0;
      this.customMoveFailures = 0;
      this.customMoveProgressTicks = 0;
      this.customMoveProgressX = this.getX();
      this.customMoveProgressY = this.getY();
      this.customMoveProgressZ = this.getZ();
      this.stepCustomMovement();
      return true;
   }

   public boolean isMovingToCustomTarget() {
      return this.customMoveTarget != null;
   }

   public void stopCustomMovement() {
      this.customMoveTarget = null;
      this.customMoveRepathTicks = 0;
      this.customMoveFailures = 0;
      this.customMoveProgressTicks = 0;
      if (!this.level().isClientSide()) {
         this.getNavigation().stop();
      }
   }

   private int mobilityWatchNodeId = -1;
   private boolean mobilityWatchFirstPoint = false;

   public void beginMobilityWatch(int nodeId) {
      this.mobilityWatchNodeId = nodeId;
      this.mobilityWatchFirstPoint = true;
      this.resetMobility();
      this.applyProfile();
      if (!this.activeMobilityPoints.isEmpty()) {
         NpcProfile.MobilityPoint p0 = this.activeMobilityPoints.get(0);
         BlockPos t = this.mobilityPosition(p0.coordinates);
         if (t != null && this.navigateTo(t)) {
            this.activeMobilityPoint = 0;
            this.activeMobilityTarget = t;
         }
      }
   }

   public boolean isWatchingMobilityArrival() {
      return this.mobilityWatchFirstPoint;
   }

   public void triggerMobilityArrival(int nodeId) {
      if (this.level().isClientSide() || !(this.level() instanceof ServerLevel level)) return;
      if (!GuideDialogue.graphActive(this.profile)) return;
      NpcDialogueGraph graph = this.profile.dialogueGraph;
      if (graph == null) return;
      NpcDialogueGraph.Node node = graph.get(nodeId);
      if (node == null || node.type != NpcDialogueGraph.MOBILITY) return;
      ServerPlayer player = level.getNearestPlayer(this, 32.0) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
      GuideDialogue.triggerMobilityChain(this, player, node);
   }

   public BlockPos findStablePosition(ServerLevel level, BlockPos origin) {
      if (origin == null) return null;
      for (int step = 0; step <= 12; step++) {
         int offset = step == 0 ? 0 : (step % 2 == 1 ? (step + 1) / 2 : -step / 2);
         BlockPos candidate = origin.offset(0, offset, 0);
         if (candidate.getY() >= level.getMinBuildHeight() && candidate.getY() < level.getMaxBuildHeight()
             && this.getNavigation().isStableDestination(candidate)
             && !level.getBlockState(candidate).isSolid()) {
            return candidate;
         }
      }
      return origin;
   }

   private void stepCustomMovement() {
      if (this.customMoveTarget == null || !(this.level() instanceof ServerLevel level)) return;
      double dx = this.customMoveTarget.x - this.getX();
      double dz = this.customMoveTarget.z - this.getZ();
      double dy = Math.abs(this.getY() - this.customMoveTarget.y);
      double distSq = dx * dx + dz * dz;

      if (distSq <= 2.25 && dy <= 2.5) {
         this.stopCustomMovement();
         return;
      }

      double dist = Math.sqrt(distSq);
      BlockPos targetBlock = BlockPos.containing(this.customMoveTarget.x, this.customMoveTarget.y, this.customMoveTarget.z);

      BlockPos waypoint;
      if (dist <= 16.0 && this.customMoveFailures == 0) {
         waypoint = findStablePosition(level, targetBlock);
      } else {
         double factor = dist > 16.0 ? 16.0 / dist : 1.0;
         double wx = this.getX() + dx * factor;
         double wz = this.getZ() + dz * factor;
         if (dist > 0.01 && this.customMoveFailures > 0) {
            int detour = this.customMoveFailures % 5;
            double side = switch (detour) { case 1 -> 3.0; case 2 -> -3.0; case 3 -> 6.0; case 4 -> -6.0; default -> 0.0; };
            wx -= (dz / dist) * side;
            wz += (dx / dist) * side;
         }
         int wy = (int)Math.floor(this.getY()) + Math.max(-4, Math.min(4, targetBlock.getY() - (int)Math.floor(this.getY())));
         waypoint = findStablePosition(level, BlockPos.containing(wx, wy, wz));
      }

      if (waypoint == null) {
         waypoint = targetBlock;
      }

      double baseSpeed = this.walkingNavigationSpeed(false);
      double effectiveSpeed = baseSpeed * this.customMoveSpeed;
      boolean started = this.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, effectiveSpeed);
      if (started) {
         this.customMoveRepathTicks = 20;
      } else {
         this.customMoveFailures = Math.min(this.customMoveFailures + 1, 10);
         this.customMoveRepathTicks = EscortRules.retryTicks(this.customMoveFailures);
         if (this.customMoveFailures >= 10) {
            this.stopCustomMovement();
         }
      }
   }

   private void tickCustomMovement() {
      if (this.customMoveTarget == null || !(this.level() instanceof ServerLevel level)) return;
      double dx = this.customMoveTarget.x - this.getX();
      double dz = this.customMoveTarget.z - this.getZ();
      double dy = Math.abs(this.getY() - this.customMoveTarget.y);
      double distSq = dx * dx + dz * dz;

      if (distSq <= 2.25 && dy <= 2.5) {
         this.stopCustomMovement();
         return;
      }

      if (++this.customMoveProgressTicks >= 100) {
         double pdx = this.getX() - this.customMoveProgressX;
         double pdy = this.getY() - this.customMoveProgressY;
         double pdz = this.getZ() - this.customMoveProgressZ;
         if (pdx * pdx + pdy * pdy + pdz * pdz < 0.25) {
            this.customMoveFailures = Math.min(this.customMoveFailures + 1, 10);
            this.customMoveRepathTicks = EscortRules.retryTicks(this.customMoveFailures);
            if (this.customMoveFailures >= 10) {
               this.stopCustomMovement();
               return;
            }
         } else {
            this.customMoveFailures = 0;
         }
         this.customMoveProgressX = this.getX();
         this.customMoveProgressY = this.getY();
         this.customMoveProgressZ = this.getZ();
         this.customMoveProgressTicks = 0;
      }

      if (this.customMoveRepathTicks > 0) {
         this.customMoveRepathTicks--;
         if (this.customMoveFailures > 0 || !this.getNavigation().isDone()) {
            return;
         }
      }

      this.stepCustomMovement();
   }

   public void finishEscort() {
      if (this.escort == null) return;
      GuideDialogue.closeNpcDialogues(this);
      NpcEscort.stopMotion(this);
      this.escort = null;
      this.getNavigation().resetMaxVisitedNodesMultiplier();
      this.resetMobility();
      this.applyProfile();
   }

   public NpcProfile profile() {
      return this.profile;
   }

   public void setProfile(NpcProfile var1) {
      NpcProfile next = var1 == null ? new NpcProfile() : var1;
      if (this.mobilitySettingsChanged(this.profile, next)) {
         this.resetMobility();
      }
      this.profile = next;
      this.applyProfile();
   }

   private boolean mobilitySettingsChanged(NpcProfile before, NpcProfile after) {
      if (before == null || after == null || before.idleWalking != after.idleWalking
         || !java.util.Objects.equals(before.mobilityGroupId, after.mobilityGroupId)
         || !java.util.Objects.equals(before.mobilityGroupName, after.mobilityGroupName)
         || before.mobilityPoints.size() != after.mobilityPoints.size()
         || before.mobilitySchedule.size() != after.mobilitySchedule.size()) {
         return true;
      }
      for (int i = 0; i < before.mobilityPoints.size(); i++) {
         NpcProfile.MobilityPoint oldPoint = before.mobilityPoints.get(i);
         NpcProfile.MobilityPoint newPoint = after.mobilityPoints.get(i);
         if (oldPoint == null ? newPoint != null : !oldPoint.sameAs(newPoint)) {
            return true;
         }
      }
      for (int i = 0; i < before.mobilitySchedule.size(); i++) {
         NpcProfile.MobilityScheduleEntry oldEntry = before.mobilitySchedule.get(i);
         NpcProfile.MobilityScheduleEntry newEntry = after.mobilitySchedule.get(i);
         if (oldEntry == null ? newEntry != null : !oldEntry.sameAs(newEntry)) {
            return true;
         }
      }
      return false;
   }

   private void resetMobility() {
      this.customMoveTarget = null;
      this.customMoveRepathTicks = 0;
      this.customMoveFailures = 0;
      this.customMoveProgressTicks = 0;
      this.mobilityRouteCheckSecond = Long.MIN_VALUE;
      this.getNavigation().stop();
      this.mobilityWaitTicks = 0;
      this.activeMobilityPoint = -1;
      this.lastMobilityPoint = -1;
      this.activeMobilityTarget = null;
      this.mobilityPaused = false;
   }

   public void applyProfile() {
      NpcProfile var1 = this.profile;
      if (var1.name != null && !var1.name.isEmpty()) {
         int nameColor = switch (NpcProfile.clamp(var1.nameColor, 0, 3)) {
            case 0 -> 0xFFFFFF;
            case 1 -> 0xFFE9A8;
            case 2 -> 0xFFD24A;
            default -> 0xBFBFBF;
         };
         this.setCustomName(Component.literal(var1.name).withStyle(style -> style.withColor(nameColor)));
      } else {
         this.setCustomName(null);
      }

      this.setCustomNameVisible(var1.nameTag);
      int clampedScale = NpcProfile.clamp(var1.scale, 50, 150);
      this.entityData.set(DATA_SCALE, clampedScale);
      AttributeInstance var2 = this.getAttribute(Attributes.SCALE);
      if (var2 != null) {
         var2.setBaseValue(clampedScale / 100.0);
      }
      this.refreshDimensions();

      this.setPoseIndex(this.isEscorting() ? 0 : var1.pose);
      this.entityData.set(DATA_TEXTURE, var1.texture == null ? "" : var1.texture);
   }

   public int relationTo(ServerPlayer var1) {
      return NpcRelations.get(var1.server).get(this.relationKey(), var1.getUUID(), this.profile.defaultRelation);
   }

   public static void addRelation(ServerPlayer var0, GuideNpc var1, int var2) {
      NpcRelations var3 = NpcRelations.get(var0.server);
      int var4 = var3.get(var1.relationKey(), var0.getUUID(), var1.profile().defaultRelation);
      var3.set(var1.relationKey(), var0.getUUID(), var0.getName().getString(), var4 + var2);
   }

   public void sendConfigView(ServerPlayer var1) {
      Frozen var2 = var1.server.registryAccess();
      CompoundTag var3 = new CompoundTag();
      var3.put("Profile", this.profile.save(var2));
      ListTag var4 = new ListTag();

      for (NpcRelations.Rel var6 : NpcRelations.get(var1.server).list(this.relationKey())) {
         CompoundTag var7 = new CompoundTag();
         var7.putString("Id", var6.player().toString());
         var7.putString("Name", var6.name());
         var7.putInt("V", var6.value());
         var4.add(var7);
      }

      var3.put("Relations", var4);
      var3.put("MobilityGroups", NpcMobilityGroups.get(var1.server).summaryTag());
      NpcNet.sendTo(var1, new NpcNet.CfgView(this.getId(), var3));
   }

   public static GuideNpc find(ServerPlayer var0, int var1) {
      GuideNpc var2 = null;
      if (var0.level().getEntity(var1) instanceof GuideNpc var3) {
         var2 = var3;
      } else {
         for (ServerLevel var5 : var0.server.getAllLevels()) {
            if (var5.getEntity(var1) instanceof GuideNpc var6) {
               var2 = var6;
               break;
            }
         }
      }

      return var2 != null && !(var0.distanceToSqr(var2) > 64.0) ? var2 : null;
   }

   public static void applyConfig(ServerPlayer var0, int var1, CompoundTag var2) {
      GuideNpc var3 = find(var0, var1);
      if (var3 != null && var2 != null) {
         if (!var0.hasPermissions(2)) {
            var0.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
         } else {
            if (var2.getBoolean("GraphOnly")) {
               boolean hasMain = var2.contains("DialogueGraph", 10);
               NpcDialogueGraph graph = hasMain ? NpcDialogueGraph.load(var2.getCompound("DialogueGraph")) : null;
               if (hasMain && graph == null) {
                  var0.displayClientMessage(Component.translatable("message.settledlands.graph.invalid"), true);
                  var3.sendConfigView(var0);
                  return;
               }
               NpcProfile graphProfile = var3.profile().copy();
               if (graph != null) graphProfile.dialogueGraph = graph;
               if (var2.contains("WalkGraph", 10)) {
                  NpcDialogueGraph walkSub = NpcDialogueGraph.load(var2.getCompound("WalkGraph"));
                  if (walkSub != null) graphProfile.walkGraph = walkSub;
               }
               if (var2.contains("AnimGraph", 10)) {
                  NpcDialogueGraph animSub = NpcDialogueGraph.load(var2.getCompound("AnimGraph"));
                  if (animSub != null) graphProfile.animGraph = animSub;
               }
               if (graph != null) graph.applySpecialLines(graphProfile);
               if ((graph != null && NpcDialogueGraph.encodedSize(graph.save()) > NpcDialogueGraph.MAX_PACKET_BYTES)
                   || NpcDialogueGraph.encodedSize(graphProfile.save(var0.server.registryAccess())) > 1800000) {
                  var0.displayClientMessage(Component.translatable("message.settledlands.graph.size"), true);
                  var3.sendConfigView(var0);
                  return;
               }
               var3.setProfile(graphProfile);
               var3.sendConfigView(var0);
               GuideDialogue.resend(var0, graphProfile);
               var0.displayClientMessage(Component.translatable("message.settledlands.cfg.saved"), true);
               return;
            }
            NpcProfile var4 = NpcProfile.load(var2, var0.server.registryAccess());
            NpcMobilityGroups mobilityCatalog = NpcMobilityGroups.get(var0.server);
            var4.mobilitySchedule.removeIf(entry -> entry == null || mobilityCatalog.getGroup(entry.groupId) == null);
            NpcProfile previous = var3.profile();
            if (java.util.Objects.equals(var4.mobilityGroupId, previous.mobilityGroupId)) {
               var4.mobilityGroupName = previous.mobilityGroupName;
               var4.mobilityPoints.clear();
               for (NpcProfile.MobilityPoint point : previous.mobilityPoints) {
                  if (point != null && var4.mobilityPoints.size() < NpcProfile.MAX_MOBILITY_POINTS) {
                     var4.mobilityPoints.add(new NpcProfile.MobilityPoint(point.coordinates, point.chance, point.duration));
                  }
               }
            } else if (!NpcMobilityGroups.get(var0.server).assignProfile(var4, var4.mobilityGroupId)) {
               var4.mobilityGroupId = previous.mobilityGroupId;
               var4.mobilityGroupName = previous.mobilityGroupName;
               var4.mobilityPoints.clear();
               for (NpcProfile.MobilityPoint point : previous.mobilityPoints) {
                  if (point != null && var4.mobilityPoints.size() < NpcProfile.MAX_MOBILITY_POINTS) {
                     var4.mobilityPoints.add(new NpcProfile.MobilityPoint(point.coordinates, point.chance, point.duration));
                  }
               }
            }
            var3.setProfile(var4);
            var3.sendConfigView(var0);
            GuideDialogue.resend(var0, var4);
            var0.displayClientMessage(Component.translatable("message.settledlands.cfg.saved"), true);
         }
      }
   }

   public static void applyEdit(ServerPlayer var0, int var1, String var2, int var3, int var4, int var5, String var6, String var7) {
      if (var1 == NpcNet.MOBILITY_EDITOR_ID) {
         NpcMobilityGroups.handleWandEdit(var0, var3, var5, var2, var6, var7);
         return;
      }
      GuideNpc var8 = find(var0, var1);
      if (var8 != null) {
         if (!var0.hasPermissions(2)) {
            var0.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
         } else if ("escort.cancel".equals(var2)) {
            var8.finishEscort();
            var8.sendConfigView(var0);
            var0.displayClientMessage(Component.translatable("message.settledlands.escort.cancelled"), true);
         } else if ("refresh".equals(var2)) {
            var8.sendConfigView(var0);
         } else if ("test".equals(var2)) {
            GuideDialogue.start(var0, var8);
         } else {
            if (var2 != null && var2.startsWith("mobility.schedule.")) {
               NpcProfile scheduledProfile = editMobilitySchedule(var8.profile(), var0.server, var2, var3, var7);
               if (scheduledProfile != null) {
                  var8.setProfile(scheduledProfile);
                  var8.sendConfigView(var0);
                  GuideDialogue.resend(var0, scheduledProfile);
               }
               return;
            }
            NpcProfile var9;
            if ("field".equals(var2) && "mobilityGroupId".equals(var6)) {
               var9 = var8.profile().copy();
               if (!NpcMobilityGroups.get(var0.server).assignProfile(var9, var7)) {
                  var9 = null;
               } else {
                  var9 = var9.sanitized();
               }
            } else {
               var9 = NpcEdit.apply(var8.profile(), var2, var3, var4, var5, var6, var7);
            }
            if (var9 != null) {
               var8.setProfile(var9);
               var8.sendConfigView(var0);
               GuideDialogue.resend(var0, var9);
            }
         }
      }
   }

   private static NpcProfile editMobilitySchedule(NpcProfile current, MinecraftServer server, String operation, int index, String value) {
      NpcProfile next = current.copy();
      NpcMobilityGroups catalog = NpcMobilityGroups.get(server);
      switch (operation) {
         case "mobility.schedule.add": {
            if (next.mobilitySchedule.size() >= NpcProfile.MAX_MOBILITY_SCHEDULE_ENTRIES) {
               return null;
            }
            List<NpcMobilityGroups.Group> groups = catalog.groups();
            if (groups.isEmpty()) {
               return null;
            }
            String groupId = next.mobilitySchedule.isEmpty() ? next.mobilityGroupId
               : next.mobilitySchedule.get(next.mobilitySchedule.size() - 1).groupId;
            if (catalog.getGroup(groupId) == null) {
               groupId = groups.get(0).id();
            }
            int start = 0;
            if (!next.mobilitySchedule.isEmpty()) {
               int latest = next.mobilitySchedule.stream().mapToInt(entry -> entry.second).max().orElse(-1);
               start = Math.floorMod(latest + 200, NpcProfile.MOBILITY_DAY_SECONDS);
            }
            int second = -1;
            for (int offset = 0; offset < NpcProfile.MOBILITY_DAY_SECONDS; offset++) {
               int candidate = (start + offset) % NpcProfile.MOBILITY_DAY_SECONDS;
               boolean used = next.mobilitySchedule.stream().anyMatch(entry -> entry.second == candidate);
               if (!used) {
                  second = candidate;
                  break;
               }
            }
            if (second < 0) {
               return null;
            }
            next.mobilitySchedule.add(new NpcProfile.MobilityScheduleEntry(second, groupId));
            break;
         }
         case "mobility.schedule.remove": {
            if (index < 0 || index >= next.mobilitySchedule.size()) {
               return null;
            }
            next.mobilitySchedule.remove(index);
            break;
         }
         case "mobility.schedule.time": {
            if (index < 0 || index >= next.mobilitySchedule.size()) {
               return null;
            }
            int second;
            try {
               second = Integer.parseInt(value == null ? "" : value.trim());
            } catch (NumberFormatException ignored) {
               return null;
            }
            if (second < 0 || second >= NpcProfile.MOBILITY_DAY_SECONDS) {
               return null;
            }
            for (int i = 0; i < next.mobilitySchedule.size(); i++) {
               if (i != index && next.mobilitySchedule.get(i).second == second) {
                  return null;
               }
            }
            next.mobilitySchedule.get(index).second = second;
            break;
         }
         case "mobility.schedule.group": {
            if (index < 0 || index >= next.mobilitySchedule.size() || catalog.getGroup(value) == null) {
               return null;
            }
            next.mobilitySchedule.get(index).groupId = NpcProfile.clean(value, 36);
            break;
         }
         default:
            return null;
      }
      return next.sanitized();
   }

   public static void applyRelation(ServerPlayer var0, int var1, String var2, int var3) {
      GuideNpc var4 = find(var0, var1);
      if (var4 != null) {
         if (!var0.hasPermissions(2)) {
            var0.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
         } else {
            String var5 = NpcProfile.clean(var2, 32);
            ServerPlayer var6 = null;

            for (ServerPlayer var8 : var0.server.getPlayerList().getPlayers()) {
               if (var8.getName().getString().equalsIgnoreCase(var5)) {
                  var6 = var8;
                  break;
               }
            }

            if (var6 == null) {
               var0.displayClientMessage(Component.translatable("message.settledlands.rel.noplayer", new Object[]{Component.literal(var5)}), true);
            } else {
               NpcRelations var9 = NpcRelations.get(var0.server);
               if (var3 < 0) {
                  var9.clear(var4.relationKey(), var6.getUUID());
                  var0.displayClientMessage(Component.translatable("message.settledlands.rel.reset", new Object[]{var6.getName()}), true);
               } else {
                  var9.set(var4.relationKey(), var6.getUUID(), var6.getName().getString(), var3);
                  var0.displayClientMessage(
                     Component.translatable(
                        "message.settledlands.rel.set", new Object[]{var6.getName(), Component.literal(String.valueOf(NpcProfile.clamp(var3, 0, 100)))}
                     ),
                     true
                  );
               }

               var4.sendConfigView(var0);
            }
         }
      }
   }

   protected InteractionResult mobInteract(Player var1, InteractionHand var2) {
      ItemStack var3 = var1.getItemInHand(var2);
      if (!this.level().isClientSide()) {
         if (var3.getItem() instanceof MobilityWandItem) {
            if (var1 instanceof ServerPlayer serverPlayer) {
               GuideDialogue.stop(serverPlayer);
               MobilityWandItem.assignToNpc(serverPlayer, var2, this);
            }
            return InteractionResult.sidedSuccess(false);
         }
         if (var3.getItem() instanceof NpcWandItem) {
            if (var1 instanceof ServerPlayer var8) {
               GuideDialogue.stop(var8);
               if (var8.hasPermissions(2)) {
                  this.sendConfigView(var8);
               } else {
                  var8.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
               }
            }

            return InteractionResult.sidedSuccess(false);
         }

         if (this.isEscorting()) {
            GuideDialogue.start(var1, this);
            return InteractionResult.sidedSuccess(false);
         }
         GuidePhrase var4 = GuideTomeItem.phraseOf(var3);
         if (var4 != null) {
            boolean var5 = this.teach(var4);
            if (var1 instanceof ServerPlayer var6) {
               var6.displayClientMessage(
                  Component.translatable(
                     var5 ? "message.settledlands.guide.learned" : "message.settledlands.guide.known",
                     new Object[]{Component.literal(String.valueOf(var4.number())), Component.literal(String.valueOf(this.phraseCount()))}
                  ),
                  true
               );
            }

            this.playSound(var5 ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.VILLAGER_NO, var5 ? 1.0F : 0.4F, var5 ? 1.05F : 0.9F);
            if (var5 && this.level() instanceof ServerLevel var9) {
               var9.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + this.getBbHeight() * 0.62, this.getZ(), 14, 0.55, 0.5, 0.55, 0.02);
            }
         } else if (var1 instanceof ServerPlayer var10 && GuideDialogue.listenToNpc(var10, this)) {
            return InteractionResult.sidedSuccess(false);
         } else {
            GuideDialogue.start(var1, this);
         }
      }

      return InteractionResult.sidedSuccess(this.level().isClientSide());
   }

   protected SoundEvent getAmbientSound() {
      return SoundEvents.SKELETON_AMBIENT;
   }

   protected SoundEvent getHurtSound(DamageSource var1) {
      return SoundEvents.SKELETON_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.SKELETON_DEATH;
   }

   protected float getSoundVolume() {
      return 0.6F;
   }

   public void addAdditionalSaveData(CompoundTag var1) {
      super.addAdditionalSaveData(var1);
      CompoundTag var2 = new CompoundTag();
      ListTag var3 = new ListTag();

      for (GuidePhrase var5 : this.phrases) {
         CompoundTag var6 = new CompoundTag();
         var6.putInt("N", var5.number());
         var6.putString("T", var5.text());
         var3.add(var6);
      }

      var2.put("Phrases", var3);
      var2.put("Profile", this.profile.save(this.level().registryAccess()));
      if (this.escort != null) var2.put("Escort", this.escort.save());
      var1.put("Guide", var2);
   }

   public void readAdditionalSaveData(CompoundTag var1) {
      super.readAdditionalSaveData(var1);
      this.phrases.clear();
      this.escort = null;
      if (!var1.contains("Guide", 10)) {
         this.profile = new NpcProfile();
      } else {
         CompoundTag var2 = var1.getCompound("Guide");
         ListTag var3 = var2.getList("Phrases", 10);

         for (int var4 = 0; var4 < var3.size() && this.phrases.size() < 512; var4++) {
            CompoundTag var5 = var3.getCompound(var4);
            String var6 = var5.getString("T").trim();
            if (!var6.isEmpty()) {
               this.phrases.add(new GuidePhrase(var5.getInt("N"), var6));
            }
         }

         this.profile = var2.contains("Profile", 10) ? NpcProfile.load(var2.getCompound("Profile"), this.level().registryAccess()) : new NpcProfile();
         if (var2.contains("sl_copy_src")
            && this.level() instanceof ServerLevel slServer
            && slServer.getEntity(var2.getUUID("sl_copy_src")) instanceof GuideNpc srcNpc) {
            this.profile = NpcProfile.load(srcNpc.profile().save(this.level().registryAccess()), this.level().registryAccess());
         }
         if (var2.contains("Escort", 10)) {
            this.escort = NpcEscort.load(var2.getCompound("Escort"), this.level().getMinBuildHeight(), this.level().getMaxBuildHeight());
            if (this.escort != null) this.getNavigation().setMaxVisitedNodesMultiplier(0.5F);
         }
         this.applyProfile();
         if (this.level() instanceof ServerLevel var7) {
            NpcRelations var9 = NpcRelations.get(var7.getServer());
            var9.migrateNpc(this.getUUID(), this.relationKey());
            List var10 = NpcQuests.questIds(this);
            var9.migrateQuestSlotsToIds(this.getUUID(), var10);
            var9.migrateQuestSlotsToIds(this.relationKey(), var10);
         }
      }
   }

   private record MobilityRoute(String id, List<NpcProfile.MobilityPoint> points) {
   }
}
