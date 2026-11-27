#!/usr/bin/env python3
"""
Gate (Build 449): Comprehensive bytecode, localization, mixin zero-lambda, and live JVM probe
verification for Build 449:
  - Named Trial Mob Pools (config/settledlands/mob_pools/*.txt) & network channel registration
  - Shared Trial Reward Pools (config/settledlands/loot_pools/*.txt) with LootPoolEditorScreen440
  - Compact dropdown + 20x20 gear + 12x12 inline row-gear inspectors for Mob & Reward modules
  - 3D Modpack Mob Catalog & safe InventoryScreen preview sizing / category helpers
  - Disk-authoritative refresh on graph open/save + 6 missing gui.settledlands.trial.cfg.* keys fixed
"""

import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import check_mixin_class

ROOT = pathlib.Path(__file__).resolve().parent.parent
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

REQUIRED_CLASSES = (
    "dev/settledlands/chest/ChestLootNet436.class",
    "dev/settledlands/trial/TrialMobPool449.class",
    "dev/settledlands/trial/TrialMobPool449$WavePick.class",
    "dev/settledlands/trial/TrialMobPoolManager449.class",
    "dev/settledlands/trial/TrialMobPoolNet449.class",
    "dev/settledlands/trial/TrialMobPoolNet449$Pools.class",
    "dev/settledlands/trial/TrialMobPoolNet449$PoolQuery.class",
    "dev/settledlands/trial/TrialMobPoolNet449$PoolData.class",
    "dev/settledlands/trial/TrialMobPoolNet449$PoolSave.class",
    "dev/settledlands/trial/TrialModule447.class",
    "dev/settledlands/trial/TrialGraph446.class",
    "dev/settledlands/npc/client/TrialMobPreview449.class",
    "dev/settledlands/npc/client/TrialMobPreview449$MobEntry.class",
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.class",
    "dev/settledlands/npc/client/TrialMobPoolEditorScreen449.class",
    "dev/settledlands/npc/client/LootPoolGearButton440.class",
    "dev/settledlands/npc/client/LootPoolCard436.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
    "dev/settledlands/npc/client/TrialModuleClient447.class",
    "dev/settledlands/npc/client/TrialModuleCard447.class",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.class",
    "dev/settledlands/npc/client/GraphTooltips448.class",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.class",
)

ZERO_LAMBDA_MIXINS = (
    "dev/settledlands/mixin/TrialModuleInspector447Mixin",
    "dev/settledlands/mixin/GraphCardFooter448Mixin",
    "dev/settledlands/mixin/AutoAiFooter448Mixin",
    "dev/settledlands/mixin/GraphUiWidgets448Mixin",
    "dev/settledlands/mixin/GraphDropdownHover448Mixin",
    "dev/settledlands/mixin/GraphHover448Mixin",
)

FIXED_447_KEYS = (
    "gui.settledlands.trial.cfg.ominous_equip",
    "gui.settledlands.trial.cfg.nobreak",
    "gui.settledlands.trial.cfg.destruct",
    "gui.settledlands.trial.cfg.nobreak_r",
    "gui.settledlands.trial.cfg.nobreak_s",
    "gui.settledlands.trial.cfg.nobreak_amp",
)

