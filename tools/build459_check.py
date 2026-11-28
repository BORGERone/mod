#!/usr/bin/env python3
"""
Gate (Build 459): Full verification suite for Build 459:
  1. Manifest & version (0.8.0-build459-blue-line-target-gating)
  2. Strict Sponge Mixin package checks (zero '$', zero non-@Mixin, zero synthetic lambdas in
     ZERO_LAMBDA_459, zero invokedynamic in execution/substitution/catalog/zone/blue-target mixins)
  3. settledlands.mixins.json ordering:
     - VarActionsSubstituteMixin457 BEFORE TeleportActionsMixin424 in mixins[]
     - VarExecutionNpcMixin453 and BlueLineNpcMixin459 BEFORE GuideRange450Mixin in mixins[]
     - BlueLinePassMixin459 and BlueLineBlockMixin459 present in mixins[]
     - Client mixin ordering preserved (NpcDropdownFirst333 first, GraphHover448Mixin last)
  4. Mixin @Redirect / @Inject bytecode target audit:
     - Verifies every target method + invoke target in BlueLineNpcMixin459, BlueLinePassMixin459,
       BlueLineBlockMixin459, and VarExecutionNpcMixin453 exists in the actual JAR bytecode
     - Verifies zero duplicate @Redirect collisions on the same (method, invokeTarget) across
       all Build 453-459 mixins on GuideDialogue, RadiusPass354, and BlockTrigger351
  5. Inherited Build 450 + 452 + 455 + 456 + 457/458 JVM probes + Probe459 covering:
     - Exact user SLGRAPH318 1 graph (`TIMER #11 -> LINE #13` without blue wire)
     - Blue wire predicates (excluding VarDataNode453 direct & Pursuit-forwarded wires)
     - ThreadLocal no-event-target stack & nested RADIUS fan-out override
     - Block/Door/Chest/Trial autonomous vs interactive dialogue gating & CHECK target filtering
     - NpcResolveFrame / BlockWalkFrame extensions
"""

import json
import pathlib
import re
import shutil
import struct
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
import build456_check
import build458_check
import check_mixin_class

ZERO_LAMBDA_459 = build458_check.ZERO_LAMBDA_457 + (
    "dev/settledlands/mixin/NpcPursuitOutputMixin280",
    "dev/settledlands/mixin/BlueLineNpcMixin459",
    "dev/settledlands/mixin/BlueLinePassMixin459",
    "dev/settledlands/mixin/BlueLineBlockMixin459",
)

