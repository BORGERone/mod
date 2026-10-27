# ПЕРЕДАЧА СМЕНЫ: «у трупа нет модели» (SettledLands 0.8.0)

Файл для новой сессии. Читать целиком до первой сборки. Всё, что ниже, проверено
по байткоду/логам, а не «по памяти». Дата: 27.10.2026.

---

## 0. Задача в одну строку

Труп игрока (`dev.settledlands.npc.GuideCorpseNpc`) **не рисуется моделью** —
либо ничего не видно («прозрачный»), либо встроенная заглушка. Живые NPC при этом
рисуются нормально. Нужно найти, почему у трупа не разрешается имя модели, и
починить. Собирать jar агент обязан сам (компилятора нет — только правка байткода
и новые классы).

Текущий кандидат на раздачу: **build 236** (см. §4). **Он ещё не проверен в игре:
последний лог, который есть в репозитории, — про недокачанный jar (§5).**

---

## 1. Первые действия новой сессии (5 минут, ничего не собирать)

```bash
cd /home/user/mod
git fetch origin && git log --oneline -3 origin/main -- latest.log
git checkout origin/main -- latest.log && wc -l latest.log
```
Если `latest.log` новее коммита `10b423f` — это новый лог, читать его (§5, §6).
Если нет — игра после 236 не запускалась (или лог не залит).

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
| 236 | `DATA_TEXTURE` пишется в начале `applyProfile` и в конце `dressBodyCorpse` + лог | ⏳ **в игре не проверен**, см. §5 |

---

## 4. Билды, их sha256 и статус

Все файлы лежат в корне репозитория (ветка `arena/829e0ad0-mod`).

| файл | байт | sha256 | статус |
|---|---|---|---|
| `settledlands-0.8.0-build229-sablesweep.jar` | — | — | рабочая база (до фиксов) |
| `settledlands-0.8.0-build230-modelfix.jar` | 1536982 | `b41dc40574e3c2d8…` | отозван |
| `settledlands-0.8.0-build231-earlytex.jar` | 1538081 | `c1ca5ebf9d642bbd…` | отозван |
| `settledlands-0.8.0-build232-earlytex2.jar` | 1538344 | `a75d8a8f724f5e74…` | рабочая база для диффов, модели нет |
| `settledlands-0.8.0-build233-probe.jar` | 1540690 | `0592d61c4ffe5888…` | отозван (регресс-кейс валидатора) |
| `settledlands-0.8.0-build234-probe.jar` | 1540699 | `f83b8c69178176ec…` | отозван (регресс-кейс JVM-верификатора) |
| `settledlands-0.8.0-build235-probe.jar` | 1540699 | `cfddca97baf6a98b…` | зонд, грузится |
| **`settledlands-0.8.0-build236-corpse-tex.jar`** | **1541160** | **`4d640528b565e788…`** | **кандидат: 236 проверен всеми шлюзами и настоящей JVM, в игре — нет** |

Полные хеши — в `git log` / `SESSION-STATE.md`.

---

## 5. Последний лог (origin/main `10b423f`, 1170 строк) — НЕ баг мода

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

Инструкция юзеру: `CHECK-JAR-236-RU.txt` (PowerShell `Invoke-WebRequest -OutFile`
+ сверка `Length = 1541160` и хеша; резервы jsDelivr и GitHub API).
**Правило: при «вылете» сначала сверять размер/хеш скачанного файла, потом код.**

---

## 6. Маркеры, которые надо искать в НОВОМ логе

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
(`TexProbe` печатает строку только при изменении значения — спама не будет.)

---

## 7. Дерево решений по новому логу

### A. `model=` пустое (сервер не получил имя модели)
Значит `possessModelKey(player)` пуст → при вселении `npc.texturePath()` был `""` либо
`setPossessed` не выполнился. Смотреть `PossessionSystem.possess` (184 Б) — он пишет
`settledlands_possess_model = npc.texturePath()` перед `npc.discard()`.
Готовый фикс (build 237): в `possess` логировать `texturePath()` и `profile.texture`,
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

## 8. Инструменты (всё в `tools/`, всё работает)

| файл | что делает |
|---|---|
| `validate_jar.py` | строгий валидатор jar: структура до последнего байта, пул, операнды, атрибуты, цели переходов и `exception handler` на границах инструкций, фреймы `StackMapTable` на всех целях ветвлений, `count` у `invokeinterface`, версии, миксины из JSON. `--base` даёт побайтовый дифф |
| `jvm_verify.py` | **главное**: собирает синтетические заглушки внешних типов и грузит настоящий jar настоящей JVM (`tools/jdkpkg/.../java`), вызывает метод по рефлексии. Ловит VerifyError, который валидатор не видит |
| `classedit.py` | вставка байтов в `Code` существующего класса: пересчёт переходов/switch, `exception_table`, `LineNumberTable`, `StackMapTable`, сплайс без порчи остального файла, проверка пересборки пула байт-в-байт |
| `smt.py` | разбор/сборка `StackMapTable` (типы сохраняются, меняется только delta) |
| `selftest_probe.py` | JVM-самотест самописного `TexProbe` (тот же генератор, что идёт в jar) |
| `check_mixin_class.py` | проверка самописного миксина (`@Mixin` массивами и т.д.) |
| `disasm.py` | дизассемблер одного метода: `python3 tools/disasm.py <jar> --class X.class --method name` |
| `decode.py` | длина инструкций/цели переходов |
| `patch_corpse_tex.py` | сборка 236: две вставки в `GuideNpc` и `PlayerCorpsePossession` |
| `patch_probe233.py` | сборка 235: `TexProbe` + `CorpseRenderProbeMixin` (генератор классов с мини-ассемблером) |
| `verify_jar.py` | zip/toml/lang/mixins/маркеры + дифф |

