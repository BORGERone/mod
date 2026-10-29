#!/usr/bin/env python3
"""Run all 32 Build 246 behavioral mutants against Build 254 via the 254 graph/UI/undo fixtures."""
import argparse, pathlib, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import mutants246 as m246

FIXTURE_MAP = {
    'jvm_graph_stats246.py': 'jvm_graph_stats254.py',
    'jvm_graph_ui246.py': 'jvm_graph_ui254.py',
    'jvm_graph_undo246.py': 'jvm_graph_undo254.py',
}


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    q.add_argument('--only')
    a = q.parse_args()
    selected = [x for x in m246.CASES if not a.only or x[0] == a.only]
    assert selected
    with tempfile.TemporaryDirectory(prefix='mutants246-on-254-') as d:
        for mode, fixture, signal in selected:
            mapped = FIXTURE_MAP.get(fixture, fixture)
            bad = pathlib.Path(d) / (mode + '.jar')
            m246.mutate(a.jar, bad, mode)
            result = subprocess.run(
                [sys.executable, str(ROOT / 'tests' / mapped), '--java', str(a.java.resolve()), '--jar', str(bad)],
                capture_output=True, text=True,
            )
            out = result.stdout + result.stderr
            if result.returncode == 0 or 'AssertionError' not in out or signal not in out or 'VerifyError' in out or 'ClassFormatError' in out:
                raise AssertionError(f'uncalibrated 246 mutant {mode} on 254: rc={result.returncode}; expected {signal!r}\n{out}')
            print('OK rejected verifier-valid 246 mutant on 254:', mode, flush=True)
    print('246 behavioral mutants against 254: PASS', len(selected))


if __name__ == '__main__':
    main()
