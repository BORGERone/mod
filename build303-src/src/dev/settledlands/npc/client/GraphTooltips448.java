package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialMobPool449;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Build 448: state-aware, contextual hover tooltip engine with a strict &gt;2-second hover delay
 * across both {@link NpcDialogueGraphScreen} (top bar, inspector widgets, sliders, dropdowns,
 * context menu, canvas pins/sockets, Auto-AI/Stats cards, and node bodies) and
 * {@link TrialControlConfigScreen446}.
 */
public final class GraphTooltips448 {

    public static final long DELAY_MS = 2050L;

    private static final int BG = 0xF210151F;
    private static final int DIVIDER = 0xFF2B3548;
    private static final int BODY_TEXT = 0xFFD4DFEE;
    private static final int DEFAULT_ACCENT = 0xFF7FC8F8;
    private static final int WARN_ACCENT = 0xFFFFB74D;
    private static final int OMINOUS_ACCENT = 0xFFE06FD8;
    private static final int NORMAL_ACCENT = 0xFF4CE0A8;
    private static final int SPECIAL_ACCENT = 0xFF64B5F6;

    public static final class Tip {
        public final String slotId;
        public final String title;
        public final String body;
        public final int accent;

        public Tip(String slotId, String title, String body, int accent) {
            this.slotId = slotId;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.accent = accent;
        }
    }

    public static final class ConfigArea {
        public final String slotId;
        public final Object widget;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final String tag;
        public final String arg;

        public ConfigArea(String slotId, Object widget, int x, int y, int w, int h,
                String tag, String arg) {
            this.slotId = slotId;
            this.widget = widget;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.tag = tag;
            this.arg = arg == null ? "" : arg;
        }
    }

    private static final int[] STATS_ROWS = { 32, 66, 100 };
    private static final String[] STATS_LABELS = {
        "gui.settledlands.stats.health",
        "gui.settledlands.stats.damage",
        "gui.settledlands.stats.speed"
    };

    private static final class TrackedLabel {
        final String key;
        final int x;
        final int y;

        TrackedLabel(String key, int x, int y) {
            this.key = key;
            this.x = x;
            this.y = y;
        }
    }

    private static final class TrackedWidget {
        final Object widget;
        final int kind; // 0=button, 1=box, 2=multibox, 3=dropdown, 4=slider, 5=gear
        final int x;
        final int y;
        final int w;
        final int h;
        final String rawKey;
        final List<Component> dropdownEntries;
        String labelKey = "";
        int inspectorIdx = -1;
        String explicitTag = "";

        TrackedWidget(Object widget, int kind, int x, int y, int w, int h,
                String rawKey, List<Component> dropdownEntries) {
            this.widget = widget;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.rawKey = rawKey == null ? "" : rawKey;
            this.dropdownEntries = dropdownEntries;
        }

        boolean contains(double mx, double my) {
            int ry = y;
            int rh = h;
            if (widget instanceof AbstractWidget aw) {
                if (!aw.visible) return false;
                ry = aw.getY();
                rh = Math.max(12, aw.getHeight());
            }
            return mx >= x && mx < x + w && my >= ry && my < ry + rh;
        }
    }

    private static final class ScreenState {
        final List<TrackedWidget> widgets = new ArrayList<>();
        final Map<Object, TrackedWidget> byWidget = new IdentityHashMap<>();
        final List<TrackedLabel> labels = new ArrayList<>();
        Node selectedNode;
        int selectedId = -1;
        int sliderOrder = 0;
        int modeBtnOrder = 0;
    }

    private static final class DropdownPopupState {
        Object dropdown;
        int scroll;
        int hoveredIndex = -1;
        int popupX;
        int popupY;
        int popupW;
        int popupH;
        long updatedAt;
    }

    private static final Map<NpcDialogueGraphScreen, ScreenState> SCREENS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static NpcDialogueGraphScreen buildingScreen;
    private static final DropdownPopupState OPEN_POPUP = new DropdownPopupState();

    // Shared hover state machine
    private static Object activeOwner;
    private static String activeSlotId = "";
    private static long hoverStartMs = 0L;
    private static boolean shownForSlot = false;

    private GraphTooltips448() {}

    // ---- Registration hooks -------------------------------------------------

    public static void beginScreenWidgets(NpcDialogueGraphScreen screen) {
        if (screen == null) return;
        buildingScreen = screen;
        SCREENS.put(screen, new ScreenState());
    }

    public static void recordButton(NpcDialogueGraphScreen screen, Object btn, String key,
            int x, int y, int w, int h) {
        addTracked(screen, btn, 0, x, y, w, h, key, null);
    }

    public static void recordBox(NpcDialogueGraphScreen screen, Object box,
            int x, int y, int w, int h) {
        addTracked(screen, box, 1, x, y, w, h, "", null);
    }

    public static void recordMultiBox(NpcDialogueGraphScreen screen, Object box,
            int x, int y, int w) {
        int h = (box instanceof AbstractWidget aw) ? Math.max(20, aw.getHeight()) : 60;
        int ry = (box instanceof AbstractWidget aw) ? aw.getY() : y;
        addTracked(screen, box, 2, x, ry, Math.max(30, w), h, "", null);
    }

    public static void recordUiDropdown(Object widget, int x, int y, int w, List<Component> entries) {
        if (buildingScreen == null || widget == null) return;
        addTracked(buildingScreen, widget, 3, x, y, w, 20, "", entries);
    }

    public static void recordUiSlider(Object widget, int x, int y, int w) {
        if (buildingScreen == null || widget == null) return;
        addTracked(buildingScreen, widget, 4, x, y, w, 20, "", null);
    }

    public static void recordGearButton(Object widget, int x, int y, int w, int h) {
        if (buildingScreen == null || widget == null) return;
        addTracked(buildingScreen, widget, 5, x, y, w, h, "gui.settledlands.loot_pool.edit_btn_tip", null);
    }

    public static void recordOpenPopup(Object dropdown, int scroll, List<?> entries, int mouseX, int mouseY) {
        if (dropdown == null) return;
        ScreenState st = findScreenForWidget(dropdown);
        if (st == null && buildingScreen != null) st = SCREENS.get(buildingScreen);
        TrackedWidget tw = st != null ? st.byWidget.get(dropdown) : null;
        if (tw == null) return;
        int count = entries == null ? 0 : entries.size();
        int visible = Math.min(7, count);
        int baseY = (dropdown instanceof AbstractWidget aw) ? aw.getY() + aw.getHeight() : tw.y + 20;
        int px = tw.x;
        int py = baseY;
        int pw = Math.max(tw.w, 90);
        int ph = visible * 14;
        OPEN_POPUP.dropdown = dropdown;
        OPEN_POPUP.scroll = scroll;
        OPEN_POPUP.popupX = px;
        OPEN_POPUP.popupY = py;
        OPEN_POPUP.popupW = pw;
        OPEN_POPUP.popupH = ph;
        OPEN_POPUP.updatedAt = System.currentTimeMillis();
        if (mouseX >= px && mouseX < px + pw && mouseY >= py && mouseY < py + ph) {
            int row = (mouseY - py) / 14;
            int idx = scroll + row;
            OPEN_POPUP.hoveredIndex = (idx >= 0 && idx < count) ? idx : -1;
        } else {
            OPEN_POPUP.hoveredIndex = -1;
        }
    }

    public static void recordLabel(NpcDialogueGraphScreen screen, String key, int x, int y) {
        if (screen == null || key == null) return;
        ScreenState st = SCREENS.get(screen);
        if (st == null) {
            st = new ScreenState();
            SCREENS.put(screen, st);
        }
        st.labels.add(new TrackedLabel(key, x, y));
    }

    public static void removeLabel(NpcDialogueGraphScreen screen, String key, int y) {
        if (screen == null || key == null) return;
        ScreenState st = SCREENS.get(screen);
        if (st == null) return;
        for (int i = st.labels.size() - 1; i >= 0; i--) {
            TrackedLabel tl = st.labels.get(i);
            if (tl.y == y && key.equals(tl.key)) {
                st.labels.remove(i);
            }
        }
    }

    public static void replaceLabelKey(NpcDialogueGraphScreen screen, String oldKey,
            String newKey, int y) {
        if (screen == null || oldKey == null || newKey == null) return;
        ScreenState st = SCREENS.get(screen);
        if (st == null) return;
        for (int i = 0; i < st.labels.size(); i++) {
            TrackedLabel tl = st.labels.get(i);
            if (tl.y == y && oldKey.equals(tl.key)) {
                st.labels.set(i, new TrackedLabel(newKey, tl.x, tl.y));
            }
        }
    }

    public static void finishScreenWidgets(NpcDialogueGraphScreen screen,
            List<?> rawInspectorWidgets, NpcDialogueGraph graph, int selected) {
        if (screen == null) return;
        ScreenState st = SCREENS.get(screen);
        if (st == null) {
            st = new ScreenState();
            SCREENS.put(screen, st);
        }
        st.selectedId = selected;
        st.selectedNode = (graph != null && selected >= 0) ? graph.get(selected) : null;
        if (rawInspectorWidgets != null) {
            for (int i = 0; i < rawInspectorWidgets.size(); i++) {
                TrackedWidget tw = st.byWidget.get(rawInspectorWidgets.get(i));
                if (tw != null) tw.inspectorIdx = i;
            }
        }
        // Resolve nearest preceding label for each inspector field/dropdown/slider
        for (TrackedWidget tw : st.widgets) {
            if (tw.inspectorIdx < 0) continue;
            if (tw.kind == 1 || tw.kind == 2 || tw.kind == 3) {
                tw.labelKey = findNearestLabelKey(st.labels, tw.x, tw.y);
            } else if (tw.kind == 4) {
                tw.explicitTag = "slider_" + st.sliderOrder++;
            } else if (tw.kind == 0) {
                tw.labelKey = findNearestLabelKey(st.labels, tw.x, tw.y);
            }
        }
        buildingScreen = null;
    }

    private static void addTracked(NpcDialogueGraphScreen screen, Object widget, int kind,
            int x, int y, int w, int h, String rawKey, List<Component> entries) {
        if (screen == null || widget == null) return;
        ScreenState st = SCREENS.get(screen);
        if (st == null) {
            st = new ScreenState();
            SCREENS.put(screen, st);
        }
        TrackedWidget tw = new TrackedWidget(widget, kind, x, y, w, h, rawKey, entries);
        st.widgets.add(tw);
        st.byWidget.put(widget, tw);
    }

    private static ScreenState findScreenForWidget(Object widget) {
        synchronized (SCREENS) {
            for (ScreenState st : SCREENS.values()) {
                if (st.byWidget.containsKey(widget)) return st;
            }
        }
        return null;
    }

