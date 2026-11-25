#!/usr/bin/env python3
"""
Build 444 - Custom Book Defaults & Exact Page Limit + Fair Lectern/Bookshelf Loot Odds.

Builds on Build 443 (``settledlands-0.8.0-build443-custom-books-lectern-bookshelf.jar``):
  1. Custom Book defaults & exact vanilla page fit enforcement:
       * ``CustomBook443#DEFAULT_AUTHOR = "Неизвестен"`` across new books, parsed files missing an
         ``author:`` line, imported writable/authorless books, and book editor hint/reset paths.
       * ``CustomBook443#fitPageText`` + ``LootPoolBookEditorScreen443#fitsVanillaPage`` enforce
         vanilla Minecraft page bounds (114px width x 14 lines, including trailing newline cursor
         check and bold/formatting width) so text cannot be typed or pasted beyond what fits on
         the book page, truncating oversized pastes at the last clean non-code boundary.
  2. Fair full-pool probabilities and strict item caps for Lecterns and Chiseled Bookshelves:
       * ``LootPool436#rollLecternDrops`` rolls 1 attempt against the FULL unfiltered loot pool
         (respecting ``Шт = 0`` / ``0-N`` and all ``Редк.<=`` / ``Вес ящ.`` filters); non-book
         drops result in an empty lectern instead of inflating book odds to 100%.
       * ``LootPool436#rollBookshelfDrops`` rolls against the FULL pool without stripping non-books
         first (non-books become missed empty rolls), places 1 book per accepted drop (preventing
         stacked entries like ``2-6`` from spilling into all 6 slots), and enforces the node's
         ``Шт`` item limit.
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

BASE_VERSION = "0.8.0-build443-custom-books-lectern-bookshelf"
NEW_VERSION = "0.8.0-build444-book-limits-and-lectern-odds"
OUT_NAME = "settledlands-0.8.0-build444-book-limits-and-lectern-odds.jar"

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
    "dev/settledlands/chest/CustomBook443.java",
    "dev/settledlands/chest/CustomBookManager443.java",
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/npc/client/LootPoolBookEditorScreen443.java",
)

MARKERS = {
    "dev/settledlands/chest/CustomBook443.class": (
        "Неизвестен", "fitPageText", "rollbackIncompleteFormatting",
    ),
    "dev/settledlands/chest/CustomBookManager443.class": (
        "Неизвестен", "extractBookFromStack", "buildBookStack",
    ),
    "dev/settledlands/chest/LootPool436.class": (
        "rollLecternDrops", "rollBookshelfDrops",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": (
        "rollLecternDrops", "rollBookshelfDrops", "syncChiseledShelfState",
    ),
    "dev/settledlands/npc/client/LootPoolBookEditorScreen443.class": (
        "countVanillaPageLines", "fitsVanillaPage", "fitPageText", "page_full",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.book_editor.chars_hint": "Строк: %s / %s  |  Симв.: %s / %s  |  Палитра вставляет коды в Заголовок/Страницу",
    "gui.settledlands.book_editor.page_full": "Страница заполнена: максимум %s строк на лист книги",
}

LANG_SET_EN = {
    "gui.settledlands.book_editor.chars_hint": "Lines: %s / %s  |  Chars: %s / %s  |  Palette inserts codes into Title/Page",
    "gui.settledlands.book_editor.page_full": "Page is full: maximum %s lines fit on a book page",
}

CHANGELOG_EN = (
    "Build 444 sets the default custom book author to 'Неизвестен', enforces exact vanilla book page "
    "wrapping limits (114px x 14 lines) inside the Book Editor, and fixes Lectern / Chiseled Bookshelf "
    "loot rolling so non-book items in mixed pools behave as true misses and bookshelves strictly "
    "respect the node's item count cap."
)
PATCHINFO = (
    "Сборка 444: исправления редактора книг и честные шансы лута на кафедрах / книжных полках.\n"
    "  * Автор новых и импортированных без автора книг по умолчанию теперь «Неизвестен»;\n"
    "  * В редакторе книг введён точный ограничитель текста страницы по реальной вместимости\n"
    "    страницы Minecraft (114px x 14 строк с учётом &l и цветовых кодов), вставка обрезается\n"
    "    по границе влезшего текста без разрыва кодов форматирования;\n"
    "  * Кафедра (Lectern) и резная книжная полка (Chiseled Bookshelf) теперь крутят рулетку по\n"
    "    полному смешанному пулу: выпавший не-книжный предмет оставляет слот пустым, а полка\n"
    "    кладёт максимум 1 книгу на бросок и строго соблюдает лимит «Шт» узла.\n"
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


def compile_build444(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build444 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out444",
                    help="directory for compiled build 444 classes")
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
        print("compiling Build 444 sources via ECJ (-21)...")
        compile_build444(base, srcroot)
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
