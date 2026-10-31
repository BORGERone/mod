# Исходники SettledLands — что реально есть и где

Проверено 2026-10-31 по всем 10 веткам `github.com/BORGERone/mod` и по всему
Google Drive (76 архивов `*-Source.zip`, 24 папки).

## Короткий вывод

Полного дерева проекта (Gradle: `src/main/java/dev/settledlands/...`) **нет ни в
ветках, ни на Drive**. Всё, что существует, — это три вида артефактов:

1. **патч-исходники** — 2–40 маленьких `.java` (миксины + вспомогательные классы),
   из которых собирались конкретные сборки;
2. **декомпилированный справочник** — полное дерево классов, но только для
   build 73/74 и build 132;
3. **собранные JAR** — единственный актуальный источник для 272+.

Класс `NpcDialogueGraphScreen` (центр всей работы с графом, 94 КБ байткода,
181 метод в текущей версии) в актуальном виде существует **только как байткод**.

## 1. Ветки репозитория

| Ветка | `.java` | Что внутри |
|-------|---------|-----------|
| `arena/24676dbe-mod` | 118 мод-файлов | `src/build246 … src/build271` — патч-исходники всех сборок 246–271 (миксины, `NpcAutoAiGraphUi255`, `NpcStatsGraphUi`, `PursuitGraphNodes271`, `NpcPursuit269`), плюс `tools/` со заглушками API |
| `arena/801cfd09-mod` | 74 мод-файла | то же до `src/build263` |
| `arena/829e0ad0-mod` | 1 | `patch-src-229/` (один файл) |
| `arena/b98b1cb0-mod` (эта) | — | `build273-src … build278-src` (наши патч-исходники) + `reference/` |
| `arena/b7115d06-mod` | 0 мод-файлов | только `tools/` |
| `09f50ea8`, `3069fd2a`, `5962dc3d`, `8afbe7d2`, `main` | 0 | исходников нет |

Локальная копия патч-исходников 246–271 уже выгружена в
`reference/patch-src-build246-271/` (102 файла, 1.2 МБ) — можно смотреть без
переключения ветки.

## 2. Google Drive

Полные данные: `SettledLands-Build272-Source.zip` (id `1uU_tH1_NHNlH6bqPpXxvL2Zh3JLW9gt_`),
`SettledLands-Build271-Pursuit-Nodes-Source.zip` (id `1HGXD-ra5htPvb9QP9lzLZM35GZFiVXtk`),
`SettledLands-Build272-Pursuit-SingleNode-Source.tar.gz` (id `1QBphdFCIvanAfecUewm7c6Lvk6GSbD8I`).

| Файл | id | Что внутри |
|------|----|-----------|
| `SettledLands-Build132-Cutscene-Full-Source.zip` | `1ZS1yIlEbOg9FOIX4DgCbpshXlZBSa7GZ` | **единственное полное дерево**: `reference-decompiled-java/` (114 файла) + `patch-src/` (50 файлов) + baseline jar + манифесты |
| `SettledLands-Build272-Source.zip` | `1uU_tH1_NHNlH6bqPpXxvL2Zh3JLW9gt_` | патч-исходники 272 (7 файлов) + `baseline271.jar` |
| `SettledLands-Build272-Pursuit-SingleNode-Source.tar.gz` | `1QBphdFCIvanAfecUewm7c6Lvk6GSbD8I` | снимок рабочего каталога 272: `src/build272`, `tools/api263…271`, 485 `.java` (сам мод — только 7) |
| `SettledLands-Build271-Pursuit-Nodes-Source.zip` | `1HGXD-ra5htPvb9QP9lzLZM35GZFiVXtk` | снимок 271 (959 файлов, 496 `.java`, из них мод — `src/build269..271`) |
| `SettledLands-Build269/270/263/268-…-Source.zip` | см. Drive | патч-исходники соответствующих сборок |
| `SettledLands-Build74-Decompiled` / `Build73-Decompiled` (папки) | `1iVZtDhuje2CkjNI9Fns9-0kZguVIJjsW`, `1CASsOZHGVq_m5fbfnVx56Nyg4IZlAF7H` | декомпил 73/74 |

Скачано и разложено в репозиторий:

```
reference/decompiled-build132/   114 .java  — полное дерево build132 (декомпил)
reference/patched-build132/       50 .java  — патч-исходники build132 (слияние 172+226)
reference/patch-src-build246-271/ 102 .java — патч-исходники 246–271 (из ветки)
```

## 3. Насколько это применимо к текущей сборке

`reference/patched-build132/…/NpcDialogueGraphScreen.java` — 2025 строк,
и в нём **нет** ни `pursuit`, ни `NpcStatsGraphUi`, ни `NpcAutoAiGraphUi255`,
ни фейдов катсцены. Текущий класс — 181 метод и 94 КБ байткода; появившиеся
после 132 подсистемы (стат-инспектор, авто-ИИ, преследование, катсцены,
профессии) в этом исходнике отсутствуют.

То есть старые исходники годятся как справочник по стилю и по базовым веткам
`widgets()`/`drawNode()`, но не как база для правки текущей сборки.

## 4. Что нужно, чтобы уйти от байткода

Полное актуальное дерево проекта (Gradle-проект или `src/main/java` сборки
272+, экспорт из IDE). Тогда правки идут прямо в `.java`, ECJ-компиляция всего
дерева и байткод-вставка больше не нужны, а регрессы вида «потерялся хук
drawNode» станут невозможны.
