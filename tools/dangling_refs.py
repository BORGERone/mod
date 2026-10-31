#!/usr/bin/env python3
"""Gate 5 - no dangling references to members a build removed.

Build 286 replaced PursuitGraphNode272 and dropped its helper methods, but the
mixin class NpcPursuitCanvasMixin272 that was compiled against Build 285 stayed
in the jar and still called PursuitGraphNode272.hit(...). The compiler never saw
it (the mixin was not recompiled), jvm_verify only checks the shape of each class
and check_mixin_class only checks injection points, so the game crashed while
rendering the dialogue screen with NoSuchMethodError.

This gate compares every class that exists in both jars with the previous jar and
looks for methods/fields that disappeared while some class in the new jar still
references them.

Usage: python3 tools/dangling_refs.py <jar> [--base PREVIOUS.jar]
Exit code 0 = no dangling reference.
"""

import argparse
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import classfile
from classfile import ClassFormatError


def members(cf):
    out = set()
    for m in cf.methods:
        out.add(("method", m.name, m.desc))
    for f in cf.fields:
        out.add(("field", f.name, f.desc))
    return out


def load(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not name.endswith(".class"):
                continue
            out[name] = classfile.parse(z.read(name), name)
    return out


def refs(cf):
    """Every (owner, kind, name, desc) this class references through the pool."""
    out = []
    for entry in cf.cp:
        if not entry or entry[0] not in (9, 10, 11):
            continue
        cls_i, nt_i = entry[1]
        try:
            owner = cf.class_name(cls_i)
            nat = cf.cp[nt_i]
            if not nat or nat[0] != 12:
                continue
            name = cf.utf8(nat[1][0])
            desc = cf.utf8(nat[1][1])
        except (ClassFormatError, IndexError, TypeError):
            continue
        kind = "field" if entry[0] == 9 else "method"
        out.append((owner, kind, name, desc))
    return out


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--base", default=None)
    args = ap.parse_args(argv)

    if not args.base:
        print("dangling_refs: SKIP - no --base given")
        return 0
    if not os.path.exists(args.base):
        print("dangling_refs: FAIL - no such base jar: %s" % args.base)
        return 1

    new = load(args.jar)
    old = load(args.base)

    removed = {}
    for name, cf in new.items():
        if name not in old:
            continue
        gone = members(old[name]) - members(cf)
        if gone:
            removed[name[:-len(".class")]] = gone

    if not removed:
        print("dangling_refs: OK  %s" % os.path.basename(args.jar))
        print("  removed members : 0")
        return 0

    dangling = []
    for name, cf in sorted(new.items()):
        for owner, kind, member, desc in refs(cf):
            gone = removed.get(owner)
            if not gone or (kind, member, desc) not in gone:
                continue
            dangling.append((name[:-len(".class")], owner, kind, member, desc))

    print("dangling_refs: %s  %s"
          % ("FAIL" if dangling else "OK", os.path.basename(args.jar)))
    print("  classes with fewer members than the base : %d" % len(removed))
    for owner in sorted(removed):
        seats = sorted("%s %s%s" % (k, n, d) for k, n, d in removed[owner])
        print("    - %s: %s" % (owner, ", ".join(seats)))
    if not dangling:
        print("  dangling references : 0")
        return 0

    print("  dangling references : %d  (NoSuchMethodError/NoSuchFieldError at runtime)"
          % len(dangling))
    for user, owner, kind, member, desc in dangling:
        print("    ! %s still calls %s %s %s%s"
              % (user, owner, kind, member, desc))
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
