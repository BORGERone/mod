#!/usr/bin/env python3
"""Точечная правка байткода существующих классов: вставка инструкций в `Code`.

Зачем: компилятора в песочнице нет, а вставлять байты «на глаз» уже стоило
трёх отозванных сборок (230 — AIOOBE при загрузке миксинов, 233 — пул,
234 — смещения переходов). Здесь всё считается, а не угадывается:

  * инструкции декодируются, цели переходов и switch пересчитываются;
  * exception_table переезжает вместе с кодом;
  * LineNumberTable.start_pc сдвигается;
  * StackMapTable пересобирается из разобранных фреймов (tools/smt.py);
  * max_stack можно поднять с запасом — это всегда безопасно.

Правка идёт сплайсом: меняется только тело нужного `Code`, остальной файл
(и весь пул, кроме добавленных в конец записей) остаётся байт-в-байт.
"""
import struct

import decode
import smt


class Cursor:
    def __init__(self, d, p=0):
        self.d = d
        self.p = p

    def u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def u2(self):
        v = struct.unpack(">H", self.d[self.p:self.p + 2])[0]; self.p += 2; return v

    def u4(self):
        v = struct.unpack(">I", self.d[self.p:self.p + 4])[0]; self.p += 4; return v

    def take(self, n):
        v = self.d[self.p:self.p + n]; self.p += n; return v


