#!/usr/bin/env python3
"""Gate: the four reports of build 398 are answered where the player will see them.

The author's four lines, and what this gate refuses to let go quiet:

  1. «сейчас тумблер "открыта/закрыта" визуально в правом окне изменяется только при перезаходе в граф.
     Так-же и в графе сундука». - Both inspectors must write the button's word **inside the click**, so the
     word follows the node instead of waiting for the next build of the sidebar;
  2. «после взлома сундука у нас не открывается инвентарь сундука». - Three independent guards: the panel
     closes only while it is the screen in front (`onClose` reads `Minecraft#screen`), it asks again while no
     container is open (`containerMenu` against `inventoryMenu`), and the server lifts the lock of that chest
     for a few seconds the moment the panel is solved (`ChestLock397#grant`), so one more click always opens it;
  3. «сейчас нельзя вытаскивать из синего узла провод». - The chest card must be a value source: every half of
     a blue pin is keyed on `PursuitGraphNode272#isValueSourceNode`, so the chest has to be in that predicate,
     not painted next to it;
  4. «добавь ноде главной у сундука узел "взлом" а главной ноде двери узел "открытие"». - Each card carries a
     second execution socket: drawn by the card, wired by `CardEventPin398Mixin` (drag, drop, cut, cable), and
     stored in `Node#falseNext` - **never** in `Node#next`, which is the first socket's chain. The runtimes read
     their own field: `ChestGraph396#hackChainStart` for a cracked chest, `DoorGraph382#openChainStart` for a
     door opened by hand.

Behaviourally, `tools/harness/CardSockets398Probe.java` runs on the real classes from the jar and answers the
one question that decides whether the feature is worth anything - are the two chains two fields:

    SECOND fields=next:true falseNext:true apart=true
    FIRST next=1 falseNext=2
    FALLBACK wired=1
    DOOR second=2 first=1 apart=true
    NAMES chest=gui.settledlands.chest.pin_hack door=gui.settledlands.door.pin_open
    STOCK untouched=true
    GRANT mine=true other=false

    python3 tools/card_sockets_check.py settledlands-0.8.0-build398-card-sockets.jar

Exit 0 = the toggles follow the click, a cracked chest can open, the blue socket of a chest can be wired, and
both cards carry their second event socket without touching the first chain.
"""
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = ROOT.parent / ".venv-jdk/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "CardSockets398Probe.java"
PROBE_CLASS = "CardSockets398Probe"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("stubs396-out", "api", "api5", "api4", "api3", "stubs-out", "api2")]

PIN = "dev/settledlands/npc/client/CardEventPin398.class"
PIN_MIXIN = "dev/settledlands/mixin/CardEventPin398Mixin.class"
DOOR_SIGNAL = "dev/settledlands/door/DoorSignal398.class"
DOOR_CARD = "dev/settledlands/npc/client/DoorCard382.class"
CHEST_CARD = "dev/settledlands/npc/client/ChestCard396.class"
DOOR_USE = "dev/settledlands/mixin/DoorUse373Mixin.class"
DOOR_GRAPH = "dev/settledlands/door/DoorGraph382.class"
CHEST_GRAPH = "dev/settledlands/chest/ChestGraph396.class"
CHEST_EVENTS = "dev/settledlands/chest/ChestGraph396Events.class"
CHEST_PENDING = "dev/settledlands/chest/ChestGraph396Events$Pending.class"
CHEST_NET = "dev/settledlands/chest/ChestNet397.class"
CHEST_LOCK = "dev/settledlands/chest/ChestLock397.class"
PANEL = "dev/settledlands/door/HackPanel377.class"
DOOR_ROWS = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
CHEST_ROWS = "dev/settledlands/mixin/NpcChestRows396Mixin.class"
VALUE_PIN = "dev/settledlands/npc/client/PursuitGraphNode272.class"

NEW_KEYS = {
    "gui.settledlands.door.pin_open": ("открытие", "opening"),
    "gui.settledlands.chest.pin_hack": ("взлом", "hack"),
    "message.settledlands.chest.again": ("Замок снят: нажми на сундук ещё раз",
                                         "The lock is off: click the chest again"),
}

