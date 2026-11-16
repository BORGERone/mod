#!/usr/bin/env python3
"""Gate: a block's «Реплика» opens the real dialogue window, with the camera on the block.

Build 358 replaces the chat line of build 357 with the stock dialogue screen. Four things can go wrong
and none of them throws:

  * the aim packet is never sent, or is sent after the window - the window opens, the camera stays
    where the player was looking, and the block that is speaking is behind or beside him;
  * the aim is not the screen's own arithmetic (or is transposed) - the head turns the wrong way;
  * the pick/close handshake is missing - the window opens and the chain either never continues (a
    click lands in the stock handler that has no session for a block id) or continues at the line
    itself, which loops the same line forever, or ignores Esc;
  * the fallback is gone - a line with no window possible (no player, an unknown style) is dropped
    instead of said in the chat.

So this gate asks the shipped jar:

  * the six new classes are there, the two new mixins are registered as common mixins (a client-side
    registration would leave a dedicated server calling a class it does not have), and neither is in
    the client list;
  * the pick mixin injects at HEAD, cancellably, into GuideDialogue#pick; the stop mixin into
    GuideDialogue#stop - the two entries every click and every Esc of a window arrives at;
  * BlockTalk358#say sends the aim packet before the window, and the window carries the block's style;
  * the style is the stock GuideDialogue#style, reached reflectively (build 342: an @Invoker in an
    interface mixin is refused for a class target), never rebuilt by hand;
  * the client aim sets both the rotation and the previous rotation, through the same
    BlockTalk358#aimAngles the probe exercises;
  * the walk's line branch calls BlockTalk358#say first and keeps the chat only after it;
  * the aim arithmetic, the aim packet and the "continue after the line" mapping are read by running
    them: tools/harness/BlockTalkProbe.java is compiled against this jar and executed.

    python3 tools/block_talk_check.py settledlands-0.8.0-build358-block-talk.jar

Exit 0 = the window opens on a block, the camera is on the block, and the chain waits for the click.
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
PROBE = ROOT / "tools" / "harness" / "BlockTalkProbe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

TALK = "dev/settledlands/npc/BlockTalk358.class"
WHERE = "dev/settledlands/npc/BlockTalk358$Where.class"
OPEN = "dev/settledlands/npc/BlockTalk358$Open.class"
CLIENT = "dev/settledlands/npc/client/BlockTalkClient358.class"
PICK = "dev/settledlands/mixin/BlockTalkPick358Mixin.class"
STOP = "dev/settledlands/mixin/BlockTalkStop358Mixin.class"
WALKER = "dev/settledlands/npc/BlockTrigger351.class"
RADIUS = "dev/settledlands/npc/RadiusPass354.class"
OPENER = "dev/settledlands/mixin/NpcBlockOpen351Mixin.class"
MIXINS_JSON = "settledlands.mixins.json"

EXPECT = {
    "AIM": "-90.00 26.57 | 90.00 36.87 | 0.00 -9.46",
    "MARKER": "round=same plain=null",
    "NEXT": "line=ok end=-1 missing=-1 null=-1",
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
        for needed in (TALK, WHERE, OPEN, CLIENT, PICK, STOP, WALKER, RADIUS, OPENER):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        mixins = json.loads(zf.read(MIXINS_JSON).decode("utf-8"))
        common = mixins.get("mixins", [])
        client = mixins.get("client", [])
        for name in ("BlockTalkPick358Mixin", "BlockTalkStop358Mixin"):
            if name not in common:
                problems.append("%s is not a common mixin - a dedicated server would not have it"
                                % name)
            if name in client:
                problems.append("%s is a client mixin - the pick/close handshake is server side" % name)
        if "NpcBlockOpen351Mixin" not in client:
            problems.append("NpcBlockOpen351Mixin left the client list - the aim hook is gone")

        # ---- the window itself ---------------------------------------------
        _k, _cp, talk_methods, talk_refs = load_class(zf, TALK)
        say = find_method(talk_methods, "say(")
        if not say:
            problems.append("BlockTalk358 has no say() - a line would never open a window")
        else:
            for needle, why in (
                    ("NpcNet$CfgView", "the aim packet is not sent"),
                    ("NpcNet$DlgView", "the dialogue window is not sent"),
                    ("NpcNet.sendTo", "nothing reaches the player"),
                    # Build 392: `say` fills the line through BlockTalk358#line, which is the one
                    # place that fills the stock placeholders and then the body a door card's blue
                    # socket hands over (%target%). Either call satisfies the rule - what must not
                    # happen is a window with the raw text in it.
                    (("NpcProfile.fill", "BlockTalk358.line"),
                     "the line does not fill the stock placeholders"),
                    ("BlockTrigger351.idFor", "the window carries no block id - the client would "
                                              "hand a block view to the entity screen")):
                options = (needle,) if isinstance(needle, str) else needle
                if not any(option in say for option in options):
                    problems.append("say() is wrong: %s" % why)
            field_of = "NpcProfile.blockMouseLook"
            if field_of not in say:
                problems.append("say() does not set %s - the camera could be moved off the block "
                                "while the window is open" % field_of)
            if say.find("NpcNet$CfgView") > say.find("NpcNet$DlgView"):
                problems.append("say() sends the window before the aim packet - the camera would "
                                "turn after the window is already up")
        pick = find_method(talk_methods, "pick(")
        if not pick:
            problems.append("BlockTalk358 has no pick() - a click in the window would go nowhere")
        else:
            for needle, why in (
                    ("NpcNet$DlgEnd", "the window is never closed when it is continued"),
                    ("BlockTrigger351.resume", "the chain never continues after the line"),
                    ("NpcNet.sendTo", "nothing reaches the player")):
                if needle not in pick:
                    problems.append("pick() is wrong: %s" % why)
        if not find_method(talk_methods, "closed("):
            problems.append("BlockTalk358 has no closed() - Esc would leave the block talking")
        style = find_method(talk_methods, "style(")
        if not style:
            problems.append("BlockTalk358 has no style() - a window cannot be built without one")
        else:
            for needle, why in (
                    ("getDeclaredMethod", "the stock style is not resolved reflectively"),
                    ("setAccessible", "the stock style is never made reachable"),
                    ("GuideDialogue", "the stock style of GuideDialogue is not the one used")):
                if needle not in style:
                    problems.append("style() is wrong: %s" % why)

        # ---- the handshake --------------------------------------------------
        _k2, pick_cp, _m2, pick_refs = load_class(zf, PICK)
        if not any(r[0] and r[0].endswith("npc/BlockTalk358") and r[1] == "pick" for r in pick_refs):
            problems.append("the pick mixin does not call BlockTalk358#pick")
        pick_strings = strings(pick_cp)
        for needle, why in (
                ("pick(Lnet/minecraft/server/level/ServerPlayer;II)V",
                 "the pick mixin does not name GuideDialogue#pick"),
                ("HEAD", "the pick mixin does not inject at HEAD"),
                ("cancellable", "the pick mixin cannot cancel - the stock handler would run too")):
            if needle not in pick_strings:
                problems.append("the pick mixin is wrong: %s" % why)

        _k3, stop_cp, _m3, stop_refs = load_class(zf, STOP)
        if not any(r[0] and r[0].endswith("npc/BlockTalk358") and r[1] == "closed" for r in stop_refs):
            problems.append("the stop mixin does not call BlockTalk358#closed")
        if "stop(Lnet/minecraft/server/level/ServerPlayer;)V" not in strings(stop_cp):
            problems.append("the stop mixin does not name GuideDialogue#stop")

        # ---- the client aim -------------------------------------------------
        _k4, _cp4, client_methods, client_refs = load_class(zf, CLIENT)
        aim = find_method(client_methods, "aim(")
        if not aim:
            problems.append("BlockTalkClient358 has no aim() - the camera would never turn")
        else:
            for needle, why in (
                    ("BlockTalk358.aimAngles", "the client does not use the probed arithmetic"),
                    ("Player.setYRot", "the yaw is never applied"),
                    ("Player.setXRot", "the pitch is never applied"),
                    ("Player.yRotO", "the previous yaw is left alone - the head would sweep there"),
                    ("Player.xRotO", "the previous pitch is left alone - the head would sweep there")):
                if needle not in aim:
                    problems.append("aim() is wrong: %s" % why)
        if not any(r[0] and r[0].endswith("npc/BlockTalk358") and r[1] == "readMarker"
                   for r in client_refs):
            problems.append("the client never asks whether a config view is a talk")

        # ---- the walk and the two callers -----------------------------------
        _k5, _cp5, walker_methods, _r5 = load_class(zf, WALKER)
        walk = find_method(walker_methods, "walk(")
        if not walk:
            problems.append("BlockTrigger351 has no chain walk")
        else:
            window = walk.find("BlockTalk358.say(")
            chat = walk.find("BlockTrigger351.say(")
            if window < 0:
                problems.append("the walk never opens the dialogue window for a line node")
            elif chat < 0 or chat < window:
                problems.append("the walk does not fall back to the chat after the window - a line "
                                "with no window possible would be lost")
            else:
                between = walk[window:chat]
                if "ifeq" not in between or "return" not in between:
                    problems.append("the walk does not stop at the window - it would swallow the "
                                    "line and keep going:\n%s" % between)

        _k6, _cp6, _m6, radius_refs = load_class(zf, RADIUS)
        if not any(r[0] and r[0].endswith("BlockTalk358$Where") for r in radius_refs):
            problems.append("the radius pass does not tell the chain which block it lives on - a "
                            "radius line could not aim")

        _k7, _cp7, _m7, opener_refs = load_class(zf, OPENER)
        for needle, why in (
                ("isTalk", "the opener cannot tell a talk from an edit"),
                ("aim", "the opener never aims the camera"),
                ("openEditor", "the opener no longer opens the editor")):
            if not any(r[1] == needle for r in opener_refs):
                problems.append("the opener is wrong: %s" % why)
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
        if compiled.returncode != 0 or not (tmpdir / "BlockTalkProbe.class").is_file():
            return ["the talk probe did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir)] + [str(jar)] + STUBS), "BlockTalkProbe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the talk probe crashed:\n%s" % output[-800:]]
        problems = []
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("the talk probe says %s = %r, expected %r"
                                % (key, got.get(key), want))
        return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("block_talk_check: no such jar %s" % jar)
        return 2
    problems = structural(jar) + round_trip(jar)
    if problems:
        print("block_talk_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("block_talk_check: OK  %s  (a block's «Реплика» opens the stock dialogue window, the "
          "camera turns to the block, and the chain waits for the click or Esc)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
