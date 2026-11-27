"""Build 450: Universal min-max range inputs (`a-b` or single numbers) across Trial Spawners,
Mob/Reward Pools, and stock Dialogue/Block/Door/Chest Graph Nodes; unified single-field
min-max rows (Spawn Delay, Reward Count, Random outputs, Button Relations/Points); and a
multi-row compact 3D Mob Catalog fitting multiple rows on every GUI scale."""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent

BASE_VERSION = "0.8.0-build449-named-trial-pools-and-3d-mob-catalog"
NEW_VERSION = "0.8.0-build450-universal-ranges-and-compact-mob-catalog"
OUT_NAME = "settledlands-0.8.0-build450-universal-ranges-and-compact-mob-catalog.jar"

STUB_LAYERS = [
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
    ROOT / "build303-src" / "stubs446-src",
    ROOT / "build303-src" / "stubs449-src",
]

SOURCE_FILES = [
    "dev/settledlands/trial/RangeSpec450.java",
    "dev/settledlands/trial/TrialConfig446.java",
    "dev/settledlands/trial/TrialMobPool449.java",
    "dev/settledlands/trial/TrialModule447.java",
    "dev/settledlands/trial/TrialSpawnerBridge446.java",
    "dev/settledlands/trial/TrialControlActions446.java",
    "dev/settledlands/trial/TrialGraph446.java",
    "dev/settledlands/npc/GraphRanges450.java",
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.java",
    "dev/settledlands/npc/client/TrialMobPoolEditorScreen449.java",
    "dev/settledlands/npc/client/TrialModuleClient447.java",
    "dev/settledlands/npc/client/TrialModuleCard447.java",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.java",
    "dev/settledlands/npc/client/GraphRangeUi450.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.java",
    "dev/settledlands/mixin/GraphRangeInputs450Mixin.java",
    "dev/settledlands/mixin/GuideRange450Mixin.java",
    "dev/settledlands/mixin/TriggerRange450Mixin.java",
    "dev/settledlands/mixin/RadiusRange450Mixin.java",
]

MARKERS = {
    "dev/settledlands/trial/RangeSpec450.class": ("isIntRangeInput", "isFloatRangeInput"),
    "dev/settledlands/trial/TrialConfig446.class": ("toResolvedJson", "hasRanges"),
    "dev/settledlands/trial/TrialMobPool449.class": ("weightRange", "parseWeightRangeInput"),
    "dev/settledlands/npc/GraphRanges450.class": ("rs450:", "rerollNode"),
    "dev/settledlands/npc/client/TrialMobCatalogScreen449.class": ("enableScissor", "drawScaledText"),
    "dev/settledlands/npc/client/GraphRangeUi450.class": ("gui.settledlands.graph.relation_range",),
    "dev/settledlands/mixin/GraphRangeInputs450Mixin.class": ("settledlands$applyRangesUi450",),
    "dev/settledlands/mixin/GuideRange450Mixin.class": ("settledlands$evalIfRange450",),
    "dev/settledlands/mixin/TriggerRange450Mixin.class": ("settledlands$rerollWalk450",),
    "dev/settledlands/mixin/RadiusRange450Mixin.class": ("settledlands$rerollEventFired450",),
}

COMMON_MIXINS_450 = [
    "GuideRange450Mixin",
    "TriggerRange450Mixin",
    "RadiusRange450Mixin",
]

CLIENT_MIXINS_450 = [
    "GraphRangeInputs450Mixin",
]

