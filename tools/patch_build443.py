#!/usr/bin/env python3
"""
Build 443 - Custom Colored Loot-Pool Books + Lectern & Chiseled Bookshelf Chest-Graph Support.

Builds on Build 442 (``settledlands-0.8.0-build442-rarity-chance-crate-fix.jar``):
  1. Custom colored loot-pool books (``config/settledlands/books/<id>.txt`` -> ``settledlands:book/<id>``):
       * ``CustomBook443``: pure data model, & / § / &#RRGGBB span parser, transliterating ID
         normalizer, file format, Base64-safe wire codecs, and client summary cache.
       * ``CustomBookManager443``: server disk CRUD, ``DataComponents.CUSTOM_NAME`` /
         ``DataComponents.WRITTEN_BOOK_CONTENT`` builder, inventory book extraction, and
         ``ItemTags.LECTERN_BOOKS`` / ``ItemTags.BOOKSHELF_BOOKS`` compatibility filtering.
       * ``CustomBookNet443``: 4 payloads (``custom_books``, ``custom_book_query``, ``custom_book_data``,
         ``custom_book_save``) registered inside ``ChestLootNet436#onRegister``.
       * ``LootPoolBookEditorScreen443`` + integration in ``LootPoolEditorScreen440`` (``+ Книга``,
         inline edit button, colored book names) and ``LootPoolItemPickerScreen440`` (``Свои книги`` tab
         and RMB import from inventory).
  2. Lectern & Chiseled Bookshelf full chest-graph support:
       * ``ChestGraph396#isChest`` & ``isChestAt`` recognize ``LecternBlock`` / ``LecternBlockEntity``
         and ``ChiseledBookShelfBlock`` / ``ChiseledBookShelfBlockEntity`` (automatically covering
         wand highlights, anchors, radius triggers, dungeon schematics, and lock protection).
       * ``LootPoolManager436`` resolves ``settledlands:book/<id>`` drops across all containers and
         filters mixed loot pools for lecterns (max 1 book + ``LecternBlock.resetBookState``) and
         chiseled bookshelves (up to 6 books + ``SLOT_OCCUPIED_PROPERTIES`` + ``sendBlockUpdated``).
       * ``ChestGraph396Events`` waits on ``LecternMenu`` close when occupied and uses 2-tick windowless
         completion for chiseled bookshelves and empty lecterns; ``ChestOpen401`` / ``ChestNet397``
         preserve the client's exact targeted bookshelf slot face/coordinates after lock cracking.
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

BASE_VERSION = "0.8.0-build442-rarity-chance-crate-fix"
NEW_VERSION = "0.8.0-build443-custom-books-lectern-bookshelf"
OUT_NAME = "settledlands-0.8.0-build443-custom-books-lectern-bookshelf.jar"

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
    "dev/settledlands/chest/CustomBookNet443.java",
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/chest/ChestLootNet436.java",
    "dev/settledlands/chest/ChestGraph396.java",
    "dev/settledlands/chest/ChestGraph396Events.java",
    "dev/settledlands/chest/ChestNet397.java",
    "dev/settledlands/chest/ChestOpen401.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.java",
    "dev/settledlands/npc/client/LootPoolBookEditorScreen443.java",
)

MARKERS = {
    "dev/settledlands/chest/CustomBook443.class": (
        "settledlands:book/", "parseSpans", "encodeBookWire", "decodeBookWire",
    ),
    "dev/settledlands/chest/CustomBookManager443.class": (
        "buildBookStack", "extractBookFromStack", "resolveItemStack", "isValidForLectern",
    ),
    "dev/settledlands/chest/CustomBookNet443.class": (
        "settledlands", "custom_books", "custom_book_query", "custom_book_save",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": (
        "populateLectern", "syncChiseledShelfState", "filterLecternEntries", "filterBookshelfEntries",
    ),
    "dev/settledlands/chest/ChestGraph396.class": (
        "LecternBlock", "ChiseledBookShelfBlock", "sendBookSummaries",
    ),
    "dev/settledlands/chest/ChestGraph396Events.class": (
        "computeNoWindowTicks", "noWindowTicks",
    ),
    "dev/settledlands/chest/ChestOpen401.class": (
        "captureClientHit",
    ),
    "dev/settledlands/npc/client/LootPoolBookEditorScreen443.class": (
        "renderBookPreview", "importFromHand", "giveBookToSelf",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.editor.add_book": "+ Книга",
    "gui.settledlands.loot_pool.editor.add_book_tip": "Создать или редактировать свою цветную книгу в пул",
    "gui.settledlands.loot_pool.editor.edit_book_tip": "Редактировать эту книгу",
    "gui.settledlands.loot_pool.picker.mode_books": "Свои книги",
    "gui.settledlands.loot_pool.picker.new_book": "+ Новая книга",
    "gui.settledlands.loot_pool.picker.empty_books": "Нет сохранённых книг. Нажмите '+ Новая книга'",
    "gui.settledlands.loot_pool.picker.hint_books": "ЛКМ: выбрать книгу | Shift+ЛКМ: несколько | ПКМ: редактировать",
    "gui.settledlands.loot_pool.picker.hint_inv": "ЛКМ: выбрать предмет | ПКМ по книге: импорт в редактор книг",
    "gui.settledlands.book_editor.title": "Редактор книги",
    "gui.settledlands.book_editor.id_lbl": "ID:",
    "gui.settledlands.book_editor.title_lbl": "Загол.:",
    "gui.settledlands.book_editor.author_lbl": "Автор:",
    "gui.settledlands.book_editor.new_btn": "Новая",
    "gui.settledlands.book_editor.new_tip": "Создать новую книгу",
    "gui.settledlands.book_editor.switch_tip": "Выбрать сохранённую книгу",
    "gui.settledlands.book_editor.delete_tip": "Удалить файл этой книги на сервере (два клика)",
    "gui.settledlands.book_editor.add_page": "+ Стр.",
    "gui.settledlands.book_editor.del_page": "- Стр.",
    "gui.settledlands.book_editor.page_hint": "Текст страницы... Поддерживаются коды &0-&f, &#RRGGBB, &l/&o/&n/&m/&r",
    "gui.settledlands.book_editor.from_hand": "Из руки",
    "gui.settledlands.book_editor.from_hand_tip": "Импортировать книгу (written/writable_book) из руки",
    "gui.settledlands.book_editor.give_self": "Выдать себе",
    "gui.settledlands.book_editor.give_self_tip": "Выдать себе копию этой книги для проверки в игре",
    "gui.settledlands.book_editor.save_to_pool": "Сохр. и в пул",
    "gui.settledlands.book_editor.by_author": "от %s",
    "gui.settledlands.book_editor.none": "(нет книг)",
    "gui.settledlands.book_editor.saved": "Книга сохранена: books/%s.txt",
    "gui.settledlands.book_editor.deleted": "Удалена книга: %s",
    "gui.settledlands.book_editor.given": "Книга выдана в инвентарь!",
    "gui.settledlands.book_editor.imported": "Книга импортирована из инвентаря!",
    "gui.settledlands.book_editor.new_created": "Новая книга: %s (нажмите Сохранить)",
    "gui.settledlands.book_editor.delete_warn": "Ещё раз «Удалить» для удаления %s.txt!",
    "gui.settledlands.book_editor.err_id": "Укажите корректный ID книги (латиница/кириллица/цифры)",
    "gui.settledlands.book_editor.err_no_hand_book": "Возьмите в руку книгу с пером или написанную книгу",
    "gui.settledlands.book_editor.err_hex": "Введите 6 hex-символов цвета (напр. FFAA00)",
    "gui.settledlands.book_editor.hex_tip": "Вставить произвольный цвет &#RRGGBB",
    "gui.settledlands.book_editor.color_tip": "Вставить цвет %s",
    "gui.settledlands.book_editor.fmt_bold": "Жирный (%s)",
    "gui.settledlands.book_editor.fmt_italic": "Курсив (%s)",
    "gui.settledlands.book_editor.fmt_underline": "Подчёркнутый (%s)",
    "gui.settledlands.book_editor.fmt_strike": "Зачёркнутый (%s)",
    "gui.settledlands.book_editor.fmt_reset": "Сброс стиля (%s)",
    "gui.settledlands.book_editor.chars_hint": "Стр.: %s / %s симв. | Клик по цвету вставляет код в Заголовок или Страницу",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.editor.add_book": "+ Book",
    "gui.settledlands.loot_pool.editor.add_book_tip": "Create or edit a custom colored book for this pool",
    "gui.settledlands.loot_pool.editor.edit_book_tip": "Edit this book",
    "gui.settledlands.loot_pool.picker.mode_books": "Custom books",
    "gui.settledlands.loot_pool.picker.new_book": "+ New book",
    "gui.settledlands.loot_pool.picker.empty_books": "No custom books yet. Click '+ New book'",
    "gui.settledlands.loot_pool.picker.hint_books": "LMB: add book | Shift+LMB: multi-add | RMB: edit book",
    "gui.settledlands.loot_pool.picker.hint_inv": "LMB: pick item | RMB on a book: import into book editor",
    "gui.settledlands.book_editor.title": "Book Editor",
    "gui.settledlands.book_editor.id_lbl": "ID:",
    "gui.settledlands.book_editor.title_lbl": "Title:",
    "gui.settledlands.book_editor.author_lbl": "Author:",
    "gui.settledlands.book_editor.new_btn": "New",
    "gui.settledlands.book_editor.new_tip": "Create a new book",
    "gui.settledlands.book_editor.switch_tip": "Switch to an existing saved book",
    "gui.settledlands.book_editor.delete_tip": "Delete this book file on the server (double-click)",
    "gui.settledlands.book_editor.add_page": "+ Page",
    "gui.settledlands.book_editor.del_page": "- Page",
    "gui.settledlands.book_editor.page_hint": "Page text... Supports &0-&f, &#RRGGBB, &l/&o/&n/&m/&r codes",
    "gui.settledlands.book_editor.from_hand": "From hand",
    "gui.settledlands.book_editor.from_hand_tip": "Import a written or writable book from your hand",
    "gui.settledlands.book_editor.give_self": "Give self",
    "gui.settledlands.book_editor.give_self_tip": "Give yourself a copy of this book to test in-game",
    "gui.settledlands.book_editor.save_to_pool": "Save & Add",
    "gui.settledlands.book_editor.by_author": "by %s",
    "gui.settledlands.book_editor.none": "(no books)",
    "gui.settledlands.book_editor.saved": "Saved book: books/%s.txt",
    "gui.settledlands.book_editor.deleted": "Deleted book: %s",
    "gui.settledlands.book_editor.given": "Book added to your inventory!",
    "gui.settledlands.book_editor.imported": "Book imported from inventory!",
    "gui.settledlands.book_editor.new_created": "New book: %s (click Save)",
    "gui.settledlands.book_editor.delete_warn": "Click Delete again to remove %s.txt!",
    "gui.settledlands.book_editor.err_id": "Enter a valid book ID",
    "gui.settledlands.book_editor.err_no_hand_book": "Hold a Written Book or Book and Quill in your hand",
    "gui.settledlands.book_editor.err_hex": "Enter 6 hex digits (e.g. FFAA00)",
    "gui.settledlands.book_editor.hex_tip": "Insert custom &#RRGGBB hex color",
    "gui.settledlands.book_editor.color_tip": "Insert color %s",
    "gui.settledlands.book_editor.fmt_bold": "Bold (%s)",
    "gui.settledlands.book_editor.fmt_italic": "Italic (%s)",
    "gui.settledlands.book_editor.fmt_underline": "Underline (%s)",
    "gui.settledlands.book_editor.fmt_strike": "Strikethrough (%s)",
    "gui.settledlands.book_editor.fmt_reset": "Reset formatting (%s)",
    "gui.settledlands.book_editor.chars_hint": "Page: %s / %s chars | Palette inserts codes into focused Title or Page",
}

CHANGELOG_EN = (
    "Build 443 adds an in-game Custom Colored Book Editor (config/settledlands/books/<id>.txt, "
    "referenced as settledlands:book/<id>) with &-codes/hex colors, live book preview, and hand/inventory "
    "import, plus full Chest Graph and Loot Pool support for vanilla Lecterns and Chiseled Bookshelves."
)
PATCHINFO = (
    "Сборка 443: кастомные цветные книги в пулах лута + кафедры и резные книжные полки в графах сундуков.\n"
    "  * Добавлены кастомные книги config/settledlands/books/<id>.txt (ID в пулах: settledlands:book/<id>)\n"
    "    с поддержкой 16 цветов &0-&f, &#RRGGBB hex и стилей &l/&o/&n/&m/&r в названии и страницах;\n"
    "  * Добавлен встроенный «Редактор книги» (+ Книга в редакторе пула, вкладка «Свои книги» в каталоге,\n"
    "    кнопка «Из руки» / ПКМ по книге из инвентаря, «Выдать себе» и живой пергаментный предпросмотр);\n"
    "  * Кафедры (minecraft:lectern) и резные книжные полки (minecraft:chiseled_bookshelf) полностью\n"
    "    поддерживаются графами сундуков: палочка, замок, взлом (с точным слотом полки), якоря и\n"
    "    генерация лута в данжах (смешанные пулы автоматически фильтруются под книги).\n"
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


def compile_build443(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build443 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out443",
                    help="directory for compiled build 443 classes")
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
        print("compiling Build 443 sources via ECJ (-21)...")
        compile_build443(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    # Remove stale inner classes of any replaced outer class if the set changed, then add/replace all
    # compiled classes cleanly.
    compiled_map = {}
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        compiled_map[rel] = compiled.read_bytes()

    for rel, data in sorted(compiled_map.items()):
        for marker in MARKERS.get(rel, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (rel, marker))
        if rel in blobs:
            print("  replaced: %s (%d bytes)" % (rel, len(data)))
            blobs[rel] = data
        else:
            print("  added   : %s (%d bytes)" % (rel, len(data)))
            order.append(rel)
            blobs[rel] = data

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (+%d keys)" % (lp, len(entries)))

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
