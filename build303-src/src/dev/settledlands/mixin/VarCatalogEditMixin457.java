package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.VarCatalogStore457;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles {@code var457.*} catalog edits across NPC and block/door/chest/trial editors. */
@Mixin(GuideNpc.class)
public abstract class VarCatalogEditMixin457 {

    @Inject(method = "applyEdit", at = @At("HEAD"), cancellable = true, require = 0)
    private static void settledlands457$handleCatalogEdit(
            ServerPlayer player,
            int entityId,
            String op,
            int a,
            int b,
            int c,
            String key,
            String value,
            CallbackInfo ci) {
        if (VarCatalogStore457.isCatalogOp(op)) {
            VarCatalogStore457.handleEdit(player, entityId, op, a, b, c, key, value);
            ci.cancel();
        }
    }
}
