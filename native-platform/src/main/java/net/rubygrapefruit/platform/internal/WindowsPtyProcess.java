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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class WindowsPtyProcess implements PtyProcess {
    private static final long INVALID_HANDLE = -1L;

    private final long pid;
    private volatile long hPC;
    private volatile long ptyReadHandle;
    private volatile long ptyWriteHandle;
    private volatile long stderrReadHandle;
    private volatile long processHandle;
    private final boolean stderrMerged;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread drainer;
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
        this.drainer = new Thread(this::drainLoop, "windows-pty-drainer-" + pid);
        this.drainer.setDaemon(true);
        this.drainer.start();
    }

    private void drainLoop() {
        byte[] buf = new byte[8192];
        while (true) {
            long h = ptyReadHandle;
            if (h == 0 || h == INVALID_HANDLE) return;
            FunctionResult r = new FunctionResult();
            int n = WindowsPtyFunctions.nativeRead(h, buf, 0, buf.length, r);
            if (n < 0) return;
            if (r.isFailed()) return;
        }
    }

    @Override
    public OutputStream getOutputStream() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public InputStream getInputStream() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
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
        if (pcH != 0 && pcH != INVALID_HANDLE) {
            WindowsPtyFunctions.closePseudoConsole(pcH, new FunctionResult());
        }
        try {
            drainer.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            if (!exited && procH != 0 && procH != INVALID_HANDLE) {
                FunctionResult killResult = new FunctionResult();
                WindowsPtyFunctions.destroyProcess(procH, 0, 0, killResult);
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
    }
}
