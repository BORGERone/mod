# ПЕРЕДАЧА СМЕНЫ: SettledLands 0.8.0

## Актуальное состояние: build 240 — читать раньше истории ниже

Пользователь попросил вселяться **только в NPC с его собственной локальной целочисленной переменной `культ` ≥ 1**. Без неё, при 0 или отрицательном значении — отказ. Личные/общие очки игрока не должны разрешать вселение или записывать переменную NPC. Другое поведение, обычную смерть и алтарь не менять. Пользователь подтвердил в игре 237; 238–240 в игре ещё не подтверждены.

Кандидат `settledlands-0.8.0-build240-npc-local-cult.jar`: **1 544 935 байт**, SHA-256 `2e4a29f6f147d6d081234ea5b7d3839b9a5ba297e984b3962b6f3113e47d76b5`. Сделать копию мира, установить только этот jar, ZIP в `mods` не класть.

В 239 серверный `PossessionSystem.onEntityInteractSpecific` уже проверял `cult(npc) >= 1`, но более ранний `PossessionCultBridge.onInteractSpecific` с приоритетом HIGHEST вызывал `mirror`: положительные личные/общие очки игрока записывали `культ=1` в выбранного NPC и тем самым обходили проверку. `tools/patch_npc_local_cult240.py` воспроизводимо заменяет **только исполняемое тело** обработчика моста на `nop` + `return`, той же длины; аннотации, остальные методы, старый настоящий gate и сценарный writer не меняются. Дифф относительно 239: класс моста, ru/en текст отказа, TOML, patch-info — ровно пять записей. Локальный `культ` регистрозависим и читается из persistent NBT `settledlands_local_vars` именно выбранного NPC. Остальные ограничения вселения сохранены. Возврат владельца к своему телу у алтаря не менялся.

**Старые сохранения:** значения, которые мост ранее записал NPC, остаются — байткод не различает их и значения, выставленные специально. Автоматического удаления/миграции нет. Для проверки запрета брать NPC, у которого локального `культ` точно нет/он 0; личные/общие очки игрока могут быть положительными.

Полный `tests/gate_build240.py` (см. §8) прошёл: вложенные шлюзы 239/238/237, негативные калибровки валидатора и JVM, точный diff, воспроизводимость, Java 21 и реальные байткоды моста, gate, переноса инвентаря и graph-variable writer с исполняемыми synthetic-зависимостями. Build 239 подтверждает старый обход; мутанты восстановленного моста, постоянно разрешающего `cult` и порога `>1` обнаружены. Проверены два NPC, 0/отрицательное/1/больше, отсутствие, точный регистр, строковое значение, очки игрока и исключения инструментов/трупов; ниже написан план игрового прогона. **Это не Minecraft/NeoForge: игра не запускалась.**

Backup `SettledLands-Build240-NpcLocalCult-Source.zip` содержит AI-GUIDE, инструменты/тесты и точные jar-входы 230/232–239. 230/233/234 заведомо сломаны и предназначены только для негативных проверок.

## Историческое состояние: build 239

Пользователь дополнительно попросил: после полного получения вещей из собственного трупа **или гроба** сущность исчезает. При любом остатке, ошибке разбора и недопуске остаётся. Обычную смерть не менять. Пользователь подтвердил в игре 237, но **не подтвердил проверку 238 или 239**.

Кандидат `settledlands-0.8.0-build239-empty-corpse-cleanup.jar`: **1 544 746 байт**, SHA-256 `b68fd79357da507fff35939964816c2a301101ac3afb31298bf4ea31ab7a4290`. Перед испытанием сохранить копию мира, оставить только этот jar в `mods` (ZIP туда не класть).

`tools/patch_corpse_cleanup239.py` воспроизводимо патчит точную базу 238. В `CorpseRecovery.tryClaim` только после выдачи всех вещей из изначально непустого списка и удаления ключа `settledlands_corpse_items`: `corpse.dropLeash(true,true)` (прежняя ванильная механика возвращает прикреплённый поводок) и `corpse.discard()`. Пустое тело без вещей не удаляется. При неполной выдаче труп и привязанный поводок остаются. Нет нового tick/per-frame кода; обычная смерть, алтарь, render, здоровье и события 238 не изменены. Ровно три записи изменены относительно 238: `CorpseRecovery.class`, TOML и patch-info; прочие методы helper и все остальные классы побайтно сохранены.

Полный `tests/gate_build239.py` проходит: включает весь gate 238/237 и §8, точный diff и воспроизводимость, реальные байткоды на Java 21, 238 как негативный контроль, проверки полного/частичного/пустого инвентаря, невалидного CODEC, повторных событий, владельца/очереди/измерения/мёртвого игрока, двух тел, имитации загрузки и поводка. Мутанты отсутствия удаления/возврата поводка, преждевременного удаления, обхода UUID/очереди детектируются. Фактический Minecraft/NeoForge не запущен; стенды не равны игре.

Backup 239 содержит AI-GUIDE, документацию, инструменты, тесты, jar 239 и входные jar 230/232–238 в `test-inputs/`. 230/233/234 заведомо сломаны и нужны только для негативных тестов. Команда повторного шлюза из backup — в §8 ниже. При игровом сбое нужны свежий `latest.log` этого запуска, время, кто нажал, состояние инвентаря/трупа и наличие поводка.

