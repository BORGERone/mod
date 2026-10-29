import dev.settledlands.npc.*;
import dev.settledlands.npc.client.*;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

public class GraphUiTest {
    static void check(boolean v, String msg) {
        if (!v) throw new AssertionError(msg);
    }

    static boolean click(NpcDialogueGraphScreen s, NpcDialogueGraph.Node n, int x, int y, int button) {
        return NpcStatsGraphUi.mouseClicked(s, s.canvas.screenX(n.x + x), s.canvas.screenY(n.y + y), button);
    }

    public static void main(String[] args) {
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = g.add(0, 0, 0);
        g.entry = line.id;
        NpcGraphCanvas c = new NpcGraphCanvas();
        c.bounds(0, 0, 800, 500);
        c.panX = c.panY = 0;
        c.zoom = 1;
        NpcDialogueGraphScreen s = new NpcDialogueGraphScreen(g, c);
        NpcStatsGraphUi.ensure(s);
        NpcDialogueGraph.Node n = NpcStatsGraph.find(g);
        check(s.changed == 1 && n != null && n.x == -360 && n.y == 0, "ensure left of entry");
        c.focus(n);

        check(c.hit(g, c.screenX(n.x + 290), c.screenY(n.y + 165)) == n.id, "large hit height 170");
        check(c.hit(g, c.screenX(n.x + 290), c.screenY(n.y + 175)) == -1, "outside height 170");
        check(c.hitPort(g, c.screenX(n.x + 220), c.screenY(n.y + 50), true) == -1, "no port");
        check(click(s, n, 260, 38, 0) && n.varValue == 11 && s.remembered == 1 && s.changed == 2, "health plus");
        check(click(s, n, 190, 72, 0) && n.checkRadius == 9 && s.remembered == 2, "damage minus");
        check(click(s, n, 260, 106, 0) && n.checkCooldown == 11, "speed plus");

        n.varValue = 100;
        int saved = s.changed;
        check(click(s, n, 260, 38, 0) && n.varValue == 100 && s.changed == saved, "max bound");
        n.varValue = 1;
        check(click(s, n, 190, 38, 0) && n.varValue == 1 && s.changed == saved, "min bound");

        // Build 254 #5: bottom Randomize button click & deterministic Randomize checks
        n.varValue = 50;
        n.checkRadius = 50;
        n.checkCooldown = 50;
        int remBefore = s.remembered;
        int chgBefore = s.changed;
        boolean changedOnce = NpcStatsGraphUi.randomize(s, n, new Random(7L));
        check(changedOnce && s.remembered == remBefore + 1 && s.changed == chgBefore + 1, "randomize remembers and marks changed");
        check(Math.abs(n.varValue - 50) <= 2 && Math.abs(n.checkRadius - 50) <= 2 && Math.abs(n.checkCooldown - 50) <= 2, "randomize within [-2,+2]");

        // Zero-delta (0, 0, 0) roll must NOT pollute undo history
        Random zeroRng = new Random() {
            @Override
            public int nextInt(int bound) {
                if (bound != 5) throw new AssertionError("expected bound 5, got " + bound);
                return 2; // 2 - 2 == 0
            }
        };
        int h0 = n.varValue, d0 = n.checkRadius, sp0 = n.checkCooldown;
        int rem0 = s.remembered, chg0 = s.changed;
        check(!NpcStatsGraphUi.randomize(s, n, zeroRng), "zero roll returns false");
        check(n.varValue == h0 && n.checkRadius == d0 && n.checkCooldown == sp0 && s.remembered == rem0 && s.changed == chg0, "zero roll leaves stats & undo untouched");

        // Actual mouse click on bottom Randomize button (145, 145) inside [12..278) x [134..156)
        int origBefore = s.original;
        for (int attempt = 0; attempt < 10; attempt++) {
            check(click(s, n, 145, 145, 0), "randomize button click consumed");
        }
        check(s.original == origBefore, "randomize button does not start node drag");

        n.varValue = 1;
        check(click(s, n, 10, 10, 1) && s.original == 0, "right click protected");
        check(click(s, n, 10, 10, 0) && s.original == 1 && s.hidden == 1, "drag delegates");
        s.menu = true;
        check(click(s, n, 260, 38, 0) && n.varValue == 1 && s.original == 2, "menu priority");
        check(click(s, n, 145, 145, 0) && n.varValue == 1 && s.original == 3, "menu blocks randomize");
        s.menu = false;

        // Build 254 #2: draw three rows + bottom Randomize button with ZERO shadow/Z-offset text
        GuiGraphics gfx = new GuiGraphics();
        NpcStatsGraphUi.draw(s, gfx, n);
        check(gfx.rectangles == 13 && gfx.words.size() == 14 && gfx.pose().depth == 0,
                "draw three rows + randomize: rects=" + gfx.rectangles + " words=" + gfx.words.size());
        check(gfx.shadowCalls == 0 && gfx.centeredCalls == 0,
                "no dropShadow/Z+0.03f leak: shadow=" + gfx.shadowCalls + " centered=" + gfx.centeredCalls);
        for (double tz : gfx.textMaxZ) {
            check(tz == 0.0, "text Z must stay at 0.0 so overlapping windows cover it: " + tz);
        }
        check(gfx.words.contains("gui.settledlands.stats.randomize"), "randomize label drawn");

        NpcStatsGraphUi.draw(s, gfx, line);
        check(s.drawOriginal == 1, "ordinary drawing unchanged");
        c.focus(n);
        check(Math.abs(c.screenX(n.x + 150) - 400) < 0.001 && Math.abs(c.screenY(n.y + 85) - 250) < 0.001, "focus center of 300x170");
        c.frameAll(g);
        check(c.zoom > 0 && c.zoom <= 1 && c.visible(line) && c.visible(n), "frame all");
        System.out.println("Real canvas and UI spinners, zero-shadow Z, randomize button, drawing, selection, limits: PASS");
    }
}
