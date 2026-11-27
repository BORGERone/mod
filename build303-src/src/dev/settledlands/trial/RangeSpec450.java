package dev.settledlands.trial;

import java.util.Locale;
import java.util.Random;

/**
 * Build 450: unified min-max range parser, validator, formatter, and sampler for Trial Spawner,
 * Mob/Reward Pools, and Dialogue/Trigger Graph nodes.
 *
 * <p>Supports single numbers ({@code 20}, {@code -5}, {@code 1.5}) as well as any common range
 * separator ({@code 20-40}, {@code 20 - 40}, {@code 20..40}, {@code 20:40}, {@code 20~40},
 * {@code 20–40}, {@code -10-10}, {@code -20..-5}, {@code 1.2-2.0}). Inverted bounds are normalized
 * automatically ({@code lo <= hi}).</p>
 */
public final class RangeSpec450 {

    private RangeSpec450() {}

    public static final class IntRange {
        public final int min;
        public final int max;

        public IntRange(int min, int max) {
            if (min <= max) {
                this.min = min;
                this.max = max;
            } else {
                this.min = max;
                this.max = min;
            }
        }

        public boolean isRange() {
            return min < max;
        }

        public int pick(Random rnd) {
            if (min >= max || rnd == null) return min;
            long span = (long) max - (long) min + 1L;
            if (span <= 1L) return min;
            if (span >= Integer.MAX_VALUE) {
                return min + (int) Math.floor(rnd.nextDouble() * span);
            }
            return min + rnd.nextInt((int) span);
        }

        public boolean contains(int value) {
            return value >= min && value <= max;
        }

        public String format() {
            return min == max ? Integer.toString(min) : (min + "-" + max);
        }

