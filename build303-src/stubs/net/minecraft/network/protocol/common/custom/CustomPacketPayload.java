package net.minecraft.network.protocol.common.custom;

/** Compile-time signature only (Build 351): the payload base the mod's own packets implement. */
public interface CustomPacketPayload {
    interface Type<T extends CustomPacketPayload> {
    }
}
