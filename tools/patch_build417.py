#!/usr/bin/env python3
"""
Build 417 - «Незаконно» toggle on door and chest lockpicking cards with NPC stealth witness checks.

Features added on top of Build 416 (`settledlands-0.8.0-build416-hns-stealth-gaze.jar`):

  1) Inspector toggle «Незаконно: да / нет» on both the «Дверь» card (`DoorGraph382` /
     `NpcDoorRows382Mixin`) and the «Сундук» card (`ChestLock397` / `NpcChestRows396Mixin`), stored
     in `NpcDialogueGraph.Node#varLocal` (NBT `"VarLocal"`, unconditionally saved, loaded, and
     clipboard-copied for every node type).

  2) Witness evaluation via `NpcIllegalHack417`: only live SettledLands `GuideNpc` entities (excluding
     `GuideTriggerNpc` and `GuideCorpseNpc`) count as witnesses, gated by unobstructed line of sight
     (`hasLineOfSight`) plus Hackers 'n Slashers sight perception (`NpcHnsStealth416`, with directional
     cone + sneak-distance fallback when Hackers 'n Slashers is absent).

  3) Start refusal + mid-lockpick abort:
     - If an NPC already perceives the player at click time, lockpicking does not start (`HackPanel377`
       never opens — synced `:illegal` flag defers client-side door panel open until server check) and
       the player sees «За вами наблюдают».
     - If an NPC spots the player while `HackPanel377` is open, `GuideNpcStealth416Mixin` ->
       `NpcIllegalHack417#onNpcServerTick` immediately aborts the session, sends `DoorSync375` `X|` to
       close `HackPanel377` on the client, and displays «За вами наблюдают».
     - Closing `HackPanel377` unsolved sends `DoorHack377` `!cancel` to clear the server session cleanly,
       and `DoorHack377#handle` / `ChestNet397#solve` re-verify witnesses before unlocking.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build416-hns-stealth-gaze"
NEW_VERSION = "0.8.0-build417-illegal-lockpick-watch"
OUT_NAME = "settledlands-0.8.0-build417-illegal-lockpick-watch.jar"

REMOVED = ()
ADDED = (
    "dev/settledlands/npc/NpcIllegalHack417.class",
    "dev/settledlands/npc/NpcIllegalHack417$Session.class",
)
REPLACED = (
    "dev/settledlands/npc/NpcHnsStealth416.class",
    "dev/settledlands/mixin/GuideNpcStealth416Mixin.class",
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorHack377.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/chest/ChestLock397.class",
    "dev/settledlands/chest/ChestGraph396Events.class",
    "dev/settledlands/chest/ChestNet397.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
)
SAME_INNER = (
    "dev/settledlands/chest/ChestGraph396Events$Pending.class",
    "dev/settledlands/chest/ChestNet397$Panel.class",
    "dev/settledlands/chest/ChestNet397$Ready.class",
    "dev/settledlands/chest/ChestNet397$Solved.class",
)

LANG_SET = {
    "gui.settledlands.hack.illegal": "Незаконно",
    "gui.settledlands.hack.illegal_on": "Незаконно: да",
    "gui.settledlands.hack.illegal_off": "Незаконно: нет",
    "message.settledlands.hack.watched": "За вами наблюдают",
}
LANG_SET_EN = {
    "gui.settledlands.hack.illegal": "Illegal",
    "gui.settledlands.hack.illegal_on": "Illegal: yes",
    "gui.settledlands.hack.illegal_off": "Illegal: no",
    "message.settledlands.hack.watched": "You are being watched",
}

MIXIN_MARKERS = {
    "dev/settledlands/npc/NpcIllegalHack417.class": (
        "message.settledlands.hack.watched",
        "doesNpcWatchPlayer",
        "isPlayerWatched",
        "beginIllegalHack",
        "onNpcServerTick",
        "checkBeforeComplete",
    ),
    "dev/settledlands/mixin/GuideNpcStealth416Mixin.class": (
        "NpcIllegalHack417",
        "onNpcServerTick",
    ),
    "dev/settledlands/door/DoorGraph382.class": (
        "varLocal",
        "illegal",
        "setIllegal",
        "illegalAt",
    ),
    "dev/settledlands/door/DoorSync375.class": (
        "isIllegal",
        "sendOpenDoorHack",
        "sendAbortHack",
    ),
    "dev/settledlands/door/DoorEvents368.class": (
        "isIllegal",
        "beginIllegalHack",
        "sendOpenDoorHack",
    ),
    "dev/settledlands/door/DoorHack377.class": (
        "!cancel",
        "sendCancel",
        "checkBeforeComplete",
    ),
    "dev/settledlands/door/HackPanel377.class": (
        "abortFromServer",
        "sendCancel",
    ),
    "dev/settledlands/chest/ChestLock397.class": (
        "varLocal",
        "illegal",
        "setIllegal",
    ),
    "dev/settledlands/chest/ChestGraph396Events.class": (
        "beginIllegalHack",
    ),
    "dev/settledlands/chest/ChestNet397.class": (
        "checkBeforeComplete",
    ),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "settledlands$doorIllegal",
        "setIllegal",
    ),
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": (
        "settledlands$chestIllegal",
        "setIllegal",
    ),
}

CHANGELOG_EN = (
    "Build 417 adds an 'Illegal' ('Незаконно') toggle to both the Door and Chest lockpicking graph cards: "
    "when enabled, live SettledLands NPCs (excluding invisible triggers and corpses) act as witnesses via "
    "Hackers 'n Slashers stealth sight perception + line of sight. If an NPC already sees the player, "
    "lockpicking refuses to start without flashing the panel and shows 'За вами наблюдают'; if an NPC spots "
    "the player mid-lockpick, the lockpick panel immediately closes and shows 'За вами наблюдают'."
)
PATCHINFO = (
    "Сборка 417 добавляет тумблер «Незаконно» в настройки нод взлома «Дверь» и «Сундук»: если он включён, "
    "живые НПС мода (кроме триггеров и трупов) выступают свидетелями с учётом прямой видимости и скрытности "
    "Hackers 'n Slashers. Если НПС уже видит игрока при попытке взлома — панель взлома даже не открывается и "
    "выводится надпись «За вами наблюдают»; если НПС замечает игрока во время взлома — взлом мгновенно "
    "прерывается, панель закрывается и выводится «За вами наблюдают».\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = root / "out417"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("MIXIN_MARKERS names %s, which is not in ADDED or REPLACED" % name)

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

    for name in ADDED + REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MIXIN_MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name in ADDED:
            if name in blobs:
                raise SystemExit("%s already in base jar" % name)
            order.append(name)
            print("  added   : %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to base" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    # Self-check: every compiled class in out417 must match the jar byte-for-byte
    # (either packaged via ADDED/REPLACED or verified identical in SAME_INNER).
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel in SAME_INNER:
            if rel not in blobs or blobs[rel] != compiled.read_bytes():
                raise SystemExit("inner class %s changed unexpectedly; move to REPLACED!" % rel)
            continue
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings and strings[key] == value:
                raise SystemExit("%s already has %s = %r" % (path, key, value))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    stale = 'displayName="Settled Lands %s"' % BASE_VERSION
    if stale not in toml:
        raise SystemExit("base toml has no displayName to bump")
    toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, displayName, description prefix" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
