package net.minecraft.core;

/**
 * Compile-time mirror (Build 449): in MC 1.21.1 {@code Holder.Reference} is a concrete class
 * implementing {@code Holder} (not an interface), so {@code SoundEvents.UI_BUTTON_CLICK.value()}
 * must compile to {@code invokevirtual} rather than {@code invokeinterface}.
 */
public interface Holder<T> {
    T value();

    class Reference<T> implements Holder<T> {
        @Override
        public T value() {
            return null;
        }
    }
}
