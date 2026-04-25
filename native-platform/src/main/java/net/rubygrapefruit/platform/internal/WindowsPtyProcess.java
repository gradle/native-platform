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
import net.rubygrapefruit.platform.internal.jni.WindowsPtyFunctions;
import net.rubygrapefruit.platform.terminal.PtyProcess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows ConPTY implementation of {@link PtyProcess}.
 *
 * <p>The stdout drainer is started immediately because ConPTY
 * back-pressures the child until someone reads from the master pipe.
 * The child watcher calls {@code ClosePseudoConsole} once
 * {@code WaitForSingleObject} returns so ConPTY flushes any pending
 * output and the drainer sees a broken pipe — ConPTY's master read pipe
 * does not EOF on child exit on its own, unlike the POSIX kernel's
 * hangup of the master fd when the slave closes.</p>
 */
public class WindowsPtyProcess implements PtyProcess {
    private static final long INVALID_HANDLE = -1L;
    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

    private volatile long pid;
    private volatile long hPC;
    private volatile long ptyReadHandle;
    private volatile long ptyWriteHandle;
    private volatile long stderrReadHandle;
    private volatile long processHandle;
    private volatile boolean stderrMerged;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final BufferedPtyInputStream stdoutStream;
    private final BufferedPtyInputStream stderrStream;
    private final WindowsPtyOutputStream outputStream;
    private final Thread stdoutDrainer;
    private volatile Thread stderrDrainer;
    private volatile Thread childWatcher;
    private final Object exitLock = new Object();
    private boolean exited;
    private int exitCode;

    public WindowsPtyProcess(long hPC, long ptyReadHandle, long ptyWriteHandle,
                             long stderrReadHandle, long processHandle, long pid,
                             boolean stderrMerged) {
        this.hPC = hPC;
        this.ptyReadHandle = ptyReadHandle;
        this.ptyWriteHandle = ptyWriteHandle;
        this.stderrReadHandle = stderrReadHandle;
        this.processHandle = processHandle;
        this.pid = pid;
        this.stderrMerged = stderrMerged;
        this.stdoutStream = new BufferedPtyInputStream();
        this.stderrStream = new BufferedPtyInputStream();
        this.outputStream = new WindowsPtyOutputStream(this);
        this.stdoutDrainer = startDrainer(this::drainStdout, "windows-pty-stdout-drainer");
    }

    void attachProcess(long processHandleArg, long pidArg, long stderrReadHandleArg) {
        synchronized (this) {
            this.processHandle = processHandleArg;
            this.pid = pidArg;
            this.stderrReadHandle = stderrReadHandleArg;
            this.stderrMerged = (stderrReadHandleArg == 0L || stderrReadHandleArg == INVALID_HANDLE);
            if (!this.stderrMerged) {
                this.stderrDrainer = startDrainer(this::drainStderr, "windows-pty-stderr-drainer");
            } else {
                this.stderrStream.signalEof();
            }
            this.childWatcher = startDrainer(this::watchChild, "windows-pty-child-watcher");
        }
    }

