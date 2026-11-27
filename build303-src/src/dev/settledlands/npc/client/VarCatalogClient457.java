package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Client-side cache & dropdown integration for the world-wide variable catalog ({@code Build 457}).
 */
public final class VarCatalogClient457 {

    public static final int ACCENT_COLOR = 0xFFFF9A2E;
    public static final int SCOPE_COLOR_SHARED = 0xFF56C9FF;
    public static final int SCOPE_COLOR_PERSONAL = 0xFF7BE088;
    public static final int SCOPE_COLOR_LOCAL = 0xFFFFAA44;
    public static final int LOCKED_COLOR = 0xFF7A808E;

    public static final class Entry {
        public String name = "";
        public int scope = VarDataNode453.SCOPE_LOCAL;
        public int value = 0;
        public int defaultValue = 0;
        public String ownerKey = "";
        public String ownerLabel = "";
        public String description = "";
        public boolean available = true;
        public int usageCount = 0;

        public String key() {
            return scope + "|" + (ownerKey == null ? "" : ownerKey) + "|"
                    + (name == null ? "" : name.toLowerCase(Locale.ROOT));
        }
    }

    public static final class DropdownRowMeta {
        public final boolean isCreateRow;
        public final Entry entry;
        public final String displayName;

        public DropdownRowMeta(boolean isCreateRow, Entry entry, String displayName) {
            this.isCreateRow = isCreateRow;
            this.entry = entry;
            this.displayName = displayName != null ? displayName : "";
        }
    }

    public static final class TrackedDropdown {
        public final NpcUi.NpcDropdown dropdown;
        public final List<DropdownRowMeta> rows;
        public final NpcDialogueGraphScreen screen;
        public final NpcDialogueGraph.Node node;
        public final int accentColor;
        public final Consumer<Entry> onPick;

        public TrackedDropdown(NpcUi.NpcDropdown dropdown, List<DropdownRowMeta> rows,
                NpcDialogueGraphScreen screen, NpcDialogueGraph.Node node,
                int accentColor, Consumer<Entry> onPick) {
            this.dropdown = dropdown;
            this.rows = rows;
            this.screen = screen;
            this.node = node;
            this.accentColor = accentColor;
            this.onPick = onPick;
        }
    }

    private static final Map<NpcUi.NpcDropdown, TrackedDropdown> TRACKED_DROPDOWNS = new WeakHashMap<>();

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static String currentOwnerKey = "";
    private static String currentOwnerLabel = "";
    private static int currentContextId = 0;
    private static int revision = 0;
    private static int activeEntityId = 0;

    private VarCatalogClient457() {}

    public static void setActiveEntityId(int entityId) {
        if (entityId != 0) activeEntityId = entityId;
    }

    public static int activeEntityId() {
        return activeEntityId;
    }

    public static String currentOwnerKey() {
        return currentOwnerKey;
    }

    public static String currentOwnerLabel() {
        return currentOwnerLabel;
    }

    public static int currentContextId() {
        return currentContextId;
    }

