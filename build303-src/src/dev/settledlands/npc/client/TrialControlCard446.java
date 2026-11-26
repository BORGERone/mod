package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 446: canvas card, NPC-menu row painter, and inspector callback factories for the
 * cross-graph {@link TrialControlNode446} node ({@code trigger:spawner:...}).
 */
public final class TrialControlCard446 {

    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int LABEL = -3947581;
    private static final int MUTED = -7693400;
    private static final int PORT = -855310;
    private static final int ROW_HOVER = -13416358;
    private static final int ROW_IDLE = -15065049;

    private TrialControlCard446() {}

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        int accent = TrialControlNode446.ACCENT_COLOR;
        boolean selected = screen.settledlands$isSelected(node.id);
        int textWidth = width - 16;

        TrialControlNode446.Parsed p = TrialControlNode446.parsedOrDefault(node);
        String opLabel = tr(TrialControlNode446.opKey(p.op));
        String dimLabel = p.dimension == null || p.dimension.isBlank()
                ? tr("gui.settledlands.trial_ctl.dim_current")
                : TeleportCard424.worldDisplayName(p.dimension);
        String cx = p.rawX.isEmpty() ? "0" : p.rawX;
        String cy = p.rawY.isEmpty() ? "0" : p.rawY;
        String cz = p.rawZ.isEmpty() ? "0" : p.rawZ;
        String placeLine = dimLabel + " (" + cx + ", " + cy + ", " + cz + ")";

        String extraLine;
        if (TrialControlNode446.opUsesConfig(p.op)) {
            TrialConfig446 cfg = p.config();
            String modeLbl = TrialConfig446.MODE_OMINOUS.equals(cfg.mode)
                    ? tr("gui.settledlands.trial.mode.ominous")
                    : TrialConfig446.MODE_SPECIAL.equals(cfg.mode)
                    ? tr("gui.settledlands.trial.mode.special")
                    : tr("gui.settledlands.trial.mode.normal");
            extraLine = modeLbl + " \u00b7 " + cfg.totalMobs + "x" + cfg.simultaneousMobs;
        } else if (TrialControlNode446.OP_STOP.equals(p.op)) {
            extraLine = tr(p.killMobs
                    ? "gui.settledlands.trial_ctl.kill_mobs_yes"
                    : "gui.settledlands.trial_ctl.kill_mobs_no");
        } else {
            extraLine = node.next >= 0 ? "-> #" + node.next
                    : tr("gui.settledlands.trial_ctl.card_hint");
        }

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
            graphics.drawString(font, clip(font, tr(TrialControlNode446.TITLE_KEY), textWidth - idW - 6),
                    8, 6, accent, false);
            graphics.drawString(font, idTag, width - 8 - idW, 6, MUTED, false);

            graphics.drawString(font, clip(font,
                    tr("gui.settledlands.trial_ctl.op_prefix") + ": " + opLabel, textWidth),
                    8, 28, -1, false);
            graphics.drawString(font, clip(font, placeLine, textWidth), 8, 46, LABEL, false);
            graphics.drawString(font, clip(font, extraLine, textWidth), 8, 68, MUTED, false);

