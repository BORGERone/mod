#!/usr/bin/env python3
"""Build 236 «corpse-tex» — труп получает модель, даже если применение профиля упало.

Диагностика: GuideRenderer рисует труп только если
`BbModelManager.getModel(entity.texturePath())` не null, а `texturePath()` читает
`entityData(DATA_TEXTURE)`. Это поле выставляется:

  * `GuideNpc.applyProfile()` — ПОСЛЕДНЕЙ строкой (после setCustomName,
    refreshDimensions, setPoseIndex), а вызывающий код ловит Throwable и молчит;
  * `NpcProfile.texture` попадает в поле профиля только в конце
    `GuideNpc.setProfile()` — после mobilitySettingsChanged/resetMobility.

То есть любое исключение в любом из этих мест = пустая текстура = труп не
рисуется вообще («прозрачная модель»).

Что делает патч (две вставки, обе без новых ветвлений => новых фреймов не нужно):

  1. `GuideNpc.applyProfile()` — сразу после `astore_1` (offset 5) выставляем
     DATA_TEXTURE из profile.texture (через Objects.toString(..., "") — без NPE
     на null-текстуре), т.е. ДО всего, что может бросить.
  2. `PlayerCorpsePossession.dressBodyCorpse()` — в самом конце (перед return)
     ещё раз выставляем DATA_TEXTURE из PendingBody.model() и пишем в лог
     `[SettledLands][corpse] model=… tex=…`. Это последняя точка метода: перекрыть
     значение уже некому, а лог сразу покажет, пустое ли имя модели.

    python3 tools/patch_corpse_tex.py --base settledlands-0.8.0-build235-probe.jar \
        --out settledlands-0.8.0-build236-corpse-tex.jar
"""
import argparse, hashlib, struct, sys, zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import classedit  # noqa: E402

NEW_VERSION = "0.8.0-build236-corpse-tex"
OLD_VERSION = "0.8.0-build235-probe"
NPC = "dev/settledlands/npc/GuideNpc"
CORPSE = "dev/settledlands/npc/GuideCorpseNpc"
POSS = "dev/settledlands/npc/PlayerCorpsePossession"
PENDING = POSS + "$PendingBody"
SED = "net/minecraft/network/syncher/SynchedEntityData"
ACC = "net/minecraft/network/syncher/EntityDataAccessor"

CHANGELOG = ("Build 236: the corpse model name (DATA_TEXTURE) is now written before anything can throw - "
             "at the start of GuideNpc.applyProfile() and at the very end of dressBodyCorpse(), which also "
             "logs model=... tex=... . Earlier builds set it as the last statement of a method called inside "
             "catch(Throwable), so any exception left the field empty and the corpse rendered as nothing.")
PATCHINFO = ("сборка 236: имя модели трупа (DATA_TEXTURE) пишется ДО того, как что-либо может бросить — "
             "в начале GuideNpc.applyProfile() и в самом конце dressBodyCorpse(), плюс лог model=… tex=… . "
             "Раньше это была последняя строка метода, вызываемого внутри catch(Throwable): любое исключение "
             "оставляло поле пустым, и труп не рисовался вообще.")


def u2(v):
    return struct.pack(">H", v)


def ldc(cp, idx):
    return b"\x12" + bytes([idx]) if idx <= 255 else b"\x13" + u2(idx)


def patch_pool(k, raw):
    """Проверить, что пересборка пула без добавлений байт-в-байт равна оригиналу."""
    saved = k._dirty
    k._dirty = True
    pool = k._pool_bytes()
    k._dirty = saved
    assert pool == raw[10:k.cp_end], "пересборка пула не совпала с оригиналом"
    return pool


