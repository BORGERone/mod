#!/usr/bin/env python3
"""Readable bytecode listing built on tools/classfile.py (no JDK needed).

    python3 tools/javap_lite.py <jar|class-file> --class dev/settledlands/npc/client/NpcUi$NpcDropdown
    python3 tools/javap_lite.py <jar> --class '<owner>' --method mouseClicked
    python3 tools/javap_lite.py <jar> --class '<owner>' --method '<name>' --grep popup

Unlike tools/disasm.py this one resolves every constant-pool reference through
classfile.py, which parses long/double pool slots correctly, so it never throws
"index out of range" on a class that also holds doubles (that is why the older
tool could not print NpcConfigScreen).
"""
import argparse
import sys
import zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import classfile as CF

OPS = {
    0x00: "nop", 0x01: "aconst_null", 0x02: "iconst_m1", 0x03: "iconst_0", 0x04: "iconst_1",
    0x05: "iconst_2", 0x06: "iconst_3", 0x07: "iconst_4", 0x08: "iconst_5",
    0x09: "lconst_0", 0x0a: "lconst_1", 0x0b: "fconst_0", 0x0c: "fconst_1", 0x0d: "fconst_2",
    0x0e: "dconst_0", 0x0f: "dconst_1",
    0x10: "bipush", 0x11: "sipush", 0x12: "ldc", 0x13: "ldc_w", 0x14: "ldc2_w",
    0x15: "iload", 0x16: "lload", 0x17: "fload", 0x18: "dload", 0x19: "aload",
    0x1a: "iload_0", 0x1b: "iload_1", 0x1c: "iload_2", 0x1d: "iload_3",
    0x1e: "lload_0", 0x1f: "lload_1", 0x20: "lload_2", 0x21: "lload_3",
    0x22: "fload_0", 0x23: "fload_1", 0x24: "fload_2", 0x25: "fload_3",
    0x26: "dload_0", 0x27: "dload_1", 0x28: "dload_2", 0x29: "dload_3",
    0x2a: "aload_0", 0x2b: "aload_1", 0x2c: "aload_2", 0x2d: "aload_3",
    0x2e: "iaload", 0x2f: "laload", 0x30: "faload", 0x31: "daload", 0x32: "aaload",
    0x33: "baload", 0x34: "caload", 0x35: "saload",
    0x36: "istore", 0x37: "lstore", 0x38: "fstore", 0x39: "dstore", 0x3a: "astore",
    0x3b: "istore_0", 0x3c: "istore_1", 0x3d: "istore_2", 0x3e: "istore_3",
    0x3f: "lstore_0", 0x40: "lstore_1", 0x41: "lstore_2", 0x42: "lstore_3",
    0x43: "fstore_0", 0x44: "fstore_1", 0x45: "fstore_2", 0x46: "fstore_3",
    0x47: "dstore_0", 0x48: "dstore_1", 0x49: "dstore_2", 0x4a: "dstore_3",
    0x4b: "astore_0", 0x4c: "astore_1", 0x4d: "astore_2", 0x4e: "astore_3",
    0x4f: "iastore", 0x50: "lastore", 0x51: "fastore", 0x52: "dastore", 0x53: "aastore",
    0x54: "bastore", 0x55: "castore", 0x56: "sastore",
    0x57: "pop", 0x58: "pop2", 0x59: "dup", 0x5a: "dup_x1", 0x5b: "dup_x2",
    0x5c: "dup2", 0x5d: "dup2_x1", 0x5e: "dup2_x2", 0x5f: "swap",
    0x60: "iadd", 0x61: "ladd", 0x62: "fadd", 0x63: "dadd", 0x64: "isub", 0x65: "lsub",
    0x66: "fsub", 0x67: "dsub", 0x68: "imul", 0x69: "lmul", 0x6a: "fmul", 0x6b: "dmul",
    0x6c: "idiv", 0x6d: "ldiv", 0x6e: "fdiv", 0x6f: "ddiv", 0x70: "irem", 0x71: "lrem",
    0x72: "frem", 0x73: "drem", 0x74: "ineg", 0x75: "lneg", 0x76: "fneg", 0x77: "dneg",
    0x78: "ishl", 0x79: "lshl", 0x7a: "ishr", 0x7b: "lshr", 0x7c: "iushr", 0x7d: "lushr",
    0x7e: "iand", 0x7f: "land", 0x80: "ior", 0x81: "lor", 0x82: "ixor", 0x83: "lxor",
    0x84: "iinc", 0x85: "i2l", 0x86: "i2f", 0x87: "i2d", 0x88: "l2i", 0x89: "l2f",
    0x8a: "l2d", 0x8b: "f2i", 0x8c: "f2l", 0x8d: "f2d", 0x8e: "d2i", 0x8f: "d2l",
    0x90: "d2f", 0x91: "i2b", 0x92: "i2c", 0x93: "i2s", 0x94: "lcmp", 0x95: "fcmpl",
    0x96: "fcmpg", 0x97: "dcmpl", 0x98: "dcmpg",
    0x99: "ifeq", 0x9a: "ifne", 0x9b: "iflt", 0x9c: "ifge", 0x9d: "ifgt", 0x9e: "ifle",
    0x9f: "if_icmpeq", 0xa0: "if_icmpne", 0xa1: "if_icmplt", 0xa2: "if_icmpge",
    0xa3: "if_icmpgt", 0xa4: "if_icmple", 0xa5: "if_acmpeq", 0xa6: "if_acmpne",
    0xa7: "goto", 0xa8: "jsr", 0xa9: "ret", 0xaa: "tableswitch", 0xab: "lookupswitch",
    0xac: "ireturn", 0xad: "lreturn", 0xae: "freturn", 0xaf: "dreturn", 0xb0: "areturn",
    0xb1: "return", 0xb2: "getstatic", 0xb3: "putstatic", 0xb4: "getfield", 0xb5: "putfield",
    0xb6: "invokevirtual", 0xb7: "invokespecial", 0xb8: "invokestatic", 0xb9: "invokeinterface",
    0xba: "invokedynamic", 0xbb: "new", 0xbc: "newarray", 0xbd: "anewarray",
    0xbe: "arraylength", 0xbf: "athrow", 0xc0: "checkcast", 0xc1: "instanceof",
    0xc2: "monitorenter", 0xc3: "monitorexit", 0xc4: "wide", 0xc5: "multianewarray",
    0xc6: "ifnull", 0xc7: "ifnonnull", 0xc8: "goto_w", 0xc9: "jsr_w",
}


