package dev.settledlands.mixin;

import dev.settledlands.door.DoorEvents368;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 368: a locked door cannot be destroyed by anything that is not a player either.
 *
 * <p>A zombie does not fire a break event - it bashes the door and the level removes the block
 * ({@code BreakDoorGoal} calls {@code Level#destroyBlock}), so a guard on the event alone would leave
 * exactly the griefing the author asked about. The guard sits on the level instead: a locked door is
 * refused here, whoever asked. A player breaking it is already stopped earlier, by the break event of
 * {@link DoorEvents368}, because that one can also say why.</p>
 *
 * <p>Cost: one block read and one store lookup per block destroyed in the world, and nothing at all
 * when the block is not a door - {@code DoorEvents368#locked} answers false immediately.</p>
 */
@Mixin(targets = "net.minecraft.world.level.Level", remap = false)
public abstract class DoorBreak368Mixin {

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void settledlands$lockedDoorSurvives(BlockPos pos, boolean dropBlock,
            CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (DoorEvents368.locked(level, pos)) cir.setReturnValue(Boolean.FALSE);
    }
}
