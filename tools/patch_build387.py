#!/usr/bin/env python3
"""
Build 387 - the lock panel leaves when the hands do, not when the door does.

The author's report: «сейчас реализован механизм блокировки ввода игрока, чтобы во время взлома ничего
не нажималось случайно. Я хочу чтобы он пропадал только тогда, когда все кнопки отжаты, а не когда
дверь взломана».

He was right, and it was one line: build 377/378 closed the panel on the same frame the last key went
down (`solved = true; DoorHack377.send(pos); this.onClose();`). The door opened on the server, and the
four to ten keys the player was still pressing were suddenly covered by nothing - they fell through to
the game, which is precisely the accident the blocking was added to prevent.

Now the win only **accepts** the hack:

  * `DoorHack377.send(pos)` still goes out immediately - the door opens when the lock is solved, as
    before;
  * the panel stays up, repainted to «ВЗЛОМ ПРИНЯТ · отпусти все клавиши», keeps swallowing every key,
    and closes itself the moment nothing is held;
  * what "nothing" means is checked twice, because either source alone lies: the panel records every key
    it saw go down (`pressed`) and clears it on release, and every frame `allReleased()` prunes that set
    against the hardware - a release that happens while the window is unfocused may never arrive - and
    checks the panel's own keys through `held()`, which is what catches a key that was already down when
    the panel opened and therefore never sent a press;
  * ESC before the hack is still the immediate way out (the door stays shut). After it, ESC has to be
    **held** about a second: a tap must not undo the protection, a stuck key must not trap the player in
    a panel for ever.

Nothing else in the build changes: same door logic, same graph, same keys, same clock.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build386-door-nodes"
NEW_VERSION = "0.8.0-build387-panel-release"
OUT_NAME = "settledlands-0.8.0-build387-panel-release.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/door/HackPanel377.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/HackPanel377.class": (
        "allReleased", "stillDown", "escDownAt", "ВЗЛОМ ПРИНЯТ"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 387 makes the lock panel's input block end when the player lets go, not when the door opens. "
    "Solving the panel still opens the door at once, but the panel itself now stays up with «ВЗЛОМ "
    "ПРИНЯТ» and keeps the keyboard until every key that was held has come back up - so a win can no "
    "longer press W, drop the held item or open the inventory by accident. The keys the panel was told "
    "about are tracked as a set and pruned against the hardware every frame, and the panel's own keys "
    "are measured directly, which also covers a key that was already down when the panel opened. ESC "
    "still closes an unsolved panel at once; a solved one needs ESC held for about a second, so a tap "
    "cannot undo the block and a stuck key cannot trap the player."
)
PATCHINFO = (
    "Сборка 387: блокировка ввода в панели взлома теперь снимается тогда, когда игрок отпустил клавиши, "
    "а не тогда, когда дверь взломана. Взлом по-прежнему сразу открывает дверь, но сама панель остаётся "
    "с надписью «ВЗЛОМ ПРИНЯТ» и держит клавиатуру, пока не отпущены все зажатые клавиши, — победа "
    "больше не может случайно нажать W, выбросить предмет или открыть инвентарь. Нажатые клавиши "
    "запоминаются в наборе и каждый кадр сверяются с железом, а клавиши самой панели читаются напрямую "
    "(это же закрывает случай, когда клавиша была зажата ещё до открытия панели). ESC по-прежнему сразу "
    "закрывает невзломанную панель, а у взломанной его надо удерживать около секунды — случайное "
    "нажатие защиту не снимает, а залипшая клавиша не запирает игрока в панели.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out387"

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
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to the base - nothing to replace" % name)
            for marker in MIXIN_MARKERS.get(name, ()):
                if marker.encode("utf-8") not in data:
                    raise SystemExit("%s does not contain %s - wrong class?" % (name, marker))
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
