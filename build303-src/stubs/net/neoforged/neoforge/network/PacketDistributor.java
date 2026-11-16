package net.neoforged.neoforge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Build 376: the two ways this mod sends a payload. */
public final class PacketDistributor {
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {}

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {}

    /**
     * Build 377: client to server. The base mod's own payloads send themselves with exactly this call
     * ({@code GuideNet}, {@code CutsceneRecTogglePayload}).
     */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {}

    private PacketDistributor() {}
}
