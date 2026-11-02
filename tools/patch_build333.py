#!/usr/bin/env python3
"""
Build 333 - an open dropdown list owns the click before the graph does.

  The list of a dropdown is painted last, over every card, but the click was looked up late:
  inside the screen's body, after the auto-AI card, and only in the fall-through that reaches the
  graph's own code. Everything in front of that fall-through hit-tests the canvas - cards, pins,
  toggles, points - and none of them knows a list is open, so a row drawn over a node gave the
  click to the node: the node was selected or dragged, and the list never saw it.

  One client mixin, first in the list so its HEAD hook runs before the others: if the screen has
  an open dropdown, the click is handed to that dropdown and goes no further. The row under the
  cursor is chosen, a click elsewhere only closes the list, nothing on the canvas is touched.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build332-blue-wire-points"
NEW_VERSION = "0.8.0-build333-dropdown-first"
OUT_NAME = "settledlands-0.8.0-build333-dropdown-first.jar"

REPLACED = ()
ADDED = (
    "dev/settledlands/mixin/NpcDropdownFirst333.class",
)

# Goes first in the client list: its HEAD hook must run before the hooks of builds 272-321.
MIXIN = "NpcDropdownFirst333"

MIXIN_METHODS = (
    "popupClicked",
    "popupOpen",
    "closePopup",
    "activeDropdown",
    "openDropdown",
)

CHANGELOG_EN = (
    "Build 333 fixes the dropdown list in the graph: while a list is open it owns the click. Before this, "
    "the list was painted over the cards but the click was looked up late, behind the auto-AI card and "
    "behind the node handlers, so a row that lay over a node selected or dragged the node and the "
    "profession in the list could not be picked. Now the screen hands the click to the open list first: "
    "the row under the cursor is chosen, a click anywhere else only closes the list, and nothing on the "
    "canvas is touched. Scrolling over the list keeps scrolling it."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 333: \u043e\u0442\u043a\u0440\u044b\u0442\u044b\u0439 "
    "\u0432\u044b\u043f\u0430\u0434\u0430\u044e\u0449\u0438\u0439 \u0441\u043f\u0438\u0441\u043e\u043a "
    "\u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 \u043a\u043b\u0438\u043a \u043f\u0435\u0440\u0432\u044b\u043c - "
    "\u043d\u043e\u0434\u0430 \u043f\u043e\u0434 \u0441\u043f\u0438\u0441\u043a\u043e\u043c \u0431\u043e\u043b\u044c\u0448\u0435 "
    "\u043d\u0435 \u0437\u0430\u0431\u0438\u0440\u0430\u0435\u0442 \u0435\u0433\u043e. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out333"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED + ADDED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            for marker in MIXIN_METHODS:
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not call %s - wrong class?" % (name, marker))
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    client = mixins.setdefault("client", [])
    if MIXIN in client:
        client.remove(MIXIN)
    client.insert(0, MIXIN)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client, first client = %s"
          % (len(mixins.get("mixins", [])), len(client), client[0]))

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
