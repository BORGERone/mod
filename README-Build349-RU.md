# SettledLands 0.8.0 — Build 349 «Броня: исправление краша 348»

348-я падала на первом же кадре. Причина в логе одна.

```
java.lang.NoSuchMethodError: 'net.minecraft.client.model.EntityModel
    net.minecraft.client.model.geom.EntityModelSet.bakeLayer(ModelLayerLocation)'
    at dev.settledlands.model.BodyArmor348.bake(BodyArmor348.java:337)
net.minecraft.ReportedException: Rendering entity in world
```

## Почему

`EntityModelSet#bakeLayer` возвращает **корневую часть слоя** (`ModelPart`), а не модель. Модель
строит вызывающий: `new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR))`. Я объявил
тип возврата как `EntityModel` по памяти — и, что хуже, эта самая обёртка `new HumanoidModel<>(…)`
стояла в той же строке доказательства, по которой я «подтверждал» вызов. Заглушка совпала с памятью,
гейт проверял только имя метода — и ошибка доехала до игры.

Второй, более важный дефект: пасс отрисовки не был защищён. Неверный внешний вызов брони не должен
убивать клиент — броня это визуальный слой, без неё игра обязана работать.

## Что сделано

1. **Вызов приведён к реальному API:** `bakeLayer` → `ModelPart`, затем `new HumanoidModel<>(root)`.
   Заглушка (`api5-src`), таблица `CONFIRMED_EXTERNAL` и гейт `body_armor_check` теперь несут и
   проверяют **форму** вызова, а не только имя.
2. **Пасс больше не может уронить клиент:** и `bake`, и `draw` обёрнуты в защиту. Первый сбой
   выключает пасс до конца сессии и пишет одну строку
   `SettledLands/Armor348: armour pass switched off for this session: <ошибка>` — то есть цена
   неверного вызова это броня, а не игра.
3. Всё из 348 остаётся: броня рисуется поверх собственного тела игрока и каждого NPC, NPC надевают
   броню из своего инвентаря.

## Что проверить

1. Игра запускается и мир грузится (это и был краш).
2. Надень броню: шлем, нагрудник, поножи, боты — на теле, двигается вместе с телом.
3. NPC: положи нагрудник в его инвентарь — надевается; перезайди в мир — NPC одеты.
4. Если брони на теле не видно вообще, а в логе есть строка `armour pass switched off for this
   session: …` — пришли эту строку, по ней видно, какой именно вызов не сошёлся. Игра при этом
   работает.

## Гейты

```
verify_jar          PASS  (5 гейтов, 518 записей, 68 миксинов, 0 новых внешних ссылок против 348)
body_armor_check    OK    (обновлён: якорь инъекции, списки миксинов, синк только читает инвентарь,
                           форма bakeLayer → ModelPart + HumanoidModel(ModelPart),
                           и защита от исключений в draw/bake)
blue_wire_check     OK      cinematic_check       PASS    clipboard_check      OK
common_dialogue_check PASS  common_graph_check    PASS    damage_source_check  PASS
damage_trace_check  PASS    dropdown_check        PASS    punchy_bridge_check  PASS
pursuit_check       OK      radius_zone_check     OK      random_line_check    PASS
self_link_check     PASS    touch_n_motion_check  PASS    wire_points_check    OK
```

## Открыто (следующими шагами)

* волосы под шлемом — три плоскости внутри костей `head` (две) и `bone` (одна);
* блоки торса у женской модели — нужно определение пользователя (какие кости/координаты);
* узоры кузнечных шаблонов (`ArmorTrim`) не рисуются;
* 347: подтверждения ноды урона в личной вкладке в этом логе нет — в сессии не было ни одного удара
  (`[dmg]` в логе ноль строк).

## Файлы

* джарник: `settledlands-0.8.0-build349-armor-apifix.jar`
* исходники: `SettledLands-Build349-ArmorApiFix-Source.zip`
* контрольные суммы: `SHA256SUMS-build349.txt`
