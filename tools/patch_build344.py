#!/usr/bin/env python3
"""
Build 344 - the legs Cinematic Interactions folds at a campfire are the possessed NPC's legs.

  Cinematic's campfire scene is its only scene with a body: CampfireStation.renderBody works out a hip,
  a knee and an ankle for each leg and hands every segment to LegMesh.draw, which builds thigh and shin
  cubes and textures them with LocalPlayer.getSkin(). A possessed player is an Ivy rig everywhere else,
  so the fire gave them the player's own crossed legs under the NPC's hands.

  Build 344 places the NPC model's own leg bone on Cinematic's joints instead - stretched onto each
  segment and turned onto it, with the NPC's own texture - and keeps Cinematic's armour pass on top so
  leggings and boots stay. Nothing about the camera, the station pose or the joints changes. The method
  and the class are reached by name, resolved once, and any failure leaves Cinematic's own legs.

  Base: 343 (hands only). New classes: the leg guard and one class mixin injection on LegMesh.draw.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build343-cinematic-arms"
NEW_VERSION = "0.8.0-build344-cinematic-legs"
OUT_NAME = "settledlands-0.8.0-build344-cinematic-legs.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/CinematicLegs344.class",
    "dev/settledlands/npc/client/CinematicLegs344$LegCache.class",
    "dev/settledlands/mixin/CinematicLegMesh344.class",
)

MIXINS = ()
CLIENT_MIXINS = ("CinematicLegMesh344",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/CinematicLegs344.class": (
        "SettledLands/CinematicLegs344", "drawLegs", "REPORT_LIMIT", "rotationTo"),
    "dev/settledlands/npc/client/CinematicLegs344$LegCache.class": ("skipDraw",),
    "dev/settledlands/mixin/CinematicLegMesh344.class": (
        "dev.dyslexic.cinematic.client.LegMesh", "settledlands$npcSceneLegs", "draw"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 344 gives the legs Cinematic Interactions folds at a campfire to the possessed body. The "
    "campfire is Cinematic's only scene with a body: it works out a hip, a knee and an ankle for each "
    "leg and draws its own cubes with the player's skin, so a possessed player sat at the fire with the "
    "player's legs. The NPC model's own leg bone is now placed on those very joints - stretched onto "
    "each segment, turned onto it, textured with the NPC's own texture - and Cinematic's armour pass "
    "still draws leggings and boots on top. The camera, the station pose and the joints are untouched; "
    "an unpossessed player, a model without a leg bone and any failure all keep Cinematic's own legs."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 344: \u043d\u043e\u0433\u0438 \u0432 \u0441"
    "\u0446\u0435\u043d\u0435 \u043a\u043e\u0441\u0442\u0440\u0430 Cinematic \u0442\u0435"
    "\u043f\u0435\u0440\u044c \u043d\u043e\u0433\u0438 NPC, \u0430 \u043d\u0435 \u0441"
    "\u043a\u0438\u043d \u0438\u0433\u0440\u043e\u043a\u0430; \u0441\u0443\u0441\u0442"
    "\u0430\u0432\u044b, \u043f\u043e\u0437\u0430 \u0441\u0442\u0430\u043d\u0446\u0438"
    "\u0438 \u0438 \u0440\u0443\u043a\u0438 343 \u043d\u0435 \u0442\u0440\u043e\u043d"
    "\u0443\u0442\u044b."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out344"

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
            raise SystemExit("%s must not be a common mixin - it draws on the client" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (+%s)"
          % (len(common), len(client), ", ".join(CLIENT_MIXINS)))

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
