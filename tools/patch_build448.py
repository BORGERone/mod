"""Build 448: clean RMB menu (trial modules via restore button only), docked card footers
explaining every node across all graph types, and state-aware >2-second hover tooltips."""

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

BASE_VERSION = "0.8.0-build447-modular-trial-spawner-graph"
NEW_VERSION = "0.8.0-build448-card-descriptions-and-hover-tooltips"
OUT_NAME = "settledlands-0.8.0-build448-card-descriptions-and-hover-tooltips.jar"

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
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/client/LootPoolCard436.java",
    "dev/settledlands/npc/client/TrialControlConfigScreen446.java",
    "dev/settledlands/npc/client/TrialModulePickerScreen447.java",
    "dev/settledlands/npc/client/GraphCardDesc448.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.java",
    "dev/settledlands/mixin/GraphCardFooter448Mixin.java",
    "dev/settledlands/mixin/AutoAiFooter448Mixin.java",
    "dev/settledlands/mixin/GraphUiWidgets448Mixin.java",
    "dev/settledlands/mixin/GraphDropdownHover448Mixin.java",
    "dev/settledlands/mixin/GraphHover448Mixin.java",
]

CLIENT_MIXINS_APPENDED = [
    "GraphCardFooter448Mixin",
    "AutoAiFooter448Mixin",
    "GraphUiWidgets448Mixin",
    "GraphDropdownHover448Mixin",
    "GraphHover448Mixin",
]

MARKERS = {
    "dev/settledlands/npc/client/GraphCardDesc448.class": ("desc448.card.trial_hub", "drawNodeFooter"),
    "dev/settledlands/npc/client/GraphTooltips448.class": ("tip448.top.undo", "renderGraphTooltip"),
    "dev/settledlands/mixin/GraphHover448Mixin.class": ("settledlands$renderTooltip448",),
    "dev/settledlands/mixin/GraphCardFooter448Mixin.class": ("settledlands$drawNodeFooter448",),
}

