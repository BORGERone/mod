SettledLands 0.8.0-build254-ui-ivy
Файл: settledlands-0.8.0-build254-ui-ivy.jar (2 181 852 байт)
SHA-256: 19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c
База: точная подтверждённая пользователем сборка 253 (settledlands-0.8.0-build253-shield-copy.jar, SHA-256 a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e).

Что изменено в сборке 254:
1. Дефолтная позиция ноды «Характеристики» слева от начальной реплики:
   - В NpcStatsGraph.ensure(NpcDialogueGraph) якорем по умолчанию теперь берётся узел начальной реплики g.get(g.entry) (фоллбэк — самый левый обычный узел с special == 0), а нода характеристик ставится на (clampCoord(anchor.x - 360), clampCoord(anchor.y)) = (-360, 0) вместо прежних (568, -240) справа от «Прибытие #2».
2. Исправлена отрисовка символов слайдера поверх других окон:
   - В NpcStatsGraphUi.draw все вызовы GuiGraphics.drawCenteredString (который рисует тень со сдвигом Font.SHADOW_OFFSET = +0.03F по Z и оставался в depth-буфере поверх перекрывающих плоскостей на Z = 0) заменены на drawString(..., dropShadow=false) с ручным центрированием через Font.width.
3. Выпадающий список выбора модельки рисуется поверх всех элементов:
   - В NpcConfigScreen.render (pc=752) и NpcBranchScreen.render (pc=605) вызовы NpcUi$NpcDropdown.renderPopup перенаправлены через NpcDropdownTop254.renderPopupTop с g.flush(), сдвигом PoseStack по Z на +500.0F (поверх 3D-превью сущности Z = 50..95 и всех виджетов), повторным flush() и popPose().
4. Дефолтная модель НПС — ivy_oleander.bbmodel:
   - Модель ivy_oleander.bbmodel встроена в /assets/settledlands/dragon_default/ivy_oleander.bbmodel и при инициализации NpcClient автоматически извлекается в <gameDir>/settledlands/models/ivy_oleander.bbmodel (если файл отсутствует).
   - В NpcProfile (<init> и load без ключа "Texture") и GuideNpc (defineSynchedData, defaultProfile, readAdditionalSaveData) дефолтной моделью назначена "ivy_oleander.bbmodel" с сохранением явного выбора стандартной модели (Texture: "") и исключением GuideTriggerNpc и GuideCorpseNpc.
5. Кнопка «Рандомизировать» внизу ноды характеристик:
   - Высота ноды «Характеристики» увеличена с 140 до 170 пикселей; в нижней части ноды добавлена кнопка «Рандомизировать» (gui.settledlands.stats.randomize), изменяющая каждую из 3 характеристик независимо на случайный шаг в диапазоне [-2..+2] (включая 0 без изменения параметра) с клампом [1..100] и поддержкой отмены Ctrl+Z.

Что сохранено без изменений:
- Байт-в-байт сохранены все классы 253/252/251 (BbModel, BbModelParser, PlayerBodyRenderer, FirstPersonSwordBridge252, FirstPersonPalEmfAccess252, PlayerMotionBridge, HackSlashAccess, FreshEmfAccess, PossessionSystem, PossessionLoginSync251, NpcCopyPickPayload, NpcCopyEggFix253, GuideTriggerNpc, GuideCorpseNpc, GuideRenderer).
- Все механики 247–243 сохранены без регрессий.

Ограничения проверки:
- Реальные классы мода, JOML 1.10.5, Gson 2.10.1 и совместимые компоненты проверены на настоящей Java 21 (-Xverify:all) с синтетическими API окружения. Полный запуск клиента/сервера Minecraft 1.21.1 + NeoForge и визуальный рендер не выполнялись — необходима проверка в игре по CHECKLIST-TEST-BUILD254-RU.md.
