package net.minecraft.network.codec;

import java.util.function.Function;

/**
 * Build 376: only the one-component {@code composite} overload is mirrored - that is the whole of what
 * a door sync payload needs, and every extra overload written here would be a signature to get wrong.
 */
public interface StreamCodec<B, V> {
    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec, Function<C, T1> getter,
            Function<T1, C> factory) {
        return null;
    }
}
