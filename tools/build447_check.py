#!/usr/bin/env python3
"""
Gate (Build 447): Comprehensive bytecode + live JVM verification for Build 447 (modular Trial
Spawner graph redesign layered atop Build 446).
"""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import anchor_check

ROOT = pathlib.Path(__file__).resolve().parent.parent
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
TRIAL_TUNER_JAR = pathlib.Path("/tmp/trialtuner/trialtuner-0.1.0-fixed-v8.1.jar")

REQUIRED_CLASSES = (
    # Core Build 446 classes still required
    "dev/settledlands/trial/TrialConfig446.class",
    "dev/settledlands/trial/TrialSpawnerBridge446.class",
    "dev/settledlands/trial/TrialGraph446Store.class",
    "dev/settledlands/trial/TrialGraph446.class",
    "dev/settledlands/trial/TrialSpawnerEvents446.class",
    "dev/settledlands/trial/TrialControlNode446.class",
    "dev/settledlands/trial/TrialControlActions446.class",
    "dev/settledlands/npc/GraphBlockCleanup446.class",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.class",
    "dev/settledlands/npc/client/TrialGraphClient446.class",
    "dev/settledlands/npc/client/TrialCard446.class",
    "dev/settledlands/npc/client/TrialControlCard446.class",
    # New Build 447 modular classes
    "dev/settledlands/trial/TrialModule447.class",
    "dev/settledlands/npc/client/TrialModuleCard447.class",
    "dev/settledlands/npc/client/TrialModuleClient447.class",
    "dev/settledlands/npc/client/TrialModulePickerScreen447.class",
    # Build 446 + 447 mixins
    "dev/settledlands/mixin/BlockDestroy446Mixin.class",
    "dev/settledlands/mixin/TrialGraphValidationMixin446.class",
    "dev/settledlands/mixin/NpcTrialOpen446Mixin.class",
    "dev/settledlands/mixin/NpcTrialSave446Mixin.class",
    "dev/settledlands/mixin/NpcTrialNoStats446Mixin.class",
    "dev/settledlands/mixin/NpcTrialNoAi446Mixin.class",
    "dev/settledlands/mixin/NpcTrialCard446Mixin.class",
    "dev/settledlands/mixin/NpcTrialRows446Mixin.class",
    "dev/settledlands/mixin/NpcTrialSocket446Mixin.class",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.class",
    "dev/settledlands/mixin/TrialControlActionsMixin446.class",
    "dev/settledlands/mixin/TrialControlWiringMixin446.class",
    "dev/settledlands/mixin/TrialControlGraphUiMixin446.class",
    "dev/settledlands/mixin/TrialControlInspectorMixin446.class",
    "dev/settledlands/mixin/TrialCanvasSize447Mixin.class",
    "dev/settledlands/mixin/TrialGraphCanvas447Mixin.class",
    "dev/settledlands/mixin/TrialModuleUi447Mixin.class",
    "dev/settledlands/mixin/TrialModuleSocket447Mixin.class",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.class",
    "dev/settledlands/mixin/compat/TrialSpawnerEntityMixin446.class",
    "dev/settledlands/mixin/compat/TrialTriggersMixin446.class",
)

