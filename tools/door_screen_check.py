#!/usr/bin/env python3
"""Gate: the graph of a door looks and behaves like the graph of a block.

Build 382 shipped a door's editor that opened the stock canvas and put a door node into it. The author
opened it in game and named the three failures in one sentence - "ты допустил точно те же ошибки что и
при создании графа блока, исправь все сразу" - and all three were answered once already, for the graph
of a block, in builds 359-362. Build 383 ports those answers, and this gate is what keeps them ported:
each check below is one of the failures, written as a question about the jar.

  * **The tabs.** «Главный / Распорядок / Общее» were in the top bar of a door's editor, because the
    strip of build 360 only answered for a block's id. The test in `BlockNoTabs360Mixin` must now
    answer for a door as well (`DoorGraph382#isDoorEditorId`) and must still take the buttons off the
    screen with `WidgetPark361#park` - hiding them was not enough twice.
  * **The inspector of a «Реплика».** The stock switch of the editor has no branch for the door's type
    and fell through to the rows of a topic node: a text box, a trigger, a relation change, min/max of
    relation and of points. Build 359 parked those rows for a name card; the door's rows mixin must
    park them too (`WidgetPark361#park`, `labels` cleared, `inspectorHeight`/`sidebarHeight` reset) and
    build only its own three rows.
  * **The card.** The stock painter drew a pink box titled «Случайность» with the topic lines under our
    text, cut off at the right edge - it has no branch for the door's type. The mixin must cancel
    `drawNode` at HEAD and the card must be painted whole by `DoorCard382`, inside the node box.
  * **The two extra panels.** «Характеристики» is put on every graph by `NpcStatsGraphUi#ensure` and
    «Автономный ИИ» by `NpcAutoAiGraphUi255#ensure` plus the profile sync. Both must be cancelled for a
    door, as build 359/361 cancel them for a block - and the AI panel's `mouseClicked` must **hand the
    click on** to `NpcStatsGraphUi#mouseClicked`, never drop it: that method is the screen's only way
    into the click chain, and dropping it made a block's canvas unclickable in build 361.
  * **The values.** A row written straight into the node is a row that is lost - the lesson of the
    block's name colour in build 362. Every write of the three rows must go through the screen's own
    `propertyChange(Runnable)`, and the scroll range must be grown by their height, or the toggle sits
    below the reachable part of the panel.
  * **The file.** A door's graph must not keep a body's cards: `DoorGraph382#stripCards` drops the
    stats and name types before the graph is handed to the editor and before it is stored.

Usage: python3 tools/door_screen_check.py <jar>
Exit 0 = a door's editor has no tabs, no stock inspector rows, no stats/AI panels, its node is painted
by this mod and its three values are committed through the screen's own path.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

LOGIC = "dev/settledlands/door/DoorGraph382.class"
CARD_MIXIN = "dev/settledlands/mixin/NpcDoorCard382Mixin.class"
CARD = "dev/settledlands/npc/client/DoorCard382.class"
CARD_CLASS = CARD[:-len(".class")]
ROWS_MIXIN = "dev/settledlands/mixin/NpcDoorRows382Mixin.class"
TABS_MIXIN = "dev/settledlands/mixin/BlockNoTabs360Mixin.class"
NO_STATS = "dev/settledlands/mixin/NpcDoorNoStats383Mixin.class"
NO_AI = "dev/settledlands/mixin/NpcDoorNoAi383Mixin.class"
CONFIG = "settledlands.mixins.json"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"

SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen"
# A member the mixin takes over with @Shadow is declared in the mixin class, so the call site of a
# shadowed method or field names the mixin, not the screen. Both owners are accepted: which one the
# compiler picks is an implementation detail, the shadow is the same either way.
ROWS = "dev/settledlands/mixin/NpcDoorRows382Mixin"
STATS_UI = "dev/settledlands/npc/client/NpcStatsGraphUi"
AI_UI = "dev/settledlands/npc/client/NpcAutoAiGraphUi255"
PARK = "dev/settledlands/npc/client/WidgetPark361"
DOOR = "dev/settledlands/door/DoorGraph382"

# The three lines of the card and the title - the ones the author read as «Случайность» and clipped.
CARD_KEYS = ("gui.settledlands.door.node", "gui.settledlands.door.count",
             "gui.settledlands.door.speed", "gui.settledlands.door.sealed",
             "gui.settledlands.door.unsealed", "gui.settledlands.door.hint")


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


def code_text(parsed, name):
    """The bytecode listing of one method, or "" - used where one constant-pool entry is not proof:
    `sidebarHeight = 0` and `sidebarHeight += CONTENT` share their field ref."""
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
    return {cp[name_idx][1] for _acc, name_idx, _desc, _attrs in parsed["methods"]}


def calls(refs, owner, name=None):
    return [ref for ref in refs if ref[0] == owner and (name is None or ref[1] == name)]


def main(argv):
    if len(argv) != 1:
        return fail("usage: door_screen_check.py <jar>")
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        blobs = {n: z.read(n) for n in names}
    for required in (LOGIC, CARD_MIXIN, CARD, ROWS_MIXIN, TABS_MIXIN, CONFIG, RU, EN):
        if required not in blobs:
            return fail("%s is missing - build 383 is not in this jar" % required)
    if NO_STATS not in blobs:
        return fail("NpcDoorNoStats383Mixin is missing - a door's graph would carry a «Характеристики»"
                    " card again")
    if NO_AI not in blobs:
        return fail("NpcDoorNoAi383Mixin is missing - a door's graph would carry an «Автономный ИИ»"
                    " panel again")

    # ---- the two new halves are client mixins --------------------------------------------------
    config = json.loads(blobs[CONFIG].decode("utf-8"))
    client = config.get("client", [])
    common = config.get("mixins", [])
    for name in ("NpcDoorNoStats383Mixin", "NpcDoorNoAi383Mixin", "NpcDoorCard382Mixin",
                 "NpcDoorRows382Mixin"):
        if name not in client:
            return fail("%s is not a client mixin - it would be loaded on the server" % name)
        if name in common:
            return fail("%s is a common mixin - it touches client classes" % name)

    # ---- 1. no tabs ---------------------------------------------------------------------------
    tabs, tabs_refs = parse(blobs[TABS_MIXIN])
    if not calls(tabs_refs, DOOR, "isDoorEditorId"):
        return fail("BlockNoTabs360Mixin does not answer for a door - «Главный / Распорядок / Общее»"
                    " would still be in the top bar of a door's editor")
    if not calls(tabs_refs, PARK, "park"):
        return fail("BlockNoTabs360Mixin does not park the tab buttons - hiding them has come back"
                    " twice already")
    for key in ("tab_main", "tab_walk", "tab_anim"):
        if key not in text(tabs):
            return fail("BlockNoTabs360Mixin lost the tab key %s" % key)

    # ---- 2. no «Характеристики» card ----------------------------------------------------------
    stats, stats_refs = parse(blobs[NO_STATS])
    if "ensure" not in text(stats):
        return fail("NpcDoorNoStats383Mixin does not inject into NpcStatsGraphUi#ensure")
    if not calls(stats_refs, STATS_UI + "$CallbackInfo") and not any(
            m == "cancel" for _o, m, _d in stats_refs):
        return fail("NpcDoorNoStats383Mixin does not cancel the stats card")
    if not calls(stats_refs, "dev/settledlands/npc/BlockName359", "dropType"):
        return fail("NpcDoorNoStats383Mixin does not drop a stats card that is already in the graph")
    if not calls(stats_refs, DOOR, "isDoorEditorId"):
        return fail("NpcDoorNoStats383Mixin does not check for a door's editor")

    # ---- 3. no «Автономный ИИ» panel, and the click is handed on ------------------------------
    ai, ai_refs = parse(blobs[NO_AI])
    ai_text = text(ai)
    for method in ("ensure", "draw", "mouseClicked"):
        if method not in ai_text:
            return fail("NpcDoorNoAi383Mixin does not inject into NpcAutoAiGraphUi255#%s" % method)
    if not calls(ai_refs, STATS_UI, "mouseClicked"):
        return fail("NpcDoorNoAi383Mixin does not hand the click on to NpcStatsGraphUi#mouseClicked -"
                    " a door's canvas would stop answering the mouse, the way a block's did in 361")
    if not calls(ai_refs, DOOR, "isDoorEditorId"):
        return fail("NpcDoorNoAi383Mixin does not check for a door's editor")

    # ---- 4. the inspector: stock rows parked, three own rows, every write committed -------------
    rows, rows_refs = parse(blobs[ROWS_MIXIN])
    owned = {SCREEN, ROWS}
    if not any(r[0] in owned and r[1] == "propertyChange" for r in rows_refs):
        return fail("NpcDoorRows382Mixin does not write through propertyChange - the values of a door"
                    " node would be lost, the way the block's name colour was in 362")
    if any(r[0] in owned and r[1] in ("remember", "changed") for r in rows_refs):
        return fail("NpcDoorRows382Mixin still takes its own undo point / dirty flag instead of the"
                    " screen's commit")
    if not calls(rows_refs, PARK, "park"):
        return fail("NpcDoorRows382Mixin does not park the stock rows - the inspector of a door would"
                    " show the rows of a «Реплика» again")
    if not [r for r in rows_refs if r[0] in owned and r[1] == "labels"]:
        return fail("NpcDoorRows382Mixin does not clear the labels of the parked rows")
    if not [r for r in rows_refs if r[0] in owned and r[1] == "inspectorWidgets"]:
        return fail("NpcDoorRows382Mixin does not walk the inspector's own widget list")
    if not [r for r in rows_refs if r[0] in owned and r[1] == "sidebarHeight"]:
        return fail("NpcDoorRows382Mixin does not touch sidebarHeight - the toggle would sit below the"
                    " reachable part of the panel")
    body = code_text(rows, "settledlands$doorRows")
    stores = [line for line in body.splitlines() if "sidebarHeight" in line and "putfield" in line]
    if len(stores) < 2:
        return fail("NpcDoorRows382Mixin resets sidebarHeight but does not grow it by the height of the"
                    " three rows - the toggle would be unreachable")
    if not calls(rows_refs, "dev/settledlands/npc/client/NpcUi", "slider"):
        return fail("NpcDoorRows382Mixin has no real slider")
    if not any(r[0] in owned and r[1] == "button" for r in rows_refs):
        return fail("NpcDoorRows382Mixin has no «закрыта/открыта» toggle")
    if not calls(rows_refs, DOOR, "setKeys") or not calls(rows_refs, DOOR, "setSpeed"):
        return fail("NpcDoorRows382Mixin does not write the two numbers into the node")
    if not calls(rows_refs, DOOR, "setSealed"):
        return fail("NpcDoorRows382Mixin does not write the «закрыта/открыта» flag")

    # ---- 5. the card is ours, whole, and inside the node box -----------------------------------
    card_mixin, card_mixin_refs = parse(blobs[CARD_MIXIN])
    if not any(m == "cancel" for _o, m, _d in card_mixin_refs):
        return fail("NpcDoorCard382Mixin does not cancel the stock painter - the node of a door would"
                    " be the pink «Случайность» box again")
    if not calls(card_mixin_refs, CARD_CLASS, "draw"):
        return fail("NpcDoorCard382Mixin does not hand the node to DoorCard382")
    card, card_refs = parse(blobs[CARD])
    card_text = text(card)
    if not any(m == "drawString" for _o, m, _d in card_refs):
        return fail("DoorCard382 draws no text")
    if not any(m == "fill" for _o, m, _d in card_refs):
        return fail("DoorCard382 paints no box")
    for key in CARD_KEYS:
        if key not in card_text:
            return fail("DoorCard382 does not draw %s - a line of the card would be missing" % key)
    if not any(m in ("screenX", "screenY") for _o, m, _d in card_refs):
        return fail("DoorCard382 ignores the canvas transform - the card would be drawn at 0,0")
    if not any(m == "zoom" for _o, m, _d in card_refs):
        return fail("DoorCard382 ignores the zoom - the card would not match the node box")
    if not any(m == "pushPose" for _o, m, _d in card_refs) or not any(
            m == "popPose" for _o, m, _d in card_refs):
        return fail("DoorCard382 translates the pose without restoring it")

    # ---- 6. the file of a door holds the door and nothing else ---------------------------------
    logic, logic_refs = parse(blobs[LOGIC])
    if "stripCards" not in methods(logic):
        return fail("DoorGraph382#stripCards is missing - a door's file would keep a body's cards")
    if not calls(logic_refs, "dev/settledlands/npc/BlockName359", "dropType"):
        return fail("DoorGraph382#stripCards drops nothing")
    if not any(m == "containsKey" for _o, m, _d in logic_refs):
        return fail("DoorGraph382#stripCards does not repair an entry that pointed at a dropped card")

    # ---- 7. the lines the card draws exist in both languages -----------------------------------
    for path in (RU, EN):
        strings = json.loads(blobs[path].decode("utf-8"))
        for key in CARD_KEYS:
            if key not in strings:
                return fail("%s has no %s" % (path.rsplit("/", 1)[-1], key))

    print("door_screen_check: OK  %s  (a door's editor has no tab strip, no rows of a «Реплика», no"
          " «Характеристики» card and no «Автономный ИИ» panel, its node is painted whole by this mod"
          " inside the node box, its three rows commit through propertyChange and the scroll range is"
          " grown for them, the AI click is handed on instead of dropped, and a door's graph keeps"
          " neither of the body's cards)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
