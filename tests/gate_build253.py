#!/usr/bin/env python3
"""Release gate 253: full 252–237/HANDOFF §8 on exact bases, plus 253 shield-tilt removal & NPC copy regressions.

Genuine Java 21 (-Xverify:all), BbModel, NpcCopyPickPayload, GuideNpc/GuideTriggerNpc/GuideCorpseNpc,
PAL 1.1.6, user-supplied HnS beta2.5 data + BladeTracker, EMF Compat Core 2.3.2,
Touch'n Motion 1.0.4, and EMF 3.3.12 components are exercised with synthetic APIs/providers.
This is NOT a full Minecraft/NeoForge launch or visual test.
No external assets or third-party mod JARs are distributed.
"""
import argparse, base64, hashlib, json, pathlib, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit, gate_build237 as g237, gate_build242 as g242, gate_build247 as g247, gate_build249 as g249, gate_build250 as g250, gate_build251 as g251, gate_build252 as g252, patch_shield_copy253 as p

PY = sys.executable
SHA = 'a5ac75dfbf24ad4fd0e06fcdcc3c4da51b2ab64bdea24440f43903259d01814e'
SIZE = 1629114


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
    for untouched in (
        'dev/settledlands/npc/client/PlayerBodyRenderer.class',
        'dev/settledlands/model/FirstPersonSwordBridge252.class',
        'dev/settledlands/model/FirstPersonPalEmfAccess252.class',
        'dev/settledlands/model/BbModelParser.class',
        'dev/settledlands/model/BbModelManager.class',
        'dev/settledlands/model/PlayerMotionBridge.class',
        'dev/settledlands/model/HackSlashAccess.class',
        'dev/settledlands/model/FreshEmfAccess.class',
        'dev/settledlands/npc/PossessionSystem.class',
        'dev/settledlands/npc/PossessionLoginSync251.class',
        'dev/settledlands/npc/GuideNpc.class',
        'dev/settledlands/npc/GuideTriggerNpc.class',
        'dev/settledlands/npc/GuideCorpseNpc.class',
        'dev/settledlands/npc/client/NpcPickCopy.class',
        'dev/settledlands/npc/client/GuideRenderer.class',
    ):
        assert old[untouched] == new[untouched], untouched

    # 1. BbModel: only renderActorHeldItem changed; legacy blocking tilt removed; 251 hooks kept
    model_key = p.MODEL + '.class'
    assert new[model_key] == p.patch_model(old[model_key])
    g242.compare_methods(old[model_key], new[model_key], changed={('renderActorHeldItem', p.ITEM_DESC)})
    model_cls = classedit.Klass(new[model_key])
    item_refs = g242.refs(model_cls, model_cls.find_method('renderActorHeldItem', p.ITEM_DESC))
    assert not any(m in ('isBlocking', 'getUseAnimation') or owner.endswith('UseAnim') for owner, m, _ in item_refs), item_refs
    assert any(owner == p.BRIDGE and m == 'itemPosition' for owner, m, _ in item_refs), item_refs
    assert any(owner == p.BRIDGE and m == 'renderHeldItem' for owner, m, _ in item_refs), item_refs

    # 2. NpcCopyPickPayload: only buildCopyEgg changed; calls NpcCopyEggFix253.eggItem and withEntityId before CustomData.of
    copy_key = p.COPY_PAYLOAD + '.class'
    assert new[copy_key] == p.patch_copy_payload(old[copy_key])
    g242.compare_methods(old[copy_key], new[copy_key], changed={('buildCopyEgg', p.COPY_DESC)})
    copy_cls = classedit.Klass(new[copy_key])
    copy_refs = g242.refs(copy_cls, copy_cls.find_method('buildCopyEgg', p.COPY_DESC))
    egg_idx = next(i for i, r in enumerate(copy_refs) if r[:2] == (p.COPY_FIX, 'eggItem'))
    id_idx = next(i for i, r in enumerate(copy_refs) if r[:2] == (p.COPY_FIX, 'withEntityId'))
    cd_idx = next(i for i, r in enumerate(copy_refs) if r[:2] == ('net/minecraft/world/item/component/CustomData', 'of'))
    assert egg_idx < id_idx < cd_idx, copy_refs

    for name in p.ADDED | p.CHANGED:
        if name.endswith('.class'):
            assert classedit.Klass(new[name]).major == 65, name
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0]
    assert meta['version'] == p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode().startswith(p.PATCHINFO)
    print('OK exact 253 diff: shield blocking tilt removed in BbModel.renderActorHeldItem + entity id populated in NpcCopyPickPayload.buildCopyEgg; all 252/251/NPC/gameplay classes byte-identical', flush=True)


