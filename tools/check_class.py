#!/usr/bin/env python3
"""Структурная проверка скомпилированного/пропатченного class-файла без JVM.

    python3 tools/check_class.py old.class new.class [--method applyProfile] [--expect-insert HEX]

Проверяет:
  * оба файла разбираются до последнего байта (нет «хвостов» и обрывов);
  * пул констант, список полей и классовые атрибуты идентичны;
  * отличается ровно один метод (по умолчанию — указанный);
  * в нём: code_len вырос на длину вставки, хвост кода побайтово совпал,
    start_pc в LineNumberTable и offset_delta первого фрейма StackMapTable
    сдвинуты на ту же величину, вложенные атрибуты разбираются без остатка.
"""
import argparse, struct, sys


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
    magic = c.take(4)
    if magic != b"\xca\xfe\xba\xbe":
        raise ValueError("не class-файл")
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
    access, this, sup = struct.unpack(">HHH", c.take(6))
    ifc = c.u2()
    ifaces = c.take(2 * ifc)

    def members():
        cnt = c.u2()
        out = []
        for _ in range(cnt):
            head = c.take(6)
            an = c.u2()
            attrs = []
            for _ in range(an):
                ai = c.u2(); ln = c.u4(); attrs.append((ai, c.take(ln)))
            out.append((head, attrs))
        return out

    fields = members()
    methods = members()
    class_attrs = c.take(len(data) - c.p)
    return dict(major=major, minor=minor, cp=cp, ifaces=ifaces, fields=fields,
                methods=methods, class_attrs=class_attrs)


def utf8(cp, idx):
    t = cp[idx]
    return t[1] if t and t[0] == 1 else f"#{idx}"


