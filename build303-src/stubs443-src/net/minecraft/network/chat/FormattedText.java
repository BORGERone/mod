package net.minecraft.network.chat;

import java.util.Optional;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public interface FormattedText {
    String getString();

    <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style);

    interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String text);
    }
}
