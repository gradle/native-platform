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

package net.rubygrapefruit.platform.internal.jni;

import net.rubygrapefruit.platform.internal.FunctionResult;

public class PosixPtyFunctions {

    public static final int EIO = 5;
    public static final int ENXIO = 6;
    public static final int SIGTERM = 15;
    public static final int SIGKILL = 9;

    public static native boolean isPtyAvailable();

    /**
     * Allocates the PTY pair plus the stderr pipe and applies the initial
     * terminal size. Does not fork. Returns four fds in {@code outFds}:
     * {@code [masterFd, slaveFd, stderrReadFd, stderrWriteFd]}. The caller
     * is expected to start drainer threads on {@code masterFd} and
     * {@code stderrReadFd} before invoking {@link #spawnInPty}, then hand
     * {@code slaveFd} and {@code stderrWriteFd} to that call.
     */
    public static native void createPty(int cols, int rows,
                                        int[] outFds,
                                        FunctionResult result);

    /**
     * Forks and execs the child inside an existing PTY allocated by
     * {@link #createPty}.
     *
     * <p>Two forks are performed.  An "anchor" process is forked first and
     * calls {@code setsid} + {@code ioctl(TIOCSCTTY)} so the slave PTY
     * becomes the controlling terminal of the anchor's session.  The
     * anchor then forks a "grandchild" that {@code dup2}s the slave/stderr
     * fds and {@code execve}s the user command.  The anchor stays alive
     * past the grandchild's exit, blocked on a sync pipe; the caller
     * releases it by closing {@code outAux[2]} once the master has been
     * fully drained.</p>
     *
     * <p>Why two forks: on FreeBSD the kernel runs
     * {@code killjobc()} → {@code VOP_REVOKE(s_ttyvp, REVOKEALL)} →
     * {@code ttydev_close(FREVOKE)} → {@code tty_flush(FWRITE)} →
     * {@code ttyoutq_flush(&t_outq)} synchronously when a session leader
     * exits with a controlling terminal.  That flush discards any
     * slave-to-master bytes the master has not yet read.  Holding an extra
     * slave fd in the parent does not help because {@code REVOKEALL}
     * invalidates every aliased fd.  By making the anchor (not the user
     * command) the session leader, the flush only fires after we have
     * already drained the queue.  Linux and macOS gain identical
     * structural plumbing at trivial cost.</p>
     *
     * <p>Closes {@code slaveFd} and {@code stderrWriteFd} in the parent
     * regardless of outcome.  On success, populates {@code outAux} with
     * three values (widened to {@code long}):</p>
     * <ul>
     *   <li>{@code outAux[0]} — anchor pid; the caller should
     *       {@code waitPid} this after the anchor has been released.</li>
     *   <li>{@code outAux[1]} — info pipe read fd; reading it yields the
     *       grandchild's exit status (4 bytes, native int).</li>
     *   <li>{@code outAux[2]} — sync pipe write fd; closing it releases
     *       the anchor.</li>
     * </ul>
     *
     * <p>Returns the grandchild pid via the JNI return value.</p>
     */
    public static native long spawnInPty(int slaveFd, int stderrWriteFd,
                                         String[] command,
                                         String[] environment,
                                         String workingDir,
                                         long[] outAux,
                                         FunctionResult result);

    public static native int waitPid(long pid, FunctionResult result);

    public static native void closeFd(int fd, FunctionResult result);

    public static native void setPtySize(int masterFd, int cols, int rows, FunctionResult result);

    public static native void killProcess(long pid, int signal, FunctionResult result);

    public static native int nativeRead(int fd, byte[] buf, int off, int len, FunctionResult result);

    public static native int nativeWrite(int fd, byte[] buf, int off, int len, FunctionResult result);
}
