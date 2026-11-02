#!/usr/bin/env python3
"""Gate: a scene hand belongs to the NPC you possess - and the build starts.

Build 343. Cinematic Interactions replaces block windows with first-person scenes and draws the hands of
a scene itself: `Hands.drawArm` builds its own arm cubes around the station and textures them with
`LocalPlayer.getSkin()`. A possessed player is drawn as an Ivy rig, so the scene gave them the player's
own arm hanging over the crafting table. Cinematic also owns a model-based pass for that arm
(`Hands.drawModelArm`, used with Yes Steve Model) which places the arm and then calls
`PlayerRenderer.renderRightHand` / `renderLeftHand` - the very method Build 339 hands to the NPC. Build
343 routes a possessed player's scene arm onto that pass.

Ways that can ship silently broken - or not ship at all:

  * an interface mixin targets Cinematic's class. Mixin refuses that shape and build 342 died in PREPARE
    with "@Mixin target type mismatch: Hands is not an interface", before the title screen;
  * an @Invoker comes back, or the hand-over reaches Cinematic through a compile-time type, so a missing
    or changed mod stops the game instead of keeping Cinematic's own arm;
  * the injection is not a client mixin, or names a method that no longer exists, so it never applies;
  * it cancels the arm without drawing one - a scene would lose a hand, the one thing a hand-over must
    never do;
  * it fires without a possessed player, or without the arm Cinematic itself would draw;
  * the report is unbounded, or Cinematic's own classes are copied into the mod jar.

    python3 tools/cinematic_check.py settledlands-0.8.0-build343-cinematic-arms.jar

Exit 0 = every shape that stops the game is gone, and a possessed player's scene hands are the NPC's.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

MIXIN = "dev/settledlands/mixin/CinematicHands343.class"
GUARD = "dev/settledlands/npc/client/CinematicArms343.class"
CONFIG = "settledlands.mixins.json"

TARGET = "dev.dyslexic.cinematic.client.Hands"
# The same class as it appears in a constant pool's Class entry.
TARGET_CLASS = "dev/dyslexic/cinematic/client/Hands"
HANDED_OVER = "drawModelArm"
HAND = "drawArm"
# The 7-argument drawArm: pose stack, buffers, camera, hand, shoulder, right, light, forward.
HAND_DESC = ("(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
             "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
             "Lnet/minecraft/world/phys/Vec3;ZILnet/minecraft/world/phys/Vec3;)V")

# Build 342's shapes: an interface mixin with an @Invoker on a class target - refused by Mixin at
# startup. None of them may come back.
BROKEN_342 = (
    "dev/settledlands/npc/client/CinematicArms342.class",
    "dev/settledlands/mixin/CinematicHands342.class",
    "dev/settledlands/mixin/CinematicHandsInvoker342.class",
)

STATIC = 0x0008
INTERFACE = 0x0200


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    refs = set()
    classes = set()
    for index, value in enumerate(cp):
        if not value:
            continue
        if value[0] == 7:
            try:
                classes.add(disasm.cls_name(cp, index))
            except Exception:
                pass
        if value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs.add((owner, name, desc))
    return parsed, refs, classes


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
        for entry in (MIXIN, GUARD):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        for entry in BROKEN_342:
            if entry in names:
                return fail("the jar still carries %s - that shape stopped build 342 at startup" % entry)
        copied = [n for n in names if n.startswith("dev/dyslexic/")]
        if copied:
            return fail("the jar carries Cinematic's own classes: %s" % ", ".join(copied[:3]))
        mixin_blob = z.read(MIXIN)
        guard_blob = z.read(GUARD)
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        # Nothing may hold a compile-time reference to Cinematic: the injection names it in its
        # @Mixin targets string and the guard looks it up by name, so a mod that is absent, renamed or
        # reshaped can never stop the game.
        typed = []
        for entry in names:
            if not entry.endswith(".class"):
                continue
            blob = z.read(entry)
            if b"dev/dyslexic/cinematic" not in blob and b"dev.dyslexic.cinematic" not in blob:
                continue
            _p, _r, classes = info(blob)
            if TARGET_CLASS in classes:
                typed.append(entry)
    if typed:
        return fail("Cinematic is linked as a compile-time type from %s - a missing or reshaped mod "
                    "would stop the game; only text may name it" % ", ".join(sorted(typed)))

    # -- the hand-over is a class mixin, because an interface mixin on a class is what killed 342 ---
    if "CinematicHands343" not in config.get("client", []):
        return fail("the hand-over is not a client mixin - it would never meet the scene renderer")
    if "CinematicHands343" in config.get("mixins", []):
        return fail("the hand-over is a common mixin - scenes are client only")
    if any("Invoker" in name for name in config.get("client", []) + config.get("mixins", [])):
        return fail("an invoker mixin is still registered - Mixin refuses that on a class target")
    mixin, mixin_refs, _classes = info(mixin_blob)
    mixin_text = text(mixin)
    if mixin["access"] & INTERFACE:
        return fail("the hand-over is an interface mixin - Mixin refuses a class target and the game "
                    "will not start (build 342)")
    if "Invoker" in mixin_text:
        return fail("the hand-over declares an @Invoker - that is the shape that killed build 342")
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
    if "Pseudo" not in mixin_text:
        return fail("the hand-over is not @Pseudo - the mod would not load without Cinematic")
    handlers = {n: v for n, v in methods(mixin).items() if n != "<init>"}
    if len(handlers) != 1:
        return fail("the hand-over injects more than one method: %s" % ", ".join(sorted(handlers)))
    name, (access, _desc) = next(iter(handlers.items()))
    if not access & STATIC:
        return fail("%s is not static - Cinematic's arm entry point is static" % name)
    print("ok: one client injection on Hands.%s at HEAD, class mixin, static, @Pseudo, require = 0" % HAND)

    # -- it draws the NPC arm and only then takes the hand -----------------------------------------
    if not any(r[0] == "dev/settledlands/npc/client/CinematicArms343" and r[1] == "handOwner"
               for r in mixin_refs):
        return fail("the injection does not ask whose hand this scene arm is")
    if not any(r[0] == "dev/settledlands/npc/client/CinematicArms343" and r[1] == "drawModelArm"
               for r in mixin_refs):
        return fail("the injection does not go through Cinematic's model-arm pass")
    if not any(r[0].endswith("callback/CallbackInfo") and r[1] == "cancel" for r in mixin_refs):
        return fail("the hand-over never takes the hand from Cinematic")
    print("ok: the arm is placed by Cinematic and taken only after it was handed over")

    # -- and the guard reaches Cinematic softly, once ---------------------------------------------
    guard, guard_refs, guard_classes = info(guard_blob)
    guard_text = text(guard)
    if TARGET_CLASS in guard_classes:
        return fail("the guard links Cinematic at compile time - a missing mod would stop the game")
    if TARGET not in guard_text:
        return fail("the guard does not name Cinematic's class as text")
    if HANDED_OVER not in guard_text:
        return fail("the guard does not name Cinematic's model-arm pass")
    if "MethodHandle" not in guard_text or "invokeExact" not in guard_text:
        return fail("the hand-over does not go through a resolved handle - it would need a mixin "
                    "accessor, the shape that killed build 342")
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
    if not any(r[1] == "unreflect" for r in guard_refs):
        return fail("the guard never builds the handle it promises")
    print("ok: the arm pass is resolved once, as text, and every refusal keeps Cinematic's own hand")

    print("cinematic_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
