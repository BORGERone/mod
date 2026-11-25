package dev.settledlands.npc.client;

import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
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
import net.minecraft.world.item.ItemStack;

/**
 * Build 440: full in-game editor window for {@code config/settledlands/loot_pools/*.txt}.
 * Lets builders create, edit, test-roll, and delete loot pools (items, min-max counts, rarity,
 * weights, live drop percentages) directly from the Loot Pool node inspector or pool dropdown.
 */
public final class LootPoolEditorScreen440 extends Screen {

    private static final int ROW_H = 26;
    private static final int MAX_VISIBLE_POPUP = 7;
    private static final int POPUP_ROW_H = 15;

    static volatile LootPoolEditorScreen440 active;

    private final NpcDialogueGraphScreen parentScreen;
    private final NpcDialogueGraph.Node sourceNode;
    private final String requestedPool;

    private final List<RowData> rowsData = new ArrayList<>();
    private final List<RowSlot> slots = new ArrayList<>();
    private final List<PreviewDrop> previewDrops = new ArrayList<>();
    private final Random previewRandom = new Random();

    private EditBox fileNameBox;
    private Button poolDropBtn;
    private Button newPoolBtn;
    private Button deletePoolBtn;
    private Button addItemBtn;
    private Button testRollBtn;
    private Button saveBtn;
    private Button saveCloseBtn;
    private Button closeBtn;

    private String fileNameText = "";
    private String loadedServerFile = "";
    private boolean fileExistsOnServer;
    private boolean querySent;
    private boolean loading;
    private boolean dirty;
    private boolean suppressSync;
    private boolean closeAfterSave;
    private boolean draggingScroll;

    private boolean poolPopupOpen;
    private int poolPopupScroll;
    private long switchWarnAt;
    private String pendingSwitchPool = "";
    private long deleteArmAt;
    private long closeWarnAt;

    private int rowScroll;
    private int visibleRows;
    private boolean previewEmptyWarning;
    private String statusText = "";
    private int statusColor = 0xFF9AA5B8;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowX;
    private int itemColW;
    private int minX;
    private int maxX;
    private int rarX;
    private int wgtX;
    private int pctX;
    private int delX;
    private int listTop;
    private int listBottom;
    private int scrollTrackX;
    private int fileBoxX;
    private int fileBoxY;
    private int fileBoxW;

    private static final class RowData {
        String itemId = "";
        String minText = "1";
        String maxText = "1";
        String rarText = "10";
        String wgtText = "50";
        ItemStack cachedStack = ItemStack.EMPTY;
        String cachedId = "";

        RowData(String itemId, int minCount, int maxCount, int rarity, double weight) {
            this.itemId = itemId == null ? "" : itemId;
            int min = Math.max(1, Math.min(64, minCount));
            int max = Math.max(min, Math.min(64, maxCount));
            this.minText = Integer.toString(min);
            this.maxText = Integer.toString(max);
            this.rarText = Integer.toString(Math.max(0, rarity));
            this.wgtText = LootPool436.formatWeight(weight > 0.0D ? weight : 10.0D);
        }

        ItemStack stack() {
            if (!itemId.equals(cachedId)) {
                cachedId = itemId;
                cachedStack = NpcItems.stack(itemId, 1);
            }
            return cachedStack;
        }

        int parsedRarity() {
            Integer v = LootPool436.parseIntInput(rarText);
            return v == null || v < 0 ? -1 : Math.min(LootPool436.MAX_CAP_LIMIT, v);
        }

        double parsedWeight() {
            Double v = LootPool436.parseWeightInput(wgtText);
            return v == null || !Double.isFinite(v) || v <= 0.0D ? 0.0D : v;
        }

        int parsedMin() {
            Integer v = LootPool436.parseIntInput(minText);
            return v == null ? -1 : Math.max(1, Math.min(64, v));
        }

        int parsedMax() {
            Integer v = LootPool436.parseIntInput(maxText);
            return v == null ? -1 : Math.max(1, Math.min(64, v));
        }

        LootPool436.PoolEntry toPoolEntry() {
            String cleanId = LootPool436.cleanItemId(itemId);
            if (cleanId.isEmpty() || !cleanId.contains(":")) return null;
            int rar = parsedRarity();
            double wgt = parsedWeight();
            int min = parsedMin();
            int max = parsedMax();
            if (rar < 0 || wgt <= 0.0D || min <= 0 || max <= 0) return null;
            if (max < min) max = min;
            return new LootPool436.PoolEntry(cleanId, rar, wgt, min, max);
        }
    }

    private static final class RowSlot {
        EditBox minBox;
        EditBox maxBox;
        EditBox rarBox;
        EditBox wgtBox;
        Button delBtn;
        int y;

        void setVisible(boolean visible) {
            minBox.visible = visible;
            minBox.setEditable(visible);
            maxBox.visible = visible;
            maxBox.setEditable(visible);
            rarBox.visible = visible;
            rarBox.setEditable(visible);
            wgtBox.visible = visible;
            wgtBox.setEditable(visible);
            delBtn.visible = visible;
            delBtn.active = visible;
        }

