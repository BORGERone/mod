#!/usr/bin/env python3
"""Release gate 254: full 253–237/HANDOFF §8 on exact bases, plus all 5 Build 254 UI & Ivy Oleander regressions.

Genuine Java 21 (-Xverify:all) exercises real NpcStatsGraph, NpcStatsGraphUi, NpcConfigScreen, NpcBranchScreen,
NpcUi$NpcDropdown, NpcDropdownTop254, NpcDefaultModel254, NpcProfile, GuideNpc/GuideTriggerNpc/GuideCorpseNpc,
NpcClient, BbModelStorage, BbModelParser, BbModel, NpcCopyPickPayload, PAL 1.1.6, HnS beta2.5 data + BladeTracker,
EMF Compat Core 2.3.2, Touch'n Motion 1.0.4, and EMF 3.3.12 components with synthetic APIs/providers.
This is NOT a full Minecraft/NeoForge launch or visual test.
No external third-party mod JARs are distributed.
"""
import argparse, base64, hashlib, json, pathlib, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit, gate_build237 as g237, gate_build242 as g242, gate_build245 as g245, gate_build246 as g246, gate_build247 as g247, gate_build249 as g249, gate_build250 as g250, gate_build251 as g251, gate_build252 as g252, gate_build253 as g253, patch_ui_ivy254 as p
from patch_corpse_pose237 import constant_ref

