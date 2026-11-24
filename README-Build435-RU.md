# Settled Lands 0.8.0 Build 435 — Отложенный атомарный спавн комнат с контрапциями (`create:stationary_contraption`) в тикающих чанках

База: `settledlands-0.8.0-build434-contraption-pos-fix.jar`

## Что показала проверка Build 434 и разбор ядра Minecraft / NeoForge 1.21.1

После исправления `Pos` и немедленной привязки `attachContraption` в Build 434 в тестовом данже (`settledlands:hell_dungeon`):
- `create:mechanical_bearing` и `create:creative_motor` спавнятся и работают, но `create:stationary_contraption` всё равно отсутствовала на стене (и не появлялась после перезахода, без ошибок в `latest.log`),
- тогда как отдельная вставка схематики через пушку Create рядом с игроком работала с поворотом `0` (`turns = 0`).

Разбор исходников ванильного Minecraft 1.21.1 + NeoForge (`ServerLevel`, `PersistentEntitySectionManager`, `ChunkMap`, `DistanceManager`) показал ключевое отличие между строительством рядом с игроком и генерацией данжа в `DungeonManager425`:

1. **`PersistentEntitySectionManager` в чанках со статусом `FULL` (`Visibility.HIDDEN` / `ChunkLoadStatus.FRESH`)**:
   - Когда игрок телепортируется в новый `settledlands:hell_dungeon`, комнаты на глубине `150–250` блоков (в т.ч. `а Астролябия 1.nbt`) размещаются `DungeonManager425.tickPendingGeneration`, пока эти чанки ещё находятся за границей `simulation_distance` (или только получили `FullChunkStatus.FULL` без перевода в `ENTITY_TICKING`) — их `EntitySection` в `PersistentEntitySectionManager` имеет видимость `Visibility.HIDDEN` и статус загрузки `ChunkLoadStatus.FRESH`.
   - При вызове `level.addFreshEntity(entity)` в таком чанке сущность не получает ни `startTracking`, ни `startTicking`, и оказывается в гонке асинхронной загрузки/сохранения секций (`FRESH -> PENDING -> LOADED`) и отложенного пробуждения контрапции с подшипником (`bearing`).
   - В то же время обычная проверка `ServerLevel.isPositionEntityTicking(BlockPos)` (которую ванилла уже использует, а также другие события мода вроде `AltarNpcReviveEvents`) проверяет оба условия сразу:
     `entityManager.canPositionTick(pos) && chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(pos))`
     — то есть и `Visibility.TICKING` + `ChunkLoadStatus.LOADED`, и уровень чанка `ENTITY_TICKING` (`<= 31`).

2. **Многотиковая постройка `а Астролябия 1.nbt` (`5930` блоков > `blocks_per_tick = 4096`)**:
   - Из-за лимита `4096` блоков/тик комната с Астролябией строилась минимум за 2 тика. Если игрок приближался к границе чанков в промежутке, блоки/подшипник/контрапция могли активироваться в разное время или при частично прогруженных соседних чанках комнаты.

## Что изменено в Build 435

1. **`dev/settledlands/dungeon/DungeonManager425.java`**
   - Комнаты не-старта (`i > 0`), содержащие сущности (`hasEntities(pt)`, например `а Астролябия 1.nbt`), теперь пропускаются в `ensureInstanceInitialized` (`immediatePieces`) и в `pickNextPiece`, пока `ServerLevel.isPositionEntityTicking(BlockPos)` не вернёт `true` для **всех** чанков, пересекаемых комнатой (`minCx..maxCx x minCz..maxCz`), а также для всех позиций её `BlockEntity` и `Entity`.
   - Пока чанки ещё не `ENTITY_TICKING`, все остальные обычные комнаты данжа продолжают строиться в штатном режиме без задержек.
   - Как только игрок подходит достаточно близко и все чанки комнаты становятся `ENTITY_TICKING`, вся комната с контрапцией (блоки, `mechanical_bearing`, `creative_motor` и сущность `create:stationary_contraption`) строится **атомарно за 1 серверный тик** (`sliceSize = remaining` для комнат с сущностями до `16384` блоков) с итоговым INFO-логом:
     `settledlands: placed piece 'а Астролябия 1' in '...' @ (...) turns=...: blocks=..., be=..., ents=..., createCompat=...`

2. **`dev/settledlands/dungeon/DungeonTemplate425.java` и `CreateCompat431.java`**
   - Добавлена явная проверка возвращаемого значения `level.addFreshEntity(entity)` и `entity.isRemoved()` + `level.isPositionEntityTicking(ePos)`: при любом отбрасывании или удалении сущности в `latest.log` сразу пишется `WARN`, а при успешном спавне контрапции — `INFO` (`settledlands: spawned create:stationary_contraption at ... ticking=true createCompat=true`).
   - В `CreateCompat431` сохранена точная сигнатура `attachContraption(ServerLevel, Entity)` и добавлено логирование ошибок инициализации рефлексии Create, отсутствующего контроллера или неудачной привязки `isAttachedTo` (для немедленной диагностики по `latest.log`, если когда-либо проявится несовместимость с версией Create).

3. **`build303-src/stubs425-src/net/minecraft/server/level/ServerLevel.java`, `tools/harness/HellDungeon427Probe.java`, `tools/hell_dungeon_check.py`**
   - Добавлен compile-stub `ServerLevel.isPositionEntityTicking(BlockPos)` (соответствует ванильному методу, уже вызываемому в базовом JAR классом `AltarNpcReviveEvents`).
   - Добавлен тест `testEntityTickingDeferralBuild435`: проверяет откладывание комнаты с контрапцией при `isPositionEntityTicking == false` (в т.ч. если не готов хотя бы один угловой чанк) и атомарную постройку за 1 тик при переходе всех чанков в `ENTITY_TICKING`.

## Что посмотреть в `latest.log` при тесте Build 435 в игре

Если протестировать заход в новый адский данж и подойти к Астролябии, в `latest.log` появятся строки вида:
- `settledlands: spawned create:stationary_contraption at BlockPos{...} (exact=...) turns=... ticking=true createCompat=true`
- `settledlands: placed piece 'а Астролябия 1' in '...' @ (...) turns=...: blocks=5930, be=10/0, ents=1/0, createCompat=true`

Если же контрапция всё равно не появится, сразу пришлите из `latest.log` строки с префиксом `settledlands:` (они теперь точно покажут `added=...`, `removed=...`, `ticking=...`, `createCompat=...` и состояние `attachContraption`).

## Сборка и проверки

- `ecj-3.46.100.jar` + `tools/patch_build435.py` -> `settledlands-0.8.0-build435-contraption-ticking-spawn.jar`
- `tools/validate_jar.py --expect-version 0.8.0-build435-contraption-ticking-spawn` — PASS
- `tools/verify_jar.py --base settledlands-0.8.0-build434-contraption-pos-fix.jar` — PASS (618 classes, 0 new external API refs, 0 dangling refs)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build434-contraption-pos-fix.jar` — PASS (5991 call targets)
- `tools/hell_dungeon_check.py` — PASS (`HellDungeon425Probe: 6297`, `HellDungeon427Probe: 11098`, `CreateCompat434Probe: 7`)
