#!/usr/bin/env python3
"""СТРОГИЙ валидатор jar: то, что обязан прогнать NeoForge-сканер, но без игры.

    python3 tools/validate_jar.py <jar> [--base prev.jar]

Почему появился (инцидент build 233): NeoForge на старте читает КАЖДЫЙ класс в jar
(`net.neoforged.fml.loading.modscan.Scanner` -> ASM ClassReader). Класс с ссылкой на
несуществующий индекс пула валит скан:
    java.lang.ArrayIndexOutOfBoundsException: Index 79 out of bounds for length 79
    at org.objectweb.asm.ClassReader.readUtf
и игра падает ДО загрузки модов. Прошлые чекеры смотрели только «существующие»
записи пула и пропускали такой дефект.

Проверки:
  1. zip цел, дублей нет;
  2. toml парсится (tomllib), mixins.json парсится, все миксины из конфигов есть в jar;
  3. КАЖДЫЙ .class: структура до последнего байта, без хвостов;
  4. все индексы пула (this/super/interfaces/поля/методы/атрибуты/операнды)
     существуют и имеют правильный тег — то, на чём упал 233;
  5. байткод: инструкции укладываются в code_length, операнды-индексы валидны,
     exception_table в границах кода;
  6. вложенные атрибуты (Code/LineNumberTable/StackMapTable/аннотации) разбираются
     ровно до конца;
  7. версия класса 49..65;
  8. @Mixin.value/targets закодированы массивом (иначе ClassCastException на prepare);
  9. --base: побайтовый дифф записей.
"""
import argparse, json, struct, sys, zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from disasm import parse_class, OPS, cls_name, ref, const_str  # noqa: E402
from decode import instr_len  # noqa: E402
import smt  # noqa: E402

ERRORS = []
WARN = []


def err(where, msg):
    ERRORS.append(f"{where}: {msg}")


def u1(b, p):
    return b[p]


def u2(b, p):
    return struct.unpack(">H", b[p:p + 2])[0]


def u4(b, p):
    return struct.unpack(">I", b[p:p + 4])[0]


# ---------------------------------------------------------------- constant pool
def check_pool(where, cp):
    n = len(cp)
    for i, e in enumerate(cp):
        if not e:
            continue
        tag, v = e
        if tag == 1:
            continue
        if tag in (7, 8, 16, 19, 20):          # Class, String, MethodType, Module, Package
            x = struct.unpack(">H", v)[0]
            if not (0 < x < n) or cp[x] is None or cp[x][0] != 1:
                err(where, f"cp[{i}] тег {tag} -> utf8 #{x} не существует")
        elif tag == 15:                        # MethodHandle
            kind = v[0]
            x = struct.unpack(">H", v[1:3])[0]
            if not (1 <= kind <= 9):
                err(where, f"cp[{i}] MethodHandle: недопустимый kind {kind}")
            if not (0 < x < n) or cp[x] is None or cp[x][0] not in (9, 10, 11):
                err(where, f"cp[{i}] MethodHandle -> #{x} не Fieldref/Methodref")
        elif tag in (9, 10, 11):               # Fieldref/Methodref/InterfaceMethodref
            ci, nti = struct.unpack(">HH", v)
            if not (0 < ci < n) or cp[ci] is None or cp[ci][0] != 7:
                err(where, f"cp[{i}] ref -> class #{ci} не Class")
            if not (0 < nti < n) or cp[nti] is None or cp[nti][0] != 12:
                err(where, f"cp[{i}] ref -> NameAndType #{nti} нет")
        elif tag == 12:                        # NameAndType
            a, b = struct.unpack(">HH", v)
            for x in (a, b):
                if not (0 < x < n) or cp[x] is None or cp[x][0] != 1:
                    err(where, f"cp[{i}] NameAndType -> utf8 #{x} нет")
        elif tag in (17, 18):                  # Dynamic / InvokeDynamic
            bsm, nti = struct.unpack(">HH", v)
            if not (0 < nti < n) or cp[nti] is None or cp[nti][0] != 12:
                err(where, f"cp[{i}] indy/dyn -> NameAndType #{nti} нет")
        elif tag in (3, 4, 5, 6):
            pass
        else:
            err(where, f"cp[{i}] неизвестный тег {tag}")


