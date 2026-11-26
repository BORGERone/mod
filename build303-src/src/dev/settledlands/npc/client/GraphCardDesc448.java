package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 448: renders a compact, docked bottom description panel beneath every node/card across all
 * graph editors (NPC dialogue/walk/shared, block, door, chest, and Trial Spawner graphs) explaining
 * what the card does and what it is responsible for, without altering any wire/pin socket geometry.
 */
public final class GraphCardDesc448 {

    public static final int MAX_FOOTER_H = 26;
    private static final int LINE_H = 9;
    private static final int SELECTED_BORDER = -1458585;
    private static final int IDLE_BORDER = -12168601;
    private static final int AI_BORDER = -11818858;
    private static final int FOOTER_BG = 0xFF171B22;
    private static final int FOOTER_DIVIDER = 0xFF2B3544;
    private static final int FOOTER_TEXT = 0xFFA4B3CA;
    private static final int STATS_TYPE = 16;

    private GraphCardDesc448() {}

    /** Localization key for the bottom card description of any graph node. */
    public static String footerKey(Node node) {
        if (node == null) return "gui.settledlands.desc448.card.unknown";

        if (node.type == BlockName359.TYPE) return "gui.settledlands.desc448.card.block_name";
        if (node.type == DoorGraph382.DOOR_TYPE) return "gui.settledlands.desc448.card.door";
        if (node.type == DoorGraph382.SIGNAL_TYPE) return "gui.settledlands.desc448.card.door_signal";
        if (node.type == DoorGraph382.TARGET_TYPE) return "gui.settledlands.desc448.card.door_target";
        if (node.type == ChestGraph396.CHEST_TYPE) return "gui.settledlands.desc448.card.chest";
        if (node.type == TrialGraph446.TRIAL_TYPE) return "gui.settledlands.desc448.card.trial_hub";
        if (node.type == STATS_TYPE) return "gui.settledlands.desc448.card.stats";

        int modSlot = TrialModule447.slotOf(node);
        if (modSlot >= 0) {
            return "gui.settledlands.desc448.card.mod_" + TrialModule447.kindOf(modSlot);
        }
        if (TrialControlNode446.isControlNode(node)) return "gui.settledlands.desc448.card.trial_ctrl";
        if (TeleportNode424.isTeleportNode(node)) return "gui.settledlands.desc448.card.teleport";
        if (LootPool436.isLootPoolNode(node)) return "gui.settledlands.desc448.card.loot_pool";
        if (PursuitGraphNode272.isPursuitNode(node)) return "gui.settledlands.desc448.card.pursuit";

        if (node.type == NpcDialogueGraph.LINE && node.cutsceneLine) {
            return "gui.settledlands.desc448.card.cutscene_line";
        }
        if (node.type >= 0 && node.type <= 15) {
            return "gui.settledlands.desc448.card.node_" + node.type;
        }
        return "gui.settledlands.desc448.card.unknown";
    }

    /** Width of any graph node card (matching {@code NpcStatsGraphUi.width}). */
    public static int cardWidth(Node node) {
        if (node == null) return 220;
        if (TrialGraph446.isTrialNode(node)) return TrialCard446.CARD_W;
        return node.type == STATS_TYPE ? 300 : 220;
    }

    /** Height of any graph node card (matching {@code NpcStatsGraphUi.height}). */
    public static int cardHeight(Node node) {
        if (node == null) return 100;
        if (TrialGraph446.isTrialNode(node)) return TrialCard446.CARD_H;
        return node.type == STATS_TYPE ? 224 : 100;
    }

    /** Computes exact footer height (17 px for 1 line, 26 px for 2 lines) for a node card. */
    public static int nodeFooterHeight(Font font, Node node) {
        if (node == null) return 0;
        int w = cardWidth(node);
        List<String> lines = wrapLines(font, tr(footerKey(node)), Math.max(40, w - 12));
        return 4 + Math.max(1, lines.size()) * LINE_H + 4;
    }

