#!/usr/bin/env python3
"""
Build 334 - the "Анимации" tab becomes "Общее": one graph for every NPC.

  Before this build the tab was a per-NPC sub-graph: each NPC kept a copy in its profile and ticked
  it on its own, so editing it for one NPC changed that one NPC. The tab is now common - it lives in
  the world (settledlands_common_graph), every NPC ticks it instead of its own, and the editor of any
  NPC shows the same graph, so what the author saves there applies to all NPCs at once, including
  ones created later.

  Nothing new is invented for storage: SavedData / computeIfAbsent, exactly like CutsceneStorage.
  While no common graph has been saved yet, every NPC keeps running its own tab, so an older world
  behaves as before until the author saves the common one.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build333-dropdown-first"
NEW_VERSION = "0.8.0-build334-common-graph"
OUT_NAME = "settledlands-0.8.0-build334-common-graph.jar"

REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcCommonGraphStorage.class",
    "dev/settledlands/npc/NpcCommonGraph334.class",
    "dev/settledlands/mixin/NpcCommonGraphMixin334.class",
    "dev/settledlands/mixin/NpcCommonGraphDamageMixin334.class",
)

# Both targets (GuideNpc, GuideDialogue) exist on both sides, so they go into the common list.
MIXINS = ("NpcCommonGraphMixin334", "NpcCommonGraphDamageMixin334")

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcCommonGraphMixin334.class": (
        "tickGraphCheckNodes", "tickGraphTimeNodes", "tickGraphTimerNodes",
        "settledlands$sendCommon", "settledlands$takeCommon",
    ),
    "dev/settledlands/mixin/NpcCommonGraphDamageMixin334.class": ("handleNpcDamaged",),
    "dev/settledlands/npc/NpcCommonGraphStorage.class": ("settledlands_common_graph", "setDirty"),
    "dev/settledlands/npc/NpcCommonGraph334.class": ("inDamage",),
}

# The tab is renamed; the key stays the same, only its text changes.
LANG_SET = {
    "gui.settledlands.graph.tab_anim": "Общее",
}
LANG_SET_EN = {
    "gui.settledlands.graph.tab_anim": "Common",
}

CHANGELOG_EN = (
    "Build 334 turns the \"Animations\" tab into \"Common\": there is now one such graph for the whole "
    "world instead of one per NPC. It is kept in world data, every NPC ticks it (checks, time and timer "
    "nodes - so a check, an answer button or a dialogue placed there runs for every NPC), and the tab "
    "shows the same graph whichever NPC you open it from, so one save changes all of them, including "
    "NPCs created later. Until the common graph is saved for the first time each NPC keeps running its "
    "own tab, so nothing already built stops working."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 334: \u0432\u043a\u043b\u0430\u0434\u043a\u0430 "
    "\u00ab\u0410\u043d\u0438\u043c\u0430\u0446\u0438\u0438\u00bb \u0441\u0442\u0430\u043b\u0430 "
    "\u00ab\u041e\u0431\u0449\u0438\u043c\u00bb - \u043e\u0434\u0438\u043d \u0433\u0440\u0430\u0444 "
    "\u043d\u0430 \u0432\u0435\u0441\u044c \u043c\u0438\u0440. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out334"

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

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key not in strings:
                raise SystemExit("%s has no key %s to rename" % (path, key))
            print("  %s: %s = %r (was %r)" % (path.rsplit("/", 1)[-1], key, value, strings[key]))
            strings[key] = value
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

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
