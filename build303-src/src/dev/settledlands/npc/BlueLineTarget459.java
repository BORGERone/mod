package dev.settledlands.npc;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialSpawnerBridge446;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Build 459: enforces strict blue-target recipient gating and redirection for dialogue nodes
 * ({@link NpcDialogueGraph#LINE}, {@link NpcDialogueGraph#BUTTON}, {@link NpcDialogueGraph#CUTSCENE})
 * during autonomous/non-player event flows ({@code TIMER}, {@code TIME}, non-player {@code CHECK},
 * and non-player {@code DAMAGE}) across both NPC ({@link GuideDialogue}) and block/door/chest/trial
 * ({@link RadiusPass354}, {@link BlockTrigger351}) graphs. Zero lambdas.
 */
public final class BlueLineTarget459 {

    private static final ThreadLocal<Deque<Boolean>> NO_EVENT_TARGET_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Deque<ServerPlayer[]>> RESOLVED_PLAYER_STACK = new ThreadLocal<>();

    private static final Predicate<Entity> ALIVE_NON_SPECTATOR = new Predicate<Entity>() {
        @Override
        public boolean test(Entity e) {
            return e != null && e.isAlive() && !e.isRemoved()
                    && !(e instanceof Player p && p.isSpectator());
        }
    };

    private BlueLineTarget459() {}

    // ---- Autonomous / no-event-player context stack ----

    public static void pushNoEventTarget(boolean noEventTarget) {
        Deque<Boolean> st = NO_EVENT_TARGET_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            NO_EVENT_TARGET_STACK.set(st);
        }
        st.push(noEventTarget ? Boolean.TRUE : Boolean.FALSE);
    }

    public static void popNoEventTarget() {
        Deque<Boolean> st = NO_EVENT_TARGET_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) NO_EVENT_TARGET_STACK.remove();
    }

    public static boolean isNoEventTarget() {
        Deque<Boolean> st = NO_EVENT_TARGET_STACK.get();
        return st != null && !st.isEmpty() && Boolean.TRUE.equals(st.peek());
    }

    // ---- Resolved recipient handoff between GuideDialogue.resolveExecutionTarget and trigger*Chain ----

    public static void pushResolvedSlot() {
        Deque<ServerPlayer[]> st = RESOLVED_PLAYER_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            RESOLVED_PLAYER_STACK.set(st);
        }
        st.push(new ServerPlayer[1]);
    }

    public static void recordResolvedPlayer(ServerPlayer player) {
        Deque<ServerPlayer[]> st = RESOLVED_PLAYER_STACK.get();
        if (st == null || st.isEmpty()) return;
        ServerPlayer[] slot = st.peek();
        if (slot != null) slot[0] = player;
    }

    public static ServerPlayer popResolvedPlayer() {
        Deque<ServerPlayer[]> st = RESOLVED_PLAYER_STACK.get();
        if (st == null) return null;
        ServerPlayer[] slot = st.isEmpty() ? null : st.pop();
        if (st.isEmpty()) RESOLVED_PLAYER_STACK.remove();
        return slot == null ? null : slot[0];
    }

    // ---- Validation & wire inspection ----

    public static boolean isValidRecipient(ServerPlayer sp, Level expectedLevel) {
        return sp != null
                && !sp.isRemoved()
                && sp.isAlive()
                && !sp.isSpectator()
                && (expectedLevel == null || sp.level() == expectedLevel);
    }

    /**
     * Returns true when {@code consumer} has an incoming blue wire carrying an entity target
     * ({@code CHECK}, {@code DAMAGE}, {@code Door}, {@code Chest}, {@code Trial}, or {@code Pursuit}),
     * excluding variable-only {@link VarDataNode453} wires (direct or forwarded via {@code Pursuit}).
     */
    public static boolean hasBlueEntityWire(NpcDialogueGraph graph, NpcDialogueGraph.Node consumer) {
        if (graph == null || consumer == null) return false;
        if (VarScopeStore453.resolveBlueVarNode(graph, consumer) != null) {
            return false;
        }
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId >= 0) {
            NpcDialogueGraph.Node src = graph.get(srcId);
            if (src == null || VarDataNode453.isVarDataNode(src)) return false;
            return isEntityBlueSourceNode(src);
        }
        if (graph.nodes != null) {
            for (NpcDialogueGraph.Node cand : graph.nodes.values()) {
                if (cand != null && NpcPursuit269.isPursuitAction(cand.action)
                        && pursuitHasConsumer(cand.action, consumer.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEntityBlueSourceNode(NpcDialogueGraph.Node src) {
        if (src == null) return false;
        return src.type == NpcDialogueGraph.CHECK
                || src.type == NpcDialogueGraph.DAMAGE
                || src.type == 40
                || src.type == 50
                || TrialGraph446.isTrialNode(src)
                || NpcPursuit269.isPursuitAction(src.action);
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

    // ---- NPC resolution ----

    /**
     * Resolves the recipient {@link ServerPlayer} from {@code consumer}'s blue entity wire inside an
     * NPC graph, or {@code null} when no blue entity wire is attached or the wired source yields no
     * live non-spectator player in {@code npc}'s level.
     */
    public static ServerPlayer resolveNpcLineBluePlayer(
            GuideNpc npc, NpcDialogueGraph graph, NpcDialogueGraph.Node consumer) {
        if (npc == null || graph == null || consumer == null) return null;
        if (!hasBlueEntityWire(graph, consumer)) return null;
        Entity ent = VarScopeStore453.resolveNpcBlueEntity(npc, graph, consumer);
        if (ent instanceof ServerPlayer sp && isValidRecipient(sp, npc.level())) {
            return sp;
        }
        return null;
    }

    // ---- Block / Door / Chest / Trial resolution ----

    /**
     * Resolves the effective recipient for {@link NpcDialogueGraph#LINE},
     * {@link NpcDialogueGraph#BUTTON}, or {@link NpcDialogueGraph#CUTSCENE} inside
     * {@link BlockTrigger351#walk}:
     * <ul>
     *   <li>In autonomous/no-target flows ({@code TIMER}, {@code TIME}, non-player {@code CHECK}),
     *       returns ONLY a live non-spectator {@link ServerPlayer} obtained from the node's blue
     *       entity wire, or {@code null} to suppress delivery.</li>
     *   <li>In player-initiated flows (block click, door open, chest open, trial participant, or
     *       player-hit {@code CHECK}), preserves the event {@code player}.</li>
     * </ul>
     */
    public static ServerPlayer resolveBlockDialogTarget(
            ServerPlayer player,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node node,
            BlockTalk358.Where where) {
        if (isNoEventTarget()) {
            return resolveBlockLineBluePlayer(player, graph, node, where);
        }
        return player;
    }

    /**
     * Strictly resolves a live non-spectator {@link ServerPlayer} from {@code consumer}'s blue
     * entity wire inside a block/door/chest/trial graph (never falls back to arbitrary nearby players
     * or {@link VarDataNode453} variables).
     */
    public static ServerPlayer resolveBlockLineBluePlayer(
            ServerPlayer eventPlayer,
            NpcDialogueGraph graph,
            NpcDialogueGraph.Node consumer,
            BlockTalk358.Where where) {
        if (graph == null || consumer == null || where == null || where.level() == null) {
            return null;
        }
        if (!hasBlueEntityWire(graph, consumer)) return null;
        int srcId = VarDataNode453.entityInput(graph, consumer);
        if (srcId < 0) return null;
        NpcDialogueGraph.Node src = graph.get(srcId);
        if (src == null || VarDataNode453.isVarDataNode(src)) return null;

        ServerLevel level = where.level();
        if (src.type == NpcDialogueGraph.CHECK) {
            return resolveBlockCheckPlayer(level, src, where);
        }
        if (TrialGraph446.isTrialNode(src)) {
            BlockPos pos = new BlockPos(where.x(), where.y(), where.z());
            BlockEntity be = level.getBlockEntity(pos);
            List<UUID> participants = TrialSpawnerBridge446.readParticipants(be);
            if (participants == null || participants.isEmpty()) return null;
            double cx = where.x() + 0.5D;
            double cy = where.y() + 0.5D;
            double cz = where.z() + 0.5D;
            ServerPlayer best = null;
            double bestD2 = Double.MAX_VALUE;
            List<ServerPlayer> players = level.players();
            if (players == null) return null;
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer sp = players.get(i);
                if (!isValidRecipient(sp, level)) continue;
                UUID pid = sp.getUUID();
                if (pid == null || !participants.contains(pid)) continue;
                double d2 = sp.distanceToSqr(cx, cy, cz);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = sp;
                }
            }
            return best;
        }
        if (src.type == 40) {
            return findOnlinePlayerByName(level, DoorGraph382.opener(src));
        }
        if (src.type == 50) {
            return findOnlinePlayerByName(level, ChestGraph396.opener(src));
        }
        return null;
    }

    /**
     * Resolves a {@link ServerPlayer} matching a block {@link NpcDialogueGraph#CHECK} node, honoring
     * {@code checkTarget} ({@code TARGET_PLAYER} / {@code TARGET_ANY} vs non-player filters) and
     * square/circle bounds ({@link RadiusZone456}), and permitting creative-mode players.
     */
    public static ServerPlayer resolveBlockCheckPlayer(
            ServerLevel level, NpcDialogueGraph.Node checkNode, BlockTalk358.Where where) {
        if (level == null || checkNode == null || where == null) return null;
        if (checkNode.type != NpcDialogueGraph.CHECK) return null;
        int targetKind = checkNode.checkTarget;
        if (targetKind == NpcDialogueGraph.TARGET_MONSTER
                || targetKind == NpcDialogueGraph.TARGET_NPC
                || targetKind == NpcDialogueGraph.TARGET_PASSIVE) {
            return null;
        }

        double r = Math.max(1.0D, Math.min(64.0D, checkNode.checkRadius));
        double rSq = r * r;
        double cx = where.x() + 0.5D;
        double cy = where.y() + 0.5D;
        double cz = where.z() + 0.5D;
        boolean square = RadiusZone456.isSquare(checkNode);

        if (targetKind == NpcDialogueGraph.TARGET_ANY) {
            AABB box = new AABB(where.x(), where.y(), where.z(),
                    where.x() + 1.0D, where.y() + 1.0D, where.z() + 1.0D).inflate(r);
            List<Entity> bodies = level.getEntitiesOfClass(Entity.class, box, ALIVE_NON_SPECTATOR);
            if (bodies == null || bodies.isEmpty()) return null;
            Entity closest = null;
            double bestD2 = Double.MAX_VALUE;
            for (int i = 0; i < bodies.size(); i++) {
                Entity e = bodies.get(i);
                if (e == null || e.level() != level) continue;
                if (square) {
                    if (!RadiusZone456.isInsideBox(cx, cy, cz, e.getX(), e.getY(), e.getZ(), r)) {
                        continue;
                    }
                } else {
                    if (e.distanceToSqr(cx, cy, cz) > rSq) continue;
                }
                double d2 = e.distanceToSqr(cx, cy, cz);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    closest = e;
                }
            }
            if (closest instanceof ServerPlayer sp && isValidRecipient(sp, level)) {
                return sp;
            }
            return null;
        }

        List<ServerPlayer> players = level.players();
        if (players == null || players.isEmpty()) return null;
        ServerPlayer best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer sp = players.get(i);
            if (!isValidRecipient(sp, level)) continue;
            if (square) {
                if (!RadiusZone456.isInsideBox(cx, cy, cz, sp.getX(), sp.getY(), sp.getZ(), r)) {
                    continue;
                }
            } else {
                if (sp.distanceToSqr(cx, cy, cz) > rSq) continue;
            }
            double d2 = sp.distanceToSqr(cx, cy, cz);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = sp;
            }
        }
        return best;
    }

    public static ServerPlayer findOnlinePlayerByName(ServerLevel level, String name) {
        if (level == null || name == null || name.isBlank()) return null;
        String target = name.trim();
        List<ServerPlayer> players = level.players();
        if (players == null || players.isEmpty()) return null;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer sp = players.get(i);
            if (!isValidRecipient(sp, level)) continue;
            if (sp.getName() != null && target.equals(sp.getName().getString())) {
                return sp;
            }
        }
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer sp = players.get(i);
            if (!isValidRecipient(sp, level)) continue;
            if (sp.getName() != null && target.equalsIgnoreCase(sp.getName().getString())) {
                return sp;
            }
        }
        return null;
    }

    /**
     * Used by {@link RadiusPass354#timerEvents} and {@link RadiusPass354#timeEvents} when no player
     * is within the default 32-block {@code EVENT_RANGE}: checks whether any dialogue/button/cutscene
     * node in {@code graph} currently resolves a live player from a blue entity wire (e.g. a
     * 40..64-block {@code CHECK} node or recorded {@code Door}/{@code Chest}/{@code Trial} target).
     */
    public static ServerPlayer findReachableBluePlayerForAutonomousStart(
            ServerLevel level, RadiusPass354.Spot spot, NpcDialogueGraph graph) {
        if (level == null || spot == null || graph == null || graph.nodes == null) return null;
        List<ServerPlayer> players = level.players();
        if (players == null || players.isEmpty()) return null;
        BlockTalk358.Where where = new BlockTalk358.Where(
                level, spot.key(), spot.x(), spot.y(), spot.z());
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null) continue;
            if (node.type != NpcDialogueGraph.LINE
                    && node.type != NpcDialogueGraph.BUTTON
                    && node.type != NpcDialogueGraph.CUTSCENE) {
                continue;
            }
            if (!hasBlueEntityWire(graph, node)) continue;
            ServerPlayer resolved = resolveBlockLineBluePlayer(null, graph, node, where);
            if (resolved != null) return resolved;
        }
        return null;
    }
}