        void clearFocus() {
            minBox.setFocused(false);
            maxBox.setFocused(false);
            rarBox.setFocused(false);
            wgtBox.setFocused(false);
        }
    }

    private static final class PreviewDrop {
        final ItemStack stack;
        final int count;

        PreviewDrop(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }

    public LootPoolEditorScreen440(NpcDialogueGraphScreen parentScreen,
            NpcDialogueGraph.Node sourceNode, String requestedPool) {
        super(Component.translatable("gui.settledlands.loot_pool.editor.title"));
        this.parentScreen = parentScreen;
        this.sourceNode = sourceNode;
        this.requestedPool = LootPool436.sanitizeFileName(requestedPool);
    }

    public static void handleServerData(String rawPayload) {
        LootPoolEditorScreen440 ed = active;
        if (ed == null || rawPayload == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != ed) return;
        ed.applyServerData(rawPayload);
    }

    public static void onPoolsListUpdated() {
        LootPoolEditorScreen440 ed = active;
        if (ed == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen == ed) {
            ed.updateActionButtons();
        }
    }

    @Override
    protected void init() {
        active = this;
        LootPoolCard436.ensureClientListenersRegistered();

        panelW = Math.max(Math.min(width - 16, 620), Math.min(width - 8, 370));
        panelH = Math.max(Math.min(height - 16, 390), Math.min(height - 8, 224));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        rowX = panelX + 10;
        int contentW = panelW - 28;
        int rowRight = rowX + contentW;
        scrollTrackX = rowRight + 6;

        delX = rowRight - 20;
        pctX = delX - 4 - 42;
        wgtX = pctX - 6 - 48;
        rarX = wgtX - 6 - 44;
        maxX = rarX - 8 - 28;
        minX = maxX - 10 - 28;
        itemColW = Math.max(70, minX - 6 - rowX);

        int headRight = panelX + panelW - 10;
        closeBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.close"),
                        b -> tryCloseScreen())
                .bounds(headRight - 64, panelY + 6, 64, 16)
                .build();
        addRenderableWidget(closeBtn);

        deletePoolBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.delete"),
                        b -> onDeletePressed())
                .bounds(headRight - 64 - 84, panelY + 6, 80, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.delete_tip")))
                .build();
        addRenderableWidget(deletePoolBtn);

        int fileRowY = panelY + 28;
        int newBtnW = 76;
        int dropBtnW = 18;
        int fileLabelW = 42;
        fileBoxX = panelX + 10 + fileLabelW;
        fileBoxY = fileRowY;
        fileBoxW = Math.max(90, headRight - newBtnW - 4 - dropBtnW - 2 - fileBoxX);

        fileNameBox = new EditBox(font, fileBoxX, fileBoxY, fileBoxW, 18,
                Component.translatable("gui.settledlands.loot_pool.file"));
        fileNameBox.setMaxLength(64);
        fileNameBox.setHint(Component.literal("loot_pool.txt"));
        fileNameBox.setFilter(LootPoolEditorScreen440::isValidFileChar);
        suppressSync = true;
        fileNameBox.setValue(fileNameText);
        suppressSync = false;
        fileNameBox.setResponder(txt -> {
            if (suppressSync) return;
            fileNameText = txt == null ? "" : txt;
            markDirty();
        });
        addRenderableWidget(fileNameBox);

        poolDropBtn = Button.builder(Component.literal("v"), b -> togglePoolPopup())
                .bounds(fileBoxX + fileBoxW + 2, fileRowY, dropBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.switch_tip")))
                .build();
        addRenderableWidget(poolDropBtn);

        newPoolBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.new_pool"),
                        b -> startNewPool())
                .bounds(headRight - newBtnW, fileRowY, newBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.new_tip")))
                .build();
        addRenderableWidget(newPoolBtn);

        listTop = panelY + 76;
        int footerH = 48;
        listBottom = panelY + panelH - footerH;
        visibleRows = Math.max(2, (listBottom - listTop) / ROW_H);

        slots.clear();
        for (int s = 0; s < visibleRows; s++) {
            final int slotIdx = s;
            int ry = listTop + s * ROW_H;
            int wy = ry + 4;
            RowSlot slot = new RowSlot();
            slot.y = ry;

            slot.minBox = new EditBox(font, minX, wy, 28, 18, Component.empty());
            slot.minBox.setMaxLength(3);
            slot.minBox.setFilter(LootPoolEditorScreen440::isIntInput);
            slot.minBox.setResponder(val -> onRowFieldChanged(slotIdx, 0, val));
            addRenderableWidget(slot.minBox);

            slot.maxBox = new EditBox(font, maxX, wy, 28, 18, Component.empty());
            slot.maxBox.setMaxLength(3);
            slot.maxBox.setFilter(LootPoolEditorScreen440::isIntInput);
            slot.maxBox.setResponder(val -> onRowFieldChanged(slotIdx, 1, val));
            addRenderableWidget(slot.maxBox);

            slot.rarBox = new EditBox(font, rarX, wy, 44, 18, Component.empty());
            slot.rarBox.setMaxLength(7);
            slot.rarBox.setFilter(LootPoolEditorScreen440::isIntInput);
            slot.rarBox.setResponder(val -> onRowFieldChanged(slotIdx, 2, val));
            addRenderableWidget(slot.rarBox);

            slot.wgtBox = new EditBox(font, wgtX, wy, 48, 18, Component.empty());
            slot.wgtBox.setMaxLength(9);
            slot.wgtBox.setFilter(LootPoolEditorScreen440::isDecimalInput);
            slot.wgtBox.setResponder(val -> onRowFieldChanged(slotIdx, 3, val));
            addRenderableWidget(slot.wgtBox);

            slot.delBtn = Button.builder(Component.literal("x"), b -> deleteRowAtSlot(slotIdx))
                    .bounds(delX, wy, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.remove_row")))
                    .build();
            addRenderableWidget(slot.delBtn);

            slots.add(slot);
        }

        int btnY = panelY + panelH - 24;
        boolean compact = panelW < 470;
        int addW = compact ? 92 : 118;
        int testW = compact ? 54 : 84;
        int saveCloseW = compact ? 96 : 124;
        int saveW = compact ? 66 : 80;

        addItemBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.add_item"),
                        b -> openItemPicker(-1))
                .bounds(panelX + 10, btnY, addW, 18)
                .build();
        addRenderableWidget(addItemBtn);

        testRollBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.test"),
                        b -> runTestRoll())
                .bounds(panelX + 14 + addW, btnY, testW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.test_tip")))
                .build();
        addRenderableWidget(testRollBtn);

        saveCloseBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.save_close"),
                        b -> triggerSave(true))
                .bounds(headRight - saveCloseW, btnY, saveCloseW, 18)
                .build();
        addRenderableWidget(saveCloseBtn);

        saveBtn = Button.builder(
                        Component.translatable("gui.settledlands.loot_pool.editor.save"),
                        b -> triggerSave(false))
                .bounds(headRight - saveCloseW - 4 - saveW, btnY, saveW, 18)
                .build();
        addRenderableWidget(saveBtn);

        if (!querySent) {
            querySent = true;
            loading = true;
            statusColor = 0xFF9AA5B8;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.loading").getString();
            ChestLootNet436.sendPoolQuery(requestedPool);
        }

        refreshRowWidgets(false);
        updateActionButtons();
    }

