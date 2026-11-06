package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.WidgetPark361;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 396: the inspector of the chest card - five lines of fact and nothing to edit - and nothing at all
 * for the stock nodes of a chest's graph.
 *
 * <p>The door's panel is three sliders and a toggle because a door has a hack panel to configure. A chest has
 * nothing to configure: its card is the chest's identity, its two outputs are sockets of the card (build
 * 392's rule) and its trigger is the chest being closed. So the panel shows what the card cannot fit - how
 * many times the chest was opened, who opened it last, when the chain runs, and what the two sockets on the
 * right are - and offers no control that would write a field nothing reads.</p>
 *
 * <p>Which means the stock rows of this node must go: the editor does not know the type of a chest node, its
 * switch falls through to the rows of a «Реплика» and every one of them writes a field a chest node does not
 * use. They are <b>parked</b> - moved far above the window and hidden, both - their labels are dropped and
 * the scroll height is reset, which is the rule of build 359 for the «Имя» card and of build 383 for the
 * door, applied here unchanged. The stock rows of every <b>stock</b> node in a chest's graph stay exactly as
 * they are: a «Реплика», a «Триггер» or a «В радиусе» in a chest's graph is the stock node with the stock
 * fields, because the chest runs the same interpreter a block does.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcChestRows396Mixin {

    /** What the five lines take, in the same units the stock rows count in. */
    private static final int CONTENT = 12 * 5;

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

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$chestRows(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!ChestGraph396.isChestGraphNode(node)) return;

        settledlands$parkStockRows();
        sidebarHeight = 0;

        int x = inspectorX + 8;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            label(ChestGraph396.labelKey(node.type), x, y);
            y += 12;
            label(Component.translatable("gui.settledlands.chest.count",
                    Component.literal(Integer.toString(ChestGraph396.count(node)))).getString(), x, y);
            y += 12;
            String who = ChestGraph396.opener(node);
            label(who.isEmpty()
                    ? Component.translatable("gui.settledlands.chest.opener_none").getString()
                    : Component.translatable("gui.settledlands.chest.opener_last",
                            Component.literal(who)).getString(), x, y);
            y += 12;
            label(Component.translatable("gui.settledlands.chest.trigger").getString(), x, y);
            y += 12;
            label(Component.translatable("gui.settledlands.chest.pin_hint").getString(), x, y);
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
}
