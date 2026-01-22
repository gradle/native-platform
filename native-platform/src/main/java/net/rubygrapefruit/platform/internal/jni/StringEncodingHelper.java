package net.rubygrapefruit.platform.internal.jni;

import java.nio.charset.StandardCharsets;

/**
 * Helper class for string encoding conversions between Java and native code.
 *
 * <p>
 * Doing UTF-8 &lt;-&gt; UTF-16 conversions in C(++) without libraries is currently inefficient and error-prone, as
 * documented at <a href="https://thephd.dev/the-c-c++-rust-string-text-encoding-api-landscape#standard-c-and-c">
 * The Wonderfully Terrible World of C and C++ Encoding APIs
 * </a>. In a future C version that contains
 * <a href="https://thephd.dev/_vendor/future_cxx/papers/C%20-%20Restartable%20and%20Non-Restartable%20Character%20Functions%20for%20Efficient%20Conversions.html">
 * N3366 - Restartable Functions for Efficient Character Conversions
 * </a>, we can use that instead of calling back into Java for conversions.
 * </p>
 */
// Used by JNI code
@SuppressWarnings("unused")
public final class StringEncodingHelper {
    public static native void initialize();

    public static byte[] toUtf8(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String fromUtf8(byte[] utf8Bytes) {
        return new String(utf8Bytes, StandardCharsets.UTF_8);
    }

    private StringEncodingHelper() {
    }
}
