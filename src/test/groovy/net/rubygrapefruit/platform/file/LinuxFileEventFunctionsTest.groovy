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
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions
import spock.lang.Ignore
import spock.lang.Requires

import java.util.concurrent.TimeUnit

@Requires({ Platform.current().linux })
class LinuxFileEventFunctionsTest extends AbstractFileEventsTest {
    final LinuxFileEventFunctions fileEvents = Native.get(LinuxFileEventFunctions.class)

    def "caches file events instance"() {
        expect:
        Native.get(LinuxFileEventFunctions.class) is fileEvents
    }

    @Override
    protected FileWatcher startNewWatcher(FileWatcherCallback callback) {
        // Avoid setup operations to be reported
        waitForChangeEventLatency()
        fileEvents.startWatcher(callback)
    }

    @Override
    protected void waitForChangeEventLatency() {
        TimeUnit.MILLISECONDS.sleep(50)
    }

    @Override
    protected void stopWatcher() {
        super.stopWatcher()
    }

    @Ignore("The behavior doesn't seem consistent across Linux variants")
    // Sometimes we get the same watch descriptor back when registering the watch with a different path,
    // other times not, but freeing the resulting watchers leads to errors
    def "fails when watching same directory both directly and via symlink"() {
        given:
        def canonicalDir = new File(rootDir, "watchedDir")
        canonicalDir.mkdirs()
        def linkedDir = new File(rootDir, "linked")
        java.nio.file.Files.createSymbolicLink(linkedDir.toPath(), canonicalDir.toPath())

        when:
        startWatcher(canonicalDir, linkedDir)

        then:
        def ex = thrown NativeException
        ex.message == "Already watching path: ${linkedDir.absolutePath}"

        when:
        startWatcher(linkedDir, canonicalDir)

        then:
        ex = thrown NativeException
        ex.message == "Already watching path: ${canonicalDir.absolutePath}"
    }
}
