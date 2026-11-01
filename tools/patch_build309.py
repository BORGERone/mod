#!/usr/bin/env python3
"""Build 309 - back to the node graph as it was before the other mod's look.

The workspace look work started in Build 304 and ran through 305, 306 and 307. This build goes
back further than the last rollback did: it is built straight from the 303 jar, the last build
before any of that started.

What is in it, unchanged from 303:

  * the blue value wire drawn as one whole line that never drops a frame;
  * the pursuit node's "cancel pursuit" switch as the second row of its card;
  * pursuit walking at the old catch-up speed, catch-up faster still;
  * the round blue value pins and the white ports and cables;
  * "Условие" evaluating only the variable written into it, the corpse as a pursuit target,
    the middle-click wire cut.

Nothing from Builds 304 onwards is shipped: no workspace skin class, no workspace mixin, no node
library panel, no separate workspace screen. The mixin list is the 303 list.

Usage: python3 tools/patch_build309.py <base-303.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build303-wires-stop-speed"
NEW_VERSION = "0.8.0-build309-classic"
OUT_NAME = "settledlands-0.8.0-build309-classic.jar"

GONE_CLASSES = (
    "dev/settledlands/npc/client/WorkspaceSkin304.class",
    "dev/settledlands/npc/client/WorkspaceTheme305.class",
    "dev/settledlands/npc/client/NpcNodeLibrary305.class",
    "dev/settledlands/npc/client/NpcWorkspace306.class",
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class",
    "dev/settledlands/mixin/NpcWorkspaceMixin304.class",
    "dev/settledlands/mixin/NpcLibrary305Mixin.class",
    "dev/settledlands/mixin/NpcWorkspaceEntry306Mixin.class",
)
GONE_MIXINS = (
    "NpcWorkspaceMixin304",
    "NpcLibrary305Mixin",
    "NpcWorkspaceEntry306Mixin",
)

CHANGELOG_EN = (
    "Build 309 is the node graph as it was before the workspace look work started. It is built "
    "from the 303 jar, so nothing from Builds 304 to 308 is in it: no workspace skin class, no "
    "workspace mixin, no node library panel and no separate workspace screen. What is in it is "
    "the graph from 303: the blue value wire drawn as one whole line that never drops a frame, "
    "the pursuit node's cancel switch as the second row of its card, pursuit at the old catch-up "
    "speed with a faster catch-up, round blue value pins, white ports and cables, the condition "
    "node checking only its own variable, and the corpse as a pursuit target. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 309: \u0433\u0440\u0430\u0444 \u043d\u043e\u0434 "
    "\u043a\u0430\u043a \u0434\u043e \u0440\u0430\u0431\u043e\u0442 \u043f\u043e\u0434 "
    "\u0447\u0443\u0436\u043e\u0439 \u0432\u0438\u0434. \u0421\u043e\u0431\u0440\u0430\u043d\u0430 "
    "\u043f\u0440\u044f\u043c\u043e \u0438\u0437 jar 303: \u0441\u043a\u0438\u043d\u0430 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441\u0430, \u043c\u0438\u043a\u0441\u0438\u043d\u0430 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441\u0430, \u0431\u0438\u0431\u043b\u0438\u043e\u0442\u0435\u043a\u0438 "
    "\u043d\u043e\u0434 \u0438 \u043e\u0442\u0434\u0435\u043b\u044c\u043d\u043e\u0433\u043e "
    "\u044d\u043a\u0440\u0430\u043d\u0430 \u0432 \u043d\u0435\u0439 \u043d\u0435\u0442. "
    "\u0412\u043d\u0443\u0442\u0440\u0438 \u2014 \u0433\u0440\u0430\u0444 \u0438\u0437 303: "
    "\u0441\u0438\u043d\u0438\u0439 \u043f\u0440\u043e\u0432\u043e\u0434 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f "
    "\u0446\u0435\u043b\u044c\u043d\u043e\u0439 \u043b\u0438\u043d\u0438\u0435\u0439, "
    "\u0442\u0443\u043c\u0431\u043b\u0435\u0440 \u043e\u0442\u043c\u0435\u043d\u044b "
    "\u043f\u0440\u0435\u0441\u043b\u0435\u0434\u043e\u0432\u0430\u043d\u0438\u044f "
    "\u0432\u0442\u043e\u0440\u043e\u0439 \u0441\u0442\u0440\u043e\u043a\u043e\u0439, "
    "\u0441\u0442\u0430\u0440\u044b\u0435 \u0441\u043a\u043e\u0440\u043e\u0441\u0442\u0438, "
    "\u043a\u0440\u0443\u0433\u043b\u044b\u0435 \u0441\u0438\u043d\u0438\u0435 \u043f\u0438\u043d\u044b "
    "\u0438 \u0431\u0435\u043b\u0430\u044f \u043f\u0440\u043e\u0432\u043e\u0434\u043a\u0430. "
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

    for name in GONE_CLASSES:
        if name in blobs:
            del blobs[name]
            order.remove(name)
            print("  removed: %s" % name)

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = [m for m in mixins.get("mixins", []) if m not in GONE_MIXINS]
    client = [m for m in mixins.get("client", []) if m not in GONE_MIXINS]
    mixins["mixins"] = common
    mixins["client"] = client
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

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