LANG_SET_RU = {
    # ---- Docked card footers (every node type across all graph editors) ----
    "gui.settledlands.desc448.card.node_0": "Выводит текст реплики NPC игроку и переводит диалог дальше.",
    "gui.settledlands.desc448.card.cutscene_line": "Озвучивает реплику внутри катсцены и двигает сценарий дальше.",
    "gui.settledlands.desc448.card.node_1": "Показывает кнопку ответа игрока и задаёт условия показа.",
    "gui.settledlands.desc448.card.node_2": "Выполняет команду сервера или действие при срабатывании импульса.",
    "gui.settledlands.desc448.card.node_3": "Сравнивает переменную с числом и разветвляет путь на Истина / Ложь.",
    "gui.settledlands.desc448.card.node_4": "Изменяет значение числовой переменной (локальной или общей).",
    "gui.settledlands.desc448.card.node_5": "Срабатывает, когда выбранная сущность входит в заданный радиус.",
    "gui.settledlands.desc448.card.node_6": "Запускает цепочку при получении NPC урона от указанного источника.",
    "gui.settledlands.desc448.card.node_7": "Активирует цепочку в заданное время суток игрового мира.",
    "gui.settledlands.desc448.card.node_8": "Запускает ветку с задержкой или циклически каждые N секунд.",
    "gui.settledlands.desc448.card.node_9": "Воспроизводит анимацию модели NPC (с привязкой к кости и повтором).",
    "gui.settledlands.desc448.card.node_10": "Выдаёт игроку задание и запускает ветку после его выбора.",
    "gui.settledlands.desc448.card.node_11": "Направляет NPC в точку мира (свободно или в режиме сопровождения).",
    "gui.settledlands.desc448.card.node_12": "Назначает NPC группу патрулирования и даёт сигнал у 1-й точки.",
    "gui.settledlands.desc448.card.node_13": "Случайно выбирает один из выходов согласно заданному диапазону.",
    "gui.settledlands.desc448.card.node_14": "Проигрывает камеру-катсцену из студии с привязанной цепочкой.",
    "gui.settledlands.desc448.card.node_15": "Находит игрока или моба в радиусе и передаёт ссылку по синему пину.",
    "gui.settledlands.desc448.card.stats": "Задаёт базовые характеристики NPC, его профессию и внешность.",
    "gui.settledlands.desc448.card.auto_ai": "Управляет автономной ходьбой по радиусу и автоатакой врагов.",
    "gui.settledlands.desc448.card.block_name": "Задаёт отображаемое имя блока-триггера и цвет заголовка.",
    "gui.settledlands.desc448.card.door": "Настраивает замок двери, сложность мини-игры и события открытия.",
    "gui.settledlands.desc448.card.door_signal": "Устаревший узел импульса открытия двери.",
    "gui.settledlands.desc448.card.door_target": "Устаревший узел ссылки на игрока, взломавшего дверь.",
    "gui.settledlands.desc448.card.chest": "Настраивает замок сундука, сложность взлома и выдачу добычи.",
    "gui.settledlands.desc448.card.trial_hub": "Центр триал-спавнера: собирает 8 модулей и запускает события волн.",
    "gui.settledlands.desc448.card.trial_ctrl": "Управляет триал-спавнером в мире (старт, настройка, стоп, сброс).",
    "gui.settledlands.desc448.card.teleport": "Телепортирует игрока или цель в заданные координаты и измерение.",
    "gui.settledlands.desc448.card.loot_pool": "Заполняет сундук добычей из выбранного пула с учётом веса.",
    "gui.settledlands.desc448.card.pursuit": "Заставляет NPC преследовать указанную цель или прекратить погоню.",
    "gui.settledlands.desc448.card.mod_mode": "Задаёт режим испытания (Обычный/Зловещий/Особый) и масштаб.",
    "gui.settledlands.desc448.card.mod_radius": "Определяет радиус обнаружения игроков и зону спавна мобов.",
    "gui.settledlands.desc448.card.mod_wave": "Задаёт размер волны, лимит мобов на арене и количество в пачке.",
    "gui.settledlands.desc448.card.mod_timing": "Настраивает паузу между пачками спавна и время отката (кулдаун).",
    "gui.settledlands.desc448.card.mod_mobs": "Список мобов спавнера и относительные веса их выпадения.",
    "gui.settledlands.desc448.card.mod_rewards": "Таблица наград, выпадающих после успешной зачистки волн.",
    "gui.settledlands.desc448.card.mod_ominous": "Параметры зловещего режима: усиление, броня, опасности и ключи.",
    "gui.settledlands.desc448.card.mod_protect": "Защита блоков арены от ломки и саморазрушение после N волн.",
    "gui.settledlands.desc448.card.unknown": "Служебный узел графа логики.",

    # ---- >2s tooltips: Top bar & Context menu ----
    "gui.settledlands.tip448.top.undo": "Отменяет последнее изменение графа (сочетание клавиш Ctrl+Z).",
    "gui.settledlands.tip448.top.redo": "Возвращает отменённое изменение графа (сочетание клавиш Ctrl+Y).",
    "gui.settledlands.tip448.top.tab_main": "Основной диалог и логика взаимодействия с игроком.",
    "gui.settledlands.tip448.top.tab_walk": "Отдельный граф для событий на маршруте и точках патрулирования.",
    "gui.settledlands.tip448.top.tab_anim": "Глобальный граф общих событий и фоновых анимаций NPC.",
    "gui.settledlands.tip448.ctx.copy": "Создаёт дубликат выбранного узла рядом на холсте со всеми его настройками.",
    "gui.settledlands.tip448.ctx.delete": "Удаляет узел и разрывает все входящие и исходящие провода.",

    # ---- >2s tooltips: Create menu rows ----
    "gui.settledlands.tip448.menu.node_0": "Добавить окно с текстом реплики NPC (или реплику катсцены).",
    "gui.settledlands.tip448.menu.node_1": "Добавить кнопку варианта ответа для игрока в диалоге.",
    "gui.settledlands.tip448.menu.node_2": "Добавить узел, выполняющий серверную команду или триггер.",
    "gui.settledlands.tip448.menu.node_3": "Добавить условие If для проверки числовой переменной.",
    "gui.settledlands.tip448.menu.node_4": "Добавить узел изменения значения переменной (+ / -).",
    "gui.settledlands.tip448.menu.node_5": "Добавить событие входа игрока или сущности в радиус.",
    "gui.settledlands.tip448.menu.node_6": "Добавить событие срабатывания при получении урона NPC.",
    "gui.settledlands.tip448.menu.node_7": "Добавить событие срабатывания по времени суток в мире.",
    "gui.settledlands.tip448.menu.node_8": "Добавить таймер задержки или циклического запуска.",
    "gui.settledlands.tip448.menu.node_9": "Добавить воспроизведение анимации модели NPC.",
    "gui.settledlands.tip448.menu.node_10": "Добавить узел выдачи задания (квеста) игроку.",
    "gui.settledlands.tip448.menu.node_11": "Добавить команду перемещения NPC в заданные координаты.",
    "gui.settledlands.tip448.menu.node_12": "Добавить смену группы патрулирования/подвижности NPC.",
    "gui.settledlands.tip448.menu.node_13": "Добавить узел случайного ветвления с настраиваемыми выходами.",
    "gui.settledlands.tip448.menu.node_14": "Добавить запуск камерной катсцены из студии.",
    "gui.settledlands.tip448.menu.node_15": "Добавить поиск ближайшей цели в радиусе (выдаёт синюю ссылку).",
    "gui.settledlands.tip448.menu.node_40": "Карточка замка и событий двери.",
    "gui.settledlands.tip448.menu.node_50": "Карточка замка и лута сундука.",
    "gui.settledlands.tip448.menu.node_60": "Центральный хаб-узел триал-спавнера.",
    "gui.settledlands.tip448.menu.pursuit": "Добавить команду преследования цели (или остановки погони).",
    "gui.settledlands.tip448.menu.teleport": "Добавить узел телепортации цели в указанную точку и мир.",
    "gui.settledlands.tip448.menu.trial_ctrl": "Добавить узел управления любым триал-спавнером в мире по координатам.",
    "gui.settledlands.tip448.menu.loot_pool": "Добавить генератор случайной добычи в сундуке из готового пула.",

    # ---- >2s tooltips: Common buttons & dropdowns ----
    "gui.settledlands.tip448.w.unlink": "Отсоединяет все исходящие провода этого узла (сам узел остаётся).",
    "gui.settledlands.tip448.w.delete": "Безвозвратно удаляет этот узел и его подключения (можно отменить Ctrl+Z).",
    "gui.settledlands.tip448.w.loot_pool_gear": "Открывает встроенный визуальный редактор предметов для выбранного пула добычи.",
    "gui.settledlands.tip448.world_item": "Выбрать это измерение для координат действия.",
    "gui.settledlands.tip448.dropdown_item": "Нажмите, чтобы выбрать этот вариант из списка.",

    # ---- >2s tooltips: Trial mode states (dynamic!) ----
    "gui.settledlands.tip448.mode.normal": "Текущий режим: ОБЫЧНЫЙ.\nСтандартное испытание Trial Spawner: базовый баланс волн, обычная экипировка и стандартная таблица наград без зловещих эффектов.",
    "gui.settledlands.tip448.mode.ominous": "Текущий режим: ЗЛОВЕЩИЙ.\nМобы усиливаются (множитель x%s), получают чарованную броню (если включено), срабатывают ловушки арены, а обычные ключи можно конвертировать в зловещие.",
    "gui.settledlands.tip448.mode.special": "Текущий режим: ОСОБЫЙ.\nРасширенный пользовательский режим испытания с индивидуальными правилами волны и без авто-подстройки под зловещий флаг.",

    # ---- >2s tooltips: Trial config toggles & module controls ----
    "gui.settledlands.tip448.cfg.scale_on": "Состояние: ВКЛ.\nКоличество мобов в волне и лимит на арене автоматически увеличиваются за каждого дополнительного игрока в радиусе активации.",
    "gui.settledlands.tip448.cfg.scale_off": "Состояние: ВЫКЛ.\nВолна всегда использует строго базовое число мобов и одновременный лимит, независимо от количества игроков рядом.",
    "gui.settledlands.tip448.om.equip_on": "Состояние: ВКЛ.\nВ зловещем режиме враги получают усиленную зловещую экипировку и эффекты по правилам спавнера.",
    "gui.settledlands.tip448.om.equip_off": "Состояние: ВЫКЛ.\nДаже в зловещем режиме мобы спавнятся с базовым снаряжением без дополнительного зловещего обвеса.",
    "gui.settledlands.tip448.om.hazards_on": "Состояние: ВКЛ.\nВо время зловещего боя над ареной периодически срабатывают опасные атаки/эффекты по интервалу тиков.",
    "gui.settledlands.tip448.om.hazards_off": "Состояние: ВЫКЛ.\nДополнительные периодические опасности/ловушки арены в зловещем режиме отключены.",
    "gui.settledlands.tip448.om.keys_on": "Состояние: ВКЛ.\nПри выпадении ключа испытаний в зловещем режиме награда конвертируется в зловещий ключ.",
    "gui.settledlands.tip448.om.keys_off": "Состояние: ВЫКЛ.\nКлючи выдаются строго по таблице наград без автоматической замены на зловещий ключ.",
    "gui.settledlands.tip448.pr.nobreak_on": "Состояние: ВКЛ.\nНа время волны на игроков в радиусе накладывается утомление копания, защищающее блоки арены от ломания.",
    "gui.settledlands.tip448.pr.nobreak_off": "Состояние: ВЫКЛ.\nИгроки могут свободно ломать блоки вокруг спавнера во время боя (эффект защиты не накладывается).",
    "gui.settledlands.tip448.pr.destruct_on": "Состояние: ВКЛ.\nПосле указанного числа волн спавнер с заданной вероятностью (%) саморазрушится после зачистки.",
    "gui.settledlands.tip448.pr.destruct_off": "Состояние: ВЫКЛ.\nСпавнер остаётся на месте вечно и снова запускает волны после окончания отката.",

    # ---- >2s tooltips: Trial module lifecycle & pickers ----
    "gui.settledlands.tip448.mod.unlink_state": "Сейчас модуль ПОДКЛЮЧЁН к хабу.\nНажмите, чтобы отсоединить провод от слота хаба: тогда хаб временно вернётся к настройкам по умолчанию для этого раздела.",
    "gui.settledlands.tip448.mod.attach_state": "Сейчас модуль НЕ ПОДКЛЮЧЁН.\nНажмите, чтобы автоматически протянуть цветной провод к нужному слоту центрального хаба.",
    "gui.settledlands.tip448.mod.delete": "Удалить этот модуль из графа. При необходимости его всегда можно вернуть кнопкой «Восстановить модули» на хабе.",
    "gui.settledlands.tip448.mod.add_mob_cat": "Открыть каталог всех сущностей игры и сразу добавить выбранного моба в пул спавна.",
    "gui.settledlands.tip448.mod.open_mob_list": "Открыть полный табличный редактор пула мобов (прокрутка списка, веса, удаление и поиск).",
    "gui.settledlands.tip448.mod.add_rew_cat": "Открыть каталог предметов игры и добавить новую награду в таблицу выпадения.",
    "gui.settledlands.tip448.mod.open_rew_list": "Открыть полный табличный редактор наград с настройкой количества, веса и шанса %.",
    "gui.settledlands.tip448.mod.pick_mob": "Открыть визуальный список мобов с поиском и заменить существо в этой строке.",
    "gui.settledlands.tip448.mod.pick_reward": "Открыть визуальный каталог предметов с поиском и заменить награду в этой строке.",
    "gui.settledlands.tip448.mod.remove_entry": "Удалить эту строку из списка.",
    "gui.settledlands.tip448.mod.weight_mob": "Относительный вес появления этого моба. Чем больше вес по сравнению с остальными, тем чаще он выпадает.",
    "gui.settledlands.tip448.mod.reward_box": "Числовой параметр этой строки награды (вес, минимум/максимум штук или шанс в %).",

    # ---- >2s tooltips: Trial hub & control node ----
    "gui.settledlands.tip448.hub.restore_modules": "Находит свободные модули или создаёт недостающие из 8 слотов и автоматически подключает их проводами к хабу. Уже подключённые модули не перезаписываются.",
    "gui.settledlands.tip448.hub.clear_sockets": "Отсоединяет все провода событий справа от хаба.",
    "gui.settledlands.tip448.tc.configure": "Открывает полное окно настройки параметров триал-спавнера (4 вкладки: Основное, Зловещее, Мобы, Награды).",
    "gui.settledlands.tip448.tc.kill_yes": "Состояние: Убивать мобов = ДА.\nПри остановке спавнера все живые мобы, призванные этим спавнером, будут немедленно уничтожены.",
    "gui.settledlands.tip448.tc.kill_no": "Состояние: Убивать мобов = НЕТ.\nСпавнер прекратит призыв новых врагов, но уже появившиеся мобы останутся в мире.",
    "gui.settledlands.tip448.tc.here": "Заполняет поля координат относительным «~ ~ ~» (точка самого NPC/блока, запускающего граф).",
    "gui.settledlands.tip448.tc.coords": "Мировые координаты блока trialtuner:trial_spawner (поддерживаются относительные смещения вида ~5 ~ ~-3).",

    # ---- >2s tooltips: TrialControl op dropdown ----
    "gui.settledlands.tip448.op.start": "Принудительно активирует триал-спавнер и запускает новую волну (если он не на откате).",
    "gui.settledlands.tip448.op.config": "Записывает в блок спавнера указанную конфигурацию без немедленного запуска волны.",
    "gui.settledlands.tip448.op.config_start": "Применяет сохранённую конфигурацию к блоку спавнера и сразу же запускает испытание.",
    "gui.settledlands.tip448.op.stop": "Принудительно останавливает текущее активное испытание на спавнере.",
    "gui.settledlands.tip448.op.reset_cd": "Мгновенно сбрасывает таймер отката (кулдауна) спавнера, чтобы его можно было активировать снова.",
    "gui.settledlands.tip448.op.destroy": "Останавливает спавнер и удаляет блок спавнера испытаний из мира.",

    # ---- >2s tooltips: Trial scalar fields (shared by modules + TrialControlConfigScreen446) ----
    "gui.settledlands.tip448.tf.act_range": "Радиус активации (1–64 бл.): расстояние от спавнера, на котором он замечает игроков и начинает испытание.",
    "gui.settledlands.tip448.tf.spawn_range": "Радиус спавна (1–16 бл.): максимальная дистанция от блока, где могут появляться мобы.",
    "gui.settledlands.tip448.tf.total_mobs": "Базовое количество мобов (1–256): сколько врагов нужно победить за волну при 1 игроке.",
    "gui.settledlands.tip448.tf.per_player_total": "Бонус мобов за игрока (0–64): сколько врагов добавляется к цели волны за каждого лишнего игрока.",
    "gui.settledlands.tip448.tf.sim_mobs": "Макс. одновременно (1–64): сколько мобов из этого спавнера может находиться на арене в один момент.",
    "gui.settledlands.tip448.tf.per_player_sim": "Лимит на игрока (0–32): прибавка к одновременному лимиту мобов за каждого дополнительного игрока.",
    "gui.settledlands.tip448.tf.batch": "Размер пачки (1–8): сколько мобов спавнер выпускает за одну вспышку призыва.",
    "gui.settledlands.tip448.tf.min_delay": "Мин. пауза (тики, 20 = 1 сек): минимальное время ожидания перед следующей пачкой спавна.",
    "gui.settledlands.tip448.tf.max_delay": "Макс. пауза (тики, 20 = 1 сек): максимальное время ожидания перед следующей пачкой спавна.",
    "gui.settledlands.tip448.tf.cooldown": "Перезарядка (тики, 36000 = 30 мин): время отдыха спавнера после победы перед новым запуском.",
    "gui.settledlands.tip448.tf.ominous_mult": "Множитель мобов (1.0–4.0): во сколько раз увеличивается сила/число мобов в зловещем режиме.",
    "gui.settledlands.tip448.tf.ominous_interval": "Интервал угроз (тики): как часто на арене срабатывают зловещие эффекты (160 т = 8 сек).",
    "gui.settledlands.tip448.tf.nobreak_r": "Радиус защиты (1–64 бл.): на какой дистанции игроки получают защиту блоков от ломки.",
    "gui.settledlands.tip448.tf.nobreak_s": "Длительность защиты (сек): на сколько секунд продлевается запрет ломания блоков на волне.",
    "gui.settledlands.tip448.tf.nobreak_amp": "Уровень утомления (0–3): сила эффекта Mining Fatigue (3 — практически полностью блокирует копание).",
    "gui.settledlands.tip448.tf.destruct_waves": "Волн до самосноса (1–100): после скольких пройденных волн спавнер сможет самоуничтожиться.",
    "gui.settledlands.tip448.tf.destruct_chance": "Шанс самосноса (0–100%): вероятность, что спавнер разрушится по завершении указанного числа волн.",

    # ---- >2s tooltips: TrialControlConfigScreen tabs & controls ----
    "gui.settledlands.tip448.cfg.tab_general": "Основные настройки режима, радиусов, размеров волны, задержек и защиты от ломки.",
    "gui.settledlands.tip448.cfg.tab_ominous": "Настройки зловещего режима: множитель, конверсия ключей и опасные эффекты арены.",
    "gui.settledlands.tip448.cfg.tab_mobs": "Список существ для призыва и их относительные шансы появления.",
    "gui.settledlands.tip448.cfg.tab_rewards": "Таблица наград: предметы, разброс количества, вес и шанс выпадения %.",
    "gui.settledlands.tip448.cfg.save_close": "Применяет все введённые изменения к узлу спавнера и возвращает в редактор графа.",
    "gui.settledlands.tip448.cfg.cancel": "Закрывает окно без сохранения внесённых здесь правок.",
    "gui.settledlands.tip448.cfg.reset": "Сбрасывает все параметры всех 4 вкладок к стандартным заводским значениям.",
    "gui.settledlands.tip448.cfg.add_mob": "Добавляет новую строку моба (по умолчанию зомби) в конец списка.",
    "gui.settledlands.tip448.cfg.add_reward": "Добавляет новую строку предмета (по умолчанию изумруд) в конец таблицы наград.",
    "gui.settledlands.tip448.cfg.mob_id": "Технический ID сущности (например minecraft:breeze или из мода).",
    "gui.settledlands.tip448.cfg.rew_id": "Технический ID предмета награды (например minecraft:trial_key).",
    "gui.settledlands.tip448.cfg.rew_min": "Минимальное количество штук этого предмета при выпадении (1–64).",
    "gui.settledlands.tip448.cfg.rew_max": "Максимальное количество штук этого предмета при выпадении (1–64).",
    "gui.settledlands.tip448.cfg.rew_weight": "Вес предмета при случайном выборе внутри пула (чем больше, тем чаще).",
    "gui.settledlands.tip448.cfg.rew_chance": "Процентный шанс выпадения этой награды при ролле (0–100%).",
    "gui.settledlands.tip448.cfg.picker_back": "Вернуться обратно к настройкам без выбора элемента.",
    "gui.settledlands.tip448.cfg.picker_search": "Фильтровать каталог по русскому/английскому названию или техническому ID.",
    "gui.settledlands.tip448.cfg.done": "Сохранить изменения списка прямо в модульный узел и вернуться к графу.",

    # ---- >2s tooltips: Door, Chest & Lockpicking sliders ----
    "gui.settledlands.tip448.door.sealed": "Состояние: ЗАКРЫТА.\nДверь нельзя открыть обычным нажатием — игроку придётся пройти мини-игру взлома замка.",
    "gui.settledlands.tip448.door.unsealed": "Состояние: ОТКРЫТА.\nДверь работает как самая обычная дверь Minecraft без замка и взлома.",
    "gui.settledlands.tip448.chest.locked": "Состояние: ПОД ЗАМКОМ.\nСундук закрыт на взлом — обычный клик запускает мини-игру, а содержимое защищено до открытия.",
    "gui.settledlands.tip448.chest.unlocked": "Состояние: ОТКРЫТ.\nСундук открывается обычным нажатием без мини-игры взлома.",
    "gui.settledlands.tip448.hack.illegal_on": "Состояние: НЕЗАКОННЫЙ ВЗЛОМ = ДА.\nВзлом этого объекта считается преступлением: ближайшие стражники/NPC заметят нарушителя и отреагируют.",
    "gui.settledlands.tip448.hack.illegal_off": "Состояние: ЗАКОННЫЙ / БЕЗ ШТРАФА.\nПопытки взлома этого объекта не привлекают внимание стражи и не снижают репутацию.",
    "gui.settledlands.tip448.hack.slider_keys": "Сколько клавиш (от 4 до 10) потребуется последовательно нажать или удержать в мини-игре взлома.",
    "gui.settledlands.tip448.hack.slider_speed": "Как часто (в секундах, 1–20) сменяется активная комбинация клавиш при взломе.",
    "gui.settledlands.tip448.hack.slider_diff_1": "Текущая сложность 1 (Лёгкая): используются только клавиши верхнего буквенного ряда (Q–P / Й–З).",
    "gui.settledlands.tip448.hack.slider_diff_2": "Текущая сложность 2 (Средняя): к верхнему ряду добавляется средний ряд клавиатуры (A–L / Ф–Д).",
    "gui.settledlands.tip448.hack.slider_diff_3": "Текущая сложность 3 (Сложная): задействованы все три ряда клавиатуры (Q–M / Й–Б).",

    # ---- >2s tooltips: Pursuit, Teleport, LootPool, BlockName ----
    "gui.settledlands.tip448.pursuit.type_npc": "Режим цели: ДРУГОЙ NPC.\nПреследователь будет искать и бежать к указанному NPC по его идентификатору или имени.",
    "gui.settledlands.tip448.pursuit.type_player": "Режим цели: ИГРОК.\nПреследователь выберет целью игрока (по нику, %player% или через синий пин).",
    "gui.settledlands.tip448.pursuit.stop_on": "Режим: ЗАВЕРШИТЬ ПРЕСЛЕДОВАНИЕ.\nВместо старта погони этот узел полностью прекращает текущее преследование у NPC.",
    "gui.settledlands.tip448.pursuit.stop_off": "Режим: НАЧАТЬ ПРЕСЛЕДОВАНИЕ.\nNPC начнёт преследовать цель по координатам с заданной дистанцией и скоростью.",
    "gui.settledlands.tip448.pursuit.val_npc": "ID или имя целевого NPC. Оставьте пустым, если цель приходит по синему проводу.",
    "gui.settledlands.tip448.pursuit.val_player": "Ник игрока или %player%. Можно оставить пустым, если подключён синий пин цели.",
    "gui.settledlands.tip448.pursuit.keep": "Дистанция в блоках, на которой NPC останавливается около цели (0 — вплотную).",
    "gui.settledlands.tip448.pursuit.catch_up": "Дистанция отставания, при превышении которой NPC резко ускоряется, чтобы не потерять цель.",
    "gui.settledlands.tip448.pursuit.speed": "Коэффициент ускорения NPC во время рывка за отставшей целью.",
    "gui.settledlands.tip448.tp.coords": "Координаты назначения X Y Z (можно писать ~ для смещения от текущей позиции, например ~ ~2 ~5).",
    "gui.settledlands.tip448.lp.rarity": "Максимальная редкость предметов из пула (ограничивает выпадение особо редкого лута; 0 — без ограничения).",
    "gui.settledlands.tip448.lp.weight": "Ограничение суммарного веса предметов (помогает контролировать ценность сундука).",
    "gui.settledlands.tip448.lp.rolls": "Сколько попыток выбора предметов (роллов) будет сделано при заполнении этого сундука.",
    "gui.settledlands.tip448.blk.name": "Название блока-триггера: отображается на карточке и помогает отличать разные триггеры в мире.",
    "gui.settledlands.tip448.drop.world": "Измерение (верхний мир, Нижний мир, Край или модовое измерение), в котором выполняется действие.",
    "gui.settledlands.tip448.drop.loot_pool": "Выберите готовый JSON-файл пула добычи. Нажмите на шестерёнку рядом, чтобы отредактировать его предметы.",
    "gui.settledlands.tip448.drop.presets": "Быстрая вставка готовой серверной команды или действия в триггер вместо ручного ввода.",
    "gui.settledlands.tip448.drop.target": "Кого отслеживает или подхватывает проверка в указанном радиусе.",
    "gui.settledlands.tip448.drop.damage": "Фильтр источника урона по NPC: любой урон, только от игрока, только от монстра или не от игрока.",
    "gui.settledlands.tip448.drop.time": "Выберите готовое время суток (Рассвет, Полдень, Закат, Полночь) или свой тик 0–23999.",
    "gui.settledlands.tip448.drop.anim": "Выбор анимации модели из ресурсов мода для проигрывания у этого NPC.",
    "gui.settledlands.tip448.drop.bone": "Часть тела/кость модели, к которой применяется анимация (или вся модель целиком).",
    "gui.settledlands.tip448.drop.cutscene": "Выбор сохранённой катсцены камеры для запуска при срабатывании этого узла.",
    "gui.settledlands.tip448.drop.mobility": "Имя группы точек патрулирования из настроек маршрута NPC.",
    "gui.settledlands.tip448.drop.block_color": "Цвет шапки карточки «Имя блока» для удобной визуальной пометки.",

    # ---- >2s tooltips: Var ops & Command presets ----
    "gui.settledlands.tip448.varop.0": "Равно (==): истина, если текущее значение переменной точно совпадает с порогом.",
    "gui.settledlands.tip448.varop.1": "Не равно (!=): истина при любом значении, отличном от указанного.",
    "gui.settledlands.tip448.varop.2": "Больше (>): истина, если переменная строго больше заданного числа.",
    "gui.settledlands.tip448.varop.3": "Больше или равно (>=): истина, если переменная не меньше порога.",
    "gui.settledlands.tip448.varop.4": "Меньше (<): истина, если переменная строго меньше заданного числа.",
    "gui.settledlands.tip448.varop.5": "Меньше или равно (<=): истина, если переменная не превышает порог.",
    "gui.settledlands.tip448.preset.0": "Раскрывающийся список готовых шаблонов команд для быстрой подстановки.",
    "gui.settledlands.tip448.preset.1": "Включает проигрывание музыкальной темы для игрока или рядом с NPC.",
    "gui.settledlands.tip448.preset.2": "Запускает режим сопровождения (эскорт) игрока через триггер.",
    "gui.settledlands.tip448.preset.3": "Подставляет шаблон команды /give %player% для выдачи предмета.",
    "gui.settledlands.tip448.preset.4": "Изменяет уровень репутации/отношения игрока к этому NPC.",
    "gui.settledlands.tip448.preset.5": "Открывает интерфейс торговли с настроенными товарами NPC.",
    "gui.settledlands.tip448.preset.6": "Принудительно закрывает текущее окно диалога у игрока.",
    "gui.settledlands.tip448.preset.7": "Отправляет игрока в специальное измерение/сцену Чистилища.",
    "gui.settledlands.tip448.preset.8": "Проигрывает указанный звуковой эффект (/playsound).",
    "gui.settledlands.tip448.preset.9": "Выводит системное сообщение в чат (/say) с поддержкой %player%.",

    # ---- >2s tooltips: Stock node toggles & fields ----
    "gui.settledlands.tip448.stk.local_on": "Состояние: ЛОКАЛЬНАЯ = ДА.\nЗначение этой переменной хранится отдельно для каждого игрока (диалоги игроков не мешают друг другу).",
    "gui.settledlands.tip448.stk.local_off": "Состояние: ОБЩАЯ ПЕРЕМЕННАЯ.\nЗначение общее для всех игроков на сервере/у этого NPC (изменил один — видят все).",
    "gui.settledlands.tip448.stk.shared_on": "Состояние: ОБЩИЕ ОЧКИ = ДА.\nРеплика проверяет глобальный счётчик очков, а не личные очки игрока.",
    "gui.settledlands.tip448.stk.shared_off": "Состояние: ЛИЧНЫЕ ОЧКИ.\nПроверяются очки и отношение конкретного игрока, ведущего диалог.",
    "gui.settledlands.tip448.stk.repeat_on": "Состояние: ПОВТОР = ЦИКЛ.\nТаймер будет срабатывать снова и снова каждые N секунд, пока активен.",
    "gui.settledlands.tip448.stk.repeat_off": "Состояние: ОДНОКРАТНО.\nТаймер подождёт указанное число секунд, сработает один раз и остановится.",
    "gui.settledlands.tip448.stk.escort_on": "Состояние: ЭСКОРТ = ВКЛ.\nNPC пойдёт к точке только пока игрок держится рядом, дожидаясь его.",
    "gui.settledlands.tip448.stk.escort_off": "Состояние: ОБЫЧНОЕ ДВИЖЕНИЕ.\nNPC самостоятельно направляется в указанную точку без ожидания игрока.",
    "gui.settledlands.tip448.stk.cutline_on": "Состояние: РЕПЛИКА КАТСЦЕНЫ = ВКЛ.\nТекст показывается в формате субтитров катсцены без блокирующего окна диалога.",
    "gui.settledlands.tip448.stk.cutline_off": "Состояние: ОБЫЧНАЯ РЕПЛИКА.\nПоказывается стандартное диалоговое окно NPC с вариантами ответов.",
    "gui.settledlands.tip448.stk.anim_loop_on": "Состояние: ПОВТОР АНИМАЦИИ = ЦИКЛ.\nАнимация зациклится и будет играть бесконечно до смены состояния.",
    "gui.settledlands.tip448.stk.anim_loop_off": "Состояние: 1 РАЗ.\nАнимация проиграется целиком один раз и вернётся в позу покоя.",
    "gui.settledlands.tip448.stk.entry": "Отмечает эту реплику как стартовую (точку входа) — с неё начнётся разговор при клике по NPC.",
    "gui.settledlands.tip448.stk.add_variant": "Добавляет альтернативный вариант текста реплики (порог отношения или случайный выбор).",
    "gui.settledlands.tip448.stk.del_variant": "Удаляет последний дополнительный вариант текста этой реплики.",
    "gui.settledlands.tip448.stk.cutscene_studio": "Открывает студию записи и редактирования ключевых точек камеры катсцены.",
    "gui.settledlands.tip448.stk.cutscene_del": "Удаляет выбранную катсцену (нажмите повторно для подтверждения).",
    "gui.settledlands.tip448.stk.unlink_if": "Отключает условие If, привязанное ко входу этого узла.",

    "gui.settledlands.tip448.fld.action": "Серверная команда (/give, /tp) или спец-действие trigger:<имя>. Работают подстановки %player% и %target%.",
    "gui.settledlands.tip448.fld.trigger_cmd": "Команда или триггер, который запускает либо отрабатывает этот узел.",
    "gui.settledlands.tip448.fld.var_name": "Имя числовой переменной (латиницей/цифрами), используемой для чтения или записи.",
    "gui.settledlands.tip448.fld.var_delta": "На сколько изменить переменную при проходе импульса (положительное или отрицательное число).",
    "gui.settledlands.tip448.fld.var_compare": "Эталонное число, с которым узел If сравнивает значение переменной.",
    "gui.settledlands.tip448.fld.check_radius": "Радиус обнаружения (в блоках) вокруг NPC или блока-триггера.",
    "gui.settledlands.tip448.fld.check_cooldown": "Минимальная пауза (в секундах) между повторными срабатываниями проверки.",
    "gui.settledlands.tip448.fld.min_damage": "Минимальный порог урона за удар, при котором сработает узел.",
    "gui.settledlands.tip448.fld.time_ticks": "Точный тик игровых суток от 0 (рассвет) до 23999 (перед следующим рассветом).",
    "gui.settledlands.tip448.fld.timer_interval": "Время задержки или периода повторения таймера в секундах.",
    "gui.settledlands.tip448.fld.radius": "Радиус поиска цели (в блоках) для передачи ссылки по синему пину.",
    "gui.settledlands.tip448.fld.quest_title": "Название задания, которое увидит игрок в журнале квестов.",
    "gui.settledlands.tip448.fld.min_relation": "Минимальное отношение (или очки) игрока, при котором этот вариант доступен.",
    "gui.settledlands.tip448.fld.max_relation": "Максимальное отношение, выше которого этот вариант скрывается.",
    "gui.settledlands.tip448.fld.move_coords": "Целевые координаты X Y Z в мире (поддерживаются относительные ~X ~Y ~Z).",
    "gui.settledlands.tip448.fld.move_speed": "Скорость передвижения NPC (обычно от 0.6 до 2.0).",
    "gui.settledlands.tip448.fld.random_range": "Целочисленный диапазон для жеребьёвки случайного выхода (например 1-3).",
    "gui.settledlands.tip448.fld.random_outputs": "Количество выходных контактов справа на карточке Случайность.",
    "gui.settledlands.tip448.fld.text": "Текст, который отобразится на карточке и в окне диалога у игрока.",
    "gui.settledlands.tip448.fld.variant": "Дополнительный вариант текста при другом пороге отношения или случайном ответе.",
    "gui.settledlands.tip448.fld.relation": "Сколько единиц отношения добавить или отнять при выборе этого варианта.",
    "gui.settledlands.tip448.fld.points": "Имя кастомной шкалы очков (если не используется стандартная шкала отношения).",
    "gui.settledlands.tip448.fld.min_points": "Минимальное значение выбранных очков для показа этого варианта.",
    "gui.settledlands.tip448.fld.max_points": "Максимальное значение выбранных очков для показа этого варианта.",

    # ---- >2s tooltips: Pins & Sockets ----
    "gui.settledlands.tip448.pin.slot_mode": "Вход слота 0 (Режим): принимает модуль режима и масштабирования от числа игроков.",
    "gui.settledlands.tip448.pin.slot_radius": "Вход слота 1 (Радиус): принимает модуль радиуса активации и зоны спавна.",
    "gui.settledlands.tip448.pin.slot_wave": "Вход слота 2 (Волна): принимает модуль числа мобов, лимита и размера пачки.",
    "gui.settledlands.tip448.pin.slot_timing": "Вход слота 3 (Тайминги): принимает модуль задержек между пачками и отката.",
    "gui.settledlands.tip448.pin.slot_mobs": "Вход слота 4 (Пул мобов): принимает модуль списка мобов и их весов.",
    "gui.settledlands.tip448.pin.slot_rewards": "Вход слота 5 (Награды): принимает модуль таблицы наград после победы.",
    "gui.settledlands.tip448.pin.slot_ominous": "Вход слота 6 (Зловещее): принимает модуль зловещих усилений, угроз и ключей.",
    "gui.settledlands.tip448.pin.slot_protect": "Вход слота 7 (Защита): принимает модуль защиты арены и саморазрушения.",
    "gui.settledlands.tip448.pin.slot_wired": "Слот ПОДКЛЮЧЁН: используются параметры привязанного модуля.",
    "gui.settledlands.tip448.pin.slot_default": "Слот ПУСТ: сейчас действуют стандартные значения (подключите модуль или нажмите «Восстановить модули»).",
    "gui.settledlands.tip448.pin.trial_start": "Событие «Старт волны»: срабатывает, когда триал-спавнер переходит в активный бой.",
    "gui.settledlands.tip448.pin.trial_clear": "Событие «Зачистка»: срабатывает при победе над всеми мобами волны и переходе в откат.",
    "gui.settledlands.tip448.pin.trial_destroy": "Событие «Уничтожение»: срабатывает, если блок спавнера разрушен игроком или самосносом.",
    "gui.settledlands.tip448.pin.trial_player": "Синий пин «Игрок»: передаёт ссылку на игрока-активатора (заполняет %target% в подключённых узлах).",
    "gui.settledlands.tip448.pin.mod_out_wired": "Выход модуля ПОДКЛЮЧЁН к хабу. Кликните СКМ по проводу или перетяните к другому хабу/слоту.",
    "gui.settledlands.tip448.pin.mod_out_free": "Выход модуля СВОБОДЕН. Перетяните ЛКМ от этого ромба на входной слот хаба (или нажмите «Подключить» справа).",
    "gui.settledlands.tip448.pin.door_hack": "Событие «Взлом»: импульс срабатывает сразу при успешном решении мини-игры замка.",
    "gui.settledlands.tip448.pin.door_open": "Событие «Открытие»: импульс срабатывает при каждом открытии двери.",
    "gui.settledlands.tip448.pin.door_who": "Синий пин «Кто взломал»: передаёт игрока, открывшего или взломавшего эту дверь.",
    "gui.settledlands.tip448.pin.chest_open": "Событие «Открытие»: импульс при открытии сундука игроком.",
    "gui.settledlands.tip448.pin.chest_hack": "Событие «Взлом»: импульс при успешном взломе запертого сундука.",
    "gui.settledlands.tip448.pin.chest_who": "Синий пин «Кто открыл»: передаёт игрока, взаимодействующего с сундуком.",
    "gui.settledlands.tip448.pin.if_true": "Выход «Истина (True)»: цепочка пойдёт по этой ветке, если сравнение в узле If оказалось верным.",
    "gui.settledlands.tip448.pin.if_false": "Выход «Ложь (False)»: цепочка пойдёт сюда, если условие в узле If не выполнилось.",
    "gui.settledlands.tip448.pin.var_input": "Синий вход ссылки/данных: подключите сюда источник (например «Цель в радиусе» или «Игрок» со спавнера).",
    "gui.settledlands.tip448.pin.value_source": "Синий выход ссылки: тяните отсюда к синему входу цели, преследования, телепорта или условия.",
    "gui.settledlands.tip448.pin.in_title": "Входной контакт",
    "gui.settledlands.tip448.pin.in_body": "Принимает сигнал от предыдущего узла. Протяните провод с правого выхода другой карточки.",
    "gui.settledlands.tip448.pin.out_title": "Выходной контакт",
    "gui.settledlands.tip448.pin.out_body": "Передаёт сигнал дальше. Потяните ЛКМ к входу другого узла (СКМ по проводу — удалить).",

    # ---- >2s tooltips: World cards (Auto-AI & Stats) ----
    "gui.settledlands.tip448.ai.wander_on": "Состояние: Свободная ходьба ВКЛ.\nКогда NPC свободен, он сам бродит по окрестности в пределах заданного радиуса.",
    "gui.settledlands.tip448.ai.wander_off": "Состояние: Свободная ходьба ВЫКЛ.\nNPC будет стоять на своей точке и не станет самостоятельно уходить без команды.",
    "gui.settledlands.tip448.ai.attack_on": "Состояние: Автоатака врагов ВКЛ.\nNPC автоматически вступает в бой с враждебными мобами, вошедшими в радиус агрессии.",
    "gui.settledlands.tip448.ai.attack_off": "Состояние: Автоатака ВЫКЛ.\nNPC не нападает на враждебных мобов сам, пока ему не задан боевой скрипт.",
    "gui.settledlands.tip448.ai.wander_rad": "Максимальный радиус (в блоках), в пределах которого NPC гуляет от своей базовой точки.",
    "gui.settledlands.tip448.ai.aggro_rad": "Радиус агрессии (в блоках): на каком расстоянии NPC замечает враждебных мобов для атаки.",
    "gui.settledlands.tip448.stats.randomize": "Случайно перебирает внешность, имя и вариант модели NPC одним нажатием.",
    "gui.settledlands.tip448.stats.prof_title": "Профессия NPC",
    "gui.settledlands.tip448.stats.profession": "Нажмите, чтобы выбрать профессию/роль NPC из выпадающего списка.",
    "gui.settledlands.tip448.stats.style_title": "Пол и стиль модели",
    "gui.settledlands.tip448.stats.style": "Переключает пол модели и региональный стиль (например японский вариант внешности).",
    "gui.settledlands.tip448.stats.health": "Базовый запас здоровья (HP) этого NPC. Кнопки - / + меняют значение.",
    "gui.settledlands.tip448.stats.damage": "Базовый урон атак этого NPC в ближнем/дальнем бою.",
    "gui.settledlands.tip448.stats.speed": "Базовая скорость передвижения NPC по миру.",

    # ---- >2s tooltips: Node body hover (extra context, non-duplicating!) ----
    "gui.settledlands.tip448.node.auto_ai": "Встроенная мировая панель ИИ: настройки применяются напрямую ко всем вкладкам поведения этого NPC.",
    "gui.settledlands.tip448.node.stats": "Главная карточка NPC: её нельзя удалить — она задаёт базовые параметры и модель персонажа.",
    "gui.settledlands.tip448.node.mod_mode": "Выберите карточку, чтобы справа сменить режим испытания и масштаб от игроков.",
    "gui.settledlands.tip448.node.mod_radius": "Выберите карточку, чтобы настроить радиусы активации и спавна.",
    "gui.settledlands.tip448.node.mod_wave": "Выберите карточку, чтобы настроить общее число мобов, лимит и размер пачки.",
    "gui.settledlands.tip448.node.mod_timing": "Выберите карточку, чтобы настроить темп призыва и время перезарядки.",
    "gui.settledlands.tip448.node.mod_mobs": "Выберите карточку, чтобы редактировать список существ прямо справа или в каталоге.",
    "gui.settledlands.tip448.node.mod_rewards": "Выберите карточку, чтобы настроить предметы, количество, вес и шанс наград.",
    "gui.settledlands.tip448.node.mod_ominous": "Выберите карточку, чтобы задать зловещий множитель, экипировку, угрозы и ключи.",
    "gui.settledlands.tip448.node.mod_protect": "Выберите карточку, чтобы управлять запретом ломания блоков и авто-разрушением.",
    "gui.settledlands.tip448.node.mod_state_on": "Подключён к хабу: настройки передаются в триал-спавнер при сохранении.",
    "gui.settledlands.tip448.node.mod_state_off": "Не подключён к хабу: соедините выход справа со слотом хаба (или нажмите «Подключить»).",
    "gui.settledlands.tip448.node.trial_hub": "Подключено модулей: %d из 8.\nКаждый подключённый модуль переопределяет свой раздел настроек, остальные берут стандартные значения.",
    "gui.settledlands.tip448.node.trial_ctrl": "Нажмите на карточку, чтобы изменить координаты целевого блока спавнера или открыть полное окно настроек.",
    "gui.settledlands.tip448.node.teleport": "Подключите вход слева и задайте X/Y/Z (и измерение) в панели инспектора справа.",
    "gui.settledlands.tip448.node.loot_pool": "Кликните по шестерёнке в правом верхнем углу карточки, чтобы открыть визуальный редактор предметов пула.",
    "gui.settledlands.tip448.node.pursuit_start": "Режим старта погони: NPC начнёт преследовать цель по имени или по синему пину.",
    "gui.settledlands.tip448.node.pursuit_stop": "Режим остановки погони: сбрасывает текущую цель преследования у NPC.",
    "gui.settledlands.tip448.node.block_name": "Карточка имени блока-триггера: нажмите, чтобы переименовать блок и поменять его цвет.",
    "gui.settledlands.tip448.node.door_sealed": "Дверь сейчас ЗАКРЫТА НА ВЗЛОМ. Выходы справа передадут импульс и игрока-взломщика в цепочку графа.",
    "gui.settledlands.tip448.node.door_open": "Дверь сейчас ОТКРЫТА ДЛЯ ОБЫЧНОГО ВХОДА. Нажмите по карточке, чтобы включить замок.",
    "gui.settledlands.tip448.node.chest_locked": "Сундук сейчас ЗАКРЫТ НА ЗАМОК. Взлом запустит цепочку справа и передаст игрока по синему пину.",
    "gui.settledlands.tip448.node.chest_open": "Сундук сейчас ОТКРЫТ ОБЫЧНЫМ КЛИКОМ. Подключите справа цепочку реакции или пул лута.",
    "gui.settledlands.tip448.node.cutscene_line": "Включён режим реплики катсцены: текст пойдёт в сцену, а не в обычное диалоговое окно.",
    "gui.settledlands.tip448.node.timer_loop": "Циклический таймер: срабатывает каждые %d сек. Нажмите на карточку, чтобы изменить режим.",
    "gui.settledlands.tip448.node.timer_once": "Одноразовый таймер: задержка %d сек перед передачей сигнала дальше.",
    "gui.settledlands.tip448.node.n_0": "Подсказка: задайте начальную реплику кнопкой справа и добавляйте варианты текста под разные отношения.",
    "gui.settledlands.tip448.node.n_1": "Подсказка: соедините реплику NPC с этой кнопкой, а её выход — со следующей репликой или действием.",
    "gui.settledlands.tip448.node.n_2": "Подсказка: используйте шаблоны команд справа и подстановки %player% / %target%.",
    "gui.settledlands.tip448.node.n_3": "Подсказка: зелёный верхний выход сработает при Истине, нижний при Лжи; можно читать переменную напрямую или с синего пина.",
    "gui.settledlands.tip448.node.n_4": "Подсказка: локальные переменные хранятся отдельно для каждого игрока, общие — для всех.",
    "gui.settledlands.tip448.node.n_5": "Подсказка: событие проверяет сущностей в зоне и соблюдает кулдаун перед повторным запуском.",
    "gui.settledlands.tip448.node.n_6": "Подсказка: фильтр источника и порог минимального урона настраиваются в инспекторе справа.",
    "gui.settledlands.tip448.node.n_7": "Подсказка: используйте готовые пресеты времени (Рассвет/Полдень/Закат) или точный тик суток.",
    "gui.settledlands.tip448.node.n_8": "Подсказка: выберите задержку в секундах и однократный или циклический режим.",
    "gui.settledlands.tip448.node.n_9": "Подсказка: выберите анимацию модели, кость (например только голова или рука) и цикл повтора.",
    "gui.settledlands.tip448.node.n_10": "Подсказка: название задания и минимальный порог отношения задаются справа.",
    "gui.settledlands.tip448.node.n_11": "Подсказка: включите «Сопровождение», если NPC должен вести или ждать игрока по пути.",
    "gui.settledlands.tip448.node.n_12": "Подсказка: при достижении первой точки маршрута NPC передаст сигнал по правому проводу.",
    "gui.settledlands.tip448.node.n_13": "Подсказка: количество выходов и диапазон жеребьёвки настраиваются в правой панели.",
    "gui.settledlands.tip448.node.n_14": "Подсказка: нажмите «Студия…» в свойствах справа, чтобы снять или подправить камеру катсцены.",
    "gui.settledlands.tip448.node.n_15": "Подсказка: протяните провод от синего ромба справа к узлу «Преследование», «Телепорт» или «If».",
}

