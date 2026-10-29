#!/usr/bin/env python3
"""Release gate 252: full 251–237/HANDOFF §8 on exact bases, plus 252 first-person HnS regressions.

Genuine Java 21, BbModel, PAL 1.1.6, user-supplied HnS beta2.5 data + BladeTracker,
EMF Compat Core 2.3.2, Touch'n Motion 1.0.4, and EMF 3.3.12 components are exercised
with synthetic APIs/providers. This is NOT a full Minecraft/NeoForge launch or visual test.
No external assets or third-party mod JARs are distributed.
"""
import argparse, base64, hashlib, json, pathlib, subprocess, sys, tempfile, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit, gate_build237 as g237, gate_build242 as g242, gate_build247 as g247, gate_build249 as g249, gate_build250 as g250, gate_build251 as g251, patch_fp_hns252 as p

PY = sys.executable
SHA = '29a0e6e6245d9a906865a4020cf0f2c4532e3a488914260bf9d80df4e57dbc3d'
SIZE = 1627973
CORE_SHA = '7fbf5a1f7fc65c8adc29092aa08e212c4a43c6ff82d92af99f90f0066914e951'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = {n: b.read(n) for n in b.namelist()}
        new = {n: z.read(n) for n in z.namelist()}
    assert set(new) - set(old) == p.ADDED and not (set(old) - set(new))
    assert {n for n in old if old[n] != new[n]} == p.CHANGED
    assert old['settledlands.mixins.json'] == new['settledlands.mixins.json']
    assert old['settledlands.compat.mixins.json'] == new['settledlands.compat.mixins.json']
    for untouched in (
        'dev/settledlands/model/BbModel.class',
        'dev/settledlands/model/BbModelParser.class',
        'dev/settledlands/model/BbModelManager.class',
        'dev/settledlands/model/PlayerMotionBridge.class',
        'dev/settledlands/model/HackSlashAccess.class',
        'dev/settledlands/model/FreshEmfAccess.class',
        'dev/settledlands/npc/PossessionSystem.class',
        'dev/settledlands/npc/PossessionLoginSync251.class',
        'dev/settledlands/npc/client/GuideRenderer.class',
    ):
        assert old[untouched] == new[untouched], untouched

    rend_key = p.RENDERER + '.class'
    assert new[rend_key] == p.patch_renderer(old[rend_key])
    g242.compare_methods(old[rend_key], new[rend_key], changed={('onRenderPlayerPre', p.PRE_DESC)})
    rend_cls = classedit.Klass(new[rend_key])
    rend_refs = g242.refs(rend_cls, rend_cls.find_method('onRenderPlayerPre', p.PRE_DESC))
    assert rend_refs[0] == (p.FP_BRIDGE, 'onRenderPlayerPre', p.PRE_DESC), rend_refs[:4]
    # Bridge runs at offset 0 BEFORE spectator/invisible/possession/cameraType early returns
    assert any(r[1] == 'isSpectator' for r in rend_refs[1:])
    assert any(r[1] == 'isFirstPerson' for r in rend_refs[1:])

    # Ensure PlayerBodyRenderer + FirstPersonSwordBridge252 have no direct bytecode dependency
    # on ModList or EMF/PAL classes when optional mods are absent
    bridge_cls = classedit.Klass(new[p.FP_BRIDGE + '.class'])
    for m in bridge_cls.methods:
        if any(a['name'] == 'Code' for a in m['attrs']):
            for owner, _, _ in g242.refs(bridge_cls, m):
                assert not owner.startswith(('traben/', 'com/zigythebird/', 'net/neoforged/fml/ModList')), owner

    for name in p.ADDED | p.CHANGED:
        if name.endswith('.class'):
            assert classedit.Klass(new[name]).major == 65, name
    meta = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0]
    assert meta['version'] == p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    assert new['settledlands-patch-info.txt'].decode().startswith(p.PATCHINFO)
    print('OK exact 252 diff: single offset-0 hook in PlayerBodyRenderer.onRenderPlayerPre + isolated FP bridge; all 251 actor/crawl/login/NPC/gameplay classes byte-identical', flush=True)


def assets_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        names = [n for n in b.namelist() if n.startswith('assets/')]
        assert names and all(b.read(n) == z.read(n) for n in names), 'asset or UV data changed'
        model = json.loads(z.read('assets/settledlands/dragon_default/ivy.bbmodel'))
        texture = base64.b64decode(model['textures'][0]['source'].split(',', 1)[1], validate=True)
        assert texture == z.read('assets/settledlands/dragon_default/ivy.png')
        assert model['resolution'] == {'width': 128, 'height': 128} and len(model['animations']) == 82 and len(model['elements']) == 27
        assert digest(texture) == 'cdca7f12987915cb5b590a0060f1c4388c8bcc3ada28241ebcd237c1bf49e0ba'
    print('OK identical asset/UV/texture/parser/NPC renderer and 251 third-person pipelines', flush=True)


