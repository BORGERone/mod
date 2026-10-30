#!/usr/bin/env python3
"""Build 261 runtime scenarios for idle gaze plus inherited Auto-AI regressions."""
import contextlib
import io
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_auto_ai260 as prior

base = prior.build259.base
scenario = base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java']
needle = '''        Player viewer = new Player(level);
'''
insert = '''        Player viewer = new Player(level);
        lookProfile.lookAtPlayer = false;
        lookNpc.setProfile(lookProfile);
        lookNpc.yBodyRot = 35.0f;
        lookNpc.yHeadRot = 35.0f;
        float minHead = Float.POSITIVE_INFINITY;
        float maxHead = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 420; i++) {
            NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
            minHead = Math.min(minHead, lookNpc.yHeadRot);
            maxHead = Math.max(maxHead, lookNpc.yHeadRot);
        }
        ok(Math.abs(lookNpc.yBodyRot - 35.0f) < 1.0e-5
                && maxHead - minHead > 35.0f,
                "idle look-around should smoothly scan the head without rotating the body; range="
                + minHead + ".." + maxHead);
        lookNpc.setTalking(true);
        float headBeforeTalk = lookNpc.yHeadRot;
        NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
        ok(Math.abs(lookNpc.yHeadRot - headBeforeTalk) < 1.0e-5,
                "idle scan must not override dialogue head look");
        lookNpc.setTalking(false);
'''
assert scenario.count(needle) == 1
scenario = scenario.replace(needle, insert, 1)
base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java'] = scenario
if __name__ == '__main__':
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured):
        prior.build259.build258.previous.main()
    output = captured.getvalue().replace('build 257', 'build 261')
    output = output.replace('half calm movement', '10% faster calm movement')
    output = output.replace('PASS build 260: look toggle/dialogue/head-only',
                            'PASS build 261: idle look/head-only/dialogue')
    print(output, end='')
    print('PASS build 261: smooth randomized head-only idle scan, stable body yaw, dialogue priority')
