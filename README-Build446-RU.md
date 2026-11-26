# Settled Lands 0.8.0 Build 446 — Автоочистка графов при сносе блоков + полная интеграция `trialtuner:trial_spawner`

База: `settledlands-0.8.0-build445-direct-percent-loot-no-duplicates.jar`

## Что добавлено в Build 446

### 1. Автоматическое удаление графов и замков при уничтожении или замене блока
- **`BlockDestroy446Mixin`** (`Level#setBlock(BlockPos, BlockState, int, int)`) со стековым трекингом (без аллокаций на обычных изменениях террейна и с безопасной поддержкой рекурсивных сносов обеих половин двери):
  - при любом сносе или замене блока-хоста (ручное ломание в выживании/креативе, взрывы, `/setblock`, поршни, самоуничтожение спавнера, моды вроде Axiom) удаляет связанные записи из:
    - `BlockTriggerStore351` (обычные блоки + кэш `blockId` + сброс кулдаунов в `RadiusPass354`);
    - `DoorGraph382Store` + `DoorStore368` (обе половины двери, даже если снесена верхняя или нижняя + явная синхронизация клиента через `DoorSync375.sendUnlockExact` и `sendClearParam`);
    - `ChestGraph396Store` + `ChestLock397.clearCracksForKey` (сундуки, бочки, мебель, кафедры, книжные полки);
    - `TrialGraph446Store` (с предварительным запуском ветки **«Снос»**, если событие `TrialTriggers.onDestroyed` ещё не отработало);
  - строго игнорирует property-only смены состояния блока (`afterState.is(oldState.getBlock())` — открытие/закрытие дверей и сундуков, нажатие кнопок, рычагов, взятие/кладка книг не трогают графы);
  - периодическая сверка загруженных чанков **`GraphBlockCleanup446.reconcileLoaded`** в `RadiusPass354` (ловит прямые изменения секций чанков, обходящие `Level#setBlock`, не подгружая выгруженные чанки);
  - выживательная защита запертых дверей/сундуков (`DoorEvents368`, `DoorBreak368Mixin`, `ChestGraph396Events`) полностью сохранена.

### 2. Прямой граф `trialtuner:trial_spawner` (жезл НПС по блоку спавнера)
- **`TrialSpawnerEvents446`** перехватывает клик жезлом НПС по `trialtuner:trial_spawner` с `EventPriority.HIGHEST` на клиенте и сервере (раньше интерфейса Trial Tuner и `BlockTriggerEvents351`).
- Отдельная однотабовая карточка **`TRIAL_TYPE = 60`** (`TrialGraph446`, `TrialCard446`, `TrialGraphClient446`, `NpcTrial*446Mixin`) в диапазоне редактора `-2_000_499_999 .. -2_000_400_000`:
  - **4 неперекрывающихся правых пина** (шаг >= 18 px):
    1. `y = 32` — **«Старт»** (`originStep`, зелёный сокет `NpcTrialSocket446Mixin`): запускается при старте волны;
    2. `y = 50` — **«Зачистка»** (`next`, янтарный): запускается при завершении/зачистке волны;
    3. `y = 68` — **«Снос»** (`falseNext` через `CardEventPin398`): запускается при уничтожении или самоуничтожении спавнера;
    4. `y = 92` — **«Игрок»** (синий сокет значения через `PursuitGraphNode272` + `BlockTalk358`): передаёт последнего участника испытания (`%target%` и привязка `Реплика`/`Триггер`/`Условие`/`Переменная`/`Преследование`);
  - **Автономный 4-вкладочный редактор настроек спавнера `TrialControlConfigScreen446`** («Основное», «Зловещий», «Мобы» с пикером сущностей, «Награды» с пикером предметов), синхронизирующийся в реальном времени с `TrialSpawnerBlockEntity` через `TrialSpawnerBridge446` + собственный JSON-кодек `TrialConfig446` (без внешней зависимости от Gson/mod-классов на этапе компиляции);
  - Встраивание графа в `BlockEntity` NBT (`SettledLandsTrialGraph`), поддержка переноса якорями Axiom (`NpcGraphAnchor420` `TRIAL_KIND`), подсветка жезлом и тики `В радиусе`/`Время`/`Таймер` в `RadiusPass354`;
  - Необязательные `@Pseudo` compat-миксины `TrialSpawnerEntityMixin446` (`startWave` / `finishWave`) и `TrialTriggersMixin446` (`onDestroyed`) в `settledlands.compat.mixins.json`.

### 3. Кросс-графовый узел «Триал-спавнер» (`trigger:spawner:...`) во всех 6 графах
- Доступен в палитрах **НПС**, **общего графа**, **блока**, **двери**, **сундука** и **спавнера** (`TrialControlNode446`, `TrialControlCard446`, `BlockMenu363`, `TeleportNode424` расширен до 19 пунктов, `TrialControlActions446` / `TrialControlActionsMixin446`, `TrialControlWiringMixin446`, `TrialControlInspectorMixin446`, `BlockTrigger351.startServerOnly`).
- Поддерживает операции: **`START`** (запуск волны), **`CONFIG`** (применить параметры), **`CONFIG_START`** (применить + запустить), **`STOP`** (остановить с опцией уничтожения активных мобов), **`RESET_CD`** (сбросить перезарядку), **`DESTROY`** (уничтожить спавнер).
- Поддерживает абсолютные (`120`) и относительные (`~`, `~5`, `~-3`) координаты, выбор измерения (`Текущий мир` + 5 встроенных/любой custom id) и полный 4-вкладочный редактор `TrialControlConfigScreen446`.

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build446.py` -> `settledlands-0.8.0-build446-auto-cleanup-and-trial-spawner.jar`
- `tools/verify_jar.py --base settledlands-0.8.0-build445-direct-percent-loot-no-duplicates.jar` — **PASS** (`validate_jar`: 758 entries / 703 classes; `jvm_verify`: OK; `check_mixin_class`: 132 миксина, 1487 внешних API-ссылок, **0** новых; `dangling_refs`: **0**)
- `tools/invoke_kind_check.py` — **OK** (`6717` call target'ов сверено, **0** новых непроверенных вызовов)
- `tools/mixin_clash_check.py` — **OK** (`282` хелпера в `98` миксинах, **0** новых коллизий)
- `tools/hell_dungeon_check.py` — **PASS** (`6297` + `11088` + `7`)
- `tools/anchor_check.py` — **OK** (`ANCHOR_CHECKS=62`)
- `tools/door_graph_check.py` — **OK**
- `tools/build446_check.py` — **PASS** (`BUILD446_CHECKS=47` против `settledlands-0.8.0-build446-auto-cleanup-and-trial-spawner.jar` и `trialtuner-0.1.0-fixed-v8.1.jar`)
