#!/usr/bin/env python3
"""
Gate (Build 454): Full verification suite for Build 454 (all Build 453 features + Build 454 fixes):
  1. Manifest & version (0.8.0-build454-mixin-runtime-and-graph-polish)
  2. Strict Sponge Mixin package checks (zero '$', zero non-@Mixin, zero invokedynamic in
     VarExecutionBlockMixin453/VarExecutionNpcMixin453, VarExecutionBlockMixin453$Frame removed)
  3. settledlands.mixins.json ordering:
     - VarExecutionNpcMixin453 BEFORE GuideRange450Mixin in mixins[]
     - GraphInspectorDescMixin453 BEFORE custom inspectors, GraphClickDragMixin453 AFTER
       GraphRangeInputs450Mixin and BEFORE GraphHover448Mixin in client[]
  4. GraphInspectorDesc453.FooterReserveHost bridge check (widgets() reserve popped in
     GraphClickDragMixin453, not prematurely in GraphInspectorDescMixin453)
  5. Complete RU/EN parity & bytecode string-key scan across every class in the JAR
  6. Inherited Build 450 + 452 + 453 JVM live-execution probes + new Probe454 (BlockWalkFrame,
     NpcResolveFrame, IF == a-b GraphRanges450, WireHover453 loop-back IF orange wire detection)
"""

import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build450_check
import build452_check
import build453_check
import check_mixin_class

ZERO_LAMBDA_454 = build453_check.ZERO_LAMBDA_453

PROBE454_JAVA = r"""
import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.client.GraphInspectorDesc453;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.VarDataCard453;
import dev.settledlands.npc.client.WireHover453;
import java.util.List;

public class Probe454 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. Frame classes live cleanly outside dev.settledlands.mixin
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node ifNode = g.add(NpcDialogueGraph.IF, 600, 200);
        NpcDialogueGraph.Node varNode = g.add(NpcDialogueGraph.VARIABLE, 100, 200);
        ifNode.next = varNode.id;
        ifNode.varInputNode = varNode.id; // loop-back: vin == node.next!
        varNode.pointsName = "gold";
        varNode.varOp = VarDataNode453.OP_ADD;
        varNode.varValue = 1;

        VarScopeStore453.BlockWalkFrame bf = new VarScopeStore453.BlockWalkFrame(g, null, null);
        check(bf.graph == g && bf.where == null && bf.server == null,
                "BlockWalkFrame accessible outside mixin package");
        VarScopeStore453.NpcResolveFrame nf = new VarScopeStore453.NpcResolveFrame(null, null, null, g);
        nf.activeIfNode = ifNode;
        check(nf.graph == g && nf.activeIfNode == ifNode && nf.contextEntity == null,
                "NpcResolveFrame accessible outside mixin package");

        // 2. FooterReserveHost interface accessible cleanly
        check(GraphInspectorDesc453.FooterReserveHost.class.isInterface(),
                "FooterReserveHost is a public interface outside mixin package");

        // 3. WireHover453 loop-back IF orange wire (vin == node.next) is scannable!
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.left = 0; canvas.top = 0; canvas.right = 1200; canvas.bottom = 800;
        canvas.zoom = 1.0; canvas.panX = 0; canvas.panY = 0;
        double x1 = VarDataCard453.orangeOutScreenX(canvas, varNode);
        double y1 = VarDataCard453.orangeOutScreenY(canvas, varNode);
        // Place bend far below stock exec wire so only KIND_ORANGE passes near (mx, my)
        NpcWirePoints321.addCustomPoint(varNode, NpcWirePoints321.KIND_ORANGE, ifNode.id, 360, 560, 0);
        double bx = canvas.screenX(360);
        double by = canvas.screenY(560);
        double mx = (x1 + bx) * 0.5;
        double my = (y1 + by) * 0.5;
        WireHover453.WireHit hit = WireHover453.scanNearestWire(null, canvas, g, mx, my);
        check(hit != null && hit.kind == WireHover453.KIND_ORANGE
                && hit.from != null && hit.from.id == varNode.id && hit.toId == ifNode.id,
                "scanNearestWire detects loop-back IF orange wire even when vin == node.next (got "
                        + (hit == null ? "null" : ("kind=" + hit.kind + " from=" + (hit.from == null ? -1 : hit.from.id) + " to=" + hit.toId)) + ")");
        check(NpcWirePoints321.removePoint(g, 360, 560), "removePoint removes loop-back orange bend point");

        // 4. GraphRanges450 IF == a-b range matching works alongside VarDataNode453
        NpcDialogueGraph.Node rngIf = g.add(NpcDialogueGraph.IF, 400, 120);
        rngIf.varOp = 0;
        rngIf.varCompare = 0;
        GraphRanges450.setRangeText(rngIf, GraphRanges450.KEY_IF_COMPARE, "5-10", 0, -1000000, 1000000);
        check(GraphRanges450.hasRange(rngIf, GraphRanges450.KEY_IF_COMPARE), "IF range stored");
        check(!GraphRanges450.inRange(rngIf, GraphRanges450.KEY_IF_COMPARE, 0), "0 not in 5-10");
        check(GraphRanges450.inRange(rngIf, GraphRanges450.KEY_IF_COMPARE, 7), "7 in 5-10");
        check(!GraphRanges450.inRange(rngIf, GraphRanges450.KEY_IF_COMPARE, 11), "11 not in 5-10");

        System.out.println("BUILD454_OK=true CHECKS=" + checks);
    }
}
"""