    /** Renders the docked description footer under any node drawn via {@code NpcStatsGraphUi.draw}. */
    public static void drawNodeFooter(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int w = cardWidth(node);
        int h = cardHeight(node);
        List<String> lines = wrapLines(font, tr(footerKey(node)), Math.max(40, w - 12));
        int fh = 4 + lines.size() * LINE_H + 4;
        boolean selected = screen.settledlands$isSelected(node.id);
        int border = selected ? SELECTED_BORDER : IDLE_BORDER;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, h, w + 2, h + fh + 2, border);
            graphics.fill(0, h, w, h + fh, FOOTER_BG);
            graphics.fill(0, h, w, h + 1, FOOTER_DIVIDER);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), 6, h + 4 + i * LINE_H, FOOTER_TEXT, false);
            }
        } finally {
            pose.popPose();
        }
    }

    /** Renders the docked description footer under the world-space Auto-AI card. */
    public static void drawAutoAiFooter(NpcDialogueGraphScreen screen, GuiGraphics graphics) {
        if (screen == null || graphics == null) return;
        int eid = screen.entityId();
        if (BlockTrigger351.isBlockEditorId(eid) || DoorGraph382.isDoorEditorId(eid)
                || ChestGraph396.isChestEditorId(eid) || TrialGraph446.isTrialEditorId(eid)) {
            return;
        }
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (graph == null || canvas == null || font == null) return;

        NpcAutoAi255.GraphState state = NpcAutoAi255.graphState(graph);
        if (state == null || !state.synced) return;
        if (!NpcAutoAiGraphUi255.visible(canvas, state)) return;

        int cardX = NpcAutoAiGraphUi255.cardX(state);
        int cardY = NpcAutoAiGraphUi255.cardY(state);
        int w = NpcAutoAiGraphUi255.CARD_W;
        int h = NpcAutoAiGraphUi255.CARD_H;
        List<String> lines = wrapLines(font, tr("gui.settledlands.desc448.card.auto_ai"), w - 16);
        int fh = 4 + lines.size() * LINE_H + 4;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(cardX), canvas.screenY(cardY), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(0, h, w, h + fh + 1, AI_BORDER);
            graphics.fill(1, h, w - 1, h + fh, FOOTER_BG);
            graphics.fill(1, h, w - 1, h + 1, FOOTER_DIVIDER);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), 8, h + 4 + i * LINE_H, FOOTER_TEXT, false);
            }
        } finally {
            pose.popPose();
        }
    }

    /** Expands node visibility check downward so footers stay rendered near canvas top edge. */
    public static boolean isFooterVisible(NpcGraphCanvas canvas, Node node) {
        if (canvas == null || node == null) return false;
        double sx = canvas.screenX(node.x);
        double sy = canvas.screenY(node.y);
        double sw = cardWidth(node) * canvas.zoom;
        double sh = (cardHeight(node) + MAX_FOOTER_H + 2) * canvas.zoom;
        return sx + sw >= canvas.left && sx <= canvas.right
                && sy + sh >= canvas.top && sy <= canvas.bottom;
    }

    /** Expands Auto-AI card visibility check downward for its footer. */
    public static boolean isAutoAiFooterVisible(NpcGraphCanvas canvas, NpcAutoAi255.GraphState state) {
        if (canvas == null || state == null || !state.synced) return false;
        double sx = canvas.screenX(NpcAutoAiGraphUi255.cardX(state));
        double sy = canvas.screenY(NpcAutoAiGraphUi255.cardY(state));
        double sw = NpcAutoAiGraphUi255.CARD_W * canvas.zoom;
        double sh = (NpcAutoAiGraphUi255.CARD_H + MAX_FOOTER_H + 2) * canvas.zoom;
        return sx + sw >= canvas.left && sx <= canvas.right
                && sy + sh >= canvas.top && sy <= canvas.bottom;
    }

    /** Allows selecting/dragging a node card by clicking its docked bottom description area. */
    public static int hitNodeFooterId(NpcGraphCanvas canvas, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        if (canvas == null || graph == null || !canvas.contains(mouseX, mouseY)) return -1;
        double wx = canvas.worldX(mouseX);
        double wy = canvas.worldY(mouseY);
        Font font = safeFont();
        int best = -1;
        for (Node n : graph.nodes.values()) {
            if (n == null) continue;
            int w = cardWidth(n);
            int h = cardHeight(n);
            int fh = font != null ? nodeFooterHeight(font, n) : MAX_FOOTER_H;
            if (wx >= n.x && wx <= n.x + w && wy >= n.y + h && wy <= n.y + h + fh) {
                best = n.id;
            }
        }
        return best;
    }

    /** Wraps text into at most 2 lines fitting {@code maxW}, appending an ellipsis if needed. */
    public static List<String> wrapLines(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>(2);
        if (text == null || text.isEmpty() || font == null || maxW <= 0) {
            if (text != null && !text.isEmpty()) out.add(text);
            return out;
        }
        String clean = text.trim();
        if (font.width(clean) <= maxW) {
            out.add(clean);
            return out;
        }
        String[] words = clean.split("\\s+");
        StringBuilder line1 = new StringBuilder();
        int idx = 0;
        while (idx < words.length) {
            String cand = line1.length() == 0 ? words[idx] : line1 + " " + words[idx];
            if (font.width(cand) <= maxW || line1.length() == 0) {
                line1.setLength(0);
                line1.append(cand);
                idx++;
                if (font.width(line1.toString()) > maxW) break;
            } else {
                break;
            }
        }
        out.add(clip(font, line1.toString(), maxW));
        if (idx < words.length) {
            StringBuilder rest = new StringBuilder();
            for (int i = idx; i < words.length; i++) {
                if (rest.length() > 0) rest.append(' ');
                rest.append(words[i]);
            }
            out.add(clip(font, rest.toString(), maxW));
        }
        return out;
    }

    private static String clip(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        int ellW = font.width("\u2026");
        String cut = font.plainSubstrByWidth(text, Math.max(0, maxW - ellW));
        while (!cut.isEmpty() && font.width(cut) + ellW > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "\u2026";
    }

    private static Font safeFont() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null ? null : mc.font;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String tr(String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            return Component.translatable(key).getString();
        } catch (Throwable ignored) {
            return key;
        }
    }
}
