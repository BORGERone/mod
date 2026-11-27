"""Build 455:
UX fix over Build 454 (compiled cleanly against Build 452 base JAR):
1. Replace the permanently bottom-docked inspector description box (which shrank inspectorH
   and stayed pinned at the bottom of the screen) with an inline scrollable card at the very
   bottom of the general inspector sidebar list, moving smoothly with sidebarScroll and clipping
   cleanly inside the inspector's enableScissor() region.
2. Accurately compute the bottom end of any stock or custom inspector list (including Pursuit,
   which leaves sidebarHeight at 0) from sidebarHeight, non-parked inspectorWidgets, and
   NpcDialogueGraphScreen$Label entries, extending sidebarHeight to cover the full description.
3. Preserve all Build 453 and Build 454 runtime, mix-order, wire hover, UE5 Variable node,
   and 14-op VARIABLE math fixes.
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
NEW_VERSION = "0.8.0-build455-scrollable-inspector-desc"
OUT_NAME = "settledlands-0.8.0-build455-scrollable-inspector-desc.jar"

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
    # Client helpers
    "dev/settledlands/npc/client/GraphInspectorDesc453.java",
    "dev/settledlands/npc/client/VarDataCard453.java",
    "dev/settledlands/npc/client/VarDataInspector453.java",
    "dev/settledlands/npc/client/WireHover453.java",
    "dev/settledlands/npc/client/PursuitGraphNode272.java",
    "dev/settledlands/npc/client/NpcWirePointsUi321.java",
    "dev/settledlands/npc/client/BlockMenu363.java",
    "dev/settledlands/npc/client/GraphTooltips448.java",
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
    "dev/settledlands/mixin/GraphInspectorDescMixin453.java",
    "dev/settledlands/mixin/GraphClickDragMixin453.java",
]

SESSION_STUB_MIXIN = "dev/settledlands/mixin/VarExecutionNpcMixin453.java"

COMMON_MIXINS_453 = [
    "VarNodeEntityInputMixin453",
    "VarGraphIoMixin453",
    "VarExecutionNpcMixin453",
    "VarExecutionBlockMixin453",
    "VarActionsSilentMixin453",
    "VarValueSourceBridgeMixin453",
]

MARKERS = {
    "dev/settledlands/npc/VarDataNode453.class": ("applyMathOp", "isVarDataNode"),
    "dev/settledlands/npc/VarScopeStore453.class": ("executeVariableForNpc", "executeVariableForBlock"),
    "dev/settledlands/npc/VarScopeStore453$BlockWalkFrame.class": ("where",),
    "dev/settledlands/npc/VarScopeStore453$NpcResolveFrame.class": ("contextEntity",),
    "dev/settledlands/npc/client/GraphInspectorDesc453.class": (
        "renderInList",
        "contentHeight",
        "computeContentEndOffset",
    ),
    "dev/settledlands/npc/client/GraphInspectorDesc453$ScrollDescHost.class": (
        "settledlands$layoutScrollDesc455",
    ),
    "dev/settledlands/npc/client/WireHover453.class": ("hoveredWire", "cutNonStockWire"),
    "dev/settledlands/npc/client/VarDataCard453.class": ("drawVarDataCard", "drawVariableCard"),
    "dev/settledlands/mixin/GraphInspectorDescMixin453.class": (
        "settledlands$layoutScrollDesc455",
        "settledlands$renderDescInList455",
    ),
    "dev/settledlands/mixin/GraphClickDragMixin453.class": (
        "settledlands$deferSelectOnPress453",
        "settledlands$appendScrollableInspectorDesc455",
    ),
    "dev/settledlands/mixin/VarExecutionNpcMixin453.class": (
        "settledlands$pushResolve454",
        "settledlands$popResolve454Inject",
    ),
}

LANG_SET_RU = {
    "gui.settledlands.desc453.footer_title": "Описание",
    "gui.settledlands.desc448.card.node_4": "Выполняет математику над переменной (+, -, *, /, =, ^, %, mod, min/max) с поддержкой оранжевого и синего входов.",
    "gui.settledlands.desc448.card.var453": "Контейнер числовой переменной в стиле UE5 (Get/Set): хранит имя, число и область; подключается оранжевым проводом к «Переменная» и «Условие».",
    "gui.settledlands.var453.menu": "+ Переменная (UE5)",
    "gui.settledlands.var453.title": "Переменная (UE5)",
    "gui.settledlands.var453.added": "Добавлена нода переменной (UE5)",
    "gui.settledlands.var453.card_name": "Имя:",
    "gui.settledlands.var453.const_mode": "Число-константа",
    "gui.settledlands.var453.card_val": "Значение:",
    "gui.settledlands.var453.numbers_only": "Только целые числа · Get/Set",
    "gui.settledlands.var453.name": "Имя переменной (пусто = число-константа)",
    "gui.settledlands.var453.value": "Числовое значение (начальное / константа)",
    "gui.settledlands.var453.scope": "Область видимости",
    "gui.settledlands.var453.scope_personal": "Персональная",
    "gui.settledlands.var453.scope_shared": "Общая",
    "gui.settledlands.var453.scope_local": "Локальная",
    "gui.settledlands.var453.btn_personal": "Персональная (у каждого игрока)",
    "gui.settledlands.var453.btn_shared": "Общая (единая на весь мир)",
    "gui.settledlands.var453.btn_local": "Локальная (только этот объект/NPC)",
    "gui.settledlands.var453.wired_count": "Подключено узлов: %s",
    "gui.settledlands.var453.wired_none": "Пока никуда не подключена",
    "gui.settledlands.var453.btn_unlink": "Отвязать все провода",
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
    "gui.settledlands.var453.orange_none": "Оранжевый вход: не подключён",
    "gui.settledlands.var453.btn_unlink_orange": "Отвязать оранжевый вход",
    "gui.settledlands.var453.blue_linked": "Синий вход (субъект): #%s",
    "gui.settledlands.var453.blue_none": "Синий вход: не подключён",
    "gui.settledlands.var453.btn_unlink_blue": "Отвязать синий вход",
    "gui.settledlands.tip448.menu.var453": "Создать узел переменной в стиле UE5 (хранит имя, число, область) для подключения оранжевым проводом.",
    "gui.settledlands.tip448.node.var453": "Переменная (UE5): тяните оранжевый провод справа в ноду «Переменная» или «Условие».",
    "gui.settledlands.tip448.var453_op": "Выберите математическую операцию для ноды «Переменная».",
    "gui.settledlands.tip448.pin.var453_out_title": "Оранжевый выход (число / переменная)",
    "gui.settledlands.tip448.pin.var453_out": "Тяните провод в оранжевый вход «Переменная» или «Условие». ПКМ — отвязать провода.",
    "gui.settledlands.tip448.pin.var_orange_title": "Оранжевый вход (переменная / число)",
    "gui.settledlands.tip448.pin.var_orange_in": "Принимает оранжевый провод от ноды Переменная (UE5) или другой Переменной.",
    "gui.settledlands.tip448.pin.var_blue_title": "Синий вход (цель / игрок)",
    "gui.settledlands.tip448.pin.var_blue_in": "Определяет игрока/сущность, над персональной переменной которого выполнится операция.",
}

LANG_SET_EN = {
    "gui.settledlands.desc453.footer_title": "Description",
    "gui.settledlands.desc448.card.node_4": "Applies math to a variable (+, -, *, /, =, ^, %, mod, min/max) with both orange data and blue entity inputs.",
    "gui.settledlands.desc448.card.var453": "UE5-style numeric variable container (Get/Set): stores name, number, and scope; wire its orange pin into Variable or IF nodes.",
    "gui.settledlands.var453.menu": "+ Variable (UE5)",
    "gui.settledlands.var453.title": "Variable (UE5)",
    "gui.settledlands.var453.added": "Added UE5 variable node",
    "gui.settledlands.var453.card_name": "Name:",
    "gui.settledlands.var453.const_mode": "Numeric constant",
    "gui.settledlands.var453.card_val": "Value:",
    "gui.settledlands.var453.numbers_only": "Integers only · Get/Set",
    "gui.settledlands.var453.name": "Variable name (blank = numeric constant)",
    "gui.settledlands.var453.value": "Numeric value (initial / constant)",
    "gui.settledlands.var453.scope": "Variable scope",
    "gui.settledlands.var453.scope_personal": "Personal",
    "gui.settledlands.var453.scope_shared": "Shared",
    "gui.settledlands.var453.scope_local": "Local",
    "gui.settledlands.var453.btn_personal": "Personal (per player)",
    "gui.settledlands.var453.btn_shared": "Shared (global world)",
    "gui.settledlands.var453.btn_local": "Local (this NPC / object only)",
    "gui.settledlands.var453.wired_count": "Linked nodes: %s",
    "gui.settledlands.var453.wired_none": "Not wired anywhere yet",
    "gui.settledlands.var453.btn_unlink": "Unlink all wires",
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
    "gui.settledlands.var453.orange_none": "Orange input: not wired",
    "gui.settledlands.var453.btn_unlink_orange": "Unlink orange input",
    "gui.settledlands.var453.blue_linked": "Blue input (subject): #%s",
    "gui.settledlands.var453.blue_none": "Blue input: not wired",
    "gui.settledlands.var453.btn_unlink_blue": "Unlink blue input",
    "gui.settledlands.tip448.menu.var453": "Add a UE5-style variable data node (name, integer value, scope) to wire via orange pins.",
    "gui.settledlands.tip448.node.var453": "Variable (UE5): drag from its orange pin into Variable or IF nodes.",
    "gui.settledlands.tip448.var453_op": "Choose the mathematical operation executed by this Variable node.",
    "gui.settledlands.tip448.pin.var453_out_title": "Orange output (number / variable)",
    "gui.settledlands.tip448.pin.var453_out": "Wire into Variable or IF orange input pins. Right-click to unplug all.",
    "gui.settledlands.tip448.pin.var_orange_title": "Orange input (variable / number)",
    "gui.settledlands.tip448.pin.var_orange_in": "Receives an orange wire from a UE5 Variable node or another Variable node.",
    "gui.settledlands.tip448.pin.var_blue_title": "Blue input (target / player)",
    "gui.settledlands.tip448.pin.var_blue_in": "Selects which player/entity's personal variable this node reads or modifies.",
}

CHANGELOG_EN = """\
Build 455 - Scrollable Inspector Description
- Moved the inspector node description box out of its pinned bottom-docked position and into the general inspector scroll list as the bottom-most item.
- Inspector widgets and custom panels now use the full inspectorH without permanent footer shrinking, and the description card scrolls cleanly in and out inside the sidebar scissor region across all 6 editors (including Pursuit).
- Includes all Build 454 runtime/mixin/wire fixes and all Build 453 features (4px click-vs-drag threshold, unified colored/blue/amber/Trial wires, 14 VARIABLE math ops + dual inputs, and UE5 Variable node)."""

PATCHINFO = """\
Settled Lands 0.8.0 Build 455
- Moved inspector node description box into the scrollable sidebar list as the bottom-most item (no longer pinned on screen)
- Restored full inspectorH without bottom footer shrinking; accurate end-offset across all stock & custom inspectors (including Pursuit)
- Includes all Build 454 runtime/mixin/wire fixes and Build 453 features
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


