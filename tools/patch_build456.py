"""Build 456:
Built cleanly on top of Build 452 base JAR (incorporating all Build 453/454/455 features & fixes):
1. Variable node ("Переменная" / "Variable", formerly "Значение переменной") & "Мат выражение"
   (stock VARIABLE node 4 renamed to "Мат выражение" / "Math Expression", zero UE5 branding):
   - Variable node (`VarDataNode453`) has NO white execution ports (`VarDataCard453` omits white
     sockets, `GraphCardFooter448Mixin.hitPort` returns -1, `VarGraphIoMixin453.connect` rejects
     white wires, and palette menus never auto-connect white wires).
   - It passively receives all values pushed to it on its orange input (at y=50) from any
     "Мат выражение" (`VARIABLE` 4) or another "Переменная" (supporting named scopes AND unnamed
     node-keyed storage `__slvar_<id>` plus downstream cascade), and outputs its current stored
     value from its orange output (at y=50) with optional blue entity target pin (at y=92).
2. Square Zone shape toggle for "In Radius" (CHECK 5) and "Target" (RADIUS 15) nodes across NPC,
   block, door, chest, and trial graphs:
   - Exact Chebyshev box containment |dx| <= r && |dy| <= r && |dz| <= r matching the corners/edges
     of the world-space square particle border.
   - Persisted via SquareZone456 NBT, Node.copy(), and NpcGraphClipboard318.
   - Inspector toggle button "Форма зоны: Круг / Квадрат" (saves immediately) + card header badge.
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
NEW_VERSION = "0.8.0-build456-variable-node-ports-and-square-zones"
OUT_NAME = "settledlands-0.8.0-build456-variable-node-ports-and-square-zones.jar"

STUB_LAYERS = [
    ROOT / "tools" / "api271",
    ROOT / "build303-src" / "stubs",
    ROOT / "build303-src" / "stubs345-src",
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
]

SESSION_STUB_MIXIN = "dev/settledlands/mixin/VarExecutionNpcMixin453.java"

COMMON_MIXINS_456 = [
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
]

MARKERS = {
    "dev/settledlands/npc/VarDataNode453.class": ("applyMathOp", "isVarDataNode", "findLinkedOrangeTargets"),
    "dev/settledlands/npc/VarScopeStore453.class": (
        "executeVariableForNpc",
        "executeVariableForBlock",
        "propagateToLinkedVarNodes",
    ),
    "dev/settledlands/npc/RadiusZone456.class": ("drawSquareOutline", "isInsideBox", "isSquare"),
    "dev/settledlands/npc/RadiusZone456$ZoneShapeHost.class": ("settledlands$isSquareZone456",),
    "dev/settledlands/npc/client/RadiusZoneUi456.class": ("addShapeToggleRow", "drawCardBadge"),
    "dev/settledlands/mixin/RadiusZoneNodeMixin456.class": ("settledlands$copySquareZone456",),
    "dev/settledlands/mixin/RadiusZonePassMixin456.class": ("settledlands$spotDistanceOrBox456",),
    "dev/settledlands/mixin/RadiusZoneLegacy320Mixin456.class": ("settledlands$skipCircleRingWhenSquare456",),
    "dev/settledlands/mixin/RadiusZoneNpcCheckMixin456.class": ("settledlands$pushScanCtx456",),
    "dev/settledlands/mixin/RadiusZoneNpcDialogueMixin456.class": ("settledlands$expandPlayerDist456",),
    "dev/settledlands/npc/client/VarDataCard453.class": ("drawVarDataCard", "drawVariableCard", "findBlueSourceId"),
    "dev/settledlands/mixin/GraphCardFooter448Mixin.class": ("settledlands$disableWhitePortsOnVarData456",),
    "dev/settledlands/mixin/NpcRadiusRow355Mixin.class": ("addShapeToggleRow", "drawCardBadge"),
}

LANG_SET_RU = {
    "gui.settledlands.desc453.footer_title": "Описание",
    "gui.settledlands.graph.variable": "Мат выражение",
    "gui.settledlands.graph.add_variable": "+ Мат выражение",
    "gui.settledlands.block.palette": "У блока доступны: Реплика, Триггер, Условие If, Мат выражение, Случайность, В радиусе",
    "gui.settledlands.tip448.menu.node_4": "Добавить узел «Мат выражение» для вычисления и изменения переменной (+, -, *, /, =, ^, %, mod, min/max).",
    "gui.settledlands.tip448.node.n_4": "Подсказка: задайте имя переменной (или подключите оранжевую «Переменную» ко входу/выходу) и выберите операцию справа.",
    "gui.settledlands.desc448.card.node_4": "Выполняет мат. выражение над переменной (+, -, *, /, =, ^, %, mod, min/max) с поддержкой оранжевого и синего входов.",
    "gui.settledlands.desc448.card.var453": "Нода переменной (чтение и запись, без белых контактов): сама принимает значения с оранжевого входа (в т.ч. от «Мат выражения») и отдаёт записанное значение с оранжевого выхода; поддерживает синий пин цели.",
    "gui.settledlands.var453.menu": "+ Переменная",
    "gui.settledlands.var453.title": "Переменная",
    "gui.settledlands.var453.added": "Добавлен узел «Переменная»",
    "gui.settledlands.var453.card_name": "Имя:",
    "gui.settledlands.var453.const_mode": "Без имени (локальное значение)",
    "gui.settledlands.var453.card_val": "Значение:",
    "gui.settledlands.var453.numbers_only": "Только целые числа · Чтение/Запись",
    "gui.settledlands.var453.name": "Имя переменной (пусто = внутренняя память узла)",
    "gui.settledlands.var453.value": "Числовое значение (начальное / константа)",
    "gui.settledlands.var453.scope": "Область видимости",
    "gui.settledlands.var453.scope_personal": "Персональная",
    "gui.settledlands.var453.scope_shared": "Общая",
    "gui.settledlands.var453.scope_local": "Локальная",
    "gui.settledlands.var453.btn_personal": "Персональная (у каждого игрока)",
    "gui.settledlands.var453.btn_shared": "Общая (единая на весь мир)",
    "gui.settledlands.var453.btn_local": "Локальная (только этот объект/NPC)",
    "gui.settledlands.var453.wired_count": "Подключено узлов (выход): %s",
    "gui.settledlands.var453.wired_none": "Оранжевый выход не подключён",
    "gui.settledlands.var453.btn_unlink": "Отвязать исходящие провода",
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
    "gui.settledlands.tip448.menu.var453": "Создать узел «Переменная» (имя, число, область) с оранжевыми портами данных и синим входом цели — без белых контактов.",
    "gui.settledlands.tip448.node.var453": "Переменная: не требует белых проводов — сама принимает значения на левый оранжевый вход и отдаёт записанное значение с правого выхода.",
    "gui.settledlands.tip448.var453_op": "Выберите математическую операцию для ноды «Мат выражение».",
    "gui.settledlands.tip448.pin.var453_out_title": "Оранжевый выход (число / переменная)",
    "gui.settledlands.tip448.pin.var453_out": "Тяните провод в оранжевый вход «Переменной», «Мат выражения» или «Условие If». ПКМ — отвязать провода.",
    "gui.settledlands.tip448.pin.var_orange_title": "Оранжевый вход (переменная / число)",
    "gui.settledlands.tip448.pin.var_orange_in": "Принимает оранжевый провод от узла «Переменная» или «Мат выражение».",
    "gui.settledlands.tip448.pin.var_blue_title": "Синий вход (цель / игрок)",
    "gui.settledlands.tip448.pin.var_blue_in": "Определяет игрока/сущность, над персональной переменной которого выполнится чтение или запись.",
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
    "gui.settledlands.desc448.card.var453": "Variable container (Read/Write, no white flow ports): passively receives values on its orange input (including from Math Expression) and outputs stored values on its orange pin; supports a blue target pin.",
    "gui.settledlands.var453.menu": "+ Variable",
    "gui.settledlands.var453.title": "Variable",
    "gui.settledlands.var453.added": "Added Variable node",
    "gui.settledlands.var453.card_name": "Name:",
    "gui.settledlands.var453.const_mode": "Unnamed (node memory)",
    "gui.settledlands.var453.card_val": "Value:",
    "gui.settledlands.var453.numbers_only": "Integers only · Read/Write",
    "gui.settledlands.var453.name": "Variable name (blank = node-local memory)",
    "gui.settledlands.var453.value": "Numeric value (initial / constant)",
    "gui.settledlands.var453.scope": "Variable scope",
    "gui.settledlands.var453.scope_personal": "Personal",
    "gui.settledlands.var453.scope_shared": "Shared",
    "gui.settledlands.var453.scope_local": "Local",
    "gui.settledlands.var453.btn_personal": "Personal (per player)",
    "gui.settledlands.var453.btn_shared": "Shared (global world)",
    "gui.settledlands.var453.btn_local": "Local (this NPC / object only)",
    "gui.settledlands.var453.wired_count": "Linked outputs: %s",
    "gui.settledlands.var453.wired_none": "Orange output not wired",
    "gui.settledlands.var453.btn_unlink": "Unlink outgoing wires",
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
    "gui.settledlands.tip448.menu.var453": "Add a Variable node (name, integer value, scope) with orange data pins and a blue entity input — no white flow ports.",
    "gui.settledlands.tip448.node.var453": "Variable: requires no white wires — passively receives values on its left orange input and outputs its stored value on the right orange pin.",
    "gui.settledlands.tip448.var453_op": "Choose the mathematical operation executed by this Math Expression node.",
    "gui.settledlands.tip448.pin.var453_out_title": "Orange output (number / variable)",
    "gui.settledlands.tip448.pin.var453_out": "Wire into Variable, Math Expression, or IF orange input pins. Right-click to unplug all.",
    "gui.settledlands.tip448.pin.var_orange_title": "Orange input (variable / number)",
    "gui.settledlands.tip448.pin.var_orange_in": "Receives an orange wire from a Variable or Math Expression node.",
    "gui.settledlands.tip448.pin.var_blue_title": "Blue input (target / player)",
    "gui.settledlands.tip448.pin.var_blue_in": "Selects which player/entity's personal variable this node reads or modifies.",
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
Build 456 - Variable Node & Math Expression Rename, No White Ports on Variable, and Square Zones
- Renamed stock VARIABLE (4) node to "Math Expression" ("Мат выражение") and renamed the variable container node to "Variable" ("Переменная") across RU/EN UI, menus, tooltips, and descriptions (zero UE5 branding).
- Removed white execution flow ports from the "Variable" node (`VarDataNode453`): orange input/output sockets sit centered at y=50 alongside the blue target pin at y=92; passively accepts values from any Math Expression or upstream Variable (both named and unnamed node-scoped) and outputs stored values to any downstream Math Expression / IF / Variable.
- Added Circle / Square zone mode toggle to "In Radius" (CHECK 5) and "Target" (RADIUS 15) nodes with exact Chebyshev box detection, square world particle border, inspector toggle + card badge, and NBT/clipboard persistence.
- Includes all Build 453/454/455 runtime fixes, scrollable inspector description card, unified wire hover, and 14-op math."""

