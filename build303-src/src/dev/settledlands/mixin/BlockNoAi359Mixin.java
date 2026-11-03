package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 359: a block has no «Автономный ИИ» card.
 *
 * <p>That card is not a node at all - {@code NpcAutoAi255} keeps a per-graph state in a side map and
 * the editor's {@code ensure} marks it as synced with the graph the moment the window opens, which is
 * what makes the panel appear. Skipping the call for a block leaves the state unsynced, so the panel
 * is never drawn and the config window behind it has nothing to open from.</p>
 *
 * <p>Nothing is written to the block's graph and nothing is dropped: an NPC's own graph keeps its
 * panel, only a block's is left without one.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class BlockNoAi359Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockWithoutAi(NpcDialogueGraphScreen screen, NpcProfile profile,
            CallbackInfo ci) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
    }
}
