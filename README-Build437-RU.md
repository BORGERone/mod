# Settled Lands 0.8.0 Build 437 — Автосоздание, покрытие и перенос якорей графов для мебели/полок и данжей

База: `settledlands-0.8.0-build436-furniture-loot-pools.jar`

## Найденная проблема (почему терялись графы полок при редактировании и генерации данжа)

При редактировании постройки вроде `"старт"` и добавлении полок/шкафчиков из мода Refurbished Furniture (а также сундуков и дверей) графы не переносились в новый `settledlands:hell_dungeon` по совокупности **семи причин** в `NpcGraphAnchor420`, `ChestGraph396`, `BlockTrigger351`, `DoorGraph382`, `DoorEvents368`, `LootPoolManager436` и `DungeonTemplate425`:

1. **Якорь никогда не создавался для построек без блок-триггеров**: `NpcGraphAnchor420.graphPlaces` опрашивал только `BlockTriggerStore351`. Постройка, в которой были только полки/сундуки (`ChestGraph396Store`) или дверные графы/замки (`DoorGraph382Store`, `DoorStore368`), вообще не получала сущность-якорь `settledlands:guide_trigger`.
2. **Не было немедленной синхронизации якоря при сохранении графа**: после нажатия «Сохранить» в редакторе якорь обновлялся только по таймеру (раз в `1200` тиков / 60 секунд) или вручную палочкой. Если чертёж копировали или отправляли в данж сразу после редактирования полок, в маркере либо не было якоря, либо лежал устаревший список графов без новых полок.
3. **Мёртвая зона покрытия `24..32` блока (`ANCHOR_BLOCKS = 24` vs `GATHER_BLOCKS = 32`)**: `coveringMarker` искал существующий якорь в радиусе `32` блоков, но собирались в него только графы в радиусе `24`. Все полки на расстоянии `25–32` блоков от маркера считались «уже накрытыми» (новый маркер не ставился), но при этом не попадали внутрь маркера!
4. **`freeSpot` мог вынести якорь за крышу комнаты**: при создании якоря для настенной полки под самым потолком `freeSpot` сначала перебирал смещения `(0, +1..+3, 0)` и мог поставить `settledlands:guide_trigger` выше крыши — вне выделяемой области чертежа Axiom и вне `8`-блочного отпечатка комнаты.
5. **Ошибочное совпадение координат `(0, 0, 0)` между Обычным миром и `settledlands:hell_dungeon`**: штамп якоря хранил только `[x, y, z, turn]` без измерения (`SettledLandsAnchorDim`). Первый данж (`instance #0`) всегда генерируется в `(0, 0, 0)` — поэтому маркер, сохранявшийся в Обычном мире вокруг `(0, 0, 0)`, считал, что в данже в `(0, 0, 0)` он «никуда не сдвигался» (`moved == false`), и полностью пропускал материализацию графов!
6. **Хрупкий 60%-порог отпечатка без fallback на известный поворот комнаты**: при частичном изменении блоков комнаты (добавлены/убраны полки, заменены блоки дверей/стен в данже) `NpcGraphAnchorCore420.place(...)` мог не добрать `>= 60%` совпадения и отменял материализацию вместо использования поворота `turns`, который `DungeonTemplate425` уже точно знает.
7. **Пропуск якорей при отложенной активации чанка или пустом «запечатывании»**: `DungeonTemplate425` игнорировал `settledlands:guide_trigger`, если `addFreshEntity` возвращал `false` при спавне на границе ещё не тикающего чанка; а `NpcGraphAnchor420.seal` при удалении всех графов оставлял на маркере старые теги `Graphs`/`FP`/`MARK`/`SettledLandsAnchorAt`.

## Что исправлено в Build 437

