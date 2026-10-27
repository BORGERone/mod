#!/usr/bin/env python3
"""Мини-дизассемблер class-файлов (stdlib only).

    python3 tools/disasm.py <file.class|jar> [--class dev/settledlands/npc/GuideNpc]
                            [--method applyProfile|texturePath] [--attrs]

Печатает методы с байткодом и разрешёнными ссылками на пул констант.
"""
import argparse, struct, sys, zipfile


class C:
    def __init__(self, d):
        self.d = d; self.p = 0

    def u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def u2(self):
        v = struct.unpack(">H", self.d[self.p:self.p + 2])[0]; self.p += 2; return v

    def u4(self):
        v = struct.unpack(">I", self.d[self.p:self.p + 4])[0]; self.p += 4; return v

    def take(self, n):
        v = self.d[self.p:self.p + n]
        if len(v) != n:
            raise ValueError(f"обрыв: нужно {n} байт на позиции {self.p}")
        self.p += n
        return v


def parse_class(data):
    c = C(data)
    assert c.take(4) == b"\xca\xfe\xba\xbe", "не class-файл"
    minor, major = struct.unpack(">HH", c.take(4))
    cp_count = c.u2()
    cp = [None] * cp_count
    i = 1
    while i < cp_count:
        tag = c.u1()
        if tag == 1:
            ln = c.u2(); cp[i] = (1, c.take(ln).decode("utf-8", "replace"))
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, c.take(2))
        elif tag == 15:
            cp[i] = (tag, c.take(3))
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            cp[i] = (tag, c.take(4))
        elif tag in (5, 6):
            cp[i] = (tag, c.take(8)); i += 2; continue
        else:
            raise ValueError(f"тег CP {tag}")
        i += 1
    access, this, superidx = struct.unpack(">HHH", c.take(6))
    interfaces = [struct.unpack(">H", c.take(2))[0] for _ in range(c.u2())]

    def members():
        out = []
        for _ in range(c.u2()):
            acc, ni, di = struct.unpack(">HHH", c.take(6))
            attrs = []
            for _ in range(c.u2()):
                an = c.u2(); al = c.u4()
                attrs.append((an, c.take(al)))
            out.append((acc, ni, di, attrs))
        return out

    fields = members()
    methods = members()
    cattrs = []
    for _ in range(c.u2()):
        an = c.u2(); al = c.u4()
        cattrs.append((an, c.take(al)))
    trailing = len(data) - c.p
    return dict(major=major, minor=minor, cp=cp, access=access, this=this, super=superidx,
                interfaces=interfaces, fields=fields, methods=methods, cattrs=cattrs,
                trailing=trailing)


def cls_name(cp, idx):
    if idx == 0:
        return None
    tag, v = cp[idx]
    if tag != 7:
        return f"<cp{idx}>"
    return cp[struct.unpack(">H", v)[0]][1]


def nat(cp, idx):
    tag, v = cp[idx]
    a, b = struct.unpack(">HH", v)
    return cp[a][1], cp[b][1]


def ref(cp, idx):
    if cp[idx] is None or cp[idx][0] not in (9, 10, 11):
        return "?" + str(const_str(cp, idx))
    tag, v = cp[idx]
    ci, nti = struct.unpack(">HH", v)
    n, t = nat(cp, nti)
    return f"{cls_name(cp, ci)}.{n}{t}"


def const_str(cp, idx):
    tag, v = cp[idx]
    if tag == 8:
        return repr(cp[struct.unpack(">H", v)[0]][1])
    if tag == 3:
        return str(struct.unpack(">i", v)[0])
    if tag == 4:
        return repr(struct.unpack(">f", v)[0])
    if tag == 5:
        return str(struct.unpack(">q", v)[0])
    if tag == 6:
        return repr(struct.unpack(">d", v)[0])
    if tag == 7:
        return "class " + cls_name(cp, idx)
    return f"cp{idx}"


