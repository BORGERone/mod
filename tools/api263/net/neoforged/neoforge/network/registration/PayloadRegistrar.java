package net.neoforged.neoforge.network.registration;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

public class PayloadRegistrar {
    public PayloadRegistrar optional() { return this; }
    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) { return this; }
}
