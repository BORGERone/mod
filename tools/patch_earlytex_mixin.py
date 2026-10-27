#!/usr/bin/env python3
"""Сборка build 231 «earlytex»: миксин, выставляющий DATA_TEXTURE в начале applyProfile().

ПОЧЕМУ ТАК:
`GuideNpc.applyProfile()` пишет DATA_TEXTURE (имя bbmodel) ПОСЛЕДНЕЙ третью метода
(offset 186..211 из 236), а вызывается он из `setProfile()` и из
`PlayerCorpsePossession.dressBodyCorpse()` внутри `catch (Throwable ignored)`.
Любое исключение выше = DATA_TEXTURE остаётся "" -> GuideRenderer.renderCorpse
получает null из BbModelManager.getModel("") и рисует встроенную заглушку
(коричневый guide_npc.png). Это ровно то, что видит юзер.

Правка build 230 (вставка байт в Code) сделала класс невалидным для ASM.
Здесь вместо этого ДОБАВЛЯЕТСЯ НОВЫЙ КЛАСС-миксин и строка в
settledlands.mixins.json; байткод существующих классов не трогается вообще.

Тело обработчика (без ветвлений, max_stack=3, max_locals=2):

    String t = Objects.toString(this.profile().texture, "");
    this.entityData.set(GuideNpc.DATA_TEXTURE, t);
    System.out.print("[SettledLands] corpse texture pre-set: ");
    System.out.println(t);

Ссылки на приватные поля GuideNpc после merge находятся ВНУТРИ GuideNpc,
поэтому доступ легален; Mixin всё равно пересчитывает фреймы/стек (COMPUTE_FRAMES),
так что StackMapTable нашему методу не нужен — ветвлений нет.

    python3 tools/patch_earlytex_mixin.py \
        --base settledlands-0.8.0-build229-sablesweep.jar \
        --out settledlands-0.8.0-build231-earlytex.jar
"""
import argparse, hashlib, io, json, struct, sys, zipfile, tomllib

MIXIN_CLASS = "dev/settledlands/mixin/EarlyCorpseTextureMixin"
MIXIN_SIMPLE = "EarlyCorpseTextureMixin"
NEW_VERSION = "0.8.0-build231-earlytex"
CHANGELOG = (
    "Build 231: corpse model fix (real one). GuideRenderer takes the model name from synched entity "
    "data (DATA_TEXTURE), but GuideNpc.applyProfile() wrote it as its second-to-last statement and both "
    "callers (setProfile and PlayerCorpsePossession.dressBodyCorpse) swallow exceptions, so any failure "
    "above left it empty and the corpse silently fell back to the built-in placeholder model. A new mixin "
    "writes DATA_TEXTURE at the very start of applyProfile() and logs the value, so the corpse shows the "
    "actual bbmodel even if the rest of the profile work throws. No existing class was modified."
)
PATCHINFO_ADD = (
    "сборка 231: починка модели трупа (настоящая). GuideRenderer берёт имя модели из "
    "entityData(DATA_TEXTURE), а GuideNpc.applyProfile() выставлял его предпоследним оператором, причём "
    "оба вызывающих (setProfile и PlayerCorpsePossession.dressBodyCorpse) глотают исключения — любая "
    "ошибка выше оставляла поле пустым, и труп молча рисовался встроенной заглушкой. Новый миксин "
    "EarlyCorpseTextureMixin ставит DATA_TEXTURE в самое начало applyProfile() и пишет значение в лог. "
    "Ни один существующий класс не изменён."
)


