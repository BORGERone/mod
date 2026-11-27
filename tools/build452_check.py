#!/usr/bin/env python3
"""
Gate (Build 452): Full verification suite for Build 452:
  1. Manifest, settledlands.mixins.json ordering, zero-lambda mixins (incl. TrialModuleSignalMixin452)
  2. Removal of renderHoveredMobTip from TrialMobCatalogScreen449 + preserved Build 451 4+1 layout
  3. Complete RU/EN localization parity & class string-key scan across the entire JAR
  4. Inherited Build 450 JVM regression probe (642 checks)
  5. New Build 452 JVM live-execution probe testing conditional pools, IF routing, chaining,
     disconnectIncoming, TrialModule447.onSignalTriggered, multi-pool hub sync, and gate detection
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
import check_mixin_class

NEW_KEYS_452 = (
    "gui.settledlands.trial.mod.sig_only",
    "gui.settledlands.trial.mod.sig_hub",
    "gui.settledlands.trial.mod.sig_chain",
    "gui.settledlands.trial.mod.chain_to",
    "gui.settledlands.trial.mod.in_hub",
    "gui.settledlands.trial.mod.btn_unlink_in",
    "gui.settledlands.tip448.menu.tmod_mobs",
    "gui.settledlands.tip448.menu.tmod_rewards",
    "gui.settledlands.tip448.pin.mod_in_title",
    "gui.settledlands.tip448.pin.mod_in_wired",
    "gui.settledlands.tip448.pin.mod_in_free",
    "gui.settledlands.tip448.pin.mod_out_chain",
)

ZERO_LAMBDA_452 = build450_check.ZERO_LAMBDA_MIXINS + (
    "dev/settledlands/mixin/TrialModuleSignalMixin452",
)

PROBE452_JAVA = r"""
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialGraph446Store;
import dev.settledlands.trial.TrialModule447;
import dev.settledlands.trial.TrialControlActions446;
import java.util.List;

