# Settled Lands — Сборка 425 (`hell-dungeon`)

Сборка добавляет пустое измерение **«Адский данж»** (`settledlands:hell_dungeon`), упаковку и одноразовую установку стартовой комнаты `start.slbp` (из `старт.bp`), изоляцию инстансов на сетке `512 × 512`, фильтр мобов (без случайного спавна, но со спавнерами) и 5-й мир в узле **«Телепорт»**.

## Измерение и генерация

- Идентификатор измерения и типа измерения: `settledlands:hell_dungeon`.
- Пустотный генератор (`minecraft:flat` с `layers: []` и биомом `minecraft:the_void`): никакого природного рельефа, построек, лавовых морей или облаков.
- Незерская атмосфера освещения: `effects: "minecraft:the_nether"`, `ambient_light: 0.15`, `fixed_time: 18000`, `min_y: -64`, `height: 384`, `logical_height: 384`, `infiniburn: "#minecraft:infiniburn_nether"`.
- Фильтр спавна (`DungeonEvents425` на `MobSpawnEvent.PositionCheck`): блокирует `NATURAL`, `CHUNK_GENERATION`, `PATROL`, `STRUCTURE` и `REINFORCEMENT` внутри `settledlands:hell_dungeon`, полностью сохраняя спавнеры (`SPAWNER`, `TRIAL_SPAWNER`), команды (`COMMAND`), яйца призыва (`SPAWN_EGG`) и призывы мобов (`MOB_SUMMONED`). Естественный спавн в других мирах не затронут.

## Стартовая комната `start.slbp` и сетка инстансов

- `tools/dungeon/pack_start.py` конвертирует Axiom-схематик `старт.bp` в компактный gzipped-NBT ресурс `data/settledlands/dungeon/start.slbp` (`6 019` непустых блоков, `31` состояние палитры, `1` блочная сущность `trialtuner:trial_spawner`, `1` сущность-якорь `settledlands:guide_trigger` со снятыми `UUID`, `Pos` и `neoforge:attachments`).
- Маркерные блоки стыков прохода (`minecraft:lapis_block` и `minecraft:redstone_block`) при установке автоматически заменяются на `minecraft:nether_bricks`.
- Внешний блок `trialtuner:trial_spawner` проверяется через `BuiltInRegistries.BLOCK`: если мод Trial Tuner установлен, сущность спавнера загружается с конфигурацией волн; если отсутствует — пропускается без ошибок.
- Сущность `settledlands:guide_trigger` (якорь графа блока) спавнится со сдвигом на начало инстанса и заново привязывается якорем `NpcGraphAnchor420`.
- `DungeonData425` хранит реестр инстансов в `SavedData` (`settledlands_hell_dungeon`) на детерминированной спиральной сетке `512 × 512` (`default` на `(0, 0, 0)`, следующий на `(512, 0, 0)`, и т.д.) и устанавливает `start.slbp` строго один раз на инстанс (`placed = true`).
- Безопасный вход: точка входа в стартовой комнате `(originX + 10.5, originY - 8.0, originZ + 0.5)`, взгляд на запад (`yaw = 90.0F`, `pitch = 0.0F`), со сбросом `fallDistance`, огня и скорости (`Vec3.ZERO`). Точка возврата игрока сохраняется до первого входа в данж и восстанавливается при выходе (или падает на `overworld.getSharedSpawnPos()` при отсутствии сохранённой точки).

## Интеграция с узлом «Телепорт» и команды

- В выпадающий список миров узла **«Телепорт»** добавлен 5-й мир: `Адский данж` (`settledlands:hell_dungeon`).
- Если поля `X / Y / Z` узла «Телепорт» оставлены пустыми, игрок автоматически переносится на вход стартовой комнаты `(10.5, -8.0, 0.5)`, а при первом визите размещается `start.slbp`. Если заданы явные числа `X / Y / Z`, они используются как координаты назначения (стартовая комната при этом всё равно ставится один раз при первом посещении).
- Если сохранение было создано до добавления `settledlands:hell_dungeon` и измерение отсутствует в загруженном мире, телепортация безопасно отменяется с понятным сообщением `message.settledlands.dungeon.not_loaded` («для нового измерения нужен новый мир»).
- Команды управления и проверки:
  - `/settledlands dungeon enter [id]` *(OP lvl 2)* — вход в указанный инстанс (по умолчанию `default`);
  - `/settledlands dungeon new [id]` *(OP lvl 2)* — выделение нового инстанса на сетке `512 × 512`, установка `start.slbp` и перенос игрока;
  - `/settledlands dungeon leave` — возврат игрока в исходную точку (или спавн Обычного мира);
  - `/settledlands dungeon info` — состояние измерения `settledlands:hell_dungeon`, число инстансов и шаг сетки;
  - `/settledlands dungeon list` *(OP lvl 2)* — список инстансов, координат и статуса `placed`.

## Изменённые и добавленные файлы

- `dev.settledlands.dungeon.DungeonTemplate425` — загрузка `start.slbp`, замена маркеров прохода, восстановление состояний блоков, `BlockEntity` и `Entity`.
- `dev.settledlands.dungeon.DungeonData425` — `SavedData` инстансов на спиральной сетке `512 × 512` и точек возврата игроков.
- `dev.settledlands.dungeon.DungeonManager425` — маршрутизация входа/выхода, одноразовая установка, проверка наличия измерения.
- `dev.settledlands.dungeon.DungeonEvents425` — блокировка естественного спавна в `settledlands:hell_dungeon` с сохранением спавнеров и команд.
- `dev.settledlands.dungeon.DungeonCommand425` — регистрация `/settledlands dungeon {enter,new,leave,info,list}`.
- `dev.settledlands.npc.TeleportNode424` + `TeleportActions424` — 5-й мир `settledlands:hell_dungeon` в таблице миров, пустые координаты по умолчанию на вход и передача управления в `DungeonManager425`.
- `data/settledlands/dimension_type/hell_dungeon.json`, `data/settledlands/dimension/hell_dungeon.json`, `data/settledlands/dungeon/start.slbp`.
- `tools/dungeon/pack_start.py`, `tools/patch_build425.py`, `tools/hell_dungeon_check.py`, `tools/harness/HellDungeon425Probe.java`.

## Проверки

- `tools/verify_jar.py settledlands-0.8.0-build425-hell-dungeon.jar --expect-version 0.8.0-build425-hell-dungeon --base settledlands-0.8.0-build424-teleport.jar` — **PASS** (`595` классов, `109` миксинов, `0` новых внешних ссылок вне подтверждённого списка MC 1.21.1).
- `tools/hell_dungeon_check.py` (`6 279` JVM-проверок с реальным `start.slbp`) и `tools/teleport_check.py` (`88` JVM-проверок) — **PASS**.
- Все **41/41** гейты `tools/*_check.py`, `invoke_kind_check.py`, `mixin_clash_check.py` и побайтовая воспроизводимость (`cmp -s`) JAR и Source ZIP — **PASS**.
