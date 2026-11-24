# Hackers 'n Slashers `2.0-beta3-fixed` (v6, NeoForge 1.21.1)

Патч-сборка поверх `hackersandslashers-2.0-beta3.jar` с близкой и чистой 1st-person позой блока щитом, режимом 1st-person простоя из **Punchy** (при сохранении всех боевых/паркур/защитных анимаций HnS от 1-го лица), совместимостью с **EMF / FA+Player**, **Mallard Guard**, **Better Combat `2.4.0+1.21.1`** и переносом фиксов из `BORGERone/better-combat` (`9896e9c`).

## Что исправлено в v6 (по результатам теста v5 в игре)

### 1. Блок щитом от 1-го лица: значительно ближе к камере и без торчащего предплечья
По 3D-матричной калибровке (`FirstPersonCameraModifier` + `XP(-90°)` предмета руки + `shield_blocking.json`, проверено на `FOV = 80°–100°` и наклонённой камере):
- **`OffhandShieldPoseModifier`** (`net.dndats.api.animations`):
  - Обновлена поза блока: рука `rot = (-45°, +31°, 0°)` (итоговый yaw `+21°` с поправкой на `-10°` из `FirstPersonCameraModifier`), `pos = (+3.9, -3.7, +0.3)` + плавный подъём щита `BLOCK_ITEM_POS = (0.0, +4.4, -4.4)` с `rot = (0, 0, 0)`.
  - Центр щита стал примерно на **31% ближе к камере** (`C_z ≈ -0.448 м` вместо `-0.651 м` в `v5`, экранная площадь щита увеличена более чем вдвое), правый верхний край остаётся слева от прицела (`x ≈ 44.4%–46%`), а предплечье больше не пересекает левый нижний угол лицевой стороны щита (`arm_vis = 0` на всём `t = 0..1`).
  - Удалён дублирующийся сдвиг приседа `smoothCrouchOffset` в `OffhandShieldPoseModifier`, так как `FirstPersonCameraModifier` уже применяет `-2.0` к `left_arm.posY`.
  - Добавлены метод `onYieldToIdle()` и корректный сброс `smoothBlockBlend = 0.0F` при старте игрока/после паузы `> 2с` (и фикс порога схождения `< 0.001F -> targetBlock`), благодаря чему щит после простоя в Punchy всегда плавно поднимается с нуля без «телепортации» и резких рывков.

### 2. Передача 1st-person простоя в Punchy с сохранением всех боевых анимаций HnS
- **`DynamicAnimationHandler`** + **`PunchyClientBridge`** (`shouldAllowFirstPersonPose`, `onFirstPersonTickEnd`):
  - При установленном моде **Punchy**:
    - В спокойном 1st-person режиме (**стойка простоя, ходьба, спринт, присед** без блока/использования/паркура/атаки) слой `FIRST_PERSON_POSE_LAYER` переводится в `FirstPersonMode.NONE` и останавливается — руки от 1-го лица анимирует **Punchy** (при этом от 3-го лица / `F5` сохраняется `POSE_LAYER` HnS).
    - При переходе в простое вызывается `onFirstPersonTickEnd(...)`: сбрасывает `OffhandShieldPoseModifier.onYieldToIdle()` и обнуляет статическую инерцию камеры `FirstPersonCameraModifier`, чтобы следующий вход в боевую позу начинался чисто.
    - Все **активные 1st-person анимации HnS полностью сохранены**: атаки (`ATTACK_LAYER`), парирование/блок (`DEFENSE_LAYER`), действия (`ACTION_LAYER`, `POSE_ACTION_LAYER`), паркур/уворот/захват/прыжок (`PARKOUR_LAYER`, `isGrappling`, `isVaulting`, `isDodging`), блок щитом в левой руке (`isBlockingWithOffhandShield`), блок мечом **Mallard Guard** (`isMallardGuardingOnly`), а также предметы основной руки со специальной 1st-person анимацией использования (например, лук — `hasFirstPersonItemUsePose`). После их окончания управление плавно возвращается Punchy.
    - Побочный бонус: при еде/питье яблока/зелья в левой руке с мечом в правой (`isUsingItem` не щит и не главная рука) Punchy также корректно остаётся активным.
  - Без установленного **Punchy** поведение полностью обратно совместимо (1st-person стойки HnS работают как обычно).
  - Сохранена полная идентичность NeoForge-аннотаций `DynamicAnimationHandler` (`@EventBusSubscriber(modid = "hackersandslashers", value = {Dist.CLIENT})` и `@SubscribeEvent` на всех 4 обработчиках).