## Историческое состояние: build 238

Пользователь подтвердил: **build 237 прекрасно работает в игре**. Затем попросил возврат вещей из своего трупа по ПКМ после самостоятельного выхода из чистилища и здоровье трупа 1 HP, гроба 60 HP. Уточнение через `ask_user`: новая выдача только для смерти **во вселённом теле**, обычные смерти не менять.

Кандидат `settledlands-0.8.0-build238-corpse-recovery.jar`: 1 544 550 байт, SHA-256 `632a64b9a94c31165f351152e260ece774c62870570a4ac6101c06ca634a84c2`. В игре/NeoForge он **ещё не проверен**. Перед игровыми испытаниями сохранить копию мира, убрать другие jar SettledLands из `mods`; backup ZIP в `mods` не класть.

Кодовый путь (байткод, без javac): `IvyDeathScene` вызывает новый `CorpseRecovery.tryClaim` перед существующей отменой обычных `EntityInteract` и `EntityInteractSpecific`. Предмет гроба, поводок и инструменты редактирования идут по прежнему пути. Допуск: живой `ServerPlayer`, UUID совпадает с `GuideCorpseNpc.ownerUuid`, игрок не в `PurgatoryData.deadQueue` и не в измерении чистилища. Содержимое считывается из `settledlands_corpse_items` только выбранного трупа; обычный дроп и код алтаря не изменены.

Инвентарь наполняется по слотам ограниченными порциями. После каждой порции изменяется только исходный NBT `count`; при заполнении остаток остаётся в трупе, при повторном ПКМ дубликатов нет. Это необходимо и в creative: ванильный `Inventory.add(stack)` может обнулить непоместившийся остаток. `ItemStack.CODEC` + `DataResult.result()` не принимает частичный разбор, а исключения парсинга не удаляют NBT. Серверное `syncHealth` при конструкторе и `setCoffin` (включая загрузку) задаёт максимум/текущее здоровье 1/1 или 60/60. `hurt`, исключение `/kill`, поводок, алтарь, нежить, рендер 237 и дроп обычной смерти не менялись.

Шлюз `tests/gate_build238.py` полностью прошёл: полный шлюз 237 и его калибровки, точный diff с 237 (+1 класс, 6 изменённых записей), TOML/языки/mixins/CRC, Java 21, 5 поведенческих fixture, негативные мутанты UUID и очереди. JVM-fixture действительно исполняет байткод мода, но использует стенды Minecraft/NeoForge/DFU и **не является игровым тестом**. Проверить в игре возврат из тела, чужого владельца, полный/частичный инвентарь и повторный ПКМ, сохранение/загрузку, 1/1 и 60/60, гроб/поводок, обычную смерть и алтарь. При проблеме запросить свежий `latest.log`, состояние вещей и время.

Инструмент сборки — `tools/patch_corpse_reclaim238.py`. Backup 238 содержит оба jar, входы 230/232–236 в `test-inputs/`, инструменты/тесты, инструкции и AI-GUIDE. Билды 230/233/234 заведомо сломаны: только негативные тесты, не устанавливать. Работать только в закреплённой сессионной ветке `arena/24676dbe-mod`.

## Историческое состояние после сборки build 237

Пользователь проверил build 236: труп всё ещё не виден. Более новый приложенный лог (1 285 346 байт, SHA-256 `f6c87a4cf8668a0ba5697111356ea99d29cf35378ad2d0e12839f17f0b431062`) подтверждает загрузку 236; старый `origin/main:latest.log` ниже про битую загрузку — **не этот запуск**. В свежем логе probe показывает негроб с пустой анимацией, а также `ivy_oleander.bbmodel → BbModel` и пустую текстуру, но не пишет ID. Значения могли относиться к разным сущностям; независимых маркеров смерти нет. Не утверждать, что найдена причина именно искомого трупа.

По байткоду подтверждён дефект сохранённых трупов: `readAdditionalSaveData` 236 не восстанавливает анимацию; `GuideCorpseFreezeMixin` отменяет рендер негроба без имени, содержащего `die`. Build 237 восстанавливает `die` при загрузке только если анимация `null`/пустая и это не гроб, а при стандартном спавне задаёт её до добавления сущности. Непустые анимации не переписываются. Дополнительно переделан неверный фрагмент 236 в `dressBodyCorpse`: старая вставка обходилась на успехе и на исключении могла вызвать `IllegalAccessError`; вместо неё текстура готовится через публичный профиль, логируется один раз при успешной подготовке. Сохранены здоровье/неуязвимость, старая логика имён и гробов. Клиентский `CorpseRenderProbeMixin` выключен в конфигурации, поскольку его глобальные значения смешивают сущности и спамят на кадрах.

