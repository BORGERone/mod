#!/usr/bin/env python3
"""
Build 384 - the values of a door node are actually saved.

The author's report after build 383: «Изменения в графе не сохраняются и в следствии не влияют на дверь.»
Not a lost packet and not a lost hook: the values were written into fields the stock serialiser does
not write for our node type.

    // dev/settledlands/npc/NpcDialogueGraph, save(), straight from the shipped jar
    if (n.type == TIMER)  { v.putInt("TimerInterval", n.timerInterval); v.putBoolean("TimerRepeat", ...); }
    if (n.type == RANDOM) { v.putInt("RandomMin", n.randomMin); ... }

Build 382 kept «Кол-во чисел» in `randomMin`, «Скорость» in `timerInterval` and «Закрыта» in
`timerRepeat`, and a door node is type 40 - neither TIMER nor RANDOM. So the editor's own `save()`
handed the server a graph whose door node carried the field defaults (1, 10, true), the server stored
that, and every save rewrote the door as 4 keys / 10 s / sealed. Nothing threw and nothing logged,
which is why it took a game report to find it.

The fix is the field choice, not a new packet: the three controls now live in `Node#varValue`,
`Node#varCompare` and `Node#varOp` - the three ints the serialiser writes and reads for **every** node,
whatever its type (they belong to «Переменная» nodes, and the editor's rows for a variable node are
never built for our type: `NpcDoorRows382Mixin` parks every stock row). The mapping is in
`DoorGraph382.FIELD_NOTE`, next to the code that depends on it.

A door node that has never been written (`varValue == 0` - impossible for a written node, the count is
4..10) is filled by the new `DoorGraph382#adopt`, on open and on save, with the defaults and with the
lock the door is in right now: a graph saved by 382/383 opens as 6 keys / 5 s / its real state instead
of being written back blank, and a closed door is not unlocked by opening its graph.

Nothing else changes: same node, same rows, same card, same lock, same payload.

New gate: `tools/door_save_check.py` - it fails if the door node goes back to a field the serialiser
writes only for some types, and it re-reads the serialiser itself (decompiled source of the base mod,
committed in `reference/`, cross-checked against the jar) so the day that code changes the gate says so
instead of the author.

Base 383. One class replaced.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build383-door-graph-fix"
NEW_VERSION = "0.8.0-build384-door-graph-save"
OUT_NAME = "settledlands-0.8.0-build384-door-graph-save.jar"

REMOVED = ()
REPLACED = (
    # The one class that decides where the door node keeps its three controls and how they are read.
    "dev/settledlands/door/DoorGraph382.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()
CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/DoorGraph382.class": (
        "adopt", "varValue", "varCompare", "varOp", "stripCards"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 384 makes the values of a door node actually save, which build 383 did not: the three "
    "controls were kept in node fields the stock graph serialiser writes only for TIMER and RANDOM "
    "nodes, so every save dropped them and the door was rewritten as 6 keys, 5 seconds and sealed - "
    "the author's report was «the changes in the graph are not saved and therefore do not affect the "
    "door». The three values now live in Node#varValue, #varCompare and #varOp, the three ints the "
    "serialiser writes and reads for every node type, with the mapping kept in DoorGraph382.FIELD_NOTE "
    "next to the code that reads it. A door node written by 382/383 (varValue 0, which no written node "
    "can be) is adopted on open and on save from the defaults and from the lock the door is in, so an "
    "old graph opens on something real and a closed door is not unlocked by opening its graph. Nothing "
    "else changes: same node, same two sliders, same toggle, same lock, same payload."
)
PATCHINFO = (
    "Сборка 384 исправляет то, из-за чего в 383-й значения двери не сохранялись: три контроля лежали "
    "в полях ноды, которые штатный сериализатор графа пишет только для узлов TIMER и RANDOM, поэтому "
    "каждое сохранение их выбрасывало и дверь переписывалась как 6 клавиш, 5 секунд и «закрыта» - "
    "отсюда и было «изменения в графе не сохраняются и вследствие не влияют на дверь». Теперь три "
    "значения живут в Node#varValue, #varCompare и #varOp - тех трёх целых, которые сериализатор "
    "пишет и читает для любого типа ноды, а соответствие полей записано в DoorGraph382.FIELD_NOTE "
    "рядом с кодом, который их читает. Нода, сохранённая сборками 382/383 (varValue = 0, чего у "
    "записанной ноды быть не может), восстанавливается при открытии и при сохранении из значений по "
    "умолчанию и из настоящего замка двери: старая дверь открывается с реальными числами, а закрытая "
    "не отпирается от того, что её граф открыли. Больше ничего не менялось: та же нода, те же два "
    "ползунка, тот же тумблер, тот же замок, тот же пакет.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME
    srcdir = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out384"

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