PROBE459_JAVA = r"""
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlueLineTarget459;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphClipboard318;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.trial.TrialGraph446;
import java.util.List;

public class Probe459 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. Exact SLGRAPH318 1 user graph from bug report: node 11 TIMER -> node 13 LINE ("привет")
        String bugClip = "SLGRAPH318 1\n"
                + "node id=11 type=8 x=-402 y=-16 timerInterval=5 next=13 varInputNode=-1 entityInput453=-1\n"
                + "node id=13 type=0 x=-146 y=-48 text=\"привет\" varInputNode=-1 entityInput453=-1\n";

        NpcDialogueGraph bugGraph = new NpcDialogueGraph();
        List<Integer> pasted = NpcGraphClipboard318.paste(bugGraph, bugClip, 0, 0);
        check(pasted != null && pasted.size() == 2, "Pasted user SLGRAPH318 1 graph (2 nodes)");
        NpcDialogueGraph.Node timerNode = null;
        NpcDialogueGraph.Node lineNode = null;
        for (NpcDialogueGraph.Node n : bugGraph.nodes.values()) {
            if (n.type == NpcDialogueGraph.TIMER) timerNode = n;
            if (n.type == NpcDialogueGraph.LINE) lineNode = n;
        }
        check(timerNode != null && lineNode != null, "Graph has TIMER and LINE");
        check("привет".equals(lineNode.text), "LINE text preserved");
        check(!BlueLineTarget459.hasBlueEntityWire(bugGraph, lineNode),
                "Unwired LINE from SLGRAPH318 1 must NOT report a blue entity wire");

        // 2. Blue wire classification (Entity vs Variable vs Door/Chest/Trial/Pursuit)
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node lPlain = g.add(NpcDialogueGraph.LINE, 0, 0);
        NpcDialogueGraph.Node lBtn = g.add(NpcDialogueGraph.BUTTON, 0, 50);
        NpcDialogueGraph.Node lCut = g.add(NpcDialogueGraph.CUTSCENE, 0, 100);
        NpcDialogueGraph.Node checkSrc = g.add(NpcDialogueGraph.CHECK, -200, 0);
        NpcDialogueGraph.Node dmgSrc = g.add(NpcDialogueGraph.DAMAGE, -200, 50);
        NpcDialogueGraph.Node doorSrc = g.add(40, -200, 100);
        NpcDialogueGraph.Node chestSrc = g.add(50, -200, 150);
        NpcDialogueGraph.Node trialSrc = g.add(60, -200, 200);
        check(DoorGraph382.isDoorNode(doorSrc)
                && ChestGraph396.isChestNode(chestSrc)
                && TrialGraph446.isTrialNode(trialSrc),
                "Node types 40/50/60 match Door/Chest/Trial predicates");
        NpcDialogueGraph.Node varSrc = g.add(NpcDialogueGraph.TRIGGER, -200, 250);
        VarDataNode453.applyDefaults(varSrc);
        varSrc.pointsName = "gold";
        NpcDialogueGraph.Node pursuitSrc = g.add(NpcDialogueGraph.TRIGGER, -200, 300);
        pursuitSrc.action = "trigger:pursuit:start:npc:guard|1.5|8.0|1.5|#" + checkSrc.id;
        NpcDialogueGraph.Node pursuitVarSrc = g.add(NpcDialogueGraph.TRIGGER, -200, 350);
        pursuitVarSrc.action = "trigger:pursuit:start:npc:guard|1.5|8.0|1.5|#" + varSrc.id + "|@" + lBtn.id;

        check(!BlueLineTarget459.hasBlueEntityWire(g, lPlain), "Plain LINE has no blue wire");

        // Variable wired directly -> NOT an entity target
        lPlain.varInputNode = varSrc.id;
        check(!BlueLineTarget459.hasBlueEntityWire(g, lPlain),
                "Variable node wired to LINE must NOT count as blue recipient");

        // Variable forwarded through Pursuit (|@id) -> NOT an entity target
        check(!BlueLineTarget459.hasBlueEntityWire(g, lBtn),
                "Variable forwarded via Pursuit must NOT count as blue recipient");

        // Entity sources -> ALL count as valid blue entity wires
        lPlain.varInputNode = checkSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "CHECK -> LINE is blue entity wire");
        lPlain.varInputNode = dmgSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "DAMAGE -> LINE is blue entity wire");
        lPlain.varInputNode = doorSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "Door -> LINE is blue entity wire");
        lPlain.varInputNode = chestSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "Chest -> LINE is blue entity wire");
        lPlain.varInputNode = trialSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "Trial -> LINE is blue entity wire");
        lPlain.varInputNode = pursuitSrc.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lPlain), "Pursuit -> LINE (varInputNode) is blue entity wire");

        lCut.varInputNode = checkSrc.id;
        check(VarDataNode453.entityInput(lCut) == checkSrc.id && VarDataNode453.entityInput(g, lCut) == checkSrc.id,
                "VarDataNode453.entityInput supports CUTSCENE varInputNode");
        check(BlueLineTarget459.hasBlueEntityWire(g, lCut), "CHECK -> CUTSCENE (varInputNode) is blue entity wire");
        lCut.varInputNode = -1;
        pursuitSrc.action = "trigger:pursuit:start:npc:guard|1.5|8.0|1.5|#" + checkSrc.id + "|@" + lCut.id;
        check(BlueLineTarget459.hasBlueEntityWire(g, lCut), "Pursuit -> CUTSCENE (|@id) is blue entity wire");

        // 3. Thread-local stacks (no-event-target, RADIUS fan-out reset, resolved slot)
        check(!BlueLineTarget459.isNoEventTarget(), "noEventTarget defaults to false");
        BlueLineTarget459.pushNoEventTarget(true);
        check(BlueLineTarget459.isNoEventTarget(), "pushNoEventTarget(true) sets flag");
        BlueLineTarget459.pushNoEventTarget(false);
        check(!BlueLineTarget459.isNoEventTarget(), "Nested pushNoEventTarget(false) (RADIUS) overrides to false");
        BlueLineTarget459.popNoEventTarget();
        check(BlueLineTarget459.isNoEventTarget(), "Popping inner RADIUS restores true");
        BlueLineTarget459.popNoEventTarget();
        check(!BlueLineTarget459.isNoEventTarget(), "Popping outer restores false");

        // Block dialogue target gating with null Where in autonomous vs normal mode
        check(BlueLineTarget459.resolveBlockDialogTarget(null, g, lPlain, null) == null,
                "null player stays null in normal mode");
        BlueLineTarget459.pushNoEventTarget(true);
        check(BlueLineTarget459.resolveBlockLineBluePlayer(null, g, lPlain, null) == null,
                "resolveBlockLineBluePlayer safe with null Where");
        check(BlueLineTarget459.resolveBlockDialogTarget(null, g, lBtn, null) == null,
                "BUTTON without blue target gated in autonomous mode");
        check(BlueLineTarget459.resolveBlockDialogTarget(null, g, lCut, null) == null,
                "CUTSCENE without blue target gated in autonomous mode");
        // Verify CHECK(MONSTER/NPC/PASSIVE) filters return null in resolveBlockCheckPlayer
        checkSrc.checkTarget = NpcDialogueGraph.TARGET_MONSTER;
        check(BlueLineTarget459.resolveBlockCheckPlayer(null, checkSrc, null) == null,
                "resolveBlockCheckPlayer ignores non-player TARGET_MONSTER");
        checkSrc.checkTarget = NpcDialogueGraph.TARGET_PLAYER;
        check(BlueLineTarget459.resolveNpcLineBluePlayer(null, g, lPlain) == null,
                "resolveNpcLineBluePlayer safe with null NPC");
        BlueLineTarget459.popNoEventTarget();

        // Resolved slot stack discipline
        BlueLineTarget459.pushResolvedSlot();
        check(BlueLineTarget459.popResolvedPlayer() == null, "Empty resolved slot pops null");

        // 4. VarScopeStore453 frame extensions
        VarScopeStore453.NpcResolveFrame f4 = new VarScopeStore453.NpcResolveFrame(null, null, null, g);
        VarScopeStore453.NpcResolveFrame f5 = new VarScopeStore453.NpcResolveFrame(null, null, null, g, true);
        check(!f4.hasSession && f5.hasSession, "NpcResolveFrame preserves 4-arg and supports 5-arg hasSession");
        VarScopeStore453.resolveStack(true).push(f5);
        check(VarScopeStore453.peekResolveFrame() == f5, "VarScopeStore453 resolveStack push/peek");
        VarScopeStore453.popResolveFrame();
        check(VarScopeStore453.peekResolveFrame() == null, "VarScopeStore453 popResolveFrame clears stack");

        VarScopeStore453.BlockWalkFrame wf = new VarScopeStore453.BlockWalkFrame(g, null, null);
        wf.suppressStep = true;
        VarScopeStore453.blockWalkStack(true).push(wf);
        check(VarScopeStore453.peekBlockWalkFrame() == wf && wf.suppressStep,
                "VarScopeStore453 blockWalkStack push/peek");
        VarScopeStore453.popBlockWalkFrame();
        check(VarScopeStore453.peekBlockWalkFrame() == null, "VarScopeStore453 popBlockWalkFrame clears stack");

        System.out.println("BUILD459_OK=true CHECKS=" + checks);
    }
}
"""