    public static int revision() {
        return revision;
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static Entry findMatching(String name, int preferredScope) {
        String clean = VarScopeStore453.cleanVarName(name);
        if (clean.isEmpty()) return null;
        Entry fallback = null;
        for (Entry e : ENTRIES) {
            if (!e.available) continue;
            if (e.name.equalsIgnoreCase(clean)) {
                if (e.scope == preferredScope) return e;
                if (fallback == null) fallback = e;
            }
        }
        return fallback;
    }

    public static void applyPacket(CompoundTag tag) {
        if (tag == null) return;
        currentOwnerKey = tag.getString("OwnerKey");
        currentOwnerLabel = tag.getString("OwnerLabel");
        currentContextId = tag.getInt("ContextId");
        ENTRIES.clear();
        ListTag list = tag.getList("Items", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            Entry e = new Entry();
            e.name = row.getString("N");
            e.scope = row.getInt("S");
            e.value = row.getInt("V");
            e.defaultValue = row.getInt("D");
            e.ownerKey = row.getString("OK");
            e.ownerLabel = row.getString("OL");
            e.description = row.getString("Desc");
            e.available = !row.contains("A") || row.getBoolean("A");
            e.usageCount = row.getInt("U");
            if (!e.name.isEmpty()) {
                ENTRIES.add(e);
            }
        }
        revision++;
    }

    public static void requestSync(int entityId) {
        int id = entityId != 0 ? entityId : activeEntityId;
        if (id == 0) return;
        NpcNet.sendToServer(new NpcNet.CfgEdit(id, "var457.list", 0, 0, 0, "", ""));
    }

    public static void sendSave(int entityId, String name, int scope, int value,
            String oldName, int oldScope, String oldOwnerKey, String desc) {
        int id = entityId != 0 ? entityId : activeEntityId;
        if (id == 0) return;
        String cleanName = VarScopeStore453.cleanVarName(name);
        if (cleanName.isEmpty()) return;
        String cleanOld = oldName == null ? "" : VarScopeStore453.cleanVarName(oldName);
        String safeOwner = oldOwnerKey == null ? "" : oldOwnerKey.replace('\n', ' ').replace('|', '/');
        String safeDesc = desc == null ? "" : desc.replace('\n', ' ').trim();
        if (safeDesc.length() > 128) safeDesc = safeDesc.substring(0, 128);
        String payload = cleanOld + "|" + oldScope + "|" + safeOwner + "\n" + safeDesc;
        NpcNet.sendToServer(new NpcNet.CfgEdit(id, "var457.save", scope, value, 1, cleanName, payload));
    }

    public static void sendDelete(int entityId, String name, int scope, String ownerKey) {
        int id = entityId != 0 ? entityId : activeEntityId;
        if (id == 0) return;
        String clean = VarScopeStore453.cleanVarName(name);
        if (clean.isEmpty()) return;
        NpcNet.sendToServer(new NpcNet.CfgEdit(id, "var457.delete", scope, 0, 0,
                clean, ownerKey == null ? "" : ownerKey));
    }

    public static int scopeBadgeColor(int scope, boolean available) {
        if (!available) return LOCKED_COLOR;
        if (scope == VarDataNode453.SCOPE_SHARED) return SCOPE_COLOR_SHARED;
        if (scope == VarDataNode453.SCOPE_PERSONAL) return SCOPE_COLOR_PERSONAL;
        return SCOPE_COLOR_LOCAL;
    }

    public static String scopeTag(int scope) {
        if (scope == VarDataNode453.SCOPE_SHARED) return tr("gui.settledlands.var_catalog.scope_tag.shared");
        if (scope == VarDataNode453.SCOPE_PERSONAL) return tr("gui.settledlands.var_catalog.scope_tag.personal");
        return tr("gui.settledlands.var_catalog.scope_tag.local");
    }

    public static String scopeFullName(int scope) {
        if (scope == VarDataNode453.SCOPE_SHARED) return tr("gui.settledlands.var.scope.shared");
        if (scope == VarDataNode453.SCOPE_PERSONAL) return tr("gui.settledlands.var.scope.personal");
        return tr("gui.settledlands.var.scope.local");
    }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Populates dropdown display labels and row metadata from the synced world variable catalog.
     * Returns the initial selected index (0 = "+ Catalog / create...").
     */
    public static int buildDropdownOptions(String currentName, int currentScope,
            List<Component> outLabels, List<DropdownRowMeta> outMeta) {
        outLabels.clear();
        outMeta.clear();
        String cleanCurrent = VarScopeStore453.cleanVarName(currentName);

        String createLabel = tr("gui.settledlands.var_catalog.dropdown_pick");
        outLabels.add(Component.literal(createLabel));
        outMeta.add(new DropdownRowMeta(true, null, createLabel));

        int matchedIdx = 0;
        for (Entry e : ENTRIES) {
            if (e == null || e.name == null || e.name.isEmpty()) continue;
            String tag = scopeTag(e.scope);
            String label;
            if (!e.available) {
                label = "[" + tag + "] " + e.name;
            } else {
                label = "[" + tag + "] " + e.name + " (" + e.value + ")";
            }
            outLabels.add(Component.literal(label));
            outMeta.add(new DropdownRowMeta(false, e, e.name));
            if (e.available && !cleanCurrent.isEmpty() && e.name.equalsIgnoreCase(cleanCurrent)) {
                if (e.scope == currentScope || matchedIdx == 0) {
                    matchedIdx = outLabels.size() - 1;
                }
            }
        }
        return matchedIdx;
    }

    // ---- Tracked dropdown overlay (badges, unavailability guard, inline gears) ----

    public static void trackDropdown(NpcUi.NpcDropdown dropdown, List<DropdownRowMeta> rows,
            NpcDialogueGraphScreen screen, NpcDialogueGraph.Node node, Consumer<Entry> onPick) {
        if (dropdown == null || rows == null) return;
        TRACKED_DROPDOWNS.put(dropdown, new TrackedDropdown(dropdown, rows, screen, node, ACCENT_COLOR, onPick));
    }

    public static boolean isTracked(NpcUi.NpcDropdown dropdown) {
        return dropdown != null && TRACKED_DROPDOWNS.containsKey(dropdown);
    }

    public static void renderDropdownOverlay(NpcUi.NpcDropdown dropdown, GuiGraphics g,
            int mx, int my, List<?> entries, int scroll, int currentIndex) {
        TrackedDropdown tracked = TRACKED_DROPDOWNS.get(dropdown);
        if (tracked == null || g == null || entries == null || entries.isEmpty()) return;

        int visible = Math.min(7, entries.size());
        int x = dropdown.getX();
        int y = dropdown.getY() + dropdown.getHeight();
        int w = Math.max(90, dropdown.getWidth());
        boolean hasScroll = entries.size() > visible;
        int gearX = x + w - (hasScroll ? 18 : 14);
        int bgLeft = gearX - 2;
        int bgRight = x + w - (hasScroll ? 4 : 1);

        String tipText = null;
        int tipAccent = tracked.accentColor;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;

        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx < 0 || idx >= entries.size()) continue;
            DropdownRowMeta meta = idx < tracked.rows.size() ? tracked.rows.get(idx) : null;
            int ry = y + i * 14;
            int gy = ry + 1;

            if (meta != null && !meta.isCreateRow && meta.entry != null && !meta.entry.available) {
                g.fill(x + 1, ry + 1, bgLeft, ry + 13, 0x60101218);
                if (font != null) {
                    String tag = tr("gui.settledlands.var_catalog.locked_badge");
                    int tw = font.width(tag);
                    int tx = Math.max(x + 4, bgLeft - tw - 4);
                    g.drawString(font, tag, tx, ry + 3, LOCKED_COLOR, false);
                }
            }

            boolean rowHovered = mx >= x && mx <= x + w && my >= ry && my < ry + 14;
            boolean gearHovered = mx >= gearX - 1 && mx < gearX + 13 && my >= ry && my < ry + 14;

            g.fill(bgLeft, ry + 1, bgRight, ry + 13, rowHovered ? 0xF05C3D24 : 0xF0181D27);
            LootPoolGearButton440.drawInlineGearFrame(g, gearX, gy, gearHovered, tracked.accentColor);

            if (meta != null) {
                if (gearHovered) {
                    tipText = meta.isCreateRow
                            ? tr("gui.settledlands.var_catalog.create_row_tip")
                            : tr("gui.settledlands.var_catalog.edit_row_tip", meta.entry.name);
                } else if (rowHovered && !meta.isCreateRow && meta.entry != null && !meta.entry.available) {
                    String owner = meta.entry.ownerLabel == null || meta.entry.ownerLabel.isEmpty()
                            ? tr("gui.settledlands.var_catalog.other_object") : meta.entry.ownerLabel;
                    tipText = tr("gui.settledlands.var_catalog.unavailable_tip", owner);
                    tipAccent = LOCKED_COLOR;
                } else if (rowHovered && !meta.isCreateRow && meta.entry != null && !meta.entry.description.isEmpty()) {
                    tipText = meta.entry.description;
                }
            }
        }

