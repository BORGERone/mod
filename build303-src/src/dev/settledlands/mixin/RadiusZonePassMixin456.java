package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.RadiusZone456;
import dev.settledlands.npc.RadiusZone456.CheckZoneCtx;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 456: enforces square Chebyshev zone checks and square particle outlines for
 * {@link RadiusPass354} across blocks, doors, chests, trials, and NPC radius rings. Zero lambdas.
 */
@Mixin(value = RadiusPass354.class, remap = false)
public abstract class RadiusZonePassMixin456 {

    @Unique
    private static final ThreadLocal<NpcDialogueGraph.Node> RING_NODE = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<CheckZoneCtx> PENDING_CTX = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Deque<CheckZoneCtx>> CHECK_STACK = new ThreadLocal<>();

    @Shadow
    public static boolean visible(NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static void ring(ServerLevel level, double cx, double y, double cz, int r, long time) {
        throw new AssertionError();
    }

    @Shadow
    private static int kind(NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static Entity findTarget(ServerLevel level, RadiusPass354.Spot spot, int kind,
            int r, Map cd, long now, long cooldownMs) {
        throw new AssertionError();
    }

    // ---- Visual particle outline: block/door/chest/trial rings ----

    @Redirect(method = "rings(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;visible(Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$trackRingNode456(NpcDialogueGraph.Node node) {
        RING_NODE.set(node);
        return visible(node);
    }

    @Redirect(method = "rings(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;ring(Lnet/minecraft/server/level/ServerLevel;DDDIJ)V"),
            remap = false)
    private static void settledlands$drawSquareOrCircleSpot456(ServerLevel level,
            double cx, double y, double cz, int r, long time) {
        NpcDialogueGraph.Node cur = RING_NODE.get();
        RING_NODE.remove();
        if (RadiusZone456.isSquare(cur)) {
            RadiusZone456.drawSquareOutline(level, cx, y, cz, r, time);
        } else {
            ring(level, cx, y, cz, r, time);
        }
    }

    @Inject(method = "rings(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;J)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$clearRingNode456(ServerLevel level, RadiusPass354.Spot spot,
            NpcDialogueGraph graph, long now, CallbackInfo ci) {
        RING_NODE.remove();
    }

    // ---- Visual particle outline: NPC rings (RadiusPass354.npcTick) ----

    @Redirect(method = "npcTick(Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;visible(Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$trackNpcRingNode456(NpcDialogueGraph.Node node) {
        RING_NODE.set(node);
        return visible(node);
    }

    @Redirect(method = "npcTick(Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;ring(Lnet/minecraft/server/level/ServerLevel;DDDIJ)V"),
            remap = false)
    private static void settledlands$drawSquareOrCircleNpc456(ServerLevel level,
            double cx, double y, double cz, int r, long time) {
        NpcDialogueGraph.Node cur = RING_NODE.get();
        RING_NODE.remove();
        if (RadiusZone456.isSquare(cur)) {
            RadiusZone456.drawSquareOutline(level, cx, y, cz, r, time);
        } else {
            ring(level, cx, y, cz, r, time);
        }
    }

    @Inject(method = "npcTick(Ldev/settledlands/npc/GuideNpc;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$clearNpcRingNode456(dev.settledlands.npc.GuideNpc npc,
            CallbackInfo ci) {
        RING_NODE.remove();
    }

    // ---- Radius detection check() + lambda$10 ----

    @Redirect(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;kind(Ldev/settledlands/npc/NpcDialogueGraph$Node;)I"),
            remap = false)
    private static int settledlands$captureCheckCtx456(NpcDialogueGraph.Node node) {
        PENDING_CTX.set(new CheckZoneCtx(
                RadiusZone456.isSquare(node),
                (double) RadiusPass354.radius(node)));
        return kind(node);
    }

    @Redirect(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;findTarget(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;IILjava/util/Map;JJ)Lnet/minecraft/world/entity/Entity;"),
            remap = false)
    private static Entity settledlands$pushCtxAroundFindTarget456(ServerLevel level,
            RadiusPass354.Spot spot, int k, int r, Map cd, long now, long cooldownMs) {
        CheckZoneCtx ctx = PENDING_CTX.get();
        PENDING_CTX.remove();
        if (ctx == null) {
            return findTarget(level, spot, k, r, cd, now, cooldownMs);
        }
        Deque<CheckZoneCtx> st = CHECK_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            CHECK_STACK.set(st);
        }
        st.push(ctx);
        try {
            return findTarget(level, spot, k, r, cd, now, cooldownMs);
        } finally {
            if (!st.isEmpty()) st.pop();
            if (st.isEmpty()) CHECK_STACK.remove();
        }
    }

    @Inject(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$cleanupCheckCtx456(ServerLevel level, RadiusPass354.Spot spot,
            NpcDialogueGraph graph, List players, long now, CallbackInfo ci) {
        PENDING_CTX.remove();
    }

    @Redirect(method = "lambda$10(Ljava/util/function/Predicate;Ldev/settledlands/npc/RadiusPass354$Spot;DLnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354$Spot;distanceSqr(DDD)D"),
            remap = false)
    private static double settledlands$spotDistanceOrBox456(RadiusPass354.Spot spot,
            double ex, double ey, double ez) {
        Deque<CheckZoneCtx> st = CHECK_STACK.get();
        CheckZoneCtx ctx = st == null ? null : st.peek();
        if (ctx != null && ctx.square) {
            boolean inside = RadiusZone456.isInsideBox(
                    spot.centerX(), (double) spot.y() + 0.5D, spot.centerZ(),
                    ex, ey, ez, ctx.radius);
            return inside ? 0.0D : Double.MAX_VALUE;
        }
        return spot.distanceSqr(ex, ey, ez);
    }
}
