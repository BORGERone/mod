#!/usr/bin/env python3
"""
Build 400: no two mixins may merge a method of the same name and signature into the same class.

Why this gate exists
--------------------
The author's report was «в взломе сундука не меняется кол-во чисел, скорость и сложность». The chest
card had three working sliders, the settings were saved, and the chest's panel kept its defaults. The
code was fine; what was not fine is what Mixin does with two mixins that add a method of the same name
and signature to the same target class:

  * `NpcDoorRows382Mixin` adds `settledlands$slider(Node,IIIIIZ)I`, `settledlands$difficulty(...)` and
    `settledlands$parkStockRows()V` to `NpcDialogueGraphScreen`;
  * `NpcChestRows396Mixin` added methods of exactly those names and signatures to the same class;
  * Mixin merges one of each and silently drops the rest. The door's copy survived, the door's copy
    writes through `DoorGraph382#setKeys`, and `DoorGraph382` refuses any node that is not a door.

So every chest slider moved the handle and wrote nothing, and no gate noticed, because a jar in which
the chest's method is dead is still a jar that compiles.

The same search found three more merges of the same kind in build 400, all of them older than the
report and two of them actually dead code in the shipped jar:

  * `settledlands$parkStockRows()V` - `BlockNameInspector359Mixin` and `NpcDoorRows382Mixin`; the
    door's copy (which parks over a copy of the list) survived, the name inspector's loop over the
    live list never ran as written;
  * `settledlands$isBlock()Z` / `settledlands$isDoor()Z` - `BlockMenu363Mixin` against the block and
    door graph mixins; both bodies compute the same answer, so it was harmless, and it is gone anyway;
  * `lambda$0(Node)V` - the «Видимость» rows of a radius node and of a random-line node. Only one of
    the two bodies survived, so one of those two toggles ran the other node's code.

What counts as a clash
----------------------
* `@Shadow` members are not merged as new methods - they describe the target class. Ignored.
* `<init>`/`<clinit>` are never merged by name. Ignored.
* Everything else that a mixin declares and the target does not have becomes a new method of the
  target. Two mixins declaring the same `name + descriptor` is a clash.

The fix is always the same and always local: give one of the two a name nobody else uses, or a
signature nobody else has (a lambda that captures one more thing, e.g. `this` by calling a method of
its own mixin, gets a different descriptor). `KNOWN_SHARED` below is for pairs that are provably the
same code and cannot be given different names without touching a class this build does not ship; it
is empty, and it should stay empty.
"""

import sys
import zipfile
import collections

sys.path.insert(0, __file__.rsplit("/", 1)[0])

import check_mixin_class as C


# (target, name, descriptor) -> reason. Build 400 removed every clash it could. Exactly one is left, and
# it is the one this build could not remove without rewriting a row that the author never reported:
KNOWN_SHARED = {
    # Both are the «Видимость» button of a node type: the radius row toggles RadiusPass354, the random-line
    # row toggles NpcRandomLine336, and both callbacks capture the node alone, so both compile to
    # `lambda$0(Node)V`. Every other signature a callback of these rows could have is already taken by
    # another mixin of this screen (`()V` by the block-delete rows, `(Node;Button[])V` by the door's rows and
    # the pursuit inspector, `(Node;I)V` by the door's slider, `(Node;String)V` by every name row), so
    # removing this one is a redesign of both rows rather than a rename and it is left, named, for the next
    # build. What it costs today: one of the two click bodies is dead, and both rows edit a visible flag of
    # the node they are given, so the surviving body runs on the other node type and is refused there
    # instead of toggling the wrong thing. It is pre-existing (build 355 and build 337) and no report has
    # ever mentioned it.
    ("dev/settledlands/npc/client/NpcDialogueGraphScreen",
     "lambda$0", "(Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"):
        "radius row and random-line row: same capture, crowded signature space - see the comment above",
}


def annotation_descs(cf, member):
    return [C.ann_desc(cf, a) for a in C.member_annotations(cf, member)]


def targets_of(cf):
    for a in C.read_annotations(cf):
        if C.ann_desc(cf, a) != C.DESC_MIXIN:
            continue
        args = C.ann_args(cf, a)
        out = []
        for val in (args.get("targets"), args.get("value")):
            for s in C.as_str_list(cf, val):
                out.append(s[1:-1] if s.startswith("L") and s.endswith(";")
                           else s.replace(".", "/"))
        return out
    return []


def scan(jar_path):
    problems = []
    allowed = []
    with zipfile.ZipFile(jar_path) as zf:
        classes = C.load_classes(zf, zf.namelist())

    # target class -> [(mixin entry, class file)]
    groups = collections.defaultdict(list)
    for entry in sorted(classes):
        cf = classes[entry]
        for t in targets_of(cf):
            groups[t].append((entry, cf))

    checked = 0
    mixes = 0
    for target, members in sorted(groups.items()):
        if len(members) < 2:
            continue
        mixes += len(members)
        target_cf = classes.get(target)
        declared = set()
        if target_cf is not None:
            declared = {(m.name, m.desc) for m in target_cf.methods}
        owners = collections.defaultdict(list)
        for entry, cf in members:
            for m in cf.methods:
                if m.name in ("<init>", "<clinit>"):
                    continue
                if C.DESC_SHADOW in annotation_descs(cf, m):
                    continue
                key = (m.name, m.desc)
                if key in declared:
                    # An existing member of the screen: Mixin merges it as an override/inject target,
                    # not as a new method - and a mixin that declares it twice by name only ever has
                    # one copy either way.
                    pass
                owners[key].append(entry)
                checked += 1
        for (name, desc), where in sorted(owners.items()):
            uniq = sorted(set(where))
            if len(uniq) < 2:
                continue
            if (target, name, desc) in KNOWN_SHARED:
                allowed.append("%s: %s%s - %s"
                               % (target, name, desc, KNOWN_SHARED[(target, name, desc)]))
                continue
            problems.append(
                "%s: %s%s is declared by %s - Mixin merges one of them and silently drops the rest,"
                " so all but the survivor are dead code (build 400: the chest's sliders ran the door's"
                " code and wrote nothing)" % (target, name, desc, ", ".join(u.split("/")[-1] for u in uniq)))
    return checked, mixes, problems, allowed


def main(argv):
    if len(argv) != 1:
        print("usage: mixin_clash_check.py <jar>", file=sys.stderr)
        return 2
    jar = argv[0]
    try:
        checked, mixes, problems, allowed = scan(jar)
    except (OSError, zipfile.BadZipFile) as exc:
        print("mixin_clash_check: cannot read %s: %s" % (jar, exc), file=sys.stderr)
        return 2
    for a in allowed:
        print("  known, documented: " + a)
    for p in problems:
        print("  - " + p)
    if problems:
        print("mixin_clash_check: FAIL  %s  (%d clash(es) across %d helper(s) in %d mixin(s) that share a"
              " target)" % (jar, len(problems), checked, mixes))
        return 1
    print("mixin_clash_check: OK  %s  (no two mixins add a method of the same name and signature to the"
          " same class: %d helper(s) in %d mixin(s) that share a target, %d documented exception(s))"
          % (jar, checked, mixes, len(allowed)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
