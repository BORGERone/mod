package dev.settledlands.mixin;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.VarCatalogStore457;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches {@link VarCatalogStore457#NBT_TAG} to outgoing {@link NpcNet.CfgView} editor packets. */
@Mixin(NpcNet.class)
public abstract class VarCatalogViewMixin457 {

    @Inject(method = "sendRaw", at = @At("HEAD"), require = 0)
    private static void settledlands457$attachCatalog(
            ServerPlayer player, CustomPacketPayload payload, CallbackInfo ci) {
        if (player == null || !(payload instanceof NpcNet.CfgView view)) return;
        int eid = view.entityId();
        if (eid == VarCatalogStore457.SILENT_SYNC_ENTITY_ID || eid == -2000000000) return;
        CompoundTag data = view.data();
        if (data == null || data.contains(VarCatalogStore457.NBT_TAG) || data.contains("BlockTalkX")) {
            return;
        }
        data.put(VarCatalogStore457.NBT_TAG,
                VarCatalogStore457.buildClientTag(player.getServer(), player, eid));
    }
}
