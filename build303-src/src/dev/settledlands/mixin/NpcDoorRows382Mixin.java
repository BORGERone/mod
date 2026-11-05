package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.DoorGraphClient382;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.WidgetPark361;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382/383/385: the whole inspector of a door node - three sliders and a toggle, nothing else.
 *
 * <p>The author asked for exactly this: «нода с 2 ползунками (Кол-во чисел, Скорость) и тумблером
 * открыта/закрыта». Build 382 appended those three rows under the stock ones and the screenshot came
 * back showing the rows of a «Реплика» above them - a text box, a trigger, a relation change, min/max
 * of both - because the editor does not know the type of a door node and its switch falls through to
 * the body's rows. Every one of those writes a field a door node does not use. Build 359 met this with
 * the «Имя» card of a block and answered it in one way, which build 383 applies here unchanged: the
 * stock rows of this node are <b>parked</b> - moved far above the window and hidden, both - their
 * labels are dropped, the scroll height is reset, and the three rows below are the whole inspector.</p>
 *
 * <ul>
 *   <li><b>Кол-во чисел</b> 4..10 - how many keys the hack panel of build 377 asks for at this door;</li>
 *   <li><b>Скорость</b> 1..10 с - how often one of those keys changes (the stock panel used 5);</li>
 *   <li><b>Сложность</b> 1..3 (build 385) - which rows of the keyboard the panel draws its keys from:
 *       {@code qwertyuiop[]}, then {@code +asdfghjkl;'}, then {@code +zxcvbnm,./};</li>
 *   <li><b>Закрыта / Открыта</b> - «Закрыта»: the door opens only by solving the panel; «Открыта»:
 *       an ordinary door. The flag is the lock of build 368, so every rule of that build follows.</li>
 * </ul>
 *
 * <p><b>Build 383: every write goes through {@code propertyChange}.</b> Build 382 wrote the node
 * straight and called {@code changed()} itself, and the lesson of build 362 says why that is not the
 * same thing: the screen's own commit is what takes the undo point the stock rows share, marks the
 * graph dirty and shows the change on the card without a rebuild. The three rows are the stock path
 * now, and the scroll range is grown by their height, otherwise the toggle would sit below the part of
 * the panel that can be scrolled to and never be seen - the same bug the name rows of build 361 had.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDoorRows382Mixin {

    /** One row of the inspector: a slider is 20 px tall, a button 16, the stock gap is 2. */
    private static final int ROW = 22;

    /** What the four rows plus their title take, in the same units the stock rows count in. */
    private static final int CONTENT = 12 + ROW + ROW + ROW + 20;

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

    @Shadow
    private List labels;

    @Shadow
    private void label(String key, int x, int y) {
        throw new AssertionError();
    }

    /**
     * The screen's own commit: undo point, write, dirty flag. Build 382 skipped it and the values of
     * a door node were changed on screen without the editor knowing - the lesson of the block's name
     * colour, shipped again.
     */
    @Shadow
    private void propertyChange(Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$doorRows(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!DoorGraph382.isDoorNode(node)) return;

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
            label("gui.settledlands.door.node", x, y);
            y += 12;

            y = settledlands$slider(node, x, y, width, top, bottom, true);
            y = settledlands$slider(node, x, y, width, top, bottom, false);
            y = settledlands$difficulty(node, x, y, width, top, bottom);
            y = settledlands$seal(node, x, y, width, top, bottom);
        } finally {
            buildingInspector = false;
        }
        sidebarHeight += CONTENT;
    }

    /** Push every row and every label of this node out of the panel - hidden and moved, both. */
    private void settledlands$parkStockRows() {
        if (labels != null) labels.clear();
        if (inspectorWidgets == null) return;
        for (Object widget : inspectorWidgets.toArray()) {
            WidgetPark361.park(widget);
        }
    }

    /** One slider; {@code keys} picks which of the two values it edits. Returns the next free y. */
    private int settledlands$slider(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom, boolean keys) {
        int min = keys ? DoorGraph382.MIN_KEYS : DoorGraph382.MIN_SPEED;
        int max = keys ? DoorGraph382.MAX_KEYS : DoorGraph382.MAX_SPEED;
        int value = keys ? DoorGraph382.keys(node) : DoorGraph382.speed(node);
        NpcUi.NpcSlider slider = NpcUi.slider(x, y, width, min, max, value, shown -> {
            String text = keys
                    ? DoorGraphClient382.safe("gui.settledlands.door.count", Integer.toString(shown))
                    : DoorGraphClient382.safe("gui.settledlands.door.speed", Integer.toString(shown));
            return Component.literal(text);
        }, shown -> {
            boolean same = keys ? shown == DoorGraph382.keys(node) : shown == DoorGraph382.speed(node);
            if (same) return;
            propertyChange(() -> {
                if (keys) DoorGraph382.setKeys(node, shown);
                else DoorGraph382.setSpeed(node, shown);
            });
        });
        if (slider == null) return y;
        // Build 359 lesson: a member inherited from Minecraft is CALLED, never shadowed - Mixin
        // refuses a shadow it cannot find in the target class itself, and every graph screen died on
        // first open. The call site lands inside the merged screen, so the protected access holds.
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(slider);
        if (inspectorWidgets != null) inspectorWidgets.add(slider);
        slider.visible = y >= top && y + 20 <= bottom;
        return y + ROW;
    }

    /**
     * Build 385: «Сложность» of the panel - 1, 2 or 3, one slider step per row of the keyboard that
     * joins the board ({@code qwertyuiop[]} -> {@code +asdfghjkl;'} -> {@code +zxcvbnm,./}). It is a
     * slider like the other two, and it commits through the same {@code propertyChange}.
     */
    private int settledlands$difficulty(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom) {
        NpcUi.NpcSlider slider = NpcUi.slider(x, y, width,
                DoorGraph382.MIN_DIFFICULTY, DoorGraph382.MAX_DIFFICULTY,
                DoorGraph382.difficulty(node), shown ->
                Component.literal(DoorGraphClient382.safe("gui.settledlands.door.difficulty",
                        Integer.toString(shown))),
                shown -> {
                    if (shown == DoorGraph382.difficulty(node)) return;
                    propertyChange(() -> DoorGraph382.setDifficulty(node, shown));
                });
        if (slider == null) return y;
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(slider);
        if (inspectorWidgets != null) inspectorWidgets.add(slider);
        slider.visible = y >= top && y + 20 <= bottom;
        return y + ROW;
    }

    /** The «закрыта/открыта» toggle; the label says the state the click will produce. */
    private int settledlands$seal(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom) {
        Button toggle = button("gui.settledlands.door.seal", x, y, width, 16, () ->
                propertyChange(() -> DoorGraph382.setSealed(node, !DoorGraph382.sealed(node))));
        if (toggle == null) return y;
        toggle.setMessage(Component.translatable(DoorGraph382.sealed(node)
                ? "gui.settledlands.door.sealed" : "gui.settledlands.door.unsealed"));
        toggle.visible = y >= top && y + toggle.getHeight() <= bottom;
        return y + 20;
    }
}
