package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.RadiusZone456;
import dev.settledlands.npc.VarDataNode453;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453/456: serializes {@code ValuePinInput} alongside {@code VarInputNode} and
 * {@code SquareZone456} on graph save/load, migrates legacy blue inputs, cleans up references on
 * {@code remove}/{@code disconnect}, and routes execution + orange connections cleanly into/out of
 * {@link VarDataNode453}. Zero synthetic lambdas.
 */
@Mixin(value = NpcDialogueGraph.class, remap = false)
public abstract class VarGraphIoMixin453 {

    private static final String TAG_ENTITY_INPUT = "ValuePinInput";

    @Shadow
    public abstract NpcDialogueGraph.Node get(int id);

    @Inject(method = "save()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void settledlands$saveEntityInputs453(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag root = cir.getReturnValue();
        if (root == null || !root.contains("Nodes", 9)) return;
        ListTag list = root.getList("Nodes", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag nt = list.getCompound(i);
            NpcDialogueGraph.Node n = get(nt.getInt("Id"));
            if (n == null) continue;
            int blue = VarDataNode453.entityInput(n);
            if (blue >= 0 && (n.type == NpcDialogueGraph.IF || n.type == NpcDialogueGraph.VARIABLE
                    || VarDataNode453.isVarDataNode(n))) {
                nt.putInt(TAG_ENTITY_INPUT, blue);
            }
            if (RadiusPass354.isRadiusNode(n) && RadiusZone456.isSquare(n)) {
                nt.putBoolean(RadiusZone456.NBT_SQUARE_ZONE, true);
            }
        }
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)Ldev/settledlands/npc/NpcDialogueGraph;",
            at = @At("RETURN"), remap = false)
    private static void settledlands$loadEntityInputs453(CompoundTag tag,
            CallbackInfoReturnable<NpcDialogueGraph> cir) {
        NpcDialogueGraph graph = cir.getReturnValue();
        if (graph == null) return;
        if (tag != null && tag.contains("Nodes", 9)) {
            ListTag list = tag.getList("Nodes", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag nt = list.getCompound(i);
                NpcDialogueGraph.Node n = graph.get(nt.getInt("Id"));
                if (n == null) continue;
                if (nt.contains(TAG_ENTITY_INPUT)
                        && (n.type == NpcDialogueGraph.IF || n.type == NpcDialogueGraph.VARIABLE
                                || VarDataNode453.isVarDataNode(n))) {
                    VarDataNode453.setEntityInput(n, nt.getInt(TAG_ENTITY_INPUT));
                }
                if (nt.contains(RadiusZone456.NBT_SQUARE_ZONE) && RadiusPass354.isRadiusNode(n)) {
                    RadiusZone456.setSquare(n, nt.getBoolean(RadiusZone456.NBT_SQUARE_ZONE));
                }
            }
        }
        VarDataNode453.migrateLegacyInputs(graph);
    }

    @Inject(method = "connect(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$routeVarDataConnect453(int fromId, int toId, int fromPort,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraph.Node from = get(fromId);
        NpcDialogueGraph.Node to = get(toId);
        if (from == null || to == null) return;
        if (from.special != 0 || to.special != 0 || fromId == toId) return;

        // Explicit orange data connection
        if (fromPort == -100 && VarDataNode453.isOrangeSourceNode(from)
                && VarDataNode453.isOrangeInputNode(to)) {
            to.varInputNode = fromId;
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        // Variable nodes (VarDataNode453) have NO white flow ports: block all white execution connections
        // into or out of them; allow only explicit orange (-100) data wires.
        if (VarDataNode453.isVarDataNode(to)) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        if (VarDataNode453.isVarDataNode(from)) {
            if (fromPort < 0 && VarDataNode453.isOrangeInputNode(to)) {
                to.varInputNode = fromId;
                cir.setReturnValue(Boolean.TRUE);
            } else {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
    }

    @Inject(method = "disconnect(II)V", at = @At("RETURN"), remap = false)
    private void settledlands$cleanupDisconnect453(int fromId, int toId, CallbackInfo ci) {
        NpcDialogueGraph.Node to = get(toId);
        if (to != null && VarDataNode453.entityInput(to) == fromId) {
            VarDataNode453.setEntityInput(to, -1);
        }
        if (to != null && to.varInputNode == fromId) {
            to.varInputNode = -1;
        }
    }

    @Inject(method = "remove(I)Z", at = @At("RETURN"), remap = false)
    private void settledlands$cleanupRemove453(int id, CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraph self = (NpcDialogueGraph) (Object) this;
        if (self.nodes == null) return;
        for (NpcDialogueGraph.Node n : self.nodes.values()) {
            if (n == null) continue;
            if (VarDataNode453.entityInput(n) == id) {
                VarDataNode453.setEntityInput(n, -1);
            }
            if (n.varInputNode == id) {
                n.varInputNode = -1;
            }
        }
    }
}
