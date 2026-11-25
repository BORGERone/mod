# Settled Lands 0.8.0 Build 443 — Кастомные цветные книги, внутриигровой «Редактор книги», кафедры и резные книжные полки

База: `settledlands-0.8.0-build442-rarity-chance-crate-fix.jar`

## Что нового в Build 443

### 1. Кастомные цветные книги в пулах лута (`config/settledlands/books/<id>.txt`)
- Кастомные книги хранятся в отдельной папке `config/settledlands/books/<id>.txt` в читаемом текстовом формате (`title:`, `author:`, разделитель страниц `=== PAGE ===`).
- В пулах лута (`config/settledlands/loot_pools/<pool>.txt`) книга указывается как **`settledlands:book/<id>`** (или коротко `book:<id>` в ручном вводе) с теми же полями **`Редк.`**, **`Вес`** и **`Шт`**.
- **Полная поддержка цветов и форматирования** (и в названии предмета `DataComponents.CUSTOM_NAME` без принудительного курсива, и внутри страниц `DataComponents.WRITTEN_BOOK_CONTENT`):
  - стандартные коды `&0`–`&f` / `§0`–`§f`;
  - произвольные hex-цвета `&#RRGGBB`, `§#RRGGBB`, `{#RRGGBB}`;
  - стили `&l` (жирный), `&o` (курсив), `&n` (подчёркнутый), `&m` (зачёркнутый), `&k` (магия) и сброс `&r`, в любом порядке (`&l&6` и `&6&l`), плюс экранирование `\&`;
  - транслитерация кириллических ID в безопасный формат `[a-z0-9_-]` (до 48 символов), а также безопасная Base64-синхронизация между клиентом и сервером (`CustomBookNet443`: `custom_books`, `custom_book_query`, `custom_book_data`, `custom_book_save`).

### 2. Внутриигровой «Редактор книги» и интеграция с пулом лута
- В редакторе пула лута **`LootPoolEditorScreen440`**:
  - новая кнопка **`+ Книга`** в шапке открывает **`LootPoolBookEditorScreen443`**;
  - в строках таблицы для книг (`settledlands:book/<id>`) прямо рядом с названием отображается кнопка редактирования **`E`**;
  - названия кастомных книг в строках пула и тестовом дропе рисуются в их реальных цветах.
- В каталоге предметов **`LootPoolItemPickerScreen440`**:
  - добавлена 3-я вкладка режима **`Свои книги`** (кнопка **`+ Новая книга`**, ЛКМ / Shift+ЛКМ добавить в пул, **ПКМ** — сразу открыть на редактирование);
  - во вкладке **`Инвентарь`** клик **ПКМ** по ванильной написанной книге или книге с пером импортирует её (включая цвета и страницы!) прямо в «Редактор книги».
- В окне **`LootPoolBookEditorScreen443`**:
  - поля ID, цветного заголовка и автора, список сохранённых книг, создание новой и удаление на сервере (двойной клик для подтверждения);
  - панель быстрой вставки 16 цветов, стилей `B`/`I`/`U`/`S`/`R` и произвольного `&#RRGGBB` прямо в активное поле (Заголовок или текущую страницу);
  - многострочный редактор страницы (`MultiLineEditBox`) с переключением/добавлением/удалением страниц (`+ Стр.` / `- Стр.`), кнопкой **`Из руки`** (импорт из руки с сохранением форматирования), **`Выдать себе`** (быстрая проверка готовой книги в руке), **`Сохранить`** и **`Сохр. и в пул`**;
  - живой пергаментный предпросмотр 14-строчной страницы книги справа;
  - анти-размытие (`renderBackground` переопределён без вызова `super.renderBackground`).

