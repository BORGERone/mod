package org.slf4j;
public interface Logger {
 default void warn(String msg, Throwable t) {}
 default void warn(String msg, Object... args) {}
 default void warn(String msg, Object a, Object b) {}
 default void warn(String msg) {}
 default void info(String msg, Object... args) {}
 default void info(String msg) {}
 default void error(String msg, Throwable t) {}
 default void error(String msg, Object... args) {}
 default void error(String msg, Object a, Object b) {}
 default void error(String msg) {}
 default void debug(String msg, Object... args) {}
}
