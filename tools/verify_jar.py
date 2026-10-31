#!/usr/bin/env python3
"""Gate 4/4 - full verification + SHA256SUMS for one jar.

Runs validate_jar -> jvm_verify -> check_mixin_class in order, stops at the
first failure, and on success writes <jar>.sha256sums.txt next to the jar.

Gate 3 additionally gets --base <previous jar> when one is given: every call to
Minecraft / NeoForge from our classes must already be made by code that runs in
game (Build 280 shipped Entity#hasLineOfSight, which does not exist).

Usage: python3 tools/verify_jar.py <jar> [--expect-version 0.8.0-buildNNN]
                                   [--base PREVIOUS.jar] [--sums-dir DIR]
Exit code 0 = all gates passed.
"""

import argparse
import hashlib
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def run(gate, extra):
    cmd = [sys.executable, os.path.join(HERE, gate)] + extra
    proc = subprocess.run(cmd, capture_output=True, text=True)
    out = (proc.stdout or "") + (proc.stderr or "")
    sys.stdout.write(out)
    return proc.returncode


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--expect-version", default=None)
    ap.add_argument("--base", default=None,
                    help="previous jar known to run in game (gate 3 phase D)")
    ap.add_argument("--sums-dir", default=None)
    args = ap.parse_args(argv)

    if not os.path.exists(args.jar):
        print("verify_jar: FAIL - no such file: %s" % args.jar)
        return 1

    extra = [args.jar]
    if args.expect_version:
        extra += ["--expect-version", args.expect_version]

    print("== gate 1/4 validate_jar ==")
    if run("validate_jar.py", extra) != 0:
        print("verify_jar: FAIL - structure gate")
        return 1
    print("\n== gate 2/4 jvm_verify ==")
    if run("jvm_verify.py", [args.jar]) != 0:
        print("verify_jar: FAIL - JVM gate")
        return 1
    print("\n== gate 3/4 check_mixin_class ==")
    mixin_extra = [args.jar] + (["--base", args.base] if args.base else [])
    if run("check_mixin_class.py", mixin_extra) != 0:
        print("verify_jar: FAIL - mixin gate")
        return 1

    digest = sha256(args.jar)
    name = os.path.basename(args.jar)
    sums_dir = args.sums_dir or os.path.dirname(os.path.abspath(args.jar))
    sums_path = os.path.join(sums_dir, name + ".sha256sums.txt")
    with open(sums_path, "w", encoding="utf-8") as fh:
        fh.write("%s  %s\n" % (digest, name))

    print("\n== result ==")
    print("verify_jar: PASS  %s" % name)
    print("  sha256sums  : %s" % sums_path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
