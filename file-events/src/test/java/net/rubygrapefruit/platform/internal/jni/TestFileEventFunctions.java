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
package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.file.FileWatchEvent;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class TestFileEventFunctions extends AbstractFileEventFunctions<TestFileEventFunctions.TestFileWatcher> {

    @Override
    public WatcherBuilder newWatcher(BlockingQueue<FileWatchEvent> eventQueue) {
        return new WatcherBuilder(eventQueue);
    }

    public static class TestFileWatcher extends AbstractFileWatcher {
        private final CountDownLatch running = new CountDownLatch(1);

        public TestFileWatcher(NativeFileWatcherCallback callback) {
            super(callback);
        }

        @Override
        protected void initializeRunLoop() {
        }

        @Override
        protected void executeRunLoop() {
            try {
                running.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void doShutdown() {
            running.countDown();
        }

        @Override
        protected boolean awaitTermination(long timeoutInMillis) {
            return true;
        }

        @Override
        protected void doStartWatching(Collection<File> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean doStopWatching(Collection<File> paths) {
            throw new UnsupportedOperationException();
        }
    }

    public static class WatcherBuilder extends AbstractWatcherBuilder<TestFileWatcher> {
        WatcherBuilder(BlockingQueue<FileWatchEvent> eventQueue) {
            super(eventQueue);
        }

        @Override
        protected TestFileWatcher createWatcher(NativeFileWatcherCallback callback) {
            return new TestFileWatcher(callback);
        }
    }
}
