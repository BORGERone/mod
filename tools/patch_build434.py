#!/usr/bin/env python3
"""
Build 434 - Create Contraption "Pos" Preservation, Immediate Controller Binding & Bearing Attach Fix.

Builds on Build 433 (``settledlands-0.8.0-build433-nbt-contraption-and-spawn.jar``):
  1. Preserves transformed ``Pos`` in ``DungeonNbt431``:
       * Fixes why ``create:stationary_contraption`` (and ``create:super_glue``) disappeared in-game:
         Build 433 stripped ``"Pos"`` from ``te.mcNbt``, so Vanilla MC 1.21.1 ``Entity.load(te.mcNbt)``
         reset the entity position to ``(0, 0, 0)`` right before ``ControlledContraptionEntity.readAdditional``
         computed ``controllerPos = ControllerRelative + blockPosition()`` -> ``(1, 0, 0)`` -> discarded on
         tick 1 for missing bearing / shifted super-glue AABB to origin!
       * Now writes exact rotated ``Pos = [wx, wy, wz]`` into ``te.mcNbt`` for contraptions, ``super_glue``,
         and normal entities, fixes fractional-offset contraption ``ControllerRelative`` calculation via
         world coordinates, and rotates ``CapturedMultiblocks`` & ``Interactors`` inside ``Contraption``.
  2. Immediate contraption-to-bearing binding & safe kinetic wake (``CreateCompat431`` & ``DungeonTemplate425``):
       * Calls ``entity.moveTo(te.wx, te.wy, te.wz)`` again after ``entity.load(te.mcNbt)`` so ``boundingBox``
         is recomputed from the loaded ``Contraption.bounds``.
       * Adds ``CreateCompat431.attachContraption(level, entity)`` right after ``level.addFreshEntity(entity)``
         to bind ``ControlledContraptionEntity`` to its ``IControlContraption`` bearing immediately.
       * Routes non-windmill ``MechanicalBearingBlockEntity`` through Pass 1 (``attachKinetics()``) and
         excludes it from Pass 2 (``updateGeneratedRotation()``) in ``activateCreateRoom``, preventing
         ``assembleNextTick = true`` at ``speed == 0`` from disassembling pre-assembled contraptions before
         neighboring generators push kinetic rotation.

Base 433. Zero new mixins. Zero new external MC API calls.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build433-nbt-contraption-and-spawn"
NEW_VERSION = "0.8.0-build434-contraption-pos-fix"
OUT_NAME = "settledlands-0.8.0-build434-contraption-pos-fix.jar"

INNER_SYNC = (
    "dev/settledlands/dungeon/DungeonNbt431$CountingInputStream.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtCompound.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtReader.class",
    "dev/settledlands/dungeon/DungeonNbt431$NbtValue.class",
    "dev/settledlands/dungeon/DungeonNbt431$TransformedEntity.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)
REPLACED = (
    "dev/settledlands/dungeon/CreateCompat431.class",
    "dev/settledlands/dungeon/DungeonNbt431.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/dungeon/CreateCompat431.class": (
        "attachContraption",
        "MechanicalBearingBlockEntity",
        "ControlledContraptionEntity",
        "IControlContraption",
    ),
    "dev/settledlands/dungeon/DungeonNbt431.class": (
        "rotateHorizontalVec",
        "CapturedMultiblocks",
        "Interactors",
    ),
    "dev/settledlands/dungeon/DungeonTemplate425.class": (
        "attachContraption",
    ),
}

CHANGELOG_EN = (
    "Build 434 fixes Create stationary_contraption / super_glue disappearing on spawn by writing "
    "transformed world Pos into entity NBT (preventing Vanilla Entity.load from resetting "
    "blockPosition to 0,0,0 before ControlledContraptionEntity.readAdditional resolves "
    "ControllerRelative), re-syncing bounding boxes after load, immediately attaching contraption "
    "entities to their bearings, and keeping MechanicalBearingBlockEntity out of generator "
    "updateGeneratedRotation() pass so pre-assembled mechanisms never prematurely disassemble."
)
PATCHINFO = (
    "Сборка 434: исправление исчезновения крутящихся контрапций Create (create:stationary_contraption).\n"
    "  * В тегах сущностей теперь сохраняется пересчитанный мировой список Pos=[wx, wy, wz],\n"
    "    благодаря чему Entity.load(te.mcNbt) больше не сбрасывает координату в (0, 0, 0) до\n"
    "    вычисления ControllerRelative + blockPosition() и не провоцирует удаление на 1-м тике;\n"
    "  * После загрузки контрапции повторно вызывается moveTo для пересчёта boundingBox,\n"
    "    сущность немедленно привязывается к подшипнику через CreateCompat431.attachContraption,\n"
    "    а MechanicalBearingBlockEntity подключается к кинетической сети в Pass 1 без вызова\n"
    "    updateGeneratedRotation() на нулевой скорости (защита от ложной разборки механизма);\n"
    "  * Для дробно-смещённых контрапций и мультиблоков (CapturedMultiblocks/Interactors)\n"
    "    исправлен расчёт ControllerRelative и поворот вложенных координат.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out434",
                    help="directory containing compiled build 434 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
    if not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in INNER_SYNC:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing expected inner class: %s" % name)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        blobs[name] = data
        print("  synced  : %s (%d bytes)" % (name, len(data)))

    for name in REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name not in blobs:
            raise SystemExit("base jar has no entry: %s" % name)
        if blobs[name] == data:
            raise SystemExit("%s is byte-identical to base - why is it in REPLACED?" % name)
        print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for name in ADDED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("%s already in base jar - put it in REPLACED" % name)
        order.append(name)
        blobs[name] = data

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

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
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
