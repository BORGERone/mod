package dev.settledlands.mixin;

import dev.settledlands.door.DoorEvents368;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 373: the door itself refuses to open while it is locked.
 *
 * <p>This is the third try at the same question, and the first one that sits where the door is actually
 * opened. 370 cancelled the click - not enough. 371 denied the use through the event with
 * {@code setUseBlock}, which does not exist on {@code PlayerInteractEvent} in 1.21.1: the call threw,
 * the listener died, nothing was refused, and the door opened for a tick on every click - exactly long
 * enough to walk through it by leaning on the door and clicking fast.</p>
 *
 * <p>{@code DoorBlock#useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)} is the method
 * a right click on a door ends up in. It is refused here, before a single state is written, so there is
 * no tick in which the door is open. The signature is the one the 1.21.1 javadoc gives for
 * {@code DoorBlock}; the injection is {@code require = 0} anyway - a signature this exact game version
 * lacks must never be able to stop the launch (that was build 368).</p>
 *
 * <p>On the client {@code DoorEvents368.locked} answers false by design (the lock lives in the server's
 * saved data), so this guard does nothing there and the client keeps its ordinary behaviour; the door a
 * client may guess open is corrected by the state the server sends back.</p>
 */
@Mixin(targets = "net.minecraft.world.level.block.DoorBlock", remap = false)
public abstract class DoorUse373Mixin {

    @Inject(method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void settledlands$lockedDoorDoesNotOpen(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        try {
            boolean locked = DoorEvents368.locked(level, pos);
            // Build 374: the first entry into this method is written to the log, so the next report
            // says whether the guard is live and whether the lock was seen - not just "nothing
            // changed". A guard that never bound and a guard that bound and refused nothing look
            // identical from the outside, and that ambiguity cost build 371 a whole cycle.
            DoorEvents368.sawDoorUse(pos, locked);
            if (locked) cir.setReturnValue(InteractionResult.FAIL);
        } catch (Throwable ignored) {
            // never turn a click on a door into a crash
        }
    }
}
