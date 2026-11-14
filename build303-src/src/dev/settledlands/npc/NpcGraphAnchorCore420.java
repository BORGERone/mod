package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Build 420: the graphs of a build, packed so that a builder's copy/paste carries them.
 *
 * <p><b>The report.</b> The author builds in Axiom, copies the selection into a blueprint and pastes it
 * somewhere else - and every graph of that build stays behind. It stays behind because nothing of a block
 * graph is <i>in</i> the block: {@code settledlands_block_triggers}, {@code settledlands_door_graphs},
 * {@code settledlands_chest_graphs} and {@code settledlands_doors} are world data, one entry per
 * <b>absolute</b> position ({@code minecraft:overworld@12,64,-30}). A copy tool moves blocks and
 * block-entities; these four files are neither, so there is nothing for it to move.</p>
 *
 * <p><b>The answer.</b> A marker entity - {@code settledlands:guide_trigger}, the invisible marker the
 * base mod already ships - is stuffed with one NBT {@code List} that holds every graph of the build
 * <b>relative to the marker</b>:</p>
 *
 * <pre>
 * SettledLandsAnchor = 420
 * Graphs = [
 *   {K: "block",  Off: [x,y,z], G: &lt;graph&gt;},
 *   {K: "door",   Off: [x,y,z], G: &lt;graph&gt;, Lock: [[x,y,z], ...]},
 *   {K: "chest",  Off: [x,y,z], G: &lt;graph&gt;},
 * ]
 * FP = [ {Off: [x,y,z], B: "minecraft:oak_planks"} ]
 * </pre>
 *
 * <p>Copy tools copy entities with their NBT (Axiom calls it «Copy Entities», WorldEdit and Litematica
 * do it by default), so the list travels inside the schematic itself. When the pasted marker ticks, the
 * offsets are added to the marker's <b>new</b> position and the entries are written back into the four
 * world files - the graphs are exactly where the pasted blocks are, and every existing rule (the block
 * editor, the door's lock, the chest's panel, «Незаконно») keeps working without knowing this class
 * exists.</p>
 *
 * <p><b>Rotation.</b> The marker's own yaw cannot say how a blueprint was turned, so the anchor does not
 * trust it: it keeps a fingerprint of the blocks around it ({@link #FP}), reads the same offsets in the
 * world it was pasted into, and takes the arrangement of the eight (four quarter turns, each with and
 * without a mirror) whose fingerprint matches best. A weak match falls back to the identity, which is the
 * no-rotation case and therefore the safe answer.</p>
 *
 * <p>Everything here is plain arithmetic over {@link CompoundTag} and a handful of strings: the packing,
 * the rotation and the fingerprint match run - and are run by {@code tools/anchor_check.py} - with no game
 * in the room. The class that touches the world is {@link NpcGraphAnchor420}, and the class that turns
 * offsets into store writes is {@link NpcGraphAnchorFiles420}.</p>
 */
public final class NpcGraphAnchorCore420 {

    /** The key that says "this list belongs to SettledLands" - a layout, not a build number. */
    public static final String MARK = "SettledLandsAnchor";

    /** The layout of the list. A number of its own, so a later build can read an older anchor. */
    public static final int LAYOUT = 420;

    public static final String GRAPHS = "Graphs";
    public static final String FP = "FP";

    /** One graph entry. */
    public static final String KIND = "K";
    public static final String OFFSET = "Off";
    public static final String GRAPH = "G";
    public static final String LOCKS = "Lock";

    /** One fingerprint entry. */
    public static final String BLOCK = "B";

    /** The three kinds of graph a build can carry. The strings are in the file, so they never change. */
    public static final String BLOCK_KIND = "block";
    public static final String DOOR_KIND = "door";
    public static final String CHEST_KIND = "chest";

    /** How many graph entries one anchor may carry. */
    public static final int MAX_ENTRIES = 1024;

    /** How many blocks one fingerprint may remember. */
    public static final int MAX_FINGERPRINT = 4096;

    /** Below this share of matching fingerprint blocks no turn is chosen at all. */
    public static final double MIN_FINGERPRINT_MATCH = 0.6D;

    /** The arrangement a build with no rotation gets - and the one every tie falls back to. */
    public static final int IDENTITY = 0;

    private NpcGraphAnchorCore420() {
    }

    /** One graph of the building, as it travels inside the anchor. */
    public static final class Entry {
        public final String kind;
        public final int dx;
        public final int dy;
        public final int dz;
        /** The whole graph, or null for an entry that carries only locks. */
        public final CompoundTag graph;
        /** Door locks, relative to the entry's own place - a door has two halves, both may be locked. */
        public final List<int[]> locks;

        public Entry(String kind, int dx, int dy, int dz, CompoundTag graph, List<int[]> locks) {
            this.kind = kind == null || kind.isEmpty() ? BLOCK_KIND : kind;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.graph = graph;
            this.locks = locks == null ? new ArrayList<int[]>() : locks;
        }

        public boolean hasGraph() {
            return graph != null && !graph.isEmpty();
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString(KIND, kind);
            tag.putIntArray(OFFSET, new int[] {dx, dy, dz});
            if (hasGraph()) tag.put(GRAPH, graph);
            if (!locks.isEmpty()) {
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                for (int[] lock : locks) {
                    if (lock == null || lock.length < 3) continue;
                    CompoundTag one = new CompoundTag();
                    one.putIntArray(OFFSET, new int[] {lock[0], lock[1], lock[2]});
                    list.add(one);
                }
                if (!list.isEmpty()) tag.put(LOCKS, list);
            }
            return tag;
        }

        public static Entry fromTag(CompoundTag tag) {
            if (tag == null) return null;
            int[] off = readOffset(tag.getIntArray(OFFSET));
            CompoundTag graph = tag.contains(GRAPH, 10) ? tag.getCompound(GRAPH) : null;
            List<int[]> locks = new ArrayList<int[]>();
            net.minecraft.nbt.ListTag list = tag.getList(LOCKS, 10);
            for (int i = 0; i < list.size(); i++) {
                locks.add(readOffset(list.getCompound(i).getIntArray(OFFSET)));
            }
            return new Entry(tag.getString(KIND), off[0], off[1], off[2], graph, locks);
        }
    }

    // ---- packing ------------------------------------------------------------

    /**
     * The anchor's NBT for a list of entries and a fingerprint. An empty list writes nothing at all, so an
     * anchor with no graphs is an ordinary marker and costs nothing.
     */
    public static CompoundTag write(List<Entry> entries, List<int[]> fingerprintOffsets,
            List<String> fingerprintBlocks) {
        CompoundTag tag = new CompoundTag();
        if (entries == null || entries.isEmpty()) return tag;
        tag.putInt(MARK, LAYOUT);
        net.minecraft.nbt.ListTag graphs = new net.minecraft.nbt.ListTag();
        int written = 0;
        for (Entry entry : entries) {
            if (entry == null || written >= MAX_ENTRIES) continue;
            written++;
            graphs.add(entry.toTag());
        }
        tag.put(GRAPHS, graphs);
        net.minecraft.nbt.ListTag fp = new net.minecraft.nbt.ListTag();
        int prints = fingerprintOffsets == null ? 0 : Math.min(fingerprintOffsets.size(), MAX_FINGERPRINT);
        for (int i = 0; i < prints; i++) {
            int[] offset = fingerprintOffsets.get(i);
            String block = fingerprintBlocks == null || i >= fingerprintBlocks.size()
                    ? null : fingerprintBlocks.get(i);
            if (offset == null || block == null || block.isEmpty()) continue;
            CompoundTag one = new CompoundTag();
            one.putIntArray(OFFSET, new int[] {offset[0], offset[1], offset[2]});
            one.putString(BLOCK, block);
            fp.add(one);
        }
        if (!fp.isEmpty()) tag.put(FP, fp);
        return tag;
    }

    /** True when this tag carries graphs - the question the anchor asks once per entity. */
    public static boolean hasGraphs(CompoundTag tag) {
        return tag != null && tag.contains(GRAPHS, 9) && !tag.getList(GRAPHS, 10).isEmpty();
    }

    /** The entries of an anchor, or an empty list when the tag carries none. */
    public static List<Entry> entries(CompoundTag tag) {
        List<Entry> out = new ArrayList<Entry>();
        if (tag == null || !tag.contains(GRAPHS, 9)) return out;
        net.minecraft.nbt.ListTag list = tag.getList(GRAPHS, 10);
        for (int i = 0; i < list.size() && out.size() < MAX_ENTRIES; i++) {
            Entry entry = Entry.fromTag(list.getCompound(i));
            if (entry != null) out.add(entry);
        }
        return out;
    }

    /** The fingerprint of an anchor: the offsets, and the block standing at each. */
    public static final class Fingerprint {
        public final List<int[]> offsets = new ArrayList<int[]>();
        public final List<String> blocks = new ArrayList<String>();

        public boolean isEmpty() {
            return offsets.isEmpty();
        }
    }

    /** Reads the fingerprint back out of an anchor - an empty one when it carries none. */
    public static Fingerprint fingerprint(CompoundTag tag) {
        Fingerprint print = new Fingerprint();
        if (tag == null || !tag.contains(FP, 9)) return print;
        net.minecraft.nbt.ListTag list = tag.getList(FP, 10);
        for (int i = 0; i < list.size() && print.offsets.size() < MAX_FINGERPRINT; i++) {
            CompoundTag one = list.getCompound(i);
            String block = one.getString(BLOCK);
            if (block == null || block.isEmpty()) continue;
            print.offsets.add(readOffset(one.getIntArray(OFFSET)));
            print.blocks.add(block);
        }
        return print;
    }

    // ---- rotation -----------------------------------------------------------

    /**
     * Turns an offset by one of the eight arrangements.
     *
     * <p>Order of work: mirror first (x negated), then the quarter turns clockwise seen from above -
     * {@code (x,z)} becomes {@code (-z,x)}, the mapping a blueprint's own rotate uses. The y axis is
     * never touched: a blueprint is not pasted upside down.</p>
     *
     * @param turn 0..7; the mirror is bit 2, the quarter turn is bits 0..1
     */
    public static int[] turn(int[] offset, int turn) {
        int x = offset == null || offset.length < 3 ? 0 : offset[0];
        int y = offset == null || offset.length < 3 ? 0 : offset[1];
        int z = offset == null || offset.length < 3 ? 0 : offset[2];
        if ((turn & 4) != 0) x = -x;
        for (int i = 0; i < (turn & 3); i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, y, z};
    }

    /** The same arrangement, said in words - the log and the chat use it. */
    public static String turnName(int turn) {
        String mirror = (turn & 4) != 0 ? "зеркало + " : "";
        return mirror + ((turn & 3) * 90) + "°";
    }

    // ---- fingerprint --------------------------------------------------------

    /** What the world has to answer for a fingerprint: the block at one offset, as {@code namespace:path}. */
    public interface BlockLookup {
        String blockAt(int dx, int dy, int dz);
    }

    /**
     * Compares one arrangement against the blocks really standing around the pasted anchor.
     *
     * @return two numbers: how many fingerprint blocks were found at their turned offset, and how many
     *         were looked for
     */
    public static int[] score(List<int[]> offsets, List<String> blocks, int turn, BlockLookup lookup) {
        int found = 0;
        int asked = 0;
        if (offsets == null || blocks == null || lookup == null) return new int[] {0, 0};
        for (int i = 0; i < offsets.size() && i < blocks.size(); i++) {
            int[] offset = offsets.get(i);
            String block = blocks.get(i);
            if (offset == null || block == null || block.isEmpty()) continue;
            asked++;
            int[] turned = turn(offset, turn);
            String there = lookup.blockAt(turned[0], turned[1], turned[2]);
            if (block.equals(there)) found++;
        }
        return new int[] {found, asked};
    }

    /**
     * The arrangement to use.
     *
     * <p>Winner by the share of matching blocks; a share below {@link #MIN_FINGERPRINT_MATCH} is not a
     * match at all. Identity wins a tie, then the smaller turn number - so a build that was never rotated
     * cannot be turned by a coincidence of two blocks that happen to repeat.</p>
     */
    public static int bestTurn(List<int[]> offsets, List<String> blocks, BlockLookup lookup) {
        int best = IDENTITY;
        double bestShare = -1.0D;
        for (int turn = 0; turn < 8; turn++) {
            int[] score = score(offsets, blocks, turn, lookup);
            if (score[1] == 0) return IDENTITY;
            double share = (double) score[0] / (double) score[1];
            if (share > bestShare + 1.0E-9D) {
                bestShare = share;
                best = turn;
            }
        }
        return bestShare < MIN_FINGERPRINT_MATCH ? IDENTITY : best;
    }

    // ---- writing the world --------------------------------------------------

    /** What one paste did, for the log and for the line the author sees in the chat. */
    public static final class Result {
        public int blocks;
        public int doors;
        public int chests;
        public int locks;
        public int skipped;
        public int turn;

        public int total() {
            return blocks + doors + chests;
        }

        public String line() {
            return "якорь: графов " + total() + " (блоков " + blocks + ", дверей " + doors
                    + ", сундуков " + chests + "), замков " + locks
                    + (skipped > 0 ? ", пропущено " + skipped : "")
                    + (turn != IDENTITY ? ", поворот " + turnName(turn) : "");
        }
    }

    /** The four writes an anchor makes into the world, as the rule sees them. */
    public interface StoreSink {
        boolean block(int x, int y, int z, CompoundTag graph);

        boolean door(int x, int y, int z, CompoundTag graph);

        boolean chest(int x, int y, int z, CompoundTag graph);

        boolean lock(int x, int y, int z);
    }

    /**
     * Writes every entry of an anchor into the world, at the pasted place.
     *
     * <p>Kept separate from the entity so the rule can be run without a world: the caller hands the
     * writes in and gets back what happened. The pasted copy wins over whatever stood at the target -
     * that is the author's answer to the question, so an old graph at that spot is replaced, not merged.</p>
     */
    public static Result materialize(List<Entry> entries, int turn, BlockPos anchor, StoreSink sink) {
        Result result = new Result();
        result.turn = turn;
        if (entries == null || anchor == null || sink == null) return result;
        for (Entry entry : entries) {
            if (entry == null) continue;
            int[] turned = turn(new int[] {entry.dx, entry.dy, entry.dz}, turn);
            boolean written = false;
            if (BLOCK_KIND.equals(entry.kind)) {

                written = sink.block(anchor.getX() + turned[0], anchor.getY() + turned[1],
                        anchor.getZ() + turned[2], entry.graph);
                if (written) result.blocks++;
            } else if (DOOR_KIND.equals(entry.kind)) {
                written = sink.door(anchor.getX() + turned[0], anchor.getY() + turned[1],
                        anchor.getZ() + turned[2], entry.graph);
                if (written) result.doors++;
            } else if (CHEST_KIND.equals(entry.kind)) {
                written = sink.chest(anchor.getX() + turned[0], anchor.getY() + turned[1],
                        anchor.getZ() + turned[2], entry.graph);
                if (written) result.chests++;
            }
            for (int[] lock : entry.locks) {
                // A lock is stored relative to its own entry (both halves of one door), so it travels
                // with the entry: the anchor's place, plus the entry, plus the lock.
                int[] turnedLock = turn(lock, turn);
                if (sink.lock(anchor.getX() + turned[0] + turnedLock[0],
                        anchor.getY() + turned[1] + turnedLock[1],
                        anchor.getZ() + turned[2] + turnedLock[2])) {
                    result.locks++;
                }
            }
            if (!written && entry.hasGraph()) result.skipped++;
        }
        return result;
    }

    // ---- small helpers ------------------------------------------------------

    /** An offset against an anchor - negative values are allowed and kept. */
    public static int[] offsetOf(int x, int y, int z, BlockPos anchor) {
        if (anchor == null) return new int[] {x, y, z};
        return new int[] {x - anchor.getX(), y - anchor.getY(), z - anchor.getZ()};
    }

    /** True when a place lies inside a cube of that radius around the anchor. */
    public static boolean inside(int x, int y, int z, BlockPos anchor, int radius) {
        if (anchor == null || radius < 0) return false;
        return Math.abs(x - anchor.getX()) <= radius
                && Math.abs(y - anchor.getY()) <= radius
                && Math.abs(z - anchor.getZ()) <= radius;
    }

    static int[] readOffset(int[] raw) {
        if (raw == null || raw.length < 3) return new int[] {0, 0, 0};
        return new int[] {raw[0], raw[1], raw[2]};
    }
}
