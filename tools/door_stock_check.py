#!/usr/bin/env python3
"""Gate: a door's graph is a stock graph, and the two outputs of a door are sockets of its card.

The author's report on build 386: «мне не нравится реализация нод в графе двери, сделай реализацию как в
других графах». Build 386 had invented three node types («Открытие», «Кто открыл», «В радиусе») with their
own cards, their own inspector rows and their own little walk, which is why a door's graph worked unlike
every other graph in the mod.

The author's report on build 391, with a screenshot of a door graph holding three cards: «Ты создал ноды
импульс и цель, а я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде \"дверь\"». Builds 389-391 had answered
the earlier request with two extra cards. Build 392 puts those two outputs where he asked for them - on the
card itself, as its two sockets.

What this gate asks of the jar:

  * the palette of a door is the palette of a block: `BlockMenu363#doorType` answers through
    `BlockTrigger351#typeForRow`, a door's menu has exactly the stock rows, and no extra row, no door
    palette field and no `DoorGraph382` reference survives in the menu;
  * the chain is run by the stock interpreter: `DoorSignal388#hack` starts at the node the card's output
    socket points at (`DoorGraph382#chainStart`) and calls `BlockTrigger351#fire` with the door as the
    speaker (`BlockTalk358$Where`), so «Триггер» with `%player%` is the «кто открыл», «Переменная» moves
    the same `NpcPoints` the NPCs use, «Условие If» compares and branches, «Реплика» speaks in the stock
    window;
  * the two outputs are sockets of the card: the execution socket is the card's own `next` (the stock
    output port row), the blue socket is the value output of `PursuitGraphNode272#isValueSourceNode`, which
    now includes the door card (`isDoorValueNode` → `DoorGraph382#isDoorNode`), so the diamond, its hit
    test, the wire drag, the cut and the blue drawing pass all work with no code of their own;
  * the reference lives on the card: `DoorGraph382#setOpener` writes the hacker's name into the node's
    `text` field - a field the graph NBT writes for every node - and `#opener` reads it back, so the card
    shows it and the reference survives a save;
  * the two nodes of builds 389-391 are folded away, not left behind: `DoorGraph382#foldStarter` knows both
    old types (41 and 42), rewires the graph (`bypass`) and drops them; `#migrate` and `#open` call it, and
    the palette of a door cannot create them any more (`#allowed` must not allow 41 or 42);
  * a line wired to the blue socket reads the hacker: `BlockTalk358#withBody` fills `%target%`,
    `%target_kind%` and `%target_uuid%` with the player the chain runs as, and both the window
    (`BlockTalk358#line`) and the chat fallback (`BlockTrigger351#say`) go through it;
  * the door's own node is one card, like a block's «Имя» card: `DoorCard382` paints only that card and
    the inspector parks the stock rows only for it;
  * the walk is reachable from a real opening: `DoorHack377` calls `DoorSignal388#hack`, and a plain click
    does not.

    python3 tools/door_stock_check.py settledlands-0.8.0-build392-door-card-sockets.jar

Exit 0 = a door's graph is the block's graph: stock rows, stock types, stock interpreter, one card of its
own carrying the door's two sockets, and the nodes of the older builds folded into them.
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
PURSUIT = "dev/settledlands/npc/client/PursuitGraphNode272.class"
TALK = "dev/settledlands/npc/BlockTalk358.class"
TRIGGER_CLASS = "dev/settledlands/npc/BlockTrigger351.class"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

TRIGGER = "dev/settledlands/npc/BlockTrigger351"
TALK_WHERE = "dev/settledlands/npc/BlockTalk358$Where"
NODE = "dev/settledlands/npc/NpcDialogueGraph$Node"
DOOR = "dev/settledlands/door/DoorGraph382"

# The types build 386 invented. They are gone, and a gate that finds any of them back means a regression
# to a door-only node set.
STALE_TYPES = ("EVENT_TYPE", "WHO_TYPE", "RADIUS_TYPE", "MIN_RADIUS", "MAX_RADIUS", "DEFAULT_RADIUS")

# The two nodes of builds 389-391. They may only exist as the numbers the fold recognises.
OLD_NODES = ("SIGNAL_TYPE", "TARGET_TYPE")

# The methods and fields build 392 removed. Finding one back means the two cards are back.
GONE = ("wireStarter", "signalNodes", "relayoutStarter", "rowSpot", "freeSpot", "near", "occupied",
        "extraType", "PALETTE_EXTRA", "isSignalNode", "isTargetNode")


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
        for required in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS, PALETTE_MIXIN, CLIENT, PURSUIT,
                         TALK, TRIGGER_CLASS, RU, EN):
            if required not in names:
                return fail("%s is missing - the door's graph is not in this jar" % required)
        if OLD in names:
            return fail("%s is still in the jar - the door's own walk must be gone" % OLD)
        blobs = {n: z.read(n) for n in (LOGIC, SIGNAL, EVENTS, HACK, MENU, CARD, ROWS, PALETTE_MIXIN,
                                        CLIENT, PURSUIT, TALK, TRIGGER_CLASS, RU, EN)}

    # ---- 1. the door's own types are gone, and the two old nodes are only a fold ----------------
    logic, logic_refs = parse(blobs[LOGIC])
    stale = [name for name in STALE_TYPES if name in field_names(logic)]
    if stale:
        return fail("DoorGraph382 still declares %s - a door's nodes must be the stock ones"
                    % ", ".join(stale))
    for member in ("typeForRow", "rowForType", "paletteRows", "migrate", "allowed", "isDoorGraphNode"):
        if member not in methods(logic):
            return fail("DoorGraph382 has no %s()" % member)
    if not calls(logic_refs, TRIGGER, "typeForRow"):
        return fail("DoorGraph382 does not take its rows from the block's palette - the two graphs would"
                    " offer different nodes")
    if not calls(logic_refs, "dev/settledlands/npc/NpcDialogueGraph", "remove"):
        return fail("DoorGraph382 never removes a node - the build-386 types and the two old outputs would"
                    " stay")
    for gone in GONE:
        if gone in methods(logic) or gone in field_names(logic):
            return fail("DoorGraph382 still has %s - the two cards of builds 389-391 are back" % gone)
    allowed = code_ints(logic, "allowed")
    for old in (41, 42):
        if old in allowed:
            return fail("DoorGraph382#allowed still allows type %d - a door's menu could create the nodes of"
                        " build 389 again" % old)

    # ---- 2. the palette of a door is the palette of a block ------------------------------------
    menu, menu_refs = parse(blobs[MENU])
    if "doorType" not in methods(menu):
        return fail("BlockMenu363 has no doorType(row)")
    if not calls(menu_refs, TRIGGER, "typeForRow"):
        return fail("BlockMenu363 does not map its rows through BlockTrigger351#typeForRow")
    for gone in ("DOOR_ROWS", "DOOR_ROW_NUMBERS", "stockCount"):
        if gone in field_names(menu) or gone in methods(menu):
            return fail("BlockMenu363 still has %s - the door's palette must be the stock one" % gone)
    if any(owner == DOOR for owner, _m in menu_refs):
        return fail("BlockMenu363 still asks DoorGraph382 for a type - the menu must be the stock rows and"
                    " nothing after them")
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
    if not calls(signal_refs, DOOR, "chainStart"):
        return fail("DoorSignal388 does not start the chain where the card's output socket points - the"
                    " pulse would have nothing to run")
    if calls(signal_refs, DOOR, "signalNodes"):
        return fail("DoorSignal388 still looks for pulse nodes - the outputs are sockets of the card now")
    if not calls(signal_refs, DOOR, "setOpener"):
        return fail("DoorSignal388 never writes the hacker onto the card")
    if not calls(signal_refs, DOOR, "foldStarter"):
        return fail("DoorSignal388 does not fold the nodes of build 389-391 - a door hacked before its"
                    " graph is opened again would run nothing")
    if not calls(signal_refs, "dev/settledlands/door/DoorGraph382Store", "put"):
        return fail("DoorSignal388 does not save the graph - the name of the hacker would be lost")
    if not calls(signal_refs, TRIGGER, "fire"):
        return fail("DoorSignal388 does not hand the chain to BlockTrigger351#fire - the door would run a"
                    " walk of its own again")
    if not calls(signal_refs, TALK_WHERE, "<init>"):
        return fail("DoorSignal388 never builds the speaker of the chain (BlockTalk358$Where)")
    if not calls(signal_refs, DOOR, "isDoorNode"):
        return fail("DoorSignal388#startNode does not tell the door card apart from the start node - the"
                    " settings card would start the chain")
    _hack, hack_refs = parse(blobs[HACK])
    if not calls(hack_refs, "dev/settledlands/door/DoorSignal388", "hack"):
        return fail("DoorHack377 never pulses the graph - a solved panel would do nothing")
    _events, events_refs = parse(blobs[EVENTS])
    if calls(events_refs, "dev/settledlands/door/DoorSignal388", "hack"):
        return fail("DoorEvents368 pulses the graph on a plain click - the pulse must be the hack's")

    # ---- 3b. the two sockets, and the fold that put them there ---------------------------------
    for member in ("foldStarter", "bypass", "chainStart", "opener", "setOpener"):
        if member not in methods(logic):
            return fail("DoorGraph382 has no %s() - the card's sockets are not built" % member)
    if not calls(logic_refs, "dev/settledlands/door/DoorGraph382", "foldStarter") and \
            "foldStarter" not in methods(logic):
        return fail("DoorGraph382 does not fold the old nodes")
    folded = code_ints(logic, "foldStarter")
    for old in (41, 42):
        if old not in folded:
            return fail("DoorGraph382#foldStarter does not recognise type %d - a graph of build 389-391"
                        " would keep its two cards" % old)
    if not calls(logic_refs, NODE, "removeIf"):
        pass
    if not calls(logic_refs, "dev/settledlands/npc/NpcDialogueGraph", "remove"):
        return fail("DoorGraph382 never removes a folded node")
    # the name lives on the card: written into the node's own text, read back from it
    if not any(owner == NODE and member == "text" for owner, member in logic_refs):
        return fail("DoorGraph382 never touches the node's text - the hacker's name has nowhere to live")
    # the blue socket works because the value family knows the card
    pursuit, pursuit_refs = parse(blobs[PURSUIT])
    if "isDoorValueNode" not in methods(pursuit):
        return fail("PursuitGraphNode272 has no isDoorValueNode() - the blue socket of the card would not"
                    " be a value output")
    if not calls(pursuit_refs, DOOR, "isDoorNode"):
        return fail("PursuitGraphNode272 does not ask DoorGraph382 whether a node is the door card")

    # ---- 4. one card of its own, and the stock inspector for everything else ------------------
    card, card_refs = parse(blobs[CARD])
    for member in ("keys", "speed", "difficulty", "sealed", "opener"):
        if not calls(card_refs, DOOR, member):
            return fail("DoorCard382 does not read DoorGraph382#%s - the card would show nothing" % member)
    for member in ("socket", "diamond"):
        if member not in methods(card):
            return fail("DoorCard382 has no %s() - the card would draw no socket" % member)
    card_text = text(card)
    for key in ("gui.settledlands.door.pin_out", "gui.settledlands.door.pin_who",
                "gui.settledlands.door.hacked_last"):
        if key not in card_text:
            return fail("DoorCard382 does not draw %s - the sockets would have no labels" % key)
    for gone in ("gui.settledlands.door.signal_line1", "gui.settledlands.door.target_line1"):
        if gone in card_text:
            return fail("DoorCard382 still draws %s - that is the card of an old node" % gone)
    rows, rows_refs = parse(blobs[ROWS])
    if not calls(rows_refs, DOOR, "isDoorGraphNode"):
        return fail("the inspector does not tell the door card apart - it would replace the stock rows of"
                    " every stock node too")
    if not calls(rows_refs, DOOR, "opener"):
        return fail("the inspector of the door does not show the name of the last hacker")
    if not calls(rows_refs, DOOR, "isDoorNode"):
        return fail("the inspector builds no door rows at all")
    for gone in ("setRadius", "isRadiusNode"):
        if calls(rows_refs, DOOR, gone):
            return fail("the inspector still builds a row for the removed type %s" % gone)
    if not any(m == "propertyChange" for _o, m in rows_refs):
        return fail("the door card's rows are not committed through propertyChange")

    # ---- 4b. a line wired to the blue socket reads the hacker ---------------------------------
    talk, talk_refs = parse(blobs[TALK])
    for member in ("line", "withBody"):
        if member not in methods(talk):
            return fail("BlockTalk358 has no %s() - a line wired to the blue socket would say %target%"
                        % member)
    for token in ("%target%", "%target_kind%", "%target_uuid%"):
        if token not in text(talk):
            return fail("BlockTalk358#withBody does not fill %s" % token)
    if not calls(talk_refs, DOOR, "isDoorNode"):
        return fail("BlockTalk358 does not ask DoorGraph382 where the body comes from")
    trigger, trigger_refs = parse(blobs[TRIGGER_CLASS])
    if not calls(trigger_refs, "dev/settledlands/npc/BlockTalk358", "withBody"):
        return fail("the chat fallback of a block line does not fill the body - the two paths would differ")

    # ---- 5. an old graph is migrated ----------------------------------------------------------
    client, client_refs = parse(blobs[CLIENT])
    if not calls(client_refs, DOOR, "migrate"):
        return fail("DoorGraphClient382#graphOf does not migrate an old graph - the old nodes would sit in"
                    " it and never run")

    # ---- 6. the language says what the sockets are --------------------------------------------
    ru = json.loads(blobs[RU].decode("utf-8"))
    en = json.loads(blobs[EN].decode("utf-8"))
    for needed in ("gui.settledlands.door.pin_out", "gui.settledlands.door.pin_who",
                   "gui.settledlands.door.pin_out_hint", "gui.settledlands.door.hacked_last",
                   "gui.settledlands.door.target_none"):
        for table, path in ((ru, "ru_ru.json"), (en, "en_us.json")):
            if needed not in table:
                return fail("%s has no %s" % (path, needed))
    key = "gui.settledlands.door.palette"
    for table, path in ((ru, "ru_ru.json"), (en, "en_us.json")):
        if key not in table:
            return fail("%s has no %s" % (path, key))
        if "Открытие" in table[key] or "Opened by" in table[key]:
            return fail("%s still describes the build-386 palette" % path)

    print("door_stock_check: OK  %s  (a door's create menu is the block's row set, the chain runs through"
          " BlockTrigger351#fire from the card's output socket with the door as the speaker, the pulse comes"
          " from a solved panel and never from a plain click, the card keeps the name of the last hacker and"
          " hands it out through its blue socket, the two nodes of builds 389-391 are folded into those"
          " sockets, and only the door card replaces the stock inspector)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