### 3. Документация для ИИ-ассистентов
- В репозиторий и на Google Drive добавлена **`README-AI-MODDING-GUIDE.md`** — подробная инструкция по правильной сборке, пропатчиванию JAR без Gradle, работе с Mixin/NeoForge 1.21.1, 3D-матричной отладке поз и 6-ступенчатой верификации перед отправкой пользователю.

---

## Что было исправлено ранее (v1–v5)

- **v5**: Первичное выравнивание 1st-person щита и блока (`OffhandShieldPoseModifier` + `FirstPersonOffhandShieldMixin`) без скачка между `shield.json` и `shield_blocking.json`.
- **v4**: Видимость щита и предметов левой руки (`showRightItem = true`, `showLeftItem = true`) с выборочным подавлением Punchy (`isShowArm || isShowItem`).
- **v3**: Выборочная совместимость с **Punchy** (`PunchyConfigMixin`, `PunchyArmRendererMixin`), авто-совместимость рукава с **EMF / FA+Player**, фиксы блока щитом и парирования с **Mallard Guard (`1.56d`)**.
- **v1–v2**: Совместимость с **Better Combat `2.4.0+1.21.1`** (`BetterCombatMixinPlugin`, `BetterCombatFlagsMixin`, `BetterCombatPlayerAttackHelperMixin`, `CancelVanillaAttackMixin`), фиксы захвата (`Grapple`), стамины и диагонального уворота из `9896e9c`.

---

## Проверки и хэши (`v6`)

- `tools/validate_jar.py --expect-version 2.0-beta3`: **OK** (`1187` записей, `511` классов, `14.8 MB`).
- `tools/jvm_verify.py --target-major 65`: **OK** (`511` классов).
- `tools/dangling_refs.py --base hackersandslashers-2.0-beta3.jar`: **OK** (`0` висячих ссылок).
- `tools/check_mixin_class.py --base <v5.jar>`: **OK** (`56` миксин-классов, `0` новых внешних API относительно `v5`).
- `tools/mixin_clash_check.py`: **OK** (`0` конфликтов имён).
- Дифф пула констант и `RuntimeVisibleAnnotations` для `DynamicAnimationHandler`: методы и аннотации 1-в-1 совпадают с оригиналом.
- HotSpot Java `-Xverify:all`:
  - `VerifyV6Probe`: **ALL CHECKS PASSED** (простой/ходьба/спринт/присед с Punchy -> `NONE`, еда в левой руке -> Punchy, лук -> `hns_bow_charge_fp`, блок щитом -> плавный подъём из `0.0F` к `(3.9, -3.7, 0.3)` / `(0.0, 4.4, -4.4)` без двойного приседа, отпускание и повторный быстрый блок без рывка, атака `ATTACK_LAYER` с возвратом в Punchy, пропуск двуручника).
  - `VerifyV6Extra`: **ALL EXTRA CHECKS PASSED** (`MallardGuard`, `Dodge`, `Grapple`, `Vault`, зеркалирование для левши `HumanoidArm.LEFT`, работа при отсутствии `Punchy` в отдельном `ClassLoader`).
- **SHA-256 (`sha256sum -c` OK)**:
  - `hackersandslashers-2.0-beta3-fixed.jar`: `5720100c7c7993c293bbaade714104339f0d1c04da1ecdbf6a41d445afffdcbf` (`15 471 642` байт)
  - `HnS-2.0-beta3-Fixed-Source.zip`: `142d5234baeaa9381719573211b03a015ef8a9ae5db306e244c9f8b653d04627` (`35 876` байт)
  - `README-AI-MODDING-GUIDE.md`: `8a372be6b1044201a75cc3de52125dce64702649f86ae39542050a802a142c8d` (`20 737` байт)
