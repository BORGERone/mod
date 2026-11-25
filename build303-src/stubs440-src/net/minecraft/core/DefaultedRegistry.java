package net.minecraft.core;

import java.util.Collections;
import java.util.Iterator;

/** Compile-time mirror (Build 440): keeps interface kind and exposes iterator(). */
public interface DefaultedRegistry<T> extends Registry<T>, Iterable<T> {
    @Override
    default Iterator<T> iterator() {
        return Collections.emptyIterator();
    }
}
