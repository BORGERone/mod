package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * World-wide Variable Catalog & Editor Screen ({@code Build 457}), modeled after
 * {@link LootPoolEditorScreen440}. Lists variables from all loaded NPCs, blocks, doors,
 * chests, trials, {@code NpcPoints}, and catalog definitions, highlights scopes and ownership,
 * marks other objects' local variables as unavailable for binding, and supports create/edit/delete.
 */
public final class VarPoolEditorScreen457 extends Screen {

    private static final int ROW_H = 18;
    private static final int FILTER_ALL = -1;

    private final NpcDialogueGraphScreen parentScreen;
    private final NpcDialogueGraph.Node targetNode;
    private final Consumer<VarCatalogClient457.Entry> onPick;

    private final List<VarCatalogClient457.Entry> filtered = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listH;
    private int formX, formY, formW;
    private int scroll = 0;
    private int seenRevision = -1;

    private int scopeFilter = FILTER_ALL;
    private boolean hideUnavailable = false;
    private String selectedKey = "";
    private VarCatalogClient457.Entry selectedEntry = null;
    private boolean creatingNew = false;

    private int formScope = VarDataNode453.SCOPE_LOCAL;
    private String editingOldName = "";
    private int editingOldScope = VarDataNode453.SCOPE_LOCAL;
    private String editingOldOwnerKey = "";

    private long deleteArmAt = 0L;
    private String statusText = "";
    private int statusColor = 0xFF9AA3B2;

    private EditBox searchBox;
    private EditBox nameBox;
    private EditBox valueBox;
    private EditBox descBox;

    private Button filterAllBtn, filterSharedBtn, filterPersonalBtn, filterLocalBtn, hideOtherBtn;
    private Button scopeLocalBtn, scopePersonalBtn, scopeSharedBtn;
    private Button newBtn, saveBtn, applyNodeBtn, deleteBtn, closeBtn;

