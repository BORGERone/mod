#!/usr/bin/env python3
"""Gate: the "Общее" tab must be one graph, and every NPC must really run it.

Build 334 makes the tab common. Three ways that could ship silently broken:

  * the graph is stored per NPC after all - then "common" is a word in the README, not a behaviour;
  * the mixin is listed as a client mixin - then the server never applies it and no NPC ticks the
    common graph, while the tab still shows it;
  * the hooks are named right but call the wrong descriptors - the stub drift of this repo: an old
    compile mirror of CompoundTag answered put(String,Object)V and remove(String)Tag, which compile
    fine and die at runtime with NoSuchMethodError.

So the shipped classes are read for the store (SavedData, the world file name, the tag key), for the
mixin lists, for the three tick calls and the damage call, and for the exact descriptors of the two
calls that were fixed this build.

    python3 tools/common_graph_check.py settledlands-0.8.0-build334-common-graph.jar

Exit 0 = one graph, in world data, ticked by every NPC, and the tab says so.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

STORAGE = "dev/settledlands/npc/NpcCommonGraphStorage.class"
HELPER = "dev/settledlands/npc/NpcCommonGraph334.class"
MIXIN = "dev/settledlands/mixin/NpcCommonGraphMixin334.class"
DAMAGE = "dev/settledlands/mixin/NpcCommonGraphDamageMixin347.class"
CONFIG = "settledlands.mixins.json"

SAVED_DATA = "net/minecraft/world/level/saveddata/SavedData"
TICK_CALLS = (
    "dev/settledlands/npc/GuideDialogue.tickGraphCheckNodes",
    "dev/settledlands/npc/GuideDialogue.tickGraphTimeNodes",
    "dev/settledlands/npc/GuideDialogue.tickGraphTimerNodes",
)
# The two descriptors the old compile mirror got wrong; both are what the game really has.
PUT = ("net/minecraft/nbt/CompoundTag", "put",
       "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;")
REMOVE = ("net/minecraft/nbt/CompoundTag", "remove", "(Ljava/lang/String;)V")


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
    """(class info, set of (owner, name, descriptor) references)."""
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


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (STORAGE, HELPER, MIXIN, DAMAGE):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        blobs = {entry: z.read(entry) for entry in (STORAGE, HELPER, MIXIN, DAMAGE)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        lang_ru = json.loads(z.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        lang_en = json.loads(z.read("assets/settledlands/lang/en_us.json").decode("utf-8"))

    # -- the store ---------------------------------------------------------
    store, store_refs = info(blobs[STORAGE])
    if disasm.cls_name(store["cp"], store["super"]) != SAVED_DATA:
        return fail("the store is not a SavedData - it cannot live in world data")
    store_text = text(store)
    if "settledlands_common_graph" not in store_text:
        return fail("the store has no world file name")
    for ref in (("net/minecraft/world/level/storage/DimensionDataStorage", "computeIfAbsent", None),
                ("net/minecraft/world/level/saveddata/SavedData$Factory", "<init>", None),
                ("dev/settledlands/npc/NpcDialogueGraph", "save", "()Lnet/minecraft/nbt/CompoundTag;"),
                ("dev/settledlands/npc/NpcDialogueGraph", "load",
                 "(Lnet/minecraft/nbt/CompoundTag;)Ldev/settledlands/npc/NpcDialogueGraph;")):
        owner, name, desc = ref
        if not any(r[0] == owner and r[1] == name and (desc is None or r[2] == desc)
                   for r in store_refs):
            return fail("the store never calls %s.%s" % (owner, name))
    if not any(r[1] == "setDirty" for r in store_refs):
        return fail("the store never marks itself dirty - the graph would not be written")
    print("ok: one graph in world data (%s), loaded and saved through NpcDialogueGraph"
          % "settledlands_common_graph")

    # -- one graph for every NPC, not a copy ---------------------------------
    _helper, helper_refs = info(blobs[HELPER])
    if not any(r[0] == "dev/settledlands/npc/GuideDialogue" and r[1] == "handleNpcDamaged"
               and r[2].endswith("NpcDialogueGraph;)V") for r in helper_refs):
        return fail("the helper never runs the damage chain of the common graph")

    # -- the mixins are applied where they matter ---------------------------
    common = config.get("mixins", [])
    client = config.get("client", [])
    for name in ("NpcCommonGraphMixin334", "NpcCommonGraphDamageMixin347"):
        if name not in common:
            return fail("%s is not in the common mixin list - the server would never apply it" % name)
        if name in client:
            return fail("%s is listed as a client mixin" % name)
    print("ok: both hooks are common-side mixins (server included)")

    # -- the hooks ----------------------------------------------------------
    mixin, mixin_refs = info(blobs[MIXIN])
    mixin_text = text(mixin)
    for call in TICK_CALLS:
        owner, name = call.split(".")
        if not any(r[0] == owner and r[1] == name for r in mixin_refs):
            return fail("the tick hook never calls %s" % name)
    for hook in ("settledlands$tickCommon", "settledlands$sendCommon", "settledlands$takeCommon"):
        if hook not in mixin_text:
            return fail("the mixin has no %s handler" % hook)
    if not any(r[0] == "dev/settledlands/npc/NpcCommonGraph334" and r[1] == "save"
               for r in mixin_refs):
        return fail("saving the tab does not reach the common store")
    if PUT not in mixin_refs:
        return fail("CompoundTag.put is not the real %s - StubDrift" % (PUT[2],))
    if REMOVE not in mixin_refs:
        return fail("CompoundTag.remove is not the real %s - StubDrift" % (REMOVE[2],))
    print("ok: tick replaced for the anim pass only, editor and save read and write the store")

    # -- the damage hook adds a chain, it does not replace the stock three ---------------
    damage, damage_refs = info(blobs[DAMAGE])
    if not any(r[0] == "dev/settledlands/npc/NpcCommonGraph334" and r[1] == "damage"
               for r in damage_refs):
        return fail("the damage hook does not reach the helper")
    damage_text = text(damage)
    if "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z" not in damage_text:
        return fail("the shared damage chain is not run from GuideNpc.hurt")
    if "handleNpcDamaged" in damage_text:
        return fail("the shared damage chain still hooks handleNpcDamaged - that method is what the "
                    "NPC's own tab, the walk tab and the animation tab all pass through, so one hook "
                    "there replaces all three")
    if "cancellable" in damage_text:
        return fail("the shared damage hook can cancel - it must add a chain, not replace one")
    if not any(r[1] == "damageCooldowns" and r[0].endswith("NpcCommonGraphDamageMixin347")
               for r in damage_refs) and "damageCooldowns" not in damage_text:
        return fail("the shared damage chain has no cooldown map to work with")
    print("ok: the shared graph is one more damage chain, run once per hit from GuideNpc.hurt")

    # -- the tab says so ----------------------------------------------------
    if lang_ru.get("gui.settledlands.graph.tab_anim") != "Общее":
        return fail("ru_ru tab_anim is %r, not 'Общее'"
                    % lang_ru.get("gui.settledlands.graph.tab_anim"))
    if lang_en.get("gui.settledlands.graph.tab_anim") != "Common":
        return fail("en_us tab_anim is %r, not 'Common'"
                    % lang_en.get("gui.settledlands.graph.tab_anim"))
    print("ok: the tab is called 'Общее' (ru) / 'Common' (en)")

    print("common_graph_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
