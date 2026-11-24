# Settled Lands — Сборка 433 (`0.8.0-build433-nbt-contraption-and-spawn`)

База: `settledlands-0.8.0-build432-deadend-corridors.jar` (`0.8.0-build432-deadend-corridors`), без новых миксинов и без новых внешних API относительно Build 432.

## Что исправлено в Build 433

Исправлены две причины, из-за которых сложные `.nbt`-комнаты Create (на примере `а Астролябия 1.nbt` с `create:mechanical_bearing` и `create:stationary_contraption`) не спавнились в данже или спавнились со сломанной / невращающейся контрапцией.

### 1. Полный 3D-поворот и запуск Create Contraption (`DungeonNbt431`, `CreateSchematic431`)

Ранее при повороте комнаты (`turns = 0..3`) блоки комнаты поворачивались вокруг `(0, 0)`, а сущность `create:stationary_contraption` и её внутренние данные оставались неповернутыми:
- **`Running=1` на `create:mechanical_bearing`**: сохраняется флаг `Running=1` (раньше сбрасывался в `0` вместе с `windmill_bearing`), при этом на `create:windmill_bearing` по-прежнему выставляется `Running=0, QueueAssembly=1`. Входная кинетика (`Source`, `Network`, `Speed`) сбрасывается и переподключается в `CreateCompat431.activateCreateRoom`.
- **Точное размещение контрапции (`transformEntityPlacement`, `moveBeforeLoad=true`)**: для `create:*contraption*` не используется `BlockPos.containing(...)` со сдвигом `+0.5`, чтобы не смещать угол anchor на 1 блок; сущность позиционируется через `rotX/rotZ` до загрузки NBT с `yaw=0, pitch=0`.
- **Поворот внутренних структур контрапции (`transformContraptionNbt` / `prepareContraptionNbt`)**:
  - поворачиваются `ControllerRelative` (указывает точно на повёрнутый подшипник), `Axis` (`X↔Z`), `Motion` и `RelativeMotion`;
  - в `Contraption`: обновляются `Anchor`, `Facing` (через `rotateDir3D`), `BoundsFront` (сохраняется тип элементов `TAG_Float`), блоковые состояния в `Blocks.Palette` (через `rotateStateTag`), упакованные локальные позиции и NBT блок-сущностей в `Blocks.BlockList` (`Pos` / `Data`), а также `Seats`, `Superglue` (`From`/`To` и 6-float AABB) и `Actors` (`Pos`, `Position` относительно нового `Anchor`, `Motion`, `RelativeMotion`);
  - в `CreateSchematic431` для контрапций без `pos` (`blockPos` fallback) сохраняется целочисленный угол без `+0.5`.

### 2. Надежная загрузка и гарантированный спавн `.nbt`-комнат (`DungeonBlueprints427`, `DungeonGenerator427`, `AxiomBlueprint427`, `DungeonCommand425`)

- **Замена `.bp` на `.nbt` без дублей**: если в одной папке лежат одноимённые `.bp` и `.nbt` (как `а Астролябия 1.bp` и `а Астролябия 1.nbt`), приоритет отдаётся `.nbt`, а прежняя `.bp` заменяется в библиотеке (убраны дубли `#nbt`/`#bp`); даже если `.nbt` лежит без префикса темы рядом с префиксным `.bp` (например `Астролябия 1.nbt` и `а Астролябия 1.bp`), `.nbt` заменяет `.bp` и наследует его тему.
- **Нет потери `.nbt` без префикса в биомном режиме**: в `auto->biomes(к->а+п)` пользовательские `.nbt` без префикса `к ` / `а ` / `п ` больше не отбрасываются в неиспользуемый `THEME_OTHER`, а автоматически подключаются в глубокие биомы данжа (`infernal` / `flesh`, либо `crypt` при отсутствии глубоких тем).
- **Повышенный приоритет первого спавна `.nbt`**: в `DungeonGenerator427.pickCandidateOrder` ещё не использованные в текущем данже `.nbt`-комнаты получают бонус оценки `-7.0` (перекрывая случайный разброс среди десятков `.bp`), пока каждая активная `.nbt`-комната не появится хотя бы один раз. После первого появления комната возвращается к обычному весу.
- **Прозрачная диагностика в `/settledlands dungeon info` и `reload`**: `Library.summaryLine()` показывает активные vs отфильтрованные/без-1-лаписа `.nbt`, а если `.nbt`-файлы забыты в другой папке (например в `schematics/`, когда активна `config/axiom/blueprints/Данж/`), выводится предупреждение с точным путём.

## Проверка по реальным схемам и тестам

- **Реальная проверка на `а Астролябия 1.nbt` + 232 `.bp` из `Данж.rar`** (`Build433RealCheck`):
  - Все 4 поворота (`turns = 0..3`): `ControllerRelative` указывает точно на повёрнутый `create:mechanical_bearing` (`Running=1`), `Axis`, `Anchor`, `Facing`, `BoundsFront` (`TAG_Float`), `Palette`, все `98` блоков `BlockList` и `Actors[0]` повернуты без потерь и смещений.
  - Генерация `100` сидов в каждом из сценариев (`а Астролябия 1.nbt + .bp`, только `а Астролябия 1.nbt`, нетематическая `Астролябия 1.nbt`): **спавн в `100/100` данжей (100%)** (против `0/100`, `35.5%` и дублирования в Build 432), в среднем `110.0` комнат и `0` стен-заглушек.
- **`tools/hell_dungeon_check.py`**: `HellDungeon425Probe` (`6 297` проверок) + `HellDungeon427Probe` (`439 644` проверок PASS, включая `testBuild433ContraptionAndNbtSpawning`).
- **`tools/verify_jar.py --base settledlands-0.8.0-build432-deadend-corridors.jar`**: PASS (`673` записей, `618` классов, Java 21, `109` миксинов, `0` новых внешних API, `0` висячих ссылок).
- **`tools/invoke_kind_check.py --base settledlands-0.8.0-build432-deadend-corridors.jar`**: OK (`5 987` целей вызова).
- Живая игра NeoForge + Create 6.0.9 (клиент/сервер Minecraft) в этой среде без GUI/мода Create не запускалась — требуется проверка входа в данж на сервере/клиенте.
