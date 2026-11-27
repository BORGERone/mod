package dev.settledlands.trial;

import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.CustomBookNet443;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlockTriggerStore351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphAnchor420;
import dev.settledlands.npc.NpcNet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 446: graph core for {@code trialtuner:trial_spawner}.
 *
 * <p>Node {@link #TRIAL_TYPE} ({@code 60}) is the card on the spawner's canvas:</p>
 * <ul>
 *   <li>{@code action}: serialized {@link TrialConfig446} JSON synced live with
 *       {@code TrialSpawnerBlockEntity};</li>
 *   <li>{@code text}: last participant player name (used on card &amp; blue player pin);</li>
 *   <li>{@code varValue}: completed wave counter;</li>
 *   <li>{@code next}: event socket 1 — <b>Зачистка волны</b> (on wave finish);</li>
 *   <li>{@code falseNext}: event socket 2 — <b>Уничтожение</b> (on spawner destruction);</li>
 *   <li>{@code originStep}: event socket 3 — <b>Старт волны</b> (encoded {@code targetId + 1},
 *       {@code 0 == -1}, so default {@code 0} means unwired).</li>
 * </ul>
 */
public final class TrialGraph446 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialGraph446");

    public static final int ID_MAX = -2_000_400_000;
    public static final int ID_MIN = -2_000_499_999;

    public static final int TRIAL_TYPE = 60;
    public static final String TITLE_KEY = "gui.settledlands.trial.node";
    public static final String BE_GRAPH_TAG = "SettledLandsTrialGraph";
    public static final int ACCENT_COLOR = -13264501; // 0xFF35998B copper-oxidized teal

    private static final int MAX_IDS = 8192;
    private static final int MAX_LAST_PLAYER = 32;

    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    private static final Map<Integer, String> BY_ID = new HashMap<>();
    private static int nextSlot;

    /** Tracks spawner keys whose onDestroy socket already ran inside TrialTriggers.onDestroyed. */
    private static final Set<String> DESTROY_FIRED = new HashSet<>();

    /** Fallback participant cache so destroy-by-setblock / explosion still knows the last player. */
    private static final Map<String, List<UUID>> LAST_PLAYERS = new HashMap<>();

    private static final Set<String> WARNED = new HashSet<>();

    private TrialGraph446() {}

    // ---- editor id range ----------------------------------------------------

    public static boolean isTrialEditorId(int id) {
        return id >= ID_MIN && id <= ID_MAX;
    }

    public static synchronized int idFor(String key) {
        if (key == null || key.isEmpty()) return ID_MAX;
        Integer known = BY_KEY.get(key);
        if (known != null) return known;
        if (BY_KEY.size() >= MAX_IDS) {
            BY_KEY.clear();
            BY_ID.clear();
            nextSlot = 0;
        }
        int id = ID_MAX - nextSlot++;
        BY_KEY.put(key, id);
        BY_ID.put(id, key);
        return id;
    }

    public static synchronized String keyOf(int id) {
        return BY_ID.get(id);
    }

    // ---- card inspection / sockets ------------------------------------------

    public static boolean isTrialNode(NpcDialogueGraph.Node node) {
        return node != null && node.type == TRIAL_TYPE;
    }

    public static NpcDialogueGraph.Node trialNode(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return null;
        NpcDialogueGraph.Node entry = graph.get(graph.entry);
        if (isTrialNode(entry)) return entry;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (isTrialNode(node)) return node;
        }
        return null;
    }

    public static TrialConfig446 configOf(NpcDialogueGraph.Node node) {
        if (!isTrialNode(node)) return TrialConfig446.defaults();
        return TrialConfig446.fromJson(node.action);
    }

    public static void setConfig(NpcDialogueGraph.Node node, TrialConfig446 cfg) {
        if (!isTrialNode(node)) return;
        node.action = (cfg == null ? TrialConfig446.defaults() : cfg).toJson();
    }

    public static String lastPlayer(NpcDialogueGraph.Node node) {
        if (!isTrialNode(node) || node.text == null) return "";
        return node.text.trim();
    }

    public static void setLastPlayer(NpcDialogueGraph.Node node, String name) {
        if (!isTrialNode(node) || name == null) return;
        String trimmed = name.trim();
        node.text = trimmed.length() <= MAX_LAST_PLAYER
                ? trimmed : trimmed.substring(0, MAX_LAST_PLAYER);
    }

    public static int waves(NpcDialogueGraph.Node node) {
        return isTrialNode(node) ? Math.max(0, node.varValue) : 0;
    }

    public static void setWaves(NpcDialogueGraph.Node node, int count) {
        if (!isTrialNode(node)) return;
        node.varValue = Math.max(0, Math.min(999_999, count));
    }

    /** Socket 1 (next): Зачистка волны. */
    public static int waveClearTarget(NpcDialogueGraph.Node card) {
        return isTrialNode(card) ? card.next : -1;
    }

    public static void setWaveClearTarget(NpcDialogueGraph.Node card, int targetId) {
        if (!isTrialNode(card)) return;
        card.next = targetId < 0 ? -1 : targetId;
    }

    /** Socket 2 (falseNext): Уничтожение спавнера. */
    public static int destroyTarget(NpcDialogueGraph.Node card) {
        return isTrialNode(card) ? card.falseNext : -1;
    }

    public static void setDestroyTarget(NpcDialogueGraph.Node card, int targetId) {
        if (!isTrialNode(card)) return;
        card.falseNext = targetId < 0 ? -1 : targetId;
    }

    /** Socket 3 (originStep encoded as targetId + 1; 0 == -1): Старт волны. */
    public static int startWaveTarget(NpcDialogueGraph.Node card) {
        if (!isTrialNode(card)) return -1;
        return card.originStep > 0 ? card.originStep - 1 : -1;
    }

    public static void setStartWaveTarget(NpcDialogueGraph.Node card, int targetId) {
        if (!isTrialNode(card)) return;
        card.originStep = targetId < 0 ? 0 : targetId + 1;
    }

    public static NpcDialogueGraph.Node waveClearStart(NpcDialogueGraph graph) {
        return resolveTarget(graph, waveClearTarget(trialNode(graph)));
    }

    public static NpcDialogueGraph.Node destroyStart(NpcDialogueGraph graph) {
        return resolveTarget(graph, destroyTarget(trialNode(graph)));
    }

    public static NpcDialogueGraph.Node startWaveStart(NpcDialogueGraph graph) {
        return resolveTarget(graph, startWaveTarget(trialNode(graph)));
    }

    private static NpcDialogueGraph.Node resolveTarget(NpcDialogueGraph graph, int targetId) {
        if (graph == null || targetId < 0) return null;
        NpcDialogueGraph.Node card = trialNode(graph);
        NpcDialogueGraph.Node target = graph.get(targetId);
        if (target == null || target == card || isTrialNode(target)) return null;
        return target;
    }

    /** Clears broken references to a deleted node from any trial card's third socket and re-syncs modules. */
    public static void onNodeRemoved(NpcDialogueGraph graph, int removedId) {
        if (graph == null || graph.nodes == null || removedId < 0) return;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (isTrialNode(n) && startWaveTarget(n) == removedId) {
                setStartWaveTarget(n, -1);
            }
        }
        TrialModule447.syncCardConfig(graph);
    }

    public static boolean allowed(int type) {
        return type == TRIAL_TYPE || BlockTrigger351.allowed(type);
    }

    public static NpcDialogueGraph defaultGraph(String liveConfigJson) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        graph.enabled = true;
        TrialConfig446 cfg = liveConfigJson != null && !liveConfigJson.isBlank()
                ? TrialConfig446.fromJson(liveConfigJson)
                : TrialConfig446.defaults();
        NpcDialogueGraph.Node card = graph.add(TRIAL_TYPE, 660, 247);
        if (card != null) {
            card.text = "";
            card.varValue = 0;
            card.varOp = 1;
            card.originStep = 0;
            card.falseNext = -1;
            card.next = -1;
            card.action = cfg.toJson();
            graph.entry = card.id;
            TrialModule447.createMissingModules(graph, card, cfg);
        }
        return graph;
    }

    public static NpcDialogueGraph withTrialNode(NpcDialogueGraph graph) {
        if (graph == null) return defaultGraph(null);
        NpcDialogueGraph.Node card = trialNode(graph);
        if (card == null) {
            NpcDialogueGraph.Node made = graph.add(TRIAL_TYPE, 660, 247);
            if (made != null) {
                TrialConfig446 cfg = TrialConfig446.defaults();
                made.action = cfg.toJson();
                made.originStep = 0;
                made.varOp = 1;
                if (graph.entry < 0) graph.entry = made.id;
                card = made;
                TrialModule447.createMissingModules(graph, card, cfg);
            }
        } else if (card.varOp == 0 && !TrialModule447.hasAnyModule(graph)) {
            TrialConfig446 legacy = (card.action == null || card.action.isBlank())
                    ? TrialConfig446.defaults()
                    : TrialConfig446.fromJson(card.action);
            card.x = 660;
            card.y = 247;
            card.varOp = 1;
            TrialModule447.createMissingModules(graph, card, legacy);
        }
        if (card != null) {
            TrialModule447.syncCardConfig(graph);
            graph.entry = card.id;
        }
        return graph;
    }

    public static int stripCards(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return 0;
        int dropped = BlockName359.dropType(graph, BlockName359.STATS_TYPE)
                + BlockName359.dropType(graph, BlockName359.TYPE);
        if (graph.entry >= 0 && !graph.nodes.containsKey(graph.entry)) {
            NpcDialogueGraph.Node node = trialNode(graph);
            if (node != null) graph.entry = node.id;
        }
        return dropped;
    }

    // ---- BlockEntity NBT persistence ----------------------------------------

    public static boolean writeBlockEntityGraph(ServerLevel level, BlockPos pos, NpcDialogueGraph graph) {
        if (level == null || pos == null) return false;
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return false;
            CompoundTag pd = ((IBlockEntityExtension) be).getPersistentData();
            if (pd == null) return false;
            if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) {
                if (pd.contains(BE_GRAPH_TAG)) {
                    pd.remove(BE_GRAPH_TAG);
                    be.setChanged();
                }
                return false;
            }
            pd.put(BE_GRAPH_TAG, graph.save());
            be.setChanged();
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    public static boolean ensureBlockEntityGraph(ServerLevel level, BlockPos pos, NpcDialogueGraph graph) {
        if (level == null || pos == null || graph == null || graph.nodes == null || graph.nodes.isEmpty()) {
            return false;
        }
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return false;
            CompoundTag pd = ((IBlockEntityExtension) be).getPersistentData();
            if (pd == null) return false;
            if (pd.contains(BE_GRAPH_TAG, 10) && !pd.getCompound(BE_GRAPH_TAG).isEmpty()) {
                return false;
            }
            pd.put(BE_GRAPH_TAG, graph.save());
            be.setChanged();
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    public static NpcDialogueGraph readBlockEntityGraph(BlockEntity be) {
        if (be == null) return null;
        try {
            CompoundTag pd = ((IBlockEntityExtension) be).getPersistentData();
            if (pd == null || !pd.contains(BE_GRAPH_TAG, 10)) return null;
            CompoundTag tag = pd.getCompound(BE_GRAPH_TAG);
            if (tag == null || tag.isEmpty()) return null;
            NpcDialogueGraph loaded = NpcDialogueGraph.load(tag);
            if (loaded == null || loaded.nodes == null || loaded.nodes.isEmpty()) return null;
            loaded = withTrialNode(loaded);
            stripCards(loaded);
            return loaded;
        } catch (Throwable failure) {
            fail(failure);
            return null;
        }
    }

    /** Build 446: writes an anchor-materialized spawner graph into store, BE tag, and live config. */
    public static boolean materializeAt(ServerLevel level, int x, int y, int z, CompoundTag graphTag) {
        if (level == null || graphTag == null || graphTag.isEmpty()) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        TrialGraph446Store store = TrialGraph446Store.get(server);
        if (store == null) return false;
        NpcDialogueGraph loaded = NpcDialogueGraph.load(graphTag);
        if (loaded == null || loaded.nodes == null || loaded.nodes.isEmpty()) return false;
        loaded = withTrialNode(loaded);
        stripCards(loaded);
        BlockPos pos = new BlockPos(x, y, z);
        String key = TrialGraph446Store.key(level, pos);
        if (!store.put(key, loaded)) return false;
        NpcDialogueGraph.Node card = trialNode(loaded);
        if (card != null && card.action != null && !card.action.isBlank()) {
            TrialSpawnerBridge446.applyConfigJson(level, pos, card.action);
        }
        writeBlockEntityGraph(level, pos, loaded);
        return true;
    }

    public static int syncEmbeddedGraphsNear(ServerLevel level, List<? extends ServerPlayer> players) {
        if (level == null || players == null || players.isEmpty()) return 0;
        MinecraftServer server = level.getServer();
        if (server == null) return 0;
        TrialGraph446Store store = TrialGraph446Store.get(server);
        if (store == null) return 0;
        ServerChunkCache cache = level.getChunkSource();
        if (cache == null) return 0;

        Set<Long> visitedChunks = new HashSet<>();
        int imported = 0;
        for (int p = 0; p < players.size(); p++) {
            ServerPlayer player = players.get(p);
            if (player == null || player.level() != level) continue;
            int pcx = ((int) Math.floor(player.getX())) >> 4;
            int pcz = ((int) Math.floor(player.getZ())) >> 4;
            for (int dcx = -2; dcx <= 2; dcx++) {
                for (int dcz = -2; dcz <= 2; dcz++) {
                    int cx = pcx + dcx;
                    int cz = pcz + dcz;
                    long chunkKey = (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
                    if (!visitedChunks.add(chunkKey)) continue;
                    LevelChunk chunk = cache.getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
                    if (blockEntities == null || blockEntities.isEmpty()) continue;
                    for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
                        BlockPos pos = entry.getKey();
                        BlockEntity be = entry.getValue();
                        if (pos == null || be == null) continue;
                        if (!TrialSpawnerBridge446.isSpawnerAt(level, pos)) continue;
                        NpcDialogueGraph embedded = readBlockEntityGraph(be);
                        if (embedded == null) continue;
                        String key = TrialGraph446Store.key(level, pos);
                        if (key.isEmpty()) continue;
                        if (store.graph(key) == null && store.put(key, embedded)) {
                            // Sync embedded card config back onto the live spawner BE if needed.
                            NpcDialogueGraph.Node card = trialNode(embedded);
                            if (card != null && card.action != null && !card.action.isBlank()) {
                                TrialSpawnerBridge446.applyConfigJson(level, pos, card.action);
                            }
                            imported++;
                        }
                    }
                }
            }
        }
        return imported;
    }

    // ---- open / save --------------------------------------------------------

    public static boolean open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
            return false;
        }
        if (!TrialSpawnerBridge446.isSpawnerAt(level, pos)) return false;
        try {
            String key = TrialGraph446Store.key(level, pos);
            TrialGraph446Store store = TrialGraph446Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            String liveJson = TrialSpawnerBridge446.readConfigJson(level, pos);
            int[] stats = TrialSpawnerBridge446.readRuntimeStats(level, pos);
            boolean created = false;
            if (graph == null) {
                graph = defaultGraph(liveJson);
                if (store != null) store.put(key, graph);
                created = true;
            } else {
                graph = withTrialNode(graph);
                stripCards(graph);
            }
            NpcDialogueGraph.Node card = trialNode(graph);
            if (card != null) {
                boolean diskSynced = TrialModule447.refreshPoolsFromServerDisk(graph);
                if (diskSynced && card.action != null && !card.action.isBlank()) {
                    TrialSpawnerBridge446.applyConfigJson(level, pos, card.action);
                } else if (liveJson != null && !liveJson.isBlank()) {
                    TrialConfig446 liveCfg = TrialConfig446.fromJson(liveJson);
                    TrialConfig446 graphCfg = TrialModule447.compileConfig(graph);
                    if (!liveCfg.toJson().equals(graphCfg.toJson())) {
                        TrialModule447.applyConfigToConnectedModules(graph, liveCfg);
                    }
                }
                if (stats[1] > waves(card)) {
                    setWaves(card, stats[1]);
                }
                if (store != null) store.put(key, graph);
            }
            if (created) {
                writeBlockEntityGraph(level, pos, graph);
                NpcGraphAnchor420.anchorForSave(level, pos, true);
            } else {
                ensureBlockEntityGraph(level, pos, graph);
            }
            ChestLootNet436.sendPools(player);
            CustomBookNet443.sendBookSummaries(player);
            TrialMobPoolNet449.sendPools(player);
            CompoundTag data = new CompoundTag();
            data.put("DialogueGraph", graph.save());
            NpcNet.sendRaw(player, new NpcNet.CfgView(idFor(key), data));
            player.displayClientMessage(Component.translatable("message.settledlands.trial.graph_open",
                    Component.literal(coords(pos))), true);
            return true;
        } catch (Throwable failure) {
            fail(failure);
            return false;
        }
    }

    public static void save(ServerPlayer player, int id, CompoundTag data) {
        if (player == null || data == null) return;
        String key = keyOf(id);
        if (key == null || key.isEmpty()) return;
        try {
            CompoundTag tag = data.getCompound("DialogueGraph");
            NpcDialogueGraph graph = tag.isEmpty() ? null : NpcDialogueGraph.load(tag);
            BlockPos savedPos = TrialGraph446Store.positionOf(key);
            ServerLevel targetLevel = resolveLevelForKey(player.getServer(), key,
                    player.level() instanceof ServerLevel sl ? sl : null);
            if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) {
                TrialGraph446Store store = TrialGraph446Store.get(player.getServer());
                if (store != null) store.put(key, null);
                if (targetLevel != null && savedPos != null) {
                    writeBlockEntityGraph(targetLevel, savedPos, null);
                    NpcGraphAnchor420.anchorForSave(targetLevel, savedPos, false);
                }
                player.displayClientMessage(Component.translatable(
                        "message.settledlands.trial.graph_cleared",
                        Component.literal(coords(savedPos))), true);
                return;
            }
            graph = withTrialNode(graph);
            TrialModule447.refreshPoolsFromServerDisk(graph);
            stripCards(graph);
            NpcDialogueGraph.Node card = trialNode(graph);
            TrialGraph446Store store = TrialGraph446Store.get(player.getServer());
            if (store == null) return;
            if (!store.put(key, graph)) {
                player.displayClientMessage(Component.translatable(
                        "message.settledlands.trial.graph_full",
                        Component.literal(String.valueOf(TrialGraph446Store.MAX_SPAWNERS))), false);
                return;
            }
            if (targetLevel != null && savedPos != null) {
                if (card != null && card.action != null && !card.action.isBlank()) {
                    TrialSpawnerBridge446.applyConfigJson(targetLevel, savedPos, card.action);
                }
                writeBlockEntityGraph(targetLevel, savedPos, graph);
                NpcGraphAnchor420.anchorForSave(targetLevel, savedPos, true);
            }
            player.displayClientMessage(Component.translatable("message.settledlands.trial.graph_saved",
                    Component.literal(coords(savedPos))), true);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    // ---- spawner lifecycle events -------------------------------------------

    public static void onStartWave(ServerLevel level, BlockPos pos, Collection<UUID> participants) {
        if (level == null || pos == null) return;
        try {
            String key = TrialGraph446Store.key(level, pos);
            rememberParticipants(key, participants);
            TrialGraph446Store store = TrialGraph446Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return;
            graph = withTrialNode(graph);
            NpcDialogueGraph.Node card = trialNode(graph);
            ServerPlayer best = TrialSpawnerBridge446.resolveBestPlayer(level, pos, participants, null);
            if (card != null && best != null) {
                setLastPlayer(card, best.getName().getString());
                store.put(key, graph);
            }
            NpcDialogueGraph.Node start = startWaveStart(graph);
            if (start != null) {
                runChain(level, pos, key, graph, start, best);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static void onWaveCleared(ServerLevel level, BlockPos pos, Collection<UUID> participants) {
        if (level == null || pos == null) return;
        try {
            String key = TrialGraph446Store.key(level, pos);
            rememberParticipants(key, participants);
            TrialGraph446Store store = TrialGraph446Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return;
            graph = withTrialNode(graph);
            NpcDialogueGraph.Node card = trialNode(graph);
            ServerPlayer best = TrialSpawnerBridge446.resolveBestPlayer(level, pos, participants, null);
            if (card != null) {
                setWaves(card, waves(card) + 1);
                if (best != null) setLastPlayer(card, best.getName().getString());
                store.put(key, graph);
                ensureBlockEntityGraph(level, pos, graph);
            }
            NpcDialogueGraph.Node start = waveClearStart(graph);
            if (start != null) {
                runChain(level, pos, key, graph, start, best);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static void onSpawnerDestroyed(ServerLevel level, BlockPos pos, Collection<UUID> participants,
            ServerPlayer directActor, boolean markFired) {
        if (level == null || pos == null) return;
        try {
            String key = TrialGraph446Store.key(level, pos);
            if (key.isEmpty()) return;
            if (markFired) {
                DESTROY_FIRED.add(key);
            }
            TrialGraph446Store store = TrialGraph446Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) {
                LAST_PLAYERS.remove(key);
                return;
            }
            graph = withTrialNode(graph);
            Collection<UUID> effectiveUuids = participants != null && !participants.isEmpty()
                    ? participants : LAST_PLAYERS.remove(key);
            ServerPlayer best = TrialSpawnerBridge446.resolveBestPlayer(
                    level, pos, effectiveUuids, directActor);
            NpcDialogueGraph.Node card = trialNode(graph);
            if (card != null && best != null) {
                setLastPlayer(card, best.getName().getString());
            }
            NpcDialogueGraph.Node start = destroyStart(graph);
            if (start != null) {
                runChain(level, pos, key, graph, start, best);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static boolean consumeDestroyFired(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String key = TrialGraph446Store.key(level, pos);
        return !key.isEmpty() && DESTROY_FIRED.remove(key);
    }

    private static void rememberParticipants(String key, Collection<UUID> participants) {
        if (key == null || key.isEmpty() || participants == null || participants.isEmpty()) return;
        LAST_PLAYERS.put(key, new ArrayList<>(participants));
        if (LAST_PLAYERS.size() > 1024) LAST_PLAYERS.clear();
    }

    private static void runChain(ServerLevel level, BlockPos pos, String key,
            NpcDialogueGraph graph, NpcDialogueGraph.Node start, ServerPlayer best) {
        BlockTalk358.Where where = new BlockTalk358.Where(level, key, pos.getX(), pos.getY(), pos.getZ());
        if (best != null) {
            TrialControlActions446.withOrigin(level, pos, () ->
                    BlockTrigger351.start(best, graph, start.id, where));
        } else {
            TrialControlActions446.withOrigin(level, pos, () ->
                    BlockTrigger351.startServerOnly(level, graph, start.id, where));
        }
    }

    private static ServerLevel resolveLevelForKey(MinecraftServer server, String key,
            ServerLevel fallback) {
        if (server != null && key != null) {
            int at = key.lastIndexOf('@');
            if (at > 0) {
                net.minecraft.resources.ResourceLocation rl =
                        net.minecraft.resources.ResourceLocation.tryParse(key.substring(0, at));
                if (rl != null) {
                    ServerLevel resolved = server.getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, rl));
                    if (resolved != null) return resolved;
                }
            }
        }
        return fallback;
    }

    public static String coords(BlockPos pos) {
        if (pos == null) return "?";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public static void fail(Throwable failure) {
        String msg = String.valueOf(failure);
        if (!WARNED.add(msg) || WARNED.size() > 16) return;
        LOG.warn("trial graph pass skipped: " + msg);
    }
}
