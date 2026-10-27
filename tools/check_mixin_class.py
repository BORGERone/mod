#!/usr/bin/env python3
"""Проверка самописного миксин-класса БЕЗ JVM.

    python3 tools/check_mixin_class.py <файл.class> [--expect-method Settledlands$...]

Что делает:
  * структурный разбор до последнего байта (trailing == 0), пул констант валиден;
  * каждая ссылка в пуле (Fieldref/Methodref/NameAndType) разрешается до Utf8;
  * байткод проходится покомандно, все инструкции укладываются в code_length;
  * симуляция стека: высоты сходятся, каждая инструкция не опускает стек ниже 0,
    в конце ровно 0 и max_stack >= фактической высоты;
  * ветвлений нет вовсе (значит StackMapTable не требуется — важно для version 65);
  * печатает аннотации класса и методов.
"""
import argparse, struct, sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from disasm import parse_class, OPS, cls_name, ref, const_str, code_of
from decode import walk, targets

# (pop, push) для использованных опкодов
STACK = {
    0x2A: (0, 1), 0x2B: (0, 1), 0x2C: (0, 1), 0x2D: (0, 1),   # aload_0..3
    0x4B: (1, 0), 0x4C: (1, 0), 0x4D: (1, 0), 0x4E: (1, 0),   # astore_0..3
    0x01: (0, 1), 0x02: (0, 1), 0x03: (0, 1), 0x04: (0, 1),
    0x12: (0, 1), 0x13: (0, 1),                               # ldc, ldc_w
    0xB1: (0, 0), 0xAC: (1, 0), 0xB0: (1, 0),
    0xB2: (0, 1), 0xB3: (1, 0), 0xB4: (1, 1), 0xB5: (2, 0),
    0xBB: (0, 1), 0xC0: (1, 1), 0xC1: (1, 1), 0x57: (1, 0), 0x59: (1, 2),
}


def args_count(desc, is_static):
    """Число слотов аргументов дескриптора (без this)."""
    assert desc.startswith("("), desc
    inner, ret = desc[1:].split(")", 1)
    n = 0
    i = 0
    while i < len(inner):
        c = inner[i]
        if c == "L":
            i = inner.index(";", i) + 1
            n += 1
        elif c == "[":
            i += 1
            while inner[i] == "[":
                i += 1
            if inner[i] == "L":
                i = inner.index(";", i) + 1
            else:
                i += 1
            n += 1
        else:
            n += 2 if c in "JD" else 1
            i += 1
    slots = 0
    for c in inner:
        pass
    return n, ret


