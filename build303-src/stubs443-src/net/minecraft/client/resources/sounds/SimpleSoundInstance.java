package net.minecraft.client.resources.sounds;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** Compile-time mirror (Build 443): adds SoundEvent overload verified via ForgeJavaDocs-NG 1.21.x. */
public class SimpleSoundInstance implements SoundInstance {
    public static SimpleSoundInstance forUI(Holder<SoundEvent> sound, float pitch) {
        return new SimpleSoundInstance();
    }

    public static SimpleSoundInstance forUI(SoundEvent sound, float pitch) {
        return new SimpleSoundInstance();
    }
}
