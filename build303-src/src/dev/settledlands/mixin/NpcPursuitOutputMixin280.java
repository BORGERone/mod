package dev.settledlands.mixin;

import dev.settledlands.npc.NpcPursuit269;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 280: the pursuit node outputs its target.
 *
 * <p>A blueprint node hands its result to the nodes wired after it; this mod has
 * no value wires, so the result travels the way every other value travels here -
 * as a placeholder in the text. The pursuit node publishes the target it is bound
 * to (nick of the player or identifier of the NPC), and every node that runs
 * after it can print or use it:</p>
 *
 * <ul>
 *   <li>{@code %pursuit%} - the target itself;</li>
 *   <li>{@code %pursuit_kind%} - {@code player} or {@code npc};</li>
 *   <li>{@code %pursuit_uuid%} - UUID of the resolved entity, once it is found.</li>
 * </ul>
 *
 * <p>Both paths meet in {@code NpcProfile#fill(String, Player, int)}: the dialogue
 * screen fills a line with it, and {@code NpcActions#fill} fills every trigger
 * action and command with it before running them. One hook covers both, and a text
 * without a {@code %pursuit} token leaves the method untouched (the value is read,
 * not copied, so a hot path is never slowed down).</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcProfile", remap = false)
public abstract class NpcPursuitOutputMixin280 {
    @Inject(
            method = "fill(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;I)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$fillPursuitOutput(String text, Player player, int relation,
            CallbackInfoReturnable<String> callback) {
        String value = NpcPursuit269.fillPursuit(callback.getReturnValue(), player);
        if (value != null) callback.setReturnValue(value);
    }
}
