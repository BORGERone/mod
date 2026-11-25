# Settled Lands 0.8.0 Build 439 — Естественная 3D-раскладка лута в мебели Hands-On Furniture / Diegesis

База: `settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar`

## Найденная проблема

При заполнении данжевых контейнеров Refurbished Furniture с модами **Hands-On Furniture / Diegesis** (шкафы `storage_cabinet`, комоды `drawer`, холодильники `fridge`, кулеры `cooler`, почтовые ящики `mail_box`) метод `LootPoolManager436.populateContainer(...)` записывал предметы через стандартный `Container.setItem(slot, stack)`.

Из-за того что `StorageLogic` в `handson_furniture` при вставке предметов через `Container` оставляет позы `ItemPose` равными `null`, клиентская сцена Diegesis (`PlacedItem.update` -> `SceneInstance.freeSpot` -> `Placing.freeSpot`) раскладывала весь сгенерированный лут **строго по ровной сетке** от задней стенки к передней с шагом `0.5`, нулевым углом поворота (`yaw = 0`), одинаковым масштабом и почти исключительно на верхнюю/первую полку — выглядело искусственно, как склад магазина, а не естественный живой интерьер.

## Архитектура решения (гибрид сервер + клиент)

1. **Серверная пометка сидом (`SettledLandsScatterSeed`) без догадок о 3D-размерах моделей**:
   - **`LootPoolManager436` (`build303-src/src/dev/settledlands/chest/LootPoolManager436.java`)**: при успешном наполнении контейнера собирает индексы заполненных слотов и после записи предметов (до `be.setChanged()`) вызывает `ChestDiegesisCompat436.markLootScatter(be, populatedSlots, scatterSeed)`.
   - **`ChestDiegesisCompat436` (`build303-src/src/dev/settledlands/chest/ChestDiegesisCompat436.java`)**:
     - Безопасно через рефлексию проверяет, является ли `BlockEntity` станцией `StationHost` (без единой жёсткой ссылки на классы `com.pockyl.*` — выделенный сервер и клиенты без Diegesis работают без ошибок).
     - Очищает устаревшие позы на заново заполненных или уже пустых слотах группы `"items"` (не затрагивая другие группы слотов).
     - Записывает ненулевой детерминированный сид `SettledLandsScatterSeed` в `StationState#data()` и синхронизирует хранилища (`pollStorages`). Мебель, поставленная игроками вручную (`seed == 0`), никак не затрагивается.

2. **Точная клиентская 3D-раскладка по поверхностям и моделям (`DiegesisScatterMixin439` + `DiegesisScatterClient439`)**:
   - **`DiegesisScatterMixin439` (`build303-src/src/dev/settledlands/mixin/compat/DiegesisScatterMixin439.java`)**: опциональный `@Pseudo` клиентский миксин в `settledlands.compat.mixins.json` (`required: false`, `require = 0`), перехватывающий `SceneInstance#freeSpot(PlacedItem, PlacedShape)` только при наличии `SettledLandsScatterSeed != 0`.
   - **`DiegesisScatterClient439` (`build303-src/src/dev/settledlands/chest/DiegesisScatterClient439.java`)**:
     - Распределяет лут по **всем** доступным поверхностям мебели (`PlaceSurface`) со взвешенным случайным порядком (`area^0.75`), благодаря чему задействуются и верхние, и нижние полки шкафов, закрытые ящики комодов и дверные карманы холодильника.
     - Замеряет точные 3D-габариты каждой модели после поворота и масштабирования (`PlacedShape#extents(rotation, itemSize * scale)`) и проверяет зазор до потолка полки (`clearance`).
     - Учитывает открытые края полок (`PlaceSurface#open()`, отступ `OPEN_PAD = 0.45` / `0.25` на узких карманах), чтобы физика `PlacedPhysics` не сбрасывала предметы наружу.
     - Варьирует угол `yaw`, лежачее/стоячее положение плоских предметов (`flatLies()`), масштаб внутри `scaleRange` (с понижением при тесноте), а при удачных условиях аккуратно укладывает плоский/малый предмет на 1-уровневую стопку поверх широкого плоского предмета (`on = supportSlot`).
     - Ищет свободное место в 3 фазы (разреженное расположение -> плотный случайный подбор -> перемешанный сеточный проход с джиттером) с проверкой всех 3D AABB, гарантируя отсутствие пересечений.
     - Полная совместимость со всеми версиями Diegesis 1.21.1 (`beta.12–15` и `beta.17–19.3`): 7-аргументный конструктор `ItemPose`, опциональные `flatLies()` / `leastHalf()` / `DiegesisClient#addBehavior`, а также резервное слежение `tickWatch()` через **`ChestOpenWatch401`** и регистрация поведения на `FMLClientSetupEvent` через **`ChestScatterClientInit439`**.
     - Автоматически пересчитывает раскладку при запоздалой доставке `BlockEntityDataPacket` с сидом, даже если сцена успела инициализироваться со стандартной сеткой.

