package dev.settledlands.npc;

import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialSpawnerBridge446;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.extensions.IEntityExtension;

/**
 * Build 453/456: persistent world storage for object-local variables (block/door/chest/trial),
 * one-time initialization markers for {@link VarDataNode453} containers, and unified
 * variable/IF runtime evaluation supporting all 14 math operations plus simultaneous blue (entity)
 * and orange (variable/numeric) inputs across both NPC and block/trial graphs.
 */
public final class VarScopeStore453 extends SavedData {

    private static final String FILE = "settledlands_var_scope453";
    private static final String TAG_SCOPES = "Scopes";
    private static final String TAG_KEY = "K";
    private static final String TAG_VARS = "V";
    private static final String TAG_INIT = "Init";

    public static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final Map<String, Map<String, Integer>> localScopes = new HashMap<>();
    private final Set<String> initializedKeys = new HashSet<>();

    /** Execution context for substituting variable placeholders inside lines, choices, triggers, and actions. */
    public static final class TextVarContext {
        public final MinecraftServer server;
        public final ServerPlayer player;
        public final GuideNpc npc;
        public final BlockTalk358.Where where;
        public final NpcDialogueGraph graph;
        public final NpcDialogueGraph.Node node;

        public TextVarContext(MinecraftServer server, ServerPlayer player, GuideNpc npc,
                BlockTalk358.Where where, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
            this.server = server;
            this.player = player;
            this.npc = npc;
            this.where = where;
            this.graph = graph;
            this.node = node;
        }
    }

    private static final ThreadLocal<Deque<TextVarContext>> TEXT_VAR_STACK = new ThreadLocal<>();