def utf8_ok(where, cp, idx, field):
    if not (0 < idx < len(cp)) or cp[idx] is None or cp[idx][0] != 1:
        err(where, f"{field}: utf8 #{idx} вне пула/не Utf8 (пул={len(cp)})")


def class_ok(where, cp, idx, field):
    if not (0 < idx < len(cp)) or cp[idx] is None or cp[idx][0] != 7:
        err(where, f"{field}: class #{idx} вне пула/не Class (пул={len(cp)})")


# ---------------------------------------------------------------- code
CP_OPS_U1 = {0x12: (3, 4, 5, 6, 7, 8, 15, 16, 17, 18, 19, 20)}
CP_OPS_U2 = {
    0x13: (3, 4, 5, 6, 7, 8, 15, 16, 17, 18, 19, 20),
    0x14: (5, 6),
    0xB2: (9,), 0xB3: (9,), 0xB4: (9,), 0xB5: (9,),
    0xB6: (10,), 0xB7: (10, 11), 0xB8: (10, 11),
    0xB9: (11,), 0xBA: (18,),
    0xBB: (7,), 0xBD: (7,), 0xC0: (7,), 0xC1: (7,), 0xC5: (7,),
}


BRANCH2 = {0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6,
           0xA7, 0xC6, 0xC7}
BRANCH4 = {0xC8}


def desc_slots(desc):
    """Слоты аргументов метода в дескрипторе (long/double — два)."""
    i = 1
    end = desc.index(")")
    n = 0
    while i < end:
        c = desc[i]
        if c == "[":
            while desc[i] == "[":
                i += 1
            if desc[i] == "L":
                i = desc.index(";", i)
            i += 1
            n += 1
        elif c == "L":
            i = desc.index(";", i) + 1
            n += 1
        else:
            n += 2 if c in "JD" else 1
            i += 1
    return n