def patch_apply_profile(raw):
    k = classedit.Klass(raw)
    patch_pool(k, raw)
    f_ed = k.add_fieldref(NPC, "entityData", "L" + SED + ";")
    f_dt = k.add_fieldref(NPC, "DATA_TEXTURE", "L" + ACC + ";")
    f_tex = k.add_fieldref("dev/settledlands/npc/NpcProfile", "texture", "Ljava/lang/String;")
    m_set = k.add_methodref(SED, "set", "(L" + ACC + ";Ljava/lang/Object;)V")
    m_tostr = k.add_methodref("java/util/Objects", "toString",
                              "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;")
    s_empty = k.add_string("")
    ins = (b"\x2a" + b"\xb4" + u2(f_ed) + b"\xb2" + u2(f_dt) + b"\x2b" + b"\xb4" + u2(f_tex)
           + ldc(k, s_empty) + b"\xb8" + u2(m_tostr) + b"\xb6" + u2(m_set))
    m = k.find_method("applyProfile", "()V")
    parts = classedit.code_parts(k, m)
    # offset 5 = сразу после `aload_0; getfield profile; astore_1`
    assert (parts["code"][0] == 0x2A and parts["code"][1] == 0xB4
            and parts["code"][4] == 0x4C), "не тот пролог applyProfile (ожидался aload_0/getfield/astore_1)"
    parts = classedit.insert(parts, 5, ins)
    parts["max_stack"] = max(parts["max_stack"], 6)
    return k.dump(m, classedit.build_code_body(k, parts)), len(ins)


def patch_dress(raw):
    k = classedit.Klass(raw)
    patch_pool(k, raw)
    f_ed = k.add_fieldref(NPC, "entityData", "L" + SED + ";")
    f_dt = k.add_fieldref(NPC, "DATA_TEXTURE", "L" + ACC + ";")
    m_set = k.add_methodref(SED, "set", "(L" + ACC + ";Ljava/lang/Object;)V")
    m_model = k.add_methodref(PENDING, "model", "()Ljava/lang/String;")
    m_tex = k.add_methodref(CORPSE, "texturePath", "()Ljava/lang/String;")
    f_logger = k.add_fieldref(POSS, "LOGGER", "Lorg/slf4j/Logger;")
    m_info = k.add_interfaceref("org/slf4j/Logger", "info",
                                "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V")
    s_msg = k.add_string("[SettledLands][corpse] model={} tex={}")
    ins = (b"\x2a" + b"\xb4" + u2(f_ed) + b"\xb2" + u2(f_dt)
           + b"\x2b" + b"\xb6" + u2(m_model) + b"\xb6" + u2(m_set)
           + b"\xb2" + u2(f_logger) + ldc(k, s_msg)
           + b"\x2b" + b"\xb6" + u2(m_model)
           + b"\x2a" + b"\xb6" + u2(m_tex)
           # invokeinterface: count = число слотов аргументов + objectref = 4
           + b"\xb9" + u2(m_info) + b"\x04\x00")
    m = k.find_method("dressBodyCorpse")
    parts = classedit.code_parts(k, m)
    code = parts["code"]
    assert code[-1] == 0xB1, "dressBodyCorpse не заканчивается на return"
    parts = classedit.insert(parts, len(code) - 1, ins)
    parts["max_stack"] = max(parts["max_stack"], 4)
    return k.dump(m, classedit.build_code_body(k, parts)), len(ins)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    zin = zipfile.ZipFile(a.base)
    names = zin.namelist()
    npc_raw = zin.read(NPC + ".class")
    poss_raw = zin.read(POSS + ".class")
    npc_new, n1 = patch_apply_profile(npc_raw)
    poss_new, n2 = patch_dress(poss_raw)
    print(f"applyProfile: +{n1} B ({len(npc_raw)} -> {len(npc_new)}); "
          f"dressBodyCorpse: +{n2} B ({len(poss_raw)} -> {len(poss_new)})")

    toml_name = "META-INF/neoforge.mods.toml"
    toml_txt = zin.read(toml_name).decode("utf-8")
    old = f'version="{OLD_VERSION}"'
    assert old in toml_txt
    toml_txt = toml_txt.replace(old, f'version="{NEW_VERSION}"')
    marker = "description='''\n"
    toml_txt = toml_txt.replace(marker, marker + CHANGELOG + "\n", 1)
    import tomllib
    assert tomllib.loads(toml_txt)["mods"][0]["version"] == NEW_VERSION

    patchinfo_name = "settledlands-patch-info.txt"
    patchinfo = (PATCHINFO + "\n" + zin.read(patchinfo_name).decode("utf-8")).encode("utf-8")

    out = zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED, compresslevel=9)
    for n in names:
        data = zin.read(n)
        if n == NPC + ".class":
            data = npc_new
        elif n == POSS + ".class":
            data = poss_new
        elif n == toml_name:
            data = toml_txt.encode("utf-8")
        elif n == patchinfo_name:
            data = patchinfo
        out.writestr(zin.getinfo(n), data)
    out.close()

    data = open(a.out, "rb").read()
    print(f"готово: {a.out}  {len(data)} B  sha256 {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
