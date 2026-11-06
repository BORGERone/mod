#!/usr/bin/env python3
"""
Build 397 - the chest's lock becomes a setting.

The author's whole report, one line: «в графе сундука я не могу настраивать взлом». Build 396 gave a chest
the door's graph, but not the door's card: the chest card could only be read. A lock the author cannot set is
not a lock, so this build is the door's four settings moved onto the chest card, and the panel on the other
side of them:

  * `ChestLock397` - the chest's four numbers on the card's stock fields, with the door's ranges:
    `varCompare` = how many keys 4..10 (0 = never set, default 6), `varOp` = seconds between two changes
    1..20 (0 = 5), `checkTarget` = difficulty 1..3 (0 = 1), `pointsShared` = «Закрыта». The chest's mapping
    is deliberately NOT the door's (the door keeps the stock door fields for its own reasons) - what is the
    same is the meaning: three numbers and a flag, on the card, saved with the graph. `varValue` and `text`
    stay build 396's memory - how many times the chest was opened and who opened it last. No second store and
    no sync: the card flag IS the lock, so saving the graph locks the chest and clearing the flag frees it;
  * `ChestNet397` - the door's two packets for a chest, and nothing else: `chest_panel` (server -> client: the
    position, the keys, the seconds, the difficulty) and `chest_hack` (client -> server: this chest's panel was
    solved). A locked chest answers a click on the server with the panel; a solved panel opens the chest's own
    window through `ServerPlayer#openMenu` after the server re-checks the lock, the block and the distance;
  * `HackPanel377` learns the chest: the very same panel a door opens, titled «Взлом сундука», with the
    numbers the server sent - one lock panel in this mod, not two;
  * `ChestGraph396Events` refuses the click on a locked chest where the chest would have been opened (so a
    locked chest never stands open for even one tick - the rule of 371/376), and refuses to let one be broken
    open either; a chest opened through a solved panel records its opener and runs its chain on close exactly
    as a plain click does;
  * the chest card shows the door card's five lines, the inspector is the door's three sliders and a toggle
    (4..10, 1..20 s, 1..3, «Закрыта/Открыта»), and a graph written by build 396 gets the defaults written
    into it on open and on save.

Base 396. Six classes replaced, four added, twelve language keys, no mixin added or removed.

Not in this build, and said so in the README: a locked chest is refused for a plain click and for a break,
but nothing else in the world consults its lock (a hopper under it still pulls, an observer still fires) -
this is the player-facing lock of build 368, applied to a container.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build396-chest-graph"
NEW_VERSION = "0.8.0-build397-chest-hack"
OUT_NAME = "settledlands-0.8.0-build397-chest-hack.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestGraph396Events$Pending.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/npc/client/ChestCard396.class",
    "dev/settledlands/npc/client/ChestGraphClient396.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
)
ADDED = (
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/chest/ChestNet397$Panel.class",
    "dev/settledlands/chest/ChestNet397$Solved.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/chest/ChestGraph396.class": ("chainStart", "noteOpened", "ChestLock397"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("inventoryMenu", "ChestNet397"),
    "dev/settledlands/chest/ChestLock397.class": ("varCompare", "pointsShared", "gui.settledlands.chest.locked"),
    "dev/settledlands/chest/ChestNet397.class": ("settledlands:chest_panel", "settledlands:chest_hack"),
    "dev/settledlands/chest/ChestNet397$Panel.class": ("chest_panel",),
    "dev/settledlands/chest/ChestNet397$Solved.class": ("chest_hack",),
    "dev/settledlands/door/HackPanel377.class": ("Взлом сундука", "openForChest"),
    "dev/settledlands/npc/client/ChestCard396.class": ("ChestLock397", "gui.settledlands.chest.pin_out"),
    "dev/settledlands/npc/client/ChestGraphClient396.class": ("gui.settledlands.chest.keys",
                                                             "stateKeyShort"),
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": ("settledlands$chestRows", "ChestLock397"),
}

LANG_SET = {
    "gui.settledlands.chest.keys": "Кол-во чисел: %s",
    "gui.settledlands.chest.speed": "Скорость: %s с",
    "gui.settledlands.chest.difficulty": "Сложность: %s",
    "gui.settledlands.chest.lock": "Замок сундука",
    "gui.settledlands.chest.locked": "Закрыта (только взлом)",
    "gui.settledlands.chest.unlocked": "Открыта (обычный клик)",
    "gui.settledlands.chest.state_locked": "Замок: закрыт",
    "gui.settledlands.chest.state_open": "Замок: открыт",
    "message.settledlands.chest.locked": "Сундук заперт: сначала взлом",
    "message.settledlands.chest.free": "Сундук уже не заперт",
    "message.settledlands.chest.far": "Подойди ближе к сундуку",
    "message.settledlands.chest.gone": "Сундука больше нет",
}
LANG_SET_EN = {
    "gui.settledlands.chest.keys": "Keys: %s",
    "gui.settledlands.chest.speed": "Speed: %s s",
    "gui.settledlands.chest.difficulty": "Difficulty: %s",
    "gui.settledlands.chest.lock": "Chest lock",
    "gui.settledlands.chest.locked": "Locked (hack only)",
    "gui.settledlands.chest.unlocked": "Open (plain click)",
    "gui.settledlands.chest.state_locked": "Lock: shut",
    "gui.settledlands.chest.state_open": "Lock: open",
    "message.settledlands.chest.locked": "The chest is locked: crack it first",
    "message.settledlands.chest.free": "The chest is not locked any more",
    "message.settledlands.chest.far": "Come closer to the chest",
    "message.settledlands.chest.gone": "The chest is gone",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 397 makes the chest's hack a setting. The author's report was one line - «в графе сундука я не "
    "могу настраивать взлом» - so the chest card now carries the door card's four settings, in the same "
    "places and with the same ranges: how many keys the lock panel asks for (4..10), how often one of them "
    "changes (1..20 s), the difficulty (1..3 rows of the keyboard) and «Закрыта/Открыта». A locked chest "
    "refuses a plain click on the server, before anything could open, and answers with the very same lock "
    "panel a door opens - titled «Взлом сундука», with the numbers the card carries; a solved panel asks the "
    "server, the server re-checks the lock, the block and the distance, and opens the chest's own window. A "
    "locked chest cannot be broken open either. The card flag IS the lock: saving the graph locks the chest, "
    "clearing the flag frees it, and a graph written by build 396 opens as a chest with a working panel. "
    "The chest's chain still runs when the chest's window closes - a dialogue under an open chest is a "
    "dialogue nobody reads. Not covered: a lock that also stops a hopper or an observer below the chest."
)
PATCHINFO = (
    "Сборка 397: взлом сундука теперь настраивается. Отчёт автора - одна строка: «в графе сундука я не могу "
    "настраивать взлом», поэтому на карточке сундука появились те же четыре настройки, что у двери, на тех "
    "же местах и с теми же диапазонами: сколько чисел просит панель взлома (4..10), как часто одно из них "
    "меняется (1..20 с), сложность (1..3 ряда клавиатуры) и «Закрыта/Открыта». Запертый сундук отказывает в "
    "обычном клике на сервере, до того как что-то успело открыться, и в ответ открывает ту же самую панель "
    "взлома, что и дверь, - с заголовком «Взлом сундука» и теми числами, что стоят на карточке; решённая "
    "панель спрашивает сервер, сервер заново проверяет замок, блок и расстояние и открывает собственное окно "
    "сундука. Запертый сундук нельзя и сломать. Флаг на карточке - это и есть замок: сохранил граф - сундук "
    "заперт, снял флаг - свободен; граф, записанный сборкой 396, открывается как сундук с работающей "
    "панелью. Цепочка сундука по-прежнему срабатывает, когда окно сундука закроют. Не покрыто: замок не "
    "останавливает воронку и наблюдателя под сундуком.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out397"

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
