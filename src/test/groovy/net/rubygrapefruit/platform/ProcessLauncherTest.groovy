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

package net.rubygrapefruit.platform

import spock.lang.Specification

class ProcessLauncherTest extends Specification {
    final ProcessLauncher launcher = Native.get(ProcessLauncher)

    def "can start a child process"() {
        def javaHome = System.getProperty("java.home")
        def exe = "${javaHome}/bin/java"
        ProcessBuilder builder = new ProcessBuilder(exe, "-version")
        builder.redirectErrorStream(true)

        when:
        def process = launcher.start(builder)
        def stdout = new ByteArrayOutputStream()
        def stdoutThread = process.consumeProcessOutputStream(stdout)
        def result = process.waitFor()
        stdoutThread.join()

        then:
        result == 0
        stdout.toString().contains(System.getProperty('java.vm.version'))
    }
}
