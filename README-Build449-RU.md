# Settled Lands 0.8.0 Build 449 — Именованные пулы триал-спавнера и 3D-каталог мобов

База: `settledlands-0.8.0-build448-card-descriptions-and-hover-tooltips.jar`

## Что изменилось в Build 449

Build 449 полностью унифицирует работу с **пулами мобов** и **пулами наград** модульного триал-спавнера по единому визуальному стандарту `Пул предметов` сундуков (`LootPoolCard436` + `LootPoolEditorScreen440`) и добавляет полноценный **3D-каталог всех мобов сборки**:

### 1. Именованные пулы мобов триал-спавнера (`config/settledlands/mob_pools/*.txt`)
- Новые классы `TrialMobPool449`, `TrialMobPoolManager449`, `TrialMobPoolNet449` (4 пакета NeoForge: `settledlands:trial_mob_pools`, `_query`, `_data`, `_save`, зарегистрированные в `ChestLootNet436#onRegister`).
- Файлы хранятся в `config/settledlands/mob_pools/<name>.txt` в простом человекочитаемом формате `entity_id | weight` (поддерживаются комментарии `#` и `//`, защита от path traversal, автоматическая генерация `README_FORMAT.txt.disabled`).
- **Авторитетный диск сервера (`TrialModule447`, `TrialGraph446`)**:
  - Формат действия ноды: `trial_mod:mobs_pool:<file.txt>|<inline>` (с полной обратной совместимостью для старых графов со встроенным `trial_mod:mobs:...`).
  - При открытии редактора графа или сохранении графа (`TrialGraph446#open`/`save`) вызывается `TrialModule447.refreshPoolsFromServerDisk(graph)` и рассылаются актуальные списки пулов мобов, лута и книг клиенту — правки в `.txt` на диске автоматически подхватываются в блок `trialtuner:trial_spawner`.

### 2. Единые общие пулы наград (`config/settledlands/loot_pools/*.txt` + кастомные книги)
- Модуль **`Пул наград`** (`SLOT_REWARDS`) теперь использует общие файлы `config/settledlands/loot_pools/*.txt` (`trial_mod:rewards_pool:<file.txt>|<inline>`) и открывает полноценный визуальный редактор **`LootPoolEditorScreen440`**:
  - каталог всех предметов игры, инвентарь игрока, вставка ID, кастомные книги `LootPoolBookEditorScreen443`, диапазоны `min-max`, вес, шанс выпадения `%`, проверка дропа `Тест` и живой расчёт эффективных шансов.
  - При редактировании пула, привязанного к модулю наград триала, поля ограничений сундука (`Max rarity / Max weight / Rolls`) автоматически скрываются, тестовый ролл идёт без фильтров сундука, а сохранение/удаление синхронно обновляет узел и карточку графа.

### 3. Компактный инспектор «Выпадающий список + Шестерёнка» (`TrialModuleInspector447Mixin`, `TrialModuleClient447`, `LootPoolCard436`, `LootPoolGearButton440`, `TrialModuleCard447`)
- Из инспекторов модулей **`Пул мобов`** и **`Пул наград`** убраны громоздкие встроенные таблицы с десятками полей:
  - сверху — аккуратный выпадающий список (`NpcUi.dropdown`) сохранённых пулов с **инлайн-шестерёнками `12×12`** на каждой строке для быстрого редактирования (или создания нового пула);
  - справа — **кнопка шестерёнки `20×20`** в акцентном цвете модуля (`#E56B6F` для мобов, `#F5B841` для наград);
  - ниже — компактная сводка привязанного файла и первых позиций + подсказка папки (`mob_pools/` или `loot_pools/`).
- На карточках графа (`TrialModuleCard447`) сразу видно, подключён ли именованный файл пула или используется встроенный режим.
- Все 6 пропущенных в Build 447/448 ключей `gui.settledlands.trial.cfg.*` (`ominous_equip`, `nobreak`, `destruct`, `nobreak_r`, `nobreak_s`, `nobreak_amp`) добавлены в `ru_ru.json` и `en_us.json`.

