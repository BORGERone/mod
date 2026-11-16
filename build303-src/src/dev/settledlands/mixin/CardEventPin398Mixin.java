package dev.settledlands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.CardEventPin398;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
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
 * Build 398: the second execution socket of a card can be wired, cut, and draws its cable.
 *
 * <p>The stock screen knows three kinds of output port: the single one every node has, the two of an
 * «Условие If» ({@code hitPort} gives index 1 only when {@code type == IF}), and the branches of a «Случайность».
 * A card is neither, so a socket of its own is invisible to the stock hit test and its wire would never be
 * drawn - the author's report about the blue pin of the chest («сейчас нельзя вытаскивать из синего узла
 * провод») is exactly what happens when a socket is painted but not registered.</p>
 *
 * <p>So this mixin registers it in three places, the same three the blue value wires use
 * ({@code NpcPursuitValuePinMixin290}) because that is the only pattern in this mod known to work in game: a
 * press starts a drag (or cuts / unplugs), a release links the socket to whatever node is under the cursor
 * through the screen's own {@code remember()} and {@code changed()}, and the cables are drawn at the tail of the
 * screen's own {@code wires} pass, in the screen's own buffer and with the screen's own {@code curve}.</p>
 *
 * <p>What the socket holds is {@code Node#falseNext} - a field the stock serialiser writes and reads for every
 * node type. Nothing here writes {@code next}, so a card's first chain can never be overwritten by its second
 * one.</p>
 *
 * <p><b>Build 399: the buffer is always closed.</b> The pass in 398 began a {@code BufferBuilder} and returned
 * early when there was nothing to draw - and a builder left open is not a harmless no-op: the next
 * {@code Tesselator#begin} of the frame throws {@code IllegalStateException: Not building!}, the graph screen
 * dies on its first frame, and from the player's chair the wand simply stops opening graphs. So the empty path
 * now closes the builder the way the blue pass of build 290 does ({@code buffer.build()} with nothing uploaded),
 * and no path of this class may leave it open.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class CardEventPin398Mixin {

    /** The card whose second socket is being dragged, or -1. */
    private static int settledlands$dragCard = -1;

    /** The screen's own cable routine: (buffer, matrix, x1, y1, x2, y2, colour, dashed). */
    @Shadow
    private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2,
            double y2, int color, boolean dashed) {
        throw new AssertionError();
    }

    /**
     * {@code this}, as the screen.
     *
     * <p>Named {@code $cardScreen} and not {@code $screen}: the blue pass of build 290 already merges a
     * {@code settledlands$screen(Object)} into this very class, and two mixins that add one method of the same
     * name and signature leave it to Mixin to pick one of them. Different names, no question.</p>
     */
    private static NpcDialogueGraphScreen settledlands$cardScreen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabCardSocket(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        try {
            if (button < 0 || button > 2) return;
            NpcDialogueGraphScreen screen = settledlands$cardScreen(this);
            if (screen.settledlands$contextOpen()) return;
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null || !canvas.contains(mouseX, mouseY)) return;

            if (button == 2) {
                Node cut = CardEventPin398.wireAt(graph, canvas, mouseX, mouseY);
                if (cut == null) return;
                screen.settledlands$remember();
                CardEventPin398.setSecondChain(cut, -1);
                screen.settledlands$changed();
                cir.setReturnValue(true);
                return;
            }

            Node card = CardEventPin398.cardAt(graph, canvas, mouseX, mouseY);
            if (card == null) return;
            if (button == 1) {
                // Right click: this socket lets go of everything it points at. The stock shortcut for a pin.
                screen.settledlands$remember();
                CardEventPin398.setSecondChain(card, -1);
                screen.settledlands$changed();
            } else {
                settledlands$dragCard = card.id;
            }
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
            // a click on a second socket must never cost the screen
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropCardSocket(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int drag = settledlands$dragCard;
        if (drag < 0) return;
        settledlands$dragCard = -1;
        cir.setReturnValue(true);
        try {
            NpcDialogueGraphScreen screen = settledlands$cardScreen(this);
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null) return;
            Node card = graph.get(drag);
            if (card == null || !CardEventPin398.isCard(card)) return;

            // The drop is on a node, never on empty canvas: a chain that points at nothing is not a chain, and
            // the author's wire would look connected while leading nowhere.
            int over = canvas.hit(graph, mouseX, mouseY);
            if (over < 0 || over == card.id) return;
            Node target = graph.get(over);
            if (target == null || CardEventPin398.isCard(target)) return;
            screen.settledlands$remember();
            CardEventPin398.setSecondChain(card, over);
            screen.settledlands$changed();
        } catch (Throwable ignored) {
            // a drop that went nowhere must never cost the screen
        }
    }

    /**
     * The second cables, drawn right after the screen's own, in the screen's own buffer: identical geometry,
     * identical setup and identical restore, one colour. The line the author is dragging is drawn from the
     * socket to the cursor, so the wire is visible while it is being pulled.
     */
    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawCardSockets(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        try {
            NpcDialogueGraphScreen screen = settledlands$cardScreen(this);
            NpcDialogueGraph graph = screen.settledlands$graph();
            NpcGraphCanvas canvas = screen.settledlands$canvas();
            if (graph == null || canvas == null || graphics == null || graph.nodes == null) return;
            if (!settledlands$hasAny(graph)) return;       // nothing to draw: no buffer is ever begun

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
                quads = settledlands$cables(buffer, graphics.pose().last().pose(), graph, canvas,
                        mouseX, mouseY);
            } catch (Throwable failure) {
                quads = 0;                                  // and the buffer is closed below, always
            }
            if (quads > 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            } else {
                // Build 399: close the builder even when it stayed empty. This is the line 398 was missing.
                buffer.build();
            }
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        } catch (Throwable ignored) {
            // drawing a second cable must never cost the screen
        }
    }

    /** Every cable of a second socket, plus the rubber band of the one being pulled. */
    private int settledlands$cables(BufferBuilder buffer, Matrix4f matrix, NpcDialogueGraph graph,
            NpcGraphCanvas canvas, double mouseX, double mouseY) {
        int color = CardEventPin398.COL_AMBER_WIRE;
        int quads = 0;
        for (Node card : graph.nodes.values()) {
            if (!CardEventPin398.isCard(card)) continue;
            double x1 = CardEventPin398.pinScreenX(canvas, card);
            double y1 = CardEventPin398.pinScreenY(canvas, card);
            if (card.id == settledlands$dragCard) {
                quads += curve(buffer, matrix, x1, y1, mouseX, mouseY, color, false);
                continue;
            }
            if (!CardEventPin398.hasWire(graph, card)) continue;
            Node to = graph.get(CardEventPin398.secondChain(card));
            if (to == null) continue;
            quads += curve(buffer, matrix, x1, y1, canvas.portScreenX(to, false),
                    canvas.portScreenY(to, false, 0), color, false);
        }
        return quads;
    }

    private static boolean settledlands$hasAny(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return false;
        for (Node node : graph.nodes.values()) {
            if (!CardEventPin398.isCard(node)) continue;
            if (node.id == settledlands$dragCard) return true;
            if (CardEventPin398.hasWire(graph, node)) return true;
        }
        return false;
    }
}
