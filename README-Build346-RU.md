# SettledLands 0.8.0 — Build 346 «Трассировка урона»

Поведение **не менялось**. Это сборка-прибор: она пишет в `latest.log`, где именно теряется нода «Урон».

## Что уже видно по твоему логу

`latest-4.log` (сборка 345) даёт два независимых факта.

**1. Удар до НПС доходит, а до действия — нет.** В логе два удара: `02:00:28` и `02:01:16`, и оба
проходят через `GuideNpc.hurt` — то есть цепочка урона вызывается. Но строк
`НПС …: выполнена команда триггера` и `… не выполнена` нет ни одной. Значит нода не совпала: либо её нет
в том графе, который читает сервер, либо её остановило одно из условий (мин. урон / источник / откат).
`effect give` в этом случае не пишет в чат ничего — что ты и видишь.

**2. Урон по НПС вообще не наносится — и это не наш мод.**

```
java.lang.NullPointerException: Cannot read field "damageMultiplier" because the return value of
  PlayerHelper.lastComboStep(Player) is null
  at hackersandslashers … DamageManager.modifyIncomingDamage(DamageManager.java:98)
  at … LivingIncomingDamageEvent …
  at LivingEntity.hurt(LivingEntity.java:1125)
  at GuideNpc.hurt(GuideNpc.java:336)
```

HackersAndSlashers 2.0-beta2.5 падает на собственной проверке комбо внутри `LivingEntity.hurt`, и удар
обрывается раньше, чем игра успевает снять хп: ни урона, ни отдачи, ни красной вспышки. Это его баг
(лечится обновлением/отключением этого мода), но знать о нём надо: пока он падает, «НПС не получает
урон» будет выглядеть так же, даже когда нода сработает.

## Что делает 346

Два хука, оба только читают, ничего не отменяют:

* `GuideNpc.hurt` (HEAD) — удар пришёл;
* `GuideDialogue.handleNpcDamaged` (HEAD) — какой граф собирается обойти цепочка и что решает каждая
  нода урона в нём. Этот метод вызывается для всех четырёх графов (личный, «Прогулка», «Анимация»,
  «Общее»), поэтому одного хука хватает на все.

В лог на каждый удар:

```
[dmg] hit npc='Проводник' amount=1.0 source=entity=ServerPlayer direct=null profile=true own=enabled=true entry=0 nodes=6 damage=1
[dmg] chain npc='Проводник' graph=enabled=true entry=0 nodes=6 damage=1 amount=1.0 gameTime=8123
[dmg]   node #4 type=6 min=1 filter=1(player) cooldown=1s if-in=0 -> MATCH
```

или вместо `MATCH` одна из строк:

```
-> skip: amount 1.0 < min 5
-> skip: filter 1(player), damager entity=null direct=null
-> skip: cooldown 7/20 ticks
-> MATCH if the 1 If node(s) feeding it pass
```

Кап — 80 строк за сессию, дальше прибор выключается и сам об этом пишет. Файл не разрастётся.

## Что проверить (один заход)

1. Игра запускается, НПС говорит как раньше, руки/ноги (344) на месте.
2. Ударь НПС с нодой «Урон».
3. Выйди из игры и пришли `latest.log` (или только строки с `[dmg]`).

Читается однозначно:

| что в логе | что это значит |
|---|---|
| нет ни одной строки `[dmg] hit` | удар не доходит до НПС (мод/состояние гасит атаку) |
| `damage=0` | в серверном графе нет ни одной ноды урона — нода не сохранилась |
| `nodes=…`, но нет `node #4` | сервер читает другой граф, чем редактор |
| `-> skip: …` | конкретное условие, которое её отсекает |
| `-> MATCH` и всё равно нет команды | дело в самой строке действия, разберём её |

## Гейты

```
verify_jar            PASS  (5 гейтов, 512 записей, 65 миксинов, 0 новых внешних ссылок против 345)
damage_trace_check    NEW   (оба хука — общие, на реальных методах, HEAD, без cancel; в трассе есть
                             строка удара, графа, все три условия, свой стоп-лимит)
damage_source_check   PASS
random_line_check     PASS
cinematic_check       PASS
touch_n_motion_check  PASS
punchy_bridge_check   PASS
self_link_check       PASS
common_dialogue_check PASS
common_graph_check    PASS
dropdown_check        PASS
wire_points_check     OK
blue_wire_check       OK
pursuit_check         OK
clipboard_check       OK
radius_zone_check     OK
```
