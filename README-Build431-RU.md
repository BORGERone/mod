# Settled Lands — Сборка 431 (`0.8.0-build431-create-nbt-support`)

База: `settledlands-0.8.0-build430-dungeon-biomes-doors.jar` (`0.8.0-build430-dungeon-biomes-doors`), без новых миксинов и без новых внешних API относительно Build 430.

## Что добавлено и исправлено в Build 431

1. **Поддержка Create (`.nbt` из Schematicannon / Schematic & Quill) и ванильных `StructureTemplate .nbt` в «Адском данже»**
   - **`DungeonConfig427`** и **`DungeonBlueprints427`**:
     - При `blueprints_dir=auto` теперь автоматически сканируются `config/settledlands/blueprints`, `config/axiom/blueprints`, `blueprints` и **`schematics`** (если есть файлы `.bp` или `.nbt`); также поддерживается смешанный набор `.bp` + `.nbt` в одной папке (`config/settledlands/blueprints`).
     - Стартовый шаблон (`старт`) может быть как `.bp`, так и `.nbt`; в `.nbt` поддерживается отсутствие `lapis_block` в `старт` (автоматический спавн по центру комнаты с пропуском `sealStartEntrance`) и любое количество выходов `redstone_block` (`0+`).
     - В статистике `Library.summaryLine()`, `/settledlands dungeon info` и `/settledlands dungeon reload` добавлен счётчик `.nbt` и статус интеграции Create (`Create: активен/нет`), при этом все существующие форматы `.bp`, темы `к`/`а`/`п` и префикс `"блупринты перечитаны"` полностью сохранены.
   - **`CreateSchematic431`**:
     - Нативный Java-парсер gzip-сжатых и несжатых `.nbt` структур Create и Minecraft (`palette` или `palettes[0]`, `blocks`, `entities`), пропускающий `minecraft:air` / `cave_air` / `void_air` / `structure_void`, распознающий маркеры `minecraft:lapis_block` и `minecraft:redstone_block`, и сохраняющий полные NBT `BlockEntity` и `Entity`.
     - Блоки сортируются по тем же 3 безопасным фазам (`0`: конструктив снизу вверх -> `1`: хрупкий декор -> `2`: двери колонками `(x, z, y)`), что и в Build 430.

2. **Точные NBT-повороты (включая рефакторинг NBT из `AxiomBlueprint427` -> `DungeonNbt431`)**
   - В **`DungeonNbt431`** вынесены классы чтения/сериализации NBT (`NbtCompound`, `NbtValue`, `NbtReader`, `CountingInputStream`, `TransformedEntity`) — с полной обратной совместимостью с внешними вызовами `AxiomBlueprint427`.
   - **`DungeonNbt431.transformBlockEntityNbt`** и **`transformEntityNbt`** выполняют точный поворот на `0° / 90° / 180° / 270°` по часовой стрелке (`turns = 0..3`):
     - `x`/`z` или `X`/`Z`, массивы координат `TargetOffset`, `Target`, `Pos`, `From`, `To`, списки `Connections`, `InteractionPoints`, `TravellingPackages` (Create `chain_conveyor`), `Input`, `Bracket`, строковые поля сторон (`Facing`, `Axis`, `Side`, `Direction`);
     - Многоблочные структуры Create (`FluidTankBlockEntity`, `ItemVaultBlockEntity`): при повороте `turns != 0` или на дочерних сегментах сбрасывается устаревший абсолютный `Controller` и выставляются `Size=1` / `Uninitialized=1b`, чтобы после спавна `updateConnectivity()` пересобрал резервуары/хранилища с нуля (сохраняя инвентари/жидкости `1x1` при `t == 0`);
     - Механические ремни (`BeltBlockEntity`): на `turns != 0` сбрасываются `Controller` / `IsController` / `Length` / `Index` с сохранением корпуса `Casing`, `Covered` и красителя `Dye`;
     - Ветряные подшипники (`WindmillBearingBlockEntity`): выставляется `QueueAssembly=1b` и НЕ вызывается `updateGeneratedRotation()` до первого тика (чтобы не сбросить `queuedReassembly=false` до сборки контрапции);
     - Сущности `create:super_glue`: корректный пересчёт `From`/`To` с учётом смещения `Entity.moveTo(...)` до `load(...)`.

