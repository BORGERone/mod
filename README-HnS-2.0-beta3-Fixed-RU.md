# Hackers 'n Slashers `2.0-beta3-fixed` (v5, NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с нативными анимациями Hackers 'n Slashers, правильной 1st-person ориентацией и анимацией блока щитом, выборочной совместимостью с **Punchy**, **EMF / FA+Player**, **Mallard Guard**, **Better Combat `2.4.0+1.21.1`** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## Что исправлено в v5: положение, разворот и блок щита от 1-го лица

В `v4` щит стал видимым с мечом (`showLeftItem = true`), но при удержании в левой руке (`FirstPersonMode.THIRD_PERSON_MODEL`) отображался под неверным углом и смещением:
- Нативные позы HnS (`hns_sword_idle_fp`, `hns_dagger_idle_fp`, `parry_light_oh` и др.) рассчитаны на парное оружие в левой руке и задают `left_item = [-180, 0, 0]` (и без поправки на `shield.json` vs `shield_blocking.json`), а `FirstPersonCameraModifier` дополнительно добавлял `-10°` к `left_arm.rotY`;
- При зажатии `ПКМ` ванильная модель щита переключалась с `shield.json` (`[0, 90, 0], [10, 6, 12]`) на `shield_blocking.json` (`[45, 135, 0], [13.51, 3, 5]`), но при нулевой анимации `left_arm` в 1-м лице щит поворачивался ребром/наискось к центру экрана.

Изменения в `v5` (работают как с **Punchy**, так и без него):
1. **`OffhandShieldPoseModifier`** (`net.dndats.api.animations`, наследник PAL `AbstractModifier`):
   - Подключается самым внешним модификатором (`addModifierBefore` — индекс `0`) на 4 контроллера HnS: `HnSPlayerFirstPersonPoseController`, `HnSPlayerDefenseController`, `HnSPlayerAttackController` и `HnSPlayerActionController`;
   - Срабатывает **только** в 1-м лице локального игрока (`FirstPersonMode.isFirstPersonPass()`, `cameraType.isFirstPerson()`, `player == mc.player && mc.getCameraEntity() == player`), строго исключая инвентарь (`InventoryRenderFlag`), shadow pass (`ShaderHelper`), вид от 3-го лица / `F5` и удалённых игроков;
   - Активируется **только** при наличии щита в левой/второй руке (`ShieldItem` или `ItemHelper.isShield`), не мешая парному оружию, факелам, тотемам, использованию предмета в основной руке, захвату (`PlayerHelper.isGrappling`), анимациям `shield_bash` или двуручным стойкам без блока (`WeaponHandedness.TWO_HANDED`);
   - В спокойном состоянии опускает щит в левый нижний край экрана (`arm rot [-38°, +12°, -4°], pos [3.8, -4.6, -3.5]`) с точным выравниванием `left_item` (`rot [-54.7356°, +35.2644°, +30.0°], pos [-2.0463, -0.2426, +0.2426]`), компенсирующим разницу между `shield.json` и `shield_blocking.json` без скачка при переключении моделей;
   - При блоке щитом (`ПКМ` / `isUsingItem()` / `isBlocking()`) мгновенно сбрасывает `left_item` в `(0, 0, 0)` (без двойного поворота и рывка) и плавно (frame-rate-независимый smoothstep) поднимает руку к `rot [-45°, +24°, 0°], pos [4.2, +1.8, -3.1]`, ставя щит вертикально прямо перед игроком слева от прицела (`up = [0, 1, 0]`, `front ≈ [+0.36, 0, +0.93]`);
   - Сохраняет синхронное опускание при приседе (`-2.0`), лёгкую инерцию камеры из `FirstPersonCameraModifier` и зеркалится для левши (`HumanoidArm.LEFT`).
2. **`FirstPersonOffhandShieldMixin`** (`net.dndats.hackersandslashers.mixin.client`, зарегистрирован в `hackersandslashers.mixins.json`):
   - Мульти-таргет миксин `@Mixin(value = { HnSPlayerFirstPersonPoseController, HnSPlayerDefenseController, HnSPlayerAttackController, HnSPlayerActionController }, remap = false)`, подключающий `OffhandShieldPoseModifier` на `<init>` (`@At("RETURN")`).

