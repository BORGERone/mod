package dev.settledlands.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Build 441: silences {@code settledlands:guide_trigger} ({@code GuideTriggerNpc}) so invisible
 * blueprint graph anchors and trigger markers never emit villager ambient, hurt, or death sounds.
 */
@Mixin(targets = "dev.settledlands.npc.GuideTriggerNpc", remap = false)
public abstract class GuideTriggerSilentMixin441 {

    protected SoundEvent getAmbientSound() {
        return null;
    }

    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    protected SoundEvent getDeathSound() {
        return null;
    }

    protected float getSoundVolume() {
        return 0.0F;
    }
}
