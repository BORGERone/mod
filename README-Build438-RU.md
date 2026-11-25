# Settled Lands 0.8.0 Build 438 — Подсветка полок жезлом, перенос графов через Axiom без Entities и гарантированный якорь в данже

База: `settledlands-0.8.0-build437-anchor-furniture-fix.jar`

## Найденные проблемы

1. **Полки и мебель не подсвечивались при удержании жезла НПС (`RadiusPass354`)**:
   - Цикл подсветки частиц (`ELECTRIC_SPARK` по 8 углам блока) в `RadiusPass354.pass(...)` проходил только по `BlockTriggerStore351` и не охватывал `ChestGraph396Store` (`ChestSpot`). В результате настроенные полки/шкафы/ящики Refurbished Furniture и сундуки никак не подсвечивались жезлом, в отличие от блоков с блок-триггерами.
   - Кроме того, прежний лимит `MAX_HIGHLIGHT = 24` брал элементы в произвольном порядке обхода коллекции, из-за чего в насыщенных комнатах ближние к игроку блоки и полки могли выпадать из подсветки (поднято до `MAX_HIGHLIGHT = 64` с сортировкой кандидатов по квадрату расстояния до ближайшего игрока).
   - Для дверей (`spot.door`) по-прежнему оставлено исключение Build 382 — двери жезлом не подсвечиваются.

2. **Axiom `.bp` и копирование-вставка (`Ctrl+C` / `Ctrl+V`) по умолчанию не переносили якорь `settledlands:guide_trigger`**:
   - В моде Axiom настройка `Selection.copyWithEntities` по умолчанию выключена (`false`), а `Selection.performCopyLikeAction` включает сущность только при попадании её точной позиции внутрь `buffer.contains(x, y, z)`. Если якорь `settledlands:guide_trigger` стоял в воздухе комнаты (который часто исключается при выделении без воздуха) или копирование делалось без сущностей, чертёж `.bp` либо вставка в мире приходили без якоря, и в новом данже/постройке полностью терялись графы и пулы лута полок.
   - При этом `BlockEntities` (включая `NeoForgeData`) **всегда** сериализуются Axiom'ом при копировании выделения (`SelectionBuffer.callCopy`) и сохраняются в `AxiomBlueprint427` / `DungeonNbt431` с точным поворотом/переносом координат блока.

3. **`freeSpot` мог выбрать точку за внешней стеной или под открытым небом (`NpcGraphAnchor420`)**:
   - Если полка висела на внешней тонкой стене помещения (с одной стороны улица, с другой — комната), прежняя эвристика `hasFloorOrCeiling` проверяла только наличие пола или потолка, поэтому могла выбрать точку прямо **за стеной на улице**, где у игрока под ногами есть земля.
   - Введена оценка замкнутости помещения `interiorScore(level, pos)`: учитываются потолок, пол и боковые стены вокруг клетки, с резким предпочтением закрытых внутренних клеток комнаты (`ceiling && floor`, затем окружающие стены).

4. **Потеря стартового якоря до телепортации в данж (`DungeonManager425`)**:
   - При генерации стартовой комнаты `i == 0` до телепортации игрока целевой чанк ещё не активирован для тикания сущностей (`ENTITY_TICKING`), и попытка спавна нового якоря при `placeSlice` могла быть отклонена движком, если у комнаты не было заранее запечатанных сущностей.

## Что исправлено в Build 438

- **Подсветка полок и мебели жезлом (`build303-src/src/dev/settledlands/npc/RadiusPass354.java`)**:
  - При удержании жезла НПС (`NpcContent.wand()`) собираются кандидаты (`HighlightCandidate`) из всех точек обхода, включая обычные блок-триггеры (`BlockTriggerStore351`) **и** всю мебель/сундуки из `ChestGraph396Store` (`ChestSpot`), за исключением дверей (`spot.door`).
  - Список кандидатов сортируется по расстоянию до ближайшего игрока в радиусе `HIGHLIGHT_RANGE = 24.0`, лимит одновременной подсветки увеличен с `24` до `MAX_HIGHLIGHT = 64` (отрисовываются 8 угловых частиц `ParticleTypes.ELECTRIC_SPARK` каждые 2 тика).

- **Дублирование графа полки прямо в `BlockEntity` (`SettledLandsChestGraph` в `NeoForgeData`)**:
  - **`ChestGraph396` (`build303-src/src/dev/settledlands/chest/ChestGraph396.java`)**:
    - Добавлены методы `writeBlockEntityGraph(...)`, `readBlockEntityGraph(...)`, `ensureBlockEntityGraph(...)` и `syncEmbeddedGraphsNear(...)`.
    - При открытии редактора (`ChestGraph396.open`) и каждом сохранении/удалении графа или настроек замка (`ChestGraph396.save`) актуальный граф (включая ноды `loot_pool` и поля замка `ChestLock397`) пишется в `((IBlockEntityExtension) be).getPersistentData().put("SettledLandsChestGraph", ...)` (и удаляется при очистке графа).
    - `syncEmbeddedGraphsNear(...)` сканирует уже загруженные чанки (`ServerChunkCache.getChunkNow`, без принудительной прогрузки чанков) вокруг игроков в радиусе 2 чанков, восстанавливая графы полок из `NeoForgeData` в `ChestGraph396Store` при вставке чертежа Axiom прямо в мире.
  - **`NpcGraphAnchorFiles420` (`build303-src/src/dev/settledlands/npc/NpcGraphAnchorFiles420.java`)**:
    - При сборе графов в якорь (`collect`) гарантируется наличие `SettledLandsChestGraph` в каждом `BlockEntity` сундука/полки (`ensureBlockEntityGraph`).
    - При материализации якоря (`materialize` / sink `chest`) восстановленный граф сразу записывается также и в `BlockEntity` (`writeBlockEntityGraph`).

