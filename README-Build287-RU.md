# SettledLands 0.8.0 — Build 287 «Вылет при открытии диалога с правкой»

## Что было

```
java.lang.NoSuchMethodError: 'int dev.settledlands.npc.client.PursuitGraphNode272.hit(
    dev.settledlands.npc.client.NpcGraphCanvas, dev.settledlands.npc.NpcDialogueGraph,
    double, double)'
    at NpcGraphCanvas.handler$...$settledlands$pursuitHit(NpcGraphCanvas.java:535)
    at NpcGraphCanvas.hit(NpcGraphCanvas.java)
    at NpcDialogueGraphScreen.wires(NpcDialogueGraphScreen.java:2664)
    at NpcDialogueGraphScreen.renderBackground(NpcDialogueGraphScreen.java:2243)
```

Граф открывался, но падал, как только экран диалога начинал рисовать фон.

## Почему

В 286 класс `PursuitGraphNode272` переписывался ради пункта «провод рисуется
плохо»: из него убиралось старое рисование по пикселям. Вместе с ним уехали два
служебных метода — отсечение карточки и попадание по ней:

```java
public static boolean visible(NpcGraphCanvas c, Node n)
public static int hit(NpcGraphCanvas c, NpcDialogueGraph graph, double sx, double sy)
```

Их вызывает миксин холста `NpcPursuitCanvasMixin272`, который лежит в jar
скомпилированным против 285 и в сборке не перекомпилируется. Поэтому:

* компилятор видел только новый исходник и промолчал — там никто эти методы не звал;
* `jvm_verify` проверяет форму каждого класса по отдельности, а не ссылки между ними;
* `check_mixin_class` проверяет точки внедрения, а не вызовы внутри тела миксина.

Итог: сборка прошла все гейты и упала в игре на первом же открытии диалога.

## Что сделано

Оба метода восстановлены ровно такими, какими были в 285: та же сигнатура, тот
же модификатор доступа, тот же поток инструкций. Логика не менялась, новых
полей нет, `.bbmodel` и конфиг миксинов не тронуты.

Заодно в гейты добавлена проверка, которая делает такой вылет невозможным:
`tools/dangling_refs.py` сверяет каждый класс с предыдущим jar и падает, если
класс потерял метод или поле, на которые внутри jar кто-то ещё ссылается. На
сборке 286 она воспроизводит этот краш ровно двумя строками:

```
! dev/settledlands/mixin/NpcPursuitCanvasMixin272 still calls
  dev/settledlands/npc/client/PursuitGraphNode272 method hit(...)
! dev/settledlands/mixin/NpcPursuitCanvasMixin272 still calls
  dev/settledlands/npc/client/PursuitGraphNode272 method visible(...)
```

Теперь гейтов пять: `validate_jar` → `jvm_verify` → `check_mixin_class` →
`dangling_refs`, всё через `verify_jar.py`.

## Изменения в jar

```
~ dev/settledlands/npc/client/PursuitGraphNode272.class
~ META-INF/neoforge.mods.toml
~ settledlands-patch-info.txt
```

Записей в jar: 448 → 448, ни одного добавленного или удалённого файла. Версия,
description и displayName в `neoforge.mods.toml` обновлены на
`0.8.0-build287-graph-hit-restore`.

## Что проверить в игре

1. Открыть диалог с правкой на любом NPC — вылета быть не должно, граф
   отрисовывается как обычно.
2. Ноды преследования: карточки видны, попадание по карточке мышью работает,
   при прокрутке за пределы экрана карточки корректно пропадают.
3. Из 286 остаётся проверить то, что ещё не подтверждено: женский японский рыбак,
   провод цели при дальнем зуме и синий выход → синий вход цели.

## Гейты

`validate_jar`, `jvm_verify`, `check_mixin_class`, `dangling_refs` — все пройдены.
Новых внешних ссылок API относительно 286 нет.
