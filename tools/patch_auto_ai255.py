#!/usr/bin/env python3
"""Candidate 255 from the exact released 254; no third-party JARs are bundled."""
import argparse, hashlib, json, pathlib, struct, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import classedit, decode, gen_api255, patch_corpse_click244 as v244, patch_npc_stats246 as prev
from patch_corpse_pose237 import assert_pool_roundtrip, constant_ref, find_invoke

BASE_SHA = '19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c'
OLD_VERSION = '0.8.0-build254-ui-ivy'
NEW_VERSION = '0.8.0-build255-auto-ai'

PROFILE = 'dev/settledlands/npc/NpcProfile'
GRAPH = 'dev/settledlands/npc/NpcDialogueGraph'
EDIT = 'dev/settledlands/npc/NpcEdit'
GUIDE = 'dev/settledlands/npc/GuideNpc'
CFG_SCREEN = 'dev/settledlands/npc/client/NpcConfigScreen'
SCREEN = 'dev/settledlands/npc/client/NpcDialogueGraphScreen'
CANVAS = 'dev/settledlands/npc/client/NpcGraphCanvas'
UI246 = 'dev/settledlands/npc/client/NpcStatsGraphUi'

AUTO_AI = 'dev/settledlands/npc/NpcAutoAi255'
ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
CFG_UI = 'dev/settledlands/npc/client/NpcAutoAiConfigUi255'
GRAPH_UI = 'dev/settledlands/npc/client/NpcAutoAiGraphUi255'

TAG = 'net/minecraft/nbt/CompoundTag'
PROVIDER = 'net/minecraft/core/HolderLookup$Provider'
DMG_SRC = 'net/minecraft/world/damagesource/DamageSource'
SP = 'net/minecraft/server/level/ServerPlayer'
GUI = 'net/minecraft/client/gui/GuiGraphics'
FONT = 'net/minecraft/client/gui/Font'
LISTENER = 'net/minecraft/client/gui/components/events/GuiEventListener'

ADDED = {
    AUTO_AI + '.class',
    AUTO_AI + '$ProfileState.class',
    AUTO_AI + '$GraphState.class',
    ENGINE + '.class',
    ENGINE + '$MeleeCombo.class',
    CFG_UI + '.class',
    GRAPH_UI + '.class',
    GRAPH_UI + '$DragState.class',
}

CHANGED = {
    PROFILE + '.class',
    GRAPH + '.class',
    EDIT + '.class',
    GUIDE + '.class',
    CFG_SCREEN + '.class',
    SCREEN + '.class',
    CANVAS + '.class',
    'assets/settledlands/lang/ru_ru.json',
    'assets/settledlands/lang/en_us.json',
    'META-INF/neoforge.mods.toml',
    'settledlands-patch-info.txt',
}

CHANGELOG = (
    'Build 255: add autonomous NPC wandering within a configurable home radius (with post-chase return) '
    'and automatic hostile-mob engagement using Ivy Oleander melee combos and dodges (no fireballs), '
    'scaled by Characteristics Damage and Speed; expose two-way synchronized toggles and 1..64 radius '
    'controls in both NpcConfigScreen and NpcDialogueGraphScreen. '
    'All 243-254 mechanics, UI fixes, and animations remain intact.'
)
PATCHINFO = (
    'сборка 255: добавлена свободная ходьба НПС в радиусе от домашней точки с возвратом и автоматическая '
    'атака враждебных мобов в радиусе рукопашными комбо и уворотами Иви (без фаерболов) с учётом характеристик '
    'Урон и Скорость; добавлена двусторонне синхронизированная настройка в окне настроек НПС и в редакторе графа. '
    'Механики, UI и анимации 243–254 сохранены.'
)

