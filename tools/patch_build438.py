#!/usr/bin/env python3
"""
Build 438 - Wand Highlight for Furniture Shelves & Containers + Embedded BlockEntity Chest Graphs
for Axiom Blueprints & Guaranteed In-Room Dungeon Anchor Creation.

Builds on Build 437 (``settledlands-0.8.0-build437-anchor-furniture-fix.jar``):
  1. Wand highlights for shelves/cabinets/chests (``RadiusPass354``):
       * Hovering/holding the NPC wand now highlights both ``BlockTriggerStore351`` blocks AND
         ``ChestGraph396Store`` containers (``ChestSpot`` - shelves, storage cabinets, kitchen
         cabinets, drawers, crates, coolers, fridges, mail boxes, storage jars, chests, barrels),
         sorted closest-first to wand-holding players with ``MAX_HIGHLIGHT = 64``.
  2. Embedded container graphs inside ``BlockEntity`` NBT (``ChestGraph396``,
     ``NpcGraphAnchorFiles420``, ``NpcGraphAnchor420``, ``DungeonTemplate425``):
       * ``ChestGraph396`` stores/clears ``SettledLandsChestGraph`` inside
         ``((IBlockEntityExtension) be).getPersistentData()`` (NeoForge ``NeoForgeData``), which
         Axiom always exports inside ``BlockEntities`` even when ``copyWithEntities`` is false or
         Magic Select omits air cells.
       * Existing Build 436/437 shelves automatically receive ``SettledLandsChestGraph`` on open,
         save, or anchor seal; ``NpcGraphAnchor420`` restores embedded graphs from already-loaded
         chunks around players when Axiom structures are pasted in-world without entities.
  3. In-room anchor placement & guaranteed dungeon anchor creation (``NpcGraphAnchor420``,
     ``LootPoolManager436``, ``DungeonTemplate425``, ``DungeonManager425``):
       * Fixed the ``return true;`` bug in ``NpcGraphAnchor420.hasFloorOrCeiling``: ``freeSpot`` now
         scores candidate air cells by indoor enclosure (ceiling overhead, floor support, and
         surrounding walls) so markers spawn inside rooms beside shelves rather than outside walls.
       * ``DungeonManager425.hasEntities`` also checks ``hasEmbeddedChestGraphs`` so rooms with
         embedded shelf graphs wait for ``ENTITY_TICKING`` chunks before entity/anchor placement.
       * ``DungeonTemplate425.placeBlockEntitiesAndEntities`` restores embedded shelf graphs into
         ``ChestGraph396Store``, populates their loot pools without double-rolling
         (``LootPoolManager436.onDungeonRoomPlaced(..., populatedOut)``), and spawns/seals a live
         ``settledlands:guide_trigger`` anchor whenever none arrived in the schematic (and seals
         empty triggers when present).
       * ``DungeonManager425`` calls ``NpcGraphAnchor420.ensureForPlayer`` right after teleporting
         into ``settledlands:hell_dungeon`` so start-room anchors appear on tick 0.
"""

import argparse
import hashlib
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

BASE_VERSION = "0.8.0-build437-anchor-furniture-fix"
NEW_VERSION = "0.8.0-build438-shelf-highlight-axiom-anchor-fix"
OUT_NAME = "settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar"

ROOT = pathlib.Path(__file__).resolve().parent.parent

STUB_LAYERS = (
    ROOT / "tools" / "api271",
    ROOT / "build303-src" / "stubs",
    ROOT / "build303-src" / "stubs345-src",
    ROOT / "build303-src" / "stubs396-src",
    ROOT / "build303-src" / "stubs420-src",
    ROOT / "build303-src" / "api-src",
    ROOT / "build303-src" / "api3-src",
    ROOT / "build303-src" / "api4-src",
    ROOT / "build303-src" / "api5-src",
    ROOT / "build303-src" / "api416-src",
    ROOT / "build303-src" / "stubs425-src",
    ROOT / "build303-src" / "stubs436-src",
    ROOT / "build303-src" / "stubs438-src",
)

SOURCE_FILES = (
    "dev/settledlands/npc/RadiusPass354.java",
    "dev/settledlands/chest/ChestGraph396.java",
    "dev/settledlands/npc/NpcGraphAnchorFiles420.java",
    "dev/settledlands/npc/NpcGraphAnchor420.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/dungeon/DungeonTemplate425.java",
    "dev/settledlands/dungeon/DungeonManager425.java",
)

