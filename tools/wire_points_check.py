#!/usr/bin/env python3
"""Gate: a point holds wires, and only an aim connects them.

A point is a place wires meet. Two things about it are easy to break and impossible to see in a
screenshot: the store must never write a connection of its own accord, and the store must never
lose the text it does not own. The join is the only class allowed to change a connection, and it
must go through the graph's own connect and disconnect rather than through a link field.

So the shipped jar is asked these things:

  * no link field is touched - the model may read the saved text field and the node type, and it may
    read a link to know which wire a bend belongs to, but a point store that assigns `next`,
    `falseNext`, `choices`, `triggers`, `randomBranches` or `varInputNode` is a bug whatever it looks
    like;
  * a wire dropped on a point waits, and a point only connects its wires when it is aimed - the
    round trip in tools/tests/WirePointsRoundTrip.java proves both, and is compiled against this
    jar and run headlessly;
  * a point can always be grabbed: its hit area is wider than the dot it draws, at every zoom
    (tools/tests/PointHitTest.java), because a dot that is hard to hit turns "move the point" into
    "make another point next to it";
  * a waiting wire is drawn by the screen's own curve routine, not approximated with rectangles:
    the class that draws it must build the same buffer the screen builds and call the same
    {@code curve}, and a wire must be pullable out of a point's port.

    python3 tools/wire_points_check.py settledlands-0.8.0-build323-wire-hub.jar

Exit 0 = points wait, aim, move, delete and cannot change a connection by themselves.
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
HIT_TEST = ROOT / "tools" / "tests" / "PointHitTest.java"
CLICK_TEST = ROOT / "tools" / "tests" / "ClickOrder.java"
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
        join_refs = _all_refs(join)
        if not any("NpcDialogueGraph.connect" in r for r in join_refs):
            problems.append("the join never calls graph.connect")
        if not any("NpcDialogueGraph.disconnect" in r for r in join_refs):
            problems.append("the join never calls graph.disconnect - a rerouted wire would keep "
                            "its old target")
        # Build 323: the drop must only attach, and the aim is what connects.
        if not any("NpcWirePoints321.attach" in r for r in join_refs):
            problems.append("the join never attaches a wire to the point")
        if not any("NpcWirePoints321.promote" in r for r in join_refs):
            problems.append("the aim never promotes a waiting wire")

        ui = disasm.parse_class(z.read(UI))
        ui_refs = _all_refs(ui)
        if not any("NpcWirePoints321.points" in r for r in ui_refs):
            problems.append("the ui does not read the points of the graph")
        mixin = disasm.parse_class(z.read(MIXIN))
        mixin_refs = _all_refs(mixin)
        for needed in ("NpcWireJoin322.attach", "NpcWireJoin322.aim", "NpcWirePoints321.removePoint",
                       "NpcWirePoints321.movePoint", "NpcWirePoints321.addBend"):
            if not any(needed in r for r in mixin_refs):
                problems.append("the screen hook never calls %s" % needed)
        # Build 324: the wire that waits at a point, and the band that follows the cursor while a
        # wire is pulled out of it, are drawn with the screen's own cable routine.
        for needed in ("blaze3d/vertex/Tesselator", "blaze3d/vertex/BufferUploader",
                       "blaze3d/vertex/DefaultVertexFormat"):
            if not any(needed in r for r in mixin_refs):
                problems.append("the hook never builds a buffer of its own (%s) - a waiting wire "
                                "must be drawn like every other cable" % needed)
        mixin_bytes = z.read(MIXIN)
        # A shadowed method is called on the mixin class itself, so the reference that proves the
        # screen's own curve routine is used is the shadow of it with this signature.
        curve_sig = b"Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;DDDDIZ)I"
        if curve_sig not in mixin_bytes:
            problems.append("the hook has no shadow of the screen's curve routine")
        if b"Lorg/spongepowered/asm/mixin/Shadow;" not in mixin_bytes:
            problems.append("the hook declares no shadows at all")
        if not any("curve" in r for r in mixin_refs):
            problems.append("the hook never draws a cable with the screen's own curve routine")
        ui_bytes = z.read(UI)
        for needle in ("portAt", "overPort", "portX", "portY"):
            if needle.encode("utf-8") not in ui_bytes:
                problems.append("the ui has no %s - a wire cannot be pulled out of a point"
                                % needle)
        if b"settledlands$pull" not in mixin_bytes:
            problems.append("the screen hook has no pull state")
        # Build 325: a run of points, and a point made on a waiting wire.
        if not any("NpcWirePoints321.addRoutePoint" in r for r in mixin_refs):
            problems.append("a point cannot be made on a wire that waits at a point")
        if b"addRoutePoint" not in z.read(MODEL):
            problems.append("the store cannot grow the run of a waiting wire")
        if b"linkDistance" not in ui_bytes:
            problems.append("the wire hit test does not follow the drawn route")
        # Build 326: the press is read in one place, and only the dot itself removes a point.
        if b"onDot" not in ui_bytes or b"decide" not in ui_bytes:
            problems.append("the press has no single, testable decision")
        if not any("NpcWirePointsUi321.decide" in r for r in mixin_refs):
            problems.append("the screen does not read its clicks through the decision")

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

        compiled = subprocess.run(
            [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-cp",
             ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(HIT_TEST)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "PointHitTest.class").is_file():
            problems.append("the hit test did not compile: %s"
                            % (compiled.stdout + compiled.stderr).strip()[-400:])
            return problems
        result = subprocess.run(
            [JAVA, "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "PointHitTest"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0 or "point hit: OK" not in output:
            problems.append("a point cannot be grabbed reliably:\n%s" % output[-800:])

        compiled = subprocess.run(
            [JAVA, "-jar", str(ECJ), "-21", "-nowarn", "-cp",
             ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(CLICK_TEST)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "ClickOrder.class").is_file():
            problems.append("the click order test did not compile: %s"
                            % (compiled.stdout + compiled.stderr).strip()[-400:])
            return problems
        result = subprocess.run(
            [JAVA, "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "ClickOrder"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0 or "click order: OK" not in output:
            problems.append("the wrong gesture wins:\n%s" % output[-800:])
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
    print("wire_points_check: OK  %s  (wires wait at a point, aim connects, points can be grabbed)"
          % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
