"""Build 457:
Built cleanly on top of Build 452 base JAR (incorporating all Build 453-456 features & fixes):
1. World-wide Variable Catalog ("Переменные мира") & inline gear / dropdown picker:
   - `VarCatalogStore457` (SavedData "settledlands_var_catalog457") discovers variables across all
     loaded dimensions (NpcPoints personal/shared, NPC/block/door/chest/trial graphs, catalog edits),
     protects foreign object-local variables from being modified/bound by another entity/block, and
     purges deleted/renamed scoped values cleanly (`NpcPointsCatalogMixin457` / `NpcPointsRemoveBridge457`).
   - `VarPoolEditorScreen457` provides full CRUD (Search, All/Shared/Personal/Local filters, Hide
     Foreign Locals toggle, New/Edit/Save/Apply to Node, 2-click Delete confirm), synchronized
     silently via `VarCatalogViewMixin457` / `VarCatalogEditMixin457` / `VarCatalogConfigMixin457`.
   - `VarDataNode453`, `VARIABLE` ("Мат выражение"), and `IF` ("Условие") gain an inline catalog
     dropdown + `LootPoolGearButton440` gear button (`VarDropdownOverlayMixin457` / `VarDataInspector453`).
2. Local variable scope default on new Variable nodes (`VarDataNode453.varLocal = true`).
3. Blue variable data pin & wiring on Variable (`(220, 92)` output):
   - Variable nodes connect via blue cables to `Pursuit`, `IF`, `VARIABLE`, `LINE`, `BUTTON`,
     `TRIGGER`, and `TeleportNode424` (`PursuitGraphNode272`, `NpcPursuitValuePinMixin290`,
     `TeleportValuePinMixin457`, `VarDataCard453`, `WireHover453`, `VarGraphIoMixin453`).
   - Dynamic right-hand `IF` comparisons against blue-wired variables (`VarExecutionNpcMixin453` /
     `VarExecutionBlockMixin453`).
   - Placeholder substitution `%<varName>%`, `%var%`, `%value%` across `LINE`, `BUTTON`, `NpcActions`
     (`fill`/`run`/`runSingle`/`runForEntity`), `TeleportNode424` coordinates (`isCoordInput` allows
     `%var%`), `BlockTalk358`, `NpcProfile.fill`, and `BlockTrigger351` (`VarActionsSubstituteMixin457`,
     `BlockTalkVarMixin457`, `NpcProfileFillVarMixin457`, `VarValueSourceBridgeMixin453`).
"""

import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent

BASE_VERSION = "0.8.0-build452-conditional-pools-and-clean-catalog"
NEW_VERSION = "0.8.0-build457-var-catalog-and-blue-var-wiring"
OUT_NAME = "settledlands-0.8.0-build457-var-catalog-and-blue-var-wiring.jar"

STUB_LAYERS = [
    ROOT / "tools" / "api271",
    ROOT / "build303-src" / "stubs",
    ROOT / "build303-src" / "stubs396-src",
    ROOT / "build303-src" / "stubs420-src",
    ROOT / "build303-src" / "api-src",
    ROOT / "build303-src" / "api3-src",
    ROOT / "build303-src" / "api4-src",
    ROOT / "build303-src" / "api5-src",
    ROOT / "build303-src" / "api416-src",
    ROOT / "build303-src" / "stubs425-src",
    ROOT / "build303-src" / "stubs436-src",
    ROOT / "build303-src" / "stubs438-src",
    ROOT / "build303-src" / "stubs439-src",
    ROOT / "build303-src" / "stubs440-src",
    ROOT / "build303-src" / "stubs443-src",
    ROOT / "build303-src" / "stubs446-src",
    ROOT / "build303-src" / "stubs449-src",
]

