#!/usr/bin/env python3
"""Gate: every node a door's create menu offers is run by someone - a step by the walk, an event by the pass.

The author's report on build 393: «у меня к примеру теперь не работает нода в радиусе, проверь и остальные
ноды». Build 392 opened the whole palette of a block in a door's graph (`DoorGraph382#paletteRows` is
`BlockTrigger351#PALETTE_ROWS`), and the only thing that ever ran a door's graph was the card's hack - a
chain that starts where the output socket points. So the four event nodes («В радиусе», «Цель», «Время»,
«Таймер»), which are not steps of a chain but fire on their own, could be placed on a door, wired, saved -
and never fire, because `RadiusPass354` walked block graphs only. That is exactly "the radius node does not
work": the node is there, the wire is there, and nothing in the world ever asks.

What this gate asks:

  * structurally, `RadiusPass354` knows the door store: it reads `DoorGraph382Store#ofDimension`/`#graph`, asks
    `DoorStore368#positionOf` for the place, checks `DoorGraph382Store#isDoorAt`, and builds the same pass
    over it as over a block (`BlockTrigger351#fire` is still the interpreter behind both). The block path is
    still there: `BlockTriggerStore351#ofDimension` and its `Entry` are untouched;
  * structurally, the promise is one rule: `BlockTrigger351#allowed` is `isStep || isEventType`, so a type
    the menu offers cannot be a type nobody runs;
  * behaviourally, on the real classes from the jar: `tools/harness/DoorEvent394Probe.java` is compiled
    against this jar and run,
    * every row of the door's menu is a step or an event (no third kind),
    * `allowed` answers yes for the card, a step and an event, and no for a type outside the palette,
    * `isEventType` is true for exactly the four event nodes,
    * a door's graph with «В радиусе» -> «Реплика» is seen by the pass (`hasFiringEvent`), one with the
      node unwired is not,
    * that node's chain starts at its `next` (one «Реплика» = 1 step) and `fire` still runs it,
    * the ring follows the visibility toggle.

    python3 tools/door_event_check.py settledlands-0.8.0-build394-door-event-pass.jar

Exit 0 = every node a door's graph can hold is actually run, and a door's event node is seen by the pass.
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
PROBE = ROOT / "tools" / "harness" / "DoorEvent394Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

PASS_CLASS = "dev/settledlands/npc/RadiusPass354.class"
DOOR_PASS = "dev/settledlands/npc/RadiusPass354$DoorSpot.class"
SPOT = "dev/settledlands/npc/RadiusPass354$Spot.class"
TRIGGER = "dev/settledlands/npc/BlockTrigger351.class"
PROBE_CLASS = "DoorEvent394Probe"

EXPECT = {
    "PALETTE": "rows=11 step=7 event=4 unknown=0",
    "ALLOWED": "card=yes step=yes event=yes junk=no",
    "EVENT": "radius=yes time=yes line=no card=no",
    "UNWIRED": "event=no",
    "DOOR": "event=yes",
    "CHAIN": "start=1",
    "FIRE": "event=yes",
    "RING": "off=no on=yes",
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
        for needed in (PASS_CLASS, DOOR_PASS, SPOT, TRIGGER):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        parsed, pass_refs = refs_of(zf.read(PASS_CLASS))
        # The door half of the pass.
        for owner, name in (("dev/settledlands/door/DoorGraph382Store", "ofDimension"),
                            ("dev/settledlands/door/DoorGraph382Store", "graph"),
                            ("dev/settledlands/door/DoorGraph382Store", "isDoorAt"),
                            ("dev/settledlands/door/DoorStore368", "positionOf"),
                            ("dev/settledlands/door/DoorStore368", "dimensionOf")):
            if (owner, name) not in pass_refs:
                problems.append("RadiusPass354 never calls %s#%s - a door's graph is not walked, so its"
                                " event nodes never fire" % (owner.split("/")[-1], name))
        # The block half must still be there.
        if ("dev/settledlands/npc/BlockTriggerStore351", "ofDimension") not in pass_refs:
            problems.append("RadiusPass354 no longer reads the block graphs (ofDimension) - a block's radius"
                            " node would stop firing")
        pass_methods = methods_of(parsed)
        for name in ("hasFiringEvent", "hasVisibleRadius", "isEventType"):
            if name not in pass_methods:
                problems.append("RadiusPass354 has no %s()" % name)
        if ("dev/settledlands/npc/BlockTrigger351", "fire") not in pass_refs:
            problems.append("RadiusPass354 no longer fires through BlockTrigger351#fire - a door's event"
                            " node would have no interpreter behind it")

        trigger, trigger_refs = refs_of(zf.read(TRIGGER))
        trigger_methods = methods_of(trigger)
        if "isStep" not in trigger_methods:
            problems.append("BlockTrigger351 has no isStep() - nothing states which types a chain runs")
        if ("dev/settledlands/npc/RadiusPass354", "isEventType") not in trigger_refs:
            problems.append("BlockTrigger351#allowed does not ask RadiusPass354#isEventType - the menu's"
                            " promise and the pass are not tied together any more")
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
        print("door_event_check: no such jar %s" % jar)
        return 2
    problems = structural(jar)
    got, more = run_probe(jar)
    problems += more
    if got is not None:
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    if problems:
        print("door_event_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("door_event_check: OK  %s  (every node a door's create menu offers is run - a step by the walk of"
          " the chain, an event node by the pass, which now walks a door's graph exactly as it walks a"
          " block's)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
