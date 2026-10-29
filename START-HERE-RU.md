# SettledLands: продолжить работу

## 1. Текущая сборка

**Build 253 (ожидает проверки в игре):** `settledlands-0.8.0-build253-shield-copy.jar`, 1 629 114 байт, SHA-256 `a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e`. Построена детерминированно от точной подтверждённой пользователем базы **build 252** (`29a0e6e6245d9a906865a4020cf0f2c4532e3a488914260bf9d80df4e57dbc3d`). Независимый исходный backup: `SettledLands-Build253-ShieldCopy-Source.zip`; итоговые логи полного шлюза (`GATE-RESULT-253.txt`, `GATE-BASE-RESULT-253.txt`, `GATE-BACKUP-RESULT-253.txt`) и `SHA256SUMS-build253.txt` лежат вне архива. Не выдавать JAR до успешного полного шлюза HANDOFF §8 (`tests/gate_build253.py`) и независимого повтора из распакованного backup.

Прочитать по порядку: этот файл → `HANDOFF-NEXT-SESSION-RU.md` → `AI-GUIDE-SettledLands.md`; затем `SESSION-STATE.md`, `README-Build253-ShieldCopy-RU.txt`, `API-COMPAT-253-RU.md` и `CHECKLIST-TEST-BUILD253-RU.md`. Видео у пользователя не просить.

## 2. Что подтверждено в 252 и что изменено в 253

- Пользователь проверил **build 252** в игре и подтвердил, что удары от 1-го и 3-го лица, ползание и модель вселённого NPC при перезаходе работают штатно, и поставил две задачи для **build 253**: убрать старый «фикс» поворота щита при блоке и исправить зависание и вылет при копировании НПС.
- В **build 253**:
  1. В `BbModel.renderActorHeldItem` удалён старый блок наклона щита при блоке (`+40°` по X и сдвиг `(0.0F, 0.15F, -0.05F)` на смещениях `184..228`) при полном сохранении хуков `PlayerMotionBridge.itemPosition` и `PlayerMotionBridge.renderHeldItem` и всех остальных методов `BbModel`.
  2. В `NpcCopyPickPayload.buildCopyEgg(GuideNpc)` через хелпер `NpcCopyEggFix253` добавлена запись обязательного строкового поля `"id"` (`"settledlands:guide"`, либо `"settledlands:guide_trigger"` с `NpcContent.triggerEgg()` для `GuideTriggerNpc`) в `DataComponents.ENTITY_DATA`, что устраняет ошибки `CustomData.CODEC_WITH_ID` (`Missing id for entity in:`) в `ItemStack.save` на Render thread и краш `ReportedException: Saving entity NBT` при сохранении игрока.
- Все остальные классы 252/251 (`PlayerBodyRenderer`, `FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `GuideNpc`, `GuideRenderer`) и все механики 247–243 сохранены байт-в-байт. Touch’n Motion для самих NPC (`GuideNpc`) по-прежнему не реализован.

## 3. Проверка пользователем

Оставить в папке `mods` **только один** JAR SettledLands (`settledlands-0.8.0-build253-shield-copy.jar`). Проверить по `CHECKLIST-TEST-BUILD253-RU.md` положение щита при блоке, копирование НПС и спавн копии с сохранением мира. При замечаниях достаточно свежего `logs/latest.log` и краткого описания шагов.
