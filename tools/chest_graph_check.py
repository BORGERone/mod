#!/usr/bin/env python3
"""Gate: a chest has the same graph a door has - one card of this mod, its two outputs as sockets.

The author, after four door builds: «Все прекрасно, теперь я хочу сделать такой-же граф для сундуков».
«Такой же» is the door's design, so this gate asks exactly the questions the door's gates ask, on the
chest's classes:

  * structurally, the chest's range is its own: `ChestGraph396` declares `ID_MAX`/`ID_MIN` as new numbers,
    never equal to the door's (`DoorGraph382`) and never overlapping them, so a save can only reach the
    owner of its id;
  * structurally, the chest's node type is this mod's own and cannot be a stock node: `CHEST_TYPE` is
    outside the stock range (0..CUTSCENE) and is not the door's 40 - a chest card drawn by the stock
    painter is the pink box of build 359/382;
  * structurally, the two outputs are **sockets of the card, not nodes**: `ChestGraph396` creates exactly
    one node (`NpcDialogueGraph#add`) and never wires anything itself (`NpcDialogueGraph#connect` is not
    referenced at all), while `ChestCard396` draws the door's two shapes - `DoorCard382#socket` and
    `DoorCard382#diamond` - so the chest's pins are literally the door's pins;
  * structurally, the chain starts at the socket and runs the stock interpreter: `chainStart` and `run`
    reach `BlockTrigger351#start`, the same entry point a block's and a door's chain use;
  * structurally, the editor is the door's editor: `ChestGraph396#open` sends the same `NpcNet.CfgView`
    through `NpcNet#sendRaw`, `ChestGraphClient396#openEditor` opens the same `NpcDialogueGraphScreen`,
    the save comes back into `ChestGraph396#save`, and `RadiusPass354` walks the chests as a third kind of
    place (so a «В радиусе», «Время» or «Таймер» node in a chest's graph is run by somebody - build 394's
    lesson, not repeated);
  * structurally, the closing moment is the game's own answer: `ChestGraph396Events` reads
    `ServerPlayer#containerMenu` against `ServerPlayer#inventoryMenu`, the pair `NpcEscort` of the base mod
    compares - not a timer and not a reflection trick;
  * the seven mixins are registered (six client, one common), `BlockMenu363Mixin` knows the chest's panel,
    and the fifteen language keys are in both files;
  * behaviourally, on the real classes from the jar: `tools/harness/ChestGraph396Probe.java` shows a fresh
    chest graph holding exactly one node (the card, and the entry), an unwired socket having no chain and a
    wired one starting the chain at the node it points to, the card remembering the count and the name (in
    the two fields the stock serialiser keeps for every node), and `stripCards` dropping the two cards the
    editor adds.

Build 397 added the lock, and the author's report was one line: «в графе сундука я не могу настраивать
взлом». So the gate asks the same question of the four settings the card now carries:

  * structurally, the four numbers are the four stock node fields a save keeps - `varCompare` (keys 4..10),
    `varOp` (seconds 1..20), `checkTarget` (difficulty 1..3) and `pointsShared` (locked) - with the door's
    ranges read out of `ChestLock397`'s constant pool, so a range cannot drift from the panel it feeds;
  * structurally, every one of the four can actually be **edited in the graph**: the chest's inspector
    builds the door's sliders (`NpcUi#slider`) and writes all four setters through `propertyChange` (the
    stock undo point, the dirty flag and the card - build 362), the graph writes `applyDefaults` on open and
    on save, and the card reads the state it shows;
  * structurally, a locked chest is refused where it would have been opened and cannot be broken open:
    `ChestGraph396Events` calls `setCanceled` + `setCancellationResult` on the click and `setCanceled` on
    `BlockEvent.BreakEvent`, and asks `ChestLock397#panel` for the numbers it sends;
  * structurally, the answer opens the chest's **own** window: `ChestNet397` registers both payloads and
    calls `ServerPlayer#openMenu` with `BlockState#getMenuProvider`, then hands the opening to
    `ChestGraph396Events#remember` so the chain still runs when that window closes;
  * behaviourally, `tools/harness/ChestHack397Probe.java` walks the four setters and readers, the clamps, a
    card written by build 396 (three zeroes read back as the defaults) and a stock node of a chest's graph
    (never touched), and the twelve language keys are in both files.

    python3 tools/chest_graph_check.py settledlands-0.8.0-build396-chest-graph.jar

Exit 0 = a chest has the door's graph: one card, two sockets on it, the stock palette and interpreter.
"""
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "ChestGraph396Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("stubs396-out", "api", "api5", "api4", "api3", "stubs-out", "api2")]

