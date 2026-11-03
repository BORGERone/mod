import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 357 probe: what the block editor's palette actually builds.
 *
 * <p>The palette of the block editor is a hook on the stock screen's create menu: a row number goes
 * in, a node type comes out. Both halves are easy to get wrong and impossible to see in a screenshot -
 * a row that maps to nothing, a row that maps to a type the block cannot run, or a row list that has
 * drifted from the palette array. So the mapping lives in one static method and is asked here, without
 * a screen, a client or a world:</p>
 *
 * <pre>
 *   ROWS      0:0 2:2 3:3 4:4 5:5 13:13     every row a block offers builds its own node type
 *   PALETTE   0 2 3 4 5 13                   the array the walker and the hooks agree on
 *   REFUSED   1 6 7 8 9 10 11 12 14          a row a block cannot run answers -1
 *   CONSISTENT true                          rows and types are the same list, in the same order
 *   ALLOWED   line=true button=false         the replica node is in, a text button is not
 *   GRAPH     error=ok text=kept entry=set   a line node is a valid block node and its text survives
 * </pre>
 */
public final class BlockPaletteProbe {

    public static void main(String[] args) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < BlockTrigger351.PALETTE_ROWS.length; i++) {
            int row = BlockTrigger351.PALETTE_ROWS[i];
            if (i > 0) rows.append(' ');
            rows.append(row).append(':').append(BlockTrigger351.typeForRow(row));
        }
        System.out.println("ROWS " + rows);

        StringBuilder palette = new StringBuilder();
        for (int i = 0; i < BlockTrigger351.PALETTE.length; i++) {
            if (i > 0) palette.append(' ');
            palette.append(BlockTrigger351.PALETTE[i]);
        }
        System.out.println("PALETTE " + palette);

        StringBuilder refused = new StringBuilder();
        for (int row = 0; row <= 14; row++) {
            if (BlockTrigger351.typeForRow(row) >= 0) continue;
            if (refused.length() > 0) refused.append(' ');
            refused.append(row);
        }
        System.out.println("REFUSED " + refused);

        boolean sameLength = BlockTrigger351.PALETTE_ROWS.length == BlockTrigger351.PALETTE.length;
        boolean consistent = sameLength;
        for (int i = 0; consistent && i < BlockTrigger351.PALETTE_ROWS.length; i++) {
            consistent = BlockTrigger351.typeForRow(BlockTrigger351.PALETTE_ROWS[i])
                    == BlockTrigger351.PALETTE[i];
        }
        System.out.println("CONSISTENT " + consistent);

        System.out.println("ALLOWED line=" + BlockTrigger351.allowed(NpcDialogueGraph.LINE)
                + " button=" + BlockTrigger351.allowed(NpcDialogueGraph.BUTTON)
                + " timer=" + BlockTrigger351.allowed(NpcDialogueGraph.TIMER));

        // A block graph with a line node has to be something the editor agrees to save, and the text
        // has to survive the NBT round trip - that is what the block will say later.
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 20, 20);
        if (line == null) {
            System.out.println("GRAPH add=failed");
            return;
        }
        line.text = "Привет, %player%";
        graph.entry = line.id;
        String error = graph.error();
        NpcDialogueGraph loaded = NpcDialogueGraph.load(graph.save());
        NpcDialogueGraph.Node back = loaded == null ? null : loaded.get(line.id);
        String text = back == null ? "lost" : back.text.equals(line.text) ? "kept" : "changed";
        String entry = loaded != null && loaded.entry == line.id && loaded.get(loaded.entry) != null
                ? "set" : "lost";
        System.out.println("GRAPH error=" + (error == null || error.isEmpty() ? "ok" : error)
                + " text=" + text + " entry=" + entry);
    }
}
