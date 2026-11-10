#!/usr/bin/env python3
"""Gate: a locked door is closed to players and monsters, and open to a guide on its path.

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
    the quiet ones - `tools/harness/DoorLock368Probe.java` is compiled against this jar and run;
  * build 403: the guide pass reads the guide's own path and opens the doors on it, tells the node maker
    that a closed door is a doorway, plans the path again once the door is open, and the locked-door guard
    steps back while a guide is at the door - a lock that is shut on a guide's back is the author's
    «не проходят даже через открытые»;
  * build 404: doors are opened **ahead** of a walking guide, in the direction its navigator is taking it
    (a door opened when the guide is already at it is opened one plan too late);
  * build 407: the author's `latest.log` of build 406 showed that walking a guide through an open door on
    the move control did nothing at all for two seconds - a guide's own movement asks the navigator again
    every tick, so a wanted position set from outside is overwritten before it is used. The pass now keeps
    the door **open until the guide is through it** instead: the guide's path finder is told both that a
    closed wooden door may be opened (`setCanOpenDoors`) and that an open wooden door is plain floor
    (`setCanPassDoors`), and a door opened ahead is not shut before a guide reaches it. The checks below ask
    for exactly that, and for the absence of the push;
  * build 408: the lock. 407 refused every locked door and the guide stopped in front of its own test door,
    which is locked. The author's rule is one sentence - «нпс должен открывать все двери на его пути, в т.ч.
    и запертые» - so a locked door **on the guide's path** is opened for it (`locked` and `onPath` in the
    same condition, and the path branch passing `onPath = true`), while a locked door that merely stands next
    to the guide is still refused. Both halves are checked: a pass that opens every locked door it sees is as
    wrong as the one that opens none.

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
import classfile
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
          "PARSE": "dim=minecraft:overworld x=12 y=64 z=-30 junk=true",
          "GUIDE_PASS": "opened=true heldAhead=true closedBehind=true"}


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


def field_names(parsed):
    """Every declared field name of a class - how a build proves a panel offers letters, not digits."""
    cp = parsed["cp"]
    out = set()
    for _acc, ni, _di, _attrs in parsed["fields"]:
        if cp[ni] and cp[ni][0] == 1:
            out.add(cp[ni][1])
    return out


def field_consts(parsed):
    """The int fields of a class that carry a ConstantValue - the only way to read a `static final`."""
    cp = parsed["cp"]
    out = {}
    for _acc, ni, _di, attrs in parsed["fields"]:
        if not cp[ni] or cp[ni][0] != 1:
            continue
        name = cp[ni][1]
        for an, body in attrs:
            if not cp[an] or cp[an][0] != 1 or cp[an][1] != "ConstantValue":
                continue
            if len(body) != 2:
                continue
            idx = struct.unpack(">H", body)[0]
            if cp[idx] and cp[idx][0] == 3:
                out[name] = struct.unpack(">i", cp[idx][1])[0]
    return out


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

        # Build 403: «они не проходят даже через открытые». Three mechanisms, and every one of them is a
        # call to the game, not a promise in a comment:
        #
        #   * the path finder has to be told that a guide may open a door - in 1.21 that flag lives on the
        #     node evaluator (PathNavigation#getNodeEvaluator().setCanOpenDoors), not on the mob, so the
        #     368 answer on the mob is a method the game never asks for;
        #   * the doors that are opened have to be the ones on the guide's own path, which means the path
        #     and its nodes are read at all;
        #   * a path planned at a closed door has to be planned again once the door is open, or the guide
        #     stands in an open doorway with nowhere left to go.
        npc = "dev/settledlands/door/NpcDoors368.class"
        if npc not in names:
            problems.append("no guide door pass at all")
        else:
            npc_cls, npc_refs = parse(z.read(npc))
            if not any(r[1] == "setCanOpenDoors" and r[0]
                       and r[0].endswith("pathfinder/NodeEvaluator") for r in npc_refs):
                problems.append("the guide's node maker is never told it may open doors - a closed door "
                                "stays a wall in every path it plans (build 403)")
            if not any(r[1] == "getNodeEvaluator" and r[0]
                       and r[0].endswith("ai/navigation/PathNavigation") for r in npc_refs):
                problems.append("the pass does not reach the node maker at all")
            if not any(r[1] == "getPath" and r[0]
                       and r[0].endswith("ai/navigation/PathNavigation") for r in npc_refs):
                problems.append("the pass never reads the guide's path - the doors it opens are a guess "
                                "by distance, not the door on the way (build 403)")
            if not any(r[1] == "getNodePos" and r[0]
                       and r[0].endswith("pathfinder/Path") for r in npc_refs):
                problems.append("the pass never looks at a path node - it cannot open the door on the path")
            if not any(r[1] == "recomputePath" and r[0]
                       and r[0].endswith("ai/navigation/PathNavigation") for r in npc_refs):
                problems.append("a path planned at a closed door is never planned again - opening the "
                                "door leaves the guide where it stood (build 403)")
            if not any(r[1] == "guideNear" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in set(npc_refs) | set(guard_refs)):
                problems.append("nothing but this pass knows where the guides are - the guard cannot ask "
                                "it before shutting a door")
            # Build 404: «доходит до двери, открывает, но дальше открытой двери не проходит». A door that is
            # opened when the guide is already at it is opened one plan too late - the path it is walking
            # ends at the door. Two mechanisms are asked for here, and both are calls to the game:
            #
            #   * read where the navigator is taking the guide and open the doors AHEAD of it, so the next
            #     leg is planned against an open doorway;
            #   * walk a guide that stopped at an open door through it - a short path past the doorway, and
            #     the step itself on the move control when the path finder still refuses.
            if not any(r[1] == "getTargetPos" and r[0]
                       and r[0].endswith("ai/navigation/PathNavigation") for r in npc_refs):
                problems.append("the pass never asks where the navigator is taking the guide - the doors "
                                "ahead cannot be opened before the guide plans its next leg (build 404)")
            # Build 407: the log of 406 is the evidence. A wanted position on the move control was set
            # twice and the guide did not move a hundredth of a block for two seconds, so the push is gone
            # and the door is kept open instead. Both halves are checked: the push must NOT be there, and
            # the two door switches of the node maker must be.
            if any(r[1] == "setWantedPosition" and r[0]
                   and r[0].endswith("ai/control/MoveControl") for r in npc_refs):
                problems.append("the pass still orders a step on the move control - the log of build 406 "
                                "shows it doing nothing (a guide's own movement asks the navigator again "
                                "every tick), so the door must be kept open instead (build 407)")
            if not any(r[1] == "setCanOpenDoors" and r[0]
                       and r[0].endswith("pathfinder/NodeEvaluator") for r in npc_refs):
                problems.append("the path finder is never told that a closed wooden door may be opened - "
                                "every path a guide plans goes round the door (build 403)")
            if any(r[1] == "setCanPassDoors" and r[0]
                   and r[0].endswith("ai/navigation/PathNavigation") for r in npc_refs):
                problems.append("NpcDoors368 calls PathNavigation#setCanPassDoors - Minecraft 1.21.1 has no "
                                "such method on PathNavigation (NoSuchMethodError in the 413 log); call it "
                                "on NodeEvaluator instead (build 414)")
            if not any(r[1] == "setCanPassDoors" and r[0]
                       and r[0].endswith("pathfinder/NodeEvaluator") for r in npc_refs):
                problems.append("the path finder is never told on NodeEvaluator that a door standing open is "
                                "plain floor - the open doorway is still a special node and the guide stops "
                                "in it (build 407/414)")
            open_inner = "dev/settledlands/door/NpcDoors368$Open.class"
            if open_inner not in names:
                problems.append("NpcDoors368$Open.class is missing from the jar (build 414)")
            else:
                open_cf = classfile.parse(z.read(open_inner), open_inner)
                if not any(f.name == "walkedTo" and f.desc == "Z" for f in open_cf.fields):
                    problems.append("NpcDoors368$Open.class in the jar has no boolean walkedTo field - "
                                    "stale inner class packaged from build 404 throws NoSuchFieldError on "
                                    "every door (build 414, caught in the 413 log)")
            if not any(r[1] == "baseDoorPos" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in npc_refs):
                problems.append("NpcDoors368 does not canonicalize both halves of a two-block door - "
                                "DoorGuard371 can shut the upper locked half while the guide walks to the "
                                "lower half (build 414)")
            if not any(r[1] == "keepOpen" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in npc_refs):
                problems.append("a door this pass opened is not remembered - nothing keeps it open until "
                                "the guide is through it, and it is shut again on the next pass (build 407)")
            if not any(r[1] == "stopLeftBehind" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in npc_refs):
                problems.append("nothing closes the doors behind the guide - the author asked for a door "
                                "that is shut right behind it (build 407)")
            if not any(r[1] == "markWalkedTo" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in npc_refs):
                problems.append("a door opened ahead can be shut before a guide reaches it - that is the "
                                "open/shut loop the log of build 406 is made of (build 407)")
            # Build 407: one radius, one meaning. A door was claimed "walked through" inside 2.2 blocks
            # and shut once nobody was inside 1.7 - so it was shut in the guide's face half a block before
            # the doorway, which is the author's complaint exactly. Both numbers must be the same one.
            if b"IN_DOORWAY" not in z.read(npc):
                problems.append("the doorway radius is not shared between \"a guide has been in it\" and "
                                "\"a guide is in it\" - a door can be claimed walked through and then shut "
                                "in front of the guide (build 407)")
            if b"WAS_AT_IT" in z.read(npc):
                problems.append("a second doorway radius is back - two distances for one doorway is what "
                                "shuts the door in the guide's face (build 407)")
            # Build 408: the lock, both directions. A locked door ON THE GUIDE'S PATH is opened for the guide
            # (the author: «нпс должен открывать все двери на его пути, в т.ч. и запертые» - and 368/403 said
            # the same); a locked door the guide merely stands next to is not. 407 refused both and stopped
            # the guide in front of the door it had just opened in the log of 406, so the check is that the
            # two cases are told apart, not that locks are refused.
            if b"locked" not in z.read(npc):
                problems.append("the pass never asks whether a door is locked - a locked door on the guide's "
                                "path stays shut and the guide stands in front of it (build 408)")
            # The two branches are told apart by their own log lines: "on the guide's path - opened for the
            # guide" exists only in the allow branch, "not on a guide's path" only in the refuse branch. A
            # class that carries both cannot be answering the lock with a single yes or a single no.
            npc_bytes = z.read(npc)
            if b"is not on a guide's way" not in npc_bytes:
                problems.append("the pass does not refuse a locked door that merely stands next to the guide "
                                "- the pass would be a key to every locked door in the village (build 408)")
            # Build 410: the verdict must not be the "is it moving" flag. That flag says a guide standing in
            # front of a door is not on its way to it, and build 409 answered the lock with exactly that - the
            # pass then refused the one door the guide was standing at, five times a second, for ever.
            # Build 410/411. What must never happen is a door being judged by the "is the guide moving" flag
            # ALONE - build 409 did exactly that and a guide standing in front of a door was told the door
            # was not on its way, for ever. The flag coming back as an argument is fine (build 411 uses it to
            # narrow a cone); what is not fine is the verdict being that flag and nothing else. So: the pass
            # must offer at least one fact about the door itself - its own path, the target it walks to, or
            # its place in front of the guide's nose.
            # Build 412: the pass must not be able to delete itself. `if (failed) return;` at the top meant
            # one exception anywhere switched every door off for the rest of the session, and from inside
            # the game that is indistinguishable from a pass that was never written. A failure counter keeps
            # the pass alive; a latch does not.
            if b"failed" in npc_bytes and b"FAILURE_LIMIT" not in npc_bytes:
                problems.append("the door pass still has its old kill switch: one exception anywhere and it "
                                "never runs again for the session, so every door is a plain closed door and "
                                "nothing says so (build 412)")
            if b"beat" not in npc_bytes:
                problems.append("the door pass never says it is alive - a pass that never ran, one that found "
                                "nothing and one that switched itself off look identical in the log (build 412)")
            facts = [marker for marker in (b"onTheWay", b"onPathNode", b"inFrontOf") if marker in npc_bytes]
            if b"openAround" in npc_bytes and not facts:
                problems.append("the door pass judges a door by the 'is the guide moving' flag and by nothing "
                                "else - a guide that has stopped is by definition not moving, so its own door "
                                "is refused and it stands there for ever (build 409/410)")
            if b"is on the guide's path" not in npc_bytes:
                problems.append("the pass does not open a locked door that is on the guide's path - the "
                                "guide stands in front of its own door, which is the author's 407 report "
                                "(build 408)")

        # Build 409: the body. Vanilla's WalkNodeEvaluator treats a wooden door as a walkable doorway only
        # for a mob **under 0.6** blocks wide, so a guide 0.675 wide never had a door in its path no matter
        # what was set on the node maker - and the body only passes an open doorway dead centre. The
        # author's 409 report is what separates body from door: stuck *inside* the doorway, door open.
        content = "dev/settledlands/npc/NpcContent.class"
        if content not in names:
            problems.append("no NpcContent: nothing declares the guide's size")
        else:
            data = z.read(content)
            if struct.pack(">f", 0.675) in data:
                problems.append("the guide's body is still 0.675 blocks wide - a mob that wide does not walk "
                                "through doors at all in this game, and an open doorway is a squeeze for it "
                                "(build 409: the author got stuck IN the doorway)")
            if struct.pack(">f", 0.6) not in data:
                problems.append("the guide's body is not 0.6 blocks wide - 0.6 is the width the path finder "
                                "calls a mob that walks through doors (build 409)")
            # Build 415: the guide's height. At 2.1F the hitbox is taller than a 2-block doorway (and
            # WalkNodeEvaluator computes entityHeight = floor(2.1 + 1.0) = 3 blocks), so any door with a
            # lintel block above it - i.e. any 2-block-high passage - blocks the guide. 1.8F (player height)
            # fits 2-block doorways and passages even over carpets.
            if struct.pack(">f", 2.1) in data:
                problems.append("the guide's body is still 2.1 blocks tall - taller than a 2-block doorway, "
                                "so a block above the door stops the guide (build 415)")
            if struct.pack(">f", 1.8) not in data:
                problems.append("the guide's body is not 1.8 blocks tall - must fit 2-block passages and "
                                "doorways with a block above the door (build 415)")
            if npc in names:
                npc_raw = z.read(npc)
                if struct.pack(">d", 3.0) in npc_raw:
                    problems.append("NpcDoors368 still carries a 3.0-block reach - opens doors 3 blocks "
                                    "before the guide arrives instead of right at the door (build 415)")
                if struct.pack(">d", 1.25) not in npc_raw:
                    problems.append("NpcDoors368 is missing the close-up 1.25-block opening reach (build 415)")
            if not any(r[1] == "getTarget" and r[0] and r[0].endswith("pathfinder/Path")
                       for r in npc_refs):
                problems.append("the stuck line cannot say where the finished path was aiming - the log "
                                "would not tell a path that ended at the door from a path that went "
                                "through (build 404)")
        if guard in names:
            if not any(r[1] == "guideNear" and r[0] and r[0].endswith("door/NpcDoors368")
                       for r in guard_refs):
                problems.append("the guard still shuts a locked door while a guide is walking through it "
                                "- the guide opens it and it is slammed on the next tick (build 403)")

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

        # Build 377: the lock is no longer a wall with nothing behind it. A click on a locked door opens
        # the hack panel, and a solved panel asks the server to open that one door. Four things have to
        # be true of the jar for that to be a feature and not a decoration: the panel exists and is a
        # real screen, it offers the 4 to 10 keycaps the author asked for, it reads the keyboard itself,
        # and the door opens only through a packet the server checks against the lock.
        panel = "dev/settledlands/door/HackPanel377.class"
        hack = "dev/settledlands/door/DoorHack377.class"
        if panel not in names:
            problems.append("no hack panel: a locked door would still answer a click with nothing but "
                            "a line in the action bar")
        if hack not in names:
            problems.append("no solved-panel packet: the panel could never open a door")
        if panel in names:
            panel_cls, panel_refs = parse(z.read(panel))
            cp = panel_cls["cp"]
            parent = disasm.cls_name(cp, panel_cls["super"]) if cp[panel_cls["super"]] else ""
            if parent != "net/minecraft/client/gui/screens/Screen":
                problems.append("the hack panel is not a Screen (%s) - the game would never draw it"
                                % parent)
            if not any(r[0] == "com/mojang/blaze3d/platform/InputConstants" and r[1] == "isKeyDown"
                       for r in panel_refs):
                problems.append("the panel never reads the keyboard: 'all keys at once' would be a "
                                "guess about the events it received, not a fact")
            if not any(r[0] == "net/minecraft/client/gui/GuiGraphics" and r[1] == "drawCenteredString"
                       for r in panel_refs):
                problems.append("the panel draws no text - the keycaps would be unlabelled")
            if b"\xd0\x92\xd0\xb7\xd0\xbb\xd0\xbe\xd0\xbc" not in z.read(panel):
                problems.append("the panel has no 'Взлом' title string")
            consts = field_consts(panel_cls)
            if consts.get("MIN_BUTTONS") != 4 or consts.get("MAX_BUTTONS") != 10:
                problems.append("the panel does not offer 4 to 10 buttons as asked (min=%r max=%r)"
                                % (consts.get("MIN_BUTTONS"), consts.get("MAX_BUTTONS")))
        # Build 378: the keycaps are letters, and the clock changes a key instead of the attempt. Both
        # are visible in the class itself: the letter row is a field named LETTERS, the roll interval is
        # ROLL_MS, the swap says so, and the old "не успел - кнопки сброшены" reset is gone.
        if panel in names:
            panel_cls2, _panel_refs2 = parse(z.read(panel))
            fnames = field_names(panel_cls2)
            # Build 385 split the single row of 378 into the three rows of «Сложность»
            # (ROW_TOP / ROW_HOME / ROW_BOTTOM). What this check guards is unchanged: the keycaps are
            # letters of the keyboard, never digits.
            if not ({"LETTERS", "ROW_TOP", "ROW_HOME", "ROW_BOTTOM"} & fnames) or "DIGITS" in fnames:
                problems.append("the panel still offers digits - the author asked for keys/letters")
            if "ROLL_MS" not in fnames:
                problems.append("the panel has no rolling key interval - the clock would have to throw "
                                "the attempt away again")
            body = z.read(panel)
            if "клавиша сменилась".encode("utf-8") not in body:
                problems.append("the panel never reports a changed key - the clock may still be a reset")
            if "не успел - кнопки сброшены".encode("utf-8") in body:
                problems.append("the panel still resets the attempt when the clock runs out - the "
                                "author asked for one key to change instead")
        if hack in names:
            hack_cls, hack_refs = parse(z.read(hack))
            for owner, member, why in (
                    ("net/minecraft/network/protocol/common/custom/CustomPacketPayload$Type", "<init>",
                     "the solved-panel message is not a payload - NeoForge would not send it"),
                    ("net/neoforged/neoforge/network/PacketDistributor", "sendToServer",
                     "nothing is ever sent to the server when the panel is solved"),
                    ("dev/settledlands/door/Doors368", "setOpen",
                     "the server never opens the door the panel was solved for"),
                    ("dev/settledlands/door/DoorStore368", "isLocked",
                     "the server opens the door without checking the lock - the client would be trusted"),
                    ("net/neoforged/neoforge/network/handling/IPayloadContext", "player",
                     "the handler never asks who solved the panel")):
                if not any(r[0] == owner and r[1] == member for r in hack_refs):
                    problems.append("%s: %s" % (why, member))
        if net in names:
            _net_cls, net_refs = parse(z.read(net))
            if not any(r[1] == "playToServer" and r[0] and r[0].endswith("registration/PayloadRegistrar")
                       for r in net_refs):
                problems.append("the solved-panel payload is never registered for the server - a "
                                "solved panel would change nothing")
        if not any(r[1] == "open" and r[0] and r[0].endswith("door/HackPanel377") for r in event_refs):
            problems.append("the door click never opens the panel - the minigame would be unreachable")
        # Build 377: the guard of 371 closes every locked door that is open. A door a player has just
        # cracked open is open on purpose, so the guard has to ask before it slams it shut.
        _guard_cls, guard_refs2 = parse(z.read("dev/settledlands/door/DoorGuard371.class"))
        # Build 395: the question is crackedOpen now (build 377's timestamped cracked() is the timer the
        # author saw close his door); the intent of this check is unchanged - the guard must know a door
        # this mod opened on purpose.
        if not any(r[1] in ("crackedOpen", "cracked") and r[0] and r[0].endswith("door/DoorHack377")
                   for r in guard_refs2):
            problems.append("the guard does not know about cracked doors - a solved panel would open a "
                            "door that the next tick closes again")

        # Build 379: "the panel was solved and the door did not open" must never be unanswerable again.
        # The packet path has to say when it arrived, when it was refused and when the block refused the
        # open, and the panel has to say when it opened and when it was solved.
        for name, must in ((hack, "door_hack arrived"),
                           (hack, "was asked to open and did not"),
                           (panel, "the lock panel opened"),
                           (panel, "sending the hack")):
            if name in names and must.encode("utf-8") not in z.read(name):
                problems.append("%s does not report %r - a door that stays shut could not be explained "
                                "from the log" % (name, must))
        if hack in names and b"use as it is" not in z.read(hack) and b"the position is used" not in z.read(hack):
            problems.append("the hack still requires the exact dimension spelling - a door inside a "
                            "sub-level or a pocket dimension would never open")

        # Build 380: a solved panel has to be watched from the client side, and the packet handler has to
        # run the way the mod's own working payloads run. Without the first, "the door does not open" is
        # unanswerable; without the second the handler may be running where it cannot touch the world.
        watch = "dev/settledlands/door/DoorHackWatch380.class"
        if watch not in names:
            problems.append("no client watchdog: a door that stays shut would leave no trace at all")
        else:
            _w_cls, w_refs = parse(z.read(watch))
            if not any(r[1] == "watch" and r[0] and r[0].endswith("door/DoorHack377") for r in w_refs):
                problems.append("the watchdog never calls DoorHack377#watch")
            _cp = _w_cls["cp"]
            on_tick = False
            for _acc, _ni, _di, _attrs in _w_cls["methods"]:
                if _cp[_ni] and _cp[_ni][0] == 1 and _cp[_ni][1] == "onClientTick":
                    desc = _cp[_di][1] if _cp[_di] and _cp[_di][0] == 1 else ""
                    if "client/event/ClientTickEvent$Post" in desc:
                        on_tick = True
            if not on_tick:
                problems.append("the watchdog is not on the client tick (ClientTickEvent$Post)")
        if net in names and b"enqueueWork" not in z.read(net):
            problems.append("the hacking handler is not handed to the main thread the way the mod's own "
                            "payloads do it (context.enqueueWork)")
        for must in (b"is open - the server answered", b"still closed", b"sending the request"):
            if hack in names and must not in z.read(hack):
                problems.append("DoorHack377 does not report %r - a door that stays shut could not be "
                                "explained" % must.decode())
        if hack in names and b"- nothing opened" not in z.read(hack):
            problems.append("the 'not a door on the server' refusal is still silent")

        # Build 381: the one line that decides whether any door in this mod can ever move. StateHolder
        # publishes setValue(Property, Comparable)Object; builds 368-380 called a variant that returns
        # BlockState, which does not exist, so every open threw NoSuchMethodError. The jar is checked by
        # descriptor, not by name, because the name was always right and the shape was not.
        _doors_cls, doors_refs = parse(z.read(DOORS))
        writes = [d for (o, n, d) in doors_refs
                  if o == "net/minecraft/world/level/block/state/BlockState" and n == "setValue"]
        if not writes:
            problems.append("Doors368 never writes the OPEN property - nothing could open a door")
        elif ("(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)"
              "Ljava/lang/Object;") not in writes:
            problems.append("BlockState#setValue is called as %r - 1.21.1 publishes "
                            "(Property, Comparable)Object, and anything else is a NoSuchMethodError on "
                            "the first door (builds 368-380 never opened a door because of this)"
                            % (writes,))

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