LANG_SET_RU = {
    # Unified Trial & Graph range labels
    "gui.settledlands.trial.cfg.delay": "Задержка спавна (тики)",
    "gui.settledlands.graph.relation_range": "Отношения (мин-макс)",
    "gui.settledlands.graph.points_range": "Очки (мин-макс)",

    # New & updated >2s hover tooltips explaining range inputs ("5-15" or "10")
    "gui.settledlands.tip448.tf.delay":
        "Задержка между пачками мобов в тиках (20 т = 1 сек). Можно ввести одно число (30) или диапазон мин-макс (20-40).",
    "gui.settledlands.tip448.cfg.rew_count":
        "Количество штук при выпадении (1–64). Можно ввести одно число (2) или диапазон мин-макс (1-3).",

    "gui.settledlands.tip448.tf.act_range":
        "Радиус активации (1–64 бл.): можно задать одно число (14) или диапазон мин-макс (10-18) для переролла на волну.",
    "gui.settledlands.tip448.tf.spawn_range":
        "Радиус спавна (1–16 бл.): одно число (4) или диапазон мин-макс (3-7).",
    "gui.settledlands.tip448.tf.total_mobs":
        "Базовое число мобов за волну (1–256): одно число (6) или диапазон мин-макс (4-10) для случайного выбора на волну.",
    "gui.settledlands.tip448.tf.per_player_total":
        "Бонус мобов за игрока (0–64): одно число или диапазон (например 2-4).",
    "gui.settledlands.tip448.tf.sim_mobs":
        "Макс. одновременно на арене (1–64): одно число (3) или диапазон мин-макс (2-5).",
    "gui.settledlands.tip448.tf.per_player_sim":
        "Лимит одновременно на игрока (0–32): одно число или диапазон мин-макс (1-3).",
    "gui.settledlands.tip448.tf.batch":
        "Размер пачки спавна (1–8): одно число (2) или диапазон мин-макс (1-3).",
    "gui.settledlands.tip448.tf.cooldown":
        "Перезарядка спавнера в тиках (36000 = 30 мин): одно число или диапазон мин-макс (12000-36000).",
    "gui.settledlands.tip448.tf.ominous_mult":
        "Множитель зловещего режима (1.0–4.0): одно число (1.50) или диапазон мин-макс (1.2-2.0).",
    "gui.settledlands.tip448.tf.ominous_interval":
        "Интервал зловещих угроз в тиках (160 т = 8 сек): одно число или диапазон мин-макс (120-240).",
    "gui.settledlands.tip448.tf.nobreak_r":
        "Радиус защиты от ломания (1–64 бл.): одно число или диапазон мин-макс.",
    "gui.settledlands.tip448.tf.nobreak_s":
        "Длительность защиты (сек): одно число или диапазон мин-макс.",
    "gui.settledlands.tip448.tf.nobreak_amp":
        "Уровень утомления (0–3): одно число или диапазон мин-макс.",
    "gui.settledlands.tip448.tf.destruct_waves":
        "Волн до самосноса (1–100): одно число или диапазон мин-макс (2-5).",
    "gui.settledlands.tip448.tf.destruct_chance":
        "Шанс самосноса (0–100%): одно число или диапазон мин-макс (25-75).",

    "gui.settledlands.tip448.mod.weight_mob":
        "Относительный вес появления этого моба. Можно ввести одно число (10) или диапазон (5-15).",
    "gui.settledlands.tip448.cfg.rew_weight":
        "Вес предмета в пуле наград. Поддерживается одно число (10) или диапазон (5-20).",
    "gui.settledlands.tip448.cfg.rew_chance":
        "Процентный шанс выпадения (0–100%). Поддерживается одно число (100) или диапазон (40-80).",

    "gui.settledlands.tip448.fld.relation_range":
        "Требуемый порог отношения для показа кнопки: одно число (20) или диапазон мин-макс (10-50).",
    "gui.settledlands.tip448.fld.points_range":
        "Требуемый порог выбранных очков для показа кнопки: одно число (10) или диапазон мин-макс (5-25).",
    "gui.settledlands.tip448.fld.random_range":
        "Единый диапазон ветвлений (1–64): введите одно число (3) или мин-макс (1-4).",
    "gui.settledlands.tip448.fld.var_delta":
        "Значение или изменение переменной: одно число (5 / -2) или случайный диапазон (1-5).",
    "gui.settledlands.tip448.fld.var_compare":
        "Эталон сравнения If: одно число (10) или диапазон (5-15 — при == проверяет попадание в интервал).",
    "gui.settledlands.tip448.fld.check_radius":
        "Радиус обнаружения (1–64 бл.): одно число или диапазон мин-макс (4-8).",
    "gui.settledlands.tip448.fld.check_cooldown":
        "Пауза между срабатываниями (сек): одно число или диапазон мин-макс (5-15).",
    "gui.settledlands.tip448.fld.min_damage":
        "Минимальный порог урона за удар: одно число или диапазон мин-макс (2-6).",
    "gui.settledlands.tip448.fld.time_ticks":
        "Тик игровых суток (0–23999): одно число или диапазон мин-макс (1000-6000).",
    "gui.settledlands.tip448.fld.timer_interval":
        "Интервал таймера (сек): одно число или диапазон мин-макс (5-15 — переролл на каждый цикл).",
    "gui.settledlands.tip448.fld.radius":
        "Радиус поиска цели (1–64 бл.): одно число или диапазон мин-макс (4-10).",
}

