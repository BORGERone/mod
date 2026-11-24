# Settled Lands 0.8.0 Build 434 — Исправление спавна крутящихся механизмов Create (`create:stationary_contraption`)

База: `settledlands-0.8.0-build433-nbt-contraption-and-spawn.jar`

## Почему пропадала крутящаяся часть в Build 433 (и что ломает сам Create)

В результате детального разбора исходников Minecraft 1.21.1, Create (`mc1.21.1/dev`) и байткода Build 433 выявлены **две независимые причины**:

### 1. Причина пропажи в данже Build 433: сброс `Pos` при `Entity.load(te.mcNbt)` + ложная разборка подшипника
1. **Сброс координат в `(0, 0, 0)` внутри `Entity.load(te.mcNbt)`**:
   В Build 433 метод `DungeonNbt431.transformEntityPlacement` удалял тег `"Pos"` из `te.mcNbt`, рассчитывая, что `entity.moveTo(te.wx, te.wy, te.wz)` перед `load` сохранит позицию сущности.
   Однако ванильный `Entity.load(CompoundTag)` читает `ListTag("Pos")`, а если тега `"Pos"` нет, получает пустой список и вызывает `setPosRaw(0.0, 0.0, 0.0)` **до** вызова `ControlledContraptionEntity.readAdditional(...)`.
   В итоге `ControlledContraptionEntity` вычислял `controllerPos = ControllerRelative + blockPosition()` от `(0, 0, 0)` (то есть искал подшипник в `[1, 0, 0]` в начале координат мира!), не находил его и **удалял сам себя на 1-м серверном тике** (или при `super_glue` смещал коллизию клея в `(0, 0, 0)`).
2. **Преждевременный `updateGeneratedRotation()` на подшипнике в `CreateCompat431`**:
   В Create `MechanicalBearingBlockEntity` наследует `GeneratingKineticBlockEntity` (ради подкласса `WindmillBearingBlockEntity`), но сам ничего не генерирует (`getGeneratedSpeed() == 0`). Вызов `updateGeneratedRotation()` на нём в Pass 2 до прихода вращения от `create:creative_motor` выставлял `assembleNextTick = true` при нулевой скорости, что на 1-м тике могло приводить к немедленной разборке/остановке контрапции.
3. **Необновлённый `boundingBox` после `load`**:
   Во время `Entity.load` сущность контрапции вызывала `reapplyPosition()` до чтения поля `contraption`, когда границы коллизии ещё пустые; повторный `moveTo` после `load` пересчитывает `boundingBox` уже по загруженному `Contraption.bounds`.

### 2. Почему ломается вращение/отзеркаливание схематик в самом Create (ваши тесты)
Вы абсолютно точно заметили ванильный баг **Create** (подтверждено по официальному коду `SchematicPrinter`, `SchematicProcessor`, `ControlledContraptionEntity`, `StructureTransform` на `mc1.21.1/dev`):
- Create при повороте или отзеркаливании схематики трансформирует только блоки и `BlockEntity` (через `StructureTransform`), а для уже собранных сущностей `create:stationary_contraption` (`ControlledContraptionEntity` не переопределяет `Entity.rotate`/`mirror`) внутренний NBT (`ControllerRelative`, `Axis`, `Contraption.Anchor`, `Contraption.Facing`, `Contraption.BoundsFront`, `Contraption.Blocks`, `Actors`) **вообще не поворачивает и не переносит**. Из-за этого после поворота/инвертирования схематики пушкой или в меню предпросмотра Create подшипник и контрапция перестают совпадать по `ControllerRelative`/`Anchor`/`Facing`, и контрапция исчезает.

**Как правильно работать со схематиками:**
- **Для данжей Settled Lands (`Build 434`)**: сохраняйте `.nbt` схематику **без** ручного поворота/отзеркаливания пушкой Create (прямо в исходном виде, как `а Астролябия 1.nbt`). Мод сам поворачивает комнату и все внутренние теги `stationary_contraption` и `mechanical_bearing` на `0° / 90° / 180° / 270°` при генерации данжа!
- **Если нужно заранее повернуть или отзеркалить постройку инструментами Create перед сохранением нового `.nbt`**: перед копированием в схематик остановите подшипник ПКМ (чтобы крутящаяся часть снова стала обычными блоками) — тогда Create корректно повернёт/отзеркалит все блоки и сам подшипник. Даже остановленный подшипник (`Running=0`), если к нему примыкает активный двигатель (`create:creative_motor` и др.), автоматически соберёт конструкцию обратно при активации в игре.

## Что изменено в Build 434

1. **`dev/settledlands/dungeon/DungeonNbt431.java`**
   - В `transformEntityPlacement` (для контрапций, `create:super_glue` и обычных сущностей) и в `transformContraptionNbt` вместо удаления `"Pos"` записывается точный пересчитанный `Pos = [wx, wy, wz]` (`TAG_List` of `TAG_Double`).
   - Для контрапций с дробным смещением относительно `Anchor` (например, полу-выдвинутые поршни) положение якоря и `ControllerRelative` пересчитываются в мировых целочисленных координатах без ошибки `Math.floor`.
   - Дополнительно поворачиваются вложенные поля `Controller` в `Data`, `CapturedMultiblocks` (`Controller`, `Parts`) и `Interactors`.
2. **`dev/settledlands/dungeon/CreateCompat431.java`**
   - Добавлен метод `attachContraption(ServerLevel level, Entity entity)` — сразу при спавне `ControlledContraptionEntity` привязывает её к `IControlContraption` (подшипнику/контроллеру) через `attach(...)`, если та ещё не привязана.
   - В `activateCreateRoom` обычный `MechanicalBearingBlockEntity` (не ветряк) перенесён в Pass 1 (`attachKinetics()`) и полностью исключён из Pass 2 (`updateGeneratedRotation()`), чтобы вращение приходило строго от моторов/водяных колёс без ложной разборки на нулевой скорости.
3. **`dev/settledlands/dungeon/DungeonTemplate425.java`**
   - После `entity.load(te.mcNbt)` повторно вызывается `entity.moveTo(te.wx, te.wy, te.wz)` (для пересчёта `boundingBox` из `Contraption.bounds`).
   - Сразу после `level.addFreshEntity(entity)` вызывается `CreateCompat431.attachContraption(level, entity)`.

## Сборка и проверки

Скомпилировано через `ecj-3.46.100.jar`, проверено:
- `python3 tools/verify_jar.py --base settledlands-0.8.0-build433-nbt-contraption-and-spawn.jar settledlands-0.8.0-build434-contraption-pos-fix.jar`
- `python3 tools/invoke_kind_check.py --base settledlands-0.8.0-build433-nbt-contraption-and-spawn.jar settledlands-0.8.0-build434-contraption-pos-fix.jar`
- `python3 tools/hell_dungeon_check.py settledlands-0.8.0-build434-contraption-pos-fix.jar` (включая проверку реальной `а Астролябия 1.nbt` и JVM-тест `CreateCompat434Probe` с моками Create).