class Klass:
    """Разбор class-файла с запоминанием файловых смещений."""

    def __init__(self, raw):
        self.raw = raw
        c = Cursor(raw)
        assert c.take(4) == b"\xca\xfe\xba\xbe", "не class-файл"
        self.minor = c.u2(); self.major = c.u2()
        self.cp_count = c.u2()
        self._dirty = False
        self.cp = [None] * self.cp_count
        i = 1
        while i < self.cp_count:
            start = c.p
            tag = c.u1()
            if tag == 1:
                ln = c.u2(); s = c.take(ln)
                self.cp[i] = dict(tag=1, s=s, start=start)
            elif tag in (7, 8, 16, 19, 20):
                a = c.u2(); self.cp[i] = dict(tag=tag, a=a, start=start)
            elif tag == 15:
                a = c.u1(); b = c.u2(); self.cp[i] = dict(tag=tag, a=a, b=b, start=start)
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                a = c.u2(); b = c.u2(); self.cp[i] = dict(tag=tag, a=a, b=b, start=start)
            elif tag in (5, 6):
                raw8 = c.take(8); self.cp[i] = dict(tag=tag, raw=raw8, start=start); i += 1
            else:
                raise ValueError(f"неизвестный тег пула {tag} на {start}")
            i += 1
        self.cp_end = c.p
        self.access = c.u2(); self.this = c.u2(); self.super = c.u2()
        self.ifaces = [c.u2() for _ in range(c.u2())]
        self.fields = self._members(c)
        self.methods = self._members(c)
        self.attrs = self._attrs(c)
        assert c.p == len(raw), "хвост после разбора"

    def _attrs(self, c):
        out = []
        for _ in range(c.u2()):
            ni = c.u2(); ln = c.u4(); body_start = c.p
            body = c.take(ln)
            out.append(dict(name_idx=ni, name=self.utf8(ni), body=body,
                            body_start=body_start, len=ln))
        return out

    def _members(self, c):
        out = []
        for _ in range(c.u2()):
            acc = c.u2(); ni = c.u2(); di = c.u2()
            out.append(dict(acc=acc, name_idx=ni, desc_idx=di,
                            name=self.utf8(ni), desc=self.utf8(di),
                            attrs=self._attrs(c)))
        return out

    def utf8(self, idx):
        e = self.cp[idx]
        return e["s"].decode("utf-8", "replace") if e and e["tag"] == 1 else None

    def find_method(self, name, desc=None):
        for m in self.methods:
            if m["name"] == name and (desc is None or m["desc"] == desc):
                return m
        return None

    # ---- добавление записей в пул (в конец, индексы существующих не меняются)
    def add_utf8(self, s):
        b = s.encode() if isinstance(s, str) else s
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 1 and e["s"] == b:
                return i
        self._dirty = True
        idx = len(self.cp)
        self.cp.append(dict(tag=1, s=b, start=None))
        return idx

    def _add(self, tag, a, b=None):
        self._dirty = True
        idx = len(self.cp)
        self.cp.append(dict(tag=tag, a=a, b=b, start=None))
        return idx

    def add_class(self, internal_name):
        u = self.add_utf8(internal_name)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 7 and e["a"] == u:
                return i
        return self._add(7, u)

    def add_nat(self, name, desc):
        un = self.add_utf8(name); ud = self.add_utf8(desc)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 12 and e["a"] == un and e["b"] == ud:
                return i
        return self._add(12, un, ud)

    def add_fieldref(self, owner, name, desc):
        cls = self.add_class(owner); nat = self.add_nat(name, desc)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 9 and e["a"] == cls and e["b"] == nat:
                return i
        return self._add(9, cls, nat)

    def add_methodref(self, owner, name, desc):
        cls = self.add_class(owner); nat = self.add_nat(name, desc)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 10 and e["a"] == cls and e["b"] == nat:
                return i
        return self._add(10, cls, nat)

    def add_interfaceref(self, owner, name, desc):
        cls = self.add_class(owner); nat = self.add_nat(name, desc)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 11 and e["a"] == cls and e["b"] == nat:
                return i
        return self._add(11, cls, nat)

    def add_string(self, s):
        u = self.add_utf8(s)
        for i, e in enumerate(self.cp):
            if e and e["tag"] == 8 and e["a"] == u:
                return i
        return self._add(8, u)

    # ---- сборка файла
    def _pool_bytes(self):
        if not self._dirty:
            return self.raw[10:self.cp_end]
        out = []
        for i in range(1, len(self.cp)):
            e = self.cp[i]
            if e is None:
                continue
            out.append(self._encode_entry(e))
        return b"".join(out)

    def _encode_entry(self, e):
        tag = e["tag"]
        if tag == 1:
            return b"\x01" + struct.pack(">H", len(e["s"])) + e["s"]
        if tag in (7, 8, 16, 19, 20):
            return bytes([tag]) + struct.pack(">H", e["a"])
        if tag == 15:
            return bytes([tag]) + struct.pack(">BH", e["a"], e["b"])
        if tag in (5, 6):
            return bytes([tag]) + e["raw"]
        return bytes([tag]) + struct.pack(">HH", e["a"], e["b"])

    def dump(self, method, new_body):
        """Файл с заменённым телом Code у метода (сплайс, остальное байт-в-байт)."""
        ca = [a for a in method["attrs"] if a["name"] == "Code"][0]
        bs = ca["body_start"]
        return (self.raw[:8] + struct.pack(">H", len(self.cp)) + self._pool_bytes()
                + self.raw[self.cp_end:bs - 4]
                + struct.pack(">I", len(new_body)) + new_body
                + self.raw[bs + ca["len"]:])


def code_parts(klass, method):
    """Разобрать Code: code, max_stack, max_locals, exception_table, вложенные атрибуты."""
    a = [x for x in method["attrs"] if x["name"] == "Code"][0]
    b = a["body"]
    ms, ml = struct.unpack(">HH", b[0:4])
    n = struct.unpack(">I", b[4:8])[0]
    code = b[8:8 + n]
    p = 8 + n
    ec = struct.unpack(">H", b[p:p + 2])[0]; p += 2
    exc = []
    for _ in range(ec):
        s, e, h, ct = struct.unpack(">HHHH", b[p:p + 8]); p += 8
        exc.append([s, e, h, ct])
    nested = []
    ac = struct.unpack(">H", b[p:p + 2])[0]; p += 2
    for _ in range(ac):
        ni = struct.unpack(">H", b[p:p + 2])[0]; p += 2
        ln = struct.unpack(">I", b[p:p + 4])[0]; p += 4
        nested.append([ni, klass.utf8(ni), b[p:p + ln]])
        p += ln
    assert p == len(b), "Code разобран не до конца"
    return dict(max_stack=ms, max_locals=ml, code=code, exc=exc, nested=nested)


