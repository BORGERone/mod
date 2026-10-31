#!/usr/bin/env python3
"""Build 284 - the value wires of the pursuit node are blue, smooth, and the node
also takes a target in.

Report: "the line from the target node looks not whole but torn, with few blocks
[segments]; fix it, make it and similar value wires blue, and give this same node
an input for the target as well".

1) The wire: Build 283 drew it as 13 separate dashes of 2x2 pixels, so it read as a
   dotted, broken line. It is now one cubic cable with horizontal tangents at both
   ends, sampled roughly once per screen pixel and painted as short filled runs, so
   it is continuous at any zoom, blue (0xff4c8dfe), two pixels thick, and the same
   for both pins and the rubber band.

2) The input: the card had only a value output. It now has the matching value input
   - a blue diamond on the left edge on the same row - so a target can be wired into
   the node instead of typed. A wire from another pursuit node makes this node
   follow that node's target; the resolved text is written into this node's own
   action on every graph change, so the server always receives the current value.
   Both links are stored as extra parameters of the action
   (`...|<keep>|<catch-up>|<boost>|@<consumers>|#<source>`), so there is no new
   field, and the server keeps reading the three numbers by index.

3) Replaces NpcPursuitTargetPinMixin283 with NpcPursuitValuePinMixin284 (it handles
   both pins plus the re-materialisation hook); the old class is removed from the
   jar and from settledlands.mixins.json.

Usage: python3 tools/patch_build284.py <base-283.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build283-loco-sync-and-target-pin"
NEW_VERSION = "0.8.0-build284-value-pins-and-target-input"
OUT_NAME = "settledlands-0.8.0-build284-value-pins-and-target-input.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin284.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitTargetPinMixin283.class",
)

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ("NpcPursuitValuePinMixin284",)
DROP_MIXINS = ("NpcPursuitTargetPinMixin283",)
# These three target client-only classes (the graph screen, the render bridge), so
# they belong in the config's "client" list like every other client mixin of this
# mod. Build 283 registered the two loco mixins in the common list; moved here.
CLIENT_MIXINS = ("NpcPursuitValuePinMixin284", "NpcLocoObserveMixin283",
                 "NpcLocoSmoothMixin283")

CHANGELOG_EN = (
    "Build 284: pursuit value wires are blue and continuous, and the pursuit node takes a target in. "
    "The value wire was drawn as a handful of separate dashes, so it read as a broken dotted line; it is "
    "now one cubic cable with horizontal tangents, sampled about once per screen pixel and painted as "
    "short filled runs, so it is continuous at any zoom, in blue, two pixels thick - for both pins and "
    "for the rubber band while dragging. The card also gained the matching value input: a blue diamond "
    "on the left edge on the same row, so a target can be wired into the node instead of typed. Wire it "
    "to another pursuit node and this node follows that node's target; the resolved text is written into "
    "the node's own action on every graph change, so the server always receives the current value. Both "
    "links live in the action as extra parameters and need no new field, and the server still reads the "
    "three settings by index. "
)
PATCHINFO = (
    "Сборка 284: провода значения у ноды преследования синие и цельные, и у ноды появился вход цели. "
    "Провод рисовался десятком отдельных штрихов, из-за чего читался как рваная пунктирная линия; "
    "теперь это один кубический провод с горизонтальными касательными на концах, с шагом примерно в "
    "один экранный пиксель и короткими заливками, поэтому он непрерывен при любом зуме, синий, "
    "толщиной два пикселя — и для обоих выводов, и для резинки при перетаскивании. У карточки появился "
    "парный вход значения: синий ромб на левой кромке в том же ряду, так что цель можно не впечатывать, "
    "а подать проводом. Соедини с другой нодой преследования — и эта нода будет следовать её цели; "
    "полученный текст записывается в действие самой ноды при каждом изменении графа, поэтому сервер "
    "всегда получает актуальное значение. Обе связи живут в действии как дополнительные параметры и не "
    "требуют нового поля, сервер по-прежнему читает три настройки по индексу. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build284-src/out")
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

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        del blobs[name]
        order.remove(name)
        print("  removed: %s" % name)

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = [m for m in mixins.get("mixins", []) if m not in DROP_MIXINS]
    client = list(mixins.get("client", []))
    for mixin in NEW_MIXINS:
        if mixin not in common and mixin not in client:
            client.append(mixin)
    for mixin in CLIENT_MIXINS:
        if mixin in common:
            common.remove(mixin)
        if mixin not in client:
            client.append(mixin)
    mixins["mixins"] = common
    mixins["client"] = client
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, description prefix" % NEW_VERSION)

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
    print("wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
