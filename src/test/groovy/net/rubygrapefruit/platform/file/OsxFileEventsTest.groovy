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
import net.rubygrapefruit.platform.internal.jni.DefaultOsxFileEventFunctions
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Timeout

@IgnoreIf({ !Platform.current().macOs })
@Timeout(20)
class OsxFileEventsTest extends Specification {
    @Rule
    TemporaryFolder tmpDir
    final DefaultOsxFileEventFunctions fileEvents = Native.get(DefaultOsxFileEventFunctions.class)

    def "caches file events instance"() {
        expect:
        Native.get(DefaultOsxFileEventFunctions.class) is fileEvents
    }

    def "can open and close watch on a directory without receiving any events"() {
        given:
        def dir = tmpDir.newFolder()
        fileEvents.addRecursiveWatch(dir.absolutePath)
        DefaultOsxFileEventFunctions.ChangeCollector collector = fileEvents.startWatch()
        new File(dir, "a.txt").createNewFile();
        Thread.sleep(20)

        def changes = fileEvents.stopWatch(collector);

        expect:
        changes == [dir.canonicalPath + "/"]
    }
}