def build_code_body(cp, parts):
    out = [struct.pack(">HH", parts["max_stack"], parts["max_locals"]),
           struct.pack(">I", len(parts["code"])), parts["code"],
           struct.pack(">H", len(parts["exc"]))]
    for s, e, h, ct in parts["exc"]:
        out.append(struct.pack(">HHHH", s, e, h, ct))
    out.append(struct.pack(">H", len(parts["nested"])))
    for ni, name, body in parts["nested"]:
        out.append(struct.pack(">HI", ni, len(body)))
        out.append(body)
    return b"".join(out)


def insert(parts, at, ins, cp=None):
    """Вставить ins по смещению at. Возвращает новые parts."""
    code = parts["code"]
    # tableswitch/lookupswitch выравниваются по 4 байта от АБСОЛЮТНОГО адреса,
    # поэтому длина вставки обязана быть кратна 4 — иначе сдвиг меняет padding
    # внутри switch-инструкции и код разъезжается (инцидент 236-alpha).
    if len(ins) % 4:
        ins = ins + b"\x00" * (4 - len(ins) % 4)
    n = len(ins)
    if at not in instruction_starts(code):
        raise ValueError(f"{at} — не граница инструкции")

    def f(p):
        return p + n if p >= at else p

    new_code = bytearray(code[:at] + ins + code[at:])
    # пересчёт переходов, которые «перешагивают» точку вставки
    p = 0
    L = len(code)
    while p < L:
        ln = decode.instr_len(code, p)
        raw = bytearray(code[p:p + ln])
        op = code[p]
        if op in (0xA7, 0xA8) or 0x99 <= op <= 0xA6 or op in (0xC6, 0xC7):
            t = p + struct.unpack(">h", raw[1:3])[0]
            if f(t) - f(p) != t - p:
                nt = f(t) - f(p)
                struct.pack_into(">h", raw, 1, nt)
                new_code[f(p):f(p) + ln] = raw
            p += ln
            continue
        if op == 0xC8 or op == 0xC9:
            t = p + struct.unpack(">i", raw[1:5])[0]
            if f(t) - f(p) != t - p:
                struct.pack_into(">i", raw, 1, f(t) - f(p))
                new_code[f(p):f(p) + ln] = raw
            p += ln
            continue
        if op in (0xAA, 0xAB):
            pad = (4 - (p + 1) % 4) % 4
            q = p + 1 + pad
            dflt = p + struct.unpack(">i", code[q:q + 4])[0]
            struct.pack_into(">i", raw, q - p, f(dflt) - f(p))
            if op == 0xAA:
                lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
                for k in range(hi - lo + 1):
                    off = q + 12 + 4 * k
                    t = p + struct.unpack(">i", code[off:off + 4])[0]
                    struct.pack_into(">i", raw, off - p, f(t) - f(p))
            else:
                np_ = struct.unpack(">i", code[q + 4:q + 8])[0]
                for k in range(np_):
                    off = q + 8 + 8 * k + 4
                    t = p + struct.unpack(">i", code[off:off + 4])[0]
                    struct.pack_into(">i", raw, off - p, f(t) - f(p))
            new_code[f(p):f(p) + ln] = raw
            p += ln
            continue
        p += ln
    parts = dict(parts)
    parts["code"] = bytes(new_code)
    parts["exc"] = [[f(s), f(e), f(h), ct] for s, e, h, ct in parts["exc"]]
    nested = []
    for ni, name, body in parts["nested"]:
        if name == "LineNumberTable":
            cnt = struct.unpack(">H", body[0:2])[0]
            out = [body[0:2]]
            for i in range(cnt):
                sp, ln = struct.unpack(">HH", body[2 + 4 * i:6 + 4 * i])
                out.append(struct.pack(">HH", f(sp), ln))
            nested.append([ni, name, b"".join(out)])
        elif name == "StackMapTable":
            frames = smt.shift(smt.parse(body), at, n)
            nested.append([ni, name, smt.build(frames)])
        else:
            nested.append([ni, name, body])
    parts["nested"] = nested
    return parts


def instruction_starts(code):
    out = set()
    p = 0
    while p < len(code):
        out.add(p)
        p += decode.instr_len(code, p)
    return out
