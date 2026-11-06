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
  * the palette hook and the block's own list of allowed types disagree - build 357 moved the row ->
    type map into BlockTrigger351#typeForRow so the two can be asked in one place, and the map itself
    is checked by block_line_check.py with BlockPaletteProbe;
  * the client half is registered as a common mixin (the server would load widgets) or the common half
    as a client mixin (a dedicated server would never run the save).

    python3 tools/block_trigger_check.py settledlands-0.8.0-build351-block-trigger.jar

Build 352 adds the fifth node and the passive work around it, with three ways to get it wrong:

  * the tick pass runs every tick or walks the whole store every pass - a server with one radius block
    would pay for all 4096 entries;
  * the ring and the highlight disagree with the toggle the author sees - the node says "yes" and
    nothing is drawn, or the box on the card and the ring say different things;
  * the class that carries the entry is compiled but never packed - build 352 shipped without
    `BlockTriggerStore351$Entry` and the game could not construct the mod at all;
  * the picture is drawn at the speed of the radius event (2.5 times a second), which makes the wand
    highlight blink between refreshes and freezes the ring in place - and a body's ring is not taken
    over at all, so an NPC shows a still circle on its "Цель" node and nothing on "В радиусе";
  * the visibility row is offered on an NPC (where build 320 already draws the flag) or on a node that
    has no radius.

  * build 363: the right-click menu of a block's graph still lists the sixteen rows of the stock
    editor - rows a block cannot run are refused with a line of text instead of being absent, so the
    author learns the palette by trial. The menu is cut down from BlockTrigger351#typeForRow (never
    from a copy of it), the stock painter is replaced for a block only, the copy/delete menu of an
    existing node stays stock, and the click maps the row of the shorter panel back to the stock row;

Exit 0 = the store is world data, the hooks are in the right lists, the palette is the eleven nodes a
block can run (build 357 added «Реплика»), the chain walker is bounded, the periodic work is one
gated pass over one dimension's entries, and the block's right-click menu is the same eleven nodes,
each of the five new ones fired or waited for by something the jar actually runs.
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
TICKER = "dev/settledlands/npc/RadiusPass354.class"
NPC_RING = "dev/settledlands/mixin/NpcRadiusRingMixin354.class"
ENTRY = "dev/settledlands/npc/BlockTriggerStore351$Entry.class"
CHECK_ROW = "dev/settledlands/mixin/NpcRadiusRow355Mixin.class"
DELETE_ROW = "dev/settledlands/mixin/NpcBlockDelete355Mixin.class"
OPEN_MIXIN = "dev/settledlands/mixin/NpcBlockOpen351Mixin.class"
GRAPH_MIXIN = "dev/settledlands/mixin/NpcBlockGraph351Mixin.class"
SAVE_MIXIN = "dev/settledlands/mixin/NpcBlockSave351Mixin.class"
# build 363: the right-click menu of a block's graph carries only the rows a block can run
# build 365: the block window grew buttons
TALK = "dev/settledlands/npc/BlockTalk358.class"
MENU = "dev/settledlands/npc/client/BlockMenu363.class"
MENU_MIXIN = "dev/settledlands/mixin/BlockMenu363Mixin.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"
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


