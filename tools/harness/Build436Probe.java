import dev.settledlands.chest.ChestDiegesisCompat436;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.dungeon.DungeonTemplate425;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.LootPoolCard436;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Build 436 verification probe: exercises LootPool436 rules, file parsing, weighted rolling,
 * NpcDialogueGraph serialization & wiring, BlockMenu363 chest mode, DungeonTemplate425 direction
 * rotation, LootPoolManager436 directory creation/listing, and Diegesis/RoomyContainer reflection.
 */
public final class Build436Probe {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testNodeSerializationAndSanitization();
        testWiringAndRandomSelection();
        testRangeAndLineParsing();
        testWeightedAndCapDistribution();
        testBlockMenuChestMode();
        testDirectionPropertyRotation();
        testDirectoryCreationAndLoading();
        testDiegesisAndRoomyCompat();
        System.out.println("Build436Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testNodeSerializationAndSanitization() {
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node lp = g.add(NpcDialogueGraph.TRIGGER, 100, 120);
        check(!LootPool436.isLootPoolNode(lp), "plain trigger is not loot pool");
        LootPool436.applyDefaults(lp);
        check(LootPool436.isLootPoolNode(lp), "applyDefaults makes loot pool node");
        check("".equals(LootPool436.poolFileOf(lp)), "default pool empty");
        check("3".equals(LootPool436.rawRolls(lp)), "default rolls 3");

        LootPool436.setPoolFile(lp, "/custom/dir/loot_tier1.txt");
        check("loot_tier1.txt".equals(LootPool436.poolFileOf(lp)), "directory prefix stripped");
        check("".equals(LootPool436.sanitizeFileName("../secret.txt")), "dot-dot filename rejected");
        LootPool436.setPoolFile(lp, "hell_armory.txt");
        LootPool436.setRarity(lp, "20-75");
        LootPool436.setWeight(lp, "50");
        LootPool436.setRolls(lp, "2-4");

        CompoundTag saved = g.save();
        NpcDialogueGraph reloaded = NpcDialogueGraph.load(saved);
        NpcDialogueGraph.Node back = reloaded.get(lp.id);
        check(LootPool436.isLootPoolNode(back), "round-trip keeps loot pool node identity");
        check("hell_armory.txt".equals(LootPool436.poolFileOf(back)), "round-trip pool file");
        check("20-75".equals(LootPool436.rawRarity(back)), "round-trip rarity");
        check("50".equals(LootPool436.rawWeight(back)), "round-trip weight");
        check("2-4".equals(LootPool436.rawRolls(back)), "round-trip rolls");

        // Legacy 3-field payload without rolls defaults rolls to 3
        LootPool436.Parsed p3 = LootPool436.parse("trigger:loot_pool:old.txt|30|80");
        check(p3 != null && "old.txt".equals(p3.poolFile)
                && "30".equals(p3.rawRarity)
                && "80".equals(p3.rawWeight)
                && "3".equals(p3.rawRolls), "3-field legacy payload keeps default rolls");

        // Stripped action detection (used by NpcActions#trigger)
        check(LootPool436.isLootPoolAction("trigger:loot_pool:a.txt|||1"), "full prefix detected");
        check(LootPool436.isStrippedLootPoolAction("loot_pool:a.txt|||1"), "stripped prefix detected");
        check(!LootPool436.isLootPoolAction("trigger:teleport:minecraft:overworld|||"), "teleport ignored");
    }

