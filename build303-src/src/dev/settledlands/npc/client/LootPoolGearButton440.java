package dev.settledlands.npc.client;

import dev.settledlands.chest.LootPool436;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Build 440: 20x20 gear-icon widget placed next to Loot Pool selector rows (and reusable 12x12
 * gear glyph drawer for dropdown rows and the in-game pool editor header).
 */
public final class LootPoolGearButton440 extends AbstractWidget {

    private final Runnable onPress;
    private final int accentColor;

    public LootPoolGearButton440(int x, int y, int width, int height,
            Component tooltip, Runnable onPress) {
        this(x, y, width, height, tooltip, LootPool436.ACCENT_COLOR, onPress);
    }

    public LootPoolGearButton440(int x, int y, int width, int height,
            Component tooltip, int accentColor, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.accentColor = accentColor;
        this.onPress = onPress;
        if (tooltip != null) {
            setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (active && visible && onPress != null) {
            onPress.run();
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (graphics == null) return;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = active && isHoveredOrFocused();
        int border = hovered ? (0xFF000000 | accentColor) : 0xFF424B5E;
        int bg = hovered ? 0xFF282218 : 0xFF171B24;
        graphics.fill(x, y, x + w, y + h, border);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int gx = x + (w - 12) / 2;
        int gy = y + (h - 12) / 2;
        drawGearIcon(graphics, gx, gy, hovered, bg, accentColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /**
     * Draws a crisp 12x12 pixel-art 8-tooth gear icon at {@code (x, y)}, echoing
     * {@code GuideDialogueScreen#drawGearButton}.
     */
    public static void drawGearIcon(GuiGraphics graphics, int x, int y, boolean hovered, int holeBg) {
        drawGearIcon(graphics, x, y, hovered, holeBg, LootPool436.ACCENT_COLOR);
    }

    public static void drawGearIcon(GuiGraphics graphics, int x, int y, boolean hovered, int holeBg,
            int accentColor) {
        if (graphics == null) return;
        int fg = hovered ? (0xFF000000 | accentColor) : 0xFFD4DCE8;
        // Cardinal teeth
        graphics.fill(x + 5, y + 1, x + 7, y + 11, fg);
        graphics.fill(x + 1, y + 5, x + 11, y + 7, fg);
        // Diagonal teeth
        graphics.fill(x + 2, y + 2, x + 4, y + 4, fg);
        graphics.fill(x + 8, y + 2, x + 10, y + 4, fg);
        graphics.fill(x + 2, y + 8, x + 4, y + 10, fg);
        graphics.fill(x + 8, y + 8, x + 10, y + 10, fg);
        // Inner gear body ring & central axle hole
        graphics.fill(x + 3, y + 3, x + 9, y + 9, fg);
        graphics.fill(x + 5, y + 5, x + 7, y + 7, holeBg);
    }

    /** Draws a framed 12x12 inline gear button (used on expanded dropdown selector rows). */
    public static void drawInlineGearFrame(GuiGraphics graphics, int x, int y, boolean hovered) {
        drawInlineGearFrame(graphics, x, y, hovered, LootPool436.ACCENT_COLOR);
    }

    public static void drawInlineGearFrame(GuiGraphics graphics, int x, int y, boolean hovered,
            int accentColor) {
        if (graphics == null) return;
        int frame = hovered ? (0xFF000000 | accentColor) : 0xFF3E4656;
        int bg = hovered ? 0xFF282218 : 0xFF141820;
        graphics.fill(x, y, x + 12, y + 12, frame);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, bg);
        drawGearIcon(graphics, x, y, hovered, bg, accentColor);
    }
}
