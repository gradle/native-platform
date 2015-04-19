/*
 * Copyright 2012 Adam Murdoch
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.FileEvents;
import net.rubygrapefruit.platform.FileWatch;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.ResourceClosedException;
import net.rubygrapefruit.platform.internal.jni.FileEventFunctions;

import java.io.File;

public class DefaultFileEvents implements FileEvents {
    public FileWatch startWatch(final File target) throws NativeException {
        FunctionResult result = new FunctionResult();
        final Object handle = FileEventFunctions.createWatch(target.getAbsolutePath(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not watch for changes to %s: %s", target,
                    result.getMessage()));
        }

        return new DefaultFileWatch(handle, target);
    }

    private static class DefaultFileWatch implements FileWatch {
        private enum State {Watching, Closing, Closed}

        private final File target;
        // Protected by lock
        private final Object lock = new Object();
        private Object handle;
        private State state = State.Watching;

        public DefaultFileWatch(Object handle, File target) {
            this.handle = handle;
            this.target = target;
        }

        public void nextChange() {
            synchronized (lock) {
                if (state != State.Watching) {
                    throw new ResourceClosedException("This file watch has been closed.");
                }
            }
            FunctionResult result = new FunctionResult();
            boolean hasEvent = FileEventFunctions.waitForNextEvent(handle, result);
            if (result.isFailed()) {
                throw new NativeException(String.format("Could not receive next change to %s: %s", target,
                        result.getMessage()));
            }
            if (!hasEvent) {
                throw new ResourceClosedException(String.format("This file watch has been closed."));
            }
        }

        public void close() {
            synchronized (lock) {
                if (state != State.Watching) {
                    while (state != State.Closed) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return;
                }
                state = State.Closing;
            }

            try {
                FunctionResult result = new FunctionResult();
                FileEventFunctions.closeWatch(handle, result);
                if (result.isFailed()) {
                    throw new NativeException(String.format("Could cleanup watch handle for %s: %s", target,
                            result.getMessage()));
                }
            } finally {
                synchronized (lock) {
                    handle = null;
                    state = State.Closed;
                    lock.notifyAll();
                }
            }
        }
    }
}
