package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCheckValue289;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusZone456;
import dev.settledlands.npc.RadiusZone456.CheckZoneCtx;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 456: applies Square zone containment to {@link NpcCheckValue289#scan} (blue output pin of
 * NPC {@code CHECK} nodes). Zero lambdas.
 */
@Mixin(value = NpcCheckValue289.class, remap = false)
public abstract class RadiusZoneNpcCheckMixin456 {

    @Unique
    private static final ThreadLocal<Deque<CheckZoneCtx>> SCAN_STACK = new ThreadLocal<>();

    @Inject(method = "scan(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushScanCtx456(GuideNpc npc, ServerLevel level,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Entity> cir) {
        double r = node == null ? 1.0D : (double) Math.max(1, Math.min(64, node.checkRadius));
        Deque<CheckZoneCtx> st = SCAN_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            SCAN_STACK.set(st);
        }
        st.push(new CheckZoneCtx(RadiusZone456.isSquare(node), r));
    }

    @Inject(method = "scan(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popScanCtx456(GuideNpc npc, ServerLevel level,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Entity> cir) {
        Deque<CheckZoneCtx> st = SCAN_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) SCAN_STACK.remove();
    }

    @Unique
    private static double settledlands$evalNpcDistOrBox456(GuideNpc origin, Entity target) {
        Deque<CheckZoneCtx> st = SCAN_STACK.get();
        CheckZoneCtx ctx = st == null ? null : st.peek();
        if (ctx != null && ctx.square) {
            return RadiusZone456.isWithinNpcZone(origin, target, ctx.radius, true)
                    ? 0.0D : Double.MAX_VALUE;
        }
        return origin.distanceToSqr(target);
    }

    @Redirect(method = "lambda$0(Ldev/settledlands/npc/GuideNpc;DLdev/settledlands/npc/GuideNpc;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$distLambda0(GuideNpc origin, Entity target) {
        return settledlands$evalNpcDistOrBox456(origin, target);
    }

    @Redirect(method = "lambda$1(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$distLambda1(GuideNpc origin, Entity target) {
        return settledlands$evalNpcDistOrBox456(origin, target);
    }

    @Redirect(method = "lambda$2(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$distLambda2(GuideNpc origin, Entity target) {
        return settledlands$evalNpcDistOrBox456(origin, target);
    }

    @Redirect(method = "lambda$3(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$distLambda3(GuideNpc origin, Entity target) {
        return settledlands$evalNpcDistOrBox456(origin, target);
    }
}
