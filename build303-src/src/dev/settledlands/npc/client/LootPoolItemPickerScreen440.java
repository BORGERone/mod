package dev.settledlands.npc.client;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Build 440: searchable item catalog + player-inventory picker for the in-game loot pool editor.
 * Supports single click selection or Shift-click multi-add without closing the catalog.
 */
public final class LootPoolItemPickerScreen440 extends Screen {

    private static final int CELL = 22;

    private final LootPoolEditorScreen440 parent;
    private final int replaceRowIndex;
    private final Set<String> existingIds;

    private EditBox searchBox;
    private Button modeBtn;
    private Button addByIdBtn;
    private Button addAllInvBtn;

    private String query = "";
    private boolean inventoryOnly;
    private int scrollRow;
    private boolean draggingScroll;

    private List<ItemStack> allItems = new ArrayList<>();
    private List<ItemStack> invItems = new ArrayList<>();
    private List<ItemStack> shown = new ArrayList<>();

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
    private int rows;

    public LootPoolItemPickerScreen440(LootPoolEditorScreen440 parent, int replaceRowIndex,
            Set<String> existingIds) {
        super(Component.translatable("gui.settledlands.loot_pool.picker.title"));
        this.parent = parent;
        this.replaceRowIndex = replaceRowIndex;
        this.existingIds = existingIds;
    }

    @Override
    protected void init() {
        panelW = Math.max(Math.min(width - 16, 560), Math.min(width - 8, 340));
        panelH = Math.max(Math.min(height - 16, 360), Math.min(height - 8, 210));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        if (allItems.isEmpty()) {
            allItems = NpcItems.allStacks();
        }
        rebuildInventoryList();

        int topY = panelY + 26;
        int rightEdge = panelX + panelW - 10;

        Component backLbl = Component.translatable("gui.settledlands.loot_pool.editor.back");
        int backW = Math.max(54, font.width(backLbl.getString()) + 14);
        Button backBtn = Button.builder(backLbl, b -> onClose())
                .bounds(rightEdge - backW, panelY + 6, backW, 16)
                .build();
        addRenderableWidget(backBtn);

        int modeW = Math.max(82, Math.max(
                font.width(Component.translatable("gui.settledlands.loot_pool.picker.mode_all").getString()),
                font.width(Component.translatable("gui.settledlands.loot_pool.picker.mode_inv").getString())) + 14);
        Component addIdLbl = Component.translatable("gui.settledlands.loot_pool.picker.add_id");
        int idBtnW = Math.max(60, font.width(addIdLbl.getString()) + 14);
        int gap = 4;
        int searchW = Math.max(80, panelW - 20 - modeW - idBtnW - gap * 2);

        searchBox = new EditBox(font, panelX + 10, topY, searchW, 18,
                Component.translatable("gui.settledlands.loot_pool.picker.search"));
        searchBox.setMaxLength(120);
        searchBox.setHint(Component.translatable("gui.settledlands.loot_pool.picker.search_hint"));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text == null ? "" : text;
            scrollRow = 0;
            refreshShown();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        modeBtn = Button.builder(modeLabel(), b -> {
                    inventoryOnly = !inventoryOnly;
                    scrollRow = 0;
                    if (inventoryOnly) rebuildInventoryList();
                    b.setMessage(modeLabel());
                    if (addAllInvBtn != null) {
                        addAllInvBtn.visible = inventoryOnly && replaceRowIndex < 0;
                        addAllInvBtn.active = !invItems.isEmpty();
                    }
                    refreshShown();
                })
                .bounds(panelX + 10 + searchW + gap, topY, modeW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.picker.mode_tip")))
                .build();
        addRenderableWidget(modeBtn);

        addByIdBtn = Button.builder(addIdLbl, b -> addTypedId())
                .bounds(panelX + 10 + searchW + gap + modeW + gap, topY, idBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.picker.add_id_tip")))
                .build();
        addRenderableWidget(addByIdBtn);

        gridX = panelX + 10;
        gridY = panelY + 50;
        int footerH = 38;
        gridW = panelW - 28;
        gridH = Math.max(CELL * 2, panelY + panelH - footerH - gridY);
        cols = Math.max(4, gridW / CELL);
        rows = Math.max(2, gridH / CELL);

        if (replaceRowIndex < 0) {
            Component addAllLbl = Component.translatable("gui.settledlands.loot_pool.picker.add_inv_all");
            int addAllW = Math.max(110, font.width(addAllLbl.getString()) + 14);
            addAllInvBtn = Button.builder(addAllLbl, b -> addAllFromInventory())
                    .bounds(rightEdge - addAllW, panelY + panelH - 22, addAllW, 16)
                    .build();
            addAllInvBtn.visible = inventoryOnly;
            addAllInvBtn.active = !invItems.isEmpty();
            addRenderableWidget(addAllInvBtn);
        } else {
            addAllInvBtn = null;
        }

        refreshShown();
    }

    private Component modeLabel() {
        return Component.translatable(inventoryOnly
                ? "gui.settledlands.loot_pool.picker.mode_inv"
                : "gui.settledlands.loot_pool.picker.mode_all");
    }

    private void rebuildInventoryList() {
        invItems = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc == null ? null : mc.player;
        if (player == null) return;
        Inventory inv = player.getInventory();
        if (inv == null) return;
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack st = inv.getItem(i);
            if (st == null || st.isEmpty()) continue;
            String id = NpcItems.idOf(st);
            if (id == null || id.isEmpty()) continue;
            if (!merged.containsKey(id)) {
                merged.put(id, st.copy());
            }
        }
        invItems.addAll(merged.values());
    }

    private void refreshShown() {
        List<ItemStack> source = inventoryOnly ? invItems : allItems;
        shown = NpcItems.filter(source, query);
        int max = maxScrollRow();
        if (scrollRow > max) scrollRow = max;
        if (scrollRow < 0) scrollRow = 0;
        if (addByIdBtn != null) {
            String t = query == null ? "" : query.trim();
            addByIdBtn.active = !t.isEmpty() && !t.contains(" ");
        }
    }

    private int totalRows() {
        if (shown.isEmpty()) return 0;
        return (shown.size() + cols - 1) / cols;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - rows);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Omit vanilla Screen#renderBlurredBackground so item picker stays crisp without blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x990A0D13);

        int accent = 0xFF000000 | LootPool436.ACCENT_COLOR;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF090B10);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        LootPoolGearButton440.drawGearIcon(graphics, panelX + 8, panelY + 7, true, 0xEE141821);
        Component heading = Component.translatable(replaceRowIndex >= 0
                ? "gui.settledlands.loot_pool.picker.replace_title"
                : "gui.settledlands.loot_pool.picker.title");
        graphics.drawString(font, heading, panelX + 24, panelY + 9, 0xFFF2F5FA, false);