OPS = {
    0x00: ("nop", 0), 0x01: ("aconst_null", 0), 0x02: ("iconst_m1", 0), 0x03: ("iconst_0", 0),
    0x04: ("iconst_1", 0), 0x05: ("iconst_2", 0), 0x06: ("iconst_3", 0), 0x07: ("iconst_4", 0),
    0x08: ("iconst_5", 0), 0x09: ("lconst_0", 0), 0x0a: ("lconst_1", 0), 0x0b: ("fconst_0", 0),
    0x0c: ("fconst_1", 0), 0x0d: ("fconst_2", 0), 0x0e: ("dconst_0", 0), 0x0f: ("dconst_1", 0),
    0x10: ("bipush", 1), 0x11: ("sipush", 2), 0x12: ("ldc", 1), 0x13: ("ldc_w", 2),
    0x14: ("ldc2_w", 2), 0x15: ("iload", 1), 0x16: ("lload", 1), 0x17: ("fload", 1),
    0x18: ("dload", 1), 0x19: ("aload", 1), 0x1a: ("iload_0", 0), 0x1b: ("iload_1", 0),
    0x1c: ("iload_2", 0), 0x1d: ("iload_3", 0), 0x1e: ("lload_0", 0), 0x1f: ("lload_1", 0),
    0x20: ("lload_2", 0), 0x21: ("lload_3", 0), 0x22: ("fload_0", 0), 0x23: ("fload_1", 0),
    0x24: ("fload_2", 0), 0x25: ("fload_3", 0), 0x26: ("dload_0", 0), 0x27: ("dload_1", 0),
    0x28: ("dload_2", 0), 0x29: ("dload_3", 0), 0x2a: ("aload_0", 0), 0x2b: ("aload_1", 0),
    0x2c: ("aload_2", 0), 0x2d: ("aload_3", 0), 0x2e: ("iaload", 0), 0x2f: ("laload", 0),
    0x30: ("faload", 0), 0x31: ("daload", 0), 0x32: ("aaload", 0), 0x33: ("baload", 0),
    0x34: ("caload", 0), 0x35: ("saload", 0), 0x36: ("istore", 1), 0x37: ("lstore", 1),
    0x38: ("fstore", 1), 0x39: ("dstore", 1), 0x3a: ("astore", 1), 0x3b: ("istore_0", 0),
    0x3c: ("istore_1", 0), 0x3d: ("istore_2", 0), 0x3e: ("istore_3", 0), 0x3f: ("lstore_0", 0),
    0x40: ("lstore_1", 0), 0x41: ("lstore_2", 0), 0x42: ("lstore_3", 0), 0x43: ("fstore_0", 0),
    0x44: ("fstore_1", 0), 0x45: ("fstore_2", 0), 0x46: ("fstore_3", 0), 0x47: ("dstore_0", 0),
    0x48: ("dstore_1", 0), 0x49: ("dstore_2", 0), 0x4a: ("dstore_3", 0), 0x4b: ("astore_0", 0),
    0x4c: ("astore_1", 0), 0x4d: ("astore_2", 0), 0x4e: ("astore_3", 0), 0x4f: ("iastore", 0),
    0x50: ("lastore", 0), 0x51: ("fastore", 0), 0x52: ("dastore", 0), 0x53: ("aastore", 0),
    0x54: ("bastore", 0), 0x55: ("castore", 0), 0x56: ("sastore", 0), 0x57: ("pop", 0),
    0x58: ("pop2", 0), 0x59: ("dup", 0), 0x5a: ("dup_x1", 0), 0x5b: ("dup_x2", 0),
    0x5c: ("dup2", 0), 0x5d: ("dup2_x1", 0), 0x5e: ("dup2_x2", 0), 0x5f: ("swap", 0),
    0x60: ("iadd", 0), 0x61: ("ladd", 0), 0x62: ("fadd", 0), 0x63: ("dadd", 0), 0x64: ("isub", 0),
    0x65: ("lsub", 0), 0x66: ("fsub", 0), 0x67: ("dsub", 0), 0x68: ("imul", 0), 0x69: ("lmul", 0),
    0x6a: ("fmul", 0), 0x6b: ("dmul", 0), 0x6c: ("idiv", 0), 0x6d: ("ldiv", 0), 0x6e: ("fdiv", 0),
    0x6f: ("ddiv", 0), 0x70: ("irem", 0), 0x71: ("lrem", 0), 0x72: ("frem", 0), 0x73: ("drem", 0),
    0x74: ("ineg", 0), 0x75: ("lneg", 0), 0x76: ("fneg", 0), 0x77: ("dneg", 0), 0x78: ("ishl", 0),
    0x79: ("lshl", 0), 0x7a: ("ishr", 0), 0x7b: ("lshr", 0), 0x7c: ("iushr", 0), 0x7d: ("lushr", 0),
    0x7e: ("iand", 0), 0x7f: ("land", 0), 0x80: ("ior", 0), 0x81: ("lor", 0), 0x82: ("ixor", 0),
    0x83: ("lxor", 0), 0x84: ("iinc", 2), 0x85: ("i2l", 0), 0x86: ("i2f", 0), 0x87: ("i2d", 0),
    0x88: ("l2i", 0), 0x89: ("l2f", 0), 0x8a: ("l2d", 0), 0x8b: ("f2i", 0), 0x8c: ("f2l", 0),
    0x8d: ("f2d", 0), 0x8e: ("d2i", 0), 0x8f: ("d2l", 0), 0x90: ("d2f", 0), 0x91: ("i2b", 0),
    0x92: ("i2c", 0), 0x93: ("i2s", 0), 0x94: ("lcmp", 0), 0x95: ("fcmpl", 0), 0x96: ("fcmpg", 0),
    0x97: ("dcmpl", 0), 0x98: ("dcmpg", 0), 0x99: ("ifeq", 2), 0x9a: ("ifne", 2),
    0x9b: ("iflt", 2), 0x9c: ("ifge", 2), 0x9d: ("ifgt", 2), 0x9e: ("ifle", 2),
    0x9f: ("if_icmpeq", 2), 0xa0: ("if_icmpne", 2), 0xa1: ("if_icmplt", 2), 0xa2: ("if_icmpge", 2),
    0xa3: ("if_icmpgt", 2), 0xa4: ("if_icmple", 2), 0xa5: ("if_acmpeq", 2), 0xa6: ("if_acmpne", 2),
    0xa7: ("goto", 2), 0xa8: ("jsr", 2), 0xa9: ("ret", 1), 0xaa: ("tableswitch", -1),
    0xab: ("lookupswitch", -1), 0xac: ("ireturn", 0), 0xad: ("lreturn", 0), 0xae: ("freturn", 0),
    0xaf: ("dreturn", 0), 0xb0: ("areturn", 0), 0xb1: ("return", 0), 0xb2: ("getstatic", 2),
    0xb3: ("putstatic", 2), 0xb4: ("getfield", 2), 0xb5: ("putfield", 2), 0xb6: ("invokevirtual", 2),
    0xb7: ("invokespecial", 2), 0xb8: ("invokestatic", 2), 0xb9: ("invokeinterface", 4),
    0xba: ("invokedynamic", 4), 0xbb: ("new", 2), 0xbc: ("newarray", 1), 0xbd: ("anewarray", 2),
    0xbe: ("arraylength", 0), 0xbf: ("athrow", 0), 0xc0: ("checkcast", 2), 0xc1: ("instanceof", 2),
    0xc2: ("monitorenter", 0), 0xc3: ("monitorexit", 0), 0xc4: ("wide", -1),
    0xc5: ("multianewarray", 3), 0xc6: ("ifnull", 2), 0xc7: ("ifnonnull", 2), 0xc8: ("goto_w", 4),
}


