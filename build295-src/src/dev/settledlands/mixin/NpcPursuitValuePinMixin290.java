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
import dev.settledlands.npc.client.PursuitGraphNode272;
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
 * Build 289: the blue value wires, now with a source that is not a pursuit node.
 *
 * <p>Build 288 gave the value pins of the pursuit node the stock behaviour: same drag,
 * same drop, same drawing, same middle-click cut - only blue. Build 289 adds the other
 * end: an "In Radius" node has a blue output on its right edge, and the body that node
 * finds in its radius is the value it carries. Drag it into a pursuit node (or drag the
 * pursuit node's blue input onto the check node) and that NPC pursues whatever the
 * check node sees; the server reads the same link out of the action (§ NpcCheckValue289).
 *
 * <p>What the wire does:</p>
 *
 * <ul>
 *   <li>drag the blue output onto another pursuit node - that node takes this node's
 *       target ({@code setSource});</li>
 *   <li>drag it onto any other node - that node becomes a consumer of the value;</li>
 *   <li>drag from the blue input onto a pursuit node - this node takes that node's
 *       target;</li>
 *   <li><b>middle click on a wire cuts that one wire</b>, exactly like middle clicking
 *       an execution cable: the wire under the cursor is removed and nothing else
 *       changes. A source wire is unplugged from its input, a consumer wire drops that
 *       consumer.</li>
 *   <li>right click on a pin clears all of that pin's links (a shortcut, the middle
 *       click is the stock way);</li>
 *   <li>every one of those edits goes through the screen's undo snapshot, like the
 *       stock connect and disconnect.</li>
 * </ul>
 *
 * <p>Drawing: the wires are handed to the screen's own {@code curve} routine inside the
 * same quad buffer, shader and render state as the execution cables, so their geometry,
 * thickness and zoom behaviour are identical to a stock cable; only the colour is blue.
 * The hit test for the cut samples the same cubic with the tolerance the canvas gives
 * its own cables.</p>
 *
 * <p>Both links live in the node action (consumers {@code @ids}, source {@code #id}), so
 * they are saved with the graph and need no new field.</p>
 *
 * <p>Build 290 adds the second blue producer and the four stock consumers:</p>
 *
 * <ul>
 *   <li>the "Урон" node has the same blue output - it carries whoever hit the NPC;</li>
 *   <li>реплика, триггер, условие и переменная have a blue input on their left edge, on
 *       the same row; the link is written into the node's own {@code varInputNode}, a
 *       field the graph NBT already writes, so it needs no new field either;</li>
 *   <li>an "Условие" node keeps its stock wire to a variable node, but when it reads a
 *       body instead, that stock wire is hidden while the screen draws its cables and the
 *       blue one is drawn in its place - no double line.</li>
 * </ul>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitValuePinMixin290 {

    /** Node whose value output is being dragged, or -1. */
    private static int settledlands$dragOut = -1;

    /** Node whose value input is being dragged, or -1. */
    private static int settledlands$dragIn = -1;

    /** The screen's own cable routine: (buffer, matrix, x1, y1, x2, y2, colour, dashed). */
    @Shadow
    private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2,
            double y2, int color, boolean dashed) {
        throw new AssertionError();
    }

    private static NpcDialogueGraphScreen settledlands$screen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabValuePin(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button < 0 || button > 2) return;
        NpcDialogueGraphScreen screen = settledlands$screen(this);
        if (screen.settledlands$contextOpen()) return;
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (graph == null || canvas == null || !canvas.contains(mouseX, mouseY)) return;

        if (button == 2) {
            // Stock behaviour: the middle button cuts the cable under the cursor.
            if (settledlands$cutValueWire(screen, graph, canvas, mouseX, mouseY)) {
                cir.setReturnValue(true);
            }
            return;
        }

        // A blue output: "В радиусе" or "Урон". It is the only pin on those cards.
        Node sourceHit = settledlands$sourceNodeAt(graph, canvas, mouseX, mouseY);
        if (sourceHit != null) {
            if (button == 1) {
                // Right click unplugs every node that reads this output.
                screen.settledlands$remember();
                PursuitGraphNode272.unplugFrom(graph, sourceHit.id);
                screen.settledlands$changed();
            } else {
                settledlands$dragOut = sourceHit.id;
            }
            cir.setReturnValue(true);
            return;
        }

        // A blue input on one of the stock nodes that take a body.
        Node inputHit = settledlands$inputNodeAt(graph, canvas, mouseX, mouseY);
        if (inputHit != null) {
            if (button == 1) {
                screen.settledlands$remember();
                PursuitGraphNode272.setInputSource(inputHit, -1);
                screen.settledlands$changed();
            } else {
                settledlands$dragIn = inputHit.id;
            }
            cir.setReturnValue(true);
            return;
        }

        int id = canvas.hit(graph, mouseX, mouseY);
        if (id < 0) return;
        Node node = graph.get(id);
        if (!PursuitGraphNode272.isPursuitNode(node)) return;
        boolean out = PursuitGraphNode272.hitTargetPin(canvas, node, mouseX, mouseY);
        boolean in = !out && PursuitGraphNode272.hitTargetInPin(canvas, node, mouseX, mouseY);
        if (!out && !in) return;
        if (button == 1) {
            // Right click on a value pin clears that pin: the output drops its consumers,
            // the input drops its source.
            screen.settledlands$remember();
            if (out) {
                PursuitGraphNode272.toggleConsumer(node, -1);
            } else {
                PursuitGraphNode272.setSource(node, -1);
            }
            screen.settledlands$changed();
            cir.setReturnValue(true);
            return;
        }
        if (out) {
            settledlands$dragOut = id;
        } else {
            settledlands$dragIn = id;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropValuePin(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int out = settledlands$dragOut;
        int in = settledlands$dragIn;
        if (out < 0 && in < 0) return;
        settledlands$dragOut = -1;
        settledlands$dragIn = -1;
        cir.setReturnValue(true);

        NpcDialogueGraphScreen screen = settledlands$screen(this);
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        int id = canvas == null ? -1 : canvas.hit(graph, mouseX, mouseY);
        Node over = id >= 0 ? graph.get(id) : null;

        if (out >= 0 && PursuitGraphNode272.isValueSourceNode(graph.get(out))) {
            // A blue output can only feed a blue input: a pursuit node's target, or the
            // input of a stock node that takes a body. Anything else changes nothing.
            if (over != null && over.id != out) {
                screen.settledlands$remember();
                if (PursuitGraphNode272.isPursuitNode(over)) {
                    PursuitGraphNode272.setSource(over, out);
                } else if (PursuitGraphNode272.isValueInputNode(over)) {
                    PursuitGraphNode272.setInputSource(over, out);
                }
            }
            screen.settledlands$changed();
            return;
        }
        if (out >= 0) {
            Node source = graph.get(out);
            if (PursuitGraphNode272.isPursuitNode(source) && over != null && over.id != out) {
                screen.settledlands$remember();
                if (PursuitGraphNode272.isPursuitNode(over)) {
                    // Blue to blue: the other pursuit node now takes this node's target.
                    PursuitGraphNode272.setSource(over, source.id);
                } else {
                    PursuitGraphNode272.toggleConsumer(source, over.id);
                }
            }
        }
        if (in >= 0) {
            Node receiver = graph.get(in);
            if (receiver != null && over != null && over.id != in) {
                if (PursuitGraphNode272.isPursuitNode(receiver)
                        && (PursuitGraphNode272.isPursuitNode(over)
                        || PursuitGraphNode272.isValueSourceNode(over))) {
                    screen.settledlands$remember();
                    PursuitGraphNode272.setSource(receiver, over.id);
                } else if (PursuitGraphNode272.isValueInputNode(receiver)
                        && PursuitGraphNode272.isValueSourceNode(over)) {
                    screen.settledlands$remember();
                    PursuitGraphNode272.setInputSource(receiver, over.id);
                }
            }
        }
        screen.settledlands$changed();
    }

    /** The node whose blue output pin is under the cursor, or null. */
    private static Node settledlands$sourceNodeAt(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        if (graph == null || graph.nodes == null) return null;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isValueSourceNode(node)) continue;
            if (PursuitGraphNode272.hitCheckPin(canvas, node, mouseX, mouseY)) return node;
        }
        return null;
    }

    /** The stock node whose blue input pin is under the cursor, or null. */
    private static Node settledlands$inputNodeAt(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        if (graph == null || graph.nodes == null) return null;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isValueInputNode(node)) continue;
            if (PursuitGraphNode272.hitInputPin(canvas, node, mouseX, mouseY)) return node;
        }
        return null;
    }

    /**
     * Removes the single value wire under the cursor, the way a middle click removes an
     * execution cable. Returns true when one was cut.
     */
    private static boolean settledlands$cutValueWire(NpcDialogueGraphScreen screen,
            NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph.nodes == null) return false;

        // Inputs first: the wire a node draws into its blue input.
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isPursuitNode(node)) continue;
            int source = PursuitGraphNode272.sourceId(node);
            Node from = source < 0 ? null : graph.get(source);
            if (from == null || !PursuitGraphNode272.isPursuitNode(from)) continue;
            boolean hit = PursuitGraphNode272.hitValueWire(canvas,
                    PursuitGraphNode272.targetPinScreenX(canvas, from),
                    PursuitGraphNode272.targetPinScreenY(canvas, from),
                    PursuitGraphNode272.targetInPinScreenX(canvas, node),
                    PursuitGraphNode272.targetPinScreenY(canvas, node),
                    mouseX, mouseY);
            if (!hit) continue;
            screen.settledlands$remember();
            PursuitGraphNode272.setSource(node, -1);
            screen.settledlands$changed();
            return true;
        }

        // Then a wire from a check node into a pursuit node's blue input.
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isPursuitNode(node)) continue;
            int source = PursuitGraphNode272.sourceId(node);
            Node from = source < 0 ? null : graph.get(source);
            if (from == null || !PursuitGraphNode272.isCheckValueNode(from)) continue;
            boolean hit = PursuitGraphNode272.hitValueWire(canvas,
                    PursuitGraphNode272.checkPinScreenX(canvas, from),
                    PursuitGraphNode272.checkPinScreenY(canvas, from),
                    PursuitGraphNode272.targetInPinScreenX(canvas, node),
                    PursuitGraphNode272.targetPinScreenY(canvas, node),
                    mouseX, mouseY);
            if (!hit) continue;
            screen.settledlands$remember();
            PursuitGraphNode272.setSource(node, -1);
            screen.settledlands$changed();
            return true;
        }

        // Then a wire from a blue output into a stock node's blue input.
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isValueInputNode(node)) continue;
            int source = PursuitGraphNode272.inputSource(node);
            Node from = source < 0 ? null : graph.get(source);
            if (from == null || !PursuitGraphNode272.isValueSourceNode(from)) continue;
            boolean hit = PursuitGraphNode272.hitValueWire(canvas,
                    PursuitGraphNode272.checkPinScreenX(canvas, from),
                    PursuitGraphNode272.checkPinScreenY(canvas, from),
                    PursuitGraphNode272.inputPinScreenX(canvas, node),
                    PursuitGraphNode272.inputPinScreenY(canvas, node),
                    mouseX, mouseY);
            if (!hit) continue;
            screen.settledlands$remember();
            PursuitGraphNode272.setInputSource(node, -1);
            screen.settledlands$changed();
            return true;
        }

        // Then the outputs: one wire per consumer.
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isPursuitNode(node)) continue;
            double outX = PursuitGraphNode272.targetPinScreenX(canvas, node);
            double outY = PursuitGraphNode272.targetPinScreenY(canvas, node);
            for (Integer consumerId : PursuitGraphNode272.consumers(node)) {
                Node consumer = consumerId == null ? null : graph.get(consumerId);
                if (consumer == null) continue;
                boolean hit = PursuitGraphNode272.hitValueWire(canvas, outX, outY,
                        PursuitGraphNode272.consumerAnchorX(canvas, consumer),
                        PursuitGraphNode272.consumerAnchorY(canvas, consumer), mouseX, mouseY);
                if (!hit) continue;
                screen.settledlands$remember();
                PursuitGraphNode272.toggleConsumer(node, consumer.id);
                screen.settledlands$changed();
                return true;
            }
        }
        return false;
    }

    /**
     * Any change to the graph re-materialises every wired-in target, so what is
     * saved is what the cards show.
     */
    @Inject(method = "changed()V", at = @At("HEAD"), remap = false)
    private void settledlands$refreshValueLinks(CallbackInfo ci) {
        PursuitGraphNode272.refreshDependents(settledlands$screen(this).settledlands$graph());
    }

    /**
     * An "Условие" node that reads a body instead of a variable would be drawn twice: the
     * screen draws its own wire to {@code varInputNode}, and this mixin draws the blue one.
     * The stock wire is hidden for the duration of the stock pass and put back here, so
     * exactly one cable per wire remains and it is blue.
     */
    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), remap = false)
    private void settledlands$hideStockIfWires(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        PursuitGraphNode272.hideStockInputWires(settledlands$screen(this).settledlands$graph());
    }

    /**
     * Draws the value wires straight after the screen's own wires, in the screen's own
     * buffer: identical geometry, identical setup and identical restore, only a blue
     * colour and the pursuit endpoints.
     */
    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawValueWires(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        NpcDialogueGraphScreen screen = settledlands$screen(this);
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        PursuitGraphNode272.restoreStockInputWires(graph);
        if (graph == null || canvas == null || graphics == null) return;
        if (!PursuitGraphNode272.hasValueWires(graph, settledlands$dragOut, settledlands$dragIn)) {
            return;
        }

        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        int color = PursuitGraphNode272.COL_VALUE_WIRE;
        int quads = 0;

        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isPursuitNode(node)) continue;
            double outX = PursuitGraphNode272.targetPinScreenX(canvas, node);
            double outY = PursuitGraphNode272.targetPinScreenY(canvas, node);
            double inX = PursuitGraphNode272.targetInPinScreenX(canvas, node);

            // Value input: the node this one takes its target from.
            int source = PursuitGraphNode272.sourceId(node);
            if (node.id == settledlands$dragIn) {
                quads += curve(buffer, matrix, mouseX, mouseY, inX, outY, color, false);
            } else if (source >= 0) {
                NpcDialogueGraph.Node from = graph.get(source);
                if (from != null && PursuitGraphNode272.isPursuitNode(from)) {
                    quads += curve(buffer, matrix,
                            PursuitGraphNode272.targetPinScreenX(canvas, from),
                            PursuitGraphNode272.targetPinScreenY(canvas, from), inX, outY, color,
                            false);
                } else if (from != null && PursuitGraphNode272.isCheckValueNode(from)) {
                    // The body comes from an "In Radius" node: same cable, blue.
                    quads += curve(buffer, matrix,
                            PursuitGraphNode272.checkPinScreenX(canvas, from),
                            PursuitGraphNode272.checkPinScreenY(canvas, from), inX, outY, color,
                            false);
                }
            }

            // Value output: the rubber band, or one wire per consumer.
            if (node.id == settledlands$dragOut) {
                quads += curve(buffer, matrix, outX, outY, mouseX, mouseY, color, false);
                continue;
            }
            for (Integer consumerId : PursuitGraphNode272.consumers(node)) {
                NpcDialogueGraph.Node consumer = consumerId == null ? null : graph.get(consumerId);
                if (consumer == null) continue;
                quads += curve(buffer, matrix, outX, outY,
                        PursuitGraphNode272.consumerAnchorX(canvas, consumer),
                        PursuitGraphNode272.consumerAnchorY(canvas, consumer), color, false);
            }
        }

        // Blue inputs of the stock nodes: one cable from the source pin to the input pin.
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (!PursuitGraphNode272.isValueInputNode(node)) continue;
            double inY = PursuitGraphNode272.inputPinScreenY(canvas, node);
            if (node.id == settledlands$dragIn) {
                quads += curve(buffer, matrix, mouseX, mouseY,
                        PursuitGraphNode272.inputPinScreenX(canvas, node), inY, color, false);
                continue;
            }
            int source = PursuitGraphNode272.inputSource(node);
            NpcDialogueGraph.Node from = source < 0 ? null : graph.get(source);
            if (from == null || !PursuitGraphNode272.isValueSourceNode(from)) continue;
            quads += curve(buffer, matrix,
                    PursuitGraphNode272.checkPinScreenX(canvas, from),
                    PursuitGraphNode272.checkPinScreenY(canvas, from),
                    PursuitGraphNode272.inputPinScreenX(canvas, node), inY, color, false);
        }

        // Rubber band from a check node's blue output while it is being dragged.
        NpcDialogueGraph.Node dragged = settledlands$dragOut < 0 ? null : graph.get(settledlands$dragOut);
        if (dragged != null && PursuitGraphNode272.isValueSourceNode(dragged)) {
            quads += curve(buffer, matrix,
                    PursuitGraphNode272.checkPinScreenX(canvas, dragged),
                    PursuitGraphNode272.checkPinScreenY(canvas, dragged), mouseX, mouseY, color,
                    false);
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
}
