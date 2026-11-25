import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Build 441 verification probe: exercises single-field count range parsing, CS:GO-style
 * rarity-driven drop probabilities (including decimals), crate total weight capacity limits
 * (e.g. crate weight 40 holds 2x20 swords or 4x10 books), and fractional rarity wire/file
 * round-trips.
 */
public final class Build441Probe {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testSingleFieldCountRangeParser();
        testCrateWeightCapacityLimits();
        testRarityDrivenCaseProbabilities();
        testFractionalRarityPersistenceAndWire();
        System.out.println("Build441Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testSingleFieldCountRangeParser() {
        int[] r1 = LootPool436.parseItemCountRange("1");
        check(r1 != null && r1[0] == 1 && r1[1] == 1, "single count 1");

        int[] r2 = LootPool436.parseItemCountRange("2-6");
        check(r2 != null && r2[0] == 2 && r2[1] == 6, "range count 2-6");

        int[] r3 = LootPool436.parseItemCountRange(" 12 - 4 ");
        check(r3 != null && r3[0] == 4 && r3[1] == 12, "reversed range normalized");

        int[] r4 = LootPool436.parseItemCountRange("64");
        check(r4 != null && r4[0] == 64 && r4[1] == 64, "max stack 64 valid");

        check(LootPool436.parseItemCountRange("") == null, "empty rejected");
        check(LootPool436.parseItemCountRange("0") == null, "zero rejected");
        check(LootPool436.parseItemCountRange("65") == null, "over 64 rejected");
        check(LootPool436.parseItemCountRange("2-") == null, "incomplete trailing dash rejected");
        check(LootPool436.parseItemCountRange("-6") == null, "leading dash rejected");
        check(LootPool436.parseItemCountRange("1-2-3") == null, "double dash rejected");
    }

    private static void testCrateWeightCapacityLimits() {
        Random rnd = new Random(44101L);

        // User's exact example 1: crate weight 40, swords weight 20 -> exactly 2 swords fit.
        List<LootPool436.PoolEntry> swordsOnly = Collections.singletonList(
                new LootPool436.PoolEntry("minecraft:iron_sword", 100.0D, 20.0D, 1, 1)
        );
        LootPool436.Parsed crate40UnlimitedRolls = new LootPool436.Parsed("p.txt", "", "40", "");
        List<LootPool436.Drop> swordDrops = LootPool436.rollDrops(swordsOnly, crate40UnlimitedRolls, rnd);
        check(swordDrops.size() == 2, "crate weight 40 fits exactly 2 swords of weight 20 (got "
                + swordDrops.size() + ")");

        // User's exact example 2: crate weight 40, books weight 10 -> exactly 4 books fit.
        List<LootPool436.PoolEntry> booksOnly = Collections.singletonList(
                new LootPool436.PoolEntry("minecraft:book", 100.0D, 10.0D, 1, 1)
        );
        List<LootPool436.Drop> bookDrops = LootPool436.rollDrops(booksOnly, crate40UnlimitedRolls, rnd);
        check(bookDrops.size() == 4, "crate weight 40 fits exactly 4 books of weight 10 (got "
                + bookDrops.size() + ")");

        // Mixed pool with crate weight 40: heavy items cannot overflow 40; remainder fills with light items.
        List<LootPool436.PoolEntry> mixed = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:iron_sword", 50.0D, 20.0D, 1, 1),
                new LootPool436.PoolEntry("minecraft:book", 50.0D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("minecraft:netherite_block", 25.0D, 45.0D, 1, 1) // too heavy for 40
        );
        int hitFull40 = 0;
        for (int trial = 0; trial < 300; trial++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(mixed, crate40UnlimitedRolls, rnd);
            double sumW = 0.0D;
            for (LootPool436.Drop d : drops) {
                check(!"minecraft:netherite_block".equals(d.itemId),
                        "weight 45 never fits inside crate capacity 40");
                sumW += "minecraft:iron_sword".equals(d.itemId) ? 20.0D : 10.0D;
            }
            check(sumW > 0.0D && sumW <= 40.0D + 1e-6D,
                    "mixed crate never exceeds capacity 40 (got " + sumW + ")");
            if (Math.abs(sumW - 40.0D) < 1e-6D) hitFull40++;
        }
        check(hitFull40 > 50, "mixed crate cleanly reaches full capacity 40 in many trials (" + hitFull40 + ")");

        // Crate weight 40 + explicit rolls cap 3 -> stops at 3 drops or weight 40 (whichever hits first)
        LootPool436.Parsed crate40Cap3 = new LootPool436.Parsed("p.txt", "", "40", "3");
        List<LootPool436.Drop> cappedBooks = LootPool436.rollDrops(booksOnly, crate40Cap3, rnd);
        check(cappedBooks.size() == 3, "explicit rolls=3 stops at 3 books even when crate holds 40");
        List<LootPool436.Drop> cappedSwords = LootPool436.rollDrops(swordsOnly, crate40Cap3, rnd);
        check(cappedSwords.size() == 2, "crate weight=40 stops at 2 swords even when rolls=3");
    }

