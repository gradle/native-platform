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

package net.rubygrapefruit.platform.file

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions
import spock.lang.Requires

@Requires({ Platform.current().windows })
class WindowsFileEventsTest extends AbstractFileEventsTest {
    final WindowsFileEventFunctions fileEvents = Native.get(WindowsFileEventFunctions.class)
    FileWatcher watcher

    def "caches file events instance"() {
        expect:
        Native.get(WindowsFileEventFunctions.class) is fileEvents
    }

    @Override
    protected void startWatcher(File... roots) {
        // Avoid setup operations to be reported
        waitForChangeEventLatency()
        watcher = fileEvents.startWatching(roots*.absolutePath.toList(), callback)
    }

    @Override
    protected void stopWatcher() {
        watcher?.close()
    }

    @Override
    protected FileEvent resolveExpectedChange(FileWatcherCallback.Type type, File changedFile) {
        return new FileEvent(type, changedFile.canonicalFile)
    }

    @Override
    protected void waitForChangeEventLatency() {
        Thread.sleep(1000)
    }
}
