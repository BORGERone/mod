package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcArmor348;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 348 - an NPC's inventory change is the moment its armour changes.
 *
 * <p>{@code NpcInventory} is the container the NPC inventory screen works on: it is built from the
 * NPC's saved data when the screen opens and writes the whole list back on every edit. Both sides of
 * that life are hooked here, so the armour the NPC wears follows the inventory without a tick loop:
 * {@code setChanged} after each edit, the constructor for the list as it comes off disk.</p>
 *
 * <p>The container itself is passed on as it is - the sync only reads it and never writes, so the
 * inventory cannot change under the open screen.</p>
 */
@Mixin(targets = "dev.settledlands.npc.PossessionSystem$NpcInventory", remap = false)
public abstract class NpcInventoryArmor348Mixin {
    @Shadow
    @Final
    private GuideNpc npc;

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void settledlands$armorAfterEdit(CallbackInfo info) {
        NpcArmor348.sync(this.npc, (Container) (Object) this);
    }

    @Inject(method = "<init>(Ldev/settledlands/npc/GuideNpc;)V", at = @At("TAIL"))
    private void settledlands$armorAfterLoad(GuideNpc owner, CallbackInfo info) {
        NpcArmor348.sync(this.npc, (Container) (Object) this);
    }
}
