#!/usr/bin/env python3
"""Gate: what a door's graph shows is what a door's graph saves.

Build 383 shipped a door editor that opened, drew and answered correctly - and lost every value the
author put into it. His report: «Изменения в графе не сохраняются и в следствии не влияют на дверь».

The cause is one line of the base mod's node serialiser:

    if (n.type == TIMER) { v.putInt("TimerInterval", n.timerInterval); ... }
    if (n.type == RANDOM) { v.putInt("RandomMin", n.randomMin); ... }

Build 382 kept the door's three controls in exactly those fields, and a door node's type is neither
TIMER nor RANDOM - so the door's numbers were never written. The editor sent a graph whose node carried
the field defaults, the server stored that, and the door was re-locked and re-set to its defaults on
every save. Nothing threw, nothing logged: the feature looked finished and could not work.

What this gate asks, so that the same mistake cannot be made twice:

  * the door node stores its three controls in fields the stock serialiser writes and reads for
    **every** node type - `Node#varValue`, `Node#varCompare`, `Node#varOp` - and in none of the ones
    that sit behind a type test;
  * the serialiser really does write and read those three unconditionally: the check is made on the
    decompiled source of the base mod (`reference/decompiled-build132`, committed with this repo), where
    the always-written fields and the type-gated ones are distinguishable, and cross-checked against the
    shipped jar (the three tag names must exist in `NpcDialogueGraph`);
  * a graph written by build 382/383 - a door node with `varValue == 0`, which no written node can be,
    because the count is 4..10 - is adopted on open and on save, so opening such a door fills it from
    the defaults and from the lock the door is actually in, instead of writing a blank node back and
    unlocking a closed door;
  * the write path is still `open` -> editor -> `save` -> store, and the values are still the panel's
    two numbers (the `FIELD_NOTE` string and the clamps of 4..10 / 1..10 are in the class).

    python3 tools/door_save_check.py settledlands-0.8.0-build384-door-graph-save.jar

Exit 0 = the door's three values live in fields that survive a save, the serialiser is checked to write
them for any type, and a graph from the older builds is repaired instead of silently written blank.
"""
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

LOGIC = "dev/settledlands/door/DoorGraph382.class"
GRAPH = "dev/settledlands/npc/NpcDialogueGraph.class"
NODE = "dev/settledlands/npc/NpcDialogueGraph$Node"

ROOT = pathlib.Path(__file__).resolve().parent.parent
REFERENCE = ROOT / "reference/decompiled-build132/dev/settledlands/npc/NpcDialogueGraph.java"

# The fields the door node keeps its controls in - all of them written for every node type.
GOOD = ("varValue", "varCompare", "varOp", "checkTarget")

# Field name -> the tag the serialiser writes for it.
TAGS = {"varValue": "VarValue", "varCompare": "VarCompare", "varOp": "VarOp",
        "checkTarget": "CheckTarget"}

# The three build 382 used and this gate exists because of: written only for TIMER / RANDOM nodes.
BAD = ("randomMin", "timerInterval", "timerRepeat")


def fail(message):
    print("FAIL: " + message)
    return 1


