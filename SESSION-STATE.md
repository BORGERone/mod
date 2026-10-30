# SESSION-STATE — SettledLands

Обновлено: 2026-10-30. Ветка: `arena/b98b1cb0-mod` (имя ветки задаётся платформой, переименовать нельзя).

## Текущая сборка

`settledlands-0.8.0-build273-pursuit-mixinfix.jar` — Build 273, все 4 gates PASS.
Собрана из build 272 (`google_drive/settledlands-0.8.0-build272-pursuit-single-node.jar`).
Что и почему исправлено — `README-Build273-RU.md`.

## Что лежит в репозитории

| Путь | Что это |
|------|---------|
| `tools/validate_jar.py` | gate 1: zip/CRC, toml (modId/version/displayName/description), JSON-ассеты, mixin-конфиги |
| `tools/jvm_verify.py` | gate 2: структурная проверка каждого класса (CP, опкоды, ветки, exception-таблицы) |
| `tools/check_mixin_class.py` | gate 3: mixin-конфиги + РАЗРЕШЕНИЕ ЦЕЛЕЙ (@Inject/@Shadow/@ModifyConstant) внутри JAR |
| `tools/verify_jar.py` | gate 4: прогон 1→3 + `<jar>.sha256sums.txt` |
| `tools/classfile.py` | парсер class-файлов и байткода (без JDK) |
| `tools/patch_build273.py` | патч 272→273 на уровне байткода (удаление методов, retarget shadow) |
| `build273-src/` | исходники build 273 (2 mixin-а + скрипт + README) |
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

* Ввод символов в поле ноды преследования: в build 272 он опирался на
  `charTyped(CI)V`, которого у `NpcDialogueGraphScreen` нет. Нужна сборка из
  исходников, где экран сам объявляет `charTyped(char,int)Z` (см.
  `README-Build273-RU.md`, раздел «Что пока не работает»).
* Build 263 CUTSCENE fade по-прежнему не включать.
* Build 269/270 отозваны.

## Окружение песочницы

* `python3` есть, JDK/javac — нет, поэтому пересборка Java-исходников здесь
  невозможна: правки только на уровне байткода через `tools/`.
* Прямого интернета нет; Google Drive и GitHub — через коннекторы.
* Скачанные с Drive файлы попадают в `google_drive/` (в git не коммитятся).
