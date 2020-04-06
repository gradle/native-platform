/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.rubygrapefruit.platform.internal.jni.fileevents;

import java.util.logging.Level;
import java.util.logging.Logger;

// Used from native
@SuppressWarnings("unused")
public class NativeLogger {
    private static final Logger LOGGER = Logger.getLogger(NativeLogger.class.getName());

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
