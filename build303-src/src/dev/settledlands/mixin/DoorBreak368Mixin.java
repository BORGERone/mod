package dev.settledlands.mixin;

import dev.settledlands.door.DoorEvents368;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 369: a locked door cannot be removed from the world by anything that is not a player.
 *
 * <p>A zombie does not fire a break event - it bashes the door and the level removes the block, the
 * same way an explosion does. So the guard sits on the removal itself: a locked door is refused,
 * whoever asked. A player breaking it is stopped earlier, by the break event of
 * {@link DoorEvents368}, because that one can also say why.</p>
 *
 * <p><b>Why three shapes of one method.</b> Build 368 injected into
 * {@code destroyBlock(BlockPos, boolean)} and the game refused to start: that overload is a
 * {@code default} method on the {@code LevelWriter} interface, not a method of {@code Level}, and a
 * mixin cannot inject into an interface default through a class target. The overloads that a level
 * really declares are the three- and four-argument ones; which of them exists in a given build is not
 * something this mod can prove offline, so each injection is declared {@code require = 0}: a shape the
 * game does not have is skipped with a warning, and the game always starts. That is the lesson of 368 -
 * an injection that cannot be verified must not be able to block the boot.</p>
 *
 * <p>Cost: one block read and one store lookup per removed block, and nothing when the block is not a
 * door - {@code DoorEvents368#locked} answers false after a single state read.</p>
 */
@Mixin(targets = "net.minecraft.world.level.Level", remap = false)
public abstract class DoorBreak368Mixin {

    /** 1.21 with the removal carried out: the one the other two delegate to. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void settledlands$lockedDoorSurvivesDeep(BlockPos pos, boolean dropBlock, Entity entity,
            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        settledlands$guard(pos, cir);
    }

    /** The three-argument call vanilla makes for a mob that breaks a block. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void settledlands$lockedDoorSurvivesFor(BlockPos pos, boolean dropBlock, Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        settledlands$guard(pos, cir);
    }

    /** The two-argument call, kept as a third chance: a default on the interface in some builds. */
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void settledlands$lockedDoorSurvivesShallow(BlockPos pos, boolean dropBlock,
            CallbackInfoReturnable<Boolean> cir) {
        settledlands$guard(pos, cir);
    }

    /** Explosions and pistons go through this one instead of {@code destroyBlock}. */
    @Inject(method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void settledlands$lockedDoorSurvivesRemoval(BlockPos pos, boolean isMoving,
            CallbackInfoReturnable<Boolean> cir) {
        settledlands$guard(pos, cir);
    }

    private void settledlands$guard(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (DoorEvents368.locked((Level) (Object) this, pos)) cir.setReturnValue(Boolean.FALSE);
    }
}