        graphics.fill(gridX - 2, gridY - 2, gridX + cols * CELL + 2, gridY + rows * CELL + 2, 0xAA0C1017);
        graphics.renderOutline(gridX - 2, gridY - 2, cols * CELL + 4, rows * CELL + 4, 0xFF262E3D);

        ItemStack hoveredStack = ItemStack.EMPTY;
        int start = scrollRow * cols;
        int visibleLimit = rows * cols;
        if (shown.isEmpty()) {
            Component emptyMsg = Component.translatable(inventoryOnly
                    ? "gui.settledlands.loot_pool.picker.empty_inv"
                    : "gui.settledlands.loot_pool.picker.empty");
            graphics.drawCenteredString(font, emptyMsg, gridX + (cols * CELL) / 2,
                    gridY + (rows * CELL) / 2 - 4, 0xFF8A95A8);
        } else {
            for (int slot = 0; slot < visibleLimit; slot++) {
                int idx = start + slot;
                if (idx >= shown.size()) break;
                ItemStack stack = shown.get(idx);
                int c = slot % cols;
                int r = slot / cols;
                int cx = gridX + c * CELL;
                int cy = gridY + r * CELL;

                boolean over = mouseX >= cx && mouseX < cx + 20 && mouseY >= cy && mouseY < cy + 20;
                String id = NpcItems.idOf(stack);
                boolean inPool = existingIds != null && existingIds.contains(id);
                int cellBg = over ? 0xFF2D374B : (inPool ? 0xFF222018 : 0xFF191E28);
                int cellFrame = over ? accent : (inPool ? 0xAA9E7828 : 0xFF2B3242);

                graphics.fill(cx, cy, cx + 20, cy + 20, cellBg);
                graphics.renderOutline(cx, cy, 20, 20, cellFrame);
                graphics.renderItem(stack, cx + 2, cy + 2);
                if (inventoryOnly && stack.getCount() > 1) {
                    String cnt = Integer.toString(stack.getCount());
                    int cw = font.width(cnt);
                    graphics.drawString(font, cnt, cx + 19 - cw, cy + 11, 0xFFFFFFFF, true);
                }
                if (over) {
                    hoveredStack = stack;
                }
            }
        }

