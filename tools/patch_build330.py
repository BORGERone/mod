#!/usr/bin/env python3
"""Build 330 - the route is applied at the head of cable(...), not at the call site in wires(...).

  Build 329's log settles it: the store line printed for the whole session, the cable-hit line from
  the redirect never printed once, while the white colour of that very same call was still applied -
  and the colour hook sits on the same instruction. The call-site replacement was dead; the method
  it was meant to replace was alive.

  So the hook moved to the head of cable(...) itself: every drawn cable passes through it, whoever
  draws it, and no other injector can take it. A cable with no points returns untouched and the
  stock body runs as before. A graph with no points pays one cached check per cable, not a walk.
Build 329 - diagnostics only: does the cable hook run at all, and what does it ask for?

  Build 328 proves the store holds the point ("store: #7{0:6=1}") and the log never shows a single
  "found by ends" line. On screen that has two possible causes that look identical: the redirect on
  cable(...) is not called for that cable at all, or it is called with a different target id than
  the one the point is keyed with. This build answers it with one line, at the entry of the
  redirect, before any point is looked at:

      [settledlands/wires] cable-hit #7 p0 -> #6, points=1

  No behaviour is changed: the same redirect handles the same cable with the same points. The line
  only says whether it ran and what the exact key returned. The build is thrown away once it has
  answered the question - the fix goes into 330.
Build 328 - the cable finds its point by both ends, and says what it found in the log.

  A point is stored under the node and the port number the click was resolved with, and the drawing
  asks for it with the node and port number IT uses. Three builds tried to make those two agree. This
  one stops relying on them agreeing: when the exact key is empty the route is looked up by the two
  ends of the cable, in either direction, on any port, so a numbering difference can no longer leave
  a point drawn on the canvas with no cable through it.

  It also writes one line to the log - twice a second at most, only when it changes - with what the
  store holds and which cable had to be resolved by its ends. If a point still does not reach its
  cable, that line says which side is wrong instead of guessing.

Usage: python3 tools/patch_build328.py <base-327.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build329-wire-probe"
NEW_VERSION = "0.8.0-build330-wire-through-cable"
OUT_NAME = "settledlands-0.8.0-build330-wire-through-cable.jar"

REPLACED = (
    "dev/settledlands/npc/NpcWirePoints321.class",
    "dev/settledlands/npc/NpcWirePoints321$Bend.class",
    "dev/settledlands/npc/NpcWirePoints321$Hang.class",
    "dev/settledlands/npc/NpcWirePoints321$Data.class",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class",
)

ADDED = ()

MIXIN = "NpcWirePointsMixin321"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcWirePoints321.class": "anyPoints",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class": "cableThroughPoints",
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
    "Build 330 moves the point route to the head of the cable routine. The log of 329 shows the "
    "previous version replaced the cable call inside the wire pass, and that replacement never ran: "
    "the store line printed the whole session, the cable-hit line never printed once, and the white "
    "colour of the same call was still applied. A call site can be taken by another hook, a method "
    "cannot, so a cable now bends through the points of its wire wherever it is drawn from. Cables "
    "without points are untouched, and a graph with no points costs one cached check per cable. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 330: \u043c\u0430\u0440\u0448\u0440\u0443\u0442 "
    "\u0447\u0435\u0440\u0435\u0437 \u0442\u043e\u0447\u043a\u0438 "
    "\u043f\u0435\u0440\u0435\u043d\u0435\u0441\u0451\u043d \u043d\u0430 \u0432\u0445\u043e\u0434 "
    "cable(...): \u043f\u0435\u0440\u0435\u0445\u0432\u0430\u0442 \u043d\u0430 "
    "\u043c\u0435\u0441\u0442\u0435 \u0432\u044b\u0437\u043e\u0432\u0430 (321-329) "
    "\u043d\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u044f\u043b\u0441\u044f \u2014 "
    "\u044d\u0442\u043e \u0434\u043e\u043a\u0430\u0437\u0430\u043b \u043b\u043e\u0433 329. "
    "\u041f\u0440\u043e\u0432\u043e\u0434 \u0431\u0435\u0437 \u0442\u043e\u0447\u0435\u043a "
    "\u0440\u0438\u0441\u0443\u0435\u0442\u0441\u044f \u0448\u0442\u0430\u0442\u043d\u043e, "
    "\u0433\u0440\u0430\u0444 \u0431\u0435\u0437 \u0442\u043e\u0447\u0435\u043a "
    "\u0441\u0442\u043e\u0438\u0442 \u043e\u0434\u043d\u0443 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443 "
    "\u043d\u0430 \u043f\u0440\u043e\u0432\u043e\u0434. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out330"

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
