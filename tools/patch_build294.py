#!/usr/bin/env python3
"""Build 294 - "В радиусе" -> IF must not swallow the "nobody here" answer.

Reported: graph "В радиусе" #4 -> "Условие" #5 (`#4 == 0`), True -> `say true`,
False -> `say false`. Somebody in the radius printed `false`; nobody in the radius
printed nothing at all.

Cause, from the class itself: the walk in `resolveExecutionTarget` treats the node as a
gate. `checkProximityCondition(npc, player, node)` false sets the next node to -1 on the
spot, so the walk ended at #4 and the IF was never evaluated with 0. The node's blue
output is read separately by `NpcValueSource290#ifValue` -> `NpcCheckValue289#detected`,
and that one answers 0 when the radius is empty - the answer simply never arrived.

Fix, one hook: `NpcValueSource290#passThroughWhenValue` lets a failed check continue to
its next node, but only when the node is a value source that some node reads through a
blue wire, and only when the node carries no action of its own. A check with an action is
a gate with a payload and keeps the stock behaviour. On that pass the node's own trigger
list is skipped as well (mark consumed by `triggerBlocked`), so a failed check runs
nothing and only the consumer sees the value.

Usage: python3 tools/patch_build294.py <base-293.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build293-tp-target-guard"
NEW_VERSION = "0.8.0-build294-radius-if-branch"
OUT_NAME = "settledlands-0.8.0-build294-radius-if-branch.jar"

MIXINS_JSON = "settledlands.mixins.json"

REPLACED = (
    "dev/settledlands/npc/NpcValueSource290.class",
)
ADDED = (
    "dev/settledlands/mixin/GuideDialogueCheckGate294.class",
)
COMMON_MIXINS = ("GuideDialogueCheckGate294",)

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcValueSource290.class": ("passThroughWhenValue", "triggerBlocked"),
    "dev/settledlands/mixin/GuideDialogueCheckGate294.class": ("settledlands$checkGate",),
}

CHANGELOG_EN = (
    "Build 294 fixes the check node (\\u00abВ радиусе\\u00bb) when it feeds an IF through a blue "
    "value wire. The node used to end the walk on the spot when nobody was in its radius, "
    "so the IF never ran with the value 0 and its \\u00ab=\\u00bb branch was unreachable: with "
    "`#4 == 0` only the False chain ever worked. Now such a node passes the flow on when "
    "nobody is there, so the IF gets 0 and its True chain runs; when the radius has "
    "somebody, nothing changes. The node's own action and trigger list are still not run "
    "on a failed check - only the value reaches the consumer. Graphs without value wires "
    "behave exactly as before. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 294 \u0447\u0438\u043d\u0438\u0442 \u043d\u043e\u0434\u0443 \u00ab\u0412 \u0440\u0430\u0434\u0438\u0443\u0441\u0435\u00bb, \u043a\u043e\u0433\u0434\u0430 \u043e\u043d\u0430 \u043f\u043e\u0434\u0430\u0451\u0442 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435 "
    "\u0443\u0441\u043b\u043e\u0432\u0438\u044e \u0447\u0435\u0440\u0435\u0437 \u0441\u0438\u043d\u0438\u0439 \u043f\u0440\u043e\u0432\u043e\u0434. \u0420\u0430\u043d\u044c\u0448\u0435 \u043d\u043e\u0434\u0430 \u043e\u0431\u043e\u0440\u0430\u0432\u0430\u043b\u0430 \u043f\u0440\u043e\u0445\u043e\u0434 \u043d\u0430 \u043c\u0435\u0441\u0442\u0435, \u0435\u0441\u043b\u0438 \u0432 \u0440\u0430\u0434\u0438\u0443\u0441\u0435 "
    "\u043d\u0438\u043a\u043e\u0433\u043e \u043d\u0435 \u0431\u044b\u043b\u043e, \u043f\u043e\u044d\u0442\u043e\u043c\u0443 \u0443\u0441\u043b\u043e\u0432\u0438\u0435 \u043d\u0438 \u0440\u0430\u0437\u0443 \u043d\u0435 \u0432\u0438\u0434\u0435\u043b\u043e \u043d\u043e\u043b\u044c \u0438 \u0435\u0433\u043e \u0432\u0435\u0442\u043a\u0430 \u00ab=\u00bb \u043d\u0435 \u0441\u0440\u0430\u0431\u0430\u0442\u044b\u0432\u0430\u043b\u0430: "
    "\u043f\u0440\u0438 `#4 == 0` \u0440\u0430\u0431\u043e\u0442\u0430\u043b\u0430 \u0442\u043e\u043b\u044c\u043a\u043e \u0432\u0435\u0442\u043a\u0430 False. \u0422\u0435\u043f\u0435\u0440\u044c \u0442\u0430\u043a\u0430\u044f \u043d\u043e\u0434\u0430 \u043f\u0440\u043e\u043f\u0443\u0441\u043a\u0430\u0435\u0442 \u043f\u0440\u043e\u0445\u043e\u0434 \u0434\u0430\u043b\u044c\u0448\u0435, \u043a\u043e\u0433\u0434\u0430 "
    "\u0432 \u0440\u0430\u0434\u0438\u0443\u0441\u0435 \u043d\u0438\u043a\u043e\u0433\u043e \u043d\u0435\u0442, \u0443\u0441\u043b\u043e\u0432\u0438\u0435 \u043f\u043e\u043b\u0443\u0447\u0430\u0435\u0442 0 \u0438 \u0435\u0433\u043e \u0432\u0435\u0442\u043a\u0430 True \u0441\u0440\u0430\u0431\u0430\u0442\u044b\u0432\u0430\u0435\u0442; \u043a\u043e\u0433\u0434\u0430 \u0432 \u0440\u0430\u0434\u0438\u0443\u0441\u0435 \u043a\u0442\u043e-\u0442\u043e "
    "\u0435\u0441\u0442\u044c, \u043d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043c\u0435\u043d\u044f\u0435\u0442\u0441\u044f. \u0421\u043e\u0431\u0441\u0442\u0432\u0435\u043d\u043d\u043e\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u043e\u0434\u044b \u0438 \u0435\u0451 \u0441\u043f\u0438\u0441\u043e\u043a \u0442\u0440\u0438\u0433\u0433\u0435\u0440\u043e\u0432 \u043d\u0430 \u043d\u0435\u0443\u0434\u0430\u0447\u043d\u043e\u0439 "
    "\u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0435 \u043f\u043e-\u043f\u0440\u0435\u0436\u043d\u0435\u043c\u0443 \u043d\u0435 \u0432\u044b\u043f\u043e\u043b\u043d\u044f\u044e\u0442\u0441\u044f - \u0434\u043e \u043f\u043e\u0442\u0440\u0435\u0431\u0438\u0442\u0435\u043b\u044f \u0434\u043e\u0445\u043e\u0434\u0438\u0442 \u0442\u043e\u043b\u044c\u043a\u043e \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435. \u0413\u0440\u0430\u0444\u044b \u0431\u0435\u0437 "
    "\u043f\u0440\u043e\u0432\u043e\u0434\u043e\u0432 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0439 \u0440\u0430\u0431\u043e\u0442\u0430\u044e\u0442 \u043a\u0430\u043a \u0440\u0430\u043d\u044c\u0448\u0435. "
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
    ap.add_argument("--classes", default="build294-src/out")
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
