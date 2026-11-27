package dev.settledlands.npc.client;

import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialMobPool449;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Build 449: full modpack Mob Catalog with live 3D entity previews, category filtering,
 * search by display name / mod / registry ID, and Shift-click multi-add without closing.
 */
public final class TrialMobCatalogScreen449 extends Screen {

    private static final int CELL_H = 74;
    private static final int CELL_GAP = 6;

    private final Screen parentScreen;
    private final int replaceRowIndex;
    private final Set<String> existingIds;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private Button filterBtn;
    private Button addByIdBtn;

    private String query = "";
    private int categoryFilter = TrialMobPreview449.CAT_ALL;
    private int scrollRow;
    private boolean draggingScroll;

    private List<TrialMobPreview449.MobEntry> allMobs = new ArrayList<>();
    private final List<TrialMobPreview449.MobEntry> shown = new ArrayList<>();

    private String statusText = "";
    private int statusColor = 0xFF7BE088;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int cols;
    private int cellW;
    private int visibleRows;
    private int scrollTrackX;

    public TrialMobCatalogScreen449(Screen parentScreen, int replaceRowIndex,
            Set<String> existingIds, Consumer<String> onSelect) {
        super(Component.translatable("gui.settledlands.trial.mob_cat.title"));
        this.parentScreen = parentScreen;
        this.replaceRowIndex = replaceRowIndex;
        this.existingIds = existingIds == null ? new HashSet<>() : new HashSet<>(existingIds);
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        panelW = Math.max(Math.min(width - 16, 680), Math.min(width - 8, 360));
        panelH = Math.max(height - 16, Math.min(height - 8, 220));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        if (allMobs.isEmpty()) {
            allMobs = TrialMobPreview449.collectAllMobs();
        }

        int topY = panelY + 26;
        int rightEdge = panelX + panelW - 10;

        Component backLbl = Component.translatable("gui.settledlands.loot_pool.editor.back");
        int backW = Math.max(54, font.width(backLbl.getString()) + 14);
        addRenderableWidget(Button.builder(backLbl, b -> onClose())
                .bounds(rightEdge - backW, panelY + 6, backW, 16)
                .build());

        int modeW = Math.max(106, Math.max(
                font.width(filterLabelText(TrialMobPreview449.CAT_ALL)),
                Math.max(
                        font.width(filterLabelText(TrialMobPreview449.CAT_HOSTILE)),
                        font.width(filterLabelText(TrialMobPreview449.CAT_FRIENDLY))))
                + 14);
        Component addIdLbl = Component.translatable("gui.settledlands.loot_pool.picker.add_id");
        int idBtnW = Math.max(60, font.width(addIdLbl.getString()) + 14);
        int gap = 4;
        int searchW = Math.max(90, panelW - 20 - modeW - idBtnW - gap * 2);

        searchBox = new EditBox(font, panelX + 10, topY, searchW, 18,
                Component.translatable("gui.settledlands.trial.cfg.search"));
        searchBox.setMaxLength(120);
        searchBox.setHint(Component.translatable("gui.settledlands.trial.mob_cat.search_hint"));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text == null ? "" : text;
            scrollRow = 0;
            refreshShown();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        filterBtn = Button.builder(Component.literal(filterLabelText(categoryFilter)), b -> {
                    categoryFilter = (categoryFilter + 1) % 3;
                    scrollRow = 0;
                    b.setMessage(Component.literal(filterLabelText(categoryFilter)));
                    refreshShown();
                })
                .bounds(panelX + 10 + searchW + gap, topY, modeW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_cat.filter_tip")))
                .build();
        addRenderableWidget(filterBtn);

        addByIdBtn = Button.builder(addIdLbl, b -> addTypedId())
                .bounds(panelX + 10 + searchW + gap + modeW + gap, topY, idBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_cat.add_id_tip")))
                .build();
        addRenderableWidget(addByIdBtn);

        gridX = panelX + 10;
        gridY = panelY + 52;
        int footerH = 26;
        gridW = panelW - 28;
        scrollTrackX = gridX + gridW + 6;
        gridH = Math.max(CELL_H + 8, panelY + panelH - footerH - gridY);

        cols = Math.max(2, Math.min(5, (gridW + CELL_GAP) / 114));
        cellW = Math.max(78, (gridW - CELL_GAP * (cols - 1)) / cols);
        visibleRows = Math.max(1, (gridH + CELL_GAP) / (CELL_H + CELL_GAP));

        refreshShown();
    }

    private String filterLabelText(int mode) {
        return switch (mode) {
            case TrialMobPreview449.CAT_HOSTILE ->
                    Component.translatable("gui.settledlands.trial.mob_cat.filter_hostile").getString();
            case TrialMobPreview449.CAT_FRIENDLY ->
                    Component.translatable("gui.settledlands.trial.mob_cat.filter_friendly").getString();
            default -> Component.translatable("gui.settledlands.trial.mob_cat.filter_all").getString();
        };
    }

    private void refreshShown() {
        shown.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < allMobs.size(); i++) {
            TrialMobPreview449.MobEntry e = allMobs.get(i);
            if (TrialMobPreview449.categoryMatches(e, categoryFilter) == 0) continue;
            if (!q.isEmpty()) {
                String dl = e.displayName.toLowerCase(Locale.ROOT);
                String il = e.id.toLowerCase(Locale.ROOT);
                String ml = e.modId.toLowerCase(Locale.ROOT);
                if (!dl.contains(q) && !il.contains(q) && !ml.contains(q)) continue;
            }
            shown.add(e);
        }
        int maxScr = maxScrollRow();
        if (scrollRow > maxScr) scrollRow = maxScr;
        if (scrollRow < 0) scrollRow = 0;
    }

    private int maxScrollRow() {
        int totalRows = (shown.size() + Math.max(1, cols) - 1) / Math.max(1, cols);
        return Math.max(0, totalRows - Math.max(1, visibleRows));
    }

    private void addTypedId() {
        String raw = query == null ? "" : query.trim();
        String norm = TrialConfig446.normalizeId(raw);
        if (norm == null || !norm.contains(":")) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.trial.mob_cat.bad_id").getString();
            return;
        }
        applySelection(norm, hasShiftDown() && replaceRowIndex < 0);
    }

