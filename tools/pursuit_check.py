#!/usr/bin/env python3
"""Gate: a damage-driven pursuit must keep its body and must not be damped to a crawl.

Two things went wrong in Build 310 and are checked here on the shipped class:

  * the "Урон" node names its attacker for ten seconds only, so a pursuit that read the node
    every tick lost its target mid-chase and the NPC stood still. The fix latches the body;
    the gate asks for the latch (field {@code latched}, helper {@code latchedBody});
  * GuideNpc#walkingNavigationSpeed carries the auto-AI peace-time damping (x0.55), which made
    a pursuit slower than plain walking. The fix divides that factor out, so the speed path
    must reference {@code navigationSpeedFactor}.

    python3 tools/pursuit_check.py settledlands-0.8.0-build311-pursuit-fix.jar

Exit 0 = both fixes are in the shipped class.
"""
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

TARGET = "dev/settledlands/npc/NpcPursuit269.class"
STATE = "dev/settledlands/npc/NpcPursuit269$State.class"
INVOKES = (0xb6, 0xb7, 0xb8, 0xb9)


def method_refs(cp, info):
    out = set()
    for _acc, _ni, _di, attrs in info["methods"]:
        for an, ab in attrs:
            if cp[an][1] != "Code":
                continue
            p = 4
            length = struct.unpack(">I", ab[p:p + 4])[0]
            p += 4
            body = ab[p:p + length]
            i = 0
            while i < len(body):
                op = body[i]
                entry = disasm.OPS.get(op)
                if entry is None:
                    return out
                name, size = entry
                if op in INVOKES:
                    idx = struct.unpack(">H", body[i + 1:i + 3])[0]
                    out.add(disasm.ref(cp, idx).rsplit(".", 1)[-1].split("(", 1)[0])
                i += 1 + size
    return out


def field_names(cp, info):
    return {cp[ni][1] for _acc, ni, _di, _attrs in info["fields"]}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("pursuit_check: no such jar %s" % jar)
        return 2
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if TARGET not in names:
            print("pursuit_check: SKIP - this jar has no pursuit class")
            return 0
        main_info = disasm.parse_class(z.read(TARGET))
        state_info = disasm.parse_class(z.read(STATE)) if STATE in names else None

    refs = method_refs(main_info["cp"], main_info)
    problems = []
    if "latchedBody" not in refs:
        problems.append("the pursuit never falls back to a latched body - the damage window "
                        "will still cut the chase after ten seconds")
    if "navigationSpeedFactor" not in refs:
        problems.append("the speed path does not cancel the auto-AI damping - a pursuit walks "
                        "slower than ordinary walking")
    if state_info is None:
        problems.append("%s is gone" % STATE)
    elif "latched" not in field_names(state_info["cp"], state_info):
        problems.append("the pursuit state has no latched field")

    if problems:
        print("pursuit_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("pursuit_check: OK  %s  (body is latched, speed is undamped)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
