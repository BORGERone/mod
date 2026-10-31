# AI-GUIDE — SettledLands · ЧИТАТЬ ПЕРВЫМ при старте любой сессии
Версия 1.2 · 2026-10-20 · создан после двойного тихого отката build 123→124

## Урок
Песочница иногда восстанавливает воркспейс из УСТАРЕВШЕГО снапшота: файлы молча откатываются,
а сборка остаётся «зелёной» (собирается старый, но валидный код). **Зелёная сборка ≠ актуальное
дерево.** Доверять только хэш-диффу против эталона с Google Drive.
Признаки отката: у пачки файлов mtime одинаковы до наносекунды; маркер-грепы (§ «Маркеры») пустеют;
`javap -cp <jar> -p dev.settledlands.npc.GuideDialogue | grep handleNpcDamaged` не находит новые перегрузки;
тест-набор внезапно не компилируется без правок.

## Железные правила
1. Источник истины — Google Drive пользователя. Воркспейс песочницы — временный рабочий стол.
   **ОБНОВЛЕНИЕ (2026-10-21, после b134): Arena убрала привязку Drive — коннектор off навсегда.
   Источник истины теперь ЛОКАЛЬНЫЙ воркспейс /home/user; артефакты отдаём через present_file,
   билды 133-134 на Диск не попали (локальные копии хранить).**
2. После КАЖДОЙ принятой сборки на Drive уходят ТРИ файла (public): JAR, исходный ZIP, бэкап рабочего дерева (§В).
   → если Drive недоступен (см. п.1): собираем их локально в /home/user, не удаляем до подтверждения.
3. Перед ЛЮБОЙ правкой/сборкой — аудит дерева против последнего исходного ZIP (§А), особенно после
   рестарта песочницы / новой сессии / паузы «на проверку пользователем». Не править неаудированное дерево.
4. Восстановление — только побайтовое, из эталонного исходного ZIP. Не повторять правки «по памяти».
5. Критерий успеха восстановления: SHA-256 пересобранного JAR == SHA-256 доставленного + ALL PASS тестов.
6. Песочница чистая: старые JAR/ZIP удалять сразу после заливки на Drive; `.cache/` не бэкапить
   (платформа его не персистит — сам исчезнет); держать только текущие артефакты + входные файлы сборки.
7. Ритуал доставки, строго по порядку: правки на аудированном дереве → сборка → тесты (912+) →
   package_source → копии в /home/user → sha256-сверка → Drive ×3 public → удаление устаревших артефактов →
   present_file JAR → отчёт на русском со ссылками → обновить раздел «Состояние» этого гайда.
8. Постоянные требования пользователя: оптимизированный код, не грузящий систему; «как профессиональный
   мододел»; общение на русском; JAR всегда через present_file + прямая ссылка; не перегружать песочницу.

## А. Аудит/восстановление (перед правками)
```bash
cd /home/user && rm -rf /tmp/zrestore && mkdir -p /tmp/zrestore && cd /tmp/zrestore \
 && unzip -q "/home/user/<эталонный-исходный-ZIP>.zip" "*/patch-src/*" "*/resources/*" "*/tests/*"
# python: sha256-дифф patch-src/, resources/, tests/ против /tmp/zrestore/***/...;
# несовпавшее — cp из эталона. Рабочий скрипт: история 2026-10-20, ~25 строк.
# Файлы, которых нет в ZIP (META-INF/neoforge.mods.toml, models/item/coord_stick.json), — НЕ откат.
```

## Б. Сборка / тесты / упаковка
```bash
cd /home/user/settledlands/build92
python3 build_patch.py --java-home /tmp/jdk21
python3 tests/run_tests.py --cache .cache/compile88 --java-home /tmp/jdk21   # логи tests/<name>-{compile,test}.log
python3 package_source.py --original-zip SettledLands-Build116-Mobility-Extended-Source.zip
```
JDK21: `/tmp/jdk21` (переустановка: curl zulu21.0.11 tar.gz | tar -xz; mv /tmp/zulu21.* /tmp/jdk21).
URL/чек-суммы зависимостей — `dependencies.lock.json`.

## В. Бэкап дерева (после приёмки билда)
Один архив `SettledLands-Workspace-Backup-<дата>-build<N>.zip`: `patch-src/`, `resources/`, `tests/` +
корневые *.py/манифесты/lock + текущие JAR и исходный ZIP. Исключить: `.cache/`, `cache/`, `libs/`,
`diagnostics/`, `settledlands/`, старые артефакты, `tests/test-report.json`, `__pycache__`.
**Этот гайд включать в архив** (провайдер коннектора блокировал отдельную заливку .md фильтром контента —
обходить фильтр запрещено; внутри ZIP проходит). → Drive upload + make_public; ID — в «Состоянии».

## Маркеры целостности build 127 (grep -c, patch-src)
- `tickSubGraph` в `npc/GuideNpc.java` = 3
- `getPickResult` в `npc/GuideNpc.java` = 0 (клиентской фабрикации pick-block НЕТ — build 127)
- `sl_copy_src` в `npc/GuideNpc.java` = 2 (только readAdditionalSaveData) и в `npc/NpcCopyPickPayload.java` = 1
- Файлы есть: `npc/NpcCopyPickPayload.java`, `npc/NpcCopyModEvents.java`, `npc/client/NpcPickCopy.java`
- `NpcDialogueGraph npcDialogueGraph` в `npc/GuideDialogue.java` = 33 (с 227 файлы GuideDialogue/NpcDialogueGraph/Screen в patch-src — MERGED-исходники 172+226, имена параметров CFR-стиля; старый b127-паттерн `NpcDialogueGraph graph, NpcDialogueGraph.Node` = 11 УСТАРЕЛ)
- `handleNpcDamaged` (grep по имени) в `GuideDialogue.java` = 3 (старая сигнатура-паттерн b127 устарела)
- `groupPick` в `npc/client/NpcDialogueGraphScreen.java` = 0 — УСТАРЕЛО: в экранных классах поколений 172 и 226 groupPick нет вообще (эволюция фичи до-172, НЕ потеря слияния 227)
- `261` + `wand.point.del` в `keyPressed` `npc/client/MobilityWandScreen.java` (Delete удаляет ноду)
- `mobility_choose` в `resources/assets/settledlands/lang/{en_us,ru_ru}.json` = 0; `no_group` = 3 в каждом
- `guide_spawn_egg.copy` и `npc.copied` в обоих lang = 1; версия в `resources/META-INF/neoforge.mods.toml` задаётся `build_surgery.py --version` (build_patch.py — история b132), валидация tomllib обязательна
- Маркеры слияния 227 (patch-src, MERGED-файлы): `varLocal` = 8 в GuideDialogue / 5 в NpcDialogueGraph / 6 в Screen; `RADIUS_LOG` = 27 и `readVarValue` = 4 и `PendingCutsceneReplica` = 6 и `onCutscenePlaybackEnded` = 1 в GuideDialogue; `VarLocal` = 2, `CutsceneLine` = 2, `RadiusTarget` = 2, `RADIUS = 15` = 1 в NpcDialogueGraph; `local_on` = 2, `add_radius` = 1, `case 15 -> 15` = 1, `cutsceneLine` = 3 в Screen; `STOCK_CORPSES` = 3 в PlayerCorpsePossession; lang: 698 ключей в каждом (union 172+226), `no_group` = 3, `mobility_choose` = 0
- `sanitizeNpcSelectors` в `npc/client/CommandTabCompleter.java` = 2 (теневая замена @npc→@s для brigadier); `injectArgHints` = 2 (недеструктивные строки-подписи аргументов, НЕ списки чисел — отказано в 129)
- `cutscene_wand` в NpcContent.register = 1; `settledlands_cutscene_cam`+`settledlands_cutscenes` (SavedData×2); пакеты `cutscene_cam_exit/state/rec_toggle/scene_list/playing`; `CUTSCENE`=14 в NpcDialogueGraph (+validate/CutsceneName NBT), GuideDialogue ветка терминальная; menu TOTAL_ROWS=16 (row14=CUTSCENE, row15=RADIUS «+ Цель» — столько уже в 169–172); тест ContextMenuHarness с 227 перекалиброван: scroll(9)→scrollRow 10, max=11 (16−5), scrollbar-drag-bottom→11, scroll(−20)→0; стабы doubles с 227 включают Entity.getName() (GuideDialogue 149+ логирует имя игрока в walk-start/RADIUS_LOG — без стаба graph-runtime падал NoSuchMethodError); NeoForge 1.21: у ванильного Entity НЕТ getPersistentData — нужен каст ((IEntityExtension)entity).getPersistentData() (так работают localVars 227 и PlayerCorpsePossession 217+); javap: invokedynamic-конкаты НЕ видны как ldc-строки ('scene_'), boolean-методы — не 'void', константы int — не 'X = 14' в -c -p

## Технические инварианты мода (не терять)
- `graphActive()` = `enabled && graph.get(entry) != null`; НЕ ужесточать до LINE+NORMAL entry.
- Графы — данные NPC: `sanitized()`/`load()` не валидируют хранение.
- Тик/chain-методы принимают граф параметром; «какой граф активен» решает вызывающий.
- `startAtLine` — только главный граф (`profile.dialogueGraph == graph`).
- Тест-дубли: базовые в `create_test_doubles.py`, диалоговые поля — только через `extend_dialogue_doubles.py`.
- Обязательны: чёрные полости черепа/торса `[127,127,128,128]`; LOD — по выбранной `.bbmodel`, не дефолтной.
- **НИКОГДА не сериализовать профиль/графы целиком в ItemStack/инвентарные пакеты** — ванильный лимит
  ~2 МБ на пакет: client → server `set_creative_mode_slot` падает с DecoderException (урок 126).
  Поэтому СКМ-яйцо несёт только uuid-метку `Guide{sl_copy_src}`; профиль копируется НА СЕРВЕРЕ при
  спавне из живого оригинала (load(save()) раундтрип, без алиасинга). Копия берёт состояние на момент спавна.
- **НИКОГДА не фабриковать модские стаки на клиенте для creative-sync** (pick-block `getPickResult`,
  creative slot sync): путь client→server `set_creative_mode_slot` ломается на многих серверах/прокси/гибридах
  (декодер бьётся о модские предметы/NBT — классическая проблема, работает /give, но не creative-kick).
  **Копирование/выдача предметов — только серверная**: клиент шлёт крошечный мод-пакет с id сущности,
  сервер сам создаёт стак и кладёт в инвентарь (паттерн `NpcCopyPickPayload` + `NpcPickCopy`, build 127).
- **Entity-tag яиц/спавна читается из compound `Guide`** (`var1.getCompound("Guide")` → "Profile", не top-level).

