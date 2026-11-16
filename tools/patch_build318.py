#!/usr/bin/env python3
"""Build 318 - copy and paste of graph nodes, as text, like the editor in Unreal Engine.

  Ctrl+C writes the selected nodes to the system clipboard and Ctrl+V reads them back into any
  graph of any NPC. The block is plain readable text, so it can be carried between graphs, between
  worlds, and through a chat window; a link survives only when both of its ends were copied, and the
  entry node is never copied because a graph has one entrance.

  Three new classes: NpcGraphClipboard318 (the text format and the id remapping, plain Java),
  NpcClipboard318 (the two clipboard calls, isolated) and NpcClipboardMixin318 (the two keys on the
  graph screen). Four status messages are added to both language files.

Usage: python3 tools/patch_build318.py <base-317.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build317-water"
NEW_VERSION = "0.8.0-build318-clipboard"
OUT_NAME = "settledlands-0.8.0-build318-clipboard.jar"

REPLACED = (
)

ADDED = (
    "dev/settledlands/npc/NpcGraphClipboard318.class",
    "dev/settledlands/npc/client/NpcClipboard318.class",
    "dev/settledlands/mixin/NpcClipboardMixin318.class",
)

MIXIN = "NpcClipboardMixin318"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcGraphClipboard318.class": "SLGRAPH318",
    "dev/settledlands/npc/client/NpcClipboard318.class": "getClipboard",
    "dev/settledlands/mixin/NpcClipboardMixin318.class": "settledlands$copyPaste",
}

LANG_RU = {
    "gui.settledlands.graph.clip_copy": "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043e \u043d\u043e\u0434:",
    "gui.settledlands.graph.clip_empty": "\u041d\u0435\u0447\u0435\u0433\u043e \u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c",
    "gui.settledlands.graph.clip_paste": "\u0412\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u043e \u043d\u043e\u0434:",
    "gui.settledlands.graph.clip_fail": "\u0412\u0441\u0442\u0430\u0432\u043a\u0430 \u043d\u0435 \u0443\u0434\u0430\u043b\u0430\u0441\u044c:",
}
LANG_EN = {
    "gui.settledlands.graph.clip_copy": "Copied nodes:",
    "gui.settledlands.graph.clip_empty": "Nothing to copy",
    "gui.settledlands.graph.clip_paste": "Pasted nodes:",
    "gui.settledlands.graph.clip_fail": "Paste failed:",
}

CHANGELOG_EN = (
    "Build 318 adds copy and paste to the graph editor. Ctrl+C writes the selected nodes to the "
    "system clipboard as readable text and Ctrl+V recreates them in any graph of any NPC: every "
    "field is carried over, links between copied nodes are reconnected to their new ids, links that "
    "left the selection are dropped, and the entry node is never copied. The whole block is one "
    "undo step and the pasted nodes come back selected. "
)
PATCHINFO = (
    "\u0421\u0431\u043e\u0440\u043a\u0430 318: \u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0438 \u0432\u0441\u0442\u0430\u0432\u043a\u0430 \u043d\u043e\u0434 (Ctrl+C / Ctrl+V). \u041a\u043e\u043f\u0438\u0440\u0443\u0435\u0442\u0441\u044f \u0432\u044b\u0434\u0435\u043b\u0435\u043d\u0438\u0435 \u0441\u043e \u0432\u0441\u0435\u043c\u0438 \u0437\u043d\u0430\u0447\u0435\u043d\u0438\u044f\u043c\u0438 \u0438 \u0441\u0432\u044f\u0437\u044f\u043c\u0438 \u0432 \u0442\u0435\u043a\u0441\u0442\u043e\u0432\u044b\u0439 \u0431\u0443\u0444\u0435\u0440; \u0432\u0441\u0442\u0430\u0432\u043a\u0430 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u0432 \u043b\u044e\u0431\u043e\u043c \u0433\u0440\u0430\u0444\u0435 \u0438 \u043b\u044e\u0431\u043e\u0433\u043e NPC. "
)

ADDED = (
    "dev/settledlands/npc/NpcGraphClipboard318.class",
    "dev/settledlands/npc/client/NpcClipboard318.class",
    "dev/settledlands/mixin/NpcClipboardMixin318.class",
)

MIXIN = "NpcClipboardMixin318"

EXPECTED_METHODS = {
    "dev/settledlands/npc/NpcGraphClipboard318.class": "SLGRAPH318",
    "dev/settledlands/npc/client/NpcClipboard318.class": "getClipboard",
    "dev/settledlands/mixin/NpcClipboardMixin318.class": "settledlands$copyPaste",
}

LANG_RU = {
    "gui.settledlands.graph.clip_copy": "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043e \u043d\u043e\u0434:",
    "gui.settledlands.graph.clip_empty": "\u041d\u0435\u0447\u0435\u0433\u043e \u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c",
    "gui.settledlands.graph.clip_paste": "\u0412\u0441\u0442\u0430\u0432\u043b\u0435\u043d\u043e \u043d\u043e\u0434:",
    "gui.settledlands.graph.clip_fail": "\u0412\u0441\u0442\u0430\u0432\u043a\u0430 \u043d\u0435 \u0443\u0434\u0430\u043b\u0430\u0441\u044c:",
}
LANG_EN = {
    "gui.settledlands.graph.clip_copy": "Copied nodes:",
    "gui.settledlands.graph.clip_empty": "Nothing to copy",
    "gui.settledlands.graph.clip_paste": "Pasted nodes:",
    "gui.settledlands.graph.clip_fail": "Paste failed:",
}

CHANGELOG_EN = (
    "Build 318 adds copy and paste to the graph editor. Ctrl+C writes the selected nodes to the "
    "system clipboard as readable text and Ctrl+V recreates them in any graph of any NPC: every "
    "field is carried over, links between copied nodes are reconnected to their new ids, links that "
    "left the selection are dropped, and the entry node is never copied. The whole block is one "
    "undo step and the pasted nodes come back selected. "
)
PATCHINFO = (
    "\\u0421\\u0431\\u043e\\u0440\\u043a\\u0430 318: \\u043a\\u043e\\u043f\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435 "
    "\\u0438 \\u0432\\u0441\\u0442\\u0430\\u0432\\u043a\\u0430 \\u043d\\u043e\\u0434 (Ctrl+C / Ctrl+V). "
    "\\u041a\\u043e\\u043f\\u0438\\u0440\\u0443\\u0435\\u0442\\u0441\\u044f \\u0432\\u044b\\u0434\\u0435\\u043b\\u0435\\u043d\\u0438\\u0435 \\u0441\\u043e \\u0432\\u0441\\u0435\\u043c\\u0438 "
    "\\u0437\\u043d\\u0430\\u0447\\u0435\\u043d\\u0438\\u044f\\u043c\\u0438 \\u0438 \\u0441\\u0432\\u044f\\u0437\\u044f\\u043c\\u0438 \\u0432 \\u0442\\u0435\\u043a\\u0441\\u0442\\u043e\\u0432\\u044b\\u0439 "
    "\\u0431\\u0443\\u0444\\u0435\\u0440; \\u0432\\u0441\\u0442\\u0430\\u0432\\u043a\\u0430 \\u0440\\u0430\\u0431\\u043e\\u0442\\u0430\\u0435\\u0442 \\u0432 \\u043b\\u044e\\u0431\\u043e\\u043c \\u0433\\u0440\\u0430\\u0444\\u0435 \\u0438 \\u043b\\u044e\\u0431\\u043e\\u0433\\u043e NPC. "
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
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out318"

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
    if MIXIN not in mixins.setdefault("client", []):
        mixins["client"].append(MIXIN)
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
