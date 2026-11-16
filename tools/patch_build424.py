#!/usr/bin/env python3
"""
Build 424 - Teleport node («Телепорт») across NPC, Block, Door and Chest graph editors.

Adds a dedicated «Телепорт» graph node stored inside stock `NpcDialogueGraph.TRIGGER` (type 2,
`special == 0`) with `action = "trigger:teleport:<dimensionId>|<x>|<y>|<z>"`. Empty coordinate
fields default to 0 at execution and display (`EditBox` shows a "0" hint), and worlds include
Overworld, The Nether, The End, and SettledLands Purgatory (`settledlands:purgatory`) while
preserving any unknown custom dimension ID already saved in a graph.

Base 423. Four classes replaced (BlockMenu363 + Block/Door/Chest palette mixins), nine added
(TeleportNode424, TeleportActions424, TeleportCard424, and five Teleport*Mixin424 classes), two
common mixins + three client mixins registered, thirteen language keys added.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build423-turn-and-cell"
NEW_VERSION = "0.8.0-build424-teleport"
OUT_NAME = "settledlands-0.8.0-build424-teleport.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class",
)
ADDED = (
    "dev/settledlands/npc/TeleportNode424.class",
    "dev/settledlands/npc/TeleportNode424$Parsed.class",
    "dev/settledlands/npc/TeleportActions424.class",
    "dev/settledlands/npc/client/TeleportCard424.class",
    "dev/settledlands/mixin/TeleportActionsMixin424.class",
    "dev/settledlands/mixin/TeleportWiringMixin424.class",
    "dev/settledlands/mixin/TeleportContextMenuMixin424.class",
    "dev/settledlands/mixin/TeleportGraphUiMixin424.class",
    "dev/settledlands/mixin/TeleportInspectorMixin424.class",
)

MIXINS = (
    "TeleportActionsMixin424",
    "TeleportWiringMixin424",
)
CLIENT_MIXINS = (
    "TeleportContextMenuMixin424",
    "TeleportGraphUiMixin424",
    "TeleportInspectorMixin424",
)

MARKERS = {
    "dev/settledlands/npc/TeleportNode424.class": (
        "trigger:teleport:",
        "minecraft:overworld",
        "settledlands:purgatory",
    ),
    "dev/settledlands/npc/TeleportNode424$Parsed.class": ("Parsed",),
    "dev/settledlands/npc/TeleportActions424.class": ("teleportTo", "tryParse"),
    "dev/settledlands/npc/client/TeleportCard424.class": ("drawMenuRow", "worldPicker"),
    "dev/settledlands/npc/client/BlockMenu363.class": ("isTeleportRow", "typeForRow"),
    "dev/settledlands/mixin/TeleportActionsMixin424.class": ("settledlands$runTeleportSingle424",),
    "dev/settledlands/mixin/TeleportWiringMixin424.class": ("settledlands$connectTeleport424",),
    "dev/settledlands/mixin/TeleportContextMenuMixin424.class": ("settledlands$teleportRowAt424",),
    "dev/settledlands/mixin/TeleportGraphUiMixin424.class": ("settledlands$createTeleportNode424",),
    "dev/settledlands/mixin/TeleportInspectorMixin424.class": ("settledlands$buildTeleportInspector424",),
    "dev/settledlands/mixin/NpcBlockGraph351Mixin.class": ("isTeleportRow",),
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class": ("isTeleportRow",),
    "dev/settledlands/mixin/NpcChestPalette396Mixin.class": ("isTeleportRow",),
}

LANG_SET_RU = {
    "gui.settledlands.teleport.menu": "+ Телепорт",
    "gui.settledlands.teleport.title": "Телепорт",
    "gui.settledlands.teleport.added": "Узел «Телепорт» добавлен",
    "gui.settledlands.teleport.world": "Мир",
    "gui.settledlands.teleport.world.overworld": "Обычный мир",
    "gui.settledlands.teleport.world.nether": "Незер",
    "gui.settledlands.teleport.world.end": "Край",
    "gui.settledlands.teleport.world.purgatory": "Чистилище",
    "gui.settledlands.teleport.coords_hint": "Координаты (пусто = 0)",
    "gui.settledlands.teleport.x": "X",
    "gui.settledlands.teleport.y": "Y",
    "gui.settledlands.teleport.z": "Z",
    "gui.settledlands.teleport.card_hint": "Телепортирует игрока",
}
LANG_SET_EN = {
    "gui.settledlands.teleport.menu": "+ Teleport",
    "gui.settledlands.teleport.title": "Teleport",
    "gui.settledlands.teleport.added": "Added Teleport node",
    "gui.settledlands.teleport.world": "World",
    "gui.settledlands.teleport.world.overworld": "Overworld",
    "gui.settledlands.teleport.world.nether": "The Nether",
    "gui.settledlands.teleport.world.end": "The End",
    "gui.settledlands.teleport.world.purgatory": "Purgatory",
    "gui.settledlands.teleport.coords_hint": "Coordinates (empty = 0)",
    "gui.settledlands.teleport.x": "X",
    "gui.settledlands.teleport.y": "Y",
    "gui.settledlands.teleport.z": "Z",
    "gui.settledlands.teleport.card_hint": "Teleports player",
}

CHANGELOG_EN = (
    "Build 424 adds the Teleport node across NPC, Block, Door and Chest graph editors. Right-clicking "
    "the canvas or dragging a wire opens the menu with '+ Teleport'; the card displays the target "
    "dimension and X/Y/Z coordinates, and the inspector lets you choose Overworld, The Nether, The End, "
    "or SettledLands Purgatory alongside X/Y/Z coordinate boxes where blank inputs default to 0. "
    "Execution teleports only ServerPlayer safely (clearing fall distance, fire and velocity) and wires "
    "into sequential node chains including If/Random branches and chained Triggers."
)
PATCHINFO = (
    "Сборка 424: узел «Телепорт» во всех графах (НИП, блок, дверь, сундук).\n"
    "  * Создание из контекстного меню («+ Телепорт») или перетягиванием провода;\n"
    "  * Инспектор: выпадающий список мира (Обычный мир, Незер, Край, Чистилище) и поля X/Y/Z\n"
    "    (пустое поле отображается с подсказкой 0 и считается за 0 при телепортации);\n"
    "  * Безопасный перенос только игрока (сброс падения, огня и скорости), поддержка цепочек.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default=None, help="directory containing compiled build 424 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = pathlib.Path(args.classes) if args.classes else (root / "out424")
    if not srcroot.is_dir():
        raise SystemExit("no such compiled classes dir: %s" % srcroot)

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

    for name in REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name not in blobs:
            raise SystemExit("base jar has no entry: %s" % name)
        if blobs[name] == data:
            raise SystemExit("%s is byte-identical to base - why is it in REPLACED?" % name)
        print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for name in ADDED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("%s already in base jar - put it in REPLACED" % name)
        for marker in MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        print("  added   : %s (%d bytes)" % (name, len(data)))
        order.append(name)
        blobs[name] = data

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

    for path, table in (("assets/settledlands/lang/ru_ru.json", LANG_SET_RU),
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
    for name in MIXINS:
        if name in client:
            raise SystemExit("%s must not be in client list" % name)
        if name not in common:
            common.append(name)
            print("  mixins  : +%s" % name)
    for name in CLIENT_MIXINS:
        if name in common:
            raise SystemExit("%s must not be in common list" % name)
        if name not in client:
            client.append(name)
            print("  client  : +%s" % name)
    if client[0] != "NpcDropdownFirst333":
        raise SystemExit("NpcDropdownFirst333 must remain first in client mixins!")
    blobs["settledlands.mixins.json"] = (json.dumps(mixins, indent=2) + "\n").encode("utf-8")

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
        blobs["settledlands-patch-info.txt"] = PATCHINFO.encode("utf-8")

    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            zf.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    (outdir / (OUT_NAME + ".sha256")).write_text("%s  %s\n" % (digest, OUT_NAME))
    print("wrote %s (%d bytes, %d entries, sha256=%s)"
          % (out_path, out_path.stat().st_size, len(order), digest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
