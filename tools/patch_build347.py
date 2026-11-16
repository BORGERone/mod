#!/usr/bin/env python3
"""
Build 347 - a shared "Общее" graph is one more damage chain, not a replacement of the NPC's own.

The trace of build 346 caught the cause in one hit:

    [dmg] hit npc='Проводник' amount=1.0 source=entity=ServerPlayer ... own=enabled=true entry=0 nodes=5 damage=1
    [dmg]   node #4 type=6 min=1 filter=0(any) cooldown=1s if-in=0 -> gate-ok
    [dmg] chain npc='Проводник' graph=enabled=false entry=-1 nodes=2 damage=0 ...   (three times)

The NPC's own tab has five nodes and exactly one damage node - the one the author is looking at - and
the chain never walks it. All three calls that reach the chain carry the shared graph instead: a
two-node, disabled, entry-less graph with no damage node at all.

Build 334 hooked the shared GuideDialogue.handleNpcDamaged and cancelled it for the common graph. That
single method is what the NPC's own tab, the walk tab and the animation tab all go through, so one
unwritten shared graph switched the damage nodes of all three off - silently, and for every NPC.

Base: 346. Damage hook of 334 removed, replaced by one hook on GuideNpc.hurt that runs the shared
graph's chain once per hit, next to the three the stock body runs.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build346-damage-trace"
NEW_VERSION = "0.8.0-build347-common-damage"
OUT_NAME = "settledlands-0.8.0-build347-common-damage.jar"

REMOVED = (
    "dev/settledlands/mixin/NpcCommonGraphDamageMixin334.class",
)
REPLACED = ()
ADDED = (
    "dev/settledlands/mixin/NpcCommonGraphDamageMixin347.class",
)

MIXINS = ("NpcCommonGraphDamageMixin347",)
MIXINS_REMOVED = ("NpcCommonGraphDamageMixin334",)
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcCommonGraphDamageMixin347.class": (
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "settledlands$damageCommon",
        "NpcCommonGraph334"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 347 fixes damage nodes going silent when the world has a shared \"Общее\" graph. Build 334 "
    "ran the shared graph by cancelling GuideDialogue.handleNpcDamaged, the one method the NPC's own "
    "tab, the walk tab and the animation tab all pass through, so an empty or half-written shared "
    "graph took the damage nodes of all three with it - the 346 trace shows a hit walking a two-node "
    "graph without a single damage node while the author's own tab, with theirs, was never read. The "
    "shared graph is now one more chain, run once per hit from GuideNpc.hurt: nothing is cancelled, "
    "and a damage node fires whichever of the four tabs it was put in."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 347: \\u043d\\u043e\\u0434\\u0430 "
    "\\u0443\\u0440\\u043e\\u043d\\u0430 \\u0441\\u0440\\u0430\\u0431\\u0430"
    "\\u0442\\u044b\\u0432\\u0430\\u0435\\u0442 \\u0432 \\u043b\\u044e\\u0431"
    "\\u043e\\u0439 \\u0438\\u0437 \\u0447\\u0435\\u0442\\u044b\\u0440\\u0451"
    "\\u0445 \\u0432\\u043a\\u043b\\u0430\\u0434\\u043e\\u043a; \\u00ab\\u041e"
    "\\u0431\\u0449\\u0435\\u0435\\u00bb \\u0431\\u043e\\u043b\\u044c\\u0448"
    "\\u0435 \\u043d\\u0435 \\u043f\\u043e\\u0434\\u043c\\u0435\\u043d\\u044f"
    "\\u0435\\u0442 \\u043b\\u0438\\u0447\\u043d\\u0443\\u044e \\u0432\\u043a"
    "\\u043b\\u0430\\u0434\\u043a\\u0443."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out347"

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
        print("  mixins.json: common -%s" % name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
        print("  mixins.json: client -%s" % name)
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
    print("  mixins.json: %d common, %d client (+%s)"
          % (len(common), len(client), ", ".join(MIXINS)))

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
