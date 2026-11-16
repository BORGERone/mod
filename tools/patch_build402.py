#!/usr/bin/env python3
"""
Build 402 - the «открытие» socket speaks when the chest's window closes, for a cracked chest too.

The author's screenshot after build 401: his chest card has «открытие» wired to «Реплика #1» and «взлом» empty,
and the report is «я бы хотел чтобы реплика проигрывалась не после взлома, а уже после открытия инвентаря
сундука и его закрытия, как было до этого апдейта».

Two rules of build 401 met on that graph and silenced the line:

  * `ChestGraph396#runCrack` fell back to the «открытие» socket when «взлом» had nothing wired - so the
    «Реплика» was pulled forward to the crack, before the chest opened;
  * `ChestGraph396Events` then skipped the close of a cracked chest ("the crack chain has already run"), so the
    line played once, at the wrong moment, and never again.

The sockets are two **moments**, not two ways in, and neither stands in for the other:

  * «взлом» - before the chest opens, only when it is wired, only on a crack;
  * «открытие» - when the chest's window closes, on every opening, ordinary or cracked.

This build removes the fallback (`runCrack` follows «взлом» and nothing else) and runs the close chain on every
close (`run` follows «открытие» and nothing else). A graph that wires both says both things, in that order. A
graph with only the first socket - every graph of build 396/397 - behaves exactly as it did before 401. The
gate now asserts both directions on the bytecode: `run` must reach `chainStart` and must not reach
`hackChainStart`; `runCrack` the other way round.

Base 401: two classes replaced, none added, no new mixin, no language changes.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build401-chain-first"
NEW_VERSION = "0.8.0-build402-open-after-close"
OUT_NAME = "settledlands-0.8.0-build402-open-after-close.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # «взлом» before the opening, «открытие» on the close - and no fallback between them.
    "dev/settledlands/chest/ChestGraph396.class": ("runCrack", "hackChainStart"),
    "dev/settledlands/chest/ChestGraph396Events.class": ("takeCrack",),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 402 puts the chest's two sockets back on their own moments. The author's card wires «открытие» to a "
    "«Реплика» and leaves «взлом» empty; build 401 pulled that line forward (the chain before the opening fell "
    "back to the «открытие» socket when «взлом» was empty) and then skipped the close of a cracked chest, so the "
    "line played once, before the chest opened. Now «взлом» speaks only before the opening and only when it is "
    "wired, and «открытие» speaks when the chest's window closes - on every opening, ordinary or cracked. A "
    "graph that wires both says both, in that order."
)
PATCHINFO = (
    "Сборка 402 возвращает два узла карточки сундука на свои места. На твоей карточке «открытие» подключено к "
    "«Реплике #1», а «взлом» пуст: 401-я сборка тянула эту реплику вперёд (цепочка перед открытием "
    "откатывалась на узел «открытие», когда «взлом» пуст), а при закрытии окна взломанного сундука её "
    "пропускала — реплика звучала один раз и до сундука. Теперь «взлом» говорит только до открытия и только "
    "если он подключён, а «открытие» — когда окно сундука закрылось, при любом открытии: обычном и "
    "взломанном. Граф, где подключены оба, говорит оба и в этом порядке.\n"
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

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out402"

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
