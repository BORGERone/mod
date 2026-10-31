package dev.settledlands.revival;

import dev.settledlands.npc.GuideNpc;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class PurgatoryManager {
   public static final String MODID = "settledlands";
   public static final ResourceKey<Level> PURGATORY_LEVEL = ResourceKey.create(
      Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("settledlands", "purgatory")
   );
   private static final Set<RelativeMovement> NO_RELATIVES = Collections.emptySet();
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("settledlands");
   public static final Items ITEMS = DeferredRegister.createItems("settledlands");
   public static final DeferredBlock<RevivalAltarBlock> REVIVAL_ALTAR = BLOCKS.registerBlock(
      "revival_altar", RevivalAltarBlock::new, Properties.of().strength(3.5F).sound(SoundType.METAL)
   );
   public static final DeferredItem<?> REVIVAL_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(REVIVAL_ALTAR);
   private static final int EMERALDS_REQUIRED = 9;
   private static final BlockPos FIRST_SPAWN = new BlockPos(-223, 83, -236);
   private static final BlockPos LATER_SPAWN = new BlockPos(-155, 45, -393);
   private static final int FATIGUE_AMPLIFIER = 2;
   private static final int FATIGUE_DURATION = 600;
   private static final String NBT_AWAITING_PURGATORY = "settledlands_awaiting_purgatory";
   private static final long[] REMOVE_BUF = new long[64];
   private static final List<PurgatoryManager.Whisper> WHISPERS = new ArrayList<>();

   private PurgatoryManager() {
   }

   public static void init(IEventBus var0) {
      BLOCKS.register(var0);
      ITEMS.register(var0);
      var0.addListener(PurgatoryManager::onBuildCreativeTab);
      NeoForge.EVENT_BUS.register(PurgatoryManager.class);
   }

   private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent var0) {
      ResourceKey var1 = var0.getTabKey();
      if (var1 != null && var1.location() != null) {
         String var2 = var1.location().getPath();
         if ("functional_blocks".equals(var2) || "ingredients".equals(var2)) {
            var0.accept(new ItemStack((ItemLike)REVIVAL_ALTAR.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onPlayerDeath(LivingDeathEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer) {
         ServerPlayer var1 = (ServerPlayer)var0.getEntity();
         MinecraftServer var2 = var1.serverLevel().getServer();
         if (var2 != null) {
            PurgatoryData var3 = PurgatoryData.getMaster(var2);
            var3.enqueueDead(var1.getUUID());
            var1.getPersistentData().putBoolean("settledlands_awaiting_purgatory", true);
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onPlayerRespawn(PlayerRespawnEvent var0) {
      if (!var0.isEndConquered() && var0.getEntity() instanceof ServerPlayer) {
         ServerPlayer var1 = (ServerPlayer)var0.getEntity();
         MinecraftServer var2 = var1.serverLevel().getServer();
         if (var2 != null) {
            UUID var3 = var1.getUUID();
            PurgatoryData var4 = PurgatoryData.getMaster(var2);
            PurgatoryData.TargetPos var5 = var4.consumePendingRevive(var3);
            if (var5 != null) {
               var1.getPersistentData().putBoolean("settledlands_awaiting_purgatory", false);
               teleportToTarget(var2, var1, var5);
            } else {
               var1.getPersistentData().putBoolean("settledlands_awaiting_purgatory", false);
               var4.enqueueDead(var3);
               boolean var6 = var4.consumeFirstPurgatorySpawn(var3);
               teleportToPurgatory(var2, var1, var6);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer) {
         ServerPlayer var1 = (ServerPlayer)var0.getEntity();
         MinecraftServer var2 = var1.serverLevel().getServer();
         if (var2 != null) {
            UUID var3 = var1.getUUID();
            PurgatoryData var4 = PurgatoryData.getMaster(var2);
            PurgatoryData.TargetPos var5 = var4.consumePendingRevive(var3);
            if (var5 != null) {
               var1.getPersistentData().putBoolean("settledlands_awaiting_purgatory", false);
               teleportToTarget(var2, var1, var5);
            } else if (var4.isDeadInQueue(var3) && !PURGATORY_LEVEL.equals(var1.serverLevel().dimension())) {
               if (var1.getPersistentData().getBoolean("settledlands_awaiting_purgatory")) {
                  boolean var6 = var4.consumeFirstPurgatorySpawn(var3);
                  teleportToPurgatory(var2, var1, var6);
               } else {
                  var4.removeFromQueue(var3);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onDimensionChange(PlayerChangedDimensionEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer) {
         ServerPlayer var1 = (ServerPlayer)var0.getEntity();
         if (PURGATORY_LEVEL.equals(var0.getFrom()) && !PURGATORY_LEVEL.equals(var0.getTo())) {
            var1.removeEffect(MobEffects.DIG_SLOWDOWN);
            MinecraftServer var2 = var1.serverLevel().getServer();
            if (var2 != null) {
               PurgatoryData.getMaster(var2).removeFromQueue(var1.getUUID());
            }
         }
      }
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent var0) {
      if (var0.getPlayer() != null && !var0.getPlayer().isCreative() && PURGATORY_LEVEL.equals(var0.getPlayer().level().dimension())) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onMobSpawnPosition(PositionCheck var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && PURGATORY_LEVEL.equals(var1.dimension())) {
         MobSpawnType var3 = var0.getSpawnType();
         if (var3 == MobSpawnType.NATURAL
            || var3 == MobSpawnType.CHUNK_GENERATION
            || var3 == MobSpawnType.PATROL
            || var3 == MobSpawnType.STRUCTURE
            || var3 == MobSpawnType.REINFORCEMENT) {
            var0.setResult(Result.FAIL);
         }
      }
   }

   private static void keepPurgatoryFatigue(ServerPlayer var0) {
      if (!var0.isCreative()) {
         MobEffectInstance var1 = var0.getEffect(MobEffects.DIG_SLOWDOWN);
         if (var1 == null || var1.getDuration() < 200) {
            var0.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 2, false, false, true));
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(Post var0) {
      MinecraftServer var1 = var0.getServer();
      int var2 = var1.getTickCount();
      if (!WHISPERS.isEmpty()) {
         WHISPERS.removeIf(var2x -> {
            if (var2x.atTick() > var2) {
               return false;
            } else {
               ServerPlayer var3x = var1.getPlayerList().getPlayer(var2x.player());
               ServerLevel var4x = var1.getLevel(PURGATORY_LEVEL);
               if (var3x != null && var4x != null && PURGATORY_LEVEL.equals(var3x.serverLevel().dimension())) {
                  whisperGuide(var4x, var3x, var2x.pos());
               }

               return true;
            }
         });
      }

      if ((var2 & 7) == 0) {
         PurgatoryData var3 = PurgatoryData.getMaster(var1);
         List var4 = var1.getPlayerList().getPlayers();
         int var5 = 0;

         for (int var6 = var4.size(); var5 < var6; var5++) {
            ServerPlayer var7 = (ServerPlayer)var4.get(var5);
            if (var7.isAlive() && !var7.isSpectator()) {
               UUID var8 = var7.getUUID();
               if (var3.hasPendingRevive(var8)) {
                  PurgatoryData.TargetPos var9 = var3.consumePendingRevive(var8);
                  if (var9 != null) {
                     teleportToTarget(var1, var7, var9);
                     continue;
                  }
               }

               if (PURGATORY_LEVEL.equals(var7.serverLevel().dimension())) {
                  if (!var3.isDeadInQueue(var8)) {
                     var3.enqueueDead(var8);
                  }

                  keepPurgatoryFatigue(var7);
               } else if (var3.isDeadInQueue(var8)) {
                  var3.removeFromQueue(var8);
               }
            }
         }

         if (var3.hasDeadPlayers()) {
            for (ServerLevel var11 : var1.getAllLevels()) {
               if (!var3.hasDeadPlayers()) {
                  break;
               }

               tickLevelAltars(var11);
            }
         }
      }
   }

   private static void tickLevelAltars(ServerLevel var0) {
      PurgatoryData var1 = PurgatoryData.get(var0);
      LongOpenHashSet var2 = var1.altars();
      if (!var2.isEmpty()) {
         int var3 = 0;
         RevivalAltarBlock var4 = (RevivalAltarBlock)REVIVAL_ALTAR.get();
         LongIterator var5 = var2.iterator();

         while (var5.hasNext()) {
            long var6 = var5.nextLong();
            BlockPos var8 = BlockPos.of(var6);
            if (var0.hasChunkAt(var8)) {
               if (!var0.getBlockState(var8).is(var4)) {
                  if (var3 < REMOVE_BUF.length) {
                     REMOVE_BUF[var3++] = var6;
                  }
               } else if (tryReviveAtAltar(var0, var8)) {
                  MinecraftServer var9 = var0.getServer();
                  if (var9 != null && !PurgatoryData.getMaster(var9).hasDeadPlayers()) {
                     break;
                  }
               }
            }
         }

         for (int var10 = 0; var10 < var3; var10++) {
            var1.removeAltar(REMOVE_BUF[var10]);
         }
      }
   }

   public static boolean tryReviveAtAltar(ServerLevel var0, BlockPos var1) {
      MinecraftServer var2 = var0.getServer();
      if (var2 == null) {
         return false;
      } else {
         PurgatoryData var3 = PurgatoryData.getMaster(var2);
         if (!var3.hasDeadPlayers()) {
            return false;
         } else {
            AABB var4 = new AABB(var1.getX(), var1.getY() + 0.9, var1.getZ(), var1.getX() + 1.0, var1.getY() + 1.75, var1.getZ() + 1.0);
            List var5 = var0.getEntitiesOfClass(ItemEntity.class, var4, PurgatoryManager::isValidEmeraldEntity);
            if (var5.isEmpty()) {
               return false;
            } else {
               int var6 = 0;
               int var7 = 0;

               for (int var8 = var5.size(); var7 < var8; var7++) {
                  var6 += ((ItemEntity)var5.get(var7)).getItem().getCount();
                  if (var6 >= 9) {
                     break;
                  }
               }

               if (var6 < 9) {
                  return false;
               } else {
                  UUID var16 = var3.pollFirstDead();
                  if (var16 == null) {
                     return false;
                  } else {
                     int var9 = 9;
                     int var10 = 0;

                     for (int var11 = var5.size(); var10 < var11 && var9 > 0; var10++) {
                        ItemEntity var12 = (ItemEntity)var5.get(var10);
                        ItemStack var13 = var12.getItem();
                        int var14 = var13.getCount();
                        if (var14 <= var9) {
                           var9 -= var14;
                           var12.discard();
                        } else {
                           ItemStack var15 = var13.copy();
                           var15.shrink(var9);
                           var12.setItem(var15);
                           var9 = 0;
                        }
                     }

                     spawnVisualLightning(var0, var1);
                     String var17 = var0.dimension().location().getNamespace() + ":" + var0.dimension().location().getPath();
                     ServerPlayer var18 = var2.getPlayerList().getPlayer(var16);
                     if (var18 != null && var18.isAlive()) {
                        var18.getPersistentData().putBoolean("settledlands_awaiting_purgatory", false);
                        teleportPlayerOntoAltar(var0, var18, var1);
                     } else {
                        var3.setPendingRevive(var16, var17, var1);
                     }

                     return true;
                  }
               }
            }
         }
      }
   }

   private static boolean isValidEmeraldEntity(ItemEntity var0) {
      if (var0 != null && var0.isAlive()) {
         ItemStack var1 = var0.getItem();
         return !var1.isEmpty() && var1.is(net.minecraft.world.item.Items.EMERALD);
      } else {
         return false;
      }
   }

   private static void spawnVisualLightning(ServerLevel var0, BlockPos var1) {
      LightningBolt var2 = (LightningBolt)EntityType.LIGHTNING_BOLT.create(var0);
      if (var2 != null) {
         var2.moveTo(var1.getX() + 0.5, var1.getY() + 1.0, var1.getZ() + 0.5);
         var2.setVisualOnly(true);
         var0.addFreshEntity(var2);
      }
   }

   public static void teleportToPurgatory(MinecraftServer var0, ServerPlayer var1, boolean var2) {
      ServerLevel var3 = var0.getLevel(PURGATORY_LEVEL);
      if (var3 != null) {
         BlockPos var4 = var2 ? FIRST_SPAWN : LATER_SPAWN;
         double var5 = var4.getX() + 0.5;
         double var7 = var4.getY();
         double var9 = var4.getZ() + 0.5;
         var1.fallDistance = 0.0F;
         var1.clearFire();
         var1.setDeltaMovement(Vec3.ZERO);
         var1.teleportTo(var3, var5, var7, var9, NO_RELATIVES, var1.getYRot(), var1.getXRot());
         var1.displayClientMessage(Component.translatable("message.settledlands.purgatory.arrived"), false);
         if (var2) {
            WHISPERS.add(new PurgatoryManager.Whisper(var1.getUUID(), var4, var0.getTickCount() + 40L));
         }
      }
   }

   private static void whisperGuide(ServerLevel var0, ServerPlayer var1, BlockPos var2) {
      Object var3 = Component.translatable("entity.settledlands.guide");
      AABB var4 = new AABB(var2).inflate(64.0);
      List var5 = var0.getEntitiesOfClass(GuideNpc.class, var4);
      if (!var5.isEmpty()) {
         Component var6 = ((GuideNpc)var5.get(0)).getDisplayName();
         if (var6 != null && !var6.getString().isEmpty()) {
            var3 = var6;
         }
      }

      var1.displayClientMessage(
         Component.translatable("message.settledlands.purgatory.whisperer", new Object[]{var3})
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(
               Component.translatable("message.settledlands.purgatory.whisper").withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC})
            ),
         false
      );
   }

   private static void teleportToTarget(MinecraftServer var0, ServerPlayer var1, PurgatoryData.TargetPos var2) {
      ResourceLocation var3 = ResourceLocation.tryParse(var2.dimension);
      ServerLevel var4 = null;
      if (var3 != null) {
         ResourceKey var5 = ResourceKey.create(Registries.DIMENSION, var3);
         var4 = var0.getLevel(var5);
      }

      if (var4 == null) {
         var4 = var0.overworld();
      }

      BlockPos var6 = BlockPos.of(var2.posLong);
      teleportPlayerOntoAltar(var4, var1, var6);
   }

   private static void teleportPlayerOntoAltar(ServerLevel var0, ServerPlayer var1, BlockPos var2) {
      double var3 = var2.getX() + 0.5;
      double var5 = var2.getY() + 1.05;
      double var7 = var2.getZ() + 0.5;
      var1.fallDistance = 0.0F;
      var1.clearFire();
      var1.setDeltaMovement(Vec3.ZERO);
      var1.teleportTo(var0, var3, var5, var7, NO_RELATIVES, var1.getYRot(), 0.0F);
   }

   private record Whisper(UUID player, BlockPos pos, long atTick) {
   }
}
