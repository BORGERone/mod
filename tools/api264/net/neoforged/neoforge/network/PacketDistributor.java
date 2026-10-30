package net.neoforged.neoforge.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    public static final List<CustomPacketPayload> SENT_TO_CLIENT = new ArrayList<>();
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload first,
                                    CustomPacketPayload... rest) {
        SENT_TO_CLIENT.add(first);
        for (CustomPacketPayload payload : rest) SENT_TO_CLIENT.add(payload);
    }
}
