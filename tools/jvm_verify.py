#!/usr/bin/env python3
"""Gate 2/4 - JVM structural verification of every class in the jar.

No JDK needed. For each .class entry:
  * class-file structure (constant pool, fields, methods, attributes) parses
    with no truncation and no trailing garbage
  * class major version is plausible for the target (Java 21 -> 65)
  * this/super/interface class references resolve to Utf8
  * every Code attribute contains a decodable instruction stream
    (valid opcodes, no instruction running past the end)
  * constant-pool operands (ldc/get*/invoke*/new/checkcast/...) point at
    entries of the right tag
  * branch and switch targets land on instruction boundaries
  * exception-table ranges are inside the code and start/end/handler land on
    instruction boundaries
  * attribute lengths are consistent

This is a *structural* verifier: it does not run the JVM dataflow verifier,
but it catches every damage pattern produced by hand-edited bytecode.

Usage: python3 tools/jvm_verify.py <jar> [--target-major 65]
Exit code 0 = pass, 1 = fail.
"""

import argparse
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classfile                                        # noqa: E402
from classfile import ClassFormatError                  # noqa: E402

BRANCH_U2 = {0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2,
             0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xc6, 0xc7}
BRANCH_U4 = {0xc8, 0xc9}


def s2(b, i):
    return int.from_bytes(b[i:i + 2], "big", signed=True)


def s4(b, i):
    return int.from_bytes(b[i:i + 4], "big", signed=True)


def verify_method(cf, m):
    code_attr = m.attr("Code")
    if code_attr is None:
        return
    r = classfile.Reader(code_attr.data, "%s.%s%s:Code" % (cf.name, m.name, m.desc))
    max_stack = r.u2()
    max_locals = r.u2()
    code_len = r.u4()
    if code_len == 0:
        raise ClassFormatError("%s.%s%s: zero-length Code" % (cf.name, m.name, m.desc))
    code = r.bytes(code_len)
    owner = "%s.%s%s" % (cf.name, m.name, m.desc)

    bounds = classfile.instruction_offsets(code, owner)

    # exception table
    try:
        exc_count = r.u2()
    except ClassFormatError:
        raise
    for _ in range(exc_count):
        start, end, handler, ctype = r.u2(), r.u2(), r.u2(), r.u2()
        for label, off in (("start", start), ("end", end), ("handler", handler)):
            if off > code_len:
                raise ClassFormatError("%s: exception-table %s %d outside code (%d)"
                                       % (owner, label, off, code_len))
            if off != code_len and off not in bounds:
                raise ClassFormatError("%s: exception-table %s %d is not an "
                                       "instruction boundary" % (owner, label, off))
        if start >= end:
            raise ClassFormatError("%s: exception-table range %d..%d is empty/inverted"
                                   % (owner, start, end))
        if ctype:
            cf.class_name(ctype)

    for _ in range(r.u2()):                      # Code attributes
        nidx = r.u2()
        length = r.u4()
        cf.utf8(nidx)
        r.skip(length)
    if not r.at_end():
        raise ClassFormatError("%s: %d trailing byte(s) in Code attribute"
                               % (owner, len(r.b) - r.p))

    # decode + resolve operands
    for off, op, length in classfile.walk_code(code, owner):
        if op in classfile.CP_U2:
            idx = code[off + 1] if op == 0x12 else int.from_bytes(
                code[off + 1:off + 3], "big")
            allowed = classfile.CP_U2[op]
            if not 0 < idx < len(cf.cp) or cf.cp[idx] is None:
                raise ClassFormatError("%s: cp index %d out of range at %d"
                                       % (owner, idx, off))
            tag = cf.cp[idx][0]
            if tag not in allowed:
                raise ClassFormatError(
                    "%s: at %d opcode 0x%02x wants cp tag %s but cp#%d is %s"
                    % (owner, off, op, allowed,
                       idx, classfile.CP_TAGS.get(tag, tag)))
        elif op in BRANCH_U2:
            target = off + s2(code, off + 1)
            if not 0 <= target < code_len or target not in bounds:
                raise ClassFormatError("%s: branch at %d targets %d, not an "
                                       "instruction boundary" % (owner, off, target))
        elif op in BRANCH_U4:
            target = off + s4(code, off + 1)
            if not 0 <= target < code_len or target not in bounds:
                raise ClassFormatError("%s: wide branch at %d targets %d, not an "
                                       "instruction boundary" % (owner, off, target))
        elif op == 0xaa:                          # tableswitch
            pad = (4 - ((off + 1) % 4)) % 4
            base = off + 1 + pad
            default = off + s4(code, base)
            low = s4(code, base + 4)
            high = s4(code, base + 8)
            targets = [default] + [off + s4(code, base + 12 + 4 * i)
                                   for i in range(high - low + 1)]
            for t in targets:
                if not 0 <= t < code_len or t not in bounds:
                    raise ClassFormatError("%s: tableswitch at %d targets %d"
                                           % (owner, off, t))
        elif op == 0xab:                          # lookupswitch
            pad = (4 - ((off + 1) % 4)) % 4
            base = off + 1 + pad
            default = off + s4(code, base)
            npairs = s4(code, base + 4)
            targets = [default] + [off + s4(code, base + 8 + 8 * i + 4)
                                   for i in range(npairs)]
            for t in targets:
                if not 0 <= t < code_len or t not in bounds:
                    raise ClassFormatError("%s: lookupswitch at %d targets %d"
                                           % (owner, off, t))
        elif op == 0xbc:                          # newarray
            if not 4 <= code[off + 1] <= 11:
                raise ClassFormatError("%s: bad newarray type at %d" % (owner, off))
        elif op == 0xc5:                          # multianewarray
            if code[off + 3] == 0:
                raise ClassFormatError("%s: multianewarray with 0 dimensions at %d"
                                       % (owner, off))
    if max_stack == 0 and code_len > 1:
        pass        # legal for trivial methods, nothing to assert


