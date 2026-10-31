#!/usr/bin/env python3
"""Build 285 - the locomotion sampler reads the right field.

Crash on rendering an NPC:

    java.lang.NoSuchFieldError: Class net.minecraft.world.entity.Entity does not
    have member field 'net.minecraft.world.entity.WalkAnimationState walkAnimation'
        at NpcMotionPlayerProxy256.handler$...$settledlands$observeLocomotion

Build 283 declared the walk state on the wrong class. In 1.21.1
`walkAnimation` lives on LivingEntity, not on Entity; the compile-time stub had it
on Entity, so the mixin compiled and then failed on the first NPC render. The
mixin now reads the field through a LivingEntity reference, and the stub moved to
LivingEntity so the same mistake cannot compile again.

No other change: no behaviour, no settings, no graph format.

Usage: python3 tools/patch_build285.py <base-284.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build284-value-pins-and-target-input"
NEW_VERSION = "0.8.0-build285-loco-field-fix"
OUT_NAME = "settledlands-0.8.0-build285-loco-field-fix.jar"

REPLACED = (
    "dev/settledlands/mixin/NpcLocoObserveMixin283.class",
)
ADDED = ()
REMOVED = ()

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ()
DROP_MIXINS = ()
# These three target client-only classes (the graph screen, the render bridge), so
# they belong in the config's "client" list like every other client mixin of this
# mod. Build 283 registered the two loco mixins in the common list; moved here.
CLIENT_MIXINS = ("NpcLocoObserveMixin283", "NpcLocoSmoothMixin283")

CHANGELOG_EN = (
    "Build 285: crash fix. Rendering any NPC threw NoSuchFieldError for "
    "Entity.walkAnimation - in 1.21.1 the walk state lives on LivingEntity, so the Build 283 "
    "locomotion sampler read a field that does not exist on the class it asked for. It now reads "
    "it through a LivingEntity reference; the compile-time stub was moved to LivingEntity as well, "
    "so the same mistake cannot compile again. Nothing else changed. "
)
PATCHINFO = (
    "Сборка 285: исправление вылета. При отрисовке любого NPC падало NoSuchFieldError по полю "
    "Entity.walkAnimation — в 1.21.1 состояние шага лежит в LivingEntity, поэтому замер анимации из "
    "сборки 283 читал поле, которого у запрошенного класса нет. Теперь чтение идёт через ссылку "
    "LivingEntity, и заглушка для компиляции тоже перенесена в LivingEntity, чтобы та же ошибка "
    "больше не компилировалась. Больше ничего не менялось. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build285-src/out")
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
