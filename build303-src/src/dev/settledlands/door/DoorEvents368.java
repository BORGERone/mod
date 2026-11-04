package dev.settledlands.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 368: a locked door is closed to the player and cannot be broken - and the key opens that rule,
 * not the door.
 *
 * <ul>
 *   <li><b>right click with the key</b> - the door is locked (or unlocked again). The click itself is
 *       cancelled, so the key never opens a door it is locking;</li>
 *   <li><b>right click without the key</b> on a locked door - cancelled, with one line in the action
 *       bar. The door does not open, and nothing about it changes;</li>
 *   <li><b>breaking a locked door</b> - cancelled, with the same line. That covers the player and any
 *       mod that fires the break event; the zombie's own door bashing goes through
 *       {@code Level#destroyBlock}, which {@code DoorBreak368Mixin} guards with the same store.</li>
 * </ul>
 *
 * <p>NPCs are not touched here at all: {@link NpcDoors368} opens and closes doors for a walking guide
 * and ignores the lock, which is what the author asked for - the door is closed for the players and
 * for the monsters, not for the guides.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class DoorEvents368 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorEvents368");

    private static final String LOCKED = "message.settledlands.door.locked";
    private static final String ON = "message.settledlands.door.key_on";
    private static final String OFF = "message.settledlands.door.key_off";

    private DoorEvents368() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event == null) return;
        try {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            if (!(level instanceof ServerLevel server) || pos == null) return;
            if (!Doors368.isDoor(server.getBlockState(pos))) return;
            DoorStore368 store = DoorStore368.get(server);
            if (store == null) return;
            ItemStack stack = event.getItemStack();
            boolean key = DoorKey368.isKey(stack);
            boolean locked = store.isLocked(server, pos);
            if (!key) {
                if (!locked) return;
                // A locked door is not a door for a player: no opening, no closing, no sound of a
                // door that would only teach the player to try again.
                event.setCanceled(true);
                say(event.getEntity());
                return;
            }
            event.setCanceled(true);
            boolean nowLocked;
            if (locked) {
                store.unlock(server, pos);
                nowLocked = false;
            } else {
                store.lock(server, pos);
                nowLocked = true;
            }
            tell(event.getEntity(), Component.translatable(nowLocked ? ON : OFF).getString());
        } catch (Throwable failure) {
            LOG.warn("a door click could not be handled", failure);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event == null) return;
        try {
            if (!(event.getLevel() instanceof ServerLevel server)) return;
            BlockPos pos = event.getPos();
            if (pos == null || !Doors368.isDoor(server.getBlockState(pos))) return;
            DoorStore368 store = DoorStore368.get(server);
            if (store == null || !store.isLocked(server, pos)) return;
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) say(player);
        } catch (Throwable failure) {
            LOG.warn("a door break could not be handled", failure);
        }
    }

    /** Is this place locked? The one question {@code DoorBreak368Mixin} asks the server. */
    public static boolean locked(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || pos == null) return false;
        if (!Doors368.isDoor(server.getBlockState(pos))) return false;
        DoorStore368 store = DoorStore368.get(server);
        return store != null && store.isLocked(server, pos);
    }

    private static void say(net.minecraft.world.entity.Entity entity) {
        tell(entity, Component.translatable(LOCKED).getString());
    }

    private static void tell(net.minecraft.world.entity.Entity entity, String line) {
        if (entity instanceof ServerPlayer player && line != null && !line.isBlank()) {
            player.displayClientMessage(Component.literal(line), true);
        }
    }
}
