#!/usr/bin/env python3
"""Build 314 - a slower pursuit and locomotion that does not step.

  1. Pace, again a notch down. The walk factor goes from 1.15 to 1.05 of the NPC's ordinary walk
     and the catch-up floor from 1.25 to 1.15 with half of the node's excess boost instead of 60%,
     so 1.05x and 1.15x to 2.15x instead of 1.15x and 1.25x to 2.45x.

  2. The jerky walk. NpcLocoDriver283 measured the NPC's speed once a frame, but a mob's client
     position only moves once a tick: two frames of nothing and then a fifth of a block. That
     staircase went straight into the limb phase, so the legs moved in twenty steps a second, and
     into the blend weight, so idle/walk/run snapped. The speed is now smoothed over 60 ms and the
     phase is advanced by the smoothed value, with longer cross-fades (120 ms in, 300 ms out).

  3. The same values now reach every reader. Build 283 only replaced the two reads inside the
     bridge; Touch'n Motion's animator asks the walk state itself, and got the stepped numbers,
     which is why its blends looked jerky next to a player's. Mixin NpcLocoInputsMixin314 makes the
     state answer with the smoothed values for the states the driver has sampled, and nothing else.

Seventeen classes change: NpcPursuit269 with its four inner classes, NpcLocoDriver283 with its
state, and the new NpcLocoInputsMixin314.

Usage: python3 tools/patch_build314.py <base-313.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build313-follow"
NEW_VERSION = "0.8.0-build314-smooth"
OUT_NAME = "settledlands-0.8.0-build314-smooth.jar"

REPLACED = (
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/npc/NpcPursuit269$Published.class",
    "dev/settledlands/npc/NpcPursuit269$TargetCache.class",
    "dev/settledlands/npc/NpcPursuit269$SlotCache.class",
    "dev/settledlands/model/NpcLocoDriver283.class",
    "dev/settledlands/model/NpcLocoDriver283$State.class",
)

ADDED = (
    "dev/settledlands/mixin/NpcLocoInputsMixin314.class",
)

MIXIN = "NpcLocoInputsMixin314"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcPursuit269.class": "latchedBody",
    "dev/settledlands/npc/NpcPursuit269$State.class": "damageSource",
    "dev/settledlands/model/NpcLocoDriver283.class": "SPEED_SECONDS",
    "dev/settledlands/mixin/NpcLocoInputsMixin314.class": "settledlands$position",
}

CHANGELOG_EN = (
    "Build 314 slows the pursuit down another notch and smooths the walk. The follower now walks at "
    "1.05x the ordinary walk and catches up at 1.15x to 2.15x. The jerky movement is fixed where it "
    "started: the locomotion input was measured per frame from a position that only moves per tick, "
    "so the limb phase stepped twenty times a second and the idle/walk/run blend snapped. The speed "
    "is smoothed now and that value is what the model and Touch'n Motion read, so the transitions "
    "blend instead of cutting. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 314: \u0448\u0430\u0433 \u0435\u0449\u0451 "
    "\u043d\u0435\u043c\u043d\u043e\u0433\u043e \u043c\u0435\u0434\u043b\u0435\u043d\u043d\u0435\u0435 "
    "\u0438 \u043f\u043b\u0430\u0432\u043d\u0430\u044f \u0430\u043d\u0438\u043c\u0430\u0446\u0438\u044f. "
    "\u0425\u043e\u0434\u044c\u0431\u0430 1.05\u00d7 \u043e\u0442 "
    "\u043e\u0431\u044b\u0447\u043d\u043e\u0439, \u0434\u043e\u0433\u043e\u043d 1.15\u00d7\u2026"
    "2.15\u00d7. \u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c \u0442\u0435\u043f\u0435\u0440\u044c "
    "\u0441\u0433\u043b\u0430\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f \u0437\u0430 60 \u043c\u0441, "
    "\u0444\u0430\u0437\u0430 \u0448\u0430\u0433\u0430 \u0438\u0434\u0451\u0442 \u043f\u043e "
    "\u0441\u0433\u043b\u0430\u0436\u0435\u043d\u043d\u043e\u0439 \u0441\u043a\u043e\u0440\u043e\u0441\u0442\u0438, "
    "\u043f\u0435\u0440\u0435\u0445\u043e\u0434\u044b \u0434\u043b\u0438\u043d\u043d\u0435\u0435 "
    "(120/300 \u043c\u0441). \u042d\u0442\u0438 \u0436\u0435 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f "
    "\u0442\u0435\u043f\u0435\u0440\u044c \u0447\u0438\u0442\u0430\u0435\u0442 \u0438 "
    "Touch'n Motion (\u043c\u0438\u043a\u0441\u0438\u043d NpcLocoInputsMixin314), "
    "\u0430 \u043d\u0435 \u0442\u043e\u043b\u044c\u043a\u043e \u043c\u043e\u0441\u0442. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out314"

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