## СОСТОЯНИЕ (обновлять после каждого принятого билда)
**Build 162 «triggermix» (2026-10-23, доставлен).** Всё из 161 + фиксы по фидбэку:
1) **Хитбокс вернулся к 1.35×4.2/eyeHeight 3.5** (микро-точка 161 ОТМЕНЕНА).
2) **Блоки через триггера РЕАЛЬНО чинятся**: ванильный EntityGetter.isUnobstructed сканирует ВСЕ entity-bb,
   хука нет → добавлен МИКСИН `TriggerUnobstructedMixin` (@Mixin EntityGetter, HEAD cancellable на
   isUnobstructed(Entity, VoxelShape)): сначала замер пересечения с GuideTriggerNpc (joinIsNotEmpty), нет
   → return (ванилла без изменений); есть → повторный проход БЕЗ триггеров, воспроизводит ванилу 1:1
   (isRemoved, passengerOfSameVehicle). Модуль: resources/settledlands.mixins.json — ОВЕРЛЕЙ базового
   (path resources/settledlands.mixins.json — копия из baseline + добавлен mixin); mixin.class в jar ✓.
   Компиляция: libs/mixin.jar = net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7 с maven.neoforged.net —
   package_source.py whitelist дополнен 'mixin.jar' (раньше: baseline-build83/jlayer). Миксин только
   compile-time; на runtime он прилетает из игры.
   ДОПОЛНИТЕЛЬНО: ПКМ-блоком по триггеру из 161 (PASS) в силе, хитбокс от этого не пострадал.
3) **Тумблер block-RMB ИЗНАЧАЛЬНО ON для триггеров**: GuideNpc.defaultProfile() → fresh.lockInteraction =
   initialLockInteraction() (новый хук, база false); GuideTriggerNpc → true. Действует только на НОВЫХ NBT
   (существующие сохраняют false, пока не сохранят NBT свежего профиля... actually старые триггеры уже
   поспавненные: профиль в NBT сохранён — остаются как есть до ручного обновления в меню).
- JAR sha256 `d9510c0b28a8c53a39679eef072298525a6ed56fe0dceb84fdf0ad7091770823`
- ZIP sha256 `e90a1c85d83b4b814f0e14e459479a0afeb03f782736f9aeea2eaf5fad3cec90` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 161 «triggerwand» (2026-10-23, принят частично — точечный хитбокс откатан в 162).** Всё из 160 + доработки триггер-NPC:
1) **Виден также с жезлом**: isInvisibleTo → egg `NpcContent.triggerEgg()` ИЛИ `NpcContent.wand()` в любой руке.
2) **Блоки**: ванильный isUnobstructed (EntityGetter) идёт по getEntities(null, box) БЕЗ кастомного хука →
   перекрыть нельзя, поэтому footprint спущен: EntityType sized(0.2F, 0.05F), eyeHeight(1.75F) — блоки
   всюду поверх него ставятся свободно (остаток: только сам блок floor-клетки в ногах упирается в sliver).
   ПКМ с BlockItem в руке: mobInteract override возвращает PASS НА ОБЕИХ сторонах (клиентский CONSUME
   GuideNpc-а иначе съедал клик) → падаем в useOn блока; взаимодействие с яйцом/пустотой как обычно.
3) **Дефолт scale 50**: GuideNpc добавлен protected int initialScale(){100} — юзится и в field-init
   defaultProfile() и в defineSynchedData(DATA_SCALE, initialScale()) (виртуальный вызов из ctor, override
   константа — безопасно); GuideTriggerNpc.initialScale()==50. Загрузка NBT перекрывает дефолт; правка в
   меню работает как раньше. Существующие триггеры со scale 100 ОСТАЮТСЯ (NBT), 50 — только у новых.
- JAR sha256 `0c2dd9448aa97109944750671cdc71ff9120762930e32569886e00a2ab35791a`
- ZIP sha256 `52306f64c18d22cb77deb55eb5d8fa8f71776c7fd1a06766e1bac188057ed278` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 160 «triggerfix» (2026-10-23, принят).** Всё из 159 + три исправления катсцены/триггера:
1) **Бой в катсцене**: дыра — на FROZEN-кадре клик ЛКМ слал cutscene_cam_exit → оп-игрок (payload-guard
   hasPermissions(2)!) проходил exit() → restore() ДО окончания цепочки реплики = освобождение посреди
   сцены. Фикс: клиент (CutsceneCameraClient.onKeyTriggered) на MODE_PLAYING||MODE_FROZEN глотает
   attack/use, отменяет свинг и НЕ слушает rec/exit. Сервер (CutsceneCamera.exit): после playback-ветки
   `if (hasDeferral(player)) return;` (лог «exit ignored ... frozen deferral frame»). Естественный конец
   трека идёт через playback-ветку — НЕ затронут.
2) **Триггер виден**: кастомный GuideRenderer.render для BbModel НАПРЯМУЮ звал customModel.render,
   минуя ванильный isInvisibleTo-фильтр. +Гард в начале render(): viewer==null||isInvisibleTo(viewer) →
   return. Плюс в GuideTriggerNpc переопределён isInvisible()==true (гаснет тень/name/vanilla-гейты;
   с яйцом isInvisibleTo=false → рисуется).
3) **Триггер непроходим**: player.tick.pushEntities нашёл trigger (candidate по IS-PUSHABLE -> false это
   про толкаемость триггера количественно НЕ помогало от обратного толчка). Переопределён
   `protected void pushEntities(){}` — триггер никого не толкает → через него идут насквозь.
4) **Флаг «Запретить взаимодействие (ПКМ)»**: NpcProfile.lockInteraction (тег ``LockInteract``, копи×2,
   NpcEdit 'lockInteract'), чекбокс в блоке «Поведение» поведение-колонки cfg «gui.settledlands.field.
   lockinteract» + клиентское echo case; guard в GuideNpc.mobInteract ПОСЛЕ веток wand/mobility-wand →
   диалог/эскорт/обучение блокируются, жезлы по-прежнему открывают меню (нужно для удаления).
- JAR sha256 `cb1df736557f120ca6cfbdcf95c8ba9e16ca214bcd295826014ea7f6e0548ff0`
- ZIP sha256 `fcf7357b02aa842fc97aa48dfed1b94384b9825caf6a231760ba88700df8f5fa` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 159 «guide-trigger» (2026-10-23, доставлен).** Всё из 158 + **триггер-NPC** (технический
скриптовый моб):
- Новый класс `GuideTriggerNpc extends GuideNpc` (patch-src/dev/settledlands/npc/GuideTriggerNpc.java):
  невидим всем, кто НЕ держит его яйцо в любой руке (isInvisibleTo→prуёт за triggerEgg); noGravity в ctor;
  hurt: GENERIC_KILL → super.hurt (поэтому /kill работает), иначе только handleDamageTriggers + false
  (скрипты «по урону» срабатывают, а урона нет); canBeHitByProjectile/canCollideWith/canBeCollidedWith = false
  (бестелесный: стрелы/удочки/тонки через сквозь). Pushable=false унаследован.
- GuideNpc: hurt делегирует в protected handleDamageTriggers (для подкласса); applyEdit + оп "npc.delete"
  (после op-guard): message.settledlands.npc.deleted + discard(). Это маршрут wipe для триггера.
- NpcContent: EntityType guide_trigger (те же размеры 1.35×4.2), DeferredSpawnEggItem triggerEgg
  (bg 0x1a1527=1710631, dots 0x3804ff=3668479), регистрация entity/item, attributes.put, вкладка добавлена.
- Renderer: NpcClient.java ПЕРЕНЕСЁН в patch-src (раньше был reference-only!) + registerEntityRenderer(
  guideTrigger(), GuideRenderer::new) — та же модель/текстура.
- NpcConfigScreen: кнопка «Удалить НПС» в футере (left, рядом с done/refresh), двухшаговая: arm →
  красная «Точно удалить?» → второй клик: send("npc.delete") + onClose. deleteArmed сбрасывается в init.
  Поля deleteArmed/deleteButton.
