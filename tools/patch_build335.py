#!/usr/bin/env python3
"""
Build 335 - the buttons of the "Общее" graph are offered in the conversation.

  334 made the tab one graph for every NPC, but only its checks, timers and damage chain ever ran:
  the talk builds its answers from profile.dialogueGraph alone, so a button placed in the common
  graph was never seen. Now, when a conversation opens, the NPC's graph is briefly merged with the
  common one - the buttons nothing points at (the roots of the common graph) are hung onto the line
  the talk starts with, the chains behind them come along under fresh ids, and each chain that ends
  leads back to the NPC instead of closing the talk. The merged graph is never saved: NpcProfile.save
  puts the NPC's own graph back for the length of the call, which also covers the config editor.

  Base: 334. New classes: NpcCommonDialogue335 and the two hooks.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build334-common-graph"
NEW_VERSION = "0.8.0-build335-common-buttons"
OUT_NAME = "settledlands-0.8.0-build335-common-buttons.jar"

REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcCommonDialogue335.class",
    "dev/settledlands/npc/NpcCommonDialogue335$State.class",
    "dev/settledlands/mixin/NpcCommonDialogueMixin335.class",
    "dev/settledlands/mixin/NpcCommonDialogueSaveMixin335.class",
)

# GuideDialogue and NpcProfile both exist on both sides; the hooks only do anything server-side.
MIXINS = ("NpcCommonDialogueMixin335", "NpcCommonDialogueSaveMixin335")

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcCommonDialogueMixin335.class": (
        "settledlands$attachCommon", "settledlands$detachCommon", "settledlands$originalStart",
    ),
    "dev/settledlands/mixin/NpcCommonDialogueSaveMixin335.class": (
        "settledlands$stripCommon", "settledlands$putCommonBack",
    ),
    "dev/settledlands/npc/NpcCommonDialogue335.class": (
        "reachable", "startLines", "NpcCommonGraph334",
    ),
    "dev/settledlands/npc/NpcCommonDialogue335$State.class": ("original", "merged", "sessions"),
}

CHANGELOG_EN = (
    "Build 335 lets the \"Common\" graph take part in the conversation. A button dropped into that "
    "graph is now offered on the line the talk opens with, and pressing it walks the chain behind it "
    "in the common graph - its own lines, its own buttons, IF and random nodes included - and then "
    "hands the player back to the NPC instead of ending the talk. Checks, timers and damage keep "
    "running as before. The merged graph is only in memory while somebody is talking: saving, "
    "copying and the config editor always see the graph the author built."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 335: \u043a\u043d\u043e\u043f\u043a\u0438 "
    "\u0433\u0440\u0430\u0444\u0430 \u00ab\u041e\u0431\u0449\u0435\u0435\u00bb "
    "\u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u044e\u0442\u0441\u044f "
    "\u0432 \u0434\u0438\u0430\u043b\u043e\u0433\u0435 \u043f\u043e\u0441\u043b\u0435 "
    "\u0441\u0442\u0430\u0440\u0442\u043e\u0432\u043e\u0439 \u0440\u0435\u043f\u043b\u0438\u043a\u0438 "
    "\u0438 \u0432\u0435\u0434\u0443\u0442 \u0432 \u0441\u0432\u043e\u044e "
    "\u0446\u0435\u043f\u043e\u0447\u043a\u0443. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out335"

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
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    for name in MIXINS:
        if name not in common:
            common.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common (%s), %d client"
          % (len(common), ", ".join(MIXINS), len(mixins.get("client", []))))

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
