#!/usr/bin/env python3
"""Verifier-valid negative controls for the new Build 259 contracts."""
import argparse
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import classedit
import decode
import jvm_auto_ai259 as runtime
import patch_npc_stats246 as stats_patch
from patch_corpse_pose237 import constant_ref

ENGINE = 'dev/settledlands/npc/NpcAutoAiEngine255'
GUIDE = 'dev/settledlands/npc/GuideNpc'
SYNC_DESC = '(Ldev/settledlands/npc/GuideNpc;)V'
NAV_DESC = '(Ldev/settledlands/npc/GuideNpc;)D'


def change(raw: bytes, method: str, desc: str, update) -> bytes:
    k = classedit.Klass(raw)
    m = k.find_method(method, desc)
    assert m is not None, (method, desc)
    parts = classedit.code_parts(k, m)
    parts['code'] = update(k, bytearray(parts['code']))
    return k.dump(m, classedit.build_code_body(k, parts))


def mutate_entries(jar: pathlib.Path, kind: str) -> dict[str, bytes]:
    with zipfile.ZipFile(jar) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist()}

    if kind == 'stats-sync-disabled':
        files[ENGINE + '.class'] = stats_patch.replace_code(
            files[ENGINE + '.class'], 'syncStats', SYNC_DESC, b'\xb1', 0, 1
        )
    elif kind == 'legacy-priority-disabled':
        def revert(k, code):
            hits = [pc for pc, ins in decode.walk(code)
                    if len(ins) == 3 and ins[0] == 0xB8
                    and constant_ref(k, int.from_bytes(ins[1:3], 'big')) == (ENGINE, 'legacyAiMode',
                                                   '(Ldev/settledlands/npc/GuideNpc;)I')]
            assert len(hits) == 1, hits
            idx = k.add_methodref(GUIDE, 'aiMode', '()I')
            pc = hits[0]
            code[pc] = 0xB6  # invokevirtual, same operand stack and existing frame layout
            code[pc + 1:pc + 3] = idx.to_bytes(2, 'big')
            return bytes(code)
        files[GUIDE + '.class'] = change(files[GUIDE + '.class'],
                                          'customServerAiStep', '()V', revert)
    elif kind == 'short-wander-dwell':
        def shorten(k, code):
            hits = []
            for pc, ins in decode.walk(code):
                if ins[0] == 0x11:  # sipush
                    value = int.from_bytes(code[pc + 1:pc + 3], 'big', signed=True)
                    if value in (400, 501):
                        hits.append((pc, value))
            assert sorted(v for _, v in hits) == [400, 501], hits
            for pc, value in hits:
                code[pc + 1:pc + 3] = (40 if value == 400 else 81).to_bytes(2, 'big', signed=True)
            return bytes(code)
        files[ENGINE + '.class'] = change(files[ENGINE + '.class'],
                                           'stepWanderAndReturn', '(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcProfile;ZZ)V', shorten)
    elif kind == 'calm-speed-regressed':
        k = classedit.Klass(files[ENGINE + '.class'])
        one_idx = next((i for i, entry in enumerate(k.cp)
                        if entry and entry['tag'] == 6
                        and struct.unpack('>d', entry['raw'])[0] == 1.0), None)
        if one_idx is None:
            k.cp.append({'tag': 6, 'raw': struct.pack('>d', 1.0), 'start': None})
            one_idx = len(k.cp) - 1
            k.cp.append(None)
            k.cp_count = len(k.cp)
            k._dirty = True
        m = k.find_method('navigationSpeedFactor', NAV_DESC)
        parts = classedit.code_parts(k, m)
        code = bytearray(parts['code'])
        calm_loads = [pc for pc, ins in decode.walk(code)
                      if ins[0] == 0x14
                      and struct.unpack('>d', k.cp[int.from_bytes(ins[1:3], 'big')]['raw'])[0] == 0.55]
        assert len(calm_loads) == 1, calm_loads
        # Replace just the calm 0.5 factor with 1.0; offsets and StackMapTable remain intact.
        code[calm_loads[0] + 1:calm_loads[0] + 3] = one_idx.to_bytes(2, 'big')
        parts['code'] = bytes(code)
        files[ENGINE + '.class'] = k.dump(m, classedit.build_code_body(k, parts))
    else:
        raise ValueError(kind)
    return files


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--jar', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    java, jar, gson = a.java.resolve(), a.jar.resolve(), a.gson_source.resolve()
    kinds = ('stats-sync-disabled', 'calm-speed-regressed',
             'legacy-priority-disabled', 'short-wander-dwell')
    with tempfile.TemporaryDirectory(prefix='sl259-mutants-') as td:
        tmp = pathlib.Path(td)
        out, autostub = runtime.base.make_fixture(java, jar, gson, tmp)
        for kind in kinds:
            mutant = tmp / (kind + '.jar')
            entries = mutate_entries(jar, kind)
            with zipfile.ZipFile(jar) as src, zipfile.ZipFile(mutant, 'w', zipfile.ZIP_DEFLATED) as dst:
                for info in src.infolist():
                    dst.writestr(info, entries[info.filename])
            game = tmp / ('game-' + kind)
            game.mkdir()
            cp = ':'.join(map(str, (out, mutant, autostub)))
            result = subprocess.run([str(java), '-Xverify:all', '-cp', cp,
                                     'testing.AutoAi257Scenario', str(game)],
                                    capture_output=True, text=True)
            combined = result.stdout + result.stderr
            assert result.returncode != 0 and 'AssertionError' in combined and 'VerifyError' not in combined, (
                kind, result.returncode, result.stdout, result.stderr)
            print(f'KILLED verifier-valid mutant: {kind}', flush=True)
    print('PASS Build 259 negative controls (four verifier-valid mutants killed)', flush=True)


if __name__ == '__main__':
    main()
