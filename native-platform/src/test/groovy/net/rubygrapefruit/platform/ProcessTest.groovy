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

import net.rubygrapefruit.platform.testfixture.JavaVersion
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.BeforeEach
import spock.lang.IgnoreIf
import java.nio.file.Path

class ProcessTest extends NativePlatformSpec {
    File tmpDir
    Process process

    @BeforeEach
    void setup() {
        tmpDir = File.createTempDir()
        process = getIntegration(Process)
    }

    def cleanup() {
        tmpDir?.deleteDir()
    }

    def "caches process instance"() {
        expect:
        getIntegration(Process) == process
    }

    def "can get PID"() {
        expect:
        process.getProcessId() != 0
    }

    // Changing the working directory has no effect on Java 11+
    @IgnoreIf({ JavaVersion.majorVersion >= 11 })
    def "can get and change working directory"() {
        def newDir = new File(tmpDir, dir)
        newDir.mkdirs()
        newDir = newDir.canonicalFile
        assert newDir.directory

        when:
        def original = process.workingDirectory

        then:
        original == new File(".").canonicalFile
        original == new File(System.getProperty("user.dir"))

        when:
        process.workingDirectory = newDir

        then:
        process.workingDirectory == newDir
        new File(".").canonicalFile == newDir
        new File(System.getProperty("user.dir")) == newDir

        cleanup:
        process.workingDirectory = original

        where:
        dir << ['dir', 'dir\u03b1\u2295']
    }

    def "cannot set working directory to a directory that does not exist"() {
        def newDir = new File(tmpDir, "does not exist");

        when:
        process.workingDirectory = newDir

        then:
        NativeException e = thrown()
        e.message.startsWith("Could not set process working directory")
    }

    def "can get and set and remove environment variable"() {
        when:
        def value = process.getEnvironmentVariable(varName)

        then:
        value == null
        System.getenv(varName) == null
        System.getenv()[varName] == null

        when:
        process.setEnvironmentVariable(varName, varValue)

        then:
        process.getEnvironmentVariable(varName) == varValue
        System.getenv(varName) == varValue
        System.getenv()[varName] == varValue

        when:
        process.setEnvironmentVariable(varName, null)

        then:
        process.getEnvironmentVariable(varName) == null
        System.getenv(varName) == null
        System.getenv()[varName] == null

        where:
        varName                    | varValue
        'TEST_ENV_VAR'             | 'test value'
        'TEST_ENV_VAR\u2295\u03b1' | 'value\u03b2\u2296'
    }

    def "setting environment variable to null or empty string remove the environment variable"() {
        when:
        def value = process.getEnvironmentVariable(varName)

        then:
        value == null
        System.getenv(varName) == null
        System.getenv()[varName] == null

        when:
        process.setEnvironmentVariable(varName, varValue)

        then:
        process.getEnvironmentVariable(varName) == null
        System.getenv(varName) == null
        System.getenv()[varName] == null

        where:
        varName              | varValue
        'TEST_ENV_VAR_EMPTY' | ''
        'TEST_ENV_VAR_NULL'  | null
    }

    def "can set environment variable to supplementary character"() {
        given:
        String utfSupplementaryString = String.valueOf(Character.toChars(128165))

        when:
        process.setEnvironmentVariable("TEST", utfSupplementaryString)

        then:
        println System.getenv("TEST")
        System.getenv("TEST") == utfSupplementaryString
        System.getenv()["TEST"] == utfSupplementaryString
        println process.getEnvironmentVariable("TEST")
        process.getEnvironmentVariable("TEST") == utfSupplementaryString
    }

    def "can remove environment variable that does not exist"() {
        assert process.getEnvironmentVariable("TEST_ENV_UNKNOWN") == null

        when:
        process.setEnvironmentVariable("TEST_ENV_UNKNOWN", null)

        then:
        notThrown(NativeException)
    }

    def "can detach a process from terminal/console"() {
        when:
        process.detach()

        then:
        noExceptionThrown()

        when:
        process.detach()

        then:
        noExceptionThrown()
    }
}
