# Build 263 — API/байткод и границы проверки

## База и совместимость

Build 263 собран только от Build 262 с SHA-256
`21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531`.
`neoforge.mods.toml` сохраняет зависимости и требования базы; версия меняется на
`0.8.0-build263-cutscene-fade`.

Новые Java-классы скомпилированы ECJ 3.46.100 для Java 21 (`classfile major 65`).
Вспомогательные compile-time API stubs не попадают в JAR. Сигнатуры target-методов
сверены с class-файлами Build 262:

- `GuideDialogue.resolveExecutionTarget(ServerPlayer, Entity, GuideNpc, Session,
  NpcDialogueGraph, int, Set): int` — вызов `CutsceneCamera.play`; локали
  player/graph/node проверены по байткоду.
- `NpcCopyModEvents.onRegisterPayloads(RegisterPayloadHandlersEvent)` — регистрация
  client-bound payload.
- `CutsceneCamera.playChained(ServerPlayer, String): boolean` и
  `CutsceneCamera.onLogout(ServerPlayer): void` — цепочки/очистка.
- `CutsceneCameraClient.handlePlay(double[][], float, int): void` и
  `handlePlaying(boolean): void` — реальный target descriptor проверен JVM.
- `NpcDialogueGraph.save(): CompoundTag`, `load(CompoundTag): NpcDialogueGraph`,
  `Node.copy(): Node` и `NpcDialogueGraphScreen.widgets(): void`.

После пользовательского подтверждения, что прежний hotfix не исправил пустой инспектор (Build262 на том же графе исправен), новый candidate меняет только UI hook. Он получает текущую ноду через штатный `node()`, вызывает существующие `button`, `remember`, `changed`, `widgets` и после базового `widgets()` напрямую добавляет лишь два созданных fade-контрола в `inspectorWidgets`. Больше не меняет shared `buildingInspector`; видимость существующих виджетов не переписывается. Новый JAR SHA `293a22505849b92cec191d66fafe189cd401dc66b7a7f17904d79d057af03bc0`, size 2,274,519 bytes. Это не подтверждённый визуальный фикс: Minecraft/NeoForge не запускались.

Fade flags — отдельные `@Unique` Node-поля через `CutsceneFadeNodeAccess263`;
`Node.copy()` переносит их в undo/duplicate snapshots. NBT записывает
`CutsceneFadeOut`/`CutsceneFadeIn`. Старые поля `animLoop` и `moveEscort` не
переиспользуются.

## Сетевой/UI и временной порядок

Серверный payload содержит два boolean и отправляется перед начальной
`CutsceneCamera.play` и перед успешной попыткой `playChained`. Клиент обрабатывает
payload через `enqueueWork`; общий payload не ссылается напрямую на client-only
классы, а вызывает overlay через отражение для dedicated-server безопасности.

Playback mixin перехватывает `CutsceneCameraClient.handlePlay([[DFI)V` и при активном
fade-out сохраняет массив камеры/speed/smoothing вместо вызова оригинала. Он также
перехватывает `handlePlaying(true)`, удерживая сигнал начала. Когда overlay достиг
полного чёрного, отложенные вызовы повторяются через `Minecraft.execute` на клиентском
потоке. Поэтому камера не может стартовать во время fade-out. Callback после
фактического `handlePlaying(true)` сообщает overlay о старте; fade-in запускается
только с этого момента. Отложенный пакет playback отменяется токеном при смене
состояния, чтобы устаревший runnable не запускал старую сцену. Запоздалый
`handlePlaying(false)` от предыдущей сцены не отменяет старт текущей, пока её камера
ещё не запущена.

Overlay рисуется через `RenderGuiEvent.Post`, полноэкранным чёрным слоем, использует
монотонное время `System.nanoTime` и smoothstep 500 мс на фазу. Обе галки дают
fade-to-black, ожидание камеры на полном чёрном и fade-from-black после её старта.
Fade-out-only мгновенно открывает слой при запуске камеры; fade-in-only держит чёрный
экран до сигнала камеры. Потерянный сигнал не удерживает экран чёрным дольше 10 с.
При выключенных обеих галках overlay сбрасывается. Playback end восстанавливает
экран, если слой ещё активен.

## Проверки и ограничения

`tests/gate_build263.py` запускает deterministic rebuild, structural JAR validation,
точный diff и metadata/lang/mixin checks. Java 21 `jvm_verify.py` проверяет изменённые
GuideDialogue/NpcCopyModEvents, target `handlePlay(double[][],float,int)` и новые
mixin/helper callbacks. Runtime fixtures используют class-файлы именно из итогового
JAR и проверяют строгую очередность fade-out → deferred camera/playing → fade-in,
оба одиночных режима, timeout, NBT/save/load, copy/undo и независимость старых полей.
`tests/gate_backup263.py` дополнительно проверяет manifest, побайтное воспроизведение
ZIP и полный gate из независимой распаковки.

Во время работы устранён дефект `tools/smt.py`: при вставке байткода теперь сдвигаются
и StackMap frame PCs, и offsets `Uninitialized_variable_info`; иначе JVM сообщала
`ClassFormatError: bad offset for Uninitialized`.

Синтетические Minecraft/NeoForge API и JVM fixtures подтверждают байткод/linkage и
проверенную логику тестовых сценариев, но не подтверждают применение mixin, реальный
network dispatch, UI и поведение модпака. Minecraft/NeoForge не запускались. Для
первого игрового подтверждения нужен прогон на копии мира по
`CHECKLIST-TEST-BUILD263-RU.md`.
