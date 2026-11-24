# Hackers 'n Slashers `2.0-beta3-fixed` (v7, NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с удалением сломанной механики `Roll`, полным исправлением и расширением `Dash` на все виды оружия (и в воздухе), близкой 1st-person позой блока щитом, режимом 1st-person простоя из **Punchy**, совместимостью с **EMF / FA+Player**, **Mallard Guard**, **Better Combat `2.4.0+1.21.1`** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## Что исправлено в v7 (удаление Roll, исправление и расширение Dash)

### 1. Удаление `Roll` и привязки клавиши переката
- **`ModKeybinds`** & **`SetupKeybinds`**:
  - Удалена отдельная клавиша `ModKeybinds.ROLL` (`key.hackersandslashers.roll`, `Left Alt`); в настройках управления регистрируются 3 клавиши HnS: `PARRY`, `DASH` (`X` по умолчанию), `ULTIMATE` (`R`).
  - Добавлены переводы `"key.hackersandslashers.dash"` в `assets/hackersandslashers/lang/{en_us,pt_br,ru_ru}.json` (`"Dash"`, `"Avanço"`, `"Рывок"`).
- **`CombatParkourHandler`** (`$Client`) & **`CombatParkourManager`** & **`Animator`**:
  - Удалена обработка нажатия `ROLL.consumeClick()`, а также методы `tryTriggerRoll`, `canRoll`, `triggerRoll`, `playRollAnimation`, `playDodgeAnimation`.
  - Сохранены привязка `X` в приседе (`player.isCrouching()`) для наклона тела (`handleLeanInput`), а также паркурный `Vault` (`onVault: "roll_front"`, анимации `roll_front.json` / `roll_back.json`).

### 2. Исправление `Dash` и доступность для всех боевых предметов (и пользовательских датапаков)
- **`CombatParkourManager.canDash`**:
  - Убрана жёсткая блокировка `Dash` при отсутствии полей `onDash` / `onAirDash` в пресете — теперь любой боевой предмет, инструмент, щит или кулак (`stances != null`) может делать `Dash` на земле и 1 раз в воздухе (до приземления / воды / полёта), даже при неполных датапаках сторонних модов. Небоевые предметы (`stances == null`, еда, блоки) по-прежнему не дают `Dash`.
- **`Animator.isRegisteredAnimation` / `resolveDashAnimation` / `playDashAnimation`**:
  - Перед проигрыванием анимация проверяется в `PlayerAnimResources.hasAnimation(...)` с безопасным разбором `onAirDash` → `onDash` → `hackersandslashers:one_handed_dash` → `hackersandslashers:dash_front` → `hackersandslashers:hns_greatsword_dash`. Несуществующие анимации больше не вызывают пустое зависание/тихий отказ.
- **`DynamicAnimationHandler` (`isDodging`)**:
  - Убрано переключение на `onRoll` при отсутствии парирования: в состоянии уворота `isDodging` теперь всегда выбирается `Dash`-анимация с валидацией через `Animator.isRegisteredAnimation` и фолбэком `Animator.resolveDashAnimation(player)` (в 1-м лице при отсутствии специальной 1P dash-анимации сохраняется текущая idle-поза оружия или `resolveDashAnimation`, никогда не возвращается `null` / `"idle"`).
- **Пресеты оружия и боевые стили (`26` `presets/*.json` + `fightstyles/punches.json`)**:
  - Из всех пресетов и `punches.json` удалены поля `"onRoll"` и `"onAirRoll"`.
  - Исправлены 5 пресетов, ссылавшихся на несуществующие анимации (`rapier`, `amethyst_rapier`: `hns_rapier_dash`; `staff`, `irons_spellbooks_staffs`, `pyrium_staff`: `hns_one_handed_dash`) → `"hackersandslashers:one_handed_dash"`.
  - Добавлены `"onDash"` и `"onAirDash"` во все 12 бывших Roll-only пресетов и в `crossbow.json`:
    - двуручные тяжёлые (`"hackersandslashers:hns_greatsword_dash"`): `battleaxe`, `hammer`, `scythe`, `decrepit_scythe`, `hellrazor`;
    - одноручные / щит / арбалет (`"hackersandslashers:one_handed_dash"`): `axe`, `pickaxe`, `shovel`, `hoe`, `shield`, `mace`, `chakram`, `crossbow`.