    private static void testWiringAndRandomSelection() {
        // Note: standalone JVM probe runs without Sponge Mixin runtime transformation, so
        // LootPoolWiringMixin436 bytecode/targets are verified by build436_check + verify_jar while
        // here we verify stock node link persistence (next/falseNext/randomBranches) and
        // disconnectOutputs/LootPool436 identity across save/load.
        NpcDialogueGraph g = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(g);
        NpcDialogueGraph.Node lp1 = g.add(NpcDialogueGraph.TRIGGER, 200, 100);
        NpcDialogueGraph.Node lp2 = g.add(NpcDialogueGraph.TRIGGER, 400, 100);
        NpcDialogueGraph.Node line = g.add(NpcDialogueGraph.LINE, 600, 100);
        NpcDialogueGraph.Node rndNode = g.add(NpcDialogueGraph.RANDOM, 200, 300);
        NpcDialogueGraph.Node tp = g.add(NpcDialogueGraph.TRIGGER, 400, 300);
        LootPool436.applyDefaults(lp1);
        LootPool436.applyDefaults(lp2);
        TeleportNode424.applyDefaults(tp);

        card.next = lp1.id;
        check(g.connect(lp1.id, line.id, 0), "connect loot pool -> line");
        check(lp1.next == line.id, "loot pool next preserved");

        rndNode.randomBranches.add(lp1.id);
        rndNode.randomBranches.add(lp2.id);
        lp2.next = tp.id;

        CompoundTag saved = g.save();
        NpcDialogueGraph reloaded = NpcDialogueGraph.load(saved);
        check(reloaded.get(card.id).next == lp1.id, "card -> lp1 survived save/load");
        check(reloaded.get(lp1.id).next == line.id, "lp1 -> line survived save/load");
        check(reloaded.get(lp2.id).next == tp.id, "lp2 -> teleport survived save/load");
        check(reloaded.get(rndNode.id).randomBranches.size() == 2
                && reloaded.get(rndNode.id).randomBranches.get(0) == lp1.id
                && reloaded.get(rndNode.id).randomBranches.get(1) == lp2.id,
                "random branches to loot pools survived save/load");

        reloaded.disconnectOutputs(lp1.id);
        check(reloaded.get(lp1.id).next == -1, "disconnectOutputs clears lp1.next");
    }

    private static void testRangeAndLineParsing() {
        check(LootPool436.isRangeInput(""), "empty range input valid");
        check(LootPool436.isRangeInput("0-100"), "dash range valid");
        check(LootPool436.isRangeInput("10..50"), "dotdot range valid");
        check(!LootPool436.isRangeInput("abc"), "letters rejected");

        LootPool436.Range rEmpty = LootPool436.parseRange("", 1000);
        check(rEmpty.unbounded, "blank range is unbounded");

        LootPool436.Range rSwap = LootPool436.parseRange("80-20", 1000);
        check(!rSwap.unbounded && rSwap.min == 20 && rSwap.max == 80, "reversed range normalized");

        LootPool436.Range rClamp = LootPool436.parseRange("999999", 54);
        check(!rClamp.unbounded && rClamp.min == 54 && rClamp.max == 54, "rolls clamped to 54");

        List<LootPool436.PoolEntry> entries = LootPool436.parseLines(Arrays.asList(
                "# full line comment",
                "// another comment",
                "",
                "minecraft:apple | 10 | 80 | 2-5",
                "minecraft:iron_sword | 35 | 40 # inline comment",
                "minecraft:diamond | 90 | 5.5 | 1",
                "bad_line_without_namespace | 10 | 10",
                "minecraft:stone | 5 | 0",
                "minecraft:gold_ingot | 45 | 25 | 6-2"
        ));
        check(entries.size() == 4, "parsed exactly 4 valid entries");
        check("minecraft:apple".equals(entries.get(0).itemId)
                && entries.get(0).minCount == 2 && entries.get(0).maxCount == 5, "apple range 2-5");
        check("minecraft:iron_sword".equals(entries.get(1).itemId)
                && entries.get(1).rarity == 35 && Math.abs(entries.get(1).weight - 40.0) < 1e-9
                && entries.get(1).minCount == 1 && entries.get(1).maxCount == 1, "default count 1 + inline comment");
        check("minecraft:diamond".equals(entries.get(2).itemId)
                && Math.abs(entries.get(2).weight - 5.5) < 1e-9, "decimal weight 5.5");
        check("minecraft:gold_ingot".equals(entries.get(3).itemId)
                && entries.get(3).minCount == 2 && entries.get(3).maxCount == 6, "swapped count 6-2 -> 2-6");
    }

