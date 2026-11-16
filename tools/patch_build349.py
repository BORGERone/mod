#!/usr/bin/env python3
"""
Build 349 - the armour pass stops being able to crash the client, and reads the game's model API right.

The 348 log (`latest-6.log`) has one cause, on the first rendered frame:

    java.lang.NoSuchMethodError: 'net.minecraft.client.model.EntityModel
        net.minecraft.client.model.geom.EntityModelSet.bakeLayer(ModelLayerLocation)'
        at dev.settledlands.model.BodyArmor348.bake(BodyArmor348.java:337)

`EntityModelSet#bakeLayer` does not return a model: it returns the layer's ROOT PART, and every renderer
wraps it - `new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR))`. That wrapping
constructor sat in the very evidence 348 used to confirm the call and was read past; the return type was
declared from memory, the stub agreed with the memory, and the gate had nothing to check it against. The
client died with `ReportedException: Rendering entity in world` on the first frame.

Two changes:

  * the call is what the game actually has - `bakeLayer` -> `ModelPart`, then
    `new HumanoidModel<>(root)`. The mirror, the confirmed-external table and `body_armor_check` now
    carry and assert that shape, not just the name;
  * the pass can no longer take the client down. Bake and draw are both guarded: the first failure
    switches the pass off for the session and writes one `warn` line with the failure's own text, so a
    wrong API call costs armour, not the game.

Base: 348.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build348-body-armor"
NEW_VERSION = "0.8.0-build349-armor-apifix"
OUT_NAME = "settledlands-0.8.0-build349-armor-apifix.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/model/BodyArmor348.class",
    "dev/settledlands/model/BodyArmor348$Rig.class",
    "dev/settledlands/npc/NpcArmor348.class",
    "dev/settledlands/mixin/BbModelArmor348Mixin.class",
    "dev/settledlands/mixin/NpcInventoryArmor348Mixin.class",
    "dev/settledlands/mixin/GuideNpcArmor348Mixin.class",
)
ADDED = ()

MIXINS = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/model/BodyArmor348.class": (
        "bakeLayer",
        "PLAYER_OUTER_ARMOR",
        "armour pass switched off"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 349 fixes the crash build 348 shipped. The armour pass asked the game for its armour model "
    "with EntityModelSet#bakeLayer and declared the answer as a model, but that method returns the "
    "layer's root part and the caller wraps it - the client died with NoSuchMethodError on the first "
    "rendered frame (latest-6.log: ReportedException: Rendering entity in world). The call now has the "
    "shape the game really has, and the pass itself can no longer take the client down: bake and draw "
    "are guarded, and the first failure switches armour off for the session with one warn line instead "
    "of a crash. Everything else from 348 stands: armour is drawn on the mod's own body for the player "
    "and every NPC, and an NPC wears the armour its own inventory holds."
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 349: \\u0438\\u0441\\u043f"
    "\\u0440\\u0430\\u0432\\u043b\\u0435\\u043d \\u043a\\u0440\\u0430\\u0448 "
    "348. \\u0411\\u0440\\u043e\\u043d\\u044f \\u0440\\u0438\\u0441\\u0443"
    "\\u0435\\u0442\\u0441\\u044f \\u043f\\u0440\\u0430\\u0432\\u0438\\u043b"
    "\\u044c\\u043d\\u043e; \\u0430\\u0440\\u043c\\u043e\\u0440-\\u043f\\u0430"
    "\\u0441\\u0441 \\u0431\\u043e\\u043b\\u044c\\u0448\\u0435 \\u043d\\u0435 "
    "\\u043c\\u043e\\u0436\\u0435\\u0442 \\u0443\\u0440\\u043e\\u043d\\u0438"
    "\\u0442\\u044c \\u043a\\u043b\\u0438\\u0435\\u043d\\u0442."
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out349"

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
    if MIXINS or CLIENT_MIXINS or MIXINS_REMOVED or CLIENT_REMOVED:
        blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                             + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (unchanged)" % (len(common), len(client)))

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
