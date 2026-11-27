# Settled Lands 0.8.0 Build 450 — Универсальные числовые диапазоны и компактный 3D-каталог мобов

База: `settledlands-0.8.0-build449-named-trial-pools-and-3d-mob-catalog.jar`

## Что изменилось в Build 450

Build 450 добавляет **универсальную поддержку числовых диапазонов** (например, `20-40`, `5..15`, `1.2-2.5`, `-5-10`, включая одинарные значения) во все числовые поля модулей/редактора триал-спавнера, пула мобов и стоковых нод графа, а также **полностью переделывает вёрстку 3D-каталога мобов**, чтобы он гарантированно помещался в несколько строк при любом масштабе GUI:

### 1. Компактный многорядный 3D-каталог мобов (`TrialMobCatalogScreen449`)
- **Исправлена проблема одного ряда карточек на стандартных масштабах GUI (240–270 px)**:
  - панель каталога расширена почти на весь экран (`width - 8` × `height - 8`, не жёсткие 540×356);
  - высота карточки уменьшена с `74` до **`46 px`** (`CELL_H = 46`, `CELL_GAP = 3`, окно 3D-модельки `28×29 px`, `CELL_MIN_W = 104`), шапка сжата до `37 px`, подвал — `14 px`;
  - результат: даже на минимальной масштабированной высоте `240 px` (1080p GUI scale 4 / 720p GUI scale 3) одновременно отображается **≥ 3 полных ряда карточек** вместо одного.
- **Качественный 3D-рендер внутри карточки (`drawMobPreview`)**:
  - `gui.enableScissor` строго обрезает даже крупных или крылатых мобов границами окна модельки (`28×29 px`) — они больше не вылезают за карточку и не перекрывают текст соседних слотов;
  - улучшенный авто-расчёт масштаба, мягкий сдвиг вниз/влево, уменьшенный шрифт названий и ID мода через `drawScaledText` (название `0.85x`, `modid` `0.70x`) с аккуратной обрезкой.

### 2. Единый парсер/рандомайзер диапазонов `RangeSpec450` и полная поддержка в триал-спавнере
- Новый класс `dev.settledlands.trial.RangeSpec450`:
  - `IntRange` и `FloatRange` понимают разделители `-`, `..`, `:`, `~`, `–`, корректно разбирают отрицательные границы (`-10--2`, `-10..10`), автоматически меняют перепутанные границы (`40-20` → `20-40`) и ограничивают в заданный `[clampMin, clampMax]`.
  - Фильтры ввода для `EditBox`: `INT_RANGE` (`UNSIGNED_INT_RANGE`), `SIGNED_INT_RANGE`, `FLOAT_RANGE`.
- **`TrialConfig446` + `TrialSpawnerBridge446` + `TrialControlActions446` + `TrialGraph446`**:
  - В конфиге сохраняются строковые диапазоны (`ranges` для целых/дробных параметров, `weightRange` в `SpawnEntry`/`RewardEntry`, `chanceRange` в `RewardEntry`), при отсутствии диапазонов JSON остаётся 100% обратно совместимым со старой схемой `trialtuner`.
  - `TrialConfig446#toResolvedJson(Random)` при применении к `TrialSpawnerBlockEntity` (`TrialSpawnerBridge446#apply`, `OP_START` в `TrialControlActions446`, а также переролл при старте/очистке волны в `TrialGraph446#onWaveCleared`) каждый раз случайно выбирает конкретные числа из заданных диапазонов — в `trialtuner` `SpawnerConfig` всегда уходит чистый JSON без посторонних ключей.
  - В `TrialGraph446#pollAll` включена защита `hasRanges()`: если в привязанных модулях заданы диапазоны, периодический опрос не затирает их переролленными значениями живого спавнера.
- **`TrialMobPool449` и `TrialMobPoolEditorScreen449`**:
  - Строки формата `entity_id | 5-15` поддерживают диапазоны веса как в `.txt`-файлах, так и в сетевом протоколе (`encodeEntriesWire`/`decodeEntriesWire`).
  - Эффективные шансы `%` и строка `Тест волны` в редакторе считают середину диапазона / честный переролл веса; заголовок веса обновлён, поле расширено для ввода `min-max`.
- **Единые поля и диапазоны в модулях и конфигурации триал-спавнера (`TrialModule447`, `TrialModuleClient447`, `TrialModuleCard447`, `TrialModuleInspector447Mixin`, `TrialControlConfigScreen446`)**:
  - Убраны раздельные пары полей `Мин. задержка` + `Макс. задержка` в модуле `Тайминги` (`SLOT_TIMING`) и на вкладке `Общее` (`TAB_GENERAL`) — объединены в одно поле **`Задержка`** (`delay`, напр. `20-40`), а также объединён счётчик `Кол-во` (`cntBox`, напр. `1-3`) в таблице наград `TAB_REWARDS`.
  - Все остальные числовые поля всех 8 модулей (радиус активации/спавна, всего мобов, за игрока, одновременно, за игрока, пачка, кулдаун, множитель зловещести, интервал эффектов, защита от ломания, волны/шанс самоуничтожения, веса мобов и веса/шансы/количество наград) принимают диапазоны `min-max`.

