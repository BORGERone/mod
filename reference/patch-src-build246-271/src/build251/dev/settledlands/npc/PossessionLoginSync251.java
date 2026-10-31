package dev.settledlands.npc;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Re-send server-authoritative appearance on login without changing possession,
 * effects, inventory or attributes. The existing 250 login handler returns
 * before its old sync block for both possessed and already-bodiless players.
 */
public final class PossessionLoginSync251 {
    private PossessionLoginSync251() {}

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) return;
        try {
            MinecraftServer server = joined.getServer();
            if (server == null) return;
            List<ServerPlayer> online = server.getPlayerList().getPlayers();
            UUID id = joined.getUUID();
            PossessionClientSync joinedState = snapshot(joined);
            boolean selfSent = false;
            for (ServerPlayer other : online) {
                if (other == null) continue;
                if (id.equals(other.getUUID())) {
                    try {
                        send(joined, joinedState);
                        selfSent = true;
                    } catch (RuntimeException | LinkageError error) {
                        warn(error);
                    }
                } else {
                    try {
                        // A blank state explicitly removes a stale client entry.
                        send(joined, snapshot(other));
                    } catch (RuntimeException | LinkageError error) {
                        warn(error);
                    }
                    try {
                        // Failure in one direction must not suppress the other.
                        send(other, joinedState);
                    } catch (RuntimeException | LinkageError error) {
                        warn(error);
                    }
                }
            }
            // Robust if an implementation fires login just before list insertion.
            if (!selfSent) send(joined, joinedState);
        } catch (RuntimeException | LinkageError error) {
            // The appearance sync must never abort the original login/mechanics.
            warn(error);
        }
    }

    private static PossessionClientSync snapshot(ServerPlayer player) {
        boolean possessed = PossessionSystem.isPossessed(player);
        return new PossessionClientSync(player.getUUID(),
                possessed ? PossessionSystem.possessModelKey(player) : "",
                possessed ? PossessionSystem.possessName(player) : "");
    }

    private static void send(ServerPlayer destination, PossessionClientSync state) {
        PacketDistributor.sendToPlayer(destination, state, new CustomPacketPayload[0]);
    }

    private static void warn(Throwable error) {
        System.err.println("[SettledLands] Could not resync possessed appearance at login: " + error);
    }
}