GRAPH = "dev/settledlands/chest/ChestGraph396.class"
STORE = "dev/settledlands/chest/ChestGraph396Store.class"
EVENTS = "dev/settledlands/chest/ChestGraph396Events.class"
CARD = "dev/settledlands/npc/client/ChestCard396.class"
LOCK = "dev/settledlands/chest/ChestLock397.class"
NET = "dev/settledlands/chest/ChestNet397.class"
PANEL_PACKET = "dev/settledlands/chest/ChestNet397$Panel.class"
SOLVED_PACKET = "dev/settledlands/chest/ChestNet397$Solved.class"
PANEL_SCREEN = "dev/settledlands/door/HackPanel377.class"
OPEN_CLASS = "dev/settledlands/chest/ChestOpen401.class"
READY_PACKET = "dev/settledlands/chest/ChestNet397$Ready.class"
ROWS = "dev/settledlands/mixin/NpcChestRows396Mixin.class"
CLIENT = "dev/settledlands/npc/client/ChestGraphClient396.class"
PASS = "dev/settledlands/npc/RadiusPass354.class"
SPOT = "dev/settledlands/npc/RadiusPass354$ChestSpot.class"
MENU = "dev/settledlands/mixin/BlockMenu363Mixin.class"
DOOR_GRAPH = "dev/settledlands/door/DoorGraph382.class"
VALUE_PIN = "dev/settledlands/npc/client/PursuitGraphNode272.class"

NEW_CLASSES = (GRAPH, STORE, EVENTS, CARD, CLIENT, SPOT,
               "dev/settledlands/mixin/NpcChestSave396Mixin.class",
               "dev/settledlands/mixin/NpcChestOpen396Mixin.class",
               "dev/settledlands/mixin/NpcChestNoStats396Mixin.class",
               "dev/settledlands/mixin/NpcChestNoAi396Mixin.class",
               "dev/settledlands/mixin/NpcChestCard396Mixin.class",
               "dev/settledlands/mixin/NpcChestRows396Mixin.class",
               "dev/settledlands/mixin/NpcChestPalette396Mixin.class")

COMMON_MIXINS = ("NpcChestSave396Mixin",)
CLIENT_MIXINS = ("NpcChestOpen396Mixin", "NpcChestNoStats396Mixin", "NpcChestNoAi396Mixin",
                 "NpcChestCard396Mixin", "NpcChestRows396Mixin", "NpcChestPalette396Mixin")

LANG_KEYS = (
    "gui.settledlands.chest.node",
    "gui.settledlands.chest.count",
    "gui.settledlands.chest.opener_none",
    "gui.settledlands.chest.opener_last",
    "gui.settledlands.chest.trigger",
    "gui.settledlands.chest.pin_out",
    "gui.settledlands.chest.pin_who",
    "gui.settledlands.chest.pin_hint",
    "gui.settledlands.chest.palette",
    "gui.settledlands.chest.one_tab",
    "gui.settledlands.chest.added_node",
    "message.settledlands.chest.graph_open",
    "message.settledlands.chest.graph_saved",
    "message.settledlands.chest.graph_cleared",
    "message.settledlands.chest.graph_full",
)

# Build 397: the four settings of the lock and what the panel says when it refuses.
LANG_KEYS_397 = (
    "gui.settledlands.chest.keys",
    "gui.settledlands.chest.speed",
    "gui.settledlands.chest.difficulty",
    "gui.settledlands.chest.lock",
    "gui.settledlands.chest.locked",
    "gui.settledlands.chest.unlocked",
    "gui.settledlands.chest.state_locked",
    "gui.settledlands.chest.state_open",
    "message.settledlands.chest.locked",
    "message.settledlands.chest.free",
    "message.settledlands.chest.far",
    "message.settledlands.chest.gone",
)

# The four card fields the lock lives in, and the four setters that write them.
LOCK_FIELDS = ("varCompare", "varOp", "checkTarget", "pointsShared")
LOCK_SETTERS = ("setKeys", "setSeconds", "setDifficulty", "setLocked")
LOCK_READERS = ("keys", "seconds", "difficulty", "locked")

# The ranges of the door's panel, on the chest's card. A number out of range is a panel nobody can solve.
LOCK_RANGES = (("MIN_KEYS", 4), ("MAX_KEYS", 10), ("DEFAULT_KEYS", 6),
               ("MIN_SPEED", 1), ("MAX_SPEED", 20), ("DEFAULT_SPEED", 5),
               ("MIN_DIFFICULTY", 1), ("MAX_DIFFICULTY", 3), ("DEFAULT_DIFFICULTY", 1))