def cp_text(cf, idx):
    if not 1 <= idx < len(cf.cp) or cf.cp[idx] is None:
        return "#%d?" % idx
    tag, val = cf.cp[idx]
    if tag == 1:
        return repr(val)
    if tag == 3:
        return str(val if abs(val) < 1 << 31 else val - (1 << 32))
    if tag == 4:
        return "float#%d" % idx
    if tag in (5, 6):
        return "long#%d" % idx
    if tag == 7:
        return cf.utf8(val)
    if tag == 8:
        return "String " + cf.utf8(val)
    if tag == 16:
        return cf.utf8(val) + "" if val < len(cf.cp) and cf.cp[val] else "?"
    if tag in (9, 10, 11):
        c_owner, nt = val
        n_name, n_desc = cf.cp[nt][1] if nt < len(cf.cp) else (0, 0)
        return "%s.%s:%s" % (cf.class_name(c_owner), cf.utf8(n_name), cf.utf8(n_desc))
    if tag == 12:
        return cf.utf8(val[0]) + ":" + cf.utf8(val[1])
    if tag == 15:
        return "mh#%d" % idx
    if tag in (17, 18):
        return "dyn#%d" % idx
    if tag in (19, 20):
        return cf.utf8(val)
    return "tag%d#%d" % (tag, idx)


def code_bytes(attr):
    d = attr.data
    length = int.from_bytes(d[4:8], "big")
    return d[8:8 + length]


def dump(cf, method=None, grep=None):
    for m in cf.methods:
        if not m.is_method:
            continue
        if method and m.name != method:
            continue
        ca = m.attr("Code")
        print("\n== %s%s  (acc=%#x, %s)" % (m.name, m.desc,
              m.access, "with code" if ca else "abstract/native"))
        if not ca:
            continue
        code = code_bytes(ca)
        out = []
        for off, op, ln in CF.walk_code(code, cf.name):
            name = OPS.get(op, "op_%02x" % op)
            arg = ""
            if op in (0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2,
                      0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xc6, 0xc7):
                arg = " -> %d" % (off + int.from_bytes(code[off + 1:off + 3], "big", signed=True))
            elif op in (0xc8, 0xc9):
                arg = " -> %d" % (off + int.from_bytes(code[off + 1:off + 5], "big", signed=True))
            elif op in (0x10,):
                arg = " " + str(code[off + 1])
            elif op in (0x11,):
                arg = " " + str(int.from_bytes(code[off + 1:off + 3], "big", signed=True))
            elif op in (0x12,):
                arg = " # " + cp_text(cf, code[off + 1])
            elif op in (0x13, 0x14):
                arg = " # " + cp_text(cf, int.from_bytes(code[off + 1:off + 3], "big"))
            elif op in (0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a):
                arg = " " + str(code[off + 1])
            elif op == 0x84:
                arg = " %d, %d" % (code[off + 1], int.from_bytes(code[off + 2:off + 3], "big", signed=True))
            elif op in (0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1):
                arg = " # " + cp_text(cf, int.from_bytes(code[off + 1:off + 3], "big"))
            elif op in (0xb9, 0xba):
                arg = " # " + cp_text(cf, int.from_bytes(code[off + 1:off + 3], "big"))
            elif op == 0xc5:
                arg = " # " + cp_text(cf, int.from_bytes(code[off + 1:off + 3], "big"))
            elif op == 0xbc:
                arg = " " + str(code[off + 1])
            line = "  %5d: %s%s" % (off, name, arg)
            if grep and grep not in line:
                continue
            out.append(line)
        print("\n".join(out) if grep else "\n".join(out))
        if not grep:
            for e in ca.data and []:
                pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--class", dest="cls", required=True)
    ap.add_argument("--method")
    ap.add_argument("--grep")
    a = ap.parse_args()
    if a.path.endswith(".jar") or a.path.endswith(".zip"):
        z = zipfile.ZipFile(a.path)
        data = z.read(a.cls if a.cls.endswith(".class") else a.cls + ".class")
    else:
        data = open(a.path, "rb").read()
    cf = CF.parse(data, a.cls)
    print("%s  major=%d  super=%s" % (cf.name, cf.major,
          cf.class_name(cf.super_class) if cf.super_class else "-"))
    fields = ", ".join("%s:%s" % (f.name, f.desc) for f in cf.fields)
    print("fields:", fields)
    dump(cf, a.method, a.grep)


if __name__ == "__main__":
    main()
