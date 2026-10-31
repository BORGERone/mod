package dev.settledlands.npc;

import dev.settledlands.revival.PurgatoryManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Row;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(
   modid = "settledlands",
   bus = Bus.MOD
)
public final class NpcContent {
   public static final String MOD_ID = "settledlands";
   private static EntityType<GuideNpc> guide;
   private static Item spawnEgg;
   private static Item tome;
   private static Item wand;
   private static Item cutsceneWand;
   private static Item mobilityWand;
   private static Item coordStick;
   private static Block mobBarrier;
   private static Item mobBarrierItem;
   private static CreativeModeTab tab;

   private NpcContent() {
   }

   public static ResourceLocation id(String var0) {
      return ResourceLocation.fromNamespaceAndPath("settledlands", var0);
   }

   public static EntityType<GuideNpc> guide() {
      if (guide == null) {
         guide = Builder.of(GuideNpc::new, MobCategory.CREATURE).sized(1.35F, 4.2F).eyeHeight(3.5F).clientTrackingRange(10).build("settledlands:guide");
      }
      return guide;
   }

   public static Item spawnEgg() {
      if (spawnEgg == null) {
         spawnEgg = new DeferredSpawnEggItem(NpcContent::guide, 2890556, 12571903, new Properties());
      }
      return spawnEgg;
   }

   public static Item tome() {
      if (tome == null) {
         tome = new GuideTomeItem(new Properties().stacksTo(1));
      }
      return tome;
   }

   public static Item wand() {
      if (wand == null) {
         wand = new NpcWandItem(new Properties().stacksTo(1));
      }
      return wand;
   }

   public static Item cutsceneWand() {
      if (cutsceneWand == null) {
         cutsceneWand = new CutsceneWandItem(new Properties().stacksTo(1));
      }
      return cutsceneWand;
   }

   public static Item mobilityWand() {
      if (mobilityWand == null) {
         mobilityWand = new MobilityWandItem(new Properties().stacksTo(1));
      }
      return mobilityWand;
   }

   public static Item coordStick() {
      if (coordStick == null) {
         coordStick = new CoordStickItem(new Properties().stacksTo(1));
      }
      return coordStick;
   }

   public static Block mobBarrier() {
      if (mobBarrier == null) {
         mobBarrier = new MobBarrierBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
               .mapColor(MapColor.NONE)
               .strength(-1.0F, 3600000.0F)
               .sound(SoundType.GLASS)
               .noLootTable()
               .noOcclusion()
               .isViewBlocking((var0, var1, var2) -> false)
               .isSuffocating((var0, var1, var2) -> false)
         );
      }
      return mobBarrier;
   }

   public static Item mobBarrierItem() {
      if (mobBarrierItem == null) {
         mobBarrierItem = new BlockItem(mobBarrier(), new Properties());
      }
      return mobBarrierItem;
   }

   @SubscribeEvent
   public static void register(RegisterEvent var0) {
      if (var0.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
         var0.register(Registries.ENTITY_TYPE, id("guide"), NpcContent::guide);
      } else if (var0.getRegistryKey().equals(Registries.BLOCK)) {
         var0.register(Registries.BLOCK, id("mob_barrier"), NpcContent::mobBarrier);
      } else if (var0.getRegistryKey().equals(Registries.ITEM)) {
         var0.register(Registries.ITEM, id("guide_spawn_egg"), NpcContent::spawnEgg);
         var0.register(Registries.ITEM, id("guide_tome"), NpcContent::tome);
         var0.register(Registries.ITEM, id("npc_wand"), NpcContent::wand);
         var0.register(Registries.ITEM, id("mobility_wand"), NpcContent::mobilityWand);
         var0.register(Registries.ITEM, id("coord_stick"), NpcContent::coordStick);
         var0.register(Registries.ITEM, id("cutscene_wand"), NpcContent::cutsceneWand);
         var0.register(Registries.ITEM, id("mob_barrier"), NpcContent::mobBarrierItem);
      } else if (var0.getRegistryKey().equals(Registries.COMMAND_ARGUMENT_TYPE)) {
         var0.register(Registries.COMMAND_ARGUMENT_TYPE, id("point_name"), () -> PointNameArgument.INFO);
         PointNameArgument.registerClassMapping();
      } else if (var0.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
         var0.register(Registries.CREATIVE_MODE_TAB, id("guide"), NpcContent::creativeTab);
      }
   }

   @SubscribeEvent
   public static void attributes(EntityAttributeCreationEvent var0) {
      var0.put(guide(), GuideNpc.createAttributes().build());
   }

   private static CreativeModeTab creativeTab() {
      if (tab == null) {
         tab = CreativeModeTab.builder(Row.TOP, 0)
            .title(Component.translatable("itemGroup.settledlands.guide"))
            .icon(() -> new ItemStack(spawnEgg()))
            .displayItems((var0, var1) -> {
               var1.accept(spawnEgg());
               var1.accept(tome());
               var1.accept(wand());
               var1.accept(mobilityWand());
               var1.accept(coordStick());
               var1.accept(cutsceneWand());
               var1.accept((ItemLike)PurgatoryManager.REVIVAL_ALTAR_ITEM.get());
               var1.accept(mobBarrierItem());
            })
            .build();
      }
      return tab;
   }
}
