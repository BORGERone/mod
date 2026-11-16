#!/usr/bin/env python3
"""Gate: the radius zone toggle must be cheap, wired, and visible.

Build 320 draws the circle a "В радиусе" node measures in, as a ring of particles. Two ways that
could go wrong and stay silent in game: the ring is drawn every tick for every guide (a permanent
cost nobody asked for), or the toggle is drawn but never reaches the server (a box that clicks and
does nothing).

So the shipped classes are read for the numbers that keep it cheap - period, point count, view
range, radius cap - and for the calls that prove it works end to end: the saved flag field, the
particle send, and the two mixin lists (the ring is server side, the row is client side).
Build 361 moved the toggle of the flag from a check box on the canvas to the inspector row, so the
gate reads the row of build 355 instead of the retired box - and refuses to let the box come back.

Build 367 adds one more rule to this gate: the zone is <b>off</b> when the node is born. The stock
field behind it defaults to true, so every new «В радиусе» and «Цель» came with a ring already drawn;
the rule lives in RadiusPass354#hideByDefault and RadiusDefault367Mixin applies it to every node
NpcDialogueGraph#add creates. The probe below proves it, and that a node saved switched on is not
erased by the default.

    python3 tools/radius_zone_check.py settledlands-0.8.0-build320-radius-zone.jar

Exit 0 = the toggle is present, bounded, connected, and off by default.
"""
import json
import pathlib
import struct
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

ZONE = "dev/settledlands/npc/NpcRadiusZone320.class"
UI = "dev/settledlands/npc/client/NpcRadiusZoneUi320.class"
MIXIN_SERVER = "dev/settledlands/mixin/NpcRadiusZoneMixin320.class"
MIXIN_CLIENT = "dev/settledlands/mixin/NpcRadiusZoneUiMixin320.class"
ROW = "dev/settledlands/mixin/NpcRadiusRow355Mixin.class"
# build 367: a radius node starts with its zone off
DEFAULT = "dev/settledlands/mixin/RadiusDefault367Mixin.class"
PROBE = ROOT / "tools" / "harness" / "RadiusDefault367Probe.java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
STUBS = [str(ROOT / "build303-src" / part) for part in
         ("api", "api5", "api4", "api3", "stubs-out", "api2")]
EXPECT = {"ZONE": "check=false radius=false timer=true line=true", "SAVED": "check=true",
          "WHEEL": "up=-1 down=1 none=0"}
NAMES = {"": "NpcRadiusZoneMixin320", "client": "NpcRadiusZoneUiMixin320"}
LANG_KEY = "gui.settledlands.graph.radius_zone"

# name: (relation, bound, message)
BOUNDS = {
    "PERIOD_TICKS": (">=", 2, "the ring is drawn every tick or faster"),
    "POINTS": (">=", 12, "a circle of %s points does not read as a circle"),
    "VIEW_RANGE": (">=", 16.0, "the ring is sent even when nobody can see it"),
    "MAX_RADIUS": ("<=", 128, "the ring radius is not capped"),
    "RING_LIFT": ("<=", 1.0, "the ring floats above the NPC's head"),
}


def constants(info):
    cp = info["cp"]
    out = {}
    for _acc, ni, di, attrs in info["fields"]:
        for an, ab in attrs:
            if cp[an][1] != "ConstantValue":
                continue
            idx = struct.unpack(">H", ab[:2])[0]
            tag, raw = cp[idx]
            if tag in (3, 4, 5, 6):
                out[cp[ni][1]] = struct.unpack(">i", raw)[0] if tag == 3 else (
                    struct.unpack(">f", raw)[0] if tag == 4 else (
                        struct.unpack(">q", raw)[0] if tag == 5 else struct.unpack(">d", raw)[0]))
    return out


def refs(info):
    cp = info["cp"]
    out = set()
    for i in range(1, len(cp)):
        entry = cp[i]
        if entry and entry[0] in (9, 10, 11):
            out.add(disasm.ref(cp, i))
    return out


