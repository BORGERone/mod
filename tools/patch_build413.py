#!/usr/bin/env python3
"""
Build 413 - the door pass runs from the guide's own tick.

The author after 412, and this is the report that finally names the fault:

  «Когда загрузился в мир - дверь сразу была открыта, и персонаж начал свободно ходить через нее, но не
   закрывать за собой. А потом я закрыл дверь и он опять начал тупить перед ней»

Nothing was opened and nothing was closed, in either half. A door that was already open was walked through
for as long as it stood open, and was never shut behind him; the moment he shut it himself, the guide stopped
in front of it. Opening a door and closing one behind the guide are the same few lines of `NpcDoors368`, so
the only reading is that the pass never ran at all - and «не закрывает» is the part that proves it, because
closing does not depend on any of the questions about locks, paths or bodies that the last four builds argued
about.

Its only way in was a static `ServerTickEvent.Post` handler. If that hook is not posted, the whole door
feature does not exist at runtime - and it cannot be seen offline: the class is in the jar, the annotation is
in the class, the event class resolves, every gate passes. Build 412 fixed its ability to switch itself off;
this one fixes its ability to never be called.

One thing: a second way in, `NpcDoors368#pass(GuideNpc)`, called from the guide's own
`customServerAiStep()` by `GuideDoorPass413Mixin` - the method `GuideNpc` itself declares, and the one its own
bytecode calls `tickCustomMovement` and `tickMobility` from, i.e. the ticks on which the guide is being moved.
`require = 0`, as build 369 taught: an injection that cannot be verified offline must not block the boot. Both
ways in share `track` and one clock (`lastOwnPass`), so the pass still runs at most once every PERIOD ticks.

Base 404: `NpcDoors368` (412's survival + 413's second way in), `NpcContent` (409's 0.6 body) and the new mixin.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build413-run-from-guide-tick"
OUT_NAME = "settledlands-0.8.0-build413-run-from-guide-tick.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/npc/NpcContent.class",
)
ADDED = (
    "dev/settledlands/mixin/GuideDoorPass413Mixin.class",
)
MIXINS = (
    "GuideDoorPass413Mixin",
)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # build 413: the door pass rides the guide's own tick, so it cannot depend on a server event being posted.
    "dev/settledlands/mixin/GuideDoorPass413Mixin.class": ("customServerAiStep", "NpcDoors368"),
    # a door kept open until the guide is through it; a locked door on the guide's path is opened too.
    "dev/settledlands/door/NpcDoors368.class": ("stopLeftBehind", "keepOpen", "markWalkedTo",
                                                "setCanPassDoors", "onTheWay", "inFrontOf", "beat", "lastOwnPass"),
    # the body that actually fits a doorway - vanilla lets a mob through a door only under 0.6 blocks.
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 413 runs the guide's door pass from the guide's own tick. In 412 a door that was already open when "
    "the world loaded was walked through for minutes and never closed behind the guide, and when he shut it "
    "the guide stopped in front of it - opening and closing are the same few lines, so the pass was not "
    "running at all, and its only way in was a server event hook. A second way in now rides the guide's own "
    "customServerAiStep, the method the guide's walking code runs from. If the server hook works, both are "
    "harmless: they share one implementation and one clock. If it does not, the doors work anyway."
)
PATCHINFO = (
    "Сборка 413 запускает проход дверей из тика самого проводника. В 412-й дверь, открытая ещё при загрузке "
    "мира, была пройдена много раз и ни разу не закрыта за ним, а когда вы закрыли её сами — он встал перед "
    "ней. Открыть дверь и закрыть её за проводником — это одни и те же несколько строк, значит проход не "
    "работал вообще, а попасть в него можно было только через серверное событие. Теперь есть второй вход — "
    "из собственного customServerAiStep проводника, того самого метода, из которого работает его ходьба. "
    "Если серверный вход работает, оба безвредны: у них одна реализация и один счётчик времени. Если нет — "
    "двери всё равно работают.\n"
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

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    # Both halves one step on: NpcDoors368 from build 410 (the on-the-way verdict), NpcContent from 409
    # (the 0.6 body - the author's 409 report says the body fits the doorway now, and that half stays).
    srcroot = root / "out413"
    npc_content = root / "out409" / "dev/settledlands/npc/NpcContent.class"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = npc_content if name.endswith("npc/NpcContent.class") else srcroot / name
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
