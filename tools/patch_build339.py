#!/usr/bin/env python3
"""
Build 339 - the possessed NPC's arm, drawn where Punchy! puts the arm it replaces.

  Build 338 left a possessed player with no hands at all. It drew the NPC arm from the arm's own place
  in the model - correct only for a PoseStack the player model itself is being drawn into - while
  Punchy! hands over its own hand space, and it cancelled Punchy!'s arm whether or not anything had
  been drawn. The arm therefore landed off screen and the player's own arm was suppressed with it.

  Build 339 takes the arm Punchy! is about to draw and applies that arm's own transform
  (ModelPart.translateAndRotate - the same call the accepted community bridge uses) before drawing the
  NPC arm around that pivot. Whatever space Punchy! works in, the NPC arm lands on the arm it
  replaces, and it keeps the swing, the punch and any interaction reach the player model carries that
  frame. Punchy!'s arm is cancelled only when the NPC arm was really submitted.

  Punchy! is closed source: the hook is a @Pseudo mixin on punchy.client.render.PunchyArmRenderer
  with require = 0, and no action at all unless the player is possessed.

  Base: 338. New classes: the arm replacement and the hook; the 338 pair is removed.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build338-punchy-arms"
NEW_VERSION = "0.8.0-build339-punchy-arm-anchor"
OUT_NAME = "settledlands-0.8.0-build339-punchy-arm-anchor.jar"

# Build 338 drew the arm in the wrong space and cancelled Punchy!'s arm regardless; both its classes
# go away with it.
REMOVED = (
    "dev/settledlands/npc/client/PunchyArm338.class",
    "dev/settledlands/mixin/PunchyArmBridgeMixin338.class",
)
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/PunchyArm339.class",
    "dev/settledlands/npc/client/PunchyArm339$ArmCache.class",
    "dev/settledlands/mixin/PunchyArmBridgeMixin339.class",
)

# Rendering only, and it touches Punchy!'s classes: client side.
MIXINS = ()
CLIENT_MIXINS = ("PunchyArmBridgeMixin339",)
CLIENT_REMOVED = ("PunchyArmBridgeMixin338",)

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
    "Build 339 puts the hands back in first person with Punchy! installed. Build 338 cancelled "
    "Punchy!'s first-person arm and drew the NPC's arm from the arm's own place in the model, which "
    "is only right for the pose the player model itself is drawn into - Punchy! hands over its own "
    "hand space, so a possessed player was left with no hands at all. The mod now applies the "
    "transform of the very arm Punchy! is about to draw and puts the NPC's arm on that pivot, so it "
    "faces and moves exactly where Punchy!'s arm would, and Punchy!'s arm is cancelled only when the "
    "NPC arm was really drawn. Without Punchy! installed nothing changes, and nothing happens at all "
    "unless the player is possessed."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 339: \u0440\u0443\u043a\u0438 \u043e"
    "\u0442 \u043f\u0435\u0440\u0432\u043e\u0433\u043e \u043b\u0438\u0446\u0430 "
    "\u0441 Punchy! \u0441\u043d\u043e\u0432\u0430 \u0432\u0438\u0434\u043d\u044b"
    ": \u0440\u0443\u043a\u0430 NPC \u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f "
    "\u0432 \u0442\u043e\u0439 \u0436\u0435 \u0442\u043e\u0447\u043a\u0435 \u0438 "
    "\u0441 \u0442\u043e\u0439 \u0436\u0435 \u0430\u043d\u0438\u043c\u0430\u0446"
    "\u0438\u0435\u0439, \u0447\u0442\u043e \u0438 \u0440\u0443\u043a\u0430 "
    "\u0438\u0433\u0440\u043e\u043a\u0430, \u0430 \u0440\u0443\u043a\u0430 Punchy! "
    "\u043e\u0442\u043c\u0435\u043d\u044f\u0435\u0442\u0441\u044f \u0442\u043e\u043b"
    "\u044c\u043a\u043e \u0435\u0441\u043b\u0438 \u0440\u0443\u043a\u0430 NPC "
    "\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0442\u0435\u043b\u044c\u043d\u043e "
    "\u043d\u0430\u0440\u0438\u0441\u043e\u0432\u0430\u043d\u0430. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out339"

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
