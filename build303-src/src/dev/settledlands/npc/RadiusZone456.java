package dev.settledlands.npc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Build 456: Square vs Circular zone shape for radius nodes ({@link NpcDialogueGraph#CHECK} "В радиусе"
 * and {@link NpcDialogueGraph#RADIUS} "Цель") across NPC, block, door, chest, and trial graphs.
 *
 * <p>Stores the boolean zone-shape flag directly on {@link NpcDialogueGraph.Node} via
 * {@link ZoneShapeHost} (with WeakHashMap fallback), evaluates exact Chebyshev box containment
 * {@code |dx| <= r && |dy| <= r && |dz| <= r} matching the square particle outline rendered in
 * the world, and provides lightweight frame/context holders kept outside {@code dev.settledlands.mixin}.</p>
 */
public final class RadiusZone456 {

    public static final String NBT_SQUARE_ZONE = "SquareZone456";

    private static final double RING_LIFT = 0.12D;

    public interface ZoneShapeHost {
        boolean settledlands$isSquareZone456();

        void settledlands$setSquareZone456(boolean square);
    }

    /** Lightweight pass context for {@code RadiusPass354} &amp; {@code GuideDialogue} radius checks. */
    public static final class CheckZoneCtx {
        public final boolean square;
        public final double radius;

        public CheckZoneCtx(boolean square, double radius) {
            this.square = square;
            this.radius = radius;
        }
    }

    /** Lightweight state carrier for {@code GuideDialogue.expandRadiusAttachments} / {@code initiatorExecutesWithRadiusGate}. */
    public static final class AttachZoneState {
        public NpcDialogueGraph.Node currentNode;
        public double radius;
    }

    private static final Map<NpcDialogueGraph.Node, Boolean> FALLBACK_SQUARE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RadiusZone456() {}

    public static boolean isSquare(NpcDialogueGraph.Node node) {
        if (node == null || !RadiusPass354.isRadiusNode(node)) return false;
        Object raw = node;
        if (raw instanceof ZoneShapeHost host) {
            return host.settledlands$isSquareZone456();
        }
        return Boolean.TRUE.equals(FALLBACK_SQUARE.get(node));
    }

    public static void setSquare(NpcDialogueGraph.Node node, boolean square) {
        if (node == null) return;
        Object raw = node;
        if (raw instanceof ZoneShapeHost host) {
            host.settledlands$setSquareZone456(square);
        } else if (square) {
            FALLBACK_SQUARE.put(node, Boolean.TRUE);
        } else {
            FALLBACK_SQUARE.remove(node);
        }
    }

    public static boolean isInsideBox(double ox, double oy, double oz,
            double px, double py, double pz, double radius) {
        return Math.abs(px - ox) <= radius
                && Math.abs(py - oy) <= radius
                && Math.abs(pz - oz) <= radius;
    }

    public static boolean isWithinNpcZone(Entity origin, Entity target, double radius,
            boolean square) {
        if (origin == null || target == null) return false;
        if (!square) {
            return origin.distanceToSqr(target) <= radius * radius;
        }
        return isInsideBox(origin.getX(), origin.getY(), origin.getZ(),
                target.getX(), target.getY(), target.getZ(), radius);
    }

    public static boolean isWithinVecZone(Vec3 origin, Entity target, double radius,
            boolean square) {
        if (origin == null || target == null) return false;
        if (!square) {
            return target.distanceToSqr(origin.x, origin.y, origin.z) <= radius * radius;
        }
        return isInsideBox(origin.x, origin.y, origin.z,
                target.getX(), target.getY(), target.getZ(), radius);
    }

    /**
     * Renders a crisp square particle border around {@code (cx, y + RING_LIFT, cz)} with half-extent
     * {@code r} blocks so corners and edges of the square zone are immediately readable in world space.
     */
    public static void drawSquareOutline(ServerLevel level, double cx, double y, double cz,
            int r, long gameTime) {
        if (level == null) return;
        int clamped = Math.max(1, Math.min(96, r));
        double py = y + RING_LIFT;
        double minX = cx - clamped;
        double maxX = cx + clamped;
        double minZ = cz - clamped;
        double maxZ = cz + clamped;

        // 4 anchored corner markers so the square footprint always reads cleanly at any size
        spark(level, minX, py, minZ);
        spark(level, maxX, py, minZ);
        spark(level, maxX, py, maxZ);
        spark(level, minX, py, maxZ);

        // 3 smoothly sliding particles along each of the 4 edges (12 edge sparks + 4 corners = 16 total)
        double phase = ((double) (gameTime / 8L) * 0.14D) % 1.0D;
        double span = clamped * 2.0D;
        for (int i = 0; i < 3; i++) {
            double t = (phase + (double) i / 3.0D) % 1.0D;
            double d = t * span;
            spark(level, minX + d, py, minZ); // north edge: W -> E
            spark(level, maxX, py, minZ + d); // east edge:  N -> S
            spark(level, maxX - d, py, maxZ); // south edge: E -> W
            spark(level, minX, py, maxZ - d); // west edge:  S -> N
        }
    }

    private static void spark(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static String shapeName(NpcDialogueGraph.Node node) {
        return Component.translatable(isSquare(node)
                ? "gui.settledlands.radius_shape_square_short"
                : "gui.settledlands.radius_shape_circle_short").getString();
    }
}
