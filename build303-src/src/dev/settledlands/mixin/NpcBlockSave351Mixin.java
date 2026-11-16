package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTrigger351;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 351: a config save that arrived for a block id belongs to the block, not to an NPC.
 *
 * <p>The editor sends one packet on save - {@code CfgSave(id, data)} - and the server hands it to
 * {@code GuideNpc#applyConfig}. For a block there is no entity with that id, so the stock body would
 * walk off into a null profile; the hook takes the save first, writes the graph into world data and
 * cancels the rest. Every other id is untouched and takes the stock path exactly as before.</p>
 *
 * <p>This is the same seam build 334 used for the shared "Общее" graph - it intercepts the head of that
 * method too, and it asks for {@code AnimGraph} which a block save never carries, so the two cannot
 * fight over one packet. Ours is listed before it in the mixin config, so a block save is cancelled
 * before the shared graph ever looks at it.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcBlockSave351Mixin {

    @Inject(method = "applyConfig", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockSave(ServerPlayer player, int entityId, CompoundTag data,
            CallbackInfo ci) {
        if (!BlockTrigger351.isBlockEditorId(entityId)) return;
        ci.cancel();
        BlockTrigger351.save(player, entityId, data);
    }
}
