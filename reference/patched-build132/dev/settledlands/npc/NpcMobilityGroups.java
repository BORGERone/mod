package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/** Persistent world catalog and wand-backed route-group storage. */
public final class NpcMobilityGroups extends SavedData {
   public static final String DATA_NAME = "settledlands_mobility_groups";
   public static final int MAX_GROUPS = 256;
   public static final Factory<NpcMobilityGroups> FACTORY = new Factory<>(NpcMobilityGroups::new, NpcMobilityGroups::load, null);
   public static final String WAND_DATA_KEY = "SettledLandsMobilityWand";
   private static final int MAX_NAME = 32;
   private static NpcMobilityGroups fallbackInstance = null;
   private final Map<String, Group> groups = new LinkedHashMap<>();

   public static NpcMobilityGroups get(MinecraftServer server) {
      if (server == null) {
         if (fallbackInstance == null) fallbackInstance = new NpcMobilityGroups();
         return fallbackInstance;
      }
      try {
         ServerLevel ow = server.overworld();
         if (ow != null && ow.getDataStorage() != null) {
            return ow.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
         }
      } catch (Throwable ignored) {}
      if (fallbackInstance == null) fallbackInstance = new NpcMobilityGroups();
      return fallbackInstance;
   }

   public List<Group> groups() {
      ArrayList<Group> result = new ArrayList<>(this.groups.values());
      result.sort(Comparator.comparing(Group::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Group::id));
      return result;
   }

   public Group getGroup(String id) {
      return id == null ? null : this.groups.get(id);
   }

   public Group createGroup() {
      if (this.groups.size() >= MAX_GROUPS) {
         return null;
      }
      String id = UUID.randomUUID().toString();
      int number = 1;
      String name;
      do {
         name = "Group " + number++;
      } while (this.hasName(name, id));
      Group group = new Group(id, name, List.of());
      this.groups.put(id, group);
      this.setDirty();
      return group;
   }

   public Group ensureWandGroup(ItemStack stack) {
      if (stack == null || stack.isEmpty() || stack.getItem() != NpcContent.mobilityWand()) {
         return null;
      }
      CompoundTag itemData = readWandData(stack);
      String id = itemData.getString("Id");
      Group group = this.getGroup(id);
      if (group == null) {
         if (this.groups.size() >= MAX_GROUPS) {
            return null;
         }
         String restoredId = validId(id) ? id : UUID.randomUUID().toString();
         String restoredName = itemData.getString("Name");
         if (restoredName.isBlank()) {
            restoredName = this.nextDefaultName();
         }
         group = new Group(restoredId, this.uniqueName(restoredName, restoredId), readPoints(itemData));
         this.groups.put(group.id(), group);
         this.setDirty();
      }
      writeWandData(stack, group);
      return group;
   }

   public Group bindWandGroup(ItemStack stack, String id) {
      Group group = this.getGroup(id);
      if (group != null) {
         writeWandData(stack, group);
      }
      return group;
   }

   public AddResult addPoint(ItemStack stack, BlockPos target) {
      Group group = this.ensureWandGroup(stack);
      if (group == null) {
         return AddResult.NO_GROUP;
      }
      if (group.points().size() >= NpcProfile.MAX_MOBILITY_POINTS) {
         return AddResult.FULL;
      }
      String coordinates = target.getX() + " " + target.getY() + " " + target.getZ();
      if (group.points().stream().anyMatch(point -> coordinates.equals(point.coordinates))) {
         return AddResult.DUPLICATE;
      }
      ArrayList<NpcProfile.MobilityPoint> updated = new ArrayList<>(group.points());
      updated.add(new NpcProfile.MobilityPoint(
         NpcProfile.clean(coordinates, NpcProfile.MAX_MOBILITY_COORDINATES), 100, "5-10"
      ));
      Group replacement = new Group(group.id(), group.name(), updated);
      this.groups.put(replacement.id(), replacement);
      this.setDirty();
      writeWandData(stack, replacement);
      return AddResult.ADDED;
   }

