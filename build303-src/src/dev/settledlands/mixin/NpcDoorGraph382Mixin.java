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
 * <p>The author asked for one node for now: «Для начала только ноду двери». A palette of fifteen rows
 * where fourteen create a node a door cannot run would be a lie, so the create menu of a door's graph
 * is cut to the one row and everything else keeps the stock behaviour of {@code BlockMenu363} - the
 * stock panel, five rows and a scrollbar, the stock hit test counted from the window.</p>
 *
 * <p>The click creates the node the door is made of ({@link DoorGraph382#DOOR_TYPE}) and fills its
 * three saved fields with the defaults, so a node added by hand is exactly the node a fresh door gets.
 * The copy/delete menu of an existing node stays stock; so does every other canvas gesture.</p>
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

        NpcDialogueGraph.Node created = this.addAt(DoorGraph382.DOOR_TYPE, worldX, worldY);
        if (created != null) {
            DoorGraph382.setKeys(created, DoorGraph382.DEFAULT_KEYS);
            DoorGraph382.setSpeed(created, DoorGraph382.DEFAULT_SPEED);
            DoorGraph382.setSealed(created, true);
            this.changed();
            this.status = Component.translatable("gui.settledlands.door.added").getString();
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
