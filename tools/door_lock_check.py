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
          "KEY": "isKey=false"}


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
