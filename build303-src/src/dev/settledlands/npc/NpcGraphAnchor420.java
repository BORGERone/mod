package dev.settledlands.npc;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestGraph396Store;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.door.DoorStore368;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 420: the anchor - a marker that carries a build's graphs inside a blueprint.
 *
 * <p>The rule of the anchor is in {@link NpcGraphAnchorCore420}, the four world files in
 * {@link NpcGraphAnchorFiles420}. This class is the part that has a world: it keeps one marker per
 * building, keeps that marker's NBT equal to the graphs standing around it, and puts those graphs back
 * when the marker turns up somewhere else.</p>
 *
 * <p><b>Where the marker comes from.</b> No new block and no new entity: the anchor is the invisible
 * trigger the base mod already ships ({@code settledlands:guide_trigger}), found by the tag it carries and
 * not by a name - so a copy tool that renames entities, or a paste that gives it a new UUID, changes
 * nothing. A build needs one marker, and the pass below puts one there by itself: an author never has to
 * place anything, which is what "automatic" was asked to mean.</p>
 *
 * <p><b>What the pass does, every two seconds.</b></p>
 * <ol>
 *   <li>nothing while no player is online - a server with nobody in it has no author to serve;</li>
 *   <li>every marker with graphs near a player is <b>refreshed</b>: its list is rebuilt from the four
 *       files around it, so what a blueprint would carry is what the author built, not what was there
 *       when the marker appeared;</li>
 *   <li>a place with graphs near a player and no marker within
 *       {@link #ANCHOR_SEARCH_BLOCKS} gets a marker in the nearest free cell, so a build that was made
 *       before this build is covered as well;</li>
 *   <li>a marker whose stored place is not its own is a marker that was <b>pasted</b>: its entries are
 *       written into the four files at the new place and its stamp is moved there. The rotation is
 *       decided by the fingerprint, never by the marker's yaw (see
 *       {@link NpcGraphAnchorCore420#bestTurn}).</li>
 * </ol>
 *
 * <p>Every one of those four is a few list walks over data the mod already keeps in memory; nothing here
 * reads a chunk, and a world with no graphs and no markers costs one comparison per pass.</p>
 */
public final class NpcGraphAnchor420 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/Anchor420");

    /** The pass runs every this many ticks - twice a second, the pace the radius pass already uses. */
    public static final int PERIOD = 40;

    /** How far from a player a marker or a building is looked for. */
    public static final double PLAYER_BLOCKS = 48.0D;

    /** How far from a marker the graphs of its building are gathered, in blocks. */
    public static final int GATHER_BLOCKS = 32;

    /** How far from a graph another marker counts as "this building already has one". */
    public static final int ANCHOR_SEARCH_BLOCKS = 32;

    /** How many blocks around the marker a fingerprint remembers - a 9³ cube, air left out. */
    public static final int FINGERPRINT_BLOCKS = 4;

    /**
     * Build 422: how far from a pasted marker's own cell its build may really stand, sideways and up.
     *
     * <p>A pasted marker can land a fraction of a block low, and a marker is a mob that can be nudged a
     * cell sideways; three cells each way covers both without letting the search wander off the build.</p>
     */
    public static final int PLACE_SEARCH_BLOCKS = 5;

    /** Build 422: how far up and down the pasted build may really stand from the marker's own cell. */
    public static final int PLACE_SEARCH_UP = 3;

    /** Where the marker already put its graphs: {@code [x, y, z, turn]} - written to the entity's NBT. */
    private static final String STAMP = "SettledLandsAnchorAt";

    /** Build 437: the dimension id where {@link #STAMP} was recorded, preventing cross-dimension (0,0,0) collisions. */
    private static final String STAMP_DIM = "SettledLandsAnchorDim";

    private static final Set<String> WARNED = new java.util.HashSet<String>();

    private NpcGraphAnchor420() {
    }

    // ---- the pass -----------------------------------------------------------

    /**
     * One pass of every level. Called from {@link NpcGraphAnchorEvents420} on the server tick.
     *
     * @return how many markers were refreshed, created or materialised - for the log and the gate
     */
    public static int pass(MinecraftServer server) {
        if (server == null) return 0;
        int work = 0;
        try {
            List<? extends ServerPlayer> players = server.getPlayerList() == null
                    ? new ArrayList<ServerPlayer>() : server.getPlayerList().getPlayers();
            if (players == null || players.isEmpty()) return 0;
            for (ServerLevel level : server.getAllLevels()) {
                if (level == null) continue;
                work += passLevel(level, players);
            }
        } catch (Throwable failure) {
            warn("anchor pass skipped: " + failure);
        }
        return work;
    }

    /**
     * Build 438: runs the anchor pass immediately around one player (e.g. right after teleporting into
     * {@code settledlands:hell_dungeon}) so start-room anchors never wait up to 40 ticks.
     */
    public static int ensureForPlayer(ServerPlayer player) {
        if (player == null) return 0;
        try {
            ServerLevel level = player.serverLevel();
            if (level == null) return 0;
            return passLevel(level, Collections.singletonList(player));
        } catch (Throwable failure) {
            warn("anchor: player entry pass skipped: " + failure);
            return 0;
        }
    }

    private static int passLevel(ServerLevel level, List<? extends ServerPlayer> players) {
        int work = 0;
        // Build 438: import chest/furniture graphs carried inside BlockEntity NeoForgeData
        // (e.g. from Axiom pastes with copyWithEntities=false) before ensuring anchors.
        try {
            work += ChestGraph396.syncEmbeddedGraphsNear(level, players);
        } catch (Throwable failure) {
            warn("anchor: embedded chest sync skipped: " + failure);
        }
        List<Entity> markers = markersNear(level, players);
        for (Entity marker : markers) {
            try {
                if (materialiseIfMoved(level, marker)) work++;
            } catch (Throwable failure) {
                warn("anchor: a marker could not be materialised: " + failure);
            }
        }
        // Refreshing is cheap but not free: it walks the graphs of one dimension. Once per pass per level
        // is plenty - the author's own save is what changes them.
        for (Entity marker : markers) {
            try {
                if (refresh(level, marker)) work++;
            } catch (Throwable failure) {
                warn("anchor: a marker could not be refreshed: " + failure);
            }
        }
        work += ensureAnchors(level, players, markers);
        return work;
    }

    /** Every marker carrying graphs within {@link #PLAYER_BLOCKS} of a player, each once. */
    private static List<Entity> markersNear(ServerLevel level, List<? extends ServerPlayer> players) {
        List<Entity> out = new ArrayList<Entity>();
        for (ServerPlayer player : players) {
            if (player == null || player.level() != level) continue;
            AABB box = new AABB(player.getX() - PLAYER_BLOCKS, player.getY() - PLAYER_BLOCKS,
                    player.getZ() - PLAYER_BLOCKS, player.getX() + PLAYER_BLOCKS,
                    player.getY() + PLAYER_BLOCKS, player.getZ() + PLAYER_BLOCKS);
            List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                    entity -> entity != null && !entity.isRemoved()
                            && NpcGraphAnchorCore420.hasGraphs(data(entity)));
            if (found == null) continue;
            for (Entity entity : found) {
                if (entity != null && !out.contains(entity)) out.add(entity);
            }
        }
        return out;
    }

    /**
     * Puts a marker next to a building that has graphs and none.
     *
     * <p>The marker sits in the nearest free cell of the build, not on top of it: a marker inside a wall
     * is a marker a paste would lose. A place whose every cell is taken is skipped with one line in the
     * log - an anchor is a convenience, never a blocker.</p>
     */
    private static int ensureAnchors(ServerLevel level, List<? extends ServerPlayer> players,
            List<Entity> markers) {
        List<BlockPos> graphPlaces = graphPlaces(level, players);
        if (graphPlaces.isEmpty()) return 0;
        int made = 0;
        for (BlockPos place : graphPlaces) {
            if (made >= 8) break;
            if (coveringMarker(level, markers, place) != null) continue;
            BlockPos spot = freeSpot(level, place);
            if (spot == null) {
                warn("anchor: no free cell near " + place + " - the build keeps its graphs in the world "
                        + "only, a blueprint will not carry them");
                continue;
            }
            Entity marker = spawn(level, spot);
            if (marker == null) continue;
            made++;
            seal(level, marker, spot);
            if (!markers.contains(marker)) markers.add(marker);
        }
        return made;
    }

    /**
     * Build 437: gathers places from all four stores (block triggers, door graphs, chest/furniture
     * graphs, and door locks) within {@link #PLAYER_BLOCKS} of a player on {@code level}.
     */
    private static List<BlockPos> graphPlaces(ServerLevel level, List<? extends ServerPlayer> players) {
        List<BlockPos> out = new ArrayList<BlockPos>();
        Set<BlockPos> seen = new HashSet<BlockPos>();
        String dimension = BlockTriggerStore351.dimensionOf(level);
        MinecraftServer server = level.getServer();
        if (server == null) return out;

        BlockTriggerStore351 blocks = blockStore(server);
        if (blocks != null) {
            for (BlockTriggerStore351.Entry entry : blocks.ofDimension(dimension)) {
                if (entry == null || entry.graph == null) continue;
                BlockPos place = new BlockPos(entry.x, entry.y, entry.z);
                if (near(level, place, players) && seen.add(place)) out.add(place);
            }
        }

        DoorGraph382Store doors = doorStore(server);
        if (doors != null) {
            for (String key : doors.ofDimension(dimension)) {
                if (doors.graph(key) == null) continue;
                BlockPos place = DoorStore368.positionOf(key);
                if (place != null && near(level, place, players) && seen.add(place)) out.add(place);
            }
        }

        ChestGraph396Store chests = chestStore(server);
        if (chests != null) {
            for (String key : chests.ofDimension(dimension)) {
                if (chests.graph(key) == null) continue;
                BlockPos place = ChestGraph396Store.positionOf(key);
                if (place != null && near(level, place, players) && seen.add(place)) out.add(place);
            }
        }

        DoorStore368 locks = lockStore(server);
        if (locks != null) {
            for (String key : locks.snapshot()) {
                if (!dimension.equals(DoorStore368.dimensionOf(key))) continue;
                BlockPos place = DoorStore368.positionOf(key);
                if (place == null) continue;
                if (locks.isLocked(level, place.offset(0, -1, 0))) continue;
                if (near(level, place, players) && seen.add(place)) out.add(place);
            }
        }
        return out;
    }

    private static boolean near(ServerLevel level, BlockPos place, List<? extends ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player == null || player.level() != level) continue;
            double dx = place.getX() + 0.5D - player.getX();
            double dy = place.getY() + 0.5D - player.getY();
            double dz = place.getZ() + 0.5D - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= PLAYER_BLOCKS * PLAYER_BLOCKS) return true;
        }
        return false;
    }

    private static Entity coveringMarker(ServerLevel level, List<Entity> markers, BlockPos place) {
        String currentDim = BlockTriggerStore351.dimensionOf(level);
        for (Entity marker : markers) {
            if (marker == null || marker.isRemoved()) continue;
            if (!NpcGraphAnchorCore420.hasGraphs(data(marker))) continue;
            BlockPos anchorPos = stampedOrCell(currentDim, marker);
            if (NpcGraphAnchorCore420.inside(place.getX(), place.getY(), place.getZ(),
                    anchorPos, GATHER_BLOCKS)) {
                return marker;
            }
        }
        return null;
    }

    private static BlockPos stampedOrCell(String currentDim, Entity marker) {
        CompoundTag tag = data(marker);
        int[] were = tag.getIntArray(STAMP);
        String dim = tag.getString(STAMP_DIM);
        if (were != null && were.length >= 3
                && (dim == null || dim.isEmpty() || dim.equals(currentDim))) {
            return new BlockPos(were[0], were[1], were[2]);
        }
        return cell(marker);
    }

    /**
     * A free cell for the marker: scores adjacent cells by indoor enclosure (ceiling overhead, solid
     * floor beneath, and enclosing walls) so markers always spawn inside the room in front of
     * wall-mounted shelves rather than outside exterior walls where Axiom selections omit them.
     */
    public static BlockPos freeSpot(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return null;
        if (empty(level, place)) return place;

        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;

        // Radius 1..2 search around the target block (y from -1 to +2 relative to block).
        for (int r = 1; r <= 2; r++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r && r > 1) continue;
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos at = place.offset(dx, dy, dz);
                        if (!empty(level, at)) continue;
                        int score = interiorScore(level, at, dx, dy, dz);
                        if (score > bestScore) {
                            bestScore = score;
                            best = at;
                        }
                    }
                }
            }
            // If radius 1 already yielded a solid indoor spot (ceiling + floor + walls), stop here.
            if (best != null && bestScore >= 280) {
                return best;
            }
        }
        if (best != null) return best;

        for (int dy = 2; dy <= 3; dy++) {
            BlockPos up = place.offset(0, dy, 0);
            if (empty(level, up)) return up;
        }
        return null;
    }

    /**
     * Scores a free cell for indoor room enclosure: ceiling overhead (+200), floor support (+80),
     * enclosed cardinal walls up to 6 blocks out (+15 each), adjacent solid neighbours (+3 each),
     * same-elevation preference, minus distance penalty.
     */
    private static int interiorScore(ServerLevel level, BlockPos at, int dx, int dy, int dz) {
        boolean floor = !empty(level, at.offset(0, -1, 0)) || !empty(level, at.offset(0, -2, 0));
        boolean ceiling = false;
        for (int cy = 1; cy <= 8; cy++) {
            if (!empty(level, at.offset(0, cy, 0))) {
                ceiling = true;
                break;
            }
        }
        int wallHits = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : dirs) {
            for (int step = 1; step <= 6; step++) {
                if (!empty(level, at.offset(dir[0] * step, 0, dir[1] * step))
                        || !empty(level, at.offset(dir[0] * step, 1, dir[1] * step))) {
                    wallHits++;
                    break;
                }
            }
        }
        int adj = 0;
        int[][] adjDirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : adjDirs) {
            if (!empty(level, at.offset(d[0], d[1], d[2]))) adj++;
        }
        int distSq = dx * dx + dy * dy + dz * dz;
        int elevBonus = dy == 0 ? 8 : (dy == -1 ? 4 : 0);
        return (ceiling ? 200 : 0)
                + (floor ? 80 : 0)
                + wallHits * 15
                + adj * 3
                + elevBonus
                - distSq * 2;
    }

    private static boolean empty(ServerLevel level, BlockPos pos) {
        try {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            return state == null || state.isAir();
        } catch (Throwable failure) {
            warn("anchor: a cell could not be read: " + failure);
            return false;
        }
    }

    // ---- sealing ------------------------------------------------------------

    /**
     * Writes into one marker the graphs standing around it, and the fingerprint that will let a paste
     * find the right rotation. Safe to call as often as the pass likes: an unchanged building produces
     * the same list, and the entity's NBT is rewritten in place.
     */
    public static boolean seal(ServerLevel level, Entity marker, BlockPos anchor) {
        if (level == null || marker == null || anchor == null) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        List<NpcGraphAnchorCore420.Entry> entries =
                NpcGraphAnchorFiles420.collect(level, server, anchor, GATHER_BLOCKS);
        if (entries.isEmpty()) {
            clear(data(marker));
            return false;
        }
        NpcGraphAnchorCore420.Fingerprint print = fingerprint(level, anchor);
        CompoundTag tag = NpcGraphAnchorCore420.write(entries, print.offsets, print.blocks);
        if (!NpcGraphAnchorCore420.hasGraphs(tag)) {
            clear(data(marker));
            return false;
        }
        copy(tag, data(marker));
        stamp(marker, anchor, NpcGraphAnchorCore420.IDENTITY, level);
        return true;
    }

    /** Forces a seal for the building a player stands in, if any. Used by the interact and the log. */
    public static boolean sealHere(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return false;
        Entity existing = markerNear(level, place);
        if (existing != null && !existing.isRemoved()) {
            if (seal(level, existing, home(level, existing))) return true;
        }
        BlockPos spot = freeSpot(level, place);
        if (spot == null) spot = place;
        Entity marker = spawn(level, spot);
        return marker != null && seal(level, marker, cell(marker));
    }

    /**
     * Build 437: immediately creates or refreshes an anchor marker when a block, chest/furniture,
     * door graph or door lock is saved or removed, so blueprints exported right away always carry it.
     */
    public static boolean anchorForSave(ServerLevel level, BlockPos pos, boolean hasGraph) {
        if (level == null || pos == null) return false;
        try {
            int reach = GATHER_BLOCKS + PLACE_SEARCH_BLOCKS;
            AABB box = new AABB(pos.getX() - reach, pos.getY() - reach, pos.getZ() - reach,
                    pos.getX() + reach + 1, pos.getY() + reach + 1, pos.getZ() + reach + 1);
            List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                    entity -> entity != null && !entity.isRemoved()
                            && NpcGraphAnchorCore420.hasGraphs(data(entity)));
            Entity best = null;
            double bestDist = Double.MAX_VALUE;
            boolean updatedAny = false;
            if (found != null) {
                for (Entity marker : found) {
                    BlockPos anchorPos = home(level, marker);
                    if (!NpcGraphAnchorCore420.inside(pos.getX(), pos.getY(), pos.getZ(),
                            anchorPos, GATHER_BLOCKS)) {
                        continue;
                    }
                    if (!hasGraph) {
                        updatedAny |= seal(level, marker, anchorPos);
                        continue;
                    }
                    double dx = anchorPos.getX() + 0.5D - (pos.getX() + 0.5D);
                    double dy = anchorPos.getY() - pos.getY();
                    double dz = anchorPos.getZ() + 0.5D - (pos.getZ() + 0.5D);
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = marker;
                    }
                }
            }
            if (!hasGraph) return updatedAny;
            if (best != null) return refresh(level, best);
            BlockPos spot = freeSpot(level, pos);
            if (spot == null) spot = pos;
            Entity marker = spawn(level, spot);
            return marker != null && seal(level, marker, cell(marker));
        } catch (Throwable failure) {
            warn("anchor: save sync skipped: " + failure);
            return false;
        }
    }

    /**
     * The cell an entity's feet are in.
     *
     * <p>Build 422: {@code (int)} truncates towards zero, so a marker at {@code x = -1674.5} - which is
     * where {@link #spawn} puts every marker, half a block past a cell edge - was read as cell
     * {@code -1674} west of the truth, and {@code y = 198.999} as {@code 198}. Both are the neighbouring
     * cell, and a graph written there is a graph nothing reads. Every cell in this class comes from here.</p>
     */
    public static BlockPos cell(Entity entity) {
        if (entity == null) return new BlockPos(0, 0, 0);
        return BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
    }

    /** The fingerprint: the block standing at every cell of a cube around the marker, air left out. */
    static NpcGraphAnchorCore420.Fingerprint fingerprint(ServerLevel level, BlockPos anchor) {
        NpcGraphAnchorCore420.Fingerprint print = new NpcGraphAnchorCore420.Fingerprint();
        int r = FINGERPRINT_BLOCKS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (print.offsets.size() >= NpcGraphAnchorCore420.MAX_FINGERPRINT) return print;
                    String block = NpcGraphAnchorFiles420.blockAt(level, anchor, dx, dy, dz);
                    if (block == null || block.isEmpty()) continue;
                    print.offsets.add(new int[] {dx, dy, dz});
                    print.blocks.add(block);
                }
            }
        }
        return print;
    }

    // ---- pasting ------------------------------------------------------------

    /**
     * If this marker was carried somewhere by a blueprint, put its graphs back and move its stamp. Returns
     * true when the world was written.
     *
     * <p>The rule of the stamp: a marker writes where it put its graphs, with the turn it chose. A marker
     * standing anywhere else has been pasted - or carried - and its graphs belong at the new place.
     * Nothing is written twice: the second pass finds the stamp where it left it.</p>
     */
    public static boolean materialiseIfMoved(ServerLevel level, Entity marker) {
        if (level == null || marker == null) return false;
        CompoundTag data = data(marker);
        if (!NpcGraphAnchorCore420.hasGraphs(data)) return false;
        BlockPos feet = cell(marker);
        NpcGraphAnchorCore420.Fingerprint print = NpcGraphAnchorCore420.fingerprint(data);
        // Build 422: the cell comes from the blocks, not from the marker's own position - an entity's y
        // is a double and a paste that lands a fraction low sits in the cell *under* its build.
        // Build 423: the cell and the arrangement are one question. Searching the cell first and the
        // turn second gives the turn of the wrong cell as soon as the build was pasted turned - which is
        // exactly what the author saw («с поворотами графы съезжают»).
        NpcGraphAnchorCore420.Placement place = NpcGraphAnchorCore420.place(print.offsets, print.blocks,
                (dx, dy, dz) -> NpcGraphAnchorFiles420.blockAt(level, feet, dx, dy, dz),
                PLACE_SEARCH_BLOCKS, PLACE_SEARCH_UP);
        if (!place.found()) {
            // Nothing in reach looks like this marker's building. Writing here would put graphs on a
            // stranger's blocks, so nothing is written and the marker keeps its stamp for a later pass.
            warn("anchor: the marker at " + feet + " does not stand in the building its list was made for"
                    + " (" + place.found + "/" + place.asked + " blocks agree) - nothing was written");
            return false;
        }
        BlockPos here = feet.offset(place.dx, place.dy, place.dz);
        int[] were = data.getIntArray(STAMP);
        String stampedDim = data.getString(STAMP_DIM);
        String currentDim = BlockTriggerStore351.dimensionOf(level);
        boolean dimChanged = stampedDim != null && !stampedDim.isEmpty() && !stampedDim.equals(currentDim);
        boolean moved = dimChanged || were == null || were.length < 3
                || were[0] != here.getX() || were[1] != here.getY() || were[2] != here.getZ();
        if (!moved) return false;
        int turn = place.turn;
        List<NpcGraphAnchorCore420.Entry> entries = NpcGraphAnchorCore420.entries(data);
        NpcGraphAnchorCore420.Result result = NpcGraphAnchorFiles420.materialize(level, level.getServer(),
                entries, turn, here);
        stamp(marker, here, turn, level);
        LOG.info("anchor: pasted at " + here + " (the marker's feet are in " + feet + ", the building was"
                + " found " + place.dx + " " + place.dy + " " + place.dz + " from them, " + place.found
                + "/" + place.asked + " blocks agree, turn " + turn + ") - " + result.line()
                + " (" + entries.size() + " entries carried)");
        return result.total() > 0 || !entries.isEmpty();
    }

    /**
     * Build 436: immediately materialises a freshly spawned dungeon anchor marker and returns the
     * absolute world positions of every chest/furniture graph materialized by it.
     */
    public static List<BlockPos> materialiseForDungeon(ServerLevel level, Entity marker) {
        return materialiseForDungeon(level, marker, -1);
    }

    /**
     * Build 437: immediately materialises a freshly spawned dungeon anchor marker using {@code knownTurns}
     * as a fallback if the fingerprint misses the 60% threshold (e.g. modified start room or remapped
     * marker blocks), and guards against Overworld (0,0,0) coordinate collisions via {@link #STAMP_DIM}.
     */
    public static List<BlockPos> materialiseForDungeon(ServerLevel level, Entity marker, int knownTurns) {
        List<BlockPos> chests = new ArrayList<>();
        if (level == null || marker == null) return chests;
        CompoundTag data = data(marker);
        if (!NpcGraphAnchorCore420.hasGraphs(data)) return chests;
        BlockPos feet = cell(marker);
        NpcGraphAnchorCore420.Fingerprint print = NpcGraphAnchorCore420.fingerprint(data);
        NpcGraphAnchorCore420.BlockLookup lookup =
                (dx, dy, dz) -> NpcGraphAnchorFiles420.blockAt(level, feet, dx, dy, dz);
        NpcGraphAnchorCore420.Placement place = NpcGraphAnchorCore420.place(print.offsets, print.blocks,
                lookup, PLACE_SEARCH_BLOCKS, PLACE_SEARCH_UP);
        boolean fallbackUsed = false;
        if (!place.found()) {
            if (knownTurns >= 0) {
                place = NpcGraphAnchorCore420.placeWithTurn(print.offsets, print.blocks,
                        lookup, knownTurns & 3, PLACE_SEARCH_BLOCKS, PLACE_SEARCH_UP);
                fallbackUsed = true;
                warn("anchor: dungeon marker at " + feet + " missed 60% fingerprint match ("
                        + place.found + "/" + place.asked + "), using room rotation turns="
                        + (knownTurns & 3) + " shift=(" + place.dx + "," + place.dy + "," + place.dz + ")");
            } else {
                warn("anchor: dungeon marker at " + feet + " did not match fingerprint ("
                        + place.found + "/" + place.asked + ")");
                return chests;
            }
        }
        BlockPos here = feet.offset(place.dx, place.dy, place.dz);
        int[] were = data.getIntArray(STAMP);
        String stampedDim = data.getString(STAMP_DIM);
        String currentDim = BlockTriggerStore351.dimensionOf(level);
        boolean sameDungeonSpot = currentDim.equals(stampedDim)
                && were != null && were.length >= 3
                && were[0] == here.getX() && were[1] == here.getY() && were[2] == here.getZ();
        if (sameDungeonSpot) return chests;
        int turn = fallbackUsed && knownTurns >= 0 ? (knownTurns & 3) : place.turn;
        List<NpcGraphAnchorCore420.Entry> entries = NpcGraphAnchorCore420.entries(data);
        NpcGraphAnchorCore420.Result result = NpcGraphAnchorFiles420.materialize(level, level.getServer(),
                entries, turn, here);
        stamp(marker, here, turn, level);
        for (NpcGraphAnchorCore420.Entry entry : entries) {
            if (entry != null && NpcGraphAnchorCore420.CHEST_KIND.equals(entry.kind)) {
                int[] turned = NpcGraphAnchorCore420.turn(new int[] {entry.dx, entry.dy, entry.dz}, turn);
                chests.add(here.offset(turned[0], turned[1], turned[2]));
            }
        }
        LOG.info("anchor: dungeon room materialised at " + here + " (turn " + turn
                + (fallbackUsed ? ", fallback" : "") + ") - "
                + result.line() + " (" + chests.size() + " chest graph(s))");
        return chests;
    }

    // ---- the entity ---------------------------------------------------------

    /** The marker's own NBT - {@code IEntityExtension#getPersistentData}, the call the base jar makes. */
    public static CompoundTag data(Entity entity) {
        if (entity == null) return new CompoundTag();
        try {
            return ((IEntityExtension) entity).getPersistentData();
        } catch (Throwable failure) {
            warn("anchor: an entity has no persistent data: " + failure);
            return new CompoundTag();
        }
    }

    private static void copy(CompoundTag from, CompoundTag to) {
        if (from == null || to == null) return;
        if (from.contains(NpcGraphAnchorCore420.GRAPHS, 9)) {
            to.put(NpcGraphAnchorCore420.GRAPHS, from.getList(NpcGraphAnchorCore420.GRAPHS, 10));
        }
        if (from.contains(NpcGraphAnchorCore420.FP, 9)) {
            to.put(NpcGraphAnchorCore420.FP, from.getList(NpcGraphAnchorCore420.FP, 10));
        }
        to.putInt(NpcGraphAnchorCore420.MARK, NpcGraphAnchorCore420.LAYOUT);
    }

    private static void clear(CompoundTag tag) {
        if (tag == null) return;
        tag.remove(NpcGraphAnchorCore420.GRAPHS);
        tag.remove(NpcGraphAnchorCore420.FP);
        tag.remove(NpcGraphAnchorCore420.MARK);
        tag.remove(STAMP);
        tag.remove(STAMP_DIM);
    }

    private static void stamp(Entity marker, BlockPos at, int turn, ServerLevel level) {
        if (marker == null || at == null) return;
        CompoundTag tag = data(marker);
        tag.putIntArray(STAMP, new int[] {at.getX(), at.getY(), at.getZ(), turn});
        if (level != null) {
            tag.putString(STAMP_DIM, BlockTriggerStore351.dimensionOf(level));
        }
    }

    /** Spawns the anchor marker at a place, or null when the type is not registered in this instance. */
    public static Entity spawn(ServerLevel level, BlockPos at) {
        if (level == null || at == null) return null;
        try {
            net.minecraft.world.entity.EntityType type = markerType();
            if (type == null) {
                warn("anchor: settledlands:guide_trigger is not registered - a build's graphs cannot "
                        + "travel in a blueprint");
                return null;
            }
            Entity marker = type.create(level);
            if (marker == null) return null;
            marker.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
            marker.setYRot(0.0F);
            level.addFreshEntity(marker);
            LOG.info("anchor: a marker was placed at " + at + " - this building's graphs can now travel");
            return marker;
        } catch (Throwable failure) {
            warn("anchor: the marker could not be placed: " + failure);
            return null;
        }
    }

    private static net.minecraft.world.entity.EntityType markerType() {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("settledlands",
                        "guide_trigger");
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    /**
     * Rebuilds one marker's NBT from the world, so a blueprint carries what the author built.
     *
     * <p>Build 422. The graphs are gathered around the cell the marker <b>stamped</b>, not around the cell
     * its feet happen to be in: the two differ whenever a marker drifted a fraction of a block, and
     * gathering around the drifted cell would rewrite the list with every offset moved - undoing the very
     * paste {@link #materialiseIfMoved} had just made. The stamp is checked against the fingerprint before
     * it is trusted, so a marker that was carried somewhere else is still gathered at its new place.</p>
     */
    public static boolean refresh(ServerLevel level, Entity marker) {
        if (level == null || marker == null) return false;
        return seal(level, marker, home(level, marker));
    }

    /**
     * Where this marker's build really stands: the stamped cell while the blocks around it still agree with
     * the marker's own fingerprint, otherwise the cell the fingerprint finds nearest the marker's feet.
     */
    public static BlockPos home(ServerLevel level, Entity marker) {
        BlockPos feet = cell(marker);
        CompoundTag tag = data(marker);
        NpcGraphAnchorCore420.Fingerprint print = NpcGraphAnchorCore420.fingerprint(tag);
        int[] were = tag.getIntArray(STAMP);
        String stampedDim = tag.getString(STAMP_DIM);
        String currentDim = BlockTriggerStore351.dimensionOf(level);
        if (were != null && were.length >= 3
                && (stampedDim == null || stampedDim.isEmpty() || stampedDim.equals(currentDim))) {
            BlockPos stamped = new BlockPos(were[0], were[1], were[2]);
            int stampedTurn = were.length >= 4 ? (were[3] & 7) : NpcGraphAnchorCore420.IDENTITY;
            int[] score = NpcGraphAnchorCore420.score(print.offsets, print.blocks, stampedTurn,
                    (dx, dy, dz) -> NpcGraphAnchorFiles420.blockAt(level, stamped, dx, dy, dz));
            if (score[1] == 0 || (double) score[0] / score[1] >= NpcGraphAnchorCore420.MIN_FINGERPRINT_MATCH) {
                return stamped;
            }
        }
        // Build 423: the same search the paste uses - cell and arrangement together. A marker that was
        // pasted turned must keep gathering around the cell of its build, which only that search knows.
        NpcGraphAnchorCore420.Placement place = NpcGraphAnchorCore420.place(print.offsets, print.blocks,
                (dx, dy, dz) -> NpcGraphAnchorFiles420.blockAt(level, feet, dx, dy, dz),
                PLACE_SEARCH_BLOCKS, PLACE_SEARCH_UP);
        return place.found() ? feet.offset(place.dx, place.dy, place.dz) : feet;
    }

    private static void warn(String line) {
        if (!WARNED.add(line) || WARNED.size() > 24) return;
        LOG.warn(line);
    }

    /** Closes the entity's window on a payload - the four stores are opened by their own classes. */
    private static BlockTriggerStore351 blockStore(MinecraftServer server) {
        try {
            return BlockTriggerStore351.get(server);
        } catch (Throwable failure) {
            warn("anchor: the block graphs could not be read: " + failure);
            return null;
        }
    }

    private static DoorGraph382Store doorStore(MinecraftServer server) {
        try {
            return DoorGraph382Store.get(server);
        } catch (Throwable failure) {
            warn("anchor: the door graphs could not be read: " + failure);
            return null;
        }
    }

    private static ChestGraph396Store chestStore(MinecraftServer server) {
        try {
            return ChestGraph396Store.get(server);
        } catch (Throwable failure) {
            warn("anchor: the chest graphs could not be read: " + failure);
            return null;
        }
    }

    private static DoorStore368 lockStore(MinecraftServer server) {
        try {
            return DoorStore368.get(server);
        } catch (Throwable failure) {
            warn("anchor: the door locks could not be read: " + failure);
            return null;
        }
    }

    /** Says one line in a player's chat - the same call the block editor's own answers use. */
    public static void tell(ServerPlayer player, String line) {
        if (player == null || line == null || line.isEmpty()) return;
        try {
            player.displayClientMessage(Component.literal(line), false);
        } catch (Throwable failure) {
            warn("anchor: a line could not be shown: " + failure);
        }
    }

    /** Where this build's anchor is, in one line for the chat. */
    public static String describe(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return "нет ни мира, ни места";
        Entity marker = markerNear(level, place);
        if (marker == null) return "у постройки нет маркера - схематик её графы не увезёт";
        CompoundTag data = data(marker);
        if (!NpcGraphAnchorCore420.hasGraphs(data)) {
            return "маркер стоит в " + marker.getX() + " " + (int) Math.floor(marker.getY()) + " "
                    + marker.getZ() + ", графов в нём нет";
        }
        return NpcGraphAnchorCore420.entries(data).size() + " записей в маркере на "
                + (int) Math.floor(marker.getX()) + " " + (int) Math.floor(marker.getY()) + " "
                + (int) Math.floor(marker.getZ()) + " (снимок "
                + data.getIntArray(STAMP).length + " чисел)";
    }

    /** What one build's anchor carries, entry by entry - for the {@code list} command and the log. */
    public static List<String> contents(ServerLevel level, BlockPos place) {
        List<String> out = new ArrayList<String>();
        if (level == null || place == null) return out;
        Entity marker = markerNear(level, place);
        if (marker == null) {
            out.add("якорь: маркера рядом нет");
            return out;
        }
        List<NpcGraphAnchorCore420.Entry> entries = NpcGraphAnchorCore420.entries(data(marker));
        out.add("якорь: " + entries.size() + " записей");
        int shown = 0;
        for (NpcGraphAnchorCore420.Entry entry : entries) {
            if (shown >= 12) {
                out.add("  … и ещё " + (entries.size() - shown));
                break;
            }
            shown++;
            out.add("  " + entry.kind + " +" + entry.dx + " " + entry.dy + " " + entry.dz
                    + (entry.locks.isEmpty() ? "" : ", замков " + entry.locks.size())
                    + (entry.hasGraph() ? ", граф есть" : ", графа нет"));
        }
        return out;
    }

    /** The anchor marker nearest a place, or null. */
    public static Entity markerNear(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return null;
        AABB box = new AABB(place.getX() - ANCHOR_SEARCH_BLOCKS, place.getY() - ANCHOR_SEARCH_BLOCKS,
                place.getZ() - ANCHOR_SEARCH_BLOCKS, place.getX() + ANCHOR_SEARCH_BLOCKS + 1,
                place.getY() + ANCHOR_SEARCH_BLOCKS + 1, place.getZ() + ANCHOR_SEARCH_BLOCKS + 1);
        try {
            List<Entity> found = level.getEntitiesOfClass(Entity.class, box,
                    entity -> entity != null && !entity.isRemoved()
                            && NpcGraphAnchorCore420.hasGraphs(data(entity)));
            if (found == null || found.isEmpty()) return null;
            Entity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity marker : found) {
                double dx = marker.getX() - (place.getX() + 0.5D);
                double dy = marker.getY() - place.getY();
                double dz = marker.getZ() - (place.getZ() + 0.5D);
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = marker;
                }
            }
            return best;
        } catch (Throwable failure) {
            warn("anchor: the markers could not be listed: " + failure);
            return null;
        }
    }

    /**
     * The wand, clicked on an entity - "this building has an anchor, and it is here".
     *
     * <p>Three cases, one behaviour: the target is the anchor itself (write what stands around it), the
     * target is a plain body of the building (look for one of the four graphs near it and, if there is
     * one, put a marker beside the body and write), or the target belongs to nothing this mod knows
     * (say so once, write nothing). Returns true only when a marker was written, which is what lets the
     * click be cancelled - a click that wrote nothing must keep its old meaning.</p>
     */
    public static boolean wandOn(ServerPlayer player, ServerLevel level, Entity target) {
        if (player == null || level == null || target == null) return false;
        BlockPos place = new BlockPos((int) Math.floor(target.getX()), (int) Math.floor(target.getY()),
                (int) Math.floor(target.getZ()));
        if (NpcGraphAnchorCore420.hasGraphs(data(target))) {
            BlockPos anchorPos = home(level, target);
            boolean sealed = seal(level, target, anchorPos);
            tell(player, sealed ? "якорь: графы записаны в этот маркер (" + anchorPos.getX() + " "
                    + anchorPos.getY() + " " + anchorPos.getZ() + ")"
                    : "якорь: рядом с " + anchorPos.getX() + " " + anchorPos.getY() + " " + anchorPos.getZ()
                            + " графов нет - записывать нечего");
            return sealed;
        }
        if (markerNear(level, place) != null) {
            tell(player, "якорь: у этой постройки уже есть маркер");
            return false;
        }
        BlockPos spot = freeSpot(level, place);
        if (spot == null) spot = place;
        Entity marker = spawn(level, spot);
        if (marker == null) {
            tell(player, "якорь: маркер не поставился - смотри лог");
            return false;
        }
        BlockPos at = cell(marker);
        boolean sealed = seal(level, marker, at);
        tell(player, sealed ? "якорь: постройка получила маркер на " + at.getX() + " " + at.getY() + " "
                + at.getZ() : "якорь: маркер есть, но графов рядом нет");
        return sealed;
    }
}