def run_probe454(jar_path: pathlib.Path) -> str:
    java = build450_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
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
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar_path), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("probe454 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe454.java"
        probe_src.write_text(PROBE454_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe454 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe454"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD454_OK=true" not in r3.stdout:
            raise SystemExit("Probe454 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build454_check.py <jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        print(f"not found: {jar_path}", file=sys.stderr)
        return 2

    errors = []
    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())
        for req in build450_check.REQUIRED_CLASSES + (
            "dev/settledlands/mixin/TrialModuleSignalMixin452.class",
            "dev/settledlands/npc/VarDataNode453.class",
            "dev/settledlands/npc/VarScopeStore453.class",
            "dev/settledlands/npc/VarScopeStore453$BlockWalkFrame.class",
            "dev/settledlands/npc/VarScopeStore453$NpcResolveFrame.class",
            "dev/settledlands/npc/client/GraphInspectorDesc453.class",
            "dev/settledlands/npc/client/GraphInspectorDesc453$FooterReserveHost.class",
            "dev/settledlands/npc/client/VarDataCard453.class",
            "dev/settledlands/npc/client/VarDataInspector453.class",
            "dev/settledlands/npc/client/WireHover453.class",
            "dev/settledlands/mixin/VarNodeEntityInputMixin453.class",
            "dev/settledlands/mixin/VarGraphIoMixin453.class",
            "dev/settledlands/mixin/VarExecutionNpcMixin453.class",
            "dev/settledlands/mixin/VarExecutionBlockMixin453.class",
            "dev/settledlands/mixin/VarActionsSilentMixin453.class",
            "dev/settledlands/mixin/VarValueSourceBridgeMixin453.class",
            "dev/settledlands/mixin/VarDataMenuMixin453.class",
            "dev/settledlands/mixin/VarDataGraphUi453Mixin.class",
            "dev/settledlands/mixin/GraphInspectorDescMixin453.class",
            "dev/settledlands/mixin/GraphClickDragMixin453.class",
        ):
            if req not in names:
                errors.append(f"missing class {req}")

        # Must NOT contain the Build 453 crash class
        for bad in names:
            if bad.startswith("dev/settledlands/mixin/") and "$" in bad:
                errors.append(f"forbidden '$' class inside mixin package: {bad}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build454-mixin-runtime-and-graph-polish" not in toml:
            errors.append("neoforge.mods.toml missing Build 454 version")

        mixins_cfg = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common_list = mixins_cfg.get("mixins", [])
        for cm in (
            "VarNodeEntityInputMixin453",
            "VarGraphIoMixin453",
            "VarExecutionNpcMixin453",
            "VarExecutionBlockMixin453",
            "VarActionsSilentMixin453",
            "VarValueSourceBridgeMixin453",
        ):
            if cm not in common_list:
                errors.append(f"settledlands.mixins.json missing {cm} in mixins[]")
        if common_list.index("VarExecutionNpcMixin453") > common_list.index("GuideRange450Mixin"):
            errors.append("VarExecutionNpcMixin453 must precede GuideRange450Mixin in mixins[]")

        client_list = mixins_cfg.get("client", [])
        if not client_list or client_list[0] != "NpcDropdownFirst333":
            errors.append("NpcDropdownFirst333 must stay first in client[]")
        if client_list[-1] != "GraphHover448Mixin":
            errors.append("GraphHover448Mixin must stay last in client[]")
        if client_list.index("NpcWirePointsMixin321") > client_list.index("NpcPursuitValuePinMixin290"):
            errors.append("NpcWirePointsMixin321 must precede NpcPursuitValuePinMixin290 in client[]")
        if client_list.index("GraphInspectorDescMixin453") > client_list.index("NpcPursuitInspectorMixin277"):
            errors.append("GraphInspectorDescMixin453 must precede custom inspectors in client[]")
        if client_list.index("VarDataGraphUi453Mixin") > client_list.index("GraphRangeInputs450Mixin"):
            errors.append("VarDataGraphUi453Mixin must precede GraphRangeInputs450Mixin in client[]")
        if client_list.index("GraphClickDragMixin453") < client_list.index("GraphRangeInputs450Mixin"):
            errors.append("GraphClickDragMixin453 must run AFTER GraphRangeInputs450Mixin in client[]")
        if client_list.index("GraphClickDragMixin453") > client_list.index("GraphHover448Mixin"):
            errors.append("GraphClickDragMixin453 must precede GraphHover448Mixin in client[]")

        # Verify canvas footers disabled cleanly
        gcf = zf.read("dev/settledlands/mixin/GraphCardFooter448Mixin.class")
        if b"GraphCardDesc448" in gcf:
            errors.append("GraphCardFooter448Mixin must not invoke GraphCardDesc448 canvas footers")
        acf = zf.read("dev/settledlands/mixin/AutoAiFooter448Mixin.class")
        if b"GraphCardDesc448" in acf:
            errors.append("AutoAiFooter448Mixin must not invoke GraphCardDesc448 canvas footer")

        # Verify Build 454 FooterReserveHost bridge wiring
        cd = zf.read("dev/settledlands/mixin/GraphClickDragMixin453.class")
        if b"radius_title" not in cd or b"hoveredWire" not in cd or b"settledlands$popWidgetsReserve453" not in cd:
            errors.append("GraphClickDragMixin453 missing RADIUS/hoveredWire/FooterReserveHost markers")
        gid = zf.read("dev/settledlands/mixin/GraphInspectorDescMixin453.class")
        if b"settledlands$restoreAfterWidgets453" in gid:
            errors.append("GraphInspectorDescMixin453 still contains premature widgets() @RETURN restore")

        # Lang parity + Build 453/454 keys + scan every class for referenced keys
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in tuple(build450_check.NEW_KEYS) + build452_check.NEW_KEYS_452 + build453_check.NEW_KEYS_453:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_454:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")
        for mname in (
            "dev/settledlands/mixin/VarExecutionBlockMixin453",
            "dev/settledlands/mixin/VarExecutionNpcMixin453",
        ):
            cf = classes.get(mname)
            if cf is not None:
                indys = [k for k in cf.cp if k and k[0] == 18]
                if indys:
                    errors.append(f"mixin {mname} contains invokedynamic entries: {len(indys)}")

        allowed_stems = (
            "gui.settledlands.desc448.card.node",
            "gui.settledlands.desc448.card.mod",
            "gui.settledlands.tip448.menu.node",
            "gui.settledlands.tip448.menu.tmod",
            "gui.settledlands.tip448.node.n",
            "gui.settledlands.tip448.node.mod",
            "gui.settledlands.tip448.pin.slot",
            "gui.settledlands.tip448.varop",
            "gui.settledlands.tip448.preset",
            "gui.settledlands.tip448.op",
            "gui.settledlands.tip448.hack.slider_diff",
            "gui.settledlands.door.rows",
            "gui.settledlands.var453.op",
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

    out450 = build450_check.run_probe(jar_path)
    out452 = build452_check.run_probe452(jar_path)
    out453 = build453_check.run_probe453(jar_path)
    out454 = run_probe454(jar_path)
    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build454_check: OK ", jar_path)
    for line in out450.splitlines():
        print(" ", line)
    for line in out452.splitlines():
        print(" ", line)
    for line in out453.splitlines():
        print(" ", line)
    for line in out454.splitlines():
        print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_454)} mixins + all Build 453/454 gates OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
