#!/usr/bin/env python3
"""Build 298 - the blue wire no longer rewrites the condition card.

Reported: connecting the blue output of "В радиусе" to an "Условие" node changed the
condition on the card from "кукт == 0" to "#4 == 0", and the graph then behaved as if the
condition compared node 4 with zero. What the wire has to do is hand over the player the
check node found - nothing else. The condition keeps its own variable, operator and number.

What the code actually does (verified in bytecode):

  * server side is already right after 297 - both evalIfNode and the IF branch of
    resolveExecutionTarget only take the variable name from the linked node when that node is
    a "Переменная" (type 4); for a check/damage source the node's own variable is read, for
    the player the chain carries, which is the body the check node found;
  * the card is not: NpcDialogueGraphScreen#drawNode prints, for an IF node with
    varInputNode >= 0, the source's own variable name, and when the source has none it falls
    back to "#<id>" - hence "#4 == 0";
  * build 290 hid that link only for the cable pass (wires), so the card pass that runs
    after it already had the link back and printed "#4".

Fix: the link is taken out of varInputNode for the whole frame (render HEAD) and put back at
render TAIL, so the card draws the variable written into the condition and the stock cable
pass draws no cable into the input field; the blue pass reads the source from the saved
list instead and still draws its own cable. The stored link is untouched - it is back before
the frame ends. Nothing on the server changes.

Usage: python3 tools/patch_build298.py <base-297.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build297-if-own-variable"
NEW_VERSION = "0.8.0-build298-if-card-keeps-variable"
OUT_NAME = "settledlands-0.8.0-build298-if-card-keeps-variable.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
)
ADDED = ()
COMMON_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("stockInputSource",
                                                              "beginStockInputHide",
                                                              "hideStockInputWires",
                                                              "restoreStockInputWires"),
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class": (
        "settledlands$hideStockIfWiresForFrame",
        "settledlands$restoreStockIfWiresAfterFrame",
        "settledlands$hideStockIfWires",
        "settledlands$drawValueWires"),
}

CHANGELOG_EN = (
    "Build 298 stops the blue wire from rewriting the condition card. Connecting a check "
    "node (\u00abВ радиусе\u00bb) or a damage node to an \u00abУсловие\u00bb node no longer turns its "
    "condition into \u00ab#4 == 0\u00bb: the wire only hands over the player the check node found, "
    "and the condition keeps its own variable, operator and number. The card was the culprit "
    "- it prints the source node's variable name when one is linked, and \u00ab#<id>\u00bb when the "
    "source has none; the link is now hidden for the whole frame, so the card shows the "
    "condition as written, while the blue cable is still drawn from the source pin. The "
    "server already read the node's own variable for the player the chain carries. Nothing "
    "else changed. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 298: \u0441\u0438\u043d\u0438\u0439 \u043f\u0440\u043e\u0432\u043e\u0434 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u043f\u0435\u0440\u0435\u043f\u0438\u0441\u044b\u0432\u0430\u0435\u0442 \u0443\u0441\u043b\u043e\u0432\u0438\u0435. "
    "\u041f\u043e\u0441\u043b\u0435 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f \u043d\u043e\u0434\u044b \u00ab\u0412 \u0440\u0430\u0434\u0438\u0443\u0441\u0435\u00bb \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0430 \u0443\u0441\u043b\u043e\u0432\u0438\u044f \u043f\u043e-\u043f\u0440\u0435\u0436\u043d\u0435\u043c\u0443 "
    "\u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0441\u0432\u043e\u044e \u043f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0443\u044e, \u0441\u0432\u043e\u0439 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440 \u0438 \u0441\u0432\u043e\u0451 \u0447\u0438\u0441\u043b\u043e \u0432\u043c\u0435\u0441\u0442\u043e "
    "\u00ab#4 == 0\u00bb. \u041f\u0440\u043e\u0432\u043e\u0434 \u043f\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a\u0430, \u043a\u043e\u0442\u043e\u0440\u043e\u0433\u043e \u043d\u0430\u0448\u043b\u0430 \u043d\u043e\u0434\u0430 \u0440\u0430\u0434\u0438\u0443\u0441\u0430. "
    "\u0421\u0435\u0440\u0432\u0435\u0440\u043d\u0430\u044f \u0447\u0430\u0441\u0442\u044c \u043d\u0435 \u043c\u0435\u043d\u044f\u043b\u0430\u0441\u044c. "
)


def check_methods(path, names):
    cf = classfile.parse(path.read_bytes(), path.name)
    have = {m.name for m in cf.methods}
    for name in names:
        if name not in have:
            raise SystemExit("%s has no method %s" % (path, name))
    return sorted(have)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build298-src/out")
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
        check_methods(src, EXPECTED_METHODS[name])
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        check_methods(src, EXPECTED_METHODS[name])
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
