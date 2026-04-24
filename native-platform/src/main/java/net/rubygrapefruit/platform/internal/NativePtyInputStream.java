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

import java.io.IOException;
import java.io.InputStream;

public class NativePtyInputStream extends InputStream {
    private final PosixPtyProcess owner;
    private final boolean stderr;

    public NativePtyInputStream(PosixPtyProcess owner, boolean stderr) {
        this.owner = owner;
        this.stderr = stderr;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        if (n <= 0) {
            return -1;
        }
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;
        int fd = stderr ? owner.getStderrReadFd() : owner.getMasterFd();
        if (fd < 0) {
            return -1;
        }
        FunctionResult result = new FunctionResult();
        int n = PosixPtyFunctions.nativeRead(fd, b, off, len, result);
        if (result.isFailed()) {
            throw new IOException(result.getMessage());
        }
        return n;
    }

    @Override
    public void close() {
    }
}