SOURCE_FILES = [
    # Common & runtime
    "dev/settledlands/npc/VarDataNode453.java",
    "dev/settledlands/npc/VarScopeStore453.java",
    "dev/settledlands/npc/RadiusZone456.java",
    "dev/settledlands/npc/NpcWirePoints321.java",
    "dev/settledlands/npc/NpcWireJoin322.java",
    "dev/settledlands/npc/NpcGraphClipboard318.java",
    "dev/settledlands/npc/TeleportNode424.java",
    "dev/settledlands/trial/TrialControlNode446.java",
    "dev/settledlands/npc/NpcPointsRemoveBridge457.java",
    "dev/settledlands/npc/VarCatalogStore457.java",
    # Common mixins
    "dev/settledlands/mixin/VarNodeEntityInputMixin453.java",
    "dev/settledlands/mixin/VarGraphIoMixin453.java",
    "dev/settledlands/mixin/VarExecutionNpcMixin453.java",
    "dev/settledlands/mixin/VarExecutionBlockMixin453.java",
    "dev/settledlands/mixin/VarActionsSilentMixin453.java",
    "dev/settledlands/mixin/VarValueSourceBridgeMixin453.java",
    "dev/settledlands/mixin/RadiusZoneNodeMixin456.java",
    "dev/settledlands/mixin/RadiusZonePassMixin456.java",
    "dev/settledlands/mixin/RadiusZoneLegacy320Mixin456.java",
    "dev/settledlands/mixin/RadiusZoneNpcCheckMixin456.java",
    "dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456.java",
    "dev/settledlands/mixin/NpcPointsCatalogMixin457.java",
    "dev/settledlands/mixin/VarCatalogViewMixin457.java",
    "dev/settledlands/mixin/VarCatalogEditMixin457.java",
    "dev/settledlands/mixin/VarActionsSubstituteMixin457.java",
    "dev/settledlands/mixin/BlockTalkVarMixin457.java",
    "dev/settledlands/mixin/NpcProfileFillVarMixin457.java",
    # Client helpers
    "dev/settledlands/npc/client/GraphInspectorDesc453.java",
    "dev/settledlands/npc/client/VarDataCard453.java",
    "dev/settledlands/npc/client/VarDataInspector453.java",
    "dev/settledlands/npc/client/WireHover453.java",
    "dev/settledlands/npc/client/PursuitGraphNode272.java",
    "dev/settledlands/npc/client/NpcWirePointsUi321.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
    "dev/settledlands/npc/client/RadiusZoneUi456.java",
    "dev/settledlands/npc/client/VarCatalogClient457.java",
    "dev/settledlands/npc/client/VarPoolEditorScreen457.java",
    # Client mixins
    "dev/settledlands/mixin/GraphCardFooter448Mixin.java",
    "dev/settledlands/mixin/AutoAiFooter448Mixin.java",
    "dev/settledlands/mixin/NpcWirePointsMixin321.java",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.java",
    "dev/settledlands/mixin/CardEventPin398Mixin.java",
    "dev/settledlands/mixin/NpcTrialSocket446Mixin.java",
    "dev/settledlands/mixin/TrialModuleSocket447Mixin.java",
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.java",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.java",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.java",
    "dev/settledlands/mixin/NpcTrialPalette446Mixin.java",
    "dev/settledlands/mixin/VarDataMenuMixin453.java",
    "dev/settledlands/mixin/VarDataGraphUi453Mixin.java",
    "dev/settledlands/mixin/NpcRadiusRow355Mixin.java",
    "dev/settledlands/mixin/GraphInspectorDescMixin453.java",
    "dev/settledlands/mixin/GraphClickDragMixin453.java",
    "dev/settledlands/mixin/VarCatalogConfigMixin457.java",
    "dev/settledlands/mixin/VarDropdownOverlayMixin457.java",
    "dev/settledlands/mixin/TeleportValuePinMixin457.java",
]

SESSION_STUB_MIXIN = "dev/settledlands/mixin/VarExecutionNpcMixin453.java"

COMMON_MIXINS_457 = [
    "VarNodeEntityInputMixin453",
    "VarGraphIoMixin453",
    "VarExecutionNpcMixin453",
    "VarExecutionBlockMixin453",
    "VarActionsSilentMixin453",
    "VarValueSourceBridgeMixin453",
    "RadiusZoneNodeMixin456",
    "RadiusZonePassMixin456",
    "RadiusZoneLegacy320Mixin456",
    "RadiusZoneNpcCheckMixin456",
    "RadiusZoneNpcDialogueMixin456",
    "NpcPointsCatalogMixin457",
    "VarCatalogViewMixin457",
    "VarCatalogEditMixin457",
    "VarActionsSubstituteMixin457",
    "BlockTalkVarMixin457",
    "NpcProfileFillVarMixin457",
]

CLIENT_INSERT_MIXINS_457 = (
    "NpcWirePointsMixin321",
    "GraphInspectorDescMixin453",
    "VarDataMenuMixin453",
    "TeleportValuePinMixin457",
    "VarDataGraphUi453Mixin",
    "VarCatalogConfigMixin457",
    "VarDropdownOverlayMixin457",
    "GraphClickDragMixin453",
)

MARKERS = {
    "dev/settledlands/npc/VarDataNode453.class": ("applyMathOp", "isVarDataNode", "isBlueCapableSource"),
    "dev/settledlands/npc/VarScopeStore453.class": (
        "executeVariableForNpc",
        "executeVariableForBlock",
        "substituteVarTokens",
        "readIfRightValueForNpc",
    ),
    "dev/settledlands/npc/TeleportNode424.class": ("isCoordInput", "formatSummaryCoords"),
    "dev/settledlands/npc/VarCatalogStore457.class": ("settledlands_var_catalog457", "var457.save", "var457.delete"),
    "dev/settledlands/npc/NpcPointsRemoveBridge457.class": ("settledlands457$removeVar",),
    "dev/settledlands/mixin/NpcPointsCatalogMixin457.class": ("settledlands457$removeVar",),
    "dev/settledlands/mixin/VarCatalogViewMixin457.class": ("settledlands457$attachCatalog", "buildClientTag"),
    "dev/settledlands/mixin/VarCatalogEditMixin457.class": ("isCatalogOp", "settledlands457$handleCatalogEdit"),
    "dev/settledlands/mixin/VarActionsSubstituteMixin457.class": ("SUBST_GUARD", "settledlands457$substRunSingle"),
    "dev/settledlands/mixin/BlockTalkVarMixin457.class": ("settledlands457$blockWithBodyVars", "settledlands457$enterBlockChoices"),
    "dev/settledlands/mixin/NpcProfileFillVarMixin457.class": ("settledlands457$fillProfileVars",),
    "dev/settledlands/mixin/VarExecutionNpcMixin453.class": ("settledlands$evalIfResolve453", "settledlands457$enterSendGraphVars"),
    "dev/settledlands/mixin/VarExecutionBlockMixin453.class": ("settledlands457$evalBlockCondPlayer", "settledlands457$evalBlockCondServer"),
    "dev/settledlands/mixin/VarValueSourceBridgeMixin453.class": ("settledlands457$lineTextBlueVarHead", "settledlands457$allowBlueVarTrigger"),
    "dev/settledlands/npc/client/VarCatalogClient457.class": ("buildDropdownOptions", "handleDropdownPopupClick"),
    "dev/settledlands/npc/client/VarPoolEditorScreen457.class": ("applyToNodeAndClose", "handleDeleteClick"),
    "dev/settledlands/mixin/VarCatalogConfigMixin457.class": ("settledlands457$readCatalog",),
    "dev/settledlands/mixin/VarDropdownOverlayMixin457.class": ("settledlands457$drawVarDropdownOverlay", "settledlands457$clickVarDropdownOverlay"),
    "dev/settledlands/mixin/TeleportValuePinMixin457.class": ("settledlands$drawTeleportBlueInputPin457",),
    "dev/settledlands/npc/client/VarDataCard453.class": ("drawVarDataCard", "drawVariableCard", "findBlueSourceId"),
    "dev/settledlands/npc/client/VarDataInspector453.class": ("injectCatalogVarPicker", "enhanceBlueInputNode"),
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("isValueSourceNode", "isValueInputNode"),
    "dev/settledlands/npc/RadiusZone456.class": ("drawSquareOutline", "isInsideBox", "isSquare"),
}

