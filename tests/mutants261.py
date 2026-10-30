#!/usr/bin/env python3
"""Verifier-valid negative controls for idle gaze and Punchy first-person context."""
import argparse
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import patch_npc_stats246 as patcher

ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255.class'
FP = 'dev/settledlands/npc/client/FirstPersonPossession256.class'
GUIDE = 'dev/settledlands/npc/GuideNpc'
MULTI = 'net/minecraft/client/renderer/MultiBufferSource'


def mutate(jar: pathlib.Path, out: pathlib.Path, case: str) -> None:
    with zipfile.ZipFile(jar) as source:
        files = {info.filename: source.read(info.filename) for info in source.infolist()}
        infos = source.infolist()
    if case == 'idle-scan-noop':
        files[ENGINE] = patcher.replace_code(
            files[ENGINE], 'tickIdleLookAround', f'(L{GUIDE};)V', b'\xb1', 0, 1
        )
    elif case == 'firstperson-context-noop':
        files[FP] = patcher.replace_code(
            files[FP], 'pushFirstPersonRenderContext',
            f'(Lnet/minecraft/client/player/AbstractClientPlayer;L{MULTI};I)V',
            b'\xb1', 0, 4
        )
    else:
        raise ValueError(case)
    with zipfile.ZipFile(out, 'w') as target:
        for info in infos:
            target.writestr(info, files[info.filename])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    java, jar, gson = a.java.resolve(), a.jar.resolve(), a.gson_source.resolve()
    cases = (
        ('idle-scan-noop', 'jvm_auto_ai261.py', 'idle look-around should smoothly scan'),
        ('firstperson-context-noop', 'jvm_punchy_fp261.py', 'Punchy PlayerModel first-person pass must replace Steve'),
    )
    with tempfile.TemporaryDirectory(prefix='sl261-mutants-') as td:
        tmp = pathlib.Path(td)
        for case, fixture, expected in cases:
            mutant = tmp / (case + '.jar')
            mutate(jar, mutant, case)
            result = subprocess.run(
                [sys.executable, str(ROOT / 'tests' / fixture), '--java', str(java),
                 '--jar', str(mutant), '--gson-source', str(gson)],
                cwd=ROOT, capture_output=True, text=True,
            )
            combined = result.stdout + result.stderr
            assert result.returncode != 0 and 'AssertionError' in combined and 'VerifyError' not in combined, (
                case, result.returncode, combined[-4000:])
            assert expected in combined, (case, expected, combined[-4000:])
            print(f'KILLED verifier-valid mutant: {case}', flush=True)
    print('PASS Build 261 negative controls (2 verifier-valid mutants killed)', flush=True)

if __name__ == '__main__':
    main()
