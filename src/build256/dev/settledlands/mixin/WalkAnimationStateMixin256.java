package dev.settledlands.mixin;

import dev.settledlands.model.WalkAnimationSyncAccess256;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({WalkAnimationState.class})
public abstract class WalkAnimationStateMixin256 implements WalkAnimationSyncAccess256 {
    @Shadow
    private float speedOld;
    @Shadow
    private float speed;
    @Shadow
    private float position;

    @Override
    public void settledlands$copyWalkStateFrom(WalkAnimationState source) {
        if (source == null || (Object) source == (Object) this) return;
        WalkAnimationStateMixin256 src = (WalkAnimationStateMixin256) (Object) source;
        this.speedOld = src.speedOld;
        this.speed = src.speed;
        this.position = src.position;
    }
}
