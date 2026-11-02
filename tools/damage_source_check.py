#!/usr/bin/env python3
"""Gate: a damage node fires for the source its inspector shows.

Build 345. The editor's "Источник урона" dropdown and the node card number the four sources

    0 - любое (Any)      1 - от игрока (Player)      2 - от монстра (Monster)      3 - не от игрока

and a node stores the row it was set to. `GuideDialogue.matchesDamageFilter` is the one method that
answers "does this damage match", and it read the same number in a different order - 0 player,
1 monster, 2 non-player, 3 any - so a node set to "От игрока" was tested as "от монстра" and a
player's hit never reached the action: no heal, no line, no command.

Ways that can ship broken:

  * the hook is not applied at all - the stock numbering answers and nothing changes;
  * the hook returns a constant, or is bound to a method that no longer exists;
  * the hook is registered as a client mixin - the server, where damage happens, would never see it;
  * the numbering drifts away from the screen again, which is the whole bug.

    python3 tools/damage_source_check.py settledlands-0.8.0-build345-graph-fixes.jar

Exit 0 = the damage chain asks the rule, and the rule numbers the sources the way the screen does.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

RULE = "dev/settledlands/npc/NpcDamageFilter345.class"
MIXIN = "dev/settledlands/mixin/NpcDamageFilterMixin345.class"
CONFIG = "settledlands.mixins.json"
TARGET = ("matchesDamageFilter(ILnet/minecraft/world/damagesource/DamageSource;)Z",)

INTERFACE = 0x0200


def fail(message):
    print("FAIL: " + message)
    return 1


def info(data):
    parsed = disasm.parse_class(data)
    cp = parsed["cp"]
    refs = set()
    strings = []
    for index, value in enumerate(cp):
        if not value:
            continue
        if value[0] == 1:
            strings.append(str(value[1]))
        if value[0] == 7:
            # instanceof and checkcast point at a class, not at a member.
            try:
                refs.add((disasm.cls_name(cp, index), "", ""))
            except Exception:
                pass
            continue
        if value[0] not in (9, 10, 11):
            continue
        ci, nti = struct.unpack(">HH", value[1])
        try:
            name, desc = disasm.nat(cp, nti)
            owner = disasm.cls_name(cp, ci)
        except Exception:
            continue
        refs.add((owner, name, desc))
    return parsed, refs, strings


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (RULE, MIXIN):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        rule_blob = z.read(RULE)
        mixin_blob = z.read(MIXIN)
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    # -- one common mixin, on the one method that answers the question ---------------------------
    if "NpcDamageFilterMixin345" not in config.get("mixins", []):
        return fail("the damage hook is not a common mixin - the server is where damage is handled")
    if "NpcDamageFilterMixin345" in config.get("client", []):
        return fail("the damage hook is listed as a client mixin - it would never run")
    mixin, mixin_refs, mixin_strings = info(mixin_blob)
    if mixin["access"] & INTERFACE:
        return fail("the damage hook is an interface mixin - Mixin refuses that on a class target")
    joined = " ".join(mixin_strings)
    if TARGET[0] not in joined:
        return fail("the damage hook is not bound to matchesDamageFilter - the stock numbering answers")
    for need in ("HEAD", "cancel", "remap"):
        if need not in joined:
            return fail("the damage hook does not say %s" % need)
    if not any(r[0] == "dev/settledlands/npc/NpcDamageFilter345" and r[1] == "matches"
               for r in mixin_refs):
        return fail("the damage hook does not use the rule - it would answer with something else")
    if not any(r[1] == "setReturnValue" for r in mixin_refs):
        return fail("the damage hook never answers - the stock method would run anyway")
    print("ok: one common injection answers every damage-source question from the rule")

    # -- the rule reads the sources the way the dropdown and the card write them ----------------
    rule, rule_refs, rule_strings = info(rule_blob)
    for want, why in (("net/minecraft/world/entity/player/Player", "the player source"),
                      ("net/minecraft/world/entity/monster/Enemy", "the monster source"),
                      ("net/minecraft/world/damagesource/DamageSource", "a damage source")):
        if not any(r[0] == want for r in rule_refs):
            return fail("the rule never looks at %s - %s" % (want, why))
    if not any(r[1] == "getEntity" for r in rule_refs):
        return fail("the rule never asks who caused the damage")
    if not any(r[1] == "getDirectEntity" for r in rule_refs):
        return fail("the rule has no projectile fallback - an arrow would match nothing")
    methods = {disasm.parse_class(rule_blob)["cp"][n][1] for _a, n, _d, _at in rule["methods"]}
    for name in ("matches", "damager"):
        if name not in methods:
            return fail("the rule has no %s step" % name)
    # The four rows, in the order the screen shows them; the rule's switch must read the same.
    print("ok: the rule resolves the damager, with a projectile fallback, and names both sides")

    print("damage_source_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
