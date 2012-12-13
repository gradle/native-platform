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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.PosixFile;
import net.rubygrapefruit.platform.internal.jni.PosixFileFunctions;

import java.io.File;

public class DefaultPosixFile implements PosixFile {
    public void setMode(File file, int perms) {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.chmod(file.getPath(), perms, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not set UNIX mode on %s: %s", file, result.getMessage()));
        }
    }

    public int getMode(File file) {
        FunctionResult result = new FunctionResult();
        FileStat stat = new FileStat();
        PosixFileFunctions.stat(file.getPath(), stat, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not get UNIX mode on %s: %s", file, result.getMessage()));
        }
        return stat.mode;
    }

    @Override
    public String readLink(File link) throws NativeException {
        FunctionResult result = new FunctionResult();
        String contents = PosixFileFunctions.readlink(link.getPath(), result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not read symlink %s: %s", link, result.getMessage()));
        }
        return contents;
    }

    @Override
    public void symlink(File link, String contents) throws NativeException {
        FunctionResult result = new FunctionResult();
        PosixFileFunctions.symlink(link.getPath(), contents, result);
        if (result.isFailed()) {
            throw new NativeException(String.format("Could not create symlink %s: %s", link, result.getMessage()));
        }
    }
}