    private static void testWeightedAndCapDistribution() {
        List<LootPool436.PoolEntry> pool = Arrays.asList(
                new LootPool436.PoolEntry("mod:common", 25, 10.0, 2, 4),
                new LootPool436.PoolEntry("mod:uncommon", 5, 15.0, 1, 1),
                new LootPool436.PoolEntry("mod:rare", 80, 5.0, 1, 1),
                new LootPool436.PoolEntry("mod:heavy_cheap", 15, 120.0, 1, 1)
        );
        Random rnd = new Random(436L);

        // 1. Strict rarity<=30, crate weight<=90: excludes mod:rare (rarity 80) and mod:heavy_cheap (weight 120)
        //    Build 445 direct % without duplicate draws per entry: common (25%) drops ~5x more often than uncommon (5%).
        LootPool436.Parsed strict = new LootPool436.Parsed("p.txt", "30", "90", "5");
        int commonCnt = 0;
        int uncommonCnt = 0;
        for (int trial = 0; trial < 400; trial++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(pool, strict, rnd);
            check(drops.size() <= 2, "no duplicate draws beyond 2 eligible entries");
            double usedWeight = 0.0D;
            for (LootPool436.Drop d : drops) {
                check(!"mod:rare".equals(d.itemId), "rarity > cap never drops");
                check(!"mod:heavy_cheap".equals(d.itemId), "weight > crate capacity never drops");
                if ("mod:common".equals(d.itemId)) {
                    commonCnt++;
                    usedWeight += 10.0D;
                    check(d.count >= 2 && d.count <= 4, "common count in [2,4]");
                } else if ("mod:uncommon".equals(d.itemId)) {
                    uncommonCnt++;
                    usedWeight += 15.0D;
                    check(d.count == 1, "uncommon count == 1");
                }
            }
            check(usedWeight <= 90.0D + 1e-6D, "total crate weight never exceeds 90");
        }
        check(commonCnt > uncommonCnt * 2 && commonCnt >= 70 && uncommonCnt >= 8,
                "direct % roll favors rarity 25 over 5 (" + commonCnt + " vs " + uncommonCnt + ")");

        // 2. Range caps + range rolls
        LootPool436.Parsed ranged = new LootPool436.Parsed("p.txt", "20-90", "10-100", "2-4");
        for (int trial = 0; trial < 200; trial++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(pool, ranged, rnd);
            check(drops.size() <= 4, "ranged rolls never exceed upper bound 4");
            for (LootPool436.Drop d : drops) {
                check(!"mod:heavy_cheap".equals(d.itemId), "max weight 100 still blocks 120");
            }
        }

        // 3. Guaranteed range rolls when crate holds 4 distinct 100%-chance lightweight entries inside min weight 10
        List<LootPool436.PoolEntry> guaranteedPool = Arrays.asList(
                new LootPool436.PoolEntry("mod:always_fits_1", 100, 2.0, 1, 1),
                new LootPool436.PoolEntry("mod:always_fits_2", 100, 2.0, 1, 1),
                new LootPool436.PoolEntry("mod:always_fits_3", 100, 2.0, 1, 1),
                new LootPool436.PoolEntry("mod:always_fits_4", 100, 2.0, 1, 1),
                new LootPool436.PoolEntry("mod:heavy", 15, 120.0, 1, 1)
        );
        LootPool436.Parsed guaranteedSpec = new LootPool436.Parsed("p.txt", "100", "10-100", "2-4");
        for (int trial = 0; trial < 200; trial++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(guaranteedPool, guaranteedSpec, rnd);
            check(drops.size() >= 2 && drops.size() <= 4, "guaranteed ranged rolls stay inside [2,4]");
        }
    }

