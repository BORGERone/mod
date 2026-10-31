# SettledLands 0.8.0 — Build 275 «Pursuit — стоковая нода»

Три замечания к 274 и что с ними сделано.

## 1. Не было портов у ноды преследования

`NpcPursuitCanvasMixin272#settledlands$pursuitHitPort` — `@Inject(TAIL)` в
`NpcGraphCanvas#hitPort` — возвращал `-1`, если под курсором оказывалась нода
преследования. То есть `hitPort` всегда отвечал «порта нет».

Почему так было сделано: карточка рисовалась вручную 280×140, а порты движок
считает по стоковой рамке 220×100 —

```
portScreenX(node, right) = screenX(node.x + (right ? 220 : 0))
portScreenY(node, ...)   = screenY(node.y + 50)          // обычная нода
```

Порты оказывались в пустоте, и их отключили.

Сделано: инжектор удалён, карточка теперь ровно 220×100 — порт входа
`(x, y+50)`, порт выхода `(x+220, y+50)` совпадают с нарисованными точками и с
тем, что рисует `wires()` / `hitCable()` / `hitPointPort()`.

## 2. Нода выглядела иначе, чем остальные

`NpcPursuitGraphUiMixin272#settledlands$drawPursuitNode` отменяет стоковый
`drawNode` и рисует свою карточку 280×140 со своей палитрой и заголовком 24 px.

Сделано: `PursuitGraphNode272#drawNode` переписан на стоковую геометрию и
стоковые константы, снятые с `NpcDialogueGraphScreen#drawNode`:

| Элемент | Значение |
|---|---|
| рамка | `-2..222 / -2..102`, `#46576b`, выбранная — `#e9bd67` |
| фон | `#26313f` |
| заголовок | высота 22 px, цвет типа TRIGGER `#c77dff` |
| текст заголовка | `(8, 7)`, `#f0f3f7`, обрезка по `width-16` |
| точки портов | вход `#c4d8ea`, выход `#c77dff`, по 10×10 на `h/2` |

Внутри — те же три элемента управления, ужатые в стоковое тело: тип цели
(y 27), поле значения (y 48), тумблер «Завершить преследование» (y 69),
ряды по 18 px, ширина 204.

## 3. В поле нельзя было печатать

`NpcDialogueGraphScreen` вообще не объявляет `charTyped(char, int)` — в 273
невалидный `@Inject` в `charTyped(CI)V` был удалён, и на этом ввод закончился:
`PursuitGraphNode272#charTyped` был мёртвым кодом.

Сделано: новый миксин `NpcPursuitTypingMixin275` **добавляет** в экран метод
`charTyped(char, int)` (повторяет `Screen#charTyped`: сначала виджеты, но поле
преследования имеет приоритет, пока в нём идёт правка) и перехватывает
`keyPressed`, пока поле редактируется — backspace и Enter. Enter и Esc
закрывают правку.

## Диф по записям JAR

**изменено 6, добавлено 1, удалено 0** (440 → 441 запись).

```
~ META-INF/neoforge.mods.toml
~ dev/settledlands/mixin/NpcPursuitCanvasMixin272.class
~ dev/settledlands/npc/client/PursuitGraphNode272.class
~ dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class
~ settledlands.mixins.json
~ settledlands-patch-info.txt
+ dev/settledlands/mixin/NpcPursuitTypingMixin275.class
```

`NpcPursuitGraphUiMixin272` и `NpcPursuitContextMenuMixin272` — байт в байт из
274 (они только вызывают методы `PursuitGraphNode272`, набор методов класса не
изменился: сверено, удалённых public-методов нет, добавлен один —
`editingNode(NpcDialogueGraphScreen)`).

`NpcPursuitCanvasMixin272` пересобран из исходника; сверка с 274 показала, что
единственное отличие — отсутствие `settledlands$pursuitHitPort`. Остальные
обработчики совпадают инструкция в инструкцию.

## Как собрано

```
tools/vendor/ecj-3.46.100.jar -21 -g \
  -cp <api-заглушки>:settledlands-0.8.0-build274-pursuit-hitport-fix.jar \
  -d build275-src/out \
  build275-src/src/dev/settledlands/npc/client/PursuitGraphNode272.java \
  build275-src/src/dev/settledlands/mixin/NpcPursuitCanvasMixin272.java \
  build275-src/src/dev/settledlands/mixin/NpcPursuitTypingMixin275.java

python3 tools/patch_build275.py settledlands-0.8.0-build274-pursuit-hitport-fix.jar
```

`tools/patch_build275.py` подменяет три класса, добавляет четвёртый, вписывает
`NpcPursuitTypingMixin275` в `settledlands.mixins.json`, поднимает версию и
строку описания в `neoforge.mods.toml`, обновляет `settledlands-patch-info.txt`;
остальные записи копируются байт в байт.

## Gates

```
validate_jar: OK      441 запись, toml/JSON/CRC
jvm_verify: OK        393 класса, опкоды, CP-ссылки, ветки, exception-таблицы
check_mixin_class: OK 33 mixin-класса
verify_jar: PASS
```

## Что проверить в игре

1. Открыть редактор графа, ПКМ по пустому месту → «Преследование» — нода
   создаётся, выглядит как соседние ноды: та же рамка, фон, заголовок 22 px.
2. Потянуть провод от выхода этой ноды и подвести провод к её входу.
3. Кликнуть по полю значения и напечатать `guide` (backspace/Enter).
4. Переключить тип цели и тумблер «Завершить преследование».
