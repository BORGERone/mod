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
 * Build 286: the two value pins of the pursuit node, drawn by the canvas' own cable
 * routine.
 *
 * <p>Build 284 drew them with one filled rectangle per screen pixel. That looked
 * acceptable at 100% and fell apart when zoomed out, because the pieces are screen
 * pixels while the graph shrinks. The wires are now handed to
 * {@code NpcDialogueGraphScreen#curve}, the same method the screen uses for its own
 * cables, inside the same quad buffer and with the same shader and render state - so
 * a value wire is exactly as smooth, as thick and as zoom independent as an
 * execution wire, just blue.</p>
 *
 * <p>Pins:</p>
 * <ul>
 *   <li>blue diamond on the right - the target this node pursues; wire it to any
 *       node, or straight into the blue diamond of another pursuit node;</li>
 *   <li>blue diamond on the left - the target this node takes in;</li>
 *   <li>a value wire that ends on a pursuit node always lands on its blue input,
 *       never on its execution input.</li>
 * </ul>
 *
 * <p>Both links live in the node action (consumers {@code @ids}, source {@code #id}),
 * so they are saved with the graph and need no new field.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitValuePinMixin286 {

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
        if (button != 0 && button != 1) return;
        NpcDialogueGraphScreen screen = settledlands$screen(this);
        if (screen.settledlands$contextOpen()) return;
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (graph == null || canvas == null) return;
        int id = canvas.hit(graph, mouseX, mouseY);
        if (id < 0) return;
        Node node = graph.get(id);
        if (!PursuitGraphNode272.isPursuitNode(node)) return;
        boolean out = PursuitGraphNode272.hitTargetPin(canvas, node, mouseX, mouseY);
        boolean in = !out && PursuitGraphNode272.hitTargetInPin(canvas, node, mouseX, mouseY);
        if (!out && !in) return;
        if (button == 1) {
            // Right click on a value pin unplugs it: the output drops its consumers, the
            // input drops its source. That is the only way back once a wire is connected.
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

        if (out >= 0) {
            Node source = graph.get(out);
            if (PursuitGraphNode272.isPursuitNode(source) && over != null && over.id != out) {
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
            if (PursuitGraphNode272.isPursuitNode(receiver) && over != null && over.id != in
                    && PursuitGraphNode272.isPursuitNode(over)) {
                PursuitGraphNode272.setSource(receiver, over.id);
            }
        }
        screen.settledlands$changed();
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
