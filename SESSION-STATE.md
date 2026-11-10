# SESSION-STATE — SettledLands

> ## ⚠️ СЕССИЯ ПЕРЕДАЁТСЯ. ЧИТАЙ `HANDOFF-NEXT-AI-RU.md` ПЕРВЫМ.
>
> **2026-11-02: сборки 405 и 406 отозваны** (`git revert`, коммит `ebe27da`). Дерево побайтово равно
> **build 404** (`ee3b70e`). Причина: 405-я переносила тело проводника на 0.85 блока, когда шаг не
> срабатывал, — автор увидел «телепортируется между дверьми»; 406-я (двери за 12 блоков) не помогла.
> Записи ниже про 405/406 — **история, не текущее состояние**. Jar-ы 405/406 остались только на Google
> Drive (`DRIVE-LINKS-RU.md`).
>
> Текущая база: `settledlands-0.8.0-build404-through-the-doorway.jar`.

Обновлено: 2026-11-09. Ветка: `arena/78029e2d-mod`.

> **Сначала читать `HANDOFF-NEXT-AI-RU.md`** — там текущая задача, открытый дефект, порядок действий и
> ловушки. Этот файл — история и хронология.

## Текущая сборка

`settledlands-0.8.0-build416-hns-stealth-gaze.jar` (sha256 `c2d05abc69e0ddf75eee1b3009b0b3d535756629a0e64431b8a89e82bf9cb079`,
база — **build 415**) — **совместимость поворота головы НПС и глазика скрытности с модом Hackers 'n Slashers**:
* в `NpcHnsStealth416` + `GuideNpcStealth416Mixin` + `NpcAutoAiStealth416Mixin`: вне активного диалога НПС
  поворачивает голову (`yHeadRot`) на игрока только при прямой видимости (`hasLineOfSight`), отсутствии зелья
  невидимости (кроме `hasTrueVision`) и — когда активен стелс Hackers 'n Slashers — только при обнаружении
  (`sightLevel >= 90.0` захват, `>= 40.0` удержание), поэтому голова НПС больше не крутится на 360° сквозь
  стены и не срывает стелс со спины; скрытый рядом игрок больше не замораживает idle-осматривание и не
  перекрывает более далёкого видимого игрока;
* для `GuideTriggerNpc` и `GuideCorpseNpc` добавлены `data/settledlands/mob_behavior_override/{guide_trigger,guide_corpse}.json`
  (`visionRange=0`, `listeningRange=0`, `fieldOfView=0`) и ежетиковая очистка `sightLevel`/подозрений
  (`GuideNpcStealth416Mixin`, `GuideCorpseStealth416Mixin`), чтобы маркеры и трупы не открывали глазик скрытности;
* обнаружен и закрыт баг старого стаба `build303-src/stubs/org/spongepowered/asm/mixin/injection/Redirect.java`
  (`At[] at()` вместо одиночного `At at()`, из-за чего Mixin игнорировал `@Redirect`): в `build303-src/api416`
  положен корректный `Redirect.java`, а `tools/check_mixin_class.py` теперь проверяет `@Redirect` (тег `0x40`
  и дескриптор обработчика); добавлен гейт `tools/hns_stealth_check.py` + `HnsStealth416Probe.java` (36/36 гейтов PASS,
  `verify_jar --base 415` PASS). Нода «В радиусе» и активное «Преследование» не менялись по согласованию с автором.
**В игре не проверено.**


`settledlands-0.8.0-build415-door-at-hand-and-2block-height.jar` (sha256 `50f8be9ab2ae0ac34e56034141fb5ad406a715e8d2be26efcf1c6dfcedc8b808`,
база — **build 414**) — **открытие двери непосредственно при подходе (1.25 блока) и высота проводника 1.8
блока для проходов высотой 2 блока** (по отзыву автора после успешной проверки 414-й в игре):
* в `NpcDoors368`: `PERIOD` 5→3 тика, `REACH` 1.9→`1.25`, `STUCK_REACH` 3.0→`1.55`, `STUCK_TICKS` 20→18, а
  `openOnPath` теперь тоже ограничен горизонтальной дистанцией `reach` (больше не открывает двери за 3–4 блока
  по узлам маршрута);
* в `NpcContent`: высота `sized(0.6F, 2.1F)` пропатчена на `sized(0.6F, 1.8F)` (`0x40066666` → `0x3FE66666`),
  чтобы проводник проходил в дверные проёмы с блоком сверху и любые двухблочные проходы (и в `WalkNodeEvaluator`
  `entityHeight = floor(1.8 + 1.0) = 2` вместо `3`);
* `tools/door_lock_check.py` расширен проверками высоты `1.8F` (и запретом `2.1F`) и дистанции `1.25` (и
  запретом `3.0`). 35/35 гейтов PASS, `verify_jar --base 414` PASS.
**В игре подтверждено автором (двери и проходы 2 блока полностью закрыты).**


`settledlands-0.8.0-build414-door-pass-linked.jar` (sha256 `bac93703267493c1895b9d601888660596563d029a7ef4b2124f1f895eb6495a`,
база — **build 413**) — **исправлены обе рантайм-ошибки сцепления классов в `NpcDoors368`**, обнаруженные в
`latest.log` запуска 413-й сборки:
* упакованы обновлённые вложенные классы `NpcDoors368$Open.class` (с полем `boolean walkedTo`, отсутствие
  которого в jar 407–413 вызывало `NoSuchFieldError` в `stopLeftBehind` и `markWalkedTo`) и
  `NpcDoors368$Watch.class`;
* вызов `setCanPassDoors(true)` перенесён с `PathNavigation` (где в MC 1.21.1 его нет — `NoSuchMethodError`)
  на `NodeEvaluator` (`setCanPassDoors` / `canPassDoors`);
* согласованы обе половины двухблочной двери (`baseDoorPos`) для `DoorGuard371#guideOpened`, разделён отсчёт
  тиков по `UUID` каждого проводника (`LAST_TRACK`) между `GuideDoorPass413Mixin` и `onServerTick`, и двери
  (как обычные, так и запертые) открываются строго на пути проводника с немедленным закрытием за спиной;
* расширены `tools/dangling_refs.py` (проверка внутримодовых полей/методов в jar) и `tools/door_lock_check.py`
  (живая JVM-проба `DoorLock368Probe`). 35/35 гейтов PASS, `verify_jar --base 413` PASS.
**В игре не подтверждено.**


`settledlands-0.8.0-build404-through-the-doorway.jar` — **проводник идёт через открытый проём**. Отчёт после
403: «доходит до двери, теперь открывает, но дальше открытой двери не проходит».
Две причины, обе про *когда* дверь открыта:
* дверь открывалась, когда проводник уже у неё (1.9), а его путь к тому моменту построен при закрытой двери
  и **заканчивается у двери** — теперь двери открываются **впереди** (4 блока, конус по `nav.getTargetPos()`)
  → `NpcDoors368#openAhead`;
* стоящего у **открытой** двери проводника проводят через проём: короткий путь за проём, иначе шаг прямо в
  `MoveControl#setWantedPosition` (шаг, не телепорт) → `#pushThrough`, триггер: стоит 20 тиков + открытая
  дверь в 2 блоках + (`hasPath` ИЛИ дверь между ним и запомненной целью навигатора `WATCH`); не чаще 1/с и
  ≤3 раз на дверь (`mayPush`).
Лог стоящего проводника теперь печатает состояние навигатора: `pathState` (узлы/следующий/цель/`nav target`),
`horizontalCollision`, результат `walk-through=`.
**604 записи**, 554 класса, 44/55 миксинов (новых нет). 35/35 гейтов, `verify_jar --base 403` PASS
(3 новых члена игры в `CONFIRMED_EXTERNAL`: `Mob#getMoveControl`, `PathNavigation#getTargetPos`,
`Path#getTarget`). Патч `tools/patch_build404.py`, классы из `build303-src/out404`.
**В игре не подтверждено.**


`settledlands-0.8.0-build403-doors-on-path.jar` — **NPC проходят через двери: и через закрытые (сами
открывают), и через открытые**. Отчёт: «не только не проходят через закрытые двери, они не проходят даже
через открытые». Три причины, все в проходе дверей 368-й:
* **поиск пути не знал, что проводник открывает двери.** В 1.21 признак живёт на `NodeEvaluator`
  (`PathNavigation#getNodeEvaluator().setCanOpenDoors(true)`), а не на мобе — миксин 368-й
  (`GuideNpcDoor368Mixin#canOpenDoors`) добавляет метод, который никто не вызывает, поэтому все пути
  строились «двери = стены». Теперь `NpcDoors368#allowDoorNodes` ставит признак оценщику узлов
  каждый проход;
* **проход требовал движения** (`moved < 0.05 -> continue`): проводник у закрытой двери не двигается —
  единственный моб, ради которого проход писался, не осматривался никогда. Условие убрано, `PERIOD` 10→5,
  радиус для «упёршегося» — 3 блока;
* **путь, построенный при закрытой двери, заканчивается у двери** — теперь `recomputePath()` после
  открытия (`STUCK_TICKS = 20`).

Плюс: двери открываются **на пути** (`path.getNodePos` следующие 4 узла, `#openOnPath`), `DoorGuard371`
не закрывает дверь, если `NpcDoors368.guideNear(…, GUIDE_SAFE = 4.0)`, `CLOSE_AFTER` 100→200.
Новых миксинов нет. **603 записи**, 553 класса, 44 общих / 55 клиентских миксинов.
35/35 гейтов, `check_mixin_class` OK, `verify_jar --base 402` PASS (0 новых внешних обращений к игре —
восемь новых членов игры внесены в `CONFIRMED_EXTERNAL` с обоснованием по javadoc NeoForge 1.21 и Yarn 1.21).
sha256 `d659d5e62663c41ed0c095baa83af55d9a7d4caf833979462dd858236f4cd140`, 3 644 180 B.
Патч `tools/patch_build403.py` (**база 402**), классы из `build303-src/out403`.
`door_lock_check` расширен: требует `setCanOpenDoors`/`getNodeEvaluator`/`getPath`/`getNodePos`/
`recomputePath` в `NpcDoors368` и `guideNear` в стороже.
**В игре не подтверждено.**


`settledlands-0.8.0-build367-zone-default.jar` — **колесо меню в штатную сторону + зона у нод с
радиусом выключена по умолчанию**.
* Прокрутка: направление вынесено в `BlockMenu363.wheelStep(scrollY)` = `scrollY > 0 ? -1 : +1`
  (штатная конвенция: экран передаёт колесо как `scrollY > 0 ? -1 : +1`). В 366 было наоборот, из-за
  этого полоса «читалась» инвертированной. Клик по полосе идёт через `scroll(int)`.
* Дефолт зоны: `timerRepeat` — штатное поле ноды с дефолтом **true**, поэтому каждая новая
  CHECK/RADIUS появлялась с кольцом. Правило — `RadiusPass354.hideByDefault(node)` (только
  `isRadiusNode`), применяет новый **общий** миксин `RadiusDefault367Mixin` на
  `NpcDialogueGraph#add(III)Ldev/settledlands/npc/NpcDialogueGraph$Node;` (RETURN) — покрывает граф
  NPC, граф блока и вставку. Сохранённая включённой нода кольцо сохраняет (дефолт, не стирание).
  `load` не трогали: отсутствие ключа в старом файле по-прежнему даёт `true`.
**544 записи**, 496 классов, 39 общих / 42 клиентских миксина, 20 гейтов + `screen_smoke` (SKIP).
`radius_zone_check` теперь требует правило дефолта и гоняет `tools/harness/RadiusDefault367Probe.java`
(`ZONE check=false radius=false timer=true line=true`, `SAVED check=true`,
`WHEEL up=-1 down=1 none=0`); `block_trigger_check` требует `wheelStep`.
`verify_jar --base 366` PASS (0 удалённых членов), `validate_jar` OK.
sha256 `86e4f48563340e51c4553aebac7684602df53884bb97961e2aa7863fd49975b1`, 3 492 061 B.
Патч `tools/patch_build367.py` (**база 366**), классы из `build303-src/out367`.
**В игре не подтверждено.**

## Предыдущая сборка (366, заменена 367)

`settledlands-0.8.0-build366-menu-fix.jar` — панель меню снова штатная (5 строк, цвета, полоса,
клик по любой строке). В ней осталось инвертированное колесо — исправлено в 367.
**543 записи**, 495 классов, sha256 `375c7ead80cb000cc8aaee929f9a10ea8fc86f63723f0d1ad34c2d81750192c3`.

## Предыдущая сборка (365, заменена 366)

`settledlands-0.8.0-build365-block-events.jar` — пять новых нод блока (Кнопка, Время, Таймер,
Катсцена, Цель) и их запуск. Панель меню была 266 px и с дефектами — исправлено в 366.
**543 записи**, 495 классов, sha256 `6462e32acee20357883c2c9569eb4716e0bd7ae5722d4613bda71ef729d89f58`.

## Предыдущая сборка (364, заменена 365)

`settledlands-0.8.0-build364-block-menu.jar` — меню ПКМ на графе блока давало только доступные ноды
(Реплика, Триггер, Условие If, Переменная, В радиусе, Случайность). `BlockMenu363` +
`BlockMenu363Mixin`, замена `NpcBlockGraph351Mixin`. **542 записи**, 494 класса, sha256
`08fdaa76edade023d0784ee5a3ffb8323eeb65f7178fad443da34ce8d71d20b0`.

## Предыдущая сборка (363, заменена 364)

`settledlands-0.8.0-build363-name-colour-and-delete.jar` — **фикс двух дефектов 362**.
(1) **Цвет имени не менялся.** Строки инспектора карточки «ИМЯ» (359) писали в ноду напрямую
(`BlockName359.setColor/setName`) и не звали `NpcDialogueGraphScreen#propertyChange`. А `onClose()`
сохраняет блок-редактор **только при `dirty`** (ветка `parent instanceof NpcConfigScreen` — сохранение
всегда, иначе `if (dirty) save()`). Флаг не ставился → при закрытии окна граф блока не сохранялся, и
цвет (как и имя) терялся. В 363 обе строки идут через `propertyChange` (точка отмены + `changed()`).
В лог при выборе цвета: `[settledlands] block name colour: index N`.
(2) **Пропала кнопка «Удалить граф» у блока** (355). Она строится в x=66, y=10, 104×20 — то есть в
коробке полосы вкладок, а 362 убирал вкладки ещё и «по коробке» (`y==10, h==20, x>=66`) → кнопка
уехала вместе с ними. В 363 есть `WidgetPark361.keep()/isKept()`: кнопка помечает себя, удаление
вкладок пропускает помеченное (правила «по подписи» и «по коробке» остались).
**540 записей**, 492 класса, заменено 4 класса, 21 гейт зелёный, `verify_jar --base 362` PASS,
`validate_jar` OK. Патч `tools/patch_build363.py` (**база 362**), классы из `build303-src/out363`.
В игре **не подтверждено**.
**Доставка 363:** jar `1qEgOmyOlUT2UGq-sgd2NkHmh0DeC-xA2`, zip `1xN3cHMTJgS0_VPvOmnrSiyfD7L29V5BZ`,
оба `make_public(reader, allow_file_discovery=true)` + `list_permissions` = `anyone/reader`; коммит
`2c451cf` запушен в `arena/5618404a-mod`.
**Урок 363:** прежде чем писать в чужую модель, найди, как это делает штатная строка — здесь это
`propertyChange`: undo + dirty. И «правило по геометрии» бьёт по всему, что стоит в той же коробке;
виджет, который ставит сам мод, надо помечать.

## Предыдущая сборка (362, заменена 363)

`settledlands-0.8.0-build362-block-tabs-fix.jar` — **фикс двух дефектов 361**. Причина обоих найдена в
байткоде, а не угадана:
(1) **граф блока перестал отвечать на мышь.** У экрана графа нет своего обработчика мыши:
`NpcDialogueGraphScreen.mouseClicked(DDI)Z` — одна строка `invokestatic
NpcAutoAiGraphUi255.mouseClicked(this, x, y, b)`, а тот при промахе по карточке ИИ передаёт клик в
`NpcStatsGraphUi.mouseClicked(...)` и дальше в редактор. В 361 я отменил этот метод и вернул `false` —
то есть выбросил единственный вход в цепочку кликов. В 362 клик **передаётся дальше**
(`cir.setReturnValue(NpcStatsGraphUi.mouseClicked(screen, x, y, button))`), панель ИИ по-прежнему не
рисуется (`draw` отменён).
(2) **вкладки остались в третий раз.** 360 просил `visible=false`, 361 — `visible=false` + перенос за
окно; оба раза игра вернулась с полосой. В 362 кнопки вкладок **удаляются из списков экрана**
(`renderables` и `children`) в `@Inject(RETURN)` на `widgets()`, плюс скрытие и перенос как страховка.
Узнаются двумя способами: по подписи (переводы `tab_main/tab_walk/tab_anim`) и по коробке (y=10, h=20,
x>=66; undo/redo — та же коробка при x=10/38). В лог пишется
`[settledlands] block graph: N tab button(s) taken off the screen of M widgets` — если вкладки останутся,
лог скажет, сработал ли хук и сколько кнопок он узнал.
**540 записей**, 492 класса, 21 гейт зелёный (`block_name_check` теперь запрещает возврат дефекта:
клик ИИ обязан идти в `NpcStatsGraphUi.mouseClicked`, хук вкладок обязан звать
`WidgetPark361.children/label/park`, а `park` — трогать `renderables` и `children`),
`verify_jar --base 361` PASS, `validate_jar` OK. Патч `tools/patch_build362.py` (**база 361**), классы
из `build303-src/out362`. В игре **не подтверждено**.
**Доставка 362 (Drive снова включён 2026-11-04):** jar `13U34J8UT1wSrpEnLBsvdUj7toZ3YSSFu`, zip
`19UrXja_ObbPPvMft_1wj0Ap9K7TjwuKv`, оба `make_public(reader, allow_file_discovery=true)` +
`list_permissions` = `anyone/reader`. Форма отчёта закреплена в `AI-GUIDE-SettledLands.md` §10: краткое
описание сверху → детали → **одна голая ссылка на прямое скачивание последней строкой, после неё ничего**.
Репозиторий `BORGERone/mod` публичный, поэтому резервный прямой путь —
`https://github.com/BORGERone/mod/raw/<sha>/<файл>`.
**Урок 362:** прежде чем отменять чужой метод, посмотри, кто его вызывает и что он передаёт дальше —
`mouseClicked` экрана был тонкой делегацией в цепочку UI-классов, и отмена «одной карточки» выключила
весь холст. И второе: «спрятать» ≠ «убрать» — если виджет не подчиняется дважды, его надо вынимать из
списков экрана.

## Предыдущая сборка (361, заменена 362)


`settledlands-0.8.0-build361-block-graph-cleanup.jar` — **дочищен редактор блока после приёмки 360 в
игре**. 360 вернулась с четырьмя замечаниями: (1) карточка «Автономный ИИ» всё ещё на холсте блока,
(2) вкладки «Главный | Распорядок | Общее» всё ещё в полосе, (3) имя блока показывается вместе с
координатами, (4) тумблер зоны у ноды «Цель» не такой, как у «В радиусе». Плюс в инспекторе карточки
«ИМЯ» висели штатные строки «Реплики» (редактор не знает тип 18 и проваливается в ветку реплики).
Что сделано в 361:
`BlockNoAi359Mixin` теперь отменяет **отрисовку** (`draw` → cancel) и **клик** (`mouseClicked` → false)
карточки ИИ, а не только `ensure`: у состояния есть вторая дверь — снимок профиля
(`onSnapshotApplied`), поэтому отмена одной `ensure` в 360 не сработала;
`BlockNoTabs360Mixin` **увозит** кнопку вкладки за пределы окна (`WidgetPark361.park` → `visible=false`,
`active=false`, `setPosition/setY/setHeight` через reflection) — одного `visible=false` в 360 не хватило;
`BlockNameInspector359Mixin` паркует штатные строки узла и чистит `labels`, оставляя только «Имя в
диалогах» и «Цвет имени»; `BlockTalk358#say` больше **не подставляет координаты** (имя = только имя,
без имени — пустая строка, подпись пустая); `BlockNameCard359` не рисует заглушку вместо пустого
имени; `NpcRadiusZoneUi320` (квадратик «Зона» на холсте, сборка 320) **отменён** — у «Цели» и «В
радиусе» теперь один и тот же тумблер, строка инспектора сборки 355. Новый класс `WidgetPark361`.
**540 записей**, 492 класса, 80 миксин-классов (38/41), `verify_jar --base 360` PASS (1394 внешние
ссылки, **0 новых**), `validate_jar` OK, **21 гейт зелёный** (`block_name_check` и `radius_zone_check`
обновлены под новое поведение: первый требует парк вкладок/ИИ/no-coords, второй запрещает квадратику
вернуться). Патч `tools/patch_build361.py` (**база — 360**), классы из `build303-src/out361`.
В игре **не подтверждено**. **Урок 361:** «поставил флаг — значит сделано» не работает: если в игре
эффекта нет, надо бить по самой отрисовке (или увозить виджет из окна), а не по состоянию, которое
кто-то другой может вернуть.
Выложено: коммит `5fccfd3` + push; Drive jar `1BeFwj37CgdtmD6z2EAxqGoJKbCcs6Wsa`,
zip `1kkSCGiX2dOzUYcaXfMMEJMn19otSXzMH` (папка `0AB-al7DFuWhEUk9PVA`), оба **сразу сделаны
публичными** (`anyone/reader`, `allow_file_discovery=true`) — проверено через `list_permissions`.
sha256: jar `762cefbde8f01dab865217ecbd6b3450d424b6caebd8b816b336996453f547da`,
zip `40c72af0c8208d919d6f0dc6e90f32d8ce8672034393f3c3bb20aa8b30e9f764`.

## Предыдущая сборка (360, заменена 361)


`settledlands-0.8.0-build360-block-name-fix.jar` — **имя блока + без вкладок у графа блока; замена
отозванной 359**. 359 валила клиент при открытии **любого** графа: `@Shadow` на
`Screen#addRenderableWidget` — Mixin ищет тень **только в самом классе-цели**, а метод унаследован, и
загрузка `NpcDialogueGraphScreen` падала с `InvalidMixinException ... was not located in the target
class`. Теперь виджет регистрируется **вызовом** (стуб `Screen` объявляет стёртую сигнатуру
`(GuiEventListener)GuiEventListener`, вызов совпадает с тем, что делает штатный `textBox`), тени нет.
Гейт `check_mixin_class` снова строгий и на jar 359 **FAIL** с тем же текстом, что в логе игры, на 360 —
OK. Новое: у графа блока **нет вкладок** (`BlockNoTabs360Mixin`: `@Redirect` на `button(...)` в
`widgets()`, для блочного id кнопки вкладок `gui.settledlands.graph.tab_*` получают `visible=false,
active=false`; `switchTab` для блока по-прежнему запрещён в 351). Остальное как в 359: карточка «ИМЯ»
(нода 18, `Node#text` + `Node#varValue`, цвета `GuideDialogueScreen.NAME_COLORS`), нет карточек
«Характеристики» и «Автономного ИИ» на графе блока, окно диалога говорит именем и цветом блока.
**539 записей**, 491 класс, 80 миксин-классов (38/41), `verify_jar` PASS (1394 внешние ссылки, **0
новых** против 358), `jvm_verify` OK, `check_mixin_class` OK, `block_name_check` OK, 19 остальных
гейтов зелёные, `screen_smoke` SKIP. Патч `tools/patch_build360.py` (**база — 358**, 359 не
выпускается), классы из `build303-src/out360`. В игре **не подтверждено**.
Выложено: коммит `7e0b4e8` + push; Drive jar `1bh3JtiUVbxNC_G_Vw7malNJZxyizQVDw`,
zip `1w_rEPDnSGSsEWENUUbOU2ySzgwuv-93i` (папка `0AB-al7DFuWhEUk9PVA`).
**Ссылки были 403** — файлы заливались без публичного доступа (приватно видел только владелец).
Исправлено: `make_public(role=reader, allow_file_discovery=true)` на 360 jar+zip, а также на 358 и 359
(их тоже открыл). **Правило на будущее: сразу после `upload_file` делать файл публичным** — иначе
`https://drive.google.com/uc?export=download&id=…` отдаёт 403. Папку `make_public` не даёт (политика
Google Workspace: «This file is never writable»), поэтому публичность — **на каждом файле отдельно**.

