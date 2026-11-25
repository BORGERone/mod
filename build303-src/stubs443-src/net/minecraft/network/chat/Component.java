package net.minecraft.network.chat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Compile-time mirror (Build 443): extends Build 440 Component with FormattedText & visit(). */
public interface Component extends FormattedText {
    static MutableComponent translatable(String s) {
        return new MutableComponent(s);
    }

    static MutableComponent translatable(String s, Object... args) {
        return new MutableComponent(s);
    }

    static MutableComponent literal(String s) {
        return new MutableComponent(s);
    }

    static MutableComponent empty() {
        return new MutableComponent("");
    }

    @Override
    String getString();

    default Style getStyle() {
        return Style.EMPTY;
    }

    default List<Component> getSiblings() {
        return Collections.emptyList();
    }

    @Override
    default <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
        return Optional.empty();
    }
}