LANG_SET_EN = {
    "gui.settledlands.trial.cfg.delay": "Spawn Delay (ticks)",
    "gui.settledlands.graph.relation_range": "Relations (min-max)",
    "gui.settledlands.graph.points_range": "Points (min-max)",

    "gui.settledlands.tip448.tf.delay":
        "Delay between mob spawn bursts in ticks (20t = 1s). Enter a single number (30) or min-max range (20-40).",
    "gui.settledlands.tip448.cfg.rew_count":
        "Stack size rolled for this reward (1-64). Enter a single number (2) or min-max range (1-3).",

    "gui.settledlands.tip448.tf.act_range":
        "Activation range (1-64 blocks): single number (14) or min-max range (10-18) rolled per wave.",
    "gui.settledlands.tip448.tf.spawn_range":
        "Spawn radius (1-16 blocks): single number (4) or min-max range (3-7).",
    "gui.settledlands.tip448.tf.total_mobs":
        "Base mobs per wave (1-256): single number (6) or min-max range (4-10) rolled each wave.",
    "gui.settledlands.tip448.tf.per_player_total":
        "Extra mobs per player (0-64): single number or min-max range (e.g. 2-4).",
    "gui.settledlands.tip448.tf.sim_mobs":
        "Max simultaneous mobs (1-64): single number (3) or min-max range (2-5).",
    "gui.settledlands.tip448.tf.per_player_sim":
        "Extra simultaneous cap per player (0-32): single number or min-max range (1-3).",
    "gui.settledlands.tip448.tf.batch":
        "Spawn burst batch size (1-8): single number (2) or min-max range (1-3).",
    "gui.settledlands.tip448.tf.cooldown":
        "Spawner cooldown in ticks (36000 = 30m): single number or min-max range (12000-36000).",
    "gui.settledlands.tip448.tf.ominous_mult":
        "Ominous power multiplier (1.0-4.0): single number (1.50) or min-max range (1.2-2.0).",
    "gui.settledlands.tip448.tf.ominous_interval":
        "Ominous hazard interval in ticks (160t = 8s): single number or min-max range (120-240).",
    "gui.settledlands.tip448.tf.nobreak_r":
        "Protection radius (1-64 blocks): single number or min-max range.",
    "gui.settledlands.tip448.tf.nobreak_s":
        "Protection duration (sec): single number or min-max range.",
    "gui.settledlands.tip448.tf.nobreak_amp":
        "Mining Fatigue level (0-3): single number or min-max range.",
    "gui.settledlands.tip448.tf.destruct_waves":
        "Waves before self-destruct (1-100): single number or min-max range (2-5).",
    "gui.settledlands.tip448.tf.destruct_chance":
        "Self-destruct chance (0-100%): single number or min-max range (25-75).",

    "gui.settledlands.tip448.mod.weight_mob":
        "Relative spawn weight. Accepts a single number (10) or min-max range (5-15).",
    "gui.settledlands.tip448.cfg.rew_weight":
        "Relative pool selection weight. Accepts a single number (10) or min-max range (5-20).",
    "gui.settledlands.tip448.cfg.rew_chance":
        "Percentage drop chance (0-100%). Accepts a single number (100) or min-max range (40-80).",

    "gui.settledlands.tip448.fld.relation_range":
        "Relation threshold required to show this button: single number (20) or min-max range (10-50).",
    "gui.settledlands.tip448.fld.points_range":
        "Points threshold required to show this button: single number (10) or min-max range (5-25).",
    "gui.settledlands.tip448.fld.random_range":
        "Unified random output range (1-64): enter a single number (3) or min-max range (1-4).",
    "gui.settledlands.tip448.fld.var_delta":
        "Variable value/delta: single number (5 / -2) or random min-max range (1-5).",
    "gui.settledlands.tip448.fld.var_compare":
        "If comparison target: single number (10) or range (5-15 checks interval membership for ==).",
    "gui.settledlands.tip448.fld.check_radius":
        "Detection radius (1-64 blocks): single number or min-max range (4-8).",
    "gui.settledlands.tip448.fld.check_cooldown":
        "Cooldown between triggers (sec): single number or min-max range (5-15).",
    "gui.settledlands.tip448.fld.min_damage":
        "Minimum hit damage required: single number or min-max range (2-6).",
    "gui.settledlands.tip448.fld.time_ticks":
        "Minecraft daytime tick (0-23999): single number or min-max range (1000-6000).",
    "gui.settledlands.tip448.fld.timer_interval":
        "Timer delay/interval (sec): single number or min-max range (5-15 rolled per cycle).",
    "gui.settledlands.tip448.fld.radius":
        "Target search radius (1-64 blocks): single number or min-max range (4-10).",
}

