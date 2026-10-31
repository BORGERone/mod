package dev.settledlands;

public final class SanctityFire {
   public static final int FIRE_TICKS = 120;

   public static boolean burns(int level) {
      return level >= 2;
   }

   public static boolean extinguished(int remainingFireTicks) {
      return remainingFireTicks <= 0;
   }

   private SanctityFire() {
   }
}
