package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.WidgetPark361;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 360/361/362: a block's graph has no tabs.
 *
 * <p>An NPC's editor is three graphs on three tabs - dialogue, walking, animation. A block has exactly
 * one graph; the two extra tabs could only ever be empty, and they were already refused a click
 * (build 351). A refused tab is still a tab: the author clicks it, gets a line in the status bar and
 * spends the next minute wondering which of the three graphs his block uses. So for a block the tab
 * strip is not on the screen at all.</p>
 *
 * <p>The strip is not painted by hand - it is three ordinary stock buttons created in {@code widgets()},
 * one per tab label. Build 360 hid the button it had just built ({@code visible = false}), build 361
 * hid it and moved it out of the window, and both came back from the game with the strip still in the
 * top bar: a widget on a screen is somebody else's to keep showing, and asking it to be quiet is not the
 * same as taking it away. Build 362 therefore does the removal itself, after the screen has finished
 * building its widgets: the three buttons whose label is one of the tab labels are removed from the
 * screen's own lists ({@code WidgetPark361#park(Object, Object)}), so there is no widget left to draw
 * and no widget left to click.</p>
 *
 * <p>The button is still created - the loop around it writes the "this is the current tab" flag into it,
 * so a missing button would be a crash - it is simply not part of the screen any more. The
 * {@code @Redirect} that hides it at creation stays as the first line of defence, and the guard on
 * {@code switchTab} (build 351) stays, so a hotkey cannot reach the two graphs a block does not have.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockNoTabs360Mixin {

    /** What the last logged rebuild of a block's widgets found, so the log is not written every frame. */
    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/BlockTabs362");

    private static int settledlands$lastParked = -1;

    /** The box the stock tab strip is built in: y, height and the left edge of the first tab. */
    private static final int TAB_Y = 10;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_MIN_X = 66;

    /** The three stock tab labels. A block keeps neither, so the three keys are the whole test. */
    private static final String[] TAB_KEYS = {
        "gui.settledlands.graph.tab_main",
        "gui.settledlands.graph.tab_walk",
        "gui.settledlands.graph.tab_anim",
    };

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Redirect(method = "widgets()V",
              at = @At(value = "INVOKE",
                       target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;"
                               + "button(Ljava/lang/String;IIIILjava/lang/Runnable;)"
                               + "Lnet/minecraft/client/gui/components/Button;"),
              remap = false)
    private Button settledlands$blockHasNoTabs(String key, int x, int y, int width, int height,
            Runnable action) {
        Button made = button(key, x, y, width, height, action);
        if (made == null || key == null || !key.startsWith(settledlands$tabPrefix())) return made;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return made;
        WidgetPark361.park(screen, made);
        return made;
    }

    /**
     * And the removal that does not depend on the creation path: once the screen has built its widgets,
     * every button that shows a tab label is taken off the screen - whichever call built it, and no
     * matter what some other code did to it in between.
     */
    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$blockHasNoTabStrip(CallbackInfo ci) {
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        List<Object> children = WidgetPark361.children(screen);
        if (children.isEmpty()) return;
        int parked = 0;
        String main = settledlands$tabLabel(0);
        String walk = settledlands$tabLabel(1);
        String anim = settledlands$tabLabel(2);
        for (Object child : children.toArray()) {
            String label = WidgetPark361.label(child);
            boolean byLabel = !label.isEmpty()
                    && (label.equals(main) || label.equals(walk) || label.equals(anim));
            // Two ways to know a tab, because a strip that survives two builds deserves a second
            // question: the label it shows, and the box the stock strip is laid out in (y 10, height 20,
            // from x 66 - undo and redo are the same box at x 10 and 38).
            boolean byBox = WidgetPark361.isTabStrip(child, TAB_Y, TAB_HEIGHT, TAB_MIN_X);
            // Build 362 took the block's own «удалить граф» button off the bar with the strip: the
            // button of build 355 is built in exactly this box (x 66, y 10, h 20) and the box rule,
            // unlike the label rule, could not tell the two apart. Anything this mod declared with
            // WidgetPark361#keep - the delete button marks itself when it is built - is skipped.
            if (WidgetPark361.isKept(child)) continue;
            if (byLabel || byBox) {
                WidgetPark361.park(screen, child);
                parked++;
            }
        }
        // One line in the log per different outcome: if a strip ever survives again, the log says
        // whether this hook ran at all and how many buttons it recognised.
        if (parked != settledlands$lastParked) {
            settledlands$lastParked = parked;
            LOG.warn("[settledlands] block graph: " + parked + " tab button(s) taken off the screen of "
                    + children.size() + " widgets");
        }
    }

    private static String settledlands$tabLabel(int index) {
        return Component.translatable(TAB_KEYS[index]).getString();
    }

    private static String settledlands$tabPrefix() {
        return "gui.settledlands.graph.tab_";
    }
}
