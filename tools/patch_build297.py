#!/usr/bin/env python3
"""Build 297 - the condition node checks its own variable again.

Reported: 295 broke the condition node. What the node has to do was stated plainly - it
checks only the value of the variable written into it, with its own operator and number.
The "В радиусе" node does two things: it sends the impulse into the IF, and it passes the
player it found. It does NOT decide what the IF compares.

Builds 290/294/295 did the opposite: whenever the IF had a blue wire, the hook threw the
node's own variable away and compared "body present = 1, absent = 0" instead. So the node
never read the variable it was configured with. That substitution is what broke.

Fix: the IF hook is removed. The node reads its variable with the operator and number it
was given, and the player is the one the chain carries - which is the player the check
node found, because `triggerCheckChain` starts the walk with that body. No new code runs
for the IF node; the stock path is back.

Usage: python3 tools/patch_build297.py <base-296.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build296-revert-295"
NEW_VERSION = "0.8.0-build297-if-own-variable"
OUT_NAME = "settledlands-0.8.0-build297-if-own-variable.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/NpcValueSource290.class",
    "dev/settledlands/mixin/GuideDialogueValue290.class",
)
ADDED = ()
COMMON_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/mixin/GuideDialogueValue290.class": ("settledlands$gateTrigger",
                                                           "settledlands$lineText",
                                                           "settledlands$varFromValue"),
    "dev/settledlands/npc/NpcValueSource290.class": ("passThroughWhenValue", "triggerBlocked",
                                                    "variablePresence"),
}
GONE_METHODS = {
    "dev/settledlands/mixin/GuideDialogueValue290.class": ("settledlands$ifFromValue",),
    "dev/settledlands/npc/NpcValueSource290.class": ("ifValue",),
}

CHANGELOG_EN = (
    "Build 297 gives the condition node its own check back. The IF node checks the value of "
    "the variable written into it, with its own operator and number - as before 290. The "
    "check node (\u00abВ радиусе\u00bb) sends the impulse and passes the player it found; it no "
    "longer decides what the condition compares. Builds 290-295 replaced the whole condition "
    "with \u00abbody present = 1, absent = 0\u00bb whenever the node had a blue wire, so the variable "
    "written into the node was never read; that substitution is removed. Nothing else is "
    "changed: the value wires, the \u00abУрон\u00bb node, the variable node, the line and trigger "
    "hooks stay as they were in 296. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 297 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0430\u0435\u0442 \u043d\u043e\u0434\u0435 \u0443\u0441\u043b\u043e\u0432\u0438\u044f \u0435\u0451 \u0441\u043e\u0431\u0441\u0442\u0432\u0435\u043d\u043d\u0443\u044e "
    "\u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443: \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 \u0432\u044b\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u043d\u043e\u0439 \u0432 \u043d\u0435\u0451 \u043f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u043e\u0439, \u0441\u0432\u043e\u0438 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440 \u0438 "
    "\u0447\u0438\u0441\u043b\u043e. \u041d\u043e\u0434\u0430 \u00ab\u0412 \u0440\u0430\u0434\u0438\u0443\u0441\u0435\u00bb \u0434\u0430\u0451\u0442 \u0438\u043c\u043f\u0443\u043b\u044c\u0441 \u0438 \u043f\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u043d\u0430\u0439\u0434\u0435\u043d\u043d\u043e\u0433\u043e \u0438\u0433\u0440\u043e\u043a\u0430, "
    "\u043d\u043e \u043d\u0435 \u0440\u0435\u0448\u0430\u0435\u0442, \u0447\u0442\u043e \u0441\u0440\u0430\u0432\u043d\u0438\u0432\u0430\u0442\u044c. \u0412 290-295 \u0432\u0441\u0451 \u0443\u0441\u043b\u043e\u0432\u0438\u0435 \u043f\u043e\u0434\u043c\u0435\u043d\u044f\u043b\u043e\u0441\u044c \u043d\u0430 "
    "\u00ab\u0442\u0435\u043b\u043e \u0435\u0441\u0442\u044c = 1, \u0442\u0435\u043b\u0430 \u043d\u0435\u0442 = 0\u00bb, \u043f\u043e\u044d\u0442\u043e\u043c\u0443 \u043f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f \u043d\u043e\u0434\u044b \u043d\u0435 \u0447\u0438\u0442\u0430\u043b\u0430\u0441\u044c. "
    "\u041f\u043e\u0434\u043c\u0435\u043d\u0430 \u0443\u0431\u0440\u0430\u043d\u0430. \u041e\u0441\u0442\u0430\u043b\u044c\u043d\u043e\u0435 (\u043f\u0440\u043e\u0432\u043e\u0434\u0430 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0439, \u043d\u043e\u0434\u0430 \u00ab\u0423\u0440\u043e\u043d\u00bb, "
    "\u043f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f, \u0440\u0435\u043f\u043b\u0438\u043a\u0430, \u0442\u0440\u0438\u0433\u0433\u0435\u0440) \u043a\u0430\u043a \u0432 296. "
)


def check_methods(path, names, gone=()):
    cf = classfile.parse(path.read_bytes(), path.name)
    have = {m.name for m in cf.methods}
    for name in names:
        if name not in have:
            raise SystemExit("%s has no method %s" % (path, name))
    for name in gone:
        if name in have:
            raise SystemExit("%s still has %s - the 297 removal did not compile in"
                             % (path, name))
    return sorted(have)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build297-src/out")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    classes = pathlib.Path(args.classes)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name], GONE_METHODS.get(name, ()))
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name], GONE_METHODS.get(name, ()))
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = list(mixins.get("mixins", []))
    for mixin in COMMON_MIXINS:
        if mixin not in common:
            common.append(mixin)
    mixins["mixins"] = common
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(mixins.get("client", []))))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    for stale in ('displayName="Settled Lands"',
                  'displayName="Settled Lands %s"' % BASE_VERSION):
        if stale in toml:
            toml = toml.replace(stale, 'displayName="Settled Lands %s"' % NEW_VERSION)
            break
    else:
        raise SystemExit("base toml has no displayName to bump")
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN.replace("\\u00ab", "\u00ab")
                        .replace("\\u00bb", "\u00bb") + "\n\n", 1)
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