### Шлюз перед ЛЮБОЙ раздачей (проверено на инцидентах)
```bash
# 1. калибровка валидатора на заведомо рабочем jar
python3 tools/validate_jar.py settledlands-0.8.0-build232-earlytex2.jar      # ПРОЙДЕНЫ
# 2. новый jar
python3 tools/validate_jar.py <new>.jar --base settledlands-0.8.0-build232-earlytex2.jar
# 3. регресс-тесты: обязаны ПАДАТЬ
python3 tools/validate_jar.py settledlands-0.8.0-build233-probe.jar
python3 tools/validate_jar.py settledlands-0.8.0-build234-probe.jar
# 4. НАСТОЯЩАЯ JVM для каждого изменённого класса
python3 tools/jvm_verify.py <new>.jar 'dev/settledlands/npc/GuideNpc#applyProfile'
python3 tools/jvm_verify.py <new>.jar 'dev/settledlands/npc/PlayerCorpsePossession#dressBodyCorpse(dev.settledlands.npc/GuideCorpseNpc,dev.settledlands.npc/PlayerCorpsePossession$PendingBody)'
# 5. самописные классы/миксины
python3 tools/selftest_probe.py          # «JVM-САМОТЕСТ: OK»
python3 tools/check_mixin_class.py <path/to/Mixin.class>
# 6. общие проверки
python3 tools/verify_jar.py <new>.jar --base settledlands-0.8.0-build232-earlytex2.jar
```

Калибровка `jvm_verify.py` (после любой правки инструмента повторить):
`230 → VerifyError (Bad instruction)`, `234 → VerifyError (Illegal target of jump)`,
`232 → OK`, `236 → OK`.

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
  `[имя](https://raw.githubusercontent.com/BORGERone/mod/arena/829e0ad0-mod/<файл>)`,
  резерв `https://github.com/BORGERone/mod/raw/arena/829e0ad0-mod/<файл>`.
  Не вставлять URL в блок кода, не давать «голым текстом». Jar первым, zip вторым.
* Всегда рядом: размер, `sha256`, `certutil -hashfile <файл> SHA256`, и напоминание
  «Сохранить ссылку как…». GitHub Releases для раздачи **не** использовать.
* Перед выдачей ссылки проверять файл в ветке: `gh api repos/BORGERone/mod/contents/<файл>?ref=arena/829e0ad0-mod --jq .size`.
* После «я залил лог» — сразу `git fetch origin` + `git checkout origin/main -- latest.log`.
* jar собирает **агент**, вариант «поставь javac/собери сам» отклонён.
* Каждый билд: backup + sha256, `present_file` jar → zip, AI-GUIDE внутрь zip,
  toml проверять `tomllib`.
* Самодеятельные фиксы вне списка задачи — только **предлагать** (PROPOSE-ONLY).
* Не пересобирать `GuideNpc`/`NpcContent`/`GuideRenderer` из `patch-src-229`
  (там лежит только `PlayerCorpsePossession.java`, он НЕ компилируется) — только
  байтпатч/миксин/новый класс.
* Труп и гроб не должны быть нежитью (откат 205): HP=1/full HP, неубиваемы,
  привязка поводком к гробу. Отклонены: микро-хитбокс, keyAttack-гейт, снятие
  land-гейта, щит +40°, crossfade 0.2s, HnS-переопределения, «бонусные» фичи.

---

## 11. Как воспроизвести в игре

1. Поставить jar 236 (сверить `Length = 1541160`, sha256 `4d640528…`).
2. Вселиться в NPC (ПКМ/ЛКМ по NPC с посохом — как обычно), умереть.
3. Дождаться трупа, посмотреть на него.
4. Выйти из игры, залить `latest.log` в `origin/main`.
5. В логе нужны строки из §6.

---

## 12. Где что лежит

```
/home/user/mod/
  AI-GUIDE-SettledLands.md      правила, §11 JVM-верификация, §12 битая закачка
  SESSION-STATE.md              журнал решений по билдам (28 пунктов)
  HANDOFF-NEXT-SESSION-RU.md    этот файл
  CHECK-JAR-236-RU.txt          PowerShell-проверка скачанного jar
  README-Build2xx-*.txt         пояснения к билдам для юзера
  latest.log                    последний лог с origin/main (битая закачка 236)
  patch-src-229/dev/settledlands/npc/PlayerCorpsePossession.java   (не компилируется)
  tools/                        см. §8
  settledlands-0.8.0-build2xx-*.jar
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

- [ ] `git fetch origin`, проверить свежесть `latest.log` (§1).
- [ ] Если лог новый — выписать из него строки §6 и выбрать ветку A/B/C/D (§7).
- [ ] Если лога нет — **не собирать зря**: попросить юзера одним абзацем:
      «удали битый jar, скачай PowerShell-командой из `CHECK-JAR-236-RU.txt`,
      сверь Length/хеш, запусти, умри в теле NPC, залей лог».
- [ ] Выбранную ветку чинить через `tools/classedit.py` + `tools/smt.py`,
      обязательно прогнать шлюз §8 целиком (включая `jvm_verify.py`).
- [ ] Обновить `SESSION-STATE.md`, `AI-GUIDE` (если новый урок), собрать zip,
      `present_file` jar → zip, выдать ссылки по правилам §10.
