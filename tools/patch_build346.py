#!/usr/bin/env python3
"""
Build 346 - a bounded trace of the damage chain, to find where a damage node is lost.

Build 345 was built on a wrong cause: the damage-source numbering was never wrong (the switch in
GuideDialogue.matchesDamageFilter reads 0 any, 1 player, 2 monster, 3 non-player, the same order the
dropdown and the node card use). A node still does not fire, and the log of that session shows the hit
reaching GuideNpc.hurt while no trigger line ever appears, so the loss is inside the chain: the graph
the chain walks, the node in it, or one of its gates.

This build changes no behaviour. It writes, once per hit and at most 80 lines per session:

  [dmg] hit npc='...' amount=... source=... profile=... own=enabled=... entry=... nodes=... damage=...
  [dmg] chain npc='...' graph=... amount=... gameTime=...
  [dmg]   node #4 type=6 min=1 filter=1(player) cooldown=1s if-in=0 -> MATCH

which answers the three questions the log cannot answer today: did the hit arrive, is the graph the
chain reads the graph the author edited (and enabled), and which gate stops the node.

Base: 345.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build345-graph-fixes"
NEW_VERSION = "0.8.0-build346-damage-trace"
OUT_NAME = "settledlands-0.8.0-build346-damage-trace.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcDamageTrace346.class",
    "dev/settledlands/mixin/NpcDamageTraceHurtMixin346.class",
    "dev/settledlands/mixin/NpcDamageTraceChainMixin346.class",
)

MIXINS = ("NpcDamageTraceHurtMixin346", "NpcDamageTraceChainMixin346")
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/NpcDamageTrace346.class": (
        "SettledLands/DamageTrace346", "chain", "hurt"),
    "dev/settledlands/mixin/NpcDamageTraceHurtMixin346.class": (
        "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", "settledlands$traceHurt"),
    "dev/settledlands/mixin/NpcDamageTraceChainMixin346.class": (
        "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;"
        "Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;"
        "Ldev/settledlands/npc/NpcDialogueGraph;)V", "settledlands$traceChain"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 346 adds a bounded trace of the damage chain and changes no behaviour. A damage node is "
    "still not firing for its author, and the log of that session shows the hit reaching "
    "GuideNpc.hurt with no trigger line after it, so the node is lost inside the chain. Three log "
    "lines per hit - the hit itself, the graph the chain walks with its enabled flag and node count, "
    "and every damage node of that graph with the verdict of each gate - say exactly which of those "
    "stops it. At most 80 lines per session, then the trace switches itself off."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 346: \\u0442\\u0440\\u0430"
    "\\u0441\\u0441\\u0438\\u0440\\u043e\\u0432\\u043a\\u0430 \\u0446\\u0435"
    "\\u043f\\u043e\\u0447\\u043a\\u0438 \\u0443\\u0440\\u043e\\u043d\\u0430 "
    "(\\u0434\\u043e 80 \\u0441\\u0442\\u0440\\u043e\\u043a \\u0437\\u0430 "
    "\\u0441\\u0435\\u0441\\u0441\\u0438\\u044e, \\u043f\\u043e\\u0432\\u0435"
    "\\u0434\\u0435\\u043d\\u0438\\u0435 \\u043d\\u0435 \\u043c\\u0435\\u043d"
    "\\u044f\\u0435\\u0442)."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out346"

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
