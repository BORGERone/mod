package dev.settledlands.dungeon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Build 425: blocks passive/natural/chunk/patrol/structure/reinforcement mob spawns inside
 * {@code settledlands:hell_dungeon}, while preserving spawners, trial spawners, commands and scripted triggers.
 */
@EventBusSubscriber(modid = "settledlands")
public final class DungeonEvents425 {

    private DungeonEvents425() {}

    @SubscribeEvent
    public static void onMobSpawnPosition(MobSpawnEvent.PositionCheck event) {
        if (event == null) return;
        ServerLevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;
        if (!DungeonManager425.isInDungeon(level)) return;

        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL
                || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL
                || type == MobSpawnType.STRUCTURE
                || type == MobSpawnType.REINFORCEMENT) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