def run(jar):
    problems = []
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for needed in (ZONE, UI, MIXIN_SERVER, MIXIN_CLIENT, ROW):
            if needed not in names:
                problems.append("missing %s" % needed)
        if problems:
            return problems

        config = json.loads(z.read("settledlands.mixins.json").decode("utf-8"))
        if "NpcRadiusZoneMixin320" not in config.get("mixins", []):
            problems.append("NpcRadiusZoneMixin320 is not in the common mixin list - server only")
        if "NpcRadiusZoneUiMixin320" not in config.get("client", []):
            problems.append("NpcRadiusZoneUiMixin320 is not in the client mixin list")

        for lang in ("assets/settledlands/lang/ru_ru.json",
                     "assets/settledlands/lang/en_us.json"):
            if lang not in names:
                problems.append("missing %s" % lang)
                continue
            strings = json.loads(z.read(lang).decode("utf-8"))
            if LANG_KEY not in strings:
                problems.append("%s has no %s" % (lang, LANG_KEY))

        zone = disasm.parse_class(z.read(ZONE))
        values = constants(zone)
        for name, (relation, bound, message) in BOUNDS.items():
            value = values.get(name)
            if value is None:
                problems.append("%s is gone (%s)" % (name, message))
                continue
            bad = value < bound if relation == ">=" else value > bound
            if bad:
                problems.append("%s = %s, must be %s %s (%s)"
                                % (name, value, relation, bound, message))

        zone_refs = refs(zone)
        if not any("timerRepeat" in r for r in zone_refs):
            problems.append("the saved node flag is not read - the toggle would not survive")
        if not any("sendParticles" in r for r in zone_refs):
            problems.append("nothing is sent to the client - the ring would never appear")
        if not any("ParticleTypes.ENCHANT" in r for r in zone_refs):
            problems.append("the ring particle is gone")
        if any("setDeltaMovement" in r for r in zone_refs):
            problems.append("the ring moves the NPC - this is a display aid, not a mechanic")

        # Build 361: the one toggle of the flag is the inspector row of build 354/355 - the same row
        # the «В радиусе» node has - and the check box build 320 painted on the canvas is retired.
        # The flag is read and written through the two accessors, never as a bare field, so it has
        # exactly one owner: the row reads it, the row writes it, and the row saves on the click.
        if ROW not in names:
            problems.append("missing %s" % ROW)
        else:
            row_refs = refs(disasm.parse_class(z.read(ROW)))
            if not any("RadiusPass354.visible" in r for r in row_refs):
                problems.append("the inspector row does not read the saved flag")
            if not any("RadiusPass354.setVisible" in r for r in row_refs):
                problems.append("the inspector row does not write the saved flag")
            if not any("NpcRadiusRow355Mixin.save" in r for r in row_refs):
                problems.append("the inspector row does not save the graph on the click")
        # ---- build 367: the zone starts off ------------------------------------------------
        if DEFAULT not in names:
            problems.append("missing %s - a new radius node would draw a ring again" % DEFAULT)
        else:
            if "RadiusDefault367Mixin" not in config.get("mixins", []):
                problems.append("RadiusDefault367Mixin is not in the common mixin list - the server "
                                "builds the same nodes")
            default_refs = refs(disasm.parse_class(z.read(DEFAULT)))
            if not any("RadiusPass354.hideByDefault" in r for r in default_refs):
                problems.append("the default-off hook does not call RadiusPass354#hideByDefault - the "
                                "rule would live in two places")
            if not any("RadiusPass354.hideByDefault" in r for r in default_refs):
                problems.append("the default-off hook calls nothing on the pass")
            for marker in (b"add(III)Ldev/settledlands/npc/NpcDialogueGraph$Node;",):
                if marker not in z.read(DEFAULT):
                    problems.append("the default-off hook names no descriptor for add - an overload "
                                    "would be picked at random")

        ui_refs = refs(disasm.parse_class(z.read(UI)))
        if any("GuiGraphics.fill" in r for r in ui_refs):
            problems.append("the retired canvas check box is painted again")
        if any("NpcRadiusZone320.visible" in r for r in ui_refs):
            problems.append("the retired canvas check box reads the flag again")
        client_mixin = refs(disasm.parse_class(z.read(MIXIN_CLIENT)))
        if not any("NpcRadiusZoneUi320.draw" in r for r in client_mixin):
            problems.append("the client mixin no longer calls the retired box")

        server_mixin = refs(disasm.parse_class(z.read(MIXIN_SERVER)))
        if not any("NpcRadiusZone320.tick" in r for r in server_mixin):
            problems.append("the server mixin does not call the zone tick")
    return problems


def probe(jar):
    """Run the two rules of build 367: the default, and the wheel direction."""
    if not PROBE.is_file():
        return ["the probe %s is missing" % PROBE]
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        compiled = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn", "-cp",
             ":".join([str(jar)] + STUBS), "-d", str(tmpdir), str(PROBE)],
            capture_output=True, text=True)
        if compiled.returncode != 0 or not (tmpdir / "RadiusDefault367Probe.class").is_file():
            return ["the default probe did not compile: %s"
                    % (compiled.stdout + compiled.stderr).strip()[-400:]]
        result = subprocess.run(
            [str(JAVA), "-cp", ":".join([str(tmpdir), str(jar)] + STUBS),
             "RadiusDefault367Probe"],
            capture_output=True, text=True)
        output = (result.stdout + result.stderr).strip()
        if result.returncode != 0:
            return ["the default probe crashed:\n%s" % output[-800:]]
        problems = []
        got = {}
        for line in output.splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2:
                got[parts[0]] = parts[1].strip()
        for key, want in EXPECT.items():
            if got.get(key) != want:
                problems.append("the probe says %s = %r, expected %r"
                                % (key, got.get(key), want))
        return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("radius_zone_check: no such jar %s" % jar)
        return 2
    problems = run(jar) + probe(jar)
    if problems:
        print("radius_zone_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("radius_zone_check: OK  %s  (toggle wired, ring bounded, both mixins listed)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
