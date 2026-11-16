#!/usr/bin/env python3
"""
Build 426 - fix Mojang Brigadier CommandSyntaxException package in DungeonCommand425.

Build 425 crashed in real NeoForge 21.1.265 (Minecraft 1.21.1) during mod construction
(``FMLModContainer.constructMod -> AutomaticEventSubscriber.inject -> Class.getDeclaredMethods()``)
with ``NoClassDefFoundError: com/mojang/brigadier/CommandSyntaxException``.

Root cause: ``build303-src/stubs420-src/com/mojang/brigadier/CommandSyntaxException.java`` placed the
compile-time stub in ``com.mojang.brigadier`` instead of ``com.mojang.brigadier.exceptions``, and
``CommandSourceStack#getPlayerOrException()`` + ``Command#run(CommandContext)`` threw that wrong type.
``DungeonCommand425``'s lambda bodies emitted ``throws com/mojang/brigadier/CommandSyntaxException`` in
their method ``Exceptions`` attributes, which ``Class.getDeclaredMethods()`` resolves eagerly when
NeoForge scans ``@EventBusSubscriber`` classes.

Fix:
  * Move ``CommandSyntaxException`` stub to ``com.mojang.brigadier.exceptions``;
  * Update ``Command.java``, ``CommandSourceStack.java``, and ``DungeonCommand425.java`` to use
    ``com.mojang.brigadier.exceptions.CommandSyntaxException`` (matching every native command class in
    the base jar);
  * Add external class/package & forbidden phantom class checking in ``tools/check_mixin_class.py``
    (Phase D) and subscriber reflection checks in ``tools/hell_dungeon_check.py`` +
    ``tools/harness/HellDungeon425Probe.java``.

Base 425. One class replaced (``DungeonCommand425``), every other class byte-identical to Build 425.
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build425-hell-dungeon"
NEW_VERSION = "0.8.0-build426-dungeon-command-fix"
OUT_NAME = "settledlands-0.8.0-build426-dungeon-command-fix.jar"

REMOVED = ()
REPLACED = (
    "dev/settledlands/dungeon/DungeonCommand425.class",
)
ADDED = ()

MARKERS = {
    "dev/settledlands/dungeon/DungeonCommand425.class": (
        "com/mojang/brigadier/exceptions/CommandSyntaxException",
        "dungeon",
        "leave",
    ),
}
FORBIDDEN_MARKERS = {
    "dev/settledlands/dungeon/DungeonCommand425.class": (
        "com/mojang/brigadier/CommandSyntaxException",
    ),
}

CHANGELOG_EN = (
    "Build 426 fixes NeoForge startup crash in Build 425 (NoClassDefFoundError: "
    "com/mojang/brigadier/CommandSyntaxException during AutomaticEventSubscriber.inject) by "
    "fixing the Brigadier exception stub package to com.mojang.brigadier.exceptions and recompiling "
    "DungeonCommand425."
)
PATCHINFO = (
    "Сборка 426: исправлен краш запуска Build 425 в NeoForge 21.1.265\n"
    "  * Исправлен пакет исключения Brigadier в стабах и DungeonCommand425: "
    "com.mojang.brigadier.exceptions.CommandSyntaxException вместо com.mojang.brigadier.CommandSyntaxException;\n"
    "  * Сохранены все функции Build 425: измерение «Адский данж» (settledlands:hell_dungeon), "
    "start.slbp, сетка 512x512, фильтр спавна мобов и команды /settledlands dungeon.\n"
)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    ap.add_argument("--classes", default=None, help="directory containing compiled build 426 classes")
    args = ap.parse_args(argv)

    base = pathlib.Path(args.base)
    if not base.is_file():
        raise SystemExit("no such base jar: %s" % base)
    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_path = outdir / OUT_NAME

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = pathlib.Path(args.classes) if args.classes else (root / "out426")
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
        for bad in FORBIDDEN_MARKERS.get(name, ()):
            if bad.encode("utf-8") in data:
                raise SystemExit("%s still contains forbidden marker %r" % (name, bad))
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
        order.append(name)
        blobs[name] = data

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel not in blobs or blobs[rel] != compiled.read_bytes():
            raise SystemExit("compiled class %s was not packaged into the jar!" % rel)

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
