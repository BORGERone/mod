package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** World storage of recorded cutscene camera tracks (name -> per-tick x,y,z,yaw,pitch keys). */
public final class CutsceneStorage extends SavedData {
   private final Map<String, List<double[]>> scenes = new LinkedHashMap<>();
   private int counter;

   public static CutsceneStorage get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(factory(), "settledlands_cutscenes");
   }

   static SavedData.Factory<CutsceneStorage> factory() {
      return new SavedData.Factory<>(CutsceneStorage::new, CutsceneStorage::load, DataFixTypes.LEVEL);
   }

   static CutsceneStorage load(CompoundTag tag, HolderLookup.Provider registries) {
      CutsceneStorage data = new CutsceneStorage();
      data.counter = tag.getInt("Counter");
      ListTag list = tag.getList("Scenes", 10);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag e = list.getCompound(i);
         String name = e.getString("N");
         List<double[]> keys = new ArrayList<>();
         ListTag flat = e.getList("K", 6);
         for (int k = 0; k + 4 < flat.size(); k += 5) {
            keys.add(new double[]{flat.getDouble(k), flat.getDouble(k + 1), flat.getDouble(k + 2), flat.getDouble(k + 3), flat.getDouble(k + 4)});
         }
         if (!name.isEmpty() && keys.size() >= 2) data.scenes.put(name, keys);
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("Counter", counter);
      ListTag list = new ListTag();
      for (Map.Entry<String, List<double[]>> e : scenes.entrySet()) {
         CompoundTag entry = new CompoundTag();
         entry.putString("N", e.getKey());
         ListTag flat = new ListTag();
         for (double[] key : e.getValue()) {
            for (double v : key) flat.add(DoubleTag.valueOf(v));
         }
         entry.put("K", flat);
         list.add(entry);
      }
      tag.put("Scenes", list);
      return tag;
   }

   /** Stores a finished recording under a fresh auto name (scene_001, scene_002, ...). */
   public String store(List<double[]> keys) {
      String name;
      do {
         counter++;
         name = "scene_" + String.format(Locale.ROOT, "%03d", counter);
      } while (scenes.containsKey(name));
      scenes.put(name, keys);
      setDirty();
      return name;
   }

   public List<double[]> scene(String name) {
      return scenes.get(name);
   }

   public List<String> names() {
      List<String> result = new ArrayList<>(scenes.keySet());
      Collections.sort(result);
      return result;
   }
}
