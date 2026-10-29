#!/usr/bin/env python3
"""Release gate 256: full 255–237/HANDOFF §8 on exact bases, plus Build 256 NPC player-animation pipeline,
first-person possession (Inventory + arms + PAL/HnS sword), and corpse 1/60 HP verification.

Genuine Java 21 (-Xverify:all) exercises real NpcMotionBridge256, NpcFreshEmfAccess256, NpcMotionPlayerProxy256,
WalkAnimationStateMixin256, ClientLevelProxyLookupMixin256, PlayerBodyRenderer, PlayerFirstPersonBodyGate256,
FirstPersonPossession256, FirstPersonArmSubscriber256, PossessedFirstPersonHandMixin256,
PossessedFirstPersonModelMixin256, CorpseRecovery, GuideNpc/GuideTriggerNpc/GuideCorpseNpc, NpcAutoAi255,
NpcAutoAiEngine255, BbModel, BbModelParser, PAL 1.1.6, HnS beta2.5 data + BladeTracker, EMF Compat Core 2.3.2,
Touch'n Motion 1.0.4, and EMF 3.3.12 components with synthetic APIs/providers.
This is NOT a full Minecraft/NeoForge launch or visual test.
No external third-party mod JARs are distributed.
"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import tomllib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import gate_build237 as g237
import gate_build242 as g242
import gate_build245 as g245
import gate_build246 as g246
import gate_build247 as g247
import gate_build249 as g249
import gate_build250 as g250
import gate_build251 as g251
import gate_build252 as g252
import gate_build253 as g253
import gate_build254 as g254
import gate_build255 as g255
import patch_npc_fp_corpse256 as p

PY = sys.executable
SHA = '024937159ceb6f7173591dacf30d0c46b2fcc5233e8b495e4918d56b715c2021'
SIZE = 2254719
ALLOWED_HNS_SHAS = g255.ALLOWED_HNS_SHAS


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def static_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = {n: b.read(n) for n in b.namelist()}
        new = {n: z.read(n) for n in z.namelist()}
    assert set(new) - set(old) == p.ADDED and not (set(old) - set(new))
    assert {n for n in old if old[n] != new[n]} == p.CHANGED
    assert old['settledlands.compat.mixins.json'] == new['settledlands.compat.mixins.json']

    # Verify mixins JSON: client-only additions, common mixins untouched
    old_mx = json.loads(old['settledlands.mixins.json'].decode('utf-8'))
    new_mx = json.loads(new['settledlands.mixins.json'].decode('utf-8'))
    assert old_mx.get('mixins', []) == new_mx.get('mixins', [])
    assert new_mx.get('client', []) == list(old_mx.get('client', [])) + list(p.CLIENT_MIXINS_256)

    # All 255 Auto-AI, 254 UI/Ivy, 253 shield/copy, 252/251/250 player bridges, and NPC/corpse entity classes stay byte-identical
    for untouched in (
        'dev/settledlands/model/BbModelParser.class',
        'dev/settledlands/model/BbModelManager.class',
        'dev/settledlands/model/BbModelStorage.class',
        'dev/settledlands/model/FirstPersonSwordBridge252.class',
        'dev/settledlands/model/FirstPersonPalEmfAccess252.class',
        'dev/settledlands/model/PlayerMotionBridge.class',
        'dev/settledlands/model/HackSlashAccess.class',
        'dev/settledlands/model/FreshEmfAccess.class',
        'dev/settledlands/npc/PossessionSystem.class',
        'dev/settledlands/npc/PossessionLoginSync251.class',
        'dev/settledlands/npc/NpcCopyPickPayload.class',
        'dev/settledlands/npc/NpcCopyEggFix253.class',
        'dev/settledlands/npc/GuideNpc.class',
        'dev/settledlands/npc/GuideTriggerNpc.class',
        'dev/settledlands/npc/GuideCorpseNpc.class',
        'dev/settledlands/npc/IvyCombatAi.class',
        'dev/settledlands/npc/NpcAutoAi255.class',
        'dev/settledlands/npc/NpcAutoAiEngine255.class',
        'dev/settledlands/npc/NpcStatsGraph.class',
        'dev/settledlands/npc/NpcDefaultModel254.class',
        'dev/settledlands/npc/client/NpcAutoAiConfigUi255.class',
        'dev/settledlands/npc/client/NpcAutoAiGraphUi255.class',
        'dev/settledlands/npc/client/NpcStatsGraphUi.class',
        'dev/settledlands/npc/client/NpcDropdownTop254.class',
        'dev/settledlands/npc/client/NpcConfigScreen.class',
        'dev/settledlands/npc/client/NpcDialogueGraphScreen.class',
        'dev/settledlands/npc/client/NpcGraphCanvas.class',
        'dev/settledlands/npc/client/NpcBranchScreen.class',
        'dev/settledlands/npc/client/NpcPickCopy.class',
        'dev/settledlands/npc/client/GuideRenderer.class',
        'dev/settledlands/npc/client/NpcClient.class',
    ):
        assert old[untouched] == new[untouched], untouched

    # Exact byte-for-byte verification on all 3 patched classes
    assert new[p.BBMODEL + '.class'] == p.patch_bbmodel(old[p.BBMODEL + '.class'])
    assert new[p.PLAYER_BODY + '.class'] == p.patch_player_body_renderer(old[p.PLAYER_BODY + '.class'])
    assert new[p.CORPSE_REC + '.class'] == p.patch_corpse_recovery(old[p.CORPSE_REC + '.class'])

    # BbModel player-actor pipeline methods stay byte-for-byte untouched
    bb_old = classedit.Klass(old[p.BBMODEL + '.class'])
    bb_new = classedit.Klass(new[p.BBMODEL + '.class'])
    for m_name, m_desc in (
        ('renderActor', '(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'),
        ('settledlands$renderActorOriginal', '(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'),
        ('renderActorHeldItem', p.ITEM_DESC),
    ):
        mo = bb_old.find_method(m_name, m_desc)
        mn = bb_new.find_method(m_name, m_desc)
        assert mo and mn
        co = next(a['body'] for a in mo['attrs'] if a['name'] == 'Code')
        cn = next(a['body'] for a in mn['attrs'] if a['name'] == 'Code')
        assert co == cn, (m_name, m_desc)

    # Server-loaded CorpseRecovery must have ZERO client-package references
    cr_cls = classedit.Klass(new[p.CORPSE_REC + '.class'])
    for idx, entry in enumerate(cr_cls.cp):
        if entry and entry['tag'] == 7:
            cname = cr_cls.utf8(entry['a'])
            assert not cname.startswith(('net/minecraft/client/', 'dev/settledlands/npc/client/')), cname

    # PlayerBodyRenderer must remain compatible with minimal login-model verification stubs
    pbr_cls = classedit.Klass(new[p.PLAYER_BODY + '.class'])
    for idx, entry in enumerate(pbr_cls.cp):
        if entry and entry['tag'] == 7:
            cname = pbr_cls.utf8(entry['a'])
            assert cname not in {
                'net/neoforged/neoforge/client/event/RenderArmEvent',
                'net/minecraft/client/model/PlayerModel',
                'net/minecraft/client/model/geom/ModelPart',
            }, cname

    for name in p.ADDED | p.CHANGED:
        if name.endswith('.class'):
            expected_major = classedit.Klass(old[name]).major if name in old else 65
            assert classedit.Klass(new[name]).major == expected_major, (name, expected_major, classedit.Klass(new[name]).major)
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(p.PATCHINFO)
    print(
        'OK exact 256 diff: NPC FA+TnM bridge, FP Inventory gate + arm subscriber/mixins, server-safe CorpseRecovery 1/60 HP; all 255-250 bridges byte-identical',
        flush=True,
    )


def assets_semantics(base: pathlib.Path, jar: pathlib.Path) -> None:
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old_assets = {n: b.read(n) for n in b.namelist() if n.startswith('assets/')}
        new_assets = {n: z.read(n) for n in z.namelist() if n.startswith('assets/')}
    assert old_assets == new_assets
    assert digest(new_assets[g254.p.IVY_ASSET]) == g254.p.IVY_OLEANDER_SHA
    print('OK preserved all 255/254 assets byte-for-byte including ivy_oleander.bbmodel & locales', flush=True)


def main() -> None:
    q = argparse.ArgumentParser()
    for name in (
        'inputs', 'base245', 'base246', 'base247', 'base248', 'base249', 'base250',
        'base251', 'base252', 'base253', 'base254', 'base255',
        'java', 'jar', 'tnm', 'core', 'emf', 'etf', 'pal', 'hns', 'gson-source',
    ):
        q.add_argument('--' + name, required=True, type=pathlib.Path)
    q.add_argument(
        '--prevalidated-base-log',
        type=pathlib.Path,
        help='use separately completed 255 gate log; omitted when running full nested 255 gate directly',
    )
    a = q.parse_args()
    inputs = a.inputs.resolve()
    b245 = a.base245.resolve()
    b246 = a.base246.resolve()
    b247 = a.base247.resolve()
    b248 = a.base248.resolve()
    b249 = a.base249.resolve()
    b250 = a.base250.resolve()
    b251 = a.base251.resolve()
    b252 = a.base252.resolve()
    b253 = a.base253.resolve()
    b254 = a.base254.resolve()
    b255 = a.base255.resolve()
    java = a.java.resolve()
    jar = a.jar.resolve()
    tnm = a.tnm.resolve()
    core = a.core.resolve()
    emf = a.emf.resolve()
    etf = a.etf.resolve()
    pal = a.pal.resolve()
    hns = a.hns.resolve()
    gson = a.gson_source.resolve()

    had_cwd_settledlands = (pathlib.Path.cwd() / 'settledlands').exists()

    try:
        assert digest(b245.read_bytes()) == g247.g246.p.BASE_SHA
        assert digest(b246.read_bytes()) == g247.p.BASE_SHA
        assert digest(b247.read_bytes()) == g250.p.BASE_SHA
        assert digest(b248.read_bytes()) == g249.g248.SHA
        assert digest(b249.read_bytes()) == g249.SHA
        assert digest(b250.read_bytes()) == g250.SHA
        assert digest(b251.read_bytes()) == g251.SHA
        assert digest(b252.read_bytes()) == g252.SHA
        assert digest(b253.read_bytes()) == g253.SHA
        assert digest(b254.read_bytes()) == g254.SHA
        assert digest(b255.read_bytes()) == g255.SHA
        assert digest(core.read_bytes()) == g252.CORE_SHA
        hns_sha = digest(hns.read_bytes())
        assert hns_sha in ALLOWED_HNS_SHAS, f'unexpected HnS SHA-256: {hns_sha}'
        assert jar.stat().st_size == SIZE and digest(jar.read_bytes()) == SHA, 'wrong 256 candidate'
        ver = subprocess.run([str(java), '-version'], capture_output=True, text=True)
        assert ver.returncode == 0 and 'version "21.' in ver.stderr, ver.stderr
        print(f'build256 candidate size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}', flush=True)

        if a.prevalidated_base_log:
            log = a.prevalidated_base_log.read_text(encoding='utf-8')
            assert 'BUILD 255 GATE: PASS' in log and g255.SHA in log
            print('OK separately completed full 255 gate incl. 254–237 / HANDOFF §8:', a.prevalidated_base_log, flush=True)
        else:
            g237.run(
                'complete 255 gate incl. 254–237 / HANDOFF §8 on the exact 255 base',
                [
                    PY, ROOT / 'tests/gate_build255.py',
                    '--inputs', inputs, '--base245', b245, '--base246', b246, '--base247', b247,
                    '--base248', b248, '--base249', b249, '--base250', b250, '--base251', b251,
                    '--base252', b252, '--base253', b253, '--base254', b254,
                    '--java', java, '--jar', b255, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf,
                    '--pal', pal, '--hns', hns, '--gson-source', gson,
                ],
                contains=('BUILD 255 GATE: PASS',),
            )

        with tempfile.TemporaryDirectory(prefix='gate-build256-') as d:
            tmp = pathlib.Path(d)
            rebuilt = tmp / 'rebuild.jar'
            g237.run(
                '256 deterministic rebuild',
                [PY, ROOT / 'tools/patch_npc_fp_corpse256.py', '--base', b255, '--out', rebuilt, '--java', java],
                contains=(SHA,),
            )
            assert rebuilt.read_bytes() == jar.read_bytes()
            g237.run(
                '256 structural validator',
                [PY, ROOT / 'tools/validate_jar.py', jar, '--base', b255],
                contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',),
            )
            verify = [PY, ROOT / 'tools/verify_jar.py']
            opts = (
                ['--base', b255]
                + g237.option_entries('--expect-added', sorted(p.ADDED))
                + g237.option_entries('--expect-changed', sorted(p.CHANGED))
            )
            g237.run('256 CRC TOML locales mixins exact diff', verify + [jar] + opts, contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
            static_semantics(b255, jar)
            assets_semantics(b255, jar)

            bad = tmp / 'crc.jar'
            bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
            g237.run('256 broken CRC rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
            for label, missing in [
                ('language', 'assets/settledlands/lang/ru_ru.json'),
                ('mixin', 'settledlands.mixins.json'),
                ('npc-motion-bridge', p.NPC_BRIDGE + '.class'),
                ('fp-possession', p.FP_POSS + '.class'),
                ('ivy-asset', g254.p.IVY_ASSET),
            ]:
                bad = tmp / (label + '.jar')
                g237.alter_zip(jar, bad, remove={missing})
                g237.run(
                    '256 missing ' + label + ' rejected',
                    verify + [bad] + (opts if label in ('npc-motion-bridge', 'fp-possession', 'ivy-asset') else []),
                    success=False,
                    contains=('ПРОБЛЕМЫ',),
                )

            with zipfile.ZipFile(b255) as z:
                for label, cls_entry in [
                    ('unpatched-bbmodel', p.BBMODEL + '.class'),
                    ('unpatched-playerbody', p.PLAYER_BODY + '.class'),
                    ('unpatched-corpserecovery', p.CORPSE_REC + '.class'),
                ]:
                    bad = tmp / (label + '.jar')
                    g237.alter_zip(jar, bad, replace={cls_entry: z.read(cls_entry)})
                    try:
                        static_semantics(b255, bad)
                    except AssertionError:
                        print('OK ' + label + ' rejected', flush=True)
                    else:
                        raise AssertionError(label + ' not detected')

            # jvm_verify.py intentionally supports only reference-typed parameters; GUI/transform
            # methods containing primitives are still executed in the Xverify-backed integration fixture below.
            targets = [
                'dev/settledlands/model/NpcMotionPlayerProxy256#boundNpc',
                'dev/settledlands/model/NpcMotionPlayerProxy256#syncFrom(dev.settledlands.npc.GuideNpc)',
                'dev/settledlands/model/NpcFreshEmfAccess256#hasTnmHook',
                'dev/settledlands/npc/client/PlayerFirstPersonBodyGate256#shouldRenderBodyInFirstPerson(net.minecraft.client.Minecraft,net.minecraft.client.player.AbstractClientPlayer,net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
                'dev/settledlands/npc/client/FirstPersonPossession256#possessedModelName(net.minecraft.client.player.AbstractClientPlayer)',
                'dev/settledlands/npc/CorpseRecovery#syncHealth(dev.settledlands.npc.GuideCorpseNpc)',
                'dev/settledlands/npc/NpcAutoAi255#isAutoWander(dev.settledlands.npc.NpcProfile)',
                'dev/settledlands/npc/NpcAutoAiEngine255#isEligibleNpc(dev.settledlands.npc.GuideNpc)',
                'dev/settledlands/npc/NpcStatsGraph#ensure(dev.settledlands.npc.NpcDialogueGraph)',
                'dev/settledlands/npc/NpcDefaultModel254#initialTexture(dev.settledlands.npc.GuideNpc)',
                'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
                'dev/settledlands/npc/client/NpcClient#setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent)',
                'dev/settledlands/model/BbModel#getName',
                'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
                'dev/settledlands/npc/client/PlayerBodyRenderer#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
                'dev/settledlands/model/FirstPersonSwordBridge252#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
                'dev/settledlands/model/HackSlashAccess#prepare(net.minecraft.client.player.AbstractClientPlayer)',
                'dev/settledlands/npc/PossessionLoginSync251#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
            ]
            out = g237.run(
                '256 JVM verifies all 256/255/254/253/252/251 declarations',
                [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java],
                contains=('ИТОГ: OK',),
            )
            for target in targets:
                assert 'DECLARED ' + target.split('(')[0] in out, target

            g254.candidate_regressions_254(jar, inputs, java)
            g237.run(
                '256 preserves 251 relog appearance, payload and renderer',
                [PY, ROOT / 'tests/jvm_login_model251.py', '--java', java, '--base250', b250, '--jar', jar],
                contains=('PASS baseline 250 reproduces missing possessed model', 'PASS relog model, real payload codec/client handler/renderer'),
            )
            g237.run(
                '256 nine verifier-valid relog appearance mutants',
                [PY, ROOT / 'tests/mutants_login251.py', '--java', java, '--base250', b250, '--jar', jar],
                contains=('251 possessed model login behavioral mutants: PASS 9',),
            )
            g237.run(
                '256 preserves bodiless damage/milk',
                [PY, ROOT / 'tests/jvm_bodiless247.py', '--java', java, '--jar', jar],
                contains=('247 actual protection + 246 predicate', 'PASS'),
            )
            g237.run(
                '256 legacy bodiless mutants',
                [PY, ROOT / 'tests/mutants247.py', '--java', java, '--jar', jar],
                contains=('247 behavioral mutants: PASS 17',),
            )
            g237.run(
                '256 FA bridge regression and 1200 poses',
                [PY, ROOT / 'tests/jvm_fresh_motion250.py', '--java', java, '--jar', jar],
                contains=('51 scenarios + 1200 JOML poses PASS',),
            )
            g237.run(
                '256 inherited FA negative mutants',
                [PY, ROOT / 'tests/mutants_fresh251.py', '--java', java, '--jar', jar],
                contains=('250 FA behavioral mutants against 251: PASS 21',),
            )
            g237.run(
                '256 actual EMF root and TnM hook with controlled providers',
                [PY, ROOT / 'tests/jvm_real_emf_tnm250.py', '--java', java, '--jar', jar, '--tnm', tnm, '--emf', emf, '--etf', etf],
                contains=('PASS actual EMF 3.3.12 root and TnM hook',),
            )
            g237.run(
                '256 actual actor, crawl, PAL, nested, rollback and shield-tilt-free scenarios',
                [PY, ROOT / 'tests/jvm_motion253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal],
                contains=('CONFIRMED build 252 legacy shield tilt +40deg', 'PASS real BbModel/BbAnimation', '253 JVM motion & shield scenarios PASS'),
            )
            g237.run(
                '256 37 calibrated crawl/PAL negative mutants',
                [PY, ROOT / 'tests/mutants251.py', '--java', java, '--jar', jar, '--pal', pal],
                contains=('251 behavioral mutants: PASS 37',),
            )
            g237.run(
                '256 HnS JSON through real PAL controller and item mixin',
                [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson],
                contains=('PASS real HnS JSON 39 attacks / 195 sampled frames',),
            )
            g237.run(
                '256 four genuine HnS modifiers plus negative controls',
                [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_hns_modifiers251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson],
                contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync', '4 negative controls'),
            )
            g237.run(
                '256 combined 3rd-person PAL + real EMF root/TnM hook with HnS frames',
                [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns_tnm251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--emf', emf, '--etf', etf],
                contains=('PASS real HnS JSON 39 attacks / 195 sampled frames', 'actual EMF 3.3.12 root and TnM 1.0.4 hook'),
            )
            g237.run(
                '256 first-person HnS+PAL+EMF+Core+TnM + BladeTracker reach',
                [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_fp_sword252.py', '--java', java, '--jar', jar, '--base-jar', b251, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf],
                contains=('CONFIRMED build 251 bug: pureMinDist=0.0', 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames, frontHits=14/14, 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks'),
            )
            g237.run(
                '256 ten verifier-valid first-person HnS mutants',
                [PY, ROOT / 'tests/run_hns_compat254.py', 'mutants_fp252.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf],
                contains=('PASS mutants_fp252: 10/10 verifier-valid mutants killed',),
            )
            g237.run(
                '256 NPC copy pick-block, ENTITY_DATA id, ItemStack.save & spawn',
                [PY, ROOT / 'tests/jvm_npc_copy253.py', '--java', java, '--jar', jar, '--base-jar', b252],
                contains=('CONFIRMED build 252 NPC copy freeze/crash: Missing id for entity in DataComponents.ENTITY_DATA', 'PASS build 253 NPC copy: pick-block, codec, permissions, ENTITY_DATA id, ItemStack.save/player NBT save, independent GuideNpc+stats & GuideTriggerNpc spawn'),
            )
            g237.run(
                '256 eleven verifier-valid shield & NPC copy behavioral mutants',
                [PY, ROOT / 'tests/mutants253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal],
                contains=('PASS mutants253: 11/11 verifier-valid mutants killed',),
            )
            g237.run(
                '256 UI & Ivy Oleander end-to-end JVM verification (plus 253 bug reproduction)',
                [PY, ROOT / 'tests/jvm_ui_ivy254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson],
                contains=('CONFIRMED build 253 bugs: stats=(568,-240) right of arrival2, popupZ=0.0 behind previewZ=95.0, defaultTexture=\'\'', 'PASS build 254 UI & Ivy:'),
            )
            g237.run(
                '256 seventeen verifier-valid UI & Ivy behavioral mutants',
                [PY, ROOT / 'tests/mutants254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson],
                contains=('PASS mutants254: 17/17 verifier-valid mutants killed',),
            )
            g237.run(
                '256 Autonomous NPC AI end-to-end JVM verification (plus 254 baseline check)',
                [PY, ROOT / 'tests/jvm_auto_ai255.py', '--java', java, '--base-jar', b254, '--jar', jar, '--gson-source', gson],
                contains=('CONFIRMED build 254 baseline: no autonomous home-radius wander, no hostile-mob Ivy melee, no Auto-AI controls', 'PASS build 255 Auto-AI:'),
            )
            g237.run(
                '256 twenty-four verifier-valid Autonomous NPC AI behavioral mutants',
                [PY, ROOT / 'tests/mutants255.py', '--java', java, '--jar', jar, '--gson-source', gson],
                contains=('PASS mutants 255: 24/24 verifier-valid mutants killed',),
            )
            g237.run(
                '256 Corpse 1/60 HP & Coffin 60/60 HP JVM verification (plus 255 1/1 HP bug reproduction)',
                [PY, ROOT / 'tests/jvm_recovery256.py', '--java', java, '--base-jar', b255, '--jar', jar],
                contains=('CONFIRMED build 255 corpse HP bug: non-coffin corpse has 1.0/1.0 HP instead of 1.0/60.0 HP', 'PASS build 256 corpse 1/60 HP'),
            )
            g237.run(
                '256 NPC FA+TnM, First-Person Possession & Corpse 1/60 HP end-to-end JVM verification (plus 255 bug reproduction)',
                [PY, ROOT / 'tests/jvm_npc_fp_corpse256.py', '--java', java, '--base255-jar', b255, '--jar', jar, '--gson-source', gson],
                contains=(
                    'CONFIRMED build 255 bugs: npcFaCalls=0, npcHeldItems=0, fpInventoryCanceled=false, corpseMaxHp=1.0',
                    'PASS build 256 end-to-end: NPC FA+TnM+items+combos+corpse-guard+fallback, FP inventory+arms+PAL sword, corpse 1/60 HP',
                ),
            )
            g237.run(
                '256 twenty-four verifier-valid NPC FA+TnM, First-Person & Corpse HP behavioral mutants',
                [PY, ROOT / 'tests/mutants256.py', '--java', java, '--jar', jar, '--gson-source', gson],
                contains=('PASS mutants 256: 24/24 verifier-valid mutants killed',),
            )

        print(
            'BUILD 256 GATE: PASS (GuideNpc FA+TnM+held-items+combos+corpse-guard+fallback + possessed 1st-person Inventory/GUI/bed + 1st-person NPC arms & PAL/HnS sword + corpse 1/60 HP + 255-237 regressions; actual game/NeoForge/visual NOT run)',
            flush=True,
        )
    finally:
        if not had_cwd_settledlands:
            shutil.rmtree(pathlib.Path.cwd() / 'settledlands', ignore_errors=True)


if __name__ == '__main__':
    main()
