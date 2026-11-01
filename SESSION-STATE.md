# SESSION-STATE — SettledLands

Обновлено: 2026-11-01. Ветка: `arena/99d7691a-mod` (имя ветки задаётся платформой, переименовать нельзя).

## Текущая сборка

`settledlands-0.8.0-build308-nodes.jar` — Build 308 (откат), 5 гейтов PASS.
База — 304 (`settledlands-0.8.0-build304-workspace-skin.jar`), патч `tools/patch_build308.py`.
Цепочка (историческая): 272 (база с Drive) → 273 (`tools/patch_build273.py`) → 274 (`tools/patch_build274.py`)
→ 275 (`tools/patch_build275.py`, первая сборка из исходников через ECJ) → 276 (`tools/patch_build276.py`)
→ 277 (`tools/patch_build277.py`) → 278 (`tools/patch_build278.py`, возвращён `drawNode`-хук)
→ 279 (`tools/patch_build279.py`, дистанция преследования)
→ 280 (`tools/patch_build280.py`, реальный догон + выход `%pursuit%`)
→ 281 (`tools/patch_build281.py`, вылет `hasLineOfSight` + фаза D гейта)
→ 282 (`tools/patch_build282.py`, тело вселённого снова анимируется)
→ 283 (`tools/patch_build283.py`, анимация NPC по реальной скорости + выход цели у ноды преследования)
→ 284 (`tools/patch_build284.py`, цельный синий провод значения + вход цели у ноды)
→ 285 (`tools/patch_build285.py`, вылет NoSuchFieldError: Entity.walkAnimation → LivingEntity)
→ 286 (`tools/patch_build286.py`, провода значения штатным `curve` холста + модель профессий 63 текстуры)
→ 287 (`tools/patch_build287.py`, возвращены `hit`/`visible` в `PursuitGraphNode272` + новый гейт `dangling_refs`)
→ 288 (`tools/patch_build288.py`, средняя кнопка режет синий провод + труп как цель преследования)
→ 289 (`tools/patch_build289.py`, синий выход у ноды «В радиусе» + `NpcCheckValue289` на сервере)
→ 290 (`tools/patch_build290.py`, синие входы у реплики/триггера/условия/переменной + синий выход у «Урона»)
→ 291 (`tools/patch_build291.py`, починка вылета на запуске из 290 + гейт типов параметров)
→ 292 (`tools/patch_build292.py`, отозвана) → 293 (`tools/patch_build293.py`, строка-шаблон tp
в пуле констант `NpcActions`)
→ 294 (`tools/patch_build294.py`, `В радиусе` → `Условие`: обход не обрывается на пустом радиусе).
→ 295 (`tools/patch_build295.py`, пустой радиус всё равно запускает цепь проверки)
→ 296…304 → 305 (`tools/patch_build305.py`, воркспейс нод + библиотека с поиском).

**Build 290 НЕ ЗАПУСКАЕТСЯ**: `GuideDialogueValue290.settledlands$enterSendGraph` был объявлен с `Object` вместо
`GuideDialogue$Session` → `InvalidInjectionException: Invalid descriptor` → миксин не применился → фатальный вылет.
Урок: загрузчик миксинов сравнивает дескрипторы обработчиков БУКВАЛЬНО; `GuideDialogue$Session` package-private, из
пакета миксина его не назвать → либо хук на методе без package-private типов (в 291 — `@Redirect` на
`GuideNpc#relationTo(ServerPlayer)` внутри `sendGraph`), либо отдельный mixin-конфиг с пакетом `dev.settledlands.npc`.
`tools/check_mixin_class.py` теперь сверяет типы параметров каждого `@Inject` (на 290 падает, на 291 проходит);
`@Redirect` пока сверяется только по цели из `@At`.
Что и почему исправлено — `README-Build273-RU.md` … `README-Build285-RU.md`.
База 277 — jar 276, база 278 — jar 277, база 279 — jar 278, база 280 — jar 279, база 281 — jar 280, база 282 — jar 281; сборка из исходников: `build27X-src/`
(`src/`, `stubs/`, `out/`, для 279 ещё `api-src/`+`api2/`), ECJ 3.46.100.

Build 285: вылет при отрисовке любого NPC — `NoSuchFieldError: Entity does not have member field
walkAnimation`. Поле объявлено в `LivingEntity`; заглушка для компиляции держала его в `Entity`,
поэтому код собрался и гейты прошли. `NpcLocoObserveMixin283` читает через `LivingEntity`,
заглушка перенесена туда же. **Урок: заглушки MC должны повторять реального владельца поля/метода**,
иначе ECJ не ловит ошибку.

Build 290: синие входы у штатных нод. Клиент: `PursuitGraphNode272.isValueSourceNode` (CHECK|DAMAGE),
`isValueInputNode` (LINE|TRIGGER|IF|VARIABLE, кроме ноды преследования — она тоже TRIGGER на диске),
`inputSource/setInputSource` (поле `Node.varInputNode`, уже сохраняется в NBT), `inputPinScreenX/Y`, `hitInputPin`,
`drawValuePins`, `hideStockInputWires/restoreStockInputWires` (скрыть штатный провод IF на время `wires()`).
Миксин `NpcPursuitValuePinMixin290` заменил 289 и обслуживает все ромбы; `NpcPursuitGraphUiMixin272` рисует их в TAIL `drawNode`.
Сервер: `NpcDamageValue290` (запись нападающего в HEAD `GuideDialogue.handleNpcDamaged`, окно 200 тиков, фильтр и
`minDamage` ноды) + `NpcValueSource290` (общий `resolve`, `ifValue`, `variablePresence`, `lineText`, `triggerBlocked`).
Миксин `GuideDialogueValue290`: IF (HEAD `evalIfNode` → `evalCondition(presence, varOp, varCompare)`), переменная
(HEAD `readVarValue`), реплика (`@Redirect` на `Node.lineFor` внутри `sendGraph` + `enter(npc)` в HEAD), триггер
(HEAD 7-арг `executeGraphTriggers`, сравнение списка по идентичности), урон (HEAD `handleNpcDamaged`).
Важно: `GuideDialogue$Session` package-private — в миксине параметр типа Object; `Node.triggers` = List<Integer>,
сравнение через `(Object)`; `evalCondition`/`matchesDamageFilter` public static.

