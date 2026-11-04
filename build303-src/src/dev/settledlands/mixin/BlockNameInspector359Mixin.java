package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import java.util.function.Consumer;
import dev.settledlands.npc.client.WidgetPark361;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 359/361: the two properties of a block's «Имя» node, edited the way every other node property
 * is edited - as rows of the inspector.
 *
 * <p>The rows are added at the end of {@code widgets()}, after the screen has laid out and clipped its
 * own, so their position is read from what the screen left behind: the content of the inspector ends
 * {@code sidebarHeight} past the top of the scroll area, and every row moves with
 * {@code sidebarScroll}. The scroll range is grown by the height of the two rows, otherwise the second
 * one would exist below the reachable part of the panel and never be seen.</p>
 *
 * <p><b>Имя</b> is the stock single-line text box, written straight into {@code Node#text} - the field
 * a «Реплика» node keeps its line in, and the one the dialogue reads as the speaker of the block.
 * The box caps the name at {@link BlockName359#MAX_NAME} characters, so what the card shows and what
 * a window can render are the same string.</p>
 *
 * <p><b>Цвет</b> is the stock dropdown, filled with the four colours this mod already renders a
 * speaker name in, written into {@code Node#varValue}. Picking one repaints the card on the spot and
 * is what the dialogue is told when the block speaks.</p>
 *
 * <p><b>Build 361: the stock rows of this node are taken away.</b> The editor does not know the type
 * of a name card, so its switch falls through to the rows of a «Реплика» - a text box, a trigger, a
 * relation change, points - every one of which writes a field the name card does not use, and the
 * author saw a name card with a line editor on it. A name is <i>one</i> name and <i>one</i> colour, so
 * the screen's own rows for this node are hidden and moved out of the panel, its labels are dropped,
 * and the two rows below are the whole inspector of the node. The hiding is a park, not a flag: the
 * widgets are pushed far above the window, so no later frame can bring them back (that is exactly how
 * the tabs of build 360 came back).</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockNameInspector359Mixin {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockName359");

    @Shadow private NpcDialogueGraph graph;

    @Shadow private int selected;

    @Shadow private int inspectorX;

    @Shadow private int inspectorY;

    @Shadow private int inspectorW;

    @Shadow private int inspectorH;

    @Shadow private int sidebarScroll;

    @Shadow private int sidebarHeight;

    @Shadow private boolean buildingInspector;

    @Shadow private List inspectorWidgets;

    @Shadow private List labels;

    @Shadow private void label(String key, int x, int y) { throw new AssertionError(); }

    /**
     * The screen's own way of committing a property: it takes an undo point (throttled to one every
     * few hundred milliseconds), runs the write, and marks the graph changed - which is what the save
     * and the dirty flag read. Build 362 shipped the two rows below writing the node directly, so the
     * colour of a name was picked, shown by the list, and then lost: nothing had told the screen that
     * anything had changed. Every stock row of every other node goes through this.
     */
    @Shadow private void propertyChange(Runnable action) { throw new AssertionError(); }

    @Shadow private EditBox textBox(String value, int maxLen, int x, int y, int width, int height,
            Consumer<String> responder) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$blockNameRows(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        if (!BlockTrigger351.isBlockEditorId(((NpcDialogueGraphScreen) (Object) this).entityId())) return;
        Node node = graph.get(selected);
        if (node == null || node.type != BlockName359.TYPE) return;

        // The rows the stock switch built for a type it does not know: parked, and their labels with
        // them. Both lists were cleared at the start of this same widgets() call, so everything in
        // them belongs to the node that is selected right now.
        settledlands$parkStockRows();
        sidebarHeight = 0;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int top = inspectorY + 22;
        int bottom = inspectorY + inspectorH - 4;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            label(BlockName359.FIELD_KEY, x, y);
            y += 11;
            EditBox box = textBox(BlockName359.nameOf(node), BlockName359.MAX_NAME, x, y, width, 18,
                    value -> propertyChange(() -> BlockName359.setName(node, value)));
            if (box != null) box.visible = y >= top && y + box.getHeight() <= bottom;
            y += 22;

            label(BlockName359.COLOR_KEY, x, y);
            y += 11;
            NpcUi.NpcDropdown picker = NpcUi.dropdown(x, y, width, List.of(
                    Component.translatable(BlockName359.COLOR_KEYS[0]),
                    Component.translatable(BlockName359.COLOR_KEYS[1]),
                    Component.translatable(BlockName359.COLOR_KEYS[2]),
                    Component.translatable(BlockName359.COLOR_KEYS[3])),
                    BlockName359.colorOf(node), index -> {
                        // One line in the log per pick: if a colour ever fails again, the log says
                        // whether the list was reached at all and what it asked to store.
                        LOG.warn("[settledlands] block name colour: index " + index);
                        propertyChange(() -> BlockName359.setColor(node, index));
                    });
            if (picker != null) {
                // Inherited from Screen, so it is called and not shadowed: Mixin looks a @Shadow up
                // in the target class itself, and a member inherited from a Minecraft superclass is
                // not there - build 359 crashed every graph screen on exactly that (see the class
                // doc of BlockNoTabs360Mixin). The stub of Screen declares the erased signature, so
                // this compiles to the same call the stock textBox makes.
                ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(picker);
                if (inspectorWidgets != null) inspectorWidgets.add(picker);
                picker.visible = y >= top && y + picker.getHeight() <= bottom;
            }
        } finally {
            buildingInspector = false;
        }
        sidebarHeight += 66;
    }

    /** Push every row and every label of this node out of the panel - hidden and moved, both. */
    private void settledlands$parkStockRows() {
        if (labels != null) labels.clear();
        if (inspectorWidgets == null) return;
        for (Object widget : inspectorWidgets) {
            WidgetPark361.park(widget);
        }
    }

}