    public static void pushTextContext(MinecraftServer server, ServerPlayer player, GuideNpc npc,
            BlockTalk358.Where where, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        Deque<TextVarContext> st = TEXT_VAR_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            TEXT_VAR_STACK.set(st);
        }
        st.push(new TextVarContext(server, player, npc, where, graph, node));
    }

    public static void popTextContext() {
        Deque<TextVarContext> st = TEXT_VAR_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) TEXT_VAR_STACK.remove();
    }

    public static TextVarContext peekTextContext() {
        Deque<TextVarContext> st = TEXT_VAR_STACK.get();
        return st == null ? null : st.peek();
    }

    public interface NpcLocalVarBridge {
        CompoundTag getOrCreate(GuideNpc npc, boolean create);
    }

    /** Execution frame for {@link BlockTrigger351} walk / server-only loops (lives outside mixin pkg). */
    public static final class BlockWalkFrame {
        public final NpcDialogueGraph graph;
        public final BlockTalk358.Where where;
        public final MinecraftServer server;
        public NpcDialogueGraph.Node activeIfNode;

        public BlockWalkFrame(NpcDialogueGraph graph, BlockTalk358.Where where,
                MinecraftServer server) {
            this.graph = graph;
            this.where = where;
            this.server = server;
        }
    }

    /** Execution frame for {@link GuideDialogue#resolveExecutionTarget} (lives outside mixin pkg). */
    public static final class NpcResolveFrame {
        public final ServerPlayer player;
        public final Entity contextEntity;
        public final GuideNpc npc;
        public final NpcDialogueGraph graph;
        public NpcDialogueGraph.Node activeIfNode;
        public NpcDialogueGraph.Node activeNode;

        public NpcResolveFrame(ServerPlayer player, Entity contextEntity,
                GuideNpc npc, NpcDialogueGraph graph) {
            this.player = player;
            this.contextEntity = contextEntity;
            this.npc = npc;
            this.graph = graph;
        }
    }

    /** Execution frame for {@link GuideDialogue#executeGraphTriggers} (lives outside mixin pkg). */
    public static final class NpcTriggerFrame {
        public final ServerPlayer player;
        public final Entity contextEntity;
        public final GuideNpc npc;
        public final NpcDialogueGraph graph;
        public NpcDialogueGraph.Node currentNode;

        public NpcTriggerFrame(ServerPlayer player, Entity contextEntity,
                GuideNpc npc, NpcDialogueGraph graph) {
            this.player = player;
            this.contextEntity = contextEntity;
            this.npc = npc;
            this.graph = graph;
        }
    }

    /** Direct PersistentData lookup matching {@code GuideDialogue.localVars(GuideNpc, boolean)}. */
    public static CompoundTag getOrCreateNpcLocalTag(GuideNpc npc, boolean create) {
        if (npc == null) return null;
        CompoundTag persistent = ((IEntityExtension) (Object) npc).getPersistentData();
        CompoundTag vars = persistent.getCompound("settledlands_local_vars");
        if (create && !persistent.contains("settledlands_local_vars", 10)) {
            persistent.put("settledlands_local_vars", vars);
        }
        return vars;
    }

    private VarScopeStore453() {}

    public static VarScopeStore453 get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE);
    }

    private static SavedData.Factory<VarScopeStore453> factory() {
        return new SavedData.Factory<>(VarScopeStore453::new, VarScopeStore453::load,
                DataFixTypes.LEVEL);
    }

    private static VarScopeStore453 load(CompoundTag tag, HolderLookup.Provider registries) {
        VarScopeStore453 store = new VarScopeStore453();
        if (tag.contains(TAG_SCOPES, 9)) {
            ListTag scopes = tag.getList(TAG_SCOPES, 10);
            for (int i = 0; i < scopes.size(); i++) {
                CompoundTag entry = scopes.getCompound(i);
                String k = entry.getString(TAG_KEY);
                if (k.isEmpty()) continue;
                CompoundTag vars = entry.getCompound(TAG_VARS);
                Map<String, Integer> map = new HashMap<>();
                for (String varName : vars.getAllKeys()) {
                    map.put(varName, vars.getInt(varName));
                }
                if (!map.isEmpty()) {
                    store.localScopes.put(k, map);
                }
            }
        }
        if (tag.contains(TAG_INIT, 10)) {
            CompoundTag init = tag.getCompound(TAG_INIT);
            for (String s : init.getAllKeys()) {
                if (s != null && !s.isEmpty() && init.getBoolean(s)) {
                    store.initializedKeys.add(s);
                }
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag scopes = new ListTag();
        for (Map.Entry<String, Map<String, Integer>> entry : localScopes.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            CompoundTag one = new CompoundTag();
            one.putString(TAG_KEY, entry.getKey());
            CompoundTag vars = new CompoundTag();
            for (Map.Entry<String, Integer> v : entry.getValue().entrySet()) {
                vars.putInt(v.getKey(), v.getValue());
            }
            one.put(TAG_VARS, vars);
            scopes.add(one);
        }
        tag.put(TAG_SCOPES, scopes);

        CompoundTag init = new CompoundTag();
        for (String key : initializedKeys) {
            if (key != null && !key.isEmpty()) {
                init.putBoolean(key, true);
            }
        }
        tag.put(TAG_INIT, init);
        return tag;
    }

    public int getScopedLocal(String scopeKey, String varName) {
        if (scopeKey == null || scopeKey.isEmpty() || varName == null || varName.isBlank()) return 0;
        Map<String, Integer> map = localScopes.get(scopeKey);
        if (map == null) return 0;
        Integer val = map.get(cleanVarName(varName));
        return val == null ? 0 : val;
    }

    public boolean hasScopedLocal(String scopeKey, String varName) {
        if (scopeKey == null || scopeKey.isEmpty() || varName == null || varName.isBlank()) return false;
        Map<String, Integer> map = localScopes.get(scopeKey);
        return map != null && map.containsKey(cleanVarName(varName));
    }

    public void setScopedLocal(String scopeKey, String varName, int value) {
        if (scopeKey == null || scopeKey.isEmpty() || varName == null || varName.isBlank()) return;
        String clean = cleanVarName(varName);
        if (clean.isEmpty()) return;
        localScopes.computeIfAbsent(scopeKey, k -> new HashMap<>()).put(clean, value);
        setDirty();
    }

    public boolean removeScopedLocal(String scopeKey, String varName) {
        if (scopeKey == null || scopeKey.isEmpty() || varName == null || varName.isBlank()) return false;
        Map<String, Integer> map = localScopes.get(scopeKey);
        if (map == null) return false;
        String clean = cleanVarName(varName);
        if (map.remove(clean) != null) {
            if (map.isEmpty()) localScopes.remove(scopeKey);
            setDirty();
            return true;
        }
        return false;
    }

    public Map<String, Map<String, Integer>> snapshotLocalScopes() {
        Map<String, Map<String, Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : localScopes.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                copy.put(e.getKey(), new HashMap<>(e.getValue()));
            }
        }
        return copy;
    }

    public void removeInitKeysFor(String varName, int scope, String scopeTag) {
        String clean = cleanVarName(varName);
        if (clean.isEmpty()) return;
        boolean changed = false;
        if (scope == VarDataNode453.SCOPE_SHARED) {
            changed = initializedKeys.remove("s:" + clean);
        } else if (scope == VarDataNode453.SCOPE_LOCAL) {
            if (scopeTag != null && !scopeTag.isEmpty()) {
                changed = initializedKeys.remove("l:" + scopeTag + ":" + clean);
            }
        } else {
            String suffix = ":" + clean;
            changed = initializedKeys.removeIf(k -> k.startsWith("p:") && k.endsWith(suffix));
        }
        if (changed) setDirty();
    }

    public boolean isInitialized(String initKey) {
        return initKey != null && initializedKeys.contains(initKey);
    }

    public void markInitialized(String initKey) {
        if (initKey != null && !initKey.isEmpty() && initializedKeys.add(initKey)) {
            setDirty();
        }
    }

    public static String cleanVarName(String name) {
        return name == null ? "" : NpcPoints.cleanName(name);
    }

    private static String initKey(String name, boolean shared, boolean local, UUID playerId, String scopeTag) {
        String clean = cleanVarName(name);
        if (clean.isEmpty()) return "";
        if (local) return "l:" + (scopeTag == null ? "" : scopeTag) + ":" + clean;
        if (shared) return "s:" + clean;
        if (playerId == null) return "";
        return "p:" + playerId + ":" + clean;
    }

    // ---- Blue entity resolution ----

    public static Entity resolveNpcBlueEntity(GuideNpc npc, NpcDialogueGraph graph, NpcDialogueGraph.Node consumer) {
        if (npc == null || graph == null || consumer == null) return null;
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId < 0) {
            // Also check if a Pursuit node feeds this consumer directly via "|@ids"
            if (graph.nodes != null) {
                for (NpcDialogueGraph.Node cand : graph.nodes.values()) {
                    if (cand != null && NpcPursuit269.isPursuitAction(cand.action)
                            && pursuitHasConsumer(cand.action, consumer.id)) {
                        Entity pe = resolvePursuitEntity(npc, cand);
                        if (pe != null) return pe;
                    }
                }
            }
            return null;
        }
        NpcDialogueGraph.Node src = graph.get(srcId);
        if (src == null || VarDataNode453.isVarDataNode(src)) return null;
        if (NpcValueSource290.isSource(npc, srcId)) {
            return NpcValueSource290.resolve(npc, srcId);
        }
        if (NpcPursuit269.isPursuitAction(src.action)) {
            return resolvePursuitEntity(npc, src);
        }
        return null;
    }

    /**
     * Resolves a {@link VarDataNode453} wired into {@code consumer}'s blue input (either directly
     * via {@code entityInput453}/{@code varInputNode} or forwarded through a {@code Pursuit} node).
     */
    public static NpcDialogueGraph.Node resolveBlueVarNode(NpcDialogueGraph graph,
            NpcDialogueGraph.Node consumer) {
        if (graph == null || consumer == null) return null;
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId >= 0) {
            NpcDialogueGraph.Node src = graph.get(srcId);
            if (VarDataNode453.isVarDataNode(src)) return src;
            if (src != null && NpcPursuit269.isPursuitAction(src.action)) {
                int pSrc = pursuitSourceId(src.action);
                if (pSrc >= 0) {
                    NpcDialogueGraph.Node ps = graph.get(pSrc);
                    if (VarDataNode453.isVarDataNode(ps)) return ps;
                }
            }
            return null;
        }
        if (graph.nodes != null) {
            for (NpcDialogueGraph.Node cand : graph.nodes.values()) {
                if (cand != null && NpcPursuit269.isPursuitAction(cand.action)
                        && pursuitHasConsumer(cand.action, consumer.id)) {
                    int pSrc = pursuitSourceId(cand.action);
                    if (pSrc >= 0) {
                        NpcDialogueGraph.Node ps = graph.get(pSrc);
                        if (VarDataNode453.isVarDataNode(ps)) return ps;
                    }
                }
            }
        }
        return null;
    }

    private static int pursuitSourceId(String action) {
        if (action == null) return -1;
        int hash = action.lastIndexOf("|#");
        if (hash < 0) return -1;
        try {
            String tail = action.substring(hash + 2);
            int bar = tail.indexOf('|');
            return Integer.parseInt((bar >= 0 ? tail.substring(0, bar) : tail).trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static Entity resolvePursuitEntity(GuideNpc npc, NpcDialogueGraph.Node src) {
        if (npc == null || src == null) return null;
        String act = src.action;
        if (act != null) {
            int hash = act.lastIndexOf("|#");
            if (hash >= 0) {
                try {
                    String tail = act.substring(hash + 2);
                    int bar = tail.indexOf('|');
                    int pSrc = Integer.parseInt((bar >= 0 ? tail.substring(0, bar) : tail).trim());
                    if (pSrc >= 0 && NpcValueSource290.isSource(npc, pSrc)) {
                        Entity ent = NpcValueSource290.resolve(npc, pSrc);
                        if (ent != null) return ent;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return ((Mob) (Object) npc).getTarget();
    }

    private static boolean pursuitHasConsumer(String action, int nodeId) {
        if (action == null) return false;
        int at = action.indexOf("|@");
        if (at < 0) return false;
        int end = action.indexOf('|', at + 2);
        String list = end < 0 ? action.substring(at + 2) : action.substring(at + 2, end);
        for (String part : list.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == nodeId) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    public static ServerPlayer resolveBlockBluePlayer(ServerLevel level, ServerPlayer actor,
            NpcDialogueGraph graph, NpcDialogueGraph.Node consumer, BlockTalk358.Where where) {
        if (graph == null || consumer == null) return actor;
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId < 0) return actor;
        NpcDialogueGraph.Node src = graph.get(srcId);
        if (src == null) return null;
        if (VarDataNode453.isVarDataNode(src)) return actor;
        if (level == null && where != null) level = where.level();
        if (TrialGraph446.isTrialNode(src) && level != null && where != null) {
            BlockPos pos = new BlockPos(where.x(), where.y(), where.z());
            BlockEntity be = level.getBlockEntity(pos);
            List<UUID> participants = TrialSpawnerBridge446.readParticipants(be);
            return TrialSpawnerBridge446.resolveBestPlayer(level, pos, participants, actor);
        }
        if (src.type == NpcDialogueGraph.CHECK && level != null && where != null) {
            BlockPos pos = new BlockPos(where.x(), where.y(), where.z());
            double r = Math.max(1.0D, src.checkRadius);
            if (RadiusZone456.isSquare(src)) {
                double cx = pos.getX() + 0.5D;
                double cy = pos.getY() + 0.5D;
                double cz = pos.getZ() + 0.5D;
                ServerPlayer best = null;
                double bestD2 = Double.MAX_VALUE;
                for (ServerPlayer sp : level.players()) {
                    if (sp == null || !sp.isAlive() || sp.isSpectator()) continue;
                    if (!RadiusZone456.isInsideBox(cx, cy, cz, sp.getX(), sp.getY(), sp.getZ(), r)) {
                        continue;
                    }
                    double d2 = sp.distanceToSqr(cx, cy, cz);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = sp;
                    }
                }
                return best;
            }
            return TrialSpawnerBridge446.nearestPlayer(level, pos, r, false);
        }
        if (src.type == 40 || src.type == 50 || src.type == NpcDialogueGraph.DAMAGE) {
            return actor;
        }
        return actor;
    }

    // ---- Low-level read / write with variable initial value support ----

    private static int readRaw(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            String name,
            boolean shared,
            boolean local,
            boolean hasDefaultInit,
            int defaultInit) {
        String clean = cleanVarName(name);
        if (clean.isEmpty()) return hasDefaultInit ? defaultInit : 0;

        VarScopeStore453 store = get(server);
        if (local) {
            if (npc != null) {
                CompoundTag tag = npcLocals != null
                        ? npcLocals.getOrCreate(npc, false)
                        : getOrCreateNpcLocalTag(npc, false);
                if (tag != null && tag.contains(clean)) {
                    return tag.getInt(clean);
                }
                String iKey = initKey(clean, false, true, null, npc.getUUID().toString());
                if (hasDefaultInit && (store == null || !store.isInitialized(iKey))) {
                    return defaultInit;
                }
                return 0;
            }
            if (where != null && store != null) {
                if (store.hasScopedLocal(where.key(), clean)) {
                    return store.getScopedLocal(where.key(), clean);
                }
                String iKey = initKey(clean, false, true, null, where.key());
                if (hasDefaultInit && !store.isInitialized(iKey)) {
                    return defaultInit;
                }
            }
            return hasDefaultInit ? defaultInit : 0;
        }

        if (server == null) return hasDefaultInit ? defaultInit : 0;
        NpcPoints pts = NpcPoints.get(server);
        UUID pid = shared ? ZERO_UUID : (player == null ? null : player.getUUID());
        if (!shared && pid == null) {
            return hasDefaultInit ? defaultInit : 0;
        }
        int cur = pts.get(pid, clean, shared);
        if (cur != 0) return cur;
        if (hasDefaultInit) {
            String iKey = initKey(clean, shared, false, pid, null);
            if (store == null || !store.isInitialized(iKey)) {
                return defaultInit;
            }
        }
        return 0;
    }

    private static void writeRaw(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            String name,
            boolean shared,
            boolean local,
            int nextValue) {
        String clean = cleanVarName(name);
        if (clean.isEmpty()) return;
        VarScopeStore453 store = get(server);

        if (local) {
            if (npc != null) {
                CompoundTag tag = npcLocals != null
                        ? npcLocals.getOrCreate(npc, true)
                        : getOrCreateNpcLocalTag(npc, true);
                if (tag != null) {
                    tag.putInt(clean, nextValue);
                }
                if (store != null) {
                    store.markInitialized(initKey(clean, false, true, null, npc.getUUID().toString()));
                }
                return;
            }
            if (where != null && store != null) {
                store.setScopedLocal(where.key(), clean, nextValue);
                store.markInitialized(initKey(clean, false, true, null, where.key()));
            }
            return;
        }

        if (server == null) return;
        UUID pid = shared ? ZERO_UUID : (player == null ? null : player.getUUID());
        if (!shared && pid == null) return;
        NpcPoints pts = NpcPoints.get(server);
        pts.set(pid, clean, shared, nextValue);
        if (store != null) {
            store.markInitialized(initKey(clean, shared, false, pid, null));
        }
    }

    // ---- Execution & Evaluation for VarDataNode453, VARIABLE (4) and IF (3) ----

    /** Executes {@link VarDataNode453} inside an NPC graph: writes either its orange input value or its constant value into its variable. */
    public static void executeVarDataNodeForNpc(
            ServerPlayer player,
            Entity contextEntity,
            GuideNpc npc,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            NpcLocalVarBridge npcLocals) {
        if (!VarDataNode453.isVarDataNode(node)) return;
        MinecraftServer server = player != null ? player.getServer()
                : (npc != null && npc.level() != null ? npc.level().getServer()
                : (contextEntity != null && contextEntity.level() != null
                        ? contextEntity.level().getServer() : null));
        if (server == null) return;

        ServerPlayer targetPlayer = player;
        GuideNpc targetNpc = npc;
        int blueId = VarDataNode453.entityInput(graph, node);
        boolean blueIsVar = resolveBlueVarNode(graph, node) != null;
        if (!blueIsVar && blueId >= 0) {
            Entity blueEnt = resolveNpcBlueEntity(npc, graph, node);
            if (blueEnt instanceof ServerPlayer sp) {
                targetPlayer = sp;
            } else if (blueEnt instanceof GuideNpc gn) {
                targetNpc = gn;
                targetPlayer = null;
            } else {
                targetPlayer = null;
            }
        } else if (targetPlayer == null && contextEntity instanceof ServerPlayer sp) {
            targetPlayer = sp;
        }

        executeVarDataNodeCommon(server, targetPlayer, targetNpc, npcLocals, null, graph, node);
    }

    /** Executes {@link VarDataNode453} inside a block/door/chest/trial graph. */
    public static void executeVarDataNodeForBlock(
            ServerPlayer actor,
            MinecraftServer server,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            BlockTalk358.Where where) {
        if (!VarDataNode453.isVarDataNode(node)) return;
        ServerLevel level = where != null ? where.level()
                : (actor != null ? actor.serverLevel() : null);
        if (server == null && level != null) server = level.getServer();
        if (server == null && actor != null) server = actor.getServer();
        if (server == null) return;

        ServerPlayer targetPlayer = resolveBlockBluePlayer(level, actor, graph, node, where);
        executeVarDataNodeCommon(server, targetPlayer, null, null, where, graph, node);
    }

    private static void executeVarDataNodeCommon(
            MinecraftServer server,
            ServerPlayer targetPlayer,
            GuideNpc targetNpc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node) {
        String effName = effectiveVarName(node);
        if (node == null || effName.isBlank()) return;
        NpcDialogueGraph.Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        int valueToWrite;
        if (orangeSrc != null) {
            valueToWrite = readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals,
                    where, graph, orangeSrc, 0);
        } else {
            NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
            valueToWrite = blueVar != null
                    ? readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals,
                            where, graph, blueVar, 0)
                    : node.varValue;
        }
        writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                effName, node.pointsShared, node.varLocal, valueToWrite);
    }

    /** Executes {@link NpcDialogueGraph#VARIABLE} inside an NPC graph. */
    public static void executeVariableForNpc(
            ServerPlayer player,
            Entity contextEntity,
            GuideNpc npc,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            NpcLocalVarBridge npcLocals) {
        if (node == null || node.type != NpcDialogueGraph.VARIABLE) return;
        MinecraftServer server = player != null ? player.getServer()
                : (npc != null && npc.level() != null ? npc.level().getServer() : null);
        if (server == null) return;

        // Blue input selects whose player/NPC variable is read/modified (when wired to entity source).
        ServerPlayer targetPlayer = player;
        GuideNpc targetNpc = npc;
        int blueId = VarDataNode453.entityInput(graph, node);
        boolean blueIsVar = resolveBlueVarNode(graph, node) != null;
        if (!blueIsVar && blueId >= 0) {
            Entity blueEnt = resolveNpcBlueEntity(npc, graph, node);
            if (blueEnt instanceof ServerPlayer sp) {
                targetPlayer = sp;
            } else if (blueEnt instanceof GuideNpc gn) {
                targetNpc = gn;
                targetPlayer = null;
            } else {
                targetPlayer = null;
            }
        } else if (targetPlayer == null && contextEntity instanceof ServerPlayer sp) {
            targetPlayer = sp;
        }

        executeVariableCommon(server, targetPlayer, targetNpc, npcLocals, null, graph, node);
    }

    /** Executes {@link NpcDialogueGraph#VARIABLE} inside a block/door/chest/trial graph. */
    public static void executeVariableForBlock(
            ServerPlayer actor,
            MinecraftServer server,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            BlockTalk358.Where where) {
        if (node == null || node.type != NpcDialogueGraph.VARIABLE) return;
        ServerLevel level = where != null ? where.level()
                : (actor != null ? actor.serverLevel() : null);
        if (server == null && level != null) server = level.getServer();
        if (server == null && actor != null) server = actor.getServer();
        if (server == null) return;

        ServerPlayer targetPlayer = resolveBlockBluePlayer(level, actor, graph, node, where);
        executeVariableCommon(server, targetPlayer, null, null, where, graph, node);
    }

    private static void executeVariableCommon(
            MinecraftServer server,
            ServerPlayer targetPlayer,
            GuideNpc targetNpc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node) {
        NpcDialogueGraph.Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
        boolean hasOwnName = node.pointsName != null && !node.pointsName.isBlank();

        if (orangeSrc != null && !hasOwnName) {
            // Pattern A (Variable -> Math Expression without own name): modify orangeSrc directly,
            // then forward the updated value to any VarData nodes wired to Math Expression's output.
            String tName = effectiveVarName(orangeSrc);
            if (tName.isBlank()) return;
            ServerPlayer tPlayer = resolveNodePlayer(server, targetPlayer, targetNpc, where, graph, orangeSrc);
            GuideNpc tNpc = resolveNodeNpc(targetNpc, graph, orangeSrc);
            int operand = blueVar != null
                    ? readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, where, graph, blueVar, 0)
                    : node.varValue;
            boolean hasInit = VarDataNode453.isVarDataNode(orangeSrc) && orangeSrc.varValue != 0;
            int cur = readRaw(server, tPlayer, tNpc, npcLocals, where,
                    tName, orangeSrc.pointsShared, orangeSrc.varLocal, hasInit, orangeSrc.varValue);
            int next = VarDataNode453.applyMathOp(cur, node.varOp, operand);
            writeRaw(server, tPlayer, tNpc, npcLocals, where,
                    tName, orangeSrc.pointsShared, orangeSrc.varLocal, next);
            propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, node.id, next);
            return;
        }

        if (orangeSrc != null) {
            // Pattern B (Operand from orange wire into named Math Expression): modify own variable,
            // then forward the result to any VarData nodes wired to Math Expression's output.
            int operand = readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, where, graph, orangeSrc, 0);
            int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, where,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
            int next = VarDataNode453.applyMathOp(cur, node.varOp, operand);
            writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                    node.pointsName, node.pointsShared, node.varLocal, next);
            propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, node.id, next);
            return;
        }

        if (blueVar != null && hasOwnName) {
            // Pattern B2 (Variable wired into Math Expression's blue input -> supplies dynamic operand)
            int operand = readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, where, graph, blueVar, 0);
            int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, where,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
            int next = VarDataNode453.applyMathOp(cur, node.varOp, operand);
            writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                    node.pointsName, node.pointsShared, node.varLocal, next);
            propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, node.id, next);
            return;
        }

        if (blueVar != null && !hasOwnName) {
            // Pattern A2 (Variable wired into Math Expression's blue input without own name -> modifies that Variable)
            String tName = effectiveVarName(blueVar);
            if (!tName.isBlank()) {
                boolean hasInit = blueVar.varValue != 0;
                int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, where,
                        tName, blueVar.pointsShared, blueVar.varLocal, hasInit, blueVar.varValue);
                int next = VarDataNode453.applyMathOp(cur, node.varOp, node.varValue);
                writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                        tName, blueVar.pointsShared, blueVar.varLocal, next);
                propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, node.id, next);
                return;
            }
        }

        if (!hasOwnName) {
            // Pattern C (Math Expression without own name -> one or more Variable nodes on output):
            // modify each connected Variable node using its own stored value, scope, and blue pin,
            // then cascade to any downstream Variable chain.
            List<NpcDialogueGraph.Node> targets = VarDataNode453.findLinkedOrangeTargets(graph, node.id);
            if (targets.isEmpty()) return;
            for (NpcDialogueGraph.Node outTarget : targets) {
                String tName = effectiveVarName(outTarget);
                if (tName.isBlank()) continue;
                ServerPlayer tPlayer = resolveNodePlayer(server, targetPlayer, targetNpc, where, graph, outTarget);
                GuideNpc tNpc = resolveNodeNpc(targetNpc, graph, outTarget);
                int cur = readRaw(server, tPlayer, tNpc, npcLocals, where,
                        tName, outTarget.pointsShared, outTarget.varLocal,
                        outTarget.varValue != 0, outTarget.varValue);
                int next = VarDataNode453.applyMathOp(cur, node.varOp, node.varValue);
                writeRaw(server, tPlayer, tNpc, npcLocals, where,
                        tName, outTarget.pointsShared, outTarget.varLocal, next);
                propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, outTarget.id, next);
            }
            return;
        }

        // Pattern D (Named Math Expression without orange input): modify own variable and forward
        // the computed value to any Variable nodes wired to its orange output.
        int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, where,
                node.pointsName, node.pointsShared, node.varLocal, false, 0);
        int next = VarDataNode453.applyMathOp(cur, node.varOp, node.varValue);
        writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                node.pointsName, node.pointsShared, node.varLocal, next);
        propagateToLinkedVarNodes(server, targetPlayer, targetNpc, npcLocals, where, graph, node.id, next);
    }

    /** Returns {@code node.pointsName}, or a stable internal storage key for unnamed Variable nodes. */
    private static String effectiveVarName(NpcDialogueGraph.Node node) {
        if (node == null) return "";
        if (node.pointsName != null && !node.pointsName.isBlank()) return node.pointsName;
        if (VarDataNode453.isVarDataNode(node)) return "__slvar_" + node.id;
        return "";
    }

    private static ServerPlayer resolveNodePlayer(
            MinecraftServer server,
            ServerPlayer fallbackPlayer,
            GuideNpc hostNpc,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node dataNode) {
        if (graph == null || dataNode == null) return fallbackPlayer;
        int blueId = VarDataNode453.entityInput(graph, dataNode);
        if (blueId < 0) return fallbackPlayer;
        NpcDialogueGraph.Node blueSrc = graph.get(blueId);
        if (blueSrc == null || VarDataNode453.isVarDataNode(blueSrc)) return fallbackPlayer;
        if (hostNpc != null) {
            Entity e = resolveNpcBlueEntity(hostNpc, graph, dataNode);
            return e instanceof ServerPlayer sp ? sp : null;
        }
        ServerLevel lvl = where != null ? where.level()
                : (fallbackPlayer != null ? fallbackPlayer.serverLevel()
                        : (server != null ? server.overworld() : null));
        return resolveBlockBluePlayer(lvl, fallbackPlayer, graph, dataNode, where);
    }

    private static GuideNpc resolveNodeNpc(
            GuideNpc fallbackNpc,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node dataNode) {
        if (fallbackNpc == null || graph == null || dataNode == null) return fallbackNpc;
        int blueId = VarDataNode453.entityInput(graph, dataNode);
        if (blueId < 0) return fallbackNpc;
        NpcDialogueGraph.Node blueSrc = graph.get(blueId);
        if (blueSrc == null || VarDataNode453.isVarDataNode(blueSrc)) return fallbackNpc;
        Entity e = resolveNpcBlueEntity(fallbackNpc, graph, dataNode);
        return e instanceof GuideNpc gn ? gn : fallbackNpc;
    }

    private static boolean hasStoredValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            String name,
            boolean shared,
            boolean local) {
        String clean = cleanVarName(name);
        if (clean.isEmpty()) return false;
        VarScopeStore453 store = get(server);
        if (local) {
            if (npc != null) {
                CompoundTag tag = npcLocals != null
                        ? npcLocals.getOrCreate(npc, false)
                        : getOrCreateNpcLocalTag(npc, false);
                return tag != null && tag.contains(clean);
            }
            if (where != null && store != null) {
                return store.hasScopedLocal(where.key(), clean)
                        || store.isInitialized(initKey(clean, false, true, null, where.key()));
            }
            return false;
        }
        UUID pid = shared ? ZERO_UUID : (player == null ? null : player.getUUID());
        if (!shared && pid == null) return false;
        if (store != null && store.isInitialized(initKey(clean, shared, false, pid, null))) {
            return true;
        }
        return server != null && NpcPoints.get(server).get(pid, clean, shared) != 0;
    }

    /** Writes {@code value} to every {@link VarDataNode453} wired downstream of {@code sourceId} (BFS, cycle-safe). */
    private static void propagateToLinkedVarNodes(
            MinecraftServer server,
            ServerPlayer fallbackPlayer,
            GuideNpc hostNpc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            int sourceId,
            int value) {
        if (graph == null || graph.nodes == null || sourceId < 0) return;
        Set<Integer> visited = new HashSet<>();
        visited.add(sourceId);
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(sourceId);
        while (!queue.isEmpty() && visited.size() < 64) {
            int curId = queue.poll();
            List<NpcDialogueGraph.Node> targets = VarDataNode453.findLinkedOrangeTargets(graph, curId);
            for (NpcDialogueGraph.Node tgt : targets) {
                if (!visited.add(tgt.id)) continue;
                String tName = effectiveVarName(tgt);
                if (!tName.isBlank()) {
                    ServerPlayer tPlayer = resolveNodePlayer(server, fallbackPlayer, hostNpc, where, graph, tgt);
                    GuideNpc tNpc = resolveNodeNpc(hostNpc, graph, tgt);
                    writeRaw(server, tPlayer, tNpc, npcLocals, where,
                            tName, tgt.pointsShared, tgt.varLocal, value);
                }
                queue.add(tgt.id);
            }
        }
    }

    /** Reads the numeric value supplied by a Variable node across NPC/block graphs. */
    public static int readVarNodeValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node varNode) {
        return readOrangeNodeValue(server, player, npc, null, where, graph, varNode, 0);
    }

    /** Reads the numeric value supplied by an orange source node (Variable or Math Expression). */
    public static int readOrangeNodeValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph.Node src) {
        return readOrangeNodeValue(server, player, npc, npcLocals, where, null, src, 0);
    }

    public static int readOrangeNodeValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node src,
            int depth) {
        if (src == null) return 0;
        ServerPlayer effPlayer = resolveNodePlayer(server, player, npc, where, graph, src);
        GuideNpc effNpc = resolveNodeNpc(npc, graph, src);

        if (VarDataNode453.isVarDataNode(src)) {
            boolean hasExplicitName = src.pointsName != null && !src.pointsName.isBlank();
            String effName = effectiveVarName(src);
            if (!effName.isBlank()) {
                boolean stored = hasStoredValue(server, effPlayer, effNpc, npcLocals, where,
                        effName, src.pointsShared, src.varLocal);
                if (stored) {
                    return readRaw(server, effPlayer, effNpc, npcLocals, where,
                            effName, src.pointsShared, src.varLocal, false, 0);
                }
                if (depth < 16 && graph != null) {
                    NpcDialogueGraph.Node chained = VarDataNode453.resolveOrangeSource(graph, src);
                    if (chained != null && chained != src) {
                        return readOrangeNodeValue(server, player, npc, npcLocals, where, graph, chained, depth + 1);
                    }
                    NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, src);
                    if (blueVar != null && blueVar != src) {
                        return readOrangeNodeValue(server, player, npc, npcLocals, where, graph, blueVar, depth + 1);
                    }
                }
                if (hasExplicitName) {
                    return readRaw(server, effPlayer, effNpc, npcLocals, where,
                            effName, src.pointsShared, src.varLocal, src.varValue != 0, src.varValue);
                }
            }
            return src.varValue;
        }

        if (src.pointsName != null && !src.pointsName.isBlank()) {
            return readRaw(server, effPlayer, effNpc, npcLocals, where,
                    src.pointsName, src.pointsShared, src.varLocal, false, 0);
        }
        if (depth < 16 && graph != null) {
            NpcDialogueGraph.Node chained = VarDataNode453.resolveOrangeSource(graph, src);
            if (chained != null && chained != src) {
                return readOrangeNodeValue(server, player, npc, npcLocals, where, graph, chained, depth + 1);
            }
        }
        return src.varValue;
    }

    /** Reads left-hand value for {@link NpcDialogueGraph#IF} in an NPC graph. */
    public static int readIfLeftValueForNpc(
            ServerPlayer player,
            Entity contextEntity,
            GuideNpc npc,
            int fallback,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            NpcLocalVarBridge npcLocals) {
        if (node == null) return fallback;
        MinecraftServer server = player != null ? player.getServer()
                : (npc != null && npc.level() != null ? npc.level().getServer() : null);

        ServerPlayer targetPlayer = player;
        GuideNpc targetNpc = npc;
        int blueId = VarDataNode453.entityInput(graph, node);
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
        if (blueVar == null && blueId >= 0) {
            Entity blueEnt = resolveNpcBlueEntity(npc, graph, node);
            if (blueEnt instanceof ServerPlayer sp) {
                targetPlayer = sp;
            } else if (blueEnt instanceof GuideNpc gn) {
                targetNpc = gn;
                targetPlayer = null;
            } else {
                targetPlayer = null;
            }
        } else if (targetPlayer == null && contextEntity instanceof ServerPlayer sp) {
            targetPlayer = sp;
        }

        NpcDialogueGraph.Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        if (orangeSrc != null) {
            return readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, null, graph, orangeSrc, 0);
        }
        if (node.pointsName != null && !node.pointsName.isBlank()) {
            int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, null,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
            if (cur == 0 && npc != null && blueId < 0 && !node.pointsShared && !node.varLocal) {
                int vp = NpcValueSource290.variablePresence(npc, cleanVarName(node.pointsName));
                if (vp >= 0) return vp;
            }
            return cur;
        }
        if (blueVar != null) {
            return readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, null, graph, blueVar, 0);
        }
        return fallback;
    }

    /**
     * Reads right-hand comparison operand for {@link NpcDialogueGraph#IF} in an NPC graph:
     * when a {@link VarDataNode453} feeds the blue pin alongside an explicit variable or orange
     * input, that blue variable supplies a dynamic right-hand value instead of static {@code varCompare}.
     */
    public static int readIfRightValueForNpc(
            ServerPlayer player,
            Entity contextEntity,
            GuideNpc npc,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node) {
        if (node == null) return 0;
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
        if (blueVar == null) return node.varCompare;
        boolean hasLeftSource = VarDataNode453.resolveOrangeSource(graph, node) != null
                || (node.pointsName != null && !node.pointsName.isBlank());
        if (!hasLeftSource) return node.varCompare;
        MinecraftServer server = player != null ? player.getServer()
                : (npc != null && npc.level() != null ? npc.level().getServer()
                : (contextEntity != null && contextEntity.level() != null
                        ? contextEntity.level().getServer() : null));
        ServerPlayer sp = player != null ? player
                : (contextEntity instanceof ServerPlayer p ? p : null);
        return readOrangeNodeValue(server, sp, npc, null, null, graph, blueVar, 0);
    }

    /** Reads left-hand value for {@link NpcDialogueGraph#IF} in a block/door/chest/trial graph. */
    public static int readIfLeftValueForBlock(
            ServerPlayer actor,
            MinecraftServer server,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            BlockTalk358.Where where) {
        if (node == null) return 0;
        ServerLevel level = where != null ? where.level()
                : (actor != null ? actor.serverLevel() : null);
        if (server == null && level != null) server = level.getServer();
        if (server == null && actor != null) server = actor.getServer();

        ServerPlayer targetPlayer = resolveBlockBluePlayer(level, actor, graph, node, where);
        NpcDialogueGraph.Node orangeSrc = VarDataNode453.resolveOrangeSource(graph, node);
        if (orangeSrc != null) {
            return readOrangeNodeValue(server, targetPlayer, null, null, where, graph, orangeSrc, 0);
        }
        if (node.pointsName != null && !node.pointsName.isBlank()) {
            return readRaw(server, targetPlayer, null, null, where,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
        }
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
        if (blueVar != null) {
            return readOrangeNodeValue(server, targetPlayer, null, null, where, graph, blueVar, 0);
        }
        return 0;
    }

    /** Dynamic right-hand comparison operand for block/door/chest/trial {@link NpcDialogueGraph#IF}. */
    public static int readIfRightValueForBlock(
            ServerPlayer actor,
            MinecraftServer server,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            BlockTalk358.Where where) {
        if (node == null) return 0;
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, node);
        if (blueVar == null) return node.varCompare;
        boolean hasLeftSource = VarDataNode453.resolveOrangeSource(graph, node) != null
                || (node.pointsName != null && !node.pointsName.isBlank());
        if (!hasLeftSource) return node.varCompare;
        ServerLevel level = where != null ? where.level()
                : (actor != null ? actor.serverLevel() : null);
        if (server == null && level != null) server = level.getServer();
        if (server == null && actor != null) server = actor.getServer();
        return readOrangeNodeValue(server, actor, null, null, where, graph, blueVar, 0);
    }

    // ---- Placeholder substitution for LINE / BUTTON / TRIGGER / Teleport / Pursuit ----

    private static boolean isReservedPlaceholder(String token) {
        return switch (token) {
            case "player", "npc", "npc_id", "npc_uuid", "uuid", "relation",
                    "target", "target_kind", "target_uuid",
                    "pursuit", "pursuit_kind", "pursuit_uuid" -> true;
            default -> false;
        };
    }

    /**
     * Resolves variable tokens inside dialogue lines, choice captions, trigger commands, teleport
     * targets, and pursuit commands:
     * <ul>
     *   <li>If {@code consumerNode} has a {@link VarDataNode453} wired to its blue input: replaces
     *       {@code %var%}, {@code %value%}, {@code %target_value%}, {@code %var_name%}, and
     *       {@code %target%} / {@code %target_kind%} / {@code %target_uuid%}.</li>
     *   <li>Also replaces {@code %<varName>%} tokens for any known local/personal/shared variable
     *       visible in the current NPC/object/player context.</li>
     * </ul>
     */
    public static String substituteVarTokens(
            String text,
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node consumerNode) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text;
        if (server == null) {
            if (player != null) server = player.getServer();
            else if (npc != null && npc.level() != null) server = npc.level().getServer();
            else if (where != null && where.level() != null) server = where.level().getServer();
        }
        String out = text;
        NpcDialogueGraph.Node blueVar = resolveBlueVarNode(graph, consumerNode);
        int blueVal = 0;
        String blueClean = "";
        String blueDisplayName = "";
        if (blueVar != null) {
            blueVal = readOrangeNodeValue(server, player, npc, null, where, graph, blueVar, 0);
            blueClean = cleanVarName(blueVar.pointsName);
            blueDisplayName = (blueVar.pointsName != null && !blueVar.pointsName.isBlank())
                    ? blueVar.pointsName : ("#" + blueVar.id);
        }

        if (out.indexOf('%') < 0) return out;
        StringBuilder sb = null;
        int len = out.length();
        int i = 0;
        while (i < len) {
            char c = out.charAt(i);
            if (c == '%') {
                int close = out.indexOf('%', i + 1);
                if (close > i + 1 && close - i - 1 <= 32) {
                    String rawTok = out.substring(i + 1, close);
                    String lowerTok = rawTok.toLowerCase(java.util.Locale.ROOT);
                    String replacement = null;
                    if (blueVar != null) {
                        if ("var".equals(lowerTok) || "value".equals(lowerTok)
                                || "target_value".equals(lowerTok) || "target".equals(lowerTok)) {
                            replacement = String.valueOf(blueVal);
                        } else if ("var_name".equals(lowerTok)) {
                            replacement = blueDisplayName;
                        } else if ("target_kind".equals(lowerTok)) {
                            replacement = "variable";
                        } else if ("target_uuid".equals(lowerTok)) {
                            replacement = "var:" + blueVar.id;
                        } else if (!blueClean.isEmpty() && cleanVarName(rawTok).equalsIgnoreCase(blueClean)) {
                            replacement = String.valueOf(blueVal);
                        }
                    }
                    if (replacement == null && isSimpleVarToken(rawTok) && !isReservedPlaceholder(lowerTok)) {
                        Integer resolved = lookupNamedVarValue(server, player, npc, where, graph, rawTok);
                        if (resolved != null) {
                            replacement = String.valueOf(resolved.intValue());
                        }
                    }
                    if (replacement != null) {
                        if (sb == null) {
                            sb = new StringBuilder(len + 16);
                            sb.append(out, 0, i);
                        }
                        sb.append(replacement);
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (sb != null) sb.append(c);
            i++;
        }
        return sb == null ? out : sb.toString();
    }

    /** Convenience substitution using the active {@link TextVarContext} if one is on the thread stack. */
    public static String substituteActiveTextVars(String text, ServerPlayer player, GuideNpc npc) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text;
        TextVarContext ctx = peekTextContext();
        MinecraftServer srv = ctx != null ? ctx.server
                : (player != null ? player.getServer()
                : (npc != null && npc.level() != null ? npc.level().getServer() : null));
        ServerPlayer effPlayer = player != null ? player : (ctx != null ? ctx.player : null);
        GuideNpc effNpc = npc != null ? npc : (ctx != null ? ctx.npc : null);
        BlockTalk358.Where where = ctx != null ? ctx.where : null;
        NpcDialogueGraph graph = ctx != null ? ctx.graph : null;
        NpcDialogueGraph.Node node = ctx != null ? ctx.node : null;
        return substituteVarTokens(text, srv, effPlayer, effNpc, where, graph, node);
    }

    /** Convenience substitution for block/door/chest/trial dialogue lines and chat fallbacks. */
    public static String substituteBlockText(String text, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        if (text == null || text.isEmpty() || text.indexOf('%') < 0) return text;
        TextVarContext ctx = peekTextContext();
        MinecraftServer srv = player != null ? player.getServer() : (ctx != null ? ctx.server : null);
        BlockTalk358.Where where = ctx != null ? ctx.where : null;
        return substituteVarTokens(text, srv, player, null, where, graph, node);
    }

    private static boolean isSimpleVarToken(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') return false;
        }
        return true;
    }

    private static Integer lookupNamedVarValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            BlockTalk358.Where where,
            NpcDialogueGraph graph,
            String rawName) {
        String clean = cleanVarName(rawName);
        if (clean.isEmpty()) return null;

        // 1. Direct local storage on the active NPC or block/door/chest/trial
        if (npc != null) {
            CompoundTag tag = getOrCreateNpcLocalTag(npc, false);
            if (tag != null && tag.contains(clean)) {
                return tag.getInt(clean);
            }
        }
        VarScopeStore453 store = server == null ? null : get(server);
        if (where != null && store != null && store.hasScopedLocal(where.key(), clean)) {
            return store.getScopedLocal(where.key(), clean);
        }

        // 2. Graph node definition (matches VarDataNode453 scope & default initial value)
        if (graph != null && graph.nodes != null) {
            for (NpcDialogueGraph.Node n : graph.nodes.values()) {
                if (n == null || n.pointsName == null || n.pointsName.isBlank()) continue;
                if (cleanVarName(n.pointsName).equalsIgnoreCase(clean)) {
                    if (server == null) {
                        return readOrangeNodeValue(null, player, npc, null, where, graph, n, 0);
                    }
                    boolean hasInit = VarDataNode453.isVarDataNode(n) && n.varValue != 0;
                    return readRaw(server, player, npc, null, where,
                            n.pointsName, n.pointsShared, n.varLocal, hasInit, n.varValue);
                }
            }
        }
        if (server == null) return null;

        // 3. Player personal or global shared NpcPoints (if registered/used anywhere in world)
        NpcPoints pts = NpcPoints.get(server);
        if (player != null) {
            Map<String, Integer> pMap = pts.personalPoints(player.getUUID());
            if (pMap != null && pMap.containsKey(clean)) {
                return pMap.get(clean);
            }
        }
        Map<String, Integer> sMap = pts.sharedPoints();
        if (sMap != null && sMap.containsKey(clean)) {
            return sMap.get(clean);
        }
        return null;
    }
}