    private static String findNearestLabelKey(List<TrackedLabel> labels, int wx, int wy) {
        String bestKey = "";
        int bestDist = 9999;
        for (TrackedLabel l : labels) {
            if (l == null || l.key == null) continue;
            // Label directly above the widget (standard layout: y = label.y + 10..13)
            if (l.y <= wy + 2 && wy - l.y <= 18 && Math.abs(l.x - wx) <= 28) {
                int dist = Math.abs(wy - l.y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestKey = l.key;
                }
            }
            // Same row, label to the left (e.g. X: [box], Y: [box], Z: [box])
            if (l.x < wx && wx - l.x <= 32 && Math.abs(l.y - wy) <= 10) {
                int dist = Math.abs(wx - l.x);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestKey = l.key;
                }
            }
        }
        return bestKey;
    }

    public static void onMouseClick() {
        if (!shownForSlot) {
            hoverStartMs = System.currentTimeMillis();
        }
    }

    // ---- Main graph render hook ---------------------------------------------

    public static void renderGraphTooltip(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            int screenW, int screenH, int mouseX, int mouseY, NpcDialogueGraph graph,
            NpcGraphCanvas canvas, NpcGraphContextMenu contextMenu, NpcUi.NpcDropdown openDropdown,
            int dragging, int linkFrom, boolean panning, boolean selectingBox,
            int inspectorX, int inspectorY, int inspectorW, int inspectorH,
            int tab, int selected) {
        if (screen == null || graphics == null) return;
        Font font = screen.settledlands$font();
        if (font == null) return;

        if (dragging >= 0 || linkFrom >= 0 || panning || selectingBox) {
            resetIfOwner(screen);
            return;
        }

        Tip tip = resolveGraphTip(screen, mouseX, mouseY, graph, canvas, contextMenu,
                openDropdown, inspectorX, inspectorY, inspectorW, inspectorH, tab, selected);
        updateAndDraw(screen, graphics, font, screenW, screenH, mouseX, mouseY, tip);
    }

    private static Tip resolveGraphTip(NpcDialogueGraphScreen screen, int mouseX, int mouseY,
            NpcDialogueGraph graph, NpcGraphCanvas canvas, NpcGraphContextMenu contextMenu,
            NpcUi.NpcDropdown openDropdown, int inspectorX, int inspectorY,
            int inspectorW, int inspectorH, int tab, int selected) {
        // 1. Open context menu (blocks everything underneath)
        if (contextMenu != null && contextMenu.open) {
            return resolveContextMenuTip(screen, contextMenu, mouseX, mouseY);
        }

        // 2. Open dropdown popup (blocks everything underneath)
        ScreenState st = SCREENS.get(screen);
        Node liveSel = (graph != null && selected >= 0) ? graph.get(selected) : null;
        if (st != null && liveSel != null) {
            st.selectedId = selected;
            st.selectedNode = liveSel;
        }
        if (openDropdown != null) {
            Tip popTip = resolveOpenDropdownTip(st, openDropdown, mouseX, mouseY);
            if (popTip != null) return popTip;
        }

        // 3. Widgets (top bar + inspector)
        if (st != null) {
            for (int i = st.widgets.size() - 1; i >= 0; i--) {
                TrackedWidget tw = st.widgets.get(i);
                if (tw.inspectorIdx >= 0
                        && (mouseY < inspectorY + 21 || mouseY > inspectorY + inspectorH - 2)) {
                    continue;
                }
                if (tw.contains(mouseX, mouseY)) {
                    return resolveWidgetTip(screen, st, tw, graph);
                }
            }
            // Also check inspector informational labels (e.g. trial hub socket hints or labels above fields)
            if (screen.settledlands$insideInspector(mouseX, mouseY)
                    && mouseY >= inspectorY + 21 && mouseY <= inspectorY + inspectorH - 4) {
                for (TrackedLabel l : st.labels) {
                    if (l.key != null && mouseY >= l.y - 2 && mouseY < l.y + 11
                            && mouseX >= inspectorX + 4 && mouseX <= inspectorX + inspectorW - 4) {
                        Tip lt = resolveInspectorLabelTip(liveSel != null ? liveSel : st.selectedNode,
                                l.key, l.y);
                        if (lt != null) return lt;
                    }
                }
                return null;
            }
        } else if (screen.settledlands$insideInspector(mouseX, mouseY)) {
            return null;
        }

        // 4. Canvas pins, interactive cards, and node bodies/footers
        if (canvas != null && canvas.contains(mouseX, mouseY) && graph != null) {
            Tip pinTip = resolveCanvasPinTip(graph, canvas, mouseX, mouseY);
            if (pinTip != null) return pinTip;

            Tip aiTip = resolveAutoAiTip(screen, graph, canvas, mouseX, mouseY);
            if (aiTip != null) return aiTip;

            Tip nodeTip = resolveCanvasNodeTip(screen, graph, canvas, mouseX, mouseY, tab);
            if (nodeTip != null) return nodeTip;
        }
        return null;
    }

    // ---- 1. Context menu tooltips -------------------------------------------

    private static Tip resolveContextMenuTip(NpcDialogueGraphScreen screen,
            NpcGraphContextMenu menu, double mx, double my) {
        if (!menu.contains(mx, my)) return null;
        if (menu.targetNodeId >= 0) {
            int row = menu.rowAt(mx, my);
            if (row == 0) {
                return tip("ctx:copy", tr("gui.settledlands.graph.copy_node"),
                        tr("gui.settledlands.tip448.ctx.copy"), DEFAULT_ACCENT);
            }
            if (row == 1) {
                return tip("ctx:del", tr("gui.settledlands.graph.delete"),
                        tr("gui.settledlands.tip448.ctx.delete"), WARN_ACCENT);
            }
            return null;
        }
        if (BlockMenu363.ready()) {
            int row = BlockMenu363.rowAt(menu, mx, my);
            if (row < 0) return null;
            if (BlockMenu363.isTeleportRow(row)) {
                return tip("ctx:blk_tp", BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.teleport"), TeleportNode424.ACCENT_COLOR);
            }
            if (BlockMenu363.isTrialControlRow(row)) {
                return tip("ctx:blk_tc", BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.trial_ctrl"), TrialControlNode446.ACCENT_COLOR);
            }
            if (BlockMenu363.isLootPoolRow(row)) {
                return tip("ctx:blk_lp", BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.loot_pool"), LootPool436.ACCENT_COLOR);
            }
            if (BlockMenu363.isVarDataRow(row)) {
                return tip("ctx:blk_v453", BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.var453"), VarDataNode453.ACCENT_COLOR);
            }
            int modSlot = BlockMenu363.trialModuleSlot(row);
            if (modSlot >= 0) {
                return tip("ctx:tmod_" + modSlot, BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.tmod_" + TrialModule447.kindOf(modSlot)),
                        BlockMenu363.colour(row));
            }
            int type = BlockMenu363.doorType(row);
            if (type >= 0) {
                return tip("ctx:blk_" + type, BlockMenu363.label(row),
                        tr("gui.settledlands.tip448.menu.node_" + type), BlockMenu363.colour(row));
            }
            return null;
        }
        int row = menu.rowAt(mx, my);
        if (row < 0) {
            // Rows 16..18 painted via mixins in NPC/common graphs (pursuit=16, teleport=17, trial_ctrl=18)
            int local = (int) ((my - menu.top - 20) / 22);
            if (local >= 0 && local < 5 && mx >= menu.left && mx < menu.left + menu.width - 10) {
                row = menu.scrollRow + local;
            }
        }
        if (row >= 0 && row <= 15) {
            return tip("ctx:npc_" + row, stockMenuLabel(row),
                    tr("gui.settledlands.tip448.menu.node_" + stockMenuType(row)), DEFAULT_ACCENT);
        }
        if (row == 16) {
            return tip("ctx:npc_16", tr("gui.settledlands.graph.pursuit_menu"),
                    tr("gui.settledlands.tip448.menu.pursuit"), DEFAULT_ACCENT);
        }
        if (row == TeleportNode424.TELEPORT_ROW) {
            return tip("ctx:npc_17", tr(TeleportNode424.MENU_LABEL_KEY),
                    tr("gui.settledlands.tip448.menu.teleport"), TeleportNode424.ACCENT_COLOR);
        }
        if (row == TrialControlNode446.SPAWNER_ROW) {
            return tip("ctx:npc_18", tr(TrialControlNode446.MENU_LABEL_KEY),
                    tr("gui.settledlands.tip448.menu.trial_ctrl"), TrialControlNode446.ACCENT_COLOR);
        }
        if (row == VarDataNode453.MENU_ROW) {
            return tip("ctx:npc_19", tr(VarDataNode453.MENU_LABEL_KEY),
                    tr("gui.settledlands.tip448.menu.var453"), VarDataNode453.ACCENT_COLOR);
        }
        return null;
    }

    // ---- 2. Open dropdown popup item tooltips -------------------------------

    private static Tip resolveOpenDropdownTip(ScreenState st, NpcUi.NpcDropdown drop,
            int mx, int my) {
        if (!drop.popupOpen()) return null;
        if (OPEN_POPUP.dropdown != drop || System.currentTimeMillis() - OPEN_POPUP.updatedAt > 250L) {
            return null;
        }
        if (mx < OPEN_POPUP.popupX || mx >= OPEN_POPUP.popupX + OPEN_POPUP.popupW
                || my < OPEN_POPUP.popupY || my >= OPEN_POPUP.popupY + OPEN_POPUP.popupH) {
            return null;
        }
        int idx = OPEN_POPUP.hoveredIndex;
        TrackedWidget tw = st == null ? null : st.byWidget.get(drop);
        if (idx < 0 || tw == null || tw.dropdownEntries == null || idx >= tw.dropdownEntries.size()) {
            return null;
        }
        String entryText = tw.dropdownEntries.get(idx).getString();
        String lk = tw.labelKey;
        if ("gui.settledlands.trial_ctl.op_prefix".equals(lk)) {
            String opKey = trialOpTagByIndex(idx);
            return tip("drop:op:" + idx, entryText,
                    tr("gui.settledlands.tip448.op." + opKey), TrialControlNode446.ACCENT_COLOR);
        }
        if ("gui.settledlands.var453.op_label".equals(lk)) {
            return tip("drop:v453op:" + idx, entryText,
                    tr("gui.settledlands.tip448.var453_op"), VarDataNode453.ACCENT_COLOR);
        }
        if ("gui.settledlands.graph.var_op".equals(lk)) {
            return tip("drop:varop:" + idx, entryText,
                    tr("gui.settledlands.tip448.varop." + idx), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.trigger_presets".equals(lk)) {
            int pIdx = Math.max(0, Math.min(9, idx));
            return tip("drop:preset:" + idx, entryText,
                    tr("gui.settledlands.tip448.preset." + pIdx), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.teleport.world".equals(lk)) {
            return tip("drop:world:" + idx, entryText,
                    tr("gui.settledlands.tip448.world_item"), TeleportNode424.ACCENT_COLOR);
        }
        if ("gui.settledlands.trial.mob_pool.file".equals(lk)) {
            return tip("drop:mp:" + idx, entryText,
                    tr(idx == 0 ? "gui.settledlands.tip448.mob_pool.create_item"
                            : "gui.settledlands.tip448.mob_pool.pick_item"),
                    TrialMobPool449.ACCENT_COLOR);
        }
        if ("gui.settledlands.trial.reward_pool.file".equals(lk)) {
            return tip("drop:rp:" + idx, entryText,
                    tr(idx == 0 ? "gui.settledlands.tip448.reward_pool.create_item"
                            : "gui.settledlands.tip448.reward_pool.pick_item"),
                    TrialModule447.color(TrialModule447.SLOT_REWARDS));
        }
        return tip("drop:gen:" + lk + ":" + idx, entryText,
                tr("gui.settledlands.tip448.dropdown_item"), DEFAULT_ACCENT);
    }

    // ---- 3. Widgets (top bar & inspector) -----------------------------------

    private static Tip resolveWidgetTip(NpcDialogueGraphScreen screen, ScreenState st,
            TrackedWidget tw, NpcDialogueGraph graph) {
        Node node = st.selectedNode != null ? st.selectedNode
                : (graph != null && st.selectedId >= 0 ? graph.get(st.selectedId) : null);

        // Top bar buttons (non-inspector)
        if (tw.inspectorIdx < 0 && tw.kind == 0) {
            return resolveTopBarTip(screen, tw);
        }

        // Gear button on LootPool / Trial Mob Pool / Trial Reward Pool nodes
        if (tw.kind == 5) {
            int mSlot = TrialModule447.slotOf(node);
            if (mSlot == TrialModule447.SLOT_MOBS) {
                return tip("w:mp_gear", tr(TrialModule447.titleKey(mSlot)),
                        tr("gui.settledlands.tip448.w.mob_pool_gear"), TrialMobPool449.ACCENT_COLOR);
            }
            if (mSlot == TrialModule447.SLOT_REWARDS) {
                return tip("w:rp_gear", tr(TrialModule447.titleKey(mSlot)),
                        tr("gui.settledlands.tip448.w.reward_pool_gear"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            }
            return tip("w:lp_gear", tr("gui.settledlands.loot_pool.title"),
                    tr("gui.settledlands.tip448.w.loot_pool_gear"), LootPool436.ACCENT_COLOR);
        }

        // Sliders (Door / Chest lock panels)
        if (tw.kind == 4) {
            return resolveSliderTip(node, tw);
        }

        // Dropdowns
        if (tw.kind == 3) {
            return resolveDropdownWidgetTip(node, tw);
        }

        // Specialized node inspectors (state-aware!)
        if (TrialModule447.isModuleNode(node)) {
            return resolveModuleWidgetTip(graph, node, tw);
        }
        if (TrialGraph446.isTrialNode(node)) {
            return resolveTrialHubWidgetTip(tw);
        }
        if (TrialControlNode446.isControlNode(node)) {
            return resolveTrialCtrlWidgetTip(node, tw);
        }
        if (DoorGraph382.isDoorNode(node)) {
            return resolveDoorWidgetTip(node, tw);
        }
        if (ChestGraph396.isChestNode(node)) {
            return resolveChestWidgetTip(node, tw);
        }
        if (PursuitGraphNode272.isPursuitNode(node)) {
            return resolvePursuitWidgetTip(node, tw);
        }
        if (TeleportNode424.isTeleportNode(node)) {
            return resolveTeleportWidgetTip(tw);
        }
        if (LootPool436.isLootPoolNode(node)) {
            return resolveLootPoolWidgetTip(tw);
        }
        if (node != null && node.type == BlockName359.TYPE) {
            return resolveBlockNameWidgetTip(tw);
        }

        // Stock inspector buttons & fields
        return resolveStockWidgetTip(node, tw);
    }

    private static Tip resolveTopBarTip(NpcDialogueGraphScreen screen, TrackedWidget tw) {
        String k = tw.rawKey;
        if ("gui.settledlands.graph.undo".equals(k)) {
            return tip("top:undo", tr(k), tr("gui.settledlands.tip448.top.undo"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.redo".equals(k)) {
            return tip("top:redo", tr(k), tr("gui.settledlands.tip448.top.redo"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.tab_main".equals(k)) {
            return tip("top:tab_main", tr(k), tr("gui.settledlands.tip448.top.tab_main"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.tab_walk".equals(k)) {
            return tip("top:tab_walk", tr(k), tr("gui.settledlands.tip448.top.tab_walk"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.tab_anim".equals(k)) {
            return tip("top:tab_anim", tr(k), tr("gui.settledlands.tip448.top.tab_anim"), DEFAULT_ACCENT);
        }
        return null;
    }

    private static Tip resolveModuleWidgetTip(NpcDialogueGraph graph, Node node, TrackedWidget tw) {
        int slot = TrialModule447.slotOf(node);
        int col = TrialModule447.color(slot);
        String k = tw.rawKey;
        String lk = tw.labelKey;

        // Shared footer buttons on every module
        if ("gui.settledlands.trial.mod.btn_attach".equals(k)
                || "gui.settledlands.trial.mod.btn_unlink".equals(k)) {
            boolean linked = graph != null && node.next >= 0 && TrialGraph446.isTrialNode(graph.get(node.next));
            return tip("w:mod_link",
                    tr(linked ? "gui.settledlands.trial.mod.btn_unlink" : "gui.settledlands.trial.mod.btn_attach"),
                    tr(linked ? "gui.settledlands.tip448.mod.unlink_state" : "gui.settledlands.tip448.mod.attach_state"),
                    col);
        }
        if ("gui.settledlands.graph.delete".equals(k)) {
            return tip("w:del", tr(k), tr("gui.settledlands.tip448.mod.delete"), WARN_ACCENT);
        }

        if (slot == TrialModule447.SLOT_MODE && tw.kind == 0) {
            // Mode toggle (index 0) and Scale toggle (index 1) before Attach/Delete
            TrialModule447.ModeData d = TrialModule447.readMode(node);
            if (isScaleText(k)) {
                return tip("w:mod_scale", TrialModuleClient447.scaleButtonText(node),
                        tr(d.scaleWithPlayers ? "gui.settledlands.tip448.cfg.scale_on"
                                : "gui.settledlands.tip448.cfg.scale_off"), col);
            }
            return tip("w:mod_mode", TrialModuleClient447.modeButtonText(node),
                    modeStateDescription(d.mode, 1.5f), modeAccent(d.mode));
        }

        if (slot == TrialModule447.SLOT_OMINOUS && tw.kind == 0) {
            TrialModule447.OminousData d = TrialModule447.readOminous(node);
            if (matchesPrefix(k, "gui.settledlands.trial.cfg.ominous_equip")) {
                return tip("w:om_eq", TrialModuleClient447.ominousToggleLabel(node, 0),
                        tr(d.equipment ? "gui.settledlands.tip448.om.equip_on"
                                : "gui.settledlands.tip448.om.equip_off"), col);
            }
            if (matchesPrefix(k, "gui.settledlands.trial.cfg.ominous_hazards")) {
                return tip("w:om_haz", TrialModuleClient447.ominousToggleLabel(node, 1),
                        tr(d.hazards ? "gui.settledlands.tip448.om.hazards_on"
                                : "gui.settledlands.tip448.om.hazards_off"), col);
            }
            if (matchesPrefix(k, "gui.settledlands.trial.cfg.ominous_keys")) {
                return tip("w:om_keys", TrialModuleClient447.ominousToggleLabel(node, 2),
                        tr(d.keyConversion ? "gui.settledlands.tip448.om.keys_on"
                                : "gui.settledlands.tip448.om.keys_off"), col);
            }
        }

        if (slot == TrialModule447.SLOT_PROTECT && tw.kind == 0) {
            TrialModule447.ProtectData d = TrialModule447.readProtect(node);
            if (matchesPrefix(k, "gui.settledlands.trial.cfg.nobreak")) {
                return tip("w:pr_nb", TrialModuleClient447.protectToggleLabel(node, 0),
                        tr(d.noBreakEnabled ? "gui.settledlands.tip448.pr.nobreak_on"
                                : "gui.settledlands.tip448.pr.nobreak_off"), col);
            }
            if (matchesPrefix(k, "gui.settledlands.trial.cfg.destruct")) {
                return tip("w:pr_sd", TrialModuleClient447.protectToggleLabel(node, 1),
                        tr(d.selfDestruct ? "gui.settledlands.tip448.pr.destruct_on"
                                : "gui.settledlands.tip448.pr.destruct_off"), col);
            }
        }

        if (slot == TrialModule447.SLOT_MOBS || slot == TrialModule447.SLOT_REWARDS) {
            if ("gui.settledlands.trial.mod.add_mob_catalog".equals(k)) {
                return tip("w:add_mob_cat", tr(k), tr("gui.settledlands.tip448.mod.add_mob_cat"), col);
            }
            if ("gui.settledlands.trial.mod.open_mob_list".equals(k)) {
                return tip("w:open_mobs", tr(k), tr("gui.settledlands.tip448.mod.open_mob_list"), col);
            }
            if ("gui.settledlands.trial.mod.add_reward_catalog".equals(k)) {
                return tip("w:add_rew_cat", tr(k), tr("gui.settledlands.tip448.mod.add_rew_cat"), col);
            }
            if ("gui.settledlands.trial.mod.open_reward_list".equals(k)) {
                return tip("w:open_rew", tr(k), tr("gui.settledlands.tip448.mod.open_rew_list"), col);
            }
            if ("gui.settledlands.trial.cfg.pick".equals(k)) {
                return tip("w:pick_row:" + tw.y, tr(k),
                        tr(slot == TrialModule447.SLOT_MOBS
                                ? "gui.settledlands.tip448.mod.pick_mob"
                                : "gui.settledlands.tip448.mod.pick_reward"), col);
            }
            if ("-".equals(k)) {
                return tip("w:del_row:" + tw.y, tr("gui.settledlands.graph.delete"),
                        tr("gui.settledlands.tip448.mod.remove_entry"), WARN_ACCENT);
            }
            if (tw.kind == 1) {
                return tip("w:row_box:" + tw.x + ":" + tw.y, tr(TrialModule447.titleKey(slot)),
                        tr(slot == TrialModule447.SLOT_MOBS
                                ? "gui.settledlands.tip448.mod.weight_mob"
                                : "gui.settledlands.tip448.mod.reward_box"), col);
            }
        }

        // Numeric scalar fields mapped via labelKey
        if (!lk.isEmpty()) {
            String tk = trialFieldTipKey(lk);
            if (!tk.isEmpty()) {
                return tip("w:mod_fld:" + lk, tr(lk), tr(tk), col);
            }
        }
        return null;
    }

    private static Tip resolveTrialHubWidgetTip(TrackedWidget tw) {
        if ("gui.settledlands.trial.btn_restore_modules".equals(tw.rawKey)) {
            return tip("w:restore_mods", tr(tw.rawKey),
                    tr("gui.settledlands.tip448.hub.restore_modules"), TrialGraph446.ACCENT_COLOR);
        }
        if ("gui.settledlands.trial.btn_clear_sockets".equals(tw.rawKey)) {
            return tip("w:clear_socks", tr(tw.rawKey),
                    tr("gui.settledlands.tip448.hub.clear_sockets"), WARN_ACCENT);
        }
        return null;
    }

    private static Tip resolveTrialCtrlWidgetTip(Node node, TrackedWidget tw) {
        TrialControlNode446.Parsed p = TrialControlNode446.parsedOrDefault(node);
        int col = TrialControlNode446.ACCENT_COLOR;
        String k = tw.rawKey;
        if ("gui.settledlands.trial.btn_configure".equals(k)) {
            return tip("w:tc_cfg", tr(k), tr("gui.settledlands.tip448.tc.configure"), col);
        }
        if ("gui.settledlands.trial_ctl.kill_mobs_yes".equals(k)
                || "gui.settledlands.trial_ctl.kill_mobs_no".equals(k)) {
            return tip("w:tc_kill",
                    tr(p.killMobs ? "gui.settledlands.trial_ctl.kill_mobs_yes"
                            : "gui.settledlands.trial_ctl.kill_mobs_no"),
                    tr(p.killMobs ? "gui.settledlands.tip448.tc.kill_yes"
                            : "gui.settledlands.tip448.tc.kill_no"), col);
        }
        if ("gui.settledlands.trial_ctl.btn_here".equals(k)) {
            return tip("w:tc_here", tr(k), tr("gui.settledlands.tip448.tc.here"), col);
        }
        if ("gui.settledlands.teleport.x".equals(tw.labelKey)
                || "gui.settledlands.teleport.y".equals(tw.labelKey)
                || "gui.settledlands.teleport.z".equals(tw.labelKey)) {
            return tip("w:tc_coord_" + tw.labelKey,
                    tr("gui.settledlands.trial_ctl.coords_hint"),
                    tr("gui.settledlands.tip448.tc.coords"), col);
        }
        return resolveCommonActionButtons(k, col);
    }

    private static Tip resolveDoorWidgetTip(Node node, TrackedWidget tw) {
        if ("gui.settledlands.door.seal".equals(tw.rawKey)) {
            boolean sealed = DoorGraph382.sealed(node);
            return tip("w:door_seal",
                    tr(sealed ? "gui.settledlands.door.sealed" : "gui.settledlands.door.unsealed"),
                    tr(sealed ? "gui.settledlands.tip448.door.sealed"
                            : "gui.settledlands.tip448.door.unsealed"), DEFAULT_ACCENT);
        }
        if (dev.settledlands.npc.NpcIllegalHack417.ILLEGAL_KEY.equals(tw.rawKey)) {
            boolean illegal = DoorGraph382.illegal(node);
            return tip("w:door_ill", tr(DoorGraph382.illegalKey(node)),
                    tr(illegal ? "gui.settledlands.tip448.hack.illegal_on"
                            : "gui.settledlands.tip448.hack.illegal_off"), WARN_ACCENT);
        }
        return null;
    }

    private static Tip resolveChestWidgetTip(Node node, TrackedWidget tw) {
        if ("gui.settledlands.chest.lock".equals(tw.rawKey)) {
            boolean locked = ChestLock397.locked(node);
            return tip("w:chest_lock",
                    tr(ChestLock397.stateKey(node)),
                    tr(locked ? "gui.settledlands.tip448.chest.locked"
                            : "gui.settledlands.tip448.chest.unlocked"), DEFAULT_ACCENT);
        }
        if (dev.settledlands.npc.NpcIllegalHack417.ILLEGAL_KEY.equals(tw.rawKey)) {
            boolean illegal = ChestLock397.illegal(node);
            return tip("w:chest_ill", tr(ChestLock397.illegalKey(node)),
                    tr(illegal ? "gui.settledlands.tip448.hack.illegal_on"
                            : "gui.settledlands.tip448.hack.illegal_off"), WARN_ACCENT);
        }
        return null;
    }

    private static Tip resolveSliderTip(Node node, TrackedWidget tw) {
        String tag = tw.explicitTag;
        boolean door = DoorGraph382.isDoorNode(node);
        boolean chest = ChestGraph396.isChestNode(node);
        if (!door && !chest) return null;
        int idx = "slider_0".equals(tag) ? 0 : ("slider_1".equals(tag) ? 1 : 2);
        if (idx == 0) {
            int val = door ? DoorGraph382.keys(node) : ChestLock397.keys(node);
            return tip("w:sld_keys",
                    DoorGraphClient382.safe("gui.settledlands.door.count", Integer.toString(val)),
                    tr("gui.settledlands.tip448.hack.slider_keys"), DEFAULT_ACCENT);
        }
        if (idx == 1) {
            int val = door ? DoorGraph382.speed(node) : ChestLock397.seconds(node);
            return tip("w:sld_spd",
                    DoorGraphClient382.safe("gui.settledlands.door.speed", Integer.toString(val)),
                    tr("gui.settledlands.tip448.hack.slider_speed"), DEFAULT_ACCENT);
        }
        int diff = door ? DoorGraph382.difficulty(node) : ChestLock397.difficulty(node);
        String rowsKey = diff <= 1 ? "gui.settledlands.door.rows1"
                : (diff == 2 ? "gui.settledlands.door.rows2" : "gui.settledlands.door.rows3");
        return tip("w:sld_diff",
                DoorGraphClient382.safe("gui.settledlands.door.difficulty", Integer.toString(diff))
                        + " (" + tr(rowsKey) + ")",
                tr("gui.settledlands.tip448.hack.slider_diff_" + Math.max(1, Math.min(3, diff))),
                DEFAULT_ACCENT);
    }

    private static Tip resolvePursuitWidgetTip(Node node, TrackedWidget tw) {
        int col = 0xFFE69138;
        String k = tw.rawKey;
        if ("gui.settledlands.pursuit.target_npc_button".equals(k)
                || "gui.settledlands.pursuit.target_player_button".equals(k)) {
            boolean isNpc = PursuitGraphNode272.targetTypeIndex(node) == PursuitGraphNode272.TARGET_NPC;
            return tip("w:pur_type",
                    tr(PursuitGraphNode272.targetTypeButtonKey(node)),
                    tr(isNpc ? "gui.settledlands.tip448.pursuit.type_npc"
                            : "gui.settledlands.tip448.pursuit.type_player"), col);
        }
        if ("gui.settledlands.pursuit.stop_toggle".equals(k)
                || "gui.settledlands.pursuit.stop_toggle_on".equals(k)) {
            boolean stop = PursuitGraphNode272.stopFlag(node);
            return tip("w:pur_stop",
                    tr(PursuitGraphNode272.stopLabelKey(node)),
                    tr(stop ? "gui.settledlands.tip448.pursuit.stop_on"
                            : "gui.settledlands.tip448.pursuit.stop_off"), col);
        }
        String lk = tw.labelKey;
        if ("gui.settledlands.pursuit.target_value".equals(lk)) {
            boolean isNpc = PursuitGraphNode272.targetTypeIndex(node) == PursuitGraphNode272.TARGET_NPC;
            return tip("w:pur_val", tr(lk),
                    tr(isNpc ? "gui.settledlands.tip448.pursuit.val_npc"
                            : "gui.settledlands.tip448.pursuit.val_player"), col);
        }
        if ("gui.settledlands.pursuit.keep_distance".equals(lk)) {
            return tip("w:pur_keep", tr(lk), tr("gui.settledlands.tip448.pursuit.keep"), col);
        }
        if ("gui.settledlands.pursuit.catch_up".equals(lk)) {
            return tip("w:pur_cu", tr(lk), tr("gui.settledlands.tip448.pursuit.catch_up"), col);
        }
        if ("gui.settledlands.pursuit.catch_up_speed".equals(lk)) {
            return tip("w:pur_spd", tr(lk), tr("gui.settledlands.tip448.pursuit.speed"), col);
        }
        return resolveCommonActionButtons(k, col);
    }

    private static Tip resolveTeleportWidgetTip(TrackedWidget tw) {
        int col = TeleportNode424.ACCENT_COLOR;
        if ("gui.settledlands.teleport.x".equals(tw.labelKey)
                || "gui.settledlands.teleport.y".equals(tw.labelKey)
                || "gui.settledlands.teleport.z".equals(tw.labelKey)) {
            return tip("w:tp_coord_" + tw.labelKey,
                    tr("gui.settledlands.teleport.coords_hint"),
                    tr("gui.settledlands.tip448.tp.coords"), col);
        }
        return resolveCommonActionButtons(tw.rawKey, col);
    }

    private static Tip resolveLootPoolWidgetTip(TrackedWidget tw) {
        int col = LootPool436.ACCENT_COLOR;
        String lk = tw.labelKey;
        if ("gui.settledlands.loot_pool.max_rarity".equals(lk)) {
            return tip("w:lp_rar", tr(lk), tr("gui.settledlands.tip448.lp.rarity"), col);
        }
        if ("gui.settledlands.loot_pool.max_weight".equals(lk)) {
            return tip("w:lp_wgt", tr(lk), tr("gui.settledlands.tip448.lp.weight"), col);
        }
        if ("gui.settledlands.loot_pool.rolls".equals(lk)) {
            return tip("w:lp_rls", tr(lk), tr("gui.settledlands.tip448.lp.rolls"), col);
        }
        return resolveCommonActionButtons(tw.rawKey, col);
    }

    private static Tip resolveBlockNameWidgetTip(TrackedWidget tw) {
        if (BlockName359.FIELD_KEY.equals(tw.labelKey)) {
            return tip("w:blk_name", tr(BlockName359.FIELD_KEY),
                    tr("gui.settledlands.tip448.blk.name"), DEFAULT_ACCENT);
        }
        return null;
    }

    private static Tip resolveDropdownWidgetTip(Node node, TrackedWidget tw) {
        String lk = tw.labelKey;
        if ("gui.settledlands.trial_ctl.op_prefix".equals(lk)) {
            TrialControlNode446.Parsed p = TrialControlNode446.parsedOrDefault(node);
            return tip("w:drop_op", tr(TrialControlNode446.opKey(p.op)),
                    tr("gui.settledlands.tip448.op." + trialOpTag(p.op)),
                    TrialControlNode446.ACCENT_COLOR);
        }
        if ("gui.settledlands.teleport.world".equals(lk)) {
            return tip("w:drop_world", tr(lk),
                    tr("gui.settledlands.tip448.drop.world"), TeleportNode424.ACCENT_COLOR);
        }
        if ("gui.settledlands.loot_pool.file".equals(lk)) {
            return tip("w:drop_pool", tr(lk),
                    tr("gui.settledlands.tip448.drop.loot_pool"), LootPool436.ACCENT_COLOR);
        }
        if ("gui.settledlands.trial.mob_pool.file".equals(lk)) {
            return tip("w:drop_mob_pool", tr(lk),
                    tr("gui.settledlands.tip448.drop.mob_pool"), TrialMobPool449.ACCENT_COLOR);
        }
        if ("gui.settledlands.trial.reward_pool.file".equals(lk)) {
            return tip("w:drop_rew_pool", tr(lk),
                    tr("gui.settledlands.tip448.drop.reward_pool"),
                    TrialModule447.color(TrialModule447.SLOT_REWARDS));
        }
        if ("gui.settledlands.var453.op_label".equals(lk) && node != null) {
            return tip("w:drop_v453op", tr(lk),
                    tr("gui.settledlands.tip448.var453_op"), VarDataNode453.ACCENT_COLOR);
        }
        if ("gui.settledlands.graph.var_op".equals(lk) && node != null) {
            return tip("w:drop_varop", tr(lk),
                    tr("gui.settledlands.tip448.varop." + Math.max(0, Math.min(5, node.varOp))),
                    DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.trigger_presets".equals(lk)) {
            return tip("w:drop_preset", tr(lk),
                    tr("gui.settledlands.tip448.drop.presets"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.check_target".equals(lk)
                || "gui.settledlands.graph.radius_target".equals(lk)) {
            return tip("w:drop_tgt", tr(lk),
                    tr("gui.settledlands.tip448.drop.target"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.damage_source".equals(lk)) {
            return tip("w:drop_dmg", tr(lk),
                    tr("gui.settledlands.tip448.drop.damage"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.time_mode".equals(lk)) {
            return tip("w:drop_time", tr(lk),
                    tr("gui.settledlands.tip448.drop.time"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.anim_select".equals(lk)) {
            return tip("w:drop_anim", tr(lk),
                    tr("gui.settledlands.tip448.drop.anim"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.anim_bone".equals(lk)) {
            return tip("w:drop_bone", tr(lk),
                    tr("gui.settledlands.tip448.drop.bone"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.cutscene_select".equals(lk)) {
            return tip("w:drop_cut", tr(lk),
                    tr("gui.settledlands.tip448.drop.cutscene"), DEFAULT_ACCENT);
        }
        if ("gui.settledlands.graph.mobility_group".equals(lk)) {
            return tip("w:drop_mob", tr(lk),
                    tr("gui.settledlands.tip448.drop.mobility"), DEFAULT_ACCENT);
        }
        if (BlockName359.COLOR_KEY.equals(lk)) {
            return tip("w:drop_bcol", tr(lk),
                    tr("gui.settledlands.tip448.drop.block_color"), DEFAULT_ACCENT);
        }
        return null;
    }

    private static Tip resolveStockWidgetTip(Node node, TrackedWidget tw) {
        if (tw.kind == 0) {
            String k = tw.rawKey;
            if ("gui.settledlands.graph.local_on".equals(k)
                    || "gui.settledlands.graph.local_off".equals(k)) {
                boolean local = node != null ? node.varLocal : "gui.settledlands.graph.local_on".equals(k);
                return tip("w:stk_local",
                        tr(local ? "gui.settledlands.graph.local_on" : "gui.settledlands.graph.local_off"),
                        tr(local ? "gui.settledlands.tip448.stk.local_on"
                                : "gui.settledlands.tip448.stk.local_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.shared_on".equals(k)
                    || "gui.settledlands.graph.shared_off".equals(k)) {
                boolean sh = node != null ? node.pointsShared : "gui.settledlands.graph.shared_on".equals(k);
                return tip("w:stk_shared",
                        tr(sh ? "gui.settledlands.graph.shared_on" : "gui.settledlands.graph.shared_off"),
                        tr(sh ? "gui.settledlands.tip448.stk.shared_on"
                                : "gui.settledlands.tip448.stk.shared_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.timer_repeat_on".equals(k)
                    || "gui.settledlands.graph.timer_repeat_off".equals(k)) {
                boolean rep = node != null ? node.timerRepeat : "gui.settledlands.graph.timer_repeat_on".equals(k);
                return tip("w:stk_rep",
                        tr(rep ? "gui.settledlands.graph.timer_repeat_on"
                                : "gui.settledlands.graph.timer_repeat_off"),
                        tr(rep ? "gui.settledlands.tip448.stk.repeat_on"
                                : "gui.settledlands.tip448.stk.repeat_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.move_escort_on".equals(k)
                    || "gui.settledlands.graph.move_escort_off".equals(k)) {
                boolean esc = node != null ? node.moveEscort : "gui.settledlands.graph.move_escort_on".equals(k);
                return tip("w:stk_esc",
                        tr(esc ? "gui.settledlands.graph.move_escort_on"
                                : "gui.settledlands.graph.move_escort_off"),
                        tr(esc ? "gui.settledlands.tip448.stk.escort_on"
                                : "gui.settledlands.tip448.stk.escort_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.cutscene_line_on".equals(k)
                    || "gui.settledlands.graph.cutscene_line_off".equals(k)) {
                boolean cl = node != null ? node.cutsceneLine : "gui.settledlands.graph.cutscene_line_on".equals(k);
                return tip("w:stk_cl",
                        tr(cl ? "gui.settledlands.graph.cutscene_line_on"
                                : "gui.settledlands.graph.cutscene_line_off"),
                        tr(cl ? "gui.settledlands.tip448.stk.cutline_on"
                                : "gui.settledlands.tip448.stk.cutline_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.anim_repeat_on".equals(k)
                    || "gui.settledlands.graph.anim_repeat_off".equals(k)) {
                boolean al = node != null ? node.animLoop : "gui.settledlands.graph.anim_repeat_on".equals(k);
                return tip("w:stk_aloop",
                        tr(al ? "gui.settledlands.graph.anim_repeat_on"
                                : "gui.settledlands.graph.anim_repeat_off"),
                        tr(al ? "gui.settledlands.tip448.stk.anim_loop_on"
                                : "gui.settledlands.tip448.stk.anim_loop_off"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.entry".equals(k)) {
                return tip("w:stk_entry", tr(k), tr("gui.settledlands.tip448.stk.entry"), NORMAL_ACCENT);
            }
            if ("gui.settledlands.graph.add_variant".equals(k)) {
                return tip("w:stk_addv", tr(k), tr("gui.settledlands.tip448.stk.add_variant"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.del_variant".equals(k)) {
                return tip("w:stk_delv", tr(k), tr("gui.settledlands.tip448.stk.del_variant"), WARN_ACCENT);
            }
            if ("gui.settledlands.graph.cutscene_studio".equals(k)) {
                return tip("w:stk_studio", tr(k), tr("gui.settledlands.tip448.stk.cutscene_studio"), DEFAULT_ACCENT);
            }
            if ("gui.settledlands.graph.cutscene_del_one".equals(k)
                    || "gui.settledlands.graph.cutscene_del_all".equals(k)
                    || "gui.settledlands.graph.cutscene_del_confirm".equals(k)) {
                return tip("w:stk_cdel_" + k, tr(k), tr("gui.settledlands.tip448.stk.cutscene_del"), WARN_ACCENT);
            }
            if ("gui.settledlands.graph.unlink_if".equals(k)) {
                return tip("w:stk_uif", tr(k), tr("gui.settledlands.tip448.stk.unlink_if"), WARN_ACCENT);
            }
            if ("gui.settledlands.block.visible".equals(k)) {
                boolean vis = node != null && dev.settledlands.npc.RadiusPass354.visible(node);
                return tip("w:rad_vis",
                        tr(vis ? "gui.settledlands.block.visible_on"
                                : "gui.settledlands.block.visible_off"),
                        tr(vis ? "gui.settledlands.tip448.rad.visible_on"
                                : "gui.settledlands.tip448.rad.visible_off"),
                        vis ? 0xFF4ADE80 : DEFAULT_ACCENT);
            }
            if ("gui.settledlands.radius_shape_btn".equals(k)) {
                boolean sq = node != null && dev.settledlands.npc.RadiusZone456.isSquare(node);
                return tip("w:rad_shape",
                        tr(sq ? "gui.settledlands.radius_shape_square"
                                : "gui.settledlands.radius_shape_circle"),
                        tr(sq ? "gui.settledlands.tip448.rad.shape_square"
                                : "gui.settledlands.tip448.rad.shape_circle"),
                        sq ? 0xFF4ADE80 : 0xFF60A5FA);
            }
            return resolveCommonActionButtons(k, DEFAULT_ACCENT);
        }

        // Input fields matched by labelKey
        String lk = tw.labelKey;
        if (lk.isEmpty() && tw.kind == 2) {
            return tip("w:stk_action", tr("gui.settledlands.graph.action"),
                    tr("gui.settledlands.tip448.fld.action"), DEFAULT_ACCENT);
        }
        String fTip = stockFieldTipKey(lk);
        if (!fTip.isEmpty()) {
            return tip("w:stk_fld:" + lk, tr(lk), tr(fTip), DEFAULT_ACCENT);
        }
        return null;
    }

    private static Tip resolveCommonActionButtons(String k, int accent) {
        if ("gui.settledlands.graph.unlink".equals(k)) {
            return tip("w:unlink", tr(k), tr("gui.settledlands.tip448.w.unlink"), WARN_ACCENT);
        }
        if ("gui.settledlands.graph.delete".equals(k)) {
            return tip("w:delete", tr(k), tr("gui.settledlands.tip448.w.delete"), WARN_ACCENT);
        }
        return null;
    }

    private static Tip resolveInspectorLabelTip(Node node, String labelKey, int y) {
        if (TrialGraph446.isTrialNode(node)) {
            if ("gui.settledlands.trial.hint_start".equals(labelKey)) {
                return tip("lbl:t_start", tr("gui.settledlands.trial.pin_start"),
                        tr("gui.settledlands.tip448.pin.trial_start"), TrialCard446.START_COLOR);
            }
            if ("gui.settledlands.trial.hint_clear".equals(labelKey)) {
                return tip("lbl:t_clear", tr("gui.settledlands.trial.pin_clear"),
                        tr("gui.settledlands.tip448.pin.trial_clear"), TrialGraph446.ACCENT_COLOR);
            }
            if ("gui.settledlands.trial.hint_destroy".equals(labelKey)) {
                return tip("lbl:t_dest", tr("gui.settledlands.trial.pin_destroy"),
                        tr("gui.settledlands.tip448.pin.trial_destroy"), WARN_ACCENT);
            }
            if ("gui.settledlands.trial.hint_player".equals(labelKey)) {
                return tip("lbl:t_pl", tr("gui.settledlands.trial.pin_player"),
                        tr("gui.settledlands.tip448.pin.trial_player"), 0xFF55AAFF);
            }
        }
        String tk = trialFieldTipKey(labelKey);
        if (!tk.isEmpty()) {
            return tip("lbl:f_" + labelKey, tr(labelKey), tr(tk), TrialGraph446.ACCENT_COLOR);
        }
        String sk = stockFieldTipKey(labelKey);
        if (!sk.isEmpty()) {
            return tip("lbl:s_" + labelKey, tr(labelKey), tr(sk), DEFAULT_ACCENT);
        }
        return null;
    }

    // ---- 4. Canvas pins & sockets -------------------------------------------

    private static Tip resolveCanvasPinTip(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mx, double my) {
        final double rSq = 81.0D; // 9 px radius
        for (Node n : graph.nodes.values()) {
            if (n == null) continue;
            int w = GraphCardDesc448.cardWidth(n);
            int h = GraphCardDesc448.cardHeight(n);

            // Trial Hub 8 left module slots + 4 right sockets
            if (TrialGraph446.isTrialNode(n)) {
                double lx = TrialCard446.inputPinScreenX(canvas, n);
                for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
                    double ly = TrialCard446.inputPinScreenY(canvas, n, s);
                    if (distSq(mx, my, lx, ly) <= rSq) {
                        Node wired = TrialModule447.connectedModule(graph, n, s);
                        String title = tr(TrialModule447.titleKey(s));
                        String body = tr("gui.settledlands.tip448.pin.slot_" + TrialModule447.kindOf(s))
                                + "\n" + tr(wired != null
                                        ? "gui.settledlands.tip448.pin.slot_wired"
                                        : "gui.settledlands.tip448.pin.slot_default");
                        return tip("pin:" + n.id + ":slot_" + s, title, body, TrialModule447.color(s));
                    }
                }
                double rx = canvas.screenX(n.x + w);
                if (distSq(mx, my, rx, canvas.screenY(n.y + TrialCard446.START_PIN_Y)) <= rSq) {
                    return tip("pin:" + n.id + ":t_start", tr("gui.settledlands.trial.pin_start"),
                            tr("gui.settledlands.tip448.pin.trial_start"), TrialCard446.START_COLOR);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.FIRST_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":t_clear", tr("gui.settledlands.trial.pin_clear"),
                            tr("gui.settledlands.tip448.pin.trial_clear"), TrialGraph446.ACCENT_COLOR);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.SECOND_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":t_dest", tr("gui.settledlands.trial.pin_destroy"),
                            tr("gui.settledlands.tip448.pin.trial_destroy"), WARN_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + PursuitGraphNode272.TARGET_PIN_Y)) <= rSq) {
                    return tip("pin:" + n.id + ":t_pl", tr("gui.settledlands.trial.pin_player"),
                            tr("gui.settledlands.tip448.pin.trial_player"), 0xFF55AAFF);
                }
            }

            // Trial Module input socket (mobs / rewards) & right output pin
            if (TrialModule447.isModuleNode(n)) {
                int slot = TrialModule447.slotOf(n);
                if (TrialModule447.supportsConditionalInput(n)) {
                    double ix = TrialModuleCard447.inputPinScreenX(canvas, n);
                    double iy = TrialModuleCard447.inputPinScreenY(canvas, n);
                    if (distSq(mx, my, ix, iy) <= rSq) {
                        boolean hasIn = TrialModule447.hasIncomingControl(graph, n);
                        return tip("pin:" + n.id + ":mod_in",
                                tr("gui.settledlands.tip448.pin.mod_in_title"),
                                tr(hasIn ? "gui.settledlands.tip448.pin.mod_in_wired"
                                        : "gui.settledlands.tip448.pin.mod_in_free"),
                                hasIn ? 0xFF56D38C : TrialModule447.color(slot));
                    }
                }
                double ox = TrialModuleCard447.outputPinScreenX(canvas, n);
                double oy = TrialModuleCard447.outputPinScreenY(canvas, n);
                if (distSq(mx, my, ox, oy) <= rSq) {
                    boolean wiredHub = n.next >= 0 && TrialGraph446.isTrialNode(graph.get(n.next));
                    boolean wiredChain = !wiredHub && n.next >= 0 && graph.get(n.next) != null;
                    String key = wiredHub ? "gui.settledlands.tip448.pin.mod_out_wired"
                            : (wiredChain ? "gui.settledlands.tip448.pin.mod_out_chain"
                                    : "gui.settledlands.tip448.pin.mod_out_free");
                    return tip("pin:" + n.id + ":mod_out",
                            tr(TrialModule447.titleKey(slot)), tr(key), TrialModule447.color(slot));
                }
            }

            // Door right pins
            if (DoorGraph382.isDoorNode(n)) {
                double rx = canvas.screenX(n.x + w);
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.FIRST_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":door_hack", tr("gui.settledlands.door.pin_out"),
                            tr("gui.settledlands.tip448.pin.door_hack"), DEFAULT_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.SECOND_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":door_open", tr("gui.settledlands.door.pin_open"),
                            tr("gui.settledlands.tip448.pin.door_open"), DEFAULT_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + PursuitGraphNode272.TARGET_PIN_Y)) <= rSq) {
                    return tip("pin:" + n.id + ":door_who", tr("gui.settledlands.door.pin_who"),
                            tr("gui.settledlands.tip448.pin.door_who"), 0xFF55AAFF);
                }
            }

            // Chest right pins
            if (ChestGraph396.isChestNode(n)) {
                double rx = canvas.screenX(n.x + w);
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.FIRST_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":chest_open", tr("gui.settledlands.chest.pin_out"),
                            tr("gui.settledlands.tip448.pin.chest_open"), DEFAULT_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + CardEventPin398.SECOND_ROW)) <= rSq) {
                    return tip("pin:" + n.id + ":chest_hack", tr("gui.settledlands.chest.pin_hack"),
                            tr("gui.settledlands.tip448.pin.chest_hack"), DEFAULT_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + PursuitGraphNode272.TARGET_PIN_Y)) <= rSq) {
                    return tip("pin:" + n.id + ":chest_who", tr("gui.settledlands.chest.pin_who"),
                            tr("gui.settledlands.tip448.pin.chest_who"), 0xFF55AAFF);
                }
            }

            // If node outputs + blue input pin
            if (n.type == NpcDialogueGraph.IF) {
                double rx = canvas.screenX(n.x + w);
                if (distSq(mx, my, rx, canvas.screenY(n.y + h / 2.0D - 14.0D)) <= rSq) {
                    return tip("pin:" + n.id + ":if_t", tr("gui.settledlands.graph.if_true"),
                            tr("gui.settledlands.tip448.pin.if_true"), NORMAL_ACCENT);
                }
                if (distSq(mx, my, rx, canvas.screenY(n.y + h / 2.0D + 14.0D)) <= rSq) {
                    return tip("pin:" + n.id + ":if_f", tr("gui.settledlands.graph.if_false"),
                            tr("gui.settledlands.tip448.pin.if_false"), WARN_ACCENT);
                }
                double lx = canvas.screenX(n.x);
                if (distSq(mx, my, lx, canvas.screenY(n.y + VarDataCard453.IF_ORANGE_Y)) <= rSq) {
                    return tip("pin:" + n.id + ":if_orange",
                            tr("gui.settledlands.tip448.pin.var_orange_title"),
                            tr("gui.settledlands.tip448.pin.var_orange_in"),
                            VarDataNode453.PIN_COLOR);
                }
                if (distSq(mx, my, lx, canvas.screenY(n.y + h - 8.0D)) <= rSq) {
                    return tip("pin:" + n.id + ":var_in", tr("gui.settledlands.graph.var_input"),
                            tr("gui.settledlands.tip448.pin.var_input"), 0xFF55AAFF);
                }
            }

            // Orange data output/input pins (VarDataNode453 & VARIABLE)
            if (VarDataCard453.hitOrangeOut(canvas, n, mx, my)) {
                return tip("pin:" + n.id + ":o_out",
                        tr("gui.settledlands.tip448.pin.var453_out_title"),
                        tr("gui.settledlands.tip448.pin.var453_out"),
                        VarDataNode453.PIN_COLOR);
            }
            if (n.type == NpcDialogueGraph.VARIABLE || VarDataNode453.isVarDataNode(n)) {
                if (VarDataCard453.hitOrangeIn(canvas, n, mx, my)) {
                    return tip("pin:" + n.id + ":v_orange",
                            tr("gui.settledlands.tip448.pin.var_orange_title"),
                            tr("gui.settledlands.tip448.pin.var_orange_in"),
                            VarDataNode453.PIN_COLOR);
                }
                if (PursuitGraphNode272.hitInputPin(canvas, n, mx, my)) {
                    return tip("pin:" + n.id + ":v_blue",
                            tr("gui.settledlands.tip448.pin.var_blue_title"),
                            tr("gui.settledlands.tip448.pin.var_blue_in"), 0xFF55AAFF);
                }
            }

            // Blue value source output pin on RADIUS_TARGET and Pursuit
            if (PursuitGraphNode272.isValueSourceNode(n) && !TrialGraph446.isTrialNode(n)
                    && !DoorGraph382.isDoorNode(n) && !ChestGraph396.isChestNode(n)) {
                double rx = canvas.screenX(n.x + w);
                if (distSq(mx, my, rx, canvas.screenY(n.y + h - 8.0D)) <= rSq) {
                    return tip("pin:" + n.id + ":val_out", tr("gui.settledlands.pursuit.output"),
                            tr("gui.settledlands.tip448.pin.value_source"), 0xFF55AAFF);
                }
            }

            // Standard left input and right output pins (non-card, non-module, non-stats, non-name, non-VarData)
            if (n.type != 16 && n.type != BlockName359.TYPE && !TrialGraph446.isTrialNode(n)
                    && !TrialModule447.isModuleNode(n) && !VarDataNode453.isVarDataNode(n)) {
                double lx = canvas.screenX(n.x);
                double ly = canvas.screenY(n.y + h / 2.0D);
                if (distSq(mx, my, lx, ly) <= rSq) {
                    return tip("pin:" + n.id + ":in", tr("gui.settledlands.tip448.pin.in_title"),
                            tr("gui.settledlands.tip448.pin.in_body"), DEFAULT_ACCENT);
                }
                if (n.type != NpcDialogueGraph.IF && !DoorGraph382.isDoorNode(n)
                        && !ChestGraph396.isChestNode(n)) {
                    double rx = canvas.screenX(n.x + w);
                    if (distSq(mx, my, rx, ly) <= rSq) {
                        return tip("pin:" + n.id + ":out", tr("gui.settledlands.tip448.pin.out_title"),
                                tr("gui.settledlands.tip448.pin.out_body"), DEFAULT_ACCENT);
                    }
                }
            }
        }
        return null;
    }

    // ---- 5. Auto-AI world card state-aware tooltips -------------------------

    private static Tip resolveAutoAiTip(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            NpcGraphCanvas canvas, int mx, int my) {
        int eid = screen.entityId();
        if (BlockTrigger351.isBlockEditorId(eid) || DoorGraph382.isDoorEditorId(eid)
                || ChestGraph396.isChestEditorId(eid) || TrialGraph446.isTrialEditorId(eid)) {
            return null;
        }
        NpcAutoAi255.GraphState state = NpcAutoAi255.graphState(graph);
        if (state == null || !state.synced) return null;
        double wx = canvas.worldX(mx) - NpcAutoAiGraphUi255.cardX(state);
        double wy = canvas.worldY(my) - NpcAutoAiGraphUi255.cardY(state);
        if (wx < 0 || wx > NpcAutoAiGraphUi255.CARD_W || wy < 0
                || wy > NpcAutoAiGraphUi255.CARD_H + GraphCardDesc448.MAX_FOOTER_H) {
            return null;
        }

        if (wx >= NpcAutoAiGraphUi255.TOGGLE_X && wx <= NpcAutoAiGraphUi255.TOGGLE_X + NpcAutoAiGraphUi255.TOGGLE_W) {
            if (wy >= NpcAutoAiGraphUi255.ROW_WANDER_Y && wy <= NpcAutoAiGraphUi255.ROW_WANDER_Y + NpcAutoAiGraphUi255.CTRL_H) {
                return tip("ai:wander",
                        tr("gui.settledlands.auto_ai.wander") + ": " + tr(state.autoWander
                                ? "gui.settledlands.auto_ai.on" : "gui.settledlands.auto_ai.off"),
                        tr(state.autoWander ? "gui.settledlands.tip448.ai.wander_on"
                                : "gui.settledlands.tip448.ai.wander_off"), DEFAULT_ACCENT);
            }
            if (wy >= NpcAutoAiGraphUi255.ROW_ATTACK_Y && wy <= NpcAutoAiGraphUi255.ROW_ATTACK_Y + NpcAutoAiGraphUi255.CTRL_H) {
                return tip("ai:attack",
                        tr("gui.settledlands.auto_ai.attack") + ": " + tr(state.autoAttackHostiles
                                ? "gui.settledlands.auto_ai.on" : "gui.settledlands.auto_ai.off"),
                        tr(state.autoAttackHostiles ? "gui.settledlands.tip448.ai.attack_on"
                                : "gui.settledlands.tip448.ai.attack_off"), WARN_ACCENT);
            }
        }
        if (wy >= NpcAutoAiGraphUi255.ROW_WANDER_R_Y && wy <= NpcAutoAiGraphUi255.ROW_WANDER_R_Y + NpcAutoAiGraphUi255.CTRL_H) {
            return tip("ai:wr", tr("gui.settledlands.auto_ai.wander_radius"),
                    tr("gui.settledlands.tip448.ai.wander_rad"), DEFAULT_ACCENT);
        }
        if (wy >= NpcAutoAiGraphUi255.ROW_AGGRO_R_Y && wy <= NpcAutoAiGraphUi255.ROW_AGGRO_R_Y + NpcAutoAiGraphUi255.CTRL_H) {
            return tip("ai:ar", tr("gui.settledlands.auto_ai.aggro_radius"),
                    tr("gui.settledlands.tip448.ai.aggro_rad"), WARN_ACCENT);
        }
        return tip("ai:card", tr("gui.settledlands.auto_ai.title"),
                tr("gui.settledlands.tip448.node.auto_ai"), DEFAULT_ACCENT);
    }

    // ---- 6. Canvas nodes (extended contextual tips, distinct from card footer)

    private static Tip resolveCanvasNodeTip(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            NpcGraphCanvas canvas, int mx, int my, int tab) {
        double wx = canvas.worldX(mx);
        double wy = canvas.worldY(my);
        Font font = screen.settledlands$font();
        Node hit = null;
        for (Node n : graph.nodes.values()) {
            if (n == null) continue;
            int w = GraphCardDesc448.cardWidth(n);
            int h = GraphCardDesc448.cardHeight(n);
            if (wx >= n.x && wx <= n.x + w && wy >= n.y && wy <= n.y + h) {
                hit = n;
            }
        }
        if (hit == null) return null;

        // Interactive zones inside Stats card (type == 16)
        if (hit.type == 16) {
            double rx = wx - hit.x;
            double ry = wy - hit.y;
            if (rx >= NpcStatsGraphUi.RAND_X && rx <= NpcStatsGraphUi.RAND_X + NpcStatsGraphUi.RAND_W
                    && ry >= NpcStatsGraphUi.RAND_Y && ry <= NpcStatsGraphUi.RAND_Y + NpcStatsGraphUi.RAND_H) {
                return tip("st:rand", tr("gui.settledlands.stats.randomize"),
                        tr("gui.settledlands.tip448.stats.randomize"), DEFAULT_ACCENT);
            }
            if (rx >= NpcStatsGraphUi.PROFESSION_X && rx <= NpcStatsGraphUi.PROFESSION_X + NpcStatsGraphUi.PROFESSION_W
                    && ry >= NpcStatsGraphUi.PROFESSION_Y && ry <= NpcStatsGraphUi.PROFESSION_Y + NpcStatsGraphUi.PROFESSION_H) {
                return tip("st:prof", tr("gui.settledlands.tip448.stats.prof_title"),
                        tr("gui.settledlands.tip448.stats.profession"), DEFAULT_ACCENT);
            }
            if (ry >= NpcStatsGraphUi.VARIANTS_Y && ry <= NpcStatsGraphUi.VARIANTS_Y + NpcStatsGraphUi.VARIANTS_H) {
                return tip("st:var", tr("gui.settledlands.tip448.stats.style_title"),
                        tr("gui.settledlands.tip448.stats.style"), DEFAULT_ACCENT);
            }
            for (int r = 0; r < STATS_ROWS.length; r++) {
                int rowTop = STATS_ROWS[r];
                if (ry >= rowTop - 4 && ry <= rowTop + 26) {
                    String sk = r == 0 ? "health" : (r == 1 ? "damage" : "speed");
                    return tip("st:row_" + sk, tr(STATS_LABELS[r]),
                            tr("gui.settledlands.tip448.stats." + sk), DEFAULT_ACCENT);
                }
            }
            return tip("node:" + hit.id, tr("gui.settledlands.stats.title"),
                    tr("gui.settledlands.tip448.node.stats"), DEFAULT_ACCENT);
        }

        int slot = TrialModule447.slotOf(hit);
        if (slot >= 0) {
            boolean wired = hit.next >= 0 && TrialGraph446.isTrialNode(graph.get(hit.next));
            String extra = "";
            if (slot == TrialModule447.SLOT_MODE) {
                TrialModule447.ModeData md = TrialModule447.readMode(hit);
                extra = modeStateDescription(md.mode, 1.5f) + "\n";
            }
            return tip("node:" + hit.id, tr(TrialModule447.titleKey(slot)),
                    extra + tr("gui.settledlands.tip448.node.mod_" + TrialModule447.kindOf(slot))
                            + "\n" + tr(wired ? "gui.settledlands.tip448.node.mod_state_on"
                                    : "gui.settledlands.tip448.node.mod_state_off"),
                    TrialModule447.color(slot));
        }
        if (TrialGraph446.isTrialNode(hit)) {
            int count = TrialModule447.connectedCount(graph, hit);
            return tip("node:" + hit.id, tr(TrialGraph446.TITLE_KEY),
                    Component.translatable("gui.settledlands.tip448.node.trial_hub",
                            Integer.valueOf(count)).getString(),
                    TrialGraph446.ACCENT_COLOR);
        }
        if (TrialControlNode446.isControlNode(hit)) {
            TrialControlNode446.Parsed p = TrialControlNode446.parsedOrDefault(hit);
            return tip("node:" + hit.id, tr(TrialControlNode446.TITLE_KEY),
                    tr("gui.settledlands.tip448.op." + trialOpTag(p.op)) + "\n"
                            + tr("gui.settledlands.tip448.node.trial_ctrl"),
                    TrialControlNode446.ACCENT_COLOR);
        }
        if (TeleportNode424.isTeleportNode(hit)) {
            return tip("node:" + hit.id, tr(TeleportNode424.TITLE_KEY),
                    tr("gui.settledlands.tip448.node.teleport"), TeleportNode424.ACCENT_COLOR);
        }
        if (VarDataNode453.isVarDataNode(hit)) {
            return tip("node:" + hit.id, tr(VarDataNode453.TITLE_KEY),
                    tr("gui.settledlands.tip448.node.var453"), VarDataNode453.ACCENT_COLOR);
        }
        if (LootPool436.isLootPoolNode(hit)) {
            return tip("node:" + hit.id, tr(LootPool436.TITLE_KEY),
                    tr("gui.settledlands.tip448.node.loot_pool"), LootPool436.ACCENT_COLOR);
        }
        if (PursuitGraphNode272.isPursuitNode(hit)) {
            boolean stop = PursuitGraphNode272.stopFlag(hit);
            return tip("node:" + hit.id, tr("gui.settledlands.graph.pursuit_node"),
                    tr(stop ? "gui.settledlands.tip448.node.pursuit_stop"
                            : "gui.settledlands.tip448.node.pursuit_start"),
                    0xFFE69138);
        }
        if (hit.type == BlockName359.TYPE) {
            return tip("node:" + hit.id, tr(BlockName359.TITLE_KEY),
                    tr("gui.settledlands.tip448.node.block_name"), DEFAULT_ACCENT);
        }
        if (DoorGraph382.isDoorNode(hit)) {
            return tip("node:" + hit.id, tr("gui.settledlands.door.node"),
                    tr(DoorGraph382.sealed(hit)
                            ? "gui.settledlands.tip448.node.door_sealed"
                            : "gui.settledlands.tip448.node.door_open"), DEFAULT_ACCENT);
        }
        if (ChestGraph396.isChestNode(hit)) {
            return tip("node:" + hit.id, tr("gui.settledlands.chest.node"),
                    tr(ChestLock397.locked(hit)
                            ? "gui.settledlands.tip448.node.chest_locked"
                            : "gui.settledlands.tip448.node.chest_open"), DEFAULT_ACCENT);
        }
        if (hit.type == NpcDialogueGraph.LINE && hit.cutsceneLine) {
            return tip("node:" + hit.id, tr("gui.settledlands.graph.line"),
                    tr("gui.settledlands.tip448.node.cutscene_line"), DEFAULT_ACCENT);
        }
        if (hit.type == NpcDialogueGraph.TIMER) {
            return tip("node:" + hit.id, tr("gui.settledlands.graph.timer"),
                    Component.translatable(hit.timerRepeat
                            ? "gui.settledlands.tip448.node.timer_loop"
                            : "gui.settledlands.tip448.node.timer_once",
                            Integer.valueOf(Math.max(1, hit.timerInterval))).getString(),
                    DEFAULT_ACCENT);
        }
        if (hit.type >= 0 && hit.type <= 15) {
            return tip("node:" + hit.id, stockNodeTitle(hit.type),
                    tr("gui.settledlands.tip448.node.n_" + hit.type), DEFAULT_ACCENT);
        }
        return null;
    }

    // ---- TrialControlConfigScreen446 support --------------------------------

    public static void renderConfigTooltip(Object screenObj, GuiGraphics graphics, Font font,
            int screenW, int screenH, int mouseX, int mouseY,
            List<ConfigArea> areas, TrialConfig446 cfg, String opTag) {
        if (screenObj == null || graphics == null || font == null || areas == null) return;
        Tip found = null;
        for (int i = areas.size() - 1; i >= 0; i--) {
            ConfigArea a = areas.get(i);
            if (a.widget instanceof AbstractWidget aw && !aw.visible) continue;
            if (mouseX >= a.x && mouseX < a.x + a.w && mouseY >= a.y && mouseY < a.y + a.h) {
                found = resolveConfigTip(a, cfg, opTag);
                break;
            }
        }
        updateAndDraw(screenObj, graphics, font, screenW, screenH, mouseX, mouseY, found);
    }

    private static Tip resolveConfigTip(ConfigArea a, TrialConfig446 cfg, String opTag) {
        TrialConfig446 c = cfg == null ? TrialConfig446.defaults() : cfg;
        switch (a.tag) {
            case "mode":
                return tip(a.slotId,
                        Component.translatable("gui.settledlands.trial.cfg.mode_btn",
                                Component.translatable(TrialModuleCard447.modeKey(c.mode))).getString(),
                        modeStateDescription(c.mode, c.ominousMobMultiplier),
                        modeAccent(c.mode));
            case "prevent_break":
                return tip(a.slotId,
                        toggleTitle("gui.settledlands.trial.cfg.prevent_break", c.noBreakEnabled),
                        tr(c.noBreakEnabled ? "gui.settledlands.tip448.pr.nobreak_on"
                                : "gui.settledlands.tip448.pr.nobreak_off"),
                        TrialModule447.color(TrialModule447.SLOT_PROTECT));
            case "scale_players":
                return tip(a.slotId,
                        toggleTitle("gui.settledlands.trial.cfg.scale_players", c.scaleWithPlayers),
                        tr(c.scaleWithPlayers ? "gui.settledlands.tip448.cfg.scale_on"
                                : "gui.settledlands.tip448.cfg.scale_off"),
                        TrialModule447.color(TrialModule447.SLOT_MODE));
            case "self_destruct":
                return tip(a.slotId,
                        toggleTitle("gui.settledlands.trial.cfg.self_destruct", c.selfDestruct),
                        tr(c.selfDestruct ? "gui.settledlands.tip448.pr.destruct_on"
                                : "gui.settledlands.tip448.pr.destruct_off"),
                        WARN_ACCENT);
            case "ominous_keys":
                return tip(a.slotId,
                        toggleTitle("gui.settledlands.trial.cfg.ominous_keys", c.ominousKeyConversion),
                        tr(c.ominousKeyConversion ? "gui.settledlands.tip448.om.keys_on"
                                : "gui.settledlands.tip448.om.keys_off"),
                        OMINOUS_ACCENT);
            case "ominous_hazards":
                return tip(a.slotId,
                        toggleTitle("gui.settledlands.trial.cfg.ominous_hazards", c.ominousHazards),
                        tr(c.ominousHazards ? "gui.settledlands.tip448.om.hazards_on"
                                : "gui.settledlands.tip448.om.hazards_off"),
                        OMINOUS_ACCENT);
            case "tab":
                return tip(a.slotId, tr(a.arg),
                        tr("gui.settledlands.tip448.cfg.tab_" + tabShortName(a.arg)),
                        TrialGraph446.ACCENT_COLOR);
            case "save_close":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.save_close"),
                        tr("gui.settledlands.tip448.cfg.save_close"), NORMAL_ACCENT);
            case "cancel":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.cancel"),
                        tr("gui.settledlands.tip448.cfg.cancel"), WARN_ACCENT);
            case "reset":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.reset"),
                        tr("gui.settledlands.tip448.cfg.reset"), WARN_ACCENT);
            case "add_mob":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.add_mob"),
                        tr("gui.settledlands.tip448.cfg.add_mob"),
                        TrialModule447.color(TrialModule447.SLOT_MOBS));
            case "add_reward":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.add_reward"),
                        tr("gui.settledlands.tip448.cfg.add_reward"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "mob_id":
                return tip(a.slotId, tr("gui.settledlands.trial.mod.mobs"),
                        tr("gui.settledlands.tip448.cfg.mob_id"),
                        TrialModule447.color(TrialModule447.SLOT_MOBS));
            case "mob_pick":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.pick"),
                        tr("gui.settledlands.tip448.mod.pick_mob"),
                        TrialModule447.color(TrialModule447.SLOT_MOBS));
            case "mob_weight":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_weight"),
                        tr("gui.settledlands.tip448.mod.weight_mob"),
                        TrialModule447.color(TrialModule447.SLOT_MOBS));
            case "mob_del":
                return tip(a.slotId, tr("gui.settledlands.graph.delete"),
                        tr("gui.settledlands.tip448.mod.remove_entry"), WARN_ACCENT);
            case "rew_id":
                return tip(a.slotId, tr("gui.settledlands.trial.mod.rewards"),
                        tr("gui.settledlands.tip448.cfg.rew_id"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_pick":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.pick"),
                        tr("gui.settledlands.tip448.mod.pick_reward"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_count":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_count"),
                        tr("gui.settledlands.tip448.cfg.rew_count"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_min":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_count"),
                        tr("gui.settledlands.tip448.cfg.rew_min"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_max":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_count"),
                        tr("gui.settledlands.tip448.cfg.rew_max"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_weight":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_weight"),
                        tr("gui.settledlands.tip448.cfg.rew_weight"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_chance":
                return tip(a.slotId, tr("gui.settledlands.loot_pool.editor.col_chance"),
                        tr("gui.settledlands.tip448.cfg.rew_chance"),
                        TrialModule447.color(TrialModule447.SLOT_REWARDS));
            case "rew_del":
                return tip(a.slotId, tr("gui.settledlands.graph.delete"),
                        tr("gui.settledlands.tip448.mod.remove_entry"), WARN_ACCENT);
            case "picker_back":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.back"),
                        tr("gui.settledlands.tip448.cfg.picker_back"), DEFAULT_ACCENT);
            case "picker_search":
                return tip(a.slotId, tr("gui.settledlands.trial.cfg.search"),
                        tr("gui.settledlands.tip448.cfg.picker_search"), DEFAULT_ACCENT);
            case "done":
                return tip(a.slotId, tr("gui.settledlands.trial.mod.done"),
                        tr("gui.settledlands.tip448.cfg.done"), NORMAL_ACCENT);
            case "field":
                String fk = trialFieldTipKey(a.arg);
                return fk.isEmpty() ? null
                        : tip(a.slotId, tr(a.arg), tr(fk), TrialGraph446.ACCENT_COLOR);
            default:
                return null;
        }
    }

    // ---- Shared state machine & tooltip renderer ----------------------------

    private static void resetIfOwner(Object owner) {
        if (activeOwner == owner) {
            activeSlotId = "";
            hoverStartMs = 0L;
            shownForSlot = false;
        }
    }

    private static void updateAndDraw(Object owner, GuiGraphics graphics, Font font,
            int screenW, int screenH, int mouseX, int mouseY, Tip tip) {
        if (owner != activeOwner) {
            activeOwner = owner;
            activeSlotId = "";
            hoverStartMs = 0L;
            shownForSlot = false;
        }
        if (tip == null || tip.slotId == null || tip.body.isEmpty()) {
            activeSlotId = "";
            hoverStartMs = 0L;
            shownForSlot = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (!tip.slotId.equals(activeSlotId)) {
            activeSlotId = tip.slotId;
            hoverStartMs = now;
            shownForSlot = false;
            return;
        }
        if (!shownForSlot) {
            if (now - hoverStartMs < DELAY_MS) {
                return;
            }
            shownForSlot = true;
        }
        drawOverlay(graphics, font, screenW, screenH, mouseX, mouseY, tip);
    }

    private static void drawOverlay(GuiGraphics graphics, Font font, int screenW, int screenH,
            int mouseX, int mouseY, Tip tip) {
        int maxTextW = Math.min(248, Math.max(120, screenW - 24));
        List<String> titleLines = wrapMultiline(font, tip.title, maxTextW, 2);
        List<String> bodyLines = wrapMultiline(font, tip.body, maxTextW, 10);

        int contentW = 0;
        for (String s : titleLines) contentW = Math.max(contentW, font.width(s));
        for (String s : bodyLines) contentW = Math.max(contentW, font.width(s));
        int boxW = Math.min(screenW - 12, contentW + 14);
        int titleH = titleLines.isEmpty() ? 0 : titleLines.size() * 10 + 4;
        int bodyH = bodyLines.size() * 10;
        int boxH = 5 + titleH + bodyH + 5;

        int x = mouseX + 12;
        int y = mouseY + 14;
        if (x + boxW > screenW - 6) x = Math.max(6, mouseX - boxW - 10);
        if (y + boxH > screenH - 6) y = Math.max(6, mouseY - boxH - 10);

        int borderCol = (0xFF000000 | (tip.accent & 0x00FFFFFF));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 900.0F);
        try {
            graphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0xCC06090F);
            graphics.fill(x, y, x + boxW, y + boxH, BG);
            graphics.renderOutline(x, y, boxW, boxH, borderCol);

            int cy = y + 5;
            for (String tl : titleLines) {
                graphics.drawString(font, tl, x + 7, cy, borderCol, true);
                cy += 10;
            }
            if (!titleLines.isEmpty()) {
                graphics.fill(x + 5, cy + 1, x + boxW - 5, cy + 2, DIVIDER);
                cy += 4;
            }
            for (String bl : bodyLines) {
                graphics.drawString(font, bl, x + 7, cy, BODY_TEXT, false);
                cy += 10;
            }
        } finally {
            pose.popPose();
        }
    }

    private static List<String> wrapMultiline(Font font, String text, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxW <= 0) return out;
        String[] paras = text.split("\n", -1);
        for (String p : paras) {
            if (p.trim().isEmpty()) continue;
            String[] words = p.trim().split("\\s+");
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                String cand = cur.length() == 0 ? w : cur + " " + w;
                if (font.width(cand) <= maxW || cur.length() == 0) {
                    cur.setLength(0);
                    cur.append(cand);
                } else {
                    out.add(cur.toString());
                    if (out.size() >= maxLines) return out;
                    cur.setLength(0);
                    cur.append(w);
                }
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
                if (out.size() >= maxLines) return out;
            }
        }
        return out;
    }

    // ---- State-dependent text builders & lookup tables ----------------------

    public static String modeStateDescription(String mode, float mult) {
        String norm = TrialConfig446.normalizeMode(mode);
        if (TrialConfig446.MODE_OMINOUS.equals(norm)) {
            return Component.translatable("gui.settledlands.tip448.mode.ominous",
                    String.format(Locale.ROOT, "%.2f", mult)).getString();
        }
        if (TrialConfig446.MODE_SPECIAL.equals(norm)) {
            return tr("gui.settledlands.tip448.mode.special");
        }
        return tr("gui.settledlands.tip448.mode.normal");
    }

    private static int modeAccent(String mode) {
        String norm = TrialConfig446.normalizeMode(mode);
        if (TrialConfig446.MODE_OMINOUS.equals(norm)) return OMINOUS_ACCENT;
        if (TrialConfig446.MODE_SPECIAL.equals(norm)) return SPECIAL_ACCENT;
        return NORMAL_ACCENT;
    }

    private static String toggleTitle(String fmtKey, boolean on) {
        return Component.translatable(fmtKey,
                Component.translatable(on ? "gui.settledlands.trial.cfg.yes"
                        : "gui.settledlands.trial.cfg.no")).getString();
    }

    private static boolean isScaleText(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String prefixYes = Component.translatable("gui.settledlands.trial.cfg.scale_players", "").getString().trim();
        return !prefixYes.isEmpty() && raw.startsWith(prefixYes);
    }

    private static boolean matchesPrefix(String raw, String key) {
        if (raw == null || key == null) return false;
        String prefix = Component.translatable(key, "").getString().trim();
        int colon = prefix.indexOf(':');
        if (colon > 0) prefix = prefix.substring(0, colon + 1);
        return !prefix.isEmpty() && raw.startsWith(prefix);
    }

    private static String tabShortName(String key) {
        if ("gui.settledlands.trial.cfg.tab_ominous".equals(key)) return "ominous";
        if ("gui.settledlands.trial.cfg.tab_mobs".equals(key)) return "mobs";
        if ("gui.settledlands.trial.cfg.tab_rewards".equals(key)) return "rewards";
        return "general";
    }

    private static String trialOpTag(String op) {
        if (TrialControlNode446.OP_CONFIG.equals(op)) return "config";
        if (TrialControlNode446.OP_CONFIG_START.equals(op)) return "config_start";
        if (TrialControlNode446.OP_STOP.equals(op)) return "stop";
        if (TrialControlNode446.OP_RESET_CD.equals(op)) return "reset_cd";
        if (TrialControlNode446.OP_DESTROY.equals(op)) return "destroy";
        return "start";
    }

    private static String trialOpTagByIndex(int idx) {
        return switch (idx) {
            case 1 -> "config";
            case 2 -> "config_start";
            case 3 -> "stop";
            case 4 -> "reset_cd";
            case 5 -> "destroy";
            default -> "start";
        };
    }

    private static String trialFieldTipKey(String labelKey) {
        return switch (labelKey) {
            case "gui.settledlands.trial.cfg.act_range" -> "gui.settledlands.tip448.tf.act_range";
            case "gui.settledlands.trial.cfg.spawn_range" -> "gui.settledlands.tip448.tf.spawn_range";
            case "gui.settledlands.trial.cfg.total_mobs" -> "gui.settledlands.tip448.tf.total_mobs";
            case "gui.settledlands.trial.cfg.per_player_total" -> "gui.settledlands.tip448.tf.per_player_total";
            case "gui.settledlands.trial.cfg.sim_mobs" -> "gui.settledlands.tip448.tf.sim_mobs";
            case "gui.settledlands.trial.cfg.per_player_sim" -> "gui.settledlands.tip448.tf.per_player_sim";
            case "gui.settledlands.trial.cfg.batch" -> "gui.settledlands.tip448.tf.batch";
            case "gui.settledlands.trial.cfg.delay" -> "gui.settledlands.tip448.tf.delay";
            case "gui.settledlands.trial.cfg.min_delay" -> "gui.settledlands.tip448.tf.min_delay";
            case "gui.settledlands.trial.cfg.max_delay" -> "gui.settledlands.tip448.tf.max_delay";
            case "gui.settledlands.trial.cfg.cooldown" -> "gui.settledlands.tip448.tf.cooldown";
            case "gui.settledlands.trial.cfg.ominous_mult" -> "gui.settledlands.tip448.tf.ominous_mult";
            case "gui.settledlands.trial.cfg.ominous_interval" -> "gui.settledlands.tip448.tf.ominous_interval";
            case "gui.settledlands.trial.cfg.nobreak_r" -> "gui.settledlands.tip448.tf.nobreak_r";
            case "gui.settledlands.trial.cfg.nobreak_s" -> "gui.settledlands.tip448.tf.nobreak_s";
            case "gui.settledlands.trial.cfg.nobreak_amp" -> "gui.settledlands.tip448.tf.nobreak_amp";
            case "gui.settledlands.trial.cfg.destruct_waves" -> "gui.settledlands.tip448.tf.destruct_waves";
            case "gui.settledlands.trial.cfg.destruct_chance" -> "gui.settledlands.tip448.tf.destruct_chance";
            default -> "";
        };
    }

    private static String stockFieldTipKey(String labelKey) {
        return switch (labelKey) {
            case "gui.settledlands.graph.trigger_command" -> "gui.settledlands.tip448.fld.trigger_cmd";
            case "gui.settledlands.graph.action" -> "gui.settledlands.tip448.fld.action";
            case "gui.settledlands.graph.var_name" -> "gui.settledlands.tip448.fld.var_name";
            case "gui.settledlands.graph.var_delta" -> "gui.settledlands.tip448.fld.var_delta";
            case "gui.settledlands.graph.var_compare" -> "gui.settledlands.tip448.fld.var_compare";
            case "gui.settledlands.graph.check_radius" -> "gui.settledlands.tip448.fld.check_radius";
            case "gui.settledlands.graph.check_cooldown" -> "gui.settledlands.tip448.fld.check_cooldown";
            case "gui.settledlands.graph.min_damage" -> "gui.settledlands.tip448.fld.min_damage";
            case "gui.settledlands.graph.time_ticks" -> "gui.settledlands.tip448.fld.time_ticks";
            case "gui.settledlands.graph.timer_interval" -> "gui.settledlands.tip448.fld.timer_interval";
            case "gui.settledlands.graph.radius" -> "gui.settledlands.tip448.fld.radius";
            case "gui.settledlands.graph.quest_title" -> "gui.settledlands.tip448.fld.quest_title";
            case "gui.settledlands.graph.min_relation" -> "gui.settledlands.tip448.fld.min_relation";
            case "gui.settledlands.graph.max_relation" -> "gui.settledlands.tip448.fld.max_relation";
            case "gui.settledlands.graph.relation_range" -> "gui.settledlands.tip448.fld.relation_range";
            case "gui.settledlands.graph.move_coords" -> "gui.settledlands.tip448.fld.move_coords";
            case "gui.settledlands.graph.move_speed" -> "gui.settledlands.tip448.fld.move_speed";
            case "gui.settledlands.graph.random_range" -> "gui.settledlands.tip448.fld.random_range";
            case "gui.settledlands.graph.random_outputs" -> "gui.settledlands.tip448.fld.random_outputs";
            case "gui.settledlands.graph.text" -> "gui.settledlands.tip448.fld.text";
            case "gui.settledlands.graph.variant" -> "gui.settledlands.tip448.fld.variant";
            case "gui.settledlands.graph.relation" -> "gui.settledlands.tip448.fld.relation";
            case "gui.settledlands.graph.points" -> "gui.settledlands.tip448.fld.points";
            case "gui.settledlands.graph.min_points" -> "gui.settledlands.tip448.fld.min_points";
            case "gui.settledlands.graph.max_points" -> "gui.settledlands.tip448.fld.max_points";
            case "gui.settledlands.graph.points_range" -> "gui.settledlands.tip448.fld.points_range";
            default -> "";
        };
    }

    private static String stockNodeTitle(int type) {
        return switch (type) {
            case 0 -> tr("gui.settledlands.graph.line");
            case 1 -> tr("gui.settledlands.graph.button");
            case 2 -> tr("gui.settledlands.graph.action");
            case 3 -> tr("gui.settledlands.graph.if");
            case 4 -> tr("gui.settledlands.graph.var_name");
            case 5 -> tr("gui.settledlands.graph.check");
            case 6 -> tr("gui.settledlands.graph.damage");
            case 7 -> tr("gui.settledlands.graph.time_mode");
            case 8 -> tr("gui.settledlands.graph.timer_interval");
            case 9 -> tr("gui.settledlands.graph.anim");
            case 10 -> tr("gui.settledlands.graph.quest");
            case 11 -> tr("gui.settledlands.graph.move");
            case 12 -> tr("gui.settledlands.graph.mobility");
            case 13 -> tr("gui.settledlands.graph.random_outputs");
            case 14 -> tr("gui.settledlands.graph.cutscene");
            case 15 -> tr("gui.settledlands.pursuit.output");
            default -> "";
        };
    }

    private static String stockMenuLabel(int row) {
        String[] keys = {
            "gui.settledlands.graph.add_line", "gui.settledlands.graph.add_button",
            "gui.settledlands.graph.add_trigger", "gui.settledlands.graph.add_if",
            "gui.settledlands.graph.add_variable", "gui.settledlands.graph.add_check",
            "gui.settledlands.graph.add_damage", "gui.settledlands.graph.add_time",
            "gui.settledlands.graph.add_timer", "gui.settledlands.graph.add_anim",
            "gui.settledlands.graph.add_quest", "gui.settledlands.graph.add_move",
            "gui.settledlands.graph.add_mobility", "gui.settledlands.graph.add_random",
            "gui.settledlands.graph.add_cutscene", "gui.settledlands.graph.add_radius"
        };
        return row >= 0 && row < keys.length ? tr(keys[row]) : "";
    }

    private static int stockMenuType(int row) {
        return row >= 0 && row <= 15 ? row : 0;
    }

    private static Tip tip(String slotId, String title, String body, int accent) {
        return new Tip(slotId, title, body, accent);
    }

    private static double distSq(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static String tr(String key) {
        return GraphCardDesc448.tr(key);
    }
}
