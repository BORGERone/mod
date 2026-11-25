package dev.settledlands.mixin.compat;

import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.impl.client.scene.PlacedItem;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
import dev.settledlands.chest.DiegesisScatterClient439;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 439: optional Diegesis compatibility mixin that replaces deterministic back-to-front row
 * placement in {@code SceneInstance#freeSpot} with organic 3D scatter placement whenever the
 * server stamped a container with {@code SettledLandsScatterSeed}.
 */
@Pseudo
@Mixin(targets = "com.pockyl.diegesis.impl.client.scene.SceneInstance", remap = false)
public abstract class DiegesisScatterMixin439 {

    @Inject(
        method = "freeSpot(Lcom/pockyl/diegesis/impl/client/scene/PlacedItem;Lcom/pockyl/diegesis/impl/client/scene/PlacedShape;)Lcom/pockyl/diegesis/api/state/ItemPose;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void settledlands$scatterFreeSpot439(PlacedItem item, PlacedShape shape,
            CallbackInfoReturnable<ItemPose> cir) {
        Object pose = DiegesisScatterClient439.scatterFreeSpot(this, item, shape);
        if (pose instanceof ItemPose ip) {
            cir.setReturnValue(ip);
        }
    }
}
