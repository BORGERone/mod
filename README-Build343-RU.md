# SettledLands 0.8.0 — Build 343 «Руки сцены Cinematic Interactions = руки NPC, без вылета»

## Причина вылета в 342 — моя ошибка

В 342 я полез в приватный метод Cinematic через `@Invoker` в **интерфейсном** миксине. Mixin такого не
разрешает: интерфейсный миксин может целиться только в интерфейс. Игра падала ещё до заголовка, в фазе
PREPARE:

```
FATAL [mixin/]: Mixin prepare for mod settledlands failed preparing CinematicHandsInvoker342
InvalidMixinException: @Mixin target type mismatch:
    dev.dyslexic.cinematic.client.Hands is not an interface
    at MixinInfo$SubType.validateTarget(MixinInfo.java:552)
```

То есть до стола дело не доходило: 342 не работала вообще. Гейт 342 этого не поймал — он проверял, что
класс есть, что цель названа, что инъекция одна… но не проверял **какой формы миксин** (class vs
interface). Теперь проверяет.

## Что сделано в 343

- Тот же мост, **без формы, которую Mixin отвергает**: остаётся один обычный миксин-класс с одной
  инъекцией в `Hands.drawArm` (HEAD, `require=0`, `remap=false`, `@Pseudo`).
- Приватный `Hands.drawModelArm` больше не объявляется аксессором: он **резолвится один раз**, при
  первой руке сцены, и запоминается (MethodHandle, вызов без аллокаций). Нет метода / другая версия
  Cinematic / исключение → остаётся своя рука Cinematic, **игра не падает**.
- Классы Cinematic вообще не связаны с нами на этапе компиляции: и миксин, и гард называют их
  **только текстом**, поэтому отсутствующий или переименованный мод не может уронить клиент.
- Всё остальное — как задумывалось: у вселённого игрока руку ставит и держит Cinematic, рисуется рука
  NPC (339). Не вселён / короткая рука / сбой → рисуется рука Cinematic, сцена без руки не остаётся.
- Три класса 342 и обе записи 342 из `settledlands.mixins.json` из джарника **удалены**.

## Что проверить

1. **Игра запускается** (в 342 не запускалась).
2. Вселись в NPC, открой верстак/печь/наковальню: руки в сцене — руки Айви, а не скин игрока;
   предмет в руке, наклон, покачивание — как были.
3. `armStyle` 0 и 1 в конфиге Cinematic — руки NPC в обоих.
4. Без вселения — сцена как раньше.
5. Костёр (сидячая сцена): **ноги** там пока со скина игрока (`LegMesh`) — отдельная деталь, не
   трогал.

Если руки в сцене снова человеческие — пришли `latest.log`, в нём будут строки
`[SettledLands/CinematicArms343]`: `scene hands handed to the NPC rig '...'` (передача состоялась) либо
`scene hand stayed Cinematic's own: ...` (передача не удалась, откат на руку Cinematic).

## Гейты

```
verify_jar              PASS  (5 гейтов, 502 записи, 60 миксинов, 0 новых внешних ссылок против 342,
                               0 удалённых членов)
cinematic_check         NEW   (класс-миксин, а не интерфейс — ровно та форма, что убила 342;
                               @Invoker отсутствует; Cinematic нигде не связан типом, только текстом;
                               одна клиентская инъекция в Hands.drawArm в HEAD, static, require=0;
                               передача через резолвнутый MethodHandle (unreflect/invokeExact);
                               рука забирается только после передачи; только у вселённого и только
                               там, где Cinematic сам рисовал бы руку; отчёт ограничен; классы 342 в
                               джарнике отсутствуют; классы Cinematic в джарник не попали)
touch_n_motion_check    PASS
punchy_bridge_check     PASS
self_link_check         PASS
random_line_check       PASS
common_dialogue_check   PASS
common_graph_check      PASS
dropdown_check          PASS
wire_points_check       OK
blue_wire_check         OK
pursuit_check           OK
clipboard_check         OK
radius_zone_check       OK
```

Плюс прогон на настоящих классах из джарника:

```
CinematicHandsSmokeTest — 10 ok / ALL OK  (гард: короткая рука, null, не вселён → рука остаётся
                                           Cinematic; отчёт не падает; без Cinematic передача
                                           отказывается, а не бросает)
```

Джарник собран из 342 целиком (то есть из 341 + правка формы миксина). 341, 339 и первое лицо не
тронуты.
