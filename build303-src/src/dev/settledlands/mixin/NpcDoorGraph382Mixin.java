package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.DoorGraphClient382;
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
 * Build 382: the door editor's palette has exactly one row - «Дверь».
 *
 * <p>Build 382 cut the menu to one row («Для начала только ноду двери»); build 386 gives the door the
 * four nodes it can run - «Дверь», «Открытие», «Кто открыл» и «В радиусе» - because the author asked for
 * more, and the rest of the stock rows would still be a lie: a door cannot run a line of dialogue. The
 * row that was clicked names the type through {@code BlockMenu363#doorType}, and the node gets the
 * defaults of its type, so a node added by hand is exactly the node a fresh door gets. Everything else
 * keeps the stock behaviour of {@code BlockMenu363} - the stock panel, five rows and a scrollbar, the
 * stock hit test counted from the window - and the copy/delete menu of an existing node stays stock.</p>
 *
 * <p>One tab, like a block (build 351): a door has one graph, and the second and third tabs would be
 * saved and never run.</p>
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

        int type = BlockMenu363.doorType(row);
        if (type < 0) {
            this.status = DoorGraphClient382.paletteNote();
            return;
        }
        NpcDialogueGraph.Node created = this.addAt(type, worldX, worldY);
        if (created != null) {
            DoorGraph382.applyDefaults(created);
            this.changed();
            this.status = Component.translatable("gui.settledlands.door.added_node",
                    Component.translatable(DoorGraph382.labelKey(type))).getString();
            return;
        }
        this.status = DoorGraphClient382.paletteNote();
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
