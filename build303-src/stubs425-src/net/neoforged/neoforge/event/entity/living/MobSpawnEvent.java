package net.neoforged.neoforge.event.entity.living;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.Event;

/**
 * Compile-time mirror (Build 425): {@code MobSpawnEvent.PositionCheck} matching NeoForge 21.1.x.
 */
public abstract class MobSpawnEvent extends Event {
    public ServerLevelAccessor getLevel() {
        return null;
    }

    public static class PositionCheck extends MobSpawnEvent {
        public MobSpawnType getSpawnType() {
            return null;
        }

        public void setResult(Result result) {}

        public enum Result {
            SUCCEED,
            DEFAULT,
            FAIL
        }
    }
}
