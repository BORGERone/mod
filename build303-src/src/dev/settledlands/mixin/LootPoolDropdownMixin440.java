package dev.settledlands.mixin;

import dev.settledlands.npc.client.LootPoolCard436;
import dev.settledlands.npc.client.NpcUi;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 440: renders inline 12x12 gear edit buttons on each row of an expanded Loot Pool
 * selector dropdown and opens the in-game pool editor when clicked.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcUi$NpcDropdown", remap = false)
public abstract class LootPoolDropdownMixin440 {

    @Shadow private boolean open;
    @Shadow private List<Component> entries;
    @Shadow private int scroll;
    @Shadow private int index;

    @Inject(method = "renderPopup(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawDropdownGear440(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        LootPoolCard436.renderDropdownRowGears(
                (NpcUi.NpcDropdown) (Object) this,
                graphics, mouseX, mouseY, entries, scroll, index);
    }

    @Inject(method = "popupClicked(DD)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$clickDropdownGear440(double mouseX, double mouseY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!open) return;
        int count = entries == null ? 0 : entries.size();
        if (LootPoolCard436.handleDropdownRowGearClick(
                (NpcUi.NpcDropdown) (Object) this, mouseX, mouseY, scroll, count)) {
            open = false;
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
