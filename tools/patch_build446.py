#!/usr/bin/env python3
"""
Build 446 - Auto-Cleanup of Destroyed Block/Door/Chest/Spawner Graphs & Full Trial Spawner Support.

Builds on Build 445 (``settledlands-0.8.0-build445-direct-percent-loot-no-duplicates.jar``):
  1. Automatic graph & lock cleanup on any block destruction/replacement:
       * ``BlockDestroy446Mixin`` hooks ``Level#setBlock(BlockPos, BlockState, int, int)`` with a
         zero-allocation reentrant depth/stack tracker so any block replacement (survival/creative break,
         explosions, ``/setblock``, pistons, Trial Spawner self-destruct, mods) removes associated
         block/door/chest/spawner graphs, door locks across both halves, chest locks, and client lock
         state, while strictly preserving property-only state edits (open/close/press/flip/books).
       * ``RadiusPass354`` periodically runs ``GraphBlockCleanup446#reconcileLoaded`` over loaded
         chunks only (catching direct chunk edits such as Axiom).
       * Existing survival lock protection (``DoorEvents368``, ``DoorBreak368Mixin``,
         ``ChestGraph396Events``) remains untouched.
  2. Full ``trialtuner:trial_spawner`` integration:
       * (2a) NPC-wand click on ``trialtuner:trial_spawner`` opens a dedicated single-tab graph editor
         with card type ``TRIAL_TYPE = 60`` (``TrialGraph446``, ``TrialCard446``, 3 execution sockets:
         wave start ``pin_start``, wave clear ``pin_clear``, spawner destruction ``pin_destroy``, plus
         blue ``lastPlayer`` value socket), 4-tab live ``TrialControlConfigScreen446``, BE-embedded
         persistence for Axiom blueprints, and ``@Pseudo`` Trial Tuner compat hooks.
       * (2b) Cross-graph ``Триал-спавнер`` control node (``trigger:spawner:<op>|<dim>|<rx>|<ry>|<rz>|<flags>|<cfg>``)
         available in all 6 graph editors (NPC, common, block, door, chest, trial spawner) with
         ``START``, ``CONFIG``, ``CONFIG_START``, ``STOP``, ``RESET_CD``, and ``DESTROY`` operations.
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

BASE_VERSION = "0.8.0-build445-direct-percent-loot-no-duplicates"
NEW_VERSION = "0.8.0-build446-auto-cleanup-and-trial-spawner"
OUT_NAME = "settledlands-0.8.0-build446-auto-cleanup-and-trial-spawner.jar"

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
    ROOT / "build303-src" / "stubs446-src",
)

SOURCE_FILES = (
    # New Trial Spawner & Cleanup Core
    "dev/settledlands/trial/TrialConfig446.java",
    "dev/settledlands/trial/TrialSpawnerBridge446.java",
    "dev/settledlands/trial/TrialGraph446Store.java",
    "dev/settledlands/trial/TrialGraph446.java",
    "dev/settledlands/trial/TrialSpawnerEvents446.java",
    "dev/settledlands/trial/TrialControlNode446.java",
    "dev/settledlands/trial/TrialControlActions446.java",
    "dev/settledlands/npc/GraphBlockCleanup446.java",
    # Updated Shared / Server / Client Classes
    "dev/settledlands/npc/BlockTriggerStore351.java",
    "dev/settledlands/npc/BlockTriggerEvents351.java",
    "dev/settledlands/npc/BlockTrigger351.java",
    "dev/settledlands/npc/RadiusPass354.java",
    "dev/settledlands/npc/BlockTalk358.java",
    "dev/settledlands/door/DoorStore368.java",
    "dev/settledlands/door/DoorSync375.java",
    "dev/settledlands/door/DoorGraph382Store.java",
    "dev/settledlands/chest/ChestLock397.java",
    "dev/settledlands/npc/NpcGraphAnchorCore420.java",
    "dev/settledlands/npc/NpcGraphAnchor420.java",
    "dev/settledlands/npc/NpcGraphAnchorFiles420.java",
    "dev/settledlands/npc/TeleportNode424.java",
    "dev/settledlands/npc/client/PursuitGraphNode272.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/client/CardEventPin398.java",
    # New Trial UI Classes
    "dev/settledlands/npc/client/TrialControlConfigScreen446.java",
    "dev/settledlands/npc/client/TrialGraphClient446.java",
    "dev/settledlands/npc/client/TrialCard446.java",
    "dev/settledlands/npc/client/TrialControlCard446.java",
    # Updated Mixins
    "dev/settledlands/mixin/BlockNoTabs360Mixin.java",
    "dev/settledlands/mixin/BlockMenu363Mixin.java",
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.java",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.java",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.java",
    "dev/settledlands/mixin/TeleportContextMenuMixin424.java",
    "dev/settledlands/mixin/TeleportGraphUiMixin424.java",
    # New Mixins
    "dev/settledlands/mixin/BlockDestroy446Mixin.java",
    "dev/settledlands/mixin/TrialGraphValidationMixin446.java",
    "dev/settledlands/mixin/NpcTrialOpen446Mixin.java",
    "dev/settledlands/mixin/NpcTrialSave446Mixin.java",
    "dev/settledlands/mixin/NpcTrialNoStats446Mixin.java",
    "dev/settledlands/mixin/NpcTrialNoAi446Mixin.java",
    "dev/settledlands/mixin/NpcTrialCard446Mixin.java",
    "dev/settledlands/mixin/NpcTrialRows446Mixin.java",
    "dev/settledlands/mixin/NpcTrialSocket446Mixin.java",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.java",
    "dev/settledlands/mixin/TrialControlActionsMixin446.java",
    "dev/settledlands/mixin/TrialControlWiringMixin446.java",
    "dev/settledlands/mixin/TrialControlGraphUiMixin446.java",
    "dev/settledlands/mixin/TrialControlInspectorMixin446.java",
    # New Optional Compat Mixins
    "dev/settledlands/mixin/compat/TrialSpawnerEntityMixin446.java",
    "dev/settledlands/mixin/compat/TrialTriggersMixin446.java",
)

COMMON_MIXINS_PREPENDED = (
    "NpcTrialSave446Mixin",
)

COMMON_MIXINS_APPENDED = (
    "BlockDestroy446Mixin",
    "TrialGraphValidationMixin446",
    "TrialControlActionsMixin446",
    "TrialControlWiringMixin446",
)

CLIENT_MIXINS_APPENDED = (
    "NpcTrialOpen446Mixin",
    "NpcTrialNoStats446Mixin",
    "NpcTrialNoAi446Mixin",
    "NpcTrialCard446Mixin",
    "NpcTrialRows446Mixin",
    "NpcTrialSocket446Mixin",
    "NpcTrialPalette446Mixin",
    "TrialControlGraphUiMixin446",
    "TrialControlInspectorMixin446",
)

COMPAT_COMMON_MIXINS_APPENDED = (
    "TrialSpawnerEntityMixin446",
    "TrialTriggersMixin446",
)

MARKERS = {
    "dev/settledlands/trial/TrialConfig446.class": ("toJson", "fromJson", "sanitize"),
    "dev/settledlands/trial/TrialSpawnerBridge446.class": ("trialtuner:trial_spawner", "destroyBlock"),
    "dev/settledlands/trial/TrialGraph446Store.class": ("settledlands_trial_graphs", "keysOfDimension"),
    "dev/settledlands/trial/TrialGraph446.class": ("SettledLandsTrialGraph", "onSpawnerDestroyed"),
    "dev/settledlands/trial/TrialControlNode446.class": ("trigger:spawner:", "CONFIG_START"),
    "dev/settledlands/npc/GraphBlockCleanup446.class": ("onBlockChanged", "reconcileLoaded"),
    "dev/settledlands/mixin/BlockDestroy446Mixin.class": ("settledlands$beforeSetBlock446", "settledlands$afterSetBlock446"),
    "dev/settledlands/mixin/TrialGraphValidationMixin446.class": ("settledlands$maskTrialNodes446",),
    "dev/settledlands/mixin/NpcTrialSocket446Mixin.class": ("settledlands$drawTrialStartWires446",),
    "dev/settledlands/mixin/compat/TrialSpawnerEntityMixin446.class": ("startWave", "finishWave"),
    "dev/settledlands/mixin/compat/TrialTriggersMixin446.class": ("onDestroyed",),
}

LANG_SET_RU = {
    "gui.settledlands.trial.node": "Триал-спавнер",
    "message.settledlands.trial.graph_open": "Граф спавнера испытаний: %s",
    "message.settledlands.trial.graph_saved": "Граф спавнера испытаний сохранён (%s)",
    "message.settledlands.trial.graph_cleared": "Граф спавнера испытаний очищен (%s)",
    "message.settledlands.trial.graph_full": "Лимит графов спавнеров испытаний (%s)",
    "gui.settledlands.trial.palette": "В графе спавнера недоступен этот узел",
    "gui.settledlands.trial.added_node": "Добавлен узел «%s»",
    "gui.settledlands.trial.one_tab": "У спавнера испытаний одна вкладка графа",
    "gui.settledlands.trial.mode.normal": "Обычный",
    "gui.settledlands.trial.mode.ominous": "Зловещий",
    "gui.settledlands.trial.mode.special": "Особый",
    "gui.settledlands.trial.line_mode": "Режим: %s",
    "gui.settledlands.trial.line_mobs": "Мобы: %s",
    "gui.settledlands.trial.line_rewards": "КД / Нагр.: %s",
    "gui.settledlands.trial.line_waves": "Волн: %s",
    "gui.settledlands.trial.player_none": "Игрок: (нет)",
    "gui.settledlands.trial.player_last": "Игрок: %s",
    "gui.settledlands.trial.pin_start": "Старт",
    "gui.settledlands.trial.pin_clear": "Зачистка",
    "gui.settledlands.trial.pin_destroy": "Снос",
    "gui.settledlands.trial.pin_player": "Игрок",
    "gui.settledlands.trial.btn_configure": "Настроить спавнер...",
    "gui.settledlands.trial.btn_clear_sockets": "Отвязать все выходы",
    "gui.settledlands.trial.hint_start": "Старт: при запуске волны",
    "gui.settledlands.trial.hint_clear": "Зачистка: при победе в волне",
    "gui.settledlands.trial.hint_destroy": "Снос: при уничтожении блока",
    "gui.settledlands.trial.hint_player": "Игрок (синий): участник испытания",
    "gui.settledlands.trial.cfg.title": "Настройка триал-спавнера",
    "gui.settledlands.trial.cfg.tab_general": "Основное",
    "gui.settledlands.trial.cfg.tab_ominous": "Зловещий",
    "gui.settledlands.trial.cfg.tab_mobs": "Мобы",
    "gui.settledlands.trial.cfg.tab_rewards": "Награды",
    "gui.settledlands.trial.cfg.save_close": "Применить и закрыть",
    "gui.settledlands.trial.cfg.cancel": "Отмена",
    "gui.settledlands.trial.cfg.reset": "Сброс",
    "gui.settledlands.trial.cfg.reset_done": "Параметры сброшены по умолчанию",
    "gui.settledlands.trial.cfg.mode_btn": "Режим: %s",
    "gui.settledlands.trial.cfg.yes": "Да",
    "gui.settledlands.trial.cfg.no": "Нет",
    "gui.settledlands.trial.cfg.prevent_break": "Защита от поломки: %s",
    "gui.settledlands.trial.cfg.total_mobs": "Всего мобов",
    "gui.settledlands.trial.cfg.sim_mobs": "Одновременно",
    "gui.settledlands.trial.cfg.batch": "За спавн",
    "gui.settledlands.trial.cfg.act_range": "Радиус активации",
    "gui.settledlands.trial.cfg.spawn_range": "Радиус спавна",
    "gui.settledlands.trial.cfg.cooldown": "Кулдаун (тики)",
    "gui.settledlands.trial.cfg.min_delay": "Мин. задержка",
    "gui.settledlands.trial.cfg.max_delay": "Макс. задержка",
    "gui.settledlands.trial.cfg.scale_players": "Масштаб от игроков: %s",
    "gui.settledlands.trial.cfg.self_destruct": "Самоуничтожение: %s",
    "gui.settledlands.trial.cfg.per_player_total": "+мобов / игрок",
    "gui.settledlands.trial.cfg.destruct_waves": "Через волн",
    "gui.settledlands.trial.cfg.per_player_sim": "+одновр. / игрок",
    "gui.settledlands.trial.cfg.destruct_chance": "Шанс сноса %",
    "gui.settledlands.trial.cfg.ominous_mult": "Множитель мобов",
    "gui.settledlands.trial.cfg.ominous_keys": "Конвертировать ключи: %s",
    "gui.settledlands.trial.cfg.ominous_hazards": "Зловещие эффекты: %s",
    "gui.settledlands.trial.cfg.ominous_interval": "Интервал эффектов (тики)",
    "gui.settledlands.trial.cfg.add_mob": "+ Добавить моба",
    "gui.settledlands.trial.cfg.add_reward": "+ Добавить награду",
    "gui.settledlands.trial.cfg.pick": "Выбрать",
    "gui.settledlands.trial.cfg.keep_one_mob": "Должен остаться минимум 1 моб",
    "gui.settledlands.trial.cfg.mobs_hdr": "Сущность (id) / Выбор / Вес",
    "gui.settledlands.trial.cfg.rewards_hdr": "Предмет / Выбор / Мин / Макс / Вес / Шанс %",
    "gui.settledlands.trial.cfg.back": "Назад",
    "gui.settledlands.trial.cfg.search": "Поиск...",
    "gui.settledlands.trial.cfg.pick_mob_title": "Выбор сущности для спавнера",
    "gui.settledlands.trial.cfg.pick_item_title": "Выбор предмета для награды",
    "gui.settledlands.trial_ctl.menu": "+ Триал-спавнер",
    "gui.settledlands.trial_ctl.title": "Триал-спавнер",
    "gui.settledlands.trial_ctl.added": "Добавлен узел «Триал-спавнер»",
    "gui.settledlands.trial_ctl.op_prefix": "Действие",
    "gui.settledlands.trial_ctl.op.start": "Запуск волны",
    "gui.settledlands.trial_ctl.op.config": "Настроить",
    "gui.settledlands.trial_ctl.op.config_start": "Настроить + Старт",
    "gui.settledlands.trial_ctl.op.stop": "Остановить",
    "gui.settledlands.trial_ctl.op.reset_cd": "Сбросить КД",
    "gui.settledlands.trial_ctl.op.destroy": "Уничтожить",
    "gui.settledlands.trial_ctl.dim_current": "Текущий мир",
    "gui.settledlands.trial_ctl.kill_mobs_yes": "Убить мобов: Да",
    "gui.settledlands.trial_ctl.kill_mobs_no": "Убить мобов: Нет",
    "gui.settledlands.trial_ctl.card_hint": "Контроль спавнера",
    "gui.settledlands.trial_ctl.coords_hint": "Координаты (можно ~ или ~5)",
    "gui.settledlands.trial_ctl.btn_here": "Указать ~ ~ ~ (текущая поз.)",
}

LANG_SET_EN = {
    "gui.settledlands.trial.node": "Trial Spawner",
    "message.settledlands.trial.graph_open": "Trial Spawner graph: %s",
    "message.settledlands.trial.graph_saved": "Trial Spawner graph saved (%s)",
    "message.settledlands.trial.graph_cleared": "Trial Spawner graph cleared (%s)",
    "message.settledlands.trial.graph_full": "Trial Spawner graph limit reached (%s)",
    "gui.settledlands.trial.palette": "Node not supported in Trial Spawner graph",
    "gui.settledlands.trial.added_node": "Added \"%s\" node",
    "gui.settledlands.trial.one_tab": "Trial Spawner has a single graph tab",
    "gui.settledlands.trial.mode.normal": "Normal",
    "gui.settledlands.trial.mode.ominous": "Ominous",
    "gui.settledlands.trial.mode.special": "Special",
    "gui.settledlands.trial.line_mode": "Mode: %s",
    "gui.settledlands.trial.line_mobs": "Mobs: %s",
    "gui.settledlands.trial.line_rewards": "CD / Loot: %s",
    "gui.settledlands.trial.line_waves": "Waves: %s",
    "gui.settledlands.trial.player_none": "Player: (none)",
    "gui.settledlands.trial.player_last": "Player: %s",
    "gui.settledlands.trial.pin_start": "Start",
    "gui.settledlands.trial.pin_clear": "Cleared",
    "gui.settledlands.trial.pin_destroy": "Destroyed",
    "gui.settledlands.trial.pin_player": "Player",
    "gui.settledlands.trial.btn_configure": "Configure Spawner...",
    "gui.settledlands.trial.btn_clear_sockets": "Unlink all outputs",
    "gui.settledlands.trial.hint_start": "Start: when wave begins",
    "gui.settledlands.trial.hint_clear": "Cleared: when wave completes",
    "gui.settledlands.trial.hint_destroy": "Destroyed: on spawner removal",
    "gui.settledlands.trial.hint_player": "Player (blue): trial participant",
    "gui.settledlands.trial.cfg.title": "Trial Spawner Configuration",
    "gui.settledlands.trial.cfg.tab_general": "General",
    "gui.settledlands.trial.cfg.tab_ominous": "Ominous",
    "gui.settledlands.trial.cfg.tab_mobs": "Mobs",
    "gui.settledlands.trial.cfg.tab_rewards": "Rewards",
    "gui.settledlands.trial.cfg.save_close": "Apply & Close",
    "gui.settledlands.trial.cfg.cancel": "Cancel",
    "gui.settledlands.trial.cfg.reset": "Reset",
    "gui.settledlands.trial.cfg.reset_done": "Restored default spawner settings",
    "gui.settledlands.trial.cfg.mode_btn": "Mode: %s",
    "gui.settledlands.trial.cfg.yes": "Yes",
    "gui.settledlands.trial.cfg.no": "No",
    "gui.settledlands.trial.cfg.prevent_break": "Prevent player break: %s",
    "gui.settledlands.trial.cfg.total_mobs": "Total mobs",
    "gui.settledlands.trial.cfg.sim_mobs": "Simultaneous",
    "gui.settledlands.trial.cfg.batch": "Batch size",
    "gui.settledlands.trial.cfg.act_range": "Activation range",
    "gui.settledlands.trial.cfg.spawn_range": "Spawn range",
    "gui.settledlands.trial.cfg.cooldown": "Cooldown (ticks)",
    "gui.settledlands.trial.cfg.min_delay": "Min spawn delay",
    "gui.settledlands.trial.cfg.max_delay": "Max spawn delay",
    "gui.settledlands.trial.cfg.scale_players": "Scale with players: %s",
    "gui.settledlands.trial.cfg.self_destruct": "Self-destruct: %s",
    "gui.settledlands.trial.cfg.per_player_total": "+mobs / player",
    "gui.settledlands.trial.cfg.destruct_waves": "After waves",
    "gui.settledlands.trial.cfg.per_player_sim": "+sim / player",
    "gui.settledlands.trial.cfg.destruct_chance": "Destruct chance %",
    "gui.settledlands.trial.cfg.ominous_mult": "Ominous mob mult",
    "gui.settledlands.trial.cfg.ominous_keys": "Convert trial keys: %s",
    "gui.settledlands.trial.cfg.ominous_hazards": "Ominous hazards: %s",
    "gui.settledlands.trial.cfg.ominous_interval": "Hazard interval (ticks)",
    "gui.settledlands.trial.cfg.add_mob": "+ Add Mob",
    "gui.settledlands.trial.cfg.add_reward": "+ Add Reward",
    "gui.settledlands.trial.cfg.pick": "Pick",
    "gui.settledlands.trial.cfg.keep_one_mob": "At least 1 mob entry must remain",
    "gui.settledlands.trial.cfg.mobs_hdr": "Entity ID / Pick / Weight",
    "gui.settledlands.trial.cfg.rewards_hdr": "Item / Pick / Min / Max / Wgt / Chance %",
    "gui.settledlands.trial.cfg.back": "Back",
    "gui.settledlands.trial.cfg.search": "Search...",
    "gui.settledlands.trial.cfg.pick_mob_title": "Pick Mob Entity",
    "gui.settledlands.trial.cfg.pick_item_title": "Pick Reward Item",
    "gui.settledlands.trial_ctl.menu": "+ Trial Spawner",
    "gui.settledlands.trial_ctl.title": "Trial Spawner",
    "gui.settledlands.trial_ctl.added": "Added Trial Spawner control node",
    "gui.settledlands.trial_ctl.op_prefix": "Action",
    "gui.settledlands.trial_ctl.op.start": "Start Wave",
    "gui.settledlands.trial_ctl.op.config": "Configure",
    "gui.settledlands.trial_ctl.op.config_start": "Configure + Start",
    "gui.settledlands.trial_ctl.op.stop": "Stop Wave",
    "gui.settledlands.trial_ctl.op.reset_cd": "Reset Cooldown",
    "gui.settledlands.trial_ctl.op.destroy": "Destroy",
    "gui.settledlands.trial_ctl.dim_current": "Current world",
    "gui.settledlands.trial_ctl.kill_mobs_yes": "Kill active mobs: Yes",
    "gui.settledlands.trial_ctl.kill_mobs_no": "Kill active mobs: No",
    "gui.settledlands.trial_ctl.card_hint": "Spawner control",
    "gui.settledlands.trial_ctl.coords_hint": "Coords (~ or ~offset allowed)",
    "gui.settledlands.trial_ctl.btn_here": "Set ~ ~ ~ (current pos)",
}

CHANGELOG_EN = (
    "Build 446 automatically deletes stored block, door, chest, and trial spawner graphs "
    "(and door/chest locks across both halves) whenever the host block is destroyed or replaced "
    "(via Level#setBlock and periodic loaded-chunk reconciliation) while keeping survival lock "
    "protection and ignoring property-only blockstate updates, and adds full trialtuner:trial_spawner "
    "support with a dedicated wand-opened graph editor (wave start/clear/destroy event sockets, "
    "blue lastPlayer pin, 4-tab live configuration screen, Axiom anchor persistence, and Trial "
    "Tuner compat mixins) plus a cross-graph Trial Spawner control node across all 6 graph editors."
)
PATCHINFO = (
    "Сборка 446: автоочистка графов при уничтожении блоков и полная интеграция trialtuner:trial_spawner.\n"
    "  * Уничтожение/замена блока (ломание в креативе, взрыв, /setblock, поршни, самоуничтожение,\n"
    "    Axiom и др.) автоматически удаляет графы блоков/дверей/сундуков/спавнеров и сбрасывает\n"
    "    замки на обеих половинах двери и синхронизацию клиента, не трогая обычные переключения\n"
    "    состояний (открытие дверей/сундуков, нажатие кнопок, рычагов, взятие книг);\n"
    "  * Жезл настроек НПС по trialtuner:trial_spawner открывает граф спавнера (карточка TRIAL_TYPE=60,\n"
    "    3 сокета событий Старт/Зачистка/Снос + синий сокет последнего игрока, 4-вкладочный\n"
    "    редактор параметров, перенос через якоря Axiom и @Pseudo-интеграция с Trial Tuner);\n"
    "  * Добавлен кросс-графовый узел «Триал-спавнер» во все 6 редакторов графов (НПС, общий, блок,\n"
    "    дверь, сундук, спавнер) с действиями START/CONFIG/CONFIG_START/STOP/RESET_CD/DESTROY.\n"
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


def compile_build446(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build446 compile failed:\n" + r2.stdout + r2.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_main_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    common = doc.setdefault("mixins", [])
    client = doc.setdefault("client", [])

    for name in reversed(COMMON_MIXINS_PREPENDED):
        if name in client:
            raise SystemExit("%s must not be in client mixins" % name)
        if name not in common:
            # Keep NpcDoorSave382Mixin at index 0 for door_graph_check while staying ahead of NpcCommonGraphMixin334.
            insert_at = 1 if common and common[0] == "NpcDoorSave382Mixin" else 0
            common.insert(insert_at, name)
    for name in COMMON_MIXINS_APPENDED:
        if name in client:
            raise SystemExit("%s must not be in client mixins" % name)
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS_APPENDED:
        if name in common:
            raise SystemExit("%s must not be in common mixins" % name)
        if name not in client:
            client.append(name)
    if client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def patch_compat_mixins(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    common = doc.setdefault("mixins", [])
    for name in COMPAT_COMMON_MIXINS_APPENDED:
        if name not in common:
            common.append(name)
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out446",
                    help="directory for compiled build 446 classes")
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
        print("compiling Build 446 sources via ECJ (-21)...")
        compile_build446(base, srcroot)
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
    blobs["settledlands.compat.mixins.json"] = patch_compat_mixins(
        blobs["settledlands.compat.mixins.json"])
    print("  updated : settledlands.mixins.json + settledlands.compat.mixins.json")

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
