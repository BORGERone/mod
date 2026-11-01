#!/usr/bin/env python3
"""Gate: a reroute point must bend the cable and change nothing else.

Build 321 adds points to a wire. The feature is meant to be purely visual, and that is exactly the
kind of claim that quietly stops being true: one write to a link field and the point becomes an
edit, one extra field read and the bend appears inside an unrelated setting.

So the shipped jar is asked two things:

  * no link field is touched - the model may read the saved text field and the node type, and it may
    read a link to know which wire a bend belongs to, but a point store that assigns `next`,
    `falseNext`, `choices`, `triggers`, `randomBranches` or `varInputNode` is a bug whatever it looks
    like;
  * the round trip in tools/tests/WirePointsRoundTrip.java passes: the text format, the grid, the
    fallback field of an animation node, the refusal to overwrite foreign text, and the connection
    staying intact through add, move and delete. That test is compiled against this jar and run
    headlessly.

    python3 tools/wire_points_check.py settledlands-0.8.0-build321-wire-points.jar

Exit 0 = points survive a round trip and cannot change a connection.
"""
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = "/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
TEST = ROOT / "tools" / "tests" / "WirePointsRoundTrip.java"
STUBS = [str(ROOT / "build303-src" / "api3"), str(ROOT / "build303-src" / "api2"),
         str(ROOT / "build303-src" / "api"), str(ROOT / "build303-src" / "stubs-out")]

MODEL = "dev/settledlands/npc/NpcWirePoints321.class"
UI = "dev/settledlands/npc/client/NpcWirePointsUi321.class"
MIXIN = "dev/settledlands/mixin/NpcWirePointsMixin321.class"
JOIN = "dev/settledlands/npc/NpcWireJoin322.class"
MIXIN_NAME = "NpcWirePointsMixin321"

# Reading a link is how a bend knows which wire it belongs to; assigning one would make the point an
# edit. These are the fields that must never be written by the point store.
LINKS = ("next", "falseNext", "choices", "triggers", "randomBranches", "varInputNode")

# The only node fields the point store may touch: the two it may use for storage and the type.
ALLOWED = ("animBone", "mobilityGroupId", "type")


def _scan(info, opcodes):
    """{[opcode]} of Node fields touched by the given field opcodes, read straight from the code.

    A bytecode walker is not needed for a gate: the opcode plus the two bytes that follow it name a
    constant-pool entry, and an entry that is not a field of the node class is ignored. A false
    positive would need the same two bytes inside another instruction to point at a Fieldref of the
    node, which the length-prefixed formats make unlikely, and the gate is about not finding a write
    at all.
    """
    cp = info["cp"]
    out = set()
    for _acc, _ni, _di, attrs in info["methods"]:
        got = disasm.code_of(attrs, cp)
        if got is None:
            continue
        code = got["code"]
        for i in range(len(code) - 2):
            if code[i] not in opcodes:
                continue
            idx = struct.unpack(">H", code[i + 1:i + 3])[0]
            if not 0 < idx < len(cp) or cp[idx] is None or cp[idx][0] != 9:
                continue
            try:
                ref = disasm.ref(cp, idx)
            except (KeyError, IndexError, struct.error):
                continue
            if "NpcDialogueGraph$Node." not in ref:
                continue
            out.add(ref.split("NpcDialogueGraph$Node.")[1].split("L")[0].split(":")[0].strip())
    return out


def node_field_writes(info):
    """Every Node field the class assigns."""
    return _scan(info, {0xB5})


def node_field_reads(info):
    return _scan(info, {0xB4, 0xB5})


def _all_refs(info):
    cp = info["cp"]
    out = set()
    for i in range(1, len(cp)):
        entry = cp[i]
        if entry and entry[0] in (9, 10, 11):
            try:
                out.add(disasm.ref(cp, i))
            except (KeyError, IndexError):
                continue
    return out


def run(jar):
    problems = []
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for needed in (MODEL, UI, MIXIN, JOIN):
            if needed not in names:
                problems.append("missing %s" % needed)
        if problems:
            return problems
        config = json.loads(z.read("settledlands.mixins.json").decode("utf-8"))
        if MIXIN_NAME not in config.get("client", []):
            problems.append("%s is not in the client mixin list" % MIXIN_NAME)

        model = disasm.parse_class(z.read(MODEL))
        writes = node_field_writes(model)
        for link in LINKS:
            if link in writes:
                problems.append("the point store writes Node#%s - a bend must never change a "
                                "connection" % link)
        for extra in sorted(writes - set(ALLOWED)):
            problems.append("the point store writes Node#%s, which is not a field it may use"
                            % extra)
        # Build 322: a dropped wire joins the point through the graph's own connect, so the join
        # class is the only place that may change a connection - and it must go through connect,
        # never by writing a link field itself.
        join = disasm.parse_class(z.read(JOIN))
        join_writes = node_field_writes(join)
        if join_writes:
            problems.append("the join writes Node#%s directly - it must use graph.connect"
                            % ", ".join(sorted(join_writes)))
        if not any("NpcDialogueGraph.connect" in r for r in _all_refs(join)):
            problems.append("the join never calls graph.connect")

        reads = node_field_reads(model)
        if "animBone" not in reads or "mobilityGroupId" not in reads:
            problems.append("the stored text is not read back from both fields")

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        compiled = subprocess.run(
            [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-cp",
             ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(TEST)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "WirePointsRoundTrip.class").is_file():
            problems.append("the round trip test did not compile: %s"
                            % (compiled.stdout + compiled.stderr).strip()[-400:])
            return problems
        result = subprocess.run(
            [JAVA, "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "WirePointsRoundTrip"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0 or "wire points: OK" not in output:
            problems.append("the round trip failed:\n%s" % output[-800:])
    return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("wire_points_check: no such jar %s" % jar)
        return 2
    problems = run(jar)
    if problems:
        print("wire_points_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("wire_points_check: OK  %s  (bends round trip, connections untouched)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
