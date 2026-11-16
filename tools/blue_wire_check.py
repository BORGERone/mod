#!/usr/bin/env python3
"""Gate: a blue value wire from the damage node must be drawn and cut.

Build 309 shipped a wire that was stored in the graph, read by the server and never painted: the
two places that handle a wire into a pursuit node's blue input asked for the "В радиусе" node
alone, so a wire from the "Урон" node fell through. This gate reads the shipped class and fails
if either of those two methods asks for one producer instead of the whole value-source family.

    python3 tools/blue_wire_check.py settledlands-0.8.0-build310-blue-wire.jar

Exit 0 = the drawing and the cut both accept any blue output.
"""
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

MIXIN = "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class"
WATCHED = ("settledlands$paintValueWires", "settledlands$cutValueWire")
WANTED = "isValueSourceNode"
FORBIDDEN = "isCheckValueNode"
INVOKES = (0xb6, 0xb7, 0xb8, 0xb9)


def attr_name(cp, idx):
    return cp[idx][1]


def code_refs(cp, code):
    """Every method reference in one Code attribute, as simple names."""
    p = 4  # max_stack, max_locals
    length = struct.unpack(">I", code[p:p + 4])[0]
    p += 4
    body = code[p:p + length]
    out = set()
    i = 0
    while i < len(body):
        op = body[i]
        entry = disasm.OPS.get(op)
        if entry is None:
            raise SystemExit("unknown opcode 0x%02x in %s" % (op, MIXIN))
        name, size = entry
        if op in INVOKES:
            idx = struct.unpack(">H", body[i + 1:i + 3])[0]
            out.add(disasm.ref(cp, idx).rsplit(".", 1)[-1].split("(", 1)[0])
        i += 1 + size
    return out


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("blue_wire_check: no such jar %s" % jar)
        return 2
    with zipfile.ZipFile(jar) as z:
        if MIXIN not in z.namelist():
            print("blue_wire_check: SKIP - this jar has no value-pin mixin")
            return 0
        data = z.read(MIXIN)

    info = disasm.parse_class(data)
    cp = info["cp"]
    seen = {}
    for _acc, ni, _di, attrs in info["methods"]:
        mname = cp[ni][1]
        if mname not in WATCHED:
            continue
        for an, ab in attrs:
            if attr_name(cp, an) != "Code":
                continue
            try:
                seen.setdefault(mname, set()).update(code_refs(cp, ab))
            except SystemExit as exc:
                print("blue_wire_check: %s" % exc)
                return 1

    problems = []
    for mname in WATCHED:
        refs = seen.get(mname)
        if refs is None:
            problems.append("%s is gone from the mixin" % mname)
            continue
        if FORBIDDEN in refs:
            problems.append("%s tests %s: a wire from the damage node is skipped"
                            % (mname, FORBIDDEN))
        if WANTED not in refs:
            problems.append("%s never tests %s" % (mname, WANTED))

    if problems:
        print("blue_wire_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("blue_wire_check: OK  %s  (draw and cut both accept any blue output)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
