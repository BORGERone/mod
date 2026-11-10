package dev.settledlands.chest;

import dev.settledlands.door.DoorGraph382Events;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 396: the two clicks a chest has - the wand opens its graph, an empty hand opens the chest and the
 * chest's chain runs when the window closes.
 *
 * <p><b>The wand.</b> Exactly the door's seam ({@code DoorGraph382Events}), for exactly the door's reason:
 * the block hook of build 351 opens the graph of <b>any</b> block a wand is clicked on, and a chest is a
 * block. This hook runs at {@code HIGHEST}, so a wand click on a chest opens the chest's graph and never the
 * block graph of a chest - a graph with the block palette that nothing would ever run.</p>
 *
 * <p><b>The opening.</b> A chest is not a door: a door has no window, so its chain runs the moment it is
 * cracked, while a chest puts its own inventory window in front of the player in the same tick. A dialogue
 * sent then would be drawn under that window or replace it - either way the author sees «ничего не
 * происходит» again, and build 396 exists to avoid that. So the chest's chain runs when the chest's window
 * <b>closes</b>: he takes what he came for, the chest shuts, and the «Реплика» of the chest says its line
 * with nothing in front of it. The card says the moment in words
 * ({@code gui.settledlands.chest.trigger}).</p>
 *
 * <p><b>How the close is seen, and why it is not guessed.</b> {@code Player#containerMenu} is the vanilla
 * field that says what window a player has open, and the base mod itself compares it with
 * {@code Player#inventoryMenu} ({@code NpcEscort}, line 93 of its own decompiled source) - so this is the
 * game's own answer, not a reflection trick and not a timer. The sequence is: the click is recorded, the
 * chest's window opens in the same tick, the pass sees a menu that is not the inventory, and the moment that
 * menu is gone the chain runs. A window that never appears (a chest the player cannot open after all) falls
 * back to two seconds, so a click never leaves a pending chain behind for ever. A player who logs out or
 * changes dimension drops his entry - the chain of a chest belongs to the player standing at it.</p>
 *
 * <p><b>Build 397: the lock.</b> The card of a chest carries the door's four settings - keys, seconds,
 * difficulty and «Закрыта/Открыта» - so a click on a <b>locked</b> chest is refused here where the chest
 * would have been opened, and what the player gets instead is the very same lock panel a door opens, with
 * this chest's numbers ({@link ChestNet397}). A solved panel comes back as a packet, the server re-checks
 * the lock and the distance, and then opens the chest's own window - which is the opening this class was
 * already about, so the chain still runs when that window closes. {@link #onBreak} closes the other way in:
 * a locked chest cannot be broken open, or the lock would be a decoration.</p>
 *
 * <p>Cost: one range check and one map lookup per right-click on a chest, one graph lookup more when that
 * chest is locked, and one map walk per tick that is skipped at once while nobody has a pending chest
 * open.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class ChestGraph396Events {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/ChestGraph396");

    /** Ticks a click may take to become a window: the game opens it in the same tick, this is slack. */
    private static final long OPEN_GRACE = 2L;

    /** Ticks after which a click that never became a window is answered anyway. */
    private static final long NO_WINDOW = 40L;

    /** Ticks after which an entry is forgotten - ten minutes of standing in a chest's window is enough. */
    private static final long EXPIRE = 12_000L;

    /** One player's chest: where it is, and whether its window has been seen. */
    private static final class Pending {
        final ServerLevel level;
        final BlockPos pos;
        final String key;
        final long at;
        boolean window;

        /**
         * Build 398: was this opening a <b>crack</b>? A chest whose panel was solved runs the chain under its
         * «взлом» socket, an ordinary opening runs the chain under «открытие» - the same moment (the window
         * closing), two different chains, which is the whole point of having two sockets on the card.
         */
        final boolean cracked;

        Pending(ServerLevel level, BlockPos pos, String key, long at) {
            this(level, pos, key, at, false);
        }

        Pending(ServerLevel level, BlockPos pos, String key, long at, boolean cracked) {
            this.level = level;
            this.pos = pos;
            this.key = key;
            this.at = at;
            this.cracked = cracked;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private ChestGraph396Events() {}

    // ---- the wand -----------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWand(PlayerInteractEvent.RightClickBlock event) {
        if (event == null) return;
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!ChestGraph396.isChestAt(level, pos)) return;
            if (!DoorGraph382Events.isWand(event.getItemStack())) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (ChestGraph396.open(player, level, pos)) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        } catch (Throwable failure) {
            ChestGraph396.fail(failure);
        }
    }

    // ---- the opening --------------------------------------------------------

    /**
     * A click on a chest that has a graph. Only a main-hand click of a player who is not using the wand -
     * the wand belongs to the editor above, and the off hand would count the same click twice.
     */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event == null || event.isCanceled()) return;
        try {
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!ChestGraph396.isChestAt(level, pos)) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (DoorGraph382Events.isWand(event.getItemStack())) return;
            String key = ChestGraph396Store.key(level, pos);
            if (key.isEmpty()) return;
            ChestGraph396Store store = ChestGraph396Store.get(level.getServer());
            NpcDialogueGraph graph = store == null ? null : store.graph(key);
            if (graph == null) return;                       // an ordinary chest: nothing of ours here
            // Build 397: a chest whose card says «Закрыта» does not open for a plain click. The click is
            // refused where the chest would have been opened - not undone a tick later - so a locked
            // chest never stands open for a single tick (the rule of builds 371/376). What opens instead
            // is the very same panel a locked door opens, with this chest's four numbers.
            int[] panel = ChestLock397.panel(level, pos);
            // Build 398/400/401: the click that follows a solved panel is the opening. Build 400 lifted the
            // lock for whoever clicked first; build 401 keys the lift by the player who solved the panel and by
            // the chest, spends it on exactly one click, and keeps it for ten minutes - the chest's «взлом»
            // chain now plays before this click, and a «Реплика» is read at the player's own pace. The click it
            // belongs to is the crack, and every later opening is an ordinary one.
            boolean cracked = ChestLock397.takeCrack(player.getName().getString(), key);
            if (panel != null && cracked) panel = null;
            if (panel != null) {
                event.setCanceled(true);
                try {
                    event.setCancellationResult(InteractionResult.FAIL);
                } catch (Throwable missing) {
                    ChestGraph396.fail(missing);
                }
                if (ChestLock397.illegal(level, pos)
                        && !dev.settledlands.npc.NpcIllegalHack417.beginIllegalHack(
                                player, level, pos, true, key)) {
                    return;
                }
                player.displayClientMessage(
                        Component.translatable("message.settledlands.chest.locked"), true);
                ChestNet397.sendPanel(player, level, pos, key, panel);
                return;                                  // the chain does not run: nothing was opened
            }
            // Build 398: a chest opened through a lifted lock runs the chain under the card's «взлом» socket;
            // an ordinary opening runs «открытие».
            remember(player, level, pos, key, cracked);
        } catch (Throwable failure) {
            ChestGraph396.fail(failure);
        }
    }

    /**
     * Build 397: the two facts a real opening leaves on the card - who opened it, how many times - and the
     * chain that runs when the window closes.
     *
     * <p>One place for both ways in: a plain click on an unlocked chest ({@link #onUse}) and a locked chest
     * whose panel was solved ({@code ChestNet397#solve}, which opens the chest itself). Whatever opened the
     * chest, the graph of that chest behaves the same.</p>
     */
    public static void remember(ServerPlayer player, ServerLevel level, BlockPos pos, String key) {
        remember(player, level, pos, key, false);
    }

    /** Build 398: the same, for an opening that was a crack - its chain starts under «взлом». */
    public static void remember(ServerPlayer player, ServerLevel level, BlockPos pos, String key,
            boolean cracked) {
        if (player == null || level == null || pos == null) return;
        try {
            ChestGraph396.noteOpened(player, level, pos, key);
            PENDING.put(player.getUUID(),
                    new Pending(level, pos, key, now(level.getServer()), cracked));
        } catch (Throwable failure) {
            ChestGraph396.fail(failure);
        }
    }

    /**
     * Build 397: a locked chest cannot be broken open either.
     *
     * <p>The lock would be a decoration if it could be: a locked chest broken in one swing drops its whole
     * contents. The door has the same guard since build 368, with the same line in the action bar, and the
     * lock of a chest comes off the same way it goes on - in the editor, on the card, by whoever may open
     * the graph.</p>
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event == null) return;
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!ChestGraph396.isChestAt(level, pos)) return;
            if (!ChestLock397.locked(level, pos)) return;
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.settledlands.chest.locked"), true);
            }
        } catch (Throwable failure) {
            ChestGraph396.fail(failure);
        }
    }

    // ---- the close ----------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        long now = now(server);
        Iterator<Map.Entry<UUID, Pending>> walk = PENDING.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<UUID, Pending> entry = walk.next();
            try {
                Pending pending = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || player.level() != pending.level) {
                    walk.remove();                        // gone or elsewhere: the window closed with him
                    continue;
                }
                long age = now - pending.at;
                if (age > EXPIRE) {
                    walk.remove();
                    continue;
                }
                // The game's own answer to "what window is open": the two fields the base mod itself
                // compares ({@code NpcEscort}: {@code containerMenu == inventoryMenu}), read on
                // ServerPlayer, exactly as that jar reads them - the descriptors are that jar's own
                // (AbstractContainerMenu and InventoryMenu), not a guess of this build's stub.
                boolean open = player.containerMenu != player.inventoryMenu;
                if (open) {
                    pending.window = true;
                    continue;
                }
                if (age < OPEN_GRACE) continue;           // the same tick the click happened in
                if (!pending.window && age < NO_WINDOW) continue;   // the window is still to come
                walk.remove();
                // Build 402: the close is the «открытие» moment, and it is the same moment for every opening.
                // The «взлом» socket is not a second way in - it is the thing that happens <b>before</b> the
                // chest opens (build 401, and only when it is wired). A cracked chest therefore plays both,
                // in that order: the lock speaks, the chest opens, he takes what he came for, the chest shuts,
                // and «открытие» speaks here. Build 401 skipped this for a crack, which silenced the author's
                // own card - his «Реплика» is wired to «открытие», and his «взлом» is empty.
                ChestGraph396.run(player, pending.level, pending.pos, pending.key, pending.cracked);
            } catch (Throwable failure) {
                walk.remove();
                ChestGraph396.fail(failure);
            }
        }
    }

    /** The world's own clock - the same one the radius pass counts in. */
    private static long now(MinecraftServer server) {
        try {
            ServerLevel overworld = server == null ? null : server.overworld();
            if (overworld == null) return 0L;
            return overworld.getGameTime();
        } catch (Throwable failure) {
            return 0L;
        }
    }

    /** How many players have a chest waiting for a close - for the gate, never for the game. */
    public static int pending() {
        return PENDING.size();
    }
}