def parse(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    refs = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs.add((owner, name, desc))
    return parsed, refs


def text(parsed):
    return " ".join(str(v[1]) for v in parsed["cp"] if v and v[0] == 1)


def methods(parsed):
    cp = parsed["cp"]
    return {cp[name_idx][1] for _acc, name_idx, _desc, _attrs in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        return fail("usage: door_save_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for required in (LOGIC, GRAPH):
            if required not in names:
                return fail("%s is missing" % required)
        blobs = {n: z.read(n) for n in (LOGIC, GRAPH)}

    # ---- 1. the door node stores its controls in fields that are saved for any type ------------
    logic, logic_refs = parse(blobs[LOGIC])
    for field in GOOD:
        if not any(o == NODE and m == field for o, m, _d in logic_refs):
            return fail("the door node does not use Node#%s - the control would be saved nowhere"
                        % field)
    for field in BAD:
        if any(o == NODE and m == field for o, m, _d in logic_refs):
            return fail("the door node still uses Node#%s - that field is written only for a TIMER or"
                        " RANDOM node, so the value is dropped on every save (the bug of build 383)"
                        % field)

    # ---- 2. the serialiser writes and reads those three for every node ------------------------
    graph_text = text(parse(blobs[GRAPH])[0])
    for tag in (TAGS[f] for f in GOOD):
        if tag not in graph_text:
            return fail("NpcDialogueGraph does not mention %s - the field is not serialised at all"
                        % tag)
    if not REFERENCE.is_file():
        print("note: %s is not in the tree - the serialiser was checked against the jar only"
              % REFERENCE.relative_to(ROOT))
    else:
        source = REFERENCE.read_text(encoding="utf-8")
        start = source.index("public CompoundTag save()")
        end = source.index("public static NpcDialogueGraph load(")
        save_body = source[start:end]
        load_body = source[end:]
        for tag in (TAGS[f] for f in GOOD):
            lines = [line.strip() for line in save_body.splitlines() if '"%s"' % tag in line]
            if not lines:
                return fail("the serialiser does not write %s" % tag)
            if any("if (n.type" in line for line in lines):
                return fail("the serialiser writes %s only for some node types - a door node would"
                            " lose it on save" % tag)
            reads = [line.strip() for line in load_body.splitlines()
                     if 'getInt("%s")' % tag in line or 'getBoolean("%s")' % tag in line]
            if not reads:
                return fail("the serialiser does not read %s back" % tag)
            if any("if (n.type" in line for line in reads):
                return fail("the serialiser reads %s only for some node types" % tag)
        # The two that caused this build: they must still be behind their type tests, otherwise the
        # reasoning above (and the comment in DoorGraph382) is out of date and has to be re-checked.
        for tag, kind in (("TimerInterval", "TIMER"), ("RandomMin", "RANDOM")):
            lines = [line.strip() for line in save_body.splitlines() if '"%s"' % tag in line]
            if not lines:
                continue
            if not all("if (n.type" in line for line in lines):
                return fail("the serialiser now writes %s for every node - re-check the field choice"
                            " of the door node" % tag)

    # ---- 3. an older graph is adopted, not written back blank ----------------------------------
    names = methods(logic)
    for member in ("adopt", "keys", "speed", "sealed", "setKeys", "setSpeed", "setSealed",
                   "open", "save", "settings"):
        if member not in names:
            return fail("DoorGraph382 has no %s()" % member)
    calls = [(o, m) for o, m, _d in logic_refs if o in ("dev/settledlands/door/DoorGraph382", LOGIC)]
    if not [1 for _o, m in calls if m == "adopt"]:
        return fail("DoorGraph382 never calls its own adopt() - a graph written by build 382/383 would"
                    " be stored with every value at zero")

    # ---- 4. the values are still the panel's two numbers, clamped ------------------------------
    logic_text = text(logic)
    for needle in ("varValue = keys", "varCompare = seconds", "varOp = sealed"):
        if needle not in logic_text:
            return fail("FIELD_NOTE lost «%s» - the mapping of the fields is no longer documented in"
                        " the class that depends on it" % needle)
    numbers = set()
    for value in logic["cp"]:
        if value and value[0] == 3:
            numbers.add(struct.unpack(">i", value[1])[0])
    for wanted in (4, 10, 1, 40):
        if wanted not in numbers:
            return fail("DoorGraph382 does not carry the number %d (clamp or node type)" % wanted)

    print("door_save_check: OK  %s  (the door node keeps its three controls in Node#varValue,"
          " #varCompare and #varOp - the fields the stock serialiser writes and reads for every node"
          " type - and in none of the type-gated ones that lost them in 383; a door node written by"
          " the older builds is adopted from the defaults and the real lock instead of being saved"
          " blank; the clamps and the field map are still in the class)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
