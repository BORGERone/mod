package dev.settledlands.mixin;

import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.trial.TrialControlNode446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 424: extends the NPC/common graph context menu from 17 items (stock 0..15 + Pursuit 16)
 * to 18 items (+ Teleport 17) without conflicting with {@code NpcPursuitContextMenuMixin272}'s
 * {@code @ModifyConstant} hooks.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcGraphContextMenu", remap = false)
public abstract class TeleportContextMenuMixin424 {

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
    private void settledlands$teleportRowAt424(double mouseX, double mouseY,
            CallbackInfoReturnable<Integer> cir) {
        Integer ret = cir.getReturnValue();
        if (ret != null && ret.intValue() >= 0) return;
        if (!open || targetNodeId >= 0) return;
        if (!contains(mouseX, mouseY) || isScrollbar(mouseX, mouseY)) return;
        int local = (int) ((mouseY - top - 20.0D) / 22.0D);
        if (local < 0 || local >= TeleportNode424.NPC_VISIBLE_ROWS) return;
        int row = scrollRow + local;
        if (row == TeleportNode424.TELEPORT_ROW) {
            cir.setReturnValue(TeleportNode424.TELEPORT_ROW);
        } else if (row == TrialControlNode446.SPAWNER_ROW) {
            cir.setReturnValue(TrialControlNode446.SPAWNER_ROW);
        }
    }

    @Inject(method = "scroll(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$teleportScroll424(int delta, CallbackInfo ci) {
        if (targetNodeId >= 0) return;
        scrollRow = Math.max(0, Math.min(TeleportNode424.NPC_MAX_SCROLL, scrollRow + delta));
        ci.cancel();
    }

    @Inject(method = "scrollbarDrag(D)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$teleportScrollbarDrag424(double mouseY, CallbackInfo ci) {
        if (targetNodeId >= 0) return;
        int trackHeight = height - 20 - 4;
        if (trackHeight <= 0) {
            ci.cancel();
            return;
        }
        double rel = (mouseY - (top + 20 + 2)) / (double) trackHeight;
        int clamped = (int) Math.max(0L, Math.min((long) TeleportNode424.NPC_MAX_SCROLL,
                Math.round(rel * (double) TeleportNode424.NPC_MAX_SCROLL)));
        scrollRow = clamped;
        ci.cancel();
    }
}
