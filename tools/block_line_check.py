#!/usr/bin/env python3
"""Gate: a block can say a line ("Реплика") to the player who clicked it.

Build 357 puts the stock «Реплика» node (type 0, the one every NPC graph has) into the palette of a
block; build 358 hands the line to the stock dialogue window (see block_talk_check.py) and keeps the
chat of 357 as the fallback only. Three things can go wrong and none of them is visible in a
screenshot:

  * the palette offers the row but the server steps over the node - the author writes a line, clicks,
    and nothing is said, without an error anywhere;
  * the row maps to a type the block cannot run, or the palette array and the row map drift apart -
    a row builds a node the walker refuses;
  * the line is sent raw, without the placeholders every other text of the mod understands, or to the
    wrong audience (a radius event may fire for a body that is not a player), or the chat fallback is
    gone so a line with no window possible is dropped.

So this gate asks the shipped jar:

  * the walk has a branch for a line node; that branch opens the dialogue window first (build 358) and
    falls back to one chat helper that fills the stock placeholders ({@code NpcProfile#fill}, relation
    0 for a block) and sends the result to one player;
  * the palette hook does not carry its own map any more: it asks {@code BlockTrigger351#typeForRow},
    so there is one answer to "what can a block build";
  * the map is read by running it: {@code tools/harness/BlockPaletteProbe.java} is compiled against
    this jar and executed - every offered row builds its own type, a row a block cannot run answers
    -1, the row list and the palette array are the same list in the same order, and the line node is
    in while a text button and a timer are out, and a graph holding a line node passes
    {@code NpcDialogueGraph#error} with its text intact after save -> load;
  * the palette note the editor shows names the line node, in both locales.

    python3 tools/block_line_check.py settledlands-0.8.0-build357-block-line.jar

Exit 0 = «Реплика» builds, is said in the chat, and the palette tells the truth.
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
PROBE = ROOT / "tools" / "harness" / "BlockPaletteProbe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

LOGIC = "dev/settledlands/npc/BlockTrigger351.class"
MIXIN = "dev/settledlands/mixin/NpcBlockGraph351Mixin.class"
RU = "assets/settledlands/lang/ru_ru.json"
EN = "assets/settledlands/lang/en_us.json"
NOTE = "gui.settledlands.block.palette"

EXPECT = {
    # Build 365 grew the palette from six rows to eleven; these numbers are the new truth, and the
    # REFUSED list is what forbids the six-row palette of 364 from coming back: 1/7/8/14/15 are now
    # rows a block may build, so a jar that refuses them again fails here.
    "ROWS": "0:0 1:1 2:2 3:3 4:4 5:5 7:7 8:8 13:13 14:14 15:15",
    "PALETTE": "0 1 2 3 4 5 7 8 13 14 15",
    "REFUSED": "6 9 10 11 12",
    "CONSISTENT": "true",
    "ALLOWED": "line=true button=true timer=true",
    "EVENTS": "time=true timer=true cutscene=true target=true",
    "GRAPH": "error=ok text=kept entry=set",
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


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (LOGIC, MIXIN, RU, EN):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        k, cp, methods, refs = load_class(zf, LOGIC)
        walk = ""
        for name, body in methods.items():
            if name.startswith("walk("):
                walk = body
        say = ""
        for name, body in methods.items():
            if name.startswith("say("):
                say = body
        if not say:
            problems.append("BlockTrigger351 has no say() - a line node would be stepped over")
        else:
            for needle, why in (
                    ("Node.text", "the line does not read the node's text"),
                    ("NpcProfile.fill", "the line does not fill the stock %player% placeholders"),
                    ("displayClientMessage", "the line is never shown to the player"),
                    ("Component.literal", "the line is not built as a chat component"),
                    ("iconst_0", "the relation passed to fill() is not 0 - a block has no relation")):
                if needle not in say:
                    problems.append("say() is wrong: %s" % why)
        if not walk:
            problems.append("BlockTrigger351 has no chain walk")
        elif "BlockTrigger351.say(" not in walk:
            problems.append("the chain walk never calls say() - the node would not speak")
        else:
            lines = walk.splitlines()
            call = next(i for i, line in enumerate(lines) if "BlockTrigger351.say(" in line)
            guard = "\n".join(lines[max(0, call - 14):call])
            if "Node.typeI" not in guard or ("ifne" not in guard and "if_icmpne" not in guard):
                problems.append("say() is not reached through the node type - the walk could speak "
                                "for a node that is not a line:\n%s" % guard)
            window = next((i for i, line in enumerate(lines) if "BlockTalk358.say(" in line), -1)
            if window < 0:
                problems.append("the line branch never opens the dialogue window - the chat of build "
                                "357 is back as the only voice")
            elif window > call:
                problems.append("the chat is tried before the dialogue window - build 358 wants the "
                                "window first, the chat only as the fallback")
        if not any(name.startswith("typeForRow(") for name in methods):
            problems.append("BlockTrigger351 has no typeForRow - the palette hook has no map to ask")
        if not any(cp[ni][1] == "PALETTE_ROWS" for _a, ni, _d, _at in k["fields"]):
            problems.append("BlockTrigger351 has no PALETTE_ROWS - the offered rows are not stated")

        _k2, _cp2, mixin_methods, mixin_refs = load_class(zf, MIXIN)
        if not any(r[1] == "typeForRow" and r[0] and r[0].endswith("npc/BlockTrigger351")
                   for r in mixin_refs):
            problems.append("the palette hook does not ask BlockTrigger351#typeForRow - the editor "
                            "and the walker could disagree")

        ru = json.loads(zf.read(RU).decode("utf-8"))
        en = json.loads(zf.read(EN).decode("utf-8"))
        if "Реплика" not in ru.get(NOTE, ""):
            problems.append("the palette note does not name «Реплика» - the editor would list the "
                            "nodes a block can build and leave the new one out")
        if "Line" not in en.get(NOTE, ""):
            problems.append("the English palette note does not name the Line node")
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
        if compiled.returncode != 0 or not (tmpdir / "BlockPaletteProbe.class").is_file():
            return ["the palette probe did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir)] + [str(jar)] + STUBS), "BlockPaletteProbe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the palette probe crashed:\n%s" % output[-800:]]
        problems = []
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("the palette probe says %s = %r, expected %r"
                                % (key, got.get(key), want))
        return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("block_line_check: no such jar %s" % jar)
        return 2
    problems = structural(jar) + round_trip(jar)
    if problems:
        print("block_line_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("block_line_check: OK  %s  («Реплика» is in the block palette, builds its own node, opens "
          "the dialogue window, and falls back to the chat with the stock placeholders filled)"
          % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
