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
package net.rubygrapefruit.platform.internal.jni

import net.rubygrapefruit.platform.file.FileWatchEvent

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class TestFileEventFunctions extends AbstractFileEventFunctions<TestFileEventFunctions.TestFileWatcher> {

    @Override
    WatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        new WatcherBuilder(eventQueue)
    }

    static class TestFileWatcher extends AbstractFileEventFunctions.AbstractFileWatcher {
        enum Command {
            FAIL, TERMINATE
        }

        private final BlockingQueue<Command> commands = new LinkedBlockingQueue<Command>()

        TestFileWatcher(AbstractFileEventFunctions.NativeFileWatcherCallback callback) {
            super(callback)
        }

        @Override
        protected void initializeRunLoop() {
        }

        @Override
        protected void executeRunLoop() {
            while (true) {
                switch (commands.take()) {
                    case Command.FAIL:
                        throw new RuntimeException("Error")
                    case Command.TERMINATE:
                        return
                }
            }
        }

        void injectFailureIntoRunLoop() {
            commands.put(Command.FAIL)
        }

        @Override
        protected void doShutdown() {
            commands.put(Command.TERMINATE)
        }

        @Override
        protected boolean awaitTermination(long timeoutInMillis) {
            return true;
        }

        @Override
        protected void doStartWatching(Collection<File> paths) {
            throw new UnsupportedOperationException()
        }

        @Override
        protected boolean doStopWatching(Collection<File> paths) {
            throw new UnsupportedOperationException()
        }
    }

    static class WatcherBuilder extends AbstractFileEventFunctions.AbstractWatcherBuilder<TestFileWatcher> {
        WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue)
        }

        @Override
        protected TestFileWatcher createWatcher(AbstractFileEventFunctions.NativeFileWatcherCallback callback) {
            new TestFileWatcher(callback)
        }
    }
}
