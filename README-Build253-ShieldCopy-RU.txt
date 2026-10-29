SettledLands 0.8.0-build253-shield-copy
Файл: settledlands-0.8.0-build253-shield-copy.jar (1 629 114 байт)
SHA-256: a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e
База: точная подтверждённая пользователем сборка 252 (settledlands-0.8.0-build252-fp-hns.jar, SHA-256 29a0e6e6245d9a906865a4020cf0f2c4532e3a488914260bf9d80df4e57dbc3d).

Что изменено в сборке 253:
1. Удалён старый «фикс» поворота щита при блоке в BbModel.renderActorHeldItem:
   - На смещениях 184..228 удалён добавленный в старых сборках 179/180 условный наклон +40° по оси X и сдвиг (0.0F, 0.15F, -0.05F) при entity.isBlocking() && stack.getUseAnimation() == UseAnim.BLOCK.
   - Ориентация предметов и щита в руках теперь использует только стандартные -90° по X и 180° по Y и существующие хуки PlayerMotionBridge (PAL/HnS). Множитель скорости шага при блоке в setupAnimActor не менялся.
2. Исправлены зависание клиента и вылет сервера при копировании НПС:
   - В NpcCopyPickPayload.buildCopyEgg(GuideNpc) перед записью DataComponents.ENTITY_DATA (CustomData.of(entityTag)) добавляется обязательное строковое поле "id" ("settledlands:guide", либо "settledlands:guide_trigger" с соответствующим яйцом для GuideTriggerNpc) через изолированный хелпер NpcCopyEggFix253.
   - В Minecraft 1.21.1 постоянный кодек DataComponents.ENTITY_DATA (CustomData.CODEC_WITH_ID) требует строковый ключ "id" (TAG_STRING = 8). Его отсутствие вызывало ежекадровые исключения ItemStack.save на Render thread (с просадками OSHI WMI ~550 мс) и краш ReportedException: Saving entity NBT при сохранении игрока.

Что сохранено без изменений:
- Все классы 252/251 для взмахов HnS от 1-го лица (FirstPersonSwordBridge252, FirstPersonPalEmfAccess252, PlayerBodyRenderer) и 3-го лица Иви с FA + TnM + PAL (PlayerMotionBridge, HackSlashAccess, FreshEmfAccess), ползания и синхронизации модели при входе (PossessionLoginSync251).
- Все механики 247–243: характеристики NPC 10/10/10, экран вселения с локальным культ >= 1, бестелесность (50% гравитации, игнорирование мобами, запрет атрибутированного урона в Верхнем мире при разрешении в чистилище, защита от молока), износ макс. HP -4/-8/-16 и обычный дроп на 4-й смерти, возврат вещей/поводок и возрождение у своего тела за 9 изумрудов без очереди.

Ограничения проверки:
- Реальные классы мода, JOML 1.10.5 и совместимые компоненты проверены на настоящей Java 21 (-Xverify:all) с синтетическими API окружения. Полный запуск клиента/сервера Minecraft 1.21.1 + NeoForge и визуальный рендер не выполнялись — необходима проверка в игре по CHECKLIST-TEST-BUILD253-RU.md.
