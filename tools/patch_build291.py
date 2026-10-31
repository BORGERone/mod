#!/usr/bin/env python3
"""Build 291 - the startup fix for Build 290.

Request: "add a blue input to the trigger node, the replica, the IF condition and the
variable. And add a blue output to the damage node - it must hand out whoever damaged
the NPC."

Editor: the four stock nodes that take a body (реплика LINE, триггер TRIGGER, условие IF,
переменная VARIABLE) get the same blue diamond input the pursuit card has, on the left
edge and on the same row. The "Урон" node gets the blue output the "В радиусе" node got in
289. The input is stored in the node's own varInputNode field, which the graph NBT already
writes, so nothing new is saved. Drag, drop, middle-click cut and right-click clearing are
the same as every other value wire; an IF node's stock wire to a variable is hidden while
the blue one is drawn, so there is exactly one cable per wire.

Server: NpcDamageValue290 remembers the attacker of every damage event (the body that the
"Урон" node hands out, filtered by that node's own Фильтр and Мин. урон, valid for 10 s).
NpcValueSource290 answers "which body does node X hand out" for both producers, and the
four consumers use it: IF compares presence (1/0) with its own operation and number, a
variable reads as presence, a replica replaces %target%, %target_kind% and %target_uuid%,
and a trigger runs its commands only for that body.

The node already finds a body: GuideDialogue#findCheckTarget scans its box and returns
the first matching entity. That entity is the value.

Editor: the check card gets the same blue diamond the pursuit card has, on its right edge
and on the same row, drawn after the stock card so nothing of the original is lost. Drag
it onto a pursuit node (or drag the pursuit node's blue input onto the check node) and the
link is stored as the pursuit node's source, |#<id> - the same field the pursuit-to-pursuit
wire uses. Draw, drag, middle-click cut and right-click-unplug all behave like every other
value wire.


Usage: python3 tools/patch_build291.py <base-290.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build290-stock-node-value-ports"
NEW_VERSION = "0.8.0-build291-launch-fix"
OUT_NAME = "settledlands-0.8.0-build291-launch-fix.jar"

REPLACED = (
    "dev/settledlands/mixin/GuideDialogueValue290.class",
)
ADDED = ()
REMOVED = ()

MIXINS_JSON = "settledlands.mixins.json"
NEW_MIXINS = ()
COMMON_MIXINS = ()
DROP_MIXINS = ()
CLIENT_MIXINS = ()


CHANGELOG_EN = (
    "Build 291 fixes the startup crash of Build 290: one hook of the dialogue mixin "
    "declared its session parameter as Object, and the mixin loader compares descriptors "
    "literally, so the mod failed to apply and the game died while loading. The same hook "
    "now sits on the NPC's own relationTo call inside the same method, where the types are "
    "public, and the build gate checks every injector parameter type from now on. Nothing "
    "else changed: the blue inputs of \u0440\u0435\u043f\u043b\u0438\u043a\u0430, \u0442\u0440\u0438\u0433\u0433\u0435\u0440, \u0443\u0441\u043b\u043e\u0432\u0438\u0435 and "
    "\u043f\u0435\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f and the blue output of \u00ab\u0423\u0440\u043e\u043d\u00bb are the same as in 290. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 291 \u0447\u0438\u043d\u0438\u0442 \u0432\u044b\u043b\u0435\u0442 \u043d\u0430 \u0437\u0430\u043f\u0443\u0441\u043a\u0435 \u0438\u0437 290: \u043e\u0434\u0438\u043d \u0445\u0443\u043a \u043c\u0438\u043a\u0441\u0438\u043d\u0430 \u0434\u0438\u0430\u043b\u043e\u0433\u0430 \u0431\u044b\u043b "
    "\u043e\u0431\u044a\u044f\u0432\u043b\u0435\u043d \u0441 \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u043e\u043c Object \u0432\u043c\u0435\u0441\u0442\u043e \u0442\u0438\u043f\u0430 \u0441\u0435\u0441\u0441\u0438\u0438, \u0430 \u0437\u0430\u0433\u0440\u0443\u0437\u0447\u0438\u043a \u043c\u0438\u043a\u0441\u0438\u043d\u043e\u0432 \u0441\u0440\u0430\u0432\u043d\u0438\u0432\u0430\u0435\u0442 "
    "\u0434\u0435\u0441\u043a\u0440\u0438\u043f\u0442\u043e\u0440\u044b \u0431\u0443\u043a\u0432\u0430\u043b\u044c\u043d\u043e. \u0422\u043e\u0442 \u0436\u0435 \u0445\u0443\u043a \u0442\u0435\u043f\u0435\u0440\u044c \u0441\u0442\u043e\u0438\u0442 \u043d\u0430 \u0432\u044b\u0437\u043e\u0432\u0435 relationTo \u0432\u043d\u0443\u0442\u0440\u0438 \u0442\u043e\u0433\u043e \u0436\u0435 "
    "\u043c\u0435\u0442\u043e\u0434\u0430, \u0433\u0434\u0435 \u0442\u0438\u043f\u044b \u043f\u0443\u0431\u043b\u0438\u0447\u043d\u044b\u0435, \u0430 \u0433\u0435\u0439\u0442 \u0442\u0435\u043f\u0435\u0440\u044c \u0441\u0432\u0435\u0440\u044f\u0435\u0442 \u0442\u0438\u043f\u044b \u043f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u043e\u0432 \u0432\u0441\u0435\u0445 \u0438\u043d\u044a\u0435\u043a\u0442\u043e\u0440\u043e\u0432. "
    "\u041e\u0441\u0442\u0430\u043b\u044c\u043d\u043e\u0435 \u043d\u0435 \u043c\u0435\u043d\u044f\u043b\u043e\u0441\u044c. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build291-src/out")
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

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        del blobs[name]
        order.remove(name)
        print("  removed: %s" % name)

    for name in REPLACED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name not in blobs:
            raise SystemExit("class is not in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        print("  replaced: %s" % name)

    for name in ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        if name in blobs:
            raise SystemExit("class already in the base jar: %s" % name)
        blobs[name] = src.read_bytes()
        order.append(name)
        print("  added: %s" % name)

    mixins = json.loads(blobs[MIXINS_JSON].decode("utf-8"))
    common = [m for m in mixins.get("mixins", []) if m not in DROP_MIXINS]
    client = [m for m in mixins.get("client", []) if m not in DROP_MIXINS]
    for mixin in NEW_MIXINS:
        if mixin not in common and mixin not in client:
            client.append(mixin)
    for mixin in COMMON_MIXINS:
        if mixin not in common:
            common.append(mixin)
    mixins["mixins"] = common
    mixins["client"] = client
    blobs[MIXINS_JSON] = (json.dumps(mixins, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(common), len(client)))

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    marker = "description='''"
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, description prefix" % NEW_VERSION)

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
    print("wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
