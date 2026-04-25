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
import net.rubygrapefruit.platform.terminal.ProcessExitedException;
import net.rubygrapefruit.platform.terminal.PtyProcess;

import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link PtyProcess#getOutputStream()} for the POSIX PTY master fd.
 * Translates post-exit write errors to {@link ProcessExitedException}.
 */
public class PosixPtyOutputStream extends OutputStream {
    private final PosixPtyProcess owner;

    public PosixPtyOutputStream(PosixPtyProcess owner) {
        this.owner = owner;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
        int remaining = len;
        int offset = off;
        while (remaining > 0) {
            int fd = owner.getMasterFd();
            if (fd < 0) {
                throw new ProcessExitedException("PTY master is closed");
            }
            FunctionResult result = new FunctionResult();
            int n = PosixPtyFunctions.nativeWrite(fd, b, offset, remaining, result);
            if (result.isFailed()) {
                int e = result.getErrno();
                if (e == PosixPtyFunctions.EIO || e == PosixPtyFunctions.ENXIO) {
                    throw new ProcessExitedException("process has exited: " + result.getMessage());
                }
                throw new IOException(result.getMessage());
            }
            if (n <= 0) {
                throw new IOException("write returned " + n);
            }
            offset += n;
            remaining -= n;
        }
    }

    @Override
    public void close() {
    }
}
