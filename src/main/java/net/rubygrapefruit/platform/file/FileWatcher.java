package net.rubygrapefruit.platform.file;

import net.rubygrapefruit.platform.internal.jni.InsufficientResourcesForWatchingException;

import javax.annotation.CheckReturnValue;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * A handle for watching file system locations.
 */
@NotThreadSafe
public interface FileWatcher {
    void startWatching(Collection<File> paths) throws InsufficientResourcesForWatchingException;

    @CheckReturnValue
    boolean stopWatching(Collection<File> paths);

    /**
     * Initiates an orderly shutdown and release of any native resources.
     * No more events will arrive after this method returns.
     */
    void shutdown();

    /**
     * Blocks until the termination is complete after a {@link #shutdown()}
     * request, or the timeout occurs, or the current thread is interrupted,
     * whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this watcher terminated and
     *         {@code false} if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    @CheckReturnValue
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
