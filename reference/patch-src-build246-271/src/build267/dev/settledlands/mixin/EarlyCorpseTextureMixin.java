package dev.settledlands.mixin;

import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfessionSkin264;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcStatsGraph;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Set the profession texture before applyProfile can exit exceptionally. */
@Mixin(value = GuideNpc.class, remap = false)
public abstract class EarlyCorpseTextureMixin {
    @Shadow @Final private static EntityDataAccessor DATA_TEXTURE;

    @Inject(method = "applyProfile()V", at = @At("HEAD"), remap = false)
    private void settledlands$earlyTexture(CallbackInfo ci) {
        GuideNpc npc = (GuideNpc) (Object) this;
        NpcProfile profile = npc.profile();
        String base = profile == null || profile.texture == null ? "" : profile.texture;
        String selected = null;
        String details = "node=none profession=none male=false japanese=false";
        String failure = "none";
        boolean excluded = npc instanceof GuideCorpseNpc || npc instanceof GuideTriggerNpc;
        if (!excluded && profile != null) {
            try {
                NpcDialogueGraph.Node stats = profile.dialogueGraph == null
                        ? null : NpcStatsGraph.find(profile.dialogueGraph);
                if (stats != null) {
                    details = "node=" + stats.id
                            + " profession=" + NpcStatsGraph.profession(stats)
                            + " male=" + NpcStatsGraph.male(stats)
                            + " japanese=" + NpcStatsGraph.japanese(stats);
                }
                selected = NpcProfessionSkin264.modelName(profile);
            } catch (Throwable error) {
                failure = error.getClass().getName() + ":" + String.valueOf(error.getMessage());
            }
        }
        String path = selected == null ? base : selected;
        npc.getEntityData().set(DATA_TEXTURE, path);
        System.out.println("[SL-PROFESSION-267] early id=" + npc.getId()
                + " uuid=" + npc.getUUID()
                + " class=" + npc.getClass().getName()
                + " side=" + (npc.level().isClientSide ? "client" : "server")
                + " excluded=" + excluded
                + " " + details
                + " profileTexture=" + base
                + " selected=" + selected
                + " actual=" + npc.texturePath()
                + " error=" + failure);
    }
}
