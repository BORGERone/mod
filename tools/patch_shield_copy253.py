#!/usr/bin/env python3
"""Candidate 253 from the exact released 252; no third-party JARs are bundled."""
import argparse, hashlib, pathlib, struct, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_joml248, classedit, decode, gen_api253, patch_npc_stats246 as prev, patch_player_motion251 as p251
from patch_corpse_pose237 import constant_ref, find_invoke

BASE_SHA = '29a0e6e6245d9a906865a4020cf0f2c4532e3a488914260bf9d80df4e57dbc3d'
NEW_VERSION = '0.8.0-build253-shield-copy'

MODEL = p251.MODEL
BRIDGE = p251.BRIDGE
ITEM_DESC = p251.ITEM
COPY_PAYLOAD = 'dev/settledlands/npc/NpcCopyPickPayload'
COPY_DESC = '(Ldev/settledlands/npc/GuideNpc;)Lnet/minecraft/world/item/ItemStack;'
COPY_FIX = 'dev/settledlands/npc/NpcCopyEggFix253'
EGG_ITEM_DESC = '(Ldev/settledlands/npc/GuideNpc;)Lnet/minecraft/world/item/Item;'
WITH_ID_DESC = '(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;'

ADDED = {COPY_FIX + '.class'}
CHANGED = {
    MODEL + '.class',
    COPY_PAYLOAD + '.class',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}

CHANGELOG = (
    'Build 253: remove legacy +40deg blocking shield tilt/offset in BbModel.renderActorHeldItem '
    'and populate required entity id in NpcCopyPickPayload.buildCopyEgg (DataComponents.ENTITY_DATA / '
    'CustomData.CODEC_WITH_ID) to fix client freeze and NBT save crash on NPC copy. '
    'First-person/third-person HnS+FA+TnM, crawl, relog appearance, stats and revival remain unchanged.'
)
PATCHINFO = (
    'сборка 253: удалён старый поворот/сдвиг щита при блоке (+40° по X) в BbModel.renderActorHeldItem; '
    'исправлен фриз и вылет при копировании НПС (добавлен строковый id сущности в DataComponents.ENTITY_DATA '
    'яйца призыва NpcCopyPickPayload.buildCopyEgg); удары от 1-го и 3-го лица, ползание, перезаход и механики сохранены.'
)


def compile_sources(java: pathlib.Path, work: pathlib.Path):
    api = work / 'api'
    out = work / 'classes'
    gen_api253.main(api)
    out.mkdir()
    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(compiler.read_bytes()).hexdigest() == prev.ECJ_SHA
    sources = sorted(api.rglob('*.java')) + sorted((ROOT / 'src/build253').rglob('*.java'))
    subprocess.run(
        [str(java), '-jar', str(compiler), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-d', str(out), *map(str, sources)],
        check=True,
    )
    produced = {
        p.relative_to(out).as_posix()
        for p in out.rglob('*.class')
        if p.relative_to(out).as_posix().startswith('dev/settledlands/npc/NpcCopyEggFix253')
    }
    assert produced == ADDED, produced
    return {name: (out / name).read_bytes() for name in ADDED}


def patch_model(raw: bytes) -> bytes:
    """Remove legacy Build 179/180 blocking shield tilt (+40deg X + translate) in renderActorHeldItem."""
    k = classedit.Klass(raw)
    prev.assert_pool_roundtrip(k, raw)
    m = k.find_method('renderActorHeldItem', ITEM_DESC)
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])

    blk_pc, blk_sz = find_invoke(k, code, 'net/minecraft/world/entity/LivingEntity', 'isBlocking', '()Z')
    assert blk_pc == 185 and blk_sz == 3 and code[184] == 0x2B, (blk_pc, blk_sz, code[184])
    mc_pc, mc_sz = find_invoke(k, code, 'net/minecraft/client/Minecraft', 'getInstance', '()Lnet/minecraft/client/Minecraft;')
    assert mc_pc == 228 and mc_sz == 3, (mc_pc, mc_sz)

    # Verify exact branches and operations inside 184..228 before replacing with nops:
    assert code[188] == 0x99 and 188 + struct.unpack('>h', code[189:191])[0] == 228
    use_pc, _ = find_invoke(k, code, 'net/minecraft/world/item/ItemStack', 'getUseAnimation', '()Lnet/minecraft/world/item/UseAnim;')
    assert use_pc == 193
    assert code[196] == 0xB2 and constant_ref(k, struct.unpack('>H', code[197:199])[0]) == (
        'net/minecraft/world/item/UseAnim', 'BLOCK', 'Lnet/minecraft/world/item/UseAnim;'
    )
    assert code[199] == 0xA6 and 199 + struct.unpack('>h', code[200:202])[0] == 228
    assert code[225] == 0xB6 and constant_ref(k, struct.unpack('>H', code[226:228])[0]) == (
        'com/mojang/blaze3d/vertex/PoseStack', 'translate', '(FFF)V'
    )

    targets = decode.targets(bytes(code))
    assert not any(184 <= t < 228 for t in targets) and 228 in targets, targets

    # Operand stack is empty at 184 and 228; replacing 184..228 with nops preserves all PCs and StackMapTable frames.
    code[184:228] = b'\x00' * (228 - 184)

    # Verify both 251 hooks remain intact at their exact offsets:
    pos_pc, _ = find_invoke(
        k, code, BRIDGE, 'itemPosition',
        f'(L{MODEL};Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Z)V',
    )
    ren_pc, _ = find_invoke(
        k, code, BRIDGE, 'renderHeldItem',
        '(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/world/entity/LivingEntity;'
        'Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;'
        f'ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IL{MODEL};)V',
    )
    assert pos_pc == 152 and ren_pc == 264, (pos_pc, ren_pc)

    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def _before_entity_data_set(k, code):
    pc, _ = find_invoke(
        k, code, 'net/minecraft/world/item/component/CustomData', 'of',
        '(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/component/CustomData;',
    )
    # 45: aload_1; 46: getstatic ENTITY_DATA; 49: aload_3; 50: invokestatic CustomData.of
    assert pc == 50 and code[45] == 0x2B and code[46] == 0xB2 and code[49] == 0x2D, pc
    return 45


