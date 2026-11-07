# Гайд агента (постоянные правила сессии BORGERone/mod)

## 1. Доставка артефактов (ОБЯЗАТЕЛЬНО каждый ход, когда есть новый jar/бинарник)
1. Собрать в CI (workflow build-voxy-neoforge.yml / build-ipfix).
2. Залить на Google Drive, папка "Arena Minecraft jars"
   (id 1RhdcdqIix9frIZCUGjNKo02yJf_Pc-1U,
   https://drive.google.com/drive/folders/1RhdcdqIix9frIZCUGjNKo02yJf_Pc-1U),
   сделать make_public(reader, без discovery).
3. Дать пользователю ПРЯМУЮ ссылку Drive `https://drive.google.com/uc?id=<FILE_ID>&export=download`
   + ссылку на папку. GitHub-ссылку (draft/релиз) давать как запасную.
4. Неотправленный Drive-линк = невыполненная доставка, даже если jar собран.

## 2. Чистота песочницы и бэкап
- Песочница (/home/user, /tmp) временная и ограниченная: файлы чистятся между ходами.
- Всё важное дублировать ВНЕ песочницы: знания/патчи — git (.analysis/, workflow);
  бинарники — Google Drive + GitHub (draft-релизы); транзит байтов CI→песочница —
  orphan-commit тег arena-artifact-<run_id> (удалять сразу после проверки sha256).
- В конце хода: удалять крупные локальные бинарники и врем. каталоги, контролировать `df -h`.
- Локально держать только исходники/патчи/логи малого размера.

## 3. Откаты
- Держать таблицу known-good ниже актуальной; на «откати» сразу давать обе ссылки
  (Drive + GitHub) на known-good jar, без новых экспериментов.
- Отклонённые сборки помечать в таблице и убирать с Drive в корзину.

## 4. Коммуникация
- По-русски, коротко; ссылки только plain text; не предлагать: Distant Horizons,
  даунгрейд Sodium, отключение Eclipse/Immersive Portals, LOD без порталов (v4-ошибка).

## 5. Таблица known-good / отклонённые
| сборка | статус | sha256 | Drive | GitHub |
|---|---|---|---|---|
| voxy v5 (iris-ipfix-v5) | РАБОЧАЯ (база/откат) | 32fa43034507b1287926ec36e856d259fbc0642e233923fe5d2b73eeeb519b9c | id 1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z (uc?id=1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z&export=download) | untagged-45d58ca9715927cdc0a7 |
| voxy v8 (pipeline takeover) | ОТКЛОНЕНА (LOD-маски другого измерения) | d7b27398ff609ec5ce70cbffe7edff3a99e94295c1dc797a40c1a914264c1ea4 | в корзине | draft run 42391451970 |
| voxy v9 (clear colourTex) | ОТКЛОНЕНА (ничего не изменила: чищена не та текстура) | dfcf854e7e0e857020eed99c36bf639c18aa9183263061a2efa60febfc267652 | в корзине | run 42398948971 |
| voxy v10 (iris-ipfix-v10) | КАНДИДАТ (clear colourTex+colourSSAOTex) | 99cf32ffd8f8e32ed2735e83b6326814120e64491222b72ef5e412e43524ea5a | id 1-0ZE6q8RSJg_3jKodYrpuwkw5N-FPxXx (uc?id=1-0ZE6q8RSJg_3jKodYrpuwkw5N-FPxXx&export=download) | run 42409851350 |
| ipl-copy-fix 1.0.0 | РАБОЧАЯ (шим копий IP×Iris) | ea98169cd0defb9a34be16d38d2b02e893dcc50309c4ec345009fcee81085aef | id 1yPr7qGuGlVl3HAz4lMjLm9LQt7mzczOJ (uc?id=1yPr7qGuGlVl3HAz4lMjLm9LQt7mzczOJ&export=download) | releases/tag/ipl-copy-fix-1.0.0 |
| voxy v6 (deferred composite) | ОТКЛОНЕНА (радужные LOD) | — | в корзине | — |
| voxy v7 (nether lod off) | ОТКЛОНЕНА (смаз в верхнем мире) | b8778bb286ec83612a070c2603d340313c31fdc6aa25c7bfe8fc2075f503a984 | в корзине | draft удалён |

## 6. Причина «смазанности» = ghosting (ДОКАЗАНО из исходников, v9)
Симптом пользователя: не blur/TAA, а ПОВТОРЕНИЕ/НАЛОЖЕНИЕ картинки («шлейф окон XP»),
в т.ч. LOD другого измерения поверх текущего мира; проходит по мере прорисовки новых
чанков. Причина: upstream NormalRenderPipeline чистит у своего fb только DEPTH_STENCIL
(initDepthStencil), COLOUR не чистится НИКОГДА -> старые LOD-пиксели (в т.ч. прошлого
измерения) живут в colourTex и каждый кадр ре-блитуются в Iris gbuffer (finish() ->
transformBlitDepth), пока новые draw-вызовы их не перекроют. IrisVoxyRenderPipeline
colour чистит (glClear COLOR) -> поэтому мир, загруженный первым/напрямую, чистый,
а миры на Normal-пайплайне (созданные после смены измерения / портал-миры IP) — нет.
v9 = clear colourTex — НЕ ДОСТАТОЧНО (финальный blit читает colourSSAOTex!).
Настоящий stale-буфер: colourSSAOTex — READ_WRITE image SSAO-компьюта (SSAO.computeSSAO,
glDispatchCompute по всему вьюпорту, но imageStore только где есть LOD; остальные
пиксели хранят прошлое, включая другое измерение) -> ghost-маски/«серые чанки».
v10 = v9 + glClearNamedFramebufferfv(fbSSAO, COLOR, 0) каждый кадр в setup().
Гипотезы про TAA/пайплайн-назначение ОТКЛОНЕНЫ (не повторять!).
ЕСЛИ v10 не решит: ЗАПРАШИВАТЬ latest.log ДО следующего патча (маркер, строки
pipeline, GL-ошибки) — без лога новые патчи не собирать.
