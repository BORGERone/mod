package dev.settledlands;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class ActivityClock extends SavedData {
   public long seconds;

   public static ActivityClock get(MinecraftServer server) {
      return (ActivityClock)server.overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory(ActivityClock::new, ActivityClock::load, null), "settledlands_clock");
   }

   private static ActivityClock load(CompoundTag tag, Provider registries) {
      ActivityClock c = new ActivityClock();
      c.seconds = tag.getLong("Seconds");
      return c;
   }

   public void advance() {
      this.seconds++;
      this.setDirty();
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("Version", 1);
      tag.putLong("Seconds", this.seconds);
      return tag;
   }
}
