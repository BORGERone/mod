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
import dev.settledlands.npc.client.TrialModuleCard447;
import dev.settledlands.npc.client.TrialModuleClient447;
import dev.settledlands.npc.client.WireHover453;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Build 447: drag-to-connect, right-click unlink, middle-click cut, and color-coded Bezier cable
 * rendering between {@link TrialModule447} output diamonds and the 8 left input slots on
 * {@link TrialCard446}. Zero synthetic lambdas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class TrialModuleSocket447Mixin {

    private static int settledlands$dragModOut447 = -1;
    private static int settledlands$dragHubSlotHub447 = -1;
    private static int settledlands$dragHubSlotIdx447 = -1;
    private final Map<Integer, Integer> settledlands$tmHiddenNext447 = new HashMap<>();

    @Shadow
    private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2,
            double y2, int color, boolean dashed) {
        throw new AssertionError();
    }

    private static NpcDialogueGraphScreen settledlands$tmScreen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabModuleOrSlot447(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (button < 0 || button > 2) return;
            NpcDialogueGraphScreen screen = settledlands$tmScreen(this);
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
                    TrialModuleClient447.requestInspectorRebuild();
                    cir.setReturnValue(Boolean.TRUE);
                }
                return;
            }

            if (button == 1) {
                Node inHit = TrialModuleCard447.moduleAtInputPin(graph, canvas, mouseX, mouseY);
                if (inHit != null && TrialModule447.hasIncomingControl(graph, inHit)) {
                    screen.settledlands$remember();
                    TrialModule447.disconnectIncoming(graph, inHit.id);
                    TrialModuleClient447.requestInspectorRebuild();
                    WireHover453.invalidateCache();
                    screen.settledlands$changed();
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }

            Node modHit = TrialModuleCard447.moduleAtPin(graph, canvas, mouseX, mouseY);
            if (modHit != null) {
                if (button == 1) {
                    if (modHit.next >= 0) {
                        int old = modHit.next;
                        screen.settledlands$remember();
                        if (TrialModule447.disconnectModule(graph, modHit.id)) {
                            NpcWirePoints321.clearCustomWire(modHit, NpcWirePoints321.KIND_TRIAL_MOD, old);
                        }
                        TrialModuleClient447.requestInspectorRebuild();
                        WireHover453.invalidateCache();
                        screen.settledlands$changed();
                        cir.setReturnValue(Boolean.TRUE);
                    }
                    return;
                }
                settledlands$dragModOut447 = modHit.id;
                cir.setReturnValue(Boolean.TRUE);
                return;
            }

            int[] slotHit = TrialModuleCard447.hubSlotAt(graph, canvas, mouseX, mouseY);
            if (slotHit != null) {
                if (button == 1) {
                    screen.settledlands$remember();
                    if (TrialModule447.disconnectSlot(graph, slotHit[0], slotHit[1])) {
                        TrialModuleClient447.requestInspectorRebuild();
                        WireHover453.invalidateCache();
                        screen.settledlands$changed();
                        cir.setReturnValue(Boolean.TRUE);
                    }
                    return;
                }
                settledlands$dragHubSlotHub447 = slotHit[0];
                settledlands$dragHubSlotIdx447 = slotHit[1];
                cir.setReturnValue(Boolean.TRUE);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropModuleOrSlot447(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int dragMod = settledlands$dragModOut447;
        int dragHub = settledlands$dragHubSlotHub447;
        int dragSlot = settledlands$dragHubSlotIdx447;
        if (dragMod < 0 && dragHub < 0) return;

        settledlands$dragModOut447 = -1;
        settledlands$dragHubSlotHub447 = -1;
        settledlands$dragHubSlotIdx447 = -1;
        cir.setReturnValue(Boolean.TRUE);

        try {
            NpcDialogueGraphScreen screen = settledlands$tmScreen(this);
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null) return;

            if (dragMod >= 0) {
                Node mod = graph.get(dragMod);
                int modSlot = TrialModule447.slotOf(mod);
                if (modSlot < 0) return;

                int[] slotHit = TrialModuleCard447.hubSlotAt(graph, canvas, mouseX, mouseY);
                if (slotHit != null) {
                    if (slotHit[1] == modSlot) {
                        screen.settledlands$remember();
                        TrialModule447.connectModule(graph, mod.id, slotHit[0]);
                        TrialModuleClient447.requestInspectorRebuild();
                        screen.settledlands$changed();
                    }
                    return;
                }
                int overId = canvas.hit(graph, mouseX, mouseY);
                Node over = overId >= 0 ? graph.get(overId) : null;
                if (over == null) {
                    int[] bendPt = NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY);
                    if (bendPt != null) {
                        screen.settledlands$remember();
                        int old = mod.next;
                        if (old >= 0) {
                            TrialModule447.disconnectModule(graph, mod.id);
                            NpcWirePoints321.clearCustomWire(mod, NpcWirePoints321.KIND_TRIAL_MOD, old);
                        }
                        NpcWirePoints321.attachColor(mod, NpcWirePoints321.KIND_TRIAL_MOD, modSlot,
                                bendPt[0], bendPt[1]);
                        TrialModuleClient447.requestInspectorRebuild();
                        WireHover453.invalidateCache();
                        screen.settledlands$changed();
                        return;
                    }
                }
                if (TrialGraph446.isTrialNode(over)) {
                    screen.settledlands$remember();
                    TrialModule447.connectModule(graph, mod.id, over.id);
                    TrialModuleClient447.requestInspectorRebuild();
                    screen.settledlands$changed();
                } else if (TrialModule447.supportsConditionalInput(mod)) {
                    int portInId = canvas.hitPort(graph, mouseX, mouseY, false);
                    Node target = portInId >= 0 ? graph.get(portInId) : over;
                    if (target != null && target.id != mod.id) {
                        if (TrialGraph446.isTrialNode(target)) {
                            screen.settledlands$remember();
                            TrialModule447.connectModule(graph, mod.id, target.id);
                            TrialModuleClient447.requestInspectorRebuild();
                            screen.settledlands$changed();
                        } else if (!TrialModule447.isModuleNode(target)
                                || TrialModule447.supportsConditionalInput(target)) {
                            screen.settledlands$remember();
                            TrialModule447.connectChain(graph, mod.id, target.id);
                            TrialModuleClient447.requestInspectorRebuild();
                            screen.settledlands$changed();
                        }
                    }
                }
                return;
            }

            if (dragHub >= 0 && dragSlot >= 0) {
                Node modHit = TrialModuleCard447.moduleAtPin(graph, canvas, mouseX, mouseY);
                if (modHit != null && TrialModule447.slotOf(modHit) == dragSlot) {
                    screen.settledlands$remember();
                    TrialModule447.connectModule(graph, modHit.id, dragHub);
                    TrialModuleClient447.requestInspectorRebuild();
                    screen.settledlands$changed();
                    return;
                }
                int overId = canvas.hit(graph, mouseX, mouseY);
                Node over = overId >= 0 ? graph.get(overId) : null;
                if (TrialModule447.slotOf(over) == dragSlot) {
                    screen.settledlands$remember();
                    TrialModule447.connectModule(graph, over.id, dragHub);
                    TrialModuleClient447.requestInspectorRebuild();
                    screen.settledlands$changed();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), remap = false)
    private void settledlands$hideStockModuleWires447(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        settledlands$tmHiddenNext447.clear();
        NpcDialogueGraph graph = settledlands$tmScreen(this).settledlands$graph();
        if (graph == null || graph.nodes == null) return;
        for (Node n : graph.nodes.values()) {
            if (TrialModule447.isModuleNode(n) && n.next >= 0
                    && TrialGraph446.isTrialNode(graph.get(n.next))) {
                settledlands$tmHiddenNext447.put(Integer.valueOf(n.id), Integer.valueOf(n.next));
                n.next = -1;
            }
        }
        WireHover453.setHiddenModuleNext(settledlands$tmHiddenNext447);
    }

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawModuleWires447(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        NpcDialogueGraphScreen screen = settledlands$tmScreen(this);
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph != null && !settledlands$tmHiddenNext447.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : settledlands$tmHiddenNext447.entrySet()) {
                Node n = graph.get(entry.getKey().intValue());
                if (n != null) n.next = entry.getValue().intValue();
            }
        }
        settledlands$tmHiddenNext447.clear();
        WireHover453.setHiddenModuleNext(null);

        try {
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null || graphics == null || graph.nodes == null) return;
            if (!settledlands$hasModuleWireOrDrag447(graph)) return;

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
                quads = settledlands$paintModuleWires447(buffer, graphics.pose().last().pose(),
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

    private int settledlands$paintModuleWires447(BufferBuilder buffer, Matrix4f matrix,
            NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        NpcDialogueGraphScreen screen = settledlands$tmScreen(this);
        int quads = 0;
        for (Node mod : graph.nodes.values()) {
            int slot = TrialModule447.slotOf(mod);
            if (slot < 0 || mod.next < 0) continue;
            Node hub = graph.get(mod.next);
            if (!TrialGraph446.isTrialNode(hub)) continue;
            double x1 = TrialModuleCard447.outputPinScreenX(canvas, mod);
            double y1 = TrialModuleCard447.outputPinScreenY(canvas, mod);
            double x2 = TrialCard446.inputPinScreenX(canvas, hub);
            double y2 = TrialCard446.inputPinScreenY(canvas, hub, slot);
            boolean hov = settledlands$dragModOut447 < 0 && settledlands$dragHubSlotHub447 < 0
                    && WireHover453.isColoredHovered(screen, graph, WireHover453.KIND_TRIAL_MOD,
                            mod.id, hub.id, mouseX, mouseY);
            List<int[]> pts = NpcWirePoints321.customBends(mod,
                    NpcWirePoints321.KIND_TRIAL_MOD, hub.id);
            int modColor = TrialModule447.color(slot);
            if (pts.isEmpty()) {
                quads += curve(buffer, matrix, x1, y1, x2, y2, modColor, hov);
            } else {
                double cx = x1;
                double cy = y1;
                for (int[] pt : pts) {
                    double px = canvas.screenX(pt[0]);
                    double py = canvas.screenY(pt[1]);
                    quads += curve(buffer, matrix, cx, cy, px, py, modColor, hov);
                    cx = px;
                    cy = py;
                }
                quads += curve(buffer, matrix, cx, cy, x2, y2, modColor, hov);
            }
        }

        if (settledlands$dragModOut447 >= 0) {
            Node mod = graph.get(settledlands$dragModOut447);
            int slot = TrialModule447.slotOf(mod);
            if (slot >= 0) {
                double x1 = TrialModuleCard447.outputPinScreenX(canvas, mod);
                double y1 = TrialModuleCard447.outputPinScreenY(canvas, mod);
                quads += curve(buffer, matrix, x1, y1, mouseX, mouseY,
                        TrialModule447.color(slot), true);
            }
        }
        if (settledlands$dragHubSlotHub447 >= 0 && settledlands$dragHubSlotIdx447 >= 0) {
            Node hub = graph.get(settledlands$dragHubSlotHub447);
            if (TrialGraph446.isTrialNode(hub)) {
                double x2 = TrialCard446.inputPinScreenX(canvas, hub);
                double y2 = TrialCard446.inputPinScreenY(canvas, hub, settledlands$dragHubSlotIdx447);
                quads += curve(buffer, matrix, mouseX, mouseY, x2, y2,
                        TrialModule447.color(settledlands$dragHubSlotIdx447), true);
            }
        }
        return quads;
    }

    private static boolean settledlands$hasModuleWireOrDrag447(NpcDialogueGraph graph) {
        if (settledlands$dragModOut447 >= 0 || settledlands$dragHubSlotHub447 >= 0) return true;
        if (graph == null || graph.nodes == null) return false;
        for (Node mod : graph.nodes.values()) {
            if (TrialModule447.isModuleNode(mod) && mod.next >= 0
                    && TrialGraph446.isTrialNode(graph.get(mod.next))) {
                return true;
            }
        }
        return false;
    }
}