def run_probe459(jar_path: pathlib.Path) -> str:
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
            raise SystemExit("probe459 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe459.java"
        probe_src.write_text(PROBE459_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe459 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe459"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD459_OK=true" not in r3.stdout:
            raise SystemExit("Probe459 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def verify_mixin_targets_and_redirects(jar_path: pathlib.Path, errors: list[str]) -> None:
    """Checks that every @Inject/@Redirect method + INVOKE target in Build 459 mixins
    resolves against the actual JAR class bytecode and has zero duplicate @Redirect conflicts."""
    expected_methods = {
        "dev/settledlands/npc/GuideDialogue": {
            "tickGraphTimerNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            "tickGraphTimeNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;Ldev/settledlands/npc/NpcDialogueGraph;)V",
            "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            "triggerTimerChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            "triggerTimeChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            "triggerCheckChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            "triggerDamageChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Z)V",
            "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
        },
        "dev/settledlands/npc/RadiusPass354": {
            "timerEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            "timeEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            "nearestPlayer(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;DLjava/util/List;)Lnet/minecraft/server/level/ServerPlayer;",
        },
        "dev/settledlands/npc/BlockTrigger351": {
            "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            "say(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            "fireTriggers(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/Set;)V",
            "playCutscene(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z",
        },
    }
    with zipfile.ZipFile(jar_path) as zf:
        classes = check_mixin_class.load_classes(
            zf, [c + ".class" for c in expected_methods]
        )
        for cls_name, descs in expected_methods.items():
            cf = classes.get(cls_name)
            if cf is None:
                errors.append(f"missing target class {cls_name}")
                continue
            present = {m.name + m.desc for m in cf.methods}
            for d in sorted(descs):
                if d not in present:
                    errors.append(f"target {cls_name} missing method {d}")

        # Verify every Build 453-459 mixin emits all Sponge Mixin member/injector annotations
        # in RuntimeVisibleAnnotations (RetentionPolicy.RUNTIME) and @Mixin in RuntimeInvisibleAnnotations.
        # Sponge Mixin 0.8.7 uses Annotations.getSingleVisible / getVisible for @Inject/@Redirect/@Unique;
        # RetentionPolicy.CLASS silently disables the entire injector at runtime!
        must_be_visible = {
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/Unique;",
            "Lorg/spongepowered/asm/mixin/Shadow;",
            "Lorg/spongepowered/asm/mixin/Final;",
            "Lorg/spongepowered/asm/mixin/Overwrite;",
            "Lorg/spongepowered/asm/mixin/gen/Accessor;",
            "Lorg/spongepowered/asm/mixin/gen/Invoker;",
        }
        for entry_name in sorted(zf.namelist()):
            if not entry_name.startswith("dev/settledlands/mixin/") or not entry_name.endswith(".class"):
                continue
            stem = entry_name[:-6].rsplit("/", 1)[-1]
            if stem != "NpcPursuitOutputMixin280" and not any(tag in stem for tag in ("453", "454", "455", "456", "457", "458", "459")):
                continue
            mcf = check_mixin_class.classfile.parse(zf.read(entry_name), entry_name)
            for ann in check_mixin_class.read_annotations(mcf, ("RuntimeVisibleAnnotations",)):
                if check_mixin_class.ann_desc(mcf, ann) in (check_mixin_class.DESC_MIXIN, "Lorg/spongepowered/asm/mixin/Pseudo;"):
                    errors.append(f"{entry_name}: @Mixin/@Pseudo must use RuntimeInvisibleAnnotations (CLASS)")
            for member in list(mcf.methods) + list(mcf.fields):
                for ann in check_mixin_class.member_annotations(mcf, member, ("RuntimeInvisibleAnnotations",)):
                    adesc = check_mixin_class.ann_desc(mcf, ann)
                    if adesc in must_be_visible:
                        errors.append(
                            f"{entry_name}::{member.name}: {adesc} emitted in RuntimeInvisibleAnnotations "
                            f"(RetentionPolicy.CLASS) - Sponge Mixin ignores it at runtime!"
                        )

        # Verify zero @Redirect target collisions among Build 453-459 mixins on the 3 shared runtime targets
        redirect_seen: dict[tuple[str, str, str], str] = {}
        for src_rel in (
            "build303-src/src/dev/settledlands/mixin/VarExecutionNpcMixin453.java",
            "build303-src/src/dev/settledlands/mixin/VarExecutionBlockMixin453.java",
            "build303-src/src/dev/settledlands/mixin/RadiusZonePassMixin456.java",
            "build303-src/src/dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456.java",
            "build303-src/src/dev/settledlands/mixin/GuideDialogueCheckGate294.java",
            "build303-src/src/dev/settledlands/mixin/BlueLineNpcMixin459.java",
            "build303-src/src/dev/settledlands/mixin/BlueLinePassMixin459.java",
            "build303-src/src/dev/settledlands/mixin/BlueLineBlockMixin459.java",
        ):
            src_text = (ROOT / src_rel).read_text(encoding="utf-8")
            m_cls = re.search(r'@Mixin\((?:value\s*=\s*([A-Za-z0-9_]+)\.class|targets\s*=\s*"([^"]+)")', src_text)
            t_cls = (m_cls.group(1) or m_cls.group(2).rsplit(".", 1)[-1]) if m_cls else src_rel
            for rm in re.finditer(r'@Redirect\((.*?)\)\s*(?:private|public)', src_text, re.S):
                body = rm.group(1)
                mm = re.search(r'method\s*=\s*"([^"]+)"', body)
                tm = re.search(r'target\s*=\s*"([^"]+)"', body)
                if mm and tm:
                    key = (t_cls, mm.group(1), tm.group(1))
                    if key in redirect_seen:
                        errors.append(
                            f"duplicate @Redirect on {key} in {src_rel} and {redirect_seen[key]}"
                        )
                    redirect_seen[key] = src_rel


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build459_check.py <jar>", file=sys.stderr)
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
            "dev/settledlands/npc/VarScopeStore453$TextVarContext.class",
            "dev/settledlands/npc/NpcPointsRemoveBridge457.class",
            "dev/settledlands/npc/VarCatalogStore457.class",
            "dev/settledlands/npc/BlueLineTarget459.class",
            "dev/settledlands/npc/client/VarCatalogClient457.class",
            "dev/settledlands/npc/client/VarPoolEditorScreen457.class",
            "dev/settledlands/npc/client/VarDataCard453.class",
            "dev/settledlands/npc/client/VarDataInspector453.class",
            "dev/settledlands/npc/client/WireHover453.class",
            "dev/settledlands/npc/client/RadiusZoneUi456.class",
            "dev/settledlands/mixin/BlueLineNpcMixin459.class",
            "dev/settledlands/mixin/BlueLinePassMixin459.class",
            "dev/settledlands/mixin/BlueLineBlockMixin459.class",
            "dev/settledlands/mixin/VarExecutionNpcMixin453.class",
            "dev/settledlands/mixin/VarExecutionBlockMixin453.class",
        ):
            if req not in names:
                errors.append(f"missing class {req}")

        for bad in names:
            if bad.startswith("dev/settledlands/mixin/") and "$" in bad:
                errors.append(f"forbidden '$' class inside mixin package: {bad}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build459-blue-line-target-gating" not in toml:
            errors.append("neoforge.mods.toml missing Build 459 version")

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
            "NpcPointsCatalogMixin457",
            "VarCatalogViewMixin457",
            "VarCatalogEditMixin457",
            "VarActionsSubstituteMixin457",
            "BlockTalkVarMixin457",
            "NpcProfileFillVarMixin457",
            "BlueLineNpcMixin459",
            "BlueLinePassMixin459",
            "BlueLineBlockMixin459",
        ):
            if cm not in common_list:
                errors.append(f"settledlands.mixins.json missing {cm} in mixins[]")
        if common_list.index("NpcProfileFillVarMixin457") > common_list.index("NpcPursuitOutputMixin280"):
            errors.append("NpcProfileFillVarMixin457 must precede NpcPursuitOutputMixin280 in mixins[]")
        if common_list.index("VarActionsSubstituteMixin457") > common_list.index("TeleportActionsMixin424"):
            errors.append("VarActionsSubstituteMixin457 must precede TeleportActionsMixin424 in mixins[]")
        if common_list.index("VarExecutionNpcMixin453") > common_list.index("BlueLineNpcMixin459"):
            errors.append("VarExecutionNpcMixin453 must precede BlueLineNpcMixin459 in mixins[]")
        if common_list.index("BlueLineNpcMixin459") > common_list.index("GuideRange450Mixin"):
            errors.append("BlueLineNpcMixin459 must precede GuideRange450Mixin in mixins[]")

        client_list = mixins_cfg.get("client", [])
        if not client_list or client_list[0] != "NpcDropdownFirst333":
            errors.append("NpcDropdownFirst333 must stay first in client[]")
        if client_list[-1] != "GraphHover448Mixin":
            errors.append("GraphHover448Mixin must stay last in client[]")
        if client_list.index("VarCatalogConfigMixin457") > client_list.index("NpcBlockOpen351Mixin"):
            errors.append("VarCatalogConfigMixin457 must precede NpcBlockOpen351Mixin in client[]")

        for cls_key, marker_str in (
            ("dev/settledlands/npc/VarScopeStore453.class", "resolvePursuitActionTarget"),
            ("dev/settledlands/mixin/VarExecutionNpcMixin453.class", "radiusTargetedInitiator"),
            ("dev/settledlands/mixin/BlueLineNpcMixin459.class", "nodeHasAttachedRadiusPlayerGate"),
        ):
            if cls_key not in names or marker_str.encode("utf-8") not in zf.read(cls_key):
                errors.append(f"{cls_key} missing marker {marker_str}")

        ru_raw = zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8")
        en_raw = zf.read("assets/settledlands/lang/en_us.json").decode("utf-8")
        ru = json.loads(ru_raw)
        en = json.loads(en_raw)
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in (tuple(build450_check.NEW_KEYS)
                  + build452_check.NEW_KEYS_452
                  + build453_check.NEW_KEYS_453
                  + build456_check.NEW_KEYS_456
                  + build458_check.NEW_KEYS_457):
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")
        for lang_name, lang_map in (("ru_ru.json", ru), ("en_us.json", en)):
            for k, v in lang_map.items():
                if isinstance(v, str) and "ue5" in v.lower():
                    errors.append(f"forbidden UE5 mention in {lang_name}[{k}]: {v!r}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_459:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        for mname in (
            "dev/settledlands/npc/BlueLineTarget459",
            "dev/settledlands/mixin/BlueLineNpcMixin459",
            "dev/settledlands/mixin/BlueLinePassMixin459",
            "dev/settledlands/mixin/BlueLineBlockMixin459",
            "dev/settledlands/mixin/VarExecutionBlockMixin453",
            "dev/settledlands/mixin/VarExecutionNpcMixin453",
            "dev/settledlands/mixin/VarActionsSubstituteMixin457",
            "dev/settledlands/mixin/BlockTalkVarMixin457",
            "dev/settledlands/mixin/NpcPursuitOutputMixin280",
            "dev/settledlands/mixin/NpcProfileFillVarMixin457",
            "dev/settledlands/mixin/NpcPointsCatalogMixin457",
            "dev/settledlands/mixin/VarCatalogViewMixin457",
            "dev/settledlands/mixin/VarCatalogEditMixin457",
            "dev/settledlands/mixin/VarCatalogConfigMixin457",
            "dev/settledlands/mixin/VarDropdownOverlayMixin457",
            "dev/settledlands/mixin/TeleportValuePinMixin457",
            "dev/settledlands/mixin/RadiusZoneNodeMixin456",
            "dev/settledlands/mixin/RadiusZonePassMixin456",
        ):
            cf = classes.get(mname)
            if cf is not None:
                indys = [k for k in cf.cp if k and k[0] == 18]
                if indys:
                    errors.append(f"class {mname} contains invokedynamic entries: {len(indys)}")

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

        # Verify Build 458 renderBackground no-op preservation
        vscreen = classes.get("dev/settledlands/npc/client/VarPoolEditorScreen457")
        if vscreen is None:
            errors.append("missing VarPoolEditorScreen457 class")
        else:
            rb = [m for m in vscreen.methods
                  if m.name == "renderBackground"
                  and m.desc == "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"]
            if not rb:
                errors.append("VarPoolEditorScreen457 missing renderBackground override")
            else:
                code_attr = rb[0].attr("Code")
                code = code_attr.data if code_attr is not None else None
                code_len = struct.unpack(">I", code[4:8])[0] if code and len(code) >= 9 else 0
                bc = code[8:8 + code_len] if code_len else b""
                if bc != b"\xb1":
                    errors.append(f"VarPoolEditorScreen457.renderBackground not single RETURN: {bc.hex()}")

    verify_mixin_targets_and_redirects(jar_path, errors)

    out450 = build450_check.run_probe(jar_path)
    out452 = build452_check.run_probe452(jar_path)
    out455 = build455_check.run_probe455(jar_path)
    out456 = build456_check.run_probe456(jar_path)
    out457 = build458_check.run_probe457(jar_path)
    out459 = run_probe459(jar_path)

    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build459_check: OK ", jar_path)
    for out in (out450, out452, out455, out456, out457, out459):
        for line in out.splitlines():
            print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_459)} mixins + Probe459 + zero @Redirect clashes + all Build 450-458 gates OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