    private void applyServerData(String rawPayload) {
        String[] lines = rawPayload.split("\n", -1);
        String status = lines.length >= 1 ? lines[0].trim() : "";
        String file = lines.length >= 2 ? lines[1].trim() : "";

        loading = false;

        if (status.startsWith("ERR:")) {
            String errCode = status.substring(4);
            statusColor = 0xFFFF7070;
            if ("noperm".equals(errCode)) {
                statusText = Component.translatable("message.settledlands.cfg.noperm").getString();
            } else if ("bad_name".equals(errCode)) {
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_name").getString();
            } else {
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_io").getString();
            }
            updateActionButtons();
            return;
        }

        if ("DELETED".equals(status)) {
            if (sourceNode != null && file.equals(LootPool436.poolFileOf(sourceNode))) {
                updateNodePoolFile("");
            }
            loadedServerFile = "";
            fileExistsOnServer = false;
            rowsData.clear();
            previewDrops.clear();
            previewEmptyWarning = false;
            rowScroll = 0;
            String nextName = LootPool436.suggestNextPoolName(LootPool436.getServerPools());
            setFileNameInput(nextName);
            dirty = false;
            deleteArmAt = 0L;
            statusColor = 0xFFF5B841;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.deleted", file).getString();
            refreshRowWidgets(true);
            updateActionButtons();
            return;
        }

        if ("SAVED".equals(status)) {
            loadedServerFile = file;
            fileExistsOnServer = true;
            setFileNameInput(file);
            dirty = false;
            deleteArmAt = 0L;
            if (sourceNode != null) {
                updateNodePoolFile(file);
            }
            if (closeAfterSave) {
                closeAfterSave = false;
                returnToParentScreen();
                return;
            }
            statusColor = 0xFF7BE088;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.saved", file).getString();
            updateActionButtons();
            return;
        }

        // "OK" or "NEW"
        rowsData.clear();
        previewDrops.clear();
        previewEmptyWarning = false;
        rowScroll = 0;
        if ("OK".equals(status)) {
            List<LootPool436.PoolEntry> entries = LootPool436.decodeEntriesWire(lines, 2);
            for (LootPool436.PoolEntry e : entries) {
                rowsData.add(new RowData(e.itemId, e.minCount, e.maxCount, e.rarity, e.weight));
            }
            loadedServerFile = file;
            fileExistsOnServer = true;
            statusColor = 0xFF9AA5B8;
            statusText = "";
        } else {
            loadedServerFile = "";
            fileExistsOnServer = false;
            statusColor = 0xFFF5B841;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.new_hint").getString();
        }
        setFileNameInput(file);
        dirty = false;
        deleteArmAt = 0L;
        refreshRowWidgets(true);
        updateActionButtons();
    }