def disasm(code, cp, labels_only=False):
    out = []
    targets = set()
    pc = 0
    instrs = []
    while pc < len(code):
        op = code[pc]
        name, ln = OPS.get(op, (f"<{op:#x}>", 0))
        arg = code[pc + 1:pc + 1 + ln]
        if ln < 0 and name == "wide":
            op2 = code[pc + 1]
            ln = 3 if op2 == 0x84 else 2
            arg = code[pc + 1:pc + 1 + ln]
        elif ln < 0 and name in ("tableswitch", "lookupswitch"):
            pad = (4 - (pc + 1) % 4) % 4
            q = pc + 1 + pad
            dflt = struct.unpack(">i", code[q:q + 4])[0]
            if name == "tableswitch":
                lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
                n = hi - lo + 1
                body = code[q:q + 12 + 4 * n]
                for k in range(n):
                    t = pc + struct.unpack(">i", body[12 + 4 * k:16 + 4 * k])[0]
                    targets.add(t)
                arg = body
                ln = len(body)
            else:
                npairs = struct.unpack(">i", code[q + 4:q + 8])[0]
                body = code[q:q + 8 + 8 * npairs]
                for k in range(npairs):
                    t = pc + struct.unpack(">i", body[8 + 8 * k + 4:12 + 8 * k + 4])[0]
                    targets.add(t)
                arg = body
                ln = len(body)
            targets.add(pc + dflt)
            extra_switch = f"  default->{pc + dflt}"
        else:
            extra_switch = ""
        extra = ""
        if name in ("tableswitch", "lookupswitch"):
            extra = "  #" + extra_switch
        elif name in ("ldc", "ldc_w", "ldc2_w"):
            idx = arg[-1] if len(arg) == 1 else struct.unpack(">H", arg)[0]
            extra = f"  # {const_str(cp, idx)}"
        elif name == "invokedynamic":
            n2, t2 = struct.unpack(">HH", arg[0:4])
            extra = f"  # {cp[n2][1]}{cp[t2][1]}"
        elif name.startswith(("get", "put", "invoke")) or name in ("new", "checkcast", "instanceof", "anewarray"):
            idx = struct.unpack(">H", arg[:2])[0]
            extra = f"  # {ref(cp, idx)}"
        elif name in ("ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "if_icmpeq", "if_icmpne",
                      "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne",
                      "goto", "ifnull", "ifnonnull"):
            t = pc + struct.unpack(">h", arg)[0]
            targets.add(t)
            extra = f"  -> {t}"
        elif name == "iinc":
            extra = f"  {arg[0]}, {arg[1]}"
        elif name in ("bipush",):
            extra = f"  {struct.unpack('>b', arg)[0]}"
        elif name in ("sipush",):
            extra = f"  {struct.unpack('>h', arg)[0]}"
        elif name.startswith(("iload", "aload", "istore", "astore", "lload", "dload", "fload",
                              "lstore", "dstore", "fstore")) and ln == 1:
            extra = f"  {arg[0]}"
        instrs.append((pc, name + extra, op, arg))
        pc += 1 + ln
    for pc, txt, op, arg in instrs:
        mark = f"{pc:5d}:" if pc in targets or pc == 0 else "      "
        out.append(f"{mark} {txt}")
    return "\n".join(out), targets


