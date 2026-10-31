#!/usr/bin/env python3
"""Gate 1/4 - jar structure and metadata.

Catches the failure mode that crashed the client on 2026-10-27
("File mods\\settledlands-...jar is not a jar file" /
"java.util.zip.ZipException: zip END header not found"):
a truncated or HTML-error-page download saved with a .jar extension.

Checks:
  * the file really is a zip (central directory + END header present)
  * every entry decompresses and its CRC matches
  * no duplicate entries
  * META-INF/neoforge.mods.toml present and parses as TOML
  * modId / version / displayName / description present and non-empty
  * every [[mixins]] config named in the TOML exists in the jar
  * every JSON asset parses, nothing is a zero-byte stub

Usage: python3 tools/validate_jar.py <jar> [--expect-version 0.8.0-buildNNN]
Exit code 0 = pass, 1 = fail.
"""

import argparse
import json
import os
import sys
import zipfile

try:
    import tomllib
except ImportError:                     # python < 3.11
    tomllib = None


class Fail(Exception):
    pass


def human(n):
    n = float(n)
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return "%.1f %s" % (n, unit)
        n /= 1024.0


def check_zip(path):
    if not os.path.exists(path):
        raise Fail("file does not exist: %s" % path)
    size = os.path.getsize(path)
    if size == 0:
        raise Fail("%s is 0 bytes - download failed" % os.path.basename(path))
    with open(path, "rb") as fh:
        head = fh.read(4)
        fh.seek(max(0, size - 64))
        tail = fh.read(64)
    if head[:2] != b"PK":
        low = head.lower()
        if low[:1] == b"<" or b"<html" in tail.lower() or b"<!doctype" in tail.lower():
            raise Fail("%s starts with %r - this is an HTML page (Drive quota or "
                       "sign-in error page), not a jar"
                       % (os.path.basename(path), head[:20]))
        raise Fail("%s is not a zip archive (magic %r)"
                   % (os.path.basename(path), head))
    if b"PK\x05\x06" not in tail:
        raise Fail("%s has no zip END-of-central-directory record in its last 64 "
                   "bytes - the file is truncated (incomplete download)"
                   % os.path.basename(path))
    try:
        with zipfile.ZipFile(path) as z:
            bad = z.testzip()
            if bad is not None:
                raise Fail("corrupt entry %r (CRC mismatch)" % bad)
            names = z.namelist()
    except zipfile.BadZipFile as exc:
        raise Fail("%s is not a readable jar: %s" % (os.path.basename(path), exc))
    if len(names) != len(set(names)):
        seen, dupes = set(), set()
        for n in names:
            (dupes if n in seen else seen).add(n)
        raise Fail("duplicate entries: %s" % ", ".join(sorted(dupes)[:5]))
    return names, size


def check_metadata(path, names, expect_version):
    toml_name = "META-INF/neoforge.mods.toml"
    if toml_name not in names:
        raise Fail("missing %s" % toml_name)
    with zipfile.ZipFile(path) as z:
        raw = z.read(toml_name).decode("utf-8")
    if tomllib is None:
        raise Fail("python3.11+ required for the TOML gate (tomllib)")
    try:
        doc = tomllib.loads(raw)
    except Exception as exc:
        raise Fail("%s does not parse as TOML: %s" % (toml_name, exc))

    mods = doc.get("mods") or []
    if not mods:
        raise Fail("%s has no [[mods]] table" % toml_name)
    meta = mods[0]
    for key in ("modId", "version", "displayName"):
        val = meta.get(key)
        if not isinstance(val, str) or not val.strip():
            raise Fail("%s: %s is missing or empty (Night Config fails the load)"
                       % (toml_name, key))
    desc = meta.get("description")
    if not isinstance(desc, str) or not desc.strip():
        raise Fail("%s: description is missing or empty - the Night Config parser "
                   "crashes on this (build 272 lesson)" % toml_name)
    if expect_version and meta["version"] != expect_version:
        raise Fail("version mismatch: jar says %r, expected %r"
                   % (meta["version"], expect_version))

    configs = [m.get("config") for m in (doc.get("mixins") or [])
               if isinstance(m, dict)]
    for cfg in configs:
        if not cfg:
            raise Fail("%s: [[mixins]] entry without a config name" % toml_name)
        if cfg not in names and ("META-INF/" + cfg) not in names:
            raise Fail("%s declares mixin config %r which is not in the jar"
                       % (toml_name, cfg))
    return meta, configs


def check_assets(path, names):
    bad = []
    zero = []
    with zipfile.ZipFile(path) as z:
        for n in names:
            if n.endswith("/"):
                continue
            info = z.getinfo(n)
            if info.file_size == 0:
                zero.append(n)
                continue
            if not n.endswith(".json"):
                continue
            try:
                json.loads(z.read(n).decode("utf-8"))
            except Exception as exc:
                bad.append("%s (%s)" % (n, exc))
    if bad:
        raise Fail("invalid JSON asset(s): " + "; ".join(bad[:5]))
    if zero:
        raise Fail("zero-byte entries: " + ", ".join(zero[:5]))


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--expect-version", default=None)
    args = ap.parse_args(argv)

    path = args.jar
    try:
        names, size = check_zip(path)
        meta, configs = check_metadata(path, names, args.expect_version)
        check_assets(path, names)
        classes = [n for n in names if n.endswith(".class")]
        if not classes:
            raise Fail("jar contains no .class files")
    except Fail as exc:
        print("validate_jar: FAIL - %s" % exc)
        return 1

    print("validate_jar: OK  %s" % os.path.basename(path))
    print("  size        : %s" % human(size))
    print("  entries     : %d (%d classes)" % (len(names), len(classes)))
    print("  modId       : %s" % meta["modId"])
    print("  version     : %s" % meta["version"])
    print("  displayName : %s" % meta["displayName"])
    print("  description : %d chars" % len(meta["description"]))
    print("  mixins      : %s" % (", ".join(configs) if configs else "none"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
