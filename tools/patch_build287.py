#!/usr/bin/env python3
"""Build 287 - the dialogue screen crash: PursuitGraphNode272 lost hit() and visible().

Crash in game:

    java.lang.NoSuchMethodError: 'int dev.settledlands.npc.client.PursuitGraphNode272.hit(
        dev.settledlands.npc.client.NpcGraphCanvas, dev.settledlands.npc.NpcDialogueGraph,
        double, double)'
        at NpcGraphCanvas.handler$...$settledlands$pursuitHit
        at NpcDialogueGraphScreen.wires

Build 286 replaced PursuitGraphNode272 to drop the old per-pixel wire drawing and
took the card culling and hit test (`visible`, `hit`) with it. Those two are called
from the canvas mixin NpcPursuitCanvasMixin272, which is compiled against Build 285
and was never recompiled, so nothing in the build noticed: ECJ sees only the new
source, jvm_verify checks class shape and check_mixin_class checks injection points.
The graph worked until the dialogue screen drew its background, then died.

Both methods are restored verbatim, and the gates grow a fourth check that makes
this impossible to ship again: dangling_refs compares every class with the previous
jar and fails when a class lost a member that another class in the jar still calls.

Only PursuitGraphNode272 changes; no behaviour, no config, no other class.

Usage: python3 tools/patch_build287.py <base-286.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build286-value-wire-cables"
NEW_VERSION = "0.8.0-build287-graph-hit-restore"
OUT_NAME = "settledlands-0.8.0-build287-graph-hit-restore.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
)
ADDED = ()
REMOVED = ()

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ()
DROP_MIXINS = ()
# No mixin moves in this build: the canvas, inspector, output and value-pin mixins
# are already in the config's "client" list, and nothing new is registered.

CHANGELOG_EN = (
    "Build 287: fixes the crash when opening a dialogue with editing - "
    "NoSuchMethodError PursuitGraphNode272.hit(NpcGraphCanvas, NpcDialogueGraph, double, double). "
    "Build 286 replaced PursuitGraphNode272 to remove the old per-pixel wire drawing and removed "
    "the card culling and hit test with it. Those two are called from the canvas mixin "
    "NpcPursuitCanvasMixin272, which is compiled against Build 285 and is not recompiled by the "
    "build, so the compiler could not see the missing methods; the graph broke as soon as the "
    "dialogue screen drew its background. visible() and hit() are restored exactly as they were, "
    "and the gates gained a check that compares every class with the previous jar and refuses a "
    "build in which a class lost a method or a field that another class still calls. "
)
PATCHINFO = (
    "Сборка 287: исправлен вылет при открытии диалога с правкой — "
    "NoSuchMethodError PursuitGraphNode272.hit(NpcGraphCanvas, NpcDialogueGraph, double, double). "
    "В 286 класс PursuitGraphNode272 переписывался, чтобы убрать старое рисование провода по "
    "пикселям, и вместе с ним пропали отсечение и попадание по карточке. Их вызывает миксин "
    "холста NpcPursuitCanvasMixin272, собранный против 285 и в сборке не перекомпилируемый, "
    "поэтому компилятор пропустил пропажу, а граф падал, как только экран диалога рисовал фон. "
    "visible() и hit() восстановлены ровно как были, а в гейты добавлена проверка: каждый класс "
    "сверяется с предыдущим jar, и сборка не проходит, если класс потерял метод или поле, "
    "которые кто-то в jar ещё вызывает. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build287-src/out")
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
