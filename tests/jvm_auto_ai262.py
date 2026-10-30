#!/usr/bin/env python3
"""Build 262 runtime scenarios for persistent travel-facing and gradual torso idle scans."""
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
        lookNpc.setDeltaMovement(0.0, 0.0, 0.35);
        NpcAutoAiEngine255.runMobilityAndAutoAi(lookNpc);
        float stopBodyYaw = lookNpc.yBodyRot;
        ok(Math.abs(stopBodyYaw) < 1.0e-5,
                "body should face the travel vector before stopping; yaw=" + stopBodyYaw);
        lookNpc.setDeltaMovement(0.0, 0.0, 0.0);
        NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
        float firstIdleBodyStep = Math.abs(lookNpc.yBodyRot - stopBodyYaw);
        float firstIdleEntityStep = Math.abs(lookNpc.getYRot() - stopBodyYaw);
        ok(firstIdleBodyStep > 0.0f && firstIdleBodyStep <= 3.0f
                && firstIdleEntityStep > 0.0f && firstIdleEntityStep <= 3.0f,
                "first idle torso/entity step after stopping must be gradual; body="
                + firstIdleBodyStep + ", entity=" + firstIdleEntityStep);
        float minBody = lookNpc.yBodyRot;
        float maxBody = lookNpc.yBodyRot;
        float minHead = lookNpc.yHeadRot;
        float maxHead = lookNpc.yHeadRot;
        for (int i = 0; i < 420; i++) {
            NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
            minBody = Math.min(minBody, lookNpc.yBodyRot);
            maxBody = Math.max(maxBody, lookNpc.yBodyRot);
            minHead = Math.min(minHead, lookNpc.yHeadRot);
            maxHead = Math.max(maxHead, lookNpc.yHeadRot);
        }
        ok(maxBody - minBody > 20.0f && maxHead - minHead > 35.0f,
                "idle scan should randomly rotate torso and head; body=" + minBody + ".." + maxBody
                + ", head=" + minHead + ".." + maxHead);
        lookNpc.setTalking(true);
        float bodyBeforeTalk = lookNpc.yBodyRot;
        float headBeforeTalk = lookNpc.yHeadRot;
        NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
        ok(Math.abs(lookNpc.yBodyRot - bodyBeforeTalk) < 1.0e-5
                && Math.abs(lookNpc.yHeadRot - headBeforeTalk) < 1.0e-5,
                "idle torso and head scan must not override dialogue");
        lookNpc.setTalking(false);
        lookNpc.setPos(0.0, 64.0, 0.0);
        viewer.setPos(5.0, 64.0, 0.0);
        level.addEntity(viewer);
        lookProfile.lookAtPlayer = true;
        lookNpc.setProfile(lookProfile);
        float bodyBeforePlayerLook = lookNpc.yBodyRot;
        float headBeforePlayerLook = lookNpc.yHeadRot;
        NpcAutoAiEngine255.tickIdleLookAround(lookNpc);
        ok(Math.abs(lookNpc.yBodyRot - bodyBeforePlayerLook) < 1.0e-5
                && Math.abs(lookNpc.yHeadRot - headBeforePlayerLook) < 1.0e-5,
                "configured nearby player look must retain priority over randomized idle scan");
'''
assert scenario.count(needle) == 1
scenario = scenario.replace(needle, insert, 1)
base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java'] = scenario
if __name__ == '__main__':
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured):
        prior.build259.build258.previous.main()
    output = captured.getvalue().replace('build 257', 'build 262')
    output = output.replace('half calm movement', '10% faster calm movement')
    output = output.replace('PASS build 260: look toggle/dialogue/head-only',
                            'PASS build 262: travel-facing/idle torso/head scan/dialogue')
    print(output, end='')
    print('PASS build 262: smooth post-stop torso transition, randomized torso/head idle scan, dialogue priority')