Build 289: у ноды «В радиусе» (CHECK, type 5) появился синий выход. Клиент: `PursuitGraphNode272.isCheckValueNode/
checkPinScreenX/Y/hitCheckPin/drawCheckPin/wiredTo/unplugFrom/sourceLabel`; ромб рисуется в `@Inject TAIL drawNode`
(`NpcPursuitGraphUiMixin272`), тяга/бросок/резка — в `NpcPursuitValuePinMixin289` (заменил 288). Бросок ромба на ноду
преследования пишет `#<id>` через `setSource` — то же поле, что у связи преследование→преследование. Сервер:
`NpcCheckValue289.isCheckSource/detected` повторяет правила `GuideDialogue#findCheckTarget` (checkTarget, радиус 1..64,
владелец и наблюдатели исключены, ближайший, без кулдауна, кэш 10 тиков); `NpcPursuit269` читает `#id` из действия,
при не-check источнике работает по тексту как раньше (важно: связь 275–288 «преследование→преследование» не сломана),
источник сохраняется в NBT (`PursuitSource289`), `%pursuit%` отдаёт найденное тело. Проверено: все MC/JOML-ссылки новых
классов уже есть в базовом jar 288 (аналог фиаско 280 с `hasLineOfSight`).

Build 288: (1) синие провода значения теперь режутся средней кнопкой, как штатные кабели, — по одному проводу:
`PursuitGraphNode272.hitValueWire` сэмплирует ту же кубическую кривую, что и `NpcDialogueGraphScreen#curve`
(горизонтальные касательные, `reach = max(25*zoom, |dx|*0.45)`), с допуском `max(1.8, min(5.5, 4*zoom+1.2))` как
в `NpcGraphCanvas#hitCable`; резка идёт через `settledlands$remember()`, бросок тоже (штатная отмена). Миксин
`NpcPursuitValuePinMixin286` заменён на `...288`, правый клик по ромбу остался ярлыком «снять все связи».
(2) `NpcPursuit269.matches` больше не отбрасывает `GuideCorpseNpc` (раньше вместе с `GuideTriggerNpc`), поэтому
нода преследования находит труп по `profile.identifier`; триггеры исключены, труп-преследователь по-прежнему
запрещён в `run`. Внутренние классы `NpcPursuit269$*` намеренно не заменялись — поток инструкций идентичен.

Build 287: вылет при открытии диалога с правкой — `NoSuchMethodError PursuitGraphNode272.hit(NpcGraphCanvas,
NpcDialogueGraph, DD)I` из `NpcPursuitCanvasMixin272`. В 286 вместе со старым рисованием по пикселям из класса
уехали `visible` и `hit`; миксин холста лежит в jar собранным против 285 и не перекомпилируется, поэтому ECJ
пропустил, `jvm_verify` смотрит форму класса, а `check_mixin_class` — точки внедрения. Оба метода восстановлены
байт-в-байт по потоку инструкций (та же сигнатура и доступ). Добавлен пятый гейт `tools/dangling_refs.py`:
сверяет каждый класс с предыдущим jar и падает, если класс потерял метод/поле, на которое внутри jar ещё
ссылаются; на 286 он воспроизводит ровно этот краш. `focus` и `hitPort` намеренно не восстановлены — ссылок
на них в jar нет.

Build 286: три пункта. (1) Женский японский рыбак: в старой модели профессий (41 текстура) не было
`rybak_jp_fem.png`, пара «женщина + японский» молча падала на мужскую текстуру; плюс файлы вариантов
(`ivy_oleander_prof264_p1_m0_j1.bbmodel`) кэшировались на диске навсегда, поэтому правка текстуры не
была видна. В jar положен свежий экспорт из Blockbench (63 текстуры, `assets-src/ivy_oleander_professions_286.bbmodel`,
исходник на Drive `15UKE2OMHu2jbccuku4TZiAM3y7wEsTyX`), таблица `NpcProfessionSkin264` обновлена
(японские пары для фермера/целителя/шахтёра/лесоруба/охотника/трактирщика, женские маг и кузнец),
а `NpcProfessionModel264` пересобирает вариант по метке `MODEL_REVISION` + имя текстуры
(`<variant>.bbmodel.rev` / `.tex`). (2) Провод значения больше не рисуется прямоугольниками по
пикселям: `NpcPursuitValuePinMixin286` вызывает `NpcDialogueGraphScreen#curve` в том же буфере,
шейдере и состоянии, что и штатные кабели — при зуме ведёт себя идентично. (3) Бросок синего выхода
на другую ноду преследования теперь ставит `setSource` (её синий вход), на прочие ноды —
`toggleConsumer`; бросок в пустоту ничего не чистит, правый клик по ромбу снимает связь. Миксин 284
удалён из jar и конфига.

Build 284: провод значения рисовался штрихами (26 отсчётов, каждый второй выброшен, точки 2×2) —
«рвано, мало блоков». Теперь один кубический провод с горизонтальными касательными, шаг ~1 пиксель,
заливка коротких участков, цвет синий `#4C8DFE` (ромбы/текст `#6FB6FF`). У карточки появился парный
вход значения — синий ромб на левой кромке, в том же ряду; связь с другой нодой преследования даёт
следование её цели, а `refreshDependents` при каждом `changed()` переписывает полученный текст в
действие (цепочка до 8 звеньев). Источник хранится как `|#<id>`, получатели как `|@<ids>`, сервер
по-прежнему читает три числа по индексу. Миксин 283 заменён на `NpcPursuitValuePinMixin284`
(оба ромба + хук `changed()`), класс 283 удалён из jar и конфига; клиентские миксины 283 перенесены
в список `client`.

