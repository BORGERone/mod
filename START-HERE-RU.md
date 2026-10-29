# SettledLands: продолжить работу

## 1. Текущая сборка

**Build 252 (экспериментальная, ожидает проверки ударов от 1-го лица в игре):** `settledlands-0.8.0-build252-fp-hns.jar`, 1 627 973 байт, SHA-256 `29a0e6e6245d9a906865a4020cf0f2c4532e3a488914260bf9d80df4e57dbc3d`. Построена детерминированно от точной базы **build 251** (`a3be663ea42de11e815b67b710e589bb92dd2752eb6fbd0d5408fdba3c9d256a`). Независимый исходный backup: `SettledLands-Build252-FirstPersonHnS-Source.zip`; итоговые логи полного шлюза (`GATE-RESULT-252.txt`, `GATE-BACKUP-RESULT-252.txt`) и `SHA256SUMS-build252.txt` лежат вне архива. Не выдавать JAR до успешного полного шлюза HANDOFF §8 (`tests/gate_build252.py`) и независимого повтора из распакованного backup.

Прочитать по порядку: этот файл → `HANDOFF-NEXT-SESSION-RU.md` → `AI-GUIDE-SettledLands.md`; затем `SESSION-STATE.md`, `README-Build252-FirstPersonHnS-RU.txt`, `API-COMPAT-252-RU.md` и `CHECKLIST-TEST-BUILD252-RU.md`. Видео у пользователя не просить.

## 2. Что подтверждено в 251 и что изменено в 252

- Пользователь проверил **build 251** в игре и подтвердил, что основные проблемы исправлены (анимации со стороны от 3-го лица, ползание и модель вселённого NPC после перезахода), но сообщил об оставшейся проблеме: **от первого лица** с установленными Hackers and Slashers + Touch’n Motion удары меча идут вниз под себя и хитбокс не достаёт до моба перед игроком.
- В **build 252** добавлены `FirstPersonSwordBridge252` и `FirstPersonPalEmfAccess252` + вызов на смещении `0` `PlayerBodyRenderer.onRenderPlayerPre`: в проходе 1-го лица PAL (`FirstPersonMode.THIRD_PERSON_MODEL`, все 5 боевых/действенных слоёв HnS) поза рук/оружия PAL снимается до `EMFCompatAnimationHook` (Core) / JPM (`FA+Player`) / `TouchNMotionHook` (TnM) и восстанавливается после `EMFModelPartRoot.animate()`, а кастомные дочерние JEM-части сбрасываются через `resetPose()` (чтобы сгибы из кадров `F5` не переносились в 1-е лицо). В результате `PlayerItemInHandLayer` и `BladeTracker` получают точные векторы удара PAL вперёд по прицелу.
- Все классы 251 (`BbModel`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `GuideRenderer`) и все механики 247–243 сохранены байт-в-байт: запрет атрибутированного урона бестелесного в Верхнем мире при разрешении в чистилище и защита от молока (247), характеристики NPC 10/10/10, экран вселения с локальным `культ ≥ 1`, 50% гравитации и игнорирование мобами (246), износ макс. HP −4/−8/−16 после возрождений на маяке и обычный дроп на 4-й смерти (245), возврат вещей/поводок и 9 изумрудов у своего тела без очереди (244–243). Touch’n Motion для самих NPC (`GuideNpc`) по-прежнему не реализован.

## 3. Проверка пользователем

Оставить в папке `mods` **только один** JAR SettledLands (`settledlands-0.8.0-build252-fp-hns.jar`). Проверить по `CHECKLIST-TEST-BUILD252-RU.md` взмахи и попадания мечом от первого лица (стоя, в приседе, после переключения `F5` → 1-е лицо, во вселённом теле Иви и обычным игроком). При замечаниях достаточно свежего `logs/latest.log` и краткого описания шагов.
