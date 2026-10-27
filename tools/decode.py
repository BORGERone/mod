#!/usr/bin/env python3
"""Точный декодер байткода (stdlib only) + разбор Code/StackMapTable/LineNumberTable.

    python3 tools/decode.py <jar> <class> <method>
"""
import struct, sys, zipfile
from disasm import parse_class, cls_name, ref, const_str, code_of, OPS

LENS = {}


def instr_len(code, pc):
    op = code[pc]
    name, ln = OPS.get(op, (None, 0))
    if name is None:
        raise ValueError(f"неизвестный опкод {op:#x} на {pc}")
    if op == 0xC4:            # wide
        return 4 if code[pc + 1] == 0x84 else 3
    if op == 0xAA:            # tableswitch
        pad = (4 - (pc + 1) % 4) % 4
        q = pc + 1 + pad
        lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
        return 1 + pad + 12 + 4 * (hi - lo + 1)
    if op == 0xAB:            # lookupswitch
        pad = (4 - (pc + 1) % 4) % 4
        q = pc + 1 + pad
        npairs = struct.unpack(">i", code[q + 4:q + 8])[0]
        return 1 + pad + 8 + 8 * npairs
    return 1 + ln


def walk(code):
    out = []
    pc = 0
    while pc < len(code):
        ln = instr_len(code, pc)
        out.append((pc, code[pc:pc + ln]))
        pc += ln
    return out


BRANCH2 = {0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6,
           0xA7, 0xA8, 0xC6, 0xC7}
NAMES = {v[0]: k for k, v in OPS.items()}


def targets(code):
    """Все абсолютные цели переходов (включая switch)."""
    t = set()
    for pc, raw in walk(code):
        op = raw[0]
        if op in BRANCH2:
            t.add(pc + struct.unpack(">h", raw[1:3])[0])
        elif op == 0xC8:
            t.add(pc + struct.unpack(">i", raw[1:5])[0])
        elif op in (0xAA, 0xAB):
            pad = (4 - (pc + 1) % 4) % 4
            q = pc + 1 + pad
            t.add(pc + struct.unpack(">i", code[q:q + 4])[0])
            if op == 0xAA:
                lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
                for k in range(hi - lo + 1):
                    t.add(pc + struct.unpack(">i", code[q + 12 + 4 * k:q + 16 + 4 * k])[0])
            else:
                npairs = struct.unpack(">i", code[q + 4:q + 8])[0]
                for k in range(npairs):
                    t.add(pc + struct.unpack(">i", code[q + 8 + 8 * k + 4:q + 12 + 8 * k + 4])[0])
    return t


def parse_smt(blob):
    if not blob:
        return []
    c = blob
    n = struct.unpack(">H", c[0:2])[0]
    p = 2
    frames = []
    prev = -1
    for _ in range(n):
        ft = c[p]; p += 1
        if ft <= 63:
            d = ft
        elif ft <= 127:
            d = ft - 64
        elif ft == 247:
            d = struct.unpack(">H", c[p:p + 2])[0]; p += 2
        elif 248 <= ft <= 250:
            d = struct.unpack(">H", c[p:p + 2])[0]; p += 2
        elif ft in (251, 252, 253, 254, 255):
            d = struct.unpack(">H", c[p:p + 2])[0]; p += 2
        else:
            raise ValueError(f"frame_type {ft}")
        if ft == 247:
            p += 1  # 1 verification_type
        elif 248 <= ft <= 250:
            pass
        elif 252 <= ft <= 254:
            p += ft - 251
        elif ft == 255:
            nl = struct.unpack(">H", c[p:p + 2])[0]; p += 2
            p += nl
            ns = struct.unpack(">H", c[p:p + 2])[0]; p += 2
            p += ns
        off = d if prev < 0 else prev + d + 1
        prev = off
        frames.append((ft, d, off))
    return frames, p, len(blob)


def main():
    jar, cls, meth = sys.argv[1], sys.argv[2], sys.argv[3]
    try:
        data = zipfile.ZipFile(jar).read(cls)
    except (zipfile.BadZipFile, IsADirectoryError, PermissionError):
        data = open(jar, "rb").read()
    k = parse_class(data)
    cp = k["cp"]
    for acc, ni, di, attrs in k["methods"]:
        if cp[ni][1] != meth:
            continue
        co = code_of(attrs, cp)
        code = co["code"]
        print(f"{meth}{cp[di][1]}  max_stack={co['max_stack']} max_locals={co['max_locals']} len={len(code)}")
        tg = targets(code)
        for pc, raw in walk(code):
            mark = "*" if pc in tg else " "
            name = OPS[raw[0]][0]
            extra = ""
            if name in ("ldc", "ldc_w"):
                idx = raw[1] if len(raw) == 2 else struct.unpack(">H", raw[1:3])[0]
                extra = const_str(cp, idx)
            elif name in ("ldc2_w",):
                idx = struct.unpack(">H", raw[1:3])[0]
                extra = const_str(cp, idx)
            elif name in ("getstatic", "putstatic", "getfield", "putfield", "invokevirtual",
                          "invokespecial", "invokestatic", "invokeinterface"):
                idx = struct.unpack(">H", raw[1:3])[0]
                extra = ref(cp, idx)
            elif name in ("new", "checkcast", "instanceof", "anewarray"):
                idx = struct.unpack(">H", raw[1:3])[0]
                extra = cls_name(cp, idx)
            elif name in ("ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "if_icmpeq", "if_icmpne",
                          "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq",
                          "if_acmpne", "goto", "ifnull", "ifnonnull"):
                extra = "-> %d" % (pc + struct.unpack(">h", raw[1:3])[0])
            elif name in ("bipush",):
                extra = str(struct.unpack(">b", raw[1:2])[0])
            elif name in ("sipush",):
                extra = str(struct.unpack(">h", raw[1:3])[0])
            elif name == "iinc":
                extra = f"{raw[1]}, {struct.unpack('>b', raw[2:3])[0]}"
            elif name in ("iload", "lload", "fload", "dload", "aload",
                          "istore", "lstore", "fstore", "dstore", "astore"):
                extra = str(raw[1])
            elif name in ("tableswitch", "lookupswitch"):
                extra = "(см. raw)"
            print(f"{mark}{pc:5d}  {name:18s} {extra}")
        print("frames:", parse_smt(dict(co["attrs"]).get("StackMapTable")))
        return


if __name__ == "__main__":
    main()
