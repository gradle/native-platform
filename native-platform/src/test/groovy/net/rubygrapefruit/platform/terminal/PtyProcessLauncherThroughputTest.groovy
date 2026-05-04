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
 * <p>The POSIX assertion is calibration-relative: in the same run we measure a baseline
 * by running <em>the same producer</em> ({@code dd if=/dev/zero}) through a plain
 * non-PTY pipe ({@link ProcessBuilder}), drained via the same Java {@link InputStream}
 * pattern as the PTY path. Comparing those two measurements isolates the cost of the
 * PTY relay layer (master/slave PTY, JNI {@code nativeRead}, BufferedPtyInputStream
 * queue) on top of an OS pipe + Java I/O baseline. The ratio is robust across CI agents
 * of differing absolute speed: a slow agent slows both legs proportionally.</p>
 *
 * <p>Each PTY measurement is the median of three samples to absorb single-iteration
 * GC and scheduler jitter. The test is a sentinel, not a precise benchmark.</p>
 *
 * <p>The Windows variant keeps a loose absolute floor for now: PowerShell's
 * {@code [Console]::Out} behavior differs between a regular pipe and ConPTY (encoding,
 * buffering), so the same-producer comparison is not apples-to-apples. A baseline-relative
 * Windows measurement is a Phase 2 follow-up.</p>
 */
@Timeout(240)
class PtyProcessLauncherThroughputTest extends NativePlatformSpec {
    final PtyProcessLauncher launcher = getIntegration(PtyProcessLauncher)

    private static final int MEGABYTES = 20
    private static final int FRAME_KB = 80
    private static final long TOTAL_BYTES = (long) MEGABYTES * 1024L * 1024L
    private static final int SAMPLE_COUNT = 3
    private static final int WARMUP_RUNS = 3

    /**
     * Test fails if PTY relay throughput is more than this many times slower than the
     * baseline (same dd command, same drain loop, no PTY).
     *
     * <p>Initial local measurements (warm) cluster around 5x on macOS and 7x on Linux,
     * so a tuned floor near ~15x would catch a ~2x relay regression on either kernel.
     * Whether a single global floor or per-platform floors fit best depends on how
     * tightly the actual TC agent fleet (Ubuntu / Amazon Linux / CentOS / FreeBSD /
     * macOS amd64+arm64) clusters around those numbers.</p>
     *
     * <p>For this calibration phase the floor is deliberately loose so the
     * {@code PTY-RELAY-RATIO-POSIX} println below collects real ratios across the
     * fleet without blocking CI. After a few green TC runs we tighten the floor (or
     * split per-platform) and remove the println.</p>
     *
     * <p>TODO: tighten the floor toward ~1.5x of the observed CI maximum and remove
     * the {@code PTY-RELAY-RATIO-POSIX} println.</p>
     */
    private static final int MAX_SLOWDOWN_VS_BASELINE = 50

    @IgnoreIf({ Platform.current().windows })
    def "POSIX: PTY relay throughput tracks pipe baseline"() {
        given:
        int frameCount = (MEGABYTES * 1024).intdiv(FRAME_KB)
        // 'stty raw -echo' puts the slave into raw mode before dd runs, bypassing the
        // line discipline; 'exec dd' replaces sh so dd's stdout is the slave fd directly.
        def ptyCmd = ['/bin/sh', '-c', "stty raw -echo; exec dd if=/dev/zero bs=${FRAME_KB}K count=${frameCount} status=none".toString()]
        // Same producer, no PTY: dd writes to a regular pipe that ProcessBuilder hands us.
        def baselineCmd = ['/bin/sh', '-c', "exec dd if=/dev/zero bs=${FRAME_KB}K count=${frameCount} status=none".toString()]
        warmupPosix(ptyCmd, baselineCmd)

        when:
        // Median both legs so a single-iteration jitter does not bias the ratio.
        def baselines = (1..SAMPLE_COUNT).collect { runOneBaseline(baselineCmd) }
        def baselineMedian = baselines.sort { it.bytesPerSec }[SAMPLE_COUNT.intdiv(2)]
        def samples = (1..SAMPLE_COUNT).collect { runOnePosixRelay(ptyCmd) }
        def relayMedian = samples.sort { it.bytesPerSec }[SAMPLE_COUNT.intdiv(2)]
        double slowdown = baselineMedian.bytesPerSec / relayMedian.bytesPerSec

        // Calibration aid: print so the first few CI runs surface actual ratios, then
        // we tighten MAX_SLOWDOWN_VS_BASELINE and remove this line.
        println String.format(
            "PTY-RELAY-RATIO-POSIX: relay=%.2f MB/s, baseline=%.2f MB/s, slowdown=%.2fx",
            relayMedian.bytesPerSec / (1024.0d * 1024.0d),
            baselineMedian.bytesPerSec / (1024.0d * 1024.0d),
            slowdown)

        then:
        relayMedian.exitCode == 0
        relayMedian.received >= TOTAL_BYTES
        baselineMedian.exitCode == 0
        slowdown <= MAX_SLOWDOWN_VS_BASELINE
    }