### 3. Универсальные диапазоны на стоковых нодах графа диалогов и триггеров (`GraphRanges450`, `GraphRangeUi450` и 4 миксина)
- **`GraphRanges450` (`Node.text` компактный маркер `"rs450:k=lo-hi;..."`)**:
  - Обратно совместимое хранение внутри стандартного поля `Node.text` (≤240 символов; при вводе одиночного числа возвращается чистая строка `""`).
  - Поддерживаемые ноды и поля:
    - `IF` (`cmp`): при операторах `==` / `!=` проверка в реальном времени проверяет **попадание в отрезок `[min, max]`** (`"5-15"` верно для любых чисел от `5` до `15` включительно); при `>`, `<`, `>=`, `<=` переролливает порог.
    - `VARIABLE` (`val`): случайное целое из диапазона (включая отрицательные) при каждом срабатывании ноды.
    - `CHECK` (`rad`, `cd`), `DAMAGE` (`dmg`, `cd`), `TIME` (`t`), `TIMER` (`iv`), `RADIUS` (`r`): случайный переролл значения в конце сработавшего цикла / триггера (не посреди обратного отсчёта!).
    - `BUTTON` (`rel`, `pts` — объединены `Мин`/`Макс` репутации и очков в единые поля `Репутация` и `Очки`, напр. `-50-100`).
    - `RANDOM` (`random_range` — объединены `Выходов: 1..N` + `Макс.` в единое поле диапазона с обновлением подписи `outHeader.y + 11` на месте без пересборки виджетов во время ввода).
- **Клиентский UI (`GraphRangeInputs450Mixin` + `GraphRangeUi450`, без синтетических лямбд)**:
  - Инжектится в `NpcDialogueGraphScreen` прямо перед `GraphHover448Mixin`; паркует старые парные поля в пользу широких единых полей, ставит фильтры диапазонов (`maxLength=16`) на числовые `EditBox`-ы стоковых нод и обновляет тултипы (`GraphTooltips448`) под новые поля.
- **Серверные рандом-роллеры (`GuideRange450Mixin`, `TriggerRange450Mixin`, `RadiusRange450Mixin`)**:
  - Zero-lambda миксины на `GuideDialogue`, `BlockTrigger351` и `RadiusPass354`, гарантирующие переролл при старте, выборе ветки, обходе триггеров, проверке радиуса, урона, времени суток и таймеров.
- Локализация (`ru_ru.json` / `en_us.json`): добавлены все ключи (`gui.settledlands.trial.cfg.delay`, `gui.settledlands.graph.relation_range`, `gui.settledlands.graph.points_range`, подсказки `tip448.*`), полный паритет `1588 / 1588` ключей.

## Ключевые новые и изменённые файлы

- Ядро триал-спавнера: `RangeSpec450.java`, обновлены `TrialConfig446.java`, `TrialMobPool449.java`, `TrialModule447.java`, `TrialSpawnerBridge446.java`, `TrialControlActions446.java`, `TrialGraph446.java`
- Клиент триала и каталога: обновлены `TrialMobCatalogScreen449.java`, `TrialMobPoolEditorScreen449.java`, `TrialModuleClient447.java`, `TrialModuleCard447.java`, `TrialControlConfigScreen446.java`, `GraphTooltips448.java`
- Универсальные диапазоны графа: `GraphRanges450.java`, `GraphRangeUi450.java`
- Миксины: новые `GraphRangeInputs450Mixin.java`, `GuideRange450Mixin.java`, `TriggerRange450Mixin.java`, `RadiusRange450Mixin.java`, обновлён `TrialModuleInspector447Mixin.java`
- Инструменты сборки и проверки: `tools/patch_build450.py`, `tools/build450_check.py`

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build450.py` → `settledlands-0.8.0-build450-universal-ranges-and-compact-mob-catalog.jar`
  - Размер: `4 368 341` байт, `812` записей / `757` классов
  - SHA-256: `ae27535f22a505e03759bd3b276049fd74786dca59fc7f01f8b095d6f16b0d2a`
- `tools/build450_check.py` — **PASS** (`BUILD450_CHECKS=642`, проверка парсера диапазонов, legacy/range JSON `TrialConfig446`, схемы полей реального `dev.arena.trialtuner.config.SpawnerConfig`, пулов, модулей `TrialModule447`, маркёров `rs450:` и интервалов `IF`/`NpcDialogueGraph.error()`, многорядной геометрии каталога мобов на `240/270/360/540 px`, паритета `1588` RU/EN ключей и zero-lambda во всех миксинах)
- `tools/verify_jar.py --base` — **PASS** (146 классов миксинов, `0` новых внешних API-ссылок против Build 449, `0` оборванных ссылок)
- `tools/mixin_clash_check.py` — **PASS** (`338` методов в `111` миксинах, `0` новых конфликтов)
- `tools/invoke_kind_check.py --base` — **PASS** (`7139` уникальных целей вызовов — 100% совпадение видов вызова)
- Регрессионные проверки — **PASS**: `build447_check.py` (`91`), `build446_check.py` (`47`), `hell_dungeon_check.py` (`17 392`), `anchor_check.py` (`62`), `door_graph_check.py`, `block_trigger_check.py`, `random_line_check.py`, `dropdown_check.py`, `hack_input_check.py`.
