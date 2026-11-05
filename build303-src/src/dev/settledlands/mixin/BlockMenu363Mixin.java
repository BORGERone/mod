package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 363: the right-click menu of a block's graph is cut down to the nodes a block can run.
 *
 * <p>Build 351 taught the editor to refuse the rows a block cannot run, and the author still had to
 * read a status line to find out which rows those were. The rows are now not in the menu at all:
 * {@link BlockMenu363} measures the panel when it opens and paints it with the rows a block has, in
 * the stock order and with the stock colours.</p>
 *
 * <p>The three hooks and why each one is here:</p>
 * <ul>
 *   <li><b>measure</b> - the menu is opened inside the screen's release handler
 *       ({@code NpcGraphContextMenu#release} calls {@code show}/{@code showForWire}), so that is the
 *       one place where "a menu has just appeared" is known. It runs once per open, not per frame;</li>
 *   <li><b>paint</b> - the stock painter is a switch over sixteen row numbers with nothing to cut, so
 *       for a block it is replaced by {@link BlockMenu363#draw}. The copy/delete menu of an existing
 *       node is left completely stock;</li>
 *   <li><b>scroll</b> - build 365 puts eleven rows in the panel and not all of them fit, so the notch
 *       moves the window from here ({@link BlockMenu363#wheel}); the stock scroller counts sixteen
 *       rows and would paint a row the cursor does not mean.</li>
 * </ul>
 *
 * <p>A menu this class never measured keeps the stock panel: an unforeseen path costs the filtering,
 * never the menu itself. Cost: one measurement per open, one paint per frame that already painted a
 * panel, and an integer range check on every click and wheel - nothing per node, nothing per tick.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockMenu363Mixin {

    @Shadow
    private NpcGraphContextMenu contextMenu;

    /** The menu has just been opened (or nothing happened): measure it if this is a block's graph. */
    @Inject(method = "settledlands$mouseReleasedOriginal", at = @At("RETURN"), remap = false)
    private void settledlands$measureBlockMenu(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        // Build 382: a door's panel is measured too - one row instead of fifteen.
        boolean door = settledlands$isDoor();
        if (!door && !settledlands$isBlock()) return;
        if (this.contextMenu == null) return;
        BlockMenu363.fit(this.contextMenu,
                ((NpcDialogueGraphScreen) (Object) this).settledlands$canvas(), door);
    }

    /** Paint the cut-down panel in place of the stock one. A node's copy/delete menu stays stock. */
    @Inject(method = "drawContextMenu", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawBlockMenu(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!settledlands$editor()) return;
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (this.contextMenu.targetNodeId >= 0) return;
        if (!BlockMenu363.ready()) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        BlockMenu363.draw(graphics, screen.settledlands$font(),
                BlockMenu363.hoverRow(this.contextMenu, mouseX, mouseY));
        ci.cancel();
    }

    /**
     * The wheel over a block panel: the panel moves its own window when its rows do not all fit, and
     * swallows the notch either way, so the stock scroller never moves a row the paint ignores.
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$blockMenuNoScroll(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!settledlands$editor()) return;
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (this.contextMenu.targetNodeId >= 0) return;
        if (!BlockMenu363.ready()) return;
        BlockMenu363.wheel(this.contextMenu, scrollY);
        cir.setReturnValue(Boolean.TRUE);
    }

    private boolean settledlands$isBlock() {
        return BlockTrigger351.isBlockEditorId(((NpcDialogueGraphScreen) (Object) this).entityId());
    }

    /** Build 382: the same panel serves the door editor, with the door's one row. */
    private boolean settledlands$isDoor() {
        return DoorGraph382.isDoorEditorId(((NpcDialogueGraphScreen) (Object) this).entityId());
    }

    /** True for either graph that borrows this panel. */
    private boolean settledlands$editor() {
        return settledlands$isBlock() || settledlands$isDoor();
    }
}
