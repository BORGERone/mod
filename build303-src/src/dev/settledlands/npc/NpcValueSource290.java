package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Build 290: the blue value wires of the stock nodes.
 *
 * <p>Build 289 gave the "В радиусе" node a blue output. Build 290 gives one to the "Урон"
 * node as well (the body that damaged the NPC, see {@link NpcDamageValue290}) and gives the
 * four nodes that use a body a blue input: реплика (LINE), триггер (TRIGGER), условие (IF)
 * and переменная (VARIABLE).</p>
 *
 * <p>The input is stored in the node's own {@code varInputNode} field - it is part of the
 * graph NBT the game already writes, so the link is saved and reloaded with no new field
 * and no new data format. What the value does on each of those nodes:</p>
 *
 * <ul>
 *   <li><b>IF</b> - untouched: the node compares its own variable with its own operator and
 *       number, for the body the chain carries (the one the check node found);</li>
 *   <li><b>переменная</b> - reading that variable answers 1 while the body is there and 0
 *       when it is gone, so the same IF can compare it like any other variable;</li>
 *   <li><b>реплика</b> - {@code %target%} in the text becomes the body's name,
 *       {@code %target_kind%} its kind, {@code %target_uuid%} its uuid;</li>
 *   <li><b>триггер</b> - the node's commands run only for that body: when the chain carries
 *       somebody else, or when the source has nobody, the commands are skipped.</li>
 * </ul>
 *
 * <p>Everything here runs on the server only; the client half of the wire lives in
 * {@code PursuitGraphNode272} and the pin mixin.</p>
 */
public final class NpcValueSource290 {

    /** Body name token in a line node's text. */
    public static final String TOKEN_TARGET = "%target%";
    /** Body kind token: player, npc, monster or entity. */
    public static final String TOKEN_KIND = "%target_kind%";
    /** Body uuid token. */
    public static final String TOKEN_UUID = "%target_uuid%";

    /** Set while {@code GuideDialogue#sendGraph} builds the dialogue view. */
    private static final ThreadLocal<GuideNpc> CURRENT = new ThreadLocal<>();

    /**
     * Set while a failed check node is passing the flow through to its value consumer: the
     * node's own trigger list must not run on that pass. The first trigger call after the
     * check is always that node's, so the mark is consumed once and cleared.
     */
    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<Object> PENDING_TRIGGERS = new ThreadLocal<>();

    private NpcValueSource290() {}

    // ---- the two producers -------------------------------------------------

    /** Called from the damage hook: remember who hit this NPC, for the "Урон" node. */
    public static void recordDamage(GuideNpc npc, net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        NpcDamageValue290.record(npc, source, amount);
    }

    /** True when this node id hands out a body at all. */
    public static boolean isSource(GuideNpc npc, int nodeId) {
        return NpcCheckValue289.isCheckSource(npc, nodeId)
                || NpcDamageValue290.isDamageSource(npc, nodeId);
    }

    /** The body a source node hands out right now, or null when it has nobody. */
    public static Entity resolve(GuideNpc npc, int nodeId) {
        if (npc == null || nodeId < 0) return null;
        if (NpcCheckValue289.isCheckSource(npc, nodeId)) {
            return NpcCheckValue289.detected(npc, nodeId);
        }
        if (NpcDamageValue290.isDamageSource(npc, nodeId)) {
            return NpcDamageValue290.damager(npc, nodeId);
        }
        return null;
    }

    /** Kind label of a body, the same words the placeholders use. */
    public static String kindOf(Entity entity) {
        if (entity == null) return "none";
        if (entity instanceof Player) return "player";
        if (entity instanceof GuideNpc) return "npc";
        if (entity instanceof Enemy) return "monster";
        return "entity";
    }

    // ---- the four consumers ------------------------------------------------