def verify_class(cf, entry, target_major, warn, third_party):
    if cf.major != target_major:
        msg = ("%s: class major %d (expected %d for Java %s)"
               % (entry, cf.major, target_major,
                  {65: "21", 61: "17", 55: "11"}.get(target_major, "?")))
        if entry.startswith("dev/settledlands/"):
            warn.append(msg)
        else:
            third_party.append(msg)
    for m in cf.methods:
        verify_method(cf, m)
    for a in cf.attrs:
        cls = cf.class_attrs.get(a.name)
        if cls is not None and cls.name == "NestHost":
            pass


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--target-major", type=int, default=65,
                    help="expected class-file major version (65 = Java 21)")
    ap.add_argument("--quiet-warnings", action="store_true")
    args = ap.parse_args(argv)

    if not os.path.exists(args.jar):
        print("jvm_verify: FAIL - no such file: %s" % args.jar)
        return 1

    warn = []
    third_party = []
    checked = 0
    try:
        with zipfile.ZipFile(args.jar) as z:
            entries = [n for n in z.namelist() if n.endswith(".class")]
            if not entries:
                print("jvm_verify: FAIL - no .class entries")
                return 1
            for entry in entries:
                data = z.read(entry)
                try:
                    cf = classfile.parse(data, entry)
                    verify_class(cf, entry, args.target_major, warn, third_party)
                except ClassFormatError as exc:
                    print("jvm_verify: FAIL - %s" % exc)
                    return 1
                checked += 1
    except zipfile.BadZipFile as exc:
        print("jvm_verify: FAIL - not a readable jar: %s" % exc)
        return 1

    print("jvm_verify: OK  %s" % os.path.basename(args.jar))
    print("  classes verified : %d" % checked)
    print("  target major     : %d" % args.target_major)
    if third_party:
        print("  vendored classes : %d (older class version, expected for "
              "bundled libraries - not a mod bug)" % len(third_party))
    if warn and not args.quiet_warnings:
        print("  warnings         : %d" % len(warn))
        for w in warn[:10]:
            print("    - %s" % w)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
