package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 358: closing a block's dialogue window (Esc) drops the block's chain.
 *
 * <p>{@code GuideDialogue#stop} is where every close of a dialogue arrives, and for a player with no
 * session it is a no-op - a block window has no session, so nothing else would ever forget it. This
 * takes the block's window out of {@link BlockTalk358} and leaves the stock body alone: a body's
 * dialogue still stops exactly the way it always did.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class BlockTalkStop358Mixin {

    @Inject(method = "stop(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$blockTalkStop(ServerPlayer player, CallbackInfo ci) {
        BlockTalk358.closed(player);
    }
}