            int mid = height / 2;
            graphics.fill(-5, mid - 5, 5, mid + 5, PORT);
            graphics.fill(width - 5, mid - 5, width + 5, mid + 5, PORT);
        } finally {
            pose.popPose();
        }
    }

    /** Paints row 18 («+ Триал-спавнер») in the NPC/common context menu when scrolled into view. */
    public static void drawMenuRow(NpcGraphContextMenu menu, GuiGraphics graphics, Font font,
            int mouseX, int mouseY) {
        if (menu == null || graphics == null || font == null || !menu.open || menu.targetNodeId >= 0) {
            return;
        }
        int visible = TrialControlNode446.SPAWNER_ROW - menu.scrollRow;
        if (visible < 0 || visible >= TrialControlNode446.NPC_VISIBLE_ROWS) return;

        int rowX0 = menu.left + 2;
        int rowX1 = menu.left + menu.width - 10;
        int y = menu.top + 20 + visible * 22;
        if (y + 22 > menu.top + menu.height - 2) return;

        int hover = menu.rowAt(mouseX, mouseY);
        boolean hot = hover == TrialControlNode446.SPAWNER_ROW;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 600.0D);
        try {
            graphics.fill(rowX0, y, rowX1, y + 22, hot ? ROW_HOVER : ROW_IDLE);
            String text = clip(font, tr(TrialControlNode446.MENU_LABEL_KEY), rowX1 - rowX0 - 10);
            graphics.drawString(font, text, menu.left + 8, y + 7,
                    hot ? -1 : TrialControlNode446.ACCENT_COLOR, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static List<Component> opDropdownEntries() {
        List<Component> out = new ArrayList<>(TrialControlNode446.OPS.length);
        for (String op : TrialControlNode446.OPS) {
            out.add(Component.translatable(TrialControlNode446.opKey(op)));
        }
        return out;
    }

    public static IntConsumer opPicker(Consumer<Runnable> commit, Runnable rebuildUi, Node node) {
        return picked -> {
            if (picked >= 0 && picked < TrialControlNode446.OPS.length) {
                String op = TrialControlNode446.OPS[picked];
                commit.accept(() -> TrialControlNode446.setOp(node, op));
                if (rebuildUi != null) rebuildUi.run();
            }
        };
    }

    public static List<Component> worldDropdownEntries(String currentDimension) {
        List<Component> out = new ArrayList<>(TeleportNode424.worldCount() + 2);
        out.add(Component.translatable("gui.settledlands.trial_ctl.dim_current"));
        for (int i = 0; i < TeleportNode424.worldCount(); i++) {
            out.add(Component.translatable(TeleportNode424.worldKey(i)));
        }
        if (currentDimension != null && !currentDimension.isBlank()
                && TeleportNode424.worldIndexForId(currentDimension) < 0) {
            out.add(Component.literal(currentDimension));
        }
        return out;
    }

    public static int worldDropdownIndex(String currentDimension) {
        int idx = TrialControlNode446.dimensionIndex(currentDimension);
        if (idx >= 0) return idx;
        return TeleportNode424.worldCount() + 1;
    }

    public static IntConsumer worldPicker(Consumer<Runnable> commit, Node node, String customDim) {
        return picked -> {
            if (picked == 0) {
                commit.accept(() -> TrialControlNode446.setDimension(node, ""));
            } else if (picked > 0 && picked <= TeleportNode424.worldCount()) {
                commit.accept(() -> TrialControlNode446.setDimension(
                        node, TeleportNode424.worldId(picked - 1)));
            } else if (customDim != null && !customDim.isEmpty()) {
                commit.accept(() -> TrialControlNode446.setDimension(node, customDim));
            }
        };
    }

    public static Predicate<String> coordFilter() {
        return TrialControlNode446::isCoordInputValid;
    }

    public static Consumer<String> xSetter(Node node) {
        return val -> TrialControlNode446.setX(node, val);
    }

    public static Consumer<String> ySetter(Node node) {
        return val -> TrialControlNode446.setY(node, val);
    }

    public static Consumer<String> zSetter(Node node) {
        return val -> TrialControlNode446.setZ(node, val);
    }

    public static Runnable setTildeCoordsAction(Consumer<Runnable> commit, Runnable rebuildUi, Node node) {
        return () -> {
            commit.accept(() -> {
                TrialControlNode446.Parsed p = TrialControlNode446.parsedOrDefault(node);
                TrialControlNode446.write(node, p.op, "", "~", "~", "~", p.killMobs, p.configJson);
            });
            if (rebuildUi != null) rebuildUi.run();
        };
    }

    public static Runnable toggleKillMobsAction(Consumer<Runnable> commit, Runnable rebuildUi, Node node) {
        return () -> {
            commit.accept(() -> TrialControlNode446.setKillMobs(
                    node, !TrialControlNode446.parsedOrDefault(node).killMobs));
            if (rebuildUi != null) rebuildUi.run();
        };
    }

    public static Runnable openConfigAction(NpcDialogueGraphScreen screen, Node node) {
        return () -> Minecraft.getInstance().setScreen(
                new TrialControlConfigScreen446(screen, node, true));
    }

    public static Runnable unlinkAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            int nodeId) {
        return () -> {
            screen.settledlands$remember();
            if (graph != null) graph.disconnectOutputs(nodeId);
            screen.settledlands$changed();
        };
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String clip(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "\u2026") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "\u2026";
    }
}
