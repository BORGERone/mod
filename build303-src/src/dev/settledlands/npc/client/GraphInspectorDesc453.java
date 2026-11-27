package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.VarDataNode453;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 453: renders the node description box inside the bottom-right corner of the inspector
 * sidebar whenever a node is selected (replacing the old canvas card footers).
 */
public final class GraphInspectorDesc453 {

    /** Mixin bridge implemented by {@code GraphInspectorDescMixin453} so {@code widgets()} restores
     *  the reserved footer height AFTER all custom inspectors and range adaptors finish. */
    public interface FooterReserveHost {
        void settledlands$popWidgetsReserve453();
    }

    private static final int LINE_H = 10;
    private static final int HEADER_H = 12;
    private static final int PAD_TOP = 4;
    private static final int PAD_BOTTOM = 5;
    private static final int FOOTER_BG = 0xFF141820;
    private static final int FOOTER_HEADER_BG = 0xFF1B212D;
    private static final int BORDER_COL = -12890522; // matches stock inspector border
    private static final int TITLE_COL = 0xFF9FB4D0;
    private static final int TEXT_COL = 0xFFC8D4E6;

    private GraphInspectorDesc453() {}

    /** Localization key for the inspector description of {@code node}. */
    public static String descriptionKey(Node node) {
        if (node == null) return "gui.settledlands.desc448.card.unknown";
        if (VarDataNode453.isVarDataNode(node)) {
            return "gui.settledlands.desc448.card.var453";
        }
        return GraphCardDesc448.footerKey(node);
    }

    /** Exact height reserved at the bottom of the inspector for {@code node}, or 0 when closed. */
    public static int reserveHeight(Font font, NpcDialogueGraph graph, int selected,
            int inspectorW, int inspectorH) {
        if (font == null || graph == null || selected < 0 || inspectorW < 60 || inspectorH < 80) {
            return 0;
        }
        Node node = graph.get(selected);
        if (node == null) return 0;

        String raw = tr(descriptionKey(node));
        if (raw.isEmpty()) return 0;

        int textW = Math.max(40, inspectorW - 14);
        List<String> lines = wrapLinesFull(font, raw, textW, 6);
        int natural = PAD_TOP + HEADER_H + lines.size() * LINE_H + PAD_BOTTOM;
        int cap = Math.max(40, Math.min(86, inspectorH / 3));
        return Math.min(natural, cap);
    }

    /** Renders the docked description box in the bottom-right corner of the open inspector. */
    public static void renderFooter(GuiGraphics g, Font font, NpcDialogueGraph graph, int selected,
            int inspectorX, int inspectorY, int inspectorW, int fullInspectorH, int reservedH) {
        if (g == null || font == null || graph == null || selected < 0 || reservedH <= 0) return;
        Node node = graph.get(selected);
        if (node == null) return;

        int boxTop = inspectorY + fullInspectorH - reservedH;
        int boxBottom = inspectorY + fullInspectorH;
        int boxLeft = inspectorX;
        int boxRight = inspectorX + inspectorW;

        // Docked box sharing the inspector's border style
        g.fill(boxLeft, boxTop, boxRight, boxBottom, FOOTER_BG);
        g.fill(boxLeft + 1, boxTop, boxRight - 1, boxTop + 1, BORDER_COL);
        g.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, BORDER_COL);
        g.fill(boxLeft, boxTop, boxLeft + 1, boxBottom, BORDER_COL);
        g.fill(boxRight - 1, boxTop, boxRight, boxBottom, BORDER_COL);

        g.fill(boxLeft + 1, boxTop + 1, boxRight - 1, boxTop + 1 + HEADER_H, FOOTER_HEADER_BG);
        g.fill(boxLeft + 1, boxTop + 1 + HEADER_H, boxRight - 1, boxTop + 2 + HEADER_H, 0xFF283244);

        String title = tr("gui.settledlands.desc453.footer_title");
        if (title.isEmpty() || title.equals("gui.settledlands.desc453.footer_title")) {
            title = "Описание";
        }
        g.drawString(font, title, boxLeft + 7, boxTop + 3, TITLE_COL, false);

        int textW = Math.max(40, inspectorW - 14);
        int maxTextRows = Math.max(1, (reservedH - PAD_TOP - HEADER_H - PAD_BOTTOM + 2) / LINE_H);
        List<String> lines = wrapLinesFull(font, tr(descriptionKey(node)), textW, maxTextRows);
        int y = boxTop + HEADER_H + PAD_TOP;
        for (int i = 0; i < lines.size() && y + 8 <= boxBottom - 2; i++) {
            g.drawString(font, lines.get(i), boxLeft + 7, y, TEXT_COL, false);
            y += LINE_H;
        }
    }

    /** Wraps text across up to {@code maxLines} lines fitting {@code maxW}. */
    public static List<String> wrapLinesFull(Font font, String text, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank() || font == null || maxW <= 0 || maxLines <= 0) {
            return out;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder cur = new StringBuilder();
        int idx = 0;
        while (idx < words.length && out.size() < maxLines) {
            String word = words[idx];
            String cand = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(cand) <= maxW) {
                cur.setLength(0);
                cur.append(cand);
                idx++;
            } else if (cur.length() == 0) {
                out.add(clip(font, word, maxW));
                idx++;
            } else {
                if (out.size() == maxLines - 1) {
                    // Last line: append remaining text and clip cleanly
                    StringBuilder rest = new StringBuilder(cur);
                    while (idx < words.length) {
                        rest.append(' ').append(words[idx++]);
                    }
                    out.add(clip(font, rest.toString(), maxW));
                    cur.setLength(0);
                    break;
                }
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0 && out.size() < maxLines) {
            out.add(clip(font, cur.toString(), maxW));
        }
        return out;
    }

    private static String clip(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        String ell = "\u2026";
        int ellW = font.width(ell);
        String cut = font.plainSubstrByWidth(text, Math.max(0, maxW - ellW));
        while (!cut.isEmpty() && font.width(cut) + ellW > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ell;
    }

    private static String tr(String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            return Component.translatable(key).getString();
        } catch (Throwable ignored) {
            return key;
        }
    }
}
