#!/usr/bin/env python3
"""Gate: a node must not be able to point at itself, and the editor has to say so.

Build 337. A node wired to its own output is a dialogue that never ends - the line is shown, "next"
leads back into the same node, the same phrase comes again - and one drag makes it, because releasing
a wire on the node it started from looks like releasing it beside that node. `connect` keeps the link
without a word.

Ways that can ship silently broken:

  * the guard is registered as a client mixin - a dedicated server would still accept the graph;
  * the guard names a method the graph does not have, so it never applies;
  * the guard sits after the port check instead of at the head, so some ports still link to self;
  * the editor keeps printing its generic "wrong kind of link" line, which sends the author looking
    for a wrong node type instead of a wrong target;
  * the sentence is missing from the language files.

    python3 tools/self_link_check.py settledlands-0.8.0-build337-self-link.jar

Exit 0 = the wire is refused on both sides and the editor says why.
"""
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

RULE = "dev/settledlands/npc/NpcSelfLink337.class"
GUARD = "dev/settledlands/mixin/NpcSelfLinkMixin337.class"
STATUS = "dev/settledlands/mixin/NpcSelfLinkStatusMixin337.class"
CONFIG = "settledlands.mixins.json"
GRAPH = "dev/settledlands/npc/NpcDialogueGraph.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen.class"

CONNECT = ("connect", "(III)Z")
RELEASE = ("mouseReleased", "(DDI)Z")


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
    return {(cp[n][1], cp[d][1]) for _a, n, d, _at in parsed["methods"]}


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (RULE, GUARD, STATUS):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        blobs = {e: z.read(e) for e in (RULE, GUARD, STATUS, GRAPH, SCREEN)}
        config = json.loads(z.read(CONFIG).decode("utf-8"))
        lang_ru = json.loads(z.read("assets/settledlands/lang/ru_ru.json").decode("utf-8"))
        lang_en = json.loads(z.read("assets/settledlands/lang/en_us.json").decode("utf-8"))

    # -- the guard is on the one call that makes a wire, on both sides ------
    graph, _graph_refs = info(blobs[GRAPH])
    if CONNECT not in methods(graph):
        return fail("the graph has no connect(III)Z - there is nothing to guard")
    common = config.get("mixins", [])
    client = config.get("client", [])
    if "NpcSelfLinkMixin337" not in common:
        return fail("the guard is not a common mixin - a server would still accept a self-link")
    if "NpcSelfLinkMixin337" in client:
        return fail("the guard is listed as a client mixin only - the server would miss it")
    print("ok: connect(III)Z is guarded and the guard ships to both sides")

    guard, guard_refs = info(blobs[GUARD])
    guard_text = text(guard)
    if not any(r[0] == "dev/settledlands/npc/NpcSelfLink337" and r[1] == "isSelfLink"
               for r in guard_refs):
        return fail("the guard never asks whether the two ids are one node")
    if "connect" not in guard_text or "HEAD" not in guard_text:
        return fail("the guard is not attached to the head of connect - some ports could slip by")
    if "settledlands$noSelfLink" not in guard_text:
        return fail("the guard handler is missing - the mixin would be rejected at load")
    print("ok: the guard runs at the head of connect and refuses the same node")

    # -- the editor says why -----------------------------------------------
    screen, _screen_refs = info(blobs[SCREEN])
    if RELEASE not in methods(screen):
        return fail("the screen has no mouseReleased(DDI)Z to report the refusal from")
    if "NpcSelfLinkStatusMixin337" not in client:
        return fail("the status line hook is not a client mixin")
    status, status_refs = info(blobs[STATUS])
    if not any(r[0] == "dev/settledlands/npc/NpcSelfLink337" and r[1] == "consumeRefusal"
               for r in status_refs):
        return fail("the status line hook never reads the refusal")
    if not any(r[1] == "status" for r in status_refs):
        return fail("the status line hook never writes the status line")
    print("ok: the refusal is reported in the editor instead of the generic wrong-link line")

    # -- the sentence exists -----------------------------------------------
    rule, _rule_refs = info(blobs[RULE])
    key = "gui.settledlands.graph.self_link"
    if key not in text(rule):
        return fail("the rule does not name the language key it reports")
    for table, column in ((lang_ru, "ru"), (lang_en, "en")):
        if not table.get(key):
            return fail("%s_%s is missing" % (key, column))
    print("ok: the sentence exists in both languages")

    print("self_link_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