**База 237:** `settledlands-0.8.0-build237-corpse-pose.jar`, 1 541 606 B, SHA-256 `9abf34da8a566d882e4bb7a4561a93e019aa4c5a58461ec1e54fc51039e58c45`. Структурный и полный JVM-шлюз §8 прошли. Настоящие байткоды чтения, спавна и подготовки трупа исполнены на JVM 21 со стендами и отрицательным контролем 236; дополнительная fixture исполняет настоящие `GuideNpc.applyProfile/setProfile`. После выпуска **пользователь подтвердил работу 237 в игре**. Minecraft/NeoForge в песочнице не запускались. При пустом имени модели и при непустой анимации без `die` исправление 237 имя не угадывает.

В текущей сессии работать и пушить только в закреплённую ветку `arena/24676dbe-mod`. Указания ниже про историческую `arena/829e0ad0-mod` не дают права переключаться на неё; в будущей сессии приоритет у системного имени ветки. Входные jar для полного воспроизведения лежат в backup ZIP в `test-inputs/`; 230/233/234 заведомо сломаны и не должны устанавливаться.

Ниже сохранена историческая передача 236; при противоречии приоритет у актуального состояния выше, §8 и `SESSION-STATE.md`.

Файл для новой сессии. Читать целиком до первой сборки. Всё, что ниже, проверено
по байткоду/логам, а не «по памяти». Дата: 27.10.2026.

---

## 0. Задача в одну строку

Труп игрока (`dev.settledlands.npc.GuideCorpseNpc`) **не рисуется моделью** —
либо ничего не видно («прозрачный»), либо встроенная заглушка. Живые NPC при этом
рисуются нормально. Нужно найти, почему у трупа не разрешается имя модели, и
починить. Собирать jar агент обязан сам (компилятора нет — только правка байткода
и новые классы).

Исторический кандидат был build 236 (см. §4): пользователь проверил его в игре, проблема осталась. Позднее пользователь подтвердил, что build 237 работает. Текущий кандидат 240 и новые задачи описаны в начале.

---

## 1. Первые действия новой сессии (5 минут, ничего не собирать)

```bash
cd /home/user/mod
git fetch origin && git log --oneline -3 origin/main -- latest.log
git checkout origin/main -- latest.log && wc -l latest.log
```
Сравнивать дату, версию и SHA файла с приложенными отдельно логами. Отсутствие нового файла в `main` **не доказывает**, что игру не запускали: после 236 пользователь приложил свежий лог отдельно. После проверки 237 нужен именно лог этого нового запуска.

Дальше — только чтение, без сборки:
```bash
python3 tools/disasm.py settledlands-0.8.0-build236-corpse-tex.jar \
    --class dev/settledlands/npc/GuideNpc.class --method applyProfile
python3 tools/disasm.py settledlands-0.8.0-build236-corpse-tex.jar \
    --class dev/settledlands/npc/GuideCorpseNpc.class
```

---

## 2. Как устроен путь рендера (проверено по байткоду)

```
GuideRenderer.renderCorpse(...)                 (клиент)
  ├─ GuideCorpseFreezeMixin (@Inject HEAD)      dev/settledlands/mixin/GuideCorpseFreezeMixin
  │    if (!anim.contains("die")) → ci.cancel()          // живой рендер не наш случай
  │    m = BbModelManager.getModel(npc.texturePath())
  │    if (m == null) → ci.cancel()  → НЕ РИСУЕТСЯ НИЧЕГО  ← «прозрачный труп»
  │    else → ci.cancel(); m.render(...)
  └─ иначе ванильный MobRenderer с GuideModel + guide_npc.png  ← «заглушка»
```

`GuideNpc.texturePath()` — 14 байт: `entityData.get(DATA_TEXTURE)` (никогда не
переопределён в `GuideCorpseNpc`).

`BbModelManager.getModel(String name)`:
* `null`, пустая/пробельная строка, `"default"` (без учёта регистра) → `null`;
* иначе `BbModelStorage.findModelFile(name)`; если файла нет → `null`;
* исключение парсера → печать в `System.err` и `null`.

`BbModelStorage.findModelFile(name)`:
* `modelsDir.resolve(name)` и `isRegularFile` → файл;
* иначе, если имя не оканчивается на `.bbmodel` → `modelsDir.resolve(name + ".bbmodel")`;
* иначе `null`.
`modelsDir` = `FMLPaths.GAMEDIR / "settledlands/models"` (у юзера:
`.minecraft/settledlands/models/`, там лежат `ivy_oleander.bbmodel`, `red_coffin.bbmodel`).
Путь **не зависит от регистра только на Windows-ФС**, сам код регистро-зависим.

`DATA_TEXTURE` (тип `EntityDataAccessor`, синхронизируемое поле) выставляется:
1. в `GuideNpc.applyProfile()` — **последними** инструкциями (offset 183..209 из 236);
2. в `GuideNpc.setProfile()` — косвенно, он вызывает `applyProfile()` в конце,
   но `putfield profile` происходит **после** `mobilitySettingsChanged()`/`resetMobility()`.

