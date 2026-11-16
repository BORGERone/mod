package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;

/**
 * Build 359: a block can be given a name, and the name is what the dialogue shows.
 *
 * <p>Build 358 opened the stock dialogue window for a block's line, but the window had no speaker
 * to show: a block is not a body and has no name of its own, so the name island carried the
 * coordinates instead. This is that name - a node of its own, the way a body's card is a node of its
 * own: it lives on the block's canvas, it is written once and every dialogue of that block uses it.</p>
 *
 * <p>The node is the block's equivalent of the «Характеристики» card of an NPC: a card that exists in
 * every graph without being added from the palette, and that carries one small piece of the block's
 * identity. The difference is what it names: a body's card holds its health, its damage and its
 * profession, a block has none of those, so a block gets a name and the colour it is written in.</p>
 *
 * <p>Two fields, both of them already saved for every node, so the name survives a save without a new
 * key anywhere:</p>
 *
 * <ul>
 *   <li><b>Имя</b> - {@code Node#text}: what the window shows as the speaker, {@code %player%} and the
 *       other stock placeholders filled the same way a line is filled;</li>
 *   <li><b>Цвет</b> - {@code Node#varValue}: an index into the colours this mod already renders a
 *       speaker name in ({@code GuideDialogueScreen#NAME_COLORS}), so the block's name can be white,
 *       cream, gold or grey exactly like a body's.</li>
 * </ul>
 *
 * <p>An NPC keeps its stats card and its «Автономный ИИ» card; a block has no health to show and no
 * body to walk, so both are taken away from a block's graph ({@link #dropType}) and the name card is
 * put in their place.</p>
 *
 * <p>Cost: one walk of the graph per lookup, on the client once per card and on the server once per
 * line. Nothing here runs per tick.</p>
 */
public final class BlockName359 {

    /** Node type of the block's name card. 16 is the NPC stats card, 17 is free; 18 is this one. */
    public static final int TYPE = 18;

    /** The NPC stats card; a block must not carry one, and a save that has one drops it. */
    public static final int STATS_TYPE = 16;

    /** Longer than a name a window can show without clipping its island. */
    public static final int MAX_NAME = 32;

    /** The index the dialogue falls back to when nothing was chosen. */
    public static final int DEFAULT_COLOR = 0;

    /**
     * The speaker-name colours of this mod, index for index the array {@code GuideDialogueScreen}
     * renders a name with ({@code NAME_COLORS}): white, cream, gold, grey. A colour outside the array
     * would be clamped by the screen, so the picker offers exactly these.
     */
    public static final int[] COLORS = {0xFFFFFFFF, 0xFFFFE9A8, 0xFFFFD24A, 0xFFBFBFBF};

    /** Language keys of the four colours, in the same order as {@link #COLORS}. */
    public static final String[] COLOR_KEYS = {
        "gui.settledlands.name.color.0",
        "gui.settledlands.name.color.1",
        "gui.settledlands.name.color.2",
        "gui.settledlands.name.color.3",
    };

    public static final String TITLE_KEY = "gui.settledlands.name.title";
    public static final String FIELD_KEY = "gui.settledlands.name.field";
    public static final String COLOR_KEY = "gui.settledlands.name.color";
    public static final String EMPTY_KEY = "gui.settledlands.name.empty";

    /** Where the card is put when the graph has no node to hang it next to. */
    private static final int DEFAULT_X = -360;
    private static final int DEFAULT_Y = -160;

    /** The gap between the block's first node and the card, on both axes. */
    private static final int GAP_X = 360;
    private static final int GAP_Y = 160;

    private BlockName359() {}

    // ---- the node -----------------------------------------------------------

    /** True when this node is a name card - the type check every caller asks for. */
    public static boolean isName(NpcDialogueGraph.Node node) {
        return node != null && node.type == TYPE;
    }

    /** The name card of this graph, or null when the graph has none (an NPC graph never has one). */
    public static NpcDialogueGraph.Node find(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return null;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (isName(node)) return node;
        }
        return null;
    }

    /**
     * Put the name card on a block's graph if it is not there yet, next to the block's first node.
     * Returns true when a card was added, so the caller can mark the graph changed exactly once.
     */
    public static boolean ensure(NpcDialogueGraph graph) {
        if (graph == null) return false;
        if (find(graph) != null) return false;
        NpcDialogueGraph.Node anchor = graph.entry >= 0 ? graph.get(graph.entry) : null;
        int x = anchor == null ? DEFAULT_X : clampCoord(anchor.x - GAP_X);
        int y = anchor == null ? DEFAULT_Y : clampCoord(anchor.y - GAP_Y);
        NpcDialogueGraph.Node node = graph.add(TYPE, x, y);
        if (node == null) return false;
        node.text = "";
        node.varValue = DEFAULT_COLOR;
        return true;
    }

    /** The name of the block, empty when it was never written. */
    public static String name(NpcDialogueGraph graph) {
        return nameOf(find(graph));
    }

    /** The colour index of the block's name, {@link #DEFAULT_COLOR} when there is no card. */
    public static int color(NpcDialogueGraph graph) {
        return colorOf(find(graph));
    }

    /** Remove every node of one type - the NPC stats card of a graph that arrived from an older save. */
    public static int dropType(NpcDialogueGraph graph, int type) {
        if (graph == null || graph.nodes == null) return 0;
        List<Integer> doomed = new ArrayList<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type == type) doomed.add(node.id);
        }
        for (Integer id : doomed) graph.nodes.remove(id);
        return doomed.size();
    }

    // ---- one node -----------------------------------------------------------

    public static String nameOf(NpcDialogueGraph.Node node) {
        if (node == null || node.text == null) return "";
        String text = node.text.trim();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }

    public static int colorOf(NpcDialogueGraph.Node node) {
        return node == null ? DEFAULT_COLOR : clampColor(node.varValue);
    }

    public static void setName(NpcDialogueGraph.Node node, String name) {
        if (node == null) return;
        String text = name == null ? "" : name.trim();
        node.text = text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }

    public static void setColor(NpcDialogueGraph.Node node, int index) {
        if (node == null) return;
        node.varValue = clampColor(index);
    }

    public static int clampColor(int index) {
        return Math.max(0, Math.min(COLORS.length - 1, index));
    }

    /** The RGB of a colour index - what the card paints the title in and what the window is told. */
    public static int rgb(int index) {
        return COLORS[clampColor(index)];
    }

    private static int clampCoord(int value) {
        int max = NpcDialogueGraph.MAX_COORD;
        return Math.max(-max, Math.min(max, value));
    }
}
