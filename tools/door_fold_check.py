#!/usr/bin/env python3
"""Gate: the two nodes of builds 389-391 fold into the sockets of the door card, without losing a wire.

The author's request of build 392 - «я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде \"дверь\"» - means the
two cards he was given in 389-391 have to disappear from a graph he already has, and the graph has to keep
working: his chain, his readers, his card, his node positions.

That is rewiring, and rewiring is the one thing a bytecode check cannot see: a wire the fold forgot looks
exactly like a wire that was never drawn. So this gate runs the fold, on the real classes from the jar,
through `tools/harness/DoorSockets392Probe.java`:

  * FRESH   - a door nobody has touched is one card: «Начало» on it, no chain, the four defaults;
  * ALLOWED - a door's palette may create the card and must refuse 41 and 42, the two types of the old
              builds - otherwise the nodes come straight back from the create menu;
  * BEFORE  - the graph is built the way build 391 left it: card -> pulse -> line, a «Триггер» reading the
              blue target, «Начало» on the pulse, and the target holding a name;
  * AFTER   - after the fold there are no nodes of the two old types, the card's `next` is the node the
              pulse pointed at (the execution socket), the trigger's `varInputNode` is the card (the blue
              socket), and «Начало» is on the card;
  * OPENER  - the name the blue target held is on the card, so the reference survives the fold;
  * CHAIN   - `DoorGraph382#chainStart` returns the node the card now points at - what a hack runs;
  * AGAIN   - a second open folds nothing: the fold is idempotent, so opening a graph twice cannot eat a
              node;
  * KEPT    - the card keeps its coordinates and a node keeps its text: the fold rewires, it does not
              rebuild the author's graph.

The structural half of the same question (which classes, which keys, which fields) is `door_stock_check.py`.

    python3 tools/door_fold_check.py settledlands-0.8.0-build392-door-card-sockets.jar

Exit 0 = the old nodes are folded into the card's sockets, the chains and readers survive, and nothing the
author drew is re-created or moved.
"""
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "DoorSockets392Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

LOGIC = "dev/settledlands/door/DoorGraph382.class"

EXPECT = {
    # One card, «Начало» on it, nothing wired, the four defaults of a door.
    "FRESH": "nodes=1 entry=door chainStart=none keys=6 speed=5 sealed=true",
    # The card is a door type; the two types of builds 389-391 are refused.
    "ALLOWED": "door=true pulse=false target=false",
    # The shape build 391 left behind, and what is left of it after one open.
    "BEFORE": "nodes=5 doorTo=pulse reader=target",
    "AFTER": "nodes=3 doorTo=line reader=door entry=door folded=true",
    "OPENER": "card=Steve",
    "CHAIN": "start=line",
    "AGAIN": "folded=false nodes=3",
    "KEPT": "x=120 y=120 readerText=hello",
}


def fail(message):
    print("FAIL: " + message)
    return 1


def read_probe(jar):
    """Compiles the probe against the jar and runs it - the fold is only checked by being run."""
    if not PROBE.is_file():
        return None, ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "DoorSockets392Probe.class").is_file():
            return None, ["the probe did not compile: %s"
                          % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir)] + [str(jar)] + STUBS),
             "DoorSockets392Probe"],
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
        print("door_fold_check: no such jar %s" % jar)
        return 2
    with zipfile.ZipFile(jar) as zf:
        if LOGIC not in set(zf.namelist()):
            return fail("the jar has no %s - a door's graph is not in this build" % LOGIC)
    got, problems = read_probe(jar)
    if problems:
        print("door_fold_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    for key, want in EXPECT.items():
        if got.get(key) != want:
            problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    if problems:
        print("door_fold_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("door_fold_check: OK  %s  (the pulse and the blue target of builds 389-391 fold into the two "
          "sockets of the door card: the card keeps the chain, every reader of the blue output reads the "
          "card, «Начало» and the name of the last hacker move onto it, a second open folds nothing, and "
          "no node of the author's is moved or rebuilt)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
