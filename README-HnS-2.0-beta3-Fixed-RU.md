# Hackers 'n Slashers `2.0-beta3-fixed` (v3, NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с нативными анимациями Hackers 'n Slashers, выборочной совместимостью с **Punchy**, **EMF / FA+Player**, **Mallard Guard**, **Better Combat `2.4.0+1.21.1`** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## 1. Исправлено дублирование правой руки с Punchy (`меч + щит`) и поломка рукава с FA+Player

### Выборочное подавление Punchy (по каждой руке отдельно)
- **`hackersandslashers.punchy.mixins.json`** + **`PunchyMixinPlugin`** (`@Pseudo`, `defaultRequire = 0`, только клиент; при отсутствии Punchy не загружается):
  - **`PunchyConfigMixin`** (`punchy.config.PunchyConfig#isHandBlacklisted`) и **`PunchyArmRendererMixin`** (`punchy.client.render.PunchyArmRenderer#renderArm`, `#renderArmArmor`, `#renderItemInHand`).
  - Когда HnS активно рендерит 1-е лицо через `PlayerAnimationLib` (`FirstPersonMode.THIRD_PERSON_MODEL`, `firstPersonTransitionProgress >= 1`), Punchy отключается **только для тех рук, которыми сейчас владеет HnS**:
    - **Меч в одной руке**: HnS рисует правую руку с мечом, Punchy её не дублирует.
    - **Меч + щит в левой руке**: HnS рисует правую руку с мечом (`showRightArm = true`, `showLeftArm = false`), Punchy убирает дубль пустой правой руки, но **оставляет левую руку со щитом**!
    - **Двуручное оружие / парное оружие / атака со щитом**: HnS рисует обе руки (`showRightArm = true`, `showLeftArm = true`) — Punchy подавляет обе руки.
    - **Небоевые предметы (фонарь, кирка, блоки, пустая рука) и блок щитом (`isUsingItem`)**: HnS отступает, Punchy работает полностью нативно.
- **`PunchyClientBridge.applySyncedFirstPersonConfig`** (`DynamicAnimationHandler#updatePlayerPose` `bci 172` и `PlayerAnimator#playClientAnimation` `bci 848`): синхронизирует `showRightItem = showRightArm` и `showLeftItem = showLeftArm`, чтобы PAL не рисовал лишний предмет без руки.

### Фикс геометрии рукава с EMF / Fresh Animations: Player Extension (`FA+Player`)
- **`PunchyClientBridge.registerEmfFirstPersonCompat`** (вызывается из `PlayerAnimator.lambda$onClientSetup$1` `bci 127`): если установлен `entity_model_features`, но не установлен `emf_compat_hackers_and_slashers`, регистрирует `EMFAnimationApi.registerVanillaModelCondition` для локального игрока в `FirstPersonPass` / активной HnS 1st-person анимации (не затрагивая инвентарь и вид от 3-го лица `F5`). Это убирает «раздутый/торчащий» `rightSleeve` на руках HnS.

## 2. Исправлен блок щитом и парирование мечом с Mallard Guard (`1.56d`)

- **Щит в левой руке (`ПКМ` = ванильный блок щитом)**:
  - **`OffhandShieldInputMixin`** (`Minecraft#handleKeybinds` `@At("TAIL")`, приоритет `1500`) + **`PunchyClientBridge.tryForceOffhandShieldBlock`**: если в левой руке щит, зажата `ПКМ`, щит не на перезарядке и игрок не смотрит на интерактивный блок/сущность и не использует предмет в правой руке (еда/лук/блок), принудительно вызывает `gameMode.useItem(player, OFF_HAND)` и очищает клиентский `GuardState` Mallard Guard (`MallardGuardBridge.clearClientGuard`), не давая `MinecraftGuardInputMixin` перехватывать `ПКМ` под парирование мечом.
  - **`MallardGuardCompatHandler.onServerTick`**: на серверном тике сбрасывает `MallardGuardBridge.releaseGuard(player)` при щите в левой руке.
  - **`MallardGuardBridge.applyVanillaShieldRule`**: добавляет `minecraft:shield,#c:shields` в `GuardConfig.SHIELD_BLACKLIST` и безопасно настраивает приоритет щита/приседания в `GuardConfig`, если соответствующие поля присутствуют.
- **Парирование мечом без щита**:
  - По умолчанию `ModKeybinds.PARRY` в HnS и блок/парирование Mallard Guard оба висят на `ПКМ` (`MOUSE_1`), из-за чего оба мода перебивали друг друга на одном нажатии, а после удара/отпускания парирование срывалось.
  - **`PunchyTrailBridge.canParryWithMallardAndOffhand`**: если загружен `mallardguard` и клавиша парирования HnS совпадает с `options.keyUse` (`ModKeybinds.PARRY.same(mc.options.keyUse)`), HnS уступает `ПКМ` системе **Mallard Guard**; при переназначении парирования HnS на другую клавишу (например `V`) работают обе механики.
  - **`PunchyClientBridge.isUsingOrMallardGuarding`** (`DynamicAnimationHandler#updatePlayerPose` `bci 210`): пока игрок парирует/блокирует мечом через Mallard Guard, HnS временно отпускает позу 1-го лица, чтобы анимация парирования Mallard Guard/Punchy отображалась корректно.

## 3. Совместимость с Better Combat `2.4.0+1.21.1` и паритет с `9896e9c`

- **`META-INF/neoforge.mods.toml`**: удалён блок несовместимости `bettercombat` и подключены `hackersandslashers.bettercombat.mixins.json` и `hackersandslashers.punchy.mixins.json`.
- Сохранены **`BetterCombatMixinPlugin`**, **`BetterCombatFlagsMixin`**, **`BetterCombatPlayerAttackHelperMixin`**, **`CancelVanillaAttackMixin`**, **Mallard Guard stamina compat**, фиксы **Grapple** (`ServerConfig`, `EnergyHandler`, `EnergyManager`, `C2S_PacketGrappleState`, `CombatParkourHandler$Client`, `CombatParkourManager`), и нормализация диагонального уворота.

## Проверки и хэши (`v3`)

- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (1185 записей, 509 классов, 14.7 MB).
- `tools/jvm_verify.py --target-major 65`: **OK** (509 классов).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (0 висячих ссылок).
- `tools/check_mixin_class.py`: **OK** (55 миксин-классов).
- HotSpot Java 21 `-Xverify:all`: `VerifyAll` **509/509** классов (`verify/format_failures=0`) + `FullSuiteV3` (**33/33** поведенческих теста: подавление Punchy по рукам, левая/правая рука, синк `showLeftItem`, инвентарь/F5, EMF compat, `tryForceOffhandShieldBlock`, разрешение конфликта `ПКМ` и диагональный уворот).
- **SHA-256**:
  - `hackersandslashers-2.0-beta3-fixed.jar`: `8bffda351000bd4a1293f7004414afeef89bbab76d09b62035fd84cb7f6afb5f`
  - `HnS-2.0-beta3-Fixed-Source.zip`: `de086a5babbc9e1a1fe79b3da5d475919d52649f777f530f27177f16471d04f2`
