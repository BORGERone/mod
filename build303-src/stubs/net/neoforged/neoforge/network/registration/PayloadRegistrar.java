package net.neoforged.neoforge.network.registration;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Build 376: {@code registrar("1").playToClient(type, codec, handler)}. */
public class PayloadRegistrar {
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
        return this;
    }

    /**
     * Build 377: the other direction. The base mod registers its own client-to-server payloads with this
     * call ({@code CutsceneCamExitPayload}, {@code CutsceneRecTogglePayload}).
     */
    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
        return this;
    }
}
