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
 * Provides access to the terminal/console.
 *
 * <p>On UNIX based platforms, this provides access to the terminal. On Windows platforms, this provides access to the
 * console.
 * </p>
 */
@ThreadSafe
public interface Terminals extends NativeIntegration {
    /**
     * System outputs.
     */
    enum Output {Stdout, Stderr}

    /**
     * Returns true if the given output is attached to a terminal.
     *
     * @throws NativeException On failure.
     */
    @ThreadSafe
    boolean isTerminal(Output output) throws NativeException;

    /**
     * Returns the terminal attached to the given output.
     *
     * @return The terminal. Never returns null.
     * @throws NativeException When the output is not attached to a terminal.
     */
    @ThreadSafe
    Terminal getTerminal(Output output) throws NativeException;
}
