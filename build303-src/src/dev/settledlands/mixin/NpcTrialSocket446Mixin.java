package dev.settledlands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
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

            if (button == 2) {
                Node cut = TrialCard446.wireAt(graph, canvas, mouseX, mouseY);
                if (cut == null) return;
                screen.settledlands$remember();
                TrialGraph446.setStartWaveTarget(cut, -1);
                screen.settledlands$changed();
                cir.setReturnValue(Boolean.TRUE);
                return;
            }

            Node card = TrialCard446.cardAt(graph, canvas, mouseX, mouseY);
            if (card == null) return;
            if (button == 1) {
                screen.settledlands$remember();
                TrialGraph446.setStartWaveTarget(card, -1);
                screen.settledlands$changed();
            } else {
                settledlands$dragTrialStart446 = card.id;
            }
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
            if (over < 0 || over == card.id) return;
            Node target = graph.get(over);
            if (target == null || TrialGraph446.isTrialNode(target)) return;
            if (TrialModule447.isModuleNode(target) && !TrialModule447.supportsConditionalInput(target)) {
                return;
            }
            screen.settledlands$remember();
            TrialGraph446.setStartWaveTarget(card, over);
            TrialModule447.syncCardConfig(graph);
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
            quads += curve(buffer, matrix, x1, y1, canvas.portScreenX(to, false),
                    canvas.portScreenY(to, false, 0), color, false);
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
