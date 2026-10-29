#!/usr/bin/env python3
"""Release gate 255: full 254–237/HANDOFF §8 on exact bases, plus Build 255 Autonomous NPC AI verification.

Genuine Java 21 (-Xverify:all) exercises real NpcAutoAi255, NpcAutoAiEngine255, NpcAutoAiConfigUi255,
NpcAutoAiGraphUi255, NpcStatsGraph, NpcStatsGraphUi, NpcConfigScreen, NpcBranchScreen,
NpcDialogueGraphScreen, NpcGraphCanvas, NpcEdit, NpcDropdownTop254, NpcDefaultModel254, NpcProfile,
GuideNpc/GuideTriggerNpc/GuideCorpseNpc, NpcClient, BbModelStorage, BbModelParser, BbModel,
NpcCopyPickPayload, PAL 1.1.6, HnS beta2.5 data + BladeTracker, EMF Compat Core 2.3.2,
Touch'n Motion 1.0.4, and EMF 3.3.12 components with synthetic APIs/providers.
This is NOT a full Minecraft/NeoForge launch or visual test.
No external third-party mod JARs are distributed.
"""
import argparse, base64, hashlib, json, pathlib, shutil, subprocess, sys, tempfile, tomllib, zipfile

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
import patch_auto_ai255 as p
from patch_corpse_pose237 import constant_ref

PY = sys.executable
SHA = '7866e20a75eb5ef53e254e8e34a5eacc155d0b3ac768b99977ad4d95358b6541'
SIZE = 2215570
ALLOWED_HNS_SHAS = g254.ALLOWED_HNS_SHAS


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

    # All player animation, HnS, FA, TnM, corpse, possession, 253 shield/copy, and 254 UI/Ivy classes remain byte-identical
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
        'dev/settledlands/npc/IvyCombatAi.class',
        'dev/settledlands/npc/NpcStatsGraph.class',
        'dev/settledlands/npc/NpcDefaultModel254.class',
        'dev/settledlands/npc/client/NpcStatsGraphUi.class',
        'dev/settledlands/npc/client/NpcDropdownTop254.class',
        'dev/settledlands/npc/client/NpcBranchScreen.class',
        'dev/settledlands/npc/client/NpcPickCopy.class',
        'dev/settledlands/npc/client/GuideRenderer.class',
    ):
        assert old[untouched] == new[untouched], untouched

    # Exact byte-for-byte verification on all 7 patched classes
    assert new[p.PROFILE + '.class'] == p.patch_profile(old[p.PROFILE + '.class'])
    assert new[p.GRAPH + '.class'] == p.patch_graph(old[p.GRAPH + '.class'])
    assert new[p.EDIT + '.class'] == p.patch_edit(old[p.EDIT + '.class'])
    assert new[p.GUIDE + '.class'] == p.patch_guide_npc(old[p.GUIDE + '.class'])
    assert new[p.CFG_SCREEN + '.class'] == p.patch_cfg_screen(old[p.CFG_SCREEN + '.class'])
    assert new[p.CANVAS + '.class'] == p.patch_canvas(old[p.CANVAS + '.class'])
    assert new[p.SCREEN + '.class'] == p.patch_graph_screen(old[p.SCREEN + '.class'])

    # NpcAutoAiGraphUi255 must have ZERO drawCenteredString references and ZERO non-246 screen bridges
    allowed_screen_bridges = {
        'settledlands$graph', 'settledlands$canvas', 'settledlands$font',
        'settledlands$contextOpen', 'settledlands$insideInspector', 'settledlands$hideStatsInspector',
        'settledlands$clearSelection', 'settledlands$remember', 'settledlands$changed',
        'settledlands$mouseClickedOriginal', 'settledlands$mouseDraggedOriginal', 'settledlands$mouseReleasedOriginal',
    }
    gui_cls = classedit.Klass(new[p.GRAPH_UI + '.class'])
    for idx, entry in enumerate(gui_cls.cp):
        if entry and entry['tag'] in (9, 10, 11):
            r = constant_ref(gui_cls, idx)
            assert r[1] != 'drawCenteredString', r
            if r[0] == p.SCREEN and r[1].startswith('settledlands$'):
                assert r[1] in allowed_screen_bridges, r

    # Server-loaded NpcAutoAi255* and NpcAutoAiEngine255* must have ZERO client-package references
    for srv_name in (
        p.AUTO_AI + '.class',
        p.AUTO_AI + '$ProfileState.class',
        p.AUTO_AI + '$GraphState.class',
        p.ENGINE + '.class',
        p.ENGINE + '$MeleeCombo.class',
    ):
        srv_cls = classedit.Klass(new[srv_name])
        for idx, entry in enumerate(srv_cls.cp):
            if entry and entry['tag'] == 7:
                cname = srv_cls.utf8(entry['a'])
                assert not cname.startswith(('net/minecraft/client/', 'dev/settledlands/npc/client/')), (srv_name, cname)

    for name in p.ADDED | p.CHANGED:
        if name.endswith('.class'):
            assert classedit.Klass(new[name]).major == 65, name
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode('utf-8'))['mods'][0]
    assert meta['version'] == p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode('utf-8').startswith(p.PATCHINFO)
    print('OK exact 255 diff: server-safe NpcAutoAi255 + NpcAutoAiEngine255, zero-shadow graph UI, Mobility config section; all 254/253/252/251 classes byte-identical', flush=True)


