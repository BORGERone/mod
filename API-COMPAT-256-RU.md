# Build 256 — совместимость и техническая реализация

## База, кандидат и цель

Кандидат: `settledlands-0.8.0-build256-npc-fp-corpse.jar`, 2 254 719 байт, SHA-256 `024937159ceb6f7173591dacf30d0c46b2fcc5233e8b495e4918d56b715c2021`.

Точная база: `settledlands-0.8.0-build255-auto-ai.jar`, SHA-256 `7866e20a75eb5ef53e254e8e34a5eacc155d0b3ac768b99977ad4d95358b6541`.

- НПС с моделью Иви анимируется полноценнее, используя тот же внешний стек EMF / Fresh Animations Player Extension + Touch’n Motion, который мод уже использует для игроков.
- Первое лицо игрока, вселённого в NPC, должно использовать модель вселённого NPC в инвентаре/GUI, а для рук и мечевых атак — конечности этой модели.
- Обычное тело-труп имеет ровно 1 HP при максимуме 60 HP; гроб — 60/60.

## НПС: FA + TnM и безопасный fallback

Код: `src/build256/dev/settledlands/model/NpcMotionBridge256.java`, `NpcFreshEmfAccess256.java`, `NpcMotionPlayerProxy256.java`, `WalkAnimationSyncAccess256.java`, миксины `WalkAnimationStateMixin256` и `ClientLevelProxyLookupMixin256`.

1. Мост обёртывает только обычный `BbModel.render(GuideNpc, ...)`; `GuideCorpseNpc` и `GuideTriggerNpc` уходят в исходный рендер без прокси, EMF, TnM или предметного моста.
2. Каждый прокси — клиентская `RemotePlayer`, не добавляется в таблицу сущностей, список игроков или сетевое состояние мира. На время анимационного кадра `ClientLevel.getEntity(proxyId)` разрешает прокси только в ThreadLocal-области вызова; после рендера ссылки удаляются. `WalkAnimationState` копируется точно через клиентский accessor (положение и текущая/предыдущая скорость), а позиция, yaw/pitch, походка, предметы/броня, присед, бег, плавание, прыжок и атака отражают GuideNpc.
3. Изолированный PlayerModel-бипед проходит EMF `EMFModelPartRoot.animate()`; в опциональном случае TnM вызывается настоящий `ModelSpace.before` и его настоящий EMF animation hook для текущего UUID. Результат только на шести стандартных частях ретаргетируется матрично на `body/head/rightarm/leftarm/rightleg/leftleg` Иви с учётом вложенности BBModel, после чего отрисовывается исходный меш Иви.
4. Совместимость распознаётся без статических ссылок на сторонние client-классы: EMF 3.3.x + ETF 7.2.x; TnM включается только при 1.0.4 и Core 2.3.2, если фактический `TouchNMotionHook` зарегистрирован ровно один раз. При отсутствии/неподдержке модов, выключенном FA, неподходящем риге, исключении либо непрошедшей проверке позы используется оригинальная полноценная `BbModel.settledlands$renderActorOriginal` со своей стейт-машиной и обеими руками.
5. Полнотельный действующий пользовательский клип и разговор не затираются locomotion-анимацией. Короткие одноразовые клипы возвращаются к Fresh Animations/TnM после завершения; `customAnimBone` маска применяется поверх базового движения. Комбо build 255 остаются приоритетными.
6. В обоих рендерах предметы обрабатываются через тот же `BbModel.renderActorHeldItem`, что сохраняет локаторы, блокирование, посадку оружия, щит и build 252 HnS/PAL bridge.

Классы `BbModel`, `PlayerMotionBridge`, `FreshEmfAccess`, HnS/PAL мосты и все 255-патчи оставлены байт-в-байт. В `BbModel` изменён только NPC-overload `render(GuideNpc, ..., lod)`, добавлены два публичных package bridge-метода и alias исходного NPC-рендера.

## Вселение: инвентарь, первое лицо и руки

`PlayerBodyRenderer.onRenderPlayerPre` теперь сохраняет прежний пропуск мирового первого лица, но пропускает локального вселённого игрока через BBModel в отдельном GUI/inventory/paperdoll проходе; определение PASS (PAL/FirstPersonModel), EMF GUI, открытый Screen, сон и detached HUD preview делается отдельным reflect-only helper `PlayerFirstPersonBodyGate256`.

- `FirstPersonArmSubscriber256` заменяет пустые руки в `RenderArmEvent` костями `rightarm/leftarm` текущей модели вселения и её собственной текстурой.
- `PossessedFirstPersonHandMixin256` заменяет `PlayerRenderer.renderRightHand/renderLeftHand`; для GUI-прохода передаёт контекст модели/игрока.
- `PossessedFirstPersonModelMixin256` ловит полнотельный проход PAL `FirstPersonMode.THIRD_PERSON_MODEL` и переносит текущую 6-DOF позу стандартной руки на кость руки BBModel. Остальные части и `PlayerModel` не меняются, поэтому прежние слои предметов и HnS `BladeTracker` сохраняют свои входные pivots.
- Только серверно синхронизированная `PossessionClientSync.CLIENT_STATES` запись определяет модель; spectator/invisible/не вселённый игрок не получает NPC-руки. В первом лице не рендерится полнотельная модель перед камерой.

Классы интерфейса и миксины добавлены в клиентскую часть `settledlands.mixins.json`, без изменения common mixin-набора. Текущие контрольные JVM-проверки используют реалистичный тестовый миксин-ткатель, который переносит байткод добавленных Mixin-методов в синтетические Minecraft targets, и затем исполняют их под `-Xverify:all`; это не запуск настоящего Mixin/NeoForge.

## Труп: 1/60 HP

В `CorpseRecovery.syncHealth(GuideCorpseNpc)` на сервере базовый `Attributes.MAX_HEALTH` выставляется в `60.0` независимо от coffin-флага. Затем `setHealth` задаёт:

| Состояние | Текущий HP | Максимум HP |
|---|---:|---:|
| Обычный труп (`isCoffin() == false`) | 1 | 60 |
| Гроб (`isCoffin() == true`) | 60 | 60 |

`GuideCorpseNpc.<init>`, `setCoffin` и `readAdditionalSaveData` уже вызывают `syncHealth`; это обновляет и сохранённые тела при загрузке чанка. Серверная синхронизация остаётся в прежнем helper; `hurt`, `canBeHitByProjectile`, `isInvulnerable`, поводок, сохранение и возврат предметов не изменялись.

## Проверки

- Полный `tests/gate_build256.py`: 255–237 / HANDOFF §8, 24 новых verifier-valid мутанта, NPC FA/TnM + combo/corpse/fallback, инвентарь/руки/PAL sword pass и 1/60 HP.
- Изолированный `tests/jvm_npc_fp_corpse256.py` находит сбои 255 и исполняет 256 на JVM 21 `-Xverify:all`. Он подтверждает только контролируемую модель API и математику pose transfer.
- `tests/jvm_recovery256.py`: реальный байткод `GuideCorpseNpc` и `CorpseRecovery` даёт 1.0/60.0 на новом и загруженном теле, 60/60 у гроба; 24-мутантный стенд убивает все намеренные ошибки.
- Существующие регрессии FA/TnM, PAL/HnS, щита, копирования, GUI/характеристик, Auto-AI и исторических 237–255.

Не запускались полноценный Minecraft/NeoForge-клиент, игра пользователя и визуальная проверка. Совместимость с конкретной сборкой Fresh Animations/модпаком в игре следует проверить по чеклисту. Сторонние JAR/ассеты и пользовательские логи не включены.
