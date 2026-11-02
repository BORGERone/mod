#!/usr/bin/env python3
"""
Build 337 - a node cannot be wired to itself, and the random answer moves into the inspector.

  1. A node wired to itself is a dialogue that never ends: the line is shown, "next" leads back into
     the same node, and the same phrase comes again and again. The stock graph keeps such a link, and
     one drag makes it. connect(from, to, port) now refuses from == to, and the editor says why.
  2. Build 336 painted its random-answer check box over the node card. It is now a stock on/off row
     in the "Реплика" node's inspector - the same control every other yes/no property of a node uses -
     and the canvas half of 336 is removed from the jar.

  Base: 336. New classes: the rule, the wire guard, the inspector row and the two client hooks.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build336-random-line"
NEW_VERSION = "0.8.0-build337-self-link"
OUT_NAME = "settledlands-0.8.0-build337-self-link.jar"

# Build 336 drew its own check box over the card. Build 337 puts the toggle in the inspector as a
# stock row, so the canvas half of 336 goes away with it.
REMOVED = (
    "dev/settledlands/npc/client/NpcRandomLineUi336.class",
    "dev/settledlands/mixin/NpcRandomLineUiMixin336.class",
)
REPLACED = ()
ADDED = (
    "dev/settledlands/npc/NpcSelfLink337.class",
    "dev/settledlands/mixin/NpcSelfLinkMixin337.class",
    "dev/settledlands/npc/client/NpcRandomLineUi337.class",
    "dev/settledlands/mixin/NpcRandomLineInspectorMixin337.class",
    "dev/settledlands/mixin/NpcSelfLinkStatusMixin337.class",
)

MIXINS = ("NpcSelfLinkMixin337",)
CLIENT_MIXINS = ("NpcRandomLineInspectorMixin337", "NpcSelfLinkStatusMixin337")
CLIENT_REMOVED = ("NpcRandomLineUiMixin336",)

MIXIN_MARKERS = {
    "dev/settledlands/npc/NpcSelfLink337.class": ("self_link", "isSelfLink"),
    "dev/settledlands/mixin/NpcSelfLinkMixin337.class": ("connect", "isSelfLink"),
    "dev/settledlands/mixin/NpcRandomLineInspectorMixin337.class": (
        "widgets", "sidebarHeight", "setMessage"),
    "dev/settledlands/mixin/NpcSelfLinkStatusMixin337.class": ("mouseReleased", "consumeRefusal"),
    "dev/settledlands/npc/client/NpcRandomLineUi337.class": ("rand_line_on",),
}

LANG_SET = {
    "gui.settledlands.graph.rand_line_off": "\U0001f3b2 \u0420\u0430\u043d\u0434\u043e\u043c"
                                            "\u043d\u044b\u0439 \u043e\u0442\u0432\u0435\u0442"
                                            ": \u0432\u044b\u043a\u043b",
    "gui.settledlands.graph.rand_line_on": "\U0001f3b2 \u0420\u0430\u043d\u0434\u043e\u043c"
                                           "\u043d\u044b\u0439 \u043e\u0442\u0432\u0435\u0442"
                                           ": %s \u0438\u0437 %s",
    "gui.settledlands.graph.self_link": "\u041d\u043e\u0434\u0443 \u043d\u0435\u043b\u044c"
                                        "\u0437\u044f \u043f\u043e\u0434\u043a\u043b\u044e"
                                        "\u0447\u0438\u0442\u044c \u043a \u0441\u0430\u043c"
                                        "\u043e\u0439 \u0441\u0435\u0431\u0435",
}
LANG_SET_EN = {
    "gui.settledlands.graph.rand_line_off": "\U0001f3b2 Random answer: off",
    "gui.settledlands.graph.rand_line_on": "\U0001f3b2 Random answer: %s of %s",
    "gui.settledlands.graph.self_link": "A node cannot be linked to itself",
}

CHANGELOG_EN = (
    "Build 337 refuses a wire from a node to itself: a node wired to its own output repeated its line "
    "for ever, because every answer led back into the same node, and the drag that makes one is easy "
    "to do by accident. The editor now says so in words. The random answer of a line node moved from "
    "the check box over the card into the node's inspector, as the same on/off row every other "
    "yes/no property of a node uses."
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 337: \u043d\u043e\u0434\u0443 \u043d\u0435"
    "\u043b\u044c\u0437\u044f \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442"
    "\u044c \u043a \u0441\u0430\u043c\u043e\u0439 \u0441\u0435\u0431\u0435; \u0442"
    "\u0443\u043c\u0431\u043b\u0435\u0440 \u00ab\u0420\u0430\u043d\u0434\u043e\u043c"
    "\u043d\u044b\u0439 \u043e\u0442\u0432\u0435\u0442\u00bb \u043f\u0435\u0440\u0435"
    "\u0435\u0445\u0430\u043b \u0432 \u0438\u043d\u0441\u043f\u0435\u043a\u0442\u043e"
    "\u0440. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out337"

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
            raise SystemExit("%s must not be a common mixin - it draws on a client screen" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common (+%s), %d client (+%s)"
          % (len(common), ", ".join(MIXINS), len(client), ", ".join(CLIENT_MIXINS)))

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
