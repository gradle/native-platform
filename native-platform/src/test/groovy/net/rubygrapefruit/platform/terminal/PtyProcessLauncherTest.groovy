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
import spock.lang.IgnoreIf
import spock.lang.Timeout

import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(30)
@IgnoreIf({ Platform.current().windows })
class PtyProcessLauncherTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    private static String findExecutable(List<String> paths) {
        paths.find { new File(it).canExecute() }
    }

    private static String trueBinary() {
        def r = findExecutable(["/usr/bin/true", "/bin/true"])
        assert r : "no 'true' binary found on the agent"
        r
    }

    private static String shBinary() {
        def r = findExecutable(["/bin/sh", "/usr/bin/sh"])
        assert r : "no 'sh' binary found on the agent"
        r
    }

    def "isAvailable does not throw and returns a boolean"() {
        when:
        def result = launcher.isAvailable()

        then:
        noExceptionThrown()
        result instanceof Boolean
    }

    def "can start a trivial child process and observe exit code 0"() {
        given:
        def pty = launcher.start([trueBinary()], System.getenv(), null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }

    def "child sees isatty(0) && isatty(1) && !isatty(2)"() {
        given:
        def script = 'r=0; test -t 0 && r=$((r|1)); test -t 1 && r=$((r|2)); test -t 2 && r=$((r|4)); exit $r'
        def pty = launcher.start([shBinary(), "-c", script], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 3

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    def "exit-code decoding for normal exit"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "exit 42"], System.getenv(), null, 80, 24)
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

    def "exit-code decoding for signal kill"() {
        given:
        def pty = launcher.start([shBinary(), "-c", 'kill -TERM $$'], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 128 + 15

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    def "PATH resolution: absolute path is executed directly"() {
        given:
        def pty = launcher.start([trueBinary()], [:], null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }

    def "PATH resolution: relative name resolved via PATH"() {
        given:
        def env = ["PATH": "/usr/bin:/bin"]
        def pty = launcher.start(["true"], env, null, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
    }

    def "PATH resolution: empty entry means current directory"() {
        given:
        def tmpDir = Files.createTempDirectory("pty-cwd-").toFile()
        def target = new File(tmpDir, "mytrue")
        target.bytes = new File(trueBinary()).bytes
        target.setExecutable(true)
        def env = ["PATH": ":/nonexistent"]
        def pty = launcher.start(["mytrue"], env, tmpDir, 80, 24)

        when:
        def exitCode = pty.waitFor()

        then:
        exitCode == 0

        cleanup:
        pty?.close()
        tmpDir?.deleteDir()
    }

    def "PATH resolution: EACCES-only surfaces Permission denied"() {
        given:
        def tmpDir = Files.createTempDirectory("pty-eacces-").toFile()
        def target = new File(tmpDir, "foo")
        target.text = "not executable"
        target.setExecutable(false)
        def env = ["PATH": tmpDir.absolutePath]

        when:
        launcher.start(["foo"], env, null, 80, 24)

        then:
        def e = thrown(NativeException)
        e.message.contains("Permission denied")
        !e.message.contains("No such file or directory")

        cleanup:
        tmpDir?.deleteDir()
    }

    def "env isolation: child sees only passed environment"() {
        given:
        def env = ["PATH": "/bin:/usr/bin", "MARKER": "hello"]
        def pty = launcher.start([shBinary(), "-c", 'echo "$MARKER|$UNSET_BY_PARENT"'], env, null, 80, 24)
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        expect:
        exitCode == 0
        out.readLines().last() == "hello|"

        cleanup:
        pty?.close()
    }

    def "env isolation: passing no PATH prevents PATH resolution"() {
        when:
        launcher.start(["true"], [:], null, 80, 24)

        then:
        def e = thrown(NativeException)
        e.message != null
    }

    def "working directory: absolute path honored"() {
        given:
        def dir = new File(System.getProperty("java.io.tmpdir"))
        def pty = launcher.start([shBinary(), "-c", "pwd -P"], System.getenv(), dir, 80, 24)
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        expect:
        exitCode == 0
        def observed = out.readLines().last().replaceAll("\\r", "")
        observed == dir.canonicalPath

        cleanup:
        pty?.close()
    }

    def "working directory: null means daemon cwd"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "pwd -P"], System.getenv(), null, 80, 24)
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        expect:
        exitCode == 0
        def observed = out.readLines().last().replaceAll("\\r", "")
        observed == new File(".").canonicalPath

        cleanup:
        pty?.close()
    }

    def "working directory: non-existent path fails cleanly"() {
        given:
        def missing = new File("/does/not/exist/pty-test")

        when:
        launcher.start([trueBinary()], System.getenv(), missing, 80, 24)

        then:
        def e = thrown(NativeException)
        e.message.toLowerCase().contains("no such file") || e.message.toLowerCase().contains("chdir")
    }

    def "stdout and stderr are received on separate streams"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "echo OUT; echo ERR >&2"], System.getenv(), null, 80, 24)
        def executor = Executors.newFixedThreadPool(2)
        def outFuture = executor.submit({ pty.inputStream.text } as java.util.concurrent.Callable<String>)
        def errFuture = executor.submit({ pty.errorStream.text } as java.util.concurrent.Callable<String>)
        def exitCode = pty.waitFor()
        def stdout = outFuture.get(10, TimeUnit.SECONDS)
        def stderr = errFuture.get(10, TimeUnit.SECONDS)

        expect:
        exitCode == 0
        stdout.contains("OUT")
        !stdout.contains("ERR")
        stderr.contains("ERR")
        !stderr.contains("OUT")

        cleanup:
        executor?.shutdownNow()
        pty?.close()
    }

    def "master read returns EOF after child exit (no IOException)"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "echo done"], System.getenv(), null, 80, 24)

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

    def "initial terminal size is honored"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "stty size"], System.getenv(), null, 120, 40)
        def out = pty.inputStream.text
        def exitCode = pty.waitFor()

        expect:
        exitCode == 0
        out.contains("40 120")

        cleanup:
        pty?.close()
    }

    def "resize delivers SIGWINCH and new dimensions"() {
        given:
        def script = '''
            trap 'stty size >&2; exit 0' WINCH
            echo READY >&2
            sleep 20 &
            wait
        '''
        def pty = launcher.start([shBinary(), "-c", script], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        def stderrReader = new BufferedReader(new InputStreamReader(pty.errorStream))
        def readyLine = stderrReader.readLine()
        assert readyLine?.contains("READY")

        when:
        pty.resize(132, 50)
        def sizeLine = stderrReader.readLine()
        def exitCode = pty.waitFor()

        then:
        exitCode == 0
        sizeLine?.contains("50 132")

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    def "resize after child exit is harmless"() {
        given:
        def pty = launcher.start([trueBinary()], System.getenv(), null, 80, 24)
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

    def "destroy() sends SIGTERM"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "exec sleep 60"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(200)

        when:
        pty.destroy()
        def exitCode = pty.waitFor()

        then:
        exitCode == 128 + 15

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    def "destroyForcibly() kills a shell that traps SIGTERM"() {
        given:
        def pty = launcher.start([shBinary(), "-c", 'trap "" TERM; sleep 60'], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        Thread.sleep(200)

        when:
        pty.destroyForcibly()
        def exitCode = pty.waitFor()

        then:
        exitCode == 128 + 9

        cleanup:
        drainer?.shutdownNow()
        pty?.close()
    }

    def "close() is idempotent"() {
        given:
        def pty = launcher.start([trueBinary()], System.getenv(), null, 80, 24)
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

    def "close() while a reader is blocked does not deadlock"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "sleep 30"], System.getenv(), null, 80, 24)
        Thread.sleep(100)
        def readerException = new java.util.concurrent.atomic.AtomicReference<Throwable>()
        def readerDone = new java.util.concurrent.CountDownLatch(1)
        def reader = new Thread({
            try {
                def b = new byte[256]
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
        Thread.sleep(200)

        when:
        pty.destroyForcibly()
        pty.close()
        def finished = readerDone.await(5, TimeUnit.SECONDS)

        then:
        finished
    }

    def "close() reaps the child"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "sleep 60"], System.getenv(), null, 80, 24)

        when:
        pty.close()

        then:
        !pty.isAlive()
    }

    @IgnoreIf({ !new File("/proc/self/fd").exists() })
    def "does not leak PTY master file descriptors across spawns"() {
        given:
        def ptyFds = {
            def dir = new File("/proc/self/fd")
            dir.listFiles()?.findAll { f ->
                try {
                    def target = java.nio.file.Files.readSymbolicLink(f.toPath()).toString()
                    target.contains("/dev/ptmx") || target.contains("/dev/pts/")
                } catch (Throwable ignored) {
                    false
                }
            }?.collect { it.name } ?: []
        }
        5.times {
            def p = launcher.start([trueBinary()], System.getenv(), null, 80, 24)
            p.waitFor()
            p.close()
        }
        def initial = ptyFds() as Set

        when:
        50.times {
            def p = launcher.start([trueBinary()], System.getenv(), null, 80, 24)
            p.waitFor()
            p.close()
        }
        def finalSet = ptyFds() as Set

        then:
        (finalSet - initial).isEmpty()
    }

    def "write after child exit throws ProcessExitedException"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "exit 0"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)
        pty.waitFor()
        drainer.shutdown()
        drainer.awaitTermination(5, TimeUnit.SECONDS)
        Thread.sleep(100)

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

    @Timeout(60)
    def "handles large output without deadlock"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "yes A | head -c 1048576; exit 0"], System.getenv(), null, 80, 24)
        def buffer = new ByteArrayOutputStream()
        def reader = Thread.start {
            byte[] buf = new byte[8192]
            int n
            while ((n = pty.inputStream.read(buf)) >= 0) {
                buffer.write(buf, 0, n)
            }
        }

        when:
        def exitCode = pty.waitFor()
        reader.join(60_000)

        then:
        exitCode == 0
        !reader.isAlive()
        buffer.size() >= 500_000

        cleanup:
        pty?.close()
    }

    def "concurrent spawn from four threads"() {
        given:
        def results = new ConcurrentHashMap<String, Map>()
        def threads = (0..3).collect { i ->
            String marker = "thread-${i}".toString()
            String script = "echo ${marker}".toString()
            Thread.start {
                def pty = launcher.start([shBinary(), "-c", script], System.getenv(), null, 80, 24)
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
        threads.each { it.join(15_000) }

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

    def "rapid repeated resize does not crash"() {
        given:
        def pty = launcher.start([shBinary(), "-c", "sleep 10"], System.getenv(), null, 80, 24)
        def drainer = Executors.newSingleThreadExecutor()
        drainer.submit({ pty.inputStream.text } as Runnable)

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
}
