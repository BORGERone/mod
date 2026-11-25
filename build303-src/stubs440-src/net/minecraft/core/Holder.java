package net.minecraft.core;

/** Compile-time mirror (Build 440): exposes Reference inner type verified in base JAR. */
public interface Holder<T> {
    T value();

    interface Reference<T> extends Holder<T> {
    }
}