PROBE_CLASS = "ChestGraph396Probe"
LOCK_PROBE = ROOT / "tools" / "harness" / "ChestHack397Probe.java"
LOCK_PROBE_CLASS = "ChestHack397Probe"
# Build 400: the numbers of the card reach the panel, and the lift of a solved panel lasts one click.
OPEN_PROBE = ROOT / "tools" / "harness" / "ChestOpen401Probe.java"
OPEN_PROBE_CLASS = "ChestOpen401Probe"
CLOSE_PROBE = ROOT / "tools" / "harness" / "ChestClose402Probe.java"
CLOSE_PROBE_CLASS = "ChestClose402Probe"

EXPECT = {
    "TYPE": "own=1 stock=false",
    "CARD": "nodes=1 card=true entry=true",
    "CHAIN": "unwired=none wired=1 stop=none",
    "MEMORY": "count=3 opener=BORGER long=32",
    "STRIP": "dropped=2 left=3 entry=kept",
    "ID": "in=true back=true stable=true",
}

# Build 397, from tools/harness/ChestHack397Probe.java - the lock is a setting on the card.
EXPECT_LOCK = {
    "DEFAULTS": "keys=6 sec=5 diff=1 locked=false state=gui.settledlands.chest.state_open",
    "SET": "keys=4 sec=20 diff=3 locked=true state=gui.settledlands.chest.locked",
    "RANGE": "keys=10 sec=1 diff=1",
    "RAW": "keys=6 sec=5 diff=1",
    "STOCK": "untouched=true",
    "NOTE": "fields=6",
}


def instruction_listing_all(data, method_name):
    """Every overload of one method name, joined - `run` has two, and the chain lives in the one that works."""
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    parts = []
    for _acc, ni, di, attrs in parsed["methods"]:
        if cp[ni][1] != method_name:
            continue
        code = disasm.code_of(attrs, cp)
        if code is None:
            continue
        parts.append(disasm.disasm(code["code"], cp)[0])
    return "\n".join(parts) if parts else None


def instruction_listing(data, method_name):
    """The instructions of one method, in order, as one searchable text.

    <p>Ref order is the only order a jar has, and the order of the two calls in `solve` is the whole of the
    author's report about the chest: chain first, `chest_ready` second.</p>
    """
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    for _acc, ni, di, attrs in parsed["methods"]:
        if cp[ni][1] != method_name:
            continue
        code = disasm.code_of(attrs, cp)
        if code is None:
            return None
        return disasm.disasm(code["code"], cp)[0]
    return None


