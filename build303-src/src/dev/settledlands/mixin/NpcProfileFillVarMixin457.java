package dev.settledlands.mixin;

import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Expands active variable tokens ({@code %<varName>%}, {@code %var%}, {@code %value%}) in
 * {@link NpcProfile#fill} whenever an NPC or block dialogue {@link VarScopeStore453.TextVarContext}
 * is active (such as choice button labels). Zero lambdas.
 */
@Mixin(value = NpcProfile.class, remap = false)
public abstract class NpcProfileFillVarMixin457 {

    @Inject(method = "fill(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;I)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void settledlands457$fillProfileVars(String raw, Player player, int relation,
            CallbackInfoReturnable<String> cir) {
        String cur = cir.getReturnValue();
        if (cur == null || cur.indexOf('%') < 0) return;
        VarScopeStore453.TextVarContext ctx = VarScopeStore453.peekTextContext();
        if (ctx == null) return;
        ServerPlayer sp = player instanceof ServerPlayer s ? s : ctx.player;
        String sub = VarScopeStore453.substituteActiveTextVars(cur, sp, ctx.npc);
        if (!sub.equals(cur)) {
            cir.setReturnValue(sub);
        }
    }
}
