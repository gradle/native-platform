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

package net.rubygrapefruit.platform.internal;

public class FunctionResult {
    public enum Failure {
        // Order is important - see generic.h
        Generic,
        NoSuchFile,
        NotADirectory,
        Permissions
    }
    private String message;
    private int errno;
    private Failure failure = Failure.Generic;
    private String errorCodeDescription;

    // Called from native code
    @SuppressWarnings("UnusedDeclaration")
    void failed(String message, int failure, int errno, String errorCodeDescription) {
        this.message = message;
        this.failure = Failure.values()[failure];
        this.errno = errno;
        this.errorCodeDescription = errorCodeDescription;
    }

    // Called from native code
    @SuppressWarnings("UnusedDeclaration")
    void failed(String message) {
        this.message = message;
    }

    public boolean isFailed() {
        return message != null;
    }

    public Failure getFailure() {
        return failure;
    }

    public String getMessage() {
        if (errorCodeDescription != null && errorCodeDescription.length() > 0) {
            return String.format("%s (errno %d: %s)", message, errno, errorCodeDescription);
        }
        if (errno != 0) {
            return String.format("%s (errno %d)", message, errno);
        }
        return message;
    }
}
