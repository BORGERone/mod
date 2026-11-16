#!/usr/bin/env python3
"""
Build 360 - a block's name card (fixing the crash of 359) and no tabs on a block's graph.

Base 358. Build 359 was pulled: its inspector hook @Shadow-ed Screen#addRenderableWidget, which
Mixin looks up in the target class only, so every graph screen died on open with
"@Shadow method addRenderableWidget ... was not located in the target class". The card itself is
unchanged and the call is now made instead of shadowed - the same call the stock textBox makes.
Build 360 also takes the two unused tabs off a block's graph.

Two classes replaced, eight added:

    BlockTrigger351            replaced: a save drops the «Характеристики» card and the walk steps
                               over the name card in silence
    BlockTalk358               replaced: the window is opened under the block's name and colour
    BlockName359               added: the name card - what it is, where it sits, what it stores  (new)
    BlockNameCard359           added: the card on the canvas, in the block's colour                (new)
    BlockNameDraw359Mixin      added: drawNode hands a name node to the card                       (new)
    BlockNameInspector359Mixin added: the two inspector rows - the name and its colour             (new)
    BlockNoStats359Mixin       added: a block gets no «Характеристики» card                        (new)
    BlockNoAi359Mixin          added: a block gets no «Автономный ИИ» panel                        (new)
    BlockNameOne359Mixin       added: one name card per graph, like one stats card                 (new)
    BlockNoTabs360Mixin        added: a block's graph has no tabs (build 360)                      (new)

Build 358 gave a block a voice but no name: the dialogue window showed the coordinates of the block
where the speaker goes. A block is not a body and has no card of its own, so this build gives it one -
the way a body has its «Характеристики» card: a single card that exists on every block canvas without
being added from the palette, carrying the name and the colour it is written in. Both live in fields
every node already saves, so a name survives a save with no new key anywhere.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build358-block-talk"
NEW_VERSION = "0.8.0-build360-block-name-fix"
OUT_NAME = "settledlands-0.8.0-build360-block-name-fix.jar"

REMOVED = ()
REPLACED = (
    # a block's save drops the stats card; a block's walk steps over the name card
    "dev/settledlands/npc/BlockTrigger351.class",
    # the dialogue window opens under the block's name, in the block's colour
    "dev/settledlands/npc/BlockTalk358.class",
    "dev/settledlands/npc/BlockTalk358$Where.class",
    "dev/settledlands/npc/BlockTalk358$Open.class",
)
ADDED = (
    # the card itself: what it is, what it stores, how it is found
    "dev/settledlands/npc/BlockName359.class",
    # the card on the canvas
    "dev/settledlands/npc/client/BlockNameCard359.class",
    # the four hooks: draw, inspect, no stats, no AI, one card
    "dev/settledlands/mixin/BlockNameDraw359Mixin.class",
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class",
    "dev/settledlands/mixin/BlockNoStats359Mixin.class",
    "dev/settledlands/mixin/BlockNoAi359Mixin.class",
    "dev/settledlands/mixin/BlockNameOne359Mixin.class",
    # a block's graph has no tabs
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
)

MIXINS = ("BlockNameOne359Mixin",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ("BlockNameDraw359Mixin", "BlockNameInspector359Mixin",
                 "BlockNoStats359Mixin", "BlockNoAi359Mixin", "BlockNoTabs360Mixin")
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/BlockNameDraw359Mixin.class": (
        "BlockNameCard359", "settledlands$drawBlockNameCard"),
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class": (
        "NpcUi$NpcDropdown", "settledlands$blockNameRows"),
    "dev/settledlands/mixin/BlockNoStats359Mixin.class": (
        "NpcStatsGraphUi", "settledlands$blockNameInsteadOfStats"),
    "dev/settledlands/mixin/BlockNoAi359Mixin.class": (
        "NpcAutoAiGraphUi255", "settledlands$blockWithoutAi"),
    "dev/settledlands/mixin/BlockNameOne359Mixin.class": (
        "NpcStatsGraph", "settledlands$singleNameCard"),
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class": (
        "gui.settledlands.graph.tab_", "settledlands$blockHasNoTabs"),
}

LANG_SET = {
    "gui.settledlands.name.title": "ИМЯ",
    "gui.settledlands.name.field": "Имя в диалогах",
    "gui.settledlands.name.color": "Цвет имени",
    "gui.settledlands.name.color.0": "Белый",
    "gui.settledlands.name.color.1": "Кремовый",
    "gui.settledlands.name.color.2": "Золотой",
    "gui.settledlands.name.color.3": "Серый",
    "gui.settledlands.name.empty": "— имя не задано —",
    "gui.settledlands.name.hint": "этим именем блок говорит в диалоге",
}
LANG_SET_EN = {
    "gui.settledlands.name.title": "NAME",
    "gui.settledlands.name.field": "Name in dialogues",
    "gui.settledlands.name.color": "Name colour",
    "gui.settledlands.name.color.0": "White",
    "gui.settledlands.name.color.1": "Cream",
    "gui.settledlands.name.color.2": "Gold",
    "gui.settledlands.name.color.3": "Grey",
    "gui.settledlands.name.empty": "— no name yet —",
    "gui.settledlands.name.hint": "the block speaks under this name",
}

LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 360 fixes the crash of build 359 and finishes the block editor. The name card that 359 "
    "added is unchanged - a block graph carries one card holding the name the block speaks under and "
    "the colour it is written in - but the inspector row that shows the colour dropdown registered its "
    "widget through a mixin shadow that Mixin cannot resolve, so opening any graph screen crashed the "
    "client. It is now registered with the same call the stock text box makes, and every screen opens "
    "again. On top of that, a block's graph no longer shows the two tabs it can never use: a block has "
    "one graph, the walking and animation tabs of an NPC's editor were only ever empty, and they are "
    "gone from the strip instead of refusing a click. The «Характеристики» card and the «Автономный "
    "ИИ» panel stay off a block's graph, and a body's editor is not touched."
)
PATCHINFO = (
    "Сборка 360: исправлен вылет сборки 359 и дочищен редактор блока. Карточка «ИМЯ» не изменилась — "
    "у графа блока одна карточка с именем, которым блок говорит в диалоге, и цветом этого имени, — но "
    "строка инспектора с выпадающим списком цвета регистрировала виджет через @Shadow, который Mixin "
    "не находит (он смотрит только в сам класс-цель), и открытие любого графа валило клиент. Теперь "
    "виджет регистрируется тем же вызовом, что и штатное текстовое поле, и все экраны открываются. "
    "Плюс у графа блока больше нет двух вкладок, которые блоку не нужны: у блока один граф, вкладки "
    "ходьбы и анимации у него всегда были пустыми, и они убраны из полосы, а не «отвечают отказом». "
    "Карточки «Характеристики» и «Автономный ИИ» на графе блока по-прежнему не появляются, редактор "
    "тела не тронут.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out360"

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

    for path, table in LANG_CHANGE.items():
        if path not in blobs:
            raise SystemExit("base jar has no %s" % path)
        strings = json.loads(blobs[path].decode("utf-8"))
        for key, (before, after) in table.items():
            if strings.get(key) != before:
                raise SystemExit("%s: %s is %r, expected %r"
                                 % (path, key, strings.get(key), before))
            strings[key] = after
            print("  %s: %s = %r" % (path.rsplit("/", 1)[-1], key, after))
        blobs[path] = (json.dumps(strings, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    mixins = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common = mixins.setdefault("mixins", [])
    client = mixins.setdefault("client", [])
    for name in MIXINS_REMOVED:
        if name not in common:
            raise SystemExit("%s is not in the common list" % name)
        common.remove(name)
    for name in CLIENT_REMOVED:
        if name not in client:
            raise SystemExit("%s is not in the client list" % name)
        client.remove(name)
    for name in MIXINS_FIRST:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.insert(0, name)
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be a client mixin" % name)
        if name not in common:
            common.append(name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be a common mixin" % name)
        if name not in client:
            client.append(name)
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, ensure_ascii=False, indent=2)
                                         + "\n").encode("utf-8")
    print("  mixins.json: %d common, %d client (first: %s)"
          % (len(common), len(client), common[0]))

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
