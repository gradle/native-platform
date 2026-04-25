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
        // Start the stdout drainer immediately, before the child is attached
        // via attachProcess(). ConPTY emits its startup VT output as soon as
        // the child runs and would back-pressure the child if no one is
        // reading from ptyReadHandle. The drainer feeds the buffered
        // InputStream so callers see the bytes; if no caller reads, the bytes
        // accumulate until close() — which is fine for short-lived children.
        this.stdoutDrainer = startDrainer(this::drainStdout, "windows-pty-stdout-drainer");
    }

    void attachProcess(long processHandle, long pid, long stderrReadHandleArg) {
        synchronized (this) {
            this.processHandle = processHandle;
            this.pid = pid;
            this.stderrReadHandle = stderrReadHandleArg;
            this.stderrMerged = (stderrReadHandleArg == 0L || stderrReadHandleArg == INVALID_HANDLE);
            if (!this.stderrMerged) {
                this.stderrDrainer = startDrainer(this::drainStderr, "windows-pty-stderr-drainer");
            } else {
                this.stderrStream.signalEof();
            }
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

    public long getPtyWriteHandle() {
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
    public synchronized int waitFor() throws InterruptedException {
        if (exited) return exitCode;
        long h = processHandle;
        if (h == 0 || h == INVALID_HANDLE) {
            throw new IllegalStateException("Process handle is closed");
        }
        FunctionResult result = new FunctionResult();
        int code = WindowsPtyFunctions.waitForProcess(h, result);
        if (result.isFailed()) {
            throw new NativeException("Could not wait for process: " + result.getMessage());
        }
        exitCode = code;
        exited = true;
        return exitCode;
    }

    @Override
    public synchronized void destroy() {
        if (exited || processHandle == 0 || processHandle == INVALID_HANDLE) return;
        FunctionResult result = new FunctionResult();
        WindowsPtyFunctions.destroyProcess(processHandle, ptyWriteHandle, 500, result);
    }

    @Override
    public synchronized void destroyForcibly() {
        if (exited || processHandle == 0 || processHandle == INVALID_HANDLE) return;
        FunctionResult result = new FunctionResult();
        WindowsPtyFunctions.destroyProcess(processHandle, 0, 0, result);
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
        if (!closed.compareAndSet(false, true)) return;

        long readH;
        long writeH;
        long stderrH;
        long pcH;
        long procH;
        Thread stderrThread;
        synchronized (this) {
            readH = ptyReadHandle;
            ptyReadHandle = INVALID_HANDLE;
            writeH = ptyWriteHandle;
            ptyWriteHandle = INVALID_HANDLE;
            stderrH = stderrReadHandle;
            stderrReadHandle = INVALID_HANDLE;
            pcH = hPC;
            hPC = INVALID_HANDLE;
            procH = processHandle;
            stderrThread = stderrDrainer;
        }

        // Kill the child first so ConPTY's writer side stops producing.
        if (procH != 0 && procH != INVALID_HANDLE) {
            FunctionResult killResult = new FunctionResult();
            WindowsPtyFunctions.destroyProcess(procH, 0, 0, killResult);
        }

        // ClosePseudoConsole comes before CloseHandle on the master handles:
        // ConPTY shuts down its writer end as part of teardown, the drainer's
        // blocked ReadFile returns EOF, and the drainer exits cleanly. Closing
        // the master handles first while the drainer is still parked in
        // ReadFile would yank the handle from under an in-flight syscall, with
        // results undocumented enough to avoid.
        if (pcH != 0 && pcH != INVALID_HANDLE) {
            WindowsPtyFunctions.closePseudoConsole(pcH, new FunctionResult());
        }
        try {
            stdoutDrainer.join(2000);
            if (stderrThread != null) stderrThread.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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

        synchronized (this) {
            if (!exited && procH != 0 && procH != INVALID_HANDLE) {
                FunctionResult waitResult = new FunctionResult();
                int code = WindowsPtyFunctions.waitForProcess(procH, waitResult);
                if (!waitResult.isFailed()) {
                    exitCode = code;
                    exited = true;
                }
            }
            if (procH != 0 && procH != INVALID_HANDLE) {
                WindowsPtyFunctions.closeHandle(procH, new FunctionResult());
                processHandle = INVALID_HANDLE;
            }
        }

        stdoutStream.signalEof();
        stderrStream.signalEof();
    }
}