def check_code(where, cp, code, max_stack, max_locals, exc, nested, cp_count):
    if max_stack > 65535 or max_locals > 65535:
        err(where, "max_stack/max_locals вне u2")
    pc = 0
    n = len(code)
    while pc < n:
        op = code[pc]
        if op not in OPS:
            err(where, f"неизвестный опкод {op:#x} на {pc}")
            return
        try:
            ln = instr_len(code, pc)
        except Exception as e:                 # noqa: BLE001
            err(where, f"не удалось декодировать инструкцию на {pc}: {e}")
            return
        if pc + ln > n:
            err(where, f"инструкция на {pc} выходит за code_length")
            return
        raw = code[pc:pc + ln]
        if op in CP_OPS_U1:
            idx = raw[1]
            if not (0 < idx < cp_count) or cp[idx] is None or cp[idx][0] not in CP_OPS_U1[op]:
                err(where, f"ldc на {pc}: индекс #{idx} не в пуле или неверный тег")
        elif op in CP_OPS_U2:
            idx = struct.unpack(">H", raw[1:3])[0]
            if not (0 < idx < cp_count) or cp[idx] is None or cp[idx][0] not in CP_OPS_U2[op]:
                err(where, f"{OPS[op][0]} на {pc}: индекс #{idx} не в пуле или неверный тег")
            elif op == 0xB9:
                # count = objectref + слоты аргументов; long/double — два слота
                ci, nti = struct.unpack(">HH", cp[idx][1])
                desc = None
                if cp[nti] and cp[nti][0] == 12:
                    mu, du = struct.unpack(">HH", cp[nti][1])
                    if cp[du] and cp[du][0] == 1:
                        desc = cp[du][1]
                if desc:
                    slots = 1 + desc_slots(desc)
                    if raw[3] != slots:
                        err(where, f"invokeinterface на {pc}: count={raw[3]}, а по дескриптору "
                                   f"{desc} нужно {slots} (аргументы + objectref) — VerifyError")
                if raw[4] != 0:
                    err(where, f"invokeinterface на {pc}: последний байт должен быть 0")
            elif op == 0xC5:
                if raw[3] == 0:
                    err(where, f"multianewarray на {pc}: dims=0")
        pc += ln
    # цели переходов ОБЯЗАНЫ попадать на границы инструкций
    # (инцидент 234: VerifyError "Illegal target of jump or branch")
    starts = set()
    p2 = 0
    while p2 < n:
        starts.add(p2)
        try:
            p2 += instr_len(code, p2)
        except Exception:                      # noqa: BLE001
            break
    p3 = 0
    while p3 < n:
        op = code[p3]
        try:
            ln = instr_len(code, p3)
        except Exception:                      # noqa: BLE001
            break
        raw = code[p3:p3 + ln]
        targets = []
        if op in BRANCH2:
            targets.append(p3 + struct.unpack(">h", raw[1:3])[0])
        elif op in BRANCH4:
            targets.append(p3 + struct.unpack(">i", raw[1:5])[0])
        elif op in (0xAA, 0xAB):
            pad = (4 - (p3 + 1) % 4) % 4
            q = p3 + 1 + pad
            targets.append(p3 + struct.unpack(">i", code[q:q + 4])[0])
            if op == 0xAA:
                lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
                for k in range(hi - lo + 1):
                    targets.append(p3 + struct.unpack(">i", code[q + 12 + 4 * k:q + 16 + 4 * k])[0])
            else:
                npr = struct.unpack(">i", code[q + 4:q + 8])[0]
                for k in range(npr):
                    targets.append(p3 + struct.unpack(">i", code[q + 8 + 8 * k + 4:q + 12 + 8 * k + 4])[0])
        for t in targets:
            if t not in starts:
                err(where, f"переход на {p3} ведёт на {t}, а это НЕ граница инструкции")
        p3 += ln
    for (s, e, h, ct) in exc:
        if h not in starts:
            err(where, f"exception handler {h} не на границе инструкции")
        if not (s < e <= n):
            err(where, f"exception_table: границы {s}..{e} вне кода (len={n})")
        if h >= n:
            err(where, f"exception_table: handler {h} вне кода")
        if ct != 0:
            class_ok(where, cp, ct, "exception_table.catch_type")
    # --- StackMapTable против кода (инцидент 230/234) ---------------------
    # Каждая ветка и каждый exception handler ОБЯЗАНЫ иметь фрейм (major >= 50),
    # иначе JVM на этапе верификации падает. Инструкция сразу после
    # безусловного перехода тоже требует фрейма.
    frames = []
    for an, body in nested:
        if an == "StackMapTable":
            try:
                frames = smt.parse(body)
            except Exception as e:                 # noqa: BLE001
                err(where, f"StackMapTable: фреймы не разбираются: {e}")
                frames = []
    if frames:
        fset = set()
        prev = -1
        for f in frames:
            off = f["offset"]
            if off >= n:
                err(where, f"StackMapTable: фрейм на {off} вне кода (len={n})")
            elif off not in starts:
                err(where, f"StackMapTable: фрейм на {off} — НЕ граница инструкции")
            if off <= prev:
                err(where, f"StackMapTable: фреймы не по возрастанию ({off} после {prev})")
            fset.add(off)
            prev = off
        need = set()
        p4 = 0
        while p4 < n:
            op4 = code[p4]
            ln4 = instr_len(code, p4)
            raw4 = code[p4:p4 + ln4]
            if op4 in BRANCH2:
                need.add(p4 + struct.unpack(">h", raw4[1:3])[0])
            elif op4 in BRANCH4:
                need.add(p4 + struct.unpack(">i", raw4[1:5])[0])
            elif op4 in (0xAA, 0xAB):
                pad = (4 - (p4 + 1) % 4) % 4
                q = p4 + 1 + pad
                need.add(p4 + struct.unpack(">i", code[q:q + 4])[0])
                if op4 == 0xAA:
                    lo, hi = struct.unpack(">ii", code[q + 4:q + 12])
                    for k in range(hi - lo + 1):
                        need.add(p4 + struct.unpack(">i", code[q + 12 + 4 * k:q + 16 + 4 * k])[0])
                else:
                    npr = struct.unpack(">i", code[q + 4:q + 8])[0]
                    for k in range(npr):
                        need.add(p4 + struct.unpack(">i", code[q + 8 + 8 * k + 4:q + 12 + 8 * k + 4])[0])
            if op4 in (0xA7, 0xC8, 0xAA, 0xAB, 0xBF) or 0xAC <= op4 <= 0xB1:
                # после безусловного перехода идёт новый базовый блок
                if p4 + ln4 < n:
                    need.add(p4 + ln4)
            p4 += ln4
        for (s_, e_, h_, ct_) in exc:
            need.add(h_)
        for t in sorted(need):
            if t < n and t not in fset:
                err(where, f"нет StackMapTable-фрейма для адреса {t} "
                           f"(цель ветки/обработчик) — JVM упадёт с VerifyError")
    for an, body in nested:
        check_attr(where, cp, an, body, cp_count)


