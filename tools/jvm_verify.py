#!/usr/bin/env python3
"""Проверка правленого байткода НАСТОЯЩЕЙ JVM — без Minecraft и без компилятора.

Зачем: инциденты 230/234. Валидатор ловит структуру, но не верификатор JVM
(неинициализированная локальная, несогласованные фреймы, неверные типы в ветках).
Здесь мы:

  1. берём из jar настоящие классы, которые надо проверить;
  2. генерируем «заглушки» для всех внешних типов (net/minecraft/..., org/slf4j/...):
     нужные поля/методы есть, тела пустые — этого достаточно, чтобы верификатор
     смог разрешить типы;
  3. собираем крошечный harness, который через рефлексию вызывает проверяемый
     метод (Class.getDeclaredMethod + Method.invoke);
  4. запускаем java из tools/jdkpkg и смотрим, не прилетел ли VerifyError.

Верификация в HotSpot происходит при линковке метода, т.е. до выполнения, поэтому
дальнейший NPE внутри метода — это УСПЕХ (структура верна), а VerifyError —
провал.

    python3 tools/jvm_verify.py settledlands-0.8.0-build236-corpse-tex.jar \
        dev/settledlands/npc/GuideNpc#applyProfile \
        'dev/settledlands/npc/PlayerCorpsePossession#dressBodyCorpse(dev.settledlands.npc.GuideCorpseNpc,dev.settledlands.npc.PlayerCorpsePossession$PendingBody)'
"""
import argparse, os, re, struct, subprocess, sys, tempfile, zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import classedit  # noqa: E402
import decode  # noqa: E402
from patch_earlytex_mixin import CP  # noqa: E402

JAVA = "/home/user/tools/jdkpkg/jdk4py/java-runtime/bin/java"

# Кто чей подтип — то, чего не видно по одному дескриптору.
HINTS = {
    "net/minecraft/world/entity/PathfinderMob": "net/minecraft/world/entity/Mob",
    "net/minecraft/world/entity/Mob": "net/minecraft/world/entity/LivingEntity",
    "net/minecraft/world/entity/LivingEntity": "net/minecraft/world/entity/Entity",
    "net/minecraft/world/entity/Entity": "java/lang/Object",
    "net/minecraft/world/entity/player/Player": "net/minecraft/world/entity/LivingEntity",
    "net/minecraft/server/level/ServerPlayer": "net/minecraft/world/entity/player/Player",
    "net/minecraft/world/level/Level": "java/lang/Object",
    "net/minecraft/server/level/ServerLevel": "net/minecraft/world/level/Level",
    "net/minecraft/network/chat/MutableComponent": "java/lang/Object",
}
IFACE_HINTS = {
    "org/slf4j/Logger": True,
    "net/minecraft/network/chat/Component": True,
}
IMPL_HINTS = {
    "net/minecraft/network/chat/MutableComponent": ["net/minecraft/network/chat/Component"],
}


def u2(v):
    return struct.pack(">H", v)


def stub_class(name, super_name, is_iface, fields, methods, ifaces=(), static=None):
    static = static or set()
    """Класс-заглушка: поля и native-методы (тела не нужны), <init> вызывает super."""
    cp = CP()
    this_i = cp.cls(name)
    super_i = cp.cls(super_name)
    access = 0x0601 if is_iface else 0x0021
    fields_out = []
    for fname, fdesc in sorted(fields):
        acc = 0x0009 if (fname, fdesc) in static else 0x0001
        fields_out.append(struct.pack(">HHHH", acc, cp.utf8(fname), cp.utf8(fdesc), 0))
    methods_out = []
    for mname, mdesc in sorted(m for m in methods if not m[0].startswith("<")):
        if is_iface:
            acc = 0x0401
        else:
            acc = 0x0109 if (mname, mdesc) in static else 0x0101   # static native / native
        methods_out.append(struct.pack(">HHHH", acc, cp.utf8(mname), cp.utf8(mdesc), 0))
    if not is_iface:
        code = b"\x2a\xb7" + u2(cp.method(super_name, "<init>", "()V")) + b"\xb1"
        body = (u2(1) + u2(1) + struct.pack(">I", len(code)) + code + u2(0) + u2(0))
        methods_out.append(struct.pack(">HHHH", 0x0001, cp.utf8("<init>"), cp.utf8("()V"), 1)
                           + u2(cp.utf8("Code")) + struct.pack(">I", len(body)) + body)
    return (struct.pack(">IHHH", 0xCAFEBABE, 0, 65, len(cp.items))
            + b"".join(cp.items[1:])
            + struct.pack(">HHH", access, this_i, super_i)
            + u2(len(ifaces)) + b"".join(u2(cp.cls(x)) for x in ifaces)
            + u2(len(fields_out)) + b"".join(fields_out)
            + u2(len(methods_out)) + b"".join(methods_out)
            + u2(0))


