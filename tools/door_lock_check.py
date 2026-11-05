#!/usr/bin/env python3
"""Gate: a locked door is closed to players and monsters, and open to guides.

Build 368 has two halves that are easy to ship half-done: a lock nothing enforces (the door opens on a
right click anyway, or a zombie removes it) and a guide that never gets through its own door (the path
finder treats a closed door as a wall, so the pass that opens doors is never reached).

What this gate asks the shipped jar:

  * the classes of the mechanic are there: the store, the rules, the key item, the guide pass, and the
    two hooks that need a class (the path finder's answer, the destroyed block);
  * both hooks are **common** mixins - the lock lives on the server, and the level is a server class;
  * the guards are wired to the right calls: the interact event, the break event, `destroyBlock`, and
    `GuideNpc` for the path finder's answer;
  * the key is registered on the **mod bus** (`RegisterEvent` + `Registries.ITEM`), because an item that
    is created but never registered is an item that does not exist;
  * the asset and the four strings of the item are in the jar, so the key has a name and a picture;
  * the format of the lock file survives a write and a read, and the null-safe answers of the rules are
    the quiet ones - `tools/harness/DoorLock368Probe.java` is compiled against this jar and run.

    python3 tools/door_lock_check.py settledlands-0.8.0-build368-door-lock.jar

Exit 0 = the lock is enforced on both sides and the guides still walk through.
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
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "DoorLock368Probe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

STORE = "dev/settledlands/door/DoorStore368.class"
DOORS = "dev/settledlands/door/Doors368.class"
KEY = "dev/settledlands/door/DoorKey368.class"
EVENTS = "dev/settledlands/door/DoorEvents368.class"
PASS = "dev/settledlands/door/NpcDoors368.class"
MIX_PATH = "dev/settledlands/mixin/GuideNpcDoor368Mixin.class"
MIX_BREAK = "dev/settledlands/mixin/DoorBreak368Mixin.class"
MODEL = "assets/settledlands/models/item/door_key.json"
TEXTURE = "assets/settledlands/textures/item/door_key.png"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"
LANG = ("item.settledlands.door_key", "message.settledlands.door.locked",
        "message.settledlands.door.key_on", "message.settledlands.door.key_off")
EXPECT = {"FORMAT": "keys=2 round=2 has=true empty=0",
          "DOOR": "isDoor=false isOpen=false parts=0",
          "KEY": "isKey=false",
          "PARSE": "dim=minecraft:overworld x=12 y=64 z=-30 junk=true"}


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


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for needed in (STORE, DOORS, KEY, EVENTS, PASS, MIX_PATH, MIX_BREAK, MODEL, TEXTURE):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        config = json.loads(z.read("settledlands.mixins.json").decode("utf-8"))
        common = config.get("mixins", [])
        for needed in ("GuideNpcDoor368Mixin", "DoorBreak368Mixin"):
            if needed not in common:
                problems.append("%s is not a common mixin - the server is where a door is locked" % needed)

        key_cls, key_refs = parse(z.read(KEY))
        if not any(r[1] == "register" and r[0] and r[0].endswith("registries/RegisterEvent")
                   for r in key_refs):
            problems.append("the key item is never registered - settledlands:door_key would not exist")
        if not any(r[1] == "ITEM" for r in key_refs):
            problems.append("the key is not registered into Registries.ITEM")
        if b"EventBusSubscriber" not in z.read(KEY):
            problems.append("the key has no bus subscriber - nothing would run the registration")

        events_cls, event_refs = parse(z.read(EVENTS))
        if not any(r[1] == "setCanceled" and r[0]
                   and "event/entity/player/PlayerInteractEvent" in r[0]
                   for r in event_refs):
            problems.append("a right click on a locked door is not cancelled - it would just open")
        if not any(r[1] == "setCanceled" and r[0] and r[0].endswith("event/level/BlockEvent$BreakEvent")
                   for r in event_refs):
            problems.append("a locked door can still be broken - the break event is not cancelled")
        if not any(r[1] == "isLocked" and r[0] and r[0].endswith("door/DoorStore368")
                   for r in event_refs):
            problems.append("the rules never ask the store whether the place is locked")
        # Build 373: setUseBlock/setUseItem do NOT exist on PlayerInteractEvent in NeoForge 1.21.1.
        # Build 371 called them, threw NoSuchMethodError on the click, refused nothing, and the door
        # opened for a tick on every click - long enough to lean on it and walk through. The gate now
        # forbids those two names outright, so the mistake cannot come back by accident.
        for name in names:
            if not name.startswith("dev/settledlands/"):
                continue
            data = z.read(name)
            for banned in (b"setUseBlock", b"setUseItem"):
                if banned in data:
                    problems.append("%s calls %s, which PlayerInteractEvent does not have in 1.21.1 - "
                                    "that is build 371's silent refusal"
                                    % (name, banned.decode()))
                    break
        if not any(r[1] == "setCancellationResult" for r in event_refs):
            problems.append("a refused door click never says what it means - setCancellationResult("
                            "InteractionResult.FAIL) is the answer the game reads")
        if not any(r[1] == "FAIL" and r[0] and r[0].endswith("world/InteractionResult")
                   for r in event_refs):
            problems.append("the refused interaction result is not InteractionResult.FAIL")
        # Build 375: on 1.21.1 the client opens a door itself unless the RightClickBlock event is
        # cancelled (MultiPlayerGameMode#performUseItemOn returns early only when it is). That local
        # open is the single tick the author kept walking through, so the client has to refuse doors
        # outright and leave the decision to the server.
        if not any(r[1] == "SUCCESS" and r[0] and r[0].endswith("world/InteractionResult")
                   for r in event_refs):
            problems.append("the client never refuses a door click - the door opens locally for a tick "
                            "before the server answers, and that tick is walkable")
        if not any(r[1] == "sendBlockUpdated" for r in event_refs):
            problems.append("the client is never told the door is still closed - that is the door "
                            "that 'opens for a second'")

        pass_cls, pass_refs = parse(z.read(PASS))
        if not any(r[1] == "setOpen" and r[0] and r[0].endswith("door/Doors368") for r in pass_refs):
            problems.append("the guide pass never opens a door")
        if not any(r[1] == "getAllEntities" for r in pass_refs):
            problems.append("the guide pass does not walk the entities of a level - it would open "
                            "nothing")
        if not any(r[0] == "dev/settledlands/npc/GuideNpc" for r in pass_refs):
            problems.append("the guide pass does not call anything on a guide")

        mix_path_cls, _ = parse(z.read(MIX_PATH))
        if "canOpenDoors" not in " ".join(str(v[1]) for v in mix_path_cls["cp"] if v and v[0] == 1):
            problems.append("the path finder's question is not answered - a guide would never path "
                            "through a closed door")
        if b"GuideNpc" not in z.read(MIX_PATH):
            problems.append("the answer is not bound to GuideNpc")

        # Build 368 did not start: the guard injected into a method 1.21.1 only has as a default on the
        # LevelWriter interface, so there was no target and mixin killed the boot. A game-class mixin
        # therefore has to be optional - `require = 0` - so a signature this exact version lacks is
        # skipped instead of becoming a failed launch. Mod classes are excluded: they are in the jar and
        # a mixin on them must never be silent.
        for name in common:
            if not name.endswith("368Mixin"):
                continue
            data = z.read("dev/settledlands/mixin/%s.class" % name)
            if b"net/minecraft/" not in data and b"net/neoforged/" not in data:
                continue
            if b"require" not in data:
                problems.append("%s injects into a game class without require = 0 - build 368 is what "
                                "happens when the target signature is not the one the game has" % name)

        mix_break_cls, mix_break_refs = parse(z.read(MIX_BREAK))
        break_bytes = z.read(MIX_BREAK)
        for overload in (b"destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                         b"destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z",
                         b"destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z"):
            if overload not in break_bytes:
                problems.append("the break guard does not cover %s" % overload.decode())
        if b"removeBlock(Lnet/minecraft/core/BlockPos;Z)Z" not in break_bytes:
            problems.append("the break guard does not cover removeBlock - an explosion would still "
                            "take a locked door")
        if not any(r[1] == "locked" and r[0] and r[0].endswith("door/DoorEvents368")
                   for r in mix_break_refs):
            problems.append("the destroyed block is not checked against the lock - a zombie could "
                            "still bash a locked door away")
        if b"destroyBlock" not in z.read(MIX_BREAK):
            problems.append("the break guard is not bound to Level#destroyBlock")

        # Build 370: an item nobody can reach is an item that does not exist for the author. The tab of
        # the mod is filled by its own hand-written list, so the key has to be added by a listener.
        tab_found = False
        for name in names:
            if not name.startswith("dev/settledlands/door/") or not name.endswith(".class"):
                continue
            data = z.read(name)
            if (b"BuildCreativeModeTabContentsEvent" in data
                    and b"settledlands:guide" in data
                    and b"accept" in data):
                tab_found = True
        if not tab_found:
            problems.append("no class adds the key to the mod's creative tab - the item would exist "
                            "but never be visible (settledlands:guide)")

        # Build 371: the lock is a fact of the world, not one event's opinion.
        # Build 373: the refusal has to sit where the door is opened, not only on the event.
        door_use = "dev/settledlands/mixin/DoorUse373Mixin.class"
        if door_use not in names:
            problems.append("no DoorBlock guard: a locked door would open for a tick on every click, "
                            "which is exactly what the author can walk through")
        else:
            data = z.read(door_use)
            if b"useWithoutItem(BlockState;Level;BlockPos;Player;BlockHitResult;)" not in data \
                    and b"useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;" not in data:
                problems.append("the DoorBlock guard does not bind to useWithoutItem")
            if b"require" not in data:
                problems.append("the DoorBlock guard is required - a signature this game lacks would "
                                "stop the launch (build 368)")
            if "DoorUse373Mixin" not in common:
                problems.append("DoorUse373Mixin is not in the common mixin list")

        guard = "dev/settledlands/door/DoorGuard371.class"
        if guard not in names:
            problems.append("no guard pass: a locked door that something else opened would stay open")
        else:
            guard_cls, guard_refs = parse(z.read(guard))
            if not any(r[1] == "setOpen" and r[0] and r[0].endswith("door/Doors368")
                       for r in guard_refs):
                problems.append("the guard never closes a door")

        # Build 376: the client may not refuse every door - that was 375, and the author paid for it with
        # a round trip on every ordinary door ("the top half opens with a delay"). It has to know which
        # doors are locked instead: a payload, a registration, and a client that asks both questions.
        sync = "dev/settledlands/door/DoorSync375.class"
        net = "dev/settledlands/door/DoorNet375.class"
        for needed, why in ((sync, "no lock sync class: the client could not tell a locked door from an "
                                   "ordinary one"),
                            (net, "no payload registration: the lock list would never reach a client")):
            if needed not in names:
                problems.append(why)
        if sync in names:
            sync_cls, sync_refs = parse(z.read(sync))
            if not any(r[0] and r[0].endswith("custom/CustomPacketPayload$Type")
                       and r[1] == "<init>" for r in sync_refs):
                problems.append("the lock sync is not a CustomPacketPayload - NeoForge would not send it")
            if not any(r[0] and r[0].endswith("network/PacketDistributor") for r in sync_refs):
                problems.append("nothing is ever sent: the lock list stays on the server")
        if net in names:
            net_cls, net_refs = parse(z.read(net))
            if not any(r[1] == "playToClient" and r[0] and r[0].endswith("registration/PayloadRegistrar")
                       for r in net_refs):
                problems.append("the payload is never registered - NeoForge refuses unregistered payloads")
            if not any(r[0] and r[0].endswith("network/event/RegisterPayloadHandlersEvent")
                       for r in net_refs):
                problems.append("the registration is not on the mod bus")
        if not any(r[1] == "synced" and r[0] and r[0].endswith("door/DoorSync375") for r in event_refs):
            problems.append("the client refuses doors without asking whether it knows the locks - that is "
                            "build 375, and its cost was a round trip on every door")
        if not any(r[1] == "isLocked" and r[0] and r[0].endswith("door/DoorSync375") for r in event_refs):
            problems.append("the client never checks a door against the synced locks")
        if not any(r[1] == "getEntity"
                   and r[0] and r[0].endswith("event/entity/player/PlayerEvent$PlayerLoggedInEvent")
                   for r in event_refs):
            problems.append("a joining player is never sent the lock list - every door would stay slow "
                            "until a door is locked or unlocked")

        # Build 372: a build that cannot explain itself cannot be fixed. The jar has to be able to say
        # in the log whether the key registered, whether a click was refused, and what the guard closed.
        # Build 374: a guard that never bound and a guard that bound and refused nothing look exactly
        # the same from the outside. The refusal inside the door has to say that it ran.
        for name, must in ((KEY, b"settledlands:door_key registered"),
                           (EVENTS, b"was refused"),
                           (EVENTS, b"is not a DoorBlock"),
                           (EVENTS, b"useWithoutItem was reached"),
                           (EVENTS, b"the lock is known to this client"),
                           ("dev/settledlands/mixin/DoorUse373Mixin.class", b"sawDoorUse"),
                           ("dev/settledlands/door/DoorGuard371.class", b"the guard closed")):
            if name not in names or must not in z.read(name):
                problems.append("%s does not report %r - a report of 'nothing changed' could not be "
                                "answered from the log" % (name, must.decode()))

        model = json.loads(z.read(MODEL).decode("utf-8"))
        if model.get("textures", {}).get("layer0") != "settledlands:item/door_key":
            problems.append("the item model does not point at the texture")
        if not z.read(TEXTURE).startswith(b"\x89PNG\r\n\x1a\n"):
            problems.append("the item texture is not a PNG")

        for lang in (RU, EN):
            strings = json.loads(z.read(lang).decode("utf-8"))
            for needed in LANG:
                if needed not in strings:
                    problems.append("%s has no %s" % (lang.rsplit("/", 1)[-1], needed))
    return problems


def probe(jar):
    if not PROBE.is_file():
        return ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp",
             ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "DoorLock368Probe.class").is_file():
            return ["the door probe did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "DoorLock368Probe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the door probe crashed:\n%s" % output[-800:]]
        problems = []
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        for name, want in EXPECT.items():
            if got.get(name) != want:
                problems.append("the door probe says %s = %r, expected %r"
                                % (name, got.get(name), want))
        return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("door_lock_check: no such jar %s" % jar)
        return 2
    problems = structural(jar) + probe(jar)
    if problems:
        print("door_lock_check: FAIL  %s" % jar.name)
        for problem in problems:
            print("  - %s" % problem)
        return 1
    print("door_lock_check: OK  %s  (the key registers, a locked door cannot be opened by a player or "
          "bashed by a monster, the lock survives a save, and a walking guide opens the door it meets "
          "and closes it behind itself)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