    private void updateNodePoolFile(String poolFile) {
        if (sourceNode == null) return;
        if (parentScreen != null) {
            parentScreen.settledlands$remember();
        }
        sourceNode.action = LootPool436.formatAction(
                poolFile,
                LootPool436.rawRarity(sourceNode),
                LootPool436.rawWeight(sourceNode),
                LootPool436.rawRolls(sourceNode));
        if (parentScreen != null) {
            parentScreen.settledlands$changed();
        }
    }

    private void setFileNameInput(String text) {
        fileNameText = text == null ? "" : text;
        if (fileNameBox != null) {
            suppressSync = true;
            fileNameBox.setValue(fileNameText);
            suppressSync = false;
        }
    }

    private void markDirty() {
        dirty = true;
        closeWarnAt = 0L;
        deleteArmAt = 0L;
        pendingSwitchPool = "";
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (deletePoolBtn != null) {
            boolean armed = deleteArmAt > 0L && (System.currentTimeMillis() - deleteArmAt) < 4000L;
            deletePoolBtn.active = !loading && fileExistsOnServer && !loadedServerFile.isEmpty();
            deletePoolBtn.setMessage(Component.translatable(armed
                    ? "gui.settledlands.loot_pool.editor.delete_confirm"
                    : "gui.settledlands.loot_pool.editor.delete"));
        }
        if (saveBtn != null) saveBtn.active = !loading;
        if (saveCloseBtn != null) saveCloseBtn.active = !loading;
        if (addItemBtn != null) addItemBtn.active = !loading;
        if (testRollBtn != null) testRollBtn.active = !loading && !rowsData.isEmpty();
    }

    private void refreshRowWidgets(boolean resetFocus) {
        int maxScr = maxRowScroll();
        if (rowScroll > maxScr) rowScroll = maxScr;
        if (rowScroll < 0) rowScroll = 0;

        suppressSync = true;
        try {
            for (int s = 0; s < slots.size(); s++) {
                RowSlot slot = slots.get(s);
                if (resetFocus) slot.clearFocus();
                int idx = rowScroll + s;
                if (idx >= 0 && idx < rowsData.size()) {
                    RowData data = rowsData.get(idx);
                    slot.minBox.setValue(data.minText);
                    slot.maxBox.setValue(data.maxText);
                    slot.rarBox.setValue(data.rarText);
                    slot.wgtBox.setValue(data.wgtText);
                    slot.setVisible(true);
                } else {
                    slot.minBox.setValue("");
                    slot.maxBox.setValue("");
                    slot.rarBox.setValue("");
                    slot.wgtBox.setValue("");
                    slot.setVisible(false);
                }
            }
        } finally {
            suppressSync = false;
        }
    }

    private void onRowFieldChanged(int slotIdx, int fieldIdx, String value) {
        if (suppressSync) return;
        int dataIdx = rowScroll + slotIdx;
        if (dataIdx < 0 || dataIdx >= rowsData.size()) return;
        RowData row = rowsData.get(dataIdx);
        String v = value == null ? "" : value;
        if (fieldIdx == 0) row.minText = v;
        else if (fieldIdx == 1) row.maxText = v;
        else if (fieldIdx == 2) row.rarText = v;
        else if (fieldIdx == 3) row.wgtText = v;
        markDirty();
    }

    private void deleteRowAtSlot(int slotIdx) {
        int idx = rowScroll + slotIdx;
        if (idx < 0 || idx >= rowsData.size()) return;
        rowsData.remove(idx);
        previewDrops.clear();
        previewEmptyWarning = false;
        markDirty();
        refreshRowWidgets(true);
        updateActionButtons();
    }

    public int entryCount() {
        return rowsData.size();
    }

    public void addItemEntry(String itemId, int suggestedCount) {
        String clean = LootPool436.cleanItemId(itemId);
        if (clean.isEmpty()) return;
        int cnt = Math.max(1, Math.min(64, suggestedCount));
        rowsData.add(new RowData(clean, cnt, cnt, 10, 50.0D));
        rowScroll = Math.max(0, rowsData.size() - visibleRows);
        previewDrops.clear();
        previewEmptyWarning = false;
        markDirty();
    }

    public void replaceItemAt(int rowIdx, String itemId) {
        String clean = LootPool436.cleanItemId(itemId);
        if (clean.isEmpty() || rowIdx < 0 || rowIdx >= rowsData.size()) return;
        RowData row = rowsData.get(rowIdx);
        row.itemId = clean;
        row.cachedId = "";
        previewDrops.clear();
        previewEmptyWarning = false;
        markDirty();
    }

    private void openItemPicker(int replaceRowIdx) {
        Set<String> present = new HashSet<>();
        for (RowData r : rowsData) {
            if (r.itemId != null && !r.itemId.isEmpty()) present.add(r.itemId);
        }
        poolPopupOpen = false;
        Minecraft.getInstance().setScreen(new LootPoolItemPickerScreen440(this, replaceRowIdx, present));
    }

