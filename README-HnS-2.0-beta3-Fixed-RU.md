# Hackers 'n Slashers `2.0-beta3-fixed` (NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с нативными анимациями Hackers 'n Slashers от 1-го и 3-го лица (откат Punchy-оверрайда), совместимостью с **Better Combat `2.4.0+1.21.1`**, **Mallard Guard** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## 1. Анимации Hackers 'n Slashers возвращены нативно (`2.0-beta3`)

Все классы анимаций, камеры, FOV, свинга, шлейфов, хитбоксов и клиентского конфига полностью возвращены к оригиналу `hackersandslashers-2.0-beta3.jar` (байт-в-байт):
- `DynamicAnimationHandler`, `PlayerAnimator`, `CameraHeightSyncModifier`
- `ItemInHandLayerMixin`, `ItemInHandRendererMixin`, `VanillaEntitySwingMixin`, `PlayerRendererFovMixin`
- `TrailSystem`, `ComboManager`, `ClientConfig`, `hackersandslashers.mixins.json`
- Удалён `GameRendererAccessor` и удалена настройка `disable_first_person_animations`, чтобы даже при старом `hackersandslashers-client.toml` анимации 1-го лица работали ровно так, как задумано в `2.0-beta3`.

## 2. Совместимость с Better Combat `2.4.0+1.21.1`

- **`META-INF/neoforge.mods.toml`**: удалён блок несовместимости `modId="bettercombat" type="incompatible"` и добавлен `hackersandslashers.bettercombat.mixins.json`.
- **`BetterCombatMixinPlugin`**: проверяет наличие `net/bettercombat/api/CombatFlags.class` в classpath; без Better Combat миксины не загружаются.
- **`BetterCombatFlagsMixin`** и **`BetterCombatPlayerAttackHelperMixin`**: когда в руках оружие HnS (`ItemHelper.isHoldingCombatItem(player)`), `CombatFlags.isAttackDisabled(player)` возвращает `true`, а `PlayerAttackHelper` (`poseForPlayer`, `isDualWielding`, `isTwoHandedWielding`, `shouldAttackWithOffHand`, `getCurrentAttack`, `getRangeWithWeapon`) передаёт управление HnS.
- **`CancelVanillaAttackMixin`**: `@Mixin(value = Minecraft.class, priority = 900)` + `if (cir.isCancelled()) return;`.

## 3. Паритет с `9896e9c` поверх улучшений `2.0-beta3`

Сохранены все фичи `2.0-beta3` (`ENABLE_EXPERIMENTAL_MOB_COMBAT`, `startupFrames = 1`, финты, `ParryManager.overrideUsable`, bone/surface grapple, `canRoll`, `CompatLoader`, `ClientConfig`, коэффициенты уворота `0.75F`/`0.85F`, на месте `0.6F`), и перенесены:
- **Mallard Guard (`mallardguard`)**: `MallardGuardBridge`, `MallardGuardCompatHandler`, секция `["Mallard Guard Compat"]` в `ServerConfig`, проверка в `ParryManager.canParry` (`bci 103`) и `!keyAttack.isDown()` в `ParryKeybindHandler`.
- **Захват (`Grapple`)**: секция `["Grapple"]` (7 опций) в `ServerConfig`, `CombatParkourManager.canGrappleTarget` + `startGrapple`, стоимость энергии из конфига в `CombatParkourHandler$Client.onClientTick` (`bci 782`), проверка цели и дистанции (`Math.max(12.0, h + w + 6.0)`) в `C2S_PacketGrappleState`, а также блокировка регена энергии и сброс мёртвой цели с `S2C_PacketGrappleSync` в `EnergyHandler` и `EnergyManager`.
- **Диагональный уворот**: `CombatParkourManager.calculateDodgeVector` нормализует диагональный вектор с сохранением коэффициентов `beta3`.

## Проверки

- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (1179 записей, 504 класса, 14.7 MB).
- `tools/jvm_verify.py --target-major 65`: **OK** (504 класса).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (0 висячих ссылок).
- `tools/check_mixin_class.py`: **OK** (52 миксин-класса).
- Сверка 7 сигнатур `@Inject` с `bettercombat-neoforge-2.4.0+1.21.1.jar`: **7/7 OK**.
- Java 21 HotSpot `java -Xverify:all`: **27/27 PASS** (`fail=0` среди всех изменённых и добавленных классов) и **11/11** откаченных файлов совпадают с `2.0-beta3` байт-в-байт.
- SHA-256 `hackersandslashers-2.0-beta3-fixed.jar`: `798628bf90e4c96b0bf0c8fb92389511d14d06fc1e5421eed6634511adb4f83a`.
