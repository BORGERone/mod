# Super Resolution 0.9.2-alpha.2 + Wisteria: FG-краш и обрезка кадра

Контекст: MC 1.21.1, NeoForge 21.1.265, Sodium 0.8.13, Iris 1.8.14-beta.1, Eclipse-Shader-Unstable, Immersive Portals × Sable, Distant Horizons. Wisteria `0.1.0-alpha.1` (Drive `13oEK3ApIc9eRSxI_GFLH5FiMO4Q2wCSA`). SR `0.9.2-alpha.2+opengl`. Voxy не трогать: база v12. Eclipse / IP / DH / Sodium не отключать и не даунгрейдить.

`latest-17.log` (FG выключена) в этой сессии нет; в Drive его тоже нет. Не утверждать по нему ничего.

## Краш FG: `No reusable capture slot is available; states=[DISPATCHING, DISPATCHING, DISPATCHING]`

Текст совпадает с `CaptureFrameRing.selectReusableSlot` в **установленной** 0.9.2-alpha.2 (коммит до `08d4ac4`, 2026-10-17), не с текущим `dev`.

Кольцо из 3 слотов. Свободными считаются только `REUSABLE` и `SUBMITTED`. Если все три в `DISPATCHING`, метод сразу бросает это исключение, без ожидания.

Состояния: `REUSABLE → RECORDING → SEALED → QUEUED → DISPATCHING → SUBMITTED → REUSABLE`. `DISPATCHING` ставит `FrameGenerationWorker` **до** `dispatch()` / NGX и **до** `presentationQueue.awaitCapacityFor`. `markSubmitted` — только после успешной постановки батча в очередь показа.

Следствие: `fg=wisteria:ngx` значит, что backend выбран и минимум три кадра ушли в FG-worker. Это **не** доказательство, что сгенерированные кадры показаны. Все три слота застряли в `DISPATCHING` = worker взял кадры и не дошёл до `markSubmitted` (медленный/зависший NGX, ожидание ёмкости present-очереди, исключение до markSubmitted, тупик с pacer/Reflex). Без лога краша нельзя выбрать одну причину.

Upstream `08d4ac4` (2026-11-05) ждёт слот до 5 с вместо мгновенного краша. Этого нет в alpha.2. Ожидание не лечит зависший NGX, только fail-fast. Свой патч SR/Wisteria не собирать без лога краша.

Связанный открытый баг upstream: [IReallyWantToSleep/superresolution#217](https://github.com/IReallyWantToSleep/superresolution/issues/217) (`cameraViewToClip is not invertible` на первом кадре с `fg=wisteria:ngx`). К нашему тексту краша не приравнивать.

## Обрезка кадра: DLAA ок, пониженные пресеты режут

Независимо от FG (пользователь видел при выключенной FG). DLAA = внутреннее разрешение = окно. Quality/Balanced/Performance = render < screen.

В SR два режима: `shader_compat` и `hack`. `WindowMixin` масштабирует `Window.getWidth/Height` **только** при `hack`. Eclipse в режиме совместимости SR — это `shader_compat`: окно остаётся полным, размер должен согласовать интерфейс пака (`TextureRegion`: `-1` = render, `-2` = screen). Обрезка при scale<1 согласуется с несовпадением origin/shader blit/viewport, не с самим DLAA.

В latest-13 не было `Loading shader-pack interface configuration` / `Shader pack ... supports super resolution`. Пользователь сейчас говорит, что Eclipse работает в режиме совместимости SR — это нужно подтвердить строками лога и `config.toml` (work mode, `enable_upscale`, алгоритм, ratio). Не чинить вслепую.

## Что не делать

- Не предлагать выключать Eclipse, Immersive Portals, Distant Horizons, не даунгрейдить Sodium.
- Не собирать Voxy и не трогать v12 без свежего лога+скрина под конкретную гипотезу.
- Не собирать новый jar SR/Wisteria в этом ходе: нет лога краша и нет latest-17.

## Что прислать для следующего шага

1. Лог вылета FG (`latest.log` сессии с Vulkan+FG, плюс `crash-reports/` если есть).
2. `config/super_resolution/config.toml` той же сессии.
3. `latest-17.log` (FG off) — заново, если нужен разбор обрезки.
4. Скрин обрезки при пониженном пресете и контрольный скрин DLAA в том же месте.
5. Со строк лога: `Work mode`, `Frame generation backend negotiation`, `Loading shader-pack interface`, `enable_upscale`, render/screen size если есть.
