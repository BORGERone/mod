package dev.settledlands.revival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class RevivalAltarBlock extends Block {
   public RevivalAltarBlock(Properties var1) {
      super(var1);
   }

   protected void onPlace(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onPlace(var1, var2, var3, var4, var5);
      if (var2 instanceof ServerLevel && !var4.is(this)) {
         PurgatoryData.get((ServerLevel)var2).addAltar(var3.asLong());
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (var2 instanceof ServerLevel && !var4.is(this)) {
         PurgatoryData.get((ServerLevel)var2).removeAltar(var3.asLong());
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   public void stepOn(Level var1, BlockPos var2, BlockState var3, Entity var4) {
      super.stepOn(var1, var2, var3, var4);
      if (var1 instanceof ServerLevel && var4 instanceof ItemEntity var5 && var5.isAlive() && var5.getItem().is(Items.EMERALD)) {
         PurgatoryManager.tryReviveAtAltar((ServerLevel)var1, var2);
      }
   }

   public void fallOn(Level var1, BlockState var2, BlockPos var3, Entity var4, float var5) {
      super.fallOn(var1, var2, var3, var4, var5);
      if (var1 instanceof ServerLevel && var4 instanceof ItemEntity var6 && var6.isAlive() && var6.getItem().is(Items.EMERALD)) {
         PurgatoryManager.tryReviveAtAltar((ServerLevel)var1, var3);
      }
   }
}