def main():
    q = argparse.ArgumentParser()
    for name in ('inputs', 'base245', 'base246', 'base247', 'base248', 'base249', 'base250', 'base251',
                 'java', 'jar', 'tnm', 'core', 'emf', 'etf', 'pal', 'hns', 'gson-source'):
        q.add_argument('--' + name, required=True, type=pathlib.Path)
    q.add_argument('--prevalidated-base-log', type=pathlib.Path,
                   help='use separately completed 251 gate from this workspace; omitted for independently reproducible full gate')
    a = q.parse_args()
    inputs = a.inputs.resolve()
    b245 = a.base245.resolve()
    b246 = a.base246.resolve()
    b247 = a.base247.resolve()
    b248 = a.base248.resolve()
    b249 = a.base249.resolve()
    b250 = a.base250.resolve()
    b251 = a.base251.resolve()
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
    assert digest(core.read_bytes()) == CORE_SHA
    assert jar.stat().st_size == SIZE and digest(jar.read_bytes()) == SHA, 'wrong 252 candidate'
    ver = subprocess.run([str(java), '-version'], capture_output=True, text=True)
    assert ver.returncode == 0 and 'version "21.' in ver.stderr, ver.stderr
    print(f'build252 experimental size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}', flush=True)

    if a.prevalidated_base_log:
        log = a.prevalidated_base_log.read_text(encoding='utf-8')
        assert 'BUILD 251 GATE: PASS' in log and g251.SHA in log
        print('OK separately completed full 251 gate incl. 250–237 / HANDOFF §8:', a.prevalidated_base_log, flush=True)
    else:
        g237.run(
            'complete 251 gate incl. 250–237 / HANDOFF §8 on the exact 251 base',
            [PY, ROOT / 'tests/gate_build251.py',
             '--inputs', inputs, '--base245', b245, '--base246', b246, '--base247', b247,
             '--base248', b248, '--base249', b249, '--base250', b250,
             '--java', java, '--jar', b251, '--tnm', tnm, '--emf', emf, '--etf', etf,
             '--pal', pal, '--hns', hns, '--gson-source', gson],
            contains=('BUILD 251 GATE: PASS',),
        )

    with tempfile.TemporaryDirectory(prefix='gate-build252-') as d:
        tmp = pathlib.Path(d)
        rebuilt = tmp / 'rebuild.jar'
        g237.run('252 deterministic rebuild', [PY, ROOT / 'tools/patch_fp_hns252.py', '--base', b251, '--out', rebuilt, '--java', java], contains=(SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('252 structural validator', [PY, ROOT / 'tools/validate_jar.py', jar, '--base', b251], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY, ROOT / 'tools/verify_jar.py']
        opts = ['--base', b251] + g237.option_entries('--expect-added', sorted(p.ADDED)) + g237.option_entries('--expect-changed', sorted(p.CHANGED))
        g237.run('252 CRC TOML locales mixins exact diff', verify + [jar] + opts, contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(b251, jar)
        assets_semantics(b251, jar)

        bad = tmp / 'crc.jar'
        bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('252 broken CRC rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        for label, missing in [('language', 'assets/settledlands/lang/ru_ru.json'), ('mixin', 'settledlands.mixins.json')]:
            bad = tmp / (label + '.jar')
            g237.alter_zip(jar, bad, remove={missing})
            g237.run('252 missing ' + label + ' rejected', verify + [bad], success=False, contains=('ПРОБЛЕМЫ',))
        bad = tmp / 'bridge.jar'
        g237.alter_zip(jar, bad, remove={p.FP_ACCESS + '$Snapshot.class'})
        g237.run('252 incomplete bridge rejected', verify + [bad] + opts, success=False, contains=('ПРОБЛЕМЫ',))
        bad = tmp / 'unhooked.jar'
        with zipfile.ZipFile(b251) as z:
            orig_rend = z.read(p.RENDERER + '.class')
        g237.alter_zip(jar, bad, replace={p.RENDERER + '.class': orig_rend})
        try:
            static_semantics(b251, bad)
        except AssertionError:
            print('OK missing renderer first-person bridge rejected', flush=True)
        else:
            raise AssertionError('missing renderer bridge not detected')

        targets = [
            'dev/settledlands/npc/client/PlayerBodyRenderer#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/FirstPersonSwordBridge252#onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent$Pre)',
            'dev/settledlands/model/FirstPersonPalEmfAccess252#ensureInstalled',
            'dev/settledlands/model/BbModel#getName',
            'dev/settledlands/model/HackSlashAccess#prepare(net.minecraft.client.player.AbstractClientPlayer)',
            'dev/settledlands/npc/PossessionLoginSync251#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
        ]
        out = g237.run('252 JVM verifies real renderer and FP HnS bridge declarations', [PY, ROOT / 'tools/jvm_verify.py', jar, *targets, '--java', java], contains=('ИТОГ: OK',))
        for target in targets:
            assert 'DECLARED ' + target.split('(')[0] in out, target

        g247.candidate_regressions(jar, inputs, java)
        g237.run('252 preserves 251 relog appearance, payload and renderer', [PY, ROOT / 'tests/jvm_login_model251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('PASS baseline 250 reproduces missing possessed model', 'PASS relog model, real payload codec/client handler/renderer'))
        g237.run('252 nine verifier-valid relog appearance mutants', [PY, ROOT / 'tests/mutants_login251.py', '--java', java, '--base250', b250, '--jar', jar], contains=('251 possessed model login behavioral mutants: PASS 9',))
        g237.run('252 preserves bodiless damage/milk', [PY, ROOT / 'tests/jvm_bodiless247.py', '--java', java, '--jar', jar], contains=('247 actual protection + 246 predicate', 'PASS'))
        g237.run('252 legacy bodiless mutants', [PY, ROOT / 'tests/mutants247.py', '--java', java, '--jar', jar], contains=('247 behavioral mutants: PASS 17',))
        g237.run('252 FA bridge regression and 1200 poses', [PY, ROOT / 'tests/jvm_fresh_motion250.py', '--java', java, '--jar', jar], contains=('51 scenarios + 1200 JOML poses PASS',))
        g237.run('252 inherited FA negative mutants', [PY, ROOT / 'tests/mutants_fresh251.py', '--java', java, '--jar', jar], contains=('250 FA behavioral mutants against 251: PASS 21',))
        g237.run('252 actual EMF root and TnM hook with controlled providers', [PY, ROOT / 'tests/jvm_real_emf_tnm250.py', '--java', java, '--jar', jar, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS actual EMF 3.3.12 root and TnM hook',))
        g237.run('252 actual actor, crawl, PAL, nested and rollback scenarios', [PY, ROOT / 'tests/jvm_motion251.py', '--java', java, '--jar', jar, '--pal', pal], contains=('PASS real BbModel/BbAnimation', '251 JVM scenarios PASS'))
        g237.run('252 37 calibrated crawl/PAL negative mutants', [PY, ROOT / 'tests/mutants251.py', '--java', java, '--jar', jar, '--pal', pal], contains=('251 behavioral mutants: PASS 37',))
        g237.run('252 HnS JSON through real PAL controller and item mixin', [PY, ROOT / 'tests/jvm_realhns251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames',))
        g237.run('252 four genuine HnS modifiers plus negative controls', [PY, ROOT / 'tests/jvm_hns_modifiers251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson], contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync', '4 negative controls'))
        g237.run('252 combined 3rd-person PAL + real EMF root/TnM hook with HnS frames', [PY, ROOT / 'tests/jvm_realhns_tnm251.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--emf', emf, '--etf', etf], contains=('PASS real HnS JSON 39 attacks / 195 sampled frames', 'actual EMF 3.3.12 root and TnM 1.0.4 hook'))
        g237.run('252 first-person HnS+PAL+EMF+Core+TnM + BladeTracker reach (plus 251 bug reproduction)', [PY, ROOT / 'tests/jvm_fp_sword252.py', '--java', java, '--jar', jar, '--base-jar', b251, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('CONFIRMED build 251 bug: pureMinDist=0.0', 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames, frontHits=14/14, 5 layers, sneak, unpossessed/invisible, slim+dualwield, negative checks'))
        g237.run('252 ten verifier-valid first-person HnS mutants', [PY, ROOT / 'tests/mutants_fp252.py', '--java', java, '--jar', jar, '--pal', pal, '--hns', hns, '--gson-source', gson, '--tnm', tnm, '--core', core, '--emf', emf, '--etf', etf], contains=('PASS mutants_fp252: 10/10 verifier-valid mutants killed',))

    print('BUILD 252 GATE: PASS (experimental first-person HnS + 251 crawl/3rd-person/login; actual game/NeoForge/visual NOT run; NPC TnM NOT supported)', flush=True)


if __name__ == '__main__':
    main()
