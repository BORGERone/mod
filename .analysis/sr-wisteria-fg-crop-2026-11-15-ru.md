# Super Resolution 0.9.2-alpha.2 + Wisteria: FG-краш и обрезка кадра

Контекст: MC 1.21.1, NeoForge 21.1.265, Sodium 0.8.13, Iris 1.8.14-beta.1, Eclipse-Shader-Unstable, Immersive Portals × Sable, Distant Horizons. Wisteria `0.1.0-alpha.1` (Drive `13oEK3ApIc9eRSxI_GFLH5FiMO4Q2wCSA`). SR `0.9.2-alpha.2+opengl`. Voxy не трогать: база v12. Eclipse / IP / DH / Sodium не отключать и не даунгрейдить.

Разбор лога 2026-11-15 03:21–03:22 и `config.toml` той же сессии (приложены в Arena). Это сессия с **Vulkan + FG OFF**, не latest-17.

Конфиг: `presentation.backend=VULKAN`, `frame_generation.mode=OFF`, `enable_upscale=true`, `upscale_algo=dlss`, `upscale_ratio=1.0`, `enable_unstable_incompatible_shader_support=true`, `low_latency.nv_reflex.mode=BOOST`, `enable_present_indicator=true`.

## Краш: `No reusable capture slot is available; states=[DISPATCHING, DISPATCHING, DISPATCHING]`

Текст совпадает с `CaptureFrameRing.selectReusableSlot` в **установленной** 0.9.2-alpha.2 (коммит до `08d4ac4`, 2026-10-17), не с текущим `dev`.

Кольцо из 3 слотов. Свободными считаются только `REUSABLE` и `SUBMITTED`. Если все три в `DISPATCHING`, метод сразу бросает это исключение, без ожидания.

Состояния: `REUSABLE → RECORDING → SEALED → QUEUED → DISPATCHING → SUBMITTED → REUSABLE`. `DISPATCHING` ставит `FrameGenerationWorker` **до** `dispatch()` / NGX и **до** `presentationQueue.awaitCapacityFor`. `markSubmitted` — только после успешной постановки батча в очередь показа.

### Факты лога 03:21–03:22

- Vulkan presentation реально активен: swapchain `854x480` FIFO → IMMEDIATE, затем `3840x2160` IMMEDIATE, `images=3`, **`plannedGeneratedFrames=0`**.
- `03:21:55` `Frame generation backend negotiation: fg=wisteria:ngx, lowLatency=null`. Wisteria: `NGX DLSS-G is available, maximum generated frames ... 5`. Reflex из конфига (BOOST) **не** прицепился (`lowLatency=null`).
- Поток `[SR-FrameGeneration-FG]` жив даже при `mode=OFF` (сброс pacing на смене swapchain, provider `wisteria:ngx`).
- Eclipse: `Using shaderpack: Eclipse-Shader-Unstable.zip`. Строк `Loading shader-pack interface configuration` / `Shader pack ... supports super resolution` **нет**. `colorwheel_patcher`: нет патча Eclipse. Неофициальный SR-интерфейс пака в этом логе не подтверждён.
- `03:22:11` `Initializing algorithm NVIDIA DLSS`. `03:22:20` старт integrated server. `03:22:32.087` FATAL на render thread при входе в мир (сразу после ImmPtl config packet).
- Стек: `GameRenderer.render` → mixin `captureFinalColor` → `FrameCaptureManager.captureFinalColor` → `CaptureFrameRing.selectReusableSlot`. Mixin вызывается **каждый** кадр при запрошенном Vulkan presentation, **без** проверки FG mode. `captureFinalColor` в отличие от hudless **не** проверяет `isWorldFrame()`.

Вывод: вылет **не требует включённой генерации кадров**. При Vulkan capture-кольцо из 3 слотов заполняется путём показа; `fg=wisteria:ngx` при `plannedGeneratedFrames=0` — выбранный provider worker'а, не доказательство показанных generated-кадров. Три слота в `DISPATCHING` на join = worker взял кадры и не дошёл до `markSubmitted` за время первых тиков мира (swapchain 4K, init DLSS, нагрузка входа). Конкретную причину зависания worker'а лог не называет (нет ошибки NGX/present до FATAL).

Upstream `08d4ac4` (2026-11-05) ждёт слот до 5 с. Этого нет в alpha.2; ожидание не лечит зависший present/NGX. Свой патч SR/Wisteria в этом ходе не собирать.

Связанный открытый баг upstream: [IReallyWantToSleep/superresolution#217](https://github.com/IReallyWantToSleep/superresolution/issues/217). К этому стеку не приравнивать: в логе нет `cameraViewToClip`.

## Обрезка кадра: DLAA ок, пониженные пресеты режут

Независимо от FG (пользователь видел при выключенной FG). DLAA = внутреннее разрешение = окно. Quality/Balanced/Performance = render < screen.

В SR два режима: `shader_compat` и `hack`. `WindowMixin` масштабирует `Window.getWidth/Height` **только** при `hack`. Eclipse в режиме совместимости SR — это `shader_compat`: окно остаётся полным, размер должен согласовать интерфейс пака (`TextureRegion`: `-1` = render, `-2` = screen). Обрезка при scale<1 согласуется с несовпадением origin/shader blit/viewport, не с самим DLAA.

Этот лог — `upscale_ratio=1.0` (как DLAA) и вылет на входе в мир. Обрезку пониженного пресета он не фиксирует. Строк SR-интерфейса Eclipse нет; `enable_unstable_incompatible_shader_support=true`. Не чинить обрезку вслепую. Нужен скрин обрезки + лог сессии, которая не вылетела на join.

## Что не делать

- Не предлагать выключать Eclipse, Immersive Portals, Distant Horizons, не даунгрейдить Sodium.
- Не собирать Voxy и не трогать v12 без свежего лога+скрина под конкретную гипотезу.
- Не собирать новый jar SR/Wisteria в этом ходе: гипотеза зависания present-worker без точки фикса в нашем коде.

## Что прислать дальше

1. Скрин обрезки при Quality/Balanced (не DLAA) и контрольный DLAA, если сессия доживёт.
2. При повторном вылете — тот же `latest.log` + `crash-2026-*-client.txt`.
3. Если удастся постоять в мире с Vulkan+FG OFF: есть ли белый/cyan квадрат present indicator и строка `Work mode` / shader-pack interface.