LANG_SET_RU = {
    "gui.settledlands.desc453.footer_title": "Описание",
    "gui.settledlands.graph.variable": "Мат выражение",
    "gui.settledlands.graph.add_variable": "+ Мат выражение",
    "gui.settledlands.block.palette": "У блока доступны: Реплика, Триггер, Условие If, Мат выражение, Случайность, В радиусе",
    "gui.settledlands.tip448.menu.node_4": "Добавить узел «Мат выражение» для вычисления и изменения переменной (+, -, *, /, =, ^, %, mod, min/max).",
    "gui.settledlands.tip448.node.n_4": "Подсказка: задайте имя переменной (или подключите оранжевую «Переменную» ко входу/выходу) и выберите операцию справа.",
    "gui.settledlands.desc448.card.node_4": "Выполняет мат. выражение над переменной (+, -, *, /, =, ^, %, mod, min/max) с поддержкой оранжевого и синего входов.",
    "gui.settledlands.desc448.card.var453": "Нода переменной (чтение и запись, без белых контактов): оранжевые контакты передают числа в выражения, а синий выход на правой грани передаёт значение в Телепорт, Реплики (%имя%, %var%), Триггеры, Преследование и Условия If.",
    "gui.settledlands.var453.menu": "+ Переменная",
    "gui.settledlands.var453.title": "Переменная",
    "gui.settledlands.var453.added": "Добавлен узел «Переменная»",
    "gui.settledlands.var453.card_name": "Имя:",
    "gui.settledlands.var453.const_mode": "Без имени (локальное значение)",
    "gui.settledlands.var453.card_val": "Значение:",
    "gui.settledlands.var453.numbers_only": "Целые числа · %var% по синему",
    "gui.settledlands.var453.name": "Имя переменной (пусто = внутренняя память узла)",
    "gui.settledlands.var453.value": "Числовое значение (начальное / константа)",
    "gui.settledlands.var453.scope": "Область видимости",
    "gui.settledlands.var453.scope_personal": "Персональная",
    "gui.settledlands.var453.scope_shared": "Общая",
    "gui.settledlands.var453.scope_local": "Локальная",
    "gui.settledlands.var453.btn_personal": "Персональная (у каждого игрока)",
    "gui.settledlands.var453.btn_shared": "Общая (единая на весь мир)",
    "gui.settledlands.var453.btn_local": "Локальная (только этот объект/NPC)",
    "gui.settledlands.var453.btn_local_toggle": "Локальная (объект)",
    "gui.settledlands.var453.btn_local_on": "Локальная (объект): ВКЛ",
    "gui.settledlands.var453.btn_local_off": "Локальная (объект): ВЫКЛ",
    "gui.settledlands.var453.wired_count": "Подключено узлов (оранж. выход): %s",
    "gui.settledlands.var453.wired_none": "Оранжевый выход не подключён",
    "gui.settledlands.var453.btn_unlink": "Отвязать исходящие оранж. провода",
    "gui.settledlands.var453.op_label": "Математическая операция",
    "gui.settledlands.var453.op_0": "+=  Сложение (прибавить)",
    "gui.settledlands.var453.op_1": "-=  Вычитание (вычесть)",
    "gui.settledlands.var453.op_2": "*=  Умножение (умножить)",
    "gui.settledlands.var453.op_3": "/=  Деление (разделить)",
    "gui.settledlands.var453.op_4": "=   Присвоение (задать)",
    "gui.settledlands.var453.op_5": "^=  Возведение в степень",
    "gui.settledlands.var453.op_6": "%   Процент от текущего числа",
    "gui.settledlands.var453.op_7": "+%  Прибавить процент от числа",
    "gui.settledlands.var453.op_8": "-%  Вычесть процент от числа",
    "gui.settledlands.var453.op_9": "mod Остаток от деления",
    "gui.settledlands.var453.op_10": "min Минимум (не больше числа)",
    "gui.settledlands.var453.op_11": "max Максимум (не меньше числа)",
    "gui.settledlands.var453.op_12": "|x| Модуль числа (абсолютное)",
    "gui.settledlands.var453.op_13": "-x  Смена знака (инверсия)",
    "gui.settledlands.var453.orange_operand": "Оранжевый: #%s (%s) → операнд",
    "gui.settledlands.var453.orange_target": "Оранжевый: #%s (%s) → меняет её",
    "gui.settledlands.var453.orange_linked_in": "Оранжевый вход: #%s (%s)",
    "gui.settledlands.var453.orange_none": "Оранжевый вход: не подключён",
    "gui.settledlands.var453.btn_unlink_orange": "Отвязать оранжевый вход",
    "gui.settledlands.var453.blue_linked": "Синий вход (субъект): #%s",
    "gui.settledlands.var453.blue_none": "Синий вход: не подключён",
    "gui.settledlands.var453.btn_unlink_blue": "Отвязать синий вход",
    "gui.settledlands.var453.blue_out_card": "синий → %s",
    "gui.settledlands.var453.blue_out_count": "Синий выход: подключено %s",
    "gui.settledlands.var453.btn_unlink_blue_out": "Отвязать синие выходы",
    "gui.settledlands.var453.blue_if_var_linked": "Синий операнд (справа): #%s (%s)",
    "gui.settledlands.var453.blue_text_linked": "Синий вход: Переменная #%s (%s)",
    "gui.settledlands.tip448.menu.var453": "Создать узел «Переменная» (по умолчанию локальный) с оранжевыми и синими портами данных — без белых контактов.",
    "gui.settledlands.tip448.node.var453": "Переменная: левый оранжевый вход меняет число, правый оранжевый отдаёт в математику/условия, правый синий выход подставляет %имя% и %var% в Телепорт, Реплики, Триггеры, Преследование и Условие If.",
    "gui.settledlands.tip448.var453_op": "Выберите математическую операцию для ноды «Мат выражение».",
    "gui.settledlands.tip448.pin.var453_out_title": "Оранжевый выход (число / переменная)",
    "gui.settledlands.tip448.pin.var453_out": "Тяните провод в оранжевый вход «Переменной», «Мат выражения» или «Условие If». ПКМ — отвязать провода.",
    "gui.settledlands.tip448.pin.var453_blue_out_title": "Синий выход переменной (%var%)",
    "gui.settledlands.tip448.pin.var453_blue_out": "Тяните в синий вход Телепорта (координаты %x%), Реплики/Кнопки/Триггера (%имя% / %var%), Условия If (динамическое сравнение) или Преследования. ПКМ — отвязать.",
    "gui.settledlands.tip448.pin.blue_in_title": "Синий вход (переменная / цель)",
    "gui.settledlands.tip448.pin.blue_in": "Принимает синий провод от «Переменной» (для %имя%, %var% и динамического правого операнда If) или цель от «В радиусе».",
    "gui.settledlands.tip448.pin.var_orange_title": "Оранжевый вход (переменная / число)",
    "gui.settledlands.tip448.pin.var_orange_in": "Принимает оранжевый провод от узла «Переменная» или «Мат выражение».",
    "gui.settledlands.tip448.pin.var_blue_title": "Синий вход (цель / переменная)",
    "gui.settledlands.tip448.pin.var_blue_in": "Определяет игрока/сущность для персональной переменной или динамическую «Переменную».",
    # Variable Catalog (Build 457)
    "gui.settledlands.var.scope.local": "Локальная",
    "gui.settledlands.var.scope.personal": "Персональная",
    "gui.settledlands.var.scope.shared": "Общая",
    "gui.settledlands.var.scope.local.desc": "Привязана к конкретному NPC или объекту (блоку/двери/сундуку/спавнеру).",
    "gui.settledlands.var.scope.personal.desc": "Хранится отдельно у каждого игрока мира.",
    "gui.settledlands.var.scope.shared.desc": "Единая глобальная переменная для всего мира.",
    "gui.settledlands.var_catalog.title": "Каталог переменных мира",
    "gui.settledlands.var_catalog.gear_tip": "Открыть каталог переменных мира (создание, правка, удаление)",
    "gui.settledlands.var_catalog.dropdown_pick": "+ Каталог / создать переменную...",
    "gui.settledlands.var_catalog.create_row_tip": "Открыть каталог и создать новую переменную",
    "gui.settledlands.var_catalog.edit_row_tip": "Открыть и редактировать «%s» в каталоге",
    "gui.settledlands.var_catalog.scope_tag.local": "Лок",
    "gui.settledlands.var_catalog.scope_tag.personal": "Перс",
    "gui.settledlands.var_catalog.scope_tag.shared": "Общ",
    "gui.settledlands.var_catalog.locked_badge": "Чуж.лок",
    "gui.settledlands.var_catalog.other_object": "другой объект",
    "gui.settledlands.var_catalog.unavailable_tip": "Локальная переменная принадлежит другому объекту (%s) — только просмотр",
    "gui.settledlands.var_catalog.current_owner": "Текущий объект: %s",
    "gui.settledlands.var_catalog.empty": "Переменные не найдены",
    "gui.settledlands.var_catalog.search": "Поиск переменной...",
    "gui.settledlands.var_catalog.filter_all": "Все",
    "gui.settledlands.var_catalog.only_avail": "Скрыть чуж.: ВКЛ",
    "gui.settledlands.var_catalog.show_all": "Скрыть чуж.: ВЫКЛ",
    "gui.settledlands.var_catalog.btn_new": "+ Новая",
    "gui.settledlands.var_catalog.btn_save": "Сохранить",
    "gui.settledlands.var_catalog.btn_apply": "Выбрать в узел",
    "gui.settledlands.var_catalog.btn_delete": "Удалить",
    "gui.settledlands.var_catalog.btn_delete_confirm": "Подтвердить!",
    "gui.settledlands.var_catalog.btn_close": "Закрыть",
    "gui.settledlands.var_catalog.name_label": "Имя переменной",
    "gui.settledlands.var_catalog.value_label": "Значение (целое число)",
    "gui.settledlands.var_catalog.scope_label": "Область видимости",
    "gui.settledlands.var_catalog.desc_label": "Заметка / описание",
    "gui.settledlands.var_catalog.owner_row": "Владелец: %s",
    "gui.settledlands.var_catalog.status_new": "Создание новой переменной (локальной по умолчанию)",
    "gui.settledlands.var_catalog.status_saved": "Переменная «%s» сохранена",
    "gui.settledlands.var_catalog.status_deleted": "Переменная «%s» удалена",
    "gui.settledlands.var_catalog.confirm_delete": "Нажмите ещё раз для удаления «%s»",
    "gui.settledlands.var_catalog.err_empty_name": "Введите корректное имя переменной",
    "gui.settledlands.var_catalog.err_foreign_local": "Нельзя изменять локальную переменную чужого объекта",
    # Square zone keys preserved from 456
    "gui.settledlands.radius_shape_btn": "Форма зоны",
    "gui.settledlands.radius_shape_circle": "Форма зоны: Круг",
    "gui.settledlands.radius_shape_square": "Форма зоны: Квадрат",
    "gui.settledlands.radius_shape_circle_short": "Круг",
    "gui.settledlands.radius_shape_square_short": "Квадрат",
    "gui.settledlands.tip448.rad.visible_on": "Граница зоны отображается в мире частицами. Нажмите, чтобы скрыть.",
    "gui.settledlands.tip448.rad.visible_off": "Граница зоны скрыта в мире. Нажмите, чтобы показать контур.",
    "gui.settledlands.tip448.rad.shape_circle": "Круглая (сферическая) зона радиуса R вокруг центра. Нажмите, чтобы переключить на квадратную (±R по X/Y/Z).",
    "gui.settledlands.tip448.rad.shape_square": "Квадратная зона (куб ±R блоков по X/Y/Z, совпадает с контуром квадрата). Нажмите, чтобы переключить на круглую.",
}

