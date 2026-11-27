package dev.settledlands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcWirePointsUi321;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.npc.client.WireHover453;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: handles wiring, right-click unlink, middle-click cut, and cable rendering for the
 * Trial Spawner card's third execution socket ({@code pin_start} stored in {@code Node#originStep}).
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcTrialSocket446Mixin {

    private static int settledlands$dragTrialStart446 = -1;

    @Shadow
    private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2,
            double y2, int color, boolean dashed) {
        throw new AssertionError();
    }

    private static NpcDialogueGraphScreen settledlands$trialSocketScreen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabTrialStartSocket446(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (button < 0 || button > 2) return;
            NpcDialogueGraphScreen screen = settledlands$trialSocketScreen(this);
            if (screen.settledlands$contextOpen()) return;
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null || !canvas.contains(mouseX, mouseY)) return;

            if (NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY) != null
                    || NpcWirePointsUi321.portAt(screen, graph, mouseX, mouseY) != null) {
                return;
            }

            if (button == 2) {
                if (WireHover453.cutNonStockWire(screen, graph, mouseX, mouseY)) {
                    cir.setReturnValue(Boolean.TRUE);
                }
                return;
            }

            Node card = TrialCard446.cardAt(graph, canvas, mouseX, mouseY);
            if (card == null) return;
            if (button == 1) {
                int old = TrialGraph446.startWaveTarget(card);
                if (old >= 0) {
                    screen.settledlands$remember();
                    TrialGraph446.setStartWaveTarget(card, -1);
                    NpcWirePoints321.clearCustomWire(card, NpcWirePoints321.KIND_TRIAL_START, old);
                    TrialModule447.syncCardConfig(graph);
                    WireHover453.invalidateCache();
                    screen.settledlands$changed();
                    cir.setReturnValue(Boolean.TRUE);
                }
                return;
            }
            settledlands$dragTrialStart446 = card.id;
            cir.setReturnValue(Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropTrialStartSocket446(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int drag = settledlands$dragTrialStart446;
        if (drag < 0) return;
        settledlands$dragTrialStart446 = -1;
        cir.setReturnValue(Boolean.TRUE);
        try {
            NpcDialogueGraphScreen screen = settledlands$trialSocketScreen(this);
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null) return;
            Node card = graph.get(drag);
            if (!TrialGraph446.isTrialNode(card)) return;

            int over = canvas.hitPort(graph, mouseX, mouseY, false);
            if (over < 0) over = canvas.hit(graph, mouseX, mouseY);
            if (over < 0) {
                int[] bendPt = NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY);
                if (bendPt != null) {
                    screen.settledlands$remember();
                    int old = TrialGraph446.startWaveTarget(card);
                    if (old >= 0) {
                        TrialGraph446.setStartWaveTarget(card, -1);
                        NpcWirePoints321.clearCustomWire(card, NpcWirePoints321.KIND_TRIAL_START, old);
                    }
                    NpcWirePoints321.attachColor(card, NpcWirePoints321.KIND_TRIAL_START, 0,
                            bendPt[0], bendPt[1]);
                    TrialModule447.syncCardConfig(graph);
                    WireHover453.invalidateCache();
                    screen.settledlands$changed();
                }
                return;
            }
            if (over == card.id) return;
            Node target = graph.get(over);
            if (target == null || TrialGraph446.isTrialNode(target)) return;
            if (TrialModule447.isModuleNode(target) && !TrialModule447.supportsConditionalInput(target)) {
                return;
            }
            screen.settledlands$remember();
            TrialGraph446.setStartWaveTarget(card, over);
            TrialModule447.syncCardConfig(graph);
            WireHover453.invalidateCache();
            screen.settledlands$changed();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawTrialStartWires446(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        try {
            NpcDialogueGraphScreen screen = settledlands$trialSocketScreen(this);
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null || graphics == null || graph.nodes == null) return;
            if (!settledlands$hasAnyStartWire446(graph)) return;

            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int quads;
            try {
                quads = settledlands$paintStartWires446(buffer, graphics.pose().last().pose(),
                        graph, canvas, mouseX, mouseY);
            } catch (Throwable failure) {
                quads = 0;
            }
            if (quads > 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            } else {
                buffer.build();
            }
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        } catch (Throwable ignored) {
        }
    }

    private int settledlands$paintStartWires446(BufferBuilder buffer, Matrix4f matrix,
            NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        NpcDialogueGraphScreen screen = settledlands$trialSocketScreen(this);
        int color = TrialCard446.COL_START_WIRE;
        int quads = 0;
        for (Node card : graph.nodes.values()) {
            if (!TrialGraph446.isTrialNode(card)) continue;
            double x1 = TrialCard446.pinScreenX(canvas, card);
            double y1 = TrialCard446.pinScreenY(canvas, card);
            if (card.id == settledlands$dragTrialStart446) {
                quads += curve(buffer, matrix, x1, y1, mouseX, mouseY, color, false);
                continue;
            }
            if (!TrialCard446.hasWire(graph, card)) continue;
            Node to = graph.get(TrialGraph446.startWaveTarget(card));
            if (to == null) continue;
            boolean hov = settledlands$dragTrialStart446 < 0
                    && WireHover453.isColoredHovered(screen, graph, WireHover453.KIND_TRIAL_START,
                            card.id, to.id, mouseX, mouseY);
            List<int[]> pts = NpcWirePoints321.customBends(card,
                    NpcWirePoints321.KIND_TRIAL_START, to.id);
            double x2 = canvas.portScreenX(to, false);
            double y2 = canvas.portScreenY(to, false, 0);
            if (pts.isEmpty()) {
                quads += curve(buffer, matrix, x1, y1, x2, y2, color, hov);
            } else {
                double cx = x1;
                double cy = y1;
                for (int[] pt : pts) {
                    double px = canvas.screenX(pt[0]);
                    double py = canvas.screenY(pt[1]);
                    quads += curve(buffer, matrix, cx, cy, px, py, color, hov);
                    cx = px;
                    cy = py;
                }
                quads += curve(buffer, matrix, cx, cy, x2, y2, color, hov);
            }
        }
        return quads;
    }

    private static boolean settledlands$hasAnyStartWire446(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return false;
        for (Node node : graph.nodes.values()) {
            if (!TrialGraph446.isTrialNode(node)) continue;
            if (node.id == settledlands$dragTrialStart446) return true;
            if (TrialCard446.hasWire(graph, node)) return true;
        }
        return false;
    }
}