PROBE_JAVA = r"""
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.TrialMobPreview449;
import dev.settledlands.npc.client.TrialModuleCard447;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialMobPool449;
import dev.settledlands.trial.TrialMobPoolManager449;
import dev.settledlands.trial.TrialModule447;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class Build449Probe {
    private static int checks = 0;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("check failed (#" + checks + "): " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. Filename sanitization & normalization
        check("arena_wave_1".equals(TrialMobPool449.sanitizeFileName("arena_wave_1")),
                "sanitizeFileName should trim clean name");
        check("".equals(TrialMobPool449.sanitizeFileName("../evil.txt")),
                "sanitizeFileName must block traversal");
        check("dir.txt".equals(TrialMobPool449.sanitizeFileName("sub/dir.txt")),
                "sanitizeFileName should strip directory prefix");
        check("boss_arena.txt".equals(TrialMobPool449.normalizeSaveName("boss_arena")),
                "normalizeSaveName should add .txt");
        check("".equals(TrialMobPool449.normalizeSaveName(".txt")),
                "normalizeSaveName must reject bare .txt");
        check("mob_pool_3.txt".equals(TrialMobPool449.suggestNextPoolName(
                Arrays.asList("mob_pool_1.txt", "mob_pool_2.txt"))),
                "suggestNextPoolName should pick 3");

        // 2. Parsing, serialization, chance math, and wave rolling
        String[] sampleLines = {
            "# arena wave",
            "minecraft:zombie | 10",
            "breeze | 5",
            "bad line without colon or valid chars",
            "# comment",
            "spider | 0"
        };
        List<TrialConfig446.SpawnEntry> parsed = TrialMobPool449.parseLines(Arrays.asList(sampleLines));
        check(parsed.size() == 2, "expected 2 valid mob rows (weight 0 dropped), got " + parsed.size());
        check("minecraft:zombie".equals(parsed.get(0).entityId) && parsed.get(0).weight == 10,
                "row 0 mismatch");
        check("minecraft:breeze".equals(parsed.get(1).entityId) && parsed.get(1).weight == 5,
                "row 1 should normalize to minecraft:breeze");

        double[] pct = TrialMobPool449.computeEffectiveChancesPct(parsed);
        check(pct.length == 2 && Math.abs(pct[0] + pct[1] - 100.0D) < 0.01D,
                "effective chances must sum to 100%");
        check(Math.abs(pct[0] - 66.666D) < 0.1D, "expected 66.7%, got " + pct[0]);

        List<TrialMobPool449.WavePick> wave = TrialMobPool449.rollWavePreview(parsed, 12, new Random(42L));
        int rolledTotal = 0;
        for (TrialMobPool449.WavePick wp : wave) rolledTotal += wp.count;
        check(rolledTotal == 12, "rollWavePreview sum expected 12, got " + rolledTotal);

        String wire = TrialMobPool449.encodeEntriesWire(parsed);
        List<TrialConfig446.SpawnEntry> roundWire = TrialMobPool449.decodeEntriesWire(wire.split("\n"), 0);
        check(roundWire.size() == 2 && "minecraft:breeze".equals(roundWire.get(1).entityId),
                "wire encode/decode mismatch");

        // 3. Real disk CRUD for TrialMobPoolManager449 + LootPoolManager436 inside temp cwd
        check(TrialMobPoolManager449.savePool("../hack.txt", parsed).isEmpty(),
                "traversal savePool must fail");
        String savedName = TrialMobPoolManager449.savePool("arena_alpha", parsed);
        check("arena_alpha.txt".equals(savedName), "saved mob pool name mismatch: " + savedName);
        List<String> listM = TrialMobPoolManager449.listPools();
        check(listM.size() == 1 && "arena_alpha.txt".equals(listM.get(0)), "listPools mismatch");
        List<TrialConfig446.SpawnEntry> loadedM = TrialMobPoolManager449.loadPoolQuiet("arena_alpha.txt");
        check(loadedM.size() == 2 && "minecraft:breeze".equals(loadedM.get(1).entityId),
                "loadPoolQuiet mismatch");

        List<LootPool436.PoolEntry> lootSeed = Arrays.asList(
                new LootPool436.PoolEntry("minecraft:emerald", 100.0D, 10.0D, 2, 4),
                new LootPool436.PoolEntry("minecraft:trial_key", 50.0D, 5.0D, 1, 1));
        check("reward_alpha.txt".equals(LootPoolManager436.savePool("reward_alpha", lootSeed)),
                "savePool reward failed");

        // 4. TrialModule447 pool serialization, backwards compatibility, LootEntry round-trip & live refresh
        NpcDialogueGraph g = TrialGraph446.defaultGraph(TrialConfig446.defaults().toJson());
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(g);
        NpcDialogueGraph.Node mobMod = TrialModule447.connectedModule(g, hub, TrialModule447.SLOT_MOBS);
        NpcDialogueGraph.Node rewMod = TrialModule447.connectedModule(g, hub, TrialModule447.SLOT_REWARDS);
        check(mobMod != null && rewMod != null, "default modular graph must have mobs & rewards modules");

        // Legacy inline check first
        mobMod.action = "trial_mod:mobs:minecraft:creeper*7";
        check("".equals(TrialModule447.mobPoolFile(mobMod)), "legacy mobs: must have empty pool file");
        check("minecraft:creeper".equals(TrialModule447.readMobs(mobMod).get(0).entityId),
                "legacy mobs: parse failed");

        // Bind named mob & reward pools
        TrialModule447.writeMobPool(mobMod, "arena_alpha.txt", loadedM);
        check("arena_alpha.txt".equals(TrialModule447.mobPoolFile(mobMod)),
                "mobPoolFile mismatch: " + TrialModule447.mobPoolFile(mobMod));
        check(mobMod.action.startsWith("trial_mod:mobs_pool:arena_alpha.txt|"),
                "mob pool action prefix mismatch: " + mobMod.action);

        List<LootPool436.PoolEntry> lootEntries = LootPoolManager436.loadPoolQuiet("reward_alpha.txt");
        List<TrialConfig446.RewardEntry> conv = TrialModule447.fromLootEntries(lootEntries);
        check(conv.size() == 2 && conv.get(0).minCount == 2 && conv.get(0).maxCount == 4
                && conv.get(1).chancePercent == 50 && conv.get(1).weight == 5,
                "fromLootEntries conversion mismatch");
        List<LootPool436.PoolEntry> backLoot = TrialModule447.toLootEntries(conv);
        check(backLoot.size() == 2 && Math.abs(backLoot.get(1).rarity - 50.0D) < 0.01D,
                "toLootEntries round-trip mismatch");

        TrialModule447.writeRewardPool(rewMod, "reward_alpha.txt", conv);
        check("reward_alpha.txt".equals(TrialModule447.rewardPoolFile(rewMod)),
                "rewardPoolFile mismatch");
        check(rewMod.action.startsWith("trial_mod:rewards_pool:reward_alpha.txt|"),
                "reward pool action prefix mismatch");

        // Edit files on disk externally and verify TrialModule447.refreshPoolsFromServerDisk updates graph & compiled TrialConfig446!
        TrialMobPoolManager449.savePool("arena_alpha.txt", Arrays.asList(
                new TrialConfig446.SpawnEntry("minecraft:witch", 25)));
        LootPoolManager436.savePool("reward_alpha.txt", Arrays.asList(
                new LootPool436.PoolEntry("minecraft:nether_star", 100.0D, 99.0D, 1, 2)));

        boolean refreshed = TrialModule447.refreshPoolsFromServerDisk(g);
        check(refreshed, "refreshPoolsFromServerDisk should return true after disk edits");

        TrialConfig446 compiled = TrialModule447.compileConfig(g);
        check(compiled.mobs.size() == 1 && "minecraft:witch".equals(compiled.mobs.get(0).entityId)
                && compiled.mobs.get(0).weight == 25,
                "compiled mobs did not refresh from disk");
        check(compiled.rewards.size() == 1 && "minecraft:nether_star".equals(compiled.rewards.get(0).itemId)
                && compiled.rewards.get(0).maxCount == 2 && compiled.rewards.get(0).weight == 99,
                "compiled rewards did not refresh from disk");

        // Card summary lines distinguish pool-file mode from inline mode cleanly
        String[] mLines = TrialModuleCard447.cardLines(mobMod);
        check(mLines[0].contains("card_pool_file") || mLines[0].contains("arena_alpha.txt"),
                "mob card line 0 should use card_pool_file key, got: " + mLines[0]);
        String[] rLines = TrialModuleCard447.cardLines(rewMod);
        check(rLines[0].contains("card_pool_file") || rLines[0].contains("reward_alpha.txt"),
                "reward card line 0 should use card_pool_file key, got: " + rLines[0]);
        check(mLines[1].contains("witch"), "mob card line 1 should include refreshed mob witch, got: " + mLines[1]);
        check(rLines[1].contains("nether_star"),
                "reward card line 1 should include refreshed item nether_star, got: " + rLines[1]);

        TrialModule447.writeMobPool(mobMod, "", Arrays.asList(new TrialConfig446.SpawnEntry("minecraft:zombie", 4)));
        check(TrialModuleCard447.cardLines(mobMod)[0].contains("card_mob_inline"),
                "unlinked mob module should use card_mob_inline");

        // Delete pool check
        check(TrialMobPoolManager449.deletePool("arena_alpha.txt"), "deletePool failed");
        check(TrialMobPoolManager449.listPools().isEmpty(), "mob_pools dir should be empty after delete");

        // 5. Client catalog helpers & category filtering
        check("gui.settledlands.trial.mob_cat.cat_hostile".equals(
                TrialMobPreview449.categoryLabelKey("MONSTER")), "cat_hostile key");
        check("gui.settledlands.trial.mob_cat.cat_friendly".equals(
                TrialMobPreview449.categoryLabelKey("CREATURE")), "cat_friendly key");
        check("gui.settledlands.trial.mob_cat.cat_water".equals(
                TrialMobPreview449.categoryLabelKey("AXOLOTLS")), "cat_water key");
        check("gui.settledlands.trial.mob_cat.cat_misc".equals(
                TrialMobPreview449.categoryLabelKey("MISC")), "cat_misc key");

        TrialMobPreview449.MobEntry hostileMob = new TrialMobPreview449.MobEntry(
                "minecraft:breeze", "minecraft", "breeze", "Вихрь", "MONSTER", true);
        TrialMobPreview449.MobEntry friendlyMob = new TrialMobPreview449.MobEntry(
                "minecraft:allay", "minecraft", "allay", "Тихоня", "CREATURE", false);
        check(TrialMobPreview449.categoryMatches(hostileMob, TrialMobPreview449.CAT_ALL) == 1, "all filter");
        check(TrialMobPreview449.categoryMatches(hostileMob, TrialMobPreview449.CAT_HOSTILE) == 1, "hostile match");
        check(TrialMobPreview449.categoryMatches(friendlyMob, TrialMobPreview449.CAT_HOSTILE) == 0, "friendly reject");
        check(TrialMobPreview449.categoryMatches(friendlyMob, TrialMobPreview449.CAT_FRIENDLY) == 1, "friendly match");

        System.out.println("BUILD449_CHECKS=" + checks + " | BUILD449_OK=true");
    }
}
"""


