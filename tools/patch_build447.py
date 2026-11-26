"""Build 447: modular Trial Spawner graph nodes layered cleanly atop Build 446."""

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

BASE_VERSION = "0.8.0-build446-auto-cleanup-and-trial-spawner"
NEW_VERSION = "0.8.0-build447-modular-trial-spawner-graph"
OUT_NAME = "settledlands-0.8.0-build447-modular-trial-spawner-graph.jar"

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
]

SOURCE_FILES = [
    "dev/settledlands/trial/TrialModule447.java",
    "dev/settledlands/trial/TrialGraph446.java",
    "dev/settledlands/npc/client/PursuitGraphNode272.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/client/TrialCard446.java",
    "dev/settledlands/npc/client/TrialModuleCard447.java",
    "dev/settledlands/npc/client/TrialModuleClient447.java",
    "dev/settledlands/npc/client/TrialModulePickerScreen447.java",
    "dev/settledlands/mixin/BlockMenu363Mixin.java",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.java",
    "dev/settledlands/mixin/NpcTrialRows446Mixin.java",
    "dev/settledlands/mixin/TrialControlWiringMixin446.java",
    "dev/settledlands/mixin/TrialControlActionsMixin446.java",
    "dev/settledlands/mixin/TrialGraphValidationMixin446.java",
    "dev/settledlands/mixin/TrialCanvasSize447Mixin.java",
    "dev/settledlands/mixin/TrialGraphCanvas447Mixin.java",
    "dev/settledlands/mixin/TrialModuleUi447Mixin.java",
    "dev/settledlands/mixin/TrialModuleSocket447Mixin.java",
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.java",
]

CLIENT_MIXINS_APPENDED = [
    "TrialCanvasSize447Mixin",
    "TrialGraphCanvas447Mixin",
    "TrialModuleUi447Mixin",
    "TrialModuleSocket447Mixin",
    "TrialModuleInspector447Mixin",
]

MARKERS = {
    "dev/settledlands/trial/TrialModule447.class": ("trial_mod:", "createMissingModules"),
    "dev/settledlands/trial/TrialGraph446.class": ("TrialModule447", "compileConfig"),
    "dev/settledlands/npc/client/TrialCard446.class": ("inputPinY", "hub_modules"),
    "dev/settledlands/npc/client/TrialModuleCard447.class": ("moduleAtPin", "hubSlotAt"),
    "dev/settledlands/npc/client/TrialModuleClient447.class": ("restoreModulesAction", "scalarSetter"),
    "dev/settledlands/npc/client/TrialModulePickerScreen447.class": ("openAddMob", "loadMobOptions"),
    "dev/settledlands/npc/client/BlockMenu363.class": ("trialModuleSlot",),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("isModuleNode", "isTrialValueNode"),
    "dev/settledlands/mixin/TrialModuleUi447Mixin.class": ("syncCardConfig", "frameAll"),
    "dev/settledlands/mixin/TrialModuleSocket447Mixin.class": ("buildOrThrow", "connectModule"),
    "dev/settledlands/mixin/TrialModuleInspector447Mixin.class": ("titleKey", "deleteModuleAction"),
    "dev/settledlands/mixin/TrialCanvasSize447Mixin.class": ("isTrialNode",),
    "dev/settledlands/mixin/TrialGraphCanvas447Mixin.class": ("isTrialNode", "screenX"),
}

