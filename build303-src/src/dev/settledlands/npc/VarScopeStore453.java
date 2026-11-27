package dev.settledlands.npc;

import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialSpawnerBridge446;
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
 * Build 453: persistent world storage for object-local variables (block/door/chest/trial),
 * one-time initialization markers for UE5 {@link VarDataNode453} containers, and unified
 * variable/IF runtime evaluation supporting all 14 math operations plus simultaneous blue (entity)
 * and orange (variable/numeric) inputs across both NPC and block/trial graphs.
 */
public final class VarScopeStore453 extends SavedData {

    private static final String FILE = "settledlands_var_scope453";
    private static final String TAG_SCOPES = "Scopes";
    private static final String TAG_KEY = "K";
    private static final String TAG_VARS = "V";
    private static final String TAG_INIT = "Init";

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final Map<String, Map<String, Integer>> localScopes = new HashMap<>();
    private final Set<String> initializedKeys = new HashSet<>();

    public interface NpcLocalVarBridge {
        CompoundTag getOrCreate(GuideNpc npc, boolean create);
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

    public boolean isInitialized(String initKey) {
        return initKey != null && initializedKeys.contains(initKey);
    }

    public void markInitialized(String initKey) {
        if (initKey != null && !initKey.isEmpty() && initializedKeys.add(initKey)) {
            setDirty();
        }
    }

    private static String cleanVarName(String name) {
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
        if (srcId < 0) return null;
        NpcDialogueGraph.Node src = graph.get(srcId);
        if (src == null) return null;
        if (NpcValueSource290.isSource(npc, srcId)) {
            return NpcValueSource290.resolve(npc, srcId);
        }
        if (NpcPursuit269.isPursuitAction(src.action)) {
            // Check if the Pursuit node itself takes its body from a blue source node (#id)
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
        return null;
    }

    public static ServerPlayer resolveBlockBluePlayer(ServerLevel level, ServerPlayer actor,
            NpcDialogueGraph graph, NpcDialogueGraph.Node consumer, BlockTalk358.Where where) {
        if (graph == null || consumer == null) return actor;
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId < 0) return actor;
        NpcDialogueGraph.Node src = graph.get(srcId);
        if (src == null) return null;
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
            return TrialSpawnerBridge446.nearestPlayer(level, pos, r, false);
        }
        if (src.type == 40 || src.type == 50 || src.type == NpcDialogueGraph.DAMAGE) {
            return actor;
        }
        return actor;
    }

    // ---- Low-level read / write with UE5 initial value support ----

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
            return 0;
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

    // ---- Execution & Evaluation for VARIABLE (4) and IF (3) ----

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

        // Blue input selects whose player/NPC variable is read/modified.
        ServerPlayer targetPlayer = player;
        GuideNpc targetNpc = npc;
        int blueId = VarDataNode453.entityInput(graph, node);
        if (blueId >= 0) {
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
        boolean hasOwnName = node.pointsName != null && !node.pointsName.isBlank();

        String targetName;
        boolean targetShared;
        boolean targetLocal;
        boolean targetHasInit = false;
        int targetInit = 0;
        int operand;

        if (orangeSrc != null && !hasOwnName) {
            // Pattern A (UE5 Set/Modify linked variable): node points directly at UE5 variable container
            targetName = orangeSrc.pointsName;
            targetShared = orangeSrc.pointsShared;
            targetLocal = orangeSrc.varLocal;
            targetHasInit = VarDataNode453.isVarDataNode(orangeSrc) && orangeSrc.varValue != 0;
            targetInit = orangeSrc.varValue;
            operand = node.varValue;
        } else if (orangeSrc != null) {
            // Pattern B (Operand from orange wire): modify node's own variable using value from orangeSrc
            targetName = node.pointsName;
            targetShared = node.pointsShared;
            targetLocal = node.varLocal;
            operand = readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, where, orangeSrc);
        } else {
            // No orange wire: modify node's own variable using node.varValue
            targetName = node.pointsName;
            targetShared = node.pointsShared;
            targetLocal = node.varLocal;
            operand = node.varValue;
        }

        if (targetName == null || targetName.isBlank()) return;
        int cur = readRaw(server, targetPlayer, targetNpc, npcLocals, where,
                targetName, targetShared, targetLocal, targetHasInit, targetInit);
        int next = VarDataNode453.applyMathOp(cur, node.varOp, operand);
        writeRaw(server, targetPlayer, targetNpc, npcLocals, where,
                targetName, targetShared, targetLocal, next);
    }

    /** Reads the numeric value supplied by an orange source node (UE5 Var node or VARIABLE). */
    public static int readOrangeNodeValue(
            MinecraftServer server,
            ServerPlayer player,
            GuideNpc npc,
            NpcLocalVarBridge npcLocals,
            BlockTalk358.Where where,
            NpcDialogueGraph.Node src) {
        if (src == null) return 0;
        if (src.pointsName != null && !src.pointsName.isBlank()) {
            boolean hasInit = VarDataNode453.isVarDataNode(src) && src.varValue != 0;
            return readRaw(server, player, npc, npcLocals, where,
                    src.pointsName, src.pointsShared, src.varLocal, hasInit, src.varValue);
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
        if (blueId >= 0) {
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
            return readOrangeNodeValue(server, targetPlayer, targetNpc, npcLocals, null, orangeSrc);
        }
        if (node.pointsName != null && !node.pointsName.isBlank()) {
            return readRaw(server, targetPlayer, targetNpc, npcLocals, null,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
        }
        return fallback;
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
            return readOrangeNodeValue(server, targetPlayer, null, null, where, orangeSrc);
        }
        if (node.pointsName != null && !node.pointsName.isBlank()) {
            return readRaw(server, targetPlayer, null, null, where,
                    node.pointsName, node.pointsShared, node.varLocal, false, 0);
        }
        return 0;
    }
}
