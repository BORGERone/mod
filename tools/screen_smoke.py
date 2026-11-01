#!/usr/bin/env python3
"""Headless smoke test for a screen - the gate that would have caught Build 306.

Build 306 crashed the first time the node workspace was drawn:

    ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6
        at NpcWorkspace306.drawTopBar

Every offline gate passed: the class verified, the mixins resolved, the jar was well formed.
The bug only existed at runtime, inside an index nobody had executed.

This runs the screen in a plain JVM instead. The mod's compilation stubs are used as real
classes (they have no bodies worth having), the harness versions of Minecraft, Font, Screen and
CompoundTag replace the four that the screen actually talks to, and a driver builds a graph with
all fifteen node types plus every kind of link, then drives the screen the way a player would:
construct, init, resize, render, click, drag, box select, link, cut, scroll, search, type,
undo, redo, delete, switch tabs.

Usage: python3 tools/screen_smoke.py <jar> [--stubs build305-src/stubs]
                                    [--api build305-src/api] [--api2 build305-src/api2]
                                    [--test tools/harness/WorkspaceSmokeTest.java]
Exit code 0 = every step ran without throwing.
"""

import argparse
import os
import pathlib
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
JAVA = "/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
HARNESS = HERE / "harness"

# The four classes the harness replaces: they need behaviour, not just signatures.
OVERRIDES = (
    "net/minecraft/client/Minecraft.java",
    "net/minecraft/client/gui/Font.java",
    "net/minecraft/client/gui/screens/Screen.java",
    "net/minecraft/nbt/CompoundTag.java",
)


def java_sources(root):
    return sorted(str(p) for p in pathlib.Path(root).rglob("*.java"))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--stubs", default="build305-src/stubs")
    ap.add_argument("--api", default="build305-src/api")
    ap.add_argument("--api2", default="build305-src/api2")
    ap.add_argument("--test", default="tools/harness/WorkspaceSmokeTest.java")
    ap.add_argument("--work", default="/tmp/screen-smoke")
    args = ap.parse_args(argv)

    jar = pathlib.Path(args.jar).resolve()
    if not jar.is_file():
        print("screen_smoke: no such jar %s" % jar)
        return 2
    if not pathlib.Path(JAVA).is_file():
        print("screen_smoke: SKIP - jdk4py java is not on this machine")
        return 0
    for path in (args.api, args.api2, args.stubs):
        if not pathlib.Path(path).is_dir():
            print("screen_smoke: SKIP - missing %s" % path)
            return 0

    work = pathlib.Path(args.work)
    if work.exists():
        shutil.rmtree(work)
    stubs_out = work / "stubs-out"
    test_out = work / "test-out"
    stubs_out.mkdir(parents=True)
    test_out.mkdir(parents=True)

    # Copy the compile stubs, then let the harness own the four classes with behaviour.
    shutil.copytree(args.stubs, work / "stubs")
    for name in OVERRIDES:
        target = work / "stubs" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(HARNESS / name, target)

    cp = os.pathsep.join([args.api2, args.api, str(work / "stubs")])
    compile_stubs = subprocess.run(
        [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-cp", cp, "-d", str(stubs_out)]
        + java_sources(work / "stubs"),
        capture_output=True, text=True)
    if compile_stubs.returncode != 0:
        print("screen_smoke: cannot compile the harness stubs")
        print(compile_stubs.stdout[-2000:])
        return 2

    run_cp = os.pathsep.join([str(stubs_out), args.api2, args.api, str(jar)])
    compile_test = subprocess.run(
        [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-cp", run_cp, "-d", str(test_out),
         args.test],
        capture_output=True, text=True)
    if compile_test.returncode != 0:
        print("screen_smoke: cannot compile %s" % args.test)
        print(compile_test.stdout[-2000:])
        return 2

    run = subprocess.run(
        [JAVA, "-cp", os.pathsep.join([str(test_out), run_cp]), "WorkspaceSmokeTest"],
        capture_output=True, text=True, timeout=300)
    output = run.stdout + run.stderr
    if run.returncode != 0 or "FAILURES" in output:
        print(output[-6000:])
        print("screen_smoke: FAIL  %s" % jar.name)
        return 1
    steps = sum(1 for line in output.splitlines() if line.startswith("ok    "))
    print("screen_smoke: OK  %s" % jar.name)
    print("  screen steps driven : %d" % steps)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
