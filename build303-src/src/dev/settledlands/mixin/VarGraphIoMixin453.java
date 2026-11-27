package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
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
 * Build 453: serializes {@code ValuePinInput} alongside {@code VarInputNode} on graph save/load,
 * migrates legacy blue inputs out of {@code varInputNode} on {@code IF}/{@code VARIABLE}, cleans up
 * blue input references on {@code remove}/{@code disconnect}, and routes orange connections from
 * {@link VarDataNode453} cleanly without polluting {@code next}/{@code triggers}. Zero synthetic lambdas.
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
            if (blue >= 0 && (n.type == NpcDialogueGraph.IF || n.type == NpcDialogueGraph.VARIABLE)) {
                nt.putInt(TAG_ENTITY_INPUT, blue);
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
                if (!nt.contains(TAG_ENTITY_INPUT)) continue;
                NpcDialogueGraph.Node n = graph.get(nt.getInt("Id"));
                if (n != null && (n.type == NpcDialogueGraph.IF || n.type == NpcDialogueGraph.VARIABLE)) {
                    VarDataNode453.setEntityInput(n, nt.getInt(TAG_ENTITY_INPUT));
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

        if (VarDataNode453.isVarDataNode(to)) {
            // A pure UE5 data node never takes execution/incoming wires.
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        if (VarDataNode453.isVarDataNode(from)) {
            if (VarDataNode453.isOrangeInputNode(to)) {
                to.varInputNode = fromId;
                cir.setReturnValue(Boolean.TRUE);
            } else {
                cir.setReturnValue(Boolean.FALSE);
            }
            return;
        }
        // When connecting VARIABLE -> IF via normal execution port, keep execution flow separate
        // unless explicit orange port (-100) was requested, or preserve standard flow:
        if (fromPort == -100 && VarDataNode453.isOrangeSourceNode(from)
                && VarDataNode453.isOrangeInputNode(to)) {
            to.varInputNode = fromId;
            cir.setReturnValue(Boolean.TRUE);
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
