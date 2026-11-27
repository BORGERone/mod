#!/usr/bin/env python3
"""
Gate (Build 450): Comprehensive bytecode, localization, mixin zero-lambda, trialtuner
compatibility, and live JVM probe verification for Build 450:
  - RangeSpec450 pure parser/formatter/sampler/input-filters
  - TrialConfig446 range fields, SpawnEntry/RewardEntry ranges, toResolvedJson -> trialtuner SpawnerConfig
  - TrialMobPool449 weight ranges, midpoint %, wave rolling, wire encoding
  - TrialModule447 & TrialModuleCard447 range persistence & card summaries
  - TrialGraph446 hasRanges() guard protecting ranges on live spawner sync
  - GraphRanges450 stock node ranges ("rs450:...") & reroll/IF interval membership & NpcDialogueGraph NBT/error
  - TrialMobCatalogScreen449 compact multi-row geometry on 240p/270p/360p/540p scaled heights
  - Mixin ordering, zero-lambda enforcement across Build 447..450 mixins, RU/EN lang parity & key coverage
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
TRIALTUNER_JAR = pathlib.Path("/tmp/trialtuner/trialtuner-0.1.0-fixed-v8.1.jar")

REQUIRED_CLASSES = (
    "dev/settledlands/trial/RangeSpec450.class",
    "dev/settledlands/trial/RangeSpec450$IntRange.class",
    "dev/settledlands/trial/RangeSpec450$FloatRange.class",
    "dev/settledlands/trial/TrialConfig446.class",
    "dev/settledlands/trial/TrialMobPool449.class",
    "dev/settledlands/trial/TrialModule447.class",
    "dev/settledlands/trial/TrialSpawnerBridge446.class",
    "dev/settledlands/trial/TrialControlActions446.class",
    "dev/settledlands/trial/TrialGraph446.class",
    "dev/settledlands/npc/GraphRanges450.class",
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.class",
    "dev/settledlands/npc/client/TrialMobPoolEditorScreen449.class",
    "dev/settledlands/npc/client/TrialModuleClient447.class",
    "dev/settledlands/npc/client/TrialModuleCard447.class",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.class",
    "dev/settledlands/npc/client/GraphRangeUi450.class",
    "dev/settledlands/npc/client/GraphRangeUi450$Host.class",
    "dev/settledlands/npc/client/GraphTooltips448.class",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.class",
    "dev/settledlands/mixin/GraphRangeInputs450Mixin.class",
    "dev/settledlands/mixin/GuideRange450Mixin.class",
    "dev/settledlands/mixin/TriggerRange450Mixin.class",
    "dev/settledlands/mixin/RadiusRange450Mixin.class",
)

ZERO_LAMBDA_MIXINS = (
    "dev/settledlands/mixin/TrialModuleInspector447Mixin",
    "dev/settledlands/mixin/GraphCardFooter448Mixin",
    "dev/settledlands/mixin/AutoAiFooter448Mixin",
    "dev/settledlands/mixin/GraphUiWidgets448Mixin",
    "dev/settledlands/mixin/GraphDropdownHover448Mixin",
    "dev/settledlands/mixin/GraphHover448Mixin",
    "dev/settledlands/mixin/GraphRangeInputs450Mixin",
    "dev/settledlands/mixin/GuideRange450Mixin",
    "dev/settledlands/mixin/TriggerRange450Mixin",
    "dev/settledlands/mixin/RadiusRange450Mixin",
)

NEW_KEYS = (
    "gui.settledlands.trial.cfg.delay",
    "gui.settledlands.graph.relation_range",
    "gui.settledlands.graph.points_range",
    "gui.settledlands.tip448.tf.delay",
    "gui.settledlands.tip448.cfg.rew_count",
    "gui.settledlands.tip448.fld.relation_range",
    "gui.settledlands.tip448.fld.points_range",
)

PROBE_JAVA = r"""
import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.TrialMobCatalogScreen449;
import dev.settledlands.npc.client.TrialModuleCard447;
import dev.settledlands.npc.client.TrialModuleClient447;
import dev.settledlands.trial.RangeSpec450;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialMobPool449;
import dev.settledlands.trial.TrialModule447;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class Build450Probe {
    private static int checks = 0;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("check failed (#" + checks + "): " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. RangeSpec450 parsing, normalization, formatting, containment, filters
        RangeSpec450.IntRange r1 = RangeSpec450.parseInt("20-40", 1, 1, 1, 12000);
        check(r1.min == 20 && r1.max == 40 && r1.isRange(), "basic 20-40 failed");
        check("20-40".equals(r1.format()), "format 20-40 mismatch");

        RangeSpec450.IntRange rInv = RangeSpec450.parseInt("40..20", 1, 1, 1, 12000);
        check(rInv.min == 20 && rInv.max == 40, "inverted 40..20 must normalize");

        RangeSpec450.IntRange rNeg = RangeSpec450.parseInt("-10--2", 0, 0, -1000, 1000);
        check(rNeg.min == -10 && rNeg.max == -2 && rNeg.isRange(), "negative -10--2 failed");
        check(rNeg.contains(-5) && !rNeg.contains(-1) && !rNeg.contains(-11), "contains failed on negative range");

        RangeSpec450.IntRange rSingle = RangeSpec450.parseInt("15", 1, 1, 1, 100);
        check(rSingle.min == 15 && rSingle.max == 15 && !rSingle.isRange(), "single int mismatch");
        check("15".equals(rSingle.format()), "single format must not emit hyphen");

        RangeSpec450.FloatRange fr = RangeSpec450.parseFloat("1,2~2.5", 1.5F, 1.5F, 1.0F, 4.0F);
        check(Math.abs(fr.min - 1.2F) < 1e-4F && Math.abs(fr.max - 2.5F) < 1e-4F && fr.isRange(), "float range mismatch");
        check("1.2-2.5".equals(fr.format()), "float format mismatch: " + fr.format());

        check(RangeSpec450.isIntRangeInput("20-40", false), "valid uint range rejected");
        check(!RangeSpec450.isIntRangeInput("-5", false), "leading minus should be rejected when allowNegative=false");
        check(RangeSpec450.isIntRangeInput("-5-5", true), "signed range should be accepted");
        check(!RangeSpec450.isIntRangeInput("12abc", true), "alpha junk rejected");
        check(RangeSpec450.isFloatRangeInput("1.25-2.5"), "float range filter failed");

        Random rnd = new Random(1337L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            int picked = r1.pick(rnd);
            check(picked >= 20 && picked <= 40, "picked out of [20..40]: " + picked);
            seen.add(picked);
        }
        check(seen.size() > 5, "random picker looks degenerate");

        // 2. TrialConfig446 backward-compatible legacy JSON vs ranged JSON + toResolvedJson
        TrialConfig446 legacy = new TrialConfig446();
        String legacyJson = legacy.toJson();
        check(!legacyJson.contains("\"ranges\""), "single-value config must omit ranges object");
        check(!legacy.hasRanges(), "fresh default config must not report hasRanges");

        TrialConfig446 cfg = new TrialConfig446();
        cfg.setIntFieldWithRange("totalMobs", "4-10", 6, 1, 256);
        cfg.setIntFieldWithRange("simultaneousMobs", "2-5", 3, 1, 64);
        cfg.setIntFieldWithRange("spawnBatchSize", "1-3", 2, 1, 8);
        cfg.setIntFieldWithRange("activationRange", "10-18", 14, 1, 64);
        cfg.setIntFieldWithRange("spawnRange", "3-7", 4, 1, 16);
        cfg.setIntFieldWithRange("cooldownTicks", "1200-3600", 3600, 0, 720000);
        cfg.setIntFieldWithRange("totalMobsAddedPerPlayer", "2-4", 2, 0, 64);
        cfg.setIntFieldWithRange("simultaneousMobsAddedPerPlayer", "1-2", 1, 0, 32);
        cfg.setIntFieldWithRange("selfDestructAfterWaves", "3-6", 3, 1, 100);
        cfg.setIntFieldWithRange("selfDestructChancePercent", "25-75", 50, 0, 100);
        cfg.setFloatFieldWithRange("ominousMobMultiplier", "1.2-2.2", 1.5F, 1.0F, 4.0F);
        cfg.setIntFieldWithRange("ominousHazardIntervalTicks", "100-220", 160, 20, 12000);
        cfg.minSpawnDelayTicks = 20;
        cfg.maxSpawnDelayTicks = 40;
        cfg.mobs.get(0).weight = 5;
        cfg.mobs.get(0).weightRange = "5-15";
        cfg.rewards.add(new TrialConfig446.RewardEntry("minecraft:diamond", 1, 3, 10, 50));
        cfg.rewards.get(0).weightRange = "10-25";
        cfg.rewards.get(0).chanceRange = "40-80";

        check(cfg.hasRanges(), "ranged config must report hasRanges=true");
        String rangedJson = cfg.toJson();
        check(rangedJson.contains("\"ranges\""), "ranged JSON must preserve ranges map");
        check(rangedJson.contains("weightRange"), "ranged JSON must preserve SpawnEntry/RewardEntry weightRange");
        check(rangedJson.contains("chanceRange"), "ranged JSON must preserve RewardEntry chanceRange");

        TrialConfig446 round = TrialConfig446.fromJson(rangedJson);
        check("4-10".equals(round.rangeOrIntText("totalMobs", round.totalMobs)), "totalMobs range lost on round-trip");
        check("1.2-2.2".equals(round.rangeOrFloatText("ominousMobMultiplier", round.ominousMobMultiplier)),
                "ominousMobMultiplier range lost on round-trip");
        check("5-15".equals(round.mobs.get(0).weightRange), "mob weightRange lost on round-trip");
        check("10-25".equals(round.rewards.get(0).weightRange), "reward weightRange lost on round-trip");
        check("40-80".equals(round.rewards.get(0).chanceRange), "reward chanceRange lost on round-trip");

        TrialConfig446 copied = round.copy();
        check("4-10".equals(copied.rangeOrIntText("totalMobs", copied.totalMobs)), "copy lost ranges");

        // Verify every field in TrialConfig446 toResolvedJson output is pure trialtuner JSON
        for (int i = 0; i < 40; i++) {
            TrialConfig446 rolled = cfg.resolveRanges(rnd);
            check(!rolled.hasRanges(), "resolved config must be scalar only");
            check(rolled.totalMobs >= 4 && rolled.totalMobs <= 10, "rolled totalMobs out of range");
            check(rolled.simultaneousMobs >= 2 && rolled.simultaneousMobs <= 5, "rolled simMobs out of range");
            check(rolled.ominousMobMultiplier >= 1.2F - 1e-4F && rolled.ominousMobMultiplier <= 2.2F + 1e-4F,
                    "rolled mult out of range: " + rolled.ominousMobMultiplier);
            check(rolled.mobs.get(0).weight >= 5 && rolled.mobs.get(0).weight <= 15,
                    "rolled mob weight out of range");
            check(rolled.rewards.get(0).weight >= 10 && rolled.rewards.get(0).weight <= 25,
                    "rolled reward weight out of range");
            check(rolled.rewards.get(0).chancePercent >= 40 && rolled.rewards.get(0).chancePercent <= 80,
                    "rolled reward chance out of range");
            String resolvedJson = cfg.toResolvedJson(rnd);
            check(!resolvedJson.contains("\"ranges\"")
                            && !resolvedJson.contains("\"weightRange\"")
                            && !resolvedJson.contains("\"chanceRange\""),
                    "resolved JSON must contain only vanilla trialtuner keys");
        }

        // 3. TrialMobPool449 weight ranges
        List<TrialConfig446.SpawnEntry> poolEntries = TrialMobPool449.parseLines(Arrays.asList(
                "minecraft:zombie | 5-15",
                "minecraft:breeze | 20"
        ));
        check(poolEntries.size() == 2, "poolEntries expected 2");
        check("5-15".equals(poolEntries.get(0).weightRange) && poolEntries.get(0).weight == 5,
                "pool weightRange parse failed");
        double[] pct = TrialMobPool449.computeEffectiveChancesPct(poolEntries);
        // midpoint(5-15)=10, 20 -> 33.3% / 66.7%
        check(Math.abs(pct[0] - 33.333D) < 0.2D && Math.abs(pct[1] - 66.667D) < 0.2D,
                "pool midpoint % mismatch: " + pct[0] + ", " + pct[1]);
        String poolWire = TrialMobPool449.encodeEntriesWire(poolEntries);
        List<TrialConfig446.SpawnEntry> poolRound = TrialMobPool449.decodeEntriesWire(poolWire.split("\n"), 0);
        check("5-15".equals(poolRound.get(0).weightRange), "pool wire lost weightRange");
        List<TrialMobPool449.WavePick> wave = TrialMobPool449.rollWavePreview(poolEntries, 15, rnd);
        int sumPicks = 0;
        for (TrialMobPool449.WavePick wp : wave) sumPicks += wp.count;
        check(sumPicks == 15, "pool wave preview roll count mismatch");

        // 4. TrialModule447 range persistence & applyToConfig / writeFromConfig
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node hub = g.add(TrialGraph446.TRIAL_TYPE, 0, 0);
        NpcDialogueGraph.Node waveMod = g.add(NpcDialogueGraph.TRIGGER, -150, 0);
        TrialModule447.applyDefaults(waveMod, TrialModule447.SLOT_WAVE);
        NpcDialogueGraph.Node timeMod = g.add(NpcDialogueGraph.TRIGGER, -150, 80);
        TrialModule447.applyDefaults(timeMod, TrialModule447.SLOT_TIMING);
        NpcDialogueGraph.Node omMod = g.add(NpcDialogueGraph.TRIGGER, -150, 160);
        TrialModule447.applyDefaults(omMod, TrialModule447.SLOT_OMINOUS);
        g.connect(waveMod.id, hub.id);
        g.connect(timeMod.id, hub.id);
        g.connect(omMod.id, hub.id);

        TrialModule447.WaveData wd = TrialModule447.readWave(waveMod);
        wd.totalMobs = 4;
        wd.totalMobsText = "4-10";
        wd.simultaneousMobs = 2;
        wd.simultaneousMobsText = "2-4";
        TrialModule447.writeWave(waveMod, wd);

        TrialModule447.TimingData td = TrialModule447.readTiming(timeMod);
        td.minSpawnDelayTicks = 20;
        td.maxSpawnDelayTicks = 50;
        td.cooldownTicks = 1200;
        td.cooldownTicksText = "1200-2400";
        TrialModule447.writeTiming(timeMod, td);

        TrialModule447.OminousData od = TrialModule447.readOminous(omMod);
        od.mobMultiplier = 1.2F;
        od.mobMultiplierText = "1.2-2.0";
        TrialModule447.writeOminous(omMod, od);

        TrialModule447.syncCardConfig(g);
        TrialConfig446 synced = TrialConfig446.fromJson(hub.action);
        check(synced.hasRanges(), "synced hub config must retain ranges from connected modules");
        check("4-10".equals(synced.rangeOrIntText("totalMobs", synced.totalMobs)), "synced totalMobs range mismatch");
        check(synced.minSpawnDelayTicks == 20 && synced.maxSpawnDelayTicks == 50, "synced delay bounds mismatch");
        check("1200-2400".equals(synced.rangeOrIntText("cooldownTicks", synced.cooldownTicks)), "synced cooldown range mismatch");
        check("1.2-2".equals(synced.rangeOrFloatText("ominousMobMultiplier", synced.ominousMobMultiplier)),
                "synced multiplier range mismatch: " + synced.rangeOrFloatText("ominousMobMultiplier", synced.ominousMobMultiplier));
        String[] waveCard = TrialModuleCard447.cardLines(waveMod);
        check(waveCard != null && waveCard.length == 3
                        && "gui.settledlands.trial.mod.card_total".equals(waveCard[0]),
                "wave card line 0 mismatch");
        check("4-10".equals(TrialModuleClient447.rangeText(wd.totalMobs, wd.totalMobsText)),
                "TrialModuleClient447.rangeText mismatch");
        check("20-50".equals(TrialModuleClient447.delayText(td)),
                "TrialModuleClient447.delayText mismatch");
        check("1.2-2.0".equals(TrialModuleClient447.floatRangeText(od.mobMultiplier, od.mobMultiplierText)),
                "TrialModuleClient447.floatRangeText mismatch");

        // 5. GraphRanges450 on stock NpcDialogueGraph nodes
        NpcDialogueGraph gStock = new NpcDialogueGraph();
        NpcDialogueGraph.Node ifN = gStock.add(NpcDialogueGraph.IF, 100, 0);
        ifN.varOp = 0; // ==
        ifN.pointsName = "score";
        GraphRanges450.setRangeText(ifN, GraphRanges450.KEY_IF_COMPARE, "5-15", 0, -1000000, 1000000);
        check(GraphRanges450.isRangeMarker(ifN.text), "IF range marker missing");
        check(GraphRanges450.inRange(ifN, GraphRanges450.KEY_IF_COMPARE, 5), "IF boundary 5 must match");
        check(GraphRanges450.inRange(ifN, GraphRanges450.KEY_IF_COMPARE, 15), "IF boundary 15 must match");
        check(!GraphRanges450.inRange(ifN, GraphRanges450.KEY_IF_COMPARE, 16), "IF outside 16 must fail");

        // Clearing back to single scalar clears node.text to ""
        int sVal = GraphRanges450.setRangeText(ifN, GraphRanges450.KEY_IF_COMPARE, "10", 0, -1000000, 1000000);
        check(sVal == 10 && "".equals(ifN.text), "scalar input must restore empty node.text");

        // Re-set IF range + VARIABLE + TIMER + CHECK + DAMAGE + TIME + RADIUS
        GraphRanges450.setRangeText(ifN, GraphRanges450.KEY_IF_COMPARE, "5-15", 0, -1000000, 1000000);
        NpcDialogueGraph.Node varN = gStock.add(NpcDialogueGraph.VARIABLE, 100, 60);
        varN.pointsName = "score";
        varN.varValue = GraphRanges450.setRangeText(varN, GraphRanges450.KEY_VAR_VALUE, "-3-7", 0, -1000000, 1000000);
        NpcDialogueGraph.Node tmrN = gStock.add(NpcDialogueGraph.TIMER, 100, 120);
        tmrN.timerInterval = GraphRanges450.setRangeText(tmrN, GraphRanges450.KEY_TIMER_INTERVAL, "4-12", 5, 1, 86400);
        NpcDialogueGraph.Node chkN = gStock.add(NpcDialogueGraph.CHECK, 100, 180);
        chkN.checkRadius = GraphRanges450.setRangeText(chkN, GraphRanges450.KEY_CHECK_RADIUS, "3-9", 4, 1, 64);
        chkN.checkCooldown = GraphRanges450.setRangeText(chkN, GraphRanges450.KEY_CHECK_COOLDOWN, "5-15", 5, 0, 3600);
        NpcDialogueGraph.Node dmgN = gStock.add(NpcDialogueGraph.DAMAGE, 100, 240);
        dmgN.minDamage = GraphRanges450.setRangeText(dmgN, GraphRanges450.KEY_MIN_DAMAGE, "2-8", 1, 0, 1000);
        NpcDialogueGraph.Node timN = gStock.add(NpcDialogueGraph.TIME, 100, 300);
        timN.timeTicks = GraphRanges450.setRangeText(timN, GraphRanges450.KEY_TIME_TICKS, "1000-4000", 1000, 0, 23999);
        NpcDialogueGraph.Node radN = gStock.add(NpcDialogueGraph.RADIUS, 100, 360);
        radN.radius = GraphRanges450.setRangeText(radN, GraphRanges450.KEY_RADIUS, "4-10", 5, 1, 64);

        for (int i = 0; i < 30; i++) {
            GraphRanges450.rerollGraph(gStock, rnd);
            check(varN.varValue >= -3 && varN.varValue <= 7, "varValue out of [-3..7]");
            check(tmrN.timerInterval >= 4 && tmrN.timerInterval <= 12, "timerInterval out of [4..12]");
            check(chkN.checkRadius >= 3 && chkN.checkRadius <= 9, "checkRadius out of [3..9]");
            check(chkN.checkCooldown >= 5 && chkN.checkCooldown <= 15, "checkCooldown out of [5..15]");
            check(dmgN.minDamage >= 2 && dmgN.minDamage <= 8, "minDamage out of [2..8]");
            check(timN.timeTicks >= 1000 && timN.timeTicks <= 4000, "timeTicks out of [1000..4000]");
            check(radN.radius >= 4 && radN.radius <= 10, "radius out of [4..10]");
        }
        NpcDialogueGraph gCopy = gStock.copy();
        check(GraphRanges450.hasRange(gCopy.get(tmrN.id), GraphRanges450.KEY_TIMER_INTERVAL),
                "NpcDialogueGraph.copy() must preserve node.text ranges");
        String stockErr = gStock.error();
        check(stockErr == null || stockErr.isEmpty(),
                "NpcDialogueGraph.error() must accept rs450 markers, got: " + stockErr);

        // 6. TrialMobCatalogScreen449 multi-row geometry on common scaled resolutions
        // Mirrors computeLayout math: panelH = height - 8, gridH = panelH - 37 - 14 - 4, rowStep = 49
        int[] heights = { 240, 270, 360, 540 };
        for (int h : heights) {
            int panelH = Math.max(150, h - 8);
            int gridH = Math.max(92, panelH - 37 - 14 - 4);
            int rows = Math.max(2, (gridH + 3) / (46 + 3));
            check(rows >= 3, "MobCatalog at scaled height " + h + " must fit >=3 rows, got " + rows);
        }

        System.out.println("BUILD450_CHECKS=" + checks);
        System.out.println("BUILD450_OK=true");
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
    extra_cp = ""
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

        probe_src = td / "Build450Probe.java"
        probe_src.write_text(PROBE_JAVA, encoding="utf-8")
        r2 = subprocess.run(
            [java, "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}{extra_cp}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Build450Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path.resolve()}:{stubs_out}{extra_cp}", "Build450Probe"],
            cwd=str(td), capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD450_OK=true" not in r3.stdout:
            raise SystemExit("Build450Probe execution failed:\n" + r3.stdout + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build450_check.py <jar>", file=sys.stderr)
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

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build450-universal-ranges-and-compact-mob-catalog" not in toml:
            errors.append("neoforge.mods.toml missing Build 450 version")

        mixins_doc = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common_mixins = mixins_doc.get("mixins", [])
        client_mixins = mixins_doc.get("client", [])
        for cm in ("GuideRange450Mixin", "TriggerRange450Mixin", "RadiusRange450Mixin"):
            if cm not in common_mixins:
                errors.append(f"{cm} missing from common mixins")
        if not client_mixins or client_mixins[0] != "NpcDropdownFirst333":
            errors.append("client[0] must be NpcDropdownFirst333")
        if not client_mixins or client_mixins[-1] != "GraphHover448Mixin":
            errors.append("client[-1] must be GraphHover448Mixin")
        if "GraphRangeInputs450Mixin" not in client_mixins:
            errors.append("GraphRangeInputs450Mixin missing from client mixins")
        elif client_mixins.index("GraphRangeInputs450Mixin") > client_mixins.index("GraphHover448Mixin"):
            errors.append("GraphRangeInputs450Mixin must precede GraphHover448Mixin")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_MIXINS:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        cat_cf = zf.read("dev/settledlands/npc/client/TrialMobCatalogScreen449.class")
        if b"enableScissor" not in cat_cf or b"drawScaledText" not in cat_cf:
            errors.append("TrialMobCatalogScreen449 missing scissor/scaled-text improvements")

        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in NEW_KEYS:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing new Build 450 key: {k}")

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
    if TRIALTUNER_JAR.is_file():
        with zipfile.ZipFile(TRIALTUNER_JAR) as tzf:
            tt_cls = check_mixin_class.load_classes(tzf, [
                "dev/arena/trialtuner/config/SpawnerConfig.class",
                "dev/arena/trialtuner/config/SpawnerConfig$SpawnEntry.class",
                "dev/arena/trialtuner/config/SpawnerConfig$RewardEntry.class",
            ])
            top_cf = tt_cls.get("dev/arena/trialtuner/config/SpawnerConfig")
            sp_cf = tt_cls.get("dev/arena/trialtuner/config/SpawnerConfig$SpawnEntry")
            rw_cf = tt_cls.get("dev/arena/trialtuner/config/SpawnerConfig$RewardEntry")
            top_names = {f.name for f in top_cf.fields} if top_cf else set()
            for reqf in (
                "mode", "activationRange", "spawnRange", "totalMobs", "totalMobsAddedPerPlayer",
                "simultaneousMobs", "simultaneousMobsAddedPerPlayer", "spawnBatchSize",
                "minSpawnDelayTicks", "maxSpawnDelayTicks", "cooldownTicks", "scaleWithPlayers",
                "selfDestruct", "selfDestructAfterWaves", "selfDestructChancePercent",
                "ominousMobMultiplier", "ominousEquipment", "ominousHazards", "ominousKeyConversion",
                "ominousHazardIntervalTicks", "mobs", "rewards", "onWaveClearCommands",
                "onDestroyCommands", "noBreakEnabled", "noBreakRadius", "noBreakSeconds", "noBreakAmplifier"
            ):
                if reqf not in top_names:
                    errors.append(f"trialtuner SpawnerConfig missing field {reqf}")
            sp_names = {f.name for f in sp_cf.fields} if sp_cf else set()
            if not {"entityId", "weight"}.issubset(sp_names):
                errors.append("trialtuner SpawnEntry missing expected fields")
            rw_names = {f.name for f in rw_cf.fields} if rw_cf else set()
            if not {"itemId", "weight", "minCount", "maxCount", "chancePercent"}.issubset(rw_names):
                errors.append("trialtuner RewardEntry missing expected fields")
    if errors:
        print("build450_check FAILED:", file=sys.stderr)
        for e in errors:
            print("  -", e, file=sys.stderr)
        return 1

    print(probe_out)
    print("build450_check: PASS", jar_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
