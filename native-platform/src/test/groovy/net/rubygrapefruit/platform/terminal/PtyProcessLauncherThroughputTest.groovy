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

import net.rubygrapefruit.platform.NativePlatformSpec
import net.rubygrapefruit.platform.internal.Platform
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * Throughput regression sentinel for the PTY relay layer.
 *
 * <p>Drives a synthetic high-rate child through {@link PtyProcessLauncher} and reports
 * wall-clock throughput. Sits in native-platform (rather than in Gradle) so we can iterate
 * against locally-built JNI variants on every TC agent (including {@code WindowsAmd64})
 * without needing a published native-platform jar — Gradle's CI agents cannot resolve an
 * unpublished {@code -dev} version, but native-platform's own TC pipeline builds and runs
 * tests against locally-built variants directly.</p>
 *
 * <p>The hard floor below catches a catastrophic relay collapse; the printed MB/s line is
 * the figure to track when iterating on relay performance. The accompanying diagnostic
 * counter in {@code PosixPtyProcess.drain} / {@code WindowsPtyProcess.drain} prints
 * {@code PTY-NATIVE-DRAIN: <bytes> in <s> ... <reads>, avg <bytes/read>} on EOF, which
 * tells us the kernel-side chunk size — the actual rate-limiter we want to characterize
 * per-platform.</p>
 *
 * <p>See Step 8 / Bug 8.2 in {@code PLAN_NATIVE_PLATFORM_TUI.md} for the full diagnosis.</p>
 */
@Timeout(120)
class PtyProcessLauncherThroughputTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    private static final int MEGABYTES = 20
    private static final int FRAME_KB = 80

    @IgnoreIf({ Platform.current().windows })
    def "POSIX: PTY relay throughput"() {
        given:
        int frameCount = (MEGABYTES * 1024).intdiv(FRAME_KB)
        // 'stty raw -echo' puts the slave into raw mode before dd runs, bypassing the
        // line discipline; 'exec dd' replaces sh so dd's stdout is the slave fd directly.
        def cmd = ['/bin/sh', '-c', "stty raw -echo; exec dd if=/dev/zero bs=${FRAME_KB}K count=${frameCount} status=none".toString()]
        warmup()

        when:
        def pty = launcher.start(cmd, System.getenv(), null, 80, 24)
        long start = System.nanoTime()
        long received = drainStdout(pty)
        int exitCode = pty.waitFor()
        long elapsedNanos = System.nanoTime() - start

        then:
        exitCode == 0
        double seconds = elapsedNanos / 1_000_000_000.0d
        double mbps = (received / (1024.0d * 1024.0d)) / seconds
        println "PTY-LAUNCHER-THROUGHPUT-POSIX: ${received} bytes in ${String.format('%.3f', seconds)}s = ${String.format('%.2f', mbps)} MB/s"
        // Loose floor — catches a hard collapse, not a regression on a specific rate.
        // The printed figure (and the PTY-NATIVE-DRAIN line from the drainer) is what to
        // track when iterating on relay performance.
        mbps > 1.0d

        cleanup:
        pty?.close()
    }

    @IgnoreIf({ !Platform.current().windows })
    def "Windows: PTY relay throughput"() {
        given:
        int frameCount = (MEGABYTES * 1024).intdiv(FRAME_KB)
        // PowerShell tight-write loop: write FRAME_KB-sized zero buffers to stdout
        // FRAME_COUNT times. [Console]::OpenStandardOutput() returns a raw stream backed
        // by the stdout handle; writes bypass PowerShell's pipeline buffering, giving us
        // dd-equivalent tight write timing through ConPTY.
        //
        // Use -EncodedCommand (base64-UTF16LE) instead of -Command "<inline>" because the
        // inline form mangled at the CommandLineToArgvW boundary in our earlier run (only
        // 93 bytes received when 20 MB was expected). EncodedCommand sidesteps all `;`/`$`/
        // quote escaping. Use 'New-Object byte[] N' instead of '[byte[]]::new(N)' for
        // compatibility with older PowerShell hosts that may be on the agent.
        def script = "\$o=[Console]::OpenStandardOutput(); \$b=New-Object byte[] ${FRAME_KB * 1024}; for(\$i=0; \$i -lt ${frameCount}; \$i++){\$o.Write(\$b, 0, \$b.Length)}; \$o.Flush()".toString()
        def encoded = Base64.encoder.encodeToString(script.getBytes("UTF-16LE"))
        def cmd = ['powershell.exe', '-NoProfile', '-EncodedCommand', encoded]
        warmup()

        when:
        def pty = launcher.start(cmd, System.getenv(), null, 80, 24)
        long start = System.nanoTime()
        long received = drainStdout(pty)
        int exitCode = pty.waitFor()
        long elapsedNanos = System.nanoTime() - start

        then:
        exitCode == 0
        double seconds = elapsedNanos / 1_000_000_000.0d
        double mbps = (received / (1024.0d * 1024.0d)) / seconds
        println "PTY-LAUNCHER-THROUGHPUT-WINDOWS: ${received} bytes in ${String.format('%.3f', seconds)}s = ${String.format('%.2f', mbps)} MB/s"
        mbps > 1.0d

        cleanup:
        pty?.close()
    }

    private void warmup() {
        // Pre-run a trivial spawn so JIT, classloading, and PTY allocation paths are warm
        // before we measure. The bytes produced are discarded.
        def cmd = Platform.current().windows
            ? ['cmd.exe', '/c', 'echo warm']
            : ['/bin/sh', '-c', 'echo warm']
        def pty = launcher.start(cmd, System.getenv(), null, 80, 24)
        try {
            byte[] buf = new byte[8192]
            while (pty.inputStream.read(buf) != -1) { /* discard */ }
            pty.waitFor()
        } finally {
            pty.close()
        }
    }

    private static long drainStdout(PtyProcess pty) {
        byte[] buf = new byte[64 * 1024]
        long total = 0L
        int n
        while ((n = pty.inputStream.read(buf)) != -1) {
            total += n
        }
        return total
    }
}
