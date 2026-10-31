# SettledLands 0.8.0 — Build 273 «Pursuit Mixin Fix»

## Причина краша

Build 272 содержал три невалидных mixin-а. Каждый из них роняет клиент при
загрузке класса `NpcDialogueGraphScreen` / `NpcGraphCanvas` (то есть при
открытии редактора диалогового графа), потому что в `settledlands.mixins.json`
стоит `injectors.defaultRequire = 1`:

| # | Класс mixin | Цель | Ошибка |
|---|-------------|------|--------|
| 1 | `NpcPursuitCanvasMixin272#settledlands$pursuitFocus` | `NpcGraphCanvas#focus(Node)` | метод возвращает `void`, а callback объявлен `CallbackInfoReturnable` → critical injection failure |
| 2 | `NpcPursuitGraphUiMixin272#selected` (`@Shadow`) | `NpcDialogueGraphScreen#selected()` | такого метода нет: экран хранит `int selected` и метод `node()` = `graph.get(selected)` → unresolved shadow |
| 3 | `NpcPursuitGraphUiMixin272#settledlands$pursuitCharTyped` | `charTyped(CI)V` | экран не объявляет `charTyped` вообще (`Screen#charTyped(char,int)Z`, унаследованный метод не инжектируется) → target not found |

Отдельно: в `latest.log` от 27.10 краш другой природы — в папке `mods`
лежал битый файл `settledlands-0.8.0-build236-corpse-tex.jar`
(`java.util.zip.ZipException: zip END header not found`, «is not a jar file»).
Это недокачанный архив, его надо просто удалить.

## Что изменено в Build 273

Диф по записям JAR: **изменено 4, добавлено 0, удалено 0**.

* `dev/settledlands/mixin/NpcPursuitCanvasMixin272.class`
  — удалён обработчик `settledlands$pursuitFocus` (focus остаётся ванильным).
* `dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class`
  — удалён `settledlands$pursuitCharTyped`;
  — `@Shadow selected()` переименован в `node()` (та же семантика), вызов в
    `settledlands$pursuitKeyPressed` перенаправлен.
* `META-INF/neoforge.mods.toml` — `version = 0.8.0-build273-pursuit-mixinfix`,
  строка Build 273 первой в `description`.
* `settledlands-patch-info.txt` — описание сборки.

Остальные 436 записей скопированы байт в байт.

## Что работает

* Карточка ноды преследования: отрисовка, dropdown «NPC / Игрок»,
  подсветка поля, тумблер «Завершить преследование» — клики идут через
  `mouseClicked`/`drawNode`, они не затронуты.
* Создание ноды из контекстного меню (`rowAt`/`scroll` константы на месте).
* `keyPressed` на выбранной ноде (Enter / Esc) — теперь через `node()`.
* Серверная логика `NpcPursuit269`, сохранение/загрузка NBT — без изменений.

## Что пока не работает

Ввод символов в поле ноды. В Build 272 он опирался на несуществующий
`charTyped`, а добавить инжект некуда: у `NpcDialogueGraphScreen` нет своего
`charTyped`. Чтобы поле заработало, нужна сборка из исходников, где:

1. в `NpcDialogueGraphScreen` добавляется
   `@Override public boolean charTyped(char ch, int modifiers)` — проброс в
   `PursuitGraphNode272.charTyped(...)` для выбранной pursuit-ноды,
   `return true` при поглощении;
2. `settledlands$pursuitCharTyped` в `NpcPursuitGraphUiMixin272`
   больше не нужен.

Исходники build 273 (`build273-src/`) уже без двух битых обработчиков и с
исправленным `mouseClicked` (`setReturnValue` + `cancel`).

## Gates

```
python3 tools/verify_jar.py settledlands-0.8.0-build273-pursuit-mixinfix.jar
validate_jar: OK     440 записей, 392 класса, toml/JSON парсятся
jvm_verify: OK       392 класса, опкоды, CP-ссылки, ветки, exception-таблицы
check_mixin_class: OK 32 mixin-класса, цели @Inject/@Shadow/@ModifyConstant
                    разрешены реально (внутри JAR), внешние цели — отдельным списком
```

`check_mixin_class.py` расширен резолвом целей — именно этот режим ловит
ошибки, которые пропустили gates build 272.

## Проверка в игре

1. Удалить из `mods` битый `settledlands-0.8.0-build236-corpse-tex.jar` и
   любые другие копии SettledLands, оставить только build 273.
2. Открыть редактор диалогового графа (`/npc edit` → граф) — раньше здесь
   падал клиент.
3. ПКМ по пустому месту → «Преследование» → создать ноду, переключить
   dropdown, тумблер, Enter/Esc.
