package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.BlockMenu363;
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
 * Build 396: a chest's create menu is the stock palette, cut to the nodes a chest can run.
 *
 * <p>«Сделай реализацию как в других графах» - the author's words for the door in build 386, and they are
 * the whole of this hook. A chest's menu is the block's menu ({@code BlockMenu363}): the same rows, the
 * same labels, the same colours, the same five-row window with a scrollbar, and each row makes the node the
 * stock palette makes for it, with the stock defaults - so a «Реплика», a «Триггер», an «Условие If», a
 * «Переменная», «Случайность», «В радиусе», «Время» and «Таймер» in a chest's graph are the stock nodes run
 * by the stock interpreter ({@code BlockTrigger351#start} and the radius pass).</p>
 *
 * <p>The chest's own card is not in this menu: like the block's «Имя» card and the door's card it is created
 * with the graph, not added from a palette - an output is a socket of the card, not a node beside it
 * (build 392). One tab, too: a chest has one graph, and the second and third tabs would be saved and never
 * run.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcChestPalette396Mixin {

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
    private void settledlands$chestPalette(double x, double y, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (!ChestGraph396.isChestEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;
        if (this.contextMenu.targetNodeId >= 0) return;   // copy / delete of a node: stock behaviour
        if (!this.contextMenu.contains(x, y)) return;

        // The scrollbar of the panel is not a row: a click on it moves the window and leaves the menu open.
        if (BlockMenu363.stripHit(this.contextMenu, x, y)) {
            BlockMenu363.stripClick(this.contextMenu, y);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        int row = BlockMenu363.rowAt(this.contextMenu, x, y);
        double worldX = this.contextMenu.worldX;
        double worldY = this.contextMenu.worldY;
        int wireFrom = this.contextMenu.pendingWireFrom;
        int wirePort = this.contextMenu.pendingWirePort;
        this.contextMenu.close();
        cir.setReturnValue(Boolean.TRUE);
        if (button != 0 || row < 0) return;

        // The row of the click is a row of the block's (shorter) panel; the stock row behind it is what the
        // palette map speaks, and the type is the block's own mapping - not a second one for chests.
        int stock = BlockMenu363.stockRow(row);
        int type = stock < 0 ? -1 : ChestGraph396.typeForRow(stock);
        if (type < 0) {
            this.status = Component.translatable("gui.settledlands.chest.palette").getString();
            return;
        }
        NpcDialogueGraph.Node created = this.addAt(type, worldX, worldY);
        if (created != null) {
            if (wireFrom >= 0 && this.graph.connect(wireFrom, created.id, wirePort)) {
                this.status = Component.translatable("gui.settledlands.graph.connected").getString();
            } else {
                this.status = Component.translatable("gui.settledlands.chest.added_node",
                        Component.literal(BlockMenu363.label(row))).getString();
            }
            this.changed();
            return;
        }
        this.status = Component.translatable("gui.settledlands.chest.palette").getString();
    }

    @Inject(method = "switchTab", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$chestOneTab(int tab, CallbackInfo ci) {
        if (tab == 0) return;
        if (!ChestGraph396.isChestEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;
        this.status = Component.translatable("gui.settledlands.chest.one_tab").getString();
        ci.cancel();
    }
}
