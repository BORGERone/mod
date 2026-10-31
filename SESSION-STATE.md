# SESSION-STATE — SettledLands

Обновлено: 2026-10-31. Ветка: `arena/b98b1cb0-mod` (имя ветки задаётся платформой, переименовать нельзя).

## Текущая сборка

`settledlands-0.8.0-build294-radius-if-branch.jar` — Build 294, все 5 гейтов PASS.
Цепочка: 272 (база с Drive) → 273 (`tools/patch_build273.py`) → 274 (`tools/patch_build274.py`)
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

## Build 294 (актуально)

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

- `settledlands-0.8.0-build294-radius-if-branch.jar`, `SettledLands-Build294-RadiusIfBranch-Source.zip`,
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