LANG = {
    'ru_ru': {
        'gui.settledlands.block.auto_ai': 'Автономный ИИ',
        'gui.settledlands.field.auto_wander': 'Свободная ходьба у точки',
        'gui.settledlands.field.auto_attack': 'Атака враждебных мобов',
        'gui.settledlands.field.wander_radius': 'Радиус прогулки',
        'gui.settledlands.field.aggro_radius': 'Радиус атаки врагов',
        'gui.settledlands.hint.blocks': '%s бл.',
        'gui.settledlands.auto_ai.title': 'Автономный ИИ',
        'gui.settledlands.auto_ai.wander': 'Свободная ходьба',
        'gui.settledlands.auto_ai.wander_radius': 'Радиус прогулки',
        'gui.settledlands.auto_ai.attack': 'Атака врагов',
        'gui.settledlands.auto_ai.aggro_radius': 'Радиус атаки',
        'gui.settledlands.auto_ai.on': 'Вкл',
        'gui.settledlands.auto_ai.off': 'Выкл',
        'gui.settledlands.auto_ai.hint': 'Комбо Иви без фаерболов · возврат к дому',
    },
    'en_us': {
        'gui.settledlands.block.auto_ai': 'Autonomous AI',
        'gui.settledlands.field.auto_wander': 'Free wandering near home',
        'gui.settledlands.field.auto_attack': 'Attack hostile mobs',
        'gui.settledlands.field.wander_radius': 'Wander radius',
        'gui.settledlands.field.aggro_radius': 'Hostile aggro radius',
        'gui.settledlands.hint.blocks': '%s blocks',
        'gui.settledlands.auto_ai.title': 'Autonomous AI',
        'gui.settledlands.auto_ai.wander': 'Free wander',
        'gui.settledlands.auto_ai.wander_radius': 'Wander radius',
        'gui.settledlands.auto_ai.attack': 'Attack hostiles',
        'gui.settledlands.auto_ai.aggro_radius': 'Aggro radius',
        'gui.settledlands.auto_ai.on': 'On',
        'gui.settledlands.auto_ai.off': 'Off',
        'gui.settledlands.auto_ai.hint': 'Ivy melee combos (no fireballs) · returns home',
    },
}


def compile_sources(java: pathlib.Path, work: pathlib.Path):
    api = work / 'api'
    out = work / 'classes'
    gen_api255.main(api)
    out.mkdir()
    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(compiler.read_bytes()).hexdigest() == prev.ECJ_SHA
    sources = sorted(api.rglob('*.java')) + sorted((ROOT / 'src/build255').rglob('*.java'))
    subprocess.run(
        [str(java), '-jar', str(compiler), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
         '-d', str(out), *map(str, sources)],
        check=True,
    )
    produced = {
        p.relative_to(out).as_posix()
        for p in out.rglob('*.class')
        if '255' in p.name
    }
    assert produced == ADDED, produced
    return {name: (out / name).read_bytes() for name in ADDED}


def _last_op(expected_byte: int):
    def select(k, code):
        walk = list(decode.walk(code))
        assert walk and walk[-1][1] == bytes([expected_byte]), walk[-2:]
        return walk[-1][0]
    return select


def patch_profile(raw: bytes) -> bytes:
    raw = prev.insert_before(
        raw, '<init>', '()V', _last_op(0xB1),
        lambda k: prev.static_call(k, AUTO_AI, 'initProfile', f'(L{PROFILE};)V', b'\x2a'),
        stack=8,
    )
    raw = prev.insert_before(
        raw, 'copy', f'()L{PROFILE};', _last_op(0xB0),
        lambda k: prev.static_call(k, AUTO_AI, 'copyProfile', f'(L{PROFILE};L{PROFILE};)V', b'\x2a\x2b'),
        stack=6,
    )
    raw = prev.insert_before(
        raw, 'sanitized', f'()L{PROFILE};', _last_op(0xB0),
        lambda k: prev.static_call(k, AUTO_AI, 'sanitizeProfile', f'(L{PROFILE};L{PROFILE};)V', b'\x2a\x2b'),
        stack=8,
    )

    def before_save_ret(k, code):
        assert code[-2:] == b'\x2c\xb0', code[-4:]
        return len(code) - 2

    raw = prev.insert_before(
        raw, 'save', f'(L{PROVIDER};)L{TAG};', before_save_ret,
        lambda k: prev.static_call(k, AUTO_AI, 'saveProfile', f'(L{PROFILE};L{TAG};)V', b'\x2a\x2c'),
        stack=5,
    )

    def before_load_sanitized(k, code):
        pc, sz = find_invoke(k, code, PROFILE, 'sanitized', f'()L{PROFILE};')
        assert sz == 3 and pc == len(code) - 4 and code[pc - 1] == 0x2C, (pc, len(code))
        return pc

    return prev.insert_before(
        raw, 'load', f'(L{TAG};L{PROVIDER};)L{PROFILE};', before_load_sanitized,
        lambda k: prev.static_call(k, AUTO_AI, 'loadProfile', f'(L{TAG};L{PROFILE};)V', b'\x2a\x2c'),
        stack=7,
    )


