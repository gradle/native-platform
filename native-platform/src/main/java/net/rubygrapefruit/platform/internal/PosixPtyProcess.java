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

package net.rubygrapefruit.platform.internal;

import net.rubygrapefruit.platform.internal.jni.PosixPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * POSIX implementation of {@link PtyProcess}.
 *
 * <p>The launcher uses a two-fork "anchor" pattern (see
 * {@link PosixPtyFunctions#spawnInPty}).  The anchor is the session leader
 * with the slave PTY as its controlling terminal; the user command (the
 * grandchild) runs as a regular member of the anchor's session and process
 * group, so it receives {@code SIGWINCH} on resize but its exit does NOT
 * trigger FreeBSD's {@code killjobc()} -> {@code VOP_REVOKE} ->
 * {@code tty_flush(FWRITE)} flush of the master-readable output queue.</p>
 *
 * <p>The waiter thread reads the grandchild's exit status from
 * {@code infoPipeReadFd} (written by the anchor after its
 * {@code waitpid(grandchild)}), then closes {@code syncPipeWriteFd} to
 * release the anchor.  Only at that point does the kernel run the revoke
 * path on the slave, by which time the master drainer has already
 * consumed every byte.</p>
 */
public class PosixPtyProcess implements PtyProcess {
    private volatile long pid;             // grandchild pid (the user command)
    private volatile long anchorPid;
    private volatile int masterFd;
    private volatile int stderrReadFd;
    private volatile int infoPipeReadFd;
    private volatile int syncPipeWriteFd;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final BufferedPtyInputStream stdoutStream;
    private final BufferedPtyInputStream stderrStream;
    private final PosixPtyOutputStream outputStream;
    private final Thread stdoutDrainer;
    private final Thread stderrDrainer;
    private final Thread waiter;
    private final Object exitLock = new Object();
    private boolean exited;
    private int exitCode;

    public PosixPtyProcess(int masterFd, int stderrReadFd) {
        this.masterFd = masterFd;
        this.stderrReadFd = stderrReadFd;
        this.infoPipeReadFd = -1;
        this.syncPipeWriteFd = -1;
        this.pid = 0L;
        this.anchorPid = 0L;
        this.stdoutStream = new BufferedPtyInputStream();
        this.stderrStream = new BufferedPtyInputStream();
        this.outputStream = new PosixPtyOutputStream(this);
        this.stdoutDrainer = startDrainer(masterFd, stdoutStream, "pty-stdout-drainer-" + masterFd);
        this.stderrDrainer = startDrainer(stderrReadFd, stderrStream, "pty-stderr-drainer-" + stderrReadFd);
        this.waiter = new Thread(this::waitForChild, "pty-waiter");
        this.waiter.setDaemon(true);
    }

    private static Thread startDrainer(int fd, BufferedPtyInputStream sink, String name) {
        Thread t = new Thread(() -> drain(fd, sink), name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void drain(int fd, BufferedPtyInputStream sink) {
        byte[] buf = new byte[8192];
        try {
            while (true) {
                FunctionResult result = new FunctionResult();
                int n = PosixPtyFunctions.nativeRead(fd, buf, 0, buf.length, result);
                if (result.isFailed()) {
                    sink.signalError(new IOException(result.getMessage()));
                    return;
                }
                if (n < 0) {
                    sink.signalEof();
                    return;
                }
                sink.appendChunk(buf, n);
            }
        } catch (Throwable t) {
            sink.signalError(new IOException(t));
        }
    }

    /**
     * Records the anchor + grandchild handles produced by
     * {@link PosixPtyFunctions#spawnInPty} and starts the waiter thread.
     */
    void attachAnchor(long grandchildPid, long anchorPid, int infoPipeReadFd, int syncPipeWriteFd) {
        this.pid = grandchildPid;
        this.anchorPid = anchorPid;
        this.infoPipeReadFd = infoPipeReadFd;
        this.syncPipeWriteFd = syncPipeWriteFd;
        this.waiter.start();
    }

    /**
     * Used by the launcher when {@code spawnInPty} failed.  The native
     * side has already closed slave + stderrWrite; here we close master +
     * stderrRead so the drainers unblock, mark the process as exited so
     * {@link #waitFor()} returns, and skip the normal waiter/anchor path.
     */
    void closeAfterSpawnFailure() {
        synchronized (exitLock) {
            exited = true;
            exitCode = -1;
            exitLock.notifyAll();
        }
        // No waiter has been started (attachAnchor was never called),
        // so suppress the kill path in close() and tear down directly.
        closed.set(true);
        int master;
        int stderr;
        synchronized (this) {
            master = masterFd;
            masterFd = -1;
            stderr = stderrReadFd;
            stderrReadFd = -1;
        }
        if (master >= 0) {
            PosixPtyFunctions.closeFd(master, new FunctionResult());
        }
        if (stderr >= 0) {
            PosixPtyFunctions.closeFd(stderr, new FunctionResult());
        }
        try {
            stdoutDrainer.join(2000);
            stderrDrainer.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForChild() {
        long anchor;
        synchronized (exitLock) {
            anchor = anchorPid;
        }
        if (anchor > 0) {
            waitForChildViaAnchor();
        } else {
            waitForChildViaWaitpid();
        }
    }

    /**
     * BSD-family path.  Read the 4-byte exit status the anchor writes
     * after {@code waitpid(grandchild)}.  Native byte order matches the
     * C++ side.  Then close the sync pipe to release the anchor and
     * {@code waitPid} the anchor so it doesn't linger as a zombie.
     */
    private void waitForChildViaAnchor() {
        int infoFd = infoPipeReadFd;
        byte[] buf = new byte[4];
        int total = 0;
        boolean readOk = false;
        if (infoFd >= 0) {
            while (total < buf.length) {
                FunctionResult r = new FunctionResult();
                int n = PosixPtyFunctions.nativeRead(infoFd, buf, total, buf.length - total, r);
                if (r.isFailed() || n <= 0) {
                    break;
                }
                total += n;
            }
            readOk = (total == buf.length);
        }
        int code = readOk
                ? ByteBuffer.wrap(buf).order(ByteOrder.nativeOrder()).getInt()
                : -1;
        synchronized (exitLock) {
            exitCode = code;
            exited = true;
            exitLock.notifyAll();
        }
        // Release the anchor.  Closing the sync pipe write end unblocks
        // its read; the anchor _exits, the kernel runs killjobc on it (no
        // longer on the grandchild — that exited harmlessly inside the
        // anchor's session), the slave PTY is revoked, and master sees
        // EOF.  By this point the master drainer has already consumed
        // everything the grandchild wrote.
        int sync;
        synchronized (this) {
            sync = syncPipeWriteFd;
            syncPipeWriteFd = -1;
        }
        if (sync >= 0) {
            PosixPtyFunctions.closeFd(sync, new FunctionResult());
        }
        long anchor;
        synchronized (exitLock) {
            anchor = anchorPid;
            anchorPid = 0;
        }
        if (anchor > 0) {
            PosixPtyFunctions.waitPid(anchor, new FunctionResult());
        }
        int info;
        synchronized (this) {
            info = infoPipeReadFd;
            infoPipeReadFd = -1;
        }
        if (info >= 0) {
            PosixPtyFunctions.closeFd(info, new FunctionResult());
        }
    }

    /**
     * Linux / macOS path.  No anchor process: the child IS the session
     * leader and the daemon waitpid's it directly.  These kernels don't
     * synchronously flush the master-readable queue on session-leader
     * exit, so no anchor is needed.
     */
    private void waitForChildViaWaitpid() {
        long childPid;
        synchronized (exitLock) {
            childPid = pid;
        }
        FunctionResult result = new FunctionResult();
        int code = PosixPtyFunctions.waitPid(childPid, result);
        synchronized (exitLock) {
            if (!result.isFailed()) {
                exitCode = code;
            }
            exited = true;
            exitLock.notifyAll();
        }
    }

    int getMasterFd() {
        return masterFd;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public InputStream getInputStream() {
        return stdoutStream;
    }

    @Override
    public InputStream getErrorStream() {
        return stderrStream;
    }

    @Override
    public synchronized void resize(int cols, int rows) {
        int fd = masterFd;
        if (fd < 0) {
            return;
        }
        FunctionResult result = new FunctionResult();
        PosixPtyFunctions.setPtySize(fd, cols, rows, result);
    }

    @Override
    public long getPid() {
        return pid;
    }

    @Override
    public int waitFor() throws InterruptedException {
        synchronized (exitLock) {
            while (!exited) {
                exitLock.wait();
            }
            return exitCode;
        }
    }

    @Override
    public void destroy() {
        long p;
        synchronized (exitLock) {
            if (exited || pid <= 0) return;
            p = pid;
        }
        FunctionResult result = new FunctionResult();
        PosixPtyFunctions.killProcess(p, PosixPtyFunctions.SIGTERM, result);
    }

    @Override
    public void destroyForcibly() {
        long p;
        synchronized (exitLock) {
            if (exited || pid <= 0) return;
            p = pid;
        }
        FunctionResult result = new FunctionResult();
        PosixPtyFunctions.killProcess(p, PosixPtyFunctions.SIGKILL, result);
    }

    @Override
    public boolean isAlive() {
        synchronized (exitLock) {
            return !exited;
        }
    }

    @Override
    public int exitValue() {
        synchronized (exitLock) {
            if (!exited) {
                throw new IllegalStateException("Process has not exited");
            }
            return exitCode;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // SIGKILL the grandchild if still alive.  The anchor will reap it
        // and forward the exit status; the waiter then releases the anchor
        // and the master sees EOF.
        long p;
        synchronized (exitLock) {
            p = exited ? 0 : pid;
        }
        if (p > 0) {
            FunctionResult killResult = new FunctionResult();
            PosixPtyFunctions.killProcess(p, PosixPtyFunctions.SIGKILL, killResult);
        }
        try {
            waiter.join(5000);
            stdoutDrainer.join(2000);
            stderrDrainer.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        int master;
        int stderr;
        synchronized (this) {
            master = masterFd;
            masterFd = -1;
            stderr = stderrReadFd;
            stderrReadFd = -1;
        }
        if (master >= 0) {
            PosixPtyFunctions.closeFd(master, new FunctionResult());
        }
        if (stderr >= 0) {
            PosixPtyFunctions.closeFd(stderr, new FunctionResult());
        }
    }
}