    /** The node this consumer reads, or null when its input is not a value wire. */
    public static NpcDialogueGraph.Node sourceOf(GuideNpc npc, NpcDialogueGraph.Node consumer) {
        if (npc == null || consumer == null || consumer.varInputNode < 0) return null;
        NpcProfile profile = profileOf(npc);
        if (profile == null) return null;
        // The consumer object knows which of the three graphs it lives in; find that one
        // by identity, so an id that exists in another tab cannot be picked by mistake.
        for (NpcDialogueGraph graph : graphs(profile)) {
            if (graph == null || graph.nodes == null) continue;
            if (graph.get(consumer.id) != consumer) continue;
            NpcDialogueGraph.Node from = graph.get(consumer.varInputNode);
            return from == null || from == consumer ? null : from;
        }
        return null;
    }

    /** True when this node reads a blue value wire (and not a stock variable link). */
    public static boolean inputWired(GuideNpc npc, NpcDialogueGraph.Node consumer) {
        NpcDialogueGraph.Node from = sourceOf(npc, consumer);
        return from != null && isSourceNode(from);
    }

    /** The body this consumer's input carries right now, or null. */
    public static Entity bodyOfInput(GuideNpc npc, NpcDialogueGraph.Node consumer) {
        if (!inputWired(npc, consumer)) return null;
        return resolve(npc, consumer.varInputNode);
    }

    private static boolean isSourceNode(NpcDialogueGraph.Node node) {
        return node != null
                && (node.type == NpcDialogueGraph.CHECK || node.type == NpcDialogueGraph.DAMAGE);
    }

    // ---- IF ---------------------------------------------------------------

    /*
     * Build 297: the IF node is not touched here.
     *
     * The condition node owns its own check: the variable written into it, the operator and
     * the number. The blue wire from "В радиусе" does not replace that check - it only says
     * whose variable to read, and the chain already carries that body as its player, so the
     * stock call readVarValue(chain player, npc, variable, shared, local) is exactly right.
     *
     * Builds 290/294/295 replaced the whole condition with "body present = 1, absent = 0"
     * whenever the node had a blue wire, which is why the node stopped checking its own
     * variable. That substitution is gone.
     */

    // ---- VARIABLE ---------------------------------------------------------

    /**
     * 1 or 0 for a variable whose node reads a value wire, -1 when no such variable node
     * exists, so a normal variable keeps its stored number.
     */
    public static int variablePresence(GuideNpc npc, String name) {
        if (npc == null || name == null || name.isEmpty()) return -1;
        NpcProfile profile = profileOf(npc);
        if (profile == null) return -1;
        for (NpcDialogueGraph graph : graphs(profile)) {
            if (graph == null || graph.nodes == null) continue;
            for (NpcDialogueGraph.Node node : graph.nodes.values()) {
                if (node == null || node.type != NpcDialogueGraph.VARIABLE) continue;
                if (node.pointsName == null || !node.pointsName.equals(name)) continue;
                if (!inputWired(npc, node)) continue;
                return bodyOfInput(npc, node) != null ? 1 : 0;
            }
        }
        return -1;
    }

    // ---- LINE -------------------------------------------------------------

    /** Called while the dialogue view is built, so a line node knows whose NPC it is. */
    public static void enter(GuideNpc npc) {
        if (npc == null) CURRENT.remove();
        else CURRENT.set(npc);
    }

    /** Replaces the body tokens in a line node's text; other text is left as it was. */
    public static String lineText(NpcDialogueGraph.Node node, String text) {
        if (node == null || text == null || text.isEmpty()) return text;
        if (text.indexOf('%') < 0) return text;
        GuideNpc npc = CURRENT.get();
        if (npc == null) return text;
        if (node.type != NpcDialogueGraph.LINE) return text;
        Entity body = bodyOfInput(npc, node);
        String name = body == null ? "" : nameOf(body);
        UUID uuid = body == null ? null : body.getUUID();
        String kind = kindOf(body);
        return text.replace(TOKEN_TARGET, name)
                .replace(TOKEN_KIND, kind)
                .replace(TOKEN_UUID, uuid == null ? "" : uuid.toString());
    }

    // ---- TRIGGER ----------------------------------------------------------