- Lang en/ru: item/entity names + cfg.delete/confirm/(confirm_)tooltip + npc.deleted; total keysets +7.
- Модель resources/assets/settledlands/models/item/guide_trigger_spawn_egg.json: template_spawn_egg.
ИДЕНТИЧНОСТЬ ПОВЕДЕНИЯ: все instanceof GuideNpc и find() по entityId автоматически покрывают подкласс;
mobInteract: wand→config меню (тот же op-guard), диалоги/эскорт/тома работают.
- JAR sha256 `767976f7f485ad567f9448c44736fb1b2f337a902f6804517db6f38f2ff85034`
- ZIP sha256 `151035f4973e5d292054a0b1d28fd5708ba75d30ab570e147449a5900779fb5c` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 158 «csnoskip» (2026-10-23, принят).** Всё из 157 + **катсцены непропускаемы**: клиентский
onKeyTriggered в MODE_PLAYING больше не шлёт requestEnd() — клики ЛКМ/ПКМ при проигрывании проглатываются
(event отменён, без свинга), ролик всегда доигрывает до конца. Естественные концы сохранены: playhead>=maxT
→ requestEnd; потеря спектатора → requestEnd. Серверный гард не требовался: natural-end и skip делили один
пакет, различаются только на клиенте. Рабочее дерево build92 периодически откатывается рестартом песочницы
(потерялось до 134!) — ВСЕ рецепты ресторятся из последнего SOURCE-ZIP каждый раз, ZIP они неоткатываемы.
- JAR sha256 `61725f78cba1df1f50bcbaaf12d08e546dbd671ccfb1b779937fcd80d4a56481`
- ZIP sha256 `82118a53fbe2c8cbe97a18de0b9650e8d3b58cc0f1b849f0cb92425df4392658` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 157 «csresume» (2026-10-23, принят).** Всё из 156 + **продолжение диалога после катсцены/цепи
БЕЗ тумблера**: обычная реплика (LINE без cutsceneLine) на выходе из катсцены теперь = «продолжи диалог
здесь после возврата в тело». PendingCutsceneReplica: hasReplica → replicaFlagged + continueAtLine (plain
LINE тоже армируется + session.graphNode на ней). onCutscenePlaybackEnded доп. код 3: продогреет линию —
pending ПАРКУЕТСЯ обратно, exit() при claim==3: finishPlayback(телепорт+gamemode+MODE_OFF+отстрел client-
камеры) и сразу GuideDialogue.resumeGraphAfterCutscene(player) → sendGraph(session.graphNode) → диалог
открывается на позиции тела. Приоритеты: флаг > продолжение > терминал. Чанийные коды: 0=ничего, 1=freeze+
flagged реплика, 2=след. сцена цепи, 3=return+resume. Логи: «dialogue resumes at line #», «resumed
dialogue at line #».
- JAR sha256 `be2b504353eece684e5e13b181e2a2cd665f337bb88b2bf6c7ae1b2330c51caf`
- ZIP sha256 `2cc9c1309145394dd60c1b5b5370c7f57e932e492adf51e3ddae4aba13406f49` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 156 «cutscene-chain» (2026-10-23, принят).** Всё из 155 + **цепочки катсцен подряд**: нода
«Катсцена» исторически ТЕРМИНАЛЬНАЯ (curId=-1), вторая подряд не исполнялась вообще. Теперь CUTSCENE-case
идёт по cur.next сквозь SCR СУЩЕСТВЕННО подряд идущих CUTSCENE (guard-vs-cycles, их action/trigger-ы
исполняются на месте): имена сцен очередями в extraScenes PendingCutsceneReplica (поля +hasReplica).
onCutscenePlaybackEnded стал INT: 0=ничего (полный exit), 1=реплика клеймит кадр, 2=запущен следующий
сцен (CutsceneCamera.playChained — play() БЕЗ finishPlayback-шага! play() обычный содержит
`if(isPlaying)finishPlayback` — он бы телепортировал и снял спектатор посреди цепи; playChained этого не
делает, returnPos не перезаписывается если уже есть). exit() по claim==2 полностью ничего не делает —
камера просто перепрыгнула на следующий трек. Цепочка БЕЗ реплики: играются все сцены → обычный возврат.
Клейм реплики только после ПОСЛЕДНЕЙ сцены цепи. Логи: ARMED(+N chained, replica=), chain step, failed,
chain finished, claim result=.
- JAR sha256 `c3ec356cb90b62db2c5c0a656f8cb4d2bea7d207a208e73a43f969859a24efc6`
- ZIP sha256 `dffe261db35466009b073ccc96da5611dbb9082c41a33901859cc9a94b6644ee` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 155 «csreplica-plain» (2026-10-23, принят — голая катсцена снова играется).** Всё из 154 + регрессия «голая катсцена без
реплики больше не играется»: в stop() вызываемый resumeAfterDeferral видел `isActive(player)==true` —
но это was ПРОСТО свежезапущенная катсцена (enter() уже положил gameModes), и гард `ret==null&&!isActive`
пропускал его дальше → restore+MODE_OFF убивали ролик на следующем тике после старта. Вооружённые реплики
спасались pending-гардой stop() — поэтому 151+ сценарий работал, а голая катсцена умерла молча. Фикс:
resumeAfterDeferral возвращается, если deferredReturn НЕ содержит игрока (ret==null) — безусловно.
- JAR sha256 `4dcfa6034264359377f53194e45ea333d0714b66e7c9b5a793dee52099a76beb`
- ZIP sha256 `e51c5e97d39ca6db56388f24a47275ab3e5491e8fc2f194443fce56dad4cfa69` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 154 «csreplica-fix2» (2026-10-23, принят — реплика после катсцены заработала).** Всё из 153 + **ИСТИННЫЙ корень STALE-сессии**
(latest.log (3): ARMED → 2с → STALE, без SUPPRESSED): **тикер onServerTick чистит сессии напрямую
`var2.remove()`, минуя stop()** — условие удержания требовало `!var5.isSpectator()` и близость к NPC,
а во время ролика игрок — спектатор и улетел по треку → сессия сносилась на СЛЕДУЮЩИЙ тик. Фикс:
в else-ветку чистилы добавлено `if (cutsceneConversationActive(var5)) continue;`. SUPPRESSED-гарды
153 (start/startEscort/startAtLine) оставлены — нужны на время замороженного кадра. Контрольный
паттерн на будущее: проверять ВСЕ места прямого мутирования SESSIONS: remove() в тикере НЕ идёт через
stop(). В логах [cutscene] это читается: ARMED → (без STALE) → CLAIMS → frame FROZEN → закрытие цепи.
- JAR sha256 `19245d07308869be9863c5e3e4967b8f51b5c6fde9910973f8e8a447c435878b`
- ZIP sha256 `965b445d09bebeeabf2e101cae25d1e79a00ffea82c807d20795ff715accc25b` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 153 «csreplica-fix» (2026-10-23, не помогло — гипотеза про put-хиджак опровергнута логом).** Всё из 152 + КОРЕНЬ БАГА «реплика после катсцены
не открывается», найден по логу пользователя (ARMED → через 2 сек STALE sessionAlive=false): **пока ролик
играл, новый диалог (типовой виновник — тикающая нода «В радиусе»/CHECK с startAtLine!) вызывал stop+put
и СТИРАЛ диалоговую сессию** — stop() был защищён pending-гардой, а вот `SESSIONS.put` — нет; pending на
финиш ролика заступал мёртвой ссылкой. Фикс: helper cutsceneConversationActive (pending ИЛИ hasDeferral)
+ ранний выход с логом SUPPRESSED во всех ТРЁХ точках создания сессии: start(), startEscort(), startAtLine().
Оставлены CS-логи 152 (ARMED/NOT armed/STALE/CLAIMS/frame FROZEN). Плюс из 152: порядок пакетов
(MODE_FROZEN до Playing(false)) — иначе клиент не ловил последний кадр.
- JAR sha256 `01400725efe9ff0c68b0cd48ff92a8ba61ea67346cfcf9937683bf8d36c19593`
- ZIP sha256 `fb1b694e97a5fe9a510b615348c7ae4e5c41bce4e9574cfa1f18148e854d3a11` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 152 «csreplica-diag» (2026-10-23, принят как диагностика — лог получен, корень найден).** Всё из 151 +
по фидбэку «после катсцены обычный возврат, диалог не продолжается»: **фикс порядка пакетов в
finishPlaybackKeepCamera** — MODE_FROZEN теперь шлётся ДО PlayingPayload(false), иначе клиент уже
обнулил track и последний кадр нечем ловить (FROZEN[5] нули). Плюс диагностика CS-лога [cutscene]
(logger в CutsceneCamera: fully-qualified slf4j): ARMED / NOT armed (с причинами started/cont/contType/
flag/session/mobCtx), playback ended → NO pending / CLAIMS / STALE (sessionAlive/npcAlive), exit claimed=,
frame FROZEN. Гипотезы под проверкой: (а) arm не случился (флаг не уехал на сервер? cutscene.next!=line?
session null?), (б) stale на момент окончания ролика, (в) exit вообще не попал в isPlaying-ветку.
От пользователя: сценарий ещё раз + latest.log, строки «[cutscene]».
- JAR sha256 `40b26dc4701054055f830d3cc83ba6d5b5cd37be609f0abe9d4a58e8bbc584a5`
- ZIP sha256 `eab2b05787215381cd04b8527470dffb58c234475e66f078c20eee60fc954422` · backup-преф — в отчёте
- Тесты: ALL PASS 991.
**Build 151 «cutscene-replica» (2026-10-23, принят — в игре реплика не подхватывается, снят 152).** Всё из 150 +
**1) Переименования:** «Проверка»→«В радиусе» (+ кнопка add_check), «В радиусе»→«Цель» (+ add_radius),
ru+en, оба дерева lang (resources и reference-resources). Ключи те же, только значения.
**2) Реплики после катсцен:** LINE, идущая за CUTSCENE (cutscene.next==line.id), получает в инспекторе
тумблер «Реплика катсцены» (виден ТОЛЬКО при наличии входящей катсцены — инлайн-цикл в widgets()),
вкл → оранжевый заголовок 0xffff7a1a в цепочке цветов 1711 (ПЕРЕД веткой LINE). Поле Node.cutsceneLine,
copy(), NBT CutsceneLine (только для LINE, только true — старые сейвы совместимы). Рантайм: CUTSCENE-case
при play()==true и cont.cutsceneLine кладёт PENDING_CUTSCENE_REPLICA (session.graphNode=cont.id);
stop() рано-выходит пока pending жив (сессия НЕ должна гибнуть на -1 после катсцены!); exit-пайлоад:
isPlaying → onCutscenePlaybackEnded — если забрал (сессия жива, npc жив) → finishPlaybackKeepCamera
(deferredReturn, PlayingPayload(false), MODE_FROZEN=4) БЕЗ телепорта/рестора; клиент в handleMode FROZEN
семплирует последний кадр в FROZEN[5], играемый трек обнуляет, onCameraAngles держит FROZEN-кадр,
onMovementInput глушит, скролл игнорится; sendGraph показывает DlgView на замороженном фоне (спектатор!).
Закрытие цепочки: stop() → resumeAfterDeferral (try/catch всё! headless-даблы падают без него) → телепорт
назад + restore. onLogout: deferredReturn → SessionsData.returnPos (onLogin уже умеет). Payload-вход теперь
пускает не только опов, но и isPlaying (иначе не-оп застревал). Doubles: CompoundTag +putDouble/getDouble/
putIntArray/getIntArray/contains(1 и 2 арг)/put(String,Tag)+getList + новые ListTag(Tag,add(Object)boolean!)
и Tag — БЕЗ ЭТОГО save()/load() в тестах падает NoSuchMethodError (компиляция даблов идёт против РЕАЛЬНОГО
CompoundTag из toolchain — дескрипторы обязаны совпадать!). ЯКОРЬ: цвет-цепь на ~1711, имя-цепь ~1729 —
обе с RADIUS-ветками (143). Детальки нод 1755+: radius ветка TYPE RADIUS.
- JAR sha256 `88f7703e20d112cbd2c19805a3bf47fe042dad54b114657c8138548478eeaeea`
- ZIP sha256 `3f0fa982e3957a1bceb3c9b61ac1363a3773a0b5b4f21437b72b22547066c676` · backup-преф — в отчёте
- Тесты: ALL PASS 991 (§15: wire-обнаружение, copy, NBT-roundtrip, дефолт-false у старых сейвов).
**Build 150 «radius-gate» (2026-10-23, принят).** Всё из 149 + **прикреплённые радиусы теперь полностью
владеют списком исполнителей целевой ноды**: если у ноды есть входящие прикрепления RADIUS, инициатор
выполняет её action ТОЛЬКО когда есть прикрепление игрокового режима (Игроки/Все) И инициатор внутри его
радиуса. Чисто мобовые прикрепления (Мирные/Монстры/Другие NPC) → инициатор action не выполняет вовсе
(сценарий пользователя: #3→#5 с прикреплённым #4(Мирные) ⇒ телепортируется только лама; раньше прямой
провод #3→#5 телепортировал и игрока). Реализация: findRadiusAttachments (вынесен из 149-хелпера) +
initiatorExecutesWithRadiusGate + radiusGateSkip в TRIGGER-case walker'а и trigGateSkip в очереди триггеров
(оба action-сайта), expandRadiusAttachments принимает готовый список + tracker. Без прикреплений поведение
как в 149. Тесты §14 добиты: mob-only ⇒ 1 исполнение (лама), players-прикрепление внутри ⇒ 2, инициатор
вне радиуса players-прикрепления ⇒ 1. ЯКОРЬ-ПОДВОХ: тело `if (!cur.action.isBlank() && npc != null &&
(player != null || mobContext)) {...}` повторяется 4 раза (кейсы-шаблоны) — патчить с заголовком кейса.
- JAR sha256 `ccd10637980cdcaaf2bf1b792141c3c088ea077af4436faf74a116d2e108cc71`
- ZIP sha256 `26accec829ec1a43674f07e923ecc47d3024ce1bae978803ba264deceeb48d5f` · backup-преф — в отчёте
- Тесты: ALL PASS 986.
**Build 149 «radius-attach» (2026-10-23, принят — заменён 150 по фидбэку «телепортирует и игрока»).** Всё из 148 + **RADIUS-нода может работать как
прикреплённая, как IF-условие**: провод от выхода «В радиусе» в любую ноду БЕЗ входа радиуса в поток.
Когда целевая нода исполняется инициатором (и через walker, и через очередь триггеров LINE-продвижения),
все прикреплённые радиусы разворачиваются: каждый мэтч выполняет ЦЕЛЕВУЮ ноду от своего контекста.
Реализация: expandRadiusAttachments (поиск src.next==target || src.triggers.contains(target)), сентинел
FAN_SNAPSHOT_SENTINEL в снапшотах фан-аутов (4 места заменены на fanSnapshot()) — подавляет ре-экспансию,
initiatorContext=!mobContext; защита от двойного срабатывания: радиус, УЖЕ исполненный потоком в этом
кейскейде (id в executedTracker), не разворачивается повторно как прикреплённый (его фан+инициаторский
прогон уже отработали цель). Мэтчи дедуплицируются IdentityHashMap (несколько прикреплённых радиусов,
одна сущность — одно исполнение). Центр = NPC (fallback игрок). Логи: `[radius] attach fan node #R ->
target #T`. Сценарий пользователя: #0→#3→#5 + #4(passive)→#5 + #6(players)→#5 ⇒ лама И игрок по разу.
Тесты §14: walker-путь (ровно 2 исполнения), очередь триггеров через reflection (ровно 2, нет петли),
провод в triggers/next. СРЕДА: /home/user/settledlands/jdk21 НЕ персистентен между тернами
(снапшот ~128МБ) — каждая сессия: bash /home/user/settledlands/ensure_jdk.sh заново! И patch-src может
регрессировать на границе тернов — начинать с рестора из ПОСЛЕДНЕГО source-ZIP в /home/user.
- JAR sha256 `9822e1fa7e1cb0d47fffac35628a5cd1bd792bdaf7463f2e8b8f477cb523e6ea`
- ZIP sha256 `cb89d87b4e29cc6b2b551e69e6485753c7edd864b10a1f24be0d3df72f1e155f` · backup-преф — в отчёте
- Тесты: ALL PASS 983.
**Build 148 «mob-only» (2026-10-22, принят).** Всё из 147 + по просьбе пользователя «триггер должен
срабатывать только на ламу»: **прогон триггеров инициатора после веера RADIUS-ноды теперь выполняется
ТОЛЬКО в игроковых режимах (Игроки=0 / Все=3)**; в мобовых (Другие NPC / Монстры / Мирные мобы) инициатор
выполняет только продолжение графа (cur.next), а свои триггеры — нет; в лог пишется
`[radius] node #N initiator trigger pass skipped (mob-only mode T)`. Причина бага пользователя в 146/147:
у ноды «В радиусе» не было входящей связи (левый порт пуст) — найдено по скриншоту графа, код бился исправно.
В тестах §13: PASSIVE → ровно 1 исполнение и только от ламы; новый ассерт NPC-режима (дублёр GuideNpc
САМ регистрируется в level.entities при конструкторе — второй .add даёт двойное исполнение!). ВАЖНО О СРЕДЕ:
/tmp/jdk21 не персистентен — использовать /home/user/settledlands/jdk21 (ensure_jdk.sh); после каждой
переупаковки дерева: rm -f SOURCE-MANIFEST.json; у package_source.py default --output нужно править
на имя текущего билда (скрипт берётся из донорского дерева!). СОБЛЮДАТЬ: после сборки strings-проверка
JAR на маркеры билда (случился инцидент: дерево patch-src регрессировало до до-142, а кэш javac выдал
корректный JAR — 147 собрался «правильно» из кэша).
- JAR sha256 `6a6b6ff997655a393fb6b5b83e38a10688f35aaf951047de8684c9fea1376bca`
- ZIP sha256 `36f64c75fcfe4437a37a8af6f6033f72b7c1b05e48401043b38a04657dcdd5e2` · backup-преф — в отчёте
- Тесты: ALL PASS 977.
**Build 147 «radius-trace» (2026-10-22, принят — скриншот графа от пользователя получен, причина найдена).**
Всё из 146 + трассировка графа: лог `[graph] walk starts at node #N (type T, ctx player|mob)` при входе в
resolveExecutionTarget; на КАЖДОЙ ноде обходчика `[graph] walk hits node #N type T ctx ...` (+ строка
not-found, если walker ушёл на id, которого нет в графе); в тикере проверок `[graph] check node #N target T
radius R scanning...` перед сканом и `[graph] check node #N FIRED on <имя>` при срабатывании CHECK-ноды.
ВАЖНО при повторном патче: шаблон цикла обходчика встречается ДВАЖДЫ (resolveExecutionTarget и
peekExecutionTarget) — патчить первым вхождением (replace count=1). Логи в latest.log по подстрокам
«[graph]» и «[radius]».
- JAR sha256 `23df12c21383730a0432539d8b4126f25096764d5c2209542305b9a2eb1d26fe`
- ZIP sha256 `21afd0b5517e0ea7bef5c646a45b7aec3384db269954caf56e2c4e70032dd5ba` · backup-преф — в отчёте
- Тесты: ALL PASS 975.
**Build 146 «radius-diag» (2026-10-22, принят, лог от пользователя получен и разобран).** Всё из 145 +
диагностика в RADIUS-ноде: RADIUS_LOG (slf4j «settledlands») пишет INFO при попадании потока в ноду
(режим, радиус, центр, число триггеров), INFO по завершении веера (сколько сущностей затронуто),
WARN с именем сущности если её фан-аут упал (try/catch на КАЖДУЮ сущность — одна сущность больше
не может уронить весь веер и прогон для инициатора). Лог смотреть в latest.log по подстроке «[radius]».
ИТОГ приёма: в сессии пользователя ни одной строки «[radius]», поток до RADIUS-ноды не доходит вообще —
проблема выше по графу (или цепочка не стартует ровно так, как ожидалось). Следующий шаг — билд 147.
- JAR sha256 `992d7f5e926f51adb4a02c3a59c4be66147a1ffcc58bb349794912145ce66394`
- ZIP sha256 `0936ae540310a556cc23560955996367407b5afc4b67dae0edead83396fe97ee` · backup-преф `add0edc7e9d51b78`
- Тесты: ALL PASS 975.
**Build 145 «radius-triggers» (2026-10-22, принят).** Всё из 144 + корень бага «лама не телепортируется»:
**connect() при линковке ЛЮБОЙ ноды в TRIGGER кладёт триггер в `a.triggers`, а не в `a.next`** (архитектура
редактора!) → фан-аут мобов шёл в `cur.next == -1`, а триггер один раз исполнялся самим радиусом для
инициатора. Фикс двухчастный: (1) фан-аут теперь выполняет `cur.triggers` ДЛЯ КАЖДОЙ подхваченной сущности
через executeGraphTriggers с её контекстом (в 143 проложен contextEntity → команда от имени сущности);
(2) собственный прогон триггеров радиуса перенесён ПОСЛЕ веера — иначе снэпшоты наследовали id триггеров
и каждая сущность их скипала (ловушка последовательности!). Пойман headless-репро: добавлены даблы
(Mob, 6-arg AABB, Entity.createCommandSourceStack, Commands.performPrefixedCommand-рекордер void!,
ServerLevel.getEntitiesOfClass 2-arg) и 7 ассертов в GraphRuntimeHarness §13 (ANY/PASSIVE/PLAYER/дистанция/
источник @s). До сих пор RADIUS-нода вообще ни одной нодой-тестом не покрывалась.
- JAR sha256 `cf4423cf03c9573ad837de0811c47c04de40ecc9b59aec5db54a4ce62c552602` (локально)
- Исходный ZIP 1 400 035 b (299 файлов) · sha256 `e6cca0280f8af91394aed21ea5d9b1faeaa1ef69cbb2c41c121b5381296c93f4` (локально)
- Бэкап дерева: `SettledLands-Backup-Build145.zip` (sha — в отчёте приёмки)
- Тесты: ALL PASS 975.
**Build 144 «radius-copy» (2026-10-22, принят).** Всё из 143 + корень бага «выбор режима сбрасывается на Игроки»:
**Node.copy() не переносил radius/radiusTarget** — а через graph.copy() идут undo-снимки экрана и
NpcProfile.copy() (GuideNpc.apply). Любое применение/переоткрытие тихо обнуляло оба поля → дропдаун
падал в default «Игроки», число-радиус — в 10. Добавлено `n.radius=radius;n.radiusTarget=radiusTarget;`
рядом с checkTarget-блоком. Аудит: javap `$Node` ≥3 упоминаний radiusTarget/radius (доказательство copy).
- JAR 863 286 b · sha256 `e9e95629e6e46f403fa3c93ecbfaa90e7c2af005d83cd4a42f1f1d963e964cfe` (локально)
- Исходный ZIP 1 394 432 b (298 файлов) · sha256 `b19e064903cc0d373519226459fd52c5ef84209b245f5501285576134f445ac5` (локально)
- Бэкап дерева: `SettledLands-Backup-Build144.zip` (sha — в отчёте приёмки)
- Тесты: ALL PASS 968.
**Build 143 «radius-fixes» (2026-10-22, принят).** Всё из 142 + три фикса по фидбэку:
**1) режим «Мирные мобы» (TARGET_PASSIVE=4)**: Mob не Enemy не GuideNpc (животные, жители и т.п.);
режим «Все» теперь покрывает и их тоже (игроки+NPC+монстры+мирные, ветки дизъюнктны). Клампы 0..4.
**2) команды фан-аута исполняются ОТ ИМЕНИ задетой сущности**: NpcActions.runForEntity(Entity,...)
— CommandSourceStack строится на сущности, поэтому `tp ~ ~5 ~` (авто-вставка @s) телепортирует именно её;
trigger-формы (focus/go/anim/heal) остаются NPC-центричными. runNodeAction-хелпер, executeGraphTriggers
получил перегрузку с contextEntity (11+10 сайтов переведены, старые делегируют).
**3) каждая сущность получает СНЭПШОТ executedTracker** (`new HashSet<>(executedTracker)`): защита от
циклов radius↔radius сохранена, но общие downstream-ноды (триггер!) теперь выполняются раз на сущность
(раньше срабатывали один раз на весь веер — включая игроков в 141/142).
**+ найден и починен издевательский UI-баг:** тернарные цепочки заголовка/цвета ноды в drawNode НЕ имели
ветки RADIUS (их не трогали с 132 билда для CUTSCENE, забыли и для RADIUS) → новая нода рисовалась
фиолетовой с именем «Случайность» (default-ветки). Добавлено: цвет 0xff2f6fa3, lang `radius_title`.
- JAR 863 274 b · sha256 `99a4697015e498102b88da293348f4a696ae842748c2b08e396a049045abf3bc` (локально)
- Исходный ZIP 1 394 939 b (298 файлов) · sha256 `68580167f4fcea42bf45c9ee4cd3b2ae35c72eb0a0a47d7d039140c0c92a3b6f` (локально)
- Бэкап дерева: `SettledLands-Backup-Build143.zip` (sha — в отчёте приёмки)
- Тесты: ALL PASS 967 (аудит runForEntity в байткоде).
**Build 142 «radius-modes» (2026-10-22, принят).** Всё из 141 + режимы цели у ноды «В радиусе»:
выпадающий список **«Кого подхватить»** (как у ноды Проверка): **Игроки** (по умолчанию, веер как в 141),
**Другие NPC**, **Монстры**, **Все**. NBT `RadiusTarget` (0..3, кламп), дупликация, detail-строка «👥 тип»
на канвасе, переиспользованы lang-ключи target_* ноды Проверка (+ новый radius_target).
- Семантика (по уточнению пользователя): моб-режимы — для триггеров и будущих нод; мобы проходят по ветке
  с контекстом сущности: диалог/катсцена для них no-op (LINE → -1, CutsceneCamera.play под player!=null),
  NPC-side ноды (trigger/action/animation/move/mobility/if/check) срабатывают. Перегрузка
  resolveExecutionTarget(player, Entity contextEntity, ...), mobContext расширяет guard экшенов.
