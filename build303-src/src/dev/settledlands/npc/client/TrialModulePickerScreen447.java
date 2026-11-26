package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcItems;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Build 447: focused picker & list editor for the {@code mobs} and {@code rewards}
 * {@link TrialModule447} nodes (no monolithic 4-tab wrapper).
 *
 * <p>Uses strictly the same Minecraft/NeoForge API surface already verified in
 * {@link TrialControlConfigScreen446}.</p>
 */
public final class TrialModulePickerScreen447 extends Screen {

    public static final int VIEW_MOB_POOL = 0;
    public static final int VIEW_REWARD_POOL = 1;
    public static final int VIEW_MOB_PICKER = 2;
    public static final int VIEW_ITEM_PICKER = 3;

    private static final int PICK_CELL_H = 22;
    private static final int ROW_H = 22;

    private static final class MobOption {
        final String id;
        final String displayName;
        final String searchLower;

        MobOption(String id, String displayName) {
            this.id = id;
            this.displayName = displayName == null ? "" : displayName;
            this.searchLower = (id + " " + this.displayName).toLowerCase(Locale.ROOT);
        }
    }

    private final NpcDialogueGraphScreen parentScreen;
    private final Node moduleNode;
    private final int slot;
    private final boolean directPickerOnly;

    private int viewMode;
    private int pickerRow = -1;
    private boolean returnToList;
    private boolean undoRecorded;

    private List<TrialConfig446.SpawnEntry> mobs;
    private List<TrialConfig446.RewardEntry> rewards;

    private int listScroll;
    private String pickerQuery = "";
    private int pickerScroll;
    private int pickerGridX;
    private int pickerGridY;
    private int pickerGridW;
    private int pickerCols = 10;
    private int pickerVisibleRows = 6;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyTop;
    private int bodyBottom;

    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<GraphTooltips448.ConfigArea> tipAreas448 = new ArrayList<>();
    private List<MobOption> allMobs;
    private final List<MobOption> filteredMobs = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private Map<String, ItemStack> iconMap;
    private String statusText = "";
    private int statusColor = 0xFFFFB048;

    private TrialModulePickerScreen447(NpcDialogueGraphScreen parent, Node moduleNode, int viewMode,
            int pickerRow, boolean directPickerOnly) {
        super(Component.translatable(TrialModule447.titleKey(
                moduleNode != null && TrialModule447.slotOf(moduleNode) == TrialModule447.SLOT_REWARDS
                        ? TrialModule447.SLOT_REWARDS : TrialModule447.SLOT_MOBS)));
        this.parentScreen = parent;
        this.moduleNode = moduleNode;
        this.slot = TrialModule447.slotOf(moduleNode) == TrialModule447.SLOT_REWARDS
                ? TrialModule447.SLOT_REWARDS : TrialModule447.SLOT_MOBS;
        this.viewMode = viewMode;
        this.pickerRow = pickerRow;
        this.directPickerOnly = directPickerOnly;
        this.returnToList = !directPickerOnly;
        this.mobs = TrialModule447.readMobs(moduleNode);
        this.rewards = TrialModule447.readRewards(moduleNode);
    }