def assets_semantics(base: pathlib.Path, jar: pathlib.Path):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old_assets = {n: b.read(n) for n in b.namelist() if n.startswith('assets/')}
        new_assets = {n: z.read(n) for n in z.namelist() if n.startswith('assets/')}
        lang_changed = {'assets/settledlands/lang/ru_ru.json', 'assets/settledlands/lang/en_us.json'}
        assert set(new_assets) == set(old_assets)
        assert {n for n in old_assets if old_assets[n] != new_assets[n]} == lang_changed
        assert digest(new_assets[g254.p.IVY_ASSET]) == g254.p.IVY_OLEANDER_SHA
        for locale, extra in p.LANG.items():
            doc = json.loads(new_assets[f'assets/settledlands/lang/{locale}.json'])
            for k, v in extra.items():
                assert doc.get(k) == v, (locale, k, doc.get(k))
    print('OK preserved 254 ivy_oleander.bbmodel asset and added 14 ru_ru/en_us Auto-AI localization keys', flush=True)


def main():
    q = argparse.ArgumentParser()
    for name in ('inputs', 'base245', 'base246', 'base247', 'base248', 'base249', 'base250', 'base251', 'base252', 'base253', 'base254',
                 'java', 'jar', 'tnm', 'core', 'emf', 'etf', 'pal', 'hns', 'gson-source'):
        q.add_argument('--' + name, required=True, type=pathlib.Path)
    q.add_argument('--prevalidated-base-log', type=pathlib.Path,
                   help='use separately completed 254 gate log; omitted when running full nested 254 gate directly')
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
        assert digest(core.read_bytes()) == g252.CORE_SHA
        hns_sha = digest(hns.read_bytes())
        assert hns_sha in ALLOWED_HNS_SHAS, f'unexpected HnS SHA-256: {hns_sha}'
        assert jar.stat().st_size == SIZE and digest(jar.read_bytes()) == SHA, 'wrong 255 candidate'
        ver = subprocess.run([str(java), '-version'], capture_output=True, text=True)
        assert ver.returncode == 0 and 'version "21.' in ver.stderr, ver.stderr
        print(f'build255 candidate size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}', flush=True)

        if a.prevalidated_base_log:
            log = a.prevalidated_base_log.read_text(encoding='utf-8')
            assert 'BUILD 254 GATE: PASS' in log and g254.SHA in log
            print('OK separately completed full 254 gate incl. 253–237 / HANDOFF §8:', a.prevalidated_base_log, flush=True)
        else:
            g237.run(
                'complete 254 gate incl. 253–237 / HANDOFF §8 on the exact 254 base',
                [PY, ROOT / 'tests/gate_build254.py',
                 '--inputs', inputs, '--base245', b245, '--base246', b246, '--base247', b247,
                 '--base248', b248, '--base249', b249, '--base250', b250, '--base251', b251,
                 '--base252', b252, '--base253', b253,
                 '--java', java, '--jar', b254, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf,
                 '--pal', pal, '--hns', hns, '--gson-source', gson],
                contains=('BUILD 254 GATE: PASS',),
            )

        with tempfile.TemporaryDirectory(prefix='gate-build255-') as d:
            tmp = pathlib.Path(d)
            rebuilt = tmp / 'rebuild.jar'
            g237.run('255 deterministic rebuild', [PY, ROOT / 'tools/patch_auto_ai255.py', '--base', b254, '--out', rebuilt, '--java', java], contains=(SHA,))
            assert rebuilt.read_bytes() == jar.read_bytes()
            g237.run('255 structural validator', [PY, ROOT / 'tools/validate_jar.py', jar, '--base', b254], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
            verify = [PY, ROOT / 'tools/verify_jar.py']
            opts = ['--base', b254] + g237.option_entries('--expect-added', sorted(p.ADDED)) + g237.option_entries('--expect-changed', sorted(p.CHANGED))
            g237.run('255 CRC TOML locales mixins exact diff', verify + [jar] + opts, contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
            static_semantics(b254, jar)
            assets_semantics(b254, jar)

            bad = tmp / 'crc.jar'
            bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
            g237.run('255 broken CRC rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
            for label, missing in [
                ('language', 'assets/settledlands/lang/ru_ru.json'),
                ('mixin', 'settledlands.mixins.json'),
                ('auto-ai-state', p.AUTO_AI + '.class'),
                ('auto-ai-engine', p.ENGINE + '.class'),
                ('ivy-asset', g254.p.IVY_ASSET),
            ]:
                bad = tmp / (label + '.jar')
                g237.alter_zip(jar, bad, remove={missing})
                g237.run('255 missing ' + label + ' rejected', verify + [bad] + (opts if 'auto-ai' in label or 'ivy' in label else []), success=False, contains=('ПРОБЛЕМЫ',))

            with zipfile.ZipFile(b254) as z:
                for label, cls_entry in [
                    ('unpatched-profile', p.PROFILE + '.class'),
                    ('unpatched-graph', p.GRAPH + '.class'),
                    ('unpatched-edit', p.EDIT + '.class'),
                    ('unpatched-guidenpc', p.GUIDE + '.class'),
                    ('unpatched-cfgscreen', p.CFG_SCREEN + '.class'),
                    ('unpatched-canvas', p.CANVAS + '.class'),
                    ('unpatched-graphscreen', p.SCREEN + '.class'),
                ]:
                    bad = tmp / (label + '.jar')
                    g237.alter_zip(jar, bad, replace={cls_entry: z.read(cls_entry)})
                    try:
                        static_semantics(b254, bad)
                    except AssertionError:
                        print('OK ' + label + ' rejected', flush=True)
                    else:
                        raise AssertionError(label + ' not detected')

            targets = [
                'dev/settledlands/npc/NpcAutoAi255#isAutoWander(dev.settledlands.npc.NpcProfile)',
                'dev/settledlands/npc/NpcAutoAi255#syncGraphSaveToProfile(dev.settledlands.npc.NpcDialogueGraph,dev.settledlands.npc.NpcProfile)',
                'dev/settledlands/npc/NpcAutoAiEngine255#runMobilityAndAutoAi(dev.settledlands.npc.GuideNpc)',
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
            out = g237.run('255 JVM verifies all 255/254/253/252/251 declarations', [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java], contains=('ИТОГ: OK',))
            for target in targets:
                assert 'DECLARED ' + target.split('(')[0] in out, target

            g254.candidate_regressions_254(jar, inputs, java)
            g237.run('255 preserves 251 relog appearance, payload and renderer', [PY, ROOT / 'tests/jvm_login_model251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('PASS baseline 250 reproduces missing possessed model', 'PASS relog model, real payload codec/client handler/renderer'))
            g237.run('255 nine verifier-valid relog appearance mutants', [PY, ROOT / 'tests/mutants_login251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('251 possessed model login behavioral mutants: PASS 9',))
            g237.run('255 preserves bodiless damage/milk', [PY, ROOT / 'tests/jvm_bodiless247.py', '--java', java, '--jar', jar], contains=('247 actual protection + 246 predicate', 'PASS'))
            g237.run('255 legacy bodiless mutants', [PY, ROOT / 'tests/mutants247.py', '--java', java, '--jar', jar], contains=('247 behavioral mutants: PASS 17',))
            g237.run('255 FA bridge regression and 1200 poses', [PY, ROOT / 'tests/jvm_fresh_motion250.py', '--java', java, '--jar', jar], contains=('51 scenarios + 1200 JOML poses PASS',))
            g237.run('255 inherited FA negative mutants', [PY, ROOT / 'tests/mutants_fresh251.py', '--java', java, '--jar', jar], contains=('250 FA behavioral mutants against 251: PASS 21',))
            g237.run('255 actual EMF root and TnM hook with controlled providers', [PY, ROOT / 'tests/jvm_real_emf_tnm250.py', '--java', java, '--jar', jar, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS actual EMF 3.3.12 root and TnM hook',))
            g237.run('255 actual actor, crawl, PAL, nested, rollback and shield-tilt-free scenarios', [PY, ROOT / 'tests/jvm_motion253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('CONFIRMED build 252 legacy shield tilt +40deg', 'PASS real BbModel/BbAnimation', '253 JVM motion & shield scenarios PASS'))
            g237.run('255 37 calibrated crawl/PAL negative mutants', [PY, ROOT / 'tests/mutants251.py', '--java', java, '--jar', jar, '--pal', pal], contains=('251 behavioral mutants: PASS 37',))
            g237.run('255 HnS JSON through real PAL controller and item mixin', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames',))
            g237.run('255 four genuine HnS modifiers plus negative controls', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_hns_modifiers251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync', '4 negative controls'))
            g237.run('255 combined 3rd-person PAL + real EMF root/TnM hook with HnS frames', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_realhns_tnm251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames', 'actual EMF 3.3.12 root and TnM 1.0.4 hook'))
            g237.run('255 first-person HnS+PAL+EMF+Core+TnM + BladeTracker reach', [PY, ROOT / 'tests/run_hns_compat254.py', 'jvm_fp_sword252.py', '--java', java, '--jar', jar, '--base-jar', b251, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('CONFIRMED build 251 bug: pureMinDist=0.0', 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames, frontHits=14/14, 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks'))
            g237.run('255 ten verifier-valid first-person HnS mutants', [PY, ROOT / 'tests/run_hns_compat254.py', 'mutants_fp252.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('PASS mutants_fp252: 10/10 verifier-valid mutants killed',))
            g237.run('255 NPC copy pick-block, ENTITY_DATA id, ItemStack.save & spawn', [PY, ROOT / 'tests/jvm_npc_copy253.py', '--java', java, '--jar', jar, '--base-jar', b252], contains=('CONFIRMED build 252 NPC copy freeze/crash: Missing id for entity in DataComponents.ENTITY_DATA', 'PASS build 253 NPC copy: pick-block, codec, permissions, ENTITY_DATA id, ItemStack.save/player NBT save, independent GuideNpc+stats & GuideTriggerNpc spawn'))
            g237.run('255 eleven verifier-valid shield & NPC copy behavioral mutants', [PY, ROOT / 'tests/mutants253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('PASS mutants253: 11/11 verifier-valid mutants killed',))
            g237.run('255 UI & Ivy Oleander end-to-end JVM verification (plus 253 bug reproduction)', [PY, ROOT / 'tests/jvm_ui_ivy254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson], contains=('CONFIRMED build 253 bugs: stats=(568,-240) right of arrival2, popupZ=0.0 behind previewZ=95.0, defaultTexture=\'\'', 'PASS build 254 UI & Ivy:'))
            g237.run('255 seventeen verifier-valid UI & Ivy behavioral mutants', [PY, ROOT / 'tests/mutants254.py', '--java', java, '--base-jar', b253, '--jar', jar, '--gson-source', gson], contains=('PASS mutants254: 17/17 verifier-valid mutants killed',))
            g237.run('255 Autonomous NPC AI end-to-end JVM verification (plus 254 baseline check)', [PY, ROOT / 'tests/jvm_auto_ai255.py', '--java', java, '--base-jar', b254, '--jar', jar, '--gson-source', gson], contains=('CONFIRMED build 254 baseline: no autonomous home-radius wander, no hostile-mob Ivy melee, no Auto-AI controls', 'PASS build 255 Auto-AI:'))
            g237.run('255 twenty-four verifier-valid Autonomous NPC AI behavioral mutants', [PY, ROOT / 'tests/mutants255.py', '--java', java, '--jar', jar, '--gson-source', gson], contains=('PASS mutants 255: 24/24 verifier-valid mutants killed',))

        print('BUILD 255 GATE: PASS (autonomous home-radius wander + Ivy melee combos without fireballs + Stats Damage/Speed scaling + two-way Config/Graph UI + 254-237 regressions; actual game/NeoForge/visual NOT run; NPC TnM NOT supported)', flush=True)
    finally:
        if not had_cwd_settledlands:
            shutil.rmtree(pathlib.Path.cwd() / 'settledlands', ignore_errors=True)


if __name__ == '__main__':
    main()
