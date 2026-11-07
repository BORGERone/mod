#!/usr/bin/env python3
"""
Build 400 - the chest's settings reach its panel, and a solved panel opens the chest.

Two reports, both about the chest and both older than this build:

  * «в взломе сундука не меняется кол-во чисел, скорость и сложность». The sliders of the chest card moved,
    wrote a value, saved the graph - and the chest's panel kept asking 6 keys every 5 seconds on difficulty 1.
    The code that should have written them was in `NpcChestRows396Mixin#settledlands$slider`, and it was never
    the code that ran: the door's inspector adds a method of the very same name and signature to the very same
    screen class, Mixin keeps one of the two, and the door's version writes through `DoorGraph382#setKeys`,
    which refuses any node that is not a door. So every chest slider ran the door's code and wrote nothing -
    the same trap build 399 found in the toggle click, in the two sliders and the difficulty row next to it.
    Every helper and every callback of the chest inspector now has a name or a signature nobody else can claim;

  * «по прежнему после успешного взлома сундук не открывается». Builds 397, 398 and 399 all answered the
    solved panel by opening the chest's menu on the server (`ServerPlayer#openMenu(BlockState#getMenuProvider)`),
    with every guard passing and, in the author's game, no window. Three builds of the same idea is enough:
    this build stops calling a menu at all. The server lifts the lock of that chest and nothing else
    (`ChestNet397`), and the panel that was solved clicks the chest itself through the game's own
    `MultiPlayerGameMode#useItemOn` - the path a chest has never failed on. The click lands in
    `ChestGraph396Events#onUse`, which sees the lifted lock, lets the click through to vanilla, and records the
    opening as a **crack** so the chain under the card's «взлом» socket runs when the window closes. The lift is
    consumed by that one click, so the next opening is an ordinary one.

Base 399, eight classes replaced, no new class, no new mixin, no language changes.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build399-card-cable"
NEW_VERSION = "0.8.0-build400-chest-open"
OUT_NAME = "settledlands-0.8.0-build400-chest-open.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/chest/ChestNet397$Panel.class",
    "dev/settledlands/chest/ChestNet397$Solved.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()          # 398 registered it; this build only replaces its class

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # The chest's inspector keeps its own helper names and its own callback signatures (build 400).
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": ("lockSlider", "lockDifficulty"),
    # Every other pair of helpers that Mixin used to merge into one also got its own name or signature.
    "dev/settledlands/mixin/BlockMenu363Mixin.class": ("blockEditor", "doorEditor"),
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class": ("parkNameRows",),
    # A solved panel lifts the lock and no longer opens a menu from the server.
    "dev/settledlands/chest/ChestNet397.class": ("grant", "its lock is lifted"),
    "dev/settledlands/chest/ChestLock397.class": ("grant", "revoke"),
    # The click that opens the chest arrives here, is let through, and is recorded as a crack.
    "dev/settledlands/chest/ChestGraph396Events.class": ("granted", "revoke"),
    # The panel delivers the click through the game's own handler.
    "dev/settledlands/door/HackPanel377.class": ("useItemOn", "clickTheChest"),
    "dev/settledlands/chest/ChestNet397$Panel.class": ("chest_panel",),
    "dev/settledlands/chest/ChestNet397$Solved.class": ("chest_hack",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 400 fixes the chest's two oldest reports. The sliders of the chest card now really write the chest's "
    "numbers: the chest inspector's helpers were merged away by the door's inspector - same names, same "
    "signatures, one screen class - so every chest slider was running the door's code, which refuses a chest "
    "node, and the panel of the chest kept its defaults. Every helper and callback of the chest's rows now has "
    "its own name or its own signature. And a solved panel opens the chest again: the server no longer tries to "
    "open a menu from behind the screen (three builds of that produced no window), it lifts the lock of that one "
    "chest instead, and the panel clicks the chest through the game's own interaction handler - the click lands "
    "in the mod's own use event, passes to vanilla, opens the chest, and is recorded as a crack so the chain "
    "under the card's «взлом» socket runs when the window closes."
)
PATCHINFO = (
    "Сборка 400 чинит два старых отчёта по сундуку. 1) Ползунки карточки сундука теперь действительно пишут "
    "числа сундука: хелперы инспектора сундука затирались инспектором двери — те же имена и подписи в одном "
    "классе экрана, — поэтому каждый ползунок сундука выполнял код двери, который отказывает узлу сундука, и "
    "панель сундука оставалась с настройками по умолчанию. Теперь у каждого хелпера и каждого колбэка сундука "
    "своё имя или своя подпись. 2) Решённая панель снова открывает сундук: сервер больше не пытается открыть "
    "меню из-за экрана (три сборки этого не дали окна), а снимает замок именно этого сундука, а панель сама "
    "щёлкает по сундуку штатным обработчиком игры — клик приходит в наш же обработчик использования, проходит "
    "в ваниль, открывает сундук и записывается как взлом, чтобы цепочка под узлом «взлом» сработала при "
    "закрытии окна.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out400"

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
