#!/usr/bin/env python3
"""Gate 5 - no dangling references to members a build removed.

Build 286 replaced PursuitGraphNode272 and dropped its helper methods, but the
mixin class NpcPursuitCanvasMixin272 that was compiled against Build 285 stayed
in the jar and still called PursuitGraphNode272.hit(...). The compiler never saw
it (the mixin was not recompiled), jvm_verify only checks the shape of each class
and check_mixin_class only checks injection points, so the game crashed while
rendering the dialogue screen with NoSuchMethodError.

Build 352 shipped a jar without `BlockTriggerStore351$Entry.class`: the nested class was compiled,
the build script copied the outer class only, and every gate stayed green - `jvm_verify` checks the
classes that are present, `check_mixin_class` checks injection points, and the base comparison below
only looks at classes that exist in both jars. The game died in `Class.getDeclaredMethods0` with
`NoClassDefFoundError`. So this gate also checks that every class the mod's own code refers to is
actually in the jar.

This gate compares every class that exists in both jars with the previous jar and
looks for methods/fields that disappeared while some class in the new jar still
references them - and, independently of the base, that nothing inside the jar
points at one of the mod's own classes that the jar does not contain.

Usage: python3 tools/dangling_refs.py <jar> [--base PREVIOUS.jar]
Exit code 0 = no dangling reference.
"""

import argparse
import os
import re
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


INTERNAL = "dev/settledlands/"

# A type can reach a class two ways: a CONSTANT_Class entry the code actually uses, and a method or
# field *descriptor*. The JVM resolves both when the class is linked, and `getDeclaredMethods` alone
# is enough to walk every descriptor - which is how build 352 died. So both are checked.
DESCRIPTOR = re.compile(r"L(dev/settledlands/[A-Za-z0-9_$/]+)[;<]")


def missing_internal(files, classes):
    """(user, missing) for every reference to a dev/settledlands class the jar does not carry."""
    out = []
    for name, cf in sorted(classes.items()):
        seen = set()
        for index, entry in enumerate(cf.cp):
            if not entry:
                continue
            if entry[0] == 7:
                try:
                    seen.add(cf.utf8(entry[1]))
                except (ClassFormatError, IndexError, TypeError):
                    continue
            elif entry[0] == 1:
                try:
                    text = cf.utf8(index)
                except (ClassFormatError, IndexError, TypeError):
                    continue
                seen.update(m.group(1) for m in DESCRIPTOR.finditer(text))
        for target in sorted(seen):
            if not target.startswith(INTERNAL):
                continue
            if target + ".class" not in files:
                out.append((name[:-len(".class")], target))
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

    with zipfile.ZipFile(args.jar) as z:
        files = set(n for n in z.namelist() if n.endswith(".class"))

    new = load(args.jar)
    old = load(args.base)

    absent = missing_internal(files, new)
    if absent:
        print("dangling_refs: FAIL  %s" % os.path.basename(args.jar))
        print("  classes the jar refers to but does not carry : %d" % len(absent))
        for user, target in absent:
            print("    ! %s refers to %s (no such class in the jar)" % (user, target))
        return 1

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
