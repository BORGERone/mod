package com.hazelism.nomansland;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common (server-side) config. Values are read once into fields - never parse config in a tick loop. */
public final class NMLConfig {
    public static final ModConfigSpec SPEC;

    /** Master cap for all custom mobs of this mod alive in a world at once (spawn-stage safeguard). */
    public static final ModConfigSpec.IntValue MOB_GLOBAL_CAP;
    /** Client particle master toggle is in the client config later; this gates server-sent effect packets. */
    public static final ModConfigSpec.BooleanValue BLOOD_EFFECTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("mobs");
        MOB_GLOBAL_CAP = b.comment("Hard cap for all No Man's Land mobs combined. Prevents spawn explosions.")
                .defineInRange("globalCap", 80, 1, 1000);
        BLOOD_EFFECTS = b.comment("Allow blood drip/splash effects (also toggleable per-player on the client later).")
                .define("bloodEffects", true);
        b.pop();
        SPEC = b.build();
    }

    private NMLConfig() {}
}
