package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.BlockTriggerClient351;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 351: the block editor uses the stock graph screen, with the stock palette cut down to the
 * nodes a block can actually run.
 *
 * <p>The palette of the editor is one popup with fifteen rows. An NPC can run all of them; a block
 * cannot - it has no body to walk, animate or trade, and its only event is the right click, so
 * there is nothing to attach "время", "таймер" or "урон" to. Rather than leave rows that would
 * quietly do nothing, the block editor keeps five: <b>Реплика, Триггер, Условие If, Переменная,
 * Случайность</b> (plus the event node "В радиусе" that build 352 added). A click on any other row is
 * answered with a line that says which ones there are, so the palette never lies about what a block
 * can do.</p>
 *
 * <p>Build 357 adds the first of them: a block says a line to the player in the chat, which is why
 * "Реплика" is no longer a row a block has to refuse. The text box of the stock inspector already
 * edits {@code Node#text}, so the node is written without a single new widget.</p>
 *
 * <p>The copy/delete menu of an existing node stays stock, and so does everything else on the canvas:
 * this hook only reacts to the create menu, and only while a block id is open.</p>
 *
 * <p>Cost: one integer range check and one comparison for a click; nothing is drawn, nothing is
 * called per frame. The tab guard is the same shape: a block has one graph, so the second and third
 * tab cannot be opened (they would be saved and never run).</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcBlockGraph351Mixin {

    @Shadow
    private NpcGraphContextMenu contextMenu;

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private String status;

    @Shadow
    private NpcDialogueGraph.Node addAt(int type, double worldX, double worldY) {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$blockPalette(double x, double y, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (!settledlands$isBlock()) return;
        if (this.contextMenu.targetNodeId >= 0) return;   // copy / delete of a node: stock behaviour
        if (!this.contextMenu.contains(x, y)) return;

        int row = this.contextMenu.rowAt(x, y);
        double worldX = this.contextMenu.worldX;
        double worldY = this.contextMenu.worldY;
        int wireFrom = this.contextMenu.pendingWireFrom;
        int wirePort = this.contextMenu.pendingWirePort;
        this.contextMenu.close();
        cir.setReturnValue(Boolean.TRUE);
        if (button != 0 || row < 0) return;

        int type = settledlands$paletteType(row);
        if (type < 0) {
            this.status = BlockTriggerClient351.paletteNote();
            return;
        }
        NpcDialogueGraph.Node created = this.addAt(type, worldX, worldY);
        if (created != null && wireFrom >= 0 && this.graph.connect(wireFrom, created.id, wirePort)) {
            this.changed();
            this.status = Component.translatable("gui.settledlands.graph.connected").getString();
        }
    }

    @Inject(method = "switchTab", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$blockOneTab(int tab, CallbackInfo ci) {
        if (tab == 0 || !settledlands$isBlock()) return;
        this.status = Component.translatable("gui.settledlands.block.one_tab").getString();
        ci.cancel();
    }

    /**
     * Row of the stock palette -> the node type, or -1 when a block cannot run that row. The map
     * itself lives in {@code BlockTrigger351#typeForRow}, next to the palette it has to agree with;
     * this hook only asks. That is also what keeps the answer testable without a screen.
     */
    private static int settledlands$paletteType(int row) {
        return BlockTrigger351.typeForRow(row);
    }

    private boolean settledlands$isBlock() {
        return BlockTrigger351.isBlockEditorId(((NpcDialogueGraphScreen) (Object) this).entityId());
    }
}
