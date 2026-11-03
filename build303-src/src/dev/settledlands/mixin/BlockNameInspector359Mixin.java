package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 359: the two properties of a block's «Имя» node, edited the way every other node property is
 * edited - as rows of the inspector.
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
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockNameInspector359Mixin {

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

    @Shadow private void label(String key, int x, int y) { throw new AssertionError(); }

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
                    value -> BlockName359.setName(node, value));
            if (box != null) box.visible = y >= top && y + box.getHeight() <= bottom;
            y += 22;

            label(BlockName359.COLOR_KEY, x, y);
            y += 11;
            NpcUi.NpcDropdown picker = NpcUi.dropdown(x, y, width, List.of(
                    Component.translatable(BlockName359.COLOR_KEYS[0]),
                    Component.translatable(BlockName359.COLOR_KEYS[1]),
                    Component.translatable(BlockName359.COLOR_KEYS[2]),
                    Component.translatable(BlockName359.COLOR_KEYS[3])),
                    BlockName359.colorOf(node), index -> BlockName359.setColor(node, index));
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

}
