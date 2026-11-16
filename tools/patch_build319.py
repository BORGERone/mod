#!/usr/bin/env python3
"""Build 319 - a pasted block lands in the middle of the view, not where it was copied.

  Copying a branch in one graph and pasting it in another put the nodes at their original
  coordinates plus a fixed 24 block offset: inside the same graph that is a neat step to the side,
  but anything copied elsewhere, or pasted from another world through the clipboard, appeared off
  screen behind the current view and looked like nothing had happened.

  Ctrl+V now measures the box of the copied block and moves the middle of it onto the middle of the
  canvas the editor is showing, so the block always arrives where the eye is. The relative layout of
  the block is untouched. PasteCentered falls back to the plain offset when the canvas has no
  geometry yet.

  Two classes change: NpcGraphClipboard318 (bounds + pasteCentered + one shared parser) and
  NpcClipboardMixin318 (ask the canvas for its middle).

Usage: python3 tools/patch_build319.py <base-318.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build318-clipboard"
NEW_VERSION = "0.8.0-build319-clipboard-center"
OUT_NAME = "settledlands-0.8.0-build319-clipboard-center.jar"

REPLACED = (
    "dev/settledlands/npc/NpcGraphClipboard318.class",
    "dev/settledlands/mixin/NpcClipboardMixin318.class",
)

ADDED = (
)

MIXIN = "NpcClipboardMixin318"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcGraphClipboard318.class": "pasteCentered",
    "dev/settledlands/mixin/NpcClipboardMixin318.class": "settledlands$pasteInto",
}

CHANGELOG_EN = (
    "Build 319 puts a pasted block where the editor is looking. Ctrl+V measured nothing before: the "
    "nodes landed at their original coordinates plus a fixed offset, so a copy taken in another "
    "graph, or pasted from another world, appeared off screen and looked like it had not worked. "
    "The block is now moved as a whole so that its middle sits in the middle of the visible canvas, "
    "with its internal layout unchanged. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 319: \u0432\u0441\u0442\u0430\u0432\u043a\u0430 "
    "\u043f\u043e\u043f\u0430\u0434\u0430\u0435\u0442 \u0432 \u0446\u0435\u043d\u0442\u0440 "
    "\u044d\u043a\u0440\u0430\u043d\u0430. \u0420\u0430\u043d\u044c\u0448\u0435 "
    "\u0431\u043b\u043e\u043a \u0441\u0442\u0430\u0432\u0438\u043b\u0441\u044f \u0432 "
    "\u0441\u0432\u043e\u0438\u0445 \u0436\u0435 \u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u0430\u0445 "
    "\u0441\u043e \u0441\u0434\u0432\u0438\u0433\u043e\u043c +24, \u0438 "
    "\u043f\u0440\u0438 \u0432\u0441\u0442\u0430\u0432\u043a\u0435 \u0432 "
    "\u0434\u0440\u0443\u0433\u043e\u0439 \u0433\u0440\u0430\u0444 \u0443\u0445\u043e\u0434\u0438\u043b "
    "\u0437\u0430 \u044d\u043a\u0440\u0430\u043d. \u0422\u0435\u043f\u0435\u0440\u044c "
    "\u0441\u0435\u0440\u0435\u0434\u0438\u043d\u0430 \u0431\u043b\u043e\u043a\u0430 "
    "\u0441\u0442\u0430\u0432\u0438\u0442\u0441\u044f \u0432 \u0441\u0435\u0440\u0435\u0434\u0438\u043d\u0443 "
    "\u0432\u0438\u0434\u0438\u043c\u043e\u0433\u043e \u0445\u043e\u043b\u0441\u0442\u0430. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out319"

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
    # Build 318 already registered the mixin in the client list; this build only rewrites the class.
    if MIXIN not in mixins.get("mixins", []) and MIXIN not in mixins.get("client", []):
        mixins.setdefault("client", []).append(MIXIN)
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