---

## Что было исправлено ранее (v1–v6)

- **v6**: Калибровка близкой 1st-person позы блока щитом (`OffhandShieldPoseModifier`: `pos=(+3.9,-3.7,+0.3)`, `rot=(-45°,+31°,0°)`), передача 1st-person простоя в **Punchy** (`PunchyClientBridge` + `DynamicAnimationHandler`) с сохранением всех боевых/защитных/паркур анимаций HnS и чистым сбросом `smoothBlockBlend`, добавлена `README-AI-MODDING-GUIDE.md`.
- **v5**: Первичное выравнивание 1st-person щита и блока (`OffhandShieldPoseModifier` + `FirstPersonOffhandShieldMixin`) без скачка между `shield.json` и `shield_blocking.json`.
- **v4**: Видимость щита и предметов левой руки (`showRightItem = true`, `showLeftItem = true`) с выборочным подавлением Punchy (`isShowArm || isShowItem`).
- **v3**: Выборочная совместимость с **Punchy** (`PunchyConfigMixin`, `PunchyArmRendererMixin`), авто-совместимость рукава с **EMF / FA+Player**, фиксы блока щитом и парирования с **Mallard Guard (`1.56d`)**.
- **v1–v2**: Совместимость с **Better Combat `2.4.0+1.21.1`** (`BetterCombatMixinPlugin`, `BetterCombatFlagsMixin`, `BetterCombatPlayerAttackHelperMixin`, `CancelVanillaAttackMixin`), фиксы захвата (`Grapple`), стамины и диагонального уворота из `9896e9c`.

---

## Проверки и хэши (`v7`)

- `tools/validate_jar.py`: **OK** (`1187` записей, `511` классов, `14.8 MB`, `modId=hackersandslashers`, `version=2.0-beta3`).
- `tools/jvm_verify.py`: **OK** (`511` классов, target major `65`).
- `tools/dangling_refs.py --base <v6.jar>`: **OK** (`0` висячих ссылок во всём JAR после удаления `ROLL`/`Roll`-методов).
- `tools/check_mixin_class.py --base <v6.jar>`: **OK** (`56` миксин-классов, `0` новых внешних API).
- `tools/mixin_clash_check.py`: **OK** (`0` конфликтов имён).
- Проверка `RuntimeVisibleAnnotations` (`@EventBusSubscriber`, `@SubscribeEvent`) на перекомпилированных классах + проверка всех `27` JSON пресетов/стилей и `3` lang-файлов: **OK** (`0` отсутствующих `onDash`/`onAirDash`).
- HotSpot Java `-Xverify:all`:
  - `VerifyV6Probe`: **ALL CHECKS PASSED** (регресс-тест Punchy и позы блока щитом).
  - `VerifyV6Extra`: **ALL EXTRA CHECKS PASSED** (`MallardGuard`, `Dodge`, `Grapple`, `Vault`, левша, работа без Punchy).
  - `VerifyV7RollDashProbe`: **ALL V7 CHECKS PASSED** (отсутствие `ROLL`/`canRoll`/`triggerRoll`/`playRollAnimation`/`playDodgeAnimation`, регистрация `{PARRY, DASH, ULTIMATE}`, `isRegisteredAnimation` + фолбэки `resolveDashAnimation`, gating `canDash` для неполных датапаков / воздуха / полёта / стаггера, резерв `crouch+X` под наклон, `onClientTick` 6-tick завершение рывка, `DynamicAnimationHandler` для `isDodging`).
- **SHA-256 (`sha256sum -c hackersandslashers-2.0-beta3-fixed.jar.sha256sums.txt` OK)**:
  - `hackersandslashers-2.0-beta3-fixed.jar`: `b773c98b35c6f0dcd26052d892199bbd1153972d66e0c1e83b228ea17076014d` (`15 468 192` байт)
  - `HnS-2.0-beta3-Fixed-Source.zip`: `e642be0d39fe3c69cbbc8afbc090ce2702a4f9e7989acbff186c23f0223354ff` (`76 292` байт)
  - `README-AI-MODDING-GUIDE.md`: `8a372be6b1044201a75cc3de52125dce64702649f86ae39542050a802a142c8d` (`20 737` байт)
