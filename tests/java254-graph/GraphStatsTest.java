import dev.settledlands.npc.*;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;

public class GraphStatsTest {
    static void check(boolean x, String s) {
        if (!x) throw new AssertionError(s);
    }

    public static void main(String[] args) {
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = g.add(0, 30, 40);
        g.entry = line.id;
        g.enabled = true;
        check(NpcStatsGraph.health(null) == 10 && NpcStatsGraph.damage(null) == 10 && NpcStatsGraph.speed(null) == 10, "legacy defaults");
        check(NpcStatsGraph.clamp(-1) == 1 && NpcStatsGraph.clamp(101) == 100, "stat bounds");
        check(NpcStatsGraph.ensure(g), "created node");
        NpcDialogueGraph.Node n = NpcStatsGraph.find(g);
        check(n != null && n.x == -330 && n.y == 40 && n.varValue == 10 && n.checkRadius == 10 && n.checkCooldown == 10,
                "position left of entry and defaults: x=" + (n == null ? "null" : n.x) + " y=" + (n == null ? "null" : n.y));
        check(n.x + 300 < line.x, "strictly left of initial line");
        check(g.error().isEmpty(), "valid graph " + g.error());
        check(!NpcStatsGraph.ensure(g) && g.add(16, 0, 0) == null, "one node only");
        check(!g.remove(n.id) && g.get(n.id) == n, "not deletable");
        check(!g.connect(n.id, line.id, 0) && !g.connect(line.id, n.id, 0), "no wires");

        // Build 254 #1: default fromLegacy layout ("Начальная реплика" at (0,0), "Прибытие #1" at (0,-240), "Прибытие #2" at (280,-240))
        NpcDialogueGraph legacy = new NpcDialogueGraph();
        NpcDialogueGraph.Node start = legacy.add(0, 0, 0);
        legacy.entry = start.id;
        NpcDialogueGraph.Node arr1 = legacy.add(0, 0, -240);
        arr1.special = 1;
        NpcDialogueGraph.Node arr2 = legacy.add(0, 280, -240);
        arr2.special = 2;
        check(NpcStatsGraph.ensure(legacy), "legacy ensure");
        NpcDialogueGraph.Node ln = NpcStatsGraph.find(legacy);
        check(ln != null && ln.x == -360 && ln.y == 0, "left of initial line (-360,0), not right of arrival 2 (568,-240)");

        // Fallback when entry == -1 and clamp near -32768
        NpcDialogueGraph noEntry = new NpcDialogueGraph();
        noEntry.entry = -1;
        NpcDialogueGraph.Node sp = noEntry.add(0, -500, -240);
        sp.special = 2;
        noEntry.add(0, 120, 55);
        noEntry.add(0, 400, -100);
        check(NpcStatsGraph.ensure(noEntry), "no-entry ensure");
        NpcDialogueGraph.Node ne = NpcStatsGraph.find(noEntry);
        check(ne != null && ne.x == -240 && ne.y == 55, "left of leftmost regular node");

        NpcDialogueGraph edge = new NpcDialogueGraph();
        NpcDialogueGraph.Node farLeft = edge.add(0, -32600, 15);
        edge.entry = farLeft.id;
        check(NpcStatsGraph.ensure(edge) && NpcStatsGraph.find(edge).x == -32768 && NpcStatsGraph.find(edge).y == 15, "coord clamped at -32768");

        // Build 254 #5: randomStep distribution [-2..+2] inclusive (including 0 = unchanged) + bounds
        Random rng = new Random(2542026L);
        int[] counts = new int[5];
        for (int i = 0; i < 2500; i++) {
            int next = NpcStatsGraph.randomStep(50, rng);
            int d = next - 50;
            check(d >= -2 && d <= 2, "radius [-2,+2] violated: " + d);
            counts[d + 2]++;
        }
        for (int k = 0; k < 5; k++) {
            check(counts[k] > 350, "missing delta " + (k - 2) + ": count=" + counts[k]);
        }
        for (int i = 0; i < 200; i++) {
            int low = NpcStatsGraph.randomStep(1, rng);
            int high = NpcStatsGraph.randomStep(100, rng);
            check(low >= 1 && low <= 3, "low clamp [1..3]: " + low);
            check(high >= 98 && high <= 100, "high clamp [98..100]: " + high);
        }

        n.varValue = 11;
        n.checkRadius = 9;
        n.checkCooldown = 13;
        NpcDialogueGraph copy = g.copy();
        check(NpcStatsGraph.health(copy) == 11 && NpcStatsGraph.damage(copy) == 9 && NpcStatsGraph.speed(copy) == 13, "copy snapshot");
        n.varValue = 12;
        check(NpcStatsGraph.health(copy) == 11, "copy independent");
        NpcDialogueGraph loaded = NpcDialogueGraph.load(copy.save());
        check(loaded != null && loaded.error().isEmpty() && NpcStatsGraph.health(loaded) == 11, "roundtrip");
        check(NpcStatsGraph.find(loaded).x == -330 && NpcStatsGraph.find(loaded).y == 40, "saved position preserved");

        CompoundTag bad = copy.save();
        bad.getList("Nodes", 10).getCompound(1).putInt("VarValue", 101);
        check(NpcDialogueGraph.load(bad) == null, "bad health rejected");
        bad = copy.save();
        bad.getList("Nodes", 10).getCompound(1).putInt("CheckRadius", 0);
        check(NpcDialogueGraph.load(bad) == null, "raw low radius rejected");
        bad = copy.save();
        bad.getList("Nodes", 10).getCompound(1).remove("CheckCooldown");
        check(NpcDialogueGraph.load(bad) == null, "missing stat rejected");
        bad = copy.save();
        bad.getList("Nodes", 10).getCompound(0).putInt("Next", n.id);
        check(NpcDialogueGraph.load(bad) == null, "incoming link rejected");
        bad = copy.save();
        bad.putInt("Entry", n.id);
        check(NpcDialogueGraph.load(bad) == null, "entry rejected");
        bad = copy.save();
        bad.getList("Nodes", 10).getCompound(1).putInt("Special", 1);
        check(NpcDialogueGraph.load(bad) == null, "special rejected");
        bad = copy.save();
        CompoundTag clone = copy.save().getList("Nodes", 10).getCompound(1);
        clone.putInt("Id", 99);
        bad.getList("Nodes", 10).add(clone);
        check(NpcDialogueGraph.load(bad) == null, "duplicate rejected");

        NpcDialogueGraph unfinished = new NpcDialogueGraph();
        unfinished.enabled = true;
        unfinished.entry = -1;
        check(NpcDialogueGraph.load(unfinished.save()) != null, "unfinished legacy loads");
        NpcDialogueGraph other = new NpcDialogueGraph();
        check(NpcStatsGraph.ensure(other) && NpcStatsGraph.health(other) == 10 && NpcStatsGraph.health(copy) == 11, "individual per NPC");
        NpcDialogueGraph full = new NpcDialogueGraph();
        for (int i = 0; i < 2048; i++) check(full.add(0, i, 0) != null, "legacy node " + i);
        check(full.add(0, 0, 0) == null && NpcStatsGraph.ensure(full) && full.nodes.size() == 2049, "reserved slot");
        check(full.error().isEmpty() && NpcDialogueGraph.load(full.save()) != null, "full plus stats roundtrip");
        check(full.add(0, 0, 0) == null && full.add(16, 0, 0) == null, "full graph limited");
        NpcDialogueGraph statsFirst = new NpcDialogueGraph();
        check(NpcStatsGraph.ensure(statsFirst), "stats first");
        for (int i = 0; i < 2048; i++) check(statsFirst.add(0, i, 0) != null, "ordinary " + i);
        check(statsFirst.nodes.size() == 2049 && statsFirst.add(0, 0, 0) == null && statsFirst.error().isEmpty(), "ordinary plus stat limit");
        NpcDialogueGraph oversized = new NpcDialogueGraph();
        for (int i = 0; i < 2048; i++) check(oversized.add(0, i, 0) != null, "ordinary-only " + i);
        oversized.nodes.put(2048, new NpcDialogueGraph.Node(2048, 0, 0, 0));
        check(!oversized.error().isEmpty() && NpcDialogueGraph.load(oversized.save()) == null, "ordinary-only overflow rejected");
        System.out.println("Real graph and NBT roundtrip, left-of-entry default, randomize step, singleton, security, 2048 limit: PASS");
    }
}
