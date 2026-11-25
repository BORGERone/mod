import dev.settledlands.npc.NpcGraphAnchorCore420;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Build 420 live JVM probe for {@link NpcGraphAnchorCore420} - the rule of the anchor, with no game in
 * the room.
 *
 * <p>What it proves, in order:</p>
 * <ol>
 *   <li>the eight arrangements are the eight the game's own rotate/mirror produces - four quarter turns
 *       clockwise seen from above, each with and without the mirror, and never a change of y;</li>
 *   <li>the packing round trip keeps a block graph and a door with two locked halves - offsets, kind,
 *       the graph itself and both locks come back byte for byte;</li>
 *   <li>a fingerprint of a rotated building picks the rotation that was really pasted, not identity,
 *       and a fingerprint that matches nothing falls back to identity instead of turning a stranger's
 *       build;</li>
 *   <li>{@code materialize} lays every entry over the pasted anchor with that turn, writes both halves
 *       of a door's lock and counts what it did - the counting is what the chat line and the log say, so
 *       a wrong count is a lie to the author;</li>
 *   <li>a marker with no graphs writes nothing at all, so an anchor that was never sealed is an ordinary
 *       marker.</li>
 * </ol>
 */
public final class Anchor420Probe {

    private static int checks;
    private static int failed;

    public static void main(String[] args) {
        turnTable();
        roundTrip();
        fingerprintPicksTheTurn();
        fingerprintFallsBack();
        materializeLaysTheBuild();
        placeComesFromTheFingerprint();
        turnsAndCellTogether();
        emptyAnchorWritesNothing();
        placeWithTurnFallback();
        gatherCoverage();

        System.out.println("ANCHOR_CHECKS=" + checks);
        System.out.println(failed == 0 ? "ANCHOR_OK=true" : "ANCHOR_OK=false failed=" + failed);
        if (failed != 0) System.exit(1);
    }

    // ---- 1. the eight arrangements -----------------------------------------

    private static void turnTable() {
        int[] east = new int[] {1, 0, 0};
        expect("turn 0 keeps east", eq(NpcGraphAnchorCore420.turn(east, 0), 1, 0, 0));
        expect("turn 1 (90) sends east to north", eq(NpcGraphAnchorCore420.turn(east, 1), 0, 0, 1));
        expect("turn 2 (180) sends east to west", eq(NpcGraphAnchorCore420.turn(east, 2), -1, 0, 0));
        expect("turn 3 (270) sends east to south", eq(NpcGraphAnchorCore420.turn(east, 3), 0, 0, -1));
        expect("mirror alone keeps east's length and flips it", eq(
                NpcGraphAnchorCore420.turn(east, 4), -1, 0, 0));
        // Mirror first (x negated), then the quarter turn - the order the class documents.
        expect("mirror + 90", eq(NpcGraphAnchorCore420.turn(east, 5), 0, 0, -1));

        int[] up = new int[] {0, 3, 0};
        for (int turn = 0; turn < 8; turn++) {
            expect("turn " + turn + " never moves y", eq(NpcGraphAnchorCore420.turn(up, turn), 0, 3, 0));
        }
        expect("a turn is a bijection on the plane", distinctTurns());
        expect("turn names say the angle", NpcGraphAnchorCore420.turnName(3).equals("270°")
                && NpcGraphAnchorCore420.turnName(5).contains("зеркало"));
    }

    private static boolean distinctTurns() {
        Map<String, Integer> seen = new HashMap<String, Integer>();
        int[] probe = new int[] {2, 0, 5};
        for (int turn = 0; turn < 8; turn++) {
            int[] t = NpcGraphAnchorCore420.turn(probe, turn);
            String key = t[0] + "," + t[1] + "," + t[2];
            if (seen.put(key, Integer.valueOf(turn)) != null) return false;
        }
        return seen.size() == 8;
    }

    private static boolean eq(int[] actual, int x, int y, int z) {
        return actual != null && actual.length >= 3 && actual[0] == x && actual[1] == y && actual[2] == z;
    }

