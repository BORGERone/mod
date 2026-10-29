#!/usr/bin/env python3
"""Candidate 254 from the exact released 253; no third-party JARs are bundled."""
import argparse, hashlib, json, pathlib, struct, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_joml248, classedit, decode, gen_api254, patch_npc_stats246 as prev, patch_shield_copy253 as p253
from patch_corpse_pose237 import constant_ref, find_invoke

BASE_SHA = 'a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e'
IVY_OLEANDER_SHA = 'cc2ddc5c6f3ec988ea3829912ba18d0f5d7a7f44f1beedc3e9309847f0a32560'
NEW_VERSION = '0.8.0-build254-ui-ivy'

SG = 'dev/settledlands/npc/NpcStatsGraph'
UI = 'dev/settledlands/npc/client/NpcStatsGraphUi'
DEF_MODEL = 'dev/settledlands/npc/NpcDefaultModel254'
DROP_TOP = 'dev/settledlands/npc/client/NpcDropdownTop254'
PROFILE = 'dev/settledlands/npc/NpcProfile'
GUIDE = 'dev/settledlands/npc/GuideNpc'
CLIENT = 'dev/settledlands/npc/client/NpcClient'
CFG_SCREEN = 'dev/settledlands/npc/client/NpcConfigScreen'
BRANCH_SCREEN = 'dev/settledlands/npc/client/NpcBranchScreen'
DROPDOWN = 'dev/settledlands/npc/client/NpcUi$NpcDropdown'
IVY_ASSET = 'assets/settledlands/dragon_default/ivy_oleander.bbmodel'

COMPILED_CLASSES = {
    SG + '.class',
    UI + '.class',
    DEF_MODEL + '.class',
    DROP_TOP + '.class',
}

ADDED = {
    DEF_MODEL + '.class',
    DROP_TOP + '.class',
    IVY_ASSET,
}

