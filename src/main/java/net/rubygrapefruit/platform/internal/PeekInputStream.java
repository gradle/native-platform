package net.rubygrapefruit.platform.internal;

import java.io.IOException;
import java.io.InputStream;

public class PeekInputStream extends InputStream {
    private final InputStream delegate;
    private byte[] buffer;
    private int charsBuffered;

    public PeekInputStream(InputStream delegate) {
        this.delegate = delegate;
        buffer = new byte[4];
    }

    public int peek(int depth) throws IOException {
        if (depth < charsBuffered) {
            return buffer[depth];
        }
        if (depth != charsBuffered) {
            throw new UnsupportedOperationException("Not yet implemented.");
        }
        int ch = delegate.read();
        if (ch < 0) {
            return ch;
        }
        buffer[charsBuffered] = (byte) ch;
        charsBuffered++;
        return ch;
    }

    public void consumeAll() {
        charsBuffered = 0;
    }

    @Override
    public int read() throws IOException {
        if (charsBuffered > 0) {
            byte result = buffer[0];
            charsBuffered--;
            for (int i = 0; i < charsBuffered; i++) {
                buffer[i] = buffer[i + 1];
            }
            return result;
        }
        return delegate.read();
    }
}