    // ---- 2. the packing round trip -----------------------------------------

    private static void roundTrip() {
        CompoundTag blockGraph = new CompoundTag();
        blockGraph.putInt("nodes", 3);
        blockGraph.putString("name", "Реплика");
        CompoundTag doorGraph = new CompoundTag();
        doorGraph.putInt("nodes", 1);

        List<NpcGraphAnchorCore420.Entry> entries = new ArrayList<>();
        entries.add(new NpcGraphAnchorCore420.Entry("block", 4, 0, -2, blockGraph, null));
        List<int[]> doorLocks = new ArrayList<>();
        doorLocks.add(new int[] {0, 0, 0});
        doorLocks.add(new int[] {0, 1, 0});
        entries.add(new NpcGraphAnchorCore420.Entry("door", 7, 0, 1, doorGraph, doorLocks));
        entries.add(new NpcGraphAnchorCore420.Entry("chest", -3, 2, 0, null, null));

        List<int[]> printOffsets = new ArrayList<>();
        List<String> printBlocks = new ArrayList<>();
        printOffsets.add(new int[] {1, 0, 0});
        printBlocks.add("minecraft:stone");
        printOffsets.add(new int[] {0, 1, 0});
        printBlocks.add("minecraft:oak_planks");

        CompoundTag tag = NpcGraphAnchorCore420.write(entries, printOffsets, printBlocks);
        expect("a sealed anchor says it has graphs", NpcGraphAnchorCore420.hasGraphs(tag));
        expect("the layout number is written", tag.getInt(NpcGraphAnchorCore420.MARK)
                == NpcGraphAnchorCore420.LAYOUT);

        List<NpcGraphAnchorCore420.Entry> back = NpcGraphAnchorCore420.entries(tag);
        expect("all three entries come back", back.size() == 3);
        if (back.size() == 3) {
            expect("the block keeps its kind", "block".equals(back.get(0).kind));
            expect("the block keeps its offset", back.get(0).dx == 4 && back.get(0).dy == 0
                    && back.get(0).dz == -2);
            expect("the block keeps its graph", back.get(0).hasGraph()
                    && back.get(0).graph.getInt("nodes") == 3
                    && "Реплика".equals(back.get(0).graph.getString("name")));
            expect("the door keeps both locked halves", back.get(1).locks.size() == 2
                    && back.get(1).locks.get(1)[1] == 1);
            expect("the chest keeps its kind without a graph", "chest".equals(back.get(2).kind)
                    && !back.get(2).hasGraph());
        }

        NpcGraphAnchorCore420.Fingerprint print = NpcGraphAnchorCore420.fingerprint(tag);
        expect("the fingerprint comes back whole", print.offsets.size() == 2
                && "minecraft:oak_planks".equals(print.blocks.get(1)));
    }

    // ---- 3. the fingerprint picks the turn pasted ---------------------------

    private static void fingerprintPicksTheTurn() {
        // The building as it stood when it was sealed: stone to the east, planks above.
        List<int[]> offsets = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        offsets.add(new int[] {3, 0, 0});
        blocks.add("minecraft:stone");
        offsets.add(new int[] {0, 0, 3});
        blocks.add("minecraft:oak_planks");
        offsets.add(new int[] {0, 1, 0});
        blocks.add("minecraft:glass");

        for (int turn = 0; turn < 8; turn++) {
            final int chosen = turn;
            // The building as the paste really left it: every block of the fingerprint stands at the
            // position its own offset lands on after the turn, and nowhere else.
            final Map<String, String> pasted = new HashMap<>();
            for (int i = 0; i < offsets.size(); i++) {
                int[] at = NpcGraphAnchorCore420.turn(offsets.get(i), chosen);
                pasted.put(at[0] + "," + at[1] + "," + at[2], blocks.get(i));
            }
            NpcGraphAnchorCore420.BlockLookup world = (dx, dy, dz) -> {
                String there = pasted.get(dx + "," + dy + "," + dz);
                return there == null ? "minecraft:air" : there;
            };
            int picked = NpcGraphAnchorCore420.bestTurn(offsets, blocks, world);
            expect("a building pasted with turn " + turn + " is read as turn " + turn
                    + " (got " + picked + ")", picked == turn);
        }
    }

