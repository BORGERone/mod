package dev.settledlands.npc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client transition options for a dialogue cutscene node. */
public record CutsceneFadePayload263(boolean fadeOut, boolean fadeIn) implements CustomPacketPayload {
    public static final Type<CutsceneFadePayload263> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("settledlands", "cutscene_fade_263"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneFadePayload263> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CutsceneFadePayload263::fadeOut,
                    ByteBufCodecs.BOOL, CutsceneFadePayload263::fadeIn,
                    CutsceneFadePayload263::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, CODEC, (payload, context) ->
                context.enqueueWork(() -> applyOnClient(payload.fadeOut(), payload.fadeIn())));
    }

    /** Avoid a hard common-to-client class link on dedicated servers. */
    private static void applyOnClient(boolean fadeOut, boolean fadeIn) {
        try {
            Class<?> overlay = Class.forName("dev.settledlands.npc.client.CutsceneFadeOverlay263", true,
                    CutsceneFadePayload263.class.getClassLoader());
            overlay.getMethod("begin", boolean.class, boolean.class).invoke(null, fadeOut, fadeIn);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not apply client cutscene fade", exception);
        }
    }
}
