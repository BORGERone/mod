package dev.settledlands.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    /** Build 372: how many diagnostic lines this session has written. A report, not a rule. */
    private static int said;

    /** Build 374: has the door-side refusal been reached at least once in this session? */
    private static boolean sawUse;

    /** Build 375: has the client refused to open a door on its own at least once in this session? */
    private static boolean sawClient;

    private DoorEvents368() {}

    private static void note(String line) {
        if (said >= 8) return;
        said++;
        LOG.info(line);
    }

    /**
     * Build 374: one line the first time the game enters
     * {@code DoorBlock#useWithoutItem} with this mod's guard in place.
     *
     * <p>373 put the refusal inside the door, which is the only place where a click on a door can be
     * stopped before the door opens - but a mixin that never bound to the method looks exactly like a
     * mixin that bound and refused nothing. This line is the difference between the two: it is written
     * from inside the injected handler, so its presence in the log proves the guard is live, and its
     * {@code locked} value proves whether the lock was found at that position.</p>
     */
    public static void sawDoorUse(BlockPos pos, boolean locked) {
        if (sawUse) return;
        sawUse = true;
        try {
            note("door: DoorBlock#useWithoutItem was reached at " + pos + ", locked=" + locked
                    + " - the door-side guard of build 374 is live");
        } catch (Throwable ignored) {
            // a report must never be the reason a click fails
        }
    }

    /**
     * Build 375: the client is not allowed to open a door by itself.
     *
     * <p>This is the tick the author kept seeing. In 1.21.1 the client decides whether a click reaches
     * the block's own use in {@code MultiPlayerGameMode#performUseItemOn}, and the only thing that stops
     * it there is a cancelled {@code RightClickBlock} event
     * ({@code if (event.isCanceled()) return event.getCancellationResult();}). Our handler used to return
     * on the first line for anything that is not a {@code ServerLevel} - so on the client the event was
     * never cancelled, the client ran {@code DoorBlock#useWithoutItem} locally, and the door it drew stood
     * open until the server's answer arrived. The lock cannot be asked on the client (the store lives in
     * the server's saved data), so the client refuses every door click and lets the server decide: an
     * unlocked door is opened by the server one tick later, a locked one is never opened at all.</p>
     *
     * <p>The answer is {@code InteractionResult.SUCCESS} - a swung arm and no local use. The click still
     * reaches the server: the packet is sent before the prediction runs.</p>
     */
    private static void client(Level level, BlockPos pos, PlayerInteractEvent.RightClickBlock event) {
        try {
            if (level == null) return;
            BlockState here = level.getBlockState(pos);
            if (!Doors368.isDoor(here)) return;
            // Build 382: the wand opens the door's graph on the server. A door this client predicts
            // open while the server is opening an editor is a door that looks open and is not, so the
            // click is refused here as well - locked or not.
            if (DoorGraph382Events.isWand(event.getItemStack())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            // Build 376: once the server has told this client where the locks are, only a locked door
            // is refused and every other door is predicted locally again - the lag of 375 on ordinary
            // doors was the price of not knowing. Until the first snapshot arrives the strict answer
            // stays: refuse everything, be slow, never let a locked door open for a tick.
            if (DoorSync375.synced() && !DoorSync375.isLocked(level, pos)) return;
            // Build 377: the lock is no longer a wall with nothing behind it. On a door this client
            // knows to be locked, and without the mod's key in hand, the click opens the hack panel -
            // a row of keys that all have to be held at once. The click is still refused here, so the
            // door does not move while the panel is up, and the panel itself changes no rule: it can
            // only ask the server to open the door it was opened for, and the server checks the lock.
            // Build 382: the wand is not a pair of hands. A wand click on a door opens the door's
            // own graph on the server (DoorGraph382Events), so the panel must stay down here - only
            // an empty hand, or anything that is not the key and not the wand, cracks a lock.
            if (DoorSync375.synced() && DoorSync375.isLocked(level, pos)
                    && !DoorKey368.isKey(event.getItemStack())
                    && !DoorGraph382Events.isWand(event.getItemStack())
                    && !DoorSync375.isIllegal(level, pos)) {
                HackPanel377.open(pos);
            }
            event.setCanceled(true);
            try {
                event.setCancellationResult(InteractionResult.SUCCESS);
            } catch (Throwable missing) {
                LOG.warn("the client could not refuse a door click: " + missing);
            }
            if (!sawClient) {
                sawClient = true;
                note("door: the client refused to open the door at " + pos
                        + " on its own - the lock is known to this client (build 377)");
            }
        } catch (Throwable failure) {
            LOG.warn("a door click could not be refused on the client", failure);
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event == null) return;
        // Build 382: the wand on a door opens the door's graph and cancels the click itself. A click
        // that was already answered must not also be refused here - the author would read "дверь
        // заперта" while the editor was opening.
        if (event.isCanceled()) return;
        try {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            if (pos == null) return;
            if (!(level instanceof ServerLevel server)) {
                // Build 375: the client never opens a door on its own guess.
                client(level, pos, event);
                return;
            }
            BlockState here = server.getBlockState(pos);
            if (!Doors368.isDoor(here)) {
                if (DoorKey368.isKey(event.getItemStack()) && here != null) {
                    note("door: the key was used on " + here.getBlock() + " at " + pos
                            + " - that is not a DoorBlock, so nothing was locked");
                }
                return;
            }
            DoorStore368 store = DoorStore368.get(server);
            if (store == null) return;
            ItemStack stack = event.getItemStack();
            boolean key = DoorKey368.isKey(stack);
            boolean locked = store.isLocked(server, pos);
            if (!key) {
                if (!locked) {
                    // Build 389: an ordinary door does not pulse. The signal of a door's graph is its
                    // hack - a solved lock panel (DoorHack377) - and an unlocked door has no panel to
                    // solve, so a plain click stays a plain click: vanilla opens the door and the graph
                    // is not touched at all.
                    //
                    // Build 401: except that this IS the moment the author wired his «Реплика» to. The
                    // click on a shut door is handed to DoorSignal398, which reads the door once more on
                    // the next tick and runs the «открытие» chain if the click opened it. This leg does
                    // not depend on a mixin matching this game version - the event is the game's own -
                    // which is why the socket of build 398 cannot be silent any more.
                    if (!Doors368.anyOpen(server, pos) && event.getEntity() instanceof ServerPlayer clicker) {
                        DoorSignal398.watch(clicker, server, pos);
                    }
                    return;
                }
                // A locked door is not a door for a player: no opening, no closing, no sound of a
                // door that would only teach the player to try again.
                deny(event);
                shut(server, pos);
                note("door: the click on the locked door at " + pos + " was refused (useBlock="
                        + "DENY), open now=" + Doors368.anyOpen(server, pos));
                if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                        && event.getEntity() instanceof ServerPlayer clicker
                        && !DoorGraph382Events.isWand(stack)
                        && DoorGraph382.illegalAt(server, pos)) {
                    BlockPos lowPos = DoorGraph382.lower(server, pos);
                    String doorKey = DoorGraph382Store.lowerKey(server, pos);
                    if (dev.settledlands.npc.NpcIllegalHack417.beginIllegalHack(
                            clicker, server, lowPos, false, doorKey)) {
                        say(clicker);
                        DoorSync375.sendOpenDoorHack(clicker, pos);
                    }
                    return;
                }
                say(event.getEntity());
                return;
            }
            deny(event);
            shut(server, pos);
            boolean nowLocked;
            if (locked) {
                store.unlock(server, pos);
                nowLocked = false;
            } else {
                store.lock(server, pos);
                nowLocked = true;
            }
            // Build 376: every client that can see this door learns about it now, so from here on a
            // locked door is refused locally and an unlocked one is predicted locally.
            DoorSync375.sendDelta(server, pos, nowLocked);
            note("door: the door at " + pos + (nowLocked ? " is locked" : " is unlocked")
                    + " by the key, " + (store.size()) + " place(s) locked in this world");
            tell(event.getEntity(), Component.translatable(nowLocked ? ON : OFF).getString());
        } catch (Throwable failure) {
            LOG.warn("a door click could not be handled", failure);
        }
    }

    /**
     * Build 376: a client that has just joined is told every lock there is.
     *
     * <p>Without this the client would fall back to refusing every door click (build 375) - correct,
     * but laggy. The snapshot is sent before the player can reach a door, so the strict fallback only
     * covers the gap between joining and this line.</p>
     */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null) return;
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                DoorSync375.sendSnapshot(player);
                // Build 382: and the numbers of every door that has a graph, so the panel of that door
                // asks for the count and the speed its author set.
                DoorSync375.sendParams(player);
            }
        } catch (Throwable failure) {
            LOG.warn("a joining player could not be told which doors are locked", failure);
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

    /**
     * Refuses the click at its source.
     *
     * <p>Build 371: cancelling alone was not enough. A right click on a door is carried out by the
     * block's own use, and the yes/no the game actually reads is {@code useBlock} / {@code useItem} -
     * so both are denied explicitly, and the cancellation stays for every listener that never looks at
     * those two fields.</p>
     */
    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        // Build 373: this is the answer NeoForge 1.21.1 actually reads - the same call the base mod
        // already makes elsewhere. 371 used setUseBlock/setUseItem, which do not exist on this event:
        // the call threw, the listener died before it could refuse anything, and the door opened
        // exactly as before. Nothing in this method may run after a call that can fail, which is why
        // the result is set in its own small guard.
        try {
            event.setCancellationResult(InteractionResult.FAIL);
        } catch (Throwable missing) {
            LOG.warn("the door could not be refused through the event: " + missing);
        }
    }

    /**
     * Puts the door back the way it must be, and tells the clients.
     *
     * <p>Build 371: this is what "the door opens for a second" was. The client opens a door the moment
     * it is clicked and only then asks the server; when the server refuses, nothing had changed on the
     * server, so nothing was sent back - and the client kept its open door in front of the player for
     * as long as it took. Closing the door here and pushing the state out costs two calls and ends the
     * picture: whatever the client guessed, the answer arrives in the same tick.</p>
     */
    private static void shut(ServerLevel server, BlockPos pos) {
        if (Doors368.anyOpen(server, pos)) {
            Doors368.setOpen(server, pos, false);
        }
        BlockState state = server.getBlockState(pos);
        if (state != null) {
            server.sendBlockUpdated(pos, state, state, 3);
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
