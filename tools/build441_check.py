#!/usr/bin/env python3
"""Comprehensive verification gate for Build 441 (full-height editor, single count field,
CS:GO-style rarity chance + crate weight capacity, and silent GuideTriggerNpc anchor)."""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build439_check
import build440_check
import disasm
import invoke_kind_check

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = build440_check.LAYERS

LANG_KEYS_441 = (
    "gui.settledlands.loot_pool.editor.by_weight",
    "gui.settledlands.loot_pool.editor.tip_count",
    "gui.settledlands.loot_pool.editor.tip_rarity",
    "gui.settledlands.loot_pool.editor.tip_weight",
    "gui.settledlands.loot_pool.editor.tip_chance",
)


def fail(msg):
    print("build441_check: FAIL - " + msg)
    return 1


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = []
    problems.extend(build440_check.check_structural(jar, base_jar))

    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        silent_mixin = "dev/settledlands/mixin/GuideTriggerSilentMixin441.class"
        if silent_mixin not in names:
            problems.append("missing class: " + silent_mixin)
        else:
            parsed = disasm.parse_class(zf.read(silent_mixin))
            cp = parsed["cp"]
            mnames = {cp[ni][1] for _a, ni, _d, _at in parsed["methods"]}
            for req_m in ("getAmbientSound", "getHurtSound", "getDeathSound", "getSoundVolume"):
                if req_m not in mnames:
                    problems.append(f"{silent_mixin} missing sound override: {req_m}")
            for _a, ni, _d, _at in parsed["methods"]:
                mn = cp[ni][1]
                if mn.startswith("lambda$"):
                    problems.append(f"{silent_mixin} declares synthetic lambda: {mn}")

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = mixins.get("mixins", [])
        if "GuideTriggerSilentMixin441" not in common:
            problems.append("GuideTriggerSilentMixin441 missing from settledlands.mixins.json common list")
        if "GuideTriggerSilentMixin441" in mixins.get("client", []):
            problems.append("GuideTriggerSilentMixin441 must be in common mixins, not client")

        for lp in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            table = json.loads(zf.read(lp).decode("utf-8"))
            for key in LANG_KEYS_441:
                if not table.get(key):
                    problems.append(f"{lp} missing or empty key: {key}")
            if "\u2264" in str(table.get("gui.settledlands.loot_pool.card_limits", "")):
                problems.append(f"{lp} card_limits contains glyph-overlapping '<=' symbol")

        lp436 = zf.read("dev/settledlands/chest/LootPool436.class")
        for tok in (b"parseItemCountRange", b"formatRarity", b"parseRarityInput"):
            if tok not in lp436:
                problems.append(f"LootPool436 missing token {tok.decode()}")

        ed = zf.read("dev/settledlands/npc/client/LootPoolEditorScreen440.class")
        for tok in (b"isCountRangeInput", b"fullVisibleRows", b"compactVisibleRows"):
            if tok not in ed:
                problems.append(f"LootPoolEditorScreen440 missing token {tok.decode()}")
        if b"minBox" in ed or b"maxBox" in ed:
            problems.append("LootPoolEditorScreen440 still references obsolete split minBox/maxBox fields")
        ed_row = zf.read("dev/settledlands/npc/client/LootPoolEditorScreen440$RowData.class")
        if b"parseItemCountRange" not in ed_row:
            problems.append("LootPoolEditorScreen440$RowData missing token parseItemCountRange")

    return problems


def compile_and_run_probe441(jar: pathlib.Path):
    jar = jar.resolve()
    java = build439_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        probe_out = td / "probe_out"
        work_dir = td / "workdir"
        for d in (merged, stubs_out, probe_out, work_dir):
            d.mkdir(parents=True, exist_ok=True)

        for layer in LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise AssertionError("stubs compile failed:\n" + r1.stdout + r1.stderr)

        probe441 = ROOT / "tools" / "harness" / "Build441Probe.java"
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe441)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise AssertionError("Build441Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar}:{stubs_out}", "Build441Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError(f"Build441Probe failed (rc={r3.returncode}):\n{r3.stdout}\n{r3.stderr}")
        sys.stdout.write(r3.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build441_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    base440 = ROOT / "settledlands-0.8.0-build440-loot-pool-editor-gui.jar"
    if base440.is_file():
        rc = invoke_kind_check.main([str(jar), "--base", str(base440)])
        if rc != 0:
            return fail("invoke_kind_check failed")

    problems = check_structural(jar, base440 if base440.is_file() else None)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        build439_check.compile_and_run_all_probes(jar)
        build440_check.compile_and_run_probes(jar)
        compile_and_run_probe441(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build441_check: OK (%s verified)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