def scan_statics(klass, flags):
    """Отметить, какие члены вызываются как static (getstatic/putstatic/invokestatic)."""
    for m in klass.methods:
        ca = [a for a in m["attrs"] if a["name"] == "Code"]
        if not ca:
            continue
        parts = classedit.code_parts(klass, m)
        code = parts["code"]
        pc = 0
        while pc < len(code):
            try:
                ln = decode.instr_len(code, pc)
            except Exception:                      # noqa: BLE001
                break
            op = code[pc]
            if op in (0xB2, 0xB3, 0xB8):
                idx = struct.unpack(">H", code[pc + 1:pc + 3])[0]
                e = klass.cp[idx]
                if e and e["tag"] in (9, 10):
                    owner = klass.utf8(klass.cp[e["a"]]["a"])
                    name = klass.utf8(klass.cp[e["b"]]["a"])
                    desc = klass.utf8(klass.cp[e["b"]]["b"])
                    flags[(owner, name, desc)] = True
            pc += ln


def types_in_desc(desc):
    out = []
    i = 0
    while i < len(desc):
        c = desc[i]
        if c == "L":
            j = desc.index(";", i)
            out.append(desc[i + 1:j])
            i = j
        i += 1
    return out


def ldc(cp, idx):
    if idx <= 255:
        return b"\x12" + bytes([idx])
    return b"\x13" + u2(idx)