    private static void fingerprintFallsBack() {
        List<int[]> offsets = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        offsets.add(new int[] {2, 0, 0});
        blocks.add("minecraft:stone");
        offsets.add(new int[] {0, 0, 2});
        blocks.add("minecraft:stone");
        // A world where nothing of the fingerprint stands anywhere: every arrangement scores 0.
        NpcGraphAnchorCore420.BlockLookup empty = (dx, dy, dz) -> "minecraft:air";
        expect("a marker among strangers is not turned",
                NpcGraphAnchorCore420.bestTurn(offsets, blocks, empty)
                        == NpcGraphAnchorCore420.IDENTITY);

        // A world where only the identity reads true: a single distinctive block.
        List<int[]> one = new ArrayList<>();
        List<String> oneBlock = new ArrayList<>();
        one.add(new int[] {0, 0, 5});
        oneBlock.add("minecraft:gold_block");
        NpcGraphAnchorCore420.BlockLookup world = (dx, dy, dz) -> dz == 5 && dx == 0 && dy == 0
                ? "minecraft:gold_block" : "minecraft:air";
        expect("one block that reads true is enough to keep identity",
                NpcGraphAnchorCore420.bestTurn(one, oneBlock, world)
                        == NpcGraphAnchorCore420.IDENTITY);
    }

    // ---- 4. the build is laid over the pasted anchor -----------------------

    private static void materializeLaysTheBuild() {
        CompoundTag blockGraph = new CompoundTag();
        blockGraph.putInt("nodes", 2);
        List<NpcGraphAnchorCore420.Entry> entries = new ArrayList<>();
        entries.add(new NpcGraphAnchorCore420.Entry("block", 2, 0, 0, blockGraph, null));
        entries.add(new NpcGraphAnchorCore420.Entry("door", 5, 0, 0, blockGraph, halves()));
        entries.add(new NpcGraphAnchorCore420.Entry("chest", 8, 0, 0, blockGraph, null));

        Recorder recorder = new Recorder();
        BlockPos pasted = new BlockPos(100, 70, -40);
        NpcGraphAnchorCore420.Result result =
                NpcGraphAnchorCore420.materialize(entries, 0, pasted, recorder);

        expect("identity: the block lands east of the anchor", recorder.has("block", 102, 70, -40));
        expect("identity: the door lands at its own offset", recorder.has("door", 105, 70, -40));
        expect("identity: the chest lands at its own offset", recorder.has("chest", 108, 70, -40));
        expect("both halves of the door are locked",
                recorder.has("lock", 105, 70, -40) && recorder.has("lock", 105, 71, -40));
        expect("the count is the count", result.blocks == 1 && result.doors == 1 && result.chests == 1
                && result.locks == 2 && result.total() == 3);

        Recorder turned = new Recorder();
        NpcGraphAnchorCore420.Result one = NpcGraphAnchorCore420.materialize(entries, 1, pasted, turned);
        // turn 1: (x, z) -> (-z, x); the block at +2x goes to +2z.
        expect("turn 1: the block follows the turn", turned.has("block", 100, 70, -38));
        expect("turn 1: the door follows the turn", turned.has("door", 100, 70, -35));
        expect("turn 1: the locks follow the turn too", turned.has("lock", 100, 70, -35)
                && turned.has("lock", 100, 71, -35));
        expect("turn 1: the result says which turn it was", one.turn == 1);
        expect("turn 1: nothing was skipped", one.skipped == 0);

        // A store that refuses everything still reports, it does not throw: a full file must not take
        // the server down mid-paste.
        Recorder refusing = new Recorder();
        refusing.refuse = true;
        NpcGraphAnchorCore420.Result none =
                NpcGraphAnchorCore420.materialize(entries, 0, pasted, refusing);
        expect("a refusing store writes nothing and is counted as skipped", none.total() == 0
                && none.skipped == 3);
    }

