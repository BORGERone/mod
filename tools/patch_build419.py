#!/usr/bin/env python3
"""
Build 419 - make ESC close the lockpick menu (`HackPanel377`) immediately in every state.

Changes on top of Build 418 (`settledlands-0.8.0-build418-abort-panel-key-guard.jar`):

  * `HackPanel377#keyPressed` and `#keyReleased` now delegate ESC (`256`) directly to `Screen`
    regardless of `solved` or `aborted`, closing the menu immediately on the first press instead
    of requiring an 800 ms hold (`ESC_HOLD_MS` / `escHeldTick()` removed).
  * Non-ESC held keys still keep `HackPanel377` open (`allReleased()` + 450 ms reaction guard
    on abort) when the player does not press ESC, preventing held `P` / `W` / `Q` from bleeding
    into vanilla keybinds.
  * Panel hint text updated to «ESC - закрыть меню сразу».
"""

import argparse
import hashlib
import pathlib
import sys
import zipfile

BASE_VERSION = "0.8.0-build418-abort-panel-key-guard"
NEW_VERSION = "0.8.0-build419-esc-immediate-close"
OUT_NAME = "settledlands-0.8.0-build419-esc-immediate-close.jar"

REMOVED = ()
ADDED = ()
REPLACED = (
    "dev/settledlands/door/HackPanel377.class",
)
SAME_INNER = ()

MIXIN_MARKERS = {
    "dev/settledlands/door/HackPanel377.class": (
        "abortFromServer",
        "markAborted",
        "allReleased",
        "ВЗЛОМ ПРЕРВАН",
        "ВЗЛОМ ПРИНЯТ",
        "ESC - закрыть меню сразу",
    ),
}

CHANGELOG_EN = (
    "Build 419 makes ESC close the HackPanel377 lockpick menu immediately on the first press in all "
    "states (in progress, solved, and witness-aborted), removing the 800 ms ESC hold requirement while "
    "retaining the held-key release guard for non-ESC keys."
)
PATCHINFO = (
    "Сборка 419: клавиша ESC закрывает меню взлома HackPanel377 мгновенно с первого нажатия в любом "
    "состоянии (во время взлома, после принятия взлома и при срыве свидетелем), убрано требование "
    "удерживать ESC 800 мс. Защита от утечки зажатых не-ESC клавиш (включая P) при отпускании рук "
    "сохранена.\n"
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

    root = pathlib.Path(__file__).resolve().parent.parent / "build303-src"
    srcroot = root / "out419"

    with zipfile.ZipFile(base) as zf:
        order = zf.namelist()
        blobs = {name: zf.read(name) for name in order}

    for name in MIXIN_MARKERS:
        if name not in REMOVED and name not in ADDED and name not in REPLACED:
            raise SystemExit("MIXIN_MARKERS names %s, which is not in ADDED or REPLACED" % name)

    for name in REMOVED:
        if name not in blobs:
            raise SystemExit("expected %s in base jar to remove" % name)
        del blobs[name]
        order.remove(name)

    for name in ADDED + REPLACED:
        src = srcroot / name
        if not src.is_file():
            raise SystemExit("missing compiled class: %s" % src)
        data = src.read_bytes()
        for marker in MIXIN_MARKERS.get(name, ()):
            if marker.encode("utf-8") not in data:
                raise SystemExit("%s does not contain marker %r" % (name, marker))
        if name in ADDED:
            if name in blobs:
                raise SystemExit("%s already in base jar" % name)
            order.append(name)
            print("  added   : %s (%d bytes)" % (name, len(data)))
        else:
            if name not in blobs:
                raise SystemExit("base jar has no entry: %s" % name)
            if blobs[name] == data:
                raise SystemExit("%s is byte-identical to base" % name)
            print("  replaced: %s (%d bytes)" % (name, len(data)))
        blobs[name] = data

    for compiled in sorted(srcroot.rglob("*.class")):
        rel = compiled.relative_to(srcroot).as_posix()
        if rel in SAME_INNER:
            if rel not in blobs or blobs[rel] != compiled.read_bytes():
                raise SystemExit("inner class %s changed unexpectedly; move to REPLACED!" % rel)
            continue
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
