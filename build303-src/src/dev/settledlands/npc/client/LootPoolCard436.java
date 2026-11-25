package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 436: canvas card rendering and inspector callback factories for «Пул предметов».
 * Keeping lambdas here ensures zero synthetic lambda methods leak onto {@code NpcDialogueGraphScreen}.
 */
public final class LootPoolCard436 {

    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int LABEL = -3947581;
    private static final int MUTED = -7693400;
    private static final int PORT = -855310;

    private LootPoolCard436() {}

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        int width = NpcGraphCanvas.NODE_W;
        int height = NpcGraphCanvas.NODE_H;
        int accent = LootPool436.ACCENT_COLOR;
        boolean selected = screen.settledlands$isSelected(node.id);
        int textWidth = width - 16;

        String poolFile = LootPool436.poolFileOf(node);
        String poolLabel = poolFile.isEmpty()
                ? tr("gui.settledlands.loot_pool.none")
                : poolFile;
        String any = tr("gui.settledlands.loot_pool.any");
        String rar = LootPool436.summaryRange(LootPool436.rawRarity(node), any);
        String wgt = LootPool436.summaryRange(LootPool436.rawWeight(node), any);
        String rls = LootPool436.summaryRange(LootPool436.rawRolls(node), LootPool436.DEFAULT_ROLLS);

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
            graphics.drawString(font, clip(font, tr(LootPool436.TITLE_KEY), textWidth - idW - 6),
                    8, 6, accent, false);
            graphics.drawString(font, idTag, width - 8 - idW, 6, MUTED, false);

            graphics.drawString(font, clip(font, poolLabel, textWidth), 8, 28, -1, false);
            graphics.drawString(font,
                    clip(font, tr("gui.settledlands.loot_pool.card_limits", rar, wgt), textWidth),
                    8, 45, LABEL, false);
            graphics.drawString(font,
                    clip(font, tr("gui.settledlands.loot_pool.card_rolls", rls), textWidth),
                    8, 60, LABEL, false);

            String hint = node.next >= 0
                    ? "-> #" + node.next
                    : tr("gui.settledlands.loot_pool.card_hint");
            graphics.drawString(font, clip(font, hint, textWidth), 8, 76, MUTED, false);

            int mid = height / 2;
            graphics.fill(-5, mid - 5, 5, mid + 5, PORT);
            graphics.fill(width - 5, mid - 5, width + 5, mid + 5, PORT);
        } finally {
            pose.popPose();
        }
    }

    /** Returns sorted available pool filenames (server + local), without the leading "(none)" entry. */
    public static List<String> availablePoolFiles(String currentFile) {
        Set<String> merged = new LinkedHashSet<>();
        for (String s : LootPool436.getServerPools()) {
            String clean = LootPool436.sanitizeFileName(s);
            if (!clean.isEmpty()) merged.add(clean);
        }
        for (String s : LootPoolManager436.listPools()) {
            String clean = LootPool436.sanitizeFileName(s);
            if (!clean.isEmpty()) merged.add(clean);
        }
        List<String> sorted = new ArrayList<>(merged);
        Collections.sort(sorted);
        String cur = LootPool436.sanitizeFileName(currentFile);
        if (!cur.isEmpty() && !sorted.contains(cur)) {
            sorted.add(cur);
        }
        return sorted;
    }

    public static String firstAvailablePool() {
        List<String> files = availablePoolFiles("");
        return files.isEmpty() ? "" : files.get(0);
    }

    /** Dropdown option strings: index 0 is "", indices 1..N are filenames. */
    public static List<String> poolOptions(String currentFile) {
        List<String> files = availablePoolFiles(currentFile);
        List<String> out = new ArrayList<>(files.size() + 1);
        out.add("");
        out.addAll(files);
        return out;
    }

    public static List<Component> poolDropdownEntries(List<String> options) {
        List<Component> out = new ArrayList<>(options.size());
        for (String opt : options) {
            if (opt == null || opt.isEmpty()) {
                out.add(Component.translatable("gui.settledlands.loot_pool.none"));
            } else {
                out.add(Component.literal(opt));
            }
        }
        return out;
    }

    public static int poolDropdownIndex(List<String> options, String currentFile) {
        String cur = LootPool436.sanitizeFileName(currentFile);
        if (cur.isEmpty()) return 0;
        for (int i = 1; i < options.size(); i++) {
            if (cur.equals(options.get(i))) return i;
        }
        return 0;
    }

    public static IntConsumer poolPicker(Consumer<Runnable> commit, Node node, List<String> options) {
        return picked -> {
            if (picked >= 0 && picked < options.size()) {
                String chosen = options.get(picked);
                commit.accept(() -> LootPool436.setPoolFile(node, chosen));
            }
        };
    }

    public static Consumer<String> raritySetter(Node node) {
        return val -> LootPool436.setRarity(node, val);
    }

    public static Consumer<String> weightSetter(Node node) {
        return val -> LootPool436.setWeight(node, val);
    }

    public static Consumer<String> rollsSetter(Node node) {
        return val -> LootPool436.setRolls(node, val);
    }

    public static Runnable unlinkAction(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            int nodeId) {
        return () -> {
            screen.settledlands$remember();
            if (graph != null) graph.disconnectOutputs(nodeId);
            screen.settledlands$changed();
        };
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