**Урок 360 (главный):** `@Shadow` работает **только** для членов, объявленных в самом классе-цели.
Наследуемый от Minecraft член надо **вызывать** — компилятор найдёт его через стуб суперкласса, а
сайт вызова окажется внутри цели, где `protected` доступ разрешён (подкласс). Мой «исключение в
гейте» был самообманом: разрешать в проверке то, чего Mixin не умеет, — это не проверка. Правило:
**гейт не ослаблять под свою же реализацию**, а менять реализацию. Теперь на 359 гейт падает тем же
текстом, что игра.

**Урок 360 (второй):** Java **не** склеивает соседние строковые литералы (как Python) — в аннотации
`@At(target = "...")` нужен `+`; ecj сказал об этом прямо, но в отчёте об ошибке это выглядело как
«`<` expected».

## Сборка 359 (ОТОЗВАНА — вылет при открытии любого графа)

`settledlands-0.8.0-build359-block-name.jar` — **у блока есть имя, и этим именем он говорит в диалоге**.
На канвасе блока вместо карточек «Характеристики» и «Автономный ИИ» — одна своя карточка **«ИМЯ»**
(поле имени + выпадающий список цвета), устроенная как карточка характеристик у тела: есть сразу при
открытии блока, правится в инспекторе, из палитры не добавляется. Данные — в уже сохраняемых полях
ноды: `Node#text` (имя, кап 32) и `Node#varValue` (индекс цвета 0..3, значения как
`GuideDialogueScreen.NAME_COLORS`: `FFFFFFFF`, `FFFFE9A8`, `FFFFD24A`, `FFBFBFBF`). Нода типа **18**
(`NpcStatsGraph.TYPE = 16`, 17 свободен, 16 — карточка характеристик). Карточку ставит
`NpcStatsGraphUi.ensure`, перехваченный `BlockNoStats359Mixin`: для блочного id вызов отменяется,
старая карточка характеристик выбрасывается (`BlockName359.dropType`), создаётся имя
(`BlockName359.ensure`), экран получает `changed()`. Панель ИИ (`NpcAutoAiGraphUi255.ensure`) для
блока не вызывается `BlockNoAi359Mixin` — это не нода, а боковое состояние `NpcAutoAi255`, «убрать» =
не синхронизировать. Отрисовка своя (`BlockNameDraw359Mixin` → `client/BlockNameCard359`): стоковый
`drawNode` для незнакомого типа подписал бы карточку «Случайность» и не нарисовал текст; рамка
совпадает с `NODE_W`×`NODE_H`, поэтому выделение и перетаскивание стоковые. Инспектор —
`BlockNameInspector359Mixin` (`widgets()` RETURN, `buildingInspector`, `sidebarHeight += 66`): поле
имени (`textBox`) и `NpcUi.dropdown` на четыре цвета. `BlockNameOne359Mixin` — одно имя на граф
(`NpcStatsGraph.add`). Сохранение блока (`BlockTrigger351.save`) выбрасывает карточку характеристик,
обход цепочки молча переступает ноду имени (`BlockName359.isName`). Окно диалога
(`BlockTalk358.say`) говорит именем блока и его цветом (`profile.nameColor`), координаты уходят в
подпись; безымянный блок говорит координатами, как в 358.
**538 записей**, 490 классов, 79 миксин-классов (38/40), `verify_jar` PASS (1394 внешние ссылки,
**0 новых** против 358, 0 потерянных членов, 0 висячих), `jvm_verify` OK, `check_mixin_class` OK,
новый гейт `block_name_check` OK (одна карточка только у блока, своя отрисовка, снятие карточек
характеристик и ИИ, строки инспектора, выбрасывание старой карточки при сохранении, молчаливый
переступ ноды имени, окно читает имя и цвет, прогон `tools/harness/BlockNameProbe.java`:
`ENSURE added=true again=false cards=1 name='' color=0 where=left`,
`SET name='Forge' color=2 rgb=-11702`, `CLAMP high=3 low=0`, `LENGTH kept=32 of 40`,
`ROUND name='Forge' color=2 nodes=2`, `DROP stats=true dropped=1 left=0 name=true`,
`PLAIN find=null name='' color=0`), остальные 19 гейтов зелёные, `screen_smoke` SKIP. Патч
`tools/patch_build359.py`, классы из `build303-src/out359`. В игре **не подтверждено**.
Выложено: коммит `492aa4b` + push; Drive jar `1HXrXbICNbR1igONSrOs4Q51fHYWvq7cL`,
zip `1b8DluDBwT-pAMEAS9ATZmn8t98p30UNH` (папка `0AB-al7DFuWhEUk9PVA`).

**Урок 359:** `static final int` **инлайнится** — проверка «в байткоде есть `BlockName359.TYPE`» молча
врала, пока сравнение не стало вызовом `BlockName359.isName(node)`. Гейт, который ищет константу,
ищет `bipush`/`sipush`, а не имя. И второе: `@Shadow` на метод, объявленный не в цели, а в её
суперклассе Minecraft (`Screen#addRenderableWidget`), проверять нечем — поэтому в `check_mixin_class`
появилось исключение `INHERITED_MEMBERS` с указанием **живого вызова** в базовом jar, а
`block_name_check` этот вызов перечитывает.

**Инфраструктура 359:** в этой сессии не было Java — `pip install --break-system-packages jdk4py`
ставит её в `/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime` (все гейты ищут именно там).
Компиляция: `-cp build303-src/api:api5:api4:api3:stubs-out:api2:<предыдущий jar>` — **`api` первым**:
в `api` лежат богатые стубы (`CompoundTag#isEmpty`, `Player#displayClientMessage`), в `api2` их нет, и
с порядком «api2 раньше api» `BlockTrigger351` не собирается.


`settledlands-0.8.0-build358-block-talk.jar` — **«Реплика» блока открывает настоящее окно диалога, и
камера смотрит на блок**. Решение 357 («окна у блока нет, чат и есть голос») **отменено
пользователем**: окно — это не тело. `NpcNet.DlgView` несёт id, спикера, строку и стиль, а сущность
экран ищет только чтобы повернуть камеру (`getEntity(id)`), и этот путь null-safe — поэтому блок
получает штатное окно под **своим block-id** (`BlockTrigger351.isBlockEditorId`), а прицел делает
клиент: сервер посылает координаты блока **одним `CfgView` впереди окна** (`BlockTalk358#marker`,
теги `BlockTalkX/Y/Z`), `NpcBlockOpen351Mixin` отличает разговор от редактора, `BlockTalkClient358#aim`
считает углы `BlockTalk358#aimAngles` (та же арифметика, что `GuideDialogueScreen#face`) к центру блока
и ставит и текущий и предыдущий угол. `NpcProfile.blockMouseLook = true` → обзор мышью заблокирован,
вид стоит на блоке; `blockMovement = true`. Стиль берётся **у самого `GuideDialogue#style`**
(рефлексией, один раз, фолбэк в чат) — 26 полей `DlgStyle` руками не собираются, урок 342 про
`@Invoker` учтён. Клик/E/Enter → `DlgPick(id,0)` → `BlockTalkPick358Mixin` на `GuideDialogue#pick`
закрывает окно (`DlgEnd`) и продолжает цепочку с `node.next` (`BlockTrigger351#resume`); Esc →
`DlgClose` → `BlockTalkStop358Mixin` на `GuideDialogue#stop` обрывает цепочку. Окно забывается через
2 минуты (кап 64), чтобы поздний клик не пошёл по брошенной цепочке. Чат 357 остался **только
фолбэком** (нет игрока, пустой текст, стиль недоступен). `RadiusPass354` передаёт цепочке
`BlockTalk358.Where` — узел радиуса знает свой блок, прицел работает и там.
**531 запись**, 74 миксин-класса (37/36), 483 класса, `verify_jar` PASS (0 новых внешних ссылок против
357, 0 висячих), `jvm_verify` OK, `check_mixin_class` OK (1392 внешние ссылки, 0 новых), новый гейт
`block_talk_check` OK (пакет прицела до окна, `setYRot/setXRot/yRotO/xRotO`, чистит Esc, клик ведёт на
ноду после реплики, стиль рефлексией; проба `tools/harness/BlockTalkProbe.java`: `AIM -90.00 26.57 |
90.00 36.87 | 0.00 -9.46`, `MARKER round=same plain=null`, `NEXT line=ok end=-1 missing=-1 null=-1`),
`block_line_check` OK (обновлён: окно первым, чат после него), `block_trigger_check` OK,
`radius_flag_check` OK, остальные гейты зелёные, `screen_smoke` SKIP (нет `NpcWorkspace306`). Патч `tools/patch_build358.py`, классы из
`build303-src/out358`. В игре **не подтверждено**.
Выложено: коммит `f460cf2` + push; Drive jar `1a74tPHz1h174ecE_axsrHq66-MIkfeDT`,
zip `1s3YyLAqp4pNxT_M8fBKvGJ5xK9Btnjb4` (папка `0AB-al7DFuWhEUk9PVA`).

**Урок 358:** перед тем как писать обходной путь («тела нет — значит окна нет»), проверить, что
окну действительно нужно тело. Здесь нужен был только прицел, и он оказался отдельной задачей на
один пакет. И второе: **дескрипторы новых обращений к MC снимать с байткода самого мода** —
`GuideDialogueScreen#face` дал точные `Player.setYRot(F)V`, `Player.yRotO:F`; поэтому в стубах
(`Entity`, `Minecraft`) появились именно они, и `check_mixin_class` показал 0 новых внешних ссылок.

**Инфраструктура 358:** в стубах дописаны `Entity#getEyeY/setYRot/setXRot/yRotO/xRotO` и поля
`Minecraft#player`/`#keyboardHandler`. Второе — заодно починка: `api5` лежит в classpath первым, а его
`Minecraft.class` был собран из старого `api5-src` без полей, поэтому сборка «не видела» `player`.

Предыдущая — `settledlands-0.8.0-build357-block-line.jar` — **нода «Реплика» у блока, говорившая в
чат** (решение отменено в 358: теперь окно диалога + прицел, чат только фолбэк).
Предыдущая — `settledlands-0.8.0-build356-radius-flag.jar` — **флаг «Видимость радиуса» теперь сохраняется**. Причина
жалобы «тумблер сразу возвращается на включено» найдена и она не в клике: `NpcDialogueGraph#save`
пишет ключ `TimerRepeat` **только для ноды таймера** (`type == 8`), а `load` читает его **для любой ноды**
и при отсутствии ключа подставляет `true`. То есть у ноды «В радиусе» (и «Цель») флаг вообще не
сохранялся: каждое сохранение его выбрасывало, каждое чтение возвращало «да» — сервер снова рисовал
кольцо, а снимок (`GuideNpc.applyConfig` → `load` → `sendConfigView` → `applySnapshot` → `load`) клал
«включено» обратно в строку. Фикс: `RadiusFlagSave356Mixin` (**общий**, `@Inject` в `save()` на `RETURN`)
в конце сохранения кладёт `TimerRepeat` в тег каждой ноды `CHECK`/`RADIUS`; читатель не тронут — `load`
уже умеет читать ключ у любой ноды. Ветка таймера (`type == 8`) не затронута. **525 записей**, 72
миксин-класса (35 общих / 36 клиентских), 477 классов, `verify_jar` PASS (0 новых внешних ссылок против
355, 0 висячих, 0 недостающих своих классов), `jvm_verify` OK, новый гейт `radius_flag_check` OK
(общий миксин, врезка в `save()` на RETURN, фильтр по `iconst_5`/`bipush 15`, дырка в `save`
подтверждена, чтение в `load` без охраны по типу + круговой тест NBT), `block_trigger_check` OK,
остальные 16 проверок зелёные. Патч `tools/patch_build356.py`, класс из `build303-src/out356`,
проба `tools/harness/RadiusFlagProbe.java`. В игре **не подтверждено**.
Выложено: Drive jar `1dEZLl484QmHaTdz6rlEFvvZBZX3EoV1h`, zip `1NhEvpEtXTdZ0JNZRg6Xxq5Rlbl_x9_hj`
(папка `0AB-al7DFuWhEUk9PVA`).

**Урок 356:** `save`/`load` проверять **парой**: флаг, который пишется только для одного типа ноды, для
другого молча теряется, а `load` подставляет значение по умолчанию (`true`) — и выглядит это как
«кнопка не работает». Проба на реальных классах (`BASE → DEFAULT → MIXIN → LOAD → ON`) показывает это
дословно и остаётся гейтом.

Предыдущая — `settledlands-0.8.0-build355-radius-toggle.jar` — **тумблер радиуса и удаление графа блока**. Причина
«тумблер не выключался»: кольцо рисует **сервер** по сохранённому графу, а строка «Видимость» меняла
флаг только в редакторе до нажатия «Сохранить» — состояния расходились. Теперь
`NpcRadiusRow355Mixin` (клиент) после переключения **вызывает `save()`**, и сервер видит флаг сразу.
Второе: `NpcBlockDelete355Mixin` (клиент) — кнопка «Удалить граф» в верхней панели блока (66,10,104×20,
рядом с undo/redo), в два клика с окном 5 с (`settledlands$armedAt`), чистит ноды, ставит `entry = -1`,
`changed()` + `save()`; на сервере `BlockTrigger351.save` теперь считает **пустой граф = графа нет** и
удаляет запись (`store.put(key, null)`), в чат — `message.settledlands.block.deleted`. Кнопка неактивна,
если граф уже пуст. **524 записи**, 71 миксин (34 общих / 36 клиентских), 476 классов, `verify_jar` PASS
(0 новых внешних ссылок против 354, 0 висячих, 0 недостающих своих классов), `jvm_verify` OK,
`block_trigger_check` OK (сохраняет по клику, чистит+сбрасывает стартовую+сохраняет, пустой граф убирает
запись), остальные 15 проверок зелёные. Патч `tools/patch_build355.py`, классы из
`build303-src/out355`. В игре **не подтверждено**.
Выложено: коммит `f0b7535` + push; Drive jar `1plAx0G0pOkx1LYJLNc3Ss1AwWnp92s2O`,
zip `1vGpCImoMURBYgnOu_SzkQh0RzxM8g0T_` (папка `0AB-al7DFuWhEUk9PVA`).

**Урок 355:** клиентский флаг, который читает сервер, без `save()` не меняет ничего — строка
инспектора должна сохранять граф сама, иначе «выключено» в редакторе и кольцо в мире расходятся.

Открыто/осознанно не сделано (сказано пользователю): у блока нет нод времени/таймера/урона, нет
квестов/анимаций/движения/катсцен (нужно тело НПС), вкладки расписания/общего не открываются.
**Реплика у блока сделана в 357** (чат, `%player%`/`%relation%`; окна диалога нет — у блока нет тела).

Предыдущая — `settledlands-0.8.0-build351-block-trigger.jar` — **триггер блоков**: блок носит граф.
ПКМ жезлом настройки НПС по блоку открывает штатный редактор графа (id `-2 000 100 000 … -2 000 199 999`),
сохранение идёт тем же `CfgSave` и пишется в данные мира (SavedData `settledlands_block_triggers`, ключ
`измерение@x,y,z`, лимит 4096). ПКМ пустой рукой запускает цепочку с `graph.entry`; нет начальной ноды —
ничего не происходит. Палитра: Триггер, Условие If, Переменная, Случайность (в 352/353 добавлена пятая — «В радиусе»).
519 записей, 68 миксинов (33/34), `verify_jar` PASS, 0 новых внешних ссылок против 350. Коммит
`4df8dce`, Drive jar `10QMG0mYdXVAsM5W42x5USyeAVR-696EV`, zip `1E-1ceIpw-D7v5WT6t5u0KQizEFVun2AP`.

Предыдущая — `settledlands-0.8.0-build350-armor-off.jar` — **броня на теле отменена автором**:

`settledlands-0.8.0-build350-armor-off.jar` — состав 347 целиком (512 записей, 65 миксинов), ни одного
класса брони и ни одного armour-миксина. Отличается от 347 только `neoforge.mods.toml` и
`settledlands-patch-info.txt`. Jar-ы 348/349 удалены из дерева и убраны с Drive в корзину.