    private void startNewPool() {
        poolPopupOpen = false;
        rowsData.clear();
        previewDrops.clear();
        previewEmptyWarning = false;
        rowScroll = 0;
        loadedServerFile = "";
        fileExistsOnServer = false;
        String next = LootPool436.suggestNextPoolName(LootPool436.getServerPools());
        setFileNameInput(next);
        dirty = true;
        deleteArmAt = 0L;
        statusColor = 0xFFF5B841;
        statusText = Component.translatable("gui.settledlands.loot_pool.editor.new_hint").getString();
        refreshRowWidgets(true);
        updateActionButtons();
        if (fileNameBox != null) {
            setFocused(fileNameBox);
            fileNameBox.setFocused(true);
        }
    }

    private void togglePoolPopup() {
        poolPopupOpen = !poolPopupOpen;
        poolPopupScroll = 0;
        pendingSwitchPool = "";
        if (poolPopupOpen) {
            for (RowSlot s : slots) s.clearFocus();
            if (fileNameBox != null) fileNameBox.setFocused(false);
        }
    }

    private void onDeletePressed() {
        if (!fileExistsOnServer || loadedServerFile.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (deleteArmAt == 0L || (now - deleteArmAt) >= 4000L) {
            deleteArmAt = now;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.delete_warn",
                    loadedServerFile).getString();
            updateActionButtons();
            return;
        }
        deleteArmAt = 0L;
        loading = true;
        closeAfterSave = false;
        ChestLootNet436.sendPoolSave("DELETE", loadedServerFile, null);
        updateActionButtons();
    }

    private void triggerSave(boolean closeAfter) {
        String normName = LootPool436.normalizeSaveName(fileNameText);
        if (normName.isEmpty()) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_name").getString();
            if (fileNameBox != null) setFocused(fileNameBox);
            return;
        }

