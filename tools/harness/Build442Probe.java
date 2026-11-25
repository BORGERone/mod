import dev.settledlands.chest.LootPool436;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Build 442 verification probe: exercises the exact scenario from user feedback (tiny rarity=1
 * filler items must NEVER artificially spike to 100% after heavy drops consume crate weight), plus
 * exact effective chance calculation under node rarity & crate-weight limits.
 */
public final class Build442Probe {

    private static int passed = 0;

    public static void main(String[] args) {
        testTinyFillerRarityDoesNotSpikeInCappedCrate();
        testExactEffectiveChancesWithAndWithoutNodeLimits();
        testBlockedReasonAndCounts();
        System.out.println("Build442Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testTinyFillerRarityDoesNotSpikeInCappedCrate() {
        // Exact items & rarities/weights from user's screenshot:
        // steak (rarity 1, weight 1), iron ingot (rarity 20, weight 50),
        // diamond (rarity 80, weight 90), iron sword (rarity 35, weight 25)
        List<LootPool436.PoolEntry> pool = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:cooked_beef", 1.0D, 1.0D, 2, 6),
                new LootPool436.PoolEntry("minecraft:iron_ingot", 20.0D, 50.0D, 1, 4),
                new LootPool436.PoolEntry("minecraft:diamond", 80.0D, 90.0D, 1, 1),
                new LootPool436.PoolEntry("minecraft:iron_sword", 35.0D, 25.0D, 1, 1)
        );
        Random rnd = new Random(44201L);

        // Case 1: crate capacity 100 (all 4 items fit individually) & rolls = 3
        LootPool436.Parsed crate100 = new LootPool436.Parsed("p.txt", "", "100", "3");
        Map<String, Integer> counts = new HashMap<>();
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(pool, crate100, rnd);
            double sumW = 0.0D;
            for (LootPool436.Drop d : drops) {
                counts.merge(d.itemId, 1, Integer::sum);
                switch (d.itemId) {
                    case "minecraft:cooked_beef" -> sumW += 1.0D;
                    case "minecraft:iron_ingot" -> sumW += 50.0D;
                    case "minecraft:diamond" -> sumW += 90.0D;
                    case "minecraft:iron_sword" -> sumW += 25.0D;
                }
            }
            check(sumW <= 100.0D + 1e-6D, "crate 100 total weight respected (got " + sumW + ")");
        }
        int steak = counts.getOrDefault("minecraft:cooked_beef", 0);
        int ingot = counts.getOrDefault("minecraft:iron_ingot", 0);
        int sword = counts.getOrDefault("minecraft:iron_sword", 0);
        int diamond = counts.getOrDefault("minecraft:diamond", 0);

        check(diamond > sword && sword > ingot && ingot > steak * 3,
                "strict drop ordering diamond > sword > ingot >> steak even in weight-limited crate 100 ("
                        + diamond + " > " + sword + " > " + ingot + " >> " + steak + ")");
        check(steak < 500, "rarity-1 steak stays genuinely rare (~220 out of 30000 rolls), got " + steak);

