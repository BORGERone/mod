#!/usr/bin/env python3
"""Gate: the node wired to the door card's output socket is the FIRST step of the chain, not the one before it.

The author's report on build 392, with a screenshot of a card wired straight to a «Реплика» and to its blue
socket: «ожидал, что после взлома (и после отпускания всех кнопок) начнется диалог, но ничего не произошло».
The card showed «Взломал: BORGER», so the hack had run - and the chain had not.

The cause was one call: build 392 handed the wired node to `BlockTrigger351#fire`, the entry the event nodes
(«В радиусе», «Время», «Таймер», «Цель») use. That entry runs a node's own action and then walks on from
that node's `next` - the node itself is never stepped over, because for an event node it is not a step of a
chain. A «Реплика» is the opposite: it is nothing but its step. Wired straight to the card it was skipped and
the graph was silent, with no error anywhere.

What this gate asks:

  * structurally, `DoorSignal388#hack` runs the chain through `BlockTrigger351#start` and no longer calls
    `BlockTrigger351#fire` at all - the two entries are not interchangeable;
  * behaviourally, on the real classes from the jar: `tools/harness/DoorChain393Probe.java` is compiled
    against this jar and run, and it steps the interpreter with no player and no world -
    * a lone «Реплика» is 1 step (it is run, not skipped),
    * «Реплика» -> «Триггер» -> «Переменная» is 3 steps,
    * a door with nothing wired is 0 steps,
    * the node the card's socket points at is that first step (what a hack runs),
    * `fire` still behaves like an event entry - it walks on from `next` only.

    python3 tools/door_chain_check.py settledlands-0.8.0-build393-door-chain-runs.jar

Exit 0 = a chain wired to the door's socket really starts at that node, so a «Реплика» speaks after a hack.
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
PROBE = ROOT / "tools" / "harness" / "DoorChain393Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

SIGNAL = "dev/settledlands/door/DoorSignal388.class"
TRIGGER = "dev/settledlands/npc/BlockTrigger351.class"
TRIGGER_CLASS = "dev/settledlands/npc/BlockTrigger351"
PROBE_CLASS = "DoorChain393Probe"

EXPECT = {
    "SINGLE": "steps=1",
    "CHAIN": "steps=3",
    "NOTHING": "steps=0",
    "SOCKET": "start=3 socket=line",
    "EVENT": "fire=after",
}


def fail(message):
    print("FAIL: " + message)
    return 1


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


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (SIGNAL, TRIGGER):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems
        _parsed, signal_refs = refs_of(zf.read(SIGNAL))
        if (TRIGGER_CLASS, "start") not in signal_refs:
            problems.append("DoorSignal388 does not call BlockTrigger351#start - the node wired to the card's"
                            " socket would not be run")
        if (TRIGGER_CLASS, "fire") in signal_refs:
            problems.append("DoorSignal388 still calls BlockTrigger351#fire - that entry skips the node it is"
                            " given and walks on from its next, which is why a «Реплика» wired to the card said"
                            " nothing at all")
        trigger, _trigger_refs = refs_of(zf.read(TRIGGER))
        cp = trigger["cp"]
        methods = {cp[ni][1] for _a, ni, _d, _attrs in trigger["methods"]}
        if "start" not in methods:
            problems.append("BlockTrigger351 has no start() - there is no way to run a chain from its first"
                            " node")
        if "walk" not in methods:
            problems.append("BlockTrigger351 has no walk() - the interpreter is gone")
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
            [str(JAVA), "-cp", ":".join([str(tmpdir)] + [str(jar)] + STUBS), PROBE_CLASS],
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
        print("door_chain_check: no such jar %s" % jar)
        return 2
    problems = structural(jar)
    got, more = run_probe(jar)
    problems += more
    if got is not None:
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    if problems:
        print("door_chain_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("door_chain_check: OK  %s  (the node wired to the door card's output socket is the first step of"
          " the chain - a «Реплика» wired straight to the card speaks after a hack, a chain of three nodes"
          " steps three times, an empty socket steps nothing, and fire() keeps its event semantics)"
          % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
