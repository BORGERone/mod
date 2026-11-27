package dev.settledlands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.NpcWirePointsUi321;
import dev.settledlands.npc.client.VarDataCard453;
import dev.settledlands.npc.client.VarDataInspector453;
import dev.settledlands.npc.client.WireHover453;
import dev.settledlands.trial.TrialGraph446;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: client UI &amp; orange wiring for {@link VarDataNode453} (UE5 Variable node) and enhanced
 * {@link NpcDialogueGraph#VARIABLE} / {@link NpcDialogueGraph#IF} nodes. Zero synthetic lambdas.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class VarDataGraphUi453Mixin implements VarDataInspector453.Host {

    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private int selected;
    @Shadow private int inspectorX;
    @Shadow private int inspectorY;
    @Shadow private int inspectorW;
    @Shadow private int inspectorH;
    @Shadow private int sidebarScroll;
    @Shadow private int sidebarHeight;
    @Shadow private boolean buildingInspector;
    @Shadow private String status;
    @Shadow private List<?> labels;
    @Shadow private List<AbstractWidget> inspectorWidgets;

    @Shadow private Node addAt(int type, double worldX, double worldY) { throw new AssertionError(); }
    @Shadow private void remember() { throw new AssertionError(); }
    @Shadow private void changed() { throw new AssertionError(); }
    @Shadow private void widgets() { throw new AssertionError(); }
    @Shadow private void propertyChange(Runnable action) { throw new AssertionError(); }
    @Shadow private void label(String key, int x, int y) { throw new AssertionError(); }
    @Shadow private EditBox textBox(String initial, int maxLen, int x, int y,
            int width, int height, Consumer<String> setter) { throw new AssertionError(); }
    @Shadow private void numberBox(int value, int min, int max, int x, int y,
            int width, int height, Consumer<Integer> setter) { throw new AssertionError(); }
    @Shadow private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }
    @Shadow private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1,
            double x2, double y2, int color, boolean dashed) { throw new AssertionError(); }

    @Unique private static int settledlands$dragOrangeOut453 = -1;
    @Unique private static int settledlands$dragOrangeIn453 = -1;
    @Unique private Node settledlands$varDataNode453;
    @Unique private int settledlands$varDataSelected453 = -1;
    @Unique private final Map<Integer, Integer> settledlands$hiddenOrange453 = new HashMap<>();

    // ---- VarDataInspector453.Host bridge ----

    @Override
    public void settledlands$remember453() { remember(); }
    @Override
    public void settledlands$changed453() { changed(); }
    @Override
    public void settledlands$widgets453() { widgets(); }
    @Override
    public void settledlands$propertyChange453(Runnable action) { propertyChange(action); }
    @Override
    public void settledlands$label453(String key, int x, int y) { label(key, x, y); }
    @Override
    public EditBox settledlands$textBox453(String initial, int maxLen, int x, int y,
            int width, int height, Consumer<String> onChange) {
        return textBox(initial, maxLen, x, y, width, height, onChange);
    }
    @Override
    public void settledlands$numberBox453(int value, int min, int max, int x, int y,
            int width, int height, Consumer<Integer> setter) {
        numberBox(value, min, max, x, y, width, height, setter);
    }
    @Override
    public Button settledlands$button453(String key, int x, int y, int width, int height,
            Runnable action) {
        return button(key, x, y, width, height, action);
    }
    @Override
    public AbstractWidget settledlands$addDropdown453(NpcUi.NpcDropdown dropdown) {
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(dropdown);
        inspectorWidgets.add(dropdown);
        return dropdown;
    }
    @Override
    public void settledlands$deleteNode453(int nodeId) {
        if (graph == null) return;
        remember();
        graph.remove(nodeId);
        if (selected == nodeId) selected = -1;
        changed();
        widgets();
    }
    @Override
    public void settledlands$unlinkNode453(int nodeId) {
        if (graph == null) return;
        remember();
        graph.disconnectOutputs(nodeId);
        VarDataNode453.unplugOrangeFrom(graph, nodeId);
        changed();
        widgets();
    }
    @Override
    public void settledlands$addInspectorWidget453(AbstractWidget w) {
        if (w != null) inspectorWidgets.add(w);
    }
    @Override
    public void settledlands$setBuildingInspector453(boolean building) {
        this.buildingInspector = building;
    }

    // ---- NPC/Common context menu row 19 ----

    @Inject(method = "drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawVarDataMenuRow453(GuiGraphics graphics, int mouseX, int mouseY,
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
        VarDataCard453.drawMenuRow(contextMenu, graphics, screen.settledlands$font(), mouseX, mouseY);
    }

    // ---- Card drawing ----

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawVarCards453(GuiGraphics graphics, Node node, CallbackInfo ci) {
        if (node == null || graphics == null) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (VarDataNode453.isVarDataNode(node)) {
            VarDataCard453.drawVarDataCard(screen, graphics, node);
            ci.cancel();
            return;
        }
        if (node.type == NpcDialogueGraph.VARIABLE && node.special == 0) {
            VarDataCard453.drawVariableCard(screen, graphics, node);
            ci.cancel();
        }
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawIfOrangeOverlay453(GuiGraphics graphics, Node node, CallbackInfo ci) {
        if (node != null && node.type == NpcDialogueGraph.IF && node.special == 0) {
            VarDataCard453.drawIfOrangeOverlay((NpcDialogueGraphScreen) (Object) this, graphics, node);
        }
    }

    // ---- Orange data wiring (hide stock draw for VarData / separate VARIABLE wires, render orange with bend points + hover) ----

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), remap = false)
    private void settledlands$hideStockOrange453(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        settledlands$hiddenOrange453.clear();
        if (graph == null || graph.nodes == null) return;
        for (Node n : graph.nodes.values()) {
            if (n == null || n.type != NpcDialogueGraph.IF || n.varInputNode < 0) continue;
            Node src = graph.get(n.varInputNode);
            if (src != null && VarDataNode453.isOrangeSourceNode(src)) {
                settledlands$hiddenOrange453.put(n.id, n.varInputNode);
                n.varInputNode = -1;
            }
        }
        WireHover453.setHiddenOrangeVarIn(settledlands$hiddenOrange453);
    }

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawOrangeWires453(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (graph != null && !settledlands$hiddenOrange453.isEmpty()) {
            for (Map.Entry<Integer, Integer> e : settledlands$hiddenOrange453.entrySet()) {
                Node n = graph.get(e.getKey());
                if (n != null) n.varInputNode = e.getValue();
            }
        }
        settledlands$hiddenOrange453.clear();
        WireHover453.setHiddenOrangeVarIn(null);

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (graph == null || canvas == null || graphics == null || graph.nodes == null) return;
        if (!settledlands$hasAnyOrange453(graph)) return;

        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        int color = VarDataNode453.PIN_COLOR;
        int quads = 0;

        for (Node node : graph.nodes.values()) {
            if (!VarDataNode453.isOrangeInputNode(node)) continue;
            double x2 = VarDataCard453.orangeInScreenX(canvas, node);
            double y2 = VarDataCard453.orangeInScreenY(canvas, node);

            if (node.id == settledlands$dragOrangeIn453) {
                quads += curve(buffer, matrix, mouseX, mouseY, x2, y2, color, false);
                continue;
            }
            if (node.varInputNode < 0) continue;
            Node src = graph.get(node.varInputNode);
            if (src == null || src == node || !VarDataNode453.isOrangeSourceNode(src)) continue;
            if (node.type == NpcDialogueGraph.IF && src.type == NpcDialogueGraph.VARIABLE
                    && src.next == node.id) {
                // Legacy dual-purpose VARIABLE -> IF link already handled cleanly unless separate; draw orange cleanly anyway!
            }
            double x1 = VarDataCard453.orangeOutScreenX(canvas, src);
            double y1 = VarDataCard453.orangeOutScreenY(canvas, src);
            boolean hov = settledlands$dragOrangeOut453 < 0 && settledlands$dragOrangeIn453 < 0
                    && WireHover453.isColoredHovered(screen, graph, WireHover453.KIND_ORANGE,
                            src.id, node.id, mouseX, mouseY);
            List<int[]> pts = NpcWirePoints321.customBends(src,
                    NpcWirePoints321.KIND_ORANGE, node.id);
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

        if (settledlands$dragOrangeOut453 >= 0) {
            Node src = graph.get(settledlands$dragOrangeOut453);
            if (VarDataNode453.isOrangeSourceNode(src)) {
                quads += curve(buffer, matrix,
                        VarDataCard453.orangeOutScreenX(canvas, src),
                        VarDataCard453.orangeOutScreenY(canvas, src),
                        mouseX, mouseY, color, false);
            }
        }

        if (quads > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean settledlands$hasAnyOrange453(NpcDialogueGraph graph) {
        if (settledlands$dragOrangeOut453 >= 0 || settledlands$dragOrangeIn453 >= 0) return true;
        for (Node n : graph.nodes.values()) {
            if (VarDataNode453.isOrangeInputNode(n) && n.varInputNode >= 0
                    && VarDataNode453.isOrangeSourceNode(graph.get(n.varInputNode))) {
                return true;
            }
        }
        return false;
    }

    // ---- Mouse clicks (menu item 19 + orange pin drag/unplug) ----

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$mouseClickVar453(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (contextMenu != null && contextMenu.open) {
            if (button != 0 || contextMenu.targetNodeId >= 0) return;
            int id = screen.entityId();
            if (BlockTrigger351.isBlockEditorId(id)
                    || DoorGraph382.isDoorEditorId(id)
                    || ChestGraph396.isChestEditorId(id)
                    || TrialGraph446.isTrialEditorId(id)) {
                return;
            }
            if (contextMenu.isScrollbar(mouseX, mouseY)) return;
            int row = contextMenu.rowAt(mouseX, mouseY);
            if (row != VarDataNode453.MENU_ROW) return;

            double wx = contextMenu.worldX;
            double wy = contextMenu.worldY;
            contextMenu.close();
            Node created = addAt(NpcDialogueGraph.TRIGGER, wx, wy);
            if (created != null) {
                VarDataNode453.applyDefaults(created);
                status = Component.translatable("gui.settledlands.var453.added").getString();
                widgets();
                changed();
            }
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        if (button < 0 || button > 2 || graph == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || !canvas.contains(mouseX, mouseY)) return;

        // Bend points take precedence
        if (NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY) != null
                || NpcWirePointsUi321.portAt(screen, graph, mouseX, mouseY) != null) {
            return;
        }

        // Check orange output pins first
        for (Node n : graph.nodes.values()) {
            if (VarDataCard453.hitOrangeOut(canvas, n, mouseX, mouseY)) {
                if (button == 1) {
                    if (VarDataNode453.wiredOrangeConsumers(graph, n.id) > 0) {
                        remember();
                        VarDataNode453.unplugOrangeFrom(graph, n.id);
                        WireHover453.invalidateCache();
                        changed();
                        widgets();
                        cir.setReturnValue(Boolean.TRUE);
                    }
                    return;
                }
                if (button == 0) {
                    settledlands$dragOrangeOut453 = n.id;
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
            if (VarDataCard453.hitOrangeIn(canvas, n, mouseX, mouseY)) {
                if (button == 1) {
                    if (n.varInputNode >= 0) {
                        Node src = graph.get(n.varInputNode);
                        remember();
                        n.varInputNode = -1;
                        if (src != null) {
                            NpcWirePoints321.clearCustomWire(src, NpcWirePoints321.KIND_ORANGE, n.id);
                        }
                        WireHover453.invalidateCache();
                        changed();
                        widgets();
                        cir.setReturnValue(Boolean.TRUE);
                    }
                    return;
                }
                if (button == 0) {
                    settledlands$dragOrangeIn453 = n.id;
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$mouseReleaseVar453(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int out = settledlands$dragOrangeOut453;
        int in = settledlands$dragOrangeIn453;
        if (out < 0 && in < 0) return;
        settledlands$dragOrangeOut453 = -1;
        settledlands$dragOrangeIn453 = -1;
        cir.setReturnValue(Boolean.TRUE);

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (graph == null || canvas == null) return;

        int overId = canvas.hit(graph, mouseX, mouseY);
        Node over = overId >= 0 ? graph.get(overId) : null;

        if (out >= 0) {
            Node src = graph.get(out);
            if (src == null) return;
            if (over == null) {
                int[] bendPt = NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY);
                if (bendPt != null) {
                    remember();
                    NpcWirePoints321.attachColor(src, NpcWirePoints321.KIND_ORANGE, 0,
                            bendPt[0], bendPt[1]);
                    WireHover453.invalidateCache();
                    changed();
                }
                return;
            }
            if (over.id != out && VarDataNode453.isOrangeInputNode(over)) {
                remember();
                over.varInputNode = out;
                WireHover453.invalidateCache();
                changed();
                widgets();
            }
            return;
        }

        if (in >= 0) {
            Node consumer = graph.get(in);
            if (consumer != null && over != null && over.id != in
                    && VarDataNode453.isOrangeSourceNode(over)) {
                remember();
                consumer.varInputNode = over.id;
                WireHover453.invalidateCache();
                changed();
                widgets();
            }
        }
    }

    // ---- Inspector build for VarDataNode453 + math enhancements for VARIABLE & IF ----

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockForVarData453(CallbackInfo ci) {
        settledlands$varDataNode453 = null;
        if (graph == null || selected < 0) return;
        Node node = graph.get(selected);
        if (!VarDataNode453.isVarDataNode(node)) return;
        settledlands$varDataNode453 = node;
        settledlands$varDataSelected453 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildVarInspectors453(CallbackInfo ci) {
        Node custom = settledlands$varDataNode453;
        settledlands$varDataNode453 = null;
        if (custom != null) {
            selected = settledlands$varDataSelected453;
            sidebarHeight = VarDataInspector453.buildVarDataInspector(
                    graph, custom, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarScroll, inspectorWidgets, this);
            return;
        }
        if (graph == null || selected < 0) return;
        Node node = graph.get(selected);
        if (node != null && (node.type == NpcDialogueGraph.VARIABLE || node.type == NpcDialogueGraph.IF)) {
            sidebarHeight = VarDataInspector453.enhanceStockVariableAndIf(
                    graph, node, inspectorX, inspectorY, inspectorW, inspectorH,
                    sidebarScroll, sidebarHeight, labels, inspectorWidgets, this);
        }
    }
}
