package dev.settledlands;

import dev.settledlands.npc.NpcPointsCommand;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class WorldEvents {
   private static final String ORIGIN = "settledlands_origin";
   private final Map<UUID, WorldEvents.Visit> visits = new HashMap<>();
   public final Map<UUID, Integer> debugging = new HashMap<>();
   private final Map<EntityType<?>, Boolean> eligibility = new IdentityHashMap<>();

   private boolean eligible(EntityType<?> var1) {
      return this.eligibility
         .computeIfAbsent(
            var1,
            var0 -> {
               String var1x = BuiltInRegistries.ENTITY_TYPE.getKey(var0).toString();
               return !((List)Settings.EXCLUDED.get()).contains(var1x)
                  && (var0.getCategory() == MobCategory.MONSTER || ((List)Settings.INCLUDED.get()).contains(var1x));
            }
         );
   }

   private long now(ServerLevel var1) {
      return ActivityClock.get(var1.getServer()).seconds;
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void spawn(PositionCheck var1) {
      if (var1.getSpawnType() == MobSpawnType.NATURAL && this.eligible(var1.getEntity().getType()) && var1.getResult() != Result.FAIL) {
         ServerLevel var2 = var1.getLevel().getLevel();
         TerritoryData var3 = TerritoryData.get(var2);
         long var4 = Cell.at(var1.getEntity().blockPosition());
         double var6 = Sanctity.count(var2, var4) > 0 ? 1.0 : var3.score(var4, this.now(var2)) / ((Integer)Settings.TEMP_MAX.get()).intValue();
         if (var6 >= 1.0 || var6 > 0.0 && var2.random.nextDouble() < var6) {
            var1.setResult(Result.FAIL);
            var3.deniedSpawns++;
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void origin(FinalizeSpawnEvent var1) {
      if (this.eligible(var1.getEntity().getType()) && !var1.isSpawnCancelled()) {
         CompoundTag var2 = new CompoundTag();
         var2.putBoolean("Natural", var1.getSpawnType() == MobSpawnType.NATURAL);
         var2.putLong("Cell", Cell.at(var1.getEntity().blockPosition()));
         var2.putString("Dimension", var1.getLevel().getLevel().dimension().location().toString());
         var1.getEntity().getPersistentData().put("settledlands_origin", var2);
      }
   }

   @SubscribeEvent
   public void conversion(Post var1) {
      if (var1.getEntity().getPersistentData().contains("settledlands_origin")) {
         var1.getOutcome().getPersistentData().put("settledlands_origin", var1.getEntity().getPersistentData().getCompound("settledlands_origin").copy());
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void death(LivingDeathEvent var1) {
      LivingEntity var2 = var1.getEntity();
      if (var2.level() instanceof ServerLevel var3 && this.eligible(var2.getType())) {
         boolean var20 = var1.getSource().getEntity() instanceof Player || var2.lastHurtByPlayerTime > 0 && var2.lastHurtByPlayer != null;
         if (var20) {
            CompoundTag var5 = var2.getPersistentData();
            boolean var6 = var5.contains("settledlands_origin");
            CompoundTag var7 = var5.getCompound("settledlands_origin");
            if (var6 ? var7.getBoolean("Natural") : (Boolean)Settings.UNKNOWN.get()) {
               if (!var5.getBoolean("settledlands_credited")) {
                  var5.putBoolean("settledlands_credited", true);
                  long var8 = this.now(var3);
                  long var10 = Cell.at(var2.blockPosition());
                  TerritoryData var12 = TerritoryData.get(var3);
                  int var13 = (Integer)Settings.KILL_RADIUS.get();

                  for (int var14 = -var13; var14 <= var13; var14++) {
                     for (int var15 = -var13; var15 <= var13; var15++) {
                        var12.credit(Cell.offset(var10, var14, var15), var8);
                     }
                  }

                  if (var6) {
                     ResourceLocation var21 = ResourceLocation.tryParse(var7.getString("Dimension"));
                     if (var21 == null) {
                        return;
                     }

                     ResourceKey var22 = ResourceKey.create(Registries.DIMENSION, var21);
                     long var16 = var7.getLong("Cell");
                     boolean var18 = var22.equals(var3.dimension())
                        && BlockPos.getY(var16) == BlockPos.getY(var10)
                        && Math.abs(BlockPos.getX(var16) - BlockPos.getX(var10)) <= var13
                        && Math.abs(BlockPos.getZ(var16) - BlockPos.getZ(var10)) <= var13;
                     if (!var18) {
                        ServerLevel var19 = var3.getServer().getLevel(var22);
                        if (var19 != null) {
                           TerritoryData.get(var19).credit(var16, var8);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void place(EntityPlaceEvent var1) {
      if (var1.getEntity() instanceof ServerPlayer && var1.getLevel() instanceof ServerLevel var2) {
         TerritoryData var9 = TerritoryData.get(var2);
         long var4 = this.now(var2);
         if (var1 instanceof EntityMultiPlaceEvent var6) {
            for (BlockSnapshot var8 : var6.getReplacedBlockSnapshots()) {
               var9.unregister(var8.getPos());
               var9.register(var8.getPos(), Household.category(var2.getBlockState(var8.getPos())), var4);
            }
         } else {
            var9.unregister(var1.getPos());
            var9.register(var1.getPos(), Household.category(var1.getPlacedBlock()), var4);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public void broken(BreakEvent var1) {
      if (var1.getLevel() instanceof ServerLevel var2) {
         TerritoryData.get(var2).unregister(var1.getPos());
      }
   }

   @SubscribeEvent
   public void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post var1) {
      MinecraftServer var2 = var1.getServer();
      if (var2.getTickCount() % 20 == 0) {
         ActivityClock var3 = ActivityClock.get(var2);
         List var4 = var2.getPlayerList().getPlayers();
         boolean var5 = var4.stream().anyMatch(var0 -> !var0.isSpectator());
         if (var5 || !(Boolean)Settings.PAUSE_EMPTY.get()) {
            var3.advance();
         }

         long var6 = var3.seconds;
         HashSet var8 = new HashSet();
         HashSet var9 = new HashSet();

         for (ServerPlayer var11 : var4) {
            if (!var11.isSpectator() && var11.isAlive()) {
               var8.add(var11.getUUID());
               ServerLevel var12 = var11.serverLevel();
               TerritoryData var13 = TerritoryData.get(var12);
               WorldEvents.Visit var14 = this.visits.get(var11.getUUID());
               boolean var15 = var14 != null
                  && var14.place.dimension.equals(var12.dimension())
                  && var13.household(var14.place.cell)
                  && var13.within(var14.place.cell, var11.blockPosition(), 28.0);
               if (!var15) {
                  Long var16 = var13.nearestHousehold(var11.blockPosition());
                  if (var16 == null) {
                     this.visits.remove(var11.getUUID());
                     continue;
                  }

                  var14 = new WorldEvents.Visit(new WorldEvents.Place(var12.dimension(), var16), var6);
                  this.visits.put(var11.getUUID(), var14);
               }

               if (var6 - var14.since >= ((Integer)Settings.VISIT_GRACE.get()).intValue()) {
                  var9.add(var14.place);
               }
            } else {
               this.visits.remove(var11.getUUID());
            }
         }

         this.visits.keySet().retainAll(var8);

         for (WorldEvents.Place var21 : var9) {
            ServerLevel var25 = var2.getLevel(var21.dimension);
            if (var25 != null) {
               TerritoryData.get(var25).occupy(var21.cell);
            }
         }

         for (ServerLevel var22 : var2.getAllLevels()) {
            TerritoryData.get(var22).validate(var22);
         }

         if (var6 % 30L == 0L) {
            for (ServerLevel var23 : var2.getAllLevels()) {
               TerritoryData.get(var23).prune(var6, 4096);
            }
         }

         for (ServerPlayer var24 : var4) {
            if (this.debugging.containsKey(var24.getUUID())) {
               if (!var24.hasPermissions(2)) {
                  this.debugging.remove(var24.getUUID());
                  DebugPayload.clear(var24);
               } else {
                  DebugPayload.send(var24, this.debugging.get(var24.getUUID()), var6);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void logout(PlayerLoggedOutEvent var1) {
      this.visits.remove(var1.getEntity().getUUID());
      this.debugging.remove(var1.getEntity().getUUID());
   }

   @SubscribeEvent
   public void dimension(PlayerChangedDimensionEvent var1) {
      this.visits.remove(var1.getEntity().getUUID());
   }

   @SubscribeEvent
   public void respawn(PlayerRespawnEvent var1) {
      this.visits.remove(var1.getEntity().getUUID());
   }

   @SubscribeEvent
   public void stopped(ServerStoppedEvent var1) {
      this.visits.clear();
      this.debugging.clear();
      this.eligibility.clear();
   }

   @SubscribeEvent
   public void commands(RegisterCommandsEvent var1) {
      ModCommands.register(var1.getDispatcher(), this);
      NpcPointsCommand.register(var1.getDispatcher());
      NpcPointsCommand.registerAdd(var1.getDispatcher());
      NpcPointsCommand.registerPointList(var1.getDispatcher());
   }

   private record Place(ResourceKey<Level> dimension, long cell) {
   }

   private record Visit(WorldEvents.Place place, long since) {
   }
}
