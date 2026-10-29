#!/usr/bin/env python3
"""Deterministic candidate build 256 from the exact released build 255; no third-party JARs are bundled."""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_joml248
import classedit
import gen_api256
import patch_corpse_click244 as v244
import patch_npc_stats246 as prev
from patch_corpse_pose237 import assert_pool_roundtrip

BASE_SHA = '7866e20a75eb5ef53e254e8e34a5eacc155d0b3ac768b99977ad4d95358b6541'
OLD_VERSION = '0.8.0-build255-auto-ai'
NEW_VERSION = '0.8.0-build256-npc-fp-corpse'

BBMODEL = 'dev/settledlands/model/BbModel'
CORPSE_REC = 'dev/settledlands/npc/CorpseRecovery'
CORPSE_NPC = 'dev/settledlands/npc/GuideCorpseNpc'
PLAYER_BODY = 'dev/settledlands/npc/client/PlayerBodyRenderer'

NPC_BRIDGE = 'dev/settledlands/model/NpcMotionBridge256'
NPC_FRESH = 'dev/settledlands/model/NpcFreshEmfAccess256'
NPC_PROXY = 'dev/settledlands/model/NpcMotionPlayerProxy256'
WALK_ACCESS = 'dev/settledlands/model/WalkAnimationSyncAccess256'
FP_GATE = 'dev/settledlands/npc/client/PlayerFirstPersonBodyGate256'
FP_POSS = 'dev/settledlands/npc/client/FirstPersonPossession256'
FP_SUB = 'dev/settledlands/npc/client/FirstPersonArmSubscriber256'

MIXIN_HAND = 'PossessedFirstPersonHandMixin256'
MIXIN_MODEL = 'PossessedFirstPersonModelMixin256'
MIXIN_LEVEL = 'ClientLevelProxyLookupMixin256'
MIXIN_WALK = 'WalkAnimationStateMixin256'
CLIENT_MIXINS_256 = [MIXIN_HAND, MIXIN_MODEL, MIXIN_LEVEL, MIXIN_WALK]

NPC_RENDER_DESC = (
    '(Ldev/settledlands/npc/GuideNpc;FF'
    'Lcom/mojang/blaze3d/vertex/PoseStack;'
    'Lnet/minecraft/client/renderer/MultiBufferSource;II)V'
)
ITEM_DESC = (
    '(Lnet/minecraft/world/entity/LivingEntity;'
    'Lcom/mojang/blaze3d/vertex/PoseStack;'
    'Lnet/minecraft/client/renderer/MultiBufferSource;'
    'ILnet/minecraft/world/item/ItemStack;Z)V'
)
PRE_EVENT = 'net/neoforged/neoforge/client/event/RenderPlayerEvent$Pre'
PRE_DESC = '(L' + PRE_EVENT + ';)V'
BLOCK_FP_DESC = (
    '(ZLnet/minecraft/client/Minecraft;'
    'Lnet/minecraft/client/player/AbstractClientPlayer;'
    'L' + PRE_EVENT + ';)Z'
)

ADDED = {
    'dev/settledlands/mixin/' + MIXIN_HAND + '.class',
    'dev/settledlands/mixin/' + MIXIN_MODEL + '.class',
    'dev/settledlands/mixin/' + MIXIN_LEVEL + '.class',
    'dev/settledlands/mixin/' + MIXIN_WALK + '.class',
    NPC_FRESH + '.class',
    NPC_FRESH + '$Run.class',
    NPC_BRIDGE + '.class',
    NPC_BRIDGE + '$Biped.class',
    NPC_BRIDGE + '$CustomClipState.class',
    NPC_BRIDGE + '$Pose.class',
    NPC_BRIDGE + '$Rig.class',
    NPC_PROXY + '.class',
    WALK_ACCESS + '.class',
    FP_SUB + '.class',
    FP_POSS + '.class',
    FP_POSS + '$PartSnapshot.class',
    FP_POSS + '$RenderContext.class',
    FP_GATE + '.class',
}

