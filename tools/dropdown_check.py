#!/usr/bin/env python3
"""Gate: an open dropdown list must own the click before the graph does.

Build 333 fixes: a row of an open list that lies over a node gave the click to the node. The list
was painted last but looked up late - the screen's body walks the auto-AI card, then the stats
node, and only its fall-through reaches the code that owns the open dropdown, and every HEAD hook
of builds 272-321 hit-tests the canvas in front of that.

So three things are checked in the shipped jar:

  1. order - the guard mixin is the FIRST client mixin in settledlands.mixins.json, so its HEAD
     hook runs before the hooks that take clicks for cards, pins and points;
  2. the guard itself - the handler really reads the screen's open dropdown, gives it the click,
     closes it, clears the field and consumes the event (setReturnValue on a boolean);
  3. the drawing and the scroll - the screen paints the list from the same field the guard reads
     (render -> openDropdown.renderPopup) and still asks the open dropdown before scrolling
     (mouseScrolled -> popupScrolled), which is the "scroll over the list scrolls it" half.

    python3 tools/dropdown_check.py settledlands-0.8.0-build333-dropdown-first.jar

Exit 0 = the list is drawn, clicked and scrolled from one and the same state.
"""
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

MIXIN_NAME = "NpcDropdownFirst333"
MIXIN = "dev/settledlands/mixin/%s.class" % MIXIN_NAME
GUARD = "settledlands$dropdownFirst"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"
CONFIG = "settledlands.mixins.json"

# Client mixins of the builds before this one whose HEAD hook hit-tests the canvas.
BEFORE = ("NpcPursuitGraphUiMixin272", "NpcPursuitValuePinMixin290",
          "NpcRadiusZoneUiMixin320", "NpcWirePointsMixin321")

GUARD_CALLS = (
    "NpcDialogueGraphScreen.activeDropdown()Ldev/settledlands/npc/client/NpcUi$NpcDropdown;",
    "NpcUi$NpcDropdown.popupOpen()Z",
    "NpcUi$NpcDropdown.popupClicked(DD)Z",
    "NpcUi$NpcDropdown.closePopup()V",
    "NpcDialogueGraphScreen.openDropdown(Ldev/settledlands/npc/client/NpcUi$NpcDropdown;)V",
    "CallbackInfoReturnable.setReturnValue(Ljava/lang/Object;)V",
)


def fail(message):
    print("FAIL: " + message)
    return 1


def methods(data):
    """name+descriptor -> disassembled text for one class."""
    info = disasm.parse_class(data)
    cp = info["cp"]
    out = {}
    for _acc, ni, di, attrs in info["methods"]:
        name = cp[ni][1] + cp[di][1]
        co = disasm.code_of(attrs, cp)
        out[name] = disasm.disasm(co["code"], cp)[0] if co else ""
    return info, out


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if MIXIN not in names:
            return fail("%s is not in the jar" % MIXIN)
        mixin_data = z.read(MIXIN)
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        screen_data = z.read(SCREEN)

    client = config.get("client", [])
    if not client:
        return fail("%s has no client list" % CONFIG)
    if client[0] != MIXIN_NAME:
        return fail("client mixin #1 is %s, not %s - its HEAD hook runs too late"
                    % (client[0], MIXIN_NAME))
    if client.count(MIXIN_NAME) != 1:
        return fail("%s is listed %d times" % (MIXIN_NAME, client.count(MIXIN_NAME)))
    for other in BEFORE:
        if other in client and client.index(other) < client.index(MIXIN_NAME):
            return fail("%s is applied before the guard" % other)
    print("ok: %s is the first client mixin, before %s"
          % (MIXIN_NAME, ", ".join(o for o in BEFORE if o in client)))

    info, code = methods(mixin_data)
    body = ""
    for key, text in code.items():
        if key.startswith(GUARD):
            body = text
            break
    if not body:
        return fail("the mixin has no %s handler" % GUARD)
    for call in GUARD_CALLS:
        if call not in body:
            return fail("the guard never calls %s" % call)
    if "Boolean.TRUE" not in body:
        return fail("the guard does not consume the click (no Boolean.TRUE)")
    cp_text = " ".join(str(v[1]) for v in info["cp"] if v and v[0] == 1)
    if "cancellable" not in cp_text or "mouseClicked" not in cp_text:
        return fail("the guard is not a cancellable @Inject on mouseClicked")
    print("ok: the guard hands the click to the open list, clears it and consumes the event")

    _sinfo, scode = methods(screen_data)
    render = scode.get("render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", "")
    if "openDropdown" not in render or "NpcUi$NpcDropdown.renderPopup(" not in render:
        return fail("render no longer paints the list from openDropdown")
    scroll = scode.get("mouseScrolled(DDDD)Z", "")
    if "popupScrolled" not in scroll:
        return fail("mouseScrolled no longer asks the open list first")
    original = scode.get("settledlands$mouseClickedOriginal(DDI)Z", "")
    if "popupClicked" not in original:
        return fail("the stock fall-through lost its own dropdown guard")
    print("ok: the list is painted, scrolled and clicked from the same field")

    print("dropdown_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