- Гейт инициатора: режимы с игроками (Игроки/Все) — продолжает только внутри радиуса;
  моб-режимы (NPC/Монстры) — не блокируют инициатора никогда.
- JAR 862 371 b · sha256 `b576ea6525626d7ccb81f786b34348ce5574b387acc0ce1bf40c5598123c2935` (локально)
- Исходный ZIP 1 391 036 b (296 файлов) · sha256 `303c404c0ac9df7dec9ad910849d3d85b8daa607093c7be8af5b5325a17ff09a` (локально)
- Бэкап дерева: `SettledLands-Backup-Build142.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 966.
**Build 141 «radius-node» (2026-10-22, принят).** Всё из 140 + новая нода графа NPC **«В радиусе» (RADIUS=15)**:
числовой разветвитель-веер. Когда поток доходит до ноды — ВСЕ игроки в радиусе N блоков от NPC
(фолбэк-центр — инициатор; N=1..96, default 10, NBT `Radius`, NBT-кламп) выполняют downstream-ветку:
поставить перед CUTSCENE → все в радиусе попадают в катсцену; перед TRIGGER/действиями — то же.
Инициатор продолжает поток только если сам внутри радиуса, иначе его ветка заканчивается.
- Реализация: fan-out рекурсией resolveExecutionTarget(bystander, npc, null, graph, cur.next, executedTracker)
  по сфере ≤R (AABB-префильтр), cross-call guard `executedTracker.add(cur.id)==false → стоп` (защита от
  циклов radius↔radius); action+triggers ноды бегут 1 раз до fan-out; peek — pass-through.
- Редактор: 16-я строка ПКМ-меню («+ В радиусе»), инспектор-число, detail-строка «◎ Радиус: N» на канвасе,
  дупликация, lang en/ru; меню TOTAL_ROWS=16, harness под 11 max-scroll.
- JAR 861 763 b · sha256 `e6af9312ed64cdceb9a1bfc779e8e57030b6eda186afde0ff663c9fcbfc5dfb9` (локально)
- Исходный ZIP 1 390 053 b (297 файлов) · sha256 `32fd9e53309926082f857005a170c1f9fb7e6f7ce14bbc75faef87a94fc695e7` (локально)
- Бэкап дерева: `SettledLands-Backup-Build141.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 965.
**Build 140 «smooth-focus» (2026-10-22, принят).** Всё из 139 + два уточнения по фидбэку:
**1) плавность 0..20** (было 0..9): та же формула alpha=1/(1+s*0.6), на 20 ≈ 0.7 c догона —
предельно ватная кинематика. Шаг по колёсику = 1.
**2) плавность больше НЕ трогает передвижение**: позиционная инерция (applyInertia/setDeltaMovement)
УДАЛЕНА из клиента целиком — WASD летает как ванилла, гладятся только yaw/pitch покадровым
smoothLook. Отдельные ручки не нужны — одна плавность = только поворот; скорость — только колёсико.
- JAR 861 054 b · sha256 `96c78755d05664aaf9621c269a3c4feac71654e4435f781e5312928153cfe742` (локально)
- Исходный ZIP 1 389 422 b (297 файлов) · sha256 `7fb7440e3a636f06499d48c285023e0cebb07233be4d235d661231a025e7e74d` (локально)
- Бэкап дерева: `SettledLands-Backup-Build140.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 962 (чек инвертирован: setDeltaMovement НЕ должен быть в клиенте).
**Build 139 «camera-feel» (2026-10-22, принят).** Всё из 138 + две добивки по фидбэку:
**1) Студия наконец-то в кадре:** КОРЕНЬ найден javap'ом — ванильный `AbstractSelectionList.getRowWidth()`
HARDCODED = 220 → строки центрировались и вылезали за узкий список вправо (и композиция «уезжала»);
`SceneList.getRowWidth()` теперь override = `getWidth() - (scrollbarVisible()?14:8)`; лэйаутby-construction:
listW floor = ширина - margin*2 - gap - 104 (никогда не вылезет), compact-пороги 460/230, панель без
max-клампов-вправо.
**2) Плавность ПОВОРОТОВ (как геймпад-стик):** `smoothLook()` внутри onCameraAngles (перевёрнутая
структура: playing-ветка if-return, иначе smoothLook): раз в КАДР raw mouse-дельты копятся в
`lookTargetY/P`, а yaw/pitch подтягиваются к ним экспоненциально alpha=1/(1+smooth*0.6) (smooth=9 →
~0.3 c догона, сливочно); результаты пишутся и в entity (setYRot/YHeadRot/YBodyRot/XRot → сервер
записывает уже плавный трек), и в само событие (event.setYaw/Pitch → визуал без джиттера от yRotO-лаг).
lookInit=false при screen/pause/выходе/smooth=0 (ресинк без прыжка); ±90 кламп pitch; wrapDegrees на
всех дельтах. Shift+колёсико по-прежнему крутит 0..9 (та же ручка правит и позиционную инерцию).
- JAR 861 400 b · sha256 `c77ed0dc3a700b13384252c5773bd7246d39a17c8d6186f5a28cee83d514f708` (локально)
- Исходный ZIP 1 389 395 b (297 файлов) · sha256 `e7b67d0ab38d1fca2dcbdb7f91e14e010090491bc6bc21ceff5e0bdaadd432de` (локально)
- Бэкап дерева: `SettledLands-Backup-Build139.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 961. Уроки: (а) `getRowWidth()=220` у AbstractSelectionList — всегда override в
  узких списках; (б) НЕ переименовывать билд «на ходу»: sed package_source шаблоном `Build\d+-Cutscene-*`,
  имя-исключение (Studio-Fit) паттерн не ловит — проверять PREFIX глазами; (в) RenderFrameEvent в
  NeoForge 21.1 НЕТ — покадровые хуки через ViewportEvent.ComputeCameraAngles.
