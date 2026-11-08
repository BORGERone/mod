# ХЭНДШОФФ СЛЕДУЮЩЕЙ СЕССИИ (BORGERone/mod, ветка arena/94d4d401-mod)
_Состояние на 2026-11-08. Пользователь завершил сессию со мной: «ничего не помогло, топчешься на месте».
Этот файл + AGENT-GUIDE-RULES.md + папка Drive «Arena Minecraft handoff» = полная передача контекста._

## 0. Главное правило работы
Без свежего latest.log и скриншота от пользователя новые патчи НЕ собирать (пользователь исчерпал
терпение на слепых итерациях v6–v11). Сначала диагноз по логу, потом одна хирургическая правка,
потом доставка (Drive-ссылка в том же ходу).

## 1. Окружение пользователя
MC 1.21.1, NeoForge 21.1.265, Sodium 0.8.13, Iris 1.8.14-beta.1 (шейдерпак Eclipse ВСЕГДА включён),
Voxy 0.2.15-beta (мы собираем патченные jar в CI), Immersive Portals x Sable 0.5.0, Sable 2.0.5,
Separate Sable Render Distance 1.8.7, Veil, ~200 модов (много кастомных блоков/текстур, есть битые:
create:fluid/heavy_oil_flow, createdeco BLANK_decal), Win11, Ryzen 7 5700X, RTX 5080, Java 21.0.7.

## 2. Критерии приемки (не нарушать)
- IP x Sable работают, LOD видны ВНУТРИ портал-вью (Overworld-сторона); Eclipse включён;
  без даунгрейда Sodium; без Distant Horizons; отдаём готовые jar, не инструкции;
- ответы по-русски и КОРОТКО; ссылки plain text; каждый交付 jar = Drive-ссылка в том же ходу.

## 3. Что ГОТОВО и подтверждено пользователем
- PR45-цепочка: Voxy виден под Iris (IrisVoxyRenderPipeline пишет в G-буферы напрямую).
- Стабилизация/статтер-фиксы, шим ipl-copy-fix-1.0.0 (порталы не сквозь стены) — подтверждено.
- РАБОЧАЯ БАЗА = voxy v5 (iris-ipfix-v5): Drive uc?id=1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z&export=download,
  sha256 32fa43034507b1287926ec36e856d259fbc0642e233923fe5d2b73eeeb519b9c. На ней: порталы ок,
  смаз/ghost только у «вторых» миров/после переходов.

## 4. ОТКРЫТАЯ ПРОБЛЕМА №1 (единственная, ради которой всё затевалось)
Симптом (слова пользователя): НЕ blur и НЕ TAA-шлейфы, а ПОВТОРЕНИЕ/НАЛОЖЕНИЕ картинки
(«шлейф окон WinXP»): старое изображение держится поверх, пока новые чанки его не перерисуют;
в т.ч. LOD ДРУГОГО измерения поверх текущего мира (image-5/6: незер-LOD в небе верхнего мира);
«серые/белые чанки» (image-7) — chunk-формы без текстур. Прямой вход в мир = чисто;
после смены измерения / при активных порталах = артефакты.
Последний статус: пользователь сообщил «ничего не помогло» после v11 (уточнить, какой jar стоял:
маркер в логе iris-ipfix-v5/v10/v11).

## 5. Отклонённые/опровергнутые направления (НЕ ПОВТОРЯТЬ)
- v4 skip LOD в портал-вью (отклонён пользователем), v6 deferred composite (радуга),
  v7 nether-LOD-off (хуже), v8 takeover binding IrisVoxyRenderPipelineData (маски других измерений),
  v9 clear colourTex Normal (ничего), v10 + clear colourSSAOTex Normal (ничего),
  v11 MipGen ThreadLocal (пользователь: не помогло — но проверить, тестировал ли он v11 вообще).
- Гипотеза «TAA-шлейфы» — отвергнута пользователем терминологически и не подтверждена.
- Гипотеза «пайплайн-назначение» — опровергнута логом latest-10: ОСНОВНЫЕ миры ВСЕГДА получают
  IrisVoxyRenderPipeline (создание идёт ПОСЛЕ free старого), NormalRenderPipeline достается ТОЛЬКО
  вторичным портала-мирам IP. Значит ghost в основном виде НЕ из Normal-пайплайна.

## 6. Факты из логов (папка Drive «Arena Minecraft handoff», файлы latest-*.log)
- latest-10 (v10-сессия): краш AIOOBE Index 37/length 32 в MipGen.solidify через
  ModelFactory.processTextureBakeResult на потоке «Model factory processor»; причина — static
  SCRATCH/QUEUE в MipGen при N потоках пекарни (основной + портал-миры) → v11 лечит гонку
  (ThreadLocal+catch). Серые чанки = вероятно та же порча атласа (но v11 «не помогла» — уточнить!).
