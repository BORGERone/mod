package net.minecraft.network.codec;

import net.minecraft.network.FriendlyByteBuf;

/** Build 376: the two vanilla codecs used by the door sync payload. */
public final class ByteBufCodecs {
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 = null;

    private ByteBufCodecs() {}
}
