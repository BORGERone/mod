package org.slf4j;

/** Compile-time shape of the logger the mod writes its bounded warnings to. */
public interface Logger {
    void warn(String message);

    void warn(String message, Object argument);
}