   public boolean assignProfile(NpcProfile profile, String id) {
      if (profile == null) {
         return false;
      }
      if (id == null || id.isBlank()) {
         profile.mobilityGroupId = "";
         profile.mobilityGroupName = "";
         profile.mobilityPoints.clear();
         return true;
      }
      Group group = this.getGroup(id);
      if (group == null) {
         return false;
      }
      profile.mobilityGroupId = group.id();
      profile.mobilityGroupName = group.name();
      profile.mobilityPoints.clear();
      for (NpcProfile.MobilityPoint point : group.points()) {
         if (profile.mobilityPoints.size() >= NpcProfile.MAX_MOBILITY_POINTS) {
            break;
         }
         profile.mobilityPoints.add(copyPoint(point));
      }
      return true;
   }

   public boolean updateGroupName(String id, String name) {
      Group group = this.getGroup(id);
      if (group == null) {
         return false;
      }
      String cleaned = NpcProfile.clean(name, MAX_NAME).trim();
      if (cleaned.isEmpty()) {
         return false;
      }
      String unique = this.uniqueName(cleaned, id);
      if (unique.equals(group.name())) {
         return true;
      }
      this.groups.put(id, new Group(id, unique, group.points()));
      this.setDirty();
      return true;
   }

   public boolean updatePoint(String id, int index, String field, String value) {
      Group group = this.getGroup(id);
      if (group == null || index < 0 || index >= group.points().size()) {
         return false;
      }
      ArrayList<NpcProfile.MobilityPoint> updated = new ArrayList<>(group.points());
      NpcProfile.MobilityPoint point = updated.get(index);
      if ("chance".equals(field)) {
         point.chance = NpcProfile.clamp(parse(value, point.chance), 0, 100);
      } else if ("duration".equals(field)) {
         point.duration = NpcProfile.clean(value, NpcProfile.MAX_MOBILITY_DURATION);
      } else if ("coordinates".equals(field)) {
         point.coordinates = NpcProfile.clean(value, NpcProfile.MAX_MOBILITY_COORDINATES);
      } else {
         return false;
      }
      this.groups.put(id, new Group(group.id(), group.name(), updated));
      this.setDirty();
      return true;
   }

   public boolean removePoint(String id, int index) {
      Group group = this.getGroup(id);
      if (group == null || index < 0 || index >= group.points().size()) {
         return false;
      }
      ArrayList<NpcProfile.MobilityPoint> updated = new ArrayList<>(group.points());
      updated.remove(index);
      for (NpcProfile.MobilityPoint pt : updated) {
         if (pt.next == index) pt.next = -1;
         else if (pt.next > index) pt.next--;
         if (pt.falseNext == index) pt.falseNext = -1;
         else if (pt.falseNext > index) pt.falseNext--;
         for (int b = 0; b < pt.randomBranches.size(); b++) {
            int target = pt.randomBranches.get(b);
            if (target == index) pt.randomBranches.set(b, -1);
            else if (target > index) pt.randomBranches.set(b, target - 1);
         }
      }
      this.groups.put(id, new Group(id, group.name(), updated));
      this.setDirty();
      return true;
   }

   public boolean deleteGroup(String id) {
      if (id == null || !this.groups.containsKey(id)) {
         return false;
      }
      this.groups.remove(id);
      this.setDirty();
      return true;
   }

   public ListTag summaryTag() {
      ListTag list = new ListTag();
      for (Group group : this.groups()) {
         CompoundTag tag = new CompoundTag();
         tag.putString("Id", group.id());
         tag.putString("Name", group.name());
         tag.putInt("PointCount", group.points().size());
         list.add(tag);
      }
      return list;
   }

   public void sendWandView(ServerPlayer player, ItemStack stack, InteractionHand hand) {
      Group group = this.ensureWandGroup(stack);
      if (group == null) {
         return;
      }
      CompoundTag data = new CompoundTag();
      data.putInt("Hand", hand == InteractionHand.OFF_HAND ? 1 : 0);
      data.put("MobilityGroups", this.summaryTag());
      data.put("CurrentGroup", group.toTag());
      NpcNet.sendRaw(player, new NpcNet.CfgView(NpcNet.MOBILITY_EDITOR_ID, data));
      player.containerMenu.broadcastChanges();
   }

