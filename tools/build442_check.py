#!/usr/bin/env python3
"""Comprehensive verification gate for Build 442 (rarity chance fix, effective chances,
and inline editable node limits in LootPoolEditorScreen440)."""

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
import build441_check
import invoke_kind_check

ROOT = pathlib.Path(__file__).resolve().parent.parent

LAYERS = build440_check.LAYERS

LANG_KEYS_442 = (
    "gui.settledlands.loot_pool.editor.node_prefix",
    "gui.settledlands.loot_pool.editor.node_rar_lbl",
    "gui.settledlands.loot_pool.editor.node_wgt_lbl",
    "gui.settledlands.loot_pool.editor.node_rls_lbl",
    "gui.settledlands.loot_pool.editor.tip_node_rar",
    "gui.settledlands.loot_pool.editor.tip_node_wgt",
    "gui.settledlands.loot_pool.editor.tip_node_rls",
    "gui.settledlands.loot_pool.editor.blocked_banner",
    "gui.settledlands.loot_pool.editor.blocked_banner_tip",
    "gui.settledlands.loot_pool.editor.blocked_weight_tip",
    "gui.settledlands.loot_pool.editor.blocked_rarity_tip",
    "gui.settledlands.loot_pool.editor.skewed_chance_tip",
    "gui.settledlands.loot_pool.editor.preview_blocked_inline",
)


def fail(msg):
    print("build442_check: FAIL - " + msg)
    return 1


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = []
    problems.extend(build441_check.check_structural(jar, base_jar))

    with zipfile.ZipFile(jar) as zf:
        for lp in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            table = json.loads(zf.read(lp).decode("utf-8"))
            for key in LANG_KEYS_442:
                if not table.get(key):
                    problems.append(f"{lp} missing or empty key: {key}")

        lp436 = zf.read("dev/settledlands/chest/LootPool436.class")
        for tok in (b"computeEffectiveChancesPct", b"blockedReason", b"countBlockedBySpec"):
            if tok not in lp436:
                problems.append(f"LootPool436 missing token {tok.decode()}")

        ed = zf.read("dev/settledlands/npc/client/LootPoolEditorScreen440.class")
        for tok in (b"nodeRarBox", b"nodeWgtBox", b"nodeRollsBox", b"renderNodeLimitsBar"):
            if tok not in ed:
                problems.append(f"LootPoolEditorScreen440 missing token {tok.decode()}")

    return problems


def compile_and_run_probe442(jar: pathlib.Path):
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

        probe442 = ROOT / "tools" / "harness" / "Build442Probe.java"
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar}:{stubs_out}", "-d", str(probe_out), str(probe442)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise AssertionError("Build442Probe compile failed:\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar}:{stubs_out}", "Build442Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if r3.returncode != 0:
            raise AssertionError(f"Build442Probe failed (rc={r3.returncode}):\n{r3.stdout}\n{r3.stderr}")
        sys.stdout.write(r3.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build442_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    base441 = ROOT / "settledlands-0.8.0-build441-rarity-crate-weight-and-silent-anchor.jar"
    if base441.is_file():
        rc = invoke_kind_check.main([str(jar), "--base", str(base441)])
        if rc != 0:
            return fail("invoke_kind_check failed")

    problems = check_structural(jar, base441 if base441.is_file() else None)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        build439_check.compile_and_run_all_probes(jar)
        build440_check.compile_and_run_probes(jar)
        build441_check.compile_and_run_probe441(jar)
        compile_and_run_probe442(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build442_check: OK (%s verified)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