LANG_SET_EN = {
    "gui.settledlands.desc453.footer_title": "Description",
    "gui.settledlands.graph.variable": "Math Expression",
    "gui.settledlands.graph.add_variable": "+ Math Expression",
    "gui.settledlands.block.palette": "A block can run: Line, Trigger, If, Math Expression, Random, In radius",
    "gui.settledlands.tip448.menu.node_4": "Create a Math Expression node to compute and modify a variable (+, -, *, /, =, ^, %, mod, min/max).",
    "gui.settledlands.tip448.node.n_4": "Tip: set a variable name (or link a Variable node to its orange input/output) and pick the operation on the right.",
    "gui.settledlands.desc448.card.node_4": "Applies a math expression to a variable (+, -, *, /, =, ^, %, mod, min/max) with both orange data and blue entity inputs.",
    "gui.settledlands.desc448.card.var453": "Variable container (Read/Write, no white flow ports): orange pins wire values to math/conditions, while its right-edge blue output passes dynamic values to Teleports, Dialogue Lines (%name%, %var%), Triggers, Pursuit, and IF right-hand operands.",
    "gui.settledlands.var453.menu": "+ Variable",
    "gui.settledlands.var453.title": "Variable",
    "gui.settledlands.var453.added": "Added Variable node",
    "gui.settledlands.var453.card_name": "Name:",
    "gui.settledlands.var453.const_mode": "Unnamed (node memory)",
    "gui.settledlands.var453.card_val": "Value:",
    "gui.settledlands.var453.numbers_only": "Integers · %var% via blue pin",
    "gui.settledlands.var453.name": "Variable name (blank = node-local memory)",
    "gui.settledlands.var453.value": "Numeric value (initial / constant)",
    "gui.settledlands.var453.scope": "Variable scope",
    "gui.settledlands.var453.scope_personal": "Personal",
    "gui.settledlands.var453.scope_shared": "Shared",
    "gui.settledlands.var453.scope_local": "Local",
    "gui.settledlands.var453.btn_personal": "Personal (per player)",
    "gui.settledlands.var453.btn_shared": "Shared (global world)",
    "gui.settledlands.var453.btn_local": "Local (this NPC / object only)",
    "gui.settledlands.var453.btn_local_toggle": "Local (object)",
    "gui.settledlands.var453.btn_local_on": "Local (object): ON",
    "gui.settledlands.var453.btn_local_off": "Local (object): OFF",
    "gui.settledlands.var453.wired_count": "Linked orange outputs: %s",
    "gui.settledlands.var453.wired_none": "Orange output not wired",
    "gui.settledlands.var453.btn_unlink": "Unlink outgoing orange wires",
    "gui.settledlands.var453.op_label": "Math operation",
    "gui.settledlands.var453.op_0": "+=  Add",
    "gui.settledlands.var453.op_1": "-=  Subtract",
    "gui.settledlands.var453.op_2": "*=  Multiply",
    "gui.settledlands.var453.op_3": "/=  Divide",
    "gui.settledlands.var453.op_4": "=   Assign (set)",
    "gui.settledlands.var453.op_5": "^=  Power",
    "gui.settledlands.var453.op_6": "%   Percent of current value",
    "gui.settledlands.var453.op_7": "+%  Add percent of value",
    "gui.settledlands.var453.op_8": "-%  Subtract percent of value",
    "gui.settledlands.var453.op_9": "mod Modulo (remainder)",
    "gui.settledlands.var453.op_10": "min Minimum clamp",
    "gui.settledlands.var453.op_11": "max Maximum clamp",
    "gui.settledlands.var453.op_12": "|x| Absolute value",
    "gui.settledlands.var453.op_13": "-x  Negate sign",
    "gui.settledlands.var453.orange_operand": "Orange: #%s (%s) → operand",
    "gui.settledlands.var453.orange_target": "Orange: #%s (%s) → modifies it",
    "gui.settledlands.var453.orange_linked_in": "Orange input: #%s (%s)",
    "gui.settledlands.var453.orange_none": "Orange input: not wired",
    "gui.settledlands.var453.btn_unlink_orange": "Unlink orange input",
    "gui.settledlands.var453.blue_linked": "Blue input (subject): #%s",
    "gui.settledlands.var453.blue_none": "Blue input: not wired",
    "gui.settledlands.var453.btn_unlink_blue": "Unlink blue input",
    "gui.settledlands.var453.blue_out_card": "blue → %s",
    "gui.settledlands.var453.blue_out_count": "Blue outputs linked: %s",
    "gui.settledlands.var453.btn_unlink_blue_out": "Unlink blue outputs",
    "gui.settledlands.var453.blue_if_var_linked": "Blue right operand: #%s (%s)",
    "gui.settledlands.var453.blue_text_linked": "Blue input: Variable #%s (%s)",
    "gui.settledlands.tip448.menu.var453": "Add a Variable node (Local by default) with orange and blue data pins — no white flow ports.",
    "gui.settledlands.tip448.node.var453": "Variable: orange pins read/push numbers; right-edge blue output injects %name% / %var% into Teleports, Lines, Triggers, Pursuit, or dynamic IF comparisons.",
    "gui.settledlands.tip448.var453_op": "Choose the mathematical operation executed by this Math Expression node.",
    "gui.settledlands.tip448.pin.var453_out_title": "Orange output (number / variable)",
    "gui.settledlands.tip448.pin.var453_out": "Wire into Variable, Math Expression, or IF orange input pins. Right-click to unplug all.",
    "gui.settledlands.tip448.pin.var453_blue_out_title": "Blue variable output (%var%)",
    "gui.settledlands.tip448.pin.var453_blue_out": "Wire into Teleport (%x% coords), Line/Button/Trigger (%name% / %var%), IF (dynamic right-hand compare), or Pursuit. Right-click to unplug.",
    "gui.settledlands.tip448.pin.blue_in_title": "Blue input (variable / target)",
    "gui.settledlands.tip448.pin.blue_in": "Receives a blue cable from a Variable (%name% / %var% / dynamic IF) or an entity source from In Radius.",
    "gui.settledlands.tip448.pin.var_orange_title": "Orange input (variable / number)",
    "gui.settledlands.tip448.pin.var_orange_in": "Receives an orange wire from a Variable or Math Expression node.",
    "gui.settledlands.tip448.pin.var_blue_title": "Blue input (target / variable)",
    "gui.settledlands.tip448.pin.var_blue_in": "Selects which player/entity's personal variable to read/modify, or supplies a dynamic Variable.",
    # Variable Catalog (Build 457)
    "gui.settledlands.var.scope.local": "Local",
    "gui.settledlands.var.scope.personal": "Personal",
    "gui.settledlands.var.scope.shared": "Shared",
    "gui.settledlands.var.scope.local.desc": "Scoped strictly to this specific NPC or world object (block/door/chest/trial).",
    "gui.settledlands.var.scope.personal.desc": "Stored separately per player across the world.",
    "gui.settledlands.var.scope.shared.desc": "Single shared global variable across the entire world.",
    "gui.settledlands.var_catalog.title": "World Variable Catalog",
    "gui.settledlands.var_catalog.gear_tip": "Open World Variable Catalog (create, edit, delete)",
    "gui.settledlands.var_catalog.dropdown_pick": "+ Catalog / new variable...",
    "gui.settledlands.var_catalog.create_row_tip": "Open catalog and create a new variable",
    "gui.settledlands.var_catalog.edit_row_tip": "Open and edit \"%s\" in catalog",
    "gui.settledlands.var_catalog.scope_tag.local": "Loc",
    "gui.settledlands.var_catalog.scope_tag.personal": "Pers",
    "gui.settledlands.var_catalog.scope_tag.shared": "Shr",
    "gui.settledlands.var_catalog.locked_badge": "Other",
    "gui.settledlands.var_catalog.other_object": "another object",
    "gui.settledlands.var_catalog.unavailable_tip": "Local variable belongs to another object (%s) — read only",
    "gui.settledlands.var_catalog.current_owner": "Current object: %s",
    "gui.settledlands.var_catalog.empty": "No variables found",
    "gui.settledlands.var_catalog.search": "Search variables...",
    "gui.settledlands.var_catalog.filter_all": "All",
    "gui.settledlands.var_catalog.only_avail": "Hide foreign: ON",
    "gui.settledlands.var_catalog.show_all": "Hide foreign: OFF",
    "gui.settledlands.var_catalog.btn_new": "+ New",
    "gui.settledlands.var_catalog.btn_save": "Save",
    "gui.settledlands.var_catalog.btn_apply": "Use in Node",
    "gui.settledlands.var_catalog.btn_delete": "Delete",
    "gui.settledlands.var_catalog.btn_delete_confirm": "Confirm!",
    "gui.settledlands.var_catalog.btn_close": "Close",
    "gui.settledlands.var_catalog.name_label": "Variable Name",
    "gui.settledlands.var_catalog.value_label": "Value (integer)",
    "gui.settledlands.var_catalog.scope_label": "Scope",
    "gui.settledlands.var_catalog.desc_label": "Note / Description",
    "gui.settledlands.var_catalog.owner_row": "Owner: %s",
    "gui.settledlands.var_catalog.status_new": "Creating a new variable (Local by default)",
    "gui.settledlands.var_catalog.status_saved": "Saved variable \"%s\"",
    "gui.settledlands.var_catalog.status_deleted": "Deleted variable \"%s\"",
    "gui.settledlands.var_catalog.confirm_delete": "Click again to delete \"%s\"",
    "gui.settledlands.var_catalog.err_empty_name": "Enter a valid variable name",
    "gui.settledlands.var_catalog.err_foreign_local": "Cannot modify a local variable belonging to another object",
    # Square zone keys preserved from 456
    "gui.settledlands.radius_shape_btn": "Zone Shape",
    "gui.settledlands.radius_shape_circle": "Zone Shape: Circle",
    "gui.settledlands.radius_shape_square": "Zone Shape: Square",
    "gui.settledlands.radius_shape_circle_short": "Circle",
    "gui.settledlands.radius_shape_square_short": "Square",
    "gui.settledlands.tip448.rad.visible_on": "Zone outline particles are visible in-world. Click to hide.",
    "gui.settledlands.tip448.rad.visible_off": "Zone outline particles are hidden. Click to show the outline.",
    "gui.settledlands.tip448.rad.shape_circle": "Circular/spherical zone of radius R around center. Click to switch to Square box (±R on X/Y/Z).",
    "gui.settledlands.tip448.rad.shape_square": "Square zone (exact ±R block box along X/Y/Z matching the square outline). Click to switch to Circle.",
}

