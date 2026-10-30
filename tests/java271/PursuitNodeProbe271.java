import dev.settledlands.npc.client.PursuitGraphNodes271;

public final class PursuitNodeProbe271 {
    private static void eq(Object actual, Object expected, String label) {
        if (!java.util.Objects.equals(actual, expected))
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) {
        eq(PursuitGraphNodes271.actionForMenuRow(16), "trigger:pursuit:start:npc:", "NPC start row");
        eq(PursuitGraphNodes271.actionForMenuRow(17), "trigger:pursuit:start:player:", "player start row");
        eq(PursuitGraphNodes271.actionForMenuRow(18), "trigger:pursuit:stop", "stop row");
        eq(PursuitGraphNodes271.actionForMenuRow(15), null, "last original row retained");
        eq(PursuitGraphNodes271.actionForMenuRow(19), null, "past menu range rejected");
        eq(PursuitGraphNodes271.labelKeyForAction("trigger:pursuit:start:npc:guide_01"),
                "gui.settledlands.graph.pursuit_node_start_npc", "NPC node title");
        eq(PursuitGraphNodes271.labelKeyForAction("trigger:pursuit:start:player:PlayerName"),
                "gui.settledlands.graph.pursuit_node_start_player", "player node title");
        eq(PursuitGraphNodes271.labelKeyForAction("trigger:pursuit:stop"),
                "gui.settledlands.graph.pursuit_node_stop", "stop node title");
        eq(PursuitGraphNodes271.labelKeyForAction("trigger:ordinary"), null, "ordinary trigger unchanged");
        System.out.println("PURSUIT NODE 271 FIXTURE: PASS (three dedicated menu entries map to distinct graph actions and labels)");
    }
}
