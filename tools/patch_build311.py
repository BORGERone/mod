#!/usr/bin/env python3
"""Build 311 - the pursuit that stopped after ten seconds, and its walking speed.

Two fixes on top of Build 310, both in the pursuit runtime.

  1. The chase stopped after ten seconds. The "Урон" node names its attacker for ten seconds only
     (NpcDamageValue290.WINDOW_TICKS = 200). A pursuit started by that node re-read the node every
     tick, so once the window closed the source answered "nobody", the pursuit dropped its path and
     the NPC stood still. The body the node named once is now latched in the pursuit state and kept
     until it dies or leaves the level.

  2. A pursuit walked slower than ordinary walking. GuideNpc#walkingNavigationSpeed multiplies the
     navigation speed by the auto-AI peace-time damping (x0.55 while the NPC is not fighting), and
     the pursuit used that value as its base. The damping is now divided back out, so the pursuit
     walks at the catch-up base (0.16 against the ordinary walk's 0.10) and the catch-up adds its
     push on top.

Five classes change, all of them from NpcPursuit269: the class itself and its four inner classes.

Usage: python3 tools/patch_build311.py <base-310.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build310-blue-wire"
NEW_VERSION = "0.8.0-build311-pursuit-fix"
OUT_NAME = "settledlands-0.8.0-build311-pursuit-fix.jar"

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
    "Build 311 fixes the pursuit that froze after ten seconds and walks it at a real speed. The "
    "damage node only names its attacker for ten seconds, so a chase started by it lost its target "
    "mid-way and the NPC stood still; the body named once is now kept for as long as it is alive "
    "and in the level. The pursuit also walked slower than ordinary walking, because the auto-AI "
    "peace-time damping was left in the navigation speed; it is divided back out, so a pursuit "
    "walks at 1.6x the ordinary walk and the catch-up still adds its push on top. Nothing else is "
    "changed against Build 310. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 311: \u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u0435 "
    "\u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0432\u0441\u0442\u0430\u0451\u0442 "
    "\u0447\u0435\u0440\u0435\u0437 10 \u0441\u0435\u043a\u0443\u043d\u0434 \u0438 "
    "\u0438\u0434\u0451\u0442 \u0431\u044b\u0441\u0442\u0440\u0435\u0435 "
    "\u043e\u0431\u044b\u0447\u043d\u043e\u0439 \u0445\u043e\u0434\u044c\u0431\u044b. "
    "\u041d\u043e\u0434\u0430 \u00ab\u0423\u0440\u043e\u043d\u00bb \u043f\u043e\u043c\u043d\u0438\u0442 "
    "\u0430\u0442\u0430\u043a\u0443\u044e\u0449\u0435\u0433\u043e 10 \u0441\u0435\u043a\u0443\u043d\u0434, "
    "\u0446\u0435\u043b\u044c \u0442\u0435\u043f\u0435\u0440\u044c "
    "\u0437\u0430\u043f\u043e\u043c\u0438\u043d\u0430\u0435\u0442\u0441\u044f "
    "\u0434\u043e \u0441\u043c\u0435\u0440\u0442\u0438 \u0438\u043b\u0438 "
    "\u0443\u0445\u043e\u0434\u0430. "
    "\u0421\u043a\u043e\u0440\u043e\u0441\u0442\u044c: \u0443\u0431\u0440\u0430\u043d "
    "\u043c\u0438\u0440\u043d\u044b\u0439 \u043c\u043d\u043e\u0436\u0438\u0442\u0435\u043b\u044c "
    "\u00d70.55, \u0445\u043e\u0434\u044c\u0431\u0430 "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u044f \u2014 1.6\u00d7 "
    "\u043e\u0442 \u043e\u0431\u044b\u0447\u043d\u043e\u0439. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out311"

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
