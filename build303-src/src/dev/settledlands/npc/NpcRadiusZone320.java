package dev.settledlands.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Build 320: the "В радиусе" node can show the zone it works in.
 *
 * <p>An author sets a radius in blocks and has no way to tell what twelve of them look like around
 * this NPC in this terrain. With the node's visibility toggle on, the server draws the circle that
 * the node measures in - a ring of particles exactly at that distance, refreshed twice a second.
 * It is an authoring aid, not a game mechanic: nothing about the check, the dialogue or the graph
 * changes, and the ring is only drawn while a player is close enough to see it.</p>
 *
 * <p>Where the flag lives: a graph node has no free field, so the toggle uses
 * {@code Node#timerRepeat}, a saved boolean that only the timer node's own branch ever reads. On a
 * "В радиусе" node nothing reads it, it survives save, load, copy, undo and the clipboard, and it
 * reaches the server with the rest of the graph on Ctrl+S. {@link #FLAG_NOTE} keeps that explained
 * next to the code that depends on it.</p>
 */
public final class NpcRadiusZone320 {

    /** The saved node flag that means "draw this node's zone". See the class comment. */
    public static final String FLAG_NOTE = "Node#timerRepeat on a radius node = draw the zone";

    /** How close a player has to be for the ring to be worth sending. */
    public static final double VIEW_RANGE = 48.0D;

    /** Points on the circle. Small enough to stay cheap, dense enough to read as a circle. */
    public static final int POINTS = 24;

    /** Refreshed every this many ticks - ten times a second is wasted on a static ring. */
    public static final int PERIOD_TICKS = 8;

    /** A radius larger than this is clamped: the cardinality of the check does not change. */
    public static final int MAX_RADIUS = 64;

    /** Height above the NPC's feet, so the ring is visible in grass and on slabs. */
    public static final double RING_LIFT = 0.12D;

    private NpcRadiusZone320() {
    }

    /** Reads and writes the saved flag on a node. */
    public static boolean visible(NpcDialogueGraph.Node node) {
        return node != null && node.timerRepeat;
    }

    public static void setVisible(NpcDialogueGraph.Node node, boolean on) {
        if (node != null) node.timerRepeat = on;
    }

    /** True for the one node type this build draws: "В радиусе". */
    public static boolean isRadiusNode(NpcDialogueGraph.Node node) {
        return node != null && node.type == NpcDialogueGraph.RADIUS;
    }

    /** Called once per server tick, from the NPC's AI step. */
    public static void tick(GuideNpc npc) {
        if (npc == null) return;
        Level level = npc.level();
        if (level == null || level.isClientSide) return;
        if (npc.tickCount % PERIOD_TICKS != 0) return;

        NpcProfile profile = npc.profile();
        if (profile == null) return;
        NpcDialogueGraph graph = profile.dialogueGraph;
        if (graph == null || graph.nodes == null || graph.nodes.isEmpty()) return;

        // Nobody near enough to see it: do not spend a packet.
        if (level.getNearestPlayer(npc, VIEW_RANGE) == null) return;

        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!isRadiusNode(node) || !visible(node)) continue;
            int radius = node.radius;
            if (radius < 1) continue;
            if (radius > MAX_RADIUS) radius = MAX_RADIUS;
            ring(level, npc, radius);
        }
    }

    private static void ring(Level level, GuideNpc npc, int radius) {
        if (!(level instanceof ServerLevel server)) return;
        double ox = npc.getX();
        double oy = npc.getY() + RING_LIFT;
        double oz = npc.getZ();
        for (int i = 0; i < POINTS; i++) {
            double angle = (Math.PI * 2.0D * i) / POINTS;
            double x = ox + Math.cos(angle) * radius;
            double z = oz + Math.sin(angle) * radius;
            server.sendParticles(ENCHANT(), x, oy, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Pulled out so the single particle reference sits in one place. */
    private static net.minecraft.core.particles.ParticleOptions ENCHANT() {
        return net.minecraft.core.particles.ParticleTypes.ENCHANT;
    }
}