        // Case 2: unbounded crate weight (blank) & rolls = 3 -> exact 1:20:80:35 ratios
        counts.clear();
        LootPool436.Parsed unbounded = new LootPool436.Parsed("p.txt", "", "", "3");
        for (int i = 0; i < trials; i++) {
            List<LootPool436.Drop> drops = LootPool436.rollDrops(pool, unbounded, rnd);
            check(drops.size() == 3, "unbounded rolls=3 always drops 3 items");
            for (LootPool436.Drop d : drops) {
                counts.merge(d.itemId, 1, Integer::sum);
            }
        }
        int uSteak = counts.getOrDefault("minecraft:cooked_beef", 0);
        int uDiamond = counts.getOrDefault("minecraft:diamond", 0);
        check(uSteak > 120 && uSteak < 340, "unbounded steak ~0.74% of 30000 (~221), got " + uSteak);
        check(uDiamond > 17000 && uDiamond < 18300, "unbounded diamond ~58.8% of 30000 (~17647), got " + uDiamond);
    }

    private static void testExactEffectiveChancesWithAndWithoutNodeLimits() {
        List<LootPool436.PoolEntry> pool = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:cooked_beef", 1.0D, 1.0D, 2, 6),
                new LootPool436.PoolEntry("minecraft:iron_ingot", 20.0D, 50.0D, 1, 4),
                new LootPool436.PoolEntry("minecraft:diamond", 80.0D, 90.0D, 1, 1),
                new LootPool436.PoolEntry("minecraft:iron_sword", 35.0D, 25.0D, 1, 1)
        );

        // Unbounded spec: 1/136, 20/136, 80/136, 35/136
        double[] base = LootPool436.computeEffectiveChancesPct(pool, null);
        check(Math.abs(base[0] - (100.0D / 136.0D)) < 1e-4D, "base steak 0.735%");
        check(Math.abs(base[1] - (2000.0D / 136.0D)) < 1e-4D, "base ingot 14.706%");
        check(Math.abs(base[2] - (8000.0D / 136.0D)) < 1e-4D, "base diamond 58.824%");
        check(Math.abs(base[3] - (3500.0D / 136.0D)) < 1e-4D, "base sword 25.735%");

        // User's node spec (weight 0-30):
        // w=0 (1/31): nothing fits (0%)
        // w=1..24 (24/31): only steak fits -> steak 100%
        // w=25..30 (6/31): steak (1/36) + sword (35/36) fit; ingot(50) and diamond(90) = 0%
        LootPool436.Parsed node0to30 = new LootPool436.Parsed("p.txt", "", "0-30", "3");
        double[] eff = LootPool436.computeEffectiveChancesPct(pool, node0to30);
        double expectedSteak = (24.0D / 31.0D + (6.0D / 31.0D) * (1.0D / 36.0D)) * 100.0D;
        double expectedSword = ((6.0D / 31.0D) * (35.0D / 36.0D)) * 100.0D;
        check(Math.abs(eff[0] - expectedSteak) < 1e-4D, "effective steak ~77.96%, got " + eff[0]);
        check(Math.abs(eff[1]) < 1e-9D, "blocked ingot effective chance == 0%");
        check(Math.abs(eff[2]) < 1e-9D, "blocked diamond effective chance == 0%");
        check(Math.abs(eff[3] - expectedSword) < 1e-4D, "effective sword ~18.82%, got " + eff[3]);

        // Rarity cap + null slots inside rowEntries handled safely
        List<LootPool436.PoolEntry> withNull = Arrays.asList(
                pool.get(0),
                null,
                pool.get(2),
                pool.get(3)
        );
        LootPool436.Parsed capRar = new LootPool436.Parsed("p.txt", "0-50", "", "3");
        double[] effCap = LootPool436.computeEffectiveChancesPct(withNull, capRar);
        check(effCap.length == 4 && effCap[1] == 0.0D, "null placeholder entry gets 0%");
        check(Math.abs(effCap[2]) < 1e-9D, "diamond rarity 80 > max 50 gives 0%");
        check(effCap[3] > base[3], "sword effective chance rises when diamond is capped out");
    }

    private static void testBlockedReasonAndCounts() {
        LootPool436.PoolEntry steak = new LootPool436.PoolEntry("minecraft:cooked_beef", 1.0D, 1.0D, 2, 6);
        LootPool436.PoolEntry diamond = new LootPool436.PoolEntry("minecraft:diamond", 80.0D, 90.0D, 1, 1);
        LootPool436.Parsed weightCap30 = new LootPool436.Parsed("p.txt", "", "0-30", "3");
        LootPool436.Parsed rarityCap50 = new LootPool436.Parsed("p.txt", "50", "", "3");

        check(LootPool436.blockedReason(steak, weightCap30) == 0, "steak passes weight 0-30");
        check(LootPool436.blockedReason(diamond, weightCap30) == 2, "diamond blocked by weight 90 > 30");
        check(LootPool436.blockedReason(diamond, rarityCap50) == 1, "diamond blocked by rarity 80 > 50");
        check(LootPool436.blockedReason(new LootPool436.PoolEntry("x:y", 0.0D, 10.0D, 1, 1), null) == 3,
                "zero rarity marked invalid/blocked");
        check(LootPool436.countBlockedBySpec(Arrays.asList(steak, diamond, null), weightCap30) == 1,
                "countBlockedBySpec counts 1 blocked item");
    }
}