CHANGED = {
    SG + '.class',
    UI + '.class',
    PROFILE + '.class',
    GUIDE + '.class',
    CLIENT + '.class',
    CFG_SCREEN + '.class',
    BRANCH_SCREEN + '.class',
    'assets/settledlands/lang/ru_ru.json',
    'assets/settledlands/lang/en_us.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}

CHANGELOG = (
    'Build 254: place default Characteristics node to the left of the initial dialogue line; '
    'render Characteristics spinner glyphs without Z-offset drop shadow so overlapping windows/nodes cover them cleanly; '
    'render open NpcDropdown popups at Z=+500 above all GUI widgets and 3D previews; '
    'bundle ivy_oleander.bbmodel as the default GuideNpc model; '
    'add bottom Randomize button (-2..+2 per stat, inclusive) to the Characteristics node. '
    'All 243-253 mechanics and animations remain intact.'
)
PATCHINFO = (
    'сборка 254: дефолтная позиция ноды характеристик слева от начальной реплики; '
    'убрано просвечивание символов слайдеров характеристик сквозь окна (рендер без Z-тени); '
    'выпадающие списки NpcDropdown подняты на Z=+500 поверх всех элементов и 3D-превью; '
    'дефолтной моделью НПС сделана ivy_oleander.bbmodel; '
    'вниз ноды характеристик добавлена кнопка «Рандомизировать» (-2..+2 по каждому параметру). '
    'Механики и анимации 243–253 сохранены.'
)

LANG_ADD = {
    'assets/settledlands/lang/ru_ru.json': ('gui.settledlands.stats.randomize', 'Рандомизировать'),
    'assets/settledlands/lang/en_us.json': ('gui.settledlands.stats.randomize', 'Randomize'),
}


def compile_sources(java: pathlib.Path, work: pathlib.Path):
    api = work / 'api'
    out = work / 'classes'
    gen_api254.main(api)
    out.mkdir()
    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(compiler.read_bytes()).hexdigest() == prev.ECJ_SHA
    sources = sorted(api.rglob('*.java')) + sorted((ROOT / 'src/build254').rglob('*.java'))
    subprocess.run(
        [str(java), '-jar', str(compiler), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-d', str(out), *map(str, sources)],
        check=True,
    )
    produced = {
        p.relative_to(out).as_posix()
        for p in out.rglob('*.class')
        if p.relative_to(out).as_posix().startswith('dev/settledlands/')
        and p.relative_to(out).as_posix() in COMPILED_CLASSES
    }
    assert produced == COMPILED_CLASSES, produced
    return {name: (out / name).read_bytes() for name in COMPILED_CLASSES}


def patch_profile(raw: bytes) -> bytes:
    """Default NpcProfile.texture to ivy_oleander.bbmodel while preserving explicit '' in NBT."""
    def before_put_texture(k, code):
        for pc, ins in decode.walk(code):
            if ins[0] == 0xB5 and constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (
                PROFILE, 'texture', 'Ljava/lang/String;'
            ):
                assert pc == 25 and code[22] == 0x2A and code[23] == 0x12, pc
                return pc
        raise AssertionError('putfield texture not found in NpcProfile.<init>')

    raw = prev.insert_before(
        raw, '<init>', '()V', before_put_texture,
        lambda k: b'\x57' + prev.static_call(k, DEF_MODEL, 'defaultTexture', '()Ljava/lang/String;'),
        stack=8,
    )

    k = classedit.Klass(raw)
    prev.assert_pool_roundtrip(k, raw)
    m = k.find_method('load', '(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)L' + PROFILE + ';')
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    # 62: aload_2; 63: aload_0; 64: ldc_w "Texture"; 67: invokevirtual getString; 70: putfield texture
    assert code[62] == 0x2C and code[63] == 0x2A and code[64] == 0x13 and code[67] == 0xB6 and code[70] == 0xB5
    tex_idx = struct.unpack('>H', code[65:67])[0]
    assert k.cp[tex_idx]['tag'] == 8 and k.utf8(k.cp[tex_idx]['a']) == 'Texture'
    assert constant_ref(k, struct.unpack('>H', code[68:70])[0]) == (
        'net/minecraft/nbt/CompoundTag', 'getString', '(Ljava/lang/String;)Ljava/lang/String;'
    )
    assert constant_ref(k, struct.unpack('>H', code[71:73])[0]) == (
        PROFILE, 'texture', 'Ljava/lang/String;'
    )
    targets = decode.targets(bytes(code))
    assert not any(64 < t <= 70 for t in targets), targets
    call = prev.op(0xB8, k.add_methodref(DEF_MODEL, 'loadTexture', '(Lnet/minecraft/nbt/CompoundTag;)Ljava/lang/String;'))
    assert len(call) == 3
    code[64:70] = call + b'\x00\x00\x00'
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_guide_npc(raw: bytes) -> bytes:
    """Initialize DATA_TEXTURE and default profiles via NpcDefaultModel254 (server-safe)."""
    def before_define_texture(k, code):
        walk = list(decode.walk(code))
        for i, (pc, ins) in enumerate(walk):
            if ins[0] == 0xB2 and constant_ref(k, struct.unpack('>H', ins[1:3])[0]) == (
                GUIDE, 'DATA_TEXTURE', 'Lnet/minecraft/network/syncher/EntityDataAccessor;'
            ):
                pc_ldc, ins_ldc = walk[i + 1]
                pc_def, ins_def = walk[i + 2]
                assert ins_ldc[0] == 0x12 and ins_def[0] == 0xB6, (pc_ldc, ins_ldc, pc_def, ins_def)
                return pc_def
        raise AssertionError('DATA_TEXTURE define not found')

    raw = prev.insert_before(
        raw, 'defineSynchedData', '(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V',
        before_define_texture,
        lambda k: b'\x57\x2a' + prev.static_call(k, DEF_MODEL, 'initialTexture', '(L' + GUIDE + ';)Ljava/lang/String;'),
        stack=4,
    )

    def before_default_return(k, code):
        assert code[-2:] == b'\x2b\xb0', code[-4:]
        return len(code) - 2

    raw = prev.insert_before(
        raw, 'defaultProfile', '()L' + PROFILE + ';',
        before_default_return,
        lambda k: prev.static_call(k, DEF_MODEL, 'initDefaultProfile', '(L' + GUIDE + ';L' + PROFILE + ';)V', b'\x2a\x2b'),
        stack=3,
    )

    k = classedit.Klass(raw)
    prev.assert_pool_roundtrip(k, raw)
    m = k.find_method('readAdditionalSaveData', '(Lnet/minecraft/nbt/CompoundTag;)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])

    # Site 1 (31..42, 11 bytes): aload_0; new NpcProfile; dup; invokespecial <init>; putfield profile
    assert code[31] == 0x2A and code[32] == 0xBB and code[35] == 0x59 and code[36] == 0xB7 and code[39] == 0xB5
    assert constant_ref(k, struct.unpack('>H', code[37:39])[0]) == (PROFILE, '<init>', '()V')
    assert constant_ref(k, struct.unpack('>H', code[40:42])[0]) == (GUIDE, 'profile', 'L' + PROFILE + ';')
    targets = decode.targets(bytes(code))
    assert not any(31 < t < 42 for t in targets), targets

    create_ref = k.add_methodref(DEF_MODEL, 'createDefaultProfile', '(L' + GUIDE + ';)L' + PROFILE + ';')
    set_ref = k.add_methodref(GUIDE, 'setProfile', '(L' + PROFILE + ';)V')
    repl1 = b'\x2a\x2a' + prev.op(0xB8, create_ref) + prev.op(0xB6, set_ref) + b'\x00\x00\x00'
    assert len(repl1) == 11
    code[31:42] = repl1

    # Site 2 (185..192, 7 bytes): new NpcProfile; dup; invokespecial <init> (185 is jump target; stack=[this])
    assert code[185] == 0xBB and code[188] == 0x59 and code[189] == 0xB7 and code[192] == 0xB5
    assert constant_ref(k, struct.unpack('>H', code[190:192])[0]) == (PROFILE, '<init>', '()V')
    assert not any(185 < t < 192 for t in targets), targets
    repl2 = b'\x2a' + prev.op(0xB8, create_ref) + b'\x00\x00\x00'
    assert len(repl2) == 7
    code[185:192] = repl2

    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_npc_client(raw: bytes) -> bytes:
    """Extract bundled ivy_oleander.bbmodel right after installDefaultCorpseAssets()."""
    return prev.insert_before(
        raw, 'lambda$setup$1', '()V',
        prev.after_call(CLIENT, 'installDefaultCorpseAssets', '()V'),
        lambda k: prev.static_call(k, DROP_TOP, 'installDefaultModelAsset', '()V'),
        stack=1,
    )


def patch_dropdown_screen(raw: bytes, expected_pc: int) -> bytes:
    """Replace 3-byte invokevirtual NpcDropdown.renderPopup with 3-byte invokestatic renderPopupTop."""
    k = classedit.Klass(raw)
    prev.assert_pool_roundtrip(k, raw)
    m = k.find_method('render', '(Lnet/minecraft/client/gui/GuiGraphics;IIF)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    pc, sz = find_invoke(k, code, DROPDOWN, 'renderPopup', '(Lnet/minecraft/client/gui/GuiGraphics;II)V')
    assert pc == expected_pc and sz == 3 and code[pc] == 0xB6, (pc, sz, code[pc])
    repl = prev.op(
        0xB8,
        k.add_methodref(
            DROP_TOP, 'renderPopupTop',
            '(L' + DROPDOWN + ';Lnet/minecraft/client/gui/GuiGraphics;II)V',
        ),
    )
    assert len(repl) == 3
    code[pc:pc + 3] = repl
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_lang(raw: bytes, key: str, value: str) -> bytes:
    data = json.loads(raw.decode('utf-8'))
    assert key not in data, key
    data[key] = value
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def load_ivy_asset() -> bytes:
    model_path = ROOT / 'ivy_oleander.bbmodel'
    raw = model_path.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == IVY_OLEANDER_SHA
    return raw


def patch_entries(entries, compiled):
    assert set(compiled) == COMPILED_CLASSES
    ivy_bytes = load_ivy_asset()
    assert not ADDED.intersection(entries)

    updated = dict(entries)
    updated[SG + '.class'] = compiled[SG + '.class']
    updated[UI + '.class'] = compiled[UI + '.class']
    updated[DEF_MODEL + '.class'] = compiled[DEF_MODEL + '.class']
    updated[DROP_TOP + '.class'] = compiled[DROP_TOP + '.class']
    updated[IVY_ASSET] = ivy_bytes

    updated[PROFILE + '.class'] = patch_profile(entries[PROFILE + '.class'])
    updated[GUIDE + '.class'] = patch_guide_npc(entries[GUIDE + '.class'])
    updated[CLIENT + '.class'] = patch_npc_client(entries[CLIENT + '.class'])
    updated[CFG_SCREEN + '.class'] = patch_dropdown_screen(entries[CFG_SCREEN + '.class'], 752)
    updated[BRANCH_SCREEN + '.class'] = patch_dropdown_screen(entries[BRANCH_SCREEN + '.class'], 605)

    for lang_entry, (k, v) in LANG_ADD.items():
        updated[lang_entry] = patch_lang(entries[lang_entry], k, v)

    name = 'META-INF/neoforge.mods.toml'
    text = entries[name].decode('utf-8')
    old = 'version="0.8.0-build253-shield-copy"'
    marker = "description='''"
    assert text.count(old) == 1 and text.count(marker) == 1
    text = text.replace(old, 'version="' + NEW_VERSION + '"', 1).replace(marker, marker + CHANGELOG + '\n\n', 1)
    assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
    updated[name] = text.encode('utf-8')
    updated['settledlands-patch-info.txt'] = (
        PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
    ).encode('utf-8')

    changed = {n for n in entries if entries[n] != updated[n]}
    assert changed == CHANGED, changed
    assert set(updated) - set(entries) == ADDED
    return updated


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    ap.add_argument('--java', type=pathlib.Path, required=True)
    a = ap.parse_args()
    assert a.base.resolve() != a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest() == BASE_SHA
    with tempfile.TemporaryDirectory(prefix='settledlands-254-compile-') as td:
        compiled = compile_sources(a.java.resolve(), pathlib.Path(td))
        with zipfile.ZipFile(a.base) as base:
            assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
            entries = {i.filename: base.read(i.filename) for i in base.infolist()}
            updated = patch_entries(entries, compiled)
            a.out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(a.out, 'w') as out:
                for info in base.infolist():
                    out.writestr(info, updated[info.filename])
                for name in sorted(ADDED):
                    info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    out.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = a.out.read_bytes()
    print(f'CANDIDATE ONLY {a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
