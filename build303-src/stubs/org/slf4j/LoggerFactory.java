package org.slf4j;

/**
 * Compile-time shape of the logger factory; the game provides the real one.
 *
 * <p>Build 342 made this return a silent logger instead of {@code null}, so a harness can exercise code
 * that reports - the bounded warnings of a bridge, for instance - without a real logger on the class
 * path. Nothing here is packaged: the game's own slf4j is what the mod writes to.</p>
 */
public final class LoggerFactory {

    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void info(String message, Object argument) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Object argument) {
        }

        @Override
        public void warn(String message, Object first, Object second) {
        }

        @Override
        public void warn(String message, Object... arguments) {
        }
    };

    private LoggerFactory() {
    }

    public static Logger getLogger(String name) {
        return SILENT;
    }
}
