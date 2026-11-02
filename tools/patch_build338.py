#!/usr/bin/env python3
"""
Build 338 - the possessed NPC's arms in first person under Punchy!.

  With Punchy! installed, first person hands are drawn by Punchy!, not by vanilla: it animates its
  own player model and submits that model's arms with the player's skin. The possession mod replaces
  the vanilla hand pass, so it was never asked - a possessed NPC still showed the player's own arms
  (Steve, for a player without a skin). Build 338 hooks Punchy!'s arm pass by name and draws the NPC's
  arm in its place, following the pose Punchy! has just animated, so punches and swings carry over.

  Punchy! is closed source: the hook is a @Pseudo mixin on punchy.client.render.PunchyArmRenderer
  with require = 0, and no action at all unless the player is possessed.

  Base: 337. New classes: the arm replacement and the hook.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build337-self-link"
NEW_VERSION = "0.8.0-build338-punchy-arms"
OUT_NAME = "settledlands-0.8.0-build338-punchy-arms.jar"

REMOVED = ()
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/client/PunchyArm338.class",
    "dev/settledlands/mixin/PunchyArmBridgeMixin338.class",
)

# Rendering only, and it touches Punchy!'s classes: client side.
MIXINS = ()
CLIENT_MIXINS = ("PunchyArmBridgeMixin338",)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/PunchyArm338.class": (
        "possessedModelName", "renderPossessedFirstPersonModelArms", "consumeArmor"),
    "dev/settledlands/mixin/PunchyArmBridgeMixin338.class": (
        "punchy.client.render.PunchyArmRenderer", "settledlands$npcArm", "settledlands$noPlayerSleeve"),
}

LANG_SET = {}
LANG_SET_EN = {}

CHANGELOG_EN = (
    "Build 338 makes a possessed NPC keep its own arms in first person when Punchy! is installed. "
    "Punchy! draws the first-person arms itself, with the player's model and skin, and the possession "
    "mod was never asked - so a possessed NPC still showed the player's arms. The mod now takes "
    "Punchy!'s arm pass, draws the NPC's arm in its place in the pose Punchy! has just animated, and "
    "skips the player's armour sleeve on that side. Without Punchy! installed nothing changes, and "
    "nothing happens at all unless the player is possessed."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 338: \u0432 \u0440\u0443\u043a\u0430\u0445 \u043e"
    "\u0442 \u043f\u0435\u0440\u0432\u043e\u0433\u043e \u043b\u0438\u0446\u0430 \u0443 "
    "\u0432\u0441\u0435\u043b\u0451\u043d\u043d\u043e\u0433\u043e \u0432 NPC \u0442\u0435"
    "\u043f\u0435\u0440\u044c \u0440\u0443\u043a\u0438 NPC \u0434\u0430\u0436\u0435 \u0441 "
    "Punchy!: \u043c\u043e\u0434 \u043f\u0435\u0440\u0435\u0445\u0432\u0430\u0442\u044b\u0432"
    "\u0430\u0435\u0442 \u0435\u0433\u043e \u043f\u0440\u043e\u0445\u043e\u0434 \u0440\u0443"
    "\u043a \u0438 \u0440\u0438\u0441\u0443\u0435\u0442 \u0440\u0443\u043a\u0443 NPC \u0432 "
    "\u0442\u043e\u0439 \u0436\u0435 \u043f\u043e\u0437\u0435, \u0430 \u0440\u0443\u043a\u0430"
    "\u0432 \u0438\u0433\u0440\u043e\u043a\u0430 \u043d\u0430 \u044d\u0442\u043e\u0439 \u0441"
    "\u0442\u043e\u0440\u043e\u043d\u0435 \u043d\u0435 \u0440\u0438\u0441\u0443\u0435\u0442\u0441"
    "\u044f. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out338"

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
            raise SystemExit("%s must not be a common mixin - it draws on the client" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (+%s)"
          % (len(common), len(client), ", ".join(CLIENT_MIXINS)))

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
