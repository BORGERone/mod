package dev.settledlands.npc.client;

import dev.settledlands.chest.ChestLootNet436;
import dev.settledlands.chest.CustomBook443;
import dev.settledlands.chest.CustomBookManager443;
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
 * Build 440 + Build 441: full in-game editor window for {@code config/settledlands/loot_pools/*.txt}.
 * Lets builders create, edit, test-roll, and delete loot pools (items, single-field {@code min-max}
 * counts, CS:GO-style drop rarity chances, crate capacity weights, live drop percentages) across the
 * full screen height right down to the bottom action bar.
 */
public final class LootPoolEditorScreen440 extends Screen {

    private static final int ROW_H = 24;
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
    private EditBox nodeRarBox;
    private EditBox nodeWgtBox;
    private EditBox nodeRollsBox;
    private Button poolDropBtn;
    private Button newPoolBtn;
    private Button deletePoolBtn;
    private Button addItemBtn;
    private Button addBookBtn;
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
    private boolean nodeLimitsUndoRecorded;
    private boolean closeAfterSave;
    private boolean draggingScroll;

    private int nodeRowY;
    private int nodeRarLblX;
    private int nodeWgtLblX;
    private int nodeRollsLblX;

    private boolean poolPopupOpen;
    private int poolPopupScroll;
    private long switchWarnAt;
    private String pendingSwitchPool = "";
    private long deleteArmAt;
    private long closeWarnAt;

    private int rowScroll;
    private int fullVisibleRows;
    private int compactVisibleRows;
    private String statusText = "";
    private int statusColor = 0xFF9AA5B8;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowX;
    private int itemColW;
    private int cntX;
    private int cntW;
    private int rarX;
    private int rarW;
    private int wgtX;
    private int wgtW;
    private int pctX;
    private int pctW;
    private int delX;
    private int listTop;
    private int fullListBottom;
    private int compactListBottom;
    private int stripY;
    private int btnY;
    private int scrollTrackX;
    private int fileBoxX;
    private int fileBoxY;
    private int fileBoxW;

    private static final class RowData {
        String itemId = "";
        String countText = "1";
        String rarText = "50";
        String wgtText = "10";
        ItemStack cachedStack = ItemStack.EMPTY;
        String cachedId = "";

        RowData(String itemId, int minCount, int maxCount, double rarity, double weight) {
            this.itemId = itemId == null ? "" : itemId;
            this.countText = LootPool436.formatCountRange(minCount, maxCount);
            this.rarText = LootPool436.formatRarity(rarity > 0.0D ? rarity : 50.0D);
            this.wgtText = LootPool436.formatWeight(weight > 0.0D ? weight : 10.0D);
        }

        ItemStack stack() {
            if (!itemId.equals(cachedId)) {
                cachedId = itemId;
                cachedStack = CustomBookManager443.resolveItemStack(itemId, 1, false);
            }
            return cachedStack;
        }

        int[] parsedCountRange() {
            return LootPool436.parseItemCountRange(countText);
        }

        double parsedRarity() {
            Double v = LootPool436.parseRarityInput(rarText);
            if (v == null || !Double.isFinite(v) || v < 0.0D) return -1.0D;
            return Math.min((double) LootPool436.MAX_CAP_LIMIT, v);
        }

        double parsedWeight() {
            Double v = LootPool436.parseWeightInput(wgtText);
            return v == null || !Double.isFinite(v) || v <= 0.0D ? 0.0D : v;
        }

        LootPool436.PoolEntry toPoolEntry() {
            String cleanId = LootPool436.cleanItemId(itemId);
            if (cleanId.isEmpty() || !cleanId.contains(":")) return null;
            int[] cnt = parsedCountRange();
            double rar = parsedRarity();
            double wgt = parsedWeight();
            if (cnt == null || rar <= 0.0D || wgt <= 0.0D) return null;
            return new LootPool436.PoolEntry(cleanId, rar, wgt, cnt[0], cnt[1]);
        }
    }

    private static final class RowSlot {
        EditBox countBox;
        EditBox rarBox;
        EditBox wgtBox;
        Button delBtn;

        void setVisible(boolean visible) {
            countBox.visible = visible;
            countBox.setEditable(visible);
            rarBox.visible = visible;
            rarBox.setEditable(visible);
            wgtBox.visible = visible;
            wgtBox.setEditable(visible);
            delBtn.visible = visible;
            delBtn.active = visible;
        }

        void clearFocus() {
            countBox.setFocused(false);
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

    public static void onBooksListUpdated() {
        LootPoolEditorScreen440 ed = active;
        if (ed == null) return;
        for (RowData r : ed.rowsData) {
            if (CustomBook443.isBookItemId(r.itemId)) {
                r.cachedId = "";
            }
        }
        ed.previewDrops.clear();
    }

    @Override
    protected void init() {
        active = this;
        LootPoolCard436.ensureClientListenersRegistered();

        panelW = Math.max(Math.min(width - 16, 700), Math.min(width - 8, 370));
        panelH = Math.max(height - 16, Math.min(height - 8, 210));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        rowX = panelX + 10;
        int contentW = panelW - 28;
        int rowRight = rowX + contentW;
        scrollTrackX = rowRight + 6;

        delX = rowRight - 20;
        pctW = 48;
        pctX = delX - 4 - pctW;
        wgtW = 44;
        wgtX = pctX - 6 - wgtW;
        rarW = 46;
        rarX = wgtX - 6 - rarW;
        cntW = 50;
        cntX = rarX - 6 - cntW;
        itemColW = Math.max(76, cntX - 6 - rowX);

        int headRight = panelX + panelW - 10;
        Component closeLbl = Component.translatable("gui.settledlands.loot_pool.editor.close");
        int closeW = Math.max(54, font.width(closeLbl.getString()) + 14);
        closeBtn = Button.builder(closeLbl, b -> tryCloseScreen())
                .bounds(headRight - closeW, panelY + 6, closeW, 16)
                .build();
        addRenderableWidget(closeBtn);

        Component delLbl = Component.translatable("gui.settledlands.loot_pool.editor.delete");
        Component delConfLbl = Component.translatable("gui.settledlands.loot_pool.editor.delete_confirm");
        int delW = Math.max(76, Math.max(font.width(delLbl.getString()),
                font.width(delConfLbl.getString())) + 14);
        deletePoolBtn = Button.builder(delLbl, b -> onDeletePressed())
                .bounds(headRight - closeW - 4 - delW, panelY + 6, delW, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.delete_tip")))
                .build();
        addRenderableWidget(deletePoolBtn);

        int fileRowY = panelY + 26;
        Component newLbl = Component.translatable("gui.settledlands.loot_pool.editor.new_pool");
        int newBtnW = Math.max(58, font.width(newLbl.getString()) + 14);
        int dropBtnW = 18;
        int fileLabelW = Math.max(36, font.width(
                Component.translatable("gui.settledlands.loot_pool.editor.file_lbl").getString()) + 6);
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

        newPoolBtn = Button.builder(newLbl, b -> startNewPool())
                .bounds(headRight - newBtnW, fileRowY, newBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.new_tip")))
                .build();
        addRenderableWidget(newPoolBtn);

        if (sourceNode != null) {
            nodeRowY = panelY + 60;
            String rarLbl = Component.translatable("gui.settledlands.loot_pool.editor.node_rar_lbl").getString();
            String wgtLbl = Component.translatable("gui.settledlands.loot_pool.editor.node_wgt_lbl").getString();
            String rlsLbl = Component.translatable("gui.settledlands.loot_pool.editor.node_rls_lbl").getString();
            String nodePrefix = Component.translatable("gui.settledlands.loot_pool.editor.node_prefix").getString();

            int rarBoxW = panelW >= 500 ? 52 : 40;
            int wgtBoxW = panelW >= 500 ? 56 : 44;
            int rlsBoxW = panelW >= 500 ? 44 : 34;

            int curX = panelX + 10 + font.width(nodePrefix) + 6;
            nodeRarLblX = curX;
            curX += font.width(rarLbl) + 3;
            nodeRarBox = new EditBox(font, curX, nodeRowY, rarBoxW, 16,
                    Component.translatable("gui.settledlands.loot_pool.max_rarity"));
            nodeRarBox.setMaxLength(LootPool436.MAX_FIELD_LEN);
            nodeRarBox.setHint(Component.translatable("gui.settledlands.loot_pool.any"));
            nodeRarBox.setFilter(LootPool436::isRangeInput);
            nodeRarBox.setValue(LootPool436.rawRarity(sourceNode));
            nodeRarBox.setResponder(v -> onNodeLimitChanged());
            addRenderableWidget(nodeRarBox);

            curX += rarBoxW + 8;
            nodeWgtLblX = curX;
            curX += font.width(wgtLbl) + 3;
            nodeWgtBox = new EditBox(font, curX, nodeRowY, wgtBoxW, 16,
                    Component.translatable("gui.settledlands.loot_pool.max_weight"));
            nodeWgtBox.setMaxLength(LootPool436.MAX_FIELD_LEN);
            nodeWgtBox.setHint(Component.translatable("gui.settledlands.loot_pool.any"));
            nodeWgtBox.setFilter(LootPool436::isRangeInput);
            nodeWgtBox.setValue(LootPool436.rawWeight(sourceNode));
            nodeWgtBox.setResponder(v -> onNodeLimitChanged());
            addRenderableWidget(nodeWgtBox);

            curX += wgtBoxW + 8;
            nodeRollsLblX = curX;
            curX += font.width(rlsLbl) + 3;
            nodeRollsBox = new EditBox(font, curX, nodeRowY, rlsBoxW, 16,
                    Component.translatable("gui.settledlands.loot_pool.rolls"));
            nodeRollsBox.setMaxLength(LootPool436.MAX_FIELD_LEN);
            nodeRollsBox.setHint(Component.literal("3"));
            nodeRollsBox.setFilter(LootPool436::isRangeInput);
            nodeRollsBox.setValue(LootPool436.rawRolls(sourceNode));
            nodeRollsBox.setResponder(v -> onNodeLimitChanged());
            addRenderableWidget(nodeRollsBox);

            listTop = panelY + 95;
        } else {
            nodeRarBox = null;
            nodeWgtBox = null;
            nodeRollsBox = null;
            listTop = panelY + 72;
        }

        btnY = panelY + panelH - 24;
        stripY = btnY - 22;
        fullListBottom = Math.max(listTop + ROW_H * 2, btnY - 6);
        compactListBottom = Math.max(listTop + ROW_H, stripY - 4);
        fullVisibleRows = Math.max(2, (fullListBottom - listTop) / ROW_H);
        compactVisibleRows = Math.max(1, (compactListBottom - listTop) / ROW_H);

        slots.clear();
        for (int s = 0; s < fullVisibleRows; s++) {
            final int slotIdx = s;
            int ry = listTop + s * ROW_H;
            int wy = ry + 3;
            RowSlot slot = new RowSlot();

            slot.countBox = new EditBox(font, cntX, wy, cntW, 18, Component.empty());
            slot.countBox.setMaxLength(7);
            slot.countBox.setHint(Component.literal("1-4"));
            slot.countBox.setFilter(LootPoolEditorScreen440::isCountRangeInput);
            slot.countBox.setResponder(val -> onRowFieldChanged(slotIdx, 0, val));
            addRenderableWidget(slot.countBox);

            slot.rarBox = new EditBox(font, rarX, wy, rarW, 18, Component.empty());
            slot.rarBox.setMaxLength(9);
            slot.rarBox.setHint(Component.literal("50"));
            slot.rarBox.setFilter(LootPoolEditorScreen440::isDecimalInput);
            slot.rarBox.setResponder(val -> onRowFieldChanged(slotIdx, 1, val));
            addRenderableWidget(slot.rarBox);

            slot.wgtBox = new EditBox(font, wgtX, wy, wgtW, 18, Component.empty());
            slot.wgtBox.setMaxLength(9);
            slot.wgtBox.setHint(Component.literal("10"));
            slot.wgtBox.setFilter(LootPoolEditorScreen440::isDecimalInput);
            slot.wgtBox.setResponder(val -> onRowFieldChanged(slotIdx, 2, val));
            addRenderableWidget(slot.wgtBox);

            slot.delBtn = Button.builder(Component.literal("x"), b -> deleteRowAtSlot(slotIdx))
                    .bounds(delX, wy, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.remove_row")))
                    .build();
            addRenderableWidget(slot.delBtn);

            slots.add(slot);
        }

        int availBtnW = panelW - 20;
        Component addLbl = Component.translatable("gui.settledlands.loot_pool.editor.add_item");
        Component bookLbl = Component.translatable("gui.settledlands.loot_pool.editor.add_book");
        Component testLbl = Component.translatable("gui.settledlands.loot_pool.editor.test");
        Component saveLbl = Component.translatable("gui.settledlands.loot_pool.editor.save");
        Component saveCloseLbl = Component.translatable("gui.settledlands.loot_pool.editor.save_close");

        int addW = Math.max(58, font.width(addLbl.getString()) + 12);
        int bookW = Math.max(52, font.width(bookLbl.getString()) + 12);
        int testW = Math.max(46, font.width(testLbl.getString()) + 12);
        int saveW = Math.max(54, font.width(saveLbl.getString()) + 12);
        int saveCloseW = Math.max(70, font.width(saveCloseLbl.getString()) + 12);

        if (addW + 3 + bookW + 3 + testW + 6 + saveW + 4 + saveCloseW > availBtnW) {
            addLbl = Component.translatable("gui.settledlands.loot_pool.editor.add_item_short");
            testLbl = Component.translatable("gui.settledlands.loot_pool.editor.test_short");
            addW = Math.max(52, font.width(addLbl.getString()) + 10);
            bookW = Math.max(46, font.width(bookLbl.getString()) + 10);
            testW = Math.max(38, font.width(testLbl.getString()) + 10);
        }
        if (addW + 3 + bookW + 3 + testW + 6 + saveW + 4 + saveCloseW > availBtnW) {
            saveCloseLbl = Component.translatable("gui.settledlands.loot_pool.editor.save_close_short");
            saveCloseW = Math.max(64, font.width(saveCloseLbl.getString()) + 10);
        }

        addItemBtn = Button.builder(addLbl, b -> openItemPicker(-1))
                .bounds(panelX + 10, btnY, addW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.add_item_tip")))
                .build();
        addRenderableWidget(addItemBtn);

        addBookBtn = Button.builder(bookLbl, b -> openBookEditor(-1, ""))
                .bounds(panelX + 13 + addW, btnY, bookW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.add_book_tip")))
                .build();
        addRenderableWidget(addBookBtn);

        testRollBtn = Button.builder(testLbl, b -> runTestRoll())
                .bounds(panelX + 16 + addW + bookW, btnY, testW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.test_tip")))
                .build();
        addRenderableWidget(testRollBtn);

        saveCloseBtn = Button.builder(saveCloseLbl, b -> triggerSave(true))
                .bounds(headRight - saveCloseW, btnY, saveCloseW, 18)
                .build();
        addRenderableWidget(saveCloseBtn);

        saveBtn = Button.builder(saveLbl, b -> triggerSave(false))
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

    private boolean hasBottomStrip() {
        return !statusText.isEmpty() || !previewDrops.isEmpty();
    }

    private int activeVisibleRows() {
        if (fullVisibleRows <= 0) return 2;
        return hasBottomStrip() ? compactVisibleRows : fullVisibleRows;
    }

    private int currentListBottom() {
        return hasBottomStrip() ? compactListBottom : fullListBottom;
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
            refreshRowWidgets(false);
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
            refreshRowWidgets(false);
            updateActionButtons();
            return;
        }

        // "OK" or "NEW"
        rowsData.clear();
        previewDrops.clear();
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

    private void onNodeLimitChanged() {
        if (suppressSync || sourceNode == null) return;
        if (!nodeLimitsUndoRecorded) {
            nodeLimitsUndoRecorded = true;
            if (parentScreen != null) {
                parentScreen.settledlands$remember();
            }
        }
        String r = nodeRarBox == null ? LootPool436.rawRarity(sourceNode) : nodeRarBox.getValue();
        String w = nodeWgtBox == null ? LootPool436.rawWeight(sourceNode) : nodeWgtBox.getValue();
        String rl = nodeRollsBox == null ? LootPool436.rawRolls(sourceNode) : nodeRollsBox.getValue();
        sourceNode.action = LootPool436.formatAction(LootPool436.poolFileOf(sourceNode), r, w, rl);
        if (parentScreen != null) {
            parentScreen.settledlands$changed();
        }
        if (!previewDrops.isEmpty()) {
            previewDrops.clear();
            refreshRowWidgets(false);
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
        boolean hadStrip = hasBottomStrip();
        if (hadStrip) {
            statusText = "";
            previewDrops.clear();
            refreshRowWidgets(false);
        }
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
        if (addBookBtn != null) addBookBtn.active = !loading;
        if (testRollBtn != null) testRollBtn.active = !loading && !rowsData.isEmpty();
    }

    private void refreshRowWidgets(boolean resetFocus) {
        int visRows = activeVisibleRows();
        int maxScr = maxRowScroll();
        if (rowScroll > maxScr) rowScroll = maxScr;
        if (rowScroll < 0) rowScroll = 0;

        suppressSync = true;
        try {
            for (int s = 0; s < slots.size(); s++) {
                RowSlot slot = slots.get(s);
                if (resetFocus) slot.clearFocus();
                int idx = rowScroll + s;
                if (s < visRows && idx >= 0 && idx < rowsData.size()) {
                    RowData data = rowsData.get(idx);
                    slot.countBox.setValue(data.countText);
                    slot.rarBox.setValue(data.rarText);
                    slot.wgtBox.setValue(data.wgtText);
                    slot.setVisible(true);
                } else {
                    slot.countBox.setValue("");
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
        if (fieldIdx == 0) row.countText = v;
        else if (fieldIdx == 1) row.rarText = v;
        else if (fieldIdx == 2) row.wgtText = v;
        markDirty();
    }

    private void deleteRowAtSlot(int slotIdx) {
        int idx = rowScroll + slotIdx;
        if (idx < 0 || idx >= rowsData.size()) return;
        rowsData.remove(idx);
        previewDrops.clear();
        statusText = "";
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
        rowsData.add(new RowData(clean, cnt, cnt, 50.0D, 10.0D));
        rowScroll = Math.max(0, rowsData.size() - activeVisibleRows());
        previewDrops.clear();
        statusText = "";
        markDirty();
    }

    public void replaceItemAt(int rowIdx, String itemId) {
        String clean = LootPool436.cleanItemId(itemId);
        if (clean.isEmpty() || rowIdx < 0 || rowIdx >= rowsData.size()) return;
        RowData row = rowsData.get(rowIdx);
        row.itemId = clean;
        row.cachedId = "";
        previewDrops.clear();
        statusText = "";
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

    public void openBookEditor(int replaceRowIdx, String bookId) {
        poolPopupOpen = false;
        Minecraft.getInstance().setScreen(
                new LootPoolBookEditorScreen443(this, this, replaceRowIdx, bookId, null));
    }

    private void startNewPool() {
        poolPopupOpen = false;
        rowsData.clear();
        previewDrops.clear();
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
            if (nodeRarBox != null) nodeRarBox.setFocused(false);
            if (nodeWgtBox != null) nodeWgtBox.setFocused(false);
            if (nodeRollsBox != null) nodeRollsBox.setFocused(false);
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
            refreshRowWidgets(false);
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
            refreshRowWidgets(false);
            if (fileNameBox != null) setFocused(fileNameBox);
            return;
        }

        List<LootPool436.PoolEntry> validEntries = new ArrayList<>(rowsData.size());
        int visRows = activeVisibleRows();
        for (int i = 0; i < rowsData.size(); i++) {
            RowData row = rowsData.get(i);
            LootPool436.PoolEntry entry = row.toPoolEntry();
            if (entry == null) {
                statusColor = 0xFFFF7070;
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_row",
                        Integer.valueOf(i + 1)).getString();
                if (i < rowScroll || i >= rowScroll + visRows) {
                    rowScroll = Math.max(0, Math.min(maxRowScroll(), i));
                    refreshRowWidgets(true);
                } else {
                    refreshRowWidgets(false);
                }
                return;
            }
            // Normalize reversed/1-1 range formatting cleanly on save.
            row.countText = LootPool436.formatCountRange(entry.minCount, entry.maxCount);
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
            refreshRowWidgets(false);
        }
        updateActionButtons();
    }

    private void runTestRoll() {
        previewDrops.clear();
        List<LootPool436.PoolEntry> entries = new ArrayList<>(rowsData.size());
        for (RowData r : rowsData) {
            LootPool436.PoolEntry e = r.toPoolEntry();
            if (e != null) entries.add(e);
        }
        if (entries.isEmpty()) {
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.preview_none").getString();
            refreshRowWidgets(false);
            return;
        }
        LootPool436.Parsed spec = currentNodeSpec();
        List<LootPool436.Drop> rolled = LootPool436.rollDrops(entries, spec, previewRandom);
        if (rolled.isEmpty()) {
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.preview_filtered").getString();
            refreshRowWidgets(false);
            return;
        }
        for (LootPool436.Drop d : rolled) {
            ItemStack st = CustomBookManager443.resolveItemStack(d.itemId, 1, false);
            if (!st.isEmpty()) {
                previewDrops.add(new PreviewDrop(st, d.count));
            }
        }
        statusText = "";
        refreshRowWidgets(false);
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
                refreshRowWidgets(false);
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
        return Math.max(0, rowsData.size() - activeVisibleRows());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Omit vanilla Screen#renderBlurredBackground so the editor background stays crisp without blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x990A0D13);

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
                panelX + 10, panelY + 31, 0xFFB0BACB, false);

        LootPool436.Parsed nodeSpec = currentNodeSpec();
        List<LootPool436.PoolEntry> rowEntries = new ArrayList<>(rowsData.size());
        for (RowData r : rowsData) {
            rowEntries.add(r.toPoolEntry());
        }
        int blockedCount = sourceNode == null ? 0 : LootPool436.countBlockedBySpec(rowEntries, nodeSpec);
        double[] baseChances = LootPool436.computeEffectiveChancesPct(rowEntries, null);
        double[] effChances = sourceNode == null
                ? baseChances
                : LootPool436.computeEffectiveChancesPct(rowEntries, nodeSpec);

        // Summary line + optional node inline controls row
        renderSummaryLine(graphics, blockedCount, rowsData.size());
        if (sourceNode != null) {
            renderNodeLimitsBar(graphics);
        }

        // Column headers
        int hdrY = listTop - 13;
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_item"),
                rowX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_count"),
                cntX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_rarity"),
                rarX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_weight"),
                wgtX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_chance"),
                pctX, hdrY, 0xFF9AA5B8, false);

        int listBottom = currentListBottom();
        int visRows = activeVisibleRows();

        // Table frame reaching all the way to the bottom strip / action buttons
        int tableRight = rowX + (panelW - 28);
        graphics.fill(rowX - 2, listTop - 2, tableRight + 2, listBottom + 2, 0x990D1118);
        graphics.renderOutline(rowX - 2, listTop - 2, (tableRight - rowX) + 4, (listBottom - listTop) + 4,
                0xFF272F40);

        ItemStack hoveredRowStack = ItemStack.EMPTY;
        Component extraTooltip = null;

        if (!poolPopupOpen && blockedCount > 0 && mouseY >= panelY + 45 && mouseY < panelY + 59
                && mouseX >= panelX + panelW / 2 && mouseX <= panelX + panelW - 10) {
            extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.blocked_banner_tip");
        } else if (!poolPopupOpen && sourceNode != null
                && mouseY >= nodeRowY && mouseY < nodeRowY + 16) {
            if (mouseX >= nodeRarLblX && mouseX < nodeWgtLblX - 4) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_node_rar");
            } else if (mouseX >= nodeWgtLblX && mouseX < nodeRollsLblX - 4) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_node_wgt");
            } else if (mouseX >= nodeRollsLblX && mouseX <= nodeRollsLblX + 90) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_node_rls");
            }
        } else if (!poolPopupOpen && mouseY >= hdrY - 2 && mouseY < listTop - 2) {
            if (mouseX >= cntX && mouseX < rarX) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_count");
            } else if (mouseX >= rarX && mouseX < wgtX) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_rarity");
            } else if (mouseX >= wgtX && mouseX < pctX) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_weight");
            } else if (mouseX >= pctX && mouseX < delX) {
                extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.tip_chance");
            }
        }

        if (rowsData.isEmpty()) {
            Component emptyKey = Component.translatable(loading
                    ? "gui.settledlands.loot_pool.editor.loading"
                    : "gui.settledlands.loot_pool.editor.empty_rows");
            graphics.drawCenteredString(font, emptyKey, rowX + (tableRight - rowX) / 2,
                    (listTop + listBottom) / 2 - 4, 0xFF8390A6);
        } else {
            for (int s = 0; s < visRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                RowData data = rowsData.get(idx);
                int ry = listTop + s * ROW_H;

                int rowBg = (idx % 2 == 0) ? 0x5519202B : 0x33141923;
                graphics.fill(rowX, ry, tableRight, ry + ROW_H - 1, rowBg);

                // Item picker cell (plus inline book-edit button for settledlands:book/<id>)
                int itemBtnY = ry + 2;
                int itemBtnH = 20;
                boolean isCustomBook = CustomBook443.isBookItemId(data.itemId);
                int editBtnW = isCustomBook && itemColW >= 60 ? 18 : 0;
                int editBtnX = rowX + itemColW - editBtnW;
                boolean overEditBtn = editBtnW > 0 && !poolPopupOpen
                        && mouseX >= editBtnX && mouseX < rowX + itemColW
                        && mouseY >= itemBtnY && mouseY < itemBtnY + itemBtnH;
                boolean overItem = !poolPopupOpen && !overEditBtn
                        && mouseX >= rowX && mouseX < rowX + itemColW - editBtnW
                        && mouseY >= itemBtnY && mouseY < itemBtnY + itemBtnH;
                boolean validItem = !data.itemId.isEmpty();
                int itemBg = overItem ? 0xFF283142 : 0xFF171C25;
                int itemFrame = !validItem ? 0xFFCC4444 : (overItem ? accent : 0xFF343E52);
                graphics.fill(rowX, itemBtnY, rowX + itemColW, itemBtnY + itemBtnH, itemBg);
                graphics.renderOutline(rowX, itemBtnY, itemColW, itemBtnH, itemFrame);

                ItemStack stack = data.stack();
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, rowX + 2, itemBtnY + 2);
                    int textX = rowX + 21;
                    int avail = itemColW - 24 - editBtnW;
                    if (isCustomBook) {
                        graphics.enableScissor(textX, itemBtnY, textX + avail, itemBtnY + itemBtnH);
                        graphics.drawString(font, stack.getHoverName(), textX, itemBtnY + 6,
                                0xFFEAEFF8, false);
                        graphics.disableScissor();
                    } else {
                        String displayName = stack.getHoverName().getString();
                        graphics.drawString(font, clip(font, displayName, avail), textX, itemBtnY + 6,
                                0xFFEAEFF8, false);
                    }
                    if (overItem) {
                        hoveredRowStack = stack;
                    }
                } else {
                    String rawLabel = data.itemId.isEmpty() ? "<...>" : data.itemId;
                    graphics.drawString(font, clip(font, rawLabel, itemColW - 8 - editBtnW), rowX + 4, itemBtnY + 6,
                            0xFFFF9888, false);
                    if (overItem) {
                        extraTooltip = Component.translatable(
                                "gui.settledlands.loot_pool.editor.unknown_item", rawLabel);
                    }
                }
                if (editBtnW > 0) {
                    int eBg = overEditBtn ? 0xFF36455E : 0xFF212836;
                    graphics.fill(editBtnX, itemBtnY + 1, rowX + itemColW - 1, itemBtnY + itemBtnH - 1, eBg);
                    graphics.renderOutline(editBtnX, itemBtnY + 1, editBtnW - 1, itemBtnH - 2,
                            overEditBtn ? accent : 0xFF42506A);
                    graphics.drawCenteredString(font, "E", editBtnX + editBtnW / 2, itemBtnY + 6,
                            overEditBtn ? 0xFFFFFFFF : 0xFFF5B841);
                    if (overEditBtn) {
                        extraTooltip = Component.translatable("gui.settledlands.loot_pool.editor.edit_book_tip");
                    }
                }

                // Invalid cell highlights
                if (data.parsedCountRange() == null) {
                    graphics.renderOutline(cntX - 1, ry + 2, cntW + 2, 20, 0xFFDD4444);
                }
                if (data.parsedRarity() <= 0.0D) {
                    graphics.renderOutline(rarX - 1, ry + 2, rarW + 2, 20, 0xFFDD4444);
                }
                if (data.parsedWeight() <= 0.0D) {
                    graphics.renderOutline(wgtX - 1, ry + 2, wgtW + 2, 20, 0xFFDD4444);
                }

                // Live effective drop percentage (reflecting rarity + node rarity/crate-weight caps)
                double rarVal = data.parsedRarity();
                LootPool436.PoolEntry entry = rowEntries.get(idx);
                String pctStr;
                int pctColor;
                Component cellTip = null;
                if (rarVal < 0.0D) {
                    pctStr = "--";
                    pctColor = 0xFFDD6666;
                } else if (rarVal == 0.0D || entry == null) {
                    pctStr = "0%";
                    pctColor = 0xFFDD6666;
                } else {
                    double base = idx < baseChances.length ? baseChances[idx] : 0.0D;
                    double eff = idx < effChances.length ? effChances[idx] : base;
                    int reason = sourceNode == null ? 0 : LootPool436.blockedReason(entry, nodeSpec);
                    if (reason != 0 || eff <= 0.0001D) {
                        pctStr = "0% !";
                        pctColor = 0xFFFF6B57;
                        if (reason == 2) {
                            LootPool436.Range wR = LootPool436.parseRange(
                                    nodeSpec.rawWeight, LootPool436.MAX_CAP_LIMIT);
                            cellTip = Component.translatable(
                                    "gui.settledlands.loot_pool.editor.blocked_weight_tip",
                                    LootPool436.formatWeight(entry.weight),
                                    Integer.valueOf(wR.max));
                        } else if (reason == 1) {
                            LootPool436.Range rR = LootPool436.parseRange(
                                    nodeSpec.rawRarity, LootPool436.MAX_CAP_LIMIT);
                            cellTip = Component.translatable(
                                    "gui.settledlands.loot_pool.editor.blocked_rarity_tip",
                                    LootPool436.formatRarity(entry.rarity),
                                    Integer.valueOf(rR.max));
                        } else {
                            cellTip = Component.translatable("gui.settledlands.loot_pool.editor.filtered_tip");
                        }
                    } else if (sourceNode != null && Math.abs(eff - base) >= 0.1D) {
                        pctStr = formatPct(eff) + "*";
                        pctColor = 0xFFFFCC55;
                        cellTip = Component.translatable(
                                "gui.settledlands.loot_pool.editor.skewed_chance_tip",
                                formatPct(eff), formatPct(base));
                    } else {
                        pctStr = formatPct(eff);
                        pctColor = 0xFF93E2A2;
                    }
                }
                graphics.drawString(font, clip(font, pctStr, pctW), pctX, ry + 8, pctColor, false);
                if (cellTip != null && !poolPopupOpen
                        && mouseX >= pctX && mouseX < delX - 2 && mouseY >= ry && mouseY < ry + ROW_H) {
                    extraTooltip = cellTip;
                }
            }
        }

        // Scrollbar for rows
        int maxScr = maxRowScroll();
        if (maxScr > 0) {
            int trackH = listBottom - listTop;
            graphics.fill(scrollTrackX, listTop, scrollTrackX + 5, listTop + trackH, 0xFF10141C);
            int thumbH = Math.max(18, trackH * visRows / Math.max(1, rowsData.size()));
            int thumbY = listTop + (trackH - thumbH) * rowScroll / Math.max(1, maxScr);
            graphics.fill(scrollTrackX, thumbY, scrollTrackX + 5, thumbY + thumbH, 0xFF62718C);
        }

        // Optional status / roll preview strip (hidden during normal editing so table goes to buttons)
        ItemStack previewHover = hasBottomStrip()
                ? renderBottomStrip(graphics, mouseX, mouseY, blockedCount)
                : ItemStack.EMPTY;

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

    private static String formatPct(double pct) {
        if (!Double.isFinite(pct) || pct <= 0.0001D) return "0%";
        if (pct >= 99.95D) return "100%";
        return pct >= 10.0D
                ? String.format(Locale.ROOT, "%.1f%%", pct)
                : String.format(Locale.ROOT, "%.2f%%", pct);
    }

    private void renderSummaryLine(GuiGraphics graphics, int blockedCount, int totalCount) {
        int y = panelY + 47;
        double totalR = 0.0D;
        for (RowData r : rowsData) {
            double rr = r.parsedRarity();
            if (rr > 0.0D) totalR += rr;
        }
        String baseSummary = Component.translatable(
                "gui.settledlands.loot_pool.editor.summary",
                Integer.valueOf(totalCount),
                LootPool436.formatRarity(totalR)).getString();
        if (blockedCount > 0) {
            String warn = Component.translatable(
                    "gui.settledlands.loot_pool.editor.blocked_banner",
                    Integer.valueOf(blockedCount), Integer.valueOf(totalCount)).getString();
            int warnW = font.width(warn);
            int rightX = panelX + panelW - 10;
            int warnX = Math.max(panelX + panelW / 2, rightX - warnW);
            graphics.drawString(font, clip(font, baseSummary, Math.max(40, warnX - panelX - 16)),
                    panelX + 10, y, 0xFF8C98AD, false);
            graphics.drawString(font, clip(font, warn, rightX - warnX),
                    warnX, y, 0xFFFF9E44, false);
        } else {
            graphics.drawString(font, clip(font, baseSummary, panelW - 20), panelX + 10, y, 0xFF8C98AD, false);
        }
    }

    private void renderNodeLimitsBar(GuiGraphics graphics) {
        int labelY = nodeRowY + 4;
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.node_prefix"),
                panelX + 10, labelY, 0xFFF5B841, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.node_rar_lbl"),
                nodeRarLblX, labelY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.node_wgt_lbl"),
                nodeWgtLblX, labelY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.node_rls_lbl"),
                nodeRollsLblX, labelY, 0xFF9AA5B8, false);
    }

    private ItemStack renderBottomStrip(GuiGraphics graphics, int mouseX, int mouseY, int blockedCount) {
        ItemStack hovered = ItemStack.EMPTY;
        if (!previewDrops.isEmpty() && statusText.isEmpty()) {
            String prefix = Component.translatable("gui.settledlands.loot_pool.editor.preview_lbl").getString();
            graphics.drawString(font, prefix, panelX + 10, stripY + 4, 0xFFF5B841, false);
            int x = panelX + 14 + font.width(prefix);
            int maxXLimit = panelX + panelW - 28;
            for (PreviewDrop d : previewDrops) {
                if (x + 22 > maxXLimit) break;
                boolean over = !poolPopupOpen && mouseX >= x && mouseX < x + 18
                        && mouseY >= stripY && mouseY < stripY + 18;
                graphics.fill(x, stripY, x + 18, stripY + 18, over ? 0xFF2B3548 : 0xFF191F2A);
                graphics.renderOutline(x, stripY, 18, 18,
                        over ? (0xFF000000 | LootPool436.ACCENT_COLOR) : 0xFF333D50);
                graphics.renderItem(d.stack, x + 1, stripY + 1);
                if (d.count > 1) {
                    String cnt = Integer.toString(d.count);
                    int cw = font.width(cnt);
                    graphics.drawString(font, cnt, x + 17 - cw, stripY + 9, 0xFFFFFFFF, true);
                }
                if (over) hovered = d.stack;
                x += 22;
            }
            if (blockedCount > 0 && x + 60 < maxXLimit) {
                String warn = Component.translatable(
                        "gui.settledlands.loot_pool.editor.preview_blocked_inline",
                        Integer.valueOf(blockedCount)).getString();
                graphics.drawString(font, clip(font, warn, maxXLimit - x - 4),
                        x + 4, stripY + 4, 0xFFFF9E44, false);
            }
            int closeX = panelX + panelW - 24;
            boolean overClose = !poolPopupOpen && mouseX >= closeX && mouseX <= closeX + 14
                    && mouseY >= stripY + 2 && mouseY <= stripY + 16;
            graphics.fill(closeX, stripY + 2, closeX + 14, stripY + 16, overClose ? 0xFF354055 : 0xFF1D2432);
            graphics.drawString(font, "x", closeX + 4, stripY + 5, overClose ? 0xFFFFFFFF : 0xFF9AA5B8, false);
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
            if (hasBottomStrip() && mouseX >= panelX + 10 && mouseX <= panelX + panelW - 10
                    && mouseY >= stripY && mouseY <= stripY + 18) {
                previewDrops.clear();
                statusText = "";
                refreshRowWidgets(false);
                return true;
            }

            int listBottom = currentListBottom();
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
            int visRows = activeVisibleRows();
            for (int s = 0; s < visRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                int ry = listTop + s * ROW_H + 2;
                if (mouseX >= rowX && mouseX < rowX + itemColW && mouseY >= ry && mouseY < ry + 20) {
                    RowData row = rowsData.get(idx);
                    boolean isBook = CustomBook443.isBookItemId(row.itemId);
                    int editBtnW = isBook && itemColW >= 60 ? 18 : 0;
                    playClick();
                    if (editBtnW > 0 && mouseX >= rowX + itemColW - editBtnW) {
                        openBookEditor(idx, CustomBook443.bookIdFromItemId(row.itemId));
                    } else {
                        openItemPicker(idx);
                    }
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
            refreshRowWidgets(false);
            return true;
        }
        pendingSwitchPool = "";
        poolPopupOpen = false;
        loading = true;
        statusColor = 0xFF9AA5B8;
        statusText = Component.translatable("gui.settledlands.loot_pool.editor.loading").getString();
        ChestLootNet436.sendPoolQuery(chosen);
        refreshRowWidgets(false);
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
            int listBottom = currentListBottom();
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
        int trackH = currentListBottom() - listTop;
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

    private static boolean isCountRangeInput(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.length() > 7) return false;
        boolean seenDash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') continue;
            if (c == '-' && !seenDash) {
                seenDash = true;
                continue;
            }
            return false;
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
