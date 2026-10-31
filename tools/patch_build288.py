#!/usr/bin/env python3
"""Build 288 - the value wires behave like the stock cables, and a corpse is a valid target.

Two reports:

1) "The blue wire cannot be removed with the middle mouse button, I asked for a full
   copy of the connections, only in blue."

   The stock graph cuts the cable under the cursor on a middle click. The blue value
   wires only had my right-click-on-the-pin shortcut, which is not the same gesture and
   not per wire. A middle click now finds the single value wire under the cursor -
   sampling the same cubic the screen's curve() draws and using the tolerance
   NpcGraphCanvas#hitCable gives a stock cable - and cuts exactly that one: a source
   wire is unplugged from its input, a consumer wire drops that consumer. Drag, drop,
   drawing, cut and undo snapshots are now the stock set of behaviours, only blue.

2) "I gave the corpse the identifier 'труп' and set another NPC to pursue the corpse,
   but it does not go anywhere."

   NpcPursuit269.matches filtered corpses out together with the trigger NPCs, so a
   pursuit node aimed at a corpse never resolved a target and the controller only kept
   stopping the NPC in place. A corpse is a GuideNpc with a profile of its own, so the
   identifier now matches it. Trigger NPCs stay excluded, and a corpse still cannot be
   the *pursuer* (run() keeps that guard).

Usage: python3 tools/patch_build288.py <base-287.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build287-graph-hit-restore"
NEW_VERSION = "0.8.0-build288-value-wire-cut-and-corpse-target"
OUT_NAME = "settledlands-0.8.0-build288-value-wire-cut-and-corpse-target.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/NpcPursuit269.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin288.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin286.class",
)

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ("NpcPursuitValuePinMixin288",)
DROP_MIXINS = ("NpcPursuitValuePinMixin286",)
CLIENT_MIXINS = ("NpcPursuitValuePinMixin288",)


CHANGELOG_EN = (
    "Build 288: the blue value wires behave like the stock cables, and a corpse can be pursued. A "
    "middle click now cuts the single value wire under the cursor, exactly like middle clicking an "
    "execution cable - the wire is found by sampling the same cubic the screen draws and with the "
    "tolerance the canvas uses for its own cables, so a source wire is unplugged from its input and a "
    "consumer wire drops that consumer, one wire at a time, through the screen's undo snapshot. Until "
    "now the only way back was a right click on the pin, which cleared everything. Separately, a "
    "pursuit node aimed at a corpse never found its target because corpses were filtered out of the "
    "target matching together with the trigger NPCs, so the NPC stood still. A corpse carries a "
    "profile like any NPC and an identifier can be given to it, so it now matches; trigger NPCs stay "
    "excluded and a corpse still cannot be the pursuer. "
)
PATCHINFO = (
    "Сборка 288: синие провода значения ведут себя как штатные кабели, и труп можно преследовать. "
    "Средняя кнопка мыши теперь режет ровно тот провод значения, что под курсором, — так же, как "
    "режется кабель выполнения: провод находится по той же кривой, которую рисует экран, и с тем же "
    "допуском, что холст даёт своим кабелям; провод-источник отцепляется от входа, провод-потребитель "
    "снимает этого потребителя, по одному проводу, через снимок отмены экрана. Раньше вернуть было "
    "можно только правым кликом по ромбу, и он снимал всё сразу. Отдельно: нода преследования, "
    "нацеленная на труп, не находила цель, потому что трупы отфильтровывались из сопоставления цели "
    "вместе с NPC-триггерами, и NPC стоял на месте. У трупа есть профиль, как у любого NPC, и ему "
    "можно задать идентификатор, поэтому теперь он находится; триггеры по-прежнему исключены, и труп "
    "по-прежнему не может быть преследователем. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build288-src/out")
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