INNER_SYNC = (
    "dev/settledlands/npc/RadiusPass354$Spot.class",
    "dev/settledlands/npc/RadiusPass354$DoorSpot.class",
    "dev/settledlands/npc/RadiusPass354$ChestSpot.class",
    "dev/settledlands/npc/RadiusPass354$After.class",
    "dev/settledlands/npc/NpcGraphAnchorFiles420$1.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)

REPLACED = (
    "dev/settledlands/npc/RadiusPass354.class",
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/npc/NpcGraphAnchorFiles420.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
    "dev/settledlands/dungeon/DungeonManager425.class",
)

ADDED = (
    "dev/settledlands/npc/RadiusPass354$HighlightCandidate.class",
)

MARKERS = {
    "dev/settledlands/npc/RadiusPass354.class": ("HighlightCandidate",),
    "dev/settledlands/chest/ChestGraph396.class": ("SettledLandsChestGraph", "writeBlockEntityGraph", "readBlockEntityGraph", "syncEmbeddedGraphsNear"),
    "dev/settledlands/npc/NpcGraphAnchorFiles420.class": ("ensureBlockEntityGraph",),
    "dev/settledlands/npc/NpcGraphAnchor420.class": ("syncEmbeddedGraphsNear", "interiorScore", "ensureForPlayer"),
    "dev/settledlands/chest/LootPoolManager436.class": ("onDungeonRoomPlaced",),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("hasEmbeddedChestGraphs", "extractEmbeddedChestGraph"),
    "dev/settledlands/dungeon/DungeonManager425.class": ("hasEmbeddedChestGraphs", "ensureForPlayer"),
}

CHANGELOG_EN = (
    "Build 438 highlights shelves/cabinets/chests with graphs when holding the NPC wand (up to 64 nearest), "
    "embeds shelf/container graphs in BlockEntity NeoForgeData so Axiom blueprints always preserve them "
    "even without entities, fixes freeSpot in-room enclosure scoring so anchors never spawn outside walls, "
    "and auto-creates/seals missing dungeon room anchors from restored container graphs."
)
PATCHINFO = (
    "Сборка 438: подсветка полок/контейнеров с графами от жезла и перенос графов/якоря в Axiom.\n"
    "  * Жезл настройки НПС теперь подсвечивает искрами не только обычные блоки, но и полки,\n"
    "    шкафы, ящики и сундуки с графами (ChestSpot из ChestGraph396Store, до 64 ближайших);\n"
    "  * Графы полок и сундуков дополнительно сохраняются в BlockEntity (NeoForgeData.SettledLandsChestGraph),\n"
    "    поэтому чертежи Axiom (.bp) и Copy/Paste всегда переносят их даже без галочки Entities;\n"
    "  * Исправлен выбор клетки якоря freeSpot (убран безусловный return true, добавлена оценка потолка,\n"
    "    пола и стен) — якорь теперь ставится строго внутри комнаты рядом с полкой, а не снаружи;\n"
    "  * При спавне комнаты в данже (и при вставке в мире) встроенные графы полок восстанавливаются,\n"
    "    наполняются лутом и автоматически создают/запечатывают живой якорь settledlands:guide_trigger.\n"
)


def find_java():
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    try:
        import jdk4py  # type: ignore
        cand = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
        if cand.is_file():
            return str(cand)
    except Exception:
        pass
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java runtime found")


def compile_build438(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    src_root = ROOT / "build303-src" / "src"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        merged.mkdir()
        stubs_out.mkdir()
        for layer in STUB_LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(base_jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("stub compile failed:\n" + r1.stdout + r1.stderr)

        targets = [str(src_root / rel) for rel in SOURCE_FILES]
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{base_jar}:{stubs_out}", "-d", str(out_dir)] + targets,
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("build438 compile failed:\n" + r2.stdout + r2.stderr)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out438",
                    help="directory for compiled build 438 classes")
    ap.add_argument("--no-compile", action="store_true",
                    help="reuse existing --classes directory without running ECJ")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
    if not args.no_compile:
        print("compiling Build 438 sources via ECJ (-21)...")
        compile_build438(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in INNER_SYNC:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled inner class: %s" % src)
        if name not in blobs:
            raise SystemExit("base jar missing inner class: %s" % name)
        blobs[name] = src.read_bytes()
        print("  synced  : %s (%d bytes)" % (name, len(blobs[name])))

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
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        print("  added   : %s (%d bytes)" % (name, len(data)))
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
    print("  neoforge.mods.toml: version %s" % NEW_VERSION)

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
