package dev.settledlands.audio;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record PlayAudioPayload(
    String file,
    double originX,
    double originY,
    double originZ,
    float radius,
    float volume,
    boolean loop,
    float fadeSec,
    float pitch,
    boolean stop
) implements CustomPacketPayload {
    public static final Type<PlayAudioPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "play_audio"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayAudioPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.file, 256);
            buf.writeDouble(p.originX);
            buf.writeDouble(p.originY);
            buf.writeDouble(p.originZ);
            buf.writeFloat(p.radius);
            buf.writeFloat(p.volume);
            buf.writeBoolean(p.loop);
            buf.writeFloat(p.fadeSec);
            buf.writeFloat(p.pitch);
            buf.writeBoolean(p.stop);
        },
        buf -> new PlayAudioPayload(
            buf.readUtf(256),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .optional()
            .playToClient(TYPE, CODEC, (payload, ctx) -> ctx.enqueueWork(() -> ClientAudioService.handlePacket(payload)));
    }
}