CHANGELOG_EN = (
    "Build 450: Universal min-max ranges (a-b or single number) across Trial Spawner settings, "
    "Mob & Reward Pools, and stock Dialogue/Block/Door/Chest graph nodes; unified min-max fields "
    "(Spawn Delay, Reward Count, Random range, Button Relation/Points ranges); and compact "
    "multi-row 3D Mob Catalog cards with full scissor clipping."
)

PATCHINFO = (
    "Settled Lands Build 450 — Universal Ranges, Unified Inputs & Compact 3D Mob Catalog\n"
    "Base: 0.8.0-build449-named-trial-pools-and-3d-mob-catalog\n"
)


def verify_lang_coverage() -> None:
    missing_ru = sorted(k for k in LANG_SET_EN if k not in LANG_SET_RU)
    missing_en = sorted(k for k in LANG_SET_RU if k not in LANG_SET_EN)
    if missing_ru or missing_en:
        raise SystemExit(f"lang parity error: missing_ru={missing_ru}, missing_en={missing_en}")


def find_java() -> str:
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


def compile_build450(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build450 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_mixins_json(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    mixins = doc.get("mixins", [])
    for m in COMMON_MIXINS_450:
        if m not in mixins:
            mixins.append(m)
    doc["mixins"] = mixins

    client = doc.get("client", [])
    if not client or client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    if "GraphHover448Mixin" not in client:
        raise SystemExit("GraphHover448Mixin missing from client mixins!")
    for m in CLIENT_MIXINS_450:
        if m in client:
            client.remove(m)
    idx = client.index("GraphHover448Mixin")
    for offset, m in enumerate(CLIENT_MIXINS_450):
        client.insert(idx + offset, m)
    if client[-1] != "GraphHover448Mixin":
        raise SystemExit("GraphHover448Mixin must remain last in client mixins!")
    doc["client"] = client
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    verify_lang_coverage()

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out450",
                    help="directory for compiled build 450 classes")
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
        print("compiling Build 450 sources via ECJ (-21)...")
        compile_build450(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    outer_stems = [rel[:-5] for rel in SOURCE_FILES]
    stale_removed = set()
    new_order = []
    for name in order:
        if any(name == stem + ".class" or name.startswith(stem + "$") for stem in outer_stems):
            stale_removed.add(name)
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

    blobs["settledlands.mixins.json"] = patch_mixins_json(blobs["settledlands.mixins.json"])
    print("  updated : settledlands.mixins.json")

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
