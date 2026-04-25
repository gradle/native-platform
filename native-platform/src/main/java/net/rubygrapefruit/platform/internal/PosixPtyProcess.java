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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.PosixPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class PosixPtyProcess implements PtyProcess {
    private volatile long pid;
    private volatile int masterFd;
    private volatile int slaveFd;
    private volatile int stderrReadFd;
    private volatile int stderrWriteFd;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final BufferedPtyInputStream stdoutStream;
    private final BufferedPtyInputStream stderrStream;
    private final NativePtyOutputStream outputStream;
    private final Thread stdoutDrainer;
    private final Thread stderrDrainer;
    private final Thread waiter;
    private final Object exitLock = new Object();
    private boolean exited;
    private int exitCode;

    public PosixPtyProcess(int masterFd, int slaveFd, int stderrReadFd, int stderrWriteFd) {
        this.masterFd = masterFd;
        this.slaveFd = slaveFd;
        this.stderrReadFd = stderrReadFd;
        this.stderrWriteFd = stderrWriteFd;
        this.pid = 0L;
        this.stdoutStream = new BufferedPtyInputStream();
        this.stderrStream = new BufferedPtyInputStream();
        this.outputStream = new NativePtyOutputStream(this);
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

    void attachPid(long pid) {
        this.pid = pid;
        this.waiter.start();
    }

    // WORKAROUND. POSIX gives no portable primitive for "wait until thread
    // X has entered blocking syscall Y", and we need the drainer threads
    // parked in their first nativeRead before the child can write-and-exit
    // — otherwise strict POSIX implementations (FreeBSD's pty driver)
    // flush the line discipline buffer and we lose the child's output.
    // Linux and macOS preserve buffered output across slave close in their
    // current pty drivers, so they don't observe the race and pay nothing.
    // Anywhere else we sleep ~50 ms, which is empirically enough to let
    // the drainer threads reach their syscall under 4-way concurrent
    // spawn pressure, and only ~50 ms slower than the structural cost on
    // the platforms that need it.
    void awaitDrainersScheduled() {
        if (Platform.current().isLinux() || Platform.current().isMacOs()) {
            return;
        }
        Thread.yield();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Used by the launcher when spawnInPty failed and already closed the
     * parent's slave/stderrWrite fds. */
    void markSlaveAlreadyClosed() {
        synchronized (exitLock) {
            slaveFd = -1;
            stderrWriteFd = -1;
            exited = true;
            exitLock.notifyAll();
        }
    }

    private void waitForChild() {
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
        // Slave + stderr-write were held open in the parent until the child
        // was reaped. Closing them now drops the slave's last reference, so
        // master sees EOF and the drainers exit. Holding them this long is
        // what closes the FreeBSD-style "discard on slave close before any
        // reader is parked" race: the slave never fully closes until the
        // parent decides to, and at that point the child is already gone
        // and any pending output has flowed through the line discipline.
        int s;
        int sw;
        synchronized (exitLock) {
            s = slaveFd;
            slaveFd = -1;
            sw = stderrWriteFd;
            stderrWriteFd = -1;
        }
        if (s >= 0) {
            PosixPtyFunctions.closeFd(s, new FunctionResult());
        }
        if (sw >= 0) {
            PosixPtyFunctions.closeFd(sw, new FunctionResult());
        }
    }

    public int getMasterFd() {
        return masterFd;
    }

    public int getStderrReadFd() {
        return stderrReadFd;
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
        // Kill the child if still alive. The waiter thread will then return
        // from waitpid, mark exited, close the parent's slave + stderr-write
        // copies, the drainers see EOF and exit.
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
