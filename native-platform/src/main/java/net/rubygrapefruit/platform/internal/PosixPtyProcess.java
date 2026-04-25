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
    private volatile int stderrReadFd;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final BufferedPtyInputStream stdoutStream;
    private final BufferedPtyInputStream stderrStream;
    private final NativePtyOutputStream outputStream;
    private final Thread stdoutDrainer;
    private final Thread stderrDrainer;
    private boolean exited;
    private int exitCode;

    public PosixPtyProcess(int masterFd, int stderrReadFd, long pid) {
        this.masterFd = masterFd;
        this.stderrReadFd = stderrReadFd;
        this.pid = pid;
        this.stdoutStream = new BufferedPtyInputStream();
        this.stderrStream = new BufferedPtyInputStream();
        this.outputStream = new NativePtyOutputStream(this);
        // Start drainers before the caller invokes attachPid() and therefore
        // before the child can write a byte. POSIX leaves master-read after
        // slave close implementation-defined (Linux/macOS preserve buffered
        // output, FreeBSD discards it), so the only portable guarantee is
        // that a read is parked on the master before the child can exit.
        this.stdoutDrainer = startDrainer(masterFd, stdoutStream, "pty-stdout-drainer-" + masterFd);
        this.stderrDrainer = startDrainer(stderrReadFd, stderrStream, "pty-stderr-drainer-" + stderrReadFd);
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
        synchronized (this) {
            this.pid = pid;
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
    public synchronized int waitFor() throws InterruptedException {
        if (exited) {
            return exitCode;
        }
        FunctionResult result = new FunctionResult();
        int code = PosixPtyFunctions.waitPid(pid, result);
        if (result.isFailed()) {
            throw new NativeException("Could not wait for PTY process: " + result.getMessage());
        }
        exitCode = code;
        exited = true;
        return exitCode;
    }

    @Override
    public synchronized void destroy() {
        if (exited || pid <= 0) {
            return;
        }
        FunctionResult result = new FunctionResult();
        PosixPtyFunctions.killProcess(pid, PosixPtyFunctions.SIGTERM, result);
    }

    @Override
    public synchronized void destroyForcibly() {
        if (exited || pid <= 0) {
            return;
        }
        FunctionResult result = new FunctionResult();
        PosixPtyFunctions.killProcess(pid, PosixPtyFunctions.SIGKILL, result);
    }

    @Override
    public synchronized boolean isAlive() {
        return !exited;
    }

    @Override
    public synchronized int exitValue() {
        if (!exited) {
            throw new IllegalStateException("Process has not exited");
        }
        return exitCode;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Kill+reap before closing the master fd. The drainers are parked in
        // read(masterFd) / read(stderrReadFd); on POSIX, close(fd) does not
        // reliably unblock another thread's blocked read on that same fd, so
        // we lean on the slave-side close instead — when the child dies the
        // slave fds close, the master sees EOF/EIO, nativeRead returns -1,
        // each drainer signals EOF and exits.
        synchronized (this) {
            if (!exited && pid > 0) {
                FunctionResult killResult = new FunctionResult();
                PosixPtyFunctions.killProcess(pid, PosixPtyFunctions.SIGKILL, killResult);
                FunctionResult waitResult = new FunctionResult();
                int code = PosixPtyFunctions.waitPid(pid, waitResult);
                if (!waitResult.isFailed()) {
                    exitCode = code;
                    exited = true;
                }
            }
        }
        joinDrainers();

        int master;
        int stderr;
        synchronized (this) {
            master = masterFd;
            masterFd = -1;
            stderr = stderrReadFd;
            stderrReadFd = -1;
        }
        if (master >= 0) {
            FunctionResult result = new FunctionResult();
            PosixPtyFunctions.closeFd(master, result);
        }
        if (stderr >= 0) {
            FunctionResult result = new FunctionResult();
            PosixPtyFunctions.closeFd(stderr, result);
        }
    }

    private void joinDrainers() {
        try {
            stdoutDrainer.join(2000);
            stderrDrainer.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
