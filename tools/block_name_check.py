#!/usr/bin/env python3
"""Gate: a block has a name card, and it is the card a block's dialogue speaks under.

Build 359 takes the two body cards off a block's canvas and puts one card of its own in their place.
Seven things can go wrong and almost none of them throws:

  * the name card is never created - the canvas is empty, the block speaks under its coordinates, and
    the author has nowhere to type a name;
  * it is created for a body's graph too - a body would get a card it cannot use;
  * it is created twice - two names that disagree about what the block is called;
  * the stock drawing keeps the node - an unknown type is drawn with the title of «Случайность» and
    no text, which is the one thing a name card must not say;
  * the «Характеристики» card and the «Автономный ИИ» panel are still put on a block's graph - the
    author sees cards that mean nothing for a block, which is what this build was asked to remove;
  * the inspector rows are missing - the card shows a name that cannot be edited;
  * the dialogue keeps reading the coordinates - the name is written but never spoken.

So this gate asks the shipped jar:

  * the seven new classes are there, the five new mixins are in the config (one common, four client:
    the stats/AI hooks are client classes, and a dedicated server has no screen to hook), and the
    single-card guard is a common mixin because NpcStatsGraph is a common class;
  * the stats and AI calls the screen's constructor makes are both taken over, for a block id only;
  * drawNode hands a name node to BlockNameCard359 instead of the stock card, and the card is drawn in
    the block's colour - the colour the window is told;
  * the inspector adds the name box and the colour dropdown at the end of widgets(), inside
    buildingInspector, grows the scroll range by the rows it added, and parks the stock rows and
    labels of the card (build 361) so the panel is the name and the colour only;
  * the block's save drops the stats card, and the walk steps over the name card in silence;
  * BlockTalk358#say reads the name and the colour, and never falls back to the coordinates - build
    361: the name of a block is its name, and a block without one has an empty name;
  * the colour dropdown is registered by calling Screen#addRenderableWidget the way the stock textBox
    does, and NOT by a @Shadow - build 359 did shadow it and every graph screen died on open, because
    Mixin looks a shadow up in the target class only and the member is inherited;
  * a block's graph has no tabs: the tab buttons the stock widgets() builds are parked out of the
    window for a block id and only for it (hidden and moved, not only flagged - build 360 hid them and
    the game still showed them), so a body's editor keeps its three graphs;
  * the arithmetic and the round trip are read by running them: tools/harness/BlockNameProbe.java is
    compiled against this jar and executed.

    python3 tools/block_name_check.py settledlands-0.8.0-build359-block-name.jar

Exit 0 = a block has exactly one name card, its name and colour reach the dialogue, and the body
cards are gone from a block's graph.
"""
import json
import pathlib
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "BlockNameProbe.java"
BASE = ROOT / "settledlands-0.8.0-build358-block-talk.jar"
TABS = "dev/settledlands/mixin/BlockNoTabs360Mixin.class"
PARK = "dev/settledlands/npc/client/WidgetPark361.class"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

NAME = "dev/settledlands/npc/BlockName359.class"
CARD = "dev/settledlands/npc/client/BlockNameCard359.class"
DRAW = "dev/settledlands/mixin/BlockNameDraw359Mixin.class"
INSPECT = "dev/settledlands/mixin/BlockNameInspector359Mixin.class"
NO_STATS = "dev/settledlands/mixin/BlockNoStats359Mixin.class"
NO_AI = "dev/settledlands/mixin/BlockNoAi359Mixin.class"
ONE = "dev/settledlands/mixin/BlockNameOne359Mixin.class"
TALK = "dev/settledlands/npc/BlockTalk358.class"
BLOCK = "dev/settledlands/npc/BlockTrigger351.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"
MIXINS_JSON = "settledlands.mixins.json"

EXPECT = {
    "ENSURE": "added=true again=false cards=1 name='' color=0 where=left",
    "SET": "name='Forge' color=2 rgb=-11702",
    "CLAMP": "high=3 low=0",
    "LENGTH": "kept=32 of 40",
    "ROUND": "name='Forge' color=2 nodes=2",
    "DROP": "stats=true dropped=1 left=0 name=true",
    "PLAIN": "find=null name='' color=0",
}