Build 283: у идущих NPC анимация считалась от мобовской `walkAnimation` (обновляется раз в
тик, `speed` насыщается) — фаза шла ступеньками, вес прыгал, а догон 3x анимировался в темпе
прогулки. Новый `NpcLocoDriver283` считает фазу по пройденной дистанции (83.333 ед./блок = ровно
ванильная единица за тик на 0.24 блока/тик) и вес от реальной скорости с быстрым подъёмом
(0.08 с) и медленным спадом (0.18 с); два чтения в `renderFreshNpc` перенаправлены
(`NpcLocoSmoothMixin283`), замер — `@Inject TAIL` в `syncFrom` (`NpcLocoObserveMixin283`), прокси
не пишется. Телепорт >4 блоков за кадр отбрасывается. Одновременно у ноды преследования появился
второй выход: ромб значения (y=92) под выходом выполнения, перетаскивается на любую ноду
(`NpcPursuitTargetPinMixin283`), получатели хранятся в самом действии четвёртым параметром
`|@12,15`, поэтому новых полей в `NpcDialogueGraph$Node` нет, а `NpcPursuit269` читает три числа
по индексу и хвост игнорирует.

Build 280: причина «NPC не ускоряется» — в 279 путь пересоздавался каждые 4 тика, а
каждый `moveTo` стартует путь заново. Теперь дисциплина пересчёта взята у `NpcEscort`
(`isDone`, снимок цели `CATCH_UP_TARGET_MOVED_SQ`, сторож прогресса, откат
20/40/80/160). Скорость догона = скорость сопровождения × настройка «Ускорение догона»
(1.0–3.0, дефолт 1.5), при разрыве >24 блока и без линии видимости NPC переносится в 8
блоках от цели. Выход ноды: `%pursuit%`, `%pursuit_kind%`, `%pursuit_uuid%` —
подставляются в `NpcProfile#fill` (миксин `NpcPursuitOutputMixin280`, `RETURN` +
`getReturnValue`), то есть и в реплики, и в действия/команды.

Build 279 впервые правит **серверный** класс `NpcPursuit269`: он собран из настоящего
исходника Build 271 (`reference/patch-src-build246-271/src/build271/...`), который
перекомпилируется в байткод, совпадающий с 278 по `max_stack/max_locals/code_len` во всех
17 методах. Для компиляции серверных классов нужны заглушки `build279-src/api-src` →
`build279-src/api2` (в `tools/api271` их не хватало: `isDone/setSpeedModifier/moveTo(DDDID)`,
`getDeltaMovement/setDeltaMovement`, `xxa/yya/zza/getMoveControl`, `MoveControl`,
`getDouble/putDouble`).
JDK в песочнице нет: `java` берётся из PyPI-колеса `jdk4py` (`pip download jdk4py`,
распаковать, `chmod +x .../java-runtime/bin/*`) — это только JRE, ECJ запускается им.

## Найденные краши (хронология)

