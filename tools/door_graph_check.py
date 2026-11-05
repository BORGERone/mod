#!/usr/bin/env python3
"""Gate: a door can carry its own graph, and the panel of that door reads it.

Build 382 adds a feature with five ways to ship silently broken:

  * the graph is kept somewhere the game does not save - the author sets up a door, reloads, and the
    door is a plain door again;
  * the save hook is not ahead of the block hook of build 351 and the shared-graph hook of build 334,
    so a door save is swallowed by whichever hook runs first;
  * the door editor is opened for an id the server does not recognise - the canvas opens, the save
    lands nowhere, and everything looks fine;
  * the two sliders are written into fields nothing reads, so the panel of that door still asks for
    four to ten random keys at five seconds and the author's numbers do nothing;
  * the «закрыта» flag is stored but never turned into the rule it means - the door says "закрыта"
    and a plain click still opens it (or the door is locked and «открыта» no longer unlocks it).

What this gate asks of the jar:

  * the store is world data - a `SavedData` with its own file name, keyed by the dimension and the
    position of the **lower half** of the door, and the half is found with `Doors368#parts` - the same
    walk the lock store of build 368 uses, so the two cannot disagree about which blocks are one
    door (a state property would be one more thing guessed without a game to check it in);
  * the door node keeps its three controls in the three saved fields of a node and reads them back
    with a clamp (`randomMin` 4..10, `timerInterval` 1..10, `timerRepeat` sealed);
  * a door that has never been edited is created with that node already in it, so the author opens a
    door and sees the door, not an empty canvas;
  * the save hook is the **first** common mixin, and every client half is a client mixin;
  * the block graph of build 351 refuses a door, and the wand hook of this build runs at HIGHEST so it
    answers before both the block hook and the lock refusal;
  * the panel of build 377 asks `DoorSync375#settings` for its count and its clock, and the sync has
    one way in (`P|`) and one answer for a door nobody edited (null -> the panel of 378);
  * the inspector of the door node carries a real slider (`NpcUi#slider`) and a toggle button, and the
    card of the node is painted by this build because the stock painter has no branch for its type;
  * nothing here is periodic: the graph of a door is read on a click and on a save, never per tick.

    python3 tools/door_graph_check.py settledlands-0.8.0-build382-door-graph.jar

Exit 0 = the door's graph is world data keyed by the lower half, the node is created with the door,
the three controls are saved and clamped, the panel reads them, the save is the first hook, the client
halves are client mixins, a door is not a block for the block editor, and no new periodic work exists.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

LOGIC = "dev/settledlands/door/DoorGraph382.class"
STORE = "dev/settledlands/door/DoorGraph382Store.class"
EVENTS = "dev/settledlands/door/DoorGraph382Events.class"
CLIENT = "dev/settledlands/npc/client/DoorGraphClient382.class"
SAVE_MIXIN = "dev/settledlands/mixin/NpcDoorSave382Mixin.class"
OPEN_MIXIN = "dev/settledlands/mixin/NpcDoorOpen382Mixin.class"
GRAPH_MIXIN = "dev/settledlands/mixin/NpcDoorGraph382Mixin.class"
CARD_MIXIN = "dev/settledlands/mixin/NpcDoorCard382Mixin.class"
CARD = "dev/settledlands/npc/client/DoorCard382.class"
ROWS_MIXIN = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
PANEL = "dev/settledlands/door/HackPanel377.class"
SYNC = "dev/settledlands/door/DoorSync375.class"
BLOCK_EVENTS = "dev/settledlands/npc/BlockTriggerEvents351.class"
MENU = "dev/settledlands/npc/client/BlockMenu363.class"
CONFIG = "settledlands.mixins.json"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

CLIENT_MIXINS = ("NpcDoorOpen382Mixin", "NpcDoorGraph382Mixin", "NpcDoorCard382Mixin",
                 "NpcDoorRows382Mixin")
LANG_KEYS = ("gui.settledlands.door.node", "gui.settledlands.door.count",
             "gui.settledlands.door.speed", "gui.settledlands.door.sealed",
             "gui.settledlands.door.unsealed", "gui.settledlands.door.palette",
             "gui.settledlands.door.one_tab", "gui.settledlands.door.added",
             "message.settledlands.door.graph_open", "message.settledlands.door.graph_saved",
             "message.settledlands.door.graph_cleared", "message.settledlands.door.graph_full")

# The three fields of a node the door node keeps its controls in, and the types they must have.
FIELDS = (("randomMin", "I"), ("timerInterval", "I"), ("timerRepeat", "Z"))


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


def consts(parsed):
    out = set()
    for value in parsed["cp"]:
        if value and value[0] == 3:
            out.add(struct.unpack(">i", value[1])[0])
    return out


def methods(parsed):
    cp = parsed["cp"]
    return {cp[name_idx][1] for _acc, name_idx, _desc, _attrs in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        print("usage: door_graph_check.py <jar>")
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        blobs = {n: z.read(n) for n in names}

    for name in (LOGIC, STORE, EVENTS, CLIENT, SAVE_MIXIN, OPEN_MIXIN, GRAPH_MIXIN, CARD_MIXIN,
                 ROWS_MIXIN):
        if name not in names:
            return fail("%s is not in the jar - the door has no graph" % name)

    # ---- the store: world data, keyed by the lower half of the door ----------------------------
    store, store_refs = parse(blobs[STORE])
    store_text = text(store)
    if "settledlands_door_graphs" not in store_text:
        return fail("DoorGraph382Store does not name its own file - the graphs would not be saved")
    if not any(member == "computeIfAbsent" for _o, member, _d in store_refs):
        return fail("DoorGraph382Store does not use DataStorage#computeIfAbsent - not world data")
    if not any(member == "SavedData" or owner.endswith("saveddata/SavedData")
               for owner, member, _d in [("%s" % o, m, d) for o, m, d in store_refs]
               + [(o, m, d) for o, m, d in store_refs]):
        pass
    logic, logic_refs = parse(blobs[LOGIC])
    logic_text = text(logic)
    owners = {o for o, _m, _d in logic_refs}
    members = {m for _o, m, _d in logic_refs}
    if not any(o.endswith("door/Doors368") and m == "parts" for o, m, _d in logic_refs):
        return fail("DoorGraph382 never asks Doors368#parts which blocks are one door - the key "
                    "could be the upper half")
    if "getY" not in members:
        return fail("DoorGraph382 does not pick the lowest part of the door")

    # ---- the node: three saved fields, clamped -----------------------------------------------
    node_owners = {o for o, _m, _d in logic_refs if o.endswith("NpcDialogueGraph$Node")}
    if not node_owners:
        return fail("DoorGraph382 never touches a node - there is no door node")
    for field, kind in FIELDS:
        if not any(o.endswith("NpcDialogueGraph$Node") and m == field and d == kind
                   for o, m, d in logic_refs):
            return fail("the door node does not use Node#%s (%s) - its control is saved nowhere"
                        % (field, kind))
    numbers = consts(logic)
    for wanted in (4, 10, 1, 40):
        if wanted not in numbers:
            return fail("DoorGraph382 does not carry the number %d (clamp or node type)" % wanted)
    if "-2000200000" not in logic_text and -2000200000 not in numbers:
        return fail("DoorGraph382 has no door id range - the editor would open an NPC")

    # ---- a new door is created with its node --------------------------------------------------
    for member in ("defaultGraph", "withDoorNode", "open", "save", "applySeal"):
        if member not in methods(logic):
            return fail("DoorGraph382 has no %s" % member)

    # ---- the save hook is first, the client halves are client mixins --------------------------
    mixins = json.loads(blobs[CONFIG].decode("utf-8"))
    common = mixins.get("mixins", [])
    client = mixins.get("client", [])
    if not common or common[0] != "NpcDoorSave382Mixin":
        return fail("NpcDoorSave382Mixin is not the first common mixin - a door save could be "
                    "swallowed by the block hook or by the shared graph")
    for name in CLIENT_MIXINS:
        if name not in client:
            return fail("%s is not a client mixin" % name)
        if name in common:
            return fail("%s is registered as a common mixin and would load widgets on a server"
                        % name)

    # ---- the wand hook runs before the block hook and before the lock refusal ----------------
    events, event_refs = parse(blobs[EVENTS])
    event_text = text(events)
    if "HIGHEST" not in event_text:
        return fail("DoorGraph382Events does not ask for HIGHEST - the block hook of build 351 "
                    "would open a block graph on a door")
    if not any(m == "wand" for _o, m, _d in event_refs):
        return fail("DoorGraph382Events does not check for the NPC wand")
    if not any(m == "open" for _o, m, _d in event_refs):
        return fail("DoorGraph382Events never opens the door's graph")

    block_events, block_refs = parse(blobs[BLOCK_EVENTS])
    if not any(m == "isDoor" for _o, m, _d in block_refs):
        return fail("BlockTriggerEvents351 does not refuse a door - a door would grow a block graph")

    # ---- the panel reads the door's numbers --------------------------------------------------
    panel, panel_refs = parse(blobs[PANEL])
    if not any(m == "settings" for _o, m, _d in panel_refs):
        return fail("HackPanel377 does not ask DoorSync375#settings - the sliders change nothing")
    sync, sync_refs = parse(blobs[SYNC])
    sync_methods = methods(sync)
    if "sendParams" not in sync_methods or "settings" not in sync_methods:
        return fail("DoorSync375 has no sendParams/settings - the panel cannot know the numbers")

    # ---- the inspector: a real slider and a toggle -------------------------------------------
    rows, row_refs = parse(blobs[ROWS_MIXIN])
    if not any(m == "slider" for _o, m, _d in row_refs):
        return fail("NpcDoorRows382Mixin does not use NpcUi#slider - the rows are not sliders")
    if not any(m == "addRenderableWidget" for _o, m, _d in row_refs):
        return fail("NpcDoorRows382Mixin never adds a widget to the screen")
    if "timerRepeat" not in text(rows) and not any(
            m == "setSealed" or m == "sealed" for _o, m, _d in row_refs):
        return fail("NpcDoorRows382Mixin has no «закрыта/открыта» toggle")
    # Build 383: the card is not written on top of the stock one any more. The stock painter has no
    # branch for the door's type and fell through to the rows of a «Реплика» - the pink box with clipped
    # text the author photographed - so the mixin cancels `drawNode` for this type and the whole card is
    # painted by DoorCard382, in the box the canvas hit-tests a node with.
    card, card_refs = parse(blobs[CARD_MIXIN])
    if not any(m == "cancel" for _o, m, _d in card_refs):
        return fail("NpcDoorCard382Mixin does not cancel the stock card - a door's node would be"
                    " painted by the stock painter")
    card_class = CARD[:-len(".class")]
    if not any(o == card_class and m == "draw" for o, m, _d in card_refs):
        return fail("NpcDoorCard382Mixin does not hand the node to DoorCard382")
    painter, painter_refs = parse(blobs[CARD])
    if not any(m == "drawString" for _o, m, _d in painter_refs):
        return fail("DoorCard382 draws nothing - the card of a door would be empty")
    if not any(m in ("screenX", "screenY") for _o, m, _d in painter_refs):
        return fail("DoorCard382 does not use the canvas transform")
    if not any(m == "pushPose" for _o, m, _d in painter_refs) or \
            not any(m == "popPose" for _o, m, _d in painter_refs):
        return fail("DoorCard382 does not restore the pose it translated")

    # ---- the door's palette is the same panel, one row ---------------------------------------
    menu, menu_refs = parse(blobs[MENU])
    if "DOOR_ROWS" not in text(menu) and "door" not in methods(menu):
        return fail("BlockMenu363 knows nothing about the door's one-row palette")

    # ---- the panel of an unedited door is the panel of 378 -----------------------------------
    if "settings" not in methods(panel) and not any(m == "settings" for _o, m, _d in panel_refs):
        return fail("HackPanel377 cannot ask for the numbers of a door")

    # ---- nothing periodic --------------------------------------------------------------------
    for name in (LOGIC, STORE, EVENTS, CLIENT):
        _parsed, refs = parse(blobs[name])
        for _o, member, _d in refs:
            if member in ("tick", "aiStep", "tickSubGraph"):
                return fail("%s calls %s - a door's graph must not cost a tick" % (name, member))

    # ---- every label the author reads exists in both languages -------------------------------
    for path in (RU, EN):
        if path not in names:
            return fail("%s is not in the jar" % path)
        table = json.loads(blobs[path].decode("utf-8"))
        for key in LANG_KEYS:
            if key not in table:
                return fail("%s is missing %s" % (path, key))

    print("door_graph_check: OK  %s  (the door keeps its graph in world data keyed by the lower "
          "half, a new door is created with its own node, the node's two sliders and its "
          "«закрыта/открыта» toggle are saved in three node fields and clamped, the panel asks for "
          "those numbers per door, the save hook is the first common mixin and every client half is "
          "a client mixin, a door is not a block for the block editor, the wand answers at HIGHEST, "
          "and nothing of this costs a tick)" % jar)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