CHANGED = {
    BBMODEL + '.class',
    PLAYER_BODY + '.class',
    CORPSE_REC + '.class',
    'settledlands.mixins.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}

CHANGELOG = (
    'Build 256: connect player-equivalent animation pipeline (Fresh Animations: Player Extension + '
    'Touch\'n Motion retargeting with actor state-machine & held-item fallback) to GuideNpc while '
    'preserving story/combat clips and frozen GuideCorpseNpc/GuideTriggerNpc; render possessed NPC '
    'model in Inventory/GUI during first-person camera and replace first-person Steve arms (including '
    'PAL/HnS swings) with the possessed NPC\'s arm bones and texture; set corpse health to 1/60 HP '
    '(coffin remains 60/60 HP). Autonomous AI, characteristics, shield, HnS, crawl, wear and altar '
    'mechanics remain unchanged.'
)

PATCHINFO = (
    'сборка 256: система анимаций НПС как у игрока (Fresh Animations: Player Extension + Touch\'n Motion '
    'с ретаргетингом на риг Иви, предметами в руках, сохранением сюжетных/боевых клипов и откатом к '
    'актёрской стейт-машине без затрагивания трупов и триггеров); модель вселённого НПС в инвентаре при '
    'игре от первого лица и руки/скин рук вселённого НПС от первого лица (включая взмахи HnS/PAL); '
    'здоровье трупа 1/60 HP (у гроба 60/60 HP). ИИ 255, правки 254, щит и копирование 253, HnS 250–252, '
    'характеристики, бестелесность, износ тела и алтарь сохранены.'
)


def compile_sources(java: pathlib.Path, work: pathlib.Path) -> dict[str, bytes]:
    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(compiler.read_bytes()).hexdigest() == prev.ECJ_SHA
    joml = work / 'joml'
    build_joml248.build(java, joml)
    api = work / 'api'
    out = work / 'classes'
    gen_api256.main(str(api))
    out.mkdir()
    sources = sorted(api.rglob('*.java')) + sorted((ROOT / 'src/build256').rglob('*.java'))
    subprocess.run(
        [
            str(java), '-jar', str(compiler),
            '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
            '-cp', str(joml), '-d', str(out),
            *map(str, sources),
        ],
        check=True,
    )
    api_rel = {p.relative_to(api).with_suffix('.class').as_posix() for p in api.rglob('*.java')}
    produced = {
        p.relative_to(out).as_posix()
        for p in out.rglob('*.class')
        if p.relative_to(out).as_posix().startswith('dev/settledlands/')
        and not any(
            p.relative_to(out).as_posix() == a or p.relative_to(out).as_posix().startswith(a[:-6] + '$')
            for a in api_rel
        )
    }
    assert produced == ADDED, (produced ^ ADDED, produced, ADDED)
    return {name: (out / name).read_bytes() for name in sorted(ADDED)}


def patch_bbmodel(raw: bytes) -> bytes:
    raw = prev.forward_alias(
        raw,
        'render',
        NPC_RENDER_DESC,
        'settledlands$renderNpcOriginal',
        (NPC_BRIDGE, 'renderNpc'),
        '(L' + BBMODEL + ';' + NPC_RENDER_DESC[1:],
        b'\x2a\x2b\x24\x25\x19\x04\x19\x05\x15\x06\x15\x07',
        0xB1,
        8,
        8,
    )
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    assert k.find_method('renderActorHeldItem', ITEM_DESC) is not None
    m_parents = prev.new_method(
        k,
        'settledlands$partParents',
        '()Ljava/util/IdentityHashMap;',
        b'\x2a' + prev.op(0xB4, k.add_fieldref(BBMODEL, 'partParents', 'Ljava/util/IdentityHashMap;')) + b'\xb0',
        1,
        1,
    )
    m_item = prev.new_method(
        k,
        'settledlands$renderHeldItem',
        ITEM_DESC,
        b'\x2a\x2b\x2c\x2d\x15\x04\x19\x05\x15\x06'
        + prev.op(0xB6, k.add_methodref(BBMODEL, 'renderActorHeldItem', ITEM_DESC))
        + b'\xb1',
        7,
        7,
    )
    return v244.rebuild(k, k.methods + [m_parents, m_item])


def patch_player_body_renderer(raw: bytes) -> bytes:
    return prev.insert_before(
        raw,
        'onRenderPlayerPre',
        PRE_DESC,
        prev.after_call('net/minecraft/client/CameraType', 'isFirstPerson', '()Z'),
        lambda k: prev.static_call(
            k,
            FP_GATE,
            'blockFirstPersonRender',
            BLOCK_FP_DESC,
            b'\x19\x04\x2b\x2a',
        ),
        stack=7,
    )


def patch_corpse_recovery(raw: bytes) -> bytes:
    """Set MAX_HEALTH base unconditionally to 60.0 so non-coffin corpses have 1/60 HP (coffin 60/60 HP)."""
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    desc = '(L' + CORPSE_NPC + ';)V'
    m = k.find_method('syncHealth', desc)
    assert m is not None
    parts = classedit.code_parts(k, m)
    assert len(parts['code']) == 60 and parts['exc'] == [] and parts['nested'] == [], parts

    def build(kl: classedit.Klass) -> bytes:
        level_m = kl.add_methodref(CORPSE_NPC, 'level', '()Lnet/minecraft/world/level/Level;')
        client_m = kl.add_methodref('net/minecraft/world/level/Level', 'isClientSide', '()Z')
        max_hp_f = kl.add_fieldref('net/minecraft/world/entity/ai/attributes/Attributes', 'MAX_HEALTH', 'Lnet/minecraft/core/Holder;')
        get_attr_m = kl.add_methodref(
            CORPSE_NPC,
            'getAttribute',
            '(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;',
        )
        set_base_m = kl.add_methodref('net/minecraft/world/entity/ai/attributes/AttributeInstance', 'setBaseValue', '(D)V')
        is_coffin_m = kl.add_methodref(CORPSE_NPC, 'isCoffin', '()Z')
        set_hp_m = kl.add_methodref(CORPSE_NPC, 'setHealth', '(F)V')

        # 0: aload_0; invokevirtual level(); invokevirtual isClientSide(); ifeq -> 11; return
        # 11: aload_0; getstatic MAX_HEALTH; invokevirtual getAttribute; astore_1
        # 19: aload_1; ifnull -> 29; aload_1; bipush 60; i2d; invokevirtual setBaseValue(D)V
        # 29: aload_0; aload_0; invokevirtual isCoffin()Z; ifeq -> 43; bipush 60; i2f; goto -> 44; 43: fconst_1; 44: invokevirtual setHealth(F)V; return
        c = bytearray()
        c += b'\x2a' + prev.op(0xB6, level_m) + prev.op(0xB6, client_m)
        c += b'\x99\x00\x04\xb1'  # pc 7: ifeq +4 -> 11; pc 10: return
        assert len(c) == 11
        c += b'\x2a' + prev.op(0xB2, max_hp_f) + prev.op(0xB6, get_attr_m) + b'\x4c'
        assert len(c) == 19
        c += b'\x2b\xc6\x00\x0a'  # pc 20: ifnull +10 -> 30
        c += b'\x2b\x10\x3c\x87' + prev.op(0xB6, set_base_m)
        assert len(c) == 30
        c += b'\x2a\x2a' + prev.op(0xB6, is_coffin_m)
        c += b'\x99\x00\x09'      # pc 35: ifeq +9 -> 44
        c += b'\x10\x3c\x86'      # bipush 60; i2f
        c += b'\xa7\x00\x04'      # pc 41: goto +4 -> 45
        assert len(c) == 44
        c += b'\x0c'              # pc 44: fconst_1
        assert len(c) == 45
        c += prev.op(0xB6, set_hp_m) + b'\xb1'
        assert len(c) == 49
        return bytes(c)

    return prev.replace_code(raw, 'syncHealth', desc, build, 4, 2)


def patch_mixins_json(raw: bytes) -> bytes:
    cfg = json.loads(raw.decode('utf-8'))
    client = list(cfg.get('client', []))
    for mx in CLIENT_MIXINS_256:
        assert mx not in client, mx
        client.append(mx)
    cfg['client'] = client
    return (json.dumps(cfg, indent=2) + '\n').encode('utf-8')


def patch_entries(entries: dict[str, bytes], compiled: dict[str, bytes]) -> dict[str, bytes]:
    assert set(compiled) == ADDED and not ADDED.intersection(entries)
    updated = dict(entries)
    updated.update(compiled)
    updated[BBMODEL + '.class'] = patch_bbmodel(entries[BBMODEL + '.class'])
    updated[PLAYER_BODY + '.class'] = patch_player_body_renderer(entries[PLAYER_BODY + '.class'])
    updated[CORPSE_REC + '.class'] = patch_corpse_recovery(entries[CORPSE_REC + '.class'])
    updated['settledlands.mixins.json'] = patch_mixins_json(entries['settledlands.mixins.json'])

    toml_name = 'META-INF/neoforge.mods.toml'
    toml_txt = entries[toml_name].decode('utf-8')
    old_ver = f'version="{OLD_VERSION}"'
    marker = "description='''"
    assert toml_txt.count(old_ver) == 1 and toml_txt.count(marker) == 1
    toml_txt = toml_txt.replace(old_ver, f'version="{NEW_VERSION}"', 1).replace(
        marker, marker + CHANGELOG + '\n\n', 1
    )
    assert tomllib.loads(toml_txt)['mods'][0]['version'] == NEW_VERSION
    updated[toml_name] = toml_txt.encode('utf-8')

    updated['settledlands-patch-info.txt'] = (
        PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
    ).encode('utf-8')

    changed = {n for n in entries if entries[n] != updated[n]}
    assert changed == CHANGED, (changed ^ CHANGED, changed)
    assert set(updated) - set(entries) == ADDED
    return updated


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    ap.add_argument('--java', type=pathlib.Path, required=True)
    args = ap.parse_args()
    assert args.base.resolve() != args.out.resolve()
    assert hashlib.sha256(args.base.read_bytes()).hexdigest() == BASE_SHA, 'unexpected base JAR SHA-256'

    with tempfile.TemporaryDirectory(prefix='settledlands-256-compile-') as td:
        compiled = compile_sources(args.java.resolve(), pathlib.Path(td))
        with zipfile.ZipFile(args.base) as base:
            assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
            entries = {i.filename: base.read(i.filename) for i in base.infolist()}
            updated = patch_entries(entries, compiled)
            args.out.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(args.out, 'w') as out:
                for info in base.infolist():
                    out.writestr(info, updated[info.filename])
                for name in sorted(ADDED):
                    zi = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    zi.external_attr = 0o644 << 16
                    out.writestr(zi, updated[name])
    print('wrote', args.out, 'bytes=', args.out.stat().st_size, 'sha256=', hashlib.sha256(args.out.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
