package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class NpcPoints extends SavedData {
   public static final String DATA_NAME = "settledlands_npc_points";
   public static final Factory<NpcPoints> FACTORY = new Factory(NpcPoints::new, NpcPoints::load, null);
   public static final int MAX_NAME = 32;
   public static final int MAX_VALUE = 1000000;
   private final Map<String, Integer> shared = new HashMap<>();
   private final Map<UUID, Map<String, Integer>> personal = new HashMap<>();

   public static NpcPoints get(MinecraftServer var0) {
      return (NpcPoints)var0.overworld().getDataStorage().computeIfAbsent(FACTORY, "settledlands_npc_points");
   }

   public static String cleanName(String var0) {
      return NpcProfile.clean(var0, 32);
   }

   public int get(UUID var1, String var2, boolean var3) {
      String var4 = cleanName(var2);
      if (var4.isEmpty()) {
         return 0;
      } else {
         Map var5 = var3 ? this.shared : this.personal.get(var1);
         Integer var6 = var5 == null ? null : (Integer)var5.get(var4);
         return var6 == null ? 0 : var6;
      }
   }

   public int have(UUID var1, NpcProfile.Quest var2) {
      return var2 != null && var2.points() ? this.get(var1, var2.pointsName, var2.pointsShared) : 0;
   }

   public int add(UUID var1, String var2, boolean var3, int var4) {
      String var5 = cleanName(var2);
      if (!var5.isEmpty() && var4 != 0) {
         int var6 = clamp(this.get(var1, var5, var3) + var4);
         this.put(var1, var5, var3, var6);
         return var6;
      } else {
         return this.get(var1, var5, var3);
      }
   }

   public void set(UUID var1, String var2, boolean var3, int var4) {
      String var5 = cleanName(var2);
      if (!var5.isEmpty()) {
         this.put(var1, var5, var3, clamp(var4));
      }
   }

   public boolean spend(UUID var1, String var2, boolean var3, int var4) {
      if (var4 <= 0) {
         return true;
      } else if (this.get(var1, var2, var3) < var4) {
         return false;
      } else {
         this.add(var1, var2, var3, -var4);
         return true;
      }
   }

   public static NpcPoints.Grant apply(NpcPoints var0, UUID var1, String var2, boolean var3, int var4, boolean var5) {
      int var6 = var0.get(var1, var2, var3);
      if (var4 <= 0 || !var5) {
         return new NpcPoints.Grant(true, var0.add(var1, var2, var3, Math.max(0, var4)), var6);
      } else {
         return !var0.spend(var1, var2, var3, var4) ? new NpcPoints.Grant(false, var6, var6) : new NpcPoints.Grant(true, var0.get(var1, var2, var3), var6);
      }
   }

   public List<String> lines(UUID var1) {
      ArrayList var2 = new ArrayList();
      Map var3 = this.personal.get(var1);
      if (var3 != null) {
         for (String var5 : sorted(var3.keySet())) {
            var2.add("· " + var5 + " = " + var3.get(var5));
         }
      }

      for (String var7 : sorted(this.shared.keySet())) {
         var2.add("· " + var7 + " (общие) = " + this.shared.get(var7));
      }

      return var2;
   }

   public List<UUID> playersWithPoints() {
      ArrayList var1 = new ArrayList();

      for (Entry var3 : this.personal.entrySet()) {
         if (var3.getKey() != null && var3.getValue() != null && !((Map)var3.getValue()).isEmpty()) {
            var1.add((UUID)var3.getKey());
         }
      }

      var1.sort(Comparator.comparing(UUID::toString));
      return var1;
   }

   public Map<String, Integer> personalPoints(UUID var1) {
      Map var2 = this.personal.get(var1);
      return var2 == null ? new TreeMap<>() : new TreeMap<>(var2);
   }

   public Map<String, Integer> sharedPoints() {
      return new TreeMap<>(this.shared);
   }

   public static String describe(Map<String, Integer> var0) {
      StringBuilder var1 = new StringBuilder();

      for (Entry var3 : var0.entrySet()) {
         if (var1.length() > 0) {
            var1.append(" · ");
         }

         var1.append((String)var3.getKey()).append(' ').append(var3.getValue());
      }

      return var1.toString();
   }

   public List<String> names() {
      TreeSet var1 = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      var1.addAll(this.shared.keySet());

      for (Map var3 : this.personal.values()) {
         var1.addAll(var3.keySet());
      }

      return new ArrayList<>(var1);
   }

   private static List<String> sorted(Set<String> var0) {
      ArrayList var1 = new ArrayList(var0);
      var1.sort(String.CASE_INSENSITIVE_ORDER);
      return var1;
   }

   private static int clamp(int var0) {
      return var0 < -1000000 ? -1000000 : (var0 > 1000000 ? 1000000 : var0);
   }

   private void put(UUID var1, String var2, boolean var3, int var4) {
      Map var5 = var3 ? this.shared : this.personal.computeIfAbsent(var1, var0 -> new HashMap<>());
      if (var4 == 0) {
         var5.remove(var2);
      } else {
         var5.put(var2, var4);
      }

      this.setDirty();
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      CompoundTag var3 = new CompoundTag();

      for (Entry var5 : this.shared.entrySet()) {
         var3.putInt((String)var5.getKey(), (Integer)var5.getValue());
      }

      var1.put("Shared", var3);
      ListTag var10 = new ListTag();

      for (Entry var6 : this.personal.entrySet()) {
         if (!((Map)var6.getValue()).isEmpty()) {
            CompoundTag var7 = new CompoundTag();

            for (Entry var9 : ((Map)var6.getValue()).entrySet()) {
               var7.putInt((String)var9.getKey(), (Integer)var9.getValue());
            }

            CompoundTag var12 = new CompoundTag();
            var12.putString("Id", ((UUID)var6.getKey()).toString());
            var12.put("Points", var7);
            var10.add(var12);
         }
      }

      var1.put("Personal", var10);
      return var1;
   }

   public static NpcPoints load(CompoundTag var0, Provider var1) {
      NpcPoints var2 = new NpcPoints();
      CompoundTag var3 = var0.getCompound("Shared");

      for (String var5 : var3.getAllKeys()) {
         String var6 = cleanName(var5);
         if (!var6.isEmpty()) {
            var2.shared.put(var6, clamp(var3.getInt(var5)));
         }
      }

      ListTag var14 = var0.getList("Personal", 10);

      for (int var15 = 0; var15 < var14.size(); var15++) {
         CompoundTag var16 = var14.getCompound(var15);

         UUID var7;
         try {
            var7 = UUID.fromString(var16.getString("Id"));
         } catch (IllegalArgumentException var13) {
            continue;
         }

         CompoundTag var8 = var16.getCompound("Points");
         HashMap var9 = new HashMap();

         for (String var11 : var8.getAllKeys()) {
            String var12 = cleanName(var11);
            if (!var12.isEmpty()) {
               var9.put(var12, clamp(var8.getInt(var11)));
            }
         }

         if (!var9.isEmpty()) {
            var2.personal.put(var7, var9);
         }
      }

      return var2;
   }

   public record Grant(boolean ok, int value, int before) {
   }
}
