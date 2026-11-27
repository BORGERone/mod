package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: create-menu palette and single-tab lock for the Trial Spawner graph editor.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcTrialPalette446Mixin {

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

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$trialPalette446(double x, double y, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.contextMenu == null || !this.contextMenu.open) return;
        if (!TrialGraph446.isTrialEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;
        if (this.contextMenu.targetNodeId >= 0) return;
        if (!this.contextMenu.contains(x, y)) return;

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

        if (BlockMenu363.isTeleportRow(row)) {
            NpcDialogueGraph.Node created = this.addAt(NpcDialogueGraph.TRIGGER, worldX, worldY);
            if (created != null) {
                TeleportNode424.applyDefaults(created);
                if (wireFrom >= 0 && this.graph != null
                        && this.graph.connect(wireFrom, created.id, wirePort)) {
                    this.status = Component.translatable("gui.settledlands.graph.connected").getString();
                } else {
                    this.status = Component.translatable("gui.settledlands.teleport.added").getString();
                }
                this.widgets();
                this.changed();
            }
            return;
        }

        if (BlockMenu363.isTrialControlRow(row)) {
            NpcDialogueGraph.Node created = this.addAt(NpcDialogueGraph.TRIGGER, worldX, worldY);
            if (created != null) {
                TrialControlNode446.applyDefaults(created);
                if (wireFrom >= 0 && this.graph != null
                        && this.graph.connect(wireFrom, created.id, wirePort)) {
                    this.status = Component.translatable("gui.settledlands.graph.connected").getString();
                } else {
                    this.status = Component.translatable("gui.settledlands.trial_ctl.added").getString();
                }
                this.widgets();
                this.changed();
            }
            return;
        }

        int modSlot = BlockMenu363.trialModuleSlot(row);
        if (modSlot >= 0) {
            NpcDialogueGraph.Node created = this.addAt(NpcDialogueGraph.TRIGGER, worldX, worldY);
            if (created != null) {
                TrialModule447.applyDefaults(created, modSlot);
                if (wireFrom >= 0 && this.graph != null
                        && this.graph.connect(wireFrom, created.id, wirePort)) {
                    this.status = Component.translatable("gui.settledlands.graph.connected").getString();
                } else {
                    this.status = Component.translatable("gui.settledlands.trial.added_node",
                            Component.literal(BlockMenu363.label(row))).getString();
                }
                this.widgets();
                this.changed();
            }
            return;
        }

        int stock = BlockMenu363.stockRow(row);
        int type = stock < 0 ? -1 : BlockTrigger351.typeForRow(stock);
        if (type < 0) {
            this.status = Component.translatable("gui.settledlands.trial.palette").getString();
            return;
        }
        NpcDialogueGraph.Node created = this.addAt(type, worldX, worldY);
        if (created != null) {
            if (wireFrom >= 0 && this.graph != null && this.graph.connect(wireFrom, created.id, wirePort)) {
                this.status = Component.translatable("gui.settledlands.graph.connected").getString();
            } else {
                this.status = Component.translatable("gui.settledlands.trial.added_node",
                        Component.literal(BlockMenu363.label(row))).getString();
            }
            this.changed();
            return;
        }
        this.status = Component.translatable("gui.settledlands.trial.palette").getString();
    }

    @Inject(method = "switchTab", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$trialOneTab446(int tab, CallbackInfo ci) {
        if (tab == 0) return;
        if (!TrialGraph446.isTrialEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;
        this.status = Component.translatable("gui.settledlands.trial.one_tab").getString();
        ci.cancel();
    }
}
