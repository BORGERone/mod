# SettledLands: продолжить работу

## 1. Текущая сборка

**Build 254 (ожидает проверки в игре):** `settledlands-0.8.0-build254-ui-ivy.jar`, 2 181 852 байта, SHA-256 `19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c`. Построена детерминированно от точной подтверждённой пользователем базы **build 253** (`a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e`). Независимый исходный backup: `SettledLands-Build254-UiIvy-Source.zip`; итоговые логи полного шлюза (`GATE-RESULT-254.txt`, `GATE-BACKUP-RESULT-254.txt`) и `SHA256SUMS-build254.txt` лежат вне архива. Не выдавать JAR до успешного полного шлюза HANDOFF §8 (`tests/gate_build254.py`) и независимого повтора из распакованного backup.

Прочитать по порядку: этот файл → `HANDOFF-NEXT-SESSION-RU.md` → `AI-GUIDE-SettledLands.md`; затем `SESSION-STATE.md`, `README-Build254-UiIvy-RU.txt`, `API-COMPAT-254-RU.md` и `CHECKLIST-TEST-BUILD254-RU.md`. Видео у пользователя не просить.

## 2. Что подтверждено в 253 и что изменено в 254

- Пользователь проверил **build 253** в игре («Все хорошо») и поставил 5 задач для **build 254**: дефолтная позиция ноды характеристик слева от начальной реплики, исправление отрисовки символов слайдера поверх других окон, рендер выпадающего списка модели поверх всех элементов, дефолтная модель НПС `ivy_oleander.bbmodel` и кнопка «рандомизировать» внизу ноды характеристик с радиусом от `-2` до `+2` (включая `0`).
- В **build 254**:
  1. Нода «Характеристики» (`TYPE = 16`) создаётся слева от начальной реплики `g.get(g.entry)` на `(-360, 0)` вместо `(568, -240)`.
  2. В `NpcStatsGraphUi.draw` все `drawCenteredString` (`dropShadow=true`, `SHADOW_OFFSET = +0.03f` по Z) заменены на `drawString(..., false)` с центрированием по `Font.width`.
  3. В `NpcConfigScreen.render` (`pc=752`) и `NpcBranchScreen.render` (`pc=605`) отрисовка попапа дропдауна обернута в `NpcDropdownTop254.renderPopupTop` (`flush`, `translate(0, 0, +500.0f)` поверх 3D-превью `Z = 50..95`, `flush`, `popPose`).
  4. Модель `ivy_oleander.bbmodel` встроена в `/assets/settledlands/dragon_default/ivy_oleander.bbmodel`, автоматически извлекается при старте `NpcClient` в `<gameDir>/settledlands/models/ivy_oleander.bbmodel` и назначена дефолтом в `NpcProfile` и `GuideNpc` (с сохранением явного `Texture: ""` и исключением `GuideTriggerNpc`/`GuideCorpseNpc`).
  5. Высота ноды увеличена до `170` px; внизу добавлена кнопка «Рандомизировать» (`[12..278) x [134..156)`), меняющая каждый параметр на шаг `[-2..+2]` (включая `0`) с поддержкой `Ctrl+Z`.
- Все классы анимаций, HnS, FA, TnM, щита и копирования НПС (`BbModel`, `PlayerBodyRenderer`, `FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `NpcCopyPickPayload`, `NpcCopyEggFix253`, `GuideTriggerNpc`, `GuideCorpseNpc`, `GuideRenderer`) сохранены байт-в-байт. Touch’n Motion для самих NPC (`GuideNpc`) по-прежнему не реализован.

## 3. Проверка пользователем

Оставить в папке `mods` **только один** JAR SettledLands (`settledlands-0.8.0-build254-ui-ivy.jar`). Проверить по `CHECKLIST-TEST-BUILD254-RU.md` все 5 пунктов. При замечаниях достаточно свежего `logs/latest.log` и краткого описания шагов.
