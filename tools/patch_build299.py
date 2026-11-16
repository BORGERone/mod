#!/usr/bin/env python3
"""Build 299 - the card fix without the frame-wide state change.

Reported on 298: the "Условие" card still printed "#4 == 0" after the blue wire was
connected, and the blue wire started to lag.

Why 298 missed: it took the body link out of varInputNode for the whole frame (render HEAD
to render TAIL). Two problems with that. The card is drawn through
PursuitGraphNode272#drawNode, which for a stock node calls the original drawNode through a
bridge - hiding at render HEAD should have covered it, but the process left the graph
changed under the screen for the duration of the frame, which is exactly what makes the
cable pass repaint against a graph that flickers between two states: the lag. And the
inspector sidebar (widgets) was never covered at all.

Fix, three scoped places, no frame-wide state:

  * the screen's own drawNode is hooked for the duration of one card: the link is removed
    at HEAD and put back at RETURN, so exactly the card that prints the condition is drawn
    without it (the pursuit bridge does not run for stock nodes - that is why 298 missed);
  * the sidebar is rebuilt with the link removed for that rebuild and put back at the end of
    widgets() - widgets() calls neither changed() nor remember(), checked in bytecode, so
    nothing can be saved in the hidden state;
  * the cable pass still hides for its own duration and restores in a finally; a depth
    counter makes nested callers safe, and a leaked pass is cleared at the start of the next
    one.

A link to a "Переменная" node is a real variable link and is left alone everywhere: the
card keeps showing that variable, as it always did.

Usage: python3 tools/patch_build299.py <base-298.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build298-if-card-keeps-variable"
NEW_VERSION = "0.8.0-build299-if-card-scoped"
OUT_NAME = "settledlands-0.8.0-build299-if-card-scoped.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class",
)
ADDED = ()
COMMON_MIXINS = ()

EXPECTED_METHODS = {
    "dev/settledlands/npc/client/PursuitGraphNode272.class": ("drawStockCard", "beginWiresPass",
                                                              "stockInputSource",
                                                              "hideStockInputWires",
                                                              "restoreStockInputWires"),
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class": ("row",),
    "dev/settledlands/mixin/NpcPursuitValuePinMixin290.class": (
        "settledlands$hideLinkForCard",
        "settledlands$restoreLinkAfterCard",
        "settledlands$hideStockIfWiresForWidgets",
        "settledlands$restoreStockIfWiresAfterWidgets",
        "settledlands$hideStockIfWires",
        "settledlands$drawValueWires",
        "settledlands$paintValueWires"),
}

CHANGELOG_EN = (
    "Build 299 makes the condition card keep its own condition, without touching the graph "
    "for a whole frame. 298 hid the link for the entire frame, which made the blue cables "
    "repaint against a graph that kept changing and lagged. Now the link is removed for the "
    "single card draw and put back immediately, the sidebar is rebuilt the same way, and the "
    "cable pass hides and restores in a finally with a depth counter. The \u00ab\u0423\u0441\u043b\u043e\u0432\u0438\u0435\u00bb node "
    "keeps its own variable, operator and number after a blue wire is connected; the wire "
    "hands over the player the check node found. A link to a \u00ab\u041f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f\u00bb node is untouched. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 299: \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0430 \u0443\u0441\u043b\u043e\u0432\u0438\u044f \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442 \u0441\u0432\u043e\u0451 \u0443\u0441\u043b\u043e\u0432\u0438\u0435 "
    "\u0438 \u043f\u043e\u0441\u043b\u0435 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f \u0441\u0438\u043d\u0435\u0433\u043e \u043f\u0440\u043e\u0432\u043e\u0434\u0430, \u0438 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435\u0442 \u043b\u0430\u0433\u043e\u0432 \u043f\u0440\u043e\u0432\u043e\u0434\u0430: "
    "\u0441\u0432\u044f\u0437\u044c \u0441\u043d\u0438\u043c\u0430\u0435\u0442\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u043d\u0430 \u043e\u0434\u0438\u043d \u0432\u044b\u0437\u043e\u0432 \u0440\u0438\u0441\u043e\u0432\u0430\u043d\u0438\u044f \u043a\u0430\u0440\u0442\u043e\u0447\u043a\u0438 \u0438 \u043d\u0430 "
    "\u043f\u0435\u0440\u0435\u0441\u0431\u043e\u0440\u043a\u0443 \u0431\u043e\u043a\u043e\u0432\u043e\u0439 \u043f\u0430\u043d\u0435\u043b\u0438. \u0421\u0432\u044f\u0437\u044c \u0441 \u043d\u043e\u0434\u043e\u0439 \u00ab\u041f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f\u00bb \u043d\u0435 \u0442\u0440\u043e\u0433\u0430\u0435\u0442\u0441\u044f. "
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
    ap.add_argument("--classes", default="build299-src/out")
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
