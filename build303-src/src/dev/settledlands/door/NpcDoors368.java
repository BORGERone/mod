package dev.settledlands.door;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 368: a guide opens the door it walks into and closes it behind itself.
 *
 * <p>Vanilla mobs do this with an AI goal, which only runs when the mob's own goal selector decides to
 * walk somewhere. A guide is not driven that way: it is walked by the mod itself - a mobility point, an
 * escort, a scene - so a goal would open a door the guide never asked for, or open nothing at all. This
 * is a small pass instead, on the same server tick the rest of the mod already uses:</p>
 *
 * <ul>
 *   <li>every {@value #PERIOD} ticks, for every guide that has <b>moved</b> since the last look (that
 *       is what "while moving" means: the door of a guide that stands still is nobody's business);</li>
 *   <li>a door within {@value #REACH} blocks that is closed is opened, and remembered as opened by this
 *       guide;</li>
 *   <li>a door a guide opened is closed again once no guide is near it ({@value #CLOSE_RANGE} blocks),
 *       and only if it is still open - a door a player closed stays closed.</li>
 * </ul>
 *
 * <p>A locked door is opened here like any other: the lock is a rule for players and monsters
 * ({@link DoorEvents368}), and the author asked for the guides to keep walking through their own
 * doors.</p>
 *
 * <p>Cost: a list walk of the guides twice a second, one block lookup per closed door candidate, and no
 * block write unless a door is actually in the way.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcDoors368 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/NpcDoors368");

    /** Twice a second: a walking guide covers a third of a block in that time. */
    private static final int PERIOD = 10;

    /** How close a door has to be to be opened for the guide. */
    private static final double REACH = 1.6D;

    /** How far a guide has to get from a door it opened before the door is closed again. */
    private static final double CLOSE_RANGE = 2.4D;

    /** A door opened by a guide and left behind is closed after this many ticks anyway. */
    private static final long CLOSE_AFTER = 100L;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final List<Open> OPENED = new ArrayList<>();

    /** One door a guide opened, and when. */
    private static final class Open {
        final ServerLevel level;
        final BlockPos pos;
        final long at;

        Open(ServerLevel level, BlockPos pos, long at) {
            this.level = level;
            this.pos = pos;
            this.at = at;
        }
    }

    private static boolean failed;

    private NpcDoors368() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (failed) return;
        MinecraftServer server = event == null ? null : event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.getGameTime() % PERIOD != 0L) return;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                walk(level, overworld.getGameTime());
            }
            closeLeftBehind(overworld.getGameTime());
        } catch (Throwable failure) {
            failed = true;
            if (failure != null) {
                LOG.warn("the guide door pass switched off for this session", failure);
            }
        }
    }

    private static void walk(ServerLevel level, long now) {
        Iterable<Entity> bodies = level.getAllEntities();
        if (bodies == null) return;
        List<UUID> seen = new ArrayList<>();
        for (Entity body : bodies) {
            if (!(body instanceof dev.settledlands.npc.GuideNpc npc)) continue;
            UUID id = npc.getUUID();
            if (id == null) continue;
            seen.add(id);
            double[] last = LAST_POS.get(id);
            double x = npc.getX();
            double y = npc.getY();
            double z = npc.getZ();
            LAST_POS.put(id, new double[] {x, y, z});
            if (last == null) continue;
            double moved = Math.abs(x - last[0]) + Math.abs(y - last[1]) + Math.abs(z - last[2]);
            if (moved < 0.05D) continue;   // standing still: nothing to open for it
            openAround(level, npc, x, y, z, now);
        }
        if (LAST_POS.size() > 256) {
            LAST_POS.keySet().removeIf(id -> !seen.contains(id));
        }
    }

    /** Opens the doors in the three blocks around a walking guide. */
    private static void openAround(ServerLevel level, Entity npc, double x, double y, double z,
            long now) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!Doors368.isDoor(level.getBlockState(pos))) continue;
                    double distance = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                            pos.getZ() + 0.5D);
                    if (distance > REACH * REACH) continue;
                    if (Doors368.setOpen(level, pos, true)) {
                        OPENED.add(new Open(level, pos, now));
                    }
                }
            }
        }
    }

    /**
     * Closes the doors the guides opened and left. A door is closed when no guide stands next to it any
     * more, or when it has been open longer than {@link #CLOSE_AFTER} ticks - a guide that died in a
     * doorway must not leave the door open for ever. A door somebody else closed in the meantime is not
     * touched twice, and a door the author locked is closed again just like any other: the lock is not
     * a state of the door, it is a rule about who may open it.
     */
    private static void closeLeftBehind(long now) {
        if (OPENED.isEmpty()) return;
        for (int i = OPENED.size() - 1; i >= 0; i--) {
            Open open = OPENED.get(i);
            long age = now - open.at;
            boolean stale = age > CLOSE_AFTER || age < 0L;
            if (!stale && guidedNear(open.level, open.pos)) continue;
            OPENED.remove(i);
            if (Doors368.anyOpen(open.level, open.pos)) {
                Doors368.setOpen(open.level, open.pos, false);
            }
        }
        if (OPENED.size() > 512) OPENED.clear();
    }

    /** True when a guide is still walking through that doorway. */
    private static boolean guidedNear(ServerLevel level, BlockPos pos) {
        Iterable<Entity> bodies = level.getAllEntities();
        if (bodies == null) return false;
        for (Entity body : bodies) {
            if (!(body instanceof dev.settledlands.npc.GuideNpc)) continue;
            if (body.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    <= CLOSE_RANGE * CLOSE_RANGE) {
                return true;
            }
        }
        return false;
    }

    /** How many doors a guide has open right now - a probe, not a rule. */
    public static int openDoors() {
        return OPENED.size();
    }
}
