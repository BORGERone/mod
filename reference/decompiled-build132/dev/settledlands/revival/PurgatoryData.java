package dev.settledlands.revival;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class PurgatoryData extends SavedData {
   private static final String DATA_NAME = "settledlands_purgatory";
   private final List<UUID> deadQueue = new ArrayList<>();
   private final Map<UUID, PurgatoryData.TargetPos> pendingRevives = new LinkedHashMap<>();
   private final Set<UUID> visitedPurgatory = new HashSet<>();
   private final LongOpenHashSet altars = new LongOpenHashSet();
   public static final Factory<PurgatoryData> FACTORY = new Factory(PurgatoryData::new, PurgatoryData::load, null);

   public static PurgatoryData get(ServerLevel var0) {
      return (PurgatoryData)var0.getDataStorage().computeIfAbsent(FACTORY, "settledlands_purgatory");
   }

   public static PurgatoryData getMaster(MinecraftServer var0) {
      return get(var0.overworld());
   }

   public void addAltar(long var1) {
      if (this.altars.add(var1)) {
         this.setDirty();
      }
   }

   public void removeAltar(long var1) {
      if (this.altars.remove(var1)) {
         this.setDirty();
      }
   }

   public LongOpenHashSet altars() {
      return this.altars;
   }

   public boolean consumeFirstPurgatorySpawn(UUID var1) {
      if (this.visitedPurgatory.add(var1)) {
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public void enqueueDead(UUID var1) {
      this.pendingRevives.remove(var1);
      if (!this.deadQueue.contains(var1)) {
         this.deadQueue.add(var1);
      }

      this.setDirty();
   }

   public boolean isDeadInQueue(UUID var1) {
      return this.deadQueue.contains(var1);
   }

   public boolean removeFromQueue(UUID var1) {
      boolean var2 = this.deadQueue.remove(var1);
      if (var2) {
         this.setDirty();
      }

      return var2;
   }

   public boolean hasDeadPlayers() {
      return !this.deadQueue.isEmpty();
   }

   public UUID pollFirstDead() {
      if (this.deadQueue.isEmpty()) {
         return null;
      } else {
         UUID var1 = this.deadQueue.remove(0);
         this.setDirty();
         return var1;
      }
   }

   public void setPendingRevive(UUID var1, String var2, BlockPos var3) {
      this.pendingRevives.put(var1, new PurgatoryData.TargetPos(var2, var3.asLong()));
      this.setDirty();
   }

   public PurgatoryData.TargetPos consumePendingRevive(UUID var1) {
      PurgatoryData.TargetPos var2 = this.pendingRevives.remove(var1);
      if (var2 != null) {
         this.setDirty();
      }

      return var2;
   }

   public boolean hasPendingRevive(UUID var1) {
      return this.pendingRevives.containsKey(var1);
   }

   public static PurgatoryData load(CompoundTag var0, Provider var1) {
      PurgatoryData var2 = new PurgatoryData();

      for (long var6 : var0.getLongArray("Altars")) {
         var2.altars.add(var6);
      }

      for (Tag var13 : var0.getList("DeadQueue", 10)) {
         if (var13 instanceof CompoundTag var16 && var16.hasUUID("Id")) {
            UUID var19 = var16.getUUID("Id");
            if (!var2.deadQueue.contains(var19)) {
               var2.deadQueue.add(var19);
            }
         }
      }

      for (Tag var14 : var0.getList("PendingRevives", 10)) {
         if (var14 instanceof CompoundTag var17 && var17.hasUUID("Id")) {
            UUID var20 = var17.getUUID("Id");
            String var7 = var17.getString("Dim");
            long var8 = var17.getLong("Pos");
            var2.pendingRevives.put(var20, new PurgatoryData.TargetPos(var7, var8));
         }
      }

      for (Tag var15 : var0.getList("VisitedPurgatory", 10)) {
         if (var15 instanceof CompoundTag var18 && var18.hasUUID("Id")) {
            var2.visitedPurgatory.add(var18.getUUID("Id"));
         }
      }

      return var2;
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      var1.putLongArray("Altars", this.altars.toLongArray());
      ListTag var3 = new ListTag();
      int var4 = 0;

      for (int var5 = this.deadQueue.size(); var4 < var5; var4++) {
         CompoundTag var6 = new CompoundTag();
         var6.putUUID("Id", this.deadQueue.get(var4));
         var3.add(var6);
      }

      var1.put("DeadQueue", var3);
      ListTag var9 = new ListTag();

      for (Entry var12 : this.pendingRevives.entrySet()) {
         CompoundTag var7 = new CompoundTag();
         var7.putUUID("Id", (UUID)var12.getKey());
         var7.putString("Dim", ((PurgatoryData.TargetPos)var12.getValue()).dimension);
         var7.putLong("Pos", ((PurgatoryData.TargetPos)var12.getValue()).posLong);
         var9.add(var7);
      }

      var1.put("PendingRevives", var9);
      ListTag var11 = new ListTag();

      for (UUID var14 : this.visitedPurgatory) {
         CompoundTag var8 = new CompoundTag();
         var8.putUUID("Id", var14);
         var11.add(var8);
      }

      var1.put("VisitedPurgatory", var11);
      return var1;
   }

   public static final class TargetPos {
      public final String dimension;
      public final long posLong;

      public TargetPos(String var1, long var2) {
         this.dimension = var1;
         this.posLong = var2;
      }
   }
}
