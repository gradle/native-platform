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
import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions
import spock.lang.Requires

@Requires({ Platform.current().windows })
class WindowsFileEventFunctionsTest extends AbstractFileEventsTest {
    final WindowsFileEventFunctions fileEvents = Native.get(WindowsFileEventFunctions.class)

    def "caches file events instance"() {
        expect:
        Native.get(WindowsFileEventFunctions.class) is fileEvents
    }

    // TODO Add test for watching file
    // TODO Promote test to AbstractFileEventsTest
    def "fails when registering watch for non-existent directory"() {
        given:
        def missingDirectory = new File(rootDir, "missing")

        when:
        startWatcher(missingDirectory)

        then:
        def ex = thrown NativeException
        ex.message == "Couldn't get file handle, error = 2: " + missingDirectory.absolutePath
    }

    @Override
    protected FileWatcher startNewWatcher(FileWatcherCallback callback) {
        fileEvents.startWatcher(callback)
    }

    @Override
    protected void waitForChangeEventLatency() {
        Thread.sleep(50)
    }
}
