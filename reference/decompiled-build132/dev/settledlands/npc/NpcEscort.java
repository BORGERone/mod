package dev.settledlands.npc;

import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/** Server-only escort state. Created lazily; inactive NPCs have no controller/tick work. */
public final class NpcEscort {
    public static final int WALKING = 0, WAIT_PLAYER = 1, ARRIVAL_DIALOGUE = 2, FINISH_WAIT = 3;
    private static final int PATHS_PER_SERVER_TICK = 2;
    private static final WeakHashMap<MinecraftServer, PathBudget> BUDGETS = new WeakHashMap<>();
    private final UUID owner;
    private final String route;
    private final BlockPos destination;
    private int phase;
    private int finishTicks;
    private int repathTicks;
    private int failures;
    private int progressTicks;
    private double progressX, progressY, progressZ;
    private boolean waitingForOwner;
    private boolean catchingUp;
    private boolean hasPlayerPathSnapshot;
    private double pathPlayerX, pathPlayerY, pathPlayerZ;
    private boolean attemptedPathInWindow;

    private NpcEscort(UUID owner, String route, BlockPos destination) {
        this.owner = owner;
        this.route = route;
        this.destination = destination.immutable();
    }

    public UUID owner() { return owner; }
    public String route() { return route; }
    public BlockPos destination() { return destination; }
    public int phase() { return phase; }
    public boolean isCatchingUp() { return catchingUp; }
    public boolean belongsTo(ServerPlayer player) { return player != null && owner.equals(player.getUUID()); }

    public static NpcEscort begin(GuideNpc npc, ServerPlayer player, String route, BlockPos target) {
        NpcEscort state = new NpcEscort(player.getUUID(), route, target);
        state.repathTicks = npc.getId() & 15; // Spread simultaneous starts over server ticks.
        state.rememberProgress(npc);
        return state;
    }