CHANGELOG_EN = """\
Build 457 - World Variable Catalog, Local Scope Default, Blue Variable Wiring, and %var% Substitution
- Added persistent World Variable Catalog (`VarCatalogStore457`, `VarPoolEditorScreen457`) with All/Shared/Personal/Local filters, foreign-local guard badges, inline `NpcDropdown` pickers + gear buttons on Variable, Math Expression, and IF nodes, plus full runtime cleanup on rename/delete.
- Changed new Variable (`VarDataNode453`) nodes to default to Local (object-scoped) instead of Personal.
- Exposed a right-edge blue variable output pin at `(220, 92)` on Variable nodes with full bidirectional drag, bend-point, hover, and cut support into `Pursuit`, `IF` (dynamic right-hand comparison operand), `VARIABLE`, `LINE`, `BUTTON`, `TRIGGER`, and `TeleportNode424` (allowing `%var%` coordinates).
- Implemented runtime `%<varName>%`, `%var%`, and `%value%` substitution across NPC/block dialogue lines, choice buttons, `NpcActions` (`fill`/`run`/`runSingle`/`runForEntity`), `TeleportNode424`, and `BlockTalk358`.
- Includes all Build 453-456 features (no white ports on Variable, 14-op math, Circle/Square zone modes, scrollable inspector descriptions, unified wire hover)."""

