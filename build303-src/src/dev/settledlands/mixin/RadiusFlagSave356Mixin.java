package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import java.util.LinkedHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 356: the "Видимость радиуса" flag of a radius node now survives a save.
 *
 * <p>Build 355 made the row save on the click, and the ring still came back: the flag was written
 * into the node as {@code timerRepeat}, but {@code NpcDialogueGraph#save} writes
 * {@code TimerRepeat} only for a timer node ({@code type == 8}) - for a "В радиусе" or "Цель" node
 * the key was simply missing from the tag. What made that invisible is the read side:
 * {@code load} reads {@code TimerRepeat} for <b>every</b> node and, when the key is absent, fills in
 * {@code true}. So every save quietly turned the flag back to "да": the server drew the ring again
 * and the next snapshot put "включено" back into the row.</p>
 *
 * <p>The fix is on the writing side, where the hole is: at the end of {@code save} every radius
 * node's own tag gets the flag. Nothing else changes - the timer node's branch is untouched, and
 * {@code load} already knows how to read the key for any node, which is why no reader is patched
 * here.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class RadiusFlagSave356Mixin {

    /** The tag that holds the nodes of a graph, and the element type of that list. */
    private static final String NODES = "Nodes";
    private static final int COMPOUND = 10;

    /** The key the whole class already uses for this flag, read by {@code load} for every node. */
    private static final String FLAG = "TimerRepeat";

    @Inject(method = "save()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void settledlands$keepRadiusFlag(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag root = cir.getReturnValue();
        if (root == null || !root.contains(NODES)) return;
        NpcDialogueGraph self = (NpcDialogueGraph) (Object) this;
        LinkedHashMap<Integer, NpcDialogueGraph.Node> nodes = self.nodes;
        if (nodes == null || nodes.isEmpty()) return;
        ListTag list = root.getList(NODES, COMPOUND);
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag == null) continue;
            NpcDialogueGraph.Node node = nodes.get(tag.getInt("Id"));
            if (node == null) continue;
            if (node.type != NpcDialogueGraph.CHECK && node.type != NpcDialogueGraph.RADIUS) continue;
            tag.putBoolean(FLAG, node.timerRepeat);
        }
    }
}
