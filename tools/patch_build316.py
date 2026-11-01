#!/usr/bin/env python3
"""Build 316 - locomotion that matches the speed, and no one frame reset.

  1. The legs moved at their own rate. NpcLocoDriver283 measured blocks per second but compared
     them with FULL_SPEED = 0.24, which is blocks per TICK - twenty times too fast. The blend weight
     was pinned at 1 whatever the NPC did and the limb phase ran eight times the real stride. The
     reference is now 4.317 blocks a second, the speed of a player on foot, and the phase advances
     one vanilla unit per tick at that speed, so a slow walk swings the legs slowly.

  2. A one frame jump when a walk turned into idle. The pose fade started from its own previous
     sample and stood aside when that sample was older than 250 ms, so the first write after the
     animation had been quiet was applied whole. NpcPoseBlend315.capture now records the pose just
     before the clip writes, and the fade always starts from it.

Four classes change: NpcLocoDriver283 with its state, NpcPoseBlend315 and the pose mixin.

Usage: python3 tools/patch_build316.py <base-315.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build315-pose-fade"
NEW_VERSION = "0.8.0-build316-stride"
OUT_NAME = "settledlands-0.8.0-build316-stride.jar"

REPLACED = (
    "dev/settledlands/model/NpcLocoDriver283.class",
    "dev/settledlands/model/NpcLocoDriver283$State.class",
    "dev/settledlands/model/NpcPoseBlend315.class",
    "dev/settledlands/mixin/NpcPoseBlendMixin315.class",
)

ADDED = (
)

MIXIN = "NpcPoseBlendMixin315"

EXPECTED_METHODS = {
    "dev/settledlands/model/NpcLocoDriver283.class": "FULL_SPEED",
    "dev/settledlands/model/NpcPoseBlend315.class": "capture",
    "dev/settledlands/mixin/NpcPoseBlendMixin315.class": "settledlands$capturePose",
}

CHANGELOG_EN = (
    "Build 316 makes the legs follow the speed and removes the one frame break on the way into "
    "idle. The locomotion driver compared a speed in blocks per second with a reference in blocks "
    "per tick, so every NPC walked with the blend weight pinned and the limbs cycling eight times "
    "too fast; the reference is a player's 4.317 blocks a second now. The animation fade also "
    "starts from the pose the model really had just before the clip wrote it, so stopping no longer "
    "cuts for a frame. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 316: \u043d\u043e\u0433\u0438 "
    "\u0434\u0432\u0438\u0433\u0430\u044e\u0442\u0441\u044f \u043f\u043e "
    "\u0441\u043a\u043e\u0440\u043e\u0441\u0442\u0438 \u0438 \u043d\u0435\u0442 "
    "\u043e\u0434\u043d\u043e\u043a\u0430\u0434\u0440\u043e\u0432\u043e\u0433\u043e "
    "\u0441\u0431\u0440\u043e\u0441\u0430 \u0432 idle. \u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c "
    "\u0441\u0440\u0430\u0432\u043d\u0438\u0432\u0430\u043b\u0430\u0441\u044c \u0441 "
    "\u044d\u0442\u0430\u043b\u043e\u043d\u043e\u043c \u0432 20 \u0440\u0430\u0437 "
    "\u043c\u0435\u043d\u044c\u0448\u0435 \u2014 \u0442\u0435\u043f\u0435\u0440\u044c "
    "\u044d\u0442\u0430\u043b\u043e\u043d 4.317 \u0431\u043b\u043e\u043a\u0430/\u0441. "
    "\u041f\u0435\u0440\u0435\u0445\u043e\u0434 \u0432 idle \u0441\u043c\u0435\u0448\u0438\u0432\u0430\u0435\u0442\u0441\u044f "
    "\u043e\u0442 \u0440\u0435\u0430\u043b\u044c\u043d\u043e\u0439 \u043f\u043e\u0437\u044b "
    "\u0434\u043e \u0437\u0430\u043f\u0438\u0441\u0438 \u043a\u043b\u0438\u043f\u0430. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out316"

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
