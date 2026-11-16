#!/usr/bin/env python3
"""
Build 376 - the client is told which doors are locked, so ordinary doors predict again.

The author's report on 375: "locked doors work, but ordinary doors open laggy - the top half with a
delay". 375 made the client refuse *every* door click and wait for the server, and that wait is what he
saw: the server writes one half of a door in the packet of the click and the other a beat later, and
without the local prediction the beat is visible.

The refusal only has to cover locked doors, so the client needs to know them:

  * `DoorSync375` - one payload, `settledlands:door_sync`, a single string field: `C|` (forget all),
    `S|k1;k2;...` (snapshot), `A|...` (more of a chunked snapshot), `L|key` / `U|key` (one door changed).
    A key is the store's own string, `minecraft:overworld@12,64,-30`.
  * `DoorNet375` - the registration, `registrar("1").playToClient(type, codec, handler)`, wrapped so a
    refused payload costs the quick opening of doors, not the launch.
  * `DoorEvents368` - on login the server sends the snapshot; on lock/unlock it sends the delta; and the
    client refuses a click only when `DoorSync375.synced()` says it knows the locks and the door is one
    of them. Before the first snapshot the strict answer of 375 stands: refuse everything, be slow,
    never let a locked door open for a tick.

Signatures are from the published NeoForge 1.21.1 sources (`PayloadRegistrar#playToClient`,
`RegisterPayloadHandlersEvent#registrar`, `PacketDistributor#sendToPlayer`,
`PacketDistributor#sendToPlayersInDimension`) and the 1.21.1 javadoc
(`CustomPacketPayload.Type(ResourceLocation)`, `StreamCodec.composite(codec, getter, factory)`).

Base 375. One class replaced, two added.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build375-door-client"
NEW_VERSION = "0.8.0-build376-door-sync"
OUT_NAME = "settledlands-0.8.0-build376-door-sync.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorEvents368.class",
)
ADDED = (
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/door/DoorNet375.class",
)
# DoorUse373Mixin is already a common mixin in the base jar; listing it asserts that it still is, and
# the loop below never duplicates an entry.
MIXINS = ("DoorUse373Mixin",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 376 gives the client the list of locked doors, so ordinary doors open instantly again. Build "
    "375 stopped the client from opening any door on its own, which was secure but cost a round trip on "
    "every door - and the two halves of a door are written by the server a beat apart, which is the "
    "delay the author saw. A new payload (settledlands:door_sync) tells each client on login which doors "
    "are locked and keeps it current; the client now refuses only those, and predicts every other door "
    "exactly as vanilla does. Locked doors still never open for a tick."
)
PATCHINFO = (
    "Сборка 376 снова открывает обычные двери мгновенно: клиент получает список запертых дверей и "
    "отказывает только им. 375 запрещал клиенту открывать любую дверь самому - это надёжно, но стоило "
    "круглого обмена на каждую дверь, а две половины двери сервер пишет с разницей в такт: это и была "
    "задержка. Новый пакет settledlands:door_sync сообщает при входе, где замки, и обновляет их при "
    "каждой смене; остальные двери клиент предсказывает как обычный Minecraft. Запертая дверь "
    "по-прежнему не открывается ни на тик.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out376"

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
