package dev.settledlands.mixin;

import dev.settledlands.model.NpcMotionBridge256;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientLevel.class})
public abstract class ClientLevelProxyLookupMixin256 {
    @Inject(
        method = {"getEntity(I)Lnet/minecraft/world/entity/Entity;"},
        at = {@At("HEAD")},
        cancellable = true,
        require = 0
    )
    private void settledlands$lookupNpcMotionProxy(int id, CallbackInfoReturnable<Entity> cir) {
        if (id < NpcMotionBridge256.PROXY_ID_THRESHOLD) {
            Entity proxy = NpcMotionBridge256.lookupActiveProxy((ClientLevel) (Object) this, id);
            if (proxy != null) {
                cir.setReturnValue(proxy);
            }
        }
    }
}
