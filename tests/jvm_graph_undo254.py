#!/usr/bin/env python3
"""Actual screen history and bridges (spinner + randomize undo/redo) for Build 254."""
import argparse, pathlib, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import jvm_graph_undo246 as u246, patch_npc_stats246 as p


def main():
    q = argparse.ArgumentParser()
    q.add_argument('--java', required=True, type=pathlib.Path)
    q.add_argument('--jar', required=True, type=pathlib.Path)
    a = q.parse_args()
    with tempfile.TemporaryDirectory(prefix='undo254-') as d:
        out = pathlib.Path(d)
        overridden = {'GraphStatsTest.java', 'GraphUiTest.java', 'GraphUndoTest.java', 'Font.java', 'GuiGraphics.java', 'PoseStack.java'}
        base_ui = [f for f in sorted((ROOT / 'tests/java246-ui').rglob('*.java')) if f.name not in overridden]
        base_graph = [f for f in sorted((ROOT / 'tests/java246-graph').rglob('*.java')) if f.name not in overridden]
        base_undo = [f for f in sorted((ROOT / 'tests/java246-undo').rglob('*.java')) if f.name not in overridden]
        ui254 = [f for f in sorted((ROOT / 'tests/java254-ui').rglob('*.java')) if f.name != 'GraphUiTest.java']
        files = base_ui + base_graph + base_undo + ui254 + [ROOT / 'tests/java254-undo/GraphUndoTest.java']
        subprocess.run(
            [str(a.java.resolve()), '-jar', str(ROOT / 'tools/vendor/ecj-3.46.100.jar'),
             '-21', '-g:none', '-nowarn', '-cp', str(a.jar.resolve()), '-d', str(out), *map(str, files)],
            check=True,
        )
        (out / (p.SCREEN + '.class')).write_bytes(u246.screen_subset(a.jar))
        subprocess.run(
            [str(a.java.resolve()), '-Xshare:off', '-Xverify:all', '-cp', str(out) + ':' + str(a.jar.resolve()), 'GraphUndoTest'],
            check=True,
        )
    print('Synthetic GUI, initial fields and widget rebuild; real NpcDialogueGraphScreen history.')


if __name__ == '__main__':
    main()
