package dev.settledlands.mixin;

import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcWorkspace306;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 306: the dialogue editor opens as the node workspace.
 *
 * <p>{@code open} is the single entry point every caller in the mod uses - the config screen,
 * the dialogue itself, the "open graph" button - so redirecting it here is enough to make the
 * workspace the editor, and the old screen still exists behind the workspace's own button.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWorkspaceEntry306Mixin {

    @Inject(method = "open(ILdev/settledlands/npc/NpcProfile;Lnet/minecraft/client/gui/screens/Screen;I)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$openWorkspace(int entityId, NpcProfile profile, Screen parent,
            int focus, CallbackInfo ci) {
        if (profile == null) return;
        Minecraft.getInstance().setScreen(new NpcWorkspace306(entityId, profile, parent, focus));
        ci.cancel();
    }
}