3. **Одноразовая валидируемая фиксация поз на сервере (`settledlands:chest_loot_scatter`)**:
   - **`ChestLootNet436` (`build303-src/src/dev/settledlands/chest/ChestLootNet436.java`)**: добавлен C2S-пакет `ScatterPoses` (`settledlands:chest_loot_scatter`) с компактным Base64-кодеком `PoseEntry` (по 40 байт на слот), отправляемый один раз при открытии/фокусе контейнера вместо десятков штатных поштучных пакетов (обход лимита `ACTION_BUDGET_PER_SECOND = 40`).
   - Серверный обработчик `ChestDiegesisCompat436.commitScatteredPoses(...)` проверяет: жив ли игрок, загружен ли чанк, дистанцию `<= 64` блоков, точное совпадение `SettledLandsScatterSeed`, существование непустых слотов и отсутствие циклов в поле `on`, прогоняет каждую позу через `ItemPose#sanitized()`, записывает их в `StationState`, **удаляет** `SettledLandsScatterSeed` (повторный пакет или второй игрок не смогут перезаписать позы) и сохраняет состояние через `be.setChanged()`.

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build439.py` -> `settledlands-0.8.0-build439-furniture-3d-loot-scatter.jar`
- `tools/verify_jar.py --expect-version 0.8.0-build439-furniture-3d-loot-scatter --base settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar` — **PASS** (`validate_jar`: `699` записей / `644` класса; `jvm_verify`: `644` класса valid for Java 21; `check_mixin_class`: `114` миксинов, `1450` внешних API-ссылок, `0` новых неподтверждённых; `dangling_refs`: `0`)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar` — **OK** (`6162` call target'ов сверено)
- `tools/build439_check.py --base settledlands-0.8.0-build438-shelf-highlight-axiom-anchor-fix.jar` — **PASS**:
  - `mixin_clash_check`: **OK** (`settledlands.compat.mixins.json` содержит `required=false`, `ApothDedupMixin` и клиентский `DiegesisScatterMixin439`; ни в одном не-миксин классе мода нет жёстких ссылок на `com/pockyl/*`)
  - `door_graph_check`: **OK**
  - `anchor_check`: **OK** (`ANCHOR_CHECKS=62`)
  - `Build436Probe`: **`7305 checks passed`**
  - `Build437Probe`: **`22 checks passed`**
  - `Build438Probe`: **`32 checks passed`**
  - `Build439Probe`: **ALL 8 integration & geometry checks passed!** (заполнение `storage_cabinet`, `fridge` с дверными карманами, `drawer` с `flatLies`, нетронутая пользовательская мебель при `seed=0`, C2S-протокол с отклонением далёкого игрока / неверного сида / реплея, поздняя синхронизация сида, тик-наблюдатель `ChestOpenWatch401`, безопасность ванильных сундуков — **0 коллизий AABB** во всех сценах)
  - `Build439LegacyProbe`: **Diegesis beta.12-15 fallback + tickWatch PASS** (проверка при физическом отсутствии `flatLies`, `leastHalf`, `DiegesisClient#addBehavior`)
  - `Build439NoDiegesisProbe`: **zero-Diegesis safety PASS** (проверка без установленного мода Diegesis)
- `tools/build438_check.py` — **PASS**
- `tools/build437_check.py` — **PASS**
- `tools/build436_check.py` — **PASS** (`7305` + `4` + регрессии `396/397/401/402`)
- `tools/hell_dungeon_check.py` — **PASS** (`6297` + `11088` + `7`)
- `tools/teleport_check.py`, `tools/block_trigger_check.py`, `tools/door_screen_check.py`, `tools/punchy_bridge_check.py` — **PASS**
