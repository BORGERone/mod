package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.trial.TrialGraph446;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 446: canvas card renderer and third-socket geometry helper for {@link TrialGraph446#TRIAL_TYPE}
 * ({@code 60}).
 *
 * <p>Right-edge sockets have >= 18 px vertical spacing so hit areas never overlap:</p>
 * <ul>
 *   <li>{@link #START_PIN_Y} = 32: Старт волны ({@code originStep}, mint green)</li>
 *   <li>{@code 50}: Зачистка волны (stock {@code next}, amber)</li>
 *   <li>{@link CardEventPin398#SECOND_ROW} = 68: Уничтожение ({@code falseNext}, amber)</li>
 *   <li>{@code 92}: Последний игрок (blue {@link PursuitGraphNode272} value pin)</li>
 * </ul>
 */
public final class TrialCard446 {

    public static final int START_PIN_Y = 32;
    public static final int START_COLOR = 0xFF4CE0A8;
    public static final int COL_START_WIRE = 0xFF39C692;
    private static final double HIT = 7.0D;

    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int EDGE = -5723992;
    private static final int LABEL = -5592406;
    private static final int AMBER = -3177856;
    private static final int LABEL_STRIP = 74;

    private TrialCard446() {}

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        boolean selected = screen.settledlands$isSelected(node.id);
        int textWidth = width - 16 - LABEL_STRIP;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, width + 2, height + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, width, height, BODY);
            graphics.fill(0, 0, width, 20, HEADER);
            graphics.fill(0, 19, width, 20, TrialGraph446.ACCENT_COLOR);

            String idTag = "#" + node.id;
            int idW = font.width(idTag);
            graphics.drawString(font, clip(font, tr(TrialGraph446.TITLE_KEY), width - 16 - idW - 4),
                    8, 6, TrialGraph446.ACCENT_COLOR, false);
            graphics.drawString(font, idTag, width - 8 - idW, 6, EDGE, false);

            String[] lines = TrialGraphClient446.lines(node);
            graphics.drawString(font, clip(font, lines[0], textWidth), 8, 26, 0xFFE8EEF5, false);
            graphics.drawString(font, clip(font, lines[1], textWidth), 8, 40, LABEL, false);
            graphics.drawString(font, clip(font, lines[2], textWidth), 8, 54, LABEL, false);
            graphics.drawString(font, clip(font, lines[3], textWidth), 8, 68, LABEL, false);
            boolean nobody = TrialGraph446.lastPlayer(node).isEmpty();
            graphics.drawString(font, clip(font, lines[4], textWidth), 8, 82, nobody ? EDGE : LABEL, false);

            // Socket labels (right-aligned inside the dedicated strip)
            drawPinLabel(graphics, font, width, START_PIN_Y, tr("gui.settledlands.trial.pin_start"), START_COLOR);
            drawPinLabel(graphics, font, width, CardEventPin398.FIRST_ROW, tr("gui.settledlands.trial.pin_clear"), AMBER);
            drawPinLabel(graphics, font, width, CardEventPin398.SECOND_ROW, tr("gui.settledlands.trial.pin_destroy"), AMBER);
            drawPinLabel(graphics, font, width, PursuitGraphNode272.TARGET_PIN_Y,
                    tr("gui.settledlands.trial.pin_player"), PursuitGraphNode272.COL_VALUE);

            // Socket indicators (drawn last)
            DoorCard382.socket(graphics, width, START_PIN_Y, START_COLOR);
            DoorCard382.socket(graphics, width, CardEventPin398.FIRST_ROW, AMBER);
            DoorCard382.socket(graphics, width, CardEventPin398.SECOND_ROW, AMBER);
            DoorCard382.diamond(graphics, width, PursuitGraphNode272.TARGET_PIN_Y, 5,
                    PursuitGraphNode272.COL_VALUE);
        } finally {
            pose.popPose();
        }
    }

    private static void drawPinLabel(GuiGraphics graphics, Font font, int cardWidth, int y,
            String text, int color) {
        graphics.drawString(font, text, cardWidth - 8 - font.width(text), y - 4, color, false);
    }

    public static double pinScreenX(NpcGraphCanvas canvas, Node card) {
        return canvas == null || card == null ? 0.0D : canvas.portScreenX(card, true);
    }

    public static double pinScreenY(NpcGraphCanvas canvas, Node card) {
        return canvas == null || card == null ? 0.0D : canvas.screenY(card.y + START_PIN_Y);
    }

    public static Node cardAt(NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        double limit = Math.max(HIT * 0.65D, HIT * canvas.zoom);
        for (Node node : graph.nodes.values()) {
            if (!TrialGraph446.isTrialNode(node)) continue;
            double dx = mouseX - pinScreenX(canvas, node);
            double dy = mouseY - pinScreenY(canvas, node);
            if (dx * dx + dy * dy <= limit * limit) return node;
        }
        return null;
    }

    public static Node wireAt(NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        for (Node card : graph.nodes.values()) {
            if (!TrialGraph446.isTrialNode(card)) continue;
            int target = TrialGraph446.startWaveTarget(card);
            if (target < 0) continue;
            Node to = graph.get(target);
            if (to == null || to == card) continue;
            if (PursuitGraphNode272.hitValueWire(canvas, pinScreenX(canvas, card),
                    pinScreenY(canvas, card), canvas.portScreenX(to, false),
                    canvas.portScreenY(to, false, 0), mouseX, mouseY)) {
                return card;
            }
        }
        return null;
    }

    public static boolean hasWire(NpcDialogueGraph graph, Node card) {
        if (graph == null || !TrialGraph446.isTrialNode(card)) return false;
        int target = TrialGraph446.startWaveTarget(card);
        if (target < 0) return false;
        Node to = graph.get(target);
        return to != null && to != card;
    }

    private static String tr(String key) {
        try {
            return Component.translatable(key).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    private static String clip(Font font, String text, int width) {
        if (font == null || text == null || font.width(text) <= width) return text == null ? "" : text;
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "\u2026") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut + "\u2026";
    }
}
