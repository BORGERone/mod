package dev.settledlands.mixin;

import dev.settledlands.npc.client.NpcUi;
import dev.settledlands.npc.client.VarCatalogClient457;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Renders scope/availability badges and inline catalog gear buttons inside variable dropdown popups. */
@Mixin(NpcUi.NpcDropdown.class)
public abstract class VarDropdownOverlayMixin457 {

    @Shadow(remap = false)
    private boolean open;

    @Shadow(remap = false)
    private List<Component> entries;

    @Shadow(remap = false)
    private int scroll;

    @Shadow(remap = false)
    private int index;

    @Inject(method = "renderPopup", at = @At("TAIL"), require = 0)
    private void settledlands457$drawVarDropdownOverlay(GuiGraphics g, int mx, int my, CallbackInfo ci) {
        VarCatalogClient457.renderDropdownOverlay(
                (NpcUi.NpcDropdown) (Object) this, g, mx, my, this.entries, this.scroll, this.index);
    }

    @Inject(method = "popupClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void settledlands457$clickVarDropdownOverlay(
            double mx, double my, CallbackInfoReturnable<Boolean> cir) {
        if (!this.open) return;
        int total = this.entries == null ? 0 : this.entries.size();
        NpcUi.NpcDropdown dd = (NpcUi.NpcDropdown) (Object) this;
        if (VarCatalogClient457.handleDropdownPopupClick(dd, mx, my, this.scroll, total)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
