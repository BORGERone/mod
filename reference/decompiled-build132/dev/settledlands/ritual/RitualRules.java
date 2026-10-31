package dev.settledlands.ritual;

public final class RitualRules {
   public static final int INTRO = 40;
   public static final int CHANNEL = 100;
   public static final int TOTAL = 140;
   public static final int NO_DROP = 200;
   public static final int IMMUNE = 400;

   public static boolean channeling(long age) {
      return age >= 40L && age < 140L;
   }

   public static boolean finished(long age) {
      return age >= 140L;
   }

   public static boolean locked(long now, long until) {
      return now < until;
   }

   private RitualRules() {
   }
}