    public static void openAddMob(NpcDialogueGraphScreen parent, Node moduleNode) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_MOB_PICKER, -1, true));
    }

    public static void openPickMobRow(NpcDialogueGraphScreen parent, Node moduleNode, int row) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_MOB_PICKER, row, true));
    }

    public static void openMobPoolEditor(NpcDialogueGraphScreen parent, Node moduleNode) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_MOB_POOL, -1, false));
    }

    public static void openAddReward(NpcDialogueGraphScreen parent, Node moduleNode) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_ITEM_PICKER, -1, true));
    }

    public static void openPickRewardRow(NpcDialogueGraphScreen parent, Node moduleNode, int row) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_ITEM_PICKER, row, true));
    }

    public static void openRewardPoolEditor(NpcDialogueGraphScreen parent, Node moduleNode) {
        Minecraft.getInstance().setScreen(new TrialModulePickerScreen447(
                parent, moduleNode, VIEW_REWARD_POOL, -1, false));
    }

    @Override
    protected void init() {
        panelW = Math.min(540, Math.max(380, width - 24));
        panelH = Math.min(340, Math.max(230, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        bodyTop = panelY + 30;
        bodyBottom = panelY + panelH - 34;
        rebuildView();
    }

    private void rebuildView() {
        for (int i = 0; i < dynamicWidgets.size(); i++) {
            removeWidget(dynamicWidgets.get(i));
        }
        dynamicWidgets.clear();
        tipAreas448.clear();
        statusText = "";

        if (viewMode == VIEW_MOB_PICKER || viewMode == VIEW_ITEM_PICKER) {
            buildPickerView();
            return;
        }
        if (viewMode == VIEW_MOB_POOL) {
            buildMobPoolView();
        } else {
            buildRewardPoolView();
        }
        AbstractWidget doneBtn = Button.builder(Component.translatable("gui.settledlands.trial.mod.done"), b -> {
            commitToNode();
            returnToParent();
        }).bounds(panelX + panelW - 96, panelY + panelH - 26, 84, 18).build();
        addDyn(doneBtn);
        addTip("mp:done", doneBtn, panelX + panelW - 96, panelY + panelH - 26, 84, 18, "done", "");
    }

    private void addTip(String slotId, Object w, int x, int y, int width, int height,
            String tag, String arg) {
        tipAreas448.add(new GraphTooltips448.ConfigArea(slotId, w, x, y, width, height, tag, arg));
    }

    private void addDyn(AbstractWidget w) {
        addRenderableWidget(w);
        dynamicWidgets.add(w);
    }

    private void buildMobPoolView() {
        int x = panelX + 12;
        int w = panelW - 24;
        int topBtnY = bodyTop + 2;

        AbstractWidget addM = Button.builder(Component.translatable("gui.settledlands.trial.cfg.add_mob"), b -> {
            mobs.add(new TrialConfig446.SpawnEntry("minecraft:zombie", 1));
            int vis = visibleRows();
            listScroll = Math.max(0, mobs.size() - vis);
            commitToNode();
            rebuildView();
        }).bounds(x, topBtnY, 136, 18).build();
        addDyn(addM);
        addTip("mp:add_mob", addM, x, topBtnY, 136, 18, "add_mob", "");

        AbstractWidget catM = Button.builder(Component.translatable("gui.settledlands.trial.mod.pick_catalog"), b -> {
            pickerRow = -1;
            pickerQuery = "";
            pickerScroll = 0;
            returnToList = true;
            viewMode = VIEW_MOB_PICKER;
            rebuildView();
        }).bounds(x + 142, topBtnY, 150, 18).build();
        addDyn(catM);
        addTip("mp:cat_mob", catM, x + 142, topBtnY, 150, 18, "mob_pick", "");

        int listTop = bodyTop + 26;
        int vis = visibleRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, mobs.size() - vis)));

        int pickW = 56;
        int delW = 20;
        int weightW = 54;
        int idW = Math.max(80, w - pickW - delW - weightW - 14);

        for (int s = 0; s < vis; s++) {
            final int idx = listScroll + s;
            if (idx >= mobs.size()) break;
            final TrialConfig446.SpawnEntry entry = mobs.get(idx);
            int ry = listTop + s * ROW_H;

            EditBox idBox = new EditBox(font, x, ry, idW, 18, Component.empty());
            idBox.setMaxLength(96);
            idBox.setValue(entry.entityId);
            idBox.setResponder(val -> {
                entry.entityId = val == null ? "" : val.trim();
                commitToNode();
            });
            addDyn(idBox);
            addTip("mp:mid_" + s, idBox, x, ry, idW, 18, "mob_id", "");

            AbstractWidget pkM = Button.builder(Component.translatable("gui.settledlands.trial.cfg.pick"), b -> {
                pickerRow = idx;
                pickerQuery = "";
                pickerScroll = 0;
                returnToList = true;
                viewMode = VIEW_MOB_PICKER;
                rebuildView();
            }).bounds(x + idW + 4, ry, pickW, 18).build();
            addDyn(pkM);
            addTip("mp:mpk_" + s, pkM, x + idW + 4, ry, pickW, 18, "mob_pick", "");

            EditBox wgtBox = new EditBox(font, x + idW + pickW + 8, ry, weightW, 18, Component.empty());
            wgtBox.setMaxLength(8);
            wgtBox.setValue(Integer.toString(entry.weight));
            wgtBox.setResponder(val -> {
                entry.weight = parseInt(val, entry.weight, 1, 10000);
                commitToNode();
            });
            addDyn(wgtBox);
            addTip("mp:mwg_" + s, wgtBox, x + idW + pickW + 8, ry, weightW, 18, "mob_weight", "");

            AbstractWidget dlM = Button.builder(Component.literal("-"), b -> {
                if (mobs.size() > 1) {
                    mobs.remove(idx);
                    commitToNode();
                    rebuildView();
                } else {
                    statusText = Component.translatable("gui.settledlands.trial.cfg.keep_one_mob").getString();
                    statusColor = 0xFFFFB048;
                }
            }).bounds(x + idW + pickW + weightW + 12, ry, delW, 18).build();
            addDyn(dlM);
            addTip("mp:mdl_" + s, dlM, x + idW + pickW + weightW + 12, ry, delW, 18, "mob_del", "");
        }
    }

    private void buildRewardPoolView() {
        int x = panelX + 12;
        int w = panelW - 24;
        int topBtnY = bodyTop + 2;

        AbstractWidget addR = Button.builder(Component.translatable("gui.settledlands.trial.cfg.add_reward"), b -> {
            rewards.add(new TrialConfig446.RewardEntry("minecraft:emerald", 1, 1, 3, 100));
            int vis = visibleRows();
            listScroll = Math.max(0, rewards.size() - vis);
            commitToNode();
            rebuildView();
        }).bounds(x, topBtnY, 136, 18).build();
        addDyn(addR);
        addTip("mp:add_rew", addR, x, topBtnY, 136, 18, "add_reward", "");

        AbstractWidget catR = Button.builder(Component.translatable("gui.settledlands.trial.mod.pick_catalog"), b -> {
            pickerRow = -1;
            pickerQuery = "";
            pickerScroll = 0;
            returnToList = true;
            viewMode = VIEW_ITEM_PICKER;
            rebuildView();
        }).bounds(x + 142, topBtnY, 150, 18).build();
        addDyn(catR);
        addTip("mp:cat_rew", catR, x + 142, topBtnY, 150, 18, "rew_pick", "");

        int listTop = bodyTop + 26;
        int vis = visibleRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, rewards.size() - vis)));

        int iconW = 20;
        int pickW = 50;
        int minW = 32;
        int maxW = 32;
        int wgtW = 36;
        int pctW = 36;
        int delW = 20;
        int idW = Math.max(70, w - iconW - pickW - minW - maxW - wgtW - pctW - delW - 26);

        for (int s = 0; s < vis; s++) {
            final int idx = listScroll + s;
            if (idx >= rewards.size()) break;
            final TrialConfig446.RewardEntry entry = rewards.get(idx);
            int ry = listTop + s * ROW_H;
            int cx = x + iconW;

            EditBox idBox = new EditBox(font, cx, ry, idW, 18, Component.empty());
            idBox.setMaxLength(96);
            idBox.setValue(entry.itemId);
            idBox.setResponder(val -> {
                entry.itemId = val == null ? "" : val.trim();
                commitToNode();
            });
            addDyn(idBox);
            addTip("mp:rid_" + s, idBox, cx, ry, idW, 18, "rew_id", "");
            cx += idW + 4;

            AbstractWidget pkR = Button.builder(Component.translatable("gui.settledlands.trial.cfg.pick"), b -> {
                pickerRow = idx;
                pickerQuery = "";
                pickerScroll = 0;
                returnToList = true;
                viewMode = VIEW_ITEM_PICKER;
                rebuildView();
            }).bounds(cx, ry, pickW, 18).build();
            addDyn(pkR);
            addTip("mp:rpk_" + s, pkR, cx, ry, pickW, 18, "rew_pick", "");
            cx += pickW + 4;

            EditBox minBox = new EditBox(font, cx, ry, minW, 18, Component.empty());
            minBox.setMaxLength(4);
            minBox.setValue(Integer.toString(entry.minCount));
            minBox.setResponder(val -> {
                entry.minCount = parseInt(val, entry.minCount, 1, 64);
                commitToNode();
            });
            addDyn(minBox);
            addTip("mp:rmn_" + s, minBox, cx, ry, minW, 18, "rew_min", "");
            cx += minW + 4;

            EditBox maxBox = new EditBox(font, cx, ry, maxW, 18, Component.empty());
            maxBox.setMaxLength(4);
            maxBox.setValue(Integer.toString(entry.maxCount));
            maxBox.setResponder(val -> {
                entry.maxCount = parseInt(val, entry.maxCount, 1, 64);
                commitToNode();
            });
            addDyn(maxBox);
            addTip("mp:rmx_" + s, maxBox, cx, ry, maxW, 18, "rew_max", "");
            cx += maxW + 4;

            EditBox wgtBox = new EditBox(font, cx, ry, wgtW, 18, Component.empty());
            wgtBox.setMaxLength(6);
            wgtBox.setValue(Integer.toString(entry.weight));
            wgtBox.setResponder(val -> {
                entry.weight = parseInt(val, entry.weight, 1, 10000);
                commitToNode();
            });
            addDyn(wgtBox);
            addTip("mp:rwg_" + s, wgtBox, cx, ry, wgtW, 18, "rew_weight", "");
            cx += wgtW + 4;

            EditBox pctBox = new EditBox(font, cx, ry, pctW, 18, Component.empty());
            pctBox.setMaxLength(4);
            pctBox.setValue(Integer.toString(entry.chancePercent));
            pctBox.setResponder(val -> {
                entry.chancePercent = parseInt(val, entry.chancePercent, 0, 100);
                commitToNode();
            });
            addDyn(pctBox);
            addTip("mp:rch_" + s, pctBox, cx, ry, pctW, 18, "rew_chance", "");
            cx += pctW + 4;

            AbstractWidget dlR = Button.builder(Component.literal("-"), b -> {
                rewards.remove(idx);
                commitToNode();
                rebuildView();
            }).bounds(cx, ry, delW, 18).build();
            addDyn(dlR);
            addTip("mp:rdl_" + s, dlR, cx, ry, delW, 18, "rew_del", "");
        }
    }

    private void buildPickerView() {
        int x = panelX + 10;
        int w = panelW - 20;
        AbstractWidget backBtn = Button.builder(Component.translatable("gui.settledlands.trial.cfg.back"), b -> {
            if (directPickerOnly || !returnToList) {
                returnToParent();
            } else {
                viewMode = slot == TrialModule447.SLOT_REWARDS ? VIEW_REWARD_POOL : VIEW_MOB_POOL;
                rebuildView();
            }
        }).bounds(panelX + panelW - 86, panelY + 6, 76, 18).build();
        addDyn(backBtn);
        addTip("mp:pk_back", backBtn, panelX + panelW - 86, panelY + 6, 76, 18, "picker_back", "");

        EditBox searchBox = new EditBox(font, x, panelY + 28, w, 18,
                Component.translatable("gui.settledlands.trial.cfg.search"));
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.translatable("gui.settledlands.trial.cfg.search"));
        searchBox.setValue(pickerQuery);
        searchBox.setResponder(text -> {
            pickerQuery = text == null ? "" : text;
            pickerScroll = 0;
            refreshPickerData();
        });
        addDyn(searchBox);
        addTip("mp:pk_srch", searchBox, x, panelY + 28, w, 18, "picker_search", "");
        setInitialFocus(searchBox);

        pickerGridX = x;
        pickerGridY = panelY + 52;
        pickerGridW = w;
        int gridH = Math.max(44, panelY + panelH - 16 - pickerGridY);
        pickerCols = viewMode == VIEW_ITEM_PICKER ? Math.max(6, pickerGridW / 24) : 2;
        pickerVisibleRows = Math.max(2, gridH / PICK_CELL_H);
        refreshPickerData();
    }

    private void refreshPickerData() {
        String q = pickerQuery.trim().toLowerCase(Locale.ROOT);
        if (viewMode == VIEW_MOB_PICKER) {
            if (allMobs == null) allMobs = loadMobOptions();
            filteredMobs.clear();
            for (int i = 0; i < allMobs.size(); i++) {
                MobOption opt = allMobs.get(i);
                if (q.isEmpty() || opt.searchLower.contains(q)) {
                    filteredMobs.add(opt);
                }
            }
        } else if (viewMode == VIEW_ITEM_PICKER) {
            List<ItemStack> stacks = NpcItems.allStacks();
            filteredItems.clear();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) continue;
                if (q.isEmpty()) {
                    filteredItems.add(stack);
                    continue;
                }
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String id = rl == null ? "" : rl.toString().toLowerCase(Locale.ROOT);
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (id.contains(q) || name.contains(q)) {
                    filteredItems.add(stack);
                }
            }
        }
    }

    private static List<MobOption> loadMobOptions() {
        List<MobOption> out = new ArrayList<>();
        try {
            for (EntityType type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == null) continue;
                ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (rl == null) continue;
                String id = rl.toString();
                if (isNonLivingNoise(id)) continue;
                String name = "";
                try {
                    name = type.getDescription().getString();
                } catch (Throwable ignored) {
                }
                out.add(new MobOption(id, name));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean isNonLivingNoise(String id) {
        return id.endsWith("arrow") || id.endsWith("boat") || id.endsWith("minecart")
                || id.endsWith("item") || id.endsWith("experience_orb")
                || id.endsWith("falling_block") || id.endsWith("area_effect_cloud")
                || id.endsWith("lightning_bolt") || id.endsWith("fireball")
                || id.endsWith("snowball") || id.endsWith("egg") || id.endsWith("pearl")
                || id.equals("minecraft:player") || id.equals("minecraft:marker");
    }

    private ItemStack iconFor(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        if (iconMap == null) {
            iconMap = new HashMap<>();
            List<ItemStack> stacks = NpcItems.allStacks();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack s = stacks.get(i);
                if (s == null || s.isEmpty()) continue;
                ResourceLocation rl = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (rl != null) iconMap.putIfAbsent(rl.toString(), s);
            }
        }
        ItemStack found = iconMap.get(TrialConfig446.normalizeId(itemId));
        return found == null ? ItemStack.EMPTY : found;
    }

    private int visibleRows() {
        return Math.max(2, (bodyBottom - (bodyTop + 26)) / ROW_H);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x990A0D13);
        int accent = 0xFF000000 | TrialModule447.color(slot);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF080A0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        Component title = viewMode == VIEW_MOB_PICKER
                ? Component.translatable("gui.settledlands.trial.cfg.pick_mob_title")
                : viewMode == VIEW_ITEM_PICKER
                ? Component.translatable("gui.settledlands.trial.cfg.pick_item_title")
                : Component.translatable(TrialModule447.titleKey(slot));
        graphics.drawString(font, title, panelX + 12, panelY + 9, accent, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (viewMode == VIEW_MOB_POOL) {
            graphics.drawString(font, Component.translatable("gui.settledlands.trial.cfg.mobs_hdr"),
                    panelX + 12, bodyTop + 17, 0xFF9AA5B8, false);
            if (!statusText.isEmpty()) {
                graphics.drawString(font, clip(font, statusText, panelW - 130),
                        panelX + 12, panelY + panelH - 22, statusColor, false);
            }
        } else if (viewMode == VIEW_REWARD_POOL) {
            graphics.drawString(font, Component.translatable("gui.settledlands.trial.cfg.rewards_hdr"),
                    panelX + 12, bodyTop + 17, 0xFF9AA5B8, false);
            int listTop = bodyTop + 26;
            int vis = visibleRows();
            for (int s = 0; s < vis; s++) {
                int idx = listScroll + s;
                if (idx >= rewards.size()) break;
                ItemStack icon = iconFor(rewards.get(idx).itemId);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, panelX + 12, listTop + s * ROW_H + 1);
                }
            }
        } else if (viewMode == VIEW_MOB_PICKER) {
            renderMobPicker(graphics, mouseX, mouseY);
        } else if (viewMode == VIEW_ITEM_PICKER) {
            renderItemPicker(graphics, mouseX, mouseY);
        }

        GraphTooltips448.renderConfigTooltip(this, graphics, this.font, this.width, this.height,
                mouseX, mouseY, this.tipAreas448, null, "");
    }

    private void renderMobPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int colW = (pickerGridW - 6) / 2;
        int start = pickerScroll * 2;
        int accent = 0xFF000000 | TrialModule447.color(slot);
        for (int r = 0; r < pickerVisibleRows; r++) {
            for (int c = 0; c < 2; c++) {
                int idx = start + r * 2 + c;
                if (idx >= filteredMobs.size()) break;
                MobOption opt = filteredMobs.get(idx);
                int cx = pickerGridX + c * (colW + 6);
                int cy = pickerGridY + r * PICK_CELL_H;
                boolean over = mouseX >= cx && mouseX < cx + colW
                        && mouseY >= cy && mouseY < cy + PICK_CELL_H - 2;
                graphics.fill(cx, cy, cx + colW, cy + PICK_CELL_H - 2,
                        over ? 0xFF283A4E : 0xFF1A202B);
                graphics.renderOutline(cx, cy, colW, PICK_CELL_H - 2, over ? accent : 0xFF2D3546);
                String text = opt.displayName.isEmpty() ? opt.id : opt.displayName + " (" + opt.id + ")";
                graphics.drawString(font, clip(font, text, colW - 8), cx + 4, cy + 6, 0xFFEAEFF8, false);
            }
        }
    }

    private void renderItemPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int cellW = 24;
        int start = pickerScroll * pickerCols;
        int accent = 0xFF000000 | TrialModule447.color(slot);
        ItemStack hovered = ItemStack.EMPTY;
        for (int r = 0; r < pickerVisibleRows; r++) {
            for (int c = 0; c < pickerCols; c++) {
                int idx = start + r * pickerCols + c;
                if (idx >= filteredItems.size()) break;
                ItemStack stack = filteredItems.get(idx);
                int cx = pickerGridX + c * cellW;
                int cy = pickerGridY + r * PICK_CELL_H;
                boolean over = mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 20;
                graphics.fill(cx, cy, cx + 22, cy + 20, over ? 0xFF2B3E52 : 0xFF1A202B);
                graphics.renderOutline(cx, cy, 22, 20, over ? accent : 0xFF2D3546);
                graphics.renderItem(stack, cx + 3, cy + 2);
                if (over) hovered = stack;
            }
        }
        if (!hovered.isEmpty()) {
            setTooltipForNextRenderPass(hovered.getHoverName());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        GraphTooltips448.onMouseClick();
        if (button == 0 && viewMode == VIEW_MOB_PICKER) {
            int colW = (pickerGridW - 6) / 2;
            int start = pickerScroll * 2;
            for (int r = 0; r < pickerVisibleRows; r++) {
                for (int c = 0; c < 2; c++) {
                    int idx = start + r * 2 + c;
                    if (idx >= filteredMobs.size()) break;
                    int cx = pickerGridX + c * (colW + 6);
                    int cy = pickerGridY + r * PICK_CELL_H;
                    if (mouseX >= cx && mouseX < cx + colW
                            && mouseY >= cy && mouseY < cy + PICK_CELL_H - 2) {
                        String chosen = filteredMobs.get(idx).id;
                        if (pickerRow >= 0 && pickerRow < mobs.size()) {
                            mobs.get(pickerRow).entityId = chosen;
                        } else {
                            mobs.add(new TrialConfig446.SpawnEntry(chosen, 1));
                        }
                        commitToNode();
                        if (directPickerOnly || !returnToList) {
                            returnToParent();
                        } else {
                            viewMode = VIEW_MOB_POOL;
                            rebuildView();
                        }
                        return true;
                    }
                }
            }
        } else if (button == 0 && viewMode == VIEW_ITEM_PICKER) {
            int cellW = 24;
            int start = pickerScroll * pickerCols;
            for (int r = 0; r < pickerVisibleRows; r++) {
                for (int c = 0; c < pickerCols; c++) {
                    int idx = start + r * pickerCols + c;
                    if (idx >= filteredItems.size()) break;
                    int cx = pickerGridX + c * cellW;
                    int cy = pickerGridY + r * PICK_CELL_H;
                    if (mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 20) {
                        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(
                                filteredItems.get(idx).getItem());
                        if (rl != null) {
                            String itemId = rl.toString();
                            if (pickerRow >= 0 && pickerRow < rewards.size()) {
                                rewards.get(pickerRow).itemId = itemId;
                            } else {
                                rewards.add(new TrialConfig446.RewardEntry(itemId, 1, 1, 1, 100));
                            }
                            commitToNode();
                        }
                        if (directPickerOnly || !returnToList) {
                            returnToParent();
                        } else {
                            viewMode = VIEW_REWARD_POOL;
                            rebuildView();
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
        if (step != 0) {
            if (viewMode == VIEW_MOB_PICKER) {
                int totalRows = (filteredMobs.size() + 1) / 2;
                int maxScroll = Math.max(0, totalRows - pickerVisibleRows);
                pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll + step));
                return true;
            }
            if (viewMode == VIEW_ITEM_PICKER) {
                int totalRows = (filteredItems.size() + pickerCols - 1) / Math.max(1, pickerCols);
                int maxScroll = Math.max(0, totalRows - pickerVisibleRows);
                pickerScroll = Math.max(0, Math.min(maxScroll, pickerScroll + step));
                return true;
            }
            if (viewMode == VIEW_MOB_POOL) {
                int maxScroll = Math.max(0, mobs.size() - visibleRows());
                int next = Math.max(0, Math.min(maxScroll, listScroll + step));
                if (next != listScroll) {
                    listScroll = next;
                    rebuildView();
                }
                return true;
            }
            if (viewMode == VIEW_REWARD_POOL) {
                int maxScroll = Math.max(0, rewards.size() - visibleRows());
                int next = Math.max(0, Math.min(maxScroll, listScroll + step));
                if (next != listScroll) {
                    listScroll = next;
                    rebuildView();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if ((viewMode == VIEW_MOB_PICKER || viewMode == VIEW_ITEM_PICKER)
                && !directPickerOnly && returnToList) {
            viewMode = slot == TrialModule447.SLOT_REWARDS ? VIEW_REWARD_POOL : VIEW_MOB_POOL;
            rebuildView();
            return;
        }
        commitToNode();
        returnToParent();
    }

    private void commitToNode() {
        if (moduleNode == null) return;
        if (!undoRecorded && parentScreen != null) {
            parentScreen.settledlands$remember();
            undoRecorded = true;
        }
        if (slot == TrialModule447.SLOT_MOBS) {
            TrialModule447.writeMobs(moduleNode, mobs);
        } else if (slot == TrialModule447.SLOT_REWARDS) {
            TrialModule447.writeRewards(moduleNode, rewards);
        }
        if (parentScreen != null) {
            NpcDialogueGraph graph = parentScreen.settledlands$graph();
            if (graph != null) TrialModule447.syncCardConfig(graph);
            TrialModuleClient447.requestInspectorRebuild();
            parentScreen.settledlands$changed();
        }
    }

    private void returnToParent() {
        TrialModuleClient447.requestInspectorRebuild();
        if (parentScreen != null) {
            NpcDialogueGraph graph = parentScreen.settledlands$graph();
            if (graph != null) TrialModule447.syncCardConfig(graph);
        }
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String clip(Font font, String text, int maxW) {
        if (font == null || text == null || maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "\u2026") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "" : s + "\u2026";
    }
}
