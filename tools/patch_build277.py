#!/usr/bin/env python3
"""Build 277 - the pursuit node gets a real, exclusive inspector.

Feedback on 276:
  * "the input field still cannot be typed into, except in the right window,
    and that one looks broken: text overlaps itself, the label says the trigger
    text, and anything typed into the text box turns the node into a trigger
    node" - the Build 276 panel was painted on top of the stock trigger
    inspector: a pursuit node is a TRIGGER node, so widgets() built the preset
    dropdown, the command box and the hint first. Hiding them afterwards does not
    work - hidden widgets still receive clicks in this screen - so the labels
    overlapped and typing went into the trigger command box.
  * "in the node list the row is yellow while the node is purple, and the plus
    sign is missing" - the row was drawn with a fixed gold colour and a plain
    label.

Repair:
  * NpcPursuitInspectorMixin277 (new, replaces NpcPursuitInspectorMixin276):
      - @Inject(HEAD) on widgets(): while the screen builds its widgets the
        selected pursuit node reports TYPE_NO_INSPECTOR (-1), which no stock
        inspector branch matches, so the trigger editor is never created at all;
      - @Inject(RETURN) on widgets(): the real type is restored and the pursuit
        panel is built - target type switch, target value field (a real EditBox,
        typed through the normal widget path), stop switch - with the standard
        unlink/delete buttons moved below the panel.
  * NpcPursuitGraphUiMixin272: card editing hooks removed; the mixin only adds
    the menu row and creates the node. All keystroke interception is gone.
  * PursuitGraphNode272: card redrawn from node.action - purple header, "Target:
    NPC/player", the value and the stop state, stock port dots; no in-card input.
  * lang: gui.settledlands.graph.pursuit_menu is now "+ Pursuit" / "+ Преследование".

Usage: python3 tools/patch_build277.py <base-276.jar> [--classes DIR] [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build276-pursuit-inspector"
NEW_VERSION = "0.8.0-build277-pursuit-native-inspector"
OUT_NAME = "settledlands-0.8.0-build277-pursuit-native-inspector.jar"

REPLACED = (
    "dev/settledlands/npc/client/PursuitGraphNode272.class",
    "dev/settledlands/npc/client/PursuitGraphNode272$MenuPoint.class",
    "dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class",
)
ADDED = (
    "dev/settledlands/mixin/NpcPursuitInspectorMixin277.class",
)
REMOVED = (
    "dev/settledlands/mixin/NpcPursuitInspectorMixin276.class",
)
MIXINS_JSON = "settledlands.mixins.json"
OLD_MIXIN = "NpcPursuitInspectorMixin276"
NEW_MIXIN = "NpcPursuitInspectorMixin277"
LANG = "assets/settledlands/lang/%s.json"
LANG_SET = {
    "ru_ru": {
        "gui.settledlands.graph.pursuit_menu": "+ Преследование",
    },
    "en_us": {
        "gui.settledlands.graph.pursuit_menu": "+ Pursuit",
    },
}

CHANGELOG_EN = (
    "Build 277: the pursuit node finally has an inspector of its own, and nothing "
    "of the trigger editor is left in it. A pursuit node is stored as a TRIGGER node, "
    "so Build 276 painted its panel on top of the trigger preset dropdown and command "
    "box - the text overlapped, and typing landed in the trigger command box, which "
    "turned the node into a trigger node. Now the stock inspector branch is not entered "
    "at all: while the screen builds its widgets the pursuit node reports a type no "
    "branch matches, and the type is restored before the pursuit panel is built - "
    "target type switch, target value field and stop switch, with unlink/delete moved "
    "below. In-card text editing is gone; the card is a purple node with its ports and "
    "two text lines. The create menu row is purple and reads \"+ Pursuit\". "
)
PATCHINFO = (
    "Сборка 277: у ноды преследования наконец свой инспектор, и редактора триггера в нём "
    "нет вообще. Нода преследования хранится как TRIGGER, поэтому в 276 панель рисовалась "
    "поверх списка пресетов и поля команды триггера — текст наслаивался, а ввод попадал в "
    "поле команды и превращал ноду в триггер. Теперь ветка штатного инспектора не "
    "выполняется: пока экран собирает виджеты, нода сообщает тип, который не совпадает ни с "
    "одной веткой, а тип возвращается до сборки панели преследования — переключатель типа "
    "цели, поле значения и тумблер завершения, кнопки отвязки и удаления переехали под неё. "
    "Ввода прямо на карточке больше нет: карточка — пурпурная нода с портами и двумя "
    "строками текста. Строка в меню создания пурпурная и называется «+ Преследование». "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--classes", default="build277-src/out")
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

    for locale, extra in LANG_SET.items():
        name = LANG % locale
        data = json.loads(blobs[name].decode("utf-8"))
        for key, value in extra.items():
            if key not in data:
                raise SystemExit("%s does not define %s" % (name, key))
            data[key] = value
        blobs[name] = (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        print("  %s: %d value(s) updated" % (name, len(extra)))

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
