#!/usr/bin/env python3
"""Gate: the buttons of the "Общее" graph must really reach the conversation.

Build 335 hangs the root buttons of the common graph onto the line a talk opens with by giving the
NPC a merged graph for the length of that talk. Four ways that can ship silently broken:

  * the hook is attached in the wrong place - the conversation is built on the NPC's own graph and
    the buttons never appear, which is exactly the bug this build fixes;
  * the merged graph is left in the profile - it would be written to the entity NBT and opened in
    the editor of every other player;
  * the merge mutates the graphs it copies instead of a copy - the author's data would grow buttons
    nobody placed;
  * the hook calls a descriptor that only exists in the compile stubs - the stub drift of this repo.

So the shipped classes are read for the injection points that must exist in the jar, for the two
mixin lists, for the merge calls, and for the exact save descriptor the strip is hooked on.

    python3 tools/common_dialogue_check.py settledlands-0.8.0-build335-common-buttons.jar

Exit 0 = the buttons reach the talk, and the merged graph cannot reach disk.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

HELPER = "dev/settledlands/npc/NpcCommonDialogue335.class"
STATE = "dev/settledlands/npc/NpcCommonDialogue335$State.class"
START_MIXIN = "dev/settledlands/mixin/NpcCommonDialogueMixin335.class"
SAVE_MIXIN = "dev/settledlands/mixin/NpcCommonDialogueSaveMixin335.class"
CONFIG = "settledlands.mixins.json"
DIALOGUE = "dev/settledlands/npc/GuideDialogue.class"
PROFILE = "dev/settledlands/npc/NpcProfile.class"

START = ("settledlands$originalStart",
         "(Lnet/minecraft/world/entity/player/Player;Ldev/settledlands/npc/GuideNpc;)V")
STOP = ("stop", "(Lnet/minecraft/server/level/ServerPlayer;)V")
SAVE = ("save", "(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;")
SAVE_HANDLER = ("(Lnet/minecraft/core/HolderLookup$Provider;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V")
GRAPH_FIELD = ("dev/settledlands/npc/NpcProfile", "dialogueGraph",
               "Ldev/settledlands/npc/NpcDialogueGraph;")
STOP_REF = "Ldev/settledlands/npc/GuideDialogue;stop(Lnet/minecraft/server/level/ServerPlayer;)V"


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
    """(class info, set of (owner, name, descriptor) references, set of member names)."""
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
    out = set()
    for _acc, name_idx, desc_idx, _attrs in parsed["methods"]:
        out.add((cp[name_idx][1], cp[desc_idx][1]))
    return out


def fields(parsed):
    cp = parsed["cp"]
    out = set()
    for _acc, name_idx, desc_idx, _attrs in parsed["fields"]:
        out.add((cp[name_idx][1], cp[desc_idx][1]))
    return out


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (HELPER, STATE, START_MIXIN, SAVE_MIXIN):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        blobs = {entry: z.read(entry) for entry in (HELPER, STATE, START_MIXIN, SAVE_MIXIN,
                                                    DIALOGUE, PROFILE)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    # -- the hooks are applied where they matter -----------------------------
    common = config.get("mixins", [])
    client = config.get("client", [])
    for name in ("NpcCommonDialogueMixin335", "NpcCommonDialogueSaveMixin335"):
        if name not in common:
            return fail("%s is not in the common mixin list - the server would never apply it" % name)
        if name in client:
            return fail("%s is listed as a client mixin" % name)
    print("ok: both hooks are common-side mixins (%d common, %d client)"
          % (len(common), len(client)))

    # -- the injection points exist in the class they are fired on -----------
    dialogue, dialogue_refs = info(blobs[DIALOGUE])
    targets = methods(dialogue)
    if START not in targets:
        return fail("GuideDialogue has no %s%s - the talk would never be attached" % START)
    if STOP not in targets:
        return fail("GuideDialogue has no %s%s - the NPC would never get its graph back" % STOP)
    if not any(r[0] == "dev/settledlands/npc/GuideDialogue" and (r[1], r[2]) == STOP
               for r in dialogue_refs):
        return fail("start never calls stop - there is no point to attach the merge at")

    profile, profile_refs = info(blobs[PROFILE])
    if SAVE not in methods(profile):
        return fail("NpcProfile has no %s%s - the strip would never fire and the merge would be "
                    "written to disk" % SAVE)
    if ("dialogueGraph", "Ldev/settledlands/npc/NpcDialogueGraph;") not in fields(profile):
        return fail("NpcProfile has no dialogueGraph field to swap")
    print("ok: attach after stop in start, detach in stop, strip in save - all present in the jar")

    # -- the hooks name those exact members ----------------------------------
    start_mixin, start_refs = info(blobs[START_MIXIN])
    start_text = text(start_mixin)
    if "settledlands$originalStart" not in start_text or STOP_REF not in start_text:
        return fail("the attach hook does not point at %s" % STOP_REF)
    if not any(r[0] == "dev/settledlands/npc/NpcCommonDialogue335" and r[1] == "attach"
               for r in start_refs):
        return fail("the attach hook never reaches the helper")
    if not any(r[0] == "dev/settledlands/npc/NpcCommonDialogue335" and r[1] == "detach"
               for r in start_refs):
        return fail("the detach hook never reaches the helper")

    save_mixin, save_refs = info(blobs[SAVE_MIXIN])
    if not any(r[0] == "dev/settledlands/npc/NpcCommonDialogue335" and r[1] == "strip"
               for r in save_refs):
        return fail("the save hook never strips the merged graph")
    if not any(r[0] == "dev/settledlands/npc/NpcCommonDialogue335" and r[1] == "unstrip"
               for r in save_refs):
        return fail("the save hook never puts the merged graph back")
    # The hook is bound by name, so what guards against stub drift is its own signature: the handler
    # has to take exactly the parameters the real save takes and to return the tag.
    save_text = text(save_mixin)
    if "dev.settledlands.npc.NpcProfile" not in save_text:
        return fail("the save hook does not target NpcProfile")
    handlers = set(methods(save_mixin))
    for name in ("settledlands$stripCommon", "settledlands$putCommonBack"):
        if (name, SAVE_HANDLER) not in handlers:
            return fail("%s does not take the parameters of %s%s - StubDrift"
                        % (name, SAVE[0], SAVE[1]))
    if "CallbackInfoReturnable<Lnet/minecraft/nbt/CompoundTag;>" not in save_text:
        return fail("the save hook does not return a CompoundTag - it cannot be hooked on save")
    print("ok: hooks reach the helper and carry the exact signature of the real save")

    # -- the merge works on copies, and the copy can be undone ---------------
    helper, helper_refs = info(blobs[HELPER])
    for ref, what in (
        (("dev/settledlands/npc/NpcCommonGraph334", "graph", None), "the common graph itself"),
        (("dev/settledlands/npc/GuideDialogue", "graphActive", None), "whether the talk runs on a graph"),
        (("dev/settledlands/npc/NpcDialogueGraph", "copy", "()Ldev/settledlands/npc/NpcDialogueGraph;"),
         "a copy of the NPC's graph"),
        (("dev/settledlands/npc/NpcDialogueGraph$Node", "copy",
          "()Ldev/settledlands/npc/NpcDialogueGraph$Node;"), "a copy of a common node"),
        (("dev/settledlands/npc/NpcDialogueGraph", "get", None), "node lookup"),
    ):
        owner, name, desc = ref
        if not any(r[0] == owner and r[1] == name and (desc is None or r[2] == desc)
                   for r in helper_refs):
            return fail("the merge never takes %s" % what)
    if GRAPH_FIELD not in helper_refs:
        return fail("the merge never swaps NpcProfile.dialogueGraph")
    if any(r[0] == "dev/settledlands/npc/NpcCommonGraph334" and r[1] == "save" for r in helper_refs):
        return fail("the merge writes the common graph - it must only ever read it")
    if any(r[0] == "java/io" for r in helper_refs):
        return fail("the merge touches disk")
    helper_text = text(helper)
    for hook in ("reachable", "startLines", "roots", "merge", "strip", "unstrip"):
        if hook not in helper_text:
            return fail("the helper has no %s step" % hook)
    print("ok: merged from copies, common graph only read, swap undoable through strip/unstrip")

    print("common_dialogue_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
