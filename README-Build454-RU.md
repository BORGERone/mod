# Settled Lands — Build 454 (`0.8.0-build454-mixin-runtime-and-graph-polish`)

Хотфикс и полировка поверх Build 453, собранная на чистой базе **Build 452**. Прошла полный комплект статических и runtime-проверок (`build454_check`: `642 + 45 + 62 + 9 = 758` проверок, `verify_jar --base Build452`, `mixin_clash_check`, `invoke_kind_check`: 7333 вызовов OK, `check_mixin_class`: 157 миксинов с жёсткой проверкой Phase A2, 0 висячих ссылок).

- **Файл мода**: `settledlands-0.8.0-build454-mixin-runtime-and-graph-polish.jar` (`4 451 493` байт, `840` записей / `785` классов)
- **SHA-256**: `1ed255bb02c2b9e3242e1a5c51cf6ef3022b21d64ea729ce9f0f25eb1c6ff89f`
- **Архив исходников**: `SettledLands-Build454-MixinRuntimeAndGraphPolish-Source.zip`

---

## Что исправлено в Build 454

### 1. Исправлен краш запуска/клика `IllegalClassLoadError` (`VarExecutionBlockMixin453$Frame`)
- **Причина падения в Build 453**: вложенный класс `VarExecutionBlockMixin453$Frame` (и `ThreadLocal.withInitial(ArrayDeque::new)` / `invokedynamic`) находился внутри пакета Sponge Mixin `dev.settledlands.mixin`, из-за чего Mixin бросал `IllegalClassLoadError` при первой загрузке `BlockTrigger351` (в т.ч. по ПКМ по триггер-блоку/сундуку/двери/триалу).
- **Исправление**:
  - Вся структура кадра выполнения вынесена наружу в `VarScopeStore453.BlockWalkFrame` (`dev.settledlands.npc`).
  - `VarExecutionBlockMixin453` переведён на ленивый `new ThreadLocal<>()` без `invokedynamic` и без вложенных/лямбда-классов.

### 2. Исправлен `GuideDialogue` для сессий, `contextEntity`, рекурсивных вееров `RADIUS` и диапазонов `IF == a-b`
- **В `VarExecutionNpcMixin453` + `VarScopeStore453.NpcResolveFrame`**:
  - Перехват `GuideDialogue#resolveExecutionTarget` заменён на `@Inject(HEAD/RETURN)` со стеком `NpcResolveFrame` (учитывается и ветка `session != null`, где ранее `relationTo` пропускался, а также корректно изолируются рекурсивные переходы через `RADIUS`).
  - `contextEntity` теперь пробрасывается в `VarScopeStore453.executeVariableForNpc(...)` и `readIfLeftValueForNpc(...)`.
  - `VarExecutionNpcMixin453` поставлен перед `GuideRange450Mixin` в `settledlands.mixins.json` и сохраняет полный паритет с `GraphRanges450` (`IF == a-b`, `IF != a-b`, `rerollNode`) вместе с оранжевыми/синими входами UE5.
- **В `VarExecutionBlockMixin453`**:
  - `value(...)` и `valueServerOnly(...)` перехватываются через `@Redirect` в точках вызова внутри `walk(...)` и `startServerOnly(...)` с явной поддержкой `GraphRanges450` (`IF == a-b` / `IF != a-b`), что полностью исключает двойную конвертацию с `TriggerRange450Mixin` независимо от порядка миксинов.

### 3. Усилен резерв футера «Описание» для всех кастомных инспекторов
- В `GraphInspectorDesc453.FooterReserveHost`, `GraphInspectorDescMixin453` и `GraphClickDragMixin453`:
  - `widgets() @HEAD` по-прежнему резервирует высоту под блок **«Описание»** до того, как кастомные инспекторы скроют `selected`.
  - Финальная обрезка видимости `inspectorWidgets` и восстановление `inspectorH` теперь выполняются в `GraphClickDragMixin453` на `widgets() @RETURN` — уже **после** всех кастомных инспекторов (`Pursuit`, `Teleport`, `TrialControl`, `TrialModule`, `LootPool`, `VarDataGraphUi453Mixin`, `GraphRangeInputs450Mixin`).

### 4. Сканирование обратных оранжевых проводов `IF` (`vin == node.next`)
- В `WireHover453.scanNearestWire` удалено устаревшее ограничение `&& vin != node.next`: оранжевый провод значения, подключённый к ноде `IF` даже при циклическом возврате `IF -> Var -> IF`, теперь корректно подсвечивается, режется и принимает точки перегиба.

### 5. Усилена проверка `tools/check_mixin_class.py` (Phase A2)
- Автоматический гейт сборки теперь проверяет каждый `.class` внутри `dev/settledlands/mixin/*` и `dev/settledlands/mixin/compat/*` (даже не перечисленные в `mixins.json`) и жёстко блокирует:
  - любые `$` (вложенные/анонимные классы в пакете миксинов),
  - любые классы без `@Mixin`,
  - любые прямые ссылки между миксин-классами (кроме `@Mixin`-интерфейсов-аксессоров) или из обычных классов в пакет миксинов,
  - `invokedynamic` в `VarExecutionBlockMixin453` и `VarExecutionNpcMixin453`.

---

## Полный состав возможностей Build 453 + 454
1. Блок **«Описание»** перенесён с канваса в правый нижний угол инспектора с авто-резервом места под скролл и кастомные панели.
2. Порог клика/переноса `4 px` в `GraphClickDragMixin453`: перетаскивание карточки не открывает и не переключает инспектор.
3. Единая подсветка, резка (`СКМ` и `Shift + ЛКМ`), точки перегиба (`2x ЛКМ`), ждущие хвосты и удаление точек ПКМ/СКМ для синих, оранжевых, янтарных и триал-проводов + корректный заголовок `Цель #id`.
4. `VARIABLE`: 14 математических операций (`+=`, `-=`, `*=`, `/=`, `=`, `^=`, `%`, `+%`, `-%`, `mod`, `min`, `max`, `|x|`, `-x`) + независимые оранжевый и синий входы + локальные переменные (`VarScopeStore453`) во всех графах.
5. Нода **«Переменная (UE5)»** (`+ Переменная (UE5)`) во всех 6 редакторах (имя, числовое значение / константа, область видимости Personal / Shared / Local, оранжевый пин).
