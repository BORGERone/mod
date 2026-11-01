package dev.settledlands.model;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Build 315: a cross-fade between animation clips.
 *
 * <p>The clips are played by {@code BbModel.setupAnimRaw}, and when the graph switches from one
 * animation to another the new clip starts at its first frame and the pose is written into the
 * model in a single frame. That cut is what looks like a teleport between animations: the arm is
 * at 80 degrees and one frame later at -20.</p>
 *
 * <p>Every clip pose reaches the model through {@code NpcMotionBridge256.Pose#set(ModelPart)}, both
 * for a full-body clip and for a masked one, so the fade is applied there: the values about to be
 * written are blended with the values the part actually had on the previous frame, with a time
 * constant of {@link #FADE_SECONDS}. The part therefore walks from wherever it was to wherever the
 * clip wants it, instead of jumping.</p>
 *
 * <p>The same filter also removes the smaller jerks - a clip that restarts, a bone that was
 * overridden for one frame, the transition out of a masked animation. Nothing here reads the graph,
 * the network or the world: it is a nine-float filter per model part per frame.</p>
 */
public final class NpcPoseBlend315 {

    /** How long a part takes to reach a new pose. Short enough to stay snappy, long enough to see. */
    public static final double FADE_SECONDS = 0.09D;

    /** A frame longer than this is a stall or a teleport, not a pose to fade from. */
    private static final double MAX_STEP_SECONDS = 0.25D;

    private static final int SLOTS = 9;

    /** Part identity to its values on the previous frame. */
    private static final Map<ModelPart, float[]> PREVIOUS = new WeakHashMap<>();

    /** Part identity to the timestamp of that sample, in nanoseconds. */
    private static final Map<ModelPart, Long> WHEN = new WeakHashMap<>();

    private NpcPoseBlend315() {}

    /**
     * Blends the values a clip has just written into {@code part} with the ones it had before.
     * Called right after every pose write of the bridge.
     */
    public static void blend(ModelPart part) {
        if (part == null) return;
        long now = System.nanoTime();
        float[] current = read(part);
        synchronized (PREVIOUS) {
            float[] previous = PREVIOUS.get(part);
            Long stamp = WHEN.get(part);
            double dt = stamp == null ? 0.0D : (now - stamp.longValue()) / 1.0e9D;
            if (previous == null || !(dt > 0.0D) || dt > MAX_STEP_SECONDS) {
                PREVIOUS.put(part, current);
                WHEN.put(part, now);
                return;
            }
            double alpha = 1.0D - Math.exp(-dt / FADE_SECONDS);
            float[] blended = new float[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                blended[i] = (float) (previous[i] + (current[i] - previous[i]) * alpha);
            }
            write(part, blended);
            PREVIOUS.put(part, blended);
            WHEN.put(part, now);
        }
    }

    private static float[] read(ModelPart part) {
        return new float[] {
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale
        };
    }

    private static void write(ModelPart part, float[] values) {
        part.x = values[0];
        part.y = values[1];
        part.z = values[2];
        part.xRot = values[3];
        part.yRot = values[4];
        part.zRot = values[5];
        part.xScale = values[6];
        part.yScale = values[7];
        part.zScale = values[8];
    }
}
