#!/usr/bin/env python3
"""Build 233 «probe»: диагностический зонд для рендера трупа (без изменения поведения).

Почему: build 232 доказал, что на СЕРВЕРЕ DATA_TEXTURE теперь выставляется
верно (в логе 'corpse texture pre-set: provodnik.bbmodel'). Значит дефект живёт
на КЛИЕНТЕ: GuideCorpseFreezeMixin на HEAD renderCorpse отменяет ванильный
рендер и рисует модель сам, а если BbModelManager.getModel(texturePath()) == null,
он вообще ничего не рисует. Нужно увидеть клиентские значения.

Что добавлено:
  * dev/settledlands/diag/TexProbe — обычный класс (version 49, старый
    inference-верификатор, StackMapTable не нужен), умеет дедуплицировать:
    печатает строку только когда значение по тегу изменилось. Поэтому
    per-frame вызовы не спамят лог.
  * dev/settledlands/mixin/CorpseRenderProbeMixin — @Inject HEAD renderCorpse
    (client), только чтение:
        client.corpse.coffin / .anim / .tex / .model
        client.modelsDir / client.models (список файлов в settledlands/models)

    python3 tools/patch_probe233.py --base settledlands-0.8.0-build232-earlytex2.jar \
        --out settledlands-0.8.0-build233-probe.jar
"""
import argparse, hashlib, json, struct, sys, zipfile, tomllib

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from patch_earlytex_mixin import CP, u2, u4, attr, MIXIN_CLASS

PROBE = "dev/settledlands/diag/TexProbe"
PROBE_MIXIN = "dev/settledlands/mixin/CorpseRenderProbeMixin"
NEW_VERSION = "0.8.0-build234-probe"
CHANGELOG = (
    "Build 233: diagnostics for the corpse model. Build 232 proved the server now sets DATA_TEXTURE "
    "correctly (log line 'corpse texture pre-set: provodnik.bbmodel'), so the remaining problem is on the "
    "client: GuideCorpseFreezeMixin cancels the vanilla corpse render and draws the bbmodel itself, and if "
    "BbModelManager.getModel(texturePath()) returns null it draws nothing. This build only READS state and "
    "logs it once per change: client corpse texture / die animation / coffin flag / whether the model "
    "resolves, plus the models directory and the list of files the client actually sees. No gameplay change."
)
PATCHINFO_ADD = (
    "сборка 233: диагностика модели трупа. На сервере DATA_TEXTURE уже выставляется верно (доказано логом 232: "
    "'corpse texture pre-set: provodnik.bbmodel'), поэтому проблема на клиенте: GuideCorpseFreezeMixin "
    "отменяет ванильный рендер renderCorpse и рисует модель сам, а при getModel()==null не рисует ничего. "
    "Добавлен TexProbe (дедуплицирующий вывод) и CorpseRenderProbeMixin (только чтение): клиентское имя "
    "модели, анимация die, флаг гроба, разрешается ли модель, каталог моделей и список файлов в нём."
)


class Asm:
    """Крохотный ассемблер: метки + 2-байтные относительные переходы."""

    def __init__(self):
        self.buf = bytearray()
        self.labels = {}
        self.fixups = []      # (pos, label, kind)  kind='rel16'

    def raw(self, *b):
        for x in b:
            if isinstance(x, (bytes, bytearray)):
                self.buf += x
            else:
                self.buf.append(x & 0xFF)

    def op(self, opcode, idx=None):
        self.buf.append(opcode)
        if idx is not None:
            self.buf += u2(idx)

    def label(self, name):
        self.labels[name] = len(self.buf)

    def jump(self, opcode, label):
        self.buf.append(opcode)
        self.fixups.append((len(self.buf), label))
        self.buf += b"\x00\x00"

    def finish(self):
        for pos, lab in self.fixups:
            if lab not in self.labels:
                raise KeyError(lab)
            rel = self.labels[lab] - pos
            self.buf[pos:pos + 2] = struct.pack(">h", rel)
        return bytes(self.buf)


