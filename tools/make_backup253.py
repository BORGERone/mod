#!/usr/bin/env python3
"""Deterministic independent 253 backup. User data and external mod JARs excluded."""
import argparse, hashlib, pathlib, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
import gate_build253 as gate

EXCLUDED = {
    'GATE-RESULT-253.txt',
    'GATE-BASE-RESULT-253.txt',
    'GATE-BACKUP-RESULT-253.txt',
    'SHA256SUMS-build253.txt',
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--inputs', required=True, type=pathlib.Path)
    p.add_argument('--out', required=True, type=pathlib.Path)
    a = p.parse_args()
    files = {}

    def add(path: pathlib.Path, name: str):
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    for glob in ('*.md', '*.txt'):
        for path in sorted(ROOT.glob(glob)):
            if path.is_file() and path.name not in EXCLUDED:
                add(path, path.name)
    for folder in (
        'tools', 'tests',
        'src/build246', 'src/build247', 'src/build248', 'src/build249',
        'src/build250', 'src/build251', 'src/build252', 'src/build253',
    ):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(
                part.startswith('.') or part == '__pycache__' for part in path.relative_to(ROOT).parts
            ) and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())
    for name in (
        'settledlands-0.8.0-build245-body-wear.jar',
        'settledlands-0.8.0-build246-npc-characteristics.jar',
        'settledlands-0.8.0-build247-bodiless-protection.jar',
        'settledlands-0.8.0-build248-touchmotion-player.jar',
        'settledlands-0.8.0-build249-player-motion.jar',
        'settledlands-0.8.0-build250-fresh-motion.jar',
        'settledlands-0.8.0-build251-crawl-hns.jar',
        'settledlands-0.8.0-build252-fp-hns.jar',
        'settledlands-0.8.0-build253-shield-copy.jar',
        'SettledLands-Build228-SceneFix-Source.zip',
    ):
        add(ROOT / name, name)
    for name in sorted(set(gate.g247.g246.NAMES.values()) | set(gate.g237.NAMES.values())):
        add(a.inputs / name, 'test-inputs/' + name)

    assert digest(files['settledlands-0.8.0-build245-body-wear.jar']) == gate.g247.g246.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build246-npc-characteristics.jar']) == gate.g247.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build247-bodiless-protection.jar']) == gate.g250.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build248-touchmotion-player.jar']) == gate.g249.g248.SHA
    assert digest(files['settledlands-0.8.0-build249-player-motion.jar']) == gate.g249.SHA
    assert digest(files['settledlands-0.8.0-build250-fresh-motion.jar']) == gate.g250.SHA
    assert digest(files['settledlands-0.8.0-build251-crawl-hns.jar']) == gate.g251.SHA
    assert digest(files['settledlands-0.8.0-build252-fp-hns.jar']) == gate.g252.SHA
    assert digest(files['settledlands-0.8.0-build253-shield-copy.jar']) == gate.SHA
    assert digest(files['tools/vendor/ecj-3.46.100.jar']) == gate.g247.g246.p.ECJ_SHA
    assert digest(files['tools/vendor/joml-1.10.5-source.tar.gz']) == gate.p.build_joml248.SHA
    assert {'AI-GUIDE-SettledLands.md', 'tools/vendor/LICENSE-JOML.txt', 'BACKUP-253-RU.txt'} <= files.keys()
    assert not any(
        n.lower().endswith('.jar') and any(x in n.lower() for x in (
            'hackersandslashers', 'playeranimlib', 'playeranimationlib',
            'touch_n_motion', 'entity_model_features', 'entity_texture_features', 'emf_compat'
        )) for n in files
    )
    assert not any(n.lower().endswith(('.log', '.tar.gz')) and ('gson' in n.lower() or n.lower().endswith('.log')) for n in files)

    files['BACKUP-MANIFEST.sha256'] = ''.join(digest(files[n]) + '  ' + n + '\n' for n in sorted(files)).encode('utf8')
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.out, 'w') as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(files)
    print(f'{a.out}: {a.out.stat().st_size} bytes sha256 {digest(a.out.read_bytes())}; files={len(files)}')


if __name__ == '__main__':
    main()
