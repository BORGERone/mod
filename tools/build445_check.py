#!/usr/bin/env python3
"""Comprehensive verification gate for Build 445 (direct 0-100% per-entry loot chances
without duplicate entry draws everywhere + updated editor tooltips & chance column)."""

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build436_check
import build439_check
import build443_check
import build444_check
import invoke_kind_check
import patch_build445

ROOT = pathlib.Path(__file__).resolve().parent.parent
LAYERS = build443_check.LAYERS
TEST_SHIMS = build443_check.TEST_SHIMS


def fail(msg):
    print("build445_check: FAIL - " + msg)
    return 1


def check_structural(jar: pathlib.Path, base_jar: pathlib.Path | None = None):
    problems = []
    problems.extend(build444_check.check_structural(jar, base_jar))

    with zipfile.ZipFile(jar) as zf:
        for lp, expected in (
            ("assets/settledlands/lang/ru_ru.json", patch_build445.LANG_SET_RU),
            ("assets/settledlands/lang/en_us.json", patch_build445.LANG_SET_EN),
        ):
            table = json.loads(zf.read(lp).decode("utf-8"))
            for key, expected_val in expected.items():
                if table.get(key) != expected_val:
                    problems.append(f"{lp} key mismatch for {key}")
            if "CS:GO" in json.dumps(table, ensure_ascii=False):
                problems.append(f"{lp} still contains stale CS:GO wording")

        lp436 = zf.read("dev/settledlands/chest/LootPool436.class")
        for sym in ("MAX_CHANCE_PCT", "rollDrops", "rollLecternDrops", "rollBookshelfDrops",
                    "computeEffectiveChancesPct"):
            if sym.encode("utf-8") not in lp436:
                problems.append("LootPool436 missing " + sym)

        lpm = zf.read("dev/settledlands/chest/LootPoolManager436.class")
        if b"chance_pct" not in lpm or b"CS:GO" in lpm:
            problems.append("LootPoolManager436 guide text not updated for Build 445 direct %")

        ed = zf.read("dev/settledlands/npc/client/LootPoolEditorScreen440.class")
        for sym in ("preview_miss", "formatWeight", "countBlockedBySpec"):
            if sym.encode("utf-8") not in ed:
                problems.append("LootPoolEditorScreen440 missing " + sym)

    return problems


def compile_and_run_all_probes(jar: pathlib.Path):
    # Run all inherited Build 396..444 probes first (with updated Build 445 direct-% semantics).
    build444_check.compile_and_run_all_probes(jar)

    jar = jar.resolve()
    java = build439_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        shims_out = td / "shims_out"
        compat436_out = td / "compat436_out"
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

        build439_check._compile_map(java, ecj, TEST_SHIMS, f"{stubs_out}:{jar}", shims_out)
        compat436_only = {k: v for k, v in build436_check.COMPAT_SOURCES.items()
                          if k != "Build436CompatProbe.java"}
        build439_check._compile_map(java, ecj, compat436_only,
                                    f"{shims_out}:{jar}:{stubs_out}", compat436_out)

        r_445 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{shims_out}:{compat436_out}:{jar}:{stubs_out}", "-d", str(probe_out),
             str(ROOT / "tools" / "harness" / "Build445Probe.java")],
            capture_output=True, text=True)
        if r_445.returncode != 0:
            raise AssertionError("Build445Probe compile failed:\n" + r_445.stdout + r_445.stderr)

        run_445 = subprocess.run(
            [java, "-cp", f"{probe_out}:{shims_out}:{compat436_out}:{jar}:{stubs_out}", "Build445Probe"],
            cwd=str(work_dir), capture_output=True, text=True)
        if run_445.returncode != 0:
            raise AssertionError(f"Build445Probe failed (rc={run_445.returncode}):\n{run_445.stdout}\n{run_445.stderr}")
        sys.stdout.write(run_445.stdout)


def main(argv):
    if len(argv) != 1:
        print("usage: build445_check.py <jar>")
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    base444 = ROOT / "settledlands-0.8.0-build444-book-limits-and-lectern-odds.jar"
    if base444.is_file():
        rc = invoke_kind_check.main([str(jar), "--base", str(base444)])
        if rc != 0:
            return fail("invoke_kind_check failed")

    problems = check_structural(jar, base444 if base444.is_file() else None)
    if problems:
        for p in problems:
            print("  - " + p)
        return fail("structural check failed")

    try:
        compile_and_run_all_probes(jar)
    except AssertionError as ex:
        return fail(str(ex))

    print("build445_check: PASS (%s verified)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
