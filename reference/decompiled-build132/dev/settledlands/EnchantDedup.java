package dev.settledlands;

public final class EnchantDedup {
   public static boolean[] blanks(int[] costs, int[] clues, int[] levels) {
      boolean[] blanks = new boolean[costs.length];

      for (int i = 0; i < costs.length; i++) {
         if (offered(costs, clues, i)) {
            for (int j = 0; j < costs.length; j++) {
               if (j != i
                  && offered(costs, clues, j)
                  && clues[j] == clues[i]
                  && levels[j] == levels[i]
                  && (costs[j] < costs[i] || costs[j] == costs[i] && j < i)) {
                  blanks[i] = true;
                  break;
               }
            }
         }
      }

      return blanks;
   }

   private static boolean offered(int[] costs, int[] clues, int i) {
      return costs[i] > 0 && clues[i] >= 0;
   }

   private EnchantDedup() {
   }
}
