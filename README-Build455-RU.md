# Settled Lands — Build 455 (`0.8.0-build455-scrollable-inspector-desc`)

Патч UX боковой панели поверх Build 454 (собран на чистой базе **Build 452**). Прошёл полный комплект проверок (`build455_check`: `642 + 45 + 62 + 12 = 761` живая/статическая проба, `verify_jar --base Build452`, `mixin_clash_check`, `invoke_kind_check`: 7333 цели OK, `check_mixin_class`: 157 миксинов / 0 новых внешних API / 0 висячих ссылок).

- **Файл мода**: `settledlands-0.8.0-build455-scrollable-inspector-desc.jar` (`4 451 486` байт, `840` записей / `785` классов)
- **SHA-256**: `dd80e325925b2cadc4da0ad9e6a7c72a512e53bd0d652405a5f269ce81d858f6`
- **Архив исходников**: `SettledLands-Build455-ScrollableInspectorDesc-Source.zip`

---

## Что изменено в Build 455

### Подсказка/описание в инспекторе — в общем скроллируемом списке снизу
- **Убрано постоянное закрепление внизу экрана**: `GraphInspectorDescMixin453` больше не сжимает `inspectorH` в `widgets()`, `mouseScrolled()`, `renderBackground()` и `render()` — вся высота боковой панели полностью доступна настройкам ноды.
- **Самый нижний элемент общего списка**:
  - Через `GraphInspectorDesc453.ScrollDescHost` и `GraphClickDragMixin453` в конце `widgets()` (после всех стоковых и кастомных инспекторов: `Pursuit`, `BlockName`, `Teleport`, `LootPool`, `TrialControl`, `TrialModule`, `VarDataInspector453`, `GraphRangeInputs450Mixin`) вычисляется точная нижняя граница контента (`computeContentEndOffset`: учитывает `sidebarHeight`, не запаркованные `inspectorWidgets` и `NpcDialogueGraphScreen$Label`, включая `Pursuit`, где `sidebarHeight` ранее оставался `0`).
  - Карточка **«Описание»** помещается сразу под последним пунктом инспектора, отображает полный перенесённый текст без обрезки по трети высоты экрана и увеличивает `sidebarHeight` ровно на свою высоту и отступы.
  - Рендерится в `NpcDialogueGraphScreen.render()` прямо перед `GuiGraphics.disableScissor()`, поэтому скроллится синхронно с общим списком, автоматически обрезается рамкой боковой панели сверху и снизу и остаётся позади выпадающих меню, автодополнения команд, контекстного меню и всплывающих тултипов.

---

## Включены все исправления Build 453 и Build 454
1. **Runtime-фикс Build 454**: вынос `BlockWalkFrame` / `NpcResolveFrame` из пакета миксинов (`0` `$`-классов и `0` `invokedynamic` в миксинах исполнения — без `IllegalClassLoadError`), фикс `GuideDialogue` с `contextEntity` / рекурсивным `RADIUS` / `IF == a-b`, фикс `@Redirect` в `BlockTrigger351` и фикс обратных оранжевых проводов `IF`.
2. **UX и переменные Build 453**: порог клика/перетаскивания `4 px`, единая подсветка/резка/перегибы цветных/синих/янтарных/триал-проводов, заголовок `Цель #id`, 14 математических операций `VARIABLE` с оранжевым и синим входами и нода **«Переменная (UE5)»** во всех 6 редакторах.
