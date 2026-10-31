# SettledLands 0.8.0 — Build 278 «Карточка преследования»

Одно замечание к 277 и что с ним сделано.

## «При создании ноды преследования сразу появляется нода-триггер»

Создавалась именно нода преследования: `node.type = TRIGGER`, действие
`trigger:pursuit:start:npc:`, инспектор — панель преследования. Но **карточку
рисовал штатный `NpcDialogueGraphScreen#drawNode`**, потому что в 277 при
переписывании `NpcPursuitGraphUiMixin272` потерялся хук `drawNode`. Штатный
рендер про строку преследования в меню не знает: он печатает заголовок
«Триггер #n» и строку действия как есть. Отсюда и вид «создалась нода-триггер»,
и текст `trigger:pursuit:start:npc:guide` прямо на карточке.

## Что сделано

`NpcPursuitGraphUiMixin272` снова содержит

```java
@Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;"
              + "Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
private void settledlands$drawPursuitNode(...) { ... ci.cancel(); }
```

и карточку рисует `PursuitGraphNode272#drawNode`:

* пурпурный заголовок «Преследование» (тот же цвет и та же высота 22 px, что у
  штатной ноды), рамка выбранной ноды тоже штатная;
* строка «Цель: NPC» / «Цель: игрок»;
* значение цели (или «(пусто)»);
* «Завершить преследование ✓», когда тумблер включён;
* точки портов на штатных местах: вход `(x, y+50)`, выход `(x+220, y+50)`.

Больше ничего не менялось: создание ноды, строка «+ Преследование» в меню и
панель инспектора — код 277.

## Диф по записям JAR

**изменено 3, добавлено 0, удалено 0** (441 → 441 запись).

```
~ META-INF/neoforge.mods.toml
~ dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class
~ settledlands-patch-info.txt
```

## Сверка миксина с целью

```
INJECT drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V            OK
INJECT drawNode(Lnet/minecraft/client/gui/GuiGraphics;
                Ldev/settledlands/npc/NpcDialogueGraph$Node;)V              OK
INJECT mouseClicked(DDI)Z                                                   OK
```

## Как собрано

```
tools/vendor/ecj-3.46.100.jar -21 -g \
  -cp build278-src/api:settledlands-0.8.0-build277-pursuit-native-inspector.jar \
  -d build278-src/out build278-src/src/...

python3 tools/patch_build278.py settledlands-0.8.0-build277-pursuit-native-inspector.jar
```

## Gates

```
validate_jar: OK      441 запись, toml/JSON/CRC
jvm_verify: OK        393 класса
check_mixin_class: OK 33 mixin-класса
verify_jar: PASS
```

Gate 3 получил новую фазу C — «dispatcher hooks». Если в jar есть класс,
который должен вызываться из миксина (`PursuitGraphNode272#drawNode`), то
какой-то миксин из конфигов обязан на него ссылаться, иначе gate падает с
«dead hook». На 277 эта фаза падает, на 276 и 278 — проходит: именно такой
регресс и случился в 277.

## Что проверить в игре

1. Создать ноду из меню строкой «+ Преследование»: карточка пурпурная,
   заголовок «Преследование», строки «Цель: NPC» и значение — не «Триггер #n»
   и не строка действия.
2. Выбрать её: справа панель «Тип цели / Цель / Завершить преследование /
   Удалить исходя / Удалить узел», без шаблонов и поля команды триггера.
3. Напечатать цель в поле, стереть backspace — значение на карточке меняется,
   тип ноды остаётся преследованием.
4. Проверить вход и выход: соединяются как у остальных нод.