        if (tipText != null && font != null) {
            int tipW = font.width(tipText) + 8;
            int tx = Math.max(4, Math.min(mx + 8, Math.max(4, (x + w) - tipW)));
            int ty = my - 16;
            if (ty < 4) ty = my + 14;
            g.fill(tx - 1, ty - 1, tx + tipW + 1, ty + 13, 0xFF000000 | tipAccent);
            g.fill(tx, ty, tx + tipW, ty + 12, 0xEE141A24);
            g.drawString(font, tipText, tx + 4, ty + 2, 0xFFF2F5FA, false);
        }
    }

    public static boolean handleDropdownPopupClick(NpcUi.NpcDropdown dropdown,
            double mx, double my, int scroll, int totalRows) {
        TrackedDropdown tracked = TRACKED_DROPDOWNS.get(dropdown);
        if (tracked == null || totalRows <= 0) return false;
        int visible = Math.min(7, totalRows);
        int x = dropdown.getX();
        int y = dropdown.getY() + dropdown.getHeight();
        int w = Math.max(90, dropdown.getWidth());
        if (mx < x || mx > x + w || my < y || my >= y + visible * 14) {
            return false;
        }
        int row = (int) ((my - y) / 14.0);
        int idx = scroll + row;
        if (idx < 0 || idx >= totalRows) return false;

        boolean hasScroll = totalRows > visible;
        int gearX = x + w - (hasScroll ? 18 : 14);
        DropdownRowMeta meta = idx < tracked.rows.size() ? tracked.rows.get(idx) : null;

        // 1. Clicking inline gear opens the world Variable Catalog Screen directly on this item
        if (mx >= gearX - 1 && mx < gearX + 13) {
            dropdown.closePopup();
            openCatalogEditor(tracked.screen, tracked.node,
                    meta != null && meta.entry != null ? meta.entry.name : "",
                    meta != null && meta.entry != null ? meta.entry.scope : VarDataNode453.SCOPE_LOCAL,
                    meta != null && meta.isCreateRow,
                    tracked.onPick);
            return true;
        }

        // 2. Clicking the "+ New / Catalog..." row opens the editor directly in create mode
        if (meta != null && meta.isCreateRow) {
            dropdown.closePopup();
            openCatalogEditor(tracked.screen, tracked.node, "",
                    tracked.node != null ? VarDataNode453.scopeOf(tracked.node) : VarDataNode453.SCOPE_LOCAL,
                    true, tracked.onPick);
            return true;
        }

        // 3. Clicking an unavailable local variable belonging to another NPC/object is blocked
        if (meta != null && meta.entry != null && !meta.entry.available) {
            return true;
        }

        return false;
    }

    public static void openCatalogEditor(NpcDialogueGraphScreen screen, NpcDialogueGraph.Node node,
            String initialName, int initialScope, boolean createNew, Consumer<Entry> onPick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (screen != null) setActiveEntityId(screen.entityId());
        mc.setScreen(new VarPoolEditorScreen457(screen, node, initialName, initialScope, createNew, onPick));
    }
}
