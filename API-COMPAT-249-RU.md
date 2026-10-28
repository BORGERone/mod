# Аудит совместимости 249

Основа 247, изменён только путь `BbModel.renderActor`: wrapper и замена одного
вызова `setupAnimRaw`. Метод `setupAnim` NPC и `setupAnimRaw` байт-в-байт прежние.
При активном мосте клипы игрока не вычисляются, при отключённом вызывается
прежний `setupAnimRaw` с теми же аргументами. Ресурсы/UV/parser/рендеры
`GuideRenderer` и `PlayerBodyRenderer` побайтно совпадают с 247.

В NeoForge 1.21.1 `PlayerRenderer.render` вызывает `setModelProperties` перед
`RenderPlayerEvent.Pre`; из текущего рендера берутся crouching и позы рук. Для
изолированной модели без подмены текущей `PlayerModel.createMesh`,
`LayerDefinition.create(...).bakeRoot`, `prepareMobModel` и `setupAnim` формируют
базу. `Entity.shouldRiderSit`, фазу ходьбы, возраст, угол головы и attackTime
учитывает передача аргументов. Полное воспроизведение всех внешних анимационных
модов, ванильных мировых трансформаций, armour/cape и first-person не заявлено.

TnM 1.0.4 принимает плоские ванильные кости. Для каждой кости результат переносится
как `Vанимация × Vрест⁻¹ × Ivyрест`, затем вложенные локальные матрицы решаются
parent-first. `ModelPart.getAllParts().resetPose()` восстанавливает начальную позу,
созданную штатным `BbModelParser`, и убирает предыдущие клипы. Кости обновляются
только после проверки матриц; непредставимый shear/NaN ведут к fallback. Ресурсы
UV и PNG не редактируются. При неизвестном реальном состоянии/разных моделях и
ресурс-паках причину видимого искажения нельзя считать доказанной.

Источники API:
- NeoForge patch: https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/client/renderer/entity/player/PlayerRenderer.java.patch
- NeoForge living renderer: https://github.com/neoforged/NeoForge/blob/1.21.1/patches/net/minecraft/client/renderer/entity/LivingEntityRenderer.java.patch
- Minecraft 1.21 PlayerModel/HumanoidModel/PartDefinition/ModelPart в https://github.com/Yeet-Masta/MCP-1.21/tree/main/src/main/java/net/minecraft/client/model
- TnM 1.0.4 проверен по bytecode внешнего JAR SHA `3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56`; GPL-JAR не входит в мод/backup.

JVM использует настоящие 249 BbModel/BbAnimation/actor и отдельные TnM-алгоритмы,
но тестовые Minecraft/NeoForge/окружение TnM. Никаких выводов об игре, реальном
буфере вершин, анимации конкретного модпака или визуальном исправлении UV.
