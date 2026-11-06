#!/usr/bin/env python3
"""
Build 394 - the event nodes of a door's graph fire: «В радиусе», «Цель», «Время», «Таймер».

The author's report on build 393: «у меня к примеру теперь не работает нода в радиусе, проверь и остальные
ноды». Build 392 opened the whole palette of a block in a door's graph - the card's graph offers exactly the
rows a block's does - and the only thing that ever ran a door's graph was the hack of the card, which starts a
chain where the output socket points. The four event nodes are not steps of a chain: they fire on their own and
a chain begins at their `next`. Nothing fired them on a door, because the pass that fires them walked block
graphs only. A node placed, wired and never fired is the report, word for word.

  * `RadiusPass354` now walks a door's graph exactly as it walks a block's. A place with a graph is a `Spot`:
    a block's `Entry` is one, a door's new `DoorSpot` is the other. The four event nodes, their cooldowns,
    their day and timer bookkeeping, their radius and their ring are the same code for both - there is no
    second copy of the rules to drift;
  * the pass no longer stands down when a world has no block graphs: a world of doors has work too;
  * a door's event node says so in the log when it fires, once per firing, and a block's stays silent as
    before;
  * `BlockTrigger351#allowed` is now `isStep || RadiusPass354.isEventType`: the menu can only offer a node
    that someone runs. `isStep` is the list of the seven types a chain steps over, stated in one place;
  * the ring of a door's «В радиусе» is drawn with the same travelling particles as a block's.

Base 393. Six classes (`RadiusPass354` and its two new nested types, `BlockTrigger351`, `BlockTriggerStore351`
and its `Entry`), no new key, no new mixin, nothing else.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build393-door-chain-runs"
NEW_VERSION = "0.8.0-build394-door-event-pass"
OUT_NAME = "settledlands-0.8.0-build394-door-event-pass.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorGraph382Store.class",
    "dev/settledlands/npc/BlockTrigger351.class",
    "dev/settledlands/npc/BlockTriggerStore351$Entry.class",
    "dev/settledlands/npc/BlockTriggerStore351.class",
    "dev/settledlands/npc/RadiusPass354$After.class",
    "dev/settledlands/npc/RadiusPass354.class",
)
ADDED = (
    "dev/settledlands/npc/RadiusPass354$DoorSpot.class",
    "dev/settledlands/npc/RadiusPass354$Spot.class",
)
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph382Store.class": ("ofDimension", "isDoorAt"),
    "dev/settledlands/npc/BlockTrigger351.class": ("isStep", "isEventType"),
    "dev/settledlands/npc/BlockTriggerStore351$Entry.class": ("RadiusPass354$Spot", "hasFiringEvent"),
    "dev/settledlands/npc/BlockTriggerStore351.class": ("ofDimension", "Entry"),
    "dev/settledlands/npc/RadiusPass354.class": ("DoorGraph382Store", "DoorStore368", "isDoorAt"),
    "dev/settledlands/npc/RadiusPass354$DoorSpot.class": ("RadiusPass354$DoorSpot", "hasFiringEvent"),
    "dev/settledlands/npc/RadiusPass354$Spot.class": ("RadiusPass354$Spot", "distanceSqr"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 394 makes the event nodes of a door's graph fire. A door's editor offers the whole palette of a "
    "block, so «В радиусе», «Цель», «Время» and «Таймер» could be placed on a door - and until now nothing "
    "walked a door's graph except the hack of the card, so an event node there was never fired: the author's "
    "report «у меня к примеру теперь не работает нода в радиусе». The pass that fires those nodes now walks "
    "a door's graph exactly as it walks a block's - same rules, same cooldowns, same ring, the same "
    "interpreter behind both - and a block's graph is untouched. allowed() is now isStep || isEventType, so "
    "a menu can only offer a node that someone actually runs."
)
PATCHINFO = (
    "Сборка 394: событийные ноды графа двери начинают работать. Редактор двери предлагает всю палитру блока, "
    "поэтому «В радиусе», «Цель», «Время» и «Таймер» можно было поставить на дверь — но граф двери до сих пор "
    "не обходил никто, кроме взлома карточки, так что событийная нода там не срабатывала никогда: отчёт автора "
    "«у меня к примеру теперь не работает нода в радиусе». Теперь проход, который зажигает эти ноды, обходит "
    "граф двери точно так же, как граф блока — те же правила, те же кулдауны, то же кольцо, тот же "
    "интерпретатор; граф блока не тронут. allowed() теперь isStep || isEventType, то есть меню может "
    "предложить только ту ноду, которую кто-то действительно выполняет. В лог пишется строка, когда "
    "событийная нода двери сработала.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out394"

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