Вызовы у трупа:
```java
// PlayerCorpsePossession.dressBodyCorpse(npc, pending)
try { npc.setYRot(...); npc.setYBodyRot(...); npc.setYHeadRot(...); } catch (Throwable ignored) {}
try { if (!pending.model().isEmpty()) { prof = new NpcProfile(); ... npc.setProfile(prof); npc.applyProfile(); } } catch (Throwable ignored) {}
try { имя } catch (Throwable ignored) {}
try { playCustomAnimation("die", false) } catch (Throwable ignored) {}
try { setHealth(1f); setInvulnerable(true) } catch (Throwable ignored) {}
```
Важно: `setProfile()` уже вызывает `applyProfile()`, а строка `npc.applyProfile()`
идёт второй раз и работает с **тем** профилем, который успел записаться в поле.

Имя модели у трупа берётся при смерти игрока:
```
LivingDeathEvent → PlayerCorpsePossession.onPlayerDeath
    model = PossessionSystem.isPossessed(p) ? safe(PossessionSystem.possessModelKey(p)) : ""
    → DeadWatch(model, ...)
    → onServerTick → спавн GuideCorpseNpc → dressBodyCorpse(new PendingBody(model, name, ...))
```
а `possessModelKey(p)` — это NBT игрока `settledlands_possess_model`, куда при
вселении писалось `npc.texturePath()` (`PossessionSystem.possess`).

---

## 3. Что уже сделано и чем это закончилось

| билд | что делал | итог |
|---|---|---|
| 229 | sable-AABB | рабочая база |
| 230 | вставка 14 байт в начало `applyProfile` | ❌ AIOOBE при загрузке миксинов (вставка была неверной: `aload_1` до `astore_1`, сломанное смещение `Code`) |
| 231 | новый миксин `EarlyCorpseTextureMixin` | ❌ падение на скане (`@Mixin.value` одиночным `c`) |
| 232 | тот же миксин, аннотации массивами | ⚠️ грузится, но модели нет. Маркер `corpse texture pre-set` в логе был **неверной трактовкой** |
| 233 | зонд-класс `TexProbe` + `CorpseRenderProbeMixin` | ❌ скан: `SourceFile` добавлен в пул после `dump()` |
| 234 | те же зонды, исправлен пул | ❌ `VerifyError: Illegal target of jump or branch` (смещение считалось от операнда) |
| 235 | то же + исправлены смещения + JVM-самотест | ✅ грузится (это уже не проверялось в игре отдельно) |
| 236 | `DATA_TEXTURE` в начале `applyProfile`; попытка подготовки/лога в `dressBodyCorpse` | загружен в свежем логе, труп всё ещё невидим; вставка в `dressBodyCorpse` ошибочна |
| 237 | восстановление `die`, безопасная подготовка через профиль, выключение спамящего probe | пользователь подтвердил, что в игре работает |
| 238 | возврат вещей владельцу после выхода из чистилища; 1/1 и 60/60 | JVM-шлюз пройден, игровая проверка ещё нужна |
| 239 | удаление трупа/гроба после полной выдачи и возврат прикреплённого поводка | JVM-шлюз пройден, игровая проверка ещё нужна |

---

## 4. Билды, их sha256 и статус

Старые билды доступны в исторической ветке `arena/829e0ad0-mod`; необходимые входы также упакованы в backup 237. В текущей сессии не переключаться на историческую ветку.

| файл | байт | sha256 | статус |
|---|---|---|---|
| `settledlands-0.8.0-build229-sablesweep.jar` | — | — | рабочая база (до фиксов) |
| `settledlands-0.8.0-build230-modelfix.jar` | 1536982 | `b41dc40574e3c2d8…` | отозван |
| `settledlands-0.8.0-build231-earlytex.jar` | 1538081 | `c1ca5ebf9d642bbd…` | отозван |
| `settledlands-0.8.0-build232-earlytex2.jar` | 1538344 | `a75d8a8f724f5e74…` | рабочая база для диффов, модели нет |
| `settledlands-0.8.0-build233-probe.jar` | 1540690 | `0592d61c4ffe5888…` | отозван (регресс-кейс валидатора) |
| `settledlands-0.8.0-build234-probe.jar` | 1540699 | `f83b8c69178176ec…` | отозван (регресс-кейс JVM-верификатора) |
| `settledlands-0.8.0-build235-probe.jar` | 1540699 | `cfddca97baf6a98b…` | зонд, грузится |
| `settledlands-0.8.0-build236-corpse-tex.jar` | 1541160 | `4d640528b565e788…` | загружен в игре, проблема осталась |
| `settledlands-0.8.0-build237-corpse-pose.jar` | 1541606 | `9abf34da8a566d88…` | пользователь подтвердил в игре |
| `settledlands-0.8.0-build238-corpse-recovery.jar` | 1544550 | `632a64b9a94c3116…` | предшествующий кандидат; игра не подтверждена |
| **`settledlands-0.8.0-build239-empty-corpse-cleanup.jar`** | **1544746** | **`b68fd79357da507f…`** | **текущий кандидат: полный JVM-шлюз пройден, игровая проверка ожидается** |

Полные хеши — в `git log` / `SESSION-STATE.md`.

---

## 5. Исторический старый лог (origin/main `10b423f`, 1170 строк) — НЕ свежий лог после 236