        List<LootPool436.PoolEntry> validEntries = new ArrayList<>(rowsData.size());
        for (int i = 0; i < rowsData.size(); i++) {
            RowData row = rowsData.get(i);
            LootPool436.PoolEntry entry = row.toPoolEntry();
            if (entry == null) {
                statusColor = 0xFFFF7070;
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_row",
                        Integer.valueOf(i + 1)).getString();
                if (i < rowScroll || i >= rowScroll + visibleRows) {
                    rowScroll = Math.max(0, Math.min(maxRowScroll(), i));
                    refreshRowWidgets(true);
                }
                return;
            }
            // Normalize min/max in UI if user typed min > max
            row.minText = Integer.toString(entry.minCount);
            row.maxText = Integer.toString(entry.maxCount);
            validEntries.add(entry);
        }
        refreshRowWidgets(false);

        closeAfterSave = closeAfter;
        loading = true;
        if (!ChestLootNet436.sendPoolSave("SAVE", normName, validEntries)) {
            loading = false;
            closeAfterSave = false;
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_too_big").getString();
        }
        updateActionButtons();
    }

    private void runTestRoll() {
        previewDrops.clear();
        previewEmptyWarning = false;
        List<LootPool436.PoolEntry> entries = new ArrayList<>(rowsData.size());
        for (RowData r : rowsData) {
            LootPool436.PoolEntry e = r.toPoolEntry();
            if (e != null) entries.add(e);
        }
        if (entries.isEmpty()) {
            previewEmptyWarning = true;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.preview_none").getString();
            return;
        }
        LootPool436.Parsed spec = currentNodeSpec();
        List<LootPool436.Drop> rolled = LootPool436.rollDrops(entries, spec, previewRandom);
        if (rolled.isEmpty()) {
            previewEmptyWarning = true;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.preview_filtered").getString();
            return;
        }
        for (LootPool436.Drop d : rolled) {
            ItemStack st = NpcItems.stack(d.itemId, 1);
            if (!st.isEmpty()) {
                previewDrops.add(new PreviewDrop(st, d.count));
            }
        }
        statusText = "";
    }

    private LootPool436.Parsed currentNodeSpec() {
        if (sourceNode != null) {
            LootPool436.Parsed p = LootPool436.parse(sourceNode.action);
            if (p != null) {
                return new LootPool436.Parsed(fileNameText, p.rawRarity, p.rawWeight, p.rawRolls);
            }
        }
        return new LootPool436.Parsed(fileNameText, "", "", LootPool436.DEFAULT_ROLLS);
    }

    private void tryCloseScreen() {
        if (dirty) {
            long now = System.currentTimeMillis();
            if (closeWarnAt == 0L || (now - closeWarnAt) >= 6000L) {
                closeWarnAt = now;
                statusColor = 0xFFFFB048;
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.unsaved_warn")
                        .getString();
                return;
            }
        }
        returnToParentScreen();
    }

    private void returnToParentScreen() {
        active = null;
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private int maxRowScroll() {
        return Math.max(0, rowsData.size() - visibleRows);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int accent = 0xFF000000 | LootPool436.ACCENT_COLOR;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF080A0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        // Header
        LootPoolGearButton440.drawGearIcon(graphics, panelX + 9, panelY + 8, true, 0xEE141821);
        String heading = Component.translatable("gui.settledlands.loot_pool.editor.title").getString();
        if (dirty) heading = heading + " *";
        graphics.drawString(font, heading, panelX + 25, panelY + 10, 0xFFF2F5FA, false);

        // File row label
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.file_lbl"),
                panelX + 10, panelY + 33, 0xFFB0BACB, false);

        // Summary line
        renderSummaryLine(graphics);

        // Column headers
        int hdrY = listTop - 13;
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_item"),
                rowX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_count"),
                minX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_rarity"),
                rarX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_weight"),
                wgtX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_chance"),
                pctX, hdrY, 0xFF9AA5B8, false);

        // Table frame
        int tableRight = rowX + (panelW - 28);
        graphics.fill(rowX - 2, listTop - 2, tableRight + 2, listBottom + 2, 0x990D1118);
        graphics.renderOutline(rowX - 2, listTop - 2, (tableRight - rowX) + 4, (listBottom - listTop) + 4,
                0xFF272F40);

        // Compute total weight for live chance column
        double totalWeight = 0.0D;
        for (RowData r : rowsData) {
            totalWeight += r.parsedWeight();
        }
        LootPool436.Parsed nodeSpec = currentNodeSpec();

        ItemStack hoveredRowStack = ItemStack.EMPTY;
        Component extraTooltip = null;

        if (rowsData.isEmpty()) {
            Component emptyKey = Component.translatable(loading
                    ? "gui.settledlands.loot_pool.editor.loading"
                    : "gui.settledlands.loot_pool.editor.empty_rows");
            graphics.drawCenteredString(font, emptyKey, rowX + (tableRight - rowX) / 2,
                    (listTop + listBottom) / 2 - 4, 0xFF8390A6);
        } else {
            for (int s = 0; s < visibleRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                RowData data = rowsData.get(idx);
                int ry = listTop + s * ROW_H;

                int rowBg = (idx % 2 == 0) ? 0x5519202B : 0x33141923;
                graphics.fill(rowX, ry, tableRight, ry + ROW_H - 1, rowBg);

                // Item picker cell
                int itemBtnY = ry + 3;
                int itemBtnH = 20;
                boolean overItem = !poolPopupOpen
                        && mouseX >= rowX && mouseX < rowX + itemColW
                        && mouseY >= itemBtnY && mouseY < itemBtnY + itemBtnH;
                boolean validItem = !data.itemId.isEmpty();
                int itemBg = overItem ? 0xFF283142 : 0xFF171C25;
                int itemFrame = !validItem ? 0xFFCC4444 : (overItem ? accent : 0xFF343E52);
                graphics.fill(rowX, itemBtnY, rowX + itemColW, itemBtnY + itemBtnH, itemBg);
                graphics.renderOutline(rowX, itemBtnY, itemColW, itemBtnH, itemFrame);

                ItemStack stack = data.stack();
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, rowX + 2, itemBtnY + 2);
                    String displayName = stack.getHoverName().getString();
                    int textX = rowX + 21;
                    int avail = itemColW - 24;
                    graphics.drawString(font, clip(font, displayName, avail), textX, itemBtnY + 6,
                            0xFFEAEFF8, false);
                    if (overItem) {
                        hoveredRowStack = stack;
                    }
                } else {
                    String rawLabel = data.itemId.isEmpty() ? "<...>" : data.itemId;
                    graphics.drawString(font, clip(font, rawLabel, itemColW - 8), rowX + 4, itemBtnY + 6,
                            0xFFFF9888, false);
                    if (overItem) {
                        extraTooltip = Component.translatable(
                                "gui.settledlands.loot_pool.editor.unknown_item", rawLabel);
                    }
                }

                // Dash between min and max
                graphics.drawString(font, "-", minX + 30, ry + 8, 0xFF9AA5B8, false);

                // Invalid cell highlights
                if (data.parsedMin() < 0) {
                    graphics.renderOutline(minX - 1, ry + 3, 30, 20, 0xFFDD4444);
                }
                if (data.parsedMax() < 0) {
                    graphics.renderOutline(maxX - 1, ry + 3, 30, 20, 0xFFDD4444);
                }
                if (data.parsedRarity() < 0) {
                    graphics.renderOutline(rarX - 1, ry + 3, 46, 20, 0xFFDD4444);
                }
                if (data.parsedWeight() <= 0.0D) {
                    graphics.renderOutline(wgtX - 1, ry + 3, 50, 20, 0xFFDD4444);
                }

                // Live drop percentage + node filter badge
                double w = data.parsedWeight();
                String pctStr;
                int pctColor;
                boolean filteredOut = false;
                if (w <= 0.0D || totalWeight <= 0.0D) {
                    pctStr = "--";
                    pctColor = 0xFFDD6666;
                } else {
                    double pct = (w / totalWeight) * 100.0D;
                    pctStr = pct >= 10.0D
                            ? String.format(Locale.ROOT, "%.1f%%", pct)
                            : String.format(Locale.ROOT, "%.2f%%", pct);
                    LootPool436.PoolEntry entry = data.toPoolEntry();
                    if (sourceNode != null && entry != null && !LootPool436.passesSpec(entry, nodeSpec)) {
                        filteredOut = true;
                        pctStr = "!" + pctStr;
                        pctColor = 0xFFFF9E44;
                    } else {
                        pctColor = 0xFF93E2A2;
                    }
                }
                graphics.drawString(font, clip(font, pctStr, 42), pctX, ry + 8, pctColor, false);
                if (filteredOut && !poolPopupOpen
                        && mouseX >= pctX && mouseX < delX - 2 && mouseY >= ry && mouseY < ry + ROW_H) {
                    extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.filtered_tip");
                }
            }
        }

        // Scrollbar for rows
        int maxScr = maxRowScroll();
        if (maxScr > 0) {
            int trackH = listBottom - listTop;
            graphics.fill(scrollTrackX, listTop, scrollTrackX + 5, listTop + trackH, 0xFF10141C);
            int thumbH = Math.max(18, trackH * visibleRows / Math.max(1, rowsData.size()));
            int thumbY = listTop + (trackH - thumbH) * rowScroll / Math.max(1, maxScr);
            graphics.fill(scrollTrackX, thumbY, scrollTrackX + 5, thumbY + thumbH, 0xFF62718C);
        }

        // Status / Roll Preview strip
        ItemStack previewHover = renderBottomStrip(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (poolPopupOpen) {
            renderPoolPopup(graphics, mouseX, mouseY);
        } else if (!previewHover.isEmpty()) {
            graphics.renderTooltip(font, previewHover, mouseX, mouseY);
        } else if (!hoveredRowStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredRowStack, mouseX, mouseY);
        } else if (extraTooltip != null) {
            setTooltipForNextRenderPass(extraTooltip);
        }
    }

    private void renderSummaryLine(GuiGraphics graphics) {
        int y = panelY + 51;
        double totalW = 0.0D;
        for (RowData r : rowsData) totalW += r.parsedWeight();
        String baseSummary = Component.translatable(
                "gui.settledlands.loot_pool.editor.summary",
                Integer.valueOf(rowsData.size()),
                LootPool436.formatWeight(totalW)).getString();
        if (sourceNode != null) {
            String any = Component.translatable("gui.settledlands.loot_pool.any").getString();
            String rar = LootPool436.summaryRange(LootPool436.rawRarity(sourceNode), any);
            String wgt = LootPool436.summaryRange(LootPool436.rawWeight(sourceNode), any);
            String rls = LootPool436.rawRolls(sourceNode);
            if (rls.isEmpty()) rls = LootPool436.DEFAULT_ROLLS;
            baseSummary = baseSummary + "  |  " + Component.translatable(
                    "gui.settledlands.loot_pool.editor.node_caps", rar, wgt, rls).getString();
        }
        graphics.drawString(font, clip(font, baseSummary, panelW - 20), panelX + 10, y, 0xFF8C98AD, false);
    }

    private ItemStack renderBottomStrip(GuiGraphics graphics, int mouseX, int mouseY) {
        int stripY = panelY + panelH - 44;
        ItemStack hovered = ItemStack.EMPTY;
        if (!previewDrops.isEmpty() && statusText.isEmpty()) {
            String prefix = Component.translatable("gui.settledlands.loot_pool.editor.preview_lbl").getString();
            graphics.drawString(font, prefix, panelX + 10, stripY + 4, 0xFFF5B841, false);
            int x = panelX + 14 + font.width(prefix);
            int maxXLimit = panelX + panelW - 14;
            for (PreviewDrop d : previewDrops) {
                if (x + 22 > maxXLimit) break;
                boolean over = !poolPopupOpen && mouseX >= x && mouseX < x + 18
                        && mouseY >= stripY && mouseY < stripY + 18;
                graphics.fill(x, stripY, x + 18, stripY + 18, over ? 0xFF2B3548 : 0xFF191F2A);
                graphics.renderOutline(x, stripY, 18, 18, over ? (0xFF000000 | LootPool436.ACCENT_COLOR) : 0xFF333D50);
                graphics.renderItem(d.stack, x + 1, stripY + 1);
                if (d.count > 1) {
                    String cnt = Integer.toString(d.count);
                    int cw = font.width(cnt);
                    graphics.drawString(font, cnt, x + 17 - cw, stripY + 9, 0xFFFFFFFF, true);
                }
                if (over) hovered = d.stack;
                x += 22;
            }
            return hovered;
        }
        if (!statusText.isEmpty()) {
            graphics.drawString(font, clip(font, statusText, panelW - 20), panelX + 10, stripY + 4,
                    statusColor, false);
        }
        return hovered;
    }

    private void renderPoolPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> pools = LootPool436.getServerPools();
        int px = fileBoxX;
        int py = fileBoxY + 19;
        int pw = Math.max(fileBoxW + 20, 140);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, pools.size()));
        int ph = visible * POPUP_ROW_H + 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);

        graphics.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000);
        graphics.fill(px, py, px + pw, py + ph, 0xF5181C24);
        graphics.renderOutline(px - 1, py - 1, pw + 2, ph + 2, 0xFF000000 | LootPool436.ACCENT_COLOR);

        if (pools.isEmpty()) {
            graphics.drawString(font,
                    clip(font, Component.translatable("gui.settledlands.loot_pool.none").getString(), pw - 10),
                    px + 5, py + 4, 0xFF8A95A8, false);
        } else {
            for (int r = 0; r < visible; r++) {
                int idx = poolPopupScroll + r;
                if (idx >= pools.size()) break;
                String name = pools.get(idx);
                int ry = py + 1 + r * POPUP_ROW_H;
                boolean over = mouseX >= px && mouseX <= px + pw && mouseY >= ry && mouseY < ry + POPUP_ROW_H;
                if (over) {
                    graphics.fill(px + 1, ry, px + pw - 1, ry + POPUP_ROW_H, 0xFF2D374B);
                }
                int col = name.equals(loadedServerFile) ? (0xFF000000 | LootPool436.ACCENT_COLOR) : 0xFFE2E8F2;
                graphics.drawString(font, clip(font, name, pw - 14), px + 5, ry + 3, col, false);
            }
            if (pools.size() > visible) {
                int barH = Math.max(8, (visible * POPUP_ROW_H) * visible / pools.size());
                int maxPScr = Math.max(1, pools.size() - visible);
                int barY = py + 1 + ((visible * POPUP_ROW_H - barH) * poolPopupScroll / maxPScr);
                graphics.fill(px + pw - 4, barY, px + pw - 1, barY + barH, 0xFF6B7994);
            }
        }

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (poolPopupOpen) {
            if (button == 0 && handlePopupClick(mouseX, mouseY)) {
                return true;
            }
            poolPopupOpen = false;
            return true;
        }

        if (button == 0) {
            int maxScr = maxRowScroll();
            if (maxScr > 0) {
                int trackH = listBottom - listTop;
                if (mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + 8
                        && mouseY >= listTop && mouseY <= listTop + trackH) {
                    draggingScroll = true;
                    updateRowScrollFromMouse(mouseY);
                    return true;
                }
            }

            // Item picker cell click inside visible rows
            for (int s = 0; s < visibleRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                int ry = listTop + s * ROW_H + 3;
                if (mouseX >= rowX && mouseX < rowX + itemColW && mouseY >= ry && mouseY < ry + 20) {
                    playClick();
                    openItemPicker(idx);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePopupClick(double mouseX, double mouseY) {
        List<String> pools = LootPool436.getServerPools();
        int px = fileBoxX;
        int py = fileBoxY + 19;
        int pw = Math.max(fileBoxW + 20, 140);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, pools.size()));
        int ph = visible * POPUP_ROW_H + 2;

        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) {
            return false;
        }
        if (pools.isEmpty()) {
            poolPopupOpen = false;
            return true;
        }
        int row = (int) ((mouseY - (py + 1)) / POPUP_ROW_H);
        int idx = poolPopupScroll + row;
        if (idx < 0 || idx >= pools.size()) return true;

        String chosen = pools.get(idx);
        long now = System.currentTimeMillis();
        if (dirty && !chosen.equals(pendingSwitchPool)
                && (switchWarnAt == 0L || (now - switchWarnAt) >= 5000L)) {
            pendingSwitchPool = chosen;
            switchWarnAt = now;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.switch_warn",
                    chosen).getString();
            return true;
        }
        pendingSwitchPool = "";
        poolPopupOpen = false;
        loading = true;
        statusColor = 0xFF9AA5B8;
        statusText = Component.translatable("gui.settledlands.loot_pool.editor.loading").getString();
        ChestLootNet436.sendPoolQuery(chosen);
        updateActionButtons();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroll && button == 0) {
            updateRowScrollFromMouse(mouseY);
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
            if (poolPopupOpen) {
                int maxPScr = Math.max(0, LootPool436.getServerPools().size() - MAX_VISIBLE_POPUP);
                poolPopupScroll = Math.max(0, Math.min(maxPScr, poolPopupScroll + step));
                return true;
            }
            if (maxRowScroll() > 0 && mouseX >= panelX && mouseX <= panelX + panelW
                    && mouseY >= listTop && mouseY <= listBottom) {
                rowScroll = Math.max(0, Math.min(maxRowScroll(), rowScroll + step));
                refreshRowWidgets(true);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            if (poolPopupOpen) {
                poolPopupOpen = false;
                return true;
            }
            tryCloseScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        tryCloseScreen();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateRowScrollFromMouse(double mouseY) {
        int max = maxRowScroll();
        int trackH = listBottom - listTop;
        if (max <= 0 || trackH <= 0) return;
        double frac = (mouseY - listTop) / (double) trackH;
        if (frac < 0.0) frac = 0.0;
        if (frac > 1.0) frac = 1.0;
        int next = (int) Math.round(frac * max);
        if (next != rowScroll) {
            rowScroll = next;
            refreshRowWidgets(true);
        }
    }

    private static boolean isIntInput(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static boolean isDecimalInput(String s) {
        if (s == null || s.isEmpty()) return true;
        boolean seenSep = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') continue;
            if ((c == '.' || c == ',') && !seenSep) {
                seenSep = true;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isValidFileChar(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '\\' || c == '|' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>') {
                return false;
            }
        }
        return true;
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
