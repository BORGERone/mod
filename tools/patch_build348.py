#!/usr/bin/env python3
"""
Build 348 - the armour an entity wears is drawn on the body it wears it on.

The mod draws its own body model instead of the vanilla player model. Nothing in that path ever asked
the game for armour, so a chestplate in the chest slot changed nothing on screen: for the player body
and for every NPC the plate, helmet, leggings and boots simply did not exist.

Two things follow from that one cause:

  * the client draws them. `PLAYER_OUTER_ARMOR` / `PLAYER_INNER_ARMOR` are baked once and the body's
    own bone rotations are copied onto them right after the root part is drawn - the pose stack at
    that instant is the game's own entity model space, so the plate cannot drift off the body;
  * an NPC wears what it carries. Its equipment slots are filled from its own 27-slot inventory
    (never moved out of it), and the game's own equipment sync carries that to every client.

Base: 347.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build347-common-damage"
NEW_VERSION = "0.8.0-build348-body-armor"
OUT_NAME = "settledlands-0.8.0-build348-body-armor.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/model/BodyArmor348.class",
    "dev/settledlands/model/BodyArmor348$Rig.class",
    "dev/settledlands/npc/NpcArmor348.class",
    "dev/settledlands/mixin/BbModelArmor348Mixin.class",
    "dev/settledlands/mixin/NpcInventoryArmor348Mixin.class",
    "dev/settledlands/mixin/GuideNpcArmor348Mixin.class",
)

MIXINS = ("NpcInventoryArmor348Mixin", "GuideNpcArmor348Mixin")
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("BbModelArmor348Mixin",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/BbModelArmor348Mixin.class": (
        "settledlands$renderActorOriginal",
        "settledlands$renderNpcOriginal",
        "BodyArmor348"),
    "dev/settledlands/mixin/NpcInventoryArmor348Mixin.class": (
        "setChanged",
        "NpcArmor348"),
    "dev/settledlands/mixin/GuideNpcArmor348Mixin.class": (
        "readAdditionalSaveData",
        "NpcArmor348"),
    "dev/settledlands/model/BodyArmor348.class": (
        "armorCutoutNoCull",
        "PLAYER_OUTER_ARMOR",
        "PLAYER_INNER_ARMOR"),
    "dev/settledlands/npc/NpcArmor348.class": (
        "setItemSlot",
        "getItemBySlot"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 348 puts armour back on the body. The mod draws its own model instead of the vanilla "
    "player model, so the game never had a reason to draw a chestplate - worn armour now goes onto "
    "that body: the two vanilla armour models are baked once and take the body's own bone rotations, "
    "drawn right after the root part where the pose stack is already the game's entity model space. "
    "The same pass covers the player body and every NPC. NPCs also wear what they carry now: their "
    "four equipment slots follow their own inventory (nothing is moved out of it), so a plate put in "
    "an NPC's inventory shows on its body and reaches the client through the game's own equipment "
    "sync."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 348: \\u0431\\u0440\\u043e\\u043d\\u044f "
    "\\u0432\\u0438\\u0434\\u043d\\u0430 \\u043d\\u0430 \\u0442\\u0435\\u043b\\u0435 - "
    "\\u0438 \\u0443 \\u0438\\u0433\\u0440\\u043e\\u043a\\u0430, \\u0438 \\u0443 \\u043a"
    "\\u0430\\u0436\\u0434\\u043e\\u0433\\u043e NPC. \\u041d\\u0430\\u0434\\u0435\\u0442"
    "\\u0430\\u044f \\u0431\\u0440\\u043e\\u043d\\u044f \\u0440\\u0438\\u0441\\u0443\\u0435"
    "\\u0442\\u0441\\u044f \\u043f\\u043e\\u0432\\u0435\\u0440\\u0445 \\u0441\\u043e\\u0431"
    "\\u0441\\u0442\\u0432\\u0435\\u043d\\u043d\\u043e\\u0439 \\u043c\\u043e\\u0434\\u0435"
    "\\u043b\\u0438 \\u0442\\u0435\\u043b\\u0430; NPC \\u043d\\u0430\\u0434\\u0435\\u0432"
    "\\u0430\\u044e\\u0442 \\u0442\\u043e, \\u0447\\u0442\\u043e \\u043b\\u0435\\u0436\\u0438"
    "\\u0442 \\u0443 \\u043d\\u0438\\u0445 \\u0432 \\u0438\\u043d\\u0432\\u0435\\u043d\\u0442"
    "\\u0430\\u0440\\u0435."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out348"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("base jar has no entry to remove: %s" % name)
        order.remove(name)
        del blobs[name]
        print("  removed: %s" % name)

    for name in REPLACED + ADDED:
        src = srcdir / name
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
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

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

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name not in common:
            raise SystemExit("%s is not in the common list" % name)
        common.remove(name)
        print("  mixins.json: common -%s" % name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
        print("  mixins.json: client -%s" % name)
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
    print("  mixins.json: %d common, %d client (+%s / +%s)"
          % (len(common), len(client), ", ".join(MIXINS), ", ".join(CLIENT_MIXINS)))

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
