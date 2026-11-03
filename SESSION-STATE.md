# SESSION-STATE — SettledLands

Обновлено: 2026-11-03. Ветка: `arena/5618404a-mod` (ранее `arena/99d7691a-mod`).

> **Сначала читать `HANDOFF-NEXT-AI-RU.md`** — там текущая задача, открытый дефект, порядок действий и
> ловушки. Этот файл — история и хронология.

## Текущая сборка

`settledlands-0.8.0-build357-block-line.jar` — **нода «Реплика» у блока**. В палитру блока добавлена
штатная нода-строка (тип 0, `NpcDialogueGraph.LINE`), поэтому инспектор с полем текста, карточка,
save/load и буфер обмена — родные. Блок **говорит текст в чат** игроку, который запустил цепочку
(ПКМ пустой рукой или игрок из радиуса), с подстановкой `NpcProfile.fill(text, player, 0)`
(`%player%`, `%relation%`=0); цепочка идёт дальше сразу — окна диалога у блока нет. Пустой текст или
нет игрока → молча идём дальше. Палитра блока теперь шесть нод: **Реплика, Триггер, Условие If,
Переменная, Случайность, В радиусе**; карта «строка палитры → тип» переехала в
`BlockTrigger351#typeForRow` (+ `PALETTE_ROWS`), хук `NpcBlockGraph351Mixin` только спрашивает её;
подпись `gui.settledlands.block.palette` обновлена в обоих языках. **525 записей**, 72 миксин-класса
(35/36), 477 классов, `verify_jar` PASS (0 новых внешних ссылок против 356, 0 висячих), `jvm_verify` OK,
новый гейт `block_line_check` OK (палитра = один источник, «Реплика» строится, прочие строки -1, нода
валидна и текст переживает save→load, реплика уходит через `NpcProfile.fill`, подпись честная),
`block_trigger_check` OK (обновлён: карта в `typeForRow`, палитра из шести), `radius_flag_check` OK,
остальные 15 зелёные. Патч `tools/patch_build357.py`, классы из `build303-src/out357`, проба
`tools/harness/BlockPaletteProbe.java`. В игре **не подтверждено**.
Выложено: коммит `31b708c` + push; Drive jar `1c73_xoC0h19obIXkFUn36ebHpERGbbua`,
zip `1RFRtGQG6UufUOFjmH_HsFNUCt6UGg4Yo` (папка `0AB-al7DFuWhEUk9PVA`).

**Урок 357:** подпись палитры и карта строк — тоже код: их надо обновлять вместе с `PALETTE` и
проверять прогоном (`BlockPaletteProbe`), иначе редактор обещает ноду, которую обходчик пропускает.

**Инфраструктура 357:** в новой песочнице `jdk4py` встал в `dist-packages`, а `clipboard_check`,
`wire_points_check`, `screen_smoke` были прибиты к `/tmp/venv/...`. Теперь все три падают обратно на
`jdk4py.JAVA_HOME` (как `radius_flag_check`), гейты снова запускаются. Ставится:
`pip install jdk4py --break-system-packages`.

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