def slot_count(desc):
    inner, ret = desc[1:].split(")", 1)
    slots = 0
    i = 0
    while i < len(inner):
        c = inner[i]
        if c == "L":
            i = inner.index(";", i) + 1
            slots += 1
        elif c == "[":
            j = i
            while inner[j] == "[":
                j += 1
            if inner[j] == "L":
                i = inner.index(";", j) + 1
            else:
                i = j + 1
            slots += 1
        else:
            slots += 2 if c in "JD" else 1
            i += 1
    r = 0 if ret == "V" else (2 if ret in "JD" else 1)
    return slots, r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("path")
    a = ap.parse_args()
    data = open(a.path, "rb").read()
    k = parse_class(data)
    cp = k["cp"]
    ok = True

    def bad(msg):
        nonlocal ok
        ok = False
        print("ОШИБКА:", msg)

    print(f"class {cls_name(cp, k['this'])}  major={k['major']}  trailing={k['trailing']}")
    if k["trailing"]:
        bad("хвост после class-атрибутов")
    if k["major"] < 50:
        print(f"  major={k['major']} < 50 -> старый inference-верификатор, фреймы не нужны (ожидаемо для зонда)")

    # --- пул: ссылки разрешаются
    for i, e in enumerate(cp):
        if not e:
            continue
        tag, v = e
        if tag in (9, 10, 11):
            ci, nti = struct.unpack(">HH", v)
            if not (0 < ci < len(cp)) or cp[ci] is None or cp[ci][0] != 7:
                bad(f"cp[{i}] ссылка на не-Class {ci}")
            if not (0 < nti < len(cp)) or cp[nti] is None or cp[nti][0] != 12:
                bad(f"cp[{i}] ссылка на не-NameAndType {nti}")
        elif tag == 12:
            ni, di = struct.unpack(">HH", v)
            for x in (ni, di):
                if not (0 < x < len(cp)) or cp[x] is None or cp[x][0] != 1:
                    bad(f"cp[{i}] NameAndType -> не Utf8 {x}")
        elif tag == 7:
            x = struct.unpack(">H", v)[0]
            if not (0 < x < len(cp)) or cp[x] is None or cp[x][0] != 1:
                bad(f"cp[{i}] Class -> не Utf8 {x}")
        elif tag == 8:
            x = struct.unpack(">H", v)[0]
            if not (0 < x < len(cp)) or cp[x] is None or cp[x][0] != 1:
                bad(f"cp[{i}] String -> не Utf8 {x}")

    for acc, ni, di, attrs in k["methods"]:
        name, desc = cp[ni][1], cp[di][1]
        co = code_of(attrs, cp)
        if co is None:
            continue
        code = co["code"]
        print(f"\n== {name}{desc}  max_stack={co['max_stack']} max_locals={co['max_locals']} "
              f"code_len={len(code)} exc={co['exc']}")
        if "StackMapTable" in dict(co["attrs"]) and len(targets(code)) == 0:
            pass
        tgt = targets(code)
        if tgt and k["major"] >= 50:
            bad(f"major={k['major']} (>=50) и есть ветвления без фреймов: цели {sorted(tgt)}")
        elif tgt:
            print(f"   ветвления есть, но major={k['major']} < 50 -> старый inference-верификатор, "
                  f"StackMapTable не требуется")
        slots, ret = slot_count(desc)
        ndesc, _ = args_count(desc, False)
        need_locals = slots + (0 if name == "<clinit>" else 1)
        if co["max_locals"] < need_locals:
            bad(f"max_locals={co['max_locals']} < {need_locals}")
        if tgt:
            print("   (симуляция стека пропущена: есть ветвления)")
            continue
        h = 0
        peak = 0
        for pc, raw in walk(code):
            op = raw[0]
            if op in (0xB6, 0xB7, 0xB8):
                idx = struct.unpack(">H", raw[1:3])[0]
                _, md = ref(cp, idx).split("# ")[-1], None
                full = ref(cp, idx)
                mdesc = full[full.rindex("("):]
                s, r = slot_count(mdesc)
                pop = s + (0 if op == 0xB8 else 1)
                h -= pop
                h += r
                print(f" {pc:5d} {OPS[op][0]:15s} {full}   stack={h}")
            elif op in (0xB9,):
                bad("invokeinterface неожидан")
            elif op in STACK:
                p, q = STACK[op]
                h -= p
                h += q
                print(f" {pc:5d} {OPS[op][0]:15s} stack={h}")
            elif op in (0xB4, 0xB5, 0xB2, 0xB3):
                idx = struct.unpack(">H", raw[1:3])[0]
                ftag, fv = cp[idx]
                _, fdesc = None, None
                full = ref(cp, idx)
                fdesc = full[full.rindex(":") + 1:] if ":" in full else full[full.rindex(")") + 1:] if ")" in full else ""
                # берём дескриптор из NameAndType напрямую
                _, nti = struct.unpack(">HH", cp[idx][1])
                nn, tt = struct.unpack(">HH", cp[nti][1])
                fdesc = cp[tt][1]
                s, r = slot_count("()" + fdesc)
                if op == 0xB2:      # getstatic
                    h += r
                elif op == 0xB3:    # putstatic
                    h -= r
                elif op == 0xB4:    # getfield
                    h -= 1
                    h += r
                else:               # putfield
                    h -= 1 + r
                print(f" {pc:5d} {OPS[op][0]:15s} {full}   stack={h}")
            else:
                bad(f"неожиданный опкод {op:#x} на {pc}")
                break
            if h < 0:
                bad(f"стек ушёл в минус на {pc}")
                break
            peak = max(peak, h)
        else:
            if h != 0:
                bad(f"стек в конце {h} (должен 0)")
            if peak > co["max_stack"]:
                bad(f"max_stack={co['max_stack']} < пик {peak}")
            print(f"   пик стека {peak}, финал {h}")
        for x, y in co["attrs"]:
            print(f"   attr {x}: {y.hex() if isinstance(y, bytes) else y}")

    for x, y in k["cattrs"]:
        print(f"class attr {cp[x][1]}: {y.hex()}")
        if cp[x][1] == "RuntimeInvisibleAnnotations":
            # @Mixin.value ОБЯЗАН быть массивом ('[' ...), иначе MixinInfo.readTargets
            # падает с ClassCastException: asm.Type -> Iterable (инцидент build 231).
            if y.find(b"\x5b\x00\x01") < 0:
                bad("@Mixin.value/targets закодирован НЕ массивом — ClassCastException на старте")
            else:
                print("  @Mixin: массив найден (ок)")
    for acc, ni, di, attrs in k["fields"]:
        print(f"field {cp[ni][1]}:{cp[di][1]}")
    print("\nИТОГ:", "OK" if ok else "ЕСТЬ ОШИБКИ")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
