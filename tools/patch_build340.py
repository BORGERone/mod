#!/usr/bin/env python3
"""
Build 340 - Touch'n Motion's own motion on the body you possess.

  Build 338 left a possessed player with no hands at all. It drew the NPC arm from the arm's own place
  in the model - correct only for a PoseStack the player model itself is being drawn into - while
  Punchy! hands over its own hand space, and it cancelled Punchy!'s arm whether or not anything had
  been drawn. The arm therefore landed off screen and the player's own arm was suppressed with it.

  Touch'n Motion works its gesture out at the head of LivingEntityRenderer.render and stores it; the
  pose is written onto the player model when Entity Model Features copies its animation. The possession
  mod draws the NPC's body instead of the player and cancels the player's render in
  RenderPlayerEvent.Pre, which is fired before that head injection ever runs - so the gesture was never
  worked out for a possessed player and the retargeted body had none of it.

  Build 340 adds a client listener at HIGHEST on RenderPlayerEvent.Pre: for a possessed player it calls
  Touch'n Motion's own ModelSpace#before with the same PoseStack and partial tick the renderer would
  have given it. Nothing is reimplemented; the mod's existing Fresh Animations / Touch'n Motion retarget
  then carries the gesture onto the NPC's bones. Absent mod, changed signature or any failure: the body
  renders as it did before.

  Base: 339. New class: the listener. The 339 arm bridge is untouched.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build339-punchy-arm-anchor"
NEW_VERSION = "0.8.0-build340-tnm-possession"
OUT_NAME = "settledlands-0.8.0-build340-tnm-possession.jar"

# Nothing is removed: the 339 arm bridge stays exactly as it shipped and verified.
REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/TouchNMotionPose340.class",
)

# No mixin: this is a client event listener, auto-registered by its own annotation.
MIXINS = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/TouchNMotionPose340.class": (
        "strm.touchnmotion.ModelSpace", "possessedModelName", "TouchNMotion340"),
}

LANG_SET = {}
LANG_SET_EN = {}

MIXINS = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/PunchyArm339.class": (
        "possessedModelName", "translateAndRotate", "consumeArmor"),
    "dev/settledlands/npc/client/PunchyArm339$ArmCache.class": ("skipDraw", "restore"),
    "dev/settledlands/mixin/PunchyArmBridgeMixin339.class": (
        "punchy.client.render.PunchyArmRenderer", "settledlands$npcArm", "settledlands$noPlayerSleeve"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 340 brings Touch'n Motion's own interaction motion to the body you possess. Touch'n Motion "
    "works its gesture out at the head of the living entity render, and the possession mod cancels that "
    "render before it runs, so a possessed character never reached for a lever. The mod now asks "
    "Touch'n Motion for the gesture ahead of that cancellation, with the same pose and partial tick, and "
    "its existing Fresh Animations retarget carries the motion onto the NPC's bones. Nothing is "
    "reimplemented; without Touch'n Motion, or on a version whose entry point changed, the body renders "
    "exactly as before and the change is reported once."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 340: \u0430\u043d\u0438\u043c\u0430\u0446"
    "\u0438\u0438 \u0432\u0437\u0430\u0438\u043c\u043e\u0434\u0435\u0439\u0441\u0442"
    "\u0432\u0438\u044f Touch'n Motion \u0442\u0435\u043f\u0435\u0440\u044c \u0432\u0438"
    "\u0434\u043d\u044b \u0438 \u043d\u0430 \u0442\u0435\u043b\u0435, \u0432 \u043a"
    "\u043e\u0442\u043e\u0440\u043e\u0435 \u0442\u044b \u0432\u0441\u0435\u043b\u0438"
    "\u043b\u0441\u044f: \u0436\u0435\u0441\u0442 \u0437\u0430\u043f\u0440\u0430\u0448"
    "\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0443 \u0441\u0430\u043c\u043e\u0433"
    "\u043e Touch'n Motion \u0434\u043e \u0442\u043e\u0433\u043e, \u043a\u0430\u043a "
    "\u0432\u0441\u0435\u043b\u0435\u043d\u0438\u0435 \u043e\u0442\u043c\u0435\u043d"
    "\u044f\u0435\u0442 \u043e\u0442\u0440\u0438\u0441\u043e\u0432\u043a\u0443 \u0438"
    "\u0433\u0440\u043e\u043a\u0430. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out340"

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
