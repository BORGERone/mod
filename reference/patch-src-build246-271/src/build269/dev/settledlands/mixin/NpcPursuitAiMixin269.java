package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcAutoAiEngine255;
import dev.settledlands.npc.NpcPursuit269;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NpcAutoAiEngine255.class, remap = false)
public abstract class NpcPursuitAiMixin269 {
    @Inject(method = "runMobilityAndAutoAi(Ldev/settledlands/npc/GuideNpc;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void settledlands$tickPursuit(GuideNpc npc, CallbackInfo ci) {
        if (NpcPursuit269.tick(npc)) ci.cancel();
    }

    @Inject(method = "saveNpcData(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"), remap = false)
    private static void settledlands$savePursuit(GuideNpc npc, CompoundTag tag, CallbackInfo ci) {
        NpcPursuit269.saveNpcData(npc, tag);
    }

    @Inject(method = "loadNpcData(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"), remap = false)
    private static void settledlands$loadPursuit(GuideNpc npc, CompoundTag tag, CallbackInfo ci) {
        NpcPursuit269.loadNpcData(npc, tag);
    }
}
