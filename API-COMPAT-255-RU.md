# API и байткодная совместимость Build 255

## 1. Точный ZIP-diff относительно Build 254

- База: `settledlands-0.8.0-build254-ui-ivy.jar` (`2 181 852` Б, SHA-256 `19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c`).
- Выход: `settledlands-0.8.0-build255-auto-ai.jar` (`2 215 570` Б, SHA-256 `7866e20a75eb5ef53e254e8e34a5eacc155d0b3ac768b99977ad4d95358b6541`).
- Добавлено **8 классов** (`ADDED`):
  - `dev/settledlands/npc/NpcAutoAi255.class`, `NpcAutoAi255$ProfileState.class`, `NpcAutoAi255$GraphState.class`
  - `dev/settledlands/npc/NpcAutoAiEngine255.class`, `NpcAutoAiEngine255$MeleeCombo.class`
  - `dev/settledlands/npc/client/NpcAutoAiConfigUi255.class`
  - `dev/settledlands/npc/client/NpcAutoAiGraphUi255.class`, `NpcAutoAiGraphUi255$DragState.class`
- Изменено **11 записей** (`CHANGED`):
  - `dev/settledlands/npc/{NpcProfile,NpcDialogueGraph,NpcEdit,GuideNpc}.class`
  - `dev/settledlands/npc/client/{NpcConfigScreen,NpcDialogueGraphScreen,NpcGraphCanvas}.class`
  - `assets/settledlands/lang/{ru_ru,en_us}.json`, `META-INF/neoforge.mods.toml`, `settledlands-patch-info.txt`
- Все классы 251–254 рендера, HnS, FA, TnM, щита, копирования НПС, ноды характеристик и дефолтной модели (`BbModel`, `PlayerBodyRenderer`, `FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `NpcCopyPickPayload`, `NpcCopyEggFix253`, `NpcStatsGraph`, `NpcStatsGraphUi`, `NpcDefaultModel254`, `NpcDropdownTop254`, `GuideTriggerNpc`, `GuideCorpseNpc`, `IvyCombatAi`, `GuideRenderer`) сохранены **байт-в-байт**.

## 2. Серверная и UI-изоляция

- Серверные классы `NpcAutoAi255*` и `NpcAutoAiEngine255*` не содержат ни одной ссылки на `net.minecraft.client.*` или `dev.settledlands.npc.client.*` (проверено статически по constant pool и динамически через изолированный серверный `URLClassLoader` на Java 21 `-Xverify:all`).
- На `NpcConfigScreen` секция «Автономный ИИ» размещена в начале вкладки «Подвижность» (`tab == 5`), не затрагивая `initCharacter()` (`tab == 0`), благодаря чему условие `scrollMax() == 0` и 3D-превью сущности на вкладке «Персонаж» сохранены.
- Карточка `NpcAutoAiGraphUi255` в редакторе графа рисует весь текст исключительно через `drawString(..., dropShadow = false)` (`Z = 0.0`), не добавляет узлы в `graph.nodes` (сохраняя лимит `2048` обычных узлов + 1 узел характеристик) и не добавляет новых `settledlands$*`-мостов вне подмножества 246 (`screen_subset`), кроме `forward_alias` `settledlands$mouseDraggedOriginal` и `settledlands$mouseReleasedOriginal`.
- Запросы целей в `NpcAutoAiEngine255` ограничены локальным `AABB` (`aggroRadius <= 64`, `inflate(r, 6.0, r)`) с кулдауном сканирования `10` тиков.

## 3. Границы проверки

- Реальная Java 21 (`-Xverify:all`) исполняет байткод всех новых и унаследованных классов на многотиковых сценариях и 24/24 калиброванных мутантах `tests/mutants255.py` плюс все регрессионные наборы 237–254.
- Реальный клиент Minecraft 1.21.1 / NeoForge и визуальный рендер в живой игре в песочнице не запускались; Touch’n Motion для самих сущностей `GuideNpc` не реализован.
