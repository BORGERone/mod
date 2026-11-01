#!/usr/bin/env python3
"""Gate: the radius zone toggle must be cheap, wired, and visible.

Build 320 draws the circle a "В радиусе" node measures in, as a ring of particles. Two ways that
could go wrong and stay silent in game: the ring is drawn every tick for every guide (a permanent
cost nobody asked for), or the toggle is drawn but never reaches the server (a box that clicks and
does nothing).

So the shipped classes are read for the numbers that keep it cheap - period, point count, view
range, radius cap - and for the calls that prove it works end to end: the saved flag field, the
particle send, and the two mixin lists (the ring is server side, the tick box is client side).

    python3 tools/radius_zone_check.py settledlands-0.8.0-build320-radius-zone.jar

Exit 0 = the toggle is present, bounded and connected.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

ZONE = "dev/settledlands/npc/NpcRadiusZone320.class"
UI = "dev/settledlands/npc/client/NpcRadiusZoneUi320.class"
MIXIN_SERVER = "dev/settledlands/mixin/NpcRadiusZoneMixin320.class"
MIXIN_CLIENT = "dev/settledlands/mixin/NpcRadiusZoneUiMixin320.class"
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
        for needed in (ZONE, UI, MIXIN_SERVER, MIXIN_CLIENT):
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

        # The flag is read and written through the two accessors, never as a bare field, so it has
        # exactly one owner. Drawing reads it, the click handler writes it.
        ui_refs = refs(disasm.parse_class(z.read(UI)))
        if not any("NpcRadiusZone320.visible" in r for r in ui_refs):
            problems.append("the tick box does not read the saved flag")
        client_mixin = refs(disasm.parse_class(z.read(MIXIN_CLIENT)))
        if not any("NpcRadiusZone320.setVisible" in r for r in client_mixin):
            problems.append("the tick box does not write the saved flag")
        if not any("NpcRadiusZoneUi320.draw" in r for r in client_mixin):
            problems.append("the client mixin does not draw the tick box")

        server_mixin = refs(disasm.parse_class(z.read(MIXIN_SERVER)))
        if not any("NpcRadiusZone320.tick" in r for r in server_mixin):
            problems.append("the server mixin does not call the zone tick")
    return problems


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("radius_zone_check: no such jar %s" % jar)
        return 2
    problems = run(jar)
    if problems:
        print("radius_zone_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("radius_zone_check: OK  %s  (toggle wired, ring bounded, both mixins listed)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
