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

package net.rubygrapefruit.platform.terminal;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A child process connected to a pseudo-terminal.
 *
 * <p>The child's stdin and stdout are connected to the slave side of a PTY.
 * The child's stderr is connected to a separate pipe so that the caller can
 * read stdout and stderr independently.</p>
 *
 * <p>From the child's perspective:</p>
 * <ul>
 *   <li>fd 0 (stdin)  &mdash; PTY slave ({@code isatty()} returns {@code true})</li>
 *   <li>fd 1 (stdout) &mdash; PTY slave ({@code isatty()} returns {@code true})</li>
 *   <li>fd 2 (stderr) &mdash; pipe     ({@code isatty()} returns {@code false})</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>The following methods are safe to call concurrently with stream
 * reads/writes and with each other: {@link #resize}, {@link #destroy},
 * {@link #isAlive}, {@link #waitFor}. Stream objects returned by
 * {@link #getInputStream}, {@link #getOutputStream}, and
 * {@link #getErrorStream} are <em>not</em> thread-safe &mdash; callers must
 * synchronize externally if multiple threads access the same stream.</p>
 *
 * <h3>PTY master read behavior on child exit</h3>
 * <p>When the child exits and the slave side of the PTY is closed, reading
 * from {@link #getInputStream()} returns EOF. On Linux the kernel signals
 * this as {@code EIO} on the master fd. On macOS it typically returns
 * 0 (clean EOF), but {@code EIO} or {@code ENXIO} can also occur depending
 * on timing. The implementation normalizes all of these to standard EOF
 * behavior (returning {@code -1} from {@code read()}) so callers do not
 * see an {@code IOException} for normal child termination.</p>
 */
public interface PtyProcess extends AutoCloseable {

    /**
     * Returns an output stream connected to the master side of the PTY.
     * Writing to this stream delivers bytes to the child's stdin.
     */
    OutputStream getOutputStream();

    /**
     * Returns an input stream connected to the master side of the PTY.
     * Reading from this stream receives bytes written by the child to
     * stdout (fd 1).
     *
     * <p>Note: because a PTY echoes input by default, bytes written via
     * {@link #getOutputStream()} may also appear on this stream unless
     * the child has switched the PTY to raw mode.</p>
     */
    InputStream getInputStream();

    /**
     * Returns an input stream connected to the read end of the stderr pipe.
     * Reading from this stream receives bytes written by the child to
     * stderr (fd 2).
     *
     * <p>This stream is independent of the PTY and does not carry any
     * terminal escape sequences from the TUI.</p>
     */
    InputStream getErrorStream();

    /**
     * Changes the terminal size reported to the child.
     * On POSIX this delivers SIGWINCH to the child process group.
     *
     * @param cols Number of columns (width in characters).
     * @param rows Number of rows (height in characters).
     */
    void resize(int cols, int rows);

    /**
     * Returns the OS process ID of the child.
     */
    long getPid();

    /**
     * Waits for the child process to exit and returns its exit code.
     */
    int waitFor() throws InterruptedException;

    /**
     * Requests the child process to terminate gracefully.
     *
     * <p>On POSIX, sends SIGTERM. On Windows, writes Ctrl+C ({@code \u0003})
     * to the PTY input pipe (which ConPTY translates to CTRL_C_EVENT),
     * then waits briefly for exit. If the child does not exit within the
     * grace period, falls back to forceful termination.</p>
     *
     * @see #destroyForcibly()
     */
    void destroy();

    /**
     * Forcefully terminates the child process without giving it a chance
     * to clean up.
     *
     * <p>On POSIX, sends SIGKILL. On Windows, calls TerminateProcess.</p>
     */
    void destroyForcibly();

    /**
     * Returns true if the child process has exited.
     */
    boolean isAlive();

    /**
     * Returns the exit code.  Only valid after the process has exited.
     *
     * @throws IllegalStateException if the process is still running.
     */
    int exitValue();

    /**
     * Closes the PTY master file descriptor, the stderr pipe, and releases
     * native resources.  If the child is still running it is destroyed first.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    @Override
    void close();
}
