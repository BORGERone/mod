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
        System.out.println("point hit: OK");
    }
}
