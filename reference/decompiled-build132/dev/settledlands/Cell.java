package dev.settledlands;

import net.minecraft.core.BlockPos;

public final class Cell {
   public static long at(BlockPos p) {
      return key(Math.floorDiv(p.getX(), 16), Math.floorDiv(p.getY(), 8), Math.floorDiv(p.getZ(), 16));
   }

   public static long key(int x, int y, int z) {
      return BlockPos.asLong(x, y, z);
   }

   public static long offset(long k, int dx, int dz) {
      return key(BlockPos.getX(k) + dx, BlockPos.getY(k), BlockPos.getZ(k) + dz);
   }

   public static BlockPos min(long k) {
      return new BlockPos(BlockPos.getX(k) * 16, BlockPos.getY(k) * 8, BlockPos.getZ(k) * 16);
   }

   private Cell() {
   }
}