PATCHINFO = """\
Settled Lands 0.8.0 Build 457
- World Variable Catalog (`VarCatalogStore457` + `VarPoolEditorScreen457` + inline dropdown/gear on Variable/Math/IF) with foreign-local access protection and live cleanup
- Variable nodes default to Local (object-scoped) and expose a right-edge blue variable output pin at (220, 92)
- Blue variable cables wire into Teleport (%var% coords), Dialogue Lines/Buttons (%var%/%name%), Triggers/Actions, Pursuit, and IF (dynamic right-hand compare operand)
- Includes all Build 453-456 features (Square/Circle zones, 14-op math, wire hover, scrollable descriptions)
"""


def find_java() -> str:
    venv_java = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
    if venv_java.is_file():
        return str(venv_java)
    try:
        import jdk4py  # type: ignore
        cand = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
        if cand.is_file():
            return str(cand)
    except Exception:
        pass
    found = shutil.which("java")
    if found:
        return found
    raise SystemExit("no java runtime found")


def compile_build457(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    java = find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    src_root = ROOT / "build303-src" / "src"
    session_stub_java = ROOT / "build303-src" / "stubs345-src" / "dev" / "settledlands" / "npc" / "GuideDialogue.java"

    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        session_stub_out = td / "session_stub_out"
        merged.mkdir()
        stubs_out.mkdir()
        session_stub_out.mkdir()
        for layer in STUB_LAYERS:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(base_jar), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("stub compile failed:\n" + r1.stdout + r1.stderr)

        r_sess = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{stubs_out}:{base_jar}", "-d", str(session_stub_out),
             str(session_stub_java)],
            capture_output=True, text=True)
        if r_sess.returncode != 0:
            raise SystemExit("GuideDialogue session stub compile failed:\n"
                             + r_sess.stdout + r_sess.stderr)

        main_targets = [str(src_root / rel) for rel in SOURCE_FILES if rel != SESSION_STUB_MIXIN]
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{base_jar}:{stubs_out}", "-d", str(out_dir)] + main_targets,
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("build457 compile failed (pass 1):\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{out_dir}:{session_stub_out}:{base_jar}:{stubs_out}",
             "-d", str(out_dir), str(src_root / SESSION_STUB_MIXIN)],
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("build457 compile failed (pass 2 - VarExecutionNpcMixin453):\n"
                             + r3.stdout + r3.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_mixins_json(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))

    mixins = list(doc.get("mixins", []))
    for m in COMMON_MIXINS_457:
        while m in mixins:
            mixins.remove(m)

    idx_tp_actions = mixins.index("TeleportActionsMixin424")
    mixins.insert(idx_tp_actions, "VarActionsSubstituteMixin457")

    idx_guide_range = mixins.index("GuideRange450Mixin")
    mixins.insert(idx_guide_range, "VarExecutionNpcMixin453")

    for m in COMMON_MIXINS_457:
        if m not in ("VarExecutionNpcMixin453", "VarActionsSubstituteMixin457"):
            mixins.append(m)
    doc["mixins"] = mixins

    client = list(doc.get("client", []))
    for m in CLIENT_INSERT_MIXINS_457:
        while m in client:
            client.remove(m)

    idx_pursuit_pin = client.index("NpcPursuitValuePinMixin290")
    client.insert(idx_pursuit_pin, "NpcWirePointsMixin321")

    idx_insp = client.index("NpcPursuitInspectorMixin277")
    client.insert(idx_insp, "GraphInspectorDescMixin453")

    idx_tp_menu = client.index("TeleportContextMenuMixin424") + 1
    client.insert(idx_tp_menu, "VarDataMenuMixin453")

    idx_tp_ui = client.index("TeleportGraphUiMixin424") + 1
    client.insert(idx_tp_ui, "TeleportValuePinMixin457")

    idx_ranges = client.index("GraphRangeInputs450Mixin")
    client.insert(idx_ranges, "VarDataGraphUi453Mixin")
    client.insert(client.index("GraphRangeInputs450Mixin"), "VarCatalogConfigMixin457")

    idx_dd_hover = client.index("GraphDropdownHover448Mixin")
    client.insert(idx_dd_hover, "VarDropdownOverlayMixin457")

    idx_hover = client.index("GraphHover448Mixin")
    client.insert(idx_hover, "GraphClickDragMixin453")

    if not client or client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    if client[-1] != "GraphHover448Mixin":
        raise SystemExit("GraphHover448Mixin must remain last in client mixins!")

    doc["client"] = client
    return (json.dumps(doc, indent=2) + "\n").encode("utf-8")


