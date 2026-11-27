package dev.settledlands.mixin;

import dev.settledlands.npc.VarDataNode453;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: appends row 19 ({@link VarDataNode453#MENU_ROW}) to the NPC/common graph context
 * menu on top of {@link TeleportContextMenuMixin424}. Zero synthetic lambdas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcGraphContextMenu", remap = false)
public abstract class VarDataMenuMixin453 {

    @Shadow
    public boolean open;

    @Shadow
    public int top;

    @Shadow
    public int height;

    @Shadow
    public int targetNodeId;

    @Shadow
    public int scrollRow;

    @Shadow
    public boolean contains(double x, double y) {
        throw new AssertionError();
    }

    @Shadow
    public boolean isScrollbar(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "rowAt(DD)I", at = @At("RETURN"), cancellable = true, remap = false)
    private void settledlands$varDataRowAt453(double mouseX, double mouseY,
            CallbackInfoReturnable<Integer> cir) {
        Integer ret = cir.getReturnValue();
        if (ret != null && ret.intValue() >= 0) return;
        if (!open || targetNodeId >= 0) return;
        if (!contains(mouseX, mouseY) || isScrollbar(mouseX, mouseY)) return;
        int local = (int) ((mouseY - top - 20.0D) / 22.0D);
        if (local < 0 || local >= VarDataNode453.NPC_VISIBLE_ROWS) return;
        int row = scrollRow + local;
        if (row == VarDataNode453.MENU_ROW) {
            cir.setReturnValue(VarDataNode453.MENU_ROW);
        }
    }

    @Inject(method = "scroll(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$varDataScroll453(int delta, CallbackInfo ci) {
        if (targetNodeId >= 0) return;
        scrollRow = Math.max(0, Math.min(VarDataNode453.NPC_MAX_SCROLL, scrollRow + delta));
        ci.cancel();
    }

    @Inject(method = "scrollbarDrag(D)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$varDataScrollbarDrag453(double mouseY, CallbackInfo ci) {
        if (targetNodeId >= 0) return;
        int trackHeight = height - 20 - 4;
        if (trackHeight <= 0) {
            ci.cancel();
            return;
        }
        double rel = (mouseY - (top + 20 + 2)) / (double) trackHeight;
        int clamped = (int) Math.max(0L, Math.min((long) VarDataNode453.NPC_MAX_SCROLL,
                Math.round(rel * (double) VarDataNode453.NPC_MAX_SCROLL)));
        scrollRow = clamped;
        ci.cancel();
    }
}
