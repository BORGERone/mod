#!/usr/bin/env python3
"""Gate: the armour pass of build 348 is wired where it has to be.

Build 348 draws the armour an entity wears on the body the mod renders, and puts an NPC's carried
armour into its equipment slots. Ways that can ship broken:

  * the client injection is bound to an anchor the body method does not contain - Mixin then refuses
    the class and the client dies the moment a body is drawn;
  * the injection is listed as a common mixin, so the server loads a class that names client-only
    types;
  * the anchor is added to our own mixin list but the mixin is applied before the body method exists;
  * the armour pass is fed the wrong armour model for leggings (vanilla uses the inner model for the
    LEGS slot only and the outer model for everything else);
  * the NPC sync writes into the inventory instead of only reading it - that is how a stack ends up
    duplicated, and the inventory is the user's own list.

    python3 tools/body_armor_check.py settledlands-0.8.0-build348-body-armor.jar

Exit 0 = both hooks are in the right lists, the anchors exist in the class they point at, and the NPC
sync only reads.
"""
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

BODY = "dev/settledlands/model/BodyArmor348.class"
BODY_RIG = "dev/settledlands/model/BodyArmor348$Rig.class"
NPC = "dev/settledlands/npc/NpcArmor348.class"
CLIENT_MIXIN = "dev/settledlands/mixin/BbModelArmor348Mixin.class"
INV_MIXIN = "dev/settledlands/mixin/NpcInventoryArmor348Mixin.class"
LOAD_MIXIN = "dev/settledlands/mixin/GuideNpcArmor348Mixin.class"
BBMODEL = "dev/settledlands/model/BbModel.class"
CONFIG = "settledlands.mixins.json"

PLAYER_BODY = ("settledlands$renderActorOriginal(Lnet/minecraft/world/entity/LivingEntity;FF"
               "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
NPC_BODY = ("settledlands$renderNpcOriginal(Ldev/settledlands/npc/GuideNpc;FF"
            "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V")
ROOT_RENDER = ("Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;"
               "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")


def fail(message):
    print("FAIL: " + message)
    return 1


def strings(data):
    pool = disasm.parse_class(data)["cp"]
    return [str(value[1]) for value in pool if value and value[0] == 1]


def code_strings(data):
    """Every member reference the class makes from its own bytecode, as owner.name(desc)."""
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    out = set()
    for method in parsed["methods"]:
        code = disasm.code_of(method[3], cp)
        if not code:
            continue
        text, _ = disasm.disasm(code["code"], cp)
        for line in text.split("\n"):
            if ("invoke" in line or "getstatic" in line or "putstatic" in line
                    or "getfield" in line or "putfield" in line):
                tail = line.split("# ", 1)[-1].strip()
                out.add(tail)
    return out


def method_body(data, name):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    for method in parsed["methods"]:
        if cp[method[1]][1] != name:
            continue
        code = disasm.code_of(method[3], cp)
        if not code:
            return ""
        text, _ = disasm.disasm(code["code"], cp)
        return text
    return None


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (BODY, BODY_RIG, NPC, CLIENT_MIXIN, INV_MIXIN, LOAD_MIXIN, BBMODEL, CONFIG):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        blobs = {entry: z.read(entry) for entry in
                 (BODY, NPC, CLIENT_MIXIN, INV_MIXIN, LOAD_MIXIN, BBMODEL)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    client = config.get("client", [])
    common = config.get("mixins", [])
    if "BbModelArmor348Mixin" not in client:
        return fail("BbModelArmor348Mixin must be a client mixin")
    if "BbModelArmor348Mixin" in common:
        return fail("BbModelArmor348Mixin must not be a common mixin")
    for name in ("NpcInventoryArmor348Mixin", "GuideNpcArmor348Mixin"):
        if name not in common:
            return fail("%s must be a common mixin" % name)
        if name in client:
            return fail("%s must not be a client mixin" % name)
    print("ok: armour pass is client, both NPC hooks are common")

    # The anchors: the body methods really draw the root part, and they carry the exact descriptor.
    for wanted, label in ((PLAYER_BODY, "player body"), (NPC_BODY, "NPC body")):
        name = wanted.split("(", 1)[0]
        body = method_body(blobs[BBMODEL], name)
        if body is None:
            return fail("BbModel has no %s" % name)
        if "ModelPart.render" not in body:
            return fail("%s never draws the root part - the anchor would not match" % label)
        if "ModelPart.render(Lcom/mojang/blaze3d/vertex/PoseStack;" \
           "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V" not in body:
            return fail("%s draws a different ModelPart.render than the injection names" % label)
        if body.count("ModelPart.render") != 1:
            return fail("%s draws the root part more than once - the anchor needs an ordinal" % label)
    print("ok: both bodies draw the root part exactly once, with the anchor's descriptor")

    mixin = strings(blobs[CLIENT_MIXIN])
    for marker in ("settledlands$renderActorOriginal", "settledlands$renderNpcOriginal"):
        if not any(marker in entry for entry in mixin):
            return fail("%s does not name %s" % (CLIENT_MIXIN, marker))
    if ROOT_RENDER not in mixin:
        return fail("%s does not carry the anchor string" % CLIENT_MIXIN)
    print("ok: the client mixin names both body methods and the root-part anchor")

    inv = strings(blobs[INV_MIXIN])
    if not any(entry == "setChanged" for entry in inv):
        return fail("%s does not hook setChanged" % INV_MIXIN)
    if not any("PossessionSystem$NpcInventory" in entry for entry in inv):
        return fail("%s does not target NpcInventory" % INV_MIXIN)
    load = strings(blobs[LOAD_MIXIN])
    if not any("readAdditionalSaveData" in entry for entry in load):
        return fail("%s does not hook readAdditionalSaveData" % LOAD_MIXIN)
    print("ok: an inventory edit and a world load both reach the sync")

    npc = code_strings(blobs[NPC])
    for needed in ("LivingEntity.getItemBySlot", "LivingEntity.setItemSlot"):
        if not any(needed in entry for entry in npc):
            return fail("%s never calls %s" % (NPC, needed))
    for forbidden in ("Container.setItem", "SimpleContainer.setItem", "setChanged"):
        if any(forbidden in entry for entry in npc):
            return fail("%s writes into the inventory (%s) - the list must stay read-only"
                        % (NPC, forbidden))
    print("ok: the NPC sync reads the inventory and writes only the equipment slots")

    body = code_strings(blobs[BODY])
    for needed in ("ModelLayers.PLAYER_INNER_ARMOR", "ModelLayers.PLAYER_OUTER_ARMOR",
                   "RenderType.armorCutoutNoCull", "RenderType.armorEntityGlint",
                   "HumanoidModel.renderToBuffer"):
        if not any(needed in entry for entry in body):
            return fail("%s never calls %s" % (BODY, needed))
    print("ok: both armour models are baked, both render types are used, the glint pass is there")

    plain = strings(blobs[BODY])
    if not any(entry == "LEGS" for entry in plain):
        return fail("%s never splits leggings off onto the inner model" % BODY)
    print("body_armor_check: OK  %s  (armour on both bodies, NPC wears what it carries)"
          % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
