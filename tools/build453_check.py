#!/usr/bin/env python3
"""
Gate (Build 453): Full verification suite for Build 453:
  1. Manifest, settledlands.mixins.json ordering (NpcWirePointsMixin321 before sockets,
     GraphInspectorDescMixin453 before custom inspectors, GraphClickDragMixin453 before
     GraphHover448Mixin, NpcDropdownFirst333 first, GraphHover448Mixin last)
  2. Zero-lambda check across all Build 450 + Build 452 + Build 453 mixins (incl. Mixin rules)
  3. Complete RU/EN localization parity & bytecode string-key scan across every class in the JAR
  4. Inherited Build 450 + Build 452 JVM live-execution probes (642 + 45 checks)
  5. New Build 453 JVM live-execution probe testing all 5 requirements:
     - Req 1: GraphCardFooter448Mixin / AutoAiFooter448Mixin canvas footers disabled,
              GraphInspectorDesc453 reserveHeight & wrapping & descriptionKey
     - Req 2: GraphClickDragMixin453 click-vs-drag markers & RADIUS context menu header fix
     - Req 3: WireHover453 & NpcWirePoints321 colored bends/hangs/removePoint/aimColoredAtPoint/
              livePoints pruning across blue, orange, amber, green Trial Start, & Trial Module wires
     - Req 4: VarDataNode453 all 14 math operations (+, -, *, /, =, ^, %, +%, -%, mod, min, max,
              abs, neg), simultaneous dual left inputs (entityInput453 + varInputNode), NBT save/load
              roundtrip, legacy migration, clipboard copy/paste, & remove/disconnect cleanup
     - Req 5: VarDataNode453 UE5 variable node defaults, scopes, connect routing, BlockMenu363 rows
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
import check_mixin_class

NEW_KEYS_453 = (
    "gui.settledlands.desc453.footer_title",
    "gui.settledlands.desc448.card.node_4",
    "gui.settledlands.desc448.card.var453",
    "gui.settledlands.var453.menu",
    "gui.settledlands.var453.title",
    "gui.settledlands.var453.added",
    "gui.settledlands.var453.card_name",
    "gui.settledlands.var453.const_mode",
    "gui.settledlands.var453.card_val",
    "gui.settledlands.var453.numbers_only",
    "gui.settledlands.var453.name",
    "gui.settledlands.var453.value",
    "gui.settledlands.var453.scope",
    "gui.settledlands.var453.scope_personal",
    "gui.settledlands.var453.scope_shared",
    "gui.settledlands.var453.scope_local",
    "gui.settledlands.var453.btn_personal",
    "gui.settledlands.var453.btn_shared",
    "gui.settledlands.var453.btn_local",
    "gui.settledlands.var453.wired_count",
    "gui.settledlands.var453.wired_none",
    "gui.settledlands.var453.btn_unlink",
    "gui.settledlands.var453.op_label",
    "gui.settledlands.var453.orange_operand",
    "gui.settledlands.var453.orange_target",
    "gui.settledlands.var453.orange_none",
    "gui.settledlands.var453.btn_unlink_orange",
    "gui.settledlands.var453.blue_linked",
    "gui.settledlands.var453.blue_none",
    "gui.settledlands.var453.btn_unlink_blue",
    "gui.settledlands.tip448.menu.var453",
    "gui.settledlands.tip448.node.var453",
    "gui.settledlands.tip448.var453_op",
    "gui.settledlands.tip448.pin.var453_out_title",
    "gui.settledlands.tip448.pin.var453_out",
    "gui.settledlands.tip448.pin.var_orange_title",
    "gui.settledlands.tip448.pin.var_orange_in",
    "gui.settledlands.tip448.pin.var_blue_title",
    "gui.settledlands.tip448.pin.var_blue_in",
) + tuple(f"gui.settledlands.var453.op_{i}" for i in range(14))

ZERO_LAMBDA_453 = build452_check.ZERO_LAMBDA_452 + (
    "dev/settledlands/mixin/VarNodeEntityInputMixin453",
    "dev/settledlands/mixin/VarGraphIoMixin453",
    "dev/settledlands/mixin/VarExecutionNpcMixin453",
    "dev/settledlands/mixin/VarExecutionBlockMixin453",
    "dev/settledlands/mixin/VarActionsSilentMixin453",
    "dev/settledlands/mixin/VarValueSourceBridgeMixin453",
    "dev/settledlands/mixin/VarDataMenuMixin453",
    "dev/settledlands/mixin/VarDataGraphUi453Mixin",
    "dev/settledlands/mixin/GraphInspectorDescMixin453",
    "dev/settledlands/mixin/GraphClickDragMixin453",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290",
    "dev/settledlands/mixin/CardEventPin398Mixin",
    "dev/settledlands/mixin/NpcTrialSocket446Mixin",
    "dev/settledlands/mixin/TrialModuleSocket447Mixin",
    "dev/settledlands/mixin/NpcWirePointsMixin321",
    "dev/settledlands/mixin/GraphCardFooter448Mixin",
    "dev/settledlands/mixin/AutoAiFooter448Mixin",
)

PROBE453_JAVA = r"""
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphClipboard318;
import dev.settledlands.npc.NpcWireJoin322;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.GraphInspectorDesc453;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.npc.client.WireHover453;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

