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

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions
import spock.lang.Specification

import java.util.concurrent.TimeUnit

abstract class AbstractFileEventFunctionsTest extends Specification {
    enum FileWatcherFixture {
        MAC_OS(){
            private static final int LATENCY_IN_MILLIS = 0

            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                Native.get(OsxFileEventFunctions.class).startWatcher(
                    LATENCY_IN_MILLIS, TimeUnit.MILLISECONDS,
                    callback
                )
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(LATENCY_IN_MILLIS + 20)
            }
        },
        LINUX(){
            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                // Avoid setup operations to be reported
                waitForChangeEventLatency()
                Native.get(LinuxFileEventFunctions.class).startWatcher(callback)
            }

            @Override
            void waitForChangeEventLatency() {
                TimeUnit.MILLISECONDS.sleep(50)
            }
        },
        WINDOWS(){
            @Override
            FileWatcher startNewWatcher(FileWatcherCallback callback) {
                Native.get(WindowsFileEventFunctions.class).startWatcher(callback)
            }

            @Override
            void waitForChangeEventLatency() {
                Thread.sleep(50)
            }
        }

        static FileWatcherFixture of(Platform platform) {
            if (platform.macOs) {
                return MAC_OS
            } else if (platform.linux) {
                return LINUX
            } else if (platform.windows) {
                return WINDOWS
            } else {
                throw new IllegalArgumentException("Unsupported platform: " + platform)
            }
        }

        abstract FileWatcher startNewWatcher(FileWatcherCallback callback)

        abstract void waitForChangeEventLatency()
    }

    // We could do this with @Delegate, but Groovy doesn't let us :(
    private FileWatcherFixture watcherFixture

    def setup() {
        watcherFixture = FileWatcherFixture.of(Platform.current())
    }

    protected FileWatcher startNewWatcher(FileWatcherCallback callback) {
        watcherFixture.startNewWatcher(callback)
    }

    protected void waitForChangeEventLatency() {
        watcherFixture.waitForChangeEventLatency()
    }
}
