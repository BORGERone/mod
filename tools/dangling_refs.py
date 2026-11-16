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

STDLIB_METHODS = {
    "java/lang/Object": {
        ("<init>", "()V"), ("equals", "(Ljava/lang/Object;)Z"), ("hashCode", "()I"),
        ("toString", "()Ljava/lang/String;"), ("getClass", "()Ljava/lang/Class;"),
        ("clone", "()Ljava/lang/Object;"), ("notify", "()V"), ("notifyAll", "()V"),
        ("wait", "()V"), ("wait", "(J)V"), ("wait", "(JI)V"),
    },
    "java/lang/Enum": {
        ("name", "()Ljava/lang/String;"), ("ordinal", "()I"),
        ("compareTo", "(Ljava/lang/Enum;)I"), ("compareTo", "(Ljava/lang/Object;)I"),
        ("getDeclaringClass", "()Ljava/lang/Class;"), ("describeConstable", "()Ljava/util/Optional;"),
        ("valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;"),
    },
    "java/lang/Record": {
        ("<init>", "()V"), ("equals", "(Ljava/lang/Object;)Z"),
        ("hashCode", "()I"), ("toString", "()Ljava/lang/String;"),
    },
}

# Pre-existing dead call inside the untouched 0.8.0 base class GuideTomeScreen (never replaced).
HISTORICAL_INTERNAL_SKIPS = {
    ("dev/settledlands/npc/client/GuideTomeScreen",
     "dev/settledlands/npc/GuideNet", "method", "sendToServer",
     "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
}


def unresolved_internal_members(classes):
    """Build 414: every intra-jar Fieldref/Methodref targeting dev/settledlands/* must exist on its owner.

    Builds 407-413 added `boolean walkedTo` to `NpcDoors368$Open` and referenced it from `NpcDoors368`,
    but packaged only `NpcDoors368.class` while leaving build 404's `NpcDoors368$Open.class` in the jar.
    Because `NpcDoors368$Open.class` already existed in the base jar and lost no members relative to the
    base, both earlier checks stayed green while the game threw `NoSuchFieldError: walkedTo` on every door.
    """
    mem_map = {n[:-len(".class")]: members(cf) for n, cf in classes.items()}
    bad = []
    for uname, cf in sorted(classes.items()):
        user = uname[:-len(".class")]
        for owner, kind, name, desc in refs(cf):
            if not owner.startswith(INTERNAL) or owner not in mem_map:
                continue
            item = (user, owner, kind, name, desc)
            if item in HISTORICAL_INTERNAL_SKIPS:
                continue
            queue = [owner]
            seen = set()
            found = False
            external_ancestor = False
            while queue and not found:
                cur = queue.pop(0)
                if cur in seen:
                    continue
                seen.add(cur)
                if cur in mem_map:
                    if (kind, name, desc) in mem_map[cur]:
                        found = True
                        break
                    ccf = classes[cur + ".class"]
                    if "Lorg/spongepowered/asm/mixin/Mixin;" in ccf.annotation_descriptors():
                        external_ancestor = True
                    sup = (ccf.super_class if isinstance(ccf.super_class, str)
                           else ccf.class_name(ccf.super_class))
                    if sup:
                        queue.append(sup)
                    for iface in ccf.interfaces:
                        iname = iface if isinstance(iface, str) else ccf.class_name(iface)
                        if iname:
                            queue.append(iname)
                elif cur in STDLIB_METHODS:
                    if kind == "method" and (name, desc) in STDLIB_METHODS[cur]:
                        found = True
                        break
                    if cur != "java/lang/Object":
                        queue.append("java/lang/Object")
                else:
                    external_ancestor = True
            if not found and not external_ancestor:
                bad.append(item)
    return bad


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

    missing_members = unresolved_internal_members(new)
    if missing_members:
        print("dangling_refs: FAIL  %s" % os.path.basename(args.jar))
        print("  internal members referenced but missing on target class : %d"
              % len(missing_members))
        for user, owner, kind, member, desc in missing_members:
            print("    ! %s refers to missing %s %s.%s:%s (stale inner/helper class in jar?)"
                  % (user, kind, owner, member, desc))
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
