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
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    private static final int DROPDOWN_MAX_VISIBLE = 7;
    private static final int DROPDOWN_ROW_H = 14;

    private static final Map<NpcUi.NpcDropdown, TrackedDropdown> TRACKED_DROPDOWNS =
            new WeakHashMap<>();
    private static volatile boolean clientListenersRegistered;

    private static final class TrackedDropdown {
        final List<String> options;
        final NpcDialogueGraphScreen screen;
        final Node node;
        final Consumer<Runnable> commit;

        TrackedDropdown(List<String> options, NpcDialogueGraphScreen screen, Node node,
                Consumer<Runnable> commit) {
            this.options = options;
            this.screen = screen;
            this.node = node;
            this.commit = commit;
        }
    }

    private LootPoolCard436() {}

    public static void ensureClientListenersRegistered() {
        if (!clientListenersRegistered) {
            clientListenersRegistered = true;
            LootPool436.setClientListeners(
                    LootPoolEditorScreen440::onPoolsListUpdated,
                    LootPoolEditorScreen440::handleServerData);
        }
    }

    public static void trackPoolDropdown(NpcUi.NpcDropdown dropdown, List<String> options,
            NpcDialogueGraphScreen screen, Node node, Consumer<Runnable> commit) {
        if (dropdown == null || options == null) return;
        TRACKED_DROPDOWNS.put(dropdown, new TrackedDropdown(options, screen, node, commit));
    }

    public static AbstractWidget gearButton(int x, int y, NpcDialogueGraphScreen screen, Node node) {
        return new LootPoolGearButton440(
                x, y, 20, 20,
                Component.translatable("gui.settledlands.loot_pool.edit_btn_tip"),
                () -> openPoolEditor(screen, node, LootPool436.poolFileOf(node)));
    }

    public static void openPoolEditor(NpcDialogueGraphScreen screen, Node node, String poolFile) {
        ensureClientListenersRegistered();
        Minecraft.getInstance().setScreen(new LootPoolEditorScreen440(screen, node, poolFile));
    }

    public static void renderDropdownRowGears(NpcUi.NpcDropdown dropdown, GuiGraphics graphics,
            int mouseX, int mouseY, List<Component> entries, int scroll, int selectedIndex) {
        TrackedDropdown tracked = TRACKED_DROPDOWNS.get(dropdown);
        if (tracked == null || graphics == null || entries == null || entries.isEmpty()) return;

        int visible = Math.min(DROPDOWN_MAX_VISIBLE, entries.size());
        int px = dropdown.getX();
        int py = dropdown.getY() + dropdown.getHeight();
        int pw = Math.max(dropdown.getWidth(), 90);
        boolean hasScroll = entries.size() > visible;
        int gearX = px + pw - (hasScroll ? 18 : 14);
        int bgPadX = gearX - 2;
        int bgRight = px + pw - (hasScroll ? 4 : 1);

        String hoveredTip = null;
        for (int r = 0; r < visible; r++) {
            int idx = scroll + r;
            if (idx < 0 || idx >= entries.size()) continue;
            int ry = py + r * DROPDOWN_ROW_H;
            int gy = ry + 1;

            boolean rowOver = mouseX >= px && mouseX <= px + pw
                    && mouseY >= ry && mouseY < ry + DROPDOWN_ROW_H;
            boolean gearOver = mouseX >= gearX - 1 && mouseX < gearX + 13
                    && mouseY >= ry && mouseY < ry + DROPDOWN_ROW_H;

            // Cover trailing text cleanly before drawing the inline gear frame.
            graphics.fill(bgPadX, ry + 1, bgRight, ry + DROPDOWN_ROW_H - 1,
                    rowOver ? 0xF05C5C5C : 0xF0181818);
            LootPoolGearButton440.drawInlineGearFrame(graphics, gearX, gy, gearOver);

            if (gearOver) {
                String opt = idx < tracked.options.size() ? tracked.options.get(idx) : "";
                hoveredTip = (opt == null || opt.isEmpty())
                        ? tr("gui.settledlands.loot_pool.create_row_tip")
                        : tr("gui.settledlands.loot_pool.edit_row_tip", opt);
            }
        }

        if (hoveredTip != null) {
            Font font = Minecraft.getInstance().font;
            if (font != null) {
                int tw = font.width(hoveredTip) + 8;
                int tx = Math.max(4, Math.min(mouseX + 8, px + pw - tw + 40));
                int ty = mouseY - 16;
                if (ty < 4) ty = mouseY + 14;
                graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + 13, 0xFF000000 | LootPool436.ACCENT_COLOR);
                graphics.fill(tx, ty, tx + tw, ty + 12, 0xEE141821);
                graphics.drawString(font, hoveredTip, tx + 4, ty + 2, 0xFFF2F5FA, false);
            }
        }
    }

    public static boolean handleDropdownRowGearClick(NpcUi.NpcDropdown dropdown,
            double mouseX, double mouseY, int scroll, int entryCount) {
        TrackedDropdown tracked = TRACKED_DROPDOWNS.get(dropdown);
        if (tracked == null || entryCount <= 0) return false;

        int visible = Math.min(DROPDOWN_MAX_VISIBLE, entryCount);
        int px = dropdown.getX();
        int py = dropdown.getY() + dropdown.getHeight();
        int pw = Math.max(dropdown.getWidth(), 90);
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY >= py + visible * DROPDOWN_ROW_H) {
            return false;
        }
        boolean hasScroll = entryCount > visible;
        int gearX = px + pw - (hasScroll ? 18 : 14);
        if (mouseX < gearX - 2 || mouseX > gearX + 14) {
            return false;
        }

        int row = (int) ((mouseY - py) / DROPDOWN_ROW_H);
        int idx = scroll + row;
        if (idx < 0 || idx >= tracked.options.size()) return false;

        String chosen = tracked.options.get(idx);
        if (chosen != null && !chosen.isEmpty() && tracked.commit != null && tracked.node != null) {
            tracked.commit.accept(() -> LootPool436.setPoolFile(tracked.node, chosen));
        }
        openPoolEditor(tracked.screen, tracked.node, chosen == null ? "" : chosen);
        return true;
    }

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
        String rawW = LootPool436.rawWeight(node);
        String wgt = LootPool436.summaryRange(rawW, any);
        String rawR = LootPool436.rawRolls(node);
        String rls = rawR.isEmpty()
                ? (rawW.isEmpty() ? LootPool436.DEFAULT_ROLLS : tr("gui.settledlands.loot_pool.editor.by_weight"))
                : rawR;

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
