package dev.settledlands.mixin;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.VarCatalogStore457;
import dev.settledlands.npc.client.NpcConfigScreen;
import dev.settledlands.npc.client.VarCatalogClient457;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads {@link VarCatalogStore457#NBT_TAG} from incoming {@link NpcNet.CfgView} payloads on the client. */
@Mixin(NpcConfigScreen.class)
public abstract class VarCatalogConfigMixin457 {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, require = 0)
    private static void settledlands457$readCatalog(NpcNet.CfgView view, CallbackInfo ci) {
        if (view == null) return;
        CompoundTag data = view.data();
        if (data != null && data.contains(VarCatalogStore457.NBT_TAG)) {
            VarCatalogClient457.applyPacket(data.getCompound(VarCatalogStore457.NBT_TAG));
        }
        if (view.entityId() == VarCatalogStore457.SILENT_SYNC_ENTITY_ID) {
            ci.cancel();
        }
    }
}