    public VarPoolEditorScreen457(
            NpcDialogueGraphScreen parentScreen,
            NpcDialogueGraph.Node targetNode,
            String initialName,
            int initialScope,
            boolean createNew,
            Consumer<VarCatalogClient457.Entry> onPick) {
        super(Component.translatable("gui.settledlands.var_catalog.title"));
        this.parentScreen = parentScreen;
        this.targetNode = targetNode;
        this.onPick = onPick;
        this.creatingNew = createNew;
        this.formScope = VarDataNode453.SCOPE_LOCAL;
        if (initialScope == VarDataNode453.SCOPE_SHARED || initialScope == VarDataNode453.SCOPE_PERSONAL) {
            this.formScope = initialScope;
        }
        String cleanInit = VarScopeStore453.cleanVarName(initialName);
        if (!createNew && !cleanInit.isEmpty()) {
            VarCatalogClient457.Entry match = VarCatalogClient457.findMatching(cleanInit, this.formScope);
            if (match != null) {
                this.selectedEntry = match;
                this.selectedKey = match.key();
            }
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();

        panelW = Math.min(540, this.width - 24);
        panelH = Math.min(316, this.height - 24);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int pad = 10;
        int leftW = Math.max(210, (panelW - pad * 3) * 56 / 100);
        formW = panelW - pad * 3 - leftW;
        listX = panelX + pad;
        formX = listX + leftW + pad;
        formY = panelY + 26;

        // Filter row above list
        int fy = panelY + 26;
        int fw = (leftW - 12) / 4;
        filterAllBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.filter_all"),
                b -> { scopeFilter = FILTER_ALL; scroll = 0; rebuildFiltered(); })
                .bounds(listX, fy, fw, 16).build());
        filterSharedBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.scope_tag.shared"),
                b -> { scopeFilter = VarDataNode453.SCOPE_SHARED; scroll = 0; rebuildFiltered(); })
                .bounds(listX + fw + 4, fy, fw, 16).build());
        filterPersonalBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.scope_tag.personal"),
                b -> { scopeFilter = VarDataNode453.SCOPE_PERSONAL; scroll = 0; rebuildFiltered(); })
                .bounds(listX + (fw + 4) * 2, fy, fw, 16).build());
        filterLocalBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.scope_tag.local"),
                b -> { scopeFilter = VarDataNode453.SCOPE_LOCAL; scroll = 0; rebuildFiltered(); })
                .bounds(listX + (fw + 4) * 3, fy, fw, 16).build());

        // Search box + hideUnavailable toggle
        int sy = fy + 20;
        int hideW = 92;
        searchBox = addRenderableWidget(new EditBox(this.font, listX, sy, leftW - hideW - 4, 16,
                trComp("gui.settledlands.var_catalog.search")));
        searchBox.setHint(trComp("gui.settledlands.var_catalog.search"));
        searchBox.setMaxLength(48);
        searchBox.setResponder(s -> { scroll = 0; rebuildFiltered(); });

        hideOtherBtn = addRenderableWidget(Button.builder(hideButtonLabel(),
                b -> {
                    hideUnavailable = !hideUnavailable;
                    b.setMessage(hideButtonLabel());
                    scroll = 0;
                    rebuildFiltered();
                })
                .bounds(listX + leftW - hideW, sy, hideW, 16).build());

        listY = sy + 20;
        listW = leftW;
        listH = (panelY + panelH - 12) - listY;

        // Right side form
        int ry = formY + 12;
        nameBox = addRenderableWidget(new EditBox(this.font, formX, ry, formW, 16,
                trComp("gui.settledlands.var_catalog.name_label")));
        nameBox.setMaxLength(32);

        ry += 30;
        valueBox = addRenderableWidget(new EditBox(this.font, formX, ry, formW, 16,
                trComp("gui.settledlands.var_catalog.value_label")));
        valueBox.setMaxLength(12);
        valueBox.setFilter(VarPoolEditorScreen457::isIntInput);

        ry += 30;
        int sw = (formW - 8) / 3;
        scopeLocalBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var.scope.local"),
                b -> { formScope = VarDataNode453.SCOPE_LOCAL; updateFormButtons(); })
                .bounds(formX, ry, sw, 16).build());
        scopePersonalBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var.scope.personal"),
                b -> { formScope = VarDataNode453.SCOPE_PERSONAL; updateFormButtons(); })
                .bounds(formX + sw + 4, ry, sw, 16).build());
        scopeSharedBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var.scope.shared"),
                b -> { formScope = VarDataNode453.SCOPE_SHARED; updateFormButtons(); })
                .bounds(formX + (sw + 4) * 2, ry, sw, 16).build());

        ry += 30;
        descBox = addRenderableWidget(new EditBox(this.font, formX, ry, formW, 16,
                trComp("gui.settledlands.var_catalog.desc_label")));
        descBox.setMaxLength(128);

        int by = panelY + panelH - 72;
        int bw2 = (formW - 4) / 2;
        newBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.btn_new"),
                b -> startCreateNew())
                .bounds(formX, by, bw2, 18).build());
        saveBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.btn_save"),
                b -> saveForm())
                .bounds(formX + bw2 + 4, by, bw2, 18).build());

        by += 22;
        applyNodeBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.btn_apply"),
                b -> applyToNodeAndClose())
                .bounds(formX, by, bw2, 18).build());
        deleteBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.btn_delete"),
                b -> handleDeleteClick())
                .bounds(formX + bw2 + 4, by, bw2, 18).build());

        by += 22;
        closeBtn = addRenderableWidget(Button.builder(trComp("gui.settledlands.var_catalog.btn_close"),
                b -> onClose())
                .bounds(formX, by, formW, 18).build());

        int eid = parentScreen != null ? parentScreen.entityId() : VarCatalogClient457.activeEntityId();
        VarCatalogClient457.requestSync(eid);
        seenRevision = VarCatalogClient457.revision();
        rebuildFiltered();
        if (creatingNew) {
            startCreateNew();
        } else if (selectedEntry != null) {
            loadEntryIntoForm(selectedEntry);
        } else if (!filtered.isEmpty()) {
            for (VarCatalogClient457.Entry e : filtered) {
                if (e.available) {
                    loadEntryIntoForm(e);
                    break;
                }
            }
            if (selectedEntry == null) loadEntryIntoForm(filtered.get(0));
        } else {
            startCreateNew();
        }
    }

    private Component hideButtonLabel() {
        return Component.translatable(hideUnavailable
                ? "gui.settledlands.var_catalog.show_all"
                : "gui.settledlands.var_catalog.only_avail");
    }

    private static Component trComp(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static boolean isIntInput(String s) {
        if (s == null || s.isEmpty()) return true;
        if (s.equals("-")) return true;
        int start = s.startsWith("-") ? 1 : 0;
        for (int i = start; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private int parseIntBox(EditBox box, int fallback) {
        if (box == null) return fallback;
        String s = box.getValue().trim();
        if (s.isEmpty() || s.equals("-")) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void rebuildFiltered() {
        filtered.clear();
        String q = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        for (VarCatalogClient457.Entry e : VarCatalogClient457.entries()) {
            if (scopeFilter != FILTER_ALL && e.scope != scopeFilter) continue;
            if (hideUnavailable && !e.available) continue;
            if (!q.isEmpty()) {
                boolean nameMatch = e.name.toLowerCase(Locale.ROOT).contains(q);
                boolean ownerMatch = e.ownerLabel != null && e.ownerLabel.toLowerCase(Locale.ROOT).contains(q);
                if (!nameMatch && !ownerMatch) continue;
            }
            filtered.add(e);
        }
        if (selectedEntry != null) {
            VarCatalogClient457.Entry refreshed = null;
            for (VarCatalogClient457.Entry e : filtered) {
                if (e.key().equals(selectedKey)) {
                    refreshed = e;
                    break;
                }
            }
            selectedEntry = refreshed;
        }
        clampScroll();
        updateFormButtons();
    }

    private void startCreateNew() {
        creatingNew = true;
        selectedEntry = null;
        selectedKey = "";
        editingOldName = "";
        editingOldOwnerKey = "";
        formScope = VarDataNode453.SCOPE_LOCAL;
        editingOldScope = formScope;
        if (nameBox != null) nameBox.setValue("");
        if (valueBox != null) valueBox.setValue("0");
        if (descBox != null) descBox.setValue("");
        deleteArmAt = 0L;
        statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.status_new");
        statusColor = 0xFF9ED0FF;
        updateFormButtons();
    }

    private void loadEntryIntoForm(VarCatalogClient457.Entry e) {
        if (e == null) return;
        creatingNew = false;
        selectedEntry = e;
        selectedKey = e.key();
        editingOldName = e.name;
        editingOldScope = e.scope;
        editingOldOwnerKey = e.ownerKey != null ? e.ownerKey : "";
        formScope = e.scope;
        if (nameBox != null) nameBox.setValue(e.name);
        if (valueBox != null) valueBox.setValue(String.valueOf(e.value));
        if (descBox != null) descBox.setValue(e.description != null ? e.description : "");
        deleteArmAt = 0L;
        if (!e.available) {
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.unavailable_tip",
                    e.ownerLabel == null || e.ownerLabel.isEmpty()
                            ? VarCatalogClient457.tr("gui.settledlands.var_catalog.other_object") : e.ownerLabel);
            statusColor = 0xFFFF8866;
        } else {
            statusText = "";
        }
        updateFormButtons();
    }

    private void saveForm() {
        if (nameBox == null || valueBox == null) return;
        String clean = VarScopeStore453.cleanVarName(nameBox.getValue());
        if (clean.isEmpty()) {
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.err_empty_name");
            statusColor = 0xFFFF7766;
            return;
        }
        if (selectedEntry != null && !selectedEntry.available && formScope == VarDataNode453.SCOPE_LOCAL
                && !editingOldOwnerKey.isEmpty() && !editingOldOwnerKey.equals(VarCatalogClient457.currentOwnerKey())) {
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.err_foreign_local");
            statusColor = 0xFFFF7766;
            return;
        }
        int val = parseIntBox(valueBox, 0);
        int eid = parentScreen != null ? parentScreen.entityId() : VarCatalogClient457.activeEntityId();
        String ownerKey = formScope == VarDataNode453.SCOPE_LOCAL ? VarCatalogClient457.currentOwnerKey() : "";
        selectedKey = formScope + "|" + ownerKey + "|" + clean.toLowerCase(Locale.ROOT);
        VarCatalogClient457.sendSave(eid, clean, formScope, val,
                creatingNew ? "" : editingOldName, editingOldScope, editingOldOwnerKey,
                descBox != null ? descBox.getValue() : "");
        creatingNew = false;
        editingOldName = clean;
        editingOldScope = formScope;
        editingOldOwnerKey = ownerKey;
        statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.status_saved", clean);
        statusColor = 0xFF7BE088;
        updateFormButtons();
    }

    private void applyToNodeAndClose() {
        String clean = nameBox != null ? VarScopeStore453.cleanVarName(nameBox.getValue()) : "";
        if (clean.isEmpty()) {
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.err_empty_name");
            statusColor = 0xFFFF7766;
            return;
        }
        if (selectedEntry != null && !selectedEntry.available) {
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.err_foreign_local");
            statusColor = 0xFFFF7766;
            return;
        }
        int val = parseIntBox(valueBox, 0);
        int eid = parentScreen != null ? parentScreen.entityId() : VarCatalogClient457.activeEntityId();
        VarCatalogClient457.sendSave(eid, clean, formScope, val,
                creatingNew ? "" : editingOldName, editingOldScope, editingOldOwnerKey,
                descBox != null ? descBox.getValue() : "");

        VarCatalogClient457.Entry picked = new VarCatalogClient457.Entry();
        picked.name = clean;
        picked.scope = formScope;
        picked.value = val;
        picked.defaultValue = val;
        picked.ownerKey = formScope == VarDataNode453.SCOPE_LOCAL ? VarCatalogClient457.currentOwnerKey() : "";
        picked.ownerLabel = formScope == VarDataNode453.SCOPE_LOCAL ? VarCatalogClient457.currentOwnerLabel() : "";
        picked.description = descBox != null ? descBox.getValue() : "";
        picked.available = true;
        if (onPick != null) {
            onPick.accept(picked);
        }
        onClose();
    }

    private void handleDeleteClick() {
        if (selectedEntry == null || !selectedEntry.available) return;
        long now = System.currentTimeMillis();
        if (now - deleteArmAt > 3000L) {
            deleteArmAt = now;
            statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.confirm_delete", selectedEntry.name);
            statusColor = 0xFFFFB84D;
            updateFormButtons();
            return;
        }
        int eid = parentScreen != null ? parentScreen.entityId() : VarCatalogClient457.activeEntityId();
        VarCatalogClient457.sendDelete(eid, selectedEntry.name, selectedEntry.scope, selectedEntry.ownerKey);
        statusText = VarCatalogClient457.tr("gui.settledlands.var_catalog.status_deleted", selectedEntry.name);
        statusColor = 0xFFFF9A66;
        startCreateNew();
    }

    private void updateFormButtons() {
        boolean foreignLocked = selectedEntry != null && !selectedEntry.available;
        if (filterAllBtn != null) filterAllBtn.active = scopeFilter != FILTER_ALL;
        if (filterSharedBtn != null) filterSharedBtn.active = scopeFilter != VarDataNode453.SCOPE_SHARED;
        if (filterPersonalBtn != null) filterPersonalBtn.active = scopeFilter != VarDataNode453.SCOPE_PERSONAL;
        if (filterLocalBtn != null) filterLocalBtn.active = scopeFilter != VarDataNode453.SCOPE_LOCAL;

        if (scopeLocalBtn != null) scopeLocalBtn.active = !foreignLocked && formScope != VarDataNode453.SCOPE_LOCAL;
        if (scopePersonalBtn != null) scopePersonalBtn.active = !foreignLocked && formScope != VarDataNode453.SCOPE_PERSONAL;
        if (scopeSharedBtn != null) scopeSharedBtn.active = !foreignLocked && formScope != VarDataNode453.SCOPE_SHARED;
        if (nameBox != null) nameBox.setEditable(!foreignLocked);
        if (valueBox != null) valueBox.setEditable(!foreignLocked);
        if (descBox != null) descBox.setEditable(!foreignLocked);

        if (saveBtn != null) saveBtn.active = !foreignLocked;
        if (applyNodeBtn != null) applyNodeBtn.active = targetNode != null && !foreignLocked;
        if (deleteBtn != null) {
            deleteBtn.active = !creatingNew && selectedEntry != null && selectedEntry.available;
            boolean armed = System.currentTimeMillis() - deleteArmAt <= 3000L && deleteBtn.active;
            deleteBtn.setMessage(Component.translatable(armed
                    ? "gui.settledlands.var_catalog.btn_delete_confirm"
                    : "gui.settledlands.var_catalog.btn_delete"));
        }
    }

    private int visibleRows() {
        return Math.max(1, listH / ROW_H);
    }

    private void clampScroll() {
        int max = Math.max(0, filtered.size() - visibleRows());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (seenRevision != VarCatalogClient457.revision()) {
            seenRevision = VarCatalogClient457.revision();
            rebuildFiltered();
        }
        if (deleteArmAt > 0L && System.currentTimeMillis() - deleteArmAt > 3000L) {
            deleteArmAt = 0L;
            updateFormButtons();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        this.renderBackground(g, mx, my, partialTick);

        // Panel chrome
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF0A0D13);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0151A23);
        g.fill(panelX, panelY, panelX + panelW, panelY + 20, 0xFF1F2633);
        g.fill(panelX, panelY + 20, panelX + panelW, panelY + 21, 0xFFFF9A2E);

        String headerTitle = this.title.getString();
        g.drawString(this.font, headerTitle, panelX + 10, panelY + 6, 0xFFF3F6FC, false);
        String ctxOwner = VarCatalogClient457.currentOwnerLabel();
        if (ctxOwner != null && !ctxOwner.isEmpty()) {
            String ownerTxt = VarCatalogClient457.tr("gui.settledlands.var_catalog.current_owner", ctxOwner);
            int ow = this.font.width(ownerTxt);
            g.drawString(this.font, ownerTxt, panelX + panelW - ow - 10, panelY + 6, 0xFF9FB0C8, false);
        }

        // List container
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF0B0E14);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF10151E);

        int maxVis = visibleRows();
        if (filtered.isEmpty()) {
            g.drawCenteredString(this.font, trComp("gui.settledlands.var_catalog.empty"),
                    listX + listW / 2, listY + listH / 2 - 4, 0xFF7B8598);
        } else {
            for (int i = 0; i < maxVis; i++) {
                int idx = scroll + i;
                if (idx >= filtered.size()) break;
                VarCatalogClient457.Entry e = filtered.get(idx);
                int ry = listY + i * ROW_H;
                boolean hovered = mx >= listX && mx < listX + listW - 5 && my >= ry && my < ry + ROW_H;
                boolean selected = selectedEntry == e || (!selectedKey.isEmpty() && selectedKey.equals(e.key()));
                int bg = selected ? 0xFF2B3548 : (hovered ? 0xFF1E2634 : ((i & 1) == 0 ? 0xFF121822 : 0xFF151B26));
                g.fill(listX + 1, ry + 1, listX + listW - 5, ry + ROW_H - 1, bg);
                if (selected) {
                    g.fill(listX + 1, ry + 1, listX + 3, ry + ROW_H - 1, VarCatalogClient457.ACCENT_COLOR);
                }

                // Scope pill
                String tag = VarCatalogClient457.scopeTag(e.scope);
                int badgeCol = VarCatalogClient457.scopeBadgeColor(e.scope, e.available);
                int tw = this.font.width(tag) + 6;
                g.fill(listX + 5, ry + 3, listX + 5 + tw, ry + ROW_H - 3, (0x33 << 24) | (badgeCol & 0xFFFFFF));
                g.drawString(this.font, tag, listX + 8, ry + 5, badgeCol, false);

                // Name
                int nameX = listX + 8 + tw;
                int nameCol = e.available ? 0xFFE9EEF7 : 0xFF7B8291;
                String valStr = e.available ? String.valueOf(e.value) : "🔒";
                int valW = this.font.width(valStr);
                String ownerSuffix = (e.scope == VarDataNode453.SCOPE_LOCAL && e.ownerLabel != null && !e.ownerLabel.isEmpty())
                        ? (" (" + e.ownerLabel + ")") : "";
                String mainStr = e.name + ownerSuffix;
                int availW = Math.max(24, (listX + listW - 14 - valW) - nameX);
                String trimmed = this.font.plainSubstrByWidth(mainStr, availW);
                g.drawString(this.font, trimmed, nameX, ry + 5, nameCol, false);
                g.drawString(this.font, valStr, listX + listW - 10 - valW, ry + 5,
                        e.available ? 0xFFFFC777 : 0xFF8B6F68, false);
            }
        }

        // Scrollbar
        if (filtered.size() > maxVis) {
            int trackX = listX + listW - 4;
            g.fill(trackX, listY + 1, trackX + 3, listY + listH - 1, 0xFF1A202B);
            int thumbH = Math.max(16, (listH - 2) * maxVis / filtered.size());
            int maxScroll = Math.max(1, filtered.size() - maxVis);
            int thumbY = listY + 1 + (listH - 2 - thumbH) * scroll / maxScroll;
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF7D8B9E);
        }

        // Form labels
        g.drawString(this.font, trComp("gui.settledlands.var_catalog.name_label"), formX, formY + 2, 0xFF9EA8BC, false);
        g.drawString(this.font, trComp("gui.settledlands.var_catalog.value_label"), formX, formY + 32, 0xFF9EA8BC, false);
        g.drawString(this.font, trComp("gui.settledlands.var_catalog.scope_label"), formX, formY + 62, 0xFF9EA8BC, false);
        g.drawString(this.font, trComp("gui.settledlands.var_catalog.desc_label"), formX, formY + 92, 0xFF9EA8BC, false);

        if (formScope == VarDataNode453.SCOPE_LOCAL) {
            String ownerDisp = (selectedEntry != null && !creatingNew && !selectedEntry.ownerLabel.isEmpty())
                    ? selectedEntry.ownerLabel
                    : (ctxOwner == null || ctxOwner.isEmpty() ? "-" : ctxOwner);
            String ownerLine = this.font.plainSubstrByWidth(
                    VarCatalogClient457.tr("gui.settledlands.var_catalog.owner_row", ownerDisp), formW);
            g.drawString(this.font, ownerLine, formX, formY + 128, 0xFFB8C1D1, false);
        } else {
            String hint = VarCatalogClient457.tr(formScope == VarDataNode453.SCOPE_SHARED
                    ? "gui.settledlands.var.scope.shared.desc"
                    : "gui.settledlands.var.scope.personal.desc");
            String trimmedHint = this.font.plainSubstrByWidth(hint, formW);
            g.drawString(this.font, trimmedHint, formX, formY + 128, 0xFF8C97AB, false);
        }

        if (statusText != null && !statusText.isEmpty()) {
            String st = this.font.plainSubstrByWidth(statusText, formW);
            g.drawString(this.font, st, formX, formY + 142, statusColor, false);
        }

        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int row = (int) ((my - listY) / ROW_H);
            int idx = scroll + row;
            if (idx >= 0 && idx < filtered.size()) {
                loadEntryIntoForm(filtered.get(idx));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int max = Math.max(0, filtered.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (scrollY > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
