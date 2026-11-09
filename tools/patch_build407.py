#!/usr/bin/env python3
"""
Build 407 - the door stays open until the guide is through it.

The author's `latest.log` from build 406 (attached 2026-11-09) is the first log of the door problem that
shows the navigator's own state, and it names the root that every attempt since 368 walked past:

    door: the guide pass opened BlockPos{x=62, y=64, z=-237} ... on a guide's path
    door: the guard closed the locked door at BlockPos{x=62, y=65, z=-237}
    ...      (that pair, five times, in three quarters of a second)
    door: the guide 1cfb9bcd-... has stood still at 63.3 64.0 -236.5 - door at ... open=true;
          path 10 node(s), next 0, target BlockPos{x=53, y=64, z=-237}, ... walking;
          against a block=true; walk-through=path replanned and a cross started
    door: the guide ... has stood still ...; no path, done; against a block=false; still crossing
    door: the guide ... gave up after 140 ticks at BlockPos{x=62, y=64, z=-237}

Three facts, and they are enough:

  * opening a door was an **event** with an expiry, not part of walking. 406 opened doors twelve blocks
    ahead and closed them as soon as no guide was on the line to them - which for a door twelve blocks away
    is the very next pass. The log is that loop: opened, shut, opened, shut, with the locked-door guard
    slamming the same door (its bottom half is opened by the pass above? no - the guard's key is the top
    half, the same door) in between;
  * the guide was pressed against the door with a **finished** path of ten nodes whose target was five
    blocks *behind* the doorway it had to walk through. A path planned at a shut door ends at that door,
    and a finished path does not become a path through an open doorway by itself;
  * and the "walk it through" step of 404/406 did nothing at all for two seconds. It could not: a guide's
    own movement runs inside its AI step every tick and asks the navigator again, so a wanted position set
    from outside is overwritten before it is used.

So this build stops pushing and makes the door part of walking:

  * the guide's node maker is told **both** things: `setCanOpenDoors(true)` (a closed wooden door is a door
    the guide may open) and `setCanPassDoors(true)` (a wooden door standing open is plain floor - no opening
    step left in the way of the step itself). PathNavigation grows the second method; it is public in 1.21
    and forwards to the same node maker `getNodeEvaluator()` hands out;
  * a door a guide's path runs through is opened from the path, and it **stays open until the guide is
    through it**: not for a fixed time and not until the next pass, but until a guide has come up to it
    (1.7 blocks - the doorway) and walked away;
  * a door opened ahead of a guide is never closed before a guide has actually reached it; if it never does,
    it is closed anyway after five seconds;
  * a **locked** door is never opened by this pass, not for a tick. The log shows the pass opening one that
    the guard then had to fight; a lock is a rule, and the door being walked through must be unlocked;
  * the last resort is a plain `recomputePath()` when a guide stands still at a door that is on the way to
    its target - a replan, not a push, and not a body move. Builds 405/406's cross ("walk it out of the
    doorway" on the move control) and 405's 0.85-block body move are **gone**; the first was a no-op in the
    log and the second is the teleport the author forbade.

Base 404: one class replaced (`NpcDoors368`), one nested class removed (`NpcDoors368$Cross` stays in the
jar - the base never had it), no new mixin, no language keys. The stub that teaches the compiler
`PathNavigation#setCanPassDoors` is `build303-src/api-src` and is compiled before the main pass.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build407-door-kept-open"
OUT_NAME = "settledlands-0.8.0-build407-door-kept-open.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # a door kept open until the guide is through it, a locked door never opened, no push.
    "dev/settledlands/door/NpcDoors368.class": ("stopLeftBehind", "keepOpen", "markWalkedTo",
                                                "setCanPassDoors"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 407 keeps a door open until the guide is through it. The author's log of build 406 is the first "
    "that shows the navigator's own state, and it names the root: opening a door was an event with an "
    "expiry, not part of walking. Doors were opened twelve blocks ahead and closed again as soon as no "
    "guide stood on the line to them - for a door twelve blocks away that is the next pass - and the "
    "locked-door guard slammed the same door in between, five times in three quarters of a second. The "
    "guide stood against that door with a finished path of ten nodes whose target was behind the doorway it "
    "had to cross, because a path planned at a shut door ends at that door. Now a guide's node maker is "
    "told both that a closed wooden door may be opened and that an open wooden door is plain floor; a door "
    "on the guide's path is opened and stays open until a guide has come up to it and walked away; a door "
    "opened ahead is never closed before a guide reaches it; and a locked door is never opened at all. The "
    "walk-a-step-on-the-move-control of 404/406 is gone - the log shows it doing nothing for two seconds, "
    "because a guide's own movement asks the navigator again every tick - and so is 405's body move."
)
PATCHINFO = (
    "Сборка 407 держит дверь открытой, пока проводник не пройдёт. Ваш latest.log со сборки 406 — первый "
    "лог, где видно состояние навигатора, и в нём названа причина: открытие двери было событием с "
    "истечением срока, а не частью ходьбы. Двери открывались за двенадцать блоков и закрывались, как "
    "только на линии к ним не оставалось проводника, — для двери в двенадцати блоках это следующий же "
    "проход; а сторож запертых дверей тем временем захлопывал ту же дверь. Пять раз за три четверти "
    "секунды. Проводник стоял у этой двери с ЗАКОНЧЕННЫМ путём из десяти узлов, цель которого была за "
    "проёмом, а путь, построенный при закрытой двери, кончается у двери. Теперь поиску пути проводника "
    "говорят обе вещи: закрытую деревянную дверь можно открыть, а открытая деревянная дверь — обычный "
    "пол. Дверь на пути открывается и остаётся открытой, пока проводник не подойдёт и не отойдёт. Дверь, "
    "открытая заранее, не закрывается до того, как к ней подошли. Запертая дверь не открывается вовсе. "
    "Шаг «протолкнуть» из 404/406 убран: в логе он две секунды не делал ничего, потому что движение "
    "проводника каждый тик заново спрашивает навигатор. Перенос тела из 405-й тоже убран.\\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out407"

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
