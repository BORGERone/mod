# SESSION-STATE — SettledLands

Обновлено: 2026-10-31. Ветка: `arena/b98b1cb0-mod` (имя ветки задаётся платформой, переименовать нельзя).

## Текущая сборка

`settledlands-0.8.0-build278-pursuit-card.jar` — Build 278, все 4 gates PASS.
Цепочка: 272 (база с Drive) → 273 (`tools/patch_build273.py`) → 274 (`tools/patch_build274.py`)
→ 275 (`tools/patch_build275.py`, первая сборка из исходников через ECJ) → 276 (`tools/patch_build276.py`)
→ 277 (`tools/patch_build277.py`) → 278 (`tools/patch_build278.py`, возвращён `drawNode`-хук).
Что и почему исправлено — `README-Build273-RU.md` … `README-Build278-RU.md`.
База 277 — jar 276, база 278 — jar 277; сборка из исходников: `build27X-src/` (`src/`, `stubs/`, `out/`), ECJ 3.46.100.

## Найденные краши (хронология)

| Сборка | Симптом | Причина | Кто ловит |
|--------|---------|---------|-----------|
| 272 | старт клиента | CallbackInfoReturnable на void `focus`, @Shadow на несуществующий `selected()`, @Inject в несуществующий `charTyped` | gate 3 (цели mixin-ов) |
| 273 | создание ноды преследования | `@Inject(TAIL)` без `cancellable = true` вызывает `setReturnValue` → CancellationException | gate 3, фаза `check_cancellable` |
| 273 (лог 1) | старт клиента | чужой мод `hackersandslashers` (Forge-сигнатура `ModConfigSpec.Builder.define(String,String)`) | не наш код |
| 277 | «создаётся нода-триггер» | при переписывании `NpcPursuitGraphUiMixin272` в 277 потерялся `@Inject` на `drawNode` — карточку рисовал штатный рендер («Триггер #n» + строка действия) | не ловится гейтами (нет хука — нет цели), лечится в 278 |
| 276 | «в поле не печатается, текст наслаивается, нода становится триггером» | `@Unique` переименовал `charTyped` (не переопределение), а панель 276 рисовалась поверх штатного редактора триггера; `Screen.mouseClicked` не смотрит на `visible`, поэтому спрятанное поле команды продолжало принимать клики и затирало `node.action` | не ловится гейтами (семантика UI) — лечится тем, что ветка штатного инспектора не выполняется вовсе (277) |

Правило: перед каждой выдачей прогонять `tools/verify_jar.py`; если появляется
новый класс ошибок — сначала дописать под него фазу в gate, потом чинить.

## Что лежит в репозитории

| Путь | Что это |
|------|---------|
| `tools/validate_jar.py` | gate 1: zip/CRC, toml (modId/version/displayName/description), JSON-ассеты, mixin-конфиги |
| `tools/jvm_verify.py` | gate 2: структурная проверка каждого класса (CP, опкоды, ветки, exception-таблицы) |
| `tools/check_mixin_class.py` | gate 3: mixin-конфиги + РАЗРЕШЕНИЕ ЦЕЛЕЙ (@Inject/@Shadow/@ModifyConstant) внутри JAR + фаза C «dispatcher hooks» (класс, который обязан вызываться из миксина, не должен остаться мёртвым — ловит потерю `drawNode`-хука из 277) |
| `tools/verify_jar.py` | gate 4: прогон 1→3 + `<jar>.sha256sums.txt` |
| `tools/classfile.py` | парсер class-файлов и байткода (без JDK) |
| `tools/patch_build273.py` | патч 272→273 на уровне байткода (удаление методов, retarget shadow) |
| `build273-src/` | исходники build 273 (2 mixin-а + скрипт + README) |
| `build275-src/` | исходники build 275: `PursuitGraphNode272`, 2 mixin-а, `patch_build275.py`, README, stubs |
| `build278-src/` | исходники build 278: те же 3 класса + `patch_build278.py`, README, stubs, `out/` |
| `build277-src/` | исходники build 277: `PursuitGraphNode272` (карточка), `NpcPursuitGraphUiMixin272`, `NpcPursuitInspectorMixin277`, `patch_build277.py`, README, stubs, `out/` |
| `tools/patch_build278.py` | сборка 278: 1 класс заменён (`NpcPursuitGraphUiMixin272`), toml + patch-info |
| `tools/patch_build277.py` | сборка 277: 3 класса заменены, +1 mixin, −1 mixin, mixins.json, lang, toml, patch-info |
| `tools/patch_build275.py` | сборка 275: подмена 3 классов, +1 новый mixin, mixins.json, toml, patch-info |
| `AI-GUIDE-SettledLands.md` | гайд из build 228 (актуальные разделы про сборку/ритуал доставки) |
| `latest.log` | лог от 27.10 — там битый `build236-corpse-tex.jar`, не код |

## Как проверять сборку

```bash
python3 tools/verify_jar.py <jar> --expect-version 0.8.0-buildNNN
```

Gate 3 — единственный, который ловит «Critial injection failure» на старте.
Именно он находил три ошибки build 272. Для **новых** mixin-ов всегда
прогонять его до выдачи сборки.

## Открытый хвост

* Build 276: `@Unique` на слитом `charTyped` в 275 приводил к переименованию метода —
  ввода не было; теперь метод реально переопределяет `Screen#charTyped`. Инспектор ноды
  преследования больше не триггерный: новый `NpcPursuitInspectorMixin276` прячет пресеты и
  поле команды и строит панель «тип цели / значение / завершение».
* Урок: `@Unique` на методе, который должен переопределять метод суперкласса, ломает
  переопределение молча (gates этого не видят — метод структурно валиден).
* Build 275 закрыл три пункта из 274: портов не было (инжектор `hitPort` → -1 удалён),
  карточка была 280×140 (теперь стоковая 220×100 со стоковой палитрой), ввод в поле
  не работал (`charTyped(char,int)` слит в экран миксином `NpcPursuitTypingMixin275`).
* Мёртвый код из 272 остался, не мешает: `NpcStatsGraphUi.width/height/hitPort` ветка
  `type == 16` — таких нод нет (нода преследования имеет тип 2 / TRIGGER).
* Build 263 CUTSCENE fade по-прежнему не включать.
* Build 269/270 отозваны.

## Окружение песочницы

* `python3` есть, JDK/javac в PATH нет; Java-исходники собираются вендорным
  `tools/vendor/ecj-3.46.100.jar` против заглушек `tools/api271/` (нужен только
  `java` из временного `/tmp/jdk4`). Home-путь сборки: `build275-src/`.
* Прямого интернета нет; Google Drive и GitHub — через коннекторы.
* Скачанные с Drive файлы попадают в `google_drive/` (в git не коммитятся).
