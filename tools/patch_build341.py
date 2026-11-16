#!/usr/bin/env python3
"""
Build 341 - Touch'n Motion's motion reaches the profession rig too.

  Build 338 left a possessed player with no hands at all. It drew the NPC arm from the arm's own place
  in the model - correct only for a PoseStack the player model itself is being drawn into - while
  Punchy! hands over its own hand space, and it cancelled Punchy!'s arm whether or not anything had
  been drawn. The arm therefore landed off screen and the player's own arm was suppressed with it.

  The possessed player's body is retargeted from the player model's Fresh Animations pose only when
  the actor model answers to ivy or ivy_oleander (PlayerMotionBridge.isIvy). The mod also ships
  ivy_oleander_professions - the same rig with the profession bones, the one every NPC with a profession
  uses - and it was not on that list, so such a body kept its own clips: Touch'n Motion worked its
  gesture out and stored it, but there was no retarget to carry it onto the bones.

  Build 341 widens that one name test to every Ivy rig by prefix, and only that: the bridge's own rig
  validation still refuses anything without body/waist/limb bones. Build 340's listener is removed -
  the bridge already calls Touch'n Motion's entry point itself.

  Base: 340. New classes: the rig test and the injection; 340's listener goes away.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build340-tnm-possession"
NEW_VERSION = "0.8.0-build341-ivy-professions"
OUT_NAME = "settledlands-0.8.0-build341-ivy-professions.jar"

# Build 340 asked Touch'n Motion for the gesture a second time per frame; the bridge already does it.
REMOVED = (
    "dev/settledlands/npc/client/TouchNMotionPose340.class",
)
REPLACED = ()
ADDED = (
    "dev/settledlands/model/IvyRig341.class",
    "dev/settledlands/mixin/IvyRigGateMixin341.class",
)

MIXINS = ()
CLIENT_MIXINS = ("IvyRigGateMixin341",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/model/IvyRig341.class": ("ivy", "REPORT_LIMIT", "SettledLands/IvyRig341"),
    "dev/settledlands/mixin/IvyRigGateMixin341.class": (
        "dev.settledlands.model.PlayerMotionBridge", "isIvy", "settledlands$professionRig"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 341 lets the pose retarget reach the profession rig. The possessed player's body is "
    "retargeted from the player model's Fresh Animations pose only for the model names ivy and "
    "ivy_oleander, and the profession variant - the same rig with the profession bones, used by every "
    "NPC with a profession - was not among them, so such a body kept its own clips and Touch'n "
    "Motion's gesture never arrived. That one name test now accepts every Ivy rig; the rig validation "
    "behind it is untouched. Build 340's listener is removed: the bridge already asks Touch'n Motion "
    "for the gesture itself."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 341: \u043f\u043e\u0437\u0430 Fresh Animations"
    " / Touch'n Motion \u0442\u0435\u043f\u0435\u0440\u044c \u043f\u0435\u0440\u0435"
    "\u043d\u043e\u0441\u0438\u0442\u0441\u044f \u0438 \u043d\u0430 \u043f\u0440"
    "\u043e\u0444\u0435\u0441\u0441\u0438\u043e\u043d\u043d\u0443\u044e \u0440\u0438"
    "\u0433\u0443 (ivy_oleander_professions): \u0440\u0430\u043d\u044c\u0448\u0435 \u043f"
    "\u0435\u0440\u0435\u043d\u043e\u0441 \u0440\u0430\u0431\u043e\u0442\u0430\u043b "
    "\u0442\u043e\u043b\u044c\u043a\u043e \u0434\u043b\u044f ivy/ivy_oleander. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out341"

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
