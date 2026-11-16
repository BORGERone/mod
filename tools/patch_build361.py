#!/usr/bin/env python3
"""
Build 361 - the block editor's canvas, once more: no AI panel, no tabs, no line rows on a name.

Base 360. Build 360 came back from the game with three things still wrong on a block's graph, and one
request the author had made twice:

  * the «Автономный ИИ» card was still painted on a block's canvas. Build 359 cancelled the editor
    call that marks its state as synced; the card still appeared, so the state has a second door (the
    profile snapshot that arrives right after the window opens). Build 361 stops asking which door was
    used and cancels the drawing itself, plus the click that would hit it.
  * the two tabs of an NPC's editor were still in the top bar. Build 360 cleared their visible flag;
    build 361 also parks the buttons far above the window, where no frame can bring them back.
  * the name card still carried the stock rows of a «Реплика» node in the inspector (Текст узла,
    Триггер, отношения, очки), because the editor does not know the card's type and falls through to
    its line branch. Those rows and their labels are taken away; the card's inspector is the name and
    the colour, nothing else.
  * the block's name is the name and only the name: the dialogue window no longer puts the coordinates
    of the block under a nameless speaker (or under a named one) - a block without a name has an empty
    name, in the window and on its card.
  * the zone toggle of the «Цель» node is the same control the «В радиусе» node has: build 320's check
    box on the canvas is retired, the inspector row «Видимость радиуса: да/нет» of build 355 is the
    one toggle for both nodes.

Six classes replaced, one added:

    BlockTalk358                replaced: the window opens under the name alone, no coordinates
    BlockNameCard359            replaced: the card paints the name, or nothing when there is none
    BlockNameInspector359Mixin  replaced: the stock rows of the card are parked, ours are the whole panel
    BlockNoAi359Mixin           replaced: the AI panel is not drawn and not clicked on a block's canvas
    BlockNoTabs360Mixin         replaced: a tab button is parked out of the window, not only hidden
    NpcRadiusZoneUi320          replaced: the canvas «Зона» check box is retired (build 361)
    WidgetPark361               added: hidden and moved, the helper both of the parks use      (new)
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build360-block-name-fix"
NEW_VERSION = "0.8.0-build361-block-graph-cleanup"
OUT_NAME = "settledlands-0.8.0-build361-block-graph-cleanup.jar"

REMOVED = ()
REPLACED = (
    # the dialogue window: the name and nothing but the name
    "dev/settledlands/npc/BlockTalk358.class",
    "dev/settledlands/npc/BlockTalk358$Where.class",
    "dev/settledlands/npc/BlockTalk358$Open.class",
    # the card on the canvas: the name, or a blank line when the block has none
    "dev/settledlands/npc/client/BlockNameCard359.class",
    # the inspector of the card: the stock rows are parked, the name and the colour stay
    "dev/settledlands/mixin/BlockNameInspector359Mixin.class",
    # no «Автономный ИИ» panel on a block's canvas - not drawn, not clicked
    "dev/settledlands/mixin/BlockNoAi359Mixin.class",
    # no tabs on a block's graph - hidden and parked out of the window
    "dev/settledlands/mixin/BlockNoTabs360Mixin.class",
    # the zone toggle of the «Цель» node: the canvas box is retired, the inspector row stays
    "dev/settledlands/npc/client/NpcRadiusZoneUi320.class",
)
ADDED = (
    # hide a widget and move it out of the window - one helper for both parks
    "dev/settledlands/npc/client/WidgetPark361.class",
)

MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/npc/client/WidgetPark361.class": ("settledlands$move", "park"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}


CHANGELOG_EN = (
    "Build 361 finishes the block editor. The «Автономный ИИ» card is no longer painted on a block's "
    "canvas - build 359 stopped the editor from syncing its state and the card came back through the "
    "profile snapshot, so the drawing and its click are cancelled instead. The two tabs of an NPC's "
    "editor are parked out of the window as well as hidden, so they cannot come back. The name card's "
    "inspector no longer shows the stock rows the editor builds for a node type it does not know - the "
    "name row and the colour row are the whole panel. A block's name is the name and only the name: "
    "the dialogue window shows no coordinates, and a block without a name speaks under an empty one. "
    "The zone toggle of the «Цель» node is now the same inspector row the «В радиусе» node has, and "
    "the old canvas check box is gone. A body's editor is not touched."
)
PATCHINFO = (
    "Сборка 361: дочищен редактор блока. Карточка «Автономный ИИ» больше не рисуется на холсте "
    "блока — сборка 359 отменила синхронизацию её состояния, но карточка вернулась через снимок "
    "профиля, поэтому теперь отменяется сама отрисовка и клик по ней. Две вкладки редактора НПС "
    "убраны из окна совсем (спрятаны и увезены за пределы экрана), а не только помечены невидимыми. "
    "В инспекторе карточки «ИМЯ» больше нет штатных строк, которые редактор строит для неизвестного "
    "ему типа узла — остались только имя и цвет. Имя блока — это только имя: в окне диалога нет "
    "координат, а блок без имени говорит с пустым именем. Тумблер зоны у ноды «Цель» теперь та же "
    "строка инспектора, что и у ноды «В радиусе», а старый квадратик на холсте убран. Редактор тела "
    "не тронут.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out361"

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
