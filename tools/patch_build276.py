#!/usr/bin/env python3
"""Build 276 - give the pursuit node its own inspector and working typing.

Feedback on 275:
  * "still cannot type" - NpcPursuitTypingMixin275 declared
    `@Unique public boolean charTyped(char, int)`. @Unique tells Mixin to make the
    name unique, so the method was renamed when merged and the override never ran;
    Screen#charTyped stayed in charge and the card field never saw a character.
  * "the editing window is the IF/trigger one, it should be its own" - a pursuit
    node keeps node.type == TRIGGER (2), so NpcDialogueGraphScreen#widgets() built
    the stock TRIGGER inspector: the preset dropdown plus the free-form
    "trigger:" command box.

Repair:
  * NpcPursuitInspectorMixin276 (new, replaces NpcPursuitTypingMixin275):
      - charTyped(char, int) merged into the screen WITHOUT @Unique, so it really
        overrides Screen#charTyped (card field first, then the widgets);
      - @Inject(TAIL) on widgets(): hides the trigger preset dropdown and the
        command box and builds the pursuit panel instead - target type switch,
        target value box (a real EditBox, so typing goes through the normal
        widget path), stop switch; the standard unlink/delete buttons move under
        the panel;
      - keyPressed forwards backspace/enter to the card field.
  * PursuitGraphNode272: value/type/stop helpers used by the inspector; the card
    and the inspector both read node.action, so they cannot drift apart.
  * lang: gui.settledlands.pursuit.target_value, .target_npc_button,
    .target_player_button, .stop_toggle_on.

Usage: python3 tools/patch_build276.py <base-275.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build275-pursuit-stock-node"
NEW_VERSION = "0.8.0-build276-pursuit-inspector"
OUT_NAME = "settledlands-0.8.0-build276-pursuit-inspector.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitInspectorMixin276.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitTypingMixin275.class",
)
MIXINS_JSON = "settledlands.mixins.json"
OLD_MIXIN = "NpcPursuitTypingMixin275"
NEW_MIXIN = "NpcPursuitInspectorMixin276"
LANG = "assets/settledlands/lang/%s.json"
LANG_ADD = {
    "ru_ru": {
        "gui.settledlands.pursuit.target_value": "Цель:",
        "gui.settledlands.pursuit.target_npc_button": "Цель: NPC",
        "gui.settledlands.pursuit.target_player_button": "Цель: игрок",
        "gui.settledlands.pursuit.stop_toggle_on": "Завершить преследование ✓",
    },
    "en_us": {
        "gui.settledlands.pursuit.target_value": "Target:",
        "gui.settledlands.pursuit.target_npc_button": "Target: NPC",
        "gui.settledlands.pursuit.target_player_button": "Target: player",
        "gui.settledlands.pursuit.stop_toggle_on": "Stop pursuit ✓",
    },
}

CHANGELOG_EN = (
    "Build 276: the pursuit node has its own inspector and the field really takes text. "
    "A pursuit node is a TRIGGER node, so the inspector used to show the trigger preset "
    "dropdown and the free-form command box; those are replaced with the pursuit panel - "
    "target type switch, target value box and stop switch - while the standard unlink and "
    "delete buttons stay under it. The target value box is a normal text field, and the "
    "card field now receives typed characters too: Build 275 merged charTyped into the "
    "screen with @Unique, which made Mixin rename it, so the override never ran. "
)
PATCHINFO = (
    "Сборка 276: у ноды преследования свой инспектор, и в поле действительно можно писать. "
    "Нода преследования имеет тип TRIGGER, поэтому инспектор показывал редактор триггера "
    "(список пресетов и произвольную команду); теперь там панель преследования — переключатель "
    "типа цели, поле значения и тумблер завершения, а штатные кнопки отвязки и удаления стоят "
    "под ней. Поле значения — обычный EditBox. Поле на карточке тоже принимает символы: в 275 "
    "charTyped был помечен @Unique, Mixin переименовал метод, и переопределение не работало. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build276-src/out")
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

    for name in REPLACED + ADDED:
        src = classes / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        blobs[name] = src.read_bytes()
        if name not in order:
            order.append(name)
        print("  %s: %s" % ("replaced" if name in REPLACED else "added", name))

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("missing entry to remove: %s" % name)
        del blobs[name]
        order.remove(name)
        print("  removed: %s" % name)

    mixins = blobs[MIXINS_JSON].decode("utf-8")
    if OLD_MIXIN not in mixins:
        raise SystemExit("%s does not list %s" % (MIXINS_JSON, OLD_MIXIN))
    if NEW_MIXIN in mixins:
        raise SystemExit("%s already lists %s" % (MIXINS_JSON, NEW_MIXIN))
    mixins = mixins.replace('"%s"' % OLD_MIXIN, '"%s"' % NEW_MIXIN, 1)
    blobs[MIXINS_JSON] = mixins.encode("utf-8")
    print("  %s: %s -> %s" % (MIXINS_JSON, OLD_MIXIN, NEW_MIXIN))

    for locale, extra in LANG_ADD.items():
        name = LANG % locale
        text = blobs[name].decode("utf-8")
        data = json.loads(text)
        for key, value in extra.items():
            if key in data:
                raise SystemExit("%s already defines %s" % (name, key))
            data[key] = value
        blobs[name] = (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  %s: +%d key(s)" % (name, len(extra)))

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
