package dev.settledlands.npc;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialGraph446Store;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * World-wide catalog of variables across NPCs, blocks, doors, chests, trials, {@link NpcPoints},
 * {@link VarScopeStore453}, and saved catalog definitions ({@code Build 457}).
 *
 * <p>Syncs transparently inside existing {@link NpcNet.CfgView} payloads under {@link #NBT_TAG}
 * (and via silent updates using {@link #SILENT_SYNC_ENTITY_ID}) and receives catalog edits through
 * {@link NpcNet.CfgEdit} operations prefixed with {@code "var457."}.
 */
public final class VarCatalogStore457 extends SavedData {

    public static final String DATA_NAME = "settledlands_var_catalog457";
    public static final String NBT_TAG = "VarCatalog457";
    public static final int SILENT_SYNC_ENTITY_ID = -2000599001;

    private static final String OP_LIST = "var457.list";
    private static final String OP_SAVE = "var457.save";
    private static final String OP_DELETE = "var457.delete";

    private static final int MAX_CATALOG_DEFS = 512;
    private static final int MAX_WIRE_ITEMS = 512;

    /** Explicitly saved/created variable definitions in the catalog SavedData. */
    public static final class SavedDef {
        public String name = "";
        public int scope = VarDataNode453.SCOPE_LOCAL;
        public int defaultValue = 0;
        public String ownerKey = "";
        public String ownerLabel = "";
        public String description = "";
    }

    /** Merged world catalog entry sent to the editor client. */
    public static final class CatalogItem {
        public String name = "";
        public int scope = VarDataNode453.SCOPE_LOCAL;
        public int value = 0;
        public int defaultValue = 0;
        public boolean hasLiveValue = false;
        public String ownerKey = "";
        public String ownerLabel = "";
        public String description = "";
        public boolean available = true;
        public int usageCount = 0;
        public boolean definedInCatalog = false;
    }

    private final Map<String, SavedDef> defs = new LinkedHashMap<>();

    public static VarCatalogStore457 get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VarCatalogStore457::new, VarCatalogStore457::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    private static VarCatalogStore457 load(CompoundTag tag, HolderLookup.Provider reg) {
        VarCatalogStore457 store = new VarCatalogStore457();
        ListTag list = tag.getList("Defs", 10);
        for (int i = 0; i < list.size() && store.defs.size() < MAX_CATALOG_DEFS; i++) {
            CompoundTag row = list.getCompound(i);
            String clean = VarScopeStore453.cleanVarName(row.getString("N"));
            if (clean.isEmpty()) continue;
            SavedDef def = new SavedDef();
            def.name = clean;
            def.scope = clampScope(row.getInt("S"));
            def.defaultValue = row.getInt("D");
            def.ownerKey = def.scope == VarDataNode453.SCOPE_LOCAL ? row.getString("OK") : "";
            def.ownerLabel = def.scope == VarDataNode453.SCOPE_LOCAL ? row.getString("OL") : "";
            def.description = trimLen(row.getString("Desc"), 128);
            store.defs.put(defKey(def.name, def.scope, def.ownerKey), def);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (SavedDef def : defs.values()) {
            if (def == null || def.name.isEmpty()) continue;
            CompoundTag row = new CompoundTag();
            row.putString("N", def.name);
            row.putInt("S", def.scope);
            row.putInt("D", def.defaultValue);
            if (!def.ownerKey.isEmpty()) row.putString("OK", def.ownerKey);
            if (!def.ownerLabel.isEmpty()) row.putString("OL", def.ownerLabel);
            if (!def.description.isEmpty()) row.putString("Desc", def.description);
            list.add(row);
        }
        tag.put("Defs", list);
        return tag;
    }

    public static int clampScope(int scope) {
        if (scope == VarDataNode453.SCOPE_SHARED) return VarDataNode453.SCOPE_SHARED;
        if (scope == VarDataNode453.SCOPE_PERSONAL) return VarDataNode453.SCOPE_PERSONAL;
        return VarDataNode453.SCOPE_LOCAL;
    }

    public static String defKey(String cleanName, int scope, String ownerKey) {
        int sc = clampScope(scope);
        String ok = sc == VarDataNode453.SCOPE_LOCAL && ownerKey != null ? ownerKey : "";
        return sc + "|" + ok + "|" + cleanName.toLowerCase(Locale.ROOT);
    }

    private static String trimLen(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    // ---- Editor context ownership resolution ----

    public record OwnerContext(String ownerKey, String ownerLabel, GuideNpc npc, String scopeKey) {}

    public static OwnerContext resolveOwnerContext(MinecraftServer server, ServerPlayer player, int entityId) {
        if (BlockTrigger351.isBlockEditorId(entityId)) {
            String raw = BlockTrigger351.keyOf(entityId);
            String key = raw == null || raw.isEmpty() ? "" : ("block:" + raw);
            return new OwnerContext(key, formatScopeOwnerLabel(key, null), null, key);
        }
        if (DoorGraph382.isDoorEditorId(entityId)) {
            String raw = DoorGraph382.keyOf(entityId);
            String key = raw == null || raw.isEmpty() ? "" : ("door:" + raw);
            return new OwnerContext(key, formatScopeOwnerLabel(key, null), null, key);
        }
        if (ChestGraph396.isChestEditorId(entityId)) {
            String raw = ChestGraph396.keyOf(entityId);
            String key = raw == null || raw.isEmpty() ? "" : ("chest:" + raw);
            return new OwnerContext(key, formatScopeOwnerLabel(key, null), null, key);
        }
        if (TrialGraph446.isTrialEditorId(entityId)) {
            String raw = TrialGraph446.keyOf(entityId);
            String key = raw == null || raw.isEmpty() ? "" : ("trial:" + raw);
            return new OwnerContext(key, formatScopeOwnerLabel(key, null), null, key);
        }
        GuideNpc npc = player != null && entityId > 0 ? GuideNpc.find(player, entityId) : null;
        if (npc == null && server != null && entityId > 0) {
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(entityId);
                if (e instanceof GuideNpc gn) {
                    npc = gn;
                    break;
                }
            }
        }
        if (npc != null) {
            String key = "npc:" + npc.getUUID();
            return new OwnerContext(key, formatNpcLabel(npc), npc, key);
        }
        return new OwnerContext("", "", null, "");
    }

    private static String formatNpcLabel(GuideNpc npc) {
        if (npc == null) return "NPC";
        NpcProfile p = npc.profile();
        if (p != null && p.name != null && !p.name.isBlank()) {
            return p.name.trim();
        }
        return "NPC #" + npc.getId();
    }

    private static String formatScopeOwnerLabel(String scopeKey, String blockId) {
        if (scopeKey == null || scopeKey.isEmpty()) return "";
        String prefix = "";
        String rest = scopeKey;
        if (scopeKey.startsWith("block:")) {
            prefix = "Блок";
            rest = scopeKey.substring(6);
        } else if (scopeKey.startsWith("door:")) {
            prefix = "Дверь";
            rest = scopeKey.substring(5);
        } else if (scopeKey.startsWith("chest:")) {
            prefix = "Сундук";
            rest = scopeKey.substring(6);
        } else if (scopeKey.startsWith("trial:")) {
            prefix = "Испытание";
            rest = scopeKey.substring(6);
        } else {
            return scopeKey;
        }
        String[] parts = rest.split("\\|");
        String coords = parts.length >= 4
                ? "(" + parts[1] + ", " + parts[2] + ", " + parts[3] + ")"
                : rest;
        if (blockId != null && !blockId.isBlank()) {
            int colon = blockId.indexOf(':');
            String shortId = colon >= 0 ? blockId.substring(colon + 1) : blockId;
            return prefix + " " + shortId + " " + coords;
        }
        return prefix + " " + coords;
    }

    // ---- Server-side CfgEdit dispatcher & catalog sender ----

    public static boolean isCatalogOp(String op) {
        return OP_LIST.equals(op) || OP_SAVE.equals(op) || OP_DELETE.equals(op);
    }

    public static void handleEdit(ServerPlayer player, int entityId, String op,
            int a, int b, int c, String key, String value) {
        if (player == null || op == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        VarCatalogStore457 store = get(server);
        if (store == null) return;

        if (OP_LIST.equals(op)) {
            sendCatalogTo(player, entityId);
            return;
        }

        OwnerContext ctx = resolveOwnerContext(server, player, entityId);

        if (OP_SAVE.equals(op)) {
            int scope = clampScope(a);
            int val = b;
            boolean writeLive = (c & 1) != 0 || c == 0;
            String cleanName = VarScopeStore453.cleanVarName(key);
            if (cleanName.isEmpty()) {
                sendCatalogTo(player, entityId);
                return;
            }
            // Payload format in value: "<oldName>|<oldScope>|<oldOwnerKey>\n<description>" or plain oldName
            String oldName = "";
            int oldScope = scope;
            String oldOwnerKey = "";
            String desc = "";
            if (value != null && !value.isEmpty()) {
                int nl = value.indexOf('\n');
                String head = nl >= 0 ? value.substring(0, nl) : value;
                if (nl >= 0) desc = trimLen(value.substring(nl + 1), 128);
                String[] parts = head.split("\\|", -1);
                if (parts.length >= 1) oldName = VarScopeStore453.cleanVarName(parts[0]);
                if (parts.length >= 2) {
                    try { oldScope = clampScope(Integer.parseInt(parts[1].trim())); }
                    catch (NumberFormatException ignored) {}
                }
                if (parts.length >= 3) oldOwnerKey = parts[2].trim();
            }

            String ownerKey = scope == VarDataNode453.SCOPE_LOCAL ? ctx.ownerKey() : "";
            String ownerLabel = scope == VarDataNode453.SCOPE_LOCAL ? ctx.ownerLabel() : "";

            // Handle rename or scope migration cleanly
            if (!oldName.isEmpty() && (!oldName.equals(cleanName) || oldScope != scope
                    || (oldScope == VarDataNode453.SCOPE_LOCAL && !oldOwnerKey.isEmpty()
                            && !oldOwnerKey.equals(ownerKey)))) {
                store.defs.remove(defKey(oldName, oldScope, oldOwnerKey));
                removeLiveValue(server, player, ctx, oldName, oldScope, oldOwnerKey);
            }

            SavedDef def = new SavedDef();
            def.name = cleanName;
            def.scope = scope;
            def.defaultValue = val;
            def.ownerKey = ownerKey;
            def.ownerLabel = ownerLabel;
            def.description = desc;
            if (store.defs.size() < MAX_CATALOG_DEFS || store.defs.containsKey(defKey(cleanName, scope, ownerKey))) {
                store.defs.put(defKey(cleanName, scope, ownerKey), def);
                store.setDirty();
            }

            if (writeLive) {
                writeLiveValue(server, player, ctx, cleanName, scope, val);
            }
            sendCatalogTo(player, entityId);
            return;
        }

        if (OP_DELETE.equals(op)) {
            int scope = clampScope(a);
            String cleanName = VarScopeStore453.cleanVarName(key);
            if (cleanName.isEmpty()) {
                sendCatalogTo(player, entityId);
                return;
            }
            String reqOwner = scope == VarDataNode453.SCOPE_LOCAL
                    ? (value != null && !value.isBlank() ? value.trim() : ctx.ownerKey())
                    : "";

            // Local variable belonging to another NPC/object cannot be modified/deleted from a different editor
            if (scope == VarDataNode453.SCOPE_LOCAL && !reqOwner.isEmpty()
                    && !ctx.ownerKey().isEmpty() && !reqOwner.equals(ctx.ownerKey())) {
                sendCatalogTo(player, entityId);
                return;
            }

            store.defs.remove(defKey(cleanName, scope, reqOwner));
            store.setDirty();
            removeLiveValue(server, player, ctx, cleanName, scope, reqOwner);
            sendCatalogTo(player, entityId);
        }
    }

    private static void writeLiveValue(MinecraftServer server, ServerPlayer player, OwnerContext ctx,
            String cleanName, int scope, int value) {
        if (scope == VarDataNode453.SCOPE_SHARED) {
            NpcPoints.get(server).set(VarScopeStore453.ZERO_UUID, cleanName, true, value);
        } else if (scope == VarDataNode453.SCOPE_PERSONAL) {
            if (player != null) {
                NpcPoints.get(server).set(player.getUUID(), cleanName, false, value);
            }
        } else if (ctx != null) {
            if (ctx.npc() != null) {
                CompoundTag tag = VarScopeStore453.getOrCreateNpcLocalTag(ctx.npc(), true);
                if (tag != null) {
                    tag.putInt(cleanName, value);
                }
            } else if (!ctx.scopeKey().isEmpty()) {
                VarScopeStore453 vs = VarScopeStore453.get(server);
                if (vs != null) {
                    vs.setScopedLocal(ctx.scopeKey(), cleanName, value);
                }
            }
        }
    }

    private static void removeLiveValue(MinecraftServer server, ServerPlayer player, OwnerContext ctx,
            String cleanName, int scope, String ownerKey) {
        NpcPoints pts = NpcPoints.get(server);
        if ((Object) pts instanceof NpcPointsRemoveBridge457 bridge) {
            if (scope == VarDataNode453.SCOPE_SHARED) {
                bridge.settledlands457$removeVar(cleanName, true);
            } else if (scope == VarDataNode453.SCOPE_PERSONAL) {
                bridge.settledlands457$removeVar(cleanName, false);
            }
        }
        VarScopeStore453 vs = VarScopeStore453.get(server);
        if (scope == VarDataNode453.SCOPE_LOCAL) {
            String targetOwner = (ownerKey != null && !ownerKey.isEmpty()) ? ownerKey
                    : (ctx != null ? ctx.ownerKey() : "");
            if (ctx != null && ctx.npc() != null
                    && (targetOwner.isEmpty() || targetOwner.equals(ctx.ownerKey()))) {
                CompoundTag tag = VarScopeStore453.getOrCreateNpcLocalTag(ctx.npc(), false);
                if (tag != null) {
                    tag.remove(cleanName);
                }
            }
            if (vs != null && !targetOwner.isEmpty()) {
                vs.removeScopedLocal(targetOwner, cleanName);
                vs.removeInitKeysFor(cleanName, scope, targetOwner);
            }
        } else if (vs != null) {
            vs.removeInitKeysFor(cleanName, scope, "");
        }
    }

    public static void sendCatalogTo(ServerPlayer player, int contextEntityId) {
        if (player == null) return;
        CompoundTag payload = new CompoundTag();
        payload.put(NBT_TAG, buildClientTag(player.getServer(), player, contextEntityId));
        NpcNet.sendRaw(player, new NpcNet.CfgView(SILENT_SYNC_ENTITY_ID, payload));
    }

    // ---- World scanner + NBT builder ----

    public static CompoundTag buildClientTag(MinecraftServer server, ServerPlayer viewer, int contextEntityId) {
        OwnerContext ctx = resolveOwnerContext(server, viewer, contextEntityId);
        Map<String, CatalogItem> merged = new LinkedHashMap<>();
        VarCatalogStore457 catalogStore = get(server);
        NpcPoints points = server != null ? NpcPoints.get(server) : null;
        VarScopeStore453 scopeStore = VarScopeStore453.get(server);

        // 1. Saved catalog definitions
        if (catalogStore != null) {
            for (SavedDef def : catalogStore.defs.values()) {
                if (def == null || def.name.isEmpty()) continue;
                CatalogItem item = ensureItem(merged, def.name, def.scope, def.ownerKey, def.ownerLabel);
                item.defaultValue = def.defaultValue;
                item.description = def.description;
                item.definedInCatalog = true;
                if (!item.hasLiveValue) {
                    item.value = def.defaultValue;
                }
            }
        }

        // 2. NpcPoints: shared + personal across all tracked players
        if (points != null) {
            Map<String, Integer> shared = points.sharedPoints();
            if (shared != null) {
                for (Map.Entry<String, Integer> e : shared.entrySet()) {
                    String clean = VarScopeStore453.cleanVarName(e.getKey());
                    if (clean.isEmpty()) continue;
                    CatalogItem item = ensureItem(merged, clean, VarDataNode453.SCOPE_SHARED, "", "");
                    item.value = e.getValue() != null ? e.getValue() : 0;
                    item.hasLiveValue = true;
                }
            }
            if (viewer != null) {
                Map<String, Integer> viewerPers = points.personalPoints(viewer.getUUID());
                if (viewerPers != null) {
                    for (Map.Entry<String, Integer> e : viewerPers.entrySet()) {
                        String clean = VarScopeStore453.cleanVarName(e.getKey());
                        if (clean.isEmpty()) continue;
                        CatalogItem item = ensureItem(merged, clean, VarDataNode453.SCOPE_PERSONAL, "", "");
                        item.value = e.getValue() != null ? e.getValue() : 0;
                        item.hasLiveValue = true;
                    }
                }
            }
            for (UUID uid : points.playersWithPoints()) {
                if (viewer != null && uid.equals(viewer.getUUID())) continue;
                Map<String, Integer> pers = points.personalPoints(uid);
                if (pers == null) continue;
                for (String rawName : pers.keySet()) {
                    String clean = VarScopeStore453.cleanVarName(rawName);
                    if (clean.isEmpty()) continue;
                    ensureItem(merged, clean, VarDataNode453.SCOPE_PERSONAL, "", "");
                }
            }
        }

        // 3. VarScopeStore453 object-local variables (blocks, doors, chests, trials)
        if (scopeStore != null) {
            Map<String, Map<String, Integer>> locals = scopeStore.snapshotLocalScopes();
            for (Map.Entry<String, Map<String, Integer>> sc : locals.entrySet()) {
                String ok = sc.getKey();
                String ol = formatScopeOwnerLabel(ok, null);
                for (Map.Entry<String, Integer> ve : sc.getValue().entrySet()) {
                    String clean = VarScopeStore453.cleanVarName(ve.getKey());
                    if (clean.isEmpty()) continue;
                    CatalogItem item = ensureItem(merged, clean, VarDataNode453.SCOPE_LOCAL, ok, ol);
                    item.value = ve.getValue() != null ? ve.getValue() : 0;
                    item.hasLiveValue = true;
                }
            }
        }

        // 4. Loaded GuideNpc entities (local NBT + graphs) across all levels
        Map<String, GuideNpc> loadedNpcsByOwner = new HashMap<>();
        if (server != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                for (Entity ent : lvl.getAllEntities()) {
                    if (!(ent instanceof GuideNpc npc)) continue;
                    String ok = "npc:" + npc.getUUID();
                    String ol = formatNpcLabel(npc);
                    loadedNpcsByOwner.put(ok, npc);
                    CompoundTag localTag = VarScopeStore453.getOrCreateNpcLocalTag(npc, false);
                    if (localTag != null) {
                        for (String key : localTag.getAllKeys()) {
                            String clean = VarScopeStore453.cleanVarName(key);
                            if (clean.isEmpty()) continue;
                            CatalogItem item = ensureItem(merged, clean, VarDataNode453.SCOPE_LOCAL, ok, ol);
                            item.value = localTag.getInt(key);
                            item.hasLiveValue = true;
                        }
                    }
                    NpcProfile prof = npc.profile();
                    if (prof != null) {
                        scanGraph(merged, prof.dialogueGraph, ok, ol);
                        scanGraph(merged, prof.walkGraph, ok, ol);
                        scanGraph(merged, prof.animGraph, ok, ol);
                    }
                }
            }

            // 5. Block / Door / Chest / Trial graphs across all dimensions
            BlockTriggerStore351 blkStore = BlockTriggerStore351.get(server);
            DoorGraph382Store doorStore = DoorGraph382Store.get(server);
            ChestGraph396Store chestStore = ChestGraph396Store.get(server);
            TrialGraph446Store trialStore = TrialGraph446Store.get(server);

            for (ServerLevel lvl : server.getAllLevels()) {
                String dim = BlockTriggerStore351.dimensionOf(lvl);
                if (blkStore != null) {
                    for (BlockTriggerStore351.Entry e : blkStore.ofDimension(dim)) {
                        if (e == null) continue;
                        String ok = "block:" + e.key;
                        scanGraph(merged, e.graph, ok, formatScopeOwnerLabel(ok, e.blockId));
                    }
                }
                if (doorStore != null) {
                    for (String k : doorStore.ofDimension(dim)) {
                        String ok = "door:" + k;
                        scanGraph(merged, doorStore.graph(k), ok, formatScopeOwnerLabel(ok, null));
                    }
                }
                if (trialStore != null) {
                    for (TrialGraph446Store.Entry e : trialStore.ofDimension(dim)) {
                        if (e == null) continue;
                        String ok = "trial:" + e.key;
                        scanGraph(merged, e.graph, ok, formatScopeOwnerLabel(ok, null));
                    }
                }
            }
            if (chestStore != null) {
                for (String k : chestStore.keys()) {
                    String ok = "chest:" + k;
                    scanGraph(merged, chestStore.graph(k), ok, formatScopeOwnerLabel(ok, null));
                }
            }
        }

        // Fill live values for any remaining items (e.g., catalog defs with live values on NPC or NpcPoints)
        for (CatalogItem item : merged.values()) {
            if (item.scope == VarDataNode453.SCOPE_SHARED && points != null && !item.hasLiveValue) {
                int v = points.get(VarScopeStore453.ZERO_UUID, item.name, true);
                if (v != 0) {
                    item.value = v;
                    item.hasLiveValue = true;
                }
            } else if (item.scope == VarDataNode453.SCOPE_PERSONAL && points != null && viewer != null && !item.hasLiveValue) {
                int v = points.get(viewer.getUUID(), item.name, false);
                if (v != 0) {
                    item.value = v;
                    item.hasLiveValue = true;
                }
            } else if (item.scope == VarDataNode453.SCOPE_LOCAL && !item.hasLiveValue) {
                GuideNpc ownerNpc = loadedNpcsByOwner.get(item.ownerKey);
                if (ownerNpc != null) {
                    CompoundTag localTag = VarScopeStore453.getOrCreateNpcLocalTag(ownerNpc, false);
                    if (localTag != null && localTag.contains(item.name)) {
                        item.value = localTag.getInt(item.name);
                        item.hasLiveValue = true;
                    }
                }
                if (!item.hasLiveValue && scopeStore != null && scopeStore.hasScopedLocal(item.ownerKey, item.name)) {
                    item.value = scopeStore.getScopedLocal(item.ownerKey, item.name);
                    item.hasLiveValue = true;
                }
            }
            item.available = (item.scope != VarDataNode453.SCOPE_LOCAL)
                    || ctx.ownerKey().isEmpty()
                    || item.ownerKey.isEmpty()
                    || item.ownerKey.equals(ctx.ownerKey());
        }

        List<CatalogItem> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator
                .comparing((CatalogItem ci) -> !ci.available)
                .thenComparingInt(ci -> scopeOrder(ci.scope))
                .thenComparing(ci -> ci.name.toLowerCase(Locale.ROOT))
                .thenComparing(ci -> ci.ownerLabel.toLowerCase(Locale.ROOT)));

        CompoundTag out = new CompoundTag();
        out.putString("OwnerKey", ctx.ownerKey());
        out.putString("OwnerLabel", ctx.ownerLabel());
        out.putInt("ContextId", contextEntityId);
        ListTag list = new ListTag();
        for (int i = 0; i < sorted.size() && i < MAX_WIRE_ITEMS; i++) {
            CatalogItem ci = sorted.get(i);
            CompoundTag row = new CompoundTag();
            row.putString("N", ci.name);
            row.putInt("S", ci.scope);
            row.putInt("V", ci.value);
            row.putInt("D", ci.defaultValue);
            if (!ci.ownerKey.isEmpty()) row.putString("OK", ci.ownerKey);
            if (!ci.ownerLabel.isEmpty()) row.putString("OL", ci.ownerLabel);
            if (!ci.description.isEmpty()) row.putString("Desc", ci.description);
            row.putBoolean("A", ci.available);
            row.putInt("U", ci.usageCount);
            list.add(row);
        }
        out.put("Items", list);
        return out;
    }

    private static int scopeOrder(int scope) {
        if (scope == VarDataNode453.SCOPE_SHARED) return 0;
        if (scope == VarDataNode453.SCOPE_PERSONAL) return 1;
        return 2;
    }

    private static CatalogItem ensureItem(Map<String, CatalogItem> merged,
            String cleanName, int scope, String ownerKey, String ownerLabel) {
        int sc = clampScope(scope);
        String ok = sc == VarDataNode453.SCOPE_LOCAL && ownerKey != null ? ownerKey : "";
        String key = defKey(cleanName, sc, ok);
        CatalogItem existing = merged.get(key);
        if (existing != null) {
            if (existing.ownerLabel.isEmpty() && ownerLabel != null && !ownerLabel.isEmpty()) {
                existing.ownerLabel = ownerLabel;
            }
            return existing;
        }
        CatalogItem ci = new CatalogItem();
        ci.name = cleanName;
        ci.scope = sc;
        ci.ownerKey = ok;
        ci.ownerLabel = sc == VarDataNode453.SCOPE_LOCAL && ownerLabel != null ? ownerLabel : "";
        merged.put(key, ci);
        return ci;
    }

    private static void scanGraph(Map<String, CatalogItem> merged, NpcDialogueGraph graph,
            String ownerKey, String ownerLabel) {
        if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null) continue;
            boolean isVarNode = VarDataNode453.isVarDataNode(node);
            boolean usesVar = isVarNode
                    || node.type == NpcDialogueGraph.VARIABLE
                    || node.type == NpcDialogueGraph.IF;
            if (!usesVar) continue;
            if (node.pointsName == null || node.pointsName.isBlank()) continue;
            String clean = VarScopeStore453.cleanVarName(node.pointsName);
            if (clean.isEmpty()) continue;
            int sc = VarDataNode453.scopeOf(node);
            CatalogItem item = ensureItem(merged, clean, sc, ownerKey, ownerLabel);
            item.usageCount++;
            if (isVarNode && !item.definedInCatalog && !item.hasLiveValue && node.varValue != 0) {
                item.defaultValue = node.varValue;
                item.value = node.varValue;
            }
        }
    }
}