    @IgnoreIf({ !Platform.current().windows })
    def "Windows: PTY relay throughput is non-trivial"() {
        given:
        int frameCount = (MEGABYTES * 1024).intdiv(FRAME_KB)
        // PowerShell tight-write loop via [Console]::Out (TextWriter). Earlier attempts
        // using [Console]::OpenStandardOutput().Write(byte[]) with zero bytes produced
        // only ~93 bytes through ConPTY; null bytes appear to get swallowed by the
        // console-emulation layer somewhere between the .NET stream and the master pipe.
        // Writing printable ASCII (newline-free 'A' characters) via TextWriter survives
        // the ConPTY boundary and matches the character-stream nature of real TUI output.
        // Use -EncodedCommand to sidestep CommandLineToArgvW quoting fragility.
        def script = ("\$ErrorActionPreference='Stop'; " +
            "\$s='A'*${FRAME_KB * 1024}; " +
            "for(\$i=0; \$i -lt ${frameCount}; \$i++){[Console]::Out.Write(\$s)}; " +
            "[Console]::Out.Flush()").toString()
        def encoded = Base64.encoder.encodeToString(script.getBytes("UTF-16LE"))
        def cmd = ['powershell.exe', '-NoProfile', '-EncodedCommand', encoded]
        warmupWindows(cmd)

        when:
        def samples = (1..SAMPLE_COUNT).collect { runOneWindowsRelay(cmd) }
        def median = samples.sort { it.bytesPerSec }[SAMPLE_COUNT.intdiv(2)]
        double mbps = median.bytesPerSec / (1024.0d * 1024.0d)

        // Calibration aid until Phase 2 delivers a baseline-relative Windows assertion.
        println String.format("PTY-RELAY-MBPS-WINDOWS: %.2f MB/s", mbps)

        then:
        median.exitCode == 0
        mbps > 1.0d
    }

    private static class Sample {
        long received
        long elapsedNanos
        double bytesPerSec
        int exitCode
    }

    private Sample runOnePosixRelay(List<String> cmd) {
        def pty = launcher.start(cmd, System.getenv(), null, 80, 24)
        try {
            long start = System.nanoTime()
            long received = drainInputStream(pty.inputStream)
            int exitCode = pty.waitFor()
            long elapsedNanos = System.nanoTime() - start
            return new Sample(
                received: received,
                elapsedNanos: elapsedNanos,
                bytesPerSec: received / (elapsedNanos / 1_000_000_000.0d),
                exitCode: exitCode
            )
        } finally {
            pty.close()
        }
    }

    private Sample runOneWindowsRelay(List<String> cmd) {
        def pty = launcher.start(cmd, System.getenv(), null, 80, 24)
        try {
            // Drain stderr concurrently so a PowerShell error doesn't deadlock the test by
            // filling the stderr pipe.
            def stderrDrainer = Thread.start {
                try {
                    byte[] buf = new byte[8192]
                    while (pty.errorStream.read(buf) != -1) { /* discard */ }
                } catch (Throwable ignored) {
                }
            }
            long start = System.nanoTime()
            long received = drainInputStream(pty.inputStream)
            int exitCode = pty.waitFor()
            long elapsedNanos = System.nanoTime() - start
            stderrDrainer.join(5000)
            return new Sample(
                received: received,
                elapsedNanos: elapsedNanos,
                bytesPerSec: received / (elapsedNanos / 1_000_000_000.0d),
                exitCode: exitCode
            )
        } finally {
            pty.close()
        }
    }

    /**
     * Runs the same producer command via {@link ProcessBuilder} (stdout = pipe, no PTY) and
     * drains it through the same {@link InputStream} loop used for the PTY path. The
     * resulting bytes/sec is the baseline against which the PTY relay's slowdown factor
     * is asserted.
     */
    private static Sample runOneBaseline(List<String> cmd) {
        def pb = new ProcessBuilder(cmd)
        pb.redirectErrorStream(false)
        long start = System.nanoTime()
        def process = pb.start()
        try {
            // Drain stderr concurrently to avoid filling its pipe and stalling the producer.
            def stderrDrainer = Thread.start {
                try {
                    byte[] buf = new byte[8192]
                    while (process.errorStream.read(buf) != -1) { /* discard */ }
                } catch (Throwable ignored) {
                }
            }
            long received = drainInputStream(process.inputStream)
            int exitCode = process.waitFor()
            long elapsedNanos = System.nanoTime() - start
            stderrDrainer.join(5000)
            return new Sample(
                received: received,
                elapsedNanos: elapsedNanos,
                bytesPerSec: received / (elapsedNanos / 1_000_000_000.0d),
                exitCode: exitCode
            )
        } finally {
            process.destroy()
        }
    }

    /**
     * Exercises the actual measurement code paths with representative byte volume so
     * JIT compiles the drain loops, the launcher spawn path, and the ProcessBuilder
     * pipe drain before we time anything. Without this, the first measured sample is
     * cold and a cold baseline is ~3x slower than warm, which would make the slowdown
     * ratio look artificially favorable.
     */
    private void warmupPosix(List<String> ptyCmd, List<String> baselineCmd) {
        WARMUP_RUNS.times { runOnePosixRelay(ptyCmd) }
        WARMUP_RUNS.times { runOneBaseline(baselineCmd) }
    }

    private void warmupWindows(List<String> cmd) {
        WARMUP_RUNS.times { runOneWindowsRelay(cmd) }
    }

    private static long drainInputStream(InputStream stream) {
        byte[] buf = new byte[64 * 1024]
        long total = 0L
        int n
        while ((n = stream.read(buf)) != -1) {
            total += n
        }
        return total
    }
}
