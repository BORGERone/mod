#!/usr/bin/env python3
"""Run historical 251/252 HnS regression harnesses with either private beta2.5-bettercombat-test
(0e1e54fc...) or public 2.0-beta2.5 (f759036b...) without modifying historical 251/252 scripts."""
import hashlib, importlib, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]

ALLOWED_HNS_SHAS = {
    '0e1e54fc03adcd59bd1d46f821d42788bc8d9757cd8f41ed118cfc30c241a7bb',
    'f759036b3fd8e4572789d4b93911d9bfd58bf797d70e61a228b81a153a1f5aba',
}


def main():
    assert len(sys.argv) >= 2, 'usage: run_hns_compat254.py <script.py> [args...]'
    script = pathlib.Path(sys.argv[1]).stem
    args = sys.argv[2:]
    assert '--hns' in args, '--hns required'
    hns_path = pathlib.Path(args[args.index('--hns') + 1]).resolve()
    hns_sha = hashlib.sha256(hns_path.read_bytes()).hexdigest()
    assert hns_sha in ALLOWED_HNS_SHAS, f'unexpected HnS SHA-256: {hns_sha}'

    for mod_name in ('jvm_realhns251', 'jvm_hns_modifiers251', 'jvm_realhns_tnm251', 'jvm_fp_sword252'):
        mod = importlib.import_module(mod_name)
        if hasattr(mod, 'HNS_SHA'):
            mod.HNS_SHA = hns_sha
        if hasattr(mod, 'SHA') and isinstance(mod.SHA, dict) and 'hns' in mod.SHA:
            mod.SHA['hns'] = hns_sha

    target = importlib.import_module(script)
    sys.argv = [str(ROOT / 'tests' / (script + '.py')), *args]
    target.main()


if __name__ == '__main__':
    main()
