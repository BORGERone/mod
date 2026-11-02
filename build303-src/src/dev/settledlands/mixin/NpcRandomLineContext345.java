package dev.settledlands.mixin;

import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcRandomLine345;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 345: the NPC is known while its dialogue view is built.
 *
 * <p>A node turns into a line in one place - {@code GuideDialogue.sendGraph} asks the node for its
 * text and sends it to the player - and that is also the only place where the NPC behind the line is
 * at hand. The hook marks the NPC for the length of that call, so the roll of a random line can be
 * kept per body instead of per shared node. Nothing else about the view changes: the text, the
 * options and the style are the graph's own.</p>
 *
 * <p>Both hooks are plain head/return injections on a method of the mod's own class; the exit runs on
 * every normal return, so the mark never outlives the view it was set for.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class NpcRandomLineContext345 {

    @Inject(
            method = "sendGraph(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/GuideNpc;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterNpc(ServerPlayer player, GuideDialogue.Session session,
            GuideNpc npc, CallbackInfo ci) {
        NpcRandomLine345.enter(npc);
    }

    @Inject(
            method = "sendGraph(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/GuideNpc;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitNpc(ServerPlayer player, GuideDialogue.Session session,
            GuideNpc npc, CallbackInfo ci) {
        NpcRandomLine345.exit();
    }
}