**Build 138 «cutscene-flight» (2026-10-21, принят).** Всё из 137 + переупаковка скорости/плавности
по фидбэку (ползунки студии «ни на что не влияли» по ощущению — это была скорость ВОСПРОИЗВЕДЕНИЯ,
ожидалось управление полётом при записи):
**1) Скорость/плавность теперь живут в режиме камеры и крутятся КОЛЁСИКОМ:** scroll = скорость полёта
спектатора ×0.25..×4 шаг 0.25 (`InputEvent.MouseScrollingEvent`, отменяем; клиент → throttled 100ms
`cutscene_flight` (perm2+spec) → сервер `CutsceneCamera.applyFlightSpeed`: flyingSpeed = сохранённый
при enter() базис × mul + `player.onUpdateAbilities()`; базис Map<UUID,Float> flightSpeeds, restore()
возвращает при выходе/релоге). **Shift+scroll = плавность 0..9** — КЛИЕНТСКАЯ инерция:
пост-тик setDeltaMovement(lerp(vanilla, prev, k=smooth/10)) → записываемый трек САМ получается сглаженным;
действие и смена — actionbar `cutscene.flight` + постоянная HUD-строка справа-сверху в режиме камеры
(CutsceneOverlay ветка !playing). handleMode(CAMERA/RECORDING) → flightDirty (mul переживает сессии,
переотправляется на входе); инерция сбрасывается. Отправка throttled + собрана в onClientTick ДО
playing-ветки.
**2) Студия адаптивная:** все размеры от width/height; compact-режим (<210 h / <400 w): строки 16px,
зазоры 3, список ≤160; узкая панель (<170) → имя и кнопка переименования СТЕКОМ; подсказки/инфо
переносятся через font.split (FormattedCharSequence, обрезка по нижнему краю); слайдеры скорости/
плавности ИЗ СТУДИИ УБРАНЫ (Protocol `cutscene_settings` и storage Sp/Sm оставлены — обратная
совместимость, UI больше не путает; воспроизведение всегда ×1/гладкость0 + вечный Catmull-Rom).
Строка-подсказка `studio.hint` в правой панели. J/J-закрытие, /cutscenes, «Студия…» в графе — как в 137.
- JAR 860 824 b · sha256 `b58a6b5811bd25e6bc9c8c8f3efe1caa48a275c49c6a8a2cf2dc1983714f5f48` (локально)
- Исходный ZIP 1 390 289 b (297 файлов) · sha256 `2953ba670496c5d375fef4b984ebfbf6d6e35e7a994aa202da70c5230dc76316` (локально)
- Бэкап дерева: `SettledLands-Backup-Build138.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 960. Уроки: (а) откат стирает и libs/ → jsr305-3.0.2.jar перескачивать curl'ом
  (sha `766ad2a0...28d0c7`), он НЕ входит в Source-ZIP, но входит в backup-ZIP; (б) `onUpdateAbilities()`
  только у ServerPlayer/Player, у Abilities такого метода НЕТ (javap двух классов подряд сливается
  в выводе — проверять по отдельности!); (в) аудит-чек listW/ламбд — только по nested-классам.
**Build 137 «cutscene-studio» (2026-10-21, принят).** Всё из 136 + две крупные фичи по запросу:
**1) Настройки скорости и плавности — НА СЦЕНУ (сервер, `CutsceneStorage.Scene{speed:float,smooth:int}`,
NBT "Sp"/"Sm", дефолт 1.0/0, обратная совместимость со старыми мирами).** Скорость ×0.25..×4 (шаг 0.25):
клиентский playhead `double` теперь шагает `speed` ключей/тик (frame t = playhead + (partial-1)*speed),
конец сцены клиент сам детектит (playhead >= len-1 → requestEnd, без серверного таймера); entity-sync
тикает по sample(playhead) через общий `sample(t)` (Catmull-Rom + lerp углов в double[5] SAMPLE-буфер,
zero-alloc). Плавность 0..3: пре-фильтр трека ядром [0.25,0.5,0.25] × N проходов в handlePlay ПОСЛЕ
yaw-unwrap (оригинал на сервере не трогаем — настройку можно крутить когда угодно).
**2) «Студия катсцен» — отдельный экран `CutsceneStudioScreen`** (ObjectSelectionList слева с ×-метой,
панель справа: EditBox имени + Переименовать, два snapping-слайдера (AbstractSliderButton), строка
длительности (keys/20/speed), ▶ Просмотр, ✕ Удалить (double-confirm), Готово). Открытие: кейбинд J
(`CutsceneStudioKeys` ModBus=RegisterKeyMappingsEvent + GameBus=ClientTickEvent consumeClick,
toggle-закрытие), команда `/cutscenes` (`CutsceneCommands`), кнопка «Студия…» в инспекторе графа —
всё через round-trip: `cutscene_studio_req` (c→s) → sendSceneList(player) + `cutscene_studio_open` (s→c)
→ setScreen. Экран авто-refresh'ится из handleScenes (instanceof CutsceneStudioScreen → refresh(),
выделение держится по имени сквозь переименование/резайз). Пакеты: `cutscene_rename`/`cutscene_settings`
/`cutscene_playreq` — все perm 2+, валидация имени 1..32 без % * / \ (isValidName, сервер-авторитет),
rename сохраняет порядок (LinkedHashMap rebuild), exists → msg rename_exists. Scene-list пакет расширен:
Entry(name,keys,speed,smooth) — графовый dropdown НЕ тронут (CutsceneCameraClient.scenes() List<String>
сохранён, handleScenes строит его из infos; `sendSceneList(ServerPlayer)`-оверлоад, CamEvents.login
переведён на него).
- JAR 857 464 b · sha256 `e34d4ea85da57da726a5d33d38a166517c42dadd0c709402ba422faf825316f4` (локально)
- Исходный ZIP 1 383 548 b (294 файла) · sha256 `0af436e27ece691dd202b5ba432cd6c721d2e4cf83f1c631a57607953d91d5bd` (локально)
- Бэкап дерева: `SettledLands-Backup-Build137.zip` (sha финального архива — в отчёте приёмки)
- Тесты: ALL PASS 957. Уроки: (а) ObjectSelectionList несёт `javax.annotation.Nullable` → javac
  CompletionFailure без jsr305 → **libs/jsr305-3.0.2.jar** (sha256 `766ad2a0...28d0c7`, подхватывается
  glob'ом extra_libs автоматически — НЕ УДАЛЯТЬ); (б) аудит-маркеры вложенных классов лежат в
  ClassName$Nested — инспектировать отдельно; (в) package_source запускать СТРОГО после ALL PASS
  (в 136 `| tail` замаскировал exit code, ZIP уехал со старым аудитом — отсюда «возвращающийся» фикс).
**Build 136 «cutscene-frame» (2026-10-21, принят).** Всё из 135 + три жалобы по фидбэку рваности камеры:
**1) НАСТОЯЩИЙ покадровый рендер камеры** — тик-степпинг 134 и unwrap 135 не убрали рывки окончательно:
entity живёт 20 tps, а рендер 60+ fps → теперь камера вычисляется КАЖДЫЙ КАДР в
`ViewportEvent.ComputeCameraAngles`: Catmull-Rom по 4 ключам для x/y/z (t=(trackTick-1)+partial,
partial=`mc.getTimer().getGameTimeDeltaPartialTick(true)`) + Mth.lerp yaw/pitch; позиция — reflection
в PROTECTED `Camera.setPosition` (MethodHandles.privateLookupIn+unreflect, invokeExact (Camera,double,double,double)void,
кэш MethodHandle). У события getPartialTick НЕТ, у Minecraft getDeltaTracker() НЕТ (1.21.1) — только getTimer().
onClientTick ужат до сервер-синка entity (чанки/коллизии); unwrap при handlePlay сохранён.
**2) локализация кнопки удаления** — сломал седьмой откат (lang к ~133): все cutscene-ключи 131-136
(16/язык) теперь заливаются ИДЕМПОТЕНТНО одним json.update при каждой сборке-приёмке.
**3) удалить ВСЕ катсцены** — вторая кнопка «Удалить ВСЕ ✕» (double-confirm ✔?) в панели CUTSCENE-ноды;
wildcard "*" в `cutscene_delete` → CutsceneStorage.clear() + sendSceneList + msg deleted_all (perm 2+);
одна сцена — storage.delete + msg deleted. Поля pendingSceneDelete + pendingSceneDeleteAll.
- JAR 828 367 b · sha256 `9c93217414b2d92e813d7efac3d6046d43fd381bbe7272adae0dd605762b7b66` (локально)
- Исходный ZIP 1 356 467 b (277 файлов) · sha256 `ab30854a3bfbd2a27b76804c22af9238d2387c3e32c392800e4269a88822d9af` (локально)
- Бэкап дерева: `SettledLands-Backup-Build136.zip` (sha256 финального архива — в отчёте приёмки; вшитый гайд меняет сумму)
- Тесты: ALL PASS 945 (audit-чеки: ComputeCameraAngles, MethodHandles, setPosition, getGameTimeDeltaPartialTick,
  storage delete/clear, wildcard deleted_all, MAX_KEYS play-payload).
**Build 135 «cutscene-polish» (2026-10-21, принят).** Всё из 134 (клиентский трек + eye-поправка +
возврат на старт) + фидбэк: **развёртка yaw** при старте показа (yaw завёрнут в ±180; через границу
интерполяция вила камеру назад на 359° за тик — вот и рывки на поворотах; лечится Mth.wrapDegrees
дельтой подряд); **удаление катсцен**: кнопка ✕ (двойное подтверждение ✔?) рядом с dropdown в инспекторе
CUTSCENE-ноды → `cutscene_delete` (perm 2+) → CutsceneStorage.delete + sendSceneList. Поле экрана
`pendingSceneDelete`. Drive-коннектор мёртв (раздел альтернатив в шапке); откат песочницы между 134-135
откатил 3 файла к 133 (переписаны заново + объединены) — ВСЕГДА делать аудит маркеров после отката.
- JAR 826 627 b · sha256 `34544f0fd606ddf31c03baf48ae811e018864dacceb6c486598c7bbfc73a66ec` (локально)
- Исходный ZIP (277 файлов) · sha256 `a4852a7f319b32587540dc0dbee9485342c0e1f57d2ae5e5031b911e4c7ebf76` (локально)
- Старшие — локально + Диск до b132. Тесты: ALL PASS 941. Проект: `/home/user/settledlands/build92/`.
**1) плавность** — НЕ серверные телепорты (20 Гц рывки), а трек целиком на клиент (`cutscene_play`,
double[][] keys ×5) + шаг 1 ключ/клиентский тик через setPos/setRot: ванильный xo/x-lerp рендерит
настоящие 60fps; ввод игрока подавляется (MovementInputUpdateEvent, класс net.minecraft.client.player.Input!).
**2) +1 блок** — ключи = eye-position, при показе вычитаем player.getEyeHeight() (компенсация ног).
**3) конец сцены** — возврат в точку старта: SavedData sessions хранит returnPos (rx,ry,rz) + gm,
finishPlayback/релог возвращают туда; playingReturn map в CutsceneCamera.
- JAR 823 865 b · sha256 `95b625a26f3446e858b1cb6378f7dc7b69629ca3f6e6d9283b4c1d7ea5bad89f` · Drive ID (pending)
- Исходный ZIP (275 файлов) · sha256 `c2189fd55453c11c03ddf508b476a1036746a28b16495341e76458fad670918a` · Drive ID (pending)
- Build 133: JAR sha `e53d88c7...`, ZIP `0e833f06...` — тоже pending заливки (коннектор off).
- Build 132: JAR `1zQ0G091s4meorM10k76s6CjlHDlWB1CY`, ZIP `1ZS1yIlEbOg9FOIX4DgCbpshXlZBSa7GZ`, бэкап `1GYjb8BDKvjatGxgTXnt82svgGjt5oNix`. Старшие — на Drive.
- Тесты: ALL PASS 939. Проект: `/home/user/settledlands/build92/`. Входные файлы сборки (не удалять):
  `baseline-build83.jar`, `SettledLands-Build116-Mobility-Extended-Source.zip`.
**Каждый:** спектатор-инвентарь не даёт выбрать жезл → клавиши катсцены больше НЕ требуют жезла:
ПКМ=REC тоггл, ЛКМ=выход (сервер при записи сперва остановит её), в показе ЛКМ/ПКМ=отмена.
**Меню нод:** строка CUTSCENE возвращала белый default-цвет → case 14 = 0xfff0abfc; «+» в меню
берётся из самих ланг-строк (add_cutscene → "+ Катсцена (Cutscene)"/"+ Cutscene").
- JAR 821 084 b · sha256 `e53d88c78f8653e0d5869b0daa53e117fd5ec9587b10f356c392c74f2866d66a` · Drive ID (pending: коннектор был off)
- Исходный ZIP 1 347 xxx b (273 файла) · sha256 `0e833f065946f075e9a112ae02109ce5a0eb76a5d2d89d794816dfbe8a81eea6` · Drive ID (pending)
- Build 132: JAR `1zQ0G091s4meorM10k76s6CjlHDlWB1CY`, ZIP `1ZS1yIlEbOg9FOIX4DgCbpshXlZBSa7GZ`, бэкап `1GYjb8BDKvjatGxgTXnt82svgGjt5oNix`. Старшие — на Drive.
- Тесты: ALL PASS 937. Проект: `/home/user/settledlands/build92/`. Входные файлы сборки (не удалять):
  `baseline-build83.jar`, `SettledLands-Build116-Mobility-Extended-Source.zip`.

**Предыдущее состояние (132 «cutscene-full», 2026-10-21).** Всё из 131 + СИСТЕМА КАТСЦЕН целиком:
**запись** — в режиме камеры ПКМ (без жезла в руке) = REC/стоп; ключи eye-pos+yaw/pitch снимает СЕРВЕР
каждый тик (Set.of() телепорты), сохранение `settledlands_cutscenes` SavedData, автоимена scene_001…;
**воспроизведение** — сервер такт за тактом телепортирует spectator-игрока по ключам, на клиенте
чёрные кинополосы + скрытый HUD (`cutscene_playing`), любой ПКМ = отмена; после конца гейммод восстановлен.
**Нода CUTSCENE (=14)** — меню + всё визуальное (цвет/имя/подзаголовок/цвет портов), инспектор с
НАСТОЯЩИМ dropdown `NpcUi.dropdown` (имена прилетают пакетом `cutscene_scene_list`; синк: логин опов,
после записи — всем). Нода ТЕРМИНАЛЬНАЯ (curId=-1), киноприём закрывает экран диалога.
Управление ПКМ в spectator (client intercept по mode: 0/1/2/3): wand-в-руке=выход камеры, иначе REC-тоггл.
- JAR 821 145 b · sha256 `5733e04d157f676cdfecc247930599f303eb875e084f8dca581a516cdb9afc3a` · Drive ID `1zQ0G091s4meorM10k76s6CjlHDlWB1CY`
- Исходный ZIP 1 347 115 b (273 файла) · sha256 `6b5415b2bf9b0e6fd6b78d72f340a5974057bdd5176d051c4a264e6258550197` · Drive ID `1ZS1yIlEbOg9FOIX4DgCbpshXlZBSa7GZ`
- Build 131: JAR `1aJjFcKKGK8s3u7hXGUTuoNH8USBdaGGk`, ZIP `186pAhCHVQ8qzbno-Ud60xFWMAcub15Ok`, бэкап `1Nsnr8ok6CLJxEXfOYmArUMc860wE65VJ`. Старшие — на Drive.
- Тесты: ALL PASS 937. Проект: `/home/user/settledlands/build92/`. Входные файлы сборки (не удалять):
  `baseline-build83.jar`, `SettledLands-Build116-Mobility-Extended-Source.zip`.
- Версионные строки живут в ТРЁХ местах: `build_patch.py VERSION`, `package_source.py PREFIX/--output`,
  `resources/META-INF/neoforge.mods.toml` (иначе «Version metadata mismatch»).
- Кэши toolchain: `--cache .cache/compile88` для тестов; `prepare_classpath.py` пересоздаёт `.cache/settledlands84`.

## Чек-лист старта сессии
1. Прочитать этот гайд. 2. Аудит §А против последнего ZIP из «Состояния» → восстановить расхождения.
3. `/tmp/jdk21` на месте (иначе §Б). 4. Только после чистого аудита — правки. 5. В конце — правило №7.

## Обновление гайда
После каждой приёмки: обновить «Маркеры» и «Состояние», гайд упаковать в бэкап дерева (§В), сообщить пользователю.


## Build 163 «purgatory-corpse» (2026-10-24)
- Смерть игрока → GuideCorpseNpc (settledlands:guide_corpse) на месте смерти (CorpseLifecycle, GAME-bus LivingDeathEvent LOWEST). Лежачий рендер: поворот 90° Z в GuideRenderer.renderCorpse; без bb-модели тела — ванильный GuideModel.
- CoffinItem (settledlands:coffin): ПКМ по трупу → coffin-режим (consume except creative). Только в coffin: canBeLeashed() (1.21.1 Leashable no-arg!). mobInteract порядок: coffin → lead (setLeashedTo/dropLeash) → super.
- Труп тикает каждые 20 тиков (GuideCorpseNpc.tickCorpseState): PurgatoryData.get(level).altars() — алтари живут в данных СВОЕГО измерения; очередь мертвецов — на getMaster(server). Гроб в радиусе 4 → revive: owner online+alive → clearFire/delta 0/fallDistance 0/teleportTo(level, x+0.5, y+1.05, z+0.5, Set.of(), yaw, pitcher 0) + removeFromQueue; offline → removeFromQueue + setPendingRevive(uuid, "ns:path", altar). Владелец жив вне чистилища и не в очереди → discard (stale cleanup). Эффекты: visual lightning + souls.
- getPersistentData() НЕ виден компилятору из чистого minecraft-compile.jar → каст ((IEntityExtension) owner).getPersistentData().
- PlayerBodyRenderer (Dist.CLIENT): RenderPlayerEvent.Pre → если BbModelStorage содержит player_body.bbmodel (папка GAMEDIR/settledlands/models), отменяем ваниль и рисуем BbModel.renderActor (setupAnimRaw: talking=false, custom off, lookAtPlayer=true). Дефолтный комплект corpse_coffin.bbmodel/coffin.png кладёт NpcClient.installDefaultCorpseAssets() из /assets/settledlands/corpse_default/.
- Сборочная сумка: докачан libs/jsr305-3.0.2.jar (javax.annotation для IEntityExtension), версия 0.8.0-build163-purgatory-corpse. Тесты 991 PASS.


## Build 164 «dragonai» (2026-10-24)
- NpcProfile.aiMode 0/1 (NBT "AiMode") — обычный NPC или драконий боевой AI; NpcEdit кейс "aiMode"; чекбокс gui.settledlands.field.aimode в NpcConfigScreen.
- IvyCombatAi: порт бокс-мозга Ivy (Saint's Dragons, MIT; ассеты — разрешение соавтора). Revenge-таргет 120т, диалог/поводок выше боя. ATTACK_RANGE 2.45, dash 2.35..5, throw 5..16 (3 SmallFireball), dodge 40% на двойной подряд урон, ATTACK_DAMAGE 4.0 база. Комбо-таблица Attack enum с impactTicks. customAnim-слой для анимаций (orthodox_* / dodge_*).
- Модель: tools/geo2bbmodel.py конвертирует GeckoLib geo.json+animation.json+png → наш .bbmodel (box-UV faces, outliner-пивоты, keyframes pre/post). ivy.bbmodel (84 аним.) + ivy.png авто-инсталл через NpcClient.installDefaultCorpseAssets (packs). Рендер-fallback GuideRenderer: aiMode==1 && texture пюсто → "ivy". Синк aiMode: DATA_AI_MODE byte.
- Связка: GuideNpc.hurt → ivyCombat.onHurt; customServerAiStep → ivyCombat.tick() первым.


## Build 165 «ivybody» (2026-10-24)
- PlayerBodyRenderer: игрок рендерится `player_body.bbmodel` если есть, иначе fallback на bundled `ivy.bbmodel` (была только player_body), иначе ванилла. То есть по умолчанию игрок = Ivy. Никаких новых файлов и полей, 991 PASS.


## Build 166 «ivyfix» (2026-10-25)
- BbModel.findAnimation: exact match first, substring fallback. Блoк bugfix: в ivy-паке 84 анимации и contains("idle") ловил orthodox_idle/swim_idle по HashMap-порядку → вечная боксерская стойка у игрока/NPC. Аналогично чинит walk/talk коллизии.
- Workspace rollback из Backup-Build165.zip (source-zip-165 терялся в rollback); тулчейн пересобран (jdk21+libs из zip-163, jsr305 скачан).

## Build 217 «player-corpse» (2026-10-26)

Смерть вселённого игрока оформляется как смерть NPC: труп (стоковый GuideCorpseNpc c
setOwner) получает профиль тела (модель/имя из PossessionSystem) в момент спавна и дальше
идёт стоковым путём (гроб → поводок → маяк возрождения → GuideCorpseNpc.tickCorpseState
→ reviveOwner: removeFromQueue + телепорт к алтарю + setPendingRevive). Сразу после
стокового воскрешения срабатывает авто-вселение: ServerTickEvent.Post-watcher видит
игрока живым, вне очереди чистилища и с меткой settledlands_body_pending → выставляет
settledlands_possess/settledlands_possess_model/settledlands_possess_name (ключи 216),
снимает bodiless-эффект (PossessionSystem.bodiless) и шлёт PossessionClientSync.push.

Точки расширения/риски:
- Маркер тела хранится в persistent-данных ИГРОКА (перенос через PlayerEvent.Clone);
  у трупа — settledlands_player_body(+_model/_name) — чисто диагностика, труп дискардится.
- Приватное поле GuideCorpseNpc.ownerUuid читается reflection'ом (мод не обфусцирован).
  Если поле переименуют — труп просто не получит облик тела, воскрешение/вселение не ломаются.
- EntityJoinLevelEvent-метка принимается только для трупов с tickCount<=2 (новорождённый
  в тик смерти), иначе чанковые загрузки старых трупов могли бы перехватить чужую метку.
- Спам-гард: если игрок уже вселён в другого — авто-вселение пропускается (сообщение
  body_revived_busy), маркер очищается.
- Изумруды: для тела игрока сток их не требует (в отличие от NPC-пути 213/214) — оставлено так.
- PurgatoryData.getMaster(server).isDeadInQueue — единственный источник истины «игрок ещё
  в чистилище»; выход из очереди возможен только через reviveOwner/админ-poll — оба валидны.

### Build 218 (фикс 217): «маяк-only» для авто-вселения
Watcher на ServerTickEvent.Post перед авто-вселением проверяет nearRevivalAltar():
игрок должен стоять в пределах 5 блоков от любого BlockPos из PurgatoryData.altars().
reviveOwner телепортирует игрока на блок маяка, поэтому «жив + вне очереди + у маяка»
= воскрешён маяком. Сам-выход из чистилища → маркер settledlands_body_* снимается молча.

### ERRATUM 217/218: toml regression (ложный краш Quark/Zeta)
Слепая строковая вставка в neoforge.mods.toml съела открывающие ''' у description →
невалидный TOML → FML помечал jar невалидным и при показе экрана ошибки игра падала с
маскирующим "Where is minecraft???" (Quark #5663). Исправлено пересборкой из toml jar216.
ПРАВИЛО: после любой правки META-INF/neoforge.mods.toml — обязательная валидация
python tomllib перед репаком jar.

### Build 219 «corpse-guarantee»
DeadWatch: тик-наблюдение за свежей смертью (40 тиков), findOwnerCorpse (AABB 4б,
reflection ownerUuid); при отсутствии стокового трупа создаём свой
(NpcContent.guideCorpse().create + moveTo + setOwner + профиль/метка 217). Все шаги
в LOGGER. Наблюдение ставится на ЛЮБУЮ смерть игрока (не только вселённого) —
stock-first, без дублей. Зачем: в толстой сборке стоковый CorpseLifecycle эмпирически
не оставляет труп; механика заведомо не получала его.

### Build 220 «cult-bridge»
PossessionCultBridge (@EventBusSubscriber, HIGHEST, EntityInteractSpecific): перед
стоковым гейтом культа зеркалит «культ»=1 из NpcPoints(shared/personal) в npc
persistent «settledlands_local_vars». Зачем: нода переменной 215 пишет по скоупу
в NpcPoints, гейт 216 читает только npc-local → ложный отказ. Побочка: зеркаленный
культ остаётся на NPC навсегда. Каждая попытка вселения логируется (local/shared/personal).

### Build 221 «cult >= 1»
Гейт вселения ослаблен с ==1 на >=1 однобайтовым патчем PossessionSystem.class
(if_icmpeq 0x9F -> if_icmpge 0xA2 сразу после invokestatic cult(); паттерн-поиск
B8 00 FE 04 9F, ровно одно вхождение). Причина из лога: у NPC культ=17/18/19 —
нода переменной 215 инкрементит, строгий ==1 вечно отказывал. lang no_cult
перефразирован в «меньше 1». Мост PossessionCultBridge зеркалит при >=1.

### Build 222 «corpse-visible»
На "spawned our own" трупе: playCustomAnimation("die", false) + профиль тела
(как в сценах гибели NPC) — в 221 труп сервером создавался, но оставался невидимым/
нечитаемым клиентом. SurvivalCheck (+60 тиков): лог "corpse alive"/"VANISHED".
Игроку в чат координаты тела (body_left_at). Мост: текст лога под гейт >=1.

### ПРАВИЛО ВЫДАЧИ (усилено)
После каждой сборки: present_file jar ПЕРВЫМ, затем zip. По реплике «не скинул»/
«забыл мод» — НЕ пересобирать, просто present_file jar повторно. Перед выдачей
сверять sha256 файла на диске с объявленным.

### Build 223 «corpse-keep»
GuideCorpseKeepMixin (@Redirect x2 в GuideCorpseNpc.tickCorpseState, mixins в
settledlands.mixins.json): (1) discard() пропускается для помеченного
settledlands_player_body (сток дискардит труп владельца после его сам-выхода из
чистилища — из-за этого труп 219-222 исчезал через ~5c); (2) reviveOwner при
меченом трупе вызывается только если владелец ещё в deadQueue (иначе маяк
выдёргивал сам-вышедшего телепортом). Оригинальный private reviveOwner вызывается
reflection (компилироваться напрямую private нельзя). Вещи: LivingDropsEvent
(HIGHEST, possessed-only) -> NBT settledlands_body_items (Clone-копия), возврат в
repossess и при self-exit-снятии маркера.

### ERRATUM 223→224
@Redirect target = строго owner из constant pool вызова (javap -v Methodref),
а НЕ объявляющий класс метода. discard() в tickCorpseState инвокается с
owner=GuideCorpseNpc. Урок: для каждого INVOKE-таргета сверять Methodref #N.

### Build 225 «corpse-polish»
Вид: dressBodyCorpse() повторяет IvyDeathScene рецепт (setYRot/yBodyRot/yHeadRot,
setProfile+applyProfile, literal-name+visible, playCustomAnimation("die",false),
setHealth(1), setInvulnerable, ClientboundTeleportEntityPacket broadcast) — на обоих
путях (сток-join через PENDING и гарантия через DeadWatch; у обоих теперь повороты).
Изумруды: GuideCorpseKeepMixin.tryConsumeEmeralds (коробка y+0.9..y+1.75, 9 шт,
Items.EMERALD стеки на алтаре) — только для меченных трупов и только если владелец
в deadQueue. Лут: self-exit ветка watcher'а БОЛЬШЕ не restore'ит (только repossess);
повторные смерти merge-аппендят в settledlands_body_items.

### Build 226 «own-corpse items»
Вещи живут на трупе: pending ITEMS_KEY(settledlands_body_items, player NBT) → на
спавне трупа переписываются в corpse NBT (CORPSE_ITEMS_KEY) [onCorpseJoin и
guaranteeCorpses], буфер чистится. GuideCorpseKeepMixin при ревайве (после очереди
и изумрудов) переносит stacks corpse→player ITEMS_KEY; repossess их выдаёт. Merge
при повторной смерти УБРАН по явному требованию юзера (вещи не перелетают между
трупами). Ключи ITEMS_KEY/CORPSE_ITEMS_KEY public — миксин их инлайнит.

### Build 227 «restore» (2026-10-27)
ВОССТАНОВЛЕНИЕ потерянного в 173–215 + два бага 226. Метод: декомпиляция CFR 0.152
(/tmp/cfr.jar) шести ОТЛИЧАЮЩИХСЯ классов из jar172 (донор: бандл b188) и jar226,
ручное слияние (база = 172, дельты 226), чистка ~40 CFR-артефактов (slot-reuse:
`var15_93`, `object`-итераторы, dup-объявления в лямбдах, ternary-каскады),
перекомпиляция JDK21, наложение «хирургией» (build_surgery.py --base jar226).

**Слитые классы (overlay 13 файлов)**: GuideDialogue(+$Session+$Observation
+$PendingCutsceneReplica), NpcDialogueGraph(+$Node+$1), client/NpcDialogueGraphScreen
(+$Label), npc/PlayerCorpsePossession(+3 record). Контроль: javap-суперсет
(merged ⊇ 226 И ⊇ 172 по всем членам, лямбда-номера нормализованы) — /tmp/membercheck.py;
дескриптор CutsceneCamera.exit → GuideDialogue.onCutscenePlaybackEnded:(LServerPlayer;)I
совпал (в 226 был NoSuchMethodError — «тихий» отказ реплики); 175/175 lang-ключей
merged-классов есть в обеих локалях.

**Из 172 восстановлено**: тумблер «Реплика катсцены» (LINE с входящим CUTSCENE,
оранжевый заголовок -34278), PendingCutsceneReplica + onCutscenePlaybackEnded
(коды 0/1/2/3) + resumeGraphAfterCutscene + cutsceneConversationActive-гейты,
RADIUS-нода (тип 15): fan-out 4 режима (TARGET_PLAYER/NPC/MONSTER/ANY/PASSIVE),
инициатор-гейт, дистанционный гейт, radius 1–96 (def 10) / radiusTarget 0–4 NBT-клампы,
строка меню «+ Цель» (row 15, цвет -8467457), CHECK снова «В радиусе»/«+ В радиусе»,
кнопки del-one/del-all/«Студия…» + CutsceneDeletePayload/CutsceneStudioRequestPayload,
fanSnapshot/findRadiusAttachments/initiatorExecutesWithRadiusGate/expandRadiusAttachments,
RADIUS_LOG-диагностика, stop()-pending-guard.

**Из 226 сохранено (дельты поверх 172)**: varLocal (локальные переменные 215+):
Node.varLocal + NBT «VarLocal» (save/load/copy/connect-пропагация), тумблеры
«Локальная: вкл/выкл» в инспекторах VARIABLE и IF, local_flag-суффикс drawNode,
GuideDialogue.localVars/readVarValue/addVarValue (persistentData НПС, ключ
settledlands_local_vars, каст IEntityExtension), evalIfNode/walker-IF/walker-VARIABLE
на readVarValue/addVarValue. ВСЁ 226-остальное (модели, рендер, вселение, трупы) — из базы, не тронуто.

**Баг-1 (два трупа) фикс в PlayerCorpsePossession**: (a) STOCK_CORPSES
Map<ownerUUID,corpseUUID> — onCorpseJoin записывает каждый свежий стоковый труп
(tickCount<=2), guaranteeCorpses доверяет записи (remove → no duplicate spawn);
(b) чанк точки смерти выгружен (!level.isLoaded(BlockPos.containing(pos))) →
отложка +40t (списком deferred, НЕ правкой во время итерации — CME!), кап
UNLOADED_DEFER_MAX=6000t; (c) onPlayerDrops после буферизации сразу
findOwnerCorpse+movePendingItemsToCorpse (сток-труп к дропам уже создан —
LivingDeathEvent LOWEST раньше LivingDropsEvent).

**Lang-слияние**: union 698 ключей (172=685 + 13 новых 226: bodiless, local_flag/on/off,
npc_inventory, possession.*8); восстановлены 47 ключей 172 (studio.*11, cutscene-сообщения 8,
add_radius, cutscene_line_on/off, cutscene_del_*, cutscene_studio, radius*, target_passive,
coffin, guide_corpse/trigger entity, cfg.delete*4, aimode/lockinteract, хоткей студии);
4 value-конфликта → значения 172 (add_check «+ В радиусе», check «В радиусе», cam_enter,
rec_start — они описывают живую 172-механику). Генератор: merge227/lang-merge (python,
в истории сессии), результат в jar и в resources/ зипа.

**Тесты 227** (testrun: merge227/testrun, копия b188-харнесов): escort 199, profile 47,
graph 211, context-menu 92, dialogue 56, graph-runtime 153, audio 63, bbmodel 64,
render-order 38 — PASS. ДВЕ калибровки эры (не регрессии, доказано байткодом 169–172):
ContextMenuHarness 15→16 строк (b188-отчёт 92 был записан на до-радиусном jar e53d88c7!);
create_test_doubles.py: Entity-стаб + getName() (GD 149+ логирует имя в walk-start).
Свои целевые: MergeCheck 14 (varLocal roundtrip/connect-пропагация, radius-клампы+def10,
cutsceneLine roundtrip, RADIUS=15/TARGET_PASSIVE=4, evalIfNode-семантика 226: local без
НПС → 0, blank name → fallback n).

**Уроки 227**: (1) сырой CFR-декомпилат НЕ компилируется — ~40 артефактов на 3 файла,
чинятся итеративно, семантика сохраняется; (2) «PASS-базлайны» b188 могли быть записаны
на jar ДРЕВНЕЕ донора (test-report.json хранит sha — сверять!); (3) run_tests.py падает
на первой стадии — калибровку чинить и перезапускать всё; (4) лямбда-захваты в javap —
часть сигнатуры: calcBoxHeight(lines, n5, n6) vs (lines, lines, n6) выдал себя разным
capture-списком (совпадение до байта необязательно, но супертсет-проверка ловит);
(5) deferred-список вместо модификации ArrayList во время итерации.

Файлы: jar `settledlands-0.8.0-build227-restore.jar` sha256
`6b7220216d54ba8a1197fc47ffeb3ba5e7fb4b350c3706571b967332f876de3c`; zip
`SettledLands-Build227-Restore-Source.zip`; рабочее дерево слияния
`settledlands/build92/merge227/` (dec172, dec226, src — merged, resources-overlay,
testrun — харнесы+MergeCheck.java). Статус: доставлен, ждёт плейтеста юзера
(реплика/радиус/два-трупа вживую не проверялись — headless-покрытие частичное).

### Build 228 «scenefix» (2026-10-27)
Баг юзера: после смерти вселённым (/kill) и сам-выхода из чистилища «труп» невидим
и 60/60. ДИАГНОЗ по latest.log: сессия шла на jar 222 (старый jar в mods!) — часть
симптомов закрыта в 223–227; НО дыра оставалась и в 227: IvyDeathScene ведёт сцену
тела НПС по persistentData-меткам settledlands_ivydie_t0/stage, возраст =
entity.tickCount − t0; tickCount НЕ персистится и обнуляется при перезагрузке
чанка → если чанк смерти выгрузился между 71 (setInvisible) и 72 (спавн трупа +
discard тела) тиками (игрок в чистилище), после возврата возраст отрицательный и
конверсия не завершается никогда: тело вечно невидимое и живое. Фикс:
PlayerCorpsePossession.healStuckIvyBodies (sweep 1 раз/сек по всем ServerLevel,
GuideNpc не-трупы): t0 > tickCount → если рядом ±4/±3 уже есть GuideCorpseNpc —
снять метки и npc.discard() (не плодим второй труп: у тела игрока свой труп уже
есть); иначе t0 = tickCount − 72 (конверсия завершается сразу). Логи: «stuck ivy
body discarded» / «stuck ivy scene rebased». Урок: сток-сцены, сравнивающие
tickCount с персистным t0, ломаются перезагрузкой чанка — лечить sweep'ом или
хранить возраст в NBT. Тесты: сюита 923 PASS + MergeCheck 14 на jar228.
Маркеры 228: `settledlands_ivydie_t0` в PlayerCorpsePossession.java = 5,
`healStuckIvyBodies` = 2. jar sha256 b992aad31478dcbd203068d159d2772151e1a8e1905c606b07e41779cb1c8148.
