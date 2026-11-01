package net.minecraft.world.level;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Compile-time mirror (Build 320): the earlier mirror plus the two members the radius zone needs.
 * Both are already called by the base jar - {@code GuideNpc#customServerAiStep} looks for a nearby
 * player with exactly this signature.
 */
public class Level {
    public boolean isClientSide;

    public boolean hasChunkAt(BlockPos pos) { return true; }

    public long getGameTime() { return 0; }

    public Player getNearestPlayer(Entity entity, double distance) { return null; }

    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box,
            Predicate<? super T> test) {
        return null;
    }
}