    public static BlockPos parseCoordinates(String value, int minY, int maxY) {
        if (value == null || value.length() > NpcProfile.MAX_MOBILITY_COORDINATES || value.isBlank()) return null;
        String[] parts = value.trim().split("[,\\s]+");
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            if (x < -29999984 || x > 29999984 || z < -29999984 || z > 29999984 || y < minY || y >= maxY) return null;
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** O(1) owner lookup, arithmetic and counters. No global entity/player scans. */
    public void tick(GuideNpc npc) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        // Once the arrival conversation ends, the five-second wait no longer needs the owner online.
        if (phase == FINISH_WAIT) {
            stopMotion(npc);
            if (--finishTicks <= 0) npc.finishEscort();
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level || !player.isAlive() || player.isSpectator() || player.isRemoved()) {
            stopMotion(npc);
            if (phase == ARRIVAL_DIALOGUE) {
                phase = FINISH_WAIT;
                finishTicks = EscortRules.FINISH_WAIT_TICKS;
            }
            return; // Travel/wait-for-player pauses; never force-loads or teleports.
        }
        if (phase == ARRIVAL_DIALOGUE) {
            stopMotion(npc);
            if (!GuideDialogue.hasEscortArrival(player, npc)) dialogueClosed(player, true);
            return;
        }
        if (phase == WAIT_PLAYER) {
            stopMotion(npc);
            if (player.distanceToSqr(npc) <= EscortRules.ARRIVAL_PLAYER_DISTANCE_SQ
                && !GuideDialogue.isInDialogue(player)
                && player.containerMenu == player.inventoryMenu) {
                openArrival(npc, player);
            }
            return;
        }
        if (EscortRules.atDestination(npc.getX(), npc.getY(), npc.getZ(),
                destination.getX(), destination.getY(), destination.getZ(), npc.getBbWidth())) {
            phase = WAIT_PLAYER;
            catchingUp = false;
            waitingForOwner = false;
            stopMotion(npc);
            return;
        }

        if (GuideDialogue.hasEscortConversation(player, npc)) {
            stopMotion(npc);
            return;
        }
        double playerDistanceSq = player.distanceToSqr(npc);
        boolean catchUp = EscortRules.shouldCatchUp(playerDistanceSq,
            distanceToDestinationSq(player.getX(), player.getY(), player.getZ()),
            distanceToDestinationSq(npc.getX(), npc.getY(), npc.getZ()), catchingUp);
        if (catchUp != catchingUp) {
            catchingUp = catchUp;
            waitingForOwner = false;
            stopMotion(npc); // Never continue a stale path toward the old target.
            repathTicks = 0; // A mode transition requests the new target immediately, within the shared budget.
            failures = 0;
            hasPlayerPathSnapshot = false;
            rememberProgress(npc);
        }
        if (!catchingUp) {
            waitingForOwner = EscortRules.shouldWait(playerDistanceSq, waitingForOwner);
            if (waitingForOwner) {
                stopMotion(npc);
                return;
            }
        }

        PathNavigation navigation = npc.getNavigation();
        double speed = npc.walkingNavigationSpeed(catchingUp);
        navigation.setSpeedModifier(speed); // Cheap setter, not a new pathfinding request.
        if (++progressTicks >= 100) {
            double dx = npc.getX() - progressX, dy = npc.getY() - progressY, dz = npc.getZ() - progressZ;
            if (attemptedPathInWindow && dx * dx + dy * dy + dz * dz < 0.25) {
                stopMotion(npc);
                failures = Math.min(failures + 1, 10);
                repathTicks = EscortRules.retryTicks(failures);
            }
            rememberProgress(npc);
        }
        if (repathTicks > 0) repathTicks--;
        if (repathTicks > 0) return;
        if (!navigation.isDone() && (!catchingUp || !playerPathTargetMoved(player))) return;
        if (!acquirePathBudget(level.getServer())) return;

        BlockPos waypoint = nextWaypoint(npc, level, player);
        attemptedPathInWindow = true;
        if (catchingUp) {
            pathPlayerX = player.getX(); pathPlayerY = player.getY(); pathPlayerZ = player.getZ();
            hasPlayerPathSnapshot = true;
        }
        if (waypoint != null && navigation.moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 0, speed)) {
            // A partial path that reports success is not proof that the NPC actually moved.
            // Keep both its detour and backoff until real horizontal progress has been made.
            double dx = npc.getX() - progressX, dz = npc.getZ() - progressZ;
            if (dx * dx + dz * dz >= 1.0) failures = 0;
            repathTicks = EscortRules.retryTicks(failures);
        } else {
            failures = Math.min(failures + 1, 10);
            repathTicks = EscortRules.retryTicks(failures);
        }
    }

    public boolean openArrival(GuideNpc npc, ServerPlayer player) {
        if (phase != WAIT_PLAYER || !belongsTo(player)
            || player.level() != npc.level() || player.distanceToSqr(npc) > EscortRules.ARRIVAL_PLAYER_DISTANCE_SQ) return false;
        phase = ARRIVAL_DIALOGUE;
        GuideDialogue.closeNpcDialogues(npc); // Bound player has priority over bystander travel lines.
        stopMotion(npc);
        GuideDialogue.startEscort(player, npc, true);
        return true;
    }

    public void dialogueClosed(ServerPlayer player, boolean arrival) {
        if (arrival && phase == ARRIVAL_DIALOGUE && belongsTo(player)) {
            phase = FINISH_WAIT;
            finishTicks = EscortRules.FINISH_WAIT_TICKS;
        }
    }

    private double distanceToDestinationSq(double x, double y, double z) {
        double dx = x - (destination.getX() + 0.5), dy = y - destination.getY(), dz = z - (destination.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean playerPathTargetMoved(ServerPlayer player) {
        if (!hasPlayerPathSnapshot) return true;
        double dx = player.getX() - pathPlayerX, dy = player.getY() - pathPlayerY, dz = player.getZ() - pathPlayerZ;
        return dx * dx + dy * dy + dz * dz >= EscortRules.CATCH_UP_TARGET_MOVED_SQ;
    }

    private BlockPos nextWaypoint(GuideNpc npc, ServerLevel level, ServerPlayer player) {
        BlockPos target = catchingUp ? BlockPos.containing(player.getX(), player.getY(), player.getZ()) : destination;
        double goalX = catchingUp ? player.getX() : destination.getX() + 0.5;
        double goalZ = catchingUp ? player.getZ() : destination.getZ() + 0.5;
        double dx = goalX - npc.getX(), dz = goalZ - npc.getZ();
        double horizontalSq = dx * dx + dz * dz;
        if (failures == 0 && horizontalSq <= 24.0 * 24.0 && Math.abs(target.getY() - npc.getY()) <= 8) {
            return chunkLoaded(level, target) ? target : null;
        }
        double distance = Math.sqrt(horizontalSq);
        double factor = distance > EscortRules.WAYPOINT_DISTANCE ? EscortRules.WAYPOINT_DISTANCE / distance : 1.0;
        double x = npc.getX() + dx * factor, z = npc.getZ() + dz * factor;
        // A deterministic lateral retry helps a straight-line waypoint bypass obstacles.
        if (distance > 0.01 && failures > 0) {
            int detour = failures % 5;
            double side = switch (detour) { case 1 -> 4; case 2 -> -4; case 3 -> 8; case 4 -> -8; default -> 0; };
            x -= dz / distance * side;
            z += dx / distance * side;
        }
        int y = (int)Math.floor(npc.getY()) + Math.max(-4, Math.min(4, target.getY() - (int)Math.floor(npc.getY())));
        BlockPos waypoint = BlockPos.containing(x, y, z);
        if (!chunkLoaded(level, waypoint)) return null;
        // At most nine local block checks, only when a bounded replan is permitted.
        for (int step = 0; step <= 8; step++) {
            int offset = step == 0 ? 0 : (step % 2 == 1 ? (step + 1) / 2 : -step / 2);
            BlockPos candidate = waypoint.offset(0, offset, 0);
            if (candidate.getY() >= level.getMinBuildHeight() && candidate.getY() < level.getMaxBuildHeight()
                && npc.getNavigation().isStableDestination(candidate)) return candidate;
        }
        return waypoint;
    }

    private static boolean chunkLoaded(ServerLevel level, BlockPos position) {
        // getChunkNow is a non-loading lookup of an already available full chunk.
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private void rememberProgress(GuideNpc npc) {
        progressX = npc.getX(); progressY = npc.getY(); progressZ = npc.getZ(); progressTicks = 0;
        attemptedPathInWindow = false;
    }

    static void stopMotion(GuideNpc npc) {
        if (!npc.getNavigation().isDone()) npc.getNavigation().stop();
        npc.getMoveControl().setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0.0);
        npc.xxa = 0.0F; npc.yya = 0.0F; npc.zza = 0.0F;
        Vec3 movement = npc.getDeltaMovement();
        if (movement.x != 0.0 || movement.z != 0.0) npc.setDeltaMovement(0.0, movement.y, 0.0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner);
        tag.putString("Route", route);
        tag.putInt("X", destination.getX()); tag.putInt("Y", destination.getY()); tag.putInt("Z", destination.getZ());
        tag.putInt("Phase", phase); tag.putInt("FinishTicks", finishTicks);
        tag.putBoolean("CatchingUp", catchingUp);
        return tag;
    }

    public static NpcEscort load(CompoundTag tag, int minY, int maxY) {
        if (tag == null || !tag.hasUUID("Owner") || !EscortRules.validCode(tag.getString("Route"))) return null;
        int x = tag.getInt("X"), y = tag.getInt("Y"), z = tag.getInt("Z");
        if (x < -29999984 || x > 29999984 || z < -29999984 || z > 29999984 || y < minY || y >= maxY) return null;
        int phase = tag.getInt("Phase");
        if (phase < WALKING || phase > FINISH_WAIT) return null;
        NpcEscort state = new NpcEscort(tag.getUUID("Owner"), tag.getString("Route"), new BlockPos(x, y, z));
        // A GUI/session cannot survive reload. Safely offer the arrival line again.
        state.phase = phase == ARRIVAL_DIALOGUE ? WAIT_PLAYER : phase;
        state.catchingUp = state.phase == WALKING && tag.getBoolean("CatchingUp");
        state.finishTicks = Math.max(1, Math.min(EscortRules.FINISH_WAIT_TICKS, tag.getInt("FinishTicks")));
        state.repathTicks = EscortRules.REPATH_TICKS;
        return state;
    }

    private static boolean acquirePathBudget(MinecraftServer server) {
        PathBudget budget = BUDGETS.computeIfAbsent(server, ignored -> new PathBudget());
        int tick = server.getTickCount();
        if (budget.tick != tick) { budget.tick = tick; budget.used = 0; }
        if (budget.used >= PATHS_PER_SERVER_TICK) return false;
        budget.used++;
        return true;
    }

    private static final class PathBudget { int tick = -1, used; }
}
