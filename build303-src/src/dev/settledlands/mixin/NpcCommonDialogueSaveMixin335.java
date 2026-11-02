package dev.settledlands.mixin;

import dev.settledlands.npc.NpcCommonDialogue335;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 335: the merged graph a talk runs on never reaches disk or another player's editor.
 *
 * <p>{@code NpcProfile.save} is the single door the profile leaves through - it writes the entity
 * NBT, it is what the copy tool reads, and {@code GuideNpc.sendConfigView} calls it to build the
 * packet the graph editor opens. For the length of that one call the NPC's own graph is put back, so
 * every one of those sees exactly the graph the author built. The merge itself is in place again on
 * return, because the conversation that is running still needs it.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcProfile", remap = false)
public abstract class NpcCommonDialogueSaveMixin335 {

    @Unique
    private NpcDialogueGraph settledlands$keptDialogue;

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void settledlands$stripCommon(HolderLookup.Provider provider,
            CallbackInfoReturnable<CompoundTag> cir) {
        this.settledlands$keptDialogue = NpcCommonDialogue335.strip((NpcProfile) (Object) this);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void settledlands$putCommonBack(HolderLookup.Provider provider,
            CallbackInfoReturnable<CompoundTag> cir) {
        NpcDialogueGraph kept = this.settledlands$keptDialogue;
        this.settledlands$keptDialogue = null;
        NpcCommonDialogue335.unstrip((NpcProfile) (Object) this, kept);
    }
}
