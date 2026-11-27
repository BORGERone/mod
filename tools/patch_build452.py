"""Build 452: Remove the floating hover tooltip in the 3D Mob Catalog, add a conditional-input
socket to both Mob Pool (SLOT_MOBS) and Reward Pool (SLOT_REWARDS) module cards so IF / control
chains can route pools dynamically, keep pool cards silent when driven by an incoming IF/signal
and allow passing control along downstream chains, and add + Mob Pool / + Reward Pool to the
Trial Spawner RMB palette."""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent

BASE_VERSION = "0.8.0-build451-mob-catalog-cleanup-and-more-rows"
NEW_VERSION = "0.8.0-build452-conditional-pools-and-clean-catalog"
OUT_NAME = "settledlands-0.8.0-build452-conditional-pools-and-clean-catalog.jar"

STUB_LAYERS = [
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
]

SOURCE_FILES = [
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.java",
    "dev/settledlands/trial/TrialGraph446Store.java",
    "dev/settledlands/trial/TrialControlActions446.java",
    "dev/settledlands/trial/TrialModule447.java",
    "dev/settledlands/trial/TrialGraph446.java",
    "dev/settledlands/mixin/TrialModuleSignalMixin452.java",
    "dev/settledlands/mixin/TrialControlWiringMixin446.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.java",
    "dev/settledlands/npc/client/TrialModuleCard447.java",
    "dev/settledlands/mixin/TrialModuleSocket447Mixin.java",
    "dev/settledlands/mixin/NpcTrialSocket446Mixin.java",
    "dev/settledlands/npc/client/TrialModuleClient447.java",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
]

COMMON_MIXINS_452 = [
    "TrialModuleSignalMixin452",
]

MARKERS = {
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.class": (
        "enableScissor",
        "drawScaledText",
    ),
    "dev/settledlands/trial/TrialModule447.class": (
        "supportsConditionalInput",
        "hasIncomingControl",
        "evaluateAutonomousIfGates",
        "onSignalTriggered",
    ),
    "dev/settledlands/mixin/TrialModuleSignalMixin452.class": (
        "settledlands$signalModuleOnWalk452",
        "settledlands$signalModuleServerOnly452",
    ),
    "dev/settledlands/npc/client/TrialModuleCard447.class": (
        "moduleAtInputPin",
        "compactInLabel",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.trial.mod.sig_only": "По сигналу (%s)",
    "gui.settledlands.trial.mod.sig_hub": "%s → %s",
    "gui.settledlands.trial.mod.sig_chain": "%s → цепь %s",
    "gui.settledlands.trial.mod.chain_to": "Цепь → %s",
    "gui.settledlands.trial.mod.in_hub": "Хаб",
    "gui.settledlands.trial.mod.btn_unlink_in": "Отвязать вход",
    "gui.settledlands.tip448.menu.tmod_mobs": "Добавить ещё одну карточку «Пул мобов» (можно подключить к ветке IF / цепи).",
    "gui.settledlands.tip448.menu.tmod_rewards": "Добавить ещё одну карточку «Пул наград» (можно подключить к ветке IF / цепи).",
    "gui.settledlands.tip448.pin.mod_in_title": "Вход сигнала (IF / цепь)",
    "gui.settledlands.tip448.pin.mod_in_wired": "К пулу подключён управляющий сигнал: сам по себе пул молчит и включается только по сигналу (и передаёт цепь дальше). ПКМ — отвязать вход.",
    "gui.settledlands.tip448.pin.mod_in_free": "Перетащите сюда провод от IF (Да/Нет), Случайности или события: тогда пул будет активироваться только по сигналу, а без входа работает сам по себе.",
    "gui.settledlands.tip448.pin.mod_out_chain": "Выход пула подключён к следующей ноде цепи: при активации пул обновится и передаст сигнал дальше.",
}