Предыдущая — `settledlands-0.8.0-build347-common-damage.jar` — **нода урона снова работает в личной вкладке**.
Причина (найдена трассировкой 346 по логу пользователя, `latest-5.log`): все три вызова
`GuideDialogue.handleNpcDamaged` несли **общий** граф (`nodes=2 enabled=false entry=-1 damage=0`), а
личная вкладка (`nodes=5 damage=1`, нода #4, все условия проходят) не читалась вовсе. Виноват хук 334:
он вешался на этот **единственный** метод, через который проходят все три штатные цепочки (личная,
прогулка, анимация), и отменял его — один недописанный общий граф выключал ноды урона везде и у всех.
Фикс: `NpcCommonGraphDamageMixin334` удалён из джарника и из `mixins`, вместо него
`NpcCommonGraphDamageMixin347` на `GuideNpc.hurt` (HEAD): цепочка общего графа **добавляется** к трём
штатным один раз за удар, ничего не отменяется. Трасса 346 осталась (кап 80 строк). Патч
`tools/patch_build347.py`; `tools/common_graph_check.py` обновлён (хук урона обязан быть на `hurt` и НЕ
может вешать `handleNpcDamaged`/cancel). 512 записей, 65 миксинов. В игре **не подтверждено**.

**Важно про порядок:** общий граф прогоняется на HEAD `hurt`, т.е. до штатных цепочек. Если одна и та
же нода с одним id есть и в «Общем», и в личной вкладке, первая займёт слот отката в общей карте
`damageCooldowns` и вторая в течение откатa не сработает. Порядок выбран осознанно: при падении чужого
мода внутри `LivingEntity.hurt` (см. HackersAndSlashers) RETURN-хук не выполнился бы вообще.

**Из лога 346 (`latest-5.log`):** удар доходит до `GuideNpc.hurt` дважды на клиенте и сервере (клиент —
`LocalPlayer`, сервер — `ServerPlayer`), `own=enabled=true entry=0 nodes=5 damage=1`;
`node #4 type=6 min=1 filter=0(any) if-in=0 -> gate-ok`. Плюс прежний факт: HackersAndSlashers
2.0-beta2.5 кидает NPE `PlayerHelper.lastComboStep()` в `LivingIncomingDamageEvent` внутри
`LivingEntity.hurt` — урон по НПС не начисляется (чужой мод).

**Урок для будущих сборок:** интерфейсный миксин (`public interface ...`) допустим только для цели,
которая сама интерфейс. Для доступа к приватным членам чужого класса — резолв по имени (MethodHandle,
кэшируется) или `@Shadow` в класс-миксине, но `@Shadow` валидируется при применении и падает фатально,
если метода нет; поэтому выбран резолв с откатом. Значения из UI всегда сверять с тем порядком, который
читает рантайм (этот баг — ровно рассинхрон нумерации).

Предыдущая — `settledlands-0.8.0-build341-ivy-professions.jar` — Build 341 (**подтверждена в игре: всё
работает**): перенос позы FA/Touch'n Motion доходит и до профессионной риги. 340 была пустой:
`PlayerMotionBridge` УЖЕ зовёт `ModelSpace#before` (см. `available()` → `modelBefore`), слушатель 340
звал её второй раз и удалён из джарника. Настоящая причина: `PlayerMotionBridge.isIvy` (и
`NpcMotionBridge256.isIvyRig`) принимает ровно `ivy` и `ivy_oleander`, а профессионная модель
`ivy_oleander_professions` (её используют все NPC с профессией) — нет; `PlayerMotionBridge.render`
для неё уходит в `settledlands$renderActorOriginal`, то есть тело рисуется своими клипами без переноса
позы с модели игрока → жест TnM считается, но переносить его некуда. Решение: миксин
`IvyRigGateMixin341` (client) на `isIvy` в RETURN расширяет ответ по префиксу `ivy`
(`IvyRig341.isIvyName`); валидация риги (`validRig`/`eligible`) не тронута; ограниченный лог
`[SettledLands/IvyRig341] actor rig '<name> ... accepted|inherited` (≤4 строк). Гейт
`tools/touch_n_motion_check.py` переписан, харнесс `IvyRigSmokeTest` (9 ok). Патч
`tools/patch_build341.py`, база 340, 500 записей, 59 миксинов.

Предыдущая — `settledlands-0.8.0-build340-tnm-possession.jar` — Build 340 (**пустая**, отозвана по
сути: дублировала вызов, который мост уже делает).

Предыдущая — `settledlands-0.8.0-build339-punchy-arm-anchor.jar` — Build 339 (подтверждена в игре:
«руки от первого лица теперь отображаются как надо»).
`settledlands-0.8.0-build340-tnm-possession.jar` — Build 340: **анимации взаимодействия Touch'n
Motion на теле, в которое ты вселился**. Причина: TnM считает жест в HEAD
`LivingEntityRenderer.render` и складывает в своё состояние (поза уходит на модель игрока при копии
позы EMF); `PlayerBodyRenderer` отменяет отрисовку игрока в `RenderPlayerEvent.Pre`, который
срабатывает **раньше** этого HEAD → жест для вселённого игрока не считался никогда, и ретаргет
FA/TnM нёс на кости NPC только базовую анимацию. Решение: новый клиентский слушатель
`TouchNMotionPose340` (`@EventBusSubscriber`, `@SubscribeEvent(priority = HIGHEST)`), который для
вселённого игрока зовёт **собственный** `strm.touchnmotion.ModelSpace#before(LivingEntity, PoseStack,
float, Consumer)` с тем же PoseStack/partialTick; дальше работает существующий мост (`PlayerMotionBridge`
/ `NpcMotionBridge256`, там же `tnmReady`/`hasTnmHook`). Method резолвится один раз, потребитель —
константа (0 аллокаций в кадре), всё в try/catch, warning один; для не-вселённых — no-op. Гейт
`tools/touch_n_motion_check.py`, харнесс `tools/harness/TouchNMotionPoseSmokeTest.java` (6 ok). Патч
`tools/patch_build340.py`, база 339, 499 записей. В игре **не подтверждено**.

Предыдущая — `settledlands-0.8.0-build339-punchy-arm-anchor.jar` — Build 339 (подтверждена в игре:
«руки от первого лица теперь отображаются как надо»): рука NPC рисуется после
`ModelPart.translateAndRotate` от той самой руки, которую Punchy собирается рисовать, отмена своей
руки Punchy — только при реально нарисованной руке NPC.

Предыдущая — `settledlands-0.8.0-build338-punchy-arms.jar` — Build 338 (**отозвана**: в первом лице
пропадали руки целиком).
`settledlands-0.8.0-build339-punchy-arm-anchor.jar` — Build 339: **рука NPC там, где её рисует
Punchy!**. 338 оставлял игрока без рук в первом лице: рисовал руку NPC рендерером, рассчитанным на
пространство модели игрока (`renderPossessedFirstPersonModelArms`), тогда как `renderArm` отдаёт
своё пространство руки, и при этом безусловно отменял руку Punchy. 339 берёт ту руку, которую Punchy
собирается рисовать, применяет её собственный `ModelPart.translateAndRotate(poseStack)` (тот же приём,
что в принятом мосте сообщества) и рисует руку NPC вокруг этой точки — она встаёт ровно на
заменяемую руку и наследует анимацию кадра (замах, удар, «потянуться к рычагу» из Touch'n Motion).
Отмена руки Punchy только при реально отправленной руке NPC; нет кости/модели/текстуры — Punchy
рисует свою. Кости руки NPC сохраняются/возвращаются в `finally`, LOD форсируется и возвращается,
состояние костей кэшировано по модели (в кадре 0 аллокаций). Из 338 убраны push/pop first-person
контекста и переключение видимости рук модели игрока; классы 338 удалены из джарника. Гейт
`tools/punchy_bridge_check.py` переписан под 339 (ловит отсутствие `translateAndRotate`, пуш
контекста, забытые кости), харнесс `tools/harness/PunchyArm339SmokeTest.java` (12 ok). Патч
`tools/patch_build339.py`, база 338, 498 записей. В игре **не подтверждено**.

Предыдущая — `settledlands-0.8.0-build338-punchy-arms.jar` — Build 338 (**отозвана**: в первом лице
пропадали руки целиком). Первый заход в Punchy!: `@Pseudo`-миксин на
`punchy.client.render.PunchyArmRenderer.renderArm` HEAD + `renderArmArmor`, отрисовка руки NPC через
`FirstPersonPossession256.renderPossessedFirstPersonModelArms` с пушем first-person контекста.

Предыдущая — `settledlands-0.8.0-build337-self-link.jar` — Build 337 (подтверждена в игре):
**самосвязь запрещена, тумблер в инспекторе**.
`settledlands-0.8.0-build338-punchy-arms.jar` — Build 338: **руки NPC в первом лице с Punchy!**.
С установленным Punchy! руки от первого лица рисует он сам (своя копия `PlayerModel` + текстура
игрока), а ванильный проход `ItemInHandRenderer.renderHandsWithItems`, который заменяет вселение, он
не вызывает — поэтому при вселении в NPC в первом лице оставались руки игрока (Стив). Перехват по
имени класса/метода (мод закрытый): `@Pseudo`-миксин `PunchyArmBridgeMixin338` (**только client**) на
`punchy.client.render.PunchyArmRenderer.renderArm` в HEAD, `require = 0`, `remap = false`; сигнатура
обработчика ровно 9 параметров + `CallbackInfo`
(`PlayerModel, AbstractClientPlayer, HumanoidArm, PoseStack, MultiBufferSource, int, ResourceLocation,
boolean slim, float partialTick`). `PunchyArm338.replace(...)`: сначала `possessedModelName` (пусто →
выход, руку Punchy не трогаем), потом наличие кости NPC (`getPart("rightarm"|"right_arm")`), затем
`pushFirstPersonRenderContext` → `renderPossessedFirstPersonModelArms(...)` (рисует руку NPC **в позе,
которую Punchy только что проиграл**) → `popPlayerRenderContext()` в `finally`; видимость рук модели и
LOD восстанавливаются, `Throwable` ловится (отдаём руку Punchy). Отменяем свою руку Punchy на этой
стороне (`ci.cancel()`), `renderArmArmor` гасится через `ThreadLocal<HumanoidArm>` (`consumeArmor`),
чтобы рукав брони игрока не лёг поверх руки NPC. Миксинов 58 (client 29). Гейт `tools/punchy_bridge_check.py`
(точные дескрипторы обоих обработчиков, порядок вызовов, `@Pseudo`, классы игры не попали в джарник),
харнесс `tools/harness/PunchyArm338SmokeTest.java` (11 ok). Патч `tools/patch_build338.py`, база 337,
497 записей. В игре **не подтверждено**.

Предыдущая — `settledlands-0.8.0-build337-self-link.jar` — Build 337 (подтверждена в игре): **самосвязь
запрещена, тумблер в инспекторе**. `NpcSelfLinkMixin337` (common) на HEAD `NpcDialogueGraph.connect(III)Z` отказывает при
`from == to` — это единственная точка, через которую редактор делает все провода; отказ помечается
(`NpcSelfLink337.refuse()`), и `NpcSelfLinkStatusMixin337` (client TAIL `mouseReleased`) пишет в строку
состояния `gui.settledlands.graph.self_link`. Тумблер «Рандомный ответ» переехал из нарисованного
чекбокса (336, удалён из джарника вместе с миксином) в штатную строку инспектора:
`NpcRandomLineInspectorMixin337` добавляет `button(...)` в конец `widgets()` с `y` от
`sidebarHeight`, `setMessage` из `NpcRandomLineUi337`, обрезкой как у штатных строк и `sidebarHeight += 22`.
`settledlands-0.8.0-build337-self-link.jar` — Build 337: **самосвязь запрещена, тумблер в
инспекторе**. `NpcSelfLinkMixin337` (common) на HEAD `NpcDialogueGraph.connect(III)Z` отказывает при
`from == to` — это единственная точка, через которую редактор делает все провода; отказ помечается
(`NpcSelfLink337.refuse()`), и `NpcSelfLinkStatusMixin337` (client TAIL `mouseReleased`) пишет в строку
состояния `gui.settledlands.graph.self_link`. Тумблер «Рандомный ответ» переехал из нарисованного
чекбокса (336, удалён из джарника вместе с миксином) в штатную строку инспектора:
`NpcRandomLineInspectorMixin337` добавляет `button(...)` в конец `widgets()` с `y` от
`sidebarHeight`, `setMessage` из `NpcRandomLineUi337`, обрезкой как у штатных строк и `sidebarHeight += 22`.
Причина «бесконечного цикла» 336: стоковый граф принимал связь ноды с самой собой (подтверждено
харнессом). Гейты: новый `tools/self_link_check.py`, переписан `tools/random_line_check.py`, все
остальные PASS; харнессы `SelfLink337SmokeTest` (11), `RandomLineSmokeTest` (16),
`CommonDialogueSmokeTest` (17) — ALL OK.

Предыдущая — `settledlands-0.8.0-build336-random-line.jar` — Build 336: **у ноды «Реплика» тумблер «🎲 Случайная
реплика»**. Включаешь — один бросок костей по числу фраз (фразы разделяются новой строкой), результат
пишется в саму ноду: `Node#varValue`, 0 = выкл, 1..n = выпавшая фраза. `Node#lineFor` через
`NpcRandomLineNodeMixin336` (HEAD, cancellable) отдаёт выпавшую строку — это единственная точка, через
которую диалог берёт текст ноды, поэтому работают и плейсхолдеры, и цепочки. Тумблер нарисован над
карточкой (`NpcRandomLineUi336`), клик забирается в HEAD `mouseClicked` до перетаскивания; клиентский
миксин — **только** в `client`. `NpcRandomLine336` — правило (фразы, бросок, подрезка). Добавлено 4
класса, гейты PASS (включая новый `tools/random_line_check.py` и прогон
`tools/harness/RandomLineSmokeTest.java`). База — 335, патч `tools/patch_build336.py`.

Важно: поля `timerRepeat`/`timerInterval` для этого не подошли — у них дефолт `true`/`10`.

Предыдущая — `settledlands-0.8.0-build335-common-buttons.jar` — Build 335: **кнопки «Общего» видно в диалоге**.
334 сделал «Общее» общим для тика и урона, но диалог собирает ответы только из `profile.dialogueGraph`
(`sendGraph` — `choices` ноды, по которой идёт разговор; `pickGraph` — поиск id там же), поэтому нода
из «Общего» не показывалась. Теперь на время разговора в профиль подставляется слитая копия (граф NPC
+ достижимые от корней ноды «Общего» под новыми id), корни «Общего» вешаются на стартовую реплику,
конец цепочки ведёт назад к NPC. `NpcProfile#save` на время вызова возвращает родной граф, поэтому
слияние не попадает ни в NBT, ни в редактор. Добавлено 4 класса, гейты PASS (включая новый
`tools/common_dialogue_check.py` и прогон `tools/harness/CommonDialogueSmokeTest.java`). База — 334,
патч `tools/patch_build335.py`.

Предыдущая — `settledlands-0.8.0-build334-common-graph.jar` — Build 334: вкладка **«Анимации» стала «Общим»** —
один граф на весь мир. Граф хранится в мировых данных (`NpcCommonGraphStorage`, файл
`settledlands_common_graph`), его тикает каждый NPC вместо личного (`NpcCommonGraphMixin334`), урон
тоже идёт из него (`NpcCommonGraphDamageMixin334`), редактор любого NPC показывает один и тот же
граф. Пока общий граф не сохранён — каждый NPC работает со своим прежним. Добавлено 4 класса, гейты
PASS (включая новый `tools/common_graph_check.py`). База — 333, патч `tools/patch_build334.py`.

Предыдущая — `settledlands-0.8.0-build333-dropdown-first.jar`: **открытый выпадающий список забирает
клик первым**. Список рисуется поверх всего (`render` → Z+500), а клик искался поздно — за
HEAD-хуками 272/290/320/321, за карточкой авто-ИИ и только в самом конце за штатным сторожем
`openDropdown`. Новый миксин `NpcDropdownFirst333` стоит **первым** в `client` и в HEAD
`mouseClicked` отдаёт клик открытому списку: `popupClicked` по строке, `closePopup` по прочим
кнопкам, `openDropdown(null)` при закрытии, `return true`. Добавлен 1 класс, гейты PASS (включая
новый `tools/dropdown_check.py`). База — 332, патч `tools/patch_build333.py`.

Предыдущая — `settledlands-0.8.0-build332-blue-wire-points.jar`: точки работают **и на синих проводах**
(значения). Точка синего провода живёт в том же тексте узла под маркером `v<дальний конец>:x,y`,
рисуется проходом значений через свои точки, находится hit-тестом по тем же концам, тянется,
снимается и исчезает вместе с отрезанным проводом.

До неё — `settledlands-0.8.0-build331-middle-click-remove.jar`: точка снимается **средней кнопкой
мыши** (`decide`, кнопка 2 = кнопка 1), тест `ClickOrder` обновлён под это правило.

До них: 330 — маршрут через точки применяется на входе самого `cable(...)` (после того как лог 329
доказал, что `@Redirect` на месте вызова не выполнялся); 329 — только диагностика `cable-hit`;
328 — маршрут по двум концам (`routed`) + строки `store:`.

**Подтверждено в игре:** 330 — провод изгибается через точку и идёт за ней (пользователь, 2026-11-02).

**Нерешённое наблюдение из логов 328/329:** хранилище прыгает `#7{0:6=1 }` ↔ `empty` примерно каждые
5 секунд (16:51:46–52 пусто, дальше опять точка). Либо в это время открывался граф другого NPC, либо
точка на этих секундах теряется — уточнить у пользователя, это не проверено.

Цепочка (историческая): 272 → 273 → … → 324 → 325 (`addBend(...,index)`, `addRoutePoint`, `wireAt` по
пути) → 326 (`decide`/`onDot`, drag завершает жест, бросок на точку всегда ждёт, `alsoLinks`) →
327 (`pointOn`, порт ветки случайной ноды, `drawn`) → 328 (`routed`, диагностика) → 329 (`cable-hit`,
диагностика входа перехвата) → 330 (перехват перенесён в сам `cable(...)`) → 331 (СКМ удаляет
точку) → 332 (точки на синих проводах) → 333 (список забирает клик первым) → 334 (вкладка
«Общее» — общий граф на всех NPC).


## Найденные краши (хронология)

| Сборка | Симптом | Причина | Кто ловит |
|--------|---------|---------|-----------|
| 272 | старт клиента | CallbackInfoReturnable на void `focus`, @Shadow на несуществующий `selected()`, @Inject в несуществующий `charTyped` | gate 3 (цели mixin-ов) |
| 273 | создание ноды преследования | `@Inject(TAIL)` без `cancellable = true` вызывает `setReturnValue` → CancellationException | gate 3, фаза `check_cancellable` |
| 273 (лог 1) | старт клиента | чужой мод `hackersandslashers` (Forge-сигнатура `ModConfigSpec.Builder.define(String,String)`) | не наш код |
| 277 | «создаётся нода-триггер» | при переписывании `NpcPursuitGraphUiMixin272` в 277 потерялся `@Inject` на `drawNode` — карточку рисовал штатный рендер («Триггер #n» + строка действия) | не ловится гейтами (нет хука — нет цели), лечится в 278 |
| 282 | «пропали анимации у тела, в которое вселился» | `PlayerMotionBridge#setup` по замыслу ставил тело в нейтральную позу, когда Fresh Animations Player Extension не установлен — в инстансе его нет вообще (лог: «is unavailable or disabled for this player»), поэтому вселённое тело стояло замороженным, а NPC жили через откат `NpcMotionBridge256` | переписана 1 инструкция (`iload freshPath` → `iconst_0`), ветка нейтральной позы не выполняется никогда, при неработающем FA тело берёт встроенную актёрскую анимацию; текст предупреждения исправлен на месте |
| 280 | «Ticking entity», `NoSuchMethodError: Entity#hasLineOfSight` | метод объявлен в **заглушке** `Entity`, в реальном MC он у `LivingEntity`; ветка срабатывала только при разрыве >24 блока | gate 3 фаза D (внешние API против сборки, работающей в игре) — добавлена в 281 |
| 276 | «в поле не печатается, текст наслаивается, нода становится триггером» | `@Unique` переименовал `charTyped` (не переопределение), а панель 276 рисовалась поверх штатного редактора триггера; `Screen.mouseClicked` не смотрит на `visible`, поэтому спрятанное поле команды продолжало принимать клики и затирало `node.action` | не ловится гейтами (семантика UI) — лечится тем, что ветка штатного инспектора не выполняется вовсе (277) |

Правило: перед каждой выдачей прогонять `tools/verify_jar.py`; если появляется
новый класс ошибок — сначала дописать под него фазу в gate, потом чинить.

## Что лежит в репозитории

| Путь | Что это |
|------|---------|
| `tools/validate_jar.py` | gate 1: zip/CRC, toml (modId/version/displayName/description), JSON-ассеты, mixin-конфиги |
| `tools/jvm_verify.py` | gate 2: структурная проверка каждого класса (CP, опкоды, ветки, exception-таблицы) |
| `tools/check_mixin_class.py` | gate 3: mixin-конфиги + РАЗРЕШЕНИЕ ЦЕЛЕЙ (@Inject/@Shadow/@ModifyConstant) внутри JAR + фаза C «dispatcher hooks» (класс, который обязан вызываться из миксина, не должен остаться мёртвым — ловит потерю `drawNode`-хука из 277) |
| `tools/verify_jar.py` | gate 4: прогон 1→3 + `<jar>.sha256sums.txt` |
| `tools/classfile.py` | парсер class-файлов и байткода (без JDK) |
| `tools/patch_build273.py` | патч 272→273 на уровне байткода (удаление методов, retarget shadow) |
| `build273-src/` | исходники build 273 (2 mixin-а + скрипт + README) |
| `build275-src/` | исходники build 275: `PursuitGraphNode272`, 2 mixin-а, `patch_build275.py`, README, stubs |
| `build278-src/` | исходники build 278: те же 3 класса + `patch_build278.py`, README, stubs, `out/` |
| `build279-src/` | исходники build 279: `NpcPursuit269` (сервер), `PursuitGraphNode272`, 2 mixin-а, `patch_build279.py`, README, stubs, `api-src`/`api2`, `out/` |
| `build280-src/` | исходники build 280: то же + 3-й mixin `NpcPursuitOutputMixin280`, `patch_build280.py` |
| `build282-src/` | исходников нет: build 282 правит байткод `PlayerMotionBridge#setup` напрямую (`tools/patch_build282.py`), 1 инструкция |
| `build281-src/` | исходники build 281: те же исходники с конусом обзора вместо `hasLineOfSight`, `patch_build281.py` |
| `build277-src/` | исходники build 277: `PursuitGraphNode272` (карточка), `NpcPursuitGraphUiMixin272`, `NpcPursuitInspectorMixin277`, `patch_build277.py`, README, stubs, `out/` |
| `tools/patch_build282.py` | сборка 282: правка 2 байт в `PlayerMotionBridge#setup` (iload→iconst_0) + текст предупреждения, toml + patch-info; сам проверяет шаблон и результат |
| `tools/patch_build281.py` | сборка 281: 1 класс заменён (`NpcPursuit269`), toml + patch-info |
| `tools/patch_build280.py` | сборка 280: 6 классов заменено, 2 добавлено (в т.ч. `NpcPursuit269$Published`), mixins.json (+1), lang (+3 ключа), toml, patch-info |
| `tools/patch_build279.py` | сборка 279: 6 классов заменено (`NpcPursuit269` + 3 внутренних, `PursuitGraphNode272`, `NpcPursuitInspectorMixin277`), lang (+2 ключа), toml, patch-info |
| `tools/patch_build278.py` | сборка 278: 1 класс заменён (`NpcPursuitGraphUiMixin272`), toml + patch-info |
| `tools/patch_build277.py` | сборка 277: 3 класса заменены, +1 mixin, −1 mixin, mixins.json, lang, toml, patch-info |
| `tools/patch_build275.py` | сборка 275: подмена 3 классов, +1 новый mixin, mixins.json, toml, patch-info |
| `AI-GUIDE-SettledLands.md` | гайд из build 228 (актуальные разделы про сборку/ритуал доставки) |
| `latest.log` | лог от 27.10 — там битый `build236-corpse-tex.jar`, не код |

## Как проверять сборку

```bash
python3 tools/verify_jar.py <jar> --expect-version 0.8.0-buildNNN \
        --base <предыдущий jar, который работает в игре>
```

`--base` включает фазу D гейта 3: каждый вызов Minecraft / NeoForge из наших
классов должен уже встречаться в базовой сборке (иначе это выдумка заглушки —
урок 280).

Gate 3 — единственный, который ловит «Critial injection failure» на старте.
Именно он находил три ошибки build 272. Для **новых** mixin-ов всегда
прогонять его до выдачи сборки.

## Открытый хвост

* Build 279: ждёт проверки в игре. Изменено: дистанция считается до цели (по умолчанию 1.6,
  настройка 1.0–8.0), остановка гасит move control и скорость (`NpcEscort#stopMotion`),
  догон = скорость сопровождения + перестроение каждые 4 тика, выход на 3 блоках,
  преследователь идёт к кольцу дистанции по своему направлению, две настройки ноды
  (`|дистанция|догон` в действии + `PursuitKeep279`/`PursuitCatchUp279` в NBT).
  Действие ноды остаётся типа TRIGGER — это по-прежнему нужно серверному пути триггеров.
* Build 276: `@Unique` на слитом `charTyped` в 275 приводил к переименованию метода —
  ввода не было; теперь метод реально переопределяет `Screen#charTyped`. Инспектор ноды
  преследования больше не триггерный: новый `NpcPursuitInspectorMixin276` прячет пресеты и
  поле команды и строит панель «тип цели / значение / завершение».
* Урок: `@Unique` на методе, который должен переопределять метод суперкласса, ломает
  переопределение молча (gates этого не видят — метод структурно валиден).
* Build 275 закрыл три пункта из 274: портов не было (инжектор `hitPort` → -1 удалён),
  карточка была 280×140 (теперь стоковая 220×100 со стоковой палитрой), ввод в поле
  не работал (`charTyped(char,int)` слит в экран миксином `NpcPursuitTypingMixin275`).
* Мёртвый код из 272 остался, не мешает: `NpcStatsGraphUi.width/height/hitPort` ветка
  `type == 16` — таких нод нет (нода преследования имеет тип 2 / TRIGGER).
* Build 263 CUTSCENE fade по-прежнему не включать.
* Build 269/270 отозваны.

## Окружение песочницы

* `python3` есть, JDK/javac в PATH нет; Java-исходники собираются вендорным
  `tools/vendor/ecj-3.46.100.jar` против заглушек `tools/api271/` (+ `build279-src/api-src`).
* `java` из `/tmp/jdk4` пропадает между сессиями. Восстановление:
  `pip download jdk4py --no-deps -d /tmp/jdkpkg`, распаковать колесо в `/tmp/jdk4`,
  `chmod +x /tmp/jdk4/jdk4py/java-runtime/bin/*`; это JRE 25, ECJ 3.46.100 работает.
* Home-путь сборки: `build279-src/`.
* Прямого интернета нет; Google Drive и GitHub — через коннекторы.
* Скачанные с Drive файлы попадают в `google_drive/` (в git не коммитятся).

## Build 291 (актуально)

- `settledlands-0.8.0-build291-launch-fix.jar` — Drive `1M49OGceKN0eD3TGJsevGG_jVxlT1v6aF` (public).
- `SettledLands-Build291-LaunchFix-Source.zip` — Drive `1WHFEkfH-ndtoJV1_m8xCiE8JT_vKV3au` (public).
- `SHA256SUMS-build291.txt`, `README-Build291-RU.md`, патчер `tools/patch_build291.py`.
- Гейты: validate_jar OK (455 записей / 407 классов), jvm_verify OK, check_mixin_class OK (38 миксинов, типы параметров сверены),
  verify_jar PASS (0 новых ссылок, 0 висячих),diff с 290: ~`mixin/GuideDialogueValue290.class` + метаданные.

## Build 292 (актуально)

Причина вылета пользователя: `tp @npc ~ ~5 ~` в ноде триггера. `NpcActions#fill` подставляет
`@npc` → uuid НПС, затем `NpcActions#command`/`#commandForEntity` проверяют
`^(tp|teleport)\s+([~^\-0-9].*)` — «цели нет, только координаты» → вставляют `@s`.
UUID начинается с hex-цифры ⇒ `tp @s <uuid> ~ ~5 ~` ⇒ «Неверный аргумент для команды».

Фикс: `NpcSelfTarget292.needsSelfTarget` (токен после tp/teleport состоит только из `~ ^ - . 0-9`)
+ `NpcActionsTpFix292` — `@Redirect` на `String.replaceFirst` в `command` и `commandForEntity`
(по одному вызову в каждом, патчер это проверяет: `check_redirect_sites`).
`@s` — игрок (источник команды), `@npc` — НПС (uuid), `@p`/ники не трогаются.

- `settledlands-0.8.0-build292-tp-target-fix.jar` — Drive `18jBFhFrrfDyziqp-oM_5o1SORPWaf1bz` (public);
  `SettledLands-Build292-TpTarget-Source.zip` — Drive `1rYfuWAN3YqxkVwC2Xz3lTphGRDmzdSzT` (public);
  `SHA256SUMS-build292.txt`, `tools/patch_build292.py`, `build292-src/`.
- Гейты: 457 записей / 409 классов, 39 миксинов, 0 новых внешних ссылок, 0 висячих.

## Build 293 (актуально)

Лог `latest-5.log` показал: игра запускалась на 292 (`settledlands-0.8.0-build292-tp-target-fix.jar`),
и всё равно выполнила `/tp @s <uuid> ~ ~5 ~`. То есть `@Redirect` на `String.replaceFirst` в
`NpcActions.command` в этой сборке **не сработал** (молча, без падения). Вывод: не полагаться на
инъекцию там, где можно поправить саму строку.

293 (база — 291, без 292) меняет ОДНУ строку в пуле констант `NpcActions.class`:
`^(tp|teleport)\s+([~^\-0-9].*)` → `^(tp|teleport)\s+[~^\-0-9.]*(\s.*)?` (30 → 35 байт).
Class-файл не содержит абсолютных смещений, поэтому Utf8-запись просто переписывается с новой
длиной (`tools/patch_build293.py: patch_constant`). Читателей ровно два — `command` и
`commandForEntity`; сборщик это проверяет, класс дополнительно парсится `java.lang.classfile` (JDK 25).

- `settledlands-0.8.0-build293-tp-target-guard.jar` — Drive `1XN5-9TrTQkLrg6jNq8BGH96jn9k1IXIi` (public);
  `SettledLands-Build293-TpTargetGuard-Source.zip` — Drive `1DIq9b7owCw2usdsECfOXuHtiPkdqXJ5i` (public);
  `SHA256SUMS-build293.txt`, `README-Build293-RU.md`, `tools/patch_build293.py`.
- Дифф против 291: `~npc/NpcActions.class`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 455 записей / 407 классов, 38 миксинов, 0 новых внешних ссылок, 0 висячих.

## Build 316 (актуально) — шаг по скорости и без однокадрового сброса

Жалоба: «при остановке на 1 кадр сбрасывается анимация при переходе в idle» + «двигает ногами
независимо от скорости».

- **Ноги:** `FULL_SPEED` был 0.24, а измерение — в блоках/с. Разница ×20: вес бленда всегда 1.0,
  фаза 8.3 юнита/тик вместо 0.46. Исправлено: `FULL_SPEED = 4.317` (скорость игрока, блоки/с),
  `UNITS_PER_BLOCK = 20/4.317 = 4.633`.
- **Один кадр:** `NpcPoseBlend315.blend` стартовал от своего прошлого сэмпла и отключался при
  паузе > 250 мс. Теперь `capture(part)` на HEAD `Pose#set` снимает позу **до** записи, blend идёт
  от неё всегда.
- Гейт `pursuit_check.py` проверяет единицы (`FULL_SPEED >= 3.5`, `UNITS_PER_BLOCK >= 3.0`) и
  наличие `capture` + хука. На 315 падает тремя пунктами.

## Build 315 — кросс-фейд между анимациями

Жалоба: «всё ещё рваные телепорты между анимациями».

Причина: клип записывал всю позу в модель за один кадр (рука 80° → −20°). Единая точка записи —
`NpcMotionBridge256.Pose#set(ModelPart)` (через неё идут и полная анимация, и маска). За ней теперь
стоит `NpcPoseBlend315`: 9 чисел на часть тела смешиваются с прошлым кадром, τ = 90 мс, сброс на
первом кадре части и при паузе > 250 мс. Миксин — `NpcPoseBlendMixin315` (client).

Для компиляции добавлен стаб `build303-src/stubs/net/minecraft/client/model/geom/ModelPart.java`
(только 9 публичных float-полей; имена подтверждены байткодом `Pose.set`, новых внешних ссылок 0).

`pursuit_check.py` требует `NpcPoseBlend315` + запись миксина в конфиге; на 314 падает.

## Build 314 — ещё медленнее и плавная анимация

Жалоба: «ещё немного замедли, анимации всё ещё рваные, переходов нет, в TnM всё плавно».

- **Скорость:** `PURSUIT_WALK` 1.15 → 1.05, `CATCH_UP_EXTRA` 1.25 → 1.15, share 0.6 → 0.5
  (догон при boost 1.5: ×1.40).
- **Рваность:** `NpcLocoDriver283` мерил скорость по позиции раз в кадр, а позиция моба на клиенте
  меняется раз в тик → ступенька шла в фазу шага и в вес бленда. Теперь скорость сглаживается
  (`SPEED_SECONDS = 0.06`), фаза идёт по сглаженной скорости, переходы 120/300 мс.
- **До TnM:** новый `NpcLocoInputsMixin314` (`@Mixin(targets="net.minecraft.world.entity.WalkAnimationState", remap=false)`)
  перехватывает `position(F)F`, `speed(F)F`, `speed()F` и отдаёт сглаженные значения для
  зарегистрированных состояний; для остальных `NaN` → ваниль.
- Патчер `patch_build314.py` первым умеет и замену, и **добавление** класса + запись в
  `settledlands.mixins.json` (client).

`pursuit_check.py` дополнительно требует `SPEED_SECONDS`, наличие миксина и его запись в конфиге.

## Build 313 — преследование не встаёт, пока идёт цель

Жалоба: «останавливается, хотя всё ещё должен идти за игроком, вёл себя как спутник в Скайриме».

- **Стоп на дистанции.** `arrived` ставился при входе в keepDistance (1.6) и тормозил тело; возврат
  ходьбы — только через RESUME_MARGIN. Идущий игрок проходит зазор за секунду → цикл стоп-старт.
  Теперь «прибыл» ставится только при **неподвижной** цели: смещение цели > 0.1 блока/тик
  (`TARGET_MOVING_SQ = 0.01`) означает «идёт» и преследование не тормозит.
- **Конец пути.** Новый путь строился только после смещения цели на 3 блока. Теперь
  `FOLLOW_STEP_SQ = 0.25` (0.5 блока) — путь перестраивается сразу; стоим только когда мы у цели
  и цель стоит. Если тело застряло далеко от стоящей цели — путь перестраивается по откату
  (20/40 тиков), а не замирает.
- `RESUME_MARGIN` вернулся к 0.75.

`pursuit_check.py` требует `movedBy`, `TARGET_MOVING_SQ`, `FOLLOW_STEP_SQ` и проверяет числа.

## Build 312 — темп преследования и рваное движение

Жалоба после 311: «слишком быстрые» + «рваные, останавливается перед тем как повернуться».

- **Скорость.** 311 гнал 1.6×…3.04× обычной ходьбы. Теперь `PURSUIT_WALK = 1.15` (0.115) и
  догон `CATCH_UP_EXTRA = 1.25 + 0.6 × (boost − 1) × ramp` → ×1.55 при boost 1.5, максимум ×2.45.
  База берётся как `walkingNavigationSpeed(false) / damping`, то есть настоящая ходьба (0.10).
- **Рваность.** Причины: (1) путь пересобирался при смещении цели на 2 блока — теперь держится до
  конца, пересборка только в догоне после 3 блоков и не чаще 10 тиков (`REPATH_SUCCESS_TICKS`);
  (2) смена режима догона вызывала `stopMotion` — убрано; (3) откат после провала 20/40/80/160
  тиков (`RETRY_MAX_SHIFT = 3`) — теперь 20/40; (4) сторож прогресса больше не обнуляет движение;
  (5) `RESUME_MARGIN` 0.75 → 1.0, чтобы цель на границе не включала/выключала тело.

`pursuit_check.py` теперь читает ConstantValue из класса и проверяет числа (PURSUIT_WALK ≤ 1.3,
CATCH_UP_EXTRA ≤ 1.4, RETRY_MAX_SHIFT ≤ 1, REPATH_SUCCESS_TICKS ≤ 14, RESUME_MARGIN ≤ 2.0).

## Build 311 — преследование: 10 секунд и скорость

Две жалобы с одной связки «Урон → преследование»:
1) NPC преследует ровно 10 секунд и встаёт; 2) скорость ходьбы при преследовании не поднята.

- **10 секунд** — `NpcDamageValue290.WINDOW_TICKS = 200`. Нода преследования читала источник каждый
  тик, после окна получала «никого» → `stopMotion`. Фикс: `State.latched` + `latchedBody(npc, state)`
  — тело защёлкивается до смерти/ухода; свежий ответ источника перекрывает защёлку.
- **Скорость** — `walkingNavigationSpeed(true)` внутри умножается на мирный множитель
  `NpcAutoAiEngine255.navigationSpeedFactor` (×0.55), из-за чего преследование = 0.088 против 0.10
  обычной ходьбы. Фикс: делим на множитель → 0.16 (1.6× обычной), догон ×1.4…×1.9 сверху.

Изменены 5 классов `NpcPursuit269` (+`$State/$Published/$TargetCache/$SlotCache`). Гейт
`tools/pursuit_check.py` проверяет наличие защёлки и отмену множителя; на 310 падает.

## Build 310 — синий провод от ноды «Урон»

Жалоба: «вытянул синий кабель из выходного узла урона и подключил во входной узел преследования,
но синий провод не отображается».

Причина: в `NpcPursuitValuePinMixin290` и рисование (`settledlands$paintValueWires`), и резка
(`settledlands$cutValueWire`) проверяли `isCheckValueNode(from)` — только «В радиусе». Ссылка
при этом сохранялась в графе и работала на сервере, поэтому NPC преследовал цель, а провода не было.

Фикс — два условия: `isCheckValueNode(from)` → `isValueSourceNode(from)` (В радиусе **или** Урон).
Тронут один класс, `dev/settledlands/mixin/NpcPursuitValuePinMixin290.class`.

Новый гейт `tools/blue_wire_check.py` читает класс из jar и падает, если рисование или резка
снова сузят проверку: 303/309 — FAIL (воспроизводит баг), 310 — OK.

## Build 309 — откат до 303

Пользователь: «откатить ещё до того момента как мы начали менять граф под граф из другого мода».
308 откатывал только до 304, а 304 — уже скин под G&G, поэтому сборка сделана **от jar 303**.

- в 309 нет ничего из 304–308: `WorkspaceSkin304`, `NpcWorkspaceMixin304`, `WorkspaceTheme305`,
  `NpcNodeLibrary305`, `NpcWorkspace306`, `NpcWorkspaceEntry306Mixin`, `NpcLibrary305Mixin`,
  ключей `gui.settledlands.workspace.*`;
- внутри — 303 целиком: синий провод одной линией, тумблер отмены преследования второй строкой,
  старые скорости, круглые синие пины, белые порты/кабели, «Условие» по своей переменной,
  резка связи средней кнопкой, труп как цель;
- mixins.json — 20 client как в 303, версия `0.8.0-build309-classic`.

Гейты: 458 записей / 410 классов, 41 миксин, 1385 внешних ссылок / 0 новых vs 303, verify_jar PASS.
`screen_smoke` — SKIP (экрана нет). Исходники 304–307 остаются в истории git.

## Build 308 — откат к нашему виду нод

Пользователь: «откатим все эти изменения, вернёмся к нашему виду нод». Сборка сделана **от jar 304**,
а не патчем 307: воркспейс 306/307, его миксин и библиотека 305 в неё не входят вообще.

- убрано: `NpcWorkspace306` (+`$Link`), `NpcWorkspaceEntry306Mixin`, `NpcLibrary305Mixin`,
  `WorkspaceTheme305`, `NpcNodeLibrary305`, ключи `gui.settledlands.workspace.*`;
- mixins.json — список 304 (21 client), версия/displayName подняты до `0.8.0-build308-nodes`;
- в jar остаётся скин 304: холст в точку, карточки с рамкой, круглые порты, тёплые кабели,
  белая проводка 301/302 и синие провода значения; все инспекторы на месте.

`tools/screen_smoke.py` остаётся в репозитории: на jar без экрана воркспейса он пишет `SKIP`.
Исходники 306/307 остаются в истории git и в `build305-src/src`, в архив 308 не входят.
Гейты: 460 записей / 412 классов, 42 миксина, 1385 внешних ссылок / 0 новых vs 304, verify_jar PASS.

## Build 307 — воркспейс открывается + новый гейт

306 вылетала на первом кадре воркспейса:
`ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6` в
`NpcWorkspace306.drawTopBar` — подпись кнопки читалась как `LABELS[action + 1]`, а в таблице 6
записей; кнопка «Назад» (action 5) просила седьмую. Исправлено: `LABELS[action]` с зажимом в
границы. Рядом: `addNode` зажимает координаты в `MAX_COORD`, `graph.connect(...)` в
`mouseReleased` обёрнут в try/catch, `restore()` игнорирует битый снимок.

**Новый гейт 6 — `tools/screen_smoke.py`**: прогон экрана в обычной JVM без игры.
`build305-src/stubs` компилируются и грузятся как настоящие классы, четыре из них
(`Minecraft`, `Font`, `Screen`, `CompoundTag`) заменяются версиями из `tools/harness`, драйвер
`tools/harness/WorkspaceSmokeTest.java` строит граф со всеми 15 типами + все виды связей и
дёргает экран: construct / init / resize / render ×5 / save-tag / клики / драг / рамка / пан /
колесо / библиотека / поиск / ввод / undo / redo / delete / вкладки / создание графа / ПКМ.
На 306 гейт падает (`FAILURES: 7`) — ровно там, где падала игра; на 307 — 37 шагов, 0 падений.
Гейты: 466 записей / 418 классов, 44 миксина, 1389 внешних ссылок / 0 новых vs 306, verify_jar PASS.

## Build 306 — воркспейс нод = сам редактор

Пользователь: «взять код графа из нод, сделать его копию, скрыть там все существующие графы и
добавить мои». Реализовано своим экраном, G&G — только слой рисования и раскладка (разрешение
авторов заявлено пользователем).

- `npc/client/NpcWorkspace306 extends Screen` — владеет графом: `NpcGraphCanvas` (пан/зум/хиты),
  карточки `WorkspaceTheme305.drawNodeFrame`, порты по `portScreenX/Y`, кабели-безье по связям
  `next / falseNext / varInputNode / choices / triggers / randomBranches`, выбор, рамка,
  перетаскивание (шаг 8), связь порт→порт через `graph.connect(from,to,port)`, резка
  `graph.disconnect`, удаление, undo/redo через `graph.save()`/`NpcDialogueGraph.load`,
  три вкладки, библиотека `NpcNodeLibrary305`, сохранение тем же `NpcNet$CfgSave`.
- `mixin/NpcWorkspaceEntry306Mixin` — `NpcDialogueGraphScreen.open(...)` HEAD-cancellable →
  `new NpcWorkspace306(...)`. Старый экран доступен кнопкой «Старый редактор» (поля редактируются
  там), поэтому ни один инспектор не потерян.
- lang: `gui.settledlands.workspace.{title,legacy,new_graph}`.
- Новые стабы (только для компиляции): `Minecraft`, `CustomPacketPayload`, расширенный `Screen`,
  `CompoundTag` с настоящей сигнатурой `Tag put(String,Tag)` (гейт фазы D поймал рукописный
  `put(String,Object)` — такой сигнатуры в игре нет).

Ещё нет: инспектор полей внутри воркспейса, меню добавления по ПКМ на холсте.
Гейты: 466 записей / 418 классов, 44 миксина, 1389 внешних ссылок / 0 новых vs 305, verify_jar PASS.

## Build 305 — воркспейс нод + библиотека с поиском

Пользователь: 304 — «ты просто перерисовал внешний вид», нужен **код системы нод**. LICENCE G&G
(`Riieno/Gadgets-And-Gizmos`) — All Rights Reserved; пользователь заявил, что разрешение авторов
получено, поэтому работа продолжена, но своими классами: перенесён только слой рисования, наши
ноды остаются нашими.

Новое:
- `npc/client/WorkspaceTheme305` — слой рисования воркспейса: `drawNodeFrame` (рамка, шапка с
  тоном акцента, точка-индикатор), `drawPort` (тёмный ободок + тело + светлое ядро),
  `drawExecPort` (ромб), `drawRow`, `drawPanel`, `drawTitleBar`, `drawRoundedRect`,
  `drawTopRoundedRect`, `drawCircle`, `drawDiamond`, `mix`/`withAlpha`. Палитра — дефолт темы.
- `npc/client/NpcNodeLibrary305` — панель 132 px слева: заголовок «Ноды» + счётчик, строки по
  14 px, прокрутка, `L` — скрыть. Поиск по RU-названию и латинскому алиасу.
- `mixin/NpcLibrary305Mixin` — на **своих** методах экрана: `widgets()` TAIL (невидимый
  `EditBox` в `children()` — не `addRenderableWidget`, гейт ругался на неизвестный внешний API),
  `render` TAIL (панель поверх холста), `mouseClicked` HEAD-cancellable (панель не отдаёт клик
  холсту), `mouseScrolled`, `keyPressed`. Нода создаётся штатным `add(int)` (`@Shadow public
  abstract`).
- `WorkspaceSkin304` переписан на `WorkspaceTheme305`: холст/точки/карточки/порты рисует
  перенесённый слой, палитра — алиасы на `WorkspaceTheme305`.
- lang: `gui.settledlands.workspace.{library,search,empty}` в ru_ru/en_us.

Ещё нет: меню «добавить ноду» с поиском на холсте (этап 2), перетаскивание/связи/инспектор кодом
воркспейса (этап 3).
Гейты: 463 записи / 415 классов, 43 миксина, 1389 внешних ссылок / 0 новых vs 304, verify_jar PASS.

## Build 304 — рабочая область нод как в G&G

Взята палитра/геометрия Advanced Contraption Controller из «Create Aeronautics: Gadgets &
Gizmos» (публичные исходники, `AdvancedControllerV2Theme` + `GraphV2ThemeData.DEFAULT`):
холст #0D1215 + точки #1B2429 через 24 ед., панель #202A2F, рамка #39464C, акцент #C6873F,
текст #E8E0CD, карточка 220×100, шапка 22, порт 10×10.
Реализация: новый обычный класс `WorkspaceSkin304` + миксин `NpcWorkspaceMixin304` на **своих**
методах экрана — `grid` (HEAD, cancellable → тёмный холст + точки) и `drawNode` (TAIL → рамка,
акцентная полоса, круглые порты). Кабели → `WorkspaceSkin304.CABLE` (через существующий
ModifyArg в `NpcWhiteWiring302`). Карточка pursuit перекрашена в палитру темы и зовёт тот же скин.
Ещё нет: перекраска шапок (нужна перерисовка заголовка), библиотека нод с поиском, меню
«добавить ноду» с поиском — это 305/306.
Гейты: 460 записей / 412 классов, 42 миксина, 1385 внешних ссылок / 0 новых vs 303.

## Build 303 — 302 без вылета

302 роняла клиент при открытии диалога с правкой: `Mixin apply ... NpcWhiteWiring302 ...
contains non-private static field COL_CABLE_WHITE:I`. **Mixin-класс не может объявлять статическое
поле, кроме приватного** — Mixin выбрасывает весь класс, и клиент падает на первой загрузке
целевого класса. Цвета (`COL_PORT_WHITE`, `COL_CABLE_WHITE`) теперь читаются из
`PursuitGraphNode272`, в двух белых миксинах полей нет.
Гейт `tools/check_mixin_class.py` дополнен правилом «в миксине нет статических не-приватных полей»
(на 302 он падает, на 300/301 проходит) — теперь такой вылет не пройдёт сборку.
Остальное как в 302: синий проход = код 300, тумблер стопа второй строкой, скорости (преследование
0.4/атрибут, догон ×1.4…3.4), пин «В радиусе» на своём порту.

## Build 302 — синие провода, тумблер стопа, скорости

Синие провода пропали в 301 не из-за синего кода: `settledlands$drawValueWires` /
`settledlands$paintValueWires` в 301 побайтово те же, что в 300. Убил их белый `@Redirect` на
`GuiGraphics.fill` — единственный хук в чужой метод Minecraft, а не в наш класс: цель не нашлась,
Mixin при неattachнутом хуке (require по умолчанию = `defaultRequire` = 1) выбрасывает весь класс,
вместе с синим проходом, который жил в нём же. Урок: **чужой хук не должен жить в одном классе с
синим проходом**; белая проводка вынесена в отдельные классы `NpcWhiteWiring302` (кабели) и `NpcWhitePorts302` (порты,
единственный хук в метод Minecraft) с `require = 0`;
`NpcPursuitValuePinMixin290` — ровно версия 300.
Тумблер «Завершить преследование» теперь второй в панели (`y + 35`, сразу под типом цели), ряды
сдвинуты компактнее; раньше он стоял на `y + 180` и обрезался правилом видимости.
Скорости: преследование = бывшая скорость догона (`walkingNavigationSpeed(true)`, 0.4/атрибут),
догон = она же × `CATCH_UP_EXTRA` (1.4) с ramp по «Ускорению догона» → 0.56…1.36/атрибут.
Пин «В радиусе» снова рисуется на своём порту (`drawPin` по `checkPinScreenX/Y`, радиус 2.0 × зум).
Гейты: 458 записей / 410 классов, 41 миксин, 1385 внешних ссылок / 0 новых vs 301, 0 висячих.

## Build 301 — пины и белая проводка

Синие пины: радиус в единицах карточки (PIN_RADIUS = 2.0) × зум, координаты через `canvas.screenX/Y`
(`drawValuePin`), захват `max(3.0, 5.0 × zoom)` (`pinHitRadius`); пины карточки pursuit рисуются после
её трансформации.
Белая проводка: `@Redirect` на `GuiGraphics.fill(IIIII)V` в `drawNode` — белеет только прямоугольник
10×10 на краю карточки (`isPortSquare`); `@ModifyArg index=6` на `cable`/`curve` в `wires` — все обычные
кабели белые. У трёх хуков `expect = -1` (несколько точек внедрения).

## Build 300 — провод и пин

Симптом: после 299 условие считается верно, но синий провод пропадал/появлялся («лаг»), пин — большой плюс.
Причина: `hasValueWires` читал `inputSource` (поле `varInputNode`), пока связь временно снята на проход
кабелей → «проводов нет» → ничего не рисовалось; то же в `wiredTo`. Плюс непарное скрытие на
`drawNode` для нод pursuit (мост отменяет метод, RETURN не срабатывает).

Фикс: `hasValueWires`/`wiredTo` → `stockInputSource`; скрытие для карточки только для обычных нод;
на `render` HEAD — только восстановление (`resetStockInputHide`); пины — круглая точка r=3 (`drawPin`),
`CHECK_PIN_HIT` 5. Гейты пройдены (456 / 408 классов, 39 миксинов, 1385 ссылок).

## Build 299 — карточка условия и лаг провода

298 не сработала: `PursuitGraphNode272#drawNode` вызывается только для нод pursuit, обычная нода
условия рисуется штатным `NpcDialogueGraphScreen#drawNode`. Лаг в 298 — от снятия связи на весь
кадр (граф менялся под проходом проводов).

299: связь снимается на один вызов `drawNode` (HEAD) и возвращается в RETURN; вокруг `widgets()`;
на проходе `wires()` с `finally`. Счётчик `HIDE_DEPTH` + `beginWiresPass` для сброса зависшего
прохода. Связь с нодой «Переменная» не трогается.

## Build 298 (актуально) — провод не переписывает карточку условия

Симптом: подключение синего провода «В радиусе» → условие показывало `#4 == 0` вместо `? кукт == 0`.
Причина: сервер уже верный (297) — имя переменной берётся у подключённой ноды только если это
нода «Переменная» (тип 4); врала карточка `drawNode`, которая для IF с `varInputNode >= 0` берёт
имя источника, а при пустом имени подставляет `#<id>`. В 290 связь пряталась только на время
`wires()`, карточки рисуются позже.

Фикс 298: связь вынимается из `varInputNode` на весь кадр (`@Inject HEAD/TAIL` на `render`),
синий проход читает источник через `PursuitGraphNode272.stockInputSource`, сохранённая связь
возвращается в том же кадре. Гейты пройдены (456 записей / 408 классов, 39 миксинов, 1385 ссылок).

## Build 297 (актуально) — условие проверяет свою переменную

Главное уточнение от пользователя: нода условия проверяет ТОЛЬКО выставленную в ней переменную
(свой оператор, своё число). Нода «В радиусе» даёт импульс и передаёт найденного игрока, но не
решает, что сравнивать. Сборки 290–295 подменяли проверку на «тело есть/нет» (1/0) — переменная
не читалась вообще. В 297 хук на `evalIfNode` и метод `ifValue` удалены; условие снова читает
свою переменную у игрока цепи (= игрок, найденный нодой радиуса). Пустой радиус — цепь не идёт.

Гейты: 456 записей / 408 классов, 39 миксинов, 1385 ссылок, 0 новых. Diff vs 296:
`~NpcValueSource290` (-ifValue), `~mixin/GuideDialogueValue290` (-settledlands$ifFromValue),
toml, patch-info.

## Build 296 (актуально) — откат 295

295 отменена: хук запускал цепь из пустого радиуса без тела, и это сломало ноду условия.
296 = код 294 без изменений, изменены только `neoforge.mods.toml` и `settledlands-patch-info.txt`
(+0/-0/~0 классов; побайтно совпадает с 294 везде, кроме этих двух записей).
Гейты пройдены: 456 записей / 408 классов, 39 миксинов, 1385 ссылок, 0 новых.

Замысел пользователя (важно, 294/295 его НЕ реализуют): нода «В радиусе» находит игрока и отдаёт
ноде условия id этого игрока; нода условия читает **переменную этого игрока** и по сравнению
выводит True/False. Сейчас 290 подменяет проверку на «тело есть/нет» (1/0) — это не то.
Вопрос открыт: какую именно переменную найденного игрока читает условие и что она должна видеть,
когда игрока нет.

## Build 295 (актуально)

После 294 пользователь сообщил: в цепи «В радиусе» #4 → «Условие» #5 (`#4 == 0`) при пустом радиусе
по-прежнему ничего не печатается. Лог `latest-6.log` показал механизм: нода CHECK — не звено обхода,
а сканер. `tickGraphCheckNodes` каждые 10 тиков печатает `[graph] check node #4 target 0 radius 5
scanning...`, и **только при найденном теле** — `FIRED on <имя>` и `walk starts at node #5 for <имя>`,
то есть цепь стартует со СЛЕДУЮЩЕЙ ноды. При пустом радиусе обхода нет вообще, поэтому правка 294
(внутри `resolveExecutionTarget`) никогда не выполнялась.

295: `GuideNpcEmptyCheck295` (`@Inject HEAD` на `GuideNpc#customServerAiStep` — оттуда и вызывается
`tickGraphCheckNodes`) → `NpcValueSource290#tickEmptyChecks` (раз в 10 тиков, свой кулдаун по ноде). После штатного скана по каждому CHECK-узлу: пусто по `NpcCheckValue289#detected`
(скан 289 без кулдаунов) + нода питает синий вход (`varInputNode == id` у любого узла) + нет своего
`action` + вышел кулдаун ноды (`checkCooldown*20` тиков, `LAST_EMPTY`) + `isIncomingIfSatisfied(null,…)`
→ `GuideDialogue.resolveExecutionTarget(null, npc, null, graph, node.next, new HashSet<>())`. Первый пустой проход
пишет одну строку `[settledlands] [graph] empty radius #N: chain starts at node #M` — доказательство, что
хук в игре работает; дальше молчит.
Действие и триггеры самой ноды на пустом проходе не выполняются; тело «на своём кулдауне» — это
тело, им владеет штатный путь, двойного срабатывания нет. Поведение прочих графов не меняется.

- `settledlands-0.8.0-build295-empty-radius-chain.jar`, `SettledLands-Build295-EmptyRadius-Source.zip`,
  `SHA256SUMS-build295.txt`, `README-Build295-RU.md`, `tools/patch_build295.py`.
- Дифф против 294: `+mixin/GuideDialogueEmptyCheck295.class`, `~npc/NpcValueSource290.class`,
  `~settledlands.mixins.json`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 458 записей / 410 классов, 41 миксин, 1385 внешних ссылок, 0 новых, 0 висячих.
- Урок: у CHECK-ноды ДВА пути — сканер (`tickGraphCheckNodes` → `triggerCheckChain`) и ветка type 5
  внутри `resolveExecutionTarget`. Правки делать в сканере: именно он решает, будет ли цепь вообще.

## Build 294

Цепь «В радиусе» #4 (Цель: Игрок, Радиус 5, Кулдаун 5 с) → «Условие» #5 (`#4 == 0`),
True → `say true`, False → `say false`. Кто-то в радиусе — печатается `false`; никого — не происходило ничего.

Причина (по байткоду 293): в `GuideDialogue#resolveExecutionTarget` нода CHECK (type 5) — ворота.
`checkProximityCondition` false → `curId = -1` сразу, обход заканчивается на #4, условие #5 не выполняется
и ни разу не видит 0. Значение ноды для синего провода считается отдельно
(`NpcValueSource290#ifValue` → `NpcCheckValue289#detected`) и пустой радиус = 0 — ответ просто не доезжал.

294: `GuideDialogueCheckGate294` (`@Redirect` на `checkProximityCondition` в `resolveExecutionTarget`) +
`NpcValueSource290#passThroughWhenValue`. Правило: непрошедшая проверка пропускает обход дальше по `next`
только если нода питает синий вход (некто в графе имеет `varInputNode == id`), нода — источник
(CHECK/DAMAGE) и у неё нет своего действия; своё действие и её список триггеров при этом не выполняются
(метка `PENDING`/`PENDING_TRIGGERS` гасится в `triggerBlocked`). Всё остальное — как было.
Нода CHECK со своим действием остаётся воротами (непрошедшая проверка обрывает цепь, как раньше).

- `settledlands-0.8.0-build294-radius-if-branch.jar` — Drive `1nyhbbinE4s-xFSxCKJx3vFS3aC3fBtAu` (public);
  `SettledLands-Build294-RadiusIfBranch-Source.zip` — Drive `1APfyH5hc0V4oKN6hr_XICYi57xt1bTjF` (public);
  `SHA256SUMS-build294.txt`, `README-Build294-RU.md`, `tools/patch_build294.py`.
- Дифф против 293: `+mixin/GuideDialogueCheckGate294.class`, `~npc/NpcValueSource290.class`,
  `~settledlands.mixins.json`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 456 записей / 408 классов, 39 миксинов (20 common + 18 client), 1385 внешних ссылок, 0 новых, 0 висячих.

## Урок Build 292 → 293

Лог `latest-5.log` (запуск на 292): `НПС Проводник: выполнена команда триггера: «/tp @s 8efbfa3e-… ~ ~5 ~»`
→ `@Redirect` на `String.replaceFirst` в `NpcActions.command` **не дал эффекта** (игра не падала, значит
инъекция формально применилась либо не зарегистрировалась — вопрос открыт; точный ответ даст 293).
Вывод на будущее: если правку можно выразить заменой строки/константы в СВОЁМ классе — делать так, а не
инъекцией; инъекция в свой класс у нас уже дважды вела себя не так, как ожидалось (290 — fatal, 292 — молча).

Техника: Utf8-запись пула констант можно переписать с новой длиной (`tools/patch_build293.py`), в class-файле
нет абсолютных смещений, остальные байты не двигаются. Проверка: `python3 - <<'PY'` парсит класс через
`java.lang.classfile` (JDK 25) — `parsed ok, methods=20`, строка в пуле `cp#181`.

## Drive-ids

**367 (текущая):** jar `1_XOIzzcukkt07-kShQFic_SRPxzO8jzq` (**public**), zip
`1sVQUl2KPPXNwJ5gwzcGHPpgDDuMY6Ds0` (**public**) — оба `anyone/reader` + `allow_file_discovery`,
проверено `list_permissions`. Коммит `f00e245` в `arena/5618404a-mod`.

**366:** jar `1gaq6vVgHTwtjs6nvGH_S8-KlS2CXqUQt`, zip `1Ruzv_yQvHlpzjWzpjddC2MnPR6ucKLuA` (оба public).
**365:** jar `1A2IaBOmiDTK8xvWG5t5WobYsi9onAPM7`, zip `1K7hHFVjaHiTQalpSrw8MLzK56mOWkYn_`.
**364:** jar `1NixebxWn-UIo2aOR-QeFNgTby0dsANzn`, zip `1X-IGiIK48wp0IcsyUmIUyt2xuBAwNYkm`.

## Drive ids

- 367: jar `1_XOIzzcukkt07-kShQFic_SRPxzO8jzq` (public), zip `1sVQUl2KPPXNwJ5gwzcGHPpgDDuMY6Ds0` (public)
- 368: jar `1PWCs50aMTIW_bUgffGxOQcErujF4AAZr` (public), zip `1LTauf0-cDfpR-DOmLaF4mTKQPEcMxeVb` (public) - **does not start**, see 369
- 369: jar `10NmZoT2EMt-CNRbqZFvV_AxW4lBlZ129` (public), zip `16XvrKcgbXcIOzs9hpfmd-9GCtLgiKlTa` (public) - **boots** (user log: only createrailwaysnavigator failed)
- 370: jar `1c1taj-mj1kFX5vk0XWtnQbf4v9Ew-tMk` (public), zip `1mIoHYFmbT3h1vFOU_6CuLMT7b0SWkufM` (public)
- 371: jar `1P7GecY-8QcNlCWAqZJgCcJKXoEvxcv_6` (public), zip `1IQfkxtt7vpaCu-Wa-o7bJrrxoWRAxY9H` (public)
- 372: jar `1Tu01-FJ1ajWNJ7H2M4tdEWRp__luvM5l` (public), zip `1z07djo7NRVoHVCVNXhn3g1GdZB2f6Bts` (public)
- 373: jar `1gM1Y9GOBRCfLdvj0c7JRWzZ4uqxW8PCy` (public), zip `1hZ_fIDCZzeZFARRnCuuDgpg93kKMOUj2` (public)

- 373 in game: **NOT tested**. `latest-10.log` (2026-11-05 13:46) loaded `settledlands-0.8.0-build373-door-refuse.jar`, then the client died at the loading screen (13:47:09) - zero `door:` lines, no world load, zero `dev.settledlands` frames in the crash. Crash = createdeco 2.1.3 `Catwalks.<clinit>` -> `CTSpriteShifter.getCT` -> ponder `StitchedSprite.<init>` `HashMap.computeIfAbsent` **ConcurrentModificationException** (parallel mod construction race) -> loader "broken mod state" -> keybinds not registered -> Quark `AutoWalkKeybindModule$Client.acceptInput` NPE on the first mouse click -> FATAL. Same shape in `latest-9.log` (369 session) with createrailwaysnavigator instead of createdeco: the loser of the Create/ponder sprite race is a different mod every launch. Not our code.

- 374 shipped (`door-proof`): same refusal as 373 (`DoorBlock#useWithoutItem` HEAD), plus proof - the first entry into that method logs `door: DoorBlock#useWithoutItem was reached at <pos>, locked=<bool> - the door-side guard of build 374 is live` (`DoorEvents368.sawDoorUse`, once per session) and the key line now carries `(build 374 door-proof)`. Handler wrapped in try/catch. `door_lock_check` extended: it now requires `useWithoutItem was reached` in DoorEvents368 and `sawDoorUse` in DoorUse373Mixin (373 jar FAILs on both). 23 gates green, `verify_jar --base 373` PASS (0 removed members, 0 new external refs).
- 374 artifacts: jar `settledlands-0.8.0-build374-door-proof.jar` 3 516 995 B / 558 entries, sha256 `cce5bc08efe9c7e75560ec82297a41293cc311c51cd5b299667190f9c79b4332`; source zip `SettledLands-Build374-DoorProof-Source.zip` 437 entries, sha256 `e1ab7f7abe715afad9c83b27ad1276db86322445ca618f71a686e97754dfd28a`; `tools/patch_build374.py` (BASE 373, 3 REPLACED); commit `06d4c4d`.
- UI screen the author showed in three screenshots (block graph: tabs «Главный/Распорядитель/Общее», «Удалить Граф», node panel, «Создать узел» dropdown) is **not** to be touched: the author answered that the screenshots are only context. Do not touch the base screen's layout.
- Workspace resets between turns: `git fetch origin && git reset --hard origin/arena/5618404a-mod` restores everything including build303-src, out373/out374 and the built jars (all tracked); `jdk4py` must be re-installed each session.
- 374 Drive ids (both public): jar `13GcMQg55eh2RXOr7Osz1K8i3OmrBO9ui`, zip `1iR5RPVVuPYPo4wxP13Kv7-rxdQlGN5To`.

- **375 shipped (`door-client`) - the tick was the client.** In NeoForge 1.21.1 `MultiPlayerGameMode#performUseItemOn` runs `PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(...); if (event.isCanceled()) return event.getCancellationResult();` and nothing else stops the local block use. `DoorEvents368.onRightClick` began with `if (!(level instanceof ServerLevel server) || pos == null) return;`, so on the client the event was never cancelled, the client ran `DoorBlock#useWithoutItem` locally and drew an open door until the server answered - that is the 1-tick window. 375 adds `DoorEvents368.client(...)`: on a non-`ServerLevel` the handler cancels the event and sets `InteractionResult.SUCCESS` for any door block, so only the server opens doors (unlocked doors open one tick later; the packet is sent before the prediction, so the server still receives the click). One log line per session: `door: the client refused to open the door at <pos> on its own - only the server opens doors now (build 375)`.
- 375 gates: `door_lock_check` now also requires an `InteractionResult.SUCCESS` reference in DoorEvents368 and the string `only the server opens doors` (374 jar FAILs on both, 375 passes). 23 gates green, `verify_jar --base 374` PASS (0 removed members, 0 new external refs).
- 375 artifacts: jar `settledlands-0.8.0-build375-door-client.jar` 3 517 472 B / 558 entries, sha256 `0d2aaeef4c56846300d70093bb9059b80e26cef21c028e68948d0a15b338114d`; source zip 438 entries, sha256 `f765f6635722aebac0f4f47f8a55748e3d7a3cfc4f35969cd8577f8e4b618883`; `tools/patch_build375.py` (BASE 374, 1 REPLACED); commit `c77ab2b`.
- 375 Drive ids (both public): jar `1apKDlwSaW47uxK5yxu_JBYQZZ6c4OvB_`, zip `1R8WPxC9NnmqIn7MIXdvGYwotvU4ZFoG1`.
- Next report to watch for: `the client refused to open the door` (client path live) and `DoorBlock#useWithoutItem was reached ... locked=<bool>` (door guard live). If the door still flickers with both lines present, the open comes from outside the click path (redstone, another mod, contraption) - ask for `latest.log`.

- **Links: Drive `uc?export=download` gave the author a 404 for the 375 jar** even though `get_file` shows `permissions: anyone/reader, allow_file_discovery: true` and the file is untrashed. Drive redirects cannot be tested from the sandbox (`drive.google.com` → curl 000). Standing decision from now on: publish **GitHub raw at the build's commit sha** - `https://github.com/BORGERone/mod/raw/<full-sha>/<file>` (repo is public, `isPrivate: false`; verified: 302 to raw.githubusercontent.com, contents API reports the exact byte size). Keep uploading to Drive as a copy, but the link in the reply is the GitHub one, and state the sha256 next to it.
- 375 link used: `https://github.com/BORGERone/mod/raw/f59fc44799efd6ed20fc3addd9fd3c28bcde2bf9/settledlands-0.8.0-build375-door-client.jar` (3 517 472 bytes, sha256 `0d2aaeef4c56846300d70093bb9059b80e26cef21c028e68948d0a15b338114d`).

## Build 376 - door sync (shipped)

**Build 376 shipped 2026-11-05, commit `036192e`.**

- Jar `settledlands-0.8.0-build376-door-sync.jar`, 3 524 390 B, sha256
  `1f65b745e30eff5385ce6183f745a66f02f814eff8b4d154577fb593a350b78a`, Drive `1GW792jzhEcn00UbKFSLSOhAdSW2Q55xf`.
- Zip `SettledLands-Build376-DoorSync-Source.zip`, 829 244 B, sha256
  `835a4ec5e21810e1d64a44d0b547b4343e6d7f7ed258c0ad839b33390b047b6d`, Drive `17YOZhBI6gba-BBkbixPO_TS7lCORFQqC`.
- README `README-Build376-RU.md`.
- Problem it solves: 375 refused *every* door click on the client, so an unlocked door was opened only by the
  server's block update and the two halves animated apart ("top half opens late").
- Mechanism: payload `settledlands:door_sync` (`DoorSync375`), registered client-bound in `DoorNet375`
  (`playToClient`, mod bus). `C|` clear, `S|k1;k2;` snapshot, `A|...` snapshot chunk (CHUNK=7000), `L|key`/`U|key`
  delta. Snapshot on `PlayerLoggedInEvent`; delta on lock/unlock to `server.getPlayerList().getPlayers()` via
  `PacketDistributor.sendToPlayer`. Client refuses a click only when `synced()==false` or `isLocked(level,pos)`
  (block + ±1 Y); otherwise vanilla predicts.
- No unproven API: `sendToPlayersInDimension` was dropped for the loop the base mod already uses;
  `ByteBufCodecs.STRING_UTF8` is the one new member and is allowlisted in `tools/check_mixin_class.py` with the
  1.21.1 javadoc evidence (`static final StreamCodec<ByteBuf, String>`).
- 23 gates green, `verify_jar --base 375` PASS (0 removed members, 0 new external refs).
- Not confirmed in game: the network layer was verified against published NeoForge 1.21.1 sources + 1.21.1
  javadoc, not a running client. Fallback if registration fails is the 375 strict behaviour (safe but laggy).

## Build 377 - the lock panel (shipped)

- Jar `settledlands-0.8.0-build377-door-hack.jar`, 562 entries, sha256
  `a827ac8e876848cb7b8b60856d3be648e5b09db13a2edc282b2f35431fe5b020`.
- Zip `SettledLands-Build377-DoorHack-Source.zip`, 458 entries, sha256
  `d92a685db7df0919d97a8c0eb1c608cf2cb8547e3d3128b9b9d7d1cc0aac83b2`.
- README `README-Build377-RU.md`, patch script `tools/patch_build377.py` (base 376).
- Feature: clicking a locked door without the key opens `HackPanel377` (a 1.21.1 `Screen`): 4-10 random
  number-row keycaps, all must be held at the same moment (`InputConstants.isKeyDown` polled per frame),
  15 s per attempt, timeout/Esc change nothing. Success sends `settledlands:door_hack` (C2S, single string
  = the store key) and the server opens the door if it is still locked and the player is within 6 blocks.
  The lock is not removed; `DoorGuard371` skips a door for 8 s after a crack (`DoorHack377.cracked`).
- Classes: replaced `DoorEvents368`, `DoorNet375`, `DoorGuard371`; added `DoorHack377`, `HackPanel377`.
- Stubs widened (compile-only): `Screen` (width/height/font/title/init/render/renderBackground/keyPressed/
  keyReleased/isPauseScreen/onClose/removed), `GuiGraphics` (Component overloads), `Minecraft#screen`/
  `getWindow`, `InputConstants`, `Window`, `IPayloadContext#player/enqueueWork`,
  `PacketDistributor#sendToServer`, `PayloadRegistrar#playToServer`.
- 23 gates green, `verify_jar --base 376` PASS, 0 new external refs.
- Sandbox note: this environment had no JDK and an old checkout; `pip install --break-system-packages
  jdk4py` restores `/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime/bin/java`, and
  `git fetch --depth=1 origin arena/5618404a-mod && git reset --hard FETCH_HEAD` restores the branch tip.
- Not confirmed in game: panel look, key ergonomics, and the whole C2S path.
- Drive copies (public): jar `1q4tvRs1Qx0jfqq53fzjc094RSAFCUAdw`, zip `1J28mVCjriZfBP2edP4WdYkwnV-qTwbT7`.
- Commit `aa375d1` (jar + README + zip + SHA256SUMS-build377.txt + patch script).

## Build 378 - letters and a rolling key (shipped)

- Jar `settledlands-0.8.0-build378-door-letters.jar`, 562 entries, sha256
  `1028ec9f6d584e8d06f3a2b5981e5698d0a43b7593584aa4f8c0d9e189118385`.
- Zip `SettledLands-Build378-DoorLetters-Source.zip`, 459 entries, sha256
  `414721e821c302bc47a9413bde822108217ece51bb3c882b21e0c932708a3998`.
- README `README-Build378-RU.md`, patch script `tools/patch_build378.py` (base 377, REPLACED 5 classes,
  ADDED none - note: `srcdir` must point at `out378`).
- Changes the author asked for: the keycaps are letters (`LETTERS` = Q W E R T Y U I O P, GLFW
  81/87/69/82/84/89/85/73/79/80) instead of digits; the clock no longer resets the attempt, every
  `ROLL_MS` = 5000 ms one random key changes to a letter the panel is not showing, and held keys stay
  held (the replaced button simply goes dark). Bar = time to the next roll, flash "клавиша сменилась".
- Gate `door_lock_check` now also requires: `LETTERS` present / `DIGITS` absent, `ROLL_MS` present,
  the swap message present, the old "не успел - кнопки сброшены" reset absent. 377 fails all four.
- All gates green, `verify_jar --base 377` PASS, 0 new external refs.

## Build 379 - the hack path reports itself (shipped)

- Jar `settledlands-0.8.0-build379-door-hack-report.jar`, sha256
  `39c09f8ac2f7be588eeb0a187fcf149d62a4dd3d7dfbdd7f366abc6db7293d66`; zip
  `SettledLands-Build379-DoorHackReport-Source.zip`, sha256
  `416b5a13ceb3daf2133474e171ec1bc3f38c01f6be7ca9804eea0b40dd1862b9`; README `README-Build379-RU.md`;
  patch script `tools/patch_build379.py` (base 378).
- The author reported "in the previous version the door did not open" **without a usable log**: the
  attached `latest-11.log` crashes at mod loading on `createrailwaysnavigator` (ModBlocks.<clinit>),
  before any world loads, and no uploaded log contains a single `door:` line.
- Two changes: (1) `DoorHack377.read` no longer requires the dimension half of the key to match - the
  position is read on its own and a spelling difference is only logged (doors inside Sable/ImmPtl
  sub-levels and pocket dimensions would never open before); (2) the whole path is instrumented -
  `door_hack arrived`, the result of `setOpen` (`changed=`, `open now=`), `was asked to open and did not`,
  panel `opened` and `sending the hack`.
- Gate: `door_lock_check` requires those four log strings and forbids the strict dimension check. 378
  fails all five rules.
- All gates green, `verify_jar --base 378` PASS, 0 new external refs.
- Open question for the next turn: which `door:` line appears in the author's log; if the panel never
  opens, look at "the lock panel could not be opened" (a throw inside `HackPanel377.open`).

## Build 380 - the hack handler on the main thread (shipped)

- Jar `settledlands-0.8.0-build380-door-hack-watch.jar`, sha256
  `ae7e057f5fdd215a89407f6a12c2845a2f2b3687073034474734024c58180c7d`; zip
  `SettledLands-Build380-DoorHackWatch-Source.zip`, sha256
  `2ee255aeae773e17d5257aa4843bd825b91e0e9dc34e861b1fd353717f365f13`; README `README-Build380-RU.md`;
  patch `tools/patch_build380.py` (base 379, REPLACED 5, ADDED `DoorHackWatch380`).
- **Hard lesson (do not repeat):** a stub signature that "looks right" compiles a broken invokeinterface.
  `IPayloadContext#enqueueWork` is `(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;`, not
  `void`. The right way to learn a NeoForge signature: dump the ref from the base jar's constant pool
  (tools logic in this session's notes: parse_class + CP Methodref scan) instead of trusting the mirror.
- The author is on 378/379: the panel is what they wanted, the door still does not open, and no log was
  sent. 380 therefore (a) runs the C2S handler through `context.enqueueWork(...)` exactly like the mod's
  working payloads, (b) re-sends a solved panel once after 1.2 s and gives up with a message after 4 s,
  (c) shows every server refusal to the player in the action bar, in Russian, so the failure point is
  visible without the log.
- New client-only class `DoorHackWatch380` (`@EventBusSubscriber(modid="settledlands", value={Dist.CLIENT})`,
  `ClientTickEvent.Post` -> `DoorHack377.watch()`); stubs added: `ClientTickEvent`, `ClientLevel`,
  `Minecraft#level`.
- All gates green, `verify_jar --base 379` PASS.
- Next step if it still fails: the action-bar line names the failing check; if the panel says nothing at
  all, the panel itself never solved (keyboard ghosting on the number/letter row is the remaining suspect).

## Build 381 - the door opens (DoorOpen) - 2026-11-06

- **The bug of builds 368-380 is found and fixed: the `setValue` descriptor.** The author's
  `logs/latest.log` from 380 finally named it:
  `NoSuchMethodError: BlockState.setValue(Property, Comparable)` thrown inside `DoorHack377` and logged
  as `door: a solved panel could not be applied: ...`. In 1.21.1 `setValue` is declared on
  `net.minecraft.world.level.block.state.StateHolder<O,S>` as
  `<T extends Comparable<T>, V extends T> S setValue(Property<T>, V)`; `S` is unbounded, so the erased
  return is **Object**, not BlockState. Our api-src mirror declared `BlockState setValue(Property<T>, T)`
  and emitted an unresolvable `invokevirtual`. Every door open since 368 threw, and each caller's
  try/catch swallowed it - hack, NPC guide doors (368) and the guard (371) all go through
  `Doors368.setOpen`.
- Fix: `build303-src/api-src/.../block/state/BlockState.java` now declares raw
  `public Object setValue(Property property, Comparable value)`; `Doors368.setOpen` does
  `Object next = state.setValue(part, value); if (!(next instanceof BlockState)) continue;` then
  `level.setBlock(part, (BlockState) next, 10)`. Verified with `tools/disasm.py`: the ref is
  `setValue (L.../properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;`.
- Gate hardening: `door_lock_check` now asserts that descriptor (name-only checking passed for ten
  builds while the shape was wrong) and it FAILS on the 380 jar as expected; `check_mixin_class`
  allowlist entry updated to the Object descriptor.
- Ship: `settledlands-0.8.0-build381-door-open.jar` (563 entries, only `Doors368.class` replaced, 3302 B),
  version `0.8.0-build381-door-open`, sha256
  `e0c3ffdbec51e8f42270fed9193bb333d1a61b3d70383c5ba43ae2c3d484d4dd`; zip
  `SettledLands-Build381-DoorOpen-Source.zip` (465 entries), sha256
  `92defdaf947d92ecd10aa4c7802313ddc680120f8ac845f7624c2c60832fe7f9`; README
  `README-Build381-RU.md`; patch `tools/patch_build381.py` (base 380, REPLACED 1, ADDED none).
- All gates green, `verify_jar --base 380` PASS.
- **Lesson to keep:** the mod's own bytecode is no reference for vanilla signatures (it never calls
  `BlockState#setValue`); hand-written mirrors must be checked against the 1.21.1 javadoc
  (lexxie.dev `StateHolder`) *and* the emitted descriptor dumped with `disasm.py`.
- Unconfirmed in game: that 381 actually opens the door, that the guard re-closes it after 8 s, and that
  NPC doors now open/close - all three depend on this one call and were dead for the same reason.

## Blocker after 381: the pack's Create addons crash at startup (not our mod)

- `latest-13.log` and `latest-14.log` both die in mod construction, on **different** mods:
  `createdeco-2.1.3` (`SpriteShifts.<clinit>`) and then
  `createrailwaysnavigator-1.21.1-beta-0.10.0-C6` (`ModBlocks.<clinit>`), with the **same** stack:
  `ponder-1.0.82 net.createmod.catnip.render.StitchedSprite.<init>` -> `HashMap.computeIfAbsent` ->
  `ConcurrentModificationException`. The loser rotates per launch => it is a cross-thread race on
  catnip's static sprite map, not one bad addon, and not our jar (we replace one door class that is
  not touched at construction).
