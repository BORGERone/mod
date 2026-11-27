package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.VarDataNode453;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Build 453/455: renders the node description box as the bottom-most scrollable item inside the
 * general inspector list whenever a node is selected (instead of docking over the bottom of the
 * screen).
 */
public final class GraphInspectorDesc453 {

    /**
     * Mixin bridge implemented by {@code GraphInspectorDescMixin453} and invoked at the end of
     * {@code widgets()} (after all stock/custom inspectors and range adaptors finish) to place
     * the description card below the last inspector item and extend {@code sidebarHeight}.
     */
    public interface ScrollDescHost {
        void settledlands$layoutScrollDesc455();
    }

    private static final int LINE_H = 10;
    private static final int HEADER_H = 12;
    private static final int PAD_TOP = 5;
    private static final int PAD_BOTTOM = 4;
    private static final int MAX_LINES = 20;
    private static final int CARD_BG = 0xFF141820;
    private static final int CARD_HEADER_BG = 0xFF1B212D;
    private static final int BORDER_COL = 0xFF283244;
    private static final int TITLE_COL = 0xFF9FB4D0;
    private static final int TEXT_COL = 0xFFC8D4E6;

    private static Field fLabelY;
    private static boolean labelFieldProbed;

    private GraphInspectorDesc453() {}

    /** Localization key for the inspector description of {@code node}. */
    public static String descriptionKey(Node node) {
        if (node == null) return "gui.settledlands.desc448.card.unknown";
        if (VarDataNode453.isVarDataNode(node)) {
            return "gui.settledlands.desc448.card.var453";
        }
        return GraphCardDesc448.footerKey(node);
    }

    /** Full natural height of the scrollable description block for {@code node}, or 0 if empty. */
    public static int contentHeight(Font font, NpcDialogueGraph graph, int selected,
            int inspectorW) {
        if (font == null || graph == null || selected < 0 || inspectorW < 60) {
            return 0;
        }
        Node node = graph.get(selected);
        if (node == null) return 0;

        String raw = tr(descriptionKey(node));
        if (raw.isEmpty()) return 0;

        int boxW = Math.max(40, inspectorW - 16);
        int textW = Math.max(30, boxW - 12);
        List<String> lines = wrapLinesFull(font, raw, textW, MAX_LINES);
        if (lines.isEmpty()) return 0;
        return 1 + HEADER_H + 1 + PAD_TOP + lines.size() * LINE_H + PAD_BOTTOM;
    }

    /**
     * Computes the scroll offset (relative to {@code inspectorY + 24}) right below the lowest
     * inspector item so the description block appends cleanly at the bottom of any stock or custom
     * inspector list (including {@code Pursuit}, which does not update {@code sidebarHeight}).
     */
    public static int computeContentEndOffset(
            int inspectorY,
            int sidebarScroll,
            int sidebarHeight,
            List<?> labels,
            List<AbstractWidget> widgets) {
        int base = Math.max(0, sidebarHeight);
        int itemsMax = 0;

        if (widgets != null) {
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget w = widgets.get(i);
                if (w == null) continue;
                int wy = w.getY();
                if (wy + sidebarScroll < inspectorY) continue;
                int end = (wy + w.getHeight() + sidebarScroll) - (inspectorY + 24);
                if (end > itemsMax) itemsMax = end;
            }
        }

        if (labels != null) {
            for (int i = 0; i < labels.size(); i++) {
                int ly = labelY(labels.get(i));
                if (ly == Integer.MIN_VALUE) continue;
                if (ly + sidebarScroll < inspectorY) continue;
                int end = (ly + 11 + sidebarScroll) - (inspectorY + 24);
                if (end > itemsMax) itemsMax = end;
            }
        }

        return Math.max(base + 4, itemsMax > 0 ? itemsMax + 6 : 0);
    }

    /**
     * Renders the scrollable description card inside the inspector's active scissor clip at
     * {@code boxTop}.
     */
    public static void renderInList(
            GuiGraphics g,
            Font font,
            NpcDialogueGraph graph,
            int selected,
            int inspectorX,
            int inspectorY,
            int inspectorW,
            int inspectorH,
            int boxTop,
            int boxH) {
        if (g == null || font == null || graph == null || selected < 0 || boxH <= 0) return;
        Node node = graph.get(selected);
        if (node == null) return;

        int clipTop = inspectorY + 21;
        int clipBottom = inspectorY + inspectorH - 1;
        if (boxTop + boxH <= clipTop || boxTop >= clipBottom) {
            return;
        }

        int boxLeft = inspectorX + 8;
        int boxW = Math.max(40, inspectorW - 16);
        int boxRight = boxLeft + boxW;
        int boxBottom = boxTop + boxH;

        g.fill(boxLeft, boxTop, boxRight, boxBottom, CARD_BG);
        g.fill(boxLeft + 1, boxTop, boxRight - 1, boxTop + 1, BORDER_COL);
        g.fill(boxLeft, boxBottom - 1, boxRight, boxBottom, BORDER_COL);
        g.fill(boxLeft, boxTop, boxLeft + 1, boxBottom, BORDER_COL);
        g.fill(boxRight - 1, boxTop, boxRight, boxBottom, BORDER_COL);

        g.fill(boxLeft + 1, boxTop + 1, boxRight - 1, boxTop + 1 + HEADER_H, CARD_HEADER_BG);
        g.fill(boxLeft + 1, boxTop + 1 + HEADER_H, boxRight - 1, boxTop + 2 + HEADER_H, BORDER_COL);

        String title = tr("gui.settledlands.desc453.footer_title");
        if (title.isEmpty() || title.equals("gui.settledlands.desc453.footer_title")) {
            title = "Описание";
        }
        g.drawString(font, title, boxLeft + 6, boxTop + 3, TITLE_COL, false);

        int textW = Math.max(30, boxW - 12);
        List<String> lines = wrapLinesFull(font, tr(descriptionKey(node)), textW, MAX_LINES);
        int y = boxTop + 1 + HEADER_H + 1 + PAD_TOP;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), boxLeft + 6, y, TEXT_COL, false);
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

    private static int labelY(Object lbl) {
        if (lbl == null) return Integer.MIN_VALUE;
        if (!labelFieldProbed) {
            labelFieldProbed = true;
            try {
                Field f = lbl.getClass().getDeclaredField("y");
                f.setAccessible(true);
                fLabelY = f;
            } catch (Throwable ignored) {
            }
        }
        if (fLabelY == null) return Integer.MIN_VALUE;
        try {
            return fLabelY.getInt(lbl);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
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