LANG_SET_EN = {
    "gui.settledlands.trial.mod.sig_only": "By signal (%s)",
    "gui.settledlands.trial.mod.sig_hub": "%s → %s",
    "gui.settledlands.trial.mod.sig_chain": "%s → chain %s",
    "gui.settledlands.trial.mod.chain_to": "Chain → %s",
    "gui.settledlands.trial.mod.in_hub": "Hub",
    "gui.settledlands.trial.mod.btn_unlink_in": "Unlink input",
    "gui.settledlands.tip448.menu.tmod_mobs": "Add another Mob Pool module card (can be wired to an IF branch or chain).",
    "gui.settledlands.tip448.menu.tmod_rewards": "Add another Reward Pool module card (can be wired to an IF branch or chain).",
    "gui.settledlands.tip448.pin.mod_in_title": "Signal input (IF / chain)",
    "gui.settledlands.tip448.pin.mod_in_wired": "Control wire attached: this pool no longer applies unconditionally on its own — it activates only when signaled and forwards the chain. Right-click to unlink input.",
    "gui.settledlands.tip448.pin.mod_in_free": "Wire an IF (True/False), Random, or event node here to activate this pool conditionally; when unwired it applies directly on its own.",
    "gui.settledlands.tip448.pin.mod_out_chain": "Pool output wired to the next chain node: when signaled, this pool applies and forwards execution downstream.",
}

CHANGELOG_EN = """\
Build 452 - Conditional Pool Inputs & Hover Tooltip Removal
- Removed the floating hover tooltip card (renderHoveredMobTip) from TrialMobCatalogScreen449 while preserving card hover outline highlights.
- Added a left conditional-input socket on Mob Pool (SLOT_MOBS) and Reward Pool (SLOT_REWARDS) module cards:
  * When unwired on input, pool cards apply directly as before.
  * When wired from an IF / Random / event / upstream chain, pool cards stay silent on their own, activate their pool when signaled during execution (including standalone IF gates evaluated at save, wave start, wave clear, and OP_START), and forward control downstream via their output wire.
  * Hub mob/reward slots accept multiple conditional pool cards, and Trial Spawner RMB menu exposes + Mob Pool and + Reward Pool."""

PATCHINFO = """\
Settled Lands 0.8.0 Build 452
- Removed floating mob hover tooltip in 3D Mob Catalog
- Added conditional input sockets to Mob Pool and Reward Pool nodes (IF / chain routing)
- Pool cards only emit/apply by themselves when no IF/input wire is attached; when wired, IF drives activation and pools pass signals downstream
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


def compile_build452(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    src_root = ROOT / "build303-src" / "src"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        merged.mkdir()
        stubs_out.mkdir()
        for layer in STUB_LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(base_jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("stub compile failed:\n" + r1.stdout + r1.stderr)

        targets = [str(src_root / rel) for rel in SOURCE_FILES]
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{base_jar}:{stubs_out}", "-d", str(out_dir)] + targets,
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("build452 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_mixins_json(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    mixins = doc.get("mixins", [])
    for m in COMMON_MIXINS_452:
        if m not in mixins:
            mixins.append(m)
    doc["mixins"] = mixins

    client = doc.get("client", [])
    if not client or client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    if client[-1] != "GraphHover448Mixin":
        raise SystemExit("GraphHover448Mixin must remain last in client mixins!")
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    if set(LANG_SET_RU) != set(LANG_SET_EN):
        raise SystemExit("lang parity error in patch_build452.py")

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out452",
                    help="directory for compiled build 452 classes")
    ap.add_argument("--no-compile", action="store_true",
                    help="reuse existing --classes directory without running ECJ")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
    if not args.no_compile:
        print("compiling Build 452 sources via ECJ (-21)...")
        compile_build452(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    outer_stems = [rel[:-5] for rel in SOURCE_FILES]
    stale_removed = set()
    new_order = []
    for name in order:
        if any(name == stem + ".class" or name.startswith(stem + "$") for stem in outer_stems):
            stale_removed.add(name)
            blobs.pop(name, None)
        else:
            new_order.append(name)
    order = new_order

    compiled_map = {}
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        compiled_map[rel] = compiled.read_bytes()

    for rel, data in sorted(compiled_map.items()):
        for marker in MARKERS.get(rel, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (rel, marker))
        action = "replaced" if rel in stale_removed else "added   "
        print("  %s: %s (%d bytes)" % (action, rel, len(data)))
        order.append(rel)
        blobs[rel] = data

    blobs["settledlands.mixins.json"] = patch_mixins_json(blobs["settledlands.mixins.json"])
    print("  updated : settledlands.mixins.json")

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (%d keys updated)" % (lp, len(entries)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    stale = 'displayName="Settled Lands %s"' % BASE_VERSION
    if stale not in toml:
        raise SystemExit("base toml has no displayName to bump")
    toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    (outdir / (OUT_NAME + ".sha256sums.txt")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