### 3. Кафедры (`minecraft:lectern`) и резные книжные полки (`minecraft:chiseled_bookshelf`) в графах сундуков
- `ChestGraph396.isChest` / `isChestAt` теперь распознают **кафедры** (включая `LecternBlockEntity`, который в ванильном Minecraft 1.21.1 не реализует `Container`) и **резные книжные полки** (`ChiseledBookShelfBlock` / `ChiseledBookShelfBlockEntity`, 6 слотов).
- Поддерживаются все возможности графов сундуков: привязка палочкой, подсветка частиц (`RadiusPass354`), замок и панель взлома (`ChestLock397`, `ChestNet397`), сохранение в `BlockEntity` при копировании/вставке и генерации в данжах (`NpcGraphAnchor420`, `DungeonTemplate425`).
- В **`ChestOpen401.captureClientHit`** перед открытием панели взлома запоминаются точная грань и координаты клика, чтобы после взлома повторный клик попадал в нужный слот резной полки.
- В **`ChestGraph396Events`** учтено, что резные полки и пустые кафедры не открывают меню (`OPEN_GRACE = 2` тика, не ждёт 40 тиков), тогда как кафедра с книгой корректно ждёт закрытия `LecternMenu`.
- При генерации лута в **`LootPoolManager436`**:
  - для **кафедры** и **резной книжной полки** предметы из смешанных пулов автоматически фильтруются до броска (для кафедры — `settledlands:book/*` и `#minecraft:lectern_books`, для резной полки — `settledlands:book/*` и `#minecraft:bookshelf_books`), поэтому обычные предметы вроде алмазов не «съедают» броски;
  - после выдачи книги на кафедру вызывается `LecternBlock.resetBookState(..., true)` для обновления состояния `HAS_BOOK`, а на резной полке обновляются все 6 свойств `ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES` и рассылается `sendBlockUpdated`.

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build443.py` -> `settledlands-0.8.0-build443-custom-books-lectern-bookshelf.jar` (`4035956` байт, `722` записи, `667` классов, `sha256=4a91bd7c85c8fbe64602d7c470b333b9a3909d8be1f9c1dc62e26bdc759fba92`)
- `tools/verify_jar.py --expect-version 0.8.0-build443-custom-books-lectern-bookshelf --base settledlands-0.8.0-build442-rarity-chance-crate-fix.jar` — **PASS** (`validate_jar`: OK; `jvm_verify`: 667 классов valid for Java 21; `check_mixin_class`: 116 миксинов, 1487 внешних API-ссылок, **0** новых относительно Build 442; `dangling_refs`: **0**)
- `tools/invoke_kind_check.py --base settledlands-0.8.0-build442-rarity-chance-crate-fix.jar` — **OK** (`6436` call target'ов сверено)
- `tools/build443_check.py settledlands-0.8.0-build443-custom-books-lectern-bookshelf.jar` — **PASS**:
  - структурные проверки Build 396–443 (классы `CustomBook443*`, `CustomBookManager443`, `CustomBookNet443*`, `LootPoolBookEditorScreen443`, 43 RU/EN ключа, анти-размытие, изоляция выделенного сервера, ноль синтетических лямбд в миксинах Build 436–443, привязка `Lectern*`/`ChiseledBookShelf*`);
  - `mixin_clash_check`: **OK**; `door_graph_check`: **OK**; `anchor_check`: **OK** (`ANCHOR_CHECKS=62`);
  - все пробы Build 396–442 (`ChestGraph396Probe`, `ChestHack397Probe`, `ChestClose402Probe`, `Build436Probe` `7764`, `Build437Probe` `22`, `Build438Probe` `32`, `Build439Probe`/`Legacy`/`NoDiegesis`, `Build440Probe` `59`, `Build441Probe` `21095`, `Build442Probe` `20020`) — **PASS**;
  - **`Build443Probe`**: **`78 checks passed`** (ID/транслитерация, парсер `&`/`§`/`&#RRGGBB`/`{#RRGGBB}`/`\&` и `&l&6`, round-trip `.txt` и Base64 wire, CRUD на диске, `WRITTEN_BOOK` компоненты и обратный импорт, C2S/S2C протокол с проверкой OP и выпадением при полном инвентаре, смешанный пул на кафедре и резной полке с обновлением `HAS_BOOK` / `SLOT_OCCUPIED_PROPERTIES` и задержками событий закрытия).
- `tools/teleport_check.py` (**PASS**, 88), `tools/block_trigger_check.py` (**OK**), `tools/door_save_check.py` (**OK**), `tools/hell_dungeon_check.py` (**PASS**, `6297` + `11088` + `7`).