# The corrected hint lines: a card with two execution sockets must not describe one.
CHANGED_KEYS = {
    "gui.settledlands.door.pin_out_hint": ("Справа: взлом, открытие и синий выход",
                                           "On the right: hack, opening and the blue output"),
    "gui.settledlands.chest.pin_hint": ("Справа: открытие, взлом и синий выход",
                                        "On the right: the opening, the hack and the blue output"),
}

EXPECT = {
    "SECOND": "fields=next:true falseNext:true apart=true",
    "FIRST": "next=1 falseNext=2",
    "FALLBACK": "wired=1",
    "DOOR": "second=2 first=1 apart=true",
    "NAMES": "chest=gui.settledlands.chest.pin_hack door=gui.settledlands.door.pin_open",
    "STOCK": "untouched=true",
    "GRANT": "mine=true other=false",
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


def fields_of(parsed):
    cp = parsed["cp"]
    return {cp[ni][1] for _f, ni, _d, _attrs in parsed["fields"]}


def int_constant(parsed, name):
    """The value of a `static final int` field, read from its ConstantValue attribute."""
    cp = parsed["cp"]
    for _acc, ni, _desc, attrs in parsed["fields"]:
        if cp[ni][1] != name:
            continue
        for an, data in attrs:
            if cp[an][1] != "ConstantValue" or len(data) < 2:
                continue
            index = struct.unpack(">H", data[:2])[0]
            entry = cp[index]
            if entry and entry[0] == 3:
                return struct.unpack(">i", entry[1])[0]
        return None
    return None


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (PIN, PIN_MIXIN, DOOR_SIGNAL):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        # --- 1. the toggles write their own word inside the click -----------------
        for path, helper, reader, what in (
                (DOOR_ROWS, "settledlands$sealWord", ("dev/settledlands/door/DoorGraph382", "sealed"),
                 "door"),
                (CHEST_ROWS, "settledlands$lockWord", ("dev/settledlands/chest/ChestLock397", "stateKey"),
                 "chest")):
            parsed, refs = refs_of(zf.read(path))
            if helper not in methods_of(parsed):
                problems.append("%s: no %s() - the toggle's word would still be written once, and the"
                                " author would keep seeing the old state until he reopens the graph"
                                % (what, helper))
            if not any(r[0] == "net/minecraft/client/gui/components/Button" and r[1] == "setMessage"
                       for r in refs):
                problems.append("%s: the toggle never calls Button#setMessage" % what)
            if not any(r[1] == "translatable" for r in refs):
                problems.append("%s: the toggle's word is not read from the language files" % what)
            if not any(r[0] == reader[0] and r[1] == reader[1] for r in refs):
                problems.append("%s: the toggle's word does not read the node (%s#%s) - it would show"
                                " whatever it was built with" % ((what,) + reader))

        # --- 2. a cracked chest really opens -------------------------------------
        panel, panel_refs = refs_of(zf.read(PANEL))
        panel_methods = methods_of(panel)
        if "onClose" not in panel_methods:
            problems.append("HackPanel377 does not override onClose() - a solved panel can close the chest's"
                            " window that already replaced it, which is the report of build 398")
        if not any(r[1] == "screen" and r[0] == "net/minecraft/client/Minecraft" for r in panel_refs):
            problems.append("HackPanel377#onClose does not read Minecraft#screen - it cannot tell whether it"
                            " is still the screen in front")
        if "settledlands$askAgain" not in panel_methods:
            problems.append("HackPanel377 has no settledlands$askAgain() - the answer to a chest is sent once"
                            " and never repeated")
        for wanted in ("containerMenu", "inventoryMenu"):
            if not any(r[1] == wanted for r in panel_refs):
                problems.append("HackPanel377 never reads %s - it cannot tell whether a container opened, so"
                                " it cannot know the chest's window is late" % wanted)
        lock, lock_refs = refs_of(zf.read(CHEST_LOCK))
        for name in ("grant", "granted"):
            if name not in methods_of(lock):
                problems.append("ChestLock397 has no %s() - a solved panel would have the server's window as"
                                " its only way in" % name)
        if "GRANTED" not in fields_of(lock):
            problems.append("ChestLock397 keeps no lifted locks - nothing would ever time out")
        net, net_refs = refs_of(zf.read(CHEST_NET))
        if not any(r[1] == "grant" and r[0] == "dev/settledlands/chest/ChestLock397" for r in net_refs):
            problems.append("ChestNet397 never lifts the lock - a client whose window does not arrive is stuck"
                            " with a chest it already cracked")
        if not any(r[1] == "remember" and r[0] == "dev/settledlands/chest/ChestGraph396Events"
                   for r in net_refs):
            problems.append("ChestNet397 never hands the opening to ChestGraph396Events")
        if not any(r[1] == "remember" and r[2].endswith("Z)V") for r in net_refs):
            problems.append("ChestNet397 never marks the opening as a CRACK (remember(..., boolean)) - the"
                            " «взлом» chain would never know it was one")
        events, event_refs = refs_of(zf.read(CHEST_EVENTS))
        if not any(r[1] == "run" and r[2].endswith("Z)Z") for r in event_refs):
            problems.append("ChestGraph396Events does not pass the crack on to ChestGraph396#run")
        pending, _pending_refs = refs_of(zf.read(CHEST_PENDING))
        if "cracked" not in fields_of(pending):
            problems.append("ChestGraph396Events$Pending has no cracked field - the moment of the close would"
                            " run the wrong chain")

        # --- 3. the blue socket of a chest can be dragged ------------------------
        pin_value, value_refs = refs_of(zf.read(VALUE_PIN))
        value_methods = methods_of(pin_value)
        if "isChestValueNode" not in value_methods:
            problems.append("PursuitGraphNode272 has no isChestValueNode() - the blue socket of a chest's card"
                            " would be painted and unreachable")
        if not any(r[1] == "isChestNode" and r[0] == "dev/settledlands/chest/ChestGraph396"
                   for r in value_refs):
            problems.append("PursuitGraphNode272 never asks whether a node is the chest's card")
        if not any(r[1] == "isChestValueNode" and r[0] == "dev/settledlands/npc/client/PursuitGraphNode272"
                   for r in value_refs):
            problems.append("isValueSourceNode does not include the chest - every half of a blue pin (drag,"
                            " drop, cut, stored link, drawn cable) is keyed on that one predicate")

        # --- 4. the second socket, and the field it must never write -------------
        card, card_refs = refs_of(zf.read(PIN))
        card_methods = methods_of(card)
        for name in ("isCard", "secondChain", "setSecondChain", "labelKey", "cardAt", "wireAt",
                     "hasWire", "pinScreenX", "pinScreenY"):
            if name not in card_methods:
                problems.append("CardEventPin398 has no %s()" % name)
        first = int_constant(card, "FIRST_ROW")
        second = int_constant(card, "SECOND_ROW")
        if first is None or second is None or second <= first:
            problems.append("CardEventPin398: the second row (%r) is not below the first (%r) - the two"
                            " sockets would be drawn on top of each other" % (second, first))
        if not any(r[1] == "falseNext" and r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
                   for r in card_refs):
            problems.append("CardEventPin398 never touches Node#falseNext - the second chain would not survive"
                            " a save (the lesson of build 384)")
        if any(r[1] == "next" and r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
               for r in card_refs):
            problems.append("CardEventPin398 writes Node#next - the second socket would steal the first chain")

        mixin, mixin_refs = refs_of(zf.read(PIN_MIXIN))
        mixin_methods = methods_of(mixin)
        for name in ("settledlands$grabCardSocket", "settledlands$dropCardSocket",
                     "settledlands$drawCardSockets"):
            if name not in mixin_methods:
                problems.append("CardEventPin398Mixin has no %s() - the socket would be painted only" % name)
        if not any(r[1] == "curve" and r[0] == "dev/settledlands/mixin/CardEventPin398Mixin"
                   for r in mixin_refs):
            problems.append("CardEventPin398Mixin does not use the screen's own curve() - its cable would not"
                            " match the stock cables")
        if any(r[1] == "connect" for r in mixin_refs):
            problems.append("CardEventPin398Mixin calls NpcDialogueGraph#connect - for a card that writes"
                            " Node#next, so the second socket would overwrite the first chain")
        if not any(r[1] == "settledlands$remember" for r in mixin_refs):
            problems.append("the second socket does not take the screen's undo point")
        if not any(r[1] == "settledlands$changed" for r in mixin_refs):
            problems.append("the second socket does not mark the graph dirty")

        mixins = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        if "CardEventPin398Mixin" not in mixins.get("client", []):
            problems.append("CardEventPin398Mixin is not in the client mixin list - the socket would be drawn"
                            " and never wired")
        if "CardEventPin398Mixin" in mixins.get("mixins", []):
            problems.append("CardEventPin398Mixin is on the common list - it touches client classes")

        # both cards draw the socket and its label
        for path, why in ((DOOR_CARD, "door"), (CHEST_CARD, "chest")):
            parsed, refs = refs_of(zf.read(path))
            if not any(r[0] == "dev/settledlands/npc/client/CardEventPin398" for r in refs):
                problems.append("%s's card does not read CardEventPin398 - the second socket would not be"
                                " drawn or labelled" % why)
            if not any(r[1] == "socket" for r in refs):
                problems.append("%s's card draws no round socket" % why)

        # the runtimes read their own field
        door_graph, door_graph_refs = refs_of(zf.read(DOOR_GRAPH))
        if "openChainStart" not in methods_of(door_graph):
            problems.append("DoorGraph382 has no openChainStart() - a door opened by hand would run nothing")
        if not any(r[1] == "falseNext" and r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
                   for r in door_graph_refs):
            problems.append("DoorGraph382 never reads Node#falseNext - the «открытие» socket has no field")
        chest_graph, chest_graph_refs = refs_of(zf.read(CHEST_GRAPH))
        if "hackChainStart" not in methods_of(chest_graph):
            problems.append("ChestGraph396 has no hackChainStart() - a cracked chest would run the wrong"
                            " chain")
        if not any(r[1] == "falseNext" and r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
                   for r in chest_graph_refs):
            problems.append("ChestGraph396 never reads Node#falseNext - the «взлом» socket has no field")

        use, use_refs = refs_of(zf.read(DOOR_USE))
        if not any(r[1] == "open" and r[0] == "dev/settledlands/door/DoorSignal398" for r in use_refs):
            problems.append("DoorUse373Mixin never calls DoorSignal398#open - a door opened by hand is silent")
        if not any(r[1] == "isOpen" and r[0] == "dev/settledlands/door/Doors368" for r in use_refs):
            problems.append("DoorUse373Mixin does not compare the door's state - a refused click or a closing"
                            " door would fire the «открытие» chain")
        signal, signal_refs = refs_of(zf.read(DOOR_SIGNAL))
        if not any(r[1] == "start" and r[0] == "dev/settledlands/npc/BlockTrigger351" for r in signal_refs):
            problems.append("DoorSignal398 does not reach BlockTrigger351#start - the chain would be skipped,"
                            " not run (build 393)")
        if not any(r[1] == "openChainStart" and r[0] == "dev/settledlands/door/DoorGraph382"
                   for r in signal_refs):
            problems.append("DoorSignal398 never asks the card's second socket where to start")

        # --- the language ------------------------------------------------------
        for path, index in (("assets/settledlands/lang/ru_ru.json", 0),
                            ("assets/settledlands/lang/en_us.json", 1)):
            strings = json.loads(zf.read(path).decode("utf-8"))
            short = path.rsplit("/", 1)[-1]
            for key, values in NEW_KEYS.items():
                if strings.get(key) != values[index]:
                    problems.append("%s: %s is %r, expected %r"
                                    % (short, key, strings.get(key), values[index]))
            for key, values in CHANGED_KEYS.items():
                if strings.get(key) != values[index]:
                    problems.append("%s: %s still describes one socket (%r)"
                                    % (short, key, strings.get(key)))
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
        print("card_sockets_check: no such jar %s" % jar)
        return 2
    problems = structural(jar)
    got, more = run_probe(jar)
    problems += more
    if got is not None:
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("%s is %r, expected %r" % (key, got.get(key), want))
    if problems:
        print("card_sockets_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("card_sockets_check: OK  %s  (the toggles follow the click, a cracked chest opens, the blue socket"
          " of a chest is wireable, and both cards carry a second event socket without touching the first"
          " chain)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