LANG_SET_RU = {
    "gui.settledlands.trial.hub_modules": "Модули: %s",
    "gui.settledlands.trial.hub_range": "Радиус: %s м",
    "gui.settledlands.trial.btn_restore_modules": "Восстановить модули",
    "gui.settledlands.trial.mod.mode": "Режим спавнера",
    "gui.settledlands.trial.mod.radius": "Радиус спавна",
    "gui.settledlands.trial.mod.wave": "Волна мобов",
    "gui.settledlands.trial.mod.timing": "Тайминги и КД",
    "gui.settledlands.trial.mod.mobs": "Пул мобов",
    "gui.settledlands.trial.mod.rewards": "Пул наград",
    "gui.settledlands.trial.mod.ominous": "Зловещий режим",
    "gui.settledlands.trial.mod.protect": "Защита и самоснос",
    "gui.settledlands.trial.mod.pin_mode": "Режим",
    "gui.settledlands.trial.mod.pin_radius": "Радиус",
    "gui.settledlands.trial.mod.pin_wave": "Волна",
    "gui.settledlands.trial.mod.pin_timing": "Таймер",
    "gui.settledlands.trial.mod.pin_mobs": "Мобы",
    "gui.settledlands.trial.mod.pin_rewards": "Награды",
    "gui.settledlands.trial.mod.pin_ominous": "Зловещ.",
    "gui.settledlands.trial.mod.pin_protect": "Защита",
    "gui.settledlands.trial.mod.menu_mode": "+ Модуль: Режим",
    "gui.settledlands.trial.mod.menu_radius": "+ Модуль: Радиус",
    "gui.settledlands.trial.mod.menu_wave": "+ Модуль: Волна",
    "gui.settledlands.trial.mod.menu_timing": "+ Модуль: Тайминги",
    "gui.settledlands.trial.mod.menu_mobs": "+ Модуль: Пул мобов",
    "gui.settledlands.trial.mod.menu_rewards": "+ Модуль: Награды",
    "gui.settledlands.trial.mod.menu_ominous": "+ Модуль: Зловещий",
    "gui.settledlands.trial.mod.menu_protect": "+ Модуль: Защита",
    "gui.settledlands.trial.mod.unlinked": "Не подключён (тяните →)",
    "gui.settledlands.trial.mod.card_scale": "От игроков: %s",
    "gui.settledlands.trial.mod.card_act": "Активация: %s м",
    "gui.settledlands.trial.mod.card_spawn": "Радиус спавна: %s м",
    "gui.settledlands.trial.mod.card_total": "Всего: %s",
    "gui.settledlands.trial.mod.card_sim": "Одновр.: %s",
    "gui.settledlands.trial.mod.card_batch": "За спавн (пачка): %s",
    "gui.settledlands.trial.mod.card_delay": "Задержка: %s т.",
    "gui.settledlands.trial.mod.card_cd": "КД: %s",
    "gui.settledlands.trial.mod.card_mob_count": "Мобов в пуле: %s",
    "gui.settledlands.trial.mod.card_rew_count": "Наград в пуле: %s",
    "gui.settledlands.trial.mod.card_rew_empty": "Список наград пуст",
    "gui.settledlands.trial.mod.card_om_mult": "Множитель: %s",
    "gui.settledlands.trial.mod.card_om_flags": "Броня/Эфф: %s",
    "gui.settledlands.trial.mod.card_om_keys": "Конв. ключей: %s",
    "gui.settledlands.trial.mod.card_nb": "Защита: %s",
    "gui.settledlands.trial.mod.card_sd": "Самоснос: %s",
    "gui.settledlands.trial.mod.slot_default": "по умолч.",
    "gui.settledlands.trial.mod.linked_to": "Подключён к %s",
    "gui.settledlands.trial.mod.unlinked_short": "Не подключён",
    "gui.settledlands.trial.mod.btn_unlink": "Отвязать",
    "gui.settledlands.trial.mod.btn_attach": "Подключить",
    "gui.settledlands.trial.mod.add_mob_catalog": "+ Добавить моба (каталог)",
    "gui.settledlands.trial.mod.open_mob_list": "Редактор списка мобов...",
    "gui.settledlands.trial.mod.add_reward_catalog": "+ Добавить награду (каталог)",
    "gui.settledlands.trial.mod.open_reward_list": "Таблица наград...",
    "gui.settledlands.trial.mod.pick_catalog": "Открыть каталог",
    "gui.settledlands.trial.mod.done": "Готово",
}

