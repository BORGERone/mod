import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcWirePointsUi321;

public final class PointHitTest {
    public static void main(String[] args) {
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        for (double zoom : new double[] { 0.05, 0.4, 1.0, 2.0 }) {
            canvas.zoom = zoom;
            double radius = NpcWirePointsUi321.hitRadius(canvas);
            if (radius < 9.0) {
                System.out.println("point hit too small at zoom " + zoom + ": " + radius);
                System.exit(1);
            }
            int half = NpcWirePointsUi321.handleHalf(canvas);
            if (radius < half * 2 + 2) {
                System.out.println("points are drawn wider than they can be grabbed: " + radius);
                System.exit(1);
            }
        }
        // A wire that runs through a point is clicked where it is drawn, not where the straight
        // cable between its ports would be: that is where the next point gets made.
        canvas.zoom = 1.0;
        NpcDialogueGraph graph = new NpcDialogueGraph();
        Node from = graph.add(NpcDialogueGraph.LINE, 0, 0);
        Node to = graph.add(NpcDialogueGraph.LINE, 400, 200);
        from.next = to.id;
        NpcWirePoints321.addBend(from, 0, to.id, 304, 400);
        int[] segment = new int[1];
        double onBend = NpcWirePointsUi321.linkDistance(canvas, graph, from, 0, to.id,
                canvas.screenX(304), canvas.screenY(400), segment);
        if (onBend > 2.0) {
            System.out.println("a point on a bent wire is not on the wire: " + onBend);
            System.exit(1);
        }
        if (segment[0] < 0 || segment[0] > 1) {
            System.out.println("the segment under the point is wrong: " + segment[0]);
            System.exit(1);
        }
        double onRoute = NpcWirePointsUi321.linkDistance(canvas, graph, from, 0, to.id,
                canvas.screenX(150), canvas.screenY(200), segment);
        if (onRoute <= 4.0) {
            System.out.println("a place well off the bent route reads as on it: " + onRoute);
            System.exit(1);
        }
        System.out.println("point hit: OK");
    }
}
