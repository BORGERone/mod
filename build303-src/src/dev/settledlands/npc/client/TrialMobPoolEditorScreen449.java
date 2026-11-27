package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialMobPool449;
import dev.settledlands.trial.TrialMobPoolNet449;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
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
 * Build 449: full in-game editor window for {@code config/settledlands/mob_pools/*.txt}.
 * Mirrors {@link LootPoolEditorScreen440} with live 3D mob thumbnails, weights, effective spawn
 * percentages, test-wave previews, and a searchable modpack catalog with entity previews.
 */
public final class TrialMobPoolEditorScreen449 extends Screen {

    private static final int ROW_H = 26;
    private static final int MAX_VISIBLE_POPUP = 7;
    private static final int POPUP_ROW_H = 15;

    static volatile TrialMobPoolEditorScreen449 active;

    private final NpcDialogueGraphScreen parentScreen;
    private final NpcDialogueGraph.Node sourceNode;
    private final String requestedPool;

    private final List<RowData> rowsData = new ArrayList<>();
    private final List<RowSlot> slots = new ArrayList<>();
    private final List<TrialMobPool449.WavePick> previewWave = new ArrayList<>();
    private final Random previewRandom = new Random();

    private EditBox fileNameBox;
    private Button poolDropBtn;
    private Button newPoolBtn;
    private Button deletePoolBtn;
    private Button addCatalogBtn;
    private Button testWaveBtn;
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
    private int fullVisibleRows;
    private int compactVisibleRows;
    private String statusText = "";
    private int statusColor = 0xFF9AA5B8;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowX;
    private int mobColW;
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
        String entityId;
        String weightText;

        RowData(String entityId, int weight) {
            this.entityId = entityId == null ? "minecraft:zombie" : entityId;
            this.weightText = Integer.toString(TrialMobPool449.clampWeight(weight));
        }

        int parsedWeight() {
            Integer v = TrialMobPool449.parseWeightInput(weightText);
            return v == null ? -1 : v;
        }

        TrialConfig446.SpawnEntry toEntry() {
            String norm = TrialConfig446.normalizeId(entityId);
            int w = parsedWeight();
            if (norm == null || !norm.contains(":") || w <= 0) return null;
            return new TrialConfig446.SpawnEntry(norm, w);
        }
    }

    private static final class RowSlot {
        EditBox weightBox;
        Button delBtn;

        void setVisible(boolean visible, boolean canDelete) {
            weightBox.visible = visible;
            weightBox.setEditable(visible);
            delBtn.visible = visible;
            delBtn.active = visible && canDelete;
        }

        void clearFocus() {
            weightBox.setFocused(false);
        }
    }

    public TrialMobPoolEditorScreen449(NpcDialogueGraphScreen parentScreen,
            NpcDialogueGraph.Node sourceNode, String requestedPool) {
        super(Component.translatable("gui.settledlands.trial.mob_pool.editor_title"));
        this.parentScreen = parentScreen;
        this.sourceNode = sourceNode;
        this.requestedPool = TrialMobPool449.sanitizeFileName(requestedPool);
    }

    public static void handleServerData(String rawPayload) {
        TrialMobPoolEditorScreen449 ed = active;
        if (ed == null || rawPayload == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != ed) return;
        ed.applyServerData(rawPayload);
    }

    public static void onPoolsListUpdated() {
        TrialMobPoolEditorScreen449 ed = active;
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

        panelW = Math.max(Math.min(width - 16, 680), Math.min(width - 8, 360));
        panelH = Math.max(height - 16, Math.min(height - 8, 220));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        rowX = panelX + 10;
        int contentW = panelW - 28;
        int rowRight = rowX + contentW;
        scrollTrackX = rowRight + 6;

        delX = rowRight - 20;
        pctW = 54;
        pctX = delX - 6 - pctW;
        wgtW = 58;
        wgtX = pctX - 6 - wgtW;
        mobColW = Math.max(110, wgtX - 6 - rowX);

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
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_pool.delete_tip")))
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
                Component.translatable("gui.settledlands.trial.mob_pool.file"));
        fileNameBox.setMaxLength(64);
        fileNameBox.setHint(Component.literal("mob_pool_1.txt"));
        fileNameBox.setFilter(TrialMobPoolEditorScreen449::isValidFileChar);
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
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_pool.switch_tip")))
                .build();
        addRenderableWidget(poolDropBtn);

        newPoolBtn = Button.builder(newLbl, b -> startNewPool())
                .bounds(headRight - newBtnW, fileRowY, newBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_pool.new_tip")))
                .build();
        addRenderableWidget(newPoolBtn);

        listTop = panelY + 76;
        btnY = panelY + panelH - 24;
        stripY = btnY - 24;
        fullListBottom = Math.max(listTop + ROW_H * 2, btnY - 6);
        compactListBottom = Math.max(listTop + ROW_H, stripY - 4);
        fullVisibleRows = Math.max(2, (fullListBottom - listTop) / ROW_H);
        compactVisibleRows = Math.max(1, (compactListBottom - listTop) / ROW_H);

        slots.clear();
        for (int s = 0; s < fullVisibleRows; s++) {
            final int slotIdx = s;
            int ry = listTop + s * ROW_H;
            int wy = ry + 4;
            RowSlot slot = new RowSlot();

            slot.weightBox = new EditBox(font, wgtX, wy, wgtW, 18, Component.empty());
            slot.weightBox.setMaxLength(6);
            slot.weightBox.setHint(Component.literal("10"));
            slot.weightBox.setFilter(TrialModuleClient447.intFilter());
            slot.weightBox.setResponder(val -> onRowWeightChanged(slotIdx, val));
            addRenderableWidget(slot.weightBox);

            slot.delBtn = Button.builder(Component.literal("x"), b -> deleteRowAtSlot(slotIdx))
                    .bounds(delX, wy, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("gui.settledlands.loot_pool.editor.remove_row")))
                    .build();
            addRenderableWidget(slot.delBtn);

            slots.add(slot);
        }

        Component addLbl = Component.translatable("gui.settledlands.trial.mob_pool.add_catalog");
        Component testLbl = Component.translatable("gui.settledlands.trial.mob_pool.test_wave");
        Component saveLbl = Component.translatable("gui.settledlands.loot_pool.editor.save");
        Component saveCloseLbl = Component.translatable("gui.settledlands.loot_pool.editor.save_close");

        int addW = Math.max(92, font.width(addLbl.getString()) + 14);
        int testW = Math.max(70, font.width(testLbl.getString()) + 14);
        int saveW = Math.max(54, font.width(saveLbl.getString()) + 12);
        int saveCloseW = Math.max(72, font.width(saveCloseLbl.getString()) + 12);
        int availBtnW = panelW - 20;
        if (addW + 4 + testW + 8 + saveW + 4 + saveCloseW > availBtnW) {
            saveCloseLbl = Component.translatable("gui.settledlands.loot_pool.editor.save_close_short");
            saveCloseW = Math.max(64, font.width(saveCloseLbl.getString()) + 10);
        }

        addCatalogBtn = Button.builder(addLbl, b -> openMobCatalog(-1))
                .bounds(panelX + 10, btnY, addW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_pool.add_catalog_tip")))
                .build();
        addRenderableWidget(addCatalogBtn);

        testWaveBtn = Button.builder(testLbl, b -> runTestWave())
                .bounds(panelX + 14 + addW, btnY, testW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.trial.mob_pool.test_wave_tip")))
                .build();
        addRenderableWidget(testWaveBtn);

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
            TrialMobPoolNet449.sendPoolQuery(requestedPool);
        }

        refreshRowWidgets(false);
        updateActionButtons();
    }

    private boolean hasBottomStrip() {
        return !statusText.isEmpty() || !previewWave.isEmpty();
    }

    private int activeVisibleRows() {
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
            if (sourceNode != null && file.equals(TrialModule447.mobPoolFile(sourceNode))) {
                TrialModule447.writeMobPool(sourceNode, "", collectValidEntries());
                syncHubAndMarkChanged();
            }
            loadedServerFile = "";
            fileExistsOnServer = false;
            rowsData.clear();
            for (TrialConfig446.SpawnEntry e : TrialConfig446.defaultMobs()) {
                rowsData.add(new RowData(e.entityId, e.weight));
            }
            previewWave.clear();
            rowScroll = 0;
            setFileNameInput(TrialMobPool449.suggestNextPoolName(TrialMobPool449.getServerPools()));
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
                TrialModule447.writeMobPool(sourceNode, file, collectValidEntries());
                syncHubAndMarkChanged();
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
        previewWave.clear();
        rowScroll = 0;
        if ("OK".equals(status)) {
            List<TrialConfig446.SpawnEntry> entries = TrialMobPool449.decodeEntriesWire(lines, 2);
            for (TrialConfig446.SpawnEntry e : entries) {
                rowsData.add(new RowData(e.entityId, e.weight));
            }
            if (rowsData.isEmpty()) {
                for (TrialConfig446.SpawnEntry e : TrialConfig446.defaultMobs()) {
                    rowsData.add(new RowData(e.entityId, e.weight));
                }
            }
            loadedServerFile = file;
            fileExistsOnServer = true;
            statusColor = 0xFF9AA5B8;
            statusText = "";
            dirty = false;
        } else {
            loadedServerFile = "";
            fileExistsOnServer = false;
            List<TrialConfig446.SpawnEntry> seed = sourceNode != null
                    ? TrialModule447.readMobs(sourceNode)
                    : TrialConfig446.defaultMobs();
            for (TrialConfig446.SpawnEntry e : seed) {
                rowsData.add(new RowData(e.entityId, e.weight));
            }
            statusColor = 0xFFF5B841;
            statusText = Component.translatable("gui.settledlands.trial.mob_pool.new_hint").getString();
            dirty = true;
        }
        setFileNameInput(file);
        deleteArmAt = 0L;
        refreshRowWidgets(true);
        updateActionButtons();
    }

    private List<TrialConfig446.SpawnEntry> collectValidEntries() {
        List<TrialConfig446.SpawnEntry> out = new ArrayList<>(rowsData.size());
        for (RowData r : rowsData) {
            TrialConfig446.SpawnEntry e = r.toEntry();
            if (e != null) out.add(e);
        }
        return out;
    }

    private void syncHubAndMarkChanged() {
        if (parentScreen == null) return;
        NpcDialogueGraph g = parentScreen.settledlands$graph();
        if (g != null) TrialModule447.syncCardConfig(g);
        TrialModuleClient447.requestInspectorRebuild();
        parentScreen.settledlands$changed();
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
        if (hasBottomStrip()) {
            statusText = "";
            previewWave.clear();
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
        if (addCatalogBtn != null) addCatalogBtn.active = !loading;
        if (testWaveBtn != null) testWaveBtn.active = !loading && !rowsData.isEmpty();
    }

    private void refreshRowWidgets(boolean resetFocus) {
        int visRows = activeVisibleRows();
        int maxScr = maxRowScroll();
        if (rowScroll > maxScr) rowScroll = maxScr;
        if (rowScroll < 0) rowScroll = 0;

        boolean canDel = rowsData.size() > 1;
        suppressSync = true;
        try {
            for (int s = 0; s < slots.size(); s++) {
                RowSlot slot = slots.get(s);
                if (resetFocus) slot.clearFocus();
                int idx = rowScroll + s;
                if (s < visRows && idx >= 0 && idx < rowsData.size()) {
                    slot.weightBox.setValue(rowsData.get(idx).weightText);
                    slot.setVisible(true, canDel);
                } else {
                    slot.weightBox.setValue("");
                    slot.setVisible(false, false);
                }
            }
        } finally {
            suppressSync = false;
        }
    }

    private void onRowWeightChanged(int slotIdx, String value) {
        if (suppressSync) return;
        int idx = rowScroll + slotIdx;
        if (idx < 0 || idx >= rowsData.size()) return;
        rowsData.get(idx).weightText = value == null ? "" : value;
        markDirty();
    }

    private void deleteRowAtSlot(int slotIdx) {
        if (rowsData.size() <= 1) return;
        int idx = rowScroll + slotIdx;
        if (idx < 0 || idx >= rowsData.size()) return;
        rowsData.remove(idx);
        previewWave.clear();
        statusText = "";
        markDirty();
        refreshRowWidgets(true);
        updateActionButtons();
    }

    private void openMobCatalog(int replaceRowIdx) {
        Set<String> present = new HashSet<>();
        for (RowData r : rowsData) {
            if (r.entityId != null && !r.entityId.isEmpty()) present.add(r.entityId);
        }
        poolPopupOpen = false;
        Minecraft.getInstance().setScreen(new TrialMobCatalogScreen449(
                this, replaceRowIdx, present, chosenId -> {
                    if (replaceRowIdx >= 0 && replaceRowIdx < rowsData.size()) {
                        rowsData.get(replaceRowIdx).entityId = chosenId;
                    } else if (rowsData.size() < TrialMobPool449.MAX_ENTRIES) {
                        rowsData.add(new RowData(chosenId, 5));
                        rowScroll = Math.max(0, rowsData.size() - activeVisibleRows());
                    }
                    previewWave.clear();
                    statusText = "";
                    markDirty();
                }));
    }

    private void startNewPool() {
        poolPopupOpen = false;
        rowsData.clear();
        for (TrialConfig446.SpawnEntry e : TrialConfig446.defaultMobs()) {
            rowsData.add(new RowData(e.entityId, e.weight));
        }
        previewWave.clear();
        rowScroll = 0;
        loadedServerFile = "";
        fileExistsOnServer = false;
        setFileNameInput(TrialMobPool449.suggestNextPoolName(TrialMobPool449.getServerPools()));
        dirty = true;
        deleteArmAt = 0L;
        statusColor = 0xFFF5B841;
        statusText = Component.translatable("gui.settledlands.trial.mob_pool.new_hint").getString();
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
            refreshRowWidgets(false);
            updateActionButtons();
            return;
        }
        deleteArmAt = 0L;
        loading = true;
        closeAfterSave = false;
        TrialMobPoolNet449.sendPoolSave("DELETE", loadedServerFile, null);
        updateActionButtons();
    }

    private void triggerSave(boolean closeAfter) {
        String normName = TrialMobPool449.normalizeSaveName(fileNameText);
        if (normName.isEmpty()) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_name").getString();
            refreshRowWidgets(false);
            if (fileNameBox != null) setFocused(fileNameBox);
            return;
        }
        List<TrialConfig446.SpawnEntry> valid = new ArrayList<>(rowsData.size());
        int visRows = activeVisibleRows();
        for (int i = 0; i < rowsData.size(); i++) {
            TrialConfig446.SpawnEntry entry = rowsData.get(i).toEntry();
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
            valid.add(entry);
        }
        if (valid.isEmpty()) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.trial.mob_pool.err_empty").getString();
            refreshRowWidgets(false);
            return;
        }

        closeAfterSave = closeAfter;
        loading = true;
        if (!TrialMobPoolNet449.sendPoolSave("SAVE", normName, valid)) {
            loading = false;
            closeAfterSave = false;
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_too_big").getString();
            refreshRowWidgets(false);
        }
        updateActionButtons();
    }

    private void runTestWave() {
        previewWave.clear();
        List<TrialConfig446.SpawnEntry> valid = collectValidEntries();
        if (valid.isEmpty()) {
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.trial.mob_pool.err_empty").getString();
            refreshRowWidgets(false);
            return;
        }
        int waveCount = 6;
        if (parentScreen != null && parentScreen.settledlands$graph() != null) {
            TrialConfig446 compiled = TrialModule447.compileConfig(parentScreen.settledlands$graph());
            waveCount = Math.max(1, Math.min(32, compiled.totalMobs));
        }
        previewWave.addAll(TrialMobPool449.rollWavePreview(valid, waveCount, previewRandom));
        statusText = "";
        refreshRowWidgets(false);
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

        LootPoolGearButton440.drawGearIcon(graphics, panelX + 9, panelY + 8, true, 0xEE141821,
                TrialMobPool449.ACCENT_COLOR);
        String heading = Component.translatable("gui.settledlands.trial.mob_pool.editor_title").getString();
        if (dirty) heading = heading + " *";
        graphics.drawString(font, heading, panelX + 25, panelY + 10, 0xFFF2F5FA, false);

        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.file_lbl"),
                panelX + 10, panelY + 31, 0xFFB0BACB, false);

        // Summary line
        long totalW = 0L;
        List<TrialConfig446.SpawnEntry> rowEntries = new ArrayList<>(rowsData.size());
        for (RowData r : rowsData) {
            TrialConfig446.SpawnEntry e = r.toEntry();
            rowEntries.add(e);
            if (e != null) totalW += e.weight;
        }
        String sumStr = Component.translatable("gui.settledlands.trial.mob_pool.summary",
                Integer.valueOf(rowsData.size()), Long.valueOf(totalW)).getString();
        graphics.drawString(font, TrialMobPreview449.clip(font, sumStr, panelW - 24),
                panelX + 10, panelY + 48, 0xFF9AA5B8, false);

        double[] chances = TrialMobPool449.computeEffectiveChancesPct(rowEntries);

        int hdrY = listTop - 13;
        graphics.drawString(font, Component.translatable("gui.settledlands.trial.mob_pool.col_mob"),
                rowX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_weight"),
                wgtX, hdrY, 0xFF9AA5B8, false);
        graphics.drawString(font, Component.translatable("gui.settledlands.loot_pool.editor.col_chance"),
                pctX, hdrY, 0xFF9AA5B8, false);

        int listBottom = currentListBottom();
        int visRows = activeVisibleRows();
        int tableRight = rowX + (panelW - 28);
        graphics.fill(rowX - 2, listTop - 2, tableRight + 2, listBottom + 2, 0x990D1118);
        graphics.renderOutline(rowX - 2, listTop - 2, (tableRight - rowX) + 4, (listBottom - listTop) + 4,
                0xFF272F40);

        String hoveredMobId = null;
        if (rowsData.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.settledlands.loot_pool.editor.loading"),
                    rowX + (tableRight - rowX) / 2, (listTop + listBottom) / 2 - 4, 0xFF8390A6);
        } else {
            for (int s = 0; s < visRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                RowData data = rowsData.get(idx);
                int ry = listTop + s * ROW_H;
                int rowBg = (idx % 2 == 0) ? 0x5519202B : 0x33141923;
                graphics.fill(rowX, ry, tableRight, ry + ROW_H - 1, rowBg);

                int cellY = ry + 2;
                int cellH = 22;
                boolean overMob = !poolPopupOpen
                        && mouseX >= rowX && mouseX < rowX + mobColW
                        && mouseY >= cellY && mouseY < cellY + cellH;
                if (overMob) hoveredMobId = data.entityId;

                int cellBg = overMob ? 0xFF283142 : 0xFF171C25;
                int cellFrame = overMob ? accent : 0xFF343E52;
                graphics.fill(rowX, cellY, rowX + mobColW, cellY + cellH, cellBg);
                graphics.renderOutline(rowX, cellY, mobColW, cellH, cellFrame);

                // 20x20 live 3D mob thumbnail on the left of the cell
                int thumbX1 = rowX + 2;
                int thumbY1 = cellY + 1;
                int thumbX2 = thumbX1 + 20;
                int thumbY2 = thumbY1 + 20;
                graphics.fill(thumbX1, thumbY1, thumbX2, thumbY2, 0xFF0E121A);
                float lookX = overMob ? (float) mouseX : (float) ((thumbX1 + thumbX2) / 2 + 12);
                float lookY = overMob ? (float) mouseY : (float) ((thumbY1 + thumbY2) / 2 - 2);
                TrialMobPreview449.renderPreviewBox(
                        graphics, font, data.entityId, thumbX1, thumbY1, thumbX2, thumbY2, lookX, lookY);

                String dispName = TrialMobPreview449.displayName(data.entityId);
                String label = dispName + " (" + data.entityId + ")";
                graphics.drawString(font,
                        TrialMobPreview449.clip(font, label, mobColW - 30),
                        rowX + 26, cellY + 7, 0xFFEAEFF8, false);

                if (data.parsedWeight() <= 0) {
                    graphics.renderOutline(wgtX - 1, ry + 3, wgtW + 2, 20, 0xFFDD4444);
                }

                double pct = idx < chances.length ? chances[idx] : 0.0D;
                String pctStr = formatPct(pct);
                graphics.drawString(font, TrialMobPreview449.clip(font, pctStr, pctW),
                        pctX, ry + 9, pct > 0.0D ? 0xFF93E2A2 : 0xFFDD6666, false);
            }
        }

        int maxScr = maxRowScroll();
        if (maxScr > 0) {
            int trackH = listBottom - listTop;
            graphics.fill(scrollTrackX, listTop, scrollTrackX + 5, listTop + trackH, 0xFF10141C);
            int thumbH = Math.max(18, trackH * visRows / Math.max(1, rowsData.size()));
            int thumbY = listTop + (trackH - thumbH) * rowScroll / Math.max(1, maxScr);
            graphics.fill(scrollTrackX, thumbY, scrollTrackX + 5, thumbY + thumbH, accent);
        }

        if (hasBottomStrip()) {
            renderBottomStrip(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (poolPopupOpen) {
            renderPoolPopup(graphics, mouseX, mouseY);
        } else if (hoveredMobId != null) {
            setTooltipForNextRenderPass(Component.translatable(
                    "gui.settledlands.trial.mob_pool.change_mob_tip",
                    TrialMobPreview449.displayName(hoveredMobId) + " (" + hoveredMobId + ")"));
        }
    }

    private void renderBottomStrip(GuiGraphics graphics) {
        int sx = panelX + 10;
        int sw = panelW - 20;
        graphics.fill(sx, stripY, sx + sw, stripY + 20, 0xFF0E121B);
        graphics.renderOutline(sx, stripY, sw, 20, 0xFF283144);
        if (!previewWave.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < previewWave.size(); i++) {
                TrialMobPool449.WavePick p = previewWave.get(i);
                if (i > 0) sb.append(" · ");
                sb.append(p.count).append('x').append(' ')
                        .append(TrialMobPreview449.displayName(p.entityId));
            }
            String txt = Component.translatable("gui.settledlands.trial.mob_pool.wave_prefix",
                    sb.toString()).getString();
            graphics.drawString(font, TrialMobPreview449.clip(font, txt, sw - 12),
                    sx + 6, stripY + 6, 0xFFEAEFF8, false);
        } else if (!statusText.isEmpty()) {
            graphics.drawString(font, TrialMobPreview449.clip(font, statusText, sw - 12),
                    sx + 6, stripY + 6, statusColor, false);
        }
    }

    private void renderPoolPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> pools = TrialMobPool449.getServerPools();
        int px = fileBoxX;
        int py = fileBoxY + 19;
        int pw = Math.max(fileBoxW + 20, 140);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, pools.size()));
        int ph = visible * POPUP_ROW_H + 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);

        graphics.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF06080D);
        graphics.fill(px, py, px + pw, py + ph, 0xFA141822);
        graphics.renderOutline(px - 1, py - 1, pw + 2, ph + 2, 0xFF000000 | TrialMobPool449.ACCENT_COLOR);

        if (pools.isEmpty()) {
            graphics.drawString(font,
                    TrialMobPreview449.clip(font,
                            Component.translatable("gui.settledlands.trial.mob_pool.no_pools").getString(),
                            pw - 10),
                    px + 6, py + 4, 0xFF9AA5B8, false);
        } else {
            for (int r = 0; r < visible; r++) {
                int idx = poolPopupScroll + r;
                if (idx >= pools.size()) break;
                String name = pools.get(idx);
                int ry = py + 1 + r * POPUP_ROW_H;
                boolean over = mouseX >= px && mouseX <= px + pw && mouseY >= ry && mouseY < ry + POPUP_ROW_H;
                if (over) {
                    graphics.fill(px + 1, ry, px + pw - 1, ry + POPUP_ROW_H, 0xFF283447);
                }
                int col = name.equals(loadedServerFile)
                        ? (0xFF000000 | TrialMobPool449.ACCENT_COLOR)
                        : 0xFFE2E8F2;
                graphics.drawString(font, TrialMobPreview449.clip(font, name, pw - 14),
                        px + 5, ry + 3, col, false);
            }
            if (pools.size() > visible) {
                int barH = Math.max(8, (visible * POPUP_ROW_H) * visible / pools.size());
                int maxPScr = Math.max(1, pools.size() - visible);
                int barY = py + 1 + ((visible * POPUP_ROW_H - barH) * poolPopupScroll / maxPScr);
                graphics.fill(px + pw - 4, barY, px + pw - 1, barY + barH,
                        0xFF000000 | TrialMobPool449.ACCENT_COLOR);
            }
        }

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (poolPopupOpen) {
            if (button == 0 && handlePopupClick(mouseX, mouseY)) return true;
            poolPopupOpen = false;
            return true;
        }
        if (button == 0) {
            if (hasBottomStrip() && mouseX >= panelX + 10 && mouseX <= panelX + panelW - 10
                    && mouseY >= stripY && mouseY <= stripY + 20) {
                previewWave.clear();
                statusText = "";
                refreshRowWidgets(false);
                return true;
            }
            int listBottom = currentListBottom();
            int maxScr = maxRowScroll();
            if (maxScr > 0 && mouseX >= scrollTrackX - 2 && mouseX <= scrollTrackX + 8
                    && mouseY >= listTop && mouseY <= listBottom) {
                draggingScroll = true;
                updateRowScrollFromMouse(mouseY);
                return true;
            }
            int visRows = activeVisibleRows();
            for (int s = 0; s < visRows; s++) {
                int idx = rowScroll + s;
                if (idx >= rowsData.size()) break;
                int ry = listTop + s * ROW_H + 2;
                if (mouseX >= rowX && mouseX < rowX + mobColW && mouseY >= ry && mouseY < ry + 22) {
                    playClick();
                    openMobCatalog(idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePopupClick(double mouseX, double mouseY) {
        List<String> pools = TrialMobPool449.getServerPools();
        int px = fileBoxX;
        int py = fileBoxY + 19;
        int pw = Math.max(fileBoxW + 20, 140);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, pools.size()));
        int ph = visible * POPUP_ROW_H + 2;
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) return false;
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
        TrialMobPoolNet449.sendPoolQuery(chosen);
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
                int maxPScr = Math.max(0, TrialMobPool449.getServerPools().size() - MAX_VISIBLE_POPUP);
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
        if (keyCode == 256) {
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
        double frac = Math.max(0.0D, Math.min(1.0D, (mouseY - listTop) / (double) trackH));
        int next = (int) Math.round(frac * max);
        if (next != rowScroll) {
            rowScroll = next;
            refreshRowWidgets(true);
        }
    }

    private static boolean isValidFileChar(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.length() > 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.' && c != ' ') {
                return false;
            }
        }
        return true;
    }

    private static String formatPct(double pct) {
        if (!Double.isFinite(pct) || pct <= 0.0001D) return "0%";
        if (pct >= 99.95D) return "100%";
        return pct >= 10.0D
                ? String.format(Locale.ROOT, "%.1f%%", pct)
                : String.format(Locale.ROOT, "%.2f%%", pct);
    }

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }
}