LANG_SET_EN = {
    "gui.settledlands.trial.hub_modules": "Modules: %s",
    "gui.settledlands.trial.hub_range": "Range: %s m",
    "gui.settledlands.trial.btn_restore_modules": "Restore modules",
    "gui.settledlands.trial.mod.mode": "Spawner Mode",
    "gui.settledlands.trial.mod.radius": "Spawn Radius",
    "gui.settledlands.trial.mod.wave": "Mob Wave",
    "gui.settledlands.trial.mod.timing": "Timings & Cooldown",
    "gui.settledlands.trial.mod.mobs": "Mob Pool",
    "gui.settledlands.trial.mod.rewards": "Reward Pool",
    "gui.settledlands.trial.mod.ominous": "Ominous Mode",
    "gui.settledlands.trial.mod.protect": "Protection & Self-Destruct",
    "gui.settledlands.trial.mod.pin_mode": "Mode",
    "gui.settledlands.trial.mod.pin_radius": "Radius",
    "gui.settledlands.trial.mod.pin_wave": "Wave",
    "gui.settledlands.trial.mod.pin_timing": "Timing",
    "gui.settledlands.trial.mod.pin_mobs": "Mobs",
    "gui.settledlands.trial.mod.pin_rewards": "Rewards",
    "gui.settledlands.trial.mod.pin_ominous": "Ominous",
    "gui.settledlands.trial.mod.pin_protect": "Protect",
    "gui.settledlands.trial.mod.menu_mode": "+ Module: Mode",
    "gui.settledlands.trial.mod.menu_radius": "+ Module: Radius",
    "gui.settledlands.trial.mod.menu_wave": "+ Module: Wave",
    "gui.settledlands.trial.mod.menu_timing": "+ Module: Timings",
    "gui.settledlands.trial.mod.menu_mobs": "+ Module: Mob Pool",
    "gui.settledlands.trial.mod.menu_rewards": "+ Module: Rewards",
    "gui.settledlands.trial.mod.menu_ominous": "+ Module: Ominous",
    "gui.settledlands.trial.mod.menu_protect": "+ Module: Protect",
    "gui.settledlands.trial.mod.unlinked": "Unlinked (drag →)",
    "gui.settledlands.trial.mod.card_scale": "Scale players: %s",
    "gui.settledlands.trial.mod.card_act": "Activation: %s m",
    "gui.settledlands.trial.mod.card_spawn": "Spawn radius: %s m",
    "gui.settledlands.trial.mod.card_total": "Total: %s",
    "gui.settledlands.trial.mod.card_sim": "Simult.: %s",
    "gui.settledlands.trial.mod.card_batch": "Batch size: %s",
    "gui.settledlands.trial.mod.card_delay": "Delay: %s t",
    "gui.settledlands.trial.mod.card_cd": "CD: %s",
    "gui.settledlands.trial.mod.card_mob_count": "Mobs in pool: %s",
    "gui.settledlands.trial.mod.card_rew_count": "Rewards in pool: %s",
    "gui.settledlands.trial.mod.card_rew_empty": "Reward pool empty",
    "gui.settledlands.trial.mod.card_om_mult": "Multiplier: %s",
    "gui.settledlands.trial.mod.card_om_flags": "Gear/Haz: %s",
    "gui.settledlands.trial.mod.card_om_keys": "Key conv.: %s",
    "gui.settledlands.trial.mod.card_nb": "No-break: %s",
    "gui.settledlands.trial.mod.card_sd": "Self-destr.: %s",
    "gui.settledlands.trial.mod.slot_default": "default",
    "gui.settledlands.trial.mod.linked_to": "Linked to %s",
    "gui.settledlands.trial.mod.unlinked_short": "Unlinked",
    "gui.settledlands.trial.mod.btn_unlink": "Unlink",
    "gui.settledlands.trial.mod.btn_attach": "Attach",
    "gui.settledlands.trial.mod.add_mob_catalog": "+ Add Mob (catalog)",
    "gui.settledlands.trial.mod.open_mob_list": "Edit mob pool...",
    "gui.settledlands.trial.mod.add_reward_catalog": "+ Add Reward (catalog)",
    "gui.settledlands.trial.mod.open_reward_list": "Edit reward pool...",
    "gui.settledlands.trial.mod.pick_catalog": "Browse catalog",
    "gui.settledlands.trial.mod.done": "Done",
}

CHANGELOG_EN = (
    "Build 447 redesigns the trialtuner:trial_spawner graph editor into a modular node network: "
    "instead of a single card opened through a separate 'Configure' button, new and migrated "
    "Trial Spawner graphs open as a central hub card (256x176 with 8 color-coded left input slots, "
    "Start/Clear/Destroy execution pins, and blue lastPlayer pin) wired from 8 specialized "
    "configuration module cards (Mode, Spawn Radius, Mob Wave, Timings & Cooldown, Mob Pool, "
    "Reward Pool, Ominous Mode, Protection & Self-Destruct), each with its own focused sidebar "
    "inspector, catalog mob/item picker screens, live hub/server synchronization, drag-and-drop "
    "module wiring, and right-click module palette actions."
)

PATCHINFO = (
    "Сборка 447: модульная сеть узлов в редакторе графа trialtuner:trial_spawner.\n"
    "  * Вместо одной монолитной карточки с кнопкой «Настроить спавнер» граф спавнера испытаний\n"
    "    теперь открывается как центральный хаб-узел (256x176, 8 цветных входных слотов слева,\n"
    "    выходы Старт/Зачистка/Снос и синий пин игрока справа) и 8 подключённых к нему\n"
    "    специализированных модульных узлов: Режим, Радиус, Волна, Тайминги/КД, Пул мобов,\n"
    "    Пул наград, Зловещий режим и Защита/Самоснос;\n"
    "  * Каждый модуль имеет собственный специализированный инспектор справа (и каталоги выбора\n"
    "    мобов/предметов для пулов) и в реальном времени компилируется в конфигурацию хаба\n"
    "    и блока trialtuner:trial_spawner при сохранении графа;\n"
    "  * Полная поддержка переподключения модулей цветными проводами (ЛКМ/ПКМ/СКМ),\n"
    "    создания новых модулей через ПКМ-меню палитры, кнопки «Восстановить модули» на хабе\n"
    "    и автоматической миграции старых однонодовых графов.\n"
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


def compile_build447(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build447 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_main_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    common = doc.setdefault("mixins", [])
    client = doc.setdefault("client", [])

    for name in CLIENT_MIXINS_APPENDED:
        if name in common:
            raise SystemExit("%s must not be in common mixins" % name)
        if name not in client:
            client.append(name)
    if client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out447",
                    help="directory for compiled build 447 classes")
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
        print("compiling Build 447 sources via ECJ (-21)...")
        compile_build447(base, srcroot)
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

    blobs["settledlands.mixins.json"] = patch_main_mixins(blobs["settledlands.mixins.json"])
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
