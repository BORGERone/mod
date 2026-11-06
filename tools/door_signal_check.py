#!/usr/bin/env python3
"""Gate: a door's graph has four nodes, and the signal of «Открытие» really runs.

Build 386 answers three requests that can each ship looking finished and doing nothing:

  * **20 seconds.** The author asked to raise the panel's clock; a bound that is only painted on the
    slider would still stop the panel at ten. The constant of the store has to be 20.
  * **More nodes.** Four rows in a door's create menu, each one a type the door can run. A row that
    creates nothing, or a click that always creates the door node - the build-382 behaviour - would look
    like four rows and be one.
  * **The signal.** «Открытие» has to fire when the door is really opened, «Кто открыл» has to be filled
    with that player, and «В радиусе» has to be able to stop the chain. And it has to be unable to hurt
    the server: a bounded walk, a guard against loops, and a door that is already open when it runs.

What this gate asks of the jar:

  * `DoorGraph382` carries the four type constants (40, 41, 42, 43), the palette array and the bounds
    1..20 for the clock and 1..32 for the radius, all as `ConstantValue`s;
  * the palette of a door's menu is four rows and the row names the type
    (`BlockMenu363#doorType` over `DoorGraph382#typeForRow`), and a created node gets its defaults;
  * `DoorGraph386#fire` exists, is called from both places where a door really opens (the ordinary click
    of `DoorEvents368` and the solved panel of `DoorHack377`), walks the stock `next` links with a step
    limit and a visited set, writes the opener into the «Кто открыл» node and stops at a radius it does
    not pass;
  * the card painter knows all four types and the inspector builds a row for each - the radius slider
    commits through `propertyChange`, the two signal nodes are read-only lines;
  * every line the four cards and the two read-only panels use exists in both language files.

    python3 tools/door_signal_check.py settledlands-0.8.0-build386-door-nodes.jar

Exit 0 = the clock goes to 20, the palette has four real types, the signal fires on a real opening with
a bounded, loop-safe walk, and every node of the graph is painted and editable.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

LOGIC = "dev/settledlands/door/DoorGraph382.class"
SIGNAL = "dev/settledlands/door/DoorGraph386.class"
EVENTS = "dev/settledlands/door/DoorEvents368.class"
HACK = "dev/settledlands/door/DoorHack377.class"
MENU = "dev/settledlands/npc/client/BlockMenu363.class"
CARD = "dev/settledlands/npc/client/DoorCard382.class"
ROWS = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
PALETTE_MIXIN = "dev/settledlands/mixin/NpcDoorGraph382Mixin.class"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

# The `static final int`s the door graph must carry, and their values.
CONSTANTS = {
    "DOOR_TYPE": 40,
    "EVENT_TYPE": 41,
    "WHO_TYPE": 42,
    "RADIUS_TYPE": 43,
    "MIN_SPEED": 1,
    "MAX_SPEED": 20,
    "MIN_RADIUS": 1,
    "MAX_RADIUS": 32,
}

LANG_KEYS = (
    "gui.settledlands.door.event", "gui.settledlands.door.who", "gui.settledlands.door.radius",
    "gui.settledlands.door.event_line1", "gui.settledlands.door.event_line2",
    "gui.settledlands.door.event_line3", "gui.settledlands.door.event_line4",
    "gui.settledlands.door.who_line1", "gui.settledlands.door.who_line3",
    "gui.settledlands.door.who_none", "gui.settledlands.door.who_last",
    "gui.settledlands.door.radius_line", "gui.settledlands.door.radius_line2",
    "gui.settledlands.door.radius_line3", "gui.settledlands.door.added_node",
)


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


def field_consts(parsed):
    """The int fields that carry a ConstantValue - the only way to read a `static final`."""
    cp = parsed["cp"]
    out = {}
    for _acc, ni, _di, attrs in parsed["fields"]:
        if not cp[ni] or cp[ni][0] != 1:
            continue
        name = cp[ni][1]
        for an, body in attrs:
            if not cp[an] or cp[an][0] != 1 or cp[an][1] != "ConstantValue":
                continue
            if len(body) != 2:
                continue
            idx = struct.unpack(">H", body)[0]
            if cp[idx] and cp[idx][0] == 3:
                out[name] = struct.unpack(">i", cp[idx][1])[0]
    return out


def code_ints(listing):
    out = set()
    tokens = listing.split()
    for i, token in enumerate(tokens):
        if token in ("bipush", "sipush") and i + 1 < len(tokens):
            try:
                out.add(int(tokens[i + 1]))
            except ValueError:
                pass
        elif token.startswith("iconst_"):
            tail = token[len("iconst_"):]
            if tail == "m1":
                out.add(-1)
            elif tail.isdigit():
                out.add(int(tail))
    return out


def code_text(parsed, name):
    cp = parsed["cp"]
    for _acc, name_idx, _desc, attrs in parsed["methods"]:
        if cp[name_idx][1] != name:
            continue
        co = disasm.code_of(attrs, cp)
        if co is None:
            return ""
        listing, _labels = disasm.disasm(co["code"], cp)
        return listing
    return ""


def main(argv):
    if len(argv) != 1:
        return fail("usage: door_signal_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for required in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS, PALETTE_MIXIN, RU, EN):
            if required not in names:
                return fail("%s is missing - build 386 is not in this jar" % required)
        blobs = {n: z.read(n) for n in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS,
                                        PALETTE_MIXIN, RU, EN)}

    # ---- 1. twenty seconds, four types, two bounds --------------------------------------------
    logic, logic_refs = parse(blobs[LOGIC])
    values = field_consts(logic)
    for name, wanted in CONSTANTS.items():
        if name not in values:
            return fail("DoorGraph382 has no constant %s" % name)
        if values[name] != wanted:
            return fail("DoorGraph382.%s is %d, expected %d" % (name, values[name], wanted))

    # ---- 2. the palette has four real rows ------------------------------------------------------
    menu, menu_refs = parse(blobs[MENU])
    if "doorType" not in methods(menu):
        return fail("BlockMenu363 has no doorType(row) - every click would create the door node again")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "typeForRow"
               for o, m, _d in menu_refs):
        return fail("BlockMenu363 does not map its rows through DoorGraph382#typeForRow")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "colour" for o, m, _d in menu_refs):
        return fail("BlockMenu363 does not colour the door's rows by node type")
    palette, palette_refs = parse(blobs[PALETTE_MIXIN])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "applyDefaults"
               for o, m, _d in palette_refs):
        return fail("the palette click does not give a new node the defaults of its type")
    if not any(o == "dev/settledlands/npc/client/BlockMenu363" and m == "doorType"
               for o, m, _d in palette_refs):
        return fail("the palette click does not ask the menu which type the row is")

    # ---- 3. the signal: called from a real opening, bounded, loop-safe ------------------------
    signal, signal_refs = parse(blobs[SIGNAL])
    for member in ("fire", "within"):
        if member not in methods(signal):
            return fail("DoorGraph386 has no %s()" % member)
    for owner, member in (("dev/settledlands/door/DoorEvents368", "fire"),
                          ("dev/settledlands/door/DoorHack377", "fire")):
        other, other_refs = parse(blobs[EVENTS if "Events" in owner else HACK])
        if not any(o == "dev/settledlands/door/DoorGraph386" and m == member
                   for o, m, _d in other_refs):
            return fail("%s never fires the door's signal - a door opened there would be silent"
                        % owner.rsplit("/", 1)[-1])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "eventNode"
               for o, m, _d in signal_refs):
        return fail("DoorGraph386 does not look for the «Открытие» node")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "setOpener"
               for o, m, _d in signal_refs):
        return fail("DoorGraph386 never writes the opener - the blue node would stay empty")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "radius"
               for o, m, _d in signal_refs):
        return fail("DoorGraph386 does not read the radius - the gate would do nothing")
    if not any(o in ("java/util/HashSet", "java/util/Set") and m in ("add", "contains")
               for o, m, _d in signal_refs):
        return fail("DoorGraph386 has no visited set - a loop in a hand-made graph would spin")
    body = code_text(signal, "fire")
    if not body:
        return fail("DoorGraph386#fire has no code")
    if 32 not in code_ints(body):
        return fail("DoorGraph386#fire has no step limit - a long chain would run unbounded")
    if "putfield" in body and "athrow" in body:
        return fail("DoorGraph386#fire throws where it must swallow - the door is already open")

    # ---- 4. every node is painted and editable -------------------------------------------------
    card, card_refs = parse(blobs[CARD])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "labelKey"
               for o, m, _d in card_refs):
        return fail("DoorCard382 does not take the title of a node from its type")
    card_text = text(card)
    for key in ("gui.settledlands.door.event_line1", "gui.settledlands.door.who_last",
                "gui.settledlands.door.radius_line"):
        if key not in card_text:
            return fail("DoorCard382 does not draw %s" % key)
    rows, rows_refs = parse(blobs[ROWS])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "setRadius"
               for o, m, _d in rows_refs):
        return fail("the inspector has no radius slider")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "opener"
               for o, m, _d in rows_refs):
        return fail("the inspector of «Кто открыл» does not show the opener")
    if not any(o == "dev/settledlands/door/DoorGraph382" and m in ("isWhoNode", "isRadiusNode")
               for o, m, _d in rows_refs):
        return fail("the inspector does not tell the door's node types apart")
    if not any(r[1] == "propertyChange" for r in rows_refs):
        return fail("the inspector rows are not committed through propertyChange")

    # ---- 5. every line exists in both languages ------------------------------------------------
    for path in (RU, EN):
        strings = json.loads(blobs[path].decode("utf-8"))
        for key in LANG_KEYS:
            if key not in strings:
                return fail("%s has no %s" % (path.rsplit("/", 1)[-1], key))

    print("door_signal_check: OK  %s  (the clock goes to 20 s, the create menu of a door has four rows"
          " and each row names a real type, «Открытие» fires from both places a door really opens and"
          " walks the wires with a step limit, a visited set and a radius gate, the blue «Кто открыл» is"
          " filled with the opener, and every node of the graph is painted and editable)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