def find_java() -> str:
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    try:
        import jdk4py  # type: ignore
        cand = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
        if cand.is_file():
            return str(cand)
    except Exception:
        pass
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java runtime found")


def run_probe(jar_path: pathlib.Path) -> str:
    java = find_java()
    stub_layers = (
        ROOT / "tools" / "api271",
        ROOT / "build303-src" / "stubs",
        ROOT / "build303-src" / "stubs345-src",
        ROOT / "build303-src" / "stubs396-src",
        ROOT / "build303-src" / "stubs420-src",
        ROOT / "build303-src" / "api-src",
        ROOT / "build303-src" / "api3-src",
        ROOT / "build303-src" / "api4-src",
        ROOT / "build303-src" / "api5-src",
        ROOT / "build303-src" / "api416-src",
        ROOT / "build303-src" / "stubs425-src",
        ROOT / "build303-src" / "stubs436-src",
        ROOT / "build303-src" / "stubs438-src",
        ROOT / "build303-src" / "stubs439-src",
        ROOT / "build303-src" / "stubs440-src",
        ROOT / "build303-src" / "stubs443-src",
        ROOT / "build303-src" / "stubs446-src",
        ROOT / "build303-src" / "stubs449-src",
    )
    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        probe_out = td / "probe_out"
        merged.mkdir()
        stubs_out.mkdir()
        probe_out.mkdir()
        for layer in stub_layers:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar_path), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("probe stub compile failed:\n" + r1.stdout + r1.stderr)

        probe_src = td / "Build449Probe.java"
        probe_src.write_text(PROBE_JAVA, encoding="utf-8")
        r2 = subprocess.run(
            [java, "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Build449Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path.resolve()}:{stubs_out}", "Build449Probe"],
            cwd=str(td), capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD449_OK=true" not in r3.stdout:
            raise SystemExit("Build449Probe execution failed:\n" + r3.stdout + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build449_check.py <jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        print(f"not found: {jar_path}", file=sys.stderr)
        return 2

    errors = []
    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())
        for req in REQUIRED_CLASSES:
            if req not in names:
                errors.append(f"missing class {req}")

        # Manifest / version
        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build449-named-trial-pools-and-3d-mob-catalog" not in toml:
            errors.append("neoforge.mods.toml missing Build 449 version")

        # Mixin ordering & zero-lambda rule
        mixins_doc = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        client_mixins = mixins_doc.get("client", [])
        if not client_mixins or client_mixins[0] != "NpcDropdownFirst333":
            errors.append("client[0] must be NpcDropdownFirst333")
        if not client_mixins or client_mixins[-1] != "GraphHover448Mixin":
            errors.append("client[-1] must be GraphHover448Mixin")
        if "TrialModuleInspector447Mixin" not in client_mixins:
            errors.append("TrialModuleInspector447Mixin missing from client mixins")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_MIXINS:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        # Verify popup z-translate in TrialMobPoolEditorScreen449
        pop_cf = zf.read("dev/settledlands/npc/client/TrialMobPoolEditorScreen449.class")
        if b"renderPoolPopup" not in pop_cf or b"pushPose" not in pop_cf:
            errors.append("TrialMobPoolEditorScreen449.renderPoolPopup missing PoseStack pushPose/z=500")

        # Localization parity + 6 fixed keys + 0 missing exact gui.settledlands.trial/tip448 keys
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in FIXED_447_KEYS:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing fixed Build 447/448 key: {k}")

        # Scan all class files for exact Build 449 / Trial / Tip448 lang references
        allowed_stems = (
            "gui.settledlands.desc448.card.node",
            "gui.settledlands.desc448.card.mod",
            "gui.settledlands.tip448.menu.node",
            "gui.settledlands.tip448.node.n",
            "gui.settledlands.tip448.node.mod",
            "gui.settledlands.tip448.pin.slot",
            "gui.settledlands.tip448.varop",
            "gui.settledlands.tip448.preset",
            "gui.settledlands.tip448.op",
            "gui.settledlands.tip448.hack.slider_diff",
            "gui.settledlands.door.rows",
        )
        for n in sorted(names):
            if not n.endswith(".class"):
                continue
            blob = zf.read(n)
            for m in re.finditer(rb"(?:gui|message)\.settledlands\.[a-zA-Z0-9_.]+", blob):
                s = m.group(0).decode("ascii")
                if s.endswith(".") or s.endswith("_") or s in allowed_stems:
                    continue
                if s not in ru or s not in en:
                    errors.append(f"missing lang key {s} referenced in {n}")

    probe_out = run_probe(jar_path)
    if errors:
        print("build449_check FAILED:", file=sys.stderr)
        for e in errors:
            print("  -", e, file=sys.stderr)
        return 1

    print(probe_out)
    print("build449_check: PASS", jar_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
