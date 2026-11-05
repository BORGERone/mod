#!/usr/bin/env python3
"""
Build 382 - a door carries its own graph. The author's build-380 log named the bug outright:

  java.lang.NoSuchMethodError: 'net.minecraft.world.level.block.state.BlockState
    net.minecraft.world.level.block.state.BlockState.setValue(
      net.minecraft.world.level.block.state.properties.Property, java.lang.Comparable)'

In 1.21.1 `setValue` is declared on `StateHolder<O, S>` as
`<T extends Comparable<T>, V extends T> S setValue(Property<T>, V)`. `S` is unbounded, so the erased
return type the game publishes is **Object**, not BlockState. The mirror in `api-src` declared it as
returning BlockState, so `Doors368.setOpen` compiled an invokevirtual that resolves to nothing - and
because every door write in this mod goes through `Doors368.setOpen`, **no door this mod opened has ever
moved**: the door hack, the guide pass of build 368 and the guard of build 371 all threw here and were
swallowed by their own try/catch. One descriptor, three features.

Fixed by declaring the mirror the way the game publishes it (raw `Property`, `Comparable`, returns
`Object`) and casting the result with `instanceof BlockState`.

The author's request: "мини игра, при попытке открыть дверь будет мини игра со взломом - появится окно, в
котором в ряд будет от 4 до 10 кнопок, и нужно будет все их одновременно нажать, чтобы открыть дверь". And
on the input: the buttons are pressed **on the keyboard**; on a failed attempt **nothing** happens to the
door - it stays locked and may be tried again at once.

  * `DoorHack377` - one payload, `settledlands:door_hack` (client to server), a single string field: the
    door key the store already uses, `minecraft:overworld@12,64,-30`. The server rebuilds the position from
    it, so the dimension is part of the check, then asks two things: is this door still locked **on the
    server**, and is the player still standing next to it. Only then does it open the door - the lock stays.
  * `HackPanel377` - the panel itself, a 1.21.1 `Screen`: a row of 4 to 10 keycaps from the number row,
    each one lit while that key is held. `InputConstants.isKeyDown(window, key)` is asked every frame, so
    "all of them at once" is read from the keyboard and not from the events the panel happened to receive -
    a key already held when the panel opened, or released while the window lost focus, cannot lie. Fifteen
    seconds per attempt; when it runs out the buttons clear and nothing else happens.
  * `DoorNet375` - the second registration, `registrar("1").playToServer(...)`, guarded on its own so a
    refused hack packet costs the panel, not the launch and not the lock sync.
  * `DoorEvents368` - the client half of the door click: a door this client knows to be locked, and no key
    in hand, now opens the panel instead of only refusing. The click is still refused, so the door does not
    move while the panel is up.

Signatures: every call the panel makes is one the base mod's own screens already make - `Screen#render/
renderBackground/keyPressed/keyReleased/isPauseScreen/onClose`, `GuiGraphics.fill/drawCenteredString`,
`Minecraft#setScreen/getWindow/screen`, `InputConstants.isKeyDown(Minecraft.getInstance().getWindow()
.getWindow(), key)` (`MobilityWandScreen`, `NpcDialogueGraphScreen`), and for the packet
`PacketDistributor#sendToServer`, `PayloadRegistrar#playToServer`, `IPayloadContext#player`
(`GuideNet`, `CutsceneRecTogglePayload`).

Base 376. Two classes replaced, two added.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build381-door-open"
NEW_VERSION = "0.8.0-build382-door-graph"
OUT_NAME = "settledlands-0.8.0-build382-door-graph.jar"

REMOVED = ()
REPLACED = (
    # The three classes the door graph reads or feeds, and the three the editor borrows.
    "dev/settledlands/door/DoorSync375.class",
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/npc/BlockTriggerEvents351.class",
    "dev/settledlands/npc/client/BlockMenu363.class",
    "dev/settledlands/mixin/BlockMenu363Mixin.class",
)
ADDED = (
    "dev/settledlands/door/DoorGraph382.class",
    "dev/settledlands/door/DoorGraph382Store.class",
    "dev/settledlands/door/DoorGraph382Events.class",
    "dev/settledlands/npc/client/DoorGraphClient382.class",
    "dev/settledlands/mixin/NpcDoorSave382Mixin.class",
    "dev/settledlands/mixin/NpcDoorOpen382Mixin.class",
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class",
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class",
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class",
)
MIXINS = ("NpcDoorSave382Mixin",)
MIXINS_FIRST = ("NpcDoorSave382Mixin",)
MIXINS_REMOVED = ()
CLIENT_MIXINS = (
    "NpcDoorOpen382Mixin", "NpcDoorGraph382Mixin", "NpcDoorCard382Mixin", "NpcDoorRows382Mixin",
)
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/mixin/NpcDoorSave382Mixin.class": (
        "applyConfig", "isDoorEditorId", "DoorGraph382"),
    "dev/settledlands/mixin/NpcDoorOpen382Mixin.class": (
        "open", "NpcConfigScreen", "DoorGraphClient382"),
    "dev/settledlands/mixin/NpcDoorGraph382Mixin.class": (
        "mouseClicked", "switchTab", "addAt"),
    "dev/settledlands/mixin/NpcDoorCard382Mixin.class": (
        "drawNode", "doorCard", "zoom"),
    "dev/settledlands/mixin/NpcDoorRows382Mixin.class": (
        "widgets", "slider", "NpcSlider"),
    "dev/settledlands/door/DoorGraph382Store.class": (
        "settledlands_door_graphs", "Doors", "computeIfAbsent"),
    "dev/settledlands/door/DoorGraph382.class": (
        "lowerKey", "NpcNet", "defaultGraph"),
    "dev/settledlands/door/DoorGraph382Events.class": (
        "RightClickBlock", "isWand", "HIGHEST"),
    "dev/settledlands/npc/client/DoorGraphClient382.class": (
        "DialogueGraph", "NpcDialogueGraphScreen", "openEditor"),
}

LANG_SET = {
    "gui.settledlands.door.node": "Дверь",
    "gui.settledlands.door.count": "Кол-во чисел: %s",
    "gui.settledlands.door.speed": "Скорость: %s с",
    "gui.settledlands.door.sealed": "Закрыта (только взлом)",
    "gui.settledlands.door.unsealed": "Открыта (обычный клик)",
    "gui.settledlands.door.seal": "Состояние двери",
    "gui.settledlands.door.palette": "У двери доступна одна нода: Дверь",
    "gui.settledlands.door.one_tab": "У двери один граф: вкладки распорядка и общего недоступны",
    "gui.settledlands.door.added": "Дверь добавлена",
    "message.settledlands.door.graph_open": "Граф двери · %s",
    "message.settledlands.door.graph_saved": "Граф двери сохранён · %s · %s",
    "message.settledlands.door.graph_cleared": "Граф двери удалён · %s",
    "message.settledlands.door.graph_full": "Слишком много дверей с графом: %s",
}
LANG_SET_EN = {
    "gui.settledlands.door.node": "Door",
    "gui.settledlands.door.count": "Keys: %s",
    "gui.settledlands.door.speed": "Speed: %s s",
    "gui.settledlands.door.sealed": "Closed (hack only)",
    "gui.settledlands.door.unsealed": "Open (ordinary click)",
    "gui.settledlands.door.seal": "Door state",
    "gui.settledlands.door.palette": "A door has one node: Door",
    "gui.settledlands.door.one_tab": "A door has one graph: the schedule and shared tabs are not available",
    "gui.settledlands.door.added": "Door added",
    "message.settledlands.door.graph_open": "Door graph · %s",
    "message.settledlands.door.graph_saved": "Door graph saved · %s · %s",
    "message.settledlands.door.graph_cleared": "Door graph removed · %s",
    "message.settledlands.door.graph_full": "Too many doors with a graph: %s",
}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 382 gives a door its own graph. Right click a door with the NPC wand and the stock graph "
    "editor opens on that door - the same canvas, wires, undo, copy and save the NPC and block graphs "
    "use - and the door is created with one node in it: a Door node with two sliders (how many keys "
    "its hack panel asks for, 4 to 10, and how often one of them changes, 1 to 10 seconds) and a "
    "toggle that says whether the door is closed (only a solved panel opens it) or open (an ordinary "
    "door again). The toggle is the lock of build 368, so the refusal, the unbreakable door, the 8 s "
    "guard and the lock sync all follow unchanged. The panel reads the two numbers per door. The "
    "palette of a door has exactly one row, the graph lives in world data keyed by the lower half of "
    "the door, and a block graph can no longer be opened on a door. Build 381 fixed why no door ever "
    "opened: the mod wrote a door's OPEN property through a method shape the game does not have "
    "(setValue returning BlockState instead of Object), so every open since build 368 threw "
    "NoSuchMethodError."
)
PATCHINFO = (
    "Сборка 382 даёт двери собственный граф: ПКМ жезлом настройки НПС по двери открывает штатный "
    "редактор графов, и в новой двери уже стоит нода «Дверь» - два ползунка (сколько клавиш просит "
    "панель взлома у этой двери, 4-10, и как часто одна из них меняется, 1-10 с) и тумблер "
    "«закрыта/открыта». «Закрыта» - дверь открывается только собранной панелью, «Открыта» - обычная "
    "дверь. Тумблер работает через замок 368-й сборки, поэтому отказ обычному клику, невозможность "
    "сломать, сторож на 8 секунд и синхронизация замков остались как были. Панель взлома читает эти "
    "два числа для каждой двери отдельно. В палитре двери одна строка, граф хранится в данных мира "
    "по нижней половине двери, а граф блока на двери больше не открывается. "
    "Сборка 381 исправляет причину, по которой не открывалась ни одна дверь: мод писал свойство OPEN "
    "через метод, которого в игре нет (setValue с возвратом BlockState вместо Object), поэтому каждое "
    "открытие с 368-й сборки падало с NoSuchMethodError.\n"
)

def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out382"

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

    for name in EXTRA:
        src = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / EXTRA_DIR / name.split("assets/settledlands/", 1)[1]
        if not src.is_file():
            raise SystemExit("missing asset: %s" % src)
        data = src.read_bytes()
        if name in blobs:
            raise SystemExit("base jar already has %s" % name)
        order.append(name)
        blobs[name] = data
        print("  asset: %s (%d bytes)" % (name, len(data)))

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
