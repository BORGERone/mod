# Settled Lands 0.8.0 Build 436 — Мебель для хранения Refurbished Furniture (+ Diegesis / Handson Furniture) и узел «Пул предметов» для данжей

База: `settledlands-0.8.0-build435-contraption-ticking-spawn.jar`

## Что добавлено в Build 436

### 1. Поддержка стандартной мебели для хранения из Refurbished Furniture в графе сундуков
- **`ChestGraph396.isChest` / `isChestAt`**: теперь наряду с ванильными `ChestBlock` и `BarrelBlock` поддерживаются контейнеры хранения из пространства имён `refurbished_furniture`, имеющие `BlockEntity implements Container`, с суффиксами:
  - `_crate` (ящики)
  - `_drawer` (включая `_kitchen_drawer`)
  - `_cooler` (переносные холодильники/кулеры)
  - `_storage_cabinet` (включая `_kitchen_storage_cabinet`)
  - `_fridge` (`light_fridge`, `dark_fridge`)
  - `_mail_box` (почтовые ящики)
  - `_storage_jar` (банки для хранения)
- Технологические приборы и не-контейнеры (`*_freezer`, `*_microwave`, `*_stove`, `*_toaster`, `*_grill`, `frying_pan`, `*_cutting_board`, `recycle_bin`, `*_electricity_generator`, `workbench`, `post_box`) намеренно исключены.
- Для всей поддерживаемой мебели работают палочка редактора, граф сундука, замок, мини-игра взлома (`HackPanel377`), цепи «открытие»/«взлом», сохранение в якорь (`NpcGraphAnchor420`) и перенос в данжах.

### 2. Совместимость с `Diegesis` и `Handson Furniture` (и работа без них)
- **`ChestClientEvents436`**: клик палочкой редактора по сундуку или мебели на клиенте отменяется с `InteractionResult.SUCCESS` (аналогично `DoorEvents368`), чтобы `diegesis` не захватывал камеру в 3D-режим, при этом пакет клика всё равно доходит до сервера и открывает граф сундука.
- **`ChestDiegesisCompat436` + `ChestGraph396Events`**: при отслеживании закрытия сундука (`onTick`) помимо `containerMenu != inventoryMenu` мягкой рефлексией проверяется открытая 3D-станция `Diegesis.activeStation(ServerLevel, BlockPos)` и список игроков `StationContext.players()` — цепь «открытие» срабатывает и после выхода из 3D-режима.
- **`ChestOpen401.openFace`**: авто-открытие после взлома больше не использует жёсткий `Direction.UP` для всех блоков — сначала определяется лицевая грань по свойству `direction` (`dir.getOpposite()`), что требуется `DrawerBlock` / `StorageCabinetBlock` / `FridgeBlock` при игре без `handson_furniture`, иначе используется грань клика.
- **`DungeonTemplate425.rotateProps`**: добавлен поворот свойства `direction` наряду с `facing` / `target`, чтобы мебель Refurbished корректно поворачивалась во всех 4 ориентациях комнат данжа.

### 3. Узел «Пул предметов» («Item Pool») в редакторе графа сундука
- **Редактор графа сундука (`BlockMenu363`, `NpcChestPalette396Mixin`, `LootPoolCard436`, `LootPoolGraphUiMixin436`, `LootPoolInspectorMixin436`, `LootPoolWiringMixin436`, `LootPoolActionsMixin436`)**:
  - В контекстном меню графа сундука добавлен пункт **`+ Пул предметов`** (в остальных графах его нет).
  - Карточка и инспектор позволяют выбрать `.txt` файл пула из выпадающего списка (синхронизируется сервером по пакету `settledlands:chest_loot_pools` через `ChestLootNet436` с локальным fallback), а также указать:
    - **Макс. редкость** (`rarity` потолок: число `50` или диапазон `0-100`, пусто = без ограничений),
    - **Макс. вес** (`weight` потолок: число `50` или диапазон `0-100`, пусто = без ограничений),
    - **Кол-во предметов** (число бросков `3` или диапазон `1-4`).
  - Хранится в `NpcDialogueGraph.TRIGGER` (`trigger:loot_pool:<poolFile>|<maxRarity>|<maxWeight>|<rolls>`), полностью совместимо с якорем чертежей (`NpcGraphAnchor420`) и соединяется последовательно (включая `Условие If` и `Случайность`).
- **Формат файлов `config/settledlands/loot_pools/*.txt`**:
  - При первом обращении автоматически создаётся папка `config/settledlands/loot_pools/` со справочным файлом `README_FORMAT.txt.disabled` (не попадает в список `.txt`).
  - Каждая строка в `.txt`:
    `ID_предмета | редкость | вес | [количество или мин-макс]`
    Пример:
    ```
    minecraft:cooked_beef | 5  | 80 | 2-6
    minecraft:iron_ingot  | 20 | 50 | 1-4
    minecraft:diamond     | 85 | 10 | 1
    ```
  - На каждом броске выбираются предметы с `rarity <= capRarity` и `weight <= capWeight`, а среди прошедших предмет выбирается случайно пропорционально его весу `weight`.
- **Генерация только в данжах SettledLands (`LootPoolManager436` + `DungeonTemplate425` + `NpcGraphAnchor420`)**:
  - При размещении комнаты `settledlands:hell_dungeon` с якорем `settledlands:guide_trigger` (`materialiseForDungeon`) контейнеры с нодой «Пул предметов» автоматически заполняются в пустые слоты.
  - Вне `settledlands:hell_dungeon` (и при обычных кликах по сундуку) нода не генерирует предметы.
  - Учитываются нативные слоты `RoomyContainer.handson$ownSize()` (чтобы лут не попадал в расширенные слоты 3D-сцены за пределами обычного GUI) и ограничения контейнера (`StorageJar` `getMaxStackSize() == 1` и `canPlaceItem`).

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build436.py` -> `settledlands-0.8.0-build436-furniture-loot-pools.jar`
- `tools/verify_jar.py --base settledlands-0.8.0-build435-contraption-ticking-spawn.jar` — **PASS** (`validate_jar`: 688 entries / 633 classes; `jvm_verify`: 633 classes valid for Java 21; `check_mixin_class`: 113 mixins verified, 0 unconfirmed refs; `dangling_refs`: 0)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build435-contraption-ticking-spawn.jar` — **PASS** (6082 call targets)
- `tools/mixin_clash_check.py` — **PASS** (240 helpers in 83 shared-target mixins, 0 clashes)
- `tools/build436_check.py` — **PASS** (`Build436Probe: 7305 checks passed`, `Build436CompatProbe: 4 checks passed` с живыми mock-контрактами `Diegesis` и `RoomyContainer`, `ChestRegressionProbes (396/397/401/402): PASS`)
- `tools/teleport_check.py` — **PASS** (`Teleport424Probe: 88 checks passed`)
- `tools/hell_dungeon_check.py` — **PASS** (`HellDungeon425Probe: 6297`, `HellDungeon427Probe: 11088`, `CreateCompat434Probe: 7`)
- `tools/anchor_check.py` — **PASS** (`ANCHOR_CHECKS=56`)