- **`NpcGraphAnchor420` (`build303-src/src/dev/settledlands/npc/NpcGraphAnchor420.java`)**:
  - `graphPlaces(...)` теперь собирает графы и замки из **всех 4 хранилищ**: `BlockTriggerStore351`, `DoorGraph382Store`, `ChestGraph396Store` и `DoorStore368`.
  - Убран разрыв покрытия: радиус `GATHER_BLOCKS = 32` стал единым (с переходом через `ANCHOR_BLOCKS == GATHER_BLOCKS == 32`) — ни одна полка в пределах 32 блоков больше не выпадает и не плодит дубликаты якоря; `coveringMarker(...)` использует `stampedOrCell(...)` с проверкой `STAMP_DIM`.
  - Добавлен **`anchorForSave(ServerLevel, BlockPos, boolean hasGraph)`**: немедленно обновляет покрывающий маркер (или создаёт/очищает его) в момент сохранения или удаления графа.
  - Переработан **`freeSpot(ServerLevel, BlockPos)`**: приоритет отдаётся свободным клеткам внутри помещения (с полом/потолком по соседству от блока), и только в крайнем случае — поиск вокруг/вверх.
  - Введены **`STAMP_DIM = "SettledLandsAnchorDim"`** и очистка устаревших NBT-тегов в `seal(...)`, если у постройки не осталось ни одного графа (`clear(data(marker))`).
  - `sealHere(...)` переиспользует уже существующий поблизости маркер (включая `home(level, marker)`) вместо создания дубликата; `wandOn(...)` обновляет `home(level, marker)`.
  - В `materialiseForDungeon(ServerLevel, Entity, int knownTurns)` добавлен учёт `STAMP_DIM` (маркер из другого измерения больше не пропускает материализацию даже на совпадающих координатах `(0,0,0)`) и fallback `NpcGraphAnchorCore420.placeWithTurn(...)` при известном повороте `knownTurns >= 0`.
- **`NpcGraphAnchorCore420` (`build303-src/src/dev/settledlands/npc/NpcGraphAnchorCore420.java`)**:
  - Добавлен метод `placeWithTurn(...)` для поиска наилучшего сдвига при заранее известном повороте комнаты `turns`.
- **Немедленная синхронизация якоря из всех редакторов и замков**:
  - `ChestGraph396.save` — вызывает `NpcGraphAnchor420.anchorForSave(...)` при сохранении и очистке графа сундука/полки;
  - `BlockTrigger351.save` — вызывает `NpcGraphAnchor420.anchorForSave(...)` (через `parsePos(key)`) при сохранении и удалении блок-триггера;
  - `DoorGraph382.save` и `DoorEvents368.onRightClick` — вызывают `NpcGraphAnchor420.anchorForSave(...)` при сохранении/очистке дверного графа и при переключении ключа-замка.
- **Данжевый спавн и лут (`DungeonTemplate425`, `LootPoolManager436`)**:
  - `LootPoolManager436.onDungeonRoomPlaced(ServerLevel, List<Entity>, int knownTurns)` передаёт точный поворот `turns` комнаты в `NpcGraphAnchor420.materialiseForDungeon`.
  - `DungeonTemplate425.placeBlockEntitiesAndEntities` передаёт `turns` в `LootPoolManager436.onDungeonRoomPlaced` и гарантированно включает `settledlands:guide_trigger` с графами в материализацию даже если `addFreshEntity` вернул `false` / `entity.isRemoved()` при спавне до активации тикинга чанка.

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build437.py` -> `settledlands-0.8.0-build437-anchor-furniture-fix.jar`
- `tools/verify_jar.py --expect-version 0.8.0-build437-anchor-furniture-fix --base settledlands-0.8.0-build436-furniture-loot-pools.jar` — **PASS** (`validate_jar`: 688 entries / 633 classes; `jvm_verify`: 633 classes valid for Java 21; `check_mixin_class`: 113 mixins verified, 0 unconfirmed refs; `dangling_refs`: 0)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build436-furniture-loot-pools.jar` — **OK** (`6091` targets checked)
- `tools/mixin_clash_check.py` — **OK** (`240` helpers in `83` shared-target mixins, `0` clashes)
- `tools/build437_check.py` — **PASS** (структурно-байткодный аудит всех 8 изменённых классов + живой JVM end-to-end прогон `Build437Probe` на `22` сценариях: мебель без блок-триггеров, размещение маркера под потолком внутри комнаты, покрытие `25–32` без дубликатов, очистка тегов, `sealHere` без дублей, дверной граф/замок без триггеров, коллизия `(0,0,0)` между Overworld и `settledlands:hell_dungeon` для новых и legacy-маркеров, и fallback по `knownTurns` при изменении `>50%` блоков комнаты с выдачей лута из пула)
- `tools/anchor_check.py --base settledlands-0.8.0-build436-furniture-loot-pools.jar` — **PASS** (`Anchor420Probe: 62 checks passed`, включая `placeWithTurnFallback` и `gatherCoverage`)
- `tools/build436_check.py` — **PASS** (`Build436Probe: 7305`, `Build436CompatProbe: 4`, `Chest{Graph396,Hack397,Open401,Close402}Probe: PASS`)
- `tools/hell_dungeon_check.py` — **PASS** (`HellDungeon425Probe: 6297`, `HellDungeon427Probe: 11088`, `CreateCompat434Probe: 7`)
- `tools/teleport_check.py` — **PASS** (`Teleport424Probe: 88`)
- `tools/block_trigger_check.py`, `tools/door_graph_check.py`, `tools/door_lock_check.structural` — **PASS**
