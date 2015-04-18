/*
 * Copyright 2012 Adam Murdoch
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

package net.rubygrapefruit.platform;

/**
 * A source for file change events.
 */
@ThreadSafe
public interface FileWatch {
    /**
     * Blocks until the next change occurs, or this watch is closed.
     */
    void nextChange() throws NativeException;

    /**
     * Stops watching for changes. Any thread blocked in {@link #nextChange()} will return.
     * @throws NativeException
     */
    void close() throws NativeException;
}
