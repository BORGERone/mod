package dev.settledlands.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 345: every NPC throws the dice of a random line for itself.
 *
 * <p>Build 336 put the roll of a "Реплика" node into the node itself, so the phrase was one value for
 * the whole world: ten NPCs sharing the tab all said the same line, because they were all reading the
 * same number. The dice belong to the NPC, not to the shared node - a node only says "this line is
 * random, and here is the author's own example of it".</p>
 *
 * <p>So the roll is now kept per NPC and per node, thrown once the first time that NPC needs the line
 * and answered from the cache after that: the character keeps its phrase for as long as it exists,
 * and the next NPC of the same profession - or the same one after the world reloads - throws its own.
 * The map is weak on the NPC, so an NPC that is gone takes its rolls with it and the cache cannot grow
 * past the bodies that are actually loaded. Two numbers per NPC per random node, and no allocation
 * on the way back.</p>
 *
 * <p>The NPC is known only while {@code GuideDialogue.sendGraph} builds the dialogue view - that is
 * the one call that turns a node into a line, and it is wrapped by
 * {@code NpcRandomLineContext345}. Everywhere else the phrase falls back to the author's stored roll,
 * so nothing outside a spoken line can change.</p>
 */
public final class NpcRandomLine345 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/NpcRandomLine345");

    /** Bounded report: the first few rigs whose dice are thrown, so a live log says which. */
    private static final int REPORT_LIMIT = 4;

    /** Rolled phrase per NPC and node id; weak on the NPC so a dead body takes its rolls with it. */
    private static final Map<GuideNpc, Map<Integer, Integer>> ROLLS = new WeakHashMap<>();

    /** The NPC whose dialogue view is being built; set by the hook around {@code sendGraph}. */
    private static GuideNpc speaking;

    private NpcRandomLine345() {
    }

    /** The NPC that is about to say a line, for the length of one dialogue view. */
    public static void enter(GuideNpc npc) {
        speaking = npc;
    }

    /** The view is sent: no NPC is speaking any more. */
    public static void exit() {
        speaking = null;
    }

    /** The NPC currently speaking, or null. Kept for tests; the roll is the only consumer. */
    public static GuideNpc speaking() {
        return speaking;
    }

    /**
     * The phrase this NPC has to say for the node, or null when that is not this build's decision -
     * the toggle is off, there is nothing to choose from, or the line is not random at all.
     */
    public static String pick(NpcDialogueGraph.Node node) {
        if (!NpcRandomLine336.random(node)) return null;
        List<String> lines = NpcRandomLine336.phrases(node.text);
        if (lines.size() < 2) return null;
        GuideNpc npc = speaking;
        int index = npc == null
                ? NpcRandomLine336.index(node, lines.size())
                : roll(npc, node, lines.size());
        return lines.get(index);
    }

    /** The phrase this NPC settled on for the node, 1-based; the roll of the author's example otherwise. */
    public static int rollFor(GuideNpc npc, NpcDialogueGraph.Node node) {
        int count = NpcRandomLine336.count(node);
        if (npc == null || count < 1) return node == null ? 0 : NpcRandomLine336.roll(node);
        return roll(npc, node, count);
    }

    /** The roll of one NPC for one node: thrown once, then answered from the NPC's own cache. */
    private static int roll(GuideNpc npc, NpcDialogueGraph.Node node, int count) {
        Map<Integer, Integer> byNode = ROLLS.get(npc);
        if (byNode == null) {
            byNode = new HashMap<>(4);
            ROLLS.put(npc, byNode);
        }
        Integer before = byNode.get(node.id);
        int rolled = rollInto(byNode, node.id, count);
        if (before == null || before != rolled) note(npc, node, rolled);
        return rolled - 1;
    }

    /**
     * The pure part of the dice: the roll one node has for one owner, kept in that owner's own map.
     * The same map and node answer the same number until the number stops fitting the text, and then
     * the dice are thrown again - so an author who edits the phrases never leaves a stale index.
     */
    public static int rollInto(Map<Integer, Integer> byNode, int nodeId, int count) {
        if (count < 1) return 0;
        Integer stored = byNode.get(nodeId);
        if (stored != null && stored >= 1 && stored <= count) return stored;
        int rolled = 1 + ThreadLocalRandom.current().nextInt(count);
        byNode.put(nodeId, rolled);
        return rolled;
    }

    /** One line for the first few rolls, so a live log says which body rolled which phrase. */
    private static void note(GuideNpc npc, NpcDialogueGraph.Node node, int rolled) {
        if (ROLLS.size() > REPORT_LIMIT) return;
        String name = npc.getName() == null ? "?" : npc.getName().getString();
        LOGGER.warn("random line: NPC '{}' rolled {} for node {}", name, rolled, node.id);
    }
}
