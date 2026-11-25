#!/usr/bin/env python3
"""
Build 441 - Loot Pool Editor Full-Height Layout, Single Count Range Field,
CS:GO-Style Rarity Chances + Crate Weight Limit, and Silent GuideTriggerNpc Anchor.

Builds on Build 440 (``settledlands-0.8.0-build440-loot-pool-editor-gui.jar``):
  1. Full-height editor & item picker layout:
       * ``LootPoolEditorScreen440`` expands cleanly to full viewport height (``height - 16``)
         with the item table frame extending directly down to the bottom action bar whenever no
         temporary status/test-roll strip is open (and collapsing smoothly by one row only while
         showing roll preview / status, dismissible by click).
  2. Unified item quantity range field:
       * Replaces separate min/max boxes with one ``Кол-во`` / ``Count`` box accepting both
         single counts (``1``) and ``min-max`` ranges (``2-6``) parsed by
         ``LootPool436.parseItemCountRange``.
  3. Rarity as drop chance (CS:GO case style) & Weight as crate capacity limiter:
       * ``LootPool436.rollDrops`` selects items using ``entry.rarity`` (allowing whole and decimal
         values such as ``79.92`` / ``0.26``), while ``entry.weight`` consumes the chest/crate's
         total weight capacity (e.g. crate weight ``40`` fits 2 swords of weight ``20`` or 4 books
         of weight ``10``). Blank ``rolls`` with a crate weight limit fills until weight runs out.
       * Editor live ``%`` column reflects ``rarity / totalRarity * 100%`` and inspector labels
         clarify rarity cap vs crate weight limit.
  4. Silent node anchor / trigger markers:
       * ``GuideTriggerSilentMixin441`` silences ``settledlands:guide_trigger``
         (``GuideTriggerNpc``) by returning ``null`` for ambient/hurt/death sounds and ``0.0F``
         for sound volume, stopping all villager noises from graph anchors and trigger entities.
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

BASE_VERSION = "0.8.0-build440-loot-pool-editor-gui"
NEW_VERSION = "0.8.0-build441-rarity-crate-weight-and-silent-anchor"
OUT_NAME = "settledlands-0.8.0-build441-rarity-crate-weight-and-silent-anchor.jar"

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
)

SOURCE_FILES = (
    "dev/settledlands/chest/LootPool436.java",
    "dev/settledlands/chest/LootPoolManager436.java",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.java",
    "dev/settledlands/mixin/GuideTriggerSilentMixin441.java",
)

INNER_SYNC = (
    "dev/settledlands/chest/LootPool436$Parsed.class",
    "dev/settledlands/chest/LootPool436$Range.class",
    "dev/settledlands/chest/LootPool436$PoolEntry.class",
    "dev/settledlands/chest/LootPool436$Drop.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowData.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowSlot.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$PreviewDrop.class",
    "dev/settledlands/npc/client/LootPoolCard436$TrackedDropdown.class",
)

REPLACED = (
    "dev/settledlands/chest/LootPool436.class",
    "dev/settledlands/chest/LootPoolManager436.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class",
    "dev/settledlands/npc/client/LootPoolCard436.class",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
)

ADDED = (
    "dev/settledlands/mixin/GuideTriggerSilentMixin441.class",
)

MARKERS = {
    "dev/settledlands/chest/LootPool436.class": (
        "parseItemCountRange", "formatRarity", "parseRarityInput",
    ),
    "dev/settledlands/chest/LootPoolManager436.class": (
        "rarity_chance", "crate capacity weight",
    ),
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class": (
        "isCountRangeInput", "fullVisibleRows", "compactVisibleRows",
    ),
    "dev/settledlands/npc/client/LootPoolItemPickerScreen440.class": (
        "LootPoolItemPickerScreen440",
    ),
    "dev/settledlands/npc/client/LootPoolCard436.class": (
        "by_weight",
    ),
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class": (
        "gearButton", "trackPoolDropdown",
    ),
    "dev/settledlands/mixin/GuideTriggerSilentMixin441.class": (
        "getAmbientSound", "getHurtSound", "getDeathSound", "getSoundVolume",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.limits_hint": "Лимиты (пусто = без огр.; напр. 40 или 20-60)",
    "gui.settledlands.loot_pool.max_rarity": "Лимит редкости (опц.)",
    "gui.settledlands.loot_pool.max_weight": "Вес ящика (вместимость)",
    "gui.settledlands.loot_pool.rolls": "Макс. шт. (пусто=по весу)",
    "gui.settledlands.loot_pool.card_limits": "Редк<=%s Вес ящ:%s",
    "gui.settledlands.loot_pool.editor.summary": "Записей: %s | Сумм. редк.: %s",
    "gui.settledlands.loot_pool.editor.node_caps": "Узел: Редк.<=%s  Вес ящ.:%s  Шт: %s",
    "gui.settledlands.loot_pool.editor.by_weight": "по весу",
    "gui.settledlands.loot_pool.editor.err_row": "Проверьте предмет, кол-во (1-64), редкость (>0) и вес (>0) в строке %s",
    "gui.settledlands.loot_pool.editor.filtered_tip": "Превышает лимит редкости или вместимость ящика этого узла",
    "gui.settledlands.loot_pool.editor.preview_filtered": "Ничего не выпало (проверьте лимит редкости и вес ящика в узле)",
    "gui.settledlands.loot_pool.editor.tip_count": "Количество за 1 выпадение: число или диапазон через - (напр. 1 или 2-6)",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Шанс выпадения предмета (как в кейсах: % = Редк. / Сумма редкостей)",
    "gui.settledlands.loot_pool.editor.tip_weight": "Сколько веса предмета тратится из вместимости ящика (напр. 20 при ящике 40)",
    "gui.settledlands.loot_pool.editor.tip_chance": "Расчётный процентный шанс выпадения из этого пула",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.limits_hint": "Crate limits (blank = no limit; e.g. 40 or 20-60)",
    "gui.settledlands.loot_pool.max_rarity": "Rarity cap (optional)",
    "gui.settledlands.loot_pool.max_weight": "Crate weight limit",
    "gui.settledlands.loot_pool.rolls": "Max items (blank=by weight)",
    "gui.settledlands.loot_pool.card_limits": "Rar<=%s Crate:%s",
    "gui.settledlands.loot_pool.editor.summary": "Entries: %s | Total rarity: %s",
    "gui.settledlands.loot_pool.editor.node_caps": "Node: Rar<=%s  Crate:%s  Max: %s",
    "gui.settledlands.loot_pool.editor.by_weight": "by weight",
    "gui.settledlands.loot_pool.editor.err_row": "Check item, count (1-64), rarity (>0), and weight (>0) on row %s",
    "gui.settledlands.loot_pool.editor.filtered_tip": "Exceeds this node's rarity cap or crate weight capacity",
    "gui.settledlands.loot_pool.editor.preview_filtered": "No drops (check node rarity cap and crate weight capacity)",
    "gui.settledlands.loot_pool.editor.tip_count": "Drop stack size: single number or min-max with - (e.g. 1 or 2-6)",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Drop chance weight (CS:GO case style: % = Rarity / Sum of rarities)",
    "gui.settledlands.loot_pool.editor.tip_weight": "Crate capacity weight consumed per drop (e.g. 20 inside a 40-weight crate)",
    "gui.settledlands.loot_pool.editor.tip_chance": "Computed percentage drop chance in this pool",
}

CHANGELOG_EN = (
    "Build 441 expands the Loot Pool Editor to full screen height down to the bottom buttons, "
    "combines item count into a single min-max field with '-', uses Rarity to drive CS:GO-style "
    "percentage drop chances while Weight limits total crate capacity, and silences GuideTriggerNpc "
    "anchors so they no longer emit villager sounds."
)
PATCHINFO = (
    "Сборка 441: доработки редактора пулов предметов, новой логики редкости/веса и тихий якорь узлов.\n"
    "  * Список предметов в редакторе пула растянут до самого низа экрана (вплотную к кнопкам);\n"
    "  * Поле «Кол-во» объединено в одно текстовое поле с поддержкой диапазона через «-» (напр. 2-6);\n"
    "  * Поле «Редк.» теперь задаёт процентный шанс выпадения предмета (по типу кейсов CS:GO,\n"
    "    поддерживаются и целые, и дробные числа вроде 0.26), а «Вес» выступает ограничителем\n"
    "    вместимости ящика (напр. ящик весом 40 вмещает 2 меча весом 20 или 4 книги весом 10);\n"
    "  * Невидимые маркеры-якоря и триггеры (settledlands:guide_trigger) полностью заглушены\n"
    "    через GuideTriggerSilentMixin441 и больше не издают звуки жителя.\n"
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


def compile_build441(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build441 compile failed:\n" + r2.stdout + r2.stderr)


def patch_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    common = list(doc.get("mixins") or [])
    if "GuideTriggerSilentMixin441" not in common:
        common.append("GuideTriggerSilentMixin441")
    doc["mixins"] = common
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out441",
                    help="directory for compiled build 441 classes")
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
        print("compiling Build 441 sources via ECJ (-21)...")
        compile_build441(base, srcroot)
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

    mixins_name = "settledlands.mixins.json"
    if mixins_name not in blobs:
        raise SystemExit("base jar missing %s" % mixins_name)
    blobs[mixins_name] = patch_mixins(blobs[mixins_name])
    print("  updated : %s (added common GuideTriggerSilentMixin441)" % mixins_name)

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


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
