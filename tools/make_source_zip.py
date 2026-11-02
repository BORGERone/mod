#!/usr/bin/env python3
"""Build the per-build source zip: the README, the tools and the sources we actually patch.

One zip per build, made from the tree at that build's commit. Nothing is compiled here - the zip
exists so the next person can read the exact sources a shipped jar was built from.

    python3 tools/make_source_zip.py --readme README-Build333-RU.md \
        --out SettledLands-Build333-DropdownFirst-Source.zip

Deterministic: fixed timestamps and modes, entries sorted, so the same tree gives the same bytes
and the same sha256 as the file that was published.
"""
import argparse
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent


def collect(readme, mixin_sources):
    files = []
    if readme:
        path = ROOT / readme
        if not path.is_file():
            raise SystemExit("no such readme: %s" % path)
        files.append((readme, path))
    for pattern, base in (("*.py", ROOT / "tools"), ("*.java", ROOT / "tools" / "tests")):
        for path in sorted(base.glob(pattern)):
            files.append((path.relative_to(ROOT).as_posix(), path))
    for base in (ROOT / "build303-src" / "src", ROOT / "build303-src" / "api3-src",
                 ROOT / "build303-src" / "api4-src", ROOT / "build303-src" / "stubs345-src",
                 ROOT / "build303-src" / "api-src",
                 ROOT / "build303-src" / "stubs"):
        for path in sorted(base.rglob("*.java")):
            files.append((path.relative_to(ROOT).as_posix(), path))
    # The compile mirrors of the mixin annotations and the JVM harness are part of how a build is
    # made: build 335 could not express its injection point without At.Shift in tools/api271.
    for base in (ROOT / "tools" / "api271", ROOT / "tools" / "harness"):
        for path in sorted(base.rglob("*.java")):
            files.append((path.relative_to(ROOT).as_posix(), path))
    names = [n for n, _ in files]
    if mixin_sources:
        for want in mixin_sources:
            if want not in names:
                raise SystemExit("the zip would not contain %s" % want)
    return sorted(files)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--readme", default="")
    ap.add_argument("--must-contain", action="append", default=[],
                    help="relative path that has to end up in the zip (repeatable)")
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)

    files = collect(args.readme, args.must_contain)
    out = ROOT / args.out
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name, path in files:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, path.read_bytes())
    print("wrote %s (%d entries)" % (out, len(files)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