PROBE_JAVA = r"""
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorGraph382Store;
import dev.settledlands.door.DoorStore368;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlockTriggerStore351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphAnchorCore420;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.CardEventPin398;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.npc.client.TrialModuleCard447;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialGraph446Store;
import dev.settledlands.trial.TrialModule447;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class Build447Probe {
    private static int checks = 0;

    private static void check(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError("FAILED: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. Disjoint editor ID ranges
        check(TrialGraph446.isTrialEditorId(TrialGraph446.ID_MAX), "trial ID_MAX inside range");
        check(TrialGraph446.isTrialEditorId(TrialGraph446.ID_MIN), "trial ID_MIN inside range");
        check(!TrialGraph446.isTrialEditorId(BlockTrigger351.ID_MAX), "block ID not trial");
        check(!TrialGraph446.isTrialEditorId(DoorGraph382.ID_MAX), "door ID not trial");
        check(!TrialGraph446.isTrialEditorId(ChestGraph396.ID_MAX), "chest ID not trial");

        // 2. TrialConfig446 JSON codec & sanitization
        TrialConfig446 cfg = TrialConfig446.defaults();
        cfg.mode = TrialConfig446.MODE_OMINOUS;
        cfg.scaleWithPlayers = false;
        cfg.activationRange = 22;
        cfg.spawnRange = 7;
        cfg.totalMobs = 18;
        cfg.totalMobsAddedPerPlayer = 5;
        cfg.simultaneousMobs = 6;
        cfg.simultaneousMobsAddedPerPlayer = 2;
        cfg.spawnBatchSize = 3;
        cfg.minSpawnDelayTicks = 15;
        cfg.maxSpawnDelayTicks = 55;
        cfg.cooldownTicks = 1200;
        cfg.mobs.add(new TrialConfig446.SpawnEntry("minecraft:breeze", 3));
        cfg.rewards.add(new TrialConfig446.RewardEntry("minecraft:diamond", 5, 1, 4, 50));
        cfg.ominousMobMultiplier = 2.25f;
        cfg.ominousEquipment = false;
        cfg.ominousHazards = true;
        cfg.ominousHazardIntervalTicks = 240;
        cfg.ominousKeyConversion = false;
        cfg.noBreakEnabled = false;
        cfg.noBreakRadius = 30;
        cfg.noBreakSeconds = 120;
        cfg.noBreakAmplifier = 2;
        cfg.selfDestruct = true;
        cfg.selfDestructAfterWaves = 5;
        cfg.selfDestructChancePercent = 65;
        cfg.onWaveClearCommands = "say wave;give @p minecraft:apple|extra";
        String json = cfg.toJson();

        // Cross-check SpawnerConfig presence & methods when TT JAR is on classpath
        if (args.length > 0 && "with_tt".equals(args[0])) {
            Class<?> scCls = Class.forName("dev.arena.trialtuner.config.SpawnerConfig",
                    false, Build447Probe.class.getClassLoader());
            boolean hasFrom = false;
            boolean hasTo = false;
            for (java.lang.reflect.Method m : scCls.getDeclaredMethods()) {
                if ("fromJson".equals(m.getName())) hasFrom = true;
                if ("toJson".equals(m.getName())) hasTo = true;
            }
            check(hasFrom && hasTo, "TT SpawnerConfig exposes fromJson & toJson");
        }

        // 3. Modular defaultGraph(json) creates hub + 8 connected module cards
        NpcDialogueGraph g = TrialGraph446.defaultGraph(json);
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(g);
        check(hub != null && hub.type == TrialGraph446.TRIAL_TYPE, "trial hub created");
        check(hub.varOp == 1, "hub marked as modular (varOp == 1)");
        check(g.nodes.size() == 9, "defaultGraph starts with 9 interconnected nodes (hub + 8 modules)");
        check(TrialModule447.connectedCount(g, hub) == 8, "all 8 modules wired to hub");

        for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
            NpcDialogueGraph.Node mod = TrialModule447.connectedModule(g, hub, s);
            check(mod != null, "slot " + s + " module present");
            check(mod.next == hub.id, "slot " + s + " wired to hub");
            check(mod.x + TrialModuleCard447.CARD_W <= hub.x, "slot " + s + " sits left of hub without overlap");
            check(!PursuitGraphNode272.isValueInputNode(mod), "slot " + s + " rejects blue value input pin");
        }

        // Compiled config matches input live config across every module field
        TrialConfig446 compiled = TrialModule447.compileConfig(g);
        check(TrialConfig446.MODE_OMINOUS.equals(compiled.mode) && !compiled.scaleWithPlayers, "compiled mode/scale");
        check(compiled.activationRange == 22 && compiled.spawnRange == 7, "compiled radius");
        check(compiled.totalMobs == 18 && compiled.totalMobsAddedPerPlayer == 5
                && compiled.simultaneousMobs == 6 && compiled.simultaneousMobsAddedPerPlayer == 2
                && compiled.spawnBatchSize == 3, "compiled wave");
        check(compiled.minSpawnDelayTicks == 15 && compiled.maxSpawnDelayTicks == 55
                && compiled.cooldownTicks == 1200, "compiled timing");
        check(compiled.mobs.size() == 2 && "minecraft:breeze".equals(compiled.mobs.get(1).entityId),
                "compiled mobs pool");
        int lastRew = compiled.rewards.size() - 1;
        check("minecraft:diamond".equals(compiled.rewards.get(lastRew).itemId)
                && compiled.rewards.get(lastRew).maxCount == 4
                && compiled.rewards.get(lastRew).chancePercent == 50, "compiled reward pool");
        check(Math.abs(compiled.ominousMobMultiplier - 2.25f) < 0.01f
                && !compiled.ominousEquipment && compiled.ominousHazards
                && compiled.ominousHazardIntervalTicks == 240 && !compiled.ominousKeyConversion,
                "compiled ominous module");
        check(!compiled.noBreakEnabled && compiled.noBreakRadius == 30 && compiled.noBreakSeconds == 120
                && compiled.noBreakAmplifier == 2 && compiled.selfDestruct
                && compiled.selfDestructAfterWaves == 5 && compiled.selfDestructChancePercent == 65,
                "compiled protect/self-destruct module");

        // 4. NBT save/load preserves modular topology + all module values
        CompoundTag savedNbt = g.save();
        NpcDialogueGraph loaded = NpcDialogueGraph.load(savedNbt);
        loaded = TrialGraph446.withTrialNode(loaded);
        NpcDialogueGraph.Node loadedHub = TrialGraph446.trialNode(loaded);
        check(loaded.nodes.size() == 9 && TrialModule447.connectedCount(loaded, loadedHub) == 8,
                "NBT round-trip keeps all 8 wired modules");
        TrialConfig446 loadedCompiled = TrialModule447.compileConfig(loaded);
        check(loadedCompiled.toJson().equals(compiled.toJson()), "NBT round-trip preserves compiled config");

        // 5. Connect/disconnect/swap/restore module wiring
        NpcDialogueGraph.Node extraMobs = loaded.add(NpcDialogueGraph.TRIGGER, 40, 620);
        TrialModule447.applyDefaults(extraMobs, TrialModule447.SLOT_MOBS);
        TrialModule447.writeMobs(extraMobs, List.of(new TrialConfig446.SpawnEntry("minecraft:blaze", 9)));
        check(TrialModule447.isModuleNode(extraMobs) && TrialModule447.slotOf(extraMobs) == TrialModule447.SLOT_MOBS,
                "new mobs module recognized");
        NpcDialogueGraph.Node oldMobs = TrialModule447.connectedModule(loaded, loadedHub, TrialModule447.SLOT_MOBS);
        check(TrialModule447.connectModule(loaded, extraMobs.id, loadedHub.id), "connectModule swaps slot");
        check(oldMobs.next == -1 && extraMobs.next == loadedHub.id, "previous mobs module unlinked");
        check("minecraft:blaze".equals(TrialGraph446.configOf(loadedHub).mobs.get(0).entityId),
                "hub config syncs immediately on module swap");

        // Disconnecting a module reverts that slot to defaults while keeping other modules intact
        check(TrialModule447.disconnectSlot(loaded, loadedHub.id, TrialModule447.SLOT_MOBS), "disconnectSlot works");
        check(TrialModule447.connectedCount(loaded, loadedHub) == 7, "7/8 connected after disconnect");
        check("minecraft:zombie".equals(TrialGraph446.configOf(loadedHub).mobs.get(0).entityId),
                "unplugged slot falls back to default mobs");
        check(TrialModule447.compileConfig(loaded).activationRange == 22,
                "other modules still active while mobs unplugged");

        // Deleting a module updates hub config and does NOT auto-recreate on withTrialNode (since varOp == 1)
        loaded.remove(extraMobs.id);
        loaded.remove(oldMobs.id);
        TrialGraph446.onNodeRemoved(loaded, oldMobs.id);
        TrialGraph446.withTrialNode(loaded);
        check(TrialModule447.connectedCount(loaded, loadedHub) == 7,
                "withTrialNode respects user-deleted module when varOp == 1");
        int restored = TrialModule447.createMissingModules(loaded, loadedHub, TrialModule447.compileConfig(loaded));
        check(restored == 1 && TrialModule447.connectedCount(loaded, loadedHub) == 8,
                "createMissingModules restores only missing slot");

        // 6. Legacy Build 446 single-node migration (varOp == 0 -> 9 modular nodes)
        NpcDialogueGraph legacy = new NpcDialogueGraph();
        legacy.enabled = true;
        NpcDialogueGraph.Node legacyCard = legacy.add(TrialGraph446.TRIAL_TYPE, 120, 120);
        legacyCard.varOp = 0;
        legacyCard.action = json;
        legacy.entry = legacyCard.id;
        legacy = TrialGraph446.withTrialNode(legacy);
        NpcDialogueGraph.Node migratedHub = TrialGraph446.trialNode(legacy);
        check(migratedHub.varOp == 1 && legacy.nodes.size() == 9, "legacy 1-node graph migrated to 9 nodes");
        check(TrialModule447.connectedCount(legacy, migratedHub) == 8, "legacy graph has 8 connected modules");
        check(TrialModule447.compileConfig(legacy).totalMobs == 18, "legacy config preserved in modules");

        // 7. Hub geometry & non-overlapping socket spacing (>= 18 px)
        check(TrialCard446.CARD_W == 256 && TrialCard446.CARD_H == 176, "hub size 256x176");
        check(TrialCard446.inputPinY(0) >= 24 && TrialCard446.inputPinY(7) <= TrialCard446.CARD_H - 16,
                "left 8 input pins inside hub vertical bounds");
        for (int s = 1; s < TrialModule447.MODULE_COUNT; s++) {
            check(TrialCard446.inputPinY(s) - TrialCard446.inputPinY(s - 1) >= 18,
                    "left slot spacing >= 18 at " + s);
        }
        check(CardEventPin398.FIRST_ROW - TrialCard446.START_PIN_Y >= 18, "pin_start vs pin_clear gap >= 18");
        check(CardEventPin398.SECOND_ROW - CardEventPin398.FIRST_ROW >= 18, "pin_clear vs pin_destroy gap >= 18");
        check(PursuitGraphNode272.TARGET_PIN_Y - CardEventPin398.SECOND_ROW >= 18, "pin_destroy vs blue gap >= 18");

        // 8. 3-socket mapping + cleanup + blue pin
        NpcDialogueGraph.Node s1 = g.add(NpcDialogueGraph.LINE, 1000, 100);
        NpcDialogueGraph.Node s2 = g.add(NpcDialogueGraph.TRIGGER, 1000, 200);
        NpcDialogueGraph.Node s3 = g.add(NpcDialogueGraph.VARIABLE, 1000, 300);
        TrialGraph446.setStartWaveTarget(hub, s1.id);
        TrialGraph446.setWaveClearTarget(hub, s2.id);
        TrialGraph446.setDestroyTarget(hub, s3.id);
        check(TrialGraph446.startWaveTarget(hub) == s1.id, "pin_start wired");
        check(TrialGraph446.waveClearTarget(hub) == s2.id, "pin_clear wired");
        check(TrialGraph446.destroyTarget(hub) == s3.id, "pin_destroy wired");
        check(CardEventPin398.isCard(hub), "CardEventPin398 recognises trial card");
        check(PursuitGraphNode272.isValueSourceNode(hub), "PursuitGraphNode272 blue pin recognises trial card");
        TrialGraph446.onNodeRemoved(g, s1.id);
        check(TrialGraph446.startWaveTarget(hub) == -1, "onNodeRemoved cleans third socket");

        // 9. TrialControlNode446 & BlockMenu363 non-trial invariance + trial module menu
        NpcDialogueGraph.Node ctrl = new NpcDialogueGraph.Node(99, NpcDialogueGraph.TRIGGER, 0, 0);
        TrialControlNode446.applyDefaults(ctrl);
        check(TrialControlNode446.isControlNode(ctrl), "default ctrl recognised");
        TrialControlNode446.write(ctrl, TrialControlNode446.OP_CONFIG_START, "minecraft:the_nether",
                "~5", "~", "~-3", true, json);
        TrialControlNode446.Parsed cp = TrialControlNode446.parse(ctrl.action);
        check(cp != null && cp.resolveX(100) == 105 && cp.config().totalMobs == 18, "ctrl round-trip");

        int[] baseRows = BlockMenu363.rows();
        check(BlockMenu363.count() == baseRows.length + 2, "non-trial BlockMenu count remains rows + 2");
        check(BlockMenu363.isTeleportRow(baseRows.length), "Teleport row at rows.length");
        check(BlockMenu363.isTrialControlRow(baseRows.length + 1), "TrialControl row at rows.length + 1");

        // 10. Store cleanup & AnchorCore TRIAL_KIND
        TrialGraph446Store tStore = new TrialGraph446Store();
        tStore.put("minecraft:overworld@5,70,-3", g);
        check(tStore.contains("minecraft:overworld@5,70,-3"), "trial store contains");
        check(tStore.remove("minecraft:overworld@5,70,-3") && tStore.size() == 0, "trial store remove");

        NpcGraphAnchorCore420.Entry te = new NpcGraphAnchorCore420.Entry(
                NpcGraphAnchorCore420.TRIAL_KIND, 2, 3, -1, new CompoundTag(), null);
        te.graph.putInt("Test", 1);
        final int[] count = {0};
        NpcGraphAnchorCore420.Result res = NpcGraphAnchorCore420.materialize(
                List.of(te), 0, new BlockPos(10, 64, 20),
                new NpcGraphAnchorCore420.StoreSink() {
                    @Override public boolean block(int x, int y, int z, CompoundTag gr) { return false; }
                    @Override public boolean door(int x, int y, int z, CompoundTag gr) { return false; }
                    @Override public boolean chest(int x, int y, int z, CompoundTag gr) { return false; }
                    @Override public boolean lock(int x, int y, int z) { return false; }
                    @Override public boolean trial(int x, int y, int z, CompoundTag gr) {
                        count[0]++;
                        return x == 12 && y == 67 && z == 19;
                    }
                });
        check(res.trials == 1 && count[0] == 1 && res.total() == 1, "AnchorCore TRIAL_KIND materialize");

        System.out.println("BUILD447_CHECKS=" + checks + " | BUILD447_OK=true");
    }
}
"""


