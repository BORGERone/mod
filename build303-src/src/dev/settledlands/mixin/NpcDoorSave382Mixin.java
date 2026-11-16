package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 382: a config save that arrived for a door id belongs to the door, not to an NPC.
 *
 * <p>The editor sends one packet on save - {@code CfgSave(id, data)} - and the server hands it to
 * {@code GuideNpc#applyConfig}. A door id has no entity behind it, so the stock body would walk off
 * into a null profile; this hook takes the save first, writes the graph into world data (and the
 * «закрыта/открыта» flag into the lock store of build 368) and cancels the rest.</p>
 *
 * <p>Listed <b>first</b> in the common list, before the block hook of build 351 and before the shared
 * «Общее» graph hook of build 334: the three ranges do not overlap, but a save must reach the hook
 * that owns its id before any other hook looks at the same packet. This is the same seam, the same
 * order and the same shape as build 351 - one more owner of one more id range.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcDoorSave382Mixin {

    @Inject(method = "applyConfig", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorSave(ServerPlayer player, int entityId, CompoundTag data,
            CallbackInfo ci) {
        if (!DoorGraph382.isDoorEditorId(entityId)) return;
        ci.cancel();
        DoorGraph382.save(player, entityId, data);
    }
}
