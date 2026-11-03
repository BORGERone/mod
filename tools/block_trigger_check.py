#!/usr/bin/env python3
"""Gate: a block can carry a graph that the editor edits and the server runs.

Build 351 adds a feature with four ways to ship silently broken:

  * the graphs are kept in a place the game never saves - the author configures a block, reloads, and
    the graph is gone;
  * the save hook is not ahead of the shared-graph hook, so a block save is swallowed by "Общее";
  * the block editor is opened for an id the server does not recognise - the palette opens, the save
    lands nowhere, and everything looks fine;
  * the palette offers nodes a block has no way to run (no body, no tick, no damage event), so the
    author builds a chain that quietly does half of what it says;
  * the client half is registered as a common mixin (the server would load widgets) or the common half
    as a client mixin (a dedicated server would never run the save).

    python3 tools/block_trigger_check.py settledlands-0.8.0-build351-block-trigger.jar

Exit 0 = the store is world data, the hooks are in the right lists, the palette is the four nodes a
block can run, and the chain walker is bounded.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

STORE = "dev/settledlands/npc/BlockTriggerStore351.class"
LOGIC = "dev/settledlands/npc/BlockTrigger351.class"
EVENTS = "dev/settledlands/npc/BlockTriggerEvents351.class"
CLIENT = "dev/settledlands/npc/client/BlockTriggerClient351.class"
OPEN_MIXIN = "dev/settledlands/mixin/NpcBlockOpen351Mixin.class"
GRAPH_MIXIN = "dev/settledlands/mixin/NpcBlockGraph351Mixin.class"
SAVE_MIXIN = "dev/settledlands/mixin/NpcBlockSave351Mixin.class"
CONFIG = "settledlands.mixins.json"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

SHARED_GRAPH_MIXIN = "NpcCommonGraphMixin334"
LANG_KEYS = ("message.settledlands.block.open", "message.settledlands.block.saved",
             "message.settledlands.block.full", "gui.settledlands.block.palette",
             "gui.settledlands.block.one_tab")


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


def palette_rows(parsed):
    """The switch of the palette hook: (low, high, default offset, {row: offset})."""
    cp = parsed["cp"]
    for _acc, name_idx, _desc, attrs in parsed["methods"]:
        if cp[name_idx][1] != "settledlands$paletteType":
            continue
        code = disasm.code_of(attrs, cp)
        if not code:
            return None
        raw = code["code"]
        at = raw.find(b"\xab")                 # lookupswitch, what ecj emits for a small map
        table = at >= 0
        if not table:
            at = raw.find(b"\xaa")             # tableswitch, if a compiler ever emits one
        if at < 0:
            return None
        pad = (4 - (at + 1) % 4) % 4
        base = at + 1 + pad
        if base + 8 > len(raw):
            return None
        default = struct.unpack(">i", raw[base:base + 4])[0]
        if table:
            count = struct.unpack(">i", raw[base + 4:base + 8])[0]
            targets = {}
            for index in range(count):
                off = base + 8 + index * 8
                if off + 8 > len(raw):
                    break
                match, target = struct.unpack(">ii", raw[off:off + 8])
                targets[match] = target
            return min(targets), max(targets), default, targets
        low, high = struct.unpack(">ii", raw[base + 4:base + 12])
        targets = {}
        for index in range(high - low + 1):
            off = base + 12 + index * 4
            if off + 4 > len(raw):
                break
            targets[low + index] = struct.unpack(">i", raw[off:off + 4])[0]
        return low, high, default, targets
    return None


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        missing = [n for n in (STORE, LOGIC, EVENTS, CLIENT, OPEN_MIXIN, GRAPH_MIXIN, SAVE_MIXIN)
                   if n not in names]
        if missing:
            return fail("build 351 is not in this jar: %s" % ", ".join(missing))
        blobs = {n: z.read(n) for n in (STORE, LOGIC, EVENTS, CLIENT, OPEN_MIXIN, GRAPH_MIXIN,
                                        SAVE_MIXIN)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        ru = json.loads(z.read(RU).decode("utf-8"))
        en = json.loads(z.read(EN).decode("utf-8"))

    common = config.get("mixins", [])
    client = config.get("client", [])
    if "NpcBlockSave351Mixin" not in common:
        return fail("the save hook is not a common mixin - a dedicated server would never save a block")
    if "NpcBlockSave351Mixin" in client:
        return fail("the save hook is also a client mixin")
    if SHARED_GRAPH_MIXIN in common:
        if common.index("NpcBlockSave351Mixin") > common.index(SHARED_GRAPH_MIXIN):
            return fail("the block save hook is listed after %s: a block save would be taken as the "
                        "shared graph" % SHARED_GRAPH_MIXIN)
    for name in ("NpcBlockOpen351Mixin", "NpcBlockGraph351Mixin"):
        if name not in client:
            return fail("%s is not a client mixin - the editor would never open" % name)
        if name in common:
            return fail("%s is registered as a common mixin" % name)

    store, store_refs = parse(blobs[STORE])
    store_text = text(store)
    if "settledlands_block_triggers" not in store_text:
        return fail("the store has no world data file name")
    if not any(r[1] == "computeIfAbsent" for r in store_refs):
        return fail("the store does not go through world data (computeIfAbsent)")
    if not any(r[0] and r[0].endswith("saveddata/SavedData") for r in store_refs):
        return fail("the store is not SavedData")
    if 4096 not in consts(store):
        return fail("the store has no block cap (4096)")
    if "Blocks" not in store_text:
        return fail("the store has no list tag name")

    logic, logic_refs = parse(blobs[LOGIC])
    logic_text = text(logic)
    for wanted in ("BlockGraph", "DialogueGraph"):
        if wanted not in logic_text:
            return fail("the block graph tag %r is missing - the editor and the server disagree"
                        % wanted)
    if not any(r[1] == "evalCondition" for r in logic_refs):
        return fail("an If node is not evaluated with the stock comparison")
    if not any(r[1] == "run" and r[0] and r[0].endswith("npc/NpcActions") for r in logic_refs):
        return fail("a trigger node does not run through NpcActions")
    if not any(r[0] and r[0].endswith("npc/NpcPoints") for r in logic_refs):
        return fail("a variable node does not use NpcPoints")
    if 256 not in consts(logic):
        return fail("the chain walk has no step bound (256) - a self link could loop the server")
    logic_methods = {(logic["cp"][n][1], logic["cp"][d][1]) for _a, n, d, _at in logic["methods"]}
    for wanted in ("isBlockEditorId", "idFor", "keyOf"):
        if not any(m[0] == wanted for m in logic_methods):
            return fail("BlockTrigger351 has no %s - an id would not find its block again" % wanted)
    id_consts = consts(logic)
    if -2000100000 not in id_consts or -2000199999 not in id_consts:
        return fail("the block editor id range is not the documented one")

    events, event_refs = parse(blobs[EVENTS])
    if not any(r[0] and r[0].endswith("PlayerInteractEvent$RightClickBlock") for r in event_refs):
        return fail("no right click on a block is listened to")
    if not any(r[1] == "wand" for r in event_refs):
        return fail("the NPC wand is not the editor opener")
    if not any(r[1] == "is" and r[2] == "(Lnet/minecraft/world/item/Item;)Z" for r in event_refs):
        return fail("the wand click is not compared by item identity")
    for wanted in ("open", "run"):
        if not any(r[1] == wanted and r[0] and r[0].endswith("npc/BlockTrigger351")
                   for r in event_refs):
            return fail("the event never calls BlockTrigger351.%s" % wanted)
    if not any(r[1] == "setCanceled" for r in event_refs):
        return fail("the wand click does not cancel the block's own interaction")

    graph_mixin, graph_refs = parse(blobs[GRAPH_MIXIN])
    for wanted in ("mouseClicked", "switchTab", "addAt", "contextMenu"):
        if not any(r[1] == wanted or wanted in text(graph_mixin) for r in graph_refs):
            return fail("the palette hook is not bound to %s" % wanted)
    palette = palette_rows(graph_mixin)
    if palette is None:
        return fail("the block palette is not a switch on the stock row numbers")
    low, high, default, targets = palette
    wanted = {2: "Trigger", 3: "If", 4: "Variable", 13: "Random"}
    rows = {row for row, offset in targets.items() if offset != default}
    if rows != set(wanted):
        return fail("the block palette maps rows %s; it must map exactly %s (Trigger, If, "
                    "Variable, Random)" % (sorted(rows), sorted(wanted)))

    client_cls, client_refs = parse(blobs[CLIENT])
    if not any(r[1] == "setScreen" for r in client_refs):
        return fail("the editor is never opened")
    if not any(r[0] and r[0].endswith("client/NpcDialogueGraphScreen") for r in client_refs):
        return fail("the block editor is not the stock graph screen")

    for path, table in (("ru", ru), ("en", en)):
        for key in LANG_KEYS:
            if key not in table:
                return fail("%s is missing %s" % (path, key))

    # No per-tick work: the whole feature is a click handler, nothing may be added to a tick loop.
    for name, data in blobs.items():
        _parsed, refs = parse(data)
        for owner, member, desc in refs:
            if member in ("tick", "tickSubGraph", "aiStep") and owner is not None:
                return fail("%s calls %s - a block graph must not tick" % (name, member))

    print("block_trigger_check: OK  %s  (a block keeps its graph in world data, the palette is the "
          "four nodes it can run, and the chain walk is bounded)" % jar)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
