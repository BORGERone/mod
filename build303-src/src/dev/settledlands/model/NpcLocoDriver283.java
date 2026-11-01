package dev.settledlands.model;

import dev.settledlands.npc.GuideNpc;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.WalkAnimationState;

/**
 * Build 283: locomotion input for the Fresh Animations / Touch'n Motion NPC path.
 *
 * <p>The bridge fed the player rig the mob's own
 * {@code walkAnimation.position(partialTick)} and {@code speed(partialTick)}. That
 * state is advanced once per tick and its speed saturates at the mob cycle, so a
 * player-shaped rig got a stepped, speed-independent input: the limb cycle moved
 * in tick steps, the idle/walk weight jumped between ticks, and an NPC catching up
 * (up to 3x speed since Build 280) animated at the cadence of a strolling one.</p>
 *
 * <p>This driver replaces those two inputs with values taken from what is actually
 * drawn:</p>
 * <ul>
 *   <li>{@code phase} - the limb cycle, advanced by the distance the NPC covered
 *       since the last frame. At the reference speed this is exactly the vanilla
 *       one unit per tick; a slower NPC swings slower and a catching-up one swings
 *       faster, and because it is distance based the feet keep matching the ground
 *       instead of sliding;</li>
 *   <li>{@code amount} - the blend weight, the speed relative to the reference
 *       speed, following a short rise and a longer fall, so idle/walk/run
 *       cross-fades instead of snapping;</li>
 * </ul>
 *
 * <p>Movement is measured from the rendered position, not from
 * {@code getDeltaMovement()}: AI movement is not synced for mobs, so the client
 * delta is usually zero, while the interpolated position is exactly what the
 * player sees. Teleports (the Build 280 relocation) are ignored instead of being
 * turned into a stride.</p>
 *
 * <p>State is keyed by the {@link WalkAnimationState} instance the render code
 * already passes around. Nothing here touches the world, the server or
 * networking: it is read-only rendering input.</p>
 */
public final class NpcLocoDriver283 {

    /**
     * Blocks per <b>second</b> that count as a full walk for a player-shaped rig: a player on foot
     * covers 4.317 blocks a second. Build 316: this used to be 0.24, which is blocks per tick -
     * the measured speed is blocks per second, so every NPC was 20x over the full walk. The blend
     * weight was pinned at 1 and the limbs cycled eight times too fast, which is why the legs
     * moved on their own regardless of how fast the body went.
     */
    public static final double FULL_SPEED = 4.317D;

    /**
     * Limb units per block travelled. At {@link #FULL_SPEED} the phase advances one vanilla unit
     * per tick (20 a second), so an ordinary walk looks like it always did and a slow walk swings
     * the legs slowly instead of at the same rate as a run.
     */
    private static final double UNITS_PER_BLOCK = 20.0D / FULL_SPEED;

    /** Step assumed for the very first frame of a part, before any timing is known. */
    private static final double FIRST_STEP_SECONDS = 1.0D / 60.0D;

    /** Time constants of the blend weight: quick to start walking, slower to settle. */
    private static final double RISE_SECONDS = 0.12D;
    private static final double FALL_SECONDS = 0.30D;

    /**
     * Build 314: time constant of the measured speed. The client position of a mob only moves
     * once a tick, so a per-frame measurement is a staircase - two frames of nothing, then a
     * jump of a fifth of a block. Feeding that into the limb phase moved the legs in twenty
     * steps a second, which is what the jerky walk was. The speed is smoothed instead, and the
     * phase is advanced by the smoothed value.
     */
    private static final double SPEED_SECONDS = 0.06D;

    /** Frames are never longer than this; a stall must not turn into a stride. */
    private static final double MAX_STEP_SECONDS = 0.25D;

    /** One step of one limb cycle of the vanilla player model, cos(limbSwing * 0.6662). */
    private static final float CYCLE = (float) (Math.PI * 2.0D / 0.6662D);

    /** The phase is wrapped here so it never loses precision on a long session. */
    private static final float WRAP = CYCLE * 1024.0f;

    /** A jump longer than this in one frame is a teleport, not a stride. */
    private static final double TELEPORT_DISTANCE = 4.0D;

    /** Longer gaps than this carry no usable speed reading. */
    private static final double MAX_GAP_SECONDS = 1.0D;