def assets_semantics(base: pathlib.Path, jar: pathlib.Path):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        names = [n for n in b.namelist() if n.startswith('assets/')]
        assert names and all(b.read(n) == z.read(n) for n in names), 'asset or UV data changed'
        model = json.loads(z.read('assets/settledlands/dragon_default/ivy.bbmodel'))
        texture = base64.b64decode(model['textures'][0]['source'].split(',', 1)[1], validate=True)
        assert texture == z.read('assets/settledlands/dragon_default/ivy.png')
        assert model['resolution'] == {'width': 128, 'height': 128} and len(model['animations']) == 82 and len(model['elements']) == 27
        assert digest(texture) == 'cdca7f12987915cb5b590a0060f1c4388c8bcc3ada28241ebcd237c1bf49e0ba'
    print('OK identical asset/UV/texture/parser/NPC renderer and 252/251 pipelines', flush=True)


def main():
    q = argparse.ArgumentParser()
    for name in ('inputs', 'base245', 'base246', 'base247', 'base248', 'base249', 'base250', 'base251', 'base252',
                 'java', 'jar', 'tnm', 'core', 'emf', 'etf', 'pal', 'hns', 'gson-source'):
        q.add_argument('--' + name, required=True, type=pathlib.Path)
    q.add_argument('--prevalidated-base-log', type=pathlib.Path,
                   help='use separately completed 252 gate from this workspace; omitted for independently reproducible full gate')
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
    assert digest(core.read_bytes()) == g252.CORE_SHA
    assert jar.stat().st_size == SIZE and digest(jar.read_bytes()) == SHA, 'wrong 253 candidate'
    ver = subprocess.run([str(java), '-version'], capture_output=True, text=True)
    assert ver.returncode == 0 and 'version "21.' in ver.stderr, ver.stderr
    print(f'build253 candidate size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}', flush=True)

    if a.prevalidated_base_log:
        log = a.prevalidated_base_log.read_text(encoding='utf-8')
        assert 'BUILD 252 GATE: PASS' in log and g252.SHA in log
        print('OK separately completed full 252 gate incl. 251–237 / HANDOFF §8:', a.prevalidated_base_log, flush=True)
    else:
        g237.run(
            'complete 252 gate incl. 251–237 / HANDOFF §8 on the exact 252 base',
            [PY, ROOT / 'tests/gate_build252.py',
             '--inputs', inputs, '--base245', b245, '--base246', b246, '--base247', b247,
             '--base248', b248, '--base249', b249, '--base250', b250, '--base251', b251,
             '--java', java, '--jar', b252, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf,
             '--pal', pal, '--hns', hns, '--gson-source', gson],
            contains=('BUILD 252 GATE: PASS',),
        )

    with tempfile.TemporaryDirectory(prefix='gate-build253-') as d:
        tmp = pathlib.Path(d)
        rebuilt = tmp / 'rebuild.jar'
        g237.run('253 deterministic rebuild', [PY, ROOT / 'tools/patch_shield_copy253.py', '--base', b252, '--out', rebuilt, '--java', java], contains=(SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('253 structural validator', [PY, ROOT / 'tools/validate_jar.py', jar, '--base', b252], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, ROOT / 'tools/verify_jar.py']
        opts = ['--base', b252] + g237.option_entries('--expect-added', sorted(p.ADDED)) + g237.option_entries('--expect-changed', sorted(p.CHANGED))
        g237.run('253 CRC TOML locales mixins exact diff', verify + [jar] + opts, contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(b252, jar)
        assets_semantics(b252, jar)

        bad = tmp / 'crc.jar'
        bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('253 broken CRC rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        for label, missing in [('language', 'assets/settledlands/lang/ru_ru.json'), ('mixin', 'settledlands.mixins.json')]:
            bad = tmp / (label + '.jar')
            g237.alter_zip(jar, bad, remove={missing})
            g237.run('253 missing ' + label + ' rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ',))
        bad = tmp / 'missing-fix.jar'
        g237.alter_zip(jar, bad, remove={p.COPY_FIX + '.class'})
        g237.run('253 missing NpcCopyEggFix253 rejected', verify + [bad] + opts, success=False, contains=('ПРОБЛЕМЫ',))

        with zipfile.ZipFile(b252) as z:
            orig_bb = z.read(p.MODEL + '.class')
            orig_cp = z.read(p.COPY_PAYLOAD + '.class')
        for label, rep in [('unpatched-bbmodel', {p.MODEL + '.class': orig_bb}), ('unpatched-copypayload', {p.COPY_PAYLOAD + '.class': orig_cp})]:
            bad = tmp / (label + '.jar')
            g237.alter_zip(jar, bad, replace=rep)
            try:
                static_semantics(b252, bad)
            except AssertionError:
                print('OK ' + label + ' rejected', flush=True)
            else:
                raise AssertionError(label + ' not detected')

        targets = [
            'dev/settledlands/model/BbModel#getName',
            'dev/settledlands/npc/NpcCopyPickPayload#buildCopyEgg(dev.settledlands.npc.GuideNpc)',
            'dev/settledlands/npc/NpcCopyEggFix253#withEntityId(dev.settledlands.npc.GuideNpc,net.minecraft.nbt.CompoundTag)',
            'dev/settledlands/npc/NpcCopyEggFix253#eggItem(dev.settledlands.npc.GuideNpc)',
            'dev/settledlands/npc/client/PlayerBodyRenderer#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/FirstPersonSwordBridge252#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/HackSlashAccess#prepare(net.minecraft.client.player.AbstractClientPlayer)',
            'dev/settledlands/npc/PossessionLoginSync251#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
        ]
        out = g237.run('253 JVM verifies patched BbModel, NpcCopyPickPayload, NpcCopyEggFix253 and 252/251 declarations', [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java], contains=('ИТОГ: OK',))
        for target in targets:
            assert 'DECLARED ' + target.split('(')[0] in out, target

        g247.candidate_regressions(jar, inputs, java)
        g237.run('253 preserves 251 relog appearance, payload and renderer', [PY, ROOT / 'tests/jvm_login_model251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('PASS baseline 250 reproduces missing possessed model', 'PASS relog model, real payload codec/client handler/renderer'))
        g237.run('253 nine verifier-valid relog appearance mutants', [PY, ROOT / 'tests/mutants_login251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('251 possessed model login behavioral mutants: PASS 9',))
        g237.run('253 preserves bodiless damage/milk', [PY, ROOT / 'tests/jvm_bodiless247.py', '--java', java, '--jar', jar], contains=('247 actual protection + 246 predicate', 'PASS'))
        g237.run('253 legacy bodiless mutants', [PY, ROOT / 'tests/mutants247.py', '--java', java, '--jar', jar], contains=('247 behavioral mutants: PASS 17',))
        g237.run('253 FA bridge regression and 1200 poses', [PY, ROOT / 'tests/jvm_fresh_motion250.py', '--java', java, '--jar', jar], contains=('51 scenarios + 1200 JOML poses PASS',))
        g237.run('253 inherited FA negative mutants', [PY, ROOT / 'tests/mutants_fresh251.py', '--java', java, '--jar', jar], contains=('250 FA behavioral mutants against 251: PASS 21',))
        g237.run('253 actual EMF root and TnM hook with controlled providers', [PY, ROOT / 'tests/jvm_real_emf_tnm250.py', '--java', java, '--jar', jar, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS actual EMF 3.3.12 root and TnM hook',))
        g237.run('253 actual actor, crawl, PAL, nested, rollback and shield-tilt-free scenarios (plus 252 tilt reproduction)', [PY, ROOT / 'tests/jvm_motion253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('CONFIRMED build 252 legacy shield tilt +40deg', 'PASS real BbModel/BbAnimation', '253 JVM motion & shield scenarios PASS'))
        g237.run('253 37 calibrated crawl/PAL negative mutants', [PY, ROOT / 'tests/mutants251.py', '--java', java, '--jar', jar, '--pal', pal], contains=('251 behavioral mutants: PASS 37',))
        g237.run('253 HnS JSON through real PAL controller and item mixin', [PY, ROOT / 'tests/jvm_realhns251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames',))
        g237.run('253 four genuine HnS modifiers plus negative controls', [PY, ROOT / 'tests/jvm_hns_modifiers251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync', '4 negative controls'))
        g237.run('253 combined 3rd-person PAL + real EMF root/TnM hook with HnS frames', [PY, ROOT / 'tests/jvm_realhns_tnm251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames', 'actual EMF 3.3.12 root and TnM 1.0.4 hook'))
        g237.run('253 first-person HnS+PAL+EMF+Core+TnM + BladeTracker reach (plus 251 bug reproduction)', [PY, ROOT / 'tests/jvm_fp_sword252.py', '--java', java, '--jar', jar, '--base-jar', b251, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('CONFIRMED build 251 bug: pureMinDist=0.0', 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames, frontHits=14/14, 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks'))
        g237.run('253 ten verifier-valid first-person HnS mutants', [PY, ROOT / 'tests/mutants_fp252.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('PASS mutants_fp252: 10/10 verifier-valid mutants killed',))
        g237.run('253 NPC copy pick-block, ENTITY_DATA id, ItemStack.save & spawn (plus 252 freeze/crash reproduction)', [PY, ROOT / 'tests/jvm_npc_copy253.py', '--java', java, '--jar', jar, '--base-jar', b252], contains=('CONFIRMED build 252 NPC copy freeze/crash: Missing id for entity in DataComponents.ENTITY_DATA', 'PASS build 253 NPC copy: pick-block, codec, permissions, ENTITY_DATA id, ItemStack.save/player NBT save, independent GuideNpc+stats & GuideTriggerNpc spawn'))
        g237.run('253 eleven verifier-valid shield & NPC copy behavioral mutants', [PY, ROOT / 'tests/mutants253.py', '--java', java, '--jar', jar, '--base-jar', b252, '--pal', pal], contains=('PASS mutants253: 11/11 verifier-valid mutants killed',))

    print('BUILD 253 GATE: PASS (shield-tilt removed + NPC copy ENTITY_DATA id fixed + 252/251 HnS/FA/TnM/crawl/login; actual game/NeoForge/visual NOT run; NPC TnM NOT supported)', flush=True)


if __name__ == '__main__':
    main()
