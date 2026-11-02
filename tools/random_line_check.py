#!/usr/bin/env python3
"""Gate: the random answer of a "Реплика" node has to reach the line the NPC says, and be editable
where the author expects it.

Build 337 keeps the roll of build 336 (a saved integer on the node, handed to `Node#lineFor`, the one
call every place that shows a line goes through) but moves the control from a check box painted over
the card into the node's inspector, as the same on/off row every other yes/no property of a node uses.
Four ways that can ship silently broken:

  * the roll is read from a field nothing saves - the dice would be forgotten on every reload;
  * the node hook is not bound to the real `lineFor` descriptor - the toggle would flip and change
    nothing;
  * the inspector row is registered as a common mixin - the server would try to load a class that
    builds client widgets, or the row is never added to the scroll range and cannot be seen;
  * the old canvas check box comes back, drawing a second control over the node;
  * the label keys are missing, and the row shows a raw translation key instead of a name.

    python3 tools/random_line_check.py settledlands-0.8.0-build337-self-link.jar

Exit 0 = the roll is saved, the line uses it, and the toggle is a real inspector row.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

RULE = "dev/settledlands/npc/NpcRandomLine336.class"
NODE_MIXIN = "dev/settledlands/mixin/NpcRandomLineNodeMixin336.class"
UI = "dev/settledlands/npc/client/NpcRandomLineUi337.class"
UI_MIXIN = "dev/settledlands/mixin/NpcRandomLineInspectorMixin337.class"
GONE = ("dev/settledlands/npc/client/NpcRandomLineUi336.class",
        "dev/settledlands/mixin/NpcRandomLineUiMixin336.class")
CONFIG = "settledlands.mixins.json"
NODE = "dev/settledlands/npc/NpcDialogueGraph$Node.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"

LINE_FOR = ("lineFor", "(I)Ljava/lang/String;")
WIDGETS = ("widgets", "()V")
ROLL_FIELD = ("dev/settledlands/npc/NpcDialogueGraph$Node", "varValue", "I")


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    refs = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs.add((owner, name, desc))
    return parsed, refs


def text(parsed):
    return " ".join(str(v[1]) for v in parsed["cp"] if v and v[0] == 1)


def methods(parsed):
    cp = parsed["cp"]
    return {(cp[n][1], cp[d][1]) for _a, n, d, _at in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (RULE, NODE_MIXIN, UI, UI_MIXIN):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        for entry in GONE:
            if entry in names:
                return fail("%s is still shipped - the canvas check box has to be gone" % entry)
        blobs = {e: z.read(e) for e in (RULE, NODE_MIXIN, UI, UI_MIXIN, NODE, SCREEN)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        lang_ru = json.loads(z.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        lang_en = json.loads(z.read("assets/settledlands/lang/en_us.json").decode("utf-8"))

    # -- the rule lives in a saved field ------------------------------------
    rule, rule_refs = info(blobs[RULE])
    if ROLL_FIELD not in rule_refs:
        return fail("the roll is not kept in Node#varValue - it would not survive a reload")
    if any(r[0] == "dev/settledlands/npc/NpcDialogueGraph$Node"
           and r[1] in ("timerInterval", "timerRepeat") for r in rule_refs):
        return fail("the rule still leans on the timer fields - those default to on, not off")
    rule_text = text(rule)
    for step in ("phrases", "reroll", "pick", "toggle", "index"):
        if step not in rule_text:
            return fail("the rule has no %s step" % step)
    print("ok: one saved integer carries the toggle and the rolled phrase")

    # -- the line the NPC says uses it --------------------------------------
    node, _node_refs = info(blobs[NODE])
    if LINE_FOR not in methods(node):
        return fail("Node has no %s%s - there is nothing to hook" % LINE_FOR)
    _node_mixin, node_mixin_refs = info(blobs[NODE_MIXIN])
    if not any(r[0] == "dev/settledlands/npc/NpcRandomLine336" and r[1] == "pick"
               for r in node_mixin_refs):
        return fail("the node hook never asks the rule for a phrase")
    print("ok: lineFor is hooked and asks the rule")

    # -- the toggle is a real inspector row ---------------------------------
    common = config.get("mixins", [])
    client = config.get("client", [])
    if "NpcRandomLineNodeMixin336" not in common:
        return fail("the node hook is not a common mixin - the server would never apply it")
    if "NpcRandomLineUiMixin336" in client or "NpcRandomLineUiMixin336" in common:
        return fail("the canvas check box of 336 is still registered")
    if "NpcRandomLineInspectorMixin337" not in client:
        return fail("the inspector row is not a client mixin - it would be loaded on the server")
    if "NpcRandomLineInspectorMixin337" in common:
        return fail("the inspector row is listed as common - the server cannot build widgets")
    print("ok: node hook common, inspector row client only (%d/%d)" % (len(common), len(client)))

    screen, _screen_refs = info(blobs[SCREEN])
    if WIDGETS not in methods(screen):
        return fail("the screen has no widgets() to add a row to")
    ui_mixin, ui_mixin_refs = info(blobs[UI_MIXIN])
    ui_text = text(ui_mixin)
    for part in ("widgets", "sidebarHeight", "setMessage", "inspectorH"):
        if part not in ui_text:
            return fail("the inspector row never touches %s - it would be invisible or stale"
                        % part)
    screen_methods = methods(screen)
    for member in (("changed", "()V"), ("remember", "()V"),
                   ("button", "(Ljava/lang/String;IIIILjava/lang/Runnable;)"
                              "Lnet/minecraft/client/gui/components/Button;")):
        if member not in screen_methods:
            return fail("the screen has no %s%s - the row's shadow would not bind" % member)
    for part, why in (("changed", "it would not be saved"), ("remember", "it could not be undone")):
        if not any(r[1] == part for r in ui_mixin_refs):
            return fail("flipping the toggle never calls %s() - %s" % (part, why))
    print("ok: the row is a widget inside the scroll range, and flips through remember/changed")

    # -- the control has a name ---------------------------------------------
    for table, column in ((lang_ru, "ru"), (lang_en, "en")):
        for key in ("gui.settledlands.graph.rand_line_off", "gui.settledlands.graph.rand_line_on"):
            if not table.get(key):
                return fail("%s_%s is missing" % (key, column))
    if "%s" not in lang_ru["gui.settledlands.graph.rand_line_on"]:
        return fail("the ru label has no place for the rolled number")
    print("ok: the labels exist in both languages and show the rolled number")

    print("random_line_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
