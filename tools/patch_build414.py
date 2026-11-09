#!/usr/bin/env python3
"""
Build 414 - fix the two runtime LinkageErrors in NpcDoors368 caught by the Build 413 latest.log.

The author's Minecraft 1.21.1 log from Build 413 showed the exact two crashes inside NpcDoors368:

  1) `java.lang.NoSuchFieldError: Class dev.settledlands.door.NpcDoors368$Open does not have member field
     'boolean walkedTo'` at `NpcDoors368.stopLeftBehind(NpcDoors368.java:554)` and `markWalkedTo(:529)`.
     Builds 407-413 added `boolean walkedTo` to `NpcDoors368$Open`, compiled `NpcDoors368$Open.class` into
     `out407..out413`, and then packaged only the outer `NpcDoors368.class`, leaving build 404's stale
     `NpcDoors368$Open.class` in the jar. On the very first door opened (`{62, 64, -237}` at 02:11:30.517),
     `stopLeftBehind` threw before removing the entry from `OPENED`, and every subsequent `track()` crashed
     in `markWalkedTo` before reaching `openOnPath` / `openAround` - so the door never closed behind the
     guide for 38 seconds, and once the player closed it at 02:12:08, the guide could never open it again.

  2) `java.lang.NoSuchMethodError: 'void net.minecraft.world.entity.ai.navigation.PathNavigation.
     setCanPassDoors(boolean)'` at `NpcDoors368.allowDoorNodes(NpcDoors368.java:347)`.
     In Minecraft 1.21.1, `setCanPassDoors(boolean)` / `canPassDoors()` live on `NodeEvaluator` right next
     to `setCanOpenDoors(boolean)` / `canOpenDoors()`, not on `PathNavigation`.

Along with packaging all three compiled `NpcDoors368*.class` files and calling `evaluator.setCanPassDoors`,
Build 414 keeps per-guide tick pacing (`LAST_TRACK` by UUID instead of one global `lastOwnPass` shared with
`onServerTick`), canonicalizes both halves of a two-block door (`baseDoorPos`) so `DoorGuard371` never shuts
the upper half of a locked door opened ahead on the guide's path, and opens only doors that are on the
guide's path (`onPath`, locked or unlocked) so a door closed behind the guide is not reopened behind its back.

Base: Build 413 (`settledlands-0.8.0-build413-run-from-guide-tick.jar`).
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build413-run-from-guide-tick"
NEW_VERSION = "0.8.0-build414-door-pass-linked"
OUT_NAME = "settledlands-0.8.0-build414-door-pass-linked.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/NpcDoors368.class",
    "dev/settledlands/door/NpcDoors368$Open.class",
    "dev/settledlands/door/NpcDoors368$Watch.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/NpcDoors368.class": (
        "stopLeftBehind", "keepOpen", "markWalkedTo", "baseDoorPos",
        "setCanPassDoors", "canPassDoors", "onTheWay", "inFrontOf", "beat", "LAST_TRACK",
    ),
    "dev/settledlands/door/NpcDoors368$Open.class": ("walkedTo",),
    "dev/settledlands/door/NpcDoors368$Watch.class": ("target", "age"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

CHANGELOG_EN = (
    "Build 414 fixes the two runtime linkage errors in NpcDoors368 caught by the Build 413 game log: "
    "packages the updated inner classes NpcDoors368$Open (with boolean walkedTo, fixing NoSuchFieldError in "
    "markWalkedTo and stopLeftBehind) and NpcDoors368$Watch, and calls setCanPassDoors(true) on NodeEvaluator "
    "instead of PathNavigation (fixing NoSuchMethodError in Minecraft 1.21.1). Also paces per guide by UUID, "
    "tracks both halves of a two-block door for the locked-door guard, and opens only doors on the guide's "
    "path (including locked ones) before closing them right behind the guide."
)
PATCHINFO = (
    "Сборка 414 устраняет две рантайм-ошибки сцепления классов в NpcDoors368, пойманные по вашему latest.log "
    "из 413-й сборки: упаковывает обновлённые внутренние классы NpcDoors368$Open (с полем walkedTo, из-за "
    "отсутствия которого падали закрытие двери за спиной и последующие открытия) и NpcDoors368$Watch, а также "
    "переносит вызов setCanPassDoors(true) с PathNavigation на NodeEvaluator (исправляя NoSuchMethodError в "
    "Minecraft 1.21.1). Кроме того, ведёт отсчёт тиков отдельно по каждому проводнику, учитывает обе половины "
    "двухблочной запертой двери для сторожа замков и открывает все двери строго на пути проводника (включая "
    "запертые), закрывая их сразу за его спиной.\n"
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
    srcroot = root / "out414"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to the base - nothing to replace" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    # Self-check: every compiled class in out414 must be packaged into the jar byte-for-byte,
    # so no inner/helper class can ever be left behind again.
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET),
                        ("assets/settledlands/lang/en_us.json", LANG_SET_EN)):
        if not table:
            continue
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, value in table.items():
            if key in strings and strings[key] == value:
                raise SystemExit("%s already has %s = %r" % (path, key, value))
            strings[key] = value
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, value))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    for path, table in LANG_CHANGE.items():
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, (before, after) in table.items():
            if strings.get(key) != before:
                raise SystemExit("%s: %s is %r, expected %r"
                                 % (path, key, strings.get(key), before))
            strings[key] = after
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, after))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name not in common:
            raise SystemExit("%s is not in the common list" % name)
        common.remove(name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
    for name in MIXINS_FIRST:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.insert(0, name)
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be a common mixin" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (first: %s)"
          % (len(common), len(client), common[0]))

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
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d entries)" % (out_path, len(order)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