    private static final Map<WalkAnimationState, State> STATES = new WeakHashMap<>();

    private NpcLocoDriver283() {}

    private static final class State {
        float amount;
        float phase;
        double lastX;
        double lastZ;
        long nanos;
        boolean primed;
        /** Smoothed walking speed in blocks per second, Build 314. */
        double speed;
    }

    /**
     * Advances the locomotion state of one NPC to this frame. Cheap by design: a
     * couple of subtractions and one map lookup per rendered NPC per frame.
     */
    public static void observe(WalkAnimationState state, GuideNpc npc) {
        if (state == null || npc == null) return;
        long now = System.nanoTime();
        synchronized (STATES) {
            State s = STATES.get(state);
            if (s == null) {
                s = new State();
                STATES.put(state, s);
            }
            advance(s, npc, now);
        }
    }

    /**
     * Makes {@code alias} report the same frame values as {@code source}. The
     * bridge reads the mob state, but anything else that looks at the proxy's own
     * state during the same frame gets identical numbers instead of the tick-stepped
     * copy.
     */
    public static void alias(WalkAnimationState source, WalkAnimationState alias) {
        if (source == null || alias == null || source == alias) return;
        synchronized (STATES) {
            State s = STATES.get(source);
            if (s == null) return;
            STATES.put(alias, s);
        }
    }

    /** Limb phase for this frame, or {@code NaN} when the state was never observed. */
    public static float position(WalkAnimationState state) {
        if (state == null) return Float.NaN;
        synchronized (STATES) {
            State s = STATES.get(state);
            return s == null ? Float.NaN : s.phase;
        }
    }

    /** Blend weight (0 = idle, 1 = full speed) for this frame, or {@code NaN}. */
    public static float speed(WalkAnimationState state) {
        if (state == null) return Float.NaN;
        synchronized (STATES) {
            State s = STATES.get(state);
            return s == null ? Float.NaN : s.amount;
        }
    }

    private static void advance(State s, GuideNpc npc, long now) {
        double x = npc.getX();
        double z = npc.getZ();
        if (!s.primed || !Double.isFinite(x) || !Double.isFinite(z)) {
            s.primed = true;
            s.nanos = now;
            s.lastX = x;
            s.lastZ = z;
            return;
        }
        long elapsedNanos = now - s.nanos;
        if (elapsedNanos <= 0L) return;
        double dt = elapsedNanos / 1_000_000_000.0D;
        s.nanos = now;
        if (dt > MAX_GAP_SECONDS) {
            s.lastX = x;
            s.lastZ = z;
            return;
        }

        double dx = x - s.lastX;
        double dz = z - s.lastZ;
        s.lastX = x;
        s.lastZ = z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(distance) || distance > TELEPORT_DISTANCE) distance = 0.0D;

        double window = Math.min(dt, MAX_STEP_SECONDS);
        if (!npc.isAlive() || npc.isPassenger() || npc.isRemoved()) distance = 0.0D;
        // distance is blocks per frame, window is seconds: instant is blocks per second, the
        // same unit FULL_SPEED is in.
        double instant = window <= 0.0D ? 0.0D : distance / window;

        // Smooth the staircase away before anything uses it.
        double follow = 1.0D - Math.exp(-window / SPEED_SECONDS);
        s.speed += (instant - s.speed) * follow;
        if (!Double.isFinite(s.speed) || s.speed < 0.0D) s.speed = 0.0D;

        double ratio = s.speed / FULL_SPEED;
        float target = ratio <= 0.0D ? 0.0f : (float) Math.min(1.0D, ratio);

        double tau = target > s.amount ? RISE_SECONDS : FALL_SECONDS;
        double alpha = 1.0D - Math.exp(-window / tau);
        s.amount += (target - s.amount) * (float) alpha;
        if (s.amount < 0.0f) s.amount = 0.0f;
        if (s.amount > 1.0f) s.amount = 1.0f;

        // The stride follows the smoothed speed, so it keeps its rate through the tick steps
        // and eases out over a couple of tenths of a second instead of freezing mid-step.
        s.phase += (float) (s.speed * window * UNITS_PER_BLOCK);
        if (s.phase > WRAP || s.phase < 0.0f || !Float.isFinite(s.phase)) {
            s.phase = s.phase % WRAP;
            if (s.phase < 0.0f) s.phase += WRAP;
        }
    }
}