def numeric_consts(parsed):
    """Every number in the pool: int, long, float and double - a 400 written as 400L is still 400."""
    out = set()
    for value in parsed["cp"]:
        if not value:
            continue
        if value[0] == 3:
            out.add(struct.unpack(">i", value[1])[0])
        elif value[0] == 5:
            out.add(struct.unpack(">q", value[1])[0])
        elif value[0] == 4:
            out.add(struct.unpack(">f", value[1])[0])
        elif value[0] == 6:
            out.add(struct.unpack(">d", value[1])[0])
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
        missing = [n for n in (STORE, ENTRY, LOGIC, EVENTS, CLIENT, TICKER, NPC_RING, CHECK_ROW, DELETE_ROW,
                               OPEN_MIXIN, GRAPH_MIXIN, SAVE_MIXIN)
                   if n not in names]
        if missing:
            return fail("build 351 is not in this jar: %s" % ", ".join(missing))
        blobs = {n: z.read(n) for n in (STORE, ENTRY, LOGIC, EVENTS, CLIENT, TICKER, NPC_RING, CHECK_ROW, DELETE_ROW,
                                        OPEN_MIXIN, GRAPH_MIXIN, SAVE_MIXIN, TALK)}
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
    for name in ("NpcBlockOpen351Mixin", "NpcBlockGraph351Mixin", "NpcRadiusRow355Mixin",
                 "NpcBlockDelete355Mixin"):
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
    # build 357: the row -> type map moved into BlockTrigger351#typeForRow, next to the palette it
    # has to agree with; block_line_check.py runs BlockPaletteProbe to read the map itself.
    if not any(r[1] == "typeForRow" and r[0] and r[0].endswith("npc/BlockTrigger351")
               for r in graph_refs):
        return fail("the block palette does not ask BlockTrigger351 which node a row builds - the "
                    "editor and the walker could disagree about what a block can build")
    if palette_rows(graph_mixin) is not None:
        return fail("the block palette still carries its own switch - the map must live in "
                    "BlockTrigger351#typeForRow, where it can be checked against PALETTE")

    # ---- build 363: the right-click menu shows only the rows a block can run --------------------
    for name in (MENU, MENU_MIXIN):
        if name not in names:
            return fail("build 363 is not in this jar: %s" % name)
    with zipfile.ZipFile(jar) as extra:
        blobs[MENU] = extra.read(MENU)
        blobs[MENU_MIXIN] = extra.read(MENU_MIXIN)
        screen_bytes = extra.read(SCREEN)
    if MENU_MIXIN.rsplit("/", 1)[-1][:-6] not in client:
        return fail("the menu hook is not a client mixin - it paints a screen")
    menu_cls, menu_refs = parse(blobs[MENU])
    if not any(r[1] == "typeForRow" and r[0] and r[0].endswith("npc/BlockTrigger351")
               for r in menu_refs):
        return fail("the block's menu does not ask BlockTrigger351#typeForRow which rows a block "
                    "has - a copy of the palette would drift away from it")
    for wanted in ("drawString", "fill", "pose"):
        if not any(r[1] == wanted for r in menu_refs):
            return fail("the block's menu never calls %s - it would paint nothing" % wanted)
    if not any(r[1] == "translatable" for r in menu_refs):
        return fail("the block's menu does not translate its labels")
    if "rows" not in text(menu_cls) or "stockRow" not in text(menu_cls):
        return fail("the block's menu does not map its own rows back to the stock ones")
    hook, hook_refs = parse(blobs[MENU_MIXIN])
    hook_text = text(hook)
    for wanted in ("drawContextMenu", "settledlands$mouseReleasedOriginal", "mouseScrolled"):
        if wanted not in hook_text:
            return fail("the menu hook is not bound to %s" % wanted)
    if not any(r[1] == "draw" and r[0] and r[0].endswith("client/BlockMenu363")
               for r in hook_refs):
        return fail("the menu hook never paints the cut-down panel")
    if not any(r[1] == "fit" and r[0] and r[0].endswith("client/BlockMenu363")
               for r in hook_refs):
        return fail("the menu hook never measures the panel - every row would be drawn at the "
                    "height of sixteen rows")
    if "cancel" not in hook_text:
        return fail("the menu hook does not cancel the stock panel - sixteen rows would be painted "
                    "under the cut-down ones")
    for signature in (b"settledlands$mouseReleasedOriginal", b"drawContextMenu", b"mouseScrolled"):
        if signature not in screen_bytes:
            return fail("the screen has no %s - the menu hook would silently do nothing"
                        % signature.decode())
    if not any(r[1] == "stockRow" and r[0] and r[0].endswith("client/BlockMenu363")
               for r in graph_refs):
        return fail("the block palette click never maps the row of the shorter panel back to the "
                    "stock row - a click would build the node of the wrong row")
    if not any(r[1] == "typeForRow" and r[0] and r[0].endswith("npc/BlockTrigger351")
               for r in graph_refs):
        return fail("the block palette click does not ask BlockTrigger351 which node a row builds")

    # ---- build 365: Кнопка, Время, Таймер, Катсцена, Цель --------------------------------------
    # Five rows joined the palette. A row that builds a node nobody fires is worse than no row, so
    # each new type is checked on the side that makes it real: the two clocks and the zone in the tick
    # pass, the button in the block window, the scene in the walk.
    _pass, pass_refs = parse(blobs[TICKER])
    pass_methods = {_pass["cp"][n][1] for _a, n, _d, _at in _pass["methods"]}
    for wanted in ("isEventNode", "timeEvents", "timerEvents", "resumeCutscenes", "afterCutscene"):
        if wanted not in pass_methods:
            return fail("RadiusPass354 has no %s - that node would be stepped over by the walk and "
                        "fired by nobody" % wanted)
    logic_parsed, logic_refs = parse(blobs[LOGIC])
    if not any(r[1] == "isEventNode" and r[0] and r[0].endswith("npc/RadiusPass354")
               for r in logic_refs):
        return fail("the walk does not ask the pass what an event node is - Время, Таймер and Цель "
                    "would be stepped over in silence")
    if not any(r[1] == "afterCutscene" and r[0] and r[0].endswith("npc/RadiusPass354")
               for r in logic_refs):
        return fail("the walk never hands a started scene back to the pass - the chain after a "
                    "«Катсцена» would never continue")
    if not any(r[1] == "isPlaying" for r in pass_refs):
        return fail("the tick pass never asks whether a scene is still playing - a «Катсцена» "
                    "would hold its chain for ever")
    if not any(r[1] == "getDayTime" for r in pass_refs):
        return fail("the «Время» node does not read the hour of the day - it could not fire at its "
                    "own time")
    if 400 not in numeric_consts(_pass):
        return fail("the 400 tick window of the stock time rule is gone - the node would miss its "
                    "hour between two passes")
    if 32 not in numeric_consts(_pass):
        return fail("no 32 block reach in the pass - a clock would fire for a player at the edge of "
                    "the world")
    # Build 394: the flag itself moved into the Entry of the store (the place a pass watches is an
    # Entry), and the counting is now RadiusPass354#hasFiringEvent - which is isEventNode over the four
    # event types, i.e. exactly what this check has always been about. Look in both classes.
    store_parts = [STORE] + [name for name in blobs if name.startswith(STORE[:-len(".class")] + "$")]
    store_fire_refs = set()
    for name in store_parts:
        store_fire_refs |= parse(blobs[name])[1]
    if not any(r[1] in ("isEventNode", "hasFiringEvent") and r[0]
               and r[0].endswith("npc/RadiusPass354") for r in store_fire_refs):
        return fail("the store's \"has something to fire\" flag ignores Время and Таймер - a block "
                    "with only a clock would never be ticked")

    talk_cls, _talk_refs = parse(blobs[TALK])
    talk_methods = {talk_cls["cp"][n][1] for _a, n, _d, _at in talk_cls["methods"]}
    if "choices" not in talk_methods:
        return fail("the block window has no choices() - a «Кнопка» would have nothing to show")
    if "targets" not in text(talk_cls):
        return fail("a block window does not keep the target of each button - a click would walk on "
                    "from node.next instead of the node the author wired")
    if not any(r[1] == "choices" and r[0] and r[0].endswith("npc/BlockTalk358")
               for r in logic_refs):
        return fail("BlockTrigger351 has no button step - the «Кнопка» row would build a node the "
                    "walk steps over in silence")
    if not any(r[1] == "play" and r[0] and r[0].endswith("npc/CutsceneCamera")
               for r in logic_refs):
        return fail("the walk never plays a scene - the «Катсцена» row would build a node that does "
                    "nothing")

    # ---- build 366: the panel is the stock one again -------------------------------------------
    # 365 made the panel eleven rows tall: the window grew, the scrollbar vanished, the rows lost the
    # colours of their node types, and every click below the fifth row was dropped because the stock
    # hit test only knows its own five rows. These checks forbid that panel from coming back.
    menu_nums = numeric_consts(menu_cls)
    if 134 not in menu_nums:
        return fail("the block panel is not the stock height (134) - it is the tall panel of build "
                    "365 again: it does not fit the screen and has no scrollbar")
    if 266 in menu_nums:
        return fail("the block panel still computes the eleven-row height of build 365")
    for wanted in (-7550482, -8467457):
        if wanted not in menu_nums:
            return fail("the block panel lost the stock row colours (missing %d) - every row "
                        "would be painted grey" % wanted)
    menu_methods = {menu_cls["cp"][n][1] for _a, n, _d, _at in menu_cls["methods"]}
    if "wheelStep" not in menu_methods:
        return fail("the block panel has no wheelStep - the wheel direction would be written inline "
                    "and could be inverted again (build 366 scrolled against the hand)")
    if not any(r[1] == "wheelStep" and r[0] and r[0].endswith("client/BlockMenu363")
               for r in menu_refs):
        return fail("the panel's wheel does not go through BlockMenu363#wheelStep - the direction of "
                    "the notch is not the stock one")
    for wanted in ("rowAt", "stripHit", "stripClick", "colour"):
        if wanted not in menu_methods:
            return fail("BlockMenu363 has no %s - the panel could not be clicked below its fifth "
                        "row, or would have no scrollbar" % wanted)
    graph_mixin_parsed, graph_mixin_refs2 = parse(blobs[GRAPH_MIXIN])
    if any(r[1] == "rowAt" and r[0] and r[0].endswith("client/NpcGraphContextMenu")
           for r in graph_mixin_refs2):
        return fail("the block palette still asks NpcGraphContextMenu#rowAt for the clicked row - "
                    "that hit test only knows five rows and drops every row below them")
    for wanted in ("rowAt", "stripHit"):
        if not any(r[1] == wanted and r[0] and r[0].endswith("client/BlockMenu363")
                   for r in graph_mixin_refs2):
            return fail("the block palette click never asks BlockMenu363#%s - the row of the click "
                        "and the row that is painted could disagree" % wanted)

    menu_cls, _menu_refs = parse(blobs[MENU])
    if "wheel" not in {menu_cls["cp"][n][1] for _a, n, _d, _at in menu_cls["methods"]}:
        return fail("the block panel cannot scroll - eleven rows do not fit and the last five could "
                    "never be clicked")
    if not any(r[1] == "wheel" and r[0] and r[0].endswith("client/BlockMenu363")
               for r in parse(blobs[MENU_MIXIN])[1]):
        return fail("the wheel never reaches the block panel - the stock scroller counts sixteen rows")

    client_cls, client_refs = parse(blobs[CLIENT])
    if not any(r[1] == "setScreen" for r in client_refs):
        return fail("the editor is never opened")
    if not any(r[0] and r[0].endswith("client/NpcDialogueGraphScreen") for r in client_refs):
        return fail("the block editor is not the stock graph screen")

    # ---- build 352: the radius node, its ring and the wand highlight --------------------------
    ticker, ticker_refs = parse(blobs[TICKER])
    ticker_methods = {(ticker["cp"][n][1], ticker["cp"][d][1]) for _a, n, d, _at in ticker["methods"]}
    if not any(m[0] == "onServerTick" for m in ticker_methods):
        return fail("the tick pass has no server tick handler")
    if not any(r[0] and r[0].endswith("event/tick/ServerTickEvent$Post") for r in ticker_refs):
        return fail("the tick pass is not bound to the server tick event the base jar already uses")
    if not any(r[1] == "getGameTime" for r in ticker_refs):
        return fail("the tick pass has no period gate - it would run every tick")
    periods = consts(ticker)
    if 2 not in periods:
        return fail("the picture period (2) is not in the pass - the sparks would blink again")
    if 8 not in periods:
        return fail("the radius event period (8) is not in the pass - it would ask the world every "
                    "other tick")
    if not any(r[0] and r[0].endswith("particles/ParticleTypes") and r[1] == "ELECTRIC_SPARK"
               for r in ticker_refs):
        return fail("the wand highlight does not spark on the blocks")
    if not any(r[0] and r[0].endswith("particles/ParticleTypes") and r[1] == "ENCHANT"
               for r in ticker_refs):
        return fail("the radius ring is not drawn")
    if not any(r[1] == "sendParticles" for r in ticker_refs):
        return fail("the tick pass never sends a particle")
    if not any(r[1] == "getItemInHand" for r in ticker_refs):
        return fail("the highlight does not look at the hand holding the wand")
    if not any(r[1] == "ofDimension" for r in ticker_refs):
        return fail("the tick pass does not go through the per-dimension index - it would scan the "
                    "whole store every period")

    store_text = text(store)
    if "byDimension" not in store_text or "ofDimension" not in store_text:
        return fail("the store has no per-dimension index")
    if not any(r[1] == "ring" for r in ticker_refs) or not any(r[1] == "check" for r in ticker_refs):
        return fail("the tick pass does not separate the ring entries from the check entries")

    # the ring of a body: build 320 draws a still circle for the "Цель" node only, the hook takes
    # the whole drawing over so a body gets a travelling ring for "В радиусе" too
    npc_ring, npc_ring_refs = parse(blobs[NPC_RING])
    if "dev.settledlands.npc.NpcRadiusZone320" not in text(npc_ring):
        return fail("the NPC ring hook does not target build 320's class")
    if "tick(Ldev/settledlands/npc/GuideNpc;)V" not in text(npc_ring):
        return fail("the NPC ring hook does not name build 320's tick method - it would never apply")
    if not any(r[1] == "npcTick" for r in npc_ring_refs):
        return fail("the NPC ring hook does not draw through the pass - a body would show nothing")

    row_mixin, row_refs = parse(blobs[CHECK_ROW])
    if not any(r[1] == "setVisible" for r in row_refs):
        return fail("the visibility row does not write the node flag")
    if not any(r[0] and r[0].endswith("npc/RadiusPass354") and r[1] == "isRadiusNode"
               for r in row_refs):
        return fail("the visibility row does not use the pass's idea of a radius node - build 320's "
                    "own answer excludes the \"В радиусе\" node, so the row would miss it")
    if any(r[1] == "isBlockEditorId" for r in row_refs):
        return fail("the visibility row is still limited to a block - a body must get it too")
    if not any(r[1] == "save" for r in row_refs):
        return fail("the visibility row does not save on the click - the ring is drawn by the "
                    "server, so the toggle would look off and stay on")

    # build 355: a block can be cleared with a button, and an empty graph means "no graph"
    delete_mixin, delete_refs = parse(blobs[DELETE_ROW])
    for wanted in ("clear", "save", "isBlockEditorId"):
        if not any(r[1] == wanted for r in delete_refs):
            return fail("the block delete button does not %s" % wanted)
    if "entry" not in text(delete_mixin):
        return fail("the block delete button leaves the start node pointing at a removed node")
    if "block.deleted" not in text(logic):
        return fail("the server does not report a cleared block - an empty graph must remove the "
                    "entry, not keep an empty one")

    common, client_list = config.get("mixins", []), config.get("client", [])
    if "NpcRadiusRingMixin354" not in common:
        return fail("the NPC ring hook is not registered as a common mixin - the server would keep "
                    "320's still circle")
    if "NpcRadiusRow355Mixin" not in client_list or "NpcRadiusRow355Mixin" in common:
        return fail("the visibility row must be a client mixin")
    if "NpcBlockDelete355Mixin" not in client_list or "NpcBlockDelete355Mixin" in common:
        return fail("the block delete button must be a client mixin")
    for stale in ("NpcBlockCheckRow352Mixin", "NpcRadiusRow354Mixin"):
        if stale in client_list or stale in common:
            return fail("%s is still registered" % stale)

    for key in ("gui.settledlands.block.visible", "gui.settledlands.block.visible_on",
                "gui.settledlands.block.visible_off"):
        if key not in LANG_KEYS:
            pass

    for path, table in (("ru", ru), ("en", en)):
        for key in LANG_KEYS + ("gui.settledlands.block.visible",
                                "gui.settledlands.block.visible_on",
                                "gui.settledlands.block.visible_off",
                                "gui.settledlands.block.delete",
                                "gui.settledlands.block.delete_ask",
                                "message.settledlands.block.deleted"):
            if key not in table:
                return fail("%s is missing %s" % (path, key))

    # Only the tick pass may be periodic; the click path must stay a hash lookup.
    for name in (LOGIC, EVENTS, CLIENT, STORE):
        _parsed, refs = parse(blobs[name])
        for owner, member, desc in refs:
            if member in ("tick", "tickSubGraph", "aiStep"):
                return fail("%s calls %s - the periodic work belongs to the tick pass only"
                            % (name, member))

    print("block_trigger_check: OK  %s  (a block keeps its graph in world data, the palette is the "
          "eleven nodes it can run and the row map lives in BlockTrigger351#typeForRow, the chain walk "
          "is bounded, the picture runs every 2 ticks, the radius event every 8, a body's ring goes "
          "through the same pass, Время and Таймер fire from that pass, Цель fires in the same pass, "
          "a «Кнопка» waits for its click and a «Катсцена» for its playback, and both the visibility "
          "toggle and the delete button reach the server; the panel is the stock five-row list with "
          "its scrollbar and its colours, a click reaches every row, and the wheel moves it the stock "
          "way)" % jar)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
