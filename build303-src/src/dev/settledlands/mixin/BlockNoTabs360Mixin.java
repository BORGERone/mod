package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 360: a block's graph has no tabs.
 *
 * <p>An NPC's editor is three graphs on three tabs - dialogue, walking, animation. A block has exactly
 * one graph; the two extra tabs could only ever be empty, and they were already refused a click
 * (build 351). A refused tab is still a tab: the author clicks it, gets a line in the status bar and
 * spends the next minute wondering which of the three graphs his block uses. So for a block the two
 * tabs are not drawn at all.</p>
 *
 * <p>The tab strip is not painted by hand - it is three ordinary stock buttons created in
 * {@code widgets()}, the same call that makes undo and redo, one per tab label. This hook takes that
 * one call for a tab label while a block is open and hides the button it made. The button still
 * exists and is still created (the loop around it writes the \"this is the current tab\" flag into it,
 * so a missing button would be a crash), it is simply never visible and never clickable, which is what
 * a tab a block does not have should be.</p>
 *
 * <p>The guard on {@code switchTab} stays, so a hotkey cannot reach the two hidden graphs either.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockNoTabs360Mixin {

    /** The three stock tab labels. A block keeps none of them, so the prefix is the whole test. */
    private static final String TAB_PREFIX = "gui.settledlands.graph.tab_";

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Redirect(method = "widgets()V",
              at = @At(value = "INVOKE",
                       target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;"
                               + "button(Ljava/lang/String;IIIILjava/lang/Runnable;)"
                               + "Lnet/minecraft/client/gui/components/Button;"),
              remap = false)
    private Button settledlands$blockHasNoTabs(String key, int x, int y, int width, int height,
            Runnable action) {
        Button made = button(key, x, y, width, height, action);
        if (made == null || key == null || !key.startsWith(TAB_PREFIX)) return made;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return made;
        made.visible = false;
        made.active = false;
        return made;
    }
}