def build_probe_class():
    cp = CP()
    a_code = cp.utf8("Code")
    a_src = cp.utf8("SourceFile")

    this_idx = cp.cls(PROBE)
    super_idx = cp.cls("java/lang/Object")
    f_last = cp.field(PROBE, "LAST", "Ljava/util/HashMap;")

    m_hash_init = cp.method("java/util/HashMap", "<init>", "()V")
    m_map_get = cp.method("java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
    m_map_put = cp.method("java/util/HashMap", "put",
                          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
    m_equals = cp.method("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")
    f_sysout = cp.field("java/lang/System", "out", "Ljava/io/PrintStream;")
    c_sb = cp.cls("java/lang/StringBuilder")
    m_sb_init = cp.method("java/lang/StringBuilder", "<init>", "()V")
    m_sb_app = cp.method("java/lang/StringBuilder", "append",
                         "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
    m_sb_str = cp.method("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
    m_println = cp.method("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
    s_prefix = cp.string("[SettledLands][probe] ")
    s_eq = cp.string(" = ")
    s_null = cp.string("NULL")
    c_object = cp.cls("java/lang/Object")
    m_getclass = cp.method("java/lang/Object", "getClass", "()Ljava/lang/Class;")
    m_simplename = cp.method("java/lang/Class", "getSimpleName", "()Ljava/lang/String;")
    m_log = cp.method(PROBE, "log", "(Ljava/lang/String;Ljava/lang/String;)V")
    for idx in (s_prefix, s_eq, s_null):
        assert idx <= 255, "ldc-индекс > 255"

    # --- <clinit>
    a = Asm()
    a.op(0xBB, cp.cls("java/util/HashMap"))     # new
    a.raw(0x59)                                 # dup
    a.op(0xB7, m_sb_init if False else m_hash_init)   # invokespecial
    a.op(0xB3, f_last)                          # putstatic
    a.raw(0xB1)
    clinit_code = a.finish()

    # --- log(String tag, String value)
    a = Asm()
    a.op(0xB2, f_last)
    a.raw(0x2A)                                 # aload_0
    a.op(0xB6, m_map_get)
    a.op(0xC0, cp.cls("java/lang/String"))      # checkcast
    a.raw(0x4D)                                 # astore_2
    a.raw(0x2C)                                 # aload_2
    a.raw(0x2B)                                 # aload_1
    a.op(0xB8, m_equals)
    a.jump(0x99, "L1")                          # ifeq L1
    a.raw(0xB1)                                 # return
    a.label("L1")
    a.op(0xB2, f_last)
    a.raw(0x2A, 0x2B)                           # aload_0, aload_1
    a.op(0xB6, m_map_put)
    a.raw(0x57)                                 # pop
    a.op(0xB2, f_sysout)
    a.op(0xBB, c_sb)
    a.raw(0x59)
    a.op(0xB7, m_sb_init)
    a.raw(0x12, s_prefix)                       # ldc
    a.op(0xB6, m_sb_app)
    a.raw(0x2A)                                 # aload_0
    a.op(0xB6, m_sb_app)
    a.raw(0x12, s_eq)
    a.op(0xB6, m_sb_app)
    a.raw(0x2B)                                 # aload_1
    a.op(0xB6, m_sb_app)
    a.op(0xB6, m_sb_str)
    a.op(0xB6, m_println)
    a.raw(0xB1)
    log_code = a.finish()

    # --- logObj(String tag, Object value)
    a = Asm()
    a.raw(0x2B)                                 # aload_1
    a.jump(0xC7, "L1")                          # ifnonnull L1
    a.raw(0x12, s_null)
    a.jump(0xA7, "L2")                          # goto L2
    a.label("L1")
    a.raw(0x2B)                                 # aload_1
    a.op(0xB6, m_getclass)
    a.op(0xB6, m_simplename)
    a.label("L2")
    a.raw(0x4D)                                 # astore_2
    a.raw(0x2A, 0x2C)                           # aload_0, aload_2
    a.op(0xB8, m_log)
    a.raw(0xB1)
    logobj_code = a.finish()

    def code_attr(code, max_stack, max_locals):
        body = u2(max_stack) + u2(max_locals) + u4(len(code)) + code + u2(0) + u2(0)
        return attr(a_code, body)

    methods = [
        (0x0001, cp.utf8("<init>"), cp.utf8("()V"), [code_attr(
            bytes([0x2A, 0xB7]) + u2(cp.method("java/lang/Object", "<init>", "()V")) + b"\xB1", 1, 1)]),
        (0x0008, cp.utf8("<clinit>"), cp.utf8("()V"), [code_attr(clinit_code, 2, 0)]),
        (0x0009, cp.utf8("log"), cp.utf8("(Ljava/lang/String;Ljava/lang/String;)V"),
         [code_attr(log_code, 5, 3)]),
        (0x0009, cp.utf8("logObj"), cp.utf8("(Ljava/lang/String;Ljava/lang/Object;)V"),
         [code_attr(logobj_code, 3, 3)]),
    ]

    # ВАЖНО: все записи пула должны быть добавлены ДО cp.dump(), иначе
    # индексы в структуре класса окажутся за границей пула (краш 233:
    # ClassReader.readUtf AIOOBE Index 79 out of bounds for length 79).
    field_body = (u2(0x001A) + u2(cp.utf8("LAST"))
                  + u2(cp.utf8("Ljava/util/HashMap;")) + u2(0))
    methods_body = bytearray()
    for acc, ni, di, attrs in methods:
        methods_body += u2(acc) + u2(ni) + u2(di) + u2(len(attrs)) + b"".join(attrs)
    attrs_body = u2(1) + attr(a_src, u2(cp.utf8("TexProbe.java")))

    body = (u2(0x0031) + u2(this_idx) + u2(super_idx) + u2(0)      # public final super
            + u2(1) + field_body                                   # fields
            + u2(len(methods)) + bytes(methods_body)
            + attrs_body)
    out = b"\xca\xfe\xba\xbe" + u2(0) + u2(49) + cp.dump() + body
    return out, dict(cp=len(cp.items), log=len(log_code), logobj=len(logobj_code))


def build_probe_mixin():
    cp = CP()
    a_code = cp.utf8("Code")
    a_src = cp.utf8("SourceFile")
    a_ria = cp.utf8("RuntimeInvisibleAnnotations")
    a_rva = cp.utf8("RuntimeVisibleAnnotations")

    this_idx = cp.cls(PROBE_MIXIN)
    super_idx = cp.cls("java/lang/Object")

    init_code = bytes([0x2A, 0xB7]) + u2(cp.method("java/lang/Object", "<init>", "()V")) + b"\xB1"
    init_attr = attr(a_code, u2(1) + u2(1) + u4(len(init_code)) + init_code + u2(0) + u2(0))
    init_method = (u2(0x0001) + u2(cp.utf8("<init>")) + u2(cp.utf8("()V"))
                   + u2(1) + init_attr)

    corpse = "dev/settledlands/npc/GuideCorpseNpc"
    desc = ("(Ldev/settledlands/npc/GuideCorpseNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            "Lnet/minecraft/client/renderer/MultiBufferSource;ILorg/spongepowered/asm/mixin/"
            "injection/callback/CallbackInfo;)V")

    m_iscoffin = cp.method(corpse, "isCoffin", "()Z")
    m_anim = cp.method(corpse, "getCustomAnimName", "()Ljava/lang/String;")
    m_tex = cp.method(corpse, "texturePath", "()Ljava/lang/String;")
    m_tostr_b = cp.method("java/lang/Boolean", "toString", "(Z)Ljava/lang/String;")
    m_getmodel = cp.method("dev/settledlands/model/BbModelManager", "getModel",
                           "(Ljava/lang/String;)Ldev/settledlands/model/BbModel;")
    m_modelsdir = cp.method("dev/settledlands/model/BbModelStorage", "getModelsDir", "()Ljava/nio/file/Path;")
    m_listmodels = cp.method("dev/settledlands/model/BbModelStorage", "listAvailableModels", "()Ljava/util/List;")
    m_strval = cp.method("java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;")
    m_log = cp.method(PROBE, "log", "(Ljava/lang/String;Ljava/lang/String;)V")
    m_logobj = cp.method(PROBE, "logObj", "(Ljava/lang/String;Ljava/lang/Object;)V")

    keys = ["client.corpse.coffin", "client.corpse.anim", "client.corpse.tex",
            "client.corpse.model", "client.modelsDir", "client.models"]
    strs = {k: cp.string(k) for k in keys}
    for k, v in strs.items():
        assert v <= 255, k

    a = Asm()
    a.raw(0x12, strs["client.corpse.coffin"])
    a.raw(0x2B)                                  # aload_1 (corpse)
    a.op(0xB6, m_iscoffin)
    a.op(0xB8, m_tostr_b)
    a.op(0xB8, m_log)

    a.raw(0x12, strs["client.corpse.anim"])
    a.raw(0x2B)
    a.op(0xB6, m_anim)
    a.op(0xB8, m_log)

    a.raw(0x12, strs["client.corpse.tex"])
    a.raw(0x2B)
    a.op(0xB6, m_tex)
    a.op(0xB8, m_log)

    a.raw(0x12, strs["client.corpse.model"])
    a.raw(0x2B)
    a.op(0xB6, m_tex)
    a.op(0xB8, m_getmodel)
    a.op(0xB8, m_logobj)

    a.raw(0x12, strs["client.modelsDir"])
    a.op(0xB8, m_modelsdir)
    a.op(0xB8, m_strval)
    a.op(0xB8, m_log)

    a.raw(0x12, strs["client.models"])
    a.op(0xB8, m_listmodels)
    a.op(0xB8, m_strval)
    a.op(0xB8, m_log)
    a.raw(0xB1)
    code = a.finish()

    code_attr = attr(a_code, u2(3) + u2(8) + u4(len(code)) + code + u2(0) + u2(0))

    at_ann = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/injection/At;")) + u2(1)
              + u2(cp.utf8("value")) + b"s" + u2(cp.utf8("HEAD")))
    inject = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/injection/Inject;")) + u2(3)
              + u2(cp.utf8("method")) + b"s" + u2(cp.utf8("renderCorpse"))
              + u2(cp.utf8("at")) + b"@" + at_ann
              + u2(cp.utf8("require")) + b"I" + u2(cp.integer(0)))
    handler = (u2(0x0002) + u2(cp.utf8("settledlands$probeRenderCorpse")) + u2(cp.utf8(desc))
               + u2(2) + code_attr + attr(a_rva, u2(1) + inject))

    # @Mixin(targets = {"dev.settledlands.npc.client.GuideRenderer"})
    mixin_ann = (u2(cp.utf8("Lorg/spongepowered/asm/mixin/Mixin;")) + u2(1)
                 + u2(cp.utf8("targets")) + b"[" + u2(1) + b"s"
                 + u2(cp.utf8("dev.settledlands.npc.client.GuideRenderer")))
    class_attrs = (attr(a_src, u2(cp.utf8("CorpseRenderProbeMixin.java")))
                   + attr(a_ria, u2(1) + mixin_ann))

    body = (u2(0x0421) + u2(this_idx) + u2(super_idx) + u2(0)
            + u2(0)
            + u2(2) + init_method + handler
            + u2(2) + class_attrs)          # оба счётчика атрибутов сверены ниже
    out = b"\xca\xfe\xba\xbe" + u2(0) + u2(65) + cp.dump() + body
    return out, dict(cp=len(cp.items), code=len(code))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    probe, pinfo = build_probe_class()
    mixin, minfo = build_probe_mixin()
    print(f"TexProbe: {len(probe)} B (cp={pinfo['cp']}, log={pinfo['log']}, logObj={pinfo['logobj']})")
    print(f"CorpseRenderProbeMixin: {len(mixin)} B (cp={minfo['cp']}, code={minfo['code']})")

    zin = zipfile.ZipFile(a.base)
    names = zin.namelist()
    assert PROBE + ".class" not in names and PROBE_MIXIN + ".class" not in names

    json_name = "settledlands.mixins.json"
    cfg = json.loads(zin.read(json_name))
    simple = PROBE_MIXIN.rsplit("/", 1)[1]
    assert simple not in cfg["mixins"] and simple not in cfg["client"]
    cfg["client"].insert(0, simple)
    json_bytes = (json.dumps(cfg, indent=2, ensure_ascii=False) + "\n").encode("utf-8")

    toml_name = "META-INF/neoforge.mods.toml"
    toml_txt = zin.read(toml_name).decode("utf-8")
    old = 'version="0.8.0-build232-earlytex2"'
    assert old in toml_txt
    toml_txt = toml_txt.replace(old, f'version="{NEW_VERSION}"')
    marker = "description='''\n"
    toml_txt = toml_txt.replace(marker, marker + CHANGELOG + "\n", 1)
    assert tomllib.loads(toml_txt)["mods"][0]["version"] == NEW_VERSION

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
    for cls_name, cls_bytes in ((PROBE, probe), (PROBE_MIXIN, mixin)):
        zi = zipfile.ZipInfo(cls_name + ".class", date_time=(2026, 10, 27, 12, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        out.writestr(zi, cls_bytes)
    out.close()

    data = open(a.out, "rb").read()
    print(f"готово: {a.out}  {len(data)} B  sha256 {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