LANG_SET_EN = {
    "gui.settledlands.desc448.card.node_0": "Displays an NPC dialogue line to the player and advances flow.",
    "gui.settledlands.desc448.card.cutscene_line": "Plays a subtitle line inside a cutscene and advances the scene.",
    "gui.settledlands.desc448.card.node_1": "Shows a dialogue choice button with optional visibility thresholds.",
    "gui.settledlands.desc448.card.node_2": "Runs a server command or custom trigger action when pulsed.",
    "gui.settledlands.desc448.card.node_3": "Compares a variable against a value and splits True / False.",
    "gui.settledlands.desc448.card.node_4": "Modifies a numeric variable (per-player local or shared).",
    "gui.settledlands.desc448.card.node_5": "Fires when a matching entity enters the configured radius.",
    "gui.settledlands.desc448.card.node_6": "Fires when the NPC takes damage from a matching source.",
    "gui.settledlands.desc448.card.node_7": "Fires when the world reaches the configured time of day.",
    "gui.settledlands.desc448.card.node_8": "Delays or repeats execution on an interval in seconds.",
    "gui.settledlands.desc448.card.node_9": "Plays a model animation on the NPC (bone filter & loop).",
    "gui.settledlands.desc448.card.node_10": "Offers a quest option and runs the branch when chosen.",
    "gui.settledlands.desc448.card.node_11": "Moves the NPC to target coordinates (free walk or escort).",
    "gui.settledlands.desc448.card.node_12": "Assigns a patrol mobility group and signals at point 1.",
    "gui.settledlands.desc448.card.node_13": "Randomly routes execution across its configured outputs.",
    "gui.settledlands.desc448.card.node_14": "Plays the selected camera cutscene from the studio.",
    "gui.settledlands.desc448.card.node_15": "Finds a target in radius and outputs a blue reference pin.",
    "gui.settledlands.desc448.card.stats": "Configures base NPC stats, profession, and appearance.",
    "gui.settledlands.desc448.card.auto_ai": "Controls autonomous idle wandering and hostile auto-aggro.",
    "gui.settledlands.desc448.card.block_name": "Sets the trigger block display name and header color.",
    "gui.settledlands.desc448.card.door": "Configures door lock, hack difficulty, and open/hack events.",
    "gui.settledlands.desc448.card.door_signal": "Legacy door open signal node.",
    "gui.settledlands.desc448.card.door_target": "Legacy door hacker target reference node.",
    "gui.settledlands.desc448.card.chest": "Configures chest lock, hack difficulty, and loot events.",
    "gui.settledlands.desc448.card.trial_hub": "Trial Spawner hub: combines 8 modules and fires wave events.",
    "gui.settledlands.desc448.card.trial_ctrl": "Commands a world Trial Spawner (start, configure, stop, reset).",
    "gui.settledlands.desc448.card.teleport": "Teleports the player or target to coordinates and dimension.",
    "gui.settledlands.desc448.card.loot_pool": "Populates a chest from a weighted/rarity loot pool file.",
    "gui.settledlands.desc448.card.pursuit": "Starts chasing a target entity or cancels active pursuit.",
    "gui.settledlands.desc448.card.mod_mode": "Sets trial mode (Normal/Ominous/Special) and player scaling.",
    "gui.settledlands.desc448.card.mod_radius": "Defines player activation range and mob spawn radius.",
    "gui.settledlands.desc448.card.mod_wave": "Sets total wave mobs, arena cap, and spawn batch size.",
    "gui.settledlands.desc448.card.mod_timing": "Configures spawn batch delays and post-clear cooldown.",
    "gui.settledlands.desc448.card.mod_mobs": "Defines the spawner mob pool and relative spawn weights.",
    "gui.settledlands.desc448.card.mod_rewards": "Configures the reward loot table granted on wave clear.",
    "gui.settledlands.desc448.card.mod_ominous": "Configures ominous multiplier, gear, hazards, and keys.",
    "gui.settledlands.desc448.card.mod_protect": "Arena block-break protection and self-destruct after N waves.",
    "gui.settledlands.desc448.card.unknown": "Auxiliary logic graph node.",

    "gui.settledlands.tip448.top.undo": "Undo the most recent graph edit (Ctrl+Z).",
    "gui.settledlands.tip448.top.redo": "Redo the last undone graph edit (Ctrl+Y).",
    "gui.settledlands.tip448.top.tab_main": "Main dialogue and player interaction flow.",
    "gui.settledlands.tip448.top.tab_walk": "Separate graph used while following walk/patrol routes.",
    "gui.settledlands.tip448.top.tab_anim": "Global shared graph for ambient events and animations.",
    "gui.settledlands.tip448.ctx.copy": "Duplicates this node and its settings nearby on the canvas.",
    "gui.settledlands.tip448.ctx.delete": "Deletes this node and disconnects all attached wires.",

    "gui.settledlands.tip448.menu.node_0": "Create an NPC dialogue line (or cutscene subtitle line).",
    "gui.settledlands.tip448.menu.node_1": "Create a clickable player dialogue choice button.",
    "gui.settledlands.tip448.menu.node_2": "Create a trigger node that executes a command or action.",
    "gui.settledlands.tip448.menu.node_3": "Create an If condition node branching on True / False.",
    "gui.settledlands.tip448.menu.node_4": "Create a Variable node to modify numeric state.",
    "gui.settledlands.tip448.menu.node_5": "Create a radius-check event triggered by nearby entities.",
    "gui.settledlands.tip448.menu.node_6": "Create a damage-taken event node for the NPC.",
    "gui.settledlands.tip448.menu.node_7": "Create a time-of-day event node.",
    "gui.settledlands.tip448.menu.node_8": "Create a one-shot delay or repeating interval timer.",
    "gui.settledlands.tip448.menu.node_9": "Create a model animation playback node.",
    "gui.settledlands.tip448.menu.node_10": "Create a quest offer node.",
    "gui.settledlands.tip448.menu.node_11": "Create a move/escort navigation command node.",
    "gui.settledlands.tip448.menu.node_12": "Create a mobility patrol-group switch node.",
    "gui.settledlands.tip448.menu.node_13": "Create a random multi-output splitter node.",
    "gui.settledlands.tip448.menu.node_14": "Create a camera cutscene player node.",
    "gui.settledlands.tip448.menu.node_15": "Create a target-in-radius locator outputting a blue value pin.",
    "gui.settledlands.tip448.menu.node_40": "Door lock and event hub card.",
    "gui.settledlands.tip448.menu.node_50": "Chest lock and loot event hub card.",
    "gui.settledlands.tip448.menu.node_60": "Central Trial Spawner hub card.",
    "gui.settledlands.tip448.menu.pursuit": "Create a pursuit node to chase a target or stop chasing.",
    "gui.settledlands.tip448.menu.teleport": "Create a Teleport node moving a target to XYZ & dimension.",
    "gui.settledlands.tip448.menu.trial_ctrl": "Create a Trial Spawner controller node targeting world XYZ.",
    "gui.settledlands.tip448.menu.loot_pool": "Create a Loot Pool node filling the chest from a pool JSON.",

    "gui.settledlands.tip448.w.unlink": "Disconnects all outgoing wires from this node.",
    "gui.settledlands.tip448.w.delete": "Deletes this node and its wires (undoable via Ctrl+Z).",
    "gui.settledlands.tip448.w.loot_pool_gear": "Opens the visual loot pool item editor for the selected pool.",
    "gui.settledlands.tip448.world_item": "Use this dimension for the node coordinates.",
    "gui.settledlands.tip448.dropdown_item": "Click to select this option.",

    "gui.settledlands.tip448.mode.normal": "Current mode: NORMAL.\nStandard Trial Spawner waves with baseline mobs and standard reward table.",
    "gui.settledlands.tip448.mode.ominous": "Current mode: OMINOUS.\nMobs scale by x%s, receive enchanted armor (if enabled), arena hazards trigger periodically, and keys may convert to ominous keys.",
    "gui.settledlands.tip448.mode.special": "Current mode: SPECIAL.\nCustom trial challenge ruleset without automatic ominous conversion.",

    "gui.settledlands.tip448.cfg.scale_on": "State: ON.\nTotal and simultaneous mob caps scale up for each extra player in activation range.",
    "gui.settledlands.tip448.cfg.scale_off": "State: OFF.\nWave uses fixed base mob counts regardless of player count.",
    "gui.settledlands.tip448.om.equip_on": "State: ON.\nOminous mobs spawn with enhanced ominous armor and equipment.",
    "gui.settledlands.tip448.om.equip_off": "State: OFF.\nOminous mobs keep baseline gear without extra ominous armor.",
    "gui.settledlands.tip448.om.hazards_on": "State: ON.\nPeriodic arena hazards trigger during active ominous waves.",
    "gui.settledlands.tip448.om.hazards_off": "State: OFF.\nPeriodic ominous arena hazards are disabled.",
    "gui.settledlands.tip448.om.keys_on": "State: ON.\nDropped trial keys convert into ominous keys while ominous mode is active.",
    "gui.settledlands.tip448.om.keys_off": "State: OFF.\nRewards roll verbatim without key conversion.",
    "gui.settledlands.tip448.pr.nobreak_on": "State: ON.\nApplies Mining Fatigue inside the radius to protect arena blocks during combat.",
    "gui.settledlands.tip448.pr.nobreak_off": "State: OFF.\nPlayers can freely mine arena blocks during active waves.",
    "gui.settledlands.tip448.pr.destruct_on": "State: ON.\nAfter the configured wave count, the spawner may self-destruct at the set % chance.",
    "gui.settledlands.tip448.pr.destruct_off": "State: OFF.\nSpawner persists permanently and cycles after each cooldown.",

    "gui.settledlands.tip448.mod.unlink_state": "Module is LINKED to the hub.\nClick to disconnect it so the hub temporarily falls back to defaults for this slot.",
    "gui.settledlands.tip448.mod.attach_state": "Module is UNLINKED.\nClick to automatically wire this module to the matching hub slot.",
    "gui.settledlands.tip448.mod.delete": "Remove this module from the graph. You can recreate any missing slots via 'Restore modules' on the hub.",
    "gui.settledlands.tip448.mod.add_mob_cat": "Open the full entity catalog and add a mob straight into this pool.",
    "gui.settledlands.tip448.mod.open_mob_list": "Open the scrollable mob pool table editor with search and weights.",
    "gui.settledlands.tip448.mod.add_rew_cat": "Open the item catalog and add a new reward entry.",
    "gui.settledlands.tip448.mod.open_rew_list": "Open the full reward pool table editor (counts, weights, chance %).",
    "gui.settledlands.tip448.mod.pick_mob": "Pick a mob from the visual catalog to replace this entry.",
    "gui.settledlands.tip448.mod.pick_reward": "Pick an item from the visual catalog to replace this entry.",
    "gui.settledlands.tip448.mod.remove_entry": "Delete this entry row.",
    "gui.settledlands.tip448.mod.weight_mob": "Relative spawn weight (higher values appear proportionally more often).",
    "gui.settledlands.tip448.mod.reward_box": "Numeric parameter for this reward row (weight, min/max count, or chance %).",

    "gui.settledlands.tip448.hub.restore_modules": "Re-attaches free modules or creates any missing slots out of 8 and wires them to the hub without overwriting linked modules.",
    "gui.settledlands.tip448.hub.clear_sockets": "Clears outgoing event wires on the right side of the hub.",
    "gui.settledlands.tip448.tc.configure": "Opens the full 4-tab Trial Spawner configuration window.",
    "gui.settledlands.tip448.tc.kill_yes": "State: Kill mobs = YES.\nStopping the spawner immediately removes all active spawned mobs.",
    "gui.settledlands.tip448.tc.kill_no": "State: Kill mobs = NO.\nStops new spawns while leaving existing mobs alive.",
    "gui.settledlands.tip448.tc.here": "Sets coordinates to relative '~ ~ ~' (origin of the triggering block/NPC).",
    "gui.settledlands.tip448.tc.coords": "Target trialtuner:trial_spawner block XYZ coordinates (relative ~ offsets supported).",

    "gui.settledlands.tip448.op.start": "Activates the target Trial Spawner and starts a wave if off cooldown.",
    "gui.settledlands.tip448.op.config": "Writes this configuration into the target spawner without immediately starting it.",
    "gui.settledlands.tip448.op.config_start": "Applies this configuration to the spawner and immediately begins the trial.",
    "gui.settledlands.tip448.op.stop": "Force-stops the active trial wave on the target spawner.",
    "gui.settledlands.tip448.op.reset_cd": "Clears the target spawner's cooldown so it can activate again immediately.",
    "gui.settledlands.tip448.op.destroy": "Stops and removes the target Trial Spawner block from the world.",

    "gui.settledlands.tip448.tf.act_range": "Activation range (1-64 blocks): distance at which players trigger the spawner.",
    "gui.settledlands.tip448.tf.spawn_range": "Spawn radius (1-16 blocks): max horizontal offset where mobs can spawn.",
    "gui.settledlands.tip448.tf.total_mobs": "Base total mobs (1-256) required to clear a wave with 1 player.",
    "gui.settledlands.tip448.tf.per_player_total": "Extra mobs added to total wave target per additional player (0-64).",
    "gui.settledlands.tip448.tf.sim_mobs": "Base max simultaneous mobs allowed on the arena at once (1-64).",
    "gui.settledlands.tip448.tf.per_player_sim": "Extra simultaneous mob cap per additional player (0-32).",
    "gui.settledlands.tip448.tf.batch": "Number of mobs spawned together in each spawn burst (1-8).",
    "gui.settledlands.tip448.tf.min_delay": "Minimum delay between spawn bursts in ticks (20 ticks = 1 second).",
    "gui.settledlands.tip448.tf.max_delay": "Maximum delay between spawn bursts in ticks (20 ticks = 1 second).",
    "gui.settledlands.tip448.tf.cooldown": "Cooldown after clearing a wave in ticks (36000 ticks = 30 minutes).",
    "gui.settledlands.tip448.tf.ominous_mult": "Ominous mob power/count multiplier (1.0-4.0).",
    "gui.settledlands.tip448.tf.ominous_interval": "Ticks between ominous arena hazard triggers (160t = 8s).",
    "gui.settledlands.tip448.tf.nobreak_r": "Radius around spawner where players receive block-break protection (1-64).",
    "gui.settledlands.tip448.tf.nobreak_s": "Duration in seconds of the Mining Fatigue protection aura.",
    "gui.settledlands.tip448.tf.nobreak_amp": "Mining Fatigue amplifier (0-3; 3 strongly prevents block breaking).",
    "gui.settledlands.tip448.tf.destruct_waves": "Number of completed waves before self-destruct can roll (1-100).",
    "gui.settledlands.tip448.tf.destruct_chance": "Percent chance (0-100%) to destroy the spawner once wave count is reached.",

    "gui.settledlands.tip448.cfg.tab_general": "Mode, ranges, wave sizing, delays, cooldown, and arena protection.",
    "gui.settledlands.tip448.cfg.tab_ominous": "Ominous multiplier, key conversion, and arena hazard interval.",
    "gui.settledlands.tip448.cfg.tab_mobs": "Mob pool list and relative spawn weights.",
    "gui.settledlands.tip448.cfg.tab_rewards": "Reward loot table: items, count range, weight, and % chance.",
    "gui.settledlands.tip448.cfg.save_close": "Applies changes to the Trial Spawner node and returns to the graph.",
    "gui.settledlands.tip448.cfg.cancel": "Discards edits made in this window and returns to the graph.",
    "gui.settledlands.tip448.cfg.reset": "Resets all 4 tabs back to default configuration values.",
    "gui.settledlands.tip448.cfg.add_mob": "Appends a new mob entry (zombie by default) to the pool.",
    "gui.settledlands.tip448.cfg.add_reward": "Appends a new reward entry (emerald by default) to the table.",
    "gui.settledlands.tip448.cfg.mob_id": "Namespaced entity ID (e.g. minecraft:breeze).",
    "gui.settledlands.tip448.cfg.rew_id": "Namespaced reward item ID (e.g. minecraft:trial_key).",
    "gui.settledlands.tip448.cfg.rew_min": "Minimum stack size rolled for this reward (1-64).",
    "gui.settledlands.tip448.cfg.rew_max": "Maximum stack size rolled for this reward (1-64).",
    "gui.settledlands.tip448.cfg.rew_weight": "Relative selection weight within the reward pool.",
    "gui.settledlands.tip448.cfg.rew_chance": "Percentage roll chance for this entry (0-100%).",
    "gui.settledlands.tip448.cfg.picker_back": "Return to the previous view without picking.",
    "gui.settledlands.tip448.cfg.picker_search": "Filter catalog entries by display name or technical ID.",
    "gui.settledlands.tip448.cfg.done": "Save pool changes to the module node and return to the graph.",

    "gui.settledlands.tip448.door.sealed": "State: LOCKED.\nNormal clicks are blocked; players must solve the lockpicking minigame to open the door.",
    "gui.settledlands.tip448.door.unsealed": "State: UNLOCKED.\nDoor behaves like a regular Minecraft door with no minigame.",
    "gui.settledlands.tip448.chest.locked": "State: LOCKED.\nNormal clicks start the lockpicking minigame and protect chest contents.",
    "gui.settledlands.tip448.chest.unlocked": "State: UNLOCKED.\nChest opens normally with no lockpicking minigame.",
    "gui.settledlands.tip448.hack.illegal_on": "State: ILLEGAL HACK = YES.\nPicking this lock alerts nearby guards/NPCs and counts as a crime.",
    "gui.settledlands.tip448.hack.illegal_off": "State: LEGAL / SAFE.\nPicking this lock triggers no guard hostility or crime penalties.",
    "gui.settledlands.tip448.hack.slider_keys": "Number of keys (4-10) required in the lockpicking sequence.",
    "gui.settledlands.tip448.hack.slider_speed": "Interval in seconds (1-20) before an active lockpicking key cycles.",
    "gui.settledlands.tip448.hack.slider_diff_1": "Difficulty 1 (Easy): uses top keyboard letter row only (Q-P).",
    "gui.settledlands.tip448.hack.slider_diff_2": "Difficulty 2 (Medium): uses top and home keyboard rows (Q-L).",
    "gui.settledlands.tip448.hack.slider_diff_3": "Difficulty 3 (Hard): uses all three keyboard letter rows (Q-M).",

    "gui.settledlands.tip448.pursuit.type_npc": "Target type: NPC.\nChases the specified NPC ID or linked value source.",
    "gui.settledlands.tip448.pursuit.type_player": "Target type: PLAYER.\nChases the specified player name, %player%, or blue value pin.",
    "gui.settledlands.tip448.pursuit.stop_on": "Mode: STOP PURSUIT.\nCancels any active pursuit task on this NPC.",
    "gui.settledlands.tip448.pursuit.stop_off": "Mode: START PURSUIT.\nStarts chasing the target using the configured distance and speed.",
    "gui.settledlands.tip448.pursuit.val_npc": "Target NPC ID/name. Leave empty when wired from a blue value socket.",
    "gui.settledlands.tip448.pursuit.val_player": "Player name or %player%. Leave empty when wired from a blue value socket.",
    "gui.settledlands.tip448.pursuit.keep": "Distance in blocks at which the NPC stops near the target.",
    "gui.settledlands.tip448.pursuit.catch_up": "Distance threshold beyond which the NPC triggers catch-up sprinting.",
    "gui.settledlands.tip448.pursuit.speed": "Speed multiplier applied while catching up to a distant target.",
    "gui.settledlands.tip448.tp.coords": "Destination X Y Z coordinates (supports relative ~ offsets such as ~ ~2 ~5).",
    "gui.settledlands.tip448.lp.rarity": "Maximum item rarity allowed from the pool (0 = unlimited).",
    "gui.settledlands.tip448.lp.weight": "Total weight budget limit for generated items.",
    "gui.settledlands.tip448.lp.rolls": "Number of item selection rolls made when filling the chest.",
    "gui.settledlands.tip448.blk.name": "Display name shown on this trigger block's graph card.",
    "gui.settledlands.tip448.drop.world": "Target world dimension for this node's coordinates.",
    "gui.settledlands.tip448.drop.loot_pool": "Select a loot pool file. Click the gear button next to it to edit its contents.",
    "gui.settledlands.tip448.drop.presets": "Quickly fill a preset server command or built-in trigger.",
    "gui.settledlands.tip448.drop.target": "Entity filter matched within the radius.",
    "gui.settledlands.tip448.drop.damage": "Damage source filter (Any, Player, Monster, Non-player).",
    "gui.settledlands.tip448.drop.time": "Pick a preset time of day or enter a custom tick (0-23999).",
    "gui.settledlands.tip448.drop.anim": "Model animation clip to play on the NPC.",
    "gui.settledlands.tip448.drop.bone": "Skeleton bone filter (e.g. head/arm or entire model).",
    "gui.settledlands.tip448.drop.cutscene": "Saved camera cutscene to play when reached.",
    "gui.settledlands.tip448.drop.mobility": "Patrol/mobility group name configured on this NPC.",
    "gui.settledlands.tip448.drop.block_color": "Accent color for the Block Name card header.",

    "gui.settledlands.tip448.varop.0": "Equals (==): True when variable matches the target number.",
    "gui.settledlands.tip448.varop.1": "Not equal (!=): True when variable differs from the target number.",
    "gui.settledlands.tip448.varop.2": "Greater (>): True when variable is strictly above threshold.",
    "gui.settledlands.tip448.varop.3": "Greater/Equal (>=): True when variable meets or exceeds threshold.",
    "gui.settledlands.tip448.varop.4": "Less (<): True when variable is strictly below threshold.",
    "gui.settledlands.tip448.varop.5": "Less/Equal (<=): True when variable is at most threshold.",
    "gui.settledlands.tip448.preset.0": "Select a command template from the dropdown.",
    "gui.settledlands.tip448.preset.1": "Plays a music track for the player or NPC area.",
    "gui.settledlands.tip448.preset.2": "Starts escort navigation via command trigger.",
    "gui.settledlands.tip448.preset.3": "Inserts a /give %player% item template.",
    "gui.settledlands.tip448.preset.4": "Adjusts player-NPC relation score.",
    "gui.settledlands.tip448.preset.5": "Opens the NPC trade shop screen.",
    "gui.settledlands.tip448.preset.6": "Closes the active dialogue screen.",
    "gui.settledlands.tip448.preset.7": "Sends the player to the Purgatory event/dimension.",
    "gui.settledlands.tip448.preset.8": "Plays a sound effect (/playsound).",
    "gui.settledlands.tip448.preset.9": "Broadcasts a chat message (/say) supporting %player%.",

    "gui.settledlands.tip448.stk.local_on": "State: LOCAL = YES.\nStored separately per player so dialogues do not interfere.",
    "gui.settledlands.tip448.stk.local_off": "State: SHARED VARIABLE.\nShared globally across all players on the NPC/server.",
    "gui.settledlands.tip448.stk.shared_on": "State: SHARED POINTS = YES.\nEvaluates global shared points rather than per-player score.",
    "gui.settledlands.tip448.stk.shared_off": "State: PLAYER POINTS.\nEvaluates personal points/relation of the active player.",
    "gui.settledlands.tip448.stk.repeat_on": "State: REPEAT = LOOP.\nFires continuously every N seconds while active.",
    "gui.settledlands.tip448.stk.repeat_off": "State: ONE-SHOT.\nWaits N seconds, fires once, then stops.",
    "gui.settledlands.tip448.stk.escort_on": "State: ESCORT = ON.\nNPC moves only while the player remains nearby.",
    "gui.settledlands.tip448.stk.escort_off": "State: NORMAL WALK.\nNPC walks to destination autonomously.",
    "gui.settledlands.tip448.stk.cutline_on": "State: CUTSCENE LINE = ON.\nDisplays as cutscene subtitles without opening the dialogue box.",
    "gui.settledlands.tip448.stk.cutline_off": "State: NORMAL DIALOGUE.\nShows inside the regular NPC dialogue window.",
    "gui.settledlands.tip448.stk.anim_loop_on": "State: LOOP = ON.\nAnimation repeats continuously until changed.",
    "gui.settledlands.tip448.stk.anim_loop_off": "State: PLAY ONCE.\nAnimation plays through once and returns to idle.",
    "gui.settledlands.tip448.stk.entry": "Marks this line as the starting dialogue node when talking to the NPC.",
    "gui.settledlands.tip448.stk.add_variant": "Adds an alternate text/relation variant to this dialogue line.",
    "gui.settledlands.tip448.stk.del_variant": "Removes the last alternate text variant.",
    "gui.settledlands.tip448.stk.cutscene_studio": "Opens the camera cutscene keyframe studio.",
    "gui.settledlands.tip448.stk.cutscene_del": "Deletes the selected cutscene (click again to confirm).",
    "gui.settledlands.tip448.stk.unlink_if": "Unlinks the incoming If condition attached to this node.",

    "gui.settledlands.tip448.fld.action": "Server command (/give, /tp) or trigger:<action>. Supports %player% and %target%.",
    "gui.settledlands.tip448.fld.trigger_cmd": "Command or trigger expression attached to this node.",
    "gui.settledlands.tip448.fld.var_name": "Identifier of the numeric variable to read or modify.",
    "gui.settledlands.tip448.fld.var_delta": "Numeric delta (+/-) added to the variable.",
    "gui.settledlands.tip448.fld.var_compare": "Target number compared against the variable.",
    "gui.settledlands.tip448.fld.check_radius": "Detection radius in blocks.",
    "gui.settledlands.tip448.fld.check_cooldown": "Minimum cooldown in seconds between activations.",
    "gui.settledlands.tip448.fld.min_damage": "Minimum hit damage required to fire.",
    "gui.settledlands.tip448.fld.time_ticks": "Exact Minecraft daytime tick (0-23999).",
    "gui.settledlands.tip448.fld.timer_interval": "Timer delay or interval in seconds.",
    "gui.settledlands.tip448.fld.radius": "Search radius in blocks for the blue target output.",
    "gui.settledlands.tip448.fld.quest_title": "Title shown to players in the quest log.",
    "gui.settledlands.tip448.fld.min_relation": "Minimum relation/score threshold required for this variant.",
    "gui.settledlands.tip448.fld.max_relation": "Maximum relation threshold above which this variant hides.",
    "gui.settledlands.tip448.fld.move_coords": "Destination XYZ coordinates (supports relative ~ offsets).",
    "gui.settledlands.tip448.fld.move_speed": "NPC movement speed multiplier (typically 0.6-2.0).",
    "gui.settledlands.tip448.fld.random_range": "Integer range [min-max] for random output selection.",
    "gui.settledlands.tip448.fld.random_outputs": "Number of output pins on this Random node.",
    "gui.settledlands.tip448.fld.text": "Node text displayed on the card and to the player.",
    "gui.settledlands.tip448.fld.variant": "Alternate text variant for relation tiers or random lines.",
    "gui.settledlands.tip448.fld.relation": "Relation delta applied when choosing this option.",
    "gui.settledlands.tip448.fld.points": "Custom points scoreboard name (optional).",
    "gui.settledlands.tip448.fld.min_points": "Minimum custom points required for this option.",
    "gui.settledlands.tip448.fld.max_points": "Maximum custom points allowed for this option.",

    "gui.settledlands.tip448.pin.slot_mode": "Slot 0 (Mode): accepts a Mode & Player Scaling module.",
    "gui.settledlands.tip448.pin.slot_radius": "Slot 1 (Radius): accepts Activation & Spawn Radius module.",
    "gui.settledlands.tip448.pin.slot_wave": "Slot 2 (Wave): accepts Wave Counts & Batch module.",
    "gui.settledlands.tip448.pin.slot_timing": "Slot 3 (Timings): accepts Delays & Cooldown module.",
    "gui.settledlands.tip448.pin.slot_mobs": "Slot 4 (Mobs): accepts the Mob Pool module.",
    "gui.settledlands.tip448.pin.slot_rewards": "Slot 5 (Rewards): accepts the Reward Pool module.",
    "gui.settledlands.tip448.pin.slot_ominous": "Slot 6 (Ominous): accepts Ominous Settings module.",
    "gui.settledlands.tip448.pin.slot_protect": "Slot 7 (Protect): accepts Protection & Self-Destruct module.",
    "gui.settledlands.tip448.pin.slot_wired": "Slot WIRED: using values from the attached module.",
    "gui.settledlands.tip448.pin.slot_default": "Slot EMPTY: using defaults (wire a module or click 'Restore modules').",
    "gui.settledlands.tip448.pin.trial_start": "On Wave Start event: fires when the Trial Spawner enters combat.",
    "gui.settledlands.tip448.pin.trial_clear": "On Wave Clear event: fires when all wave mobs are defeated.",
    "gui.settledlands.tip448.pin.trial_destroy": "On Destroy event: fires if the spawner breaks or self-destructs.",
    "gui.settledlands.tip448.pin.trial_player": "Blue Player pin: outputs the triggering player (%target% downstream).",
    "gui.settledlands.tip448.pin.mod_out_wired": "Module output LINKED to the hub. Middle-click wire to detach.",
    "gui.settledlands.tip448.pin.mod_out_free": "Module output FREE. Drag left-mouse from here to the matching hub slot.",
    "gui.settledlands.tip448.pin.door_hack": "On Hack event: fires upon solving the lock minigame.",
    "gui.settledlands.tip448.pin.door_open": "On Open event: fires when the door opens.",
    "gui.settledlands.tip448.pin.door_who": "Blue Who pin: outputs the player who opened or hacked the door.",
    "gui.settledlands.tip448.pin.chest_open": "On Open event: fires when a player opens the chest.",
    "gui.settledlands.tip448.pin.chest_hack": "On Hack event: fires upon unlocking the locked chest.",
    "gui.settledlands.tip448.pin.chest_who": "Blue Who pin: outputs the player opening or hacking the chest.",
    "gui.settledlands.tip448.pin.if_true": "True output: executes when the If comparison succeeds.",
    "gui.settledlands.tip448.pin.if_false": "False output: executes when the If comparison fails.",
    "gui.settledlands.tip448.pin.var_input": "Blue value input: connect a blue reference pin here.",
    "gui.settledlands.tip448.pin.value_source": "Blue value output: drag to a blue input pin on Pursuit, Teleport, or If.",
    "gui.settledlands.tip448.pin.in_title": "Execution Input",
    "gui.settledlands.tip448.pin.in_body": "Receives execution flow from a preceding node's right output.",
    "gui.settledlands.tip448.pin.out_title": "Execution Output",
    "gui.settledlands.tip448.pin.out_body": "Drag to another node's input to wire sequence (MMB to cut).",

    "gui.settledlands.tip448.ai.wander_on": "State: Auto-Wander ON.\nNPC freely strolls within its configured wander radius when idle.",
    "gui.settledlands.tip448.ai.wander_off": "State: Auto-Wander OFF.\nNPC stays anchored unless moved by a graph script.",
    "gui.settledlands.tip448.ai.attack_on": "State: Auto-Attack ON.\nNPC automatically engages hostile mobs entering aggro range.",
    "gui.settledlands.tip448.ai.attack_off": "State: Auto-Attack OFF.\nNPC does not engage hostiles on its own.",
    "gui.settledlands.tip448.ai.wander_rad": "Maximum idle wander distance in blocks from home.",
    "gui.settledlands.tip448.ai.aggro_rad": "Detection radius in blocks for auto-attacking hostile mobs.",
    "gui.settledlands.tip448.stats.randomize": "Randomizes appearance, name, and variant style in one click.",
    "gui.settledlands.tip448.stats.prof_title": "NPC Profession",
    "gui.settledlands.tip448.stats.profession": "Click to choose the NPC's profession/role from the popup.",
    "gui.settledlands.tip448.stats.style_title": "Body & Style Toggles",
    "gui.settledlands.tip448.stats.style": "Toggles NPC model gender and style variants.",
    "gui.settledlands.tip448.stats.health": "Base max health (HP). Click - / + to adjust.",
    "gui.settledlands.tip448.stats.damage": "Base attack damage. Click - / + to adjust.",
    "gui.settledlands.tip448.stats.speed": "Base movement speed. Click - / + to adjust.",

    "gui.settledlands.tip448.node.auto_ai": "World-space Autonomous AI panel shared across this NPC's behavior.",
    "gui.settledlands.tip448.node.stats": "Permanent root card configuring NPC base attributes and model.",
    "gui.settledlands.tip448.node.mod_mode": "Select to edit trial mode and player scaling in the inspector.",
    "gui.settledlands.tip448.node.mod_radius": "Select to edit activation and spawn radius.",
    "gui.settledlands.tip448.node.mod_wave": "Select to edit wave totals, concurrent caps, and batch size.",
    "gui.settledlands.tip448.node.mod_timing": "Select to edit spawn delays and cooldown.",
    "gui.settledlands.tip448.node.mod_mobs": "Select to edit spawned entities and weights or open the catalog.",
    "gui.settledlands.tip448.node.mod_rewards": "Select to configure loot items, counts, weights, and chances.",
    "gui.settledlands.tip448.node.mod_ominous": "Select to configure ominous multiplier, gear, hazards, and keys.",
    "gui.settledlands.tip448.node.mod_protect": "Select to toggle block-break protection and self-destruct rules.",
    "gui.settledlands.tip448.node.mod_state_on": "Linked to hub: settings are compiled into the Trial Spawner on save.",
    "gui.settledlands.tip448.node.mod_state_off": "Unlinked: wire its right output to the matching hub slot or click 'Attach'.",
    "gui.settledlands.tip448.node.trial_hub": "Connected modules: %d of 8.\nEach wired module overrides its section; empty slots use defaults.",
    "gui.settledlands.tip448.node.trial_ctrl": "Select to set target Trial Spawner coordinates or open its full 4-tab config.",
    "gui.settledlands.tip448.node.teleport": "Wire an execution input and set destination XYZ & dimension on the right.",
    "gui.settledlands.tip448.node.loot_pool": "Click the top-right gear icon on the card to open the visual pool editor.",
    "gui.settledlands.tip448.node.pursuit_start": "Start pursuit mode: NPC chases the target from text or blue pin.",
    "gui.settledlands.tip448.node.pursuit_stop": "Stop pursuit mode: clears active chase target.",
    "gui.settledlands.tip448.node.block_name": "Select to rename this trigger block and change its header color.",
    "gui.settledlands.tip448.node.door_sealed": "Door is LOCKED. Hacking triggers the right outputs and passes the hacker.",
    "gui.settledlands.tip448.node.door_open": "Door is UNLOCKED for normal clicks. Select to enable lockpicking.",
    "gui.settledlands.tip448.node.chest_locked": "Chest is LOCKED. Hacking fires the right outputs and passes the player.",
    "gui.settledlands.tip448.node.chest_open": "Chest opens normally on click. Wire loot/events on the right.",
    "gui.settledlands.tip448.node.cutscene_line": "Cutscene subtitle mode is active for this line.",
    "gui.settledlands.tip448.node.timer_loop": "Repeating timer: fires every %d seconds. Select to change interval/mode.",
    "gui.settledlands.tip448.node.timer_once": "One-shot timer: delays %d seconds before continuing.",
    "gui.settledlands.tip448.node.n_0": "Tip: mark the opening line as Entry and add relation/random variants on the right.",
    "gui.settledlands.tip448.node.n_1": "Tip: wire from a dialogue line into this choice, then out to the next response.",
    "gui.settledlands.tip448.node.n_2": "Tip: use the command presets dropdown and %player% / %target% placeholders.",
    "gui.settledlands.tip448.node.n_3": "Tip: upper green output fires on True, lower on False.",
    "gui.settledlands.tip448.node.n_4": "Tip: local variables are per-player; shared variables are global.",
    "gui.settledlands.tip448.node.n_5": "Tip: detects entities inside the radius with configurable cooldown.",
    "gui.settledlands.tip448.node.n_6": "Tip: configure damage source filter and minimum damage on the right.",
    "gui.settledlands.tip448.node.n_7": "Tip: choose a daytime preset or custom world tick 0-23999.",
    "gui.settledlands.tip448.node.n_8": "Tip: set seconds interval and one-shot vs repeating mode on the right.",
    "gui.settledlands.tip448.node.n_9": "Tip: pick animation clip, bone group, and loop toggle on the right.",
    "gui.settledlands.tip448.node.n_10": "Tip: set quest title and minimum relation requirement on the right.",
    "gui.settledlands.tip448.node.n_11": "Tip: toggle Escort to make the NPC wait for nearby players while walking.",
    "gui.settledlands.tip448.node.n_12": "Tip: fires its right output when the NPC reaches waypoint #1.",
    "gui.settledlands.tip448.node.n_13": "Tip: configure output count and range on the right.",
    "gui.settledlands.tip448.node.n_14": "Tip: click 'Studio...' on the right to record or adjust cutscene keyframes.",
    "gui.settledlands.tip448.node.n_15": "Tip: wire its right blue pin to Pursuit, Teleport, or If.",
}