    private static void testRarityDrivenCaseProbabilities() {
        // CS:GO-style case weights in rarity column; weight column intentionally equal/irrelevant to chance
        // because crate capacity is blank (unbounded).
        List<LootPool436.PoolEntry> csCase = Arrays.asList(
                new LootPool436.PoolEntry("case:blue", 79.92D, 10.0D, 1, 1),
                new LootPool436.PoolEntry("case:purple", 15.98D, 50.0D, 1, 1),
                new LootPool436.PoolEntry("case:pink", 3.2D, 5.0D, 1, 1),
                new LootPool436.PoolEntry("case:red", 0.64D, 1.0D, 1, 1),
                new LootPool436.PoolEntry("case:gold", 0.26D, 1.0D, 1, 1)
        );
        Random rnd = new Random(44102L);
        LootPool436.Parsed spec = new LootPool436.Parsed("case.txt", "", "", "1");

        Map<String, Integer> counts = new HashMap<>();
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> d = LootPool436.rollDrops(csCase, spec, rnd);
            check(d.size() == 1, "1 drop per case opening");
            counts.merge(d.get(0).itemId, 1, Integer::sum);
        }
        int blue = counts.getOrDefault("case:blue", 0);
        int purple = counts.getOrDefault("case:purple", 0);
        int pink = counts.getOrDefault("case:pink", 0);
        int red = counts.getOrDefault("case:red", 0);
        int gold = counts.getOrDefault("case:gold", 0);

        check(blue > 15_000 && blue < 17_000, "blue (~79.92%) count in expected range: " + blue);
        check(purple > 2_800 && purple < 3_600, "purple (~15.98%) count in expected range: " + purple);
        check(pink > 500 && pink < 820, "pink (~3.2%) count in expected range: " + pink);
        check(red > 70 && red < 200, "red (~0.64%) count in expected range: " + red);
        check(gold > 15 && gold < 100 && gold < red, "gold (~0.26%) rarest drop: " + gold);
        check(blue > purple && purple > pink && pink > red && red > gold,
                "strict probability ordering driven by rarity (" + blue + " > " + purple + " > "
                        + pink + " > " + red + " > " + gold + ")");

        // When crate capacity is unbounded and book rarity=80 vs sword rarity=20 (even if book weight=30
        // and sword weight=1), rarity strictly decides 80% vs 20%.
        List<LootPool436.PoolEntry> bookVsSword = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:book", 80.0D, 30.0D, 1, 1),
                new LootPool436.PoolEntry("minecraft:iron_sword", 20.0D, 1.0D, 1, 1)
        );
        int books = 0;
        int swords = 0;
        for (int i = 0; i < 4000; i++) {
            List<LootPool436.Drop> d = LootPool436.rollDrops(bookVsSword, spec, rnd);
            if ("minecraft:book".equals(d.get(0).itemId)) books++;
            else swords++;
        }
        check(books > 3000 && swords > 650 && books > swords * 3,
                "rarity 80 beats rarity 20 regardless of item weight (" + books + " vs " + swords + ")");
    }

    private static void testFractionalRarityPersistenceAndWire() throws Exception {
        List<LootPool436.PoolEntry> entries = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:cooked_beef", 79.92D, 5.0D, 2, 6),
                new LootPool436.PoolEntry("minecraft:diamond", 0.26D, 20.0D, 1, 1)
        );
        String text = LootPool436.formatPoolFile("cs_case.txt", entries);
        check(text.contains("79.92 | 5 | 2-6") && text.contains("0.26 | 20 | 1"),
                "file format preserves fractional rarity & compact count");

        List<LootPool436.PoolEntry> parsed = LootPool436.parseLines(Arrays.asList(text.split("\n")));
        check(parsed.size() == 2
                && Math.abs(parsed.get(0).rarity - 79.92D) < 1e-6D
                && Math.abs(parsed.get(1).rarity - 0.26D) < 1e-6D,
                "parseLines reads fractional rarity cleanly");

        String wire = LootPool436.encodeEntriesWire(entries);
        List<LootPool436.PoolEntry> decoded = LootPool436.decodeEntriesWire(
                ("OK\ncs_case.txt\n" + wire).split("\n", -1), 2);
        check(decoded.size() == 2
                && Math.abs(decoded.get(0).rarity - 79.92D) < 1e-6D
                && Math.abs(decoded.get(1).rarity - 0.26D) < 1e-6D,
                "wire round-trip preserves fractional rarity");

        // Save to disk via LootPoolManager436 + reload
        Path dir = LootPoolManager436.ensureDirectory();
        check(Files.isDirectory(dir), "pool dir created");
        String saved = LootPoolManager436.savePool("cs_case_441", entries);
        check("cs_case_441.txt".equals(saved), "saved fractional rarity pool");
        List<LootPool436.PoolEntry> onDisk = LootPoolManager436.loadPoolQuiet("cs_case_441.txt");
        check(onDisk.size() == 2 && Math.abs(onDisk.get(1).rarity - 0.26D) < 1e-6D,
                "disk reload preserves 0.26 rarity");
        LootPoolManager436.deletePool("cs_case_441.txt");

        // Ensure README_FORMAT.txt.disabled was updated with crate weight & rarity docs when freshly created
        Path guide = dir.resolve("README_FORMAT.txt.disabled");
        if (Files.isRegularFile(guide)) {
            String guideText = Files.readString(guide, StandardCharsets.UTF_8);
            check(guideText.contains("rarity_chance") || guideText.contains("weight"),
                    "README guide text present");
        }
    }
}