   public static void handleWandEdit(ServerPlayer player, int pointIndex, int handIndex, String op, String key, String value) {
      if (!player.hasPermissions(2)) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.settledlands.mobility.no_permission"), true);
         return;
      }
      InteractionHand hand = handIndex == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack stack = player.getItemInHand(hand);
      if (stack.getItem() != NpcContent.mobilityWand()) {
         return;
      }
      NpcMobilityGroups catalog = get(player.getServer());
      Group current = catalog.ensureWandGroup(stack);
      if (current == null) {
         return;
      }
      boolean refresh = true;
      switch (op == null ? "" : op) {
         case "wand.group.new": {
            Group created = catalog.createGroup();
            if (created != null) {
               catalog.bindWandGroup(stack, created.id());
            } else {
               player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.settledlands.mobility.group_limit"), true);
            }
            break;
         }
         case "wand.group.select": {
            String targetId = value != null && !value.isBlank() ? value : key;
            catalog.bindWandGroup(stack, targetId);
            break;
         }
         case "wand.group.name": {
            String newName = value != null && !value.isBlank() ? value : key;
            catalog.updateGroupName(current.id(), newName);
            catalog.bindWandGroup(stack, current.id());
            break;
         }
         case "wand.point.chance": {
            String val = value != null && !value.isBlank() ? value : key;
            catalog.updatePoint(current.id(), pointIndex, "chance", val);
            catalog.bindWandGroup(stack, current.id());
            break;
         }
         case "wand.point.duration": {
            String val = value != null && !value.isBlank() ? value : key;
            catalog.updatePoint(current.id(), pointIndex, "duration", val);
            catalog.bindWandGroup(stack, current.id());
            break;
         }
         case "wand.point.coords": {
            String val = value != null && !value.isBlank() ? value : key;
            catalog.updatePoint(current.id(), pointIndex, "coordinates", val);
            catalog.bindWandGroup(stack, current.id());
            break;
         }
         case "wand.point.add": {
            String val = value != null && !value.isBlank() ? value : key;
            String coords = (val != null && !val.isBlank()) ? val : (player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ());
            if (current.points().size() < NpcProfile.MAX_MOBILITY_POINTS) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               pts.add(new NpcProfile.MobilityPoint(NpcProfile.clean(coords, NpcProfile.MAX_MOBILITY_COORDINATES), 100, "5-10"));
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.point.del": {
            catalog.removePoint(current.id(), pointIndex);
            catalog.bindWandGroup(stack, current.id());
            break;
         }
         case "wand.group.del": {
            catalog.deleteGroup(current.id());
            Group nextGroup = null;
            if (!catalog.groups.isEmpty()) {
               nextGroup = catalog.groups.values().iterator().next();
            } else {
               nextGroup = catalog.createGroup();
            }
            if (nextGroup != null) {
               catalog.bindWandGroup(stack, nextGroup.id());
            }
            break;
         }
         case "wand.node.add": {
            if (current.points().size() < NpcProfile.MAX_MOBILITY_POINTS) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               int nodeType = pointIndex; // 0=Point, 1=Random, 2=If
               NpcProfile.MobilityPoint mp = new NpcProfile.MobilityPoint();
               mp.type = nodeType;
               if (nodeType == NpcProfile.MobilityPoint.TYPE_POINT) {
                  String coords = (value != null && !value.isBlank()) ? value : (player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ());
                  mp.coordinates = NpcProfile.clean(coords, NpcProfile.MAX_MOBILITY_COORDINATES);
                  mp.chance = 100;
                  mp.duration = "5-10";
               } else if (nodeType == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                  mp.randomMin = 1;
                  mp.randomMax = 5;
                  for (int i = 0; i < 5; i++) mp.randomBranches.add(-1);
               } else if (nodeType == NpcProfile.MobilityPoint.TYPE_IF) {
                  mp.chance = 50;
                  mp.next = -1;
                  mp.falseNext = -1;
               }
               int connectFrom = -1;
               int connectPort = 0;
               if (key != null) {
                  String[] colonSplit = key.split(":");
                  String coordsPart = colonSplit[0];
                  if (coordsPart.contains(",")) {
                     String[] split = coordsPart.split(",");
                     mp.canvasX = parse(split[0], 0);
                     mp.canvasY = parse(split[1], 0);
                  }
                  if (colonSplit.length >= 3) {
                     connectFrom = parse(colonSplit[1], -1);
                     connectPort = parse(colonSplit[2], 0);
                  }
               }
               int newIndex = pts.size();
               pts.add(mp);

               if (connectFrom >= 0 && connectFrom < pts.size() - 1) {
                  NpcProfile.MobilityPoint src = pts.get(connectFrom);
                  if (src.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                     src.next = newIndex;
                  } else if (src.type == NpcProfile.MobilityPoint.TYPE_IF) {
                     if (connectPort == 1) src.falseNext = newIndex;
                     else src.next = newIndex;
                  } else if (src.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                     while (src.randomBranches.size() < src.randomMax) {
                        src.randomBranches.add(-1);
                     }
                     if (connectPort >= 0 && connectPort < src.randomBranches.size()) {
                        src.randomBranches.set(connectPort, newIndex);
                     }
                  }
               }

               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.connect": {
            int from = pointIndex;
            int to = parse(key, -1);
            int port = parse(value, 0);
            if (from >= 0 && from < current.points().size() && to >= 0 && to < current.points().size() && from != to) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint src = pts.get(from);
               if (src.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                  src.next = to;
               } else if (src.type == NpcProfile.MobilityPoint.TYPE_IF) {
                  if (port == 1) {
                     src.falseNext = to;
                  } else {
                     src.next = to;
                  }
               } else if (src.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                  while (src.randomBranches.size() < src.randomMax) {
                     src.randomBranches.add(-1);
                  }
                  if (port >= 0 && port < src.randomBranches.size()) {
                     src.randomBranches.set(port, to);
                  }
               }
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.disconnect": {
            int from = pointIndex;
            int port = parse(value, 0);
            if (from >= 0 && from < current.points().size()) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint src = pts.get(from);
               if (src.type == NpcProfile.MobilityPoint.TYPE_POINT) {
                  src.next = -1;
               } else if (src.type == NpcProfile.MobilityPoint.TYPE_IF) {
                  if (port == 1) {
                     src.falseNext = -1;
                  } else {
                     src.next = -1;
                  }
               } else if (src.type == NpcProfile.MobilityPoint.TYPE_RANDOM) {
                  if (port >= 0 && port < src.randomBranches.size()) {
                     src.randomBranches.set(port, -1);
                  }
               }
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.move": {
            if (pointIndex >= 0 && pointIndex < current.points().size() && key != null && key.contains(",")) {
               String[] split = key.split(",");
               int nx = parse(split[0], 0);
               int ny = parse(split[1], 0);
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint pt = pts.get(pointIndex);
               pt.canvasX = nx;
               pt.canvasY = ny;
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.random_min": {
            int val = NpcProfile.clamp(parse(value != null && !value.isBlank() ? value : key, 1), 1, 64);
            if (pointIndex >= 0 && pointIndex < current.points().size()) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint pt = pts.get(pointIndex);
               pt.randomMin = Math.min(val, pt.randomMax);
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.random_max": {
            int val = NpcProfile.clamp(parse(value != null && !value.isBlank() ? value : key, 2), 1, 64);
            if (pointIndex >= 0 && pointIndex < current.points().size()) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint pt = pts.get(pointIndex);
               pt.randomMax = Math.max(pt.randomMin, val);
               while (pt.randomBranches.size() < pt.randomMax) {
                  pt.randomBranches.add(-1);
               }
               while (pt.randomBranches.size() > pt.randomMax) {
                  pt.randomBranches.remove(pt.randomBranches.size() - 1);
               }
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         case "wand.node.if_chance": {
            int val = NpcProfile.clamp(parse(value != null && !value.isBlank() ? value : key, 50), 0, 100);
            if (pointIndex >= 0 && pointIndex < current.points().size()) {
               ArrayList<NpcProfile.MobilityPoint> pts = new ArrayList<>(current.points());
               NpcProfile.MobilityPoint pt = pts.get(pointIndex);
               pt.chance = val;
               Group rep = new Group(current.id(), current.name(), pts);
               catalog.groups.put(rep.id(), rep);
               catalog.setDirty();
               catalog.bindWandGroup(stack, rep.id());
            }
            break;
         }
         default:
            refresh = false;
      }
      if (refresh) {
         catalog.sendWandView(player, stack, hand);
      }
   }

   public CompoundTag save(CompoundTag tag, Provider provider) {
      ListTag list = new ListTag();
      for (Group group : this.groups()) {
         list.add(group.toTag());
      }
      tag.put("Groups", list);
      return tag;
   }

   public static NpcMobilityGroups load(CompoundTag tag, Provider provider) {
      NpcMobilityGroups catalog = new NpcMobilityGroups();
      ListTag list = tag.getList("Groups", 10);
      for (int i = 0; i < list.size() && catalog.groups.size() < MAX_GROUPS; i++) {
         CompoundTag groupTag = list.getCompound(i);
         String id = groupTag.getString("Id");
         if (!validId(id) || catalog.groups.containsKey(id)) {
            continue;
         }
         String name = groupTag.getString("Name");
         if (name.isBlank()) {
            name = catalog.nextDefaultName();
         }
         Group group = new Group(id, catalog.uniqueName(name, id), readPoints(groupTag));
         catalog.groups.put(id, group);
      }
      return catalog;
   }

   private String nextDefaultName() {
      int number = 1;
      String name;
      do {
         name = "Group " + number++;
      } while (this.hasName(name, ""));
      return name;
   }

   private String uniqueName(String requested, String exceptId) {
      String base = NpcProfile.clean(requested, MAX_NAME).trim();
      if (base.isEmpty()) {
         base = "Group";
      }
      String candidate = base;
      int suffix = 2;
      while (this.hasName(candidate, exceptId)) {
         String tail = " (" + suffix++ + ")";
         int keep = Math.max(1, MAX_NAME - tail.length());
         candidate = base.substring(0, Math.min(base.length(), keep)) + tail;
      }
      return candidate;
   }

   private boolean hasName(String name, String exceptId) {
      String wanted = name.toLowerCase(Locale.ROOT);
      for (Group group : this.groups.values()) {
         if (!group.id().equals(exceptId) && group.name().toLowerCase(Locale.ROOT).equals(wanted)) {
            return true;
         }
      }
      return false;
   }

   private static boolean validId(String id) {
      try {
         UUID.fromString(id);
         return true;
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   private static int parse(String text, int fallback) {
      try {
         return Integer.parseInt(text == null ? "" : text.trim());
      } catch (NumberFormatException ignored) {
         return fallback;
      }
   }

   private static ArrayList<NpcProfile.MobilityPoint> readPoints(CompoundTag tag) {
      ArrayList<NpcProfile.MobilityPoint> points = new ArrayList<>();
      ListTag list = tag.getList("Points", 10);
      for (int i = 0; i < list.size() && points.size() < NpcProfile.MAX_MOBILITY_POINTS; i++) {
         CompoundTag pointTag = list.getCompound(i);
         int type = pointTag.contains("Type") ? pointTag.getInt("Type") : 0;
         String coordinates = NpcProfile.clean(pointTag.getString("Coordinates"), NpcProfile.MAX_MOBILITY_COORDINATES);
         if (type == NpcProfile.MobilityPoint.TYPE_POINT && coordinates.isBlank()) {
            continue;
         }
         int chance = NpcProfile.clamp(pointTag.contains("Chance") ? pointTag.getInt("Chance") : 100, 0, 100);
         String duration = NpcProfile.clean(pointTag.contains("Duration") ? pointTag.getString("Duration") : "5-10",
            NpcProfile.MAX_MOBILITY_DURATION);
         NpcProfile.MobilityPoint mp = new NpcProfile.MobilityPoint(coordinates, chance, duration);
         mp.type = type;
         mp.next = pointTag.contains("Next") ? pointTag.getInt("Next") : -1;
         mp.falseNext = pointTag.contains("FalseNext") ? pointTag.getInt("FalseNext") : -1;
         mp.randomMin = pointTag.contains("RandomMin") ? pointTag.getInt("RandomMin") : 1;
         mp.randomMax = pointTag.contains("RandomMax") ? pointTag.getInt("RandomMax") : 2;
         if (pointTag.contains("RandomBranches")) {
            for (int b : pointTag.getIntArray("RandomBranches")) mp.randomBranches.add(b);
         }
         mp.canvasX = pointTag.contains("CanvasX") ? pointTag.getInt("CanvasX") : 0;
         mp.canvasY = pointTag.contains("CanvasY") ? pointTag.getInt("CanvasY") : 0;
         mp.targetGroupId = pointTag.getString("TargetGroup");
         points.add(mp);
      }
      return points;
   }

   private static ArrayList<NpcProfile.MobilityPoint> copyPoints(List<NpcProfile.MobilityPoint> source) {
      ArrayList<NpcProfile.MobilityPoint> points = new ArrayList<>();
      if (source != null) {
         for (NpcProfile.MobilityPoint point : source) {
            if (point != null && points.size() < NpcProfile.MAX_MOBILITY_POINTS) {
               points.add(point.copy());
            }
         }
      }
      return points;
   }

   private static NpcProfile.MobilityPoint copyPoint(NpcProfile.MobilityPoint point) {
      return point == null ? null : point.copy();
   }

   private static CompoundTag readWandData(ItemStack stack) {
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return new CompoundTag();
      }
      CompoundTag root = customData.copyTag();
      return root.contains(WAND_DATA_KEY, 10) ? root.getCompound(WAND_DATA_KEY).copy() : new CompoundTag();
   }

   private static void writeWandData(ItemStack stack, Group group) {
      if (stack == null || stack.isEmpty() || group == null) {
         return;
      }
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      CompoundTag root = customData == null ? new CompoundTag() : customData.copyTag();
      root.put(WAND_DATA_KEY, group.toTag());
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
   }

   public enum AddResult {
      ADDED,
      DUPLICATE,
      FULL,
      NO_GROUP
   }

   public static final class Group {
      private final String id;
      private final String name;
      private final List<NpcProfile.MobilityPoint> points;

      private Group(String id, String name, List<NpcProfile.MobilityPoint> points) {
         this.id = id;
         this.name = NpcProfile.clean(name, MAX_NAME);
         this.points = List.copyOf(copyPoints(points));
      }

      public String id() {
         return this.id;
      }

      public String name() {
         return this.name;
      }

      public List<NpcProfile.MobilityPoint> points() {
         return copyPoints(this.points);
      }

      public CompoundTag toTag() {
         CompoundTag tag = new CompoundTag();
         tag.putString("Id", this.id);
         tag.putString("Name", this.name);
         ListTag pointTags = new ListTag();
         for (NpcProfile.MobilityPoint point : this.points) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putString("Coordinates", point.coordinates);
            pointTag.putInt("Chance", point.chance);
            pointTag.putString("Duration", point.duration);
            if (point.type != 0) pointTag.putInt("Type", point.type);
            if (point.next != -1) pointTag.putInt("Next", point.next);
            if (point.falseNext != -1) pointTag.putInt("FalseNext", point.falseNext);
            if (point.randomMin != 1) pointTag.putInt("RandomMin", point.randomMin);
            if (point.randomMax != 2) pointTag.putInt("RandomMax", point.randomMax);
            if (!point.randomBranches.isEmpty()) pointTag.putIntArray("RandomBranches", point.randomBranches.stream().mapToInt(Integer::intValue).toArray());
            if (point.canvasX != 0) pointTag.putInt("CanvasX", point.canvasX);
            if (point.canvasY != 0) pointTag.putInt("CanvasY", point.canvasY);
            if (!point.targetGroupId.isBlank()) pointTag.putString("TargetGroup", point.targetGroupId);
            pointTags.add(pointTag);
         }
         tag.put("Points", pointTags);
         return tag;
      }
   }
}
