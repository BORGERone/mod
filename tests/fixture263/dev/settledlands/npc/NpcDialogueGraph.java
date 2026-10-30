package dev.settledlands.npc;

import java.util.HashMap;
import java.util.Map;

public final class NpcDialogueGraph {
    public static final int CUTSCENE = 14;
    private final Map<Integer, Node> nodes = new HashMap<>();
    public Node add(int id, int type) {
        Node node = new Node(id, type);
        nodes.put(id, node);
        return node;
    }
    public Node get(int id) { return nodes.get(id); }

    public static final class Node implements CutsceneFadeNodeAccess263 {
        public final int id;
        public int type;
        public int next = -1;
        public String cutsceneName = "";
        // Legacy animation/movement fields must remain independent from fade options.
        public boolean animLoop;
        public boolean moveEscort;
        private boolean fadeOut;
        private boolean fadeIn;
        public Node(int id, int type) { this.id = id; this.type = type; }
        @Override public boolean settledlands$getFadeOut() { return fadeOut; }
        @Override public void settledlands$setFadeOut(boolean value) { fadeOut = value; }
        @Override public boolean settledlands$getFadeIn() { return fadeIn; }
        @Override public void settledlands$setFadeIn(boolean value) { fadeIn = value; }
    }
}
