package net.rubygrapefruit.platform.file;

import net.rubygrapefruit.platform.ThreadSafe;

public interface CaseSensitivity {
    /**
     * Returns true if this file system performs case sensitive searches.
     */
    @ThreadSafe
    boolean isCaseSensitive();

    /**
     * Returns true if this file system preserves file name case.
     */
    @ThreadSafe
    boolean isCasePreserving();
}
