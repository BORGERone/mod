#!/usr/bin/env python3
"""Build 321 - reroute points on a cable, purely visual.

  A double click on a wire adds a bend, dragging it moves it, a double click on the bend deletes it.
  The cable is drawn through its bends with the screen's own curve routine, one segment per leg, so
  a bent cable looks exactly like a straight one. Nothing about the connection changes: the point is
  stored as text in a node field the node's own type never reads, and the wire is identified by the
  node, the port and the target it already had.

  Three new classes: NpcWirePoints321 (the store and the text format, plain Java), NpcWirePointsUi321
  (handles and hit tests) and NpcWirePointsMixin321 (the cable redirect plus the three mouse hooks).

Usage: python3 tools/patch_build321.py <base-320.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build320-radius-zone"
NEW_VERSION = "0.8.0-build321-wire-points"
OUT_NAME = "settledlands-0.8.0-build321-wire-points.jar"

REPLACED = (
)

ADDED = (
    "dev/settledlands/npc/NpcWirePoints321.class",
    "dev/settledlands/npc/NpcWirePoints321$Handle.class",
    "dev/settledlands/npc/client/NpcWirePointsUi321.class",
    "dev/settledlands/npc/client/NpcWirePointsUi321$Wire.class",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class",
)

MIXIN = "NpcWirePointsMixin321"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcWirePoints321.class": "SLP1",
    "dev/settledlands/npc/client/NpcWirePointsUi321.class": "handleAt",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class": "settledlands$cableThroughPoints",
}

LANG_RU = {}
LANG_EN = {}

CHANGELOG_EN = (
    "Build 321 adds reroute points to the graph editor. Double click a cable to drop a bend on it, "
    "drag the bend to move it, double click it to remove it: the cable is then drawn through its "
    "bends with the same curve routine, so it looks like any other cable and only goes where it was "
    "told. It is purely visual - the connection, the server and the dialogue are untouched, and the "
    "bends are saved with the graph. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 321: \u0442\u043e\u0447\u043a\u0438 "
    "\u043f\u0435\u0440\u0435\u0433\u0438\u0431\u0430 \u043d\u0430 "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u0430\u0445. "
    "\u0414\u0432\u043e\u0439\u043d\u043e\u0439 \u043a\u043b\u0438\u043a \u043f\u043e "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u0443 \u0441\u0442\u0430\u0432\u0438\u0442 "
    "\u0442\u043e\u0447\u043a\u0443, \u043f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u0435 "
    "\u0434\u0432\u0438\u0433\u0430\u0435\u0442, \u0434\u0432\u043e\u0439\u043d\u043e\u0439 "
    "\u043a\u043b\u0438\u043a \u043f\u043e \u0442\u043e\u0447\u043a\u0435 "
    "\u0443\u0431\u0438\u0440\u0430\u0435\u0442. \u0422\u043e\u043b\u044c\u043a\u043e "
    "\u0432\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u043e: "
    "\u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435 \u0438 "
    "\u0434\u0438\u0430\u043b\u043e\u0433\u0438 \u043d\u0435 "
    "\u0438\u0437\u043c\u0435\u043d\u044f\u044e\u0442\u0441\u044f. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out321"

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
