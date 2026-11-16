#!/usr/bin/env python3
"""
Build 354 - the radius picture, fixed: the wand highlight stops blinking, the ring travels around the
circle, and a body shows its radius the same way a block does.

Base 353. Three classes added, two removed:

    RadiusPass354            the pass, now at two speeds: picture every 2 ticks, radius events every 8
    BlockTrigger351 / Store351  recompiled: both asked the removed pass what a radius node is
    NpcRadiusRingMixin354    takes over build 320's static body ring so it travels too (common)
    NpcRadiusRow354Mixin     the "Видимость" row, now in every editor, not only the block's (client)

    BlockTrigger352          removed: its eight-tick pass is what made the highlight blink
    NpcBlockCheckRow352Mixin removed: replaced by NpcRadiusRow354Mixin, which also covers bodies

Build 352 refreshed the sparks 2.5 times a second; a spark lives a few ticks, so it blinked between
every refresh. The picture now runs every 2 ticks - faster than a spark dies - while the radius event
itself stays at 8 ticks, because that is the half that asks the world questions. The ring is no longer
a still picture either: every picture tick the whole circle steps forward, and the particles still
alive sketch the trail, so the points travel.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build353-block-radius"
NEW_VERSION = "0.8.0-build354-radius-motion"
OUT_NAME = "settledlands-0.8.0-build354-radius-motion.jar"

REMOVED = (
    # build 352's pass: one speed for everything, which is what made the highlight blink
    "dev/settledlands/npc/BlockTrigger352.class",
    # build 352's inspector row: block editor only, replaced by the row that covers bodies too
    "dev/settledlands/mixin/NpcBlockCheckRow352Mixin.class",
)
REPLACED = (
    # both asked the removed pass what a radius node is; they ask the new one now
    "dev/settledlands/npc/BlockTriggerStore351.class",
    "dev/settledlands/npc/BlockTriggerStore351$Entry.class",
    "dev/settledlands/npc/BlockTrigger351.class",
)
ADDED = (
    "dev/settledlands/npc/RadiusPass354.class",
    "dev/settledlands/mixin/NpcRadiusRingMixin354.class",
    "dev/settledlands/mixin/NpcRadiusRow354Mixin.class",
)

MIXINS = ("NpcRadiusRingMixin354",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("NpcRadiusRow354Mixin",)
CLIENT_REMOVED = ("NpcBlockCheckRow352Mixin",)

MIXIN_MARKERS = {
    "dev/settledlands/npc/RadiusPass354.class": (
        "ServerTickEvent", "ELECTRIC_SPARK", "ENCHANT", "npcTick"),
    "dev/settledlands/mixin/NpcRadiusRingMixin354.class": (
        "tick(Ldev/settledlands/npc/GuideNpc;)V", "NpcRadiusZone320", "travellingRing"),
    "dev/settledlands/mixin/NpcRadiusRow354Mixin.class": (
        "widgets", "visible_on", "isRadiusNode"),
}

LANG_SET: dict = {}
LANG_SET_EN: dict = {}

CHANGELOG_EN = (
    "Build 354 fixes how the radius looks. The wand highlight no longer blinks: sparks were refreshed "
    "twice a second and a spark lives a few ticks, so the picture now runs ten times a second, while "
    "the radius event itself still fires twice a second. The ring is no longer a still circle - every "
    "picture tick it steps a little further, so the particles travel around the radius. And a body "
    "shows its radius exactly like a block: the same travelling ring, the same \"Видимость\" row in "
    "the inspector of the radius node (build 320's card box still writes the same flag)."
)
PATCHINFO = (
    "Сборка 354: подсветка блока больше не мигает (искры обновляются 10 раз в секунду, событие "
    "радиуса — по-прежнему 2 раза в секунду), частицы кольца едут по окружности, и радиус у НПС "
    "показывается так же, как у блока — тем же кольцом и той же строкой «Видимость радиуса» в "
    "инспекторе ноды.\n"
    "Заменены: BlockTrigger352 -> RadiusPass354; NpcBlockCheckRow352Mixin -> NpcRadiusRow354Mixin "
    "(строка больше не только для блока); добавлен NpcRadiusRingMixin354 — кольцо НПС рисует тот же "
    "проход, что и у блока.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out354"

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
