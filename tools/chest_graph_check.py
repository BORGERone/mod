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

PROBE_CLASS = "ChestGraph396Probe"

EXPECT = {
    "TYPE": "own=1 stock=false",
    "CARD": "nodes=1 card=true entry=true",
    "CHAIN": "unwired=none wired=1 stop=none",
    "MEMORY": "count=3 opener=BORGER long=32",
    "STRIP": "dropped=2 left=3 entry=kept",
    "ID": "in=true back=true stable=true",
}


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
            for key in LANG_KEYS:
                if key not in strings or not strings[key]:
                    problems.append("%s is missing %s" % (path.rsplit("/", 1)[-1], key))
    return problems


def run_probe(jar):
    if not PROBE.is_file():
        return None, ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / (PROBE_CLASS + ".class")).is_file():
            return None, ["the probe did not compile: %s"
                          % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), PROBE_CLASS],
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
    if problems:
        print("chest_graph_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("chest_graph_check: OK  %s  (a chest has the door's graph: one card of this mod, its two"
          " outputs as sockets on it, the stock palette and the stock interpreter)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
