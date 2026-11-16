#!/usr/bin/env python3
"""Build 307 - the workspace renders.

Build 306 crashed the moment the workspace opened:

    java.lang.ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6
        at NpcWorkspace306.drawTopBar(NpcWorkspace306.java:370)

The top bar builds one label table for three tabs and three buttons, and the label was read
with the action id plus one, so the last button ("Назад") asked for entry 6 of a six entry
table. It crashed on the first frame, before anything could be clicked.

Fixed here, and three neighbours around it, because a screen that only draws has to survive
awkward input as well:

  * the label is read by the action id itself, clamped to the table;
  * "add node" clamps the placement coordinates to the model's own limit, so a graph panned
    far out cannot ask for a node outside MAX_COORD;
  * a refused link is caught instead of thrown - connect() is model code and may refuse;
  * an undo snapshot that no longer loads is ignored instead of taking the screen down.

Usage: python3 tools/patch_build307.py <base-306.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build306-workspace"
NEW_VERSION = "0.8.0-build307-workspace-render"
OUT_NAME = "settledlands-0.8.0-build307-workspace-render.jar"

MIXINS_JSON = "settledlands.mixins.json"
LANG_RU = "assets/settledlands/lang/ru_ru.json"
LANG_EN = "assets/settledlands/lang/en_us.json"

REPLACED = (
    "dev/settledlands/npc/client/NpcWorkspace306.class",
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class",
)
ADDED = ()
COMMON_MIXINS = ()
CLIENT_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/NpcWorkspace306.class": (
        "render", "drawTopBar", "topButtons", "mouseClicked", "addNode", "clampCoord",
        "links", "save"),
    "dev/settledlands/npc/client/NpcWorkspace306$Link.class": ("<init>",),
}

LANG_ADD_RU = {}
LANG_ADD_EN = {}

CHANGELOG_EN = (
    "Build 307 fixes the crash on opening the node workspace. The top bar has three tabs and "
    "three buttons in one label table, and the last button asked for the entry after the end "
    "of it, which threw on the very first frame - that is why the editor closed instead of "
    "opening. The label is now read by the action id, clamped to the table. Along with it: "
    "adding a node clamps the coordinates to the graph's own limit, a refused link is caught "
    "instead of thrown, and an undo snapshot that no longer loads is ignored. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 307: \u0438\u0441\u043f\u0440\u0430\u0432\u043b\u0435\u043d "
    "\u0432\u044b\u043b\u0435\u0442 \u043f\u0440\u0438 "
    "\u043e\u0442\u043a\u0440\u044b\u0442\u0438\u0438 "
    "\u0432\u043e\u0440\u043a\u0441\u043f\u0435\u0439\u0441\u0430: "
    "\u0432\u0435\u0440\u0445\u043d\u044f\u044f \u043f\u0430\u043d\u0435\u043b\u044c "
    "\u0447\u0438\u0442\u0430\u043b\u0430 \u043f\u043e\u0434\u043f\u0438\u0441\u044c "
    "\u043f\u043e \u0438\u043d\u0434\u0435\u043a\u0441\u0443 "
    "\u00ab\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435+1\u00bb \u0438 "
    "\u0432\u044b\u0445\u043e\u0434\u0438\u043b\u0430 \u0437\u0430 "
    "\u0433\u0440\u0430\u043d\u0438\u0446\u0443 \u0442\u0430\u0431\u043b\u0438\u0446\u044b "
    "\u043d\u0430 \u043a\u043d\u043e\u043f\u043a\u0435 "
    "\u00ab\u041d\u0430\u0437\u0430\u0434\u00bb. \u0417\u0430\u043e\u0434\u043d\u043e: "
    "\u043a\u043e\u043e\u0440\u0434\u0438\u043d\u0430\u0442\u044b "
    "\u043d\u043e\u0432\u043e\u0439 \u043d\u043e\u0434\u044b "
    "\u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u044b "
    "\u043b\u0438\u043c\u0438\u0442\u043e\u043c "
    "\u043c\u043e\u0434\u0435\u043b\u0438, \u043e\u0442\u043a\u0430\u0437 "
    "\u0441\u0432\u044f\u0437\u0438 \u043b\u043e\u0432\u0438\u0442\u0441\u044f, "
    "\u0431\u0438\u0442\u044b\u0439 undo-\u0441\u043d\u0438\u043c\u043e\u043a "
    "\u0438\u0433\u043d\u043e\u0440\u0438\u0440\u0443\u0435\u0442\u0441\u044f. "
)


def check_methods(path, names):
    cf = classfile.parse(path.read_bytes(), path.name)
    have = {m.name for m in cf.methods}
    for name in names:
        if name not in have:
            raise SystemExit("%s has no method %s" % (path, name))
    return sorted(have)


def patch_lang(raw, additions):
    data = json.loads(raw.decode("utf-8"))
    added = 0
    for key, value in additions.items():
        if key not in data:
            data[key] = value
            added += 1
    return (json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"), added


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build305-src/out")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    classes = pathlib.Path(args.classes)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name])
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name])
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = list(mixins.get("mixins", []))
    for mixin in COMMON_MIXINS:
        if mixin not in common:
            common.append(mixin)
    mixins["mixins"] = common
    client = list(mixins.get("client", []))
    for mixin in CLIENT_MIXINS:
        if mixin not in client:
            client.append(mixin)
    mixins["client"] = client
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

    for path, additions in ((LANG_RU, LANG_ADD_RU), (LANG_EN, LANG_ADD_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        blobs[path], added = patch_lang(blobs[path], additions)
        print("  %s: +%d keys" % (path, added))

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
