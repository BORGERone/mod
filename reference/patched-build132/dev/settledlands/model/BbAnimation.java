package dev.settledlands.model;

import java.util.*;

public final class BbAnimation {
    public static record Keyframe(
        float time,
        float x,
        float y,
        float z,
        String interpolation,
        String easing,
        float[] bezierLeftTime,
        float[] bezierLeftValue,
        float[] bezierRightTime,
        float[] bezierRightValue
    ) {
        public Keyframe(float time, float x, float y, float z, String interpolation) {
            this(time, x, y, z, interpolation, "", null, null, null, null);
        }

        public boolean hasBezier() {
            return bezierRightValue != null || bezierLeftValue != null || "bezier".equalsIgnoreCase(interpolation);
        }
    }

    private final String name;
    private final float length;
    private final String loop;
    private final Map<String, List<Keyframe>> channels;
    private final Set<String> animatedBones;

    public BbAnimation(String name, float length, String loop, Map<String, List<Keyframe>> channels) {
        this.name = name;
        this.length = Math.max(0.05f, length);
        this.loop = loop != null ? loop.toLowerCase(Locale.ROOT) : "loop";
        this.channels = channels != null ? channels : Collections.emptyMap();

        Set<String> bones = new LinkedHashSet<>();
        for (String key : this.channels.keySet()) {
            int slash = key.indexOf('/');
            if (slash > 0) {
                bones.add(key.substring(0, slash).toLowerCase(Locale.ROOT));
            }
        }
        this.animatedBones = Collections.unmodifiableSet(bones);
    }

    public String getName() {
        return name;
    }

    public float getLength() {
        return length;
    }

    public String getLoop() {
        return loop;
    }

    public Set<String> getAnimatedBones() {
        return animatedBones;
    }

    public boolean hasBoneChannel(String bone, String channel) {
        String key = (bone + "/" + channel).toLowerCase(Locale.ROOT);
        List<Keyframe> list = channels.get(key);
        return list != null && !list.isEmpty();
    }

    public float[] sample(float timeSeconds, String bone, String channel) {
        String key = (bone + "/" + channel).toLowerCase(Locale.ROOT);
        List<Keyframe> list = channels.get(key);
        if (list == null || list.isEmpty()) return null;

        float t = timeSeconds;
        if ("loop".equals(loop)) {
            t = t % length;
            if (t < 0.0f) t += length;
        } else if ("once".equals(loop)) {
            t = Math.min(length, Math.max(0.0f, t));
        }

        if (list.size() == 1 || t <= list.get(0).time) {
            Keyframe k = list.get(0);
            return new float[]{k.x, k.y, k.z};
        }
        if (t >= list.get(list.size() - 1).time) {
            Keyframe k = list.get(list.size() - 1);
            return new float[]{k.x, k.y, k.z};
        }

        // Search for bracket [k0, k1]
        for (int i = 0; i < list.size() - 1; i++) {
            Keyframe k0 = list.get(i);
            Keyframe k1 = list.get(i + 1);
            if (t >= k0.time && t <= k1.time) {
                float span = k1.time - k0.time;
                if (span <= 1e-5f) {
                    return new float[]{k0.x, k0.y, k0.z};
                }

                // 1. Step interpolation
                if ("step".equalsIgnoreCase(k0.interpolation)) {
                    return new float[]{k0.x, k0.y, k0.z};
                }

                // 2. Easing curve (if easing property is explicitly specified on either keyframe)
                String easingName = (k1.easing != null && !k1.easing.isBlank() && !k1.easing.equalsIgnoreCase("linear")) ? k1.easing
                                  : ((k0.easing != null && !k0.easing.isBlank() && !k0.easing.equalsIgnoreCase("linear")) ? k0.easing : "");
                if (!easingName.isEmpty()) {
                    float u = (t - k0.time) / span;
                    float alpha = evalEasing(easingName, u);
                    float rx = k0.x + alpha * (k1.x - k0.x);
                    float ry = k0.y + alpha * (k1.y - k0.y);
                    float rz = k0.z + alpha * (k1.z - k0.z);
                    return new float[]{rx, ry, rz};
                }

                // 3. Bezier curve interpolation (when interpolation is 'bezier' or explicit bezier handles)
                if ("bezier".equalsIgnoreCase(k0.interpolation) || "bezier".equalsIgnoreCase(k1.interpolation)) {
                    float rx = sampleBezierAxis(t, k0.time, k0.x, k1.time, k1.x, k0.bezierRightTime, k0.bezierRightValue, k1.bezierLeftTime, k1.bezierLeftValue, 0);
                    float ry = sampleBezierAxis(t, k0.time, k0.y, k1.time, k1.y, k0.bezierRightTime, k0.bezierRightValue, k1.bezierLeftTime, k1.bezierLeftValue, 1);
                    float rz = sampleBezierAxis(t, k0.time, k0.z, k1.time, k1.z, k0.bezierRightTime, k0.bezierRightValue, k1.bezierLeftTime, k1.bezierLeftValue, 2);
                    return new float[]{rx, ry, rz};
                }

                // 4. Catmull-Rom spline interpolation
                if ("catmullrom".equalsIgnoreCase(k0.interpolation) || "catmullrom".equalsIgnoreCase(k1.interpolation)) {
                    Keyframe p0 = i > 0 ? list.get(i - 1) : k0;
                    Keyframe p1 = k0;
                    Keyframe p2 = k1;
                    Keyframe p3 = (i + 2 < list.size()) ? list.get(i + 2) : k1;
                    float u = (t - k0.time) / span;
                    float rx = sampleCatmullRom(u, p0.x, p1.x, p2.x, p3.x);
                    float ry = sampleCatmullRom(u, p0.y, p1.y, p2.y, p3.y);
                    float rz = sampleCatmullRom(u, p0.z, p1.z, p2.z, p3.z);
                    return new float[]{rx, ry, rz};
                }

                // 5. Smooth / Smoothstep or standard Linear
                float u = (t - k0.time) / span;
                float alpha = ("smooth".equalsIgnoreCase(k0.interpolation) || "smoothstep".equalsIgnoreCase(k0.interpolation))
                    ? (u * u * (3.0f - 2.0f * u))
                    : u;

                float rx = k0.x + alpha * (k1.x - k0.x);
                float ry = k0.y + alpha * (k1.y - k0.y);
                float rz = k0.z + alpha * (k1.z - k0.z);
                return new float[]{rx, ry, rz};
            }
        }

        Keyframe last = list.get(list.size() - 1);
        return new float[]{last.x, last.y, last.z};
    }