| Сборка | Симптом | Причина | Кто ловит |
|--------|---------|---------|-----------|
| 272 | старт клиента | CallbackInfoReturnable на void `focus`, @Shadow на несуществующий `selected()`, @Inject в несуществующий `charTyped` | gate 3 (цели mixin-ов) |
| 273 | создание ноды преследования | `@Inject(TAIL)` без `cancellable = true` вызывает `setReturnValue` → CancellationException | gate 3, фаза `check_cancellable` |
| 273 (лог 1) | старт клиента | чужой мод `hackersandslashers` (Forge-сигнатура `ModConfigSpec.Builder.define(String,String)`) | не наш код |
| 277 | «создаётся нода-триггер» | при переписывании `NpcPursuitGraphUiMixin272` в 277 потерялся `@Inject` на `drawNode` — карточку рисовал штатный рендер («Триггер #n» + строка действия) | не ловится гейтами (нет хука — нет цели), лечится в 278 |
| 282 | «пропали анимации у тела, в которое вселился» | `PlayerMotionBridge#setup` по замыслу ставил тело в нейтральную позу, когда Fresh Animations Player Extension не установлен — в инстансе его нет вообще (лог: «is unavailable or disabled for this player»), поэтому вселённое тело стояло замороженным, а NPC жили через откат `NpcMotionBridge256` | переписана 1 инструкция (`iload freshPath` → `iconst_0`), ветка нейтральной позы не выполняется никогда, при неработающем FA тело берёт встроенную актёрскую анимацию; текст предупреждения исправлен на месте |
| 280 | «Ticking entity», `NoSuchMethodError: Entity#hasLineOfSight` | метод объявлен в **заглушке** `Entity`, в реальном MC он у `LivingEntity`; ветка срабатывала только при разрыве >24 блока | gate 3 фаза D (внешние API против сборки, работающей в игре) — добавлена в 281 |
| 276 | «в поле не печатается, текст наслаивается, нода становится триггером» | `@Unique` переименовал `charTyped` (не переопределение), а панель 276 рисовалась поверх штатного редактора триггера; `Screen.mouseClicked` не смотрит на `visible`, поэтому спрятанное поле команды продолжало принимать клики и затирало `node.action` | не ловится гейтами (семантика UI) — лечится тем, что ветка штатного инспектора не выполняется вовсе (277) |

Правило: перед каждой выдачей прогонять `tools/verify_jar.py`; если появляется
новый класс ошибок — сначала дописать под него фазу в gate, потом чинить.

## Что лежит в репозитории

| Путь | Что это |
|------|---------|
| `tools/validate_jar.py` | gate 1: zip/CRC, toml (modId/version/displayName/description), JSON-ассеты, mixin-конфиги |
| `tools/jvm_verify.py` | gate 2: структурная проверка каждого класса (CP, опкоды, ветки, exception-таблицы) |
| `tools/check_mixin_class.py` | gate 3: mixin-конфиги + РАЗРЕШЕНИЕ ЦЕЛЕЙ (@Inject/@Shadow/@ModifyConstant) внутри JAR + фаза C «dispatcher hooks» (класс, который обязан вызываться из миксина, не должен остаться мёртвым — ловит потерю `drawNode`-хука из 277) |
| `tools/verify_jar.py` | gate 4: прогон 1→3 + `<jar>.sha256sums.txt` |
| `tools/classfile.py` | парсер class-файлов и байткода (без JDK) |
| `tools/patch_build273.py` | патч 272→273 на уровне байткода (удаление методов, retarget shadow) |
| `build273-src/` | исходники build 273 (2 mixin-а + скрипт + README) |
| `build275-src/` | исходники build 275: `PursuitGraphNode272`, 2 mixin-а, `patch_build275.py`, README, stubs |
| `build278-src/` | исходники build 278: те же 3 класса + `patch_build278.py`, README, stubs, `out/` |
| `build279-src/` | исходники build 279: `NpcPursuit269` (сервер), `PursuitGraphNode272`, 2 mixin-а, `patch_build279.py`, README, stubs, `api-src`/`api2`, `out/` |
| `build280-src/` | исходники build 280: то же + 3-й mixin `NpcPursuitOutputMixin280`, `patch_build280.py` |
| `build282-src/` | исходников нет: build 282 правит байткод `PlayerMotionBridge#setup` напрямую (`tools/patch_build282.py`), 1 инструкция |
| `build281-src/` | исходники build 281: те же исходники с конусом обзора вместо `hasLineOfSight`, `patch_build281.py` |
| `build277-src/` | исходники build 277: `PursuitGraphNode272` (карточка), `NpcPursuitGraphUiMixin272`, `NpcPursuitInspectorMixin277`, `patch_build277.py`, README, stubs, `out/` |
| `tools/patch_build282.py` | сборка 282: правка 2 байт в `PlayerMotionBridge#setup` (iload→iconst_0) + текст предупреждения, toml + patch-info; сам проверяет шаблон и результат |
| `tools/patch_build281.py` | сборка 281: 1 класс заменён (`NpcPursuit269`), toml + patch-info |
| `tools/patch_build280.py` | сборка 280: 6 классов заменено, 2 добавлено (в т.ч. `NpcPursuit269$Published`), mixins.json (+1), lang (+3 ключа), toml, patch-info |
| `tools/patch_build279.py` | сборка 279: 6 классов заменено (`NpcPursuit269` + 3 внутренних, `PursuitGraphNode272`, `NpcPursuitInspectorMixin277`), lang (+2 ключа), toml, patch-info |
| `tools/patch_build278.py` | сборка 278: 1 класс заменён (`NpcPursuitGraphUiMixin272`), toml + patch-info |
| `tools/patch_build277.py` | сборка 277: 3 класса заменены, +1 mixin, −1 mixin, mixins.json, lang, toml, patch-info |
| `tools/patch_build275.py` | сборка 275: подмена 3 классов, +1 новый mixin, mixins.json, toml, patch-info |
| `AI-GUIDE-SettledLands.md` | гайд из build 228 (актуальные разделы про сборку/ритуал доставки) |
| `latest.log` | лог от 27.10 — там битый `build236-corpse-tex.jar`, не код |

## Как проверять сборку

```bash
python3 tools/verify_jar.py <jar> --expect-version 0.8.0-buildNNN \
        --base <предыдущий jar, который работает в игре>
```

`--base` включает фазу D гейта 3: каждый вызов Minecraft / NeoForge из наших
классов должен уже встречаться в базовой сборке (иначе это выдумка заглушки —
урок 280).

Gate 3 — единственный, который ловит «Critial injection failure» на старте.
Именно он находил три ошибки build 272. Для **новых** mixin-ов всегда
прогонять его до выдачи сборки.

## Открытый хвост

* Build 279: ждёт проверки в игре. Изменено: дистанция считается до цели (по умолчанию 1.6,
  настройка 1.0–8.0), остановка гасит move control и скорость (`NpcEscort#stopMotion`),
  догон = скорость сопровождения + перестроение каждые 4 тика, выход на 3 блоках,
  преследователь идёт к кольцу дистанции по своему направлению, две настройки ноды
  (`|дистанция|догон` в действии + `PursuitKeep279`/`PursuitCatchUp279` в NBT).
  Действие ноды остаётся типа TRIGGER — это по-прежнему нужно серверному пути триггеров.
* Build 276: `@Unique` на слитом `charTyped` в 275 приводил к переименованию метода —
  ввода не было; теперь метод реально переопределяет `Screen#charTyped`. Инспектор ноды
  преследования больше не триггерный: новый `NpcPursuitInspectorMixin276` прячет пресеты и
  поле команды и строит панель «тип цели / значение / завершение».
* Урок: `@Unique` на методе, который должен переопределять метод суперкласса, ломает
  переопределение молча (gates этого не видят — метод структурно валиден).
* Build 275 закрыл три пункта из 274: портов не было (инжектор `hitPort` → -1 удалён),
  карточка была 280×140 (теперь стоковая 220×100 со стоковой палитрой), ввод в поле
  не работал (`charTyped(char,int)` слит в экран миксином `NpcPursuitTypingMixin275`).
* Мёртвый код из 272 остался, не мешает: `NpcStatsGraphUi.width/height/hitPort` ветка
  `type == 16` — таких нод нет (нода преследования имеет тип 2 / TRIGGER).
* Build 263 CUTSCENE fade по-прежнему не включать.
* Build 269/270 отозваны.

## Окружение песочницы

* `python3` есть, JDK/javac в PATH нет; Java-исходники собираются вендорным
  `tools/vendor/ecj-3.46.100.jar` против заглушек `tools/api271/` (+ `build279-src/api-src`).
* `java` из `/tmp/jdk4` пропадает между сессиями. Восстановление:
  `pip download jdk4py --no-deps -d /tmp/jdkpkg`, распаковать колесо в `/tmp/jdk4`,
  `chmod +x /tmp/jdk4/jdk4py/java-runtime/bin/*`; это JRE 25, ECJ 3.46.100 работает.
* Home-путь сборки: `build279-src/`.
* Прямого интернета нет; Google Drive и GitHub — через коннекторы.
* Скачанные с Drive файлы попадают в `google_drive/` (в git не коммитятся).

## Build 291 (актуально)

- `settledlands-0.8.0-build291-launch-fix.jar` — Drive `1M49OGceKN0eD3TGJsevGG_jVxlT1v6aF` (public).
- `SettledLands-Build291-LaunchFix-Source.zip` — Drive `1WHFEkfH-ndtoJV1_m8xCiE8JT_vKV3au` (public).
- `SHA256SUMS-build291.txt`, `README-Build291-RU.md`, патчер `tools/patch_build291.py`.
- Гейты: validate_jar OK (455 записей / 407 классов), jvm_verify OK, check_mixin_class OK (38 миксинов, типы параметров сверены),
  verify_jar PASS (0 новых ссылок, 0 висячих),diff с 290: ~`mixin/GuideDialogueValue290.class` + метаданные.

## Build 292 (актуально)

Причина вылета пользователя: `tp @npc ~ ~5 ~` в ноде триггера. `NpcActions#fill` подставляет
`@npc` → uuid НПС, затем `NpcActions#command`/`#commandForEntity` проверяют
`^(tp|teleport)\s+([~^\-0-9].*)` — «цели нет, только координаты» → вставляют `@s`.
UUID начинается с hex-цифры ⇒ `tp @s <uuid> ~ ~5 ~` ⇒ «Неверный аргумент для команды».

Фикс: `NpcSelfTarget292.needsSelfTarget` (токен после tp/teleport состоит только из `~ ^ - . 0-9`)
+ `NpcActionsTpFix292` — `@Redirect` на `String.replaceFirst` в `command` и `commandForEntity`
(по одному вызову в каждом, патчер это проверяет: `check_redirect_sites`).
`@s` — игрок (источник команды), `@npc` — НПС (uuid), `@p`/ники не трогаются.

- `settledlands-0.8.0-build292-tp-target-fix.jar` — Drive `18jBFhFrrfDyziqp-oM_5o1SORPWaf1bz` (public);
  `SettledLands-Build292-TpTarget-Source.zip` — Drive `1rYfuWAN3YqxkVwC2Xz3lTphGRDmzdSzT` (public);
  `SHA256SUMS-build292.txt`, `tools/patch_build292.py`, `build292-src/`.
- Гейты: 457 записей / 409 классов, 39 миксинов, 0 новых внешних ссылок, 0 висячих.

## Build 293 (актуально)

Лог `latest-5.log` показал: игра запускалась на 292 (`settledlands-0.8.0-build292-tp-target-fix.jar`),
и всё равно выполнила `/tp @s <uuid> ~ ~5 ~`. То есть `@Redirect` на `String.replaceFirst` в
`NpcActions.command` в этой сборке **не сработал** (молча, без падения). Вывод: не полагаться на
инъекцию там, где можно поправить саму строку.

293 (база — 291, без 292) меняет ОДНУ строку в пуле констант `NpcActions.class`:
`^(tp|teleport)\s+([~^\-0-9].*)` → `^(tp|teleport)\s+[~^\-0-9.]*(\s.*)?` (30 → 35 байт).
Class-файл не содержит абсолютных смещений, поэтому Utf8-запись просто переписывается с новой
длиной (`tools/patch_build293.py: patch_constant`). Читателей ровно два — `command` и
`commandForEntity`; сборщик это проверяет, класс дополнительно парсится `java.lang.classfile` (JDK 25).

- `settledlands-0.8.0-build293-tp-target-guard.jar` — Drive `1XN5-9TrTQkLrg6jNq8BGH96jn9k1IXIi` (public);
  `SettledLands-Build293-TpTargetGuard-Source.zip` — Drive `1DIq9b7owCw2usdsECfOXuHtiPkdqXJ5i` (public);
  `SHA256SUMS-build293.txt`, `README-Build293-RU.md`, `tools/patch_build293.py`.
- Дифф против 291: `~npc/NpcActions.class`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 455 записей / 407 классов, 38 миксинов, 0 новых внешних ссылок, 0 висячих.

## Build 308 (актуально) — откат к нашему виду нод

Пользователь: «откатим все эти изменения, вернёмся к нашему виду нод». Сборка сделана **от jar 304**,
а не патчем 307: воркспейс 306/307, его миксин и библиотека 305 в неё не входят вообще.

- убрано: `NpcWorkspace306` (+`$Link`), `NpcWorkspaceEntry306Mixin`, `NpcLibrary305Mixin`,
  `WorkspaceTheme305`, `NpcNodeLibrary305`, ключи `gui.settledlands.workspace.*`;
- mixins.json — список 304 (21 client), версия/displayName подняты до `0.8.0-build308-nodes`;
- в jar остаётся скин 304: холст в точку, карточки с рамкой, круглые порты, тёплые кабели,
  белая проводка 301/302 и синие провода значения; все инспекторы на месте.

`tools/screen_smoke.py` остаётся в репозитории: на jar без экрана воркспейса он пишет `SKIP`.
Исходники 306/307 остаются в истории git и в `build305-src/src`, в архив 308 не входят.
Гейты: 460 записей / 412 классов, 42 миксина, 1385 внешних ссылок / 0 новых vs 304, verify_jar PASS.

## Build 307 — воркспейс открывается + новый гейт

306 вылетала на первом кадре воркспейса:
`ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6` в
`NpcWorkspace306.drawTopBar` — подпись кнопки читалась как `LABELS[action + 1]`, а в таблице 6
записей; кнопка «Назад» (action 5) просила седьмую. Исправлено: `LABELS[action]` с зажимом в
границы. Рядом: `addNode` зажимает координаты в `MAX_COORD`, `graph.connect(...)` в
`mouseReleased` обёрнут в try/catch, `restore()` игнорирует битый снимок.

**Новый гейт 6 — `tools/screen_smoke.py`**: прогон экрана в обычной JVM без игры.
`build305-src/stubs` компилируются и грузятся как настоящие классы, четыре из них
(`Minecraft`, `Font`, `Screen`, `CompoundTag`) заменяются версиями из `tools/harness`, драйвер
`tools/harness/WorkspaceSmokeTest.java` строит граф со всеми 15 типами + все виды связей и
дёргает экран: construct / init / resize / render ×5 / save-tag / клики / драг / рамка / пан /
колесо / библиотека / поиск / ввод / undo / redo / delete / вкладки / создание графа / ПКМ.
На 306 гейт падает (`FAILURES: 7`) — ровно там, где падала игра; на 307 — 37 шагов, 0 падений.
Гейты: 466 записей / 418 классов, 44 миксина, 1389 внешних ссылок / 0 новых vs 306, verify_jar PASS.

## Build 306 — воркспейс нод = сам редактор

Пользователь: «взять код графа из нод, сделать его копию, скрыть там все существующие графы и
добавить мои». Реализовано своим экраном, G&G — только слой рисования и раскладка (разрешение
авторов заявлено пользователем).

- `npc/client/NpcWorkspace306 extends Screen` — владеет графом: `NpcGraphCanvas` (пан/зум/хиты),
  карточки `WorkspaceTheme305.drawNodeFrame`, порты по `portScreenX/Y`, кабели-безье по связям
  `next / falseNext / varInputNode / choices / triggers / randomBranches`, выбор, рамка,
  перетаскивание (шаг 8), связь порт→порт через `graph.connect(from,to,port)`, резка
  `graph.disconnect`, удаление, undo/redo через `graph.save()`/`NpcDialogueGraph.load`,
  три вкладки, библиотека `NpcNodeLibrary305`, сохранение тем же `NpcNet$CfgSave`.
- `mixin/NpcWorkspaceEntry306Mixin` — `NpcDialogueGraphScreen.open(...)` HEAD-cancellable →
  `new NpcWorkspace306(...)`. Старый экран доступен кнопкой «Старый редактор» (поля редактируются
  там), поэтому ни один инспектор не потерян.
- lang: `gui.settledlands.workspace.{title,legacy,new_graph}`.
- Новые стабы (только для компиляции): `Minecraft`, `CustomPacketPayload`, расширенный `Screen`,
  `CompoundTag` с настоящей сигнатурой `Tag put(String,Tag)` (гейт фазы D поймал рукописный
  `put(String,Object)` — такой сигнатуры в игре нет).

Ещё нет: инспектор полей внутри воркспейса, меню добавления по ПКМ на холсте.
Гейты: 466 записей / 418 классов, 44 миксина, 1389 внешних ссылок / 0 новых vs 305, verify_jar PASS.

## Build 305 — воркспейс нод + библиотека с поиском

Пользователь: 304 — «ты просто перерисовал внешний вид», нужен **код системы нод**. LICENCE G&G
(`Riieno/Gadgets-And-Gizmos`) — All Rights Reserved; пользователь заявил, что разрешение авторов
получено, поэтому работа продолжена, но своими классами: перенесён только слой рисования, наши
ноды остаются нашими.

Новое:
- `npc/client/WorkspaceTheme305` — слой рисования воркспейса: `drawNodeFrame` (рамка, шапка с
  тоном акцента, точка-индикатор), `drawPort` (тёмный ободок + тело + светлое ядро),
  `drawExecPort` (ромб), `drawRow`, `drawPanel`, `drawTitleBar`, `drawRoundedRect`,
  `drawTopRoundedRect`, `drawCircle`, `drawDiamond`, `mix`/`withAlpha`. Палитра — дефолт темы.
- `npc/client/NpcNodeLibrary305` — панель 132 px слева: заголовок «Ноды» + счётчик, строки по
  14 px, прокрутка, `L` — скрыть. Поиск по RU-названию и латинскому алиасу.
- `mixin/NpcLibrary305Mixin` — на **своих** методах экрана: `widgets()` TAIL (невидимый
  `EditBox` в `children()` — не `addRenderableWidget`, гейт ругался на неизвестный внешний API),
  `render` TAIL (панель поверх холста), `mouseClicked` HEAD-cancellable (панель не отдаёт клик
  холсту), `mouseScrolled`, `keyPressed`. Нода создаётся штатным `add(int)` (`@Shadow public
  abstract`).
- `WorkspaceSkin304` переписан на `WorkspaceTheme305`: холст/точки/карточки/порты рисует
  перенесённый слой, палитра — алиасы на `WorkspaceTheme305`.
- lang: `gui.settledlands.workspace.{library,search,empty}` в ru_ru/en_us.

Ещё нет: меню «добавить ноду» с поиском на холсте (этап 2), перетаскивание/связи/инспектор кодом
воркспейса (этап 3).
Гейты: 463 записи / 415 классов, 43 миксина, 1389 внешних ссылок / 0 новых vs 304, verify_jar PASS.

## Build 304 — рабочая область нод как в G&G

Взята палитра/геометрия Advanced Contraption Controller из «Create Aeronautics: Gadgets &
Gizmos» (публичные исходники, `AdvancedControllerV2Theme` + `GraphV2ThemeData.DEFAULT`):
холст #0D1215 + точки #1B2429 через 24 ед., панель #202A2F, рамка #39464C, акцент #C6873F,
текст #E8E0CD, карточка 220×100, шапка 22, порт 10×10.
Реализация: новый обычный класс `WorkspaceSkin304` + миксин `NpcWorkspaceMixin304` на **своих**
методах экрана — `grid` (HEAD, cancellable → тёмный холст + точки) и `drawNode` (TAIL → рамка,
акцентная полоса, круглые порты). Кабели → `WorkspaceSkin304.CABLE` (через существующий
ModifyArg в `NpcWhiteWiring302`). Карточка pursuit перекрашена в палитру темы и зовёт тот же скин.
Ещё нет: перекраска шапок (нужна перерисовка заголовка), библиотека нод с поиском, меню
«добавить ноду» с поиском — это 305/306.
Гейты: 460 записей / 412 классов, 42 миксина, 1385 внешних ссылок / 0 новых vs 303.

## Build 303 — 302 без вылета

302 роняла клиент при открытии диалога с правкой: `Mixin apply ... NpcWhiteWiring302 ...
contains non-private static field COL_CABLE_WHITE:I`. **Mixin-класс не может объявлять статическое
поле, кроме приватного** — Mixin выбрасывает весь класс, и клиент падает на первой загрузке
целевого класса. Цвета (`COL_PORT_WHITE`, `COL_CABLE_WHITE`) теперь читаются из
`PursuitGraphNode272`, в двух белых миксинах полей нет.
Гейт `tools/check_mixin_class.py` дополнен правилом «в миксине нет статических не-приватных полей»
(на 302 он падает, на 300/301 проходит) — теперь такой вылет не пройдёт сборку.
Остальное как в 302: синий проход = код 300, тумблер стопа второй строкой, скорости (преследование
0.4/атрибут, догон ×1.4…3.4), пин «В радиусе» на своём порту.

## Build 302 — синие провода, тумблер стопа, скорости

Синие провода пропали в 301 не из-за синего кода: `settledlands$drawValueWires` /
`settledlands$paintValueWires` в 301 побайтово те же, что в 300. Убил их белый `@Redirect` на
`GuiGraphics.fill` — единственный хук в чужой метод Minecraft, а не в наш класс: цель не нашлась,
Mixin при неattachнутом хуке (require по умолчанию = `defaultRequire` = 1) выбрасывает весь класс,
вместе с синим проходом, который жил в нём же. Урок: **чужой хук не должен жить в одном классе с
синим проходом**; белая проводка вынесена в отдельные классы `NpcWhiteWiring302` (кабели) и `NpcWhitePorts302` (порты,
единственный хук в метод Minecraft) с `require = 0`;
`NpcPursuitValuePinMixin290` — ровно версия 300.
Тумблер «Завершить преследование» теперь второй в панели (`y + 35`, сразу под типом цели), ряды
сдвинуты компактнее; раньше он стоял на `y + 180` и обрезался правилом видимости.
Скорости: преследование = бывшая скорость догона (`walkingNavigationSpeed(true)`, 0.4/атрибут),
догон = она же × `CATCH_UP_EXTRA` (1.4) с ramp по «Ускорению догона» → 0.56…1.36/атрибут.
Пин «В радиусе» снова рисуется на своём порту (`drawPin` по `checkPinScreenX/Y`, радиус 2.0 × зум).
Гейты: 458 записей / 410 классов, 41 миксин, 1385 внешних ссылок / 0 новых vs 301, 0 висячих.

## Build 301 — пины и белая проводка

Синие пины: радиус в единицах карточки (PIN_RADIUS = 2.0) × зум, координаты через `canvas.screenX/Y`
(`drawValuePin`), захват `max(3.0, 5.0 × zoom)` (`pinHitRadius`); пины карточки pursuit рисуются после
её трансформации.
Белая проводка: `@Redirect` на `GuiGraphics.fill(IIIII)V` в `drawNode` — белеет только прямоугольник
10×10 на краю карточки (`isPortSquare`); `@ModifyArg index=6` на `cable`/`curve` в `wires` — все обычные
кабели белые. У трёх хуков `expect = -1` (несколько точек внедрения).

## Build 300 — провод и пин

Симптом: после 299 условие считается верно, но синий провод пропадал/появлялся («лаг»), пин — большой плюс.
Причина: `hasValueWires` читал `inputSource` (поле `varInputNode`), пока связь временно снята на проход
кабелей → «проводов нет» → ничего не рисовалось; то же в `wiredTo`. Плюс непарное скрытие на
`drawNode` для нод pursuit (мост отменяет метод, RETURN не срабатывает).

Фикс: `hasValueWires`/`wiredTo` → `stockInputSource`; скрытие для карточки только для обычных нод;
на `render` HEAD — только восстановление (`resetStockInputHide`); пины — круглая точка r=3 (`drawPin`),
`CHECK_PIN_HIT` 5. Гейты пройдены (456 / 408 классов, 39 миксинов, 1385 ссылок).

## Build 299 — карточка условия и лаг провода

298 не сработала: `PursuitGraphNode272#drawNode` вызывается только для нод pursuit, обычная нода
условия рисуется штатным `NpcDialogueGraphScreen#drawNode`. Лаг в 298 — от снятия связи на весь
кадр (граф менялся под проходом проводов).

299: связь снимается на один вызов `drawNode` (HEAD) и возвращается в RETURN; вокруг `widgets()`;
на проходе `wires()` с `finally`. Счётчик `HIDE_DEPTH` + `beginWiresPass` для сброса зависшего
прохода. Связь с нодой «Переменная» не трогается.

## Build 298 (актуально) — провод не переписывает карточку условия

Симптом: подключение синего провода «В радиусе» → условие показывало `#4 == 0` вместо `? кукт == 0`.
Причина: сервер уже верный (297) — имя переменной берётся у подключённой ноды только если это
нода «Переменная» (тип 4); врала карточка `drawNode`, которая для IF с `varInputNode >= 0` берёт
имя источника, а при пустом имени подставляет `#<id>`. В 290 связь пряталась только на время
`wires()`, карточки рисуются позже.

Фикс 298: связь вынимается из `varInputNode` на весь кадр (`@Inject HEAD/TAIL` на `render`),
синий проход читает источник через `PursuitGraphNode272.stockInputSource`, сохранённая связь
возвращается в том же кадре. Гейты пройдены (456 записей / 408 классов, 39 миксинов, 1385 ссылок).

## Build 297 (актуально) — условие проверяет свою переменную

Главное уточнение от пользователя: нода условия проверяет ТОЛЬКО выставленную в ней переменную
(свой оператор, своё число). Нода «В радиусе» даёт импульс и передаёт найденного игрока, но не
решает, что сравнивать. Сборки 290–295 подменяли проверку на «тело есть/нет» (1/0) — переменная
не читалась вообще. В 297 хук на `evalIfNode` и метод `ifValue` удалены; условие снова читает
свою переменную у игрока цепи (= игрок, найденный нодой радиуса). Пустой радиус — цепь не идёт.

Гейты: 456 записей / 408 классов, 39 миксинов, 1385 ссылок, 0 новых. Diff vs 296:
`~NpcValueSource290` (-ifValue), `~mixin/GuideDialogueValue290` (-settledlands$ifFromValue),
toml, patch-info.

## Build 296 (актуально) — откат 295

295 отменена: хук запускал цепь из пустого радиуса без тела, и это сломало ноду условия.
296 = код 294 без изменений, изменены только `neoforge.mods.toml` и `settledlands-patch-info.txt`
(+0/-0/~0 классов; побайтно совпадает с 294 везде, кроме этих двух записей).
Гейты пройдены: 456 записей / 408 классов, 39 миксинов, 1385 ссылок, 0 новых.

Замысел пользователя (важно, 294/295 его НЕ реализуют): нода «В радиусе» находит игрока и отдаёт
ноде условия id этого игрока; нода условия читает **переменную этого игрока** и по сравнению
выводит True/False. Сейчас 290 подменяет проверку на «тело есть/нет» (1/0) — это не то.
Вопрос открыт: какую именно переменную найденного игрока читает условие и что она должна видеть,
когда игрока нет.

## Build 295 (актуально)

После 294 пользователь сообщил: в цепи «В радиусе» #4 → «Условие» #5 (`#4 == 0`) при пустом радиусе
по-прежнему ничего не печатается. Лог `latest-6.log` показал механизм: нода CHECK — не звено обхода,
а сканер. `tickGraphCheckNodes` каждые 10 тиков печатает `[graph] check node #4 target 0 radius 5
scanning...`, и **только при найденном теле** — `FIRED on <имя>` и `walk starts at node #5 for <имя>`,
то есть цепь стартует со СЛЕДУЮЩЕЙ ноды. При пустом радиусе обхода нет вообще, поэтому правка 294
(внутри `resolveExecutionTarget`) никогда не выполнялась.

295: `GuideNpcEmptyCheck295` (`@Inject HEAD` на `GuideNpc#customServerAiStep` — оттуда и вызывается
`tickGraphCheckNodes`) → `NpcValueSource290#tickEmptyChecks` (раз в 10 тиков, свой кулдаун по ноде). После штатного скана по каждому CHECK-узлу: пусто по `NpcCheckValue289#detected`
(скан 289 без кулдаунов) + нода питает синий вход (`varInputNode == id` у любого узла) + нет своего
`action` + вышел кулдаун ноды (`checkCooldown*20` тиков, `LAST_EMPTY`) + `isIncomingIfSatisfied(null,…)`
→ `GuideDialogue.resolveExecutionTarget(null, npc, null, graph, node.next, new HashSet<>())`. Первый пустой проход
пишет одну строку `[settledlands] [graph] empty radius #N: chain starts at node #M` — доказательство, что
хук в игре работает; дальше молчит.
Действие и триггеры самой ноды на пустом проходе не выполняются; тело «на своём кулдауне» — это
тело, им владеет штатный путь, двойного срабатывания нет. Поведение прочих графов не меняется.

- `settledlands-0.8.0-build295-empty-radius-chain.jar`, `SettledLands-Build295-EmptyRadius-Source.zip`,
  `SHA256SUMS-build295.txt`, `README-Build295-RU.md`, `tools/patch_build295.py`.
- Дифф против 294: `+mixin/GuideDialogueEmptyCheck295.class`, `~npc/NpcValueSource290.class`,
  `~settledlands.mixins.json`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 458 записей / 410 классов, 41 миксин, 1385 внешних ссылок, 0 новых, 0 висячих.
- Урок: у CHECK-ноды ДВА пути — сканер (`tickGraphCheckNodes` → `triggerCheckChain`) и ветка type 5
  внутри `resolveExecutionTarget`. Правки делать в сканере: именно он решает, будет ли цепь вообще.

## Build 294

Цепь «В радиусе» #4 (Цель: Игрок, Радиус 5, Кулдаун 5 с) → «Условие» #5 (`#4 == 0`),
True → `say true`, False → `say false`. Кто-то в радиусе — печатается `false`; никого — не происходило ничего.

Причина (по байткоду 293): в `GuideDialogue#resolveExecutionTarget` нода CHECK (type 5) — ворота.
`checkProximityCondition` false → `curId = -1` сразу, обход заканчивается на #4, условие #5 не выполняется
и ни разу не видит 0. Значение ноды для синего провода считается отдельно
(`NpcValueSource290#ifValue` → `NpcCheckValue289#detected`) и пустой радиус = 0 — ответ просто не доезжал.

294: `GuideDialogueCheckGate294` (`@Redirect` на `checkProximityCondition` в `resolveExecutionTarget`) +
`NpcValueSource290#passThroughWhenValue`. Правило: непрошедшая проверка пропускает обход дальше по `next`
только если нода питает синий вход (некто в графе имеет `varInputNode == id`), нода — источник
(CHECK/DAMAGE) и у неё нет своего действия; своё действие и её список триггеров при этом не выполняются
(метка `PENDING`/`PENDING_TRIGGERS` гасится в `triggerBlocked`). Всё остальное — как было.
Нода CHECK со своим действием остаётся воротами (непрошедшая проверка обрывает цепь, как раньше).

- `settledlands-0.8.0-build294-radius-if-branch.jar` — Drive `1nyhbbinE4s-xFSxCKJx3vFS3aC3fBtAu` (public);
  `SettledLands-Build294-RadiusIfBranch-Source.zip` — Drive `1APfyH5hc0V4oKN6hr_XICYi57xt1bTjF` (public);
  `SHA256SUMS-build294.txt`, `README-Build294-RU.md`, `tools/patch_build294.py`.
- Дифф против 293: `+mixin/GuideDialogueCheckGate294.class`, `~npc/NpcValueSource290.class`,
  `~settledlands.mixins.json`, `~neoforge.mods.toml`, `~settledlands-patch-info.txt`.
- Гейты: 456 записей / 408 классов, 39 миксинов (20 common + 18 client), 1385 внешних ссылок, 0 новых, 0 висячих.

## Урок Build 292 → 293

Лог `latest-5.log` (запуск на 292): `НПС Проводник: выполнена команда триггера: «/tp @s 8efbfa3e-… ~ ~5 ~»`
→ `@Redirect` на `String.replaceFirst` в `NpcActions.command` **не дал эффекта** (игра не падала, значит
инъекция формально применилась либо не зарегистрировалась — вопрос открыт; точный ответ даст 293).
Вывод на будущее: если правку можно выразить заменой строки/константы в СВОЁМ классе — делать так, а не
инъекцией; инъекция в свой класс у нас уже дважды вела себя не так, как ожидалось (290 — fatal, 292 — молча).

Техника: Utf8-запись пула констант можно переписать с новой длиной (`tools/patch_build293.py`), в class-файле
нет абсолютных смещений, остальные байты не двигаются. Проверка: `python3 - <<'PY'` парсит класс через
`java.lang.classfile` (JDK 25) — `parsed ok, methods=20`, строка в пуле `cp#181`.
