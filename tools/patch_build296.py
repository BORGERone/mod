#!/usr/bin/env python3
"""Build 296 - revert of 295.

Build 295 broke the condition node: it started the chain from an empty radius with no
initiator, so the IF node was evaluated for nobody instead of for the body the "В радиусе"
node found. The user asked for the change to be reverted, not adjusted.

So 296 is 294's code, unchanged, with a new version number so the mod list shows which jar
is installed. Nothing in this jar is new: no class is added, replaced or removed. Only the
three metadata strings move (version, displayName, description) plus the patch-info line,
which is what every build does anyway.

Usage: python3 tools/patch_build296.py <base-294.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build294-radius-if-branch"
NEW_VERSION = "0.8.0-build296-revert-295"
OUT_NAME = "settledlands-0.8.0-build296-back-to-294.jar"

# The 295 additions that must not be in this jar - checked, not trusted.
ABSENT_CLASSES = (
    "dev/settledlands/mixin/GuideNpcEmptyCheck295.class",
)
ABSENT_METHODS = {
    "dev/settledlands/npc/NpcValueSource290.class": ("tickEmptyChecks",),
}

CHANGELOG_EN = (
    "Build 296 reverts build 295. 295 made the check node (\u00abВ радиусе\u00bb) start its "
    "chain when the radius was empty, with no body at all, and that broke the condition "
    "node: the condition must be evaluated for the body the check node found, not for "
    "nobody. This jar carries the 294 code unchanged - the check node behaves exactly as it "
    "did in 294, the condition node works as it did before 295, and nothing else was "
    "touched. The reverted build stays in the history; the question of what the condition "
    "should compare for a body is open and is being clarified. "
)

PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 296 \u043e\u0442\u043c\u0435\u043d\u044f\u0435\u0442 295. \u0412 295 \u043d\u043e\u0434\u0430 \u00ab\u0412 \u0440\u0430\u0434\u0438\u0443\u0441\u0435\u00bb "
    "\u0437\u0430\u043f\u0443\u0441\u043a\u0430\u043b\u0430 \u0446\u0435\u043f\u044c \u0431\u0435\u0437 \u0442\u0435\u043b\u0430, \u0438 \u044d\u0442\u043e \u0441\u043b\u043e\u043c\u0430\u043b\u043e \u043d\u043e\u0434\u0443 \u0443\u0441\u043b\u043e\u0432\u0438\u044f: \u0443\u0441\u043b\u043e\u0432\u0438\u0435 "
    "\u0434\u043e\u043b\u0436\u043d\u043e \u0441\u0447\u0438\u0442\u0430\u0442\u044c\u0441\u044f \u0434\u043b\u044f \u0442\u0435\u043b\u0430, \u043a\u043e\u0442\u043e\u0440\u043e\u0435 \u043d\u0430\u0448\u043b\u0430 \u043d\u043e\u0434\u0430 \u0440\u0430\u0434\u0438\u0443\u0441\u0430, \u0430 \u043d\u0435 \u0434\u043b\u044f "
    "\u043d\u0438\u043a\u043e\u0433\u043e. \u0412 \u044d\u0442\u043e\u043c \u044f\u0434\u0440\u0435 \u043a\u043e\u0434 294 \u0431\u0435\u0437 \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0439: \u043d\u043e\u0434\u0430 \u0440\u0430\u0434\u0438\u0443\u0441\u0430 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u043a\u0430\u043a \u0432 294, "
    "\u043d\u043e\u0434\u0430 \u0443\u0441\u043b\u043e\u0432\u0438\u044f - \u043a\u0430\u043a \u0434\u043e 295. \u0418\u0437\u043c\u0435\u043d\u0435\u043d\u044b \u0442\u043e\u043b\u044c\u043a\u043e \u043c\u0435\u0442\u0430\u0434\u0430\u043d\u043d\u044b\u0435 \u0441\u0431\u043e\u0440\u043a\u0438. "
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

    # The base must be the 294 jar and must not carry any of the 295 additions.
    for name in ABSENT_CLASSES:
        if name in blobs:
            raise SystemExit("base jar already carries the 295 class: %s" % name)
    for name, methods in ABSENT_METHODS.items():
        cf = classfile.parse(blobs[name], name)
        have = {m.name for m in cf.methods}
        for method in methods:
            if method in have:
                raise SystemExit("base jar already carries %s#%s" % (name, method))
    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    for name in ABSENT_CLASSES:
        simple = name.rsplit("/", 1)[-1][:-len(".class")]
        if simple in mixins.get("mixins", []):
            raise SystemExit("base mixins.json still lists %s" % simple)

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
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")
    print("  neoforge.mods.toml: version %s, displayName, description prefix" % NEW_VERSION)

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")
        print("  patch-info: revert note")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d entries, no class changed)" % (out_path, len(order)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
