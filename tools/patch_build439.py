#!/usr/bin/env python3
"""
Build 439 - Natural Organic 3D Loot Scatter for Hands-On Furniture / Diegesis Containers.

Builds on Build 438 (``settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar``):
  1. Server scatter seed stamping & validated one-shot pose commit (``LootPoolManager436``,
     ``ChestDiegesisCompat436``, ``ChestLootNet436``):
       * When ``LootPoolManager436#populateContainer`` places dungeon loot inside a Diegesis /
         Hands-On Furniture ``StationHost``, it clears any stale poses on populated/empty slots
         and stamps ``SettledLandsScatterSeed`` inside ``StationState#data()``.
       * Clients compute exact poses and send one ``settledlands:chest_loot_scatter`` C2S payload
         containing Base64 binary pose entries; the server verifies proximity (<= 64 blocks),
         chunk load, and matching seed, sanitizes poses via ``ItemPose#sanitized``, resolves
         non-cyclic support slots, commits poses into ``StationState``, clears the seed so no
         other player can re-roll, and saves/syncs via ``be.setChanged()``.
  2. Exact client 3D scatter placement (``DiegesisScatterMixin439``, ``DiegesisScatterClient439``,
     ``ChestScatterClientInit439``, ``ChestOpenWatch401``):
       * Optional ``@Pseudo`` compat mixin in ``settledlands.compat.mixins.json`` hooks
         ``SceneInstance#freeSpot(PlacedItem, PlacedShape)`` when ``SettledLandsScatterSeed != 0``.
       * Uses exact client ``PlacedShape#extents`` and ``PlaceSurface`` bounds/``open()`` sides
         across all surfaces (cabinets, drawers, fridge shelves & door pockets, coolers, stoves,
         mailboxes), distributing loot across surfaces weighted by area, applying natural yaw/
         flat-rest variations, varied scale within ``scaleRange``, neat 1-level stacking on wide
         flat supports, open-edge padding to avoid physics drops, and zero AABB overlaps.
       * Registers a global ``DiegesisClient#addBehavior`` handler (with a ``ChestOpenWatch401``
         tick watch fallback for older Diegesis 1.21.1 builds) that re-scatters on late chunk
         sync and commits poses on first open/focus.
"""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

BASE_VERSION = "0.8.0-build438-shelf-highlight-axiom-anchor-fix"
NEW_VERSION = "0.8.0-build439-furniture-3d-loot-scatter"
OUT_NAME = "settledlands-0.8.0-build439-furniture-3d-loot-scatter.jar"

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
    ROOT / "build303-src" / "stubs439-src",
)

SOURCE_FILES = (
    "dev/settledlands/chest/ChestDiegesisCompat436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/chest/ChestLootNet436.java",
    "dev/settledlands/chest/ChestOpenWatch401.java",
    "dev/settledlands/chest/DiegesisScatterClient439.java",
    "dev/settledlands/chest/ChestScatterClientInit439.java",
    "dev/settledlands/mixin/compat/DiegesisScatterMixin439.java",
)

INNER_SYNC = (
    "dev/settledlands/chest/ChestLootNet436$Pools.class",
)

REPLACED = (
    "dev/settledlands/chest/ChestDiegesisCompat436.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/chest/ChestLootNet436.class",
    "dev/settledlands/chest/ChestOpenWatch401.class",
)

ADDED = (
    "dev/settledlands/chest/ChestDiegesisCompat436$PoseEntry.class",
    "dev/settledlands/chest/ChestLootNet436$ScatterPoses.class",
    "dev/settledlands/chest/DiegesisScatterClient439.class",
    "dev/settledlands/chest/DiegesisScatterClient439$SentEntry.class",
    "dev/settledlands/chest/DiegesisScatterClient439$SupportCandidate.class",
    "dev/settledlands/chest/DiegesisScatterClient439$SurfaceScore.class",
    "dev/settledlands/chest/DiegesisScatterClient439$BehaviorInvocationHandler.class",
    "dev/settledlands/chest/DiegesisScatterClient439$RawPlacedInfo.class",
    "dev/settledlands/chest/ChestScatterClientInit439.class",
    "dev/settledlands/mixin/compat/DiegesisScatterMixin439.class",
)

MARKERS = {
    "dev/settledlands/chest/ChestDiegesisCompat436.class": (
        "SettledLandsScatterSeed", "markLootScatter", "commitScatteredPoses", "encodePoses",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": ("markLootScatter",),
    "dev/settledlands/chest/ChestLootNet436.class": (
        "chest_loot_scatter", "sendScatterPoses", "commitScatteredPoses",
    ),
    "dev/settledlands/chest/ChestOpenWatch401.class": ("tickWatch",),
    "dev/settledlands/chest/DiegesisScatterClient439.class": (
        "scatterFreeSpot", "trySyncFocusedStation", "registerBehavior", "tickWatch",
    ),
    "dev/settledlands/chest/ChestScatterClientInit439.class": ("onClientSetup",),
    "dev/settledlands/mixin/compat/DiegesisScatterMixin439.class": (
        "settledlands$scatterFreeSpot439", "scatterFreeSpot",
    ),
}

CHANGELOG_EN = (
    "Build 439 replaces deterministic back-to-front shelf grids in Hands-On Furniture / Diegesis "
    "dungeon containers with natural client+server 3D item scattering using exact PlacedShape and "
    "PlaceSurface bounds, varied yaw/scale/flat-rest and 1-level stacking, open-edge safety padding, "
    "and one-shot server persistence via settledlands:chest_loot_scatter."
)
PATCHINFO = (
    "Сборка 439: естественная 3D-раскладка лута в мебели Hands-On Furniture / Diegesis.\n"
    "  * Вместо ровной сетки от задней стенки к передней лут в полках, шкафах, ящиках и\n"
    "    холодильниках данжей теперь раскладывается естественно по всем поверхностям\n"
    "    (включая нижние полки, закрытые ящики комодов и дверные карманы холодильника);\n"
    "  * Клиент рассчитывает точные 3D-размеры каждой модели (PlacedShape#extents) и границы\n"
    "    поверхности (PlaceSurface, отступ от открытых краёв и потолка), варьирует угол yaw,\n"
    "    масштаб, лежачее/стоячее положение и редкие стопки на широких плоских предметах без пересечений;\n"
    "  * Сервер помечает заспавненный лут-контейнер сидом SettledLandsScatterSeed, а при первом\n"
    "    открытии/фокусе клиент фиксирует вычисленные позы на сервере одним валидируемым пакетом.\n"
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


def compile_build439(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build439 compile failed:\n" + r2.stdout + r2.stderr)


def patch_compat_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    client = list(doc.get("client") or [])
    if "DiegesisScatterMixin439" not in client:
        client.append("DiegesisScatterMixin439")
    doc["client"] = client
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out439",
                    help="directory for compiled build 439 classes")
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
        print("compiling Build 439 sources via ECJ (-21)...")
        compile_build439(base, srcroot)
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

    compat_name = "settledlands.compat.mixins.json"
    if compat_name not in blobs:
        raise SystemExit("base jar missing %s" % compat_name)
    blobs[compat_name] = patch_compat_mixins(blobs[compat_name])
    print("  updated : %s (added client DiegesisScatterMixin439)" % compat_name)

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
