import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * Build 358 probe: the three things a talking block does that no screenshot can prove.
 *
 * <p>The window itself is the stock screen and its line is the author's text - both are visible in
 * game. What is not visible is whether the camera arithmetic is the screen's own (a block is a point,
 * not a body: if the angles were transposed the head would turn the wrong way and the block would be
 * behind the author), whether the aim packet survives the trip (a marker the client cannot read is a
 * window with the camera left where it was), and whether the chain continues at the node after the
 * line rather than at the line itself (which would loop the same line forever).</p>
 *
 * <pre>
 *   AIM     -90.00 26.57 | 90.00 36.87 | 0.00 -9.46   in front, behind, to the side, all at the centre
 *   MARKER  round=same plain=null                     the aim packet carries the block, a plain view does not
 *   NEXT    line=ok end=-1 missing=-1 null=-1         after the line, at an end, at nothing, without a graph
 * </pre>
 */
public final class BlockTalkProbe {

    public static void main(String[] args) {
        // The three angles are the arithmetic of GuideDialogueScreen#face, pointed at a block centre:
        // a block at +X is straight ahead, one at -X is straight back, one at +Z is to the side.
        System.out.println("AIM " + angles(0.5D, 66.0D, 0.5D, 3, 64, 0)
                + " | " + angles(0.5D, 66.0D, 0.5D, -2, 64, 0)
                + " | " + angles(0.5D, 66.0D, 0.5D, 0, 66, 3));

        CompoundTag tag = BlockTalk358.marker(12, -64, 300);
        int[] back = BlockTalk358.readMarker(tag);
        String round = back == null ? "lost"
                : back[0] == 12 && back[1] == -64 && back[2] == 300 ? "same" : "changed";
        CompoundTag plain = new CompoundTag();
        plain.putInt("x", 1);
        String stray = BlockTalk358.readMarker(plain) == null ? "null" : "read";
        System.out.println("MARKER round=" + round + " plain=" + stray);

        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 10, 10);
        NpcDialogueGraph.Node after = graph.add(NpcDialogueGraph.TRIGGER, 10, 40);
        if (line == null || after == null) {
            System.out.println("NEXT line=failed");
            return;
        }
        line.text = "Привет";
        line.next = after.id;
        after.next = -1;
        graph.entry = line.id;

        String lineStep = BlockTalk358.next(graph, line.id) == after.id ? "ok" : "wrong";
        System.out.println("NEXT line=" + lineStep
                + " end=" + BlockTalk358.next(graph, after.id)
                + " missing=" + BlockTalk358.next(graph, 4242)
                + " null=" + BlockTalk358.next(null, 0));
    }

    private static String angles(double px, double eyeY, double pz, int bx, int by, int bz) {
        float[] angles = BlockTalk358.aimAngles(px, eyeY, pz, bx, by, bz);
        return String.format(Locale.ROOT, "%.2f %.2f", angles[0], angles[1]);
    }
}
