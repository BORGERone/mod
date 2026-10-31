package net.minecraft.network.codec;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface StreamCodec<B, V> {
    static <B, A, C, V> StreamCodec<B, V> composite(
            StreamCodec<? super B, A> firstCodec, Function<V, A> firstGetter,
            StreamCodec<? super B, C> secondCodec, Function<V, C> secondGetter,
            BiFunction<A, C, V> constructor) {
        return null;
    }
}