def code_of(method_attrs, cp):
    for an, ad in method_attrs:
        if cp[an][1] == "Code":
            c = C(ad)
            ms, ml, clen = struct.unpack(">HHI", c.take(8))
            code = c.take(clen)
            etl = c.u2()
            exc = []
            for _ in range(etl):
                s, e, h, ct = struct.unpack(">HHHH", c.take(8))
                exc.append((s, e, h, ct))
            attrs = []
            for _ in range(c.u2()):
                an2 = c.u2(); al2 = c.u4()
                attrs.append((cp[an2][1], c.take(al2)))
            return dict(code=code, max_stack=ms, max_locals=ml, exc=exc, attrs=attrs,
                        head=ad[:8])
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    ap.add_argument("--class", dest="cls")
    ap.add_argument("--method", dest="method")
    ap.add_argument("--attrs", action="store_true")
    a = ap.parse_args()
    data = open(a.path, "rb").read()
    if data[:2] == b"PK":
        z = zipfile.ZipFile(a.path)
        data = z.read(a.cls)
    k = parse_class(data)
    cp = k["cp"]
    print(f"{cls_name(cp, k['this'])}  major={k['major']} trailing={k['trailing']}")
    print("fields:", ", ".join(f"{cp[n][1]}:{cp[d][1]}" for _, n, d, _ in k["fields"]))
    for acc, ni, di, attrs in k["methods"]:
        name = cp[ni][1]; desc = cp[di][1]
        if a.method and a.method != name:
            continue
        print(f"\n== {name}{desc}  (acc={acc:#x})")
        co = code_of(attrs, cp)
        if co is None:
            continue
        print(f"   max_stack={co['max_stack']} max_locals={co['max_locals']} code_len={len(co['code'])} exc={co['exc']}")
        if a.attrs:
            for an, ad in co["attrs"]:
                print(f"   attr {an}: {ad.hex()}")
        txt, _ = disasm(co["code"], cp)
        print(txt)


if __name__ == "__main__":
    main()
