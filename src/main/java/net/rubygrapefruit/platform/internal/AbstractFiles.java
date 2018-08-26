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

import net.rubygrapefruit.platform.*;
import net.rubygrapefruit.platform.file.FilePermissionException;
import net.rubygrapefruit.platform.file.Files;
import net.rubygrapefruit.platform.file.NoSuchFileException;
import net.rubygrapefruit.platform.file.NotADirectoryException;

import java.io.File;

public abstract class AbstractFiles implements Files {
    protected NativeException listDirFailure(File dir, FunctionResult result) {
        if (result.getFailure() == FunctionResult.Failure.NoSuchFile) {
            throw new NoSuchFileException(String.format("Could not list directory %s as this directory does not exist.", dir));
        }
        if (result.getFailure() == FunctionResult.Failure.NotADirectory) {
            throw new NotADirectoryException(String.format("Could not list directory %s as it is not a directory.", dir));
        }
        if (result.getFailure() == FunctionResult.Failure.Permissions) {
            throw new FilePermissionException(String.format("Could not list directory %s: permission denied", dir));
        }
        throw new NativeException(String.format("Could not list directory %s: %s", dir, result.getMessage()));
    }
}
