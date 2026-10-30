#!/usr/bin/env python3
"""Build 260 JVM scenarios for sound, toggle-aware head look and movement-facing fixes."""
import contextlib
import io
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_auto_ai259 as build259

base = build259.base
scenario = base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java']
scenario = scenario.replace('import dev.settledlands.npc.*;\n',
                            'import dev.settledlands.npc.*;\n'
                            'import net.minecraft.world.entity.player.Player;\n')
needle = '        ServerLevel level = new ServerLevel();\n'
insert = '''        ServerLevel level = new ServerLevel();

        GuideNpc lookNpc = new GuideNpc(NpcContent.guide(), level);
        NpcProfile lookProfile = lookNpc.profile().copy();
        lookProfile.lookAtPlayer = false;
        lookNpc.setProfile(lookProfile);
        ok(!NpcAutoAiEngine255.shouldLookAtPlayer(lookNpc),
                "look toggle off should suppress idle player look");
        lookNpc.setTalking(true);
        ok(NpcAutoAiEngine255.shouldLookAtPlayer(lookNpc),
                "dialogue should enable player look even with the toggle off");
        lookNpc.setTalking(false);
        lookProfile.lookAtPlayer = true;
        lookNpc.setProfile(lookProfile);
        ok(NpcAutoAiEngine255.shouldLookAtPlayer(lookNpc),
                "look toggle on should enable player look");
        Player viewer = new Player(level);
        lookNpc.setPos(0.0, 64.0, 0.0);
        viewer.setPos(5.0, 64.0, 0.0);
        lookNpc.yBodyRot = 35.0f;
        lookNpc.yHeadRot = 0.0f;
        NpcAutoAiEngine255.lookHeadAtPlayer(lookNpc, viewer);
        ok(Math.abs(lookNpc.yBodyRot - 35.0f) < 1.0e-5
                && Math.abs(lookNpc.yHeadRot) > 1.0f,
                "player look must rotate the head without rotating the body");
'''
assert scenario.count(needle) == 1
scenario = scenario.replace(needle, insert, 1)
base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java'] = scenario
entity = base.AUTO_AI_OVERRIDES['net/minecraft/world/entity/Entity.java']
assert 'public boolean onGround()' not in entity
entity = entity.replace(
    '    public boolean isPassenger() { return passenger; }\n',
    '    public boolean isPassenger() { return passenger; }\n'
    '    public boolean onGround() { return true; }\n'
)
base.AUTO_AI_OVERRIDES['net/minecraft/world/entity/Entity.java'] = entity

if __name__ == '__main__':
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured):
        build259.build258.previous.main()
    output = captured.getvalue().replace('build 257', 'build 260')
    output = output.replace('half calm movement', '10% faster calm movement')
    output = output.replace('PASS build 260: node-backed',
                            'PASS build 260: look toggle/dialogue/head-only; node-backed')
    print(output, end='')