def check_attr(where, cp, name_idx, body, cp_count):
    utf8_ok(where, cp, name_idx, "имя атрибута")
    if name_idx == 0 or cp[name_idx][0] != 1:
        return
    name = cp[name_idx][1]
    try:
        _check_attr_inner(where, cp, name, body, cp_count)
    except Exception as e:                       # noqa: BLE001
        err(where, f"атрибут {name}: разбор упал ({e})")


def _check_attr_inner(where, cp, name, body, cp_count):
    if name == "Code":
        if len(body) < 8:
            err(where, "Code: слишком короткий")
            return
        ms, ml, clen = struct.unpack(">HHI", body[:8])
        if 8 + clen + 2 > len(body):
            err(where, "Code: code_length выходит за атрибут")
            return
        code = body[8:8 + clen]
        p = 8 + clen
        etl = u2(body, p)
        p += 2
        if p + 8 * etl + 2 > len(body):
            err(where, "Code: exception_table выходит за атрибут")
            return
        exc = [struct.unpack(">HHHH", body[p + 8 * i:p + 8 * i + 8]) for i in range(etl)]
        p += 8 * etl
        ac = u2(body, p)
        p += 2
        nested = []
        for _ in range(ac):
            if p + 6 > len(body):
                err(where, "Code: вложенные атрибуты обрезаны")
                return
            an = u2(body, p)
            al = u4(body, p + 2)
            p += 6
            if p + al > len(body):
                err(where, "Code: вложенный атрибут выходит за границы")
                return
            nested.append((an, body[p:p + al]))
            p += al
        if p != len(body):
            err(where, f"Code: разбор не дошёл до конца ({p} из {len(body)})")
        check_code(where, cp, code, ms, ml, exc, nested, cp_count)
    elif name == "LineNumberTable":
        cnt = u2(body, 0)
        if len(body) != 2 + 4 * cnt:
            err(where, "LineNumberTable: длина не сходится")
    elif name == "StackMapTable":
        try:
            cnt = u2(body, 0)
            p = 2
            prev = -1
            for _ in range(cnt):
                ft = body[p]; p += 1
                if ft <= 63:                    # same_frame
                    d = ft
                elif ft <= 127:                 # same_locals_1_stack_item
                    d = ft - 64
                    p = skip_vtype(body, p)
                elif ft == 247:                 # same_locals_1_stack_item_extended
                    d = u2(body, p); p += 2
                    p = skip_vtype(body, p)
                elif 248 <= ft <= 251:          # chop / same_frame_extended
                    d = u2(body, p); p += 2
                elif 252 <= ft <= 254:          # append
                    d = u2(body, p); p += 2
                    for _ in range(ft - 251):
                        p = skip_vtype(body, p)
                elif ft == 255:                 # full_frame
                    d = u2(body, p); p += 2
                    nl = u2(body, p); p += 2
                    for _ in range(nl):
                        p = skip_vtype(body, p)
                    ns = u2(body, p); p += 2
                    for _ in range(ns):
                        p = skip_vtype(body, p)
                else:
                    err(where, f"StackMapTable: недопустимый frame_type {ft}")
                    return
                off = d if prev < 0 else prev + d + 1
                prev = off
            if p != len(body):
                err(where, f"StackMapTable: разбор не дошёл до конца ({p} из {len(body)})")
        except Exception as e:                 # noqa: BLE001
            err(where, f"StackMapTable не разбирается: {e}")
    elif name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
        try:
            cnt = u2(body, 0)
            p = 2
            for _ in range(cnt):
                p = check_annotation(where, cp, body, p, cp_count)
            if p != len(body):
                err(where, f"{name}: разбор не дошёл до конца ({p} из {len(body)})")
        except Exception as e:                 # noqa: BLE001
            err(where, f"{name} не разбираются: {e}")
    elif name == "SourceFile":
        if len(body) != 2:
            err(where, "SourceFile: длина != 2")
        else:
            utf8_ok(where, cp, u2(body, 0), "SourceFile.sourcefile_index")
    elif name == "Signature":
        if len(body) != 2:
            err(where, "Signature: длина != 2")
        else:
            utf8_ok(where, cp, u2(body, 0), "Signature.signature_index")
    elif name == "ConstantValue":
        if len(body) != 2:
            err(where, "ConstantValue: длина != 2")
        else:
            idx = u2(body, 0)
            if not (0 < idx < cp_count) or cp[idx] is None or cp[idx][0] not in (3, 4, 5, 6, 8):
                err(where, f"ConstantValue: индекс #{idx} неверен")
    elif name == "Exceptions":
        cnt = u2(body, 0)
        if len(body) != 2 + 2 * cnt:
            err(where, "Exceptions: длина не сходится")
        for i in range(cnt):
            class_ok(where, cp, u2(body, 2 + 2 * i), "Exceptions")
    elif name == "NestHost":
        if len(body) != 2:
            err(where, "NestHost: длина != 2")
        else:
            class_ok(where, cp, u2(body, 0), "NestHost")
    elif name in ("NestMembers", "PermittedSubclasses"):
        cnt = u2(body, 0)
        if len(body) != 2 + 2 * cnt:
            err(where, f"{name}: длина не сходится")
        for i in range(cnt):
            class_ok(where, cp, u2(body, 2 + 2 * i), name)
    elif name == "InnerClasses":
        cnt = u2(body, 0)
        if len(body) != 2 + 8 * cnt:
            err(where, "InnerClasses: длина не сходится")
        for i in range(cnt):
            q = 2 + 8 * i
            inner, outer, nm = u2(body, q), u2(body, q + 2), u2(body, q + 4)
            if inner:
                class_ok(where, cp, inner, "InnerClasses.inner")
            if outer:
                class_ok(where, cp, outer, "InnerClasses.outer")
            if nm:
                utf8_ok(where, cp, nm, "InnerClasses.inner_name")
    elif name == "EnclosingMethod":
        class_ok(where, cp, u2(body, 0), "EnclosingMethod.class")
        if u2(body, 2):
            idx = u2(body, 2)
            if not (0 < idx < cp_count) or cp[idx] is None or cp[idx][0] != 12:
                err(where, f"EnclosingMethod.method #{idx} не NameAndType")
    elif name == "MethodParameters":
        cnt = body[0]
        if len(body) != 1 + 4 * cnt:
            err(where, "MethodParameters: длина не сходится")
        for i in range(cnt):
            nm = u2(body, 1 + 4 * i)
            if nm:
                utf8_ok(where, cp, nm, "MethodParameters.name")
    elif name == "LocalVariableTable":
        cnt = u2(body, 0)
        if len(body) != 2 + 10 * cnt:
            err(where, "LocalVariableTable: длина не сходится")
        for i in range(cnt):
            q = 2 + 10 * i
            utf8_ok(where, cp, u2(body, q + 4), "LocalVariableTable.name")
            utf8_ok(where, cp, u2(body, q + 6), "LocalVariableTable.descriptor")
    elif name == "AnnotationDefault":
        p = check_element_value(where, cp, body, 0, cp_count)
        if p != len(body):
            err(where, "AnnotationDefault: разбор не дошёл до конца")
    elif name in ("RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations"):
        cnt = body[0]
        p = 1
        for _ in range(cnt):
            n = u2(body, p); p += 2
            for _ in range(n):
                p = check_annotation(where, cp, body, p, cp_count)
        if p != len(body):
            err(where, f"{name}: разбор не дошёл до конца")
    elif name in ("Deprecated", "Synthetic"):
        if body:
            err(where, f"{name}: должен быть пустым")
    elif name == "BootstrapMethods":
        cnt = u2(body, 0)
        q = 2
        for _ in range(cnt):
            ref = u2(body, q); ac = u2(body, q + 2); q += 4
            if not (0 < ref < cp_count) or cp[ref] is None or cp[ref][0] != 15:
                err(where, f"BootstrapMethods: bsm_ref #{ref} не MethodHandle")
            for _ in range(ac):
                idx = u2(body, q); q += 2
                if not (0 < idx < cp_count) or cp[idx] is None:
                    err(where, f"BootstrapMethods: аргумент #{idx} вне пула")
        if q != len(body):
            err(where, "BootstrapMethods: разбор не дошёл до конца")


