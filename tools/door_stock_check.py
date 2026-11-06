#!/usr/bin/env python3
"""Gate: a door's graph is a stock graph - the same rows, the same types, the same interpreter.

The author's report on build 386: «мне не нравится реализация нод в графе двери, сделай реализацию как в
других графах». Build 386 had invented three node types («Открытие», «Кто открыл», «В радиусе») with their
own cards, their own inspector rows and their own little walk, which is why a door's graph worked unlike
every other graph in the mod.

What this gate asks of the jar - the block's answer, build 351, applied to a door:

  * the palette of a door is the palette of a block: `BlockMenu363#doorType` answers through
    `BlockTrigger351#typeForRow`, and no field of a door palette of its own survives;
  * every stock row creates a stock type, so the stock card painter, the stock inspector rows and the
    stock fields come with it - there is nothing left for this mod to draw but the door card;
  * the chain is run by the stock interpreter: `DoorSignal388` starts at the node marked «Начало»
    (`startNode`) and calls `BlockTrigger351#fire` with the door as the speaker
    (`BlockTalk358$Where`), so «Триггер» with `%player%` is the «кто открыл», «Переменная» moves the same
    `NpcPoints` the NPCs use, «Условие If» compares and branches, «Реплика» speaks in the stock window;
  * build 389's two outputs are wired the way the author drew them: `DoorGraph382#wireStarter` creates a
    «Импульс» (41) and a blue «Цель» (42) after the card and links them, `DoorSignal388#hack` - called from
    the solved panel only, never from a plain click - pulses every signal node and stamps the hacker's name
    into every target node, and both are reachable from the end of a door's create menu;
  * the door's own node is one card, like a block's «Имя» card: `DoorGraph382` keeps no type other than
    `DOOR_TYPE`, `DoorCard382` paints only that card, and the inspector parks the stock rows only for it;
  * a graph written by build 386 is migrated instead of kept: `DoorGraph382#migrate` removes nodes and
    `DoorGraphClient382#graphOf` calls it, so a node that cannot run never sits in a graph pretending it
    might;
  * the walk is reachable from a real opening: both `DoorEvents368` and `DoorHack377` call
    `DoorSignal388#fire`.

    python3 tools/door_stock_check.py settledlands-0.8.0-build388-door-stock-nodes.jar

Exit 0 = a door's graph is the block's graph: stock rows, stock types, stock interpreter, one card of its
own, and old invented nodes migrated away.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

LOGIC = "dev/settledlands/door/DoorGraph382.class"
SIGNAL = "dev/settledlands/door/DoorSignal388.class"
OLD = "dev/settledlands/door/DoorGraph386.class"
EVENTS = "dev/settledlands/door/DoorEvents368.class"
HACK = "dev/settledlands/door/DoorHack377.class"
MENU = "dev/settledlands/npc/client/BlockMenu363.class"
CARD = "dev/settledlands/npc/client/DoorCard382.class"
ROWS = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
PALETTE_MIXIN = "dev/settledlands/mixin/NpcDoorGraph382Mixin.class"
CLIENT = "dev/settledlands/npc/client/DoorGraphClient382.class"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

TRIGGER = "dev/settledlands/npc/BlockTrigger351"
TALK = "dev/settledlands/npc/BlockTalk358$Where"

# The types build 386 invented. They are gone, and a gate that finds any of them back means a regression
# to a door-only node set.
STALE_TYPES = ("EVENT_TYPE", "WHO_TYPE", "RADIUS_TYPE", "MIN_RADIUS", "MAX_RADIUS", "DEFAULT_RADIUS")


def fail(message):
    print("FAIL: " + message)
    return 1


def parse(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    refs = {}
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs[(owner, name)] = desc
    return parsed, refs


def text(parsed):
    return " ".join(str(v[1]) for v in parsed["cp"] if v and v[0] == 1)


def methods(parsed):
    cp = parsed["cp"]
    return {cp[name_idx][1] for _a, name_idx, _d, _attrs in parsed["methods"]}


def field_names(parsed):
    cp = parsed["cp"]
    return {cp[ni][1] for _a, ni, _d, _attrs in parsed["fields"] if cp[ni] and cp[ni][0] == 1}


def calls(refs, owner, member):
    return any(o == owner and m == member for o, m in refs)


def code_ints(parsed, name):
    """The int constants a method pushes - the only way to read a `static final int` that was inlined."""
    cp = parsed["cp"]
    for _acc, name_idx, _desc, attrs in parsed["methods"]:
        if cp[name_idx][1] != name:
            continue
        co = disasm.code_of(attrs, cp)
        if co is None:
            return set()
        listing, _labels = disasm.disasm(co["code"], cp)
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
                if tail.isdigit():
                    out.add(int(tail))
                elif tail == "m1":
                    out.add(-1)
        return out
    return set()


def main(argv):
    if len(argv) != 1:
        return fail("usage: door_stock_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for required in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS, PALETTE_MIXIN, CLIENT, RU, EN):
            if required not in names:
                return fail("%s is missing - build 388 is not in this jar" % required)
        if OLD in names:
            return fail("%s is still in the jar - the door's own walk must be gone" % OLD)
        blobs = {n: z.read(n) for n in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS,
                                        PALETTE_MIXIN, CLIENT, RU, EN)}

    # ---- 1. the door's own types are gone ------------------------------------------------------
    logic, logic_refs = parse(blobs[LOGIC])
    stale = [name for name in STALE_TYPES if name in field_names(logic)]
    if stale:
        return fail("DoorGraph382 still declares %s - a door's nodes must be the stock ones"
                    % ", ".join(stale))
    for member in ("typeForRow", "rowForType", "paletteRows", "migrate"):
        if member not in methods(logic):
            return fail("DoorGraph382 has no %s()" % member)
    if not calls(logic_refs, TRIGGER, "typeForRow"):
        return fail("DoorGraph382 does not take its rows from the block's palette - the two graphs would"
                    " offer different nodes")
    if not calls(logic_refs, "dev/settledlands/npc/NpcDialogueGraph", "remove"):
        return fail("DoorGraph382#migrate never removes a node - the build-386 types would stay")

    # ---- 2. the palette of a door is the palette of a block ------------------------------------
    menu, menu_refs = parse(blobs[MENU])
    if "doorType" not in methods(menu):
        return fail("BlockMenu363 has no doorType(row)")
    if not calls(menu_refs, TRIGGER, "typeForRow"):
        return fail("BlockMenu363 does not map its rows through BlockTrigger351#typeForRow")
    for gone in ("DOOR_ROWS", "DOOR_ROW_NUMBERS"):
        if gone in field_names(menu):
            return fail("BlockMenu363 still has %s - the door's palette must be the stock one" % gone)
    mixin, mixin_refs = parse(blobs[PALETTE_MIXIN])
    if not calls(mixin_refs, "dev/settledlands/npc/client/BlockMenu363", "doorType"):
        return fail("the palette click does not ask the menu which type the row is")
    if not any(m == "added_node" for _o, m in mixin_refs) and "door.added_node" not in text(mixin):
        return fail("the palette click says nothing when a node is created")

    # ---- 3. the chain runs through the stock interpreter --------------------------------------
    signal, signal_refs = parse(blobs[SIGNAL])
    for member in ("hack", "startNode"):
        if member not in methods(signal):
            return fail("DoorSignal388 has no %s()" % member)
    if not calls(signal_refs, "dev/settledlands/door/DoorGraph382", "signalNodes"):
        return fail("DoorSignal388 does not look for the pulse nodes - nothing would fire the graph")
    if not calls(signal_refs, "dev/settledlands/door/DoorGraph382", "setOpener"):
        return fail("DoorSignal388 never stamps the hacker into the blue target node")
    if not calls(signal_refs, "dev/settledlands/door/DoorGraph382Store", "put"):
        return fail("DoorSignal388 does not save the graph - the name of the hacker would be lost")
    if not calls(signal_refs, TRIGGER, "fire"):
        return fail("DoorSignal388 does not hand the chain to BlockTrigger351#fire - the door would run a"
                    " walk of its own again")
    if not calls(signal_refs, TALK, "<init>"):
        return fail("DoorSignal388 never builds the speaker of the chain (BlockTalk358$Where)")
    if 40 not in code_ints(signal, "startNode") and not calls(
            signal_refs, "dev/settledlands/door/DoorGraph382", "isDoorNode"):
        return fail("DoorSignal388#startNode does not tell the door card apart from the start node - the"
                    " settings card would start the chain")
    _hack, hack_refs = parse(blobs[HACK])
    if not calls(hack_refs, "dev/settledlands/door/DoorSignal388", "hack"):
        return fail("DoorHack377 never pulses the graph - a solved panel would do nothing")
    _events, events_refs = parse(blobs[EVENTS])
    if calls(events_refs, "dev/settledlands/door/DoorSignal388", "hack"):
        return fail("DoorEvents368 pulses the graph on a plain click - the pulse must be the hack's")

    # ---- 3b. the two outputs of the card ------------------------------------------------------
    for member in ("wireStarter", "signalNodes", "setOpener", "extraType"):
        if member not in methods(logic):
            return fail("DoorGraph382 has no %s() - the two outputs of the card are not built" % member)
    wired = code_ints(logic, "wireStarter")
    for wanted in (41, 42):
        if wanted not in wired:
            return fail("DoorGraph382#wireStarter does not create type %d - a fresh door would have no"
                        " signal and no target" % wanted)
    if not calls(menu_refs, "dev/settledlands/door/DoorGraph382", "extraType"):
        return fail("BlockMenu363 does not offer the two nodes of the door at the end of its menu")

    # ---- 4. one card of its own, and the stock inspector for everything else ------------------
    card, card_refs = parse(blobs[CARD])
    for member in ("keys", "speed", "difficulty", "sealed"):
        if not calls(card_refs, "dev/settledlands/door/DoorGraph382", member):
            return fail("DoorCard382 does not read DoorGraph382#%s - the card would show nothing" % member)
    for text_part in ("gui.settledlands.door.target_line1", "gui.settledlands.door.signal_line1"):
        if text_part not in text(card):
            return fail("DoorCard382 does not draw %s" % text_part)
    rows, rows_refs = parse(blobs[ROWS])
    if not calls(rows_refs, "dev/settledlands/door/DoorGraph382", "isDoorGraphNode"):
        return fail("the inspector does not tell the door card apart - it would replace the stock rows of"
                    " every stock node too")
    if not calls(rows_refs, "dev/settledlands/door/DoorGraph382", "opener"):
        return fail("the inspector of the blue target does not show the name of the hacker")
    for gone in ("setRadius", "isRadiusNode"):
        if calls(rows_refs, "dev/settledlands/door/DoorGraph382", gone):
            return fail("the inspector still builds a row for the removed type %s" % gone)
    if not any(m == "propertyChange" for _o, m in rows_refs):
        return fail("the door card's rows are not committed through propertyChange")

    # ---- 5. an old graph is migrated ----------------------------------------------------------
    client, client_refs = parse(blobs[CLIENT])
    if not calls(client_refs, "dev/settledlands/door/DoorGraph382", "migrate"):
        return fail("DoorGraphClient382#graphOf does not migrate a build-386 graph - the old nodes would"
                    " sit in it and never run")

    # ---- 6. the palette note says what a door offers ------------------------------------------
    ru = json.loads(blobs[RU].decode("utf-8"))
    en = json.loads(blobs[EN].decode("utf-8"))
    for needed in ("gui.settledlands.door.signal", "gui.settledlands.door.target",
                   "gui.settledlands.door.target_last", "gui.settledlands.door.target_none",
                   "gui.settledlands.door.signal_line1", "gui.settledlands.door.target_line3"):
        for table, path in ((ru, "ru_ru.json"), (en, "en_us.json")):
            if needed not in table:
                return fail("%s has no %s" % (path, needed))
    key = "gui.settledlands.door.palette"
    for table, path in ((ru, "ru_ru.json"), (en, "en_us.json")):
        if key not in table:
            return fail("%s has no %s" % (path, key))
        if "Открытие" in table[key] or "Opened by" in table[key]:
            return fail("%s still describes the build-386 palette" % path)

    print("door_stock_check: OK  %s  (a door's create menu is the block's row set plus the door's own two"
          " outputs, the chain runs through BlockTrigger351#fire with the door as the speaker, the pulse"
          " comes from a solved panel and never from a plain click, every blue target is stamped with the"
          " hacker's name and saved, a fresh door is wired card -> pulse -> target, and only the three cards"
          " of this mod replace the stock inspector)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