        int maxScr = maxScrollRow();
        if (maxScr > 0) {
            int trackX = gridX + cols * CELL + 5;
            int trackY = gridY;
            int trackH = rows * CELL;
            graphics.fill(trackX, trackY, trackX + 5, trackY + trackH, 0xFF10141C);
            int thumbH = Math.max(18, trackH * rows / Math.max(1, totalRows()));
            int thumbY = trackY + (trackH - thumbH) * scrollRow / Math.max(1, maxScr);
            graphics.fill(trackX, thumbY, trackX + 5, thumbY + thumbH, 0xFF5D6B85);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        int infoY = panelY + panelH - 32;
        if (!hoveredStack.isEmpty()) {
            String id = NpcItems.idOf(hoveredStack);
            String label = hoveredStack.getHoverName().getString() + "  (" + id + ")";
            int maxW = panelW - 20;
            graphics.drawString(font, clip(font, label, maxW), panelX + 10, infoY, 0xFFEAEFF8, false);
        } else if (!statusText.isEmpty()) {
            graphics.drawString(font, clip(font, statusText, panelW - 20), panelX + 10, infoY, statusColor, false);
        } else {
            Component hint = Component.translatable(replaceRowIndex >= 0
                    ? "gui.settledlands.loot_pool.picker.hint_replace"
                    : "gui.settledlands.loot_pool.picker.hint_multi");
            graphics.drawString(font, clip(font, hint.getString(), panelW - 20), panelX + 10, infoY, 0xFF8C97AA, false);
        }

        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int maxScr = maxScrollRow();
            if (maxScr > 0) {
                int trackX = gridX + cols * CELL + 4;
                int trackY = gridY;
                int trackH = rows * CELL;
                if (mouseX >= trackX && mouseX <= trackX + 8 && mouseY >= trackY && mouseY <= trackY + trackH) {
                    draggingScroll = true;
                    updateScrollFromMouse(mouseY, trackY, trackH);
                    return true;
                }
            }
            int idx = indexAt(mouseX, mouseY);
            if (idx >= 0 && idx < shown.size()) {
                pickStack(shown.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll && button == 0) {
            updateScrollFromMouse(mouseY, gridY, rows * CELL);
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
        if (maxScrollRow() > 0) {
            int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
            if (step != 0) {
                scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            onClose();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && shown.size() == 1) {
            pickStack(shown.get(0));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int trackY, int trackH) {
        int max = maxScrollRow();
        if (max <= 0 || trackH <= 0) return;
        double frac = (mouseY - trackY) / (double) trackH;
        if (frac < 0.0) frac = 0.0;
        if (frac > 1.0) frac = 1.0;
        scrollRow = (int) Math.round(frac * max);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseY < gridY) return -1;
        int dx = (int) mouseX - gridX;
        int dy = (int) mouseY - gridY;
        int c = dx / CELL;
        int r = dy / CELL;
        if (c < 0 || c >= cols || r < 0 || r >= rows) return -1;
        if (dx % CELL >= 20 || dy % CELL >= 20) return -1;
        int idx = (scrollRow + r) * cols + c;
        return idx >= 0 && idx < shown.size() ? idx : -1;
    }

    private void pickStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = NpcItems.idOf(stack);
        if (id == null || id.isEmpty()) return;
        int suggestedCount = inventoryOnly ? Math.max(1, Math.min(64, stack.getCount())) : 1;
        applyPickedItemId(id, stack.getHoverName().getString(), suggestedCount);
    }

    private void addTypedId() {
        String raw = query == null ? "" : query.trim();
        String id = LootPool436.cleanItemId(raw);
        if (id.isEmpty() || id.contains(" ")) return;
        ItemStack probe = NpcItems.stack(id, 1);
        String name = probe.isEmpty() ? id : probe.getHoverName().getString();
        applyPickedItemId(id, name, 1);
    }

    private void applyPickedItemId(String id, String displayName, int suggestedCount) {
        playClick();
        if (replaceRowIndex >= 0) {
            parent.replaceItemAt(replaceRowIndex, id);
            onClose();
            return;
        }
        parent.addItemEntry(id, suggestedCount);
        if (existingIds != null) existingIds.add(id);
        if (Screen.hasShiftDown()) {
            statusColor = 0xFF7BE088;
            statusText = Component.translatable("gui.settledlands.loot_pool.picker.added_multi",
                    displayName, Integer.valueOf(parent.entryCount())).getString();
        } else {
            onClose();
        }
    }

    private void addAllFromInventory() {
        if (invItems.isEmpty() || replaceRowIndex >= 0) return;
        int added = 0;
        for (ItemStack stack : invItems) {
            if (stack == null || stack.isEmpty()) continue;
            String id = NpcItems.idOf(stack);
            if (id == null || id.isEmpty()) continue;
            if (existingIds != null && existingIds.contains(id)) continue;
            int cnt = Math.max(1, Math.min(64, stack.getCount()));
            parent.addItemEntry(id, cnt);
            if (existingIds != null) existingIds.add(id);
            added++;
        }
        playClick();
        if (added > 0) {
            onClose();
        } else {
            statusColor = 0xFFF5B841;
            statusText = Component.translatable("gui.settledlands.loot_pool.picker.inv_all_present").getString();
        }
    }

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static String clip(Font font, String text, int maxW) {
        if (font == null || text == null || maxW <= 6) return "";
        if (font.width(text) <= maxW) return text;
        String cut = font.plainSubstrByWidth(text, Math.max(4, maxW - font.width("...")));
        return cut + "...";
    }
}
