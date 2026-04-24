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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class PosixPtyProcess implements PtyProcess {
    private final long pid;
    private volatile int masterFd;
    private volatile int stderrReadFd;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final NativePtyInputStream inputStream;
    private final NativePtyInputStream errorStream;
    private final NativePtyOutputStream outputStream;
    private boolean exited;
    private int exitCode;

    public PosixPtyProcess(int masterFd, int stderrReadFd, long pid) {
        this.masterFd = masterFd;
        this.stderrReadFd = stderrReadFd;
        this.pid = pid;
        this.inputStream = new NativePtyInputStream(this, false);
        this.errorStream = new NativePtyInputStream(this, true);
        this.outputStream = new NativePtyOutputStream(this);
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
        return inputStream;
    }

    @Override
    public InputStream getErrorStream() {
        return errorStream;
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
        synchronized (this) {
            if (exited || pid <= 0) {
                return;
            }
        }
        FunctionResult killResult = new FunctionResult();
        PosixPtyFunctions.killProcess(pid, PosixPtyFunctions.SIGKILL, killResult);
        FunctionResult waitResult = new FunctionResult();
        int code = PosixPtyFunctions.waitPid(pid, waitResult);
        synchronized (this) {
            if (!waitResult.isFailed()) {
                exitCode = code;
                exited = true;
            }
        }
    }
}
