#!/usr/bin/env python3
"""Gate: with Punchy! installed, a possessed player shows the NPC's arms, not the player's.

Build 338. Punchy! draws the first-person arms itself and never asks the vanilla hand pass, so the
possession mod was never offered the arm: a possessed NPC kept showing the player's skin. The hook is
one mixin on Punchy!'s own renderer, but Punchy! is closed source - nothing here can be checked
against its jar, so the shape of the hook is what has to be checked.

Ways that can ship silently broken:

  * the handler signature differs from Punchy!'s renderArm by one parameter - the injection never
    applies and the arms stay the player's, with no error anywhere;
  * the mixin is not @Pseudo - the mod fails to load on a client without Punchy!;
  * the mixin is listed as a common mixin - a dedicated server has no renderer to hook;
  * the arm is drawn before the possession check, so an ordinary player loses Punchy!'s own arm;
  * the NPC arm is drawn without pushing the render context, or the context is left on the stack;
  * the model's visible flags are flipped without putting them back;
  * an exception in the bridge reaches Punchy! instead of falling back to its own arm.

    python3 tools/punchy_bridge_check.py settledlands-0.8.0-build338-punchy-arms.jar

Exit 0 = the hook applies on a possessed player only, and cannot take Punchy! down with it.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

BRIDGE = "dev/settledlands/npc/client/PunchyArm338.class"
HOOK = "dev/settledlands/mixin/PunchyArmBridgeMixin338.class"
CONFIG = "settledlands.mixins.json"
PUNCHY = "punchy.client.render.PunchyArmRenderer"

# Punchy!'s own signatures on 1.21.1 - the handler must match them exactly, parameter for parameter.
ARM_HANDLER = ("settledlands$npcArm",
               "(Lnet/minecraft/client/model/PlayerModel;"
               "Lnet/minecraft/client/player/AbstractClientPlayer;"
               "Lnet/minecraft/world/entity/HumanoidArm;"
               "Lcom/mojang/blaze3d/vertex/PoseStack;"
               "Lnet/minecraft/client/renderer/MultiBufferSource;"
               "ILnet/minecraft/resources/ResourceLocation;ZF"
               "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
ARMOR_HANDLER = ("settledlands$noPlayerSleeve",
                 "(Lnet/minecraft/client/model/PlayerModel;"
                 "Lnet/minecraft/client/player/AbstractClientPlayer;"
                 "Lnet/minecraft/world/entity/HumanoidArm;"
                 "Lcom/mojang/blaze3d/vertex/PoseStack;"
                 "Lnet/minecraft/client/renderer/MultiBufferSource;"
                 "IZF"
                 "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")

# Classes of the game itself must never be in the mod jar - they would shadow the real ones.
GAME_CLASSES = (
    "net/minecraft/client/model/PlayerModel.class",
    "net/minecraft/client/player/AbstractClientPlayer.class",
    "net/minecraft/client/renderer/MultiBufferSource.class",
    "net/minecraft/client/renderer/texture/OverlayTexture.class",
    "net/minecraft/world/entity/HumanoidArm.class",
    "org/spongepowered/asm/mixin/Pseudo.class",
)


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
    return [(cp[n][1], cp[d][1], attrs) for _a, n, d, attrs in parsed["methods"]]


def code_text(parsed, name):
    cp = parsed["cp"]
    for method_name, _desc, attrs in methods(parsed):
        if method_name != name:
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
        for entry in (BRIDGE, HOOK):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        shipped = [n for n in GAME_CLASSES if n in names]
        if shipped:
            return fail("the jar carries game classes: %s" % ", ".join(shipped))
        blobs = {e: z.read(e) for e in (BRIDGE, HOOK)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    # -- the hook is on Punchy!'s renderer, and only on the client ----------
    hook, hook_refs = info(blobs[HOOK])
    hook_text = text(hook)
    common = config.get("mixins", [])
    client = config.get("client", [])
    if "PunchyArmBridgeMixin338" not in client:
        return fail("the hook is not a client mixin - it would never meet a renderer")
    if "PunchyArmBridgeMixin338" in common:
        return fail("the hook is a common mixin - a dedicated server has no renderer to hook")
    if "Lorg/spongepowered/asm/mixin/Pseudo;" not in hook_text:
        return fail("the hook is not @Pseudo - the mod would fail to load without Punchy!")
    if PUNCHY not in hook_text:
        return fail("the hook does not name %s" % PUNCHY)
    if "renderArm" not in hook_text or "renderArmArmor" not in hook_text:
        return fail("the hook does not name Punchy!'s arm and armour passes")
    if "require" not in hook_text:
        return fail("the hook does not tolerate a Punchy! build without that method")
    print("ok: a @Pseudo client hook on %s.renderArm" % PUNCHY)

    # -- the handler is Punchy!'s own signature ----------------------------
    present = {(n, d) for n, d, _a in methods(hook)}
    for name, desc in (ARM_HANDLER, ARMOR_HANDLER):
        if (name, desc) not in present:
            return fail("%s does not take Punchy!'s parameters - the injection would never apply"
                        % name)
    if not any(r[0] == "dev/settledlands/npc/client/PunchyArm338" and r[1] == "replace"
               for r in hook_refs):
        return fail("the arm handler never asks for the NPC arm")
    if not any(r[0] == "dev/settledlands/npc/client/PunchyArm338" and r[1] == "consumeArmor"
               for r in hook_refs):
        return fail("the armour handler never checks whether the arm was replaced")
    if not any(r[0].endswith("callback/CallbackInfo") and r[1] == "cancel" for r in hook_refs):
        return fail("the hook cannot cancel Punchy!'s own arm")
    print("ok: both handlers match Punchy!'s signatures and cancel its own arm")

    # -- the bridge draws the NPC arm, and only for a possessed player -----
    bridge, bridge_refs = info(blobs[BRIDGE])
    bridge_text = text(bridge)
    for owner, name in (("dev/settledlands/npc/client/FirstPersonPossession256", "possessedModelName"),
                        ("dev/settledlands/npc/client/FirstPersonPossession256",
                         "pushFirstPersonRenderContext"),
                        ("dev/settledlands/npc/client/FirstPersonPossession256",
                         "popPlayerRenderContext"),
                        ("dev/settledlands/npc/client/FirstPersonPossession256",
                         "renderPossessedFirstPersonModelArms"),
                        ("dev/settledlands/model/BbModelManager", "getModel"),
                        ("dev/settledlands/model/BbModel", "getPart"),
                        ("dev/settledlands/model/BbModel", "getTextureLocation"),
                        ("net/minecraft/client/model/geom/ModelPart", "visible"),
                        ("java/lang/ThreadLocal", "remove")):
        if not any(r[0] == owner and r[1] == name for r in bridge_refs):
            return fail("the bridge never uses %s.%s" % (owner.rsplit("/", 1)[-1], name))
    for alias in ("rightarm", "right_arm", "leftarm", "left_arm"):
        if alias not in bridge_text:
            return fail("the bridge does not look for the %r bone - some NPCs would lose the arm"
                        % alias)
    print("ok: the bridge asks for the NPC arm by bone name and restores what it touches")

    # -- the order inside the arm call is what keeps an ordinary player intact
    txt, code = code_text(bridge, "replace")
    if not txt:
        return fail("the bridge has no replace method")
    steps = ["possessedModelName", "getModel", "pushFirstPersonRenderContext",
             "renderPossessedFirstPersonModelArms", "popPlayerRenderContext"]
    where = [txt.find(step) if txt.find(step) >= 0 else -1 for step in steps]
    if min(where) < 0:
        return fail("the arm call does not run through %s" % steps[where.index(-1)])
    if where != sorted(where):
        return fail("the arm call runs out of order: %s" % dict(zip(steps, where)))
    if not code["exc"]:
        return fail("nothing catches a failure in the arm call - it would reach Punchy!")
    print("ok: an unpossessed player is decided before anything is drawn, and a failure falls back")

    print("punchy_bridge_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
