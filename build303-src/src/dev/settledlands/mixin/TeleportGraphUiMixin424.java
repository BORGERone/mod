package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.TeleportCard424;
import dev.settledlands.trial.TrialGraph446;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 424: draws the Teleport node card, renders row 17 («+ Телепорт») in the NPC/common context
 * menu, and creates Teleport nodes when clicked in NPC/common graphs.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TeleportGraphUiMixin424 {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private NpcGraphContextMenu contextMenu;

    @Shadow
    private String status;

    @Shadow
    private NpcDialogueGraph.Node addAt(int type, double worldX, double worldY) {
        throw new AssertionError();
    }

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Inject(method = "drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawTeleportMenuRow424(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (contextMenu == null || !contextMenu.open || contextMenu.targetNodeId >= 0) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        int id = screen.entityId();
        if (BlockTrigger351.isBlockEditorId(id)
                || DoorGraph382.isDoorEditorId(id)
                || ChestGraph396.isChestEditorId(id)
                || TrialGraph446.isTrialEditorId(id)) {
            return;
        }
        TeleportCard424.drawMenuRow(contextMenu, graphics, screen.settledlands$font(), mouseX, mouseY);
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawTeleportCard424(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (!TeleportNode424.isTeleportNode(node)) return;
        TeleportCard424.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
        ci.cancel();
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$createTeleportNode424(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (contextMenu == null || !contextMenu.open || button != 0 || contextMenu.targetNodeId >= 0) {
            return;
        }
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        int id = screen.entityId();
        if (BlockTrigger351.isBlockEditorId(id)
                || DoorGraph382.isDoorEditorId(id)
                || ChestGraph396.isChestEditorId(id)
                || TrialGraph446.isTrialEditorId(id)) {
            return;
        }
        if (contextMenu.isScrollbar(mouseX, mouseY)) return;
        int row = contextMenu.rowAt(mouseX, mouseY);
        if (row != TeleportNode424.TELEPORT_ROW) return;

        double worldX = contextMenu.worldX;
        double worldY = contextMenu.worldY;
        int wireFrom = contextMenu.pendingWireFrom;
        int wirePort = contextMenu.pendingWirePort;
        contextMenu.close();

        NpcDialogueGraph.Node created = addAt(NpcDialogueGraph.TRIGGER, worldX, worldY);
        if (created != null) {
            TeleportNode424.applyDefaults(created);
            if (wireFrom >= 0 && graph != null && graph.connect(wireFrom, created.id, wirePort)) {
                status = Component.translatable("gui.settledlands.graph.connected").getString();
            } else {
                status = Component.translatable("gui.settledlands.teleport.added").getString();
            }
            widgets();
            changed();
        }
        cir.setReturnValue(Boolean.TRUE);
    }
}
