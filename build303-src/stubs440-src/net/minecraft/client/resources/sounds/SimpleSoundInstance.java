package net.minecraft.client.resources.sounds;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public class SimpleSoundInstance implements SoundInstance {
    public static SimpleSoundInstance forUI(Holder<SoundEvent> sound, float pitch) {
        return new SimpleSoundInstance();
    }
}
