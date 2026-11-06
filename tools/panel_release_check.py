#!/usr/bin/env python3
"""Gate: the lock panel may not close while the player is still holding keys.

The author's report: «хочу чтобы блокировка пропадала только тогда, когда все кнопки отжаты, а не
когда дверь взломана». Until build 386 the panel closed on the very frame the last key went down, so the
six or eight keys still under the player's fingers were released into a game that was no longer covered
by any screen.

What this gate asks of the jar, all of it structural, because that is what a jar can be asked:

  * the panel keeps a set of the keys it was told about (`pressed`, a `java.util.Set`), filled in
    `keyPressed` and emptied in `keyReleased`;
  * a helper `allReleased()` decides the question, and it asks both the set - pruned against the
    hardware every frame, because a release can happen while the window is unfocused and never arrive -
    and the panel's own keys through `held()`, which covers a key that was already down when the panel
    opened and therefore never sent a press;
  * in `render()` the close comes **after** `allReleased()` and after the hack was sent: the instruction
    order is the assertion. On the build that had the bug, `onClose` follows `send` directly.

    python3 tools/panel_release_check.py settledlands-0.8.0-build387-panel-release.jar

Exit 0 = a solved panel closes only after every held key is released, and a tap cannot escape it while
a held ESC still can.
"""
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

PANEL = "dev/settledlands/door/HackPanel377.class"


def fail(message):
    print("FAIL: " + message)
    return 1


def method_listing(parsed, name):
    cp = parsed["cp"]
    for _acc, name_idx, _desc, attrs in parsed["methods"]:
        if cp[name_idx][1] != name:
            continue
        co = disasm.code_of(attrs, cp)
        if co is None:
            return ""
        listing, _labels = disasm.disasm(co["code"], cp)
        return listing
    return ""


def methods(parsed):
    cp = parsed["cp"]
    return {cp[name_idx][1] for _a, name_idx, _d, _attrs in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        return fail("usage: panel_release_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        if PANEL not in z.namelist():
            return fail("%s is missing" % PANEL)
        parsed = disasm.parse_class(z.read(PANEL))
    cp = parsed["cp"]
    names = methods(parsed)

    for needed in ("keyPressed", "keyReleased", "render", "allReleased", "stillDown"):
        if needed not in names:
            return fail("HackPanel377 has no %s() - the panel cannot know the hands are still on the keys"
                        % needed)

    # ---- the set of keys that went down and has not come up -----------------------------------
    pressed_desc = None
    for _acc, ni, di, _attrs in parsed["fields"]:
        if cp[ni] and cp[ni][0] == 1 and cp[ni][1] == "pressed":
            pressed_desc = cp[di][1] if cp[di] and cp[di][0] == 1 else None
    if pressed_desc is None:
        return fail("HackPanel377 has no `pressed` field - the panel does not remember the keys it saw")
    if pressed_desc != "Ljava/util/Set;":
        return fail("`pressed` is %s, not a java.util.Set" % pressed_desc)

    press = method_listing(parsed, "keyPressed")
    release = method_listing(parsed, "keyReleased")
    released = method_listing(parsed, "allReleased")
    render = method_listing(parsed, "render")

    if "java/util/Set.add(" not in press:
        return fail("keyPressed does not record the key - a key held at the win would be forgotten")
    if "java/util/Set.remove(" not in release:
        return fail("keyReleased does not clear the key - the panel would wait for a release it already had")
    if "InputConstants.isKeyDown(" not in released:
        return fail("allReleased() never asks the hardware - a release while the window was unfocused"
                    " would never arrive and the panel would hang")
    if "java/util/Set.isEmpty(" not in released:
        return fail("allReleased() does not look at the set of held keys")
    if "HackPanel377.held(" not in released:
        return fail("allReleased() ignores the panel's own keys - a key already down when the panel"
                    " opened sends no press and would be missed")

    # ---- the close in render comes after the answer, not instead of it -------------------
    send_at = render.find("DoorHack377.send(")
    if send_at < 0:
        return fail("render() never sends the hack - the panel could not open a door")
    close_at = render.find("HackPanel377.onClose(", send_at)
    if close_at < 0:
        return fail("render() never closes a solved panel - the player would be stuck in it")
    answer_at = render.find("HackPanel377.allReleased(", send_at)
    if answer_at < 0 or answer_at > close_at:
        return fail("a solved panel closes without asking allReleased() - that is the build-386 bug:"
                    " the keys still under the fingers fall through to the game")
    if render.find("HackPanel377.stillDown(", send_at) < 0:
        return fail("a solved panel does not count the keys still held, so the player cannot tell why it"
                    " has not closed yet")

    # ---- the escape hatch is a hold, not a tap -------------------------------------------
    if "escDownAt" not in press:
        return fail("keyPressed does not time the ESC hold - a tap would escape a solved panel")
    if "Screen.keyPressed(" not in press:
        return fail("keyPressed never hands ESC to the screen - an unsolved panel could not be closed")

    text = " ".join(str(v[1]) for v in cp if v and v[0] == 1)
    for line in ("ВЗЛОМ ПРИНЯТ", "отпусти"):
        if line not in text:
            return fail("the solved panel does not say «%s» - it has to explain why it is still here" % line)

    print("panel_release_check: OK  %s  (a solved panel keeps the keyboard: the keys it saw go down and"
          " not up live in a set, allReleased() prunes that set against the hardware and checks the"
          " panel's own keys, render() asks that question before it closes, and ESC only escapes when"
          " it is held)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
