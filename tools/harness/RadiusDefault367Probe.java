import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.client.BlockMenu363;

/**
 * Build 367 probe: the two rules of this build, without a client, a world or a screen.
 *
 * <pre>
 *   ZONE  check=false radius=false timer=true line=true   a new radius node has its ring off
 *                                                        (and nothing else is touched)
 *   SAVED check=true                                      a node saved switched on keeps it
 *   WHEEL up=-1 down=1 none=0                             the wheel moves the window the stock way
 * </pre>
 */
public final class RadiusDefault367Probe {

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node check = graph.add(NpcDialogueGraph.CHECK, 10, 10);
        NpcDialogueGraph.Node radius = graph.add(NpcDialogueGraph.RADIUS, 20, 10);
        NpcDialogueGraph.Node timer = graph.add(NpcDialogueGraph.TIMER, 30, 10);
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 40, 10);
        // The rule the mixin calls on every node the graph creates.
        RadiusPass354.hideByDefault(check);
        RadiusPass354.hideByDefault(radius);
        RadiusPass354.hideByDefault(timer);
        RadiusPass354.hideByDefault(line);
        System.out.println("ZONE check=" + check.timerRepeat + " radius=" + radius.timerRepeat
                + " timer=" + timer.timerRepeat + " line=" + line.timerRepeat);

        // A node the author switched on before this build is not erased by it.
        NpcDialogueGraph.Node saved = graph.add(NpcDialogueGraph.CHECK, 50, 10);
        RadiusPass354.setVisible(saved, true);
        RadiusPass354.hideByDefault(null);
        System.out.println("SAVED check=" + saved.timerRepeat);

        System.out.println("WHEEL up=" + BlockMenu363.wheelStep(1.0D)
                + " down=" + BlockMenu363.wheelStep(-1.0D)
                + " none=" + BlockMenu363.wheelStep(0.0D));
    }
}
