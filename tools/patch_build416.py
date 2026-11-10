#!/usr/bin/env python3
"""
Build 416 - Hackers 'n Slashers stealth compatibility for NPC head gaze and Stealth'o Meter.

Root causes fixed on top of Build 415 (`settledlands-0.8.0-build415-door-at-hand-and-2block-height.jar`):

  1) `GuideNpc#customServerAiStep()` picked `level().getNearestPlayer(this, 12.0D)` in 360 degrees
     through walls and regardless of crouch/darkness/invisibility, then rotated `npc.yHeadRot` directly
     toward the player every tick via `NpcAutoAiEngine255#lookHeadAtPlayer`. That bypassed Hackers 'n
     Slashers' `LookControlMixin` and immediately turned `yHeadRot` onto a sneaking player behind the
     NPC, putting the player inside `StealthHelper.isOnFov(npc, player)` and spiking `sightLevel` (and
     the player's HUD Stealth'o Meter eye) to 100%.

  2) `NpcAutoAiEngine255#tickIdleLookAround(GuideNpc)` checked `level().getNearestPlayer(npc, 12.0D)`
     without stealth/line-of-sight gating, freezing idle head/body look whenever a hidden player was
     within 12 blocks.

  3) `GuideTriggerNpc` (invisible trigger marker) and `GuideCorpseNpc` (player corpse/coffin) extend
     `GuideNpc -> PathfinderMob -> Mob`, so Hackers 'n Slashers treated them as sighted mobs and opened
     the player's stealth eye near invisible triggers and corpses.

Scope agreed with the author:
  - Only gate automatic NPC head-turning and Stealth'o Meter perception on non-sighted entities.
  - Leave the «В радиусе» (`CHECK`) node unchanged.
  - Do not cancel an already-running «Преследование» (`NpcPursuit269`) if the player later hides.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build415-door-at-hand-and-2block-height"
NEW_VERSION = "0.8.0-build416-hns-stealth-gaze"
OUT_NAME = "settledlands-0.8.0-build416-hns-stealth-gaze.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcHnsStealth416.class",
    "dev/settledlands/mixin/GuideNpcStealth416Mixin.class",
    "dev/settledlands/mixin/NpcAutoAiStealth416Mixin.class",
    "dev/settledlands/mixin/GuideCorpseStealth416Mixin.class",
)
DATA_ADDED = (
    ("settledlands/mob_behavior_override/guide_trigger.json",
     "data/settledlands/mob_behavior_override/guide_trigger.json"),
    ("settledlands/mob_behavior_override/guide_corpse.json",
     "data/settledlands/mob_behavior_override/guide_corpse.json"),
)
MIXINS = (
    "GuideNpcStealth416Mixin",
    "NpcAutoAiStealth416Mixin",
    "GuideCorpseStealth416Mixin",
)

MIXIN_MARKERS = {
    "dev/settledlands/npc/NpcHnsStealth416.class": (
        "net.dndats.hackersandslashers.utils.helper.EntityHelper",
        "getSightLevel",
        "hasMobTrueVision",
        "enable_stealth_system",
        "canLookAtPlayer",
        "pickVisibleLookPlayer",
        "lookHeadAtVisiblePlayer",
        "clearBlindEntitySight",
    ),
    "dev/settledlands/mixin/GuideNpcStealth416Mixin.class": (
        "customServerAiStep",
        "NpcHnsStealth416",
        "pickVisibleLookPlayer",
        "lookHeadAtVisiblePlayer",
    ),
    "dev/settledlands/mixin/NpcAutoAiStealth416Mixin.class": (
        "tickIdleLookAround",
        "NpcHnsStealth416",
        "pickVisibleLookPlayer",
    ),
    "dev/settledlands/mixin/GuideCorpseStealth416Mixin.class": (
        "tick",
        "NpcHnsStealth416",
        "clearBlindEntitySight",
    ),
}

CHANGELOG_EN = (
    "Build 416 integrates SettledLands NPC head-tracking with Hackers 'n Slashers stealth: NPCs no longer "
    "rotate yHeadRot 360 degrees onto sneaking, invisible, or occluded players behind walls or behind their "
    "backs before spotting them (gating non-dialogue head-tracking by line of sight and Hackers 'n Slashers "
    "sightLevel thresholds while keeping smooth tracking once spotted and during active dialogue), idle look "
    "is no longer frozen by hidden nearby players, and invisible GuideTriggerNpc markers and GuideCorpseNpc "
    "corpses/coffins no longer open the player's Stealth'o Meter eye. The CHECK ('In radius') node and "
    "ongoing Pursuit ('Преследование') remain unchanged as requested."
)
PATCHINFO = (
    "Сборка 416 связывает автоматический поворот головы НПС с системой скрытности Hackers 'n Slashers: "
    "НПС больше не поворачивают голову на 360 градусов и сквозь стены на крадущегося или спрятавшегося "
    "игрока (вне активного диалога поворот головы срабатывает только при прямой видимости и когда глазик "
    "скрытности Hackers 'n Slashers обнаружил игрока), скрытый рядом игрок больше не замораживает "
    "праздное осматривание НПС, а невидимые маркеры-триггеры (GuideTriggerNpc) и трупы/гробы "
    "(GuideCorpseNpc) больше не открывают глазик скрытности игрока. Нода «В радиусе» и уже запущенное "
    "«Преследование» оставлены без изменений.\n"
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
    srcroot = root / "out416"
    dataroot = root / "data416"

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

    # Self-check: every compiled class in out416 (including any inner classes) must match the jar byte-for-byte.
    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s does not match the jar!" % rel)

    for rel_src, jar_dest in DATA_ADDED:
        dpath = dataroot / rel_src
        if not dpath.is_file():
            raise SystemExit("missing data json: %s" % dpath)
        raw = dpath.read_bytes()
        json.loads(raw.decode("utf-8"))
        if jar_dest in blobs:
            raise SystemExit("base jar already has data entry: %s" % jar_dest)
        order.append(jar_dest)
        blobs[jar_dest] = raw
        print("  added data: %s (%d bytes)" % (jar_dest, len(raw)))

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for m in MIXINS:
        if m not in common:
            common.append(m)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (added: %s)"
          % (len(common), len(client), ", ".join(MIXINS)))

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