```
[Render thread/FATAL] Error during pre-loading phase:
  File mods\settledlands-0.8.0-build236-corpse-tex.jar is not a jar file
Caused by: java.util.zip.ZipException: zip END header not found
...
java.lang.ExceptionInInitializerError
Caused by: java.lang.IllegalStateException: zetafix: could not resolve mod container for quark
```
Это **недокачанный jar** (обрезанный файл), мод вообще не загрузился, остальное —
каскад. Сам артефакт целый: 355 записей, EOCD на 1 541 138, `zipfile.testzip()` → `None`.

Для текущего билда инструкция — `CHECK-JAR-237-RU.txt` (PowerShell `Invoke-WebRequest -OutFile` и сверка размера/хеша). GitHub Contents API не возвращает base64 для файлов >1 MiB, поэтому не использовать его как резерв скачивания.
**Правило: при «вылете» сначала сверять размер/хеш скачанного файла, потом код.**

---

## 6. Маркеры, которые надо искать в НОВОМ логе

С build 237 клиентский `CorpseRenderProbeMixin` отключён, поэтому строки `client.corpse.*` ожидаемо отсутствуют. Серверный `[corpse]` пишет один раз на успешном пути подготовки possessed-тела, но не содержит ID и не заменяет подтверждение создания конкретной сущности. Не выводить связь между значениями разных сущностей без адресной диагностики.

```
[SettledLands][corpse] model=<...> tex=<...>          ← 236, сервер: что записали трупу
[SettledLands][probe] client.corpse.coffin
[SettledLands][probe] client.corpse.anim
[SettledLands][probe] client.corpse.tex               ← что видит клиент
[SettledLands][probe] client.corpse.model             ← разрешилась ли модель
[SettledLands][probe] client.modelsDir
[SettledLands][probe] client.models                    ← какие файлы клиент видит
[SettledLands] player death at (...) possessed=... — corpse watch scheduled   ← уже было в 229
```
Исторический `TexProbe` дедуплицирует каждую метку глобально, не по ID: чередование сущностей всё равно вызывает per-frame спам. В 237 его клиентский вызов отключён.

---

## 7. Дерево решений по новому логу

### A. `model=` пустое (сервер не получил имя модели)
Значит `possessModelKey(player)` пуст → при вселении `npc.texturePath()` был `""` либо
`setPossessed` не выполнился. Смотреть `PossessionSystem.possess` (184 Б) — он пишет
`settledlands_possess_model = npc.texturePath()` перед `npc.discard()`.
Возможная диагностика в будущем билде (не считать доказанным исправлением 237): в `possess` логировать `texturePath()` и `profile.texture`,
при пустом `texturePath()` брать `this.profile.texture` (поле
`dev/settledlands/npc/NpcProfile.texture`, `getfield`) и писать через
`Objects.toString(x, "")`. Инструменты: `tools/classedit.py` (`insert`), `tools/smt.py`.

### B. `model=` есть, `client.corpse.tex` пустая на клиенте
Синхронизация `SynchedEntityData`: значение записали после `addFreshEntity`, и dirty-бит
не ушёл / записали до добавления. Фикс: писать `DATA_TEXTURE` **до** спавна в
`DeadWatch`-пути (там есть готовый `model`), либо в `GuideCorpseNpc.tick()` на сервере
(дешёвая самопроверка «поле пустое, а профиль есть → записать»).

### C. `client.corpse.tex` есть, `client.corpse.model` = `null`
Не находится файл модели. Сверить `client.corpse.tex` с `client.models`:
* имя с расширением/без (`.bbmodel` добавляется автоматически, если его нет);
* регистр (`Ivy_Oleander.bbmodel` vs `ivy_oleander.bbmodel`) — код регистро-зависим;
* имя с каталогом/пробелами;
* ошибку парсера искать в `System.err` этого же лога (там печатается имя файла и стек).
Фикс — правка `BbModelStorage.findModelFile` или `BbModelManager.getModel`
(небольшие методы, вставка в `Code` через `tools/classedit.py`).

### D. Всё непустое и `client.corpse.model` не null, но труп не виден
Тогда проблема в самом `BbModel.render(...)` (поза/анимация `die`/масштаб) —
начинать с `CorpseRenderProbeMixin`: добавить в лог аргументы `render` (свет, overlay,
частичный тик) и результат `BbAnimation`.

---

## 8. Инструменты и обязательный шлюз перед раздачей

- `tools/classedit.py`, `tools/smt.py`: точечные вставки с пересчётом переходов, таблиц и фреймов; не править байткод вручную.
- `tools/patch_corpse_pose237.py`: воспроизводимый патч с контролем SHA входов 235/236 и точного диффа.
- `tools/validate_jar.py`: структура каждого класса, TOML, mixin JSON, пулы, инструкции, атрибуты и границы переходов.
- `tools/jvm_verify.py`: настоящая JVM, реальные классы, синтетические зависимости; цель с аргументами задаётся полностью. JVM должна выдать `DECLARED` для каждой цели; ошибку до этого нельзя считать успехом.
- `tools/selftest_probe.py`, `tools/check_mixin_class.py`, `tools/verify_jar.py`: отдельные JVM/аннотации/zip/lang/mixins/TOML и строгий allowlist диффа.
- `tests/jvm_corpse_load.py`, `jvm_corpse_spawn.py`, `jvm_corpse_dress.py`, `jvm_corpse_realprofile.py`: реальные байткоды и исполняемые заглушки, отрицательный контроль build 236, границы поведения. Последний тест запускает реальные `GuideNpc.applyProfile/setProfile`; при нормальном пути дополнительный вызов повышает число пересчётов размеров с двух до трёх — это не игровой тест.

