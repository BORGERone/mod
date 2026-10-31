package dev.settledlands;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Household {
   public static int category(BlockState state) {
      Block b = state.getBlock();
      if (b instanceof BedBlock) {
         return 1;
      } else if (b instanceof ChestBlock || b instanceof BarrelBlock) {
         return 2;
      } else if (b instanceof CraftingTableBlock || b instanceof AbstractFurnaceBlock) {
         return 3;
      } else {
         return b instanceof CampfireBlock ? 4 : 0;
      }
   }

   private Household() {
   }
}
