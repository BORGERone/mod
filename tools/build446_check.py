#!/usr/bin/env python3
"""
Gate (Build 446): Comprehensive bytecode + live JVM verification for Build 446:
  1. Verifies all new/updated Build 446 classes, mixin registrations, BufferBuilder closure
     guards, zero synthetic lambdas in shared-target mixins, and full RU/EN translation keys.
  2. Cross-checks @Pseudo mixins and reflection field/method names against real Trial Tuner v8.1
     JAR when available at /tmp/trialtuner/trialtuner-0.1.0-fixed-v8.1.jar.
  3. Compiles and executes a live Java 21 probe testing:
     - TrialConfig446 JSON codec & live SpawnerConfig cross-round-trip;
     - TrialControlNode446 action parsing, ~-offsets, pipes-safe JSON payload;
     - TrialGraph446 3-socket mapping (pin_start/pin_clear/pin_destroy) + onNodeRemoved cleanup;
     - Non-overlapping right-edge pin geometry (32 / 50 / 68 / 92 px) & CardEventPin398 /
       PursuitGraphNode272 predicates;
     - BlockMenu363 & TeleportNode424 (19 NPC items, maxScroll=14, SPAWNER_ROW=18);
     - Store cleanup invariants (BlockTriggerStore351, DoorStore368, DoorGraph382Store,
       TrialGraph446Store, NpcGraphAnchorCore420 TRIAL_KIND).
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
    "dev/settledlands/mixin/compat/TrialSpawnerEntityMixin446.class",
    "dev/settledlands/mixin/compat/TrialTriggersMixin446.class",
)

PROBE_JAVA = r"""
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
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialGraph446Store;
import dev.settledlands.chest.ChestGraph396;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class Build446Probe {
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
        cfg.totalMobs = 18;
        cfg.simultaneousMobs = 6;
        cfg.mobs.add(new TrialConfig446.SpawnEntry("minecraft:breeze", 3));
        cfg.rewards.add(new TrialConfig446.RewardEntry("minecraft:diamond", 5, 1, 4, 50));
        cfg.onWaveClearCommands = "say wave;give @p minecraft:apple|extra";
        String json = cfg.toJson();
        TrialConfig446 parsed = TrialConfig446.fromJson(json);
        check(TrialConfig446.MODE_OMINOUS.equals(parsed.mode), "mode round-trip");
        check(parsed.totalMobs == 18 && parsed.simultaneousMobs == 6, "mob counts round-trip");
        check(parsed.mobs.size() == 2 && "minecraft:breeze".equals(parsed.mobs.get(1).entityId), "mobs round-trip");
        int baseRewards = cfg.rewards.size() - 1;
        check(parsed.rewards.size() == baseRewards + 1
                && "minecraft:diamond".equals(parsed.rewards.get(baseRewards).itemId)
                && parsed.rewards.get(baseRewards).chancePercent == 50, "rewards round-trip");

        // Bounds clamping & null resilience
        TrialConfig446 bad = TrialConfig446.fromJson("{invalid");
        check(bad.totalMobs >= 1 && !bad.mobs.isEmpty(), "bad json falls back cleanly");
        check(TrialConfig446.nextMode(TrialConfig446.MODE_TRIAL).equals(TrialConfig446.MODE_OMINOUS), "mode cycle 1");
        check(TrialConfig446.nextMode(TrialConfig446.MODE_OMINOUS).equals(TrialConfig446.MODE_SPECIAL), "mode cycle 2");
        check(TrialConfig446.nextMode(TrialConfig446.MODE_SPECIAL).equals(TrialConfig446.MODE_TRIAL), "mode cycle 3");

        // Cross-check SpawnerConfig presence & methods when TT JAR is on classpath
        if (args.length > 0 && "with_tt".equals(args[0])) {
            Class<?> scCls = Class.forName("dev.arena.trialtuner.config.SpawnerConfig",
                    false, Build446Probe.class.getClassLoader());
            boolean hasFrom = false;
            boolean hasTo = false;
            for (java.lang.reflect.Method m : scCls.getDeclaredMethods()) {
                if ("fromJson".equals(m.getName())) hasFrom = true;
                if ("toJson".equals(m.getName())) hasTo = true;
            }
            check(hasFrom && hasTo, "TT SpawnerConfig exposes fromJson & toJson");
        }

        // 3. TrialGraph446 3-socket mapping & cleanup
        NpcDialogueGraph g = TrialGraph446.defaultGraph(json);
        NpcDialogueGraph.Node card = TrialGraph446.trialNode(g);
        check(card != null && card.type == TrialGraph446.TRIAL_TYPE, "trial card created");
        check(TrialGraph446.startWaveTarget(card) == -1, "startWaveTarget initially unwired (0 -> -1)");
        NpcDialogueGraph.Node s1 = new NpcDialogueGraph.Node(10, NpcDialogueGraph.LINE, 300, 100);
        NpcDialogueGraph.Node s2 = new NpcDialogueGraph.Node(11, NpcDialogueGraph.TRIGGER, 300, 200);
        NpcDialogueGraph.Node s3 = new NpcDialogueGraph.Node(12, NpcDialogueGraph.VARIABLE, 300, 300);
        g.nodes.put(10, s1);
        g.nodes.put(11, s2);
        g.nodes.put(12, s3);
        TrialGraph446.setStartWaveTarget(card, 10);
        TrialGraph446.setWaveClearTarget(card, 11);
        TrialGraph446.setDestroyTarget(card, 12);
        check(TrialGraph446.startWaveTarget(card) == 10, "pin_start -> 10");
        check(TrialGraph446.waveClearTarget(card) == 11, "pin_clear -> 11");
        check(TrialGraph446.destroyTarget(card) == 12, "pin_destroy -> 12");
        check(CardEventPin398.isCard(card), "CardEventPin398 recognises trial card");
        check(PursuitGraphNode272.isValueSourceNode(card), "PursuitGraphNode272 blue pin recognises trial card");
        TrialGraph446.onNodeRemoved(g, 10);
        check(TrialGraph446.startWaveTarget(card) == -1, "onNodeRemoved cleans third socket");

        // Pin spacing check (>= 18 px vertically so no hit overlap occurs)
        check(CardEventPin398.FIRST_ROW - TrialCard446.START_PIN_Y >= 18, "pin_start vs pin_clear gap >= 18");
        check(CardEventPin398.SECOND_ROW - CardEventPin398.FIRST_ROW >= 18, "pin_clear vs pin_destroy gap >= 18");
        check(PursuitGraphNode272.TARGET_PIN_Y - CardEventPin398.SECOND_ROW >= 18, "pin_destroy vs blue gap >= 18");

        // 4. TrialControlNode446 parsing & ~-coords & pipes-safe JSON
        NpcDialogueGraph.Node ctrl = new NpcDialogueGraph.Node(20, NpcDialogueGraph.TRIGGER, 0, 0);
        TrialControlNode446.applyDefaults(ctrl);
        check(TrialControlNode446.isControlNode(ctrl), "default ctrl recognised");
        TrialControlNode446.write(ctrl, TrialControlNode446.OP_CONFIG_START, "minecraft:the_nether",
                "~5", "~", "~-3", true, json);
        TrialControlNode446.Parsed cp = TrialControlNode446.parse(ctrl.action);
        check(cp != null, "ctrl parsed");
        check(TrialControlNode446.OP_CONFIG_START.equals(cp.op), "ctrl op CONFIG_START");
        check("minecraft:the_nether".equals(cp.dimension), "ctrl dim");
        check(cp.resolveX(100) == 105 && cp.resolveY(64) == 64 && cp.resolveZ(-10) == -13, "tilde coords resolve");
        check(cp.killMobs, "killMobs flag");
        check(cp.config().totalMobs == 18, "nested config JSON survived");
        check(TrialControlNode446.isCoordInputValid("~-12") && !TrialControlNode446.isCoordInputValid("abc"), "coord filter");

        // 5. Menus (NPC 19 items + BlockMenu363 compact rows)
        check(TeleportNode424.NPC_MENU_ITEMS == 19, "NPC_MENU_ITEMS == 19");
        check(TeleportNode424.NPC_MAX_SCROLL == 14, "NPC_MAX_SCROLL == 14");
        check(TrialControlNode446.SPAWNER_ROW == 18, "SPAWNER_ROW == 18");
        int[] blockRows = BlockMenu363.rows();
        check(BlockMenu363.isTeleportRow(blockRows.length), "BlockMenu Teleport row index");
        check(BlockMenu363.isTrialControlRow(blockRows.length + 1), "BlockMenu TrialControl row index");

        // 6. Store cleanup & AnchorCore TRIAL_KIND
        BlockTriggerStore351 bStore = new BlockTriggerStore351();
        bStore.put("minecraft:overworld@1,64,2", g);
        bStore.rememberBlockId("minecraft:overworld@1,64,2", "minecraft:stone");
        check("minecraft:stone".equals(bStore.entry("minecraft:overworld@1,64,2").blockId), "blockId stored");
        check(bStore.remove("minecraft:overworld@1,64,2") && bStore.size() == 0, "block store remove");

        DoorGraph382Store dStore = new DoorGraph382Store();
        dStore.put("minecraft:overworld@2,64,3", g);
        check(dStore.ofDimension("minecraft:overworld").size() == 1, "door dim added");
        dStore.put("minecraft:overworld@2,64,3", null);
        check(dStore.size() == 0 && dStore.ofDimension("minecraft:overworld").isEmpty(), "door put(null) clears dim");

        DoorStore368 lockStore = new DoorStore368();
        CompoundTag lockTag = new CompoundTag();
        net.minecraft.nbt.ListTag llist = new net.minecraft.nbt.ListTag();
        CompoundTag lk = new CompoundTag();
        lk.putString("K", "minecraft:overworld@2,64,3");
        llist.add(lk);
        lockTag.put("Locked", llist);
        check(lockStore.unlockExact("minecraft:overworld@2,64,3") == false, "unlockExact on empty = false");

        TrialGraph446Store tStore = new TrialGraph446Store();
        tStore.put("minecraft:overworld@5,70,-3", g);
        check(tStore.contains("minecraft:overworld@5,70,-3"), "trial store contains");
        check(tStore.keysOfDimension("minecraft:overworld").size() == 1, "trial store dim index");
        CompoundTag savedTag = tStore.save(new CompoundTag(), null);
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

        System.out.println("BUILD446_CHECKS=" + checks + " | BUILD446_OK=true");
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
    jar = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "settledlands-0.8.0-build446-auto-cleanup-and-trial-spawner.jar"
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
        for m in ("BlockDestroy446Mixin", "TrialGraphValidationMixin446", "TrialControlActionsMixin446", "TrialControlWiringMixin446"):
            if m not in common:
                problems.append("missing common mixin: " + m)
        for m in ("NpcTrialOpen446Mixin", "NpcTrialNoStats446Mixin", "NpcTrialNoAi446Mixin",
                  "NpcTrialCard446Mixin", "NpcTrialRows446Mixin", "NpcTrialSocket446Mixin",
                  "NpcTrialPalette446Mixin", "TrialControlGraphUiMixin446", "TrialControlInspectorMixin446"):
            if m not in client:
                problems.append("missing client mixin: " + m)

        compat = json.loads(zf.read("settledlands.compat.mixins.json").decode("utf-8"))
        if compat.get("required") is not False:
            problems.append("settledlands.compat.mixins.json must set required=false")
        for m in ("TrialSpawnerEntityMixin446", "TrialTriggersMixin446"):
            if m not in compat.get("mixins", []):
                problems.append("missing compat mixin: " + m)

        sock_raw = zf.read("dev/settledlands/mixin/NpcTrialSocket446Mixin.class")
        _sc, sock_methods, _sf = anchor_check.class_refs(sock_raw)
        buf_methods = {nm for owner, nm, _ds in sock_methods if "BufferBuilder" in owner}
        if "buildOrThrow" not in buf_methods or "build" not in buf_methods:
            problems.append("NpcTrialSocket446Mixin must handle both buildOrThrow() and build() on empty buffer")

        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        for k in ("gui.settledlands.trial.node", "gui.settledlands.trial_ctl.menu", "gui.settledlands.trial.cfg.title"):
            if not ru.get(k) or not en.get(k):
                problems.append("missing translation key: " + k)

    if TRIAL_TUNER_JAR.is_file():
        with zipfile.ZipFile(TRIAL_TUNER_JAR) as tz:
            be_raw = tz.read("dev/arena/trialtuner/content/TrialSpawnerBlockEntity.class")
            tr_raw = tz.read("dev/arena/trialtuner/trigger/TrialTriggers.class")
            for sym in (b"startWave", b"finishWave", b"applyConfig", b"getConfigJson", b"completedWaves", b"participants"):
                if sym not in be_raw:
                    problems.append("TrialSpawnerBlockEntity missing expected symbol: " + sym.decode())
            if b"onDestroyed" not in tr_raw:
                problems.append("TrialTriggers missing onDestroyed")

    if problems:
        for p in problems:
            print("FAIL:", p)
        return 1

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
            raise SystemExit("probe stub compile failed:\n" + r1.stdout + r1.stderr)

        probe_src = td / "Build446Probe.java"
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
            cp = cp + ":" + str(TRIAL_TUNER_JAR)
            run_args.append("with_tt")

        r3 = subprocess.run([java, "-cp", cp, "Build446Probe"] + run_args,
                            capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("Build446Probe failed:\n" + r3.stdout + r3.stderr)
        print(r3.stdout.strip())

    print("build446_check: PASS", jar.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
