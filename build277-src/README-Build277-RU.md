# SettledLands 0.8.0 — Build 277 «Свой инспектор без триггера»

Четыре замечания к 276 и что с ними сделано.

## Почему в 276 всё сломалось

Нода преследования хранится как нода-триггер (`type == TRIGGER`), иначе серверная
логика `trigger:pursuit:...` не сработает. Поэтому `widgets()` строила **весь**
редактор триггера — список шаблонов, поле команды, подсказку — а панель
преследования из 276 рисовалась поверх него. Спрятать их после сборки нельзя:

* `Screen.mouseClicked` перебирает детей и **не смотрит на `visible`** — скрытый
  виджет продолжает принимать клики (проверено по байткоду
  `settledlands$mouseClickedOriginal`: `invokespecial Screen.mouseClicked`);
* поле команды триггера стояло там же, где наше поле, поэтому ввод уходил в него;
* когда в поле команды попадал текст, `node.action` переставал начинаться с
  `trigger:pursuit:` — и `drawNode` рисовал ноду как обычный **триггер**. Отсюда
  «нода превращается в ноду триггера»;
* подписи двух редакторов рисовались в одних и тех же координатах — отсюда
  наслоение текста.

## 1. «В поле ввода по-прежнему нельзя ничего ввести»

В 277 ввода прямо на карточке больше нет: карточка — это только отображение.
Писать нужно в инспекторе, и это обычный `EditBox`, созданный штатным
`textBox(...)` экрана. Ввод идёт стандартным путём: клик по полю ставит фокус
(`Screen.mouseClicked`), символы приходят в `Screen.charTyped`, backspace и Enter —
в `Screen.keyPressed` (экран в конце своего `keyPressed` вызывает
`Screen.keyPressed`, который передаёт клавишу в фокусный виджет). Ничего не
сливается в класс экрана, `@Unique` нигде не используется.

Ответ на ввод идёт через штатный `propertyChange(...)`: одна запись в
`node.action`, один `remember()` на 700 мс, один `changed()`.

## 2. «Текст наслаивается, написано про триггер»

Инспектор триггера теперь **не создаётся вообще**. При сборке виджетов выбор
временно снимается (`selected = -1`), поэтому `widgets()` успевает построить
только верхнюю панель и сразу выходит из блока инспектора. Сразу после возврата
выбор восстанавливается и строится панель преследования:

* подпись «Тип цели:» и кнопка `Цель: NPC` / `Цель: игрок`;
* подпись «Цель:» и поле значения (`EditBox`, до 64 символов);
* кнопка «Завершить преследование» (включённая — с ✓);
* штатные «Отвязать» и «Удалить» — под панелью, с теми же действиями, что и в стоке
  (`disconnectOutputs` / `remove` + `widgets()`).

Ни одного виджета триггера в инспекторе не остаётся, поэтому и накладываться
нечему.

## 3. «Если ввести что-то в текстовое поле — нода превратится в триггер»

Ввод больше не может испортить действие: поле пишет `trigger:pursuit:start:npc:…`
или `...:start:player:…`, кнопка завершения — `trigger:pursuit:stop`, и только они.
Пока нода в режиме «stop», введённая цель запоминается (`LAST_START`) и
возвращается при выключении тумблера. `node.type` не меняется никогда — нода
остаётся нодой преследования.

## 4. «В списке нод строка жёлтая, хотя нода пурпурная, и нет плюса»

Строка меню создания теперь пурпурная (`0xffc77dff`, как заголовок карточки) и
подписана `+ Преследование` (`+ Pursuit`). Геометрия строки — как у стоковых:
подсветка заканчивается перед полосой прокрутки.

## Карточка ноды

Пурпурный заголовок «Преследование», ниже — `Цель: NPC` / `Цель: игрок`,
значение (или «(пусто)») и, если включено, «Завершить преследование ✓».
Геометрия и порты — стоковые: 220×100, вход на `(x, y+50)`, выход на
`(x+220, y+50)`.

## Новые/изменённые ключи языка

```
~ gui.settledlands.graph.pursuit_menu   →  «+ Преследование» / «+ Pursuit»
```

Остальные ключи преследования не менялись.

## Диф по записям JAR

**изменено 7, добавлено 1, удалено 1** (441 → 441 запись).

```
~ META-INF/neoforge.mods.toml
~ assets/settledlands/lang/en_us.json
~ assets/settledlands/lang/ru_ru.json
~ dev/settledlands/npc/client/PursuitGraphNode272.class
~ dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class
~ dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class
~ settledlands.mixins.json
~ settledlands-patch-info.txt
+ dev/settledlands/mixin/NpcPursuitInspectorMixin277.class
- dev/settledlands/mixin/NpcPursuitInspectorMixin276.class
```

## Сверка миксина с целью

```
FIELD graph / selected / inspectorX / inspectorY / inspectorW / inspectorH
FIELD sidebarScroll / buildingInspector / inspectorWidgets                   OK
SHADOW remember()V / changed()V / widgets()V / propertyChange(Runnable)V      OK
SHADOW label(Ljava/lang/String;II)V                                          OK
SHADOW textBox(Ljava/lang/String;IIIII Ljava/util/function/Consumer;)…        OK
SHADOW button(Ljava/lang/String;IIII Ljava/lang/Runnable;)…                   OK
INJECT widgets()V (HEAD), widgets()V (RETURN)
```

Все `@Shadow` и цели `@Inject` сверены гейтом `check_mixin_class` с байткодом
`NpcDialogueGraphScreen`.

## Как собрано

```
tools/vendor/ecj-3.46.100.jar -21 -g \
  -cp build277-src/api:settledlands-0.8.0-build276-pursuit-inspector.jar \
  -d build277-src/out build277-src/src/...

python3 tools/patch_build277.py settledlands-0.8.0-build276-pursuit-inspector.jar
```

## Gates

```
validate_jar: OK      441 запись, toml/JSON/CRC
jvm_verify: OK        393 класса
check_mixin_class: OK 33 mixin-класса
verify_jar: PASS
```

## Что проверить в игре

1. Выбрать ноду преследования — справа только «Тип цели / Цель / Завершить
   преследование / Отвязать / Удалить». Никаких шаблонов, поля команды и слова
   «триггер».
2. Кликнуть в поле «Цель» и напечатать `guide`, стереть backspace; текст на
   карточке меняется сразу.
3. Нажать «Цель: NPC» — станет «Цель: игрок», значение сохранится. Нажать
   «Завершить преследование» и обратно — значение вернётся.
4. Открыть меню создания: строка «+ Преследование» пурпурная, как карточка;
   создать ноду ей и убедиться, что вход и выход соединяются как у остальных нод.
