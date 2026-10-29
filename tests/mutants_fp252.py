#!/usr/bin/env python3
"""Verifier-valid negative controls (mutation suite) for Build 252 first-person HnS."""
import argparse, hashlib, pathlib, shutil, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import build_joml248, gen_api252
from jvm_fp_sword252 import SHA, compile_env, run_jar, sha

BRIDGE_REL = 'dev/settledlands/model/FirstPersonSwordBridge252.java'
ACCESS_REL = 'dev/settledlands/model/FirstPersonPalEmfAccess252.java'

MUTANTS = [
    (
        'no-renderer-hook',
        BRIDGE_REL,
        'if (disabled || event == null) return;',
        'if (true) return;',
        'attack:',
    ),
    (
        'skip-first-person-pass-check',
        ACCESS_REL,
        'if (context == null || !FirstPersonMode.isFirstPersonPass()) return null;',
        'if (context == null) return null;',
        'Non-FirstPersonPass full-body render (paperdoll) was hijacked',
    ),
    (
        'skip-camera-first-person-check',
        ACCESS_REL,
        'if (cameraType == null || !cameraType.isFirstPerson()) return null;',
        'if (cameraType == null) return null;',
        '3rd-person',
    ),
    (
        'skip-camera-entity-check',
        ACCESS_REL,
        'if (cameraEntity != null && cameraEntity != player) return null;',
        'if (false) return null;',
        'Freecam cameraEntity != player',
    ),
    (
        'skip-first-person-hand-check',
        ACCESS_REL,
        'if (state == null || root == null || !root.isMainModel || state.isFirstPersonHand()) return null;',
        'if (state == null || root == null || !root.isMainModel) return null;',
        'Ordinary FA first-person hand animation was overwritten',
    ),
    (
        'skip-root-identity-check',
        ACCESS_REL,
        'if (!(model instanceof IEMFModel emf) || !emf.emf$isEMFModel() || emf.emf$getEMFRootModel() != root) {',
        'if (!(model instanceof IEMFModel emf) || !emf.emf$isEMFModel()) {',
        'Private non-renderer EMFModelPartRoot',
    ),
    (
        'attack-layer-only',
        ACCESS_REL,
        'ResourceLocation.fromNamespaceAndPath("hackersandslashers", "action_layer"),',
        'ResourceLocation.fromNamespaceAndPath("hackersandslashers", "unused_layer"),',
        'layer:action_layer',
    ),
    (
        'capture-after-core-crouch-hook',
        ACCESS_REL,
        'if (hooks.isEmpty() || hooks.get(0) != PRE_HOOK) {\n            hooks.remove(PRE_HOOK);\n            hooks.add(0, PRE_HOOK);\n        }\n        if (hooks.get(hooks.size() - 1) != POST_HOOK) {\n            hooks.remove(POST_HOOK);\n            hooks.add(POST_HOOK);\n        }\n        return hooks.size() >= 2\n                && hooks.get(0) == PRE_HOOK\n                && hooks.get(hooks.size() - 1) == POST_HOOK;',
        'if (!hooks.contains(PRE_HOOK)) {\n            hooks.add(Math.min(1, hooks.size()), PRE_HOOK);\n        }\n        if (hooks.get(hooks.size() - 1) != POST_HOOK) {\n            hooks.remove(POST_HOOK);\n            hooks.add(POST_HOOK);\n        }\n        return hooks.size() >= 2;',
        'sneak-attack',
    ),
    (
        'no-rebracket-late-hook',
        ACCESS_REL,
        'if (hooks.get(hooks.size() - 1) != POST_HOOK) {\n            hooks.remove(POST_HOOK);\n            hooks.add(POST_HOOK);\n        }\n        return hooks.size() >= 2\n                && hooks.get(0) == PRE_HOOK\n                && hooks.get(hooks.size() - 1) == POST_HOOK;',
        'if (!hooks.contains(POST_HOOK)) {\n            hooks.add(POST_HOOK);\n        }\n        return hooks.size() >= 2;',
        'late-hook-rebracket',
    ),
    (
        'no-custom-jem-reset',
        ACCESS_REL,
        'custom.resetPose();',
        'if (false) custom.resetPose();',
        'EMF_right_arm',
    ),
]


