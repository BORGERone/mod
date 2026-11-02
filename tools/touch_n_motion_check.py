#!/usr/bin/env python3
"""Gate: Touch'n Motion's own motion reaches the body you possess.

Build 340. Touch'n Motion works its gesture out at the head of
`LivingEntityRenderer.render` and stores it; the possession mod cancels that render in
`RenderPlayerEvent.Pre`, which is fired before that head injection ever runs - so the gesture was
never computed for a possessed player, and the retargeted NPC body had none of it.

Ways that can ship silently broken:

  * the listener is not registered at all, or not on the client, so nothing is ever called;
  * the listener runs after the possession renderer that cancels the pass - the call would come too
    late and resolve to nothing;
  * the pose entry point is resolved per frame instead of once, or a lambda is allocated every frame;
  * the entry point is called for players the mod is not drawing, which would run Touch'n Motion's
    motion for every player on the server, every frame;
  * an absent or changed Touch'n Motion throws into the render pass instead of being ignored;
  * Touch'n Motion's own classes are copied into the mod jar.

    python3 tools/touch_n_motion_check.py settledlands-0.8.0-build340-tnm-possession.jar

Exit 0 = the gesture is asked for, once per rendered frame, only for a possessed player.
"""
import pathlib
import re
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

POSE = "dev/settledlands/npc/client/TouchNMotionPose340.class"
BODY = "dev/settledlands/npc/client/PlayerBodyRenderer.class"

TN_MODEL_SPACE = "strm.touchnmotion.ModelSpace"
TN_BEFORE = "before"


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


def code_text(parsed, name):
    cp = parsed["cp"]
    for _a, n, _d, attrs in parsed["methods"]:
        if cp[n][1] != name:
            continue
        co = disasm.code_of(attrs, cp)
        if co is None:
            return None, None
        txt, _labels = disasm.disasm(co["code"], cp)
        return txt, co
    return None, None


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if POSE not in names:
            return fail("%s is not in the jar" % POSE)
        if BODY not in names:
            return fail("%s is gone - nothing cancels the player render for the listener to run "
                        "ahead of" % BODY)
        copied = [n for n in names if n.startswith("strm/touchnmotion/")]
        if copied:
            return fail("the jar carries Touch'n Motion's classes: %s" % ", ".join(copied[:3]))
        data = z.read(POSE)

    pose, refs = info(data)
    body = text(pose)

    # -- registered, on the client, ahead of the renderer that cancels ------
    if "Lnet/neoforged/fml/common/EventBusSubscriber;" not in body:
        return fail("the listener is not registered on the event bus")
    if "settledlands" not in body or "Lnet/neoforged/api/distmarker/Dist;" not in body:
        return fail("the listener does not declare the mod id and the client side")
    if "Lnet/neoforged/bus/api/SubscribeEvent;" not in body:
        return fail("the handler is not a subscriber")
    if "HIGHEST" not in body:
        return fail("the handler does not run at HIGHEST - the possession renderer would cancel the "
                    "pass before Touch'n Motion is asked")
    if not any(r[0] == "net/neoforged/neoforge/client/event/RenderPlayerEvent$Pre"
               for r in refs):
        return fail("the listener does not take the player render event")
    print("ok: a client listener at HIGHEST on RenderPlayerEvent.Pre")

    # -- it asks Touch'n Motion itself, once resolved ----------------------
    if TN_MODEL_SPACE not in body:
        return fail("the listener does not name Touch'n Motion's pose entry point")
    if TN_BEFORE not in body:
        return fail("the listener does not name the entry point method")
    for param in ("net/minecraft/world/entity/LivingEntity", "com/mojang/blaze3d/vertex/PoseStack",
                  "java/util/function/Consumer"):
        if param not in body:
            return fail("the resolved signature does not match Touch'n Motion's (%s)" % param)
    if not any(r[0] == "java/lang/Class" and r[1] == "forName" for r in refs):
        return fail("the class is never looked up - it would be a load-time dependency on "
                    "Touch'n Motion")
    if not any(r[1] == "invoke" and r[0] == "java/lang/reflect/Method" for r in refs):
        return fail("the entry point is never called")
    fields = {pose["cp"][n][1]: pose["cp"][d][1] for _a, n, d, _t in pose["methods"]}
    static_fields = {pose["cp"][f[1]][1]: pose["cp"][f[2]][1] for f in pose["fields"]}
    if static_fields.get("poseBefore") != "Ljava/lang/reflect/Method;":
        return fail("the resolved method is not kept - it would be looked up every frame")
    if "Ljava/util/function/Consumer;" not in "".join(static_fields.values()):
        return fail("no constant consumer - a lambda would be allocated every frame")
    print("ok: Touch'n Motion's entry point is resolved once and reused")

    # -- only for a possessed player, and never at the cost of the pass ----
    guard, guard_code = code_text(pose, "onRenderPlayerPre")
    if not guard:
        return fail("the listener has no handler")
    order = [guard.find("possessedModelName"), guard.find("getPoseStack")]
    if -1 in order:
        return fail("the handler does not check possession before using the PoseStack")
    if not any(r[0] == "dev/settledlands/npc/client/FirstPersonPossession256"
               and r[1] == "possessedModelName" for r in refs):
        return fail("the handler never asks whether the player is possessed")
    if not guard_code["exc"]:
        return fail("nothing catches a failure in the handler - it would reach the render pass")
    if not any(r[0] == "org/slf4j/Logger" and r[1] == "warn" for r in refs):
        return fail("a changed Touch'n Motion is never reported")
    if "reported" not in static_fields:
        return fail("the warning is not bounded to once")
    call, call_code = code_text(pose, "pose")
    if not call or not call_code["exc"]:
        return fail("the call itself is not guarded - a version change would throw every frame")
    print("ok: only a possessed player is passed on, and a changed Touch'n Motion is reported once")

    print("touch_n_motion_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
