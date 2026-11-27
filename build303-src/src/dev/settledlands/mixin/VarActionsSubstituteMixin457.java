package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcActions;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes {@code %var%}/{@code %value%}/{@code %<varName>%} placeholders inside
 * {@link NpcActions#fill}, {@link NpcActions#run}, {@link NpcActions#runSingle}, and
 * {@link NpcActions#runForEntity} before downstream action handlers (teleport, pursuit, commands)
 * parse their arguments. Zero lambdas.
 */
@Mixin(value = NpcActions.class, remap = false)
public abstract class VarActionsSubstituteMixin457 {

    @Unique
    private static final ThreadLocal<Boolean> SUBST_GUARD = new ThreadLocal<>();

    @Inject(method = "fill(Ljava/lang/String;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;I)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands457$fillVars(String raw, ServerPlayer player, GuideNpc npc,
            int relation, CallbackInfoReturnable<String> cir) {
        String cur = cir.getReturnValue();
        if (cur == null || cur.indexOf('%') < 0) return;
        String sub = VarScopeStore453.substituteActiveTextVars(cur, player, npc);
        if (!sub.equals(cur)) {
            cir.setReturnValue(sub);
        }
    }

    @Inject(method = "run(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands457$substRun(ServerPlayer player, GuideNpc npc, int relation,
            String action, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(SUBST_GUARD.get()) || action == null || action.indexOf('%') < 0) return;
        String sub = VarScopeStore453.substituteActiveTextVars(action, player, npc);
        if (sub.equals(action)) return;
        SUBST_GUARD.set(Boolean.TRUE);
        try {
            cir.setReturnValue(NpcActions.run(player, npc, relation, sub));
        } finally {
            SUBST_GUARD.remove();
        }
    }

    @Inject(method = "runSingle(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands457$substRunSingle(ServerPlayer player, GuideNpc npc, int relation,
            String action, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(SUBST_GUARD.get()) || action == null || action.indexOf('%') < 0) return;
        String sub = VarScopeStore453.substituteActiveTextVars(action, player, npc);
        if (sub.equals(action)) return;
        SUBST_GUARD.set(Boolean.TRUE);
        try {
            cir.setReturnValue(NpcActions.runSingle(player, npc, relation, sub));
        } finally {
            SUBST_GUARD.remove();
        }
    }

    @Inject(method = "runForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands457$substRunForEntity(Entity target, GuideNpc npc, int relation,
            String action, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(SUBST_GUARD.get()) || action == null || action.indexOf('%') < 0) return;
        ServerPlayer sp = target instanceof ServerPlayer p ? p : null;
        String sub = VarScopeStore453.substituteActiveTextVars(action, sp, npc);
        if (sub.equals(action)) return;
        SUBST_GUARD.set(Boolean.TRUE);
        try {
            cir.setReturnValue(NpcActions.runForEntity(target, npc, relation, sub));
        } finally {
            SUBST_GUARD.remove();
        }
    }
}
