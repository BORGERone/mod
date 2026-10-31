#!/usr/bin/env python3
"""Build 275 - render the pursuit node as a stock graph node and let it be typed into.

Diagnosis (Build 274 jar, decompiled with tools/disasm.py):
  1. "no ports": NpcPursuitCanvasMixin272#settledlands$pursuitHitPort was
     @Inject(TAIL) on NpcGraphCanvas#hitPort and called
     cir.setReturnValue(-1) for every pursuit node, so a port under the cursor
     was always reported as "nothing". It existed because the Build 272 card was
     drawn 280x140 by hand while portScreenX/portScreenY place ports on the
     stock 220x100 box (input x, output x + 220, both y + 50).
  2. "looks different": NpcPursuitGraphUiMixin272#settledlands$drawPursuitNode
     cancels the stock drawNode and calls PursuitGraphNode272#drawNode, which
     painted its own 280x140 frame with a 24 px title bar and a different
     palette.
  3. "cannot type": the screen has no charTyped(char, int) at all (Build 273
     removed the invalid @Inject into charTyped(CI)V), so keystrokes never
     reached PursuitGraphNode272#charTyped.

Repair:
  * PursuitGraphNode272: card geometry is 220x100 and the frame is drawn with
    the stock constants (border 0xff46576b / 0xffe9bd67 when selected, body
    0xff26313f, 22 px header 0xffc77dff = TRIGGER colour, title 0xfff0f3f7,
    port dots 0xffc4d8ea / 0xffc77dff). The three controls stay, compacted into
    the stock body. Also remembers which node is being edited.
  * NpcPursuitCanvasMixin272: hitPort injector removed -> stock port hit test.
  * NpcPursuitTypingMixin275 (new): merges charTyped(char, int) into the screen
    (mirrors Screen#charTyped, pursuit field first) and forwards keyPressed
    while the field is being edited.

Everything else in the jar is copied byte for byte.

Usage: python3 tools/patch_build275.py <base-274.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build274-pursuit-hitport-fix"
NEW_VERSION = "0.8.0-build275-pursuit-stock-node"
OUT_NAME = "settledlands-0.8.0-build275-pursuit-stock-node.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcPursuitCanvasMixin272.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitTypingMixin275.class",
)
MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXIN = "NpcPursuitTypingMixin275"

CHANGELOG_EN = (
    "Build 275: the pursuit node is a normal graph node now. It is drawn as the "
    "stock 220x100 card - same border, background, 22 px header, TRIGGER header "
    "colour and stock port dots - instead of the previous hand-drawn 280x140 card, "
    "and the injector that forced NpcGraphCanvas#hitPort to -1 for pursuit nodes is "
    "gone, so the node has a working input port on the left and output port on the "
    "right. The target field also accepts typed text: a charTyped(char, int) is "
    "merged into NpcDialogueGraphScreen (the screen never declared one) and sends "
    "keystrokes to the field being edited, with keyPressed covering backspace and "
    "Enter. "
)
PATCHINFO = (
    "Сборка 275: нода преследования стала обычной нодой графа. Она рисуется стоковой "
    "карточкой 220x100 (та же рамка, фон, заголовок 22 px, цвет заголовка типа TRIGGER, "
    "стоковые точки портов) вместо нарисованной вручную 280x140, а инжектор, который "
    "принудительно возвращал -1 из NpcGraphCanvas#hitPort для нод преследования, удалён — "
    "у ноды есть рабочий вход слева и выход справа. Поле цели принимает ввод с клавиатуры: "
    "в NpcDialogueGraphScreen добавлен charTyped(char, int) (в экране его не было), "
    "keyPressed отвечает за backspace и Enter. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build275-src/out")
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

    for name in REPLACED + ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        blobs[name] = src.read_bytes()
        if name not in order:
            order.append(name)
        print("  %s: %s" % ("replaced" if name in REPLACED else "added", name))

    mixins = blobs[MIXINS_JSON].decode("utf-8")
    if NEW_MIXIN in mixins:
        raise SystemExit("%s already lists %s" % (MIXINS_JSON, NEW_MIXIN))
    needle = '"NpcPursuitCanvasMixin272"\n  ]'
    if needle not in mixins:
        raise SystemExit("%s: client list tail not found" % MIXINS_JSON)
    mixins = mixins.replace(
        needle, '"NpcPursuitCanvasMixin272",\n    "%s"\n  ]' % NEW_MIXIN, 1)
    blobs[MIXINS_JSON] = mixins.encode("utf-8")
    print("  %s: client += %s" % (MIXINS_JSON, NEW_MIXIN))

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
