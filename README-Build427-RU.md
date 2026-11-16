# Settled Lands — Сборка 427 (`procedural-dungeon`)

Процедурная генерация «Адского данжа» (`settledlands:hell_dungeon`) напрямую из файлов блупринтов Axiom (`.bp`) на диске — **без необходимости перепаковывать все комнаты в `.slbp` внутрь JAR**.

База — подтверждённая в игре сборка **426** (`0.8.0-build426-dungeon-command-fix`). Новых миксинов нет.

---

## Как работает загрузка `.bp` с диска

1. **Автосоздание конфига и папки при старте:** при первом обращении мод создаёт:
   - `config/settledlands/dungeon.properties` — настройки генерации;
   - `config/settledlands/blueprints/` (и файл `README-BLUEPRINTS-RU.txt` внутри).
2. **Автопоиск папки (`blueprints_dir=auto`):** мод по очереди проверяет:
   1. `config/settledlands/blueprints/` (включая подпапки)
   2. `config/axiom/blueprints/` (включая подпапки — например `config/axiom/blueprints/Данж/`)
   3. `blueprints/` (включая подпапки)
   - Берётся первая папка, в которой найдены валидные данжевые `.bp` (содержит `старт.bp` или комнаты с маркером `minecraft:lapis_block`).
   - Случайные постройки без `minecraft:lapis_block` автоматически пропускаются.
3. **Умный фильтр тем (`filter_mode=auto`):**
   - Если в папке лежат смешанные темы из `Данж.rar` (`infernal`, `crypt`, `sewer`, `cove`, `flesh`) — для `settledlands:hell_dungeon` автоматически выбираются только адские комнаты (`infernal` / префикс `а `).
   - Если в папке лежат только ваши собственные `.bp` без других тем — загружаются **все** найденные валидные комнаты (`auto->all`).
   - В `config/settledlands/dungeon.properties` можно задать `filter_mode=all`, `filter_mode=tag:crypt` (или `sewer`, `cove`, `flesh`) или `filter_mode=prefix:а ` и точный путь `blueprints_dir=...`.
4. **Запасной режим (если папка пуста):** если ни одного `.bp` на диске нет, используется встроенный в мод `start.slbp`, а игроку выводится подсказка положить `.bp` в папку и выполнить `/settledlands dungeon reload`. Если внешние комнаты есть, а `старт.bp` в папке отсутствует — автоматически подставится встроенный `start.slbp`, а комнаты возьмутся из папки.

---

## Что происходит при генерации инстанса данжа

1. **Нативный парсер `AxiomBlueprint427`:** читает бинарный формат Axiom `.bp` (`0x0AE5BB36`, заголовок с `Tags`, GZIP NBT `BlockRegion` с 16×16×16 упакованными палитрами, `BlockEntities` и `Entities`) напрямую в чистой Java — **мод Axiom на выделенном сервере не требуется**.
2. **Планировщик `DungeonGenerator427`:**
   - Детерминированно (от `seed` мира и `id` инстанса) стыкует комнаты внутри ячейки `512 × 512` (`max_radius = 230`, `min_y = -52`, `max_y = 240`, целевой размер `60..80` комнат).
   - Каждый проём сопоставляется по `lapis_block` (вход) и `redstone_block` (выход) с точным поворотом на `0° / 90° / 180° / 270°` без пересечений bounding box.
   - Поворачиваются все свойства блоков (`facing`, `axis`, `rotation`, `north/east/south/west`, `shape` у рельс/ступеней), координаты и направления `BlockEntities` (`trialtuner:trial_spawner`, сундуки) и `Entities` (`settledlands:guide_trigger` с графами/якорями `NpcGraphAnchor420`).
   - Ветви закрываются тупиками и каменными заглушками `minecraft:nether_bricks` (включая тыловой проём `старт`, чтобы за спиной при спавне не было провала в войд).
   - Все маркеры швов `lapis_block` и `redstone_block` автоматически заменяются на `doorway_block` (по умолчанию `minecraft:nether_bricks`).
3. **Плавная пошаговая постройка (`DungeonEvents425#onServerTick`):**
   - При входе синхронно ставятся первые `immediate_pieces` (`8` комнат) и тыльная заделка старта, игрок сразу появляется в безопасной точке внутри стартовой комнаты.
   - Остальные комнаты достраиваются по тикам с бюджетом `blocks_per_tick` (`4096` блоков/тик) и **приоритетом для ближайших к игрокам комнат**; прогресс каждого куска сохраняется в `SavedData` (`settledlands_hell_dungeon`), поэтому перезапуск сервера посреди стройки аккуратно достроит оставшееся без дублей и дыр.

---

## Установка и команды в игре

1. Замените файл в `mods/` на `settledlands-0.8.0-build427-procedural-dungeon.jar`.
2. Положите `.bp` файлы (например содержимое `Данж.rar`) в `.minecraft/config/settledlands/blueprints/` **или** в `.minecraft/config/axiom/blueprints/Данж/` (на сервере — в `config/settledlands/blueprints/`).
3. Команды в игре:
   - `/settledlands dungeon reload` — перечитать `config/settledlands/dungeon.properties` и папку `.bp` **без перезапуска игры** (показывает путь папки, фильтр, число комнат/тупиков и старт);
   - `/settledlands dungeon info` — статус измерения, количество инстансов и сводка загруженных `.bp`;
   - `/settledlands dungeon enter [id]` — войти в инстанс данжа (по умолчанию `default`);
   - `/settledlands dungeon new [id]` — создать и войти в новый инстанс в свободной ячейке `512 × 512`;
   - `/settledlands dungeon list` — список инстансов и прогресс их постройки (`[building X/Y]` / `[done Y/Y]`);
   - `/settledlands dungeon leave` — вернуться к сохранённой точке до входа.

---

## Верификация сборки 427

- **45 / 45 гейтов** (`validate_jar.py`, `jvm_verify.py`, `check_mixin_class.py --base build426`, `dangling_refs.py --base build426`, `invoke_kind_check.py --base build426`, `teleport_check.py`, `anchor_check.py`, `hell_dungeon_check.py`, и все гейты систем диалогов/дверей/взлома/камеры) — **PASS**.
- **`check_mixin_class.py --base settledlands-0.8.0-build426-dungeon-command-fix.jar`:** `1441` внешних ссылок, **`0` новых внешних API** сверх подтверждённой в игре сборки 426, **`0` запрещённых классов Brigadier**.
- **`hell_dungeon_check.py`:**
  - `HellDungeon425Probe`: `6 293` проверки (обратная совместимость fallback, сетка, фильтр мобов, команды) — **PASS**.
  - `HellDungeon427Probe`: `371 194` проверки (нативный ридер всех `232` реальных `.bp` из `Данж.rar`, автосоздание конфига/папки, автопоиск, фильтры, повороты блоков/свойств/сущностей, приоритизация к игроку, сохранение/загрузка посреди постройки, заделка всех швов, рефлексия всех классов, `/settledlands dungeon reload`) — **PASS**.
- **Детерминизм сборки (`cmp -s`)** для JAR и Source ZIP — **PASS**.