### 4. Визуальный редактор пула мобов (`TrialMobPoolEditorScreen449`) и 3D-каталог всех мобов сборки (`TrialMobCatalogScreen449`, `TrialMobPreview449`)
- **`TrialMobPoolEditorScreen449`**: полноэкранный редактор `mob_pools/*.txt` с переключателем файлов (поверх с `Z=500`), созданием/удалением с двойным подтверждением, таблицей **живых 3D-миниатюр мобов `20×20`**, редактированием весов, живыми `%` шансами и строкой **`Тест волны`**.
- **`TrialMobCatalogScreen449`**: единый 3D-каталог всех живых существ, мобов и боссов из ванили и всех установленных модов (также подключён к кнопке выбора моба в `TrialControlConfigScreen446`):
  - поиск по локализованному названию, ID мода (`modid`) и техническому идентификатору;
  - переключатель категорий (`Все / Враги / Мирные+`) и быстрое добавление по точному ID;
  - живые 3D-модельки через `InventoryScreen.renderEntityInInventoryFollowsMouse` с `enableScissor`-обрезкой, авто-масштабом по габаритам сущности, лимитом создания ≤ 4 новых сущностей за кадр (LRU-кэш на 96 записей) и безопасным откатом к яйцу призыва / значку категории;
  - **Shift + клик** для серийного добавления нескольких мобов без закрытия каталога.

## Ключевые новые и изменённые файлы

- Ядро и сеть: `TrialMobPool449.java`, `TrialMobPoolManager449.java`, `TrialMobPoolNet449.java`, обновлены `ChestLootNet436.java`, `TrialModule447.java`, `TrialGraph446.java`
- Клиент: `TrialMobPreview449.java`, `TrialMobCatalogScreen449.java`, `TrialMobPoolEditorScreen449.java`, обновлены `LootPoolGearButton440.java`, `LootPoolCard436.java`, `LootPoolEditorScreen440.java`, `TrialModuleClient447.java`, `TrialModuleCard447.java`, `TrialControlConfigScreen446.java`, `GraphTooltips448.java`
- Миксин: обновлён `TrialModuleInspector447Mixin.java` (zero-lambda инспектор выпадающий список + шестерёнка)
- Стабы и сборка: `stubs449-src` (`MobCategory`, `EntityType`, `InventoryScreen`, `Holder` с конкретным классом `Holder.Reference`), `tools/patch_build449.py`, `tools/build449_check.py`, `tools/make_source_zip.py`

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build449.py` -> `settledlands-0.8.0-build449-named-trial-pools-and-3d-mob-catalog.jar` (`4 336 544` байт, `802` записи / `747` классов)
- `tools/build449_check.py` — **PASS** (`BUILD449_CHECKS=45`, `0` отсутствующих ключей локализации среди `1581` ключей в `ru_ru.json` и `en_us.json`, живая Java 21 CRUD + refresh + wave-roll проба)
- `tools/build448_check.py` — **PASS** (`BUILD448_CHECKS=114`, `BYTECODE_KEYS_VERIFIED=229`, `DYNAMIC_KEYS_VERIFIED=100`)
- `tools/build447_check.py` — **PASS** (`BUILD447_CHECKS=91`)
- `tools/verify_jar.py --base` — **PASS** (`0` новых внешних API-ссылок против Build 448, `0` оборванных ссылок)
- `tools/invoke_kind_check.py --base` — **PASS** (`7067` уникальных целей вызовов — 100% совпадение видов вызова, включая `invokevirtual Holder$Reference.value`)
- `tools/mixin_clash_check.py` — **PASS** (`326` методов в `109` миксинах, `0` конфликтов, `0` лямбд в миксинах 447/448)
