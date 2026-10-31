# SettledLands 0.8.0 — Build 285 «Вылет при отрисовке NPC»

## Что было

```
java.lang.NoSuchFieldError: Class net.minecraft.world.entity.Entity does not
have member field 'net.minecraft.world.entity.WalkAnimationState walkAnimation'
    at NpcMotionPlayerProxy256.handler$...$settledlands$observeLocomotion
    at NpcMotionPlayerProxy256.syncFrom
```

Сборка 283 добавила пофреймовый замер анимации и читала состояние шага так:

```java
NpcLocoDriver283.observe(npc.walkAnimation, npc);
NpcLocoDriver283.alias(npc.walkAnimation, ((Entity) (Object) this).walkAnimation);
```

В 1.21.1 поле `walkAnimation` объявлено в `LivingEntity`, а не в `Entity`. В
заглушке для компиляции оно лежало в `Entity` — код собрался, гейты прошли (в
них нет настоящего Minecraft), и падало уже в игре на первом же NPC.

## Что сделано

Чтение идёт через правильный тип:

```java
LivingEntity body = npc;
LivingEntity proxy = (LivingEntity) (Object) this;
NpcLocoDriver283.observe(body.walkAnimation, npc);
NpcLocoDriver283.alias(body.walkAnimation, proxy.walkAnimation);
```

Заглушка `Entity` больше не содержит это поле, оно перенесено в заглушку
`LivingEntity`, поэтому такая же ошибка больше не соберётся.

В jar заменён один класс — `dev/settledlands/mixin/NpcLocoObserveMixin283.class`,
в нём единственная ссылка на поле теперь `net/minecraft/world/entity/LivingEntity`.
Поведение, настройки и формат графа не менялись: анимация из 283 работает как
задумано, просто больше не роняет игру.

## Что проверить

* Спавн скопированного NPC, обычный NPC, любой моб-модель — без вылета.
* Анимация ходьбы по скорости (из 283) на месте.

## Дифф

```
записей:  448 -> 448
изменено: 3
  META-INF/neoforge.mods.toml
  settledlands-patch-info.txt
  dev/settledlands/mixin/NpcLocoObserveMixin283.class
добавлено: 0
удалено:   0
```

Гейты: `validate_jar` OK, `jvm_verify` OK, `check_mixin_class` OK (1384 внешние
ссылки, новых относительно 284 — 0), `verify_jar` PASS.