    /**
     * True when this trigger node must not run: its input is wired, and either nobody is
     * there or the chain carries a different body. The list is compared by identity - it
     * is the very list stored on the node - so the check can never hit another node.
     */
    public static boolean triggerBlocked(GuideNpc npc, NpcDialogueGraph graph,
            List<String> triggers, Entity entity) {
        if (Boolean.TRUE.equals(PENDING.get())) {
            PENDING.remove();
            Object pending = PENDING_TRIGGERS.get();
            PENDING_TRIGGERS.remove();
            // The node's own payload, skipped because its check found nobody.
            if (pending == null || pending == (Object) triggers) return true;
        }
        if (npc == null || graph == null || graph.nodes == null || triggers == null
                || triggers.isEmpty()) {
            return false;
        }
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null || node.type != NpcDialogueGraph.TRIGGER) continue;
            // Compared as objects: the list is the very list stored on the node.
            if ((Object) node.triggers != (Object) triggers) continue;
            if (!inputWired(npc, node)) return false;
            Entity body = bodyOfInput(npc, node);
            if (body == null) return true;
            return entity != null && body != entity;
        }
        return false;
    }

    // ---- helpers ----------------------------------------------------------

    /** Node with this id in this graph, or null. */
    public static NpcDialogueGraph.Node node(NpcDialogueGraph graph, int nodeId) {
        if (graph == null || graph.nodes == null) return null;
        return graph.get(nodeId);
    }

    private static String nameOf(Entity entity) {
        try {
            net.minecraft.network.chat.Component name = entity.getName();
            if (name != null) return name.getString();
        } catch (Throwable ignored) {
            // fall through to the class name
        }
        return entity.getClass().getSimpleName();
    }

    /**
     * Build 294: a check node that feeds a value wire must not stop the walk.
     *
     * <p>Reported chain: "В радиусе" #4 -&gt; "Условие" #5 with {@code #4 == 0}, True -&gt;
     * {@code say true}, False -&gt; {@code say false}. Somebody in the radius printed
     * {@code false}; nobody in the radius printed nothing.</p>
     *
     * <p>Why: the walk treats "В радиусе" as a gate - {@code checkProximityCondition} false
     * ends the walk on the spot, so the IF was never reached with the "nobody" answer, and
     * its {@code == 0} branch was dead. The node's blue output is a different thing: it is
     * read live by the consumer through {@link #bodyOfInput}, and it answers 0 when the radius
     * is empty.</p>
     *
     * <p>So a failed check is allowed to let the flow through - but only when the node is
     * used as a value source, and only when the node carries no action of its own. A check
     * with an action is a gate with a payload, and keeps the stock behaviour: nothing runs,
     * the chain ends. The node's own trigger list is skipped on that pass as well, so a
     * failed check runs nothing at all and only the consumer sees the answer.</p>
     */
    public static boolean passThroughWhenValue(GuideNpc npc, NpcDialogueGraph.Node check) {
        if (npc == null || check == null) return false;
        if (check.action != null && !check.action.isBlank()) return false;
        if (!isSourceNode(check) || !feedsValueWire(npc, check)) return false;
        PENDING.set(Boolean.TRUE);
        PENDING_TRIGGERS.set(check.triggers);
        return true;
    }

    /** True when some node of this NPC's graphs takes its value from {@code check}. */
    private static boolean feedsValueWire(GuideNpc npc, NpcDialogueGraph.Node check) {
        NpcProfile profile = profileOf(npc);
        if (profile == null) return false;
        for (NpcDialogueGraph graph : graphs(profile)) {
            if (graph == null || graph.nodes == null) continue;
            if (graph.get(check.id) != check) continue;
            for (NpcDialogueGraph.Node node : graph.nodes.values()) {
                if (node != null && node.varInputNode == check.id) return true;
            }
        }
        return false;
    }

    private static NpcProfile profileOf(GuideNpc npc) {
        try {
            return npc.profile();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<NpcDialogueGraph> graphs(NpcProfile profile) {
        List<NpcDialogueGraph> out = new ArrayList<>(3);
        if (profile.dialogueGraph != null) out.add(profile.dialogueGraph);
        if (profile.walkGraph != null) out.add(profile.walkGraph);
        if (profile.animGraph != null) out.add(profile.animGraph);
        return out;
    }
}
