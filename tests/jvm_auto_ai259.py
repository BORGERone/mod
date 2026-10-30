#!/usr/bin/env python3
"""Build 259 NPC AI runtime scenarios, including combo and pace regression checks."""
import contextlib
import io
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_auto_ai258 as build258
base = build258.base

scenario = build258.base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java']
old_pace = 'navigationSpeedFactor(measured) - 1.0'
assert scenario.count(old_pace) == 1
scenario = scenario.replace(old_pace, 'navigationSpeedFactor(measured) - 1.1', 1)
needle = '''        ok(hostile.totalDamageTaken > before && hostile.totalDamageTaken >= 0.25f,
                "autonomous combat must land a melee hit against the hostile mob; damage=" + hostile.totalDamageTaken
                + " target=" + NpcAutoAiEngine255.of(guard).currentTarget()
                + " anim=" + guard.getCustomAnimName()
                + " dist=" + guard.distanceToSqr(hostile));
'''
assert scenario.count(needle) == 1
combo_check = needle + '''        String firstCombo = guard.getCustomAnimName();
        ok("orthodox_left_jab_right_cross".equals(firstCombo)
                || "orthodox_jab_jab_hook".equals(firstCombo),
                "combat should select an Ivy multi-hit combo, got " + firstCombo);
        float firstImpact = hostile.totalDamageTaken;
        for (int i = 0; i < 12; i++) tick(guard, step);
        ok(hostile.totalDamageTaken > firstImpact,
                "the selected Ivy combo should land its follow-up hit");
'''
scenario = scenario.replace(needle, combo_check, 1)
build258.base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java'] = scenario

if __name__ == '__main__':
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured):
        build258.previous.main()
    output = captured.getvalue().replace('build 257', 'build 259')
    output = output.replace('half calm movement', '10% faster calm movement')
    print(output, end='')
