package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 446/447: canvas hub card renderer and socket geometry helper for {@link TrialGraph446#TRIAL_TYPE}
 * ({@code 60}).
 *
 * <p>Left-edge input slots ({@code 0..7}, y = 28..154 step 18) receive wires from the 8 specialized
 * {@link TrialModule447} cards. Right-edge sockets stay on rows 32/50/68/92:</p>
 * <ul>
 *   <li>{@link #START_PIN_Y} = 32: Старт волны ({@code originStep}, mint green)</li>
 *   <li>{@code 50}: Зачистка волны (stock {@code next}, amber)</li>
 *   <li>{@link CardEventPin398#SECOND_ROW} = 68: Уничтожение ({@code falseNext}, amber)</li>
 *   <li>{@code 92}: Последний игрок (blue {@link PursuitGraphNode272} value pin)</li>
 * </ul>
 */
public final class TrialCard446 {

    public static final int CARD_W = 256;
    public static final int CARD_H = 176;

    public static final int INPUT_PIN_X = 0;
    public static final int INPUT_PIN_Y0 = 28;
    public static final int INPUT_PIN_STEP = 18;

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
    private static final int DIVIDER = 0xFF283444;

    private TrialCard446() {}

    public static int inputPinY(int slot) {
        return INPUT_PIN_Y0 + slot * INPUT_PIN_STEP;
    }

    public static double inputPinScreenX(NpcGraphCanvas canvas, Node hub) {
        return canvas == null || hub == null ? 0.0D : canvas.screenX(hub.x);
    }

    public static double inputPinScreenY(NpcGraphCanvas canvas, Node hub, int slot) {
        return canvas == null || hub == null ? 0.0D : canvas.screenY(hub.y + inputPinY(slot));
    }

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (canvas == null || font == null) return;

        int width = CARD_W;
        int height = CARD_H;
        boolean selected = screen.settledlands$isSelected(node.id);

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

            // Vertical divider between left module slots and right summary/outputs
            graphics.fill(85, 24, 86, height - 6, DIVIDER);

            // Left 8 module input slots
            int connected = 0;
            for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
                int py = inputPinY(s);
                boolean wired = TrialModule447.connectedModule(graph, node, s) != null;
                if (wired) connected++;
                int col = wired ? TrialModule447.color(s) : EDGE;
                graphics.drawString(font, clip(font, tr(TrialModule447.pinKey(s)), 72),
                        8, py - 4, col, false);
                if (wired) {
                    DoorCard382.diamond(graphics, INPUT_PIN_X, py, 4, TrialModule447.color(s));
                } else {
                    DoorCard382.diamond(graphics, INPUT_PIN_X, py, 4, TrialModule447.dimColor(s));
                    graphics.fill(-1, py - 1, 2, py + 2, BODY);
                }
            }

            // Middle-right summary (top section next to right pins)
            int midX = 92;
            int midW = 86;
            String[] lines = TrialGraphClient446.lines(node);
            graphics.drawString(font, clip(font, lines[0], midW), midX, 28, 0xFFE8EEF5, false);
            graphics.drawString(font, clip(font, lines[1], midW), midX, 46, LABEL, false);
            graphics.drawString(font, clip(font, lines[2], midW), midX, 64, LABEL, false);
            graphics.drawString(font, clip(font, lines[3], midW), midX, 88, LABEL, false);

            // Right output sockets & labels
            drawPinLabel(graphics, font, width, START_PIN_Y, tr("gui.settledlands.trial.pin_start"), START_COLOR);
            drawPinLabel(graphics, font, width, CardEventPin398.FIRST_ROW, tr("gui.settledlands.trial.pin_clear"), AMBER);
            drawPinLabel(graphics, font, width, CardEventPin398.SECOND_ROW, tr("gui.settledlands.trial.pin_destroy"), AMBER);
            drawPinLabel(graphics, font, width, PursuitGraphNode272.TARGET_PIN_Y,
                    tr("gui.settledlands.trial.pin_player"), PursuitGraphNode272.COL_VALUE);

            // Lower-right summary box
            graphics.fill(midX, 106, width - 8, 107, DIVIDER);
            int botW = width - 8 - midX;
            String modStatus = TrialGraphClient446.safe("gui.settledlands.trial.hub_modules",
                    connected + "/" + TrialModule447.MODULE_COUNT);
            graphics.drawString(font, clip(font, modStatus, botW), midX, 114,
                    connected == TrialModule447.MODULE_COUNT ? 0xFF56D38C : 0xFFF5B841, false);

            TrialConfig446 cfg = TrialGraph446.configOf(node);
            String rangeLine = TrialGraphClient446.safe("gui.settledlands.trial.hub_range",
                    cfg.activationRange + "/" + cfg.spawnRange);
            graphics.drawString(font, clip(font, rangeLine, botW), midX, 131, LABEL, false);

            boolean nobody = TrialGraph446.lastPlayer(node).isEmpty();
            graphics.drawString(font, clip(font, lines[4], botW), midX, 148,
                    nobody ? EDGE : 0xFF79C0FF, false);

            // Socket indicators (drawn last on right edge)
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