CHANGELOG_EN = (
    "Build 448 polishes graph usability across all editors: (1) removes the 8 Trial Spawner "
    "module rows from the right-click creation palette (while keeping the 'Restore modules' "
    "button on the hub inspector); (2) adds compact docked bottom description panels under "
    "every node/card across all graphs (NPC dialogue/walk/shared, Auto-AI, Stats, Block, "
    "Door, Chest, Trial Hub & 8 Modules, Control, Teleport, Loot Pool, Pursuit); and "
    "(3) introduces a state-aware >2-second hover tooltip overlay covering all graph UI "
    "buttons, dropdowns, sliders, fields, canvas pins, interactive cards, and the Trial "
    "Config screen."
)

PATCHINFO = (
    "Сборка 448: полная пояснительная навигация и чистое меню во всех редакторах графов.\n"
    "  * ПКМ-меню палитры очищено от 8 отдельных строк модулей спавнера (для достройки\n"
    "    используется кнопка «Восстановить модули» в инспекторе хаба);\n"
    "  * Каждая карточка во всех графах (диалог/распорядок/общее, ИИ, характеристики, блок,\n"
    "    дверь, сундук, хаб и 8 модулей триал-спавнера, управление, телепорт, пул лута,\n"
    "    преследование) получила пристыкованный снизу блок с кратким объяснением её роли;\n"
    "  * Добавлены контекстные и зависимые от текущего состояния всплывающие подсказки\n"
    "    при наведении курсора более 2 секунд на кнопки, переключатели, списки, слайдеры,\n"
    "    поля, пины, карточки и вкладки окна настройки триал-спавнера.\n"
)


