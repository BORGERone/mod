package dev.settledlands.npc;

import java.util.Objects;

public record GuidePhrase(int number, String text) implements Comparable<GuidePhrase> {
   public static final int MIN_NUMBER = 1;
   public static final int MAX_NUMBER = 999999;
   public static final int MAX_TEXT = 480;

   public GuidePhrase(int number, String text) {
      text = text == null ? "" : text.trim();
      this.number = number;
      this.text = text;
   }

   public boolean sameAs(GuidePhrase var1) {
      return var1 != null && var1.number == this.number && var1.text.equals(this.text);
   }

   public int compareTo(GuidePhrase var1) {
      int var2 = Integer.compare(this.number, var1.number);
      return var2 != 0 ? var2 : this.text.compareTo(var1.text);
   }

   @Override
   public boolean equals(Object var1) {
      return var1 instanceof GuidePhrase var2 && var2.number == this.number && var2.text.equals(this.text);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.number, this.text);
   }
}