def refs_of(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    out = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        out.add((owner, name, desc))
    return parsed, out


def methods_of(parsed):
    cp = parsed["cp"]
    return {cp[ni][1] for _a, ni, _d, _attrs in parsed["methods"]}


def int_constant(cf, name):
    """The value of a `static final int` field, or None when the class has no such field."""
    for field in cf.fields:
        if field.name != name:
            continue
        attr = field.attr("ConstantValue")
        if attr is None or len(attr.data) < 2:
            return None
        index = struct.unpack(">H", attr.data[:2])[0]
        entry = cf.cp[index]
        if not entry or entry[0] != 3:
            return None
        return struct.unpack(">i", struct.pack(">I", entry[1]))[0]
    return None


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in NEW_CLASSES + (PASS, MENU, DOOR_GRAPH, VALUE_PIN):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        graph_bytes = zf.read(GRAPH)
        graph, graph_refs = refs_of(graph_bytes)
        graph_methods = methods_of(graph)

        # 1. the chest's range is its own, and does not touch the door's.
        chest = classfile.parse(graph_bytes, GRAPH)
        chest_max = int_constant(chest, "ID_MAX")
        chest_min = int_constant(chest, "ID_MIN")
        door = classfile.parse(zf.read(DOOR_GRAPH), DOOR_GRAPH)
        door_max = int_constant(door, "ID_MAX")
        door_min = int_constant(door, "ID_MIN")
        if None in (chest_max, chest_min, door_max, door_min):
            problems.append("ID_MAX/ID_MIN could not be read - the editors no longer say which ids are"
                            " theirs")
        else:
            if chest_max < chest_min:
                problems.append("the chest range is inverted (ID_MAX %d < ID_MIN %d)"
                                % (chest_max, chest_min))
            if not (chest_max < door_min or chest_min > door_max):
                problems.append("the chest range %d..%d overlaps the door range %d..%d - a save could"
                                " reach the wrong owner" % (chest_min, chest_max, door_min, door_max))

        # 2. the card's type is this mod's own and is not the door's.
        chest_type = int_constant(chest, "CHEST_TYPE")
        door_type = int_constant(door, "DOOR_TYPE")
        if chest_type is None:
            problems.append("ChestGraph396 has no CHEST_TYPE - the card has no type of its own")
        else:
            if 0 <= chest_type <= 14:
                problems.append("CHEST_TYPE %d is inside the stock range 0..14 - the stock painter would"
                                " draw the card as a stock node (build 359/382 pink box)" % chest_type)
            if door_type is not None and chest_type == door_type:
                problems.append("CHEST_TYPE equals the door's %d - the two cards would be one type"
                                % door_type)

        # 3. one card, two sockets: exactly one node is created and nothing is wired by this class.
        for needed in ("chainStart", "noteOpened", "run", "save", "open", "stripCards", "defaultGraph",
                       "withChestNode", "isChestEditorId", "idFor", "keyOf", "isChest", "isChestAt",
                       "typeForRow", "paletteRows", "isChestNode", "isChestGraphNode", "opener",
                       "setOpener", "count", "setCount"):
            if needed not in graph_methods:
                problems.append("ChestGraph396 has no %s()" % needed)
        if any(r[1] == "connect" and r[0] and r[0].endswith("NpcDialogueGraph")
               for r in graph_refs):
            problems.append("ChestGraph396 wires something itself - the chest's outputs are sockets of"
                            " its card, not nodes beside it (build 392)")

        # 4. the stock interpreter and the stock packet: the same seams a block and a door use.
        if not any(r[1] == "start" and r[0] == "dev/settledlands/npc/BlockTrigger351"
                   for r in graph_refs):
            problems.append("ChestGraph396 does not reach BlockTrigger351#start - a chest's chain would"
                            " not be run by the interpreter a block's and a door's chain is run by")
        if not any(r[1] == "sendRaw" and r[0] == "dev/settledlands/npc/NpcNet" for r in graph_refs):
            problems.append("ChestGraph396 does not send NpcNet#sendRaw - the stock editor would never"
                            " receive the chest's graph")

        # 5. the card draws the door's two socket shapes, and the value socket is the blue one.
        card, card_refs = refs_of(zf.read(CARD))
        for wanted, why in (("socket", "the round execution socket of the card"),
                            ("diamond", "the blue value socket of the card")):
            if not any(r[1] == wanted and r[0] == "dev/settledlands/npc/client/DoorCard382"
                       for r in card_refs):
                problems.append("ChestCard396 does not draw %s (%s) - a chest's pins must be the door's"
                                " pins, not a second set" % (why, wanted))
        # The blue of a value pin is a `static final int`, so it is inlined into whoever uses it and there
        # is no field reference to find (the lesson of build 384). The value is read from the class that
        # owns it and looked for in the card's own constant pool - the card draws the pin in that colour or
        # it does not.
        pin = classfile.parse(zf.read(VALUE_PIN), VALUE_PIN)
        blue = int_constant(pin, "COL_VALUE")
        card_cf = classfile.parse(zf.read(CARD), CARD)
        ints = {struct.unpack(">i", struct.pack(">I", e[1]))[0]
                for e in card_cf.cp if e and e[0] == 3}
        if blue is None:
            problems.append("PursuitGraphNode272 has no COL_VALUE - the blue of a value pin is gone")
        elif blue not in ints:
            problems.append("ChestCard396 does not use the blue value colour (%d) of this mod's value"
                            " pins - the blue socket on a chest's card is not the blue socket of a door's"
                            % blue)

        # 6. the close is the game's own answer, not a clock.
        events, event_refs = refs_of(zf.read(EVENTS))
        field_refs = {(r[0], r[1]) for r in event_refs}
        for wanted in (("net/minecraft/server/level/ServerPlayer", "containerMenu"),
                       ("net/minecraft/server/level/ServerPlayer", "inventoryMenu")):
            if wanted not in field_refs:
                problems.append("ChestGraph396Events does not read %s#%s - the moment the chest's window"
                                " closes would be a guess again" % wanted)
        if not any(r[1] == "isWand" and r[0] and r[0].endswith("door/DoorGraph382Events")
                   for r in event_refs):
            problems.append("ChestGraph396Events does not use the same wand test as every other graph"
                            " editor")

        # 7. the pass walks the chests as a third kind of place.
        place, place_refs = refs_of(zf.read(PASS))
        if "addChests" not in methods_of(place):
            problems.append("RadiusPass354 has no addChests() - a chest's «В радиусе», «Время» and"
                            " «Таймер» nodes would be placed, wired and never fired (build 394)")
        if not any(r[0] == "dev/settledlands/chest/ChestGraph396Store" for r in place_refs):
            problems.append("RadiusPass354 never reads the chest store")

        # 8. the two extra tabs are taken off a chest's screen, not only refused a click (build 362).
        tabs = zf.read("dev/settledlands/mixin/BlockNoTabs360Mixin.class")
        if b"ChestGraph396" not in tabs:
            problems.append("BlockNoTabs360Mixin does not know the chest - the tab strip of a body's"
                            " editor would stand on a chest's canvas, which is what build 362 removed"
                            " from a block's")

        # 9. the panel of a block and a door also serves a chest.
        if not any(r[1] == "isChestEditorId" and r[0] and r[0].endswith("chest/ChestGraph396")
                   for r in refs_of(zf.read(MENU))[1]):
            problems.append("BlockMenu363Mixin does not know the chest's panel - the create menu of a"
                            " chest would be an unmeasured stock list")

        # 10. the mixins are registered, and one of them owns each seam.
        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = mixins.get("mixins", [])
        client = mixins.get("client", [])
        for name in COMMON_MIXINS:
            if name not in common:
                problems.append("%s is not in the common mixin list - the save of a chest would never"
                                " reach the chest" % name)
            if name in client:
                problems.append("%s is in both lists" % name)
        for name in CLIENT_MIXINS:
            if name not in client:
                problems.append("%s is not in the client mixin list - the chest's editor would be a"
                                " body's editor" % name)
            if name in common:
                problems.append("%s is in both lists" % name)
        for path, needle, why in ((zf.read("dev/settledlands/mixin/NpcChestOpen396Mixin.class"),
                                  b"openEditor", "the editor is not opened"),
                                 (zf.read("dev/settledlands/mixin/NpcChestSave396Mixin.class"),
                                  b"settledlands$chestSave", "the save is not taken"),
                                 (zf.read("dev/settledlands/mixin/NpcChestCard396Mixin.class"),
                                  b"drawNode", "the card is not painted"),
                                 (zf.read("dev/settledlands/mixin/NpcChestRows396Mixin.class"),
                                  b"widgets", "the inspector rows are not built"),
                                 (zf.read("dev/settledlands/mixin/NpcChestPalette396Mixin.class"),
                                  b"switchTab", "the second tab is not refused")):
            if needle not in path:
                problems.append("%s: %s" % (why, needle.decode()))

        # 11. the language keys, in both files.
        for path in ("assets/settledlands/lang/ru_ru.json", "assets/settledlands/lang/en_us.json"):
            strings = json.loads(zf.read(path).decode("utf-8"))
            for key in LANG_KEYS + LANG_KEYS_397:
                if key not in strings or not strings[key]:
                    problems.append("%s is missing %s" % (path.rsplit("/", 1)[-1], key))

        # 12. BUILD 397: the lock is a setting, and it lives on the card's stock fields.
        #     «в графе сундука я не могу настраивать взлом» - a card that shows a lock the author cannot
        #     set is not a setting, and a lock kept in a second store is a lock that falls out of step with
        #     the graph. So the four values are the four stock node fields the save already keeps (build
        #     384: a field a node type does not own is a field the save drops), written by four setters.
        lock_bytes = zf.read(LOCK)
        lock_cf = classfile.parse(lock_bytes, LOCK)
        lock, lock_refs = refs_of(lock_bytes)
        lock_methods = methods_of(lock)
        for name, want in LOCK_RANGES:
            got = int_constant(lock_cf, name)
            if got != want:
                problems.append("ChestLock397.%s is %r, expected %d - the panel of a chest would ask a"
                                " number the door's panel never asks" % (name, got, want))
        for name in LOCK_FIELDS:
            if not any(r[1] == name and r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
                       for r in lock_refs):
                problems.append("ChestLock397 never touches Node#%s - one of the four settings of the"
                                " lock would be a value nothing in the save keeps" % name)
        for name in LOCK_READERS + LOCK_SETTERS + ("panel", "applyDefaults", "stateKey",
                                                   "stateKeyShort", "locked"):
            if name not in lock_methods:
                problems.append("ChestLock397 has no %s()" % name)

        # 13. the card is where those settings are set: the graph writes the defaults, the inspector writes
        #     the values through the same commit the stock rows use, and the card shows the state.
        if not any(r[1] == "applyDefaults" and r[0] == "dev/settledlands/chest/ChestLock397"
                   for r in graph_refs):
            problems.append("ChestGraph396 never calls ChestLock397#applyDefaults - a chest that was"
                            " never edited would open with three zeroes where the panel's numbers are")
        rows, rows_refs = refs_of(zf.read(ROWS))
        for name in LOCK_SETTERS:
            if not any(r[1] == name and r[0] == "dev/settledlands/chest/ChestLock397"
                       for r in rows_refs):
                problems.append("the chest's inspector never calls ChestLock397#%s - that setting cannot"
                                " be edited in the graph, which is the whole of build 397's report" % name)
        if not any(r[1] == "slider" and r[0] == "dev/settledlands/npc/client/NpcUi"
                   for r in rows_refs):
            problems.append("the chest's inspector builds no slider - the three numbers of the lock have"
                            " no control")
        if "propertyChange" not in methods_of(rows):
            problems.append("the chest's inspector does not commit through propertyChange - the stock"
                            " undo point, the dirty flag and the card would not see the change (362)")
        card_397, card_refs_397 = refs_of(zf.read(CARD))
        if not any(r[0] == "dev/settledlands/chest/ChestLock397" for r in card_refs_397):
            problems.append("ChestCard396 does not read the lock - the card would show anything but the"
                            " state of its own chest")

        # 14. the two packets are registered, and the answer opens the CHEST'S OWN window.
        net, net_refs = refs_of(zf.read(NET))
        for name in ("playToClient", "playToServer"):
            if not any(r[1] == name and r[0] == "net/neoforged/neoforge/network/registration/PayloadRegistrar"
                       for r in net_refs):
                problems.append("ChestNet397 does not register %s - one half of the lock panel would never"
                                " arrive" % name)
        # Build 400: the opening is the player's own click, not a menu opened from behind a screen. Three
        # builds of `ServerPlayer#openMenu(state.getMenuProvider(...))` produced no window in the author's
        # game («по прежнему после успешного взлома сундук не открывается»), so the rule is now: the answer
        # lifts the lock of that one chest, and the panel clicks the chest through the game's own handler.
        # Build 401: the lift is a crack keyed by player and chest (a «Реплика» takes longer than ten
        # seconds), and the chest is clicked only after the chain of that chest has had its say.
        if not any(r[1] == "crack" and r[0] == "dev/settledlands/chest/ChestLock397" for r in net_refs):
            problems.append("ChestNet397 does not crack the chest a panel was solved for - the click that"
                            " follows would be refused and the panel would open again")
        if any(r[1] == "grant" for r in net_refs):
            problems.append("ChestNet397 still lifts a lock with ChestLock397#grant - the ten-second lift"
                            " of build 400 is gone (build 401); the crack is keyed by player and chest")
        # The order is the report («сначала открывается инвентарь а потом проигрывается реплика»), and the
        # order of a method is its instruction stream - a constant-pool set has no order at all.
        solve = instruction_listing(zf.read(NET), "solve")
        if solve is None:
            problems.append("ChestNet397 has no solve(...) - a solved panel could not answer its chest")
        else:
            run_at = solve.find("ChestGraph396.runCrack(")
            ready_at = solve.find("ChestNet397.sendReady(")
            if run_at < 0:
                problems.append("ChestNet397#solve never runs the «взлом» chain - the «Реплика» of that"
                                " socket would never be heard")
            if ready_at < 0:
                problems.append("ChestNet397#solve never tells the client the chest is ready - the chest"
                                " would never open")
            if run_at >= 0 and ready_at >= 0 and run_at > ready_at:
                problems.append("ChestNet397#solve tells the client the chest is ready BEFORE it runs the"
                                " «взлом» chain - that is the order the author reported as wrong («сначала"
                                " открывается инвентарь а потом проигрывается реплика»)")
        if any(r[1] == "openMenu" and r[0] == "net/minecraft/server/level/ServerPlayer"
               for r in net_refs):
            problems.append("ChestNet397 opens a menu from the server again - the path that never opened a"
                            " chest in the author's game (build 400)")
        events_400, event_refs_400 = refs_of(zf.read(EVENTS))
        # Build 401: the crack is asked (cracked) and taken (takeCrack) - the take is what spends it.
        # takeCrack answers both questions at once - is this click a crack, and is that crack now spent -
        # and answering them in one call is what makes the click and the spend the same moment.
        for wanted, why in (("takeCrack", "the click that follows a solved panel must be let through, and"
                                          " the crack must belong to exactly one click"),):
            if not any(r[1] == wanted and r[0] == "dev/settledlands/chest/ChestLock397"
                       for r in event_refs_400):
                problems.append("ChestGraph396Events never calls ChestLock397#%s - %s" % (wanted, why))
        if not any(r[2].endswith("Z)V") and r[1] == "remember" for r in event_refs_400):
            problems.append("ChestGraph396Events does not pass the crack on to remember(..., boolean) - a"
                            " chest opened by a cracked panel would run the «открытие» chain")
        # Build 401: the click moved out of the panel and into ChestOpen401, because the panel's close came
        # BEFORE the «взлом» chain. The panel must not click its chest at all any more.
        panel_400, panel_refs_400 = refs_of(zf.read(PANEL_SCREEN))
        if any(r[1] == "useItemOn" for r in panel_refs_400):
            problems.append("HackPanel377 still clicks its chest - that click opened the window before the"
                            " «Реплика» of the «взлом» chain, which is the report of build 401")
        if OPEN_CLASS not in names:
            problems.append("the jar has no %s - nothing would open a cracked chest" % OPEN_CLASS)
        else:
            opener, opener_refs = refs_of(zf.read(OPEN_CLASS))
            for name in ("arm", "watch", "clear"):
                if name not in methods_of(opener):
                    problems.append("ChestOpen401 has no %s() - the click of a cracked chest has no door"
                                    " through it" % name)
            if not any(r[1] == "useItemOn"
                       and r[0] == "net/minecraft/client/multiplayer/MultiPlayerGameMode"
                       for r in opener_refs):
                problems.append("ChestOpen401 never clicks the chest through MultiPlayerGameMode#useItemOn -"
                                " the solved panel would have no way to open anything")
            if not any("BlockHitResult" in r[2] for r in opener_refs):
                problems.append("ChestOpen401 does not build the BlockHitResult a click needs")
            if not any(r[1] == "screen" and r[0] == "net/minecraft/client/Minecraft"
                       for r in opener_refs):
                problems.append("ChestOpen401 never reads Minecraft#screen - it cannot tell the chain's"
                                " window from a closed one, and the click would fire on top of the«Реплика»")
        # Build 402: the two sockets are two moments, and neither stands in for the other. A close is the
        # later one - `ChestGraph396#run` may only ever follow «открытие» (chainStart), and the pre-opening
        # chain - `runCrack` - may only ever follow «взлом» (hackChainStart). Build 401 broke exactly this in
        # both directions: runCrack fell back to chainStart when «взлом» was empty (the author's own card), and
        # the close was skipped for a crack.
        graph_cls, graph_refs = refs_of(zf.read(GRAPH))
        run_src = instruction_listing_all(zf.read(GRAPH), "run")
        crack_src = instruction_listing_all(zf.read(GRAPH), "runCrack")
        if run_src is None or crack_src is None:
            problems.append("ChestGraph396 has no run()/runCrack() - a chest could not speak at all")
        else:
            if "ChestGraph396.closeChainStart(" not in run_src:
                problems.append("ChestGraph396#run never asks closeChainStart() - the line of a closed chest"
                                " would not play")
            if "ChestGraph396.hackChainStart(" in run_src:
                problems.append("ChestGraph396#run follows the card's «взлом» socket - the close is the"
                                " «открытие» moment (build 402), the crack's chain plays before the opening")
            if "ChestGraph396.hackChainStart(" not in crack_src:
                problems.append("ChestGraph396#runCrack never follows the card's «взлом» socket")
            close_src = instruction_listing_all(zf.read(GRAPH), "closeChainStart")
            if close_src is None or "ChestGraph396.chainStart(" not in close_src:
                problems.append("ChestGraph396#closeChainStart does not follow the card's «открытие» socket")
            if "ChestGraph396.chainStart(" in crack_src or "closeChainStart(" in crack_src:
                problems.append("ChestGraph396#runCrack falls back to the «открытие» socket - that is the"
                                " build-401 rule that played the author's «Реплика» before the chest opened"
                                " («я бы хотел чтобы реплика проигрывалась ... уже после открытия инвентаря"
                                " сундука и его закрытия»)")
        if READY_PACKET not in names:
            problems.append("the jar has no chest_ready packet - the server could not tell the client when"
                            " the chain of a cracked chest is done")
        elif b"chest_ready" not in zf.read(READY_PACKET):
            problems.append("%s does not declare chest_ready" % READY_PACKET)
        for name in (PANEL_PACKET, SOLVED_PACKET):
            if name not in names:
                problems.append("the jar has no %s" % name)
        for packet, needle in ((PANEL_PACKET, b"chest_panel"), (SOLVED_PACKET, b"chest_hack")):
            if packet in names and needle not in zf.read(packet):
                problems.append("%s does not declare %s" % (packet, needle.decode()))
        screen, screen_refs = refs_of(zf.read(PANEL_SCREEN))
        if "openForChest" not in methods_of(screen):
            problems.append("HackPanel377 has no openForChest() - a locked chest would have no panel,"
                            " and this mod would grow a second lock screen instead of reusing this one")
        if not any(r[1] == "sendSolved" and r[0] == "dev/settledlands/chest/ChestNet397"
                   for r in screen_refs):
            problems.append("a solved panel never answers a chest - the door's packet would open nothing")

        # 15. a locked chest is refused where it would have been opened, and cannot be broken open either.
        event_refs_397 = {(r[1], r[0]) for r in event_refs}
        for name, owner in (("setCanceled", "net/neoforged/neoforge/event/entity/player/"
                                            "PlayerInteractEvent$RightClickBlock"),
                            ("setCancellationResult", "net/neoforged/neoforge/event/entity/player/"
                                                      "PlayerInteractEvent$RightClickBlock"),
                            ("setCanceled", "net/neoforged/neoforge/event/level/BlockEvent$BreakEvent")):
            if (name, owner) not in event_refs_397:
                problems.append("ChestGraph396Events never calls %s on %s - a locked chest could be opened"
                                " or broken open, and the lock would be a decoration"
                                % (name, owner.rsplit("/", 1)[-1]))
        if not any(r[1] == "sendPanel" and r[0] == "dev/settledlands/chest/ChestNet397"
                   for r in event_refs):
            problems.append("ChestGraph396Events never sends the panel - a locked chest would refuse the"
                            " click and stay silent")
        if not any(r[1] == "panel" and r[0] == "dev/settledlands/chest/ChestLock397"
                   for r in event_refs):
            problems.append("ChestGraph396Events never asks ChestLock397#panel - the numbers on the card"
                            " would not reach the player")
    return problems


EXPECT_CLOSE = {
    # The author's card: «открытие» → «Реплика #1», «взлом» empty.
    "AUTHOR": "next=1 falseNext=-1 close=1 hack=none",
    # Both sockets: two moments, two nodes - a close never borrows the crack's chain.
    "BOTH": "next=1 falseNext=2 close=1 hack=2",
    "EMPTY": "close=none hack=none",
}

EXPECT_OPEN = {
    "CRACK_BEFORE": "false",
    "CRACK_ONE": "true",
    "CRACK_OTHER": "false",
    "CRACK_SPENT": "cracked=true take1=true take2=false",
    "ARM_BEFORE": "false",
    "ARM_READY": "true",
    "ARM_CLEARED": "false",
    "WATCH_QUIET": "ok",
}


def run_probe(jar):
    return run_one(jar, PROBE, PROBE_CLASS)


def run_one(jar, source, cls):
    if not source.is_file():
        return None, ["the probe %s is missing" % source]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(source)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / (cls + ".class")).is_file():
            return None, ["the probe did not compile: %s"
                          % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), cls],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return None, ["the probe crashed:\n%s" % output[-800:]]
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        return got, []


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("chest_graph_check: no such jar %s" % jar)
        return 2
    problems = structural(jar)
    got, more = run_probe(jar)
    problems += more
    if got is not None:
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    lock, more_lock = run_one(jar, LOCK_PROBE, LOCK_PROBE_CLASS)
    problems += more_lock
    if lock is not None:
        for key, want in EXPECT_LOCK.items():
            if lock.get(key) != want:
                problems.append("lock %s is %r, expected %r" % (key, lock.get(key), want))
    opened, more_open = run_one(jar, OPEN_PROBE, OPEN_PROBE_CLASS)
    problems += more_open
    if opened is not None:
        for key, want in EXPECT_OPEN.items():
            if opened.get(key) != want:
                problems.append("open %s is %r, expected %r" % (key, opened.get(key), want))
    closed, more_close = run_one(jar, CLOSE_PROBE, CLOSE_PROBE_CLASS)
    problems += more_close
    if closed is not None:
        for key, want in EXPECT_CLOSE.items():
            if closed.get(key) != want:
                problems.append("close %s is %r, expected %r" % (key, closed.get(key), want))
    if problems:
        print("chest_graph_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("chest_graph_check: OK  %s  (a chest has the door's graph - one card of this mod, its two"
          " outputs as sockets on it, the stock palette and the stock interpreter - and the lock of that"
          " card is a setting: three sliders and a toggle the graph can edit, honoured by the chest's own"
          " panel; its card's three sliders write the numbers that panel asks for, and the lift of a solved"
          " panel is spent by the click that follows it, and that click waits for the chain; its two sockets are"
          " two moments - «взлом» before the opening, «открытие» on the close)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
