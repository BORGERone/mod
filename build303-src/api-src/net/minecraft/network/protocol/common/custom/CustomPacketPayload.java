package net.minecraft.network.protocol.common.custom;

import net.minecraft.resources.ResourceLocation;

/**
 * Build 376: a payload carries its own name and nothing else - the codec is bound at registration
 * (NeoForge 1.21.1, {@code PayloadRegistrar#playToClient}).
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
