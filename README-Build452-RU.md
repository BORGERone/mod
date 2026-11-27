# Settled Lands 0.8.0 Build 452 — Условный вход на пулах мобов и наград + чистый каталог мобов

База: `settledlands-0.8.0-build451-mob-catalog-cleanup-and-more-rows.jar`

## Что изменилось в Build 452

1. **Убрано всплывающее окно при наведении в 3D-каталоге мобов (`TrialMobCatalogScreen449`)**
   - Удалены вызов `renderHoveredMobTip(...)` и сам метод всплывающей подсказки, который перекрывал карточки при наведении курсора.
   - Подсветка рамки выбранной/наведённой карточки в 4-рядной сетке 3D-каталога сохранена полностью без перекрытия соседних мобов.

2. **Условное подключение и цепочки для карточек «Пул мобов» и «Пул наград»**
   - У модулей `Пул мобов` (`SLOT_MOBS`) и `Пул наград` (`SLOT_REWARDS`) появился **левый входной сокет сигнала** (ромбовидный разъём на левой грани карточки).
   - **Без подключённого входа** карточка пула ведёт себя как раньше: автоматически отдаёт свой пул хабу триал-спавнера при сохранении, старте волны и перезапуске.
   - **Если к левому входу подключена нода управления** (`IF` Да/Нет, `Случайность`, событие спавнера или любая цепь триггеров):
     - сам пул «молчит» на постоянке и **не перезаписывает** конфиг спавнера сам по себе (и не затирается при синхронизации хаба обратно в карточки);
     - карточка активирует свой пул (и обновляет живой `TrialSpawnerBlockEntity`) **только в момент получения сигнала** (при прохождении ветки `IF` / сигнала цепи), не затрагивая остальные слоты спавнера;
     - выход пула может быть подключён как к **слоту Хаба** (`Мобы` / `Награды`), так и **дальше по цепи** к другой ноде или следующему условному пулу — сигнал применяет текущий пул и передаёт управление дальше;
     - входной сокет можно в любой момент отвязать **ПКМ по входному сокету** или кнопкой **«Отвязать вход»** в инспекторе, возвращая пул к обычному автономному поведению.

3. **Множественные карточки пулов и добавление из ПКМ-меню графа**
   - К слотам `Мобы` и `Награды` центрального хаба теперь можно подключать **несколько разных карточек** `Пул мобов` и `Пул наград` одновременно (например, разные ветки `IF` для разных волн или условий).
   - В ПКМ-меню создания нод (и при протягивании провода от `IF` / события в пустое место) в редакторе триал-спавнера добавлены пункты **`+ Пул мобов`** и **`+ Пул наград`** с автоматическим подключением провода.
   - Автономные ветки `IF → Пул` (включая `IF` с чтением переменной без отдельного триггер-входа) автоматически пересчитываются при сохранении графа, на старте волны, при зачистке волны и при перебросе диапазонов (`OP_START`).

## Изменённые и добавленные файлы

- `build303-src/src/dev/settledlands/npc/client/TrialMobCatalogScreen449.java`
- `build303-src/src/dev/settledlands/trial/TrialModule447.java`
- `build303-src/src/dev/settledlands/trial/TrialGraph446.java`
- `build303-src/src/dev/settledlands/trial/TrialGraph446Store.java`
- `build303-src/src/dev/settledlands/trial/TrialControlActions446.java`
- `build303-src/src/dev/settledlands/mixin/TrialModuleSignalMixin452.java` *(новый zero-lambda миксин на `BlockTrigger351`)*
- `build303-src/src/dev/settledlands/mixin/TrialControlWiringMixin446.java`
- `build303-src/src/dev/settledlands/npc/client/BlockMenu363.java`
- `build303-src/src/dev/settledlands/mixin/NpcTrialPalette446Mixin.java`
- `build303-src/src/dev/settledlands/npc/client/TrialModuleCard447.java`
- `build303-src/src/dev/settledlands/mixin/TrialModuleSocket447Mixin.java`
- `build303-src/src/dev/settledlands/mixin/NpcTrialSocket446Mixin.java`
- `build303-src/src/dev/settledlands/npc/client/TrialModuleClient447.java`
- `build303-src/src/dev/settledlands/mixin/TrialModuleInspector447Mixin.java`
- `build303-src/src/dev/settledlands/npc/client/GraphTooltips448.java`
- `settledlands.mixins.json`, `assets/settledlands/lang/{ru_ru,en_us}.json` (12 новых ключей, всего `1600` пар RU/EN)
- `tools/patch_build452.py`, `tools/build452_check.py`

## Сборка и проверки

- `ecj-3.46.100.jar` (`-21`) + `tools/patch_build452.py` → `settledlands-0.8.0-build452-conditional-pools-and-clean-catalog.jar`
  - Размер: `4 379 642` байт, `814` записей / `759` классов
  - SHA-256: `f931246a26c97eea5f4aab8791ed78088a44d07b26b539166cc19f823268ee0e`
- `tools/build452_check.py` — **PASS** (`BUILD450_CHECKS=642`, `BUILD452_CHECKS=45`, `11` zero-lambda миксинов, паритет `1600` RU/EN ключей, живые JVM-тесты условных пулов, цепочек, `disconnectIncoming`, `onSignalTriggered` и автономных `IF`-гейтов)
- `tools/verify_jar.py --base settledlands-0.8.0-build451-mob-catalog-cleanup-and-more-rows.jar` — **PASS** (`0` новых внешних API-ссылок, `0` оборванных ссылок)
- `tools/mixin_clash_check.py` — **PASS**
- `tools/invoke_kind_check.py --base ...build451...` — **PASS** (`7164` целей вызовов)