def _before_spawn_egg(k, code):
    pc, sz = find_invoke(k, code, 'dev/settledlands/npc/NpcContent', 'spawnEgg', '()Lnet/minecraft/world/item/Item;')
    assert pc == 4 and sz == 3, (pc, sz)
    return pc


def patch_copy_payload(raw: bytes) -> bytes:
    """Populate entityTag 'id' before CustomData.of(entityTag) and choose matching spawn egg item."""
    raw = prev.insert_before(
        raw, 'buildCopyEgg', COPY_DESC, _before_entity_data_set,
        lambda k: prev.static_call(k, COPY_FIX, 'withEntityId', WITH_ID_DESC, b'\x2a\x2d') + b'\x57',
        stack=7,
    )
    raw = prev.insert_before(
        raw, 'buildCopyEgg', COPY_DESC, _before_spawn_egg,
        lambda k: b'\x2a',
        stack=7,
    )
    k = classedit.Klass(raw)
    m = k.find_method('buildCopyEgg', COPY_DESC)
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    at, sz = find_invoke(k, code, 'dev/settledlands/npc/NpcContent', 'spawnEgg', '()Lnet/minecraft/world/item/Item;')
    assert sz == 3 and code[at] == 0xB8, (at, sz, code[at])
    code[at:at + 3] = prev.op(0xB8, k.add_methodref(COPY_FIX, 'eggItem', EGG_ITEM_DESC))
    parts['code'] = bytes(code)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_entries(entries, compiled):
    assert set(compiled) == ADDED and not ADDED.intersection(entries)
    updated = dict(entries)
    updated.update(compiled)
    updated[MODEL + '.class'] = patch_model(entries[MODEL + '.class'])
    updated[COPY_PAYLOAD + '.class'] = patch_copy_payload(entries[COPY_PAYLOAD + '.class'])

    name = 'META-INF/neoforge.mods.toml'
    text = entries[name].decode()
    old = 'version="0.8.0-build252-fp-hns"'
    marker = "description='''"
    assert text.count(old) == 1 and text.count(marker) == 1
    text = text.replace(old, 'version="' + NEW_VERSION + '"', 1).replace(marker, marker + CHANGELOG + '\n\n', 1)
    assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
    updated[name] = text.encode()
    updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode()).encode()

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
    with tempfile.TemporaryDirectory(prefix='settledlands-253-compile-') as td:
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
