package net.minecraft.network.chat;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;

/** Compile-time mirror (Build 443): mirrors MutableComponent descriptors already present in base JAR. */
public class MutableComponent implements Component {
    private final String s;

    public MutableComponent(String s) {
        this.s = s;
    }

    @Override
    public String getString() {
        return s;
    }

    public MutableComponent append(Component sibling) {
        return this;
    }

    public MutableComponent append(String text) {
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> updater) {
        return this;
    }

    public MutableComponent withStyle(ChatFormatting formatting) {
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formattings) {
        return this;
    }
}
