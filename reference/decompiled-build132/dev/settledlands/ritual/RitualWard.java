package dev.settledlands.ritual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

public final class RitualWard {
   public static final String DROP = "settledlands_no_drop_until";
   public static final String IMMUNE = "settledlands_ritual_immune_until";

   public static void mark(BannerBlockEntity banner, long now) {
      CompoundTag tag = banner.getPersistentData();
      tag.putLong("settledlands_no_drop_until", Math.max(tag.getLong("settledlands_no_drop_until"), now + 200L));
      tag.putLong("settledlands_ritual_immune_until", Math.max(tag.getLong("settledlands_ritual_immune_until"), now + 400L));
      banner.setChanged();
   }

   public static boolean noDrop(BannerBlockEntity b, long now) {
      return RitualRules.locked(now, b.getPersistentData().getLong("settledlands_no_drop_until"));
   }

   public static boolean immune(BannerBlockEntity b, long now) {
      return RitualRules.locked(now, b.getPersistentData().getLong("settledlands_ritual_immune_until"));
   }

   private RitualWard() {
   }
}
