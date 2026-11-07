#!/usr/bin/env python3
"""
Build 399 - the graph screens open again.

The author, after 398: «ты сломал графы, они теперь не открываются». Not a crash, not a wrong screen - the wand
click simply did nothing, which is what a graph screen looks like when it dies on its first frame and the mod's
own handler swallows the reason.

The cause was one line in `CardEventPin398Mixin`, and it is the kind of line that looks like a tidy early exit:

    BufferBuilder buffer = Tesselator.getInstance().begin(QUADS, POSITION_COLOR);
    ...
    if (quads <= 0) return;                      // <- the builder is left OPEN

A `BufferBuilder` that is begun and never finished is not a harmless no-op. `Tesselator#begin` refuses to begin
another one while one is open, so the next pass of the frame - the screen's own cables on the following frame,
or any later draw pass - throws `IllegalStateException: Not building!` and the graph screen dies. Every door and
every chest graph has a card, so every one of them entered this pass; with no second cable wired yet there was
nothing to draw, `quads` stayed 0, and the screen went down on its first frame. The blue pass of build 290 -
the pattern this mixin was copied from - closes the builder on both paths (`drawWithShader(buildOrThrow())` or
`buffer.build()`), and that is exactly what was dropped here.

So this build closes the builder on both paths, wraps the whole pass so a drawing failure can never cost the
screen, and puts the cable code in its own method so there is one place that decides whether a buffer is open.
Two more things, small but real:

  * the helper this mixin adds to the screen was called `settledlands$screen`, which the blue pass of build 290
    already adds to the same class - two mixins with one method name and signature leave it to Mixin to pick
    one. Renamed to `settledlands$cardScreen`;
  * the chest inspector's new click callback had the same name and signature as the door's. Both are merged
    into the same class, so Mixin could hand the chest the door's click - a chest's toggle would then flip a
    door's seal. The chest now closes over the node through a one-element holder, so its callback has its own
    signature.

Base 398, two classes replaced, no new class, no new mixin, no language changes.
"""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build398-card-sockets"
NEW_VERSION = "0.8.0-build399-card-cable"
OUT_NAME = "settledlands-0.8.0-build399-card-cable.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/mixin/CardEventPin398Mixin.class",
    "dev/settledlands/mixin/NpcChestRows396Mixin.class",
)
ADDED = ()
MIXINS = ()
MIXINS_FIRST = ()
MIXINS_REMOVED = ()
CLIENT_MIXINS = ()          # 398 registered it; this build only replaces its class

CLIENT_REMOVED = ()

MIXIN_MARKERS = {
    # The cable pass must keep the empty path that closes the builder, and the helper must be its own name.
    "dev/settledlands/mixin/CardEventPin398Mixin.class": ("settledlands$cardScreen", "BufferBuilder"),
    "dev/settledlands/mixin/NpcChestRows396Mixin.class": ("settledlands$lockWord", "chest.lock"),
}

LANG_SET = {}
LANG_SET_EN = {}
LANG_CHANGE = {}

EXTRA = ()
EXTRA_DIR = "assets368"

CHANGELOG_EN = (
    "Build 399 fixes build 398: the graph screens open again. The second-socket pass began a vertex buffer and "
    "returned early when there was nothing to draw, leaving the buffer open - so the next draw pass of the frame "
    "threw and a door's or a chest's graph died on its first frame, which is why the wand appeared to stop "
    "working. The buffer is now closed on both paths, exactly as the blue value-wire pass has always done, and "
    "no path of that pass can throw its way out of the screen. The mixin's helper method was also renamed so it "
    "no longer shares a name with another mixin's helper on the same class, and the chest inspector's toggle "
    "click got its own signature so it can never be replaced by the door's click."
)
PATCHINFO = (
    "Сборка 399 чинит 398: графы снова открываются. Проход второго узла начинал вершинный буфер и выходил "
    "заранее, когда рисовать было нечего, — буфер оставался незакрытым, и следующий проход кадра падал, так "
    "что граф двери или сундука умирал на первом же кадре; именно поэтому клик палочкой как будто перестал "
    "работать. Теперь буфер закрывается на обоих путях, как это всегда делал синий проход 290-й, и ни один "
    "путь этого прохода не может выбросить исключение в экран. Заодно хелпер миксина переименован, чтобы не "
    "делить имя с хелпером другого миксина на том же классе, а клик тумблера сундука получил собственную "
    "сигнатуру, чтобы его не мог подменить клик двери.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    srcroot = pathlib.Path(__file__).resolve().parent.parent / "build303-src" / "out399"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("%s has markers but is in no list" % name)

    for name in ADDED + REPLACED:
        src = srcroot / name
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