# ---------------------------------------------------------------- class writer
class CP:
    def __init__(self):
        self.items = [None]           # индекс 1..n
        self.cache = {}

    def _add(self, key, raw):
        if key in self.cache:
            return self.cache[key]
        self.items.append(raw)
        idx = len(self.items) - 1
        if idx > 65535:
            raise ValueError("пул переполнен")
        self.cache[key] = idx
        return idx

    def utf8(self, s):
        b = s.encode("utf-8")
        return self._add(("u", s), b"\x01" + struct.pack(">H", len(b)) + b)

    def cls(self, name):
        return self._add(("c", name), b"\x07" + struct.pack(">H", self.utf8(name)))

    def string(self, s):
        return self._add(("s", s), b"\x08" + struct.pack(">H", self.utf8(s)))

    def integer(self, v):
        return self._add(("i", v), b"\x03" + struct.pack(">i", v))

    def nat(self, name, desc):
        return self._add(("n", name, desc),
                         b"\x0c" + struct.pack(">HH", self.utf8(name), self.utf8(desc)))

    def field(self, owner, name, desc):
        return self._add(("f", owner, name, desc),
                         b"\x09" + struct.pack(">HH", self.cls(owner), self.nat(name, desc)))

    def method(self, owner, name, desc):
        return self._add(("m", owner, name, desc),
                         b"\x0a" + struct.pack(">HH", self.cls(owner), self.nat(name, desc)))

    def dump(self):
        out = [struct.pack(">H", len(self.items))]
        for raw in self.items[1:]:
            out.append(raw)
        return b"".join(out)


def u2(v):
    return struct.pack(">H", v)


def u4(v):
    return struct.pack(">I", v)


def attr(name_idx, body):
    return u2(name_idx) + u4(len(body)) + body


