#!/usr/bin/env python3
"""
Build 373 - the refusal moves into the door.

The author's report on 372: "I can still walk through a locked door if I lean on it and click fast."
That sentence is a precise diagnosis, and it says the refusal never happened: the door opens for one
tick per click, and a player pressed against it slips through in that tick.

Why it never happened, and why "nothing changed" was the correct description of 371 and 372:

  * 371 denied the click with {@code event.setUseBlock(Event.Result.DENY)}. **That method does not exist
    on {@code PlayerInteractEvent} in NeoForge 1.21.1** - the version the base mod runs on calls
    {@code setCancellationResult} instead. The call threw {@code NoSuchMethodError} inside the listener,
    the listener died on that line, and everything after it - including the door closing and the message
    - never ran. My own {@code catch (Throwable)} swallowed it into a warning line.
  * So the door was opened by vanilla exactly as before, and build 371's guard then closed it a tick
    later. That is the "open for a second" the author kept describing, in its most literal form.

Three changes, and the first one is the one that fixes it:

  1. {@code DoorUse373Mixin} refuses {@code DoorBlock#useWithoutItem} - the method a right click on a
     door actually ends in (1.21.1 javadoc: {@code protected InteractionResult useWithoutItem(BlockState,
     Level, BlockPos, Player, BlockHitResult)}). The door never opens, so there is no tick to walk
     through. {@code require = 0}: a signature this exact version lacks must not stop the launch.
  2. {@code DoorEvents368} uses {@code setCancellationResult(InteractionResult.FAIL)} - the call the base
     mod already makes - and sets it inside its own small guard, so a missing method can never again
     take the rest of the refusal with it.
  3. The stub of {@code PlayerInteractEvent} no longer offers {@code setUseBlock}/{@code setUseItem}, and
     the API allowlist no longer excuses them: the mistake cannot be repeated by accident. The gate now
     forbids both names in the jar.

Base 372. Nine classes replaced, one mixin added to the common list.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build372-door-log"
NEW_VERSION = "0.8.0-build373-door-refuse"
OUT_NAME = "settledlands-0.8.0-build373-door-refuse.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorStore368.class",
    "dev/settledlands/door/Doors368.class",
    "dev/settledlands/door/DoorKey368.class",
    "dev/settledlands/door/NpcIds368.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/DoorTab370.class",
    "dev/settledlands/door/DoorGuard371.class",
)
ADDED = (
    "dev/settledlands/mixin/DoorUse373Mixin.class",
)
MIXINS = ("DoorUse373Mixin",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/DoorUse373Mixin.class": ("useWithoutItem", "DoorEvents368"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 373 stops a locked door from opening at all. The refusal now sits on DoorBlock#useWithoutItem, "
    "the method a right click on a door ends in, so there is no tick in which the door is open - leaning "
    "on a locked door and clicking fast no longer gets anyone through. Build 371's event-based denial "
    "called setUseBlock, which PlayerInteractEvent does not have in 1.21.1: it threw, the listener died, "
    "and nothing was ever refused. The event now uses setCancellationResult(InteractionResult.FAIL), and "
    "the gate forbids the two names that do not exist."
)
PATCHINFO = (
    "Сборка 373 не даёт запертой двери открыться вообще. Отказ теперь стоит на "
    "DoorBlock#useWithoutItem - методе, в который попадает ПКМ по двери, - поэтому тика, в который "
    "дверь открыта, больше нет: упереться в запертую дверь и быстро кликать больше не помогает. "
    "Запрет из 371 вызывал setUseBlock, которого в PlayerInteractEvent у 1.21.1 нет: вызов бросал "
    "исключение, слушатель умирал, и ничего не запрещалось. Теперь событие использует "
    "setCancellationResult(InteractionResult.FAIL), а гейт запрещает оба несуществующих имени.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out373"

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
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for name in EXTRA:
        src = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / EXTRA_DIR / name.split("assets/settledlands/", 1)[1]
        if not src.is_file():
            raise SystemExit("missing asset: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("base jar already has %s" % name)
        order.append(name)
        blobs[name] = data
        print("  asset: %s (%d bytes)" % (name, len(data)))

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
