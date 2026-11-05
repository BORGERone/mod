package dev.settledlands.door;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 371: a locked door cannot stay open, whatever opened it.
 *
 * <p>The click is refused twice over now ({@code DoorEvents368} denies the use and closes the door in
 * the same tick), but a lock that depends on one event is a lock with one hole in it: another mod, a
 * redstone signal, a piston, an odd interaction path, a client that guessed. This pass makes the lock a
 * fact of the world instead: a locked door that is open and that no guide opened is closed again.</p>
 *
 * <p>Cost is bounded on purpose. The list of locks is copied once every {@value #REBUILD} ticks, and
 * only {@value #BUDGET} of them are looked at per tick, so a world with thousands of locked doors costs
 * the same as a world with three. A door a guide is walking through is left alone
 * ({@code NpcDoors368.guideOpened}): the author asked for guides to pass, not for doors to be sealed.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class DoorGuard371 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorGuard371");

    /** Doors checked per tick. A door that is open by accident is open for a fraction of a second. */
    private static final int BUDGET = 64;

    /** How often the list of locks is re-copied. */
    private static final int REBUILD = 40;

    private static List<String> keys = new ArrayList<>();
    private static int cursor;
    private static long builtAt = Long.MIN_VALUE;
    private static boolean failed;
    private static int closed;

    private DoorGuard371() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (failed) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        try {
            long time = server.overworld() == null ? 0L : server.overworld().getGameTime();
            if (keys.isEmpty() || time - builtAt >= REBUILD || time < builtAt) {
                rebuild(server);
                if (keys.isEmpty()) return;
            }
            check(server);
        } catch (Throwable failure) {
            failed = true;
            if (failure != null) {
                LOG.warn("the locked door guard switched off for this session", failure);
            }
        }
    }

    private static void rebuild(MinecraftServer server) {
        keys = new ArrayList<>();
        cursor = 0;
        ServerLevel overworld = server.overworld();
        builtAt = overworld == null ? 0L : overworld.getGameTime();
        DoorStore368 store = overworld == null ? null : DoorStore368.get(overworld);
        if (store == null) return;
        Set<String> snapshot = store.snapshot();
        if (snapshot == null) return;
        keys.addAll(snapshot);
    }

    /** Looks at the next slice of the locks and closes what should not be open. */
    private static void check(MinecraftServer server) {
        int looked = 0;
        int size = keys.size();
        while (looked < BUDGET && size > 0) {
            if (cursor >= size) cursor = 0;
            String key = keys.get(cursor++);
            looked++;
            BlockPos pos = DoorStore368.positionOf(key);
            if (pos == null) continue;
            ServerLevel level = levelOf(server, DoorStore368.dimensionOf(key));
            if (level == null || !level.hasChunkAt(pos)) continue;
            if (!Doors368.isDoor(level.getBlockState(pos))) continue;
            if (!Doors368.anyOpen(level, pos)) continue;
            if (NpcDoors368.guideOpened(level, pos)) continue;
            // Build 377: a door a player has just cracked open is open on purpose. Without this the
            // lock panel would work and the door would slam shut on the next tick.
            if (DoorHack377.cracked(level, pos)) continue;
            Doors368.setOpen(level, pos, false);
            if (closed < 3) {
                closed++;
                LOG.info("door: the guard closed the locked door at " + pos + " in level "
                        + level.dimension());
            }
        }
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isEmpty()) return null;
        Iterable<ServerLevel> levels = server.getAllLevels();
        if (levels == null) return null;
        for (ServerLevel level : levels) {
            if (level == null) continue;
            if (dimension.equals(DoorStore368.dimensionOf(level))) return level;
        }
        return null;
    }

    /** How many locks the guard is watching - a probe, not a rule. */
    public static int watching() {
        return keys.size();
    }
}