def find_java():
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java runtime found")


def main():
    jar = (pathlib.Path(sys.argv[1]) if len(sys.argv) > 1
           else ROOT / "settledlands-0.8.0-build447-modular-trial-spawner-graph.jar")
    if not jar.is_file():
        raise SystemExit("missing jar: %s" % jar)

    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for cls in REQUIRED_CLASSES:
            if cls not in names:
                problems.append("missing class: " + cls)

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = mixins.get("mixins", [])
        client = mixins.get("client", [])
        if client[0] != "NpcDropdownFirst333":
            problems.append("NpcDropdownFirst333 is not first in client mixins")
        if "NpcTrialSave446Mixin" not in common or common.index("NpcTrialSave446Mixin") > common.index("NpcCommonGraphMixin334"):
            problems.append("NpcTrialSave446Mixin must appear before NpcCommonGraphMixin334")
        for m in ("TrialCanvasSize447Mixin", "TrialGraphCanvas447Mixin",
                  "TrialModuleUi447Mixin", "TrialModuleSocket447Mixin", "TrialModuleInspector447Mixin"):
            if m not in client:
                problems.append("missing client mixin: " + m)
            if m in common:
                problems.append("447 client mixin leaked into common: " + m)

        # Ensure zero synthetic lambdas in new 447 mixins and NpcTrialRows446Mixin
        for m in ("NpcTrialRows446Mixin", "TrialCanvasSize447Mixin", "TrialGraphCanvas447Mixin",
                  "TrialModuleUi447Mixin", "TrialModuleSocket447Mixin", "TrialModuleInspector447Mixin"):
            raw = zf.read(f"dev/settledlands/mixin/{m}.class")
            if b"lambda$" in raw:
                problems.append(f"{m} contains synthetic lambda$ method!")

        # Ensure monolithic "Configure Spawner" button was removed from Trial Hub inspector
        rows_raw = zf.read("dev/settledlands/mixin/NpcTrialRows446Mixin.class")
        if b"gui.settledlands.trial.btn_configure" in rows_raw:
            problems.append("NpcTrialRows446Mixin still references monolithic btn_configure button")
        if b"gui.settledlands.trial.btn_restore_modules" not in rows_raw:
            problems.append("NpcTrialRows446Mixin missing btn_restore_modules")

        # BufferBuilder closure check in TrialModuleSocket447Mixin
        sock_raw = zf.read("dev/settledlands/mixin/TrialModuleSocket447Mixin.class")
        _sc, sock_methods, _sf = anchor_check.class_refs(sock_raw)
        buf_methods = {nm for owner, nm, _ds in sock_methods if "BufferBuilder" in owner}
        if "buildOrThrow" not in buf_methods or "build" not in buf_methods:
            problems.append("TrialModuleSocket447Mixin must handle both buildOrThrow() and build() on empty buffer")

        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        for k in (
            "gui.settledlands.trial.node",
            "gui.settledlands.trial.hub_modules",
            "gui.settledlands.trial.btn_restore_modules",
            "gui.settledlands.trial.mod.mode",
            "gui.settledlands.trial.mod.radius",
            "gui.settledlands.trial.mod.wave",
            "gui.settledlands.trial.mod.timing",
            "gui.settledlands.trial.mod.mobs",
            "gui.settledlands.trial.mod.rewards",
            "gui.settledlands.trial.mod.ominous",
            "gui.settledlands.trial.mod.protect",
            "gui.settledlands.trial.mod.done",
        ):
            if not ru.get(k) or not en.get(k):
                problems.append("missing translation key: " + k)

    if problems:
        raise SystemExit("build447_check FAILED:\n  - " + "\n  - ".join(problems))

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
             "-cp", str(jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("stub compile for probe failed:\n" + r1.stdout + r1.stderr)

        probe_src = td / "Build447Probe.java"
        probe_src.write_text(PROBE_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("probe compile failed:\n" + r2.stdout + r2.stderr)

        cp = f"{probe_out}:{jar}:{stubs_out}"
        run_args = []
        if TRIAL_TUNER_JAR.is_file():
            cp = f"{cp}:{TRIAL_TUNER_JAR}"
            run_args.append("with_tt")
        r3 = subprocess.run([java, "-cp", cp, "Build447Probe"] + run_args,
                            capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("Build447Probe failed:\n" + r3.stdout + r3.stderr)
        print(r3.stdout.strip())
    print(f"build447_check: PASS {jar}")


if __name__ == "__main__":
    main()