    private static Thread startDrainer(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void drainStdout() {
        drain(this::currentReadHandle, stdoutStream);
    }

    private void drainStderr() {
        drain(this::currentStderrHandle, stderrStream);
    }

    private long currentReadHandle() {
        return ptyReadHandle;
    }

    private long currentStderrHandle() {
        return stderrReadHandle;
    }

    private static void drain(java.util.function.LongSupplier handleSupplier, BufferedPtyInputStream sink) {
        byte[] buf = new byte[8192];
        try {
            while (true) {
                long h = handleSupplier.getAsLong();
                if (h == 0L || h == INVALID_HANDLE) {
                    sink.signalEof();
                    return;
                }
                FunctionResult r = new FunctionResult();
                int n = WindowsPtyFunctions.nativeRead(h, buf, 0, buf.length, r);
                if (r.isFailed()) {
                    sink.signalError(new IOException(r.getMessage()));
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

    private void watchChild() {
        long h;
        synchronized (this) {
            h = processHandle;
        }
        if (h == 0L || h == INVALID_HANDLE) {
            return;
        }
        FunctionResult r = new FunctionResult();
        int code = WindowsPtyFunctions.waitForProcess(h, r);
        // ClosePseudoConsole flushes ConPTY's internal buffers, closes its
        // writer end, then returns. The stdout drainer then sees a broken
        // pipe and signals EOF on stdoutStream — without this, callers that
        // read pty.inputStream until EOF (Groovy's `text`, etc.) hang on
        // child exit because ConPTY does not auto-shutdown when the child
        // dies. The same call also closes the input pipe, so any pending
        // outputStream.write fails with ERROR_BROKEN_PIPE which
        // WindowsPtyOutputStream surfaces as ProcessExitedException.
        closePseudoConsoleOnce();
        synchronized (exitLock) {
            if (!r.isFailed()) {
                exitCode = code;
            }
            exited = true;
            exitLock.notifyAll();
        }
    }

    private void closePseudoConsoleOnce() {
        long h;
        synchronized (this) {
            h = hPC;
            if (h == 0L || h == INVALID_HANDLE) {
                return;
            }
            hPC = INVALID_HANDLE;
        }
        WindowsPtyFunctions.closePseudoConsole(h, new FunctionResult());
    }

    long getPtyWriteHandle() {
        return ptyWriteHandle;
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
        return stderrMerged ? EMPTY_STREAM : stderrStream;
    }

    @Override
    public synchronized void resize(int cols, int rows) {
        if (hPC == 0 || hPC == INVALID_HANDLE) return;
        FunctionResult result = new FunctionResult();
        WindowsPtyFunctions.resizePseudoConsole(hPC, cols, rows, result);
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
        long h;
        synchronized (this) {
            if (processHandle == 0 || processHandle == INVALID_HANDLE) return;
            h = processHandle;
        }
        synchronized (exitLock) {
            if (exited) return;
        }
        FunctionResult result = new FunctionResult();
        WindowsPtyFunctions.destroyProcess(h, ptyWriteHandle, 500, result);
    }

    @Override
    public void destroyForcibly() {
        long h;
        synchronized (this) {
            if (processHandle == 0 || processHandle == INVALID_HANDLE) return;
            h = processHandle;
        }
        synchronized (exitLock) {
            if (exited) return;
        }
        FunctionResult result = new FunctionResult();
        WindowsPtyFunctions.destroyProcess(h, 0, 0, result);
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
        if (!closed.compareAndSet(false, true)) return;

        // Make sure the child exits so the watcher releases. destroyProcess is
        // a no-op once the child has already exited.
        long procHandleSnapshot;
        synchronized (this) {
            procHandleSnapshot = processHandle;
        }
        if (procHandleSnapshot != 0 && procHandleSnapshot != INVALID_HANDLE) {
            FunctionResult killResult = new FunctionResult();
            WindowsPtyFunctions.destroyProcess(procHandleSnapshot, 0, 0, killResult);
        }

        // ClosePseudoConsole comes before CloseHandle on the master handles.
        // The watcher may have already done it; closePseudoConsoleOnce makes
        // both paths safe.
        closePseudoConsoleOnce();

        Thread watcher;
        Thread stderrThread;
        synchronized (this) {
            watcher = childWatcher;
            stderrThread = stderrDrainer;
        }
        try {
            if (watcher != null) watcher.join(5000);
            stdoutDrainer.join(2000);
            if (stderrThread != null) stderrThread.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        long readH;
        long writeH;
        long stderrH;
        long procH;
        synchronized (this) {
            readH = ptyReadHandle;
            ptyReadHandle = INVALID_HANDLE;
            writeH = ptyWriteHandle;
            ptyWriteHandle = INVALID_HANDLE;
            stderrH = stderrReadHandle;
            stderrReadHandle = INVALID_HANDLE;
            procH = processHandle;
        }

        if (readH != 0 && readH != INVALID_HANDLE) {
            WindowsPtyFunctions.closeHandle(readH, new FunctionResult());
        }
        if (writeH != 0 && writeH != INVALID_HANDLE) {
            WindowsPtyFunctions.closeHandle(writeH, new FunctionResult());
        }
        if (stderrH != 0 && stderrH != INVALID_HANDLE) {
            WindowsPtyFunctions.closeHandle(stderrH, new FunctionResult());
        }
        if (procH != 0 && procH != INVALID_HANDLE) {
            synchronized (exitLock) {
                if (!exited) {
                    FunctionResult waitResult = new FunctionResult();
                    int code = WindowsPtyFunctions.waitForProcess(procH, waitResult);
                    if (!waitResult.isFailed()) {
                        exitCode = code;
                        exited = true;
                        exitLock.notifyAll();
                    }
                }
            }
            WindowsPtyFunctions.closeHandle(procH, new FunctionResult());
            synchronized (this) {
                processHandle = INVALID_HANDLE;
            }
        }

        stdoutStream.signalEof();
        stderrStream.signalEof();
    }
}