## Что исправлено в v4: видимость щита в левой руке с мечом

- **`PunchyClientBridge.applySyncedFirstPersonConfig`** (обе перегрузки для `HnSPlayerFirstPersonPoseController` и `PlayerAnimationController`): выставляет `cfg.setShowRightItem(true)` и `cfg.setShowLeftItem(true)`, сохраняя нативные флаги рук HnS (`showRightArm` / `showLeftArm`), чтобы PAL отображал щит/предмет в левой руке так же, как в оригинальном HnS `2.0-beta3`.
- **`PunchyClientBridge.shouldSuppressPunchyArm`**: проверяет `cfg.isShowRightArm() || cfg.isShowRightItem()` для `RIGHT` и `cfg.isShowLeftArm() || cfg.isShowLeftItem()` для `LEFT`, чтобы пока HnS владеет 1-м лицом (`FirstPersonMode.THIRD_PERSON_MODEL`, `progress >= 1.0`), Punchy подавлял обе руки/предметы (без дублирования щита), а при остановке `FIRST_PERSON_POSE_LAYER` (топор, кирка, факел, пустая рука) возвращал управление Punchy.

## 1. Совместимость с Punchy и EMF / FA+Player (из v3 + v4)

### Выборочное подавление Punchy
- **`hackersandslashers.punchy.mixins.json`** + **`PunchyMixinPlugin`** (`@Pseudo`, `defaultRequire = 0`, только клиент; при отсутствии Punchy не загружается):
  - **`PunchyConfigMixin`** (`punchy.config.PunchyConfig#isHandBlacklisted`) и **`PunchyArmRendererMixin`** (`punchy.client.render.PunchyArmRenderer#renderArm`, `#renderArmArmor`, `#renderItemInHand`).
  - Когда HnS активно рендерит 1-е лицо через `PlayerAnimationLib` (`FirstPersonMode.THIRD_PERSON_MODEL`, `firstPersonTransitionProgress >= 1`), Punchy отключается для рук/предметов под контролем HnS (`isShowArm || isShowItem`):
    - **Меч в одной руке / Меч + щит в левой руке**: HnS/PAL рисует правую руку с мечом и щит в левой руке (с корректной 1st-person стойкой и блоком из `v5`), а Punchy полностью подавлен и не дублирует руку или щит.
    - **Двуручное оружие / парное оружие / атака со щитом**: HnS/PAL рисует обе руки и оба предмета — Punchy подавляет обе руки.
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

## Проверки и хэши (`v5`)

- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (1187 записей, 511 классов, 14.8 MB).
- `tools/jvm_verify.py --target-major 65`: **OK** (511 классов).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (0 висячих ссылок).
- `tools/check_mixin_class.py`: **OK** (56 миксин-классов; 0 новых непроверенных внешних API-вызовов относительно `v4`).
- HotSpot Java `-Xverify:all`:
  - `VerifyChanged`: все 13 изменённых/новых классов (включая `OffhandShieldPoseModifier` и `FirstPersonOffhandShieldMixin`) прошли байткод-верификацию;
  - `VerifyV4Probe`: **ALL V4 PROBE ASSERTIONS PASSED**;
  - `VerifyV5Probe`: **ALL V5 PROBE ASSERTIONS PASSED** (проверены гейты инвентаря / shadow pass / 3rd-person / F5 / remote player, idle-выравнивание `shield.json` <-> `shield_blocking.json`, плавный переход `blockBlend` в `(0,0,0)` + `[-45°, +24°, 0°]` и обратный возврат, присед `-2.0F`, зеркалирование для левши, пропуск при не-щите, использовании основной руки, захвате, `TWO_HANDED` без блока и сохранении анимации `shield_bash`).
- **SHA-256**:
  - `hackersandslashers-2.0-beta3-fixed.jar`: `ad1b979a186c09fba5acb6d32b5ef2f49f770cf527457eaeef657ee10750449e`
  - `HnS-2.0-beta3-Fixed-Source.zip`: `018b0c74c172e95c8177b01a7d8f3bd3598366b8d99da58e7721954900c5707f`
