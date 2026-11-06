#!/usr/bin/env python3
"""Gate: a cracked door is not closed by a clock - it stays open while the player is at it.

The author, on build 394: «чето немного странное происходит, дверь после взлома сама закрывается через
секунд 5-10». That is build 377's eight-second grace (`OPEN_FOR_MS = 8000L`) running out: `DoorHack377`
remembered a cracked door with a timestamp, and `DoorGuard371` closed it when the timestamp was old. The
server log he attached shows it to the millisecond - `door_hack` at 14:52:57.623, `the guard closed the
locked door` at 14:53:05.666, and again 14:53:16.414 -> 14:53:24.463.

What this gate asks:

  * structurally, `DoorHack377` has no `cracked` any more (the timestamped question) and has `crackedOpen`
    plus the pure rule `keepOpen`; the guard of 371 asks `crackedOpen` and still lets guides through;
  * structurally, the rule is about the door and the player, not about time: `crackedOpen` reads
    `Doors368#isDoor` and `Doors368#anyOpen` (the mark dies with the open it was made for) and
    `getPlayerList` (somebody has to be at the door);
  * no 8000 millisecond grace is left anywhere in `DoorHack377`, and no `OPEN_FOR_MS` field either - the
    timer was the bug;
  * behaviourally, on the real class from the jar: `tools/harness/DoorCracked395Probe.java` walks the whole
    truth table of `keepOpen` - sixteen combinations, exactly one of which keeps a door open, and that one
    is "cracked by us, a door, open, somebody at it".

    python3 tools/door_cracked_check.py settledlands-0.8.0-build395-door-stays-open.jar

Exit 0 = a door hacked open stays open while the player is there, and is closed only when the room is empty.
"""
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "DoorCracked395Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

HACK = "dev/settledlands/door/DoorHack377.class"
GUARD = "dev/settledlands/door/DoorGuard371.class"
PROBE_CLASS = "DoorCracked395Probe"

EXPECT = {
    "RULE": "keeps=1 of=16",
    "TIMER": "keep=yes close=no",
    "NULL": "doors=false",
}


def refs_of(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    out = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, _desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        out.add((owner, name))
    return parsed, out


def methods_of(parsed):
    cp = parsed["cp"]
    return {cp[ni][1] for _a, ni, _d, _attrs in parsed["methods"]}


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (HACK, GUARD):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        hack, hack_refs = refs_of(zf.read(HACK))
        hack_methods = methods_of(hack)
        if "crackedOpen" not in hack_methods:
            problems.append("DoorHack377 has no crackedOpen() - the guard has no rule to ask")
        if "keepOpen" not in hack_methods:
            problems.append("DoorHack377 has no keepOpen() - the cracked-door rule is not stated in one"
                            " place any more")
        if "cracked" in hack_methods:
            problems.append("DoorHack377 still has the timestamped cracked() of build 377 - that question"
                            " is what closes the door under the player")
        for owner, name, why in (("dev/settledlands/door/Doors368", "anyOpen",
                                  "a cracked door is not checked for being open - the mark could outlive"
                                  " the door"),
                                 ("dev/settledlands/door/Doors368", "isDoor",
                                  "a cracked door is not checked for still being a door"),
                                 ("net/minecraft/server/players/PlayerList", "getPlayers",
                                  "nothing asks whether a player is at the cracked door - the door would"
                                  " be closed on a timer again")):
            if not any(r[0] == owner and r[1] == name for r in hack_refs):
                problems.append("%s: %s" % (why, name))
        if b"OPEN_FOR_MS" in zf.read(HACK):
            problems.append("OPEN_FOR_MS is still in DoorHack377 - the eight-second grace is the bug the"
                            " author reported")
        if b"8000" in zf.read(HACK):
            problems.append("the 8000 millisecond grace is still in DoorHack377")

        guard, guard_refs = refs_of(zf.read(GUARD))
        if not any(r[1] == "crackedOpen" and r[0] and r[0].endswith("door/DoorHack377")
                   for r in guard_refs):
            problems.append("the guard of 371 does not ask crackedOpen - a hacked door would be closed"
                            " again by whatever rule the guard has")
        if not any(r[1] == "guideOpened" for r in guard_refs):
            problems.append("the guard no longer lets a guide through - 371's own rule is gone")
    return problems


def run_probe(jar):
    if not PROBE.is_file():
        return None, ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / (PROBE_CLASS + ".class")).is_file():
            return None, ["the probe did not compile: %s"
                          % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), PROBE_CLASS],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return None, ["the probe crashed:\n%s" % output[-800:]]
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        return got, []


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("door_cracked_check: no such jar %s" % jar)
        return 2
    problems = structural(jar)
    got, more = run_probe(jar)
    problems += more
    if got is not None:
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    if problems:
        print("door_cracked_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("door_cracked_check: OK  %s  (a door hacked open stays open while the player is at it and is"
          " closed only when the room is empty - there is no clock left in the rule)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
