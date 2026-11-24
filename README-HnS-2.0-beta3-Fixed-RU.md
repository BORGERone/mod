# Hackers 'n Slashers `2.0-beta3-fixed` (v4, NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с нативными анимациями Hackers 'n Slashers, выборочной совместимостью с **Punchy**, **EMF / FA+Player**, **Mallard Guard**, **Better Combat `2.4.0+1.21.1`** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## Что исправлено в v4: видимость щита в левой руке с мечом

В `v3` при удержании одноручного меча (`hns_sword_idle_fp` и др. позы с `showRightArm = true, showLeftArm = false`) щит в левой руке визуально пропадал, потому что:
- При активной позе `FirstPersonMode.THIRD_PERSON_MODEL` мод **PlayerAnimationLib (PAL)** сам отменяет ванильный/Punchy-рендер обеих рук (`ItemInHandRendererMixin#disableDefaultItemIfNeeded` при `transitionProgress == 1`), поэтому Punchy не мог рисовать левую руку со щитом;
- В то же время `PunchyClientBridge.applySyncedFirstPersonConfig` в `v3` выставлял `showLeftItem = showLeftArm` (`false`), из-за чего PAL отменял и собственный рендер щита (`cancelItemRender`), хотя в нативном HnS анимации `hns_sword_idle_fp` / `hns_dagger_idle_fp` / `hns_gauntlet_idle_fp` специально задают кейфреймы `left_item` (`rot [-180,0,0] pos [0,5,0]`) при скрытой левой руке (`showLeftArm = false`, `showLeftItem = true`).

Изменения в `PunchyClientBridge.java` (`v4`):
1. **`applySyncedFirstPersonConfig`** (обе перегрузки для `HnSPlayerFirstPersonPoseController` и `PlayerAnimationController`): выставляет `cfg.setShowRightItem(true)` и `cfg.setShowLeftItem(true)`, сохраняя нативные флаги рук HnS (`showRightArm` / `showLeftArm`), чтобы PAL отображал щит/предмет в левой руке так же, как в оригинальном HnS `2.0-beta3`.
2. **`shouldSuppressPunchyArm`**: проверяет `cfg.isShowRightArm() || cfg.isShowRightItem()` для `RIGHT` и `cfg.isShowLeftArm() || cfg.isShowLeftItem()` для `LEFT`, чтобы пока HnS владеет 1-м лицом (`FirstPersonMode.THIRD_PERSON_MODEL`, `progress >= 1.0`), Punchy подавлял обе руки/предметы (без дублирования щита или лишней левой руки), а при остановке `FIRST_PERSON_POSE_LAYER` (топор, кирка, факел, пустая рука) полностью возвращал управление Punchy.

## 1. Совместимость с Punchy и EMF / FA+Player (из v3 + v4)

### Выборочное подавление Punchy
- **`hackersandslashers.punchy.mixins.json`** + **`PunchyMixinPlugin`** (`@Pseudo`, `defaultRequire = 0`, только клиент; при отсутствии Punchy не загружается):
  - **`PunchyConfigMixin`** (`punchy.config.PunchyConfig#isHandBlacklisted`) и **`PunchyArmRendererMixin`** (`punchy.client.render.PunchyArmRenderer#renderArm`, `#renderArmArmor`, `#renderItemInHand`).
  - Когда HnS активно рендерит 1-е лицо через `PlayerAnimationLib` (`FirstPersonMode.THIRD_PERSON_MODEL`, `firstPersonTransitionProgress >= 1`), Punchy отключается для рук/предметов под контролем HnS (`isShowArm || isShowItem`):
    - **Меч в одной руке / Меч + щит в левой руке**: HnS/PAL рисует правую руку с мечом (`showRightArm = true, showRightItem = true`) и щит в левой руке (`showLeftArm = false, showLeftItem = true`), а Punchy полностью подавлен и не дублирует руку или щит.
    - **Двуручное оружие / парное оружие / атака со щитом**: HnS/PAL рисует обе руки и оба предмета (`showRightArm = true, showLeftArm = true, showRightItem = true, showLeftItem = true`) — Punchy подавляет обе руки.
    - **Небоевые предметы (фонарь, кирка, топор, блоки, пустая рука)**: HnS останавливает 1st-person позу, Punchy работает полностью нативно.

