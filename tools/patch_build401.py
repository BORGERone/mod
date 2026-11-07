#!/usr/bin/env python3
"""
Build 401 - the «взлом» chain speaks before the chest opens, the door's «открытие» always speaks, and ESC closes
the panel it was pressed in.

Three reports, and none of them is about a missing screen or a missing node:

  * «порядок: сначала реплика, потом инвентарь». Build 400 opened a solved chest from the panel's `onClose` -
    the one mechanism in this story that ever worked in the author's game - but the panel closes when the last
    key comes up, which is *before* the server has run a single node of the chain under the card's «взлом»
    socket. So the chest window arrived first and the «Реплика» played over it. The click moved to
    `ChestOpen401`: the server runs the chain, notes whether one of its windows took the chain over
    (`BlockTrigger351#startReportingWindow` and the `stopped[0]` it fills), and only then says
    `settledlands:chest_ready`. The client holds the same click until that window is gone and the screen has
    been empty for two ticks. The lock that lets the click through is now a **crack** keyed by the player and
    the chest (`ChestLock397#crack/takeCrack`, ten minutes) - a «Реплика» is read at the player's pace, and the
    ten-second lift of build 400 was not;

  * «дверь: при открытии двери проиграется реплика, но сейчас ничего не происходит». The door's «открытие»
    chain has exactly one trigger: an injection at the `RETURN` of `DoorBlock#useWithoutItem`
    (`DoorUse373Mixin`), `require = 0`. A selector that stops matching on a game version does not fail the
    launch - it never runs, and a wired socket is silent with nothing anywhere to say why. This build gives the
    chain a second leg that no mixin can take away: the game's own right-click event on a shut door hands the
    door to `DoorSignal398#watch`, and one tick later the door is read again - shut before, open now means that
    click opened it. The two legs share the quarter-second guard, so a live mixin and a live watcher still run
    the chain once. Both legs now write a line: the mixin says it is alive and that it saw a door go from shut
    to open, and every reason the chain can decline to run is written out once, with the reason;

  * «проблемы с закрытием взлома на ESC, срабатывает через раз». The solved panel closed itself when every key
    was up, and its ESC hatch waited for a 1200 ms *hold* measured from `keyPressed` events - and key repeats
    are the window manager's business, so the hold sometimes ended by itself and sometimes never happened. The
    hold is measured in `Screen#tick()` on the hardware (`InputConstants#isKeyDown`), shortened to 800 ms,
    drawn as a percentage so the player can see it, and the panel keeps its "answer is already with the server"
    guard: the door's click still belongs to `DoorHack377`, and the chest's click is now `ChestOpen401`'s. What
    the panel no longer does is send the answer a second time when no window arrives (`settledlands$askAgain`
    is now a report, not a resend): a resend would run the «взлом» chain a second time, and that chain now
    comes *before* the chest opens.

Base 400: nine classes replaced, four added (ChestOpen401, ChestOpenWatch401, ChestNet397$Ready,
DoorSignal398$Watching), no new mixin, no language changes.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build400-chest-open"
NEW_VERSION = "0.8.0-build401-chain-first"
OUT_NAME = "settledlands-0.8.0-build401-chain-first.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    # Four classes asked the click event for its entity through a descriptor the game does not have (see the
    # comment in the stub of PlayerInteractEvent): `Entity` instead of `Player`. The call threw inside the
    # `try` of every click handler, so a handler stopped halfway - which is what a door that opens by hand and
    # says nothing, and a chest whose chain ran in the wrong order, look like from the outside.
    "dev/settledlands/door/DoorGraph382Events.class",
    "dev/settledlands/npc/BlockTriggerEvents351.class",
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorSignal398.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/mixin/DoorUse373Mixin.class",
    "dev/settledlands/npc/BlockTrigger351.class",
)
ADDED = (
    "dev/settledlands/chest/ChestNet397$Ready.class",
    "dev/settledlands/chest/ChestOpen401.class",
    "dev/settledlands/chest/ChestOpenWatch401.class",
    "dev/settledlands/door/DoorSignal398$Watching.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # The crack of a solved chest: per player, per chest, ten minutes.
    "dev/settledlands/chest/ChestLock397.class": ("crack", "takeCrack", "CRACKED"),
    # The server runs the chain first and says `chest_ready` after it.
    "dev/settledlands/chest/ChestNet397.class": ("sendReady", "runCrack"),
    "dev/settledlands/chest/ChestNet397$Ready.class": ("chest_ready",),
    "dev/settledlands/chest/ChestGraph396.class": ("runCrack", "startReportingWindow"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("takeCrack",),
    "dev/settledlands/door/DoorGraph382Events.class": ("RightClickBlock",),
    "dev/settledlands/npc/BlockTriggerEvents351.class": ("RightClickBlock",),
    "dev/settledlands/npc/BlockTrigger351.class": ("startReportingWindow",),
    # The click waits for the chain's window, one tick at a time.
    "dev/settledlands/chest/ChestOpen401.class": ("useItemOn", "is ready to be clicked"),
    "dev/settledlands/chest/ChestOpenWatch401.class": ("ClientTickEvent",),
    # ESC on the hardware, and no click from the panel.
    "dev/settledlands/door/HackPanel377.class": ("escHeldSince",),
    # The second leg of the door's «открытие» and the evidence lines of both legs.
    "dev/settledlands/door/DoorSignal398.class": ("WATCHING", "sawOpened"),
    "dev/settledlands/door/DoorSignal398$Watching.class": (),
    "dev/settledlands/door/DoorEvents368.class": ("DoorSignal398", "watch"),
    "dev/settledlands/mixin/DoorUse373Mixin.class": ("sawOpened", "sawUse"),
}

LANG_SET = {}
LANG_SET_EN = {}
# Build 401: the click is not the player's job any more. The line said «нажми на сундук ещё раз» because
# build 400 could only ask; the client now clicks for him, and a line telling him to do something the game
# already did is the kind of thing that makes the author click twice and close what just opened.
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "message.settledlands.chest.again": ("Замок снят: нажми на сундук ещё раз",
                                             "Замок снят: сундук откроется сам"),
    },
    "assets/settledlands/lang/en_us.json": {
        "message.settledlands.chest.again": ("The lock is off: click the chest again",
                                             "The lock is off: the chest opens by itself"),
    },
}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 401 puts the chest's «взлом» chain in front of the chest's window, makes the door's «открытие» chain "
    "fire on an ordinary click, and gives the hack panel an ESC that works every time. The chest is no longer "
    "opened by the panel's close - the server runs the chain under the card's «взлом» socket first, reports "
    "whether one of its windows took the chain over, and the client's click waits until that window is gone; "
    "the lock that lets the click through is keyed by the player and the chest and lasts ten minutes, because a "
    "«Реплика» is read at the player's pace. The door's chain now has two legs: the mixin at "
    "DoorBlock#useWithoutItem and the game's own right-click event, which watches a shut door for one tick and "
    "runs the chain when the click opened it - a mixin whose selector stops matching can no longer silence a "
    "wired socket. Both legs write what they saw, and every reason the chain may decline to run is logged once. "
    "ESC on a solved panel is timed in Screen#tick() on the hardware, shortened to 800 ms and drawn as a "
    "percentage; the panel no longer re-sends its answer, which would run the chain twice."
)
PATCHINFO = (
    "Сборка 401 делает три вещи. 1) Сундук: цепочка под узлом «взлом» играет ДО окна сундука. Панель больше не "
    "щёлкает сундук при закрытии — сервер сначала прогоняет цепочку, отмечает, остановило ли её окно (реплика, "
    "выбор, катсцена), и только потом говорит клиенту settledlands:chest_ready; клик ждёт, пока это окно "
    "закроется и экран два тика будет пуст. Замок, который пропускает клик, теперь «взлом» с ключом "
    "игрок+сундук на десять минут — реплику читают в своём темпе, а десяти секунд сборки 400 не хватало. "
    "2) Дверь: у цепочки «открытие» было одно срабатывание — инъекция в RETURN DoorBlock#useWithoutItem с "
    "require = 0; если селектор не совпал с версией игры, миксин молча не работает, и провод молчит. Теперь "
    "есть вторая нога: обычный клик по закрытой двери (игровое событие, миксином его не убрать) передаёт дверь "
    "в DoorSignal398#watch, и через тик дверь читается снова — была закрыта, стала открыта, значит открыл этот "
    "клик. Обе ноги делят защиту в четверть секунды, поэтому цепочка всё равно срабатывает один раз; обе "
    "пишут в лог, что видели, и все причины, по которым цепочка не пошла, пишутся один раз с причиной. "
    "3) ESC в панели взлома: удержание считается в Screen#tick() по железу (InputConstants#isKeyDown), "
    "сокращено до 800 мс и рисуется процентами; повторы keyPressed, на которые сборка 398 опиралась, приходили "
    "«через раз». Панель больше не отправляет ответ повторно (settledlands$askAgain теперь только пишет в лог): "
    "повтор прогнал бы цепочку «взлом» второй раз.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out401"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to the base - nothing to replace" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if not table:
            continue
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings and strings[key] == value:
                raise SystemExit("%s already has %s = %r" % (path, key, value))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    for path, table in LANG_CHANGE.items():
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, (before, after) in table.items():
            if strings.get(key) != before:
                raise SystemExit("%s: %s is %r, expected %r"
                                 % (path, key, strings.get(key), before))
            strings[key] = after
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, after))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name not in common:
            raise SystemExit("%s is not in the common list" % name)
        common.remove(name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
    for name in MIXINS_FIRST:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.insert(0, name)
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be a common mixin" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (first: %s)"
          % (len(common), len(client), common[0]))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    stale = 'displayName="Settled Lands %s"' % BASE_VERSION
    if stale not in toml:
        raise SystemExit("base toml has no displayName to bump")
    toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, displayName, description prefix" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d entries)" % (out_path, len(order)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