3. **Повороты блочных свойств и пост-размещение живых механизмов Create (`DungeonTemplate425` + `CreateCompat431`)**
   - **`DungeonTemplate425.rotateProps`** и **`CreateCompat431`**:
     - Если Create загружен, поворот `BlockState` дополнительно сверяется/делегируется через `com.simibubi.create.content.contraptions.StructureTransform` (чистой рефлексией, без жёсткой зависимости в байткоде и без сбоев при отсутствии Create).
     - Исправлен поворот **`create:chain_drive`** (`axis` + `connected_along_first_coordinate` с пересчётом `connAxis = connFirst ? (axis==X ? Y : X) : (axis==Z ? Y : Z)` и инверсией `part` `start`/`end` только при смене знака оси соединения) и **`create:encased_cogwheel`** (`top_shaft` <-> `bottom_shaft`), а также **`create:belt`** (`slope=vertical` -> смена `part` при развороте знака горизонтальной оси).
     - **`CreateCompat431.activateCreateRoom`** вызывается из `DungeonManager425` **только после завершения постройки всей комнаты** (`end >= total`), выполняя 3 прохода:
       1. `BeltBlock.initBelt`, `FluidTankBlockEntity#updateConnectivity`, `ItemVaultBlockEntity#updateConnectivity`, `KineticBlockEntity#attachKinetics`;
       2. Пробуждение генераторов кинетики (`reActivateSource=true` + `updateGeneratedRotation()`, пропуская `WindmillBearingBlockEntity`);
       3. `SmartBlockEntity#sendData()`, `be.setChanged()` и `ServerLevel.sendBlockUpdated(...)`.

## Проверки (45/45 гейтов PASS, детерминизм `cmp -s` PASS)

- **`tools/hell_dungeon_check.py`**: `HellDungeon425Probe` (`6 297` проверок PASS) + `HellDungeon427Probe` (`8 556` проверок PASS, включая `78` новых JVM-тестов Build 431: gzip/raw `.nbt`, `palettes[0]`, многоблочные `FluidTank` и `ItemVault`, `Belt` на `slope=vertical`, `ChainDrive` CW90/CCW90/vertical, `EncasedCogwheel`, `WindmillBearing` без раннего сброса `QueueAssembly`, `SuperGlue`, пост-активацию `CreateCompat431` и `ServerLevel.sendBlockUpdated`).
- **`tools/verify_jar.py --base settledlands-0.8.0-build430-dungeon-biomes-doors.jar`**: PASS (`672` записей, `617` классов, Java 21, `109` миксинов, **`0` новых внешних API** из `1 441`, `0` висячих ссылок).
- **`tools/invoke_kind_check.py --base settledlands-0.8.0-build430-dungeon-biomes-doors.jar`**: OK (`5 963` целей вызова).
- **Все `41/41` `*_check.py`** (`anchor`, `mixin_clash`, `event_getter`, `door_*`, `chest_*`, `hns_stealth`, `illegal_hack`, `hell_dungeon_check` и др.) — **PASS**.
- **Детерминированная сборка (`cmp -s`)**: JAR `settledlands-0.8.0-build431-create-nbt-support.jar` (`3 842 799` байт) и Source ZIP `SettledLands-Build431-CreateNbtSupport-Source.zip` воспроизводятся байт-в-байт.

## Как использовать в игре

1. Положите `settledlands-0.8.0-build431-create-nbt-support.jar` в папку `mods/` (с Create или без него — при отсутствии Create `.nbt` структуры всё равно ставятся с корректными ванильными/NBT-поворотами и без крашей).
2. Положите `.nbt` схемы Create (или смешайте с `.bp` Axiom) в `config/settledlands/blueprints/` или стандартную папку `schematics/` (при `blueprints_dir=auto`).
3. Выполните `/settledlands dungeon reload` — в ответе увидите число найденных `.nbt`/`.bp` файлов и статус `Create: активен`.
4. Войдите в данж через `/settledlands dungeon enter` или узел **«Телепорт»** и проверьте, что конвейеры, цепные приводы, резервуары, хранилища и механизмы Create собираются и крутятся корректно при любом из 4 поворотов.
