package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.TeleportNode424;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 424: canvas card, NPC-menu row painter and inspector callback factories for the «Телепорт»
 * node. Keeping callback factories here keeps all synthetic lambda methods off the mixin target
 * class ({@code mixin_clash_check.py}).
 */
public final class TeleportCard424 {

    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int LABEL = -3947581;
    private static final int MUTED = -7693400;
    private static final int PORT = -855310;
    private static final int ROW_HOVER = -13416358;
    private static final int ROW_IDLE = -15065049;
    private static final int STRIP_BG = -14800581;
    private static final int THUMB = -10193781;
    private static final int THUMB_HOT = -7035976;

    private TeleportCard424() {}

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        int accent = TeleportNode424.ACCENT_COLOR;
        boolean selected = screen.settledlands$isSelected(node.id);
        int textWidth = width - 16;

        String dim = TeleportNode424.dimensionOf(node);
        String worldLabel = worldDisplayName(dim);
        TeleportNode424.Parsed parsed = TeleportNode424.parse(node.action);
        String coordLine = parsed == null
                ? "0, 0, 0"
                : "X: " + TeleportNode424.formatCoord(parsed.x)
                        + "  Y: " + TeleportNode424.formatCoord(parsed.y)
                        + "  Z: " + TeleportNode424.formatCoord(parsed.z);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, accent);

            String idTag = "#" + node.id;
            int idW = font.width(idTag);
            graphics.drawString(font, clip(font, tr(TeleportNode424.TITLE_KEY), textWidth - idW - 6),
                    8, 6, accent, false);
            graphics.drawString(font, idTag, width - 8 - idW, 6, MUTED, false);

            graphics.drawString(font,
                    clip(font, tr("gui.settledlands.teleport.world") + ": " + worldLabel, textWidth),
                    8, 28, LABEL, false);
            graphics.drawString(font, clip(font, coordLine, textWidth), 8, 48, -1, false);

            String hint = node.next >= 0
                    ? "-> #" + node.next
                    : tr("gui.settledlands.teleport.card_hint");
            graphics.drawString(font, clip(font, hint, textWidth), 8, 76, MUTED, false);

            int mid = height / 2;
            graphics.fill(-5, mid - 5, 5, mid + 5, PORT);
            graphics.fill(width - 5, mid - 5, width + 5, mid + 5, PORT);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Paints row 17 («+ Телепорт») in the NPC/common context menu and redraws the 18-row scrollbar
     * thumb cleanly so it stays inside the track for {@code scrollRow = 0..13}.
     */
    public static void drawMenuRow(NpcGraphContextMenu menu, GuiGraphics graphics, Font font,
            int mouseX, int mouseY) {
        if (menu == null || graphics == null || font == null || !menu.open || menu.targetNodeId >= 0) {
            return;
        }
        int rowX0 = menu.left + 2;
        int rowX1 = menu.left + menu.width - 10;
        int visible = TeleportNode424.TELEPORT_ROW - menu.scrollRow;
        int hover = menu.rowAt(mouseX, mouseY);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 600.0D);
        try {
            if (visible >= 0 && visible < TeleportNode424.NPC_VISIBLE_ROWS) {
                int y = menu.top + 20 + visible * 22;
                if (y + 22 <= menu.top + menu.height - 2) {
                    boolean hot = hover == TeleportNode424.TELEPORT_ROW;
                    graphics.fill(rowX0, y, rowX1, y + 22, hot ? ROW_HOVER : ROW_IDLE);
                    String text = clip(font, tr(TeleportNode424.MENU_LABEL_KEY), rowX1 - rowX0 - 10);
                    graphics.drawString(font, text, menu.left + 8, y + 7,
                            hot ? -1 : TeleportNode424.ACCENT_COLOR, false);
                }
            }

            int sx = menu.left + menu.width - 8;
            int sy = menu.top + 22;
            int sh = menu.height - 24;
            if (sh > 0) {
                graphics.fill(sx, sy, sx + 6, sy + sh, STRIP_BG);
                int thumbH = Math.max(16, sh * TeleportNode424.NPC_VISIBLE_ROWS / TeleportNode424.NPC_MENU_ITEMS);
                int clampedScroll = Math.max(0, Math.min(TeleportNode424.NPC_MAX_SCROLL, menu.scrollRow));
                int thumbY = sy + (sh - thumbH) * clampedScroll / TeleportNode424.NPC_MAX_SCROLL;
                boolean hotThumb = mouseX >= sx && mouseX <= sx + 6
                        && mouseY >= thumbY && mouseY <= thumbY + thumbH;
                graphics.fill(sx, thumbY, sx + 6, thumbY + thumbH, hotThumb ? THUMB_HOT : THUMB);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    public static List<Component> worldDropdownEntries(String currentDimension) {
        List<Component> out = new ArrayList<>(TeleportNode424.worldCount() + 1);
        for (int i = 0; i < TeleportNode424.worldCount(); i++) {
            out.add(Component.translatable(TeleportNode424.worldKey(i)));
        }
        if (TeleportNode424.worldIndexForId(currentDimension) < 0
                && currentDimension != null && !currentDimension.isEmpty()) {
            out.add(Component.literal(currentDimension));
        }
        return out;
    }

    public static int worldDropdownIndex(String currentDimension) {
        int idx = TeleportNode424.worldIndexForId(currentDimension);
        if (idx >= 0) return idx;
        return currentDimension != null && !currentDimension.isEmpty()
                ? TeleportNode424.worldCount() : 0;
    }

    public static IntConsumer worldPicker(Consumer<Runnable> commit, Node node, String initialCustomDim) {
        return picked -> {
            if (picked >= 0 && picked < TeleportNode424.worldCount()) {
                commit.accept(() -> TeleportNode424.setWorld(node, picked));
            } else if (initialCustomDim != null && !initialCustomDim.isEmpty()) {
                commit.accept(() -> TeleportNode424.setDimensionId(node, initialCustomDim));
            }
        };
    }

    public static Consumer<String> xSetter(Node node) {
        return val -> TeleportNode424.setX(node, val);
    }

    public static Consumer<String> ySetter(Node node) {
        return val -> TeleportNode424.setY(node, val);
    }

    public static Consumer<String> zSetter(Node node) {
        return val -> TeleportNode424.setZ(node, val);
    }

    public static Runnable unlinkAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            int nodeId) {
        return () -> {
            screen.settledlands$remember();
            if (graph != null) graph.disconnectOutputs(nodeId);
            screen.settledlands$changed();
        };
    }

    public static String worldDisplayName(String dimension) {
        String key = TeleportNode424.worldKeyForId(dimension);
        return key != null ? tr(key) : dimension;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
