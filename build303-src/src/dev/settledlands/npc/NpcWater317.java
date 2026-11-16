package dev.settledlands.npc;

import net.minecraft.world.phys.Vec3;

/**
 * Build 317: a guide NPC is never hurt by a fall, never drowns, and floats.
 *
 * <p>Until now a guide was an ordinary living entity in the two places that matter:</p>
 *
 * <ul>
 *   <li>Walking off a ledge, being pushed out of a high window or riding a lift down let
 *       {@code fallDistance} grow, and the vanilla landing hurt the NPC for it. A guide that
 *       follows a player along a cliff path could be knocked down or killed by the terrain
 *       alone.</li>
 *   <li>Water was no different. The air supply drained while the head was under, and once it ran
 *       out vanilla applied drowning damage every tick. The body also sinks, because nothing ever
 *       pushes a mob up while it is not actively swimming, so the NPC came to rest on the bottom
 *       of the pond and stayed there.</li>
 * </ul>
 *
 * <p>Falling is handled at its source: the distance is held at {@link #FALL_GUARD}, which is
 * below zero on purpose. The landing reads whatever the last tick of movement added to it, and a
 * single tick can add at most one terminal-velocity step - a little under four blocks - so the
 * value the landing sees is still negative and the three block threshold that turns a fall into
 * damage is never reached. Holding it at zero would not do: the movement of the landing tick
 * still adds to it before the check, and a long free fall adds close to four blocks in that one
 * tick.</p>
 *
 * <p>Water is handled in the two ways it hurts and holds a mob. The air supply is refilled while
 * the body is in water, which is the state vanilla drowns from, so the drowning tick never
 * happens at all. The body is then given a small constant push upwards so it comes up to the
 * surface instead of settling on the bottom; the push is capped at {@link #RISE_CAP}, about a
 * block a second, so the NPC rises, reaches the surface and only bobs there. It is not a
 * teleport, and it does not fight the navigation, which only writes the horizontal part of the
 * movement.</p>
 */
public final class NpcWater317 {

    /** Upward speed added per tick while the body is in water, in blocks per tick. */
    public static final double BUOYANCY = 0.060D;

    /** The fastest the water may ever carry the NPC up, in blocks per tick. */
    public static final double RISE_CAP = 0.060D;

    /**
     * The value {@code fallDistance} is held at every tick, in blocks. Negative by design - see
     * the class comment - and far enough below the three block threshold that a whole landing
     * tick cannot raise it back over.
     */
    public static final float FALL_GUARD = -8.0F;

    private NpcWater317() {
    }

    /** Called once per server tick, before the NPC's own AI step. */
    public static void tick(GuideNpc npc) {
        if (npc == null) return;

        npc.fallDistance = FALL_GUARD;

        if (!npc.isInWater()) return;

        int maxAir = npc.getMaxAirSupply();
        if (npc.getAirSupply() < maxAir) npc.setAirSupply(maxAir);

        Vec3 motion = npc.getDeltaMovement();
        if (motion == null) return;
        double rise = motion.y + BUOYANCY;
        if (rise > RISE_CAP) rise = RISE_CAP;
        if (rise > motion.y) npc.setDeltaMovement(motion.x, rise, motion.z);
    }
}