- Mod list is identical in 13 and 14 (220 jars); vs the working 380 run (`latest-12.log`) the only
  diffs are our jar and `purescale` 1.2 -> 1.3.0. So 380 got lucky, 381 got unlucky twice.
  **381 is therefore still unverified in game** - no `door:` lines exist in any log yet.
- Workaround to make the pack launch (verified in FML source, branch 1.21.1):
  `ModWorkManager.parallelExecutor()` = `new ForkJoinPool(MAX_THREADS, ...)` with threads literally
  named `modloading-worker-N`, and `MAX_THREADS` = `maxThreads` in `config/fml.toml`
  (`<= 0 -> Runtime.availableProcessors()`). Setting `maxThreads = 1` serialises mod construction,
  which makes the race impossible. Revert to `-1` after the test.
- Do not "fix" this inside Settled Lands: the target class lives in ponder, we have no copy of that
  jar here, and an untestable mixin into a third-party class is worse than the crash.
## Build 382 - a door carries its own graph (DoorGraph) - 2026-11-06

- **381 verified in game by the author** ("все прекрасно работает") - the `setValue` descriptor fix holds.
- Request: "при тапе ПКМом жезлом настройки нпс по двери - создастся собственный граф двери, где по
  дефолту будет нода с 2 ползунками (Кол-во чисел, Скорость) и тумблером открыта/закрыта".
  Author's answers: the toggle means «Закрыта» = the door opens **only** by solving the panel,
  «Открыта» = an ordinary door again; the sliders are keys 4..10 and seconds 1..10; palette = the door
  node only.
