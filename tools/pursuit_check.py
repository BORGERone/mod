#!/usr/bin/env python3
"""Gate: a damage-driven pursuit must keep its body and must not be damped to a crawl.

Two things went wrong in Build 310 and are checked here on the shipped class:

  * the "Урон" node names its attacker for ten seconds only, so a pursuit that read the node
    every tick lost its target mid-chase and the NPC stood still. The fix latches the body;
    the gate asks for the latch (field {@code latched}, helper {@code latchedBody});
  * GuideNpc#walkingNavigationSpeed carries the auto-AI peace-time damping (x0.55), which made
    a pursuit slower than plain walking. The fix divides that factor out, so the speed path
    must reference {@code navigationSpeedFactor}.

Build 312 added the pacing and the path policy, checked numerically here because both were
"it works but it looks wrong" bugs: a sprint (PURSUIT_WALK / CATCH_UP_EXTRA too high), and a walk
that stopped, turned and set off again because the path was rebuilt every couple of blocks
(REPATH_SUCCESS_TICKS / RETRY_MAX_SHIFT / RESUME_MARGIN).

Build 314 added the animation half: the locomotion input is smoothed (SPEED_SECONDS) instead of
measured per frame, and the smoothed numbers reach every reader through NpcLocoInputsMixin314,
which has to be in the jar and in the client mixin list or Touch'n Motion goes back to stepped
blends.

    python3 tools/pursuit_check.py settledlands-0.8.0-build311-pursuit-fix.jar

Exit 0 = both fixes are in the shipped class.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

TARGET = "dev/settledlands/npc/NpcPursuit269.class"
DRIVER = "dev/settledlands/model/NpcLocoDriver283.class"
LOCO_MIXIN = "dev/settledlands/mixin/NpcLocoInputsMixin314.class"
MIXIN_CONFIG = "settledlands.mixins.json"
STATE = "dev/settledlands/npc/NpcPursuit269$State.class"
INVOKES = (0xb6, 0xb7, 0xb8, 0xb9)


def method_refs(cp, info):
    out = set()
    for _acc, _ni, _di, attrs in info["methods"]:
        for an, ab in attrs:
            if cp[an][1] != "Code":
                continue
            p = 4
            length = struct.unpack(">I", ab[p:p + 4])[0]
            p += 4
            body = ab[p:p + length]
            i = 0
            while i < len(body):
                op = body[i]
                entry = disasm.OPS.get(op)
                if entry is None:
                    return out
                name, size = entry
                if op in INVOKES:
                    idx = struct.unpack(">H", body[i + 1:i + 3])[0]
                    out.add(disasm.ref(cp, idx).rsplit(".", 1)[-1].split("(", 1)[0])
                i += 1 + size
    return out


def field_names(cp, info):
    return {cp[ni][1] for _acc, ni, _di, _attrs in info["fields"]}


def field_values(cp, info):
    """ConstantValue fields as numbers - the pacing policy is readable in the class file."""
    out = {}
    for _acc, ni, _di, attrs in info["fields"]:
        for an, ab in attrs:
            if cp[an][1] != "ConstantValue":
                continue
            idx = struct.unpack(">H", ab[:2])[0]
            tag, raw = cp[idx]
            if tag == 3:
                out[cp[ni][1]] = float(struct.unpack(">i", raw)[0])
            elif tag == 4:
                out[cp[ni][1]] = struct.unpack(">f", raw)[0]
            elif tag == 5:
                out[cp[ni][1]] = float(struct.unpack(">q", raw)[0])
            elif tag == 6:
                out[cp[ni][1]] = struct.unpack(">d", raw)[0]
    return out


# name: (ceiling, message)
LIMITS = {
    "PURSUIT_WALK": (1.3, "the pursuit walk is faster than a purposeful walk"),
    "CATCH_UP_EXTRA": (1.4, "the catch-up floor is back at the sprint of Build 311"),
    "RETRY_MAX_SHIFT": (1.0, "the retry backoff can freeze the body for longer than two seconds"),
    "REPATH_SUCCESS_TICKS": (14.0, "a walking path is rebuilt too eagerly - that is the stutter"),
    "RESUME_MARGIN": (2.0, "a target on the standoff edge switches the body on and off"),
    "TARGET_MOVING_SQ": (0.05, "a walking target no longer counts as moving - the follower will "
                               "brake at the standoff while you walk away"),
    "FOLLOW_STEP_SQ": (1.0, "a finished path waits for the target to slide too far - that is the "
                            "stop between two paths"),
}
MISSING = {
    "TARGET_MOVING_SQ": "the target-motion threshold is gone",
    "FOLLOW_STEP_SQ": "the follow-up step threshold is gone",
    "PURSUIT_WALK": "the pursuit walk factor is gone",
    "CATCH_UP_EXTRA": "the catch-up floor is gone",
    "RETRY_MAX_SHIFT": "the retry backoff cap is gone",
    "REPATH_SUCCESS_TICKS": "the repath cooldown is gone",
    "RESUME_MARGIN": "the resume margin is gone",
}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        print("pursuit_check: no such jar %s" % jar)
        return 2
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if TARGET not in names:
            print("pursuit_check: SKIP - this jar has no pursuit class")
            return 0
        main_info = disasm.parse_class(z.read(TARGET))
        state_info = disasm.parse_class(z.read(STATE)) if STATE in names else None
        driver_info = disasm.parse_class(z.read(DRIVER)) if DRIVER in names else None
        config = json.loads(z.read(MIXIN_CONFIG)) if MIXIN_CONFIG in names else None

    refs = method_refs(main_info["cp"], main_info)
    problems = []
    if "latchedBody" not in refs:
        problems.append("the pursuit never falls back to a latched body - the damage window "
                        "will still cut the chase after ten seconds")
    if "movedBy" not in refs:
        problems.append("nothing asks whether the target moved past a finished path")
    if "navigationSpeedFactor" not in refs:
        problems.append("the speed path does not cancel the auto-AI damping - a pursuit walks "
                        "slower than ordinary walking")
    if driver_info is None:
        problems.append("the locomotion driver is gone (%s)" % DRIVER)
    elif "SPEED_SECONDS" not in field_values(driver_info["cp"], driver_info):
        problems.append("the locomotion speed is no longer smoothed (SPEED_SECONDS)")

    if LOCO_MIXIN not in names:
        problems.append("the smoothed locomotion never reaches Touch'n Motion (%s)" % LOCO_MIXIN)
    elif config is None or "NpcLocoInputsMixin314" not in config.get("client", []):
        problems.append("NpcLocoInputsMixin314 is not in the client mixin list")

    values = field_values(main_info["cp"], main_info)
    for name, message in MISSING.items():
        if name not in values:
            problems.append("%s (%s)" % (message, name))
    for name, (ceiling, message) in LIMITS.items():
        value = values.get(name)
        if value is not None and value > ceiling:
            problems.append("%s: %s = %s, limit %s" % (message, name, value, ceiling))
    if state_info is None:
        problems.append("%s is gone" % STATE)
    elif "latched" not in field_names(state_info["cp"], state_info):
        problems.append("the pursuit state has no latched field")

    if problems:
        print("pursuit_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        return 1
    print("pursuit_check: OK  %s  (body latched, speed undamped, pacing and repath sane)" % jar.name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
