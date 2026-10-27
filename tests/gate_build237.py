#!/usr/bin/env python3
"""Full pre-release gate for build 237; no compiler or Minecraft required.

Example (from repo root):
    python3 tests/gate_build237.py --inputs /tmp/settledlands-236-gate \
      --java /path/to/java21 --jar settledlands-0.8.0-build237-corpse-pose.jar

The JVM checks use synthetic Minecraft/NeoForge stand-ins, NOT a game run.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))
import patch_probe233  # noqa: E402

PY = sys.executable
EXPECTED_SHA = '9abf34da8a566d882e4bb7a4561a93e019aa4c5a58461ec1e54fc51039e58c45'
NAMES = {
    230: 'settledlands-0.8.0-build230-modelfix.jar',
    232: 'settledlands-0.8.0-build232-earlytex2.jar',
    233: 'settledlands-0.8.0-build233-probe.jar',
    234: 'settledlands-0.8.0-build234-probe.jar',
    235: 'settledlands-0.8.0-build235-probe.jar',
    236: 'settledlands-0.8.0-build236-corpse-tex.jar',
}
ADDED232 = ['dev/settledlands/diag/TexProbe.class', 'dev/settledlands/mixin/CorpseRenderProbeMixin.class']
CHANGED232 = ['META-INF/neoforge.mods.toml', 'dev/settledlands/npc/CorpseLifecycle.class',
              'dev/settledlands/npc/GuideCorpseNpc.class', 'dev/settledlands/npc/GuideNpc.class',
              'dev/settledlands/npc/PlayerCorpsePossession.class', 'settledlands-patch-info.txt']
CHANGED236 = ['META-INF/neoforge.mods.toml', 'dev/settledlands/npc/CorpseLifecycle.class',
              'dev/settledlands/npc/GuideCorpseNpc.class', 'dev/settledlands/npc/PlayerCorpsePossession.class',
              'settledlands-patch-info.txt', 'settledlands.mixins.json']
GUIDE = 'dev/settledlands/npc/GuideNpc#applyProfile'
PROBE = 'dev/settledlands/diag/TexProbe#logObj(java.lang.String,java.lang.Object)'
TARGETS = [GUIDE,
           'dev/settledlands/npc/GuideCorpseNpc#readAdditionalSaveData(net.minecraft.nbt.CompoundTag)',
           'dev/settledlands/npc/CorpseLifecycle#onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent)',
           'dev/settledlands/npc/PlayerCorpsePossession#dressBodyCorpse(dev.settledlands.npc.GuideCorpseNpc,dev.settledlands.npc.PlayerCorpsePossession$PendingBody)']


def run(label, args, success=True, contains=()):
    p = subprocess.run([str(x) for x in args], cwd=ROOT, capture_output=True, text=True)
    out = p.stdout + p.stderr
    okay = ((p.returncode == 0) if success else (p.returncode != 0)) and all(x in out for x in contains)
    if not okay:
        print(f'FAIL {label}: return code {p.returncode}\n{out}', file=sys.stderr)
        raise SystemExit(1)
    print('OK', label)
    return out


def option_entries(flag, names):
    return [arg for name in names for arg in (flag, name)]


def corrupt_crc(data):
    """Modify central-directory CRC for a nonessential file, keeping zip readable."""
    buf = bytearray(data)
    with tempfile.NamedTemporaryFile() as t:
        t.write(buf); t.flush()
        with zipfile.ZipFile(t.name) as z:
            offset = z.start_dir
    while buf[offset:offset + 4] == b'PK\x01\x02':
        name_len, extra_len, comment_len = struct.unpack_from('<HHH', buf, offset + 28)
        name = bytes(buf[offset + 46:offset + 46 + name_len]).decode('utf-8')
        if name == 'settledlands-patch-info.txt':
            crc = struct.unpack_from('<I', buf, offset + 16)[0]
            struct.pack_into('<I', buf, offset + 16, crc ^ 1)
            return bytes(buf)
        offset += 46 + name_len + extra_len + comment_len
    raise ValueError('could not find central directory entry to corrupt')


def alter_zip(source, dest, remove=(), replace=None):
    replace = replace or {}
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(dest, 'w') as new:
        for info in old.infolist():
            if info.filename not in remove:
                new.writestr(info, replace.get(info.filename, old.read(info.filename)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    args = ap.parse_args()
    source = {n: args.inputs / name for n, name in NAMES.items()}
    jar = args.jar.resolve()
    java = args.java.resolve()
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA:
        raise SystemExit(f'wrong candidate SHA: {digest}')
    print(f'build237 size={jar.stat().st_size} sha256={digest}')
    version = subprocess.run([str(java), '-version'], text=True, capture_output=True)
    if version.returncode or 'version "21.' not in version.stderr:
        raise SystemExit('Java 21 required; got ' + version.stderr)
    print('OK Java 21:', version.stderr.splitlines()[0])

    with tempfile.TemporaryDirectory(prefix='gate-build237-') as td:
        tmp = pathlib.Path(td)
        rebuilt = tmp / 'rebuilt.jar'
        run('deterministic build', [PY, TOOLS / 'patch_corpse_pose237.py', '--base', source[236],
                                    '--base235', source[235], '--out', rebuilt])
        assert rebuilt.read_bytes() == jar.read_bytes(), 'rebuilt jar differs'

        validate = [PY, TOOLS / 'validate_jar.py']
        run('validator control build 232', validate + [source[232]], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        run('validator build 237 vs 232', validate + [jar, '--base', source[232]], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        run('validator build 237 vs 236', validate + [jar, '--base', source[236]], contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        for n in (233, 234):
            run(f'validator negative build {n}', validate + [source[n]], success=False, contains=('ОШИБКИ',))

        verify = [PY, TOOLS / 'verify_jar.py']
        run('general checks and exact diff vs 232', verify + [jar, '--base', source[232]] +
            option_entries('--expect-added', ADDED232) + option_entries('--expect-changed', CHANGED232),
            contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        run('general checks and exact diff vs 236', verify + [jar, '--base', source[236]] +
            option_entries('--expect-changed', CHANGED236) + ['--marker', '[SettledLands][corpse] model={} tex={}'],
            contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        run('diff allowlist negative', verify + [jar, '--base', source[236], '--expect-changed', 'fake.class'],
            success=False, contains=('НЕОЖИДАННЫЙ ДИФФ',))

        bad_crc = tmp / 'bad-crc.jar'; bad_crc.write_bytes(corrupt_crc(jar.read_bytes()))
        run('CRC negative', verify + [bad_crc], success=False, contains=('ПРОБЛЕМЫ: целостность',))
        lang = 'assets/settledlands/lang/ru_ru.json'
        missing = tmp / 'missing-lang.jar'; alter_zip(jar, missing, remove={lang})
        run('missing language negative', verify + [missing], success=False, contains=('ПРОБЛЕМЫ: языки',))
        invalid = tmp / 'invalid-lang.jar'; alter_zip(jar, invalid, replace={lang: b'{invalid'})
        run('invalid language negative', verify + [invalid], success=False, contains=('ПРОБЛЕМЫ: языки',))
        with zipfile.ZipFile(jar) as z:
            ru = json.loads(z.read(lang))
        ru.pop(next(iter(ru)))
        mismatch = tmp / 'mismatched-lang.jar'; alter_zip(jar, mismatch, replace={lang: json.dumps(ru).encode()})
        run('language keys negative', verify + [mismatch], success=False, contains=('ПРОБЛЕМЫ: языки',))
        nomixin = tmp / 'missing-mixin.jar'; alter_zip(jar, nomixin, remove={'settledlands.mixins.json'})
        run('missing mixin config negative', verify + [nomixin], success=False, contains=('ПРОБЛЕМЫ: mixins',))

        with zipfile.ZipFile(jar) as z:
            assert z.read('dev/settledlands/diag/TexProbe.class') == patch_probe233.build_probe_class(with_main=False)[0]
            mixins = json.loads(z.read('settledlands.mixins.json'))
            assert 'CorpseRenderProbeMixin' not in mixins['client']
            for name in ('EarlyCorpseTextureMixin', 'CorpseRenderProbeMixin'):
                path = tmp / (name + '.class')
                path.write_bytes(z.read('dev/settledlands/mixin/' + name + '.class'))
                run('custom mixin ' + name, [PY, TOOLS / 'check_mixin_class.py', path], contains=('OK',))
        print('OK probe generator equals shipped class; per-frame probe disabled')
        run('real JVM probe self-test', [PY, TOOLS / 'selftest_probe.py', '--java', java], contains=('JVM-САМОТЕСТ: OK',))

        jvm = [PY, TOOLS / 'jvm_verify.py']
        for n, target in ((230, GUIDE), (234, PROBE)):
            run(f'JVM verifier negative build {n}', jvm + [source[n], target, '--java', java],
                success=False, contains=('VerifyError', 'ИТОГ: ПРОВАЛ'))
        for n, target in ((232, GUIDE), (236, GUIDE), (235, PROBE)):
            run(f'JVM verifier positive build {n}', jvm + [source[n], target, '--java', java],
                contains=('DECLARED ', 'ИТОГ: OK'))
        run('JVM verifier missing target negative', jvm + [jar, GUIDE,
            'dev/settledlands/npc/GuideNpc#noSuchMethod', '--java', java],
            success=False, contains=('target method with parameters not in class',))
        run('JVM verifier cannot reach JVM negative', jvm + [jar, GUIDE, '--java', '/bin/false'],
            success=False, contains=('ИТОГ: НЕ ПРОВЕРЕНО',))
        for target in TARGETS:
            run('JVM verifier ' + target.split('#')[1].split('(')[0], jvm + [jar, target, '--java', java],
                contains=('DECLARED ', 'ИТОГ: OK'))

        for fixture in ('jvm_corpse_load.py', 'jvm_corpse_spawn.py', 'jvm_corpse_dress.py', 'jvm_corpse_realprofile.py'):
            run('executable JVM behavior ' + fixture,
                [PY, ROOT / 'tests' / fixture, '--java', java, '--base236', source[236], '--jar', jar],
                contains=('236 negative control', '237 JVM execution'))
    print('BUILD 237 GATE: PASS (synthetic dependencies; not an in-game test)')


if __name__ == '__main__':
    main()
