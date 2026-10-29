# SettledLands: продолжить работу

## 1. Текущая сборка

**Build 255 (ожидает проверки в игре):** `settledlands-0.8.0-build255-auto-ai.jar`, 2 215 570 байт, SHA-256 `7866e20a75eb5ef53e254e8e34a5eacc155d0b3ac768b99977ad4d95358b6541`. Построена детерминированно от точной базы **build 254** (`19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c`). Независимый исходный backup: `SettledLands-Build255-AutoAi-Source.zip`; итоговые логи полного шлюза (`GATE-RESULT-255.txt`, `GATE-BACKUP-RESULT-255.txt`) и `SHA256SUMS-build255.txt` лежат вне архива. Не выдавать JAR до успешного полного шлюза HANDOFF §8 (`tests/gate_build255.py`) и независимого повтора из распакованного backup.

Прочитать по порядку: этот файл → `HANDOFF-NEXT-SESSION-RU.md` → `AI-GUIDE-SettledLands.md`; затем `SESSION-STATE.md`, `README-Build255-AutoAi-RU.txt`, `API-COMPAT-255-RU.md` и `CHECKLIST-TEST-BUILD255-RU.md`. Видео у пользователя не просить.

## 2. Что добавлено в 255 поверх 254

- По запросу пользователя в **build 255** добавлен автономный ИИ НПС (по умолчанию выключен, чтобы не сдвигать сюжетных и торговых НПС):
  1. **Свободная ходьба вокруг исходной точки с возвратом (`autoWander`, `wanderRadius` `1..64`, дефолт `10`):** НПС запоминает домашнюю точку в NBT (`Guide.AutoHomeSet`, `AutoHomeX/Y/Z`), гуляет по безопасным блокам внутри радиуса без обязательных точек жезла подвижности и автоматически возвращается в радиус при смещении или после боя. Диалог с игроком, поводок, езда пассажиром, сопровождение (`escort`), `customMoveTarget` и активный маршрут жезла имеют приоритет; `GuideCorpseNpc` и `GuideTriggerNpc` полностью исключены.
  2. **Автоатака враждебных мобов комбо Иви без фаерболов (`autoAttackHostiles`, `aggroRadius` `1..64`, дефолт `12`):** НПС атакует враждебных мобов (`Enemy`) в радиусе и отвечает ударившим его нейтральным мобам (не трогая игроков, мирных животных и других `GuideNpc`), используя рукопашные комбо и увороты Иви (`JAB`, `HOOK`, `JAB_RIGHT_CROSS`, `JAB_JAB_HOOK`, `DASH_RIGHT_CROSS`, `dodge_left/right`, `orthodox_idle`, `exit_orthodox`) строго **без фаерболов**, с масштабированием силы удара и скорости от параметров `Урон` и `Скорость` ноды «Характеристики».
  3. **Двусторонняя настройка в двух местах:** секция «Автономный ИИ» во вкладке «Подвижность» `NpcConfigScreen` (3D-превью на вкладке «Персонаж» не затронуто) и перетаскиваемая карточка «Автономный ИИ» (`Z = 0.0`, `dropShadow = false`, `Ctrl+Z` / `Ctrl+Y`, `GraphOnly` save) под нодой «Характеристики» в `NpcDialogueGraphScreen`, включая сохранение в яйце копии НПС.
- Все 5 правок **build 254** и все классы анимаций, HnS, FA, TnM, щита и копирования НПС (`BbModel`, `PlayerBodyRenderer`, `FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `NpcCopyPickPayload`, `NpcCopyEggFix253`, `NpcStatsGraph`, `NpcStatsGraphUi`, `NpcDefaultModel254`, `NpcDropdownTop254`, `GuideTriggerNpc`, `GuideCorpseNpc`, `GuideRenderer`) сохранены байт-в-байт. Touch’n Motion для самих NPC (`GuideNpc`) по-прежнему не реализован.

## 3. Проверка пользователем

Оставить в папке `mods` **только один** JAR SettledLands (`settledlands-0.8.0-build255-auto-ai.jar`). Проверить по `CHECKLIST-TEST-BUILD255-RU.md` свободную ходьбу, возврат к точке, рукопашный бой комбо Иви и синхронизацию настроек между вкладкой «Подвижность» и редактором графа. При замечаниях достаточно свежего `logs/latest.log` и краткого описания шагов.