- **Гарантированное создание и перенос якорей (`NpcGraphAnchor420`, `DungeonTemplate425`, `LootPoolManager436`, `DungeonManager425`)**:
  - **`NpcGraphAnchor420` (`build303-src/src/dev/settledlands/npc/NpcGraphAnchor420.java`)**:
    - Перед проходом по маркерам в `passLevel(...)` вызывается `ChestGraph396.syncEmbeddedGraphsNear(...)`, так что даже при Axiom-копировании без сущностей полки сразу получают записи в `ChestGraph396Store` и живой `settledlands:guide_trigger`.
    - Лимит `ensureAnchors` за один тик увеличен до `8`.
    - Добавлен **`ensureForPlayer(ServerPlayer)`**, который при необходимости немедленно проверяет чанк/окрестность игрока (`ensureAnchors(...)`), не ожидая 1200-тикового интервала.
    - **`freeSpot(...)`** переведён на `interiorScore(...)` — настенные полки ставят маркер строго внутрь комнаты под потолком, а не снаружи.
  - **`LootPoolManager436` (`build303-src/src/dev/settledlands/chest/LootPoolManager436.java`)**:
    - Добавлена перегрузка `onDungeonRoomPlaced(ServerLevel, List<Entity>, int knownTurns, Set<BlockPos> populatedOut)`, передающая уже заполненные координаты контейнеров для защиты от двойного ролла лута.
  - **`DungeonTemplate425` (`build303-src/src/dev/settledlands/dungeon/DungeonTemplate425.java`)**:
    - Добавлены `hasEmbeddedChestGraphs(StartTemplate)` и `extractEmbeddedChestGraph(CompoundTag)`.
    - В `placeBlockEntitiesAndEntities(...)`:
      - При наличии у установленного `BlockEntity` встроенного `SettledLandsChestGraph` граф автоматически регистрируется в `ChestGraph396Store`, а контейнер наполняется лутом (если ещё не был наполнен через `anchor` — двойного ролла никогда не происходит).
      - Если в комнате `.bp` были полки с `SettledLandsChestGraph`, а живого запечатанного `settledlands:guide_trigger` нет (например, `.bp` сохранён в Axiom без сущностей или с пустым `guide_trigger`), шаблон переиспользует пустой `guide_trigger` либо сам спавнит новый `settledlands:guide_trigger` через `NpcGraphAnchor420.freeSpot` и запечатывает его всеми восстановленными графами комнаты.
  - **`DungeonManager425` (`build303-src/src/dev/settledlands/dungeon/DungeonManager425.java`)**:
    - `hasEntities(...)` считает также шаблоны с `hasEmbeddedChestGraphs(pt)` (ожидая полной готовности `ENTITY_TICKING` чанков при пошаговой расстановке комнат).
    - `enterDungeon(...)` и `createAndEnterUniqueInstance(...)` вызывают `NpcGraphAnchor420.ensureForPlayer(player)` сразу после телепортации, страхуя стартовую комнату от потери сущности до загрузки чанка.

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build438.py` -> `settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar`
- `tools/verify_jar.py --expect-version 0.8.0-build438-shelf-highlight-axiom-anchor-fix --base settledlands-0.8.0-build437-anchor-furniture-fix.jar` — **PASS** (`validate_jar`: `689` записей / `634` класса; `jvm_verify`: `634` класса valid for Java 21; `check_mixin_class`: `113` миксинов, `1450` внешних API-ссылок, `0` неподтверждённых; `dangling_refs`: `0`)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build437-anchor-furniture-fix.jar` — **OK** (`6102` call target'ов сверено)
- `tools/build438_check.py --base settledlands-0.8.0-build437-anchor-furniture-fix.jar` — **PASS**:
  - `mixin_clash_check`: **OK**
  - `door_graph_check`: **OK**
  - `anchor_check`: **OK** (`Anchor420Probe: 62 checks passed`)
  - `Build436Probe`: **`7305 checks passed`**
  - `Build437Probe`: **`22 checks passed`**
  - `Build438Probe`: **`32 checks passed`** (подсветка 8 искрами полок/шкафов/ящиков + блоков без дверей, лимит `64` и сортировка ближайших, сохранение и удаление `SettledLandsChestGraph` в `BlockEntity` `NeoForgeData`, вставка в мире Axiom без сущностей с автосозданием якоря и подсветкой, выбор позиции `freeSpot` внутри закрытой комнаты, спавн `.bp`-комнаты в данже с `0` сущностей, переиспользование пустого `guide_trigger` без двойного ролла лута, спасение стартового якоря при входе в данж)
- `tools/build437_check.py` — **PASS**
- `tools/build436_check.py` — **PASS** (`7305` + `4` + регрессии `396/397/401/402`)
- `tools/hell_dungeon_check.py` — **PASS** (`HellDungeon425Probe: 6297`, `HellDungeon427Probe: 11088`, `CreateCompat434Probe: 7`)
- `tools/teleport_check.py` — **PASS** (`Teleport424Probe: 88`)
- `tools/block_trigger_check.py` — **PASS**