def compile_build455(base_jar: pathlib.Path, out_dir: pathlib.Path) -> None:
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
            raise SystemExit("build455 compile failed (pass 1):\n" + r2.stdout + r2.stderr)

        r3 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{out_dir}:{session_stub_out}:{base_jar}:{stubs_out}",
             "-d", str(out_dir), str(src_root / SESSION_STUB_MIXIN)],
            capture_output=True, text=True)
        if r3.returncode != 0:
            raise SystemExit("build455 compile failed (pass 2 - VarExecutionNpcMixin453):\n"
                             + r3.stdout + r3.stderr)


def patch_lang(raw: bytes, entries: dict) -> bytes:
    doc = json.loads(raw.decode("utf-8"))
    doc.update(entries)
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def patch_mixins_json(raw: bytes) -> bytes:
    doc = json.loads(raw.decode("utf-8"))

    mixins = list(doc.get("mixins", []))
    for m in COMMON_MIXINS_453:
        while m in mixins:
            mixins.remove(m)
    idx_guide_range = mixins.index("GuideRange450Mixin")
    mixins.insert(idx_guide_range, "VarExecutionNpcMixin453")
    for m in COMMON_MIXINS_453:
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
        raise SystemExit("lang parity error in patch_build455.py")

    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default="/tmp/out455",
                    help="directory for compiled build 455 classes")
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
        print("compiling Build 455 sources via ECJ (-21)...")
        compile_build455(base, srcroot)
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