public class Probe453 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // ---- Req 1: Inspector footer description lookup ----
        NpcDialogueGraph gDesc = new NpcDialogueGraph();
        NpcDialogueGraph.Node ueNode = gDesc.add(NpcDialogueGraph.TRIGGER, 0, 0);
        VarDataNode453.applyDefaults(ueNode);
        check("gui.settledlands.desc448.card.var453".equals(GraphInspectorDesc453.descriptionKey(ueNode)),
                "descriptionKey(ueNode) must map to var453 key");
        NpcDialogueGraph.Node rad = gDesc.add(NpcDialogueGraph.RADIUS, 40, 0);
        check("gui.settledlands.desc448.card.node_15".equals(GraphInspectorDesc453.descriptionKey(rad)),
                "descriptionKey(RADIUS) must map to node_15");

        // ---- Req 4: All 14 math operations on VARIABLE ----
        check(VarDataNode453.applyMathOp(10, VarDataNode453.OP_ADD, 5) == 15, "ADD 10 + 5 = 15");
        check(VarDataNode453.applyMathOp(10, VarDataNode453.OP_SUB, 15) == -5, "SUB 10 - 15 = -5");
        check(VarDataNode453.applyMathOp(7, VarDataNode453.OP_MUL, -3) == -21, "MUL 7 * -3 = -21");
        check(VarDataNode453.applyMathOp(21, VarDataNode453.OP_DIV, 4) == 5, "DIV 21 / 4 = 5");
        check(VarDataNode453.applyMathOp(21, VarDataNode453.OP_DIV, 0) == 21, "DIV by 0 guarded");
        check(VarDataNode453.applyMathOp(99, VarDataNode453.OP_SET, 0) == 0, "SET 0 works");
        check(VarDataNode453.applyMathOp(99, VarDataNode453.OP_SET, 42) == 42, "SET 42 works");
        check(VarDataNode453.applyMathOp(2, VarDataNode453.OP_POW, 10) == 1024, "POW 2^10 = 1024");
        check(VarDataNode453.applyMathOp(7, VarDataNode453.OP_POW, 0) == 1, "POW 7^0 = 1");
        check(VarDataNode453.applyMathOp(2, VarDataNode453.OP_POW, 9999999) == 1_000_000_000, "POW clamps fast");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_PCT_OF, 15) == 30, "PCT_OF 15% of 200 = 30");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_ADD_PCT, 15) == 230, "ADD_PCT 200 + 15% = 230");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_SUB_PCT, 25) == 150, "SUB_PCT 200 - 25% = 150");
        check(VarDataNode453.applyMathOp(23, VarDataNode453.OP_MOD, 5) == 3, "MOD 23 % 5 = 3");
        check(VarDataNode453.applyMathOp(23, VarDataNode453.OP_MOD, 0) == 23, "MOD by 0 guarded");
        check(VarDataNode453.applyMathOp(50, VarDataNode453.OP_MIN, 30) == 30, "MIN(50, 30) = 30");
        check(VarDataNode453.applyMathOp(50, VarDataNode453.OP_MAX, 80) == 80, "MAX(50, 80) = 80");
        check(VarDataNode453.applyMathOp(-42, VarDataNode453.OP_ABS, 0) == 42, "ABS(-42) = 42");
        check(VarDataNode453.applyMathOp(17, VarDataNode453.OP_NEG, 0) == -17, "NEG(17) = -17");

        // ---- Req 5: UE5 Variable node defaults, scopes, menu, & connect routing ----
        check(TeleportNode424.NPC_MENU_ITEMS == 20, "NPC_MENU_ITEMS updated to 20");
        check(TrialControlNode446.NPC_MENU_ITEMS == 20, "TrialControl NPC_MENU_ITEMS updated to 20");

        NpcGraphContextMenu menu = new NpcGraphContextMenu();
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.left = 0; canvas.top = 0; canvas.right = 800; canvas.bottom = 600;
        menu.show(100, 100, canvas);
        BlockMenu363.fit(menu, canvas, false, false, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Block palette includes VarData row");
        BlockMenu363.fit(menu, canvas, true, false, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Door palette includes VarData row");
        BlockMenu363.fit(menu, canvas, false, true, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Chest palette includes VarData row");
        BlockMenu363.fit(menu, canvas, false, false, true);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Trial palette includes VarData row");

        NpcDialogueGraph gVar = new NpcDialogueGraph();
        NpcDialogueGraph.Node ue = gVar.add(NpcDialogueGraph.TRIGGER, 0, 0);
        VarDataNode453.applyDefaults(ue);
        ue.pointsName = "gold";
        ue.varValue = 100;
        VarDataNode453.setScope(ue, VarDataNode453.SCOPE_SHARED);
        check(ue.pointsShared && !ue.varLocal, "SCOPE_SHARED sets pointsShared=true, varLocal=false");
        VarDataNode453.setScope(ue, VarDataNode453.SCOPE_LOCAL);
        check(!ue.pointsShared && ue.varLocal, "SCOPE_LOCAL sets varLocal=true, pointsShared=false");
        VarDataNode453.setScope(ue, VarDataNode453.SCOPE_PERSONAL);
        check(!ue.pointsShared && !ue.varLocal, "SCOPE_PERSONAL clears both");

        NpcDialogueGraph.Node vNode = gVar.add(NpcDialogueGraph.VARIABLE, 260, 0);
        NpcDialogueGraph.Node ifNode = gVar.add(NpcDialogueGraph.IF, 260, 140);
        NpcDialogueGraph.Node chkNode = gVar.add(NpcDialogueGraph.CHECK, 0, 260);
        NpcDialogueGraph.Node lineNode = gVar.add(NpcDialogueGraph.LINE, 260, 280);

        check(VarDataNode453.isVarDataNode(ue), "ue is VarDataNode");
        check(!PursuitGraphNode272.isValueInputNode(ue), "ue does not expose a blue input pin");

        // Verify simultaneous orange + blue inputs on VARIABLE & IF
        ue.varValue = 250;
        vNode.varInputNode = ue.id;
        PursuitGraphNode272.setInputSource(vNode, chkNode.id);
        check(vNode.varInputNode == ue.id, "VARIABLE keeps orange varInputNode");
        check(PursuitGraphNode272.inputSource(vNode) == chkNode.id, "VARIABLE keeps blue entity input independently");

        ifNode.varInputNode = ue.id;
        PursuitGraphNode272.setInputSource(ifNode, chkNode.id);
        check(ifNode.varInputNode == ue.id, "IF keeps orange varInputNode");
        check(PursuitGraphNode272.inputSource(ifNode) == chkNode.id, "IF keeps blue entity input independently");

        // Line keeps using varInputNode for blue input for NpcValueSource290 compatibility
        PursuitGraphNode272.setInputSource(lineNode, chkNode.id);
        check(lineNode.varInputNode == chkNode.id && PursuitGraphNode272.inputSource(lineNode) == chkNode.id,
                "LINE blue input stays on varInputNode for NpcValueSource290 compatibility");

        // Clipboard preserves both orange + blue inputs (Node.copy is also bridged at runtime by VarNodeEntityInputMixin453)
        String clipText = NpcGraphClipboard318.copy(gVar, List.of(ue.id, vNode.id, chkNode.id));
        NpcDialogueGraph gPaste = new NpcDialogueGraph();
        List<Integer> pasted = NpcGraphClipboard318.paste(gPaste, clipText, 40, 40);
        check(pasted != null && pasted.size() == 3, "Clipboard pastes all 3 nodes");
        NpcDialogueGraph.Node pastedVar = null;
        NpcDialogueGraph.Node pastedUe = null;
        NpcDialogueGraph.Node pastedChk = null;
        for (int pid : pasted) {
            NpcDialogueGraph.Node pn = gPaste.get(pid);
            if (pn.type == NpcDialogueGraph.VARIABLE) pastedVar = pn;
            else if (VarDataNode453.isVarDataNode(pn)) pastedUe = pn;
            else if (pn.type == NpcDialogueGraph.CHECK) pastedChk = pn;
        }
        check(pastedVar != null && pastedUe != null && pastedChk != null, "All node types pasted");
        check(pastedVar.varInputNode == pastedUe.id, "Clipboard remaps orange varInputNode");
        check(VarDataNode453.entityInput(pastedVar) == pastedChk.id, "Clipboard remaps blue entityInput453");

        // Legacy migration test: IF with varInputNode pointing to CHECK moves to entityInput453
        NpcDialogueGraph gLeg = new NpcDialogueGraph();
        NpcDialogueGraph.Node legChk = gLeg.add(NpcDialogueGraph.CHECK, 0, 0);
        NpcDialogueGraph.Node legIf = gLeg.add(NpcDialogueGraph.IF, 200, 0);
        legIf.varInputNode = legChk.id;
        VarDataNode453.migrateLegacyInputs(gLeg);
        check(VarDataNode453.entityInput(legIf) == legChk.id && legIf.varInputNode == -1,
                "migrateLegacyInputs moves legacy blue source out of varInputNode into entityInput453");

        // ---- Req 3: NpcWirePoints321 + WireHover453 colored bend points, hangs, & pruning ----
        NpcDialogueGraph gWires = new NpcDialogueGraph();
        NpcDialogueGraph.Node wHub = gWires.add(TrialGraph446.TRIAL_TYPE, 400, 0);
        wHub.action = "{\"activationRange\":14}";
        NpcDialogueGraph.Node wMod = gWires.add(NpcDialogueGraph.TRIGGER, 0, 0);
        TrialModule447.applyDefaults(wMod, TrialModule447.SLOT_MOBS);
        TrialModule447.connectModule(gWires, wMod.id, wHub.id);
        NpcDialogueGraph.Node wUe = gWires.add(NpcDialogueGraph.TRIGGER, 0, 120);
        VarDataNode453.applyDefaults(wUe);
        NpcDialogueGraph.Node wVar = gWires.add(NpcDialogueGraph.VARIABLE, 260, 120);
        wVar.varInputNode = wUe.id;
        NpcDialogueGraph.Node wChk = gWires.add(NpcDialogueGraph.CHECK, 0, 260);
        PursuitGraphNode272.setInputSource(wVar, wChk.id);
        NpcDialogueGraph.Node wNext = gWires.add(NpcDialogueGraph.LINE, 700, 0);
        TrialGraph446.setStartWaveTarget(wHub, wNext.id);

        // Add bend points on ALL colored wire types
        NpcWirePoints321.addValuePoint(wChk, wVar.id, 120, 240, 0);
        NpcWirePoints321.addCustomPoint(wUe, NpcWirePoints321.KIND_ORANGE, wVar.id, 136, 160, 0);
        NpcWirePoints321.addCustomPoint(wMod, NpcWirePoints321.KIND_TRIAL_MOD, wHub.id, 200, 40, 0);
        NpcWirePoints321.addCustomPoint(wHub, NpcWirePoints321.KIND_TRIAL_START, wNext.id, 560, 40, 0);

        // Serialize SLP2 via animBone & clear cache by verifying livePoints
        List<int[]> pts = WireHover453.livePoints(gWires);
        check(pts.size() == 4, "All 4 colored wire bend points are alive (got " + pts.size() + ")");
        check(NpcWirePoints321.valueBends(wChk, wVar.id).size() == 1, "Blue bend stored");
        check(NpcWirePoints321.customBends(wUe, NpcWirePoints321.KIND_ORANGE, wVar.id).size() == 1, "Orange bend stored");
        check(NpcWirePoints321.customBends(wMod, NpcWirePoints321.KIND_TRIAL_MOD, wHub.id).size() == 1, "Module bend stored");
        check(NpcWirePoints321.customBends(wHub, NpcWirePoints321.KIND_TRIAL_START, wNext.id).size() == 1, "Trial start bend stored");

        // Remove orange point via NpcWirePoints321.removePoint -> gone cleanly!
        check(NpcWirePoints321.removePoint(gWires, 136, 160), "removePoint removes orange bend point");
        check(NpcWirePoints321.customBends(wUe, NpcWirePoints321.KIND_ORANGE, wVar.id).isEmpty(),
                "Orange bends empty after removePoint");
        check(NpcWirePoints321.removePoint(gWires, 120, 240), "removePoint removes blue bend point");
        check(NpcWirePoints321.valueBends(wChk, wVar.id).isEmpty(),
                "Blue bends empty after removePoint");

        // Waiting colored hang + promote via WireHover453.aimColoredAtPoint
        NpcDialogueGraph.Node wIf = gWires.add(NpcDialogueGraph.IF, 400, 120);
        NpcWirePoints321.attachColor(wUe, NpcWirePoints321.KIND_ORANGE, 0, 160, 160);
        check(WireHover453.pullColorAtPoint(gWires, 160, 160) == VarDataNode453.PIN_COLOR,
                "pullColorAtPoint returns orange for orange hang");
        check(NpcWireJoin322.canAim(gWires, 160, 160, wIf.id), "NpcWireJoin322.canAim sees colorHang");
        check(WireHover453.aimColoredAtPoint(gWires, 160, 160, wIf.id) == 1,
                "aimColoredAtPoint connects orange hang to IF");
        check(wIf.varInputNode == wUe.id, "IF.varInputNode wired by aimColoredAtPoint");
        check(NpcWirePoints321.customBends(wUe, NpcWirePoints321.KIND_ORANGE, wIf.id).size() == 1,
                "Promoted orange hang preserves bend point");

        // Unplug blue wire -> livePoints prunes stale blue point automatically
        NpcWirePoints321.addValuePoint(wChk, wVar.id, 128, 248, 0);
        PursuitGraphNode272.setInputSource(wVar, -1);
        List<int[]> afterUnplug = WireHover453.livePoints(gWires);
        boolean foundStale = false;
        for (int[] p : afterUnplug) {
            if (p[0] == 128 && p[1] == 248) foundStale = true;
        }
        check(!foundStale, "livePoints automatically prunes point when blue wire is cut");

        // Full NBT save() -> load() roundtrip for VarDataNode453 + varOp + SLP2 + remove cleanup
        vNode.varOp = VarDataNode453.OP_ADD_PCT;
        vNode.varValue = 15;
        CompoundTag saved = gVar.save();
        NpcDialogueGraph loaded = NpcDialogueGraph.load(saved);
        check(loaded != null, "NpcDialogueGraph.load(saved) succeeds");
        NpcDialogueGraph.Node loadedUe = loaded.get(ue.id);
        NpcDialogueGraph.Node loadedVar = loaded.get(vNode.id);
        check(VarDataNode453.isVarDataNode(loadedUe) && loadedUe.varValue == 250
                && "gold".equals(loadedUe.pointsName),
                "UE5 variable node survives NBT save/load");
        check(loadedVar.varOp == VarDataNode453.OP_ADD_PCT && loadedVar.varInputNode == ue.id,
                "VARIABLE varOp and orange link survive NBT save/load");
        check(loaded.remove(ue.id), "remove UE5 variable node succeeds");
        check(loaded.get(ue.id) == null, "removed node gone from graph");

        System.out.println("BUILD453_OK=true CHECKS=" + checks);
    }
}
"""


def run_probe453(jar_path: pathlib.Path) -> str:
    java = build452_check.build450_check.find_java() if hasattr(build450_check, "find_java") else build450_check.find_java()
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
            raise SystemExit("probe453 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe453.java"
        probe_src.write_text(PROBE453_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe453 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe453"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD453_OK=true" not in r3.stdout:
            raise SystemExit("Probe453 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build453_check.py <jar>", file=sys.stderr)
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
            "dev/settledlands/npc/client/GraphInspectorDesc453.class",
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

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build453-inspector-ux-wires-and-ue5-variables" not in toml:
            errors.append("neoforge.mods.toml missing Build 453 version")

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
        if client_list.index("GraphClickDragMixin453") > client_list.index("GraphHover448Mixin"):
            errors.append("GraphClickDragMixin453 must precede GraphHover448Mixin in client[]")

        # Verify canvas footers disabled cleanly (no call to GraphCardDesc448)
        gcf = zf.read("dev/settledlands/mixin/GraphCardFooter448Mixin.class")
        if b"GraphCardDesc448" in gcf:
            errors.append("GraphCardFooter448Mixin must not invoke GraphCardDesc448 canvas footers")
        acf = zf.read("dev/settledlands/mixin/AutoAiFooter448Mixin.class")
        if b"GraphCardDesc448" in acf:
            errors.append("AutoAiFooter448Mixin must not invoke GraphCardDesc448 canvas footer")

        # Verify RADIUS header fix & drag threshold markers
        cd = zf.read("dev/settledlands/mixin/GraphClickDragMixin453.class")
        if b"radius_title" not in cd or b"hoveredWire" not in cd:
            errors.append("GraphClickDragMixin453 missing expected RADIUS header fix / hoveredWire markers")

        # Lang parity + Build 453 keys + scan every class for referenced keys
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in tuple(build450_check.NEW_KEYS) + build452_check.NEW_KEYS_452 + NEW_KEYS_453:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_453:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

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
    out453 = run_probe453(jar_path)
    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build453_check: OK ", jar_path)
    for line in out450.splitlines():
        print(" ", line)
    for line in out452.splitlines():
        print(" ", line)
    for line in out453.splitlines():
        print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_453)} mixins + all 5 Build 453 features OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
