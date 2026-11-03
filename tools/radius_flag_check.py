#!/usr/bin/env python3
"""Gate: the radius visibility flag survives a save.

Build 355 made the "Видимость радиуса" row save on the click, and the value still jumped back to
"да". The reason was not the click but the writer: `NpcDialogueGraph#save` writes `TimerRepeat` only
for a timer node (`type == 8`), while `load` reads that key for **every** node and fills in `true`
when it is absent. A "В радиусе" or "Цель" node therefore lost the flag on every save - the server
drew the ring again and the next snapshot put "включено" back into the row.

This gate asks the shipped jar four things:

  * the mixin exists and is a **common** mixin - the server saves the same graph, so the flag has to
    be written on both sides;
  * it injects into `save()Lnet/minecraft/nbt/CompoundTag;` at RETURN, filters on the two node types
    that carry a radius, and writes the key the reader already understands;
  * the hole is where we say it is: `save` writes `TimerRepeat` once and only behind `type == 8`,
    and `load` reads it with no type guard at all - which is why no reader is patched;
  * the round trip really works: `tools/harness/RadiusFlagProbe.java` is compiled against this jar
    and the NBT mirror and run headlessly - the flag is absent before the rule and `load` turns that
    absence into `true` (the reported bug, reproduced), the rule then writes it, `false` survives
    `load`, and `true` comes back as `true` rather than as a default.

    python3 tools/radius_flag_check.py settledlands-0.8.0-build356-radius-flag.jar

Exit 0 = a radius ring switched off stays off across a save, a reopen and a restart.
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
PROBE = ROOT / "tools" / "harness" / "RadiusFlagProbe.java"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]

MIXIN = "dev/settledlands/mixin/RadiusFlagSave356Mixin.class"
MIXIN_NAME = "RadiusFlagSave356Mixin"
GRAPH = "dev/settledlands/npc/NpcDialogueGraph.class"
PASS354 = "dev/settledlands/npc/RadiusPass354.class"

SAVE = "save()Lnet/minecraft/nbt/CompoundTag;"
CHECK_TYPE = 5
RADIUS_TYPE = 15


def load_class(zf, name):
    """(constant pool, class dict, {method: disassembly}) for one class of the jar."""
    data = zf.read(name)
    k = disasm.parse_class(data)
    cp = k["cp"]
    methods = {}
    for _acc, ni, di, attrs in k["methods"]:
        code = disasm.code_of(attrs, cp)
        if code is None:
            continue
        methods[cp[ni][1] + cp[di][1]] = disasm.disasm(code["code"], cp)[0]
    return cp, k, methods


def constant_value(k, cp, name):
    """The int a `static final int` really holds: ConstantValue carries a pool index, not the value."""
    for _acc, ni, di, attrs in k["fields"]:
        if cp[ni][1] != name:
            continue
        for an, ad in attrs:
            if cp[an][1] == "ConstantValue":
                index = int.from_bytes(ad[:2], "big")
                return int.from_bytes(cp[index][1][:4], "big", signed=True)
    return None


class Reader:
    """Just enough of the class-file reader for one annotation attribute."""

    def __init__(self, data):
        self.data = data
        self.at = 0

    def u1(self):
        value = self.data[self.at]
        self.at += 1
        return value

    def u2(self):
        value = int.from_bytes(self.data[self.at:self.at + 2], "big")
        self.at += 2
        return value


def read_value(r, cp):
    tag = chr(r.u1())
    if tag == "s":
        return cp[r.u2()][1]
    if tag in "BCDFIJSZ":
        return cp[r.u2()][1]
    if tag == "e":
        r.u2()
        return cp[r.u2()][1]
    if tag == "c":
        return cp[r.u2()][1]
    if tag == "@":
        return read_annotation(r, cp)
    if tag == "[":
        return [read_value(r, cp) for _ in range(r.u2())]
    raise ValueError("element value tag %r" % tag)


def read_annotation(r, cp):
    kind = cp[r.u2()][1]
    pairs = {}
    for _ in range(r.u2()):
        name = cp[r.u2()][1]
        pairs[name] = read_value(r, cp)
    return kind, pairs


def annotations(attrs, cp):
    """Every annotation on a member, as [(type, {element: value})]."""
    found = []
    for an, ad in attrs:
        if cp[an][1] != "RuntimeVisibleAnnotations":
            continue
        r = Reader(ad)
        for _ in range(r.u2()):
            found.append(read_annotation(r, cp))
    return found


def injections(cp, k):
    """[(method name, {method: target, at: point})] for every @Inject in a mixin."""
    found = []
    for _acc, ni, di, attrs in k["methods"]:
        name = cp[ni][1]
        for kind, pairs in annotations(attrs, cp):
            if not kind.endswith("injection/Inject;"):
                continue
            points = pairs.get("at", [])
            if isinstance(points, tuple):
                points = [points]
            values = [pairs_["value"] for _kind, pairs_ in points if "value" in pairs_]
            found.append((name, pairs.get("method"), values))
    return found


def lines_with(text, needle):
    return [i for i, line in enumerate(text.splitlines()) if needle in line]


def around(text, index, back=16):
    return "\n".join(text.splitlines()[max(0, index - back):index + 1])


def structural(jar):
    problems = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for needed in (MIXIN, GRAPH, PASS354):
            if needed not in names:
                problems.append("the jar has no %s" % needed)
        if problems:
            return problems

        config = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common = config.get("mixins", [])
        client = config.get("client", [])
        if MIXIN_NAME not in common:
            problems.append("%s is not registered as a common mixin - the server would not write the "
                            "flag" % MIXIN_NAME)
        if MIXIN_NAME in client:
            problems.append("%s is registered as a client mixin - the server saves the graph too"
                            % MIXIN_NAME)
        if common.count(MIXIN_NAME) != 1:
            problems.append("%s is listed %d times in the common mixins"
                            % (MIXIN_NAME, common.count(MIXIN_NAME)))

        mixin_cp, mixin_k, mixin_methods = load_class(zf, MIXIN)
        text = "\n".join(mixin_methods.values())
        found = injections(mixin_cp, mixin_k)
        if not found:
            problems.append("the mixin has no @Inject at all")
        else:
            if len(found) != 1:
                problems.append("the mixin has %d injections, expected one" % len(found))
            method_name, target, points = found[0]
            if isinstance(target, list) and len(target) == 1:
                target = target[0]
            if target != SAVE:
                problems.append("the mixin injects into %r, not %s - the flag would never be written"
                                % (target, SAVE))
            if points != ["RETURN"]:
                problems.append("the mixin injects at %r, not RETURN - save() has not built the tag "
                                "yet at any other point" % (points,))
            if not mixin_methods.get(method_name + "(Lorg/spongepowered/asm/mixin/injection/"
                                     "callback/CallbackInfoReturnable;)V"):
                problems.append("the injected method %r does not have the shape of a save() hook"
                                % method_name)
        for needle, why in (
                ("getReturnValue", "it does not read the tag save() built"),
                ("'Nodes'", "it does not walk the node list"),
                ("'Id'", "it does not match a tag to its node"),
                ("'TimerRepeat'", "it does not write the key the reader understands"),
                ("putBoolean", "it does not write the flag"),
                ("iconst_5", "it does not cover the \"В радиусе\" node (type %d)" % CHECK_TYPE),
                ("bipush  15", "it does not cover the \"Цель\" node (type %d)" % RADIUS_TYPE),
                ("Node.typeI", "it does not look at the node type"),
                ("Node.timerRepeatZ", "it does not read the node's flag")):
            if needle not in text:
                problems.append("the mixin is wrong: %s" % why)

        cp, graph_k, graph_methods = load_class(zf, GRAPH)
        if constant_value(graph_k, cp, "CHECK") != CHECK_TYPE:
            problems.append("NpcDialogueGraph.CHECK is %s, the mixin writes type %d"
                            % (constant_value(graph_k, cp, "CHECK"), CHECK_TYPE))
        if constant_value(graph_k, cp, "RADIUS") != RADIUS_TYPE:
            problems.append("NpcDialogueGraph.RADIUS is %s, the mixin writes type %d"
                            % (constant_value(graph_k, cp, "RADIUS"), RADIUS_TYPE))

        save = graph_methods.get(SAVE, "")
        if not save:
            problems.append("NpcDialogueGraph has no %s to inject into" % SAVE)
        else:
            writes = [i for i in lines_with(save, "putBoolean")
                      if "'TimerRepeat'" in around(save, i, back=3)]
            if len(writes) != 1:
                problems.append("save writes TimerRepeat %d times, expected exactly the timer node's "
                                "one - the guard moved" % len(writes))
            elif "bipush  8" not in around(save, writes[0], back=24):
                problems.append("save no longer guards TimerRepeat with type == 8 - this gate is "
                                "stale, the mixin may now be writing a duplicate")
            elif not lines_with(save, "'TimerRepeat'"):
                problems.append("save does not mention TimerRepeat at all")

        load = graph_methods.get("load(Lnet/minecraft/nbt/CompoundTag;)Ldev/settledlands/npc/NpcDialogueGraph;", "")
        if not load:
            problems.append("NpcDialogueGraph has no load()")
        else:
            flag_lines = lines_with(load, "'TimerRepeat'")
            if len(flag_lines) < 2:
                problems.append("load does not read TimerRepeat for every node - the reader would have "
                                "to be patched too, and this build does not patch it")
            else:
                window = "\n".join(load.splitlines()[flag_lines[0]:flag_lines[-1] + 4])
                if "contains" not in window or "getBoolean" not in window:
                    problems.append("load no longer does contains + getBoolean on TimerRepeat")

        _p_cp, _p_k, pass_methods = load_class(zf, PASS354)
        row = pass_methods.get("isRadiusNode(Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z", "")
        for token, why in (("iconst_5", "the row no longer covers \"В радиусе\""),
                           ("bipush  15", "the row no longer covers \"Цель\"")):
            if token not in row:
                problems.append("RadiusPass354.isRadiusNode is out of step with the mixin: %s" % why)
    return problems


def round_trip(jar):
    """Compile and run the probe against this jar; return a list of problems."""
    problems = []
    if not PROBE.is_file():
        return ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        classpath = ":".join([str(jar)] + STUBS)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp", classpath,
             "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "RadiusFlagProbe.class").is_file():
            return ["the round trip test did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir)] + [str(jar)] + STUBS), "RadiusFlagProbe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the round trip crashed:\n%s" % output[-800:]]
        got = {}
        for line in output.splitlines():
            parts = line.split()
            if parts and parts[0] in ("BASE", "DEFAULT", "MIXIN", "LOAD", "ON"):
                got[parts[0]] = dict(p.split("=", 1) for p in parts[1:] if "=" in p)
        want = {
            "BASE": {"check": "absent", "radius": "absent", "timer": "false"},
            "DEFAULT": {"check": "true", "radius": "true"},
            "MIXIN": {"check": "false", "radius": "false", "timer": "false"},
            "LOAD": {"check": "false", "radius": "false", "timer": "false"},
            "ON": {"check": "true"},
        }
        for stage, expected in want.items():
            if stage not in got:
                problems.append("the round trip printed no %s line:\n%s" % (stage, output[-400:]))
                continue
            for key, value in expected.items():
                if got[stage].get(key) != value:
                    problems.append("%s: %s is %r, expected %r"
                                    % (stage, key, got[stage].get(key), value))
    return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("radius_flag_check: no such jar %s" % jar)
        return 2
    problems = structural(jar) + round_trip(jar)
    if problems:
        print("radius_flag_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("radius_flag_check: OK  %s  (a radius ring switched off stays off: the flag is written on "
          "save for both radius node types, and load already reads it)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
