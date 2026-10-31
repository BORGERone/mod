#!/usr/bin/env python3
"""Build 293 - the tp-shortcut guard, patched in the class itself.

Reported: `tp @npc ~ ~5 ~` in a trigger node ran as `tp @s <uuid> ~ ~5 ~`
(NpcActions fills @npc with the NPC uuid, then the stock guard sees a leading
hex digit, decides "coordinates only" and inserts @s).

Build 292 tried to redirect String.replaceFirst in the running class and the
user's log shows the insertion still happening, so this build stops relying on
an injection and rewrites the guard pattern itself:

    old  ^(tp|teleport)\\s+([~^\\-0-9].*)          (30 chars, "first char counts")
    new  ^(tp|teleport)\\s+[~^\\-0-9.]*(\\s.*)?    ("the whole first token counts")

A class file has no absolute file offsets - everything is parsed sequentially -
so the Utf8 entry can simply be given a new length; nothing else in the class
moves. The change is one constant-pool string, no new class, no mixin: the two
String.matches calls in `command` and `commandForEntity` are the only readers.

Usage: python3 tools/patch_build293.py <base-291.jar> [--out DIR]
"""

import argparse
import hashlib
import json
import pathlib
import struct
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile  # noqa: E402

BASE_VERSION = "0.8.0-build291-launch-fix"
NEW_VERSION = "0.8.0-build293-tp-target-guard"
OUT_NAME = "settledlands-0.8.0-build293-tp-target-guard.jar"

TARGET_CLASS = "dev/settledlands/npc/NpcActions.class"
OLD_PATTERN = b"^(tp|teleport)\\s+([~^\\-0-9].*)"
NEW_PATTERN = b"^(tp|teleport)\\s+[~^\\-0-9.]*(\\s.*)?"


def cp_entries(data):
    """Yield (index, offset_of_tag, offset_after_entry, tag, value) for the pool."""
    count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    index = 1
    while index < count:
        start = pos
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack(">H", data[pos:pos + 2])[0]
            value = data[pos + 2:pos + 2 + length]
            pos += 2 + length
            yield index, start, pos, tag, value
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
            yield index, start, pos, tag, None
        elif tag in (5, 6):
            pos += 8
            yield index, start, pos, tag, None
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
            yield index, start, pos, tag, None
        elif tag == 15:
            pos += 3
            yield index, start, pos, tag, None
        else:
            raise SystemExit("unknown constant-pool tag %d at entry %d" % (tag, index))
        index += 1


def patch_constant(data, old, new):
    hits = [(idx, start, end) for idx, start, end, tag, value in cp_entries(data)
            if tag == 1 and value == old]
    if len(hits) != 1:
        raise SystemExit("expected exactly one constant %r, found %d" % (old, len(hits)))
    idx, start, end = hits[0]
    body = data[start + 3:end]
    if body != old:
        raise SystemExit("constant-pool bookkeeping mismatch at #%d" % idx)
    patched = data[:start + 1] + struct.pack(">H", len(new)) + new + data[end:]
    return patched, idx


def matching_sites(data):
    """The two String.matches calls that read the patched pattern."""
    cf = classfile.parse(data, TARGET_CLASS)
    pattern_idx = [i for i in range(1, len(cf.cp))
                   if cf.cp[i] and cf.cp[i][0] == 1 and cf.cp[i][1].encode() == NEW_PATTERN]
    if len(pattern_idx) != 1:
        raise SystemExit("patched pattern is not in the pool exactly once")
    want = pattern_idx[0]
    # ldc of a string literal points at the String entry, which points at the Utf8
    readers = {want}
    for i in range(1, len(cf.cp)):
        entry = cf.cp[i]
        if entry and entry[0] == 8 and entry[1] == want:
            readers.add(i)
    sites = []
    for m in cf.methods:
        for a in m.attrs:
            if a.name != "Code":
                continue
            length = int.from_bytes(a.data[4:8], "big")
            raw = a.data[8:8 + length]
            for off, op, _l in classfile.walk_code(raw, "%s.%s" % (cf.name, m.name)):
                if op in (0x12, 0x13):
                    idx = raw[off + 1] if op == 0x12 else (raw[off + 1] << 8) | raw[off + 2]
                    if idx in readers:
                        sites.append((m.name, off))
    return sites


CHANGELOG_EN = (
    "Build 293 fixes trigger commands that name their target. The mod fills @npc with the "
    "NPC uuid and the stock shortcut then saw a leading hex digit, took it for coordinates "
    "and inserted @s: `tp @npc ~ ~5 ~` ran as `tp @s <uuid> ~ ~5 ~` and the game rejected "
    "it. The shortcut now requires the whole word after tp to be a coordinate, so @s, @p, "
    "@npc, names and every other command are left alone. "
)
PATCHINFO = (
    "Сборка 293 чинит команды триггера с явной целью. Мод подставляет в @npc uuid НПС, а "
    "короткий путь «цели нет, только координаты» видел шестнадцатеричную цифру в начале "
    "uuid и дописывал @s: `tp @npc ~ ~5 ~` уходило как `tp @s <uuid> ~ ~5 ~`, и игра "
    "отвечала «Неверный аргумент для команды». Теперь короткий путь требует, чтобы всё "
    "слово после tp было координатой; @s, @p, @npc, ники и остальные команды не трогаются. "
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    with zipfile.ZipFile(args.base) as z:
        infos = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in infos}
        order = [i.filename for i in infos]

    patched, cp_index = patch_constant(blobs[TARGET_CLASS], OLD_PATTERN, NEW_PATTERN)
    classfile.parse(patched, TARGET_CLASS)          # the pool still parses
    sites = matching_sites(patched)
    if sorted(name for name, _off in sites) != ["command", "commandForEntity"]:
        raise SystemExit("unexpected readers of the guard: %r" % (sites,))
    print("  %s: constant #%d, %d -> %d bytes, read by %s"
          % (TARGET_CLASS, cp_index, len(OLD_PATTERN), len(NEW_PATTERN),
             ", ".join("%s@%d" % s for s in sites)))
    blobs[TARGET_CLASS] = patched

    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)
    toml = toml.replace('displayName="Settled Lands"',
                        'displayName="Settled Lands %s"' % NEW_VERSION)
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
    print("wrote %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
