package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcProfessionSkin264;
import dev.settledlands.npc.NpcStatsGraph;
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
        dev.settledlands.npc.NpcProfile profile = npc.profile();
        dev.settledlands.npc.NpcDialogueGraph.Node stats = profile == null || profile.dialogueGraph == null
                ? null : NpcStatsGraph.find(profile.dialogueGraph);
        String path = NpcProfessionSkin264.modelName(profile);
        boolean excluded = npc instanceof dev.settledlands.npc.GuideCorpseNpc
                || npc instanceof dev.settledlands.npc.GuideTriggerNpc;
        if (!excluded && path != null) npc.getEntityData().set(DATA_TEXTURE, path);
        System.out.println("[SL-PROFESSION-266] apply id=" + npc.getId()
                + " uuid=" + npc.getUUID()
                + " class=" + npc.getClass().getName()
                + " side=" + (npc.level().isClientSide ? "client" : "server")
                + " excluded=" + excluded
                + " node=" + (stats == null ? "none" : stats.id)
                + " profession=" + (stats == null ? "none" : NpcStatsGraph.profession(stats))
                + " male=" + (stats != null && NpcStatsGraph.male(stats))
                + " japanese=" + (stats != null && NpcStatsGraph.japanese(stats))
                + " profileTexture=" + (profile == null ? "null" : profile.texture)
                + " selected=" + path
                + " actual=" + npc.texturePath());
    }
}