### Полный повторяемый шлюз build 240

Из распакованного backup 240 (Python 3.11+ и Java 21; `javac` не нужен). При отсутствии JVM можно установить её вне репозитория: `python3 -m pip install --no-cache-dir --target /tmp/jdkpkg 'jdk4py==21.0.8.2'`.

```bash
python3 tests/gate_build240.py --inputs test-inputs \
  --base237 test-inputs/settledlands-0.8.0-build237-corpse-pose.jar \
  --base238 test-inputs/settledlands-0.8.0-build238-corpse-recovery.jar \
  --base239 test-inputs/settledlands-0.8.0-build239-empty-corpse-cleanup.jar \
  --java /path/to/java21 --jar settledlands-0.8.0-build240-npc-local-cult.jar
```

Ожидается `BUILD 240 GATE: PASS`. Включает gate 239/238/237 и все калибровки §8. `tools/patch_npc_local_cult240.py` принимает только точный SHA базы 239; `tools/make_backup240.py` собирает воспроизводимый ZIP с проверочным манифестом. Проверяются реальное исполнение байткода и три негативных мутанта, но вместо игровых API — стенды. После изменения `tools/jvm_verify.py` обязательно перепроверять положительные и отрицательные калибровки, уже включённые в gate.

### Исторический полный повторяемый шлюз build 239

Из распакованного backup 239 (нужны Python 3.11+ и Java 21; без javac):

```bash
python3 tests/gate_build239.py --inputs test-inputs \
  --base237 test-inputs/settledlands-0.8.0-build237-corpse-pose.jar \
  --base238 test-inputs/settledlands-0.8.0-build238-corpse-recovery.jar \
  --java /path/to/java21 --jar settledlands-0.8.0-build239-empty-corpse-cleanup.jar
```

Ожидается `BUILD 239 GATE: PASS`. Включены оба исторических шлюза и реальные JVM-тесты на синтетических API; отдельно нужен игровой тест. `tools/patch_corpse_cleanup239.py` требует точный SHA базы 238. Backup детерминированно создаётся `tools/make_backup239.py`; Manifest проверяет все содержимые файлы.

### Исторический полный повторяемый шлюз build 238

Из распакованного backup 238 (Java 21 можно временно поставить `python3 -m pip install --no-cache-dir --target /tmp/jdkpkg 'jdk4py==21.0.8.2'`):

```bash
python3 tests/gate_build238.py --inputs test-inputs \
  --base237 test-inputs/settledlands-0.8.0-build237-corpse-pose.jar \
  --java /path/to/java21 --jar settledlands-0.8.0-build238-corpse-recovery.jar
```

Ожидается `BUILD 238 GATE: PASS`. Запускает весь шлюз 237 ниже, проверяет все изменённые классы 238 и точный diff, старые 4 fixture также на 238, новую `tests/jvm_recovery238.py` и отрицательные мутанты обхода владельца/очереди. В fixture код мода настоящий, но игровые зависимости — стенды; игра ещё не проверена. После изменений `jvm_verify` калибровки старых сломанных билдов обязательны.

### Исторический полный повторяемый шлюз build 237

Backup содержит входные jar в `test-inputs/`; 230/233/234 использовать только как отрицательные тесты. Нужны Python 3.11+ и **JVM 21**, не `javac`. При её отсутствии агент может временно установить `jdk4py==21.0.8.2` через pip вне репозитория.

```bash
python3 tests/gate_build237.py --inputs /path/to/test-inputs \
  --java /path/to/java21 \
  --jar settledlands-0.8.0-build237-corpse-pose.jar
```

Ожидается `BUILD 237 GATE: PASS`. Скрипт проверяет воспроизводимую сборку, валидатор на 232 и 237, отрицательные 233/234, точный allowlist относительно 232 и 236, TOML, языки и mixin-классы, побайтное соответствие `TexProbe` генератору и его JVM-самотест. Дополнительно проверяются CRC, отсутствие/невалидность языка, несовпадение ключей, отсутствующий mixin, ошибочный allowlist и невозможная цель JVM-проверки: каждый негативный тест обязан падать.

`jvm_verify.py` после любой правки требует повторной калибровки: build 230 `GuideNpc#applyProfile` → `VerifyError: Bad instruction`; build 234 `TexProbe#logObj(java.lang.String,java.lang.Object)` → `VerifyError: Illegal target of jump`; build 232/236 `GuideNpc#applyProfile` и build 235 `TexProbe#logObj(...)` → OK. Для 237 отдельно проверять `GuideNpc#applyProfile`, `GuideCorpseNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)`, `CorpseLifecycle#onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent)` и `PlayerCorpsePossession#dressBodyCorpse(dev.settledlands.npc.GuideCorpseNpc,dev.settledlands.npc.PlayerCorpsePossession$PendingBody)`. Текущий скрипт всё это делает.

