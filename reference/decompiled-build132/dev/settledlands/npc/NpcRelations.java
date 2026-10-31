package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class NpcRelations extends SavedData {
   public static final String DATA_NAME = "settledlands_npc_relations";
   public static final Factory<NpcRelations> FACTORY = new Factory(NpcRelations::new, NpcRelations::load, null);
   private final Map<UUID, Map<UUID, NpcRelations.Rel>> byNpc = new HashMap<>();
   private final Map<UUID, Map<UUID, Set<Integer>>> questsDone = new HashMap<>();
   private final Map<UUID, Map<UUID, Set<Integer>>> questsTaken = new HashMap<>();
   private final Map<UUID, Set<Integer>> chainDone = new HashMap<>();
   private final Map<UUID, Set<String>> takenIds = new HashMap<>();
   private final Map<UUID, Set<String>> doneIds = new HashMap<>();
   private final Set<String> chainIds = new TreeSet<>();

   public static NpcRelations get(MinecraftServer var0) {
      return (NpcRelations)var0.overworld().getDataStorage().computeIfAbsent(FACTORY, "settledlands_npc_relations");
   }

   public void migrateNpc(UUID var1, UUID var2) {
      if (var1 != null && var2 != null && !var1.equals(var2)) {
         boolean var3 = false;
         Map var4 = this.byNpc.get(var1);
         if (var4 != null && !var4.isEmpty()) {
            Map var5 = this.byNpc.computeIfAbsent(var2, var0 -> new HashMap<>());

            for (Entry var7 : var4.entrySet()) {
               var5.putIfAbsent(var7.getKey(), var7.getValue());
            }

            this.byNpc.remove(var1);
            var3 = true;
         }

         var3 |= mergeQuestSets(this.questsDone, var1, var2);
         var3 |= mergeQuestSets(this.questsTaken, var1, var2);
         Set var10 = this.chainDone.get(var1);
         if (var10 != null && !var10.isEmpty()) {
            Set var11 = this.chainDone.computeIfAbsent(var2, var0 -> new TreeSet<>());
            var11.addAll(var10);
            this.chainDone.remove(var1);
            var3 = true;
         }

         if (var3) {
            this.setDirty();
         }
      }
   }

   private static boolean mergeQuestSets(Map<UUID, Map<UUID, Set<Integer>>> var0, UUID var1, UUID var2) {
      Map var3 = (Map)var0.get(var1);
      if (var3 != null && !var3.isEmpty()) {
         Map var4 = var0.computeIfAbsent(var2, var0x -> new HashMap());

         for (Entry var6 : var3.entrySet()) {
            Set var7 = var4.computeIfAbsent(var6.getKey(), var0x -> new TreeSet());
            var7.addAll((Set)var6.getValue());
         }

         var0.remove(var1);
         return true;
      } else {
         return false;
      }
   }

   public boolean questTakenId(UUID var1, String var2) {
      String var3 = normId(var2);
      if (var3 == null) {
         return false;
      } else {
         Set var4 = this.takenIds.get(var1);
         return var4 != null && var4.contains(var3);
      }
   }

   public void markQuestTakenId(UUID var1, String var2) {
      String var3 = normId(var2);
      if (var3 != null) {
         this.takenIds.computeIfAbsent(var1, var0 -> new TreeSet<>()).add(var3);
         this.setDirty();
      }
   }

   public void clearQuestTakenId(UUID var1, String var2) {
      String var3 = normId(var2);
      Set var4 = this.takenIds.get(var1);
      if (var3 != null && var4 != null && var4.remove(var3)) {
         this.setDirty();
      }
   }

   public boolean questDoneId(UUID var1, String var2) {
      String var3 = normId(var2);
      if (var3 == null) {
         return false;
      } else {
         Set var4 = this.doneIds.get(var1);
         return var4 != null && var4.contains(var3);
      }
   }

   public void markQuestDoneId(UUID var1, String var2) {
      String var3 = normId(var2);
      if (var3 != null) {
         this.doneIds.computeIfAbsent(var1, var0 -> new TreeSet<>()).add(var3);
         this.setDirty();
      }
   }

   public boolean chainDoneId(String var1) {
      String var2 = normId(var1);
      return var2 != null && this.chainIds.contains(var2);
   }

   public void markChainDoneId(String var1) {
      String var2 = normId(var1);
      if (var2 != null) {
         this.chainIds.add(var2);
         this.setDirty();
      }
   }

   public void migrateQuestSlotsToIds(UUID var1, List<String> var2) {
      if (var1 != null && var2 != null && !var2.isEmpty()) {
         boolean var3 = false;
         var3 |= mergeSlotSet(this.questsTaken, var1, var2, this.takenIds);
         var3 |= mergeSlotSet(this.questsDone, var1, var2, this.doneIds);
         Set var4 = this.chainDone.get(var1);
         if (var4 != null && !var4.isEmpty()) {
            for (int var6 : var4) {
               if (var6 >= 0 && var6 < var2.size()) {
                  addId(this.chainIds, (String)var2.get(var6));
               }
            }

            this.chainDone.remove(var1);
            var3 = true;
         }

         if (var3) {
            this.setDirty();
         }
      }
   }

   private static boolean mergeSlotSet(Map<UUID, Map<UUID, Set<Integer>>> var0, UUID var1, List<String> var2, Map<UUID, Set<String>> var3) {
      Map var4 = (Map)var0.get(var1);
      if (var4 != null && !var4.isEmpty()) {
         for (Entry var6 : var4.entrySet()) {
            Set var7 = var3.computeIfAbsent((UUID)var6.getKey(), var0x -> new TreeSet());

            for (int var9 : (Set)var6.getValue()) {
               if (var9 >= 0 && var9 < var2.size()) {
                  addId(var7, (String)var2.get(var9));
               }
            }
         }

         var0.remove(var1);
         return true;
      } else {
         return false;
      }
   }

   private static void addId(Set<String> var0, String var1) {
      if (var1 != null && !var1.isEmpty()) {
         var0.add(var1);
      }
   }

   private static String normId(String var0) {
      if (var0 == null) {
         return null;
      } else {
         String var1 = var0.trim();
         return var1.isEmpty() ? null : (var1.length() > 128 ? var1.substring(0, 128) : var1);
      }
   }

   public int get(UUID var1, UUID var2, int var3) {
      Map var4 = this.byNpc.get(var1);
      if (var4 == null) {
         return var3;
      } else {
         NpcRelations.Rel var5 = (NpcRelations.Rel)var4.get(var2);
         return var5 == null ? var3 : var5.value();
      }
   }

   public Integer raw(UUID var1, UUID var2) {
      Map var3 = this.byNpc.get(var1);
      if (var3 == null) {
         return null;
      } else {
         NpcRelations.Rel var4 = (NpcRelations.Rel)var3.get(var2);
         return var4 == null ? null : var4.value();
      }
   }

   public void set(UUID var1, UUID var2, String var3, int var4) {
      Map var5 = this.byNpc.computeIfAbsent(var1, var0 -> new HashMap<>());
      String var6 = NpcProfile.clean(var3, 32);
      if (var6.isEmpty()) {
         NpcRelations.Rel var7 = (NpcRelations.Rel)var5.get(var2);
         var6 = var7 == null ? "" : var7.name();
      }

      var5.put(var2, new NpcRelations.Rel(var2, var6, NpcProfile.clamp(var4, 0, 100)));
      this.setDirty();
   }

   public void clear(UUID var1, UUID var2) {
      Map var3 = this.byNpc.get(var1);
      if (var3 != null && var3.remove(var2) != null) {
         this.setDirty();
      }
   }

   public boolean chainDone(UUID var1, int var2) {
      Set var3 = this.chainDone.get(var1);
      return var3 != null && var2 >= 0 && var3.contains(var2);
   }

   public void markChainDone(UUID var1, int var2) {
      if (var2 >= 0) {
         this.chainDone.computeIfAbsent(var1, var0 -> new TreeSet<>()).add(var2);
         this.setDirty();
      }
   }

   public void clearChainDone(UUID var1, int var2) {
      Set var3 = this.chainDone.get(var1);
      if (var3 != null && var3.remove(var2)) {
         this.setDirty();
      }
   }

   public boolean questDone(UUID var1, UUID var2, int var3) {
      Map var4 = this.questsDone.get(var1);
      if (var4 != null && var3 >= 0) {
         Set var5 = (Set)var4.get(var2);
         return var5 != null && var5.contains(var3);
      } else {
         return false;
      }
   }

   public void markQuestDone(UUID var1, UUID var2, int var3) {
      if (var3 >= 0) {
         this.questsDone.computeIfAbsent(var1, var0 -> new HashMap<>()).computeIfAbsent(var2, var0 -> new TreeSet<>()).add(var3);
         this.setDirty();
      }
   }

   public boolean questTaken(UUID var1, UUID var2, int var3) {
      Map var4 = this.questsTaken.get(var1);
      if (var4 != null && var3 >= 0) {
         Set var5 = (Set)var4.get(var2);
         return var5 != null && var5.contains(var3);
      } else {
         return false;
      }
   }

   public void markQuestTaken(UUID var1, UUID var2, int var3) {
      if (var3 >= 0) {
         this.questsTaken.computeIfAbsent(var1, var0 -> new HashMap<>()).computeIfAbsent(var2, var0 -> new TreeSet<>()).add(var3);
         this.setDirty();
      }
   }

   public void clearQuestTaken(UUID var1, UUID var2, int var3) {
      Map var4 = this.questsTaken.get(var1);
      Set var5 = var4 == null ? null : (Set)var4.get(var2);
      if (var5 != null && var5.remove(var3)) {
         this.setDirty();
      }
   }

   public int questsDoneCount(UUID var1, UUID var2) {
      Map var3 = this.questsDone.get(var1);
      Set var4 = var3 == null ? null : (Set)var3.get(var2);
      return var4 == null ? 0 : var4.size();
   }

   public List<NpcRelations.Rel> list(UUID var1) {
      Map var2 = this.byNpc.get(var1);
      if (var2 != null && !var2.isEmpty()) {
         ArrayList var3 = new ArrayList(var2.values());
         var3.sort(Comparator.comparing(NpcRelations.Rel::name, String.CASE_INSENSITIVE_ORDER));
         return var3;
      } else {
         return List.of();
      }
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      CompoundTag var3 = new CompoundTag();

      for (Entry var5 : this.byNpc.entrySet()) {
         ListTag var6 = new ListTag();

         for (NpcRelations.Rel var8 : ((Map)var5.getValue()).values()) {
            CompoundTag var9 = new CompoundTag();
            var9.putString("Id", var8.player().toString());
            var9.putString("Name", var8.name() == null ? "" : var8.name());
            var9.putInt("V", var8.value());
            var6.add(var9);
         }

         var3.put(((UUID)var5.getKey()).toString(), var6);
      }

      var1.put("Npcs", var3);
      var1.put("Quests", saveQuestSets(this.questsDone));
      var1.put("Taken", saveQuestSets(this.questsTaken));
      var1.put("ChainDone", saveChainDone(this.chainDone));
      var1.put("TakenIds", saveIdSets(this.takenIds));
      var1.put("DoneIds", saveIdSets(this.doneIds));
      ListTag var10 = new ListTag();

      for (String var12 : this.chainIds) {
         var10.add(StringTag.valueOf(var12));
      }

      var1.put("ChainIds", var10);
      return var1;
   }

   private static CompoundTag saveIdSets(Map<UUID, Set<String>> var0) {
      CompoundTag var1 = new CompoundTag();

      for (Entry var3 : var0.entrySet()) {
         if (!((Set)var3.getValue()).isEmpty()) {
            ListTag var4 = new ListTag();

            for (String var6 : (Set)var3.getValue()) {
               var4.add(StringTag.valueOf(var6));
            }

            var1.put(((UUID)var3.getKey()).toString(), var4);
         }
      }

      return var1;
   }

   private static Map<UUID, Set<String>> loadIdSets(CompoundTag var0) {
      HashMap var1 = new HashMap();

      for (String var3 : var0.getAllKeys()) {
         UUID var4;
         try {
            var4 = UUID.fromString(var3);
         } catch (IllegalArgumentException var9) {
            continue;
         }

         ListTag var5 = var0.getList(var3, 8);
         TreeSet var6 = new TreeSet();

         for (int var7 = 0; var7 < var5.size(); var7++) {
            String var8 = var5.getString(var7);
            if (!var8.isEmpty()) {
               var6.add(var8);
            }
         }

         if (!var6.isEmpty()) {
            var1.put(var4, var6);
         }
      }

      return var1;
   }

   private static CompoundTag saveChainDone(Map<UUID, Set<Integer>> var0) {
      CompoundTag var1 = new CompoundTag();

      for (Entry var3 : var0.entrySet()) {
         if (!((Set)var3.getValue()).isEmpty()) {
            var1.putIntArray(((UUID)var3.getKey()).toString(), ((Set)var3.getValue()).stream().mapToInt(Integer::intValue).toArray());
         }
      }

      return var1;
   }

   private static Map<UUID, Set<Integer>> loadChainDone(CompoundTag var0) {
      HashMap var1 = new HashMap();

      for (String var3 : var0.getAllKeys()) {
         UUID var4;
         try {
            var4 = UUID.fromString(var3);
         } catch (IllegalArgumentException var10) {
            continue;
         }

         TreeSet var5 = new TreeSet();

         for (int var9 : var0.getIntArray(var3)) {
            if (var9 >= 0 && var9 < 6) {
               var5.add(var9);
            }
         }

         if (!var5.isEmpty()) {
            var1.put(var4, var5);
         }
      }

      return var1;
   }

   private static CompoundTag saveQuestSets(Map<UUID, Map<UUID, Set<Integer>>> var0) {
      CompoundTag var1 = new CompoundTag();

      for (Entry var3 : var0.entrySet()) {
         ListTag var4 = new ListTag();

         for (Entry var6 : ((Map)var3.getValue()).entrySet()) {
            if (!((Set)var6.getValue()).isEmpty()) {
               CompoundTag var7 = new CompoundTag();
               var7.putString("Id", ((UUID)var6.getKey()).toString());
               var7.putIntArray("Done", ((Set)var6.getValue()).stream().mapToInt(Integer::intValue).toArray());
               var4.add(var7);
            }
         }

         if (!var4.isEmpty()) {
            var1.put(((UUID)var3.getKey()).toString(), var4);
         }
      }

      return var1;
   }

   private static Map<UUID, Map<UUID, Set<Integer>>> loadQuestSets(CompoundTag var0) {
      HashMap var1 = new HashMap();

      for (String var3 : var0.getAllKeys()) {
         UUID var4;
         try {
            var4 = UUID.fromString(var3);
         } catch (IllegalArgumentException var15) {
            continue;
         }

         ListTag var5 = var0.getList(var3, 10);
         HashMap var6 = new HashMap();

         for (int var7 = 0; var7 < var5.size(); var7++) {
            CompoundTag var8 = var5.getCompound(var7);

            try {
               UUID var9 = UUID.fromString(var8.getString("Id"));
               TreeSet var10 = new TreeSet();

               for (int var14 : var8.getIntArray("Done")) {
                  if (var14 >= 0 && var14 < 6) {
                     var10.add(var14);
                  }
               }

               if (!var10.isEmpty()) {
                  var6.put(var9, var10);
               }
            } catch (IllegalArgumentException var16) {
            }
         }

         if (!var6.isEmpty()) {
            var1.put(var4, var6);
         }
      }

      return var1;
   }

   public static NpcRelations load(CompoundTag var0, Provider var1) {
      NpcRelations var2 = new NpcRelations();
      CompoundTag var3 = var0.getCompound("Npcs");

      for (String var5 : var3.getAllKeys()) {
         UUID var6;
         try {
            var6 = UUID.fromString(var5);
         } catch (IllegalArgumentException var15) {
            continue;
         }

         ListTag var7 = var3.getList(var5, 10);
         HashMap var8 = new HashMap();

         for (int var9 = 0; var9 < var7.size(); var9++) {
            CompoundTag var10 = var7.getCompound(var9);

            try {
               UUID var11 = UUID.fromString(var10.getString("Id"));
               String var12 = NpcProfile.clean(var10.getString("Name"), 32);
               int var13 = NpcProfile.clamp(var10.getInt("V"), 0, 100);
               var8.put(var11, new NpcRelations.Rel(var11, var12, var13));
            } catch (IllegalArgumentException var14) {
            }
         }

         if (!var8.isEmpty()) {
            var2.byNpc.put(var6, var8);
         }
      }

      var2.questsDone.putAll(loadQuestSets(var0.getCompound("Quests")));
      var2.questsTaken.putAll(loadQuestSets(var0.getCompound("Taken")));
      var2.chainDone.putAll(loadChainDone(var0.getCompound("ChainDone")));
      var2.takenIds.putAll(loadIdSets(var0.getCompound("TakenIds")));
      var2.doneIds.putAll(loadIdSets(var0.getCompound("DoneIds")));
      ListTag var16 = var0.getList("ChainIds", 8);

      for (int var17 = 0; var17 < var16.size(); var17++) {
         String var18 = var16.getString(var17);
         if (!var18.isEmpty()) {
            var2.chainIds.add(var18);
         }
      }

      return var2;
   }

   public record Rel(UUID player, String name, int value) {
   }
}
