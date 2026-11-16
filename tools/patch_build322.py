#!/usr/bin/env python3
"""Build 322 - a wire released on a reroute point joins it there.

  Build 321 made points purely visual, so a wire dropped on one did nothing: the release fell
  through to the stock path, which only knows ports and nodes, and the screen offered its "make a
  node out of this wire" menu instead. A bend is a place a wire can end, so it is taken first now:
  the connection is made through NpcDialogueGraph#connect and the bend is added, so the dragged wire
  runs through the point and meets the wire the point already sits on - a junction, drawn like every
  other cable.

  One new class (NpcWireJoin322) plus the release hook and a highlight in NpcWirePointsUi321 and
  NpcWirePointsMixin321.

Usage: python3 tools/patch_build322.py <base-321.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build321-wire-points"
NEW_VERSION = "0.8.0-build322-join-points"
OUT_NAME = "settledlands-0.8.0-build322-join-points.jar"

REPLACED = (
    "dev/settledlands/npc/client/NpcWirePointsUi321.class",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class",
)

ADDED = (
    "dev/settledlands/npc/NpcWireJoin322.class",
)

MIXIN = "NpcWirePointsMixin321"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcWireJoin322.class": "canJoin",
    "dev/settledlands/npc/client/NpcWirePointsUi321.class": "drawHandles",
    "dev/settledlands/mixin/NpcWirePointsMixin321.class": "settledlands$releasePoint",
}

LANG_RU = {}
LANG_EN = {}

CHANGELOG_EN = (
    "Build 322 lets a wire end on a reroute point. Dragging a wire and releasing it on a bend used "
    "to do nothing - the release was treated as a release over empty canvas and offered to make a "
    "node out of the wire. Now the connection is made and the new cable bends through the point, so "
    "the point works as a junction: several cables in, one out, drawn like the rest. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 322: \u043f\u0440\u043e\u0432\u043e\u0434 "
    "\u043c\u043e\u0436\u043d\u043e \u043e\u0442\u043f\u0443\u0441\u0442\u0438\u0442\u044c "
    "\u043d\u0430 \u0442\u043e\u0447\u043a\u0435 "
    "\u043f\u0435\u0440\u0435\u0433\u0438\u0431\u0430: "
    "\u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435 "
    "\u0441\u043e\u0437\u0434\u0430\u0451\u0442\u0441\u044f, \u0430 "
    "\u043d\u043e\u0432\u044b\u0439 \u043f\u0440\u043e\u0432\u043e\u0434 "
    "\u043f\u0440\u043e\u0445\u043e\u0434\u0438\u0442 \u0447\u0435\u0440\u0435\u0437 "
    "\u0442\u043e\u0447\u043a\u0443. \u0422\u043e\u0447\u043a\u0430 "
    "\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0441\u044f "
    "\u0443\u0437\u043b\u043e\u043c \u0441\u0445\u043e\u0434\u0430 "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u043e\u0432. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out322"

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
