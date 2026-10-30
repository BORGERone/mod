# Build 262 — API/ABI и регрессии

## База и точечное изменение

- База: `settledlands-0.8.0-build261-idle-look-punchy-animation.jar`, SHA-256 `979d64831b47336a84a72ec0d803da4f19d5ee6e1fdb85e542e7e08b03647df6`.
- Build 262 изменяет только три ZIP entries: `dev/settledlands/npc/NpcAutoAiEngine255.class`, `META-INF/neoforge.mods.toml`, `settledlands-patch-info.txt`. Все остальные файлы Build 261 побайтово сохранены.
- Публичные подписи и типы методов не меняются. В engine добавлен публичный вспомогательный метод `tickBodyFacingMovement(GuideNpc)`; существующие `runMobilityAndAutoAi(GuideNpc)` и `tickIdleLookAround(GuideNpc)` сохраняют ABI.
- Renderer `NpcMotionBridge256`, его временное movement-facing переопределение с восстановлением состояния в `finally`, client mixins и конфигурация не изменялись.

## Реализация

`runMobilityAndAutoAi` сначала сохраняет yaw по вектору фактического движения, затем запускает idle look. При остановке случайные цели не привязаны к новому исходному направлению; голова следует с коэффициентом `0.12F`, torso/body yaw и entity yaw — с коэффициентом `0.035F`. Случайные цели находятся на расстоянии 25–80 градусов и обновляются с паузой 25–74 тика. Idle scan уступает диалогу, бою, активному target/navigation, движению, взгляду на ближайшего игрока и активному autonomous wander/return.

## Проверки

`tests/gate_build262.py` подтверждает точную базу/diff, детерминированное воспроизведение, структуру JAR, сохранение Build 261 renderer и ABI-референса `AttributeInstance.removeModifier(ResourceLocation)`, Java 21 runtime post-stop и regression scenarios, а также negative control, убивающий no-op idle torso. Включён Build 261 animation/first-person runtime regression. Настоящий Minecraft/NeoForge/modpack не запускался.
