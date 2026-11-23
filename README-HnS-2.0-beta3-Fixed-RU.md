# Hackers 'n Slashers `2.0-beta3-fixed` (NeoForge 1.21.1)

Собранный патч-релиз поверх `hackersandslashers-2.0-beta3.jar` для совместной работы с **Punchy / HyperPunchy**, **Better Combat `2.4.0+1.21.1`**, **Mallard Guard** и полным переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## 1. Только Punchy от 1-го лица + шлейфы и хитбоксы HnS по рукам Punchy

### Причина бага со сковородкой и оружием от 1-го лица
`ItemHelper.isMeleeWeapon` распознавал оружие (включая `farmersdelight:skillet`), после чего `DynamicAnimationHandler.updatePlayerPose` и `PlayerAnimator.determineFirstPersonMode` переключали рендер на 3D-модель игрока `FirstPersonMode.THIRD_PERSON_MODEL` вместо рук Punchy.

### Что изменено
- **`ClientConfig`**: добавлена настройка `[Visuals] disable_first_person_animations = true` (по умолчанию включена). Когда она `true`:
  - `PlayerAnimator.determineFirstPersonMode` возвращает `FirstPersonMode.DISABLED` для атак, защиты, действий и паркура.
  - `DynamicAnimationHandler.updatePlayerPose` и `ItemInHandLayerMixin` (`bci 1505` и `bci 176`) считают `isFirstPersonPoseAllowed(...) = false`, отключая 1st-person слой позы и камерную 1st-person модель HnS, сохраняя все **3rd-person** анимации.
  - `CameraHeightSyncModifier` и `PlayerRendererFovMixin` (`priority = 2050`) не смещают камеру и не переписывают FOV в 1-м лице.
  - `ItemInHandRendererMixin.HnS$applyWeaponTransforms` не накладывает HnS-трансформы на `FIRST_PERSON_*` руки — позицию предмета целиком задаёт Punchy.
  - `ComboManager` (`powerAttackBehavior`, `comboBehavior`) вызывает `PunchyTrailBridge.playAttackAnimationAndSwing(...)`, запуская 3rd-person анимацию HnS и одновременно `player.swing(hand, false)` для свинга руки Punchy.
  - `VanillaEntitySwingMixin` не отменяет ванильный/Punchy-свинг при `disable_first_person_animations = true`.
- **Выравнивание шлейфов (`BladeTracker` + `TrailSystem`) по рукам Punchy**:
  - `ItemInHandRendererMixin` (`HnS$captureWeaponPosition`, `HnS$captureTrail`) переводит координаты из view-space рук 1-го лица в world-space через `PunchyTrailBridge.viewOrWorldToWorld` (`v.rotate(camera.rotation()).add(camPos)`), оставляя 3rd-person расчёт без поворота камеры.
  - `TrailSystem.onRenderLevel` (`bci 467`) и `GameRendererAccessor` используют `gameRenderer.getFov(camera, partialTick, false)` — ту же матрицу проекции, что и `ItemInHandRenderer` для рук Punchy.

## 2. Совместимость с Better Combat `2.4.0+1.21.1`

- **`META-INF/neoforge.mods.toml`**: убран конфликт-блок `[[dependencies.hackersandslashers]] modId="bettercombat" type="incompatible"`, добавлен миксин-конфиг `hackersandslashers.bettercombat.mixins.json`.
- **`BetterCombatMixinPlugin`**: проверяет наличие `net/bettercombat/api/CombatFlags.class` в classpath; если Better Combat не установлен, миксины не загружаются.
- **`BetterCombatFlagsMixin`** и **`BetterCombatPlayerAttackHelperMixin`**: если `ItemHelper.isHoldingCombatItem(player)` истинно, `CombatFlags.isAttackDisabled(player)` возвращает `true`, а `PlayerAttackHelper` (`poseForPlayer`, `isDualWielding`, `isTwoHandedWielding`, `shouldAttackWithOffHand`, `getCurrentAttack`, `getRangeWithWeapon`) уступает управление HnS, не ломая оружие вне HnS.
- **`CancelVanillaAttackMixin`**: установлен `@Mixin(value = Minecraft.class, priority = 900)` и проверка `if (cir.isCancelled()) return;`, чтобы комбо HnS обрабатывалось до `MinecraftClientInject` (`1000`).

## 3. Полный паритет с `9896e9c` поверх `2.0-beta3`

Сохранены все улучшения `2.0-beta3` (`ENABLE_EXPERIMENTAL_MOB_COMBAT`, `startupFrames = 1` в парировании, финты, `ParryManager.overrideUsable`, bone/surface grapple, `canRoll`, `CompatLoader`, `ClientConfig.FIRST_PERSON_ANIMATION_FOV`, `energy_bar_offset_x/y`, значения силы уворота `0.75F`/`0.85F`, на месте `0.6F`), а из `9896e9c` перенесены:
- **Mallard Guard (`mallardguard`)**: `MallardGuardBridge`, `MallardGuardCompatHandler`, секция `["Mallard Guard Compat"]` в `ServerConfig`, проверка в `ParryManager.canParry` и защита от зажатой атаки `!keyAttack.isDown()` в `ParryKeybindHandler`.
- **Захват (`Grapple`)**: секция `["Grapple"]` в `ServerConfig` (`enable_grapple`, `grapple_energy_per_second`, `min_grapple_target_height`, `min_grapple_target_width`, `allow_grapple_babies`, `grapple_entity_whitelist`, `grapple_entity_blacklist`), `CombatParkourManager.canGrappleTarget` + `startGrapple`, расход энергии `ServerConfig.getGrappleEnergyPerSecond() / 20.0F` в `CombatParkourHandler$Client.onClientTick`, проверка цели и дистанции (`Math.max(12.0, h + w + 6.0)`) в `C2S_PacketGrappleState`, а также блокировка регена энергии и серверный сброс мёртвой/невалидной цели с `S2C_PacketGrappleSync` в `EnergyHandler` и `EnergyManager`.
- **Нормализация диагонального уворота**: `CombatParkourManager.calculateDodgeVector` делегирует в `PunchyTrailBridge.calculateDodgeVector`, нормализуя `(forward, strafe)` по длине с сохранением коэффициентов `beta3`.

## Проверка

Все автоматические проверки пройдены:
- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (1180 записей, 505 классов, 14.7 MB).
- `tools/jvm_verify.py --target-major 65`: **OK** (505 классов).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (0 висячих ссылок).
- `tools/check_mixin_class.py`: **OK** (53 миксин-класса).
- Проверка 7 сигнатур `@Inject` против `bettercombat-neoforge-2.4.0+1.21.1.jar`: **7/7 OK**.
- Java 21 HotSpot `java -Xverify:all` по всем 28 изменённым и добавленным классам: **28/28 PASS** (`fail=0`).

## Артефакты

- `hackersandslashers-2.0-beta3-fixed.jar` (`SHA-256: 0053bb36e1c000aa66d047d19905914b7ab8adac94a6439f2082e9c3e22065fe`)
- `hackersandslashers-2.0-beta3-fixed.jar.sha256sums.txt`
- `HnS-2.0-beta3-Fixed-Source.zip` (`SHA-256: 1f9e1a7bf2fd0a4b5220e60669c7ff45a0a27cb86699a9427d1f93464a53cda9`)
