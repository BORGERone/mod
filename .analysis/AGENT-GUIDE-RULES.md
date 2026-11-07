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
| voxy v5 (iris-ipfix-v5) | РАБОЧАЯ (база) | 32fa43034507b1287926ec36e856d259fbc0642e233923fe5d2b73eeeb519b9c | id 1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z (uc?id=1ZMU1o3sLZrBW0UDgHaRtqQ-k22wdBy-Z&export=download) | untagged-45d58ca9715927cdc0a7 |
| ipl-copy-fix 1.0.0 | РАБОЧАЯ (шим копий IP×Iris) | ea98169cd0defb9a34be16d38d2b02e893dcc50309c4ec345009fcee81085aef | (пока только GitHub; добавить на Drive при следующей сборке) | releases/tag/ipl-copy-fix-1.0.0 |
| voxy v6 (deferred composite) | ОТКЛОНЕНА (радужные LOD) | — | в корзине | — |
| voxy v7 (nether lod off) | ОТКЛОНЕНА (смаз в верхнем мире) | b8778bb286ec83612a070c2603d340313c31fdc6aa25c7bfe8fc2075f503a984 | в корзине | draft удалён |

## 6. Открытая проблема (следующий фикс)
Смазанность LOD привязана к порядку создания VoxyRenderSystem (первый мир получает
IrisVoxyRenderPipeline, остальные — NormalRenderPipeline, который под Iris даёт
двойное освещение/TAA-шлейфы). Ждём лог пользователя со строками
"Creating voxy iris render pipeline" / "Iris pipeline data already bound ..."
против таймлайна переходов по измерениям; фикс = корректное назначение/освобождение
IrisVoxyRenderPipelineData.thePipeline при смене мира (или безопасный Normal-путь).
