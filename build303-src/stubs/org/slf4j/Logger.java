package org.slf4j;

/** Compile-time shape of the logger the mod writes its bounded warnings to. */
public interface Logger {
    void info(String message);

    void info(String message, Object argument);

    void warn(String message);

    void warn(String message, Object argument);

    void warn(String message, Object first, Object second);

    void warn(String message, Object... arguments);
}
