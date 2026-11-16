#!/usr/bin/env python3
"""Build 312 - the pursuit pacing and the stop-turn-go stutter.

Build 311 fixed two real bugs and overshot on both: the chase read as a sprint, and the body kept
stopping, turning and setting off again.

  1. Pacing. Build 311 ran the pursuit at the catch-up base (0.16) with a catch-up factor of 1.4 to
     1.9, so 1.6x to 3.04x the ordinary 0.10 walk. The walk is now 1.15x (0.115) and the catch-up
     is 1.25x plus 60% of the node's excess boost, so 1.55x by default and 2.45x at the maximum.

  2. Stutter. Three things braked a moving body: the path was rebuilt whenever the target slid two
     blocks (each rebuild drops the current path, and the mob has to stop and turn), the catch-up
     mode change zeroed the motion, and a failed path backed off for up to 160 ticks while standing.
     The path is now kept until it is finished, the mode change no longer brakes, the backoff is
     capped at 40 ticks and the watchdog no longer zeroes the motion.

Five classes change, all of them from NpcPursuit269.

Usage: python3 tools/patch_build312.py <base-311.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build311-pursuit-fix"
NEW_VERSION = "0.8.0-build312-pursuit-pacing"
OUT_NAME = "settledlands-0.8.0-build312-pursuit-pacing.jar"

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
    "Build 312 sets the pace of a pursuit and removes the stutter. A chasing NPC now walks at "
    "1.15x its ordinary walk and at 1.25x to 2.45x while catching up, instead of the 1.6x to 3.04x "
    "of Build 311. The stop-turn-go look is gone as well: the path is kept until it is finished "
    "instead of being rebuilt every couple of blocks, changing catch-up mode no longer brakes the "
    "body, and a failed path retries within two seconds instead of standing for up to eight. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 312: \u0442\u0435\u043c\u043f "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u044f "
    "\u0438 \u0440\u0432\u0430\u043d\u044b\u0435 \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u044f. "
    "\u0428\u0430\u0433 \u0432\u043c\u0435\u0441\u0442\u043e \u00d71.6\u2026\u00d73.04 \u2014 "
    "\u00d71.15 \u0432 \u043e\u0431\u044b\u0447\u043d\u043e\u043c "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u0438, "
    "\u00d71.25\u2026\u00d72.45 \u0432 \u0434\u043e\u0433\u043e\u043d\u0435. "
    "\u041f\u0443\u0442\u044c \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 "
    "\u043f\u0435\u0440\u0435\u0441\u0442\u0440\u0430\u0438\u0432\u0430\u0435\u0442\u0441\u044f "
    "\u043a\u0430\u0436\u0434\u044b\u0435 \u0434\u0432\u0430 \u0431\u043b\u043e\u043a\u0430, "
    "\u0441\u043c\u0435\u043d\u0430 \u0440\u0435\u0436\u0438\u043c\u0430 \u043d\u0435 "
    "\u0442\u043e\u0440\u043c\u043e\u0437\u0438\u0442 \u0442\u0435\u043b\u043e, "
    "\u043f\u043e\u0432\u0442\u043e\u0440 \u043f\u0443\u0442\u0438 \u2014 \u043d\u0435 "
    "\u043f\u043e\u0437\u0436\u0435 \u0434\u0432\u0443\u0445 \u0441\u0435\u043a\u0443\u043d\u0434. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out312"

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
