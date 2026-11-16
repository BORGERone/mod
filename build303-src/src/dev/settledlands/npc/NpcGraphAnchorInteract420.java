package dev.settledlands.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Build 420: the wand, clicked on a body, is the handle of an anchor.
 *
 * <p>The pass of {@link NpcGraphAnchor420} puts a marker beside a building by itself, and that is the
 * whole feature - an author never has to know the anchor exists. This listener is the deliberate half,
 * for the case the automatic half cannot see: a build whose only content is a body with a graph, which
 * lives on the body and not in the world.</p>
 *
 * <ul>
 *   <li><b>the wand on an anchor marker</b> - "write the graphs of this building into this marker now",
 *       the same thing {@code /settledlands anchor seal} does;</li>
 *   <li><b>the wand on a plain body</b> - "this body is part of a build, give that build an anchor
 *       here".</li>
 * </ul>
 *
 * <p>Two things are taken from the shape of this mod's own click listeners rather than invented. The
 * event is {@code PlayerInteractEvent.EntityInteract}, which {@code BlockTriggerEvents351} already
 * subscribes to for a block (its cousin {@code RightClickBlock}) and which the base mod uses on the same
 * bus from {@code PossessionSystem}. The priority is {@link EventPriority#LOWEST}, so the base mod's own
 * answer to a wand click - {@code PossessionCultBridge}'s possession path, {@code PossessionSystem}'s
 * inventory path - is decided first; the anchor writes what it writes and cancels the click only when it
 * really wrote, leaving every other click exactly as it was before this build.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcGraphAnchorInteract420 {

    private NpcGraphAnchorInteract420() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event == null) return;
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level() == null || !(player.level() instanceof ServerLevel level)) return;
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) return;
            if (NpcContent.wand() == null || !stack.is(NpcContent.wand())) return;
            Entity target = event.getTarget();
            if (target == null) return;
            if (!NpcGraphAnchor420.wandOn(player, level, target)) return;
            event.setCancellationResult(InteractionResult.sidedSuccess(false));
            event.setCanceled(true);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
        }
    }
}
