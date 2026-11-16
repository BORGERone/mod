package net.minecraft.world.level;

import net.minecraft.server.level.ServerLevel;

/**
 * Compile-time mirror (Build 425): {@code ServerLevelAccessor} returned by {@code MobSpawnEvent.getLevel()}.
 */
public interface ServerLevelAccessor extends LevelAccessor {
    ServerLevel getLevel();
}