Обязательно выполняются четыре поведенческие fixture с отрицательными контролями на 236. Реальная JVM с тестовыми заглушками **не** подтверждает работу NeoForge/рендера в игре; после каждого билда нужен отдельный пользовательский игровой прогон.

---

## 9. Грабли байткода (стоили шести отозванных сборок)

1. **Не вставлять байты «на глаз»** — только `tools/classedit.py`.
2. `rel = target_addr − opcode_addr` (не операнда!) — инцидент 234.
3. `invokeinterface`: `count = objectref + слоты аргументов` (для
   `info(String,Object,Object)` = **4**), последний байт `0` — инцидент 236α.
4. `tableswitch`/`lookupswitch` выравниваются по 4 байта от **абсолютного** адреса:
   длина вставки обязана быть кратна 4 (добивается `nop`) — инцидент 236β.
5. Порядок сборки пула: все `cp.utf8(...)` **до** `dump()` (инцидент 233).
6. `@Mixin.value` и `targets` — **массивы** (`5b 00 01 63 …`), иначе ClassCastException.
7. Свой класс без ветвлений → фреймы не нужны; с ветвлениями → либо major 49,
   либо честные `StackMapTable` (и только `jvm_verify.py` это подтверждает).
8. `ldc` — индекс ≤ 255, иначе `ldc_w`.

---

## 10. Правила, которые юзер требует соблюдать (нарушать нельзя)

* Ответы **короткие**, без «простыней» и оправданий. Схема: что случилось → почему →
  что сделано → что проверить → ссылка.
* В каждом ответе, где есть собранный jar, — **кликабельная markdown-ссылка**
  `[имя](https://raw.githubusercontent.com/BORGERone/mod/arena/24676dbe-mod/<файл>)`,
  резерв `https://github.com/BORGERone/mod/raw/arena/24676dbe-mod/<файл>`. В новой сессии подставить её закреплённую ветку.
  Не вставлять URL в блок кода, не давать «голым текстом». Jar первым, zip вторым.
* Всегда рядом: размер, `sha256`, `certutil -hashfile <файл> SHA256`, и напоминание
  «Сохранить ссылку как…». GitHub Releases для раздачи **не** использовать.
* Перед выдачей ссылки проверять файл в текущей ветке: `gh api repos/BORGERone/mod/contents/<файл>?ref=arena/24676dbe-mod --jq .size`. Contents API для файлов >1 MiB не даёт base64 в `content` и не подходит как запасная скачка.
* После «я залил лог» — сразу `git fetch origin` + `git checkout origin/main -- latest.log`.
* jar собирает **агент**, вариант «поставь javac/собери сам» отклонён.
* Каждый билд: backup + sha256, `present_file` jar → zip → jar (jar остаётся последним в просмотре), AI-GUIDE внутрь zip,
  toml проверять `tomllib`.
* Самодеятельные фиксы вне списка задачи — только **предлагать** (PROPOSE-ONLY).
* Не пересобирать `GuideNpc`/`NpcContent`/`GuideRenderer` из `patch-src-229`
  (там лежит только `PlayerCorpsePossession.java`, он НЕ компилируется) — только
  байтпатч/миксин/новый класс.
* Труп и гроб не должны быть нежитью (откат 205): 1/1 и 60/60 HP, прежняя неуязвимость (включая существующее исключение `/kill`),
  привязка поводком к гробу. Отклонены: микро-хитбокс, keyAttack-гейт, снятие
  land-гейта, щит +40°, crossfade 0.2s, HnS-переопределения, «бонусные» фичи.

---

## 11. Как проверить build 240 в игре

1. Сохранить копию мира. В `mods` оставить только `settledlands-0.8.0-build240-npc-local-cult.jar`; ZIP туда не класть. Сверить размер и SHA из начала файла.
2. Бестелесным игроком попытаться вселиться в **нового или проверенно не отмеченного** NPC: отсутствие `культ`, 0 и отрицательное значение должны запрещать вселение, даже если у игрока положительные личные или общие очки `культ`. Переменная NPC не должна при этом создаваться или изменяться.
3. Через прежний сценарий/редактор локальных переменных NPC выставить именно локальное `культ` и выполнить действие графа; с 1 и больше вселение должно работать по прежним правилам, с 0 — нет. Проверить, что локальное значение одного NPC не даёт допуск другому; имя `культ` писать точно, с тем же регистром.
4. Старые NPC могли получить `культ=1` от прежнего моста; обновление не удаляет такие значения. Проверить сохранение/повторный вход, а также старую механику смерти во вселённом теле, возврат вещей, тело/гроб и поводок. Обычная смерть и алтарь должны работать как прежде.
5. Если сбой — приложить свежий `latest.log` этого запуска, время, точные значения переменных игрока и выбранного NPC и описать способ проверки. Игровой результат не заявлять без подтверждения пользователя.

### Исторический план проверки build 239

