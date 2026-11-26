#!/usr/bin/env python3
"""
Gate (Build 448): Comprehensive bytecode, localization parity, mixin target verification,
and live JVM probe for Build 448:
  - RMB palette cleaned (no 8 trial module rows)
  - Docked bottom descriptions on all node cards across every graph + Auto-AI card
  - State-aware >2-second hover tooltips across graph canvas, widgets, dropdowns, sliders, and config screens
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
TRIAL_TUNER_JAR = pathlib.Path("/tmp/trialtuner/trialtuner-0.1.0-fixed-v8.1.jar")

REQUIRED_CLASSES = (
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/npc/client/LootPoolCard436.class",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.class",
    "dev/settledlands/npc/client/TrialModulePickerScreen447.class",
    "dev/settledlands/npc/client/GraphCardDesc448.class",
    "dev/settledlands/npc/client/GraphTooltips448.class",
    "dev/settledlands/npc/client/GraphTooltips448$Tip.class",
    "dev/settledlands/npc/client/GraphTooltips448$ConfigArea.class",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.class",
    "dev/settledlands/mixin/GraphCardFooter448Mixin.class",
    "dev/settledlands/mixin/AutoAiFooter448Mixin.class",
    "dev/settledlands/mixin/GraphHover448Mixin.class",
    "dev/settledlands/mixin/GraphUiWidgets448Mixin.class",
    "dev/settledlands/mixin/GraphDropdownHover448Mixin.class",
)

EXPECTED_CLIENT_MIXINS = (
    "GraphCardFooter448Mixin",
    "AutoAiFooter448Mixin",
    "GraphUiWidgets448Mixin",
    "GraphDropdownHover448Mixin",
    "GraphHover448Mixin",
)

# Exact (mixin_class_internal, target_class_internal, [(method_name, method_desc), ...])
MIXIN_TARGET_CHECKS = (
    (
        "dev/settledlands/mixin/GraphCardFooter448Mixin",
        "dev/settledlands/npc/client/NpcStatsGraphUi",
        (
            ("draw", "(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"),
            ("visible", "(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            ("hit", "(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcDialogueGraph;DD)I"),
        ),
    ),
    (
        "dev/settledlands/mixin/AutoAiFooter448Mixin",
        "dev/settledlands/npc/client/NpcAutoAiGraphUi255",
        (
            ("draw", "(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;)V"),
            ("visible", "(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcAutoAi255$GraphState;)Z"),
        ),
    ),
    (
        "dev/settledlands/mixin/GraphUiWidgets448Mixin",
        "dev/settledlands/npc/client/NpcUi",
        (
            ("dropdown", "(IIILjava/util/List;ILjava/util/function/IntConsumer;)Ldev/settledlands/npc/client/NpcUi$NpcDropdown;"),
            ("slider", "(IIIIIILjava/util/function/IntFunction;Ljava/util/function/IntConsumer;)Ldev/settledlands/npc/client/NpcUi$NpcSlider;"),
        ),
    ),
    (
        "dev/settledlands/mixin/GraphDropdownHover448Mixin",
        "dev/settledlands/npc/client/NpcUi$NpcDropdown",
        (
            ("renderPopup", "(Lnet/minecraft/client/gui/GuiGraphics;II)V"),
        ),
    ),
    (
        "dev/settledlands/mixin/GraphHover448Mixin",
        "dev/settledlands/npc/client/NpcDialogueGraphScreen",
        (
            ("widgets", "()V"),
            ("label", "(Ljava/lang/String;II)V"),
            ("button", "(Ljava/lang/String;IIIILjava/lang/Runnable;)Lnet/minecraft/client/gui/components/Button;"),
            ("textBox", "(Ljava/lang/String;IIIIILjava/util/function/Consumer;)Lnet/minecraft/client/gui/components/EditBox;"),
            ("multiLineTextBox", "(Ljava/lang/String;IIIIIILnet/minecraft/network/chat/Component;Ljava/util/function/Consumer;)Lnet/minecraft/client/gui/components/MultiLineEditBox;"),
            ("render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            ("mouseClicked", "(DDI)Z"),
        ),
    ),
)

PROBE_JAVA = r"""
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.GraphCardDesc448;
import dev.settledlands.npc.client.GraphTooltips448;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Build448Probe {
    private static int checks = 0;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("check failed (#" + checks + "): " + msg);
    }

    public static void main(String[] args) {
        // 1. BlockMenu363: no trial module rows left for any graph kind (block, door, chest, trial)
        int stockCount = BlockMenu363.rows().length;
        for (int mode = 0; mode < 4; mode++) {
            boolean doorMode = mode == 1;
            boolean chestMode = mode == 2;
            boolean trialMode = mode == 3;
            BlockMenu363.fit(null, null, doorMode, chestMode, trialMode);
            int expectedCount = stockCount + (chestMode ? 3 : 2);
            int c = BlockMenu363.count();
            check(c == expectedCount, "BlockMenu363.count() mode=" + mode
                    + " expected " + expectedCount + ", got " + c);
            int seenCtrl = 0;
            for (int r = 0; r < c; r++) {
                check(BlockMenu363.trialModuleSlot(r) == -1,
                        "trialModuleSlot must be -1 in Build 448 for row " + r);
                if (BlockMenu363.isTrialControlRow(r)) seenCtrl++;
            }
            check(seenCtrl == 1, "expected 1 TrialControl row for mode=" + mode + ", got " + seenCtrl);
        }

        // 2. GraphCardDesc448: every single node type across all editors has a non-unknown footerKey
        Set<String> seenKeys = new HashSet<>();
        for (int t = 0; t <= 15; t++) {
            NpcDialogueGraph.Node n = new NpcDialogueGraph.Node(t + 1, t, 0, 0);
            String k = GraphCardDesc448.footerKey(n);
            check(("gui.settledlands.desc448.card.node_" + t).equals(k),
                    "wrong footerKey for stock node " + t + ": " + k);
            seenKeys.add(k);
        }

        NpcDialogueGraph.Node cutLine = new NpcDialogueGraph.Node(100, NpcDialogueGraph.LINE, 0, 0);
        cutLine.cutsceneLine = true;
        check("gui.settledlands.desc448.card.cutscene_line".equals(GraphCardDesc448.footerKey(cutLine)),
                "cutscene line key mismatch");
        seenKeys.add(GraphCardDesc448.footerKey(cutLine));

        NpcDialogueGraph.Node stats = new NpcDialogueGraph.Node(101, 16, 0, 0);
        check("gui.settledlands.desc448.card.stats".equals(GraphCardDesc448.footerKey(stats)), "stats key");
        check(GraphCardDesc448.cardWidth(stats) == 300 && GraphCardDesc448.cardHeight(stats) == 224,
                "stats dimensions mismatch");
        seenKeys.add(GraphCardDesc448.footerKey(stats));

        NpcDialogueGraph.Node blkName = new NpcDialogueGraph.Node(102, BlockName359.TYPE, 0, 0);
        check("gui.settledlands.desc448.card.block_name".equals(GraphCardDesc448.footerKey(blkName)), "block_name");
        seenKeys.add(GraphCardDesc448.footerKey(blkName));

        NpcDialogueGraph.Node door = new NpcDialogueGraph.Node(103, DoorGraph382.DOOR_TYPE, 0, 0);
        NpcDialogueGraph.Node doorSig = new NpcDialogueGraph.Node(104, DoorGraph382.SIGNAL_TYPE, 0, 0);
        NpcDialogueGraph.Node doorTgt = new NpcDialogueGraph.Node(105, DoorGraph382.TARGET_TYPE, 0, 0);
        check("gui.settledlands.desc448.card.door".equals(GraphCardDesc448.footerKey(door)), "door key");
        check("gui.settledlands.desc448.card.door_signal".equals(GraphCardDesc448.footerKey(doorSig)), "door_sig");
        check("gui.settledlands.desc448.card.door_target".equals(GraphCardDesc448.footerKey(doorTgt)), "door_tgt");

        NpcDialogueGraph.Node chest = new NpcDialogueGraph.Node(106, ChestGraph396.CHEST_TYPE, 0, 0);
        check("gui.settledlands.desc448.card.chest".equals(GraphCardDesc448.footerKey(chest)), "chest key");

        // Modular trial graph creates hub + all 8 modules
        NpcDialogueGraph trialGraph = TrialGraph446.defaultGraph(TrialConfig446.defaults().toJson());
        NpcDialogueGraph.Node hub = TrialGraph446.trialNode(trialGraph);
        check("gui.settledlands.desc448.card.trial_hub".equals(GraphCardDesc448.footerKey(hub)), "trial_hub");
        check(GraphCardDesc448.cardWidth(hub) == 256 && GraphCardDesc448.cardHeight(hub) == 176,
                "trial hub dimensions mismatch");
        int seenMods = 0;
        for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
            NpcDialogueGraph.Node mod = TrialModule447.connectedModule(trialGraph, hub, s);
            check(mod != null, "missing module for slot " + s);
            String expected = "gui.settledlands.desc448.card.mod_" + TrialModule447.kindOf(s);
            String got = GraphCardDesc448.footerKey(mod);
            check(expected.equals(got), "module slot " + s + " expected " + expected + ", got " + got);
            seenKeys.add(got);
            seenMods++;
        }
        check(seenMods == 8, "expected all 8 modules");

        NpcDialogueGraph.Node tc = new NpcDialogueGraph.Node(300, NpcDialogueGraph.TRIGGER, 0, 0);
        tc.action = TrialControlNode446.DEFAULT_ACTION;
        check("gui.settledlands.desc448.card.trial_ctrl".equals(GraphCardDesc448.footerKey(tc)), "trial_ctrl");

        NpcDialogueGraph.Node tp = new NpcDialogueGraph.Node(301, NpcDialogueGraph.TRIGGER, 0, 0);
        tp.action = TeleportNode424.DEFAULT_ACTION;
        check("gui.settledlands.desc448.card.teleport".equals(GraphCardDesc448.footerKey(tp)), "teleport");

        NpcDialogueGraph.Node lp = new NpcDialogueGraph.Node(302, NpcDialogueGraph.TRIGGER, 0, 0);
        lp.action = LootPool436.DEFAULT_ACTION;
        check("gui.settledlands.desc448.card.loot_pool".equals(GraphCardDesc448.footerKey(lp)), "loot_pool");

        NpcDialogueGraph.Node pursuit = new NpcDialogueGraph.Node(303, NpcDialogueGraph.TRIGGER, 0, 0);
        pursuit.action = "trigger:pursuit:npc:1";
        check("gui.settledlands.desc448.card.pursuit".equals(GraphCardDesc448.footerKey(pursuit)), "pursuit");

        check(seenKeys.size() >= 25, "expected >=25 distinct card description keys, got " + seenKeys.size());

        // 3. Footer visibility and hit testing
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.bounds(0, 0, 800, 600);
        canvas.panX = 0;
        canvas.panY = 0;
        canvas.zoom = 1.0D;

        // Place node so main card body ends just above canvas.top (y=-105, h=100 => bottom=-5),
        // while the 26px footer extends to +21 inside the visible canvas.
        NpcDialogueGraph.Node edgeNode = new NpcDialogueGraph.Node(400, NpcDialogueGraph.LINE, 50, -105);
        check(GraphCardDesc448.isFooterVisible(canvas, edgeNode),
                "isFooterVisible should return true when only footer enters canvas top edge");

        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node hitNode = new NpcDialogueGraph.Node(401, NpcDialogueGraph.LINE, 100, 100);
        g.nodes.put(hitNode.id, hitNode);
        int hitId = GraphCardDesc448.hitNodeFooterId(canvas, g,
                canvas.screenX(150), canvas.screenY(210)); // 100+100=200 card bottom, 210 in footer
        check(hitId == 401, "hitNodeFooterId should select node when clicking docked footer, got " + hitId);
        int missId = GraphCardDesc448.hitNodeFooterId(canvas, g,
                canvas.screenX(150), canvas.screenY(240));
        check(missId == -1, "hitNodeFooterId should miss below footer");

        // Auto-AI footer visibility
        NpcAutoAi255.GraphState ai = new NpcAutoAi255.GraphState();
        ai.synced = true;
        ai.cardX = 50;
        ai.cardY = -130;
        check(GraphCardDesc448.isAutoAiFooterVisible(canvas, ai),
                "isAutoAiFooterVisible should return true near top edge");

        // 4. GraphTooltips448 config area registration helpers + click handling
        List<GraphTooltips448.ConfigArea> areas = new ArrayList<>();
        Object dummyW = new Object();
        areas.add(new GraphTooltips448.ConfigArea("slotA", dummyW, 10, 20, 100, 18, "mode", "2.0"));
        check(areas.size() == 1 && "slotA".equals(areas.get(0).slotId) && "2.0".equals(areas.get(0).arg),
                "ConfigArea registration check failed");
        GraphTooltips448.onMouseClick();
        GraphTooltips448.beginScreenWidgets(null);
        GraphTooltips448.finishScreenWidgets(null, null, g, 0);
        GraphTooltips448.recordLabel(null, "k", 0, 0);
        GraphTooltips448.recordOpenPopup(null, 0, null, 0, 0);

        System.out.println("BUILD448_CHECKS=" + checks + " | BUILD448_OK=true");
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
    if len(sys.argv) < 2:
        raise SystemExit("usage: build448_check.py <jar>")
    jar = pathlib.Path(sys.argv[1])
    if not jar.is_file():
        raise SystemExit(f"no such jar: {jar}")

    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for req in REQUIRED_CLASSES:
            if req not in names:
                problems.append(f"missing class in JAR: {req}")

        # Manifest/TOML checks
        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if 'version="0.8.0-build448-card-descriptions-and-hover-tooltips"' not in toml:
            problems.append("neoforge.mods.toml missing Build 448 version")

        # Mixin registration & order
        mixins_json = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = mixins_json.get("mixins", [])
        client = mixins_json.get("client", [])
        if not client or client[0] != "NpcDropdownFirst333":
            problems.append("NpcDropdownFirst333 must remain first in client mixins")
        for m in EXPECTED_CLIENT_MIXINS:
            if m in common:
                problems.append(f"{m} must NOT be in common mixins")
            if m not in client:
                problems.append(f"{m} missing from client mixins")
        if client and client[-1] != "GraphHover448Mixin":
            problems.append(f"GraphHover448Mixin must be last in client mixins (got {client[-1]})")

        # Zero synthetic lambdas in Build 448 + palette mixins
        for m in list(EXPECTED_CLIENT_MIXINS) + ["NpcTrialPalette446Mixin"]:
            cls_path = f"dev/settledlands/mixin/{m}.class"
            if cls_path in names:
                cf_map = check_mixin_class.load_classes(zf, [cls_path])
                cf = next(iter(cf_map.values()))
                for meth in cf.methods:
                    if "lambda$" in meth.name:
                        problems.append(f"forbidden synthetic lambda in {m}: {meth.name}")

        # Verify every mixin target class actually declares every injected method+descriptor
        for mixin_cls, target_cls, method_specs in MIXIN_TARGET_CHECKS:
            target_path = target_cls + ".class"
            if target_path not in names:
                problems.append(f"missing mixin target {target_path} for {mixin_cls}")
                continue
            cf_map = check_mixin_class.load_classes(zf, [target_path])
            tcf = next(iter(cf_map.values()))
            declared = {(m.name, m.desc) for m in tcf.methods}
            for mname, mdesc in method_specs:
                if (mname, mdesc) not in declared:
                    problems.append(
                        f"target {target_cls} missing {mname}{mdesc} required by {mixin_cls}")

        # Verify ALL desc448 / tip448 keys referenced in compiled classes exist in BOTH ru_ru & en_us
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if len(ru) != len(en) or set(ru.keys()) != set(en.keys()):
            only_ru = set(ru.keys()) - set(en.keys())
            only_en = set(en.keys()) - set(ru.keys())
            problems.append(f"lang key mismatch ru vs en: only_ru={sorted(only_ru)[:5]}, only_en={sorted(only_en)[:5]}")

        referenced_keys = set()
        for cls in ("dev/settledlands/npc/client/GraphCardDesc448.class",
                    "dev/settledlands/npc/client/GraphTooltips448.class"):
            cf_map = check_mixin_class.load_classes(zf, [cls])
            cf = next(iter(cf_map.values()))
            for entry in cf.cp:
                if not entry or entry[0] != 8:
                    continue
                idx = entry[1]
                utf = cf.cp[idx]
                if not utf or utf[0] != 1:
                    continue
                s = utf[1]
                if isinstance(s, bytes):
                    s = s.decode("utf-8", "replace")
                if re.fullmatch(r"gui\.settledlands\.(desc448|tip448)\.[a-z0-9_.]+", s):
                    if not s.endswith(".") and not s.endswith("_"):
                        referenced_keys.add(s)

        for k in sorted(referenced_keys):
            if not ru.get(k):
                problems.append(f"ru_ru.json missing or empty key referenced in bytecode: {k}")
            if not en.get(k):
                problems.append(f"en_us.json missing or empty key referenced in bytecode: {k}")

        # Check all dynamically formatted templates too
        dynamic_keys = (
            [f"gui.settledlands.desc448.card.node_{i}" for i in range(16)]
            + [f"gui.settledlands.desc448.card.mod_{k}"
               for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
            + [f"gui.settledlands.tip448.menu.node_{i}" for i in range(16)]
            + [f"gui.settledlands.tip448.menu.node_{i}" for i in (40, 50, 60)]
            + [f"gui.settledlands.tip448.node.n_{i}" for i in range(16)]
            + [f"gui.settledlands.tip448.node.mod_{k}"
               for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
            + [f"gui.settledlands.tip448.pin.slot_{k}"
               for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
            + [f"gui.settledlands.tip448.varop.{i}" for i in range(6)]
            + [f"gui.settledlands.tip448.preset.{i}" for i in range(10)]
            + [f"gui.settledlands.tip448.op.{k}"
               for k in ("start", "config", "config_start", "stop", "reset_cd", "destroy")]
            + [f"gui.settledlands.tip448.hack.slider_diff_{i}" for i in (1, 2, 3)]
        )
        for k in dynamic_keys:
            if not ru.get(k):
                problems.append(f"ru_ru.json missing dynamic key: {k}")
            if not en.get(k):
                problems.append(f"en_us.json missing dynamic key: {k}")

    if problems:
        raise SystemExit("build448_check FAILED:\n  - " + "\n  - ".join(problems))

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

        probe_src = td / "Build448Probe.java"
        probe_src.write_text(PROBE_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("probe compile failed:\n" + r2.stdout + r2.stderr)

        cp = f"{probe_out}:{jar}:{stubs_out}"
        if TRIAL_TUNER_JAR.is_file():
            cp = f"{cp}:{TRIAL_TUNER_JAR}"
        r3 = subprocess.run([java, "-cp", cp, "Build448Probe"], capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("Build448Probe failed:\n" + r3.stdout + r3.stderr)
        print(f"BYTECODE_KEYS_VERIFIED={len(referenced_keys)} | DYNAMIC_KEYS_VERIFIED={len(dynamic_keys)}")
        print(r3.stdout.strip())
    print(f"build448_check: PASS {jar}")


if __name__ == "__main__":
    main()
