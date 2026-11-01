#!/usr/bin/env python3
"""Gate: the node clipboard must survive a round trip, and the screen must be wired to it.

Build 318 puts copy and paste on Ctrl+C / Ctrl+V. The risky half is the format, not the keys: a
field that is written but never read back, a link that keeps pointing at an old id, an entry node
copied into a graph that already has one - all of that is silent in game until a dialogue breaks.

So the shipped jar is asked directly: the test in tools/tests/ClipboardRoundTrip.java is compiled
against this jar, run headlessly, and expected to copy a branch, paste it into a second graph and
compare every interesting field, every remapped link and the two failure paths.

The rest is wiring: the mixin class must exist, be in the client mixin list, and the status keys it
uses must be in the language files, otherwise the editor reports a raw key name.

    python3 tools/clipboard_check.py settledlands-0.8.0-build318-clipboard.jar

Exit 0 = the round trip passes on this jar and the screen is wired to it.
"""
import json
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = "/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
TEST = ROOT / "tools" / "tests" / "ClipboardRoundTrip.java"
# The graph model names Minecraft types in its signatures, so the compile-time mirrors
# have to be on the classpath - they are never shipped.
STUBS = [str(ROOT / "build303-src" / "api3"), str(ROOT / "build303-src" / "api2"),
         str(ROOT / "build303-src" / "api"), str(ROOT / "build303-src" / "stubs-out")]

MIXIN = "dev/settledlands/mixin/NpcClipboardMixin318.class"
MIXIN_NAME = "NpcClipboardMixin318"
CLIPBOARD = "dev/settledlands/npc/NpcGraphClipboard318.class"
PLATFORM = "dev/settledlands/npc/client/NpcClipboard318.class"
KEYS = ("clip_copy", "clip_empty", "clip_paste", "clip_fail")


def run(jar):
    problems = []
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for needed in (CLIPBOARD, PLATFORM, MIXIN):
            if needed not in names:
                problems.append("missing %s" % needed)
        if problems:
            return problems
        config = json.loads(z.read("settledlands.mixins.json").decode("utf-8"))
        if MIXIN_NAME not in config.get("client", []):
            problems.append("%s is not in the client mixin list" % MIXIN_NAME)
        for lang in ("assets/settledlands/lang/ru_ru.json",
                     "assets/settledlands/lang/en_us.json"):
            if lang not in names:
                problems.append("missing %s" % lang)
                continue
            keys = json.loads(z.read(lang).decode("utf-8"))
            for key in KEYS:
                if "gui.settledlands.graph." + key not in keys:
                    problems.append("%s has no gui.settledlands.graph.%s" % (lang, key))

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        compiled = subprocess.run(
            [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-nowarn", "-proceedOnError",
             "-cp", ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(TEST)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "ClipboardRoundTrip.class").is_file():
            problems.append("the round trip test did not compile: %s"
                            % (compiled.stdout + compiled.stderr).strip()[-400:])
            return problems
        result = subprocess.run(
            [JAVA, "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "ClipboardRoundTrip"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0 or "round trip: OK" not in output:
            problems.append("the round trip failed:\n%s" % output[-800:])
    return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("clipboard_check: no such jar %s" % jar)
        return 2
    problems = run(jar)
    if problems:
        print("clipboard_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("clipboard_check: OK  %s  (copy and paste round trip, screen wired)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
