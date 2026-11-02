#!/usr/bin/env python3
"""Gate: the damage trace of build 346 is wired and bounded.

Build 346 exists to answer one question with facts instead of guesses: a damage node does not fire,
the hit does reach GuideNpc.hurt (proved by the log of the build 345 session), so the node is lost
inside the chain - the graph the chain walks, the node in it, or one of its gates.

Ways that can ship broken:

  * a trace hook is missing from the common mixin list, so the run says nothing at all and the user
    has to test again for nothing;
  * a hook is bound to a method that no longer exists - with require=1 the game then does not start;
  * the hook is listed as a client mixin, and the server side of the chain is never traced;
  * the trace is unbounded, so a fight writes megabytes into latest.log.

    python3 tools/damage_trace_check.py settledlands-0.8.0-build346-damage-trace.jar

Exit 0 = both hooks are common, bound to the right methods, and the writer is capped.
"""
import json
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

TRACE = "dev/settledlands/npc/NpcDamageTrace346.class"
HURT = "dev/settledlands/mixin/NpcDamageTraceHurtMixin346.class"
CHAIN = "dev/settledlands/mixin/NpcDamageTraceChainMixin346.class"
CONFIG = "settledlands.mixins.json"

HURT_METHOD = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
CHAIN_METHOD = ("handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;"
                "Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;"
                "Ldev/settledlands/npc/NpcDialogueGraph;)V")


def fail(message):
    print("FAIL: " + message)
    return 1


def strings(data):
    """Every constant-pool string of the class, decoded the way the JVM reads it."""
    pool = disasm.parse_class(data)["cp"]
    return [str(value[1]) for value in pool if value and value[0] == 1]


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = argv[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for entry in (TRACE, HURT, CHAIN):
            if entry not in names:
                return fail("%s is not in the jar" % entry)
        trace = z.read(TRACE)
        hurt = strings(z.read(HURT))
        chain = strings(z.read(CHAIN))
        config = json.loads(z.read(CONFIG).decode("utf-8"))

    common = config.get("mixins", [])
    client = config.get("client", [])
    for name in ("NpcDamageTraceHurtMixin346", "NpcDamageTraceChainMixin346"):
        if name not in common:
            return fail("%s is not a common mixin - the damage chain runs on the server" % name)
        if name in client:
            return fail("%s is listed as a client mixin" % name)
    if len(common) != len(set(common)):
        return fail("the common mixin list has a duplicate - Mixin refuses that at startup")
    print("ok: both trace hooks are common, and no name is listed twice")

    if HURT_METHOD not in hurt or "settledlands$traceHurt" not in hurt:
        return fail("the hurt hook is not bound to %s" % HURT_METHOD)
    if CHAIN_METHOD not in chain or "settledlands$traceChain" not in chain:
        return fail("the chain hook is not bound to %s" % CHAIN_METHOD)
    for blob, name in ((hurt, "hurt"), (chain, "chain")):
        if "HEAD" not in blob:
            return fail("the %s hook does not sit at HEAD - it would miss the call" % name)
        if "cancellable" in blob:
            return fail("the %s hook can cancel - a trace must never change behaviour" % name)
    print("ok: both hooks sit at HEAD of the real methods and neither can cancel")

    text = " ".join(strings(trace))
    for needle, why in (("[dmg] {}", "the line prefix"),
                        ("hit npc='", "the hit line"),
                        ("chain npc='", "the graph line"),
                        ("MATCH", "the verdict"),
                        ("skip: amount", "the minimum-damage gate"),
                        ("skip: filter", "the source gate"),
                        ("skip: cooldown", "the cooldown gate"),
                        ("SettledLands/DamageTrace346", "its own logger name")):
        if needle not in text:
            return fail("the trace has no %s (%s)" % (why, needle))
    if "enabled=" not in text or "entry=" not in text or "damage=" not in text:
        return fail("the trace does not say whether the graph is enabled, its entry, or its damage nodes")
    limit = re.search(r"LIMIT", text)
    if not limit:
        return fail("the trace has no limit marker")
    # The cap is a constant field; a jar without one would flood latest.log.
    with zipfile.ZipFile(jar) as z:
        raw = z.read(TRACE)
    if b"trace limit reached" not in raw:
        return fail("the trace never announces that it stopped - a silent cap is a lie")
    print("ok: the hit, the graph, every gate and its own stop line are all there")

    print("damage_trace_check: PASS  (%s)" % pathlib.Path(jar).name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
