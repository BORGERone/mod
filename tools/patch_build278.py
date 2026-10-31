#!/usr/bin/env python3
"""Build 278 - a new pursuit node no longer looks like a trigger node.

Feedback on 277:
  * "when I try to make a pursuit node, a trigger node is created right away" -
    the node was created correctly (action "trigger:pursuit:start:npc:") and its
    inspector was the pursuit panel, but the card was painted by the stock
    NpcDialogueGraphScreen#drawNode, which knows nothing about the pursuit row:
    it printed "Триггер #n" and the raw action string. Build 277 dropped the
    drawNode hook when NpcPursuitGraphUiMixin272 was rewritten, so
    PursuitGraphNode272#drawNode was compiled in but never called.

Repair:
  * NpcPursuitGraphUiMixin272: @Inject(HEAD, cancellable) on
    drawNode(GuiGraphics, Node) again - PursuitGraphNode272 draws the purple
    pursuit card (title "Преследование", "Цель: NPC/игрок", the value and the
    stop state, stock port dots) and the stock drawing is cancelled.

Nothing else changes: creation, the menu row and the inspector panel are the
Build 277 code.

Usage: python3 tools/patch_build278.py <base-277.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build277-pursuit-native-inspector"
NEW_VERSION = "0.8.0-build278-pursuit-card"
OUT_NAME = "settledlands-0.8.0-build278-pursuit-card.jar"

REPLACED = (
    "dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class",
)

CHANGELOG_EN = (
    "Build 278: a newly created pursuit node no longer looks like a trigger node. "
    "Build 277 lost the drawNode hook of NpcPursuitGraphUiMixin272, so the stock "
    "renderer painted every pursuit node as \"Trigger #n\" with the raw action text; the "
    "node itself was correct and its inspector was the pursuit panel. The hook is back: "
    "the card is the purple pursuit node with its title, target type, target value and "
    "stop state, with the stock input and output ports. "
)
PATCHINFO = (
    "Сборка 278: только что созданная нода преследования больше не выглядит как нода-триггер. "
    "В 277 потерялся хук drawNode в NpcPursuitGraphUiMixin272, поэтому карточку рисовал штатный "
    "рендер: «Триггер #n» и строка действия как есть. Сама нода была верной, и инспектор был "
    "панелью преследования. Хук возвращён: карточка — пурпурная нода преследования с заголовком, "
    "типом цели, значением цели и состоянием завершения, вход и выход на штатных местах. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build278-src/out")
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
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

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
