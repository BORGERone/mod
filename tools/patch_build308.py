#!/usr/bin/env python3
"""Build 308 - roll back to our node look.

Builds 305 to 307 added a node library panel and then replaced the whole dialogue screen with a
workspace screen of our own. The workspace moved field editing out of the editor and behind a
button, and it is not what was asked for. This build goes back to the editor as it was in Build
304: the dialogue screen itself, painted with the workspace cards, round ports, the warm cable
colour and the dark dot canvas, with every inspector, every field and every existing panel
exactly where it has always been.

The rollback is done by building straight from the 304 jar: the workspace screen, its mixin and
the library panel are not shipped at all, and the mixin list is the 304 list.

Usage: python3 tools/patch_build308.py <base-304.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

BASE_VERSION = "0.8.0-build304-workspace-skin"
NEW_VERSION = "0.8.0-build308-nodes"
OUT_NAME = "settledlands-0.8.0-build308-nodes.jar"

CHANGELOG_EN = (
    "Build 308 rolls the editor back to our node look of Build 304. The separate workspace screen "
    "and the node library panel of Builds 305 to 307 are out: opening the graph opens the dialogue "
    "screen that has always run in game, painted with the workspace cards, round ports, the warm "
    "cable colour and the dark dot canvas. Every inspector and every field is back where it was, "
    "the graph format and the save packet are untouched, and the blue value wires keep their "
    "colour. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 308: \u043e\u0442\u043a\u0430\u0442 \u043a "
    "\u043d\u0430\u0448\u0435\u043c\u0443 \u0432\u0438\u0434\u0443 \u043d\u043e\u0434 "
    "\u0438\u0437 304. \u041e\u0442\u0434\u0435\u043b\u044c\u043d\u044b\u0439 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441 \u0438 \u043f\u0430\u043d\u0435\u043b\u044c "
    "\u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0438 \u0438\u0437 305\u2013307 "
    "\u0443\u0431\u0440\u0430\u043d\u044b: \u0433\u0440\u0430\u0444 "
    "\u043e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u0442 \u0448\u0442\u0430\u0442\u043d\u044b\u0439 "
    "\u044d\u043a\u0440\u0430\u043d \u0441 \u043d\u0430\u0448\u0438\u043c\u0438 "
    "\u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0430\u043c\u0438, \u043a\u0440\u0443\u0433\u043b\u044b\u043c\u0438 "
    "\u043f\u043e\u0440\u0442\u0430\u043c\u0438 \u0438 \u0442\u0451\u043c\u043d\u044b\u043c "
    "\u0445\u043e\u043b\u0441\u0442\u043e\u043c \u0432 \u0442\u043e\u0447\u043a\u0443. "
    "\u0418\u043d\u0441\u043f\u0435\u043a\u0442\u043e\u0440\u044b, \u0444\u043e\u0440\u043c\u0430\u0442 "
    "\u0433\u0440\u0430\u0444\u0430 \u0438 \u043f\u0430\u043a\u0435\u0442 "
    "\u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f \u2014 \u0431\u0435\u0437 "
    "\u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0439. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    removed = [name for name in blobs
               if name.endswith("NpcWorkspace306.class")
               or name.endswith("NpcWorkspace306$Link.class")
               or name.endswith("NpcWorkspaceEntry306Mixin.class")
               or name.endswith("NpcLibrary305Mixin.class")]
    for name in removed:
        del blobs[name]
        order.remove(name)
        print("  removed: %s" % name)

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    gone = {"NpcWorkspaceEntry306Mixin", "NpcLibrary305Mixin"}
    client = [m for m in mixins.get("client", []) if m not in gone]
    mixins["client"] = client
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(mixins.get("mixins", [])), len(client)))

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
