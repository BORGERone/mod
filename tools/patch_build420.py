#!/usr/bin/env python3
"""
Build 420 - the anchor: a build's graphs travel inside a blueprint.

The author's report: «в этих схематиках не копируются графы блоков». They cannot be copied today, because
nothing of a graph is in a block - `settledlands_block_triggers`, `settledlands_door_graphs`,
`settledlands_chest_graphs` and `settledlands_doors` are world data keyed by **absolute** position, and a
copy tool moves blocks and block-entities.

This build puts the graphs of a build inside a marker entity, **relative to the marker**, so that the NBT
a blueprint already carries carries them too:

  * `NpcGraphAnchorCore420` (+ `Entry`, `Fingerprint`, `Result`, `StoreSink`, `BlockLookup`) - the rule, and
    only the rule: packing to/from NBT, the eight arrangements (four quarter turns x mirror), the
    fingerprint score that decides which of them a paste really used, and the walk that lays every entry
    over the pasted marker. No game class except `BlockPos`, `CompoundTag`, `ListTag`;
  * `NpcGraphAnchorFiles420` - the world: the four stores opened one by one (a broken file costs only its
    own kind), the gathering of a build within a radius, and the writes;
  * `NpcGraphAnchor420` - the marker itself: the base mod's own invisible `settledlands:guide_trigger`,
    found by the NBT it carries and not by a name, sealed automatically next to a building that has graphs,
    and materialised when a stamp says it was carried somewhere else. Rotation is never read from the
    marker's yaw - a blueprint's turn does not reach an entity's yaw - but decided by the fingerprint;
  * `NpcGraphAnchorEvents420` - one `ServerTickEvent.Post`, paced at the same 40 ticks the radius pass
    uses, doing nothing while no player is online;
  * `NpcGraphAnchorInteract420` - the wand on a body: "this build has an anchor", cancelling the click only
    when something was really written.

Answering the author's four choices of this conversation: automatic sealing, automatic placement in a free
cell, the pasted copy wins over whatever stood there, and rotation/mirror in this same build.

Base 419. Five classes and six inner classes added, six mixin-free listeners, no mixin added or removed, no
new block and no new store.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build420-graph-anchor"
NEW_VERSION = "0.8.0-build421-anchor-registry-fix"
OUT_NAME = "settledlands-0.8.0-build421-anchor-registry-fix.jar"

REMOVED = ()
# Build 421 replaces the very classes build 420 added: same names, same layout, one compile mirror fixed.
REPLACED = (
    "dev/settledlands/npc/NpcGraphAnchorFiles420.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
)
ADDED = (
    "dev/settledlands/npc/NpcGraphAnchorCore420.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Entry.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Fingerprint.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Result.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$StoreSink.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$BlockLookup.class",
    "dev/settledlands/npc/NpcGraphAnchorFiles420.class",
    "dev/settledlands/npc/NpcGraphAnchorFiles420$1.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
    "dev/settledlands/npc/NpcGraphAnchorEvents420.class",
    "dev/settledlands/npc/NpcGraphAnchorInteract420.class",
    "dev/settledlands/npc/NpcGraphAnchorCommand420.class",
)

# The strings that must be inside a class before it may enter the jar - a marker that was never sealed
# must be an ordinary marker, so these are the load-bearing ones.
MARKERS = {
    "dev/settledlands/npc/NpcGraphAnchorCore420.class": (
        "SettledLandsAnchor",
        "Graphs",
        "Off",
        "Lock",
        "FP",
    ),
    "dev/settledlands/npc/NpcGraphAnchorInteract420.class": (
        "onEntityInteract",
        "wandOn",
        "setCanceled",
    ),
    "dev/settledlands/npc/NpcGraphAnchorEvents420.class": (
        "ServerTickEvent",
        "getGameTime",
    ),
    "dev/settledlands/npc/NpcGraphAnchorCommand420.class": (
        "settledlands",
        "anchor",
        "seal",
    ),
    "dev/settledlands/npc/NpcGraphAnchor420.class": (
        "settledlands",
        "guide_trigger",
    ),
    "dev/settledlands/npc/NpcGraphAnchorFiles420.class": (
        "BlockTriggerStore351",
        "DoorGraph382Store",
        "ChestGraph396Store",
        "DoorStore368",
    ),
}

CHANGELOG_EN = (
    "Build 420 makes a build's graphs travel inside a schematic: an invisible anchor marker "
    "(settledlands:guide_trigger) keeps every block, door and chest graph of a building relative to "
    "itself, so a copy tool that copies entities with their NBT (Axiom \"Copy Entities\", WorldEdit, "
    "Litematica, structure blocks) carries them, and the graphs are written back at the pasted place - "
    "including quarter turns and mirrors, decided by a fingerprint of the blocks around the marker. "
    "Sealing and placement are automatic, a pasted copy wins over what stood there, and "
    "/settledlands anchor seal|here|list plus the NPC wand on a body are the manual handles."
)
PATCHINFO = (
    "Сборка 420: графы постройки едут внутри схематика. Невидимый маркер-якорь "
    "(settledlands:guide_trigger) держит графы блоков, дверей (с замками) и сундуков относительно себя, "
    "поэтому копирование сущностей с NBT в Axiom/WorldEdit/Litematica переносит их, а на месте вставки "
    "графы записываются обратно по новым координатам - с поворотом и зеркалом, которые определяются "
    "отпечатком блоков вокруг маркера. Запечатывание и установка маркера автоматические, вставленная "
    "копия перезаписывает старое, руками есть /settledlands anchor seal|here|list и жезл НПС по телу.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = root / "out420"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    ADDED_OK = set()
    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

    # REPLACED first: a name can be in both lists only by mistake, and replacing has the stricter checks.
    for name in REPLACED + ADDED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in ADDED and name not in REPLACED and name in blobs:
            # Build 421 rewrites two of the classes build 420 adds and leaves the other ten alone; the ones
            # it leaves alone have to be byte-identical to what 420 shipped, which is the check here.
            if blobs[name] != data:
                raise SystemExit("%s already in base jar and differs - put it in REPLACED" % name)
            print("  same    : %s (unchanged from base)" % name)
            ADDED_OK.add(name)
            continue
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name in ADDED:
            if name not in order:
                order.append(name)
            print("  added   : %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to base - why is it in REPLACED?" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
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
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
