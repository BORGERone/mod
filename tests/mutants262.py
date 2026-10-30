#!/usr/bin/env python3
"""Verifier-valid negative control for Build 262's post-stop torso idle scan."""
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
GUIDE = 'dev/settledlands/npc/GuideNpc'


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    java, jar, gson = a.java.resolve(), a.jar.resolve(), a.gson_source.resolve()
    with tempfile.TemporaryDirectory(prefix='sl262-mutants-') as td:
        tmp = pathlib.Path(td)
        mutant = tmp / 'idle-torso-noop.jar'
        with zipfile.ZipFile(jar) as source:
            files = {info.filename: source.read(info.filename) for info in source.infolist()}
            infos = source.infolist()
        files[ENGINE] = patcher.replace_code(
            files[ENGINE], 'tickIdleLookAround', f'(L{GUIDE};)V', b'\xb1', 0, 1
        )
        with zipfile.ZipFile(mutant, 'w') as target:
            for info in infos:
                target.writestr(info, files[info.filename])
        result = subprocess.run(
            [sys.executable, str(ROOT / 'tests/jvm_auto_ai262.py'), '--java', str(java),
             '--jar', str(mutant), '--gson-source', str(gson)],
            cwd=ROOT, capture_output=True, text=True,
        )
        combined = result.stdout + result.stderr
        expected = 'first idle torso/entity step after stopping must be gradual'
        assert result.returncode != 0 and 'AssertionError' in combined and 'VerifyError' not in combined, combined[-4000:]
        assert expected in combined, combined[-4000:]
        print('KILLED verifier-valid mutant: idle-torso-noop', flush=True)
    print('PASS Build 262 negative control (1 verifier-valid mutant killed)', flush=True)

if __name__ == '__main__':
    main()