1. Сохранить копию мира, убрать предыдущий jar из `mods`, поставить только build 239; сверить 1 544 746 байт и SHA-256 из актуального состояния. Backup ZIP не класть в `mods`.
2. Войти в NPC, умереть, самостоятельно выйти из чистилища, найти именно свой труп и нажать ПКМ пустой рукой. После полного возврата труп исчезает; отдельно проверить гроб и прикреплённый поводок (отсоединяется и выпадает).
3. При полном/частичном инвентаре труп или гроб должен остаться, как и оставшиеся вещи; освободить место и повторить. Проверить чужого игрока, повторный ПКМ и сохранение/загрузку. Пустой обычный труп по ПКМ не пропадает.
4. Проверить здоровье тела 1/1 и гроба 60/60, неуязвимость, механику поводка и алтарь; обычная смерть должна работать как раньше. Если обнаружен сбой — приложить свежий `latest.log` этого прогона, указать время и состояние вещей.

### Исторический план проверки 237 (уже пройден пользователем)

1. Удалить build 236 из папки `mods`, поставить только build 237; сверить 1 541 606 байт и SHA-256 из актуального состояния. Backup ZIP не класть в `mods`.
2. Войти в мир, проверить уже существовавший сохранённый труп; затем вселиться в NPC и умереть, дождаться **нового** трупа. Отдельно убедиться, что гроб работает как раньше.
3. Если труп всё ещё не виден — выйти из мира, приложить свежий `latest.log` этого запуска и сообщить приблизительное время смерти/тип трупа. Искать `[SettledLands][corpse] model=… tex=…`, маркеры смерти, ошибки. Отсутствие `[probe]` в build 237 ожидаемо.
4. Не угадывать имя модели при пустом `model`/`tex`; не считать глобальные значения в логах 236 доказательством, что они от одной сущности. Если одного серверного лога недостаточно, сначала разработать адресный одноразовый зонд без per-frame спама.

---

## 12. Где что лежит

```
/home/user/mod/
  AI-GUIDE-SettledLands.md      правила, §11 JVM-верификация, §12 битая закачка
  SESSION-STATE.md              журнал решений по билдам (28 пунктов)
  HANDOFF-NEXT-SESSION-RU.md    этот файл
  CHECK-JAR-239-RU.txt          PowerShell-проверка скачанного jar
  README-Build239-EmptyCorpseCleanup-RU.txt  сведения и игровые шаги
  latest.log                    старый origin/main лог (не более новый attachment после 236)
  tools/                        сборщики 238/239, байтредактор, валидаторы
  tests/                        полный шлюз и JVM-fixture
  settledlands-0.8.0-build237-corpse-pose.jar   подтверждённая база
  settledlands-0.8.0-build238-corpse-recovery.jar
  settledlands-0.8.0-build239-empty-corpse-cleanup.jar
  SettledLands-Build239-EmptyCorpseCleanup-Source.zip  все тестовые входы, инструменты, AI-GUIDE и jar
```

Полезные дескрипторы/поля, которые точно понадобятся:
```
dev/settledlands/npc/GuideNpc            entityData:Lnet/minecraft/network/syncher/SynchedEntityData;
                                         DATA_TEXTURE:Lnet/minecraft/network/syncher/EntityDataAccessor;
                                         profile:Ldev/settledlands/npc/NpcProfile;
                                         setProfile(Ldev/settledlands/npc/NpcProfile;)V
                                         applyProfile()V   texturePath()Ljava/lang/String;
dev/settledlands/npc/NpcProfile          texture:Ljava/lang/String;
dev/settledlands/npc/PlayerCorpsePossession   LOGGER:Lorg/slf4j/Logger;
                                         dressBodyCorpse(Ldev/settledlands/npc/GuideCorpseNpc;Ldev/settledlands/npc/PlayerCorpsePossession$PendingBody;)V
                                         onPlayerDeath(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V
dev/settledlands/npc/PossessionSystem    possess(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;)V
                                         possessModelKey(Lnet/minecraft/world/entity/Entity;)Ljava/lang/String;
                                         setPossessed(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Ljava/lang/String;)V
dev/settledlands/model/BbModelManager    getModel(Ljava/lang/String;)Ldev/settledlands/model/BbModel;
dev/settledlands/model/BbModelStorage    findModelFile(Ljava/lang/String;)Ljava/io/File;
                                         getModelsDir()Ljava/nio/file/Path;
```

---

## 13. Что делать в первую очередь (чек-лист)

- [ ] Уточнить результат игровой проверки **239**; не считать JVM-стенды игрой. 237 подтверждён, 238 и 239 пользователем ещё не подтверждены.
- [ ] Если пользователь сообщил о сбое, анализировать самый свежий приложенный лог именно от 239, время, владельца, поводок, состояние списка и инвентаря; не затереть его старым `origin/main:latest.log`.
- [ ] Учесть явно выбранный scope: только смерть во вселённом теле; обычную смерть не менять.
- [ ] При необходимости чинить через `tools/classedit.py` + `tools/smt.py` и прогнать полный шлюз §8, включая `gate_build239.py`, JVM и поведенческие негативные контроли.
- [ ] Обновить `SESSION-STATE.md`, `AI-GUIDE`, собрать backup, показать jar → zip → jar, выдать ссылки, размер и хеши.
