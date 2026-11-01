package dev.settledlands.mixin;

import dev.settledlands.npc.client.WorkspaceSkin304;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Build 302: white ordinary wiring, in a class of its own.
 *
 * <p>The same three hooks lived inside the value-wire mixin in Build 301, and in that build the
 * blue cables stopped being drawn. Nothing here touches the value-wire code: this class only
 * changes a colour, and keeping it apart means a problem here can never take the blue wires
 * with it.</p>
 *
 * <ul>
 *   <li><b>cables</b> - every stock cable goes through {@code cable}, which takes its colour as
 *       the seventh argument, so replacing that one argument covers ordinary lines and
 *       relation-coloured lines at once. The blue value pass draws its own curves from a
 *       different method and keeps its colour.</li>
 * </ul>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWhiteWiring302 {

    @ModifyArg(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;cable(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;Ldev/settledlands/npc/NpcDialogueGraph$Node;ILdev/settledlands/npc/NpcDialogueGraph$Node;IIZ)I"),
            index = 6, require = 0, expect = -1, remap = false)
    private int settledlands$whiteCable(int color) {
        return WorkspaceSkin304.CABLE;
    }

    /** The rubber band a cable being dragged is drawn with goes through the same routine. */
    @ModifyArg(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;curve(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;DDDDIZ)I"),
            index = 6, require = 0, expect = -1, remap = false)
    private int settledlands$whiteBand(int color) {
        return WorkspaceSkin304.CABLE;
    }
}
