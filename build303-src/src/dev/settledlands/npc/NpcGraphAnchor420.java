package dev.settledlands.npc;

import java.util.ArrayList;
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
    public static final int GATHER_BLOCKS = 24;

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

    private static int passLevel(ServerLevel level, List<? extends ServerPlayer> players) {
        int work = 0;
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
                    entity -> entity != null && NpcGraphAnchorCore420.hasGraphs(data(entity)));
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
            if (made >= 4) break;
            if (nearestMarker(markers, place) != null) continue;
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

    /** The places of the graphs of one level that lie near a player. */
    private static List<BlockPos> graphPlaces(ServerLevel level, List<? extends ServerPlayer> players) {
        List<BlockPos> out = new ArrayList<BlockPos>();
        String dimension = BlockTriggerStore351.dimensionOf(level);
        MinecraftServer server = level.getServer();
        if (server == null) return out;
        BlockTriggerStore351 blocks = blockStore(server);
        if (blocks != null) {
            for (BlockTriggerStore351.Entry entry : blocks.ofDimension(dimension)) {
                if (entry == null || entry.graph == null) continue;
                BlockPos place = new BlockPos(entry.x, entry.y, entry.z);
                if (near(place, players) && !out.contains(place)) out.add(place);
            }
        }
        return out;
    }

    private static boolean near(BlockPos place, List<? extends ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player == null) continue;
            double dx = place.getX() + 0.5D - player.getX();
            double dy = place.getY() + 0.5D - player.getY();
            double dz = place.getZ() + 0.5D - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= 24.0D * 24.0D) return true;
        }
        return false;
    }

    private static Entity nearestMarker(List<Entity> markers, BlockPos place) {
        for (Entity marker : markers) {
            if (marker == null) continue;
            double dx = marker.getX() - (place.getX() + 0.5D);
            double dy = marker.getY() - place.getY();
            double dz = marker.getZ() - (place.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz <= (double) ANCHOR_SEARCH_BLOCKS * ANCHOR_SEARCH_BLOCKS) {
                return marker;
            }
        }
        return null;
    }

    /**
     * A free cell for the marker: the given place itself while it is empty, then its neighbours, then the
     * column above it. Never a solid block - a marker inside a wall would be a marker a paste drops.
     */
    public static BlockPos freeSpot(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return null;
        if (empty(level, place)) return place;
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos up = place.offset(0, dy, 0);
            if (empty(level, up)) return up;
        }
        for (int r = 1; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos at = place.offset(dx, dy, dz);
                        if (empty(level, at)) return at;
                    }
                }
            }
        }
        return null;
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
        if (entries.isEmpty()) return false;
        NpcGraphAnchorCore420.Fingerprint print = fingerprint(level, anchor);
        CompoundTag tag = NpcGraphAnchorCore420.write(entries, print.offsets, print.blocks);
        if (!NpcGraphAnchorCore420.hasGraphs(tag)) return false;
        copy(tag, data(marker));
        stamp(marker, anchor, NpcGraphAnchorCore420.IDENTITY);
        return true;
    }

    /** Forces a seal for the building a player stands in, if any. Used by the interact and the log. */
    public static boolean sealHere(ServerLevel level, BlockPos place) {
        if (level == null || place == null) return false;
        Entity marker = spawn(level, freeSpot(level, place) == null ? place : freeSpot(level, place));
        return marker != null && seal(level, marker, cell(marker));
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
        boolean moved = were == null || were.length < 3
                || were[0] != here.getX() || were[1] != here.getY() || were[2] != here.getZ();
        if (!moved) return false;
        int turn = place.turn;
        List<NpcGraphAnchorCore420.Entry> entries = NpcGraphAnchorCore420.entries(data);
        NpcGraphAnchorCore420.Result result = NpcGraphAnchorFiles420.materialize(level, level.getServer(),
                entries, turn, here);
        stamp(marker, here, turn);
        LOG.info("anchor: pasted at " + here + " (the marker's feet are in " + feet + ", the building was"
                + " found " + place.dx + " " + place.dy + " " + place.dz + " from them, " + place.found
                + "/" + place.asked + " blocks agree, turn " + turn + ") - " + result.line()
                + " (" + entries.size() + " entries carried)");
        return result.total() > 0 || !entries.isEmpty();
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

    private static void stamp(Entity marker, BlockPos at, int turn) {
        if (marker == null || at == null) return;
        data(marker).putIntArray(STAMP, new int[] {at.getX(), at.getY(), at.getZ(), turn});
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
        NpcGraphAnchorCore420.Fingerprint print = NpcGraphAnchorCore420.fingerprint(data(marker));
        int[] were = data(marker).getIntArray(STAMP);
        if (were != null && were.length >= 3) {
            BlockPos stamped = new BlockPos(were[0], were[1], were[2]);
            int[] score = NpcGraphAnchorCore420.score(print.offsets, print.blocks,
                    NpcGraphAnchorCore420.IDENTITY,
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

    /** Closes the entity's window on a payload - the two stores are opened by their own classes. */
    private static BlockTriggerStore351 blockStore(MinecraftServer server) {
        try {
            return BlockTriggerStore351.get(server);
        } catch (Throwable failure) {
            warn("anchor: the block graphs could not be read: " + failure);
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
                    entity -> entity != null && NpcGraphAnchorCore420.hasGraphs(data(entity)));
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
            boolean sealed = seal(level, target, place);
            tell(player, sealed ? "якорь: графы записаны в этот маркер (" + place.getX() + " "
                    + place.getY() + " " + place.getZ() + ")"
                    : "якорь: рядом с " + place.getX() + " " + place.getY() + " " + place.getZ()
                            + " графов нет - записывать нечего");
            return sealed;
        }
        if (markerNear(level, place) != null) {
            tell(player, "якорь: у этой постройки уже есть маркер");
            return false;
        }
        Entity marker = spawn(level, freeSpot(level, place) == null ? place : freeSpot(level, place));
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
