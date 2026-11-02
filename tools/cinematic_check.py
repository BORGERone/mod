#!/usr/bin/env python3
"""Gate: a scene hand belongs to the NPC you possess, not to the player's own skin.

Build 342. Cinematic Interactions replaces block windows with first-person scenes and draws the hands of
a scene itself: `Hands.drawArm` builds its own arm cubes around the station and textures them with
`LocalPlayer.getSkin()`. A possessed player is drawn as an Ivy rig, so the scene gave them the player's
own arm hanging over the crafting table. Cinematic also owns a model-based pass for that arm
(`Hands.drawModelArm`, used with Yes Steve Model) which places the arm and then calls
`PlayerRenderer.renderRightHand` / `renderLeftHand` - the very method Build 339 hands to the NPC. Build
342 routes a possessed player's scene arm onto that pass.

Ways that can ship silently broken:

  * the injection is not a client mixin, or names a method that no longer exists, so it never applies;
  * it cancels the arm without drawing one - a scene would lose a hand, the one thing a hand-over must
    never do;
  * it fires without a possessed player, or without the arm Cinematic itself would draw, so a normal
    scene loses its hands;
  * it reaches into Cinematic's placement and rebuilds the arm geometry here, where it would drift away
    from the mod on its next update;
  * the report is unbounded, or Cinematic's own classes are copied into the mod jar.

    python3 tools/cinematic_check.py settledlands-0.8.0-build342-cinematic-arms.jar

Exit 0 = a possessed player's scene hands are the NPC's, and every other hand stays Cinematic's own.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

MIXIN = "dev/settledlands/mixin/CinematicHands342.class"
INVOKER = "dev/settledlands/mixin/CinematicHandsInvoker342.class"
GUARD = "dev/settledlands/npc/client/CinematicArms342.class"
CONFIG = "settledlands.mixins.json"

TARGET = "dev.dyslexic.cinematic.client.Hands"
HANDED_OVER = "drawModelArm"
HAND = "drawArm"
# The 7-argument drawArm: pose stack, buffers, camera, hand, shoulder, right, light, forward.
HAND_DESC = ("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
             "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
             "Lnet/minecraft/world/phys/Vec3;ZILnet/minecraft/world/phys/Vec3;)V")

STATIC = 0x0008


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
    return {cp[n][1]: (a, cp[d][1] if d else "") for a, n, d, _t in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (MIXIN, INVOKER, GUARD):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        copied = [n for n in names if n.startswith("dev/dyslexic/")]
        if copied:
            return fail("the jar carries Cinematic's own classes: %s" % ", ".join(copied[:3]))
        mixin_blob = z.read(MIXIN)
        invoker_blob = z.read(INVOKER)
        guard_blob = z.read(GUARD)
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        # Only these two classes may name Cinematic at all: one hand-over, one place to look.
        touching = []
        for entry in names:
            if not entry.endswith(".class"):
                continue
            blob = z.read(entry)
            if b"dev/dyslexic/cinematic" in blob or b"dev.dyslexic.cinematic" in blob:
                touching.append(entry)
    if sorted(touching) != sorted([MIXIN, INVOKER]):
        return fail("Cinematic is named from %s - the hand-over must live in the two mixins"
                    % (", ".join(sorted(set(touching) - {MIXIN, INVOKER})) or "nowhere"))

    # -- the hand-over is a client mixin on Cinematic's own arm entry point -----------------
    if "CinematicHands342" not in config.get("client", []):
        return fail("the hand-over is not a client mixin - it would never meet the scene renderer")
    if "CinematicHandsInvoker342" not in config.get("client", []):
        return fail("the invoker is not a client mixin - Cinematic's arm pass would stay unreachable")
    if "CinematicHands342" in config.get("mixins", []) or \
            "CinematicHandsInvoker342" in config.get("mixins", []):
        return fail("the hand-over is a common mixin - scenes are client only")
    mixin, mixin_refs = info(mixin_blob)
    mixin_text = text(mixin)
    if TARGET not in mixin_text:
        return fail("the hand-over does not name %s" % TARGET)
    if HAND not in mixin_text:
        return fail("the hand-over does not sit on %s" % HAND)
    if HAND_DESC not in mixin_text:
        return fail("the hand-over does not name the arm signature Cinematic draws with")
    if "HEAD" not in mixin_text or "require" not in mixin_text or "remap" not in mixin_text:
        return fail("the hand-over does not sit at HEAD with require = 0 / remap = false")
    if "cancel" not in mixin_text:
        return fail("the hand-over cannot suppress Cinematic's own arm")
    handlers = {n: v for n, v in methods(mixin).items() if n != "<init>"}
    if len(handlers) != 1:
        return fail("the hand-over injects more than one method: %s" % ", ".join(sorted(handlers)))
    name, (access, _desc) = next(iter(handlers.items()))
    if not access & STATIC:
        return fail("%s is not static - Cinematic's arm entry point is static" % name)
    print("ok: one client injection on Hands.%s at HEAD, static, require = 0" % HAND)

    # -- it draws the NPC arm and only then takes the hand ---------------------------------
    if not any(r[0] == "dev/settledlands/mixin/CinematicHandsInvoker342" and r[1] == "settledlands$drawModelArm"
               for r in mixin_refs):
        return fail("the hand-over does not go through Cinematic's model-arm pass")
    if not any(r[0].endswith("callback/CallbackInfo") and r[1] == "cancel" for r in mixin_refs):
        return fail("the hand-over never takes the hand from Cinematic")
    if not any(r[0].endswith("CinematicArms342") and r[1] == "noteFailure" for r in mixin_refs):
        return fail("a failure would be silent instead of keeping Cinematic's own arm")
    print("ok: the arm is placed by Cinematic and taken only after it was handed over")

    # -- the invoker reaches Cinematic's own placement, it does not copy it ----------------
    invoker, invoker_refs = info(invoker_blob)
    invoker_text = text(invoker)
    if TARGET not in invoker_text:
        return fail("the invoker does not name %s" % TARGET)
    if HANDED_OVER not in invoker_text:
        return fail("the invoker does not name %s" % HANDED_OVER)
    if "Invoker" not in invoker_text:
        return fail("the invoker is not an @Invoker - it would be reflection at render time")
    accessors = {n: v for n, v in methods(invoker).items() if n != "<init>"}
    if len(accessors) != 1:
        return fail("the invoker declares more than one accessor: %s" % ", ".join(sorted(accessors)))
    acc_name, (acc_access, acc_desc) = next(iter(accessors.items()))
    if not acc_access & STATIC:
        return fail("%s is not static - the arm pass it reaches is static" % acc_name)
    if "LocalPlayer" not in acc_desc:
        return fail("the invoker does not hand Cinematic the player whose arm it is")
    print("ok: Cinematic's own model-arm pass is reached directly, no copy of its geometry")

    # -- and only for a possessed player, with an arm Cinematic would draw -----------------
    guard, guard_refs = info(guard_blob)
    guard_text = text(guard)
    if "handOwner" not in guard_text:
        return fail("the guard does not decide whose hand a scene arm is")
    if "possessedModelName" not in guard_text:
        return fail("the guard does not ask the possession state - every scene hand would be taken")
    if "armTooShort" not in guard_text:
        return fail("the guard does not refuse the arms Cinematic itself would not draw")
    if "REPORT_LIMIT" not in guard_text:
        return fail("the report is not bounded")
    if not any(r[0] == "java/util/Set" for r in guard_refs):
        return fail("the report does not remember what it has already said")
    if any(r[1] in ("getSkin", "armStyle") for r in guard_refs):
        return fail("the guard reaches into Cinematic's own settings or the player's skin")
    print("ok: only a possessed player's hand is taken, and the report is bounded")

    print("cinematic_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
