package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcContent;
import dev.settledlands.npc.NpcMobilityGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;

final class MobilityWandClient {
   private static int ticks;

   private MobilityWandClient() {
   }

   static void tick() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.player == null) {
         return;
      }
      if (++ticks % 8 != 0) {
         return;
      }
      ItemStack wand = minecraft.player.getMainHandItem();
      if (wand.getItem() != NpcContent.mobilityWand()) {
         wand = minecraft.player.getOffhandItem();
      }
      if (wand.getItem() != NpcContent.mobilityWand()) {
         return;
      }
      CustomData customData = wand.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return;
      }
      CompoundTag root = customData.copyTag();
      CompoundTag wandData = root.getCompound(NpcMobilityGroups.WAND_DATA_KEY);
      ListTag points = wandData.getList("Points", 10);
      for (int i = 0; i < points.size(); i++) {
         BlockPos point = parse(points.getCompound(i).getString("Coordinates"));
         if (point == null || minecraft.player.distanceToSqr(point.getX() + 0.5, point.getY() + 0.4, point.getZ() + 0.5) > 4096.0) {
            continue;
         }
         minecraft.level.addParticle(ParticleTypes.HAPPY_VILLAGER,
            point.getX() + 0.5, point.getY() + 0.35, point.getZ() + 0.5, 0.0, 0.025, 0.0);
      }
   }

   private static BlockPos parse(String value) {
      if (value == null || value.isBlank()) {
         return null;
      }
      String[] parts = value.trim().split("[,\\s]+", -1);
      if (parts.length != 3) {
         return null;
      }
      try {
         int x = Integer.parseInt(parts[0]);
         int y = Integer.parseInt(parts[1]);
         int z = Integer.parseInt(parts[2]);
         return new BlockPos(x, y, z);
      } catch (NumberFormatException ignored) {
         return null;
      }
   }
}
