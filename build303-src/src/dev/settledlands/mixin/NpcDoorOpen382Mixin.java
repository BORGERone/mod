package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.client.DoorGraphClient382;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382: a config view whose id belongs to a door opens the graph editor.
 *
 * <p>{@code NpcConfigScreen#open(CfgView)} is the one place the client turns a server view into a
 * screen. An NPC id opens the config screen, a mobility id opens the mobility editor (build 244), a
 * block id opens the graph editor (build 351) and a door id opens the same graph editor on the same
 * graph - the door has no name, no profession and no relations, so there is nothing else to show.</p>
 *
 * <p>Cost: one integer range check per config view; the stock body is untouched for every other id,
 * which is every id that existed before this build.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcConfigScreen", remap = false)
public abstract class NpcDoorOpen382Mixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorOpen(NpcNet.CfgView view, CallbackInfo ci) {
        if (view == null || !DoorGraph382.isDoorEditorId(view.entityId())) return;
        ci.cancel();
        DoorGraphClient382.openEditor(view);
    }
}
