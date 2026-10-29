#!/usr/bin/env python3
import argparse, pathlib, subprocess, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--java', required=True, type=pathlib.Path)
    p.add_argument('--jar', required=True, type=pathlib.Path)
    a = p.parse_args()
    with tempfile.TemporaryDirectory(prefix='ui254-') as d:
        out = pathlib.Path(d)
        overridden = {'GraphUiTest.java', 'Font.java', 'GuiGraphics.java', 'PoseStack.java'}
        base_ui = [f for f in sorted((ROOT / 'tests/java246-ui').rglob('*.java')) if f.name not in overridden]
        base_graph = [f for f in sorted((ROOT / 'tests/java246-graph').rglob('*.java')) if f.name != 'GraphStatsTest.java']
        files = base_ui + base_graph + sorted((ROOT / 'tests/java254-ui').rglob('*.java'))
        subprocess.run(
            [str(a.java.resolve()), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
             '-21', '-g:none', '-nowarn', '-cp', str(a.jar.resolve()), '-d', str(out), *map(str, files)],
            check=True,
        )
        subprocess.run(
            [str(a.java.resolve()), '-Xshare:off', '-Xverify:all', '-cp', str(out) + ':' + str(a.jar.resolve()), 'GraphUiTest'],
            check=True,
        )
    print('Synthetic GUI and screen bridge; real NpcStatsGraphUi + NpcGraphCanvas.')


if __name__ == '__main__':
    main()