    private void applySelection(String entityId, boolean keepOpen) {
        if (entityId == null || entityId.isEmpty()) return;
        playClick();
        if (onSelect != null) {
            onSelect.accept(entityId);
        }
        existingIds.add(entityId);
        if (keepOpen) {
            statusColor = 0xFF7BE088;
            statusText = Component.translatable("gui.settledlands.trial.mob_cat.added",
                    TrialMobPreview449.displayName(entityId)).getString();
            return;
        }
        onClose();
    }

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TrialMobPreview449.beginFrame();
        graphics.fill(0, 0, width, height, 0x990A0D13);

        int accent = 0xFF000000 | TrialMobPool449.ACCENT_COLOR;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF080A0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        String titleKey = replaceRowIndex >= 0
                ? "gui.settledlands.trial.mob_cat.title_replace"
                : "gui.settledlands.trial.mob_cat.title";
        String title = Component.translatable(titleKey, Integer.valueOf(shown.size())).getString();
        graphics.drawString(font, title, panelX + 10, panelY + 9, 0xFFF2F5FA, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Background grid container
        int containerH = Math.min(gridH, visibleRows * (CELL_H + CELL_GAP) - CELL_GAP + 6);
        graphics.fill(gridX - 2, gridY - 2, gridX + gridW + 2, gridY + containerH, 0x990D1118);
        graphics.renderOutline(gridX - 2, gridY - 2, gridW + 4, containerH + 2, 0xFF272F40);

        TrialMobPreview449.MobEntry hoveredEntry = null;
        int startIdx = scrollRow * cols;
        int previewH = 44;

        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = startIdx + r * cols + c;
                if (idx >= shown.size()) break;
                TrialMobPreview449.MobEntry entry = shown.get(idx);

                int cx = gridX + c * (cellW + CELL_GAP);
                int cy = gridY + r * (CELL_H + CELL_GAP);
                if (cy + CELL_H > gridY + gridH + 2) break;

                boolean over = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + CELL_H;
                boolean inPool = existingIds.contains(entry.id);
                if (over) hoveredEntry = entry;

                int cardBg = over ? 0xFF242E3F : (inPool ? 0xFF17261E : 0xFF181D28);
                int border = over ? accent : (inPool ? 0xFF4CA86B : 0xFF2B3445);
                graphics.fill(cx, cy, cx + cellW, cy + CELL_H, cardBg);
                graphics.renderOutline(cx, cy, cellW, CELL_H, border);

                // Podium behind the 3D mob preview
                int px1 = cx + 2;
                int py1 = cy + 2;
                int px2 = cx + cellW - 2;
                int py2 = cy + 2 + previewH;
                graphics.fill(px1, py1, px2, py2, over ? 0xFF121822 : 0xFF0E121A);
                graphics.fill(px1, py2 - 1, px2, py2, 0xFF252E3E);

                float lookX = over ? (float) mouseX : (float) ((px1 + px2) / 2 + 16);
                float lookY = over ? (float) mouseY : (float) ((py1 + py2) / 2 - 4);
                TrialMobPreview449.renderPreviewBox(
                        graphics, font, entry.id, px1 + 2, py1 + 1, px2 - 2, py2 - 2, lookX, lookY);

                // Category badge at top-right of podium
                String catTxt = Component.translatable(
                        TrialMobPreview449.categoryLabelKey(entry.categoryName)).getString();
                int catCol = TrialMobPreview449.categoryBadgeColor(entry.categoryName);
                int catW = font.width(catTxt) + 4;
                graphics.fill(px2 - catW - 1, py1 + 1, px2 - 1, py1 + 10, 0xBB0A0D13);
                graphics.drawString(font, catTxt, px2 - catW + 1, py1 + 2, catCol, false);

                if (inPool) {
                    graphics.fill(px1 + 1, py1 + 1, px1 + 11, py1 + 11, 0xCC1E3B28);
                    graphics.drawString(font, "\u2713", px1 + 3, py1 + 2, 0xFF7BE088, false);
                }

                // Name + namespace:id lines
                int nameY = cy + previewH + 5;
                graphics.drawString(font, TrialMobPreview449.clip(font, entry.displayName, cellW - 8),
                        cx + 4, nameY, 0xFFF2F5FA, false);
                graphics.drawString(font, TrialMobPreview449.clip(font, entry.id, cellW - 8),
                        cx + 4, nameY + 11, 0xFF8391A6, false);
            }
        }

        if (shown.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.settledlands.trial.mob_cat.empty"),
                    gridX + gridW / 2, gridY + Math.max(20, gridH / 2 - 4), 0xFF7F8A9E);
        }

        // Scrollbar
        int maxScr = maxScrollRow();
        if (maxScr > 0) {
            int trackH = Math.max(24, containerH - 2);
            graphics.fill(scrollTrackX, gridY, scrollTrackX + 5, gridY + trackH, 0xFF10141C);
            int totalRows = (shown.size() + cols - 1) / cols;
            int thumbH = Math.max(16, (int) ((double) visibleRows / totalRows * trackH));
            int thumbY = gridY + (int) ((double) scrollRow / maxScr * (trackH - thumbH));
            graphics.fill(scrollTrackX, thumbY, scrollTrackX + 5, thumbY + thumbH, accent);
        }

        // Bottom hint & status
        int hintY = panelY + panelH - 18;
        if (!statusText.isEmpty()) {
            graphics.drawString(font, TrialMobPreview449.clip(font, statusText, panelW - 24),
                    panelX + 10, hintY, statusColor, false);
        } else {
            graphics.drawString(font,
                    TrialMobPreview449.clip(font,
                            Component.translatable(replaceRowIndex >= 0
                                    ? "gui.settledlands.trial.mob_cat.hint_replace"
                                    : "gui.settledlands.trial.mob_cat.hint_multi").getString(),
                            panelW - 24),
                    panelX + 10, hintY, 0xFF8C97AB, false);
        }

        if (hoveredEntry != null) {
            renderHoveredMobTip(graphics, hoveredEntry, mouseX, mouseY);
        }
    }

    private void renderHoveredMobTip(GuiGraphics graphics, TrialMobPreview449.MobEntry e,
            int mouseX, int mouseY) {
        String l1 = e.displayName;
        String l2 = e.id + "  [" + e.modId + "]";
        String l3 = Component.translatable(
                TrialMobPreview449.categoryLabelKey(e.categoryName)).getString();
        String l4 = Component.translatable("gui.settledlands.trial.mob_cat.tip_action").getString();
        int tw = Math.max(Math.max(font.width(l1), font.width(l2)),
                Math.max(font.width(l3), font.width(l4))) + 12;
        int th = 46;
        int tx = Math.min(mouseX + 10, width - tw - 6);
        int ty = mouseY - th - 6;
        if (ty < 4) ty = Math.min(height - th - 6, mouseY + 14);
        int accent = 0xFF000000 | TrialMobPool449.ACCENT_COLOR;
        graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF080A0F);
        graphics.fill(tx, ty, tx + tw, ty + th, 0xF2121620);
        graphics.renderOutline(tx, ty, tw, th, accent);
        graphics.drawString(font, l1, tx + 6, ty + 5, 0xFFF2F5FA, false);
        graphics.drawString(font, l2, tx + 6, ty + 15, 0xFF9AA5B8, false);
        graphics.drawString(font, l3, tx + 6, ty + 25,
                TrialMobPreview449.categoryBadgeColor(e.categoryName), false);
        graphics.drawString(font, l4, tx + 6, ty + 35, 0xFF7BE088, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int maxScr = maxScrollRow();
            int containerH = Math.min(gridH, visibleRows * (CELL_H + CELL_GAP) - CELL_GAP + 6);
            if (maxScr > 0 && mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + 8
                    && mouseY >= gridY && mouseY <= gridY + containerH) {
                draggingScroll = true;
                updateScrollFromMouse(mouseY, containerH);
                return true;
            }

            int startIdx = scrollRow * cols;
            for (int r = 0; r < visibleRows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = startIdx + r * cols + c;
                    if (idx >= shown.size()) break;
                    int cx = gridX + c * (cellW + CELL_GAP);
                    int cy = gridY + r * (CELL_H + CELL_GAP);
                    if (cy + CELL_H > gridY + gridH + 2) break;
                    if (mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + CELL_H) {
                        boolean keepOpen = replaceRowIndex < 0 && hasShiftDown();
                        applySelection(shown.get(idx).id, keepOpen);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll && button == 0) {
            int containerH = Math.min(gridH, visibleRows * (CELL_H + CELL_GAP) - CELL_GAP + 6);
            updateScrollFromMouse(mouseY, containerH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        if (step != 0) {
            int maxScr = maxScrollRow();
            scrollRow = Math.max(0, Math.min(maxScr, scrollRow + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateScrollFromMouse(double mouseY, int trackH) {
        int maxScr = maxScrollRow();
        if (maxScr <= 0 || trackH <= 0) return;
        double frac = Math.max(0.0D, Math.min(1.0D, (mouseY - gridY) / (double) trackH));
        scrollRow = (int) Math.round(frac * maxScr);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
