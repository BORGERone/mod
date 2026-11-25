package dev.settledlands.mixin;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.LootPoolCard436;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 436: inspector sidebar for the «Пул предметов» node (pool file selector dropdown +
 * max-rarity / max-weight / drop-rolls inputs + incoming-If helper + unlink/delete).
 * Zero synthetic lambdas are declared in this mixin class.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class LootPoolInspectorMixin436 {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private int selected;

    @Shadow
    private int inspectorX;

    @Shadow
    private int inspectorY;

    @Shadow
    private int inspectorW;

    @Shadow
    private int inspectorH;

    @Shadow
    private int sidebarScroll;

    @Shadow
    private int sidebarHeight;

    @Shadow
    private boolean buildingInspector;

    @Shadow
    private List inspectorWidgets;

    private NpcDialogueGraph.Node settledlands$lootPoolNode436;
    private int settledlands$lootPoolSelected436 = -1;
    private int settledlands$lootPoolDeleteId436 = -1;

    @Shadow
    private void remember() {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Shadow
    private void propertyChange(Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private void label(String key, int x, int y) {
        throw new AssertionError();
    }

    @Shadow
    private EditBox textBox(String value, int maxLength, int x, int y, int width, int height,
            Consumer<String> onChange) {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private int incomingIfSection(NpcDialogueGraph.Node node, int x, int y, int width) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("HEAD"), remap = false)
    private void settledlands$skipStockForLootPool436(CallbackInfo ci) {
        settledlands$lootPoolNode436 = null;
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!LootPool436.isLootPoolNode(node)) return;
        settledlands$lootPoolNode436 = node;
        settledlands$lootPoolSelected436 = selected;
        selected = -1;
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$buildLootPoolInspector436(CallbackInfo ci) {
        NpcDialogueGraph.Node node = settledlands$lootPoolNode436;
        settledlands$lootPoolNode436 = null;
        if (node == null) return;

        selected = settledlands$lootPoolSelected436;
        settledlands$lootPoolDeleteId436 = node.id;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll;
        int startIndex = inspectorWidgets.size();

        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        buildingInspector = true;
        try {
            label(LootPool436.TITLE_KEY, x, y);
            y += 14;

            label("gui.settledlands.loot_pool.file", x, y);
            y += 11;

            String currentFile = LootPool436.poolFileOf(node);
            List<String> options = LootPoolCard436.poolOptions(currentFile);
            int dropWidth = Math.max(60, width - 24);
            NpcUi.NpcDropdown dropdown = NpcUi.dropdown(
                    x, y, dropWidth,
                    LootPoolCard436.poolDropdownEntries(options),
                    LootPoolCard436.poolDropdownIndex(options, currentFile),
                    LootPoolCard436.poolPicker(this::settledlands$lpCommit436, node, options));
            LootPoolCard436.trackPoolDropdown(
                    dropdown, options, screen, node, this::settledlands$lpCommit436);
            screen.addRenderableWidget(dropdown);
            inspectorWidgets.add(dropdown);

            AbstractWidget gearBtn = LootPoolCard436.gearButton(x + width - 20, y, screen, node);
            screen.addRenderableWidget(gearBtn);
            inspectorWidgets.add(gearBtn);
            y += 24;

            label("gui.settledlands.loot_pool.limits_hint", x, y);
            y += 12;

            label("gui.settledlands.loot_pool.max_rarity", x, y);
            y += 10;
            EditBox rarityBox = textBox(LootPool436.rawRarity(node), LootPool436.MAX_FIELD_LEN,
                    x, y, width, 20, LootPoolCard436.raritySetter(node));
            rarityBox.setHint(Component.literal("0-100"));
            rarityBox.setFilter(LootPool436::isRangeInput);
            y += 23;

            label("gui.settledlands.loot_pool.max_weight", x, y);
            y += 10;
            EditBox weightBox = textBox(LootPool436.rawWeight(node), LootPool436.MAX_FIELD_LEN,
                    x, y, width, 20, LootPoolCard436.weightSetter(node));
            weightBox.setHint(Component.literal("40"));
            weightBox.setFilter(LootPool436::isRangeInput);
            y += 23;

            label("gui.settledlands.loot_pool.rolls", x, y);
            y += 10;
            EditBox rollsBox = textBox(LootPool436.rawRolls(node), LootPool436.MAX_FIELD_LEN,
                    x, y, width, 20, LootPoolCard436.rollsSetter(node));
            rollsBox.setHint(Component.literal("3"));
            rollsBox.setFilter(LootPool436::isRangeInput);
            y += 25;

            label("gui.settledlands.loot_pool.folder_hint", x, y);
            y += 11;
            label("gui.settledlands.loot_pool.dungeon_only", x, y);
            y += 14;

            y = incomingIfSection(node, x, y, width);
            y += 4;

            int half = (width - 4) / 2;
            button("gui.settledlands.graph.unlink", x, y, half, 16,
                    LootPoolCard436.unlinkAction(screen, graph, node.id));
            button("gui.settledlands.graph.delete", x + half + 4, y, width - half - 4, 16,
                    this::settledlands$lpDelete436);
            y += 22;
        } finally {
            buildingInspector = false;
        }

        sidebarHeight = y + sidebarScroll - (inspectorY + 24);
        int topBound = inspectorY + 22;
        int bottomBound = inspectorY + inspectorH - 4;
        for (int i = startIndex; i < inspectorWidgets.size(); i++) {
            AbstractWidget w = (AbstractWidget) inspectorWidgets.get(i);
            w.visible = w.getY() >= topBound && w.getY() + w.getHeight() <= bottomBound;
        }
    }

    private void settledlands$lpCommit436(Runnable action) {
        propertyChange(action);
    }

    private void settledlands$lpDelete436() {
        if (graph == null || settledlands$lootPoolDeleteId436 < 0) return;
        remember();
        graph.remove(settledlands$lootPoolDeleteId436);
        selected = -1;
        changed();
        widgets();
    }
}
