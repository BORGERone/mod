#!/usr/bin/env python3
"""Build 317 - a guide is not hurt by a fall, does not drown, and floats.

  A guide NPC was an ordinary living entity in the two ways that mattered on rough ground. Walking
  off a ledge or being pushed out of a high window let fallDistance grow, and the vanilla landing
  hurt it; water drained the air supply and, once it was empty, applied drowning damage every tick
  while the body settled on the bottom of the pond and stayed there.

  Two new classes. NpcWater317 holds fallDistance below zero every tick, so the landing tick cannot
  bring it back over the three block threshold; keeps the air supply full while the body is in
  water, so the drowning tick never runs; and adds a small capped push upwards so the NPC comes up
  to the surface instead of sinking. NpcWaterMixin317 hangs that on customServerAiStep - the one
  method every server tick goes through.

Usage: python3 tools/patch_build317.py <base-316.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build316-stride"
NEW_VERSION = "0.8.0-build317-water"
OUT_NAME = "settledlands-0.8.0-build317-water.jar"

REPLACED = (
)

ADDED = (
    "dev/settledlands/npc/NpcWater317.class",
    "dev/settledlands/mixin/NpcWaterMixin317.class",
)

MIXIN = "NpcWaterMixin317"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcWater317.class": "BUOYANCY",
    "dev/settledlands/mixin/NpcWaterMixin317.class": "settledlands$waterTick",
}

CHANGELOG_EN = (
    "Build 317 stops the ground and the water from hurting a guide NPC. The fall distance is held "
    "below zero every tick, so a landing is never turned into damage whatever the height of the "
    "drop, the air supply is kept full while the body is in water, so drowning never starts, and a "
    "small capped push upwards brings the NPC to the surface instead of letting it settle on the "
    "bottom. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 317: NPC "
    "\u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 "
    "\u0443\u0440\u043e\u043d \u043e\u0442 "
    "\u043f\u0430\u0434\u0435\u043d\u0438\u044f, "
    "\u043d\u0435 \u0442\u043e\u043d\u0435\u0442 \u0438 "
    "\u043d\u0435 \u0437\u0430\u0434\u044b\u0445\u0430\u0435\u0442\u0441\u044f. "
    "\u0423\u0440\u043e\u043d \u043e\u0442 "
    "\u043f\u0430\u0434\u0435\u043d\u0438\u044f \u043d\u0435 "
    "\u043d\u0430\u043a\u0430\u043f\u043b\u0438\u0432\u0430\u0435\u0442\u0441\u044f, "
    "\u0437\u0430\u043f\u0430\u0441 \u0432\u043e\u0437\u0434\u0443\u0445\u0430 "
    "\u0432 \u0432\u043e\u0434\u0435 \u0434\u0435\u0440\u0436\u0438\u0442\u0441\u044f "
    "\u043f\u043e\u043b\u043d\u044b\u043c, \u0430 "
    "\u043d\u0435\u0431\u043e\u043b\u044c\u0448\u043e\u0439 "
    "\u0442\u043e\u043b\u0447\u043e\u043a \u0432\u0432\u0435\u0440\u0445 "
    "\u0432\u044b\u043d\u043e\u0441\u0438\u0442 NPC \u043d\u0430 "
    "\u043f\u043e\u0432\u0435\u0440\u0445\u043d\u043e\u0441\u0442\u044c. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out317"

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
    if MIXIN not in mixins.setdefault("mixins", []):
        mixins["mixins"].append(MIXIN)
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
