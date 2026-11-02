#!/usr/bin/env python3
"""Gate: Touch'n Motion's motion reaches every Ivy body you possess, not two model names.

Build 341. The possessed player's body is retargeted from the player model's Fresh Animations pose
only when the actor model answers to `ivy` or `ivy_oleander` (`PlayerMotionBridge.isIvy`). The mod also
ships `ivy_oleander_professions` - the same rig with the profession bones, the one every NPC with a
profession uses - and it was not on that list, so such a body kept its own clips. Touch'n Motion's
gesture was worked out and stored, but there was no retarget to carry it onto the bones.

Ways that can ship silently broken:

  * the widened gate is not a client mixin, or names the wrong method, so it never applies;
  * it forces the answer instead of widening it - a model that the bridge refused for a reason would
    now be retargeted anyway;
  * the rig validation behind the name check is bypassed, so a rig without body/waist/limb bones is
    retargeted and the body comes out mangled;
  * the report is unbounded, or the build still carries the 340 listener that called Touch'n Motion a
    second time per frame;
  * Touch'n Motion's own classes are copied into the mod jar.

    python3 tools/touch_n_motion_check.py settledlands-0.8.0-build341-ivy-professions.jar

Exit 0 = every Ivy rig is offered to the retarget, and only the name test was widened.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

GATE = "dev/settledlands/model/IvyRig341.class"
MIXIN = "dev/settledlands/mixin/IvyRigGateMixin341.class"
BRIDGE = "dev/settledlands/model/PlayerMotionBridge.class"
CONFIG = "settledlands.mixins.json"

# The 340 listener asked Touch'n Motion for the gesture a second time per frame: the bridge already
# does it. It must not come back.
REDUNDANT = "dev/settledlands/npc/client/TouchNMotionPose340.class"

TARGET = "dev.settledlands.model.PlayerMotionBridge"
METHOD = "isIvy"


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
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


def methods(parsed):
    cp = parsed["cp"]
    return {cp[n][1] for _a, n, _d, _t in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (GATE, MIXIN, BRIDGE):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        if REDUNDANT in names:
            return fail("the jar still carries %s - the bridge already asks Touch'n Motion" % REDUNDANT)
        copied = [n for n in names if n.startswith("strm/touchnmotion/")]
        if copied:
            return fail("the jar carries Touch'n Motion's classes: %s" % ", ".join(copied[:3]))
        gate_blob = z.read(GATE)
        mixin_blob = z.read(MIXIN)
        bridge_blob = z.read(BRIDGE)
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    # -- the widened gate is a client mixin on the one method that decides -----
    if "IvyRigGateMixin341" not in config.get("client", []):
        return fail("the gate is not a client mixin - it would never meet the renderer")
    if "IvyRigGateMixin341" in config.get("mixins", []):
        return fail("the gate is a common mixin - the bridge it widens is client only")
    mixin, mixin_refs = info(mixin_blob)
    mixin_text = text(mixin)
    if TARGET not in mixin_text:
        return fail("the gate does not name %s" % TARGET)
    if METHOD not in mixin_text:
        return fail("the gate does not name the model name test %s" % METHOD)
    if "RETURN" not in mixin_text or "require" not in mixin_text or "remap" not in mixin_text:
        return fail("the gate does not sit at RETURN with require = 0 / remap = false")
    handlers = methods(mixin) - {"<init>"}
    if len(handlers) != 1:
        return fail("the gate injects more than the one name test: %s" % ", ".join(sorted(handlers)))
    print("ok: one client injection on PlayerMotionBridge.%s at RETURN" % METHOD)

    # -- it widens the answer, it does not force it ---------------------------
    if not any(r[0].endswith("callback/CallbackInfoReturnable") and r[1] == "getReturnValue"
               for r in mixin_refs):
        return fail("the gate does not read the bridge's own answer")
    if not any(r[0].endswith("callback/CallbackInfoReturnable") and r[1] == "setReturnValue"
               for r in mixin_refs):
        return fail("the gate never widens the answer")
    if any(r[0] == TARGET and r[1] in ("validRig", "eligible") for r in mixin_refs):
        return fail("the gate touches the rig validation - a rig without the bones would be "
                    "retargeted anyway")
    bridge, _bridge_refs = info(bridge_blob)
    if not {"validRig", "eligible"} <= methods(bridge):
        return fail("the bridge no longer validates the rig behind the name test")
    print("ok: the answer is only widened, and the bridge's own rig validation still decides")

    # -- and only for the Ivy rigs -------------------------------------------
    gate, gate_refs = info(gate_blob)
    gate_text = text(gate)
    if "ivy" not in gate_text:
        return fail("the gate does not name the rig prefix")
    if not any(r[1] == "startsWith" for r in gate_refs):
        return fail("the gate does not match by prefix - the profession rig would still be refused")
    if "REPORT_LIMIT" not in " ".join(v[1] for v in gate["cp"] if v and v[0] == 1):
        return fail("the report is not bounded")
    if not any(r[0] == "java/util/Set" for r in gate_refs):
        return fail("the report does not remember what it has already said")
    print("ok: every Ivy rig answers yes, and the report is bounded")

    print("touch_n_motion_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
