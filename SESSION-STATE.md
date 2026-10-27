# Settled Lands — SESSION-STATE (обновлено 27.10.2026, перед переходом в новый диалог)

ЭТОТ ФАЙЛ + AI-GUIDE-SettledLands.md = полная картина для новой сессии.
Читай оба ПЕРЕД любыми действиями. Рабочий язык — русский.

## Актуальный релиз: Build 228 «scenefix» — выдан файлами, ЖДЁТ ПЛЕЙТЕСТА
- jar: `/home/user/settledlands-0.8.0-build228-scenefix.jar`, sha256 `b992aad31478dcbd203068d159d2772151e1a8e1905c606b07e41779cb1c8148` (1 523 701 B)
- source-zip: `/home/user/SettledLands-Build228-SceneFix-Source.zip`, sha256 `7ebebfde7a489f496709fe73806abf5415e2e586a51658127147420edb154d70` (72 flat entry)
- README: `/home/user/README-Build228-SceneFix-RU.txt`, sha256 `8eed72073bc4fd8773de0d0e7ddd00d4eda02a320cf73c2c09236b7ad593b11a`
- В /home/user лежат также копии 227/226 и их zip/readme, SHA256SUMS.txt (все хэши), AI-GUIDE-SettledLands.md.
- **DRIVE-ХВОСТ**: коннектор google_drive был ВЫКЛЮЧЕН в конце диалога → 228 (jar/zip/README) НЕ залит. 227 залит и публичен: jar `1sdILFj4_zfgvbh6AK42mGso7jAqWY2Pe`, zip `1kfOI_BGUNoNuElMS5o_Vy7gOw7MUMmYg`, README `17mUPWZSA3cCueApA2Nj2uDbbfA_AcoNQ`. Папка: `1a0n2_H7RhetWwJYXF49i9NfIMExno_Kl` («Settled Lands Build 226 - Own Corpse Items», https://drive.google.com/drive/folders/1a0n2_H7RhetWwJYXF49i9NfIMExno_Kl). Когда юзер включит коннектор: initiate_resumable_upload (jar 1523701 B, zip 314986 B) + upload_file для README → curl PUT одним чанком (`X-Goog-Upload-Command: upload, finalize`, `X-Goog-Upload-Offset: 0`) → make_public ×3 → вписать id сюда.
- **ВАЖНО ПРО МОДС ЮЗЕРА**: его latest.log (https://raw.githubusercontent.com/BORGERone/mod/main/latest.log) от последней сессии показывал jar **222** в папке mods, хотя репорт был «против 227». Всегда сверяй версию мода в логе юзера ДО диагноза и напоминай ему выкладывать свежий jar.

## История багов юзера и статус
1. «Два трупа при смерти вселённого» — ИСПРАВЛЕНО в 227 (STOCK_CORPSES-запись стокового трупа, отложка при выгруженном чанке, вещи сразу на труп).
2. «Нет тумблера Реплика катсцены у LINE после CUTSCENE» — ИСПРАВЛЕНО в 227 (слияние 6 классов 172↔226: GuideDialogue, NpcDialogueGraph, NpcDialogueGraphScreen + CutsceneCamera-цепочка).
3. «Потеряна нода Цель/радиус (149–151)» — ВОССТАНОВЛЕНО в 227 (тип 15, «+ Цель», клампы 1–96/0–4, fan-out; CHECK снова «В радиусе»). varLocal (215+) сохранён. Lang union 698 ключей.
4. «После смерти вселённым (/kill) и сам-выхода из чистилища труп невидим и 60/60» — ИСПРАВЛЕНО в 228: это ТЕЛО НПС, застрявшее в сцене IvyDeathScene (возраст = tickCount − persisted t0; tickCount обнуляется при перезагрузке чанка → конверсия «71=невидимость, 72=труп+discard» не завершается никогда). Фикс: sweep `healStuckIvyBodies` (1 раз/сек, все ServerLevel): `t0 > tickCount` → рядом (±4/±3) есть GuideCorpseNpc? снять метки + `npc.discard()` (не плодим второй труп) : `t0 = tickCount − 72`. Логи: «stuck ivy body discarded» / «stuck ivy scene rebased». ЖДЁТ ПЛЕЙТЕСТА.
- Аудит-баги B2/B3/B4 (мои находки) — PROPOSE-ONLY, без списка юзера не делать.

## ПЛАН ДЛЯ НОВОГО ДИАЛОГА (что делать первым делом)
1. Прочитать этот файл и AI-GUIDE-SettledLands.md (в нём §217–228, маркеры, уроки).
2. Ждать фидбек юзера по плейтесту 228: чек-лист = один труп (1 HP, видимый, вещи в нём); нет невидимых «60/60»; реплика катсцены + выход из камеры; нода «+ Цель»/радиус; локальные переменные. Новые баги = только из его списка; база хирургии = jar228.
3. Если включён google_drive — долить 228 (см. DRIVE-ХВОСТ), вписать id сюда.
4. «скинь мод»/«показывай мод» = повторный present_file: СНАЧАЛА jar, ПОТОМ zip. Без пересборки.
5. Восстановление окружения (sandbox новый каждый диалог!):
   - `bash /home/user/settledlands/ensure_jdk.sh` (если `jdk21/bin/javac` не работает — `rm -rf jdk21` и снова);
   - classpath: `cd /home/user/settledlands/build92 && python3 prepare_classpath.py --cache /home/user/settledlands/cache/cp228 --java-home /home/user/settledlands/jdk21` — СКРИПТ ТОЛЬКО ПЕЧАТАЕТ cp в stdout (последняя строка)! Для run_tests обязательно сохранить: `… | tail -1 > /home/user/settledlands/cache/cp228/classpath.txt`.
   - surgery-cp для компиляции фиксов: `/home/user/settledlands/build92/settledlands-0.8.0-build228-scenefix.jar:` + cp из prepare (mod-jar ПЕРВЫМ, дальше baseline83 не страшен).
   - /tmp НЕ персистентен: /tmp/res228 (resources для zip), /tmp/m228cls, cfr.jar теряются. Resources брать `jar xf <актуальный.jar>` (5 файлов: 2 lang, 2 models/item, META-INF/neoforge.mods.toml). CFR 0.152 — с Maven Central, если понадобится декомпиляция.
6. Тесты: `cd /home/user/settledlands/build92/merge227/testrun`, положить тестируемый jar туда + build-report.json {jar, sha256}, `python3 tests/run_tests.py --cache /home/user/settledlands/cache/cp228 --java-home /home/user/settledlands/jdk21` → expect ALL PASS 923 (199/47/211/92/56/153/63/64/38). MergeCheck: javac+java с cp = mergecheck:jar228:minecraft-compile:neoforge:libraries{datafixerupper,guava,commons-io,log4j-api,log4j-core,slf4j-api,log4j-slf4j2-impl,brigadier,fastutil,gson} (все в cache/cp228/libraries) → 14 PASS.
7. Хирургия: `build_surgery.py --base <prev.jar> --expect-base-sha <sha> --classes <dir> --version 0.8.0-buildNNN-<tag> --output <jar>` (сам валидирует zip/toml/lang/маркеры). packaging zip: python zipfile flat-структура = README 217–NNN + SOURCE-NOTE + AI-GUIDE + patch-src/** + resources/** (шаблон — любой прошлый zip).

## Жёсткие ограничения юзера (НЕ нарушать)
- Не донимать ask_user-виджетами; самодеятельные фиксы вне списка юзера — только предлагать.
- НЕ пересобирать GuideNpc/NpcContent/GuideRenderer из patch-src (байтпатч/миксин/новый класс; GuideRenderer только миксином). GuideDialogue/NpcDialogueGraph/Screen = MERGED-исходники (компилируются), но пересборка только хирургией поверх последнего jar.
- Труп/гроб НЕ нежить (откат 205): труп HP=1, гроб full HP, оба неубиваемы; без звуков скелета; не убивать NPC в сцене смерти; leash к гробу, якорь низкий.
- Алтарь: NPC-гробы приоритетны (213); очередь чистилища нетронута; 9 изумрудов оба; сам-вышедший НЕ ресается маяком; лут ТОЛЬКО из гроба/трупа; вещи только свой конкретный труп (merge-append 225 ОТКЛОНЁН).
- Отклонено: микро-хитбокс; keyAttack-гейт; снятие land-гейта; гард поверх wands/CoffinItem/LEAD; назад-ходьба=reversal; щит +40°; crossfade 0.2s/one-shots hard-cut; HnS: dashes→dodge, hold-LMB→sword_swing_slash, еда→main_hand_*. Никаких «бонусных» фич без запроса.
- Ритуал: backup + sha256 каждого билда; present_file jar первым, zip вторым; AI-GUIDE внутри zip; toml только после tomllib-валидации (урок 223).

## Уроки (полный список в AI-GUIDE; здесь главное)
- toml без tomllib = краш (223). @Redirect owner — из constant pool вызова (224). «Бонусные» фичи — нет (225).
- Сырой CFR не компилируется (~40 артефактов/3 файла); лямбда-capture восстанавливать по javap synthetic.
- Харнесы b188 могли калиброваться под древний jar (test-report хранит sha jar — сверять!); меню 16 строк с 169+; doubles нужны Entity.getName().
- Сток-сцены, сравнивающие tickCount с персистным t0, ломаются перезагрузкой чанка (баг 228) — лечить sweep'ом или хранить возраст в NBT.
- ДИАГНОЗ НАЧИНАЙ с версии мода в логе юзера (кейс «репорт против 227 на jar 222»).
- Connector-инструменты Drive грузятся КАЖДЫЙ ход (list_connector_tools); «disabled» = юзер выключил app для разговора → просить включить, не пытаться.
- JDK21/cache/cp не персистентны между диалогами (см. план п.5); build92/.cache в снапшот НЕ попадает.

##Workspace-карта
- /home/user: SESSION-STATE.md, AI-GUIDE-SettledLands.md, SHA256SUMS.txt, jar/zip/README 226–228, SettledLands-Build226-OwnCorpse-Source.zip.
- /home/user/settledlands/build92: РАБОЧЕЕ ДЕРЕВО — patch-src (MERGED GD/Graph/Screen + PCP с фиксами 227/228), build_surgery.py, prepare_classpath.py, libs/, merge227/ (dec172/dec226/src/resources-overlay/testrun с харнесами+MergeCheck), README-227/228, jar/zip 227/228.
- /home/user/settledlands/b188/SettledLands-Build188-SprintJump/: донор-эпоха (AI-GUIDE≤162, README 92–188, jar169–172, харнесы-оригиналы, build188-source.zip-распаковка).
- /home/user/settledlands/cache/: cp228/ (minecraft-compile, neoforge, libraries/, classpath.txt после шага 5), ensure_jdk.sh уровнем выше, jdk21/ (восстанавливаемый).
- Drive: папка 1a0n2_H7RhetWwJYXF49i9NfIMExno_Kl; доноры 150–160 (скачивать ТОЛЬКО если понадобится бисект 173–215): build150-radius-gate 1C_GnOHqLEa3VTeuS00w4HqTNVNrIDFQy, 151-cutscene-replica 1Nz3Zf-vwzPA0ijhax5JhJ7-aWq0tQzSl, 152 1JZTPfmpSp7wtN3j7X0H5vdOusMCYkQTY, 153 1n1EoyS5xTEMgPJ49Nv3Trpnsxyx7sl2w, 154 1WBeLsHc6LlB7TeCc_sPL5HyPigaNRzrt, 156 1cxda4fJpo8hHFiBRm5Q08kOolynh3bPU, 157 14IdZvuZkbiv4-QtftAVlnj6NW1rhZzqQ, 158 1f4bG_AbtO8tnGbIWVnF6G84kCNouV_lz, 159 19ffICAbzm5GRkUsxyXiaZOGkDqWWjtML, 160 1opIKesV1GZjd0_ndqteq_m6AkyPYaxr5; b188-source.zip 1Pe-ikvuC-VbHluDilGJe01D6k4MRXEYj.

## ПОПОЛНЕНИЕ 27.10.2026 №2 (после «залил 228»)
Приехал только **`SettledLands-Build228-SceneFix-Source.zip`** — sha256 `7ebebfde7a489f496709fe73806abf5415e2e586a51658127147420edb154d70`
(совпадает с эталоном 228), 72 flat entry, распакован в `/home/user/work/b228`. Внутри проверено:
`resources/META-INF/neoforge.mods.toml` → `version="0.8.0-build228-scenefix"`, lang 698+698;
`patch-src/dev/settledlands/npc/PlayerCorpsePossession.java` (29 710 B) содержит `healStuckIvyBodies` + вызов в `onServerTick`;
`AI-GUIDE-SettledLands.md` (97 553 B, эпоха ≤228), README 217–228, SOURCE-NOTE.
Сверка ресурсов zip228 ↔ jar227: lang en/ru, coord_stick, cutscene_wand — **идентичны**; отличается ТОЛЬКО toml
(версия 227→228 и описание). ⇒ дельта 227→228 = toml + класс(ы) PlayerCorpsePossession (фикс sweep). Больше ничего.
**JAR 228 ПО-ПРЕЖНЕМУ НЕ ЗАЛИТ** (в main: 168, 227-restore, Source-zip228, Workspace-b132; других jar нет).
⇒ Для выдачи/базы нужен именно `settledlands-0.8.0-build228-scenefix.jar` (1 523 701 B, sha `b992aad3…c148`).
Восстановить его в этом sandbox НЕЛЬЗЯ: нет `jdk.compiler` (jdk4py = JRE: только java, jcmd, jfr…; `java --list-modules` — без compiler),
нет classpath Minecraft 1.21.1 / NeoForge 21.1.253 (Maven/Mojang заблокированы), а без них `PlayerCorpsePossession.java`
не скомпилировать. Компиляция возможна только в сессии с доступом к Maven.

## §0 ГЛАВНОЕ ПРАВИЛО НОВОЙ СЕССИИ: СНАЧАЛА EGRESS, ПОТОМ ПЛАНЫ
Первым действием: `bash tools/check_env.sh` (в репозитории). Он печатает вердикт COMPILE-CAPABLE или JAR-ONLY.
Формула воспроизводимости из исходников = **cpmin (classpath, ~60 МБ) + компилятор (egress на ~11 c)**.
Компилятор персистентно не живёт (JDK ~190 МБ выбивает снапшот), cpmin — живёт, если workspace <= ~97 МБ.
Если egress закрыт — сборки НЕТ ни в каком диалоге; не тратить ходы, рабочий артефакт = готовый jar.

### Замер этого инстанса (27.10.2026, ветка arena/829e0ad0-mod) — JAR-ONLY
- api.adoptium.net — BLOCKED (000), piston-meta.mojang.com — BLOCKED, repo1/repo.maven.apache.org — BLOCKED,
  maven.neoforged.net — BLOCKED, maven.minecraftforge.net / libraries.minecraft.net — BLOCKED,
  raw.githubusercontent.com — BLOCKED. Открыты только: github.com / api / codeload, pypi.org, registry.npmjs.org.
- Компилятора нет и взять неоткуда: `jdk4py` на PyPI = **JRE** (21.0.8.x и 25.0.2.x, 30–35 МБ; `--list-modules` без
  `jdk.compiler`, source-file mode падает с «Module jdk.compiler not in boot Layer»); пакетов с ecj/javac на PyPI нет
  (`ecj`, `eclipse-jdt-core`, `java-compiler`, `javac` — 404; `pyjdk` — пустышка 0.0 МБ, не брать). В npm тоже нет.
  Единственная польза от jdk4py — `java` для инспекции: `/home/user/tools/jdkpkg/jdk4py/java-runtime/bin/java`.
- Снапшот workspace в этот инстанс НЕ приехал: нет `settledlands/` вообще (ни build92, ни cache/cpmin, ни jdk21),
  в /home/user только клон репозитория. => cpmin из прошлого диалога надо создавать заново при открытом egress.
- Наличие 228: в `main` приехал только Source-zip (sha 7ebebfde…4d70, идентичен эталону) — **jar 228 отсутствует**,
  поэтому даже JAR-ONLY путь упирается в юзера: нужен `settledlands-0.8.0-build228-scenefix.jar` (1 523 701 B,
  sha b992aad3…c148) из его папки mods, либо с Drive.

## Ключевые sha (история)
226 `b4a762c8…4633`; 227 jar `6b722021…de3c` / zip `03416d32…d7e4` / README `5432d14e…dbdf`; 228 jar `b992aad3…c148` / zip `7ebebfde…4d70` / README `8eed7207…b11a`; baseline83 `dcbdb263…`; donor b168 `e5ca762b…05e9`; jar172 `fec4b01f…6fcf4f`.

## Старт нового диалога (фраза юзеру для копипасты)
«Продолжаем Settled Lands. Читай в sandbox /home/user/SESSION-STATE.md и /home/user/AI-GUIDE-SettledLands.md, действуй по плану из SESSION-STATE. Актуальный билд 228, жду/даю фидбек по плейтесту.»

## РЕЗЕТ SNAPSHOT 27.10.2026 (записано агентом, ветка arena/829e0ad0-mod)
- Новый sandbox оказался ПУСТЫМ: нет /home/user/settledlands (build92, patch-src, merge227, jar/zip 226–228),
  нет AI-GUIDE-SettledLands.md, SHA256SUMS.txt, README-227/228, нет jdk21 и cache/cp228.
  В репозитории BORGERone/mod лежат ТОЛЬКО: latest.log (от 26.10, jar **build222**), settledlands-0.8.0-build168-eyefix.jar,
  2 bbmodel и html. => артефакты прошлой сессии не пережили смену sandbox; их надо ЗАНОВО загрузить в чат.
- Ограничения сети этого sandbox: доступны github.com/api.github.com/registry.npmjs.org/pypi.org/files.pythonhosted.org;
  НЕДОСТУПНЫ repo1.maven.org, maven.neoforged.net, piston-meta.mojang.com, raw/objects.githubusercontent.com.
  => JDK можно поставить только через `pip install jdk4py` (PyPI), а classpath Minecraft/NeoForge восстановить НЕЛЬЗЯ,
  поэтому компиляция/тесты (923 PASS + MergeCheck) в этой сессии невозможны без внешнего доступа к Maven.
- Чтобы не терять контекст снова: этот файл теперь лежит в git-репозитории (ветка сессии).
  Рекомендуется также хранить в репозитории (или в Drive) jar/zip последнего билда и AI-GUIDE, т.к. /home/user вне .git не персистентен.
- latest.log в репозитории — 26.10 22:25–22:29, мод **0.8.0-build222-corpsevisible**, строка
  «stock corpse missing - spawned our own for BORGER (possessed=true)» = поведение ДО фикса 227.
  Это не фидбек по 228 — для диагностики нужен свежий лог с jar 228.

## ПОПОЛНЕНИЕ 27.10.2026 (после заливки юзером 2 файлов в main)
В `main` (коммит b935c29) юзер залил:
- `settledlands-0.8.0-build227-restore.jar` — это **настоящий build 227**, sha256 `6b7220216d54ba8a1197fc47ffeb3ba5e7fb4b350c3706571b967332f876de3c`
  (совпадает с историей), 1 522 990 B, toml-версия `0.8.0-build227-restore`, 352 записи / 306 классов,
  lang 698+698, `healStuckIvyBodies` ОТСУТСТВУЕТ ⇒ это база ДО фикса 228.
- `SettledLands-Workspace-Backup-2026-10-21-build132.zip` — старый бэкап рабочего дерева эпохи **build 132**
  (21.10.2026): patch-src 50 файлов (GD/Graph/Screen ещё НЕ слитые с 226), tests/ (харнесы + run_tests.py),
  prepare_classpath.py, build_patch.py, package_source.py, AI-GUIDE v1.2 (≤132), resources b132, jar132.
ЧЕГО ПО-ПРЕЖНЕМУ НЕТ: jar 228 (sha b992aad3…c148, 1 523 701 B), SettledLands-Build228-SceneFix-Source.zip
(7ebebfde…4d70, 314 986 B, 72 flat entry), build_surgery.py, merge227/ (dec172/dec226/MergeCheck), README-227/228.
⇒ Найти/залить 228. Альтернатива: включить google_drive и взять с Drive 227-source-zip (`1kfOI_BGUNoNuElMS5o_Vy7gOw7MUMmYg`)
— в нём patch-src уже эпохи 227 (слитые GD/Graph/Screen + PCP с фиксами 227) и AI-GUIDE ≤227; фикс 228 придётся переписать заново.
Ограничение среды (сеть): Maven/Mojang/NeoForge НЕ доступны, javac НЕТ (только JRE 25 из `pip install jdk4py`
в /home/user/tools/jdkpkg) ⇒ компиляция и прогон 923 тестов в этой сессии невозможны; правки только байткодом/патчем jar.
