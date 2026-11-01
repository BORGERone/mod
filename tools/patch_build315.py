#!/usr/bin/env python3
"""Build 315 - a cross-fade between animation clips.

Switching an animation still cut: the new clip starts at its first frame and its whole pose is
written into the model in one frame, so an arm that was at 80 degrees is at -20 the next frame.
That is the teleport between animations.

Every clip pose - full body or masked - reaches the model through one place,
NpcMotionBridge256.Pose#set(ModelPart). NpcPoseBlend315 is called right behind it and blends the
values the clip just wrote with the values the part had on the previous frame, over 90 ms. The
part walks from where it was to where the clip wants it; a clip that restarts, a bone overridden
for a single frame and the end of a masked animation are smoothed by the same filter.

Two classes are added, NpcPoseBlend315 and the mixin NpcPoseBlendMixin315. Nothing else changes
against Build 314.

Usage: python3 tools/patch_build315.py <base-314.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build314-smooth"
NEW_VERSION = "0.8.0-build315-pose-fade"
OUT_NAME = "settledlands-0.8.0-build315-pose-fade.jar"

REPLACED = (
)

ADDED = (
    "dev/settledlands/model/NpcPoseBlend315.class",
    "dev/settledlands/mixin/NpcPoseBlendMixin315.class",
)

MIXIN = "NpcPoseBlendMixin315"

EXPECTED_METHODS = {
    "dev/settledlands/model/NpcPoseBlend315.class": "FADE_SECONDS",
    "dev/settledlands/mixin/NpcPoseBlendMixin315.class": "settledlands$blendPose",
}

CHANGELOG_EN = (
    "Build 315 cross-fades animation clips. A clip switch used to write the whole new pose into the "
    "model in a single frame, which looked like a teleport between animations; every pose a clip "
    "writes is now blended with the previous frame over 90 ms, for full-body and masked animations "
    "alike. Restarting a clip and leaving a masked animation are smoothed by the same filter. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 315: \u043f\u043b\u0430\u0432\u043d\u043e\u0435 "
    "\u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435 "
    "\u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0439. \u0420\u0430\u043d\u044c\u0448\u0435 "
    "\u043d\u043e\u0432\u0430\u044f \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f "
    "\u0437\u0430\u043f\u0438\u0441\u044b\u0432\u0430\u043b\u0430\u0441\u044c \u0432 "
    "\u043c\u043e\u0434\u0435\u043b\u044c \u0437\u0430 \u043e\u0434\u0438\u043d \u043a\u0430\u0434\u0440 "
    "\u2014 \u043e\u0442\u0441\u044e\u0434\u0430 \u0442\u0435\u043b\u0435\u043f\u043e\u0440\u0442 "
    "\u043c\u0435\u0436\u0434\u0443 \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f\u043c\u0438. "
    "\u0422\u0435\u043f\u0435\u0440\u044c \u043f\u043e\u0437\u0430 "
    "\u0441\u043c\u0435\u0448\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0441 "
    "\u043f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0438\u043c \u043a\u0430\u0434\u0440\u043e\u043c "
    "\u0437\u0430 90 \u043c\u0441, \u0434\u043b\u044f \u043f\u043e\u043b\u043d\u044b\u0445 \u0438 "
    "\u0447\u0430\u0441\u0442\u0438\u0447\u043d\u044b\u0445 \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u0439. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    # Compiled classes come from a build directory next to this script's repo root.
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out315"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED + ADDED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        want = EXPECTED_METHODS.get(name)
        if want and want.encode("utf-8") not in data:
            raise SystemExit("%s does not look like the build we expect (no %s)"
                             % (name, want))
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    if MIXIN not in mixins.setdefault("client", []):
        mixins["client"].append(MIXIN)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(mixins.get("mixins", [])),
                                                   len(mixins["client"])))

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
