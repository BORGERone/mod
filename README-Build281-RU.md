# SettledLands 0.8.0 — Build 281 «Вылет при далёкой цели»

Один вылет из 280 и что с ним сделано.

## Что было

```
java.lang.NoSuchMethodError: 'boolean net.minecraft.world.entity.Entity.
                                hasLineOfSight(net.minecraft.world.entity.Entity)'
    at dev.settledlands.npc.NpcPursuit269.tick(NpcPursuit269.java:302)
```

В 280 проверка «видит ли цель NPC» вызывала `Entity#hasLineOfSight(Entity)`.
У `Entity` такого метода нет — он есть у `LivingEntity`. Компилятор об этом не
знал: метод был объявлен в **заглушке** `net/minecraft/world/entity/Entity.java`,
которую я написал для сборки в песочнице. Пока цель была ближе 24 блоков, ветка
не выполнялась; как только убежал дальше — сервер упал («Ticking entity»).

## Что сделано

Проверка больше не обращается к Minecraft вообще: NPC считается скрытым, если он
вне **конуса обзора цели** — 70° по половине угла, считается по `yaw` цели:

```java
double yaw  = Math.toRadians(target.getYRot());
double lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
double dot  = (dx * lookX + dz * lookZ) / horizontal;
return dot < Math.cos(Math.toRadians(coneDegrees));
```

Это те же данные, что уже использовала геометрия строя (`slotPosition`), то есть
вызовы, которые в сборке работают с самого начала. Перенос NPC остался: цель
дальше 24 блоков, NPC вне конуса обзора, не чаще раза в 20 тиков → устойчивое
место в 8 блоках (`findStablePosition`, `hasChunkAt` — оба уже используются в
моде). Остальное — как в 280: дисциплина пересчёта пути сопровождения (из-за
неё NPC разгоняется), «Ускорение догона», выход `%pursuit%`.

## Новый гейт: фаза D «external API»

Класс ошибки новый, поэтому сначала гейт, потом починка (правило из
SESSION-STATE).

`tools/check_mixin_class.py` получил фазу D и ключ `--base <предыдущий jar>`:

> каждый вызов Minecraft / NeoForge из наших классов должен уже встречаться в
> сборке, которая работает в игре.

Проверка идёт по константному пулу (`owner#name+desc`):

* ссылка есть в базовой сборке ровно в таком виде — ок;
* тот же `name+desc` есть у базовой сборки на другом внешнем классе
  (Minecraft в jar нет, параметр типа `Entity` может законно объявлять метод,
  который база вызывает через `ServerPlayer`) — ок;
* метода нет вовсе, либо он есть только на **нашем** классе, а новый код
  объявляет его на внешнем — FAIL.

Если каталога базовой сборки нет, фаза печатает «not checked».

Проверка на реальных сборках:

```
build279 vs build278   OK   1383 ссылки, новых 0
build280 vs build279   FAIL dev/settledlands… Entity#hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z
                             is used by this jar and is known from nothing in the base jar
build281 vs build279   OK   1384 ссылки, новых 0
```

`tools/verify_jar.py` принимает `--base` и передаёт его в гейт 3.

## Диф по записям JAR

**изменено 3, добавлено 0, удалено 0** (443 → 443):

```
META-INF/neoforge.mods.toml
dev/settledlands/npc/NpcPursuit269.class
settledlands-patch-info.txt
```

Внутренние классы `NpcPursuit269$*` перекомпилированы без изменений смысла,
поэтому в jar оставлены копии из 280 — диф остаётся честным.

## Как собрано

```
tools/vendor/ecj-3.46.100.jar -21 -g -nowarn \
  -cp build281-src/api:build281-src/api2:settledlands-0.8.0-build280-pursuit-catchup.jar \
  -d build281-src/out build281-src/src/...

python3 tools/patch_build281.py settledlands-0.8.0-build280-pursuit-catchup.jar
```

Из заглушки `Entity` удалён ложный `hasLineOfSight`.

## Gates

```
validate_jar: OK      443 записи, 395 классов, toml/JSON/CRC
jvm_verify: OK        395 классов
check_mixin_class: OK 34 mixin-класса, фаза D: новых внешних API 0
verify_jar: PASS
```

## Что проверить в игре

1. Нода преследования на игрока, убежать дальше 24 блоков — вылета нет.
2. Убежать далеко и скрыться за поворотом — NPC оказывается рядом, но не на
   глазах; если он в поле зрения, переноса нет, он просто догоняет бегом.
3. «Ускорение догона» 1.0 / 3.0 — разница в скорости заметна.
4. NPC останавливается на «Дистанции» и не заходит вплотную.
5. Текст `%pursuit%` в следующей ноде подставляет цель.