def build_mixin_class():
    cp = CP()
    a_code = cp.utf8("Code")
    a_src = cp.utf8("SourceFile")
    a_rva = cp.utf8("RuntimeVisibleAnnotations")
    a_ria = cp.utf8("RuntimeInvisibleAnnotations")

    this_name = MIXIN_CLASS
    this_idx = cp.cls(this_name)
    super_idx = cp.cls("java/lang/Object")

    # <init>
    init_code = bytes([0x2a, 0xb7]) + u2(cp.method("java/lang/Object", "<init>", "()V")) + bytes([0xb1])
    init_code_attr = u2(1) + u2(1) + u4(len(init_code)) + init_code + u2(0) + u2(0)
    init_method = (u2(0x0001) + u2(cp.utf8("<init>")) + u2(cp.utf8("()V"))
                   + u2(1) + attr(a_code, init_code_attr))

    # обработчик: settledlands$earlyTexture(CallbackInfo)
    desc = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"
    m_profile = cp.method("dev/settledlands/npc/GuideNpc", "profile", "()Ldev/settledlands/npc/NpcProfile;")
    f_texture = cp.field("dev/settledlands/npc/NpcProfile", "texture", "Ljava/lang/String;")
    s_empty = cp.string("")
    m_tostr = cp.method("java/util/Objects", "toString",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;")
    f_entitydata = cp.field("dev/settledlands/npc/GuideNpc", "entityData",
                            "Lnet/minecraft/network/syncher/SynchedEntityData;")
    f_datatex = cp.field("dev/settledlands/npc/GuideNpc", "DATA_TEXTURE",
                         "Lnet/minecraft/network/syncher/EntityDataAccessor;")
    m_set = cp.method("net/minecraft/network/syncher/SynchedEntityData", "set",
                      "(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V")
    f_sysout = cp.field("java/lang/System", "out", "Ljava/io/PrintStream;")
    s_log = cp.string("[SettledLands] corpse texture pre-set: ")
    m_print = cp.method("java/io/PrintStream", "print", "(Ljava/lang/String;)V")
    m_println = cp.method("java/io/PrintStream", "println", "(Ljava/lang/String;)V")

    code = b"".join([
        bytes([0x2a]),              # aload_0
        bytes([0xb6]) + u2(m_profile),
        bytes([0xb4]) + u2(f_texture),
        bytes([0x12]) + bytes([s_empty]),       # ldc ""   (индекс обязан быть <= 255)
        bytes([0xb8]) + u2(m_tostr),
        bytes([0x4c]),              # astore_1
        bytes([0x2a]),              # aload_0
        bytes([0xb4]) + u2(f_entitydata),
        bytes([0xb2]) + u2(f_datatex),
        bytes([0x2b]),              # aload_1
        bytes([0xb6]) + u2(m_set),
        bytes([0xb2]) + u2(f_sysout),
        bytes([0x12]) + bytes([s_log]),
        bytes([0xb6]) + u2(m_print),
        bytes([0xb2]) + u2(f_sysout),
        bytes([0x2b]),
        bytes([0xb6]) + u2(m_println),
        bytes([0xb1]),              # return
    ])
    for idx in (s_empty, s_log):
        assert idx <= 255, "ldc-индекс больше 255"

    code_attr = u2(3) + u2(2) + u4(len(code)) + code + u2(0) + u2(0)

    # @Inject(method = "applyProfile()V", at = @At("HEAD"), require = 0)
    at_ann = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/injection/At;")) + u2(1)
              + u2(cp.utf8("value")) + b"s" + u2(cp.utf8("HEAD")))
    inject_ann = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/injection/Inject;")) + u2(3)
                  + u2(cp.utf8("method")) + b"s" + u2(cp.utf8("applyProfile()V"))
                  + u2(cp.utf8("at")) + b"@" + at_ann
                  + u2(cp.utf8("require")) + b"I" + u2(cp.integer(0)))
    inject_rva = u2(1) + inject_ann

    handler = (u2(0x0002) + u2(cp.utf8("settledlands$earlyTexture")) + u2(cp.utf8(desc))
               + u2(2) + attr(a_code, code_attr) + attr(a_rva, inject_rva))

    # @Mixin(GuideNpc.class)  (class -> element tag 'c' + дескриптор класса)
    mixin_ann = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/Mixin;")) + u2(1)
                 + u2(cp.utf8("value")) + b"c"
                 + u2(cp.utf8("Ldev/settledlands/npc/GuideNpc;")))
    mixin_ria = u2(1) + mixin_ann

    class_attrs = (attr(a_src, u2(cp.utf8("EarlyCorpseTextureMixin.java")))
                   + attr(a_ria, mixin_ria))

    # структура класса: header, cp, access/this/super, interfaces, fields, methods, attrs
    out = (b"\xca\xfe\xba\xbe" + u2(0) + u2(65) + cp.dump()
           + u2(0x0421) + u2(this_idx) + u2(super_idx) + u2(0)
           + u2(0)
           + u2(2) + init_method + handler
           + u2(2) + class_attrs)
    return out, dict(this_index=this_idx, code=code, code_len=len(code),
                     cp_size=len(cp.items), s_empty=s_empty, s_log=s_log)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    cls, info = build_mixin_class()
    print(f"mixin class: {len(cls)} байт, cp={info['cp_size']}, code={info['code_len']} байт")

    zin = zipfile.ZipFile(a.base)
    names = zin.namelist()
    assert MIXIN_CLASS + ".class" not in names

    json_name = "settledlands.mixins.json"
    mixins = json.loads(zin.read(json_name))
    assert MIXIN_SIMPLE not in mixins["mixins"]
    # вставляем сразу после GuideCorpseKeepMixin, чтобы не сломать defaultRequire-логику
    mixins["mixins"].insert(0, MIXIN_SIMPLE)
    json_bytes = (json.dumps(mixins, indent=2, ensure_ascii=False) + "\n").encode("utf-8")

    toml_name = "META-INF/neoforge.mods.toml"
    toml_txt = zin.read(toml_name).decode("utf-8")
    old_ver = f'version="0.8.0-build229-sablesweep"'
    assert old_ver in toml_txt
    toml_txt = toml_txt.replace(old_ver, f'version="{NEW_VERSION}"')
    marker = "description='''\n"
    assert marker in toml_txt
    toml_txt = toml_txt.replace(marker, marker + CHANGELOG + "\n", 1)
    parsed = tomllib.loads(toml_txt)
    assert parsed["mods"][0]["version"] == NEW_VERSION, parsed["mods"][0]["version"]

    patchinfo_name = "settledlands-patch-info.txt"
    patchinfo = None
    if patchinfo_name in names:
        patchinfo = (PATCHINFO_ADD + "\n" + zin.read(patchinfo_name).decode("utf-8")).encode("utf-8")

    out = zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED, compresslevel=9)
    for n in names:
        data = zin.read(n)
        if n == json_name:
            data = json_bytes
        elif n == toml_name:
            data = toml_txt.encode("utf-8")
        elif patchinfo is not None and n == patchinfo_name:
            data = patchinfo
        out.writestr(zin.getinfo(n), data)
    zi = zipfile.ZipInfo(MIXIN_CLASS + ".class", date_time=(2026, 10, 27, 12, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    out.writestr(zi, cls)
    out.close()

    data = open(a.out, "rb").read()
    print(f"готово: {a.out}  {len(data)} B  sha256 {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
