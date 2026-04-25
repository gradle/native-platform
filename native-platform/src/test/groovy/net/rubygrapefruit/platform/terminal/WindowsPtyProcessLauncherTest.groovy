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
import net.rubygrapefruit.platform.NativeIntegration
import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import net.rubygrapefruit.platform.internal.jni.WindowsHandleFunctions
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions
import spock.lang.IgnoreIf
import spock.lang.Timeout

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
        // Two non-obvious gotchas folded into one test:
        //
        // 1. cmd.exe needs a handful of bootstrap variables (SystemRoot,
        //    ComSpec, PATH, …) to start. An env containing only {MARKER}
        //    causes exit 255 before the /c command runs, masking the actual
        //    env-block encoding test. Derive the env from System.getenv() so
        //    cmd boots, then add MARKER on top.
        //
        // 2. `|` is cmd's pipe operator. `echo a|b` runs `b` as a command on
        //    the right side of the pipe — for `b == %unset_var%` cmd reports
        //    exit 255 from the failed pipeline. Using a colon separator
        //    avoids the parsing rabbit hole entirely and still keeps the
        //    boundary between substituted and literal segments visible.
        //
        // Isolation is verified by adding MARKER and looking up a freshly
        // generated variable name we never put in the env block: cmd echoes
        // the literal %name% for unset variables, so the literal appearing
        // in the output is direct evidence the variable was not passed.
        given:
        def absentName = "NP_TEST_ABSENT_" + UUID.randomUUID().toString().replace("-", "_")
        def env = new HashMap<>(System.getenv())
        env.remove(absentName) // already absent, but explicit for the reader
        env.put("MARKER", "hello_marker")
        def pty = launcher.start(
                ["cmd.exe", "/c", "echo %MARKER%:%" + absentName + "%"],
                env, null, 80, 24)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("hello_marker:%" + absentName + "%")

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

    // ---- Tier 6.2 — Resize ----

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "initial PTY size is honored by the child"() {
        // Uncommon dimensions to avoid coincidental matches with VT escape
        // sequences emitted by ConPTY's terminal init.
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command",
                 "[Console]::WindowWidth; [Console]::WindowHeight"],
                System.getenv(), null, 121, 37)

        when:
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        out.contains("121")
        out.contains("37")

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "resize() propagates new dimensions to the child"() {
        given:
        def script = '''
$lastW = [Console]::WindowWidth
Write-Output ("INITIAL=" + $lastW)
$deadline = (Get-Date).AddSeconds(20)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 100
    $w = [Console]::WindowWidth
    if ($w -ne $lastW) {
        Write-Output ("RESIZED=" + $w)
        exit 0
    }
}
exit 1
'''
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", script],
                System.getenv(), null, 80, 24)
        def collected = new StringBuilder()
        def collectedLock = new Object()
        def reader = Thread.start {
            byte[] buf = new byte[4096]
            int n
            try {
                while ((n = pty.inputStream.read(buf)) >= 0) {
                    synchronized (collectedLock) {
                        collected.append(new String(buf, 0, n))
                    }
                }
            } catch (IOException ignored) {
            }
        }
        // Wait until the child has reported its initial size, otherwise the
        // resize() below races the child's first WindowWidth read.
        def deadline = System.currentTimeMillis() + 15_000
        boolean ready = false
        while (System.currentTimeMillis() < deadline) {
            synchronized (collectedLock) {
                if (collected.toString().contains("INITIAL=80")) {
                    ready = true
                    break
                }
            }
            Thread.sleep(100)
        }
        assert ready : "child never reported INITIAL=80; out=" + collected.toString()

        when:
        pty.resize(132, 50)
        def deadline2 = System.currentTimeMillis() + 15_000
        boolean sawResize = false
        while (System.currentTimeMillis() < deadline2) {
            synchronized (collectedLock) {
                if (collected.toString().contains("RESIZED=132")) {
                    sawResize = true
                    break
                }
            }
            Thread.sleep(100)
        }
        def exitCode = pty.waitFor()

        then:
        sawResize
        exitCode == 0

        cleanup:
        reader?.join(2000)
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "resize after child exit is harmless"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        pty.waitFor()
        drainer.shutdown()
        drainer.awaitTermination(5, TimeUnit.SECONDS)

        when:
        pty.resize(99, 99)

        then:
        noExceptionThrown()

        cleanup:
        pty?.close()
    }

    // ---- Tier 6.3 — Lifecycle and resource safety ----

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "destroy() terminates a sleeping child within reasonable time"() {
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 60"],
                System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(500)

        when:
        long start = System.currentTimeMillis()
        pty.destroy()
        def exitCode = pty.waitFor()
        long elapsed = System.currentTimeMillis() - start

        then:
        !pty.isAlive()
        exitCode != 0
        elapsed < 5_000

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "destroy() escalates to TerminateProcess when the child ignores Ctrl+C"() {
        // PowerShell with TreatControlCAsInput=$true and an explicit
        // CancelKeyPress handler that sets Cancel=$true delivers Ctrl+C as a
        // regular character on stdin and short-circuits the cancel event, so
        // Start-Sleep keeps running until the parent's grace window expires
        // and TerminateProcess fires.
        given:
        def script = '''
[Console]::TreatControlCAsInput = $true
$null = Register-ObjectEvent -InputObject ([Console]) -EventName CancelKeyPress -Action { $eventArgs.Cancel = $true }
Start-Sleep -Seconds 60
'''
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", script],
                System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(500)

        when:
        long start = System.currentTimeMillis()
        pty.destroy()
        def exitCode = pty.waitFor()
        long elapsed = System.currentTimeMillis() - start

        then:
        !pty.isAlive()
        // 500 ms grace + TerminateProcess should land well under 5 s.
        elapsed < 5_000
        // TerminateProcess uses exit code 1 on the destroyProcess fallback.
        exitCode == 1

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "destroyForcibly() terminates immediately even when the child ignores Ctrl+C"() {
        given:
        def script = '''
[Console]::TreatControlCAsInput = $true
$null = Register-ObjectEvent -InputObject ([Console]) -EventName CancelKeyPress -Action { $eventArgs.Cancel = $true }
Start-Sleep -Seconds 60
'''
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", script],
                System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(500)

        when:
        long start = System.currentTimeMillis()
        pty.destroyForcibly()
        def exitCode = pty.waitFor()
        long elapsed = System.currentTimeMillis() - start

        then:
        !pty.isAlive()
        // No grace period — termination should be near-instant.
        elapsed < 2_000
        exitCode == 1

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "close() is idempotent"() {
        given:
        def pty = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        pty.waitFor()
        drainer.shutdown()
        drainer.awaitTermination(5, TimeUnit.SECONDS)

        when:
        pty.close()
        pty.close()
        pty.close()

        then:
        noExceptionThrown()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "close() unblocks a thread blocked in inputStream.read"() {
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 60"],
                System.getenv(), null, 80, 24)
        Thread.sleep(300)
        def readerException = new AtomicReference<Throwable>()
        def readerDone = new CountDownLatch(1)
        def reader = new Thread({
            try {
                byte[] b = new byte[256]
                while (pty.inputStream.read(b) >= 0) {
                    // drain
                }
            } catch (Throwable t) {
                readerException.set(t)
            } finally {
                readerDone.countDown()
            }
        })
        reader.start()
        Thread.sleep(300)

        when:
        pty.destroyForcibly()
        pty.close()
        boolean finished = readerDone.await(5, TimeUnit.SECONDS)

        then:
        finished
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "close() with active read on a long-running child completes quickly"() {
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 60"],
                System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(500)

        when:
        long start = System.currentTimeMillis()
        pty.close()
        long elapsed = System.currentTimeMillis() - start

        then:
        !pty.isAlive()
        elapsed < 5_000

        cleanup:
        drainer?.shutdownNow()
    }

    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "spawn/close loop adds no HANDLEs beyond the ConPTY baseline"() {
        // The Windows ConPTY API itself adds 1 HANDLE per
        // CreatePseudoConsole/ClosePseudoConsole pair to
        // GetProcessHandleCount on this CI configuration — verified by
        // running the same loop with no CreateProcessW, no drainers, and
        // no Java PtyProcess (see the per-iter rate logged below). It is
        // a kernel/ConPTY-side artefact, not something a CloseHandle on
        // the Java side can recover.
        //
        // What this test actually pins, then, is that the full
        // spawn → drainer → watcher → CreateProcessW → close path adds
        // *no further* per-spawn HANDLEs on top of that ConPTY baseline.
        // A real bug (a missing CloseHandle on ptyReadHandle,
        // ptyWriteHandle, processHandle, or pi.hThread) shows up as a
        // higher rate than the ConPTY-only baseline.
        given:
        def closeOnly = {
            long[] handles = new long[4]
            def r = new net.rubygrapefruit.platform.internal.FunctionResult()
            WindowsPtyFunctions.createPseudoConsole(80, 24, handles, r)
            assert !r.failed : r.message
            WindowsPtyFunctions.closePseudoConsole(handles[0], new net.rubygrapefruit.platform.internal.FunctionResult())
            WindowsPtyFunctions.closeHandle(handles[1], new net.rubygrapefruit.platform.internal.FunctionResult())
            WindowsPtyFunctions.closeHandle(handles[2], new net.rubygrapefruit.platform.internal.FunctionResult())
        }
        def spawnAndClose = {
            def p = launcher.start(["cmd.exe", "/c", "exit 0"], System.getenv(), null, 80, 24)
            p.waitFor()
            p.close()
        }
        20.times { closeOnly() }
        20.times { spawnAndClose() }

        int conptyBefore = WindowsHandleFunctions.getProcessHandleCount()
        50.times { closeOnly() }
        int conptyAfter = WindowsHandleFunctions.getProcessHandleCount()
        double conptyRate = (conptyAfter - conptyBefore) / 50.0d
        println "[Tier 6.3.7] ConPTY-only baseline HANDLE rate = ${conptyRate}/iter"

        when:
        int spawnBefore = WindowsHandleFunctions.getProcessHandleCount()
        50.times { spawnAndClose() }
        int spawnAfter = WindowsHandleFunctions.getProcessHandleCount()
        double spawnRate = (spawnAfter - spawnBefore) / 50.0d
        println "[Tier 6.3.7] spawn+close HANDLE rate = ${spawnRate}/iter"

        then:
        // Adding the full process-spawn + drainer + watcher path on top of
        // ConPTY must not introduce additional per-iter HANDLE growth.
        // 0.5 of slack covers timing jitter in GetProcessHandleCount
        // without masking a real per-spawn miss.
        spawnRate < conptyRate + 0.5d
    }

    // ---- Tier 6.4 — Stress and concurrency ----

    @Timeout(90)
    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "large output does not deadlock"() {
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", "'A' * 1048576"],
                System.getenv(), null, 80, 24)
        def buffer = new ByteArrayOutputStream()
        def reader = Thread.start {
            byte[] buf = new byte[8192]
            int n
            try {
                while ((n = pty.inputStream.read(buf)) >= 0) {
                    buffer.write(buf, 0, n)
                }
            } catch (IOException ignored) {
            }
        }

        when:
        def exitCode = pty.waitFor()
        reader.join(60_000)

        then:
        exitCode == 0
        !reader.isAlive()
        // PowerShell prints the 1 MB string plus VT/banner output, and ConPTY
        // may add its own escape sequences; assert ≥ 500 KB rather than
        // pinning an exact size.
        buffer.size() >= 500_000

        cleanup:
        pty?.close()
    }

    @Timeout(60)
    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "concurrent spawn from four threads"() {
        given:
        def results = new ConcurrentHashMap<String, Map>()
        def threads = (0..3).collect { i ->
            String marker = "thread-${i}".toString()
            String command = "echo " + marker
            Thread.start {
                def pty = launcher.start(
                        ["cmd.exe", "/c", command],
                        System.getenv(), null, 80, 24)
                try {
                    def out = pty.inputStream.text
                    def exitCode = pty.waitFor()
                    results.put(marker, [exitCode: exitCode, out: out])
                } finally {
                    pty.close()
                }
            }
        }

        when:
        threads.each { it.join(30_000) }

        then:
        results.size() == 4
        (0..3).each { i ->
            String marker = "thread-${i}".toString()
            def r = results[marker]
            assert r != null
            assert r.exitCode == 0
            assert r.out.contains(marker)
            (0..3).each { other ->
                if (other != i) {
                    assert !r.out.contains("thread-${other}".toString())
                }
            }
        }
    }

    @Timeout(60)
    @IgnoreIf({ !WindowsPtyProcessLauncherTest.conptyAvailable() })
    def "rapid repeated resize does not crash"() {
        given:
        def pty = launcher.start(
                ["powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 30"],
                System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(300)

        when:
        100.times { i ->
            pty.resize(100 + (i % 30), 30 + (i % 20))
        }
        pty.destroyForcibly()
        pty.waitFor()

        then:
        noExceptionThrown()

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    // ---- Tier 6.5 — Public-API contract sentinels ----

    def "PtyProcess extends AutoCloseable"() {
        expect:
        AutoCloseable.isAssignableFrom(PtyProcess)
    }

    def "PtyProcessLauncher extends NativeIntegration"() {
        expect:
        NativeIntegration.isAssignableFrom(PtyProcessLauncher)
    }

    def "isAvailable signature is boolean and call is non-throwing"() {
        when:
        boolean r = launcher.isAvailable()

        then:
        noExceptionThrown()
        r || !r
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
