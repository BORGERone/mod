#!/usr/bin/env python3
"""Gate: one event type, one `getEntity()` - the mod may not guess a return type the game does not have.

The bug this gate exists for, build 401: the compile-time mirror of `PlayerInteractEvent` declared
`getEntity()` returning `Entity`, so every click handler compiled against it asked the game for

    PlayerInteractEvent$RightClickBlock.getEntity()Lnet/minecraft/world/entity/Entity;

The game's own method is inherited from `PlayerEvent`, which narrows `EntityEvent`'s answer to `Player`, and
its descriptor is the only one that exists:

    PlayerInteractEvent$RightClickBlock.getEntity()Lnet/minecraft/world/entity/player/Player;

An `invokevirtual` with a descriptor nobody declares does not fail at compile time and does not fail at load
time - it fails on the first click, as a `NoSuchMethodError` inside the handler's own `try`, where it turns a
click handler into a handler that stops halfway. That is invisible in a log by default and invisible in game
except as "nothing happened", which is exactly how the author reported the door's «открытие» socket.

The rule is not a table of signatures (a table can be wrong the same way the mirror was). It is coherence:
a mod cannot call the same getter of the same event class through two different descriptors, because the game
declares exactly one. The base mod's own classes are compiled against the real thing and take part in the
count, so a mirror that disagrees with them cannot pass - the majority is never the argument, the agreement is.

Usage:

    python3 tools/event_getter_check.py settledlands-0.8.0-build401-chain-first.jar

Exit 0 = every `net/neoforged/**/event/**` getter the jar calls through one descriptor only.
"""
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import disasm

# The getters a click or tick handler reaches for; all of them are inherited-narrowed in the game, which is
# what makes them the ones a mirror gets wrong.
GETTERS = ("getEntity", "getLevel", "getPos", "getItemStack", "getFace", "getHand", "getSide")


def fail(message):
    print("event_getter_check: FAIL  " + message)
    return 1


def main(argv):
    if len(argv) != 1:
        print(__doc__)
        return 2
    jar = pathlib.Path(argv[0])
    if not jar.is_file():
        return fail("no such jar: %s" % jar)

    seen = {}     # (owner, name) -> {descriptor: [classes]}
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            if not name.endswith(".class"):
                continue
            try:
                parsed = disasm.parse_class(zf.read(name))
            except Exception:
                continue
            cp = parsed["cp"]
            for value in cp:
                if not value or value[0] not in (10, 11):
                    continue
                ci, nti = struct.unpack(">HH", value[1])
                try:
                    owner = disasm.cls_name(cp, ci)
                    method, desc = disasm.nat(cp, nti)
                except Exception:
                    continue
                if "event" not in owner or method not in GETTERS:
                    continue
                if owner.startswith("net/neoforged/neoforge/event/") is False:
                    continue
                seen.setdefault((owner, method), {}).setdefault(desc, []).append(name)

    problems = []
    for (owner, method), descs in sorted(seen.items()):
        if len(descs) < 2:
            continue
        detail = "; ".join("%s <- %s" % (desc, ", ".join(sorted(set(where))[:4]))
                           for desc, where in sorted(descs.items()))
        problems.append("%s#%s is called through %d descriptors: %s"
                        % (owner, method, len(descs), detail))

    if problems:
        print("event_getter_check: FAIL  %s" % jar.name)
        for line in problems:
            print("  - " + line)
        print("  one of these descriptors does not exist in the game, and the call that uses it throws"
              " NoSuchMethodError inside its own try - a handler that stops halfway.")
        return 1

    refs = sum(len(w) for descs in seen.values() for w in descs.values())
    print("event_getter_check: OK  %s  (%d NeoForge event getter reference(s) over %d owner/method pair(s),"
          " each through exactly one descriptor - no mirror of this mod disagrees with the base mod or with"
          " itself about a return type)" % (jar.name, refs, len(seen)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
