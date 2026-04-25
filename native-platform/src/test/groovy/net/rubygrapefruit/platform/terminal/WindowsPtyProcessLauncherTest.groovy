/*
 * Copyright 2026 the original author or authors.
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

package net.rubygrapefruit.platform.terminal

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions
import spock.lang.IgnoreIf
import spock.lang.Timeout

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(30)
@IgnoreIf({ !Platform.current().windows })
class WindowsPtyProcessLauncherTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    static boolean conptyAvailable() {
        try {
            getIntegration(PtyProcessLauncher)
            return WindowsPtyFunctions.isConPtyAvailable()
        } catch (Throwable ignored) {
            return false
        }
    }

    def "isConPtyAvailable returns a boolean and does not throw"() {
        when:
        boolean available = WindowsPtyFunctions.isConPtyAvailable()
        boolean launcherAvailable = launcher.isAvailable()
        println "[Tier 6.0.1] WindowsPtyFunctions.isConPtyAvailable=${available} PtyProcessLauncher.isAvailable=${launcherAvailable}"

        then:
        noExceptionThrown()
    }

    def "isConPtyAvailable matches launcher.isAvailable"() {
        expect:
        WindowsPtyFunctions.isConPtyAvailable() == launcher.isAvailable()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "can spawn a basic ConPTY process and observe exit 0"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }

    // ---- Tier 6.1 — Core correctness ----

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "child writes to stdout and the master sees the bytes"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "echo CONPTY_HELLO"], System.getenv(), null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("CONPTY_HELLO")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "exit-code decoding for normal exit"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 42"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 42

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "exit code 259 is not conflated with STILL_ACTIVE"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 259"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 259

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "command-line quoting preserves arguments containing spaces"() {
        given:
        def message = "hello windows"
        def pty = launcher.start(["cmd.exe", "/c", "echo", message], System.getenv(), null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains(message)

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "command-line quoting preserves arguments with embedded double quotes"() {
        // cmd's echo prints the raw command-line tail verbatim, including the
        // backslash-escapes our quoting layer adds for CommandLineToArgvW. So
        // the goal is to confirm CreateProcessW accepted the quoted string and
        // the argument bytes survived the round trip; we don't try to assert
        // any specific dequoted form because cmd doesn't apply CommandLineToArgvW.
        given:
        def message = 'he said "hi"'
        def pty = launcher.start(["cmd.exe", "/c", "echo", message], System.getenv(), null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("he said")
        out.contains("hi")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "env isolation: child sees only passed environment"() {
        // cmd.exe needs a handful of variables (SystemRoot, ComSpec, PATH, …)
        // to even start; an env containing only {MARKER} causes it to exit 255
        // before reaching the /c command, which would mask the encoding test.
        // Pass the daemon's env *minus* one specific variable plus a marker,
        // and prove isolation via two assertions: the marker is visible in the
        // child, and the omitted variable is *not* — cmd echoes the literal
        // %name% for unset variables, so the literal appearing in the output
        // is direct evidence the variable was not in the passed env block.
        given:
        def absentName = "NP_TEST_ABSENT_" + UUID.randomUUID().toString().replace("-", "_")
        def env = new HashMap<>(System.getenv())
        env.remove(absentName) // already absent, but explicit for the reader
        env.put("MARKER", "hello_marker")
        def pty = launcher.start(
                ["cmd.exe", "/c", "echo %MARKER%|%" + absentName + "%"],
                env, null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("hello_marker|%" + absentName + "%")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "Unicode environment values survive the env block encoding"() {
        given:
        def env = ["UNI": "café"]
        def pty = launcher.start(["cmd.exe", "/c", "echo %UNI%"], env, null, 80, 24)

        when:
        def out = new String(readAllBytes(pty.inputStream), "UTF-8")
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("café")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "working directory: absolute path is honored"() {
        // ConPTY emits OSC title-set + cursor escapes as part of terminal
        // init, so the *last* line of output is not necessarily the `cd`
        // result. Match any line in the captured output instead.
        given:
        def workDir = new File(System.getProperty("java.io.tmpdir"))
        def pty = launcher.start(["cmd.exe", "/c", "cd"], System.getenv(), workDir, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        def expected = workDir.canonicalPath
        out.readLines().any { it.toLowerCase().contains(expected.toLowerCase()) }

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "working directory: non-existent path fails cleanly"() {
        given:
        def missing = new File("C:\\does\\not\\exist\\conpty-test")

        when:
        launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), missing, 80, 24)

        then:
        def e = thrown(NativeException)
        e.message != null
        !e.message.toLowerCase().contains("could not create pseudo-console")
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "stream separation: stdout and stderr are received on independent streams or stderr is merged"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "echo OUT_MSG & echo ERR_MSG 1>&2"], System.getenv(), null, 80, 24)
        def executor = Executors.newFixedThreadPool(2)
        def outFuture = executor.submit({ pty.inputStream.text } as java.util.concurrent.Callable<String>)
        def errFuture = executor.submit({ pty.errorStream.text } as java.util.concurrent.Callable<String>)
        def exitCode = pty.waitFor()
        def stdout = outFuture.get(10, TimeUnit.SECONDS)
        def stderr = errFuture.get(10, TimeUnit.SECONDS)

        expect:
        exitCode == 0
        // Either: stderr split worked and ERR_MSG is on errorStream only; or stderr was
        // merged into the ConPTY (acceptable per the public-API contract — see test 6.1.10).
        if (!stderr.isEmpty()) {
            assert stderr.contains("ERR_MSG")
            assert !stderr.contains("OUT_MSG")
            assert stdout.contains("OUT_MSG")
            assert !stdout.contains("ERR_MSG")
        } else {
            assert stdout.contains("OUT_MSG")
            assert stdout.contains("ERR_MSG")
        }

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "master read returns EOF after child exits without throwing"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "echo done"], System.getenv(), null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        noExceptionThrown()
        exitCode == 0
        out.contains("done")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "write after child exit throws ProcessExitedException"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        pty.waitFor()
        drainer.shutdown()
        drainer.awaitTermination(5, TimeUnit.SECONDS)
        Thread.sleep(200)

        when:
        def os = pty.outputStream
        boolean thrown = false
        try {
            for (int i = 0; i < 1024; i++) {
                os.write("x".bytes)
                os.flush()
            }
        } catch (ProcessExitedException ignored) {
            thrown = true
        }

        then:
        thrown

        cleanup:
        pty?.close()
    }

    private static byte[] readAllBytes(InputStream input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream()
        byte[] chunk = new byte[4096]
        int n
        while ((n = input.read(chunk)) >= 0) {
            buffer.write(chunk, 0, n)
        }
        buffer.toByteArray()
    }
}
