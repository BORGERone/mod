package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 396: a config save that arrived for a chest id belongs to the chest, not to an NPC.
 *
 * <p>The same seam the blocks (351) and the doors (382) take. The editor sends one packet on save -
 * {@code CfgSave(id, data)} - and the server hands it to {@code GuideNpc#applyConfig}. A chest id has no
 * entity behind it, so the stock body would walk off into a null profile; this hook takes the save first,
 * writes the graph into world data and cancels the rest.</p>
 *
 * <p>Listed <b>first</b> in the common list, next to the door's hook and for the same reason: a save must
 * reach the hook that owns its id before any other hook looks at the same packet. The three ranges do not
 * overlap - an NPC id, a block id, a door id and a chest id are four different numbers.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcChestSave396Mixin {

    @Inject(method = "applyConfig", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestSave(ServerPlayer player, int entityId, CompoundTag data,
            CallbackInfo ci) {
        if (!ChestGraph396.isChestEditorId(entityId)) return;
        ci.cancel();
        ChestGraph396.save(player, entityId, data);
    }
}
