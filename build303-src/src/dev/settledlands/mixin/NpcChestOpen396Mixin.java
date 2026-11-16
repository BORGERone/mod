package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.client.ChestGraphClient396;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 396: a config view whose id belongs to a chest opens the graph editor.
 *
 * <p>{@code NpcConfigScreen#open(CfgView)} is the one place the client turns a server view into a screen.
 * An NPC id opens the config screen, a block id opens the graph editor (351), a door id opens the same
 * editor on the door's graph (382) and a chest id opens the same editor on the chest's graph - the same
 * screen, the same canvas, the same save, one more owner of one more id range.</p>
 *
 * <p>Cost: one integer range check per config view; every id that existed before this build is untouched.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcConfigScreen", remap = false)
public abstract class NpcChestOpen396Mixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestOpen(NpcNet.CfgView view, CallbackInfo ci) {
        if (view == null || !ChestGraph396.isChestEditorId(view.entityId())) return;
        ci.cancel();
        ChestGraphClient396.openEditor(view);
    }
}