def skip_vtype(b, p):
    """verification_type_info: 1 байт для 0..6, 3 байта для Object(7)/Uninitialized(8)."""
    tag = b[p]
    if tag <= 6:
        return p + 1
    if tag in (7, 8):
        return p + 3
    raise ValueError(f"недопустимый verification_type_info tag {tag}")


def skip_vtype(b, p):
    """verification_type_info: 1 байт для 0..6, 3 байта для Object(7)/Uninitialized(8)."""
    tag = b[p]
    if tag <= 6:
        return p + 1
    if tag in (7, 8):
        return p + 3
    raise ValueError(f"недопустимый verification_type_info tag {tag}")


def check_annotation(where, cp, b, p, cp_count):
    """Разбор annotation с проверкой КАЖДОГО индекса пула (тут прятался баг 233)."""
    t = u2(b, p); p += 2
    utf8_ok(where, cp, t, "annotation.type_index")
    n = u2(b, p); p += 2
    for _ in range(n):
        nm = u2(b, p); p += 2
        utf8_ok(where, cp, nm, "annotation.element_name_index")
        p = check_element_value(where, cp, b, p, cp_count)
    return p


def check_element_value(where, cp, b, p, cp_count):
    tag = chr(b[p]); p += 1
    if tag in "BCDFIJSZ":
        idx = u2(b, p); p += 2
        if not (0 < idx < cp_count) or cp[idx] is None or cp[idx][0] not in (3, 4, 5, 6, 8):
            err(where, f"annotation: const_value_index #{idx} неверен")
    elif tag == "s":
        idx = u2(b, p); p += 2
        utf8_ok(where, cp, idx, "annotation.string")
    elif tag == "e":
        a, b2 = u2(b, p), u2(b, p + 2); p += 4
        utf8_ok(where, cp, a, "annotation.enum type")
        utf8_ok(where, cp, b2, "annotation.enum name")
    elif tag == "c":
        idx = u2(b, p); p += 2
        utf8_ok(where, cp, idx, "annotation.class descriptor")
    elif tag == "@":
        p = check_annotation(where, cp, b, p, cp_count)
    elif tag == "[":
        n = u2(b, p); p += 2
        for _ in range(n):
            p = check_element_value(where, cp, b, p, cp_count)
    else:
        raise ValueError(f"неизвестный тег элемента аннотации {tag!r}")
    return p


