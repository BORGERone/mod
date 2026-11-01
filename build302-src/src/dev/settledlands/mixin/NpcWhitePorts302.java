package dev.settledlands.mixin;

import dev.settledlands.npc.client.PursuitGraphNode272;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 302: white connection ports on the stock cards, on their own.
 *
 * <p>This is the only hook of the build that targets a method of Minecraft rather than one of
 * the mod's own, and it is the one that can fail to attach. In Build 301 it shared a class
 * with the blue value-wire pass, and when it could not be attached Mixin dropped the whole
 * class - the blue wires went with it. It now has a class to itself and {@code require = 0},
 * so the worst case is coloured ports and nothing else.</p>
 *
 * <p>The stock card paints its 10x10 edge squares in the colour of the node type. The fill
 * call is the same one the card uses for its body, its header and its label chips, so only a
 * rectangle that is exactly 10x10 on the left or right edge of a 220-wide card is whitened;
 * everything else passes through unchanged.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWhitePorts302 {

    /** Build 301/302: ordinary connection ports are white, not coloured by node type. */
    public static final int COL_PORT_WHITE = 0xfff2f2f2;

    /** A 10x10 square on the left or right edge of a card - the stock port, nothing else. */
    private static boolean settledlands$isPortSquare(int x1, int y1, int x2, int y2) {
        return x2 - x1 == 10 && y2 - y1 == 10
                && (x1 == -5 || x1 == PursuitGraphNode272.CARD_WIDTH - 5);
    }

    @Redirect(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            expect = -1, require = 0, remap = false)
    private void settledlands$whitePort(GuiGraphics graphics, int x1, int y1, int x2, int y2,
            int color) {
        graphics.fill(x1, y1, x2, y2,
                settledlands$isPortSquare(x1, y1, x2, y2) ? COL_PORT_WHITE : color);
    }

}