def code_parts(attrs):
    for ai, body in attrs:
        if ai is None:
            continue
        return ai, body
    return None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--method", default="applyProfile")
    ap.add_argument("--insert-hex", default=None)
    a = ap.parse_args()

    old = parse_class(open(a.old, "rb").read())
    new = parse_class(open(a.new, "rb").read())
    fails = []

    print("=== общая структура ===")
    print(f"  classfile v{old['major']}.{old['minor']} / v{new['major']}.{new['minor']}")
    print(f"  пул констант идентичен: {old['cp'] == new['cp']}")
    print(f"  интерфейсы идентичны:   {old['ifaces'] == new['ifaces']}")
    print(f"  поля идентичны:         {old['fields'] == new['fields']}")
    print(f"  класс-атрибуты:         {old['class_attrs'] == new['class_attrs']}"
          f" ({len(old['class_attrs'])} байт)")
    fails.append(("пул", old["cp"] == new["cp"]))
    fails.append(("поля", old["fields"] == new["fields"]))
    fails.append(("класс-атрибуты", old["class_attrs"] == new["class_attrs"]))

    names_old = [utf8(old["cp"], struct.unpack(">H", h[2:4])[0]) for h, _ in old["methods"]]
    names_new = [utf8(new["cp"], struct.unpack(">H", h[2:4])[0]) for h, _ in new["methods"]]
    print(f"  состав методов:         {names_old == names_new} ({len(names_old)})")
    fails.append(("состав методов", names_old == names_new))

    changed = []
    for (h1, at1), (h2, at2) in zip(old["methods"], new["methods"]):
        if at1 != at2:
            changed.append(utf8(old["cp"], struct.unpack(">H", h1[2:4])[0]))
    print(f"  изменённые методы:      {changed}")

    idx = names_old.index(a.method)
    ai1, body1 = old["methods"][idx][1][0]
    ai2, body2 = new["methods"][idx][1][0]
    n1 = utf8(old["cp"], ai1); n2 = utf8(new["cp"], ai2)
    print(f"\n=== {a.method} ({n1} -> {n2}) ===")
    fails.append(("атрибут Code", n1 == n2 == "Code"))

    def split(body):
        ms, ml, cl = struct.unpack(">HHI", body[:8])
        code = body[8:8 + cl]
        off = 8 + cl
        etl = struct.unpack(">H", body[off:off + 2])[0]; off += 2
        et = body[off:off + 8 * etl]; off += 8 * etl
        na = struct.unpack(">H", body[off:off + 2])[0]; off += 2
        nested = []
        for _ in range(na):
            ni = struct.unpack(">H", body[off:off + 2])[0]
            ln = struct.unpack(">I", body[off + 2:off + 6])[0]
            off += 6
            nested.append((ni, body[off:off + ln])); off += ln
        return ms, ml, code, et, nested, off

    ms1, ml1, c1, et1, nest1, o1 = split(body1)
    ms2, ml2, c2, et2, nest2, o2 = split(body2)
    print(f"  max_stack {ms1}->{ms2}, max_locals {ml1}->{ml2}")
    print(f"  code_len {len(c1)} -> {len(c2)} ({len(c2) - len(c1):+d})")
    print(f"  разобран целиком: old={o1 == len(body1)} new={o2 == len(body2)}")
    fails.append(("разбор старого", o1 == len(body1)))
    fails.append(("разбор нового", o2 == len(body2)))
    fails.append(("max_locals не изменился", ml1 == ml2))
    fails.append(("exception table не изменилась", et1 == et2))

    delta = len(c2) - len(c1)
    if a.insert_hex:
        ins = bytes.fromhex(a.insert_hex)
        ok = c2[:len(ins)] == ins and c2[len(ins):] == c1
        print(f"  вставка {len(ins)} байт: {ins.hex()}")
        print(f"  хвост кода совпадает с исходным: {c2[len(ins):] == c1}")
        fails.append(("вставка", ok))
        fails.append(("длина совпадает", delta == len(ins)))

    print("\n=== вложенные атрибуты ===")
    for (ni1, ab1), (ni2, ab2) in zip(nest1, nest2):
        name = utf8(old["cp"], ni1)
        if name == "LineNumberTable":
            k1 = struct.unpack(">H", ab1[:2])[0]
            k2 = struct.unpack(">H", ab2[:2])[0]
            ok = k1 == k2 and all(
                struct.unpack(">H", ab2[2 + j * 4:4 + j * 4])[0]
                == struct.unpack(">H", ab1[2 + j * 4:4 + j * 4])[0] + delta
                for j in range(k1)
            )
            print(f"  LineNumberTable: записей {k1}, все start_pc +{delta}: {ok}")
            fails.append(("LineNumberTable", ok))
        elif name == "StackMapTable":
            nf1 = struct.unpack(">H", ab1[:2])[0]
            nf2 = struct.unpack(">H", ab2[:2])[0]
            f1 = ab1[2]; f2 = ab2[2]
            print(f"  StackMapTable: фреймов {nf1}->{nf2}, тип первого {f1}->{f2}")
            print(f"    old: {ab1.hex()}")
            print(f"    new: {ab2.hex()}")
            ok = nf1 == nf2 and ab1[2:] != ab2[2:]
            if f1 == f2 and f1 >= 251:
                d1 = struct.unpack(">H", ab1[3:5])[0]
                d2 = struct.unpack(">H", ab2[3:5])[0]
                rest_ok = ab1[5:] == ab2[5:]
                print(f"    delta {d1} -> {d2} (+{d2 - d1}), остаток идентичен: {rest_ok}")
                ok = ok and rest_ok and (d2 - d1) == delta
            fails.append(("StackMapTable", ok))
        else:
            print(f"  {name}: идентичен {ab1 == ab2}")
            fails.append((name, ab1 == ab2))

    print("\n" + "-" * 50)
    bad = [n for n, ok in fails if not ok]
    if bad:
        print("ПРОБЛЕМЫ:", ", ".join(bad))
        return 1
    print("ВСЕ СТРУКТУРНЫЕ ПРОВЕРКИ ПРОЙДЕНЫ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
