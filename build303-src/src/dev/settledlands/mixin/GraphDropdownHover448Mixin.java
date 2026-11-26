package dev.settledlands.mixin;

import dev.settledlands.npc.client.GraphTooltips448;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 448: tracks open dropdown popup coordinates, scroll offset, and hovered row so
 * {@link GraphTooltips448} can show tooltips for individual dropdown options.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcUi$NpcDropdown", remap = false)
public abstract class GraphDropdownHover448Mixin {

    @Shadow private List<Component> entries;
    @Shadow private int scroll;
    @Shadow private boolean open;

    @Inject(method = "renderPopup(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"), remap = false)
    private void settledlands$trackOpenPopup448(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (open) {
            GraphTooltips448.recordOpenPopup(this, scroll, entries, mouseX, mouseY);
        }
    }
}
