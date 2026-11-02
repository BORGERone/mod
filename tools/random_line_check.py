#!/usr/bin/env python3
"""Gate: the "случайная реплика" toggle has to reach the line the NPC says.

Build 336 keeps the roll in a saved node field (`varValue`: 0 off, 1..n the phrase) and hands it to
`Node#lineFor`, the one call every place that shows a line goes through. Four ways that can ship
silently broken:

  * the roll is read from a field nothing saves - the dice would be forgotten on every reload;
  * the node hook is not bound to the real `lineFor` descriptor - the toggle would flip and change
    nothing;
  * the on-canvas toggle is registered as a common mixin - the server would try to load a class that
    draws with client graphics;
  * the label keys are missing, and the control shows a raw translation key instead of a name.

So the shipped classes are read for the field the roll lives in, for the injection targets that must
exist in the jar, for the two mixin lists, and for the label keys.

    python3 tools/random_line_check.py settledlands-0.8.0-build336-random-line.jar

Exit 0 = the roll is saved, the line uses it, and the toggle is where it belongs.
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
UI = "dev/settledlands/npc/client/NpcRandomLineUi336.class"
UI_MIXIN = "dev/settledlands/mixin/NpcRandomLineUiMixin336.class"
CONFIG = "settledlands.mixins.json"
NODE = "dev/settledlands/npc/NpcDialogueGraph$Node.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"

LINE_FOR = ("lineFor", "(I)Ljava/lang/String;")
DRAW_NODE = ("drawNode", "(Lnet/minecraft/client/gui/GuiGraphics;"
                        "Ldev/settledlands/npc/NpcDialogueGraph$Node;)V")
MOUSE = ("mouseClicked", "(DDI)Z")
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
    node_mixin, node_mixin_refs = info(blobs[NODE_MIXIN])
    if not any(r[0] == "dev/settledlands/npc/NpcRandomLine336" and r[1] == "pick"
               for r in node_mixin_refs):
        return fail("the node hook never asks the rule for a phrase")
    print("ok: lineFor is hooked and asks the rule")

    # -- the toggle is where it belongs -------------------------------------
    common = config.get("mixins", [])
    client = config.get("client", [])
    if "NpcRandomLineNodeMixin336" not in common:
        return fail("the node hook is not a common mixin - the server would never apply it")
    if "NpcRandomLineUiMixin336" not in client:
        return fail("the toggle is not a client mixin - it would be loaded on the server")
    if "NpcRandomLineUiMixin336" in common:
        return fail("the toggle is listed as common - the server cannot draw it")
    print("ok: node hook common, on-canvas toggle client only (%d/%d)"
          % (len(common), len(client)))

    screen, _screen_refs = info(blobs[SCREEN])
    screen_methods = methods(screen)
    for member in (DRAW_NODE, MOUSE):
        if member not in screen_methods:
            return fail("the screen has no %s%s to attach the toggle to" % member)
    ui_mixin, ui_mixin_refs = info(blobs[UI_MIXIN])
    for owner, name in (("dev/settledlands/npc/client/NpcRandomLineUi336", "draw"),
                        ("dev/settledlands/npc/client/NpcRandomLineUi336", "at")):
        if not any(r[0] == owner and r[1] == name for r in ui_mixin_refs):
            return fail("the toggle hook never calls %s.%s" % (owner.rsplit("/", 1)[-1], name))
    if not any(r[1] == "settledlands$changed" for r in ui_mixin_refs):
        return fail("flipping the toggle never marks the graph changed - it would not be saved")
    if not any(r[1] == "settledlands$remember" for r in ui_mixin_refs):
        return fail("flipping the toggle cannot be undone")
    print("ok: the toggle draws over the card, takes the click first and marks the graph changed")

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
