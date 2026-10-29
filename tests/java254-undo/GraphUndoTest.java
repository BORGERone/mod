import dev.settledlands.npc.*;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcStatsGraphUi;
import java.lang.reflect.*;
import java.util.*;

public class GraphUndoTest {
    static void check(boolean v, String m) {
        if (!v) throw new AssertionError(m);
    }

    static Field f(Class<?> c, String n) throws Exception {
        Field x = c.getDeclaredField(n);
        x.setAccessible(true);
        return x;
    }

    static Method m(Class<?> c, String n, Class<?>... p) throws Exception {
        Method x = c.getDeclaredMethod(n, p);
        x.setAccessible(true);
        return x;
    }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("dev.settledlands.npc.client.NpcDialogueGraphScreen");
        Object s = c.getConstructor().newInstance();
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node start = g.add(0, 0, 0);
        g.entry = start.id;
        check(NpcStatsGraph.ensure(g), "create");
        NpcDialogueGraph.Node n = NpcStatsGraph.find(g);
        check(n != null && n.x == -360 && n.y == 0, "left of start");

        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.bounds(0, 0, 800, 500);
        canvas.zoom = 1;
        canvas.focus(n);
        f(c, "graph").set(s, g);
        f(c, "graphs").set(s, new NpcDialogueGraph[]{g, null, null});
        f(c, "canvas").set(s, canvas);
        f(c, "undo").set(s, new ArrayDeque<NpcDialogueGraph>());
        f(c, "redo").set(s, new ArrayDeque<NpcDialogueGraph>());
        f(c, "summaries").set(s, new HashMap<>());
        f(c, "selected").setInt(s, -1);
        f(c, "importError").set(s, "");

        Method click = c.getMethod("mouseClicked", double.class, double.class, int.class);
        Method history = m(c, "history", boolean.class);
        check((Boolean) click.invoke(s, canvas.screenX(n.x + 260), canvas.screenY(n.y + 38), 0) && NpcStatsGraph.health(g) == 11, "spinner");
        check(f(c, "dirty").getBoolean(s) && f(c, "revision").getInt(s) == 1, "dirty");
        history.invoke(s, false);
        g = (NpcDialogueGraph) f(c, "graph").get(s);
        check(NpcStatsGraph.health(g) == 10 && ((NpcDialogueGraph[]) f(c, "graphs").get(s))[0] == g, "real undo");
        history.invoke(s, true);
        g = (NpcDialogueGraph) f(c, "graph").get(s);
        check(NpcStatsGraph.health(g) == 11, "real redo");

        // Real undo/redo for Randomize!
        n = NpcStatsGraph.find(g);
        n.varValue = 25;
        n.checkRadius = 35;
        n.checkCooldown = 45;
        check(NpcStatsGraphUi.randomize((NpcDialogueGraphScreen) s, n, new Random(19L)), "randomize mutates");
        int rh = NpcStatsGraph.health(g), rd = NpcStatsGraph.damage(g), rs = NpcStatsGraph.speed(g);
        check(rh != 25 || rd != 35 || rs != 45, "randomized values differ");
        history.invoke(s, false);
        g = (NpcDialogueGraph) f(c, "graph").get(s);
        check(NpcStatsGraph.health(g) == 25 && NpcStatsGraph.damage(g) == 35 && NpcStatsGraph.speed(g) == 45, "real randomize undo");
        history.invoke(s, true);
        g = (NpcDialogueGraph) f(c, "graph").get(s);
        check(NpcStatsGraph.health(g) == rh && NpcStatsGraph.damage(g) == rd && NpcStatsGraph.speed(g) == rs, "real randomize redo");

        history.invoke(s, false);
        g = (NpcDialogueGraph) f(c, "graph").get(s);
        n = NpcStatsGraph.find(g);
        canvas.focus(n);
        click.invoke(s, canvas.screenX(n.x + 260), canvas.screenY(n.y + 38), 0);
        check(((Deque<?>) f(c, "redo").get(s)).isEmpty(), "new click clears redo");
        for (int i = 0; i < 30; i++) {
            click.invoke(s, canvas.screenX(n.x + 260), canvas.screenY(n.y + 38), 0);
        }
        check(((Deque<?>) f(c, "undo").get(s)).size() == 24, "history limit");

        NpcDialogueGraph full = new NpcDialogueGraph();
        check(NpcStatsGraph.ensure(full), "reserved");
        for (int i = 0; i < 2047; i++) check(full.add(0, i, 0) != null, "ordinary " + i);
        f(c, "graph").set(s, full);
        ((NpcDialogueGraph[]) f(c, "graphs").get(s))[0] = full;
        Method add = m(c, "addAt", int.class, double.class, double.class);
        check(add.invoke(s, 0, 0.0, 0.0) != null && full.nodes.size() == 2049, "editor last ordinary slot");
        check(add.invoke(s, 0, 0.0, 0.0) == null && full.nodes.size() == 2049, "editor overflow");
        NpcDialogueGraph oldFull = new NpcDialogueGraph();
        for (int i = 0; i < 2048; i++) check(oldFull.add(0, i, 0) != null, "legacy " + i);
        f(c, "graph").set(s, oldFull);
        ((NpcDialogueGraph[]) f(c, "graphs").get(s))[0] = oldFull;
        check(add.invoke(s, 0, 0.0, 0.0) == null && oldFull.nodes.size() == 2048, "legacy limit");
        System.out.println("Real spinner & randomize, screen undo/redo and editor size limit: PASS");
    }
}
