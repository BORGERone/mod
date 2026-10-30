import dev.settledlands.npc.CutsceneFadeGraphData263;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class CutsceneFadeGraphDataProbe263 {
    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node source = graph.add(7, NpcDialogueGraph.CUTSCENE);
        source.animLoop = true;
        source.moveEscort = true;
        CutsceneFadeGraphData263.setFadeOut(source, true);
        CutsceneFadeGraphData263.setFadeIn(source, false);
        check("fade switches read independently", CutsceneFadeGraphData263.fadeOut(source)
                && !CutsceneFadeGraphData263.fadeIn(source));
        check("legacy animation/movement flags are untouched", source.animLoop && source.moveEscort);

        NpcDialogueGraph.Node copied = graph.add(8, NpcDialogueGraph.CUTSCENE);
        CutsceneFadeGraphData263.copy(source, copied);
        check("copy/undo snapshot preserves fade switches", CutsceneFadeGraphData263.fadeOut(copied)
                && !CutsceneFadeGraphData263.fadeIn(copied));
        NpcDialogueGraph.Node otherType = graph.add(9, 9);
        CutsceneFadeGraphData263.setFadeOut(otherType, true);
        check("non-cutscene nodes reject fade settings", !CutsceneFadeGraphData263.fadeOut(otherType));

        CompoundTag savedNode = new CompoundTag();
        savedNode.putInt("Id", source.id);
        savedNode.putInt("Type", source.type);
        ListTag nodes = new ListTag();
        nodes.add(savedNode);
        CompoundTag root = new CompoundTag();
        root.put("Nodes", nodes);
        CutsceneFadeGraphData263.save(graph, root);
        check("enabled options persist as dedicated NBT keys", savedNode.getBoolean("CutsceneFadeOut")
                && !savedNode.contains("CutsceneFadeIn"));

        NpcDialogueGraph loadedGraph = new NpcDialogueGraph();
        NpcDialogueGraph.Node loaded = loadedGraph.add(7, NpcDialogueGraph.CUTSCENE);
        CutsceneFadeGraphData263.load(loadedGraph, root);
        check("NBT restores independent options", CutsceneFadeGraphData263.fadeOut(loaded)
                && !CutsceneFadeGraphData263.fadeIn(loaded));
        System.out.println("CUTSCENE FADE GRAPH DATA 263: PASS");
    }
}
