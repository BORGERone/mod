#!/usr/bin/env python3
"""
Build 385 - the hack panel takes the keyboard, and a door node gets a difficulty.

Two requests from the author, in his words:

  «я хотел бы чтобы кнопки блокировали пользовательский ввод, а то сейчас к примеру можно нажать кнопку R
   и перезагрузить шейдеры и т.д. нужно блокировать все кроме кнопки выхода (ESC)»

  «так-же хочу добавить в ноду новый пункт "сложность" - при первой сложности уже доступны кнопки из
   блока qwertyuiop[] а при второй будут уже появлятся кнопки еще и из строчки asdfghjkl;' ну и при
   третей со следующей строчки»

1. The input lock. `HackPanel377` answered `true` only for the keys it was showing and let everything
   else fall through to `Screen#keyPressed`, which returns false and hands the key to the game's key
   mappings - Iris reloads its shaders on R, and every other shortcut of the pack was live inside the
   panel too. Now every key but ESC is swallowed (both press and release), and ESC goes to the screen's
   own handler, which closes the panel and nothing else. Reading the hardware directly
   (`InputConstants#isKeyDown`) is what the panel measures, so the lock does not affect the game itself.

2. The difficulty. The door node carries a fourth value, 1..3, edited by a third slider in the inspector
   and shown on the card as «Сложность: 2 (qwerty + asdf)»:

     * 1 - `qwertyuiop[]` (12 keys, the row of build 378 plus the two keys the author named);
     * 2 - those plus `asdfghjkl;'` (23 keys);
     * 3 - those plus `zxcvbnm,./` (33 keys).

   The panel draws its keys out of that pool, and so does the swap of build 378: a harder door does not
   ask for more keys, it asks the same number of keys out of a bigger board. GLFW codes of these keys
   equal their ASCII values, which is why the keycap label can stay `String.valueOf((char) code)`.

Storage and transport: difficulty lives in `Node#checkTarget` - another int the stock serialiser writes
for every node and reads back with a default (the only code that range-checks it does so for «Проверка»
nodes, which ours is not; the lesson of build 384 is that the field must be one every type saves). The
sync line of build 382 grows one field: `key=count:seconds:difficulty`, and a two-field line - one still
in flight from 384 - is read as difficulty 1.

Base 384. Five classes replaced, no new class, no new mixin.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build384-door-graph-save"
NEW_VERSION = "0.8.0-build385-door-hack-difficulty"
OUT_NAME = "settledlands-0.8.0-build385-door-hack-difficulty.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/npc/client/DoorCard382.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/HackPanel377.class": (
        "keyPressed", "ROW_HOME", "difficulty", "isKeyDown"),
    "dev/settledlands/door/DoorGraph382.class": (
        "difficulty", "checkTarget", "settings"),
    "dev/settledlands/door/DoorSync375.class": (
        "putSettings", "difficulty", "sendParams"),
    "dev/settledlands/npc/client/DoorCard382.class": (
        "difficulty", "drawString", "screenX"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "widgets", "propertyChange", "setDifficulty"),
}

LANG_SET = {
    "gui.settledlands.door.difficulty": "Сложность: %s",
    "gui.settledlands.door.rows1": "qwerty",
    "gui.settledlands.door.rows2": "qwerty + asdf",
    "gui.settledlands.door.rows3": "qwerty + asdf + zxc",
}
LANG_SET_EN = {
    "gui.settledlands.door.difficulty": "Difficulty: %s",
    "gui.settledlands.door.rows1": "qwerty",
    "gui.settledlands.door.rows2": "qwerty + asdf",
    "gui.settledlands.door.rows3": "qwerty + asdf + zxc",
}
LANG_CHANGE = {
    "assets/settledlands/lang/ru_ru.json": {
        "gui.settledlands.door.hint": ("Клавиши и скорость панели взлома",
                                       "Ввод заблокирован · ESC — выход"),
    },
    "assets/settledlands/lang/en_us.json": {
        "gui.settledlands.door.hint": ("Keys and speed of the hack panel",
                                       "Input is blocked · ESC to leave"),
    },
}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 385 does two things the author asked for. The hack panel now owns the keyboard: every key "
    "but ESC is swallowed, so no key mapping, hotkey or mod shortcut (a shader reload on R, for "
    "instance) fires while it is open, and ESC closes the panel and nothing else. And a door node has "
    "a fourth value, «Сложность» 1..3, edited by a third slider and shown on the card with the rows it "
    "opens: 1 is qwertyuiop[], 2 adds asdfghjkl;' and 3 adds zxcvbnm,./. The panel draws its keys out "
    "of that board and swaps one of them inside it, so a harder door asks the same number of keys out "
    "of a bigger keyboard. Difficulty is stored in a node field every type saves and travels in the "
    "door sync line as one more field, with an old two-field line read as difficulty 1."
)
PATCHINFO = (
    "Сборка 385 делает две вещи по просьбе автора. Панель взлома теперь забирает себе клавиатуру: все "
    "клавиши, кроме ESC, глотаются, поэтому ни одна привязка, hotkey или шорткат мода (например, "
    "перезагрузка шейдеров на R) не срабатывает, пока панель открыта, а ESC закрывает только панель. "
    "И у ноды двери появилось четвёртое значение - «Сложность» 1..3: третий ползунок в инспекторе и "
    "строка на карточке с рядами, которые она открывает: 1 - qwertyuiop[], 2 - плюс asdfghjkl;', "
    "3 - плюс zxcvbnm,./. Панель берёт клавиши из этого набора и меняет одну из них внутри него, так "
    "что сложная дверь просит столько же клавиш, но с большего поля. Сложность хранится в поле ноды, "
    "которое сохраняется для любого типа, и едет в строке синхронизации ещё одним полем, причём старая "
    "строка из двух полей читается как сложность 1.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out385"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("base jar has no entry to remove: %s" % name)
        order.remove(name)
        del blobs[name]
        print("  removed: %s" % name)

    for name in REPLACED + ADDED:
        src = srcdir / name
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
