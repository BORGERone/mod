# SettledLands 0.8.0 — Build 280 «Догон и выход Цель»

Два пункта к 279 и что с ними сделано.

## 1. «NPC не ускоряется, если я отхожу»

Причина найдена в коде самой 279: в режиме догона путь перестраивался каждые
4 тика (`tickCount % 4`), а каждый `moveTo` **начинает путь заново** — NPC всё
время сбрасывал разгон и до скорости догона просто не доходил.

Теперь путь держится до конца, как в ноде сопровождения (`NpcEscort`):

* пересчёт — когда `navigation.isDone()`, когда цель отошла на 2 блока от
  снимка (`EscortRules.CATCH_UP_TARGET_MOVED_SQ = 4.0`) в режиме догона, или по
  сторожу прогресса сопровождения: нет смещения на полблока за 100 тиков →
  попытка считается неудачной;
* откат попыток как у сопровождения: `20 << min(failures, 3)` = 20/40/80/160
  тиков;
* скорость догона = `GuideNpc#walkingNavigationSpeed(true)` × **«Ускорение
  догона»**, нарастающая от 1.0 на пороге догона до значения настройки на
  двойном пороге; выход из догона на 3 блоках (`CATCH_UP_END_DISTANCE_SQ`);
* новая настройка ноды **«Ускорение догона:»** 1.0 … 3.0, по умолчанию **1.5**;
* если цель дальше **24 блоков** и не видит NPC (`hasLineOfSight`), NPC
  переносится на устойчивое место (`GuideNpc#findStablePosition`) в 8 блоках от
  неё, не чаще раза в 20 тиков, только в загруженный чанк — чтобы он не отставал
  навсегда, но и не появлялся на глазах.

## 2. «Выводить из ноды параметр Цель, как в Unreal»

В этом моде нет проводов значений, зато есть подстановки. Выход ноды сделан так
же, как всё остальное в графе:

* нода публикует цель, к которой привязана, и **следующие за ней ноды** читают
  её в тексте и в действиях/командах:
  * `%pursuit%` — ник игрока или идентификатор NPC (то, что введено в «Цель»);
  * `%pursuit_kind%` — `player` или `npc`;
  * `%pursuit_uuid%` — UUID найденной сущности (пусто, пока не найдена);
* на карточке ноды появилась строка выхода `→ Цель: <значение>` над выходным
  портом;
* в инспекторе — строка «Выход: Цель → %pursuit%».

Подстановка сделана одной точкой: `NpcProfile#fill(String, Player, int)` —
через неё идут и реплики диалога (`GuideDialogue.fill`), и действия триггеров
(`NpcActions.fill`). Если в тексте нет `%pursuit`, метод не трогается.

## Диф по записям JAR

**изменено 11, добавлено 2, удалено 0** (441 → 443):

```
META-INF/neoforge.mods.toml
assets/settledlands/lang/en_us.json
assets/settledlands/lang/ru_ru.json
settledlands.mixins.json
settledlands-patch-info.txt
dev/settledlands/mixin/NpcPursuitInspectorMixin277.class
dev/settledlands/npc/NpcPursuit269.class
dev/settledlands/npc/NpcPursuit269$State.class
dev/settledlands/npc/NpcPursuit269$TargetCache.class
dev/settledlands/npc/NpcPursuit269$SlotCache.class
dev/settledlands/npc/client/PursuitGraphNode272.class
+ dev/settledlands/npc/NpcPursuit269$Published.class
+ dev/settledlands/mixin/NpcPursuitOutputMixin280.class
```

## Как собрано

Контроллер по-прежнему собран из настоящего исходника Build 271
(`reference/patch-src-build246-271/src/build271/.../NpcPursuit269.java`).

```
tools/vendor/ecj-3.46.100.jar -21 -g -nowarn \
  -cp build280-src/api:build280-src/api2:settledlands-0.8.0-build279-pursuit-distance.jar \
  -d build280-src/out build280-src/src/...

python3 tools/patch_build280.py settledlands-0.8.0-build279-pursuit-distance.jar
```

Новый миксин `NpcPursuitOutputMixin280` (`@Inject` на `RETURN` у
`NpcProfile#fill`, `cancellable`) добавлен в `settledlands.mixins.json`.

## Gates

```
validate_jar: OK      443 записи, 395 классов, toml/JSON/CRC
jvm_verify: OK        395 классов
check_mixin_class: OK 34 mixin-класса
verify_jar: PASS
```

## Что проверить в игре

1. Отойти от NPC на 10–20 блоков: он переходит на скорость догона и догоняет,
   останавливается на «Дистанции».
2. Убежать далеко (больше 24 блоков) и скрыться из вида — NPC оказывается
   рядом, но не на глазах.
3. Поставить «Ускорение догона» 1.0 и 3.0 — разница в скорости заметна.
4. Догон не дёргается: NPC не останавливается каждую секунду, идёт ровно.
5. Нода «Цель» = ник игрока, дальше нода с текстом `Привет, %pursuit%!` —
   подставляется ник; `%pursuit_kind%` = `player`; в действии (команда)
   `%pursuit%` тоже подставляется.
6. Ноды 278/279 (действие без `|…`) работают как раньше: 1.6 / 8.0 / ×1.5.
