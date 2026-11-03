import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Build 356 probe: does a radius node's visibility flag survive a graph round trip?
 *
 * <p>Run against the built jar and the compile-time NBT mirror, so it exercises the real
 * {@code NpcDialogueGraph#save} and {@code #load}. It prints the state of the flag before and after
 * the rule build 356's mixin applies, which is what the check script reads:</p>
 *
 * <pre>
 *   BASE    check=absent radius=absent timer=false     the hole: save drops the flag of a radius node
 *   DEFAULT check=true   radius=true                    and load fills in "true" - "включено" is back
 *   MIXIN   check=false  radius=false  timer=false      every radius node's tag now carries it
 *   LOAD    check=false  radius=false  timer=false      load honours it, no reader patch needed
 *   ON      check=true                                  and "on" comes back as "on", not as a default
 * </pre>
 */
public final class RadiusFlagProbe {

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node check = graph.add(NpcDialogueGraph.CHECK, 10, 20);
        NpcDialogueGraph.Node radius = graph.add(NpcDialogueGraph.RADIUS, 40, 20);
        NpcDialogueGraph.Node timer = graph.add(NpcDialogueGraph.TIMER, 70, 20);
        if (check == null || radius == null || timer == null) {
            System.out.println("FAIL add returned null");
            return;
        }
        if (NpcDialogueGraph.CHECK != 5 || NpcDialogueGraph.RADIUS != 15) {
            System.out.println("FAIL unexpected node type ids: CHECK=" + NpcDialogueGraph.CHECK
                    + " RADIUS=" + NpcDialogueGraph.RADIUS);
            return;
        }

        check.timerRepeat = false;
        radius.timerRepeat = false;
        timer.timerRepeat = false;

        CompoundTag tag = graph.save();
        System.out.println("BASE check=" + flag(tag, check) + " radius=" + flag(tag, radius)
                + " timer=" + flag(tag, timer));

        // The reported bug: the key is missing, so load hands back its default - "включено".
        NpcDialogueGraph dropped = NpcDialogueGraph.load(tag);
        if (dropped == null) {
            System.out.println("FAIL load returned null");
            return;
        }
        System.out.println("DEFAULT check=" + loadedFlag(dropped, check)
                + " radius=" + loadedFlag(dropped, radius));

        // Exactly what RadiusFlagSave356Mixin does at the end of save().
        write(tag, check);
        write(tag, radius);
        System.out.println("MIXIN check=" + flag(tag, check) + " radius=" + flag(tag, radius)
                + " timer=" + flag(tag, timer));

        NpcDialogueGraph loaded = NpcDialogueGraph.load(tag);
        if (loaded == null) {
            System.out.println("FAIL load returned null");
            return;
        }
        System.out.println("LOAD check=" + loadedFlag(loaded, check) + " radius=" + loadedFlag(loaded, radius)
                + " timer=" + loadedFlag(loaded, timer));

        // And the other way round: a graph that has the ring on must stay on.
        NpcDialogueGraph on = new NpcDialogueGraph();
        NpcDialogueGraph.Node lit = on.add(NpcDialogueGraph.CHECK, 10, 20);
        lit.timerRepeat = true;
        CompoundTag onTag = on.save();
        write(onTag, lit);
        NpcDialogueGraph onLoaded = NpcDialogueGraph.load(onTag);
        System.out.println("ON check=" + loadedFlag(onLoaded, lit));
    }

    private static void write(CompoundTag root, NpcDialogueGraph.Node node) {
        ListTag list = root.getList("Nodes", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (one != null && one.getInt("Id") == node.id
                    && (node.type == NpcDialogueGraph.CHECK
                        || node.type == NpcDialogueGraph.RADIUS)) {
                one.putBoolean("TimerRepeat", node.timerRepeat);
            }
        }
    }

    private static String flag(CompoundTag root, NpcDialogueGraph.Node node) {
        ListTag list = root.getList("Nodes", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (one != null && one.getInt("Id") == node.id) {
                return one.contains("TimerRepeat") ? one.getBoolean("TimerRepeat") ? "true" : "false"
                        : "absent";
            }
        }
        return "missing-node";
    }

    private static String loadedFlag(NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        NpcDialogueGraph.Node found = graph.nodes.get(node.id);
        if (found == null) return "missing-node";
        return found.timerRepeat ? "true" : "false";
    }
}