def patch_graph(raw: bytes) -> bytes:
    raw = prev.insert_before(
        raw, '<init>', '()V', _last_op(0xB1),
        lambda k: prev.static_call(k, AUTO_AI, 'initGraph', f'(L{GRAPH};)V', b'\x2a'),
        stack=4,
    )
    raw = prev.insert_before(
        raw, 'copy', f'()L{GRAPH};', _last_op(0xB0),
        lambda k: prev.static_call(k, AUTO_AI, 'copyGraph', f'(L{GRAPH};L{GRAPH};)V', b'\x2a\x2b'),
        stack=5,
    )

    def before_save_ret(k, code):
        assert code[-2:] == b'\x2b\xb0', code[-4:]
        return len(code) - 2

    raw = prev.insert_before(
        raw, 'save', f'()L{TAG};', before_save_ret,
        lambda k: prev.static_call(k, AUTO_AI, 'saveGraph', f'(L{GRAPH};L{TAG};)V', b'\x2a\x2b'),
        stack=6,
    )
    return prev.insert_before(
        raw, 'load', f'(L{TAG};)L{GRAPH};', _last_op(0xB0),
        lambda k: prev.static_call(k, AUTO_AI, 'loadGraph', f'(L{GRAPH};L{TAG};)L{GRAPH};', b'\x2a'),
        stack=9,
    )


def patch_edit(raw: bytes) -> bytes:
    desc = f'(L{PROFILE};Ljava/lang/String;Ljava/lang/String;)V'
    alias = 'settledlands$fieldOriginal'
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    m = k.find_method('field', desc)
    assert m is not None and k.find_method(alias, desc) is None
    old_body = next(x['body'] for x in m['attrs'] if x['name'] == 'Code')
    wrapper = (
        b'\x2a\x2b\x2c'
        + prev.op(0xB8, k.add_methodref(EDIT, alias, desc))
        + b'\x2a\x2b\x2c'
        + prev.op(0xB8, k.add_methodref(AUTO_AI, 'applyFieldEdit', f'(L{PROFILE};Ljava/lang/String;Ljava/lang/String;)Z'))
        + b'\x57\xb1'
    )
    raw = k.dump(m, v244.code_body(3, 3, wrapper))
    k = classedit.Klass(raw)
    m = k.find_method('field', desc)
    alt = dict(m)
    alt['acc'] = 0x0009
    alt['name_idx'] = k.add_utf8(alias)
    alt['name'] = alias
    alt['attrs'] = [{'name_idx': k.add_utf8('Code'), 'name': 'Code', 'body': old_body}]
    return v244.rebuild(k, k.methods + [alt])


