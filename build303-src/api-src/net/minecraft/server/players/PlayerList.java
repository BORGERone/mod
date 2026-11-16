package net.minecraft.server.players;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Compile-time mirror (Build 352): {@code getPlayers} is what the base jar already iterates.
 *
 * <p>Build 396 adds {@code getPlayer(UUID)}, which the base jar itself calls -
 * {@code GuideDialogue}, line 119 and 226 of its decompiled source: {@code getPlayerList().getPlayer(id)}.
 * So the shape is proven by a jar that runs in game, not by memory.</p>
 */
public class PlayerList {
    public List<ServerPlayer> getPlayers() { return null; }

    /** The player with this id, or null when he is not on the server. */
    public ServerPlayer getPlayer(UUID id) { return null; }
}
