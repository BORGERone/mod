#!/usr/bin/env python3
"""Build 313 - a pursuer that keeps walking while the target walks.

Build 312 fixed the path churn, but a follower still stopped at two places:

  1. the standoff. Within keep distance the pursuit counted itself as "arrived" and braked, and
     it only set off again once the target was a further margin away. With the default 1.6 blocks
     a walking player crossed that gap every second, so the body started and stopped in a loop -
     the "escort waiting for you" look. It is now "arrived" only while the target is standing
     still: per-tick target movement above TARGET_MOVING_SQ means the pursuit keeps walking.

  2. the end of a path. When the path ran out, the next one waited for the target to slide three
     blocks (and for the retry cooldown). A short FOLLOW_STEP_SQ now means the next path is built
     as soon as the target has moved at all, so the body keeps its stride between paths.

Five classes change, all of them from NpcPursuit269.

Usage: python3 tools/patch_build313.py <base-312.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build312-pursuit-pacing"
NEW_VERSION = "0.8.0-build313-follow"
OUT_NAME = "settledlands-0.8.0-build313-follow.jar"

REPLACED = (
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/npc/NpcPursuit269$Published.class",
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class",
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class",
)

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcPursuit269.class": "latchedBody",
    "dev/settledlands/npc/NpcPursuit269$State.class": "damageSource",
}

CHANGELOG_EN = (
    "Build 313 makes a pursuer keep walking while the target walks. It no longer counts itself as "
    "arrived at the standoff distance while the target is moving, so it follows continuously "
    "instead of walking up, stopping and waiting for you to move on, and the next path is built as "
    "soon as the one it was walking runs out. The pacing of Build 312 is unchanged. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 313: \u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u0442\u0435\u043b\u044c "
    "\u0438\u0434\u0451\u0442 \u0431\u0435\u0437 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043e\u043a, "
    "\u043f\u043e\u043a\u0430 \u0438\u0434\u0451\u0442 \u0446\u0435\u043b\u044c: "
    "\u0441\u0442\u043e\u0438\u0442 \u0442\u043e\u043b\u044c\u043a\u043e \u0442\u043e\u0433\u0434\u0430, "
    "\u043a\u043e\u0433\u0434\u0430 \u0446\u0435\u043b\u044c \u0432\u0441\u0442\u0430\u043b\u0430 "
    "\u0441\u0430\u043c\u0430. \u041d\u043e\u0432\u044b\u0439 \u043f\u0443\u0442\u044c "
    "\u0441\u0442\u0440\u043e\u0438\u0442\u0441\u044f \u0441\u0440\u0430\u0437\u0443, "
    "\u043a\u0430\u043a \u0442\u043e\u043b\u044c\u043a\u043e \u0446\u0435\u043b\u044c "
    "\u0441\u043c\u0435\u0441\u0442\u0438\u043b\u0430\u0441\u044c. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out313"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name not in blobs:
            raise SystemExit("base jar has no entry: %s" % name)
        want = EXPECTED_METHODS.get(name)
        if want and want.encode("utf-8") not in data:
            raise SystemExit("%s does not look like the build we expect (no %s)"
                             % (name, want))
        blobs[name] = data
        print("  replaced: %s (%d bytes)" % (name, len(data)))

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
