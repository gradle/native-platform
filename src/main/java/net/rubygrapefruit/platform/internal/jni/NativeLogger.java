package net.rubygrapefruit.platform.internal.jni;

import java.util.logging.Level;
import java.util.logging.Logger;

// Used from native
@SuppressWarnings("unused")
public class NativeLogger {
    static final Logger LOGGER = Logger.getLogger(NativeLogger.class.getName());

    enum LogLevel {
        ALL(Level.ALL),
        FINEST(Level.FINEST),
        FINER(Level.FINER),
        FINE(Level.FINE),
        CONFIG(Level.CONFIG),
        INFO(Level.INFO),
        WARNING(Level.WARNING),
        SEVERE(Level.SEVERE),
        OFF(Level.OFF);

        private final Level delegate;

        LogLevel(Level delegate) {
            this.delegate = delegate;
        }

        Level getLevel() {
            return delegate;
        }
    }

    public static void log(int level, String message) {
        LOGGER.log(LogLevel.values()[level].getLevel(), message);
    }

    public static int getLogLevel() {
        Logger effectiveLogger = LOGGER;
        Level effectiveLevel;
        while (true) {
            effectiveLevel = effectiveLogger.getLevel();
            if (effectiveLevel != null) {
                break;
            }
            effectiveLogger = effectiveLogger.getParent();
            if (effectiveLogger == null) {
                throw new AssertionError("Effective log level is not set");
            }
        }

        for (LogLevel logLevel : LogLevel.values()) {
            if (logLevel.getLevel().equals(effectiveLevel)) {
                return logLevel.ordinal();
            }
        }
        throw new AssertionError("Unknown effective log level found: " + effectiveLevel);
    }
}
