#!/usr/bin/env python3
"""Build 327 - a point is put on the cable, and only on a cable that is drawn.

  Two ways a dot could end up somewhere no wire is:

  1. The point was placed where the CURSOR was, and then snapped to the eight-unit grid. The hit
     test takes a click up to six pixels off the cable, and the grid moves it four more, so a dot
     could stand beside its cable with no bend in sight. Now the click is projected onto the cable
     itself and the point is put there; the grid is applied only while it keeps the point on the
     cable. The store no longer snaps a point it is given, so nothing moves it afterwards.
  2. The wire was keyed with a port number the screen never draws. The screen draws branch i of a
     random node with port i, but a click on a branch was stored under port 0, so no cable was ever
     looked up under that key: a dot was drawn and no cable passed through it. A click now resolves
     to the port the screen draws with, and a point whose key is not drawn by the screen is dropped
     instead of being drawn as a floating dot.

  tools/tests/WireKeyTest.java pins both: it walks every kind of link (next, choice, trigger, false
  branch, random branch) and asserts that the key a click resolves to is the key the cable is drawn
  with, and that a click a few pixels off a cable makes a point that sits on that cable.

Usage: python3 tools/patch_build327.py <base-326.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build326-point-waits"
NEW_VERSION = "0.8.0-build327-point-on-cable"
OUT_NAME = "settledlands-0.8.0-build327-point-on-cable.jar"

REPLACED = (
    "dev/settledlands/npc/NpcWirePoints321.class",
    "dev/settledlands/npc/NpcWirePoints321$Bend.class",
    "dev/settledlands/npc/NpcWirePoints321$Hang.class",
    "dev/settledlands/npc/NpcWirePoints321$Data.class",
    "dev/settledlands/npc/NpcWireJoin322.class",
    "dev/settledlands/npc/client/NpcWirePointsUi321.class",
    "dev/settledlands/npc/client/NpcWirePointsUi321$Wire.class",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class",
)

ADDED = ()

MIXIN = "NpcWirePointsMixin321"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcWirePoints321.class": "drawn",
    "dev/settledlands/npc/NpcWireJoin322.class": "alsoLinks",
    "dev/settledlands/npc/client/NpcWirePointsUi321.class": "pointOn",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class": "settledlands$drawWires",
}

LANG_RU = {
    "gui.settledlands.graph.point_added": "\u0422\u043e\u0447\u043a\u0430 \u043f\u043e\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u0430",
    "gui.settledlands.graph.point_removed": "\u0422\u043e\u0447\u043a\u0430 \u0443\u0431\u0440\u0430\u043d\u0430",
    "gui.settledlands.graph.point_waiting": "\u041f\u0440\u043e\u0432\u043e\u0434 \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d \u043d\u0430 \u0442\u043e\u0447\u043a\u0435 \u2014 \u0446\u0435\u043b\u044c \u0437\u0430\u0434\u0430\u0451\u0442\u0441\u044f \u0438\u0437 \u0442\u043e\u0447\u043a\u0438",
    "gui.settledlands.graph.point_refused": "\u041f\u0440\u043e\u0432\u043e\u0434 \u043d\u0435 \u0437\u0430\u043a\u0440\u0435\u043f\u043b\u0451\u043d: \u0443 \u043d\u043e\u0434\u044b \u043d\u0435\u0441\u043a\u043e\u043b\u044c\u043a\u043e \u0441\u0432\u044f\u0437\u0435\u0439",
}
LANG_EN = {
    "gui.settledlands.graph.point_added": "Point added",
    "gui.settledlands.graph.point_removed": "Point removed",
    "gui.settledlands.graph.point_waiting": "Wire held by the point - aim it from the point",
    "gui.settledlands.graph.point_refused": "The wire was not held: the node has several links",
}

CHANGELOG_EN = (
    "Build 327 puts a point where the cable is. A double click used to place the dot where the "
    "cursor was - up to the hit radius off the cable - and then snap it to the grid, which could "
    "push it further, so a point could stand beside its wire with no bend. The click is now "
    "projected onto the cable and the point is made there; the grid is used only as long as it "
    "keeps the point on the cable, and nothing moves the point afterwards. A click also resolves to "
    "the port number the screen draws that wire with - every branch of a random node included - and "
    "a point whose key the screen never draws is dropped instead of being shown as a dot on empty "
    "canvas. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 327: \u0442\u043e\u0447\u043a\u0430 \u0441\u0442\u0430\u0432\u0438\u0442\u0441\u044f "
    "\u0442\u0430\u043c, \u0433\u0434\u0435 \u043f\u0440\u043e\u0432\u043e\u0434. "
    "\u041a\u043b\u0438\u043a \u043f\u0435\u0440\u0435\u043d\u043e\u0441\u0438\u0442\u0441\u044f "
    "\u043d\u0430 \u0441\u0430\u043c\u0443 \u043a\u0440\u0438\u0432\u0443\u044e "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u0430, \u0438 \u0442\u043e\u0447\u043a\u0430 "
    "\u043f\u043e\u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u043d\u0430 "
    "\u043d\u0451\u043c, \u0430 \u043d\u0435 \u0440\u044f\u0434\u043e\u043c; "
    "\u0441\u0435\u0442\u043a\u0430 \u043f\u0440\u0438\u043c\u0435\u043d\u044f\u0435\u0442\u0441\u044f, "
    "\u0442\u043e\u043b\u044c\u043a\u043e \u0435\u0441\u043b\u0438 \u043d\u0435 "
    "\u0441\u0434\u0432\u0438\u0433\u0430\u0435\u0442 \u0442\u043e\u0447\u043a\u0443 \u0441 "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u0430. "
    "\u041f\u043e\u0440\u0442 \u043f\u0440\u043e\u0432\u043e\u0434\u0430 \u0431\u0435\u0440\u0451\u0442\u0441\u044f "
    "\u0442\u043e\u0442 \u0436\u0435, \u043a\u043e\u0442\u043e\u0440\u044b\u043c "
    "\u044d\u043a\u0440\u0430\u043d \u0440\u0438\u0441\u0443\u0435\u0442 "
    "\u043f\u0440\u043e\u0432\u043e\u0434 (\u0432\u0435\u0442\u043a\u0438 \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e\u0439 "
    "\u043d\u043e\u0434\u044b \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u0435\u043b\u044c\u043d\u043e), "
    "\u0430 \u0442\u043e\u0447\u043a\u0430, \u043a\u043b\u044e\u0447 \u043a\u043e\u0442\u043e\u0440\u043e\u0439 "
    "\u044d\u043a\u0440\u0430\u043d \u043d\u0435 \u0440\u0438\u0441\u0443\u0435\u0442, "
    "\u0443\u0431\u0438\u0440\u0430\u0435\u0442\u0441\u044f. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out327"

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

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_RU),
                        ("assets/settledlands/lang/en_us.json", LANG_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        added = 0
        for key, value in table.items():
            if key not in strings:
                strings[key] = value
                added += 1
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  %s: %d keys added" % (path.rsplit("/", 1)[-1], added))

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    # The bends are drawn by the client.
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