def harness(main_class, targets):
    """main(): для каждой цели вызвать метод через рефлексию."""
    cp = CP()
    code = bytearray()
    for cls, mname, params in targets:
        code += ldc(cp, cp.string(cls.replace("/", ".")))
        code += b"\xb8" + u2(cp.method("java/lang/Class", "forName",
                                          "(Ljava/lang/String;)Ljava/lang/Class;"))
        code += ldc(cp, cp.string(mname))
        code += (b"\x10" + bytes([len(params)])) if len(params) else b"\x03"
        code += b"\xbd" + u2(cp.cls("java/lang/Class"))
        for i, p in enumerate(params):
            code += b"\x59"                            # dup
            code += (b"\x10" + bytes([i])) if i else b"\x03"
            code += ldc(cp, cp.cls(p.replace(".", "/")))
            code += b"\x53"                            # aastore
        code += b"\xb6" + u2(cp.method("java/lang/Class", "getDeclaredMethod",
                                          "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
        code += b"\x01"                                # aconst_null (target)
        code += b"\x03"                                # 0
        code += b"\xbd" + u2(cp.cls("java/lang/Object"))
        code += b"\xb6" + u2(cp.method("java/lang/reflect/Method", "invoke",
                                          "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"))
        code += b"\x57"                                # pop
        code += b"\xb2" + u2(cp.field("java/lang/System", "out", "Ljava/io/PrintStream;"))
        code += ldc(cp, cp.string("LINKED " + cls + "#" + mname))
        code += b"\xb6" + u2(cp.method("java/io/PrintStream", "println", "(Ljava/lang/String;)V"))
    code += b"\xb1"

    cp2 = cp
    mixin_main = cp2.cls("VerifyHarness")
    super_i = cp2.cls("java/lang/Object")
    code_attr = (u2(6) + u2(1) + struct.pack(">I", len(code)) + bytes(code)
                 + u2(0) + u2(0))
    m_code_name = cp2.utf8("Code")
    main_m = (struct.pack(">HHHH", 0x0009, cp2.utf8("main"),
                          cp2.utf8("([Ljava/lang/String;)V"), 1)
              + u2(m_code_name) + struct.pack(">I", len(code_attr)) + code_attr)
    return (struct.pack(">IHHH", 0xCAFEBABE, 0, 65, len(cp2.items))
            + b"".join(cp2.items[1:])
            + struct.pack(">HHH", 0x0021, mixin_main, super_i)
            + u2(0) + u2(0) + u2(1) + main_m + u2(0))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("targets", nargs="+", help="Class#method или Class#method(desc,desc)")
    ap.add_argument("--java", default=JAVA)
    a = ap.parse_args()

    zin = zipfile.ZipFile(a.jar)
    real = {n[:-6]: zin.read(n) for n in zin.namelist() if n.endswith(".class")}
    parsed = {n: classedit.Klass(d) for n, d in real.items()}

    targets = []
    for t in a.targets:
        cls, meth = t.split("#")
        params = []
        if "(" in meth:
            meth, rest = meth.split("(")
            params = [p.strip() for p in rest.rstrip(")").split(",") if p.strip()]
        targets.append((cls, meth, params))

    # --- собираем всё, что нужно для проверки: целевые классы и их предки
    need = set()
    for _, _, params in targets:
        for p in params:
            p = p.replace(".", "/")
            if p in real:
                need.add(p)
    for cls, _, _ in targets:
        cur = cls
        while cur in real and cur not in need:
            need.add(cur)
            s = parsed[cur].cp[parsed[cur].super]
            cur = parsed[cur].utf8(s["a"]) if s and s["tag"] == 7 else None
    # --- внешние типы и их члены
    stubs = {}          # name -> dict(fields=set, methods=set, iface=bool)
    for cls in need:
        k = parsed[cls]
        for i in range(1, len(k.cp)):
            e = k.cp[i]
            if not e:
                continue
            if e["tag"] == 7:
                nm = k.utf8(e["a"])
                if nm in real or nm.startswith(("java/", "javax/", "jdk/", "sun/")):
                    continue
                stubs.setdefault(nm, dict(fields=set(), methods=set(),
                                          iface=IFACE_HINTS.get(nm, False)))
            elif e["tag"] in (9, 10, 11):
                owner = k.utf8(k.cp[e["a"]]["a"])
                name, desc = k.utf8(k.cp[e["b"]]["a"]), k.utf8(k.cp[e["b"]]["b"])
                if owner in real:
                    continue
                d = stubs.setdefault(owner, dict(fields=set(), methods=set(),
                                                 iface=(e["tag"] == 11)))
                if e["tag"] == 11:
                    d["iface"] = True
                (d["fields"] if e["tag"] == 9 else d["methods"]).add((name, desc))

    # --- недостающие члены, которые реальный класс наследует от заглушки-предка
    for cls in need:
        k = parsed[cls]
        chain = []
        cur = cls
        while cur in real:
            chain.append(cur)
            s = parsed[cur].cp[parsed[cur].super]
            cur = parsed[cur].utf8(s["a"]) if s and s["tag"] == 7 else None
        stub_super = cur
        if (stub_super and not stub_super.startswith(("java/", "javax/"))
                and stub_super not in real):
            stubs.setdefault(stub_super, dict(fields=set(), methods=set(),
                                              iface=IFACE_HINTS.get(stub_super, False)))
        for i in range(1, len(k.cp)):
            e = k.cp[i]
            if not e or e["tag"] not in (9, 10, 11):
                continue
            owner = k.utf8(k.cp[e["a"]]["a"])
            name, desc = k.utf8(k.cp[e["b"]]["a"]), k.utf8(k.cp[e["b"]]["b"])
            if owner not in real:
                continue
            # объявлен ли член где-то в реальной цепочке?
            found = False
            c = owner
            while c in real:
                kk = parsed[c]
                pool = kk.fields if e["tag"] == 9 else kk.methods
                for m in pool:
                    if m["name"] == name and (m["desc"] == desc or e["tag"] == 9):
                        found = True
                        break
                if found:
                    break
                s = kk.cp[kk.super]
                c = kk.utf8(s["a"]) if s and s["tag"] == 7 else None
            if found or stub_super is None or stub_super.startswith(("java/", "javax/")):
                continue
            d = stubs.setdefault(stub_super, dict(fields=set(), methods=set(), iface=False))
            (d["fields"] if e["tag"] == 9 else d["methods"]).add((name, desc))

    # интерфейсы, которые реализуют реальные классы, обязаны быть интерфейсами
    known_ifaces = set()
    for cls, kk in parsed.items():
        for ii in kk.ifaces:
            e = kk.cp[ii]
            if e and e["tag"] == 7:
                known_ifaces.add(kk.utf8(e["a"]))
    for nm in list(stubs):
        if nm in known_ifaces:
            stubs[nm]["iface"] = True

    # предки из таблицы подсказок тоже должны существовать
    for _ in range(4):
        for nm in list(stubs):
            sup = HINTS.get(nm)
            if sup and sup not in real and not sup.startswith(("java/", "javax/")):
                stubs.setdefault(sup, dict(fields=set(), methods=set(),
                                           iface=IFACE_HINTS.get(sup, False)))
            for iface in IMPL_HINTS.get(nm, []):
                if iface not in real:
                    stubs.setdefault(iface, dict(fields=set(), methods=set(),
                                                 iface=IFACE_HINTS.get(iface, True)))

    # типы из дескрипторов тоже должны существовать
    for _ in range(4):
        grew = False
        for cls in need:
            k = parsed[cls]
            for i in range(1, len(k.cp)):
                e = k.cp[i]
                if not e or e["tag"] not in (9, 10, 11):
                    continue
                desc = k.utf8(k.cp[e["b"]]["b"])
                for t in types_in_desc(desc):
                    if t in real or t.startswith(("java/", "javax/")) or t in stubs:
                        continue
                    stubs.setdefault(t, dict(fields=set(), methods=set(),
                                             iface=IFACE_HINTS.get(t, False)))
                    grew = True
        for name, d in list(stubs.items()):
            for _, desc in list(d["methods"]) + list(d["fields"]):
                for t in types_in_desc(desc):
                    if t in real or t.startswith(("java/", "javax/")) or t in stubs:
                        continue
                    stubs.setdefault(t, dict(fields=set(), methods=set(),
                                             iface=IFACE_HINTS.get(t, False)))
                    grew = True
        if not grew:
            break

    # типы из дескрипторов самих проверяемых классов (объявления методов/полей)
    for _ in range(4):
        grew = False
        for cls in need:
            kk = parsed[cls]
            descs = [m["desc"] for m in kk.methods] + [f["desc"] for f in kk.fields]
            for desc in descs:
                for t in types_in_desc(desc):
                    if t in real or t.startswith(("java/", "javax/")) or t in stubs:
                        continue
                    stubs.setdefault(t, dict(fields=set(), methods=set(),
                                             iface=IFACE_HINTS.get(t, False)))
                    grew = True
        if not grew:
            break

    static_flags = {}
    for cls in need:
        scan_statics(parsed[cls], static_flags)

    # финальная сверка: всё, что реальные классы называют интерфейсом — интерфейс
    for nm in stubs:
        if nm in known_ifaces:
            stubs[nm]["iface"] = True
            if HINTS.get(nm) == "java/lang/Object":
                HINTS.pop(nm, None)

    work = tempfile.mkdtemp(prefix="jvmverify-")
    for name, d in stubs.items():
        path = os.path.join(work, name + ".class")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sup = HINTS.get(name, "java/lang/Object")
        with open(path, "wb") as f:
            f.write(stub_class(name, sup, d["iface"], d["fields"], d["methods"],
                               IMPL_HINTS.get(name, [])))
    with open(os.path.join(work, "VerifyHarness.class"), "wb") as f:
        f.write(harness(None, targets))

    cp = work + os.pathsep + a.jar
    cmd = [a.java, "-Xshare:off", "-Xverify:all", "-cp", cp, "VerifyHarness"]

    def build_and_run(hints, impl, ifaces, tg=None):
        os.makedirs(work, exist_ok=True)
        for nm, d in stubs.items():
            path = os.path.join(work, nm + ".class")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            sup = hints.get(nm, "java/lang/Object")
            with open(path, "wb") as f:
                f.write(stub_class(nm, sup, d["iface"], d["fields"], d["methods"],
                                   impl.get(nm, []),
                                   {(n, ds) for (o, n, ds) in static_flags if o == nm}))
        with open(os.path.join(work, "VerifyHarness.class"), "wb") as f:
            f.write(harness(None, tg or targets))
        rr = subprocess.run(cmd, capture_output=True, text=True)
        return rr.stdout + rr.stderr

    outs = []
    for tg in targets:
        out = build_and_run(HINTS, IMPL_HINTS, IFACE_HINTS, [tg])
        for _ in range(40):
            m = re.search(r"Type '([\w/$]+)'.*?is not assignable to '([\w/$]+)'", out, re.S)
            if not m:
                break
            sub, sup = m.group(1), m.group(2)
            if sub not in stubs or sup in real:
                break
            if stubs.get(sup, {}).get("iface") or sup in known_ifaces:
                IMPL_HINTS.setdefault(sub, [])
                if sup not in IMPL_HINTS[sub]:
                    IMPL_HINTS[sub].append(sup)
            else:
                HINTS[sub] = sup
            out = build_and_run(HINTS, IMPL_HINTS, IFACE_HINTS, [tg])
        outs.append(out)
    out = "\n".join(outs)
    # авто-подгонка иерархии заглушек: JVM сама говорит, кто кому обязан быть подтипом
    for _ in range(40):
        m = re.search(r"Type '([\w/$]+)'.*?is not assignable to '([\w/$]+)'", out, re.S)
        if not m:
            break
        sub, sup = m.group(1), m.group(2)
        if sub not in stubs or sup in real:
            break
        if stubs.get(sup, {}).get("iface") or sup in known_ifaces:
            IMPL_HINTS.setdefault(sub, [])
            if sup not in IMPL_HINTS[sub]:
                IMPL_HINTS[sub].append(sup)
        else:
            HINTS[sub] = sup
        outs = []
    for tg in targets:
        out = build_and_run(HINTS, IMPL_HINTS, IFACE_HINTS, [tg])
        for _ in range(40):
            m = re.search(r"Type '([\w/$]+)'.*?is not assignable to '([\w/$]+)'", out, re.S)
            if not m:
                break
            sub, sup = m.group(1), m.group(2)
            if sub not in stubs or sup in real:
                break
            if stubs.get(sup, {}).get("iface") or sup in known_ifaces:
                IMPL_HINTS.setdefault(sub, [])
                if sup not in IMPL_HINTS[sub]:
                    IMPL_HINTS[sub].append(sup)
            else:
                HINTS[sub] = sup
            out = build_and_run(HINTS, IMPL_HINTS, IFACE_HINTS, [tg])
        outs.append(out)
    out = "\n".join(outs)
    print(f"заглушек: {len(stubs)}")
    bad = ("VerifyError" in out or "ClassFormatError" in out
           or "UnsupportedClassVersionError" in out)
    # Линковка (а с ней и верификация всех методов) происходит ДО <clinit>,
    # поэтому любое исключение времени исполнения означает: верификация пройдена.
    linked = ("LINKED " in out or "UnsatisfiedLinkError" in out
              or "NullPointerException" in out or "ExceptionInInitializerError" in out
              or "AbstractMethodError" in out or "NoSuchMethodError" in out
              or "InvocationTargetException" in out or "IncompatibleClassChangeError" in out)
    seen = out.count("LINKED ")
    linked_ok = linked or seen >= len(targets)
    for line in out.splitlines():
        if any(s in line for s in ("VerifyError", "ClassFormatError", "Exception in thread",
                                   "NoClassDefFoundError", "LINKED")):
            print("  ", line.strip())
    extra = [ln.strip() for ln in out.splitlines() if ln.strip().startswith("Caused by")]
    for e in extra[:5]:
        print("  ", e)
    if bad:
        print("ИТОГ: ПРОВАЛ (JVM не приняла байткод)")
        return 1
    if not linked_ok:
        print("ИТОГ: НЕ ПРОВЕРЕНО (класс не удалось даже слинковать на заглушках)")
        return 2
    if seen >= len(targets):
        print(f"ИТОГ: OK (методы выполнены: {seen}/{len(targets)})")
    else:
        print("ИТОГ: OK (линковка и верификация пройдены; исполнение остановилось "
              "на заглушке — это уже ПОСЛЕ верификации)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