    private static void testBlockMenuChestMode() {
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.left = 0; canvas.top = 0; canvas.right = 800; canvas.bottom = 600;
        NpcGraphContextMenu menu = new NpcGraphContextMenu();
        menu.open = true;
        menu.targetNodeId = -1;
        menu.left = 40;
        menu.top = 40;
        menu.width = 160;

        int baseLen = BlockMenu363.rows().length;

        // Block mode keeps count == baseLen + 1 (preserves Teleport424Probe contract)
        BlockMenu363.fit(menu, canvas, false, false);
        check(BlockMenu363.count() == baseLen + 1, "block mode count == rows + 1");
        check(BlockMenu363.isTeleportRow(baseLen), "teleport row at baseLen");
        check(!BlockMenu363.isLootPoolRow(baseLen + 1), "loot pool disabled in block mode");

        // Chest mode exposes Loot Pool row right after Teleport
        BlockMenu363.fit(menu, canvas, false, true);
        check(BlockMenu363.count() == baseLen + 2, "chest mode count == rows + 2");
        check(BlockMenu363.isTeleportRow(baseLen), "teleport row unchanged in chest mode");
        check(BlockMenu363.isLootPoolRow(baseLen + 1), "loot pool row enabled at baseLen+1");
        check(BlockMenu363.colour(baseLen + 1) == LootPool436.ACCENT_COLOR, "loot pool accent colour");

        // Restore default non-chest state at end
        BlockMenu363.reset();
        BlockMenu363.fit(menu, canvas, false, false);
    }

    private static void testDirectionPropertyRotation() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("direction", "north");
        props.put("open", "false");
        String[] expected = {"north", "east", "south", "west"};
        for (int turns = 0; turns < 4; turns++) {
            Map<String, String> out = DungeonTemplate425.rotateProps(props, turns, "refurbished_furniture:oak_drawer");
            check(expected[turns].equals(out.get("direction")),
                    "direction rotation turns=" + turns + " -> " + out.get("direction"));
            check("false".equals(out.get("open")), "non-directional props preserved");
        }
    }

    private static void testDirectoryCreationAndLoading() throws Exception {
        // Working dir is set to an isolated temp dir by build436_check.py so we exercise real disk I/O
        Path dir = LootPoolManager436.ensureDirectory();
        check(Files.isDirectory(dir), "pool directory auto-created");
        check(Files.isRegularFile(dir.resolve("README_FORMAT.txt.disabled")),
                "README_FORMAT.txt.disabled auto-created in empty dir");
        check(LootPoolManager436.listPools().isEmpty(), ".disabled guide is NOT listed as a .txt pool");

        Files.writeString(dir.resolve("b_rare.txt"),
                "# comment\nminecraft:diamond | 85 | 15 | 1\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("a_common.txt"),
                "minecraft:apple | 5 | 70 | 2-4\nminecraft:bread | 10 | 50 | 1\n",
                StandardCharsets.UTF_8);

        List<String> pools = LootPoolManager436.listPools();
        check(pools.size() == 2 && "a_common.txt".equals(pools.get(0)) && "b_rare.txt".equals(pools.get(1)),
                "listPools sorted & .txt filtered");

        List<LootPool436.PoolEntry> loaded = LootPoolManager436.loadPool("a_common.txt");
        check(loaded.size() == 2, "loadPool parsed 2 entries");
        check(LootPoolManager436.loadPool("../escape.txt").isEmpty(), "loadPool blocks path traversal");

        // Dropdown merges server + local without duplicates
        LootPool436.setServerPools(Arrays.asList("server_only.txt", "a_common.txt"));
        List<String> opts = LootPoolCard436.poolOptions("missing_saved.txt");
        check("".equals(opts.get(0)), "dropdown index 0 is blank none option");
        check(opts.contains("a_common.txt") && opts.contains("b_rare.txt")
                && opts.contains("server_only.txt") && opts.contains("missing_saved.txt"),
                "dropdown merges server, local and saved custom file");
    }

    private static void testDiegesisAndRoomyCompat() {
        // Check graceful no-crash behavior and RoomyContainer reflection path
        Container fakePlain = new Container() {
            @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
            @Override public int getContainerSize() { return 162; }
            @Override public void setItem(int slot, ItemStack stack) {}
            @Override public int getMaxStackSize() { return 64; }
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
        };
        int cap = ChestDiegesisCompat436.usableSlotCount(new Object(), fakePlain);
        // When RoomyContainer is absent from classpath, cap falls back to 162; if RoomyContainer mock is
        // present on classpath (see Build436CompatProbe below), RoomyContainer subclass returns ownSize!
        check(cap == 162, "plain container fallback slot count");
        check(!ChestDiegesisCompat436.isStationOpen(null, null, null), "null station check safe");
    }
}
