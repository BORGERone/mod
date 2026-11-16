#!/usr/bin/env python3
"""
Build 393 - a chain wired to the door card's output socket really starts at that node.

The author's report on build 392, with a screenshot of a card wired to a «Реплика» and to its blue socket:
«ожидал, что после взлома (и после отпускания всех кнопок) начнется диалог, но ничего не произошло». The card
already showed «Взломал: BORGER», so the hack had run - and the chain had not.

One call was wrong. Build 392 handed the wired node to `BlockTrigger351#fire` - the entry the event nodes
(«В радиусе», «Время», «Таймер», «Цель») use, which runs a node's own action and then walks on from that
node's `next`, because an event node is not a step of a chain. A «Реплика» is nothing but its step, so it was
skipped and the graph was silent, with nothing in the log.

  * `BlockTrigger351#walk` now returns how many nodes it stepped over, and a new `BlockTrigger351#start` runs
    a chain from its first node - the node is a step, with everything a step means: a «Реплика» opens its
    window, a «Триггер» runs its commands, an «Условие» branches;
  * `DoorSignal388#hack` uses `start`, so the node the card's socket points at is executed, and the log line
    says how many nodes were stepped over;
  * `fire` is untouched: the event pass of build 352 needs it exactly as it is.

Base 392. Two classes replaced (`BlockTrigger351`, `DoorSignal388`), no new key, no new mixin, nothing else.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build392-door-card-sockets"
NEW_VERSION = "0.8.0-build393-door-chain-runs"
OUT_NAME = "settledlands-0.8.0-build393-door-chain-runs.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorSignal388.class",
    "dev/settledlands/npc/BlockTrigger351.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorSignal388.class": ("start", "chainStart"),
    "dev/settledlands/npc/BlockTrigger351.class": ("start", "walk"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 393 makes a chain wired to the door card's output socket actually start at that node. Build 392 "
    "handed the wired node to the event entry of the interpreter, which runs a node's action and walks on "
    "from its next, so a «Реплика» connected straight to the card was skipped and a hack said nothing - the "
    "author's report «ожидал, что после взлома начнется диалог, но ничего не произошло». BlockTrigger351 now "
    "has start(): the node is the first step of the chain, a «Реплика» opens its window, a «Триггер» runs its "
    "commands, and the door's hack uses it. fire() keeps its event semantics unchanged."
)
PATCHINFO = (
    "Сборка 393: цепочка, подключённая к выходному узлу карточки двери, действительно начинается с этой ноды. "
    "392-я отдавала ноду входу событий (fire), который выполняет действие ноды и идёт дальше по её next, "
    "поэтому «Реплика», подключённая прямо к карточке, пропускалась и взлом ничего не говорил — отчёт автора "
    "«ожидал, что после взлома начнется диалог, но ничего не произошло». Теперь есть BlockTrigger351#start: "
    "нода — первый шаг цепочки, «Реплика» открывает окно, «Триггер» выполняет команды, и взлом двери "
    "использует именно его. Поведение fire для событийных нод не изменилось.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out393"

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
