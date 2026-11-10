package dev.settledlands.npc;

import dev.settledlands.door.DoorSync375;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Build 417: «Незаконно» toggle on door and chest lockpicking cards.
 *
 * <p>When a door card ({@code DoorGraph382}) or chest card ({@code ChestLock397}) has its «Незаконно»
 * flag enabled ({@code Node.varLocal == true}):</p>
 * <ul>
 *   <li>If any live SettledLands NPC ({@link GuideNpc}, excluding {@link GuideTriggerNpc} and
 *       {@link GuideCorpseNpc}) already sees the player at click time (via Hackers 'n Slashers sight
 *       perception + unobstructed line of sight, or directional cone + line of sight when Hackers 'n
 *       Slashers is absent), lockpicking is refused before {@code HackPanel377} even opens and the
 *       player receives «За вами наблюдают» ({@link #MSG_WATCHED}).</li>
 *   <li>If lockpicking begins unseen and a live {@link GuideNpc} spots the player while the lockpick
 *       panel is open, the active session is immediately aborted, {@code HackPanel377} closes on the
 *       client, and «За вами наблюдают» is shown.</li>
 * </ul>
 */
public final class NpcIllegalHack417 {

    public static final String MSG_WATCHED = "message.settledlands.hack.watched";
    public static final String ILLEGAL_KEY = "gui.settledlands.hack.illegal";
    public static final String ILLEGAL_ON = "gui.settledlands.hack.illegal_on";
    public static final String ILLEGAL_OFF = "gui.settledlands.hack.illegal_off";

    /** Maximum witness scan distance around the lockpicking player (matches HnS mob vision range). */
    public static final double WITNESS_RANGE = 24.0D;
    public static final double WITNESS_RANGE_SQR = WITNESS_RANGE * WITNESS_RANGE;

    /** Fallback witness sight range when Hackers 'n Slashers is not installed / disabled. */
    public static final double FALLBACK_RANGE = 16.0D;
    public static final double FALLBACK_SNEAK_RANGE = 8.0D;
    public static final double PERSONAL_SPACE_SQR = 3.0D;
    public static final float FALLBACK_HALF_FOV_DEG = 60.0F;

    /** Max distance squared before an active lockpick session is considered abandoned (7 blocks). */
    public static final double SESSION_REACH_SQR = 49.0D;
    public static final long MAX_SESSION_MS = 300_000L;

    static final class Session {
        final ServerLevel level;
        final BlockPos pos;
        final boolean chest;
        final String key;
        final long startedAtMs;

        Session(ServerLevel level, BlockPos pos, boolean chest, String key, long startedAtMs) {
            this.level = level;
            this.pos = pos;
            this.chest = chest;
            this.key = key;
            this.startedAtMs = startedAtMs;
        }
    }

    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<UUID, Session>();

    private NpcIllegalHack417() {}

    /**
     * Returns true when {@code npc} is a live sighted SettledLands NPC that currently perceives
     * {@code player} visually (unobstructed line of sight + Hackers 'n Slashers stealth perception).
     */
    public static boolean doesNpcWatchPlayer(GuideNpc npc, Player player) {
        if (npc == null || player == null) {
            return false;
        }
        if (NpcHnsStealth416.isBlindNpc(npc)) {
            return false;
        }
        if (!npc.isAlive() || npc.isRemoved()) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (NpcHnsStealth416.isPlayerBodiless(player)) {
            return false;
        }
        double distSq = npc.distanceToSqr(player);
        if (distSq > WITNESS_RANGE_SQR) {
            return false;
        }

        boolean hnsActive = NpcHnsStealth416.isHnsStealthActive();
        boolean trueVision = hnsActive && NpcHnsStealth416.hasTrueVision(npc);

        if (player.isInvisible() && !trueVision) {
            return false;
        }
        if (!npc.hasLineOfSight(player)) {
            return false;
        }
        if (npc.getTarget() == player) {
            return true;
        }

        if (hnsActive && !trueVision) {
            double sight = NpcHnsStealth416.getSightLevel(player, npc);
            double threshold = NpcHnsStealth416.isTrackingPlayer(npc, player)
                    ? NpcHnsStealth416.RETAIN_SIGHT_THRESHOLD
                    : NpcHnsStealth416.ACQUIRE_SIGHT_THRESHOLD;
            return sight >= threshold;
        }

        if (!hnsActive) {
            double maxRange = player.isShiftKeyDown() ? FALLBACK_SNEAK_RANGE : FALLBACK_RANGE;
            if (distSq > maxRange * maxRange) {
                return false;
            }
            if (distSq <= PERSONAL_SPACE_SQR) {
                return true;
            }
            double dx = player.getX() - npc.getX();
            double dz = player.getZ() - npc.getZ();
            float angleToPlayer = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            float delta = Math.abs(wrapDegrees(angleToPlayer - npc.yHeadRot));
            return delta <= FALLBACK_HALF_FOV_DEG;
        }

        return true;
    }

    private static float wrapDegrees(float deg) {
        float d = deg % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }

    /**
     * Scans nearby live {@link GuideNpc} entities in {@code level} and returns true if at least one
     * witness currently perceives {@code player}.
     */
    public static boolean isPlayerWatched(ServerLevel level, Player player) {
        if (level == null || player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        AABB box = new AABB(
                px - WITNESS_RANGE, py - 12.0D, pz - WITNESS_RANGE,
                px + WITNESS_RANGE, py + 12.0D, pz + WITNESS_RANGE);
        List<GuideNpc> npcs = level.getEntitiesOfClass(
                GuideNpc.class,
                box,
                n -> n != null && n.isAlive() && !n.isRemoved() && !NpcHnsStealth416.isBlindNpc(n));
        if (npcs == null || npcs.isEmpty()) {
            return false;
        }
        for (int i = 0, size = npcs.size(); i < size; i++) {
            if (doesNpcWatchPlayer(npcs.get(i), player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called on the server when {@code player} clicks a locked door or chest whose card is marked
     * «Незаконно». Returns {@code true} if the player is unseen and the lockpick panel may open;
     * returns {@code false} and shows «За вами наблюдают» if an NPC already sees the player.
     */
    public static boolean beginIllegalHack(
            ServerPlayer player, ServerLevel level, BlockPos pos, boolean chest, String key) {
        if (player == null || level == null || pos == null) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (isPlayerWatched(level, player)) {
            if (uuid != null) {
                ACTIVE.remove(uuid);
            }
            notifyWatched(player);
            return false;
        }
        if (uuid != null) {
            ACTIVE.put(uuid, new Session(
                    level,
                    new BlockPos(pos.getX(), pos.getY(), pos.getZ()),
                    chest,
                    key == null ? "" : key,
                    System.currentTimeMillis()));
        }
        return true;
    }

    /** Clears any active illegal lockpick session for {@code player} (on solve, ESC close, or abort). */
    public static void endSession(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (uuid != null) {
            ACTIVE.remove(uuid);
        }
    }

    /**
     * Final server guard right before unlocking a solved door or chest: ends any active session and,
     * if {@code illegal} is enabled and an NPC sees the player right now, aborts and refuses the solve.
     */
    public static boolean checkBeforeComplete(
            ServerPlayer player, ServerLevel level, BlockPos pos, boolean illegal) {
        endSession(player);
        if (!illegal) {
            return true;
        }
        if (player == null || level == null || pos == null) {
            return false;
        }
        if (isPlayerWatched(level, player)) {
            abortWatchedHack(player);
            return false;
        }
        return true;
    }

    /**
     * Fast per-tick witness check invoked from {@code GuideNpc#customServerAiStep()}. Costs a single
     * empty-map check when nobody is picking an illegal lock.
     */
    public static void onNpcServerTick(GuideNpc npc) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (npc == null || NpcHnsStealth416.isBlindNpc(npc) || !npc.isAlive() || npc.isRemoved()) {
            return;
        }
        if (!(npc.level() instanceof ServerLevel npcLevel)) {
            return;
        }
        MinecraftServer server = npcLevel.getServer();
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Session>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session session = entry.getValue();
            if (session == null || now - session.startedAtMs > MAX_SESSION_MS) {
                it.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null
                    || !player.isAlive()
                    || player.isSpectator()
                    || player.level() != session.level
                    || !nearBlock(player, session.pos)) {
                it.remove();
                continue;
            }
            if (player.level() != npcLevel) {
                continue;
            }
            if (doesNpcWatchPlayer(npc, player)) {
                it.remove();
                abortWatchedHack(player);
            }
        }
    }

    public static void notifyWatched(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.displayClientMessage(Component.translatable(MSG_WATCHED), true);
        } catch (Throwable ignored) {
        }
    }

    public static void abortWatchedHack(ServerPlayer player) {
        if (player == null) {
            return;
        }
        DoorSync375.sendAbortHack(player);
        notifyWatched(player);
    }

    private static boolean nearBlock(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= SESSION_REACH_SQR;
    }

    /** Test/probe helper: number of currently tracked illegal lockpick sessions. */
    public static int activeSessionCount() {
        return ACTIVE.size();
    }

    /** Test/probe helper: clears all tracked sessions. */
    public static void clearSessionsForTests() {
        ACTIVE.clear();
    }
}
