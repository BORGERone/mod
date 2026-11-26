#!/usr/bin/env python3
"""
Build 445 - Direct 0-100% Per-Entry Loot Chances Without Duplicate Entry Draws Everywhere.

Builds on Build 444 (``settledlands-0.8.0-build444-book-limits-and-lectern-odds.jar``):
  1. Direct 0-100% per-entry loot probability everywhere (chests, chiseled bookshelves, lecterns):
       * ``LootPool436#rollDrops`` evaluates each shuffled eligible row at most ONCE against its
         direct ``0-100%`` chance (``clamp(rarity, 0, 100) / 100``), respecting node ``Редк.<=``,
         ``Вес ящ.``, and ``Шт`` caps — no single pool row ever duplicates itself beyond its
         ``Кол-во`` field.
       * ``LootPool436#rollLecternDrops`` and ``#rollBookshelfDrops`` apply the exact same direct
         ``0-100%`` per-entry chance on compatible book rows so a single ``book_3 | 1 | 50`` entry
         with ``Шт: 3`` spawns 1 copy 50% of the time and leaves the lectern/bookshelf empty 50%
         of the time, while multiple distinct book rows can fill up to ``Шт`` slots cleanly.
  2. Accurate editor & format documentation:
       * ``LootPool436#computeEffectiveChancesPct`` computes direct ``0-100%`` per-entry odds
         (accounting for ``Редк.<=``, ``Вес ящ.``, and ``Шт`` caps/ranges).
       * ``LootPoolEditorScreen440`` validates ``0 < Редк. <= 100%``, displays total pool weight in
         the summary line, distinguishes missed ``%`` preview rolls from node-blocked pools, and
         updates tooltips / ``README_FORMAT.txt.disabled`` / ``ru_ru.json`` / ``en_us.json``.
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

BASE_VERSION = "0.8.0-build444-book-limits-and-lectern-odds"
NEW_VERSION = "0.8.0-build445-direct-percent-loot-no-duplicates"
OUT_NAME = "settledlands-0.8.0-build445-direct-percent-loot-no-duplicates.jar"

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
    ROOT / "build303-src" / "stubs440-src",
    ROOT / "build303-src" / "stubs443-src",
)

SOURCE_FILES = (
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
)

MARKERS = {
    "dev/settledlands/chest/LootPool436.class": (
        "MAX_CHANCE_PCT", "rollDrops", "rollLecternDrops", "rollBookshelfDrops",
        "computeEffectiveChancesPct",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": (
        "chance_pct", "rollLecternDrops", "rollBookshelfDrops",
    ),
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class": (
        "preview_miss", "formatWeight", "countBlockedBySpec",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.editor.summary": "Записей: %s | Сумм. вес: %s",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Прямой %% шанс выпадения этой записи (0–100%%). Напр. 50 = ровно 50%% без дублей",
    "gui.settledlands.loot_pool.editor.tip_chance": "Итоговый %% шанс выпадения записи за контейнер (учитывает Редк., Вес ящ., Редк.<= и Шт)",
    "gui.settledlands.loot_pool.editor.tip_node_rls": "Макс. выпавших записей на ящик/полку (без дублей одной записи сверх её Кол-во; пусто = по весу)",
    "gui.settledlands.loot_pool.editor.skewed_chance_tip": "С учётом узла: %s (базовый шанс строки: %s)",
    "gui.settledlands.loot_pool.editor.err_row": "Проверьте предмет, кол-во (1-64), шанс Редк. (>0 и <=100%%) и вес (>0) в строке %s",
    "gui.settledlands.loot_pool.editor.preview_miss": "Тест броска: ничего не выпало (промах по %% шанса)",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.editor.summary": "Entries: %s | Total weight: %s",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Direct %% drop chance for this entry (0–100%%). E.g. 50 = exact 50%% chance, no duplicate draws",
    "gui.settledlands.loot_pool.editor.tip_chance": "Effective %% drop chance per container (accounts for Rarity %%, Crate weight, Rarity cap, and Max items)",
    "gui.settledlands.loot_pool.editor.tip_node_rls": "Max dropped entries per container/shelf (no duplicates beyond row Count; blank = fill by weight)",
    "gui.settledlands.loot_pool.editor.skewed_chance_tip": "With node limits: %s (entry base chance: %s)",
    "gui.settledlands.loot_pool.editor.err_row": "Check item, count (1-64), Rarity %% (>0 and <=100%%), and weight (>0) on row %s",
    "gui.settledlands.loot_pool.editor.preview_miss": "Test roll: nothing dropped (missed %% roll)",
}

CHANGELOG_EN = (
    "Build 445 switches loot pool rarity to direct 0-100% per-entry probabilities without "
    "duplicating a single pool entry beyond its configured count across chests, chiseled "
    "bookshelves, and lecterns, and updates the editor chance column, tooltips, and summary."
)
PATCHINFO = (
    "Сборка 445: прямой 0–100% шанс выпадения каждой записи без дублей во всех контейнерах.\n"
    "  * Поле «Редк.» для каждой строки пула теперь означает прямой процентный шанс выпадения\n"
    "    этой записи за контейнер (0–100%), одинаково для сундуков, книжных полок и кафедр.\n"
    "    Например: «book_3 | 1 | Редк. 50 | Вес 10» при «Шт: 3» выпадает 1 раз с шансом 50%\n"
    "    и оставляет полку/кафедру пустой в 50% случаев (без повторов сверх «Кол-во»);\n"
    "  * Несколько разных записей в одном пуле перемешиваются и ограничиваются «Шт» и «Вес ящ.»;\n"
    "  * Колонка «Шанс», подсказки в редакторе и README_FORMAT.txt.disabled обновлены под прямой %.\n"
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


def compile_build445(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build445 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out445",
                    help="directory for compiled build 445 classes")
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
        print("compiling Build 445 sources via ECJ (-21)...")
        compile_build445(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    outer_stems = [rel[:-5] for rel in SOURCE_FILES]
    stale_removed = []
    new_order = []
    for name in order:
        if any(name == stem + ".class" or name.startswith(stem + "$") for stem in outer_stems):
            stale_removed.append(name)
            blobs.pop(name, None)
        else:
            new_order.append(name)
    order = new_order

    compiled_map = {}
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        compiled_map[rel] = compiled.read_bytes()

    for rel, data in sorted(compiled_map.items()):
        for marker in MARKERS.get(rel, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (rel, marker))
        action = "replaced" if rel in stale_removed else "added   "
        print("  %s: %s (%d bytes)" % (action, rel, len(data)))
        order.append(rel)
        blobs[rel] = data

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (%d keys updated)" % (lp, len(entries)))

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
    (outdir / (OUT_NAME + ".sha256sums.txt")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