### Фикс геометрии рукава с EMF / Fresh Animations: Player Extension (`FA+Player`)
- **`PunchyClientBridge.registerEmfFirstPersonCompat`** (вызывается из `PlayerAnimator.lambda$onClientSetup$1`): если установлен `entity_model_features`, но не установлен `emf_compat_hackers_and_slashers`, регистрирует `EMFAnimationApi.registerVanillaModelCondition` для локального игрока в `FirstPersonPass` / активной HnS 1st-person анимации (не затрагивая инвентарь и вид от 3-го лица `F5`). Это убирает «раздутый/торчащий» `rightSleeve` на руках HnS.

## 2. Блок щитом и парирование мечом с Mallard Guard (`1.56d`)

- **Щит в левой руке (`ПКМ` = ванильный блок щитом)**:
  - **`OffhandShieldInputMixin`** (`Minecraft#handleKeybinds` `@At("TAIL")`, приоритет `1500`) + **`PunchyClientBridge.tryForceOffhandShieldBlock`**: если в левой руке щит, зажата `ПКМ`, щит не на перезарядке и игрок не смотрит на интерактивный блок/сущность и не использует предмет в правой руке (еда/лук/блок), принудительно вызывает `gameMode.useItem(player, OFF_HAND)` и очищает клиентский `GuardState` Mallard Guard (`MallardGuardBridge.clearClientGuard`), не давая `MinecraftGuardInputMixin` перехватывать `ПКМ` под парирование мечом.
  - **`MallardGuardCompatHandler.onServerTick`**: на серверном тике сбрасывает `MallardGuardBridge.releaseGuard(player)` при щите в левой руке.
  - **`MallardGuardBridge.applyVanillaShieldRule`**: добавляет `minecraft:shield,#c:shields` в `GuardConfig.SHIELD_BLACKLIST` и безопасно настраивает приоритет щита/приседания в `GuardConfig`, если соответствующие поля присутствуют.
- **Парирование мечом без щита**:
  - **`PunchyTrailBridge.canParryWithMallardAndOffhand`**: если загружен `mallardguard` и клавиша парирования HnS совпадает с `options.keyUse` (`ModKeybinds.PARRY.same(mc.options.keyUse)`), HnS уступает `ПКМ` системе **Mallard Guard**; при переназначении парирования HnS на другую клавишу (например `V`) работают обе механики.
  - **`PunchyClientBridge.isUsingOrMallardGuarding`**: пока игрок парирует/блокирует мечом через Mallard Guard, HnS учитывает состояние защиты так же, как `isUsingItem()`.

## 3. Совместимость с Better Combat `2.4.0+1.21.1` и паритет с `9896e9c`

- **`META-INF/neoforge.mods.toml`**: удалён блок несовместимости `bettercombat` и подключены `hackersandslashers.bettercombat.mixins.json` и `hackersandslashers.punchy.mixins.json`.
- Сохранены **`BetterCombatMixinPlugin`**, **`BetterCombatFlagsMixin`**, **`BetterCombatPlayerAttackHelperMixin`**, **`CancelVanillaAttackMixin`**, **Mallard Guard stamina compat**, фиксы **Grapple** (`ServerConfig`, `EnergyHandler`, `EnergyManager`, `C2S_PacketGrappleState`, `CombatParkourHandler$Client`, `CombatParkourManager`), и нормализация диагонального уворота.

## Проверки и хэши (`v4`)

- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (1185 записей, 509 классов, 14.7 MB).
- `tools/jvm_verify.py --target-major 65`: **OK** (509 классов).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (0 висячих ссылок).
- `tools/check_mixin_class.py`: **OK** (55 миксин-классов).
- HotSpot Java 21 `-Xverify:all`: верификация изменённых/новых классов (0 `VerifyError`/`ClassFormatError`) + `VerifyV4Probe` (**все проверки пройдены**: `applySyncedFirstPersonConfig` сохраняет `showLeftItem=true` / `showRightItem=true` при `showLeftArm=false` для меча со щитом; `shouldSuppressPunchyArm` / `shouldSuppressPunchyHand` подавляют обе руки при активном HnS 1st-person для правши и левши и уступают Punchy при неактивном HnS, переходе `< 1.0`, `DISABLED`, `F5`, инвентаре и shadow pass).
- **SHA-256**:
  - `hackersandslashers-2.0-beta3-fixed.jar`: `c23c1492bbc6a02b8d0d700e0619a52f0599612c9f7f71acb6a391f788046b0d`
  - `HnS-2.0-beta3-Fixed-Source.zip`: `ebc8816628c22deb4d7345f7ba4bc51f17e0c689ab15056e43ccf6c78a4b0907`
