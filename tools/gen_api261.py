#!/usr/bin/env python3
"""Compile-only API additions for Build 261 idle gaze and Punchy first-person context."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_api260
import gen_api256

ENGINE_FILES = dict(gen_api260.FILES)
_entity = ENGINE_FILES['net/minecraft/world/entity/Entity.java']
assert 'public boolean onGround()' not in _entity
ENGINE_FILES['net/minecraft/world/entity/Entity.java'] = _entity.replace(
    '    public boolean isPassenger() { return false; }\n',
    '    public boolean isPassenger() { return false; }\n'
    '    public boolean onGround() { return true; }\n', 1
)
_level = ENGINE_FILES['net/minecraft/world/level/Level.java']
assert 'getNearestPlayer' not in _level
_level = _level.replace(
    'import net.minecraft.core.BlockPos;\n',
    'import net.minecraft.core.BlockPos;\n'
    'import net.minecraft.world.entity.Entity;\n'
    'import net.minecraft.world.entity.player.Player;\n', 1
)
ENGINE_FILES['net/minecraft/world/level/Level.java'] = _level.replace(
    '    public boolean isClientSide;\n',
    '    public boolean isClientSide;\n'
    '    public Player getNearestPlayer(Entity entity, double distance) { return null; }\n', 1
)
_mth = ENGINE_FILES['net/minecraft/util/Mth.java']
assert 'wrapDegrees' not in _mth
ENGINE_FILES['net/minecraft/util/Mth.java'] = _mth.replace(
    '    public static float rotLerp(float delta, float start, float end) { return 0; }\n',
    '    public static float rotLerp(float delta, float start, float end) { return 0; }\n'
    '    public static float wrapDegrees(float value) { return value; }\n', 1
)

BRIDGE_FILES = dict(gen_api256.SRC)
BRIDGE_FILES['net/minecraft/client/player/LocalPlayer.java'] = '''package net.minecraft.client.player;\npublic class LocalPlayer extends AbstractClientPlayer {}'''
_multi = BRIDGE_FILES['net/minecraft/client/renderer/MultiBufferSource.java']
assert 'class BufferSource' not in _multi
BRIDGE_FILES['net/minecraft/client/renderer/MultiBufferSource.java'] = _multi.replace(
    '    VertexConsumer getBuffer(RenderType type);\n',
    '    VertexConsumer getBuffer(RenderType type);\n'
    '    class BufferSource implements MultiBufferSource {\n'
    '        public VertexConsumer getBuffer(RenderType type) { return null; }\n'
    '    }\n', 1
)


def _write(files, out: Path):
    for rel, source in files.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')


def main_engine(out: Path):
    _write(ENGINE_FILES, out)


def main_bridge(out: Path):
    _write(BRIDGE_FILES, out)


def main(out: Path):
    main_engine(out)

if __name__ == '__main__':
    main(Path(sys.argv[1]))
