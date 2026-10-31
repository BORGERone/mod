#!/usr/bin/env python3
"""Build 286 - value wires drawn by the canvas' own cable routine, blue to blue.

Report: "the new target line still looks bad, especially when zoomed far out - just
take the rendering from the code. Also the blue target node cannot be plugged into
the target input of another blue node, only into the general input of the pursuit
node; it has to be its own pin."

1) Rendering: Build 284 painted the wire as one filled rectangle per screen pixel,
   so it degraded as soon as the graph was zoomed out - screen pixels against a
   shrinking graph. The wires are now drawn inside NpcDialogueGraphScreen#wires with
   the screen's own curve() cable routine, the same quad buffer, shader and render
   state as the execution cables, and flushed and restored the same way. Geometry,
   thickness and zoom behaviour are therefore identical to a stock cable; only the
   colour differs.

2) Connection: dropping a value wire on a pursuit node now plugs it into that node's
   blue target input (setSource) instead of the general consumer list, and a value
   wire that ends on a pursuit node is drawn into the blue input, not the execution
   input.

3) NpcPursuitValuePinMixin284 is replaced by NpcPursuitValuePinMixin286 (it carries
   the click, the drag, the re-materialisation hook and now the drawing); the old
   class is removed from the jar and from the mixin config.

4) Female Japanese fisherman: the bundled profession model still had 41 textures and
   rybak_jp_fem.png did not exist at all, so the female + Japanese fisherman silently
   resolved to the male texture. The jar now carries the current Blockbench export
   (63 textures, assets-src/ivy_oleander_professions_286.bbmodel) and
   NpcProfessionSkin264 maps the new Japanese pairs and the new female skins.
   NpcProfessionModel264 rebuilds a generated variant file when the shipped model or
   the selected texture changed; before, an existing variant file was reused forever,
   which is why the old male texture stuck even after a fix.

Usage: python3 tools/patch_build286.py <base-285.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build285-loco-field-fix"
NEW_VERSION = "0.8.0-build286-value-wire-cables"
OUT_NAME = "settledlands-0.8.0-build286-value-wire-cables.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/NpcProfessionSkin264.class",
    "dev/settledlands/npc/client/NpcProfessionModel264.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin286.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitValuePinMixin284.class",
)

MIXINS_JSON = "settledlands.mixins.json"
PROFESSION_MODEL = "assets/settledlands/dragon_default/ivy_oleander_professions.bbmodel"
NEW_MIXINS = ("NpcPursuitValuePinMixin286",)
DROP_MIXINS = ("NpcPursuitValuePinMixin284",)
# These three target client-only classes (the graph screen, the render bridge), so
# they belong in the config's "client" list like every other client mixin of this
# mod. Build 283 registered the two loco mixins in the common list; moved here.
CLIENT_MIXINS = ("NpcPursuitValuePinMixin286", "NpcLocoObserveMixin283",
                 "NpcLocoSmoothMixin283")

CHANGELOG_EN = (
    "Build 286: the pursuit value wires are drawn by the canvas' own cable routine, and a blue target "
    "output plugs into the blue target input. Build 284 painted the wire as one rectangle per screen "
    "pixel, which fell apart as soon as the graph was zoomed out, because it drew screen pixels against "
    "a shrinking graph. The wires now go through NpcDialogueGraphScreen#wires with the same curve "
    "routine, the same quad buffer, the same shader and the same render state as the execution cables, "
    "so their geometry, thickness and zoom behaviour are identical - only the colour is blue. Dropping "
    "a value wire onto a pursuit node now connects it to that node's blue target input instead of the "
    "general input, and a value wire that ends on a pursuit node is drawn into the blue input. "
)
PATCHINFO = (
    "Сборка 286: провода значения у ноды преследования рисуются штатной процедурой кабеля холста, и "
    "синий выход цели втыкается в синий вход цели. В 284 провод рисовался одним прямоугольником на "
    "экранный пиксель и разваливался при отдалении, потому что это экранные пиксели против "
    "уменьшающегося графа. Теперь провода идут через NpcDialogueGraphScreen#wires той же процедурой "
    "curve, в том же буфере квадов, с тем же шейдером и тем же состоянием отрисовки, что и провода "
    "выполнения, — геометрия, толщина и поведение при зуме идентичны, отличается только цвет. "
    "Бросание провода значения на ноду преследования теперь подключает его к её синему входу цели, а "
    "не к общему входу, и провод значения, приходящий в ноду преследования, рисуется в синий вход. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build286-src/out")
    ap.add_argument("--model", default="assets-src/ivy_oleander_professions_286.bbmodel")
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

    model = pathlib.Path(args.model)
    if not model.is_file():
        raise SystemExit("missing profession model: %s" % model)
    blobs[PROFESSION_MODEL] = model.read_bytes()
    print("  replaced asset: %s" % PROFESSION_MODEL)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = [m for m in mixins.get("mixins", []) if m not in DROP_MIXINS]
    client = [m for m in mixins.get("client", []) if m not in DROP_MIXINS]
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