PY = sys.executable
SHA = '19634ba583fd6f196973416d9420b0b20b58a4508dd5c65a7a061094cdd11f5c'
SIZE = 2181852
ALLOWED_HNS_SHAS = {
    '0e1e54fc03adcd59bd1d46f821d42788bc8d9757cd8f41ed118cfc30c241a7bb',
    'f759036b3fd8e4572789d4b93911d9bfd58bf797d70e61a228b81a153a1f5aba',
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def static_semantics(base: pathlib.Path, jar: pathlib.Path):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = {n: b.read(n) for n in b.namelist()}
        new = {n: z.read(n) for n in z.namelist()}
    assert set(new) - set(old) == p.ADDED and not (set(old) - set(new))
    assert {n for n in old if old[n] != new[n]} == p.CHANGED
    assert old['settledlands.mixins.json'] == new['settledlands.mixins.json']
    assert old['settledlands.compat.mixins.json'] == new['settledlands.compat.mixins.json']

    # All player animation, HnS, FA, TnM, corpse, possession, and 253 shield/copy classes remain byte-identical
    for untouched in (
        'dev/settledlands/model/BbModel.class',
        'dev/settledlands/model/BbModelParser.class',
        'dev/settledlands/model/BbModelManager.class',
        'dev/settledlands/model/BbModelStorage.class',
        'dev/settledlands/npc/client/PlayerBodyRenderer.class',
        'dev/settledlands/model/FirstPersonSwordBridge252.class',
        'dev/settledlands/model/FirstPersonPalEmfAccess252.class',
        'dev/settledlands/model/PlayerMotionBridge.class',
        'dev/settledlands/model/HackSlashAccess.class',
        'dev/settledlands/model/FreshEmfAccess.class',
        'dev/settledlands/npc/PossessionSystem.class',
        'dev/settledlands/npc/PossessionLoginSync251.class',
        'dev/settledlands/npc/NpcCopyPickPayload.class',
        'dev/settledlands/npc/NpcCopyEggFix253.class',
        'dev/settledlands/npc/GuideTriggerNpc.class',
        'dev/settledlands/npc/GuideCorpseNpc.class',
        'dev/settledlands/npc/client/NpcPickCopy.class',
        'dev/settledlands/npc/client/GuideRenderer.class',
        'dev/settledlands/npc/client/NpcDialogueGraphScreen.class',
        'dev/settledlands/npc/client/NpcGraphCanvas.class',
    ):
        assert old[untouched] == new[untouched], untouched

    # Exact method-level patch verification on existing classes
    assert new[p.PROFILE + '.class'] == p.patch_profile(old[p.PROFILE + '.class'])
    g242.compare_methods(
        old[p.PROFILE + '.class'], new[p.PROFILE + '.class'],
        changed={
            ('<init>', '()V'),
            ('load', '(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)L' + p.PROFILE + ';'),
        },
    )

    assert new[p.GUIDE + '.class'] == p.patch_guide_npc(old[p.GUIDE + '.class'])
    g242.compare_methods(
        old[p.GUIDE + '.class'], new[p.GUIDE + '.class'],
        changed={
            ('defineSynchedData', '(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V'),
            ('defaultProfile', '()L' + p.PROFILE + ';'),
            ('readAdditionalSaveData', '(Lnet/minecraft/nbt/CompoundTag;)V'),
        },
    )

    assert new[p.CLIENT + '.class'] == p.patch_npc_client(old[p.CLIENT + '.class'])
    g242.compare_methods(
        old[p.CLIENT + '.class'], new[p.CLIENT + '.class'],
        changed={('lambda$setup$1', '()V')},
    )

    assert new[p.CFG_SCREEN + '.class'] == p.patch_dropdown_screen(old[p.CFG_SCREEN + '.class'], 752)
    g242.compare_methods(
        old[p.CFG_SCREEN + '.class'], new[p.CFG_SCREEN + '.class'],
        changed={('render', '(Lnet/minecraft/client/gui/GuiGraphics;IIF)V')},
    )

    assert new[p.BRANCH_SCREEN + '.class'] == p.patch_dropdown_screen(old[p.BRANCH_SCREEN + '.class'], 605)
    g242.compare_methods(
        old[p.BRANCH_SCREEN + '.class'], new[p.BRANCH_SCREEN + '.class'],
        changed={('render', '(Lnet/minecraft/client/gui/GuiGraphics;IIF)V')},
    )

    # NpcStatsGraphUi must have ZERO drawCenteredString references
    ui_cls = classedit.Klass(new[p.UI + '.class'])
    for idx, entry in enumerate(ui_cls.cp):
        if entry and entry['tag'] in (9, 10, 11):
            r = constant_ref(ui_cls, idx)
            assert r[1] != 'drawCenteredString', r

    # Server-loaded NpcDefaultModel254 must have ZERO net/minecraft/client/ or dev/settledlands/npc/client/ refs
    dm_cls = classedit.Klass(new[p.DEF_MODEL + '.class'])
    for idx, entry in enumerate(dm_cls.cp):
        if entry and entry['tag'] == 7:
            cname = dm_cls.utf8(entry['a'])
            assert not cname.startswith(('net/minecraft/client/', 'dev/settledlands/npc/client/')), cname

    for name in p.ADDED | p.CHANGED:
        if name.endswith('.class'):
            assert classedit.Klass(new[name]).major == 65, name
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(p.PATCHINFO)
    print('OK exact 254 diff: left-of-entry stats, zero-shadow UI, Z=+500 dropdown popups, server-safe ivy_oleander default, bottom Randomize; all 253/252/251 classes byte-identical', flush=True)


def assets_semantics(base: pathlib.Path, jar: pathlib.Path):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old_assets = {n: b.read(n) for n in b.namelist() if n.startswith('assets/')}
        new_assets = {n: z.read(n) for n in z.namelist() if n.startswith('assets/')}
        lang_changed = {'assets/settledlands/lang/ru_ru.json', 'assets/settledlands/lang/en_us.json'}
        assert set(new_assets) - set(old_assets) == {p.IVY_ASSET}
        assert {n for n in old_assets if old_assets[n] != new_assets[n]} == lang_changed

        ivy_raw = new_assets[p.IVY_ASSET]
        assert digest(ivy_raw) == p.IVY_OLEANDER_SHA
        model = json.loads(ivy_raw)
        texture = base64.b64decode(model['textures'][0]['source'].split(',', 1)[1], validate=True)
        assert texture == new_assets['assets/settledlands/dragon_default/ivy.png']
        assert model['resolution'] == {'width': 128, 'height': 128}
        assert len(model['animations']) == 82 and len(model['elements']) == 27
        assert all(a['name'].startswith('ivy_oleander.animation.') for a in model['animations'])
        assert digest(texture) == 'cdca7f12987915cb5b590a0060f1c4388c8bcc3ada28241ebcd237c1bf49e0ba'
    print('OK bundled ivy_oleander.bbmodel (128x128 UV, 27 elements, 82 animations, embedded PNG) and preserved 253 assets', flush=True)


def candidate_regressions_254(jar: pathlib.Path, inputs: pathlib.Path, java: pathlib.Path):
    bases = {i: inputs / name for i, name in g246.NAMES.items()}
    for label, targets in [('245 inherited targets', g245.TARGETS), ('246 inherited targets', g246.TARGETS), ('247 inherited targets', g247.TARGETS)]:
        out = g237.run('254 JVM verifies ' + label, [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java], contains=('ИТОГ: OK',))
        for target in targets:
            assert 'DECLARED ' + target.split('(')[0] in out, target
    for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
        g237.run('254 renderer regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java, '--base236', inputs / g237.NAMES[236], '--jar', jar], contains=('236 negative control', '237 JVM execution'))
    g237.run('254 cleanup regression', [PY, ROOT / 'tests/jvm_cleanup239.py', '--java', java, '--base238', bases[238], '--jar', jar], contains=('239 JVM execution',))
    for fixture, sig in [('jvm_noqueue243.py', 'real death/join/clone/drops/altar/recovery'), ('jvm_noqueue_altar243.py', 'no FIFO, multiple players'), ('jvm_queue_disabled243.py', 'real persisted queue API is disabled'), ('jvm_server_tick243.py', 'real server tick -> real level altar scan'), ('jvm_login243.py', 'pending/login')]:
        cmd = [PY, ROOT / 'tests/run_legacy246.py', fixture] if fixture == 'jvm_noqueue243.py' else [PY, ROOT / 'tests' / fixture]
        g237.run('254 no-queue regression ' + fixture, cmd + ['--java', java, '--base242', bases[242], '--jar', jar], contains=(sig,))
    g237.run('254 read-only cult diagnostic', [PY, ROOT / 'tests/jvm_cultprobe246.py', '--java', java, '--jar', jar], contains=('read-only cult probe:',))
    for fixture, sig in [('jvm_corpse_click244.py', 'complete/partial recovery'), ('jvm_real_corpse_dialogue244.py', 'Real GuideNpc.mobInteract JVM'), ('jvm_self_escape244.py', 'Real death/drop/clone/purgatory-exit/click JVM')]:
        g237.run('254 corpse regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java, '--base243', bases[243], '--jar', jar], contains=(sig,))
    for fixture, sig in [('jvm_body_wear245.py', 'Body wear: 3 paid lives'), ('jvm_wear_login245.py', 'Actual possession login hook')]:
        g237.run('254 wear regression ' + fixture, [PY, ROOT / 'tests/run_legacy246.py', fixture, '--java', java, '--jar', jar], contains=(sig, 'PASS'))
    for fixture, sig in [
        ('jvm_graph_stats254.py', 'Real graph and NBT roundtrip, left-of-entry default, randomize step'),
        ('jvm_graph_ui254.py', 'Real canvas and UI spinners, zero-shadow Z, randomize button'),
        ('jvm_graph_undo254.py', 'Real spinner & randomize, screen undo/redo'),
        ('jvm_network_stats246.py', 'Actual network, guards'),
        ('jvm_body_stats246.py', 'Actual NPC body stats'),
        ('jvm_possession_stats246.py', 'Real possession bridge'),
        ('jvm_stats_screen246.py', 'Actual stats screen'),
        ('jvm_gravity246.py', 'Actual BodilessEffect'),
    ]:
        g237.run('254 stats/graph regression ' + fixture, [PY, ROOT / 'tests' / fixture, '--java', java, '--jar', jar], contains=(sig, 'PASS'))
    g237.run('254 legacy wear mutants', [PY, ROOT / 'tests/mutants_legacy246.py', '--java', java, '--jar', jar], contains=('245 behavioral mutants against 246: PASS 18',))
    g237.run('254 legacy stats mutants', [PY, ROOT / 'tests/mutants_legacy246_for254.py', '--java', java, '--jar', jar], contains=('246 behavioral mutants against 254: PASS 32',))


def main():
    q = argparse.ArgumentParser()
    for name in ('inputs', 'base245', 'base246', 'base247', 'base248', 'base249', 'base250', 'base251', 'base252', 'base253',
                 'java', 'jar', 'tnm', 'core', 'emf', 'etf', 'pal', 'hns', 'gson-source'):
        q.add_argument('--' + name, required=True, type=pathlib.Path)
    q.add_argument('--prevalidated-base-log', type=pathlib.Path,
                   help='use separately completed 253 gate log; omitted when running full nested 253 gate directly')
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
    java = a.java.resolve()
    jar = a.jar.resolve()
    tnm = a.tnm.resolve()
    core = a.core.resolve()
    emf = a.emf.resolve()
    etf = a.etf.resolve()
    pal = a.pal.resolve()
    hns = a.hns.resolve()
    gson = a.gson_source.resolve()

    assert digest(b245.read_bytes()) == g247.g246.p.BASE_SHA
    assert digest(b246.read_bytes()) == g247.p.BASE_SHA
    assert digest(b247.read_bytes()) == g250.p.BASE_SHA
    assert digest(b248.read_bytes()) == g249.g248.SHA
    assert digest(b249.read_bytes()) == g249.SHA
    assert digest(b250.read_bytes()) == g250.SHA
    assert digest(b251.read_bytes()) == g251.SHA
    assert digest(b252.read_bytes()) == g252.SHA
    assert digest(b253.read_bytes()) == g253.SHA
    assert digest(core.read_bytes()) == g252.CORE_SHA
    hns_sha = digest(hns.read_bytes())
    assert hns_sha in ALLOWED_HNS_SHAS, f'unexpected HnS SHA-256: {hns_sha}'
    assert jar.stat().st_size == SIZE and digest(jar.read_bytes()) == SHA, 'wrong 254 candidate'
    ver = subprocess.run([str(java), '-version'], capture_output=True, text=True)
    assert ver.returncode == 0 and 'version "21.' in ver.stderr, ver.stderr
    print(f'build254 candidate size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}', flush=True)

    if a.prevalidated_base_log:
        log = a.prevalidated_base_log.read_text(encoding='utf-8')
        assert 'BUILD 253 GATE: PASS' in log and g253.SHA in log
        print('OK separately completed full 253 gate incl. 252–237 / HANDOFF §8:', a.prevalidated_base_log, flush=True)
    else:
        g237.run(
            'complete 253 gate incl. 252–237 / HANDOFF §8 on the exact 253 base',
            [PY, ROOT / 'tests/gate_build253.py',
             '--inputs', inputs, '--base245', b245, '--base246', b246, '--base247', b247,
             '--base248', b248, '--base249', b249, '--base250', b250, '--base251', b251, '--base252', b252,
             '--java', java, '--jar', b253, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf,
             '--pal', pal, '--hns', hns, '--gson-source', gson],
            contains=('BUILD 253 GATE: PASS',),
        )

    with tempfile.TemporaryDirectory(prefix='gate-build254-') as d:
        tmp = pathlib.Path(d)
        rebuilt = tmp / 'rebuild.jar'
        g237.run('254 deterministic rebuild', [PY, ROOT / 'tools/patch_ui_ivy254.py', '--base', b253, '--out', rebuilt, '--java', java], contains=(SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('254 structural validator', [PY, ROOT / 'tools/validate_jar.py', jar, '--base', b253], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, ROOT / 'tools/verify_jar.py']
        opts = ['--base', b253] + g237.option_entries('--expect-added', sorted(p.ADDED)) + g237.option_entries('--expect-changed', sorted(p.CHANGED))
        g237.run('254 CRC TOML locales mixins exact diff', verify + [jar] + opts, contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(b253, jar)
        assets_semantics(b253, jar)

        bad = tmp / 'crc.jar'
        bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('254 broken CRC rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        for label, missing in [
            ('language', 'assets/settledlands/lang/ru_ru.json'),
            ('mixin', 'settledlands.mixins.json'),
            ('helper-class', p.DEF_MODEL + '.class'),
            ('ivy-asset', p.IVY_ASSET),
        ]:
            bad = tmp / (label + '.jar')
            g237.alter_zip(jar, bad, remove={missing})
            g237.run('254 missing ' + label + ' rejected', verify + [bad] + (opts if 'helper' in label or 'ivy' in label else []), success=False, contains=('ПРОБЛЕМЫ',))

        with zipfile.ZipFile(b253) as z:
            for label, cls_entry in [
                ('unpatched-profile', p.PROFILE + '.class'),
                ('unpatched-guidenpc', p.GUIDE + '.class'),
                ('unpatched-npcclient', p.CLIENT + '.class'),
                ('unpatched-cfgscreen', p.CFG_SCREEN + '.class'),
                ('unpatched-branchscreen', p.BRANCH_SCREEN + '.class'),
            ]:
                bad = tmp / (label + '.jar')
                g237.alter_zip(jar, bad, replace={cls_entry: z.read(cls_entry)})
                try:
                    static_semantics(b253, bad)
                except AssertionError:
                    print('OK ' + label + ' rejected', flush=True)
                else:
                    raise AssertionError(label + ' not detected')

        targets = [
            'dev/settledlands/npc/NpcStatsGraph#ensure(dev.settledlands.npc.NpcDialogueGraph)',
            'dev/settledlands/npc/client/NpcStatsGraphUi#ensure(dev.settledlands.npc.client.NpcDialogueGraphScreen)',
            'dev/settledlands/npc/NpcDefaultModel254#initialTexture(dev.settledlands.npc.GuideNpc)',
            'dev/settledlands/npc/NpcDefaultModel254#loadTexture(net.minecraft.nbt.CompoundTag)',
            'dev/settledlands/npc/client/NpcDropdownTop254#installDefaultModelAsset',
            'dev/settledlands/npc/NpcProfile#load(net.minecraft.nbt.CompoundTag,net.minecraft.core.HolderLookup$Provider)',
            'dev/settledlands/npc/GuideNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
            'dev/settledlands/npc/client/NpcClient#setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent)',
            'dev/settledlands/model/BbModel#getName',
            'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
            'dev/settledlands/npc/NpcCopyEggFix253#withEntityId(dev.settledlands.npc.GuideNpc,net.minecraft.nbt.CompoundTag)',
            'dev/settledlands/npc/client/PlayerBodyRenderer#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/FirstPersonSwordBridge252#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/HackSlashAccess#prepare(net.minecraft.client.player.AbstractClientPlayer)',
            'dev/settledlands/npc/PossessionLoginSync251#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
        ]
        out = g237.run('254 JVM verifies all 254/253/252/251 declarations', [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java], contains=('ИТОГ: OK',))
        for target in targets:
            assert 'DECLARED ' + target.split('(')[0] in out, target

        candidate_regressions_254(jar, inputs, java)
        g237.run('254 preserves 251 relog appearance, payload and renderer', [PY, ROOT / 'tests/jvm_login_model251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('PASS baseline 250 reproduces missing possessed model', 'PASS relog model, real payload codec/client handler/renderer'))
        g237.run('254 nine verifier-valid relog appearance mutants', [PY, ROOT / 'tests/mutants_login251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('251 possessed model login behavioral mutants: PASS 9',))
        g237.run('254 preserves bodiless damage/milk', [PY, ROOT / 'tests/jvm_bodiless247.py', '--java', java, '--jar', jar], contains=('247 actual protection + 246 predicate', 'PASS'))
        g237.run('254 legacy bodiless mutants', [PY, ROOT / 'tests/mutants247.py', '--java', java, '--jar', jar], contains=('247 behavioral mutants: PASS 17',))
        g237.run('254 FA bridge regression and 1200 poses', [PY, ROOT / 'tests/jvm_fresh_motion250.py', '--java', java, '--jar', jar], contains=('51 scenarios + 1200 JOML poses PASS',))
        g237.run('254 inherited FA negative mutants', [PY, ROOT / 'tests/mutants_fresh251.py', '--java', java, '--jar', jar], contains=('250 FA behavioral mutants against 251: PASS 21',))
        g237.run('254 actual EMF root and TnM hook with controlled providers', [PY, ROOT / 'tests/jvm_real_emf_tnm250.py', '--java', java, '--jar', jar, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS actual EMF 3.3.12 root and TnM hook',))
        g237.run('254 actual actor, crawl, PAL, nested, rollback and shield-tilt-free scenarios', [PY, ROOT / 'tests/jvm_motion253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('CONFIRMED build 252 legacy shield tilt +40deg', 'PASS real BbModel/BbAnimation', '253 JVM motion & shield scenarios PASS'))
        g237.run('254 37 calibrated crawl/PAL negative mutants', [PY, ROOT / 'tests/mutants251.py', '--java', java, '--jar', jar, '--pal', pal], contains=('251 behavioral mutants: PASS 37',))
        g237.run('254 HnS JSON through real PAL controller and item mixin', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames',))
        g237.run('254 four genuine HnS modifiers plus negative controls', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_hns_modifiers251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync', '4 negative controls'))
        g237.run('254 combined 3rd-person PAL + real EMF root/TnM hook with HnS frames', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns_tnm251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames', 'actual EMF 3.3.12 root and TnM 1.0.4 hook'))
        g237.run('254 first-person HnS+PAL+EMF+Core+TnM + BladeTracker reach', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_fp_sword252.py', '--java', java, '--jar', jar, '--base-jar', b251, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('CONFIRMED build 251 bug: pureMinDist=0.0', 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames, frontHits=14/14, 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks'))
        g237.run('254 ten verifier-valid first-person HnS mutants', [PY, ROOT / 'tests/run_hns_compat254.py', 'mutants_fp252.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('PASS mutants_fp252: 10/10 verifier-valid mutants killed',))
        g237.run('254 NPC copy pick-block, ENTITY_DATA id, ItemStack.save & spawn', [PY, ROOT / 'tests/jvm_npc_copy253.py', '--java', java, '--jar', jar, '--base-jar', b252], contains=('CONFIRMED build 252 NPC copy freeze/crash: Missing id for entity in DataComponents.ENTITY_DATA', 'PASS build 253 NPC copy: pick-block, codec, permissions, ENTITY_DATA id, ItemStack.save/player NBT save, independent GuideNpc+stats & GuideTriggerNpc spawn'))
        g237.run('254 eleven verifier-valid shield & NPC copy behavioral mutants', [PY, ROOT / 'tests/mutants253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('PASS mutants253: 11/11 verifier-valid mutants killed',))
        g237.run('254 UI & Ivy Oleander end-to-end JVM verification (plus 253 bug reproduction)', [PY, ROOT / 'tests/jvm_ui_ivy254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson], contains=('CONFIRMED build 253 bugs: stats=(568,-240) right of arrival2, popupZ=0.0 behind previewZ=95.0, defaultTexture=\'\'', 'PASS build 254 UI & Ivy:'))
        g237.run('254 seventeen verifier-valid UI & Ivy behavioral mutants', [PY, ROOT / 'tests/mutants254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson], contains=('PASS mutants254: 17/17 verifier-valid mutants killed',))

    print('BUILD 254 GATE: PASS (left-of-entry stats + zero-shadow sliders + Z=500 dropdowns + ivy_oleander default + bottom Randomize + 253-237 regressions; actual game/NeoForge/visual NOT run; NPC TnM NOT supported)', flush=True)


if __name__ == '__main__':
    main()
