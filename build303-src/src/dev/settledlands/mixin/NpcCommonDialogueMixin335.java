package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCommonDialogue335;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 335: a conversation runs on a graph that also holds the buttons of «Общее».
 *
 * <p>The talk is assembled at the end of {@code settlelands$originalStart} - the body of
 * {@code start}, kept under that name by an earlier build - directly after it closes the previous
 * conversation and before the session is put into the map. Attaching there means the graph the
 * session will be built from is already the merged one, and {@code startGraph} - the very next thing
 * that happens - lists the common buttons along with the NPC's own.</p>
 *
 * <p>{@code stop} is the one place every conversation ends in, so it is where the NPC gets its own
 * graph back. Note that the early {@code stop} inside a new {@code start} runs before the attach, so
 * re-opening a talk simply releases the old one and starts the new one clean.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class NpcCommonDialogueMixin335 {

    @Inject(method = "settledlands$originalStart",
            at = @At(value = "INVOKE",
                     target = "Ldev/settledlands/npc/GuideDialogue;stop(Lnet/minecraft/server/level/ServerPlayer;)V",
                     shift = At.Shift.AFTER),
            remap = false)
    private static void settledlands$attachCommon(Player player, GuideNpc npc, CallbackInfo ci) {
        NpcCommonDialogue335.attach(player, npc);
    }

    @Inject(method = "stop", at = @At("HEAD"), remap = false)
    private static void settledlands$detachCommon(ServerPlayer player, CallbackInfo ci) {
        NpcCommonDialogue335.detach(player);
    }
}
