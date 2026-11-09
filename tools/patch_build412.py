#!/usr/bin/env python3
"""
Build 412 - the door pass can no longer switch itself off for the rest of the session.

The author after 411, and this is the fourth build in a row that ends at a door: «По прежнему тупит перед
дверью, причем не важно заперта она или просто закрыта».

That last half is the clue, and it is not a dress code for the door, it is a statement about the code: for an
unlocked door, `openDoor` does not ask any of the questions builds 409/410/411 argued about. An unlocked door
is opened by the first branch there is. So "locked or not, the same" means the pass that opens doors was not
running - and it turns out it has a way to stop, and it is mine:

  * `onServerTick` began with `if (failed) return;` and every exception anywhere in the pass set `failed` for
    good. One warning in the log, then every door is a plain closed door until the game is restarted. Every
    build since 368 has grown this pass (path reading, the target watch, the on-the-way tests, the lock rule),
    so one failure in it - or in any class it calls - silently deletes the whole feature. A pass that dies
    and a feature that was never written look identical from inside the game.

Fix, one class, and it is about the pass's own survival rather than a new theory about doors:

  * the pass keeps running: a failed pass is counted, logged three times at most, and the pass carries on to
    the next one. It stands down only after two hundred consecutive failures, which is not a bad tick but a
    pass that cannot work at all;
  * and it now says it is alive: once a minute it logs how many guides it can see, how many doors it has
    opened since the game started, and how many passes failed. Every report since 408 needed that line and
    none of them had it - a pass that never ran, a pass that ran and found nothing, and a pass that had
    switched itself off were indistinguishable in the log.

Base 404: `NpcDoors368` (build 412) and `NpcContent` (409's 0.6 body). The on-the-way answers of 411
(path, target, geometry) are unchanged.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build404-through-the-doorway"
NEW_VERSION = "0.8.0-build412-pass-cannot-switch-itself-off"
OUT_NAME = "settledlands-0.8.0-build412-pass-cannot-switch-itself-off.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/npc/NpcContent.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # a door kept open until the guide is through it; a locked door on the guide's path is opened too.
    "dev/settledlands/door/NpcDoors368.class": ("stopLeftBehind", "keepOpen", "markWalkedTo",
                                                "setCanPassDoors", "onTheWay", "inFrontOf", "beat"),
    # the body that actually fits a doorway - vanilla lets a mob through a door only under 0.6 blocks.
    "dev/settledlands/npc/NpcContent.class": ("settledlands:guide",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 412 stops the door pass from switching itself off for the rest of the session. It began every pass "
    "with 'if it has ever failed, stop' and set that flag on any exception anywhere in it - so one bad tick "
    "turned every door into a plain closed door until the game was restarted, with a single warning to say "
    "so. Every build since 368 has added work to that pass, so this was a trap waiting for any of them. Now a "
    "failed pass is counted, reported at most three times, and the pass carries on; it stands down only after "
    "two hundred consecutive failures. The pass also proves it is alive once a minute: guides in sight, doors "
    "opened since the game started, failed passes."
)
PATCHINFO = (
    "Сборка 412 не даёт проходу дверей выключать себя до конца сессии. Каждый проход начинался с «если он "
    "когда-то падал — выходим», а флаг падения ставился на любое исключение внутри: один плохой тик "
    "превращал все двери в обычные закрытые до перезапуска игры, и в логе оставалось одно предупреждение. С "
    "368-й в этот проход добавляли работу в каждой сборке, так что это была ловушка для любой из них. Теперь "
    "упавший проход считается, пишется в лог не больше трёх раз, и проход продолжает работать; он "
    "останавливается только после двухсот подряд неудач. И раз в минуту он подтверждает, что жив: сколько "
    "проводников видит, сколько дверей открыл с начала игры, сколько проходов упало.\n"
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
    srcroot = root / "out412"
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
