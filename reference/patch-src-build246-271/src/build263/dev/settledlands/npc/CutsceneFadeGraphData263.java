package dev.settledlands.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Persists the two cutscene-only flags while retaining the existing compact Node ABI. */
public final class CutsceneFadeGraphData263 {
    private static final String FADE_OUT_KEY = "CutsceneFadeOut";
    private static final String FADE_IN_KEY = "CutsceneFadeIn";

    private CutsceneFadeGraphData263() {}

    /**
     * For CUTSCENE nodes, the existing animation-loop and move-escort booleans are otherwise unused.
     * Reuse them as storage so legacy graph copying/undo and the binary Node layout remain intact.
     */
    public static boolean fadeOut(NpcDialogueGraph.Node node) {
        return node != null && node.type == NpcDialogueGraph.CUTSCENE
                && ((CutsceneFadeNodeAccess263) (Object) node).settledlands$getFadeOut();
    }

    public static boolean fadeIn(NpcDialogueGraph.Node node) {
        return node != null && node.type == NpcDialogueGraph.CUTSCENE
                && ((CutsceneFadeNodeAccess263) (Object) node).settledlands$getFadeIn();
    }

    public static void setFadeOut(NpcDialogueGraph.Node node, boolean value) {
        if (node != null && node.type == NpcDialogueGraph.CUTSCENE) {
            ((CutsceneFadeNodeAccess263) (Object) node).settledlands$setFadeOut(value);
        }
    }

    public static void setFadeIn(NpcDialogueGraph.Node node, boolean value) {
        if (node != null && node.type == NpcDialogueGraph.CUTSCENE) {
            ((CutsceneFadeNodeAccess263) (Object) node).settledlands$setFadeIn(value);
        }
    }

    public static void copy(NpcDialogueGraph.Node source, NpcDialogueGraph.Node target) {
        if (source == null || target == null || source.type != NpcDialogueGraph.CUTSCENE
                || target.type != NpcDialogueGraph.CUTSCENE) return;
        setFadeOut(target, fadeOut(source));
        setFadeIn(target, fadeIn(source));
    }

    public static void save(NpcDialogueGraph graph, CompoundTag root) {
        if (graph == null || root == null || !root.contains("Nodes")) return;
        ListTag savedNodes = root.getList("Nodes", 10);
        for (int i = 0; i < savedNodes.size(); i++) {
            CompoundTag saved = savedNodes.getCompound(i);
            if (saved.getInt("Type") != NpcDialogueGraph.CUTSCENE) continue;
            NpcDialogueGraph.Node node = graph.get(saved.getInt("Id"));
            if (fadeOut(node)) saved.putBoolean(FADE_OUT_KEY, true);
            if (fadeIn(node)) saved.putBoolean(FADE_IN_KEY, true);
        }
    }

    public static void load(NpcDialogueGraph graph, CompoundTag root) {
        if (graph == null || root == null || !root.contains("Nodes")) return;
        ListTag savedNodes = root.getList("Nodes", 10);
        for (int i = 0; i < savedNodes.size(); i++) {
            CompoundTag saved = savedNodes.getCompound(i);
            if (saved.getInt("Type") != NpcDialogueGraph.CUTSCENE) continue;
            NpcDialogueGraph.Node node = graph.get(saved.getInt("Id"));
            setFadeOut(node, saved.getBoolean(FADE_OUT_KEY));
            setFadeIn(node, saved.getBoolean(FADE_IN_KEY));
        }
    }
}
