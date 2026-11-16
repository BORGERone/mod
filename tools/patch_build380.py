#!/usr/bin/env python3
"""
Build 380 - the client watches every solved panel and reports what became of it, and the packet handler runs the way the mod's working payloads run.

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

BASE_VERSION = "0.8.0-build379-door-hack-report"
NEW_VERSION = "0.8.0-build380-door-hack-watch"
OUT_NAME = "settledlands-0.8.0-build380-door-hack-watch.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/DoorEvents368.class",
    "dev/settledlands/door/DoorNet375.class",
    # Build 371's guard closes every locked door that is open - a cracked door would slam shut on the
    # next tick, so the guard has to know which doors a player just opened.
    "dev/settledlands/door/DoorGuard371.class",
    # Build 378: the panel itself - letters instead of digits, and a rolling key instead of a reset.
    "dev/settledlands/door/HackPanel377.class",
    "dev/settledlands/door/DoorHack377.class",
)
ADDED = (
    # Build 380: the client-side watcher that says what became of a solved panel.
    "dev/settledlands/door/DoorHackWatch380.class",
)
# DoorUse373Mixin is already a common mixin in the base jar; listing it asserts that it still is, and
# the loop below never duplicates an entry.
MIXINS = ("DoorUse373Mixin",)
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"


CHANGELOG_EN = (
    "Build 380 watches a solved panel from the client: if the door is still shut after 1.2 s the request "
    "is sent again, after 4 s the log says the server never opened it, and if it opens the log says so. "
    "The client-to-server handler now runs through context.enqueueWork exactly like the mod's own working "
    "payloads, and the last silent refusal (the block is not a door on the server) is written down. "
    "Build 379 answers 'the panel was solved and the door did not open': the server writes one line when "
    "the packet arrives and one when it is refused, the panel writes one when it opens and one when it is "
    "solved, and the door is opened from the position in the key even when this level spells that "
    "position's dimension differently (a door in a sub-level or a pocket dimension). Build 378 makes the "
    "lock panel read the author's way: the keycaps are letters (Q W E R T Y U I O P) "
    "instead of digits, and the clock no longer throws an attempt away - every five seconds one key of "
    "the row changes to a different letter while everything the player holds stays held. "
    "Build 377 turns a locked door into a lock to crack: clicking one without the mod key opens a panel "
    "of four to ten keycaps from the number row, and the door opens only when every one of them is held "
    "down at the same moment. The keyboard is read directly every frame, so the count is the real one. "
    "The lock itself is unchanged - the door is still closed to a plain click, still unbreakable, still "
    "open to NPCs, and a failed or abandoned panel changes nothing."
)
PATCHINFO = (
    "Сборка 380 следит за собранной панелью со стороны клиента: если через 1,2 с дверь закрыта, запрос "
    "уходит повторно; через 4 с в лог пишется, что сервер её так и не открыл; если открыл — тоже "
    "пишется. Обработчик пакета теперь выполняется через context.enqueueWork, как у собственных рабочих "
    "пакетов мода, и последний молчаливый отказ (на сервере в этой точке не дверь) тоже пишется в лог. "
    "Сборка 379 отвечает на «панель собрал, а дверь не открылась»: сервер пишет строку, когда пакет "
    "пришёл, и строку, когда отказал; панель пишет, когда открылась и когда собрана; дверь теперь "
    "открывается по позиции из ключа, даже если это измерение называет её иначе (дверь в подуровне или "
    "карманном измерении). "
    "Сборка 378: в панели взлома теперь буквы (Q W E R T Y U I O P) вместо цифр, а таймер больше не "
    "сбрасывает набор - каждые 5 секунд одна клавиша ряда меняется на другую, зажатые клавиши остаются "
    "зажатыми. "
    "Сборка 377 добавляет взлом: клик по запертой двери без ключа открывает панель из 4-10 кнопок "
    "цифрового ряда, и дверь открывается только когда все они зажаты одновременно. Клавиатура "
    "опрашивается каждый кадр, поэтому счёт настоящий. Сам замок не меняется: дверь по-прежнему не "
    "открывается обычным кликом, не ломается и пропускает НПС, а провал или закрытая панель ничего не "
    "меняют.\n"
)

def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out380"

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