PATCHINFO = """\
Settled Lands 0.8.0 Build 456
- Renamed stock VARIABLE (4) to "Math Expression" ("Мат выражение") and VarDataNode453 to "Variable" ("Переменная"), zero UE5 branding
- Removed white flow ports on "Variable" node (orange in/out at y=50 + blue target at y=92); passively accepts & cascades pushed values (named or unnamed) and outputs stored values
- Added Circle / Square zone mode to CHECK (5) and RADIUS (15) nodes with matching square particle outline, NBT/clipboard save, and UI badge/toggle
- Includes all Build 453/454/455 fixes (scrollable inspector descriptions, wire hover, math ops)
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


def compile_build456(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build456 compile failed (pass 1):\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{out_dir}:{session_stub_out}:{base_jar}:{stubs_out}",
             "-d", str(out_dir), str(src_root / SESSION_STUB_MIXIN)],
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("build456 compile failed (pass 2 - VarExecutionNpcMixin453):\n"
                             + r3.stdout + r3.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_mixins_json(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))

    mixins = list(doc.get("mixins", []))
    for m in COMMON_MIXINS_456:
        while m in mixins:
            mixins.remove(m)
    idx_guide_range = mixins.index("GuideRange450Mixin")
    mixins.insert(idx_guide_range, "VarExecutionNpcMixin453")
    for m in COMMON_MIXINS_456:
        if m != "VarExecutionNpcMixin453":
            mixins.append(m)
    doc["mixins"] = mixins

    client = list(doc.get("client", []))

    for m in (
        "NpcWirePointsMixin321",
        "GraphInspectorDescMixin453",
        "VarDataMenuMixin453",
        "VarDataGraphUi453Mixin",
        "GraphClickDragMixin453",
    ):
        while m in client:
            client.remove(m)

    idx_pursuit_pin = client.index("NpcPursuitValuePinMixin290")
    client.insert(idx_pursuit_pin, "NpcWirePointsMixin321")

    idx_insp = client.index("NpcPursuitInspectorMixin277")
    client.insert(idx_insp, "GraphInspectorDescMixin453")

    idx_tp_menu = client.index("TeleportContextMenuMixin424") + 1
    client.insert(idx_tp_menu, "VarDataMenuMixin453")

    idx_ranges = client.index("GraphRangeInputs450Mixin")
    client.insert(idx_ranges, "VarDataGraphUi453Mixin")

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
        raise SystemExit("lang parity error in patch_build456.py")

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out456",
                    help="directory for compiled build 456 classes")
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
        print("compiling Build 456 sources via ECJ (-21)...")
        compile_build456(base, srcroot)
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
