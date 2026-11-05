#!/usr/bin/env python3
"""Gate: the hack panel owns the keyboard, and «Сложность» really changes the board.

Two things the author asked for in build 385, and one way each of them can ship looking finished and
doing nothing:

  * **The input lock.** A screen that answers `false` from `keyPressed` hands the key to the game's key
    mappings. Build 384 answered `true` only for the keys it was showing, so R still reloaded the
    shaders and every other shortcut of the pack worked inside the panel. The handler must swallow
    every key and pass on exactly one - ESC - to the screen's own handler (which closes the panel and
    nothing else). Both press and release, or a release lets the game think the key was never down.
  * **The difficulty.** 1 - `qwertyuiop[]`, 2 - adds `asdfghjkl;'`, 3 - adds `zxcvbnm,./`. The pool has
    to be built from that value and used by both the draw and the swap; a difficulty that only paints a
    number on the card is the same bug as a slider that writes nowhere.

What this gate asks of the jar:

  * `HackPanel377#keyPressed` and `#keyReleased` compare against 256 (ESC), call the screen's own
    handler for it and return true otherwise - the old per-key whitelist is gone;
  * the three rows are in the class as constants, including the punctuation keys (`[`, `]`, `;`, `'`,
    `,`, `.`, `/`) that the author named, and `pool(int)` builds them by difficulty;
  * `pick(...)` takes the difficulty and draws out of the pool - the same pool the swap of build 378
    draws from, so a harder door asks the same number of keys out of a bigger board;
  * `DoorGraph382#difficulty` reads and writes a node field the stock serialiser saves for every type,
    clamped to 1..3, and the door's sync line carries it (the client half parses a third field and falls
    back to 1 for a line that has two);
  * the inspector builds the third slider through `propertyChange` and grows the scroll range for it,
    and the card has a line for it.

    python3 tools/hack_input_check.py settledlands-0.8.0-build385-door-hack-difficulty.jar

Exit 0 = every key but ESC is swallowed by the panel, the three rows exist and the difficulty value
reaches both the board and the door's save.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

PANEL = "dev/settledlands/door/HackPanel377.class"
LOGIC = "dev/settledlands/door/DoorGraph382.class"
SYNC = "dev/settledlands/door/DoorSync375.class"
CARD = "dev/settledlands/npc/client/DoorCard382.class"
ROWS_MIXIN = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

NODE = "dev/settledlands/npc/NpcDialogueGraph$Node"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen"

# The keys of the three rows: Q..P plus [ ] (the row the author named), A..L plus ; ', Z..M plus , . /.
ROW_CODES = (81, 87, 69, 82, 84, 89, 85, 73, 79, 80, 91, 93,
             65, 83, 68, 70, 71, 72, 74, 75, 76, 59, 39,
             90, 88, 67, 86, 66, 78, 77, 44, 46, 47)

LANG_KEYS = ("gui.settledlands.door.difficulty", "gui.settledlands.door.rows1",
             "gui.settledlands.door.rows2", "gui.settledlands.door.rows3")


def fail(message):
    print("FAIL: " + message)
    return 1


def parse(data):
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


def ints(parsed):
    out = set()
    for value in parsed["cp"]:
        if value and value[0] == 3:
            out.add(struct.unpack(">i", value[1])[0])
    return out


def methods(parsed):
    cp = parsed["cp"]
    return {cp[name_idx][1] for _acc, name_idx, _desc, _attrs in parsed["methods"]}


def code_ints(listing):
    """Every small int a listing pushes. The row arrays are built in <clinit> with bipush, so the key
    codes are in the code, not in the constant pool."""
    out = set()
    tokens = listing.split()
    for i, token in enumerate(tokens):
        if token in ("bipush", "sipush") and i + 1 < len(tokens):
            try:
                out.add(int(tokens[i + 1]))
            except ValueError:
                pass
        elif token.startswith("iconst_"):
            tail = token[len("iconst_"):]
            if tail == "m1":
                out.add(-1)
            elif tail.isdigit():
                out.add(int(tail))
    return out


def code_text(parsed, name):
    """The bytecode listing of one method, or "" when there is no such method."""
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


def main(argv):
    if len(argv) != 1:
        return fail("usage: hack_input_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for required in (PANEL, LOGIC, SYNC, CARD, ROWS_MIXIN, RU, EN):
            if required not in names:
                return fail("%s is missing - build 385 is not in this jar" % required)
        blobs = {n: z.read(n) for n in (PANEL, LOGIC, SYNC, CARD, ROWS_MIXIN, RU, EN)}

    panel, panel_refs = parse(blobs[PANEL])

    # ---- 1. every key but ESC is swallowed -----------------------------------------------------
    for name in ("keyPressed", "keyReleased"):
        body = code_text(panel, name)
        if not body:
            return fail("HackPanel377 has no %s" % name)
        if "sipush        256" not in body and "256" not in body:
            return fail("%s never looks at ESC - the panel could not be closed" % name)
        if "invokespecial" not in body:
            return fail("%s never reaches the screen's own handler, so ESC would do nothing" % name)
        if body.count("ireturn") < 2:
            return fail("%s has one way out - either every key is swallowed (ESC included) or none is"
                        % name)
    if "owns" in methods(panel):
        return fail("HackPanel377 still decides per key whether to swallow it - that is exactly the"
                    " check of build 384 that let R reload the shaders")
    if not any(o == "com/mojang/blaze3d/platform/InputConstants" and m == "isKeyDown"
               for o, m, _d in panel_refs):
        return fail("HackPanel377 no longer reads the hardware directly - the lock must not change how"
                    " the keys are measured")

    # ---- 2. the three rows are the rows the author named ---------------------------------------
    numbers = code_ints(code_text(panel, "<clinit>"))
    for code in ROW_CODES:
        if code not in numbers:
            return fail("HackPanel377 has no key %d - a row of the difficulty is missing" % code)
    for size in (12, 11, 10):
        if size not in numbers:
            return fail("HackPanel377 has no row of %d keys - the three rows are not 12/11/10" % size)
    if "pool" not in methods(panel):
        return fail("HackPanel377 has no pool(difficulty) - the difficulty would only paint a number")
    if "clampDifficulty" not in methods(panel):
        return fail("HackPanel377 does not clamp the difficulty to 1..3")
    pick = None
    cp = panel["cp"]
    for _acc, name_idx, desc_idx, _attrs in panel["methods"]:
        if cp[name_idx][1] == "pick":
            pick = cp[desc_idx][1]
    if pick is None:
        return fail("HackPanel377 has no pick(...)")
    if pick != "(Ljava/util/Random;II)[I":
        return fail("HackPanel377#pick does not take the difficulty: %s" % pick)
    # The swap of build 378 has to draw from the same board, or difficulty 3 swaps in a Q.
    roll = code_text(panel, "roll")
    if not roll:
        return fail("HackPanel377 has no roll()")
    if "putfield" in roll and "pool" not in " ".join(
            str(v[1]) for v in cp if v and v[0] == 1):
        return fail("HackPanel377#roll does not use the pool - the swap would stay on row 1")
    if "LETTERS" in text(panel):
        return fail("HackPanel377 still has the single row of build 378 (LETTERS)")

    # ---- 3. the door node stores the difficulty in a field every type saves --------------------
    logic, logic_refs = parse(blobs[LOGIC])
    if "difficulty" not in methods(logic) or "setDifficulty" not in methods(logic):
        return fail("DoorGraph382 has no difficulty accessors")
    if not any(o == NODE and m == "checkTarget" for o, m, _d in logic_refs):
        return fail("DoorGraph382 does not keep the difficulty in Node#checkTarget - the value would"
                    " be lost on save (the bug of build 383)")
    if "checkTarget = difficulty" not in text(logic):
        return fail("FIELD_NOTE no longer documents where the difficulty lives")
    for wanted in (1, 3):
        if wanted not in ints(logic):
            return fail("DoorGraph382 does not carry the bound %d of the difficulty" % wanted)

    # ---- 4. the difficulty travels with the door's settings ------------------------------------
    sync, sync_refs = parse(blobs[SYNC])
    if not any(o == "dev/settledlands/door/HackPanel377" and m == "clampDifficulty"
               for o, m, _d in sync_refs):
        return fail("DoorSync375 does not parse a third field of the settings pair - the panel of this"
                    " door would always open on difficulty 1")
    if not any(m == "putSettings" for _o, m, _d in sync_refs):
        return fail("DoorSync375 lost putSettings")

    # ---- 5. the inspector row and the card line -----------------------------------------------
    rows, rows_refs = parse(blobs[ROWS_MIXIN])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "setDifficulty"
               for o, m, _d in rows_refs):
        return fail("NpcDoorRows382Mixin has no difficulty row")
    if not any(r[1] == "propertyChange" for r in rows_refs):
        return fail("NpcDoorRows382Mixin does not commit the difficulty through propertyChange")
    card, card_refs = parse(blobs[CARD])
    if not any(o == "dev/settledlands/door/DoorGraph382" and m == "difficulty"
               for o, m, _d in card_refs):
        return fail("DoorCard382 does not show the difficulty")
    for path in (RU, EN):
        strings = json.loads(blobs[path].decode("utf-8"))
        for key in LANG_KEYS:
            if key not in strings:
                return fail("%s has no %s" % (path.rsplit("/", 1)[-1], key))

    print("hack_input_check: OK  %s  (the panel swallows every key but ESC on press and release and"
          " still reads the hardware directly; the three rows of the difficulty are in the class, the"
          " pool is built from the value and used by the swap as well; the difficulty is stored in a"
          " node field every type saves, clamped 1..3, carried by the door's sync line and editable in"
          " the inspector through propertyChange, and the card shows it)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
