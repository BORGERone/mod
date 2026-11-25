#!/usr/bin/env python3
"""
Build 442 - Fix Low-Rarity Spike in Weight-Capped Crates, Exact Effective Chances & Inline Node Caps.

Builds on Build 441 (``settledlands-0.8.0-build441-rarity-crate-weight-and-silent-anchor.jar``):
  1. Honest rarity roulette under crate weight capacity (``LootPool436.rollDrops``):
       * The roulette draws from entries that fit the crate's initial capacity (``initialWeight``)
         rather than shrinking candidates to ``remainingWeight``. Low-rarity filler items (e.g.
         rarity 1, weight 1) can no longer jump to 100% chance to fill leftover weight after a
         heavy drop (such as diamond weight 90 in a 100-weight crate).
       * When a chosen drop exceeds remaining capacity, by-weight mode (blank ``rolls``) stops
         cleanly while fixed-roll mode skips that roll without distorting ratios.
  2. Exact effective drop probabilities in editor (``LootPool436.computeEffectiveChancesPct`` +
     ``LootPoolEditorScreen440``):
       * Chance column computes the true expected per-roll drop percentage taking into account
         node limits (``rawRarity``, ``rawWeight`` ranges): items exceeding crate limits show
         ``0% !`` with an explicit reason tooltip, and skewed items show their actual chance ``*``
         with a comparison tooltip instead of misleading raw pool ratios.
  3. Inline editable node limits in the Loot Pool Editor:
       * When opened from a graph node, the editor exposes compact editable fields for node
         ``Редк.<=``, ``Вес ящ.``, and ``Шт`` directly in the header plus a clear warning banner
         whenever node caps block items in the pool.
       * Inspector hints for rarity/weight caps default cleanly to ``любая``/``any`` (no limit).
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

BASE_VERSION = "0.8.0-build441-rarity-crate-weight-and-silent-anchor"
NEW_VERSION = "0.8.0-build442-rarity-chance-crate-fix"
OUT_NAME = "settledlands-0.8.0-build442-rarity-chance-crate-fix.jar"

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
    "dev/settledlands/npc/client/LootPoolEditorScreen440.java",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.java",
)

INNER_SYNC = (
    "dev/settledlands/chest/LootPool436$Parsed.class",
    "dev/settledlands/chest/LootPool436$Range.class",
    "dev/settledlands/chest/LootPool436$PoolEntry.class",
    "dev/settledlands/chest/LootPool436$Drop.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowData.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$RowSlot.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440$PreviewDrop.class",
)

REPLACED = (
    "dev/settledlands/chest/LootPool436.class",
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class",
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class",
)

MARKERS = {
    "dev/settledlands/chest/LootPool436.class": (
        "computeEffectiveChancesPct", "blockedReason", "countBlockedBySpec",
    ),
    "dev/settledlands/npc/client/LootPoolEditorScreen440.class": (
        "nodeRarBox", "nodeWgtBox", "nodeRollsBox", "renderNodeLimitsBar",
    ),
    "dev/settledlands/mixin/LootPoolInspectorMixin436.class": (
        "gui.settledlands.loot_pool.any",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.loot_pool.max_rarity": "Потолок редк. (опц.)",
    "gui.settledlands.loot_pool.max_weight": "Вес ящика (пусто=без лим.)",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Шанс выпадения: чем больше число, тем чаще падает (как в кейсах CS:GO)",
    "gui.settledlands.loot_pool.editor.tip_chance": "Реальный шанс на броске (учитывает Редкость и лимиты узла Вес ящ. / Редк.<=)",
    "gui.settledlands.loot_pool.editor.node_prefix": "Узел:",
    "gui.settledlands.loot_pool.editor.node_rar_lbl": "Редк.<=",
    "gui.settledlands.loot_pool.editor.node_wgt_lbl": "Вес ящ.:",
    "gui.settledlands.loot_pool.editor.node_rls_lbl": "Шт:",
    "gui.settledlands.loot_pool.editor.tip_node_rar": "Потолок редкости для этого узла (пусто = без ограничений)",
    "gui.settledlands.loot_pool.editor.tip_node_wgt": "Вместимость ящика по весу (пусто = без лимита). Предметы тяжелее НЕ выпадут!",
    "gui.settledlands.loot_pool.editor.tip_node_rls": "Макс. число дропов в ящик (пусто = пока хватает веса ящика)",
    "gui.settledlands.loot_pool.editor.blocked_banner": "! Отсечено узлом: %s/%s",
    "gui.settledlands.loot_pool.editor.blocked_banner_tip": "Лимиты узла (Вес ящ. / Редк.<=) блокируют часть предметов — очистите их или увеличьте",
    "gui.settledlands.loot_pool.editor.blocked_weight_tip": "Отсечён: вес %s > макс. Вес ящ. (%s) -> шанс 0%%",
    "gui.settledlands.loot_pool.editor.blocked_rarity_tip": "Отсечён: редк. %s > макс. редк. узла (%s) -> шанс 0%%",
    "gui.settledlands.loot_pool.editor.skewed_chance_tip": "С учётом узла: %s (чистый шанс по пулу: %s)",
    "gui.settledlands.loot_pool.editor.preview_blocked_inline": "(! %s отсеч.)",
}

LANG_SET_EN = {
    "gui.settledlands.loot_pool.max_rarity": "Rarity cap (optional)",
    "gui.settledlands.loot_pool.max_weight": "Crate weight (blank=no limit)",
    "gui.settledlands.loot_pool.editor.tip_rarity": "Drop chance weight: higher number drops MORE often (CS:GO case style)",
    "gui.settledlands.loot_pool.editor.tip_chance": "Effective drop chance per roll (accounts for Rarity plus node Crate weight / Rarity cap)",
    "gui.settledlands.loot_pool.editor.node_prefix": "Node:",
    "gui.settledlands.loot_pool.editor.node_rar_lbl": "Rar<=",
    "gui.settledlands.loot_pool.editor.node_wgt_lbl": "Crate:",
    "gui.settledlands.loot_pool.editor.node_rls_lbl": "Max:",
    "gui.settledlands.loot_pool.editor.tip_node_rar": "Optional upper rarity cap on this node (blank = any)",
    "gui.settledlands.loot_pool.editor.tip_node_wgt": "Crate weight capacity limit (blank = no limit). Items heavier than this NEVER drop!",
    "gui.settledlands.loot_pool.editor.tip_node_rls": "Max drops per crate (blank = fill by crate weight)",
    "gui.settledlands.loot_pool.editor.blocked_banner": "! Node blocks %s/%s",
    "gui.settledlands.loot_pool.editor.blocked_banner_tip": "Node limits (Crate weight / Rarity cap) block some items — clear or raise the limits above",
    "gui.settledlands.loot_pool.editor.blocked_weight_tip": "Blocked: weight %s > max Crate weight (%s) -> 0%% chance",
    "gui.settledlands.loot_pool.editor.blocked_rarity_tip": "Blocked: rarity %s > max node rarity (%s) -> 0%% chance",
    "gui.settledlands.loot_pool.editor.skewed_chance_tip": "With node limits: %s (raw pool share: %s)",
    "gui.settledlands.loot_pool.editor.preview_blocked_inline": "(! %s blocked)",
}

CHANGELOG_EN = (
    "Build 442 fixes low-rarity filler items spiking to 100% chance in weight-capped crates, "
    "calculates exact effective drop percentages under active node limits (marking blocked items as "
    "0% ! with clear tooltips), and adds inline editable node caps directly in the Loot Pool Editor."
)
PATCHINFO = (
    "Сборка 442: исправление шансов при ограничении веса ящика, честный расчёт колонки Шанс и поля узла в редакторе.\n"
    "  * Исправлен перекос вероятности: лёгкие предметы с низкой редкостью (напр. редкость 1) больше не\n"
    "    подскакивают до 100% шанса при заполнении остатка веса ящика после тяжёлых предметов;\n"
    "  * Колонка «Шанс» показывает реальный процент с учётом ограничений узла (Вес ящ. и Редк.<=):\n"
    "    непроходящие предметы помечаются как «0% !» с причиной, а искажённые шансы — с подсказкой;\n"
    "  * Прямо в шапке редактора добавлены редактируемые поля узла (Редк.<=, Вес ящ., Шт) и заметное\n"
    "    предупреждение, если лимиты узла отсекают часть предметов пула.\n"
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


def compile_build442(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build442 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out442",
                    help="directory for compiled build 442 classes")
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
        print("compiling Build 442 sources via ECJ (-21)...")
        compile_build442(base, srcroot)
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

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

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
