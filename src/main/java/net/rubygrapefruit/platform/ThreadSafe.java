package net.rubygrapefruit.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates that the given class or method is thread safe.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ThreadSafe {
}
