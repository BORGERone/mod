package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.ChestGraphClient396;
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
 * Build 396/397: the whole inspector of the chest card - three sliders and a toggle, plus the facts the card
 * cannot fit - and nothing at all for the stock nodes of a chest's graph.
 *
 * <p>Build 396 gave a chest a graph and a card, and its panel could only be read: five lines of fact and no
 * control. The author's verdict was one line - «в графе сундука я не могу настраивать взлом» - and it is the
 * door's own story from build 382, one build late: a card that shows a lock the author cannot set is not a
 * setting. So the four rows of the door card ({@code NpcDoorRows382Mixin}) are here, writing the chest's own
 * fields, and the facts that were the whole of the 396 panel move under them.</p>
 *
 * <ul>
 *   <li><b>Кол-во чисел</b> 4..10 - how many keys the lock panel of this chest asks for (the stock panel
 *       used 6);</li>
 *   <li><b>Скорость</b> 1..20 с - how often one of those keys changes (the stock panel used 5). The door
 *       stops at 10; a chest is a slower thing to crack, and the author's own hack tool allows 20;</li>
 *   <li><b>Сложность</b> 1..3 - which rows of the keyboard the panel draws from: {@code qwertyuiop[]}, then
 *       {@code +asdfghjkl;'}, then {@code +zxcvbnm,./};</li>
 *   <li><b>Закрыта / Открыта</b> - «Закрыта»: the chest opens only by solving the panel; «Открыта»: an
 *       ordinary chest. The card flag <b>is</b> the lock (build 397), so saving the graph locks the chest and
 *       clearing the flag frees it - there is no second store to fall out of step.</li>
 * </ul>
 *
 * <p><b>The stock rows must go.</b> The editor does not know the type of a chest node, its switch falls
 * through to the rows of a «Реплика» and every one of them writes a field a chest node does not use. They
 * are <b>parked</b> - moved far above the window and hidden, both - their labels are dropped and the scroll
 * height is reset, which is the rule of build 359 for the «Имя» card, of build 383 for the door, and of build
 * 396 for this card, applied here unchanged. The stock rows of every <b>stock</b> node in a chest's graph
 * stay exactly as they are: a «Реплика», a «Триггер» or a «В радиусе» in a chest's graph is the stock node
 * with the stock fields, because the chest runs the same interpreter a block does.</p>
 *
 * <p><b>Every write goes through {@code propertyChange}</b>, the lesson of build 362 that build 382 shipped
 * without and had to fix in 383: the screen's own commit is what takes the undo point the stock rows share,
 * marks the graph dirty and shows the change on the card without a rebuild. The scroll range grows by the
 * height of these rows as well, or the last one would sit below the part of the panel that can be scrolled
 * to and never be seen.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcChestRows396Mixin {

    /** One row of the inspector: a slider is 20 px tall, a button 16, the stock gap is 2. */
    private static final int ROW = 22;

    /** The four rows, their title, and the three lines of fact under them. */
    private static final int CONTENT = 12 + ROW + ROW + ROW + 20 + 12 + 12 + 12;

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

    @Shadow
    private void propertyChange(Runnable action) {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$chestRows(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        // Build 396: only the chest card itself replaces the stock rows. Every stock node of a chest's graph
        // keeps the stock inspector, because it is the stock node with the stock fields.
        if (!ChestGraph396.isChestGraphNode(node)) return;

        settledlands$parkStockRows();
        sidebarHeight = 0;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int top = inspectorY + 22;
        int bottom = inspectorY + inspectorH - 4;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            label(ChestGraph396.labelKey(node.type), x, y);
            y += 12;

            // Build 397: the settings the author could not reach. They are the door's rows, in the door's
            // order, writing the chest's fields through ChestLock397 - so the panel of this chest asks for
            // exactly the numbers the author sets here.
            y = settledlands$slider(node, x, y, width, top, bottom, true);
            y = settledlands$slider(node, x, y, width, top, bottom, false);
            y = settledlands$difficulty(node, x, y, width, top, bottom);
            y = settledlands$lock(node, x, y, width, top, bottom);

            // The facts that were the whole of the 396 panel, kept under the controls: how many times the
            // chest was opened, who opened it last, and what the two sockets on the right of the card are.
            label(Component.translatable("gui.settledlands.chest.count",
                    Component.literal(Integer.toString(ChestGraph396.count(node)))).getString(), x, y);
            y += 12;
            String who = ChestGraph396.opener(node);
            label(who.isEmpty()
                    ? Component.translatable("gui.settledlands.chest.opener_none").getString()
                    : Component.translatable("gui.settledlands.chest.opener_last",
                            Component.literal(who)).getString(), x, y);
            y += 12;
            label(Component.translatable("gui.settledlands.chest.pin_hint").getString(), x, y);
            y += 12;
            sidebarHeight += CONTENT + 12;
        } finally {
            buildingInspector = false;
        }
    }

    /** Push every row and every label of this node out of the panel - hidden and moved, both. */
    private void settledlands$parkStockRows() {
        if (labels != null) labels.clear();
        if (inspectorWidgets == null) return;
        for (Object widget : inspectorWidgets.toArray()) {
            WidgetPark361.park(widget);
        }
    }

    /** One of the two value sliders; {@code keys} picks which. Returns the next free y. */
    private int settledlands$slider(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom, boolean keys) {
        int min = keys ? ChestLock397.MIN_KEYS : ChestLock397.MIN_SPEED;
        int max = keys ? ChestLock397.MAX_KEYS : ChestLock397.MAX_SPEED;
        int value = keys ? ChestLock397.keys(node) : ChestLock397.seconds(node);
        NpcUi.NpcSlider slider = NpcUi.slider(x, y, width, min, max, value, shown -> {
            String text = keys
                    ? ChestGraphClient396.safe("gui.settledlands.chest.keys", Integer.toString(shown))
                    : ChestGraphClient396.safe("gui.settledlands.chest.speed", Integer.toString(shown));
            return Component.literal(text);
        }, shown -> {
            boolean same = keys ? shown == ChestLock397.keys(node) : shown == ChestLock397.seconds(node);
            if (same) return;
            propertyChange(() -> {
                if (keys) ChestLock397.setKeys(node, shown);
                else ChestLock397.setSeconds(node, shown);
            });
        });
        if (slider == null) return y;
        // Build 359 lesson: a member inherited from Minecraft is CALLED, never shadowed - Mixin refuses a
        // shadow it cannot find in the target class itself, and every graph screen died on first open.
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(slider);
        if (inspectorWidgets != null) inspectorWidgets.add(slider);
        slider.visible = y >= top && y + 20 <= bottom;
        return y + ROW;
    }

    /** «Сложность» of the panel: 1, 2 or 3 rows of the keyboard, one slider step each. */
    private int settledlands$difficulty(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom) {
        NpcUi.NpcSlider slider = NpcUi.slider(x, y, width,
                ChestLock397.MIN_DIFFICULTY, ChestLock397.MAX_DIFFICULTY,
                ChestLock397.difficulty(node), shown ->
                Component.literal(ChestGraphClient396.safe("gui.settledlands.chest.difficulty",
                        Integer.toString(shown))),
                shown -> {
                    if (shown == ChestLock397.difficulty(node)) return;
                    propertyChange(() -> ChestLock397.setDifficulty(node, shown));
                });
        if (slider == null) return y;
        ((NpcDialogueGraphScreen) (Object) this).addRenderableWidget(slider);
        if (inspectorWidgets != null) inspectorWidgets.add(slider);
        slider.visible = y >= top && y + 20 <= bottom;
        return y + ROW;
    }

    /**
     * The «закрыта/открыта» toggle; the label says the state the click will produce.
     *
     * <p><b>Build 398: the label follows the click.</b> Same bug as the door's, found at the same time by the
     * author («и в графе сундука»): the message was written once when the inspector was built, so the word on
     * the button changed only when the graph was reopened. It is now asked for in the click itself.</p>
     */
    private int settledlands$lock(NpcDialogueGraph.Node node, int x, int y, int width,
            int top, int bottom) {
        final Button[] self = new Button[1];
        Button toggle = button("gui.settledlands.chest.lock", x, y, width, 16, () -> {
            propertyChange(() -> ChestLock397.setLocked(node, !ChestLock397.locked(node)));
            if (self[0] != null) self[0].setMessage(settledlands$lockWord(node));
        });
        if (toggle == null) return y;
        self[0] = toggle;
        toggle.setMessage(settledlands$lockWord(node));
        toggle.visible = y >= top && y + toggle.getHeight() <= bottom;
        return y + 20;
    }

    /** «Закрыта (только взлом)» or «Открыта (обычный клик)», read from the node as it is right now. */
    private static Component settledlands$lockWord(NpcDialogueGraph.Node node) {
        return Component.translatable(ChestLock397.stateKey(node));
    }
}
