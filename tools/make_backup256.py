#!/usr/bin/env python3
"""Deterministic independent build 256 backup. User data and external mod JARs excluded."""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tests'))
import gate_build256 as gate

EXCLUDED = {
    'GATE-RESULT-256.txt',
    'GATE-BASE-RESULT-256.txt',
    'GATE-BACKUP-RESULT-256.txt',
    'SHA256SUMS-build256.txt',
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    args = ap.parse_args()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
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
        'src/build254', 'src/build255', 'src/build256',
    ):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(
                part.startswith('.') or part == '__pycache__'
                for part in path.relative_to(ROOT).parts
            ) and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())

    for name in (
        'ivy_oleander.bbmodel',
        'settledlands-0.8.0-build245-body-wear.jar',
        'settledlands-0.8.0-build246-npc-characteristics.jar',
        'settledlands-0.8.0-build247-bodiless-protection.jar',
        'settledlands-0.8.0-build248-touchmotion-player.jar',
        'settledlands-0.8.0-build249-player-motion.jar',
        'settledlands-0.8.0-build250-fresh-motion.jar',
        'settledlands-0.8.0-build251-crawl-hns.jar',
        'settledlands-0.8.0-build252-fp-hns.jar',
        'settledlands-0.8.0-build253-shield-copy.jar',
        'settledlands-0.8.0-build254-ui-ivy.jar',
        'settledlands-0.8.0-build255-auto-ai.jar',
        'settledlands-0.8.0-build256-npc-fp-corpse.jar',
        'SettledLands-Build228-SceneFix-Source.zip',
    ):
        add(ROOT / name, name)

    for name in sorted(set(gate.g255.g247.g246.NAMES.values()) | set(gate.g255.g237.NAMES.values())):
        add(args.inputs / name, 'test-inputs/' + name)

    # Exact ancestry and bundled model checks
    assert digest(files['ivy_oleander.bbmodel']) == gate.g254.p.IVY_OLEANDER_SHA
    assert digest(files['settledlands-0.8.0-build245-body-wear.jar']) == gate.g247.g246.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build246-npc-characteristics.jar']) == gate.g247.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build247-bodiless-protection.jar']) == gate.g250.p.BASE_SHA
    assert digest(files['settledlands-0.8.0-build248-touchmotion-player.jar']) == gate.g249.g248.SHA
    assert digest(files['settledlands-0.8.0-build249-player-motion.jar']) == gate.g249.SHA
    assert digest(files['settledlands-0.8.0-build250-fresh-motion.jar']) == gate.g250.SHA
    assert digest(files['settledlands-0.8.0-build251-crawl-hns.jar']) == gate.g251.SHA
    assert digest(files['settledlands-0.8.0-build252-fp-hns.jar']) == gate.g252.SHA
    assert digest(files['settledlands-0.8.0-build253-shield-copy.jar']) == gate.g253.SHA
    assert digest(files['settledlands-0.8.0-build254-ui-ivy.jar']) == gate.g254.SHA
    assert digest(files['settledlands-0.8.0-build255-auto-ai.jar']) == gate.g255.SHA
    assert digest(files['settledlands-0.8.0-build256-npc-fp-corpse.jar']) == gate.SHA
    assert digest(files['tools/vendor/ecj-3.46.100.jar']) == gate.g255.g247.g246.p.ECJ_SHA
    assert digest(files['tools/vendor/joml-1.10.5-source.tar.gz']) == gate.g255.g253.p.build_joml248.SHA
    assert {'AI-GUIDE-SettledLands.md', 'tools/vendor/LICENSE-JOML.txt', 'BACKUP-256-RU.txt'} <= files.keys()

    forbidden = (
        'hackersandslashers', 'playeranimlib', 'playeranimationlib',
        'touch_n_motion', 'entity_model_features', 'entity_texture_features', 'emf_compat',
    )
    assert not any(
        n.lower().endswith('.jar') and any(term in n.lower() for term in forbidden)
        for n in files
    )
    assert not any(
        n.lower().endswith(('.log', '.tar.gz'))
        and ('gson' in n.lower() or n.lower().endswith('.log'))
        for n in files
    )

    files['BACKUP-MANIFEST.sha256'] = ''.join(
        digest(files[n]) + '  ' + n + '\n' for n in sorted(files)
    ).encode('utf8')
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.out, 'w') as zf:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            zf.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(args.out) as zf:
        assert zf.testzip() is None and len(zf.namelist()) == len(files)
    print(f'{args.out}: {args.out.stat().st_size} bytes sha256 {digest(args.out.read_bytes())}; files={len(files)}')


if __name__ == '__main__':
    main()
