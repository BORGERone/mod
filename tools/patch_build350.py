#!/usr/bin/env python3
"""
Build 350 - the body-armour work of 348/349 is withdrawn at the author's request.

348 and 349 only ever added six classes and three mixin list entries on top of 347, so withdrawing the
feature means shipping 347's composition again - not a half-removed armour pass. The entry list of this
jar is byte-for-byte 347 (512 entries, 65 mixins: 31 common / 32 client); the only differences are the
version string, the display name and the changelog line in the toml, and the patch-info file.

    diff of entry names, 349 vs 347:  +6 added by 348 (Body + NpcArmor348 + 3 mixins), 0 removed
    diff of entry names, 350 vs 347:  must be empty - the patcher asserts it

Base: 347.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build347-common-damage"
NEW_VERSION = "0.8.0-build350-armor-off"
OUT_NAME = "settledlands-0.8.0-build350-armor-off.jar"

# Nothing of the armour build may survive, and nothing else may change either: both lists stay empty.
REMOVED = (
    "dev/settledlands/model/BodyArmor348.class",
    "dev/settledlands/model/BodyArmor348$Rig.class",
    "dev/settledlands/npc/NpcArmor348.class",
    "dev/settledlands/mixin/BbModelArmor348Mixin.class",
    "dev/settledlands/mixin/NpcInventoryArmor348Mixin.class",
    "dev/settledlands/mixin/GuideNpcArmor348Mixin.class",
)
REPLACED = ()
ADDED = ()

MIXINS = ()
MIXINS_REMOVED = ("BbModelArmor348Mixin", "NpcInventoryArmor348Mixin", "GuideNpcArmor348Mixin")
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 350 withdraws the body-armour work of builds 348 and 349 - the author does not want it. The "
    "jar is build 347's composition: 512 entries, the same 65 mixins, no armour classes and no armour "
    "mixins. Nothing else changed, so whatever 347 did - including a damage node firing from the NPC's "
    "own tab and from the shared \"Общее\" graph - still holds here."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 350: \\u0431\\u0440\\u043e\\u043d\\u044f "
    "\\u043d\\u0430 \\u0442\\u0435\\u043b\\u0435 \\u043e\\u0442\\u043c\\u0435\\u043d\\u0435"
    "\\u043d\\u0430 \\u043f\\u043e \\u043f\\u0440\\u043e\\u0441\\u044c\\u0431\\u0435 "
    "\\u0430\\u0432\\u0442\\u043e\\u0440\\u0430. \\u0421\\u043e\\u0441\\u0442\\u0430\\u0432 "
    "\\u2014 \\u043a\\u0430\\u043a \\u0432 347."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REMOVED:
        # The base is 347, which never had these; if one is present the base is wrong and we stop.
        if name in blobs:
            raise SystemExit("base jar unexpectedly contains %s - wrong base?" % name)
    for name in REPLACED + ADDED:
        raise SystemExit("this python has no class changes, %s does not belong here" % name)

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name in common or name in client:
            raise SystemExit("base jar still lists %s - wrong base?" % name)
    print("  mixins.json: %d common, %d client (no armour mixins)" % (len(common), len(client)))

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

    # The composition must be exactly the base's - same entries, same count. Only the toml, the patch-info
    # and nothing else may differ in content.
    with zipfile.ZipFile(args.base) as z:
        base_names = set(z.namelist())
    with zipfile.ZipFile(out_path) as z:
        out_names = set(z.namelist())
    if base_names != out_names:
        raise SystemExit("entry list differs from the base: %s"
                         % sorted(base_names ^ out_names))
    print("  entry list identical to the base (%d entries)" % len(out_names))

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d entries)" % (out_path, len(order)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