    private static List<int[]> halves() {
        List<int[]> locks = new ArrayList<>();
        locks.add(new int[] {0, 0, 0});
        locks.add(new int[] {0, 1, 0});
        return locks;
    }

    private static final class Recorder implements NpcGraphAnchorCore420.StoreSink {
        final List<String> writes = new ArrayList<>();
        boolean refuse;

        @Override
        public boolean block(int x, int y, int z, CompoundTag graph) {
            return record("block", x, y, z);
        }

        @Override
        public boolean door(int x, int y, int z, CompoundTag graph) {
            return record("door", x, y, z);
        }

        @Override
        public boolean chest(int x, int y, int z, CompoundTag graph) {
            return record("chest", x, y, z);
        }

        @Override
        public boolean lock(int x, int y, int z) {
            return record("lock", x, y, z);
        }

        private boolean record(String kind, int x, int y, int z) {
            if (refuse) return false;
            writes.add(kind + "@" + x + "," + y + "," + z);
            return true;
        }

        boolean has(String kind, int x, int y, int z) {
            return writes.contains(kind + "@" + x + "," + y + "," + z);
        }
    }

    // ---- 4b. the cell of a paste is the build's cell, not the marker's feet -

    /**
     * Build 422, from the author's own log: a marker sealed at {@code y = 199} reported «pasted» at
     * {@code y = 198} two seconds later, with no paste in between, and the graph was written one block
     * under the block it belongs to. The cause is that a marker's y is a {@code double} and the cell was
     * read with {@code floor(y)} of the marker's feet - so half a block of drift moves the graphs.
     *
     * <p>These cases pin the fix: the fingerprint decides the cell, and it decides the same cell whether
     * the marker stands on it exactly or a fraction of a block below it.</p>
     */
    private static void placeComesFromTheFingerprint() {
        // The author's own geometry. The build's anchor cell is A = y 199 and its floor is the cell
        // below A (y 198); the fingerprint was recorded relative to A, so every offset has dy = -1.
        // The marker drifted one cell down: its feet are now at y 198 instead of 199.
        final int feetY = 198;
        Map<String, String> world = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.put(key(dx, 198, dz), "minecraft:stone");
            }
        }
        NpcGraphAnchorCore420.BlockLookup stays =
                (dx, dy, dz) -> world.getOrDefault(key(dx, feetY + dy, dz), "minecraft:air");
        List<int[]> offsets = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                offsets.add(new int[] {dx, -1, dz});
                blocks.add("minecraft:stone");
            }
        }

        int[] dropped = NpcGraphAnchorCore420.bestPlace(offsets, blocks, stays, 5, 3);
        expect("a marker one cell below its build is put back on the build",
                dropped[0] == 0 && dropped[1] == 1 && dropped[2] == 0);

        // The graphs must be written at the cell the fingerprint found - A - and nowhere near the feet.
        CompoundTag graph = new CompoundTag();
        graph.putInt("nodes", 1);
        List<NpcGraphAnchorCore420.Entry> entries = new ArrayList<>();
        entries.add(new NpcGraphAnchorCore420.Entry("block", 0, 0, 0, graph, null));
        BlockPos here = new BlockPos(0, feetY, 0).offset(dropped[0], dropped[1], dropped[2]);
        Recorder recorder = new Recorder();
        NpcGraphAnchorCore420.materialize(entries, 0, here, recorder);
        expect("the graph lands on the build's cell", recorder.has("block", 0, 199, 0));
        expect("the graph is not written one cell low", !recorder.has("block", 0, 198, 0));

        // A marker that has not moved must not be moved by the search: same cell in, same cell out.
        NpcGraphAnchorCore420.BlockLookup exact =
                (dx, dy, dz) -> world.getOrDefault(key(dx, 199 + dy, dz), "minecraft:air");
        int[] still = NpcGraphAnchorCore420.bestPlace(offsets, blocks, exact, 5, 3);
        expect("a marker standing on its build keeps its own cell",
                still[0] == 0 && still[1] == 0 && still[2] == 0);

        // Strangers: nothing matches, so the marker's own cell is kept rather than a random shift.
        NpcGraphAnchorCore420.BlockLookup strangers = (dx, dy, dz) -> "minecraft:air";
        int[] nowhere = NpcGraphAnchorCore420.bestPlace(offsets, blocks, strangers, 5, 3);
        expect("a fingerprint that matches nothing does not move the anchor",
                nowhere[0] == 0 && nowhere[1] == 0 && nowhere[2] == 0);
    }

    /**
     * Build 423, from the author's report «все копируется, но с поворотами графы съезжают».
     *
     * <p>Build 422 searched the cell with unrotated offsets and only then the turn. For turns 2, 4 and 6
     * (180 and 270 degrees, with and without the mirror) the cell was found wrong, and the graphs were laid
     * over a shifted anchor - the drive the author saw. Cell and turn are now searched as one pair.</p>
     *
     * <p>This case walks all eight arrangements and, inside each, a marker that drifted one cell in each
     * direction: 144 pastes, every one of which must land on its own cell with its own turn.</p>
     */
    private static void turnsAndCellTogether() {
        // A deliberately lopsided build: a floor plus four stacks of different blocks, so that a wrong
        // arrangement cannot score as well as the right one.
        Map<String, String> build = new HashMap<>();
        for (int dx = 0; dx < 6; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                build.put(key(dx, -1, dz), "minecraft:stone");
            }
        }
        String[] stacks = {"minecraft:oak_planks", "minecraft:glowstone", "minecraft:gold_block",
                "minecraft:bookshelf"};
        int[][] where = {{5, 0}, {0, 3}, {5, 3}, {2, 2}};
        for (int i = 0; i < stacks.length; i++) {
            for (int dy = 0; dy <= 2; dy++) {
                build.put(key(where[i][0], dy, where[i][1]), stacks[i]);
            }
        }
        // The marker was sealed off-centre, as it is in the author's world.
        final int anchorX = 1;
        final int anchorZ = 1;
        List<int[]> offsets = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (Map.Entry<String, String> e : build.entrySet()) {
            String[] p = e.getKey().split("/");
            offsets.add(new int[] {Integer.parseInt(p[0]) - anchorX, Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]) - anchorZ});
            blocks.add(e.getValue());
        }

        int wrong = 0;
        int cases = 0;
        for (int turn = 0; turn < 8; turn++) {
            Map<String, String> turned = new HashMap<>();
            for (Map.Entry<String, String> e : build.entrySet()) {
                String[] p = e.getKey().split("/");
                int[] t = NpcGraphAnchorCore420.turn(
                        new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])},
                        turn);
                turned.put(key(t[0], t[1], t[2]), e.getValue());
            }
            int[] movedAnchor = NpcGraphAnchorCore420.turn(new int[] {anchorX, 0, anchorZ}, turn);
            // Pasting nowhere near the origin, so nothing is right by accident.
            final int px = 100;
            final int py = 70;
            final int pz = -40;
            Map<String, String> pasted = new HashMap<>();
            for (Map.Entry<String, String> e : turned.entrySet()) {
                String[] p = e.getKey().split("/");
                pasted.put(key(Integer.parseInt(p[0]) + px, Integer.parseInt(p[1]) + py,
                        Integer.parseInt(p[2]) + pz), e.getValue());
            }
            final int ax = px + movedAnchor[0];
            final int az = pz + movedAnchor[2];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cases++;
                    final int fx = ax + dx;
                    final int fz = az + dz;
                    NpcGraphAnchorCore420.BlockLookup look = (a, b, c) -> pasted
                            .getOrDefault(key(fx + a, py + b, fz + c), "minecraft:air");
                    NpcGraphAnchorCore420.Placement p =
                            NpcGraphAnchorCore420.place(offsets, blocks, look, 5, 3);
                    // The feet drifted by (dx, dz), so the build's cell is that much back.
                    if (p.dx != -dx || p.dy != 0 || p.dz != -dz || p.turn != turn) wrong++;
                }
            }
        }
        expect("every turn of every pasted build lands on its own cell with its own turn ("
                + cases + " cases)", wrong == 0);
        if (wrong != 0) System.out.println("  wrong placements: " + wrong + " of " + cases);
    }

    private static String key(int x, int y, int z) {
        return x + "/" + y + "/" + z;
    }

    // ---- 5. an unsealed marker is an ordinary marker -----------------------

    private static void emptyAnchorWritesNothing() {
        CompoundTag empty = NpcGraphAnchorCore420.write(null, null, null);
        expect("no entries means no list", empty.isEmpty());
        expect("an unsealed marker has no graphs", !NpcGraphAnchorCore420.hasGraphs(empty));
        expect("nothing to materialise from an unsealed marker",
                NpcGraphAnchorCore420.entries(empty).isEmpty());
        expect("a random compound tag is not an anchor",
                !NpcGraphAnchorCore420.hasGraphs(new CompoundTag()));
    }

    // ---- 6. Build 437: placeWithTurn fallback & gather radius coverage -------

    private static void placeWithTurnFallback() {
        List<int[]> offsets = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        // 10 fingerprint blocks; 6 will be replaced (e.g. edited room / lapis->nether_bricks)
        for (int i = 1; i <= 10; i++) {
            offsets.add(new int[] {i, 0, 0});
            blocks.add("minecraft:nether_bricks");
        }
        // Rotated by turns=1 ((x,z) -> (-z,x) => +i on Z), and feet drifted by dy=-1
        Map<String, String> world = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            world.put(key(0, 1, i), "minecraft:nether_bricks");
        }
        NpcGraphAnchorCore420.BlockLookup look = (dx, dy, dz) -> world.getOrDefault(key(dx, dy, dz), "minecraft:air");
        NpcGraphAnchorCore420.Placement strict = NpcGraphAnchorCore420.place(offsets, blocks, look, 5, 3);
        expect("4/10 agreement misses strict 60% place()", !strict.found());

        NpcGraphAnchorCore420.Placement fb =
                NpcGraphAnchorCore420.placeWithTurn(offsets, blocks, look, 1, 5, 3);
        expect("placeWithTurn recovers exact turn=1 and dy=1 shift on 4/10 match",
                fb.turn == 1 && fb.dx == 0 && fb.dy == 1 && fb.dz == 0 && fb.found == 4);

        NpcGraphAnchorCore420.Placement emptyFp = NpcGraphAnchorCore420.placeWithTurn(
                new ArrayList<>(), new ArrayList<>(), look, 3, 5, 3);
        expect("placeWithTurn with empty fingerprint stays at (0,0,0) with known turn=3",
                emptyFp.turn == 3 && emptyFp.dx == 0 && emptyFp.dy == 0 && emptyFp.dz == 0);
    }

    private static void gatherCoverage() {
        BlockPos anchor = new BlockPos(0, 0, 0);
        expect("distance 30 is inside radius 32 (no 24..32 dead zone)",
                NpcGraphAnchorCore420.inside(30, 0, -28, anchor, 32));
        expect("distance 32 is inside radius 32",
                NpcGraphAnchorCore420.inside(-32, 32, 0, anchor, 32));
        expect("distance 33 is outside radius 32",
                !NpcGraphAnchorCore420.inside(33, 0, 0, anchor, 32));
    }

    // ---- bookkeeping --------------------------------------------------------

    private static void expect(String what, boolean ok) {
        checks++;
        if (!ok) {
            failed++;
            System.out.println("FAIL " + what);
        }
    }
}
