package dev.settledlands;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class TerritoryData extends SavedData {
   public static final int CURRENT_VERSION = 1;
   private static final String DATA_NAME = "settledlands_territories";
   private static final boolean STRICT = Boolean.getBoolean("settledlands.strictData");
   public static volatile boolean LAST_LOAD_FAILED;
   public static final Factory<TerritoryData> FACTORY = new Factory(TerritoryData::new, TerritoryData::load, null);
   public final Long2ObjectOpenHashMap<TerritoryData.Zone> zones = new Long2ObjectOpenHashMap();
   public final LongOpenHashSet permanent = new LongOpenHashSet();
   private final Long2ByteOpenHashMap blocks = new Long2ByteOpenHashMap();
   private final Long2ObjectOpenHashMap<int[]> categories = new Long2ObjectOpenHashMap();
   private final Long2LongOpenHashMap occupied = new Long2LongOpenHashMap();
   private final LongLinkedOpenHashSet validation = new LongLinkedOpenHashSet();
   private long lastWarning = Long.MIN_VALUE;
   public long deniedSpawns;
   private boolean readFailed;

   public static TerritoryData get(ServerLevel var0) {
      return (TerritoryData)var0.getDataStorage().computeIfAbsent(FACTORY, "settledlands_territories");
   }

   public boolean isDirty() {
      return !this.readFailed && super.isDirty();
   }

   public int blockCount() {
      return this.blocks.size();
   }

   private void warn(long var1) {
      if (this.lastWarning == Long.MIN_VALUE || var1 - this.lastWarning >= 300L) {
         LogUtils.getLogger().warn("Settled Lands storage limit reached: new records skipped; existing progress retained.");
         this.lastWarning = var1;
      }
   }

   public boolean room(long var1) {
      if (this.zones.size() + this.permanent.size() < (Integer)Settings.MAX_CELLS.get()) {
         return true;
      } else {
         this.warn(var1);
         return false;
      }
   }

   public double score(long var1, long var3) {
      if (this.permanent.contains(var1) && (Boolean)Settings.PERMANENT.get()) {
         return ((Integer)Settings.TEMP_MAX.get()).intValue();
      } else {
         TerritoryData.Zone var5 = (TerritoryData.Zone)this.zones.get(var1);
         return var5 == null ? 0.0 : Math.min((double)((Integer)Settings.TEMP_MAX.get()).intValue(), var5.current(var3));
      }
   }

   public int kills(long var1) {
      if (this.permanent.contains(var1)) {
         return (Integer)Settings.PERM_MAX.get();
      } else {
         TerritoryData.Zone var3 = (TerritoryData.Zone)this.zones.get(var1);
         return var3 == null ? 0 : var3.kills;
      }
   }

   public void credit(long var1, long var3) {
      if (!this.permanent.contains(var1)) {
         TerritoryData.Zone var5 = (TerritoryData.Zone)this.zones.get(var1);
         if (var5 == null) {
            if (!this.room(var3)) {
               return;
            }

            var5 = new TerritoryData.Zone();
            this.zones.put(var1, var5);
         }

         var5.score = Progress.afterKill(
            var5.score,
            var5.updated,
            var3,
            ((Integer)Settings.GRACE.get()).intValue(),
            ((Integer)Settings.DECAY.get()).intValue(),
            (Integer)Settings.TEMP_MAX.get()
         );
         var5.updated = var3;
         var5.kills = Math.min((Integer)Settings.PERM_MAX.get(), var5.kills + 1);
         this.promote(var1);
         this.setDirty();
      }
   }

   private void promote(long var1) {
      TerritoryData.Zone var3 = (TerritoryData.Zone)this.zones.get(var1);
      if ((Boolean)Settings.PERMANENT.get()
         && var3 != null
         && var3.kills >= (Integer)Settings.PERM_MAX.get()
         && this.habitation(var1) >= ((Integer)Settings.HABITATION.get()).intValue()) {
         this.permanent.add(var1);
         this.zones.remove(var1);
         this.setDirty();
      }
   }

   public void reset(long var1) {
      this.zones.remove(var1);
      this.permanent.remove(var1);
      this.setDirty();
   }

   public void force(long var1, double var3, int var5, boolean var6, long var7) {
      if (this.zones.containsKey(var1) || this.permanent.contains(var1) || this.room(var7)) {
         this.reset(var1);
         if (var6) {
            this.permanent.add(var1);
         } else {
            TerritoryData.Zone var9 = new TerritoryData.Zone();
            var9.score = var3;
            var9.updated = var7;
            var9.kills = var5;
            this.zones.put(var1, var9);
            this.promote(var1);
         }

         this.setDirty();
      }
   }

   private void index(long var1, int var3, int var4) {
      long var5 = Cell.at(BlockPos.of(var1));
      int[] var7 = (int[])this.categories.computeIfAbsent(var5, var0 -> new int[4]);
      var7[var3 - 1] = var7[var3 - 1] + var4;
      if (var7[0] + var7[1] + var7[2] + var7[3] == 0) {
         this.categories.remove(var5);
         this.occupied.remove(var5);
      }
   }

   public void register(BlockPos var1, int var2, long var3) {
      if (var2 != 0) {
         long var5 = var1.asLong();
         if (!this.blocks.containsKey(var5) && this.blocks.size() >= (Integer)Settings.MAX_BLOCKS.get()) {
            this.warn(var3);
         } else {
            byte var7 = this.blocks.get(var5);
            if (var7 != var2) {
               if (var7 != 0) {
                  this.index(var5, var7, -1);
               }

               this.blocks.put(var5, (byte)var2);
               this.index(var5, var2, 1);
               this.validation.add(var5);
               this.setDirty();
            }
         }
      }
   }

   public void unregister(BlockPos var1) {
      long var2 = var1.asLong();
      byte var4 = this.blocks.remove(var2);
      if (var4 != 0) {
         this.index(var2, var4, -1);
         this.validation.remove(var2);
         this.setDirty();
      }
   }

   public void validate(ServerLevel var1) {
      int var2 = Math.min(16, this.validation.size());

      for (int var3 = 0; var3 < var2; var3++) {
         long var4 = this.validation.removeFirstLong();
         BlockPos var6 = BlockPos.of(var4);
         if (var1.hasChunkAt(var6) && Household.category(var1.getBlockState(var6)) != this.blocks.get(var4)) {
            this.unregister(var6);
         } else {
            this.validation.add(var4);
         }
      }
   }

   public boolean household(long var1) {
      if (!this.categories.containsKey(var1)) {
         return false;
      } else {
         int var3 = 0;

         for (int var4 = -1; var4 <= 1; var4++) {
            for (int var5 = -1; var5 <= 1; var5++) {
               int[] var6 = (int[])this.categories.get(Cell.offset(var1, var4, var5));
               if (var6 != null) {
                  for (int var7 = 0; var7 < 4; var7++) {
                     if (var6[var7] > 0) {
                        var3 |= 1 << var7;
                     }
                  }
               }
            }
         }

         return Integer.bitCount(var3) >= (Integer)Settings.CATEGORIES.get();
      }
   }

   public boolean within(long var1, BlockPos var3, double var4) {
      if (BlockPos.getY(var1) != Math.floorDiv(var3.getY(), 8)) {
         return false;
      } else {
         BlockPos var6 = Cell.min(var1);
         double var7 = var3.getX() + 0.5 - (var6.getX() + 8);
         double var9 = var3.getZ() + 0.5 - (var6.getZ() + 8);
         return var7 * var7 + var9 * var9 <= var4 * var4;
      }
   }

   public Long nearestHousehold(BlockPos var1) {
      long var2 = Cell.at(var1);
      Long var4 = null;
      double var5 = Double.MAX_VALUE;

      for (int var7 = -2; var7 <= 2; var7++) {
         for (int var8 = -2; var8 <= 2; var8++) {
            long var9 = Cell.offset(var2, var7, var8);
            if (this.within(var9, var1, 24.0) && this.household(var9)) {
               BlockPos var11 = Cell.min(var9);
               double var12 = var1.distToCenterSqr(var11.getX() + 8, var11.getY() + 4, var11.getZ() + 8);
               if (var12 < var5) {
                  var4 = var9;
                  var5 = var12;
               }
            }
         }
      }

      return var4;
   }

   public long habitation(long var1) {
      long var3 = 0L;

      for (int var5 = -1; var5 <= 1; var5++) {
         for (int var6 = -1; var6 <= 1; var6++) {
            long var7 = Cell.offset(var1, var5, var6);
            long var9 = this.occupied.get(var7);
            if (var9 > var3 && this.household(var7)) {
               var3 = var9;
            }
         }
      }

      return var3;
   }

   public void occupy(long var1) {
      long var3 = this.occupied.get(var1);
      long var5 = ((Integer)Settings.HABITATION.get()).intValue();
      if (var3 < var5) {
         this.occupied.put(var1, var3 + 1L);
         this.setDirty();
      }

      if (var3 + 1L >= var5) {
         for (int var7 = -1; var7 <= 1; var7++) {
            for (int var8 = -1; var8 <= 1; var8++) {
               this.promote(Cell.offset(var1, var7, var8));
            }
         }
      }
   }

   public void prune(long var1, int var3) {
      int var4 = 0;
      ObjectIterator var5 = this.zones.long2ObjectEntrySet().iterator();

      while (var5.hasNext()) {
         TerritoryData.Zone var6 = (TerritoryData.Zone)((Entry)var5.next()).getValue();
         if (var6.kills == 0 && var6.current(var1) <= 0.0) {
            var5.remove();
            if (++var4 >= var3) {
               break;
            }
         }
      }

      if (var4 > 0) {
         this.setDirty();
      }
   }

   private static TerritoryData load(CompoundTag var0, Provider var1) {
      int var2 = var0.getInt("Version");
      if (var2 == 1) {
         try {
            return read(var0);
         } catch (RuntimeException var4) {
            LogUtils.getLogger()
               .error(
                  "[settledlands] Не удалось прочитать данные территорий, мир стартует с пустыми данными. Файл world/data/settledlands_territories.dat не будет перезаписан.",
                  var4
               );
            return failed();
         }
      } else if (STRICT) {
         throw new IllegalStateException("Settled Lands: unsupported data version " + var2 + " (expected 1)");
      } else {
         if (var2 > 1) {
            LogUtils.getLogger()
               .error(
                  "[settledlands] settledlands_territories.dat создан более новой версией мода (v{} > v{}). Файл оставлен без изменений, изменения территорий не будут сохраняться. Верните прежнюю версию мода или сделайте копию папки world/data.",
                  var2,
                  1
               );
         } else {
            LogUtils.getLogger()
               .warn(
                  "[settledlands] settledlands_territories.dat имеет неизвестную версию v{} — данные территорий не прочитаны, файл оставлен для восстановления вручную.",
                  var2
               );
         }

         return failed();
      }
   }

   private static TerritoryData failed() {
      TerritoryData var0 = new TerritoryData();
      var0.readFailed = true;
      LAST_LOAD_FAILED = true;
      return var0;
   }

   private static TerritoryData read(CompoundTag var0) {
      TerritoryData var1 = new TerritoryData();

      for (Tag var3 : var0.getList("Zones", 10)) {
         CompoundTag var4 = (CompoundTag)var3;
         TerritoryData.Zone var5 = new TerritoryData.Zone();
         var5.score = var4.getDouble("Score");
         var5.updated = var4.getLong("Updated");
         var5.kills = var4.getInt("Kills");
         var1.zones.put(var4.getLong("Key"), var5);
      }

      for (long var17 : var0.getLongArray("Permanent")) {
         var1.permanent.add(var17);
         var1.zones.remove(var17);
      }

      for (Tag var12 : var0.getList("Blocks", 10)) {
         CompoundTag var15 = (CompoundTag)var12;
         long var18 = var15.getLong("Pos");
         byte var7 = var15.getByte("Category");
         if (var7 >= 1 && var7 <= 4) {
            var1.blocks.put(var18, var7);
            var1.index(var18, var7, 1);
            var1.validation.add(var18);
         }
      }

      for (Tag var13 : var0.getList("Occupied", 10)) {
         CompoundTag var16 = (CompoundTag)var13;
         long var19 = var16.getLong("Key");
         if (var1.categories.containsKey(var19)) {
            var1.occupied.put(var19, var16.getLong("Seconds"));
         }
      }

      return var1;
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      var1.putInt("Version", 1);
      ListTag var3 = new ListTag();
      ObjectIterator var4 = this.zones.long2ObjectEntrySet().iterator();

      while (var4.hasNext()) {
         Entry var5 = (Entry)var4.next();
         TerritoryData.Zone var6 = (TerritoryData.Zone)var5.getValue();
         CompoundTag var7 = new CompoundTag();
         var7.putLong("Key", var5.getLongKey());
         var7.putDouble("Score", var6.score);
         var7.putLong("Updated", var6.updated);
         var7.putInt("Kills", var6.kills);
         var3.add(var7);
      }

      var1.put("Zones", var3);
      var1.putLongArray("Permanent", this.permanent.toLongArray());
      var3 = new ListTag();
      var4 = this.blocks.long2ByteEntrySet().iterator();

      while (var4.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry var12 = (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry)var4.next();
         CompoundTag var14 = new CompoundTag();
         var14.putLong("Pos", var12.getLongKey());
         var14.putByte("Category", var12.getByteValue());
         var3.add(var14);
      }

      var1.put("Blocks", var3);
      var3 = new ListTag();
      var4 = this.occupied.long2LongEntrySet().iterator();

      while (var4.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2LongMap.Entry var13 = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)var4.next();
         CompoundTag var15 = new CompoundTag();
         var15.putLong("Key", var13.getLongKey());
         var15.putLong("Seconds", var13.getLongValue());
         var3.add(var15);
      }

      var1.put("Occupied", var3);
      return var1;
   }

   public static final class Zone {
      public double score;
      public long updated;
      public int kills;

      public double current(long var1) {
         return Progress.current(this.score, this.updated, var1, ((Integer)Settings.GRACE.get()).intValue(), ((Integer)Settings.DECAY.get()).intValue());
      }
   }
}
