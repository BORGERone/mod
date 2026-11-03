package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 358: a click inside a block's dialogue window continues the block's chain, not a body's.
 *
 * <p>{@code GuideDialogue#pick} is the one entry of "a row was chosen" - the client sends
 * {@code DlgPick(entityId, index)} for a window and this is where it lands. It already answers null for
 * ids that have no session: the stock body starts by looking the session up and returning when the id
 * does not match, so a block id is a no-op there. {@link BlockTalk358#pick} takes the pick first and
 * cancels only when the id is a block's own window; every body in the game reaches the stock body
 * untouched.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class BlockTalkPick358Mixin {

    @Inject(method = "pick(Lnet/minecraft/server/level/ServerPlayer;II)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockTalkPick(ServerPlayer player, int entityId, int index,
            CallbackInfo ci) {
        if (BlockTalk358.pick(player, entityId, index)) ci.cancel();
    }
}