def main(argv):
    if set(LANG_SET_RU) != set(LANG_SET_EN):
        diff = set(LANG_SET_RU) ^ set(LANG_SET_EN)
        raise SystemExit("lang parity error in patch_build457.py: %s" % sorted(diff))

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out457",
                    help="directory for compiled build 457 classes")
    ap.add_argument("--no-compile", action="store_true",
                    help="reuse existing --classes directory without running ECJ")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(args.classes)
    if not args.no_compile:
        print("compiling Build 457 sources via ECJ (-21)...")
        compile_build457(base, srcroot)
    elif not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = list(zf.namelist())
        blobs = {name: zf.read(name) for name in order}

    outer_stems = [rel[:-5] for rel in SOURCE_FILES]
    stale_removed = set()
    new_order = []
    for name in order:
        if any(name == stem + ".class" or name.startswith(stem + "$") for stem in outer_stems):
            stale_removed.add(name)
            blobs.pop(name, None)
        else:
            new_order.append(name)
    order = new_order

    compiled_map = {}
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        compiled_map[rel] = compiled.read_bytes()

    for rel, data in sorted(compiled_map.items()):
        for marker in MARKERS.get(rel, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (rel, marker))
        action = "replaced" if rel in stale_removed else "added   "
        print("  %s: %s (%d bytes)" % (action, rel, len(data)))
        order.append(rel)
        blobs[rel] = data

    blobs["settledlands.mixins.json"] = patch_mixins_json(blobs["settledlands.mixins.json"])
    print("  updated : settledlands.mixins.json")

    for lp, entries in (
        ("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
        ("assets/settledlands/lang/en_us.json", LANG_SET_EN),
    ):
        if lp not in blobs:
            raise SystemExit("base jar missing %s" % lp)
        blobs[lp] = patch_lang(blobs[lp], entries)
        print("  updated : %s (%d keys updated)" % (lp, len(entries)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    stale = 'displayName="Settled Lands %s"' % BASE_VERSION
    if stale not in toml:
        raise SystemExit("base toml has no displayName to bump")
    toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    (outdir / (OUT_NAME + ".sha256sums.txt")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