public class Probe452 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    private static NpcDialogueGraph.Node makeHub(NpcDialogueGraph g, int x, int y) {
        NpcDialogueGraph.Node h = g.add(TrialGraph446.TRIAL_TYPE, x, y);
        h.action = TrialConfig446.defaults().toJson();
        return h;
    }

    private static NpcDialogueGraph.Node makeMod(NpcDialogueGraph g, int slot, int x, int y) {
        NpcDialogueGraph.Node n = g.add(NpcDialogueGraph.TRIGGER, x, y);
        TrialModule447.applyDefaults(n, slot);
        return n;
    }

    public static void main(String[] args) throws Exception {
        // 1. supportsConditionalInput: ONLY SLOT_MOBS & SLOT_REWARDS nodes
        NpcDialogueGraph gKind = new NpcDialogueGraph();
        for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
            NpcDialogueGraph.Node m = makeMod(gKind, s, 0, s * 20);
            boolean expected = (s == TrialModule447.SLOT_MOBS || s == TrialModule447.SLOT_REWARDS);
            check(TrialModule447.supportsConditionalInput(m) == expected,
                    "supportsConditionalInput(slot=" + s + ") must be " + expected);
        }
        check(!TrialModule447.supportsConditionalInput(null), "supportsConditionalInput(null) must be false");

        // 2. Autonomous pool (no incoming IF/control): applies itself in compileConfig
        NpcDialogueGraph gAuto = new NpcDialogueGraph();
        NpcDialogueGraph.Node hubAuto = makeHub(gAuto, 300, 0);
        NpcDialogueGraph.Node mobAuto = makeMod(gAuto, TrialModule447.SLOT_MOBS, 0, 0);
        TrialModule447.writeMobs(mobAuto, List.of(new TrialConfig446.SpawnEntry("minecraft:skeleton", 3)));
        check(TrialModule447.connectModule(gAuto, mobAuto.id, hubAuto.id),
                "connectModule(mobAuto -> hubAuto) must succeed");
        check(!TrialModule447.hasIncomingControl(gAuto, mobAuto),
                "unwired mobAuto must have hasIncomingControl=false");
        TrialConfig446 cfgAuto = TrialModule447.compileConfig(gAuto);
        check(cfgAuto != null && !cfgAuto.mobs.isEmpty()
                && "minecraft:skeleton".equals(cfgAuto.mobs.get(0).entityId),
                "autonomous mob pool must populate mobs in compileConfig");

        // 3. Conditional pools wired from IF (True -> MobA / Skeleton, False -> MobB / Blaze), both wired to Hub
        NpcDialogueGraph gCond = new NpcDialogueGraph();
        NpcDialogueGraph.Node hub = makeHub(gCond, 400, 0);
        TrialConfig446 baseCfg = TrialConfig446.defaults();
        baseCfg.mobs = List.of(new TrialConfig446.SpawnEntry("minecraft:creeper", 1));
        baseCfg.rewards = List.of(new TrialConfig446.RewardEntry("minecraft:emerald", 5, 1, 2, 100));
        hub.action = baseCfg.toJson();

        NpcDialogueGraph.Node mobA = makeMod(gCond, TrialModule447.SLOT_MOBS, 200, -40);
        TrialModule447.writeMobs(mobA, List.of(new TrialConfig446.SpawnEntry("minecraft:skeleton", 2)));

        NpcDialogueGraph.Node mobB = makeMod(gCond, TrialModule447.SLOT_MOBS, 200, 60);
        TrialModule447.writeMobs(mobB, List.of(new TrialConfig446.SpawnEntry("minecraft:blaze", 4)));

        NpcDialogueGraph.Node rewAuto = makeMod(gCond, TrialModule447.SLOT_REWARDS, 200, 160);
        TrialModule447.writeRewards(rewAuto, List.of(
                new TrialConfig446.RewardEntry("minecraft:diamond", 10, 1, 1, 100)));

        NpcDialogueGraph.Node rewCond = makeMod(gCond, TrialModule447.SLOT_REWARDS, 200, 260);
        TrialModule447.writeRewards(rewCond, List.of(
                new TrialConfig446.RewardEntry("minecraft:netherite_scrap", 5, 1, 1, 100)));

        NpcDialogueGraph.Node ifNode = gCond.add(NpcDialogueGraph.IF, 0, 0);
        ifNode.next = mobA.id;
        ifNode.falseNext = mobB.id;

        // Multi-pool attachment to same Hub slot must keep BOTH mobA and mobB!
        check(TrialModule447.connectModule(gCond, mobA.id, hub.id), "connect mobA -> hub");
        check(TrialModule447.connectModule(gCond, mobB.id, hub.id), "connect mobB -> hub alongside mobA");
        check(TrialModule447.connectModule(gCond, rewAuto.id, hub.id), "connect rewAuto -> hub");
        check(TrialModule447.connectModule(gCond, rewCond.id, hub.id), "connect rewCond -> hub");
        List<NpcDialogueGraph.Node> connectedMobs = TrialModule447.connectedModules(gCond, hub, TrialModule447.SLOT_MOBS);
        check(connectedMobs.size() == 2, "expected 2 connected mob pools on hub, got " + connectedMobs.size());

        // Non-pool Wave module -> connecting another Wave module still replaces (not multi-pool)
        NpcDialogueGraph.Node w1 = makeMod(gCond, TrialModule447.SLOT_WAVE, 200, 320);
        NpcDialogueGraph.Node w2 = makeMod(gCond, TrialModule447.SLOT_WAVE, 200, 380);
        TrialModule447.connectModule(gCond, w1.id, hub.id);
        TrialModule447.connectModule(gCond, w2.id, hub.id);
        check(TrialModule447.connectedModules(gCond, hub, TrialModule447.SLOT_WAVE).size() == 1,
                "non-pool SLOT_WAVE must remain single-module per hub");

        // mobA and mobB have incoming IF -> silent on their own
        check(TrialModule447.hasIncomingControl(gCond, mobA), "mobA must have hasIncomingControl=true");
        check(TrialModule447.hasIncomingControl(gCond, mobB), "mobB must have hasIncomingControl=true");
        check(!TrialModule447.hasIncomingControl(gCond, rewAuto), "rewAuto must have hasIncomingControl=false");

        // Wire hub Start-Wave event socket -> rewCond -> rewCond becomes conditional too!
        TrialGraph446.setStartWaveTarget(hub, rewCond.id);
        check(TrialModule447.hasIncomingControl(gCond, rewCond),
                "rewCond driven by hub Start-Wave must have hasIncomingControl=true");

        // compileConfig must apply rewAuto (autonomous) and MUST NOT let mobA/mobB/rewCond apply on their own!
        TrialConfig446 compiled = TrialModule447.compileConfig(gCond);
        check(compiled.mobs.size() == 1 && "minecraft:creeper".equals(compiled.mobs.get(0).entityId),
                "conditional mobA/mobB must NOT apply on their own in compileConfig, got "
                        + (compiled.mobs.isEmpty() ? "empty" : compiled.mobs.get(0).entityId));
        check(compiled.rewards.size() == 1 && "minecraft:diamond".equals(compiled.rewards.get(0).itemId),
                "autonomous rewAuto must apply in compileConfig without rewCond overwriting it, got "
                        + (compiled.rewards.isEmpty() ? "empty" : compiled.rewards.get(0).itemId));

        // applyConfigToConnectedModules must preserve both mobA (skeleton), mobB (blaze), rewCond (netherite_scrap)
        TrialModule447.applyConfigToConnectedModules(gCond, compiled);
        check("minecraft:skeleton".equals(TrialModule447.readMobs(mobA).get(0).entityId),
                "mobA skeleton must not be overwritten by applyConfigToConnectedModules");
        check("minecraft:blaze".equals(TrialModule447.readMobs(mobB).get(0).entityId),
                "mobB blaze must not be overwritten by applyConfigToConnectedModules");
        check("minecraft:netherite_scrap".equals(TrialModule447.readRewards(rewCond).get(0).itemId),
                "rewCond netherite_scrap must not be overwritten by applyConfigToConnectedModules");

        // 4. Signal execution: triggering mobB applies blaze to hub WITHOUT touching rewards!
        hub.action = compiled.toJson();
        TrialModule447.onSignalTriggered(null, gCond, mobB, null);
        TrialConfig446 afterMobB = TrialConfig446.fromJson(hub.action);
        check("minecraft:blaze".equals(afterMobB.mobs.get(0).entityId),
                "onSignalTriggered(mobB) must update hub mobs to blaze, got " + afterMobB.mobs.get(0).entityId);
        check("minecraft:diamond".equals(afterMobB.rewards.get(0).itemId),
                "onSignalTriggered(mobB) must preserve existing diamond reward, got " + afterMobB.rewards.get(0).itemId);

        // Triggering rewCond updates rewards to netherite_scrap WITHOUT touching mobB's blaze!
        TrialModule447.onSignalTriggered(null, gCond, rewCond, null);
        TrialConfig446 afterRewCond = TrialConfig446.fromJson(hub.action);
        check("minecraft:netherite_scrap".equals(afterRewCond.rewards.get(0).itemId),
                "onSignalTriggered(rewCond) must update hub rewards to netherite_scrap");
        check("minecraft:blaze".equals(afterRewCond.mobs.get(0).entityId),
                "onSignalTriggered(rewCond) must preserve blaze mobs");

        // 5. Chaining: connectChain(mobA -> rewCond) succeeds; connectChain(mobA -> w1[WAVE]) is rejected
        check(!TrialModule447.connectChain(gCond, mobA.id, w1.id),
                "connectChain to non-conditional module w1(SLOT_WAVE) must return false");
        check(TrialModule447.connectChain(gCond, mobA.id, rewCond.id),
                "connectChain(mobA -> rewCond) must succeed");
        check(mobA.next == rewCond.id, "mobA.next must now point to rewCond");

        // 6. disconnectIncoming removes IF wire from mobA so mobA becomes autonomous again!
        List<TrialModule447.IncomingRef> refs = TrialModule447.incomingSources(gCond, mobA);
        check(refs.size() == 1 && refs.get(0).source.id == ifNode.id,
                "incomingSources(mobA) must report ifNode");
        check(TrialModule447.disconnectIncoming(gCond, mobA.id),
                "disconnectIncoming(mobA) must return true");
        check(ifNode.next == -1 && ifNode.falseNext == mobB.id,
                "disconnectIncoming(mobA) must clear ifNode.next and keep ifNode.falseNext");
        check(!TrialModule447.hasIncomingControl(gCond, mobA),
                "mobA after disconnectIncoming must have hasIncomingControl=false");

        // Verify mobA can now also be autonomous in a single-pool hub!
        NpcDialogueGraph gRecovered = new NpcDialogueGraph();
        NpcDialogueGraph.Node rHub = makeHub(gRecovered, 300, 0);
        NpcDialogueGraph.Node rMob = makeMod(gRecovered, TrialModule447.SLOT_MOBS, 100, 0);
        TrialModule447.writeMobs(rMob, List.of(new TrialConfig446.SpawnEntry("minecraft:enderman", 1)));
        NpcDialogueGraph.Node rIf = gRecovered.add(NpcDialogueGraph.IF, 0, 0);
        rIf.next = rMob.id;
        TrialModule447.connectModule(gRecovered, rMob.id, rHub.id);
        check("minecraft:zombie".equals(TrialModule447.compileConfig(gRecovered).mobs.get(0).entityId),
                "while IF attached, rMob stays silent");
        TrialModule447.disconnectIncoming(gRecovered, rMob.id);
        check("minecraft:enderman".equals(TrialModule447.compileConfig(gRecovered).mobs.get(0).entityId),
                "after disconnectIncoming, rMob acts on its own again");

        // 7. Standalone IF gate with VAR(varValue=0) helper still counts as autonomous gate!
        NpcDialogueGraph gGate = new NpcDialogueGraph();
        NpcDialogueGraph.Node gHub = makeHub(gGate, 300, 0);
        NpcDialogueGraph.Node gPool = makeMod(gGate, TrialModule447.SLOT_MOBS, 150, 0);
        TrialModule447.connectModule(gGate, gPool.id, gHub.id);
        NpcDialogueGraph.Node gIf = gGate.add(NpcDialogueGraph.IF, 50, 0);
        gIf.next = gPool.id;
        NpcDialogueGraph.Node gVarRead = gGate.add(4, -50, 0); // VAR value-only reader (varValue == 0)
        gVarRead.varValue = 0;
        gVarRead.next = gIf.id;

        var mHasTrig = TrialModule447.class.getDeclaredMethod(
                "hasTriggerInputForGate", NpcDialogueGraph.class, NpcDialogueGraph.Node.class);
        mHasTrig.setAccessible(true);
        boolean hasTrigRead = (Boolean) mHasTrig.invoke(null, gGate, gIf);
        check(!hasTrigRead, "VAR(varValue=0) -> IF must allow autonomous IF gate evaluation");
        gVarRead.varValue = 1; // now acts as a variable-assignment trigger step
        boolean hasTrigSet = (Boolean) mHasTrig.invoke(null, gGate, gIf);
        check(hasTrigSet, "VAR(varValue=1) -> IF must treat IF as triggered by upstream VAR step");

        // 8. TrialGraph446Store.findByGraph lookup & TrialControlActions446 origin accessors
        TrialGraph446Store store = new TrialGraph446Store();
        var mRem = TrialGraph446Store.class.getDeclaredMethod("remember", String.class, NpcDialogueGraph.class);
        mRem.setAccessible(true);
        mRem.invoke(store, "minecraft:overworld@10,64,-20", gGate);
        TrialGraph446Store.Entry entry = store.findByGraph(gGate);
        check(entry != null && entry.graph == gGate, "TrialGraph446Store.findByGraph must resolve owned graph");
        check(store.findByGraph(gCond) == null, "TrialGraph446Store.findByGraph on foreign graph must return null");
        check(TrialControlActions446.currentOriginLevel() == null && TrialControlActions446.currentOriginPos() == null,
                "currentOrigin* outside execution must be null");

        System.out.println("BUILD452_CHECKS=" + checks);
        System.out.println("BUILD452_OK=true");
    }
}
"""


def run_probe452(jar_path: pathlib.Path) -> str:
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
            raise SystemExit("probe452 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe452.java"
        probe_src.write_text(PROBE452_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe452 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe452"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD452_OK=true" not in r3.stdout:
            raise SystemExit("Probe452 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build452_check.py <jar>", file=sys.stderr)
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
            "dev/settledlands/trial/TrialModule447$IncomingRef.class",
        ):
            if req not in names:
                errors.append(f"missing class {req}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build452-conditional-pools-and-clean-catalog" not in toml:
            errors.append("neoforge.mods.toml missing Build 452 version")

        mixins_cfg = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        if "TrialModuleSignalMixin452" not in mixins_cfg.get("mixins", []):
            errors.append("settledlands.mixins.json missing TrialModuleSignalMixin452 in mixins[]")
        client_list = mixins_cfg.get("client", [])
        if not client_list or client_list[0] != "NpcDropdownFirst333":
            errors.append("NpcDropdownFirst333 must stay first in client[]")
        if client_list[-1] != "GraphHover448Mixin":
            errors.append("GraphHover448Mixin must stay last in client[]")

        # Verify Build 452 hover tooltip removal + Build 451 clean layout in TrialMobCatalogScreen449
        cat_cf = zf.read("dev/settledlands/npc/client/TrialMobCatalogScreen449.class")
        if b"renderHoveredMobTip" in cat_cf:
            errors.append("TrialMobCatalogScreen449 must NOT contain renderHoveredMobTip")
        for req_marker in (b"enableScissor", b"drawScaledText", b"drawRows", b"clip"):
            if req_marker not in cat_cf:
                errors.append(f"TrialMobCatalogScreen449 missing {req_marker.decode()}")

        # Verify BlockMenu363 has trialModuleSlot & TrialModuleCard447 has inputPinScreenX
        bm_cf = zf.read("dev/settledlands/npc/client/BlockMenu363.class")
        if b"trialModuleSlot" not in bm_cf:
            errors.append("BlockMenu363 missing trialModuleSlot")
        card_cf = zf.read("dev/settledlands/npc/client/TrialModuleCard447.class")
        for mk in (b"inputPinScreenX", b"moduleAtInputPin", b"compactInLabel"):
            if mk not in card_cf:
                errors.append(f"TrialModuleCard447 missing {mk.decode()}")

        # Lang parity + Build 452 keys + scan every class for referenced keys
        ru = json.loads(zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        en = json.loads(zf.read("assets/settledlands/lang/en_us.json").decode("utf-8"))
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in tuple(build450_check.NEW_KEYS) + NEW_KEYS_452:
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_452:
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
    out452 = run_probe452(jar_path)
    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build452_check: OK ", jar_path)
    for line in out450.splitlines():
        print(" ", line)
    for line in out452.splitlines():
        print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_452)} mixins + hover tooltip removed + conditional pool/chain/signal runtime OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
