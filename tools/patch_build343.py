#!/usr/bin/env python3
"""
Build 343 - Cinematic Interactions' scene hands, without the mixin shape that killed the client.

  Build 342 handed a possessed player's scene arm to Cinematic's own model-arm pass, but reached that
  private method through a Mixin @Invoker declared in an interface mixin. Mixin refuses that outright:
  an interface mixin may only target an interface, so the client died in PREPARE with

      @Mixin target type mismatch: dev.dyslexic.cinematic.client.Hands is not an interface

  and never reached the title screen. Build 343 keeps the same hand-over and drops the shape Mixin
  rejects: the injection stays a plain class mixin, and the private method is looked up once, on the
  first scene hand that needs it, then remembered. A Cinematic build whose method has another shape now
  keeps its own arm instead of stopping the game.

  Base: 342 (broken - the whole reason for this build). New classes: the guard and the injection; all
  three 342 classes and both 342 mixin entries go away.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build342-cinematic-arms"
NEW_VERSION = "0.8.0-build343-cinematic-arms"
OUT_NAME = "settledlands-0.8.0-build343-cinematic-arms.jar"

# Build 342's shapes: the interface mixin is the one Mixin refused at startup.
REMOVED = (
    "dev/settledlands/npc/client/CinematicArms342.class",
    "dev/settledlands/mixin/CinematicHands342.class",
    "dev/settledlands/mixin/CinematicHandsInvoker342.class",
)
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/CinematicArms343.class",
    "dev/settledlands/mixin/CinematicHands343.class",
)

MIXINS = ()
CLIENT_MIXINS = ("CinematicHands343",)
CLIENT_REMOVED = ("CinematicHands342", "CinematicHandsInvoker342")

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/CinematicArms343.class": (
        "SettledLands/CinematicArms343", "handOwner", "REPORT_LIMIT", "invokeExact"),
    "dev/settledlands/mixin/CinematicHands343.class": (
        "dev.dyslexic.cinematic.client.Hands", "settledlands$npcSceneHand", "drawArm"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 343 gives the Cinematic Interactions scene hands to the possessed body, without the mixin "
    "shape that stopped build 342 at startup. A possessed player's scene arm is handed to Cinematic's "
    "own model-arm pass - the one it uses with Yes Steve Model - which places the arm at the station "
    "and calls the very hand method Build 339 already gives to the NPC, so the arm keeps Cinematic's "
    "placement, reach and held item and the hand that appears is the NPC's. The private method is "
    "looked up once instead of being declared as a mixin accessor, which Mixin refuses for a class "
    "target; an unpossessed player, an arm Cinematic would not draw, and any failure all keep "
    "Cinematic's own arm, so a scene never loses a hand."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 343: \u0440\u0443\u043a\u0438 \u0441\u0446\u0435\u043d"
    "\u044b Cinematic Interactions \u0442\u0435\u043f\u0435\u0440\u044c \u0440\u0443\u043a\u0438 NPC,"
    " \u0430 \u043d\u0435 \u0441\u043a\u0438\u043d \u0438\u0433\u0440\u043e\u043a\u0430; \u0444"
    "\u043e\u0440\u043c\u0430 \u043c\u0438\u043a\u0441\u0438\u043d\u0430 \u0438\u0437 342, \u043d"
    "\u0430 \u043a\u043e\u0442\u043e\u0440\u043e\u0439 \u0438\u0433\u0440\u0430 \u043f\u0430\u0434"
    "\u0430\u043b\u0430 \u043f\u0440\u0438 \u0437\u0430\u043f\u0443\u0441\u043a\u0435, \u0443\u0431"
    "\u0440\u0430\u043d\u0430."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out343"

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