def load_class(zf, name):
    k = disasm.parse_class(zf.read(name))
    cp = k["cp"]
    methods = {}
    for _acc, ni, di, attrs in k["methods"]:
        code = disasm.code_of(attrs, cp)
        if code is None:
            continue
        methods[cp[ni][1] + cp[di][1]] = disasm.disasm(code["code"], cp)[0]
    refs = set()
    for value in cp:
        if not value or value[0] not in (9, 10, 11):
            continue
        ci, nti = int.from_bytes(value[1][:2], "big"), int.from_bytes(value[1][2:4], "big")
        try:
            owner = disasm.cls_name(cp, ci)
            name_, desc = disasm.nat(cp, nti)
        except Exception:
            continue
        refs.add((owner, name_, desc))
    return k, cp, methods, refs


def strings(cp):
    return {value[1] for value in cp if value and value[0] == 1}


def find_method(methods, prefix):
    for name, body in methods.items():
        if name.startswith(prefix):
            return body
    return ""


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (NAME, CARD, DRAW, INSPECT, NO_STATS, NO_AI, ONE, TABS, PARK, TALK, BLOCK):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        mixins = json.loads(zf.read(MIXINS_JSON).decode("utf-8"))
        common = mixins.get("mixins", [])
        client = mixins.get("client", [])
        if "BlockNameOne359Mixin" not in common:
            problems.append("BlockNameOne359Mixin is not a common mixin - NpcStatsGraph is a "
                            "common class and the guard has to be there on both sides")
        for name in ("BlockNameDraw359Mixin", "BlockNameInspector359Mixin",
                     "BlockNoStats359Mixin", "BlockNoAi359Mixin", "BlockNoTabs360Mixin"):
            if name not in client:
                problems.append("%s is not a client mixin - its target is a client class" % name)
            if name in common:
                problems.append("%s is a common mixin - a dedicated server has no "
                                "NpcDialogueGraphScreen to hook" % name)

        # ---- the card itself ----------------------------------------------
        _k, name_cp, name_methods, _r = load_class(zf, NAME)
        if not find_method(name_methods, "ensure("):
            problems.append("BlockName359 has no ensure() - a block would get no name card")
        if not find_method(name_methods, "dropType("):
            problems.append("BlockName359 has no dropType() - a stats card could not be dropped")
        palette = strings(name_cp)
        for key in ("gui.settledlands.name.title", "gui.settledlands.name.field",
                    "gui.settledlands.name.color", "gui.settledlands.name.color.0",
                    "gui.settledlands.name.color.1", "gui.settledlands.name.color.2",
                    "gui.settledlands.name.color.3", "gui.settledlands.name.empty"):
            if key not in palette:
                problems.append("BlockName359 does not name %s - the row would show a raw key" % key)
        ensure = find_method(name_methods, "ensure(")
        if "NpcDialogueGraph.add" not in ensure:
            problems.append("ensure() does not add the node to the graph")
        if "BlockName359.clampCoord" not in ensure:
            problems.append("ensure() does not clamp the card's position - a graph with a node at "
                            "the edge would put the card outside the canvas")
        clamp = find_method(name_methods, "clampCoord(")
        if "Math.min" not in clamp or "Math.max" not in clamp:
            problems.append("clampCoord() is not a clamp - the card could be put outside the canvas")

        # ---- the two body cards -------------------------------------------
        _ks, stats_cp, stats_methods, _rs = load_class(zf, NO_STATS)
        stats_strings = strings(stats_cp)
        for needle, why in (
                ("ensure", "the stats hook does not name NpcStatsGraphUi#ensure - the card would "
                          "still be put on a block's graph"),
                ("dev.settledlands.npc.client.NpcStatsGraphUi",
                 "the stats hook does not target NpcStatsGraphUi"),
                ("cancellable", "the stats hook cannot cancel - the stock call would run too")):
            if needle not in stats_strings:
                problems.append("the stats hook is wrong: %s" % why)
        body = find_method(stats_methods, "settledlands$blockNameInsteadOfStats")
        for needle, why in (
                ("BlockTrigger351.isBlockEditorId", "the hook does not check for a block - a body "
                                                    "would lose its stats card"),
                ("BlockName359.dropType", "an older stats card is never dropped"),
                ("BlockName359.ensure", "the name card is never put in its place"),
                ("NpcDialogueGraphScreen.settledlands$changed", "the editor is not told the graph "
                                                                "it opened is not the graph it saves")):
            if needle not in body:
                problems.append("the stats hook is wrong: %s" % why)

        _ka, ai_cp, _am, _ra = load_class(zf, NO_AI)
        ai_strings = strings(ai_cp)
        for needle, why in (
                ("ensure", "the AI hook does not name NpcAutoAiGraphUi255#ensure - the panel would "
                          "still appear on a block"),
                ("dev.settledlands.npc.client.NpcAutoAiGraphUi255",
                 "the AI hook does not target NpcAutoAiGraphUi255"),
                ("cancellable", "the AI hook cannot cancel")):
            if needle not in ai_strings:
                problems.append("the AI hook is wrong: %s" % why)
        if "BlockTrigger351.isBlockEditorId" not in find_method(
                _am, "settledlands$blockWithoutAi"):
            problems.append("the AI hook does not check for a block - a body would lose its panel")
        # Build 361: cancelling the sync was not enough (the card came back in game through the profile
        # snapshot), so the drawing and the click are cancelled as well.
        for needle, why in (("draw", "the panel is not cancelled where it is drawn"),
                            ("mouseClicked", "a click on a panel that is not drawn is still taken")):
            if needle not in ai_strings:
                problems.append("the AI hook is wrong: %s" % why)
        ai_draw = find_method(_am, "settledlands$blockWithoutAiDraw")
        for needle, why in (("cancel", "the panel is still painted on a block's canvas"),
                            ("BlockTrigger351.isBlockEditorId",
                             "a body's canvas would lose its AI panel too")):
            if needle not in ai_draw:
                problems.append("the AI draw hook is wrong: %s" % why)
        if "setReturnValue" not in find_method(_am, "settledlands$blockWithoutAiClick"):
            problems.append("the AI click hook never reports the click as taken")

        _ko, _oc, one_methods, _ro = load_class(zf, ONE)
        one = find_method(one_methods, "settledlands$singleNameCard")
        for needle, why in (
                ("BlockName359.find", "a second name card is not refused"),
                ("setReturnValue", "the refused card is not reported back to the caller")):
            if needle not in one:
                problems.append("the one-card guard is wrong: %s" % why)

        # ---- drawing -------------------------------------------------------
        _kd, draw_cp, draw_methods, _rd = load_class(zf, DRAW)
        draw = find_method(draw_methods, "settledlands$drawBlockNameCard")
        for needle, why in (
                ("BlockNameCard359.draw", "the card is not drawn by the card class"),
                ("BlockTrigger351.isBlockEditorId", "a body's graph would also get the card"),
                ("cancel", "the stock card is still drawn - it labels an unknown type «Случайность»")):
            if needle not in draw:
                problems.append("the draw hook is wrong: %s" % why)
        drawnode = ("drawNode(Lnet/minecraft/client/gui/GuiGraphics;"
                    "Ldev/settledlands/npc/NpcDialogueGraph$Node;)V")
        if drawnode not in strings(draw_cp):
            problems.append("the draw hook does not name drawNode")

        _kc, card_cp, card_methods, _rc = load_class(zf, CARD)
        paint = find_method(card_methods, "draw(")
        for needle, why in (
                ("BlockName359.rgb", "the card is not drawn in the block's colour"),
                ("BlockName359.nameOf", "the card does not show the block's name"),
                ("NpcGraphCanvas.screenX", "the card is not placed on the canvas"),
                ("GuiGraphics.drawString", "the card writes nothing")):
            if needle not in paint:
                problems.append("the card is wrong: %s" % why)

        # ---- the inspector rows -------------------------------------------
        _ki, insp_cp, insp_methods, _ri = load_class(zf, INSPECT)
        rows = find_method(insp_methods, "settledlands$blockNameRows")
        whole = "\n".join(insp_methods.values())
        for needle, why in (
                ("NpcUi.dropdown", "there is no colour dropdown"),
                ("BlockName359.nameOf", "the name box is not filled with the block's name"),
                ("BlockName359.setName", "the name box does not write the name"),
                ("BlockName359.setColor", "the dropdown does not write the colour"),
                ("sidebarHeight", "the scroll range is not grown - the colour row could be "
                                  "unreachable")):
            if needle not in whole:
                problems.append("the inspector rows are wrong: %s" % why)
        # Build 361: the stock rows the editor builds for an unknown node type are taken away, so the
        # card's inspector is the name and the colour and nothing else.
        for needle, why in (
                ("WidgetPark361.park", "the stock rows of the card are still built and kept"),
                ("labels", "the stock labels of the card are still drawn"),
                ("inspectorWidgets", "the stock widgets of the card are never looked at")):
            if needle not in whole:
                problems.append("the inspector rows are wrong: %s" % why)
        if "widgets()V" not in strings(insp_cp):
            problems.append("the inspector hook does not name widgets()")

        # ---- the block's own code -----------------------------------------
        _kb, block_cp, block_methods, _rb = load_class(zf, BLOCK)
        save = find_method(block_methods, "save(")
        if "BlockName359.dropType" not in save:
            problems.append("a block's save keeps a stats card that arrived with the graph")
        walk = find_method(block_methods, "walk(")
        if "BlockName359.isName" not in walk:
            problems.append("the walk does not step over the name card - it would be reported as a "
                            "node a block cannot run, once per session")

        _kt, _tc, talk_methods, _rt = load_class(zf, TALK)
        say = find_method(talk_methods, "say(")
        for needle, why in (
                ("BlockName359.name", "the window still opens under the coordinates only"),
                ("BlockName359.color", "the name's colour never reaches the window"),
                ("NpcProfile.nameColor", "the window is not told which colour to paint the name")):
            if needle not in say:
                problems.append("say() is wrong: %s" % why)
        # Build 361: the coordinates are not a name. The speaker is the name and only the name, so the
        # helper that turned a block into "x y z" must not be called by the window any more.
        if "BlockTalk358.where" in say:
            problems.append("say() still writes the block's coordinates - the name of a block is its "
                            "name, and a block without one has an empty name")

        # ---- the dropdown's registration -----------------------------------
        registered = ("(Lnet/minecraft/client/gui/components/events/GuiEventListener;)"
                      "Lnet/minecraft/client/gui/components/events/GuiEventListener;")
        _ki2, insp_cp2, insp_methods2, insp_refs2 = load_class(zf, INSPECT)
        if not any(ref[1] == "addRenderableWidget" and ref[2] == registered
                   for ref in insp_refs2):
            problems.append("the inspector does not register the dropdown through "
                            "addRenderableWidget - the colour row would never be drawn")
        if any(m.startswith("addRenderableWidget(") for m in insp_methods2):
            problems.append("the inspector hook declares addRenderableWidget itself - Mixin looks a "
                            "@Shadow up in the target class only, so this is the crash of build 359")

        # ---- no tabs on a block's graph -------------------------------------
        _kt2, tabs_cp, tabs_methods, tabs_refs = load_class(zf, TABS)
        tabs = find_method(tabs_methods, "settledlands$blockHasNoTabs")
        for needle, why in (
                ("BlockTrigger351.isBlockEditorId", "a body's editor would lose its tabs too"),
                ("NpcDialogueGraphScreen.entityId", "the hook never asks which screen it is on"),
                ("WidgetPark361.park", "the tab button is never taken off the screen")):
            if needle not in tabs:
                problems.append("the tabs hook is wrong: %s" % why)
        # Build 361: hiding a flag was not enough in game (360 came back with the tabs on screen), so
        # the park has to hide the button AND move it out of the window - both, in one helper.
        _kp, park_cp, park_methods, _rp = load_class(zf, PARK)
        park_all = "\n".join(park_methods.values())
        park_strings = strings(park_cp)
        for needle, why in (
                ("visible", "a parked widget is never hidden"),
                ("active", "a parked widget is never made unclickable"),
                ("setPosition", "a parked widget is never moved - a hidden flag can be set back"),
                ("setY", "the second way of moving a widget is gone"),
                ("setHeight", "the third way of moving a widget is gone")):
            if needle not in park_all and needle not in park_strings:
                problems.append("the park helper is wrong: %s" % why)
        if "0x1" not in park_all and "invoke" not in park_all:
            problems.append("the park helper does not call anything - every widget would stay put")
        tab_button = "(Ljava/lang/String;IIIILjava/lang/Runnable;)Lnet/minecraft/client/gui/components/Button;"
        if not any(ref[1] == "button" and ref[2] == tab_button for ref in tabs_refs):
            problems.append("the tabs hook does not wrap the stock button() that builds the strip")
        tab_strings = strings(tabs_cp)
        for needle, why in (
                ("widgets()V", "the tabs hook does not run in widgets(), where the strip is built"),
                ("gui.settledlands.graph.tab_", "the hook does not recognise the tab labels")):
            if needle not in tab_strings:
                problems.append("the tabs hook is wrong: %s" % why)
    return problems


def round_trip(jar):
    if not PROBE.is_file():
        return ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "BlockNameProbe.class").is_file():
            return ["the name probe did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS), "BlockNameProbe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the name probe crashed:\n%s" % output[-800:]]
        problems = []
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("the name probe says %s = %r, expected %r"
                                % (key, got.get(key), want))
        return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("block_name_check: no such jar %s" % jar)
        return 2
    problems = structural(jar) + round_trip(jar)
    if problems:
        print("block_name_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("block_name_check: OK  %s  (a block has one name card, the name and its colour reach the "
          "dialogue, and a block's graph carries no «Характеристики» and no «Автономный ИИ»)"
          % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