def patch_guide_npc(raw: bytes) -> bytes:
    # 1. Add bridges settledlands$hasActiveMobilityRoute and settledlands$tickMobilityOriginal
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    route_desc = f'()L{GUIDE}$MobilityRoute;'
    has_route_body = (
        b'\x2a'
        + prev.op(0xB7, k.add_methodref(GUIDE, 'currentMobilityRoute', route_desc))
        + prev.op(0xB8, k.add_methodref('java/util/Objects', 'nonNull', '(Ljava/lang/Object;)Z'))
        + b'\xac'
    )
    tick_mob_body = (
        b'\x2a'
        + prev.op(0xB7, k.add_methodref(GUIDE, 'tickMobility', '()V'))
        + b'\xb1'
    )
    extra = [
        prev.new_method(k, 'settledlands$hasActiveMobilityRoute', '()Z', has_route_body, 1, 1),
        prev.new_method(k, 'settledlands$tickMobilityOriginal', '()V', tick_mob_body, 1, 1),
    ]
    raw = v244.rebuild(k, k.methods + extra)

    # 2. Save & load home anchor inside "Guide" CompoundTag
    def before_put_guide(k2, code):
        for pc, ins in decode.walk(code):
            if ins[0] == 0x13:
                idx = struct.unpack('>H', ins[1:3])[0]
                if k2.cp[idx]['tag'] == 8 and k2.utf8(k2.cp[idx]['a']) == 'Guide':
                    assert code[pc - 1] == 0x2B and code[pc + 3] == 0x2C, pc
                    return pc
        raise AssertionError('ldc_w "Guide" not found in GuideNpc.addAdditionalSaveData')

    raw = prev.insert_before(
        raw, 'addAdditionalSaveData', f'(L{TAG};)V', before_put_guide,
        lambda k2: prev.static_call(k2, ENGINE, 'saveNpcData', f'(L{GUIDE};L{TAG};)V', b'\x2a\x2c'),
        stack=5,
    )
    raw = prev.insert_before(
        raw, 'readAdditionalSaveData', f'(L{TAG};)V',
        prev.after_call('net/minecraft/world/entity/PathfinderMob', 'readAdditionalSaveData', f'(L{TAG};)V'),
        lambda k2: prev.static_call(k2, ENGINE, 'loadNpcData', f'(L{GUIDE};L{TAG};)V', b'\x2a\x2b'),
        stack=5,
    )

    # 3. Retaliate / dodge on hurt when autoAttackHostiles is enabled
    def on_hurt_inside_true(k2, code):
        pc, sz = find_invoke(k2, code, GUIDE, 'aiMode', '()I')
        assert code[pc - 1] == 0x2A, pc
        return pc - 1

    raw = prev.insert_before(
        raw, 'hurt', f'(L{DMG_SRC};F)Z', on_hurt_inside_true,
        lambda k2: prev.static_call(k2, ENGINE, 'onHurt', f'(L{GUIDE};L{DMG_SRC};)V', b'\x2a\x2b'),
        stack=4,
    )

    # 4. Sync Auto-AI settings from saved main graph into profile on GraphOnly save
    def before_apply_special(k2, code):
        pc, _ = find_invoke(k2, code, GRAPH, 'applySpecialLines', f'(L{PROFILE};)V')
        assert code[pc - 4:pc] == b'\x19\x05\x19\x06', code[pc - 4:pc]
        return pc - 4

    raw = prev.insert_before(
        raw, 'applyConfig', f'(L{SP};IL{TAG};)V', before_apply_special,
        lambda k2: prev.static_call(
            k2, AUTO_AI, 'syncGraphSaveToProfile', f'(L{GRAPH};L{PROFILE};)V', b'\x19\x05\x19\x06'
        ),
        stack=6,
    )

    # 5. customServerAiStep: swap tickMobility() -> runMobilityAndAutoAi(this), plus escort/customMove/talking hooks
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    m = k.find_method('customServerAiStep', '()V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = bytearray(parts['code'])
    mob_pc, mob_sz = find_invoke(k, code, GUIDE, 'tickMobility', '()V')
    assert mob_sz == 3 and code[mob_pc] == 0xB6 and mob_pc not in decode.targets(bytes(code)), mob_pc
    code[mob_pc:mob_pc + 3] = prev.op(0xB8, k.add_methodref(ENGINE, 'runMobilityAndAutoAi', f'(L{GUIDE};)V'))
    parts['code'] = bytes(code)
    raw = k.dump(m, classedit.build_code_body(k, parts))

    def before_escort_tick(k2, code2):
        pc, _ = find_invoke(k2, code2, 'dev/settledlands/npc/NpcEscort', 'tick', f'(L{GUIDE};)V')
        assert code2[pc - 5] == 0x2A and code2[pc - 4] == 0xB4 and code2[pc - 1] == 0x2A, pc
        return pc - 5

    def before_custom_move(k2, code2):
        pc, _ = find_invoke(k2, code2, GUIDE, 'tickCustomMovement', '()V')
        assert code2[pc - 1] == 0x2A, pc
        return pc - 1

    def before_talking_pause(k2, code2):
        for pc, ins in decode.walk(code2):
            if ins[0] == 0xB5 and constant_ref(k2, struct.unpack('>H', ins[1:3])[0]) == (
                GUIDE, 'mobilityPaused', 'Z'
            ):
                assert code2[pc - 2:pc] == b'\x2a\x04', code2[pc - 2:pc]
                return pc - 2
        raise AssertionError('putfield mobilityPaused not found')

    raw = prev.insert_before(
        raw, 'customServerAiStep', '()V', before_talking_pause,
        lambda k2: prev.static_call(k2, ENGINE, 'onTalkingPaused', f'(L{GUIDE};)V', b'\x2a'),
        stack=7,
    )
    raw = prev.insert_before(
        raw, 'customServerAiStep', '()V', before_custom_move,
        lambda k2: prev.static_call(k2, ENGINE, 'onScriptedMoveActive', f'(L{GUIDE};)V', b'\x2a'),
        stack=7,
    )
    return prev.insert_before(
        raw, 'customServerAiStep', '()V', before_escort_tick,
        lambda k2: prev.static_call(k2, ENGINE, 'onScriptedMoveActive', f'(L{GUIDE};)V', b'\x2a'),
        stack=7,
    )


def patch_cfg_screen(raw: bytes) -> bytes:
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    methods = []

    def add(name, desc, body, stack, locals_):
        methods.append(prev.new_method(k, name, desc, body, stack, locals_))

    def fld(name, desc):
        return prev.op(0xB4, k.add_fieldref(CFG_SCREEN, name, desc))

    def priv(name, desc):
        return prev.op(0xB7, k.add_methodref(CFG_SCREEN, name, desc))

    def virt(name, desc):
        return prev.op(0xB6, k.add_methodref(CFG_SCREEN, name, desc))

    add('settledlands$profile', f'()L{PROFILE};', b'\x2a' + fld('profile', f'L{PROFILE};') + b'\xb0', 1, 1)
    add('settledlands$font', f'()L{FONT};', b'\x2a' + fld('font', f'L{FONT};') + b'\xb0', 1, 1)
    add('settledlands$left', '()I', b'\x2a' + priv('left', '()I') + b'\xac', 1, 1)
    add('settledlands$contentRight', '()I', b'\x2a' + priv('contentRight', '()I') + b'\xac', 1, 1)
    add('settledlands$fieldX', '()I', b'\x2a' + priv('fieldX', '()I') + b'\xac', 1, 1)
    add('settledlands$fieldW', '()I', b'\x2a' + priv('fieldW', '()I') + b'\xac', 1, 1)
    add('settledlands$rowH', '()I', b'\x2a' + priv('rowH', '()I') + b'\xac', 1, 1)
    add('settledlands$rowLabel', '(Ljava/lang/String;I)I', b'\x2a\x2b\x1c' + priv('rowLabel', '(Ljava/lang/String;I)I') + b'\xac', 3, 3)
    add('settledlands$blockStart', '(Ljava/lang/String;I)I', b'\x2a\x2b\x1c' + priv('blockStart', '(Ljava/lang/String;I)I') + b'\xac', 3, 3)
    add('settledlands$contentTop', '(I)I', b'\x2a\x1b' + priv('contentTop', '(I)I') + b'\xac', 2, 2)
    add('settledlands$blockEnd', '(II)I', b'\x2a\x1b\x1c' + priv('blockEnd', '(II)I') + b'\xac', 3, 3)
    add('settledlands$addWidget', f'(L{LISTENER};)L{LISTENER};', b'\x2a\x2b' + virt('addRenderableWidget', f'(L{LISTENER};)L{LISTENER};') + b'\xb0', 2, 2)
    add('settledlands$field', '(Ljava/lang/String;I)V', b'\x2a\x2b\x1c' + priv('field', '(Ljava/lang/String;I)V') + b'\xb1', 3, 3)

    raw = v244.rebuild(k, k.methods + methods)

    def before_mobility_section(k2, code2):
        # 0: aload_0; 1: invokevirtual bodyTop; 4: aload_0; 5: getfield scrollY; 8: isub; 9: istore_1; 10: aload_0
        assert code2[8:11] == b'\x64\x3c\x2a', code2[:12]
        return 10

    raw = prev.insert_before(
        raw, 'initMobility', '()V', before_mobility_section,
        lambda k2: prev.static_call(k2, CFG_UI, 'appendSection', f'(L{CFG_SCREEN};I)I', b'\x2a\x1b') + b'\x3c',
        stack=10,
    )

    def before_send_field(k2, code2):
        for pc, ins in decode.walk(code2):
            if ins[0] == 0x13:
                idx = struct.unpack('>H', ins[1:3])[0]
                if k2.cp[idx]['tag'] == 8 and k2.utf8(k2.cp[idx]['a']) == 'field':
                    assert code2[pc - 1] == 0x2A, pc
                    return pc
        raise AssertionError('ldc_w "field" not found in NpcConfigScreen.field')

    return prev.insert_before(
        raw, 'field', '(Ljava/lang/String;Ljava/lang/String;)V', before_send_field,
        lambda k2: prev.static_call(
            k2, CFG_UI, 'onLocalFieldEdit', f'(L{CFG_SCREEN};Ljava/lang/String;Ljava/lang/String;)V', b'\x2a\x2b\x2c'
        ),
        stack=8,
    )


def patch_canvas(raw: bytes) -> bytes:
    return prev.replace_code(
        raw, 'frameAll', f'(L{GRAPH};)V',
        lambda k: prev.static_call(k, GRAPH_UI, 'frameAll', f'(L{CANVAS};L{GRAPH};)V', b'\x2a\x2b') + b'\xb1',
        2, 2,
    )


def patch_graph_screen(raw: bytes) -> bytes:
    raw = prev.replace_code(
        raw, 'mouseClicked', '(DDI)Z',
        lambda k2: prev.static_call(k2, GRAPH_UI, 'mouseClicked', f'(L{SCREEN};DDI)Z', b'\x2a\x27\x29\x15\x05') + b'\xac',
        6, 6,
    )
    raw = prev.forward_alias(
        raw, 'mouseDragged', '(DDIDD)Z', 'settledlands$mouseDraggedOriginal',
        (GRAPH_UI, 'handleMouseDragged'), f'(L{SCREEN};DDIDD)Z',
        b'\x2a\x27\x29\x15\x05\x18\x06\x18\x08', 0xAC, 10, 10,
    )
    raw = prev.forward_alias(
        raw, 'mouseReleased', '(DDI)Z', 'settledlands$mouseReleasedOriginal',
        (GRAPH_UI, 'handleMouseReleased'), f'(L{SCREEN};DDI)Z',
        b'\x2a\x27\x29\x15\x05', 0xAC, 6, 6,
    )

    ctor = f'(IL{PROFILE};Lnet/minecraft/client/gui/screens/Screen;I)V'
    raw = prev.insert_before(
        raw, '<init>', ctor,
        prev.after_call(UI246, 'ensure', f'(L{SCREEN};)V'),
        lambda k2: (
            b'\x2a\x2a'
            + prev.op(0xB4, k2.add_fieldref(SCREEN, 'profile', f'L{PROFILE};'))
            + prev.op(0xB8, k2.add_methodref(GRAPH_UI, 'ensure', f'(L{SCREEN};L{PROFILE};)V'))
        ),
        stack=4,
    )

    def before_disable_scissor(k2, code2):
        pc, _ = find_invoke(k2, code2, GUI, 'disableScissor', '()V')
        return pc

    raw = prev.insert_before(
        raw, 'renderBackground', f'(L{GUI};IIF)V', before_disable_scissor,
        lambda k2: prev.static_call(k2, GRAPH_UI, 'draw', f'(L{SCREEN};L{GUI};)V', b'\x2a\x2b'),
        stack=7,
    )

    raw = prev.insert_before(
        raw, 'history', '(Z)V', _last_op(0xB1),
        lambda k2: prev.static_call(k2, GRAPH_UI, 'onHistoryApplied', f'(L{SCREEN};)V', b'\x2a'),
        stack=5,
    )

    def before_snapshot_widgets(k2, code2):
        pc, _ = find_invoke(k2, code2, SCREEN, 'widgets', '()V')
        assert code2[pc - 1] == 0x2A, pc
        return pc - 1

    return prev.insert_before(
        raw, 'applySnapshot', f'(L{PROFILE};)V', before_snapshot_widgets,
        lambda k2: (
            b'\x2a\x2a'
            + prev.op(0xB4, k2.add_fieldref(SCREEN, 'graphs', f'[L{GRAPH};'))
            + b'\x03\x32\x2a'
            + prev.op(0xB4, k2.add_fieldref(SCREEN, 'profile', f'L{PROFILE};'))
            + prev.op(0xB8, k2.add_methodref(GRAPH_UI, 'onSnapshotApplied', f'(L{SCREEN};L{GRAPH};L{PROFILE};)V'))
        ),
        stack=5,
    )


def patch_entries(entries, compiled):
    assert set(compiled) == ADDED and not ADDED.intersection(entries)
    updated = dict(entries)
    updated.update(compiled)

    updated[PROFILE + '.class'] = patch_profile(entries[PROFILE + '.class'])
    updated[GRAPH + '.class'] = patch_graph(entries[GRAPH + '.class'])
    updated[EDIT + '.class'] = patch_edit(entries[EDIT + '.class'])
    updated[GUIDE + '.class'] = patch_guide_npc(entries[GUIDE + '.class'])
    updated[CFG_SCREEN + '.class'] = patch_cfg_screen(entries[CFG_SCREEN + '.class'])
    updated[CANVAS + '.class'] = patch_canvas(entries[CANVAS + '.class'])
    updated[SCREEN + '.class'] = patch_graph_screen(entries[SCREEN + '.class'])

    for locale, extra in LANG.items():
        name = f'assets/settledlands/lang/{locale}.json'
        doc = json.loads(entries[name])
        assert not (set(doc) & set(extra)), set(doc) & set(extra)
        doc.update(extra)
        updated[name] = (json.dumps(doc, indent=2, ensure_ascii=False) + '\n').encode('utf-8')

    name = 'META-INF/neoforge.mods.toml'
    text = entries[name].decode('utf-8')
    old = f'version="{OLD_VERSION}"'
    marker = "description='''"
    assert text.count(old) == 1 and text.count(marker) == 1
    text = text.replace(old, f'version="{NEW_VERSION}"', 1).replace(
        marker, marker + CHANGELOG + '\n\n', 1
    )
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
    args = ap.parse_args()
    assert args.base.resolve() != args.out.resolve()
    assert hashlib.sha256(args.base.read_bytes()).hexdigest() == BASE_SHA

    with tempfile.TemporaryDirectory(prefix='settledlands-255-compile-') as td:
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
                    info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    out.writestr(info, updated[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes()
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
