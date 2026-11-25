#!/usr/bin/env python3
"""
Build 437 - Graph Anchor Auto-Creation for Furniture/Chests/Doors, Immediate Save Sync,
Spatial Coverage Alignment, and Cross-Dimension Dungeon Materialisation Fix.

Builds on Build 436 (``settledlands-0.8.0-build436-furniture-loot-pools.jar``):
  1. NpcGraphAnchor420.graphPlaces now reads ALL four graph/lock stores
     (BlockTriggerStore351, ChestGraph396Store, DoorGraph382Store, DoorStore368) within
     PLAYER_BLOCKS (48.0D) on the marker's dimension, so furniture-only or door-only
     structures automatically receive a ``settledlands:guide_trigger`` anchor.
  2. Aligned spatial coverage & room-aware placement:
       * GATHER_BLOCKS increased to 32 (matching ANCHOR_SEARCH_BLOCKS = 32), and
         ensureAnchors checks coverage via the exact ``inside(..., GATHER_BLOCKS)`` cube
         around each marker's home/stamp so the 24..32 block dead zone is eliminated.
       * freeSpot prefers adjacent in-room cells (horizontal/floor-or-ceiling) before
         climbing above roofs, preventing wall-mounted shelves near ceilings from spawning
         markers outside the room bounds.
       * seal() clears stale anchor tags when entries are empty; sealHere() reuses a
         covering marker before spawning a new one; wandOn() refreshes around home().
  3. Immediate save/lock anchor sync (``anchorForSave``):
       * ChestGraph396.save, BlockTrigger351.save, DoorGraph382.save, and DoorEvents368
         key toggles immediately create or refresh the covering anchor on the same tick,
         so exporting blueprints right after editing never omits new shelves/graphs.
  4. Dimension-safe stamp & dungeon rotation fallback:
       * Stamps record ``SettledLandsAnchorDim`` so blueprints built around (0,0,0) in
         Overworld never collide with instance #0 at (0,0,0) in ``settledlands:hell_dungeon``.
       * DungeonTemplate425 passes exact room ``turns`` to LootPoolManager436.onDungeonRoomPlaced
         -> NpcGraphAnchor420.materialiseForDungeon, using NpcGraphAnchorCore420.placeWithTurn
         as a fallback if blocks around the marker missed the 60% fingerprint threshold,
         and still materialising guide_trigger markers even if entity spawn in a non-ticking
         chunk returns false.
"""

import argparse
import hashlib
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

BASE_VERSION = "0.8.0-build436-furniture-loot-pools"
NEW_VERSION = "0.8.0-build437-anchor-furniture-fix"
OUT_NAME = "settledlands-0.8.0-build437-anchor-furniture-fix.jar"

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
)

SOURCE_FILES = (
    "dev/settledlands/npc/NpcGraphAnchorCore420.java",
    "dev/settledlands/npc/NpcGraphAnchor420.java",
    "dev/settledlands/npc/BlockTrigger351.java",
    "dev/settledlands/chest/ChestGraph396.java",
    "dev/settledlands/door/DoorGraph382.java",
    "dev/settledlands/door/DoorEvents368.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/dungeon/DungeonTemplate425.java",
)

INNER_SYNC = (
    "dev/settledlands/npc/NpcGraphAnchorCore420$BlockLookup.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Entry.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Fingerprint.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Placement.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Result.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$StoreSink.class",
    "dev/settledlands/dungeon/DungeonTemplate425$BlockEntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$EntitySpec.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PaletteEntry.class",
    "dev/settledlands/dungeon/DungeonTemplate425$PlacementStats.class",
    "dev/settledlands/dungeon/DungeonTemplate425$StartTemplate.class",
)

REPLACED = (
    "dev/settledlands/npc/NpcGraphAnchorCore420.class",
    "dev/settledlands/npc/NpcGraphAnchor420.class",
    "dev/settledlands/npc/BlockTrigger351.class",
    "dev/settledlands/chest/ChestGraph396.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/dungeon/DungeonTemplate425.class",
)

ADDED = ()

MARKERS = {
    "dev/settledlands/npc/NpcGraphAnchorCore420.class": ("placeWithTurn", "Placement"),
    "dev/settledlands/npc/NpcGraphAnchor420.class": (
        "SettledLandsAnchorDim",
        "anchorForSave",
        "coveringMarker",
        "materialiseForDungeon",
    ),
    "dev/settledlands/npc/BlockTrigger351.class": ("anchorForSave",),
    "dev/settledlands/chest/ChestGraph396.class": ("anchorForSave",),
    "dev/settledlands/door/DoorGraph382.class": ("anchorForSave",),
    "dev/settledlands/door/DoorEvents368.class": ("anchorForSave",),
    "dev/settledlands/chest/LootPoolManager436.class": ("materialiseForDungeon",),
    "dev/settledlands/dungeon/DungeonTemplate425.class": ("onDungeonRoomPlaced",),
}

CHANGELOG_EN = (
    "Build 437 fixes graph anchor creation and dungeon materialisation for furniture shelves, chests, "
    "blocks, and doors: anchors now auto-create for all four graph/lock stores on save and tick, "
    "gather radius matches coverage (32 blocks, no 24..32 dead zone), in-room placement avoids roofs, "
    "and dungeon room placement tags stamp dimensions and falls back to known room rotation."
)
PATCHINFO = (
    "Сборка 437: исправление создания якоря графов и восстановления графов мебели/полок в данже.\n"
    "  * Якорь (settledlands:guide_trigger) теперь автоматически создаётся и для построек только\n"
    "    с графами мебели/сундуков (ChestGraph396Store) и дверей (DoorGraph382Store / DoorStore368),\n"
    "    а не только блок-триггеров;\n"
    "  * Мгновенное создание/обновление якоря сразу при сохранении графа полки, сундука, блока,\n"
    "    двери или замка (anchorForSave), без ожидания до 2 секунд перед сохранением чертежа;\n"
    "  * Радиус сбора GATHER_BLOCKS=32 согласован с проверкой покрытия (убрана мёртвая зона 24..32)\n"
    "    и улучшен выбор клетки внутри комнаты рядом с полкой (не на крыше);\n"
    "  * Штамп якоря хранит измерение (SettledLandsAnchorDim), чтобы постройки около (0,0,0)\n"
    "    не конфликтовали с нулевым инстансом данжа, а материализация комнаты данжа передаёт точный\n"
    "    поворот turns и использует его как fallback даже при переделке блоков вокруг маркера.\n"
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


def compile_build437(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build437 compile failed:\n" + r2.stdout + r2.stderr)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out437",
                    help="directory for compiled build 437 classes")
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
        print("compiling Build 437 sources via ECJ (-21)...")
        compile_build437(base, srcroot)
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
