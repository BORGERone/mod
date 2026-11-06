package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
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
 * Build 388: the door editor's palette is the stock palette - the same rows a block's graph offers.
 *
 * <p>The author's report on build 386: «мне не нравится реализация нод в графе двери, сделай реализацию
 * как в других графах». Build 386 had invented three types of its own so that a door could run a signal,
 * a radius and a name; the honest answer is the block's one (build 351): a graph runs the stock nodes
 * through the stock interpreter, and the only node of its own is the card that carries the thing's
 * identity. So this menu now creates stock node types - {@code BlockMenu363#doorType} answers with the
 * type of the stock row that was clicked - and the door card is created with the graph instead of from
 * here.</p>
 *
 * <p>One tab, like a block: a door has one graph, and the second and third tabs would be saved and
 * never run.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDoorGraph382Mixin {

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
    private void settledlands$doorPalette(double x, double y, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (!settledlands$isDoor()) return;
        if (this.contextMenu.targetNodeId >= 0) return;   // copy / delete of a node: stock behaviour
        if (!this.contextMenu.contains(x, y)) return;

        if (BlockMenu363.stripHit(this.contextMenu, x, y)) {
            BlockMenu363.stripClick(this.contextMenu, y);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        int row = BlockMenu363.rowAt(this.contextMenu, x, y);
        double worldX = this.contextMenu.worldX;
        double worldY = this.contextMenu.worldY;
        this.contextMenu.close();
        cir.setReturnValue(Boolean.TRUE);
        if (button != 0 || row < 0) return;

        // Build 388: the row makes the node the stock palette makes for that row - the same type, the
        // same defaults, the same stock card and the same stock inspector rows. There is no door type
        // to switch on any more; the door card is not in this menu.
        int type = BlockMenu363.doorType(row);
        if (type < 0) {
            this.status = Component.translatable("gui.settledlands.door.palette").getString();
            return;
        }
        NpcDialogueGraph.Node created = this.addAt(type, worldX, worldY);
        if (created != null) {
            this.changed();
            this.status = Component.translatable("gui.settledlands.door.added_node",
                    Component.literal(BlockMenu363.label(row))).getString();
            return;
        }
        this.status = Component.translatable("gui.settledlands.door.palette").getString();
    }

    @Inject(method = "switchTab", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$doorOneTab(int tab, CallbackInfo ci) {
        if (tab == 0 || !settledlands$isDoor()) return;
        this.status = Component.translatable("gui.settledlands.door.one_tab").getString();
        ci.cancel();
    }

    private boolean settledlands$isDoor() {
        return DoorGraph382.isDoorEditorId(((NpcDialogueGraphScreen) (Object) this).entityId());
    }
}
