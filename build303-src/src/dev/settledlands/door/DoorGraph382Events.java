package dev.settledlands.door;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Build 382: the NPC wand on a door opens the graph of that door.
 *
 * <p>Two things have to be true at once here, and both are why this class exists instead of one more
 * branch in {@code DoorEvents368}:</p>
 *
 * <ul>
 *   <li><b>it has to happen before the block graph of build 351</b> - that hook opens the graph of
 *       <b>any</b> block with this wand, and a door is a block. Without the priority, a wand click on a
 *       door would open the block editor of a door: a graph with the block palette that nothing ever
 *       runs;</li>
 *   <li><b>it has to happen before the lock answer</b> - a locked door refuses every click, and a wand
 *       click must reach the editor instead of the refusal. The event is cancelled here with
 *       {@code SUCCESS}, so the door does not move and the lock listener never has to answer.</li>
 * </ul>
 *
 * <p>Only the server half opens anything: the editor arrives as the ordinary {@code CfgView} of the
 * stock screen, exactly like the block editor of build 351. Nothing happens without permission 2, and
 * nothing happens for any other item - an empty hand or the mod's key keep every rule they had.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class DoorGraph382Events {

    private DoorGraph382Events() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event == null) return;
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!DoorGraph382.isDoorAt(level, pos)) return;
            if (!isWand(event.getItemStack())) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (DoorGraph382.open(player, level, pos)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        } catch (Throwable failure) {
            DoorGraph382.fail(failure);
        }
    }

    /** True when this stack is the NPC configuration wand of the base mod. */
    public static boolean isWand(ItemStack stack) {
        try {
            return stack != null && !stack.isEmpty() && dev.settledlands.npc.NpcContent.wand() != null
                    && stack.is(dev.settledlands.npc.NpcContent.wand());
        } catch (Throwable failure) {
            DoorGraph382.fail(failure);
            return false;
        }
    }
}
