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
 * <p>The assertion is calibration-relative: in the same run we measure a baseline by
 * running <em>the same producer</em> ({@code dd if=/dev/zero} on POSIX, the PowerShell
 * tight-write loop on Windows) through a plain non-PTY pipe ({@link ProcessBuilder}),
 * drained via the same Java {@link InputStream} pattern as the PTY path. Comparing
 * those two measurements isolates the cost of the PTY/ConPTY relay layer on top of
 * an OS pipe + Java I/O baseline. The ratio is robust across CI agents of differing
 * absolute speed: a slow agent slows both legs proportionally.</p>
 *
 * <p>Each measurement is the median of three samples and is preceded by warmup runs
 * of the same code paths, so JIT and the kernel page cache are primed before timing
 * starts. The test is a sentinel, not a precise benchmark.</p>
 *
 * <p>Windows uses an absolute MB/s floor on the relay rather than a slowdown ratio.
 * Calibration showed the PowerShell-via-pipe baseline is too unstable on Windows
 * agents (4.5x variance dominated by PowerShell startup) to be a reliable
 * denominator; the ConPTY relay's own MB/s is far more stable. The slowdown ratio
 * is still printed for diagnostic visibility but is not asserted on.</p>
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
     * Per-platform POSIX slowdown floors: the test fails if PTY relay throughput is
     * more than this many times slower than the baseline (same dd command, same
     * drain loop, no PTY).
     *
     * <p>Tuned to roughly 1.7x of the observed maximum on each kernel across 5 TC
     * runs per agent. Catches roughly a 1.7x relay regression per platform without
     * depending on absolute MB/s. Structural overhead differs sharply between
     * kernels (a single global floor would have to be loose to pass the slowest
     * platform, leaving the others under-constrained):</p>
     * <ul>
     *   <li>Linux / FreeBSD agents observed 2.62x to 5.88x across 25 runs (max on
     *       Ubuntu amd64). Floor 10 leaves ~1.7x headroom over the worst case.</li>
     *   <li>macOS agents observed 6.88x (amd64; very stable) and 9.07-12.32x
     *       (aarch64; ~36% variance) across 10 runs. Apple Silicon has a much
     *       faster pipe baseline (~2 GB/s vs ~0.5-1 GB/s on other agents), which
     *       inflates the ratio even though the relay itself runs faster
     *       absolutely. Floor 20 leaves ~1.6x headroom over the worst case.</li>
     * </ul>
     *
     * <p>TODO: remove the {@code PTY-RELAY-RATIO-POSIX} println after enough runs
     * confirm the floors hold without flakiness.</p>
     */
    private static final int MAX_SLOWDOWN_LINUX_FREEBSD = 10
    private static final int MAX_SLOWDOWN_MACOS = 20

    private static int maxSlowdownForCurrentPlatform() {
        def p = Platform.current()
        if (p.linux || p.freeBSD) return MAX_SLOWDOWN_LINUX_FREEBSD
        if (p.macOs) return MAX_SLOWDOWN_MACOS
        throw new IllegalStateException("Unsupported POSIX platform for throughput test: " + p)
    }

    /**
     * Windows uses an absolute MB/s floor on the relay, not a slowdown ratio. The
     * pipe-baseline approach we use on POSIX is unreliable here: across 5 TC runs
     * the PowerShell-via-pipe baseline swung from 13.78 MB/s to 61.72 MB/s (a 4.5x
     * spread, dominated by PowerShell startup variance), while the ConPTY relay was
     * stable at 1.12-1.48 MB/s (~32% spread). A noisy baseline can make the
     * slowdown ratio look favorable when the relay is regressing, hiding real
     * problems behind the wrong noise.
     *
     * <p>Floor of 0.7 MB/s leaves ~37% headroom over the slowest observed relay
     * (1.12 MB/s) and ~46% over the median (~1.3 MB/s). Catches a meaningful
     * regression while remaining immune to baseline variance.</p>
     */
    private static final double MIN_RELAY_MBPS_WINDOWS = 0.7d

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
        slowdown <= maxSlowdownForCurrentPlatform()
    }

    @IgnoreIf({ !Platform.current().windows })
    def "Windows: PTY relay throughput is non-trivial"() {
        given:
        int frameCount = (MEGABYTES * 1024).intdiv(FRAME_KB)
        // PowerShell tight-write loop via [Console]::Out (TextWriter). Pin OutputEncoding
        // to UTF-8 (no BOM) so 'A' (0x41) is emitted as a single byte regardless of
        // whether stdout is a pipe (baseline leg) or ConPTY (PTY leg). Without pinning,
        // PowerShell's redirected-output default may differ from its console default
        // (BOMs, UTF-16, console codepage) and the byte streams would not be comparable.
        //
        // Earlier attempts using [Console]::OpenStandardOutput().Write(byte[]) with null
        // bytes produced only ~93 bytes through ConPTY; null bytes appear to get
        // swallowed by the console-emulation layer somewhere between the .NET stream
        // and the master pipe. Writing printable ASCII via TextWriter survives the
        // ConPTY boundary and matches the character-stream nature of real TUI output.
        //
        // Use -EncodedCommand to sidestep CommandLineToArgvW quoting fragility.
        def script = ("\$ErrorActionPreference='Stop'; " +
            "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding \$false; " +
            "\$s='A'*${FRAME_KB * 1024}; " +
            "for(\$i=0; \$i -lt ${frameCount}; \$i++){[Console]::Out.Write(\$s)}; " +
            "[Console]::Out.Flush()").toString()
        def encoded = Base64.encoder.encodeToString(script.getBytes("UTF-16LE"))
        def cmd = ['powershell.exe', '-NoProfile', '-EncodedCommand', encoded]
        warmupWindows(cmd)

        when:
        // Same producer through a plain pipe (no ConPTY) for the printed diagnostic
        // ratio. Not used for assertion: across 5 TC runs the PowerShell-via-pipe
        // baseline swung 13-62 MB/s while the ConPTY relay was stable at 1.12-1.48
        // MB/s, so a baseline-relative assertion would hide regressions behind
        // baseline noise. We assert on the relay's absolute MB/s instead.
        def baselines = (1..SAMPLE_COUNT).collect { runOneBaseline(cmd) }
        def baselineMedian = baselines.sort { it.bytesPerSec }[SAMPLE_COUNT.intdiv(2)]
        def samples = (1..SAMPLE_COUNT).collect { runOneWindowsRelay(cmd) }
        def relayMedian = samples.sort { it.bytesPerSec }[SAMPLE_COUNT.intdiv(2)]
        double relayMbps = relayMedian.bytesPerSec / (1024.0d * 1024.0d)
        double baselineMbps = baselineMedian.bytesPerSec / (1024.0d * 1024.0d)

        // Diagnostic: keep printing the ratio so drift in either leg is visible
        // over time, even though the assertion below is on relay MB/s only.
        println String.format(
            "PTY-RELAY-RATIO-WINDOWS: relay=%.2f MB/s, baseline=%.2f MB/s, slowdown=%.2fx",
            relayMbps, baselineMbps, baselineMedian.bytesPerSec / relayMedian.bytesPerSec)

        then:
        relayMedian.exitCode == 0
        baselineMedian.exitCode == 0
        relayMbps >= MIN_RELAY_MBPS_WINDOWS
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
        WARMUP_RUNS.times { runOneBaseline(cmd) }
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
