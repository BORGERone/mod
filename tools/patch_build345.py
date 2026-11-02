#!/usr/bin/env python3
"""
Build 345 - two fixes to the dialogue graph, in one jar.

  1. A damage node never fired for the source the author picked. The dropdown and the node card number
     the four sources 0 any, 1 player, 2 monster, 3 non-player, and the node's number is stored as the
     dropdown row. GuideDialogue.matchesDamageFilter read the same number in another order (0 player,
     1 monster, 2 non-player, 3 any), so "От игрока (Player)" was tested as "от монстра" and a player's
     hit never reached the action.

  2. The random line of a "Реплика" node rolled once for the whole world, because the result lived in
     the shared node. The dice are now thrown per NPC, once, and kept for as long as that body exists.

  New: the filter rule and the per-NPC dice, one hook each, and the node hook of 336 re-pointed at the
  per-NPC roll. Base: 344 (the build the report came from).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build344-cinematic-legs"
NEW_VERSION = "0.8.0-build345-graph-fixes"
OUT_NAME = "settledlands-0.8.0-build345-graph-fixes.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/mixin/NpcRandomLineNodeMixin336.class",
)
ADDED = (
    "dev/settledlands/npc/NpcDamageFilter345.class",
    "dev/settledlands/npc/NpcRandomLine345.class",
    "dev/settledlands/mixin/NpcDamageFilterMixin345.class",
    "dev/settledlands/mixin/NpcRandomLineContext345.class",
)

MIXINS = ("NpcDamageFilterMixin345", "NpcRandomLineContext345")
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/NpcDamageFilter345.class": ("NpcDamageFilter345", "matches", "damager"),
    "dev/settledlands/npc/NpcRandomLine345.class": ("SettledLands/NpcRandomLine345", "rollInto", "pick"),
    "dev/settledlands/mixin/NpcDamageFilterMixin345.class": (
        "matchesDamageFilter(ILnet/minecraft/world/damagesource/DamageSource;)Z",
        "settledlands$damageFilter"),
    "dev/settledlands/mixin/NpcRandomLineContext345.class": (
        "sendGraph(Lnet/minecraft/server/level/ServerPlayer;"
        "Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/GuideNpc;)V",
        "settledlands$enterNpc", "settledlands$exitNpc"),
    "dev/settledlands/mixin/NpcRandomLineNodeMixin336.class": ("NpcRandomLine345", "pick"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 345 fixes two things in the dialogue graph. A damage node now fires for the source picked "
    "in its inspector: the dropdown and the node card number the sources 0 any, 1 player, 2 monster, "
    "3 non-player, but the damage chain read the same number in another order, so a node set to "
    "\"From Player\" was tested as \"from monster\" and a player's hit never reached its action. The "
    "random line of a \"Реплика\" node now throws its dice per NPC: the roll used to live in the "
    "shared node, so every NPC said the same phrase; each body now rolls for itself once and keeps "
    "its phrase."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 345: \\u0434\\u0432\\u0435 \\u043f\\u0440\\u0430"
    "\\u0432\\u043a\\u0438 \\u0433\\u0440\\u0430\\u0444\\u0430. \\u041d\\u043e\\u0434\\u0430 "
    "\\u0443\\u0440\\u043e\\u043d\\u0430 \\u0441\\u0440\\u0430\\u0431\\u0430\\u0442\\u044b\\u0432"
    "\\u0430\\u0435\\u0442 \\u043d\\u0430 \\u0442\\u043e\\u0442 \\u0438\\u0441\\u0442\\u043e\\u0447"
    "\\u043d\\u0438\\u043a, \\u043a\\u043e\\u0442\\u043e\\u0440\\u044b\\u0439 \\u0432\\u044b\\u0431"
    "\\u0440\\u0430\\u043d \\u0432 \\u0438\\u043d\\u0441\\u043f\\u0435\\u043a\\u0442\\u043e\\u0440"
    "\\u0435; \\u0441\\u043b\\u0443\\u0447\\u0430\\u0439\\u043d\\u0430\\u044f \\u0440\\u0435\\u043f"
    "\\u043b\\u0438\\u043a\\u0430 \\u0431\\u0440\\u043e\\u0441\\u0430\\u0435\\u0442 \\u043a\\u0443"
    "\\u0431\\u0438\\u043a\\u0438 \\u0434\\u043b\\u044f \\u043a\\u0430\\u0436\\u0434\\u043e\\u0433"
    "\\u043e NPC \\u043e\\u0442\\u0434\\u0435\\u043b\\u044c\\u043d\\u043e."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out345"

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
