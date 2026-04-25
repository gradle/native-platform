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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * InputStream backed by a queue that a separate drainer thread fills from
 * a native fd. Decouples the consumer's read pace from the kernel-level
 * PTY buffer, which is what lets us guarantee — portably — that bytes
 * written by a fast-exit child are observed by the parent even on POSIX
 * implementations that flush the master read queue on slave close.
 */
public class BufferedPtyInputStream extends InputStream {
    private static final byte[] EOF_MARKER = new byte[0];

    private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
    private volatile IOException error;
    private byte[] current;
    private int currentOffset;

    public void appendChunk(byte[] buf, int len) {
        if (len <= 0) return;
        byte[] copy = new byte[len];
        System.arraycopy(buf, 0, copy, 0, len);
        chunks.add(copy);
    }

    public void signalEof() {
        chunks.add(EOF_MARKER);
    }

    public void signalError(IOException e) {
        error = e;
        chunks.add(EOF_MARKER);
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        if (n <= 0) return -1;
        return one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;
        if (current == null || currentOffset >= current.length) {
            try {
                current = chunks.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            currentOffset = 0;
            if (current == EOF_MARKER) {
                if (error != null) throw error;
                return -1;
            }
        }
        int n = Math.min(len, current.length - currentOffset);
        System.arraycopy(current, currentOffset, b, off, n);
        currentOffset += n;
        return n;
    }

    @Override
    public int available() {
        if (current != null && currentOffset < current.length) {
            return current.length - currentOffset;
        }
        byte[] head = chunks.peek();
        return (head != null && head != EOF_MARKER) ? head.length : 0;
    }

    @Override
    public void close() {
    }
}
