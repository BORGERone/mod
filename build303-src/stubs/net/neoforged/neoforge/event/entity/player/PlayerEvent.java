package net.neoforged.neoforge.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Build 376: only the login event is mirrored - that is when a client is told which doors are locked. */
public class PlayerEvent extends Event {
    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }
}
