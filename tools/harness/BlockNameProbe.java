import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 359 probe: what the name card stores, and whether it survives a save.
 *
 * <p>The card itself is a picture and the inspector rows are widgets - both are seen in game. What no
 * screenshot can show is whether the two fields it uses are the ones the graph actually writes to disk,
 * whether a name keeps its edges (a 40-character name would be cut by the window's own island, so the
 * field caps it at 32), whether a colour index outside the four is refused instead of painting the
 * name black, and whether a stats card that arrived from an older save is really gone after the drop
 * the block's save performs.</p>
 *
 * <pre>
 *   ENSURE  added=true again=false cards=1 name='' color=0 where=left    the card is put once, empty
 *   SET     name='Forge' color=2 rgb=-11702                           what the window will be told
 *   CLAMP   high=3 low=0                                                an index out of range is folded
 *   LENGTH  kept=32 of 40                                               the cap the window needs
 *   ROUND   name='Forge' color=2 nodes=2                              a save keeps both fields
 *   DROP    stats=true dropped=1 left=0 name=true                       a stats card is gone, the name stays
 *   PLAIN   find=null name='' color=0                                   a graph without a card reads empty
 * </pre>
 */
public final class BlockNameProbe {

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 10, 10);
        if (line == null) {
            System.out.println("ENSURE line=missing");
            return;
        }
        graph.entry = line.id;

        boolean first = BlockName359.ensure(graph);
        boolean again = BlockName359.ensure(graph);
        NpcDialogueGraph.Node card = BlockName359.find(graph);
        System.out.println("ENSURE added=" + first + " again=" + again + " cards=" + cards(graph)
                + " name='" + BlockName359.name(graph) + "' color=" + BlockName359.color(graph)
                + " where=" + (card != null && card.x < line.x ? "left" : "right"));
        if (card == null) return;

        BlockName359.setName(card, "  Forge  ");
        BlockName359.setColor(card, 2);
        System.out.println("SET name='" + BlockName359.name(graph) + "' color="
                + BlockName359.color(graph) + " rgb=" + BlockName359.rgb(2));

        BlockName359.setColor(card, 99);
        int high = BlockName359.color(graph);
        BlockName359.setColor(card, -5);
        int low = BlockName359.color(graph);
        BlockName359.setColor(card, 2);
        System.out.println("CLAMP high=" + high + " low=" + low);

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 40; i++) longName.append('Я');
        BlockName359.setName(card, longName.toString());
        int kept = BlockName359.name(graph).length();
        BlockName359.setName(card, "Forge");
        System.out.println("LENGTH kept=" + kept + " of 40");

        NpcDialogueGraph loaded = NpcDialogueGraph.load(graph.save());
        System.out.println("ROUND name='" + BlockName359.name(loaded) + "' color="
                + BlockName359.color(loaded) + " nodes=" + (loaded == null ? -1 : loaded.nodes.size()));

        NpcDialogueGraph.Node stats = graph.add(BlockName359.STATS_TYPE, 0, 0);
        int dropped = BlockName359.dropType(graph, BlockName359.STATS_TYPE);
        System.out.println("DROP stats=" + (stats != null) + " dropped=" + dropped + " left="
                + BlockName359.dropType(graph, BlockName359.STATS_TYPE)
                + " name=" + (BlockName359.find(graph) != null));

        System.out.println("PLAIN find=" + BlockName359.find(new NpcDialogueGraph())
                + " name='" + BlockName359.name(null) + "' color=" + BlockName359.color(null));
    }

    private static int cards(NpcDialogueGraph graph) {
        int count = 0;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type == BlockName359.TYPE) count++;
        }
        return count;
    }
}
