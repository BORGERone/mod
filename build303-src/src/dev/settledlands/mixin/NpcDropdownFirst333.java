package dev.settledlands.mixin;

import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 333: an open list owns the click before anything else on the screen does.
 *
 * <p>The list of a dropdown is painted last, over everything - {@code render} pushes it to
 * Z+500 - but the click was looked up late. The screen's click chain is: this mixin, the
 * pursuit card menu, the value pins, the radius toggle, the points, then the body of
 * {@code mouseClicked}, which walks the auto-AI card, then the stats node, and only in its
 * fall-through reaches the graph's own code that owns the open dropdown. Every one of those
 * steps in front of that fall-through hit-tests something on the canvas - a card, a pin, a
 * toggle, a point - and none of them knows a list is open. So a click on a row that happens to
 * lie over a node card was taken by the card: the node was selected or dragged, and the list,
 * although it was the thing under the cursor and the thing drawn on top, never saw the click.
 * </p>
 *
 * <p>The list is drawn from {@code openDropdown}, and this hook reads the same field, so what
 * is on the screen is exactly what takes the click: the row under the cursor is chosen, a click
 * anywhere else only closes the list, and the click goes no further in either case. No node, no
 * card, no pin, no point and no cable can be touched while a list is open.</p>
 *
 * <p>This mixin is first in the client list of {@code settledlands.mixins.json} so that its
 * HEAD hook runs before the HEAD hooks of the builds after 272. Scrolling is untouched: the
 * screen's own {@code mouseScrolled} already asks the open dropdown first.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcDropdownFirst333 {

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropdownFirst(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcUi.NpcDropdown popup = screen.activeDropdown();
        if (popup == null || !popup.popupOpen()) return;
        // The left button picks the row under the cursor; the others only put the list away.
        if (button == 0) {
            popup.popupClicked(mouseX, mouseY);
        } else {
            popup.closePopup();
        }
        if (!popup.popupOpen()) screen.openDropdown(null);
        cir.setReturnValue(Boolean.TRUE);
    }
}
