# SettledLands 0.8.0 — Build 276 «Свой инспектор и рабочий ввод»

Два замечания к 275 и что с ними сделано.

## 1. «По-прежнему нельзя писать»

В 275 в экран сливался метод

```java
@Unique
public boolean charTyped(char ch, int modifiers) { ... }
```

`@Unique` — это указание Mixin «сделай имя уникальным», то есть при слиянии метод
переименовывается (`charTyped$<mixin>`). Переопределения `Screen#charTyped` не
получалось: символы по-прежнему уходили в стоковую реализацию, поле на карточке их
не видело. Метод был валиден по структуре, поэтому gates его пропустили — ошибка
видна только в рантайме.

Сделано: `@Unique` убран, метод действительно переопределяет `Screen#charTyped`
(сначала поле карточки, затем виджеты — как в стоке). Дополнительно:

* `keyPressed` перехватывается только когда на карточке идёт правка, backspace и
  Enter/Esc работают;
* если фокус у виджета (поле в инспекторе), карточка ввод не перехватывает —
  `PursuitGraphNode272` смотрит `screen.getFocused()`;
* клик по полю карточки снимает фокус с виджетов (`setFocused(null)`), чтобы
  ввод шёл именно туда, куда кликнули.

## 2. «В окне редактирования то же, что у ноды IF»

Нода преследования — это `Node.type == TRIGGER (2)`, поэтому `widgets()` строила
штатный инспектор триггера: список пресетов (`gui.settledlands.graph.trigger_presets`)
и произвольную команду в многострочном поле.

Сделано: новый миксин `NpcPursuitInspectorMixin276`, `@Inject(TAIL)` в
`widgets()`. Если выбранная нода — нода преследования:

* список пресетов и поле команды скрываются (`visible = false`, снят фокус,
  убраны из `inspectorWidgets`) — скрытый виджет в 1.21.1 не участвует в
  hit-test, потому что `AbstractWidget#isMouseOver` проверяет `active && visible`;
* строится панель преследования: подпись «Тип цели» → кнопка-переключатель
  `Цель: NPC` / `Цель: игрок`; подпись «Цель» → поле значения (обычный `EditBox`,
  через приватный хелпер экрана `textBox(...)`, поэтому ввод идёт штатным путём
  через виджеты); кнопка «Завершить преследование» (включённая — с ✓);
* штатные кнопки «Отвязать» и «Удалить» остаются, но переезжают под панель
  (`setY`).

Карточка и инспектор читают одно и то же — `node.action`, так что расхождений нет.
Пока нода в режиме «stop», введённая цель запоминается и возвращается при
выключении тумблера.

## Новые ключи языка

`assets/settledlands/lang/{ru_ru,en_us}.json`:

```
gui.settledlands.pursuit.target_value
gui.settledlands.pursuit.target_npc_button
gui.settledlands.pursuit.target_player_button
gui.settledlands.pursuit.stop_toggle_on
```

## Диф по записям JAR

**изменено 7, добавлено 1, удалено 1** (441 → 441 запись).

```
~ META-INF/neoforge.mods.toml
~ assets/settledlands/lang/en_us.json
~ assets/settledlands/lang/ru_ru.json
~ dev/settledlands/npc/client/PursuitGraphNode272.class
~ dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class
~ settledlands.mixins.json
~ settledlands-patch-info.txt
+ dev/settledlands/mixin/NpcPursuitInspectorMixin276.class
- dev/settledlands/mixin/NpcPursuitTypingMixin275.class
```

`NpcPursuitCanvasMixin272` и `NpcPursuitGraphUiMixin272` — байт в байт из 275.

## Сверка миксина с целью

Перед сборкой все `@Shadow` нового миксина сверены с байткодом
`NpcDialogueGraphScreen` (все резолвятся):

```
FIELD graph / selected / inspectorX / inspectorY / inspectorW / sidebarScroll / inspectorWidgets  OK
SHADOW label(Ljava/lang/String;II)V                                        OK
SHADOW textBox(Ljava/lang/String;IIIII Ljava/util/function/Consumer;)…      OK
SHADOW button(Ljava/lang/String;IIII Ljava/lang/Runnable;)…                 OK
INJECT widgets()V (TAIL), keyPressed(III)Z (HEAD)
MERGED charTyped(CI)Z  (без @Unique)
```

## Как собрано

```
tools/vendor/ecj-3.46.100.jar -21 -g \
  -cp <заглушки tools/api271>:settledlands-0.8.0-build275-pursuit-stock-node.jar \
  -d build276-src/out build276-src/src/...

python3 tools/patch_build276.py settledlands-0.8.0-build275-pursuit-stock-node.jar
```

## Gates

```
validate_jar: OK      441 запись, toml/JSON/CRC
jvm_verify: OK        393 класса
check_mixin_class: OK 33 mixin-класса
verify_jar: PASS
```

## Что проверить в игре

1. Выбрать ноду преследования — справа должна быть панель «Тип цели / Цель /
   Завершить преследование», а не список пресетов и поле команды.
2. Кликнуть в поле «Цель» в инспекторе и напечатать `guide`; backspace работает,
   на карточке текст появляется сразу.
3. Нажать «Цель: NPC» — переключится на «Цель: игрок», введённое значение
   сохранится.
4. Кликнуть по полю на самой карточке и печатать там; Enter/Esc закрывают правку.
