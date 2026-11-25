package dev.settledlands.chest;

import dev.settledlands.door.DoorGraph382Events;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Build 436: client-side wand click cancellation on chest/Refurbished-furniture targets.
 *
 * <p>Following {@code DoorEvents368#client}, NeoForge sends {@code ServerboundUseItemOnPacket}
 * before {@code CommonHooks.onRightClickBlock} fires on the client, so cancelling here with
 * {@link InteractionResult#SUCCESS} stops local container prediction and prevents {@code diegesis}
 * from starting a 3D station camera focus while the server wand listener opens the chest graph.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class ChestClientEvents436 {

    private ChestClientEvents436() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event == null || event.isCanceled()) return;
        try {
            Level level = event.getLevel();
            if (level == null || !level.isClientSide) return;
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!DoorGraph382Events.isWand(event.getItemStack())) return;
            if (!ChestGraph396.isChest(level.getBlockState(pos))) return;
            event.setCanceled(true);
            try {
                event.setCancellationResult(InteractionResult.SUCCESS);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }
}
