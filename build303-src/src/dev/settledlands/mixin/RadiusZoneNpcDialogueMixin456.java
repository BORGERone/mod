package dev.settledlands.mixin;

import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusZone456;
import dev.settledlands.npc.RadiusZone456.AttachZoneState;
import dev.settledlands.npc.RadiusZone456.CheckZoneCtx;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 456: enforces Square zone checks inside {@link GuideDialogue} for NPC {@code CHECK}
 * (proximity condition &amp; autonomous tick scan) and {@code RADIUS} ("Цель" gate &amp; fan-out
 * attachments). Zero lambdas.
 */
@Mixin(value = GuideDialogue.class, remap = false)
public abstract class RadiusZoneNpcDialogueMixin456 {

    @Unique
    private static final ThreadLocal<Deque<CheckZoneCtx>> CHECK_STACK = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<NpcDialogueGraph.Node> TICK_NEXT_NODE = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Deque<AttachZoneState>> GATE_STACK = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Deque<AttachZoneState>> EXPAND_STACK = new ThreadLocal<>();

    @Shadow
    private static Entity findCheckTarget(GuideNpc npc, ServerLevel level, int targetKind,
            int radius, Map cooldowns, long now, long cdMs) {
        throw new AssertionError();
    }

    @Unique
    private static void settledlands$pushCheckCtx(NpcDialogueGraph.Node node) {
        double r = node == null ? 1.0D : (double) Math.max(1, Math.min(64, node.checkRadius));
        Deque<CheckZoneCtx> st = CHECK_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            CHECK_STACK.set(st);
        }
        st.push(new CheckZoneCtx(RadiusZone456.isSquare(node), r));
    }

    @Unique
    private static void settledlands$popCheckCtx() {
        Deque<CheckZoneCtx> st = CHECK_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) CHECK_STACK.remove();
    }

    @Unique
    private static double settledlands$evalNpcCheckDist456(GuideNpc origin, Entity target) {
        Deque<CheckZoneCtx> st = CHECK_STACK.get();
        CheckZoneCtx ctx = st == null ? null : st.peek();
        if (ctx != null && ctx.square) {
            return RadiusZone456.isWithinNpcZone(origin, target, ctx.radius, true)
                    ? 0.0D : Double.MAX_VALUE;
        }
        return origin.distanceToSqr(target);
    }

    // ---- 1. checkProximityCondition (incoming CHECK guard) ----

    @Inject(method = "checkProximityCondition(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushProx456(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Boolean> cir) {
        settledlands$pushCheckCtx(node);
    }

    @Inject(method = "checkProximityCondition(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popProx456(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Boolean> cir) {
        settledlands$popCheckCtx();
    }

    @Redirect(method = "checkProximityCondition(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$proxDist456(GuideNpc npc, Entity player) {
        return settledlands$evalNpcCheckDist456(npc, player);
    }

    // ---- 2. tickGraphCheckNodes + findCheckTarget + lambdas ----

    @Redirect(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"),
            remap = false)
    private static Object settledlands$trackTickCheckNode456(Iterator it) {
        Object next = it.next();
        if (next instanceof NpcDialogueGraph.Node n) {
            TICK_NEXT_NODE.set(n);
        }
        return next;
    }

    @Redirect(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;findCheckTarget(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerLevel;IILjava/util/Map;JJ)Lnet/minecraft/world/entity/Entity;"),
            remap = false)
    private static Entity settledlands$pushTickFindCheck456(GuideNpc npc, ServerLevel level,
            int targetKind, int radius, Map cooldowns, long now, long cdMs) {
        settledlands$pushCheckCtx(TICK_NEXT_NODE.get());
        try {
            return findCheckTarget(npc, level, targetKind, radius, cooldowns, now, cdMs);
        } finally {
            settledlands$popCheckCtx();
        }
    }

    @Inject(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$clearTickCheckNode456(GuideNpc npc, NpcDialogueGraph graph,
            Map cooldowns, CallbackInfo ci) {
        TICK_NEXT_NODE.remove();
    }

    @Redirect(method = "lambda$findCheckTarget$13(Ldev/settledlands/npc/GuideNpc;DLdev/settledlands/npc/GuideNpc;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$findCheckL13(GuideNpc origin, Entity target) {
        return settledlands$evalNpcCheckDist456(origin, target);
    }

    @Redirect(method = "lambda$findCheckTarget$14(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$findCheckL14(GuideNpc origin, Entity target) {
        return settledlands$evalNpcCheckDist456(origin, target);
    }

    @Redirect(method = "lambda$findCheckTarget$15(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$findCheckL15(GuideNpc origin, Entity target) {
        return settledlands$evalNpcCheckDist456(origin, target);
    }

    @Redirect(method = "lambda$findCheckTarget$16(Ldev/settledlands/npc/GuideNpc;DLnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false)
    private static double settledlands$findCheckL16(GuideNpc origin, Entity target) {
        return settledlands$evalNpcCheckDist456(origin, target);
    }

    // ---- 3. initiatorExecutesWithRadiusGate (RADIUS self-gate) ----

    @Inject(method = "initiatorExecutesWithRadiusGate(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/util/List;)Z",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushGate456(ServerPlayer player, GuideNpc npc,
            List attachments, CallbackInfoReturnable<Boolean> cir) {
        Deque<AttachZoneState> st = GATE_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            GATE_STACK.set(st);
        }
        st.push(new AttachZoneState());
    }

    @Inject(method = "initiatorExecutesWithRadiusGate(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/util/List;)Z",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popGate456(ServerPlayer player, GuideNpc npc,
            List attachments, CallbackInfoReturnable<Boolean> cir) {
        Deque<AttachZoneState> st = GATE_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) GATE_STACK.remove();
    }

    @Redirect(method = "initiatorExecutesWithRadiusGate(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/util/List;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"),
            remap = false)
    private static Object settledlands$trackGateNode456(Iterator it) {
        Object next = it.next();
        Deque<AttachZoneState> st = GATE_STACK.get();
        AttachZoneState cur = st == null ? null : st.peek();
        if (cur != null && next instanceof NpcDialogueGraph.Node n) {
            cur.currentNode = n;
            cur.radius = (double) Math.max(1, Math.min(96, n.radius));
        }
        return next;
    }

    @Redirect(method = "initiatorExecutesWithRadiusGate(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/util/List;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double settledlands$gateDist456(ServerPlayer player, Vec3 origin) {
        Deque<AttachZoneState> st = GATE_STACK.get();
        AttachZoneState cur = st == null ? null : st.peek();
        if (cur != null && RadiusZone456.isSquare(cur.currentNode)) {
            return RadiusZone456.isWithinVecZone(origin, player, cur.radius, true)
                    ? 0.0D : Double.MAX_VALUE;
        }
        return player.distanceToSqr(origin.x, origin.y, origin.z);
    }

    // ---- 4. expandRadiusAttachments (RADIUS fan-out for players, NPCs, enemies) ----

    @Inject(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushExpand456(ServerPlayer initiator, GuideNpc npc,
            NpcDialogueGraph graph, NpcDialogueGraph.Node sourceNode, List attachments, Set visited,
            CallbackInfo ci) {
        Deque<AttachZoneState> st = EXPAND_STACK.get();
        if (st == null) {
            st = new ArrayDeque<>();
            EXPAND_STACK.set(st);
        }
        st.push(new AttachZoneState());
    }

    @Inject(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popExpand456(ServerPlayer initiator, GuideNpc npc,
            NpcDialogueGraph graph, NpcDialogueGraph.Node sourceNode, List attachments, Set visited,
            CallbackInfo ci) {
        Deque<AttachZoneState> st = EXPAND_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) EXPAND_STACK.remove();
    }

    @Redirect(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"),
            remap = false)
    private static Object settledlands$trackExpandNode456(Iterator it) {
        Object next = it.next();
        Deque<AttachZoneState> st = EXPAND_STACK.get();
        AttachZoneState cur = st == null ? null : st.peek();
        if (cur != null && next instanceof NpcDialogueGraph.Node n) {
            cur.currentNode = n;
            cur.radius = (double) Math.max(1, Math.min(96, n.radius));
        }
        return next;
    }

    @Unique
    private static double settledlands$expandVecDistOrBox456(Entity entity, Vec3 origin) {
        Deque<AttachZoneState> st = EXPAND_STACK.get();
        AttachZoneState cur = st == null ? null : st.peek();
        if (cur != null && RadiusZone456.isSquare(cur.currentNode)) {
            return RadiusZone456.isWithinVecZone(origin, entity, cur.radius, true)
                    ? 0.0D : Double.MAX_VALUE;
        }
        return entity.distanceToSqr(origin.x, origin.y, origin.z);
    }

    @Redirect(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double settledlands$expandPlayerDist456(ServerPlayer player, Vec3 origin) {
        return settledlands$expandVecDistOrBox456(player, origin);
    }

    @Redirect(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double settledlands$expandNpcDist456(GuideNpc targetNpc, Vec3 origin) {
        return settledlands$expandVecDistOrBox456(targetNpc, origin);
    }

    @Redirect(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double settledlands$expandEntityDist456(Entity targetEntity, Vec3 origin) {
        return settledlands$expandVecDistOrBox456(targetEntity, origin);
    }
}
