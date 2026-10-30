package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcProfessionSkin264;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuideNpc.class, remap = false)
public abstract class GuideNpcProfessionSkinMixin264 {
    @Shadow @Final private static EntityDataAccessor DATA_TEXTURE;

    @Inject(method = "applyProfile()V", at = @At("TAIL"), remap = false)
    private void settledlands$applyProfessionSkin(CallbackInfo ci) {
        GuideNpc npc = (GuideNpc) (Object) this;
        if (npc instanceof dev.settledlands.npc.GuideCorpseNpc || npc instanceof dev.settledlands.npc.GuideTriggerNpc) return;
        String path = NpcProfessionSkin264.modelName(npc.profile());
        if (path != null) npc.getEntityData().set(DATA_TEXTURE, path);
    }
}
