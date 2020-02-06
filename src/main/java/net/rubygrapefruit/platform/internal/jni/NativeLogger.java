package net.rubygrapefruit.platform.internal.jni;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NativeLogger {
    private static Logger LOGGER;

    enum LogLevel {
        FINEST(Level.FINEST),
        FINER(Level.FINER),
        FINE(Level.FINE),
        CONFIG(Level.CONFIG),
        INFO(Level.INFO),
        WARNING(Level.WARNING),
        SEVERE(Level.SEVERE);

        private final Level delegate;

        LogLevel(Level delegate) {
            this.delegate = delegate;
        }

        Level getLevel() {
            return delegate;
        }
    }

    public static void initLogging(Class<?> loggerClass) {
        Logger logger = Logger.getLogger(loggerClass.getName());
        Level effectiveLevel = Level.FINEST; // getEffectiveLevel(logger);

        for (LogLevel logLevel : LogLevel.values()) {
            if (logLevel.getLevel().equals(effectiveLevel)) {
                LOGGER = logger;
                initLogging(logLevel.ordinal());
                return;
            }
        }
        throw new AssertionError("Invalid log level for " + loggerClass + ": " + effectiveLevel);
    }

    private static Level getEffectiveLevel(Logger logger) {
        Logger effectiveLogger = logger;
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
        return effectiveLevel;
    }

    private static native void initLogging(int logLevel);

    // Used from native
    @SuppressWarnings("unused")
    public static void log(int level, String message) {
        LOGGER.log(LogLevel.values()[level].getLevel(), message);
    }
}