def build_mutant_jar(java: pathlib.Path, base_252_jar: pathlib.Path, work: pathlib.Path,
                     joml: pathlib.Path, api: pathlib.Path,
                     rel_file: str, old_snip: str, new_snip: str) -> pathlib.Path:
    src_dir = work / 'src'
    shutil.copytree(ROOT / 'src/build252', src_dir)
    target = src_dir / rel_file
    txt = target.read_text(encoding='utf-8')
    assert txt.count(old_snip) == 1, f'Expected exact snippet in {rel_file}'
    target.write_text(txt.replace(old_snip, new_snip, 1), encoding='utf-8')

    classes_dir = work / 'classes'
    classes_dir.mkdir()
    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    sources = sorted(src_dir.rglob('*.java')) + sorted(api.rglob('*.java'))
    subprocess.run(
        [str(java), '-jar', str(compiler), '-21', '-encoding', 'UTF-8', '-g', '-nowarn',
         '-cp', str(joml), '-d', str(classes_dir), *map(str, sources)],
        check=True,
    )
    produced = {
        p.relative_to(classes_dir).as_posix(): p.read_bytes()
        for p in classes_dir.glob('dev/settledlands/model/FirstPerson*.class')
    }
    mut_jar = work / 'mutant.jar'
    with zipfile.ZipFile(base_252_jar, 'r') as zin, zipfile.ZipFile(mut_jar, 'w') as zout:
        for info in zin.infolist():
            data = produced.get(info.filename, zin.read(info.filename))
            zi = zipfile.ZipInfo(info.filename, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zout.writestr(zi, data)
    return mut_jar


def main():
    q = argparse.ArgumentParser()
    for flag in ('java', 'jar', 'pal', 'hns', 'gson-source', 'tnm', 'core', 'emf', 'etf'):
        q.add_argument('--' + flag, required=True, type=pathlib.Path)
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    pal = a.pal.resolve()
    hns = a.hns.resolve()
    gson_source = a.gson_source.resolve()
    tnm = a.tnm.resolve()
    core = a.core.resolve()
    emf = a.emf.resolve()
    etf = a.etf.resolve()
    for key in SHA:
        assert sha(locals()[key]) == SHA[key], key

    with tempfile.TemporaryDirectory(prefix='mutants252-') as d:
        tmp = pathlib.Path(d)
        out, autostub, gout, mocha, javassist, joml = compile_env(
            java, jar, pal, hns, gson_source, tnm, core, emf, etf, tmp
        )
        deps = (out, pal, core, emf, etf, tnm, autostub, gout, mocha, javassist, joml)
        # Positive control first
        ok_out = run_jar(java, jar, hns, deps, expect_broken=False)
        assert 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM' in ok_out, ok_out

        api = tmp / 'api252'
        gen_api252.main(api)

        cp_base = [out, emf, etf, core, tnm]
        cp_tail = [pal, hns, autostub, gout, mocha, javassist, joml]

        for name, rel_file, old_snip, new_snip, expected_marker in MUTANTS:
            mwork = tmp / ('m_' + name)
            mwork.mkdir()
            mjar = build_mutant_jar(java, jar, mwork, joml, api, rel_file, old_snip, new_snip)
            cp = ':'.join(map(str, [*cp_base, mjar, *cp_tail]))
            r = subprocess.run(
                [str(java), '-Xverify:all', '-cp', cp, 'testing.FirstPersonSwordScenario252', str(hns)],
                capture_output=True, text=True,
            )
            combined = r.stdout + '\n' + r.stderr
            assert r.returncode != 0, f'Mutant {name} SURVIVED!\n{combined}'
            assert 'VerifyError' not in combined and 'LinkageError' not in combined, (name, combined)
            assert expected_marker in combined, f'Mutant {name} failed for unexpected reason:\n{combined}'
            print(f'KILLED {name} ({expected_marker})', flush=True)

        print(f'PASS mutants_fp252: {len(MUTANTS)}/{len(MUTANTS)} verifier-valid mutants killed', flush=True)


if __name__ == '__main__':
    main()
