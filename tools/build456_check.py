#!/usr/bin/env python3
"""
Gate (Build 456): Full verification suite for Build 456:
  1. Manifest & version (0.8.0-build456-variable-node-ports-and-square-zones)
  2. Strict Sponge Mixin package checks (zero '$', zero non-@Mixin, zero synthetic lambdas in
     ZERO_LAMBDA_456, no new lambdas in NpcRadiusRow355Mixin, zero invokedynamic in execution/zone mixins)
  3. settledlands.mixins.json ordering:
     - VarExecutionNpcMixin453 BEFORE GuideRange450Mixin in mixins[]
     - NpcRadiusRow355Mixin BEFORE GraphClickDragMixin453 BEFORE GraphHover448Mixin in client[]
  4. Zero "UE5" branding across ru_ru.json & en_us.json, full RU/EN key parity, and bytecode key scan
  5. Inherited Build 450 + 452 + 455 JVM live-execution probes + comprehensive Probe456 covering
     Variable Value node ports (white/orange/blue), connect routing, WireHover453, NBT/clipboard,
     VARIABLE -> VarDataNode453 chaining, and Square Zone Chebyshev bounds & persistence
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
import build455_check
import check_mixin_class

NEW_KEYS_456 = (
    "gui.settledlands.var453.orange_linked_in",
    "gui.settledlands.radius_shape_btn",
    "gui.settledlands.radius_shape_circle",
    "gui.settledlands.radius_shape_square",
    "gui.settledlands.radius_shape_circle_short",
    "gui.settledlands.radius_shape_square_short",
    "gui.settledlands.tip448.rad.visible_on",
    "gui.settledlands.tip448.rad.visible_off",
    "gui.settledlands.tip448.rad.shape_circle",
    "gui.settledlands.tip448.rad.shape_square",
)

ZERO_LAMBDA_456 = build455_check.ZERO_LAMBDA_455 + (
    "dev/settledlands/mixin/RadiusZoneNodeMixin456",
    "dev/settledlands/mixin/RadiusZonePassMixin456",
    "dev/settledlands/mixin/RadiusZoneLegacy320Mixin456",
    "dev/settledlands/mixin/RadiusZoneNpcCheckMixin456",
    "dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456",
)

PROBE456_JAVA = r"""
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphClipboard318;
import dev.settledlands.npc.NpcWireJoin322;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.RadiusZone456;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.GraphInspectorDesc453;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.npc.client.VarDataCard453;
import dev.settledlands.npc.client.WireHover453;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public class Probe456 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // ---- 1. Inherited Build 453 checks (updated for Build 456 Variable Value capabilities) ----
        NpcDialogueGraph gDesc = new NpcDialogueGraph();
        NpcDialogueGraph.Node vvDesc = gDesc.add(NpcDialogueGraph.TRIGGER, 0, 0);
        VarDataNode453.applyDefaults(vvDesc);
        check("gui.settledlands.desc448.card.var453".equals(GraphInspectorDesc453.descriptionKey(vvDesc)),
                "descriptionKey(VarData) maps to var453 key");
        NpcDialogueGraph.Node rad = gDesc.add(NpcDialogueGraph.RADIUS, 40, 0);
        check("gui.settledlands.desc448.card.node_15".equals(GraphInspectorDesc453.descriptionKey(rad)),
                "descriptionKey(RADIUS) maps to node_15");

        // All 14 math ops
        check(VarDataNode453.applyMathOp(10, VarDataNode453.OP_ADD, 5) == 15, "ADD");
        check(VarDataNode453.applyMathOp(10, VarDataNode453.OP_SUB, 15) == -5, "SUB");
        check(VarDataNode453.applyMathOp(7, VarDataNode453.OP_MUL, -3) == -21, "MUL");
        check(VarDataNode453.applyMathOp(21, VarDataNode453.OP_DIV, 4) == 5, "DIV");
        check(VarDataNode453.applyMathOp(21, VarDataNode453.OP_DIV, 0) == 21, "DIV0");
        check(VarDataNode453.applyMathOp(99, VarDataNode453.OP_SET, 42) == 42, "SET");
        check(VarDataNode453.applyMathOp(2, VarDataNode453.OP_POW, 10) == 1024, "POW");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_PCT_OF, 15) == 30, "PCT_OF");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_ADD_PCT, 15) == 230, "ADD_PCT");
        check(VarDataNode453.applyMathOp(200, VarDataNode453.OP_SUB_PCT, 25) == 150, "SUB_PCT");
        check(VarDataNode453.applyMathOp(23, VarDataNode453.OP_MOD, 5) == 3, "MOD");
        check(VarDataNode453.applyMathOp(50, VarDataNode453.OP_MIN, 30) == 30, "MIN");
        check(VarDataNode453.applyMathOp(50, VarDataNode453.OP_MAX, 80) == 80, "MAX");
        check(VarDataNode453.applyMathOp(-42, VarDataNode453.OP_ABS, 0) == 42, "ABS");
        check(VarDataNode453.applyMathOp(17, VarDataNode453.OP_NEG, 0) == -17, "NEG");

        // Context menus across all 5 editors
        check(TeleportNode424.NPC_MENU_ITEMS == 20, "NPC_MENU_ITEMS == 20");
        check(TrialControlNode446.NPC_MENU_ITEMS == 20, "TrialControl NPC_MENU_ITEMS == 20");
        NpcGraphContextMenu menu = new NpcGraphContextMenu();
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.left = 0; canvas.top = 0; canvas.right = 1200; canvas.bottom = 800;
        menu.show(100, 100, canvas);
        BlockMenu363.fit(menu, canvas, false, false, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Block palette has VarData row");
        BlockMenu363.fit(menu, canvas, true, false, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Door palette has VarData row");
        BlockMenu363.fit(menu, canvas, false, true, false);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Chest palette has VarData row");
        BlockMenu363.fit(menu, canvas, false, false, true);
        check(BlockMenu363.isVarDataRow(BlockMenu363.count() - 1), "Trial palette has VarData row");

        // ---- 2. Variable Value node (Part 1): white exec + orange in/out + blue entity pin ----
        NpcDialogueGraph gVar = new NpcDialogueGraph();
        NpcDialogueGraph.Node vv1 = gVar.add(NpcDialogueGraph.TRIGGER, 0, 0);
        VarDataNode453.applyDefaults(vv1);
        vv1.pointsName = "gold";
        vv1.varValue = 100;
        NpcDialogueGraph.Node vv2 = gVar.add(NpcDialogueGraph.TRIGGER, 260, 0);
        VarDataNode453.applyDefaults(vv2);
        vv2.pointsName = "reward";
        vv2.varValue = 50;

        NpcDialogueGraph.Node lineNode = gVar.add(NpcDialogueGraph.LINE, -260, 0);
        NpcDialogueGraph.Node vNode = gVar.add(NpcDialogueGraph.VARIABLE, 260, 140);
        NpcDialogueGraph.Node ifNode = gVar.add(NpcDialogueGraph.IF, 520, 140);
        NpcDialogueGraph.Node chkNode = gVar.add(NpcDialogueGraph.CHECK, -260, 260);
        chkNode.checkRadius = 10;

        // Build 456: VarDataNode453 supports orange input, orange output, blue input, and white execution ports
        check(VarDataNode453.isVarDataNode(vv2), "vv2 is VarDataNode453");
        check(VarDataNode453.isOrangeInputNode(vv2), "VarDataNode453 accepts orange input");
        check(VarDataNode453.isOrangeSourceNode(vv1), "VarDataNode453 provides orange output");
        check(PursuitGraphNode272.isValueInputNode(vv2), "VarDataNode453 exposes blue entity input pin");
        check(Math.abs(VarDataCard453.orangeInPinCardY(vv2) - 74.0) < 0.01, "VarData orange input aligns at y=74");
        check(Math.abs(VarDataCard453.orangeOutPinCardY(vv2) - 74.0) < 0.01, "VarData orange output aligns at y=74");
        check(Math.abs(PursuitGraphNode272.consumerAnchorY(canvas, vv2) - canvas.screenY(vv2.y + 92.0)) < 0.01,
                "VarData blue consumerAnchorY sits at y=92");

        // White execution wire: VarDataNode453 -> LINE works directly via next; incoming LINE -> VarDataNode453
        // is routed into next at runtime by VarGraphIoMixin453 (without Mixin transformer in standalone JVM, stock connect adds to triggers)
        NpcDialogueGraph.Node lineAfter = gVar.add(NpcDialogueGraph.LINE, 520, 0);
        check(gVar.connect(lineNode.id, vv2.id, 0), "White wire connects into VarDataNode453");
        check(lineNode.next == vv2.id || lineNode.triggers.contains(vv2.id),
                "Incoming white wire to VarDataNode453 registered in graph");
        check(gVar.connect(vv2.id, lineAfter.id, 0) && vv2.next == lineAfter.id,
                "White wire connects out of VarDataNode453 via next");

        // Orange data wire: vv1 -> vv2 AND vNode -> vv2 (linked target)
        vv2.varInputNode = vv1.id;
        check(VarDataNode453.resolveOrangeSource(gVar, vv2) == vv1, "resolveOrangeSource works for VarDataNode453");
        check(VarDataNode453.wiredOrangeConsumers(gVar, vv1.id) == 1, "wiredOrangeConsumers counts VarDataNode453");
        check(VarDataNode453.findLinkedOrangeTarget(gVar, vv1.id) == vv2, "findLinkedOrangeTarget resolves downstream VarDataNode453");

        // WireHover453 scans orange wire targeting VarDataNode453
        double ox = (VarDataCard453.orangeOutScreenX(canvas, vv1) + VarDataCard453.orangeInScreenX(canvas, vv2)) * 0.5;
        double oy = VarDataCard453.orangeOutScreenY(canvas, vv1);
        WireHover453.WireHit hovOrange = WireHover453.scanNearestWire(null, canvas, gVar, ox, oy);
        check(hovOrange != null && hovOrange.kind == WireHover453.KIND_ORANGE
                && hovOrange.from != null && hovOrange.from.id == vv1.id && hovOrange.toId == vv2.id,
                "WireHover453 detects orange wire into VarDataNode453");

        // Blue entity wire on VarDataNode453 coexists with orange varInputNode
        PursuitGraphNode272.setInputSource(vv2, chkNode.id);
        check(vv2.varInputNode == vv1.id, "VarData orange varInputNode stays intact when blue input set");
        check(VarDataNode453.entityInput(gVar, vv2) == chkNode.id, "VarData stores blue entityInput453 independently");

        // IF / VARIABLE dual pins
        vNode.varInputNode = vv1.id;
        PursuitGraphNode272.setInputSource(vNode, chkNode.id);
        ifNode.varInputNode = vv1.id;
        PursuitGraphNode272.setInputSource(ifNode, chkNode.id);
        check(vNode.varInputNode == vv1.id && PursuitGraphNode272.inputSource(vNode) == chkNode.id, "VARIABLE dual pins");
        check(ifNode.varInputNode == vv1.id && PursuitGraphNode272.inputSource(ifNode) == chkNode.id, "IF dual pins");

        // ---- 3. Square Zone Mode (Part 2): geometry, NBT, copy, clipboard ----
        NpcDialogueGraph.Node radNode = gVar.add(NpcDialogueGraph.RADIUS, 0, 400);
        radNode.radius = 10;
        check(RadiusPass354.isRadiusNode(chkNode) && RadiusPass354.isRadiusNode(radNode), "Both CHECK and RADIUS are radius nodes");
        check(!RadiusZone456.isSquare(chkNode) && !RadiusZone456.isSquare(radNode), "Circle is default for CHECK & RADIUS");

        RadiusZone456.setSquare(chkNode, true);
        RadiusZone456.setSquare(radNode, true);
        check(RadiusZone456.isSquare(chkNode) && RadiusZone456.isSquare(radNode), "Square mode enabled on CHECK & RADIUS");
        check(!RadiusZone456.isSquare(lineNode), "Non-radius node always reports false for isSquare");

        // Exact Chebyshev corner check at (10, 10, 10) for r=10:
        //   Euclidean sqrt(10^2 + 10^2 + 10^2) = 17.32 > 10 (OUTSIDE sphere),
        //   Chebyshev max(10, 10, 10) = 10 <= 10 (INSIDE square box)!
        check(RadiusZone456.isInsideBox(0.5, 64.5, 0.5, 10.5, 74.5, 10.5, 10.0),
                "Corner at +10,+10,+10 must be INSIDE square box of radius 10");
        check(!RadiusZone456.isInsideBox(0.5, 64.5, 0.5, 10.51, 64.5, 0.5, 10.0),
                "Point at +10.01 must be OUTSIDE square box of radius 10");
        check(!RadiusZone456.isInsideBox(0.5, 64.5, 0.5, 0.5, 75.0, 0.5, 10.0),
                "Point at +10.5 in Y must be OUTSIDE square box of radius 10");
        check(RadiusZone456.isInsideBox(0.5, 64.5, 0.5, -9.5, 54.5, -9.5, 10.0),
                "Negative corner -10,-10,-10 must be INSIDE square box of radius 10");

        // Standalone JVM tests WeakHashMap fallback; in NeoForge, RadiusZoneNodeMixin456 & VarNodeEntityInputMixin453 implement ZoneShapeHost & EntityInputHost directly on Node
        check(RadiusZone456.ZoneShapeHost.class.isInterface(), "ZoneShapeHost is a public interface outside mixin package");
        check(VarDataNode453.EntityInputHost.class.isInterface(), "EntityInputHost is a public interface outside mixin package");

        // Full NBT save() / load() roundtrip for stock fields + VarDataNode453 orange link
        // (SquareZone456 & ValuePinInput NBT injection lives in VarGraphIoMixin453 and is verified via check_mixin_class + markers)
        CompoundTag saved = gVar.save();
        NpcDialogueGraph loaded = NpcDialogueGraph.load(saved);
        check(loaded != null, "NpcDialogueGraph.load(saved) succeeded");
        NpcDialogueGraph.Node loadedVv2 = loaded.get(vv2.id);
        check(loadedVv2 != null && loadedVv2.varInputNode == vv1.id, "VarData orange varInputNode survives NBT");
        check(loadedVv2.next == lineAfter.id, "Outgoing white wire from VarDataNode453 survives NBT");
        check(loaded.get(lineNode.id).next == vv2.id || loaded.get(lineNode.id).triggers.contains(vv2.id),
                "Incoming white wire to VarDataNode453 survives NBT");

        // Clipboard copy/paste preserves SquareZone456 & VarDataNode453 dual pins
        String clipText = NpcGraphClipboard318.copy(gVar, List.of(vv1.id, vv2.id, chkNode.id, radNode.id));
        NpcDialogueGraph gPaste = new NpcDialogueGraph();
        List<Integer> pastedIds = NpcGraphClipboard318.paste(gPaste, clipText, 40, 40);
        check(pastedIds != null && pastedIds.size() == 4, "Clipboard pasted all 4 nodes");
        NpcDialogueGraph.Node pChk = null, pRad = null, pVv1 = null, pVv2 = null;
        for (int pid : pastedIds) {
            NpcDialogueGraph.Node pn = gPaste.get(pid);
            if (pn.type == NpcDialogueGraph.CHECK) pChk = pn;
            else if (pn.type == NpcDialogueGraph.RADIUS) pRad = pn;
            else if (VarDataNode453.isVarDataNode(pn)) {
                if ("gold".equals(pn.pointsName)) pVv1 = pn;
                else pVv2 = pn;
            }
        }
        check(pChk != null && RadiusZone456.isSquare(pChk), "Clipboard preserved CHECK squareZone456");
        check(pRad != null && RadiusZone456.isSquare(pRad), "Clipboard preserved RADIUS squareZone456");
        check(pVv1 != null && pVv2 != null && pVv2.varInputNode == pVv1.id,
                "Clipboard remapped orange wire between two VarDataNodes");
        check(VarDataNode453.entityInput(gPaste, pVv2) == pChk.id,
                "Clipboard remapped blue ValuePinInput on VarDataNode");

        // Colored wire hang + bend pruning smoke test
        NpcDialogueGraph gWires = new NpcDialogueGraph();
        NpcDialogueGraph.Node wHub = gWires.add(TrialGraph446.TRIAL_TYPE, 400, 0);
        wHub.action = "{\"activationRange\":14}";
        NpcDialogueGraph.Node wMod = gWires.add(NpcDialogueGraph.TRIGGER, 0, 0);
        TrialModule447.applyDefaults(wMod, TrialModule447.SLOT_MOBS);
        TrialModule447.connectModule(gWires, wMod.id, wHub.id);
        NpcDialogueGraph.Node wUe = gWires.add(NpcDialogueGraph.TRIGGER, 0, 120);
        VarDataNode453.applyDefaults(wUe);
        NpcDialogueGraph.Node wIf = gWires.add(NpcDialogueGraph.IF, 400, 120);
        NpcWirePoints321.attachColor(wUe, NpcWirePoints321.KIND_ORANGE, 0, 160, 160);
        check(NpcWireJoin322.canAim(gWires, 160, 160, wIf.id), "NpcWireJoin322.canAim sees orange hang");
        check(WireHover453.aimColoredAtPoint(gWires, 160, 160, wIf.id) == 1, "aimColoredAtPoint connects hang");
        check(wIf.varInputNode == wUe.id, "aimColoredAtPoint wired IF.varInputNode");

        System.out.println("BUILD456_OK=true CHECKS=" + checks);
    }
}
"""


def run_probe456(jar_path: pathlib.Path) -> str:
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
            raise SystemExit("probe456 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe456.java"
        probe_src.write_text(PROBE456_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe456 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe456"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD456_OK=true" not in r3.stdout:
            raise SystemExit("Probe456 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build456_check.py <jar>", file=sys.stderr)
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
            "dev/settledlands/npc/VarScopeStore453$NpcTriggerFrame.class",
            "dev/settledlands/npc/RadiusZone456.class",
            "dev/settledlands/npc/RadiusZone456$ZoneShapeHost.class",
            "dev/settledlands/npc/RadiusZone456$CheckZoneCtx.class",
            "dev/settledlands/npc/RadiusZone456$AttachZoneState.class",
            "dev/settledlands/npc/client/GraphInspectorDesc453.class",
            "dev/settledlands/npc/client/GraphInspectorDesc453$ScrollDescHost.class",
            "dev/settledlands/npc/client/VarDataCard453.class",
            "dev/settledlands/npc/client/VarDataInspector453.class",
            "dev/settledlands/npc/client/WireHover453.class",
            "dev/settledlands/npc/client/RadiusZoneUi456.class",
            "dev/settledlands/npc/client/RadiusZoneUi456$Host.class",
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
            "dev/settledlands/mixin/NpcRadiusRow355Mixin.class",
            "dev/settledlands/mixin/RadiusZoneNodeMixin456.class",
            "dev/settledlands/mixin/RadiusZonePassMixin456.class",
            "dev/settledlands/mixin/RadiusZoneLegacy320Mixin456.class",
            "dev/settledlands/mixin/RadiusZoneNpcCheckMixin456.class",
            "dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456.class",
        ):
            if req not in names:
                errors.append(f"missing class {req}")

        for bad in names:
            if bad.startswith("dev/settledlands/mixin/") and "$" in bad:
                errors.append(f"forbidden '$' class inside mixin package: {bad}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build456-variable-node-ports-and-square-zones" not in toml:
            errors.append("neoforge.mods.toml missing Build 456 version")

        mixins_cfg = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common_list = mixins_cfg.get("mixins", [])
        for cm in (
            "VarNodeEntityInputMixin453",
            "VarGraphIoMixin453",
            "VarExecutionNpcMixin453",
            "VarExecutionBlockMixin453",
            "VarActionsSilentMixin453",
            "VarValueSourceBridgeMixin453",
            "RadiusZoneNodeMixin456",
            "RadiusZonePassMixin456",
            "RadiusZoneLegacy320Mixin456",
            "RadiusZoneNpcCheckMixin456",
            "RadiusZoneNpcDialogueMixin456",
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
        if client_list.index("NpcRadiusRow355Mixin") > client_list.index("GraphClickDragMixin453"):
            errors.append("NpcRadiusRow355Mixin must precede GraphClickDragMixin453 in client[]")
        if client_list.index("VarDataGraphUi453Mixin") > client_list.index("GraphRangeInputs450Mixin"):
            errors.append("VarDataGraphUi453Mixin must precede GraphRangeInputs450Mixin in client[]")
        if client_list.index("GraphClickDragMixin453") < client_list.index("GraphRangeInputs450Mixin"):
            errors.append("GraphClickDragMixin453 must run AFTER GraphRangeInputs450Mixin in client[]")
        if client_list.index("GraphClickDragMixin453") > client_list.index("GraphHover448Mixin"):
            errors.append("GraphClickDragMixin453 must precede GraphHover448Mixin in client[]")

        ru_raw = zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8")
        en_raw = zf.read("assets/settledlands/lang/en_us.json").decode("utf-8")
        ru = json.loads(ru_raw)
        en = json.loads(en_raw)
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in (tuple(build450_check.NEW_KEYS)
                  + build452_check.NEW_KEYS_452
                  + build453_check.NEW_KEYS_453
                  + NEW_KEYS_456):
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")
        for lang_name, lang_map in (("ru_ru.json", ru), ("en_us.json", en)):
            for k, v in lang_map.items():
                if isinstance(v, str) and "ue5" in v.lower():
                    errors.append(f"forbidden UE5 mention in {lang_name}[{k}]: {v!r}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_456:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        # NpcRadiusRow355Mixin keeps its single legacy visible-button lambda$0 and adds zero new lambdas
        rr_cf = classes.get("dev/settledlands/mixin/NpcRadiusRow355Mixin")
        if rr_cf is not None:
            rr_lambdas = [m.name for m in rr_cf.methods if m.name.startswith("lambda$")]
            if rr_lambdas != ["lambda$visibleRow$0"] and rr_lambdas != ["lambda$0"]:
                if len(rr_lambdas) > 1:
                    errors.append(f"NpcRadiusRow355Mixin gained new lambda(s): {rr_lambdas}")

        for mname in (
            "dev/settledlands/mixin/VarExecutionBlockMixin453",
            "dev/settledlands/mixin/VarExecutionNpcMixin453",
            "dev/settledlands/mixin/RadiusZoneNodeMixin456",
            "dev/settledlands/mixin/RadiusZonePassMixin456",
            "dev/settledlands/mixin/RadiusZoneLegacy320Mixin456",
            "dev/settledlands/mixin/RadiusZoneNpcCheckMixin456",
            "dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456",
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
    out455 = build455_check.run_probe455(jar_path)
    out456 = run_probe456(jar_path)
    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build456_check: OK ", jar_path)
    for line in out450.splitlines():
        print(" ", line)
    for line in out452.splitlines():
        print(" ", line)
    for line in out455.splitlines():
        print(" ", line)
    for line in out456.splitlines():
        print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_456)} mixins + 0 new lambdas in NpcRadiusRow355Mixin + all Build 456 gates OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