        @Override
        public String toString() {
            return format();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IntRange r)) return false;
            return min == r.min && max == r.max;
        }

        @Override
        public int hashCode() {
            return 31 * min + max;
        }
    }

    public static final class FloatRange {
        public final float min;
        public final float max;

        public FloatRange(float min, float max) {
            if (min <= max) {
                this.min = min;
                this.max = max;
            } else {
                this.min = max;
                this.max = min;
            }
        }

        public boolean isRange() {
            return max - min > 1e-5F;
        }

        public float pick(Random rnd) {
            if (!isRange() || rnd == null) return min;
            float raw = min + rnd.nextFloat() * (max - min);
            return Math.round(raw * 100.0F) / 100.0F;
        }

        public String format() {
            if (!isRange()) return formatFloatCompact(min);
            return formatFloatCompact(min) + "-" + formatFloatCompact(max);
        }

        @Override
        public String toString() {
            return format();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FloatRange r)) return false;
            return Math.abs(min - r.min) <= 1e-4F && Math.abs(max - r.max) <= 1e-4F;
        }

        @Override
        public int hashCode() {
            return 31 * Float.floatToIntBits(Math.round(min * 100.0F) / 100.0F)
                    + Float.floatToIntBits(Math.round(max * 100.0F) / 100.0F);
        }
    }

    /**
     * Parses an integer range or single integer, clamping both ends to {@code [boundMin..boundMax]}
     * and normalizing {@code lo <= hi}. Returns {@code (fbMin, fbMax)} if unparseable or blank.
     */
    public static IntRange parseInt(String raw, int fbMin, int fbMax, int boundMin, int boundMax) {
        int cFbMin = clamp(fbMin, boundMin, boundMax);
        int cFbMax = clamp(fbMax, boundMin, boundMax);
        if (raw == null) return new IntRange(cFbMin, cFbMax);
        String s = normalizeRaw(raw);
        if (s.isEmpty()) return new IntRange(cFbMin, cFbMax);
        String[] parts = splitRangeParts(s);
        if (parts == null) {
            try {
                int v = clamp(Integer.parseInt(s), boundMin, boundMax);
                return new IntRange(v, v);
            } catch (NumberFormatException ignored) {
                return new IntRange(cFbMin, cFbMax);
            }
        }
        try {
            int a = clamp(Integer.parseInt(parts[0]), boundMin, boundMax);
            int b = clamp(Integer.parseInt(parts[1]), boundMin, boundMax);
            return new IntRange(a, b);
        } catch (NumberFormatException ignored) {
            return new IntRange(cFbMin, cFbMax);
        }
    }

    /**
     * Parses an integer range or single integer strictly (returns {@code null} when blank or invalid).
     */
    public static IntRange tryParseInt(String raw, int boundMin, int boundMax) {
        if (raw == null) return null;
        String s = normalizeRaw(raw);
        if (s.isEmpty()) return null;
        String[] parts = splitRangeParts(s);
        if (parts == null) {
            try {
                int v = clamp(Integer.parseInt(s), boundMin, boundMax);
                return new IntRange(v, v);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            int a = clamp(Integer.parseInt(parts[0]), boundMin, boundMax);
            int b = clamp(Integer.parseInt(parts[1]), boundMin, boundMax);
            return new IntRange(a, b);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static FloatRange parseFloat(String raw, float fbMin, float fbMax,
            float boundMin, float boundMax) {
        float cFbMin = clampFloat(fbMin, boundMin, boundMax);
        float cFbMax = clampFloat(fbMax, boundMin, boundMax);
        if (raw == null) return new FloatRange(cFbMin, cFbMax);
        String s = normalizeRaw(raw).replace(',', '.');
        if (s.isEmpty()) return new FloatRange(cFbMin, cFbMax);
        String[] parts = splitRangeParts(s);
        if (parts == null) {
            try {
                float v = Float.parseFloat(s);
                if (!Float.isFinite(v)) return new FloatRange(cFbMin, cFbMax);
                v = clampFloat(v, boundMin, boundMax);
                return new FloatRange(v, v);
            } catch (NumberFormatException ignored) {
                return new FloatRange(cFbMin, cFbMax);
            }
        }
        try {
            float a = Float.parseFloat(parts[0]);
            float b = Float.parseFloat(parts[1]);
            if (!Float.isFinite(a) || !Float.isFinite(b)) return new FloatRange(cFbMin, cFbMax);
            return new FloatRange(clampFloat(a, boundMin, boundMax), clampFloat(b, boundMin, boundMax));
        } catch (NumberFormatException ignored) {
            return new FloatRange(cFbMin, cFbMax);
        }
    }

    public static FloatRange tryParseFloat(String raw, float boundMin, float boundMax) {
        if (raw == null) return null;
        String s = normalizeRaw(raw).replace(',', '.');
        if (s.isEmpty()) return null;
        String[] parts = splitRangeParts(s);
        if (parts == null) {
            try {
                float v = Float.parseFloat(s);
                if (!Float.isFinite(v)) return null;
                v = clampFloat(v, boundMin, boundMax);
                return new FloatRange(v, v);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            float a = Float.parseFloat(parts[0]);
            float b = Float.parseFloat(parts[1]);
            if (!Float.isFinite(a) || !Float.isFinite(b)) return null;
            return new FloatRange(clampFloat(a, boundMin, boundMax), clampFloat(b, boundMin, boundMax));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Returns {@code true} iff {@code raw} contains a genuine range separator between two bounds. */
    public static boolean hasRangeSeparator(String raw) {
        if (raw == null) return false;
        String s = normalizeRaw(raw);
        return splitRangeParts(s) != null;
    }

    /** EditBox filter for unsigned or optionally signed integer ranges (e.g. {@code 20-40}, {@code -10..10}). */
    public static boolean isIntRangeInput(String s, boolean allowNegative) {
        if (s == null || s.isEmpty()) return true;
        if (s.length() > 24) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '.' || c == ':'
                    || c == '~' || c == '–' || c == '—' || c == ' ') {
                continue;
            }
            return false;
        }
        if (!allowNegative && s.charAt(0) == '-') {
            // Do not accept leading negative sign on strictly non-negative fields.
            return false;
        }
        return true;
    }

    /** EditBox filter for decimal/float ranges (e.g. {@code 1.2-2.5}, {@code 25.5-75}). */
    public static boolean isFloatRangeInput(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.length() > 24) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-'
                    || c == ':' || c == '~' || c == '–' || c == '—' || c == ' ') {
                continue;
            }
            return false;
        }
        return true;
    }

    public static String formatInt(int min, int max) {
        return min == max ? Integer.toString(min) : (Math.min(min, max) + "-" + Math.max(min, max));
    }

    public static String formatFloat(float min, float max) {
        float lo = Math.min(min, max);
        float hi = Math.max(min, max);
        if (hi - lo <= 1e-5F) return formatFloatCompact(lo);
        return formatFloatCompact(lo) + "-" + formatFloatCompact(hi);
    }

    public static String formatFloatCompact(float v) {
        float rounded = Math.round(v * 100.0F) / 100.0F;
        if (Math.abs(rounded - Math.round(rounded)) < 1e-4F) {
            return Integer.toString(Math.round(rounded));
        }
        String s = String.format(Locale.ROOT, "%.2f", rounded);
        if (s.indexOf('.') >= 0) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Splits {@code raw} on the first binary range separator ({@code ..}, {@code ~}, {@code :},
     * {@code -}, {@code –}, {@code —}), carefully preserving a leading unary {@code -} on either
     * bound (e.g. {@code -10--2}, {@code -10..10}, {@code -10-10}, {@code 5-15}).
     */
    static String[] splitRangeParts(String s) {
        if (s == null || s.isEmpty()) return null;
        int n = s.length();
        // Prefer explicit multi-char/non-ambiguous separators first.
        int dots = s.indexOf("..");
        if (dots > 0 && dots + 2 < n) {
            String a = s.substring(0, dots).trim();
            String b = s.substring(dots + 2).trim();
            if (!a.isEmpty() && !b.isEmpty()) return new String[] { a, b };
        }
        for (char sep : new char[] { '~', ':', '–', '—' }) {
            int idx = s.indexOf(sep);
            if (idx > 0 && idx + 1 < n) {
                String a = s.substring(0, idx).trim();
                String b = s.substring(idx + 1).trim();
                if (!a.isEmpty() && !b.isEmpty()) return new String[] { a, b };
            }
        }
        // Binary '-' after at least one digit on the left (skips unary '-' at index 0).
        boolean sawDigit = false;
        for (int i = 0; i < n - 1; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sawDigit = true;
            } else if (c == '-' && sawDigit) {
                String a = s.substring(0, i).trim();
                String b = s.substring(i + 1).trim();
                if (!a.isEmpty() && !b.isEmpty()) return new String[] { a, b };
            }
        }
        return null;
    }

    private static String normalizeRaw(String raw) {
        if (raw == null) return "";
        return raw.trim().replace('–', '-').replace('—', '-');
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float clampFloat(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
