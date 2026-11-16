#!/usr/bin/env python3
"""
Build 342 - Cinematic Interactions' scene hands become the possessed NPC's hands.

  Cinematic Interactions replaces block windows with first-person scenes and draws the hands of a scene
  itself: Hands.drawArm builds its own arm cubes around the station and textures them with
  LocalPlayer.getSkin(). A possessed player is drawn as an Ivy rig, so the scene gave them the player's
  own arm hanging over the crafting table - the NPC's body, the player's hand.

  Cinematic already owns a second path for that arm - Hands.drawModelArm, used with Yes Steve Model -
  which places the arm at the station and then calls PlayerRenderer.renderRightHand /
  renderLeftHand. That is exactly the method Build 339 hands over to the NPC, so the fix is to route a
  possessed player's scene arm onto that path and leave everything else - placement, reach, bob, the
  item in hand - Cinematic's own.

  Base: 341. New classes: the guard and the two mixins (the injection and the invoker into Cinematic's
  private model-arm pass).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build341-ivy-professions"
NEW_VERSION = "0.8.0-build342-cinematic-arms"
OUT_NAME = "settledlands-0.8.0-build342-cinematic-arms.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/CinematicArms342.class",
    "dev/settledlands/mixin/CinematicHands342.class",
    "dev/settledlands/mixin/CinematicHandsInvoker342.class",
)

MIXINS = ()
CLIENT_MIXINS = ("CinematicHands342", "CinematicHandsInvoker342")
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/CinematicArms342.class": (
        "SettledLands/CinematicArms342", "handOwner", "REPORT_LIMIT"),
    "dev/settledlands/mixin/CinematicHands342.class": (
        "dev.dyslexic.cinematic.client.Hands", "settledlands$npcSceneHand", "drawArm"),
    "dev/settledlands/mixin/CinematicHandsInvoker342.class": (
        "dev.dyslexic.cinematic.client.Hands", "settledlands$drawModelArm", "drawModelArm"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 342 gives the Cinematic Interactions scene hands to the possessed body. Cinematic draws the "
    "hands of a scene itself and textures them with the player's own skin, so a possessed player saw the "
    "player's arm at the NPC's crafting table. Cinematic's own model-arm pass - the one it uses with Yes "
    "Steve Model - places the arm at the station and then calls the very hand method Build 339 already "
    "gives to the NPC, so a possessed player's scene arm is routed there: placement, reach and the held "
    "item stay Cinematic's, the hand that appears is the NPC's. Unpossessed players and every other "
    "scene pass are untouched, and a scene never loses a hand."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 342: \u0440\u0443\u043a\u0438 \u0441\u0446\u0435\u043d\u044b"
    " Cinematic Interactions \u0442\u0435\u043f\u0435\u0440\u044c \u0440\u0443\u043a\u0438 NPC,"
    " \u0430 \u043d\u0435 \u0441\u043a\u0438\u043d \u0438\u0433\u0440\u043e\u043a\u0430."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out342"

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
