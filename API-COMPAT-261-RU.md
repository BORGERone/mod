# Build 261 — совместимость и границы изменений

## База

Build 261 построен детерминированно от точного Build 260: `settledlands-0.8.0-build260-npc-facing-sounds.jar`, SHA-256 `8d47171845652c1a3499643ca1936a7d6b742d9e88811751d94d84bef47d2ebc`. Новая версия: `0.8.0-build261-idle-look-punchy-animation`.

Изменения в JAR: обновлены `NpcAutoAiEngine255`, `NpcMotionBridge256`, `FirstPersonPossession256`, его вложенный `RenderContext`, `settledlands.mixins.json`, `META-INF/neoforge.mods.toml` и patch-info; добавлен клиентский `PunchyFirstPersonContextMixin261`. Остальные записи Build 260 побайтно совпадают. Для Build 258 ABI `AttributeInstance.removeModifier(ResourceLocation)Z` сохранён.

## NPC: idle-взгляд и движение

- `NpcAutoAiEngine255.tickIdleLookAround` задаёт случайные направления на 25–80 градусов от направления корпуса, с плавной интерполяцией головы. Меняется только `yHeadRot`; body yaw, навигация и фактическое направление перемещения не изменяются. Взгляд игрока, разговор, цель/бой, движение, полёт и активный маршрут имеют приоритет над idle-сканированием.
- На Ivy-риге при доступном EMF/ETF Fresh Animations и Touch’n Motion не обходятся отдельной веткой при разговоре или активном full-body Blockbench-клипе. Базовая FA/TnM-поза продолжает вычисляться; затем накладывается talking/custom clip. При ходьбе сохранённая локомоционная поза обеих ног восстанавливается после наложения полнотельного клипа. Авторские сюжетные/боевые клипы остаются, а не заменяются FA.
- Если опциональный анимационный стек не инициализируется, рендер остаётся на прежнем BBModel fallback. Трупы и trigger NPC по-прежнему обходят мост анимации.

## Punchy / руки при вселении

Новая клиентская mixin-обвязка обрамляет `ItemInHandRenderer.renderHandsWithItems` и добавляет possession-контекст на всё время ванильного прохода рук. Внутри него `PossessedFirstPersonModelMixin256` распознаёт прямой `PlayerModel`-рендер (без PAL-маркера и без обязательного `RenderArmEvent`) и заменяет Steve arms костями правой/левой руки текущей модели NPC с её текстурой. При вложенных рендерах выбирается корректный активный first-person context. Mixin имеет `require=0`; новых compile/runtime-зависимостей от Punchy нет.

Punchy-версия пользователя неизвестна, двоичный мод не входил в тестовый набор, Minecraft/NeoForge с Punchy не запускался. Поэтому зафиксировано совместимое место интеграции и JVM-проверен PlayerModel-based путь, но требуется проверка в реальном клиенте с установленной версией Punchy из `CHECKLIST-TEST-BUILD261-RU.md`.

## Проверка

`tests/gate_build261.py` — воспроизводимая сборка и точный diff, структура ZIP/TOML/mixins, статические инварианты, Java 21 runtime idle-look/AI scenarios и end-to-end fixture анимаций/первого лица (включая hurt → dialogue, обе движущиеся ноги и Punchy-style PlayerModel). Два verifier-valid negative controls убивают idle-look no-op и first-person-context no-op. Это не игровой тест и не подтверждает работу конкретной версии Punchy в запущенном модпаке.