# ---------------------------------------------------------------- class
def check_class(where, data):
    try:
        k = parse_class(data)
    except Exception as e:                       # noqa: BLE001
        err(where, f"класс не разбирается: {e}")
        return
    if k["trailing"]:
        err(where, f"хвост {k['trailing']} байт после class-атрибутов")
    if not (49 <= k["major"] <= 65):
        err(where, f"major={k['major']} вне диапазона 49..65")
    cp = k["cp"]
    n = len(cp)
    check_pool(where, cp)
    class_ok(where, cp, k["this"], "this_class")
    if k["super"]:
        class_ok(where, cp, k["super"], "super_class")
    for i in k["interfaces"]:
        class_ok(where, cp, i, "interfaces")
    for acc, ni, di, attrs in k["fields"] + k["methods"]:
        utf8_ok(where, cp, ni, "имя члена")
        utf8_ok(where, cp, di, "дескриптор члена")
        for an, body in attrs:
            check_attr(where, cp, an, body, n)
    for an, body in k["cattrs"]:
        check_attr(where, cp, an, body, n)
        if an and cp[an][0] == 1 and cp[an][1] == "RuntimeInvisibleAnnotations":
            if body.find(b"\x5b\x00\x01") < 0 and b"Lorg/spongepowered/asm/mixin/Mixin;" in b"".join(
                    cp[i][1].encode() for i in range(1, n) if cp[i] and cp[i][0] == 1):
                WARN.append(f"{where}: @Mixin без массива в аннотации — проверьте")
    return k


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--base")
    a = ap.parse_args()
    z = zipfile.ZipFile(a.jar)
    bad = z.testzip()
    names = z.namelist()
    if bad:
        err("zip", f"битая запись {bad}")
    dups = {n for n in names if names.count(n) > 1}
    if dups:
        err("zip", f"дубли записей: {dups}")

    import tomllib
    toml_txt = z.read("META-INF/neoforge.mods.toml").decode("utf-8")
    try:
        t = tomllib.loads(toml_txt)
        ver = t["mods"][0]["version"]
        print(f"toml ok, version={ver}")
    except Exception as e:                       # noqa: BLE001
        err("toml", str(e))
        ver = None

    for jn in [n for n in names if n.endswith(".mixins.json") or n.endswith("mixins.json")]:
        try:
            cfg = json.loads(z.read(jn))
        except Exception as e:                   # noqa: BLE001
            err(jn, f"json не парсится: {e}")
            continue
        pkg = cfg.get("package", "").replace(".", "/")
        for sect in ("mixins", "client", "server"):
            for m in cfg.get(sect, []):
                path = f"{pkg}/{m}.class"
                if path not in names:
                    err(jn, f"миксин {m} отсутствует ({path})")

    classes = [n for n in names if n.endswith(".class")]
    for n in classes:
        check_class(n, z.read(n))

    print(f"классов проверено: {len(classes)}; записей: {len(names)}")
    for w in WARN:
        print("WARN:", w)
    if a.base:
        zb = zipfile.ZipFile(a.base)
        nb = zb.namelist()
        only_new = sorted(set(names) - set(nb))
        only_old = sorted(set(nb) - set(names))
        changed = [n for n in names if n in nb and z.read(n) != zb.read(n)]
        print("дифф: новых", only_new, "| удалено", only_old,
              "| изменено", changed, "| идентичных", len([n for n in names if n in nb]) - len(changed))
    if ERRORS:
        print(f"\nОШИБКИ ({len(ERRORS)}):")
        for e in ERRORS[:60]:
            print(" ", e)
        return 1
    print("\nВСЕ ПРОВЕРКИ ПРОЙДЕНЫ (структура, пул, операнды, атрибуты, миксины)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