- Iris логает «Reloading pipeline on dimension change: overworld => overworld» при перезагрузке
  миров IP (ClientWorldLoader) — Iris-пайплайн ПЕРЕСОЗДАЁТСЯ без смены измерения; Voxy VRS тоже
  пересоздаётся (2 раза за сессию до краша).
- «world renderer reloading cancelled during portal rendering» (IP) — перезагрузки миров
  происходят во время рендера портала.
- ipl-copy-fix активен с 23:29:25 (Copy shim active) — его копии фреймбуферов работают в момент
  пересозданий миров (подозрение №2).

## 7. Гипотезы в порядке приоритета для следующей сессии (все требуют проверки логом/экспериментом)
A) Eclipse/Iris history-буферы (TAA/accumulation colortex): Iris пересоздаёт пайплайн на
   «dimension change overworld=>overworld», history-текстуры могут переживать пересоздание и
   содержать старый мир; LOD-пиксели без motion vectors не вымываются → маски. Эксперимент:
   пользователь отключает TAA/motion blur в Eclipse (или ставит pack без history) → маски исчезают?
B) ipl-copy-fix: после пересоздания мира IP шим может копировать из старого фреймбуфера
   (кэш-ссылки на texture id) → каждый кадр штампует старую картинку. Эксперимент: снять
   ipl-copy-fix-1.0.0.jar (порталы станут сквозь стены, но временно) → маски исчезают?
C) IrisVoxyRenderPipeline: его fb.attach после пересоздания Iris-пайплайна может держать
   attachment'ы старых Iris-текстур (id переиспользуются драйвером) → рисует в мертвые текстуры
   или читает мусор. Проверка: лог+GL-маркеры, код IrisVoxyRenderPipeline.attach.
D) HiZ/occlusion-буферы Voxy (viewport.hiZBuffer) переживают пересоздание VRS → traversal
   рисует старые ноды. Проверка: VoxyRenderSystem конструктор/free.

## 8. Инфраструктура сборки и доставки (рабочая, не изобретать заново)
- CI: .github/workflows/build-voxy-neoforge.yml, job voxy = набор heredoc-патчей поверх
  j-shelfwood/voxy-neoforge@47cfa85266cb; маркер сборки в RenderPipelineFactory.
  Запуск: временно добавить `push:` в on: (workflow_dispatch недоступен токену Arena), push ветки,
  poll `gh run list`; job ipfix собирает ipl-copy-fix.
- Транзит jar CI→песочница: шаг «Stash jar as orphan-commit tag» пушит тег
  arena-artifact-<run_id> (и arena-artifact-ipfix-<run_id>); в песочнице:
  git fetch origin refs/tags/… ; git show <tag>:voxy.jar > file; сверить sha256;
  затем УДАЛИТЬ тег (gh api -X DELETE repos/BORGERone/mod/git/refs/tags/…) + git tag -d + gc.
- Drive: папка «Arena Minecraft jars» id 1RhdcdqIix9frIZCUGjNKo02yJf_Pc-1U (jar'ы),
  новая папка «Arena Minecraft handoff» (доки/логи/скрины). upload_file требует АБСОЛЮТНЫЙ путь;
  make_public(reader, allow_file_discovery=false); прямая ссылка uc?id=<ID>&export=download.
- Релизы GitHub: draft'ы voxy-pr45-… (история), публичный ipl-copy-fix-1.0.0.
- Песочница сбрасывается МЕЖДУ ХОДАМИ: в начале хода всегда
  git fetch -f origin arena/94d4d401-mod:refs/remotes/origin/arena/94d4d401-mod && git reset --hard origin/arena/94d4d401-mod.
- Крупные бинарники в конце хода удалять; git gc --prune=now после тегов; df -h контролировать.

## 9. Инвентарь Drive (jar'ы)
| файл | статус | sha256 | id |
|---|---|---|---|
| voxy-0.2.15-beta-v5-WORKING.jar | БАЗА/откат | 32fa4303…b9c | 1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z |
| ipl-copy-fix-1.0.0.jar | РАБОЧИЙ шим | ea98169c…aef | 1yPr7qGuGlVl3HAz4lMjLm9LQt7mzczOJ |
| voxy-…-v10-ssao-clear.jar | заменена | 99cf32ff…a5a | 1-0ZE6q8RSJg_3jKodYrpuwkw5N-FPxXx |
| voxy-…-v11-mipgen-race-fix.jar | кандидат (не подтверждена) | 97b7478e…783 | 1hUWaZIxffxu05YNoXRh4D-BMSmGkilfy |
| v6/v7/v8/v9 | в корзине | — | — |

## 10. Первый шаг следующей сессии
1) Прочитать этот файл + AGENT-GUIDE-RULES.md; 2) задать пользователю ОДИН вопрос: какой jar стоит
   (маркер в логе) и что именно осталось (маски? серые? краш?) + свежий latest.log и скрин;
3) поставить эксперимент A или B (см. §7) ДО любого патча; 4) только потом одна правка + доставка.
