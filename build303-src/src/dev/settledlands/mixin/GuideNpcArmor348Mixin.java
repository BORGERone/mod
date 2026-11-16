package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcArmor348;
import dev.settledlands.npc.PossessionSystem;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 348 - an NPC that already carries armour puts it on when the world loads.
 *
 * <p>The inventory screen is not the only way an inventory can hold a plate: the author's NPCs were
 * filled before this build existed, and after this build an NPC can also be copied with its inventory
 * intact. Neither case opens the screen, so nothing would set the equipment slots. {@code
 * readAdditionalSaveData} runs once per NPC per load, right after the mob's own equipment lists have
 * been read - which makes it the exact place to bring the worn set in line with the carried one,
 * without paying for a check on every tick.</p>
 */
@Mixin(value = GuideNpc.class, remap = false)
public abstract class GuideNpcArmor348Mixin {
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void settledlands$armorOnLoad(CompoundTag tag, CallbackInfo info) {
        GuideNpc self = (GuideNpc) (Object) this;
        NpcArmor348.sync(self, new PossessionSystem.NpcInventory(self));
    }
}