- Architecture (mirrors build 351's block graph, deliberately): `DoorGraph382Store` (SavedData
  `settledlands_door_graphs`, key = dimension@lower half, `Doors368#parts` picks the lower half - no
  state property needed), `DoorGraph382` (id range -2_000_200_000..-2_000_299_999, `DOOR_TYPE = 40`,
  node fields `randomMin` = keys, `timerInterval` = seconds, `timerRepeat` = sealed, `defaultGraph`,
  `open`, `save`, `applySeal`), `DoorGraph382Events` (wand on a door, `EventPriority.HIGHEST`,
  cancels), `DoorGraphClient382` (opens the stock screen, card text), and five mixins:
  `NpcDoorSave382Mixin` (common, **first**), `NpcDoorOpen382Mixin`, `NpcDoorGraph382Mixin` (one-row
  palette + one tab), `NpcDoorCard382Mixin` (drawNode RETURN paints «Дверь» + three lines),
  `NpcDoorRows382Mixin` (widgets RETURN: two `NpcUi#slider` rows + a toggle button).
- **Sealed == the lock of build 368** (`DoorStore368.lock/unlock`), so refusal, unbreakable door, the
  8 s guard and `DoorSync375` all follow with no new rule. The panel's two numbers travel over the same
  `DoorSync375` string payload: `P|key=count:seconds;...` (and `D|` clears), sent on login and after
  every graph save; `HackPanel377` asks `DoorSync375#settings` and falls back to random 4..10 / 5000 ms.
