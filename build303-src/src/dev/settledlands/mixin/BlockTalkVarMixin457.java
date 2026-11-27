package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes variable placeholders ({@code %var%}, {@code %value%}, {@code %var_name%},
 * {@code %target%}, and {@code %<varName>%}) inside block/door/chest/trial dialogue lines
 * and choice captions. Zero lambdas.
 */
@Mixin(value = BlockTalk358.class, remap = false)
public abstract class BlockTalkVarMixin457 {

    @Unique
    private static final ThreadLocal<NpcDialogueGraph> ACTIVE_CHOICES_GRAPH = new ThreadLocal<>();

    @Inject(method = "withBody(Ljava/lang/String;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands457$blockWithBodyVars(String text, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<String> cir) {
        String cur = cir.getReturnValue();
        if (cur == null || cur.indexOf('%') < 0) return;
        String sub = VarScopeStore453.substituteBlockText(cur, player, graph, node);
        if (!sub.equals(cur)) {
            cir.setReturnValue(sub);
        }
    }

    @Inject(method = "choices(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z",
            at = @At("HEAD"), remap = false)
    private static void settledlands457$enterBlockChoices(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, BlockTalk358.Where where, CallbackInfoReturnable<Boolean> cir) {
        ACTIVE_CHOICES_GRAPH.set(graph);
        VarScopeStore453.pushTextContext(
                player != null ? player.getServer() : null, player, null, where, graph, node);
    }

    @Inject(method = "choices(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z",
            at = @At("RETURN"), remap = false)
    private static void settledlands457$exitBlockChoices(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, BlockTalk358.Where where, CallbackInfoReturnable<Boolean> cir) {
        ACTIVE_CHOICES_GRAPH.remove();
        VarScopeStore453.popTextContext();
    }

    @Redirect(method = "choices(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcProfile;fill(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;I)Ljava/lang/String;"),
            remap = false)
    private static String settledlands457$fillChoiceCaptionVars(String s, Player p, int relation) {
        String base = NpcProfile.fill(s, p, relation);
        if (base == null || base.indexOf('%') < 0) return base;
        ServerPlayer sp = p instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        return VarScopeStore453.substituteActiveTextVars(base, sp, null);
    }
}
