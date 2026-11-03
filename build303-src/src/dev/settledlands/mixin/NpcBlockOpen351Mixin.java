package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.client.BlockTalkClient358;
import dev.settledlands.npc.client.BlockTriggerClient351;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 351: a config view whose id belongs to a block opens the graph editor, not the NPC screen.
 *
 * <p>{@code NpcConfigScreen#open(CfgView)} is the one place the client turns a server view into a
 * screen. The mobility wand already borrows it with an id of its own ({@code MOBILITY_EDITOR_ID}) and
 * gets its own editor; a block does the same, only it opens the stock graph editor directly - the
 * entity config screen has fields (name, profession, relations) a block has no business showing.</p>
 *
 * <p>Build 358 borrows the same door for the other thing a block can do: say a line. A view whose data
 * carries {@code BlockTalkX/Y/Z} is a talking block, not an edit - the head is aimed at the block and no
 * screen is opened, because the dialogue window itself arrives right behind it as a {@code DlgView}. A
 * block view without the coordinates is the editor, exactly as in build 351.</p>
 *
 * <p>Cost: one integer range check per config view; the stock body is untouched for every other id,
 * which is every id that exists today.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcConfigScreen", remap = false)
public abstract class NpcBlockOpen351Mixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockOpen(NpcNet.CfgView view, CallbackInfo ci) {
        if (view == null || !BlockTrigger351.isBlockEditorId(view.entityId())) return;
        ci.cancel();
        if (BlockTalkClient358.isTalk(view)) {
            // Build 358: the block is about to speak - turn the camera to it and let the window open.
            BlockTalkClient358.aim(view);
            return;
        }
        BlockTriggerClient351.openEditor(view);
    }
}
