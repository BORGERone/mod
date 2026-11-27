package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.GraphInspectorDesc453;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.WireHover453;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453:
 * <ol>
 *   <li>Click-vs-drag threshold ({@code <= 4px}): pressing a node card to drag it no longer opens
 *       or switches the right-hand inspector sidebar; only releasing without dragging selects the
 *       node and opens its settings.</li>
 *   <li>Unified wire hover arbitration inside {@code hitCable} so hovering a colored/blue/trial
 *       wire highlights that wire exclusively instead of an unrelated white wire behind it.</li>
 *   <li>Fixes the context menu header for {@code RADIUS (15)} («Цель #id» instead of «Задание #id»).</li>
 * </ol>
 * Zero synthetic lambdas.
 */
@Mixin(value = NpcDialogueGraphScreen.class, remap = false)
public abstract class GraphClickDragMixin453 {

    private static final double DRAG_THRESHOLD_PX = 4.0D;

    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphCanvas canvas;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private NpcUi.NpcDropdown openDropdown;
    @Shadow private Set<Integer> multiSelected;
    @Shadow private Map<Integer, int[]> dragOrigins;
    @Shadow private double dragMouseWorldX;
    @Shadow private double dragMouseWorldY;
    @Shadow private int selected;
    @Shadow private int dragging;
    @Shadow private int sidebarScroll;
    @Shadow private int topBarRight;

    @Shadow private boolean isInsideInspector(double mouseX, double mouseY) { throw new AssertionError(); }
    @Shadow private boolean isShiftDownSafe() { throw new AssertionError(); }
    @Shadow private boolean isControlDownSafe() { throw new AssertionError(); }
    @Shadow private void remember() { throw new AssertionError(); }
    @Shadow private void widgets() { throw new AssertionError(); }

    @Unique private int settledlands$pendingNode453 = -1;
    @Unique private double settledlands$pressX453;
    @Unique private double settledlands$pressY453;
    @Unique private boolean settledlands$dragMoved453;

    // ---- 1. Click-vs-drag node selection (hooks into *Original so all 6 editors share it) ----

    @Inject(method = "settledlands$mouseClickedOriginal(DDI)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$deferSelectOnPress453(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        settledlands$pendingNode453 = -1;
        settledlands$dragMoved453 = false;

        if (button != 0) return;
        if (openDropdown != null) return;
        if (contextMenu != null && contextMenu.open) return;
        if (isInsideInspector(mouseX, mouseY)) return;
        if (mouseY >= 6 && mouseY <= 54 && mouseX <= topBarRight) return;
        if (graph == null || canvas == null || !canvas.contains(mouseX, mouseY)) return;
        if (canvas.hitPort(graph, mouseX, mouseY, true) >= 0) return;

        int hit = canvas.hit(graph, mouseX, mouseY);
        if (hit < 0) return;

        // Defer selection and widgets() until mouseReleased without drag!
        settledlands$pendingNode453 = hit;
        settledlands$pressX453 = mouseX;
        settledlands$pressY453 = mouseY;
        settledlands$dragMoved453 = false;

        this.dragging = hit;
        this.dragMouseWorldX = canvas.worldX(mouseX);
        this.dragMouseWorldY = canvas.worldY(mouseY);
        this.dragOrigins.clear();
        if (multiSelected != null && multiSelected.contains(hit)) {
            for (int nid : multiSelected) {
                Node n = graph.get(nid);
                if (n != null) dragOrigins.put(nid, new int[] { n.x, n.y });
            }
        } else {
            Node n = graph.get(hit);
            if (n != null) dragOrigins.put(hit, new int[] { n.x, n.y });
        }
        cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "settledlands$mouseDraggedOriginal(DDIDD)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$gateDragThreshold453(double mouseX, double mouseY, int button,
            double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (settledlands$pendingNode453 < 0 || button != 0) return;
        if (!settledlands$dragMoved453) {
            double dist = Math.hypot(mouseX - settledlands$pressX453, mouseY - settledlands$pressY453);
            if (dist <= DRAG_THRESHOLD_PX) {
                cir.setReturnValue(Boolean.TRUE);
                return;
            }
            settledlands$dragMoved453 = true;
            remember();
        }
        // Threshold crossed: allow original drag movement to move dragOrigins smoothly
    }

    @Inject(method = "settledlands$mouseReleasedOriginal(DDI)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$resolveClickOrDrag453(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int hit = settledlands$pendingNode453;
        if (hit < 0) return;

        boolean moved = settledlands$dragMoved453;
        settledlands$pendingNode453 = -1;
        settledlands$dragMoved453 = false;
        this.dragging = -1;
        this.dragOrigins.clear();

        if (!moved && button == 0 && graph != null && graph.get(hit) != null) {
            // Pure click (not a drag): select node and open the inspector sidebar
            if (multiSelected != null) {
                if (!multiSelected.contains(hit)) {
                    if (!isShiftDownSafe() && !isControlDownSafe()) {
                        multiSelected.clear();
                    }
                    multiSelected.add(hit);
                }
            }
            this.selected = hit;
            this.sidebarScroll = 0;
            widgets();
        }
        cir.setReturnValue(Boolean.TRUE);
    }

    // ---- 2. Unified wire hover arbitration inside hitCable ----

    @Inject(method = "hitCable(Ldev/settledlands/npc/NpcDialogueGraph$Node;ILdev/settledlands/npc/NpcDialogueGraph$Node;IDD)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$arbitrateCableHover453(Node from, int fromPort, Node to, int toPort,
            double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        WireHover453.WireHit hov = WireHover453.hoveredWire(screen, graph, mouseX, mouseY);
        if (hov == null) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        if (hov.kind != WireHover453.KIND_STOCK) {
            // A colored wire or waiting wire is closer to the cursor: do not highlight a stock cable behind it
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        boolean match = hov.from == from && hov.fromPort == fromPort
                && to != null && hov.toId == to.id;
        cir.setReturnValue(Boolean.valueOf(match));
    }

    // ---- 3. RADIUS (15) context menu header fix ("Цель #id" instead of "Задание #id") ----

    @Inject(method = "drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$fixRadiusMenuTitle453(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (contextMenu == null || !contextMenu.open || contextMenu.targetNodeId < 0 || graph == null) {
            return;
        }
        Node target = graph.get(contextMenu.targetNodeId);
        if (target == null || target.type != NpcDialogueGraph.RADIUS || target.special != 0) return;

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        int left = contextMenu.left;
        int top = contextMenu.top;
        int width = contextMenu.width;
        // Cleanly paint over the erroneous "Задание #id" header text with "Цель #id"
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(left + 2, top + 2, left + width - 2, top + 18, -15656669);
        String title = Component.translatable("gui.settledlands.graph.radius_title").getString()
                + " #" + target.id;
        graphics.drawString(screen.settledlands$font(), title, left + 8, top + 6, -1852043, false);
        graphics.pose().popPose();
    }

    // ---- 4. Restore footer reserve AFTER all custom inspectors & range boxes finish ----

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$popInspectorFooterReserve454(CallbackInfo ci) {
        ((GraphInspectorDesc453.FooterReserveHost) (Object) this).settledlands$popWidgetsReserve453();
    }
}
