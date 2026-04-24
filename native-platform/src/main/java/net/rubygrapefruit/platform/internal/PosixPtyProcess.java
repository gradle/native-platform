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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class PosixPtyProcess implements PtyProcess {
    private final long pid;
    private int masterFd;
    private int stderrReadFd;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean exited;
    private int exitCode;

    public PosixPtyProcess(int masterFd, int stderrReadFd, long pid) {
        this.masterFd = masterFd;
        this.stderrReadFd = stderrReadFd;
        this.pid = pid;
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
    public void resize(int cols, int rows) {
        throw new UnsupportedOperationException("not yet implemented");
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
    public void destroy() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public void destroyForcibly() {
        throw new UnsupportedOperationException("not yet implemented");
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
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (masterFd >= 0) {
            FunctionResult result = new FunctionResult();
            PosixPtyFunctions.closeFd(masterFd, result);
            masterFd = -1;
        }
        if (stderrReadFd >= 0) {
            FunctionResult result = new FunctionResult();
            PosixPtyFunctions.closeFd(stderrReadFd, result);
            stderrReadFd = -1;
        }
    }
}
