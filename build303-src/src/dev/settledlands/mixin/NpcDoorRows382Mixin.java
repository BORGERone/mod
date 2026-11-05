package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.DoorGraphClient382;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382: the three controls of a door node, in the inspector - two sliders and a toggle.
 *
 * <p>The author asked for exactly this: «нода с 2 ползунками (Кол-во чисел, Скорость) и тумблером
 * открыта/закрыта». The sliders are the stock {@code NpcUi#slider} widget the config screen of this
 * mod already uses everywhere (a real slider with a real grab handle, not a number box that looks like
 * one), and the toggle is a stock button that says which of the two states it will switch to.</p>
 *
 * <ul>
 *   <li><b>Кол-во чисел</b> 4..10 - how many keys the hack panel of build 377 asks for at this door;</li>
 *   <li><b>Скорость</b> 1..10 с - how often one of those keys changes (the stock panel used 5);</li>
 *   <li><b>Закрыта / Открыта</b> - «Закрыта»: the door opens only by solving the panel; «Открыта»:
 *       an ordinary door. The flag is the lock of build 368, so every rule of that build follows.</li>
 * </ul>
 *
 * <p>The undo history is not flooded: the sliders snapshot the graph once per drag gesture (the same
 * 600 ms window the stock property edits use), not once per pixel. The rows are appended after the
 * stock ones, exactly where build 354 puts its own row, and they are hidden when the panel is scrolled
 * away - a control the author cannot see must not take a click.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDoorRows382Mixin {

    /** One row of the inspector: a slider is 20 px tall, a button 16, and the stock gap is 2. */
    private static final int ROW = 22;

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
    private long propertyUndoAt;

    @Shadow
    private void remember() {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
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

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int top = inspectorY + 22;
        int bottom = inspectorY + inspectorH - 4;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            y = settledlands$slider(node, x, y, width, top, bottom, true);
            y = settledlands$slider(node, x, y, width, top, bottom, false);
            y = settledlands$seal(node, x, y, width, top, bottom);
        } finally {
            buildingInspector = false;
        }
        sidebarHeight = y - (inspectorY + 24 - sidebarScroll) - 2;
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
            settledlands$snapshot();
            if (keys) DoorGraph382.setKeys(node, shown);
            else DoorGraph382.setSpeed(node, shown);
            changed();
        });
        if (slider == null) return y;
        // Build 359 lesson: a member inherited from Minecraft is CALLED, never shadowed - Mixin
        // refuses a shadow it cannot find in the target class itself, and every graph screen died on
        // first open. The call site lands inside the merged screen, so the protected access holds.
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(slider);
        slider.visible = y >= top && y + 20 <= bottom;
        return y + ROW;
    }

    /** The «закрыта/открыта» toggle; the label says the state the click will produce. */
    private int settledlands$seal(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom) {
        Button toggle = button("gui.settledlands.door.seal", x, y, width, 16, () -> {
            settledlands$snapshot();
            DoorGraph382.setSealed(node, !DoorGraph382.sealed(node));
            changed();
        });
        if (toggle == null) return y;
        toggle.setMessage(Component.translatable(DoorGraph382.sealed(node)
                ? "gui.settledlands.door.sealed" : "gui.settledlands.door.unsealed"));
        toggle.visible = y >= top && y + toggle.getHeight() <= bottom;
        return y + ROW;
    }

    /** One undo entry per gesture, not per pixel - the same 600 ms window the stock rows use. */
    private void settledlands$snapshot() {
        long now = System.currentTimeMillis();
        if (now - propertyUndoAt < 600L) return;
        propertyUndoAt = now;
        remember();
    }
}