def verify_lang_coverage() -> None:
    missing_ru = sorted(k for k in LANG_SET_EN if k not in LANG_SET_RU)
    missing_en = sorted(k for k in LANG_SET_RU if k not in LANG_SET_EN)
    if missing_ru or missing_en:
        raise SystemExit(f"lang parity error: missing_ru={missing_ru}, missing_en={missing_en}")
    # Verify key groups referenced dynamically by GraphCardDesc448 / GraphTooltips448
    expected = (
        [f"gui.settledlands.desc448.card.node_{i}" for i in range(16)]
        + [f"gui.settledlands.desc448.card.mod_{k}"
           for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
        + [f"gui.settledlands.tip448.menu.node_{i}" for i in range(16)]
        + [f"gui.settledlands.tip448.node.n_{i}" for i in range(16)]
        + [f"gui.settledlands.tip448.node.mod_{k}"
           for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
        + [f"gui.settledlands.tip448.pin.slot_{k}"
           for k in ("mode", "radius", "wave", "timing", "mobs", "rewards", "ominous", "protect")]
        + [f"gui.settledlands.tip448.varop.{i}" for i in range(6)]
        + [f"gui.settledlands.tip448.preset.{i}" for i in range(10)]
        + [f"gui.settledlands.tip448.op.{k}"
           for k in ("start", "config", "config_start", "stop", "reset_cd", "destroy")]
        + [f"gui.settledlands.tip448.hack.slider_diff_{i}" for i in (1, 2, 3)]
    )
    for k in expected:
        if not LANG_SET_RU.get(k) or not LANG_SET_EN.get(k):
            raise SystemExit(f"missing expected localization key: {k}")


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


def compile_build448(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build448 compile failed:\n" + r2.stdout + r2.stderr)


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
        if name in client:
            client.remove(name)
        client.append(name)
    if client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    if client[-1] != "GraphHover448Mixin":
        raise SystemExit("GraphHover448Mixin must remain last in client mixins!")
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    verify_lang_coverage()

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out448",
                    help="directory for compiled build 448 classes")
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
        print("compiling Build 448 sources via ECJ (-21)...")
        compile_build448(base, srcroot)
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
