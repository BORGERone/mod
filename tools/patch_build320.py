#!/usr/bin/env python3
"""Build 320 - the "В радиусе" node can show the zone it works in.

  An author sets a radius and cannot tell what it looks like in this terrain. A visibility toggle on
  the node draws the circle it measures in, as a ring of particles around the NPC, refreshed twice a
  second while someone is close enough to see it. It is an authoring aid only: no check, dialogue or
  graph behaviour changes.

  Four new classes: NpcRadiusZone320 (the server ring), NpcRadiusZoneUi320 (the check box on the
  canvas), and one mixin for each side. The flag lives in Node#timerRepeat - a saved boolean no
  radius node reads - so it survives save, load, copy, undo and the clipboard without touching the
  graph format.

Usage: python3 tools/patch_build320.py <base-319.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build319-clipboard-center"
NEW_VERSION = "0.8.0-build320-radius-zone"
OUT_NAME = "settledlands-0.8.0-build320-radius-zone.jar"

REPLACED = (
)

ADDED = (
    "dev/settledlands/npc/NpcRadiusZone320.class",
    "dev/settledlands/npc/client/NpcRadiusZoneUi320.class",
    "dev/settledlands/mixin/NpcRadiusZoneMixin320.class",
    "dev/settledlands/mixin/NpcRadiusZoneUiMixin320.class",
)

# Two lists: the ring is drawn by the server, the tick box by the client.
MIXIN = "NpcRadiusZoneMixin320"
MIXIN_CLIENT = "NpcRadiusZoneUiMixin320"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcRadiusZone320.class": "VIEW_RANGE",
    "dev/settledlands/npc/client/NpcRadiusZoneUi320.class": "settledlands",
    "dev/settledlands/mixin/NpcRadiusZoneMixin320.class": "settledlands$radiusZone",
    "dev/settledlands/mixin/NpcRadiusZoneUiMixin320.class": "settledlands$clickZoneToggle",
}

LANG_RU = {
    "gui.settledlands.graph.radius_zone": "\u0417\u043e\u043d\u0430",
}
LANG_EN = {
    "gui.settledlands.graph.radius_zone": "Zone",
}

CHANGELOG_EN = (
    "Build 320 adds a visibility toggle to the \"In Radius\" node. Turned on, the server draws the "
    "circle the node measures in - a ring of particles at exactly that distance around the NPC, "
    "refreshed twice a second and only while a player is close enough to see it. It is an authoring "
    "aid: the check, the dialogue and the graph behave as before, and the flag is saved with the "
    "node. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 320: \u0443 \u043d\u043e\u0434\u044b "
    "\u00ab\u0412 \u0440\u0430\u0434\u0438\u0443\u0441\u0435\u00bb \u043f\u043e\u044f\u0432\u0438\u043b\u0441\u044f "
    "\u0442\u0443\u043c\u0431\u043b\u0435\u0440 \u00ab\u0417\u043e\u043d\u0430\u00bb: "
    "\u0432\u043a\u043b\u044e\u0447\u0451\u043d\u043d\u044b\u0439, \u043e\u043d "
    "\u0440\u0438\u0441\u0443\u0435\u0442 \u043a\u0440\u0443\u0433 "
    "\u0440\u0430\u0434\u0438\u0443\u0441\u0430 \u0447\u0430\u0441\u0442\u0438\u0446\u0430\u043c\u0438 "
    "\u0432\u043e\u043a\u0440\u0443\u0433 NPC. \u0422\u043e\u043b\u044c\u043a\u043e "
    "\u0432\u0438\u0437\u0443\u0430\u043b\u044c\u043d\u0430\u044f "
    "\u043f\u043e\u043c\u043e\u0449\u044c, \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 "
    "\u0438 \u0434\u0438\u0430\u043b\u043e\u0433\u0438 \u043d\u0435 "
    "\u0438\u0437\u043c\u0435\u043d\u0438\u043b\u0438\u0441\u044c. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    # Compiled classes come from a build directory next to this script's repo root.
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out320"

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    for name in REPLACED + ADDED:
        src = srcdir / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        want = EXPECTED_METHODS.get(name)
        if want and want.encode("utf-8") not in data:
            raise SystemExit("%s does not look like the build we expect (no %s)"
                             % (name, want))
        if name in ADDED:
            if name in blobs:
                raise SystemExit("base jar already has %s" % name)
            order.append(name)
            print("  added: %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_RU),
                        ("assets/settledlands/lang/en_us.json", LANG_EN)):
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        added = 0
        for key, value in table.items():
            if key not in strings:
                strings[key] = value
                added += 1
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  %s: %d keys added" % (path.rsplit("/", 1)[-1], added))

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    # The ring is drawn on the server, the tick box on the client: two lists.
    if MIXIN not in mixins.setdefault("mixins", []):
        mixins["mixins"].append(MIXIN)
    if MIXIN_CLIENT not in mixins.setdefault("client", []):
        mixins["client"].append(MIXIN_CLIENT)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                        + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client" % (len(mixins.get("mixins", [])),
                                                   len(mixins["client"])))

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
