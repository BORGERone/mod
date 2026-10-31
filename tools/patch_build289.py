#!/usr/bin/env python3
"""Build 289 - the blue output of the "In Radius" node.

Request: "I want a blue output on the 'In Radius' node. It has to carry the value of the
player (or mob) that it detects."

The node already finds a body: GuideDialogue#findCheckTarget scans its box and returns
the first matching entity. That entity is the value.

Editor: the check card gets the same blue diamond the pursuit card has, on its right edge
and on the same row, drawn after the stock card so nothing of the original is lost. Drag
it onto a pursuit node (or drag the pursuit node's blue input onto the check node) and the
link is stored as the pursuit node's source, |#<id> - the same field the pursuit-to-pursuit
wire uses. Draw, drag, middle-click cut and right-click-unplug all behave like every other
value wire.

Server: NpcCheckValue289 answers "which entity does node X of this NPC see right now",
with the node's own rules (checkTarget, checkRadius 1..64, owner excluded, spectators
excluded), ignoring the trigger cooldown and taking the nearest match. NpcPursuit269 reads
the source id out of the action, asks that class for the body, and pursues it; the node
output %pursuit% now reports the body it actually found. The source id is saved with the
NPC, so the link survives a reload.

Usage: python3 tools/patch_build289.py <base-288.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build288-value-wire-cut-and-corpse-target"
NEW_VERSION = "0.8.0-build289-check-node-value-output"
OUT_NAME = "settledlands-0.8.0-build289-check-node-value-output.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/NpcPursuit269.class",
    "dev/settledlands/npc/NpcPursuit269$State.class",
    "dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin289.class",
    "dev/settledlands/npc/NpcCheckValue289.class",
    "dev/settledlands/npc/NpcCheckValue289$Cache.class",
    "dev/settledlands/npc/NpcCheckValue289$Hit.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin288.class",
)

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ("NpcPursuitValuePinMixin289",)
DROP_MIXINS = ("NpcPursuitValuePinMixin288",)
CLIENT_MIXINS = ("NpcPursuitValuePinMixin289",)


CHANGELOG_EN = (
    "Build 289: the 'In Radius' node has the blue value output. The node already scans its "
    "radius for a body (GuideDialogue#findCheckTarget); that body is what the new pin carries. "
    "The pin is drawn on the right edge of the check card, on the same row as the pursuit card's "
    "pins, and is wired like every other value wire: drag it onto a pursuit node - or drag the "
    "pursuit node's blue input onto the check node - and the link is stored as that node's source, "
    "drawn as the same blue cable and cut by the same middle click. On the server "
    "NpcCheckValue289 answers which entity the node sees, with the node's own rules (its target "
    "kind, its 1-64 block radius, the owner and spectators excluded, nearest match, no trigger "
    "cooldown), and the pursuit node pursues that body instead of a typed name, so it keeps "
    "following whoever walks into the radius and switches when that body leaves. %pursuit% reports "
    "the body that was found. The link is saved with the NPC. "
)
PATCHINFO = (
    "Сборка 289: у ноды «В радиусе» появился синий выход значения. Нода и так ищет тело в своём "
    "радиусе (GuideDialogue#findCheckTarget) — это тело и есть значение провода. Ромб нарисован на "
    "правой кромке карточки проверки, в том же ряду, что ромбы карточки преследования, и "
    "подключается как любой провод значения: бросок на ноду преследования (или бросок синего входа "
    "ноды преследования на ноду проверки) записывает её источником, рисуется тем же синим кабелем и "
    "режется тем же средним кликом. На сервере NpcCheckValue289 отвечает, кого нода видит, по её же "
    "правилам (тип цели, радиус 1–64, владелец и наблюдатели исключены, берётся ближайший, без "
    "кулдауна срабатывания), и нода преследования идёт к этому телу вместо вписанного имени: "
    "продолжает следовать за тем, кто вошёл в радиус, и переключается, когда тот уходит. %pursuit% "
    "показывает найденное тело. Связь сохраняется вместе с NPC. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build289-src/out")
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
    client = [m for m in mixins.get("client", []) if m not in DROP_MIXINS]
    for mixin in NEW_MIXINS:
        if mixin not in common and mixin not in client:
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