- Edits to older builds: `BlockTriggerEvents351` returns early on a door (a door is not a block),
  `BlockMenu363` gained a `door` mode (one-row panel, `fit(menu, canvas, doorMode)`),
  `BlockMenu363Mixin` measures/paints for both editors, `DoorEvents368` (a) does not open the panel with
  the wand in hand, (b) refuses the wand click on the client too (no ghost-open door), (c) ignores an
  already-cancelled click, (d) sends the settings on join.
- **Do not shadow inherited Minecraft members.** First attempt shadowed `Screen#addRenderableWidget`;
  `verify_jar` refused it and the gate's own doc records that build 359 tried exactly that and every
  graph screen died. Fixed by calling it through the screen (the stub declares it public) - the call
  site lands inside the merged class.
- Ladder of lessons that keep repeating: check the emitted descriptor/ref with `tools/disasm.py`, keep
  `DoorBlock.HALF`/`DoubleBlockHalf`/`BlockPos#below` out of the jar (they are "known from nothing in
  the base jar" to `verify_jar`), and let `door_graph_check.py` fail first on the previous jar.
- Ship: `settledlands-0.8.0-build382-door-graph.jar` (572 entries, 6 replaced, 9 added, 43 common /
  46 client mixins), sha256 `605d477c3b49ffcc390bcf84eaae685fe9ee42a0ba44b2398683bb3fb5a742a4`; zip
  `SettledLands-Build382-DoorGraph-Source.zip` (479 entries), sha256
  `e1c76d12842a4bbcb98fb56dd2ae9f4cbb8ebb351990592199c2c6dfc3ffa6c4`; README
  `README-Build382-RU.md`; gate `tools/door_graph_check.py`; patch `tools/patch_build382.py`
  (base 381, `srcdir out382`).
- All gates green, `verify_jar --base 381` PASS (0 new external refs).
- **Unconfirmed in game**: the three text lines inside the node card (positions), the two sliders in the
  inspector, the one-row palette, and that a save really lands in the store and re-locks the door.


## Build 383 - the door graph, corrected to look like the block graph

The author opened 382 in game and sent a screenshot with «ты допустил точно те же ошибки что и при
создании графа блока, исправь все сразу». All three failures had already been solved for the block
graph in 359-362, so 383 is a port, not a new design:

- **Tabs.** `BlockNoTabs360Mixin` gained `settledlands$oneGraph(screen)` = `BlockTrigger351
  .isBlockEditorId(id) || DoorGraph382.isDoorEditorId(id)`; both injection points (the `@Redirect` on
  `button(...)` and the `widgets()` RETURN park) now ask it. The strip is parked off the screen, not
  hidden (`WidgetPark361.park`).
- **Inspector.** `NpcDoorRows382Mixin` rewritten: at `widgets()` RETURN it parks every stock row
  (`WidgetPark361.park`) and clears `labels`, sets `sidebarHeight = 0`, builds its own three rows
  (title «Дверь», two `NpcUi#slider` rows, the seal button) and then `sidebarHeight += CONTENT` (76).
  Every write is now `propertyChange(...)` - the 382 version wrote the node directly and called
  `changed()` itself, the exact bug that lost the block's name colour in 362.
- **Card.** `NpcDoorCard382Mixin` now cancels `drawNode` at HEAD for `DoorGraph382.isDoorNode` and
  hands the node to the new `npc/client/DoorCard382`, which paints border/body/header and four text
  lines inside `NpcGraphCanvas.NODE_W x NODE_H`, `pose.translate(canvas.screenX/screenY)` +
  `scale(zoom)`, wrapped in push/popPose + `DoorGraph382.fail`. Painting over the stock card (382) is
  what produced the pink «Случайность» box with clipped text.
- **No body panels.** `NpcDoorNoStats383Mixin` cancels `NpcStatsGraphUi#ensure` for a door and drops
  types 16/18 via `BlockName359.dropType`; `NpcDoorNoAi383Mixin` cancels `NpcAutoAiGraphUi255
  #ensure/#draw` and its `mouseClicked` **hands the click on** to `NpcStatsGraphUi.mouseClicked`
  (never drops it - the 361 lesson).
- **Server side.** `DoorGraph382.stripCards(graph)` drops types 16/18 and repairs `entry` if it pointed
  at a dropped node; called in `open()` and in `save()`.
- Build: `tools/patch_build383.py` (base 382, `srcdir out383`, 4 replaced / 3 added, lang
  `gui.settledlands.door.hint`, client mixins 46 -> 48). New gate `tools/door_screen_check.py` (fails on
  382, passes on 383); `door_graph_check.py` updated to the 383 card contract (cancel + `DoorCard382
  .draw` + painter draws/transform/push-pose); `block_name_check.py` now reads the tabs hook together
  with `settledlands$oneGraph`.
- Ship: `settledlands-0.8.0-build383-door-graph-fix.jar` (575 entries, 43 common / 48 client mixins),
  README `README-Build383-RU.md`, zip `SettledLands-Build383-DoorGraphFix-Source.zip`,
  `SHA256SUMS-build383.txt`. `verify_jar --base 382` PASS, 0 new external refs, 92 mixins.
- **Unconfirmed in game**: the parked tab strip on a door's editor, the three inspector rows' placement
  and scroll height, and the four card lines' pixel positions inside the node box.


## Build 384 - why a door's values were never saved (root cause found, fixed)

Author after 383: «Изменения в графе не сохраняются и в следствии не влияют на дверь». Root cause is in
the base mod's serialiser, `dev/settledlands/npc/NpcDialogueGraph#save()`:

    if (n.type == TIMER)  { v.putInt("TimerInterval", n.timerInterval); v.putBoolean("TimerRepeat", ...); }
    if (n.type == RANDOM) { v.putInt("RandomMin", n.randomMin); ... }

382/383 kept the door's three controls in exactly those fields; a door node is type 40, so the client's
`save()` sent a node with the field defaults (randomMin=1, timerInterval=10, timerRepeat=true), the
server stored that, and every save rewrote the door as 4 keys / 10 s / sealed. Nothing threw.

Fix (no new packet, no new mixin): `keys` -> `Node#varValue`, `speed` -> `Node#varCompare`, `sealed` ->
`Node#varOp` (1/0) - the three ints written/read for EVERY node type (`v.putInt("VarValue",...)` and
`n.varValue=v.getInt("VarValue")` are outside all type gates); `Node#copy()` copies all three, so undo,
copy/paste and the profile copies keep them. `varValue == 0` marks "never written" (a written count is
4..10) and the new `DoorGraph382#adopt(graph, sealed)` fills it with defaults + the door's real lock,
called from `open()` and from `save()` so a 382/383 graph is repaired instead of written blank (and a
closed door is not unlocked by opening its graph). `FIELD_NOTE` now documents the new mapping, and
`DoorGraph382.FIELDS` holds the three names.

New gate `tools/door_save_check.py`: fails if the door node uses a type-gated field again; re-reads the
serialiser in `reference/decompiled-build132/.../NpcDialogueGraph.java` (committed) and asserts the three
tags are written AND read without `if (n.type`, and that `TimerInterval`/`RandomMin` are still gated (if
that ever changes, the gate says so). Verified: fails on 383, passes on 384. `door_graph_check.FIELDS`
moved to the new trio.

Ship: `settledlands-0.8.0-build384-door-graph-save.jar` (575 entries, 1 replaced, 43 common / 48 client
mixins), README `README-Build384-RU.md`, zip `SettledLands-Build384-DoorGraphSave-Source.zip` (486
entries), `SHA256SUMS-build384.txt`, patch `tools/patch_build384.py` (base 383, `srcdir out384`).
`verify_jar --base 383` PASS, 0 new external refs, all 24 `*_check.py` green.

**Unconfirmed in game**: that the values now come back after Ctrl+S + reopen and after a world reload
(steps 3 and 6 of the README). The cause is proven from the shipped decompiled source; the round trip
itself is not executed anywhere here.

## Build 385 - the panel takes the keyboard, and the door node gets a difficulty

Two author requests: «кнопки блокировали пользовательский ввод… блокировать все кроме ESC» and «в ноду
новый пункт "сложность": 1 - qwertyuiop[], 2 - +asdfghjkl;', 3 - +zxcvbnm,./».

- **Input lock.** `HackPanel377#keyPressed` used to answer `true` only for its own keys and let the rest
  fall through to `Screen#keyPressed` → the game's key mappings (R reloaded Iris shaders). Now every key
  but ESC returns `true` on both press and release; ESC goes to `super` (which closes the panel only).
  Key state is still read with `InputConstants.isKeyDown`, so the lock does not change the minigame.
  Honest limit: only keys routed through the screen are blocked; a mod reading raw GLFW events would not
  be.
- **Difficulty.** Fourth node value, `Node#checkTarget` (written by the stock serialiser for every type,
  read back with a default; only «Проверка» nodes range-check it). `DoorGraph382.difficulty/setDifficulty`
  clamp 1..3, `settings()` now returns 4 ints, `DoorSync375` sends `key=count:seconds:difficulty` and
  parses a missing third field as 1. `HackPanel377` has `ROW_TOP` (81,87,69,82,84,89,85,73,79,80,91,93),
  `ROW_HOME` (65,83,68,70,71,72,74,75,76,59,39), `ROW_BOTTOM` (90,88,67,86,66,78,77,44,46,47) -
  `pool(difficulty)` and both `pick(...)` and `roll()` use it. Third inspector slider (`CONTENT` 76 -> 98)
  and a card line «Сложность: N (qwerty + asdf)»; card lines re-pitched to 28/42/56/70/84 in the 100 px
  box, and `gui.settledlands.door.hint` now reads «Ввод заблокирован · ESC — выход».
- Gates: new `tools/hack_input_check.py` (fails on 384, passes here); `door_save_check.GOOD` grew
  `checkTarget` (+ tag map); `door_screen_check.CARD_KEYS` grew the difficulty/rows keys (digit suffix is
  checked as its prefix in the class and as three keys in both lang files); `door_lock_check` accepts the
  three row fields instead of the single `LETTERS`.
- Ship: `settledlands-0.8.0-build385-door-hack-difficulty.jar` (575 entries, 5 replaced, 43/48 mixins),
  README `README-Build385-RU.md`, zip `SettledLands-Build385-HackDifficulty-Source.zip` (488 entries),
  `SHA256SUMS-build385.txt`, patch `tools/patch_build385.py` (base 384, `srcdir out385`).
  `verify_jar --base 384` PASS, 0 new external refs, all 25 `*_check.py` green.
- **Unconfirmed in game**: that R/F3/E really do nothing while the panel is up (an Iris version that
  reads raw GLFW events would still fire), and that difficulty 2/3 visibly widen the board.

## Build 386 - четыре ноды у графа двери, сигнал «Открытие», скорость до 20 с (shipped)

- **Speed.** `DoorGraph382.MAX_SPEED` 10 -> 20; nothing else moved (the panel, the log line and the store
  take the seconds as they come).
- **Four node types of the door graph**: `DOOR_TYPE` 40 (brown -3725286), `EVENT_TYPE` 41 «Открытие»
  (amber -3177856), `WHO_TYPE` 42 «Кто открыл» (blue -12973611), `RADIUS_TYPE` 43 «В радиусе» (green
  -13315175). `PALETTE = {40,41,42,43}`, `labelKey(type)`, `colour(type)`, `isDoorGraphNode`,
  `typeForRow`/`rowForType`, `isEventNode/isWhoNode/isRadiusNode`, `applyDefaults(node)`,
  `radius/setRadius` on `checkRadius` (1..32 via `MIN/MAX_RADIUS`, default 8; 0 = never written),
  `opener/setOpener` on `node.text` (trim 32), `eventNode(graph)` = the first «Открытие» node.
- **Palette.** `BlockMenu363` door mode is four rows (`DOOR_ROW_NUMBERS = {0,1,2,3}`, old `DOOR_COLOUR`
  gone): `doorType(compact)` -> `DoorGraph382.typeForRow`, label/colour/stockRow delegate by type, stock
  rows still parked for every door-graph type. `NpcDoorGraph382Mixin` resolves the type from the row,
  calls `applyDefaults`, writes `gui.settledlands.door.added_node` with the type name.
- **Painting/inspecting.** `DoorCard382` paints all four types (own title, accent, per-type lines; the
  door keeps the five old lines at 28/42/56/70/84). `NpcDoorCard382Mixin` hooks `isDoorGraphNode`.
  `NpcDoorRows382Mixin` builds per-type rows: door = 4 stock rows, radius = new slider
  `settledlands$radius`, who/event = read-only labels.
- **The signal - `door/DoorGraph386`** (new class): `fire(player, level, pos)` reads the door's graph from
  `DoorGraph382Store`, starts at `eventNode`, walks the stock `next` links with `MAX_STEPS = 32`, a
  visited set (a node met twice ends the walk), a 400 ms per door+player guard (one click per hand),
  writes the name into every «Кто открыл» node (`setOpener` + `store.put`, so build 384's save path
  carries it) and **stops** at a «В радиусе» node the opener does not pass. `within()` measures from the
  player to the door's lower half. Every failure is swallowed with one line per class - the door is
  already open.
- **Both real openings call it**: `DoorEvents368.onRightClick` (unlocked, empty hand, door shut right
  now) and `DoorHack377.handle` (`if (nowOpen) DoorGraph386.fire(...)`).
- **Serialisation**: nothing to add - the stock `save()` writes `Text`, `CheckRadius` and `Next` for
  every node type unconditionally, so the opener and the radius survive save -> server -> world file.
- **Lang (ru+en)**: `door.event|who|radius|event_line1..4|who_line1|who_line3|who_none|who_last|
  radius_line|radius_line2|radius_line3|added_node`, palette line re-pitched to the four nodes.
- Gates: new `tools/door_signal_check.py` (fails on 385, passes here: constant values 40..43 and 1..20,
  palette asks the row for its type and applies defaults, `fire` called from both open paths, bounded
  walk + visited set + radius gate + opener write, card and inspector know all four types, every line in
  both lang files). All other 25 `*_check.py` unchanged and green.
- Ship: `settledlands-0.8.0-build386-door-nodes.jar` (576 entries, 1 added + 8 replaced, 43/48 mixins),
  README `README-Build386-RU.md`, zip `SettledLands-Build386-DoorNodes-Source.zip` (491 entries),
  `SHA256SUMS-build386.txt`, patch `tools/patch_build386.py` (base 385, `srcdir out386`).
  `verify_jar --base 385` PASS, 0 new external refs.
- **Unconfirmed in game**: the four-row menu geometry, the height of the three new cards, the blue node
  refreshing in an *open* editor (the graph is saved on the server; reopening shows it), and sneak+click
  not firing the signal (no sneak test exists in the compile mirror, so the signal is tied to «empty hand
  + door shut right now»).
- **Not built on purpose**: a stock «if» for a door - there is nothing for it to compare yet (see the
  README's last section).

## Build 387 - the panel's input block ends when the hands do, not when the door does (shipped)

- **The bug**: build 377/378 did `solved = true; DoorHack377.send(pos); this.onClose();` on the solve
  frame - the door opened and the 4..10 keys still held fell through to the game, which is the accident
  the blocking existed to prevent.
- **The fix** (`door/HackPanel377`): solving only *accepts* the hack. `send(pos)` still goes out at once
  (the door opens when the lock is solved); the panel repaints to «ВЗЛОМ ПРИНЯТ · замок снят, дверь
  открыта», keeps swallowing every key, shows «ещё зажато: N», and closes on the frame nothing is held.
- **How "nothing is held" is known** (two sources, each alone lies): field
  `pressed = new LinkedHashSet<Integer>()` filled in `keyPressed`, cleared in `keyReleased`;
  `allReleased()` prunes that set against `InputConstants.isKeyDown` every frame (a release while the
  window is unfocused may never arrive) and also requires `held() == 0`, which covers a key already down
  when the panel opened (never sends a press). `stillDown()` counts pool keys once (via `held()`) plus
  the extra pressed ones.
- **ESC**: before the hack it is `super.keyPressed` as before (immediate close, door stays shut); after
  it, swallowed unless held `ESC_HOLD_MS = 1200` ms (`escDownAt`), so a tap cannot undo the block and a
  stuck key cannot trap the player. `keyReleased(ESC)` resets `escDownAt`.
- **Clock**: `roll()` still runs only while `!solved`, so a solved panel no longer swaps keys.
- Gates: new `tools/panel_release_check.py` (fails on 386 with «HackPanel377 has no allReleased()»,
  passes here: `pressed` is a `java.util.Set`, add in keyPressed / remove in keyReleased, `allReleased`
  calls `isKeyDown` + `Set.isEmpty` + `held`, and in `render` the `onClose` comes **after**
  `DoorHack377.send` **and after** `allReleased`; ESC timed through `escDownAt`; the two Russian lines
  exist). All other 26 `*_check.py` green.
- Ship: `settledlands-0.8.0-build387-panel-release.jar` (576 entries, 1 replaced: HackPanel377,
  43/48 mixins), README `README-Build387-RU.md`, zip `SettledLands-Build387-PanelRelease-Source.zip`
  (493 entries), `SHA256SUMS-build387.txt`, patch `tools/patch_build387.py` (base 386, `srcdir out387`).
  `verify_jar --base 386` PASS.
- **Unconfirmed in game**: the centred «ВЗЛОМ ПРИНЯТ» layout inside the unchanged 138 px panel, the
  accuracy of the «ещё зажато» counter against real fingers, and that no release reaches the game after
  the panel closes.
- **Not done on purpose**: mouse buttons are not blocked (the panel never blocked them; a screen already
  keeps clicks out of the world). Offered to add if asked.

## Build 388 - a door's graph is a stock graph (shipped)

**User ask:** «мне не нравится реализация нод в графе двери, сделай реализацию как в других графах»;
chosen option «both» = stock node types first, stock drawing/palette/inspector with it - shipped as one build
because the two halves share the same files.

- **Types**: only `DoorGraph382.DOOR_TYPE = 40` (the door card, the role `BlockName359.TYPE = 18` plays for a
  block) remains. 41/42/43 (EVENT/WHO/RADIUS), `MIN/MAX/DEFAULT_RADIUS`, `PALETTE`, `labelKey`, `colour`,
  `isEventNode/isWhoNode/isRadiusNode`, `radius/setRadius`, `opener/setOpener`, `eventNode`, `MAX_OPENER` are
  all gone. New: `typeForRow`/`rowForType`/`paletteRows`/`allowed` delegate to `BlockTrigger351`, and
  `migrate(graph)` drops 41-43 nodes (called from `DoorGraphClient382.graphOf`).
- **Palette**: `BlockMenu363` has no door list any more - `rows()`/`label()`/`colour()`/`stockRow()` are the
  stock ones for both editors, `doorType(compact) = BlockTrigger351.typeForRow(stockRow(compact))`, and the
  now-unused `DOOR_ROWS`/`DOOR_ROW_NUMBERS` are deleted. `fit(menu, canvas, doorMode)` keeps its 3-arg shape
  for call sites; the flag changes nothing.
- **Runner**: new `door/DoorSignal388` - `fire(player, level, pos)` resolves the store/graph, picks the start
  with `startNode()` (the node marked «Начало», never the door card, else the lowest-id stock node), guards
  400 ms per door+player, and hands the chain to **`BlockTrigger351.fire`** with
  `new BlockTalk358.Where(level, key, x, y, z)` - the very interpreter the block graph uses. So «Триггер» +
  `%player%` is the "who opened" output, «Переменная» moves `NpcPoints`, «If» branches, «Реплика»/«Кнопка»
  speak in the stock window with the door as speaker, «Катсцена» plays. `DoorGraph386` deleted from the jar.
- **Hooks**: `DoorEvents368` (empty hand, shut door) and `DoorHack377` (`nowOpen`) call
  `DoorSignal388.fire`.
- **Card/inspector**: `DoorCard382` paints only the door card (5 lines, clipped, block-name-card styling);
  `NpcDoorRows382Mixin` parks stock rows **only** for `isDoorNode` and builds the 3 sliders + toggle; stock
  nodes keep the stock inspector untouched.
- **Lang**: `gui.settledlands.door.palette` re-pitched to «У двери те же ноды, что у блока» /
  "A door offers the same nodes as a block". No other new keys.
- **Gates**: `door_signal_check.py` (386 design) deleted; new `tools/door_stock_check.py` (fails on 387,
  passes here). `door_graph_check.py` palette assertion rewritten for the stock palette. All 27 `*_check.py`
  green, `verify_jar --base 387` PASS (576 entries, 1 removed + 1 added, 43/48 mixins, 0 new external refs,
  0 dangling refs).
- Ship: `settledlands-0.8.0-build388-door-stock-nodes.jar`, README `README-Build388-RU.md`, zip
  `SettledLands-Build388-DoorStockNodes-Source.zip` (494 entries), `SHA256SUMS-build388.txt`, patch
  `tools/patch_build388.py` (base 387, `srcdir out388`, `REMOVED` support used for the first time).
- **Known gaps stated in the README**: a «Реплика» in a door's graph opens the stock window but with a blank
  speaker name (the stock code reads a block's «Имя» card, type 18, which a door has none of); event nodes
  are stepped over by a walk exactly as in a block graph; the blue "who opened" node is now the stock
  `Триггер` with `%player%`.

## Build 389 - the door card's two outputs: «Импульс» (pulse on a hack) and blue «Цель» (shipped)

**User ask:** «сделай теперь из ноды "дверь" один выходной узел сигнала и синий узел цели. Узел сигнала
будет давать импульс если дверь взломать, синий будет передавать ссылку на того кто взломал дверь».

- **Types back, with a new meaning** (388's removals were reverted for 41/42, 43 stays dead):
  `SIGNAL_TYPE = 41` «Импульс» (amber -3177856), `TARGET_TYPE = 42` «Цель» (blue -12973611),
  `labelKey(type)`, `colour(type)`, `PALETTE_EXTRA = {41,42}`, `extraType(i)`, `isSignalNode`,
  `isTargetNode`, `isDoorGraphNode` = card|signal|target, `opener/setOpener` on `node.text` (≤32),
  `signalNodes(graph)` (id-sorted), `wireStarter(graph)` (card → signal → target, only missing wires),
  `MAX_OPENER = 32`.
- **Starter shape**: `defaultGraph` now produces card → pulse → target wired left to right, and sets
  `graph.entry = signal.id`. `withDoorNode` wires the starter **only** when it had to create the card in an
  empty graph; an existing graph is left alone (so a deleted «Цель» never comes back; 388-era graphs keep
  working because the hack falls back to `startNode`).
- **Pulse = hack only**: `DoorSignal388#hack(player, level, pos)` (renamed from `fire`) stamps the name into
  every target node, then fires `BlockTrigger351.fire` for **each** signal node (fallback: `startNode`), and
  `store.put(key, graph)` when it stamped anything (so the name survives the save). `DoorHack377` (solved
  panel) calls `hack`; `DoorEvents368` (plain click on an unlocked door) **no longer calls anything**.
- **Menu**: `BlockMenu363` door mode = the stock rows **plus** the two extra rows at the end
  (`count() = rows().length + (door ? 2 : 0)`, `doorType` → `DoorGraph382.extraType(compact - stockCount)`,
  label/colour from `DoorGraph382`). Block menus unchanged.
- **Cards**: `DoorCard382` draws the card (5 lines), «Импульс» (4 lines) and «Цель» (3 lines, name or
  «Ещё никто не взламывал»). `NpcDoorRows382Mixin` builds rows for all three: sliders for the card,
  read-only for signal and target (the target row shows the hacker).
- **Lang (ru+en)**: `door.signal`, `door.target`, `door.signal_line1..4`, `door.target_line1`,
  `door.target_last`, `door.target_none`, `door.target_line3`.
- **Gates**: `door_stock_check.py` extended (menu = block rows + exactly two own types; `hack` called from
  the solved panel and never from `DoorEvents368`; `setOpener` + `DoorGraph382Store#put`; `wireStarter` pushes
  41/42; cards read `labelKey`/`opener`; six new lang keys in both files). All 27 `*_check.py` green,
  `verify_jar --base 388` PASS (576 entries, 8 replaced, 43/48 mixins, 0 new external refs, 0 dangling).
- Ship: `settledlands-0.8.0-build389-door-signal-target.jar`, README `README-Build389-RU.md`, zip
  `SettledLands-Build389-SignalTarget-Source.zip` (495 entries), `SHA256SUMS-build389.txt`, patch
  `tools/patch_build389.py` (base 388, `srcdir out389`).
- **Unconfirmed in game**: the look of the two new cards in 100 px, that the starter shape does not overlap
  nodes of an old door (old doors are not touched), and that the target refreshes in an *open* editor.
- **Stated gaps**: pulse is hack-only (one line to add plain-open), 388-era graphs start at «Начало» until
  the author adds a pulse node, event nodes are stepped over, and a door's «Реплика» still has a blank
  speaker name (needs a door «Имя» card - offered as its own build).

## Build 390 - the pulse and the blue target really appear on every door (shipped)

**User report (with screenshot):** «сейчас визуально не видно узлы, так-же я не смог нащупать синий узел».
The screenshot showed a door card wired straight to «Реплика #1» with «НАЧАЛО» on it - because build 389
wired the starter shape **only for a brand-new graph**; a door whose graph existed since 382-388 never got
the two nodes, and in a 13-row palette they were hidden at the bottom of the scroll.

- `DoorGraph382#wireStarter` is now `boolean` and works on an existing chain: it puts the pulse in a free
  spot near the card (`freeSpot` tries (0,160), (260,160), (-260,160), (0,300), (260,0), (-260,0), with
  `occupied()` testing the 220x100 box), the blue target 260 px right of it (skipping occupied spots), then
  `signal.next = door.next`, `door.next = signal`, `graph.entry = signal.id` - the author's chain keeps
  running, after the pulse, and «Начало» moves onto it.
- **Drawn once per door, and remembered**: `DoorGraph382Store` persists a new `S` list of keys
  (`starterDone/markStarter`, `java.util.Set<String> starter`, saved in `save()` and read in `load()`), and
  `DoorGraph382.open()` calls `wireStarter` + `store.put` + `markStarter` when the flag is absent. A node the
  author deletes afterwards stays deleted.
- Verified in bytecode that `NpcDoorCard382Mixin` cancels on `DoorGraph382.isDoorGraphNode` (card|signal|
  target) and `DoorCard382` reads `labelKey`/`colour`/`opener` - so the three cards are painted; the problem
  was never the painter.
- Gates: no new gate needed (390 changes when the starter runs, not what it makes); `door_stock_check`
  still passes because `wireStarter` still pushes 41/42, and now also references `starterDone/markStarter`.
  All 27 `*_check.py` green, `verify_jar --base 389` PASS (576 entries, 2 replaced, 43/48 mixins).
- Ship: `settledlands-0.8.0-build390-door-outputs-visible.jar` (sha256 6a9633ad…92a485), README
  `README-Build390-RU.md`, zip `SettledLands-Build390-OutputsVisible-Source.zip` (496 entries),
  `SHA256SUMS-build390.txt`, patch `tools/patch_build390.py` (base 389, `srcdir out390`).
- **Unconfirmed in game**: that the auto-placed pulse spot is free on the author's door, that the two cards
  do not clip their lines, and that the stock editor's error check says nothing about «Начало» on our node.

## Build 391 - the two outputs sit in a clean row (shipped)

**User report (screenshot of a brand-new door):** «создал новую дверь, у нее какие-то непонятные ноды».
The nodes existed; the **layout** was the problem - 390 placed the pulse directly under the card, and the
stock canvas draws a wire between two boxes in the same column as a loop (out of the card's right port, down,
back across the card, into the pulse's left port). The blue target stood to the right with no wire at all.

- `DoorGraph382#rowSpot(graph, door, index)` replaces `freeSpot`: x = door.x + 280*index, y = door.y, moved
  down 140 per collision, so the pulse (index 1) and the target (index 2) form the row the canvas draws
  left-to-right without a loop.
- `wireStarter`: `signal.next = door.next` when the card already pointed somewhere (author's chain kept,
  after the pulse), otherwise `signal.next = target.id` - a brand-new door reads card → pulse → target in one
  line, so the blue target is visibly part of the chain. `door.next = signal`, `entry = signal` as before.
- `relayoutStarter(graph)`: a door wired by 390 (pulse at `(door.x, door.y+160)`, target at
  `(door.x+260, door.y+160)`, tolerance 8 px) is moved to the row **once**, on the next open; a node the
  author dragged elsewhere is never touched. `DoorGraph382.open()` calls it when `starterDone` is already
  true and saves if it moved anything.
- Gates: `door_stock_check` now also requires `rowSpot` + `relayoutStarter` and **forbids** `freeSpot` (fails
  on 390 with «no rowSpot()»). All 27 `*_check.py` green; `verify_jar --base 390` PASS (576 entries, 1
  replaced, 43/48 mixins, 0 new refs).
- Ship: `settledlands-0.8.0-build391-door-neat-row.jar` (sha256 d96c97c4…76a2), README
  `README-Build391-RU.md`, zip `SettledLands-Build391-NeatRow-Source.zip` (497 entries),
  `SHA256SUMS-build391.txt`, patch `tools/patch_build391.py` (base 390, `srcdir out391`).
- **Unconfirmed in game**: that the right-to-left wire really draws straight for this door, that the one-time
  move did not catch a node the author had nudged by <8 px, and that the target standing after the pulse is
  what he wants when his own chain already existed.

## Build 392 - the two outputs are sockets of the door card (shipped)

**User report (screenshot of the 391 graph):** «теперь все выглядит так. Ты похоже меня не понял. Ты создал
ноды импульс и цель, а я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде "дверь"». Builds 389-391 answered the
386/389 request with two extra cards; he wanted the two outputs to be sockets **on** the door card.

- The door card now carries both outputs itself: the round amber **execution socket** on the right edge at the
  stock output row (`NODE_H / 2`), which is the card's own `next`, so wires/drag/cut stay stock; and one row
  under it the blue **value socket** - `PursuitGraphNode272.isValueSourceNode` now includes the door card via
  `isDoorValueNode` -> `DoorGraph382.isDoorNode`, so pin, hit test, drag, cut, blue pass and count all work.
- The reference lives on the card: `DoorGraph382#setOpener` writes the hacker into `Node#text` (written for
  every node by the serialiser), `#opener` reads it; the card shows «Взломал: …», and `%target%` /
  `%target_kind%` / `%target_uuid%` in a «Реплика» wired to the blue socket resolve to the hacker
  (`BlockTalk358#line`/`withBody`, also used by the `BlockTrigger351#say` chat fallback).
- `DoorGraph382#foldStarter` folds the 389-391 nodes into the sockets: pulse chain -> `door.next`, readers of
  the target -> `door.id`, its name -> the card, «Начало» -> the card; `bypass`/`drop` rewire every list
  (next/falseNext/randomBranches/choices/triggers). Runs in `open()`, `save()`, `migrate()` and `DoorSignal388#hack`.
- `DoorSignal388#hack` starts at `DoorGraph382#chainStart` (the card's socket target), legacy «Начало» only when
  nothing is wired; `allowed()` no longer accepts 41/42; `BlockMenu363` is the stock rows only; card layout is
  five lines (count, speed, difficulty, state, hacker) with the socket labels in a 74 px strip on the right.
- Gates: `door_stock_check.py` rewritten for 392 (requires foldStarter/bypass/chainStart/opener/setOpener,
  isDoorValueNode, %target%; forbids wireStarter/signalNodes/relayoutStarter/rowSpot/freeSpot/PALETTE_EXTRA/
  extraType/isSignalNode/isTargetNode and 41/42 in allowed - fails on 391); new `door_fold_check.py` runs
  `tools/harness/DoorSockets392Probe.java` against the jar (fold of card->pulse->line + trigger reading target
  gives 3 nodes, door.next=line, trigger.varInputNode=door, entry=door, opener=Steve, second fold=false,
  coordinates and texts kept); `door_screen_check.py` drops `door.hint` from the card keys (the panel itself
  prints the ESC hold line), `block_talk_check.py` accepts `BlockTalk358#line` as the placeholder filler.
  All 28 gates green; `verify_jar --base 391` PASS (576 entries, 11 replaced, 43/48 mixins, 0 new refs).
- Ship: `settledlands-0.8.0-build392-door-card-sockets.jar` (sha256 64698415…dde87), README
  `README-Build392-RU.md`, zip `SettledLands-Build392-CardSockets-Source.zip` (500 entries),
  `SHA256SUMS-build392.txt`, patch `tools/patch_build392.py` (base 391, `srcdir out392`, 11 entries).
- **Unconfirmed in game**: how the two sockets and their labels look at his zoom (text clipped to 130 px, label
  strip 74 px), that the diamond does not cover the blue wire start (same point by construction), that the fold
  did not remove a node he had dragged and that was a 41/42 by type, and that `%target%` really shows the
  hacker's name in the dialogue window.

## Build 393 — `0.8.0-build393-door-chain-runs` (commit `6dcc960`)

- Report on 392: door card → «Реплика #1», card's blue socket → that line's value input, panel hacked
  (card said «Взломал: BORGER»), **no dialogue**: «ожидал, что после взлома (и после отпускания всех
  кнопок) начнется диалог, но ничего не произошло».
- Cause: `DoorSignal388#hack` handed the wired node to `BlockTrigger351#fire`, the **event-node** entry
  («В радиусе», «Время», «Таймер», «Цель»): it runs the node's own action and then walks on from its
  `next`. A «Реплика» is nothing but its step, so it was skipped — silently, nothing in the log.
- Fix: `BlockTrigger351#walk` returns the number of steps; new `BlockTrigger351#start(player, graph,
  startId, where)` walks from that node (0 on null / `startId < 0` / exception). `DoorSignal388#hack`
  uses `start`, accumulates `stepped`, logs `N node(s) stepped from the card's output socket` (or
  `«Начало»` for a graph of 388 or older). `fire` untouched (the 352 event pass needs it as it was).
- Gate `tools/door_chain_check.py`: structural (`start` called, `fire` not called from DoorSignal388,
  `start`+`walk` present) + behavioural — compiles `tools/harness/DoorChain393Probe.java` and requires
  `SINGLE steps=1 / CHAIN steps=3 / NOTHING steps=0 / SOCKET start=3 socket=line / EVENT fire=after`.
  393 jar passes, 392 jar fails (no `start`). `door_stock_check.py` needle moved to `start`.
- 29 gates green; `verify_jar --base 392` PASS (576 entries, 2 replaced, 0 new refs, walk listed as the
  one changed member — `void` → `int`). Jar sha256 `e79341ab…39b0`, 3583736 bytes on GitHub.
- Ship: README `README-Build393-RU.md`, zip `SettledLands-Build393-ChainRuns-Source.zip` (503 entries,
  sha256 `d82aa6b0…1a0b`), `SHA256SUMS-build393.txt`, patch `tools/patch_build393.py` (base 392,
  `srcdir out393`, 2 entries).
- **Unconfirmed in game**: that the «Реплика» window really opens (and over/after the hack panel — the
  window is sent on the hack, the panel closes on all-keys-up; `GuideDialogueScreen#open` sets its screen
  unconditionally, so whichever frame wins the window is on top), the camera aim on the door, `%target%`.

## Build 394 — `0.8.0-build394-door-event-pass` (commit `eb09234`)

- Report on 393: «у меня к примеру теперь не работает нода в радиусе, проверь и остальные ноды».
- Audit: `fire` is byte-identical to 392 (only a `pop` for walk's new return), so a block's «В радиусе» did
  not regress. The hole is the **door's graph**: since 392 it offers a block's whole palette, but the only
  thing that ran a door's graph was the card's hack (a chain from the socket). The four event nodes
  («В радиусе», «Цель», «Время», «Таймер») are not steps of a chain — they fire on their own — and
  `RadiusPass354` walked block graphs only. Dead on a door since 392.
- Fix: a place with a graph is a `Spot` — `BlockTriggerStore351.Entry` implements it, new
  `RadiusPass354$DoorSpot` is a door (key, position from `DoorStore368.positionOf`, `isDoorAt` check).
  `pass`/`check`/`timeEvents`/`timerEvents`/`rings`/`findTarget`/`nearestPlayer` take `Spot`; one copy of
  the rules for both. Tick no longer stands down when a world has no block graphs; `DoorGraph382Store#ofDimension`
  (linear, replaces sorted `keys()` ten times a second). A door failure is logged once and does not disable
  the block half (`doorFailureSeen`). `fired()` logs a door's event firing; blocks stay silent.
  `BlockTrigger351#isStep` (7 types) + `allowed = isStep || RadiusPass354.isEventType` (4 event types).
- Gate `tools/door_event_check.py`: structural (pass calls DoorGraph382Store#ofDimension/#graph/#isDoorAt +
  DoorStore368#positionOf/#dimensionOf, block path still `ofDimension`, `allowed` asks `isEventType`) +
  behavioural probe `tools/harness/DoorEvent394Probe.java`:
  `PALETTE rows=11 step=7 event=4 unknown=0`, `ALLOWED card=yes step=yes event=yes junk=no`,
  `EVENT radius=yes time=yes line=no card=no`, `UNWIRED event=no`, `DOOR event=yes`, `CHAIN start=1`,
  `FIRE event=yes`, `RING off=no on=yes`. 394 passes; 393 fails.
- `block_trigger_check.py` relaxed precisely: the store's "has something to fire" flag now lives in
  `$Entry` and is `RadiusPass354#hasFiringEvent` — the gate accepts either class, `isEventNode` or
  `hasFiringEvent`.
- 30 gates green; `verify_jar --base 393` PASS (578 entries, 0 new external refs; removed members are
  private methods whose signature widened `Entry`→`Spot`). Jar sha256 `120a57a6…2620`, 3587684 bytes.
- Source zip `SettledLands-Build394-DoorEvents-Source.zip` (506 entries, sha256 `7f833f6a…f879`).
- **Unconfirmed in game**: the tick itself (no world in the probe), the door ring's look, and whether his
  broken «В радиусе» was on a door at all (if on a block, the graph is the suspect: wire, radius,
  target kind, visibility toggle).

## Build 395 — `0.8.0-build395-door-stays-open` (commit `14d2f5e`)

- Report on 394: «чето немного странное происходит, дверь после взлома сама закрывается через секунд 5-10»,
  with `latest-15.log`. The log shows it twice to the millisecond: `door_hack` 14:52:57.623 → `the guard
  closed the locked door` 14:53:05.666; again 14:53:16.414 → 14:53:24.463 (8.04 / 8.05 s) = build 377's
  `OPEN_FOR_MS = 8000L` grace. Nothing wrong with 393/394 (log: `1 node(s) stepped from the card's output
  socket`, and 394's door event node fires: `a «Цель» node of minecraft:overworld@43,64,-248 fired for
  BORGER` every 10 s = his `checkCooldown` setting).
- Fix: `DoorHack377.CRACKED` is a `Set<String>` (no timestamps); `cracked()` (timestamped) **removed**, new
  `crackedOpen(Level, BlockPos)` = marked && isDoor && anyOpen && someoneNear(16 blocks), with `forget()`
  taking the mark of both halves back the moment the rule stops holding; the rule itself is the pure
  `keepOpen(marked, door, open, near)`. `KEEP_OPEN = 16.0D`. `DoorGuard371` calls `crackedOpen` instead of
  `cracked`; its promise (no locked door open in an empty room) and `NpcDoors368.guideOpened` untouched.
- Gate `tools/door_cracked_check.py`: no `OPEN_FOR_MS`/`8000` in DoorHack377, `crackedOpen` refs
  `Doors368#isDoor`/`#anyOpen` and `PlayerList#getPlayers`, guard asks `crackedOpen` and still
  `guideOpened`; probe `tools/harness/DoorCracked395Probe.java` walks all 16 combinations of `keepOpen`
  (`RULE keeps=1 of=16`, `TIMER keep=yes close=no`, `NULL doors=false`). 394 fails it.
  `door_lock_check.py` needle accepts `crackedOpen` or `cracked` (intent unchanged).
- 31 gates green; `verify_jar --base 394` PASS (578 entries, 0 new refs; removed: `cracked`, `OPEN_FOR_MS`,
  `CRACKED` as Map). Jar sha256 `89ad3247…1102`, 3588360 bytes. Zip
  `SettledLands-Build395-DoorStaysOpen-Source.zip` (509 entries, sha256 `1276f0bc…de34`).
- **Unconfirmed in game**: the server tick seeing the player (16 blocks), the door closing when the room is
  emptied. Offered: if he wants a hacked door to stay open even with nobody around, drop the "empty room"
  half next build.
- **Env note:** the sandbox was re-provisioned mid-session — the local checkout had been reset to `c72f0a3`
  and `/home/user/.venv-jdk` + `/tmp/venv` were gone. Recovered with
  `git fetch origin arena/5618404a-mod:refs/remotes/origin/arena/5618404a-mod && git reset --hard
  origin/arena/5618404a-mod` (remote was at afb8487, everything was pushed), and
  `python3 -m venv /home/user/.venv-jdk && .venv-jdk/bin/pip install jdk4py` re-created the JDK. `out*`
  dirs are untracked/gitignored and must be recompiled after such a reset.

## Build 396 — a chest gets the door's graph (shipped, `f7dc543`)

- The author: «Все прекрасно, теперь я хочу сделать такой-же граф для сундуков». «Такой же» = the door stack,
  moved onto a chest. Nothing invented: same editor, same canvas, same save, same palette, same interpreter.
- **New classes:** `dev/settledlands/chest/ChestGraph396` (own node type `CHEST_TYPE = 50`, own id range
  `ID_MAX = -2_000_300_000` / `ID_MIN = -2_000_399_999` - blocks are `-2_000_100_000..-2_000_199_999`, doors
  `-2_000_200_000..-2_000_299_999`; `defaultGraph`/`withChestNode`/`stripCards` = the 383 rule; two sockets:
  `chainStart` off the card's `next`, the opener in `Node#text`, the count in `Node#varValue` - the two fields
  the stock serialiser writes for every node, the 384 lesson), `ChestGraph396Store` (`SavedData`
  `settledlands_chest_graphs`, key `dimension@x,y,z`, `MAX_CHESTS = 4096`), `ChestGraph396Events`
  (wand at `HIGHEST` opens the editor before the 351 block hook; a main-hand click records the opener and
  counts the open, then the chain runs **when the chest's window closes** - `player.containerMenu !=
  player.inventoryMenu`, the pair `NpcEscort` compares; `OPEN_GRACE = 2`, `NO_WINDOW = 40`, `EXPIRE = 12000`),
  `npc/client/ChestCard396` (reuses `DoorCard382.socket`/`diamond`; three lines: count / opener / trigger),
  `npc/client/ChestGraphClient396` (stock `NpcDialogueGraphScreen`, the door's `openEditor` shape).
- **Mixins:** common `NpcChestSave396Mixin` (`GuideNpc.applyConfig`); client `NpcChestOpen396Mixin`
  (`NpcConfigScreen.open`), `NpcChestNoStats396Mixin`, `NpcChestNoAi396Mixin`, `NpcChestCard396Mixin`
  (`drawNode`), `NpcChestRows396Mixin` (five label lines, stock rows parked with `WidgetPark361`),
  `NpcChestPalette396Mixin` (stock rows, stock types, `pendingWireFrom` reconnect, one tab).
- **Edited (and why):** `RadiusPass354` + `$Spot`/`$DoorSpot` (+ new `$ChestSpot`, `addChests`,
  `chestFailureSeen`, `Spot#place()` so the log says `chest:`), `BlockMenu363Mixin` (`isChest` in
  measure/paint/wheel), `BlockNoTabs360Mixin` (`oneGraph` includes the chest - the tab strip must be gone,
  not just refused).
- **Stub work this build needed** (all mirrors proven by the base jar's own bytecode, not memory):
  `api-src/.../Player.java` + `containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;` and
  `inventoryMenu:Lnet/minecraft/world/inventory/InventoryMenu;` (the base jar's `ServerPlayer` refs give the
  exact descriptors), new `AbstractContainerMenu`/`InventoryMenu` mirrors, `PlayerList#getPlayer(UUID)`,
  `PlayerInteractEvent#getHand()`, and `stubs396-src` with `ChestBlock`/`BarrelBlock` (a chest is a chest).
  No `BlockEntity`/`MenuProvider` work was needed in the end - the chest's own window never has to be opened
  by this mod.
- **Wasm/no-new-API:** `verify_jar --base 395` PASS, 0 new external API refs (the first attempt used
  `ServerPlayer#inventoryMenu:AbstractContainerMenu` - gate D caught it; the real descriptor is `InventoryMenu`).
- Gate `tools/chest_graph_check.py`: id ranges disjoint from the door's, `CHEST_TYPE` outside 0..14 and != 40,
  `ChestGraph396` never calls `NpcDialogueGraph#connect` (outputs are sockets, not nodes), the card draws
  `DoorCard382.socket`+`diamond` in `PursuitGraphNode272.COL_VALUE` (inlined int - read from the pin class),
  `BlockTrigger351#start` + `NpcNet#sendRaw` reached, `ServerPlayer#containerMenu`/`#inventoryMenu` read,
  `RadiusPass354#addChests`, menus know the chest, 7 mixins registered, 15 lang keys in both files. Probe
  `tools/harness/ChestGraph396Probe.java` → `TYPE own=1 stock=false`, `CARD nodes=1 card=true entry=true`,
  `CHAIN unwired=none wired=1 stop=none`, `MEMORY count=3 opener=BORGER long=32`, `STRIP dropped=2 left=3
  entry=kept`, `ID in=true back=true stable=true`.
- **Ship:** jar `settledlands-0.8.0-build396-chest-graph.jar` (592 entries, 3613383 bytes, sha256
  `4927f6fb…8d07`), zip `SettledLands-Build396-ChestGraph-Source.zip` (528 entries, sha256 `0e30afc5…4dee`),
  `SHA256SUMS-build396.txt`, commit `f7dc543`, 32/32 gates green.
- **Not in this build (also in the README):** containers from other mods, a double chest = two graphs, no
  delete button (clear = delete all nodes + save, as for a door), no wand sparks (as for a door).
- **Unconfirmed in game:** the close-detection in a live session (containerMenu transitions), the card's
  look at real GUI scale, the chain's chain start on a wired chest.

## Build 397 - the chest's hack becomes a setting

- **The report:** «в графе сундука я не могу настраивать взлом». Build 396 gave the chest a card that
  could only be read; a lock the author cannot set is not a lock, so this build is the door card's four
  settings on the chest card, and the panel on the other side of them.
- **Where the lock lives:** on the chest node, in stock fields the save already keeps (rule of 384/392/397):
  `varCompare` = keys 4..10 (0 -> default 6), `varOp` = seconds 1..20 (0 -> 5), `checkTarget` = difficulty
  1..3 (0 -> 1), `pointsShared` = locked (written for every node). `varValue`/`text` stay 396's memory
  (opens / last opener) - the chest mapping deliberately differs from the door's. **No second store and no
  sync:** the card flag IS the lock, so saving the graph locks the chest and clearing it frees it.
- **New/changed classes:** `chest/ChestLock397` (values, `panel(Level,BlockPos)` -> `int[]{keys,sec,diff}`,
  `locked(Level,BlockPos)`, `near` with `REACH=6.0`, `applyDefaults`, `stateKey`/`stateKeyShort`, `cardAt`),
  `chest/ChestNet397` (+`$Panel`, `$Solved`; payloads `settledlands:chest_panel` / `settledlands:chest_hack`,
  registrar `"1"`, both handlers through `enqueueWork`; `solve()` re-checks lock/block/reach, then
  `ServerPlayer#openMenu(BlockState#getMenuProvider(...))` and `ChestGraph396Events#remember`),
  `chest/ChestGraph396` (`applyDefaults` on open/save/new), `chest/ChestGraph396Events` (`panel` refusal in
  `onUse` with `setCanceled` + `setCancellationResult(FAIL)`, new `onBreak` guard, new public `remember`),
  `door/HackPanel377` (`openForChest(key,pos,keys,sec,diff)`, `chestKey` field, title/captions switch, win
  answers `ChestNet397#sendSolved`), `npc/client/ChestCard396` (door card's five lines, state line in the
  accent colour), `npc/client/ChestGraphClient396#lines` (5 lines), `mixin/NpcChestRows396Mixin` (three
  sliders 4..10 / 1..20 / 1..3 + lock toggle, all through `propertyChange`, facts moved below).
- **API mirrors added this build:** `api-src/.../world/MenuProvider.java` (empty interface),
  `BlockState#getMenuProvider(Level,BlockPos):MenuProvider`, `ServerPlayer#openMenu(MenuProvider):OptionalInt`
  (both proven against the base jar's descriptors). Lang: 12 RU + 12 EN keys.
- **Gate:** `tools/chest_graph_check.py` extended (ranges read from `ChestLock397`'s constant pool, the four
  card fields touched, the inspector calling all four setters + `NpcUi#slider` + `propertyChange`,
  `applyDefaults` reached, both payloads registered, `openMenu`+`getMenuProvider` + `remember`, the click and
  break refusals, `openForChest`/`sendSolved`, 12 keys). New probe
  `tools/harness/ChestHack397Probe.java` -> `DEFAULTS keys=6 sec=5 diff=1 locked=false
  state=gui.settledlands.chest.state_open`, `SET keys=4 sec=20 diff=3 locked=true
  state=gui.settledlands.chest.locked`, `RANGE keys=10 sec=1 diff=1`, `RAW keys=6 sec=5 diff=1`,
  `STOCK untouched=true`, `NOTE fields=6`. The 396 probe still passes.
- **Ship:** jar `settledlands-0.8.0-build397-chest-hack.jar` (596 entries, 3627413 bytes, sha256
  `555c650f…e3a019`), zip `SettledLands-Build397-ChestHack-Source.zip` (533 entries, sha256
  `d3e01a4d…70cb9`), `SHA256SUMS-build397.txt`, `verify_jar --base 396` PASS (0 new external refs), 32 checks
  green + 5 verify gates.
- **Not in this build:** the lock does not stop a hopper or an observer under the chest, a double chest is
  two graphs, another mod's container is not covered.
- **Unconfirmed in game:** the panel arriving on a live client (title/layout), the vanilla window opening
  from a server `openMenu` on NeoForge 1.21.1, no hand swing on a refused click, the opener line landing on
  the card in the same tick as the window close.

## Build 398 — the second socket on both cards, live toggles, a chest that really opens

`settledlands-0.8.0-build398-card-sockets.jar` (599 entries, 549 classes, 3 633 688 B, sha256
`855d2073…f8d08`), base 397. Sources `SettledLands-Build398-CardSockets-Source.zip` (539 entries, sha256
`4909758f…4b84c`), `SHA256SUMS-build398.txt`, `README-Build398-RU.md`, patch `tools/patch_build398.py`,
classes from `build303-src/out398` (19).

- **The report, four lines:** (1) «тумблер "открыта/закрыта" визуально в правом окне изменяется только при
  перезаходе в граф. Так-же и в графе сундука»; (2) «после взлома сундука у нас не открывается инвентарь
  сундука»; (3) «нельзя вытаскивать из синего узла провод»; (4) «добавь ноде главной у сундука узел
  "взлом" а главной ноде двери узел "открытие"».
- **(1)** The word was written once, when the sidebar was built. Both `NpcDoorRows382Mixin.settledlands$seal`
  and `NpcChestRows396Mixin.settledlands$lock` now keep the button in `final Button[] self = new Button[1]`
  and write `setMessage(settledlands$sealWord(node) / settledlands$lockWord(node))` **inside the click**,
  after `propertyChange(...)`; the same helper runs once at build, so the two paths cannot disagree.
- **(2)** Three independent guards: `HackPanel377#onClose` closes only when `Minecraft#screen == this` (the
  vanilla window that replaced the panel was being closed by it); `settledlands$askAgain` resends the solved
  answer up to `ASK_TIMES=2` every `ASK_AGAIN_MS=1200` while `containerMenu == inventoryMenu`;
  `ChestNet397` calls `ChestLock397#grant(key)` (`GRANT_MS=10_000`) **unconditionally** right after
  `ChestGraph396Events#remember` - it was first written inside the `!shown` branch, i.e. on the wrong side.
- **(3)** Not `hitPort` (trampoline to `NpcStatsGraphUi.hitPort`, returns -1 for type 16): the single
  predicate every half of a blue pin is keyed on is `PursuitGraphNode272#isValueSourceNode`, and the chest
  card was missing from it. Now `isChestValueNode` (`ChestGraph396.isChestNode`).
- **(4)** `npc/client/CardEventPin398` (geometry `FIRST_ROW=NODE_H/2`, `SECOND_ROW=FIRST_ROW+18`, `isCard`,
  `secondChain`/`setSecondChain`, `labelKey` -> `gui.settledlands.{chest.pin_hack,door.pin_open}`,
  `pinScreenX/Y`, `cardAt`, `wireAt`, `hasWire`), `mixin/CardEventPin398Mixin` (client: grab, drop, cut,
  cable via the screen's own `curve`, undo point + dirty flag; **never** `connect(III)`, which writes
  `next` for a card), `door/DoorSignal398`. **The second chain lives in `Node#falseNext`** - the field the
  stock serialiser writes for every node type (rule of 384); `setSecondChain` refuses anything that is not a
  card (`isCard`), which `CardSockets398Probe` caught as `STOCK untouched=false` before the fix.
  Runtimes: `ChestGraph396#hackChainStart` (cracked -> second socket, **falls back** to `chainStart` for a
  396/397 graph), `DoorGraph382#openChainStart` (hand-opened door, closed->open only, server side,
  `GUARD_MS=250`; no fallback on purpose - the first socket is the hack). `ChestGraph396Events$Pending`
  gained `cracked`; `DoorUse373Mixin` compares `Doors368#isOpen` before and after the click.
- **Lang:** +3 keys (`door.pin_open`, `chest.pin_hack`, `chest.again`), 2 hints corrected (both files).
- **Gates:** `verify_jar --base 397` **PASS** (14 replaced / 3 added, 44 common + 55 client mixins, 1408
  external refs, 0 new); all 33 `tools/*_check.py` green, including the new `tools/card_sockets_check.py`
  (message-in-click, the three chest-window guards, `isValueSourceNode`+chest, `falseNext`-only / never
  `next`, the mixin not calling `connect`, both runtimes reading `falseNext`, the 5 lang keys, and the
  probe's 7 lines). `tools/harness/CardSockets398Probe.java` -> `SECOND fields=next:true falseNext:true
  apart=true`, `FIRST next=1 falseNext=2`, `FALLBACK wired=1`, `DOOR second=2 first=1 apart=true`,
  `NAMES chest=gui.settledlands.chest.pin_hack door=gui.settledlands.door.pin_open`, `STOCK untouched=true`,
  `GRANT mine=true other=false`. Probes 392-397 still pass.
- **Not in this build:** «взлом» is an event, not a command; a door opened by a mob/piston runs no chain;
  a double chest is still two graphs.
- **Unconfirmed in game:** the toggle repainting in the same frame, the vanilla window arriving after
  `openMenu` on NeoForge 1.21.1 (and the resend not stacking a second window), the second socket's cable
  landing pixel-exact next to the blue diamond at every zoom, the door's «открытие» not firing twice on a
  fast double click.

## Build 399 — the graph screens open again (398 was broken)

`settledlands-0.8.0-build399-card-cable.jar` (599 entries, 549 classes, 3 633 895 B, sha256 `7eaef16e…0ac51`),
base 398, two classes replaced (`CardEventPin398Mixin`, `NpcChestRows396Mixin`), source zip
`SettledLands-Build399-CardCable-Source.zip` (540 entries, sha256 `f0317855…68f5`).

- **Report:** «ты сломал графы, они теперь не открываются» — no crash, the wand click did nothing at all
  (confirmed with the author: option «клик палочкой вообще ничего не делает»).
- **Cause, found by reading the shipped bytecode of the 398 drawing pass:** `settledlands$drawCardSockets`
  began a `BufferBuilder` (`Tesselator#begin`) and then `return`ed when `quads <= 0` — **leaving the builder
  open**. `Tesselator#begin` refuses a second begin while one is open, so the next draw pass of the frame threw
  `IllegalStateException: Not building!` and the screen died on its first frame. Every door/chest graph has a
  card, so every one of them entered the pass; with no second cable wired `quads` was 0 — the empty path was the
  one that ran. The 290 blue pass, which this mixin was copied from, closes the builder on both paths
  (`drawWithShader(buildOrThrow())` / `buffer.build()`); that line was dropped.
- **Fix:** both paths close the buffer; `settledlands$cables` owns the decision; whole pass wrapped so a draw
  failure can never escape into the screen; render state restored (`disableBlend` was missing too).
- **Two more real defects fixed in the same pass:** the mixin's helper was `settledlands$screen`, the very
  name+signature the 290 mixin already merges into `NpcDialogueGraphScreen` (renamed `settledlands$cardScreen`);
  the chest inspector's toggle callback shared name+signature with the door's (`lambda$6(Node,[Button])V`), so
  Mixin could hand the chest the door's click and flip a door's seal (the chest now closes over a `Node[]`
  holder → its own signature).
- **Gate:** `tools/card_sockets_check.py` gained the rule that would have caught it — it now **fails on 398**
  with all three findings and passes on 399. `verify_jar --base 398` PASS (0 new external refs, 0 dangling),
  all 33 `*_check.py` green, probes 392–398 unchanged.
- **Lessons for the next build:** a `BufferBuilder` must be closed on *every* path, including the one that draws
  nothing; never name a mixin helper the same as another mixin's helper on the same target; two mixins' synthetic
  lambdas must not share name+descriptor; and **a green structural gate is not a running game**.

## Build 402 — the two chest sockets are two moments: «взлом» before, «открытие» on the close

`settledlands-0.8.0-build402-open-after-close.jar` (603 entries, 553 classes, 3 641 836 B, sha256
`4dab0a7c…de53`), base 401, **2 classes replaced, 0 added, 0 removed members**, source zip
`SettledLands-Build402-OpenAfterClose-Source.zip` (553 entries, sha256 `b86b2e14…c0fe`), patch
`tools/patch_build402.py`, tree `build303-src/out402`.

- **Report (with a screenshot):** card `Сундук` → «открытие» → «Реплика #1», «взлом» empty; «я бы хотел чтобы
  реплика проигрывалась не после взлома, а уже после открытия инвентаря сундука и его закрытия, как было до
  этого апдейта». Two build-401 rules met on that card: `runCrack` fell back to `chainStart` when «взлом» was
  empty (pulling the line before the chest), and `ChestGraph396Events` skipped the close of a cracked chest.
- **Fix:** no fallback in `runCrack` (only `hackChainStart`), and `run`/the close always follow «открытие»
  (new named method `ChestGraph396#closeChainStart`, which is `chainStart`). A chest that is cracked plays
  «взлом» before (only if wired) and «открытие» on the close; a 396/397 graph (one socket) behaves as before
  401.
- **Gate:** `chest_graph_check` now asserts both directions on the bytecode — `run` reaches `closeChainStart`
  and must not reach `hackChainStart`, `runCrack` the other way round; **it fails on build 401**.
- **Probe:** `tools/harness/ChestClose402Probe.java` →
  `AUTHOR next=1 falseNext=-1 close=1 hack=none`, `BOTH next=1 falseNext=2 close=1 hack=2`,
  `EMPTY close=none hack=none`.
- **Verified:** `verify_jar --base 401` PASS, all **35** gates green, `mixin_clash_check` green.

## Build 401 — the «взлом» chain speaks first, the door's «открытие» always speaks, ESC works

`settledlands-0.8.0-build401-chain-first.jar` (603 entries, 553 classes, 3 642 737 B, sha256
`ce074e6b…67840b`), base 400, **9 classes replaced, 4 added**, source zip
`SettledLands-Build401-ChainFirst-Source.zip` (551 entries, sha256 `8cbb0c08…2aa59`), patch script
`tools/patch_build401.py`, class tree `build303-src/out401`.

- **The root cause found on the way (and it is the cause of report 2, not a side note):** the compile-time
  mirror `PlayerInteractEvent` declared `getEntity()` returning `Entity`, while the game's method is
  `PlayerEvent`'s and returns `Player`. Four classes compiled against the wrong descriptor -
  `ChestGraph396Events`, `DoorEvents368`, `DoorGraph382Events`, `BlockTriggerEvents351` - and every
  `event.getEntity()` threw `NoSuchMethodError` **inside the handler's own `try`**. Same for
  `BlockEvent#getLevel()`: the game declares `LevelAccessor`, the mirror said `Level` (used by the chest/door
  break guards). A handler that dies on its first line is "nothing happens", which is exactly the report.
  The base mod's own `GuideDialogue`/`PossessionSystem` call the `Player` descriptor - that is what settled it.
- **New gate 35:** `tools/event_getter_check.py` - one event getter, one descriptor per jar (mod and base mod
  together). **It fails on build 400** on `getEntity` and `getLevel`, and is green on 401.
- **Report 1 (chest order «реплика → окно»):** the click left the panel. `ChestNet397#solve` now: crack
  (`ChestLock397.crack(player, key)`, `CRACK_MS = 600_000`) → `ChestGraph396#runCrack` →
  `BlockTrigger351#startReportingWindow` (new, `stopped[0]` says a window took the chain over) → **then**
  `settledlands:chest_ready` (`ChestNet397$Ready`, `key|wait`). Client `ChestOpen401` (`arm/armed/clear/watch`,
  `TTL 60 s`, `WINDOW_GRACE 5 s`, `QUIET_TICKS 2`) delivers the 400 `useItemOn` click only when the chain's
  window is gone; `ChestOpenWatch401` (`ClientTickEvent.Post`) drives it. `ChestGraph396Events` no longer runs
  the «взлом» chain on window close (it would speak twice). Action bar: «Замок снят: сундук откроется сам».
- **Report 2 (door «открытие» silent):** the mixin leg stays, and now logs (`DoorUse373Mixin is LIVE`,
  "saw the door go shut → open"); a second leg that no mixin can remove watches the click:
  `DoorEvents368#onRightClick` (unlocked, shut) → `DoorSignal398#watch` → `ServerTickEvent.Post` re-reads the
  door and runs the chain if it opened. Both legs share `GUARD_MS = 250`. Every reason the chain can decline is
  logged once, including "the card has nothing wired to its «открытие» socket".
- **Report 3 (ESC «через раз»):** the solved panel's hold is timed in `tick()` and `render()` on the hardware
  (`InputConstants.isKeyDown`, helper `escHeldTick`/`settledlands$escDown`), `ESC_HOLD_MS` 1200 → **800**, drawn
  as a percentage; `keyPressed` no longer times it; `settledlands$askAgain` only reports, never resends (a
  resend would run the «взлом» chain twice).
- **Probe:** `tools/harness/ChestOpen401Probe.java` → `CRACK_ONE true`, `CRACK_OTHER false`,
  `CRACK_SPENT cracked=true take1=true take2=false`, `ARM_READY true`, `ARM_CLEARED false`,
  `WATCH_QUIET ok`. Order «chain → ready» is asserted on the **instruction order** of `ChestNet397#solve`.
- **Verified:** `verify_jar --base 400` PASS (0 new external refs; `Screen#tick()` added to
  `CONFIRMED_EXTERNAL`), all **35** gates green, `mixin_clash_check` green (1 documented exception).

## Build 400 — the chest's settings reach its panel, and a solved panel opens the chest

`settledlands-0.8.0-build400-chest-open.jar` (599 entries, 549 classes, 3 634 462 B, sha256 `1eaff1a7…f5012`),
base 399, **10 classes replaced, 0 added**, source zip `SettledLands-Build400-ChestOpen-Source.zip`
(545 entries, sha256 `5d198691…9da4`).

- **Report 1:** «в взломе сундука не меняется кол-во чисел, скорость и сложность». **Cause:** Mixin merges one
  method per name+signature per target, and `NpcChestRows396Mixin` declared `settledlands$slider(Node,IIIIIZ)I`,
  `settledlands$difficulty(...)` and `settledlands$parkStockRows()V` — the exact names and signatures
  `NpcDoorRows382Mixin` already merges into `NpcDialogueGraphScreen`. The door's copies survived; the door's
  copies write through `DoorGraph382#setKeys`, which refuses anything that is not a door. Every chest slider moved
  and wrote nothing, and no gate could see it: a jar with a dead chest method still compiles.
- **Fix 1:** chest helpers are `$lockSlider`, `$lockDifficulty`, `$parkChestRows`, and their callbacks close over
  `Node[] card` + a `String`/prefix so their `lambda$N` signatures are theirs alone.
- **Report 2:** «по прежнему после успешного взлома сундук не открывается» (398, 399 both answered with
  `ServerPlayer#openMenu(state.getMenuProvider(...))` and produced no window).
- **Fix 2 — a different path, not a better menu call:** `ChestNet397#solve` only lifts that chest's lock
  (`ChestLock397.grant`); `HackPanel377#onClose` → `settledlands$clickTheChest()` performs the player's own click
  through `Minecraft#gameMode.useItemOn(player, MAIN_HAND, BlockHitResult(centre, UP, pos, false))`; the click
  lands in `ChestGraph396Events#onUse`, which reads the lift, **consumes** it (`ChestLock397.revoke`), lets the
  click through to vanilla, and records the opening as a crack (`remember(..., true)`) so the «взлом» chain runs
  on close and later openings run «открытие».
- **Five more silent merges removed in the same pass:** `settledlands$parkStockRows` (name inspector vs door),
  `settledlands$isBlock`/`$isDoor` (block menu vs graph mixins), the chest's `lambda$3(I)Component` difficulty
  label (vs the door's).
- **New gate:** `tools/mixin_clash_check.py` — **fails on 399 with 12 clashes** (including `$slider` and
  `$difficulty`) and is green on 400 with **one documented exception**: `lambda$0(Node)V` shared by
  `NpcRadiusRow355Mixin` and `NpcRandomLineInspectorMixin337` (pre-existing since 355/337, no report, and every
  other capture signature on that screen is already taken — removing it means rewriting both rows).
- **New probe:** `tools/harness/ChestOpen400Probe.java` (run by `chest_graph_check`) prints
  `CARD_AFTER keys=9 sec=3 diff=2` and `LIFT_SPENT cracked=true after=false again=false`.
- **Gate results:** `verify_jar --base 399` PASS (0 new external refs — the four new game members are in
  `CONFIRMED_EXTERNAL` with javadoc evidence; 0 dangling), all **34** `*_check.py` green.
- **Lessons:** a silently merged method is *dead code that compiles* — the gate must compare helper names **and
  lambda descriptors** across every mixin of one target; and when the same "obvious" API fails three builds in a
  row (`openMenu` from behind a screen), change the mechanism, not the arguments.
