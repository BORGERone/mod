#!/usr/bin/env python3
"""Java 21 runtime regression for the exact Build 263 fade overlay class."""
import argparse
import hashlib
import pathlib
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ECJ_SHA = '74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'
API = sorted((ROOT / 'tools/api263').rglob('*.java'))
FIXTURES = [
    ROOT / 'tests/fixture263/dev/settledlands/npc/NpcDialogueGraph.java',
    ROOT / 'tests/fixture263/dev/settledlands/npc/client/CutsceneCameraClient.java',
    ROOT / 'tests/fixture263/dev/settledlands/npc/CutsceneStorage.java',
    ROOT / 'tests/fixture263/net/minecraft/server/MinecraftServer.java',
    ROOT / 'tests/fixture263/net/minecraft/server/level/ServerPlayer.java',
    ROOT / 'tests/fixture263/net/minecraft/network/FriendlyByteBuf.java',
    ROOT / 'tests/fixture263/net/minecraft/network/RegistryFriendlyByteBuf.java',
]


def run(command: list[str], label: str) -> None:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(f'{label} failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', required=True, type=pathlib.Path)
    parser.add_argument('--jar', required=True, type=pathlib.Path)
    args = parser.parse_args()
    java, jar = args.java.resolve(), args.jar.resolve()
    ecj = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    assert hashlib.sha256(ecj.read_bytes()).hexdigest() == ECJ_SHA
    assert java.is_file() and jar.is_file()

    with tempfile.TemporaryDirectory(prefix='sl263-fade-jvm-') as td:
        root = pathlib.Path(td)
        classes = root / 'classes'
        classes.mkdir()
        with zipfile.ZipFile(jar) as archive:
            for entry in (
                'dev/settledlands/npc/client/CutsceneFadeOverlay263.class',
                'dev/settledlands/npc/CutsceneFadeGraphData263.class',
                'dev/settledlands/npc/CutsceneFadeNodeAccess263.class',
                'dev/settledlands/npc/CutsceneFadeControl263.class',
                'dev/settledlands/npc/CutsceneFadeControl263$SceneFade.class',
                'dev/settledlands/npc/CutsceneFadePayload263.class',
            ):
                target = classes / entry
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))

        run([str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
             '-cp', str(classes), '-d', str(classes), *map(str, API), *map(str, FIXTURES)],
            'compile minimal APIs and cutscene graph/network runtime fixtures')
        probes = [
            ROOT / 'tests/CutsceneFadeOverlayProbe263.java',
            ROOT / 'tests/CutsceneFadeGraphDataProbe263.java',
            ROOT / 'tests/CutsceneFadeControlProbe263.java',
        ]
        run([str(java), '-jar', str(ecj), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn',
             '-cp', str(classes), '-d', str(classes), *map(str, probes)], 'compile fade regression probes')
        for probe in ('CutsceneFadeOverlayProbe263', 'CutsceneFadeGraphDataProbe263', 'CutsceneFadeControlProbe263'):
            run([str(java), '-Xverify:all', '-cp', str(classes), probe],
                f'run exact Build 263 classes under full JVM bytecode verification: {probe}')
    print('OK fade phases, graph NBT/copy, independent legacy flags, and initial/chained scene ordering')


if __name__ == '__main__':
    main()