    private static float sampleCatmullRom(float u, float p0, float p1, float p2, float p3) {
        float u2 = u * u;
        float u3 = u2 * u;
        return 0.5f * (
            (2.0f * p1) +
            (-p0 + p2) * u +
            (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * u2 +
            (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * u3
        );
    }

    private static float sampleBezierAxis(
        float t,
        float t0, float v0,
        float t1, float v1,
        float[] rightTime, float[] rightVal,
        float[] leftTime, float[] leftVal,
        int axis
    ) {
        float spanT = t1 - t0;
        if (spanT <= 1e-5f) return v0;

        float dt0 = (rightTime != null && rightTime.length > axis) ? rightTime[axis] : (spanT / 3.0f);
        float dv0 = (rightVal != null && rightVal.length > axis) ? rightVal[axis] : 0.0f;

        float dt1 = (leftTime != null && leftTime.length > axis) ? leftTime[axis] : (-spanT / 3.0f);
        float dv1 = (leftVal != null && leftVal.length > axis) ? leftVal[axis] : 0.0f;

        // Relative handles from keyframes (matching Blockbench keyframe.js getBezierLerp)
        float p1_v = v0 + dv0;
        float p2_v = v1 + dv1;

        // Normalized time handles in [0, 1]
        float x1 = Math.max(0.0f, Math.min(1.0f, Math.abs(dt0) / spanT));
        float x2 = Math.max(0.0f, Math.min(1.0f, 1.0f - Math.abs(dt1) / spanT));

        float targetAlpha = Math.max(0.0f, Math.min(1.0f, (t - t0) / spanT));

        // Fast path for standard linear handles
        if (Math.abs(x1 - 1.0f / 3.0f) < 1e-4f && Math.abs(x2 - 2.0f / 3.0f) < 1e-4f
            && Math.abs(p1_v - v0) < 1e-4f && Math.abs(p2_v - v1) < 1e-4f) {
            return v0 + targetAlpha * (v1 - v0);
        }

        // Bisection search for u in [0, 1] such that X(u) == targetAlpha
        // (guaranteed monotonic convergence, no division by zero, no NaN)
        float low = 0.0f, high = 1.0f;
        for (int iter = 0; iter < 12; iter++) {
            float mid = (low + high) * 0.5f;
            float oneMinusM = 1.0f - mid;
            float xm = 3.0f * oneMinusM * oneMinusM * mid * x1 + 3.0f * oneMinusM * mid * mid * x2 + mid * mid * mid;
            if (xm < targetAlpha) {
                low = mid;
            } else {
                high = mid;
            }
        }
        float u = (low + high) * 0.5f;

        // Evaluate Y(u)
        float oneMinusU = 1.0f - u;
        float res = oneMinusU * oneMinusU * oneMinusU * v0
                  + 3.0f * oneMinusU * oneMinusU * u * p1_v
                  + 3.0f * oneMinusU * u * u * p2_v
                  + u * u * u * v1;

        return Float.isNaN(res) ? (v0 + targetAlpha * (v1 - v0)) : res;
    }

    public static float evalEasing(String easing, float t) {
        if (easing == null || easing.isBlank() || easing.equalsIgnoreCase("linear")) {
            return t;
        }
        String e = easing.toLowerCase(Locale.ROOT);
        return switch (e) {
            case "easeinsine" -> 1.0f - (float) Math.cos((t * Math.PI) / 2.0);
            case "easeoutsine" -> (float) Math.sin((t * Math.PI) / 2.0);
            case "easeinoutsine" -> -0.5f * ((float) Math.cos(Math.PI * t) - 1.0f);

            case "easeinquad" -> t * t;
            case "easeoutquad" -> 1.0f - (1.0f - t) * (1.0f - t);
            case "easeinoutquad" -> t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 2) / 2.0f;

            case "easeincubic" -> t * t * t;
            case "easeoutcubic" -> 1.0f - (float) Math.pow(1.0 - t, 3);
            case "easeinoutcubic" -> t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0f;

            case "easeinquart" -> t * t * t * t;
            case "easeoutquart" -> 1.0f - (float) Math.pow(1.0 - t, 4);
            case "easeinoutquart" -> t < 0.5f ? 8.0f * t * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 4) / 2.0f;

            case "easeinquint" -> t * t * t * t * t;
            case "easeoutquint" -> 1.0f - (float) Math.pow(1.0 - t, 5);
            case "easeinoutquint" -> t < 0.5f ? 16.0f * t * t * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 5) / 2.0f;

            case "easeinexpo" -> t == 0.0f ? 0.0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
            case "easeoutexpo" -> t == 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
            case "easeinoutexpo" -> t == 0.0f ? 0.0f : (t == 1.0f ? 1.0f : (t < 0.5f ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0f : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0f));

            case "easeincirc" -> 1.0f - (float) Math.sqrt(1.0 - Math.pow(t, 2));
            case "easeoutcirc" -> (float) Math.sqrt(1.0 - Math.pow(t - 1.0, 2));
            case "easeinoutcirc" -> t < 0.5f ? (1.0f - (float) Math.sqrt(1.0 - Math.pow(2.0 * t, 2))) / 2.0f : ((float) Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2)) + 1.0f) / 2.0f;

            case "easeinback" -> {
                final float c1 = 1.70158f;
                final float c3 = c1 + 1.0f;
                yield c3 * t * t * t - c1 * t * t;
            }
            case "easeoutback" -> {
                final float c1 = 1.70158f;
                final float c3 = c1 + 1.0f;
                yield 1.0f + c3 * (float) Math.pow(t - 1.0, 3) + c1 * (float) Math.pow(t - 1.0, 2);
            }
            case "easeinoutback" -> {
                final float c1 = 1.70158f;
                final float c2 = c1 * 1.525f;
                yield t < 0.5f
                    ? ((float) Math.pow(2.0 * t, 2) * ((c2 + 1.0f) * 2.0f * t - c2)) / 2.0f
                    : ((float) Math.pow(2.0 * t - 2.0, 2) * ((c2 + 1.0f) * (t * 2.0f - 2.0f) + c2) + 2.0f) / 2.0f;
            }

            case "easeinelastic" -> {
                final float c4 = (float) (2.0 * Math.PI) / 3.0f;
                yield t == 0.0f ? 0.0f : (t == 1.0f ? 1.0f : -(float) Math.pow(2.0, 10.0 * t - 10.0) * (float) Math.sin((t * 10.0f - 10.75f) * c4));
            }
            case "easeoutelastic" -> {
                final float c4 = (float) (2.0 * Math.PI) / 3.0f;
                yield t == 0.0f ? 0.0f : (t == 1.0f ? 1.0f : (float) Math.pow(2.0, -10.0 * t) * (float) Math.sin((t * 10.0f - 0.75f) * c4) + 1.0f);
            }
            case "easeinoutelastic" -> {
                final float c5 = (float) (2.0 * Math.PI) / 4.5f;
                yield t == 0.0f ? 0.0f : (t == 1.0f ? 1.0f : (t < 0.5f
                    ? -((float) Math.pow(2.0, 20.0 * t - 10.0) * (float) Math.sin((20.0f * t - 11.125f) * c5)) / 2.0f
                    : ((float) Math.pow(2.0, -20.0 * t + 10.0) * (float) Math.sin((20.0f * t - 11.125f) * c5)) / 2.0f + 1.0f));
            }

            case "easeinbounce" -> 1.0f - evalBounce(1.0f - t);
            case "easeoutbounce" -> evalBounce(t);
            case "easeinoutbounce" -> t < 0.5f ? (1.0f - evalBounce(1.0f - 2.0f * t)) / 2.0f : (1.0f + evalBounce(2.0f * t - 1.0f)) / 2.0f;

            case "smooth", "smoothstep" -> t * t * (3.0f - 2.0f * t);

            default -> t;
        };
    }

    private static float evalBounce(float x) {
        final float n1 = 7.5625f;
        final float d1 = 2.75f;
        if (x < 1.0f / d1) {
            return n1 * x * x;
        } else if (x < 2.0f / d1) {
            x -= 1.5f / d1;
            return n1 * x * x + 0.75f;
        } else if (x < 2.5f / d1) {
            x -= 2.25f / d1;
            return n1 * x * x + 0.9375f;
        } else {
            x -= 2.625f / d1;
            return n1 * x * x + 0.984375f;
        }
    }
}
